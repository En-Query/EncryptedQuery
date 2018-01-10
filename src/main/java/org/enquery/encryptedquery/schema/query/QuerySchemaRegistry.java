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
package org.enquery.encryptedquery.schema.query;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class QuerySchemaRegistry
{
  // The registry. Maps schema name to query schema.
  private static final Map<String,QuerySchema> registry = new HashMap<>();

  // Not designed to be instantiated.
  QuerySchemaRegistry()
  {

  }

  /**
   * Adds the given query schema to the registry.
   * <p>
   * If there was an existing schema with the same name, it is replaced.
   *
   * @param schema
   *          The query schema to add.
   * @return the previous schema registered at the same name, or <code>null</code> if there were none.
   */
  public static QuerySchema put(QuerySchema schema)
  {
    return registry.put(schema.getSchemaName(), schema);
  }

  /**
   * Returns the query schema with the given name.
   *
   * @param schemaName
   *          The query schema name to be returned.
   * @return The query schema, or <code>null</code> if no such schema.
   */
  public static QuerySchema get(String schemaName)
  {
    return registry.get(schemaName);
  }

  /**
   * Returns the set of query schema names held in the registry.
   *
   * @return The possibly empty set of query schema names.
   */
  public static Set<String> getNames()
  {
    return registry.keySet();
  }

  /**
   * Clear the registry
   */
  public static void clearRegistry()
  {
    registry.clear();
  }
}
