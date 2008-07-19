/*
 * Copyright (c) 2008 LabKey Corporation
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

import org.apache.log4j.PropertyConfigurator;

import java.io.InputStream;
import java.io.IOException;
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
            in = LoggerUtil.class.getClassLoader().getResourceAsStream(classloaderResource);
            Properties props = new Properties();
            props.load(in);
            PropertyConfigurator.configure(props);
        }
        finally
        {
            if (in != null) { try { in.close(); } catch (IOException e) {} }
        }
    }
}