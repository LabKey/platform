/*
 * Copyright (c) 2008-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.pipeline.mule;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.properties.PropertiesConfiguration;
import org.apache.logging.log4j.core.config.properties.PropertiesConfigurationFactory;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;

/**
* User: jeckels
* Date: Jul 18, 2008
*/
public class LoggerUtil
{
    public static void initLogging(String name, String classloaderResource) throws IOException
    {
        InputStream in = null;
        try
        {
            if (classloaderResource.toLowerCase().endsWith(".properties"))
            {
                ConfigurationSource source = new ConfigurationSource(new FileInputStream(classloaderResource));
                PropertiesConfigurationFactory factory = new PropertiesConfigurationFactory();
                LoggerContext context = (LoggerContext) LogManager.getContext();
                PropertiesConfiguration propertiesConfiguration = factory.getConfiguration(context, source);
                context.setConfiguration(propertiesConfiguration);
                Configurator.initialize(propertiesConfiguration);
            }
            else
            {
                Configurator.initialize(name, LoggerUtil.class.getClassLoader(), classloaderResource);
            }
        }
        finally
        {
            if (in != null) { try { in.close(); } catch (IOException e) {} }
        }
    }

}