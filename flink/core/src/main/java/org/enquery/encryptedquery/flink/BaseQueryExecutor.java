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
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;

import javax.xml.bind.JAXBException;

import org.apache.commons.lang3.Validate;
import org.apache.flink.api.common.io.FileOutputFormat;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.DataSet;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.typeutils.RowTypeInfo;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.types.Row;
import org.enquery.encryptedquery.data.DataSchema;
import org.enquery.encryptedquery.data.DataSchemaElement;
import org.enquery.encryptedquery.data.Query;
import org.enquery.encryptedquery.data.QueryInfo;
import org.enquery.encryptedquery.data.QuerySchema;
import org.enquery.encryptedquery.encryption.CipherText;
import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.enquery.encryptedquery.encryption.CryptoSchemeFactory;
import org.enquery.encryptedquery.encryption.CryptoSchemeRegistry;
import org.enquery.encryptedquery.flink.batch.BucketKeySelector;
import org.enquery.encryptedquery.flink.batch.ColumnReduceFunction;
import org.enquery.encryptedquery.flink.batch.DataPartitionsReduceFunction;
import org.enquery.encryptedquery.flink.batch.ResponseFileOutputFormat;
import org.enquery.encryptedquery.flink.streaming.AllColumnsPerWindowReducer;
import org.enquery.encryptedquery.flink.streaming.ExecutionTimeTrigger;
import org.enquery.encryptedquery.flink.streaming.StreamingColumnProcessor;
import org.enquery.encryptedquery.flink.streaming.StreamingColumnReducer;
import org.enquery.encryptedquery.flink.streaming.WindowResultSink;
import org.enquery.encryptedquery.xml.transformation.QueryTypeConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BaseQueryExecutor implements FlinkTypes {

	private static final Logger log = LoggerFactory.getLogger(BaseQueryExecutor.class);

	protected QuerySchema querySchema;
	protected DataSchema dataSchema;
	protected Query query;
	protected int maxHitsPerSelector;
	protected int columnEncryptionPartitionCount;
	protected Integer selectorFieldIndex;
	protected String selectorFieldType;
	protected RowTypeInfo rowTypeInfo;
	protected Map<String, String> config;
	protected Path outputFileName;
	protected Path inputFileName;
	protected Map<String, String> fieldTypeMap;
	protected String selectorFieldName;
	protected QueryTypeConverter queryTypeConverter;
	protected long computeThreshold;
	protected boolean initialized;
	protected long windowSizeInSeconds;
	protected Long runtimeSeconds;
	protected String jobName;

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

	public RowTypeInfo getRowTypeInfo() {
		return rowTypeInfo;
	}

	public void run(StreamExecutionEnvironment env, DataStream<Row> source) throws Exception {

		Long maxTimestamp = null;
		if (this.runtimeSeconds != null) {
			maxTimestamp = System.currentTimeMillis() + (this.runtimeSeconds * 1000);
		}

		initializeCommon();
		initializeStreaming();

		final QueryInfo queryInfo = query.getQueryInfo();
		final RowHashMapFunction rowHash = new RowHashMapFunction(selectorFieldIndex, selectorFieldType, getRowTypeInfo(), queryInfo);
		final StreamingColumnProcessor columnProcessor = new StreamingColumnProcessor(query, computeThreshold, config);
		final StreamingColumnReducer columnReducer = new StreamingColumnReducer(queryInfo.getPublicKey(), config);
		final AllColumnsPerWindowReducer allColumnsPerWidow = new AllColumnsPerWindowReducer();
		final WindowResultSink sinkFunction = new WindowResultSink(queryInfo, config, outputFileName.toString());
		final ExecutionTimeTrigger trigger = new ExecutionTimeTrigger(maxTimestamp, outputFileName.toString(), maxHitsPerSelector);
		final Time windowSize = Time.seconds(windowSizeInSeconds);

		source
				.map(rowHash)
				.keyBy("rowIndex")
				.timeWindow(windowSize)
				.trigger(trigger)
				.process(columnProcessor)
				.keyBy(r -> r.col)
				.timeWindow(windowSize)
				.process(columnReducer)
				.timeWindowAll(windowSize)
				.process(allColumnsPerWidow)
				.addSink(sinkFunction);

		env.execute(jobName);
	}

	public void run(ExecutionEnvironment env, DataSet<Row> source) throws Exception {

		initializeCommon();
		initializeBatch();

		final QueryInfo queryInfo = query.getQueryInfo();
		final RowHashMapFunction rowHash = new RowHashMapFunction(selectorFieldIndex, selectorFieldType, getRowTypeInfo(), queryInfo);
		final ColumnReduceFunction columnReducer = new ColumnReduceFunction(queryInfo.getPublicKey(), config);
		final FileOutputFormat<Tuple2<Integer, CipherText>> outputFormat = new ResponseFileOutputFormat(queryInfo, config);
		final DataPartitionsReduceFunction partionReducer = new DataPartitionsReduceFunction(query, computeThreshold, config);

		// initSource(env)
		source
				// shuffle input records for better parallelism
				.rebalance()
				// create QueueRecords
				.map(rowHash)
				// remove excess rows per rowIndex
				.groupBy("rowIndex")
				.first(maxHitsPerSelector)
				// partition remaining rows in roughly equal buckets
				.groupBy(new BucketKeySelector(columnEncryptionPartitionCount))
				.reduceGroup(partionReducer)
				.groupBy(0)
				.reduceGroup(columnReducer)
				.write(outputFormat, outputFileName.toString())
				.setParallelism(1);

		// execute program
		env.execute(jobName);
	}

	public void initializeCommon() throws Exception {
		if (initialized) return;
		Validate.notNull(config);

		initializeCryptoScheme();

		query = loadQuery(inputFileName);
		Validate.notNull(query);
		Validate.notNull(query.getQueryInfo());

		QueryInfo queryInfo = query.getQueryInfo();
		querySchema = queryInfo.getQuerySchema();
		Validate.notNull(querySchema);
		querySchema.validate();

		dataSchema = querySchema.getDataSchema();
		Validate.notNull(dataSchema);
		dataSchema.validate();

		maxHitsPerSelector = Integer.valueOf(config.getOrDefault(FlinkConfigurationProperties.MAX_HITS_PER_SELECTOR, "1000"));
		Validate.isTrue(maxHitsPerSelector > 0, "maxHitsPerSelector must be > 0");

		computeThreshold = Long.parseLong(config.getOrDefault(FlinkConfigurationProperties.COMPUTE_THRESHOLD, "30000"));
		Validate.isTrue(computeThreshold > 0, "%s must be > 0", FlinkConfigurationProperties.COMPUTE_THRESHOLD);

		selectorFieldName = queryInfo.getQuerySchema().getSelectorField();
		fieldTypeMap = new HashMap<>();
		dataSchema.elements().stream()
				.forEach(e -> fieldTypeMap.put(e.getName(), e.getDataType()));

		querySchema.getElementList().stream()
				.forEach(e -> fieldTypeMap.put(e.getName(), dataSchema.elementByName(e.getName()).getDataType()));

		rowTypeInfo = makeRowTypeInfo();

		log.info(
				MessageFormat
						.format("Loaded query params HashBitSize: ''{0}'',  Selector: ''{1}'', Embed Selector: ''{2}''.",
								queryInfo.getHashBitSize(),
								selectorFieldName,
								queryInfo.getEmbedSelector()));

		jobName = "Encrypted Query -> " + outputFileName.getFileName().toString();
		initialized = true;
	}

	/**
	 * 
	 */
	public void initializeStreaming() {
		windowSizeInSeconds = Integer.valueOf(config.getOrDefault(FlinkConfigurationProperties.WINDOW_LENGTH_IN_SECONDS, "60"));
		Validate.isTrue(windowSizeInSeconds > 0, "'%s' must be > 0", FlinkConfigurationProperties.WINDOW_LENGTH_IN_SECONDS);

		runtimeSeconds = null;
		String runtimeStr = config.get(FlinkConfigurationProperties.STREAM_RUNTIME_SECONDS);
		if (runtimeStr != null) {
			runtimeSeconds = Long.valueOf(runtimeStr);
		}
		Validate.isTrue(runtimeSeconds == null || runtimeSeconds > 0, "'%s' must be null or > 0", FlinkConfigurationProperties.STREAM_RUNTIME_SECONDS);
	}

	/**
	 * 
	 */
	protected void initializeBatch() {
		columnEncryptionPartitionCount = Integer.valueOf(config.getOrDefault(FlinkConfigurationProperties.COLUMN_ENCRYPTION_PARTITION_COUNT, "1"));
		Validate.isTrue(columnEncryptionPartitionCount > 0, "columnEncryptionPartitionCount must be > 0");
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

	protected Query loadQuery(Path file) throws IOException, FileNotFoundException, JAXBException {
		try (FileInputStream fis = new FileInputStream(file.toFile())) {
			org.enquery.encryptedquery.xml.schema.Query xml = queryTypeConverter.unmarshal(fis);
			return queryTypeConverter.toCoreQuery(xml);
		}
	}

	/**
	 * This the Flink Schema associated to the SQL Query, not the query.
	 * 
	 * @return
	 */
	protected RowTypeInfo makeRowTypeInfo() {
		final boolean debugging = log.isDebugEnabled();

		if (debugging) log.debug("Making row type info.");

		final int numberOfElements = dataSchema.elementCount();
		final TypeInformation<?>[] types = new TypeInformation<?>[numberOfElements];
		final String[] fieldNames = new String[numberOfElements];

		for (int i = 0; i < numberOfElements; ++i) {
			DataSchemaElement element = dataSchema.elementByPosition(i);
			fieldNames[i] = element.getName();
			String type = element.getDataType();
			types[i] = pirTypeToFlinkType(type);

			if (debugging) log.debug("Field index: {}, name: {}, type: {}", i, fieldNames[i], types[i]);

			if (Objects.equals(element.getName(), selectorFieldName)) {
				selectorFieldIndex = i;
				selectorFieldType = type;
			}
		}

		if (selectorFieldIndex == null) {
			throw new RuntimeException(
					MessageFormat.format("Selector field ''{0}'' not found in data schema fields ''{1}''.",
							selectorFieldName,
							new HashSet<>(dataSchema.elementNames())));
		}

		if (debugging) log.debug("Selector field index {}, type: {}", selectorFieldIndex, selectorFieldType);
		return new RowTypeInfo(types, fieldNames);
	}

}
