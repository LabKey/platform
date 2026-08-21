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
            LOG.debug("Resource {} is found in Servlet Context.", resource);
        }
        else
        {
            LOG.debug("Resource {} is not found in Servlet Context, loading from classpath or as external file", resourcePath);
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
                LOG.debug("Resource {} is not found in filesystem", resourcePath);
            }
        }

        if (is == null)
        {
            is = super.loadResource(resource);
        }

        return is;
    }
}
