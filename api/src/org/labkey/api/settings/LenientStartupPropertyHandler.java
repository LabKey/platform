package org.labkey.api.settings;

import java.util.Collection;
import java.util.List;

/**
 * Handles a startup property scope that, unlike a StartupStartupPropertyHandler with fixed property names, has ad hoc
 * property names, such as an email address or a group name. A single StartupProperty is provided in the constructor
 * only for documentation purposes.
 */
public abstract class LenientStartupPropertyHandler<T extends StartupProperty> extends StartupPropertyHandler<T>
{
    private final T _property;

    public LenientStartupPropertyHandler(String scope, T property)
    {
        super(scope);
        _property = property;
    }

    public T getProperty()
    {
        return _property;
    }

    @Override
    public Collection<T> getDocumentationProperties()
    {
        return List.of(_property);
    }

    // A collection of entries is passed instead of a map because there are likely multiple entries that all correspond
    // to the same StartupProperty.
    public abstract void handle(Collection<StartupPropertyEntry> entries);
}
