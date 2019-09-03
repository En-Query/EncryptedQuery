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
package org.enquery.encryptedquery.encryptedquery.hadoop.mapreduce;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import javax.xml.bind.JAXBException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocalFileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.HdfsConfiguration;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.test.PathUtils;
import org.apache.hadoop.util.ToolRunner;
import org.enquery.encryptedquery.core.FieldType;
import org.enquery.encryptedquery.data.ClearTextQueryResponse;
import org.enquery.encryptedquery.data.ClearTextQueryResponse.Hits;
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
import org.enquery.encryptedquery.hadoop.core.HDFSFileIOUtils;
import org.enquery.encryptedquery.hadoop.mapreduce.Responder;
import org.enquery.encryptedquery.querier.decrypt.DecryptResponse;
import org.enquery.encryptedquery.querier.encrypt.EncryptQuery;
import org.enquery.encryptedquery.querier.encrypt.Querier;
import org.enquery.encryptedquery.utils.FileIOUtils;
import org.enquery.encryptedquery.utils.PIRException;
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

	private static final String CLUSTER_1 = "cluster1";
	private File testDataPath;
	private Configuration conf;
	private MiniDFSCluster cluster;
	private FileSystem fs;

	private static final java.nio.file.Path RESPONSE_FILE_NAME = Paths.get("target/response.xml");
	private static final java.nio.file.Path QUERY_FILE_NAME = Paths.get("target/query.xml");
	private static final java.nio.file.Path CONFIG_FILE_NAME = Paths.get("target/test-classes/", "config.cfg");
	private static final java.nio.file.Path HADOOP_CONFIG_FILE_NAME = Paths.get("target/test-classes/", "hadoop.xml");
	private static final java.nio.file.Path DATA_FILE_NAME = Paths.get("target/test-classes/", "phone-data.json");
	private static final String HDFS_WORKING_FOLDER = "testing";

	private static final String SELECTOR = "275-913-7889";
	private static final List<String> SELECTORS = Arrays.asList(new String[] {SELECTOR});

	private QuerySchema querySchema;
	private PaillierCryptoScheme crypto;
	private ResponseTypeConverter responseConverter;
	private QueryKey queryKey;

	private QueryTypeConverter queryConverter;

	private DecryptResponse decryptResponse;

	@Before
	public void prepare() throws Exception {
		log.info("Start initializing test.");

		Files.deleteIfExists(RESPONSE_FILE_NAME);
		Files.deleteIfExists(QUERY_FILE_NAME);

		testDataPath = new File(PathUtils.getTestDir(getClass()), "miniclusters");

		System.clearProperty(MiniDFSCluster.PROP_TEST_BUILD_DATA);
		conf = new HdfsConfiguration();

		File testDataCluster1 = new File(testDataPath, CLUSTER_1);
		String c1Path = testDataCluster1.getAbsolutePath();
		conf.set(MiniDFSCluster.HDFS_MINIDFS_BASEDIR, c1Path);
		conf.set("hadoop.tmp.dir", "target/tmp");
		// System.setProperty("hadoop.tmp.dir", "target/tmp");
		conf.set("mapreduce.jobtracker.staging.root.dir", "target/tmp");
		cluster = new MiniDFSCluster.Builder(conf).build();

		fs = FileSystem.get(conf);

		Map<String, String> config = FileIOUtils.loadPropertyFile(CONFIG_FILE_NAME);
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

		decryptResponse = new DecryptResponse();
		decryptResponse.setCryptoRegistry(cryptoRegistry);
		decryptResponse.setExecutionService(Executors.newCachedThreadPool());

		querySchema = createPhoneDataQuerySchema();

		HDFSFileIOUtils.copyLocalFileToHDFS(fs, HDFS_WORKING_FOLDER, DATA_FILE_NAME);

		log.info("Finished initializing test.");
	}

	private void createQuery() throws Exception {
		createQuery(null);
	}


	private void createQuery(String filter) throws Exception {
		Querier querier = createQuerier(SELECTORS, filter);
		queryKey = querier.getQueryKey();

		// save the query
		try (OutputStream os = new FileOutputStream(QUERY_FILE_NAME.toFile())) {
			queryConverter.marshal(queryConverter.toXMLQuery(querier.getQuery()), os);
		}
	}

	@After
	public void tearDown() throws Exception {
		Path dataDir = new Path(
				testDataPath.getParentFile().getParentFile().getParent());
		fs.delete(dataDir, true);
		File rootTestFile = new File(testDataPath.getParentFile().getParentFile().getParent());
		String rootTestDir = rootTestFile.getAbsolutePath();
		Path rootTestPath = new Path(rootTestDir);
		LocalFileSystem localFileSystem = FileSystem.getLocal(conf);

		cluster.shutdown();
		localFileSystem.delete(rootTestPath, true);
	}

	@Test
	public void testWithFilter() throws Exception {
		createQuery("Duration > 200");
		run("v2");
		ClearTextQueryResponse answer = decryptResult();
		assertEquals(1, answer.selectorCount());
		Selector sel = answer.selectorByName("Caller");
		assertEquals("Caller", sel.getName());
		assertEquals(1, sel.hitCount());
		Hits h = sel.hitsBySelectorValue("275-913-7889");
		assertEquals("275-913-7889", h.getSelectorValue());
		assertEquals(0, h.recordCount());

	}


	@Test
	public void testNoFilterV1() throws Exception {
		createQuery();
		run("v1");
		ClearTextQueryResponse answer = decryptResult();
		assertEquals(1, answer.selectorCount());
		Selector sel = answer.selectorByName("Caller");
		assertEquals("Caller", sel.getName());
		assertEquals(1, sel.hitCount());
		Hits h = sel.hitsBySelectorValue("275-913-7889");
		assertEquals("275-913-7889", h.getSelectorValue());
		assertEquals(2, h.recordCount());
	}

	@Test
	public void testNoFilterV2() throws Exception {
		createQuery();
		run("v2");
		ClearTextQueryResponse answer = decryptResult();
		assertEquals(1, answer.selectorCount());
		Selector sel = answer.selectorByName("Caller");
		assertEquals("Caller", sel.getName());
		assertEquals(1, sel.hitCount());
		Hits h = sel.hitsBySelectorValue("275-913-7889");
		assertEquals("275-913-7889", h.getSelectorValue());
		assertEquals(2, h.recordCount());
	}

	private ClearTextQueryResponse decryptResult() throws FileNotFoundException, IOException, JAXBException, InterruptedException, PIRException, ClassNotFoundException, InstantiationException, IllegalAccessException {
		Response response = loadFile();
		response.getQueryInfo().printQueryInfo();

		log.info("# Response records: ", response.getResponseElements().size());

		ClearTextQueryResponse answer = decryptResponse.decrypt(response, queryKey);
		log.info(answer.toString());
		return answer;
	}

	private void run(String version) throws Exception {
		Path hdfsInputFile = new Path(HDFS_WORKING_FOLDER + Path.SEPARATOR + DATA_FILE_NAME.getFileName().toString());

		Map<String, String> config = FileIOUtils.loadPropertyFile(CONFIG_FILE_NAME);
		config.put("processing.method", version);
		java.nio.file.Path configFile = Paths.get("target/config.properties");
		FileIOUtils.savePropertyFile(configFile, config);

		log.info("Running Hadoop Map Reduce.");
		String[] args = new String[] {
				"-D",
				"hdfs.port=" + Integer.toString(cluster.getNameNodePort()),
				"-conf",
				HADOOP_CONFIG_FILE_NAME.toString(),
				"-files",
				QUERY_FILE_NAME.toString() + "#query.xml," + CONFIG_FILE_NAME.toString() + "#config.properties",
				"-i",
				hdfsInputFile.toString(),
				"-q",
				QUERY_FILE_NAME.toString(),
				"-sp",
				configFile.toString(),
				"-o",
				RESPONSE_FILE_NAME.toString()
		};

		int exitCode = ToolRunner.run(new Responder(), args);
		assertEquals(0, exitCode);
	}

	private Response loadFile() throws FileNotFoundException, IOException, JAXBException {
		try (FileInputStream fis = new FileInputStream(RESPONSE_FILE_NAME.toFile())) {
			org.enquery.encryptedquery.xml.schema.Response xml = responseConverter.unmarshal(fis);
			return responseConverter.toCore(xml);
		}
	}

	private Querier createQuerier(List<String> selectors, String filter) throws Exception {
		RandomProvider randomProvider = new RandomProvider();
		EncryptQuery queryEnc = new EncryptQuery();
		queryEnc.setCrypto(crypto);
		queryEnc.setRandomProvider(randomProvider);
		return queryEnc.encrypt(querySchema, selectors, 1, 9, filter);
	}


	private QuerySchema createPhoneDataQuerySchema() {
		int pos = 0;

		DataSchema ds = new DataSchema();
		ds.setName("phone-data");
		DataSchemaElement dse = new DataSchemaElement();

		dse = new DataSchemaElement();
		dse.setName("Duration");
		dse.setDataType(FieldType.INT);
		dse.setPosition(pos++);
		ds.addElement(dse);

		dse = new DataSchemaElement();
		dse.setName("Caller");
		dse.setDataType(FieldType.STRING);
		dse.setPosition(pos++);
		ds.addElement(dse);

		dse = new DataSchemaElement();
		dse.setName("Callee");
		dse.setDataType(FieldType.STRING);
		dse.setPosition(pos++);
		ds.addElement(dse);

		dse = new DataSchemaElement();
		dse.setName("Timestamp");
		dse.setDataType(FieldType.ISO8601DATE);
		dse.setPosition(pos++);
		ds.addElement(dse);

		QuerySchema qs = new QuerySchema();
		qs.setName("phone-data");
		qs.setSelectorField("Caller");
		qs.setDataSchema(ds);

		QuerySchemaElement field1 = new QuerySchemaElement();
		field1.setName("Caller");
		qs.addElement(field1);

		QuerySchemaElement field2 = new QuerySchemaElement();
		field2.setName("Callee");
		qs.addElement(field2);

		QuerySchemaElement field3 = new QuerySchemaElement();
		field3.setName("Duration");
		qs.addElement(field3);

		QuerySchemaElement field4 = new QuerySchemaElement();
		field4.setName("Timestamp");
		qs.addElement(field4);

		return qs;
	}

}
