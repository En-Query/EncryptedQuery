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


import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

public class JavaSerializer extends SerializationService
{

  /**
   * Stores the given object on the given stream using Java serialization.
   * 
   * @param outputStream
   *          The stream on which to store the object.
   * @param obj
   *          The object to be stored.
   * @throws IOException
   *           If a problem occurs storing the object on the given stream.
   */

  public void write(OutputStream outputStream, Storable obj) throws IOException
  {
    ObjectOutputStream oos = new ObjectOutputStream(outputStream);
    oos.writeObject(obj);
  }

  /**
   * Read an object from the given stream of the given type.
   * 
   * @param inputStream
   *          The stream from which to read the object.
   * @param classType
   *          The type of object being retrieved.
   * @throws IOException
   *           If a problem occurs reading the object from the stream.
   */
  @SuppressWarnings("unchecked")
  public <T> T read(InputStream inputStream, Class<T> classType) throws IOException
  {
    try (ObjectInputStream oin = new ObjectInputStream(inputStream))
    {
      return (T) oin.readObject();
    } catch (ClassNotFoundException e)
    {
      throw new RuntimeException(e);
    }
  }
}