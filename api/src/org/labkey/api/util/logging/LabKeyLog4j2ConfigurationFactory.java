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
import org.apache.logging.log4j.core.config.xml.XmlConfigurationFactory;
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
public class LabKeyLog4j2ConfigurationFactory extends XmlConfigurationFactory
{
    @Override
    public String[] getSupportedTypes()
    {
        return new String[]{".log4j2.xml",".xml", "*"};
    }

    @Override
    public Configuration getConfiguration(final LoggerContext context, final ConfigurationSource source)
    {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        // Use Ant-style patterns to match resources
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(classLoader);

        try
        {
            //Get Base Log4j2 configuration
            List<XmlConfiguration> configs = resolveConfigFiles("classpath:log4j2.xml", context, resolver);

            //Get any Override configurations
            List<XmlConfiguration> overrideConfigs = resolveConfigFiles("classpath*:**/config/*.log4j2.xml", context, resolver);

            // If there are no override configs, then return base configuration
            if (overrideConfigs.isEmpty())
                return super.getConfiguration(context, source);

            // Sort the override configs: 00.log4j2.xml < 01.log4j2.xml < 02.log4j2.xml
            overrideConfigs.sort(Comparator.comparing(AbstractConfiguration::getName));

            // log4j2.xml, 00.log4j2.xml, 01.log4j2.xml...
            configs.addAll(overrideConfigs);
            return new CompositeConfiguration(configs);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    private List<XmlConfiguration> resolveConfigFiles(String pattern, final LoggerContext context, PathMatchingResourcePatternResolver resolver) throws IOException
    {
        List<XmlConfiguration> configs = new ArrayList<>();
        Resource[] resources = resolver.getResources(pattern);

        for (Resource resource : resources)
        {
            File config = resource.getFile();
            if (config.exists())
            {
                ConfigurationSource cs = ConfigurationSource.fromUri(resource.getURI());
                XmlConfiguration xmlConfiguration = new XmlConfiguration(context, cs);
                configs.add(xmlConfiguration);
            }
        }

        return configs;
    }
}
