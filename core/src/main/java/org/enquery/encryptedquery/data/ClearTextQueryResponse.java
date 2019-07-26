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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClearTextQueryResponse extends AbstractIndentedToString {

	private static final Logger log = LoggerFactory.getLogger(ClearTextQueryResponse.class);

	public static class Field extends AbstractIndentedToString {
		private final String name;
		private final Object value;

		public Field(String name, Object value) {
			this.name = name;
			this.value = value;
		}

		public String getName() {
			return name;
		}

		public Object getValue() {
			return value;
		}

		@Override
		protected void toStringInner(StringBuilder builder, int indent) {
			builder.append(nSpaces(indent))
					.append("name='")
					.append(name)
					.append("', ")
					.append("value='")
					.append(value)
					.append("'");
		}
	}

	public static class Record extends AbstractIndentedToString {
		private Map<String, Field> fieldsMap = new HashMap<>();
		private Integer selector;

		@SuppressWarnings("unchecked")
		public void add(Field field) {
			final String fieldName = field.getName();
			final Object fieldValue = field.getValue();

			Field prev = fieldsMap.put(fieldName, field);
			if (prev != null) {
				List<Object> list = null;
				if (prev.value instanceof List) {
					list = (List<Object>) prev.value;
				} else {
					list = new ArrayList<>();
					list.add(prev.value);
				}
				list.add(fieldValue);
				fieldsMap.put(fieldName, new Field(fieldName, list));
			}
		}

		public void add(String fieldName, Object fieldValue) {
			Validate.notNull(fieldName);
			Validate.notNull(fieldValue);

			Field field = new Field(fieldName, fieldValue);
			add(field);
		}

		public void forEachField(Consumer<Field> action) {
			fieldsMap.values().forEach(action);
		}

		public Integer getSelector() {
			return selector;
		}

		public void setSelector(Integer selector) {
			this.selector = selector;
		}

		@Override
		public void toStringInner(StringBuilder builder, int indent) {
			final String prefix = nSpaces(indent);
			builder.append(prefix)
					.append("selector=")
					.append(selector)
					.append(",\n")
					.append(prefix)
					.append("fields [\n");
			collectionToString(fieldsMap.values(), builder, indent + INDENT);
			builder.append(prefix).append("]");
		}

		public int fieldCount() {
			return fieldsMap.size();
		}

		/**
		 * @param fieldName
		 * @return
		 */
		public Field fieldByName(String fieldName) {
			return fieldsMap.get(fieldName);
		}
	}

	public static class Hits extends AbstractIndentedToString {
		final private String selectorValue;
		final private List<Record> records = new ArrayList<>();

		public Hits(String selectorValue) {
			this.selectorValue = selectorValue;
		}

		public String getSelectorValue() {
			return selectorValue;
		}

		public void add(Record record) {
			Validate.notNull(record);
			if (log.isDebugEnabled()) log.debug("Adding record: {}", record);
			records.add(record);
		}

		public void add(Hits other) {
			Validate.notNull(other);
			Validate.notNull(other.records);
			Validate.isTrue(Objects.equals(this.selectorValue, other.selectorValue));

			this.records.addAll(other.records);
		}

		@Override
		public void toStringInner(StringBuilder builder, int indent) {
			String spaces = nSpaces(indent);
			builder
					.append(spaces)
					.append("selectorValue='")
					.append(selectorValue)
					.append("'\n")
					.append(spaces)
					.append("records [\n");
			collectionToString(records, builder, indent + INDENT);
			builder.append(spaces).append("]");
		}

		public void forEachRecord(Consumer<? super Record> action) {
			records.stream().forEach(action);
		}

		public int recordCount() {
			return records.size();
		}

		public Record recordByIndex(int index) {
			return records.get(index);
		}
	}

	public static class Selector extends AbstractIndentedToString {

		final private String name;
		final private Map<String, Hits> hits = new HashMap<>();

		public Selector(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}

		public void add(Selector other) {
			Validate.notNull(other);
			Validate.notNull(other.hits);
			Validate.isTrue(Objects.equals(this.name, other.name));

			other.hits.values().stream().forEach(h -> add(h));
		}

		public void add(Hits otherHits) {
			Validate.notNull(otherHits);
			Validate.notNull(otherHits.records);

			Hits prev = hits.get(otherHits.getSelectorValue());
			if (prev != null) {
				prev.add(otherHits);
			} else {
				hits.put(otherHits.getSelectorValue(), otherHits);
			}
		}

		public void forEachHits(Consumer<? super Hits> action) {
			hits.values().stream().filter(h -> h.recordCount() > 0).forEach(action);
		}

		public Hits hitsBySelectorValue(String selectorValue) {
			return hits.get(selectorValue);
		}

		@Override
		public void toStringInner(StringBuilder builder, int indent) {
			final String prefix = nSpaces(indent);
			builder.append(prefix)
					.append("name='")
					.append(name)
					.append("',\n")
					.append(prefix)
					.append("hits [\n");

			collectionToString(hits.values(), builder, indent + INDENT);
			builder.append(prefix).append("]");
		}

		public int hitCount() {
			return hits.size();
		}
	}

	final private String queryName;
	final private String queryId;
	final private Map<String, Selector> selectors = new HashMap<>();

	public ClearTextQueryResponse(String queryName, String queryId) {
		this.queryName = queryName;
		this.queryId = queryId;
	}


	public void add(Selector selector) {
		Validate.notNull(selector);

		if (log.isDebugEnabled()) log.debug("Adding selector: {}", selector);

		Selector prev = selectors.get(selector.getName());
		if (prev != null) {
			prev.add(selector);
		} else {
			selectors.put(selector.getName(), selector);
		}
	}

	public String getQueryName() {
		return queryName;
	}

	public String getQueryId() {
		return queryId;
	}

	public void add(ClearTextQueryResponse other) {
		Validate.notNull(other);
		Validate.isTrue(Objects.equals(this.queryId, other.queryId));

		other.selectors
				.forEach((n, v) -> {
					Selector selector = selectors.get(n);
					if (selector != null) {
						selector.add(v);
					} else {
						add(v);
					}
				});

	}

	public void forEach(Consumer<? super Selector> action) {
		selectors.values().forEach(action);
	}

	@Override
	protected void toStringInner(StringBuilder builder, int indent) {
		final String prefix = nSpaces(indent);
		builder
				.append(prefix)
				.append("queryName='")
				.append(queryName)
				.append("',\n")
				.append(prefix)
				.append("queryId='")
				.append(queryId)
				.append("',\n")
				.append(prefix)
				.append("selectors [\n");

		collectionToString(selectors.values(), builder, indent + INDENT);
		builder.append(prefix).append("]");
	}

	public int selectorCount() {
		return selectors.size();
	}

	public Selector selectorByName(String name) {
		return selectors.get(name);
	}
}
