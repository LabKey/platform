package org.labkey.api.settings;

import org.apache.logging.log4j.Logger;
import org.labkey.api.util.logging.LogHelper;

import java.util.Map;

public class RandomSiteSettingsPropertyHandler extends StandardStartupPropertyHandler<RandomStartupProperties>
{
    private static final Logger LOG = LogHelper.getLogger(AppPropsImpl.class, "Additional site settings startup properties");

    public RandomSiteSettingsPropertyHandler()
    {
        super(AppProps.SCOPE_SITE_SETTINGS, RandomStartupProperties.class);
    }

    @Override
    public void handle(Map<RandomStartupProperties, StartupPropertyEntry> properties)
    {
        if (!properties.isEmpty())
        {
            WriteableAppProps writeable = AppProps.getWriteableInstance();
            properties.forEach((rsp, cp) -> {
                LOG.info("Setting additional site-level startup property '" + rsp.name() + "' to '" + cp.getValue() + "'");
                rsp.setValue(writeable, cp.getValue());
            });
            writeable.save(null);
        }
    }
}
