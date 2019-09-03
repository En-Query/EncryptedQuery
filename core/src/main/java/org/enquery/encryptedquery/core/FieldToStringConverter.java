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
package org.enquery.encryptedquery.core;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.codec.binary.Hex;
import org.enquery.encryptedquery.utils.ISO8601DateParser;

public class FieldToStringConverter implements FieldTypeValueConverterVisitor<String> {

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldType.FieldTypeVisitor#visitByte()
	 */
	@Override
	public String visitByte(Byte value) {
		return new String(new byte[] {value});
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitString(java.util.List)
	 */
	@Override
	public String visitStringList(List<String> value) {
		// TODO: concatenate values?
		return Objects.toString(value);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldType.FieldTypeVisitor#visitString()
	 */
	@Override
	public String visitString(String value) {
		return value;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldType.FieldTypeVisitor#visitByteArray(byte[])
	 */
	@Override
	public String visitByteArray(byte[] value) {
		return Hex.encodeHexString(value);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldType.FieldTypeVisitor#visitChar(java.lang.
	 * Character)
	 */
	@Override
	public String visitChar(Character value) {
		return Character.toString(value);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldType.FieldTypeVisitor#visitDouble(java.lang.Double)
	 */
	@Override
	public String visitDouble(Double value) {
		return Double.toString(value);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldType.FieldTypeVisitor#visitFloat(java.lang.Float)
	 */
	@Override
	public String visitFloat(Float value) {
		return Float.toString(value);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldType.FieldTypeVisitor#visitLong(java.lang.Long)
	 */
	@Override
	public String visitLong(Long value) {
		return Long.toString(value);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldType.FieldTypeVisitor#visitShort(java.lang.Short)
	 */
	@Override
	public String visitShort(Short value) {
		return Short.toString(value);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldType.FieldTypeVisitor#visitInt(java.lang.Integer)
	 */
	@Override
	public String visitInt(Integer value) {
		return Integer.toString(value);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldType.FieldTypeVisitor#visitISO8601Date(java.lang.
	 * String)
	 */
	@Override
	public String visitISO8601Date(Instant value) {
		return ISO8601DateParser.fromInstant(value);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldType.FieldTypeVisitor#visitIP6(java.lang.String)
	 */
	@Override
	public String visitIP6(String value) {
		return value;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldType.FieldTypeVisitor#visitIP4(java.lang.String)
	 */
	@Override
	public String visitIP4(String value) {
		return value;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitByte(java.util.List)
	 */
	@Override
	public String visitByteList(List<Byte> value) {
		return Objects.toString(value);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitISO8601Date(java.util.
	 * List)
	 */
	@Override
	public String visitISO8601DateList(List<Instant> value) {
		return value.stream().map(i -> ISO8601DateParser.fromInstant(i)).collect(Collectors.toList()).toString();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitIP6(java.util.List)
	 */
	@Override
	public String visitIP6List(List<String> value) {
		return Objects.toString(value);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitIP4(java.util.List)
	 */
	@Override
	public String visitIP4List(List<String> value) {
		return Objects.toString(value);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitByteArray(java.util.List)
	 */
	@Override
	public String visitByteArrayList(List<byte[]> value) {
		return Objects.toString(value);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitChar(java.util.List)
	 */
	@Override
	public String visitCharList(List<Character> value) {
		return Objects.toString(value);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitDouble(java.util.List)
	 */
	@Override
	public String visitDoubleList(List<Double> value) {
		return Objects.toString(value);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitFloat(java.util.List)
	 */
	@Override
	public String visitFloatList(List<Float> value) {
		return Objects.toString(value);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitLong(java.util.List)
	 */
	@Override
	public String visitLongList(List<Long> value) {
		return Objects.toString(value);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitShort(java.util.List)
	 */
	@Override
	public String visitShortList(List<Short> value) {
		return Objects.toString(value);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitInt(java.util.List)
	 */
	@Override
	public String visitIntList(List<Integer> value) {
		return Objects.toString(value);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitBoolean(java.lang.
	 * Boolean)
	 */
	@Override
	public String visitBoolean(Boolean value) {
		return Objects.toString(value);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeValueConverterVisitor#visitBooleanList(java.util.
	 * List)
	 */
	@Override
	public String visitBooleanList(List<Boolean> value) {
		return Objects.toString(value);
	}
}
