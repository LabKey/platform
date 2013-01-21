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
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.util.PageFlowUtil;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

public class TableSelector extends ExecutingSelector<TableSelector.TableSqlFactory, TableSelector>
{
    private final TableInfo _table;
    private final Collection<ColumnInfo> _columns;
    private final @Nullable Filter _filter;
    private final @Nullable Sort _sort;

    private boolean _forDisplay = false;

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

    // Select a single column
    public TableSelector(ColumnInfo column, @Nullable Filter filter, @Nullable Sort sort)
    {
        this(column.getParentTable(), PageFlowUtil.set(column), filter, sort);
    }

    // Select a single column from all rows
    public TableSelector(ColumnInfo column)
    {
        this(column, null, null);
    }

    @Override
    protected TableSelector getThis()
    {
        return this;
    }

    /**
     * If no transaction is active and the SQL statement is a SELECT, this method assumes it is safe to tweak
     * connection parameters (such as disabling auto-commit, and never committing) to optimize memory and other
     * resource usage.
     *
     * If you are, for example, invoking a stored procedure that will have side effects via a SELECT statement,
     * you must explicitly start your own transaction and commit it.
     */
    public Results getResults() throws SQLException
    {
        return getResults(true);
    }

    public Results getResults(boolean cache) throws SQLException
    {
        return getResults(cache, false);
    }

    /**
     * If no transaction is active and the SQL statement is a SELECT, this method assumes it is safe to tweak
     * connection parameters (such as disabling auto-commit, and never committing) to optimize memory and other
     * resource usage.
     *
     * If you are, for example, invoking a stored procedure that will have side effects via a SELECT statement,
     * you must explicitly start your own transaction and commit it.
     */

    public Results getResults(boolean cache, boolean scrollable) throws SQLException
    {
        boolean closeResultSet = cache;
        TableSqlFactory tableSqlFactory = getSqlFactory(true);
        ExecutingResultSetFactory factory = new ExecutingResultSetFactory(tableSqlFactory, closeResultSet, scrollable, true);
        ResultSet rs = getResultSet(factory, cache);

        return new ResultsImpl(rs, tableSqlFactory.getSelectedColumns());
    }

    public Results getResultsAsync(final boolean cache, final boolean scrollable, HttpServletResponse response) throws IOException, SQLException
    {
        setLogger(ConnectionWrapper.getConnectionLogger());
        AsyncQueryRequest<Results> asyncRequest = new AsyncQueryRequest<Results>(response);
        setAsyncRequest(asyncRequest);

        return asyncRequest.waitForResult(new Callable<Results>()
        {
            public Results call() throws Exception
            {
                return getResults(cache, scrollable);
            }
        });
    }

    public TableSelector setForDisplay(boolean forDisplay)
    {
        _forDisplay = forDisplay;
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

        return getObject(clazz, new ExecutingResultSetFactory(tableSqlGetter));
    }

    @Override
    public long getRowCount()
    {
        // TODO: Shouldn't actually need the sub-query in the TableSelector case... just use a "COUNT(*)" ExprColumn directly with the filter + table
        // For now, produce "SELECT 1 FROM ..." in the sub-select and ignore the sort
        TableSqlFactory sqlFactory = new RowCountingSqlFactory(_table, _filter);
        return super.getRowCount(sqlFactory) - sqlFactory._scrollOffset;      // Corner case -- asking for rowCount with offset on a dialect that doesn't support offset
    }

    @Override
    public boolean exists()
    {
        // Produce "SELECT 1 FROM ..." in the sub-select and ignore the sort
        TableSqlFactory sqlFactory = new RowCountingSqlFactory(_table, _filter);

        if (sqlFactory.requiresManualScrolling())
            return getRowCount() > 0;  // Obscure case of using exists with offset in database that doesn't natively support offset... can't use EXISTS query in this case
        else
            return super.exists(sqlFactory);  // Normal case... wrap an EXISTS query around the "SELECT 1 FROM..." sub-select
    }

    // TODO: forEachFieldKeyMap()

    // TODO: Convert to return Map<FieldKey, List<Aggregate.Result>>
    public Map<String, List<Aggregate.Result>> getAggregates(final List<Aggregate> aggregates)
    {
        AggregateSqlFactory sqlFactory = new AggregateSqlFactory(_filter, aggregates, _columns);
        ResultSetFactory resultSetFactory = new ExecutingResultSetFactory(sqlFactory);

        return handleResultSet(resultSetFactory, new ResultSetHandler<Map<String, List<Aggregate.Result>>>()
        {
            @Override
            public Map<String, List<Aggregate.Result>> handle(@Nullable ResultSet rs, Connection conn) throws SQLException
            {
                Map<String, List<Aggregate.Result>> results = new HashMap<String, List<Aggregate.Result>>();

                // null == rs is the short-circuit case... SqlFactory didn't find any aggregate columns, so
                // query wasn't executed. Just return an empty map in this case.
                if (null != rs)
                {
                    if (!rs.next())
                        throw new IllegalStateException("Expected a non-empty resultset from aggregate query.");

                    for (Aggregate agg : aggregates)
                    {
                        if (!results.containsKey(agg.getColumnName()))
                            results.put(agg.getColumnName(), new ArrayList<Aggregate.Result>());

                        results.get(agg.getColumnName()).add(agg.getResult(rs));
                    }
                }

                return results;
            }
        });
    }

    public Map<String, List<Aggregate.Result>> getAggregatesAsync(final List<Aggregate> aggregates, HttpServletResponse response) throws IOException
    {
        setLogger(ConnectionWrapper.getConnectionLogger());
        AsyncQueryRequest<Map<String, List<Aggregate.Result>>> asyncRequest = new AsyncQueryRequest<Map<String, List<Aggregate.Result>>>(response);
        setAsyncRequest(asyncRequest);

        try
        {
            return asyncRequest.waitForResult(new Callable<Map<String, List<Aggregate.Result>>>()
            {
                public Map<String, List<Aggregate.Result>> call() throws Exception
                {
                    return getAggregates(aggregates);
                }
            });
        }
        catch (SQLException e)
        {
            throw getExceptionFramework().translate(getScope(), "Message", null, e);
        }
    }

    @Override
    // Return the standard SQL factory (a TableSqlFactory); non-standard methods can create custom factories (or wrap
    // this one) to optimize specific queries (see getRowCount() and getObject()).
    protected TableSqlFactory getSqlFactory(boolean isResultSet)
    {
        // If returning a ResultSet, select one extra row to support isComplete()
        return new TableSqlFactory(_filter, _sort, _columns, isResultSet ? 1 : 0, true);
    }

    protected class TableSqlFactory extends BaseSqlFactory
    {
        private final @Nullable Filter _filter;
        private final @Nullable Sort _sort;
        private final boolean _allowSort;
        private final int _extraRows;

        private Collection<ColumnInfo> _columns;
        private long _scrollOffset = 0;
        private @Nullable Integer _statementMaxRows;

        public TableSqlFactory(@Nullable Filter filter, @Nullable Sort sort, Collection<ColumnInfo> columns, int extraRows, boolean allowSort)
        {
            _filter = filter;
            _sort = allowSort ? sort : null;    // Ensure consistency
            _columns = columns;
            _extraRows = extraRows;
            _allowSort = allowSort;
        }

        @Override      // Note: This method refers to _table, _offset, _rowCount, and _forDisplay from parent; the other fields are from this class.
        public SQLFragment getSql()
        {
            if (_forDisplay)
            {
                Map<String, ColumnInfo> map = Table.getDisplayColumnsList(_columns);

                // QueryService.getSelectSQL() also calls ensureRequiredColumns, so this call is redundant. However, we
                // need to know the actual select columns (e.g., if the caller is building a Results) and getSelectSQL()
                // doesn't return them. TODO: Provide a way to return the selected columns from getSelectSQL()
                Table.ensureRequiredColumns(_table, map, _filter, _sort, null);
                _columns = map.values();
            }

            boolean forceSort = _allowSort && (_offset != Table.NO_OFFSET || _maxRows != Table.ALL_ROWS);
            long selectOffset;

            if (requiresManualScrolling())
            {
                // Offset is set but the dialect's SQL doesn't support it, so implement offset manually:
                // - Select offset + maxRows rows
                // - Set _scrollOffset so getResultSet() skips over the rows we don't want

                _scrollOffset = _offset;
                selectOffset = 0;
            }
            else
            {
                // Standard case is simply to create SQL using maxRows and offset

                _scrollOffset = 0;
                selectOffset = _offset;
            }

            int selectMaxRows = (Table.ALL_ROWS == _maxRows || Table.NO_ROWS == _maxRows) ? _maxRows : (int)_scrollOffset + _maxRows + _extraRows;
            SQLFragment sql = QueryService.get().getSelectSQL(_table, _columns, _filter, _sort, selectMaxRows, selectOffset, forceSort);

            // This is for SAS, which doesn't support a SQL LIMIT syntax, so we must set Statement.maxRows() instead
            _statementMaxRows = _table.getSqlDialect().requiresStatementMaxRows() ? selectMaxRows : null;

            if (null != _namedParameters)
            {
                QueryService.get().bindNamedParameters(sql, _namedParameters);
                QueryService.get().validateNamedParameters(sql);
            }

            return sql;
        }

        protected boolean requiresManualScrolling()
        {
            return _offset != Table.NO_OFFSET && !_table.getSqlDialect().supportsOffset();
        }

        @Override
        public @Nullable Integer getStatementMaxRows()
        {
            return _statementMaxRows;
        }

        @Override
        public void processResultSet(ResultSet rs) throws SQLException
        {
            // Special handling for dialects that don't support offset
            while (_scrollOffset > 0 && rs.next())
                _scrollOffset--;
        }

        public Collection<ColumnInfo> getSelectedColumns()
        {
            return _columns;
        }
    }


    // Generated SQL is being used in a sub-select, so ensure no ORDER BY clause gets generated. ORDER BY is a waste of
    // time (at best) or a SQLException (on SQL Server)
    protected class PreventSortTableSqlFactory extends TableSqlFactory
    {
        public PreventSortTableSqlFactory(Filter filter, Collection<ColumnInfo> columns)
        {
            // Really don't include a sort for this query
            super(filter, null, columns, 0, false);
        }
    }


    // This factory ignores the select columns, instead producing "SELECT 1 FROM ...", and ignores the sort.
    protected class RowCountingSqlFactory extends PreventSortTableSqlFactory
    {
        public RowCountingSqlFactory(TableInfo table, Filter filter)
        {
            super(filter, getRowCountingSelectColumns(table));
        }
    }

    private static Collection<ColumnInfo> getRowCountingSelectColumns(TableInfo table)
    {
        ColumnInfo column = new ExprColumn(table, "One", new SQLFragment("1"), JdbcType.INTEGER);
        return Collections.singleton(column);
    }


    // Make sure the aggregates are selected in the inner query... use QueryService.getColumns() so it works with lookups, etc.
    private static Collection<ColumnInfo> ensureAggregates(TableInfo table, Collection<ColumnInfo> columns, List<Aggregate> aggregates)
    {
        List<FieldKey> aggFieldKeys = new LinkedList<FieldKey>();

        for (Aggregate aggregate : aggregates)
            aggFieldKeys.add(aggregate.getFieldKey());

        return QueryService.get().getColumns(table, aggFieldKeys, columns).values();
    }


    protected class AggregateSqlFactory extends PreventSortTableSqlFactory
    {
        private final List<Aggregate> _aggregates;

        public AggregateSqlFactory(Filter filter, List<Aggregate> aggregates, Collection<ColumnInfo> columns)
        {
            super(filter, ensureAggregates(_table, columns, aggregates));
            _aggregates = aggregates;
        }

        @Override
        public SQLFragment getSql()
        {
            SQLFragment innerSql = super.getSql();

            SQLFragment aggregateSql = new SQLFragment();
            aggregateSql.append("SELECT ");
            boolean first = true;

            // We want a column map that only includes the inner selected columns, so pass null for table
            Map<FieldKey, ColumnInfo> columnMap = Table.createColumnMap(null, getSelectedColumns());

            for (Aggregate agg : _aggregates)
            {
                if (agg.isCountStar() || columnMap.containsKey(agg.getFieldKey()))
                {
                    if (first)
                        first = false;
                    else
                        aggregateSql.append(", ");

                    aggregateSql.append(agg.getSQL(_table.getSqlDialect(), columnMap));
                }
            }

            // if we didn't find any columns, then skip the SQL call completely... we'll return an empty map
            if (first)
                return null;

            return aggregateSql.append(" FROM (").append(innerSql).append(") S");
        }
    }
}
