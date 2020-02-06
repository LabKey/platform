package org.labkey.devtools.authentication;

import org.labkey.api.data.Container;
import org.labkey.api.security.AuthenticationConfiguration.SecondaryAuthenticationConfiguration;
import org.labkey.api.security.BaseAuthenticationConfiguration;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

import java.util.Map;

public class TestSecondaryConfiguration extends BaseAuthenticationConfiguration<TestSecondaryProvider> implements SecondaryAuthenticationConfiguration<TestSecondaryProvider>
{
    public TestSecondaryConfiguration(TestSecondaryProvider provider, Map<String, Object> props)
    {
        super(provider, props);
    }

    @Override
    public ActionURL getRedirectURL(User candidate, Container c)
    {
        return TestSecondaryController.getTestSecondaryURL(c, getRowId());
    }
}
