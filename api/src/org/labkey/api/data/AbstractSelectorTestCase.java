package org.labkey.api.data;

import org.apache.commons.lang3.mutable.MutableInt;
import org.junit.Assert;
import org.labkey.api.util.ResultSetUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
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

        rowCount.setValue(0);
        selector.forEachMap(new Selector.ForEachBlock<Map<String, Object>>()
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
        selector.forEach(new Selector.ForEachBlock<K>()
        {
            @Override
            public void exec(K bean) throws SQLException
            {
                rowCount.increment();
            }
        }, clazz);
        assertEquals(count, rowCount.intValue());

        // Test ResultSet operations

        rowCount.setValue(0);
        selector.forEach(new Selector.ForEachBlock<ResultSet>()
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

    protected void verifyResultSet(Table.TableResultSet rs, int expectedRowCount, boolean expectedComplete) throws SQLException
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