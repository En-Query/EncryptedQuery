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

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.Validate;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocatedFileStatus;
import org.apache.hadoop.fs.RemoteIterator;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.SequenceFile.Reader;
import org.apache.hadoop.util.Tool;
import org.enquery.encryptedquery.data.QueryInfo;
import org.enquery.encryptedquery.encryption.CipherText;
import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.enquery.encryptedquery.encryption.CryptoSchemeFactory;
import org.enquery.encryptedquery.encryption.CryptoSchemeRegistry;
import org.enquery.encryptedquery.hadoop.core.HadoopConfigurationProperties;
import org.enquery.encryptedquery.responder.ResponderProperties;
import org.enquery.encryptedquery.utils.FileIOUtils;
import org.enquery.encryptedquery.xml.transformation.QueryReader;
import org.enquery.encryptedquery.xml.transformation.QueryTypeConverter;
import org.enquery.encryptedquery.xml.transformation.ResponseWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Responder extends Configured implements Tool {

	private static final Logger log = LoggerFactory.getLogger(Responder.class);

	private Path queryFile;
	private Path outputFile;
	private String inputFile;
	private Path configFile;
	private Path hdfsWorkingFolder;
	private Path hdfsInitFolder;
	private Path hdfsMultFolder;
	private Path hdfsFinalFolder;
	private boolean success;

	private QueryInfo queryInfo;
	private CryptoScheme crypto;

	private QueryTypeConverter queryConverter;

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

		success = ProcessData.run(conf,
				inputFile,
				hdfsMultFolder);

		if (success) {
			success = CombineColumnResults.run(conf,
					hdfsMultFolder,
					hdfsFinalFolder);
		}

		log.info("Jobs finished with success={}", success);

		if (success) {
			downloadResponse(conf);
		}

		cleanup(conf, success);

		// cleanup the crypto:
		if (crypto != null) {
			crypto.close();
			crypto = null;
		}

		return success ? 0 : 1;

	}

	private void downloadResponse(Configuration conf) throws IOException, XMLStreamException {
		log.info("Begin downloading response file(s).");
		final FileSystem fs = FileSystem.newInstance(conf);
		org.apache.hadoop.fs.Path hdfsFile = new org.apache.hadoop.fs.Path(hdfsFinalFolder.toString());

		try (OutputStream outStream = new FileOutputStream(outputFile.toFile());
				ResponseWriter responseWriter = new ResponseWriter(outStream);) {

			responseWriter.writeBeginDocument();
			responseWriter.writeBeginResponse();
			responseWriter.write(queryInfo);
			responseWriter.writeBeginResultSet();

			RemoteIterator<LocatedFileStatus> iterator = fs.listFiles(hdfsFile, false);
			while (iterator.hasNext()) {
				LocatedFileStatus status = iterator.next();
				if (status.getPath().getName().startsWith("part-r-")) {
					processFile(conf, responseWriter, status.getPath());
				}
			}

			responseWriter.writeEndResultSet();
			responseWriter.writeEndResponse();
			responseWriter.writeEndDocument();
		}

		log.info("Finished downloading response file(s).");
	}

	private void cleanup(Configuration conf, boolean success) throws IOException, XMLStreamException {
		log.info("Cleaning up HDFS temp dirs.");
		FileSystem fs = FileSystem.newInstance(conf);
		fs.delete(new org.apache.hadoop.fs.Path(hdfsInitFolder.toString()), true);
		fs.delete(new org.apache.hadoop.fs.Path(hdfsMultFolder.toString()), true);
		fs.delete(new org.apache.hadoop.fs.Path(hdfsFinalFolder.toString()), true);
		log.info("Finished cleaning up HDFS temp dirs.");
	}

	/**
	 * @param conf
	 * @param responseWriter
	 * @param path
	 * @throws IOException
	 * @throws XMLStreamException
	 */
	private void processFile(Configuration conf, ResponseWriter responseWriter, org.apache.hadoop.fs.Path path) throws IOException, XMLStreamException {
		log.info("Downloading file: {}", path);

		final IntWritable key = new IntWritable();
		final BytesWritable value = new BytesWritable();
		try (SequenceFile.Reader reader = new SequenceFile.Reader(conf, Reader.file(path), Reader.bufferSize(4096));) {
			while (reader.next(key, value)) {
				final CipherText cipherText = crypto.cipherTextFromBytes(value.copyBytes());
				responseWriter.writeResponseItem(key.get(), cipherText);
			}
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

		hdfsWorkingFolder = Paths.get(config.get(HadoopConfigurationProperties.HDFSWORKINGFOLDER));
		hdfsInitFolder = hdfsWorkingFolder.resolve("init");
		hdfsMultFolder = hdfsWorkingFolder.resolve("mult");
		hdfsFinalFolder = hdfsWorkingFolder.resolve("final");

		String mhs = config.get(ResponderProperties.MAX_HITS_PER_SELECTOR);
		if (mhs != null) {
			conf.setInt(ResponderProperties.MAX_HITS_PER_SELECTOR, Integer.valueOf(mhs));
		}

		conf.set(HadoopConfigurationProperties.HDFSWORKINGFOLDER, hdfsWorkingFolder.toString());
		// conf.set("mapreduce.map.speculative", "false");
		// conf.set("mapreduce.reduce.speculative", "false");

		crypto = CryptoSchemeFactory.make(config);
		final CryptoSchemeRegistry cryptoRegistry = new CryptoSchemeRegistry() {
			@Override
			public CryptoScheme cryptoSchemeByName(String schemeId) {
				if (schemeId.equals(crypto.name())) {
					return crypto;
				}
				return null;
			}
		};

		queryConverter = new QueryTypeConverter();
		queryConverter.setCryptoRegistry(cryptoRegistry);
		queryConverter.initialize();

		queryInfo = loadQueryInfo();

		// override Querier chunk size if needed
		Integer chunkSize = loadChunkSize(config);
		if (chunkSize != null) {
			conf.setInt(HadoopConfigurationProperties.CHUNK_SIZE, chunkSize);
			if (chunkSize < queryInfo.getDataChunkSize()) {
				queryInfo.setDataChunkSize(chunkSize);
			}
		}

		int columnBufferMemoryMB = loadBufferMemoryMB(config);
		if (columnBufferMemoryMB > 0) {
			conf.setInt(HadoopConfigurationProperties.COLUMN_BUFFER_MEMORY_MB, columnBufferMemoryMB);
		}
	}

	public QueryInfo loadQueryInfo() throws Exception {
		try (InputStream inputStream = new FileInputStream(queryFile.toFile());
				QueryReader qr = new QueryReader();) {

			qr.parse(inputStream);
			return queryConverter.fromXMLQueryInfo(qr.getQueryInfo());
		}
	}

	/**
	 * @param config
	 * @param queryFile2
	 * @return
	 * @throws Exception
	 */
	private Integer loadChunkSize(Map<String, String> config) throws Exception {
		String chunkSizeStr = config.get(HadoopConfigurationProperties.CHUNK_SIZE);
		if (chunkSizeStr != null) {
			return Integer.valueOf(chunkSizeStr);
		}
		return null;
	}

	/**
	 * @param config
	 * @return
	 */
	private int loadBufferMemoryMB(Map<String, String> config) {
		String value = config.get(HadoopConfigurationProperties.COLUMN_BUFFER_MEMORY_MB);
		if (value != null) {
			return Integer.valueOf(value);
		}
		return 0;
	}
}
