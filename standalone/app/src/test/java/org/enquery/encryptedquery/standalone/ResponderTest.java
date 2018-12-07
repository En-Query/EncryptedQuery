/*
 * EncryptedQuery is an open source project allowing user to query databases with queries under homomorphic encryption to securing the query and results set from database owner inspection. 
 * Copyright (C) 2018  EnQuery LLC 
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.enquery.encryptedquery.standalone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.xml.bind.JAXBException;

import org.enquery.encryptedquery.data.ClearTextQueryResponse;
import org.enquery.encryptedquery.data.ClearTextQueryResponse.Selector;
import org.enquery.encryptedquery.data.DataSchema;
import org.enquery.encryptedquery.data.DataSchemaElement;
import org.enquery.encryptedquery.data.QuerySchema;
import org.enquery.encryptedquery.data.QuerySchemaElement;
import org.enquery.encryptedquery.encryption.ModPowAbstraction;
import org.enquery.encryptedquery.encryption.PaillierEncryption;
import org.enquery.encryptedquery.encryption.PrimeGenerator;
import org.enquery.encryptedquery.encryption.impl.ModPowAbstractionJavaImpl;
import org.enquery.encryptedquery.querier.wideskies.decrypt.DecryptResponse;
import org.enquery.encryptedquery.querier.wideskies.encrypt.EncryptQuery;
import org.enquery.encryptedquery.querier.wideskies.encrypt.EncryptionPropertiesBuilder;
import org.enquery.encryptedquery.querier.wideskies.encrypt.Querier;
import org.enquery.encryptedquery.querier.wideskies.encrypt.QuerierFactory;
import org.enquery.encryptedquery.querier.wideskies.encrypt.QueryKey;
import org.enquery.encryptedquery.query.wideskies.Query;
import org.enquery.encryptedquery.response.wideskies.Response;
import org.enquery.encryptedquery.utils.FileIOUtils;
import org.enquery.encryptedquery.utils.RandomProvider;
import org.enquery.encryptedquery.xml.transformation.QueryKeyTypeConverter;
import org.enquery.encryptedquery.xml.transformation.QueryTypeConverter;
import org.enquery.encryptedquery.xml.transformation.ResponseTypeConverter;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ResponderTest {


	private final Logger log = LoggerFactory.getLogger(ResponderTest.class);

	private static final Integer DATA_CHUNK_SIZE = 1;
	private static final Integer HASH_BIT_SIZE = 9;
	private static final Path RESPONSE_FILE_NAME = Paths.get("target/response.xml");
	private static final Path QUERY_FILE_NAME = Paths.get("target/query.xml");
	private static final Path CONFIG_FILE_NAME = Paths.get("target/test-classes/", "config.cfg");
	private static final Path DATA_FILE_NAME = Paths.get("target/test-classes/", "simple-data.json");
	private static final Path CDR_DATA_FILE = Paths.get("target/test-classes/", "cdr5.json");
	private static final Path CDR_QUERY_FILE = Paths.get("target/test-classes/", "cdr5-query.xml");
	private static final Path CDR_QUERY_KEY_FILE = Paths.get("target/test-classes/", "cdr5-query-key.xml");

	public static final int paillierBitSize = 384;  
	public static final int certainty = 128;
	private static final String SELECTOR = "31";
	private static final List<String> SELECTORS = Arrays.asList(new String[] {SELECTOR});

	private QuerySchema querySchema;
	private PaillierEncryption paillierEnc;
	private EncryptQuery queryEnc;
	private ModPowAbstraction modPow;
	private QuerierFactory querierFactory;
	private PrimeGenerator primeGenerator;
	private RandomProvider randomProvider;
	private ExecutorService threadPool;
	
	// private QueryTypeConverter queryConverter;
	private ResponseTypeConverter responseConverter;
	private QueryKey queryKey;
	private Query query;

	@Before
	public void prepare() throws Exception {
		log.info("Start initializing test.");

		Files.deleteIfExists(RESPONSE_FILE_NAME);
		Files.deleteIfExists(QUERY_FILE_NAME);

		// queryConverter = new QueryTypeConverter();
		responseConverter = new ResponseTypeConverter();

		querySchema = createQuerySchema();

		threadPool = Executors.newFixedThreadPool(1);
		randomProvider = new RandomProvider();
		modPow = new ModPowAbstractionJavaImpl();
		primeGenerator = new PrimeGenerator(modPow, randomProvider);
		paillierEnc = new PaillierEncryption(modPow, primeGenerator, randomProvider);
		queryEnc = new EncryptQuery(modPow, paillierEnc, randomProvider, threadPool);
		querierFactory = new QuerierFactory(modPow, paillierEnc, queryEnc);

		Querier querier = createQuerier("People", SELECTORS);
		queryKey = querier.getQueryKey();
		query = querier.getQuery();

		log.info("Finished initializing test.");
	}

	@Test
	public void testCdr5File() throws Exception {

		Responder responder = new Responder();
		responder.setOutputFileName(RESPONSE_FILE_NAME);
		responder.setInputDataFile(CDR_DATA_FILE);
		responder.setQuery(loadQuery(CDR_QUERY_FILE));
		responder.run(FileIOUtils.loadPropertyFile(CONFIG_FILE_NAME));

		Response response = loadFile();
		response.getQueryInfo().printQueryInfo();

		log.info("# Response records: ", response.getResponseElements().size());

		DecryptResponse dr = new DecryptResponse();
		ExecutorService es = Executors.newCachedThreadPool();

		ClearTextQueryResponse answer = dr.decrypt(es, response, loadQueryKey(CDR_QUERY_KEY_FILE));
		log.info(answer.toString());

		assertEquals(1, answer.selectorCount());

		Selector selector = answer.selectorByName("Caller #");
		assertNotNull(selector);

		assertEquals(1, selector.hitCount());

		selector.forEachHits(hits -> {
			assertEquals("275-913-7889", hits.getSelectorValue());
			assertEquals(2, hits.recordCount());
			hits.forEachRecord(hit -> {
				hit.forEachField(field -> {
					log.info("Field {}", field);
					if (field.getName().equals("Caller #")) {
						assertEquals("275-913-7889", field.getValue());
					}
				});
			});
		});
	}

	@Test
	public void testXertaFile() throws Exception {

		Path CDR_DATA_FILE = Paths.get("target/test-classes/", "xerta-50.json");
		Path CDR_QUERY_FILE = Paths.get("target/test-classes/", "xerta-query.xml");
		Path CDR_QUERY_KEY_FILE = Paths.get("target/test-classes/", "xerta-query-key.xml");

		Responder responder = new Responder();
		responder.setOutputFileName(RESPONSE_FILE_NAME);
		responder.setInputDataFile(CDR_DATA_FILE);
		responder.setQuery(loadQuery(CDR_QUERY_FILE));
		responder.run(FileIOUtils.loadPropertyFile(CONFIG_FILE_NAME));

		Response response = loadFile();
		response.getQueryInfo().printQueryInfo();

		log.info("# Response records: ", response.getResponseElements().size());

		DecryptResponse dr = new DecryptResponse();
		ExecutorService es = Executors.newCachedThreadPool();

		ClearTextQueryResponse answer = dr.decrypt(es, response, loadQueryKey(CDR_QUERY_KEY_FILE));
		log.info(answer.toString());

		assertEquals(1, answer.selectorCount());

		Selector selector = answer.selectorByName("Mnemonic");
		assertNotNull(selector);

		assertEquals(2, selector.hitCount());

//		selector.forEachHits(hits -> {
//			assertEquals("TC1", hits.getSelectorValue());
//			assertEquals(1, hits.recordCount());
//			hits.forEachRecord(hit -> {
//				hit.forEachField(field -> {
//					log.info("Field {}", field);
//					if (field.getName().equals("Mnemonic")) {
//						assertEquals("TC1", field.getValue());
//					}
//				});
//			});
//		});
	}
	
	@SuppressWarnings("static-access")
	private QueryKey loadQueryKey(Path fileName) throws FileNotFoundException, IOException, JAXBException {
		QueryKeyTypeConverter qtc = new QueryKeyTypeConverter();
		try (InputStream is = new FileInputStream(fileName.toFile())) {
			return qtc.toCore(qtc.unmarshal(is));
		}
	}

	private Query loadQuery(Path fileName) throws JAXBException, FileNotFoundException, IOException {
		QueryTypeConverter qtc = new QueryTypeConverter();
		try (InputStream is = new FileInputStream(fileName.toFile())) {
			return qtc.toCoreQuery(qtc.unmarshal(is));
		}
	}

	@Test
	public void test() throws Exception {

		Responder responder = new Responder();
		responder.setOutputFileName(RESPONSE_FILE_NAME);
		responder.setInputDataFile(DATA_FILE_NAME);
		responder.setQuery(query);
		responder.run(FileIOUtils.loadPropertyFile(CONFIG_FILE_NAME));

		Response response = loadFile();
		response.getQueryInfo().printQueryInfo();

		log.info("# Response records: ", response.getResponseElements().size());

		DecryptResponse dr = new DecryptResponse();
		ExecutorService es = Executors.newCachedThreadPool();

		ClearTextQueryResponse answer = dr.decrypt(es, response, queryKey);
		log.info("answer: " + answer);

		final Map<String, Object> returnedFields = new HashMap<>();
		Selector sel = answer.selectorByName("age");
		sel.forEachHits(hits -> {
			hits.forEachRecord(record -> {
				record.forEachField(field -> {
					log.info("Field {}", field);
					returnedFields.put(field.getName(), field.getValue());
				});
			});
		});


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
		Properties baseTestEncryptionProperties = EncryptionPropertiesBuilder
				.newBuilder()
				.dataChunkSize(DATA_CHUNK_SIZE)
				.hashBitSize(HASH_BIT_SIZE)
				.paillierBitSize(paillierBitSize)
				.certainty(certainty)
				.embedSelector(true)
				.queryType(queryType)
				.build();

		return querierFactory.createQuerier(querySchema, UUID.randomUUID(), selectors, baseTestEncryptionProperties);
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


}
