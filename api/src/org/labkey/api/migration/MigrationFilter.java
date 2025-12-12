package org.labkey.api.migration;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.GUID;

/**
 * A MigrationFilter adds support for the named filter property in the migration configuration file. If present,
 * saveFilter() is called with the container guid and property value. Modules can register these to present
 * module-specific filters.
 */
public interface MigrationFilter
{
    String getName();

    // Implementations should validate guid nullity
    void saveFilter(@Nullable GUID guid, String value);
}
