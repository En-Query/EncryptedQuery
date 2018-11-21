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

import java.text.DecimalFormat;
import java.util.List;

import org.enquery.encryptedquery.responder.wideskies.common.ColumnBasedResponderProcessor;
import org.enquery.encryptedquery.responder.wideskies.common.RowBasedResponderProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessingUtils {

	private static final Logger logger = LoggerFactory.getLogger(ProcessingUtils.class);
	private static DecimalFormat numFormat = new DecimalFormat("###,###,###,###,###,###");

	/**
	 * Returns the number of rows processed by the responder processing threads.
	 * 
	 * @param processors List of processing threads
	 * @return long number of records processed
	 */
	public static long recordsProcessed(List<ColumnBasedResponderProcessor> processors) {
		long recordsProcessed = 0;
		for (ColumnBasedResponderProcessor processor : processors) {
			recordsProcessed += processor.getRecordsProcessed();
		}
		logger.debug("Records Processed so far {}", numFormat.format(recordsProcessed));
		return recordsProcessed;
	}

	/**
	 * Returns the number of rows processed by the responder processing threads.
	 * 
	 * @param processors List of processing threads
	 * @return long number of records processed
	 */
	public static long recordsProcessedRowBased(List<RowBasedResponderProcessor> processors) {
		long recordsProcessed = 0;
		for (RowBasedResponderProcessor processor : processors) {
			recordsProcessed += (processor).getRecordsProcessed();
		}
		logger.debug("Records Processed so far {}", numFormat.format(recordsProcessed));
		return recordsProcessed;
	}

	/**
	 * Returns the Percent of records processed based on how many have been loaded into the queues
	 * 
	 * @return int Percent Complete
	 */
	public static int getPercentComplete(long recordsLoaded, List<ColumnBasedResponderProcessor> processors) {
		long counter = ProcessingUtils.recordsProcessed(processors);
		if (recordsLoaded != 0) {
			double pct = Math.round(((double) counter / (double) recordsLoaded) * 100.0);
			logger.debug("RL {} / RP {} / % {}", recordsLoaded, counter, pct);
			return (int) pct;
		} else {
			return 0;
		}
	}

	/**
	 * Returns the Percent of records processed based on how many have been loaded into the queues
	 * 
	 * @return int Percent Complete
	 */
	public static int getPercentCompleteRowBased(long recordsLoaded, List<RowBasedResponderProcessor> processors) {
		long counter = ProcessingUtils.recordsProcessedRowBased(processors);
		if (recordsLoaded != 0) {
			double pct = Math.round(((double) counter / (double) recordsLoaded) * 100.0);
			logger.debug("RL {} / RP {} / % {}", recordsLoaded, counter, pct);
			return (int) pct;
		} else {
			return 0;
		}
	}
}
