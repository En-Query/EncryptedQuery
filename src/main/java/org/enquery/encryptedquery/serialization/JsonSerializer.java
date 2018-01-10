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
package org.enquery.encryptedquery.serialization;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;

import org.enquery.encryptedquery.querier.wideskies.Querier;
import org.enquery.encryptedquery.querier.wideskies.QuerierDeserializer;

public class JsonSerializer extends SerializationService
{
  public static final Gson gson = new GsonBuilder()
      .registerTypeAdapter(Querier.class, new QuerierDeserializer()).setPrettyPrinting()
      .excludeFieldsWithoutExposeAnnotation().serializeNulls().create();

  /**
   * Stores the given object on the output stream as JSON.
   *
   * @param outputStream
   *          The stream on which to store the object.
   * @param obj
   *          The object to be stored.
   * @throws IOException
   *           If a problem occurs storing the object on the given stream.
   */
  @Override
  public void write(OutputStream outputStream, Storable obj) throws IOException
  {
    Writer writer = new OutputStreamWriter(outputStream);
    gson.toJson(obj, obj.getClass(), writer);
    writer.close();
  }

  /**
   * Read a JSON string from the given input stream and returns the Object representation.
   *
   * @param inputStream
   *          The stream from which to read the object.
   * @param classType
   *          The type of object being retrieved.
   * @throws IOException
   *           If a problem occurs reading the object from the stream.
   */
  @Override
  public <T> T read(InputStream inputStream, Class<T> classType) throws IOException
  {
    Reader reader = new InputStreamReader(inputStream);
    return gson.fromJson(reader, classType);

  }

}
