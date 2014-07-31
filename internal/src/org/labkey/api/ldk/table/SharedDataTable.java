package org.labkey.api.ldk.table;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.UserSchema;

import java.util.Arrays;

/**
 * A TableInfo that will always show rows from the current folder plus the shared container
 *
 * If alwaysReverToParent = true, then when queried from a workbook, this will always filter as though the parent is the source
 */
public class SharedDataTable<SchemaType extends UserSchema> extends SimpleUserSchema.SimpleTable<SchemaType>
{
    private boolean _alwaysReverToParent = false;

    public SharedDataTable(SchemaType schema, TableInfo table, boolean alwaysReverToParent)
    {
        super(schema, table);
        applyContainerFilter(getContainerFilter());
        _alwaysReverToParent = alwaysReverToParent;
    }

    @Override
    public boolean supportsContainerFilter()
    {
        return false;
    }

    @Override
    public SharedDataTable<SchemaType> init()
    {
        return (SharedDataTable<SchemaType>)super.init();
    }

    @Override
    public ContainerFilter getContainerFilter()
    {
        Container[] arr = getUserSchema().getContainer().isWorkbook() ? new Container[]{ContainerManager.getSharedContainer(), getUserSchema().getContainer().getParent()} : new Container[]{ContainerManager.getSharedContainer()};
        return new ContainerFilter.CurrentPlusExtras(getUserSchema().getUser(), arr);
    }
}
