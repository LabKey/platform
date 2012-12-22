package org.labkey.api.data;

import org.apache.commons.lang3.mutable.MutableInt;
import org.junit.Assert;
import org.junit.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

/**
 * User: adam
 * Date: 12/18/12
 * Time: 8:04 PM
 */
public class SqlSelectorTestCase extends Assert
{
    @Test
    public void testSqlSelector() throws SQLException
    {
        test(new SqlSelector(CoreSchema.getInstance().getSchema(), "SELECT RowId, Body FROM comm.Announcements"), TestClass.class);
    }

    private <K> void test(SqlSelector selector, Class<K> clazz) throws SQLException
    {
        int count = (int)selector.getRowCount();

        assertTrue(selector.exists() || 0 == count);

        assertEquals(count, selector.getValueMap().size());
        assertEquals(count, selector.getArrayList(Map.class).size());
        assertEquals(count, selector.getCollection(Map.class).size());
        assertEquals(count, selector.getArray(Map.class).length);

        final MutableInt rowCount = new MutableInt();

        selector.forEach(new Selector.ForEachBlock<ResultSet>(){
            @Override
            public void exec(ResultSet object) throws SQLException
            {
                rowCount.increment();
            }
        });

        assertEquals(count, rowCount.intValue());
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

        TableSelectorTestCase.verifyResultSets(selector, count, true);
    }

    public static class TestClass
    {
        private int _rowId;
        private String _body;

        public int getRowId()
        {
            return _rowId;
        }

        public void setRowId(int rowId)
        {
            _rowId = rowId;
        }

        public String getBody()
        {
            return _body;
        }

        public void setBody(String body)
        {
            _body = body;
        }
    }
}
