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
package org.enquery.encryptedquery.test.utils;

import org.enquery.encryptedquery.querier.wideskies.EncryptionPropertiesBuilder;
import org.enquery.encryptedquery.querier.wideskies.Querier;
import org.enquery.encryptedquery.querier.wideskies.QuerierConst;
import org.enquery.encryptedquery.querier.wideskies.QuerierFactory;
import org.enquery.encryptedquery.querier.wideskies.decrypt.DecryptResponse;
import org.enquery.encryptedquery.query.wideskies.Query;
import org.enquery.encryptedquery.query.wideskies.QueryUtils;
import org.enquery.encryptedquery.responder.wideskies.standalone.Responder;
import org.enquery.encryptedquery.response.wideskies.Response;
import org.enquery.encryptedquery.schema.query.QuerySchema;
import org.enquery.encryptedquery.schema.query.QuerySchemaRegistry;
import org.enquery.encryptedquery.schema.response.QueryResponseJSON;
import org.enquery.encryptedquery.serialization.LocalFileSystemStore;
import org.enquery.encryptedquery.utils.PIRException;
import org.enquery.encryptedquery.utils.QueryResultsWriter;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.fail;

public class StandaloneQuery
{
  private static final Logger logger = LoggerFactory.getLogger(StandaloneQuery.class);
  public static final String QUERY_SIDE_OUPUT_FILE_PREFIX = "querySideOut";

  /**
   * A helper method to create a querier from a query type string and a list of selectors. Used in tests.
   * @param queryType A string describing the query schema for this querier.
   * @param selectors A list of selectors for the querier.
   * @return A {@link Querier} representing the submitted information.
   * @throws PIRException
   * @throws InterruptedException
   */
  public static Querier createQuerier(String queryType, List<String> selectors) throws PIRException, InterruptedException
  {
    Properties baseTestEncryptionProperties = EncryptionPropertiesBuilder.newBuilder().dataPartitionBitSize(BaseTests.dataPartitionBitSize)
        .hashBitSize(BaseTests.hashBitSize).paillierBitSize(BaseTests.paillierBitSize).certainty(BaseTests.certainty)
        .queryType(queryType).build();
    return QuerierFactory.createQuerier(BaseTests.queryIdentifier, selectors, baseTestEncryptionProperties);
  }

  // Base method to perform the query
  public static List<QueryResponseJSON> performStandaloneQuery(List<JSONObject> dataElements, String queryType, List<String> selectors, int numThreads,
      boolean testFalsePositive) throws IOException, InterruptedException, PIRException
  {

    logger.info("Performing watchlisting: ");

    QuerySchema qSchema = QuerySchemaRegistry.get(queryType);

    // Create the necessary files
    LocalFileSystemStore storage = new LocalFileSystemStore();
    File fileQuerier = File.createTempFile(QUERY_SIDE_OUPUT_FILE_PREFIX + "-" + QuerierConst.QUERIER_FILETAG, ".txt");
    File fileQuery = File.createTempFile(QUERY_SIDE_OUPUT_FILE_PREFIX + "-" + QuerierConst.QUERY_FILETAG, ".txt");
    String responseFile = "encryptedResponse";
    File fileResponse = File.createTempFile(responseFile, ".txt");
    String finalResultsFile = "finalResultFile";
    File fileFinalResults = File.createTempFile(finalResultsFile, ".txt");

    logger.info("fileQuerier = " + fileQuerier.getAbsolutePath() + " fileQuery  = " + fileQuery.getAbsolutePath() + " responseFile = "
        + fileResponse.getAbsolutePath() + " fileFinalResults = " + fileFinalResults.getAbsolutePath());

    Querier querier = createQuerier(queryType, selectors);
    logger.info("Completed encryption of the selectors - completed formation of the encrypted query vectors:");

    // Dork with the embedSelectorMap to generate a false positive for the last valid selector in selectors
    if (testFalsePositive)
    {
      Map<Integer,String> embedSelectorMap = querier.getEmbedSelectorMap();
      logger.info("embedSelectorMap((embedSelectorMap.size()-2)) = " + embedSelectorMap.get((embedSelectorMap.size() - 2)) + " selector = "
          + selectors.get((embedSelectorMap.size() - 2)));
      embedSelectorMap.put((embedSelectorMap.size() - 2), "fakeEmbeddedSelector");
    }

    // Write necessary output files
    storage.store(fileQuerier, querier);
    storage.store(fileQuery, querier.getQuery());

    // Perform the PIR query and build the response elements
    logger.info("Performing the PIR Query and constructing the response elements:");
    Query query = storage.recall(fileQuery, Query.class);
    Responder pirResponder = new Responder(query);
    logger.info("Query and Responder elements constructed");
    for (JSONObject jsonData : dataElements)
    {
      String selector = QueryUtils.getSelectorByQueryTypeJSON(qSchema, jsonData);
      logger.info("selector = " + selector + " numDataElements = " + jsonData.size());
      try
      {
//        pirResponder.addDataElement(selector, jsonData);
      } catch (Exception e)
      {
        fail(e.toString());
      }
    }
    logger.info("Completed the PIR Query and construction of the response elements:");

    // Set the response object, extract, write to file
    logger.info("Forming response from response elements; writing to a file");
//    pirResponder.setResponseElements();
//    Response responseOut = pirResponder.getResponse();
//    storage.store(fileResponse, responseOut);
    logger.info("Completed forming response from response elements and writing to a file");

    // Perform decryption
    // Reconstruct the necessary objects from the files
    logger.info("Performing decryption; writing final results file");
    Response responseIn = storage.recall(fileResponse, Response.class);
    querier = storage.recall(fileQuerier, Querier.class);

    // Perform decryption and output the result file
    DecryptResponse decryptResponse = new DecryptResponse(responseIn, querier);
    QueryResultsWriter.writeResultFile(fileFinalResults, decryptResponse.decrypt(numThreads));
    logger.info("Completed performing decryption and writing final results file");

    // Read in results
    logger.info("Reading in and checking results");
    List<QueryResponseJSON> results = TestUtils.readResultsFile(fileFinalResults);

    // Clean up
    fileQuerier.delete();
    fileQuery.delete();
    fileResponse.delete();
    fileFinalResults.delete();

    return results;
  }
}
