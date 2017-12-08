package org.labkey.core.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.Permission;

public class ApiKeysTableInfo extends FilteredTable<CoreQuerySchema>
{
    public ApiKeysTableInfo(@NotNull CoreQuerySchema schema)
    {
        super(schema.getDbSchema().getTable(CoreQuerySchema.API_KEYS_TABLE_NAME), schema);
        addWrapColumn(getRealTable().getColumn("RowId")).setHidden(true);
        addWrapColumn(getRealTable().getColumn("CreatedBy"));
        addWrapColumn(getRealTable().getColumn("Created"));
        addWrapColumn(getRealTable().getColumn("Expiration"));
    }

    @Override
    public boolean supportsContainerFilter()
    {
        return false;
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        // We only allow delete on this table. No need for permission check, since we already know user has
        // UserManagementPermission at the root.
        return perm.equals(DeletePermission.class);
    }

    @Override
    public @Nullable QueryUpdateService getUpdateService()
    {
        return new DefaultQueryUpdateService(this, getRealTable());
    }
}
