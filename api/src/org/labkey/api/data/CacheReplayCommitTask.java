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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final Map<Object, Set<Op>> _opsByCache = new HashMap<>();
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

    void removeUsingFilter(@NotNull Object cache, @NotNull Object filter, @NotNull Runnable removal)
    {
        add(new Op(cache, Kind.FILTER, filter), removal);
    }

    void clear(@NotNull Object cache, @NotNull Runnable clear)
    {
        Set<Op> ops = _opsByCache.remove(cache);
        if (ops != null)
            ops.forEach(_operations::remove);
        _reloadCounts.remove(cache);
        add(new Op(cache, Kind.CLEAR, null), clear);
    }

    private boolean add(Op op, Runnable operation)
    {
        if (_operations.putIfAbsent(op, operation) != null)
            return false;
        _opsByCache.computeIfAbsent(op.cache(), c -> new HashSet<>()).add(op);
        return true;
    }

    private void drop(Op reload)
    {
        if (_operations.remove(reload) != null)
        {
            _opsByCache.get(reload.cache()).remove(reload);
            _reloadCounts.merge(reload.cache(), -1, Integer::sum);
        }
    }

    int size()
    {
        return _operations.size();
    }

    @Override
    public void run()
    {
        // Copy: a reload can register more operations when commitAndKeepConnection() leaves a transaction on the thread
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
        public void clearDropsEarlierOperationsOnTheCache()
        {
            CacheReplayCommitTask task = new CacheReplayCommitTask();
            task.reload("c1", "a", 1, op("reload a"));
            task.remove("c1", "b", op("remove b"));
            task.removeUsingFilter("c1", "filter", op("filter"));
            task.remove("c2", "b", op("remove c2 b"));
            task.clear("c1", op("clear"));
            task.reload("c1", "c", 1, op("reload c"));
            task.clear("c1", op("clear again"));
            task.reload("c1", "d", 1, op("reload d"));
            assertEquals(List.of("remove c2 b", "clear again", "reload d"), run(task));
        }

        @Test
        public void allInstancesAreEqual()
        {
            assertEquals(new CacheReplayCommitTask(), new CacheReplayCommitTask());
            assertEquals(new CacheReplayCommitTask().hashCode(), new CacheReplayCommitTask().hashCode());
        }
    }
}
