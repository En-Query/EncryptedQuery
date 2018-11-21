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
package org.enquery.encryptedquery.responder.standalone.runner;

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
import org.enquery.encryptedquery.query.wideskies.Query;
import org.enquery.encryptedquery.responder.business.ChildProcessLogger;
import org.enquery.encryptedquery.responder.data.entity.DataSourceType;
import org.enquery.encryptedquery.responder.data.service.QueryRunner;
import org.enquery.encryptedquery.standalone.StandaloneConfigurationProperties;
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
public class StandaloneQueryRunner implements QueryRunner {

	private static final Logger log = LoggerFactory.getLogger(StandaloneQueryRunner.class);
	private Path javaPath;
	private Path jarPath;
	private String name;
	private String description;
	private String dataSchemaName;
	private Path dataSourceFilePath;
	private Path runDir;
	private String encryptionMethodClass;
	private String modPowAbstractionClass;
	private String javaOptions;
	private QueryTypeConverter queryTypeConverter;
	private int numberOfThreads;
	private int maxQueueSize;
	private long computeThreshold;
	private String jniLibryPath;

	@Reference
	private ExecutorService threadPool;

	@Activate
	void activate(final Config config) {
		name = config.name();
		Validate.notBlank(name, "name cannot be blank");

		description = config.description();
		Validate.notBlank(description, "description cannot be blank");

		log.info(
				"Creating StandaloneRunner with name '{}' and description '{}'.",
				name,
				description);

		dataSchemaName = config.data_schema_name();

		Validate.notBlank(dataSchemaName, "DataSchema name cannot be blank.");
		Validate.notBlank(config._run_directory(), "Run directory cannot be blank.");

		dataSourceFilePath = Paths.get(config.data_source_file());
		Validate.isTrue(Files.exists(dataSourceFilePath), "Does not exists: " + dataSourceFilePath);

		runDir = Paths.get(config._run_directory());
		Validate.isTrue(Files.exists(runDir), "Does not exists: " + runDir);

		encryptionMethodClass = config._column_encryption_class_name();
		Validate.notBlank(encryptionMethodClass);

		modPowAbstractionClass = config._mod_pow_class_name();
		Validate.notBlank(modPowAbstractionClass);

		javaPath = Paths.get(config._java_path());
		Validate.isTrue(Files.exists(javaPath), "Does not exists: " + javaPath);

		Validate.notNull(config._application_jar_path(), "Missing .application.jar.path property.");
		jarPath = Paths.get(config._application_jar_path());
		Validate.isTrue(Files.exists(jarPath), "Does not exists: " + jarPath);

		javaOptions = config._java_options();

		queryTypeConverter = new QueryTypeConverter();

		numberOfThreads = config._number_of_threads();
		maxQueueSize = config._max_queue_size();
		computeThreshold = config._compute_threshold();
		jniLibryPath = config._jni_library_path();
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
	public DataSourceType getType() {
		return DataSourceType.Batch;
	}

	@Override
	public void run(Map<String, String> parameters, Query query, String outputFileName, OutputStream stdOutput) {
		Validate.notNull(query);
		Validate.notNull(outputFileName);
		log.info("Output File: {}", outputFileName);
		Validate.isTrue(!Files.exists(Paths.get(outputFileName)));
		Path workingTempDir = null;
		try {
			workingTempDir = Files.createTempDirectory(runDir, "stand-alone-");
			Path queryFile = createQueryFile(workingTempDir, query);
			Path configFile = createConfigFile(workingTempDir, parameters);

			List<String> arguments = new ArrayList<>();
			arguments.add(javaPath.toString());
			if (javaOptions != null) {
				arguments.add(javaOptions);
			}
			arguments.add("-jar");
			arguments.add(jarPath.toAbsolutePath().toString());
			arguments.add("-q");
			arguments.add(queryFile.toAbsolutePath().toString());
			arguments.add("-c");
			arguments.add(configFile.toAbsolutePath().toString());
			arguments.add("-o");
			arguments.add(outputFileName);
			arguments.add("-i");
			arguments.add(dataSourceFilePath.toAbsolutePath().toString());

			ProcessBuilder processBuilder = new ProcessBuilder(arguments);
			processBuilder.directory(workingTempDir.toFile());
			processBuilder.redirectErrorStream(true);

			log.info("Launch stand-alone query runner with arguments: " + arguments);
			Process proc = processBuilder.start();

			// capture and log child process output in separate thread
			threadPool.submit(
					new ChildProcessLogger(proc.getInputStream(),
							log,
							stdOutput));

			int exitCode = proc.waitFor();

			log.info("Standalone execution exited with code: {}.", exitCode);

			if (exitCode != 0) {
				throw new IOException("Error running Standalone Query Runner.");
			}

		} catch (IOException | InterruptedException | JAXBException e) {
			// FileUtils.deleteQuietly(workingTempDir.toFile());
			throw new RuntimeException("Error running Standalone Query Runner.", e);
		} finally {
			// FileUtils.deleteQuietly(workingTempDir.toFile());
		}
	}


	private Path createConfigFile(Path dir, Map<String, String> parameters) throws FileNotFoundException, IOException {
		Path result = dir.resolve("config.properties");

		Properties p = new Properties();

		p.put(StandaloneConfigurationProperties.COLUMN_ENCRYPTION_CLASS_NAME, encryptionMethodClass);
		p.put(StandaloneConfigurationProperties.MOD_POW_CLASS_NAME, modPowAbstractionClass);
		p.put(StandaloneConfigurationProperties.PROCESSING_THREADS, Integer.toString(numberOfThreads));
		p.put(StandaloneConfigurationProperties.MAX_QUEUE_SIZE, Integer.toString(maxQueueSize));
		p.put(StandaloneConfigurationProperties.COMPUTE_THRESHOLD, Long.toString(computeThreshold));

		if (jniLibryPath != null) {
			p.put("jni.library.path", jniLibryPath);
		}

		if (parameters != null) {
			parameters.forEach((k, v) -> {
				p.put(k, v);
			});
		}

		try (OutputStream os = new FileOutputStream(result.toFile())) {
			p.store(os, "Automatically generated by: " + this.getClass());
		}
		return result;
	}

	private Path createQueryFile(Path dir, Query query) throws IOException, JAXBException {
		Path result = dir.resolve("query.xml");
		try (FileOutputStream os = new FileOutputStream(result.toFile())) {
			queryTypeConverter.marshal(queryTypeConverter.toXMLQuery(query), os);
		}
		return result;
	}
}
