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
package org.enquery.encryptedquery.responder.flink.jdbc.runner;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.ExecutorService;

import javax.xml.bind.JAXBException;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.core.CoreConfigurationProperties;
import org.enquery.encryptedquery.data.Query;
import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.enquery.encryptedquery.encryption.CryptoSchemeRegistry;
import org.enquery.encryptedquery.flink.FlinkConfigurationProperties;
import org.enquery.encryptedquery.responder.ResponderProperties;
import org.enquery.encryptedquery.responder.business.ChildProcessLogger;
import org.enquery.encryptedquery.responder.data.entity.DataSourceType;
import org.enquery.encryptedquery.responder.data.service.DataSchemaService;
import org.enquery.encryptedquery.responder.data.service.QueryRunner;
import org.enquery.encryptedquery.xml.transformation.QueryTypeConverter;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(configurationPolicy = ConfigurationPolicy.REQUIRE,
		immediate = true)
@Designate(ocd = Config.class, factory = true)
public class FlinkJdbcQueryRunner implements QueryRunner {

	private static final Logger log = LoggerFactory.getLogger(FlinkJdbcQueryRunner.class);

	private String name;
	private String description;
	private String driverClassName;
	private String connectionUrl;
	private String sqlQuery;
	private String dataSchemaName;
	private String additionalFlinkArguments;
	private Path programPath;
	private Path jarPath;
	private Path runDir;
	private Integer flinkParallelism;
	private Integer computeThreshold;
	private Integer columnEncryptionPartitionCount;

	@Reference
	private DataSchemaService dss;
	@Reference
	private ExecutorService threadPool;
	@Reference
	private QueryTypeConverter queryTypeConverter;
	@Reference
	private CryptoSchemeRegistry cryptoRegistry;
	private DataSourceType type;


	@Activate
	void activate(final Config config) {
		name = config.name();
		Validate.notBlank(name, "name cannot be blank");

		description = config.description();
		Validate.notBlank(description, "description cannot be blank");

		log.info(
				"Creating FlinkJdbcQueryRunner with name '{}' and description '{}'.",
				name,
				description);

		driverClassName = config._jdbc_driver();
		connectionUrl = config._jdbc_url();
		sqlQuery = config._jdbc_query();
		dataSchemaName = config.data_schema_name();
		additionalFlinkArguments = config._additional_flink_arguments();

		Validate.notBlank(driverClassName, "JDBC Driver cannot be blank.");
		Validate.notBlank(connectionUrl, "JDBC Connection URL cannot be blank.");
		Validate.notBlank(sqlQuery, "JDBC SQL cannot be blank.");
		Validate.notBlank(dataSchemaName, "DataSchema name cannot be blank.");
		Validate.notBlank(config._flink_install_dir(), "Flink install dir cannot be blank.");
		Validate.notBlank(config._jar_file_path(), "Flink jar path cannot be blank.");
		Validate.notBlank(config._run_directory(), "Run directory cannot be blank.");

		jarPath = Paths.get(config._jar_file_path());
		Validate.isTrue(Files.exists(jarPath), "Does not exists: " + jarPath);

		programPath = Paths.get(config._flink_install_dir(), "bin", "flink");
		Validate.isTrue(Files.exists(programPath), "Does not exists: " + programPath);

		runDir = Paths.get(config._run_directory());
		Validate.isTrue(Files.exists(runDir), "Does not exists: " + runDir);

		if (config._flink_parallelism() != null) {
			flinkParallelism = Integer.valueOf(config._flink_parallelism());
		}

		if (config._compute_threshold() != null) {
			computeThreshold = Integer.valueOf(config._compute_threshold());
		}

		Validate.notBlank(config.type(), "Type is required.");
		this.type = DataSourceType.valueOf(config.type());

		if (config._column_encryption_partition_count() != null) {
			columnEncryptionPartitionCount = Integer.parseInt(config._column_encryption_partition_count());
			Validate.isTrue(columnEncryptionPartitionCount > 0);
		}
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public String description() {
		return description;
	}

	@Override
	public String dataSchemaName() {
		return dataSchemaName;
	}

	@Override
	public void run(Query query, Map<String, String> parameters, String outputFileName, OutputStream stdOutput) {

		Validate.notNull(query);
		Validate.notNull(outputFileName);
		Validate.isTrue(!Files.exists(Paths.get(outputFileName)));

		Path workingTempDir = null;
		try {
			workingTempDir = Files.createTempDirectory(runDir, "flink-jdbc");
			Path jdbcConProps = createJDBCConnectionPropertyFile(workingTempDir);
			Path queryFile = createQueryFile(workingTempDir, query);
			Path configFile = createConfigFile(workingTempDir, query, parameters);

			List<String> arguments = new ArrayList<>();
			arguments.add(programPath.toString());
			arguments.add("run");
			if (additionalFlinkArguments != null) {
				arguments.add(additionalFlinkArguments);
			}

			if (flinkParallelism != null) {
				arguments.add("-p");
				arguments.add(Integer.toString(flinkParallelism));
			}

			arguments.add(jarPath.toString());
			arguments.add("-c");
			arguments.add(jdbcConProps.toString());

			arguments.add("-q");
			arguments.add(queryFile.toString());
			arguments.add("-sp");
			arguments.add(configFile.toString());
			arguments.add("-o");
			arguments.add(outputFileName);

			ProcessBuilder processBuilder = new ProcessBuilder(arguments);
			processBuilder.directory(workingTempDir.toFile());
			processBuilder.redirectErrorStream(true);

			log.info("Launch flink with arguments: " + arguments);
			Process proc = processBuilder.start();

			// capture and log child process output in separate thread
			threadPool.submit(
					new ChildProcessLogger(proc.getInputStream(),
							log,
							stdOutput));

			int exitCode = proc.waitFor();

			log.info("Flink exited with code: {}.", exitCode);

			if (exitCode != 0) {
				throw new IOException("Error running Flink-JDBC application.");
			}

		} catch (IOException | InterruptedException | JAXBException e) {
			// FileUtils.deleteQuietly(workingTempDir.toFile());
			throw new RuntimeException("Error running Flink-JDBC query.", e);
		} finally {
			// FileUtils.deleteQuietly(workingTempDir.toFile());
		}
	}

	private Path createConfigFile(Path dir, Query query, Map<String, String> parameters) throws FileNotFoundException, IOException {
		Path result = Paths.get(dir.toString(), "config.properties");
		int maxHitsPerSelector = 1000;

		Properties p = new Properties();

		if (parameters != null) {
			if (parameters.containsKey(ResponderProperties.MAX_HITS_PER_SELECTOR)) {
				maxHitsPerSelector = Integer.parseInt(parameters.get(ResponderProperties.MAX_HITS_PER_SELECTOR));
			}
		}
		p.setProperty(FlinkConfigurationProperties.MAX_HITS_PER_SELECTOR, Integer.toString(maxHitsPerSelector));

		if (columnEncryptionPartitionCount != null) {
			p.setProperty(FlinkConfigurationProperties.COLUMN_ENCRYPTION_PARTITION_COUNT,
					Integer.toString(columnEncryptionPartitionCount));
		}

		if (computeThreshold != null) {
			p.setProperty(FlinkConfigurationProperties.COMPUTE_THRESHOLD, computeThreshold.toString());
		}

		// Pass the CryptoScheme configuration to the external application,
		// the external application needs to instantiate the CryptoScheme whit these parameters
		final String schemeId = query.getQueryInfo().getCryptoSchemeId();
		CryptoScheme cryptoScheme = cryptoRegistry.cryptoSchemeByName(schemeId);
		Validate.notNull(cryptoScheme, "CryptoScheme not found for id: %s", schemeId);
		p.setProperty(CoreConfigurationProperties.CRYPTO_SCHEME_CLASS_NAME, cryptoScheme.getClass().getName());
		for (Entry<String, String> entry : cryptoScheme.configurationEntries()) {
			p.setProperty(entry.getKey(), entry.getValue());
		}

		StringBuilder sb = new StringBuilder();
		p.forEach((k, v) -> {
			sb.append("\n");
			sb.append(k);
			sb.append("=");
			sb.append(v);
		});
		log.info("Running with configuration: {}", sb.toString());

		try (OutputStream os = new FileOutputStream(result.toFile())) {
			p.store(os, "Automatically generated by: " + this.getClass());
		}
		return result.getFileName();
	}

	private Path createQueryFile(Path dir, Query query) throws IOException, JAXBException {
		Path result = Paths.get(dir.toString(), "query.xml");
		try (FileOutputStream os = new FileOutputStream(result.toFile())) {
			queryTypeConverter.marshal(queryTypeConverter.toXMLQuery(query), os);
		}
		return result.getFileName();
	}

	private Path createJDBCConnectionPropertyFile(Path dir) throws IOException {
		Path result = Paths.get(dir.toString(), "jdbc.properties");

		Properties p = new Properties();
		p.setProperty("jdbc.driver", this.driverClassName);
		p.setProperty("jdbc.url", this.connectionUrl);
		p.setProperty("jdbc.query", this.sqlQuery);

		try (OutputStream os = new FileOutputStream(result.toFile())) {
			p.store(os, "Automatically generated by: " + this.getClass());
		}
		return result.getFileName();
	}

	@Override
	public DataSourceType getType() {
		return type;
	}
}
