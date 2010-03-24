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
