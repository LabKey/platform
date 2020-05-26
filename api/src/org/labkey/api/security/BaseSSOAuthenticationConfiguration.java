package org.labkey.api.security;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.security.AuthenticationConfiguration.SSOAuthenticationConfiguration;
import org.labkey.api.security.AuthenticationProvider.SSOAuthenticationProvider;

import java.util.Map;

public abstract class BaseSSOAuthenticationConfiguration<AP extends SSOAuthenticationProvider<?>> extends BaseAuthenticationConfiguration<AP> implements SSOAuthenticationConfiguration<AP>
{
    private final boolean _autoRedirect;

    protected BaseSSOAuthenticationConfiguration(AP provider, Map<String, Object> props)
    {
        super(provider, props);
        _autoRedirect = (Boolean)props.get("autoRedirect");
    }

    @Override
    public boolean isAutoRedirect()
    {
        return _autoRedirect;
    }

    @Override
    public @NotNull Map<String, Object> getLoggingProperties()
    {
        Map<String, Object> map = super.getLoggingProperties();
        map.put("autoRedirect", _autoRedirect);

        return map;
    }
}
