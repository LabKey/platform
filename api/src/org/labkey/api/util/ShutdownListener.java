package org.labkey.api.util;

import javax.servlet.ServletContextEvent;

/**
 * User: brittp
 * Date: Dec 3, 2005
 * Time: 5:15:42 PM
 */
public interface ShutdownListener
{
    void shutdownStarted(ServletContextEvent servletContextEvent);
}
