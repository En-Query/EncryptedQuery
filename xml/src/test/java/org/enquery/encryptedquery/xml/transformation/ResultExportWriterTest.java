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
import java.util.List;
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
import org.enquery.encryptedquery.xml.schema.Execution;
import org.enquery.encryptedquery.xml.schema.ExecutionResource;
import org.enquery.encryptedquery.xml.schema.ExecutionWithResults;
import org.enquery.encryptedquery.xml.schema.ObjectFactory;
import org.enquery.encryptedquery.xml.schema.ResultExport;
import org.enquery.encryptedquery.xml.schema.ResultResource;
import org.junit.Test;
import org.xml.sax.SAXException;

/**
 *
 */
public class ResultExportWriterTest {


	private static final String XSD_PATH = "src/main/resources/org/enquery/encryptedquery/xml/schema/result-export.xsd";

	private Schema xmlSchema;
	private JAXBContext jaxbContext;

	private XMLGregorianCalendar time;

	public ResultExportWriterTest() {
		SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
		try {
			xmlSchema = factory.newSchema(new File(XSD_PATH));
			jaxbContext = JAXBContext.newInstance(ObjectFactory.class);
		} catch (SAXException | JAXBException e) {
			throw new RuntimeException("Error initializing Query XSD schema.", e);
		}
	}

	@Test
	public void test() throws FileNotFoundException, IOException, XMLStreamException, JAXBException {

		time = XMLFactories.toUTCXMLTime(new Date());

		Path resultFile = Paths.get("target/result-export.xml");
		Path responseFile = Paths.get("src/test/resources/response.xml");
		Execution execution = makeSampleExecution();
		ExecutionResource exResource = makeSampleExecutionResource(execution);
		ResultResource result = makeSampleResource();

		try (OutputStream out = Files.newOutputStream(resultFile);
				InputStream payloadInputStream = Files.newInputStream(responseFile);
				ResultExportWriter writer = new ResultExportWriter(out);) {

			writer.begin(true);
			writer.beginItem();
			writer.writeExecutionResource(exResource);
			writer.beginResults();
			writer.writeResultResource(result, payloadInputStream);
			writer.writeResultResource(result, null);
			writer.endResults();
			writer.endItem();

			writer.beginItem();
			writer.writeExecutionResource(exResource);
			writer.endItem();

			writer.end();
		}

		validate(resultFile);
	}

	/**
	 * @param execution
	 * @return
	 */
	private ExecutionResource makeSampleExecutionResource(Execution execution) {
		ExecutionResource result = new ExecutionResource();
		result.setExecution(execution);
		result.setId(101);
		result.setSelfUri("execution uri");
		result.setDataSourceUri("data source uri");
		result.setResultsUri("results uri");
		return result;
	}

	/**
	 * @return
	 */
	private Execution makeSampleExecution() {
		Execution result = new Execution();
		result.setSchemaVersion(Versions.EXECUTION_BI);
		result.setUuid("48b852c3ea9f42eabb3eaca5703641d4");
		result.setScheduledFor(time);
		result.setSubmittedOn(time);
		result.setStartedOn(time);
		result.setCompletedOn(time);
		return result;
	}

	/**
	 * @param resultF
	 * @throws IOException
	 * @throws JAXBException
	 */
	private void validate(Path file) throws IOException, JAXBException {
		try (InputStream inputStream = Files.newInputStream(file)) {
			ResultExport resultImport = unmarshal(inputStream);
			assertEquals(2, resultImport.getItem().size());

			ExecutionWithResults item = resultImport.getItem().get(0);
			ExecutionResource executionResource = item.getExecutionResource();
			assertEquals(101, executionResource.getId());
			assertEquals("execution uri", executionResource.getSelfUri());
			assertEquals("data source uri", executionResource.getDataSourceUri());
			assertEquals("results uri", executionResource.getResultsUri());

			Execution ex = executionResource.getExecution();
			assertEquals("48b852c3ea9f42eabb3eaca5703641d4", ex.getUuid());
			assertEquals(time, ex.getScheduledFor());
			assertEquals(time, ex.getSubmittedOn());
			assertEquals(time, ex.getStartedOn());
			assertEquals(time, ex.getCompletedOn());

			List<ResultResource> list = item.getResults().getResultResource();
			assertEquals(2, list.size());

			ResultResource result = list.get(0);
			assertEquals(1, result.getId());
			assertEquals("result uri", result.getSelfUri());
			assertNotNull(result.getPayload());

			result = list.get(1);
			assertEquals(1, result.getId());
			assertEquals("result uri", result.getSelfUri());
			assertNull(result.getPayload());
		}
	}

	/**
	 * @return
	 */
	private ResultResource makeSampleResource() {
		ResultResource result = new ResultResource();
		result.setId(1);
		result.setSelfUri("result uri");
		result.setCreatedOn(time);
		result.setWindowStart(time);
		result.setWindowEnd(time);

		// ExecutionResource executionRes = new ExecutionResource();
		// executionRes.setId(2);
		// executionRes.setSelfUri("execution uri");
		// executionRes.setResultsUri("results uri");
		// executionRes.setDataSourceUri("data source uri");

		Execution ex = new Execution();
		ex.setSchemaVersion(Versions.EXECUTION_BI);
		ex.setUuid(UUID.randomUUID().toString().replace("-", ""));
		ex.setCompletedOn(time);
		ex.setStartedOn(time);
		ex.setScheduledFor(time);
		// executionRes.setExecution(ex);
		// result.setExecution(executionRes);
		return result;
	}


	public org.enquery.encryptedquery.xml.schema.ResultExport unmarshal(InputStream fis) throws JAXBException {
		Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
		jaxbUnmarshaller.setSchema(xmlSchema);
		StreamSource source = new StreamSource(fis);
		JAXBElement<org.enquery.encryptedquery.xml.schema.ResultExport> element =
				jaxbUnmarshaller.unmarshal(source, org.enquery.encryptedquery.xml.schema.ResultExport.class);
		return element.getValue();
	}
}
