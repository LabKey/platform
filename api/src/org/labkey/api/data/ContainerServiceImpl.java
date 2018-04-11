package org.labkey.api.data;

import org.labkey.api.util.GUID;
import org.labkey.api.util.Path;

public class ContainerServiceImpl implements ContainerService
{
    @Override
    public Container getForId(GUID id)
    {
        return ContainerManager.getForId(id);
    }

    @Override
    public Container getForId(String id)
    {
        return ContainerManager.getForId(id);
    }

    @Override
    public Container getForPath(Path path)
    {
        return ContainerManager.getForPath(path);
    }

    @Override
    public Container getForPath(String path)
    {
        return ContainerManager.getForPath(path);
    }
}
