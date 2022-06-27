package org.labkey.api.settings;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.module.ModuleLoader;

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

    // Startup properties come from the global properties files in most cases. Tests can override to provide test properties.
    @NotNull
    public Collection<StartupPropertyEntry> getStartupProperties()
    {
        return ModuleLoader.getInstance().getStartupPropertyEntries(getScope());
    }

    // Tests can override to avoid standard checks
    public boolean performChecks()
    {
        return true;
    }

    public abstract Collection<T> getDocumentationProperties();
}
