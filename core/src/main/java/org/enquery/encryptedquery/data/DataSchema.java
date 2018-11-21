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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.Validate;

public class DataSchema implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 5649492402213159302L;
	private String name;
	private Map<String, DataSchemaElement> elementsByName = new LinkedHashMap<>();
	private Map<Integer, DataSchemaElement> elementsByPosition = new LinkedHashMap<>();
	private List<DataSchemaElement> elementList = new ArrayList<DataSchemaElement>();


	public DataSchema() {}

	public void validate() {
		Validate.notBlank(name);
		Validate.notNull(elementsByName);
		Validate.notNull(elementList);
		Validate.isTrue(elementList.size() > 0);
		Validate.noNullElements(elementList);
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void addElement(DataSchemaElement element) {
		elementsByName.put(element.getName(), element);
		elementsByPosition.put(element.getPosition(), element);
		elementList.add(element);
	}

	public int elementCount() {
		return elementList.size();
	}

	/**
	 * Returns a specific Data Element from the data schema based on the given element name
	 * 
	 * @param elementName
	 * @return DataSchemaElement
	 */
	public DataSchemaElement elementByName(String elementName) {
		return elementsByName.get(elementName);
	}

	public DataSchemaElement elementByPosition(int position) {
		DataSchemaElement result = elementsByPosition.get(position);
		Validate.notNull(result);
		return result;
	}

	public Set<String> elementNames() {
		return elementsByName.keySet();
	}

	public List<DataSchemaElement> elements() {
		return elementList;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("DataSchema [name=").append(name).append("]");
		builder.append("\n      [Elements=").append(elementsByName.size()).append("]");
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
		DataSchema other = (DataSchema) obj;
		if (name == null) {
			if (other.name != null) {
				return false;
			}
		} else if (!name.equals(other.name)) {
			return false;
		}
		return true;
	}
}
