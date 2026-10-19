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

import io.opentracing.Span;
import io.opentracing.util.GlobalTracer;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.security.User;
import org.labkey.api.usageMetrics.SimpleMetricsService;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.logging.LogHelper;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.security.Principal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Stops in-flight requests and their database work, logging one structured line and counting one usage metric per
 * cancel so the effect can be measured in production.
 */
public final class RequestCancellation
{
    private static final Logger LOG = LogHelper.getLogger(RequestCancellation.class, "Cancellation of in-flight requests and their queries");
    // statementCancelled and backendCancelled overlap when a timeout cancels an async query, so they don't add up
    private static final String METRIC_AREA = "requestCancel";
    private static final long STUCK_AFTER_MS = 10_000;

    public enum Source
    {
        TIMEOUT("timeout", "timeout"),
        CLIENT_DISCONNECT("client_disconnect", "clientDisconnect"),
        SHUTDOWN("shutdown", "shutdown");

        private final String _logName;
        private final String _metricName;

        Source(String logName, String metricName)
        {
            _logName = logName;
            _metricName = metricName;
        }

        @Override
        public String toString()
        {
            return _logName;
        }
    }

    /** A request's cancel, filled in by whichever threads act on it and logged once, when the request exits */
    static final class Cancelled
    {
        private final Source _source;
        private final long _cancelledAt;
        private final Set<Integer> _spids = ConcurrentHashMap.newKeySet();
        private final AtomicBoolean _stuckReported = new AtomicBoolean();
        private volatile boolean _statementCancelled;

        private Cancelled(Source source, long cancelledAt)
        {
            _source = source;
            _cancelledAt = cancelledAt;
        }

        Source source()
        {
            return _source;
        }
    }

    private RequestCancellation()
    {
    }

    /**
     * Records that a request is being cancelled, for {@link #requestFinished} to log once it exits
     * @return false if this request was already cancelled
     */
    static boolean requestCancelled(@NotNull Thread thread, @Nullable TransactionFilter.RequestTracker tracker, @NotNull Source source)
    {
        // A request can be cancelled more than once, e.g. a timeout after a client disconnect; time its exit from the first
        if (tracker != null && !tracker.cancellation().compareAndSet(null, new Cancelled(source, System.currentTimeMillis())))
            return false;

        LOG.debug("request-cancel-begin source={} thread={}", source, thread.getName());
        increment(source._metricName);
        return true;
    }

    /**
     * A cancelled request that never exits never writes its {@link #requestFinished} line, so a later sweep reports it, once
     */
    static void cancelledRequestStillRunning(@NotNull Thread thread, @NotNull TransactionFilter.RequestTracker tracker)
    {
        Cancelled cancelled = tracker.cancellation().get();
        if (cancelled == null)
            return;

        long sinceCancelMs = System.currentTimeMillis() - cancelled._cancelledAt;
        if (sinceCancelMs >= STUCK_AFTER_MS && cancelled._stuckReported.compareAndSet(false, true))
            LOG.warn("request-cancel-stuck source={} thread={} sinceCancelMs={} url={}", cancelled._source, thread.getName(), sinceCancelMs, getUrl(tracker));
    }

    /**
     * Called on the request thread
     * @param source null when the interrupt didn't come through {@link #requestCancelled}
     */
    static void statementCancelled(@Nullable Source source, boolean statementLive)
    {
        LOG.log(source == null ? Level.INFO : Level.DEBUG, "request-cancel-statement source={} statementLive={}", source == null ? "interrupt" : source, statementLive);
        if (statementLive)
        {
            increment("statementCancelled");
            Cancelled cancelled = getCancelled(Thread.currentThread());
            if (cancelled != null)
                cancelled._statementCancelled = true;
        }
    }

    /** @return how the request on this thread was cancelled, or null if it hasn't been */
    public static @Nullable Source getSource(@NotNull Thread thread)
    {
        Cancelled cancelled = getCancelled(thread);
        return cancelled == null ? null : cancelled.source();
    }

    static @Nullable Cancelled getCancelled(@NotNull Thread thread)
    {
        TransactionFilter.RequestTracker tracker = TransactionFilter.getRequestSummary(thread);
        return tracker == null ? null : tracker.cancellation().get();
    }

    /**
     * Called as every request exits; writes a cancelled request's one INFO line
     * @param tracker the exiting request's
     */
    static void requestFinished(@NotNull Thread thread, @NotNull TransactionFilter.RequestTracker tracker)
    {
        Cancelled cancelled = tracker.cancellation().get();
        if (cancelled == null)
            return;

        long cancelToExitMs = System.currentTimeMillis() - cancelled._cancelledAt;
        String user = null;
        try
        {
            Principal principal = tracker.request().getUserPrincipal();
            user = principal == null ? "guest" : principal.getName();
        }
        catch (IllegalStateException ignored) {} // The request has already been recycled

        LOG.info("request-cancel source={} thread={} elapsedMs={} cancelToExitMs={} statementCancelled={} spidsSignalled={} user={} url={}",
            cancelled._source, thread.getName(), cancelled._cancelledAt - tracker.startTime(), cancelToExitMs,
            cancelled._statementCancelled, cancelled._spids, user, getUrl(tracker));

        Span span = GlobalTracer.get().activeSpan();
        if (span != null)
        {
            span.setTag("labkey.cancel.source", cancelled.source().toString());
            span.setTag("labkey.cancel.to_exit_ms", cancelToExitMs);
        }
    }

    private static @Nullable String getUrl(@NotNull TransactionFilter.RequestTracker tracker)
    {
        try
        {
            return tracker.getUrl();
        }
        catch (IllegalStateException e)
        {
            return null; // The request has already been recycled
        }
    }

    /**
     * Asks the database to stop whatever these connections are running, which makes the JDBC call that's waiting on
     * each one throw so its thread unwinds and releases the connection itself. Works even when the thread is blocked
     * in a socket read that an interrupt can't break.
     * @param count false when resending a cancel for a request already counted, so a slow exit doesn't inflate the metric
     */
    public static void cancelQueries(@NotNull Map<DbScope, Set<ConnectionWrapper>> connectionsByScope, @NotNull Source source, boolean count)
    {
        cancelQueriesViaNewConnection(cancelQueriesViaOwningConnection(connectionsByScope, source, null, count), source, null, count);
    }

    /**
     * The first half of {@link #cancelQueries}, which never waits on the pool
     * @param cancelled the request whose backends these are, which records them for its exit line
     * @return the connections it couldn't signal, for {@link #cancelQueriesViaNewConnection}
     */
    static Map<DbScope, Set<ConnectionWrapper>> cancelQueriesViaOwningConnection(@NotNull Map<DbScope, Set<ConnectionWrapper>> connectionsByScope, @NotNull Source source, @Nullable Cancelled cancelled, boolean count)
    {
        Map<DbScope, Set<ConnectionWrapper>> remaining = new HashMap<>();
        connectionsByScope.forEach((scope, connections) -> {
            if (connections.isEmpty())
                return;

            Set<ConnectionWrapper> signalled = Set.of();
            try
            {
                signalled = scope.getSqlDialect().cancelQueriesViaOwningConnection(connections);
                backendsCancelled(scope, signalled, source, cancelled, count);
            }
            catch (RuntimeException e)
            {
                LOG.error("Failed to cancel queries on {} for {}", scope.getDisplayName(), source, e);
            }

            Set<ConnectionWrapper> unsignalled = new HashSet<>(connections);
            unsignalled.removeAll(signalled);
            if (!unsignalled.isEmpty())
                remaining.put(scope, unsignalled);
        });
        return remaining;
    }

    /** The fallback half of {@link #cancelQueries}, for sessions the driver's cancel request couldn't reach; it opens a new connection */
    static void cancelQueriesViaNewConnection(@NotNull Map<DbScope, Set<ConnectionWrapper>> connectionsByScope, @NotNull Source source, @Nullable Cancelled cancelled, boolean count)
    {
        connectionsByScope.forEach((scope, connections) -> {
            try
            {
                List<ConnectionWrapper> allocated = connections.stream().filter(ConnectionWrapper::isAllocated).toList();
                if (!allocated.isEmpty() && scope.getSqlDialect().cancelQueries(scope, allocated, false))
                    backendsCancelled(scope, new HashSet<>(allocated), source, cancelled, count);
            }
            catch (RuntimeException e)
            {
                LOG.error("Failed to cancel queries on {} for {}", scope.getDisplayName(), source, e);
            }
        });
    }

    private static void backendsCancelled(DbScope scope, Set<ConnectionWrapper> signalled, Source source, @Nullable Cancelled cancelled, boolean count)
    {
        if (signalled.isEmpty())
            return;

        Set<Integer> spids = signalled.stream().map(ConnectionWrapper::getSPID).filter(Objects::nonNull).collect(Collectors.toSet());
        if (cancelled != null)
            cancelled._spids.addAll(spids);

        // A shutdown cancels no request, so it has no exit line to fold into
        LOG.log(source == Source.SHUTDOWN ? Level.INFO : Level.DEBUG, "request-cancel-backends source={} scope={} spids={}", source, scope.getDisplayName(), spids);
        if (count)
        {
            signalled.forEach(c -> increment("backendCancelled"));
            // Shutdown never goes through requestCancelled, so its source metric counts backends instead of requests
            if (source == Source.SHUTDOWN)
                signalled.forEach(c -> increment(source._metricName));
        }
    }

    private static void increment(String metricName)
    {
        try
        {
            SimpleMetricsService.get().increment(DefaultModule.CORE_MODULE_NAME, METRIC_AREA, metricName);
        }
        catch (RuntimeException e)
        {
            // Metrics must never block a cancel, e.g. during shutdown once modules are going away
            LOG.debug("Failed to count {} cancel", metricName, e);
        }
    }

    public static class TestCase extends Assert
    {
        /** Runs body on a new thread that carries this test's TestContext, rethrowing whatever it throws */
        public static void runOnRequestThread(Runnable body) throws Exception
        {
            HttpServletRequest testRequest = TestContext.get().getRequest();
            User user = TestContext.get().getUser();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread thread = new Thread(() -> {
                try
                {
                    TestContext.setTestContext(testRequest, user);
                    body.run();
                }
                catch (Throwable t)
                {
                    failure.set(t);
                }
            }, "RequestCancellation test request");

            thread.start();
            thread.join(60_000);
            assertFalse("Request thread didn't exit", thread.isAlive());

            switch (failure.get())
            {
                case null -> {}
                case Error e -> throw e;
                case Exception e -> throw e;
                default -> throw new RuntimeException(failure.get());
            }
        }

        /** Runs body inside a TransactionFilter request on the current thread, cancelled first if cancelledBy is set */
        public static void doRequest(@NotNull MockHttpServletRequest request, @Nullable Source cancelledBy, @NotNull Runnable body)
        {
            try
            {
                new TransactionFilter().doFilter(request, new MockHttpServletResponse(), (_, _) -> {
                    if (cancelledBy != null)
                    {
                        Thread thread = Thread.currentThread();
                        assertTrue(requestCancelled(thread, TransactionFilter.getRequestSummary(thread), cancelledBy));
                    }
                    body.run();
                });
            }
            catch (IOException | ServletException e)
            {
                throw new RuntimeException(e);
            }
        }

        public static void doRequest(@Nullable Source cancelledBy, @NotNull Runnable body)
        {
            doRequest(new MockHttpServletRequest("GET", "/test"), cancelledBy, body);
        }

        @Test
        public void testCancelledOnce() throws Exception
        {
            runOnRequestThread(() -> doRequest(null, () -> {
                Thread thread = Thread.currentThread();
                TransactionFilter.RequestTracker tracker = TransactionFilter.getRequestSummary(thread);
                assertNull(getSource(thread));

                assertTrue(requestCancelled(thread, tracker, Source.CLIENT_DISCONNECT));
                // Only the first cancel counts
                assertFalse(requestCancelled(thread, tracker, Source.TIMEOUT));
                assertEquals(Source.CLIENT_DISCONNECT, getSource(thread));
            }));
        }

        /** A live statement's cancel is recorded for the request's exit line */
        @Test
        public void testStatementRecorded() throws Exception
        {
            runOnRequestThread(() -> doRequest(Source.TIMEOUT, () -> {
                Cancelled cancelled = getCancelled(Thread.currentThread());
                assertNotNull(cancelled);

                statementCancelled(Source.TIMEOUT, false);
                assertFalse(cancelled._statementCancelled);
                statementCancelled(Source.TIMEOUT, true);
                assertTrue(cancelled._statementCancelled);
            }));
        }

        @Test
        public void testStuckReportedAfterGracePeriod() throws Exception
        {
            runOnRequestThread(() -> doRequest(null, () -> {
                Thread thread = Thread.currentThread();
                TransactionFilter.RequestTracker tracker = TransactionFilter.getRequestSummary(thread);

                Cancelled recent = new Cancelled(Source.TIMEOUT, System.currentTimeMillis());
                tracker.cancellation().set(recent);
                cancelledRequestStillRunning(thread, tracker);
                assertFalse("Reported within the grace period", recent._stuckReported.get());

                Cancelled old = new Cancelled(Source.TIMEOUT, System.currentTimeMillis() - STUCK_AFTER_MS);
                tracker.cancellation().set(old);
                cancelledRequestStillRunning(thread, tracker);
                assertTrue("Not reported after the grace period", old._stuckReported.get());
            }));
        }

        /** A sweep can cancel a request just as it finishes; that mustn't carry over to the thread's next request */
        @Test
        public void testFinishedRequest() throws Exception
        {
            runOnRequestThread(() -> {
                Thread thread = Thread.currentThread();
                AtomicReference<TransactionFilter.RequestTracker> finished = new AtomicReference<>();
                doRequest(null, () -> finished.set(TransactionFilter.getRequestSummary(thread)));

                requestCancelled(thread, finished.get(), Source.TIMEOUT);
                doRequest(null, () -> assertNull("Cancel applied to the next request", getSource(thread)));
            });
        }

        /** A request nested on the thread, e.g. an include, must not clear the outer request's cancel when it exits */
        @Test
        public void testNestedRequest() throws Exception
        {
            runOnRequestThread(() -> doRequest(Source.TIMEOUT, () -> {
                Thread thread = Thread.currentThread();
                doRequest(null, () -> {});
                assertEquals(Source.TIMEOUT, getSource(thread));
            }));
        }

        private record BlockedQuery(DbScope scope, Thread thread, AtomicReference<Throwable> failure, Set<ConnectionWrapper> connections, long start) {}

        /** Starts a query that runs outside AsyncQueryRequest, like selectDistinct, so it ignores an interrupt */
        private static BlockedQuery startBlockedQuery() throws InterruptedException
        {
            DbScope scope = DbScope.getLabKeyScope();

            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread queryThread = new Thread(() -> {
                try
                {
                    new SqlSelector(scope, "SELECT pg_sleep(30)").getObject(Object.class);
                }
                catch (Throwable t)
                {
                    failure.set(t);
                }
                finally
                {
                    DbScope.finishedWithThread();
                }
            }, "RequestCancellation test query");

            long start = System.currentTimeMillis();
            queryThread.start();

            Map<DbScope, Set<ConnectionWrapper>> connections = Map.of();
            while (connections.isEmpty() && System.currentTimeMillis() - start < 10_000)
            {
                //noinspection BusyWait
                Thread.sleep(100);
                connections = ConnectionWrapper.getConnectionsByScopeForEffectiveThread(queryThread);
            }
            assertFalse("Query thread never got a connection", connections.isEmpty());
            // Let the statement reach the server
            Thread.sleep(500);

            return new BlockedQuery(scope, queryThread, failure, connections.get(scope), start);
        }

        private static void assertStopped(BlockedQuery query) throws InterruptedException
        {
            query.thread().join(15_000);

            assertFalse("Query wasn't cancelled", query.thread().isAlive());
            assertNotNull("Query should have failed with a cancellation", query.failure().get());
            assertTrue("Cancel took too long", System.currentTimeMillis() - query.start() < 15_000);
            assertTrue("Connection wasn't released", ConnectionWrapper.getConnectionsByScopeForThread(query.thread()).isEmpty());
        }

        @Test
        public void testCancelBlockedQuery() throws Exception
        {
            BlockedQuery query = startBlockedQuery();
            Set<Integer> spids = query.connections().stream().map(ConnectionWrapper::getSPID).collect(Collectors.toSet());
            Cancelled cancelled = new Cancelled(Source.TIMEOUT, System.currentTimeMillis());
            Map<DbScope, Set<ConnectionWrapper>> unsignalled = cancelQueriesViaOwningConnection(Map.of(query.scope(), query.connections()), Source.TIMEOUT, cancelled, true);
            cancelQueriesViaNewConnection(unsignalled, Source.TIMEOUT, cancelled, true);
            assertStopped(query);
            assertEquals("Cancelled backends weren't recorded for the exit line", spids, cancelled._spids);
        }

        /** The usual path, which needs no pool connection */
        @Test
        public void testDriverCancel() throws Exception
        {
            BlockedQuery query = startBlockedQuery();
            Set<ConnectionWrapper> signalled = query.scope().getSqlDialect().cancelQueriesViaOwningConnection(query.connections());
            assertEquals("Driver cancel didn't signal every connection", query.connections(), signalled);
            assertStopped(query);
        }

        /** The fallback for sessions the driver's cancel request can't reach */
        @Test
        public void testCancelBackendFallback() throws Exception
        {
            BlockedQuery query = startBlockedQuery();
            assertTrue("pg_cancel_backend failed", query.scope().getSqlDialect().cancelQueries(query.scope(), query.connections(), false));
            assertStopped(query);
        }
    }
}
