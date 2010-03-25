/*
 * Copyright (c) 2010 LabKey Corporation
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
package org.labkey.api.util;

import org.apache.log4j.Logger;

import javax.servlet.ServletContextEvent;
import java.util.concurrent.atomic.AtomicLong;

/**
 * HeatBeatThread is faster than using System.currentTimeMillis() for
 * checking if some period of time has passed.
 *
 * http://dow.ngra.de/2008/10/27/when-systemcurrenttimemillis-is-too-slow/
 */
public class HeartBeatThread extends Thread implements ShutdownListener
{
    private static final Logger _log = Logger.getLogger(HeartBeatThread.class);

    public static volatile int counter = 0;

    static
    {
        new HeartBeatThread().start();
    }

    public HeartBeatThread()
    {
        setDaemon(true);
        setName(getClass().getSimpleName());
        ContextListener.addShutdownListener(this);
    }

    public void run()
    {
        while (true)
        {
            try
            {
                Thread.sleep(1000);
            }
            catch (InterruptedException e)
            {
                _log.debug(getName() + " interrupted");
            }

            counter++;
        }
    }

    public void shutdownPre(ServletContextEvent servletContextEvent)
    {
        interrupt();
    }

    public void shutdownStarted(ServletContextEvent servletContextEvent)
    {
    }
}
