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

import java.util.List;

public enum FieldType {

	BYTE("byte") {

		@Override
		public <E> E convert(FieldTypeValueConverterVisitor<E> visitor, Object value) {
			return visitor.visitByte((Byte) value);
		}

		@Override
		public Object convert(FieldTypeProducerVisitor visitor) {
			return visitor.visitByte();
		}

		@Override
		public Object convert(FieldTypeUntypedValueConverterVisitor visitor, Object value) {
			return visitor.visitByte(value);
		}
	},
	BYTE_LIST("byte_list") {

		@SuppressWarnings("unchecked")
		@Override
		public <E> E convert(FieldTypeValueConverterVisitor<E> visitor, Object value) {
			return visitor.visitByteList((List<Byte>) value);
		}

		@Override
		public Object convert(FieldTypeProducerVisitor visitor) {
			return visitor.visitByteList();
		}

		@Override
		public Object convert(FieldTypeUntypedValueConverterVisitor visitor, Object value) {
			return visitor.visitByteList(value);
		}
	},
	BOOLEAN("boolean") {

		@Override
		public <E> E convert(FieldTypeValueConverterVisitor<E> visitor, Object value) {
			return visitor.visitBoolean((Boolean) value);
		}

		@Override
		public Object convert(FieldTypeProducerVisitor visitor) {
			return visitor.visitBoolean();
		}

		@Override
		public Object convert(FieldTypeUntypedValueConverterVisitor visitor, Object value) {
			return visitor.visitBoolean(value);
		}
	},
	BOOLEAN_LIST("boolean_list") {

		@SuppressWarnings("unchecked")
		@Override
		public <E> E convert(FieldTypeValueConverterVisitor<E> visitor, Object value) {
			return visitor.visitBooleanList((List<Boolean>) value);
		}

		@Override
		public Object convert(FieldTypeProducerVisitor visitor) {
			return visitor.visitBooleanList();
		}

		@Override
		public Object convert(FieldTypeUntypedValueConverterVisitor visitor, Object value) {
			return visitor.visitBooleanList(value);
		}
	},
	SHORT("short") {

		@Override
		public <E> E convert(FieldTypeValueConverterVisitor<E> visitor, Object value) {
			return visitor.visitShort((Short) value);
		}

		@Override
		public Object convert(FieldTypeProducerVisitor visitor) {
			return visitor.visitShort();
		}

		@Override
		public Object convert(FieldTypeUntypedValueConverterVisitor visitor, Object value) {
			return visitor.visitShort(value);
		}

	},
	SHORT_LIST("short_list") {

		@SuppressWarnings("unchecked")
		@Override
		public <E> E convert(FieldTypeValueConverterVisitor<E> visitor, Object value) {
			return visitor.visitShortList((List<Short>) value);
		}

		@Override
		public Object convert(FieldTypeProducerVisitor visitor) {
			return visitor.visitShortList();
		}

		@Override
		public Object convert(FieldTypeUntypedValueConverterVisitor visitor, Object value) {
			return visitor.visitShortList(value);
		}
	},
	INT("int") {

		@Override
		public <E> E convert(FieldTypeValueConverterVisitor<E> visitor, Object value) {
			return visitor.visitInt((Integer) value);
		}

		@Override
		public Object convert(FieldTypeProducerVisitor visitor) {
			return visitor.visitInt();
		}

		@Override
		public Object convert(FieldTypeUntypedValueConverterVisitor visitor, Object value) {
			return visitor.visitInt(value);
		}
	},
	INT_LIST("int_list") {

		@SuppressWarnings("unchecked")
		@Override
		public <E> E convert(FieldTypeValueConverterVisitor<E> visitor, Object value) {
			return visitor.visitIntList((List<Integer>) value);
		}

		@Override
		public Object convert(FieldTypeProducerVisitor visitor) {
			return visitor.visitIntList();
		}

		@Override
		public Object convert(FieldTypeUntypedValueConverterVisitor visitor, Object value) {
			return visitor.visitIntList(value);
		}
	},
	LONG("long") {

		@Override
		public <E> E convert(FieldTypeValueConverterVisitor<E> visitor, Object value) {
			return visitor.visitLong((Long) value);
		}

		@Override
		public Object convert(FieldTypeProducerVisitor visitor) {
			return visitor.visitLong();
		}

		@Override
		public Object convert(FieldTypeUntypedValueConverterVisitor visitor, Object value) {
			return visitor.visitLong(value);
		}
	},
	LONG_LIST("long_list") {

		@SuppressWarnings("unchecked")
		@Override
		public <E> E convert(FieldTypeValueConverterVisitor<E> visitor, Object value) {
			return visitor.visitLongList((List<Long>) value);
		}

		@Override
		public Object convert(FieldTypeProducerVisitor visitor) {
			return visitor.visitLongList();
		}

		@Override
		public Object convert(FieldTypeUntypedValueConverterVisitor visitor, Object value) {
			return visitor.visitLongList(value);
		}
	},
	FLOAT("float") {

		@Override
		public <E> E convert(FieldTypeValueConverterVisitor<E> visitor, Object value) {
			return visitor.visitFloat((Float) value);
		}

		@Override
		public Object convert(FieldTypeProducerVisitor visitor) {
			return visitor.visitFloat();
		}

		@Override
		public Object convert(FieldTypeUntypedValueConverterVisitor visitor, Object value) {
			return visitor.visitFloat(value);
		}
	},
	FLOAT_LIST("float_list") {

		@SuppressWarnings("unchecked")
		@Override
		public <E> E convert(FieldTypeValueConverterVisitor<E> visitor, Object value) {
			return visitor.visitFloatList((List<Float>) value);
		}

		@Override
		public Object convert(FieldTypeProducerVisitor visitor) {
			return visitor.visitFloatList();
		}

		@Override
		public Object convert(FieldTypeUntypedValueConverterVisitor visitor, Object value) {
			return visitor.visitFloatList(value);
		}
	},
	DOUBLE("double") {

		@Override
		public <E> E convert(FieldTypeValueConverterVisitor<E> visitor, Object value) {
			return visitor.visitDouble((Double) value);
		}

		@Override
		public Object convert(FieldTypeProducerVisitor visitor) {
			return visitor.visitDouble();
		}

		@Override
		public Object convert(FieldTypeUntypedValueConverterVisitor visitor, Object value) {
			return visitor.visitDouble(value);
		}
	},
	DOUBLE_LIST("double_list") {

		@SuppressWarnings("unchecked")
		@Override
		public <E> E convert(FieldTypeValueConverterVisitor<E> visitor, Object value) {
			return visitor.visitDoubleList((List<Double>) value);
		}

		@Override
		public Object convert(FieldTypeProducerVisitor visitor) {
			return visitor.visitDoubleList();
		}

		@Override
		public Object convert(FieldTypeUntypedValueConverterVisitor visitor, Object value) {
			return visitor.visitDoubleList(value);
		}
	},
	CHAR("char") {

		@Override
		public <E> E convert(FieldTypeValueConverterVisitor<E> visitor, Object value) {
			return visitor.visitChar((Character) value);
		}

		@Override
		public Object convert(FieldTypeProducerVisitor visitor) {
			return visitor.visitChar();
		}

		@Override
		public Object convert(FieldTypeUntypedValueConverterVisitor visitor, Object value) {
			return visitor.visitChar(value);
		}
	},
	CHAR_LIST("char_list") {

		@SuppressWarnings("unchecked")
		@Override
		public <E> E convert(FieldTypeValueConverterVisitor<E> visitor, Object value) {
			return visitor.visitCharList((List<Character>) value);
		}

		@Override
		public Object convert(FieldTypeProducerVisitor visitor) {
			return visitor.visitCharList();
		}

		@Override
		public Object convert(FieldTypeUntypedValueConverterVisitor visitor, Object value) {
			return visitor.visitCharList(value);
		}
	},
	STRING("string") {

		@Override
		public <E> E convert(FieldTypeValueConverterVisitor<E> visitor, Object value) {
			return visitor.visitString((String) value);
		}

		@Override
		public Object convert(FieldTypeProducerVisitor visitor) {
			return visitor.visitString();
		}

		@Override
		public Object convert(FieldTypeUntypedValueConverterVisitor visitor, Object value) {
			return visitor.visitString(value);
		}

	},
	STRING_LIST("string_list") {

		@SuppressWarnings("unchecked")
		@Override
		public <E> E convert(FieldTypeValueConverterVisitor<E> visitor, Object value) {
			return visitor.visitStringList((List<String>) value);
		}

		@Override
		public Object convert(FieldTypeProducerVisitor visitor) {
			return visitor.visitStringList();
		}

		@Override
		public Object convert(FieldTypeUntypedValueConverterVisitor visitor, Object value) {
			return visitor.visitStringList(value);
		}

	},
	BYTEARRAY("byteArray") {

		@Override
		public <E> E convert(FieldTypeValueConverterVisitor<E> visitor, Object value) {
			return visitor.visitByteArray((byte[]) value);
		}

		@Override
		public Object convert(FieldTypeProducerVisitor visitor) {
			return visitor.visitByteArray();
		}

		@Override
		public Object convert(FieldTypeUntypedValueConverterVisitor visitor, Object value) {
			return visitor.visitByteArray(value);
		}
	},
	BYTEARRAY_LIST("byteArray_list") {

		@SuppressWarnings("unchecked")
		@Override
		public <E> E convert(FieldTypeValueConverterVisitor<E> visitor, Object value) {
			return visitor.visitByteArrayList((List<byte[]>) value);
		}

		@Override
		public Object convert(FieldTypeProducerVisitor visitor) {
			return visitor.visitByteArrayList();
		}

		@Override
		public Object convert(FieldTypeUntypedValueConverterVisitor visitor, Object value) {
			return visitor.visitByteArrayList(value);
		}
	},
	IP4("ip4") {

		@Override
		public <E> E convert(FieldTypeValueConverterVisitor<E> visitor, Object value) {
			return visitor.visitIP4((String) value);
		}

		@Override
		public Object convert(FieldTypeProducerVisitor visitor) {
			return visitor.visitIP4();
		}

		@Override
		public Object convert(FieldTypeUntypedValueConverterVisitor visitor, Object value) {
			return visitor.visitIP4(value);
		}
	},
	IP4_LIST("ip4_list") {

		@SuppressWarnings("unchecked")
		@Override
		public <E> E convert(FieldTypeValueConverterVisitor<E> visitor, Object value) {
			return visitor.visitIP4List((List<String>) value);
		}

		@Override
		public Object convert(FieldTypeProducerVisitor visitor) {
			return visitor.visitIP4List();
		}

		@Override
		public Object convert(FieldTypeUntypedValueConverterVisitor visitor, Object value) {
			return visitor.visitIP4List(value);
		}
	},
	IP6("ip6") {

		@Override
		public <E> E convert(FieldTypeValueConverterVisitor<E> visitor, Object value) {
			return visitor.visitIP6((String) value);
		}

		@Override
		public Object convert(FieldTypeProducerVisitor visitor) {
			return visitor.visitIP6();
		}

		@Override
		public Object convert(FieldTypeUntypedValueConverterVisitor visitor, Object value) {
			return visitor.visitIP6(value);
		}
	},
	IP6_LIST("ip6_list") {

		@SuppressWarnings("unchecked")
		@Override
		public <E> E convert(FieldTypeValueConverterVisitor<E> visitor, Object value) {
			return visitor.visitIP6List((List<String>) value);
		}

		@Override
		public Object convert(FieldTypeProducerVisitor visitor) {
			return visitor.visitIP6List();
		}

		@Override
		public Object convert(FieldTypeUntypedValueConverterVisitor visitor, Object value) {
			return visitor.visitIP6List(value);
		}
	},
	ISO8601DATE("ISO8601Date") {

		@Override
		public <E> E convert(FieldTypeValueConverterVisitor<E> visitor, Object value) {
			return visitor.visitISO8601Date((String) value);
		}

		@Override
		public Object convert(FieldTypeProducerVisitor visitor) {
			return visitor.visitISO8601Date();
		}

		@Override
		public Object convert(FieldTypeUntypedValueConverterVisitor visitor, Object value) {
			return visitor.visitISO8601Date(value);
		}
	},
	ISO8601DATE_LIST("ISO8601Date_list") {

		@SuppressWarnings("unchecked")
		@Override
		public <E> E convert(FieldTypeValueConverterVisitor<E> visitor, Object value) {
			return visitor.visitISO8601DateList((List<String>) value);
		}

		@Override
		public Object convert(FieldTypeProducerVisitor visitor) {
			return visitor.visitISO8601DateList();
		}

		@Override
		public Object convert(FieldTypeUntypedValueConverterVisitor visitor, Object value) {
			return visitor.visitISO8601DateList(value);
		}
	};

	private final String externalName;

	/**
	 * 
	 */
	private FieldType(String name) {
		externalName = name;
	}

	@Override
	public String toString() {
		return externalName;
	}

	static public FieldType fromExternalName(String name) {
		for (FieldType v : values()) {
			if (v.getExternalName().equalsIgnoreCase(name)) return v;
		}

		throw new IllegalArgumentException("Invalid FieldType external name: " + name);
	}

	public static String asString(FieldType type, Object fieldValue) {
		return type.convert(new FieldToStringConverter(), fieldValue);
	}

	/**
	 * Perform a convertion given a field value. Produces a value.
	 * 
	 * @param visitor encapsulates the convertion to perform
	 * @param fieldValue The value of the field
	 * @return the result of the convertion
	 */
	public abstract <E> E convert(FieldTypeValueConverterVisitor<E> visitor, Object fieldValue);

	/**
	 * Perform a convertion of a FieldType to Object without a value.
	 * 
	 * @param visitor
	 * @return
	 */
	public abstract Object convert(FieldTypeProducerVisitor visitor);

	/**
	 * Perform a convertion of FieldType given untyped object value.
	 * 
	 * @param visitor
	 * @return
	 */
	public abstract Object convert(FieldTypeUntypedValueConverterVisitor visitor, Object value);

	public String getExternalName() {
		return externalName;
	}
}
