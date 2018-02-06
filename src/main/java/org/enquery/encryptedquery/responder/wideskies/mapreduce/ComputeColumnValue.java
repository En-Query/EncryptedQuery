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
package org.enquery.encryptedquery.responder.wideskies.mapreduce;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.Callable;

import org.enquery.encryptedquery.encryption.ModPowAbstraction;
import org.enquery.encryptedquery.query.wideskies.Query;
import org.enquery.encryptedquery.query.wideskies.QueryUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ComputeColumnValue implements Callable<ColumnValue> {

	private static final Logger logger = LoggerFactory.getLogger(ComputeColumnValue.class);

    private String colNum;
    private int columnCounter = 0;
    private List<String> values;
    private Query query = null;
     
    public ComputeColumnValue(String colNum, List<String> colVals, Query query) {
        this.colNum = colNum;
        this.values = colVals;
        this.query = query;
    }
     
    public ColumnValue call() throws Exception {
    	
        long startTime = System.currentTimeMillis();
        BigInteger column = BigInteger.valueOf(1);
    	for (String val : values) {
    		String[] valueInfo = val.split(",");
    		int rowIndex = Integer.valueOf(valueInfo[0]);
    		String part = valueInfo[1];
    		//  logger.debug("Processing rowIndex {}, value {}", rowIndex, part);

    		byte[] partAsByteArray = QueryUtils.hexStringToByteArray(part);
    		BigInteger partAsBI = new BigInteger(1, partAsByteArray);
    		//  logger.debug("partAsBi: " + partAsBI.toString(16));

    		BigInteger rowQuery = query.getQueryElement(rowIndex);
    		//  logger.debug("rowQuery: " + rowQuery.toString());

    		BigInteger exp = null;   	
			exp = ModPowAbstraction.modPow(rowQuery, partAsBI, query.getNSquared());

    		//  logger.debug("exp = " + exp.toString());
    		column = (column.multiply(exp)).mod(query.getNSquared());
    		//  logger.debug("column = " + column.toString());   	
    		columnCounter++;
    	}    	   
    	ColumnValue cv = new ColumnValue(colNum, column);
        long duration = System.currentTimeMillis() - startTime;
        logger.debug("It took {} Milliseconds to process {} parts for column {}", duration, columnCounter, colNum);
    	
    	return cv;
    }
}
