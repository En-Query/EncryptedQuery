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
package org.enquery.encryptedquery.schema.query.filter;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.apache.hadoop.io.ArrayWritable;
import org.apache.hadoop.io.MapWritable;
import org.elasticsearch.hadoop.mr.WritableArrayWritable;
import org.enquery.encryptedquery.schema.data.DataSchema;
import org.enquery.encryptedquery.utils.StopListUtils;

/**
 * Filter class to filter data elements based upon a stoplist applied to specified field elements
 */
public class StopListFilter implements DataFilter
{
  private static final long serialVersionUID = 1L;

  private Set<String> filterSet = null;
  private Set<String> stopList = null;

  public StopListFilter(Set<String> filterSetIn, Set<String> stopListIn)
  {
    filterSet = filterSetIn;
    stopList = stopListIn;
  }

  @Override
  public boolean filterDataElement(MapWritable dataElement, DataSchema dSchema)
  {
    boolean passFilter = true;

    // If the data element contains a value on the stoplist (corresponding to a key in the filterSet), do not use
    for (String filterName : filterSet)
    {
      if (dSchema.isArrayElement(filterName))
      {
        List<String> elementArray = null;
        if (dataElement.get(dSchema.getTextName(filterName)) instanceof WritableArrayWritable)
        {
          elementArray = Arrays.asList(((WritableArrayWritable) dataElement.get(dSchema.getTextName(filterName))).toStrings());
        }
        else if (dataElement.get(dSchema.getTextName(filterName)) instanceof ArrayWritable)
        {
          elementArray = Arrays.asList(((ArrayWritable) dataElement.get(dSchema.getTextName(filterName))).toStrings());
        }

        if (elementArray != null && elementArray.size() > 0)
        {
          for (String element : elementArray)
          {
            passFilter = StopListUtils.checkElement(element, stopList);
            if (!passFilter)
            {
              break;
            }
          }
        }
      }
      else
      {
        String element = dataElement.get(dSchema.getTextName(filterName)).toString();
        passFilter = StopListUtils.checkElement(element, stopList);
      }
      if (!passFilter)
      {
        break;
      }
    }
    return passFilter;
  }
}
