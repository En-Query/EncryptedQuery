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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.PublicKey;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.bind.JAXBException;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.output.MultipleOutputs;
import org.enquery.encryptedquery.core.Partitioner;
import org.enquery.encryptedquery.data.Query;
import org.enquery.encryptedquery.data.RecordEncoding;
import org.enquery.encryptedquery.encryption.CipherText;
import org.enquery.encryptedquery.encryption.ColumnProcessor;
import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.enquery.encryptedquery.encryption.CryptoSchemeFactory;
import org.enquery.encryptedquery.encryption.CryptoSchemeRegistry;
import org.enquery.encryptedquery.hadoop.core.HadoopConfigurationProperties.MRStats;
import org.enquery.encryptedquery.responder.ResponderProperties;
import org.enquery.encryptedquery.utils.ConversionUtils;
import org.enquery.encryptedquery.utils.PIRException;
import org.enquery.encryptedquery.xml.transformation.QueryTypeConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reducer class for the ProcessData job
 * 
 * <p>
 */
public class ProcessDataReducer extends Reducer<IntWritable, BytesWritable, LongWritable, BytesWritable> {
	private static final Logger log = LoggerFactory.getLogger(ProcessDataReducer.class);
	private final DecimalFormat numFormat = new DecimalFormat("###,###,###,###,###,###");

	private LongWritable outputKey = null;
	private BytesWritable outputValue;

	private MultipleOutputs<LongWritable, BytesWritable> mos = null;

	private HashMap<Integer, List<Pair<Integer, byte[]>>> dataColumns;
	// the column values for the encrypted query calculations
	private TreeMap<Integer, CipherText> columns = null;
	// keeps track of column location for each rowIndex (Selector Hash)
	private int[] rowColumnCounters;
	private int rowIndexCount =0;
	private int computeThreshold=30000;

	private Query query = null;
	private boolean limitHitsPerSelector = true;
	private int maxHitsPerSelector = -1;

	private Map<String, String> config;

	private CryptoScheme crypto;
	private ColumnProcessor cec;

	private QueryTypeConverter queryTypeConverter;
	
    transient private Partitioner partitioner;
    transient private RecordEncoding recordEncoding;
    transient private byte[] handle;

	@Override
	public void setup(Context ctx) throws IOException, InterruptedException {
		super.setup(ctx);

		outputKey = new LongWritable();
		outputValue = new BytesWritable();
		mos = new MultipleOutputs<>(ctx);
		
		if (partitioner == null) {
			partitioner = new Partitioner();
		}

		FileSystem fs = FileSystem.newInstance(ctx.getConfiguration());
		log.info("Hadoop FileSystem Uri( {} )",fs.getUri().toString() );
		String queryFileName = ctx.getConfiguration().get("queryFileName");
		String configFileName = ctx.getConfiguration().get("configFileName");
		String hadoopWorkingFolder = ctx.getConfiguration().get(HadoopConfigurationProperties.HDFSWORKINGFOLDER);
		computeThreshold = ctx.getConfiguration().getInt(ResponderProperties.COMPUTE_THRESHOLD, 30000);
				
		try {
			Path queryFile = new Path(hadoopWorkingFolder + Path.SEPARATOR + queryFileName);
			Path configFile = new Path(hadoopWorkingFolder + Path.SEPARATOR + configFileName);
			log.info("Loading Config File ( {} ).", configFile.toString());
			config = HDFSFileIOUtils.loadConfig(fs, configFile);

			initializeCryptoScheme();
			
			log.info("Loading Query File ( {} ).", queryFile.toString());
			query = loadQuery(fs, queryFile);
			log.info("Query Id: {}", query.getQueryInfo().getIdentifier());
			Validate.notNull(query, "Query value cannot be null.");
			Validate.notNull(config, "Config invalid.");
			limitHitsPerSelector = ctx.getConfiguration().getBoolean(ResponderProperties.LIMIT_HITS_PER_SELECTOR, true);
			maxHitsPerSelector = ctx.getConfiguration().getInt(ResponderProperties.MAX_HITS_PER_SELECTOR, 10000);
	
			handle = crypto.loadQuery(query.getQueryInfo(), query.getQueryElements());
			cec = crypto.makeColumnProcessor(handle);
			recordEncoding = new RecordEncoding(query.getQueryInfo());

		} catch (Exception e) {
			log.error("Exception initializing ColumnReducer: {}", e.getMessage());
			throw new RuntimeException(e.getMessage());
		}
		
		dataColumns = new HashMap<>();
		columns = new TreeMap<>();

		// Initialize row counters
		final int size = 1 << query.getQueryInfo().getHashBitSize();
		if (rowColumnCounters == null) {
			rowColumnCounters = new int[size];
		} else {
			Arrays.fill(rowColumnCounters, 0);
		}

	}

	@Override
	public void reduce(IntWritable rowIndexW, Iterable<BytesWritable> dataElements, Context ctx) {

		int rowIndex = rowIndexW.get();
		int hitCount = 0;
		ctx.getCounter(MRStats.NUM_HASHES_REDUCER).increment(1);

		for (BytesWritable dataElement : dataElements) {
			if (limitHitsPerSelector && hitCount >= maxHitsPerSelector) {
				if (log.isDebugEnabled()) {
					log.debug("maxHitsPerSelector limit ({}) reached for rowIndex = {}", maxHitsPerSelector, rowIndex);
				}
				ctx.getCounter(HadoopConfigurationProperties.MRStats.NUM_HASHS_OVER_MAX_HITS).increment(1);
				break;
			}
			try {
			addDataElement(rowIndex, dataElement.copyBytes());
			} catch (Exception e) {
				log.error("Exception adding data element in ColumnReducer: {}", e.getMessage());
				throw new RuntimeException(e.getMessage());
			}
			
		}
		rowIndexCount++;
		if ((rowIndexCount % computeThreshold) == 0) {
			log.info("Processed {} records, compute threshold {} reached, will pause to encrypt/reduce value", numFormat.format(rowIndexCount), numFormat.format(computeThreshold));
			processColumns();
			log.info("Compute finished, resuming data processing");
		}
		
	}

	public void addDataElement(int rowIndex, byte[] rowParts) throws PIRException {
		List<Byte> rowPartsList = new ArrayList<Byte>();
		for (byte b : rowParts) {
			rowPartsList.add(b);
		}

		if (log.isDebugEnabled()) {
			log.info("RowIndex {} Parts Length {} Parts {}", rowIndex, rowParts.length, Hex.encodeHexString(rowParts));
		}

		ByteBuffer encoded = ByteBuffer.wrap(rowParts);
		final List<byte[]> hitValPartitions = partitioner.createPartitions(encoded, query.getQueryInfo().getDataChunkSize());

		int rowCounter = rowColumnCounters[rowIndex];

		for (int i = 0; i < hitValPartitions.size(); ++i) {
			if (!dataColumns.containsKey(i + rowCounter)) {
				dataColumns.put(i + rowCounter, new ArrayList<Pair<Integer, byte[]>>());
			}
			dataColumns.get(i + rowCounter).add(Pair.of(rowIndex, hitValPartitions.get(i)));
		}

		// Update the rowCounter (next free column position) for the selector
		rowColumnCounters[rowIndex] += hitValPartitions.size();
	}

	/**
	 * Since we do not have an endless supply of memory to process unlimited data process the data
	 * in chunks.
	 */
	private void processColumns() {
		computeEncryptedColumns();
		dataColumns.clear();
	}

	/**
	 * This method adds all the entries ready for computation into the library and computes to a
	 * single value. That value is then stored to be computed with the next batch of values.
	 */
	public void computeEncryptedColumns() {
		final PublicKey publicKey = query.getQueryInfo().getPublicKey();

		for (final Map.Entry<Integer, List<Pair<Integer, byte[]>>> entry : dataColumns.entrySet()) {
			final int col = entry.getKey();
			final List<Pair<Integer, byte[]>> dataCol = entry.getValue();
			for (int i = 0; i < dataCol.size(); ++i) {
				if (null != dataCol.get(i)) {
					cec.insert(dataCol.get(i).getLeft(), dataCol.get(i).getRight());
				}
			}
			final CipherText newValue = cec.computeAndClear();
			CipherText prevValue = columns.get(col);
			if (prevValue == null) {
				prevValue = crypto.encryptionOfZero(publicKey);
			}
			final CipherText sum = crypto.computeCipherAdd(publicKey, prevValue, newValue);
			columns.put(col, sum);
		}
	}

	
	@Override
	public void cleanup(Context ctx) throws IOException, InterruptedException {
		// Process remaining data in dataColumns array
		computeEncryptedColumns();
        // loop trough columns and write to mos
		for (Map.Entry<Integer, CipherText> entry : columns.entrySet()) {
			outputKey.set(entry.getKey());
			byte[] columnBytes = entry.getValue().toBytes();
			outputValue.set(columnBytes, 0, columnBytes.length);
            if (log.isDebugEnabled()) {
            	log.debug("Column {} byteLength {} bytes {}", entry.getKey(), columnBytes.length, Hex.encodeHexString(columnBytes));
            }
            
			mos.write(HadoopConfigurationProperties.EQ_COLS, outputKey, outputValue);
		     	
		}
		
		mos.close();
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
	
	protected Query loadQuery(FileSystem fs, Path file) throws IOException, FileNotFoundException, JAXBException {
		try (FSDataInputStream fis = fs.open(file)) {
			org.enquery.encryptedquery.xml.schema.Query xml = queryTypeConverter.unmarshal(fis);
			return queryTypeConverter.toCoreQuery(xml);
		}
	}

}