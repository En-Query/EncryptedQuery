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
package org.enquery.encryptedquery.responder.wideskies.standalone;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import org.enquery.encryptedquery.encryption.ModPowAbstraction;
import org.enquery.encryptedquery.query.wideskies.Query;
import org.enquery.encryptedquery.query.wideskies.QueryInfo;
import org.enquery.encryptedquery.query.wideskies.QueryUtils;
import org.enquery.encryptedquery.response.wideskies.Response;
import org.enquery.encryptedquery.schema.query.QuerySchema;
import org.enquery.encryptedquery.schema.query.QuerySchemaRegistry;
import org.enquery.encryptedquery.serialization.LocalFileSystemStore;
import org.enquery.encryptedquery.utils.KeyedHash;
import org.enquery.encryptedquery.utils.SystemConfiguration;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to perform stand alone responder functionalities
 * <p>
 * Used primarily for testing, although it can be used anywhere in standalone mode
 * <p>
 * Does not bound the number of hits that can be returned per selector
 * <p>
 * Does not use the DataFilter class -- assumes all filtering happens before calling addDataElement()
 * <p>
 * NOTE: Only uses in expLookupTables that are contained in the Query object, not in hdfs as this is a standalone responder
 */
public class Responder
{
  private static final Logger logger = LoggerFactory.getLogger(Responder.class);

  private Query query = null;
  private QueryInfo queryInfo = null;
  private QuerySchema qSchema = null;
  private int dataPartitionBitSize = 8;

  private Response response = null;

  private TreeMap<Integer,BigInteger> columns = null; // the column values for the PIR calculations

  private ArrayList<Integer> rowColumnCounters; // keeps track of how many hit partitions have been recorded for each row/selector

  public Responder(Query queryInput)
  {
    query = queryInput;
    queryInfo = query.getQueryInfo();
    String queryType = queryInfo.getQueryType();
    dataPartitionBitSize = queryInfo.getDataPartitionBitSize();

    if (SystemConfiguration.getBooleanProperty("pir.allowAdHocQuerySchemas", false))
    {
      qSchema = queryInfo.getQuerySchema();
    }
    if (qSchema == null)
    {
      qSchema = QuerySchemaRegistry.get(queryType);
    }

    response = new Response(queryInfo);

    // Columns are allocated as needed, initialized to 1
    columns = new TreeMap<>();

    // Initialize row counters
    rowColumnCounters = new ArrayList<>();
    for (int i = 0; i < Math.pow(2, queryInfo.getHashBitSize()); ++i)
    {
      rowColumnCounters.add(0);
    }
  }

  public Response getResponse()
  {
    return response;
  }

  public TreeMap<Integer,BigInteger> getColumns()
  {
    return columns;
  }

  /**
   * Method to compute the standalone response
   * <p>
   * Assumes that the input data is a single file in the local filesystem and is fully qualified
   */
  public void computeStandaloneResponse() throws IOException
  {
    // Read in data, perform query
    String inputData = SystemConfiguration.getProperty("pir.inputData");
    try
    {
      BufferedReader br = new BufferedReader(new FileReader(inputData));

      String line;
      JSONParser jsonParser = new JSONParser();
      logger.info("Reading and processing datafile...");
      int lineCounter = 0;
      while ((line = br.readLine()) != null)
      {
        //logger.debug("line = " + line);
        JSONObject jsonData = (JSONObject) jsonParser.parse(line);

        //logger.debug("jsonData = " + jsonData.toJSONString());

        String selector = QueryUtils.getSelectorByQueryTypeJSON(qSchema, jsonData);
        addDataElement(selector, jsonData);
        lineCounter++;
        if ( (lineCounter % 1000) == 0) {
        	logger.info("Processed {} records so far...", lineCounter);
        }
      }
      br.close();
      logger.info("Processed {} total records", lineCounter -1);
    } catch (Exception e)
    {
      e.printStackTrace();
    }

    // Set the response object, extract, write to file
    String outputFile = SystemConfiguration.getProperty("pir.outputFile");
    setResponseElements();
    new LocalFileSystemStore().store(outputFile, response);
  }

  /**
   * Method to add a data element associated with the given selector to the Response
   * <p>
   * Assumes that the dataMap contains the data in the schema specified
   * <p>
   * Initialize Paillier ciphertext values Y_i to 1 (as needed -- column values as the # of hits grows)
   * <p>
   * Initialize 2^hashBitSize counters: c_t = 0, 0 <= t <= (2^hashBitSize - 1)
   * <p>
   * For selector T:
   * <p>
   * For data element D, split D into partitions of size partitionSize-many bits:
   * <p>
   * D = D_0 || ... ||D_{\ceil{bitLength(D)/partitionSize} - 1)}
   * <p>
   * Compute H_k(T); let E_T = query.getQueryElement(H_k(T)).
   * <p>
   * For each data partition D_i:
   * <p>
   * Compute/Update:
   * <p>
   * Y_{i+c_{H_k(T)}} = (Y_{i+c_{H_k(T)}} * ((E_T)^{D_i} mod N^2)) mod N^2 ++c_{H_k(T)}
   * 
   */
  public void addDataElement(String selector, JSONObject jsonData) throws Exception
  {
    // Extract the data from the input record into byte chunks based on the query type
    List<BigInteger> inputData = QueryUtils.partitionDataElement(qSchema, jsonData, queryInfo.getEmbedSelector());
    List<BigInteger> hitValPartitions = new ArrayList<BigInteger>();
    
    // determine how many bytes per partition based on the dataPartitionBitSize
    // dataPartitionBitSize needs to be a multiple of 8 as we are using UTF-8 and we do not want to split a byte.
    int bytesPerPartition = 1;
    if (( dataPartitionBitSize % 8 ) == 0 ) {
    	bytesPerPartition = dataPartitionBitSize / 8 ;
    }
    else {
    	logger.error("dataPartitionBitSize must be a multiple of 8 !! {}", dataPartitionBitSize);
    }
	logger.debug("bytesPerPartition {}", bytesPerPartition);
    if (bytesPerPartition > 1) {
        byte[] tempByteArray = new byte[bytesPerPartition];
        int j = 0;
    	for (int i = 0; i < inputData.size(); i++) {
           if (j < bytesPerPartition) {
               tempByteArray[j] = inputData.get(i).byteValue();
           } else {
        	   BigInteger bi = new BigInteger(1, tempByteArray);
        	   hitValPartitions.add(bi);
               //logger.debug("Part added {}", bi.toString(16));
        	   j = 0;
               tempByteArray[j] = inputData.get(i).byteValue();
           }
           j++;
        }
        if (j <= bytesPerPartition ) {
        	while (j < bytesPerPartition) {
    	    	tempByteArray[j] = new Byte("0");
    	    	j++;
        	}
       	    BigInteger bi = new BigInteger(1, tempByteArray);
        	hitValPartitions.add( bi );
         	//logger.debug("Part added {}", bi.toString(16));
        }
    } else {  // Since there is only one byte per partition lets avoid the extra work
      hitValPartitions = inputData;
    }
    //int index = 1;
    //for (BigInteger bi : hitValPartitions) {
    //  logger.debug("Part {} BigInt {} / Byte {}", index, bi.toString(), bi.toString(16) );
    //  index++;
    //}
    
    // Pull the necessary elements
    int rowIndex = KeyedHash.hash(queryInfo.getHashKey(), queryInfo.getHashBitSize(), selector);
    int rowCounter = rowColumnCounters.get(rowIndex);
    BigInteger rowQuery = query.getQueryElement(rowIndex);

    //logger.debug("hitValPartitions.size() = " + hitValPartitions.size() + " rowIndex = " + rowIndex + " rowCounter = " + rowCounter + " rowQuery = "
    //    + rowQuery.toString() + " pirWLQuery.getNSquared() = " + query.getNSquared().toString());

    // Update the associated column values
    for (int i = 0; i < hitValPartitions.size(); ++i)
    {
      if (!columns.containsKey(i + rowCounter))
      {
        columns.put(i + rowCounter, BigInteger.valueOf(1));
      }
      BigInteger column = columns.get(i + rowCounter); // the next 'free' column relative to the selector
      //logger.debug("Before: columns.get(" + (i + rowCounter) + ") = " + columns.get(i + rowCounter));

      BigInteger exp;
      if (query.getQueryInfo().useExpLookupTable() && !query.getQueryInfo().useHDFSExpLookupTable()) // using the standalone
      // lookup table
      {
        exp = query.getExp(rowQuery, hitValPartitions.get(i).intValue());
      }
      else
      // without lookup table
      {
        //logger.debug("i = " + i + " hitValPartitions.get(i).intValue() = " + hitValPartitions.get(i).intValue());
        exp = ModPowAbstraction.modPow(rowQuery, hitValPartitions.get(i), query.getNSquared());
      }
      column = (column.multiply(exp)).mod(query.getNSquared());

      columns.put(i + rowCounter, column);

      //logger.debug(
      //    "exp = " + exp + " i = " + i + " partition = " + hitValPartitions.get(i) + " = " + hitValPartitions.get(i).toString(2) + " column = " + column);
      //logger.debug("After: columns.get(" + (i + rowCounter) + ") = " + columns.get(i + rowCounter));
    }

    // Update the rowCounter (next free column position) for the selector
    rowColumnCounters.set(rowIndex, (rowCounter + hitValPartitions.size()));
    //logger.debug("rowIndex {} next column is {}", rowIndex, rowColumnCounters.get(rowIndex));
  }

  // Sets the elements of the response object that will be passed back to the
  // querier for decryption
  public void setResponseElements()
  {
    //logger.debug("numResponseElements = " + columns.size());
    // for(int key: columns.keySet())
    // {
    // logger.debug("key = " + key + " column = " + columns.get(key));
    // }

    response.setResponseElements(columns);
  }
}
