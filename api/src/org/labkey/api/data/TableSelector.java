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

// TODO: Filter, Sort, Offset, Limit, etc.
public class TableSelector extends BaseSelector
{
    private final TableInfo _table;
    private final Collection<ColumnInfo> _columns;

    // Select specified columns from a table
    public TableSelector(TableInfo table, Collection<ColumnInfo> columns)
    {
        super(table.getSchema().getScope());
        _table = table;
        _columns = columns;
    }

    // Select all columns from a table
    public TableSelector(TableInfo table)
    {
        this(table, Table.ALL_COLUMNS);
    }

    // Select specified columns from a table
    public TableSelector(TableInfo table, Set<String> columnNames)
    {
        this(table, Table.columnInfosList(table, columnNames));
    }

    // Select a single column -- not sure this is useful
    public TableSelector(ColumnInfo column)
    {
        this(column.getParentTable(), PageFlowUtil.set(column));
    }

    @Override
    SQLFragment getSql()
    {
        return QueryService.get().getSelectSQL(_table, _columns, null, null, Table.ALL_ROWS, Table.NO_OFFSET, false);
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testTableSelector()
        {
            TableSelector userSelector = new TableSelector(CoreSchema.getInstance().getTableInfoActiveUsers());

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
