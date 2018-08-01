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
package org.enquery.encryptedquery.responder.wideskies.jdbc.dao;

import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.enquery.encryptedquery.query.wideskies.QueryInfo;
import org.enquery.encryptedquery.query.wideskies.QueryUtils;
import org.enquery.encryptedquery.responder.wideskies.common.QueueRecord;
import org.enquery.encryptedquery.utils.KeyedHash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessRecord {

	private static final Logger logger = LoggerFactory.getLogger(ProcessRecord.class);
	/**
	 * Inserts a database record into the correct queue.
	 * @param queryInfo
	 * @param rs
	 * @param hashGroupSize
	 * @param queues
	 * @return int (1 if inserted into queue, 0 if no selector, -1 if other error)
	 */
	public static int insertRecord(int maxHitsPerSelector, HashMap<Integer, Integer> rowIndexCounter, HashMap<Integer, Integer> rowIndexOverflowCounter, 
			QueryInfo queryInfo, ResultSet rs, int hashGroupSize, List<ConcurrentLinkedQueue<QueueRecord>> queues) {

        Map<String, Object> rowData;
        
		try {

			ResultSetMetaData rsmd = rs.getMetaData();
			int columnCount = rsmd.getColumnCount();

			// Extract data from Database Record returned and insert into JSON format
			rowData = new HashMap<String, Object>();
			for(int i = 1; i <= columnCount; i++) {
				Object value = DataTypeConversion.getObject(rsmd.getColumnType(i), i, rs);
//				logger.info("column name {} type {}", rsmd.getColumnName(i), rsmd.getColumnTypeName(i));
				rowData.put(rsmd.getColumnName(i), value);
//				logger.info("Value for column {} is {}", rsmd.getColumnName(i), value);
			}

			String selector = null;
			String selectorFieldName = queryInfo.getQuerySchema().getSelectorName();
			int whichQueue = 0;
			selector = (String) rowData.get(selectorFieldName); // QueryUtils.getSelectorByQueryTypeJSON(queryInfo.getQuerySchema(), jsonData);
			if (selector != null) {
				try {
					int rowIndex = KeyedHash.hash(queryInfo.getHashKey(), queryInfo.getHashBitSize(), selector);
					if ( rowIndexCounter.containsKey(rowIndex) ) {
						rowIndexCounter.put(rowIndex, (rowIndexCounter.get(rowIndex) + 1));
					} else {
						rowIndexCounter.put(rowIndex, 1);
					}
					if ( rowIndexCounter.get(rowIndex) <= maxHitsPerSelector) {					
						whichQueue = rowIndex / hashGroupSize;
						List<Byte> parts = QueryUtils.partitionDataElementMap(queryInfo.getQuerySchema(), rowData, queryInfo.getEmbedSelector());
						QueueRecord qr = new QueueRecord(rowIndex, selector, parts);
						queues.get(whichQueue).add(qr);
						return 1;			  
					} else {
						if ( rowIndexOverflowCounter.containsKey(rowIndex) ) {
							rowIndexOverflowCounter.put(rowIndex, (rowIndexOverflowCounter.get(rowIndex) + 1));
						} else {
							rowIndexOverflowCounter.put(rowIndex, 1);
						}
						return -1;
					}
				} catch (Exception e) {
					logger.error("Exception parsing Record {}", e.getMessage());
					return -1;
				}
			} else {
				// Selector is null, report back as such.
				return 0;
			}
		} catch (Exception e) {
			logger.error("Exception inserting record into queue {}", e.getMessage());
			return -2;
		}
	}

}