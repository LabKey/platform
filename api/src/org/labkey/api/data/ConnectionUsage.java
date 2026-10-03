/*
 * Copyright (c) 2026 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Counts connection pool borrows and hold time within a window opened by {@link #mark()}. Usage is keyed on
 * {@link DbScope#getEffectiveThread()}, so async threads sharing a request's connections are charged to that request.
 * Off by default unless assertions are enabled; admins can toggle it from the profiler settings until restart.
 * Turning it off starts a new generation, which discards the per-action totals gathered so far.
 */
public class ConnectionUsage
{
    private static final Map<Thread, Usage> USAGE = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Mark DISABLED = new Mark();

    private static volatile boolean _enabled = assertionsEnabled();
    private static volatile int _generation = 0;

    /** acquireNanos is the whole borrow: poolNanos in getConnection(), setupNanos for per-connection setup, and the remainder building the wrapper */
    public record Snapshot(long borrows, long acquireNanos, long poolNanos, long setupNanos, long heldNanos, long wallNanos, int maxConcurrent, long unreturned, int generation)
    {
        /** False once tracking has been turned off since the window opened */
        public boolean isCurrent()
        {
            return generation == _generation;
        }
    }

    @SuppressWarnings({"AssertWithSideEffects", "ConstantValue"})
    private static boolean assertionsEnabled()
    {
        boolean enabled = false;
        assert enabled = true;
        return enabled;
    }

    public static boolean isEnabled()
    {
        return _enabled;
    }

    public static synchronized void setEnabled(boolean enabled)
    {
        if (_enabled && !enabled)
            _generation++;
        _enabled = enabled;
    }

    public static int getGeneration()
    {
        return _generation;
    }

    /** Opaque token returned by {@link #mark()} */
    public static final class Mark
    {
        private final @Nullable Usage _usage;
        private final int _generation;
        // Counters at the mark; a borrow belongs to this window when its sequence number exceeds _borrows
        private final long _borrows;
        private final long _acquireNanos;
        private final long _poolNanos;
        private final long _setupNanos;
        // Only connections borrowed within the window, so one leaked earlier on this thread isn't charged here
        private int _active;
        private int _maxActive;
        private long _openStartSum;
        private long _heldClosedNanos;
        private long _wallStart;
        private long _wallClosedNanos;

        private Mark()
        {
            _usage = null;
            _generation = 0;
            _borrows = _acquireNanos = _poolNanos = _setupNanos = 0;
        }

        private Mark(@NotNull Usage usage)
        {
            _usage = usage;
            _generation = ConnectionUsage._generation;
            _borrows = usage._borrows;
            _acquireNanos = usage._acquireNanos;
            _poolNanos = usage._poolNanos;
            _setupNanos = usage._setupNanos;
        }

        private void borrow(long now)
        {
            if (_active++ == 0)
                _wallStart = now;
            _maxActive = Math.max(_maxActive, _active);
            _openStartSum += now;
        }

        private void release(long borrowedAt, long now)
        {
            _active--;
            _heldClosedNanos += now - borrowedAt;
            _openStartSum -= borrowedAt;
            if (_active == 0)
                _wallClosedNanos += now - _wallStart;
        }
    }

    /** Identifies one borrow so its return is credited only to the windows that saw it */
    record Borrow(@NotNull Usage usage, long sequence, long borrowedAt) {}

    // Sums of nanoTime() values may overflow; that's harmless because only differences are reported
    static final class Usage
    {
        private long _borrows;
        private long _acquireNanos;
        private long _poolNanos;
        private long _setupNanos;
        private final List<Mark> _marks = new ArrayList<>(2);

        private synchronized Borrow borrow(long acquireNanos, long poolNanos, long setupNanos, long now)
        {
            _borrows++;
            _acquireNanos += acquireNanos;
            _poolNanos += poolNanos;
            _setupNanos += setupNanos;
            for (Mark mark : _marks)
                mark.borrow(now);
            return new Borrow(this, _borrows, now);
        }

        private synchronized void release(Borrow borrow, long now)
        {
            for (Mark mark : _marks)
                if (borrow.sequence() > mark._borrows)
                    mark.release(borrow.borrowedAt(), now);
        }

        private synchronized Mark mark()
        {
            Mark mark = new Mark(this);
            _marks.add(mark);
            return mark;
        }

        private synchronized Snapshot measure(Mark mark)
        {
            long now = System.nanoTime();
            _marks.remove(mark);
            return new Snapshot(
                _borrows - mark._borrows,
                _acquireNanos - mark._acquireNanos,
                _poolNanos - mark._poolNanos,
                _setupNanos - mark._setupNanos,
                mark._heldClosedNanos + mark._active * now - mark._openStartSum,
                mark._wallClosedNanos + (mark._active > 0 ? now - mark._wallStart : 0),
                mark._maxActive,
                mark._active,
                mark._generation
            );
        }
    }

    /**
     * Opens a measurement window for the current effective thread. Windows may nest; each one counts only the
     * connections borrowed within it. Every mark must be passed to {@link #measure(Mark)}, typically in a finally
     * block, or it stays on the thread for the thread's lifetime.
     */
    public static @NotNull Mark mark()
    {
        if (!_enabled)
            return DISABLED;

        return USAGE.computeIfAbsent(DbScope.getEffectiveThread(), _ -> new Usage()).mark();
    }

    /**
     * Closes the window; unreturned counts connections borrowed within it and still held.
     * @return null if tracking was off when the window opened
     */
    public static @Nullable Snapshot measure(@NotNull Mark mark)
    {
        return null == mark._usage ? null : mark._usage.measure(mark);
    }

    /** @return the Borrow to credit when this connection is returned, or null if the thread has never been marked */
    static @Nullable Borrow recordBorrow(long acquireNanos, long poolNanos, long setupNanos, long borrowedAt)
    {
        Usage usage = USAGE.get(DbScope.getEffectiveThread());
        return null == usage ? null : usage.borrow(acquireNanos, poolNanos, setupNanos, borrowedAt);
    }

    static void recordReturn(@Nullable Borrow borrow)
    {
        if (null != borrow)
            borrow.usage().release(borrow, System.nanoTime());
    }

    public static class TestCase extends Assert
    {
        private boolean _wasEnabled;

        // Bypasses setEnabled() so running these tests doesn't discard the server's per-action totals
        @Before
        public void enable()
        {
            _wasEnabled = isEnabled();
            _enabled = true;
        }

        @After
        public void restore()
        {
            _enabled = _wasEnabled;
        }

        private static Snapshot measureOnNewThread(ThrowingRunnable block) throws Exception
        {
            AtomicReference<Snapshot> result = new AtomicReference<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread thread = new Thread(() -> {
                Mark mark = mark();
                try
                {
                    block.run();
                }
                catch (Throwable t)
                {
                    failure.set(t);
                }
                result.set(measure(mark));
            }, "ConnectionUsage test");
            thread.start();
            thread.join();
            if (null != failure.get())
                throw new AssertionError("Failure on test thread", failure.get());
            return result.get();
        }

        private interface ThrowingRunnable
        {
            void run() throws Exception;
        }

        @Test
        public void testConcurrentPooledBorrows() throws Exception
        {
            DbScope scope = DbScope.getLabKeyScope();
            Snapshot snapshot = measureOnNewThread(() -> {
                try (Connection ignored1 = scope.getPooledConnection();
                     Connection ignored2 = scope.getPooledConnection();
                     Connection ignored3 = scope.getPooledConnection())
                {
                    Thread.sleep(5);
                }
            });
            assertTrue(snapshot.isCurrent());
            assertEquals(3, snapshot.borrows());
            assertEquals(3, snapshot.maxConcurrent());
            assertTrue("Acquire phases can't exceed the whole", snapshot.poolNanos() + snapshot.setupNanos() <= snapshot.acquireNanos());
            assertEquals(0, snapshot.unreturned());
            assertTrue("Summed hold time should exceed the union", snapshot.heldNanos() > snapshot.wallNanos());
            assertTrue(snapshot.wallNanos() >= 5_000_000);
        }

        @Test
        public void testThreadConnectionRefCountIsOneBorrow() throws Exception
        {
            DbScope scope = DbScope.getLabKeyScope();
            Snapshot snapshot = measureOnNewThread(() -> {
                try (Connection outer = scope.getConnection();
                     Connection inner = scope.getConnection())
                {
                    assertSame(outer, inner);
                }
            });
            assertEquals(1, snapshot.borrows());
            assertEquals(1, snapshot.maxConcurrent());
            assertEquals(0, snapshot.unreturned());
        }

        @Test
        public void testUnreturned() throws Exception
        {
            DbScope scope = DbScope.getLabKeyScope();
            AtomicReference<Connection> held = new AtomicReference<>();
            Snapshot snapshot = measureOnNewThread(() -> held.set(scope.getPooledConnection()));
            held.get().close();
            assertEquals(1, snapshot.borrows());
            assertEquals(1, snapshot.unreturned());
        }

        @Test
        public void testSharedThreadChargesOwner() throws Exception
        {
            DbScope scope = DbScope.getLabKeyScope();
            Snapshot snapshot = measureOnNewThread(() -> {
                Thread piggyback = new Thread(() -> {
                    try (Connection ignored = scope.getPooledConnection())
                    {
                    }
                    catch (Exception e)
                    {
                        throw new RuntimeException(e);
                    }
                });
                try (var ignored = DbScope.shareConnections(Thread.currentThread(), piggyback))
                {
                    piggyback.start();
                    piggyback.join();
                }
            });
            assertEquals(1, snapshot.borrows());
            assertEquals(0, snapshot.unreturned());
        }

        @Test
        public void testNestedMarks() throws Exception
        {
            DbScope scope = DbScope.getLabKeyScope();
            AtomicReference<Snapshot> inner = new AtomicReference<>();
            Snapshot outer = measureOnNewThread(() -> {
                try (Connection ignored = scope.getPooledConnection())
                {
                    Mark mark = mark();
                    try (Connection ignored2 = scope.getPooledConnection())
                    {
                    }
                    inner.set(measure(mark));
                }
            });
            assertEquals(2, outer.borrows());
            assertEquals(2, outer.maxConcurrent());
            assertEquals(1, inner.get().borrows());
            assertEquals("Inner window shouldn't count the outer connection", 1, inner.get().maxConcurrent());
            assertTrue(outer.wallNanos() >= inner.get().wallNanos());
        }

        @Test
        public void testEarlierLeakNotCharged() throws Exception
        {
            DbScope scope = DbScope.getLabKeyScope();
            AtomicReference<Snapshot> leaking = new AtomicReference<>();
            AtomicReference<Snapshot> later = new AtomicReference<>();
            measureOnNewThread(() -> {
                Mark first = mark();
                Connection leaked = scope.getPooledConnection();
                leaking.set(measure(first));

                Mark second = mark();
                Thread.sleep(5);
                try (Connection ignored = scope.getPooledConnection())
                {
                    leaked.close();
                    later.set(measure(second));
                }
            });
            Snapshot snapshot = later.get();
            assertEquals(1, leaking.get().unreturned());
            assertEquals(1, snapshot.borrows());
            assertEquals(1, snapshot.maxConcurrent());
            assertEquals("Returning the earlier leak shouldn't mask this window's", 1, snapshot.unreturned());
            assertTrue("Window shouldn't be charged for the earlier leak's hold time", snapshot.wallNanos() < 5_000_000);
        }

        @Test
        public void testDisabled() throws Exception
        {
            _enabled = false;
            DbScope scope = DbScope.getLabKeyScope();
            Snapshot snapshot = measureOnNewThread(() -> {
                try (Connection ignored = scope.getPooledConnection())
                {
                }
            });
            assertNull(snapshot);
        }
    }
}
