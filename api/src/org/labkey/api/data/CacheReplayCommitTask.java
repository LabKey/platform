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
import org.labkey.api.data.DbScope.Transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Replays a transaction's DatabaseCache removals, clears, and loads into the shared caches after commit, in the order
 * they first happened. One per transaction, so a transaction touching many caches queues one commit task, not one per
 * cache and key, and so an operation that a later removal or clear would undo can be dropped before it runs.
 */
public final class CacheReplayCommitTask implements Runnable
{
    private enum Kind { RELOAD, REMOVE, FILTER, CLEAR }

    private record Op(@NotNull Object cache, @NotNull Kind kind, @Nullable Object key) {}

    // Insertion order is replay order; a duplicate keeps the first one's position
    private final Map<Op, Runnable> _operations = new LinkedHashMap<>();
    private final Map<Object, Integer> _reloadCounts = new HashMap<>();

    /** The replay task already queued in the transaction, or a newly queued one */
    static CacheReplayCommitTask get(Transaction transaction)
    {
        return transaction.addCommitTask(new CacheReplayCommitTask(), DbScope.CommitTaskOption.POSTCOMMIT);
    }

    /** Queues the reload unless the cache already has limit reloads queued */
    void reload(@NotNull Object cache, @NotNull Object key, int limit, @NotNull Runnable reload)
    {
        if (_reloadCounts.getOrDefault(cache, 0) < limit && add(new Op(cache, Kind.RELOAD, key), reload))
            _reloadCounts.merge(cache, 1, Integer::sum);
    }

    void remove(@NotNull Object cache, @NotNull Object key, @NotNull Runnable removal)
    {
        drop(new Op(cache, Kind.RELOAD, key));
        add(new Op(cache, Kind.REMOVE, key), removal);
    }

    @SuppressWarnings("unchecked")
    <K> void removeUsingFilter(@NotNull Object cache, @NotNull Predicate<K> filter, @NotNull Runnable removal)
    {
        List.copyOf(_operations.keySet()).stream()
            .filter(op -> op.kind() == Kind.RELOAD && op.cache().equals(cache) && filter.test((K)op.key()))
            .forEach(this::drop);
        add(new Op(cache, Kind.FILTER, filter), removal);
    }

    /**
     * Replaces the cache's other operations with one clear at the position of its earliest invalidation. Moving the
     * clear later would let a reload of another cache queued in between read this cache's stale entries.
     */
    void clear(@NotNull Object cache, @NotNull Runnable clear)
    {
        Op clearOp = new Op(cache, Kind.CLEAR, null);
        Map<Op, Runnable> replay = new LinkedHashMap<>();
        _operations.forEach((op, operation) -> {
            if (!op.cache().equals(cache))
                replay.put(op, operation);
            else if (op.kind() != Kind.RELOAD)
                replay.putIfAbsent(clearOp, op.kind() == Kind.CLEAR ? operation : clear);
        });
        replay.putIfAbsent(clearOp, clear);
        _operations.clear();
        _operations.putAll(replay);
        _reloadCounts.remove(cache);
    }

    private boolean add(Op op, Runnable operation)
    {
        return _operations.putIfAbsent(op, operation) == null;
    }

    private void drop(Op reload)
    {
        if (_operations.remove(reload) != null)
            _reloadCounts.merge(reload.cache(), -1, Integer::sum);
    }

    int size()
    {
        return _operations.size();
    }

    @Override
    public void run()
    {
        // Copy guards against concurrent modification only: when commitAndKeepConnection() runs on a transaction that
        // isn't the innermost, a reload registers operations here that never run, since clearCommitTasks() discards them
        List.copyOf(_operations.values()).forEach(Runnable::run);
    }

    @Override
    public boolean equals(Object o)
    {
        return o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode()
    {
        return getClass().hashCode();
    }

    // CommitTaskQueue.add() logs this eagerly on every duplicate, so keep it allocation-free
    @Override
    public String toString()
    {
        return "DatabaseCache replay";
    }

    public static class TestCase extends Assert
    {
        private final List<String> _log = new ArrayList<>();

        private Runnable op(String name)
        {
            return () -> _log.add(name);
        }

        private List<String> run(CacheReplayCommitTask task)
        {
            task.run();
            return _log;
        }

        @Test
        public void runsOperationsInFirstRegistrationOrder()
        {
            CacheReplayCommitTask task = new CacheReplayCommitTask();
            task.remove("c1", "a", op("remove a"));
            task.reload("c1", "b", 10, op("reload b"));
            task.clear("c2", op("clear c2"));
            task.remove("c1", "a", op("remove a again"));
            assertEquals(List.of("remove a", "reload b", "clear c2"), run(task));
        }

        @Test
        public void capsReloadsPerCache()
        {
            CacheReplayCommitTask task = new CacheReplayCommitTask();
            task.reload("c1", "a", 2, op("1a"));
            task.reload("c1", "a", 2, op("1a again")); // Duplicate doesn't count against the limit
            task.reload("c1", "b", 2, op("1b"));
            task.reload("c1", "c", 2, op("1c"));
            task.reload("c2", "a", 2, op("2a"));
            assertEquals(List.of("1a", "1b", "2a"), run(task));
        }

        @Test
        public void removalDropsEarlierReloadOfTheKey()
        {
            CacheReplayCommitTask task = new CacheReplayCommitTask();
            task.reload("c1", "a", 10, op("reload a"));
            task.reload("c1", "b", 10, op("reload b"));
            task.reload("c2", "a", 10, op("reload c2 a"));
            task.remove("c1", "a", op("remove a"));
            task.reload("c1", "a", 10, op("reload a after remove"));
            assertEquals(List.of("reload b", "reload c2 a", "remove a", "reload a after remove"), run(task));
        }

        @Test
        public void droppedReloadFreesItsSlot()
        {
            CacheReplayCommitTask task = new CacheReplayCommitTask();
            task.reload("c1", "a", 1, op("reload a"));
            task.remove("c1", "a", op("remove a"));
            task.reload("c1", "b", 1, op("reload b"));
            assertEquals(List.of("remove a", "reload b"), run(task));
        }

        @Test
        public void filterDropsEarlierMatchingReloads()
        {
            CacheReplayCommitTask task = new CacheReplayCommitTask();
            task.reload("c1", "a1", 2, op("reload a1"));
            task.reload("c1", "b1", 2, op("reload b1"));
            task.reload("c2", "a1", 2, op("reload c2 a1"));
            task.removeUsingFilter("c1", (String key) -> key.startsWith("a"), op("filter"));
            task.reload("c1", "a2", 2, op("reload a2")); // Freed slot
            assertEquals(List.of("reload b1", "reload c2 a1", "filter", "reload a2"), run(task));
        }

        @Test
        public void clearReplacesTheCachesOperationsAtItsEarliestInvalidation()
        {
            CacheReplayCommitTask task = new CacheReplayCommitTask();
            task.reload("c1", "a", 1, op("reload a"));
            task.remove("c1", "b", op("remove b"));
            task.removeUsingFilter("c1", (String key) -> false, op("filter"));
            task.remove("c2", "b", op("remove c2 b"));
            task.clear("c1", op("clear"));
            task.reload("c1", "c", 1, op("reload c"));
            task.clear("c1", op("clear again"));
            task.reload("c1", "d", 1, op("reload d"));
            assertEquals(List.of("clear", "remove c2 b", "reload d"), run(task));
        }

        @Test
        public void clearNeverRunsAfterAReloadQueuedBehindAnEarlierInvalidation()
        {
            CacheReplayCommitTask task = new CacheReplayCommitTask();
            task.clear("c1", op("clear c1"));
            task.reload("c2", "x", 10, op("reload c2 x"));
            task.clear("c1", op("clear c1 again"));
            task.remove("c3", "a", op("remove c3 a"));
            task.reload("c2", "y", 10, op("reload c2 y"));
            task.clear("c3", op("clear c3"));
            assertEquals(List.of("clear c1", "reload c2 x", "clear c3", "reload c2 y"), run(task));
        }

        @Test
        public void clearIsAppendedWhenTheCacheHasNoEarlierInvalidation()
        {
            CacheReplayCommitTask task = new CacheReplayCommitTask();
            task.reload("c1", "a", 1, op("reload a"));
            task.remove("c2", "b", op("remove c2 b"));
            task.clear("c1", op("clear"));
            assertEquals(List.of("remove c2 b", "clear"), run(task));
        }

        @Test
        public void allInstancesAreEqual()
        {
            assertEquals(new CacheReplayCommitTask(), new CacheReplayCommitTask());
            assertEquals(new CacheReplayCommitTask().hashCode(), new CacheReplayCommitTask().hashCode());
        }
    }
}
