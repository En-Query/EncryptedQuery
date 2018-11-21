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
package org.enquery.encryptedquery.responder.business;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;

public class ChildProcessLogger implements Runnable {
	private InputStream inputStream;
	private Logger log;
	private OutputStream stdOutput;

	public ChildProcessLogger(InputStream inputStream, Logger log, OutputStream stdOutput) {
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

			int i = 0;
			String line = null;
			while ((line = br.readLine()) != null) {
				if (i++ > 0) bw.newLine();
				log.info(line);
				bw.write(line);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
