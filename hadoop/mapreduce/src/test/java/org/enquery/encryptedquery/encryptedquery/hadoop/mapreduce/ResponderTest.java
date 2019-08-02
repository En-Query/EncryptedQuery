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
import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.xml.bind.JAXBException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocalFileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.HdfsConfiguration;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.test.PathUtils;
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
import org.enquery.encryptedquery.hadoop.core.HDFSFileIOUtils;
import org.enquery.encryptedquery.hadoop.core.HadoopConfigurationProperties;
import org.enquery.encryptedquery.hadoop.mapreduce.Responder;
import org.enquery.encryptedquery.querier.decrypt.DecryptResponse;
import org.enquery.encryptedquery.querier.encrypt.EncryptQuery;
import org.enquery.encryptedquery.querier.encrypt.Querier;
import org.enquery.encryptedquery.utils.FileIOUtils;
import org.enquery.encryptedquery.utils.RandomProvider;
import org.enquery.encryptedquery.xml.transformation.QueryTypeConverter;
import org.enquery.encryptedquery.xml.transformation.ResponseTypeConverter;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
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
	private static final java.nio.file.Path DATA_FILE_NAME = Paths.get("target/test-classes/", "phone-data.json");
	private static String HDFS_WORKING_FOLDER;

	private static final String SELECTOR = "275-913-7889";
	private static final List<String> SELECTORS = Arrays.asList(new String[] {SELECTOR});

	private QuerySchema querySchema;
	private PaillierCryptoScheme crypto;
	private ResponseTypeConverter responseConverter;
	private QueryKey queryKey;
	private Query query;

	private Map<String, String> config;

	private QueryTypeConverter queryConverter;

	@Before
	public void prepare() throws Exception {
		log.info("Start initializing test.");

		Files.deleteIfExists(RESPONSE_FILE_NAME);
		Files.deleteIfExists(QUERY_FILE_NAME);

		testDataPath = new File(PathUtils.getTestDir(getClass()),
				"miniclusters");

		System.clearProperty(MiniDFSCluster.PROP_TEST_BUILD_DATA);
		conf = new HdfsConfiguration();

		File testDataCluster1 = new File(testDataPath, CLUSTER_1);
		String c1Path = testDataCluster1.getAbsolutePath();
		conf.set(MiniDFSCluster.HDFS_MINIDFS_BASEDIR, c1Path);
		cluster = new MiniDFSCluster.Builder(conf).build();

		fs = FileSystem.get(conf);

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

		querySchema = createPhoneDataQuerySchema();

		Querier querier = createQuerier("PhoneData", SELECTORS);
		queryKey = querier.getQueryKey();
		query = querier.getQuery();

		// save the query
		try (OutputStream os = new FileOutputStream(QUERY_FILE_NAME.toFile())) {
			queryConverter.marshal(queryConverter.toXMLQuery(querier.getQuery()), os);
		}

		HDFS_WORKING_FOLDER = config.get(HadoopConfigurationProperties.HDFSWORKINGFOLDER);
		HDFSFileIOUtils.copyLocalFileToHDFS(fs, HDFS_WORKING_FOLDER, DATA_FILE_NAME);
		HDFSFileIOUtils.copyLocalFileToHDFS(fs, HDFS_WORKING_FOLDER, CONFIG_FILE_NAME);
		HDFSFileIOUtils.copyLocalFileToHDFS(fs, HDFS_WORKING_FOLDER, QUERY_FILE_NAME);


		log.info("Finished initializing test.");
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
	public void testFile() throws Exception {

		Path hdfsInputFile = new Path(HDFS_WORKING_FOLDER + Path.SEPARATOR + DATA_FILE_NAME.getFileName().toString());

		log.info("Running Hadoop Map Reduce..");
		Responder q = new Responder();
		q.setInputFileName(hdfsInputFile);
		q.setOutputFileName(RESPONSE_FILE_NAME);
		q.setConfig(config);
		q.setQueryFileName(QUERY_FILE_NAME);
		q.setConfigFileName(CONFIG_FILE_NAME);
		q.setHdfs(fs);
		q.setV1ProcessingMethod(false);
		q.run();

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
		Selector selector = answer.selectorByName("Caller #");
		assertNotNull(selector);
		selector.forEachHits(hits -> {
			assertEquals("275-913-7889", hits.getSelectorValue());
			hits.forEachRecord(record -> {
				record.forEachField(field -> {
					log.info("Field {}", field);
					returnedFields.put(field.getName(), field.getValue());
				});
			});
		});
	}

	private void writeToHDFS(FileSystem hdfs, Object objToWrite, String hdfsFileName) throws IOException {
		log.info("Writing {} into HDFS", hdfsFileName);

		org.apache.hadoop.fs.Path hdfswritepath = new org.apache.hadoop.fs.Path(hdfsFileName);

		if (hdfs.exists(hdfswritepath)) {
			hdfs.delete(hdfswritepath, false);
		}
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ObjectOutput out = null;
		byte[] outputBytes = null;
		try {
			out = new ObjectOutputStream(bos);
			out.writeObject(objToWrite);
			out.flush();
			outputBytes = bos.toByteArray();
		} finally {
			try {
				bos.close();
			} catch (IOException ex) {
				log.error("Failed to convert object {} to byte array", objToWrite.getClass().getName());
			}
		}

		FSDataOutputStream outputStream = hdfs.create(hdfswritepath);
		outputStream.write(outputBytes);
		outputStream.close();
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
		int dataChunkSize = 1;
		int hashBitSize = 9;
		return queryEnc.encrypt(querySchema, selectors, dataChunkSize, hashBitSize);
	}


	private QuerySchema createPhoneDataQuerySchema() {
		DataSchema ds = new DataSchema();
		ds.setName("phone-data");
		DataSchemaElement dse1 = new DataSchemaElement();
		dse1.setName("Caller #");
		dse1.setDataType(FieldType.STRING);
		dse1.setPosition(0);
		ds.addElement(dse1);

		DataSchemaElement dse2 = new DataSchemaElement();
		dse2.setName("Callee #");
		dse2.setDataType(FieldType.STRING);
		dse2.setPosition(1);
		ds.addElement(dse2);

		DataSchemaElement dse3 = new DataSchemaElement();
		dse3.setName("Duration");
		dse3.setDataType(FieldType.STRING);
		dse3.setPosition(2);
		ds.addElement(dse3);

		DataSchemaElement dse4 = new DataSchemaElement();
		dse4.setName("Date/Time");
		dse4.setDataType(FieldType.STRING);
		dse4.setPosition(3);
		ds.addElement(dse4);


		QuerySchema qs = new QuerySchema();
		qs.setName("phone-data");
		qs.setSelectorField("Caller #");
		qs.setDataSchema(ds);

		QuerySchemaElement field1 = new QuerySchemaElement();
		field1.setName("Caller #");
		qs.addElement(field1);

		QuerySchemaElement field2 = new QuerySchemaElement();
		field2.setName("Callee #");
		qs.addElement(field2);

		QuerySchemaElement field3 = new QuerySchemaElement();
		field3.setName("Duration");
		qs.addElement(field3);

		QuerySchemaElement field4 = new QuerySchemaElement();
		field4.setName("Date/Time");
		qs.addElement(field4);

		return qs;
	}

}
