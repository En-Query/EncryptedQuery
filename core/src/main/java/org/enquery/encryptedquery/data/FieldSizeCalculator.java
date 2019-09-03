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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor;

/**
 *
 */
public class FieldSizeCalculator implements FieldTypeValueConverterVisitor<Integer> {

	private final VarIntEncoding varIntEncoder;
	private final Integer maxSize;
	private final Integer maxArraySize;

	/**
	 * 
	 */
	public FieldSizeCalculator(VarIntEncoding varIntEncoder, Integer maxSize, Integer maxArraySize) {
		this.varIntEncoder = varIntEncoder;
		this.maxSize = maxSize;
		this.maxArraySize = maxArraySize;
	}

	private int calc(List<?> list, int baseSize) {
		if (list == null) return varIntEncoder.encodingLengthNonnegative64(0);

		int size = list.size();
		if (maxArraySize != null && maxArraySize < size) {
			size = maxArraySize;
		}
		return varIntEncoder.encodingLengthNonnegative64(size) + (size * baseSize);
	}

	private int calculateStringFieldSize(List<String> array) {
		if (array == null) {
			return varIntEncoder.encodingLengthNonnegative64(0);
		}

		int result = 0;
		int len = array.size();
		if (maxArraySize != null && len >= maxArraySize) {
			len = maxArraySize;
		}
		// we write the length of the array followed by each string
		result += varIntEncoder.encodingLengthNonnegative64(len);
		for (int i = 0; i < len; ++i) {
			result += calculateStringFieldSize(array.get(i));
		}
		return result;
	}

	/**
	 * @param qse
	 * @param string
	 * @return
	 */
	private int calculateStringFieldSize(final String data) {
		if (data == null) {
			return varIntEncoder.encodingLengthNonnegative64(0);
		}
		String tmp = data;
		if (maxSize != null && data.length() >= maxSize) {
			tmp = data.substring(0, maxSize);
		}
		byte[] bytes = tmp.getBytes(StandardCharsets.UTF_8);
		return varIntEncoder.encodingLengthNonnegative64(bytes.length) + bytes.length;
	}

	/**
	 * @param dse
	 * @param data
	 * @return
	 */
	private int calculateByteArrayFieldSize(List<byte[]> array) {
		if (array == null) {
			return varIntEncoder.encodingLengthNonnegative64(0);
		}

		int result = 0;
		int len = array.size();
		if (maxArraySize != null && len >= maxArraySize) {
			len = maxArraySize;
		}
		// we write the lenght of the array followed by each item
		result += varIntEncoder.encodingLengthNonnegative64(len);
		for (int i = 0; i < len; ++i) {
			result += calculateByteArrayFieldSize(array.get(i));
		}
		return result;
	}

	/**
	 * @param qse
	 * @param bs
	 * @return
	 */
	private int calculateByteArrayFieldSize(byte[] data) {
		if (data == null) return varIntEncoder.encodingLengthNonnegative64(0);

		// truncate if requested
		int len = data.length;
		// Integer max = qse.getSize();
		if (maxSize != null) {
			if (len > maxSize) {
				len = maxSize;
			}
		}
		// the size of the encoded length followed by the actual bytes
		return varIntEncoder.encodingLengthNonnegative64(len) + len;
	}


	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitByte(java.lang.Byte)
	 */
	@Override
	public Integer visitByte(Byte value) {
		return Byte.BYTES;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitByte(java.util.List)
	 */
	@Override
	public Integer visitByteList(List<Byte> value) {
		return calc(value, Byte.BYTES);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitISO8601Date(java.lang.
	 * String)
	 */
	@Override
	public Integer visitISO8601Date(Instant value) {
		return Long.BYTES;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitISO8601Date(java.util.
	 * List)
	 */
	@Override
	public Integer visitISO8601DateList(List<Instant> value) {
		return calc(value, Long.BYTES);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitIP6(java.lang.String)
	 */
	@Override
	public Integer visitIP6(String value) {
		return FieldDecoder.IPV6_SIZE;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitIP6(java.util.List)
	 */
	@Override
	public Integer visitIP6List(List<String> value) {
		return calc(value, FieldDecoder.IPV6_SIZE);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitIP4(java.lang.String)
	 */
	@Override
	public Integer visitIP4(String value) {
		return Integer.BYTES;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitIP4(java.util.List)
	 */
	@Override
	public Integer visitIP4List(List<String> value) {
		return calc(value, Integer.BYTES);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitByteArray(byte[])
	 */
	@Override
	public Integer visitByteArray(byte[] value) {
		return calculateByteArrayFieldSize(value);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitByteArray(java.util.List)
	 */
	@Override
	public Integer visitByteArrayList(List<byte[]> value) {
		return calculateByteArrayFieldSize(value);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitChar(java.lang.Character)
	 */
	@Override
	public Integer visitChar(Character value) {
		return Character.BYTES;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitChar(java.util.List)
	 */
	@Override
	public Integer visitCharList(List<Character> value) {
		return calc(value, Character.BYTES);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitDouble(java.lang.Double)
	 */
	@Override
	public Integer visitDouble(Double value) {
		return Double.BYTES;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitDouble(java.util.List)
	 */
	@Override
	public Integer visitDoubleList(List<Double> value) {
		return calc(value, Double.BYTES);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitFloat(java.lang.Float)
	 */
	@Override
	public Integer visitFloat(Float value) {
		return Float.BYTES;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitFloat(java.util.List)
	 */
	@Override
	public Integer visitFloatList(List<Float> value) {
		return calc(value, Float.BYTES);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitLong(java.lang.Long)
	 */
	@Override
	public Integer visitLong(Long value) {
		return varIntEncoder.encodingLengthSigned64(value == null ? 0 : value);
		// return Long.BYTES;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitLong(java.util.List)
	 */
	@Override
	public Integer visitLongList(List<Long> value) {
		return calc(value, Long.BYTES);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitShort(java.lang.Short)
	 */
	@Override
	public Integer visitShort(Short value) {
		return Short.BYTES;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitShort(java.util.List)
	 */
	@Override
	public Integer visitShortList(List<Short> value) {
		return calc(value, Short.BYTES);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitInt(java.lang.Integer)
	 */
	@Override
	public Integer visitInt(Integer value) {
		return varIntEncoder.encodingLengthSigned64(value == null ? 0 : (int) value);
		// return Integer.BYTES;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitInt(java.util.List)
	 */
	@Override
	public Integer visitIntList(List<Integer> value) {
		return calc(value, Integer.BYTES);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitString(java.lang.String)
	 */
	@Override
	public Integer visitString(String value) {
		return calculateStringFieldSize(value);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitString(java.util.List)
	 */
	@Override
	public Integer visitStringList(List<String> value) {
		return calculateStringFieldSize(value);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitBoolean(java.lang.
	 * Boolean)
	 */
	@Override
	public Integer visitBoolean(Boolean value) {
		return 1;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitBooleanList(java.util.
	 * List)
	 */
	@Override
	public Integer visitBooleanList(List<Boolean> value) {
		return calc(value, 1);
	}


}
