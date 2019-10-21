package org.labkey.api.security;

import org.labkey.api.security.AuthenticationConfiguration.SSOAuthenticationConfiguration;
import org.labkey.api.security.AuthenticationProvider.SSOAuthenticationProvider;

import java.util.Map;

public abstract class BaseSSOAuthenticationConfiguration<AP extends SSOAuthenticationProvider> extends BaseAuthenticationConfiguration<AP> implements SSOAuthenticationConfiguration<AP>
{
    protected BaseSSOAuthenticationConfiguration(String key, AP provider, Map<String, String> props)
    {
        super(key, provider, props);
    }
}
