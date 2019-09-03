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
package org.enquery.encryptedquery.flink.jdbc;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;

import org.enquery.encryptedquery.core.FieldTypeUntypedValueConverterVisitor;
import org.enquery.encryptedquery.utils.ISO8601DateParser;

/**
 *
 */
public class JDBCTypeConverter implements FieldTypeUntypedValueConverterVisitor {

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeUntypedValueConverterVisitor#visitByte(java.lang.
	 * Object)
	 */
	@Override
	public Byte visitByte(Object value) {
		return (value instanceof String) ? Byte.valueOf((String) value) : (Byte) value;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeUntypedValueConverterVisitor#visitByteList(java.lang
	 * .Object)
	 */
	@Override
	public List<Byte> visitByteList(Object value) {
		return convertList(value, v -> visitByte(v));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeUntypedValueConverterVisitor#visitISO8601Date(java.
	 * lang.Object)
	 */
	@Override
	public Instant visitISO8601Date(Object value) {
		if (value instanceof java.sql.Date
				|| value instanceof java.sql.Timestamp) {
			return ((java.sql.Date) value).toInstant();
		}
		return ISO8601DateParser.getInstant((String) value);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeUntypedValueConverterVisitor#visitISO8601DateList(
	 * java.lang.Object)
	 */
	@Override
	public List<Instant> visitISO8601DateList(Object value) {
		return convertList(value, v -> visitISO8601Date(v));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeUntypedValueConverterVisitor#visitIP6(java.lang.
	 * Object)
	 */
	@Override
	public String visitIP6(Object value) {
		return (String) value;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeUntypedValueConverterVisitor#visitIP6List(java.lang.
	 * Object)
	 */
	@Override
	public List<String> visitIP6List(Object value) {
		return convertList(value, v -> visitIP6(v));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeUntypedValueConverterVisitor#visitIP4(java.lang.
	 * Object)
	 */
	@Override
	public String visitIP4(Object value) {
		return (String) value;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeUntypedValueConverterVisitor#visitIP4List(java.lang.
	 * Object)
	 */
	@Override
	public List<String> visitIP4List(Object value) {
		return convertList(value, v -> visitIP4(v));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeUntypedValueConverterVisitor#visitByteArray(java.
	 * lang.Object)
	 */
	@Override
	public byte[] visitByteArray(Object value) {
		return (byte[]) value;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeUntypedValueConverterVisitor#visitByteArrayList(java
	 * .lang.Object)
	 */
	@Override
	public List<byte[]> visitByteArrayList(Object value) {
		return convertList(value, v -> visitByteArray(v));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeUntypedValueConverterVisitor#visitChar(java.lang.
	 * Object)
	 */
	@Override
	public Character visitChar(Object value) {
		return (Character) value;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeUntypedValueConverterVisitor#visitCharList(java.lang
	 * .Object)
	 */
	@Override
	public List<Character> visitCharList(Object value) {
		return convertList(value, v -> visitChar(value));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeUntypedValueConverterVisitor#visitDouble(java.lang.
	 * Object)
	 */
	@Override
	public Double visitDouble(Object value) {
		if (value instanceof Float) {
			return ((Float) value).doubleValue();
		}
		return (Double) value;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeUntypedValueConverterVisitor#visitDoubleList(java.
	 * lang.Object)
	 */
	@Override
	public List<Double> visitDoubleList(Object value) {
		return convertList(value, v -> visitDouble(value));

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeUntypedValueConverterVisitor#visitFloat(java.lang.
	 * Object)
	 */
	@Override
	public Float visitFloat(Object value) {
		return (float) value;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeUntypedValueConverterVisitor#visitFloatList(java.
	 * lang.Object)
	 */
	@Override
	public List<Float> visitFloatList(Object value) {
		return convertList(value, v -> visitFloat(value));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeUntypedValueConverterVisitor#visitLong(java.lang.
	 * Object)
	 */
	@Override
	public Long visitLong(Object value) {
		return (Long) value;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeUntypedValueConverterVisitor#visitLongList(java.lang
	 * .Object)
	 */
	@Override
	public List<Long> visitLongList(Object value) {
		return convertList(value, v -> visitLong(v));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeUntypedValueConverterVisitor#visitShort(java.lang.
	 * Object)
	 */
	@Override
	public Short visitShort(Object value) {
		return (Short) value;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeUntypedValueConverterVisitor#visitShortList(java.
	 * lang.Object)
	 */
	@Override
	public List<Short> visitShortList(Object value) {
		return convertList(value, v -> visitShort(v));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeUntypedValueConverterVisitor#visitInt(java.lang.
	 * Object)
	 */
	@Override
	public Integer visitInt(Object value) {
		return (Integer) value;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeUntypedValueConverterVisitor#visitIntList(java.lang.
	 * Object)
	 */
	@Override
	public List<Integer> visitIntList(Object value) {
		return convertList(value, v -> visitInt(v));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeUntypedValueConverterVisitor#visitString(java.lang.
	 * Object)
	 */
	@Override
	public String visitString(Object value) {
		return (String) value;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeUntypedValueConverterVisitor#visitStringList(java.
	 * lang.Object)
	 */
	@Override
	public List<String> visitStringList(Object value) {
		return convertList(value, v -> visitString(v));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeUntypedValueConverterVisitor#visitBoolean(java.lang.
	 * Object)
	 */
	@Override
	public Boolean visitBoolean(Object value) {
		return (value instanceof String) ? Boolean.valueOf((String) value) : (Boolean) value;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeUntypedValueConverterVisitor#visitBooleanList(java.
	 * lang.Object)
	 */
	@Override
	public List<Boolean> visitBooleanList(Object value) {
		return convertList(value, v -> visitBoolean(v));
	}

	@SuppressWarnings({"unchecked"})
	public <R> List<R> convertList(Object value, Function<Object, R> converter) {
		List<R> list = (List<R>) value;
		for (int i = 0; i < list.size(); ++i) {
			list.set(i, converter.apply(list.get(i)));
		}
		return list;
	}

}
