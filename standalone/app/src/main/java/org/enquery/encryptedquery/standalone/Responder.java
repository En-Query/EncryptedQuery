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
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.timer.Timer;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.core.Partitioner;
import org.enquery.encryptedquery.data.Query;
import org.enquery.encryptedquery.data.Response;
import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.enquery.encryptedquery.encryption.CryptoSchemeFactory;
import org.enquery.encryptedquery.encryption.CryptoSchemeRegistry;
import org.enquery.encryptedquery.xml.transformation.QueryTypeConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("rawtypes")
public class Responder implements AutoCloseable {

	private static final Logger log = LoggerFactory.getLogger(Responder.class);
	private static final DecimalFormat numFormat = new DecimalFormat("###,###,###,###,###,###");

	private Path inputDataFile;
	private int numberOfProcessorThreads = 1;

	// conservative default queue size, in case the records are very large
	private int maxQueueSize = 100;
	private Integer maxHitsPerSelector;

	private List<BlockingQueue<QueueRecord>> newRecordQueues = new ArrayList<>();
	private LinkedBlockingQueue<Response> responseQueue = new LinkedBlockingQueue<>();

	private Query query;
	private Path outputFileName;
	private Path queryFileName;

	private Partitioner partitioner = new Partitioner();
	private ExecutorService executionService;
	private CompletionService completionService;
	private CryptoScheme crypto;
	private QueryTypeConverter queryConverter;
	private Future<Integer> responseWriterFuture;
	private AtomicLong globalRecordsProcessed = new AtomicLong();
	private AtomicLong globalRecordsRead = new AtomicLong();
	private int computeThreshold;
	private int taskCount;

	private void initialize(Map<String, String> config) throws Exception {
		Validate.notNull(config);

		if (config.containsKey(StandaloneConfigurationProperties.PROCESSING_THREADS)) {
			numberOfProcessorThreads = Integer.valueOf(config.get(StandaloneConfigurationProperties.PROCESSING_THREADS));
		}

		// if no queue size is given, use the compute threshold if given, otherwise keep it low
		String ct = config.get(StandaloneConfigurationProperties.COMPUTE_THRESHOLD);
		Validate.notNull(ct, StandaloneConfigurationProperties.COMPUTE_THRESHOLD + " property missing.");
		computeThreshold = Integer.parseInt(ct);

		if (config.containsKey(StandaloneConfigurationProperties.MAX_QUEUE_SIZE)) {
			String mqs = config.get(StandaloneConfigurationProperties.MAX_QUEUE_SIZE).toString();
			maxQueueSize = Integer.parseInt(mqs);
		} else {
			// if no queue size is given, use the compute threshold
			maxQueueSize = computeThreshold;
		}
		// it does not make sense to use queue with more then computeThreshold slots
		if (maxQueueSize > computeThreshold) {
			maxQueueSize = computeThreshold;
		}

		if (config.containsKey(StandaloneConfigurationProperties.MAX_HITS_PER_SELECTOR)) {
			maxHitsPerSelector = Integer.parseInt(config.get(StandaloneConfigurationProperties.MAX_HITS_PER_SELECTOR));
		}

		if (executionService == null) {
			// we use two additional threads, for reading file and aggregating results
			executionService = Executors.newFixedThreadPool(numberOfProcessorThreads + 2);
		}

		if (crypto == null) {
			crypto = CryptoSchemeFactory.make(config, executionService);
		}

		final CryptoSchemeRegistry registry = new CryptoSchemeRegistry() {
			@Override
			public CryptoScheme cryptoSchemeByName(String schemeId) {
				if (schemeId == null) return null;
				if (schemeId.equals(crypto.name())) return crypto;
				return null;
			}
		};

		queryConverter = new QueryTypeConverter();
		queryConverter.setCryptoRegistry(registry);
		queryConverter.initialize();

		// Create a Queue for each thread
		for (int i = 0; i < numberOfProcessorThreads; i++) {
			newRecordQueues.add(new ArrayBlockingQueue<QueueRecord>(maxQueueSize));
		}

		// load the query
		try (FileInputStream fis = new FileInputStream(queryFileName.toFile())) {
			query = queryConverter.toCoreQuery(queryConverter.unmarshal(fis));
		}

		log.info("Standalone Query Configuration:");
		for (Map.Entry<String, String> entry : config.entrySet()) {
			log.info("  {} = {}", entry.getKey(), entry.getValue());
		}
	}

	public void run(Map<String, String> config) throws Exception {
		Validate.notNull(queryFileName);
		Validate.isTrue(Files.exists(queryFileName));
		Validate.notNull(outputFileName);
		Validate.isTrue(!Files.exists(outputFileName));
		Validate.notNull(inputDataFile);
		Validate.isTrue(Files.exists(inputDataFile));

		initialize(config);
		startProcessing();
		waitForColumnProcessingCompletion();

		// once the file reader and column processors are finished, send a special marker to tell
		// the response aggregator to end
		responseQueue.put(new EofReponse(query.getQueryInfo()));
		// now wait for the response to be written, and propagate any exceptions
		responseWriterFuture.get();
		executionService.shutdown();
		log.info("Finished.");
	}

	private void waitForColumnProcessingCompletion() throws InterruptedException, ExecutionException, TimeoutException {
		// Loop through processing threads until they are finished processing. Report how many
		// are still running every minute.
		long notificationTimer = System.currentTimeMillis() + Timer.ONE_MINUTE;
		while (taskCount > 0) {
			Future<?> f = completionService.poll(1, TimeUnit.MINUTES);

			if (f != null) {
				--taskCount;
				// allow the futures to throw exceptions
				f.get();
			}

			if (System.currentTimeMillis() > notificationTimer) {
				logProgress();
				notificationTimer = System.currentTimeMillis() + Timer.ONE_MINUTE;
			}
		}
	}

	/**
	 * Initialize & Start Processing Threads
	 * 
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	private void startProcessing() throws Exception {

		FileReader fileReader = new FileReader(inputDataFile,
				query.getQueryInfo(),
				maxHitsPerSelector,
				newRecordQueues,
				partitioner,
				globalRecordsRead);

		completionService = new ExecutorCompletionService<>(executionService);
		completionService.submit(fileReader);
		++taskCount;

		for (int i = 0; i < numberOfProcessorThreads; i++, ++taskCount) {
			completionService.submit(new ColumnBasedResponderProcessor(
					newRecordQueues.get(i),
					responseQueue,
					query,
					crypto,
					globalRecordsProcessed,
					computeThreshold));
		}

		// the response writer task is not waited on, since it wont finish until EofReponse is
		// put in the responseQueue, which have to be done only after all column processors finish
		responseWriterFuture = executionService.submit(
				new ResponseWriter(crypto,
						this.responseQueue,
						query.getQueryInfo(),
						outputFileName));

		log.info("Running Standalone Query '{}' on file '{}'.", query.getQueryInfo().getQueryName(), inputDataFile);
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
		long processed = globalRecordsProcessed.get();
		long read = globalRecordsRead.get();
		int pct = 0;

		if (read != 0) {
			pct = (int) Math.round((processed / (double) read) * 100.0);
		}

		// long globalRecordsProcessed = ProcessingUtils.recordsProcessed(responderProcessors);
		log.info("Processed {} records.  {} % complete",
				numFormat.format(processed),
				numFormat.format(pct));

	}
}
