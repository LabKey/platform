/*
 * Copyright (c) 2014-2015 LabKey Corporation
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
package org.labkey.api.data;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.QueryForeignKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * User: kevink
 * Date: 4/4/14
 *
 * Convenience class to add columns from the extension table to the primary table.
 */
public final class TableExtension
{
    private final AbstractTableInfo _primaryTable;
    private final TableInfo _extensionTable;
    private final ColumnInfo _extensionCol;
    private final QueryForeignKey _extensionFK;

    protected TableExtension(AbstractTableInfo primaryTable, TableInfo extensionTable, ColumnInfo extensionCol, QueryForeignKey extensionFK)
    {
        _primaryTable = primaryTable;
        _extensionTable = extensionTable;
        _extensionCol = extensionCol;
        _extensionFK = extensionFK;
    }

    public static TableExtension create(AbstractTableInfo primaryTable, TableInfo extensionTable)
    {
        String pkColumn = extensionTable.getPkColumnNames().get(0); // Note this only works for simple primary keys, not compound.
        String fkColumn = extensionTable.getColumn(pkColumn).getFk().getLookupColumnName();

        return create(primaryTable, extensionTable, fkColumn, pkColumn, LookupColumn.JoinType.leftOuter);
    }

    public static TableExtension create(AbstractTableInfo primaryTable, TableInfo extensionTable, String foreignKey, String lookupKey, LookupColumn.JoinType joinType)
    {
        ColumnInfo extensionCol = primaryTable.getColumn(foreignKey);
        assert extensionCol != null;

        QueryForeignKey extensionFK = new QueryForeignKey(extensionTable, null, lookupKey, null);
        extensionFK.setJoinType(joinType);

        return new TableExtension(primaryTable, extensionTable, extensionCol, extensionFK);
    }

    public TableInfo getExtensionTable()
    {
        return _extensionTable;
    }

    public Collection<ColumnInfo> addAllColumns()
    {
        String lookupKey = getLookupColumnName();
        List<ColumnInfo> baseColumns = _extensionTable.getColumns();
        Collection<ColumnInfo> columns = new ArrayList<>(baseColumns.size());
        for (ColumnInfo col : baseColumns)
        {
            // Skip the lookup column itself
            if (col.getName().equalsIgnoreCase(lookupKey))
                continue;

            ColumnInfo lookupCol = addExtensionColumn(col, col.getName());
            columns.add(lookupCol);
        }

        return columns;
    }

    public ColumnInfo addExtensionColumn(String baseColName, @Nullable String newColName)
    {
        ColumnInfo baseCol = _extensionTable.getColumn(baseColName);
        return addExtensionColumn(baseCol, newColName);
    }

    public ColumnInfo addExtensionColumn(ColumnInfo baseCol, @Nullable String newColName)
    {
        ColumnInfo aliased = wrapExtensionColumn(baseCol, newColName);
        return _primaryTable.addColumn(aliased);
    }

    public ColumnInfo wrapExtensionColumn(String baseColName, @Nullable String newColName)
    {
        ColumnInfo baseCol = _extensionTable.getColumn(baseColName);
        return wrapExtensionColumn(baseCol, newColName);
    }

    public ColumnInfo wrapExtensionColumn(ColumnInfo baseCol, @Nullable String newColName)
    {
        newColName = Objects.toString(newColName, baseCol.getName());

        ColumnInfo lookupCol = _extensionFK.createLookupColumn(_extensionCol, baseCol.getName());
        AliasedColumn aliased = new AliasedColumn(_primaryTable, newColName, lookupCol);
        if (lookupCol.isHidden() || baseCol.isHidden())
            aliased.setHidden(true);

        return aliased;
    }

    public String getLookupColumnName()
    {
        return _extensionFK.getLookupColumnName();
    }

}
