/*
 * EncryptedQuery is an open source project allowing user to query databases with queries under
 * homomorphic encryption to securing the query and results set from database owner inspection.
 * Copyright (C) 2018 EnQuery LLC
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program.
 * If not, see <https://www.gnu.org/licenses/>.
 */
package org.enquery.encryptedquery.data;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor;
import org.enquery.encryptedquery.utils.ISO8601DateParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encode a field based on its data type
 */
public class FieldEncoder implements FieldTypeValueConverterVisitor<Void> {

	private static final Logger log = LoggerFactory.getLogger(FieldEncoder.class);

	private final VarIntEncoding varIntEncoder;
	private final ByteBuffer buffer;
	private Integer maxSize, maxArraySize;
	private String fieldName;

	/**
	 * 
	 */
	public FieldEncoder(VarIntEncoding varIntEncoder, ByteBuffer buffer) {
		this.varIntEncoder = varIntEncoder;
		this.buffer = buffer;
	}

	public Integer getMaxSize() {
		return maxSize;
	}

	public void setMaxSize(Integer maxSize) {
		this.maxSize = maxSize;
	}

	public Integer getMaxArraySize() {
		return maxArraySize;
	}

	public void setMaxArraySize(Integer maxArraySize) {
		this.maxArraySize = maxArraySize;
	}

	public String getFieldName() {
		return fieldName;
	}

	public void setFieldName(String fieldName) {
		this.fieldName = fieldName;
	}

	public <E> void encodeList(final List<E> array, final Consumer<E> singleValueEncoder) {
		final boolean debugging = log.isDebugEnabled();

		int elementsToWrite = (array == null) ? 0 : array.size();

		if (debugging) {
			log.debug("Writing array field '{}' of size {}", fieldName, elementsToWrite);
		}

		if (elementsToWrite <= 0) {
			varIntEncoder.encodeNonnegative64(buffer, 0);
			return;
		}

		// write the number of elements first
		if (maxArraySize != null && maxArraySize < elementsToWrite) {
			elementsToWrite = maxArraySize;
		}
		varIntEncoder.encodeNonnegative64(buffer, elementsToWrite);

		// now write each element in the array
		for (int i = 0; i < elementsToWrite; ++i) {
			singleValueEncoder.accept(array.get(i));
		}
		if (debugging) {
			log.debug("Wrote {} elements for array field '{}'.", elementsToWrite, fieldName);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitByte(java.lang.Byte)
	 */
	@Override
	public Void visitByte(Byte value) {
		buffer.put((value == null) ? 0 : (byte) value);
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitByte(java.util.List)
	 */
	@Override
	public Void visitByteList(List<Byte> value) {
		encodeList(value, v -> visitByte(v));
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitISO8601Date(java.lang.
	 * String)
	 */
	@Override
	public Void visitISO8601Date(String value) {
		buffer.putLong(ISO8601DateParser.getLongDate(value));
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitISO8601Date(java.util.
	 * List)
	 */
	@Override
	public Void visitISO8601DateList(List<String> value) {
		encodeList(value, v -> visitISO8601Date(v));
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitIP6(java.lang.String)
	 */
	@Override
	public Void visitIP6(String value) {
		try {
			InetAddress ipv6 = null;
			if (value == null) {
				ipv6 = InetAddress.getByName("::1");
			} else {
				ipv6 = InetAddress.getByName(value);
			}
			byte[] bytes = ipv6.getAddress();
			Validate.isTrue(bytes.length == 16);
			buffer.put(bytes);
		} catch (UnknownHostException e) {
			throw new RuntimeException(e);
		}
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitIP6(java.util.List)
	 */
	@Override
	public Void visitIP6List(List<String> value) {
		encodeList(value, v -> visitIP6(v));
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitIP4(java.lang.String)
	 */
	@Override
	public Void visitIP4(String value) {
		try {
			InetAddress ipv4 = InetAddress.getByName(value);
			buffer.put(ipv4.getAddress());
		} catch (UnknownHostException e) {
			throw new RuntimeException(e);
		}
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitIP4(java.util.List)
	 */
	@Override
	public Void visitIP4List(List<String> value) {
		encodeList(value, v -> visitIP4(v));
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitByteArray(byte[])
	 */
	@Override
	public Void visitByteArray(byte[] value) {
		if (value == null || value.length == 0) {
			varIntEncoder.encodeNonnegative64(buffer, 0);
		} else {
			int len = value.length;
			if (maxSize != null && len >= maxSize) {
				len = maxSize;
			}
			varIntEncoder.encodeNonnegative64(buffer, len);
			buffer.put(value, 0, len);
		}
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitByteArray(java.util.List)
	 */
	@Override
	public Void visitByteArrayList(List<byte[]> value) {
		encodeList(value, v -> visitByteArray(v));
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitChar(java.lang.Character)
	 */
	@Override
	public Void visitChar(Character value) {
		buffer.putChar((value == null) ? Character.valueOf((char) 0) : (char) value);
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitChar(java.util.List)
	 */
	@Override
	public Void visitCharList(List<Character> value) {
		encodeList(value, v -> visitChar(v));
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitDouble(java.lang.Double)
	 */
	@Override
	public Void visitDouble(Double value) {
		buffer.putDouble((value == null) ? Double.valueOf(0) : (double) value);
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitDouble(java.util.List)
	 */
	@Override
	public Void visitDoubleList(List<Double> value) {
		encodeList(value, v -> visitDouble(v));
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitFloat(java.lang.Float)
	 */
	@Override
	public Void visitFloat(Float value) {
		buffer.putFloat((value == null) ? Float.valueOf(0) : (float) value);
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitFloat(java.util.List)
	 */
	@Override
	public Void visitFloatList(List<Float> value) {
		encodeList(value, v -> visitFloat(v));
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitLong(java.lang.Long)
	 */
	@Override
	public Void visitLong(Long value) {
		varIntEncoder.encodeSigned64(buffer, value == null ? 0 : value);
		// buffer.putLong((value == null) ? 0L : (long) value);
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitLong(java.util.List)
	 */
	@Override
	public Void visitLongList(List<Long> value) {
		encodeList(value, v -> visitLong(v));
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitShort(java.lang.Short)
	 */
	@Override
	public Void visitShort(Short value) {
		buffer.putShort((value == null) ? 0 : (short) value);
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitShort(java.util.List)
	 */
	@Override
	public Void visitShortList(List<Short> value) {
		encodeList(value, v -> visitShort(v));
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitInt(java.lang.Integer)
	 */
	@Override
	public Void visitInt(Integer value) {
		varIntEncoder.encodeSigned64(buffer, value == null ? 0 : (int) value);
		// buffer.putInt(value == null ? 0 : (int) value);
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitInt(java.util.List)
	 */
	@Override
	public Void visitIntList(List<Integer> value) {
		encodeList(value, v -> visitInt(v));
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitString(java.lang.String)
	 */
	@Override
	public Void visitString(String data) {
		if (data == null) {
			// store lenght zero
			varIntEncoder.encodeNonnegative64(buffer, 0);
		} else {
			// TODO: prior implementation was triming the string value, we are no longer doing it
			// truncate as per the Query Schema size
			// truncation of strings can't be delegated to the byte[] truncation
			// because a character is 2 bytes
			// final Integer max = qse.getSize();
			String tmp = data;
			if (maxSize != null && data.length() >= maxSize) {
				tmp = data.substring(0, maxSize);
			}
			byte[] bytes = tmp.getBytes(StandardCharsets.UTF_8);
			varIntEncoder.encodeNonnegative64(buffer, bytes.length);
			buffer.put(bytes);
		}
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitString(java.util.List)
	 */
	@Override
	public Void visitStringList(List<String> value) {
		encodeList(value, v -> visitString(v));
		return null;
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitBoolean(java.lang.
	 * Boolean)
	 */
	@Override
	public Void visitBoolean(Boolean value) {
		if (value == null)
			buffer.put((byte) -1);
		else if (value)
			buffer.put((byte) 1);
		else
			buffer.put((byte) 0);
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitBooleanList(java.util.
	 * List)
	 */
	@Override
	public Void visitBooleanList(List<Boolean> value) {
		encodeList(value, v -> visitBoolean(v));
		return null;
	}

}
