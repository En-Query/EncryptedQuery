package org.encryptedquery.responder.data.transformation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.enquery.encryptedquery.xml.schema.ObjectFactory;
import org.enquery.encryptedquery.xml.schema.Query;
import org.enquery.encryptedquery.xml.transformation.ExecutionXMLExtractor;
import org.junit.Test;
import org.xml.sax.SAXException;

public class ExecutionXMLExtractorTest {

	private static final String QUERY_XSD_PATH = "target/dependency/org/enquery/encryptedquery/xml/schema/query.xsd";

	private Schema xmlSchema;
	private JAXBContext jaxbContext;
	private ObjectFactory objectFactory;

	public ExecutionXMLExtractorTest() {
		SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

		// URL resource = getClass().getResource(QUERY_XSD_PATH);
		// Validate.notNull(resource);
		try {
			xmlSchema = factory.newSchema(new File(QUERY_XSD_PATH));
			jaxbContext = JAXBContext.newInstance(ObjectFactory.class);
		} catch (SAXException | JAXBException e) {
			throw new RuntimeException("Error initializing Query XSD schema.", e);
		}
		objectFactory = new ObjectFactory();
	}

	public void marshal(org.enquery.encryptedquery.xml.schema.Query q, OutputStream os) throws JAXBException {
		Marshaller marshaller = jaxbContext.createMarshaller();
		marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
		marshaller.marshal(objectFactory.createQuery(q), os);
	}

	public org.enquery.encryptedquery.xml.schema.Query unmarshal(InputStream fis) throws JAXBException {
		Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
		jaxbUnmarshaller.setSchema(xmlSchema);
		StreamSource source = new StreamSource(fis);
		JAXBElement<org.enquery.encryptedquery.xml.schema.Query> element =
				jaxbUnmarshaller.unmarshal(source, org.enquery.encryptedquery.xml.schema.Query.class);
		return element.getValue();
	}

	@Test
	public void xmlParse() throws FileNotFoundException, IOException, Exception {
		ExecutorService es = Executors.newCachedThreadPool();

		try (InputStream fis = new FileInputStream("src/test/resources/execution.xml");
				ExecutionXMLExtractor extractor = new ExecutionXMLExtractor(es);) {

			extractor.parse(fis);
			assertNotNull(extractor.getScheduleDate());
			assertNotNull(extractor.getQueryInputStream());
			assertNotNull(extractor.getConfig());

			assertEquals("sample.txt", extractor.getConfig().get("input"));
			assertEquals("result.xml", extractor.getConfig().get("output"));

			Query query = unmarshal(extractor.getQueryInputStream());
			assertEquals("7d7cb756-2699-4174-a697-6db8e89b8e23", query.getQueryInfo().getIdentifier());
			assertEquals(5, query.getQueryElements().getEntry().size());


			// ByteArrayOutputStream output = new ByteArrayOutputStream();
			// IOUtils.copy(extractor.getQueryInputStream(), output);
			// log.info(new String(output.toByteArray()));
		}
	}
}
