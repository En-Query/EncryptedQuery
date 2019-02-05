package org.enquery.encryptedquery.querier.data.transformation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import org.enquery.encryptedquery.querier.data.entity.jpa.Schedule;
import org.enquery.encryptedquery.querier.data.service.QueryRepository;
import org.enquery.encryptedquery.xml.schema.DataSchema;
import org.enquery.encryptedquery.xml.schema.DataSchema.Field;
import org.enquery.encryptedquery.xml.schema.Execution;
import org.enquery.encryptedquery.xml.schema.ObjectFactory;
import org.enquery.encryptedquery.xml.schema.Query;
import org.enquery.encryptedquery.xml.schema.Query.QueryElements;
import org.enquery.encryptedquery.xml.schema.QueryInfo;
import org.enquery.encryptedquery.xml.schema.QuerySchema;
import org.enquery.encryptedquery.xml.transformation.XMLFactories;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

public class ExecutionConverterTest {

	private final Logger log = LoggerFactory.getLogger(ExecutionConverterTest.class);

	private static final String EXECUTION_SCHEMA_FILE = "target/dependency/org/enquery/encryptedquery/xml/schema/execution.xsd";
	private static final Path QUERY_FILE_NAME = Paths.get("target/query.xml");
	private static final String ENTRY_VALUE_PATTERN = "entry value %d";

	private Schema xmlSchema;
	private JAXBContext jaxbContext;
	private ObjectFactory objectFactory;


	@Before
	public void prepare() throws Exception {
		log.info("Start initializing test.");

		Files.deleteIfExists(QUERY_FILE_NAME);
		SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
		try {
			xmlSchema = factory.newSchema(new File(EXECUTION_SCHEMA_FILE));
			jaxbContext = JAXBContext.newInstance(ObjectFactory.class);
		} catch (JAXBException | SAXException e) {
			throw new RuntimeException("Error initializing XSD schema.", e);
		}
		objectFactory = new ObjectFactory();

		try (OutputStream os = new FileOutputStream(QUERY_FILE_NAME.toFile())) {
			marshal(makeQuery(), os);
		}

		log.info("Finished initializing test.");
	}


	public void marshal(org.enquery.encryptedquery.xml.schema.Query query, OutputStream os) throws JAXBException {
		Marshaller marshaller = jaxbContext.createMarshaller();
		marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
		marshaller.marshal(objectFactory.createQuery(query), os);
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

		ExecutionConverter converter = new ExecutionConverter();
		converter.setQueryRepo(mockQueryRepo());
		converter.setThreadPool(Executors.newCachedThreadPool());

		Schedule schedule = new Schedule();
		schedule.setStartTime(new Date());
		org.enquery.encryptedquery.querier.data.entity.jpa.Query query = //
				new org.enquery.encryptedquery.querier.data.entity.jpa.Query();

		query.setId(1);
		schedule.setQuery(query);
		schedule.setParameters(makeParams());

		byte[] bytes = IOUtils.toByteArray(converter.toExecutionXMLStream(schedule));
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
				new FileInputStream(QUERY_FILE_NAME.toFile()));
		return mock;
	}


	private Query makeQuery() {
		Query result = new Query();
		result.setSchemaVersion(new BigDecimal("2.0"));
		result.setQueryInfo(makeQueryInfo());
		result.setQueryElements(makeQueryElements());
		return result;
	}

	/**
	 * @return
	 */
	private QueryElements makeQueryElements() {
		QueryElements result = new QueryElements();

		for (int i = 0; i < 5; ++i) {
			result.getEntry().add(makeQueryEntry(i));
		}

		return result;
	}


	private org.enquery.encryptedquery.xml.schema.Query.QueryElements.Entry makeQueryEntry(int index) {
		org.enquery.encryptedquery.xml.schema.Query.QueryElements.Entry entry = //
				new org.enquery.encryptedquery.xml.schema.Query.QueryElements.Entry();
		entry.setKey(index);
		entry.setValue(String.format(ENTRY_VALUE_PATTERN, index).getBytes());
		return entry;
	}


	private QueryInfo makeQueryInfo() {
		QueryInfo result = new QueryInfo();
		result.setCryptoSchemeId("Paillier");
		result.setDataChunkSize(8);
		result.setEmbedSelector(true);
		result.setHashBitSize(12);
		result.setHashKey("hash key");
		result.setNumBitsPerDataElement(8);
		result.setNumPartitionsPerDataElement(10);
		result.setNumSelectors(1);
		result.setPublicKey("public key".getBytes());
		result.setQueryId("test query id");
		result.setQueryName("test");
		result.setQuerySchema(makeQuerySchema());
		return result;
	}

	private QuerySchema makeQuerySchema() {
		QuerySchema result = new QuerySchema();
		result.setName("Query Schema Test");
		result.setSelectorField("field");
		org.enquery.encryptedquery.xml.schema.QuerySchema.Field field = //
				new org.enquery.encryptedquery.xml.schema.QuerySchema.Field();

		field.setName("field");
		field.setLengthType("fixed");
		field.setMaxArrayElements(12);
		field.setSize(33);

		result.getField().add(field);
		result.setDataSchema(makeDataSchema());
		return result;
	}

	private DataSchema makeDataSchema() {
		DataSchema result = new DataSchema();
		result.setName("Test Data Schema");
		Field field = new Field();
		field.setName("field");
		field.setDataType("string");
		field.setIsArray(false);
		result.getField().add(field);
		return result;
	}

}
