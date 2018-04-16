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
 */
package org.enquery.encryptedquery.responder.wideskies.common;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

import org.enquery.encryptedquery.encryption.ModPowAbstraction;
import org.enquery.encryptedquery.utils.MontgomeryReduction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.Tuple2;


public interface ComputeEncryptedColumn
{
  public void insertDataPart(int rowIndex, BigInteger part);

  public void insertDataPart(BigInteger queryElement, BigInteger part);

  public BigInteger computeColumnAndClearData();
  
  public void clearData();
}
