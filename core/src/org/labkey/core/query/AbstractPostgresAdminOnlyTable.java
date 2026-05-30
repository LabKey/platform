/*
 * Copyright (c) 2025-2026 LabKey Corporation
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
import org.labkey.api.data.VirtualTable;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.TroubleshooterPermission;

public class AbstractPostgresAdminOnlyTable extends VirtualTable<PostgresUserSchema>
{
    public AbstractPostgresAdminOnlyTable(String name, @Nullable PostgresUserSchema userSchema)
    {
        super(userSchema.getDbSchema(), name, userSchema);

        if (!userSchema.getContainer().isRoot() || !userSchema.getContainer().hasPermission(userSchema.getUser(), TroubleshooterPermission.class))
        {
            throw new IllegalArgumentException("Not available");
        }
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return perm.equals(ReadPermission.class) && getUserSchema().getContainer().hasPermission(user, TroubleshooterPermission.class);
    }
}
