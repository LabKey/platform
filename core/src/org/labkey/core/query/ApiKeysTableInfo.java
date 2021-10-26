/*
 * Copyright (c) 2017-2018 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.core.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UserManagementPermission;

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
        assert((User)user).hasRootPermission(UserManagementPermission.class);
        return perm.equals(ReadPermission.class) || perm.equals(DeletePermission.class);
    }

    @Override
    public @Nullable QueryUpdateService getUpdateService()
    {
        return new DefaultQueryUpdateService(this, getRealTable());
    }
}
