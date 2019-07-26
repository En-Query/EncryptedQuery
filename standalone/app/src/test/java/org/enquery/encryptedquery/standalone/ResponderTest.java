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
package org.enquery.encryptedquery.standalone;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.xml.bind.JAXBException;

import org.enquery.encryptedquery.core.FieldType;
import org.enquery.encryptedquery.data.ClearTextQueryResponse;
import org.enquery.encryptedquery.data.ClearTextQueryResponse.Selector;
import org.enquery.encryptedquery.data.DataSchema;
import org.enquery.encryptedquery.data.DataSchemaElement;
import org.enquery.encryptedquery.data.Query;
import org.enquery.encryptedquery.data.QueryKey;
import org.enquery.encryptedquery.data.QuerySchema;
import org.enquery.encryptedquery.data.QuerySchemaElement;
import org.enquery.encryptedquery.data.Response;
import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.enquery.encryptedquery.encryption.CryptoSchemeRegistry;
import org.enquery.encryptedquery.encryption.paillier.PaillierCryptoScheme;
import org.enquery.encryptedquery.querier.decrypt.DecryptResponse;
import org.enquery.encryptedquery.querier.encrypt.EncryptQuery;
import org.enquery.encryptedquery.querier.encrypt.Querier;
import org.enquery.encryptedquery.utils.FileIOUtils;
import org.enquery.encryptedquery.utils.RandomProvider;
import org.enquery.encryptedquery.xml.transformation.QueryTypeConverter;
import org.enquery.encryptedquery.xml.transformation.ResponseTypeConverter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ResponderTest {
	private final Logger log = LoggerFactory.getLogger(ResponderTest.class);

	private static final Path RESPONSE_FILE_NAME = Paths.get("target/response.xml");
	private static final Path QUERY_FILE_NAME = Paths.get("target/query.xml");
	private static final Path CONFIG_FILE_NAME = Paths.get("target/test-classes/", "config.cfg");
	private static final Path DATA_FILE_NAME = Paths.get("target/test-classes/", "simple-data.json");

	private static final String SELECTOR = "31";
	private static final List<String> SELECTORS = Arrays.asList(new String[] {SELECTOR});

	private QuerySchema querySchema;
	private CryptoScheme crypto;
	// private ResponseTypeConverter responseConverter;
	private QueryKey queryKey;

	private Map<String, String> config;

	// private Responder responder;
	private ExecutorService decryptionThreadPool = Executors.newCachedThreadPool();;

	// private Responder responder;

	@Before
	public void prepare() throws Exception {
		log.info("Start initializing test.");

		Files.deleteIfExists(RESPONSE_FILE_NAME);
		Files.deleteIfExists(QUERY_FILE_NAME);

		config = FileIOUtils.loadPropertyFile(CONFIG_FILE_NAME);
		log.info("Loaded config: " + config);
		crypto = new PaillierCryptoScheme();
		crypto.initialize(config);


		// responder = new Responder();
		// responder.setOutputFileName(RESPONSE_FILE_NAME);
		// responder.setInputDataFile(DATA_FILE_NAME);
		// responder.setQueryFileName(QUERY_FILE_NAME);
		// saveQuery(responder.getCrypto());
		// responder.in

		// crypto = new PaillierCryptoScheme();
		// crypto.initialize(config);
		// crypto = responder.getCrypto();



		log.info("Finished initializing test.");
	}

	private void saveQuery() throws Exception, JAXBException, IOException, FileNotFoundException {
		querySchema = createQuerySchema();

		Querier querier = createQuerier(crypto, "Books", SELECTORS);
		queryKey = querier.getQueryKey();

		saveQuery(querier.getQuery());
	}

	/**
	 * @param crypto
	 * @param query
	 * @throws IOException
	 * @throws FileNotFoundException
	 * @throws JAXBException
	 */
	private void saveQuery(Query query) throws FileNotFoundException, IOException, JAXBException {
		CryptoSchemeRegistry cryptoRegistry = new CryptoSchemeRegistry() {
			@Override
			public CryptoScheme cryptoSchemeByName(String schemeId) {
				if (schemeId.equals(crypto.name())) {
					return crypto;
				}
				return null;
			}
		};

		QueryTypeConverter queryConverter = new QueryTypeConverter();
		queryConverter.setCryptoRegistry(cryptoRegistry);
		queryConverter.initialize();

		// save the query
		try (OutputStream os = new FileOutputStream(QUERY_FILE_NAME.toFile())) {
			queryConverter.marshal(queryConverter.toXMLQuery(query), os);
		}

	}

	@After
	public void after() throws Exception {
		if (crypto != null) {
			crypto.close();
			crypto = null;
		}
	}

	@Test
	public void gapIssue() throws Exception {
		Path DATA_FILE = Paths.get("target/test-classes/", "phone-calls.json");

		querySchema = createPhoneDataQuerySchema();
		List<String> selectors = Arrays.asList(new String[] {"666-951-8350", "893-324-3654"});

		Querier querier = createQuerier(crypto, "Phone calls", selectors);
		queryKey = querier.getQueryKey();
		saveQuery(querier.getQuery());

		try (ResponderV2 responder = new ResponderV2()) {
			responder.setOutputFileName(RESPONSE_FILE_NAME);
			responder.setInputDataFile(DATA_FILE);
			responder.setQueryFileName(QUERY_FILE_NAME);
			responder.setBufferWidth(40);
			responder.run(config);
		}

		Response response = loadFile();
		response.getQueryInfo().printQueryInfo();

		log.info("# Response records: ", response.getResponseElements().size());

		DecryptResponse dr = new DecryptResponse();
		dr.setCrypto(crypto);
		dr.setExecutionService(decryptionThreadPool);
		dr.activate();

		ClearTextQueryResponse answer = dr.decrypt(response, queryKey);
		log.info(answer.toString());
		Selector selector = answer.selectorByName("Caller #");
		assertEquals(2, selector.hitCount());
		Map<String, ClearTextQueryResponse.Hits> hitsPerSelector = new HashMap<>();
		selector.forEachHits(h -> {
			hitsPerSelector.put(h.getSelectorValue(), h);
		});
		assertTrue(hitsPerSelector.containsKey("666-951-8350"));
		assertEquals(2, hitsPerSelector.get("666-951-8350").recordCount());
		assertTrue(hitsPerSelector.containsKey("893-324-3654"));
		assertEquals(1, hitsPerSelector.get("893-324-3654").recordCount());
	}

	@Test
	public void testXertaFile() throws Exception {

		Path CDR_DATA_FILE = Paths.get("target/test-classes/", "xerta-50.json");

		querySchema = createXertaQuerySchema();
		List<String> selectors = Arrays.asList(new String[] {"MTX", "LTEC", "S3F", "GBRA", "KCC", "NESR"});
		Querier querier = createQuerier(crypto, "Xerta", selectors);
		queryKey = querier.getQueryKey();
		saveQuery(querier.getQuery());

		try (ResponderV2 responder = new ResponderV2()) {
			responder.setOutputFileName(RESPONSE_FILE_NAME);
			responder.setInputDataFile(CDR_DATA_FILE);
			responder.setQueryFileName(QUERY_FILE_NAME);
			responder.run(config);
		}

		Response response = loadFile();
		response.getQueryInfo().printQueryInfo();

		log.info("# Response records: ", response.getResponseElements().size());

		// ExecutorService es = Executors.newCachedThreadPool();
		DecryptResponse dr = new DecryptResponse();
		dr.setCrypto(crypto);
		dr.setExecutionService(decryptionThreadPool);
		dr.activate();

		ClearTextQueryResponse answer = dr.decrypt(response, queryKey);
		log.info(answer.toString());

		assertEquals(1, answer.selectorCount());

		final Map<String, Object> returnedFields = new HashMap<>();
		Selector selector = answer.selectorByName("Mnemonic");
		assertNotNull(selector);
		selector.forEachHits(hits -> {
			assertEquals("MTX", hits.getSelectorValue());
			hits.forEachRecord(record -> {
				record.forEachField(field -> {
					log.info("Field {}", field);
					returnedFields.put(field.getName(), field.getValue());
				});
			});
		});
	}

	@Test
	public void testPcapFile() throws Exception {

		Path CDR_DATA_FILE = Paths.get("target/test-classes/", "pcap-data-test.json");

		querySchema = createPcapQuerySchema();

		// List<String> selectors = Arrays.asList(new String[] {"sll:ethertype:ip:udp:data",
		// "sll:ethertype:ip:tcp:mysql:vssmonitoring", "sll:ethertype:ip:tcp:ssh"});
		List<String> selectors = Arrays.asList(new String[] {"sll:ethertype:ip:tcp:ssh"});
		Querier querier = createQuerier(crypto, "Pcap", selectors);
		queryKey = querier.getQueryKey();
		saveQuery(querier.getQuery());

		try (ResponderV2 responder = new ResponderV2()) {
			responder.setOutputFileName(RESPONSE_FILE_NAME);
			responder.setInputDataFile(CDR_DATA_FILE);
			responder.setQueryFileName(QUERY_FILE_NAME);
			responder.run(config);
		}

		Response response = loadFile();
		response.getQueryInfo().printQueryInfo();

		log.info("# Response records: ", response.getResponseElements().size());

		// ExecutorService es = Executors.newCachedThreadPool();
		DecryptResponse dr = new DecryptResponse();
		dr.setCrypto(crypto);
		dr.setExecutionService(decryptionThreadPool);
		dr.activate();

		ClearTextQueryResponse answer = dr.decrypt(response, queryKey);
		log.info(answer.toString());



		final Map<String, Object> returnedFields = new HashMap<>();
		Selector selector = answer.selectorByName("_source|layers|frame|frame.protocols");
		assertNotNull(selector);
		selector.forEachHits(hits -> {
			assertEquals("sll:ethertype:ip:tcp:ssh", hits.getSelectorValue());
			hits.forEachRecord(record -> {
				record.forEachField(field -> {
					log.info("Field {}", field);
					returnedFields.put(field.getName(), field.getValue());
				});
			});
		});
	}

	@Test
	public void test() throws Exception {

		saveQuery();
		try (ResponderV2 responder = new ResponderV2()) {
			responder.setOutputFileName(RESPONSE_FILE_NAME);
			responder.setInputDataFile(DATA_FILE_NAME);
			responder.setQueryFileName(QUERY_FILE_NAME);
			responder.run(config);
		}
		Response response = loadFile();
		response.getQueryInfo().printQueryInfo();

		log.info("# Response records: ", response.getResponseElements().size());


		DecryptResponse dr = new DecryptResponse();
		dr.setCrypto(crypto);
		dr.setExecutionService(decryptionThreadPool);
		dr.activate();

		ClearTextQueryResponse answer = dr.decrypt(response, queryKey);
		log.info("answer: " + answer);
		assertNotNull(answer);
		assertEquals(1, answer.selectorCount());
		int[] hitCount = {0};
		int[] recordCount = {0};

		final Map<String, Object> returnedFields = new HashMap<>();
		Selector sel = answer.selectorByName("age");
		sel.forEachHits(hits -> {
			hitCount[0]++;
			hits.forEachRecord(record -> {
				recordCount[0]++;
				record.forEachField(field -> {
					log.info("Field {}", field);
					returnedFields.put(field.getName(), field.getValue());
				});
			});
		});

		assertEquals(1, hitCount[0]);
		assertEquals(1, recordCount[0]);
		assertEquals("Alice", returnedFields.get("name"));
		// assertEquals("['Zack', 'Yvette']", returnedFields.get("children"));
	}


	private Response loadFile() throws FileNotFoundException, IOException, JAXBException {
		CryptoSchemeRegistry cryptoRegistry = new CryptoSchemeRegistry() {
			@Override
			public CryptoScheme cryptoSchemeByName(String schemeId) {
				if (schemeId.equals(crypto.name())) {
					return crypto;
				}
				return null;
			}
		};

		QueryTypeConverter queryConverter = new QueryTypeConverter();
		queryConverter.setCryptoRegistry(cryptoRegistry);
		queryConverter.initialize();

		ResponseTypeConverter responseConverter = new ResponseTypeConverter();
		responseConverter.setQueryConverter(queryConverter);
		responseConverter.setSchemeRegistry(cryptoRegistry);
		responseConverter.initialize();

		try (FileInputStream fis = new FileInputStream(RESPONSE_FILE_NAME.toFile())) {
			org.enquery.encryptedquery.xml.schema.Response xml = responseConverter.unmarshal(fis);
			return responseConverter.toCore(xml);
		}
	}

	private Querier createQuerier(CryptoScheme crypto, String queryType, List<String> selectors) throws Exception {
		RandomProvider randomProvider = new RandomProvider();
		EncryptQuery queryEnc = new EncryptQuery();
		queryEnc.setCrypto(crypto);
		queryEnc.setRandomProvider(randomProvider);
		// dataChunkSize=1
		// hashBitSize=9
		return queryEnc.encrypt(querySchema, selectors, 2, 9);
	}

	private QuerySchema createQuerySchema() {
		DataSchema ds = new DataSchema();
		ds.setName("People");
		DataSchemaElement dse1 = new DataSchemaElement();
		dse1.setName("name");
		dse1.setDataType(FieldType.STRING);
		dse1.setPosition(0);
		ds.addElement(dse1);

		DataSchemaElement dse2 = new DataSchemaElement();
		dse2.setName("age");
		dse2.setDataType(FieldType.INT);
		dse2.setPosition(1);
		ds.addElement(dse2);

		DataSchemaElement dse3 = new DataSchemaElement();
		dse3.setName("children");
		dse3.setDataType(FieldType.STRING_LIST);
		dse3.setPosition(2);
		ds.addElement(dse3);

		QuerySchema qs = new QuerySchema();
		qs.setName("People");
		qs.setSelectorField("age");
		qs.setDataSchema(ds);

		QuerySchemaElement field1 = new QuerySchemaElement();
		field1.setName("name");
		// field1.setLengthType("fixed");
		// field1.setSize(128);
		// field1.setMaxArrayElements(1);
		qs.addElement(field1);

		QuerySchemaElement field2 = new QuerySchemaElement();
		field2.setName("children");
		field2.setMaxArrayElements(3);
		// field2.setSize(128);
		// field2.setLengthType("fixed");
		// field2.setLengthType("variable");
		qs.addElement(field2);

		QuerySchemaElement field3 = new QuerySchemaElement();
		field3.setName("age");
		// field3.setLengthType("fixed");
		// field3.setSize(4);
		// field3.setMaxArrayElements(1);
		qs.addElement(field3);

		return qs;
	}

	private QuerySchema createXertaQuerySchema() {
		DataSchema ds = new DataSchema();
		ds.setName("xerta");
		DataSchemaElement dse1 = new DataSchemaElement();
		dse1.setName("Mnemonic");
		dse1.setDataType(FieldType.STRING);
		dse1.setPosition(1);
		ds.addElement(dse1);

		DataSchemaElement dse2 = new DataSchemaElement();
		dse2.setName("Currency");
		dse2.setDataType(FieldType.STRING);
		dse2.setPosition(4);
		ds.addElement(dse2);

		DataSchemaElement dse3 = new DataSchemaElement();
		dse3.setName("MaxPrice");
		dse3.setDataType(FieldType.FLOAT);
		dse3.setPosition(8);
		ds.addElement(dse3);

		DataSchemaElement dse4 = new DataSchemaElement();
		dse4.setName("MinPrice");
		dse4.setDataType(FieldType.FLOAT);
		dse4.setPosition(9);
		ds.addElement(dse4);

		DataSchemaElement dse5 = new DataSchemaElement();
		dse5.setName("Date");
		dse5.setDataType(FieldType.STRING);
		dse5.setPosition(6);
		ds.addElement(dse5);

		DataSchemaElement dse6 = new DataSchemaElement();
		dse6.setName("SecurityType");
		dse6.setDataType(FieldType.STRING);
		dse6.setPosition(3);
		ds.addElement(dse6);

		DataSchemaElement dse7 = new DataSchemaElement();
		dse7.setName("SecurityDesc");
		dse7.setDataType(FieldType.STRING);
		dse7.setPosition(2);
		ds.addElement(dse7);

		DataSchemaElement dse9 = new DataSchemaElement();
		dse9.setName("SecurityID");
		dse9.setDataType(FieldType.INT);
		dse9.setPosition(5);
		ds.addElement(dse9);

		DataSchemaElement dse10 = new DataSchemaElement();
		dse10.setName("ISIN");
		dse10.setDataType(FieldType.STRING);
		dse10.setPosition(0);
		ds.addElement(dse10);

		DataSchemaElement dse11 = new DataSchemaElement();
		dse11.setName("Time");
		dse11.setDataType(FieldType.STRING);
		dse11.setPosition(7);
		ds.addElement(dse11);

		QuerySchema qs = new QuerySchema();
		qs.setName("xerta");
		qs.setSelectorField("Mnemonic");
		qs.setDataSchema(ds);

		QuerySchemaElement field1 = new QuerySchemaElement();
		field1.setName("Mnemonic");
		// field1.setLengthType("variable");
		// field1.setSize(20);
		// field1.setMaxArrayElements(1);
		qs.addElement(field1);

		QuerySchemaElement field2 = new QuerySchemaElement();
		field2.setName("Currency");
		// field2.setLengthType("variable");
		// field2.setSize(16);
		// field2.setMaxArrayElements(1);
		qs.addElement(field2);

		QuerySchemaElement field3 = new QuerySchemaElement();
		field3.setName("MaxPrice");
		// field3.setLengthType("fixed");
		// field3.setSize(8);
		// field3.setMaxArrayElements(1);
		qs.addElement(field3);

		QuerySchemaElement field4 = new QuerySchemaElement();
		field4.setName("MinPrice");
		// field4.setLengthType("fixed");
		// field4.setSize(8);
		// field4.setMaxArrayElements(1);
		qs.addElement(field4);

		QuerySchemaElement field5 = new QuerySchemaElement();
		field5.setName("Date");
		// field5.setLengthType("variable");
		// field5.setSize(20);
		// field5.setMaxArrayElements(1);
		qs.addElement(field5);

		QuerySchemaElement field6 = new QuerySchemaElement();
		field6.setName("SecurityType");
		// field6.setLengthType("variable");
		// field6.setSize(30);
		// field6.setMaxArrayElements(1);
		qs.addElement(field6);

		QuerySchemaElement field7 = new QuerySchemaElement();
		field7.setName("SecurityDesc");
		// field7.setLengthType("variable");
		// field7.setSize(1000);
		// field7.setMaxArrayElements(1);
		qs.addElement(field7);

		return qs;
	}


	private QuerySchema createPhoneDataQuerySchema() {
		DataSchema ds = new DataSchema();
		ds.setName("phone-data");

		DataSchemaElement dse1 = new DataSchemaElement();
		dse1.setName("Callee #");
		dse1.setDataType(FieldType.STRING);
		dse1.setPosition(1);
		ds.addElement(dse1);

		DataSchemaElement dse2 = new DataSchemaElement();
		dse2.setName("Caller #");
		dse2.setDataType(FieldType.STRING);
		dse2.setPosition(2);
		ds.addElement(dse2);

		DataSchemaElement dse3 = new DataSchemaElement();
		dse3.setName("Date/Time");
		dse3.setDataType(FieldType.STRING);
		dse3.setPosition(3);
		ds.addElement(dse3);

		DataSchemaElement dse4 = new DataSchemaElement();
		dse4.setName("Duration");
		dse4.setDataType(FieldType.STRING);
		dse4.setPosition(4);
		ds.addElement(dse4);


		QuerySchema qs = new QuerySchema();
		qs.setName("Phone Data");
		qs.setSelectorField("Caller #");
		qs.setDataSchema(ds);

		QuerySchemaElement field1 = new QuerySchemaElement();
		field1.setName("Caller #");
		// field1.setLengthType("variable");
		// field1.setSize(20);
		// field1.setMaxArrayElements(1);
		qs.addElement(field1);

		QuerySchemaElement field2 = new QuerySchemaElement();
		field2.setName("Callee #");
		// field2.setLengthType("variable");
		// field2.setSize(16);
		// field2.setMaxArrayElements(1);
		qs.addElement(field2);

		QuerySchemaElement field3 = new QuerySchemaElement();
		field3.setName("Duration");
		// field3.setLengthType("variable");
		// field3.setSize(10);
		// field3.setMaxArrayElements(1);
		qs.addElement(field3);

		QuerySchemaElement field4 = new QuerySchemaElement();
		field4.setName("Date/Time");
		// field4.setLengthType("variable");
		// field4.setSize(30);
		// field4.setMaxArrayElements(1);
		qs.addElement(field4);

		return qs;
	}

	private QuerySchema createPcapQuerySchema() {
		DataSchema ds = new DataSchema();
		ds.setName("Pcap");
		DataSchemaElement dse1 = new DataSchemaElement();
		dse1.setName("_index");
		dse1.setDataType(FieldType.STRING);
		dse1.setPosition(0);
		ds.addElement(dse1);

		DataSchemaElement dse2 = new DataSchemaElement();
		dse2.setName("_score");
		dse2.setDataType(FieldType.STRING);
		dse2.setPosition(2);
		ds.addElement(dse2);

		DataSchemaElement dse3 = new DataSchemaElement();
		dse3.setName("_source|layers|eth|eth.dst");
		dse3.setDataType(FieldType.STRING);
		dse3.setPosition(5);
		ds.addElement(dse3);

		DataSchemaElement dse4 = new DataSchemaElement();
		dse4.setName("_source|layers|eth|eth.src");
		dse4.setDataType(FieldType.STRING);
		dse4.setPosition(6);
		ds.addElement(dse4);

		DataSchemaElement dse5 = new DataSchemaElement();
		dse5.setName("_source|layers|eth|eth.type");
		dse5.setDataType(FieldType.STRING);
		dse5.setPosition(7);
		ds.addElement(dse5);

		DataSchemaElement dse6 = new DataSchemaElement();
		dse6.setName("_source|layers|frame|frame.protocols");
		dse6.setDataType(FieldType.STRING);
		dse6.setPosition(4);
		ds.addElement(dse6);

		DataSchemaElement dse7 = new DataSchemaElement();
		dse7.setName("_source|layers|frame|frame.time_epoch");
		dse7.setDataType(FieldType.STRING);
		dse7.setPosition(3);
		ds.addElement(dse7);

		DataSchemaElement dse9 = new DataSchemaElement();
		dse9.setName("_source|layers|ip|ip.dst");
		dse9.setDataType(FieldType.IP4);
		dse9.setPosition(10);
		ds.addElement(dse9);

		DataSchemaElement dse10 = new DataSchemaElement();
		dse10.setName("_source|layers|ip|ip.dst_host");
		dse10.setDataType(FieldType.IP4);
		dse10.setPosition(11);
		ds.addElement(dse10);

		DataSchemaElement dse11 = new DataSchemaElement();
		dse11.setName("_source|layers|ip|ip.src");
		dse11.setDataType(FieldType.IP4);
		dse11.setPosition(8);
		ds.addElement(dse11);

		DataSchemaElement dse12 = new DataSchemaElement();
		dse12.setName("_source|layers|ip|ip.src_host");
		dse12.setDataType(FieldType.IP4);
		dse12.setPosition(9);
		ds.addElement(dse12);

		DataSchemaElement dse13 = new DataSchemaElement();
		dse13.setName("_source|layers|tcp|tcp.dstport");
		dse13.setDataType(FieldType.INT);
		dse13.setPosition(13);
		ds.addElement(dse13);

		DataSchemaElement dse14 = new DataSchemaElement();
		dse14.setName("_source|layers|tcp|tcp.flags_tree|tcp.flags.syn_tree|_ws.expert|_ws.expert.message");
		dse14.setDataType(FieldType.STRING);
		dse14.setPosition(14);
		ds.addElement(dse14);

		DataSchemaElement dse15 = new DataSchemaElement();
		dse15.setName("_source|layers|tcp|tcp.payload");
		dse15.setDataType(FieldType.STRING);
		dse15.setPosition(15);
		ds.addElement(dse15);

		DataSchemaElement dse16 = new DataSchemaElement();
		dse16.setName("_source|layers|tcp|tcp.srcport");
		dse16.setDataType(FieldType.INT);
		dse16.setPosition(12);
		ds.addElement(dse16);

		DataSchemaElement dse8 = new DataSchemaElement();
		dse8.setName("_type");
		dse8.setDataType(FieldType.STRING);
		dse8.setPosition(1);
		ds.addElement(dse8);

		QuerySchema qs = new QuerySchema();
		qs.setName("Pcap");
		qs.setSelectorField("_source|layers|frame|frame.protocols");
		qs.setDataSchema(ds);

		QuerySchemaElement field1 = new QuerySchemaElement();
		field1.setName("_source|layers|frame|frame.protocols");
		field1.setSize(200);
		field1.setMaxArrayElements(1);
		qs.addElement(field1);

		QuerySchemaElement field2 = new QuerySchemaElement();
		field2.setName("_source|layers|ip|ip.dst");
		field2.setSize(4);
		field2.setMaxArrayElements(1);
		qs.addElement(field2);

		QuerySchemaElement field3 = new QuerySchemaElement();
		field3.setName("_source|layers|ip|ip.src");
		field3.setSize(4);
		field3.setMaxArrayElements(1);
		qs.addElement(field3);

		QuerySchemaElement field4 = new QuerySchemaElement();
		field4.setName("_source|layers|tcp|tcp.srcport");
		field4.setSize(4);
		field4.setMaxArrayElements(1);
		qs.addElement(field4);

		QuerySchemaElement field5 = new QuerySchemaElement();
		field5.setName("_source|layers|tcp|tcp.dstport");
		field5.setSize(4);
		field5.setMaxArrayElements(1);
		qs.addElement(field5);

		QuerySchemaElement field6 = new QuerySchemaElement();
		field6.setName("_source|layers|tcp|tcp.payload");
		field6.setSize(5000);
		field6.setMaxArrayElements(1);
		qs.addElement(field6);

		QuerySchemaElement field7 = new QuerySchemaElement();
		field7.setName("_source|layers|frame|frame.time_epoch");
		field7.setSize(50);
		field7.setMaxArrayElements(1);
		qs.addElement(field7);

		QuerySchemaElement field8 = new QuerySchemaElement();
		field8.setName("_source|layers|tcp|tcp.flags_tree|tcp.flags.syn_tree|_ws.expert|_ws.expert.message");
		field8.setSize(500);
		field8.setMaxArrayElements(1);
		qs.addElement(field8);

		QuerySchemaElement field9 = new QuerySchemaElement();
		field9.setName("_score");
		field9.setSize(50);
		field9.setMaxArrayElements(1);
		qs.addElement(field9);

		return qs;
	}

}
