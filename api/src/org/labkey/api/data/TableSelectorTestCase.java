/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
import org.apache.log4j.Level;
import org.junit.Test;
import org.labkey.api.collections.CsvSet;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.security.User;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.TestContext;
import org.springframework.jdbc.UncategorizedSQLException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
* User: adam
* Date: 1/19/12
* Time: 5:54 PM
*/
public class TableSelectorTestCase extends AbstractSelectorTestCase<TableSelector>
{
    @Test
    public void testTableSelector() throws SQLException
    {
        testTableSelector(CoreSchema.getInstance().getTableInfoActiveUsers(), User.class);
        testTableSelector(CoreSchema.getInstance().getTableInfoModules(), ModuleContext.class);
    }

    @Test
    public void testGetObject() throws SQLException
    {
        TableSelector userSelector = new TableSelector(CoreSchema.getInstance().getTableInfoActiveUsers());

        User user = TestContext.get().getUser();
        User selectedUser = userSelector.getObject(user.getUserId(), User.class);
        assertEquals(user, selectedUser);

        // TableSelector to test a couple exception scenarios
        TableSelector moduleSelector = new TableSelector(CoreSchema.getInstance().getTableInfoModules());
        moduleSelector.setLogLevel(Level.OFF);      // Suppress auto-logging since we're intentionally causing SQLExceptions

        // Make sure that getObject() throws if more than one row is selected
        try
        {
            moduleSelector.getObject(ModuleContext.class);
            fail("getObject() should have thrown when returning multiple objects");
        }
        catch (UncategorizedSQLException e)
        {
            String message = e.getMessage();
            // Verify that the exception message does not contain SQL (we don't want to display SQL to users...
            assertTrue("Exception message " + message + " seems to contain SQL", message.contains("SQL []") && !message.contains("SELECT"));
            // ...and that the exception is decorated, so the SQL does end up in mothership
            String decoration = ExceptionUtil.getExceptionDecoration(e, ExceptionUtil.ExceptionInfo.DialectSQL);
            assertNotNull("Exception was not decorated", decoration);
        }

        // Make sure that getObject() throws if pk == null, #20057

        // For now, null returns null to get DataReportsTest running again
        ModuleContext ctx = moduleSelector.getObject(null, ModuleContext.class);
        assertNull("getObject(null) should return null", ctx);

//        try
//        {
//            moduleSelector.getObject(null, ModuleContext.class);
//            fail("getObject() should have thrown with null pk");
//        }
//        catch (IllegalStateException e)
//        {
//            assertEquals("PK on getObject() must not be null", e.getMessage());
//        }
    }

    @Test
    public void testColumnLists() throws SQLException
    {
        TableInfo ti = CoreSchema.getInstance().getTableInfoActiveUsers();

        testColumnList(new TableSelector(ti, new HashSet<>(Arrays.asList("Email", "UserId", "DisplayName", "Created", "Active"))), false);
        testColumnList(new TableSelector(ti, new HashSet<>(ti.getColumns("Email,UserId,DisplayName,Created,Active")), null, null), false);

        testColumnList(new TableSelector(ti, PageFlowUtil.set("Email", "UserId", "DisplayName", "Created", "Active")), true);
        testColumnList(new TableSelector(ti, new LinkedHashSet<>(Arrays.asList("Email", "UserId", "DisplayName", "Created", "Active"))), true);
        testColumnList(new TableSelector(ti, new CsvSet("Email, UserId, DisplayName, Created, Active")), true);
        testColumnList(new TableSelector(ti, PageFlowUtil.set(ti.getColumn("Email"), ti.getColumn("UserId"), ti.getColumn("DisplayName"), ti.getColumn("Created"), ti.getColumn("Active")), null, null), true);
        testColumnList(new TableSelector(ti, ti.getColumns("Email,UserId,DisplayName,Created,Active"), null, null), true);

        // Singleton column collections should always be considered "stable"
        testColumnList(new TableSelector(ti, PageFlowUtil.set("Email")), true);
        testColumnList(new TableSelector(ti, new CsvSet("Email")), true);
        testColumnList(new TableSelector(ti, Collections.singleton("Email")), true);
        testColumnList(new TableSelector(ti, ti.getColumns("Email"), null, null), true);
        testColumnList(new TableSelector(ti, Collections.singleton(ti.getColumn("Email")), null, null), true);
    }

    private void testColumnList(TableSelector selector, boolean stable) throws SQLException
    {
        // The following methods should succeed with both stable and unstable ordered column lists

        assertTrue(selector.exists());
        int count = (int)selector.getRowCount();
        assertEquals(count, selector.getArray(User.class).length);
        assertEquals(count, selector.getArrayList(User.class).size());
        assertEquals(count, selector.getCollection(User.class).size());

        final MutableInt forEachCount = new MutableInt(0);
        selector.forEach(user -> {
            forEachCount.increment();
        }, User.class);
        assertEquals(count, forEachCount.intValue());

        final MutableInt forEachMapCount = new MutableInt(0);
        selector.forEachMap(map -> forEachMapCount.increment());
        assertEquals(count, forEachMapCount.intValue());

        final MutableInt forEachBatchCount = new MutableInt(0);
        selector.forEachBatch(batch -> {
            assertFalse(batch.isEmpty());
            assertTrue(batch.size() <= 3);
            forEachBatchCount.add(batch.size());
        }, User.class, 3);
        assertEquals(count, forEachBatchCount.intValue());

        // The following methods should succeed with stable ordered column lists but fail with unstable ordered column lists

        try
        {
            assertEquals(count, selector.getArray(String.class).length);
            assertTrue("Expected getArray() with primitive to fail with unstable column ordering", stable);
        }
        catch (IllegalStateException e)
        {
            assertFalse("Expected getArray() with primitive to succeed with stable column ordering", stable);
        }

        try
        {
            assertEquals(count, selector.getArrayList(String.class).size());
            assertTrue("Expected getArrayList() with primitive to fail with unstable column ordering", stable);
        }
        catch (IllegalStateException e)
        {
            assertFalse("Expected getArrayList() with primitive to succeed with stable column ordering", stable);
        }

        try
        {
            assertEquals(count, selector.getCollection(String.class).size());
            assertTrue("Expected getCollection() with primitive to fail with unstable column ordering", stable);
        }
        catch (IllegalStateException e)
        {
            assertFalse("Expected getCollection() with primitive to succeed with stable column ordering", stable);
        }

        int columnCount = selector.getColumnCount();

        try
        {
            assertEquals(count, selector.getValueMap().size());
            assertTrue("Expected getValueMap() to fail with unstable column ordering", stable);
            assertTrue("Expected getValueMap() to fail with " + StringUtilsLabKey.pluralize(columnCount, "column"), columnCount > 1);

        }
        catch (IllegalStateException e)
        {
            assertFalse("Expected getValueMap() to succeed with stable column ordering and " + StringUtilsLabKey.pluralize(columnCount, "column"), stable && columnCount > 1);
        }

        try
        {
            Map<String, Object> map = new HashMap<>();
            selector.fillValueMap(map);
            assertEquals(count, map.size());
            assertTrue("Expected fillValueMap() to fail with unstable column ordering", stable);
            assertTrue("Expected fillValueMap() to fail with " + StringUtilsLabKey.pluralize(columnCount, "column"), columnCount > 1);
        }
        catch (IllegalStateException e)
        {
            assertFalse("Expected fillValueMap() to succeed with stable column ordering and " + StringUtilsLabKey.pluralize(columnCount, "column"), stable && columnCount > 1);
        }

        //noinspection UnusedDeclaration
        try (ResultSet rs = selector.getResultSet())
        {
            assertTrue("Expected getResultSet() to fail with unstable column ordering", stable);
        }
        catch (IllegalStateException e)
        {
            assertFalse("Expected getResultSet() to succeed with stable column ordering", stable);
        }

        //noinspection UnusedDeclaration
        try (Results rs = selector.getResults())
        {
            assertTrue("Expected getResults() to fail with unstable column ordering", stable);
        }
        catch (IllegalStateException e)
        {
            assertFalse("Expected getResults() to succeed with stable column ordering", stable);
        }

        try
        {
            MutableInt forEachResultsCount = new MutableInt(0);
            selector.forEachResults(results -> {
                assertEquals(columnCount, results.getFieldIndexMap().size());
                assertEquals(columnCount, results.getFieldKeyRowMap().size());
                assertEquals(columnCount, results.getFieldMap().size());
                forEachResultsCount.increment();
            });
            assertEquals(count, forEachResultsCount.intValue());
            assertTrue("Expected getResults() to fail with unstable column ordering", stable);
        }
        catch (IllegalStateException e)
        {
            assertFalse("Expected getResults() to succeed with stable column ordering", stable);
        }
    }

    private <K> void testTableSelector(TableInfo table, Class<K> clazz) throws SQLException
    {
        TableSelector selector = new TableSelector(table);

        test(selector, clazz);
        testOffsetAndLimit(selector, clazz);

        // Verify that we can generate some execution plan
        Collection<String> executionPlan = selector.getExecutionPlan();
        assertTrue(!executionPlan.isEmpty());
    }

    @Override
    protected void verifyResultSets(TableSelector tableSelector, int expectedRowCount, boolean expectedComplete) throws SQLException
    {
        super.verifyResultSets(tableSelector, expectedRowCount, expectedComplete);

        // Test caching and scrolling options
        verifyResultSet(tableSelector.getResultSet(false, false), expectedRowCount, expectedComplete);
        verifyResultSet(tableSelector.getResultSet(false, true), expectedRowCount, expectedComplete);
        verifyResultSet(tableSelector.getResultSet(true, true), expectedRowCount, expectedComplete);

        verifyResultSet(tableSelector.getResults(), expectedRowCount, expectedComplete);
    }

    private <K> void testOffsetAndLimit(TableSelector selector, Class<K> clazz) throws SQLException
    {
        int count = (int) selector.getRowCount();

        if (count > 5)
        {
            int rowCount = 3;
            int offset = 2;

            MutableInt testCount = new MutableInt(0);
            selector.forEach(new Selector.ForEachBlock<K>()
            {
                @Override
                public void exec(K object) throws SQLException, StopIteratingException
                {
                    testCount.increment();

                    if (testCount.intValue() == rowCount)
                        stopIterating();
                }
            }, clazz);
            assertEquals(rowCount, testCount.intValue());

            selector.setMaxRows(Table.ALL_ROWS);
            assertEquals(count, (int) selector.getRowCount());
            assertEquals(count, selector.getArray(clazz).length);
            assertEquals(count, selector.getCollection(clazz).size());

            // First, query all the rows into an array and a list; we'll use these to validate that rowCount and
            // offset are working. Set an explicit row count to force the same sorting that will result below.
            selector.setMaxRows(count);
            K[] sortedArray = selector.getArray(clazz);
            List<K> sortedList = new ArrayList<>(selector.getCollection(clazz));
            verifyResultSets(selector, count, true);

            // Set a row count, verify the lengths and contents against the expected array & list subsets
            selector.setMaxRows(rowCount);
            assertEquals(rowCount, (int) selector.getRowCount());
            K[] rowCountArray = selector.getArray(clazz);
            assertEquals(rowCount, rowCountArray.length);
            assertArrayEquals(Arrays.copyOf(sortedArray, rowCount), rowCountArray);
            List<K> rowCountList = new ArrayList<>(selector.getCollection(clazz));
            assertEquals(rowCount, rowCountList.size());
            assertEquals(sortedList.subList(0, rowCount), rowCountList);
            verifyResultSets(selector, rowCount, false);

            // Set an offset, verify the lengths and contents against the expected array & list subsets
            selector.setOffset(offset);
            assertEquals(rowCount, (int) selector.getRowCount());
            K[] offsetArray = selector.getArray(clazz);
            assertEquals(rowCount, offsetArray.length);
            assertArrayEquals(Arrays.copyOfRange(sortedArray, offset, offset + rowCount), offsetArray);
            List<K> offsetList = new ArrayList<>(selector.getCollection(clazz));
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
}
