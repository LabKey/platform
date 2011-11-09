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
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

// TODO: offset, limit, cache, SelectForDisplay, SelectObject via PK, async, etc.
public class TableSelector extends BaseSelector
{
    private final TableInfo _table;
    private final Collection<ColumnInfo> _columns;
    private final Filter _filter;
    private final Sort _sort;

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

    @Override
    SQLFragment getSql()
    {
        // TODO: Handle offset and limit
        return QueryService.get().getSelectSQL(_table, _columns, _filter, _sort, Table.ALL_ROWS, Table.NO_OFFSET, false);
    }

    @Override
    protected SQLFragment getSqlForRowcount()
    {
        // TODO: Handle offset and limit
        // Ignore specified columns; only need to select PK column(s)
        return QueryService.get().getSelectSQL(_table, _table.getPkColumns(), _filter, _sort, Table.ALL_ROWS, Table.NO_OFFSET, false);
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testTableSelector()
        {
            TableSelector userSelector = new TableSelector(CoreSchema.getInstance().getTableInfoActiveUsers(), null, null);

            testSelectorMethods(userSelector, User.class);
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
                array.length == objCount.intValue()
            );
        }
    }
}
