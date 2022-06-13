package org.labkey.api.settings;

import org.labkey.api.collections.LabKeyCollectors;

import java.util.Arrays;
import java.util.Map;

public abstract class StartupPropertyHandler
{
    private final String _scope;
    private final Map<String, StartupProperty> _properties;

    public StartupPropertyHandler(String scope, StartupProperty[] properties)
    {
        _scope = scope;
        _properties = Arrays.stream(properties).collect(LabKeyCollectors.toLinkedMap(StartupProperty::name, sp->sp));
    }

    public String getScope()
    {
        return _scope;
    }

    public Map<String, StartupProperty> getProperties()
    {
        return _properties;
    }

    public abstract void handle(Map<StartupProperty, ConfigProperty> properties);
}
