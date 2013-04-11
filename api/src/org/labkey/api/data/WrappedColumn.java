/*
 * Copyright (c) 2008-2012 LabKey Corporation
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

import org.labkey.api.query.ExprColumn;

/**
 * User: jeckels
 * Date: Nov 11, 2008
 */
public class WrappedColumn extends ExprColumn
{
    private final ColumnInfo _col;

    public WrappedColumn(ColumnInfo col, String name)
    {
        super(col.getParentTable(), name, col.getValueSql(ExprColumn.STR_TABLE_ALIAS), col.getJdbcType());
        // Need to propagate if this is a special kind of column, like "entityid" or "userid"
        setSqlTypeName(col.getSqlTypeName());
        setCalculated(true);
        _col = col;
    }

    @Override
    public SQLFragment getValueSql(String tableAlias)
    {
        return _col.getValueSql(tableAlias);
    }

    @Override
    public boolean isShownInInsertView()
    {
        // By definition, you can't set a value on a wrapped column. Set directly on the source column instead.
        return false;
    }

    @Override
    public boolean isShownInUpdateView()
    {
        // By definition, you can't set a value on a wrapped column. Set directly on the source column instead.
        return false;
    }
}