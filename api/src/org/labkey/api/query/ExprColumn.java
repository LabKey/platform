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

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;

import java.util.Map;
import java.util.function.Supplier;

/**
 * {@link ColumnInfo} backed by a {@link SQLFragment} with the expression to generate the desired value. A typical way
 * to inject calculated columns into a {@link TableInfo}.
 */
public class ExprColumn extends BaseColumnInfo
{
    /** Placeholder that is later substituted with the table/subquery alias during SQL generation */
    public static final String STR_TABLE_ALIAS = "'''~~TABLE~~'''";

    protected SQLFragment _sql;
    private final ColumnInfo[] _dependentColumns;

    public ExprColumn(TableInfo parent, FieldKey key, SQLFragment sql, JdbcType type, ColumnInfo ... dependentColumns)
    {
        super(key, parent);
        setJdbcType(type);
        _sql = sql;
        if (dependentColumns != null)
        {
            for (ColumnInfo dependentColumn : dependentColumns)
            {
                if (dependentColumn == null)
                {
                    throw new NullPointerException("Dependent columns may not be null");
                }
            }
        }
        // Since these are typically calculated columns, it doesn't make sense to show them in update or insert views
        setShownInUpdateView(false);
        setShownInInsertView(false);
        setUserEditable(false);
        setCalculated(true);
        // Unless otherwise configured, guess that it might be nullable
        setNullable(true);
        _dependentColumns = dependentColumns;
    }

    
    public ExprColumn(TableInfo parent, String name, SQLFragment sql, JdbcType type, ColumnInfo ... dependentColumns)
    {
        this(parent, FieldKey.fromParts(name), sql, type, dependentColumns);
    }

    public static MutableColumnInfo create(TableInfo parent, String name, JdbcType type, SQLFragment sqlf, ColumnInfo ... dependentColumns)
    {
        return new ExprColumn(parent, name, sqlf, type, dependentColumns);
    }


    /*
     * Use this 'constructor' to create an ExprColumn whose SQL is generated on demand.  This is useful if generating
     * the SQL is at all expensive.  Most TableInfo objects are not used for generating SQL and do not need to get this value.
     */
    public static MutableColumnInfo create(TableInfo parent, FieldKey name, JdbcType type, Supplier<SQLFragment> sqlFn, ColumnInfo ... dependentColumns)
    {
        return new ExprColumn(parent, name, null, type, dependentColumns)
        {
            @Override
            public SQLFragment getValueSql(String tableAlias)
            {
                if (null == _sql)
                    _sql = sqlFn.get();
                return super.getValueSql(tableAlias);
            }
        };
    }


    @Override
    public SQLFragment getValueSql(String tableAlias)
    {
        if (tableAlias.equals(STR_TABLE_ALIAS))
            return _sql;
        String sql = StringUtils.replace(_sql.getSQL(), STR_TABLE_ALIAS, tableAlias);
        SQLFragment ret = new SQLFragment(sql);
        ret.addAll(_sql.getParams());
        return ret;
    }


    public void setValueSQL(SQLFragment sql)
    {
        _sql = sql;
    }

    
    @Override
    public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
    {
        if (_dependentColumns != null)
        {
            for (ColumnInfo col : _dependentColumns)
            {
                col.declareJoins(parentAlias, map);
            }
        }
    }
}
