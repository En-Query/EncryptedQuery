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
package org.enquery.encryptedquery.querier.data.entity.json;

public class QuerySchemaField {

	private String name;
	private Integer size;
	private Integer maxArrayElements;

	public QuerySchemaField() {}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Integer getSize() {
		return size;
	}

	public void setSize(Integer size) {
		this.size = size;
	}

	public Integer getMaxArrayElements() {
		return maxArrayElements;
	}

	public void setMaxArrayElements(Integer maxArrayElements) {
		this.maxArrayElements = maxArrayElements;
	}

	@Override
	public String toString() {
		StringBuilder output = new StringBuilder();
		output.append("\n  Name: " + name + "\n");
		output.append("  size: " + size + "\n");
		output.append("  maxArrayElements: " + maxArrayElements);

		return output.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + name.hashCode();
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
		QuerySchemaField other = (QuerySchemaField) obj;
		if (name != other.name) {
			return false;
		}
		return true;
	}

}
