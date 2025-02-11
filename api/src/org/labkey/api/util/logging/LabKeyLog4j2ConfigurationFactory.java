package org.labkey.api.util.logging;

import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.AbstractConfiguration;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Order;
import org.apache.logging.log4j.core.config.composite.CompositeConfiguration;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.xml.XmlConfiguration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Plugin(
        name = "LabKeyLog4j2ConfigurationFactory",
        category = "ConfigurationFactory"
)
@Order(1)
public class LabKeyLog4j2ConfigurationFactory extends ConfigurationFactory
{
    @Override
    public String[] getSupportedTypes()
    {
        return new String[]{".log4j2.xml",".xml", "*"};
    }

    @Override
    public Configuration getConfiguration(final LoggerContext context, final ConfigurationSource source) {
        List<XmlConfiguration> configs = new ArrayList<>();

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(classLoader);

        // Use Ant-style patterns to match resources
        Resource[] resources = null;
        try
        {
            resources = resolver.getResources("classpath*:**/configs/*.log4j2.xml");
            for (Resource resource : resources) {
                File config = resource.getFile();
                if (config.exists())
                {
                    ConfigurationSource cs = ConfigurationSource.fromUri(resource.getURI());
                    XmlConfiguration xmlConfiguration = new XmlConfiguration(context, cs);
                    configs.add(xmlConfiguration);
                }
            }

            // Sort 00.log4j2.xml < 01.log4j2.xml < 02.log4j2.xml
            configs.sort(Comparator.comparing(AbstractConfiguration::getName));
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }

        return new CompositeConfiguration(configs);
    }
}
