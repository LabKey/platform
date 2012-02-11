/*
 * Copyright (c) 2011-2012 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.query.QueryService;
import org.labkey.api.util.PageFlowUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

// TODO: cache, for display, async, etc.
public class TableSelector extends BaseSelector<TableSelector.TableSqlFactory>
{
    private final TableInfo _table;
    private final Collection<ColumnInfo> _columns;
    private final @Nullable Filter _filter;
    private final @Nullable Sort _sort;

    private int _rowCount = Table.ALL_ROWS;
    private long _offset = Table.NO_OFFSET;

    // Select specified columns from a table
    public TableSelector(TableInfo table, Collection<ColumnInfo> columns, @Nullable Filter filter, @Nullable Sort sort)
    {
        super(table.getSchema().getScope());
        _table = table;
        _columns = columns;
        _filter = filter;
        _sort = sort;
    }

    // Select all columns from a table, no filter or sort
    public TableSelector(TableInfo table)
    {
        this(table, Table.ALL_COLUMNS, null, null);
    }

    // Select all columns from a table
    public TableSelector(TableInfo table, @Nullable Filter filter, @Nullable Sort sort)
    {
        this(table, Table.ALL_COLUMNS, filter, sort);
    }

    // Select specified columns from a table
    public TableSelector(TableInfo table, Set<String> columnNames, @Nullable Filter filter, @Nullable Sort sort)
    {
        this(table, Table.columnInfosList(table, columnNames), filter, sort);
    }

    // Select a single column -- not sure this is useful
    public TableSelector(ColumnInfo column, @Nullable Filter filter, @Nullable Sort sort)
    {
        this(column.getParentTable(), PageFlowUtil.set(column), filter, sort);
    }

    public TableSelector setRowCount(int rowCount)
    {
        assert Table.validMaxRows(rowCount) : rowCount + " is an illegal value for rowCount; should be positive, Table.ALL_ROWS or Table.NO_ROWS";

        _rowCount = rowCount;
        return this;
    }

    public TableSelector setOffset(long offset)
    {
        assert Table.validOffset(offset) : offset + " is an illegal value for offset; should be positive or Table.NO_OFFSET";

        _offset = offset;
        return this;
    }

    // pk can be single value or an array of values
    public <K> K getObject(Object pk, Class<K> clazz)
    {
        return getObject(null, pk, clazz);
    }

    // pk can be single value or an array of values
    public <K> K getObject(@Nullable Container c, Object pk, Class<K> clazz)
    {
        List<ColumnInfo> pkColumns = _table.getPkColumns();
        Object[] pks;

        SimpleFilter filter = new SimpleFilter(_filter);
        if (pk instanceof SimpleFilter)
        {
            filter.addAllClauses((SimpleFilter)pk);
        }
        else
        {
            if (null != pk && pk.getClass().isArray())
                pks = (Object[]) pk;
            else
                pks = new Object[]{pk};

            assert pks.length == pkColumns.size() : "Wrong number of primary keys specified";

            for (int i = 0; i < pkColumns.size(); i++)
                filter.addCondition(pkColumns.get(i), pks[i]);
        }

        if (null != c)
            filter.addCondition("container", c);

        // Ignore the sort -- we're just getting one object
        TableSqlFactory tableSqlGetter = new PreventSortTableSqlFactory(filter, _columns);

        return getObject(clazz, tableSqlGetter);
    }

    @Override
    public long getRowCount()
    {
        // Ignore specified columns; only need to select PK column(s)
        // Ignore sort -- a waste of time (at best) or a SQLException (on SQL Server)
        TableSqlFactory tableSqlGetter = new PreventSortTableSqlFactory(_filter, _table.getPkColumns());
        return super.getRowCount(tableSqlGetter) - tableSqlGetter._scrollOffset;      // Corner case -- asking for rowCount with offset on a dialect that doesn't support offset
    }

    @Override
    TableSqlFactory getSqlFactory()
    {
        // Return the standard SQL factory (a TableSqlFactory); exposed methods can create custom factories (or wrap
        // this one) to optimize specific queries (see getRowCount() and getObject()).
        return new TableSqlFactory(_filter, _sort, _columns, true);
    }


    protected class TableSqlFactory extends BaseSqlFactory
    {
        private final @Nullable Filter _filter;
        private final @Nullable Sort _sort;
        private final Collection<ColumnInfo> _columns;
        private final boolean _allowSort;
        private long _scrollOffset = 0;

        public TableSqlFactory(@Nullable Filter filter, @Nullable Sort sort, Collection<ColumnInfo> columns, boolean allowSort)
        {
            super(TableSelector.this);
            _filter = filter;
            _sort = allowSort ? sort : null;    // Ensure consistency
            _columns = columns;
            _allowSort = allowSort;
        }

        @Override      // Note: This method refers to _table, _offset, and _rowCount from parent; the other fields are from this class.
        public SQLFragment getSql()
        {
            // NOTE: When ResultSet is supported, we'll need to select one extra row to support isComplete(). Factory will
            // need to know that a ResultSet was requested

            boolean forceSort = _allowSort && (_offset != Table.NO_OFFSET || _rowCount != Table.ALL_ROWS);

            if (_offset != Table.NO_OFFSET || _table.getSqlDialect().supportsOffset())
            {
                // Standard case is simply to create SQL using the rowCount and offset

                _scrollOffset = 0;
                return QueryService.get().getSelectSQL(_table, _columns, _filter, _sort, _rowCount, _offset, forceSort);
            }
            else
            {
                // We've asked for offset but the dialect's SQL doesn't support it, so implement offset manually:
                // - Select offset + rowCount rows
                // - Set _scrollOffset so getResultSet() skips over the rows we don't want

                _scrollOffset = _offset;
                return QueryService.get().getSelectSQL(_table, _columns, _filter, _sort, (int)_offset + _rowCount, 0, forceSort);
            }
        }

        @Override
        protected void processResultSet(ResultSet rs) throws SQLException
        {
            // Special handling for dialects that don't support offset
            while (_scrollOffset > 0 && rs.next())
                _scrollOffset--;
        }
    }


    protected class PreventSortTableSqlFactory extends TableSqlFactory
    {
        public PreventSortTableSqlFactory(Filter filter, Collection<ColumnInfo> columns)
        {
            // Really don't include a sort for this query
            super(filter, null, columns, false);
        }
    }
}
