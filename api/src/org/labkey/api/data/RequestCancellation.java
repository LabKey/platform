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
import java.util.Set;
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

    record Cancelled(Source source, long cancelledAt) {}

    private RequestCancellation()
    {
    }

    /**
     * Records that a request is being cancelled, so its exit can be timed by {@link #requestFinished}
     * @return false if this request was already cancelled
     */
    static boolean requestCancelled(@NotNull Thread thread, @Nullable TransactionFilter.RequestTracker tracker, @NotNull Source source)
    {
        // A request can be cancelled more than once, e.g. a timeout after a client disconnect; time its exit from the first
        if (tracker != null && !tracker.cancellation().compareAndSet(null, new Cancelled(source, System.currentTimeMillis())))
            return false;

        Long elapsedMs = null;
        String url = null;
        String user = null;
        if (tracker != null)
        {
            elapsedMs = System.currentTimeMillis() - tracker.startTime();
            try
            {
                url = tracker.getUrl();
                Principal principal = tracker.request().getUserPrincipal();
                user = principal == null ? "guest" : principal.getName();
            }
            catch (IllegalStateException ignored) {} // The request has already been recycled
        }

        LOG.info("request-cancel source={} thread={} elapsedMs={} user={} url={}", source, thread.getName(), elapsedMs, user, url);
        increment(source._metricName);
        return true;
    }

    /** @param source null when the interrupt didn't come through {@link #requestCancelled} */
    static void statementCancelled(@Nullable Source source, boolean statementLive)
    {
        LOG.info("request-cancel-statement source={} statementLive={}", source == null ? "interrupt" : source, statementLive);
        if (statementLive)
            increment("statementCancelled");
    }

    /** @return how the request on this thread was cancelled, or null if it hasn't been */
    public static @Nullable Source getSource(@NotNull Thread thread)
    {
        TransactionFilter.RequestTracker tracker = TransactionFilter.getRequestSummary(thread);
        Cancelled cancelled = tracker == null ? null : tracker.cancellation().get();
        return cancelled == null ? null : cancelled.source();
    }

    /**
     * Called as every request exits; logs how long a cancelled one took to stop
     * @param tracker the exiting request's
     */
    static void requestFinished(@NotNull Thread thread, @NotNull TransactionFilter.RequestTracker tracker)
    {
        Cancelled cancelled = tracker.cancellation().get();
        if (cancelled == null)
            return;

        long cancelToExitMs = System.currentTimeMillis() - cancelled.cancelledAt();
        LOG.info("request-cancel-exit source={} thread={} cancelToExitMs={}", cancelled.source(), thread.getName(), cancelToExitMs);

        Span span = GlobalTracer.get().activeSpan();
        if (span != null)
        {
            span.setTag("labkey.cancel.source", cancelled.source().toString());
            span.setTag("labkey.cancel.to_exit_ms", cancelToExitMs);
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
        cancelQueriesWithPoolConnection(cancelQueriesWithoutConnection(connectionsByScope, source, count), source, count);
    }

    /**
     * The first half of {@link #cancelQueries}, which never waits on the pool
     * @return the connections it couldn't signal, for {@link #cancelQueriesWithPoolConnection}
     */
    static Map<DbScope, Set<ConnectionWrapper>> cancelQueriesWithoutConnection(@NotNull Map<DbScope, Set<ConnectionWrapper>> connectionsByScope, @NotNull Source source, boolean count)
    {
        Map<DbScope, Set<ConnectionWrapper>> remaining = new HashMap<>();
        connectionsByScope.forEach((scope, connections) -> {
            if (connections.isEmpty())
                return;

            Set<ConnectionWrapper> signalled = Set.of();
            try
            {
                signalled = scope.getSqlDialect().cancelQueriesWithoutConnection(connections);
                backendsCancelled(scope, signalled, source, count);
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

    /** The fallback half of {@link #cancelQueries}, for sessions the driver's cancel request couldn't reach; it can wait on an exhausted pool */
    static void cancelQueriesWithPoolConnection(@NotNull Map<DbScope, Set<ConnectionWrapper>> connectionsByScope, @NotNull Source source, boolean count)
    {
        connectionsByScope.forEach((scope, connections) -> {
            try
            {
                List<ConnectionWrapper> allocated = connections.stream().filter(ConnectionWrapper::isAllocated).toList();
                if (!allocated.isEmpty() && scope.getSqlDialect().cancelQueries(scope, allocated, false))
                    backendsCancelled(scope, new HashSet<>(allocated), source, count);
            }
            catch (RuntimeException e)
            {
                LOG.error("Failed to cancel queries on {} for {}", scope.getDisplayName(), source, e);
            }
        });
    }

    private static void backendsCancelled(DbScope scope, Set<ConnectionWrapper> signalled, Source source, boolean count)
    {
        if (signalled.isEmpty())
            return;

        LOG.info("request-cancel-backends source={} scope={} spids={}", source, scope.getDisplayName(),
            signalled.stream().map(ConnectionWrapper::getSPID).collect(Collectors.toSet()));
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
                // Only the first cancel logs and counts
                assertFalse(requestCancelled(thread, tracker, Source.TIMEOUT));
                assertEquals(Source.CLIENT_DISCONNECT, getSource(thread));
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
            cancelQueries(Map.of(query.scope(), query.connections()), Source.TIMEOUT, true);
            assertStopped(query);
        }

        /** The usual path, which needs no pool connection */
        @Test
        public void testDriverCancel() throws Exception
        {
            BlockedQuery query = startBlockedQuery();
            Set<ConnectionWrapper> signalled = query.scope().getSqlDialect().cancelQueriesWithoutConnection(query.connections());
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
