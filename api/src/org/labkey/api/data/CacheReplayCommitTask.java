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

import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.DbScope.Transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Replays a transaction's DatabaseCache removals, clears, and loads into the shared caches after commit, in the order
 * they first happened. One per transaction, so a transaction touching many caches queues one commit task, not one per
 * cache and key.
 */
final class CacheReplayCommitTask implements Runnable
{
    // Equal operations dedupe, keeping the first one's position
    private final Set<Runnable> _operations = new LinkedHashSet<>();
    private final Map<Object, Integer> _reloadCounts = new HashMap<>();

    /** The replay task already queued in the transaction, or a newly queued one */
    static CacheReplayCommitTask get(Transaction transaction)
    {
        return transaction.addCommitTask(new CacheReplayCommitTask(), DbScope.CommitTaskOption.POSTCOMMIT);
    }

    void add(Runnable operation)
    {
        _operations.add(operation);
    }

    /** Queues the reload unless the cache already has limit reloads queued */
    void addReload(Object cache, int limit, Runnable reload)
    {
        if (_reloadCounts.getOrDefault(cache, 0) < limit && _operations.add(reload))
            _reloadCounts.merge(cache, 1, Integer::sum);
    }

    int size()
    {
        return _operations.size();
    }

    @Override
    public void run()
    {
        // Copy: a reload can register more operations when commitAndKeepConnection() leaves a transaction on the thread
        List.copyOf(_operations).forEach(Runnable::run);
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
        private record Op(String name, List<String> log) implements Runnable
        {
            @Override
            public void run()
            {
                log.add(name);
            }
        }

        @Test
        public void runsOperationsInFirstRegistrationOrder()
        {
            List<String> log = new ArrayList<>();
            CacheReplayCommitTask task = new CacheReplayCommitTask();
            task.add(new Op("remove a", log));
            task.addReload("cache", 10, new Op("reload a", log));
            task.add(new Op("clear", log));
            task.add(new Op("remove a", log));
            task.run();
            assertEquals(List.of("remove a", "reload a", "clear"), log);
        }

        @Test
        public void capsReloadsPerCache()
        {
            List<String> log = new ArrayList<>();
            CacheReplayCommitTask task = new CacheReplayCommitTask();
            task.addReload("cache1", 2, new Op("1a", log));
            task.addReload("cache1", 2, new Op("1a", log)); // Duplicate doesn't count against the limit
            task.addReload("cache1", 2, new Op("1b", log));
            task.addReload("cache1", 2, new Op("1c", log));
            task.addReload("cache2", 2, new Op("2a", log));
            task.run();
            assertEquals(List.of("1a", "1b", "2a"), log);
        }

        @Test
        public void allInstancesAreEqual()
        {
            assertEquals(new CacheReplayCommitTask(), new CacheReplayCommitTask());
            assertEquals(new CacheReplayCommitTask().hashCode(), new CacheReplayCommitTask().hashCode());
        }
    }
}
