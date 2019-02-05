package org.encryptedquery.responder.data.transformation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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
import java.util.GregorianCalendar;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.enquery.encryptedquery.xml.schema.Configuration;
import org.enquery.encryptedquery.xml.schema.Configuration.Entry;
import org.enquery.encryptedquery.xml.schema.DataSchema;
import org.enquery.encryptedquery.xml.schema.DataSchema.Field;
import org.enquery.encryptedquery.xml.schema.Execution;
import org.enquery.encryptedquery.xml.schema.ObjectFactory;
import org.enquery.encryptedquery.xml.schema.Query;
import org.enquery.encryptedquery.xml.schema.Query.QueryElements;
import org.enquery.encryptedquery.xml.schema.QueryInfo;
import org.enquery.encryptedquery.xml.schema.QuerySchema;
import org.enquery.encryptedquery.xml.transformation.ExecutionXMLExtractor;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

public class ExecutionXMLExtractorTest {

	private static final String ENTRY_VALUE_PATTERN = "entry value %d";
	private static final String QUERY_ID = "7d7cb756-2699-4174-a697-6db8e89b8e23";
	private static final String QUERY_XSD_PATH = "target/dependency/org/enquery/encryptedquery/xml/schema/query.xsd";
	private static final Path EXECUTION_FILE_NAME = Paths.get("target/execution.xml");
	private final Logger log = LoggerFactory.getLogger(ExecutionXMLExtractorTest.class);

	private Schema xmlSchema;
	private JAXBContext jaxbContext;
	private ObjectFactory objectFactory;


	@Before
	public void prepare() throws Exception {
		log.info("Start initializing test.");

		Files.deleteIfExists(EXECUTION_FILE_NAME);
		SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

		try {
			xmlSchema = factory.newSchema(new File(QUERY_XSD_PATH));
			jaxbContext = JAXBContext.newInstance(ObjectFactory.class);
		} catch (SAXException | JAXBException e) {
			throw new RuntimeException("Error initializing Query XSD schema.", e);
		}

		objectFactory = new ObjectFactory();

		try (OutputStream os = new FileOutputStream(EXECUTION_FILE_NAME.toFile())) {
			marshal(makeExecution(), os);
		}

		log.info("Finished initializing test.");
	}


	/**
	 * @return
	 * @throws Exception
	 */
	private Execution makeExecution() throws Exception {
		DatatypeFactory dtf = DatatypeFactory.newInstance();
		Execution execution = new Execution();
		execution.setScheduledFor(dtf.newXMLGregorianCalendar(new GregorianCalendar()));
		execution.setQuery(makeQuery());
		execution.setConfiguration(makeConfiguration());
		return execution;
	}

	/**
	 * @return
	 */
	private Configuration makeConfiguration() {
		Configuration result = new Configuration();
		result.getEntry().add(makeEntry("input", "sample.txt"));
		result.getEntry().add(makeEntry("output", "result.xml"));
		return result;
	}

	private Entry makeEntry(String key, String value) {
		Entry entry = new Entry();
		entry.setKey(key);
		entry.setValue(value);
		return entry;
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
		result.setQueryId(QUERY_ID);
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


	public void marshal(Execution ex, OutputStream os) throws JAXBException {
		Marshaller marshaller = jaxbContext.createMarshaller();
		marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
		marshaller.marshal(objectFactory.createExecution(ex), os);
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

		try (InputStream fis = new FileInputStream(EXECUTION_FILE_NAME.toFile());
				ExecutionXMLExtractor extractor = new ExecutionXMLExtractor(es);) {

			extractor.parse(fis);
			assertNotNull(extractor.getScheduleDate());
			assertNotNull(extractor.getQueryInputStream());
			assertNotNull(extractor.getConfig());

			assertEquals("sample.txt", extractor.getConfig().get("input"));
			assertEquals("result.xml", extractor.getConfig().get("output"));

			Query query = unmarshal(extractor.getQueryInputStream());
			assertEquals(QUERY_ID, query.getQueryInfo().getQueryId());
			assertEquals(5, query.getQueryElements().getEntry().size());

			int index = 0;
			for (org.enquery.encryptedquery.xml.schema.Query.QueryElements.Entry e : query.getQueryElements().getEntry()) {
				assertEquals(index, e.getKey());
				String value = new String(e.getValue());
				assertEquals(String.format(ENTRY_VALUE_PATTERN, index), value);
				++index;
			}
		}
	}
}
