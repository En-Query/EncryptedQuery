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
import java.util.TreeMap;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;
import org.enquery.encryptedquery.data.QueryInfo;
import org.enquery.encryptedquery.data.Response;
import org.enquery.encryptedquery.encryption.CipherText;
import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.enquery.encryptedquery.xml.transformation.ResponseTypeConverter;

/**
 * Reducer class for the CombineColumnResults job
 *
 * <p>
 * It is assumed that this job is run with a single reducer task. The encrypted values from the
 * previous job are inserted into a new {@code Response}, which is written to a file.
 */
public class CombineColumnResultsReducer extends Reducer<LongWritable, BytesWritable, LongWritable, Text> {

	private QueryInfo queryInfo;
	private String outputFileName = null;
	private Path outputFile = null;
	private TreeMap<Integer, CipherText> treeMap;
	private DistCacheLoader loader;

	@Override
	public void setup(Context ctx) throws IOException, InterruptedException {
		super.setup(ctx);
		try {
			Configuration conf = ctx.getConfiguration();
			loader = new DistCacheLoader();
			queryInfo = loader.loadQueryInfo();
			String hdfsWorkingFolder = conf.get(HadoopConfigurationProperties.HDFSWORKINGFOLDER);
			outputFileName = conf.get("outputFileName");
			outputFile = new Path(hdfsWorkingFolder + Path.SEPARATOR + outputFileName);
			treeMap = new TreeMap<>();
		} catch (Exception e) {
			throw new IOException("Error initializing CombineColumnResultsReducer.", e);
		}
	}

	@Override
	public void reduce(LongWritable colNum, Iterable<BytesWritable> encryptedColumns, Context ctx) throws IOException, InterruptedException {
		ctx.getCounter(HadoopConfigurationProperties.MRStats.NUM_COLUMNS).increment(1);
		CipherText finalColumnValue = null;
		int colIndex = (int) colNum.get();
		final CryptoScheme crypto = loader.getCrypto();

		boolean first = true;
		for (BytesWritable encryptedColumnW : encryptedColumns) {
			ctx.getCounter(HadoopConfigurationProperties.MRStats.TOTAL_COLUMN_COUNT).increment(1);
			if (first) {
				finalColumnValue = crypto.cipherTextFromBytes(encryptedColumnW.copyBytes());
				first = false;
			} else {
				CipherText column = crypto.cipherTextFromBytes(encryptedColumnW.copyBytes());
				finalColumnValue = crypto.computeCipherAdd(queryInfo.getPublicKey(), column, finalColumnValue);
			}
		}
		treeMap.put(colIndex, finalColumnValue);
	}

	@Override
	public void cleanup(Context ctx) throws IOException, InterruptedException {
		try {
			final ResponseTypeConverter responseConverter = loader.getResponseConverter();

			FileSystem hdfs = FileSystem.newInstance(ctx.getConfiguration());
			try (FSDataOutputStream stream = hdfs.create(outputFile);) {
				Response response = new Response(queryInfo);
				response.addResponseElements(treeMap);
				responseConverter.marshal(responseConverter.toXML(response), stream);
			}

			if (loader != null) loader.close();

		} catch (Exception e) {
			throw new IOException("Error saving response.", e);
		}
	}
}
