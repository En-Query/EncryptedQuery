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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Scanner;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;

public class FlinkStdOutputCapturer implements Runnable {

	/**
	 * This is how Flink outputs the job id in its stdout. We look for this word, and what comes
	 * next is the actual job id
	 */
	private static final String JOB_ID = "JobID";

	private InputStream inputStream;
	private Logger log;
	private OutputStream stdOutput;
	private String jobId;

	public FlinkStdOutputCapturer(InputStream inputStream, Logger log, OutputStream stdOutput) {
		Validate.notNull(inputStream);
		Validate.notNull(log);
		Validate.notNull(stdOutput);
		this.inputStream = inputStream;
		this.log = log;
		this.stdOutput = stdOutput;
	}

	@Override
	public void run() {
		try (InputStreamReader isr = new InputStreamReader(inputStream);
				BufferedReader br = new BufferedReader(isr);
				OutputStreamWriter osw = new OutputStreamWriter(stdOutput);
				BufferedWriter bw = new BufferedWriter(osw);) {

			boolean isFirstLine = true;
			String line = null;

			while ((line = br.readLine()) != null) {
				if (!isFirstLine) {
					bw.newLine();
				} else {
					isFirstLine = false;
				}
				log.info(line);
				bw.write(line);
				if (jobId == null) {
					jobId = extractJobId(line);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Extract the job id from a string.<br/>
	 * An example output from Flink is: <br/>
	 * <br/>
	 * 
	 * Job has been submitted with JobID 78d9a65366dae4099b932a5b31b214b1<br/>
	 * <br/>
	 * 
	 * So we scan the string until token `JobID` and returns the following token.
	 * 
	 * @param line
	 */
	private String extractJobId(String line) {
		String result = null;
		boolean nextTokenIsJobId = false;
		try (Scanner scanner = new Scanner(line)) {
			while (scanner.hasNext()) {
				String token = scanner.next();
				if (nextTokenIsJobId) {
					result = token;
					break;
				} else if (JOB_ID.equals(token)) {
					nextTokenIsJobId = true;
				}
			}
		}

		return result;
	}

	/**
	 * @return
	 */
	public String flinkJobId() {
		return jobId;
	}
}
