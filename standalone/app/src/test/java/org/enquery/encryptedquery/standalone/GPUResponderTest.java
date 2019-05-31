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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.xml.bind.JAXBException;

import org.enquery.encryptedquery.core.FieldTypes;
import org.enquery.encryptedquery.data.ClearTextQueryResponse;
import org.enquery.encryptedquery.data.ClearTextQueryResponse.Selector;
import org.enquery.encryptedquery.data.DataSchema;
import org.enquery.encryptedquery.data.DataSchemaElement;
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


public class GPUResponderTest {
	private final Logger log = LoggerFactory.getLogger(GPUResponderTest.class);

	private static final Path RESPONSE_FILE_NAME = Paths.get("target/response.xml");
	private static final Path QUERY_FILE_NAME = Paths.get("target/query.xml");
	private static final Path CONFIG_FILE_NAME = Paths.get("target/test-classes/", "config_gpucolproc.cfg");
	private static final Path DATA_FILE_NAME = Paths.get("target/test-classes/", "simple-data.json");

	private static final String SELECTOR = "31";
	private static final List<String> SELECTORS = Arrays.asList(new String[] {SELECTOR});

	private QuerySchema querySchema;
	private ResponseTypeConverter responseConverter;
	private QueryKey queryKey;
	// private Query query;

	private Map<String, String> config;

	private PaillierCryptoScheme crypto;

	private QueryTypeConverter queryConverter;

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
		queryConverter = new QueryTypeConverter();
		queryConverter.setCryptoRegistry(cryptoRegistry);
		queryConverter.initialize();

		responseConverter = new ResponseTypeConverter();
		responseConverter.setQueryConverter(queryConverter);
		responseConverter.setSchemeRegistry(cryptoRegistry);
		responseConverter.initialize();

		querySchema = createQuerySchema();

		Querier querier = createQuerier("Books", SELECTORS);
		queryKey = querier.getQueryKey();

		// save the query
		try (OutputStream os = new FileOutputStream(QUERY_FILE_NAME.toFile())) {
			queryConverter.marshal(queryConverter.toXMLQuery(querier.getQuery()), os);
		}

		log.info("Finished initializing test.");
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
		Querier querier = createQuerier("Xerta", selectors);
		queryKey = querier.getQueryKey();

		// save the query
		try (OutputStream os = new FileOutputStream(QUERY_FILE_NAME.toFile())) {
			queryConverter.marshal(queryConverter.toXMLQuery(querier.getQuery()), os);
		}

		try (Responder responder = new Responder()) {
			responder.setOutputFileName(RESPONSE_FILE_NAME);
			responder.setInputDataFile(CDR_DATA_FILE);
			responder.setQueryFileName(QUERY_FILE_NAME);
			responder.run(FileIOUtils.loadPropertyFile(CONFIG_FILE_NAME));
		}

		Response response = loadFile();
		response.getQueryInfo().printQueryInfo();

		log.info("# Response records: ", response.getResponseElements().size());

		ExecutorService es = Executors.newCachedThreadPool();
		DecryptResponse dr = new DecryptResponse();
		dr.setCrypto(crypto);
		dr.setExecutionService(es);
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
	public void test() throws Exception {

		try (Responder responder = new Responder()) {
			responder.setOutputFileName(RESPONSE_FILE_NAME);
			responder.setInputDataFile(DATA_FILE_NAME);
			responder.setQueryFileName(QUERY_FILE_NAME);
			responder.run(FileIOUtils.loadPropertyFile(CONFIG_FILE_NAME));
		}

		Response response = loadFile();
		response.getQueryInfo().printQueryInfo();

		log.info("# Response records: ", response.getResponseElements().size());

		ExecutorService es = Executors.newCachedThreadPool();
		DecryptResponse dr = new DecryptResponse();
		dr.setCrypto(crypto);
		dr.setExecutionService(es);
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
		try (FileInputStream fis = new FileInputStream(RESPONSE_FILE_NAME.toFile())) {
			org.enquery.encryptedquery.xml.schema.Response xml = responseConverter.unmarshal(fis);
			return responseConverter.toCore(xml);
		}
	}

	private Querier createQuerier(String queryType, List<String> selectors) throws Exception {
		RandomProvider randomProvider = new RandomProvider();
		EncryptQuery queryEnc = new EncryptQuery();
		queryEnc.setCrypto(crypto);
		queryEnc.setRandomProvider(randomProvider);
		// dataChunkSize=1
		// hashBitSize=9
		return queryEnc.encrypt(querySchema, selectors, true, 1, 9);
	}

	private QuerySchema createQuerySchema() {
		DataSchema ds = new DataSchema();
		ds.setName("People");
		DataSchemaElement dse1 = new DataSchemaElement();
		dse1.setName("name");
		dse1.setDataType("string");
		dse1.setIsArray(false);
		dse1.setPosition(0);
		ds.addElement(dse1);

		DataSchemaElement dse2 = new DataSchemaElement();
		dse2.setName("age");
		dse2.setDataType("int");
		dse2.setIsArray(false);
		dse2.setPosition(1);
		ds.addElement(dse2);

		DataSchemaElement dse3 = new DataSchemaElement();
		dse3.setName("children");
		dse3.setDataType("string");
		dse3.setIsArray(true);
		dse3.setPosition(2);
		ds.addElement(dse3);

		QuerySchema qs = new QuerySchema();
		qs.setName("People");
		qs.setSelectorField("age");
		qs.setDataSchema(ds);

		QuerySchemaElement field1 = new QuerySchemaElement();
		field1.setLengthType("fixed");
		field1.setName("name");
		field1.setSize(128);
		field1.setMaxArrayElements(1);
		qs.addElement(field1);

		QuerySchemaElement field2 = new QuerySchemaElement();
		field2.setLengthType("fixed");
		field2.setName("children");
		field2.setSize(128);
		field2.setMaxArrayElements(3);
		field2.setLengthType("variable");
		qs.addElement(field2);

		QuerySchemaElement field3 = new QuerySchemaElement();
		field3.setLengthType("fixed");
		field3.setName("age");
		field3.setSize(4);
		field3.setMaxArrayElements(1);
		qs.addElement(field3);

		return qs;
	}

	private QuerySchema createXertaQuerySchema() {
		DataSchema ds = new DataSchema();
		ds.setName("xerta");
		DataSchemaElement dse1 = new DataSchemaElement();
		dse1.setName("Mnemonic");
		dse1.setDataType(FieldTypes.STRING);
		dse1.setIsArray(false);
		dse1.setPosition(1);
		ds.addElement(dse1);

		DataSchemaElement dse2 = new DataSchemaElement();
		dse2.setName("Currency");
		dse2.setDataType(FieldTypes.STRING);
		dse2.setIsArray(false);
		dse2.setPosition(4);
		ds.addElement(dse2);

		DataSchemaElement dse3 = new DataSchemaElement();
		dse3.setName("MaxPrice");
		dse3.setDataType(FieldTypes.DOUBLE);
		dse3.setIsArray(false);
		dse3.setPosition(8);
		ds.addElement(dse3);

		DataSchemaElement dse4 = new DataSchemaElement();
		dse4.setName("MinPrice");
		dse4.setDataType(FieldTypes.DOUBLE);
		dse4.setIsArray(false);
		dse4.setPosition(9);
		ds.addElement(dse4);

		DataSchemaElement dse5 = new DataSchemaElement();
		dse5.setName("Date");
		dse5.setDataType(FieldTypes.STRING);
		dse5.setIsArray(false);
		dse5.setPosition(6);
		ds.addElement(dse5);

		DataSchemaElement dse6 = new DataSchemaElement();
		dse6.setName("SecurityType");
		dse6.setDataType(FieldTypes.STRING);
		dse6.setIsArray(false);
		dse6.setPosition(3);
		ds.addElement(dse6);

		DataSchemaElement dse7 = new DataSchemaElement();
		dse7.setName("SecurityDesc");
		dse7.setDataType(FieldTypes.STRING);
		dse7.setIsArray(false);
		dse7.setPosition(2);
		ds.addElement(dse7);

		DataSchemaElement dse9 = new DataSchemaElement();
		dse9.setName("SecurityID");
		dse9.setDataType(FieldTypes.INT);
		dse9.setIsArray(false);
		dse9.setPosition(5);
		ds.addElement(dse9);

		DataSchemaElement dse10 = new DataSchemaElement();
		dse10.setName("ISIN");
		dse10.setDataType(FieldTypes.STRING);
		dse10.setIsArray(false);
		dse10.setPosition(0);
		ds.addElement(dse10);

		DataSchemaElement dse11 = new DataSchemaElement();
		dse11.setName("Time");
		dse11.setDataType(FieldTypes.STRING);
		dse11.setIsArray(false);
		dse11.setPosition(7);
		ds.addElement(dse11);

		QuerySchema qs = new QuerySchema();
		qs.setName("xerta");
		qs.setSelectorField("Mnemonic");
		qs.setDataSchema(ds);

		QuerySchemaElement field1 = new QuerySchemaElement();
		field1.setLengthType("variable");
		field1.setName("Mnemonic");
		field1.setSize(20);
		field1.setMaxArrayElements(1);
		qs.addElement(field1);

		QuerySchemaElement field2 = new QuerySchemaElement();
		field2.setLengthType("variable");
		field2.setName("Currency");
		field2.setSize(16);
		field2.setMaxArrayElements(1);
		qs.addElement(field2);

		QuerySchemaElement field3 = new QuerySchemaElement();
		field3.setLengthType("fixed");
		field3.setName("MaxPrice");
		field3.setSize(4);
		field3.setMaxArrayElements(1);
		qs.addElement(field3);

		QuerySchemaElement field4 = new QuerySchemaElement();
		field4.setLengthType("fixed");
		field4.setName("MinPrice");
		field4.setSize(4);
		field4.setMaxArrayElements(1);
		qs.addElement(field4);

		QuerySchemaElement field5 = new QuerySchemaElement();
		field5.setLengthType("variable");
		field5.setName("Date");
		field5.setSize(20);
		field5.setMaxArrayElements(1);
		qs.addElement(field5);

		QuerySchemaElement field6 = new QuerySchemaElement();
		field6.setLengthType("variable");
		field6.setName("SecurityType");
		field6.setSize(30);
		field6.setMaxArrayElements(1);
		qs.addElement(field6);

		QuerySchemaElement field7 = new QuerySchemaElement();
		field7.setLengthType("variable");
		field7.setName("SecurityDesc");
		field7.setSize(1000);
		field7.setMaxArrayElements(1);
		qs.addElement(field7);

		return qs;
	}

}
