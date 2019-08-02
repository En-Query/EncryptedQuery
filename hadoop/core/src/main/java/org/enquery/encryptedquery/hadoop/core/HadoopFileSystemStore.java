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
import java.io.OutputStream;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

public class HadoopFileSystemStore extends StorageService
{

  private FileSystem hadoopFileSystem;

  // Prevents others from using default constructor.
  HadoopFileSystemStore()
  {
    super();
  }

  /**
   * Creates a new storage service on the given HDFS file system using default Json serialization.
   */
  public HadoopFileSystemStore(FileSystem fs)
  {
    super();
    hadoopFileSystem = fs;
  }

  /**
   * Creates a new storage service on the given HDFS file system using the given serializer
   */
  public HadoopFileSystemStore(FileSystem fs, SerializationService serial)
  {
    super(serial);
    hadoopFileSystem = fs;
  }

  /**
   * Store the given object into the HDFS file system at the given path name.
   * 
   * @param pathName
   *          The location to store the object.
   * @param value
   *          The object to store.
   * @throws IOException
   *           If a problem occurs storing the object.
   */
  public void store(String pathName, Storable value) throws IOException
  {
    store(new Path(pathName), value);
  }

  /**
   * Store the given object at into the HDFS file system at the given path.
   * 
   * @param path
   *          The HDFS path descriptor.
   * @param obj
   *          The object to store.
   * @throws IOException
   *           If a problem occurs storing the object at the given path.
   */
  public void store(Path path, Storable obj) throws IOException
  {
    try (OutputStream os = hadoopFileSystem.create(path))
    {
      serializer.write(os, obj);
    }
  }

  /**
   * Retrieves the object stored at the given path name in HDFS.
   * 
   * @param pathName
   *          The path name where the object is stored.
   * @param type
   *          The type of object being retrieved.
   * @return The object stored at that path name.
   * @throws IOException
   *           If a problem occurs retrieving the object.
   */
  public <T> T recall(String pathName, Class<T> type) throws IOException
  {
    return recall(new Path(pathName), type);
  }

  /**
   * Retrieves the object stored at the given path in HDFS.
   * 
   * @param path
   *          The HDFS path descriptor to the object.
   * @param type
   *          The type of object being retrieved.
   * @return The object stored at that path.
   * @throws IOException
   *           If a problem occurs retrieving the object.
   */
  public <T> T recall(Path path, Class<T> type) throws IOException
  {
    try (InputStream is = hadoopFileSystem.open(path))
    {
      return serializer.read(is, type);
    }
  }
}