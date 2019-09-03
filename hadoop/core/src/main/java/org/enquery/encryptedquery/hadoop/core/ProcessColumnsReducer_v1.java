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
import java.util.Arrays;

import org.apache.commons.lang3.Validate;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.mapreduce.Reducer;
import org.enquery.encryptedquery.data.Query;
import org.enquery.encryptedquery.encryption.CipherText;
import org.enquery.encryptedquery.encryption.ColumnProcessor;
import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.enquery.encryptedquery.hadoop.core.HadoopConfigurationProperties.MRStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reducer class for the ProcessColumn job
 * 
 * <p>
 * Each call to {@code reducer()} receives a stream of values {@code (row,chunk)} at a given column
 * position. These values are all read in (there will be a limited number of them) and used to
 * compute the encrypted column value for each successive column within the chunk, using one of the
 * classes implementing the {@code
 * ComputeEncryptedColumn} interface. As each encrypted column value is computed, a key-value pair
 * {@code (col, encvalue)} is emitted.
 */
public class ProcessColumnsReducer_v1 extends Reducer<IntWritable, IntBytesPairWritable, LongWritable, BytesWritable> {
	private static final Logger log = LoggerFactory.getLogger(ProcessColumnsReducer_v1.class);

	private LongWritable outputKey;
	private BytesWritable outputValue;
	private Query query;
	private int hashBitSize;
	private int bytesPerPart;
	private int chunkingByteSize;
	private int partsPerChunk;
	private int bytesPerChunk;
	private byte[][] dataChunks;
	private int rowIndices[];
	private int numParts[];
	private CryptoScheme crypto;
	private ColumnProcessor columnProcessor;
	private byte[] handle;
	private DistCacheLoader loader;

	@Override
	public void setup(Context ctx) throws IOException, InterruptedException {
		super.setup(ctx);

		try {
			outputKey = new LongWritable();
			outputValue = new BytesWritable();
			loader = new DistCacheLoader();

			query = loader.loadQuery();
			crypto = loader.getCrypto();

			log.info("Query Id: {}", query.getQueryInfo().getIdentifier());
			Validate.notNull(query, "Query value cannot be null.");
			hashBitSize = query.getQueryInfo().getHashBitSize();

			handle = crypto.loadQuery(query.getQueryInfo(), query.getQueryElements());
			columnProcessor = crypto.makeColumnProcessor(handle);

			bytesPerPart = query.getQueryInfo().getDataChunkSize();
			chunkingByteSize = Integer.valueOf(ctx.getConfiguration().get(HadoopConfigurationProperties.CHUNKING_BYTE_SIZE));
			partsPerChunk = Math.max(chunkingByteSize / bytesPerPart, 1);
			bytesPerChunk = partsPerChunk * bytesPerPart;
			log.info("HashBitSize ({}) / partsPerChunk ({}) / ChunkingByteSize ({})", hashBitSize, bytesPerChunk, chunkingByteSize);
			dataChunks = new byte[1 << hashBitSize][];
			rowIndices = new int[1 << hashBitSize];
			numParts = new int[1 << hashBitSize];
		} catch (Exception e) {
			throw new IOException("Exception initializing ProcessColumnsReducer.", e);
		}
	}

	@Override
	public void reduce(IntWritable colIndexW, Iterable<IntBytesPairWritable> rowIndexAndData, Context ctx)
			throws IOException, InterruptedException {
		ctx.getCounter(MRStats.NUM_COLUMNS).increment(1);

		int numChunks = 0;
		int colIndex = colIndexW.get();

		// read in all the (row, data) pairs
		for (IntBytesPairWritable val : rowIndexAndData) {
			// extract row index
			int rowIndex = val.getFirst().get();
			byte[] dataChunk = val.getSecond().copyBytes();

			rowIndices[numChunks] = rowIndex;
			dataChunks[numChunks] = dataChunk;
			numParts[numChunks] = dataChunk.length / bytesPerPart;
			numChunks++;
		}

		/* process each column of the buffered data */
		for (int col = 0; col < partsPerChunk; col++) {
			boolean emptyColumn = true;
			for (int i = 0; i < numChunks; i++) {
				if (numParts[i] <= col)
					continue;
				emptyColumn = false;
				int rowIndex = rowIndices[i];
				byte[] partBytes = Arrays.copyOfRange(dataChunks[i], col * bytesPerPart, (col + 1) * bytesPerPart);
				columnProcessor.insert(rowIndex, partBytes);
			}
			if (emptyColumn) {
				/* once we have encountered an empty column, we are done */
				break;
			}

			CipherText encryptedColumn = columnProcessor.computeAndClear();

			/* write encrypted column to file */
			long partColIndex = (long) colIndex * partsPerChunk + col;
			outputKey.set(partColIndex);
			byte[] columnBytes = encryptedColumn.toBytes();
			outputValue.set(columnBytes, 0, columnBytes.length);
			ctx.write(outputKey, outputValue);
		}
	}

	@Override
	public void cleanup(Context ctx) throws IOException, InterruptedException {
		try {
			if (columnProcessor != null) {
				columnProcessor.clear();
				columnProcessor = null;
			}
			if (handle != null) {
				crypto.unloadQuery(handle);
				handle = null;
			}
			if (loader != null) {
				loader.close();
				loader = null;
			}
		} catch (Exception e) {
			throw new IOException("Exception cleaning up.", e);
		}
	}
}
