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

import org.apache.log4j.Level;
import org.junit.Test;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.security.User;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.TestContext;
import org.springframework.jdbc.UncategorizedSQLException;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

        // Make sure that getObject() throws if more than one row is selected
        TableSelector moduleSelector = new TableSelector(CoreSchema.getInstance().getTableInfoModules());
        moduleSelector.setLogLevel(Level.OFF);      // Suppress auto-logging since we're intentionally causing a SQLException

        try
        {
            moduleSelector.getObject(ModuleContext.class);
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
    }

    public <K> void testTableSelector(TableInfo table, Class<K> clazz) throws SQLException
    {
        TableSelector selector = new TableSelector(table);

        test(selector, clazz);
        testOffsetAndLimit(selector, clazz);
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
