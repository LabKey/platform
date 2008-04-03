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
package org.labkey.api.util;

import org.apache.log4j.Logger;
import org.springframework.web.context.ContextLoaderListener;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.util.ArrayList;
import java.util.List;

public class ContextListener implements ServletContextListener
{
    private static Logger _log = Logger.getLogger(ContextListener.class);
    private static final List<ShutdownListener> _shutdownListeners = new ArrayList<ShutdownListener>();
    private static final List<StartupListener> _startupListeners = new ArrayList<StartupListener>();
    private static ContextLoaderListener _springContextListener;

    public void contextInitialized(ServletContextEvent servletContextEvent)
    {
        getSpringContextListener().contextInitialized(servletContextEvent);
    }

    public void contextDestroyed(ServletContextEvent servletContextEvent)
    {
        List<ShutdownListener> shutdownListeners;
        synchronized (_shutdownListeners)
        {
             shutdownListeners = new ArrayList<ShutdownListener>(_shutdownListeners);
        }
        for (ShutdownListener listener : shutdownListeners)
        {
            try
            {
                listener.shutdownStarted(servletContextEvent);
            }
            catch (Exception e)
            {
                _log.error(e);
            }
        }
        getSpringContextListener().contextDestroyed(servletContextEvent);
        org.apache.log4j.LogManager.shutdown();
        org.apache.log4j.LogManager.resetConfiguration();
        org.apache.commons.beanutils.PropertyUtils.clearDescriptors();
        org.apache.commons.beanutils.ConvertUtils.deregister();
        java.beans.Introspector.flushCaches();
    }

    public static void addShutdownListener(ShutdownListener listener)
    {
        synchronized (_shutdownListeners)
        {
            _shutdownListeners.add(listener);
        }
    }

    public static void removeShutdownListener(ShutdownListener listener)
    {
        synchronized (_shutdownListeners)
        {
            _shutdownListeners.remove(listener);
        }
    }

    public static void moduleStartuComplete(ServletContext servletContext)
    {
        synchronized (_shutdownListeners)
        {
            for (StartupListener listener : _startupListeners)
                listener.moduleStartupComplete(servletContext);
        }
    }

    public static void addStartupListener(StartupListener listener)
    {
        synchronized (_startupListeners)
        {
            _startupListeners.add(listener);
        }
    }

    public static void removeStartupListener(StartupListener listener)
    {
        synchronized (_startupListeners)
        {
            _startupListeners.remove(listener);
        }
    }

    public static synchronized ContextLoaderListener getSpringContextListener()
    {
        if (_springContextListener == null)
            _springContextListener = new ContextLoaderListener();
        return _springContextListener;
    }
}
