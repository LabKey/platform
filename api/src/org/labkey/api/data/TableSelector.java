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

// TODO: cache, for display, SelectObject via PK, async, etc.
public class TableSelector extends BaseSelector
{
    private final TableInfo _table;
    private final Collection<ColumnInfo> _columns;
    private final Filter _filter;
    private final Sort _sort;

    private int _rowCount = Table.ALL_ROWS;
    private long _offset = Table.NO_OFFSET;
    private long _scrollOffset = 0;

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

    public Collection<ColumnInfo> getColumns()
    {
        return _columns;
    }

    public TableSelector setRowCount(int rowCount)
    {
        _rowCount = rowCount;
        return this;
    }

    public TableSelector setOffset(long offset)
    {
        _offset = offset;
        return this;
    }

    @Override
    public ResultSet getResultSet() throws SQLException
    {
        ResultSet rs = super.getResultSet();

        // Special handling for dialects that don't support offset
        while (_scrollOffset > 0 && rs.next())
            _scrollOffset--;

        return rs;
    }

    @Override
    SQLFragment getSql()
    {
        return getSql(_columns);
    }

    @Override
    protected SQLFragment getSqlForRowCount()
    {
        // Ignore specified columns; only need to select PK column(s)
        return getSql(_table.getPkColumns());
    }

    private SQLFragment getSql(Collection<ColumnInfo> columns)
    {
        // NOTE: When ResultSet is supported, we'll need to select one extra row to support isComplete(). Will need
        // boolean flag that indicates ResultSet is being selected.

        boolean forceSort = (_offset != Table.NO_OFFSET || _rowCount != Table.ALL_ROWS);

        if (_offset != Table.NO_OFFSET || _table.getSqlDialect().supportsOffset())
        {
            // Standard case is to simply create SQL using the rowCount and offset

            _scrollOffset = 0;
            return QueryService.get().getSelectSQL(_table, columns, _filter, _sort, _rowCount, _offset, forceSort);
        }
        else
        {
            // We've asked for offset but the dialect's SQL doesn't support it, so implement offset manually:
            // - Select rowCount + offset rows
            // - Set _scrollOffset so getResultSet() skips over the rows we don't want

            _scrollOffset = _offset;
            return QueryService.get().getSelectSQL(_table, columns, _filter, _sort, _rowCount + (int)_offset, 0, forceSort);
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
                assertEquals(count, (int)userSelector.getRowCount());
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
}
