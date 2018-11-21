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
package org.enquery.encryptedquery.responder.it.business;

import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import javax.inject.Inject;

import org.apache.commons.lang3.ArrayUtils;
import org.enquery.encryptedquery.data.QuerySchema;
import org.enquery.encryptedquery.loader.SchemaLoader;
import org.enquery.encryptedquery.querier.wideskies.encrypt.EncryptionPropertiesBuilder;
import org.enquery.encryptedquery.querier.wideskies.encrypt.Querier;
import org.enquery.encryptedquery.querier.wideskies.encrypt.QuerierFactory;
import org.enquery.encryptedquery.query.wideskies.Query;
import org.enquery.encryptedquery.responder.data.entity.DataSource;
import org.enquery.encryptedquery.responder.data.service.DataSourceRegistry;
import org.enquery.encryptedquery.responder.data.service.QueryRunner;
import org.enquery.encryptedquery.responder.it.AbstractResponderItest;
import org.enquery.encryptedquery.responder.it.QueryRunnerConfigurator;
import org.enquery.encryptedquery.responder.it.util.FlinkDriver;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.ProbeBuilder;
import org.ops4j.pax.exam.TestProbeBuilder;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.karaf.options.KarafDistributionOption;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.service.cm.ConfigurationAdmin;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class FlinkJDBCRunnerIT extends AbstractResponderItest {

	private static final String QUERY_SCHEMA =
			"target/test-classes/schemas/get-price-query-schema.xml";
	private static final String DATA_SOURCE_NAME = "phone-book-jdbc-flink";
	private static final String DESCRIPTION = "A Flink-JDBC on a Book database.";
	private static final String SELECTOR = "A Cup of Java";
	private static final List<String> SELECTORS = Arrays.asList(new String[] {SELECTOR});
	private static final Integer DATA_PARTITION_BITSIZE = 8;
	private static final Integer HASH_BIT_SIZE = 9;
	private static final String RESPONSE_FILE_NAME = "target/response.bin";
	public static final int BIT_SIZE = 384;
	public static final int CERTAINTY = 128;

	@Inject
	private DataSourceRegistry dsRegistry;
	@Inject
	private ConfigurationAdmin confAdmin;
	@Inject
	private QuerierFactory querierFactory;

	private Querier querier;
	private Query query;
	private FlinkDriver flinkDriver = new FlinkDriver();

	@Override
	@Before
	public void init() throws Exception {
		super.init();
		installBooksDataSchema();
		flinkDriver.init();
	}

	@ProbeBuilder
	@Override
	public TestProbeBuilder probeConfiguration(TestProbeBuilder probe) {
		super.probeConfiguration(probe);
		flinkDriver.probeConfiguration(probe);
		return probe;
	}

	@After
	public void cleanup() throws IOException, InterruptedException {
		flinkDriver.cleanup();
	}

	@Configuration
	public Option[] configuration() {
		Option[] options = CoreOptions.options(
				KarafDistributionOption.editConfigurationFilePut("etc/system.properties",
						"query.schema.path",
						Paths.get(QUERY_SCHEMA).toAbsolutePath().toString()),
				KarafDistributionOption.editConfigurationFilePut("etc/system.properties",
						"response.path",
						Paths.get(RESPONSE_FILE_NAME).toAbsolutePath().toString()),
				KarafDistributionOption.editConfigurationFilePut("etc/system.properties",
						"derby.language.logStatementText",
						"true"));

		// CoreOptions.mavenBundle()
		// .groupId("org.apache.derby")
		// .artifactId("derbyclient")
		// .versionAsInProject());

		return ArrayUtils.addAll(
				ArrayUtils.addAll(super.baseOptions(), flinkDriver.configuration()), options);
	}

	@Test
	public void addAndRun() throws Exception {
		Assert.assertEquals(0, dsRegistry.list().size());

		// register a Flink-JDBC query runner
		QueryRunnerConfigurator conf = new QueryRunnerConfigurator(confAdmin);
		conf.create(DATA_SOURCE_NAME, BOOKS_DATA_SCHEMA_NAME, DESCRIPTION);

		waitUntilQueryRunnerRegistered(DATA_SOURCE_NAME);

		// the corresponding data source should be in the registry
		Collection<DataSource> sources = dsRegistry.list();
		Assert.assertEquals(1, sources.size());
		DataSource dataSource = dsRegistry.find(DATA_SOURCE_NAME);
		Assert.assertNotNull(dataSource);

		// run the query
		QueryRunner runner = dataSource.getRunner();
		assertNotNull(runner);

		querier = createQuerier("Books", SELECTORS);
		query = querier.getQuery();
		OutputStream stdOut = new ByteArrayOutputStream();

		runner.run(null, query, System.getProperty("response.path"), stdOut);
	}


	private Querier createQuerier(String queryType, List<String> selectors) throws Exception {
		SchemaLoader loader = new SchemaLoader();
		QuerySchema querySchema = loader.loadQuerySchema(Paths.get(System.getProperty("query.schema.path")));

		Properties baseTestEncryptionProperties = EncryptionPropertiesBuilder
				.newBuilder()
				.dataPartitionBitSize(DATA_PARTITION_BITSIZE)
				.hashBitSize(HASH_BIT_SIZE)
				.paillierBitSize(BIT_SIZE)
				.certainty(CERTAINTY)
				.embedSelector(true)
				.queryType(queryType)
				.build();
		return querierFactory.createQuerier(querySchema, UUID.randomUUID(), selectors, baseTestEncryptionProperties);
	}
}
