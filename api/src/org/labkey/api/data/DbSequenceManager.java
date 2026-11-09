/*
 * Copyright (c) 2013-2026 LabKey Corporation
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
import org.apache.logging.log4j.Logger;
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
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.ShutdownListener;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.logging.LogHelper;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/**
 * User: adam
 * Date: 4/6/13
 * Time: 2:23 PM
 */
public class DbSequenceManager
{
    private static final Logger LOG = LogHelper.getLogger(DbSequenceManager.class, "Database-backed sequences");

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

    public static DbSequence get(Container c, String name, long id)
    {
        return new DbSequence(c, name, ensure(c, name, id));
    }

    public static DbSequence.ReclaimableDbSequence getReclaimable(Container c, String name, int id)
    {
        return getReclaimable(c, name, id, true);
    }

    /** @param lockNow false defers the transaction's row lock to its first draw or update of the sequence */
    public static DbSequence.ReclaimableDbSequence getReclaimable(Container c, String name, int id, boolean lockNow)
    {
        return new DbSequence.ReclaimableDbSequence(c, name, ensure(c, name, id, lockNow));
    }

    // we are totally 'leaking' these sequences, however a) they are small b) we leak < 2 a day, so...
    static final ConcurrentHashMap<String, DbSequence.Preallocate> _sequences = new ConcurrentHashMap<>();

    static final ShutdownListener shutdownListener = ShutdownListener.of("DbSequenceManager", null, () -> {
        synchronized (_sequences)
        {
            for (var seq : _sequences.values())
                seq.sync();
        }
    });

    static
    {
        ContextListener.addShutdownListener(shutdownListener);
    }

    public static DbSequence getPreallocatingSequence(Container c, String name)
    {
        return getPreallocatingSequence(c, name, 0, 100);
    }

    public static void invalidatePreallocatingSequence(Container c, String name, long id)
    {
        String key = c.getId() + "/" + name + "/" + id;
        _sequences.remove(key);
    }

    public static DbSequence getPreallocatingSequence(Container c, String name, long id, int batchSize)
    {
        String key = c.getId() + "/" + name + "/" + id;
        DbSequence.Preallocate sequence = _sequences.computeIfAbsent(key, (k) -> new DbSequence.Preallocate(c, name, ensure(c, name, id), batchSize));
        // sync() writes back unguarded, so each sequence keeps one instance, sized by the largest batch any caller asks for
        sequence.ensureBatchSize(batchSize);
        return sequence;
    }

    /* This is not recommended, but if you get stuck and need to reserve a block at once */
    public static long reserveSequentialBlock(DbSequence seq, long count)
    {
        if (!(seq instanceof DbSequence.Preallocate))
            throw new IllegalStateException();
        return ((DbSequence.Preallocate)seq).reserveSequentialBlock(count);
    }

    private static int ensure(Container c, String name, long id)
    {
        return ensure(c, name, id, false);
    }

    private static int ensure(Container c, String name, long id, boolean withUpdateLock)
    {
        Integer rowId = getRowId(c, name, id, withUpdateLock);

        return Objects.requireNonNullElseGet(rowId, () -> create(c, name, id, withUpdateLock));
    }

    public static @Nullable Integer getRowId(Container c, String name, long id)
    {
        return getRowId(c, name, id, false);
    }

    public static @Nullable Integer getRowId(Container c, String name, long id, boolean withUpdateLock)
    {
        TableInfo tinfo = getTableInfo();
        SQLFragment getRowIdSql = new SQLFragment("SELECT RowId FROM ").append(tinfo.getSelectName());
        getRowIdSql.unsafeAppend(" WHERE Container = ? AND Name = ? AND Id = ?");
        getRowIdSql.add(c);
        getRowIdSql.add(name);
        getRowIdSql.add(id);

        Integer rowId = executeAndMaybeReturnInteger(tinfo, getRowIdSql);

        if (rowId != null && rowId > 0 && withUpdateLock)
            lockRow(tinfo, rowId);

        return rowId;
    }

    private static void lockRow(TableInfo tinfo, int rowId)
    {
        SQLFragment lockRowSql = new SQLFragment().unsafeAppend("SELECT RowId FROM ").append(tinfo);
        lockRowSql.unsafeAppend(" WHERE RowId = ? FOR UPDATE").add(rowId);
        new SqlExecutor(tinfo.getSchema()).execute(lockRowSql); // get lock for current transaction
    }


    private static Collection<Integer> getRowIds(Container c, String likePrefix, long id)
    {
        TableInfo tinfo = getTableInfo();
        SQLFragment getRowIdSql = new SQLFragment("SELECT RowId FROM ").append(tinfo.getSelectName());
        getRowIdSql.unsafeAppend(" WHERE Container = ? AND Name LIKE ? AND Id = ?");
        getRowIdSql.add(c);
        getRowIdSql.add(likePrefix + "%");
        getRowIdSql.add(id);

        return executeAndReturnIntCollection(tinfo, getRowIdSql);
    }

    private static int create(Container c, String name, int id)
    {
        return create(c, name, id, false);
    }

    // Always initializes to 0; use ensureMinimumValue() to set a higher starting point
    private static int create(Container c, String name, long id, boolean withUpdateLock)
    {
        TableInfo tinfo = getTableInfo();

        // TODO BIGINT
        // convert Id to INT->BIGINT
        if (id > Integer.MAX_VALUE)
            throw new IllegalStateException("Sequence ID is too large: " + id);

        SQLFragment insertSql = new SQLFragment("INSERT INTO ").append(tinfo.getSelectName());
        insertSql.unsafeAppend(" (Container, Name, Id, Value) VALUES (?, ?, ?, ?)");

        insertSql.add(c);
        insertSql.add(name);
        insertSql.add(id);
        insertSql.add(0);

        tinfo.getSqlDialect().addReselect(insertSql, tinfo.getColumn("RowId"), null);

        try
        {
            // Don't bother logging constraint violations
            int rowId = executeAndReturnInt(tinfo, insertSql, Level.ERROR);
            // The insert ran outside the transaction, so it holds no lock
            if (withUpdateLock)
                lockRow(tinfo, rowId);
            return rowId;
        }
        catch (DataIntegrityViolationException e)
        {
            // Race condition... another thread already created the DbSequence, so just return the existing RowId.
            Integer rowId = getRowId(c, name, id, withUpdateLock);

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
            SQLFragment sql = new SQLFragment("DELETE FROM ").append(tinfo.getSelectName()).unsafeAppend(" WHERE RowId = ?");
            sql.add(rowId);

            execute(tinfo, sql);
        }
    }

    public static void deleteLike(Container c, String likePrefix, long id, SqlDialect dialect)
    {
        Collection<Integer> rowIds = getRowIds(c, likePrefix, id);

        if (!rowIds.isEmpty())
        {
            TableInfo tinfo = getTableInfo();
            SQLFragment sql = new SQLFragment("DELETE FROM ").append(tinfo.getSelectName()).unsafeAppend(" WHERE RowId");
            sql = dialect.appendInClauseSql(sql, rowIds);

            execute(tinfo, sql);
        }
    }


    // Used by container delete
    // TODO: Currently called after all container listener delete methods have executed successfully... should this be transacted instead?
    public static void deleteAll(Container c)
    {
        TableInfo tinfo = getTableInfo();
        SQLFragment sql = new SQLFragment("DELETE FROM ").append(tinfo.getSelectName()).unsafeAppend(" WHERE Container = ?");
        sql.add(c);

        execute(tinfo, sql);
    }


    static long current(DbSequence sequence)
    {
        TableInfo tinfo = getTableInfo();
        SQLFragment sql = new SQLFragment("SELECT Value FROM ").append(tinfo.getSelectName()).unsafeAppend(" WHERE RowId = ?");
        sql.add(sequence.getRowId());

        Long currentValue = executeAndMaybeReturnLong(tinfo, sql);

        if (null == currentValue)
            throw new IllegalStateException("Current value for " + sequence + " was null!");

        return currentValue;
    }


    static long next(DbSequence sequence)
    {
        ReclaimableBlocks blocks = getReclaimableBlocks(sequence);
        if (null != blocks)
            return blocks.next(sequence);

        Pair<Long,Long> p = reserve(sequence, 1);
        return p.second;
    }


    static long nextAtLeast(DbSequence sequence, long minimum)
    {
        ReclaimableBlocks blocks = getReclaimableBlocks(sequence);
        if (null != blocks)
            return blocks.nextAtLeast(sequence, minimum);

        return reserve(sequence, 1, minimum).second;
    }


    // The holder for the current transaction, or null if the sequence isn't reclaimable or no transaction is active
    private static @Nullable ReclaimableBlocks getReclaimableBlocks(DbSequence sequence)
    {
        if (!sequence.useCurrentTransaction())
            return null;

        DbScope.Transaction tx = getTableInfo().getSchema().getScope().getCurrentTransaction();
        if (null == tx)
            return null;

        return tx.addCommitTask(new ReclaimableBlocks(), DbScope.CommitTaskOption.PRECOMMIT);
    }


    /**
     * Reserves reclaimable sequence values in growing blocks, keyed per transaction rather than per handle because one
     * transaction can hold several handles to the same sequence. The reserving UPDATE row-locks the sequence until
     * commit; PRECOMMIT writes the unused tail back, so a commit that skips the write-back leaves a gap, never a duplicate.
     */
    private static class ReclaimableBlocks implements Runnable
    {
        private static final long MAX_BLOCK_SIZE = 100;

        private static class Block
        {
            private long _current;
            private final long _last;
            private final long _size;

            private Block(long current, long last, long size)
            {
                _current = current;
                _last = last;
                _size = size;
            }
        }

        private final Map<Long, Block> _blocks = new HashMap<>();
        // Draws from PRECOMMIT tasks that run after the write-back go straight to the database
        private boolean _flushed = false;

        synchronized long next(DbSequence sequence)
        {
            return draw(sequence, null);
        }

        synchronized long nextAtLeast(DbSequence sequence, long minimum)
        {
            if (!_blocks.containsKey(sequence.getRowId()))
                return draw(sequence, minimum);

            ensureMinimum(sequence, minimum);
            return draw(sequence, null);
        }

        // A minimum is applied only by a reservation, so callers pass one only when no block is outstanding
        private long draw(DbSequence sequence, @Nullable Long minimum)
        {
            if (_flushed)
                return reserve(sequence, 1, minimum).second;

            Block block = _blocks.get(sequence.getRowId());
            if (null == block || block._current == block._last)
            {
                long size = 1;
                if (null != block)
                    size = Math.min(block._size * 2, MAX_BLOCK_SIZE);
                Pair<Long, Long> reserved = reserve(sequence, size, minimum);
                block = new Block(reserved.first, reserved.second, size);
                _blocks.put(sequence.getRowId(), block);
            }

            block._current++;
            return block._current;
        }

        synchronized void ensureMinimum(DbSequence sequence, long minimum)
        {
            Block block = _blocks.get(sequence.getRowId());
            if (null == block)
            {
                updateMinimum(sequence, minimum);
            }
            else if (minimum <= block._last)
            {
                block._current = Math.max(block._current, minimum);
            }
            else
            {
                // The stored value is the block's last, so this sets it to the minimum; keeping the size keeps blocks growing
                updateMinimum(sequence, minimum);
                _blocks.put(sequence.getRowId(), new Block(minimum, minimum, block._size));
            }
        }

        synchronized void setSequenceValue(DbSequence sequence, long value)
        {
            _blocks.remove(sequence.getRowId());
            updateSequenceValue(sequence, value);
        }

        @Override
        public synchronized void run()
        {
            _flushed = true;
            TableInfo tinfo = getTableInfo();
            _blocks.forEach((rowId, block) -> {
                if (block._current < block._last)
                {
                    SQLFragment sql = new SQLFragment().unsafeAppend("UPDATE ").append(tinfo)
                            .unsafeAppend(" SET Value = ? WHERE RowId = ? AND Value = ?")
                            .add(block._current).add(rowId).add(block._last);
                    if (new SqlExecutor(tinfo.getSchema()).execute(sql) != 1)
                        LOG.warn("DbSequence {} changed outside its reserved block; leaving values {}-{} unused", rowId, block._current + 1, block._last);
                }
            });
            _blocks.clear();
        }

        @Override
        public boolean equals(Object o)
        {
            return o instanceof ReclaimableBlocks;
        }

        @Override
        public int hashCode()
        {
            return ReclaimableBlocks.class.hashCode();
        }
    }


    // .first value returned is 'current', call to next() should return current+1
    // .second value is reserved for use by caller
    // in other words, next() should return values [pair.first+1, pair.second] inclusive
    static Pair<Long,Long> reserve(DbSequence sequence, long count)
    {
        return reserve(sequence, count, null);
    }


    // Like reserve(sequence, count), but first raises the value to the minimum, if given
    static Pair<Long,Long> reserve(DbSequence sequence, long count, @Nullable Long minimum)
    {
        TableInfo tinfo = getTableInfo();

        SQLFragment sql = new SQLFragment();
        sql.unsafeAppend("UPDATE ").append(tinfo.getSelectName()).unsafeAppend(" SET Value = (");

        if (null != minimum)
            sql.unsafeAppend("GREATEST(");

        addValueSql(sql, tinfo, sequence);

        if (null != minimum)
            sql.unsafeAppend(", ?)").add(minimum);

        sql.unsafeAppend(") + ? WHERE RowId = ?");
        sql.add(count);
        sql.add(sequence.getRowId());

        // Reselect the current value
        tinfo.getSqlDialect().addReselect(sql, tinfo.getColumn("Value"), null);

        long last = sequence.useCurrentTransaction() ? executeAndReturnLongInTraction(tinfo, sql) : executeAndReturnLong(tinfo, sql, Level.WARN);
        long first = last - count;
        return new Pair<>(first,last);
    }


    private static void addValueSql(SQLFragment sql, TableInfo tinfo, DbSequence sequence)
    {
        // SELECT with FOR UPDATE locks the row to ensure a true atomic update
        SQLFragment selectForUpdate = new SQLFragment("SELECT Value FROM ").append(tinfo, "seq").unsafeAppend(" WHERE RowId = ? FOR UPDATE");
        selectForUpdate.add(sequence.getRowId());
        sql.unsafeAppend("(").append(selectForUpdate).unsafeAppend(")");
    }


    // Sets the sequence value to requested minimum, if it's currently less than this value
    static void ensureMinimum(DbSequence sequence, long minimum)
    {
        ReclaimableBlocks blocks = getReclaimableBlocks(sequence);
        if (null != blocks)
            blocks.ensureMinimum(sequence, minimum);
        else
            updateMinimum(sequence, minimum);
    }

    private static void updateMinimum(DbSequence sequence, long minimum)
    {
        TableInfo tinfo = getTableInfo();

        SQLFragment sql = new SQLFragment("UPDATE ").append(tinfo.getSelectName()).unsafeAppend(" SET Value = ? WHERE RowId = ? AND Value < ?");
        sql.add(minimum);
        sql.add(sequence.getRowId());
        sql.add(minimum);

        if (sequence.useCurrentTransaction())
            new SqlExecutor(tinfo.getSchema()).execute(sql);
        else
            execute(tinfo, sql);
    }

    // Explicitly sets the sequence value, only used at shutdown!
    static void setSequenceValue(DbSequence sequence, long value)
    {
        ReclaimableBlocks blocks = getReclaimableBlocks(sequence);
        if (null != blocks)
            blocks.setSequenceValue(sequence, value);
        else
            updateSequenceValue(sequence, value);
    }

    private static void updateSequenceValue(DbSequence sequence, long value)
    {
        TableInfo tinfo = getTableInfo();

        SQLFragment sql = new SQLFragment("UPDATE ").append(tinfo.getSelectName()).unsafeAppend(" SET Value = ? WHERE RowId = ?");
        sql.add(value);
        sql.add(sequence.getRowId());

        if (sequence.useCurrentTransaction())
            new SqlExecutor(tinfo.getSchema()).execute(sql);
        else
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

    private static @Nullable Long executeAndMaybeReturnLong(TableInfo tinfo, SQLFragment sql)
    {
        DbScope scope = tinfo.getSchema().getScope();

        try (Connection conn = scope.getPooledConnection())
        {
            return new SqlSelector(scope, conn, sql).getObject(Long.class);
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

    private static long executeAndReturnLongInTraction(TableInfo tinfo, SQLFragment sql)
    {
        return new SqlExecutor(tinfo.getSchema()).executeWithResults(sql, LONG_RETURNING_RESULTSET_HANDLER);
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

            assertEquals(duplicateValues.size() + " duplicate values were detected: " + duplicateValues, 0, duplicateValues.size());
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

            Container c = JunitUtil.getTestContainer();

            JunitUtil.createRaces(() -> DbSequenceManager.ensure(c, name, 0), threads, 1, 60);
        }

        @Test
        public void testShutdown()
        {
            Container c = JunitUtil.getTestContainer();

            long first = _sequenceBulk.next();
            shutdownListener.shutdownStarted();
            assertNotNull(_sequences.remove(c.getId() + "/" + NAME_BULK + "/" + 0));
            _sequenceBulk = (DbSequence.Preallocate)DbSequenceManager.getPreallocatingSequence(c, NAME_BULK);
            long after = _sequenceBulk.next();
            assertEquals(first+1, after);
        }

        private final List<String> _reclaimableNames = new ArrayList<>();

        private String reclaimableName()
        {
            String name = "org.labkey.api.data.DbSequence.TestReclaimable/" + GUID.makeGUID();
            _reclaimableNames.add(name);
            return name;
        }

        private static DbScope scope()
        {
            return CoreSchema.getInstance().getScope();
        }

        private static List<Long> draw(DbSequence seq, int count)
        {
            return Stream.generate(seq::next).limit(count).toList();
        }

        @Test
        public void testReclaimableRollbackReusesValues()
        {
            Container c = JunitUtil.getTestContainer();
            String name = reclaimableName();

            try (DbScope.Transaction ignored = scope().ensureTransaction())
            {
                assertEquals(List.of(1L, 2L, 3L), draw(DbSequenceManager.getReclaimable(c, name, 0), 3));
            }
            assertEquals(0, DbSequenceManager.getReclaimable(c, name, 0).current());

            try (DbScope.Transaction tx = scope().ensureTransaction())
            {
                assertEquals(List.of(1L, 2L, 3L), draw(DbSequenceManager.getReclaimable(c, name, 0), 3));
                tx.commit();
            }
            assertEquals(3, DbSequenceManager.getReclaimable(c, name, 0).current());
        }

        @Test
        public void testReclaimableHandlesShareOneTransaction()
        {
            Container c = JunitUtil.getTestContainer();
            String name = reclaimableName();

            try (DbScope.Transaction tx = scope().ensureTransaction())
            {
                DbSequence first = DbSequenceManager.getReclaimable(c, name, 0);
                DbSequence second = DbSequenceManager.getReclaimable(c, name, 0);
                List<Long> values = List.of(first.next(), second.next(), first.next(), second.next());
                assertEquals(List.of(1L, 2L, 3L, 4L), values);
                tx.commit();
            }
            assertEquals(4, DbSequenceManager.getReclaimable(c, name, 0).current());
        }

        @Test
        public void testReclaimableAcrossCommitAndKeepConnectionAndWithoutTransaction()
        {
            Container c = JunitUtil.getTestContainer();
            String name = reclaimableName();

            try (DbScope.Transaction tx = scope().ensureTransaction())
            {
                DbSequence seq = DbSequenceManager.getReclaimable(c, name, 0);
                assertEquals(List.of(1L, 2L), draw(seq, 2));
                tx.commitAndKeepConnection();
                assertEquals(3, seq.next());
            }
            // Values committed by commitAndKeepConnection() survive; the uncommitted tail is reclaimed
            assertEquals(2, DbSequenceManager.getReclaimable(c, name, 0).current());

            assertFalse(scope().isTransactionActive());
            DbSequence seq = DbSequenceManager.getReclaimable(c, name, 0);
            assertEquals(List.of(3L, 4L), draw(seq, 2));
            assertEquals(4, seq.current());
        }

        @Test
        public void testReclaimableConcurrentTransactionsStayContiguous() throws Throwable
        {
            Container c = JunitUtil.getTestContainer();
            String name = reclaimableName();
            final int perTransaction = 5;
            final Set<Long> committed = new ConcurrentHashSet<>();
            final Set<Long> duplicates = new ConcurrentHashSet<>();
            final AtomicInteger iteration = new AtomicInteger();

            JunitUtil.createRaces(() -> {
                boolean commit = iteration.getAndIncrement() % 3 != 0;
                try (DbScope.Transaction tx = scope().ensureTransaction())
                {
                    List<Long> values = draw(DbSequenceManager.getReclaimable(c, name, 0), perTransaction);
                    assertEquals("Values drawn in one transaction must be contiguous: " + values, values.getFirst() + perTransaction - 1, (long) values.getLast());
                    if (commit)
                    {
                        tx.commit();
                        values.stream().filter(v -> !committed.add(v)).forEach(duplicates::add);
                    }
                }
            }, 5, 12, 60);

            assertTrue("Duplicate committed values: " + duplicates, duplicates.isEmpty());
            long total = committed.size();
            assertEquals("Committed values must be exactly 1.." + total, LongStream.rangeClosed(1, total).boxed().collect(Collectors.toSet()), committed);
            assertEquals(total, DbSequenceManager.getReclaimable(c, name, 0).current());
        }

        // Reads the stored value on the transaction's connection, unlike current()
        private static long storedValue(DbSequence seq)
        {
            TableInfo tinfo = getTableInfo();
            SQLFragment sql = new SQLFragment().unsafeAppend("SELECT Value FROM ").append(tinfo).unsafeAppend(" WHERE RowId = ?").add(seq.getRowId());
            return new SqlSelector(tinfo.getSchema(), sql).getObject(Long.class);
        }

        @Test
        public void testReclaimableReservesBlockAndWritesBackTail()
        {
            Container c = JunitUtil.getTestContainer();
            String name = reclaimableName();

            try (DbScope.Transaction tx = scope().ensureTransaction())
            {
                DbSequence seq = DbSequenceManager.getReclaimable(c, name, 0);
                assertEquals(List.of(1L, 2L, 3L, 4L), draw(seq, 4));
                assertTrue("Expected a block reserved past the drawn values", storedValue(seq) > 4);
                assertEquals("current() returns the committed value", 0, seq.current());
                tx.commit();
            }

            DbSequence seq = DbSequenceManager.getReclaimable(c, name, 0);
            assertEquals(4, seq.current());
            assertEquals(5, seq.next());
        }

        @Test
        public void testReclaimableEnsureMinimumInTransaction()
        {
            Container c = JunitUtil.getTestContainer();
            String name = reclaimableName();

            try (DbScope.Transaction tx = scope().ensureTransaction())
            {
                DbSequence seq = DbSequenceManager.getReclaimable(c, name, 0);
                List<Long> values = new ArrayList<>(draw(seq, 4));
                seq.ensureMinimum(6);
                values.add(seq.next());
                seq.ensureMinimum(3);
                values.add(seq.next());
                seq.ensureMinimum(20);
                values.add(seq.next());
                assertEquals(List.of(1L, 2L, 3L, 4L, 7L, 8L, 21L), values);
                tx.commit();
            }
            assertEquals(21, DbSequenceManager.getReclaimable(c, name, 0).current());
        }

        @Test
        public void testReclaimableDrawsFromPrecommitTasks()
        {
            Container c = JunitUtil.getTestContainer();
            String name = reclaimableName();
            List<Long> beforeWriteBack = new ArrayList<>();
            List<Long> afterWriteBack = new ArrayList<>();

            try (DbScope.Transaction tx = scope().ensureTransaction())
            {
                DbSequence seq = DbSequenceManager.getReclaimable(c, name, 0);
                tx.addCommitTask(() -> beforeWriteBack.add(seq.next()), DbScope.CommitTaskOption.PRECOMMIT);
                assertEquals(List.of(1L, 2L, 3L, 4L), draw(seq, 4));
                tx.addCommitTask(() -> afterWriteBack.add(seq.next()), DbScope.CommitTaskOption.PRECOMMIT);
                tx.commit();
            }

            assertEquals(List.of(5L), beforeWriteBack);
            assertEquals(List.of(6L), afterWriteBack);
            assertEquals(6, DbSequenceManager.getReclaimable(c, name, 0).current());
        }

        private static List<Long> drawAtLeast(DbSequence seq)
        {
            return List.of(seq.nextAtLeast(10), seq.nextAtLeast(5), seq.next(), seq.nextAtLeast(500));
        }

        @Test
        public void testNextAtLeast()
        {
            Container c = JunitUtil.getTestContainer();
            List<Long> expected = List.of(11L, 12L, 13L, 501L);

            assertEquals(expected, drawAtLeast(_sequence));
            assertEquals(expected, drawAtLeast(_sequenceBulk));
            assertEquals(expected, drawAtLeast(DbSequenceManager.getReclaimable(c, reclaimableName(), 0, false)));

            String name = reclaimableName();
            try (DbScope.Transaction tx = scope().ensureTransaction())
            {
                assertEquals(expected, drawAtLeast(DbSequenceManager.getReclaimable(c, name, 0, false)));
                tx.commit();
            }
            assertEquals(501, DbSequenceManager.getReclaimable(c, name, 0).current());
        }

        // On a sequence at 3: the second transaction's stale nextAtLeast(3) waits behind the first's nextAtLeast(10)
        private long drawBehindConcurrentTransaction(boolean commitFirst) throws Exception
        {
            Container c = JunitUtil.getTestContainer();
            String name = reclaimableName();
            assertEquals(List.of(1L, 2L, 3L), draw(DbSequenceManager.getReclaimable(c, name, 0), 3));

            try (ExecutorService executor = Executors.newSingleThreadExecutor())
            {
                Future<Long> second;
                try (DbScope.Transaction tx = scope().ensureTransaction())
                {
                    assertEquals(11, DbSequenceManager.getReclaimable(c, name, 0, false).nextAtLeast(10));

                    second = executor.submit(() -> {
                        try (DbScope.Transaction tx2 = scope().ensureTransaction())
                        {
                            long value = DbSequenceManager.getReclaimable(c, name, 0, false).nextAtLeast(3);
                            tx2.commit();
                            return value;
                        }
                    });
                    assertThrows("Second draw must wait for the first transaction's row lock", TimeoutException.class, () -> second.get(1, TimeUnit.SECONDS));

                    if (commitFirst)
                        tx.commit();
                }

                long value = second.get(30, TimeUnit.SECONDS);
                assertEquals(value, DbSequenceManager.getReclaimable(c, name, 0).current());
                return value;
            }
        }

        @Test
        public void testPreallocatingSequenceKeepsLargestBatchSize()
        {
            Container c = JunitUtil.getTestContainer();
            String name = "org.labkey.api.data.DbSequence.TestBatchSize/" + GUID.makeGUID();
            try
            {
                DbSequence unbatched = DbSequenceManager.getPreallocatingSequence(c, name, 0, 1);
                assertEquals(1, unbatched.next());
                assertEquals(1, storedValue(unbatched));

                DbSequence batched = DbSequenceManager.getPreallocatingSequence(c, name, 0, 10);
                assertSame("One instance per sequence", unbatched, batched);
                assertEquals(2, batched.next());
                assertEquals("A later, larger batch size must apply", 11, storedValue(batched));

                assertEquals(3, DbSequenceManager.getPreallocatingSequence(c, name, 0, 1).next());
                assertEquals("A smaller batch size must not shrink it", 11, storedValue(batched));
            }
            finally
            {
                DbSequenceManager.invalidatePreallocatingSequence(c, name, 0);
                DbSequenceManager.delete(c, name);
            }
        }

        @Test
        public void testLateLockDrawWaitsForConcurrentTransaction() throws Exception
        {
            assertEquals("Must draw past the committed 11, not reuse the stale minimum", 12, drawBehindConcurrentTransaction(true));
            assertEquals("A rolled-back draw must be reused", 4, drawBehindConcurrentTransaction(false));
        }

        @After
        public void cleanup()
        {
            Container c = JunitUtil.getTestContainer();
            DbSequenceManager.delete(c, NAME);
            _reclaimableNames.forEach(name -> DbSequenceManager.delete(c, name));
        }
    }
}
