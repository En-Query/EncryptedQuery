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

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.Map;

import javax.xml.bind.JAXBException;

import org.apache.commons.lang3.Validate;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.enquery.encryptedquery.data.DataSchema;
import org.enquery.encryptedquery.data.Query;
import org.enquery.encryptedquery.data.QuerySchema;
import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.enquery.encryptedquery.encryption.CryptoSchemeFactory;
import org.enquery.encryptedquery.encryption.CryptoSchemeRegistry;
import org.enquery.encryptedquery.hadoop.core.HDFSFileIOUtils;
import org.enquery.encryptedquery.hadoop.core.HadoopConfigurationProperties;
import org.enquery.encryptedquery.xml.transformation.QueryTypeConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Responder {

	private static final Logger log = LoggerFactory.getLogger(Responder.class);

	private Map<String, String> config;

	protected QuerySchema querySchema;
	protected DataSchema dataSchema;
	private java.nio.file.Path queryFileName;
	private java.nio.file.Path configFileName;
	protected Query query;
	private FileSystem hdfs;
	protected QueryTypeConverter queryTypeConverter;
	
	private Path inputDataFile;
	private Path hdfsWorkingFolder;
	private Path hdfsInitFolder;
	private Path hdfsMultFolder;
	private Path hdfsFinalFolder;
	private java.nio.file.Path outputFileName;
	private Boolean v1ProcessingMethod;

	public void initialize() throws Exception {
		
        hdfsWorkingFolder = new Path(config.get(HadoopConfigurationProperties.HDFSWORKINGFOLDER));
        hdfsInitFolder = new Path(hdfsWorkingFolder + Path.SEPARATOR + outputFileName.getFileName().toString() + "_init"); 
        hdfsMultFolder = new Path(hdfsWorkingFolder + Path.SEPARATOR + outputFileName.getFileName().toString() + "_mult"); 
        hdfsFinalFolder = new Path(hdfsWorkingFolder + Path.SEPARATOR + outputFileName.getFileName().toString() + "_final"); 

		initializeCryptoScheme();

		query = loadQuery(queryFileName);
		Validate.notNull(query);
		Validate.notNull(query.getQueryInfo());
		String queryInfoFileName = hdfsWorkingFolder.toString() + Path.SEPARATOR + "query-info";
		writeToHDFS(query.getQueryInfo(), queryInfoFileName);
		
	}

	/**
	 * @param config2
	 * @throws Exception
	 */
	private void initializeCryptoScheme() throws Exception {

		final CryptoScheme crypto = CryptoSchemeFactory.make(config);

		CryptoSchemeRegistry cryptoRegistry = new CryptoSchemeRegistry() {
			@Override
			public CryptoScheme cryptoSchemeByName(String schemeId) {
				if (schemeId.equals(crypto.name())) {
					return crypto;
				}
				return null;
			}
		};

		queryTypeConverter = new QueryTypeConverter();
		queryTypeConverter.setCryptoRegistry(cryptoRegistry);
		queryTypeConverter.initialize();
	}

	protected Query loadQuery(java.nio.file.Path file) throws IOException, FileNotFoundException, JAXBException {
		try (FileInputStream fis = new FileInputStream(file.toFile())) {
			org.enquery.encryptedquery.xml.schema.Query xml = queryTypeConverter.unmarshal(fis);
			return queryTypeConverter.toCoreQuery(xml);
		}
	}
	
	public int run() throws Exception {
		log.info("Starting Responder");

		boolean success = true;
		initialize();

		if (v1ProcessingMethod) {
			if (success) {
				success = SortDataIntoRows.run(inputDataFile, query.getQueryInfo(), config, hdfs, hdfsInitFolder);
			}
			if (success) {
				success = ProcessColumns_v1.run(config, query.getQueryInfo(), hdfs, hdfsInitFolder, hdfsMultFolder,
						queryFileName, configFileName);
			}
		} else {

			if (success) {
				success = ProcessData.run(inputDataFile, query.getQueryInfo(), config, hdfs, hdfsMultFolder,
						queryFileName, configFileName);
			}

		}
		// Concatenate the output to one file
		if (success) {
			success = CombineColumnResults.run(config, query.getQueryInfo(), hdfs, hdfsMultFolder, hdfsFinalFolder,
					outputFileName.getFileName().toString(), configFileName, v1ProcessingMethod);
		}

		// Clean up
		hdfs.delete(hdfsInitFolder, true);
		hdfs.delete(hdfsMultFolder, true);
		hdfs.delete(hdfsFinalFolder, true);
		if (success) {
			Path hdfsFile = new Path(hdfsWorkingFolder + Path.SEPARATOR + outputFileName.getFileName().toString());
			HDFSFileIOUtils.copyHDFSFileToLocal(hdfs, hdfsFile, outputFileName);
		}

		return success ? 0 : 1;

	}

	public Boolean getV1ProcessingMethod() {
		return v1ProcessingMethod;
	}

	public void setV1ProcessingMethod(Boolean v1ProcessingMethod) {
		this.v1ProcessingMethod = v1ProcessingMethod;
	}

	public java.nio.file.Path getOutputFileName() {
		return outputFileName;
	}

	public void setOutputFileName(java.nio.file.Path outputFileName) {
		this.outputFileName = outputFileName;
	}

	public Path getInputDataFile() {
		return inputDataFile;
	}

	public void setInputFileName(Path inputDataFile) {
		this.inputDataFile = inputDataFile;
	}
	
	public void setConfig(Map<String, String> config) {
		this.config = config;
	}
	
	public java.nio.file.Path getQueryFileName() {
		return queryFileName;
	}
	
	public void setQueryFileName(java.nio.file.Path queryFileName) {
		this.queryFileName = queryFileName;
	}
	
	public java.nio.file.Path getConfigFileName() {
		return configFileName;
	}
	
	public void setConfigFileName(java.nio.file.Path configFileName) {
		this.configFileName = configFileName;
	}
	
	public FileSystem getHdfs() {
		return this.hdfs;
	}
	
	public void setHdfs(FileSystem hdfs) {
		this.hdfs = hdfs;
	}
	
	private void writeToHDFS(Object objToWrite, String hdfsFileName) throws IOException {
		log.info("Writing {} into HDFS", hdfsFileName );

		org.apache.hadoop.fs.Path hdfswritepath = new org.apache.hadoop.fs.Path(hdfsFileName);
		
		if (hdfs.exists(hdfswritepath)) {
			hdfs.delete(hdfswritepath, false);
		}
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ObjectOutput out = null;
		byte[] outputBytes = null;
		try {
			out = new ObjectOutputStream(bos);
			out.writeObject(objToWrite);
			out.flush();
			outputBytes = bos.toByteArray();
		} finally {
			try {
			bos.close();
			} catch (IOException ex) {
				log.error("Failed to convert object {} to byte array", objToWrite.getClass().getName());
			}
		}

		FSDataOutputStream outputStream = hdfs.create(hdfswritepath);
		outputStream.write(outputBytes);
        outputStream.close();		
	}
}
