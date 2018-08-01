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

import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.enquery.encryptedquery.query.wideskies.QueryInfo;
import org.enquery.encryptedquery.responder.wideskies.common.ColumnBasedResponderProcessor;
import org.enquery.encryptedquery.responder.wideskies.common.QueueRecord;

//Abstract class DAO Factory
public abstract class DAOFactory {

// List of DAO types supported by the factory
public static final int MYSQL = 1;
public static final int MARIADB = 2;
public static final int ORACLE = 3;
public static final int MONGO = 4;


// There will be a method for each DAO that can be 
// created. The concrete factories will have to 
// implement these methods.
public abstract long loadResponderDataDAO(QueryInfo queryInfo, String queryStatement, int hashGroupSize, List<ConcurrentLinkedQueue<QueueRecord>> queues, List<ColumnBasedResponderProcessor> processors);
public abstract void closeConnection();
public abstract Boolean isConnected();
public abstract long getRecordsLoaded();

public static DAOFactory getDAOFactory(
   int whichFactory, Properties props) {

 switch (whichFactory) {
   case MYSQL: 
   case MARIADB:
       return new MySQLDAOFactory(props);
   case ORACLE    : 
//       return new OracleDAOFactory(props);      
   case MONGO    : 
//       return new MongoDAOFactory(props);
   default           : 
       return null;
 }
}
}