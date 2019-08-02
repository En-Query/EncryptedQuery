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
package org.enquery.encryptedquery.hadoop.mapreduce;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.Validate;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.enquery.encryptedquery.hadoop.core.HDFSFileIOUtils;
import org.enquery.encryptedquery.hadoop.core.HadoopConfigurationProperties;
import org.enquery.encryptedquery.utils.FileIOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App {

	private static final Logger log = LoggerFactory.getLogger(App.class);

	private Path queryFileName;
	private Path outputFileName;
	private String inputFileName;
	private CommandLine commandLine;
	private FileSystem hdfs;
	private Configuration hdfsConfig;
	private org.apache.hadoop.fs.Path hdfsInputFile;
	private Boolean v1ProcessingMethod = false;

	private static final Option queryFileNameOption = Option.builder("q")
			.desc("Encypted query file name (in XML format.)")
			.required()
			.hasArg()
			.build();

	private static final Option outputFileNameOption = Option.builder("o")
			.desc("Output response file name (in XML format.)")
			.required()
			.hasArg()
			.build();

	private static final Option configPropertyFileOption = Option.builder("sp")
			.desc("System configuration property file name.")
			.required()
			.hasArg()
			.build();
	
	private static final Option inputDataFileOption = Option.builder("i")
			.desc("Input Data File Name.")
			.required()
			.hasArg()
			.build();
	
	private Map<String, String> config;

	public static void main(String[] args) {
		try {
			log.info("Running Hadoop Map Reduce.");
			App app = new App();
			app.configure(args);
			int status = app.run();
			if (status == 0) {
				System.exit(0);
			} else {
				log.info("Hadoop Map Reduce Job Failed.");
				System.exit(1);
			}
		} catch (ParseException e) {
			System.out.println(MessageFormat.format("{0}\n", e.getMessage()));
			new HelpFormatter().printHelp("Hadoop-MapReduce-enquery", getOptions());
			System.exit(1);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	public void configure(String[] args) throws ParseException, Exception {
		commandLine = new DefaultParser().parse(getOptions(), args);

		queryFileName = Paths.get(getValue(queryFileNameOption));
		Validate.isTrue(Files.exists(queryFileName), "File %s does not exist.", queryFileName);

		inputFileName = getValue(inputDataFileOption);
        Validate.notBlank(inputFileName);
        
		outputFileName = Paths.get(getValue(outputFileNameOption));
		Validate.isTrue(!Files.exists(outputFileName), "Output file %s exists. Delete first.", outputFileName);
		
		config = FileIOUtils.loadPropertyFile(Paths.get(getValue(configPropertyFileOption)));

		log.info(" Configuration Parameters:");
		for (Map.Entry<String, String> entry : config.entrySet()) {
			log.info("   {} = {}", entry.getKey(), entry.getValue());
		}
		log.info("  HDFS Input File = {}", inputFileName);
		log.info("  HDFS Output File = {}", outputFileName);
		
		log.info("-End of Configuration Parameters");
		
		if (config.get(HadoopConfigurationProperties.PROCESSING_METHOD).equalsIgnoreCase("v1")) {
			v1ProcessingMethod=true;
		}
		
		String hdfsuri = config.get(HadoopConfigurationProperties.HDFSSERVER);
		String hdfsUser = config.get(HadoopConfigurationProperties.HDFSUSERNAME);
		String hdfsWorkingFolder = config.get(HadoopConfigurationProperties.HDFSWORKINGFOLDER);

		hdfs = setupHDFS(hdfsuri, hdfsUser, hdfsWorkingFolder);
		
		// Make sure Input file exists
		hdfsInputFile = new org.apache.hadoop.fs.Path(inputFileName);
		Validate.isTrue(hdfs.exists(hdfsInputFile), "Input File/Folder missing in HDFS.");
		
		//Write Query and Configuration files into HDFS so all tasks have access to the information.
		HDFSFileIOUtils.copyLocalFileToHDFS(hdfs, hdfsWorkingFolder, queryFileName);
        HDFSFileIOUtils.copyLocalFileToHDFS(hdfs,hdfsWorkingFolder, Paths.get(getValue(configPropertyFileOption)));
        
	}

	public static Options getOptions() {
		Options options = new Options();

		options.addOption(queryFileNameOption);
		options.addOption(outputFileNameOption);
		options.addOption(configPropertyFileOption);
		options.addOption(inputDataFileOption);

		return options;
	}

	public int run() throws Exception {
		Responder q = new Responder();
		q.setInputFileName(hdfsInputFile);
		q.setOutputFileName(outputFileName);
		q.setConfig(config);
		q.setQueryFileName(queryFileName);
		q.setConfigFileName(Paths.get(getValue(configPropertyFileOption)));
		q.setHdfs(hdfs);
		q.setV1ProcessingMethod(v1ProcessingMethod);
	    int success = q.run();
		
		hdfs.close();
		
		return success;
	}

	private String getValue(Option opt) {
		return commandLine.getOptionValue(opt.getOpt());
	}
	
	private FileSystem setupHDFS(String hdfsuri, String hdfsUsername, String hadoopRunDir) throws IOException {
		// ====== Init HDFS File System Object
		hdfsConfig = new Configuration();
		hdfsConfig.set("fs.defaultFS", hdfsuri);
		System.setProperty("HADOOP_USER_NAME", hdfsUsername);
		System.setProperty("hadoop.home.dir", "/");

		hdfs = FileSystem.get(URI.create(hdfsuri), hdfsConfig);

		org.apache.hadoop.fs.Path hdfsWorkingFolder = new org.apache.hadoop.fs.Path(hadoopRunDir);
		if (!hdfs.exists(hdfsWorkingFolder)) {
			// Create new Directory
			hdfs.mkdirs(hdfsWorkingFolder);
			log.info("Hadoop Path ({}) created.", hadoopRunDir);
		}
		
		return hdfs;

	}

}
