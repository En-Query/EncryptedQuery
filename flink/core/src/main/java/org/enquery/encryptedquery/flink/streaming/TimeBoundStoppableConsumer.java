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

import org.apache.flink.api.common.functions.StoppableFunction;
import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.enquery.encryptedquery.flink.TimestampFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class takes care of ending the source when a maximum runtime has elapsed.
 */
public abstract class TimeBoundStoppableConsumer<E> implements SourceFunction<E>, StoppableFunction {

	/**
	 * 
	 */
	private static final String JOB_RUNNING_MARKER_FILE_NAME = "job-running";

	private static final long serialVersionUID = -2263113444102431195L;

	private static final Logger log = LoggerFactory.getLogger(TimeBoundStoppableConsumer.class);

	private final Long runtimeInSeconds;
	private final String responseFilePath;

	private transient volatile boolean isRunning = true;
	private transient long stopTime;


	/**
	 * 
	 */
	public TimeBoundStoppableConsumer(Long runTimeInSeconds, Path responseFilePath) {
		runtimeInSeconds = runTimeInSeconds;
		this.responseFilePath = responseFilePath.toString();
	}

	/**
	 * @throws InterruptedException
	 * @throws IOException
	 */
	protected void waitForPendingWindows() throws InterruptedException, IOException {
		while (inProgressFileExists()) {
			log.info("Waiting for all responses to be created.");
			Thread.sleep(2_000);
		}
	}

	/**
	 * @return
	 * @throws IOException
	 */
	private boolean inProgressFileExists() throws IOException {
		final Path path = Paths.get(responseFilePath);
		// safety precaution, create the dir
		Files.createDirectories(path);
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
	}

	@Override
	public void cancel() {
		isRunning = false;
	}

	@Override
	public void stop() {
		cancel();
	}

	protected void beginRun() throws IOException {
		stopTime = System.currentTimeMillis() + (runtimeInSeconds * 1000);

		createJobRunningFileMarker();
		log.info("Will run until: " + TimestampFormatter.format(stopTime));
		isRunning = true;
	}

	private Path jobRunningMarkerFile() throws IOException {
		Path path = Paths.get(responseFilePath);
		Files.createDirectories(path.getParent());
		Path file = path.resolve(JOB_RUNNING_MARKER_FILE_NAME);
		return file;
	}

	protected boolean canRun() {
		return isRunning && System.currentTimeMillis() < stopTime;
	}

	protected void endRun() throws InterruptedException, IOException {
		if (System.currentTimeMillis() >= stopTime) {
			log.info("Exceeded allowed runtime.");
		} else if (!isRunning) {
			log.info("Source was externally cancelled.");
		} else {
			log.info("Source is exhausted.");
		}
		waitForPendingWindows();
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
			w.write("Running until: ");
			w.write(TimestampFormatter.format(stopTime));
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
}
