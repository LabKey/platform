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

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.Throttle;
import org.labkey.api.data.DbScope.CommitTaskOption;
import org.labkey.api.util.logging.LogHelper;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Watches one commit task queue of a transaction and warns when a single task class is queued {@link #THRESHOLD} or
 * more times, which usually means work registered per row that a value-equal task or an {@link AccumulatingCommitTask}
 * would do once. Each lambda or method reference site has its own class, so counting by class counts by call site.
 */
public class RepeatedCommitTaskMonitor
{
    private static final Logger LOG = LogHelper.getLogger(RepeatedCommitTaskMonitor.class, "Warnings about commit tasks queued repeatedly in one transaction");

    static final int THRESHOLD = 10;

    // Frames between the registering code and add(); see SqlExecutingSelector.PLUMBING_CLASSES for the same idea
    private static final Set<String> PLUMBING_CLASSES = Set.of(
            DbScope.class.getName(),
            RepeatedCommitTaskMonitor.class.getName(),
            AccumulatingCommitTask.class.getName());

    private static final int MAX_KEY_FRAMES = 10;

    // At most one warning per day per call site and queue, so an expected repeat doesn't flood the log
    private static final Throttle<RepeatedTaskWarning> WARNING_THROTTLE = new Throttle<>("Repeated commit task warnings", 1000, CacheManager.DAY,
            w -> {
                Tally tally = drainTally(w);
                LOG.warn("{} {} tasks of class {} were queued from one call site in one transaction and ran in {} ms ({} tasks in the queue). Consider a value-equal task or an AccumulatingCommitTask so the work runs once. {} transactions, max {} tasks, {} ms total, since this call site's previous warning.",
                        w.taskCount, w.option, w.taskClass, w.elapsedMs, w.queueSize, tally.transactions, tally.maxTasks, tally.totalMs, w.stackTrace);
            });

    // Repeats per throttle key since its last warning, so the once-a-day warning still reports how often the call site repeats
    private static final Map<RepeatedTaskWarning, Tally> TALLIES = new ConcurrentHashMap<>();

    private final Map<Class<?>, Integer> _counts = new HashMap<>();
    // Registration stack captured when a class reaches the threshold, in the order classes reached it
    private final Map<Class<?>, Throwable> _repeated = new LinkedHashMap<>();
    private final Map<Class<?>, Long> _elapsedNanos = new HashMap<>();
    // Not reset by clear(), so tests can read a transaction's warnings after it closes
    private int _warningCount = 0;

    /** Call only for a task that was actually queued, not one dropped as equal to a queued task */
    void added(Runnable task)
    {
        if (_counts.merge(task.getClass(), 1, Integer::sum) == THRESHOLD)
            _repeated.put(task.getClass(), new Throwable("Stack trace for repeated commit task registration"));
    }

    void run(Runnable task)
    {
        if (!_repeated.containsKey(task.getClass()))
        {
            task.run();
            return;
        }

        long start = System.nanoTime();
        try
        {
            task.run();
        }
        finally
        {
            _elapsedNanos.merge(task.getClass(), System.nanoTime() - start, Long::sum);
        }
    }

    void warnOfRepeats(CommitTaskOption option, int queueSize)
    {
        _repeated.forEach((taskClass, stackTrace) -> {
            RepeatedTaskWarning warning = new RepeatedTaskWarning(getStackKey(stackTrace), option, taskClass.getName(),
                    _counts.get(taskClass), queueSize, TimeUnit.NANOSECONDS.toMillis(_elapsedNanos.getOrDefault(taskClass, 0L)), stackTrace);
            recordTally(warning);
            WARNING_THROTTLE.execute(warning);
            _warningCount++;
        });
    }

    void clear()
    {
        _counts.clear();
        _repeated.clear();
        _elapsedNanos.clear();
    }

    /** The stack through the first frame outside DbScope's commit task plumbing: the code that registered the task */
    private static String getStackKey(Throwable t)
    {
        StackTraceElement[] frames = t.getStackTrace();
        int lastFrame = 0;

        while (lastFrame < frames.length - 1 && lastFrame < MAX_KEY_FRAMES - 1 && isPlumbingStackFrame(frames[lastFrame]))
            lastFrame++;

        return Arrays.stream(frames).limit(lastFrame + 1L).map(StackTraceElement::toString).collect(Collectors.joining("\n"));
    }

    private static boolean isPlumbingStackFrame(StackTraceElement frame)
    {
        String className = frame.getClassName();
        int nested = className.indexOf('$'); // CommitTaskOption constants and TransactionImpl are nested in DbScope

        return PLUMBING_CLASSES.contains(nested < 0 ? className : className.substring(0, nested));
    }

    private static void recordTally(RepeatedTaskWarning w)
    {
        TALLIES.compute(w, (k, t) -> t == null ? Tally.of(w) : t.add(w));
    }

    private static Tally drainTally(RepeatedTaskWarning w)
    {
        Tally tally = TALLIES.remove(w);
        return tally != null ? tally : Tally.of(w);
    }

    private record Tally(int transactions, int maxTasks, long totalMs)
    {
        static Tally of(RepeatedTaskWarning w)
        {
            return new Tally(1, w.taskCount, w.elapsedMs);
        }

        Tally add(RepeatedTaskWarning w)
        {
            return new Tally(transactions + 1, Math.max(maxTasks, w.taskCount), totalMs + w.elapsedMs);
        }
    }

    // Compares only on call site and queue, so the throttle and tally group on those rather than on every field
    private record RepeatedTaskWarning(String stackKey, CommitTaskOption option, String taskClass, int taskCount,
                                       int queueSize, long elapsedMs, Throwable stackTrace)
    {
        @Override
        public boolean equals(Object o)
        {
            return o instanceof RepeatedTaskWarning w && stackKey.equals(w.stackKey) && option == w.option;
        }

        @Override
        public int hashCode()
        {
            return 31 * stackKey.hashCode() + option.hashCode();
        }
    }

    public static class TestCase extends Assert
    {
        private static Runnable task(int i)
        {
            return () -> assertTrue(i >= 0); // Capturing, so every call returns a new instance of one class
        }

        @Test
        public void flagsAClassAtTheThreshold()
        {
            RepeatedCommitTaskMonitor monitor = new RepeatedCommitTaskMonitor();
            for (int i = 0; i < THRESHOLD - 1; i++)
                monitor.added(task(i));
            assertTrue(monitor._repeated.isEmpty());

            monitor.added(task(THRESHOLD));
            assertEquals(Set.of(task(0).getClass()), monitor._repeated.keySet());
        }

        @Test
        public void countsEachClassSeparately()
        {
            RepeatedCommitTaskMonitor monitor = new RepeatedCommitTaskMonitor();
            for (int i = 0; i < THRESHOLD - 1; i++)
            {
                monitor.added(task(i));
                monitor.added(() -> {});
            }
            assertTrue(monitor._repeated.isEmpty());
        }

        @Test
        public void timesOnlyFlaggedClasses()
        {
            RepeatedCommitTaskMonitor monitor = new RepeatedCommitTaskMonitor();
            Runnable other = () -> {};
            for (int i = 0; i < THRESHOLD; i++)
                monitor.added(task(i));
            monitor.added(other);

            monitor.run(task(0));
            monitor.run(other);
            assertEquals(Set.of(task(0).getClass()), monitor._elapsedNanos.keySet());
        }

        @Test
        public void clearResetsCounts()
        {
            RepeatedCommitTaskMonitor monitor = new RepeatedCommitTaskMonitor();
            for (int i = 0; i < THRESHOLD - 1; i++)
                monitor.added(task(i));
            monitor.clear();
            monitor.added(task(0));
            assertTrue(monitor._repeated.isEmpty());
            assertEquals(1, monitor._counts.get(task(0).getClass()).intValue());
        }

        private static Throwable createThrowable(List<String> classNames)
        {
            Throwable t = new Throwable();
            t.setStackTrace(classNames.stream()
                    .map(className -> new StackTraceElement(className, "method", "Source.java", 1))
                    .toArray(StackTraceElement[]::new));
            return t;
        }

        @Test
        public void keyStopsAtTheRegisteringFrame()
        {
            String key = getStackKey(createThrowable(List.of(
                    "org.labkey.api.data.DbScope$CommitTaskOption$2",
                    "org.labkey.api.data.DbScope$TransactionImpl",
                    "org.labkey.api.data.DbScope",
                    "org.labkey.list.model.ListManager",
                    "org.labkey.list.model.ListQueryUpdateService")));

            assertTrue(key.endsWith("org.labkey.list.model.ListManager.method(Source.java:1)"));
            assertEquals(4, key.split("\n").length);
        }

        private static RepeatedTaskWarning createWarning(String stackKey, CommitTaskOption option, int taskCount, long elapsedMs)
        {
            return new RepeatedTaskWarning(stackKey, option, "Task", taskCount, taskCount, elapsedMs, new Throwable());
        }

        // Tallies are static, so each test uses its own stack key
        @Test
        public void tallyCountsTransactionsMaxTasksAndTotalTime()
        {
            String key = "tallyCounts";
            recordTally(createWarning(key, CommitTaskOption.POSTCOMMIT, 20, 100));
            recordTally(createWarning(key, CommitTaskOption.POSTCOMMIT, 60, 300));

            assertEquals(new Tally(2, 60, 400), drainTally(createWarning(key, CommitTaskOption.POSTCOMMIT, 10, 0)));
            assertEquals(new Tally(1, 10, 5), drainTally(createWarning(key, CommitTaskOption.POSTCOMMIT, 10, 5)));
        }

        @Test
        public void tallyIsPerQueue()
        {
            String key = "tallyPerQueue";
            recordTally(createWarning(key, CommitTaskOption.PRECOMMIT, 20, 100));
            recordTally(createWarning(key, CommitTaskOption.POSTCOMMIT, 30, 200));

            assertEquals(new Tally(1, 20, 100), drainTally(createWarning(key, CommitTaskOption.PRECOMMIT, 20, 100)));
            assertEquals(new Tally(1, 30, 200), drainTally(createWarning(key, CommitTaskOption.POSTCOMMIT, 30, 200)));
        }
    }

    public static class IntegrationTestCase extends Assert
    {
        private record ValueTask(int i) implements Runnable
        {
            @Override
            public void run()
            {
            }
        }

        private static class NoOpAccumulatingTask extends AccumulatingCommitTask<String, Integer>
        {
            NoOpAccumulatingTask()
            {
                super("key");
            }

            @Override
            protected void process(@NotNull String key, @NotNull Set<Integer> values)
            {
            }
        }

        private static DbScope scope()
        {
            return CoreSchema.getInstance().getScope();
        }

        // Counts this transaction's monitors rather than the shared throttle, which other threads' commits also hit
        private static int warningsFor(boolean commit, int count, IntConsumer register)
        {
            List<RepeatedCommitTaskMonitor> monitors;
            try (DbScope.Transaction tx = scope().ensureTransaction())
            {
                DbScope.TransactionImpl impl = scope().getCurrentTransactionImpl();
                monitors = Stream.of(CommitTaskOption.PRECOMMIT, CommitTaskOption.POSTCOMMIT, CommitTaskOption.POSTROLLBACK)
                        .map(option -> option.getQueue(impl).getMonitor())
                        .toList();
                for (int i = 0; i < count; i++)
                    register.accept(i);
                if (commit)
                    tx.commit();
            }
            return monitors.stream().mapToInt(monitor -> monitor._warningCount).sum();
        }

        private static int tallyTransactions(RepeatedTaskWarning key)
        {
            Tally tally = TALLIES.get(key);
            return tally != null ? tally.transactions() : 0;
        }

        @Test
        public void warnsAtTheThreshold()
        {
            assertEquals(1, warningsFor(true, THRESHOLD, i -> scope().addCommitTask(TestCase.task(i), CommitTaskOption.POSTCOMMIT)));
        }

        @Test
        public void silentBelowTheThreshold()
        {
            assertEquals(0, warningsFor(true, THRESHOLD - 1, i -> scope().addCommitTask(TestCase.task(i), CommitTaskOption.POSTCOMMIT)));
        }

        @Test
        public void silentForDedupedTasks()
        {
            assertEquals(0, warningsFor(true, THRESHOLD, i -> scope().addCommitTask(new ValueTask(0), CommitTaskOption.POSTCOMMIT)));
        }

        @Test
        public void silentForAccumulatingTask()
        {
            assertEquals(0, warningsFor(true, THRESHOLD, i -> new NoOpAccumulatingTask().register(scope(), List.of(i), CommitTaskOption.POSTCOMMIT)));
        }

        @Test
        public void warnsForEachQueue()
        {
            assertEquals(2, warningsFor(true, THRESHOLD, i -> {
                scope().addCommitTask(TestCase.task(i), CommitTaskOption.PRECOMMIT);
                scope().addCommitTask(TestCase.task(i), CommitTaskOption.POSTCOMMIT);
            }));
        }

        @Test
        public void warnsOnlyForTheQueueThatRuns()
        {
            assertEquals(0, warningsFor(false, THRESHOLD, i -> scope().addCommitTask(TestCase.task(i), CommitTaskOption.POSTCOMMIT)));
            assertEquals(1, warningsFor(false, THRESHOLD, i -> scope().addCommitTask(TestCase.task(i), CommitTaskOption.POSTROLLBACK)));
        }

        @Test
        public void throttlesRepeatsFromOneCallSite()
        {
            // Both rounds fill their monitor from the same line, so they share a call site
            for (int round = 0; round < 2; round++)
            {
                RepeatedCommitTaskMonitor monitor = new RepeatedCommitTaskMonitor();
                for (int i = 0; i < THRESHOLD; i++)
                    monitor.added(TestCase.task(i));

                // A warning that logs drains its call site's tally; a throttled one adds to it
                Throwable stackTrace = monitor._repeated.values().iterator().next();
                RepeatedTaskWarning key = new RepeatedTaskWarning(getStackKey(stackTrace), CommitTaskOption.POSTCOMMIT, "Task", THRESHOLD, THRESHOLD, 0, stackTrace);
                int before = tallyTransactions(key);
                monitor.warnOfRepeats(CommitTaskOption.POSTCOMMIT, THRESHOLD);

                if (round == 1)
                    assertEquals(before + 1, tallyTransactions(key));
            }
        }
    }
}
