package org.labkey.api.settings;

import org.labkey.api.collections.LabKeyCollectors;

import java.util.Arrays;
import java.util.Map;

public abstract class StartupPropertyHandler<T extends Enum<T> & StartupProperty>
{
    private final String _scope;
    private final Map<String, T> _properties;

    public StartupPropertyHandler(String scope, Class<T> type)
    {
        _scope = scope;
        _properties = Arrays.stream(type.getEnumConstants()).collect(LabKeyCollectors.toLinkedMap(Enum::name, sp->sp));
    }

    public String getScope()
    {
        return _scope;
    }

    public Map<String, T> getProperties()
    {
        return _properties;
    }


    /**
     * Most startup property handlers use strict naming, meaning all property names specified in the properties file
     * must match the return value of name() for one of the StartupProperty instances. There are some handlers whose
     * properties use names that don't correspond to a StartupProperty's name, e.g, security startup properties use
     * arbitrary group names or email address as the name.
     *
     * @return A boolean indicated whether this property uses strict naming
     */
    public boolean useStrictNaming()
    {
        return true;
    }

    public abstract void handle(Map<T, ConfigProperty> properties);
}
