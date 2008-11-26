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
import org.labkey.api.exp.api.ContainerFilter;
import org.labkey.api.security.User;

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

    // container, containerFilter and user must all be set or null as they are cross-dependent
    private Container _container;
    private ContainerFilter _containerFilter;
    private User _user;

    public FilteredTable(TableInfo table)
    {
        super(table.getSchema());
        _filter = new SimpleFilter();
        _rootTable = table;
        _name = _rootTable.getName();
        _alias = _name;
        setTitleColumn(table.getTitleColumn());
    }

    public FilteredTable(TableInfo table, Container container, User user)
    {
        this(table, container, ContainerFilter.CURRENT, user);
    }

    public FilteredTable(TableInfo table, Container container, ContainerFilter containerFilter, User user)
    {
        this(table);

        if (container == null)
            throw new IllegalArgumentException("container cannot be null");
        _container = container;

        if (user == null && containerFilter != ContainerFilter.CURRENT) // CURRENT does not require a user
            throw new IllegalArgumentException("user cannot be null");
        _user = user;

        if (containerFilter == null)
            throw new IllegalArgumentException("containerFilter cannot be null");
        setContainerFilter(containerFilter);

    }

    public void wrapAllColumns(boolean preserveHidden)
    {
        for (ColumnInfo col : getRealTable().getColumns())
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

    public void addInClause(ColumnInfo col, Collection<?> params)
    {
        assert col.getParentTable() == _rootTable;
        SimpleFilter.InClause clause = new SimpleFilter.InClause(col.getValueSql().toString(), params);
        SQLFragment frag = clause.toSQLFragment(Collections.<String, ColumnInfo>emptyMap(), _schema.getSqlDialect());
        addCondition(frag);
    }

    public SQLFragment getFromSQL(String alias)
    {
        if (_filter.getWhereSQL(_rootTable.getSqlDialect()).length() == 0)
        {
            return getFromTable().getFromSQL(alias);
        }

        SQLFragment ret = new SQLFragment("(");
        ret.append(Table.getSelectSQL(getFromTable(), getFromTable().getColumns(), _filter, null));
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

    public void setContainerFilter(ContainerFilter filter)
    {
        // Bit of a hack here -- this table must already have a user or we're toast
        // In the future, we should require a user on construction, perhaps store a UserSchema object?
        if (_user == null && filter != ContainerFilter.CURRENT)
            throw new IllegalStateException("Cannot add a ContainerFilter unless this object was constructed with a user");

        _containerFilter = filter;
        ColumnInfo containerColumn = _rootTable.getColumn("container");
        if (containerColumn != null)
        {
            clearConditions(containerColumn);
            Collection<String> ids = filter.getIds(getContainer(), _user);
            if (ids != null)
            {
                addCondition(new SimpleFilter(new SimpleFilter.InClause("Container", ids)));
            }
        }
    }

    protected ContainerFilter createLazyContainerFilter()
    {
        return new ContainerFilter()
        {
            public Collection<String> getIds(Container currentContainer, User user)
            {
                return _containerFilter.getIds(currentContainer, user);
            }
        };
    }

    public ContainerFilter getContainerFilter()
    {
        return _containerFilter;
    }

    public Container getContainer()
    {
        return _container;
    }

    public boolean isContainerFilterNeeded()
    {
        return getContainerFilter() == null;
    }


    
}
