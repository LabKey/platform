package org.labkey.devtools.authentication;

import org.labkey.api.data.Container;
import org.labkey.api.security.BaseSecondaryAuthenticationConfiguration;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

import java.util.Map;

public class TestSecondaryConfiguration extends BaseSecondaryAuthenticationConfiguration<TestSecondaryProvider>
{
    public TestSecondaryConfiguration(TestSecondaryProvider provider, Map<String, Object> standardSettings, Map<String, Object> props)
    {
        super(provider, standardSettings, props);
    }

    @Override
    public ActionURL getRedirectURL(User candidate, Container c)
    {
        return TestSecondaryController.getTestSecondaryURL(c, getRowId());
    }
}
