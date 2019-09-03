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
package org.enquery.encryptedquery.flink;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.JAXBException;

import org.apache.commons.lang3.Validate;
import org.apache.flink.api.common.io.FileOutputFormat;
import org.apache.flink.api.java.DataSet;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.enquery.encryptedquery.core.FieldType;
import org.enquery.encryptedquery.data.DataSchema;
import org.enquery.encryptedquery.data.Query;
import org.enquery.encryptedquery.data.QueryInfo;
import org.enquery.encryptedquery.data.QuerySchema;
import org.enquery.encryptedquery.encryption.CipherText;
import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.enquery.encryptedquery.encryption.CryptoSchemeFactory;
import org.enquery.encryptedquery.encryption.CryptoSchemeRegistry;
import org.enquery.encryptedquery.flink.batch.ColumnReduceFunctionV2;
import org.enquery.encryptedquery.flink.batch.Dispatcher;
import org.enquery.encryptedquery.flink.batch.FlinkBatchRecordFilter;
import org.enquery.encryptedquery.flink.batch.ResponseFileOutputFormat;
import org.enquery.encryptedquery.flink.streaming.ColumnBufferMap;
import org.enquery.encryptedquery.flink.streaming.IncrementalResponseSink;
import org.enquery.encryptedquery.flink.streaming.InputRecord;
import org.enquery.encryptedquery.flink.streaming.InputRecordToQueueRecordMap;
import org.enquery.encryptedquery.flink.streaming.StreamingColumnEncryption;
import org.enquery.encryptedquery.responder.ResponderProperties;
import org.enquery.encryptedquery.xml.transformation.QueryTypeConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BaseQueryExecutor implements FlinkTypes, AutoCloseable {

	private static final Logger log = LoggerFactory.getLogger(BaseQueryExecutor.class);

	private final int DEFAULT_COLUMN_BUFFER_MEMORY_MB = 100;
	// estimated overhead of a byte[] object
	private final int DATA_CHUNK_OVERHEAD = 16;

	protected QuerySchema querySchema;
	protected DataSchema dataSchema;
	protected Query query;
	protected int bufferSize = 0;
	protected Integer maxHitsPerSelector;
	// protected int flinkParallelism;
	// protected int columnEncryptionPartitionCount;
	protected FieldType selectorFieldType;
	protected Map<String, String> config;
	protected Path outputFileName;
	protected Path inputFileName;
	protected Map<String, FieldType> fieldTypeMap;
	protected String selectorFieldName;
	protected QueryTypeConverter queryTypeConverter;
	protected int columnBufferMemory;
	protected boolean initialized;
	protected long windowSizeInSeconds;
	protected String jobName;
	protected Long maxTimestamp;
	private CryptoScheme crypto;
	private Integer maxRecordCountPerWindow;

	public Map<String, String> getConfig() {
		return config;
	}

	public void setConfig(Map<String, String> config) {
		this.config = config;
	}

	public Path getOutputFileName() {
		return outputFileName;
	}

	public void setOutputFileName(Path outputFileName) {
		this.outputFileName = outputFileName;
	}

	public Path getInputFileName() {
		return inputFileName;
	}

	public void setInputFileName(Path inputFileName) {
		this.inputFileName = inputFileName;
	}

	public Long getMaxTimestamp() {
		return maxTimestamp;
	}

	public void run(StreamExecutionEnvironment env, DataStream<InputRecord> source) throws Exception {

		initializeCommon();
		initializeStreaming();

		final QueryInfo queryInfo = query.getQueryInfo();
		final InputRecordToQueueRecordMap createQueueRecords = new InputRecordToQueueRecordMap(queryInfo);
		final String outFileName = outputFileName.toString();
		final ColumnBufferMap columnBufferMap = new ColumnBufferMap(bufferSize, queryInfo.getHashBitSize(), maxHitsPerSelector, outFileName, maxRecordCountPerWindow);
		final StreamingColumnEncryption columnEncryption = new StreamingColumnEncryption(query, config);
		final IncrementalResponseSink sinkFunction = new IncrementalResponseSink(queryInfo, config, outFileName);

		source.map(createQueueRecords).name("Create data chunks")
				.flatMap(columnBufferMap).setParallelism(1).name("Transform records to columns")
				.map(columnEncryption).name("Encrypt columns")
				.addSink(sinkFunction).setParallelism(1).name("Incrementally store results");

		env.execute(jobName);
	}

	public void run(ExecutionEnvironment env, DataSet<Map<String, Object>> source) throws Exception {

		initializeCommon();
		initializeBatch();

		final QueryInfo queryInfo = query.getQueryInfo();

		final RowHashMapFunction rowHash = new RowHashMapFunction(queryInfo);
		final Dispatcher dispatcher = new Dispatcher(bufferSize, query.getQueryInfo().getHashBitSize());
		final ColumnReduceFunctionV2 columnProcessor = new ColumnReduceFunctionV2(query, config);
		final FileOutputFormat<Tuple2<Integer, CipherText>> outputFormat = new ResponseFileOutputFormat(queryInfo);

		// final String sfn = selectorFieldName;
		// final String filter = query.getQueryInfo().getFilterExpression();
		// FlinkStreamingRecordFilter rf = null;
		// if (filter != null) {
		// rf = new FlinkStreamingRecordFilter(filter);
		// }

		source.filter(new FlinkBatchRecordFilter(query))
				// shuffle input records for better parallelism
				.rebalance()
				// .setParallelism(flinkParallelism)
				.map(rowHash).name("Create Data Chunks")
				.reduceGroup(dispatcher).name("Dispatcher")
				.setParallelism(1)
				.groupBy(0)
				.reduceGroup(columnProcessor).name("Process Columns")
				// .setParallelism(flinkParallelism)
				.write(outputFormat, outputFileName.toString()).name("Write Response")
				.setParallelism(1);

		// execute program
		env.execute(jobName);
	}

	public void initializeCommon() throws Exception {
		if (initialized) return;
		Validate.notNull(config);

		log.info("-Common Configuration Parameters:");
		for (Map.Entry<String, String> entry : config.entrySet()) {
			log.info("---{} = {}", entry.getKey(), entry.getValue());
		}
		log.info("-End of Configuration Parameters");

		initializeCryptoScheme();

		Validate.isTrue(!Files.exists(outputFileName), "Output file %s exists. Delete first.", outputFileName);

		query = loadQuery(inputFileName);
		Validate.notNull(query);
		Validate.notNull(query.getQueryInfo());
		log.info("Loaded query with filter expression: {}", query.getQueryInfo().getFilterExpression());

		QueryInfo queryInfo = query.getQueryInfo();
		querySchema = queryInfo.getQuerySchema();
		Validate.notNull(querySchema);
		querySchema.validate();

		dataSchema = querySchema.getDataSchema();
		Validate.notNull(dataSchema);
		dataSchema.validate();

		if (config.containsKey(FlinkConfigurationProperties.MAX_HITS_PER_SELECTOR)) {
			maxHitsPerSelector = Integer.valueOf(config.get(FlinkConfigurationProperties.MAX_HITS_PER_SELECTOR));
			Validate.isTrue(maxHitsPerSelector > 0, "maxHitsPerSelector must be > 0");
		}

		if (bufferSize <= 0) {
			// set bufferSize (i.e. number of columns) based on hashBitSize, dataChunkSize,
			// and the amount of memory allowed
			int bufferMemoryMb = Integer.parseInt(config.getOrDefault(ResponderProperties.COLUMN_BUFFER_MEMORY_MB,
					Integer.valueOf(DEFAULT_COLUMN_BUFFER_MEMORY_MB).toString()));
			bufferSize = (int) ((double) bufferMemoryMb * (1 << 20) / (1 << queryInfo.getHashBitSize()) / (DATA_CHUNK_OVERHEAD + queryInfo.getDataChunkSize()));
			bufferSize = Math.max(bufferSize, 1);
		}

		selectorFieldName = queryInfo.getQuerySchema().getSelectorField();
		fieldTypeMap = new HashMap<>();
		dataSchema.elements().stream()
				.forEach(e -> fieldTypeMap.put(e.getName(), e.getDataType()));

		querySchema.getElementList().stream()
				.forEach(e -> fieldTypeMap.put(e.getName(), dataSchema.elementByName(e.getName()).getDataType()));

		// log.info(
		// MessageFormat
		// .format("Loaded query params HashBitSize: ''{0}'', Selector: ''{1}''.",
		// queryInfo.getHashBitSize(),
		// selectorFieldName));
		log.info("  Loaded Query Params: HashBitSize: {}, Selector: {}", queryInfo.getHashBitSize(), selectorFieldName);
		log.info("  Column Buffer Size = {}", bufferSize);

		jobName = "Encrypted Query -> " + outputFileName.getFileName().toString();
		initialized = true;
	}

	/**
	 * 
	 */
	public void initializeStreaming() throws IOException {
		log.info("Flink Streaming Initialization");
		windowSizeInSeconds = Integer.valueOf(config.getOrDefault(FlinkConfigurationProperties.WINDOW_LENGTH_IN_SECONDS, "60"));
		Validate.isTrue(windowSizeInSeconds > 0, "'%s' must be > 0", FlinkConfigurationProperties.WINDOW_LENGTH_IN_SECONDS);

		maxTimestamp = null;
		Long runtimeSeconds = null;
		String runtimeStr = config.get(FlinkConfigurationProperties.STREAM_RUNTIME_SECONDS);
		if (runtimeStr != null) {
			runtimeSeconds = Long.valueOf(runtimeStr);
		}
		Validate.isTrue(runtimeSeconds == null || runtimeSeconds > 0, "'%s' must be null or > 0", FlinkConfigurationProperties.STREAM_RUNTIME_SECONDS);

		if (runtimeSeconds != null) {
			maxTimestamp = System.currentTimeMillis() + (runtimeSeconds * 1000);
		}

		log.info("   Window Size: {}", windowSizeInSeconds);
		log.info("   Max Run Time: {} / Max Time Stamp {}", runtimeSeconds, maxTimestamp);
		log.info("   Parent Folder: {}", outputFileName);

		// in streaming mode, create the parent directory
		Files.createDirectories(outputFileName);
	}

	/**
	 * 
	 */
	protected void initializeBatch() {
		// columnEncryptionPartitionCount =
		// Integer.valueOf(config.getOrDefault(FlinkConfigurationProperties.COLUMN_ENCRYPTION_PARTITION_COUNT,
		// "1"));
		// Validate.isTrue(columnEncryptionPartitionCount > 0, "columnEncryptionPartitionCount must
		// be > 0");
	}


	/**
	 * @param config2
	 * @throws Exception
	 */
	private void initializeCryptoScheme() throws Exception {

		crypto = CryptoSchemeFactory.make(config);

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

	protected Query loadQuery(Path file) throws IOException, FileNotFoundException, JAXBException {
		try (FileInputStream fis = new FileInputStream(file.toFile())) {
			org.enquery.encryptedquery.xml.schema.Query xml = queryTypeConverter.unmarshal(fis);
			return queryTypeConverter.toCoreQuery(xml);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.AutoCloseable#close()
	 */
	@Override
	public void close() throws Exception {
		if (crypto != null) {
			crypto.close();
			crypto = null;
		}
	}

}
