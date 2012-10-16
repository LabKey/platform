/*
 * Copyright (c) 2012 LabKey Corporation
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
import org.labkey.api.module.ModuleContext;
import org.labkey.api.security.User;
import org.labkey.api.util.ResultSetUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
* User: adam
* Date: 1/19/12
* Time: 5:54 PM
*/
public class TableSelectorTestCase extends Assert
{
    @Test
    public void testTableSelector() throws SQLException
    {
        testTableSelector(CoreSchema.getInstance().getTableInfoActiveUsers(), User.class);
        testTableSelector(CoreSchema.getInstance().getTableInfoModules(), ModuleContext.class);
    }

    public <K> void testTableSelector(TableInfo table, Class<K> clazz) throws SQLException
    {
        TableSelector selector = new TableSelector(table, null, null);

        testSelectorMethods(selector, clazz);
        testOffsetAndLimit(selector, clazz);
    }

    private <K> void testOffsetAndLimit(TableSelector selector, Class<K> clazz) throws SQLException
    {
        int count = (int) selector.getRowCount();

        if (count > 5)
        {
            int rowCount = 3;
            int offset = 2;

            selector.setMaxRows(Table.ALL_ROWS);
            assertEquals(count, (int) selector.getRowCount());
            assertEquals(count, selector.getArray(clazz).length);
            assertEquals(count, selector.getCollection(clazz).size());

            // First, query all the rows into an array and a list; we'll use these to validate that rowCount and
            // offset are working. Set an explicit row count to force the same sorting that will result below.
            selector.setMaxRows(count);
            K[] sortedArray = selector.getArray(clazz);
            List<K> sortedList = new ArrayList<K>(selector.getCollection(clazz));
            verifyResultSets(selector, count, true);

            // Set a row count, verify the lengths and contents against the expected array & list subsets
            selector.setMaxRows(rowCount);
            assertEquals(rowCount, (int) selector.getRowCount());
            K[] rowCountArray = selector.getArray(clazz);
            assertEquals(rowCount, rowCountArray.length);
            assertEquals(Arrays.copyOf(sortedArray, rowCount), rowCountArray);
            List<K> rowCountList = new ArrayList<K>(selector.getCollection(clazz));
            assertEquals(rowCount, rowCountList.size());
            assertEquals(sortedList.subList(0, rowCount), rowCountList);
            verifyResultSets(selector, rowCount, false);

            // Set an offset, verify the lengths and contents against the expected array & list subsets
            selector.setOffset(offset);
            assertEquals(rowCount, (int) selector.getRowCount());
            K[] offsetArray = selector.getArray(clazz);
            assertEquals(rowCount, offsetArray.length);
            assertEquals(Arrays.copyOfRange(sortedArray, offset, offset + rowCount), offsetArray);
            List<K> offsetList = new ArrayList<K>(selector.getCollection(clazz));
            assertEquals(rowCount, offsetList.size());
            assertEquals(sortedList.subList(offset, offset + rowCount), offsetList);
            verifyResultSets(selector, rowCount, false);

            // Back to all rows and verify
            selector.setMaxRows(Table.ALL_ROWS);
            selector.setOffset(Table.NO_OFFSET);
            assertEquals(count, (int) selector.getRowCount());
            assertEquals(count, selector.getArray(clazz).length);
            assertEquals(count, selector.getCollection(clazz).size());
            verifyResultSets(selector, count, true);
        }
    }

    private void verifyResultSets(TableSelector selector, int expectedRowCount, boolean expectedComplete) throws SQLException
    {
        // Test normal cached ResultSet, uncached ResultSet, and Results
        verifyResultSet(selector.getResultSet(), expectedRowCount, expectedComplete);
        verifyResultSet(selector.getResultSet(false, false), expectedRowCount, expectedComplete);
        verifyResultSet(selector.getResults(), expectedRowCount, expectedComplete);
    }

    private void verifyResultSet(Table.TableResultSet rs, int expectedRowCount, boolean expectedComplete) throws SQLException
    {
        try
        {
            int rsCount = 0;
            while(rs.next())
                rsCount++;
            assertEquals(expectedRowCount, rsCount);
            assertEquals(expectedComplete, rs.isComplete());
        }
        finally
        {
            ResultSetUtil.close(rs);
        }
    }

    private <K> void testSelectorMethods(TableSelector selector, Class<K> clazz) throws SQLException
    {
        assertTrue(selector.exists());

        K[] array = selector.getArray(clazz);
        Collection<K> collection = selector.getCollection(clazz);

        final AtomicInteger forEachcount = new AtomicInteger(0);
        selector.forEach(new Selector.ForEachBlock<ResultSet>() {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                forEachcount.incrementAndGet();
            }
        });

        final AtomicInteger mapCount = new AtomicInteger(0);
        selector.forEachMap(new Selector.ForEachBlock<Map<String, Object>>()
        {
            @Override
            public void exec(Map<String, Object> map) throws SQLException
            {
                mapCount.incrementAndGet();
            }
        });

        final AtomicInteger objCount = new AtomicInteger(0);
        selector.forEach(new Selector.ForEachBlock<K>() {
            @Override
            public void exec(K object) throws SQLException
            {
                objCount.incrementAndGet();
            }
        }, clazz);

        verifyResultSets(selector, array.length, true);

        assertTrue
        (
            array.length == collection.size() &&
            array.length == forEachcount.intValue() &&
            array.length == mapCount.intValue() &&
            array.length == objCount.intValue() &&
            array.length == selector.getRowCount()
        );
    }
}
