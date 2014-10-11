/*
 * Copyright (c) 2014 LabKey Corporation
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
package org.labkey.list.model;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.UserIdForeignKey;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;

import java.util.Arrays;
import java.util.List;

/**
 * Created by Joe on 8/19/2014.
 */
public class ListManagerTable extends FilteredTable<ListManagerSchema>
{
    public ListManagerTable(ListManagerSchema userSchema, TableInfo table)
    {
        super(table, userSchema);

        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("ListID"))).setHidden(true);
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Name")));
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Description")));

        ColumnInfo container = addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Container")));
        ContainerForeignKey.initColumn(container, userSchema);
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Created")));
        ColumnInfo createdBy = addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("CreatedBy")));
        UserIdForeignKey.initColumn(createdBy);
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Modified")));
        ColumnInfo modifiedBy = addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("ModifiedBy")));
        UserIdForeignKey.initColumn(modifiedBy);

        setDefaultVisibleColumns(Arrays.asList(FieldKey.fromParts("Name"), FieldKey.fromParts("Description")));
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return _userSchema.getContainer().hasPermission(this.getClass().getName() + " " + getName(), user, perm);
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return super.getDefaultVisibleColumns();
    }
}
