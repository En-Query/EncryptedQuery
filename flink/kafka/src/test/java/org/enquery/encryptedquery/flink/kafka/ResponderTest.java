package org.enquery.encryptedquery.flink.kafka;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.enquery.encryptedquery.core.FieldTypes;
import org.enquery.encryptedquery.data.ClearTextQueryResponse;
import org.enquery.encryptedquery.data.DataSchema;
import org.enquery.encryptedquery.data.DataSchemaElement;
import org.enquery.encryptedquery.data.QuerySchema;
import org.enquery.encryptedquery.data.QuerySchemaElement;
import org.enquery.encryptedquery.data.Response;
import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.enquery.encryptedquery.encryption.CryptoSchemeRegistry;
import org.enquery.encryptedquery.encryption.paillier.PaillierCryptoScheme;
import org.enquery.encryptedquery.flink.BaseQueryExecutor;
import org.enquery.encryptedquery.flink.FlinkTypes;
import org.enquery.encryptedquery.querier.decrypt.DecryptResponse;
import org.enquery.encryptedquery.querier.encrypt.EncryptQuery;
import org.enquery.encryptedquery.querier.encrypt.Querier;
import org.enquery.encryptedquery.responder.ResponderProperties;
import org.enquery.encryptedquery.utils.FileIOUtils;
import org.enquery.encryptedquery.utils.RandomProvider;
import org.enquery.encryptedquery.xml.transformation.ClearTextResponseTypeConverter;
import org.enquery.encryptedquery.xml.transformation.QueryTypeConverter;
import org.enquery.encryptedquery.xml.transformation.ResponseTypeConverter;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ResponderTest implements FlinkTypes {

	private static final Logger log = LoggerFactory.getLogger(ResponderTest.class);
	// private static final String TOPIC = "twitter-feed";
	private static final Integer DATA_CHUNK_SIZE = 2;
	private static final Integer HASH_BIT_SIZE = 8;
	private static final Path RESPONSE_FILE_NAME = Paths.get("target/response.xml");
	private static final Path QUERY_FILE_NAME = Paths.get("target/query.xml");
	private static final Path CONFIG_FILE_NAME = Paths.get("target/test-classes/", "config.properties");
	private static final Path PCAP_FILE_NAME = Paths.get("target/test-classes/", "pcap-data.json");
	private static final Path TWITTER_FILE_NAME = Paths.get("target/test-classes/", "twitter-data.json");

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
		Files.createDirectories(RESPONSE_FILE_NAME);
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
		decryptor.setCrypto(crypto);
		decryptor.setExecutionService(Executors.newCachedThreadPool());
		decryptor.activate();

		clearTextResponseConverter = new ClearTextResponseTypeConverter();
	}


	@Test
	public void testTwitter() throws Exception {

		QuerySchema querySchema = createTwitterQuerySchema();
		Querier querier = createQuerier(Collections.singletonList("Apple"), querySchema);
		runJob(TWITTER_FILE_NAME, querySchema, 1000L, 0);

		List<ClearTextQueryResponse> responses = decryptResponses(querier);
		assertEquals(1, responses.size());

		int[] hitCount = {0};
		int[] recordCount = {0};
		for (ClearTextQueryResponse answer : responses) {
			answer.forEach(sel -> {
				assertEquals(sel.toString(), "text", sel.getName());
				hitCount[0] += sel.hitCount();
				sel.forEachHits(h -> {
					assertEquals("Apple", h.getSelectorValue());
					recordCount[0] += h.recordCount();

					h.forEachRecord(r -> {
						assertEquals(r.toString(), 1, r.fieldCount());
						r.forEachField(f -> {
							assertTrue("id".equals(f.getName()));
							assertEquals(1082283985504366591L, f.getValue());
						});
					});
				});
			});
		}
		assertEquals(1, hitCount[0]);
		assertEquals(1, recordCount[0]);
	}

	@Test
	public void pCapMultipleResponses() throws Exception {

		List<String> selectors = Collections.singletonList("eth:ethertype:ip:tcp:mysql");// Arrays.asList(new
																							// String[]
																							// {"eth:ethertype:ip:tcp:mysql"});
		QuerySchema querySchema = createPcapQuerySchema();

		Querier querier = createQuerier(selectors, querySchema);

		// run for max 1 minute with up to 5 seconds delay between records
		runJob(PCAP_FILE_NAME, querySchema, 60L, 3000);

		List<ClearTextQueryResponse> responses = decryptResponses(querier);
		assertTrue(responses.size() >= 6);
	}

	@Test
	public void pCapSingleResponseSingleHit() throws Exception {

		config.put(ResponderProperties.MAX_HITS_PER_SELECTOR, "1");

		List<String> selectors = Collections.singletonList("eth:ethertype:ip:tcp:mysql");
		QuerySchema querySchema = createPcapQuerySchema();

		Querier querier = createQuerier(selectors, querySchema);

		// run for max 1 minute with up to 5 seconds delay between records
		runJob(PCAP_FILE_NAME, querySchema, 100L, 0);

		List<ClearTextQueryResponse> responses = decryptResponses(querier);
		assertTrue(responses.size() == 1);
		ClearTextQueryResponse answer = responses.get(0);
		assertNotNull(answer);
		assertEquals(1, answer.selectorCount());
		int[] hitCount = {0};
		int[] recordCount = {0};
		answer.forEach(sel -> {
			assertEquals(sel.toString(), "_source|layers|frame|frame.protocols", sel.getName());
			hitCount[0] += sel.hitCount();
			sel.forEachHits(h -> {
				assertEquals("eth:ethertype:ip:tcp:mysql", h.getSelectorValue());
				recordCount[0] += h.recordCount();
				h.forEachRecord(r -> {
					assertTrue(r.toString(), r.fieldCount() == 6);
				});
			});
		});

		assertEquals(1, hitCount[0]);
		assertEquals(10, recordCount[0]);
	}

	@Test
	public void pCapSingleResponse() throws Exception {

		QuerySchema querySchema = createPcapQuerySchema();
		Querier querier = createQuerier(Collections.singletonList("eth:ethertype:ip:tcp:mysql"), querySchema);

		// run for max 100s, no delay reading the file, all records go into a single response
		runJob(PCAP_FILE_NAME, querySchema, 100L, 0);

		List<ClearTextQueryResponse> responses = decryptResponses(querier);
		assertTrue(responses.size() == 1);

		int[] hitCount = {0};
		int[] recordCount = {0};
		for (ClearTextQueryResponse answer : responses) {
			answer.forEach(sel -> {
				assertEquals(sel.toString(), "_source|layers|frame|frame.protocols", sel.getName());
				hitCount[0] += sel.hitCount();
				sel.forEachHits(h -> {
					assertTrue("eth:ethertype:ip:tcp:mysql".equals(h.getSelectorValue()) ||
							"sll:ethertype:ip:tcp:mysql".equals(h.getSelectorValue()));

					recordCount[0] += h.recordCount();
					h.forEachRecord(r -> {
						assertTrue(r.toString(), r.fieldCount() == 6);
					});
				});
			});
		}

		assertEquals(1, hitCount[0]);
		assertEquals(10, recordCount[0]);
	}

	private void runJob(Path fileName, QuerySchema querySchema, long maxRuntime, int maxDelayBetweenRows) throws Exception {
		// final long startTime = System.currentTimeMillis();
		// set up the streaming execution environment
		final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
		env.setMaxParallelism(4);
		env.setParallelism(4);
		env.setStreamTimeCharacteristic(TimeCharacteristic.IngestionTime);

		BaseQueryExecutor responder = new BaseQueryExecutor();
		responder.setConfig(config);
		responder.setInputFileName(QUERY_FILE_NAME);
		responder.setOutputFileName(RESPONSE_FILE_NAME);
		responder.initializeCommon();
		responder.initializeStreaming();

		DataStreamSource<String> dataSource = env.addSource(//
				new FileTestSource(fileName, RESPONSE_FILE_NAME,
						maxRuntime,
						maxDelayBetweenRows));

		// env.readTextFile(fileName.toString());
		// DataStreamSource<String> endOfTimeSource = env.addSource(new RuntimeSource(30));
		// DataStream<String> joinedSource = dataSource.union(endOfTimeSource);
		// joinedSource.filter(s -> s != null).map(new ParseJson(querySchema,
		// responder.getRowTypeInfo()));

		responder.run(env, dataSource.map(new ParseJson(querySchema, responder.getRowTypeInfo())));
		log.info("Flink job ended.");

		assertTrue(!Files.exists(RESPONSE_FILE_NAME.resolve("job-running")));
	}

	@Test
	@Ignore("manually run, requires external kafka")
	public void kafkaLiveTest() throws Exception {
		Responder responder = new Responder();
		responder.setConfig(config);
		responder.setInputFileName(QUERY_FILE_NAME);
		responder.setOutputFileName(RESPONSE_FILE_NAME);
		responder.setBrokers("pirk.envieta.com:9092");
		// responder.setTopic(TOPIC);
		responder.setGroupId("test");
		responder.run();
	}


	private Querier createQuerier(List<String> selectors, QuerySchema querySchema) throws Exception {
		RandomProvider randomProvider = new RandomProvider();
		EncryptQuery queryEnc = new EncryptQuery();
		queryEnc.setCrypto(crypto);
		queryEnc.setRandomProvider(randomProvider);
		Querier result = queryEnc.encrypt(querySchema, selectors, true, DATA_CHUNK_SIZE, HASH_BIT_SIZE);

		try (OutputStream os = new FileOutputStream(QUERY_FILE_NAME.toFile())) {
			queryConverter.marshal(queryConverter.toXMLQuery(result.getQuery()), os);
		}
		return result;
	}

	private QuerySchema createTwitterQuerySchema() {
		DataSchema ds = new DataSchema();
		ds.setName("Tweets");


		DataSchemaElement dse1 = new DataSchemaElement();
		dse1.setName("timestamp_ms");
		dse1.setDataType(FieldTypes.LONG);
		dse1.setIsArray(false);
		dse1.setPosition(0);
		ds.addElement(dse1);

		DataSchemaElement dse2 = new DataSchemaElement();
		dse2.setName("id");
		dse2.setDataType(FieldTypes.LONG);
		dse2.setIsArray(false);
		dse2.setPosition(1);
		ds.addElement(dse2);

		DataSchemaElement dse3 = new DataSchemaElement();
		dse3.setName("text");
		dse3.setDataType(FieldTypes.STRING);
		dse3.setIsArray(false);
		dse3.setPosition(2);
		ds.addElement(dse3);

		QuerySchema qs = new QuerySchema();
		qs.setName("Tweets");
		qs.setSelectorField("text");
		qs.setDataSchema(ds);

		QuerySchemaElement field1 = new QuerySchemaElement();
		field1.setLengthType("fixed");
		field1.setName("id");
		field1.setSize(20);
		field1.setMaxArrayElements(1);
		qs.addElement(field1);

		QuerySchemaElement field2 = new QuerySchemaElement();
		field2.setLengthType("variable");
		field2.setName("text");
		field2.setSize(256);
		field2.setMaxArrayElements(1);
		qs.addElement(field2);

		qs.validate();
		return qs;
	}


	private List<ClearTextQueryResponse> decryptResponses(Querier querier) throws IOException, InterruptedException {
		return Files.walk(RESPONSE_FILE_NAME)
				.filter(p -> Files.isRegularFile(p))
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
						return answer;
					} catch (Exception e) {
						throw new RuntimeException("error loading response", e);
					}
				}).collect(Collectors.toList());
	}


	private QuerySchema createPcapQuerySchema() {
		DataSchema ds = new DataSchema();
		ds.setName("pcap");
		DataSchemaElement dse1 = new DataSchemaElement();
		dse1.setName("_index");
		dse1.setDataType(FieldTypes.STRING);
		dse1.setIsArray(false);
		dse1.setPosition(0);
		ds.addElement(dse1);

		DataSchemaElement dse2 = new DataSchemaElement();
		dse2.setName("_score");
		dse2.setDataType(FieldTypes.STRING);
		dse2.setIsArray(false);
		dse2.setPosition(1);
		ds.addElement(dse2);

		DataSchemaElement dse3 = new DataSchemaElement();
		dse3.setName("_source|layers|eth|eth.dst");
		dse3.setDataType(FieldTypes.STRING);
		dse3.setIsArray(false);
		dse3.setPosition(2);
		ds.addElement(dse3);

		DataSchemaElement dse4 = new DataSchemaElement();
		dse4.setName("_source|layers|eth|eth.src");
		dse4.setDataType(FieldTypes.STRING);
		dse4.setIsArray(false);
		dse4.setPosition(3);
		ds.addElement(dse4);

		DataSchemaElement dse5 = new DataSchemaElement();
		dse5.setName("_source|layers|eth|eth.type");
		dse5.setDataType(FieldTypes.STRING);
		dse5.setIsArray(false);
		dse5.setPosition(4);
		ds.addElement(dse5);

		DataSchemaElement dse6 = new DataSchemaElement();
		dse6.setName("_source|layers|frame|frame.protocols");
		dse6.setDataType(FieldTypes.STRING);
		dse6.setIsArray(false);
		dse6.setPosition(5);
		ds.addElement(dse6);

		DataSchemaElement dse7 = new DataSchemaElement();
		dse7.setName("_source|layers|frame|frame.time_epoch");
		dse7.setDataType(FieldTypes.STRING);
		dse7.setIsArray(false);
		dse7.setPosition(6);
		ds.addElement(dse7);

		DataSchemaElement dse9 = new DataSchemaElement();
		dse9.setName("_source|layers|ip|ip.dst");
		dse9.setDataType(FieldTypes.STRING);
		dse9.setIsArray(false);
		dse9.setPosition(7);
		ds.addElement(dse9);

		DataSchemaElement dse10 = new DataSchemaElement();
		dse10.setName("_source|layers|ip|ip.dst_host");
		dse10.setDataType(FieldTypes.STRING);
		dse10.setIsArray(false);
		dse10.setPosition(8);
		ds.addElement(dse10);

		DataSchemaElement dse11 = new DataSchemaElement();
		dse11.setName("_source|layers|ip|ip.src");
		dse11.setDataType(FieldTypes.STRING);
		dse11.setIsArray(false);
		dse11.setPosition(9);
		ds.addElement(dse11);

		DataSchemaElement dse12 = new DataSchemaElement();
		dse12.setName("_source|layers|ip|ip.src_host");
		dse12.setDataType(FieldTypes.STRING);
		dse12.setIsArray(false);
		dse12.setPosition(10);
		ds.addElement(dse12);

		DataSchemaElement dse13 = new DataSchemaElement();
		dse13.setName("_source|layers|tcp|tcp.flags_tree|tcp.flags.syn_tree|_ws.expert|_ws.expert.message");
		dse13.setDataType(FieldTypes.STRING);
		dse13.setIsArray(false);
		dse13.setPosition(11);
		ds.addElement(dse13);

		DataSchemaElement dse14 = new DataSchemaElement();
		dse14.setName("_source|layers|tcp|tcp.payload");
		dse14.setDataType(FieldTypes.STRING);
		dse14.setIsArray(false);
		dse14.setPosition(12);
		ds.addElement(dse14);

		DataSchemaElement dse15 = new DataSchemaElement();
		dse15.setName("_source|layers|tcp|tcp.srcport");
		dse15.setDataType(FieldTypes.INT);
		dse15.setIsArray(false);
		dse15.setPosition(13);
		ds.addElement(dse15);

		DataSchemaElement dse16 = new DataSchemaElement();
		dse16.setName("_source|layers|tcp|tcp.dstport");
		dse16.setDataType(FieldTypes.INT);
		dse16.setIsArray(false);
		dse16.setPosition(14);
		ds.addElement(dse16);

		DataSchemaElement dse17 = new DataSchemaElement();
		dse17.setName("_type");
		dse17.setDataType(FieldTypes.STRING);
		dse17.setIsArray(false);
		dse17.setPosition(15);
		ds.addElement(dse17);

		QuerySchema qs = new QuerySchema();
		qs.setName("pcap");
		qs.setSelectorField("_source|layers|frame|frame.protocols");
		qs.setDataSchema(ds);

		QuerySchemaElement field1 = new QuerySchemaElement();
		field1.setLengthType("variable");
		field1.setName("_source|layers|frame|frame.protocols");
		field1.setSize(200);
		field1.setMaxArrayElements(1);
		qs.addElement(field1);

		QuerySchemaElement field2 = new QuerySchemaElement();
		field2.setLengthType("variable");
		field2.setName("_source|layers|ip|ip.dst");
		field2.setSize(16);
		field2.setMaxArrayElements(1);
		qs.addElement(field2);

		QuerySchemaElement field3 = new QuerySchemaElement();
		field3.setLengthType("fixed");
		field3.setName("_source|layers|ip|ip.src");
		field3.setSize(15);
		field3.setMaxArrayElements(1);
		qs.addElement(field3);

		QuerySchemaElement field4 = new QuerySchemaElement();
		field4.setLengthType("fixed");
		field4.setName("_source|layers|tcp|tcp.srcport");
		field4.setSize(4);
		field4.setMaxArrayElements(1);
		qs.addElement(field4);

		QuerySchemaElement field5 = new QuerySchemaElement();
		field5.setLengthType("fixed");
		field5.setName("_source|layers|tcp|tcp.dstport");
		field5.setSize(4);
		field5.setMaxArrayElements(1);
		qs.addElement(field5);

		QuerySchemaElement field6 = new QuerySchemaElement();
		field6.setLengthType("variable");
		field6.setName("_source|layers|tcp|tcp.payload");
		field6.setSize(5000);
		field6.setMaxArrayElements(1);
		qs.addElement(field6);

		return qs;
	}
}
