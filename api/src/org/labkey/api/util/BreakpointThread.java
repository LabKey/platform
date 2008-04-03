package org.labkey.api.util;

import javax.servlet.ServletContextEvent;

/**
 * User: jeckels
 * Date: Oct 27, 2006
 */
public class BreakpointThread extends Thread implements ShutdownListener
{
    private boolean _shutdown = false;
    
    public BreakpointThread()
    {
        setDaemon(true);
        ContextListener.addShutdownListener(this);
    }

    public void run()
    {
        while (!_shutdown)
        {
            try
            {
                Thread.sleep(5000);
            }
            catch (InterruptedException e) {}
        }
    }

    public void shutdownStarted(ServletContextEvent servletContextEvent)
    {
        _shutdown = true;
        interrupt();
        try
        {
            join(2000);
        }
        catch (InterruptedException e) {}
    }
}
