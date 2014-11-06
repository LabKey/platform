package org.labkey.query.olap;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.JunitUtil;

public class JunitOlapSchemaDescriptor extends ModuleOlapSchemaDescriptor
{
    public JunitOlapSchemaDescriptor(@NotNull String id, @NotNull Module module, @NotNull Resource resource)
    {
        super(id, module, resource);
    }

    @Override
    public boolean isExposed(Container container)
    {
        return container.getParsedPath().equals(JunitUtil.getTestContainerPath());
    }
}
