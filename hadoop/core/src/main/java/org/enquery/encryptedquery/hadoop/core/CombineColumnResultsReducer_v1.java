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
import java.util.Map;
import java.util.TreeMap;

import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.output.MultipleOutputs;
import org.enquery.encryptedquery.data.QueryInfo;
import org.enquery.encryptedquery.data.Response;
import org.enquery.encryptedquery.encryption.CipherText;
import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.enquery.encryptedquery.encryption.CryptoSchemeFactory;
import org.enquery.encryptedquery.encryption.CryptoSchemeRegistry;
import org.enquery.encryptedquery.xml.transformation.QueryTypeConverter;
import org.enquery.encryptedquery.xml.transformation.ResponseTypeConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reducer class for the CombineColumnResults job
 *
 * <p>
 * It is assumed that this job is run with a single reducer task. The encrypted
 * values from the previous job are inserted into a new {@code Response}, which
 * is written to a file.
 */
public class CombineColumnResultsReducer_v1 extends Reducer<LongWritable, BytesWritable, LongWritable, Text> {
	private static final Logger log = LoggerFactory.getLogger(CombineColumnResultsReducer_v1.class);

	private MultipleOutputs<LongWritable, Text> mos = null;

	private QueryInfo queryInfo;
	private Map<String, String> config;
	private String outputFileName = null;
	private Path outputFile = null;
	private TreeMap<Integer, CipherText> treeMap = null;
	private FileSystem hdfs;
	private CryptoScheme crypto;
	@SuppressWarnings("unused")
	private CryptoSchemeRegistry cryptoRegistry;

	@Override
	public void setup(Context ctx) throws IOException, InterruptedException {
		super.setup(ctx);

		mos = new MultipleOutputs<>(ctx);

		hdfs = FileSystem.newInstance(ctx.getConfiguration());

		String hdfsWorkingFolder = ctx.getConfiguration().get("hadoopWorkingFolder");
		String configFileName = ctx.getConfiguration().get("configFileName");
		Path configFile = new Path(hdfsWorkingFolder + Path.SEPARATOR + configFileName);
		log.info("Loading Config File ( {} ).", configFile.toString());
		config = HDFSFileIOUtils.loadConfig(hdfs, configFile);

		Path queryInfoFile = new Path(hdfsWorkingFolder + Path.SEPARATOR + "query-info");
		queryInfo = new HadoopFileSystemStore(hdfs).recall(queryInfoFile, QueryInfo.class);

		log.info("Query Identifer: {}", queryInfo.getIdentifier());

		try {
			crypto = CryptoSchemeFactory.make(config);
			cryptoRegistry = new CryptoSchemeRegistry() {
				@Override
				public CryptoScheme cryptoSchemeByName(String schemeId) {
					if (schemeId == null)
						return null;
					if (schemeId.equals(crypto.name()))
						return crypto;
					return null;
				}
			};
		} catch (Exception e) {
			throw new IOException("Error creating crypto registry.", e);
		}

		outputFileName = ctx.getConfiguration().get("outputFileName");
		outputFile = new Path(hdfsWorkingFolder + Path.SEPARATOR + outputFileName);

		treeMap = new TreeMap<>();
	}

	@Override
	public void reduce(LongWritable colNum, Iterable<BytesWritable> encryptedColumns, Context ctx)
			throws IOException, InterruptedException {
		ctx.getCounter(HadoopConfigurationProperties.MRStats.NUM_COLUMNS).increment(1);
		int colIndex = (int) colNum.get();
		boolean first = true;
		for (BytesWritable encryptedColumnW : encryptedColumns) {
			ctx.getCounter(HadoopConfigurationProperties.MRStats.TOTAL_COLUMN_COUNT).increment(1);
			if (first) {
				treeMap.put(colIndex, crypto.cipherTextFromBytes(encryptedColumnW.copyBytes()));
				first = false;
			} else {
				log.warn("column index {} unexpectedly seen a second time!", colIndex);
			}
		}

	}

	@Override
	public void cleanup(Context ctx) throws IOException, InterruptedException {
		try {
			crypto = CryptoSchemeFactory.make(config);
			final CryptoSchemeRegistry registry = new CryptoSchemeRegistry() {
				@Override
				public CryptoScheme cryptoSchemeByName(String schemeId) {
					if (schemeId == null)
						return null;
					if (schemeId.equals(crypto.name()))
						return crypto;
					return null;
				}
			};

			QueryTypeConverter queryConverter = new QueryTypeConverter();
			queryConverter.setCryptoRegistry(registry);
			queryConverter.initialize();

			ResponseTypeConverter converter = new ResponseTypeConverter();
			converter.setQueryConverter(queryConverter);
			converter.setSchemeRegistry(registry);
			converter.initialize();
			FSDataOutputStream stream = hdfs.create(outputFile);

			Response response = new Response(queryInfo);
			response.addResponseElements(treeMap);
			converter.marshal(converter.toXML(response), stream);
			stream.close();
		} catch (Exception e) {
			throw new IOException("Error saving response.", e);
		}
		mos.close();
	}
}