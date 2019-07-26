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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.UUID;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.enquery.encryptedquery.xml.Versions;
import org.enquery.encryptedquery.xml.schema.Configuration;
import org.enquery.encryptedquery.xml.schema.Configuration.Entry;
import org.enquery.encryptedquery.xml.schema.Execution;
import org.enquery.encryptedquery.xml.schema.ObjectFactory;
import org.junit.Test;
import org.xml.sax.SAXException;

/**
 *
 */
public class ExecutionWriterTest {


	private static final String XSD_PATH = "src/main/resources/org/enquery/encryptedquery/xml/schema/execution.xsd";

	private Schema xmlSchema;
	private JAXBContext jaxbContext;

	public ExecutionWriterTest() {
		SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
		try {
			xmlSchema = factory.newSchema(new File(XSD_PATH));
			jaxbContext = JAXBContext.newInstance(ObjectFactory.class);
		} catch (SAXException | JAXBException e) {
			throw new RuntimeException("Error initializing Query XSD schema.", e);
		}
	}

	@Test
	public void testNoQueryNoConfig() throws FileNotFoundException, IOException, XMLStreamException, JAXBException {

		Path resultFile = Paths.get("target/execution.xml");
		Execution result = makeSampleResource();

		try (OutputStream out = Files.newOutputStream(resultFile);
				ExecutionWriter writer = new ExecutionWriter(out);) {

			writer.writeExecution(true, result, null);
		}

		try (InputStream inputStream = Files.newInputStream(resultFile)) {
			Execution e = unmarshal(inputStream);
			assertNotNull(e.getUuid());
			assertNotNull(e.getScheduledFor());
			assertNull(e.getConfiguration());
			assertNull(e.getQuery());
		}
	}

	@Test
	public void testWithQueryAndConfig() throws FileNotFoundException, IOException, XMLStreamException, JAXBException {

		Path resultFile = Paths.get("target/execution.xml");
		Path queryFile = Paths.get("src/test/resources/query.xml");
		Execution result = makeSampleResource();
		Configuration config = new Configuration();
		Entry entry = new Entry();
		entry.setKey("foo");
		entry.setValue("bar");
		config.getEntry().add(entry);
		result.setConfiguration(config);

		try (OutputStream out = Files.newOutputStream(resultFile);
				ExecutionWriter writer = new ExecutionWriter(out);
				InputStream queryInputStream = Files.newInputStream(queryFile)) {
			writer.writeExecution(true, result, queryInputStream);
		}

		try (InputStream inputStream = Files.newInputStream(resultFile)) {
			Execution e = unmarshal(inputStream);
			assertNotNull(e.getUuid());
			assertNotNull(e.getScheduledFor());
			assertNotNull(e.getQuery());
			assertNotNull(e.getConfiguration());
			assertEquals("foo", e.getConfiguration().getEntry().get(0).getKey());
			assertEquals("bar", e.getConfiguration().getEntry().get(0).getValue());
		}
	}

	/**
	 * @return
	 */
	private Execution makeSampleResource() {
		Execution result = new Execution();
		result.setSchemaVersion(Versions.EXECUTION_BI);
		XMLGregorianCalendar time = XMLFactories.toUTCXMLTime(new Date());
		result.setScheduledFor(time);
		result.setUuid(UUID.randomUUID().toString().replace("-", ""));
		result.setCompletedOn(time);
		result.setErrorMessage("error");
		result.setSubmittedOn(time);
		result.setStartedOn(time);
		return result;
	}

	public org.enquery.encryptedquery.xml.schema.Execution unmarshal(InputStream fis) throws JAXBException {
		Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
		jaxbUnmarshaller.setSchema(xmlSchema);
		StreamSource source = new StreamSource(fis);
		JAXBElement<org.enquery.encryptedquery.xml.schema.Execution> element =
				jaxbUnmarshaller.unmarshal(source, org.enquery.encryptedquery.xml.schema.Execution.class);
		return element.getValue();
	}
}
