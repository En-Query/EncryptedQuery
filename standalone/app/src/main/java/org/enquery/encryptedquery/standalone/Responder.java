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

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.management.timer.Timer;
import javax.xml.bind.JAXBException;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.core.Partitioner;
import org.enquery.encryptedquery.data.Query;
import org.enquery.encryptedquery.data.Response;
import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.enquery.encryptedquery.encryption.CryptoSchemeFactory;
import org.enquery.encryptedquery.encryption.CryptoSchemeRegistry;
import org.enquery.encryptedquery.json.JSONStringConverter;
import org.enquery.encryptedquery.responder.QueueRecord;
import org.enquery.encryptedquery.responder.RecordPartitioner;
import org.enquery.encryptedquery.responder.ResponderProperties;
import org.enquery.encryptedquery.utils.KeyedHash;
import org.enquery.encryptedquery.xml.transformation.QueryTypeConverter;
import org.enquery.encryptedquery.xml.transformation.ResponseTypeConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Responder {

	private static final Logger log = LoggerFactory.getLogger(Responder.class);
	private DecimalFormat numFormat = new DecimalFormat("###,###,###,###,###,###");

	private Path inputDataFile;
	private int numberOfProcessorThreads = 1;

	// conservative default queue size, in case the records a very large
	private int maxQueueSize = 100;
	private int maxHitsPerSelector = 1000;

	// keeps track of how many hits a given
	// selector has
	private HashMap<Integer, Integer> rowIndexCounter = new HashMap<>();

	// Log how many records exceeded the maxHitsPerSelector limit
	private HashMap<Integer, Integer> rowIndexOverflowCounter = new HashMap<>();

	private List<ArrayBlockingQueue<QueueRecord>> newRecordQueues = new ArrayList<>();
	private ConcurrentLinkedQueue<Response> responseQueue = new ConcurrentLinkedQueue<>();

	private long recordCounter = 0;
	private List<ColumnBasedResponderProcessor> responderProcessors = new ArrayList<>();
	private Query query;
	private Path outputFileName;

	private Map<String, String> runParameters = new HashMap<>();
	private Partitioner partitioner = new Partitioner();
	private ExecutorService executionService;
	// private int hashGroupSize;
	private long selectorNullCount;
	private AtomicLong lineNumber;
	private List<Future<Response>> futures;
	private CryptoScheme crypto;
	private List<String> querySchemaElementNames;

	public Responder() {}

	public int getPercentComplete() {
		return ProcessingUtils.getPercentComplete(recordCounter, responderProcessors);
	}

	private void configure(Map<String, String> config) throws Exception {
		Validate.notNull(config);
		runParameters.putAll(config);

		if (runParameters.containsKey(StandaloneConfigurationProperties.PROCESSING_THREADS)) {
			numberOfProcessorThreads = Integer.valueOf(runParameters.get(StandaloneConfigurationProperties.PROCESSING_THREADS));
		}
		if (runParameters.containsKey(StandaloneConfigurationProperties.MAX_QUEUE_SIZE)) {
			String mqs = runParameters.get(StandaloneConfigurationProperties.MAX_QUEUE_SIZE).toString();
			maxQueueSize = Integer.parseInt(mqs);
		}
		
		if (runParameters.containsKey(StandaloneConfigurationProperties.MAX_HITS_PER_SELECTOR)) {
			maxHitsPerSelector = Integer.parseInt(runParameters.get(StandaloneConfigurationProperties.MAX_HITS_PER_SELECTOR)); 
		}

		log.info("Standalone Query Configuration:");
		for (Map.Entry<String, String> entry : runParameters.entrySet()) {
			log.info("  {} = {}", entry.getKey(), entry.getValue());
		}
	}

	public void run(Map<String, String> config) throws Exception {
		Validate.notNull(query);
		Validate.notNull(outputFileName);
		Validate.isTrue(!Files.exists(outputFileName));
		Validate.notNull(inputDataFile);
		Validate.isTrue(Files.exists(inputDataFile));

		configure(config);

		log.info("Running Standalone Query '{}' on file '{}'.", query.getQueryInfo().getQueryName(), inputDataFile);

		initialize();
		processFile();
		shutdown();
		waitUntilFinished();
		outputResponse();
	}

	private void processFile() throws IOException {
		try (Stream<String> lines = Files.lines(inputDataFile)) {
			lines.forEach(line -> processLine(line));
		}

		if (log.isWarnEnabled()) {
			if (selectorNullCount > 0) {
				log.warn("{} Records had a null selector from source", selectorNullCount);
			}
		}

		if (log.isInfoEnabled()) {
			log.info("Imported {} records for processing", numFormat.format(recordCounter));
			if (rowIndexOverflowCounter.size() > 0) {
				log.warn("{} Row Hashs overflowed because of MaxHitsPerSelector.  Increase MaxHitsPerSelector to reduce this if resources allow.", rowIndexOverflowCounter.size());
				if (log.isDebugEnabled()) {
					for (int i : rowIndexOverflowCounter.keySet()) {
						log.debug("rowIndex {} exceeded max Hits {} by {}", i, maxHitsPerSelector,
								rowIndexOverflowCounter.get(i));
					}
				}
			}
		}
	}

	private void shutdown() throws InterruptedException {
		// All data has been submitted to the queue so send an EOF marker
		QueueRecord eof = new QueueRecord();
		eof.setIsEndOfFile(true);
		for (ArrayBlockingQueue<QueueRecord> q : newRecordQueues) {
			q.put(eof);
		}
		executionService.shutdown();
	}

	private void waitUntilFinished() throws InterruptedException {
		// Loop through processing threads until they are finished processing. Report how many
		// are still running every minute.
		long notificationTimer = System.currentTimeMillis() + Timer.ONE_MINUTE;
		boolean terminated = executionService.awaitTermination(1, TimeUnit.SECONDS);
		while (!terminated) {
			if (System.currentTimeMillis() > notificationTimer) {
				long running = futures.stream().filter(f -> !f.isDone()).count();;
				long recordsProcessed = ProcessingUtils.recordsProcessed(responderProcessors);
				log.info("There are {} responder processes running, {} records processed / {} % complete",
						running, numFormat.format(recordsProcessed), numFormat.format(getPercentComplete()));

				notificationTimer = System.currentTimeMillis() + Timer.ONE_MINUTE;
			}
			terminated = executionService.awaitTermination(1, TimeUnit.SECONDS);
		}
	}

	private void processLine(String line) {
		Map<String, Object> jsonData = null;
		try {
			jsonData = JSONStringConverter.toStringObjectMapFromList(querySchemaElementNames, line);
		} catch (Exception e) {
			log.warn("Failed to parse input record. Skipping. Line number: {}, Error: {}", lineNumber.get(), e.getMessage());
			return;
		}

		if (jsonData != null && jsonData.size() > 0) {
			processRow(jsonData);
			lineNumber.incrementAndGet();
		} else if (jsonData.size() < 1) {
			log.warn("jsonData has no data, input line: {}", line);
			return;
		} else {
			log.warn("jsonData is null, input line: {}", line);
			return;
		}
	}

	private void processRow(Map<String, Object> jsonData) {

		final String selector = RecordPartitioner.getSelectorValue(query.getQueryInfo().getQuerySchema(), jsonData).trim();
		// log.info("Selector Value {}", selector);
		if (selector == null || selector.length() <= 0) {
			selectorNullCount++;
			return;
		}

		try {
			int rowIndex = KeyedHash.hash(query.getQueryInfo().getHashKey(), query.getQueryInfo().getHashBitSize(), selector);
			// logger.info("Selector {} / Hash {}", selector, rowIndex);

			// Track how many "hits" there are for each selector (Converted into
			// rowIndex)
			if (rowIndexCounter.containsKey(rowIndex)) {
				rowIndexCounter.put(rowIndex, (rowIndexCounter.get(rowIndex) + 1));
			} else {
				rowIndexCounter.put(rowIndex, 1);
			}

			// If we are not over the max hits value add the record to the
			// appropriate queue
			if (rowIndexCounter.get(rowIndex) <= maxHitsPerSelector) {
				List<Byte> parts = RecordPartitioner.partitionRecord(partitioner, query.getQueryInfo(), jsonData);
				QueueRecord qr = new QueueRecord(rowIndex, selector, parts);
				int whichQueue = rowIndex % numberOfProcessorThreads;
				// log.info("Hash {} going to queue {} with {} bytes", rowIndex, whichQueue,
				// parts.size());

				// insert element in queue, waits until space is available
				newRecordQueues.get(whichQueue).put(qr);
				recordCounter++;
			} else {
				if (rowIndexOverflowCounter.containsKey(rowIndex)) {
					rowIndexOverflowCounter.put(rowIndex, (rowIndexOverflowCounter.get(rowIndex) + 1));
				} else {
					rowIndexOverflowCounter.put(rowIndex, 1);
				}
			}
		} catch (Exception e) {
			log.error("Exception adding record selector {} / record {}, Exception: {}", selector, recordCounter, e.getMessage());
		}
	}

	private void initialize() throws Exception {
		selectorNullCount = 0;
		lineNumber = new AtomicLong(0);
		crypto = CryptoSchemeFactory.make(runParameters);
		querySchemaElementNames = query.getQueryInfo().getQuerySchema().getElementNames();

		// Create a Queue for each thread
		for (int i = 0; i < numberOfProcessorThreads; i++) {
			newRecordQueues.add(new ArrayBlockingQueue<QueueRecord>(maxQueueSize));
		}

		// Initialize & Start Processing Threads
		executionService = Executors.newFixedThreadPool(numberOfProcessorThreads);
		for (int i = 0; i < numberOfProcessorThreads; i++) {
			ColumnBasedResponderProcessor processor = new ColumnBasedResponderProcessor(
					newRecordQueues.get(i),
					responseQueue,
					query,
					crypto,
					runParameters,
					partitioner);

			responderProcessors.add(processor);
		}

		futures = responderProcessors.stream()
				.map(task -> executionService.submit(task))
				.collect(Collectors.toList());
	}

	// Compile the results from all the threads into one response file.
	private void outputResponse() throws FileNotFoundException, IOException, JAXBException {
		log.info("Writing response to file: '{}'", outputFileName);

		ConsolidateResponse aggregator = new ConsolidateResponse(crypto);
		Response outputResponse = aggregator.consolidateResponse(responseQueue, query.getQueryInfo());

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

		ResponseTypeConverter converter = new ResponseTypeConverter();
		converter.setQueryConverter(queryConverter);
		converter.setSchemeRegistry(registry);
		converter.initialize();

		try (OutputStream output = new FileOutputStream(outputFileName.toFile())) {
			org.enquery.encryptedquery.xml.schema.Response xml = converter.toXML(outputResponse);
			converter.marshal(xml, output);
		}
	}

	public Query getQuery() {
		return query;
	}

	public void setQuery(Query query) {
		this.query = query;
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


}
