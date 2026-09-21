/*
 * Copyright (c) 2012-2026 LabKey Corporation
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
import org.labkey.api.data.dialect.SqlDialect;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static java.sql.Connection.TRANSACTION_READ_COMMITTED;
import static java.sql.Connection.TRANSACTION_READ_UNCOMMITTED;

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

        Map<Integer, Integer> identityMap = new SqlSelector(CoreSchema.getInstance().getSchema(), "SELECT RowId FROM comm.Announcements").getValueMap();
        assertTrue("Expected an identity map!", identityMap.entrySet().stream().allMatch(entry -> entry.getKey().equals(entry.getValue())));

        // Verify that we can generate the supported execution plans
        for (SqlDialect.ExecutionPlanType type : SqlDialect.ExecutionPlanType.values())
        {
            if (CoreSchema.getInstance().getSqlDialect().canShowExecutionPlan(type))
            {
                Collection<String> executionPlan = selector.getExecutionPlan(type);
                assertFalse(executionPlan.isEmpty());
            }
        }
    }

    @Override
    protected void verifyResultSets(SqlSelector sqlSelector, int expectedRowCount, boolean expectedComplete) throws SQLException
    {
        super.verifyResultSets(sqlSelector, expectedRowCount, expectedComplete);

        // Test caching and scrolling options
        verifyResultSet(sqlSelector.getResultSet(false, false), expectedRowCount, expectedComplete);
        verifyResultSet(sqlSelector.getResultSet(false, true), expectedRowCount, expectedComplete);
        verifyResultSet(sqlSelector.getResultSet(true, true), expectedRowCount, expectedComplete);

        // Verify getSize() and backward-scrolling behavior for each caching/scrollable combination. These behaviors
        // differ between a cached result set (CachedResultSet, cache == true) and a non-cached result set
        // (ResultSetImpl, cache == false), and silently switching a caller from cached to non-cached has caused
        // regressions (getSize() called before iteration, or beforeFirst() used to re-iterate).
        verifyCachedResultSet(sqlSelector, expectedRowCount);
        verifyForwardOnlyResultSet(sqlSelector, expectedRowCount);
        verifyScrollableUncachedResultSet(sqlSelector, expectedRowCount);
    }

    // A cached result set (getResultSet(true, true) -> CachedResultSet) knows its size without iterating and supports
    // backward scrolling, so it can be re-iterated after beforeFirst().
    private void verifyCachedResultSet(SqlSelector selector, int expectedRowCount) throws SQLException
    {
        try (TableResultSet rs = selector.getResultSet(true, true))
        {
            // getSize() must work before any iteration
            assertEquals("Cached ResultSet should report its size before iteration", expectedRowCount, rs.getSize());

            int count = 0;
            while (rs.next())
                count++;
            assertEquals(expectedRowCount, count);

            // Scroll back to the start and re-iterate
            rs.beforeFirst();
            int recount = 0;
            while (rs.next())
                recount++;
            assertEquals("Cached ResultSet should be re-iterable after beforeFirst()", expectedRowCount, recount);
        }
    }

    // A non-cached, forward-only result set (getResultSet(false, false) -> ResultSetImpl) cannot report its size until
    // it has been completely iterated, and cannot scroll backward.
    private void verifyForwardOnlyResultSet(SqlSelector selector, int expectedRowCount) throws SQLException
    {
        // getSize() throws until the result set has been completely iterated
        try (TableResultSet rs = selector.getResultSet(false, false))
        {
            assertThrows("getSize() should throw before a non-cached ResultSet is fully iterated", IllegalStateException.class, rs::getSize);
        }

        // Backward scrolling is not supported on a forward-only result set
        try (TableResultSet rs = selector.getResultSet(false, false))
        {
            assertThrows("beforeFirst() should throw on a forward-only ResultSet", SQLException.class, rs::beforeFirst);
        }

        // After complete iteration getSize() reports the row count
        try (TableResultSet rs = selector.getResultSet(false, false))
        {
            int count = 0;
            while (rs.next())
                count++;
            assertEquals(expectedRowCount, count);
            assertEquals("getSize() should report the row count after complete iteration", expectedRowCount, rs.getSize());
        }
    }

    // A non-cached but scrollable result set (getResultSet(false, true) -> ResultSetImpl over a scrollable JDBC
    // ResultSet) supports backward scrolling, but still cannot report its size until completely iterated because
    // getSize() depends on caching, not scrollability.
    private void verifyScrollableUncachedResultSet(SqlSelector selector, int expectedRowCount) throws SQLException
    {
        try (TableResultSet rs = selector.getResultSet(false, true))
        {
            int count = 0;
            while (rs.next())
                count++;
            assertEquals(expectedRowCount, count);

            rs.beforeFirst();
            int recount = 0;
            while (rs.next())
                recount++;
            assertEquals("Scrollable ResultSet should be re-iterable after beforeFirst()", expectedRowCount, recount);
        }

        try (TableResultSet rs = selector.getResultSet(false, true))
        {
            assertThrows("getSize() should throw before a non-cached ResultSet is fully iterated", IllegalStateException.class, rs::getSize);
        }
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

    // Not practical to test that very large ResultSets are uncached, but we can at least check that we see shared and
    // not shared connections where we expect them. And we can verify we've configured uncached settings on PostgreSQL.
    @Test
    public void testJdbcUncached() throws SQLException
    {
        DbScope scope = CoreSchema.getInstance().getScope();
        try (Connection conn = scope.getConnection())
        {
            // Default (no explicit setJdbcCaching() call) now auto-disables JDBC caching when it's safe: a separate,
            // uncached Connection outside a transaction.
            try (Connection conn2 = new SqlSelector(scope, "SELECT RowId, Body FROM comm.Announcements").getConnection())
            {
                assertNotEquals(conn, conn2);
                assertEquals(TRANSACTION_READ_UNCOMMITTED, conn2.getTransactionIsolation());
                assertFalse(conn2.getAutoCommit());
            }

            // Explicitly requesting caching shares the connection
            try (Connection conn2 = new SqlSelector(scope, "SELECT RowId, Body FROM comm.Announcements").setJdbcCaching(true).getConnection())
            {
                assertEquals(conn, conn2);
            }

            // Set and reset should still share
            try (Connection conn2 = new SqlSelector(scope, "SELECT RowId, Body FROM comm.Announcements").setJdbcCaching(false).setJdbcCaching(true).getConnection())
            {
                assertEquals(conn, conn2);
            }

            // Here we expect a different Connection object
            try (Connection conn2 = new SqlSelector(scope, "SELECT RowId, Body FROM comm.Announcements").setJdbcCaching(false).getConnection())
            {
                assertNotEquals(conn, conn2);
                assertEquals(TRANSACTION_READ_UNCOMMITTED, conn2.getTransactionIsolation());
                assertFalse(conn2.getAutoCommit());
            }
        }

        // A "self-contained" read (getArrayList(), forEach(), getRowCount(), etc., which fully consume and close the
        // ResultSet within the call) borrows the thread's shared connection rather than a dedicated one, so nested
        // queries reuse it and connection-local state stays visible. The outermost borrower puts it into no-caching
        // mode and restores it on release.
        Connection borrowed = new SqlSelector(scope, "SELECT RowId, Body FROM comm.Announcements").getConnection(true);
        try
        {
            // A plain thread-connection acquisition returns the very same object (it was borrowed, not dedicated)
            try (Connection threadConn = scope.getConnection())
            {
                assertEquals(borrowed, threadConn);
            }

            // A nested self-contained read reuses the same connection rather than grabbing another one
            try (Connection nested = new SqlSelector(scope, "SELECT RowId, Body FROM comm.Announcements").getConnection(true))
            {
                assertEquals(borrowed, nested);
            }

            assertEquals(TRANSACTION_READ_UNCOMMITTED, borrowed.getTransactionIsolation());
            assertFalse(borrowed.getAutoCommit());
        }
        finally
        {
            borrowed.close();
        }

        // Once the outermost borrower releases it, the thread connection is restored to normal caching mode
        try (Connection restored = scope.getConnection())
        {
            assertTrue(restored.getAutoCommit());
            assertEquals(TRANSACTION_READ_COMMITTED, restored.getTransactionIsolation());
        }

        // Inside a transaction, the default must NOT grab a separate Connection, even on PostgreSQL: the caller may be
        // relying on reading its own uncommitted writes, so we fall back to the shared, transactional Connection.
        try (DbScope.Transaction tx = scope.ensureTransaction())
        {
            try (Connection conn2 = new SqlSelector(scope, "SELECT RowId, Body FROM comm.Announcements").getConnection())
            {
                assertEquals(scope.getConnection(), conn2);
            }
            tx.commit();
        }
    }

    // Verify that nested DB access from a row callback reuses that same borrowed connection (rather than grabbing a
    // second one), returns correct results while the outer ResultSet is still open, doesn't truncate the outer
    // iteration, and leaves the thread connection fully restored once forEach() completes.
    @Test
    public void testNestedQueryDuringForEach() throws SQLException
    {
        DbScope scope = CoreSchema.getInstance().getScope();

        // The borrow-and-disable-caching path only engages outside a transaction
        assertFalse("Test assumes no active transaction on this thread", scope.isTransactionActive());

        // core.Containers always has at least the root container
        long expectedRows = new SqlSelector(scope, new SQLFragment("SELECT RowId FROM core.Containers")).getRowCount();
        assertTrue("core.Containers should never be empty", expectedRows > 0);

        MutableInt visited = new MutableInt(0);
        Set<Connection> callbackConnections = Collections.newSetFromMap(new IdentityHashMap<>());

        new SqlSelector(scope, new SQLFragment("SELECT RowId FROM core.Containers ORDER BY RowId")).forEach(Integer.class, rowId -> {
            visited.increment();

            // The callback runs while the outer ResultSet is open. Acquiring the thread connection must return the same
            // borrowed connection, in no-caching mode — proving nested code shares the transction/conncetion.
            try (Connection nested = scope.getConnection())
            {
                callbackConnections.add(nested);

                assertFalse("Nested access during forEach() should run on the uncached borrowed connection", nested.getAutoCommit());
                assertEquals(TRANSACTION_READ_UNCOMMITTED, nested.getTransactionIsolation());
            }

            // A nested self-contained query must return correct results even though the outer server-side cursor is open
            // on the same connection — this is the interleaving that would fail if the nested statement clobbered it.
            Integer nestedRowId = new SqlSelector(scope, new SQLFragment("SELECT RowId FROM core.Containers WHERE RowId = ?", rowId)).getObject(Integer.class);
            assertEquals("Nested query should return exactly the matching row", rowId, nestedRowId);
        });

        assertEquals("forEach() should visit every row even with nested queries in the callback", expectedRows, visited.longValue());
        assertEquals("Nested access should reuse the single borrowed connection across all rows", 1, callbackConnections.size());

        // Once the outermost borrower (forEach) releases the connection, it must be restored to normal caching mode
        try (Connection restored = scope.getConnection())
        {
            assertTrue(restored.getAutoCommit());
            assertEquals(TRANSACTION_READ_COMMITTED, restored.getTransactionIsolation());
        }
    }

    // Passing in a Connection and calling setJdbcCaching() should throw
    @Test(expected = IllegalStateException.class)
    public void testJdbcUncachedTrue() throws SQLException
    {
        DbScope scope = CoreSchema.getInstance().getScope();
        try (Connection conn = scope.getConnection())
        {
            new SqlSelector(scope, conn, "SELECT RowId, Body FROM comm.Announcements").setJdbcCaching(true);
        }
    }

    // Passing in a Connection and calling setJdbcCaching() should throw
    @Test(expected = IllegalStateException.class)
    public void testJdbcUncachedFalse() throws SQLException
    {
        DbScope scope = CoreSchema.getInstance().getScope();
        try (Connection conn = scope.getConnection())
        {
            new SqlSelector(scope, conn, "SELECT RowId, Body FROM comm.Announcements").setJdbcCaching(false);
        }
    }
}
