/*
 * Copyright (c) 2011 LabKey Corporation
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
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

// TODO: cache, for display, async, etc.
public class TableSelector extends BaseSelector<TableSelector.Context>
{
    private final TableInfo _table;
    private final Collection<ColumnInfo> _columns;
    private final @Nullable Filter _filter;
    private final @Nullable Sort _sort;

    private int _rowCount = Table.ALL_ROWS;
    private long _offset = Table.NO_OFFSET;
    private long _scrollOffset = 0;           // TODO: move to Context

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

        if (null != pk && pk.getClass().isArray())
            pks = (Object[]) pk;
        else
            pks = new Object[]{pk};

        assert pks.length == pkColumns.size() : "Wrong number of primary keys specified";

        SimpleFilter filter = new SimpleFilter(_filter);

        for (int i = 0; i < pkColumns.size(); i++)
            filter.addCondition(pkColumns.get(i), pks[i]);

        if (null != c)
            filter.addCondition("container", c);

        // Ignore the sort -- we're just getting one object
        Context context = new PreventSortContext(filter, _columns);

        return getObject(clazz, context);
    }

    @Override
    public ResultSet getResultSet() throws SQLException
    {
        ResultSet rs = super.getResultSet();

        // Special handling for dialects that don't support offset
        while (_scrollOffset > 0 && rs.next())          // TODO: Add to context
            _scrollOffset--;

        return rs;
    }

    @Override
    public long getRowCount()
    {
        // Ignore specified columns; only need to select PK column(s)
        // Ignore sort -- a waste of time (at best) or a SQLException (on SQL Server)
        Context context = new PreventSortContext(_filter, _table.getPkColumns());
        return super.getRowCount(context);
    }

    @Override
    Context getContext()
    {
        // Return the standard context; exposed methods can create custom contexts to optimize specific requests (see
        // getRowCount() and getObject()).
        return new Context(_filter, _sort, _columns, true);
    }

    protected SQLFragment getSql(Context ctx)
    {
        // NOTE: When ResultSet is supported, we'll need to select one extra row to support isComplete(). Context will
        // need to indicate that a ResultSet is being selected.

        boolean forceSort = ctx.allowSort() && (_offset != Table.NO_OFFSET || _rowCount != Table.ALL_ROWS);

        if (_offset != Table.NO_OFFSET || _table.getSqlDialect().supportsOffset())
        {
            // Standard case is simply to create SQL using the rowCount and offset

            _scrollOffset = 0;
            return QueryService.get().getSelectSQL(_table, ctx.getColumns(), ctx.getFilter(), ctx.getSort(), _rowCount, _offset, forceSort);
        }
        else
        {
            // We've asked for offset but the dialect's SQL doesn't support it, so implement offset manually:
            // - Select offset + rowCount rows
            // - Set _scrollOffset so getResultSet() skips over the rows we don't want

            _scrollOffset = _offset;
            return QueryService.get().getSelectSQL(_table, ctx.getColumns(), ctx.getFilter(), ctx.getSort(), (int)_offset + _rowCount, 0, forceSort);
        }
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testTableSelector()
        {
            TableSelector userSelector = new TableSelector(CoreSchema.getInstance().getTableInfoActiveUsers(), null, null);

            testSelectorMethods(userSelector, User.class);

            int count = (int)userSelector.getRowCount();

            if (count >= 5)
            {
                int rowCount = 3;
                int offset = 2;

                userSelector.setRowCount(Table.ALL_ROWS);
                assertEquals(count, (int)userSelector.getRowCount());
                assertEquals(count, userSelector.getArray(User.class).length);
                assertEquals(count, userSelector.getCollection(User.class).size());

                // First, query all the users into an array and a list; we'll use these to validate that rowCount and
                // offset are working. Set an explicit row count to force the same sorting that will result below.
                userSelector.setRowCount(count);
                User[] sortedArray = userSelector.getArray(User.class);
                List<User> sortedList = new ArrayList<User>(userSelector.getCollection(User.class));

                // Set a row count, verify the lengths and contents against the expected array & list subsets
                userSelector.setRowCount(rowCount);
                assertEquals(rowCount, (int) userSelector.getRowCount());
                User[] rowCountArray = userSelector.getArray(User.class);
                assertEquals(rowCount, rowCountArray.length);
                assertEquals(Arrays.copyOf(sortedArray, rowCount), rowCountArray);
                List<User> rowCountList = new ArrayList<User>(userSelector.getCollection(User.class));
                assertEquals(rowCount, rowCountList.size());
                assertEquals(sortedList.subList(0, rowCount), rowCountList);

                // Set an offset, verify the lengths and contents against the expected array & list subsets
                userSelector.setOffset(offset);
                assertEquals(rowCount, (int)userSelector.getRowCount());
                User[] offsetArray = userSelector.getArray(User.class);
                assertEquals(rowCount, offsetArray.length);
                assertEquals(Arrays.copyOfRange(sortedArray, offset, offset + rowCount), offsetArray);
                List<User> offsetList = new ArrayList<User>(userSelector.getCollection(User.class));
                assertEquals(rowCount, offsetList.size());
                assertEquals(sortedList.subList(offset, offset + rowCount), offsetList);

                // Back to all rows and verify
                userSelector.setRowCount(Table.ALL_ROWS);
                userSelector.setOffset(Table.NO_OFFSET);
                assertEquals(count, (int) userSelector.getRowCount());
                assertEquals(count, userSelector.getArray(User.class).length);
                assertEquals(count, userSelector.getCollection(User.class).size());
            }
        }
        
        private <K> void testSelectorMethods(TableSelector selector, Class<K> clazz)
        {
            K[] array = selector.getArray(clazz);
            Collection<K> collection = selector.getCollection(clazz);

            final AtomicInteger rsCount = new AtomicInteger(0);
            selector.forEach(new ForEachBlock<ResultSet>() {
                @Override
                public void exec(ResultSet rs) throws SQLException
                {
                    rsCount.incrementAndGet();
                }
            });

            final AtomicInteger mapCount = new AtomicInteger(0);
            selector.forEachMap(new ForEachBlock<Map<String, Object>>()
            {
                @Override
                public void exec(Map<String, Object> map) throws SQLException
                {
                    mapCount.incrementAndGet();
                }
            });

            final AtomicInteger objCount = new AtomicInteger(0);
            selector.forEach(new ForEachBlock<K>() {
                @Override
                public void exec(K user) throws SQLException
                {
                    objCount.incrementAndGet();
                }
            }, clazz);

            assertTrue
            (
                array.length == collection.size() &&
                array.length == rsCount.intValue() &&
                array.length == mapCount.intValue() &&
                array.length == objCount.intValue() &&
                array.length == selector.getRowCount()
            );
        }
    }

    protected static class Context
    {
        private final @Nullable Filter _filter;
        private final @Nullable Sort _sort;
        private final Collection<ColumnInfo> _columns;
        private final boolean _allowSort;

        public Context(@Nullable Filter filter, @Nullable Sort sort, Collection<ColumnInfo> columns, boolean allowSort)
        {
            _filter = filter;
            _sort = allowSort ? sort : null;    // Ensure consistency
            _columns = columns;
            _allowSort = allowSort;
        }

        public @Nullable Filter getFilter()
        {
            return _filter;
        }

        public @Nullable Sort getSort()
        {
            return _sort;
        }

        public Collection<ColumnInfo> getColumns()
        {
            return _columns;
        }

        public boolean allowSort()
        {
            return _allowSort;
        }
    }

    protected static class PreventSortContext extends Context
    {
        public PreventSortContext(Filter filter, Collection<ColumnInfo> columns)
        {
            // Really don't include a sort for this query
            super(filter, null, columns, false);
        }
    }
}
