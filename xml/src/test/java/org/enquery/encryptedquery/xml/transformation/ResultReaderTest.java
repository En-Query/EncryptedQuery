package org.enquery.encryptedquery.xml.transformation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

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
import org.enquery.encryptedquery.xml.schema.Response;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

public class ResultReaderTest {

	private static Logger log = LoggerFactory.getLogger(ResultReaderTest.class);

	private static final String XSD_PATH = "src/main/resources/org/enquery/encryptedquery/xml/schema/response.xsd";

	private Schema xmlSchema;
	private JAXBContext jaxbContext;

	private ExecutorService es;

	public ResultReaderTest() {
		SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
		try {
			xmlSchema = factory.newSchema(new File(XSD_PATH));
			jaxbContext = JAXBContext.newInstance(ObjectFactory.class);
		} catch (SAXException | JAXBException e) {
			throw new RuntimeException("Error initializing Query XSD schema.", e);
		}
		es = Executors.newCachedThreadPool();
	}


	@Test
	public void parseWithPayload() throws FileNotFoundException, IOException, Exception {

		try (InputStream fis = new FileInputStream("src/test/resources/result-resource.xml");
				ResultReader reader = new ResultReader(es);) {

			reader.parse(fis);

			Date expectedDate = XMLFactories.toUTCDate(XMLFactories.dtf.newXMLGregorianCalendar("2018-11-12T15:31:05.000-05:00"));
			assertEquals(4, reader.getResultId());
			assertEquals("/responder/api/rest/dataschemas/5/datasources/5/executions/5/results/4", reader.getResultUri());

			assertEquals(expectedDate, reader.getCreationDate());

			assertEquals(303, reader.getExecutionId());
			assertEquals("/responder/api/rest/dataschemas/2/datasources/203/executions/303", reader.getExecutionUri());

			ByteArrayOutputStream output = new ByteArrayOutputStream();
			try (InputStream inputStream = reader.getResponseInputStream();) {
				assertNotNull(inputStream);
				IOUtils.copy(inputStream, output);
			}

			log.info(new String(output.toByteArray()));

			try (InputStream inputStream = new ByteArrayInputStream(output.toByteArray());) {
				Response query = unmarshal(inputStream);
				assertEquals("8d008ed7-92cd-4f7a-9ea5-f93db1753532", query.getQueryInfo().getQueryId());
				assertEquals("{n=10,modulusBitSize=35}", new String(query.getQueryInfo().getPublicKey()));
			}

		}
	}

	@Test
	public void parseNoPayload() throws FileNotFoundException, IOException, Exception {

		try (InputStream fis = new FileInputStream("src/test/resources/result-resource-no-payload.xml");
				ResultReader reader = new ResultReader(es);) {

			reader.parse(fis);

			Date expectedDate = XMLFactories.toUTCDate(XMLFactories.dtf.newXMLGregorianCalendar("2018-11-12T15:31:05.000-05:00"));
			assertEquals(4, reader.getResultId());
			assertEquals("/responder/api/rest/dataschemas/5/datasources/5/executions/5/results/4", reader.getResultUri());

			assertEquals(expectedDate, reader.getCreationDate());

			assertEquals(5, reader.getExecutionId());
			assertEquals("/responder/api/rest/dataschemas/5/datasources/5/executions/5", reader.getExecutionUri());

			assertNull(reader.getResponseInputStream());
		}
	}

	public org.enquery.encryptedquery.xml.schema.Response unmarshal(InputStream fis) throws JAXBException {
		Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
		jaxbUnmarshaller.setSchema(xmlSchema);
		StreamSource source = new StreamSource(fis);
		JAXBElement<org.enquery.encryptedquery.xml.schema.Response> element =
				jaxbUnmarshaller.unmarshal(source, org.enquery.encryptedquery.xml.schema.Response.class);
		return element.getValue();
	}
}
