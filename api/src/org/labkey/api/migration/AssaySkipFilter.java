package org.labkey.api.migration;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.GUID;

import java.util.HashSet;
import java.util.Set;

public class AssaySkipFilter implements MigrationFilter
{
    private static final Set<GUID> SKIP_CONTAINERS = new HashSet<>();

    @Override
    public String getName()
    {
        return "AssaySkipFilter";
    }

    @Override
    public void saveFilter(@Nullable GUID guid, String value)
    {
        if (null == guid)
            throw new ConfigurationException(getName() + " must specify a GUID");

        SKIP_CONTAINERS.add(guid);
    }

    public static Set<GUID> getSkipContainers()
    {
        return SKIP_CONTAINERS;
    }
}
