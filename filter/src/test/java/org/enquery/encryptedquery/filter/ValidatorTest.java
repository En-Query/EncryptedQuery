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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.util.List;

import org.enquery.encryptedquery.core.FieldType;
import org.enquery.encryptedquery.data.DataSchema;
import org.enquery.encryptedquery.data.DataSchemaElement;
import org.junit.Test;

/**
 *
 */
public class ValidatorTest {


	@Test
	public void validNoSchema() {
		Validator validator = new Validator();
		validator.isValid("a > b", null);
	}

	@Test
	public void validWithSchema() {
		Validator validator = new Validator();
		validator.isValid("value < 30", makeSchema());
	}

	@Test
	public void invalidNoSchema() {
		Validator validator = new Validator();
		boolean valid = validator.isValid("a ~= b", null);
		assertFalse(valid);
	}

	@Test
	public void invalidWithSchema() {
		Validator validator = new Validator();
		boolean valid = validator.isValid("value1 == 0", makeSchema());
		assertFalse(valid);
	}

	@Test
	public void collectError() {
		Validator validator = new Validator();
		List<String> errors = validator.collectErrors("value1 == 0", makeSchema());
		assertNotNull(errors);
		assertFalse(errors.isEmpty());
	}

	private DataSchema makeSchema() {
		DataSchema dataSchema = new DataSchema();
		DataSchemaElement element = new DataSchemaElement();
		element.setDataType(FieldType.INT);
		element.setName("value");
		element.setPosition(0);
		dataSchema.addElement(element);
		return dataSchema;
	}
}
