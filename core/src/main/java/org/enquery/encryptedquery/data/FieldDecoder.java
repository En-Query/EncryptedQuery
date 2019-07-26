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
import java.util.ArrayList;
import java.util.List;

import org.enquery.encryptedquery.core.FieldTypeProducerVisitor;
import org.enquery.encryptedquery.utils.ISO8601DateParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decodes a field
 */
public class FieldDecoder implements FieldTypeProducerVisitor {

	static final int IPV6_SIZE = 16;
	private static final byte[] NULL_IPV6_VALUE = new byte[IPV6_SIZE];
	private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

	private static final Logger log = LoggerFactory.getLogger(FieldDecoder.class);

	private final VarIntEncoding varIntEncoder;
	private final ByteBuffer buffer;
	private String fieldName;

	/**
	 * 
	 */
	public FieldDecoder(VarIntEncoding varIntEncoder, ByteBuffer buffer) {
		this.varIntEncoder = varIntEncoder;
		this.buffer = buffer;
	}


	public String getFieldName() {
		return fieldName;
	}

	public void setFieldName(String fieldName) {
		this.fieldName = fieldName;
	}


	public <E> List<E> decodeList(ValueProducer<E> singleValueDecoder) {
		final boolean debugging = log.isDebugEnabled();
		final int count = (int) varIntEncoder.decodeNonnegative64(buffer);
		if (debugging) {
			log.debug("Reading array field '{}' of size {}", fieldName, count);
		}
		final List<E> result = new ArrayList<>(count);
		for (int i = 0; i < count; ++i) {
			result.add(singleValueDecoder.produce());
		}
		if (debugging) {
			log.debug("Read array field '{}' = {}", fieldName, result);
		}
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeProducerVisitor#visitByte()
	 */
	@Override
	public Byte visitByte() {
		return buffer.get();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeProducerVisitor#visitByteList()
	 */
	@Override
	public List<Byte> visitByteList() {
		return decodeList(() -> visitByte());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeProducerVisitor#visitISO8601Date()
	 */
	@Override
	public String visitISO8601Date() {
		return ISO8601DateParser.fromLongDate(buffer.getLong());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeProducerVisitor#visitISO8601DateList()
	 */
	@Override
	public List<String> visitISO8601DateList() {
		return decodeList(() -> visitISO8601Date());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeProducerVisitor#visitIP6()
	 */
	@Override
	public String visitIP6() {
		try {
			byte[] tmp = NULL_IPV6_VALUE;
			buffer.get(tmp);
			return InetAddress.getByAddress(tmp).getHostAddress();
		} catch (UnknownHostException e) {
			throw new RuntimeException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeProducerVisitor#visitIP6List()
	 */
	@Override
	public List<String> visitIP6List() {
		return decodeList(() -> visitIP6());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeProducerVisitor#visitIP4()
	 */
	@Override
	public String visitIP4() {
		try {
			byte[] tmp = new byte[Integer.BYTES];
			buffer.get(tmp);
			return InetAddress.getByAddress(tmp).getHostAddress();
		} catch (UnknownHostException e) {
			throw new RuntimeException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeProducerVisitor#visitIP4List()
	 */
	@Override
	public List<String> visitIP4List() {
		return decodeList(() -> visitIP4());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeProducerVisitor#visitByteArray()
	 */
	@Override
	public byte[] visitByteArray() {
		int length = (int) varIntEncoder.decodeNonnegative64(buffer);
		if (length == 0) return EMPTY_BYTE_ARRAY;

		byte[] tmp = new byte[length];
		buffer.get(tmp);
		return tmp;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeProducerVisitor#visitByteArrayList()
	 */
	@Override
	public List<byte[]> visitByteArrayList() {
		return decodeList(() -> visitByteArray());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeProducerVisitor#visitChar()
	 */
	@Override
	public Character visitChar() {
		return buffer.getChar();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeProducerVisitor#visitCharList()
	 */
	@Override
	public List<Character> visitCharList() {
		return decodeList(() -> visitChar());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeProducerVisitor#visitDouble()
	 */
	@Override
	public Double visitDouble() {
		return buffer.getDouble();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeProducerVisitor#visitDoubleList()
	 */
	@Override
	public List<Double> visitDoubleList() {
		return decodeList(() -> visitDouble());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeProducerVisitor#visitFloat()
	 */
	@Override
	public Float visitFloat() {
		return buffer.getFloat();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeProducerVisitor#visitFloatList()
	 */
	@Override
	public List<Float> visitFloatList() {
		return decodeList(() -> visitFloat());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeProducerVisitor#visitLong()
	 */
	@Override
	public Long visitLong() {
		return varIntEncoder.decodeSigned64(buffer);
		// return buffer.getLong();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeProducerVisitor#visitLongList()
	 */
	@Override
	public List<Long> visitLongList() {
		return decodeList(() -> visitLong());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeProducerVisitor#visitShort()
	 */
	@Override
	public Short visitShort() {
		return buffer.getShort();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeProducerVisitor#visitShortList()
	 */
	@Override
	public List<Short> visitShortList() {
		return decodeList(() -> visitShort());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeProducerVisitor#visitInt()
	 */
	@Override
	public Integer visitInt() {
		return (int) varIntEncoder.decodeSigned64(buffer);
		// return buffer.getInt();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeProducerVisitor#visitIntList()
	 */
	@Override
	public List<Integer> visitIntList() {
		return decodeList(() -> visitInt());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeProducerVisitor#visitString()
	 */
	@Override
	public String visitString() {
		int length = (int) varIntEncoder.decodeNonnegative64(buffer);
		if (length <= 0) return "";

		byte[] tmp = new byte[length];
		buffer.get(tmp);

		return new String(tmp, StandardCharsets.UTF_8);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeProducerVisitor#visitStringList()
	 */
	@Override
	public List<String> visitStringList() {
		return decodeList(() -> visitString());
	}

}
