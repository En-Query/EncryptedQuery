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

package org.enquery.encryptedquery.responder.wideskies;


import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

import org.enquery.encryptedquery.responder.wideskies.spi.ResponderPlugin;
import org.enquery.encryptedquery.utils.PIRException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResponderService
{
  private static final Logger logger = LoggerFactory.getLogger(ResponderService.class);

  // Singleton for the responder service.
  private static ResponderService service;
  private ServiceLoader<ResponderPlugin> loader;

  public static synchronized ResponderService getInstance()
  {
    if (service == null)
    {
      service = new ResponderService();
    }
    return service;
  }

  private ResponderService()
  {
    loader = ServiceLoader.load(ResponderPlugin.class);
  }

  public ResponderPlugin getResponder(String platformName) throws PIRException
  {
    try
    {
      for (ResponderPlugin plugin : loader)
      {
        if (platformName.equalsIgnoreCase(plugin.getPlatformName()))
        {
          logger.debug("Found {}, in {}", platformName, plugin.getClass().getName());
          return plugin;
        }
      }
    } catch (ServiceConfigurationError e)
    {
      logger.error("ResponderPlugin configuration error {}", e);
      throw new PIRException(e);
    }
    return null;
  }

}
