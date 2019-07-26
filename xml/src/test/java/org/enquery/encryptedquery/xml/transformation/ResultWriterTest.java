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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;

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

import org.enquery.encryptedquery.xml.schema.ObjectFactory;
import org.enquery.encryptedquery.xml.schema.Resource;
import org.enquery.encryptedquery.xml.schema.ResultResource;
import org.junit.Test;
import org.xml.sax.SAXException;

/**
 *
 */
public class ResultWriterTest {


	private static final String XSD_PATH = "src/main/resources/org/enquery/encryptedquery/xml/schema/result-resources.xsd";

	private Schema xmlSchema;
	private JAXBContext jaxbContext;

	public ResultWriterTest() {
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

		Path resultFile = Paths.get("target/results.xml");
		Path responseFile = Paths.get("src/test/resources/response.xml");
		ResultResource result = makeSampleResource();

		try (OutputStream out = Files.newOutputStream(resultFile);
				InputStream payloadInputStream = Files.newInputStream(responseFile);
				ResultWriter rw = new ResultWriter(out);) {

			rw.writeResult(result, payloadInputStream, true);
		}

		validate(resultFile);
	}

	/**
	 * @param resultF
	 * @throws IOException
	 * @throws JAXBException
	 */
	private void validate(Path file) throws IOException, JAXBException {
		try (InputStream inputStream = Files.newInputStream(file)) {
			ResultResource result = unmarshal(inputStream);
			assertEquals(1, result.getId());
			assertEquals("result uri", result.getSelfUri());
			assertEquals("execution uri", result.getExecution().getSelfUri());
		}
	}

	/**
	 * @return
	 */
	private ResultResource makeSampleResource() {
		ResultResource result = new ResultResource();
		result.setId(1);
		result.setSelfUri("result uri");
		XMLGregorianCalendar time = XMLFactories.toUTCXMLTime(new Date());
		result.setCreatedOn(time);
		result.setWindowStart(time);
		result.setWindowEnd(time);
		Resource executionRes = new Resource();
		executionRes.setId(2);
		executionRes.setSelfUri("execution uri");

		result.setExecution(executionRes);
		return result;
	}


	public org.enquery.encryptedquery.xml.schema.ResultResource unmarshal(InputStream fis) throws JAXBException {
		Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
		jaxbUnmarshaller.setSchema(xmlSchema);
		StreamSource source = new StreamSource(fis);
		JAXBElement<org.enquery.encryptedquery.xml.schema.ResultResource> element =
				jaxbUnmarshaller.unmarshal(source, org.enquery.encryptedquery.xml.schema.ResultResource.class);
		return element.getValue();
	}
}
