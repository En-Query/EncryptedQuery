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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;

import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.Validate;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.util.Tool;
import org.enquery.encryptedquery.hadoop.core.HDFSFileIOUtils;
import org.enquery.encryptedquery.hadoop.core.HadoopConfigurationProperties;
import org.enquery.encryptedquery.responder.ResponderProperties;
import org.enquery.encryptedquery.utils.FileIOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Responder extends Configured implements Tool {

	private static final Logger log = LoggerFactory.getLogger(Responder.class);

	private Path queryFile;
	private Path outputFile;
	private String inputFile;
	private Boolean v1ProcessingMethod = false;
	private Path configFile;
	private Path hdfsWorkingFolder;
	private Path hdfsInitFolder;
	private Path hdfsMultFolder;
	private Path hdfsFinalFolder;
	private boolean success;

	public boolean isSuccess() {
		return success;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.hadoop.util.Tool#run(java.lang.String[])
	 */
	@Override
	public int run(String[] args) throws Exception {
		log.info("Starting Responder with args: {}", Arrays.toString(args));

		Configuration conf = getConf();
		configure(args, conf);

		success = true;

		if (v1ProcessingMethod) {
			if (success) {
				success = SortDataIntoRows.run(conf,
						inputFile,
						hdfsInitFolder);
			}
			if (success) {
				success = ProcessColumns_v1.run(conf,
						hdfsInitFolder,
						hdfsMultFolder);
			}
		} else {
			if (success) {
				success = ProcessData.run(conf,
						inputFile,
						hdfsMultFolder);
			}

		}

		// Concatenate the output to one file

		if (success) {
			success = CombineColumnResults.run(conf,
					hdfsMultFolder,
					hdfsFinalFolder,
					outputFile.getFileName().toString(),
					v1ProcessingMethod);
		}

		// Clean up
		cleanup(conf, success);

		return success ? 0 : 1;

	}

	private void cleanup(Configuration conf, boolean success) throws IOException {
		FileSystem fs = FileSystem.newInstance(conf);

		fs.delete(new org.apache.hadoop.fs.Path(hdfsInitFolder.toString()), true);
		fs.delete(new org.apache.hadoop.fs.Path(hdfsMultFolder.toString()), true);
		fs.delete(new org.apache.hadoop.fs.Path(hdfsFinalFolder.toString()), true);
		if (success) {
			org.apache.hadoop.fs.Path hdfsFile = new org.apache.hadoop.fs.Path(hdfsWorkingFolder + org.apache.hadoop.fs.Path.SEPARATOR + outputFile.getFileName().toString());
			HDFSFileIOUtils.copyHDFSFileToLocal(fs, hdfsFile, outputFile);
		}
	}

	public void configure(String[] args, Configuration conf) throws ParseException, Exception {
		CommandLineOptions clo = new CommandLineOptions(args);

		queryFile = clo.queryFile();
		Validate.isTrue(Files.exists(queryFile), "File %s does not exist.", queryFile);

		inputFile = clo.inputFile();
		Validate.notBlank(inputFile);

		outputFile = clo.outputFile();
		Validate.isTrue(!Files.exists(outputFile), "Output file %s exists. Delete first.", outputFile);

		configFile = clo.configFile();
		Map<String, String> config = FileIOUtils.loadPropertyFile(configFile);
		if (config.get(HadoopConfigurationProperties.PROCESSING_METHOD).equalsIgnoreCase("v1")) {
			v1ProcessingMethod = true;
		}

		hdfsWorkingFolder = Paths.get(config.get(HadoopConfigurationProperties.HDFSWORKINGFOLDER));
		hdfsInitFolder = hdfsWorkingFolder.resolve("init");
		hdfsMultFolder = hdfsWorkingFolder.resolve("mult");
		hdfsFinalFolder = hdfsWorkingFolder.resolve("final");

		String mhs = config.get(ResponderProperties.MAX_HITS_PER_SELECTOR);
		if (mhs != null) {
			conf.setInt(ResponderProperties.MAX_HITS_PER_SELECTOR, Integer.valueOf(mhs));
		}

		conf.set(HadoopConfigurationProperties.CHUNKING_BYTE_SIZE, config.get(HadoopConfigurationProperties.CHUNKING_BYTE_SIZE));
		conf.set(HadoopConfigurationProperties.HDFSWORKINGFOLDER, hdfsWorkingFolder.toString());
		conf.set("mapreduce.map.speculative", "false");
		conf.set("mapreduce.reduce.speculative", "false");
	}
}
