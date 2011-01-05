/*
 * Copyright (c) 2006-2010 LabKey Corporation
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

import org.labkey.api.data.*;

import java.util.List;
import java.util.ArrayList;

abstract public class AbstractMethodInfo implements MethodInfo
{
    protected JdbcType _jdbcType;
    
    public AbstractMethodInfo(JdbcType jdbcType)
    {
        _jdbcType = jdbcType;
    }

    protected JdbcType getSqlType(ColumnInfo[] arguments)
    {
        return _jdbcType;
    }

    public ColumnInfo createColumnInfo(TableInfo parentTable, ColumnInfo[] arguments, String alias)
    {
        return new ExprColumn(parentTable, alias, getSQL(ExprColumn.STR_TABLE_ALIAS, parentTable.getSchema(), getSQLFragments(arguments)), getSqlType(arguments).sqlType);
    }

    public ColumnInfo createColumnInfo(TableInfo parentTable, ColumnInfo[] arguments, String alias, int sqlType)
    {
        return new ExprColumn(parentTable, alias, getSQL(ExprColumn.STR_TABLE_ALIAS, parentTable.getSchema(), getSQLFragments(arguments)), sqlType);
    }

    protected SQLFragment[] getSQLFragments(ColumnInfo[] arguments)
    {
        List<SQLFragment> ret = new ArrayList();
        for (ColumnInfo col : arguments)
        {
            ret.add(col.getValueSql(ExprColumn.STR_TABLE_ALIAS));
        }
        return ret.toArray(new SQLFragment[0]);
    }

    public SQLFragment getSQL(String tableAlias, DbSchema schema, SQLFragment[] arguments)
    {
        return getSQL(schema, arguments);
    }
}
