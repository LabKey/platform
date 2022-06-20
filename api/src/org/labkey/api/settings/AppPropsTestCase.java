package org.labkey.api.settings;

import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.settings.AppPropsImpl.SiteSettingsPropertyHandler;
import org.labkey.api.util.TestContext;

import java.util.Collection;
import java.util.List;

public class AppPropsTestCase extends Assert
{
    /**
     * Test that the Site Settings can be configured from startup properties
     */
    @Test
    public void testStartupPropertiesForSiteSettings()
    {
        final String TEST_MAX_BLOB_SIZE = "12345";

        // save the original Site Settings server settings so that we can restore them when this test is done
        AppProps siteSettingsProps = AppProps.getInstance();
        int originalMaxBlobSize = siteSettingsProps.getMaxBLOBSize();

        ModuleLoader.getInstance().handleStartupProperties(new SiteSettingsPropertyHandler()
        {
            @Override
            public @NotNull Collection<StartupPropertyEntry> getStartupProperties()
            {
                return List.of(new StartupPropertyEntry("maxBLOBSize", TEST_MAX_BLOB_SIZE, "startup", AppProps.SCOPE_SITE_SETTINGS));
            }

            @Override
            public boolean performChecks()
            {
                return false;
            }
        });

        // now check that the expected changes occurred to the Site Settings on the server
        int newMaxBlobSize = siteSettingsProps.getMaxBLOBSize();
        assertEquals("The expected change in Site Settings was not found", TEST_MAX_BLOB_SIZE, Integer.toString(newMaxBlobSize));

        // restore the Look And Feel server settings to how they were originally
        WriteableAppProps writeableSiteSettingsProps = AppProps.getWriteableInstance();
        writeableSiteSettingsProps.setMaxBLOBSize(originalMaxBlobSize);
        writeableSiteSettingsProps.save(TestContext.get().getUser());
    }
}
