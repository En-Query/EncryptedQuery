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
package org.enquery.encryptedquery.flink.streaming;



import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.apache.commons.lang3.Validate;
import org.apache.flink.api.common.functions.StoppableFunction;
import org.apache.flink.streaming.api.functions.source.RichSourceFunction;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.enquery.encryptedquery.data.Query;
import org.enquery.encryptedquery.filter.RecordFilter;
import org.enquery.encryptedquery.flink.TimestampFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class takes care of ending the source when a maximum runtime has elapsed.
 */
public abstract class TimeBoundStoppableConsumer extends RichSourceFunction<InputRecord> implements StoppableFunction {

	/**
	 * 
	 */
	private static final int MARKER_FILE_WAIT_TIME = 30_000;

	/**
	 * 
	 */
	private static final String JOB_RUNNING_MARKER_FILE_NAME = "job-running";

	private static final long serialVersionUID = -2263113444102431195L;

	private static final Logger log = LoggerFactory.getLogger(TimeBoundStoppableConsumer.class);

	private final Long maxTimestamp;
	private final long windowSize;
	private final String responseFilePath;
	private TimeWindow lastWindow;
	private long windowCount;
	private final String selectorFieldName;
	private final String filterExpr;

	private transient volatile boolean isRunning = true;
	private transient volatile boolean failed = false;
	private transient long recordCount = 0;
	private transient long skippedRecordCount = 0;
	private transient RecordFilter recordFilter;



	/**
	 * 
	 */
	public TimeBoundStoppableConsumer(Long maxTimestamp,
			Path responseFilePath,
			Time windowSize,
			Query query) {

		Validate.notNull(query);
		this.selectorFieldName = query.getQueryInfo().getQuerySchema().getSelectorField();

		this.maxTimestamp = maxTimestamp;
		this.responseFilePath = responseFilePath.toString();
		this.windowSize = windowSize.toMilliseconds();
		Validate.isTrue(this.windowSize / 1000 > 0L, "Window size must be > 0 seconds");

		filterExpr = query.getQueryInfo().getFilterExpression();
	}

	public long getRecordCount() {
		return recordCount;
	}

	/**
	 * @throws InterruptedException
	 * @throws IOException
	 */
	protected void waitForPendingWindows() throws InterruptedException, IOException {
		while (inProgressFileExists()) {
			log.info("Waiting for all responses to be created.");
			Thread.sleep(MARKER_FILE_WAIT_TIME);
		}
	}

	/**
	 * @return
	 * @throws IOException
	 */
	private boolean inProgressFileExists() throws IOException {
		try {
			final Path path = Paths.get(responseFilePath);
			// safety precaution, create the dir
			// Files.createDirectories(path);
			Path found = Files.walk(path)
					.filter(p -> Files.isRegularFile(p))
					.filter(f -> {
						boolean inProgress = f.getFileName().toString().endsWith(ResponseFileNameBuilder.IN_PROGRESS_SUFFIX);
						// log.info("file: {}, inProgress: {}", f, inProgress);
						return inProgress;
					})
					.findFirst()
					.orElse(null);

			log.info("Inprogress file found: {}", found);
			return found != null;
		} catch (NoSuchFileException e) {
			// this exception means some of the parent directories are not found or have been
			// deleted in the middle of scanning
			return false;
		}
	}

	@Override
	public void cancel() {
		isRunning = false;
	}

	@Override
	public void stop() {
		cancel();
	}

	protected void setAsFailed() {
		failed = true;
	}

	protected void beginRun() throws IOException {
		createJobRunningFileMarker();
		isRunning = true;
		if (filterExpr != null) {
			recordFilter = new RecordFilter(filterExpr);
			log.info("Initialized using filter expression: '{}'", filterExpr);
		}
	}

	private Path jobRunningMarkerFile() throws IOException {
		Path path = Paths.get(responseFilePath);
		Files.createDirectories(path);
		Path file = path.resolve(JOB_RUNNING_MARKER_FILE_NAME);
		return file;
	}

	protected boolean canRun() {
		return isRunning && (maxTimestamp == null || System.currentTimeMillis() < maxTimestamp);
	}

	protected void endRun(SourceContext<InputRecord> ctx) throws InterruptedException, IOException {
		log.info("Emitted a total of {} records and skipped {} records.", getRecordCount(), skippedRecordCount);

		if (failed) {
			log.info("Failed.");
		} else if (maxTimestamp != null && System.currentTimeMillis() >= maxTimestamp) {
			log.info("Exceeded allowed runtime.");
		} else if (!isRunning) {
			log.info("Source was externally cancelled.");
		} else {
			log.info("Source is exhausted.");
		}

		if (lastWindow != null) {
			sendEof(ctx, lastWindow);
			lastWindow = null;
		}


		Thread.sleep(MARKER_FILE_WAIT_TIME);
		// if (!failed) {
		waitForPendingWindows();
		// }

		deleteJobRunningMarkerFile();
	}

	/**
	 * Creates a marker file containing the timestamp of when the job is going to finish. Responder
	 * response fiel collector will not delete the directory while this file is here.
	 * 
	 * @throws IOException
	 * 
	 */
	private void createJobRunningFileMarker() throws IOException {
		Path file = jobRunningMarkerFile();
		try (BufferedWriter w = Files.newBufferedWriter(file)) {
			if (maxTimestamp != null) {
				w.write("Running until: ");
				w.write(TimestampFormatter.format(maxTimestamp));
			} else {
				w.write("Running forever.");
			}
		}
	}

	/**
	 * Deletes the job running marker file, allowing Responder to delete the directory once all
	 * response files are copied to the database.
	 * 
	 * @throws IOException
	 */
	private void deleteJobRunningMarkerFile() throws IOException {
		try {
			Files.delete(jobRunningMarkerFile());
		} catch (NoSuchFileException ignore) {
			// ok if the file was not there
		}
	}

	protected void collect(SourceContext<InputRecord> ctx, Map<String, Object> record, long timestamp) throws Exception {
		Validate.notNull(record);
		long start = TimeWindow.getWindowStartWithOffset(timestamp, 0, windowSize);
		long end = start + windowSize;
		TimeWindow current = new TimeWindow(start, end);

		// if we see a new window, send previous window eof signal
		if (lastWindow != null && !current.equals(lastWindow)) {
			sendEof(ctx, lastWindow);
		}
		lastWindow = current;

		// skip record with null selector field
		if (record.get(selectorFieldName) == null) return;

		if (recordFilter == null || recordFilter.satisfiesFilter(record)) {
			InputRecord ir = new InputRecord();
			ir.data = record;
			ir.windowMinTimestamp = current.getStart();
			ir.windowMaxTimestamp = current.maxTimestamp();
			ir.windowSize = windowCount++;
			// log.info("Sending record for window {}", WindowInfo.windowInfo(current));
			ctx.collectWithTimestamp(ir, timestamp);
			recordCount++;
		} else {
			skippedRecordCount++;
			if (log.isDebugEnabled()) log.debug("Skipped record do to not matching filter: {}", record);
		}
	}

	/**
	 * @param lastWindow2
	 * @param lastWindow3
	 */
	private void sendEof(SourceContext<InputRecord> ctx, TimeWindow timeWindow) {
		InputRecord ir = new InputRecord();
		ir.windowMinTimestamp = timeWindow.getStart();
		ir.windowMaxTimestamp = timeWindow.maxTimestamp();
		ir.eof = true;
		ir.windowSize = windowCount;
		log.info("Sending EOF signal for window {} with a total of {} records.", WindowInfo.windowInfo(timeWindow), windowCount);
		ctx.collectWithTimestamp(ir, System.currentTimeMillis());

		// reset counters
		lastWindow = null;
		windowCount = 0;
	}

}
