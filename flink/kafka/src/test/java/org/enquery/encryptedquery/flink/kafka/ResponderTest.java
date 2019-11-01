package org.enquery.encryptedquery.flink.kafka;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import javax.xml.bind.JAXBException;

import org.apache.commons.io.FileUtils;
import org.apache.flink.configuration.AkkaOptions;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.CoreOptions;
import org.apache.flink.configuration.TaskManagerOptions;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.enquery.encryptedquery.core.FieldType;
import org.enquery.encryptedquery.data.ClearTextQueryResponse;
import org.enquery.encryptedquery.data.ClearTextQueryResponse.Field;
import org.enquery.encryptedquery.data.ClearTextQueryResponse.Hits;
import org.enquery.encryptedquery.data.ClearTextQueryResponse.Record;
import org.enquery.encryptedquery.data.ClearTextQueryResponse.Selector;
import org.enquery.encryptedquery.data.DataSchema;
import org.enquery.encryptedquery.data.DataSchemaElement;
import org.enquery.encryptedquery.data.Query;
import org.enquery.encryptedquery.data.QuerySchema;
import org.enquery.encryptedquery.data.QuerySchemaElement;
import org.enquery.encryptedquery.data.Response;
import org.enquery.encryptedquery.data.validation.FilterValidator;
import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.enquery.encryptedquery.encryption.CryptoSchemeRegistry;
import org.enquery.encryptedquery.encryption.paillier.PaillierCryptoScheme;
import org.enquery.encryptedquery.flink.FlinkConfigurationProperties;
import org.enquery.encryptedquery.flink.FlinkTypes;
import org.enquery.encryptedquery.querier.decrypt.DecryptResponse;
import org.enquery.encryptedquery.querier.encrypt.EncryptQuery;
import org.enquery.encryptedquery.querier.encrypt.Querier;
import org.enquery.encryptedquery.utils.FileIOUtils;
import org.enquery.encryptedquery.utils.PIRException;
import org.enquery.encryptedquery.utils.RandomProvider;
import org.enquery.encryptedquery.xml.transformation.ClearTextResponseTypeConverter;
import org.enquery.encryptedquery.xml.transformation.QueryTypeConverter;
import org.enquery.encryptedquery.xml.transformation.ResponseTypeConverter;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ResponderTest implements FlinkTypes {

	private static final Logger log = LoggerFactory.getLogger(ResponderTest.class);
	private static final Integer DATA_CHUNK_SIZE = 2;
	private static final Integer HASH_BIT_SIZE = 8;
	private static final Path RESPONSE_FILE_NAME = Paths.get("target/response.xml");
	private static final Path QUERY_FILE_NAME = Paths.get("target/query.xml");
	private static final Path CONFIG_FILE_NAME = Paths.get("target/test-classes/", "config.properties");
	private static final Path PCAP_TWO_WINDOWS_FILE_NAME = Paths.get("target/test-classes/", "pcap-data-two-windows.json");
	private static final Path PCAP_NO_DELAY_FILE_NAME = Paths.get("target/test-classes/", "pcap-data-no-delay.json");
	private static final Path TWITTER_NO_DELAY_FILE_NAME = Paths.get("target/test-classes/", "twitter-data-no-delay.json");
	private static final Path TWITTER_TWO_WINDOWS_FILE_NAME = Paths.get("target/test-classes/", "twitter-data-two-windows.json");

	public static final int modulusBitSize = 384;
	public static final int certainty = 128;

	private QueryTypeConverter queryConverter;
	private ResponseTypeConverter responseConverter;
	private Map<String, String> config;
	private PaillierCryptoScheme crypto;
	private DecryptResponse decryptor;
	private ClearTextResponseTypeConverter clearTextResponseConverter;

	@Before
	public void before() throws Exception {

		// For streaming, output file name is a directory containing each window results,
		// create it early
		FileUtils.deleteDirectory(RESPONSE_FILE_NAME.toFile());
		// Files.createDirectories(RESPONSE_FILE_NAME);
		Files.deleteIfExists(QUERY_FILE_NAME);

		config = FileIOUtils.loadPropertyFile(CONFIG_FILE_NAME);
		crypto = new PaillierCryptoScheme();
		crypto.initialize(config);

		CryptoSchemeRegistry cryptoRegistry = new CryptoSchemeRegistry() {
			@Override
			public CryptoScheme cryptoSchemeByName(String schemeId) {
				if (schemeId.equals(crypto.name())) {
					return crypto;
				}
				return null;
			}
		};

		queryConverter = new QueryTypeConverter();
		queryConverter.setCryptoRegistry(cryptoRegistry);
		queryConverter.initialize();

		responseConverter = new ResponseTypeConverter();
		responseConverter.setQueryConverter(queryConverter);
		responseConverter.setSchemeRegistry(cryptoRegistry);
		responseConverter.initialize();

		decryptor = new DecryptResponse();
		decryptor.setCryptoRegistry(cryptoRegistry);
		decryptor.setExecutionService(Executors.newCachedThreadPool());

		clearTextResponseConverter = new ClearTextResponseTypeConverter();
	}

	@After
	public void after() throws Exception {
		if (crypto != null) {
			crypto.close();
			crypto = null;
		}
	}

	@Test
	public void twitterSingleResponse() throws Exception {

		// no delay between records, they all fit in the same window
		log.info("Running Kafka Twitter Test");
		QuerySchema querySchema = createTwitterQuerySchema();
		Querier querier = createQuerier(Collections.singletonList("Apple"), querySchema);
		runJob(TWITTER_NO_DELAY_FILE_NAME, querySchema, 10, null, querier.getQuery());

		List<ClearTextQueryResponse> responses = decryptResponses(querier);
		assertEquals(1, responses.size());

		ClearTextQueryResponse answer = responses.get(0);
		log.info(answer.toString());
		Selector selector = answer.selectorByName("text");
		assertEquals(1, answer.selectorCount());
		assertNotNull(selector);
		Hits hits = selector.hitsBySelectorValue("Apple");
		assertNotNull(hits);
		assertEquals(1, hits.recordCount());
		Record record = hits.recordByIndex(0);
		assertNotNull(record);
		assertEquals(2, record.fieldCount());
		Field id = record.fieldByName("id");
		assertNotNull(id);
		assertEquals(1082283985504366591L, id.getValue());
		Field array = record.fieldByName("array");
		assertNotNull(array);
		assertEquals(Arrays.asList(1, 2, 3), array.getValue());
	}

	@Test
	public void twitterAllRecordsFiltered() throws Exception {

		// no delay between records, they all fit in the same window
		QuerySchema querySchema = createTwitterQuerySchema();
		Querier querier = createQuerier(Collections.singletonList("Apple"), querySchema, "timestamp_ms > 1545153625284");
		runJob(TWITTER_NO_DELAY_FILE_NAME, querySchema, 10, null, querier.getQuery());

		List<ClearTextQueryResponse> responses = decryptResponses(querier);
		assertEquals(1, responses.size());

		ClearTextQueryResponse answer = responses.get(0);
		log.info(answer.toString());
		assertEquals(1, answer.selectorCount());
		Selector selector = answer.selectorByName("text");
		assertNotNull(selector);
		Hits hits = selector.hitsBySelectorValue("Apple");
		assertNotNull(hits);
		assertEquals(0, hits.recordCount());
	}

	@Test
	public void twitterFilteredAllButOne() throws Exception {

		// no delay between records, they all fit in the same window
		QuerySchema querySchema = createTwitterQuerySchema();
		Querier querier = createQuerier(Collections.singletonList("Pear"), querySchema, "timestamp_ms > 1545153625283");
		runJob(TWITTER_NO_DELAY_FILE_NAME, querySchema, 10, null, querier.getQuery());

		List<ClearTextQueryResponse> responses = decryptResponses(querier);
		assertEquals(1, responses.size());

		ClearTextQueryResponse answer = responses.get(0);
		log.info(answer.toString());
		assertEquals(1, answer.selectorCount());
		Selector selector = answer.selectorByName("text");
		assertNotNull(selector);
		Hits hits = selector.hitsBySelectorValue("Pear");
		assertNotNull(hits);
		assertEquals(1, hits.recordCount());
	}

	/**
	 * @param singletonList
	 * @param querySchema
	 * @param string
	 * @return
	 * @throws PIRException
	 * @throws InterruptedException
	 * @throws IOException
	 * @throws FileNotFoundException
	 * @throws JAXBException
	 */
	private Querier createQuerier(List<String> selectors, QuerySchema querySchema, String filter) throws InterruptedException, PIRException, FileNotFoundException, IOException, JAXBException {
		RandomProvider randomProvider = new RandomProvider();
		EncryptQuery queryEnc = new EncryptQuery();
		queryEnc.setCrypto(crypto);
		queryEnc.setRandomProvider(randomProvider);
		queryEnc.setFilterValidator(new FilterValidator() {

			@Override
			public void validate(String exp, DataSchema dataSchema) {}

			@Override
			public boolean isValid(String exp, DataSchema dataSchema) {
				return true;
			}

			@Override
			public List<String> collectErrors(String exp, DataSchema dataSchema) {
				return null;
			}
		});

		Querier result = queryEnc.encrypt(querySchema, selectors, DATA_CHUNK_SIZE, HASH_BIT_SIZE, filter);

		try (OutputStream os = new FileOutputStream(QUERY_FILE_NAME.toFile())) {
			queryConverter.marshal(queryConverter.toXMLQuery(result.getQuery()), os);
		}
		return result;
	}

	@Test
	public void twitterTwoWindows() throws Exception {

		// no delay between records, they all fit in the same window
		log.info("Running Kafka Twitter Test");
		QuerySchema querySchema = createTwitterQuerySchema();
		Querier querier = createQuerier(Collections.singletonList("Apple"), querySchema);
		runJob(TWITTER_TWO_WINDOWS_FILE_NAME, querySchema, 10, null, querier.getQuery());

		List<ClearTextQueryResponse> responses = decryptResponses(querier);
		assertEquals(2, responses.size());

		// first window should not have any result
		ClearTextQueryResponse answer = responses.get(0);
		log.info(answer.toString());
		Selector selector = answer.selectorByName("text");
		assertEquals(1, answer.selectorCount());
		assertNotNull(selector);
		assertEquals(1, selector.hitCount());
		Hits hits = selector.hitsBySelectorValue("Apple");
		assertNotNull(hits);
		assertEquals(0, hits.recordCount());

		// second window has a hit
		answer = responses.get(1);
		log.info(answer.toString());
		selector = answer.selectorByName("text");
		assertEquals(1, answer.selectorCount());
		assertNotNull(selector);
		hits = selector.hitsBySelectorValue("Apple");
		assertNotNull(hits);
		assertEquals(1, hits.recordCount());
		Record record = hits.recordByIndex(0);
		assertNotNull(record);
		assertEquals(2, record.fieldCount());
		Field id = record.fieldByName("id");
		assertNotNull(id);
		assertEquals(1082283985504366591L, id.getValue());
		Field array = record.fieldByName("array");
		assertNotNull(array);
		assertEquals(Arrays.asList(1, 2, 3), array.getValue());
	}


	@Test
	public void pCapMultipleResponses() throws Exception {
		log.info("Running Kafka Pcap Multiple Test");

		List<String> selectors = Collections.singletonList("eth:ethertype:ip:tcp:mysql");
		QuerySchema querySchema = createPcapQuerySchema();

		Querier querier = createQuerier(selectors, querySchema);

		// run for max 1 minute with 10s windows
		runJob(PCAP_TWO_WINDOWS_FILE_NAME, querySchema, 10, 60, querier.getQuery());
		// runJob(Paths.get("/Users/asoto/pcap-data-12k.json"), querySchema, 10, 60);

		List<ClearTextQueryResponse> responses = decryptResponses(querier);
		assertEquals(2, responses.size());

		// first window should found two records
		ClearTextQueryResponse answer = responses.get(0);
		log.info(answer.toString());
		Selector selector = answer.selectorByName("_source|layers|frame|frame.protocols");
		assertEquals(1, answer.selectorCount());
		assertNotNull(selector);
		assertEquals(1, selector.hitCount());
		Hits hits = selector.hitsBySelectorValue("eth:ethertype:ip:tcp:mysql");
		assertNotNull(hits);
		assertEquals(2, hits.recordCount());

		Record record = hits.recordByIndex(0);
		assertNotNull(record);
		assertEquals(5, record.fieldCount());

		Field ipSrc = record.fieldByName("_source|layers|ip|ip.src");
		assertNotNull(ipSrc);
		assertTrue("192.168.142.1".equals(ipSrc.getValue()) || "192.168.142.128".equals(ipSrc.getValue()));

		record = hits.recordByIndex(1);
		assertNotNull(record);
		assertEquals(5, record.fieldCount());

		ipSrc = record.fieldByName("_source|layers|ip|ip.src");
		assertNotNull(ipSrc);
		assertTrue("192.168.142.1".equals(ipSrc.getValue()) || "192.168.142.128".equals(ipSrc.getValue()));

		// second window has no hits
		answer = responses.get(1);
		log.info(answer.toString());
		selector = answer.selectorByName("_source|layers|frame|frame.protocols");
		assertEquals(1, answer.selectorCount());
		assertNotNull(selector);
		hits = selector.hitsBySelectorValue("eth:ethertype:ip:tcp:mysql");
		assertNotNull(hits);
		assertEquals(0, hits.recordCount());
	}

	@Test
	public void pCapSingleResponse() throws Exception {

		QuerySchema querySchema = createPcapQuerySchema();
		Querier querier = createQuerier(Collections.singletonList("eth:ethertype:ip:tcp:mysql"), querySchema);

		// run for max 100s, no delay reading the file, all records go into a single response
		runJob(PCAP_NO_DELAY_FILE_NAME, querySchema, 10, 30, querier.getQuery());

		List<ClearTextQueryResponse> responses = decryptResponses(querier);
		assertEquals(1, responses.size());

		// first window should found two records
		ClearTextQueryResponse answer = responses.get(0);
		log.info(answer.toString());
		Selector selector = answer.selectorByName("_source|layers|frame|frame.protocols");
		assertEquals(1, answer.selectorCount());
		assertNotNull(selector);
		assertEquals(1, selector.hitCount());
		Hits hits = selector.hitsBySelectorValue("eth:ethertype:ip:tcp:mysql");
		assertNotNull(hits);
		// max hits per selector is set to 10
		assertEquals(10, hits.recordCount());

		for (int i = 0; i < hits.recordCount(); ++i) {
			Record record = hits.recordByIndex(i);
			assertNotNull(record);
			assertEquals(5, record.fieldCount());
			Field ipSrc = record.fieldByName("_source|layers|ip|ip.src");
			assertNotNull(ipSrc);
			assertTrue("192.168.142.1".equals(ipSrc.getValue()) || "192.168.142.128".equals(ipSrc.getValue()));
		}
	}

	private void runJob(Path fileName, QuerySchema querySchema, int windowSize, Integer maxRuntime, Query query) throws Exception {

		config.put(FlinkConfigurationProperties.WINDOW_LENGTH_IN_SECONDS, Integer.toString(windowSize));
		if (maxRuntime == null) {
			config.remove(FlinkConfigurationProperties.STREAM_RUNTIME_SECONDS);
		} else {
			config.put(FlinkConfigurationProperties.STREAM_RUNTIME_SECONDS, Long.toString(maxRuntime));
		}

		config.put(FlinkConfigurationProperties.MAX_HITS_PER_SELECTOR, "10");

		try (Responder responder = new Responder()) {
			responder.setBufferSize(100);
			responder.setConfig(config);
			responder.setInputFileName(QUERY_FILE_NAME);
			responder.setOutputFileName(RESPONSE_FILE_NAME);
			responder.initializeCommon();
			responder.initializeStreaming();

			FileTestSource source = new FileTestSource(fileName,
					RESPONSE_FILE_NAME,
					responder.getMaxTimestamp(),
					Time.seconds(windowSize),
					query);

			Configuration cfg = new Configuration();
			cfg.setString(AkkaOptions.ASK_TIMEOUT, "2 min");
			cfg.setString(AkkaOptions.CLIENT_TIMEOUT, "2 min");
			cfg.setInteger(CoreOptions.DEFAULT_PARALLELISM, 4);
			cfg.setInteger(TaskManagerOptions.NUM_TASK_SLOTS, 4);

			StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment(4, cfg);

			responder.runWithSourceAndEnvironment(source, env);
			log.info("Flink job ended.");
			assertTrue(!Files.exists(RESPONSE_FILE_NAME.resolve("job-running")));
		}
	}

	@Test
	@Ignore("manually run, requires external kafka")
	public void kafkaLiveTest() throws Exception {
		try (Responder responder = new Responder()) {
			responder.setConfig(config);
			responder.setInputFileName(QUERY_FILE_NAME);
			responder.setOutputFileName(RESPONSE_FILE_NAME);
			responder.setBrokers("192.168.200.57:9092");
			responder.run();
		}
	}


	private Querier createQuerier(List<String> selectors, QuerySchema querySchema) throws Exception {
		return createQuerier(selectors, querySchema, null);
	}

	private QuerySchema createTwitterQuerySchema() {
		DataSchema ds = new DataSchema();
		ds.setName("Tweets");

		DataSchemaElement dse1 = new DataSchemaElement();
		dse1.setName("timestamp_ms");
		dse1.setDataType(FieldType.LONG);
		dse1.setPosition(0);
		ds.addElement(dse1);

		DataSchemaElement dse2 = new DataSchemaElement();
		dse2.setName("id");
		dse2.setDataType(FieldType.LONG);
		dse2.setPosition(1);
		ds.addElement(dse2);

		DataSchemaElement dse3 = new DataSchemaElement();
		dse3.setName("text");
		dse3.setDataType(FieldType.STRING);
		dse3.setPosition(2);
		ds.addElement(dse3);

		DataSchemaElement dse4 = new DataSchemaElement();
		dse4.setName("array");
		dse4.setDataType(FieldType.INT_LIST);
		dse4.setPosition(3);
		ds.addElement(dse4);

		QuerySchema qs = new QuerySchema();
		qs.setName("Tweets");
		qs.setSelectorField("text");
		qs.setDataSchema(ds);

		QuerySchemaElement field1 = new QuerySchemaElement();
		field1.setName("id");
		qs.addElement(field1);

		QuerySchemaElement field2 = new QuerySchemaElement();
		field2.setName("text");
		qs.addElement(field2);

		QuerySchemaElement field3 = new QuerySchemaElement();
		field3.setName("array");
		qs.addElement(field3);

		qs.validate();
		return qs;
	}

	private static class SortableResponse {
		private final int from;
		private final ClearTextQueryResponse answer;

		/**
		 * 
		 */
		public SortableResponse(String fileName, ClearTextQueryResponse answer) {
			this.answer = answer;
			String[] parts = fileName.split("-");
			from = Integer.valueOf(parts[0]);
		}
	}

	private List<ClearTextQueryResponse> decryptResponses(Querier querier) throws IOException, InterruptedException {
		List<SortableResponse> list = Files.walk(RESPONSE_FILE_NAME)
				.filter(file -> Files.isRegularFile(file) && file.toString().endsWith(".xml"))
				.map(file -> {
					log.info("Loading file: {}", file);
					try (FileInputStream fis = new FileInputStream(file.toFile())) {
						org.enquery.encryptedquery.xml.schema.Response xml = responseConverter.unmarshal(fis);
						Response response = responseConverter.toCore(xml);
						ClearTextQueryResponse answer = decryptor.decrypt(response, querier.getQueryKey());

						String name = file.getFileName().toString().replace(".xml", "-clear.xml");
						Path clearFileName = file.getParent().resolve(name);
						try (OutputStream os = new FileOutputStream(clearFileName.toFile())) {
							clearTextResponseConverter.marshal(
									clearTextResponseConverter.toXML(answer),
									os);
						}
						return new SortableResponse(file.getFileName().toString(), answer);
					} catch (Exception e) {
						throw new RuntimeException("error loading response: " + file, e);
					}
				})
				.collect(Collectors.toList());

		list.sort((a, b) -> Integer.compare(a.from, b.from));

		return list.stream()
				.map(a -> a.answer)
				.collect(Collectors.toList());
	}


	private QuerySchema createPcapQuerySchema() {
		DataSchema ds = new DataSchema();
		ds.setName("pcap");
		DataSchemaElement dse1 = new DataSchemaElement();
		dse1.setName("_index");
		dse1.setDataType(FieldType.STRING);
		dse1.setPosition(0);
		ds.addElement(dse1);

		DataSchemaElement dse2 = new DataSchemaElement();
		dse2.setName("_score");
		dse2.setDataType(FieldType.STRING);
		dse2.setPosition(1);
		ds.addElement(dse2);

		DataSchemaElement dse3 = new DataSchemaElement();
		dse3.setName("_source|layers|eth|eth.dst");
		dse3.setDataType(FieldType.STRING);
		dse3.setPosition(2);
		ds.addElement(dse3);

		DataSchemaElement dse4 = new DataSchemaElement();
		dse4.setName("_source|layers|eth|eth.src");
		dse4.setDataType(FieldType.STRING);
		dse4.setPosition(3);
		ds.addElement(dse4);

		DataSchemaElement dse5 = new DataSchemaElement();
		dse5.setName("_source|layers|eth|eth.type");
		dse5.setDataType(FieldType.STRING);
		dse5.setPosition(4);
		ds.addElement(dse5);

		DataSchemaElement dse6 = new DataSchemaElement();
		dse6.setName("_source|layers|frame|frame.protocols");
		dse6.setDataType(FieldType.STRING);
		dse6.setPosition(5);
		ds.addElement(dse6);

		DataSchemaElement dse7 = new DataSchemaElement();
		dse7.setName("_source|layers|frame|frame.time_epoch");
		dse7.setDataType(FieldType.STRING);
		dse7.setPosition(6);
		ds.addElement(dse7);

		DataSchemaElement dse9 = new DataSchemaElement();
		dse9.setName("_source|layers|ip|ip.dst");
		dse9.setDataType(FieldType.STRING);
		dse9.setPosition(7);
		ds.addElement(dse9);

		DataSchemaElement dse10 = new DataSchemaElement();
		dse10.setName("_source|layers|ip|ip.dst_host");
		dse10.setDataType(FieldType.STRING);
		dse10.setPosition(8);
		ds.addElement(dse10);

		DataSchemaElement dse11 = new DataSchemaElement();
		dse11.setName("_source|layers|ip|ip.src");
		dse11.setDataType(FieldType.STRING);
		dse11.setPosition(9);
		ds.addElement(dse11);

		DataSchemaElement dse12 = new DataSchemaElement();
		dse12.setName("_source|layers|ip|ip.src_host");
		dse12.setDataType(FieldType.STRING);
		dse12.setPosition(10);
		ds.addElement(dse12);

		DataSchemaElement dse13 = new DataSchemaElement();
		dse13.setName("_source|layers|tcp|tcp.flags_tree|tcp.flags.syn_tree|_ws.expert|_ws.expert.message");
		dse13.setDataType(FieldType.STRING);
		dse13.setPosition(11);
		ds.addElement(dse13);

		DataSchemaElement dse14 = new DataSchemaElement();
		dse14.setName("_source|layers|tcp|tcp.payload");
		dse14.setDataType(FieldType.STRING);
		dse14.setPosition(12);
		ds.addElement(dse14);

		DataSchemaElement dse15 = new DataSchemaElement();
		dse15.setName("_source|layers|tcp|tcp.srcport");
		dse15.setDataType(FieldType.STRING);
		dse15.setPosition(13);
		ds.addElement(dse15);

		DataSchemaElement dse16 = new DataSchemaElement();
		dse16.setName("_source|layers|tcp|tcp.dstport");
		dse16.setDataType(FieldType.STRING);
		dse16.setPosition(14);
		ds.addElement(dse16);

		DataSchemaElement dse17 = new DataSchemaElement();
		dse17.setName("_type");
		dse17.setDataType(FieldType.STRING);
		dse17.setPosition(15);
		ds.addElement(dse17);

		QuerySchema qs = new QuerySchema();
		qs.setName("pcap");
		qs.setSelectorField("_source|layers|frame|frame.protocols");
		qs.setDataSchema(ds);

		QuerySchemaElement field1 = new QuerySchemaElement();
		field1.setName("_source|layers|frame|frame.protocols");
		qs.addElement(field1);

		QuerySchemaElement field2 = new QuerySchemaElement();
		field2.setName("_source|layers|ip|ip.dst");
		qs.addElement(field2);

		QuerySchemaElement field3 = new QuerySchemaElement();
		field3.setName("_source|layers|ip|ip.src");
		qs.addElement(field3);

		QuerySchemaElement field4 = new QuerySchemaElement();
		field4.setName("_source|layers|tcp|tcp.srcport");
		qs.addElement(field4);

		QuerySchemaElement field5 = new QuerySchemaElement();
		field5.setName("_source|layers|tcp|tcp.dstport");
		qs.addElement(field5);

		QuerySchemaElement field6 = new QuerySchemaElement();
		field6.setName("_source|layers|tcp|tcp.payload");
		qs.addElement(field6);

		return qs;
	}
}
