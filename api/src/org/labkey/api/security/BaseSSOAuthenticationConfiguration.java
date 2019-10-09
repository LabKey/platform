package org.labkey.api.security;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.security.AuthenticationConfiguration.SSOAuthenticationConfiguration;
import org.labkey.api.security.AuthenticationProvider.SSOAuthenticationProvider;

import java.util.Map;

public abstract class BaseSSOAuthenticationConfiguration extends BaseAuthenticationConfiguration implements SSOAuthenticationConfiguration
{
    protected BaseSSOAuthenticationConfiguration(String key, AuthenticationProvider provider, Map props)
    {
        super(key, provider, props);
    }

    @NotNull
    @Override
    public SSOAuthenticationProvider getAuthenticationProvider()
    {
        return (SSOAuthenticationProvider)super.getAuthenticationProvider();
    }
}
