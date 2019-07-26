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
package org.enquery.encryptedquery.flink.jdbc;

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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.xml.bind.JAXBException;

import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.api.java.LocalEnvironment;
import org.apache.flink.configuration.AkkaOptions;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.CoreOptions;
import org.apache.flink.configuration.TaskManagerOptions;
import org.enquery.encryptedquery.core.FieldType;
import org.enquery.encryptedquery.data.ClearTextQueryResponse;
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

public class ResponderTest extends JDBCTestBase {

	private final Logger log = LoggerFactory.getLogger(ResponderTest.class);

	private static final Integer DATA_CHUNK_SIZE = 1;
	private static final Integer HASH_BIT_SIZE = 8;
	private static final Path RESPONSE_FILE_NAME = Paths.get("target/response.xml");
	private static final Path QUERY_FILE_NAME = Paths.get("target/query.xml");
	private static final Path CONFIG_FILE_NAME = Paths.get("target/test-classes/", "config.properties");

	public static final int modulusBitSize = 384;
	public static final int certainty = 128;
	private static final String SELECTOR = "A Cup of Java";
	private static final List<String> SELECTORS = Arrays.asList(new String[] {SELECTOR});

	private QuerySchema querySchema;
	private QueryTypeConverter queryConverter;
	private ResponseTypeConverter responseConverter;
	private QueryKey queryKey;
	private Map<String, String> config;
	private PaillierCryptoScheme crypto;

	@Before
	public void prepare() throws Exception {
		Files.deleteIfExists(RESPONSE_FILE_NAME);
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

		querySchema = createQuerySchema();

		Querier querier = createQuerier("Books", SELECTORS);
		queryKey = querier.getQueryKey();

		// save the query
		try (OutputStream os = new FileOutputStream(QUERY_FILE_NAME.toFile())) {
			queryConverter.marshal(queryConverter.toXMLQuery(querier.getQuery()), os);
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
	public void test() throws Exception {

		Configuration cfg = new Configuration();
		cfg.setString(AkkaOptions.ASK_TIMEOUT, "2 min");
		cfg.setString(AkkaOptions.CLIENT_TIMEOUT, "2 min");
		cfg.setInteger(CoreOptions.DEFAULT_PARALLELISM, 4);
		cfg.setInteger(TaskManagerOptions.NUM_TASK_SLOTS, 4);

		LocalEnvironment env = ExecutionEnvironment.createLocalEnvironment(cfg);

		try (Responder q = new Responder()) {
			q.setInputFileName(QUERY_FILE_NAME);
			q.setOutputFileName(RESPONSE_FILE_NAME);
			q.setDriverClassName(DRIVER_CLASS);
			q.setConnectionUrl(DB_URL);
			q.setSqlQuery(SELECT_ALL_BOOKS);
			q.setConfig(config);
			q.run(env);
		}

		Response response = loadResponseFile();
		response.getQueryInfo().printQueryInfo();

		log.info("# Response records: ", response.getResponseElements().size());

		DecryptResponse dr = new DecryptResponse();
		ExecutorService es = Executors.newCachedThreadPool();
		dr.setCrypto(crypto);
		dr.setExecutionService(es);
		dr.activate();

		ClearTextQueryResponse answer = dr.decrypt(response, queryKey);
		assertNotNull(answer);
		assertEquals(1, answer.selectorCount());
		int[] hitCount = {0};
		int[] recordCount = {0};
		answer.forEach(sel -> {
			log.info("Selector {}", sel);
			assertEquals("title", sel.getName());
			sel.forEachHits(h -> {
				hitCount[0]++;
				assertEquals("A Cup of Java", h.getSelectorValue());
				assertEquals(1, h.recordCount());
				h.forEachRecord(r -> {
					recordCount[0]++;
					r.forEachField(f -> {
						log.info("Field {}.", f);
						if (f.getName().equalsIgnoreCase("price")) {
							assertEquals(Double.valueOf("44.44"), f.getValue());
						} else if (f.getName().equalsIgnoreCase("release_dt")) {
							assertTrue("2001-01-03T11:18:00.000Z".equals(f.getValue().toString()) || "2001-01-03T11:18:00Z".equals(f.getValue().toString()));
							// assertEquals("2001-01-03T11:18:00.000Z", f.getValue());
						}
					});
				});
			});
		});

		assertEquals(1, hitCount[0]);
		assertEquals(1, recordCount[0]);
	}

	private Response loadResponseFile() throws FileNotFoundException, IOException, JAXBException {
		try (FileInputStream fis = new FileInputStream(RESPONSE_FILE_NAME.toFile())) {
			org.enquery.encryptedquery.xml.schema.Response xml = responseConverter.unmarshal(fis);
			return responseConverter.toCore(xml);
		}
	}

	@After
	public void clearOutputTable() throws Exception {
		Class.forName(DRIVER_CLASS);
		try (Connection conn = DriverManager.getConnection(DB_URL);
				Statement stat = conn.createStatement()) {
			stat.execute("DELETE FROM " + OUTPUT_TABLE);
		}
	}


	private Querier createQuerier(String queryType, List<String> selectors) throws Exception {
		RandomProvider randomProvider = new RandomProvider();
		EncryptQuery queryEnc = new EncryptQuery();
		queryEnc.setCrypto(crypto);
		queryEnc.setRandomProvider(randomProvider);

		return queryEnc.encrypt(querySchema, selectors, DATA_CHUNK_SIZE, HASH_BIT_SIZE);
	}

	private QuerySchema createQuerySchema() {
		int pos = 0;
		DataSchema ds = new DataSchema();
		ds.setName("Books");
		DataSchemaElement dse1 = new DataSchemaElement();
		dse1.setName("id");
		dse1.setDataType(FieldType.INT);
		dse1.setPosition(pos++);
		ds.addElement(dse1);

		DataSchemaElement dse2 = new DataSchemaElement();
		dse2.setName("title");
		dse2.setDataType(FieldType.STRING);
		dse2.setPosition(pos++);
		ds.addElement(dse2);

		DataSchemaElement dse3 = new DataSchemaElement();
		dse3.setName("author");
		dse3.setDataType(FieldType.STRING);
		dse3.setPosition(pos++);
		ds.addElement(dse3);

		DataSchemaElement dse4 = new DataSchemaElement();
		dse4.setName("price");
		dse4.setDataType(FieldType.DOUBLE);
		dse4.setPosition(pos++);
		ds.addElement(dse4);

		DataSchemaElement dse5 = new DataSchemaElement();
		dse5.setName("qty");
		dse5.setDataType(FieldType.INT);
		dse5.setPosition(pos++);
		ds.addElement(dse5);

		DataSchemaElement dse6 = new DataSchemaElement();
		dse6.setName("release_dt");
		dse6.setDataType(FieldType.ISO8601DATE);
		dse6.setPosition(pos++);
		ds.addElement(dse6);


		QuerySchema qs = new QuerySchema();
		qs.setName("Books");
		qs.setSelectorField("title");
		qs.setDataSchema(ds);

		QuerySchemaElement field1 = new QuerySchemaElement();
		field1.setName("id");
		qs.addElement(field1);

		QuerySchemaElement field2 = new QuerySchemaElement();
		field2.setName("title");
		qs.addElement(field2);

		QuerySchemaElement field3 = new QuerySchemaElement();
		field3.setName("author");
		field3.setMaxArrayElements(1);
		qs.addElement(field3);

		QuerySchemaElement field4 = new QuerySchemaElement();
		field4.setName("price");
		field4.setMaxArrayElements(1);
		qs.addElement(field4);


		QuerySchemaElement field5 = new QuerySchemaElement();
		field5.setName("release_dt");
		field5.setMaxArrayElements(1);
		qs.addElement(field5);

		return qs;
	}
}
