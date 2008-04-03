package org.labkey.api.data;

import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.LookupForeignKey;

/**
 * User: jeckels
 * Date: Feb 22, 2007
 */
public class ContainerTable extends FilteredTable
{
    public ContainerTable()
    {
        super(CoreSchema.getInstance().getTableInfoContainers());
        wrapAllColumns(true);
        getColumn("_ts").setIsHidden(true);
        getColumn("EntityId").setIsHidden(true);

        getColumn("Parent").setFk(new LookupForeignKey("EntityId", "Name")
        {
            public TableInfo getLookupTableInfo()
            {
                return new ContainerTable();
            }
        });
    }
}
