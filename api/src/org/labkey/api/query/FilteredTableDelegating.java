/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.api.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.TableInfo;

/**
 * This is an alternate implementation of FilteredTable.  It is exactly the same except the default implementation of wrapColumn(), using WrappedColumnInfo instead of ExprColumn()
 */
public class FilteredTableDelegating<SchemaType extends UserSchema> extends FilteredTable<SchemaType>
{
    public FilteredTableDelegating(@NotNull TableInfo table, @NotNull SchemaType userSchema)
    {
        super(table, userSchema);
    }

    public FilteredTableDelegating(@NotNull TableInfo table, @NotNull SchemaType userSchema, @Nullable ContainerFilter containerFilter)
    {
        super(table, userSchema, containerFilter);
    }


    /* DISABLED FOR NOW
    Wrap a column that comes from inner table tableAlias (may or may not be getRealTable())
    @Override
    public MutableColumnInfo wrapColumnFromJoinedTable(String name, ColumnInfo underlyingColumn)
    {
        String alias = getAliasManager().decideAlias(name);
        String label = null;
        MutableColumnInfo ret = WrappedColumnInfo.wrapDelegating(this, new FieldKey(null,name), underlyingColumn, label, alias);
        if (underlyingColumn.isKeyField() && getColumn(underlyingColumn.getName()) != null)
        {
            ret.setKeyField(false);
        }
        if (!getPHIDataLoggingColumns().isEmpty() && PHI.NotPHI != underlyingColumn.getPHI() && underlyingColumn.isShouldLog())
        {
            ret.setColumnLogging(new ColumnLogging(true, underlyingColumn.getFieldKey(), underlyingColumn.getParentTable(), getPHIDataLoggingColumns(), getPHILoggingComment(), getSelectQueryAuditProvider()));
        }
        assert ret.getParentTable() == this;
        return ret;
    }
     */

    @Override
    public MutableColumnInfo wrapColumn(String alias, ColumnInfo underlyingColumn)
    {
        assert underlyingColumn.getParentTable() == _rootTable;
        return wrapColumnFromJoinedTable(alias, underlyingColumn);
    }

    @Override
    public MutableColumnInfo wrapColumn(ColumnInfo underlyingColumn)
    {
        return wrapColumn(underlyingColumn.getName(), underlyingColumn);
    }

    @Override
    public MutableColumnInfo addWrapColumn(String name, ColumnInfo column)
    {
        assert column.getParentTable() == getRealTable() : "Column is not from the same \"real\" table";
        var ret = wrapColumnFromJoinedTable(name, column);
        propagateKeyField(column, ret);
        addColumn(ret);
        return ret;
    }

    @Override
    public MutableColumnInfo addWrapColumn(ColumnInfo column)
    {
        return addWrapColumn(column.getName(), column);
    }
}
