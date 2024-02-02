package org.labkey.pipeline.mule;

import org.apache.logging.log4j.Logger;
import org.labkey.api.util.logging.LogHelper;
import org.mule.config.ConfigurationException;
import org.mule.config.builders.MuleXmlConfigurationBuilder;
import org.mule.util.FileUtils;

import jakarta.servlet.ServletContext;
import java.io.File;
import java.io.InputStream;

/** Forked into our codebase to support transition from javax.servlet to jakarta.servlet */
public class LabKeyWebappMuleXmlConfigurationBuilder extends MuleXmlConfigurationBuilder
{
    private static final Logger LOG = LogHelper.getLogger(MuleListenerHelper.class, "Initializes and configures Mule for pipelines");
    private final ServletContext context;
    private final String webappClasspath;

    public LabKeyWebappMuleXmlConfigurationBuilder(ServletContext context, String webappClasspath) throws ConfigurationException
    {
        super();
        this.context = context;
        this.webappClasspath = webappClasspath;
    }

    @Override
    protected InputStream loadResource(String resource) throws ConfigurationException
    {
        String resourcePath = resource;
        InputStream is = null;
        if (webappClasspath != null)
        {
            resourcePath = (new File(webappClasspath, resource)).getPath();
            is = context.getResourceAsStream(resourcePath);
        }

        if (is == null)
        {
            is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
        }

        if (is != null)
        {
            LOG.debug("Resource " + resource + " is found in Servlet Context.");
        }
        else
        {
            LOG.debug("Resource " + resourcePath + " is not found in Servlet Context, loading from classpath or as external file");
        }

        if (is == null && webappClasspath != null)
        {
            resourcePath = FileUtils.newFile(webappClasspath, resource).getPath();

            try
            {
                is = super.loadResource(resourcePath);
            }
            catch (ConfigurationException e)
            {
                LOG.debug("Resource " + resourcePath + " is not found in filesystem");
            }
        }

        if (is == null)
        {
            is = super.loadResource(resource);
        }

        return is;
    }
}
