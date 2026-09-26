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
import org.junit.Assert;
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
 */
public class ConnectionUsage
{
    private static final Map<Thread, Usage> USAGE = Collections.synchronizedMap(new WeakHashMap<>());

    public record Snapshot(long borrows, long acquireNanos, long heldNanos, long wallNanos, int maxConcurrent, long unreturned)
    {
        public static final Snapshot EMPTY = new Snapshot(0, 0, 0, 0, 0, 0);
    }

    /** Opaque token returned by {@link #mark()} */
    public static final class Mark
    {
        private final Usage _usage;
        private final long _borrows;
        private final long _acquireNanos;
        private final long _heldNanos;
        private final long _wallNanos;
        private final int _active;
        private int _maxActive;

        private Mark(Usage usage, long now)
        {
            _usage = usage;
            _borrows = usage._borrows;
            _acquireNanos = usage._acquireNanos;
            _heldNanos = usage.held(now);
            _wallNanos = usage.wall(now);
            _active = usage._active;
            _maxActive = usage._active;
        }
    }

    // Sums of nanoTime() values may overflow; that's harmless because only differences are reported
    static final class Usage
    {
        private long _borrows;
        private long _acquireNanos;
        private long _heldClosedNanos;
        private long _openStartSum;
        private long _wallClosedNanos;
        private long _wallStart;
        private int _active;
        private final List<Mark> _marks = new ArrayList<>(2);

        private long held(long now)
        {
            return _heldClosedNanos + _active * now - _openStartSum;
        }

        private long wall(long now)
        {
            return _wallClosedNanos + (_active > 0 ? now - _wallStart : 0);
        }

        private synchronized void borrow(long acquireNanos, long now)
        {
            _borrows++;
            _acquireNanos += acquireNanos;
            if (_active++ == 0)
                _wallStart = now;
            _openStartSum += now;
            for (Mark mark : _marks)
                mark._maxActive = Math.max(mark._maxActive, _active);
        }

        private synchronized void release(long borrowedAt, long now)
        {
            _active--;
            _heldClosedNanos += now - borrowedAt;
            _openStartSum -= borrowedAt;
            if (_active == 0)
                _wallClosedNanos += now - _wallStart;
        }

        private synchronized Mark mark()
        {
            Mark mark = new Mark(this, System.nanoTime());
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
                held(now) - mark._heldNanos,
                wall(now) - mark._wallNanos,
                mark._maxActive,
                Math.max(0, _active - mark._active)
            );
        }
    }

    /** Opens a measurement window for the current effective thread. Windows may nest. */
    public static @NotNull Mark mark()
    {
        return USAGE.computeIfAbsent(DbScope.getEffectiveThread(), _ -> new Usage()).mark();
    }

    /** Closes the window; unreturned counts connections borrowed since the mark and still held. */
    public static @NotNull Snapshot measure(@NotNull Mark mark)
    {
        return mark._usage.measure(mark);
    }

    /** @return the Usage to credit when this connection is returned, or null if the thread has never been marked */
    static @Nullable Usage recordBorrow(long acquireNanos, long borrowedAt)
    {
        Usage usage = USAGE.get(DbScope.getEffectiveThread());
        if (null != usage)
            usage.borrow(acquireNanos, borrowedAt);
        return usage;
    }

    static void recordReturn(@Nullable Usage usage, long borrowedAt)
    {
        if (null != usage)
            usage.release(borrowedAt, System.nanoTime());
    }

    public static class TestCase extends Assert
    {
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
            assertEquals(3, snapshot.borrows());
            assertEquals(3, snapshot.maxConcurrent());
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
            assertEquals(2, inner.get().maxConcurrent());
            assertTrue(outer.wallNanos() >= inner.get().wallNanos());
        }
    }
}
