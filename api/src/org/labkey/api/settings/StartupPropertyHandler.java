package org.labkey.api.settings;

import java.util.Collection;

public abstract class StartupPropertyHandler<T extends StartupProperty>
{
    private final String _scope;

    public StartupPropertyHandler(String scope)
    {
        _scope = scope;
    }

    public String getScope()
    {
        return _scope;
    }

    public abstract Collection<T> getDocumentationProperties();
}
