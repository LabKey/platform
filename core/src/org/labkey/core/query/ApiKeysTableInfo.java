/*
 * Copyright (c) 2017-2026 LabKey Corporation
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
import org.labkey.api.data.CompareType;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;

/**
 * All users can view and delete from the "filtered to current user" version of this table. CoreQuerySchema shows
 * the unfiltered version only to those with UserManagementPermission (i.e., site and application admins).
 * ApiKeysUpdateService ensures that only the high-level admins can delete others' API keys.
 */
public class ApiKeysTableInfo extends FilteredTable<CoreQuerySchema>
{
    public ApiKeysTableInfo(@NotNull CoreQuerySchema schema, boolean filterToCurrentUser)
    {
        super(schema.getDbSchema().getTable(CoreQuerySchema.API_KEYS_TABLE_NAME), schema);
        addWrapColumn(getRealTable().getColumn("RowId")).setHidden(true);
        MutableColumnInfo createdBy = addWrapColumn(getRealTable().getColumn("CreatedBy"));
        addWrapColumn(getRealTable().getColumn("Created"));
        addWrapColumn(getRealTable().getColumn("Expiration"));
        addWrapColumn(getRealTable().getColumn("LastUsed"));
        addWrapColumn(getRealTable().getColumn("Description"));
        // Show the role's display name instead of the fully qualified Java class name that's stored
        addWrapColumn(getRealTable().getColumn("RestrictionRole")).setDisplayColumnFactory(colInfo -> new DataColumn(colInfo){
            @Override
            public Object getDisplayValue(RenderContext ctx)
            {
                String value = (String)super.getDisplayValue(ctx);
                if (value != null)
                {
                    Role role = RoleManager.getRole(value);
                    value = role != null ? role.getDisplayName() : "<Unknown Role>";
                }
                return value;
            }
        });

        if (filterToCurrentUser)
        {
            setTitle("User API Keys");
            createdBy.setHidden(true);
            addCondition(new SimpleFilter(createdBy.getFieldKey(), schema.getUser().getUserId(), CompareType.EQUAL));
        }
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
        return new ApiKeysUpdateService(this, getRealTable());
    }
}
