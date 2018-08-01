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
package org.enquery.encryptedquery.schema.data.partitioner;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.List;

import org.enquery.encryptedquery.utils.PIRException;

/**
 * Interface for data partitioning
 * <p>
 * All custom object partitioners must implement this interface
 */
public interface DataPartitioner extends Serializable
{
  /**
   * Method to partition the given Object into a List of BigInteger partition elements given its type identifier.
   * <p>
   * If the Object does not have/need a specific type identifier, use null.
   */
  List<Byte> toPartitions(Object object, String type) throws PIRException;

  /**
   * Method to reconstruct an Object given a List of its BigInteger partition elements and its type identifier.
   * <p>
   * If the Object does not have/need a specific type identifier, use null.
   */
  Object fromPartitions(List<Byte> parts, int partsIndex, String type) throws PIRException;

  /**
   * Returns the number of bits of an object with the given type.
   */
  int getBits(String type) throws PIRException;

  /**
   * Creates partitions for an array of the same type of elements - used when a data value field is an array and we wish to encode these into the return value.
   */
  List<Byte> arrayToPartitions(List<?> elementList, String type) throws PIRException;

  /**
   * Method to get an empty set of partitions by data type - used for padding return array values.
   */
  List<Byte> getPaddedPartitions(String type) throws PIRException;

  /**
   * Method to get the number of partitions of the data object given the type.
   */
  int getNumPartitions(String type) throws PIRException;
}
