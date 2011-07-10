/*
 * Copyright (c) 2006-2011 LabKey Corporation
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

import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.*;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.ActionURL;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A table that filters down to a particular set of values. A typical example
 * would be filtering to only show rows that are part of a particular container.
 */
public class FilteredTable extends AbstractTableInfo implements ContainerFilterable
{
    final private SimpleFilter _filter;
    private String _innerAlias = null;
    protected TableInfo _rootTable;

    private Container _container;

    @Nullable // if null, means default
    private ContainerFilter _containerFilter;

    private boolean _public = true;

    // CAREFUL: This constructor does not take a container... call one of the other constructors to filter based on container
    // TODO: Should be protected?
    public FilteredTable(TableInfo table)
    {
        super(table.getSchema());
        _filter = new SimpleFilter();
        _rootTable = table;
        _name = _rootTable.getName();
        _description = _rootTable.getDescription();
        // UNDONE: lazy load button bar config????
        _buttonBarConfig = _rootTable.getButtonBarConfig();

        // We used to copy the titleColumn from table, but this forced all ColumnInfos to load.  Now, delegate
        // to _rootTable lazily, allowing overrides.
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

    @Override
    public String getTitleColumn()
    {
        lazyLoadTitleColumnProperties();
        return super.getTitleColumn();
    }

    @Override
    public boolean hasDefaultTitleColumn()
    {
        lazyLoadTitleColumnProperties();
        return super.hasDefaultTitleColumn();
    }


    private void lazyLoadTitleColumnProperties()
    {
        // If _titleColumn has not been set, take the settings from _rootTable
        if (null == _titleColumn)
        {
            _titleColumn = _rootTable.getTitleColumn();
            _hasDefaultTitleColumn = _rootTable.hasDefaultTitleColumn();
        }
    }


    // This is for special case where filter depends on alias
    // should probably only be used if a column from the _rootTable needs to be disambiguated
    public void setInnerAlias(String a)
    {
        _innerAlias = a;
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
    public ActionURL getImportDataURL(Container container)
    {
        ActionURL url = super.getImportDataURL(container);
        return url != null ? url : getRealTable().getImportDataURL(container);
    }

    @Override
    public ActionURL getDeleteURL(Container container)
    {
        ActionURL url = super.getDeleteURL(container);
        return url != null ? url : getRealTable().getDeleteURL(container);
    }

    @Override
    public StringExpression getUpdateURL(Set<FieldKey> columns, Container container)
    {
        StringExpression expr = super.getUpdateURL(columns, container);
        return expr != null ? expr : getRealTable().getUpdateURL(columns, container);
    }

    @Override
    public StringExpression getDetailsURL(Set<FieldKey> columns, Container container)
    {
        StringExpression expr = super.getDetailsURL(columns, container);
        return expr != null ? expr : getRealTable().getDetailsURL(columns, container);
    }

    @Override
    public boolean hasDetailsURL()
    {
        return super.hasDetailsURL() || getRealTable().hasDetailsURL();
    }

    @Override
    public Set<FieldKey> getDetailsURLKeys()
    {
        HashSet<FieldKey> ret = new HashSet<FieldKey>();
        Set<FieldKey> superKeys = super.getDetailsURLKeys();
        Set<FieldKey> realKeys = getRealTable().getDetailsURLKeys();
        ret.addAll(superKeys);
        ret.addAll(realKeys);
        return ret;
    }

    private String filterName(ColumnInfo c)
	{
		return c.getAlias();
	}

    final public void addCondition(SQLFragment condition, String... columnNames)
    {
        if (condition.isEmpty())
            return;
        _filter.addWhereClause("(" + condition.getSQL() + ")", condition.getParams().toArray(), columnNames);
    }

    public void addCondition(SimpleFilter filter)
    {
        _filter.addAllClauses(filter);
    }

    public ColumnInfo wrapColumn(String alias, ColumnInfo underlyingColumn)
    {
        assert underlyingColumn.getParentTable() == _rootTable;
        ExprColumn ret = new ExprColumn(this, alias, underlyingColumn.getValueSql(ExprColumn.STR_TABLE_ALIAS), underlyingColumn.getJdbcType());
        ret.copyAttributesFrom(underlyingColumn);
        ret.copyURLFrom(underlyingColumn, null, null);
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

    public void clearConditions(String columnName)
    {
        _filter.deleteConditions(columnName);
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
    public final SQLFragment getFromSQL()
    {
        throw new IllegalStateException();
    }


    @NotNull
    public SQLFragment getFromSQL(String alias)
    {
        if (_filter.getWhereSQL(_rootTable.getSqlDialect()).length() == 0)
            return getFromTable().getFromSQL(alias);

        // SELECT
        SQLFragment ret = new SQLFragment("(SELECT * FROM ");

        // FROM
        //   NOTE some filters depend on knowing the name of this table in the simple case, so don't alias it
        String selectName = _rootTable.getSelectName();
        if (null != selectName && null == _innerAlias)
            ret.append(selectName);
        else
            ret.append(getFromTable().getFromSQL(StringUtils.defaultString(_innerAlias,"x")));

        // WHERE
        Map<String, ColumnInfo> columnMap = Table.createColumnMap(getFromTable(), getFromTable().getColumns());
        SQLFragment filterFrag = _filter.getSQLFragment(_rootTable.getSqlDialect(), columnMap);
        ret.append("\n").append(filterFrag).append(") ").append(alias);
        return ret;
    }


    public ColumnInfo addWrapColumn(ColumnInfo column)
    {
        assert column.getParentTable() == getRealTable();
        ColumnInfo ret = new AliasedColumn(this, column.getName(), column);
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

    protected String getContainerFilterColumn()
    {
        return "Container";
    }

    protected void applyContainerFilter(ContainerFilter filter)
    {
        ColumnInfo containerColumn = _rootTable.getColumn(getContainerFilterColumn());
        if (containerColumn != null && getContainer() != null)
        {
            clearConditions(containerColumn.getName());
            Collection<String> ids = filter.getIds(getContainer());
            if (ids != null)
            {
                addCondition(new SimpleFilter(new SimpleFilter.InClause(getContainerFilterColumn(), ids)));
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

    @Override @NotNull
    public Collection<QueryService.ParameterDecl> getNamedParameters()
    {
        return _rootTable.getNamedParameters();
    }
}
