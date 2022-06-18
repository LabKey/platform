package org.labkey.api.settings;

import java.util.Collection;

public abstract class StartupPropertyHandler<T extends StartupProperty>
{
    private final String _scope;
    private final String _startupPropertyClassName;

    public StartupPropertyHandler(String scope, String startupPropertyClassName)
    {
        _scope = scope;
        _startupPropertyClassName = startupPropertyClassName;
    }

    public String getScope()
    {
        return _scope;
    }

    public String getStartupPropertyClassName()
    {
        return _startupPropertyClassName;
    }

    public abstract Collection<T> getDocumentationProperties();
}
