/*
 * Copyright (c) 2011-2017 LabKey Corporation
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

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TableSelector extends SqlExecutingSelector<TableSelector.TableSqlFactory, TableSelector>
{
    public static final Set<String> ALL_COLUMNS = Collections.unmodifiableSet(Collections.emptySet());

    private static final Logger LOG = Logger.getLogger(TableSelector.class);

    private final TableInfo _table;
    private final Collection<ColumnInfo> _columns;
    private final @Nullable Filter _filter;
    private final @Nullable Sort _sort;
    private final boolean _stableColumnOrdering;

    private boolean _forDisplay = false;

    // Master constructor
    private TableSelector(@NotNull TableInfo table, Collection<ColumnInfo> columns, @Nullable Filter filter, @Nullable Sort sort, boolean stableColumnOrdering)
    {
        super(table.getSchema().getScope());
        _table = table;
        _columns = columns;
        _filter = filter;
        _sort = sort;
        _stableColumnOrdering = stableColumnOrdering;  // We track this to warn at method call time, e.g., if getValueMap() is called when column order is indeterminate
    }

    /*
        Select specified columns from a table. Note: many methods require a column collection that iterates in a predictable
        order; getValueMap(), fillValueMap(), getResultSet(), getResults(), and forEach(ForEachBlock<ResultSet>) will all
        throw IllegalStateException if they are called after (for example) a multi-element HashSet<ColumnInfo> has been
        passed to this constructor. Asking for a primitive typed array or collection will also throw, since we implicitly
        rely on column order (we return the values from the first one).
     */
    public TableSelector(@NotNull TableInfo table, Collection<ColumnInfo> columns, @Nullable Filter filter, @Nullable Sort sort)
    {
        this(table, columns, filter, sort, isStableOrdered(columns));
    }

    // Select all columns from a table, with no filter or sort
    public TableSelector(@NotNull TableInfo table)
    {
        this(table, ALL_COLUMNS, null, null);
    }

    /*
        Select specified columns from a table, no filter or sort. Note: many methods require the columnNames set to
        iterate in a predictable order; see comment above for more details.
    */
    public TableSelector(@NotNull TableInfo table, Set<String> columnNames)
    {
        this(table, columnNames, null, null);
    }

    // Select all columns from a table
    public TableSelector(@NotNull TableInfo table, @Nullable Filter filter, @Nullable Sort sort)
    {
        this(table, ALL_COLUMNS, filter, sort);
    }

    /*
        Select specified columns from a table. Note: many methods require the columnNames set to iterate in a predictable
        order; see comment above for more details.
    */
    public TableSelector(@NotNull TableInfo table, Set<String> columnNames, @Nullable Filter filter, @Nullable Sort sort)
    {
        this(table, columnInfosList(table, columnNames), filter, sort, isStableOrdered(columnNames));
    }

    // Select a single column
    public TableSelector(@NotNull ColumnInfo column, @Nullable Filter filter, @Nullable Sort sort)
    {
        this(column.getParentTable(), Collections.singleton(column), filter, sort, true);  // Single column is stable ordered
    }

    // Select a single column from all rows
    public TableSelector(ColumnInfo column)
    {
        this(column, null, null);
    }

    private static Collection<ColumnInfo> columnInfosList(@NotNull TableInfo table, Collection<String> select)
    {
        Collection<ColumnInfo> selectColumns;

        if (select == ALL_COLUMNS)
        {
            selectColumns = table.getColumns();
        }
        else
        {
            selectColumns = new LinkedHashSet<>();

            for (String name : select)
            {
                ColumnInfo column = table.getColumn(name);

                if (null != column)
                    selectColumns.add(column);
                else
                    LOG.warn("Requested column does not exist in table '" + table.getSelectName() + "': " + name);
            }
        }

        return selectColumns;
    }

    private static Map<String, ColumnInfo> getDisplayColumnsList(Collection<ColumnInfo> arrColumns)
    {
        Map<String, ColumnInfo> columns = new LinkedHashMap<>();
        ColumnInfo existing;

        for (ColumnInfo column : arrColumns)
        {
            existing = columns.get(column.getAlias());
            assert null == existing || existing.getName().equals(column.getName()) : existing.getName() + " != " + column.getName();
            columns.put(column.getAlias(), column);
            ColumnInfo displayColumn = column.getDisplayField();
            if (displayColumn != null)
            {
                existing = columns.get(displayColumn.getAlias());
                assert null == existing || existing.getName().equals(displayColumn.getName());
                columns.put(displayColumn.getAlias(), displayColumn);
            }
        }

        return columns;
    }

    // Used only by the junit tests
    int getColumnCount()
    {
        return _columns.size();
    }

    @Override
    protected TableSelector getThis()
    {
        return this;
    }

    /*
        Try to determine if the collection will iterate in a predictable order. Currently, all of these are assumed
        to be stable-ordered collections:

        - Collections of size 0 or 1
        - Non HashSets
        - LinkedHashSet (which extends HashSet, so we need a separate check)

    */
    private static boolean isStableOrdered(Collection<?> collection)
    {
        return (collection.size() < 2 || !(collection instanceof HashSet) || collection instanceof LinkedHashSet);
    }

    @NotNull
    @Override
    protected <E> ArrayList<E> createPrimitiveArrayList(ResultSet rs, @NotNull Table.Getter getter) throws SQLException
    {
        // Could be get getArray(), getArrayList(), or getCollection()
        ensureStableColumnOrder("This TableSelector method");
        return super.createPrimitiveArrayList(rs, getter);
    }

    @NotNull
    @Override
    public <K, V> Map<K, V> getValueMap()
    {
        ensureStableColumnOrder("getValueMap()");
        return super.getValueMap();
    }

    @NotNull
    @Override
    public <K, V> MultiValuedMap<K, V> getMultiValuedMap()
    {
        ensureStableColumnOrder("getMultiValuedMap()");
        return super.getMultiValuedMap();
    }

    @NotNull
    @Override
    public <K, V> Map<K, V> fillValueMap(@NotNull Map<K, V> fillMap)
    {
        ensureStableColumnOrder("fillValueMap()");
        return super.fillValueMap(fillMap);
    }

    @Override
    public TableResultSet getResultSet(boolean cache, boolean scrollable)
    {
        ensureStableColumnOrder("getResultSet()");
        return super.getResultSet(cache, scrollable);
    }

    @Override
    protected void forEach(ForEachBlock<ResultSet> block, ResultSetFactory factory)
    {
        ensureStableColumnOrder("forEach(ForEachBlock<ResultSet> block)");
        super.forEach(block, factory);
    }

    public void forEachResults(ForEachBlock<Results> block)
    {
        ensureStableColumnOrder("forEachResults(ForEachBlock<Results> block)");

        // Same pattern as getStandardResultSetFactory(), but gives us a reference to the sql factory which we need for the column list
        TableSqlFactory sqlFactory = getSqlFactory(false);
        handleResultSet(new ExecutingResultSetFactory(sqlFactory), (rs, conn) -> {
            Results results = new ResultsImpl(rs, sqlFactory.getSelectedColumns());
            try
            {
                while (results.next())
                    block.exec(results);
            }
            catch (StopIteratingException sie)
            {
            }

            return null;
        });
    }

    private void ensureStableColumnOrder(String methodDescription)
    {
        if (!_stableColumnOrdering)
            throw new IllegalStateException(methodDescription + " must not be called with an unstable ordered column set");
    }

    /**
     * If no transaction is active and the SQL statement is a SELECT, this method assumes it is safe to tweak
     * connection parameters (such as disabling auto-commit, and never committing) to optimize memory and other
     * resource usage.
     *
     * If you are, for example, invoking a stored procedure that will have side effects via a SELECT statement,
     * you must explicitly start your own transaction and commit it.
     */
    public Results getResults()
    {
        return getResults(true);
    }

    public Results getResults(boolean cache)
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

    public Results getResults(boolean cache, boolean scrollable)
    {
        ensureStableColumnOrder("getResults()");
        TableSqlFactory tableSqlFactory = getSqlFactory(true);
        ExecutingResultSetFactory factory = new ExecutingResultSetFactory(tableSqlFactory, cache, scrollable);
        ResultSet rs = getResultSet(factory, cache);

        return new ResultsImpl(rs, tableSqlFactory.getSelectedColumns());
    }

    public Results getResultsAsync(final boolean cache, final boolean scrollable, HttpServletResponse response) throws IOException, SQLException
    {
        setLogger(ConnectionWrapper.getConnectionLogger());
        AsyncQueryRequest<Results> asyncRequest = new AsyncQueryRequest<>(response);
        setAsyncRequest(asyncRequest);

        return asyncRequest.waitForResult(() -> getResults(cache, scrollable));
    }

    public TableSelector setForDisplay(boolean forDisplay)
    {
        _forDisplay = forDisplay;
        return this;
    }

    /** pk can be single value, an array of values, or a filter (??) */
    public <K> K getObject(Object pk, Class<K> clazz)
    {
        return getObject(null, pk, clazz);
    }

    /** pk can be single value, an array of values, or a filter (??) */
    public Map<String, Object> getMap(Object pk)
    {
        //noinspection unchecked
        return getObject(pk, Map.class);
    }

    // pk can be single value, an array of values, or a filter (??)
    public <K> K getObject(@Nullable Container c, Object pk, Class<K> clazz)
    {
        // Don't allow null pk, see #20057
        if (null == pk)
            return null; // TODO: throw new IllegalStateException("PK on getObject() must not be null");

        List<ColumnInfo> pkColumns = _table.getPkColumns();
        Object[] pks;
        SimpleFilter filter = new SimpleFilter(_filter);

        if (pk instanceof SimpleFilter)
        {
            filter.addAllClauses((SimpleFilter)pk);
        }
        else
        {
            if (pk.getClass().isArray())
                pks = (Object[]) pk;
            else
                pks = new Object[]{pk};

            assert pks.length == pkColumns.size() : "Wrong number of primary keys specified";

            for (int i = 0; i < pkColumns.size(); i++)
                filter.addCondition(pkColumns.get(i), pks[i]);
        }

        if (null != c && null != _table.getColumn("container"))
            filter.addCondition(FieldKey.fromParts("container"), c);

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
        final AggregateSqlFactory sqlFactory = new AggregateSqlFactory(_filter, aggregates, _columns);
        ResultSetFactory resultSetFactory = new ExecutingResultSetFactory(sqlFactory);

        return handleResultSet(resultSetFactory, (rs, conn) -> {
            Map<String, List<Aggregate.Result>> results = new HashMap<>();

            // null == rs is the short-circuit case... SqlFactory didn't find any aggregate columns, so
            // query wasn't executed. Just return an empty map in this case.
            if (null != rs)
            {
                // Issue 17536: Issue a warning instead of blowing up if there is no result row containing the aggregate values.
                if (!rs.next())
                {
                    Logger.getLogger(TableSelector.class).warn("Expected a non-empty resultset from aggregate query.");
                }
                else
                {
                    for (Aggregate agg : aggregates)
                    {
                        if (!results.containsKey(agg.getFieldKey().toString()))
                            results.put(agg.getFieldKey().toString(), new ArrayList<>());

                        results.get(agg.getFieldKey().toString()).add(agg.getResult(rs, sqlFactory._columnMap));
                    }
                }
            }

            return results;
        });
    }

    public Map<String, List<Aggregate.Result>> getAggregatesAsync(final List<Aggregate> aggregates, HttpServletResponse response) throws IOException
    {
        setLogger(ConnectionWrapper.getConnectionLogger());
        AsyncQueryRequest<Map<String, List<Aggregate.Result>>> asyncRequest = new AsyncQueryRequest<>(response);
        setAsyncRequest(asyncRequest);

        try
        {
            return asyncRequest.waitForResult(() -> getAggregates(aggregates));
        }
        catch (SQLException e)
        {
            throw getExceptionFramework().translate(getScope(), "TableSelector.getAggregatesAsync()", e);
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
                Map<String, ColumnInfo> map = getDisplayColumnsList(_columns);

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
            SQLFragment sql = QueryService.get().getSelectSQL(_table, _columns, _filter, _sort, selectMaxRows, selectOffset, forceSort, getQueryLogging());

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
        List<FieldKey> aggFieldKeys = new LinkedList<>();

        for (Aggregate aggregate : aggregates)
            aggFieldKeys.add(aggregate.getFieldKey());

        return QueryService.get().getColumns(table, aggFieldKeys, columns).values();
    }


    protected class AggregateSqlFactory extends PreventSortTableSqlFactory
    {
        private final List<Aggregate> _aggregates;
        private final Map<FieldKey, ColumnInfo> _columnMap;

        public AggregateSqlFactory(Filter filter, List<Aggregate> aggregates, Collection<ColumnInfo> columns)
        {
            super(filter, ensureAggregates(_table, columns, aggregates));
            _aggregates = aggregates;

            // We want a column map that only includes the inner selected columns, so pass null for table
            _columnMap = Table.createColumnMap(null, getSelectedColumns());
        }

        @Override
        public SQLFragment getSql()
        {
            SQLFragment innerSql = super.getSql();

            SQLFragment aggregateSql = new SQLFragment();
            aggregateSql.append("SELECT ");
            int validAggregates = 0;

            for (Aggregate agg : _aggregates)
            {
                if (agg.isCountStar() || _columnMap.containsKey(agg.getFieldKey()))
                {
                    String sql = agg.getSQL(_table.getSqlDialect(), _columnMap, innerSql);
                    if (sql != null)
                    {
                        if (validAggregates > 0)
                            aggregateSql.append(",\n");

                        aggregateSql.append(sql);
                        if (innerSql != null)
                        {
                            // If the aggregate uses subqueries, they need the same set of parameters as the outer sql.
                            for (int i = 0; i < agg.getType().subQueryCount(_table.getSqlDialect()); i++)
                            {
                                aggregateSql.addAll(innerSql.getParams());
                            }
                        }
                        validAggregates++;
                    }
                }
            }

            // if we didn't find any columns, then skip the SQL call completely... we'll return an empty map
            if (validAggregates == 0)
                return null;

            return aggregateSql.append(" FROM (").append(innerSql).append(") S");
        }
    }
}
