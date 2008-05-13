/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;

import java.util.Map;

public class AliasedColumn extends ColumnInfo
{
    ColumnInfo _column;
    public AliasedColumn(TableInfo parent, String name, ColumnInfo column)
    {
        super(name, parent);
        setName(name);
        setAlias(name);
        copyAttributesFrom(column);
        if (!name.equalsIgnoreCase(column.getName()))
        {
            setCaption(null);
        }
        _column = column;
    }

    public AliasedColumn(String name, ColumnInfo column)
    {
        this(column.getParentTable(), name, column);
    }

    public SQLFragment getValueSql()
    {
        if (getParentTable() == _column.getParentTable())
        {
            return _column.getValueSql();
        }
        return super.getValueSql();
    }

    public SQLFragment getValueSql(String tableAlias)
    {
        if (getParentTable() == _column.getParentTable())
        {
            return _column.getValueSql(tableAlias);
        }
        SQLFragment ret = new SQLFragment();
        ret.append(tableAlias);
        ret.append(".");
        ret.append(getSqlDialect().getColumnSelectName(_column.getAlias()));
        return ret;
    }

    public void declareJoins(Map<String, SQLFragment> map)
    {
        if (getParentTable() == _column.getParentTable())
            _column.declareJoins(map);
    }

    public ColumnInfo getColumn()
    {
        return _column;
    }
}
