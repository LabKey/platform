/*
 * Copyright (c) 2010-2016 LabKey Corporation
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

/**
 * HeatBeatThread is faster than using System.currentTimeMillis() for
 * checking if some period of time has passed.
 *
 * The "heart rate" of this counter is 1/sec.  Any attempt to measure elapsed 
 * time with this class will therefore be limited to +/- 1 sec accuracy.
 *
 * http://dow.ngra.de/2008/10/27/when-systemcurrenttimemillis-is-too-slow/
 */
public class HeartBeat
{
    public static int getCounter()
    {
        return counter;
    }

    /**
     * low resolution clock
     * @return system time with approx one second resolution
     */
    public static long currentTimeMillis()
    {
        return currentTimeMillis;
    }


    //
    // IMPL
    //

    private static final Logger _log = Logger.getLogger(HeartBeat.class);
    public static volatile int counter = 0;     // covers more than 60yrs uptime
    public static volatile long currentTimeMillis = System.currentTimeMillis();

    static
    {
        long ms = System.currentTimeMillis();
        currentTimeMillis = ms - (ms % 1000);
        new HeartBeatThread().start();
    }

    private static class HeartBeatThread extends Thread implements ShutdownListener
    {
        private volatile boolean _shutdown = false;

        HeartBeatThread()
        {
            setDaemon(true);
            setName(getClass().getSimpleName());
            ContextListener.addShutdownListener(this);
        }

        public void run()
        {
            while (!_shutdown)
            {
                try
                {
                    Thread.sleep(1000);
                }
                catch (InterruptedException e)
                {
                    _log.debug(getName() + " interrupted");
                }

                long ms = System.currentTimeMillis();
                ms -= ms % 1000;
                if (ms != currentTimeMillis)
                {
                    currentTimeMillis = ms;
                    counter++;
                }
            }
            //_log.debug("shutdown heartbeat");
        }

        public void shutdownPre()
        {
            _shutdown = true;
            interrupt();
        }

        public void shutdownStarted()
        {
        }
    }
}
