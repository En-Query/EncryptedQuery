package org.enquery.encryptedquery.querier.data.transformation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.apache.commons.io.IOUtils;
import org.enquery.encryptedquery.querier.data.entity.jpa.Query;
import org.enquery.encryptedquery.querier.data.entity.jpa.Schedule;
import org.enquery.encryptedquery.querier.data.service.QueryRepository;
import org.enquery.encryptedquery.xml.schema.Execution;
import org.enquery.encryptedquery.xml.schema.ObjectFactory;
import org.enquery.encryptedquery.xml.transformation.XMLFactories;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

public class ExecutionConverterTest {

	private final Logger log = LoggerFactory.getLogger(ExecutionConverterTest.class);

	private static final String XSD_PATH = "target/dependency/org/enquery/encryptedquery/xml/schema/execution.xsd";

	private Schema xmlSchema;
	private JAXBContext jaxbContext;
	private ObjectFactory objectFactory;

	public ExecutionConverterTest() {
		SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
		try {
			xmlSchema = factory.newSchema(new File(XSD_PATH));
			jaxbContext = JAXBContext.newInstance(ObjectFactory.class);
		} catch (SAXException | JAXBException e) {
			throw new RuntimeException("Error initializing XSD schema.", e);
		}
		objectFactory = new ObjectFactory();
	}

	public void marshal(org.enquery.encryptedquery.xml.schema.Execution ex, OutputStream os) throws JAXBException {
		Marshaller marshaller = jaxbContext.createMarshaller();
		marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
		marshaller.marshal(objectFactory.createExecution(ex), os);
	}

	public org.enquery.encryptedquery.xml.schema.Execution unmarshal(InputStream fis) throws JAXBException {
		Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
		jaxbUnmarshaller.setSchema(xmlSchema);
		StreamSource source = new StreamSource(fis);
		JAXBElement<org.enquery.encryptedquery.xml.schema.Execution> element =
				jaxbUnmarshaller.unmarshal(source, org.enquery.encryptedquery.xml.schema.Execution.class);
		return element.getValue();
	}

	@Test
	public void test() throws JAXBException, FileNotFoundException, IOException, XMLStreamException {

		ExecutionConverter c = new ExecutionConverter();
		c.setQueryRepo(mockQueryRepo());
		c.setThreadPool(Executors.newCachedThreadPool());

		Schedule schedule = new Schedule();
		schedule.setStartTime(new Date());
		Query query = new Query();
		query.setId(1);
		schedule.setQuery(query);
		schedule.setParameters(makeParams());

		byte[] bytes = IOUtils.toByteArray(c.toExecutionXMLStream(schedule));
		log.info("Output: " + new String(bytes));

		Execution ex = unmarshal(new ByteArrayInputStream(bytes));
		assertNotNull(ex.getScheduledFor());
		assertEquals(XMLFactories.toXMLTime(schedule.getStartTime()), ex.getScheduledFor());
		assertNotNull(ex.getQuery());
		assertNotNull(ex.getConfiguration());
		assertEquals(2, ex.getConfiguration().getEntry().size());
	}

	private String makeParams() {
		Map<String, String> result = new HashMap<>();
		result.put("setting1", "value1");
		result.put("setting2", "value2");

		return JSONConverter.toString(result);
	}

	private QueryRepository mockQueryRepo() throws FileNotFoundException, IOException {
		QueryRepository mock = org.mockito.Mockito.mock(QueryRepository.class);
		org.mockito.Mockito.when(mock.loadQueryBytes(1)).thenReturn(
				new FileInputStream("src/test/resources/query.xml"));
		return mock;
	}

}
