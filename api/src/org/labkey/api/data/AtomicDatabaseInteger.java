/*
 * Copyright (c) 2010-2017 LabKey Corporation
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
import org.labkey.api.exceptions.OptimisticConflictException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;

import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: adam
 * Date: Oct 22, 2010
 * Time: 7:52:17 AM
 */
public class AtomicDatabaseInteger
{
    private final TableInfo _table;
    private final ColumnInfo _targetColumn;
    private final User _user;
    private final Container _container;
    private final Object _rowId;

    // Acts like an AtomicInteger, but uses the database for synchronization. This is convenient for scenarios where
    // multiple threads (eventually, even different servers) might attempt an update but only one should succeed.
    // Currently only implements compareAndSet(), but could add other methods from AtomicInteger.
    public AtomicDatabaseInteger(ColumnInfo targetColumn, User user, @Nullable Container container, Object rowId)
    {
        if (targetColumn.getJavaObjectClass() != Integer.class)
            throw new IllegalArgumentException("Target column must be type integer");

        _table = targetColumn.getParentTable();
        List<ColumnInfo> pks = _table.getPkColumns();

        if (pks.size() != 1)
            throw new IllegalArgumentException("Target table must have a single primary key column");

        _targetColumn = targetColumn;
        _user = user;
        _container = container;
        _rowId = rowId;
    }

    // TODO: Need to explicitly pass in a Connection (in both get() and compareAndSet()) that is NOT involved in the
    // current thread's transaction; this will protect against race conditions between transactions and issues with
    // rollbacks. This also allows us to mimic the behavior of database sequences (e.g., incrementing a sequence
    // inside a transaction always survives a rollback), which is one of the possible use cases.

    // Get the current value
    public int get()
    {
        SimpleFilter filter = (null != _container ? SimpleFilter.createContainerFilter(_container) : null);
        Integer currentValue = new TableSelector(_targetColumn, filter, null).getObject(_rowId, Integer.class);

        if (null == currentValue)
            throw new IllegalStateException("Can't find row " + _rowId);

        return currentValue;
    }

    // Atomically sets the value to the given update value if the current value == the expected value.
    public boolean compareAndSet(int expect, int update)
    {
        String targetColumnName = _targetColumn.getSelectName();
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts(targetColumnName), expect);

        if (null != _container)
            filter.addCondition(FieldKey.fromParts("Container"), _container);

        Map<String, Object> in = new HashMap<>();
        in.put(_targetColumn.getSelectName(), update);

        try
        {
            // Optimistic concurrency exceptions are possible... don't log them as errors
            Map<String, Object> out = Table.update(_user, _table, in, _rowId, filter, Level.ERROR);
            assert out.get(targetColumnName).equals(update);
            return true;
        }
        catch (OptimisticConflictException e)
        {
            return false;
        }
    }

    public int incrementAndGet()
    {
        for (;;)
        {
            int current = get();
            int next = current + 1;
            if (compareAndSet(current, next))
                return next;
        }
    }

    public int decrementAndGet()
    {
        for (;;)
        {
            int current = get();
            int next = current - 1;
            if (compareAndSet(current, next))
                return next;
        }
    }

    public int addAndGet(int delta)
    {
        for (;;)
        {
            int current = get();
            int next = current + delta;
            if (compareAndSet(current, next))
                return next;
        }
    }

    public static class TestCase extends Assert
    {
        private AtomicDatabaseInteger _adi;
        private Integer _rowId = null;

        @Before
        public void setup() throws SQLException
        {
            Container c = JunitUtil.getTestContainer();
            User user = TestContext.get().getUser();
            TableInfo table = TestSchema.getInstance().getTableInfoTestTable();

            Map<String, Object> map = new HashMap<>();
            map.put("Container", c);
            map.put("IntNotNull", 0);
            map.put("DateTimeNotNull", new Date());
            map.put("BitNotNull", true);

            map = Table.insert(user, table, map);

            _rowId = (Integer)map.get("RowId");
            _adi = new AtomicDatabaseInteger(table.getColumn("IntNotNull"), user, c, _rowId);
        }

        @Test
        public void testBasicOperations() throws SQLException
        {
            assertEquals(0, _adi.get());

            assertTrue(_adi.compareAndSet(0, 4));
            assertTrue(_adi.compareAndSet(4, 2));
            assertFalse(_adi.compareAndSet(0, 3));
            assertFalse(_adi.compareAndSet(3, 2));

            assertEquals(3, _adi.incrementAndGet());
            assertEquals(4, _adi.incrementAndGet());
            assertEquals(5, _adi.incrementAndGet());
            assertEquals(4, _adi.decrementAndGet());
            assertEquals(5, _adi.incrementAndGet());
            assertEquals(6, _adi.incrementAndGet());

            assertEquals(6, _adi.addAndGet(0));
            assertEquals(12, _adi.addAndGet(6));
            assertEquals(24, _adi.addAndGet(12));
            assertEquals(14, _adi.addAndGet(-10));
            assertEquals(-4, _adi.addAndGet(-18));
            assertEquals(0, _adi.addAndGet(4));
        }

        @Test
        public void testPerformance() throws SQLException
        {
            final int n = 1000;
            final long start = System.currentTimeMillis();

            long currentGetStart;
            long currentGetDuration;
            long longestGet = -1;

            for (int i = 0; i < n; i++)
            {
                currentGetStart = System.currentTimeMillis();
                _adi.incrementAndGet();
                currentGetDuration = System.currentTimeMillis() - currentGetStart;
                if (currentGetDuration > longestGet)
                    longestGet = currentGetDuration;
            }

            final long elapsed = System.currentTimeMillis() - start;

            double perSecond = n / (elapsed / 1000.0);

            assertTrue(String.format("Performance measured less than 75 increments per second: %d increments in %dms; slowest get %dms", n, elapsed, longestGet), perSecond > 75);   // A very low bar
        }

        @After
        public void cleanup() throws SQLException
        {
            if (null != _rowId)
            {
                TableInfo table = TestSchema.getInstance().getTableInfoTestTable();
                Table.delete(table, _rowId);
            }
        }
    }
}
