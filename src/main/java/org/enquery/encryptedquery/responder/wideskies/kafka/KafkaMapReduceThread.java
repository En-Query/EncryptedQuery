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
package org.enquery.encryptedquery.responder.wideskies.kafka;

import org.apache.hadoop.util.ToolRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaMapReduceThread implements Runnable {

	private static final Logger logger = LoggerFactory.getLogger(KafkaMapReduceThread.class);
    private boolean stopMapReduce = false;
    private String outputFolder;
    private int iteration;
    
    public KafkaMapReduceThread( String workingFolder, int iteration ) {
    	this.outputFolder = workingFolder;
    	this.iteration = iteration;
    }
    
	public void stopListening() {
	     stopMapReduce = true;
	   }

	@Override
	public void run() {
		
		try
	    {
	      KafkaMapReduce pirWLTool = new KafkaMapReduce(outputFolder, iteration );
	      ToolRunner.run(pirWLTool, new String[] {});
	    } catch (Exception e)
	    {
	    	logger.error("Failed to execute Mapreducer for iteration {} error {}", iteration, e.getMessage());
	    }
	}
}
