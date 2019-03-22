/*
 * Copyright (c) 2015 LabKey Corporation
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
package org.labkey.api.study;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.UserSchema;

import java.util.Collection;


public class UnionTable extends AbstractTableInfo
{
    final UserSchema _studyQuerySchema;     // really a StudyQuerySchema
    final SQLFragment _sqlInner;
    final TableInfo _componentTable;       // one of the unioned tables

    public UnionTable(UserSchema studyQuerySchema, String tableName, Collection<ColumnInfo> cols, SQLFragment sqlf,
                      TableInfo componentTable, String titleColumn)
    {
        this(studyQuerySchema, tableName, cols, sqlf, componentTable);
        _titleColumn = titleColumn;
    }

    public UnionTable(UserSchema studyQuerySchema, String tableName, Collection<ColumnInfo> cols, SQLFragment sqlf, TableInfo componentTable)
    {
        super(studyQuerySchema.getDbSchema(), tableName);
        _studyQuerySchema = studyQuerySchema;
        _componentTable = componentTable;
        for (ColumnInfo col : cols)
        {
            col.setParentTable(this);
            addColumn(col);
        }
        _sqlInner = sqlf;
        setDefaultVisibleColumns(_componentTable.getDefaultVisibleColumns());
    }

    @Override
    public boolean isPublic()
    {
        return false;
    }

    @Override
    protected SQLFragment getFromSQL()
    {
        return _sqlInner;
    }

    @Nullable
    @Override
    public UserSchema getUserSchema()
    {
        return _studyQuerySchema;
    }

    @Override
    public boolean needsContainerClauseAdded()
    {
        return false;
    }

    public TableInfo getComponentTable()
    {
        return _componentTable;
    }
}

