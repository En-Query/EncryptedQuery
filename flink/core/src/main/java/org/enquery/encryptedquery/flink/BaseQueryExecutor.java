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
import java.math.BigInteger;
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
import org.apache.flink.types.Row;
import org.enquery.encryptedquery.data.DataSchema;
import org.enquery.encryptedquery.data.DataSchemaElement;
import org.enquery.encryptedquery.data.QuerySchema;
import org.enquery.encryptedquery.query.wideskies.Query;
import org.enquery.encryptedquery.query.wideskies.QueryInfo;
import org.enquery.encryptedquery.xml.transformation.QueryTypeConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseQueryExecutor implements FlinkTypes {

	private final Logger log = LoggerFactory.getLogger(BaseQueryExecutor.class);

	private QuerySchema querySchema;
	private DataSchema dataSchema;
	private Query query;
	private int hashBitSize;
	private int dataChunkSize;
	private String key;
	private boolean embedSelector;
	private int maxHitsPerSelector;
	private int columnEncryptionPartitionCount;
	private Integer selectorFieldIndex;
	private String selectorFieldType;
	private RowTypeInfo rowTypeInfo;
	private Map<String, String> config;
	private Path outputFileName;
	private Path inputFileName;
	private Map<String, String> fieldTypeMap;
	private String selectorFieldName;

	private QueryTypeConverter queryTypeConverter = new QueryTypeConverter();

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

	abstract protected DataSet<Row> initSource(ExecutionEnvironment env);

	public void run() throws Exception {

		final ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();

		loadAndValidateParams();

		final RowHashMapFunction rowHash = new RowHashMapFunction(selectorFieldIndex, selectorFieldType, getRowTypeInfo(), embedSelector, querySchema, key, hashBitSize, dataChunkSize);
		final ColumnReduceFunction columnReducer = new ColumnReduceFunction(query.getNSquared());
		final FileOutputFormat<Tuple2<Integer, BigInteger>> outputFormat = new ResponseFileOutputFormat(query.getQueryInfo());
		final DataPartitionsReduceFunction partionReducer = new DataPartitionsReduceFunction(query.getQueryElements(), query.getNSquared(), config, hashBitSize);

		// ((1 << hashBitSize) + columnEncryptionPartitionCount - 1) /
		// columnEncryptionPartitionCount)

		initSource(env)
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
		env.execute("Encrypted Query");
	}

	protected void loadAndValidateParams() throws Exception {
		Validate.notNull(config);

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

		hashBitSize = queryInfo.getHashBitSize();
		dataChunkSize = queryInfo.getDataChunkSize();

		// pass these params to the ComputeEncryptedColumn
		config.put(FlinkConfigurationProperties.HASH_BIT_SIZE, Integer.toString(hashBitSize));

		if (config.containsKey(FlinkConfigurationProperties.MAX_HITS_PER_SELECTOR)) {
			maxHitsPerSelector = Integer.valueOf(config.get(FlinkConfigurationProperties.MAX_HITS_PER_SELECTOR));
		} else {
			log.warn(FlinkConfigurationProperties.MAX_HITS_PER_SELECTOR + " configuration parameter is missing, defaulting to 1000");
			maxHitsPerSelector = 1000; // Default value if not found.
		}

		Validate.isTrue(maxHitsPerSelector > 0, "maxHitsPerSelector must be > 0");

		if (config.containsKey(FlinkConfigurationProperties.COLUMN_ENCRYPTION_PARTITION_COUNT)) {
			columnEncryptionPartitionCount = Integer.valueOf(config.get(FlinkConfigurationProperties.COLUMN_ENCRYPTION_PARTITION_COUNT));
		} else {
			log.warn("Setting '{}' is missing, defaulting to 1.", FlinkConfigurationProperties.COLUMN_ENCRYPTION_PARTITION_COUNT);
			columnEncryptionPartitionCount = 1;
		}

		Validate.isTrue(columnEncryptionPartitionCount > 0, "columnEncryptionPartitionCount must be > 0");

		key = queryInfo.getHashKey();
		embedSelector = queryInfo.getEmbedSelector();
		selectorFieldName = queryInfo.getQuerySchema().getSelectorField();

		fieldTypeMap = new HashMap<>();

		dataSchema.elements().stream()
				.forEach(e -> fieldTypeMap.put(e.getName(), e.getDataType()));

		querySchema.getElementList().stream()
				.forEach(e -> fieldTypeMap.put(e.getName(), dataSchema.elementByName(e.getName()).getDataType()));

		rowTypeInfo = makeRowTypeInfo();

		log.info(
				MessageFormat
						.format("Loaded query params bitSize: ''{0}'',  Selector: ''{1}'', Embed Selector: ''{2}''.",
								hashBitSize, selectorFieldName, embedSelector));

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
		final int numberOfElements = dataSchema.elementCount();
		final TypeInformation<?>[] types = new TypeInformation<?>[numberOfElements];
		final String[] fieldNames = new String[numberOfElements];

		for (int i = 0; i < numberOfElements; ++i) {
			DataSchemaElement element = dataSchema.elementByPosition(i);
			fieldNames[i] = element.getName();
			String type = element.getDataType();
			types[i] = pirTypeToFlinkType(type);

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

		return new RowTypeInfo(types, fieldNames);
	}

}
