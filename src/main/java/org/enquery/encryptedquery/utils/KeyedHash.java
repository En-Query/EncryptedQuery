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
 * Computes the hash as the bitwise AND of a mask and MD5(key ||
 * input).  The MD5 algorithm is assumed to be supported on all Java
 * platforms.
 * 
 */
public class KeyedHash
{
  private static final String HASH_METHOD = "MD5";
  private static final Logger logger = LoggerFactory.getLogger(KeyedHash.class);

  /**
   * Hash method that uses the java String hashCode()
   */
  public static int hash(String key, int bitSize, String input)
  {
    int fullHash = 0;
    try {
      MessageDigest md = MessageDigest.getInstance(HASH_METHOD);
      md.update(key.getBytes());
      byte[] array = md.digest(input.getBytes());

      fullHash = fromByteArray(array);
    } catch (NoSuchAlgorithmException e) {
      // This is not expected to happen, as every implementation of
      // the Java platform is required to implement MD5.
      logger.debug("failed to compute a hash of type " + HASH_METHOD);
      throw new UnsupportedOperationException("failed to compute a hash of type " + HASH_METHOD);
    }

    // Take only the lower bitSize-many bits of the resultant hash
    int bitLimitedHash = fullHash;
    if (bitSize < 32)
    {
      bitLimitedHash = (0xFFFFFFFF >>> (32 - bitSize)) & fullHash;
    }

    return bitLimitedHash;
  }

  private static int fromByteArray(byte[] bytes)
  {
    return bytes[0] << 24 | (bytes[1] & 0xFF) << 16 | (bytes[2] & 0xFF) << 8 | (bytes[3] & 0xFF);
  }
}
