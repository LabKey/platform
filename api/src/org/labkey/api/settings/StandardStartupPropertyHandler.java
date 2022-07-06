package org.labkey.api.settings;

import org.labkey.api.collections.LabKeyCollectors;

import java.util.Arrays;
import java.util.Map;

public abstract class StandardStartupPropertyHandler<T extends Enum<T> & StartupProperty> extends StartupPropertyHandler<T>
{
    /**
     * @param scope The scope name
     * @param type An enum that defines possible properties in this scope. The enum constants are used to validate
     *             property entries and to document available properties. The order of constant definitions determines
     *             the order they'll be displayed on the "Available Startup Properties" admin console page.
     */
    protected StandardStartupPropertyHandler(String scope, Class<T> type)
    {
        super(scope, type.getName(), Arrays.stream(type.getEnumConstants())
            .filter(sp -> null != sp.getPropertyName())
            .collect(LabKeyCollectors.toCaseInsensitiveLinkedMap(StartupProperty::getPropertyName, sp->sp)));
    }

    public abstract void handle(Map<T, StartupPropertyEntry> properties);
}
