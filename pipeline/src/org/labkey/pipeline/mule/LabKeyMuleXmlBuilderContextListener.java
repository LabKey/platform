/*
 * Copyright (c) 2023-2026 LabKey Corporation
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

import org.labkey.api.util.UnexpectedException;
import org.mule.MuleManager;
import org.mule.config.ConfigurationException;
import org.mule.util.StringUtils;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

/** Forked into our codebase to support transition from javax.servlet to jakarta.servlet */
public class LabKeyMuleXmlBuilderContextListener implements ServletContextListener
{
    public static final String INIT_PARAMETER_MULE_CONFIG = "org.mule.config";
    public static final String INIT_PARAMETER_WEBAPP_CLASSPATH = "org.mule.webapp.classpath";

    @Override
    public void contextInitialized(ServletContextEvent event)
    {
        try
        {
            initialize(event.getServletContext());
        }
        catch (ConfigurationException e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    public void initialize(ServletContext context) throws ConfigurationException
    {
        String config = context.getInitParameter(INIT_PARAMETER_MULE_CONFIG);
        if (config == null)
        {
            config = getDefaultConfigResource();
        }

        String webappClasspath = context.getInitParameter(INIT_PARAMETER_WEBAPP_CLASSPATH);
        if (StringUtils.isBlank(webappClasspath))
        {
            webappClasspath = null;
        }

        createManager(config, webappClasspath, context);
    }

    protected void createManager(String configResource, String webappClasspath, ServletContext context) throws ConfigurationException
    {
        LabKeyWebappMuleXmlConfigurationBuilder builder = new LabKeyWebappMuleXmlConfigurationBuilder(context, webappClasspath);
        builder.configure(configResource, null);
    }

    protected String getDefaultConfigResource()
    {
        return "mule-config.xml";
    }

    @Override
    public void contextDestroyed(ServletContextEvent event)
    {
        destroy();
    }

    public void destroy()
    {
        if (MuleManager.isInstanciated())
        {
            MuleManager.getInstance().dispose();
        }
    }
}
