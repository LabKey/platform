package org.labkey.api.security;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

public abstract class BaseAuthenticationConfiguration<AP extends AuthenticationProvider> implements AuthenticationConfiguration<AP>
{
    private final AP _provider;
    private final String _name;
    private final boolean _enabled;
    private final String _key;

    protected BaseAuthenticationConfiguration(String key, AP provider, Map<String, String> props)
    {
        _key = key;
        _provider = provider;
        _name = props.get("Name");
        _enabled = Boolean.valueOf(props.get("Enabled"));
    }

    @Override
    public @NotNull String getKey()
    {
        return _key;
    }

    @Override
    public @NotNull String getName()
    {
        return _name;
    }

    @NotNull
    @Override
    public AP getAuthenticationProvider()
    {
        return _provider;
    }

    @Override
    public boolean enabled()
    {
        return _enabled;
    }
}
