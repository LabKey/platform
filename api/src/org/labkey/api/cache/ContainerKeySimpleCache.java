package org.labkey.api.cache;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.util.GUID;

public class ContainerKeySimpleCache<V> extends SimpleKeyMappingCache<Container, GUID, V>
{
    public ContainerKeySimpleCache(@NotNull SimpleCache<GUID, V> delegate)
    {
        super(delegate, Container::getEntityId, ContainerManager::getForId);
    }
}
