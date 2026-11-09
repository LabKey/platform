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
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.HeartBeat;
import org.labkey.api.util.StartupListener;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ViewServlet;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.IOException;
import java.security.Principal;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.apache.commons.lang3.StringUtils.trimToEmpty;

public class TransactionFilter implements Filter
{
    private static final Logger _log = LogHelper.getLogger(ViewServlet.class, "DB connection status for HTTP requests");

    // NOTE: can't use ThreadLocal for this as you can't inspect the values for other threads
    private static final Map<Thread, RequestTracker> _pendingRequests = Collections.synchronizedMap(new WeakHashMap<>());

    public static final String READ_ONLY_ATTRIBUTE_NAME = "ReadOnlyHttpRequest";
    /** Read-only, but a legitimate export on a large instance can outlast the read-only timeout */
    public static final String EXPORT_ATTRIBUTE_NAME = "ExportHttpRequest";

    /** @param cancellation set once, by the first {@link RequestCancellation#requestCancelled} */
    public record RequestTracker(HttpServletRequest request, long startTime, AtomicReference<RequestCancellation.Cancelled> cancellation)
    {
        private RequestTracker(HttpServletRequest request)
        {
            this(request, System.currentTimeMillis(), new AtomicReference<>());
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
     * Stops a request and its database work. An interrupt alone stops a query only on the AsyncQueryRequest path; a
     * thread blocked in a JDBC read elsewhere (e.g. selectDistinct) needs the database to cancel its statement.
     */
    private static void cancelRequest(Thread thread, RequestTracker tracker, RequestCancellation.Source source)
    {
        boolean first;
        RequestCancellation.Cancelled cancelled;
        Map<DbScope, Set<ConnectionWrapper>> unsignalled;

        // The cancelled query fails within milliseconds, so the request can finish mid-cancel; doFilter() can't release the thread while we hold this
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (tracker)
        {
            if (_pendingRequests.get(thread) != tracker)
                return;

            first = RequestCancellation.requestCancelled(thread, tracker, source);
            if (!first)
                RequestCancellation.cancelledRequestStillRunning(thread, tracker);
            cancelled = tracker.cancellation().get();

            // Resent on later sweeps, since a cancel that arrives between statements does nothing
            Map<DbScope, Set<ConnectionWrapper>> connections = ConnectionWrapper.getConnectionsByScopeForEffectiveThread(thread);
            unsignalled = RequestCancellation.cancelQueriesViaOwningConnection(connections, source, cancelled, first);

            // Never close the thread's connections from here: the pool would hand one out while the thread is still mid-call on it
            if (connections.isEmpty())
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

        private static void setReadOnlyTimeout(int seconds)
        {
            WriteableAppProps props = AppProps.getWriteableInstance();
            props.setReadOnlyHttpRequestTimeout(seconds);
            props.save(TestContext.get().getUser());
        }
    }
}
