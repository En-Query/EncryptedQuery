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
import java.util.Iterator;

import org.apache.commons.lang3.Validate;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.mapreduce.Reducer;
import org.enquery.encryptedquery.data.Query;
import org.enquery.encryptedquery.encryption.CipherText;
import org.enquery.encryptedquery.encryption.ColumnProcessor;
import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reducer class for the CombineColumnResults job
 */
public class CombineColumnResultsReducer extends Reducer<IntWritable, ColumnWritable, IntWritable, BytesWritable> {

	private static final Logger log = LoggerFactory.getLogger(CombineColumnResultsReducer.class);

	private CryptoScheme crypto;
	private ColumnProcessor columnProcessor;
	private BytesWritable outputValue;
	private byte[] handle;

	@Override
	public void setup(Context ctx) throws IOException, InterruptedException {
		super.setup(ctx);
		try {
			DistCacheLoader loader = new DistCacheLoader();
			crypto = loader.getCrypto();
			Query query = loader.loadQuery();

			handle = crypto.loadQuery(query.getQueryInfo(), query.getQueryElements());
			columnProcessor = crypto.makeColumnProcessor(handle);
			outputValue = new BytesWritable();

			log.info("finished setup");
		} catch (Exception e) {
			throw new IOException("Error initializing CombineColumnResultsReducer.", e);
		}
	}

	@Override
	public void reduce(IntWritable columnNumber, Iterable<ColumnWritable> cipherTexts, Context ctx) throws IOException, InterruptedException {
		Iterator<ColumnWritable> iter = cipherTexts.iterator();
		ColumnWritable column = iter.next();
		Validate.isTrue(!iter.hasNext(), "Unexpected more than one cipher text per column.");

		column.get().forEachRow((row, data) -> {
			columnProcessor.insert(row, data);
		});
		CipherText cipherText = columnProcessor.computeAndClear();

		byte[] bytes = cipherText.toBytes();
		outputValue.set(bytes, 0, bytes.length);

		ctx.write(columnNumber, outputValue);
	}

	@Override
	public void cleanup(Context ctx) throws IOException, InterruptedException {
		try {
			log.info("cleaning up");

			if (columnProcessor != null) {
				columnProcessor.clear();
				columnProcessor = null;
			}

			if (handle != null) {
				crypto.unloadQuery(handle);
				handle = null;
			}

			if (crypto != null) {
				crypto.close();
				crypto = null;
			}

			log.info("finished cleaning up");

		} catch (Exception e) {
			throw new IOException("Error cleaning up.", e);
		}
	}
}
