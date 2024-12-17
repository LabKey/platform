package org.labkey.devtools.authentication;

import org.labkey.api.data.Container;
import org.labkey.api.security.BaseSecondaryAuthenticationConfiguration;
import org.labkey.api.security.User;
import org.labkey.api.util.URLHelper;

import java.net.URISyntaxException;
import java.util.Map;

public class TestSecondaryConfiguration extends BaseSecondaryAuthenticationConfiguration<TestSecondaryProvider>
{
    public TestSecondaryConfiguration(TestSecondaryProvider provider, Map<String, Object> standardSettings, Map<String, Object> props)
    {
        super(provider, standardSettings, props);
    }

    @Override
    public URLHelper getRedirectURL(User candidate, Container c)
    {
        try
        {
            return new URLHelper(TestSecondaryController.getTestSecondaryURL(c, getRowId()).getURIString());
        }
        catch (URISyntaxException e)
        {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }
}
