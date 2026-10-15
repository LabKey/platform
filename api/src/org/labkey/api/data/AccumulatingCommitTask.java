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
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.DbScope.CommitTaskOption;
import org.labkey.api.data.DbScope.Transaction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A commit task that collects values from every registration in a transaction and runs once over all of them, for work
 * that would otherwise be registered once per row (reindex these ids, recompute those runs). Tasks are equal when their
 * class and key are, so the transaction keeps the first one registered and later registrations add their values to it.
 * Always register through {@link #register}, which adds the values before a transaction-less scope runs the task.
 */
public abstract class AccumulatingCommitTask<K, V> implements Runnable
{
    private final @NotNull K _key;
    private final Set<V> _values = new LinkedHashSet<>();

    protected AccumulatingCommitTask(@NotNull K key)
    {
        _key = key;
    }

    /** Does the work once for every value registered under this task's key, in registration order */
    protected abstract void process(@NotNull K key, @NotNull Set<V> values);

    /**
     * Adds the values to the equal task already queued in the scope's current transaction, or queues this task if there
     * isn't one. Without a transaction, runs right away on just these values. Register a given class and key with the
     * same options every time.
     */
    public final void register(DbScope scope, Collection<? extends V> values, CommitTaskOption firstOption, CommitTaskOption... additionalOptions)
    {
        if (firstOption == CommitTaskOption.IMMEDIATE || Arrays.asList(additionalOptions).contains(CommitTaskOption.IMMEDIATE))
            throw new IllegalArgumentException("IMMEDIATE would run the task before any later registration adds its values");

        _values.addAll(values);
        AccumulatingCommitTask<K, V> queued = scope.addCommitTask(this, firstOption, additionalOptions);
        if (queued != this)
            queued._values.addAll(values);
    }

    @Override
    public final void run()
    {
        process(_key, Collections.unmodifiableSet(_values));
    }

    @Override
    public final boolean equals(Object o)
    {
        return o != null && o.getClass() == getClass() && _key.equals(((AccumulatingCommitTask<?, ?>) o)._key);
    }

    @Override
    public final int hashCode()
    {
        return 31 * getClass().hashCode() + _key.hashCode();
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + "[" + _key + ", " + _values.size() + " values]";
    }

    public static class TestCase extends Assert
    {
        private static final List<String> RUNS = Collections.synchronizedList(new ArrayList<>());

        private static class RecordingTask extends AccumulatingCommitTask<String, Integer>
        {
            RecordingTask(String key)
            {
                super(key);
            }

            @Override
            protected void process(@NotNull String key, @NotNull Set<Integer> values)
            {
                RUNS.add(key + values);
            }
        }

        private static DbScope scope()
        {
            return CoreSchema.getInstance().getScope();
        }

        @Test
        public void runsOnceWithEveryRegisteredValue()
        {
            RUNS.clear();
            try (Transaction tx = scope().ensureTransaction())
            {
                new RecordingTask("a").register(scope(), List.of(1, 2), CommitTaskOption.POSTCOMMIT);
                new RecordingTask("a").register(scope(), List.of(2, 3), CommitTaskOption.POSTCOMMIT);
                new RecordingTask("b").register(scope(), List.of(4), CommitTaskOption.POSTCOMMIT);
                assertTrue(RUNS.isEmpty());
                tx.commit();
            }
            assertEquals(List.of("a[1, 2, 3]", "b[4]"), RUNS);
        }

        @Test
        public void runsImmediatelyWithoutATransaction()
        {
            RUNS.clear();
            assertFalse(scope().isTransactionActive());
            new RecordingTask("a").register(scope(), List.of(1), CommitTaskOption.POSTCOMMIT);
            new RecordingTask("a").register(scope(), List.of(2), CommitTaskOption.POSTCOMMIT);
            assertEquals(List.of("a[1]", "a[2]"), RUNS);
        }

        @Test
        public void skippedOnRollback()
        {
            RUNS.clear();
            try (Transaction ignored = scope().ensureTransaction())
            {
                new RecordingTask("a").register(scope(), List.of(1), CommitTaskOption.POSTCOMMIT);
            }
            assertTrue(RUNS.isEmpty());
        }

        @Test(expected = IllegalArgumentException.class)
        public void rejectsImmediate()
        {
            new RecordingTask("a").register(scope(), List.of(1), CommitTaskOption.POSTCOMMIT, CommitTaskOption.IMMEDIATE);
        }

        @Test
        public void equalityIgnoresValues()
        {
            AccumulatingCommitTask<String, Integer> task = new RecordingTask("a");
            task._values.add(1);
            assertEquals(new RecordingTask("a"), task);
            assertEquals(new RecordingTask("a").hashCode(), task.hashCode());
            assertNotEquals(new RecordingTask("b"), task);
        }
    }
}
