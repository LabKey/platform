package org.labkey.api.data;

import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.security.User;

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
    public void testTableSelector()
    {
        testTableSelector(CoreSchema.getInstance().getTableInfoActiveUsers(), User.class);
        testTableSelector(CoreSchema.getInstance().getTableInfoModules(), ModuleContext.class);
    }

    public <K> void testTableSelector(TableInfo table, Class<K> clazz)
    {
        TableSelector selector = new TableSelector(table, null, null);

        testSelectorMethods(selector, clazz);
        testOffsetAndLimit(selector, clazz);
    }

    private <K> void testOffsetAndLimit(TableSelector selector, Class<K> clazz)
    {
        int count = (int) selector.getRowCount();

        if (count >= 5)
        {
            int rowCount = 3;
            int offset = 2;

            selector.setRowCount(Table.ALL_ROWS);
            assertEquals(count, (int) selector.getRowCount());
            assertEquals(count, selector.getArray(clazz).length);
            assertEquals(count, selector.getCollection(clazz).size());

            // First, query all the rows into an array and a list; we'll use these to validate that rowCount and
            // offset are working. Set an explicit row count to force the same sorting that will result below.
            selector.setRowCount(count);
            K[] sortedArray = selector.getArray(clazz);
            List<K> sortedList = new ArrayList<K>(selector.getCollection(clazz));

            // Set a row count, verify the lengths and contents against the expected array & list subsets
            selector.setRowCount(rowCount);
            assertEquals(rowCount, (int) selector.getRowCount());
            K[] rowCountArray = selector.getArray(clazz);
            assertEquals(rowCount, rowCountArray.length);
            assertEquals(Arrays.copyOf(sortedArray, rowCount), rowCountArray);
            List<K> rowCountList = new ArrayList<K>(selector.getCollection(clazz));
            assertEquals(rowCount, rowCountList.size());
            assertEquals(sortedList.subList(0, rowCount), rowCountList);

            // Set an offset, verify the lengths and contents against the expected array & list subsets
            selector.setOffset(offset);
            assertEquals(rowCount, (int) selector.getRowCount());
            K[] offsetArray = selector.getArray(clazz);
            assertEquals(rowCount, offsetArray.length);
            assertEquals(Arrays.copyOfRange(sortedArray, offset, offset + rowCount), offsetArray);
            List<K> offsetList = new ArrayList<K>(selector.getCollection(clazz));
            assertEquals(rowCount, offsetList.size());
            assertEquals(sortedList.subList(offset, offset + rowCount), offsetList);

            // Back to all rows and verify
            selector.setRowCount(Table.ALL_ROWS);
            selector.setOffset(Table.NO_OFFSET);
            assertEquals(count, (int) selector.getRowCount());
            assertEquals(count, selector.getArray(clazz).length);
            assertEquals(count, selector.getCollection(clazz).size());
        }
    }

    private <K> void testSelectorMethods(TableSelector selector, Class<K> clazz)
    {
        K[] array = selector.getArray(clazz);
        Collection<K> collection = selector.getCollection(clazz);

        final AtomicInteger rsCount = new AtomicInteger(0);
        selector.forEach(new Selector.ForEachBlock<ResultSet>() {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                rsCount.incrementAndGet();
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
