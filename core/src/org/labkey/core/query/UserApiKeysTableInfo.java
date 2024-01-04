package org.labkey.core.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;

public class UserApiKeysTableInfo extends FilteredTable<CoreQuerySchema>
{
    public UserApiKeysTableInfo(@NotNull CoreQuerySchema schema)
    {
        super(schema.getDbSchema().getTable(CoreQuerySchema.API_KEYS_TABLE_NAME), schema);
        addWrapColumn(getRealTable().getColumn("RowId")).setHidden(true);
        addWrapColumn(getRealTable().getColumn("CreatedBy")).setHidden(true);
        addWrapColumn(getRealTable().getColumn("Created"));
        addWrapColumn(getRealTable().getColumn("Expiration"));
        addCondition(new SimpleFilter(FieldKey.fromParts("CreatedBy"), schema.getUser().getUserId(), CompareType.EQUAL));
    }

    @Override
    public boolean supportsContainerFilter()
    {
        return false;
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal principal, @NotNull Class<? extends Permission> perm)
    {
        if (principal instanceof User user)
        {
           if (user.isImpersonated())
               return false;

            // We allow only read and delete on this table.
            return perm.equals(ReadPermission.class) || perm.equals(DeletePermission.class);
        }
        return false;
    }

    @Override
    public @Nullable QueryUpdateService getUpdateService()
    {
        return new DefaultQueryUpdateService(this, getRealTable());
    }
}
