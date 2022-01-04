/*
 * Copyright (c) 2013-2019 LabKey Corporation
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

import org.apache.logging.log4j.Level;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.collections.ConcurrentHashSet;
import org.labkey.api.data.BaseSelector.ResultSetHandler;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.CPUTimer;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.TestContext;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
    // This handler expects to always return a single long integer value
    private static final ResultSetHandler<Long> LONG_RETURNING_RESULTSET_HANDLER = (rs, conn) -> {
        rs.next();
        return rs.getLong(1);
    };

    public static DbSequence get(Container c, String name)
    {
        return get(c, name, 0);
    }

    public static DbSequence get(Container c, String name, int id)
    {
        return new DbSequence(c, name, ensure(c, name, id));
    }

    // we are totally 'leaking' these sequences, however a) they are small b) we leak < 2 a day, so...
    static final ConcurrentHashMap<String, DbSequence.Preallocate> _sequences = new ConcurrentHashMap<>();

    public static DbSequence getPreallocatingSequence(Container c, String name)
    {
        return getPreallocatingSequence(c, name, 0, 100);
    }

    public static void invalidatePreallocatingSequence(Container c, String name, int id)
    {
        String key = c.getId() + "/" + name + "/" + id;
        _sequences.remove(key);
    }


    public static DbSequence getPreallocatingSequence(Container c, String name, int id, int batchSize)
    {
        String key = c.getId() + "/" + name + "/" + id;
        return _sequences.computeIfAbsent(key, (k) -> new DbSequence.Preallocate(c, name, ensure(c, name, id), batchSize));
    }

    /* This is not a recommended, but if you get stuck and need to reserve a block at once */
    public static long reserveSequentialBlock(DbSequence seq, int count)
    {
        if (!(seq instanceof DbSequence.Preallocate))
            throw new IllegalStateException();
        return ((DbSequence.Preallocate)seq).reserveSequentialBlock(count);
    }


    private static int ensure(Container c, String name, int id)
    {
        Integer rowId = getRowId(c, name, id);

        if (null != rowId)
            return rowId;
        else
            return create(c, name, id);
    }


    public static @Nullable Integer getRowId(Container c, String name, int id)
    {
        TableInfo tinfo = getTableInfo();
        SQLFragment getRowIdSql = new SQLFragment("SELECT RowId FROM ").append(tinfo.getSelectName());
        getRowIdSql.append(" WHERE Container = ? AND Name = ? AND Id = ?");
        getRowIdSql.add(c);
        getRowIdSql.add(name);
        getRowIdSql.add(id);

        return executeAndMaybeReturnInteger(tinfo, getRowIdSql);
    }


    private static Collection<Integer> getRowIds(Container c, String likePrefix, int id)
    {
        TableInfo tinfo = getTableInfo();
        SQLFragment getRowIdSql = new SQLFragment("SELECT RowId FROM ").append(tinfo.getSelectName());
        getRowIdSql.append(" WHERE Container = ? AND Name LIKE ? AND Id = ?");
        getRowIdSql.add(c);
        getRowIdSql.add(likePrefix + "%");
        getRowIdSql.add(id);

        return executeAndReturnIntCollection(tinfo, getRowIdSql);
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

    public static void deleteLike(Container c, String likePrefix, int id, SqlDialect dialect)
    {
        Collection<Integer> rowIds = getRowIds(c, likePrefix, id);

        if (!rowIds.isEmpty())
        {
            TableInfo tinfo = getTableInfo();
            SQLFragment sql = new SQLFragment("DELETE FROM ").append(tinfo.getSelectName()).append(" WHERE Container = ?");
            sql.add(c);
            sql.append(" AND RowId");
            sql = dialect.appendInClauseSql(sql, rowIds);

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


    static long next(DbSequence sequence)
    {
        Pair<Long,Long> p = reserve(sequence, 1);
        return p.second;
    }


    // .first value returned is 'current', call to next() should return current+1
    // .second value is reserved for use by caller
    // in other words, next() should return values [pair.first+1, pair.second] inclusive
    static Pair<Long,Long> reserve(DbSequence sequence, int count)
    {
        TableInfo tinfo = getTableInfo();

        SQLFragment sql = new SQLFragment();
        sql.append("UPDATE ").append(tinfo.getSelectName()).append(" SET Value = (");

        addValueSql(sql, tinfo, sequence);

        sql.append(") + ? WHERE Container = ? AND RowId = ?");
        sql.add(count);
        sql.add(sequence.getContainer());
        sql.add(sequence.getRowId());

        // Reselect the current value
        tinfo.getSqlDialect().addReselect(sql, tinfo.getColumn("Value"), null);

        // Add locking appropriate to this dialect
        addLocks(tinfo, sql);

        long last = executeAndReturnLong(tinfo, sql, Level.WARN);
        long first = last - count;
        return new Pair<>(first,last);
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
    static void ensureMinimum(DbSequence sequence, long minimum)
    {
        TableInfo tinfo = getTableInfo();

        SQLFragment sql = new SQLFragment("UPDATE ").append(tinfo.getSelectName()).append(" SET Value = ? WHERE Container = ? AND RowId = ? AND Value < ?");
        sql.add(minimum);
        sql.add(sequence.getContainer());
        sql.add(sequence.getRowId());
        sql.add(minimum);

        // Add locking appropriate to this dialect
        addLocks(tinfo, sql);

        execute(tinfo, sql);
    }

    // Explicitly sets the sequence value, only used at shutdown!
    static void setSequenceValue(DbSequence sequence, long value)
    {
        TableInfo tinfo = getTableInfo();

        SQLFragment sql = new SQLFragment("UPDATE ").append(tinfo.getSelectName()).append(" SET Value = ? WHERE Container = ? AND RowId = ?");
        sql.add(value);
        sql.add(sequence.getContainer());
        sql.add(sequence.getRowId());

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

    // Executes in a separate connection that does NOT participate in the current transaction. Always returns an long.
    private static long executeAndReturnLong(TableInfo tinfo, SQLFragment sql, Level level)
    {
        DbScope scope = tinfo.getSchema().getScope();

        try (Connection conn = scope.getPooledConnection())
        {
            return new SqlExecutor(scope, conn).setLogLevel(level).executeWithResults(sql, LONG_RETURNING_RESULTSET_HANDLER);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }


    // Executes in a separate connection that does NOT participate in the current transaction. Always returns an int.
    private static Collection<Integer> executeAndReturnIntCollection(TableInfo tinfo, SQLFragment sql)
    {
        DbScope scope = tinfo.getSchema().getScope();

        try (Connection conn = scope.getPooledConnection())
        {
            return new SqlSelector(scope, conn, sql).getCollection(Integer.class);
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
        private final String NAME_BULK = "org.labkey.api.data.DbSequence.TestBulk/" + GUID.makeGUID();

        private DbSequence _sequence;
        private DbSequence.Preallocate _sequenceBulk;

        @Before
        public void setup()
        {
            Container c = JunitUtil.getTestContainer();

            DbSequenceManager.delete(c, NAME);
            _sequence = DbSequenceManager.get(c, NAME);
            _sequenceBulk = (DbSequence.Preallocate)DbSequenceManager.getPreallocatingSequence(c, NAME_BULK);
        }

        void _testBasicOperations(DbSequence seq)
        {
            assertEquals(0, seq.current());
            assertEquals(1, seq.next());
            assertEquals(1, seq.current());
            assertEquals(2, seq.next());
            assertEquals(2, seq.current());

            seq.ensureMinimum(1000);
            assertEquals(1000, seq.current());
            seq.ensureMinimum(500);
            assertEquals(1000, seq.current());
            seq.ensureMinimum(-3);
            assertEquals(1000, seq.current());
            assertEquals(1001, seq.next());
            assertEquals(1001, seq.current());
            seq.ensureMinimum(1002);
            assertEquals(1002, seq.current());
        }

        @Test
        public void testBasicOperations()
        {
            _testBasicOperations(_sequence);
            _testBasicOperations(_sequenceBulk);
        }

        void _testPerformance(DbSequence seq, String name)
        {
            final int n = 1000;
            CPUTimer timer = new CPUTimer(name);

            timer.start();
            for (int i = 0; i < n; i++)
                assertEquals(i + 1, seq.next());
            timer.stop();

            TestContext.get().logPerfResult(timer);

// TODO: Restore this check once we fix or trash lkwin03 agent01, which fails because it's slow
//            final long elapsed = timer.getTotalMilliseconds();
//            final double perSecond = n / (elapsed / 1000.0);
//            assertTrue("Less than 100 iterations per second: " + perSecond, perSecond > 100);   // A very low bar
        }

        @Test
        public void testPerformance()
        {
            _testPerformance(_sequence, "DbSequence.next");
            _testPerformance(_sequenceBulk, "DbSequence.preallocate");
        }

        void _multiThreadIncrementStressTest(DbSequence seq) throws Throwable
        {
            final int threads = 5;
            final int n = 1000;
            final int totalCount = threads * n;
            final Set<Long> values = new ConcurrentHashSet<>();
            final Set<Long> duplicateValues = new ConcurrentHashSet<>();
            final long start = System.currentTimeMillis();

            JunitUtil.createRaces(() -> {
                long next = seq.next();
                if (!values.add(next))
                    duplicateValues.add(next);
            }, threads, n, 60);

            final long elapsed = System.currentTimeMillis() - start;
            final double perSecond = totalCount / (elapsed / 1000.0);

            assertEquals(duplicateValues.size() + " duplicate values were detected: " + duplicateValues.toString(), 0, duplicateValues.size());
            assertEquals(totalCount, values.size());

            for (long i = 0; i < threads * n; i++)
                assertTrue(values.contains(i + 1));

            assertTrue("Less than 100 iterations per second: " + perSecond, perSecond > 100);   // A very low bar
        }

        @Test
        public void multiThreadIncrementStressTest() throws Throwable
        {
            _multiThreadIncrementStressTest(_sequence);
            _multiThreadIncrementStressTest(_sequenceBulk);
        }


        @Test
        // Simple test that create() responds gracefully if the sequence already exists. See #19673.
        public void createTest()
        {
            final String name = "org.labkey.api.data.DbSequence.Test/" + GUID.makeGUID();

            int rowId = DbSequenceManager.create(JunitUtil.getTestContainer(), name, 0);
            assertEquals(rowId, DbSequenceManager.create(JunitUtil.getTestContainer(), name, 0));
        }

        @Test
        // More real world test for #19673. Multiple threads should be able to ensure() the sequence without issues.
        public void multiThreadCreateTest() throws Throwable
        {
            final int threads = 5;
            final String name = "org.labkey.api.data.DbSequence.Test/" + GUID.makeGUID();

            JunitUtil.createRaces(() -> DbSequenceManager.ensure(JunitUtil.getTestContainer(), name, 0), threads, 1, 60);
        }

        @Test
        public void testShutdown()
        {
            long first = _sequenceBulk.next();
            _sequenceBulk.shutdownPre();
            _sequenceBulk.shutdownStarted();
            long after = _sequenceBulk.next();
            assertEquals(first+1, after);
        }

        @After
        public void cleanup()
        {
            Container c = JunitUtil.getTestContainer();
            DbSequenceManager.delete(c, NAME);
        }
    }
}
