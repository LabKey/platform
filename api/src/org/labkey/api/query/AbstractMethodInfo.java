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

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ColumnLogging;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MethodInfo;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;

import java.util.ArrayList;
import java.util.List;

abstract public class AbstractMethodInfo implements MethodInfo
{
    protected JdbcType _jdbcType;
    
    public AbstractMethodInfo(JdbcType jdbcType)
    {
        _jdbcType = jdbcType;
    }

    protected JdbcType getSqlType(ColumnInfo[] arguments)
    {
        JdbcType[] types = null==arguments ? new JdbcType[0] : new JdbcType[arguments.length];
        for (int i=0 ; i<types.length ; i++)
            types[i] = arguments[i].getJdbcType();
        return getJdbcType(types);
    }

    @Override
    public JdbcType getJdbcType(JdbcType[] args)
    {
        return _jdbcType;
    }

    @Override
    public MutableColumnInfo createColumnInfo(TableInfo parentTable, final ColumnInfo[] arguments, String alias)
    {
        return new ExprColumn(parentTable, alias, new SQLFragment("{{" + this.getClass().getSimpleName() + "}}"), getSqlType(arguments))
        {
            @Override
            public SQLFragment getValueSql(String tableAlias)
            {
                return getSQL(tableAlias, getParentTable().getSchema(), getSQLFragments(arguments));
            }

            @Override
            public ColumnLogging getColumnLogging()
            {
                return arguments.length > 0 ? arguments[0].getColumnLogging() : super.getColumnLogging();
            }
        };
    }

    protected SQLFragment[] getSQLFragments(ColumnInfo[] arguments)
    {
        List<SQLFragment> ret = new ArrayList<>();
        for (ColumnInfo col : arguments)
        {
            ret.add(col.getValueSql(ExprColumn.STR_TABLE_ALIAS));
        }
        return ret.toArray(new SQLFragment[0]);
    }

    @Override
    public SQLFragment getSQL(String tableAlias, DbSchema schema, SQLFragment[] arguments)
    {
        return getSQL(schema.getSqlDialect(), arguments);
    }
}
