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
package org.enquery.encryptedquery.json;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.core.FieldTypeUntypedValueConverterVisitor;
import org.enquery.encryptedquery.data.DataSchema;
import org.enquery.encryptedquery.data.DataSchemaElement;

/**
 * A HashMap, that given a DataSchema, keeps only elements that are defined in the data schema and
 * automatically flattens embeded maps. Used for parsing JSON.
 */
public class MapWithSchema implements Map<String, Object>, FieldTypeUntypedValueConverterVisitor, Serializable {

	// private static final Logger log = LoggerFactory.getLogger(MapWithSchema.class);
	private static final long serialVersionUID = 1L;
	private final DataSchema schema;
	private final String propertyNamePrefix;
	private final HashMap<String, Object> data;

	public MapWithSchema(DataSchema schema, String propertyNamePrefix) {
		Validate.notNull(schema);
		this.schema = schema;
		this.propertyNamePrefix = propertyNamePrefix;
		data = new HashMap<>();
	}

	public HashMap<String, Object> getData() {
		return data;
	}

	@Override
	public Object put(String key, Object value) {

		String elementName;
		if (propertyNamePrefix != null) {
			elementName = propertyNamePrefix + "|" + key;
		} else {
			elementName = key;
		}

		// log.info("put '{}'='{}' of type {}", elementName, value, (value != null) ?
		// value.getClass().getName() : "null");
		// nulls are not added to the map
		if (value == null) return null;


		if (value instanceof MapWithSchema) {
			this.putAll((MapWithSchema) value);
			return null;
		} else {
			// if key is not in the schema it is not added to the map
			DataSchemaElement dse = schema.elementByName(elementName);
			if (dse == null) return null;
			// convert to expected data type based on the schema
			final Object actualValue = dse.getDataType().convert(this, value);
			return data.put(elementName, actualValue);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#size()
	 */
	@Override
	public int size() {
		return data.size();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#isEmpty()
	 */
	@Override
	public boolean isEmpty() {
		return data.isEmpty();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#containsKey(java.lang.Object)
	 */
	@Override
	public boolean containsKey(Object key) {
		return data.containsKey(key);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#containsValue(java.lang.Object)
	 */
	@Override
	public boolean containsValue(Object value) {
		return data.containsValue(value);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#get(java.lang.Object)
	 */
	@Override
	public Object get(Object key) {
		return data.get(key);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#remove(java.lang.Object)
	 */
	@Override
	public Object remove(Object key) {
		return data.remove(key);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#putAll(java.util.Map)
	 */
	@Override
	public void putAll(Map<? extends String, ? extends Object> m) {
		data.putAll(m);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#clear()
	 */
	@Override
	public void clear() {
		data.clear();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#keySet()
	 */
	@Override
	public Set<String> keySet() {
		return data.keySet();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#values()
	 */
	@Override
	public Collection<Object> values() {
		return data.values();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#entrySet()
	 */
	@Override
	public Set<Entry<String, Object>> entrySet() {
		return data.entrySet();
	}

	@SuppressWarnings({"unchecked"})
	public <R> List<R> convertList(Object value, Function<Object, R> converter) {
		List<R> list = (List<R>) value;
		for (int i = 0; i < list.size(); ++i) {
			list.set(i, converter.apply(list.get(i)));
		}
		return list;
	}

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
	public String visitISO8601Date(Object value) {
		return (String) value;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.core.FieldTypeUntypedValueConverterVisitor#visitISO8601DateList(
	 * java.lang.Object)
	 */
	@Override
	public List<String> visitISO8601DateList(Object value) {
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
		if (value instanceof String) {
			return Double.valueOf((String) value);
		} else if (value instanceof Float) {
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
		if (value instanceof String) {
			return Float.parseFloat((String) value);
		} else if (value instanceof Double) {
			return (float) (double) value;
		} else {
			return (float) value;
		}
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
		if (value instanceof String) {
			return Long.valueOf((String) value);
		} else if (value instanceof Integer) {
			return ((Integer) value).longValue();
		} else if (value instanceof Short) {
			return ((Short) value).longValue();
		}
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
		if (value instanceof String) {
			return Short.valueOf((String) value);
		} else if (value instanceof Integer) {
			return ((Integer) value).shortValue();
		} else if (value instanceof Long) {
			return ((Long) value).shortValue();
		}
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
		if (value instanceof String) {
			return Integer.parseInt((String) value);
		} else if (value instanceof Long) {
			return ((Long) value).intValue();
		}
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
		return (value instanceof String) ? (String) value : value.toString();
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
}
