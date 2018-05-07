/*
 * Copyright 2017 EnQuery.
 * This product includes software licensed to EnQuery under 
 * one or more license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * 
 * This file has been modified from its original source.
 */
package org.enquery.encryptedquery.responder.wideskies.common;

import java.text.DecimalFormat;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessingUtils {

	private static final Logger logger = LoggerFactory.getLogger(ProcessingUtils.class);
	private static DecimalFormat numFormat = new DecimalFormat("###,###,###,###,###,###");

	/**
	 * Returns the number of rows processed by the responder processing threads.
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
	 * @param processors List of processing threads
	 * @return long number of records processed
	 */
	public static long recordsProcessedRowBased(List<ResponderProcessingThread> processors) {
		long recordsProcessed = 0;
		for (ResponderProcessingThread processor : processors) {
			recordsProcessed += ( processor).getRecordsProcessed();
		}
		logger.debug("Records Processed so far {}", numFormat.format(recordsProcessed));
		return recordsProcessed;
	}

	/**
	 * Returns the Percent of records processed based on how many have been loaded into the queues
	 * @return int Percent Complete
	 */
	public static int getPercentComplete(long recordsLoaded, List<ColumnBasedResponderProcessor> processors)
	{
		long counter = ProcessingUtils.recordsProcessed(processors);
		if (recordsLoaded != 0) {
			double pct =  Math.round( ( (double)counter / (double)recordsLoaded ) * 100.0 ) ;
			logger.debug("RL {} / RP {} / % {}", recordsLoaded, counter, pct);
			return (int)pct;
		} else {
			return 0;
		}
	}

}
