package org.labkey.core.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.query.FilteredTable;

public class ApiKeysTableInfo extends FilteredTable<CoreQuerySchema>
{
    public ApiKeysTableInfo(@NotNull CoreQuerySchema schema)
    {
        super(schema.getDbSchema().getTable(CoreQuerySchema.API_KEYS_TABLE_NAME), schema);
        addWrapColumn(getRealTable().getColumn("CreatedBy"));
        addWrapColumn(getRealTable().getColumn("Created"));
        addWrapColumn(getRealTable().getColumn("Expiration"));
    }

    @Override
    public boolean supportsContainerFilter()
    {
        return false;
    }
}
