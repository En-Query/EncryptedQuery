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
package org.enquery.encryptedquery.standalone;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import javax.xml.bind.JAXBException;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.core.Partitioner;
import org.enquery.encryptedquery.data.Query;
import org.enquery.encryptedquery.data.QueryInfo;
import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.enquery.encryptedquery.encryption.CryptoSchemeFactory;
import org.enquery.encryptedquery.encryption.CryptoSchemeRegistry;
import org.enquery.encryptedquery.responder.ResponderProperties;
import org.enquery.encryptedquery.xml.transformation.QueryTypeConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("rawtypes")
public class Responder implements AutoCloseable {

	private final int DEFAULT_COLUMN_BUFFER_MEMORY_MB = 2048;

	// conservative default queue size, in case the records are very large
	private final int DEFAULT_MAX_RECORD_QUEUE_SIZE = 100;

	// estimated overhead of a byte[] object
	private final int DATA_CHUNK_OVERHEAD = 16;

	private static final long PROGRESS_REPORT_INTERVAL = TimeUnit.MINUTES.toMillis(1);
	private static final Logger log = LoggerFactory.getLogger(Responder.class);
	private static final DecimalFormat numFormat = new DecimalFormat("###,###.##");
	private static final DecimalFormat pctFormat = new DecimalFormat("#%");

	private Path inputDataFile;
	private int numberOfProcessorThreads = 1;

	private int maxRecordQueueSize;
	private int maxColumnQueueSize;
	private int maxResponseQueueSize;
	private BlockingQueue<Record> recordQueue;
	private BlockingQueue<Buffer.Column> columnQueue;
	private BlockingQueue<ColumnNumberAndCipherText> responseQueue;

	private Integer maxHitsPerSelector;

	private Query query;
	private Path outputFileName;
	private Path queryFileName;

	private Partitioner partitioner = new Partitioner();
	private ExecutorService executionService;
	private CompletionService completionService;
	private CryptoScheme crypto;
	private int hashBitSize;
	private Future<Integer> responseWriterFuture;
	private AtomicLong globalRecordsRead = new AtomicLong();
	private AtomicLong globalRecordsProcessed = new AtomicLong();
	private AtomicLong globalColumnsProcessed = new AtomicLong();
	private AtomicLong globalColumnsEmitted = new AtomicLong();
	private AtomicLong globalChunksProcessed = new AtomicLong();
	private long previousColumnsProcessed = 0L;
	private long previousChunksProcessed = 0L;
	private int taskCount;
	private Future dispatcherFuture;
	private long nextProgressReportTime;
	private int bufferWidth = 0;
	private QueryInfo queryInfo;

	private void initialize(Map<String, String> config) throws Exception {
		Validate.notNull(config);
		log.info("Initializing Standalone Query Execution");
		log.info(" Configuration Parameters:");
		for (Map.Entry<String, String> entry : config.entrySet()) {
			log.info("   {} = {}", entry.getKey(), entry.getValue());
		}
		log.info("-End of Configuration Parameters");


		if (config.containsKey(StandaloneConfigurationProperties.PROCESSING_THREADS)) {
			numberOfProcessorThreads = Integer.valueOf(config.get(StandaloneConfigurationProperties.PROCESSING_THREADS));
		}

		if (config.containsKey(StandaloneConfigurationProperties.MAX_HITS_PER_SELECTOR)) {
			maxHitsPerSelector = Integer.parseInt(config.get(StandaloneConfigurationProperties.MAX_HITS_PER_SELECTOR));
		}

		maxRecordQueueSize = Integer.parseInt(config.getOrDefault(StandaloneConfigurationProperties.MAX_RECORD_QUEUE_SIZE,
				Integer.valueOf(DEFAULT_MAX_RECORD_QUEUE_SIZE).toString()));

		int defaultMaxColumnQueueSize = 2 * numberOfProcessorThreads;
		maxColumnQueueSize = Integer.parseInt(config.getOrDefault(StandaloneConfigurationProperties.MAX_COLUMN_QUEUE_SIZE,
				Integer.valueOf(defaultMaxColumnQueueSize).toString()));

		int defaultMaxResponseQueueSize = 10 * numberOfProcessorThreads;
		maxResponseQueueSize = Integer.parseInt(config.getOrDefault(StandaloneConfigurationProperties.MAX_RESPONSE_QUEUE_SIZE,
				Integer.valueOf(defaultMaxResponseQueueSize).toString()));

		if (executionService == null) {
			// we use three additional threads, for reading file and aggregating results
			executionService = Executors.newFixedThreadPool(numberOfProcessorThreads + 3);
		}

		if (crypto == null) {
			crypto = CryptoSchemeFactory.make(config, executionService);
		}

		query = loadQuery();

		queryInfo = query.getQueryInfo();

		// override chunk size if needed
		String chunkSizeStr = config.get(StandaloneConfigurationProperties.CHUNK_SIZE);
		if (chunkSizeStr != null) {
			int overrideChunkSize = Integer.valueOf(chunkSizeStr);
			if (overrideChunkSize > 0 && overrideChunkSize < queryInfo.getDataChunkSize()) {
				queryInfo.setDataChunkSize(overrideChunkSize);
			}
		}

		hashBitSize = queryInfo.getHashBitSize();

		if (bufferWidth <= 0) {
			// set bufferWidth (i.e. number of columns) based on hashBitSize, dataChunkSize,
			// and the amount of memory allowed
			int bufferMemoryMb = Integer.parseInt(config.getOrDefault(ResponderProperties.COLUMN_BUFFER_MEMORY_MB,
					Integer.valueOf(DEFAULT_COLUMN_BUFFER_MEMORY_MB).toString()));
			bufferWidth = (int) ((double) bufferMemoryMb * (1 << 20) / (1 << hashBitSize) / (DATA_CHUNK_OVERHEAD + queryInfo.getDataChunkSize()));
			bufferWidth = Math.max(bufferWidth, 1);
			log.info("Calculated column buffer width = {}", bufferWidth);
		}

		recordQueue = new ArrayBlockingQueue<>(maxRecordQueueSize);
		columnQueue = new ArrayBlockingQueue<>(maxColumnQueueSize);
		responseQueue = new ArrayBlockingQueue<>(maxResponseQueueSize);

		log.info("  Hash Bit Size = {}", hashBitSize);
		log.info("  Data Chunk Size = {}", queryInfo.getDataChunkSize());
		log.info("  Column Buffer Size = {}", bufferWidth);
		log.info("  Max Record Queue Size = {}", maxRecordQueueSize);
		log.info("  Max Column Queue Size = {}", maxColumnQueueSize);
		log.info("  Max Response Queue Size = {}", maxResponseQueueSize);
		log.info("  Max Hits per selector = {}", maxHitsPerSelector);
	}

	void setBufferWidth(int bufferWidth) {
		Validate.isTrue(bufferWidth > 0);
		this.bufferWidth = bufferWidth;
		log.info("  Internally specified column buffer width = {}", bufferWidth);
	}

	private Query loadQuery() throws JAXBException, IOException, FileNotFoundException {
		final CryptoSchemeRegistry registry = new CryptoSchemeRegistry() {
			@Override
			public CryptoScheme cryptoSchemeByName(String schemeId) {
				if (schemeId == null) return null;
				if (schemeId.equals(crypto.name())) return crypto;
				return null;
			}
		};
		QueryTypeConverter queryConverter = new QueryTypeConverter();
		queryConverter.setCryptoRegistry(registry);
		queryConverter.initialize();
		// load the query
		try (FileInputStream fis = new FileInputStream(queryFileName.toFile())) {
			return queryConverter.toCoreQuery(queryConverter.unmarshal(fis));
		}
	}

	public void run(Map<String, String> config) throws Exception {
		long startTime = System.currentTimeMillis();

		Validate.notNull(queryFileName);
		Validate.isTrue(Files.exists(queryFileName));
		Validate.notNull(outputFileName);
		Validate.isTrue(!Files.exists(outputFileName));
		Validate.notNull(inputDataFile);
		Validate.isTrue(Files.exists(inputDataFile), "File not found: '%s'", inputDataFile);

		initialize(config);
		startProcessing();
		waitForProcessing();

		// once the file reader and column processors are finished, send a special marker to tell
		// the response aggregator to end
		responseQueue.put(new ColumnNumberAndCipherTextEof());
		// now wait for the response to be written, and propagate any exceptions
		responseWriterFuture.get();

		executionService.shutdown();
		log.info("Finished in {}ms.", Long.toString(System.currentTimeMillis() - startTime));
	}

	private void waitForProcessing() throws InterruptedException, ExecutionException, TimeoutException {
		log.info("Start waiting for processor threads.");
		boolean poisonPillSent = false;
		nextProgressReportTime = System.currentTimeMillis() + PROGRESS_REPORT_INTERVAL;
		while (taskCount > 0) {
			Future<?> f = completionService.poll(1, TimeUnit.MINUTES);

			if (f != null) {
				--taskCount;
				// allow the futures to throw exceptions
				f.get();

				if (!poisonPillSent && dispatcherFuture.isDone()) {
					// dispatcher thread ended, signal column calculators to end
					sendPoisonPill();
					poisonPillSent = true;
				}
			}
			logProgress();
		}
		log.info("Finished waiting for processor threads.");
	}

	/**
	 * @throws InterruptedException
	 * 
	 */
	private void sendPoisonPill() throws InterruptedException {
		for (int i = 0; i < numberOfProcessorThreads; i++) {
			columnQueue.put(new EofColumn());
		}
	}

	/**
	 * Initialize & Start Processing Threads
	 * 
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	private void startProcessing() throws Exception {

		completionService = new ExecutorCompletionService<>(executionService);
		taskCount = 0;

		completionService.submit(new FileReader(inputDataFile,
				queryInfo,
				maxHitsPerSelector,
				recordQueue,
				partitioner,
				globalRecordsRead));
		++taskCount;


		dispatcherFuture = completionService.submit(new Dispatcher(
				recordQueue,
				columnQueue,
				query,
				crypto,
				bufferWidth,
				globalRecordsProcessed,
				globalColumnsEmitted));
		++taskCount;

		for (int i = 0; i < numberOfProcessorThreads; i++, ++taskCount) {
			completionService.submit(new ColumnCalculator(
					columnQueue,
					responseQueue,
					query,
					crypto,
					globalColumnsProcessed,
					globalChunksProcessed));
		}

		// the response writer task is not waited on, since it wont finish until EofReponse is
		// put in the responseQueue, which have to be done only after all column processors finish
		responseWriterFuture = executionService.submit(
				new ResponseWriter(this.responseQueue,
						queryInfo,
						outputFileName));

		log.info("Running Standalone Query '{}' on file '{}'.", queryInfo.getQueryName(), inputDataFile);
	}

	public Path getOutputFileName() {
		return outputFileName;
	}

	public void setOutputFileName(Path outputFileName) {
		this.outputFileName = outputFileName;
	}

	public Path getInputDataFile() {
		return inputDataFile;
	}

	public void setInputDataFile(Path inputDataFile) {
		this.inputDataFile = inputDataFile;
	}

	public Path getQueryFileName() {
		return queryFileName;
	}

	public void setQueryFileName(Path queryFileName) {
		this.queryFileName = queryFileName;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.AutoCloseable#close()
	 */
	@Override
	public void close() throws Exception {
		if (crypto != null) {
			crypto.close();
			crypto = null;
		}
	}

	public void logProgress() {
		if (System.currentTimeMillis() < nextProgressReportTime) return;

		long recordsRead = globalRecordsRead.get();
		long columnsProcessed = globalColumnsProcessed.get();

		if (columnsProcessed > previousColumnsProcessed) {
			long chunksProcessed = globalChunksProcessed.get();
			long recentColumnsProcessed = columnsProcessed - previousColumnsProcessed;
			long recentChunksProcessed = chunksProcessed - previousChunksProcessed;
			double recentAverageLoad = (double) recentChunksProcessed / recentColumnsProcessed;
			double recentAverageDensity = recentAverageLoad / (1 << hashBitSize);
			log.info("Read {} records, processed {} columns; recent average load per column = {} chunks ({})", numFormat.format(recordsRead), numFormat.format(columnsProcessed), numFormat.format(recentAverageLoad), pctFormat.format(recentAverageDensity));
			previousChunksProcessed = chunksProcessed;
		} else {
			log.info("Read {} records, processed {} columns ", numFormat.format(recordsRead), numFormat.format(columnsProcessed));
		}

		nextProgressReportTime = System.currentTimeMillis() + PROGRESS_REPORT_INTERVAL;
		previousColumnsProcessed = columnsProcessed;
	}
}
