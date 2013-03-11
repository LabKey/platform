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

import org.apache.log4j.Appender;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.RollingFileAppender;
import org.apache.log4j.xml.DOMConfigurator;

import java.io.InputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Properties;

/**
* User: jeckels
* Date: Jul 18, 2008
*/
public class LoggerUtil
{
    public static void initLogging(String classloaderResource) throws IOException
    {
        InputStream in = null;
        try
        {
            if (classloaderResource.toLowerCase().endsWith(".properties"))
            {
                in = LoggerUtil.class.getClassLoader().getResourceAsStream(classloaderResource);
                Properties props = new Properties();
                props.load(in);
                PropertyConfigurator.configure(props);
            }
            else
            {
                DOMConfigurator.configure(LoggerUtil.class.getClassLoader().getResource(classloaderResource));
            }
        }
        finally
        {
            if (in != null) { try { in.close(); } catch (IOException e) {} }
        }
    }

    /** We want to roll the file every time the server starts, which isn't directly supported by Log4J so we do it manually */
    public static void rollErrorLogFile(Logger logger)
    {
        while (logger != null && !logger.getAllAppenders().hasMoreElements())
        {
            logger = (Logger)logger.getParent();
        }

        if (logger == null)
        {
            return;
        }

        for (Enumeration e2 = logger.getAllAppenders(); e2.hasMoreElements();)
        {
            final Appender appender = (Appender)e2.nextElement();
            if (appender instanceof RollingFileAppender && "ERRORS".equals(appender.getName()))
            {
                RollingFileAppender rfa = (RollingFileAppender)appender;
                rfa.rollOver();
            }
        }
    }

}