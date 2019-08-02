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
package org.enquery.encryptedquery.responder.hadoop.mapreduce.runner;

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
import java.util.concurrent.ExecutorService;

import org.apache.commons.codec.Charsets;
import org.apache.commons.lang3.Validate;

import org.enquery.encryptedquery.core.CoreConfigurationProperties;
import org.enquery.encryptedquery.data.Query;
import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.enquery.encryptedquery.encryption.CryptoSchemeRegistry;
import org.enquery.encryptedquery.hadoop.core.HadoopConfigurationProperties;
import org.enquery.encryptedquery.json.JSONStringConverter;
import org.enquery.encryptedquery.responder.ResponderProperties;
import org.enquery.encryptedquery.responder.business.ChildProcessLogger;
import org.enquery.encryptedquery.responder.data.entity.DataSourceType;
// import org.enquery.encryptedquery.responder.data.entity.Execution;
import org.enquery.encryptedquery.responder.data.entity.ExecutionStatus;
import org.enquery.encryptedquery.responder.data.service.DataSchemaService;
import org.enquery.encryptedquery.responder.data.service.QueryRunner;
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
public class HadoopMapReduceRunner implements QueryRunner {

	private static final Logger log = LoggerFactory.getLogger(HadoopMapReduceRunner.class);

	private String name;
	private String description;
	private String dataSchemaName;
	private String dataSourceFilePath;
	private String dataSourceRecordType;
	private String limitHitsPerSelector;
	private String computeThreshold;
	private java.nio.file.Path programPath;
	private java.nio.file.Path jarPath;
	private Path runDir;
	private String queryLocation;
	
	//Hadoop Parameters from Data Source
	private String MRMapMemory;
	private String MRReduceMemory;
	private String MRMapJavaOpts;
	private String MRReduceJavaOpts;
	private String hadoopRunDir;
	private String hdfsuri;
	private String hdfsUsername;
	private String hadoopReduceTasks;
	private String additionalHadoopArguments;
	@SuppressWarnings("unused")
	private String processingMethod;
	private String chunkingByteSize;    // This is for v1 processing only
	

	@Reference
	private DataSchemaService dss;
	@Reference
	private ExecutorService threadPool;
	@Reference
	private CryptoSchemeRegistry cryptoRegistry;

	@Activate
	void activate(final Config config) {
		name = config.name();
		Validate.notBlank(name, "name cannot be blank");

		description = config.description();
		Validate.notBlank(description, "description cannot be blank");

		log.info("Creating Hadoop MapReduce Runner with name '{}' and description '{}'.", name, description);

		dataSourceFilePath = config.data_source_file();
		Validate.notBlank(dataSourceFilePath, "Data Source File Path(HDFS) cannot be blank");

		dataSourceRecordType = config.data_source_record_type();
		Validate.notBlank(dataSourceRecordType, "Data Source Record Type cannot be blank");

		dataSchemaName = config.data_schema_name();
		Validate.notBlank(dataSchemaName, "DataSchema name cannot be blank.");

		hdfsuri = config._hadoop_server_uri();
		Validate.notBlank(hdfsuri, "Hadoop server URI cannot be blank.");

		hdfsUsername = config._hadoop_username();
		Validate.notBlank(hdfsUsername, "Hadoop username cannot be blank.");

		runDir = Paths.get(config._run_directory());
		Validate.isTrue(Files.exists(runDir), "Does not exists: " + runDir);

		hadoopRunDir = config._hdfs_run_directory();
		Validate.notBlank(hadoopRunDir, "Hadoop Working Folder cannot be blank.");

		hadoopReduceTasks = config._hadoop_reduce_tasks();

		additionalHadoopArguments = config._additional_hadoop_arguments();

		limitHitsPerSelector = config._limit_hits_per_selector();
		MRMapMemory = config._hadoop_mapreduce_map_memory_mb();
		MRReduceMemory = config._hadoop_mapreduce_reduce_memory_mb();
		MRMapJavaOpts = config._hadoop_mapreduce_map_java_opts();
		MRReduceJavaOpts = config._hadoop_mapreduce_reduce_java_opts();
		chunkingByteSize = config._hadoop_chunking_byte_size(); // Used for v1 processing, ignored for rest
		computeThreshold = config._compute_threshold();
		processingMethod = config._hadoop_processing_method();

		Validate.isTrue(processingMethod.equalsIgnoreCase("v1") || processingMethod.equalsIgnoreCase("v2"), "Invalid Processing Method (v1 or v2) only.");

		Validate.notBlank(config._hadoop_install_dir(), "Hadoop install dir cannot be blank.");
		Validate.notBlank(config._application_jar_path(), "Hadoop jar path cannot be blank.");
		Validate.notBlank(config._run_directory(), "Run directory cannot be blank.");

		jarPath = Paths.get(config._application_jar_path());
		Validate.isTrue(Files.exists(jarPath), "Does not exists: " + jarPath);

		programPath = Paths.get(config._hadoop_install_dir(), "bin", "hadoop");
		Validate.isTrue(Files.exists(programPath), "Does not exists: " + programPath);

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
		Validate.isTrue(!Files.exists(Paths.get(outputFileName)));

		if (parameters != null) {
			log.info("Parameters: {}", parameters);
		}
		log.info("Working Folder: {}", runDir);
		log.info("HDFS Uri: {}", hdfsuri);

		Path workingTempDir = null;
		try {
			
			workingTempDir = Files.createTempDirectory(runDir, "hadoop-mapreduce-");
            queryLocation = parameters.get("queryLocation");
            Validate.notNull(queryLocation);
            
			Path queryFile = Paths.get(queryLocation.replaceAll("file:", ""));
			Path configFile = createConfigFile(workingTempDir, query, parameters);

			List<String> arguments = new ArrayList<>();
			arguments.add(programPath.toString());
			arguments.add("jar");
			if (additionalHadoopArguments != null) {
				String[] options = additionalHadoopArguments.split(" ");
				for (String o : options) {
					arguments.add(o);
				}
			}

			arguments.add(jarPath.toString());
			arguments.add("org.enquery.encryptedquery.hadoop.mapreduce.App");
		
			arguments.add("-i");
			arguments.add(dataSourceFilePath);
			arguments.add("-q");
			arguments.add(queryFile.toString());
			arguments.add("-sp");
			arguments.add(configFile.toString());
			arguments.add("-o");
			arguments.add(outputFileName);

			ProcessBuilder processBuilder = new ProcessBuilder(arguments);
			processBuilder.directory(workingTempDir.toFile());
			processBuilder.redirectErrorStream(true);

			log.info("Launch Hadoop with arguments: " + arguments);
			Process proc = processBuilder.start();

			// capture and log child process output in separate thread
			threadPool.submit(
					new ChildProcessLogger(proc.getInputStream(),
							log,
							stdOutput));

			int exitCode = proc.waitFor();
			Date endDate = new Date();

			log.info("Hadoop exited with code: {}.", exitCode);

			ExecutionStatus result = null;
			if (exitCode != 0) {
				result = new ExecutionStatus(endDate, "Error running Hadoop MapReduce application. Exit code: " + exitCode, false);
			} else {
				result = new ExecutionStatus(endDate, null, false);
			}

			return JSONStringConverter.toString(result).getBytes();

		} catch (IOException | InterruptedException e) {
			throw new RuntimeException("Error running Hadoop-MapReduce query.", e);
		} finally {
			// FileUtils.deleteQuietly(workingTempDir.toFile());
		}
	}

	private Path createConfigFile(Path dir, Query query, Map<String, String> parameters) throws FileNotFoundException, IOException {
		Path result = Paths.get(dir.toString(), "config.properties");
		int maxHitsPerSelector = 10000;
		Properties p = new Properties();

		if (parameters != null) {
			if (parameters.containsKey(ResponderProperties.MAX_HITS_PER_SELECTOR)) {
				maxHitsPerSelector = Integer.parseInt(parameters.get(ResponderProperties.MAX_HITS_PER_SELECTOR));
			}
		}
		p.setProperty(ResponderProperties.MAX_HITS_PER_SELECTOR, Integer.toString(maxHitsPerSelector));
		p.setProperty(ResponderProperties.LIMIT_HITS_PER_SELECTOR, limitHitsPerSelector);
		p.setProperty(ResponderProperties.DATA_SOURCE_RECORD_TYPE, dataSourceRecordType);
		p.setProperty(ResponderProperties.COMPUTE_THRESHOLD, computeThreshold);
		p.setProperty(HadoopConfigurationProperties.HDFSWORKINGFOLDER, hadoopRunDir);
		p.setProperty(HadoopConfigurationProperties.HDFSSERVER, hdfsuri);
		p.setProperty(HadoopConfigurationProperties.HDFSUSERNAME, hdfsUsername);
		p.setProperty(HadoopConfigurationProperties.HADOOP_REDUCE_TASKS, hadoopReduceTasks);
		p.setProperty(HadoopConfigurationProperties.PROCESSING_METHOD, processingMethod);
		
		p.setProperty(HadoopConfigurationProperties.CHUNKING_BYTE_SIZE, chunkingByteSize);     // Used for v1 processing
		
		if (MRMapMemory != null) {
			p.setProperty(HadoopConfigurationProperties.MR_MAP_MEMORY_MB, MRMapMemory);
		}
		if (MRReduceMemory != null) {
			p.setProperty(HadoopConfigurationProperties.MR_REDUCE_MEMORY_MB, MRReduceMemory);
		}
		if (MRMapJavaOpts != null) {
			p.setProperty(HadoopConfigurationProperties.MR_MAP_JAVA_OPTS, MRMapJavaOpts);
		}
		if (MRReduceJavaOpts != null) {
			p.setProperty(HadoopConfigurationProperties.MR_REDUCE_JAVA_OPTS, MRReduceJavaOpts);
		}

		// Pass the CryptoScheme configuration to the external application,
		// the external application needs to instantiate the CryptoScheme with these parameters
		final String schemeId = query.getQueryInfo().getCryptoSchemeId();
		CryptoScheme cryptoScheme = cryptoRegistry.cryptoSchemeByName(schemeId);
		Validate.notNull(cryptoScheme, "CryptoScheme not found for id: %s", schemeId);
		p.setProperty(CoreConfigurationProperties.CRYPTO_SCHEME_CLASS_NAME, cryptoScheme.getClass().getName());
		for (Entry<String, String> entry : cryptoScheme.configurationEntries()) {
			p.setProperty(entry.getKey(), entry.getValue());
		}

		StringBuilder sb = new StringBuilder();
		p.forEach((k, v) -> {
			sb.append("\n     ");
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