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
package org.enquery.encryptedquery.responder.flink.kafka.runner;

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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import javax.xml.bind.JAXBException;

import org.apache.commons.codec.Charsets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.core.CoreConfigurationProperties;
import org.enquery.encryptedquery.data.Query;
import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.enquery.encryptedquery.encryption.CryptoSchemeRegistry;
import org.enquery.encryptedquery.flink.FlinkConfigurationProperties;
import org.enquery.encryptedquery.flink.KafkaConfigurationProperties;
import org.enquery.encryptedquery.responder.ResponderProperties;
import org.enquery.encryptedquery.responder.data.entity.DataSourceType;
import org.enquery.encryptedquery.responder.data.entity.ExecutionStatus;
import org.enquery.encryptedquery.responder.data.service.DataSchemaService;
import org.enquery.encryptedquery.responder.data.service.QueryRunner;
import org.enquery.encryptedquery.xml.transformation.QueryTypeConverter;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(configurationPolicy = ConfigurationPolicy.REQUIRE,
		immediate = true)
@Designate(ocd = Config.class, factory = true)
public class FlinkKafkaQueryRunner implements QueryRunner {

	private static final Logger log = LoggerFactory.getLogger(FlinkKafkaQueryRunner.class);

	private String name;
	private String description;
	private String kafkaBrokers;
	private String kafkaTopic;
	private String dataSchemaName = null;
	private String additionalFlinkArguments;
	private Path programPath;
	private Path jarPath;
	private Path runDir;
	private Integer flinkParallelism;

	private Integer columnBufferMemory;

	@Reference
	private DataSchemaService dss;
	@Reference
	private ExecutorService threadPool;
	@Reference
	private QueryTypeConverter queryTypeConverter;
	@Reference
	private CryptoSchemeRegistry cryptoRegistry;

	private JobStatusQuerier statusQuerier;

	private String kafkaEmissionRate;

	@Activate
	void activate(ComponentContext cc, Config config) throws Exception {
		name = config.name();
		Validate.notBlank(name, "name cannot be blank");

		description = config.description();
		Validate.notBlank(description, "description cannot be blank");

		log.info(
				"Creating FlinkKafkaQueryRunner with name '{}' and description '{}'.",
				name,
				description);

		kafkaBrokers = config._kafka_brokers();
		kafkaTopic = config._kafka_topic();
		kafkaEmissionRate = config._kafka_emission_rate_per_second();

		dataSchemaName = config.data_schema_name();
		additionalFlinkArguments = config._additional_flink_arguments();

		Validate.notBlank(kafkaBrokers, "Kafka Brokers cannot be blank.");
		Validate.notBlank(kafkaTopic, "Kafka topic cannot be blank.");

		Validate.notBlank(dataSchemaName, "DataSchema name cannot be blank.");
		Validate.notBlank(config._flink_install_dir(), "Flink install dir cannot be blank.");
		Validate.notBlank(config._application_jar_path(), "Flink jar path cannot be blank.");
		Validate.notBlank(config._run_directory(), "Run directory cannot be blank.");

		jarPath = Paths.get(config._application_jar_path());
		Validate.isTrue(Files.exists(jarPath), "Does not exists: " + jarPath);

		programPath = Paths.get(config._flink_install_dir(), "bin", "flink");
		Validate.isTrue(Files.exists(programPath), "Does not exists: " + programPath);

		runDir = Paths.get(config._run_directory());
		Validate.isTrue(Files.exists(runDir), "Does not exists: " + runDir);

		if (config._flink_parallelism() != null) {
			flinkParallelism = Integer.valueOf(config._flink_parallelism());
		}

		if (config._column_buffer_memory_mb() != null) {
			columnBufferMemory = Integer.valueOf(config._column_buffer_memory_mb());
		}

		String historyServerUri = config._flink_history_server_uri();
		Validate.notBlank(historyServerUri, ".flink.history.server.uri cannot be blank.");

		statusQuerier = new JobStatusQuerier(cc.getBundleContext(), historyServerUri);
	}

	@Deactivate
	public void deactivate() throws Exception {
		if (statusQuerier != null) statusQuerier.stop();
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
	public byte[] run(Query query, Map<String, String> parameters, String outputFileName, OutputStream stdOutput) {

		Validate.notNull(query);
		Validate.notNull(outputFileName);
		Validate.isTrue(!Files.exists(Paths.get(outputFileName)), "Error - Output File/Folder already exists.");

		if (parameters != null) {
			log.info("Parameters: {}", parameters);
		}

		Path workingTempDir = null;
		try {
			workingTempDir = Files.createTempDirectory(runDir, "flink-kafka");
			Path kafkaConProps = createKafkaConnectionPropertyFile(workingTempDir, parameters);
			Path queryFile = createQueryFile(workingTempDir, query);
			Path configFile = createConfigFile(workingTempDir, query, parameters);

			List<String> arguments = new ArrayList<>();
			arguments.add(programPath.toString());
			arguments.add("run");
			if (additionalFlinkArguments != null) {
				String[] options = additionalFlinkArguments.split(" ");
				for (String o : options) {
					arguments.add(o);
				}
			}

			// detached mode: do not wait for completion
			arguments.add("-d");

			if (flinkParallelism != null) {
				arguments.add("-p");
				arguments.add(Integer.toString(flinkParallelism));
			}

			arguments.add(jarPath.toString());
			arguments.add("-c");
			arguments.add(kafkaConProps.toString());

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
			FlinkStdOutputCapturer capturer = new FlinkStdOutputCapturer(proc.getInputStream(),
					log,
					stdOutput);
			Future<?> future = threadPool.submit(capturer);

			int exitCode = proc.waitFor();

			log.info("Flink exited with code: {}.", exitCode);

			if (exitCode != 0) {
				throw new IOException("Error running Flink-Kafka application.");
			}

			// wait for the capturer to finish
			Validate.isTrue(future.get() == null);

			// return the captured job id
			String jobId = capturer.flinkJobId();
			Validate.isTrue(jobId != null);
			return jobId.getBytes(Charsets.UTF_8);

		} catch (IOException | InterruptedException | JAXBException | ExecutionException e) {
			throw new RuntimeException("Error running Flink-Kafka query.", e);
		} finally {
			FileUtils.deleteQuietly(workingTempDir.toFile());
		}
	}

	private Path createConfigFile(Path dir, Query query, Map<String, String> parameters)
			throws FileNotFoundException, IOException {
		Path result = Paths.get(dir.toString(), "config.properties");
		Integer maxHitsPerSelector = null;
		int windowLengthInSeconds = 60;
		Long runTimeInSeconds = null;

		Properties p = new Properties();

		if (parameters != null) {

			if (parameters.containsKey(ResponderProperties.MAX_HITS_PER_SELECTOR)) {
				maxHitsPerSelector = Integer.parseInt(parameters.get(ResponderProperties.MAX_HITS_PER_SELECTOR));
			}

			if (parameters.containsKey(FlinkConfigurationProperties.WINDOW_LENGTH_IN_SECONDS)) {
				windowLengthInSeconds = Integer
						.parseInt(parameters.get(FlinkConfigurationProperties.WINDOW_LENGTH_IN_SECONDS));
			}

			if (parameters.containsKey(FlinkConfigurationProperties.STREAM_RUNTIME_SECONDS)) {
				runTimeInSeconds = Long.parseLong(parameters.get(FlinkConfigurationProperties.STREAM_RUNTIME_SECONDS));
			}
		}

		if (maxHitsPerSelector != null) {
			p.setProperty(FlinkConfigurationProperties.MAX_HITS_PER_SELECTOR, Integer.toString(maxHitsPerSelector));
		}

		p.setProperty(FlinkConfigurationProperties.WINDOW_LENGTH_IN_SECONDS, Integer.toString(windowLengthInSeconds));

		if (runTimeInSeconds != null) {
			p.setProperty(FlinkConfigurationProperties.STREAM_RUNTIME_SECONDS, Long.toString(runTimeInSeconds));
		}

		if (flinkParallelism != null) {
			p.setProperty(FlinkConfigurationProperties.FLINK_PARALLELISM, Integer.toString(flinkParallelism));
		}

		if (columnBufferMemory != null) {
			p.setProperty(ResponderProperties.COLUMN_BUFFER_MEMORY_MB, columnBufferMemory.toString());
		}



		// Pass the CryptoScheme configuration to the external application,
		// the external application needs to instantiate the CryptoScheme whit these
		// parameters
		final String schemeId = query.getQueryInfo().getCryptoSchemeId();
		CryptoScheme cryptoScheme = cryptoRegistry.cryptoSchemeByName(schemeId);
		Validate.notNull(cryptoScheme, "CryptoScheme not found for id: %s", schemeId);
		p.setProperty(CoreConfigurationProperties.CRYPTO_SCHEME_CLASS_NAME, cryptoScheme.getClass().getName());
		for (Entry<String, String> entry : cryptoScheme.configurationEntries()) {
			p.setProperty(entry.getKey(), entry.getValue());
		}

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

	private Path createKafkaConnectionPropertyFile(Path dir, Map<String, String> parameters) throws IOException {
		Path result = Paths.get(dir.toString(), "kafka.properties");

		Properties p = new Properties();
		p.setProperty(KafkaConfigurationProperties.BROKERS, this.kafkaBrokers);
		p.setProperty(KafkaConfigurationProperties.TOPIC, this.kafkaTopic);

		if (kafkaEmissionRate != null) {
			p.setProperty(KafkaConfigurationProperties.EMISSION_RATE_PER_SECOND, this.kafkaEmissionRate);
		}

		if (parameters != null) {
			String value = parameters.get(KafkaConfigurationProperties.OFFSET);
			if (value != null) {
				p.setProperty(KafkaConfigurationProperties.OFFSET, value);
			}
		}

		try (OutputStream os = new FileOutputStream(result.toFile())) {
			p.store(os, "Automatically generated by: " + this.getClass());
		}
		return result.getFileName();
	}

	@Override
	public DataSourceType getType() {
		return DataSourceType.Streaming;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.responder.data.service.QueryRunner#status(byte[])
	 */
	@Override
	public ExecutionStatus status(byte[] handle) {
		Validate.notNull(handle);
		final String jobId = new String(handle, Charsets.UTF_8);
		return statusQuerier.status(jobId);
	}
}
