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

import java.util.HashMap;
import java.util.Map;

import org.apache.flink.streaming.api.functions.windowing.ProcessAllWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.enquery.encryptedquery.encryption.CipherText;
import org.enquery.encryptedquery.flink.TimestampFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AllColumnsPerWindowReducer extends ProcessAllWindowFunction<WindowPerColumnResult, WindowFinalResult, TimeWindow> {

	private static final Logger log = LoggerFactory.getLogger(AllColumnsPerWindowReducer.class);

	private static final long serialVersionUID = 1L;

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.flink.streaming.api.functions.windowing.ProcessAllWindowFunction#process(org.
	 * apache.flink.streaming.api.functions.windowing.ProcessAllWindowFunction.Context,
	 * java.lang.Iterable, org.apache.flink.util.Collector)
	 */
	@Override
	public void process(ProcessAllWindowFunction<WindowPerColumnResult, WindowFinalResult, TimeWindow>.Context context,
			Iterable<WindowPerColumnResult> values,
			Collector<WindowFinalResult> out) throws Exception {

		WindowPerColumnResult first = null;
		Map<Integer, CipherText> colMap = new HashMap<>();
		for (WindowPerColumnResult wr : values) {
			if (first == null) first = wr;
			colMap.put(wr.col, wr.cipherText);
		}

		WindowFinalResult result = new WindowFinalResult(//
				first.windowMinTimestamp,
				first.windowMaxTimestamp,
				colMap);

		if (log.isDebugEnabled()) {
			log.debug("Reduced {} columns of window ending in '{}'.",
					colMap.size(),
					TimestampFormatter.format(first.windowMaxTimestamp));
		}

		out.collect(result);
	}
}
