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
package org.enquery.encryptedquery.utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class for the PIR keyed hash
 * <p>
 * Defaults to SHA-256; can optionally choose MD5, SHA-1, or java .hashCode()
 * 
 */
public class KeyedHash
{
  private static final Logger logger = LoggerFactory.getLogger(KeyedHash.class);

  /**
   * Hash method that uses the java String hashCode()
   */
  public static int hash(String key, int bitSize, String input)
  {
	  int fullHash = 0;
	  try {
         MessageDigest md = MessageDigest.getInstance("SHA-256");
         byte[] array = md.digest(input.getBytes());

         fullHash = fromByteArray(array);
	  } catch (Exception e) {
	      logger.info(e.toString());
	      //This hash method works, but can create a lot of duplicate hashs if using a small hashbit size(bitsize) .
  	      fullHash = (key + input).hashCode();
	  
	  }

    // Take only the lower bitSize-many bits of the resultant hash
    int bitLimitedHash = fullHash;
    if (bitSize < 32)
    {
      bitLimitedHash = (0xFFFFFFFF >>> (32 - bitSize)) & fullHash;
    }

    return bitLimitedHash;
  }

  /**
   * Hash method to optionally specify a hash type other than the default java hashCode() hashType must be MD5, SHA-1, or SHA-256
   * 
   */
  public static int hash(String key, int bitSize, String input, String hashType)
  {
    int bitLimitedHash;

    try
    {
      MessageDigest md = MessageDigest.getInstance(hashType);
      byte[] array = md.digest(input.getBytes());

      int hashInt = fromByteArray(array);
      bitLimitedHash = hashInt;
      if (bitSize < 32)
      {
        bitLimitedHash = (0xFFFFFFFF >>> (32 - bitSize)) & hashInt;
      }
      logger.debug("hashType = " + hashType + " key = " + key + " hashInt = " + hashInt + " bitLimitedHash = " + bitLimitedHash + " Selector = " + input);

    } catch (NoSuchAlgorithmException e)
    {
      logger.info(e.toString());
      bitLimitedHash = hash(key, bitSize, input);
    }

    return bitLimitedHash;
  }

  private static int fromByteArray(byte[] bytes)
  {
    return bytes[0] << 24 | (bytes[1] & 0xFF) << 16 | (bytes[2] & 0xFF) << 8 | (bytes[3] & 0xFF);
  }
}
