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
import java.util.Date;
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
import org.enquery.encryptedquery.json.JSONStringConverter;
import org.enquery.encryptedquery.responder.ResponderProperties;
import org.enquery.encryptedquery.responder.business.ChildProcessLogger;
import org.enquery.encryptedquery.responder.data.entity.DataSourceType;
import org.enquery.encryptedquery.responder.data.entity.ExecutionStatus;
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
	private String javaOptions;
	private int numberOfThreads;
	private int columnBufferMemoryMB;
	private int maxRecordQueueSize;
	private int maxColumnQueueSize;
	private int maxResponseQueueSize;

	@Reference
	private ExecutorService threadPool;
	@Reference
	private QueryTypeConverter queryTypeConverter;
	@Reference
	private CryptoSchemeRegistry cryptoRegistry;
	private String chunkSize;

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

		javaPath = Paths.get(config._java_path());
		Validate.isTrue(Files.exists(javaPath), "Does not exists: " + javaPath);

		Validate.notNull(config._application_jar_path(), "Missing .application.jar.path property.");
		jarPath = Paths.get(config._application_jar_path());
		Validate.isTrue(Files.exists(jarPath), "Does not exists: " + jarPath);

		javaOptions = config._java_options();

		numberOfThreads = config._number_of_threads();
		columnBufferMemoryMB = config._column_buffer_memory_mb();
		maxRecordQueueSize = config._max_record_queue_size();
		maxColumnQueueSize = config._max_column_queue_size();
		maxResponseQueueSize = config._max_response_queue_size();
		chunkSize = config._chunk_size();
		if (chunkSize != null) {
			int intChunkSize = Integer.parseInt(chunkSize);
			Validate.isTrue(intChunkSize > 0);
			log.info("chunk.size = {}", intChunkSize);
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
	public DataSourceType getType() {
		return DataSourceType.Batch;
	}

	@Override
	public byte[] run(Query query, Map<String, String> parameters, String outputFileName, OutputStream stdOutput) {

		Validate.notNull(query);
		Validate.notNull(outputFileName);
		Validate.isTrue(!Files.exists(Paths.get(outputFileName)), "Folder/file: " + Paths.get(outputFileName).toString() + " already exists.");

		Path workingTempDir = null;
		try {
			workingTempDir = Files.createTempDirectory(runDir, "stand-alone-");
			Path queryFile = createQueryFile(workingTempDir, query);
			Path configFile = createConfigFile(workingTempDir, parameters, query);

			List<String> arguments = new ArrayList<>();
			arguments.add(javaPath.toString());
			if (javaOptions != null) {
				String[] options = javaOptions.split(" ");
				for (String o : options) {
					arguments.add(o);
				}
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
			Future<?> logCaptureFuture = threadPool.submit(
					new ChildProcessLogger(proc.getInputStream(),
							log,
							stdOutput));

			int exitCode = proc.waitFor();
			Date endDate = new Date();
			log.info("Standalone execution exited with code: {}.", exitCode);

			// wait for log capture to end
			logCaptureFuture.get();

			ExecutionStatus result = null;
			if (exitCode != 0) {
				result = new ExecutionStatus(endDate, "Standalone Query Runner exited with code: " + exitCode, false);
			} else {
				result = new ExecutionStatus(endDate, null, false);
			}

			return JSONStringConverter.toString(result).getBytes();

		} catch (IOException | InterruptedException | JAXBException | ExecutionException e) {
			throw new RuntimeException("Error running Standalone Query Runner.", e);
		} finally {
			FileUtils.deleteQuietly(workingTempDir.toFile());
		}
	}


	private Path createConfigFile(Path dir, Map<String, String> parameters, Query query) throws FileNotFoundException, IOException {
		Path result = dir.resolve("config.properties");

		Properties p = new Properties();

		if (parameters != null) {
			if (parameters.containsKey(ResponderProperties.MAX_HITS_PER_SELECTOR)) {
				p.put(StandaloneConfigurationProperties.MAX_HITS_PER_SELECTOR,
						parameters.get(ResponderProperties.MAX_HITS_PER_SELECTOR));
			}
		}
		p.put(StandaloneConfigurationProperties.PROCESSING_THREADS, Integer.toString(numberOfThreads));

		if (chunkSize != null) {
			p.put(StandaloneConfigurationProperties.CHUNK_SIZE, chunkSize);
		}

		// V2-specific configurations
		if (columnBufferMemoryMB > 0) {
			p.put(ResponderProperties.COLUMN_BUFFER_MEMORY_MB, Integer.toString(columnBufferMemoryMB));
		}
		if (maxRecordQueueSize > 0) {
			p.put(StandaloneConfigurationProperties.MAX_RECORD_QUEUE_SIZE, Integer.toString(maxRecordQueueSize));
		}
		if (maxColumnQueueSize > 0) {
			p.put(StandaloneConfigurationProperties.MAX_COLUMN_QUEUE_SIZE, Integer.toString(maxColumnQueueSize));
		}
		if (maxResponseQueueSize > 0) {
			p.put(StandaloneConfigurationProperties.MAX_RESPONSE_QUEUE_SIZE, Integer.toString(maxResponseQueueSize));
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.responder.data.service.QueryRunner#status(byte[])
	 */
	@Override
	public ExecutionStatus status(byte[] handle) {

		Map<String, Object> map = JSONStringConverter.toStringObjectMap(new String(handle, Charsets.UTF_8));

		Date endTime = new Date((Long) map.get("endTime"));
		String error = (String) map.get("error");

		return new ExecutionStatus(endTime, error, false);
	}
}
