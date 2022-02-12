package org.labkey.devtools.authentication;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.AuthenticationManager.LinkFactory;
import org.labkey.api.security.BaseSSOAuthenticationConfiguration;
import org.labkey.api.security.User;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

import java.util.Collections;
import java.util.Map;

public class TestSsoConfiguration extends BaseSSOAuthenticationConfiguration<TestSsoProvider>
{
    private final String _domain;
    private final LinkFactory _linkFactory = new LinkFactory(this);

    protected TestSsoConfiguration(TestSsoProvider provider, Map<String, Object> standardSettings, Map<String, Object> properties)
    {
        super(provider, standardSettings);
        _domain = (String)properties.get("domain");
    }

    @Override
    public @Nullable String getDomain()
    {
        return _domain;
    }

    @Override
    public URLHelper getUrl(String secret, ViewContext ctx)
    {
        ActionURL url = new ActionURL(TestSsoController.TestSsoAction.class, ContainerManager.getRoot());
        url.addParameter("configuration", getRowId());

        return url;
    }

    @Override
    public LinkFactory getLinkFactory()
    {
        return _linkFactory;
    }

    @Override
    public @NotNull Map<String, Object> getCustomProperties()
    {
        return null != _domain ? Map.of("domain", _domain) : Collections.emptyMap();
    }

    @Override
    public void savePlaceholderLogos(User user)
    {
        // Don't bother with the test configuration
    }
}
