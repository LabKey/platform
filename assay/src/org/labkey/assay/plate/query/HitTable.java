/*
 * Copyright (c) 2024-2026 LabKey Corporation
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
package org.labkey.assay.plate.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AssaySchema;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.assay.query.AssayDbSchema;

import java.util.ArrayList;
import java.util.List;

public class HitTable extends SimpleUserSchema.SimpleTable<UserSchema>
{
    public static final String NAME = "Hit";
    private static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();
    private final boolean _allowInsertUpdate;

    static
    {
        defaultVisibleColumns.add(FieldKey.fromParts("ProtocolId"));
        defaultVisibleColumns.add(FieldKey.fromParts("ResultId"));
        defaultVisibleColumns.add(FieldKey.fromParts("RunId"));
        defaultVisibleColumns.add(FieldKey.fromParts("WellLsid"));
    }

    public HitTable(PlateSchema schema, @Nullable ContainerFilter cf, boolean allowInsertUpdate)
    {
        super(schema, AssayDbSchema.getInstance().getTableInfoHit(), cf);
        _allowInsertUpdate = allowInsertUpdate;
    }

    @Override
    public void addColumns()
    {
        super.addColumns();
        getMutableColumnOrThrow("RunId").setFk(new QueryForeignKey.Builder(getUserSchema(), getContainerFilter()).schema(ExpSchema.SCHEMA_NAME).table(ExpSchema.TableType.Runs));
        getMutableColumnOrThrow("ProtocolId").setFk(new QueryForeignKey.Builder(getUserSchema(), getContainerFilter()).schema(AssaySchema.NAME).table(AssaySchema.ASSAY_LIST_TABLE_NAME));
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        if (!_allowInsertUpdate && (perm.equals(InsertPermission.class) || perm.equals(UpdatePermission.class)))
            return false;
        if (perm == DeletePermission.class)
            return _userSchema.getContainer().hasPermission(user, AdminPermission.class);
        return super.hasPermission(user, perm);
    }
}
