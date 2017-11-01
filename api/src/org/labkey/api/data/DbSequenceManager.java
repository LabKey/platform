/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.collections.ConcurrentHashSet;
import org.labkey.api.data.BaseSelector.ResultSetHandler;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;

/**
 * User: adam
 * Date: 4/6/13
 * Time: 2:23 PM
 */
public class DbSequenceManager
{
    // This handler expects to always return a single integer value
    private static final ResultSetHandler<Integer> INTEGER_RETURNING_RESULTSET_HANDLER = (rs, conn) -> {
        rs.next();
        return rs.getInt(1);
    };

    public static DbSequence get(Container c, String name)
    {
        return get(c, name, 0);
    }


    public static DbSequence get(Container c, String name, int id)
    {
        return new DbSequence(c, ensure(c, name, id));
    }


    private static int ensure(Container c, String name, int id)
    {
        Integer rowId = getRowId(c, name, id);

        if (null != rowId)
            return rowId;
        else
            return create(c, name, id);
    }


    private static @Nullable Integer getRowId(Container c, String name, int id)
    {
        TableInfo tinfo = getTableInfo();
        SQLFragment getRowIdSql = new SQLFragment("SELECT RowId FROM ").append(tinfo.getSelectName());
        getRowIdSql.append(" WHERE Container = ? AND Name = ? AND Id = ?");
        getRowIdSql.add(c);
        getRowIdSql.add(name);
        getRowIdSql.add(id);

        return executeAndMaybeReturnInteger(tinfo, getRowIdSql);
    }


    // Always initializes to 0; use ensureMinimumValue() to set a higher starting point
    private static int create(Container c, String name, int id)
    {
        TableInfo tinfo = getTableInfo();

        SQLFragment insertSql = new SQLFragment("INSERT INTO ").append(tinfo.getSelectName());
        insertSql.append(" (Container, Name, Id, Value) VALUES (?, ?, ?, ?)");

        insertSql.add(c);
        insertSql.add(name);
        insertSql.add(id);
        insertSql.add(0);

        tinfo.getSqlDialect().addReselect(insertSql, tinfo.getColumn("RowId"), null);

        try
        {
            // Don't bother logging constraint violations
            return executeAndReturnInt(tinfo, insertSql, Level.ERROR);
        }
        catch (DataIntegrityViolationException e)
        {
            // Race condition... another thread already created the DbSequence, so just return the existing RowId.
            Integer rowId = getRowId(c, name, id);

            if (null == rowId)
                throw new IllegalStateException("Can't create DbSequence");
            else
                return rowId;
        }
    }


    // Not typically needed... CoreContainerListener deletes all the sequences scoped to the container
    public static void delete(Container c, String name)
    {
        delete(c, name, 0);
    }


    // Useful for cases where multiple sequences are needed in a single folder, e.g., a sequence that generates row keys
    // for a list or dataset. Like the other DbSequence operations, the delete executes outside of the current thread
    // transaction; best practice is to commit the full object delete and then (on success) delete the associated sequence.
    public static void delete(Container c, String name, int id)
    {
        Integer rowId = getRowId(c, name, id);

        if (null != rowId)
        {
            TableInfo tinfo = getTableInfo();
            SQLFragment sql = new SQLFragment("DELETE FROM ").append(tinfo.getSelectName()).append(" WHERE Container = ? AND RowId = ?");
            sql.add(c);
            sql.add(rowId);

            execute(tinfo, sql);
        }
    }


    // Used by container delete
    // TODO: Currently called after all container listener delete methods have executed successfully... should this be transacted instead?
    public static void deleteAll(Container c)
    {
        TableInfo tinfo = getTableInfo();
        SQLFragment sql = new SQLFragment("DELETE FROM ").append(tinfo.getSelectName()).append(" WHERE Container = ?");
        sql.add(c);

        execute(tinfo, sql);
    }


    static int current(DbSequence sequence)
    {
        TableInfo tinfo = getTableInfo();
        SQLFragment sql = new SQLFragment("SELECT Value FROM ").append(tinfo.getSelectName()).append(" WHERE Container = ? AND RowId = ?");
        sql.add(sequence.getContainer());
        sql.add(sequence.getRowId());

        Integer currentValue = executeAndMaybeReturnInteger(tinfo, sql);

        if (null == currentValue)
            throw new IllegalStateException("Current value for " + sequence + " was null!");

        return currentValue;
    }


    static int next(DbSequence sequence)
    {
        TableInfo tinfo = getTableInfo();

        SQLFragment sql = new SQLFragment();
        sql.append("UPDATE ").append(tinfo.getSelectName()).append(" SET Value = ");

        addValueSql(sql, tinfo, sequence);

        sql.append(" + 1 WHERE Container = ? AND RowId = ?");
        sql.add(sequence.getContainer());
        sql.add(sequence.getRowId());

        // Reselect the current value
        tinfo.getSqlDialect().addReselect(sql, tinfo.getColumn("Value"), null);

        // Add locking appropriate to this dialect
        addLocks(tinfo, sql);

        return executeAndReturnInt(tinfo, sql, Level.WARN);
    }


    private static void addLocks(TableInfo tinfo, SQLFragment updateSql)
    {
        if (tinfo.getSqlDialect().isSqlServer())
            updateSql.insert(updateSql.indexOf("SET"), "WITH (XLOCK, ROWLOCK) ");
    }


    private static void addValueSql(SQLFragment sql, TableInfo tinfo, DbSequence sequence)
    {
        SqlDialect dialect = tinfo.getSqlDialect();

        if (dialect.isPostgreSQL())
        {
            // SELECT with FOR UPDATE locks the row to ensure a true atomic update
            SQLFragment selectForUpdate = new SQLFragment("SELECT Value FROM ").append(tinfo, "seq").append(" WHERE Container = ? AND RowId = ? FOR UPDATE");
            selectForUpdate.add(sequence.getContainer());
            selectForUpdate.add(sequence.getRowId());
            sql.append("(").append(selectForUpdate).append(")");
        }
        else
        {
            sql.append("Value");
        }
    }


    // Sets the sequence value to requested minimum, if it's currently less than this value
    static void ensureMinimum(DbSequence sequence, int minimum)
    {
        TableInfo tinfo = getTableInfo();

        SQLFragment sql = new SQLFragment("UPDATE ").append(tinfo.getSelectName()).append(" SET Value = ? WHERE Container = ? AND RowId = ? AND ");
        sql.add(minimum);
        sql.add(sequence.getContainer());
        sql.add(sequence.getRowId());

        addValueSql(sql, tinfo, sequence);

        sql.append(" < ?");
        sql.add(minimum);

        // Add locking appropriate to this dialect
        addLocks(tinfo, sql);

        execute(tinfo, sql);
    }


    private static TableInfo getTableInfo()
    {
        return CoreSchema.getInstance().getTableInfoDbSequences();
    }


    // Executes in a separate connection that does NOT participate in the current transaction
    private static void execute(TableInfo tinfo, SQLFragment sql)
    {
        DbScope scope = tinfo.getSchema().getScope();

        try (Connection conn = scope.getPooledConnection())
        {
            new SqlExecutor(scope, conn).execute(sql);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }


    // Executes in a separate connection that does NOT participate in the current transaction. Returns Integer or null.
    private static @Nullable Integer executeAndMaybeReturnInteger(TableInfo tinfo, SQLFragment sql)
    {
        DbScope scope = tinfo.getSchema().getScope();

        try (Connection conn = scope.getPooledConnection())
        {
            return new SqlSelector(scope, conn, sql).getObject(Integer.class);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }


    // Executes in a separate connection that does NOT participate in the current transaction. Always returns an int.
    private static int executeAndReturnInt(TableInfo tinfo, SQLFragment sql, Level level)
    {
        DbScope scope = tinfo.getSchema().getScope();

        try (Connection conn = scope.getPooledConnection())
        {
            return new SqlExecutor(scope, conn).setLogLevel(level).executeWithResults(sql, INTEGER_RETURNING_RESULTSET_HANDLER);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }


    @TestWhen(TestWhen.When.BVT)
    public static class TestCase extends Assert
    {
        // Append a GUID to allow multiple, simultaneous invocations of this test
        private final String NAME = "org.labkey.api.data.DbSequence.Test/" + GUID.makeGUID();

        private DbSequence _sequence;

        @Before
        public void setup()
        {
            Container c = JunitUtil.getTestContainer();

            DbSequenceManager.delete(c, NAME);
            _sequence = DbSequenceManager.get(c, NAME);
        }

        @Test
        public void testBasicOperations()
        {
            assertEquals(0, _sequence.current());
            assertEquals(1, _sequence.next());
            assertEquals(1, _sequence.current());
            assertEquals(2, _sequence.next());
            assertEquals(2, _sequence.current());

            _sequence.ensureMinimum(1000);
            assertEquals(1000, _sequence.current());
            _sequence.ensureMinimum(500);
            assertEquals(1000, _sequence.current());
            _sequence.ensureMinimum(-3);
            assertEquals(1000, _sequence.current());
            assertEquals(1001, _sequence.next());
            assertEquals(1001, _sequence.current());
            _sequence.ensureMinimum(1002);
            assertEquals(1002, _sequence.current());
        }

        @Test
        public void testPerformance()
        {
            final int n = 1000;
            final long start = System.currentTimeMillis();

            for (int i = 0; i < n; i++)
                assertEquals(i + 1, _sequence.next());

            final long elapsed = System.currentTimeMillis() - start;
            final double perSecond = n / (elapsed / 1000.0);

// TODO: Restore this check once we fix or trash lkwin03 agent01, which fails because it's slow
//            assertTrue("Less than 100 iterations per second: " + perSecond, perSecond > 100);   // A very low bar
        }

        @Test
        public void multiThreadIncrementStressTest() throws InterruptedException
        {
            final int threads = 5;
            final int n = 1000;
            final int totalCount = threads * n;
            final Set<Integer> values = new ConcurrentHashSet<>();
            final Set<Integer> duplicateValues = new ConcurrentHashSet<>();
            final long start = System.currentTimeMillis();

            JunitUtil.createRaces(() -> {
                int next = _sequence.next();
                if (!values.add(next))
                    duplicateValues.add(next);
            }, threads, n, 60);

            final long elapsed = System.currentTimeMillis() - start;
            final double perSecond = totalCount / (elapsed / 1000.0);

            assertEquals(duplicateValues.size() + " duplicate values were detected: " + duplicateValues.toString(), 0, duplicateValues.size());
            assertEquals(totalCount, values.size());

            for (int i = 0; i < threads * n; i++)
                assertTrue(values.contains(i + 1));

            assertTrue("Less than 100 iterations per second: " + perSecond, perSecond > 100);   // A very low bar
        }

        @Test
        // Simple test that create() responds gracefully if the sequence already exists. See #19673.
        public void createTest() throws Throwable
        {
            final String name = "org.labkey.api.data.DbSequence.Test/" + GUID.makeGUID();

            int rowId = DbSequenceManager.create(JunitUtil.getTestContainer(), name, 0);
            assertEquals(rowId, DbSequenceManager.create(JunitUtil.getTestContainer(), name, 0));
        }

        @Test
        // More real world test for #19673. Multiple threads should be able to ensure() the sequence without issues.
        public void multiThreadCreateTest() throws Throwable
        {
            final Set<Throwable> failures = new ConcurrentHashSet<>();
            final int threads = 5;
            final String name = "org.labkey.api.data.DbSequence.Test/" + GUID.makeGUID();

            JunitUtil.createRaces(() -> {
                try
                {
                    DbSequenceManager.ensure(JunitUtil.getTestContainer(), name, 0);
                }
                catch (Throwable t)
                {
                    failures.add(t);
                }
            }, threads, 1, 60);

            if (!failures.isEmpty())
                throw failures.iterator().next();
        }

        @After
        public void cleanup()
        {
            Container c = JunitUtil.getTestContainer();
            DbSequenceManager.delete(c, NAME);
        }
    }
}
