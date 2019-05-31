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
package org.enquery.encryptedquery.flink.kafka;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Random;

import org.enquery.encryptedquery.flink.streaming.TimeBoundStoppableConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class FileTestSource extends TimeBoundStoppableConsumer<String> {

	private static final long serialVersionUID = 896339099221645435L;
	private static final Logger log = LoggerFactory.getLogger(FileTestSource.class);
	// private static final int MAX_DELAY_MS = 5_000;
	private final String inputDataFile;
	private final int maxDelay;

	/**
	 * 
	 */
	public FileTestSource(Path inputDataFile, Path responseFile, long maxTimestamp, int maxDelay) {
		super(maxTimestamp, responseFile);
		this.inputDataFile = inputDataFile.toString();
		this.maxDelay = maxDelay;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.flink.streaming.api.functions.source.SourceFunction#run(org.apache.flink.streaming
	 * .api.functions.source.SourceFunction.SourceContext)
	 */
	@Override
	public void run(SourceContext<String> ctx) throws Exception {
		int count = 0;
		Random random = new Random();

		beginRun();

		// final long stoptime = System.currentTimeMillis() + (runtimeInSeconds * 1000);
		Iterator<String> iter = Files.lines(Paths.get(inputDataFile)).iterator();

		while (canRun() && iter.hasNext()) {

			// delay only after first item
			if (count > 0) {
			if (maxDelay > 0) {
				Thread.sleep(random.nextInt(maxDelay));
				if (!canRun()) break;
			}
			}

			String line = iter.next();

			synchronized (ctx.getCheckpointLock()) {
				ctx.collect(line);
				count++;
			}
		}

		log.info("{} records processed.", count);
		endRun();
	}
}
