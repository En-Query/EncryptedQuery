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

import org.enquery.encryptedquery.core.FieldTypes;
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

	@Test
	public void test() throws Exception {

		Responder q = new Responder();
		q.setInputFileName(QUERY_FILE_NAME);
		q.setOutputFileName(RESPONSE_FILE_NAME);
		q.setDriverClassName(DRIVER_CLASS);
		q.setConnectionUrl(DB_URL);
		q.setSqlQuery(SELECT_ALL_BOOKS);
		q.setConfig(config);
		q.run();

		Response response = loadResponseFile();
		response.getQueryInfo().printQueryInfo();

		log.info("# Response records: ", response.getResponseElements().size());

		DecryptResponse dr = new DecryptResponse();
		ExecutorService es = Executors.newCachedThreadPool();
		dr.setCrypto(crypto);
		dr.setExecutionService(es);
		dr.activate();

		ClearTextQueryResponse answer = dr.decrypt(response, queryKey);
		answer.forEach(sel -> {
			log.info("Selector {}", sel);
			assertEquals("title", sel.getName());
			sel.forEachHits(h -> {
				assertEquals("A Cup of Java", h.getSelectorValue());
				assertEquals(1, h.recordCount());
				h.forEachRecord(r -> {
					r.forEachField(f -> {
						log.info("Field {}.", f);
						if (f.getName().equalsIgnoreCase("price")) {
							assertEquals(Double.valueOf("44.44"), f.getValue());
						} else if (f.getName().equalsIgnoreCase("release_dt")) {
							assertEquals("2001-01-03T11:18:00.000Z", f.getValue());
						}
					});
				});
			});
		});
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

		return queryEnc.encrypt(querySchema, selectors, true, DATA_CHUNK_SIZE, HASH_BIT_SIZE);
	}

	private QuerySchema createQuerySchema() {
		DataSchema ds = new DataSchema();
		ds.setName("Books");
		DataSchemaElement dse1 = new DataSchemaElement();
		dse1.setName("id");
		dse1.setDataType(FieldTypes.INT);
		dse1.setIsArray(false);
		dse1.setPosition(0);
		ds.addElement(dse1);

		DataSchemaElement dse2 = new DataSchemaElement();
		dse2.setName("title");
		dse2.setDataType(FieldTypes.STRING);
		dse2.setIsArray(false);
		dse2.setPosition(1);
		ds.addElement(dse2);

		DataSchemaElement dse3 = new DataSchemaElement();
		dse3.setName("author");
		dse3.setDataType(FieldTypes.STRING);
		dse3.setIsArray(false);
		dse3.setPosition(2);
		ds.addElement(dse3);

		DataSchemaElement dse4 = new DataSchemaElement();
		dse4.setName("price");
		dse4.setDataType(FieldTypes.DOUBLE);
		dse4.setIsArray(false);
		dse4.setPosition(3);
		ds.addElement(dse4);

		DataSchemaElement dse5 = new DataSchemaElement();
		dse5.setName("qty");
		dse5.setDataType(FieldTypes.INT);
		dse5.setIsArray(false);
		dse5.setPosition(4);
		ds.addElement(dse5);

		DataSchemaElement dse6 = new DataSchemaElement();
		dse6.setName("release_dt");
		dse6.setDataType(FieldTypes.ISO8601DATE);
		dse6.setIsArray(false);
		dse6.setPosition(5);
		ds.addElement(dse6);


		QuerySchema qs = new QuerySchema();
		qs.setName("Books");
		qs.setSelectorField("title");
		qs.setDataSchema(ds);

		QuerySchemaElement field1 = new QuerySchemaElement();
		field1.setLengthType("fixed");
		field1.setName("id");
		field1.setSize(4);
		field1.setMaxArrayElements(1);
		qs.addElement(field1);

		QuerySchemaElement field2 = new QuerySchemaElement();
		field2.setLengthType("variable");
		field2.setName("title");
		field2.setSize(128);
		field2.setMaxArrayElements(1);
		qs.addElement(field2);

		QuerySchemaElement field3 = new QuerySchemaElement();
		field3.setLengthType("fixed");
		field3.setName("author");
		field3.setSize(50);
		field3.setMaxArrayElements(1);
		qs.addElement(field3);

		QuerySchemaElement field4 = new QuerySchemaElement();
		field4.setLengthType("fixed");
		field4.setName("price");
		field4.setSize(8);
		field4.setMaxArrayElements(1);
		qs.addElement(field4);


		QuerySchemaElement field5 = new QuerySchemaElement();
		field5.setLengthType("fixed");
		field5.setName("release_dt");
		field5.setSize(30);
		field5.setMaxArrayElements(1);
		qs.addElement(field5);

		return qs;
	}
}
