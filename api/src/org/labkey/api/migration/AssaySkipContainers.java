package org.labkey.api.migration;

import org.labkey.api.util.GUID;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

// Need to make the assay-skip containers available to both experiment and assay
public class AssaySkipContainers
{
    private static final Set<GUID> SKIP_CONTAINERS = new CopyOnWriteArraySet<>();

    private AssaySkipContainers()
    {
    }

    public static void addContainers(Set<GUID> containers)
    {
        SKIP_CONTAINERS.addAll(containers);
    }

    public static Set<GUID> getContainers()
    {
        return SKIP_CONTAINERS;
    }

    public static Set<GUID> getFilteredContainers(Set<GUID> containers)
    {
        Set<GUID> filteredContainers = new HashSet<>(containers);
        filteredContainers.removeAll(AssaySkipContainers.getContainers());
        return filteredContainers;
    }
}
