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
import java.util.Properties;
import java.util.concurrent.ExecutorService;

import javax.xml.bind.JAXBException;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.flink.FlinkConfigurationProperties;
import org.enquery.encryptedquery.query.wideskies.Query;
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
public class FlinkKafkaQueryRunner implements QueryRunner {

	private static final Logger log = LoggerFactory.getLogger(FlinkKafkaQueryRunner.class);

	private String name;
	private String description;
	private String brokers;
	private String topic;
	private String groupId;
	private Boolean forceFromStart;
	private String offsetLocation;
	private Integer windowLength;
	private Integer windowIterations;
	private String dataSchemaName = null;
	private String additionalFlinkArguments;
	private Path programPath;
	private Path jarPath;
	private Path runDir;
	private String encryptionMethodClass;
	private String modPowAbstractionClass;
	private String jniLibraryPath;
	private Integer flinkParallelism;
	private Integer computeThreshold;
	private Map<String, String> runParameters;
	private Integer columnEncryptionPartitionCount;

	@Reference
	private DataSchemaService dss;
	@Reference
	private ExecutorService threadPool;

	private DataSourceType type;

	private QueryTypeConverter queryTypeConverter;

	private Integer maxHitsPerSelector;

	@Activate
	void activate(final Config config) {
		name = config.name();
		Validate.notBlank(name, "name cannot be blank");

		description = config.description();
		Validate.notBlank(description, "description cannot be blank");

		log.info(
				"Creating FlinkKafkaQueryRunner with name '{}' and description '{}'.",
				name,
				description);

		brokers = config._kafka_brokers();
		topic = config._kafka_topic();
		groupId = config._kafka_groupId();
		if (config._kafka_force_from_start() != null) {
			forceFromStart = Boolean.valueOf(config._kafka_force_from_start());
		}
		offsetLocation = config._kafka_offset_location();
		dataSchemaName = config.data_schema_name();
		additionalFlinkArguments = config._additional_flink_arguments();

		if (config._stream_window_length() != null) {
			windowLength = Integer.valueOf(config._stream_window_length());
			Validate.isTrue(windowLength > 0);
		}

		if (config._stream_window_iterations() != null) {
			windowIterations = Integer.valueOf(config._stream_window_iterations());
		}

		Validate.notBlank(brokers, "Kafka Brokers cannot be blank.");
		Validate.notBlank(topic, "Kafka topic cannot be blank.");

		//TODO: validation for forceFromStart and offsetLocation

		
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

		encryptionMethodClass = config._column_encryption_class_name();
		Validate.notBlank(encryptionMethodClass);

		modPowAbstractionClass = config._mod_pow_class_name();
		Validate.notNull(modPowAbstractionClass);

		jniLibraryPath = config._jni_library_path();

		if (config._flink_parallelism() != null) {
			flinkParallelism = Integer.valueOf(config._flink_parallelism());
		}

		if (config._compute_threshold() != null) {
			computeThreshold = Integer.valueOf(config._compute_threshold());
		}

		Validate.notBlank(config.type(), "Type is required.");
		this.type = DataSourceType.valueOf(config.type());

		queryTypeConverter = new QueryTypeConverter();

		if (config._max_hits_per_selector() != null) {
			maxHitsPerSelector = Integer.parseInt(config._max_hits_per_selector());
			Validate.isTrue(maxHitsPerSelector > 0);
		}

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
	public void run(Map<String, String> parameters, Query query, String outputFileName, OutputStream stdOutput) {

		Validate.notNull(query);
		Validate.notNull(outputFileName);
		Validate.isTrue(!Files.exists(Paths.get(outputFileName)));

		this.runParameters = parameters;
		if (parameters != null) {
			for (Map.Entry<String, String> entry : parameters.entrySet()) {
				log.info(entry.getKey() + "/" + entry.getValue());
			}
		}


		Path workingTempDir = null;
		try {
			workingTempDir = Files.createTempDirectory(runDir, "flink-kafka");
			Path kafkaConProps = createKafkaConnectionPropertyFile(workingTempDir);
			Path queryFile = createQueryFile(workingTempDir, query);
			Path configFile = createConfigFile(workingTempDir, query);

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
			threadPool.submit(
					new ChildProcessLogger(proc.getInputStream(),
							log,
							stdOutput));

			int exitCode = proc.waitFor();

			log.info("Flink exited with code: {}.", exitCode);

			if (exitCode != 0) {
				throw new IOException("Error running Flink-Kafka application.");
			}

		} catch (IOException | InterruptedException | JAXBException e) {
			// FileUtils.deleteQuietly(workingTempDir.toFile());
			throw new RuntimeException("Error running Flink-Kafka query.", e);
		} finally {
			// FileUtils.deleteQuietly(workingTempDir.toFile());
		}
	}

	private Path createConfigFile(Path dir, Query query) throws FileNotFoundException, IOException {
		Path result = Paths.get(dir.toString(), "config.properties");

		Properties p = new Properties();

		p.setProperty(FlinkConfigurationProperties.MOD_POW_CLASS_NAME, modPowAbstractionClass);
		p.setProperty(FlinkConfigurationProperties.COLUMN_ENCRYPTION_CLASS_NAME, encryptionMethodClass);

		if (jniLibraryPath != null) {
			p.setProperty(FlinkConfigurationProperties.JNI_LIBRARIES, jniLibraryPath);
		}

		if (columnEncryptionPartitionCount != null) {
			p.setProperty(FlinkConfigurationProperties.COLUMN_ENCRYPTION_PARTITION_COUNT, Integer.toString(columnEncryptionPartitionCount));
		}

		if (computeThreshold != null) {
			p.setProperty(FlinkConfigurationProperties.COMPUTE_THRESHOLD, computeThreshold.toString());
		}

		if (maxHitsPerSelector != null) {
			p.setProperty(FlinkConfigurationProperties.MAX_HITS_PER_SELECTOR, maxHitsPerSelector.toString());
		}

		if (windowLength != null) {
			p.setProperty(FlinkConfigurationProperties.WINDOW_LENGTH, windowLength.toString());
		}

		if (windowIterations != null) {
			p.setProperty(FlinkConfigurationProperties.WINDOW_ITERATIONS, windowIterations.toString());
		}

		if (runParameters != null) {
			runParameters.forEach((k, v) -> {
				if (v != null) {
					p.setProperty(k, v);
				}
			});
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

	private Path createKafkaConnectionPropertyFile(Path dir) throws IOException {
		Path result = Paths.get(dir.toString(), "jdbc.properties");

		Properties p = new Properties();
		p.setProperty("kafka.brokers", this.brokers);
		p.setProperty("kafka.topic", this.topic);
		p.setProperty("kafka.groupId", this.groupId);
		p.setProperty("kafka.forceFromStart", this.forceFromStart.toString());
		p.setProperty("kafka.offsetLocation", this.offsetLocation);

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
