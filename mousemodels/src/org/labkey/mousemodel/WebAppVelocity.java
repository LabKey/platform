/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
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
package org.labkey.mousemodel;

import java.util.Properties;

import org.apache.log4j.Logger;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.runtime.RuntimeConstants;

public class WebAppVelocity
{
    private static Logger _log = Logger.getLogger(WebAppVelocity.class);
    private static Boolean _velocityInited = Boolean.FALSE;

    static
    {
        initVelocity();
    }

    public static void initVelocity()
    {
        synchronized (_velocityInited)
        {
            if (_velocityInited.booleanValue())
                return;

            String logFile = null;
            try
            {
                String slash = System.getProperty("file.separator");
                logFile = System.getProperty("catalina.home") + slash + "logs" + slash + "velocity.log";
                _log.debug("Velocity log file name = " + logFile);
            }
            catch (Exception e)
            {
                _log.error("Error getting system properties...");
            }

            Properties p = new Properties();
            p.setProperty("resource.loader", "web");
            p.setProperty("web.resource.loader.description", "Velocity WebApp Resource Loader");
            p.setProperty("web.resource.loader.class", "org.labkey.mousemodel.WebAppResourceLoader");
            p.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS,
                    "org.apache.velocity.runtime.log.SimpleLog4JLogSystem");
            if (null != logFile)
                p.setProperty(RuntimeConstants.RUNTIME_LOG, logFile);

            try
            {
                Velocity.init(p);
                _velocityInited = Boolean.TRUE;
            }
            catch (Exception e)
            {
                _log.error("Couldn't initialize velocity", e);
            }
        }
    }
}
