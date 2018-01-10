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

import org.apache.hadoop.util.ToolRunner;
import org.enquery.encryptedquery.responder.wideskies.spi.ResponderPlugin;
import org.enquery.encryptedquery.utils.PIRException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to launch Map Reduce responder
 */
public class MapReduceResponder implements ResponderPlugin
{
  private static final Logger logger = LoggerFactory.getLogger(MapReduceResponder.class);

  @Override
  public String getPlatformName()
  {
    return "mapreduce";
  }

  @Override
  public void run() throws PIRException
  {
    logger.info("Launching MapReduce ResponderTool:");
    try
    {
      ComputeResponseTool pirWLTool = new ComputeResponseTool();
      ToolRunner.run(pirWLTool, new String[] {});
    } catch (Exception e)
    {
      // An exception occurred invoking the tool, don't know how to recover.
      throw new PIRException(e);
    }
  }
}
