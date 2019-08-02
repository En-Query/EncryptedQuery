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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;

import javax.xml.bind.JAXBException;
import javax.xml.stream.XMLStreamException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.junit.Test;

/**
 *
 */
public class ResultExportReaderTest {

	@Test
	public void test() throws FileNotFoundException, IOException, XMLStreamException, JAXBException {

		Path resultFile = Paths.get("src/test/resources/result-export.xml");
		try (InputStream inputStream = Files.newInputStream(resultFile);
				ResultExportReader reader = new ResultExportReader(Executors.newCachedThreadPool());) {

			reader.parse(inputStream);

			// First Item
			assertTrue(reader.hasNextItem());
			reader.nextItem();
			assertEquals(303, reader.getExecutionId());
			assertEquals("48b852c3ea9f42eabb3eaca5703641d4", reader.getExecutionUUID());
			assertEquals("/responder/api/rest/dataschemas/2/datasources/203/executions/303", reader.getExecutionSelfUri());
			assertEquals("2019-03-26T20:36:17.977Z", reader.getExecutionScheduledFor().toString());

			// First Item, First result
			assertTrue(reader.hasNextResult());

			try (ResultReader resultReader = reader.nextResult();
					InputStream responseInputStream = resultReader.getResponseInputStream()) {
				assertEquals(1, resultReader.getResultId());
				assertEquals("result uri", resultReader.getResultUri());
				IOUtils.copy(responseInputStream, new NullOutputStream());
			}

			assertFalse(reader.hasNextResult());

			// Second Item
			assertTrue(reader.hasNextItem());
			reader.nextItem();

			assertEquals(304, reader.getExecutionId());
			assertEquals("48b852c3ea9f42eabb3eaca5703641c5", reader.getExecutionUUID());
			assertEquals("/responder/api/rest/dataschemas/2/datasources/203/executions/304", reader.getExecutionSelfUri());
			assertEquals("2019-03-27T20:36:17.977Z", reader.getExecutionScheduledFor().toString());
			assertEquals("there was an error running this query", reader.getExecutionErrorMsg());

			assertFalse(reader.hasNextResult());
			assertFalse(reader.hasNextItem());
		}
	}
}
