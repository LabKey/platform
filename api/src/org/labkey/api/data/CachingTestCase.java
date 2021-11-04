package org.labkey.api.data;

import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.cache.DbCache;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class CachingTestCase extends Assert
{
    @Test
    public void testCaching()
    {
        TestSchema test = TestSchema.getInstance();
        DbSchema testSchema = test.getSchema();
        TableInfo testTable = test.getTableInfoTestTable();
        TestContext ctx = TestContext.get();

        assertNotNull(testTable);
        DbCache.clear(testTable);

        Map<String, Object> m = new HashMap<>();
        m.put("DatetimeNotNull", new Date());
        m.put("BitNotNull", Boolean.TRUE);
        m.put("Text", "Added by Caching Test Suite");
        m.put("IntNotNull", 0);
        m.put("Container", JunitUtil.getTestContainer());
        m = Table.insert(ctx.getUser(), testTable, m);
        Integer rowId1 = ((Integer) m.get("RowId"));

        String key = "RowId" + rowId1;
        DbCache.put(testTable, key, m);
        Map m2 = (Map) DbCache.get(testTable, key);
        assertEquals(m, m2);

        //Does cache get cleared on delete
        Table.delete(testTable, rowId1);
        m2 = (Map) DbCache.get(testTable, key);
        assertNull(m2);

        //Does cache get cleared on insert
        m.remove("RowId");
        m = Table.insert(ctx.getUser(), testTable, m);
        int rowId2 = ((Integer) m.get("RowId"));
        key = "RowId" + rowId2;
        DbCache.put(testTable, key, m);
        m.remove("RowId");
        m = Table.insert(ctx.getUser(), testTable, m);
        int rowId3 = ((Integer) m.get("RowId"));
        m2 = (Map) DbCache.get(testTable, key);
        assertNull(m2);

        //Make sure things are not inserted in transaction
        m.remove("RowId");
        String key2;
        try (DbScope.Transaction transaction = testSchema.getScope().beginTransaction())
        {
            m = Table.insert(ctx.getUser(), testTable, m);
            int rowId4 = ((Integer) m.get("RowId"));
            key2 = "RowId" + rowId4;
            DbCache.put(testTable, key2, m);
        }
        m2 = (Map) DbCache.get(testTable, key2);
        assertNull(m2);

        // Clean up
        Table.delete(testTable, rowId2);
        Table.delete(testTable, rowId3);
    }
}
