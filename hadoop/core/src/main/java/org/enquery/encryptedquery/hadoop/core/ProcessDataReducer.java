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

import org.apache.commons.lang3.Validate;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.mapreduce.Reducer;
import org.enquery.encryptedquery.data.Query;
import org.enquery.encryptedquery.data.QueryInfo;
import org.enquery.encryptedquery.hadoop.core.Buffer.Column;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reducer class for the ProcessData job.<br/>
 * There must be only one instance of this reducer.<br/>
 * <br/>
 * 
 * Input is: (row hash, data chunks) <br/>
 * Output is: (column number, column)
 * <p>
 */
public class ProcessDataReducer extends Reducer<IntWritable, RowHashAndChunksWritable, IntWritable, ColumnWritable> {
	private static final Logger log = LoggerFactory.getLogger(ProcessDataReducer.class);

	// estimated overhead of a byte[] object
	private final int DATA_CHUNK_OVERHEAD = 16;

	private IntWritable outputKey;
	private ColumnWritable outputValue;

	private Buffer buffer;

	@Override
	public void setup(Context ctx) throws IOException, InterruptedException {
		super.setup(ctx);

		try {
			DistCacheLoader loader = new DistCacheLoader();
			outputKey = new IntWritable();
			outputValue = new ColumnWritable();

			Query query = loader.loadQuery();
			final QueryInfo queryInfo = query.getQueryInfo();

			// override Querier chunk size if needed
			int overrideChunkSize = ctx.getConfiguration().getInt(HadoopConfigurationProperties.CHUNK_SIZE, 0);
			if (overrideChunkSize > 0 && overrideChunkSize < queryInfo.getDataChunkSize()) {
				queryInfo.setDataChunkSize(overrideChunkSize);
			}

			log.info("Initialized for Query: {}", query.getQueryInfo().toString());

			int bufferSize = calcBufferSize(ctx, queryInfo);
			int hashBitSize = queryInfo.getHashBitSize();
			buffer = new Buffer(bufferSize, hashBitSize);

		} catch (Exception e) {
			throw new RuntimeException("Exception initializing ColumnReducer.", e);
		}
	}

	/**
	 * Calculate the buffer size (i.e. number of columns) based on hashBitSize, dataChunkSize, and
	 * the amount of memory allowed
	 * 
	 * @return
	 */
	private int calcBufferSize(Context ctx, QueryInfo queryInfo) {
		int bufferMemoryMb = ctx.getConfiguration().getInt(HadoopConfigurationProperties.COLUMN_BUFFER_MEMORY_MB, -1);
		if (bufferMemoryMb < 0) return 10;

		int result = (int) ((double) bufferMemoryMb * (1 << 20) / (1 << queryInfo.getHashBitSize()) / (DATA_CHUNK_OVERHEAD + queryInfo.getDataChunkSize()));
		result = Math.max(result, 1);
		log.info("Calculated column buffer size = {}", result);
		return result;
	}

	@Override
	public void reduce(IntWritable dummy, Iterable<RowHashAndChunksWritable> dataElements, Context ctx) throws IOException, InterruptedException {
		for (RowHashAndChunksWritable entry : dataElements) {
			processRecord(entry.get(), ctx);
		}
		flushRemaining(ctx);
	}

	private void processRecord(RowHashAndChunks rowHashAndChunks, Context ctx) throws InterruptedException, IOException {
		int rowHash = rowHashAndChunks.getRowHash();
		for (byte[] chunk : rowHashAndChunks.getChunks()) {
			if (!buffer.spaceAvailable(rowHash)) {
				flushOne(ctx);
			}
			buffer.add(rowHash, chunk);
		}
	}

	/**
	 * @param ctx
	 * @throws InterruptedException
	 * @throws IOException
	 */
	private void flushOne(Context ctx) throws IOException, InterruptedException {
		Column column = buffer.peek();
		Validate.notNull(column);

		outputKey.set((int) column.getColumnNumber());
		outputValue.set(column);

		ctx.write(outputKey, outputValue);
		buffer.pop();
	}

	/**
	 * @param ctx
	 * @throws InterruptedException
	 * @throws IOException
	 */
	private void flushRemaining(Context ctx) throws IOException, InterruptedException {
		Column column;
		while ((column = buffer.peek()) != null) {
			outputKey.set((int) column.getColumnNumber());
			outputValue.set(column);

			ctx.write(outputKey, outputValue);
			buffer.pop();
		}
	}
}
