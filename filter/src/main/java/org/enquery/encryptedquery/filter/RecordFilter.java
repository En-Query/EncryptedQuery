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
package org.enquery.encryptedquery.filter;

import java.util.Map;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.data.DataSchema;
import org.enquery.encryptedquery.filter.error.ErrorListener;
import org.enquery.encryptedquery.filter.predicate.PredicateFactory;
import org.joo.libra.Predicate;
import org.joo.libra.PredicateContext;

/**
 * Filter input records using SQL-like expression
 */
public class RecordFilter {

	private Predicate predicate;

	/**
	 * 
	 */
	public RecordFilter(String filterExp) {
		this(filterExp, null);
	}

	/**
	 * 
	 */
	public RecordFilter(String filterExp, DataSchema dataSchema) {
		ErrorListener listener = new ErrorListener();
		predicate = PredicateFactory.make(filterExp, dataSchema, listener);
		Validate.isTrue(predicate != null && listener.getErrors().isEmpty(),
				"Error evaluating filter expression: %s",
				Validator.concatErrors(listener.getErrors()));
	}

	public boolean satisfiesFilter(Map<String, Object> record) {
		PredicateContext context = new PredicateContext(record);
		return predicate.satisfiedBy(context);
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("RecordFilter [predicate=").append(predicate).append("]");
		return builder.toString();
	}


}
