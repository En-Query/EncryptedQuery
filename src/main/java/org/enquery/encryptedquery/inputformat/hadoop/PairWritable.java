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
package org.enquery.encryptedquery.inputformat.hadoop;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import org.apache.hadoop.io.Writable;


/**
 *  Abstract class for representing a serializable pair of type <E1,
 *  E2> where E1 and E2 are specified {@code Writable} types.
 *  <p>
 *  This class is kept abstract as Hadoop requires {@code Writable}s
 *  that implement a zero-argument constructor, but such a constructor
 *  is not defined by the {@code Writable} interface.  Subclasses
 *  should implement such a constructor.
 */
public abstract class PairWritable<E1 extends Writable, E2 extends Writable> implements Writable
{
  private E1 first;
  private E2 second;

  /**
   *  Creates a pair initialized with the given storage objects.
   */
  public PairWritable(E1 first, E2 second)
  {
    this.first = first;
    this.second = second;
  }
  
  /**
   *  Returns the {@code Writable} corresponding to the first element
   *  of the pair.
   */
  public E1 getFirst()
  {
    return this.first;
  }

  /**
   *  Returns the {@code Writable} corresponding to the second element
   *  of the pair.
   */
  public E2 getSecond()
  {
    return this.second;
  }

  /**
   *  Sets the storage object corresponding to the first element of
   *  the pair.
   */
  public void setFirst(E1 first)
  {
    this.first = first;
  }

  /**
   *  Sets the storage object corresponding to the second element of
   *  the pair.
   */
  public void setSecond(E2 second)
  {
    this.second = second;
  }

  /**
   *  Deserializes this pair from {@code in}.
   */
  public void readFields(DataInput in) throws IOException
  {
    first.readFields(in);
    second.readFields(in);
  }

  /**
   *  Serializes this pair to {@code out}.
   */
  public void write(DataOutput out) throws IOException
  {
    first.write(out);
    second.write(out);
  }
}
