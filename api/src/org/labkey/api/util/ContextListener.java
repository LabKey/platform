/*
 * Copyright (c) 2005-2017 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.logging.LogFactory;
import org.apache.log4j.Logger;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.view.ViewServlet;
import org.springframework.web.context.ContextLoaderListener;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ContextListener implements ServletContextListener
{
    private static final Logger _log = Logger.getLogger(ContextListener.class);
    private static final List<ShutdownListener> _shutdownListeners = new CopyOnWriteArrayList<>();
    private static final List<StartupListener> _startupListeners = new CopyOnWriteArrayList<>();
    private static final ContextLoaderListener _springContextListener = new ContextLoaderListener();
    private static final List<NewInstallCompleteListener> _newInstallCompleteListeners = new CopyOnWriteArrayList<>();

    public void contextInitialized(ServletContextEvent servletContextEvent)
    {
        getSpringContextListener().contextInitialized(servletContextEvent);
    }

    public void contextDestroyed(ServletContextEvent servletContextEvent)
    {
        ViewServlet.setShuttingDown(0);

        callShutdownListeners();

        ViewServlet.setShuttingDown(1000);
        getSpringContextListener().contextDestroyed(servletContextEvent);
        CacheManager.shutdown();   // Don't use a listener... we want this shutdown late
        org.apache.log4j.LogManager.shutdown();
        org.apache.log4j.LogManager.resetConfiguration();
        org.apache.commons.beanutils.PropertyUtils.clearDescriptors();
        org.apache.commons.beanutils.ConvertUtils.deregister();
        java.beans.Introspector.flushCaches();
        LogFactory.releaseAll();       // Might help with PermGen.  See 8/02/07 post at http://raibledesigns.com/rd/entry/why_i_like_tomcat_5
    }

    public static void callShutdownListeners()
    {
        // Make a copy so we use exact same list for shutdownPre() and shutdownStarted()
        List<ShutdownListener> shutdownListeners = _shutdownListeners;

        for (ShutdownListener listener : shutdownListeners)
        {
            try
            {
                _log.info("Calling " + listener.getName() + " shutdownPre()");
                listener.shutdownPre();
            }
            catch (Throwable t)
            {
                _log.error("Exception during shutdownPre(): ", t);
            }
        }

        for (ShutdownListener listener : shutdownListeners)
        {
            try
            {
                _log.info("Calling " + listener.getName() + " shutdownStarted()");
                listener.shutdownStarted();
            }
            catch (Throwable t)
            {
                _log.error("Exception during shutdownStarted(): ", t);
            }
        }
    }

    public static void addShutdownListener(ShutdownListener listener)
    {
        _shutdownListeners.add(listener);
    }

    public static void removeShutdownListener(ShutdownListener listener)
    {
        _shutdownListeners.remove(listener);
    }

    public static void moduleStartupComplete(ServletContext servletContext)
    {
        for (StartupListener listener : _startupListeners)
        {
            try
            {
                ModuleLoader.getInstance().setStartingUpMessage("Running startup listener: " + listener.getName());
                listener.moduleStartupComplete(servletContext);
            }
            catch (Throwable t)
            {
                ExceptionUtil.logExceptionToMothership(null, t);
                ModuleLoader.getInstance().setStartupFailure(t);
            }
        }
    }

    public static void addStartupListener(StartupListener listener)
    {
        _startupListeners.add(listener);
    }

    public static ContextLoaderListener getSpringContextListener()
    {
        return _springContextListener;
    }

    public static void addNewInstallCompleteListener(NewInstallCompleteListener listener)
    {
        _newInstallCompleteListeners.add(listener);
    }

    public static void afterNewInstallComplete()
    {
        for (NewInstallCompleteListener listener : _newInstallCompleteListeners)
            listener.onNewInstallComplete();
    }
}
