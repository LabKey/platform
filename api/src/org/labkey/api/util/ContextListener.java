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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.NullConfiguration;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ViewServlet;
import org.springframework.web.context.ContextLoaderListener;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @see org.labkey.bootstrap.PipelineBootstrapConfig
 * @see org.labkey.bootstrap.LabKeyBootstrapClassLoader
 */
public class ContextListener implements ServletContextListener
{
    // this is among the earliest classes loaded (except for classes loaded via annotations @ClientEndpoint @ServerEndpoint etc)

    // IMPORTANT see also LabKeyBootstrapClassLoader/PipelineBootstrapConfig which duplicates this code, keep them consistent
    // On startup on some platforms, some modules will die if java.awt.headless is not set to false.
    static
    {
        String headless = "java.awt.headless";
        if (System.getProperty(headless) == null)
            System.setProperty(headless, "true");
        // On most installs, catalina.home and catalina.base point to the same directory. However, it's possible
        // to have multiple instances share the Tomcat binaries but have their own ./logs, ./conf, etc directories
        // Thus, we want to use catalina.base for our place to find log files. http://www.jguru.com/faq/view.jsp?EID=1121565
        //PipelineBootstrapConfig.ensureLogHomeSet(System.getProperty("catalina.base") + "/logs");
        if (LogHelper.getLabKeyLogDir() == null)
        {
            // Only set this if the user hasn't overridden it
            System.setProperty(LogHelper.LOG_HOME_PROPERTY_NAME, System.getProperty("catalina.base") + "/logs");
        }
    }

    // NOTE: this line of code with LogManager.getLogger() has to happen after System.setProperty(LOG_HOME_PROPERTY_NAME)
    private static final Logger _log = LogManager.getLogger(ContextListener.class);
    private static final List<ShutdownListener> _shutdownListeners = new CopyOnWriteArrayList<>();
    private static final List<StartupListener> _startupListeners = new CopyOnWriteArrayList<>();
    private static final ContextLoaderListener _springContextListener = new ContextLoaderListener();
    private static final List<NewInstallCompleteListener> _newInstallCompleteListeners = new CopyOnWriteArrayList<>();
    private static final List<ModuleChangeListener> _moduleChangeListeners = new CopyOnWriteArrayList<>();

    @Override
    public void contextInitialized(ServletContextEvent servletContextEvent)
    {
        getSpringContextListener().contextInitialized(servletContextEvent);
    }

    @Override
    public void contextDestroyed(ServletContextEvent servletContextEvent)
    {
        ViewServlet.setShuttingDown(0);

        callShutdownListeners();

        ViewServlet.setShuttingDown(1000);
        getSpringContextListener().contextDestroyed(servletContextEvent);
        CacheManager.shutdown();   // Don't use a listener... we want this shutdown late
        LogManager.shutdown();
        LoggerContext.getContext(true).setConfiguration(new NullConfiguration());
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


    public static void addModuleChangeListener(ModuleChangeListener l)
    {
        _moduleChangeListeners.add(l);
    }

    public static void fireModuleChangeEvent(Module m)
    {
        for (var l : _moduleChangeListeners.toArray(new ModuleChangeListener[0]))
        {
            try
            {
                l.onModuleChanged(m);
            }
            catch (Throwable t)
            {
                ExceptionUtil.logExceptionToMothership(null, t);
            }
        }
    }
}
