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

import org.labkey.api.data.*;
import java.sql.Types;
import java.util.Collection;
import java.util.Collections;

/**
 * A table that filters down to a particular set of values. A typical example
 * would be filtering to only show rows that are part of a particular container.
 */
public class FilteredTable extends AbstractTableInfo
{
    final private SimpleFilter _filter;
    protected TableInfo _rootTable;

    public FilteredTable(TableInfo table)
    {
        super(table.getSchema());
        _filter = new SimpleFilter();
        _rootTable = table;
        _name = _rootTable.getName();
        _alias = _name;
        setTitleColumn(table.getTitleColumn());
    }

    public FilteredTable(TableInfo table, Container container)
    {
        this(table);
        if (container != null)
            addCondition(table.getColumn("Container"), container);
    }

    public void wrapAllColumns(boolean preserveHidden)
    {
        for (ColumnInfo col : getRealTable().getColumnsList())
        {
            ColumnInfo newCol = addWrapColumn(col);
            if (preserveHidden && col.isHidden())
            {
                newCol.setIsHidden(col.isHidden());
            }
        }
    }

    public TableInfo getRealTable()
    {
        return _rootTable;
    }

    final public void addCondition(SQLFragment condition, String... columnNames)
    {
        _filter.addWhereClause("(" + condition.getSQL() + ")", condition.getParams().toArray(), columnNames);
    }

    public void addCondition(SimpleFilter filter)
    {
        _filter.addAllClauses(filter);
    }

    public ColumnInfo wrapColumn(String alias, ColumnInfo underlyingColumn)
    {
        assert underlyingColumn.getParentTable() == _rootTable;
        ExprColumn ret = new ExprColumn(this, alias, underlyingColumn.getValueSql(ExprColumn.STR_TABLE_ALIAS), underlyingColumn.getSqlTypeInt());
        ret.copyAttributesFrom(underlyingColumn);
        ret.setCaption(ColumnInfo.captionFromName(alias));
        return ret;
    }

    public ColumnInfo wrapColumn(ColumnInfo underlyingColumn)
    {
        return wrapColumn(underlyingColumn.getName(), underlyingColumn);
    }

    public void clearConditions(ColumnInfo col)
    {
        _filter.deleteConditions(col.getName());
    }

    public void addCondition(ColumnInfo col, Container container)
    {
        assert col.getParentTable() == _rootTable;
        // This CAST improves performance on Postgres for some queries by choosing a more efficient query plan
        SQLFragment frag = new SQLFragment();
        frag.append(col.getValueSql());
        frag.append(" = CAST(");
        frag.appendStringLiteral(container.getId());
        frag.append(" AS UniqueIdentifier)");
        addCondition(frag, col.getName());
    }

    public void addCondition(ColumnInfo col, String value)
    {
        assert col.getParentTable() == _rootTable;
        SQLFragment frag = new SQLFragment();
        frag.append(col.getValueSql());
        frag.append(" = ");
        frag.appendStringLiteral(value);
        addCondition(frag, col.getName());
    }

    public void addCondition(ColumnInfo col, int value)
    {
        assert col.getParentTable() == _rootTable;
        SQLFragment frag = new SQLFragment();
        frag.append(col.getValueSql());
        frag.append(" = ");
        frag.append(Integer.toString(value));
        addCondition(frag);
    }

    public void addCondition(ColumnInfo col, float value)
    {
        assert col.getParentTable() == _rootTable;
        SQLFragment frag = new SQLFragment();
        frag.append(col.getValueSql());
        frag.append(" = ");
        frag.append(Float.toString(value));
        addCondition(frag);
    }

    public void addCondition(ColumnInfo col1, ColumnInfo col2)
    {
        assert col1.getParentTable() == col2.getParentTable();
        assert col1.getParentTable() == _rootTable;
        SQLFragment frag = new SQLFragment();
        frag.append(col1.getValueSql());
        frag.append(" = ");
        frag.append(col2.getValueSql());
        addCondition(frag);
    }

    public void addInClause(ColumnInfo col, Collection<? extends Object> params)
    {
        assert col.getParentTable() == _rootTable;
        SimpleFilter.InClause clause = new SimpleFilter.InClause(col.getValueSql().toString(), params);
        SQLFragment frag = clause.toSQLFragment(Collections.<String, ColumnInfo>emptyMap(), _schema.getSqlDialect());
        addCondition(frag);
    }

    public QueryMethod resolveMethod(String name, SQLFragment ... args)
    {
        throw new QueryException("Unknown method '" + name + "'");
    }

    public SQLFragment getSqlMethod(String name, SQLFragment ... args)
    {
        //return resolveMethod(name, arguments).getSql(arguments);
        return null;
    }

    public String getCaption(String fieldName)
    {
        return ColumnInfo.captionFromName(fieldName);
    }

    public int getSqlType(String fieldName)
    {
        return Types.VARCHAR;
    }

    public SQLFragment getFromSQL(String alias)
    {
        if (_filter.getWhereSQL(_rootTable.getSqlDialect()).length() == 0)
        {
            return getFromTable().getFromSQL(alias);
        }

        SQLFragment ret = new SQLFragment("(");
        ret.append(Table.getSelectSQL(getFromTable(), getFromTable().getColumnsList(), _filter, null));
        ret.append(")");
        ret.append(" AS " + alias);
        return ret;
    }

    public ColumnInfo addWrapColumn(ColumnInfo column)
    {
        assert column.getParentTable() == getRealTable();
        ColumnInfo ret = new AliasedColumn(this, column.getAlias(), column);
        addColumn(ret);
        return ret;
    }

    protected TableInfo getFromTable()
    {
        return getRealTable();
    }

    protected SimpleFilter getFilter()
    {
        return _filter;
    }
}
