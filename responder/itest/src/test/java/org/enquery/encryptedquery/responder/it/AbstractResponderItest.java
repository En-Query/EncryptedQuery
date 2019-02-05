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
package org.enquery.encryptedquery.responder.it;

import static org.junit.Assert.assertTrue;
import static org.ops4j.pax.exam.CoreOptions.maven;
import static org.ops4j.pax.exam.CoreOptions.propagateSystemProperty;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.configureSecurity;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.editConfigurationFilePut;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.features;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.karafDistributionConfiguration;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.keepRuntimeFolder;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.logLevel;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.replaceConfigurationFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;

import javax.inject.Inject;
import javax.sql.DataSource;

import org.apache.camel.CamelContext;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.enquery.encryptedquery.healthcheck.SystemHealthCheck;
import org.enquery.encryptedquery.responder.data.service.DataSchemaService;
import org.enquery.encryptedquery.responder.data.service.DataSourceRegistry;
import org.enquery.encryptedquery.responder.flink.jdbc.runner.FlinkJdbcQueryRunner;
import org.enquery.encryptedquery.responder.it.util.FlinkJdbcRunnerConfigurator;
import org.enquery.encryptedquery.responder.it.util.FlinkKafkaRunnerConfigurator;
import org.enquery.encryptedquery.responder.it.util.ThrowingPredicate;
import org.enquery.encryptedquery.responder.standalone.runner.StandaloneQueryRunner;
import org.junit.Assert;
import org.junit.Before;
import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.ProbeBuilder;
import org.ops4j.pax.exam.TestProbeBuilder;
import org.ops4j.pax.exam.karaf.options.LogLevelOption.LogLevel;
import org.ops4j.pax.exam.options.MavenArtifactUrlReference;
import org.ops4j.pax.exam.options.UrlReference;
import org.ops4j.pax.exam.util.Filter;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractResponderItest {

	public static final Logger log = LoggerFactory.getLogger(AbstractResponderItest.class);

	protected static final String INBOX_DIR = "inbox";

	protected static final String BOOKS_DATA_SCHEMA_NAME = "Books";

	@Inject
	protected DataSource sqlDatasource;
	@Inject
	@Filter(timeout = 60_000, value = "(camel.context.name=data-import)")
	private CamelContext restContext;
	@Inject
	protected SystemHealthCheck healthCheck;
	@Inject
	protected DataSchemaService dataSchemaService;
	@Inject
	protected DataSourceRegistry dataSourceRegistry;

	@Inject
	@Filter(timeout = 60_000)
	protected ConfigurationAdmin confAdmin;

	protected MavenArtifactUrlReference karafDistUrl;
	protected UrlReference responderFeatureUrl;
	protected UrlReference karafStandardRepo;

	@ProbeBuilder
	public TestProbeBuilder probeConfiguration(TestProbeBuilder probe) {
		probe.setHeader(Constants.BUNDLE_SYMBOLICNAME, "EQ-Responder-IT-Probe");
		probe.setHeader(Constants.DYNAMICIMPORT_PACKAGE,
				"*,org.apache.felix.service.*;status=provisional");

		return probe;
	}


	protected Option[] baseOptions() {
		karafDistUrl = maven().groupId("org.apache.karaf").artifactId("apache-karaf").versionAsInProject().type("tar.gz");
		responderFeatureUrl = maven().groupId("org.enquery.encryptedquery").artifactId("encryptedquery-responder-feature").versionAsInProject().type("xml").classifier("features");
		karafStandardRepo = maven()
				.groupId("org.apache.karaf.features")
				.artifactId("standard")
				.classifier("features")
				.versionAsInProject()
				.type("xml");

		final String dbEngine = System.getProperty("db.engine");

		return CoreOptions.options(
				// KarafDistributionOption.debugConfiguration("8000", true),
				karafDistributionConfiguration()
						.frameworkUrl(karafDistUrl)
						.name("Apache Karaf")
						.unpackDirectory(new File("target/exam"))
						.useDeployFolder(false),

				configureSecurity().disableKarafMBeanServerBuilder(),
				systemProperty("pax.exam.osgi.unresolved.fail").value("true"),
				keepRuntimeFolder(),
				logLevel(LogLevel.INFO),

				// propagate system properties
				propagateSystemProperty("flink.install.dir"),
				propagateSystemProperty("flink.jdbc.app"),

				editConfigurationFilePut("etc/system.properties",
						"derby.language.logStatementText", "true"),

				editConfigurationFilePut("etc/org.enquery.encryptedquery.healthcheck.impl.ComponentStateHealthCheck.cfg",
						".ignore.component",
						"[\"org.enquery.encryptedquery.responder.it.business.QueryRunnerMock\", "
								+ "\"org.enquery.encryptedquery.encryption.impl.ModPowAbstractionGMPImpl\","
								+ "\"" + FlinkJdbcQueryRunner.class.getName() + "\","
								+ "\"" + StandaloneQueryRunner.class.getName() + "\","
								+ "\"org.enquery.encryptedquery.responder.flink.kafka.runner.FlinkKafkaQueryRunner\""
								+ "]"),

				editConfigurationFilePut("etc/encrypted.query.responder.business.cfg",
						"inbox.dir",
						INBOX_DIR),

				features(responderFeatureUrl, "encryptedquery-responder-" + dbEngine),

				replaceConfigurationFile("etc/org.ops4j.datasource-responder.cfg",
						getResourceAsFile("/etc/org.ops4j.datasource-responder-" + dbEngine + ".cfg")),

				replaceConfigurationFile("etc/org.ops4j.pax.logging.cfg",
						getResourceAsFile("/etc/org.ops4j.pax.logging.cfg")),

				replaceConfigurationFile("etc/org.enquery.encryptedquery.jpa.config.EMConfigurator.cfg",
						getResourceAsFile("/etc/org.enquery.encryptedquery.jpa.config.EMConfigurator.cfg")));

	}

	@Before
	public void init() throws Exception {
		log.info("Initializing test");
		waitForHealthyStatus();
		truncateTables();
	}

	protected File getResourceAsFile(String path) {
		URL res = this.getClass().getResource(path);
		if (res == null) {
			throw new RuntimeException("Config resource " + path + " not found");
		}
		return new File(res.getFile());
	}

	protected void truncateTables() throws SQLException {
		try (Connection conn = sqlDatasource.getConnection();
				Statement s = conn.createStatement();) {

			String[] tables = new String[] {
					"results",
					"executions",
					"dataschemafields",
					"dataschemas",
					"data_source_ids",
					"qrtz_fired_triggers",
					"qrtz_paused_trigger_grps",
					"qrtz_scheduler_state",
					"qrtz_locks",
					"qrtz_simple_triggers",
					"qrtz_simprop_triggers",
					"qrtz_cron_triggers",
					"qrtz_blob_triggers",
					"qrtz_triggers",
					"qrtz_job_details",
					"qrtz_calendars",
			};

			for (String tableName : Arrays.asList(tables)) {
				deleteTable(conn, tableName);
			}
		}
	}


	private void deleteTable(Connection conn, String tableName) throws SQLException {
		if (tableExists(conn, tableName)) {
			Statement s = conn.createStatement();
			try {
				s.executeUpdate("delete from " + tableName);
			} catch (SQLException e) {
				s.executeUpdate("delete from " + tableName.toUpperCase());
			}
			log.info("Deleted table: '{}'", tableName);
		} else {
			log.warn("Table does not exists: '{}'", tableName);
		}
	}

	private boolean tableExists(Connection conn, String tableName) throws SQLException {
		final DatabaseMetaData dbm = conn.getMetaData();
		ResultSet tables = dbm.getTables(null, null, tableName, null);
		boolean result = tables.next();
		if (!result) {
			tables = dbm.getTables(null, null, tableName.toUpperCase(), null);
			result = tables.next();
		}
		return result;
	}

	public <T> void tryUntilTrue(int max, int sleep, String msg, ThrowingPredicate<T> operation, T operationArgument) throws Exception {
		int i = max;
		while (i-- > 0 && !operation.test(operationArgument)) {
			Thread.sleep(sleep);
		}
		assertTrue(msg, operation.test(operationArgument));
	}

	protected File saveToFile(byte[] content, File directory, String fileName) throws IOException, FileNotFoundException {
		File destFile = new File(directory, fileName);
		Files.createDirectories(directory.toPath());
		Files.deleteIfExists(destFile.toPath());
		FileUtils.writeByteArrayToFile(destFile, content);
		return destFile;
	}

	protected void waitForFileConsumed(File file) throws Exception {
		tryUntilTrue(30,
				3000,
				"File was not consumed: " + file,
				dummy -> !file.exists(),
				null);

		log.info("File was consumed: {}", file);
	}

	protected void waitForFileCreated(File file) throws Exception {
		tryUntilTrue(30,
				3000,
				"File was not created: " + file,
				dummy -> file.exists(),
				null);

		log.info("File was created: {}", file);
	}

	protected void waitForHealthyStatus() {
		waitForHealthStatus(true);
	}

	protected void waitForUnhealthyStatus() {
		waitForHealthStatus(false);
	}

	private void waitForHealthStatus(boolean expectedStatus) {
		try {
			Thread.sleep(1_000);
			int i = 120;
			while (--i > 0 && healthCheck.isHealthy() != expectedStatus) {
				log.info("Waiting for heath status to be " + expectedStatus);
				Thread.sleep(1_000);
			}
			Assert.assertTrue("Health status did not become " + expectedStatus, i > 0);
		} catch (InterruptedException e) {
			throw new RuntimeException("Interrupted.");
		}
	}

	protected void installBooksDataSchema() throws Exception {
		installDataSchema("books-data-schema.xml");
	}

	protected void installDataSchema(final String fileName) throws IOException, FileNotFoundException, Exception {
		byte[] bytes = IOUtils.resourceToByteArray("/schemas/" + fileName, this.getClass().getClassLoader());
		File inbox = new File(INBOX_DIR);
		File file = saveToFile(bytes, inbox, fileName);
		waitForFileConsumed(file);
		Thread.sleep(500);
		assertTrue(!Files.exists(Paths.get(INBOX_DIR, ".failed", fileName)));
		assertTrue(Files.exists(Paths.get(INBOX_DIR, ".processed", fileName)));
	}

	private static final String DATA_SOURCE_DESCRIPTION = "test-desc";


	protected void waitUntilQueryRunnerRegistered(String runnerName) throws Exception {
		tryUntilTrue(120,
				2000,
				"QueryRunner not registered in time",
				name -> dataSourceRegistry.find(name) != null,
				runnerName);

	}

	protected org.enquery.encryptedquery.responder.data.entity.DataSource installFlinkJdbcDataSource(String dataSourceName, String dataSchemaName) throws Exception {
		// Add a QueryRunner
		FlinkJdbcRunnerConfigurator runnerConfigurator = new FlinkJdbcRunnerConfigurator(confAdmin);
		runnerConfigurator.create(dataSourceName, dataSchemaName, DATA_SOURCE_DESCRIPTION);
		// give enough time for the QueryRunner to be registered
		waitUntilQueryRunnerRegistered(dataSourceName);

		return dataSourceRegistry.find(dataSourceName);
	}

	protected org.enquery.encryptedquery.responder.data.entity.DataSource installFlinkKafkaDataSource(String dataSourceName, String dataSchemaName) throws Exception {
		// Add a QueryRunner
		FlinkKafkaRunnerConfigurator runnerConfigurator = new FlinkKafkaRunnerConfigurator(confAdmin);
		runnerConfigurator.create(dataSourceName, dataSchemaName);
		// give enough time for the QueryRunner to be registered
		waitUntilQueryRunnerRegistered(dataSourceName);

		return dataSourceRegistry.find(dataSourceName);
	}
}
