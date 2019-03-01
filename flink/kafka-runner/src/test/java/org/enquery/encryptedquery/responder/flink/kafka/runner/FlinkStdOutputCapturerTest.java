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
package org.enquery.encryptedquery.responder.flink.kafka.runner;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class FlinkStdOutputCapturerTest {
	private static final Logger log = LoggerFactory.getLogger(FlinkStdOutputCapturerTest.class);

	private String flinkOutput = "Starting execution of program\n"
			+ "Job has been submitted with JobID 858f0444803bb8edd0ca4eaf41136645";

	private ExecutorService threadPool = Executors.newCachedThreadPool();

	@Test
	public void test() throws InterruptedException, ExecutionException {
		InputStream is = new ByteArrayInputStream(flinkOutput.getBytes());
		FlinkStdOutputCapturer capturer = new FlinkStdOutputCapturer(is,
				log, System.out);

		Future<?> future = threadPool.submit(capturer);
		future.get();


		assertEquals("858f0444803bb8edd0ca4eaf41136645", capturer.flinkJobId());
	}

}
