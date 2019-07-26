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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.core.FieldType;

public class QuerySchema implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -7587522994594336783L;
	private String name;
	private DataSchema dataSchema;
	private String selectorField;
	private Map<String, QuerySchemaElement> elements = new LinkedHashMap<>();
	private List<QuerySchemaElement> elementList = new ArrayList<>();

	public QuerySchema() {}

	public void validate() {
		Validate.notNull(elements);
		Validate.notNull(elementList);
		Validate.isTrue(elementList.size() > 0, "At least 1 data field must be selected in the query schema.");
		Validate.noNullElements(elementList);
		Validate.notBlank(name);
		Validate.notNull(dataSchema);
		Validate.notBlank(selectorField);
		Validate.notNull(this.getElement(selectorField), "Selector field '%s' not in list of query schema fields.", selectorField);
		validateElementSizes(this);
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setDataSchema(DataSchema dataSchema) {
		this.dataSchema = dataSchema;
	}

	public DataSchema getDataSchema() {
		return dataSchema;
	}

	public String getDataSchemaName() {
		return dataSchema.getName();
	}

	public void setSelectorField(String selectorField) {
		this.selectorField = selectorField;
	}

	public String getSelectorField() {
		return selectorField;
	}

	/**
	 * Returns the Elements from the query schema
	 * 
	 * @return
	 */
	public Map<String, QuerySchemaElement> getElements() {
		return elements;
	}

	/**
	 * Returns a specific Element from the query schema based on the given element name
	 * 
	 * @param elementName
	 * @return QuerySchemaElement
	 */
	public QuerySchemaElement getElement(String elementName) {
		Validate.notNull(elementName);
		for (QuerySchemaElement schemaElement : elementList) {
			if (elementName.equals(schemaElement.getName())) {
				return schemaElement;
			}
		}
		return null;
	}

	public void addElement(QuerySchemaElement element) {
		elements.put(element.getName(), element);
		elementList.add(element);
	}

	public List<String> getElementNames() {

		List<String> elementNames = new ArrayList<>();
		for (QuerySchemaElement qse : elementList) {
			elementNames.add(qse.getName());
		}
		if (elementNames.size() > 0) {
			return elementNames;
		}
		Set<Entry<String, QuerySchemaElement>> entrySet = elements.entrySet();
		Iterator<Entry<String, QuerySchemaElement>> it = entrySet.iterator();
		while (it.hasNext()) {
			elementNames.add(it.next().getKey().toString());
		}

		return elementNames;

	}

	public List<QuerySchemaElement> getElementList() {
		return elementList;
	}

	public void setElementList(List<QuerySchemaElement> elementList) {
		this.elementList = elementList;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("QuerySchema [name=").append(name).append("]");
		builder.append("\n      [Elements=").append(elements.size()).append("]");
		builder.append("\n      [ElementList=").append(elementList.size()).append("]");
		return builder.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		QuerySchema other = (QuerySchema) obj;
		if (name == null) {
			if (other.name != null) {
				return false;
			}
		} else if (!name.equals(other.name)) {
			return false;
		}
		return true;
	}

	private void validateElementSizes(QuerySchema qs) {
		for (QuerySchemaElement qse : qs.getElementList()) {

			String elementName = qse.getName();
			Validate.notNull(elementName);

			FieldType dataType = qs.getDataSchema().elementByName(elementName).getDataType();
			if (dataType == FieldType.STRING || dataType == FieldType.BYTEARRAY) {
				if (qse.getSize() != null && qse.getSize() < 1) {
					throw new RuntimeException(
							"QuerySchemaElement '" + elementName + "' size (" + qse.getSize() + ") must be > 0");
				}
				// } else {
				// int elementSize = FieldTypeUtils.getSizeByType(dataType);
				// if (!qse.getLengthType().equalsIgnoreCase("fixed")) {
				// throw new RuntimeException("QuerySchemaElement " + qse.getName() + " of data type
				// (" + dataType
				// + ") must be 'Fixed' length Type");
				// }
				// if (qse.getSize() != elementSize) {
				// throw new RuntimeException("QuerySchemaElement " + qse.getName() + " size (" +
				// qse.getSize()
				// + ") does not match data type size (" + elementSize + ")");
				// }
			}
		}
	}
}
