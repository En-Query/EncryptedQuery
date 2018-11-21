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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.editConfigurationFilePut;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import javax.inject.Inject;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.sshd.common.util.io.IoUtils;
import org.enquery.encryptedquery.data.QuerySchema;
import org.enquery.encryptedquery.loader.SchemaLoader;
import org.enquery.encryptedquery.querier.wideskies.encrypt.EncryptionPropertiesBuilder;
import org.enquery.encryptedquery.querier.wideskies.encrypt.Querier;
import org.enquery.encryptedquery.querier.wideskies.encrypt.QuerierFactory;
import org.enquery.encryptedquery.query.wideskies.Query;
import org.enquery.encryptedquery.responder.business.execution.QueryExecutionScheduler;
import org.enquery.encryptedquery.responder.data.entity.DataSchema;
import org.enquery.encryptedquery.responder.data.entity.DataSource;
import org.enquery.encryptedquery.responder.data.entity.Execution;
import org.enquery.encryptedquery.responder.data.service.DataSourceRegistry;
import org.enquery.encryptedquery.responder.data.service.ExecutionRepository;
import org.enquery.encryptedquery.responder.data.service.QueryRunner;
import org.enquery.encryptedquery.responder.data.service.ResultRepository;
import org.enquery.encryptedquery.responder.it.AbstractResponderItest;
import org.enquery.encryptedquery.xml.transformation.QueryTypeConverter;
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
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.service.cm.ConfigurationAdmin;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class QueryExecutionSchedulerIT extends AbstractResponderItest {

	private static final String QUERY_SCHEMA = "target/test-classes/schemas/get-price-query-schema.xml";
	private static final String DATA_SOURCE_NAME = "query-runner-mock";
	private static final String SELECTOR = "A Cup of Java";
	private static final List<String> SELECTORS = Arrays.asList(new String[] {SELECTOR});
	private static final Integer DATA_PARTITION_BITSIZE = 8;
	private static final Integer HASH_BIT_SIZE = 9;
	public static final int BIT_SIZE = 384;
	public static final int CERTAINTY = 128;


	@Inject
	private DataSourceRegistry dsRegistry;
	@Inject
	private ConfigurationAdmin confAdmin;
	@Inject
	private QuerierFactory querierFactory;
	@Inject
	private QueryExecutionScheduler scheduler;
	@Inject
	private ExecutionRepository executionRepository;
	@Inject
	private ResultRepository resultRepository;

	private Querier querier;
	private Query query;
	private DataSchema booksDataSchema;


	@Override
	@ProbeBuilder
	public TestProbeBuilder probeConfiguration(TestProbeBuilder probe) {
		probe = super.probeConfiguration(probe);
		probe.setHeader("Service-Component",
				"OSGI-INF/org.enquery.encryptedquery.responder.it.business.QueryRunnerMock.xml");
		return probe;
	}

	@Configuration
	public Option[] configuration() {
		return ArrayUtils.addAll(super.baseOptions(), CoreOptions.options(
				editConfigurationFilePut("etc/system.properties",
						"query.schema.path",
						Paths.get(QUERY_SCHEMA).toAbsolutePath().toString()),

				editConfigurationFilePut("etc/encrypted.query.responder.business.cfg",
						"inbox.dir",
						INBOX_DIR)));
	}

	@Before
	@Override
	public void init() throws Exception {
		super.init();
		installBooksDataSchema();
		booksDataSchema = dataSchemaService.findByName(BOOKS_DATA_SCHEMA_NAME);
		assertNotNull(booksDataSchema);

	}

	@Test
	public void addAndRun() throws Exception {
		Assert.assertEquals(0, dsRegistry.list().size());

		// register a MOCK query runner
		org.osgi.service.cm.Configuration conf = confAdmin.createFactoryConfiguration(
				"org.enquery.encryptedquery.responder.it.business.QueryRunnerMock", "?");

		Hashtable<String, String> properties = new Hashtable<>();
		conf.update(properties);

		waitUntilQueryRunnerRegistered(DATA_SOURCE_NAME);

		// the corresponding data source should be in the registry
		DataSource dataSource = dsRegistry.find(DATA_SOURCE_NAME);
		Assert.assertNotNull(dataSource);

		// run the query
		QueryRunner runner = dataSource.getRunner();
		assertNotNull(runner);

		querier = createQuerier("Books", SELECTORS);
		query = querier.getQuery();

		Execution ex = new Execution();
		ex.setDataSchema(booksDataSchema);
		ex.setReceivedTime(new Date());
		ex.setScheduleTime(new Date());
		ex.setDataSourceName(DATA_SOURCE_NAME);
		ex = executionRepository.add(ex);

		QueryTypeConverter converter = new QueryTypeConverter();
		org.enquery.encryptedquery.xml.schema.Query xmlQuery = converter.toXMLQuery(query);

		ByteArrayOutputStream os = new ByteArrayOutputStream();
		converter.marshal(xmlQuery, os);
		ex = executionRepository.updateQueryBytes(ex.getId(), os.toByteArray());

		// final File outputFile = new File("data/responses/response-" + ex.getId() + ".xml");
		// Assert.assertTrue(!outputFile.exists());

		// add the schedule
		scheduler.add(ex);

		tryUntilTrue(30,
				3000,
				"Result not created.",
				e -> resultRepository.listForExecution(e).size() > 0,
				ex);


		Integer resultId = resultRepository.listForExecution(ex).iterator().next().getId();
		try (InputStream is = resultRepository.payloadInputStream(resultId)) {
			String fileContent = new String(IoUtils.toByteArray(is));
			assertEquals(QueryRunnerMock.class.getName(), fileContent);
		}

		// the Execution record is updated with run time stamps
		Execution updated = executionRepository.find(ex.getId());
		assertNotNull(updated.getStartTime());
		assertNotNull(updated.getEndTime());
		assertTrue(updated.getEndTime().after(updated.getStartTime()));
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
