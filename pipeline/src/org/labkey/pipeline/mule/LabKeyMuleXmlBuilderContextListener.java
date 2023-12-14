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
