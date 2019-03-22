/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
package org.labkey.api.ldk.table;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;

import java.util.HashMap;
import java.util.Map;

/**
 * The goal is to allow a table to specify an additional permission which is required at insert/update/delete.
 * Table permissions are checked by calling hasPermission(), which tests for a specific Permission, such as InsertPermission, or UpdatePermission.
 * You are able to map an addition permission to any of these, which the user must also have.  Because InsertPermission and UpdatePermission are checked upstream anyway,
 * the user must also have these permissions.  This is just a way of enforcing more refined security, but not completely changing security.
 */
public class CustomPermissionsTable<SchemaType extends UserSchema> extends SimpleUserSchema.SimpleTable<SchemaType>
{
    private Map<Class<? extends Permission>, Class<? extends Permission>> _permMap = new HashMap<>();

    public CustomPermissionsTable(SchemaType schema, TableInfo table)
    {
        super(schema, table);
    }

    @Override
    public CustomPermissionsTable<SchemaType> init()
    {
        return (CustomPermissionsTable<SchemaType>)super.init();
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        if (!_userSchema.getContainer().hasPermission(user, perm))
        {
            return false;
        }

        if (_permMap.containsKey(perm))
        {
            return _userSchema.getContainer().hasPermission(user, _permMap.get(perm));
        }

        return true;
    }

    public void addPermissionMapping(Class<? extends Permission> perm1, Class<? extends Permission> perm2)
    {
        _permMap.put(perm1, perm2);
    }
}
