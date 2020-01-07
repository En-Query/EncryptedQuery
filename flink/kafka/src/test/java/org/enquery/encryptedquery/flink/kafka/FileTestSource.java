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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Map;
import java.util.Scanner;

import org.apache.flink.streaming.api.windowing.time.Time;
import org.enquery.encryptedquery.data.DataSchema;
import org.enquery.encryptedquery.data.Query;
import org.enquery.encryptedquery.flink.streaming.InputRecord;
import org.enquery.encryptedquery.flink.streaming.TimeBoundStoppableConsumer;
import org.enquery.encryptedquery.json.JSONStringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class FileTestSource extends TimeBoundStoppableConsumer {

	private static final long serialVersionUID = 896339099221645435L;
	public static final Logger log = LoggerFactory.getLogger(FileTestSource.class);

	private final String inputDataFile;
	private final DataSchema dataSchema;
	private transient JSONStringConverter jsonConverter;

	/**
	 * @param windowSize
	 * 
	 */
	public FileTestSource(Path inputDataFile,
			Path responseFile,
			Long maxTimestamp,
			Time windowSize,
			Query query) {

		super(maxTimestamp, responseFile, windowSize, query);
		this.inputDataFile = inputDataFile.toString();
		dataSchema = query.getQueryInfo().getQuerySchema().getDataSchema();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.flink.streaming.api.functions.source.SourceFunction#run(org.apache.flink.streaming
	 * .api.functions.source.SourceFunction.SourceContext)
	 */
	@Override
	public void run(SourceContext<InputRecord> ctx) throws Exception {
		beginRun();
		try {
			Iterator<String> iter = Files.lines(Paths.get(inputDataFile)).iterator();
			while (canRun() && iter.hasNext()) {

				if (!canRun()) break;

				// read the first value as delay
				String line = delay(iter.next());

				if (jsonConverter == null) {
					jsonConverter = new JSONStringConverter(dataSchema);
				}

				final Map<String, Object> record = jsonConverter.toStringObjectFlatMap(line);
				log.debug("Converted: {}\nTo: {}", line, record);

				collect(ctx, record, System.currentTimeMillis());
			}
		} finally {
			endRun(ctx);
		}
	}

	/**
	 * @param next
	 * @return
	 * @throws InterruptedException
	 * @throws IOException
	 */
	private String delay(String line) throws InterruptedException, IOException {
		if (line.charAt(0) == '{') return line;

		int tabPos = line.indexOf("\t");

		try (Scanner scanner = new Scanner(line)) {
			int delay = scanner.nextInt();
			// first value is the delay in seconds
			Thread.sleep(delay * 1_000);
		}

		return line.substring(tabPos + 1, line.length());
	}
}
