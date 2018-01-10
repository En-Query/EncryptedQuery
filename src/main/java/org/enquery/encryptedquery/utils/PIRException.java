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

/**
 * Custom exception for the PIR utilities
 * 
 */
public class PIRException extends Exception
{
  private static final long serialVersionUID = 1L;

  public PIRException()
  {}

  public PIRException(String message)
  {
    super(message);
  }

  public PIRException(Throwable cause)
  {
    super(cause);
  }

  public PIRException(String message, Throwable cause)
  {
    super(message, cause);
  }
}
