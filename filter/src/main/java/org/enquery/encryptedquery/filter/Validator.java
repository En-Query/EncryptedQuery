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

import java.util.List;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.data.DataSchema;
import org.enquery.encryptedquery.data.validation.FilterValidator;
import org.enquery.encryptedquery.filter.error.ErrorListener;
import org.enquery.encryptedquery.filter.predicate.PredicateFactory;
import org.joo.libra.Predicate;
import org.osgi.service.component.annotations.Component;

/**
 *
 */
@Component
public class Validator implements FilterValidator {

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.data.validation.FilterValidator#validate(java.lang.String,
	 * org.enquery.encryptedquery.data.DataSchema)
	 */
	@Override
	public void validate(String exp, DataSchema dataSchema) {
		ErrorListener listener = new ErrorListener();
		Predicate predicate = PredicateFactory.make(exp, dataSchema, listener);

		Validate.isTrue(predicate != null && listener.getErrors().isEmpty(),
				"Error parsing filter expression: %s",
				concatErrors(listener.getErrors()));

	}

	public static String concatErrors(List<String> errors) {
		final StringBuilder sb = new StringBuilder();
		errors.stream().forEach(s -> {
			if (sb.length() > 0) sb.append("\n");
			sb.append(s);
		});
		return sb.toString();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.data.validation.FilterValidator#isValid(java.lang.String,
	 * org.enquery.encryptedquery.data.DataSchema)
	 */
	@Override
	public boolean isValid(String exp, DataSchema dataSchema) {
		return collectErrors(exp, dataSchema).isEmpty();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.data.validation.FilterValidator#collectSyntaxErrors(java.lang.
	 * String, org.enquery.encryptedquery.data.DataSchema)
	 */
	@Override
	public List<String> collectErrors(String exp, DataSchema dataSchema) {
		ErrorListener listener = new ErrorListener();
		PredicateFactory.make(exp, dataSchema, listener);
		return listener.getErrors();
	}

}
