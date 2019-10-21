package org.labkey.devtools.authentication;

import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.AuthenticationManager.LinkFactory;
import org.labkey.api.security.BaseSSOAuthenticationConfiguration;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;

import java.util.Map;

public class TestSsoConfiguration extends BaseSSOAuthenticationConfiguration<TestSsoProvider>
{
    private final LinkFactory _linkFactory = new LinkFactory(this);

    protected TestSsoConfiguration(String key, TestSsoProvider provider, Map<String, String> props)
    {
        super(key, provider, props);
    }

    @Override
    public URLHelper getUrl(String secret)
    {
        ActionURL url = new ActionURL(TestSsoController.TestSsoAction.class, ContainerManager.getRoot());
        url.addParameter("configuration", getKey());

        return url;
    }

    @Override
    public LinkFactory getLinkFactory()
    {
        return _linkFactory;
    }
}
