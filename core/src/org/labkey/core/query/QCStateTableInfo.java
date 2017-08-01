package org.labkey.core.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.query.FilteredTable;

/**
 * Created by marty on 7/25/2017.
 */
public class QCStateTableInfo extends FilteredTable<CoreQuerySchema>
{
    public QCStateTableInfo(CoreQuerySchema schema)
    {
        super(CoreSchema.getInstance().getTableInfoQCState(), schema);
        for (ColumnInfo baseColumn : _rootTable.getColumns())
        {
            String name = baseColumn.getName();
            if ("Container".equalsIgnoreCase(name))
                continue;
            ColumnInfo wrappedColumn = addWrapColumn(baseColumn);
            if ("RowId".equalsIgnoreCase(name))
                wrappedColumn.setHidden(true);
        }
    }
}