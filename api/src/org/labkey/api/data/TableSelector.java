/*
 * Copyright (c) 2011 LabKey Corporation
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

import org.labkey.api.util.PageFlowUtil;

import java.util.Collection;
import java.util.Set;

// TODO: NYI
public class TableSelector
{
    private final TableInfo _table;
    private final Collection<ColumnInfo> _columns;

    // Select specified columns from a table
    public TableSelector(TableInfo table, Collection<ColumnInfo> columns)
    {
        _table = table;
        _columns = columns;
    }

    // Select all columns from a table
    public TableSelector(TableInfo table)
    {
        this(table, Table.ALL_COLUMNS);
    }

    // Select specified columns from a table
    public TableSelector(TableInfo table, Set<String> columnNames)
    {
        this(table, Table.columnInfosList(table, columnNames));
    }

    // Select a single column -- not sure this is useful
    public TableSelector(ColumnInfo column)
    {
        this(column.getParentTable(), PageFlowUtil.set(column));
    }
}
