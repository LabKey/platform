package org.labkey.api.cache;

import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TestSchema;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;

import java.util.Collections;
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
        DbCache.trackRemove(testTable);

        Map<String, Object> mm = new HashMap<>();                  // Modifiable map
        Map<String, Object> umm = Collections.unmodifiableMap(mm); // Unmodifiable map
        mm.put("DatetimeNotNull", new Date());
        mm.put("BitNotNull", Boolean.TRUE);
        mm.put("Text", "Added by Caching Test Suite");
        mm.put("IntNotNull", 0);
        mm.put("Container", JunitUtil.getTestContainer());
        mm = Table.insert(ctx.getUser(), testTable, mm);
        DbCache.trackRemove(testTable);
        Integer rowId1 = ((Integer) mm.get("RowId"));

        String key = "RowId" + rowId1;
        DbCache.put(testTable, key, umm);
        Map m2 = (Map) DbCache.get(testTable, key);
        assertEquals(umm, m2);

        //Does cache get cleared on delete
        Table.delete(testTable, rowId1);
        DbCache.trackRemove(testTable);
        m2 = (Map) DbCache.get(testTable, key);
        assertNull(m2);

        //Does cache get cleared on insert
        mm.remove("RowId");
        mm = Table.insert(ctx.getUser(), testTable, mm);
        DbCache.trackRemove(testTable);
        int rowId2 = ((Integer) mm.get("RowId"));
        key = "RowId" + rowId2;
        DbCache.put(testTable, key, umm);
        mm.remove("RowId");
        mm = Table.insert(ctx.getUser(), testTable, mm);
        DbCache.trackRemove(testTable);
        int rowId3 = ((Integer) mm.get("RowId"));
        m2 = (Map) DbCache.get(testTable, key);
        assertNull(m2);

        //Make sure things are not inserted in transaction
        mm.remove("RowId");
        String key2;
        try (DbScope.Transaction ignored = testSchema.getScope().beginTransaction())
        {
            mm = Table.insert(ctx.getUser(), testTable, mm);
            DbCache.trackRemove(testTable);
            int rowId4 = ((Integer) mm.get("RowId"));
            key2 = "RowId" + rowId4;
            DbCache.put(testTable, key2, umm);
        }
        m2 = (Map) DbCache.get(testTable, key2);
        assertNull(m2);

        // Clean up
        Table.delete(testTable, rowId2);
        DbCache.trackRemove(testTable);
        Table.delete(testTable, rowId3);
        DbCache.trackRemove(testTable);

        DbCache.logUnmatched();
    }
}
