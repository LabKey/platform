package org.labkey.api.settings;

import org.labkey.api.collections.LabKeyCollectors;

import java.util.Map;
import java.util.stream.Stream;

// Base class for startup property handlers that want to work with a map of property entries. Extracted from
// StandardStartupPropertyHandler to allow ExperimentalFeatureStartupPropertyHandler (which can't use an enum
// to provide its startup properties) to share implementation and use the existing handleStartupProperties()
// method. Note that we want StandardStartupPropertyHandler's type parameter (T) to extend Enum<T>, which is
// why we can't just throw a new constructor on that class.
public abstract class MapBasedStartupPropertyHandler<T extends StartupProperty> extends StartupPropertyHandler<T>
{
    public MapBasedStartupPropertyHandler(String scope, String startupPropertyClassName, Stream<T> properties)
    {
        super(scope, startupPropertyClassName, properties
            .filter(sp -> null != sp.getPropertyName())
            .collect(LabKeyCollectors.toCaseInsensitiveLinkedMap(StartupProperty::getPropertyName, sp->sp)));
    }

    public abstract void handle(Map<T, StartupPropertyEntry> properties);
}
