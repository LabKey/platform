package org.labkey.api.settings;

import javax.annotation.Nullable;

public interface StartupProperty
{
    // As a convenience for enum implementations, getStartupPropertyName() simply returns Enum.name()
    default String name()
    {
        throw new IllegalStateException("Must override getStartupProp()");
    }

    // Implementations can override this to use an alternative property name (not name()) or to filter out specific
    // properties, such as when the startup property enum is serving multiple purposes. Returning null will omit the
    // property from startup property handling and documentation.
    default @Nullable String getPropertyName()
    {
        return name();
    }

    String getDescription();
}
