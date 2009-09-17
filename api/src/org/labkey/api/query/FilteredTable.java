/*
 * Copyright (c) 2006-2009 LabKey Corporation
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
import org.labkey.api.data.*;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.ActionURL;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * A table that filters down to a particular set of values. A typical example
 * would be filtering to only show rows that are part of a particular container.
 */
public class FilteredTable extends AbstractTableInfo implements ContainerFilterable
{
    final private SimpleFilter _filter;
    protected TableInfo _rootTable;

    private Container _container;

    @Nullable // if null, means default
    private ContainerFilter _containerFilter;

    private boolean _public = true;

    public FilteredTable(TableInfo table)
    {
        super(table.getSchema());
        _filter = new SimpleFilter();
        _rootTable = table;
        _name = _rootTable.getName();
        _description = _rootTable.getDescription();
        setTitleColumn(table.getTitleColumn());
    }

    public FilteredTable(TableInfo table, Container container)
    {
        this(table, container, null);
    }

    public FilteredTable(TableInfo table, Container container, ContainerFilter containerFilter)
    {
        this(table);

        if (container == null)
            throw new IllegalArgumentException("container cannot be null");
        _container = container;

        if (containerFilter != null)
            setContainerFilter(containerFilter);
        else
            applyContainerFilter(ContainerFilter.CURRENT);
    }

    public void wrapAllColumns(boolean preserveHidden)
    {
        for (ColumnInfo col : getRealTable().getColumns())
        {
            ColumnInfo newCol = addWrapColumn(col);
            if (preserveHidden && col.isHidden())
            {
                newCol.setHidden(col.isHidden());
            }
        }
    }

    public TableInfo getRealTable()
    {
        return _rootTable;
    }

    @Override
    public ActionURL getGridURL(Container container)
    {
        ActionURL url = super.getGridURL(container);
        return url != null ? url : getRealTable().getGridURL(container);
    }

    @Override
    public ActionURL getInsertURL(Container container)
    {
        ActionURL url = super.getInsertURL(container);
        return url != null ? url : getRealTable().getInsertURL(container);
    }

    @Override
    public StringExpression getUpdateURL(Map<String, ColumnInfo> columns, Container container)
    {
        StringExpression expr = super.getUpdateURL(columns, container);
        return expr != null ? expr : getRealTable().getUpdateURL(columns, container);
    }

    @Override
    public StringExpression getDetailsURL(Map<String, ColumnInfo> columns, Container container)
    {
        StringExpression expr = super.getDetailsURL(columns, container);
        return expr != null ? expr : getRealTable().getDetailsURL(columns, container);
    }

    private String filterName(ColumnInfo c)
	{
		return c.getAlias();
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
        ret.setLabel(ColumnInfo.labelFromName(alias));
        if (underlyingColumn.isKeyField() && getColumn(underlyingColumn.getName()) != null)
        {
            ret.setKeyField(false);
        }
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
        assert col.getParentTable() == _rootTable : "Column is from the wrong table";
        // This CAST improves performance on Postgres for some queries by choosing a more efficient query plan
        SQLFragment frag = new SQLFragment();
        frag.append(filterName(col));
        frag.append(" = CAST(");
        frag.appendStringLiteral(container.getId());
        frag.append(" AS UniqueIdentifier)");
        addCondition(frag, col.getName());
    }

    public void addCondition(ColumnInfo col, String value)
    {
        assert col.getParentTable() == _rootTable;
        SQLFragment frag = new SQLFragment();
        frag.append(filterName(col));
        frag.append(" = ");
        frag.appendStringLiteral(value);
        addCondition(frag, col.getName());
    }

    public void addCondition(ColumnInfo col, int value)
    {
        assert col.getParentTable() == _rootTable : "Column is from the wrong table";
        SQLFragment frag = new SQLFragment();
        frag.append(filterName(col));
        frag.append(" = ");
        frag.append(Integer.toString(value));
        addCondition(frag);
    }

    public void addCondition(ColumnInfo col, float value)
    {
        assert col.getParentTable() == _rootTable : "Column is from the wrong table";
        SQLFragment frag = new SQLFragment();
        frag.append(filterName(col));
        frag.append(" = ");
        frag.append(Float.toString(value));
        addCondition(frag);
    }

    public void addCondition(ColumnInfo col1, ColumnInfo col2)
    {
        assert col1.getParentTable() == col2.getParentTable() : "Column is from the wrong table";
        assert col1.getParentTable() == _rootTable : "Column is from the wrong table";
        SQLFragment frag = new SQLFragment();
        frag.append(filterName(col1));
        frag.append(" = ");
        frag.append(filterName(col2));
        addCondition(frag);
    }


    public void addInClause(ColumnInfo col, Collection<?> params)
    {
        assert col.getParentTable() == _rootTable : "Column is from the wrong table";
        SimpleFilter.InClause clause = new SimpleFilter.InClause(filterName(col), params);
        SQLFragment frag = clause.toSQLFragment(Collections.<String, ColumnInfo>emptyMap(), _schema.getSqlDialect());
        addCondition(frag);
    }


    @Override
    public String getSelectName()
    {
        if (_filter.getWhereSQL(_rootTable.getSqlDialect()).length() == 0)
            return getFromTable().getSelectName();
        return null;
    }
    

    @NotNull
    public SQLFragment getFromSQL()
    {
        if (_filter.getWhereSQL(_rootTable.getSqlDialect()).length() == 0)
            return getFromTable().getFromSQL();

        SQLFragment fromSQL = getFromTable().getFromSQL();
        Map<String, ColumnInfo> columnMap = Table.createColumnMap(getFromTable(), getFromTable().getColumns());
        SQLFragment filterFrag = _filter.getSQLFragment(_rootTable.getSqlDialect(), columnMap);

        String s = fromSQL.getSQL();
        boolean simple = s.startsWith("SELECT *") && -1 == s.indexOf("WHERE");
        if (simple)
            return fromSQL.append(" ").append(filterFrag);

        SQLFragment ret = new SQLFragment("SELECT * FROM (");
        ret.append(fromSQL);
        ret.append(") x ");
        ret.append(filterFrag);
        return ret;
    }


    public ColumnInfo addWrapColumn(ColumnInfo column)
    {
        assert column.getParentTable() == getRealTable();
        ColumnInfo ret = new AliasedColumn(this, column.getAlias(), column);
        if (column.isKeyField() && getColumn(column.getName()) != null)
        {
            ret.setKeyField(false);
        }
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

    public void setContainerFilter(@NotNull ContainerFilter filter)
    {
        //noinspection ConstantConditions
        if (filter == null) // this really can happen, if other callers ignore warnings
            throw new IllegalArgumentException("filter cannot be null");
        _containerFilter = filter;
        applyContainerFilter(_containerFilter);
    }

    private void applyContainerFilter(ContainerFilter filter)
    {
        ColumnInfo containerColumn = _rootTable.getColumn("container");
        if (containerColumn != null && getContainer() != null)
        {
            clearConditions(containerColumn);
            Collection<String> ids = filter.getIds(getContainer());
            if (ids != null)
            {
                addCondition(new SimpleFilter(new SimpleFilter.InClause("Container", ids)));
            }
        }
    }

    @NotNull
    public ContainerFilter getContainerFilter()
    {
        if (_containerFilter == null)
            return ContainerFilter.CURRENT;
        return _containerFilter;
    }

    /**
     * Returns true if the container filter has never been set on this table
     */
    public boolean hasDefaultContainerFilter()
    {
        return _containerFilter == null;
    }

    public Container getContainer()
    {
        return _container;
    }

    public boolean needsContainerClauseAdded()
    {
        return false;
    }

    @Override
    public String toString()
    {
        return "FilteredTable over " + _rootTable;
    }

    public boolean isPublic()
    {
        return _public;
    }

    public void setPublic(boolean aPublic)
    {
        _public = aPublic;
    }
}
