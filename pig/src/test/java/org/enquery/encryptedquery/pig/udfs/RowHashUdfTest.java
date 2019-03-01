package org.enquery.encryptedquery.pig.udfs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.pig.ExecType;
import org.apache.pig.PigServer;
import org.enquery.encryptedquery.data.QuerySchema;
import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.enquery.encryptedquery.encryption.CryptoSchemeFactory;
import org.enquery.encryptedquery.encryption.CryptoSchemeRegistry;
import org.enquery.encryptedquery.encryption.ModPowAbstraction;
import org.enquery.encryptedquery.encryption.PrimeGenerator;
import org.enquery.encryptedquery.encryption.impl.ModPowAbstractionJavaImpl;
import org.enquery.encryptedquery.loader.SchemaLoader;
import org.enquery.encryptedquery.pig.mini.MiniCluster;
import org.enquery.encryptedquery.querier.encrypt.EncryptQuery;
import org.enquery.encryptedquery.querier.encrypt.Querier;
import org.enquery.encryptedquery.utils.FileIOUtils;
import org.enquery.encryptedquery.utils.RandomProvider;
import org.enquery.encryptedquery.xml.transformation.QueryTypeConverter;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("needs to be fixed")
@SuppressWarnings("unused")
public class RowHashUdfTest {

	@SuppressWarnings("deprecation")
	static private MiniCluster cluster = MiniCluster.buildCluster();
	private PigServer pigServer;

	private static final Integer DATA_CHUNK_SIZE = 1;
	private static final Integer HASH_BIT_SIZE = 9;

	public static final int modulusBitSize = 384;
	public static final int certainty = 128;
	private static final String SELECTOR = "A Cup of Java";
	private static final List<String> SELECTORS = Arrays.asList(new String[] {SELECTOR});

	private QuerySchema querySchema;
	private Querier querier;

	// private PaillierEncryption paillierEnc;
	private EncryptQuery queryEnc;
	private ModPowAbstraction modPow;
	// private QuerierFactory querierFactory;
	private PrimeGenerator primeGenerator;
	private RandomProvider randomProvider;
	private ExecutorService threadPool;
	private QueryTypeConverter queryConverter;
	// private ResponseTypeConverter responseConverter;
	private Map<String, String> config;


	private static final String RESOURCES_DIR = "target/test-classes/";
	private static final Path SCHEMAS_DIR = Paths.get(RESOURCES_DIR, "schemas");
	private static final Path QUERY_SCHEMA = Paths.get(SCHEMAS_DIR.toString(), "get-price-query-schema.xml");
	private static final Path CONFIG_FILE_NAME = Paths.get(RESOURCES_DIR.toString(), "config.properties");

	private static final String TEST_DATA = Paths.get(RESOURCES_DIR, "test-data.txt").toString();
	private static final String RESPONSE_FILE_NAME = "target/response.xml";
	private static final String QUERY_FILE_NAME = "target/query.xml";
	private static final String SCRIPT_FILE_NAME = Paths.get(RESOURCES_DIR, "row-hash.pig").toString();



	@AfterClass
	public static void oneTimeTearDown() throws Exception {
		cluster.shutDown();
	}

	@Before
	public void prepare() throws Exception {

		System.setProperty("pigunit.exectype", "mr");
		pigServer = new PigServer(ExecType.MAPREDUCE);

		config = FileIOUtils.loadPropertyFile(CONFIG_FILE_NAME);
		final CryptoScheme crypto = CryptoSchemeFactory.make(config);

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

		querySchema = new SchemaLoader().loadQuerySchema(QUERY_SCHEMA);

		threadPool = Executors.newFixedThreadPool(1);
		randomProvider = new RandomProvider();
		modPow = new ModPowAbstractionJavaImpl();
		primeGenerator = new PrimeGenerator(modPow, randomProvider);
		// paillierEnc = new PaillierEncryption(modPow, primeGenerator, randomProvider);
		// queryEnc = new EncryptQuery(modPow, paillierEnc, randomProvider, threadPool);
		// querierFactory = new QuerierFactory(modPow, paillierEnc, queryEnc);

		querier = createQuerier("Books", SELECTORS);

		// save the query
		try (OutputStream os = new FileOutputStream(QUERY_FILE_NAME)) {
			queryConverter.marshal(queryConverter.toXMLQuery(querier.getQuery()), os);
		}

		final Path response = Paths.get(RESPONSE_FILE_NAME);
		if (Files.exists(response)) {
			Files.walk(response)
					.sorted(Comparator.reverseOrder())
					.map(Path::toFile)
					.forEach(File::delete);
		}

	}

	private Querier createQuerier(String queryType, List<String> selectors) throws Exception {
		// Properties baseTestEncryptionProperties = EncryptionPropertiesBuilder
		// .newBuilder()
		// .dataChunkSize(DATA_CHUNK_SIZE)
		// .hashBitSize(HASH_BIT_SIZE)
		// // .modulusBitSize(modulusBitSize)
		// .certainty(certainty)
		// .embedSelector(true)
		// .build();

		return null;// querierFactory.createQuerier(querySchema, UUID.randomUUID(), selectors,
					// baseTestEncryptionProperties);
	}

	@Test
	public void testSchema() throws Exception {

		Map<String, String> argsMap = new HashMap<>();
		argsMap.put("query_file_name", QUERY_FILE_NAME);
		argsMap.put("config_file_name", CONFIG_FILE_NAME.toString());
		argsMap.put("input", TEST_DATA);
		argsMap.put("output", RESPONSE_FILE_NAME);

		pigServer.registerScript(SCRIPT_FILE_NAME, argsMap);
		pigServer.setBatchOn();
		pigServer.executeBatch();
	}

}
