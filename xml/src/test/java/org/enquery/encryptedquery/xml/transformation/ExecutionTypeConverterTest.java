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
package org.enquery.encryptedquery.xml.transformation;

import static org.junit.Assert.assertEquals;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import javax.xml.bind.JAXBException;

import org.enquery.encryptedquery.xml.schema.Execution;
import org.junit.Test;

/**
 *
 */
public class ExecutionTypeConverterTest {

	@Test
	public void testExecution() throws FileNotFoundException, IOException, JAXBException {
		try (InputStream fis = new FileInputStream("src/test/resources/execution.xml")) {
			ExecutionTypeConverter etc = new ExecutionTypeConverter();

			Execution ex = etc.unmarshalExecution(fis);
			assertEquals("3e7c5e594e424cde88c9e531a5dccb06", ex.getUuid());
		}
	}
}
