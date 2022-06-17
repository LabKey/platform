package org.labkey.api.settings;

import org.labkey.api.collections.LabKeyCollectors;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

public abstract class StandardStartupPropertyHandler<T extends Enum<T> & StartupProperty> extends StartupPropertyHandler<T>
{
    private final Map<String, T> _properties;

    /**
     *
     * @param scope The scope name
     * @param type An enum that defines all possible properties in this scope. The enum constants are used to validate
     *             property entries and to document available properties. The order of constant definitions determines
     *             the order they'll be displayed on the Available Startup Properties admin console page.
     */
    public StandardStartupPropertyHandler(String scope, Class<T> type)
    {
        super(scope);
        _properties = Arrays.stream(type.getEnumConstants()).collect(LabKeyCollectors.toLinkedMap(Enum::name, sp->sp));
    }

    public Map<String, T> getProperties()
    {
        return _properties;
    }

    @Override
    public Collection<T> getDocumentationProperties()
    {
        return _properties.values();
    }

    public abstract void handle(Map<T, StartupPropertyEntry> properties);
}
