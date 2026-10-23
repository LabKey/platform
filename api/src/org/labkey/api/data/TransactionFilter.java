/*
 * Copyright (c) 2004-2026 Fred Hutchinson Cancer Research Center
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

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.RequestCancellation.Outcome;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.util.AbortedRequestException;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.HeartBeat;
import org.labkey.api.util.QuietCloser;
import org.labkey.api.util.StartupListener;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ViewServlet;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.IOException;
import java.security.Principal;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.apache.commons.lang3.StringUtils.trimToEmpty;

public class TransactionFilter implements Filter
{
    private static final Logger _log = LogHelper.getLogger(ViewServlet.class, "DB connection status for HTTP requests");

    // NOTE: can't use ThreadLocal for this as you can't inspect the values for other threads
    private static final Map<Thread, RequestTracker> _pendingRequests = Collections.synchronizedMap(new WeakHashMap<>());

    public static final String READ_ONLY_ATTRIBUTE_NAME = "ReadOnlyHttpRequest";
    /** Read-only, but a legitimate export on a large instance can outlast the read-only timeout */
    public static final String EXPORT_ATTRIBUTE_NAME = "ExportHttpRequest";

    /**
     * @param tabId and requestId identify a request the client may cancel; read here, since another thread can't read a recycled request's headers
     * @param cancellation set once, by the first {@link RequestCancellation#requestCancelled}
     * @param cancelDeferrals open {@link #deferCancellation} scopes, changed under the tracker's lock
     * @param deferredCancel a cancel that arrived while deferred, applied when the last scope closes
     */
    public record RequestTracker(HttpServletRequest request, long startTime, @Nullable String tabId, @Nullable String requestId, AtomicReference<RequestCancellation.Cancelled> cancellation,
                                 AtomicInteger cancelDeferrals, AtomicReference<RequestCancellation.Source> deferredCancel)
    {
        private RequestTracker(HttpServletRequest request)
        {
            this(request, System.currentTimeMillis(), request.getHeader(RequestCancellation.TAB_ID_HEADER),
                request.getHeader(RequestCancellation.REQUEST_ID_HEADER), new AtomicReference<>(), new AtomicInteger(), new AtomicReference<>());
        }

        public String toLogString()
        {
            String url = getUrl();
            Principal user = request.getUserPrincipal();
            return url + " running for " + (System.currentTimeMillis() - startTime) + "ms by " + (user == null ? "guest" : user.getName());
        }

        @Override
        public @NotNull String toString()
        {
            throw new UnsupportedOperationException("Use toLogString() instead");
        }

        public @NotNull String getUrl()
        {
            return request.getRequestURI() + "?" + trimToEmpty(request.getQueryString());
        }
    }

    /* Similar to getRequestURL(), but can be used by a different thread (e.g. for thread dump) */
    public static RequestTracker getRequestSummary(Thread t)
    {
        return _pendingRequests.get(t);
    }

    static
    {
        ContextListener.addStartupListener(new StartupListener()
        {
            @Override
            public String getName()
            {
                return "HTTP request timeout";
            }

            @Override
            public void moduleStartupComplete(ServletContext servletContext)
            {
                Thread timeoutThread = new Thread(() -> {
                    while (!ContextListener.isShuttingDown())
                    {
                        long timeout = TimeUnit.SECONDS.toMillis(AppProps.getInstance().getReadOnlyHttpRequestTimeout());
                        if (timeout > 0 && !_pendingRequests.isEmpty())
                        {
                            Map<Thread, RequestTracker> pending;
                            synchronized (_pendingRequests)
                            {
                                // Copy the map and get out of the critical section
                                pending = new HashMap<>(_pendingRequests);
                            }

                            long cutoff = HeartBeat.currentTimeMillis() - timeout;
                            for (Map.Entry<Thread, RequestTracker> entry : pending.entrySet())
                            {
                                RequestTracker tracker = entry.getValue();
                                Thread thread = entry.getKey();

                                if (isTimedOut(thread, tracker, cutoff))
                                {
                                    _log.info("Timing out request for {} on thread {}", tracker.toLogString(), thread);
                                    cancelRequest(thread, tracker, RequestCancellation.Source.TIMEOUT);
                                }
                            }
                        }
                        try
                        {
                            //noinspection BusyWait
                            Thread.sleep(5_000);
                        }
                        catch (InterruptedException e)
                        {
                            return;
                        }
                    }
                }, "HTTP request timeout");
                timeoutThread.setDaemon(true);
                timeoutThread.start();
            }
        });
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws IOException, ServletException
    {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) resp;

        if (ContextListener.isShuttingDown())
        {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "The server is shutting down");
            return;
        }

        // Is it worth creating another filter for this? It is in the spirit of per-request resource tracking.
        FileUtil.startRequest();

        final Thread t = Thread.currentThread();
        final RequestTracker tracker = new RequestTracker(request);
        RequestTracker previousSummary = null;
        try
        {
            if ("GET".equalsIgnoreCase(request.getMethod()) ||
                    "HEAD".equalsIgnoreCase(request.getMethod()) ||
                    "OPTIONS".equalsIgnoreCase(request.getMethod()) ||
                    "PROPFIND".equalsIgnoreCase(request.getMethod()))
            {
                request.setAttribute(READ_ONLY_ATTRIBUTE_NAME, true);
            }
            previousSummary = _pendingRequests.put(t, tracker);
            chain.doFilter(req, resp);
        }
        finally
        {
            // Waits out a cancel in progress, whose interrupt and any ban would otherwise land on this thread's next request
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (tracker)
            {
                if (null == previousSummary)
                    _pendingRequests.remove(t);
                else
                    _pendingRequests.put(t, previousSummary);
            }
            DbScope.finishedWithThread();
            RequestCancellation.requestFinished(t, tracker);

            // Clear the interrupted flag before we wrap up
            if (Thread.interrupted())
            {
                _log.debug("HTTP request was interrupted during its execution");
            }
        }
        FileUtil.stopRequest();
    }

    /** @return true if this read-only, non-export request started before the cutoff and is still running on the thread */
    static boolean isTimedOut(Thread thread, RequestTracker tracker, long cutoff)
    {
        Object readOnly = Boolean.FALSE;
        Object export = Boolean.FALSE;
        try
        {
            readOnly = tracker.request.getAttribute(READ_ONLY_ATTRIBUTE_NAME);
            export = tracker.request.getAttribute(EXPORT_ATTRIBUTE_NAME);
        }
        catch (IllegalStateException ignored) {} // Ignore when the request has already been recycled

        // The thread may have moved on to another request since the sweep copied the pending requests
        return Boolean.TRUE.equals(readOnly) && !Boolean.TRUE.equals(export) && tracker.startTime < cutoff && _pendingRequests.get(thread) == tracker;
    }

    /**
     * Cancels a user's in-flight read-only requests from one browser tab, at the client's request
     * @param requestIds null to cancel every request from the tab, e.g. when it closes
     * @return what the cancel did to each request id
     */
    public static Map<String, Outcome> cancelRequests(int userId, @NotNull String tabId, @Nullable Collection<String> requestIds, @NotNull RequestCancellation.Source source)
    {
        Map<Thread, RequestTracker> pending;
        synchronized (_pendingRequests)
        {
            pending = new HashMap<>(_pendingRequests);
        }

        Map<String, Outcome> outcomes = new LinkedHashMap<>();
        pending.forEach((thread, tracker) -> {
            String requestId = tracker.requestId();
            if (requestId == null || !tabId.equals(tracker.tabId()) || (requestIds != null && !requestIds.contains(requestId)))
                return;

            Outcome outcome = getUncancellableOutcome(tracker, userId);
            if (outcome == null)
                outcome = cancelRequest(thread, tracker, source) ? Outcome.CANCELLED : Outcome.ALREADY_FINISHED;
            outcomes.put(requestId, outcome);
        });

        // Finished before the cancel arrived, or running on another server
        if (requestIds != null)
            requestIds.forEach(requestId -> outcomes.putIfAbsent(requestId, Outcome.NOT_FOUND));

        outcomes.forEach((requestId, outcome) -> {
            if (outcome != Outcome.CANCELLED)
                RequestCancellation.cancelIgnored(source, outcome, requestId, userId);
        });
        return outcomes;
    }

    /** @return why the user may not cancel this request, or null if they may */
    private static @Nullable Outcome getUncancellableOutcome(RequestTracker tracker, int userId)
    {
        try
        {
            HttpServletRequest request = tracker.request();
            if (!(request.getUserPrincipal() instanceof User user) || user.getUserId() != userId)
                return Outcome.WRONG_USER;
            // The client marks only reads cancellable, but nothing stops it sending any request's id
            if (!Boolean.TRUE.equals(request.getAttribute(READ_ONLY_ATTRIBUTE_NAME)))
                return Outcome.NOT_READ_ONLY;
            if (Boolean.TRUE.equals(request.getAttribute(EXPORT_ATTRIBUTE_NAME)))
                return Outcome.EXPORT;
            return null;
        }
        catch (IllegalStateException e)
        {
            return Outcome.ALREADY_FINISHED; // The request has already been recycled
        }
    }

    /**
     * Stops a request and its database work. An interrupt alone stops a query only on the AsyncQueryRequest path; a
     * thread blocked in a JDBC read elsewhere (e.g. selectDistinct) needs the database to cancel its statement.
     * @return false if the request had already finished
     */
    private static boolean cancelRequest(Thread thread, RequestTracker tracker, RequestCancellation.Source source)
    {
        return cancelRequest(thread, tracker, source, false);
    }

    /** @param deferred true when applying a cancel that {@link #deferCancellation} held off, so nothing was signalled yet */
    private static boolean cancelRequest(Thread thread, RequestTracker tracker, RequestCancellation.Source source, boolean deferred)
    {
        boolean first;
        RequestCancellation.Cancelled cancelled;
        Map<DbScope, Set<ConnectionWrapper>> unsignalled;

        // The cancelled query fails within milliseconds, so the request can finish mid-cancel; doFilter() can't release the thread while we hold this
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (tracker)
        {
            if (_pendingRequests.get(thread) != tracker)
                return false;

            first = RequestCancellation.requestCancelled(thread, tracker, source) || deferred;
            cancelled = tracker.cancellation().get();

            if (tracker.cancelDeferrals().get() > 0)
            {
                tracker.deferredCancel().compareAndSet(null, source);
                return true;
            }

            if (!first)
                RequestCancellation.cancelledRequestStillRunning(thread, tracker);

            // Resent on later sweeps, since a cancel that arrives between statements does nothing
            Map<DbScope, Set<ConnectionWrapper>> connections = ConnectionWrapper.getConnectionsByScopeForEffectiveThread(thread);
            unsignalled = RequestCancellation.cancelQueriesViaOwningConnection(connections, source, cancelled, first);

            // Never close the thread's connections from here: the pool would hand one out while the thread is still mid-call on it.
            // A deferred cancel lands between statements, where only the ban stops the request's next one.
            if (connections.isEmpty() || deferred)
                DbScope.banNewConnections(thread);
            PipelineJobService.get().killProcessesForThread(thread);
            thread.interrupt();
        }

        // Connect outside the lock, but signal inside it, so the request can't finish and hand this thread's connections to its next request first
        RequestCancellation.cancelQueriesViaNewConnection(unsignalled, source, cancelled, first, signal -> {
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (tracker)
            {
                if (_pendingRequests.get(thread) == tracker)
                    signal.run();
            }
        });
        return true;
    }

    /**
     * Holds off cancelling the current request until closed, then applies any cancel that arrived meanwhile. For work a
     * cancel can't stop early, only break, such as writing an audit row; an earlier cancel's ban doesn't apply inside it either.
     */
    public static @NotNull QuietCloser deferCancellation()
    {
        // An AsyncQueryRequest worker runs its query on the request's behalf
        Thread thread = DbScope.getEffectiveThread();
        RequestTracker tracker = _pendingRequests.get(thread);
        if (tracker == null)
            return () -> {};

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (tracker)
        {
            tracker.cancelDeferrals().incrementAndGet();
        }

        AtomicBoolean closed = new AtomicBoolean();
        return () -> {
            if (!closed.compareAndSet(false, true))
                return;

            RequestCancellation.Source deferred;
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (tracker)
            {
                if (tracker.cancelDeferrals().decrementAndGet() > 0)
                    return;
                deferred = tracker.deferredCancel().getAndSet(null);
            }
            if (deferred != null)
                cancelRequest(thread, tracker, deferred, true);
        };
    }

    static boolean isCancellationDeferred(Thread thread)
    {
        RequestTracker tracker = _pendingRequests.get(thread);
        return tracker != null && tracker.cancelDeferrals().get() > 0;
    }

    public static int getPendingRequestCount()
    {
        return _pendingRequests.size();
    }

    public static void shutDown(long msWaitForRequests)
    {
        while (msWaitForRequests > 0)
        {
            if (0==getPendingRequestCount())
                break;
            try {Thread.sleep(100);}catch(InterruptedException ignored){}
            msWaitForRequests -= 100;
        }
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testIsTimedOut() throws Exception
        {
            RequestCancellation.TestCase.runOnRequestThread(() -> {
                Thread thread = Thread.currentThread();
                AtomicReference<RequestTracker> finished = new AtomicReference<>();

                RequestCancellation.TestCase.doRequest(new MockHttpServletRequest("GET", "/test"), null, () -> {
                    RequestTracker tracker = getRequestSummary(thread);
                    finished.set(tracker);
                    assertTrue("Read-only request past the cutoff", isTimedOut(thread, tracker, tracker.startTime() + 1));
                    assertFalse("Read-only request within the timeout", isTimedOut(thread, tracker, tracker.startTime()));
                });
                assertFalse("Finished request", isTimedOut(thread, finished.get(), Long.MAX_VALUE));

                RequestCancellation.TestCase.doRequest(new MockHttpServletRequest("GET", "/test"), null, () -> {
                    RequestTracker tracker = getRequestSummary(thread);
                    tracker.request().setAttribute(EXPORT_ATTRIBUTE_NAME, true);
                    assertFalse("Export", isTimedOut(thread, tracker, Long.MAX_VALUE));
                });

                RequestCancellation.TestCase.doRequest(new MockHttpServletRequest("POST", "/test"), null, () ->
                    assertFalse("Mutating request", isTimedOut(thread, getRequestSummary(thread), Long.MAX_VALUE)));

                MockHttpServletRequest recycled = new MockHttpServletRequest("GET", "/test");
                RequestCancellation.TestCase.doRequest(recycled, null, () -> {
                    // A closed mock request throws on getAttribute(), like a request Tomcat has recycled
                    recycled.close();
                    assertFalse("Recycled request", isTimedOut(thread, getRequestSummary(thread), Long.MAX_VALUE));
                });
            });
        }

        /** The real sweep must find a long read-only request, stop its query, and leave exports alone */
        @Test
        public void testSweep() throws Exception
        {
            DbScope scope = DbScope.getLabKeyScope();

            int originalTimeout = AppProps.getInstance().getReadOnlyHttpRequestTimeout();
            setReadOnlyTimeout(2);
            try
            {
                RequestCancellation.TestCase.runOnRequestThread(() -> {
                    Thread thread = Thread.currentThread();

                    RequestCancellation.TestCase.doRequest(null, () -> {
                        long start = System.currentTimeMillis();
                        try
                        {
                            new SqlSelector(scope, "SELECT pg_sleep(30)").getObject(Object.class);
                            fail("Query wasn't cancelled");
                        }
                        catch (RuntimeException ignored) {} // Checked by its source below
                        // Timeout, HeartBeat's up-to-2 s lag, and the sweep's 5 s interval, with slack
                        assertTrue("Cancel took too long", System.currentTimeMillis() - start < 15_000);
                        assertEquals(RequestCancellation.Source.TIMEOUT, RequestCancellation.getSource(thread));
                    });
                    assertTrue("Connection wasn't released", ConnectionWrapper.getConnectionsByScopeForThread(thread).isEmpty());

                    RequestCancellation.TestCase.doRequest(null, () -> {
                        getRequestSummary(thread).request().setAttribute(EXPORT_ATTRIBUTE_NAME, true);
                        // Long enough for a sweep after the timeout despite HeartBeat's lag
                        new SqlSelector(scope, "SELECT pg_sleep(12)").getObject(Object.class);
                        assertNull("Export was cancelled", RequestCancellation.getSource(thread));
                    });
                });
            }
            finally
            {
                setReadOnlyTimeout(originalTimeout);
            }
        }

        /** With the driver's cancel unavailable, the fallback must still stop a query that ignores an interrupt, and its session must not return to the pool */
        @Test
        public void testCancelFallback() throws Exception
        {
            DbScope scope = DbScope.getLabKeyScope();
            AtomicReference<RuntimeException> queryFailure = new AtomicReference<>();
            Set<Integer> spids = ConcurrentHashMap.newKeySet();

            RequestCancellation._skipDriverCancel = true;
            try
            {
                RequestCancellation.TestCase.runOnRequestThread(() -> {
                    Thread requestThread = Thread.currentThread();
                    Thread canceller = new Thread(() -> {
                        try
                        {
                            long start = System.currentTimeMillis();
                            while (ConnectionWrapper.getConnectionsByScopeForEffectiveThread(requestThread).isEmpty() && System.currentTimeMillis() - start < 10_000)
                                //noinspection BusyWait
                                Thread.sleep(100);
                            // Let the statement reach the server
                            Thread.sleep(500);
                            ConnectionWrapper.getConnectionsByScopeForEffectiveThread(requestThread).values().forEach(c -> c.forEach(w -> spids.add(w.getSPID())));
                            cancelRequest(requestThread, getRequestSummary(requestThread), RequestCancellation.Source.TIMEOUT);
                        }
                        catch (InterruptedException ignored) {}
                    }, "TransactionFilter test canceller");
                    canceller.start();

                    RequestCancellation.TestCase.doRequest(null, () -> {
                        long start = System.currentTimeMillis();
                        try
                        {
                            new SqlSelector(scope, "SELECT pg_sleep(30)").getObject(Object.class);
                        }
                        catch (RuntimeException e)
                        {
                            queryFailure.set(e);
                        }
                        assertTrue("Cancel took too long", System.currentTimeMillis() - start < 15_000);
                    });
                    assertTrue("Connection wasn't released", ConnectionWrapper.getConnectionsByScopeForThread(requestThread).isEmpty());
                });
            }
            finally
            {
                RequestCancellation._skipDriverCancel = false;
            }

            assertNotNull("Query wasn't cancelled", queryFailure.get());
            boolean cancelled = false;
            for (Throwable t = queryFailure.get(); t != null && !cancelled; t = ExceptionUtil.getCause(t))
                cancelled = t instanceof SQLException e && SqlDialect.isCancelException(e);
            assertTrue("Query failed with something other than a cancel: " + queryFailure.get(), cancelled);

            assertFalse("Request had no connections", spids.isEmpty());
            assertEquals("Cancelled session went back to the pool", 0, RequestCancellation.TestCase.countLiveBackends(scope, spids));
        }

        /** A client may cancel only its user's read-only, non-export requests, and only from the tab that sent them */
        @Test
        public void testCancelRequestsRefused() throws Exception
        {
            int userId = TestContext.get().getUser().getUserId();
            String tabId = GUID.makeGUID();
            List<String> ids = List.of("r1");

            RequestCancellation.TestCase.runOnRequestThread(() -> {
                Thread thread = Thread.currentThread();

                RequestCancellation.TestCase.doRequest(cancellableRequest("GET", tabId, "r1"), null, () -> {
                    assertEquals(Outcome.WRONG_USER, cancelRequests(userId + 1, tabId, ids, RequestCancellation.Source.WS_MESSAGE).get("r1"));
                    assertEquals("Other tab", Outcome.NOT_FOUND, cancelRequests(userId, GUID.makeGUID(), ids, RequestCancellation.Source.WS_MESSAGE).get("r1"));
                    getRequestSummary(thread).request().setAttribute(EXPORT_ATTRIBUTE_NAME, true);
                    assertEquals(Outcome.EXPORT, cancelRequests(userId, tabId, ids, RequestCancellation.Source.WS_MESSAGE).get("r1"));
                    assertNull("Refused cancel was applied", RequestCancellation.getSource(thread));
                });

                RequestCancellation.TestCase.doRequest(cancellableRequest("POST", tabId, "r1"), null, () ->
                    assertEquals(Outcome.NOT_READ_ONLY, cancelRequests(userId, tabId, ids, RequestCancellation.Source.WS_MESSAGE).get("r1")));
            });

            assertEquals("Finished request", Outcome.NOT_FOUND, cancelRequests(userId, tabId, ids, RequestCancellation.Source.WS_MESSAGE).get("r1"));
        }

        /** A cancel by request id, or for the whole tab when its socket closes, stops a query that ignores an interrupt */
        @Test
        public void testCancelRequests() throws Exception
        {
            int userId = TestContext.get().getUser().getUserId();
            String tabId = GUID.makeGUID();

            assertCancelledWhileQuerying(cancellableRequest("GET", tabId, "r1"),
                () -> cancelRequests(userId, tabId, List.of("r1", "r2"), RequestCancellation.Source.WS_MESSAGE),
                Map.of("r1", Outcome.CANCELLED, "r2", Outcome.NOT_FOUND), RequestCancellation.Source.WS_MESSAGE);

            assertCancelledWhileQuerying(cancellableRequest("GET", tabId, "r3"),
                () -> cancelRequests(userId, tabId, null, RequestCancellation.Source.WS_CLOSE),
                Map.of("r3", Outcome.CANCELLED), RequestCancellation.Source.WS_CLOSE);
        }

        /** A deferred cancel waits for the last deferral to close, then bans the request's next connection, which only a deferral may still get */
        @Test
        public void testCancelDeferred() throws Exception
        {
            DbScope scope = DbScope.getLabKeyScope();

            RequestCancellation.TestCase.runOnRequestThread(() -> {
                Thread thread = Thread.currentThread();

                RequestCancellation.TestCase.doRequest(null, () -> {
                    try (TableResultSet rs = new SqlSelector(scope, "SELECT 1").getResultSet(false))
                    {
                        assertTrue(rs.next());
                        try (QuietCloser outer = deferCancellation())
                        {
                            try (QuietCloser inner = deferCancellation())
                            {
                                assertTrue(cancelRequest(thread, getRequestSummary(thread), RequestCancellation.Source.WS_MESSAGE));
                                assertEquals("Cancel wasn't recorded", RequestCancellation.Source.WS_MESSAGE, RequestCancellation.getSource(thread));
                            }
                            assertFalse("Cancel applied before the last deferral closed", thread.isInterrupted());
                        }
                        assertTrue("Deferred cancel wasn't applied", Thread.interrupted());
                    }
                    catch (SQLException e)
                    {
                        throw new RuntimeException(e);
                    }

                    // Like an audit insert, work inside a deferral still gets a connection despite the ban
                    try (QuietCloser ignored = deferCancellation())
                    {
                        assertEquals(Integer.valueOf(1), new SqlSelector(scope, "SELECT 1").getObject(Integer.class));
                    }

                    try
                    {
                        new SqlSelector(scope, "SELECT 1").getObject(Integer.class);
                        fail("Query after the deferred cancel wasn't stopped");
                    }
                    catch (RuntimeException e)
                    {
                        boolean aborted = false;
                        for (Throwable t = e; t != null && !aborted; t = ExceptionUtil.getCause(t))
                            aborted = t instanceof AbortedRequestException;
                        assertTrue("Query failed with something other than the ban: " + e, aborted);
                    }
                });
                assertTrue("Connection wasn't released", ConnectionWrapper.getConnectionsByScopeForThread(thread).isEmpty());
            });
        }

        private static MockHttpServletRequest cancellableRequest(String method, String tabId, String requestId)
        {
            MockHttpServletRequest request = new MockHttpServletRequest(method, "/test");
            request.setUserPrincipal(TestContext.get().getUser());
            request.addHeader(RequestCancellation.TAB_ID_HEADER, tabId);
            request.addHeader(RequestCancellation.REQUEST_ID_HEADER, requestId);
            return request;
        }

        /** Runs a query that only the database can stop, like selectDistinct's, and cancels it from another thread */
        private static void assertCancelledWhileQuerying(MockHttpServletRequest request, Supplier<Map<String, Outcome>> cancel, Map<String, Outcome> expected, RequestCancellation.Source source) throws Exception
        {
            DbScope scope = DbScope.getLabKeyScope();
            AtomicReference<Map<String, Outcome>> outcomes = new AtomicReference<>();

            RequestCancellation.TestCase.runOnRequestThread(() -> {
                Thread thread = Thread.currentThread();
                Thread canceller = new Thread(() -> {
                    try
                    {
                        long start = System.currentTimeMillis();
                        while (ConnectionWrapper.getConnectionsByScopeForEffectiveThread(thread).isEmpty() && System.currentTimeMillis() - start < 10_000)
                            //noinspection BusyWait
                            Thread.sleep(100);
                        // Let the statement reach the server
                        Thread.sleep(500);
                        outcomes.set(cancel.get());
                    }
                    catch (InterruptedException ignored) {}
                }, "TransactionFilter test canceller");

                RequestCancellation.TestCase.doRequest(request, null, () -> {
                    canceller.start();
                    long start = System.currentTimeMillis();
                    try
                    {
                        new SqlSelector(scope, "SELECT pg_sleep(30)").getObject(Object.class);
                        fail("Query wasn't cancelled");
                    }
                    catch (RuntimeException ignored) {} // Checked by its source below
                    assertTrue("Cancel took too long", System.currentTimeMillis() - start < 15_000);
                    assertEquals(source, RequestCancellation.getSource(thread));
                });

                try
                {
                    canceller.join(15_000);
                }
                catch (InterruptedException e)
                {
                    throw new RuntimeException(e);
                }
                assertTrue("Connection wasn't released", ConnectionWrapper.getConnectionsByScopeForThread(thread).isEmpty());
            });

            assertEquals(expected, outcomes.get());
        }

        private static void setReadOnlyTimeout(int seconds)
        {
            WriteableAppProps props = AppProps.getWriteableInstance();
            props.setReadOnlyHttpRequestTimeout(seconds);
            props.save(TestContext.get().getUser());
        }
    }
}
