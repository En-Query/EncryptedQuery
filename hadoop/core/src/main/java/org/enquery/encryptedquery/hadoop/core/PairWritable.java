/*
 * EncryptedQuery is an open source project allowing user to query databases with queries under
 * homomorphic encryption to securing the query and results set from database owner inspection.
 * Copyright (C) 2018 EnQuery LLC
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program.
 * If not, see <https://www.gnu.org/licenses/>.
 */
package org.enquery.encryptedquery.hadoop.core;

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
