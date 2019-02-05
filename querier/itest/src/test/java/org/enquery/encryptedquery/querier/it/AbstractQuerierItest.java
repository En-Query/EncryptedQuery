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
package org.enquery.encryptedquery.querier.it;

import static org.junit.Assert.assertNotNull;
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
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Dictionary;
import java.util.Hashtable;

import javax.inject.Inject;
import javax.sql.DataSource;

import org.apache.camel.CamelContext;
import org.apache.commons.io.FileUtils;
import org.enquery.encryptedquery.healthcheck.SystemHealthCheck;
import org.enquery.encryptedquery.querier.data.service.DataSchemaRepository;
import org.enquery.encryptedquery.querier.data.service.DataSourceRepository;
import org.enquery.encryptedquery.querier.data.service.DecryptionRepository;
import org.enquery.encryptedquery.querier.data.service.QueryRepository;
import org.enquery.encryptedquery.querier.data.service.QuerySchemaRepository;
import org.enquery.encryptedquery.querier.data.service.ResultRepository;
import org.enquery.encryptedquery.querier.data.service.RetrievalRepository;
import org.enquery.encryptedquery.querier.data.service.ScheduleRepository;
import org.enquery.encryptedquery.querier.it.util.ResponderController;
import org.enquery.encryptedquery.querier.it.util.SampleData;
import org.junit.Assert;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.ProbeBuilder;
import org.ops4j.pax.exam.TestProbeBuilder;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.karaf.options.LogLevelOption.LogLevel;
import org.ops4j.pax.exam.options.MavenArtifactUrlReference;
import org.ops4j.pax.exam.options.UrlReference;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.ops4j.pax.exam.util.Filter;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public abstract class AbstractQuerierItest {

	public static final Logger log = LoggerFactory.getLogger(AbstractQuerierItest.class);

	private static final String REST_CONFIG_PID = "encrypted.query.querier.rest";

	private static final int DEFAULT_RESPONDER_PORT = 8181;
	protected static final String QUERIES_DATA_DIR = "target/query-data";

	@Inject
	@Filter(timeout = 60_000)
	protected DataSource sqlDatasource;
	@Inject
	@Filter(timeout = 60_000, value = "(camel.context.name=rest)")
	private CamelContext restContext;
	@Inject
	@Filter(timeout = 60_000)
	protected ConfigurationAdmin configurationAdmin;
	@Inject
	@Filter(timeout = 60_000)
	protected SystemHealthCheck healthCheck;
	@Inject
	@Filter(timeout = 60_000)
	protected DataSourceRepository dataSourceRepo;
	@Inject
	@Filter(timeout = 60_000)
	protected DataSchemaRepository dataSchemaRepo;
	@Inject
	@Filter(timeout = 60_000)
	protected QueryRepository queryRepo;
	@Inject
	@Filter(timeout = 60_000)
	protected QuerySchemaRepository querySchemaRepo;
	@Inject
	@Filter(timeout = 60_000)
	protected ScheduleRepository scheduleRepo;
	@Inject
	@Filter(timeout = 60_000)
	protected ResultRepository resultRepo;
	@Inject
	@Filter(timeout = 60_000)
	protected RetrievalRepository retrievalRepo;
	@Inject
	@Filter(timeout = 60_000)
	protected DecryptionRepository decryptionRepo;

	protected MavenArtifactUrlReference karafDistUrl;
	protected UrlReference querierFeatureUrl;
	protected UrlReference karafStandardRepo;

	protected File responderInstallDir;
	protected File responderInboxDir;
	protected ResponderController responderClt;
	protected SampleData sampleData;

	@ProbeBuilder
	public TestProbeBuilder probeConfiguration(TestProbeBuilder probe) {
		probe.setHeader(Constants.BUNDLE_SYMBOLICNAME, "EQ-Querier-IT-Probe");
		probe.setHeader(Constants.DYNAMICIMPORT_PACKAGE,
				"*,org.apache.felix.service.*;status=provisional");

		return probe;
	}

	@Configuration
	protected Option baseOptions() {
		karafDistUrl = maven().groupId("org.apache.karaf").artifactId("apache-karaf").versionAsInProject().type("tar.gz");
		querierFeatureUrl = maven().groupId("org.enquery.encryptedquery").artifactId("encryptedquery-querier-feature").versionAsInProject().type("xml").classifier("features");
		karafStandardRepo = maven()
				.groupId("org.apache.karaf.features")
				.artifactId("standard")
				.classifier("features")
				.versionAsInProject()
				.type("xml");

		final String dbEngine = System.getProperty("db.engine");

		return CoreOptions.composite(
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

				features(querierFeatureUrl, "encryptedquery-querier-" + dbEngine),

				replaceConfigurationFile("etc/org.ops4j.pax.logging.cfg",
						getResourceAsFile("/etc/org.ops4j.pax.logging.cfg")),

				replaceConfigurationFile("etc/org.enquery.encryptedquery.encryption.paillier.PaillierCryptoScheme.cfg",
						getResourceAsFile("/etc/org.enquery.encryptedquery.encryption.paillier.PaillierCryptoScheme.cfg")),

				replaceConfigurationFile("etc/org.ops4j.pax.web.cfg",
						getResourceAsFile("/etc/org.ops4j.pax.web.cfg")),

				propagateSystemProperty("responder.install.dir"),
				propagateSystemProperty("flink.install.dir"),
				propagateSystemProperty("flink.jdbc.app"),
				propagateSystemProperty("native.libs.install.dir"),

				// propagateSystemProperty("standalone.install.dir"),

				// avoid port conflict with responder
				editConfigurationFilePut("etc/org.apache.karaf.management.cfg",
						"rmiServerPort", "44445"),
				editConfigurationFilePut("etc/org.apache.karaf.management.cfg",
						"rmiRegistryPort", "10100"),
				editConfigurationFilePut("etc/org.apache.karaf.shell.cfg",
						"sshPort", "8102"),

				editConfigurationFilePut("etc/encrypted.query.querier.rest.cfg",
						"camel.trace.enabled", "true"),

				// editConfigurationFilePut("etc/org.enquery.encryptedquery.querier.wideskies.encrypt.EncryptQuery.cfg",
				// QuerierProperties.NUMTHREADS, "4"),

				replaceConfigurationFile("etc/org.ops4j.datasource-querier.cfg",
						getResourceAsFile("/etc/org.ops4j.datasource-querier-" + dbEngine + ".cfg")),

				replaceConfigurationFile("etc/org.enquery.encryptedquery.jpa.config.EMConfigurator.cfg",
						getResourceAsFile("/etc/org.enquery.encryptedquery.jpa.config.EMConfigurator.cfg"))

		);

	}

	@Before
	public void prepare() {
		String responderDir = System.getProperty("responder.install.dir");
		responderInstallDir = new File(responderDir);
		responderInboxDir = new File(responderInstallDir, "inbox");
		responderClt = new ResponderController(responderDir);

		sampleData = new SampleData();
		waitForHealthyStatus();
	}

	protected File getResourceAsFile(String path) {
		URL res = this.getClass().getResource(path);
		if (res == null) {
			throw new RuntimeException("Config resource " + path + " not found");
		}
		return new File(res.getFile());
	}

	protected void truncateTables() throws SQLException {
		log.info("Truncating all tables.");

		try (Connection conn = sqlDatasource.getConnection();
				Statement s = conn.createStatement();) {

			s.executeUpdate("delete from decryptions");
			s.executeUpdate("delete from retrievals");
			s.executeUpdate("delete from results");
			s.executeUpdate("delete from schedules");
			s.executeUpdate("delete from dataschemafields");
			s.executeUpdate("delete from queryschemafields");
			s.executeUpdate("delete from queries");
			s.executeUpdate("delete from datasources");
			s.executeUpdate("delete from queryschemas");
			s.executeUpdate("delete from dataschemas");
		}
		log.info("Truncated all tables.");
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

	protected void updateConfig(String pid, Dictionary<String, String> properties) throws IOException {
		org.osgi.service.cm.Configuration configuration = configurationAdmin.getConfiguration(pid, null);
		assertNotNull(configuration);
		configuration.update(properties);
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

	protected void configuteResponderPort(int port) throws IOException, InterruptedException {
		waitForHealthyStatus();
		org.osgi.service.cm.Configuration configuration = configurationAdmin.getConfiguration(REST_CONFIG_PID, null);

		Dictionary<String, Object> properties = configuration.getProperties();
		if (properties == null) {
			properties = new Hashtable<>();
		}
		properties.put("responder.port", port);
		configuration.update(properties);
		waitForHealthyStatus();
	}

	protected int responderPort() throws IOException {
		org.osgi.service.cm.Configuration configuration = configurationAdmin.getConfiguration(REST_CONFIG_PID, null);
		if (configuration == null) return DEFAULT_RESPONDER_PORT;
		Dictionary<String, Object> properties = configuration.getProperties();
		if (properties == null) return DEFAULT_RESPONDER_PORT;

		Object value = properties.get("responder.port");
		if (value == null) return DEFAULT_RESPONDER_PORT;
		return (int) value;
		// Dictionary<String, Object> properties = configuration.getProperties();
		// properties.put("responder.port", value);
		// configuration.update(properties);
	}


}
