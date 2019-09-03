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
import static org.junit.Assume.assumeTrue;

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


public class GPUDecryptorTest {
	private final Logger log = LoggerFactory.getLogger(GPUDecryptorTest.class);

	private static final Path RESPONSE_FILE_NAME = Paths.get("target/response.xml");
	private static final Path QUERY_FILE_NAME = Paths.get("target/query.xml");
	private static final Path CONFIG_FILE_NAME = Paths.get("target/test-classes/", "config_gpudecryptor.cfg");
	private static final Path DATA_FILE_NAME = Paths.get("target/test-classes/", "people.json");

	private static final String SELECTOR = "31";
	private static final List<String> SELECTORS = Arrays.asList(new String[] {SELECTOR});

	private QuerySchema querySchema;
	private CryptoScheme crypto;
	// private ResponseTypeConverter responseConverter;
	private QueryKey queryKey;

	private Map<String, String> config;

	// private Responder responder;
	// private ExecutorService decryptionThreadPool = Executors.newCachedThreadPool();

	private DecryptResponse decryptResponse;;

	// private Responder responder;

	@Before
	public void prepare() throws Exception {
		String skip = System.getProperty("skip.gpu.native.libs");
		assumeTrue(skip == null || !Boolean.valueOf(skip));

		log.info("Start initializing test.");

		Files.deleteIfExists(RESPONSE_FILE_NAME);
		Files.deleteIfExists(QUERY_FILE_NAME);

		config = FileIOUtils.loadPropertyFile(CONFIG_FILE_NAME);
		log.info("Loaded config: " + config);
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


		decryptResponse = new DecryptResponse();
		decryptResponse.setCryptoRegistry(cryptoRegistry);
		decryptResponse.setExecutionService(Executors.newCachedThreadPool());



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
	public void testXertaFile() throws Exception {

		Path CDR_DATA_FILE = Paths.get("target/test-classes/", "xerta-50.json");

		querySchema = createXertaQuerySchema();
		List<String> selectors = Arrays.asList(new String[] {"MTX", "LTEC", "S3F", "GBRA", "KCC", "NESR"});
		Querier querier = createQuerier(crypto, "Xerta", selectors);
		queryKey = querier.getQueryKey();
		saveQuery(querier.getQuery());

		try (Responder responder = new Responder()) {
			responder.setOutputFileName(RESPONSE_FILE_NAME);
			responder.setInputDataFile(CDR_DATA_FILE);
			responder.setQueryFileName(QUERY_FILE_NAME);
			responder.run(config);
		}

		Response response = loadFile();
		response.getQueryInfo().printQueryInfo();

		log.info("# Response records: ", response.getResponseElements().size());

		ClearTextQueryResponse answer = decryptResponse.decrypt(response, queryKey);
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


		ClearTextQueryResponse answer = decryptResponse.decrypt(response, queryKey);
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

		return queryEnc.encrypt(querySchema, selectors, 1, 9);
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
		// field1.setLengthType("fixed");
		field1.setName("name");
		// field1.setSize(128);
		// field1.setMaxArrayElements(1);
		qs.addElement(field1);

		QuerySchemaElement field2 = new QuerySchemaElement();
		field2.setName("children");
		field2.setSize(128);
		field2.setMaxArrayElements(3);
		// field2.setLengthType("fixed");
		// field2.setLengthType("variable");
		qs.addElement(field2);

		QuerySchemaElement field3 = new QuerySchemaElement();
		// field3.setLengthType("fixed");
		field3.setName("age");
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
		dse3.setDataType(FieldType.DOUBLE);
		dse3.setPosition(8);
		ds.addElement(dse3);

		DataSchemaElement dse4 = new DataSchemaElement();
		dse4.setName("MinPrice");
		dse4.setDataType(FieldType.DOUBLE);
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
		// field1.setLengthType("variable");
		field1.setName("Mnemonic");
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

}
