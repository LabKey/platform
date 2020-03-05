/*
 * Copyright (c) 2012-2018 LabKey Corporation
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
import org.junit.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.stream.Stream;

/**
 * User: adam
 * Date: 12/18/12
 * Time: 8:04 PM
 */
public class SqlSelectorTestCase extends AbstractSelectorTestCase<SqlSelector>
{
    @Test
    public void testSqlSelector() throws SQLException
    {
        SqlSelector selector = new SqlSelector(CoreSchema.getInstance().getSchema(), "SELECT RowId, Body FROM comm.Announcements");
        test(selector, TestClass.class);

        // Test zero rows case
        try (Stream<Integer> stream = new SqlSelector(CoreSchema.getInstance().getSchema(), "SELECT RowId FROM comm.Announcements WHERE 1 = 0").uncachedStream(Integer.class))
        {
            MutableInt count = new MutableInt(0);
            stream.forEach(id -> count.increment());
            assertEquals(0, count.intValue());
        }

        try
        {
            new SqlSelector(CoreSchema.getInstance().getSchema(), "SELECT RowId FROM comm.Announcements").getValueMap();
            fail("Expected getValueMap() call with a single column to fail");
        }
        catch (IllegalStateException e)
        {
            assertTrue(e.getMessage().startsWith("Must select at least two columns"));
        }

        // Verify that we can generate some execution plan
        Collection<String> executionPlan = selector.getExecutionPlan();
        assertTrue(!executionPlan.isEmpty());
    }

    @Override
    protected void verifyResultSets(SqlSelector sqlSelector, int expectedRowCount, boolean expectedComplete) throws SQLException
    {
        super.verifyResultSets(sqlSelector, expectedRowCount, expectedComplete);

        // Test caching and scrolling options
        verifyResultSet(sqlSelector.getResultSet(false, false), expectedRowCount, expectedComplete);
        verifyResultSet(sqlSelector.getResultSet(false, true), expectedRowCount, expectedComplete);
        verifyResultSet(sqlSelector.getResultSet(true, true), expectedRowCount, expectedComplete);
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

    @Test(expected = IllegalStateException.class)
    public void testJdbcUncached() throws SQLException
    {
        DbScope scope = CoreSchema.getInstance().getScope();
        try (Connection conn = scope.getConnection())
        {
            new SqlSelector(scope, conn, "SELECT RowId, Body FROM comm.Announcements").setJdbcUncached();
        }
    }
}
