/*
 * Copyright (c) 2012-2013 LabKey Corporation
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

import org.apache.commons.lang3.mutable.MutableInt;
import org.junit.Assert;
import org.labkey.api.data.Selector.ForEachBatchBlock;
import org.labkey.api.data.Selector.ForEachBlock;
import org.labkey.api.util.ResultSetUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public abstract class AbstractSelectorTestCase<SELECTOR extends Selector> extends Assert
{
    protected <K> void test(SELECTOR selector, Class<K> clazz) throws SQLException
    {
        int count = (int) selector.getRowCount();

        assertTrue("exists() failed", selector.exists() || 0 == count);

        assertEquals(count, selector.getValueMap().size());

        final MutableInt rowCount = new MutableInt();

        // Test map operations

        assertEquals(count, selector.getArrayList(Map.class).size());
        assertEquals(count, selector.getCollection(Map.class).size());
        assertEquals(count, selector.getArray(Map.class).length);
        assertEquals(count, selector.getMapCollection().size());
        assertEquals(count, selector.getMapArray().length);

        rowCount.setValue(0);
        selector.forEachMap(new ForEachBlock<Map<String, Object>>()
        {
            @Override
            public void exec(Map object) throws SQLException
            {
                rowCount.increment();
            }
        });
        assertEquals(count, rowCount.intValue());

        // Test bean operations

        assertEquals(count, selector.getArrayList(clazz).size());
        assertEquals(count, selector.getCollection(clazz).size());
        assertEquals(count, selector.getArray(clazz).length);

        rowCount.setValue(0);
        selector.forEach(new ForEachBlock<K>()
        {
            @Override
            public void exec(K bean) throws SQLException
            {
                rowCount.increment();
            }
        }, clazz);
        assertEquals(count, rowCount.intValue());

        rowCount.setValue(0);
        selector.forEachBatch(new ForEachBatchBlock<K>()
        {
            @Override
            public void exec(List<K> batch) throws SQLException
            {
                assertFalse(batch.isEmpty());
                assertTrue(batch.size() <= 3);
                rowCount.add(batch.size());
            }
        }, clazz, 3);
        assertEquals(count, rowCount.intValue());

        rowCount.setValue(0);
        selector.forEachMapBatch(new ForEachBatchBlock<Map<String, Object>>()
        {
            @Override
            public void exec(List<Map<String, Object>> batch) throws SQLException
            {
                assertFalse(batch.isEmpty());
                assertTrue(batch.size() <= 5);
                rowCount.add(batch.size());
            }
        }, 5);
        assertEquals(count, rowCount.intValue());

        // Test ResultSet operations

        rowCount.setValue(0);
        selector.forEach(new ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet object) throws SQLException
            {
                rowCount.increment();
            }
        });
        assertEquals(count, rowCount.intValue());

        verifyResultSets(selector, count, true);
    }

    protected void verifyResultSets(SELECTOR selector, int expectedRowCount, boolean expectedComplete) throws SQLException
    {
        // Test normal ResultSet
        verifyResultSet(selector.getResultSet(), expectedRowCount, expectedComplete);
    }

    protected void verifyResultSet(TableResultSet rs, int expectedRowCount, boolean expectedComplete) throws SQLException
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
}