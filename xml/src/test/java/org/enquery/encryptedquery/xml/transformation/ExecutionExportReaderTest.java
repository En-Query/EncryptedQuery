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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.apache.commons.io.IOUtils;
import org.enquery.encryptedquery.xml.schema.ObjectFactory;
import org.enquery.encryptedquery.xml.schema.Query;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

/**
 *
 */
public class ExecutionExportReaderTest {

	private static Logger log = LoggerFactory.getLogger(ResultReaderTest.class);

	private static final String XSD_PATH = "src/main/resources/org/enquery/encryptedquery/xml/schema/execution-export.xsd";

	private Schema xmlSchema;
	private JAXBContext jaxbContext;

	private ExecutorService es;

	public ExecutionExportReaderTest() {
		SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
		try {
			xmlSchema = factory.newSchema(new File(XSD_PATH));
			jaxbContext = JAXBContext.newInstance(ObjectFactory.class);
		} catch (SAXException | JAXBException e) {
			throw new RuntimeException("Error initializing XSD schema.", e);
		}
		es = Executors.newCachedThreadPool();
	}


	@Test
	public void parseWithPayload() throws FileNotFoundException, IOException, Exception {

		try (InputStream fis = new FileInputStream("src/test/resources/execution-export.xml");
				ExecutionExportReader reader = new ExecutionExportReader(es);) {

			reader.parse(fis);

			// parse first execution
			assertTrue(reader.hasNextItem());
			try (ExecutionReader executionReader = reader.next();) {
				assertEquals("Books Data Schema", reader.getDataSchemaName());
				assertEquals("Books JDBC Data Source", reader.getDataSourceName());
				validateExecution(executionReader, "3e7c5e59-4e42-4cde-88c9-e531a5dccb06", "2019-03-07T16:56:18.934-05:00");
			}

			// parse second execution
			assertTrue(reader.hasNextItem());
			try (ExecutionReader executionReader = reader.next();) {
				assertEquals("Books Data Schema 2", reader.getDataSchemaName());
				assertEquals("Books JSON Data Source", reader.getDataSourceName());
				validateExecution(executionReader, "4e7c5e59-4e42-4cde-88c9-e531a5dccb06", "2019-03-08T13:00:00.000-05:00");
			}

			// no more executions in file
			assertFalse(reader.hasNextItem());
		}
	}


	private void validateExecution(ExecutionReader executionReader, String expectedId, String expectedDateStr) throws IOException, JAXBException {
		Date expectedDate = XMLFactories.toUTCDate(XMLFactories.dtf.newXMLGregorianCalendar(expectedDateStr));
		assertEquals(expectedDate, executionReader.getScheduleDate());

		ByteArrayOutputStream output = new ByteArrayOutputStream();
		InputStream inputStream = executionReader.getQueryInputStream();
		assertNotNull(inputStream);

		IOUtils.copy(inputStream, output);
		log.info(new String(output.toByteArray()));

		InputStream is = new ByteArrayInputStream(output.toByteArray());
		Query query = unmarshal(is);

		assertEquals(expectedId, query.getQueryInfo().getQueryId());
	}

	public org.enquery.encryptedquery.xml.schema.Query unmarshal(InputStream fis) throws JAXBException {
		Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
		jaxbUnmarshaller.setSchema(xmlSchema);
		StreamSource source = new StreamSource(fis);
		JAXBElement<org.enquery.encryptedquery.xml.schema.Query> element =
				jaxbUnmarshaller.unmarshal(source, org.enquery.encryptedquery.xml.schema.Query.class);
		return element.getValue();
	}

}
