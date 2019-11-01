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
package org.enquery.encryptedquery.hadoop.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.enquery.encryptedquery.core.Partitioner;
import org.enquery.encryptedquery.data.QueryInfo;
import org.enquery.encryptedquery.data.RecordEncoding;
import org.enquery.encryptedquery.filter.RecordFilter;
import org.enquery.encryptedquery.json.JSONStringConverter;
import org.enquery.encryptedquery.utils.KeyedHash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This mapper breaks each input data element into chunks and computes its row hash. It emits
 * key-value pairs {@code (row hash, data chunks)}
 */
public class ProcessDataMapper extends Mapper<LongWritable, Text, IntWritable, RowHashAndChunksWritable> {

	private static final Logger log = LoggerFactory.getLogger(ProcessDataMapper.class);

	private IntWritable keyOut;
	private RowHashAndChunksWritable valueOut;
	private QueryInfo queryInfo;
	private Partitioner partitioner;
	private JSONStringConverter jsonConverter;
	private RecordEncoding recordEncoding;
	private RecordFilter recordFilter;

	@Override
	public void setup(Context ctx) throws IOException, InterruptedException {
		super.setup(ctx);
		try {
			log.info("ProcessDataMapper - Setup Running");
			DistCacheLoader loader = new DistCacheLoader();
			keyOut = new IntWritable();
			valueOut = new RowHashAndChunksWritable();
			partitioner = new Partitioner();
			queryInfo = loader.loadQueryInfo();

			// override Querier chunk size if needed
			int overrideChunkSize = ctx.getConfiguration().getInt(HadoopConfigurationProperties.CHUNK_SIZE, 0);
			if (overrideChunkSize > 0 && overrideChunkSize < queryInfo.getDataChunkSize()) {
				queryInfo.setDataChunkSize(overrideChunkSize);
			}

			jsonConverter = new JSONStringConverter(queryInfo.getQuerySchema().getDataSchema());
			recordEncoding = new RecordEncoding(queryInfo);
			String filterExpr = queryInfo.getFilterExpression();
			if (filterExpr != null) {
				recordFilter = new RecordFilter(filterExpr, queryInfo.getQuerySchema().getDataSchema());
				log.info("Initialized using filter expression: '{}'", filterExpr);
			} else {
				recordFilter = null;
			}

			log.info("Query Identifer: {}", queryInfo.getIdentifier());
		} catch (Exception e) {
			throw new IOException("Error initializing mapper.", e);
		}
	}

	/**
	 * The key is the docID/line number and the value is the doc
	 */
	@Override
	public void map(LongWritable key, Text value, Context ctx) throws IOException, InterruptedException {

		// ctx.getCounter(HadoopConfigurationProperties.MRStats.NUM_RECORDS_INIT_MAPPER).increment(1);

		if (log.isDebugEnabled()) {
			log.debug("Input Line: {}", value.toString());
		}

		try {
			Map<String, Object> recordData = jsonConverter.toStringObjectFlatMap(value.toString());
			if (recordData == null) return;

			// filter record if needed
			if (recordFilter != null && !recordFilter.satisfiesFilter(recordData)) return;

			final String selectorValue = recordEncoding.getSelectorStringValue(recordData);
			if (StringUtils.isEmpty(selectorValue)) {
				if (log.isWarnEnabled()) {
					log.warn("No Value for Selector field {} / value ({})", queryInfo.getQuerySchema().getSelectorField(), selectorValue);
				}
				return;
			}

			Integer rowHash = KeyedHash.hash(queryInfo.getHashKey(), queryInfo.getHashBitSize(), selectorValue);
			ByteBuffer encoded = recordEncoding.encode(recordData);
			List<byte[]> chunks = partitioner.createPartitions(encoded, queryInfo.getDataChunkSize());

			RowHashAndChunks data = new RowHashAndChunks();
			data.setRowHash(rowHash);
			data.setChunks(chunks);

			// set key to fix value (0), the row hash is in the data to minimize the
			// number of calls to the reducer to one, since there is only one reducer for this map
			keyOut.set(0);
			valueOut.set(data);
			ctx.write(keyOut, valueOut);
			// ctx.getCounter(HadoopConfigurationProperties.MRStats.NUM_RECORDS_PROCESSED_INIT_MAPPER).increment(1);
		} catch (Exception e) {
			// There may be some records we cannot process, swallowing exception here to
			// continue processing
			// rest of records.
			log.error("Error in partitioning data element value {} ", value.toString());
			e.printStackTrace();
			return;
		}
	}
}
