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

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.mapreduce.Reducer;
import org.enquery.encryptedquery.responder.ResponderProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reducer class for the SortDataIntoRows job
 *
 * <p>
 * Each call to {@code reducer()} receives the data parts corresponding to the data elements
 * belonging to a single row. The stream of parts are grouped and re-emited in fixed-size chunks,
 * which are assigned successively increasing column numbers. The reducer emits key-value pairs
 * {@code ((row,col), chunk)}.
 */
public class SortDataIntoRowsReducer_v1 extends Reducer<IntWritable, BytesWritable, IntPairWritable, BytesWritable> {
	private static final Logger log = LoggerFactory.getLogger(SortDataIntoRowsReducer_v1.class);

	private int bytesPerPart;
	private int chunkingByteSize;
	private int partsPerChunk;
	private int bytesPerChunk;
	private byte[] buffer;
	private int partsInBuffer;

	private int currentRowIndex = -1;
	private long rowIndexCounter;
	private IntWritable _rowW;
	private IntWritable _colW;
	private IntPairWritable outputKey;
	private BytesWritable outputValue;
	private int maxHitsPerSelector = -1;

	@Override
	public void setup(Context ctx) throws IOException, InterruptedException {
		super.setup(ctx);
		try {
			final Configuration cfg = ctx.getConfiguration();
			DistCacheLoader loader = new DistCacheLoader();

			bytesPerPart = loader.loadQueryInfo().getDataChunkSize();
			chunkingByteSize = Integer.valueOf(cfg.get(HadoopConfigurationProperties.CHUNKING_BYTE_SIZE));
			partsPerChunk = Math.max(chunkingByteSize / bytesPerPart, 1);
			bytesPerChunk = partsPerChunk * bytesPerPart;
			buffer = new byte[bytesPerChunk];
			partsInBuffer = 0;

			_rowW = new IntWritable();
			_colW = new IntWritable();
			outputKey = new IntPairWritable(_rowW, _colW);
			outputValue = new BytesWritable();

			maxHitsPerSelector = cfg.getInt(ResponderProperties.MAX_HITS_PER_SELECTOR, -1);

		} catch (Exception e) {
			throw new IOException("Exception initializing SortDataIntoRowsReducer_v1.", e);
		}
	}

	@Override
	public void reduce(IntWritable rowIndexW, Iterable<BytesWritable> dataElements, Context ctx)
			throws IOException, InterruptedException {
		int hitCount = 0;
		int rowIndex = rowIndexW.get();
		int chunkCol = 0;

		ctx.getCounter(HadoopConfigurationProperties.MRStats.NUM_HASHS_INIT_REDUCER).increment(1);
		log.info("Reducing rowIndex {}", rowIndex);
		outputKey.getFirst().set(rowIndex);
		for (BytesWritable dataElement : dataElements) {
			if (maxHitsPerSelector > 0 && hitCount >= maxHitsPerSelector) {
				if (log.isDebugEnabled()) {
					log.debug("maxHitsPerSelector limit ({}) reached for rowIndex = {}", maxHitsPerSelector, rowIndex);
				}
				ctx.getCounter(HadoopConfigurationProperties.MRStats.NUM_HASHS_OVER_MAX_HITS).increment(1);
				break;
			}

			/*
			 * Extract data element bytes. We assume this has already been padded to be a multiple
			 * of bytesPerPart bytes
			 */
			byte[] dataElementBytes = dataElement.copyBytes();
			int current = 0;
			int partsRemaining = dataElementBytes.length / bytesPerPart;

			while (partsRemaining > 0) {
				/* If copy data as much additional data into buffer as possible */
				int partsToCopy = partsPerChunk - partsInBuffer;
				if (partsToCopy > partsRemaining) {
					partsToCopy = partsRemaining;
				}
				System.arraycopy(dataElementBytes, current * bytesPerPart, buffer, partsInBuffer * bytesPerPart,
						partsToCopy * bytesPerPart);
				partsInBuffer += partsToCopy;
				current += partsToCopy;
				partsRemaining -= partsToCopy;

				/* if buffer is full, write it out */
				if (partsInBuffer == partsPerChunk) {
					outputKey.getSecond().set(chunkCol);
					outputValue.set(buffer, 0, bytesPerChunk);
					ctx.write(outputKey, outputValue);
					chunkCol += 1;
					partsInBuffer = 0;
				}
			}

			hitCount += 1;

		}
		if (log.isDebugEnabled()) {
			log.debug("rowIndex {} hitCount {}", rowIndex, hitCount);
		}
		if (currentRowIndex != rowIndex) {
			if (currentRowIndex != -1) {
				if (log.isDebugEnabled()) {
					log.debug("RowIndex {} counter {}", rowIndex, rowIndexCounter);
				}
			}
			currentRowIndex = rowIndex;
			rowIndexCounter = 1;
		} else {
			if (currentRowIndex == rowIndex) {
				rowIndexCounter++;
			}
		}
		/* write out any remaining data */
		if (partsInBuffer > 0) {
			outputKey.getSecond().set(chunkCol);
			outputValue.set(buffer, 0, partsInBuffer * bytesPerPart);
			ctx.write(outputKey, outputValue);

			chunkCol += 1;
			partsInBuffer = 0;

		}
	}

	@Override
	public void cleanup(Context ctx) throws IOException, InterruptedException {
		if (log.isDebugEnabled()) {
			log.debug("RowIndex {} counter {}", currentRowIndex, rowIndexCounter);
		}
	}
}
