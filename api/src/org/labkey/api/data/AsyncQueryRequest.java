/*
 * Copyright (c) 2008-2026 LabKey Corporation
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

import datadog.trace.api.CorrelationIdentifier;
import datadog.trace.api.DDTags;
import datadog.trace.api.interceptor.MutableSpan;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.log.Fields;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.miniprofiler.MiniProfiler;
import org.labkey.api.miniprofiler.RequestInfo;
import org.labkey.api.query.QueryService;
import org.labkey.api.util.AbortedRequestException;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.TagRecordingSpan;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.MockHttpResponseWithRealPassthrough;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class AsyncQueryRequest<T>
{
    private static final Logger _log = LogHelper.getLogger(AsyncQueryRequest.class, "Runs DB queries on a background thread");
    private static final String CANCELLED_TAG = "labkey.async_query.cancelled";
    private static final String CLIENT_DISCONNECTED_TAG = "labkey.client_disconnected";
    private static final String ELAPSED_TAG = "labkey.async_query.ms";
    private static final long WORKER_EXIT_TIMEOUT_MS = 10_000;

    /** Cancelled workers whose requests stopped waiting for them, re-cancelled by the request timeout sweep until they finish */
    private static final Set<AsyncQueryRequest<?>> _detached = ConcurrentHashMap.newKeySet();

    private static class CancelledException extends RuntimeException
    {
    }

    @Nullable
    private final StackTraceElement[] _creationStackTrace;
    private final HttpServletResponse _rootResponse;
    /** Names the APM span; without it every async query aggregates under the bare operation name */
    private final @Nullable String _resourceName;
    /** Extra tags for APM search and grouping, beyond the resource name */
    private final @NotNull Map<String, String> _spanTags;
    /** The request's web span, which carries the URL, user agent, and client-observed duration */
    private final @Nullable MutableSpan _rootSpan;

    boolean _cancelled;
    boolean _finished;
    @Nullable Statement _statement;
    @Nullable Span _span;

    T _result;
    Throwable _exception;

    public AsyncQueryRequest(HttpServletResponse response, @Nullable String resourceName, @NotNull Map<String, String> spanTags)
    {
        _creationStackTrace = MiniProfiler.getTroubleshootingStackTrace();
        _resourceName = resourceName;
        _spanTags = spanTags;

        // Constructed on the request thread, so the active span leads to the web span
        _rootSpan = GlobalTracer.get().activeSpan() instanceof MutableSpan active ? active.getLocalRootSpan() : null;

        _rootResponse = getRootResponse(response);
    }

    /** A request can run several async queries, so the web span describes only the slowest */
    private static void tagRootSpan(MutableSpan rootSpan, long elapsedMs, Map<String, String> tags, boolean cancelled)
    {
        Object prior = rootSpan.getTag(ELAPSED_TAG);
        if (prior instanceof Number priorMs && priorMs.longValue() >= elapsedMs)
            return;

        rootSpan.setTag(ELAPSED_TAG, elapsedMs);
        // A null clears an earlier query's tag, but must not remove one the root set itself, such as a job's container
        tags.forEach((name, value) -> {
            if (null != value || null != prior)
                rootSpan.setTag(name, value);
        });
        rootSpan.setTag(CANCELLED_TAG, cancelled);
    }

    private HttpServletResponse getRootResponse(HttpServletResponse response)
    {
        // Look through the response objects to find the one that's closest to the "real" one. We can use it
        // to write spaces back to the client to see if it's still listening, even if we're using a mock response
        // object to buffer the response so we can transform it in some way.
        // This should only be done when we're writing something back that's tolerant of extra spaces at the
        // beginning of the output, like HTML and JSON, and not for things like TSV or Excel
        while (true)
        {
            if (response instanceof HttpServletResponseWrapper)
            {
                response = (HttpServletResponse) ((HttpServletResponseWrapper) response).getResponse();
            }
            else if (response instanceof MockHttpResponseWithRealPassthrough)
            {
                response = ((MockHttpResponseWithRealPassthrough) response).getResponse();
            }
            else
            {
                return response;
            }
        }
    }

    synchronized public void setStatement(@Nullable Statement statement)
    {
        if (_cancelled)
            throw new CancelledException();
        _statement = statement;
    }

    synchronized public T waitForResult(final Callable<T> callable) throws SQLException, IOException
    {
        final long start = System.currentTimeMillis();
        final QueryService qs = QueryService.get();
        final Object state = qs.cloneEnvironment();
        final RequestInfo current = MemTracker.getInstance().current();

        // Create the span, so we can connect the async query with its owning thread
        final Tracer tracer = GlobalTracer.get();
        final Span span = tracer.buildSpan("AsyncRequest").start();
        // Never a service-entry span, so Datadog computes no hits/duration/error metrics for it without this
        span.setTag(DDTags.MEASURED, true);
        if (_resourceName != null)
            span.setTag(DDTags.RESOURCE_NAME, _resourceName);
        _spanTags.forEach(span::setTag);
        _span = span;

        Runnable runnable = () -> {
            if (current != null)
                 MemTracker.get().startProfiler("async query");

            assert ThreadContext.isEmpty();  // Prevent/detect leaks

            qs.copyEnvironment(state);
            // Activate on this thread, not the caller's, or the JDBC integration starts a new trace instead of parenting here
            try (Scope ignored = tracer.activateSpan(span))
            {
                // Connect log messages with the active trace and span
                ThreadContext.put(CorrelationIdentifier.getTraceIdKey(), CorrelationIdentifier.getTraceId());
                ThreadContext.put(CorrelationIdentifier.getSpanIdKey(), CorrelationIdentifier.getSpanId());

                setResult(callable.call());
            }
            catch (Throwable t)
            {
                // Cancellation arrives here as a CancelledException or the driver's "statement cancelled" error; tagging that as an APM error makes every client disconnect look like a failure.
                // A timeout cancels the statement in the database before it interrupts the request thread, so the error can beat cancel().
                if (!isCancelled() && !isCancelException(t))
                {
                    Tags.ERROR.set(span, true);
                    span.log(Map.of(Fields.ERROR_OBJECT, t));
                }
                setException(t);
            }
            finally
            {
                if (current != null)
                    MemTracker.get().merge(current);
                qs.clearEnvironment();

                // Wrap up the span
                span.finish();
                ThreadContext.remove(CorrelationIdentifier.getTraceIdKey());
                ThreadContext.remove(CorrelationIdentifier.getSpanIdKey());
            }
        };

        String threadName = "AsyncQueryRequest: " + Thread.currentThread().getName();
        TransactionFilter.RequestTracker requestTracker = TransactionFilter.getRequestSummary(Thread.currentThread());
        if (requestTracker != null)
        {
            threadName += " for " + requestTracker.getUrl();
        }
        Thread thread = new Thread(runnable, threadName);
        // We want the async thread to use the same database connection, in case we have a transaction open, and
        // so that when the original thread finishes processing the results it ends up closing the right connection
        boolean started = false;
        try (DbScope.ConnectionSharingCloseable ignored = DbScope.shareConnections(Thread.currentThread(), thread))
        {
            thread.start();
            started = true;

            // Stash the original disconnect exception if the client has dropped off
            IOException clientDisconnectException = null;
            // A ping commits the response, so a fast query must not ping just to check for a disconnect
            boolean pinged = false;

            while(true)
            {
                if (_result == null && _exception == null)
                {
                    try
                    {
                        wait(2000);
                    }
                    catch (InterruptedException ie)
                    {
                        T uncollected = _result;
                        cancel(RequestCancellation.getSource(Thread.currentThread()));
                        awaitWorkerAfterInterrupt(thread);
                        closeResult(uncollected);
                        throw new AbortedRequestException("Interrupted before DB query completed");
                    }
                }

                if ((_result != null || _exception != null) && clientDisconnectException != null)
                {
                    // The client has disconnected, now that we're done with our async work, rethrow the IOException
                    throw clientDisconnectException;
                }

                if (_result != null)
                {
                    // The client may have left since the last ping; catch it here rather than as a Broken pipe while rendering
                    if (pinged && null != (clientDisconnectException = pingClient()))
                    {
                        closeResult(_result);
                        throw clientDisconnectException;
                    }
                    return _result;
                }

                if (_exception != null)
                {
                    if (_exception instanceof QueryService.NamedParameterNotProvided)
                        throw (QueryService.NamedParameterNotProvided)_exception;

                    if (_exception instanceof RuntimeSQLException)
                        _exception = ((RuntimeSQLException)_exception).getSQLException();
                    if (_exception instanceof SQLException sqlE)
                    {
                        SQLException sqlE2 = new SQLException(sqlE.getMessage(), sqlE.getSQLState(), sqlE.getErrorCode());
                        sqlE2.setNextException(sqlE);
                        throw sqlE2;
                    }

                    throw UnexpectedException.wrap(_exception);
                }

                if (clientDisconnectException == null)
                {
                    // Remember if the client has disconnected, but don't break out of this loop until the async
                    // thread has finished its work and released any resources it holds
                    clientDisconnectException = checkCancelled();
                    pinged = true;
                }
            }
        }
        finally
        {
            // The runnable finishes the span; without a thread to run it the span stays open and its parent trace never completes
            if (!started)
                span.finish();

            if (null != _rootSpan)
                tagRootSpan(_rootSpan, System.currentTimeMillis() - start, _spanTags, _cancelled);
        }
    }

    /**
     * Keeps sharing connections until the worker releases the one it borrowed. Otherwise its release can't find the
     * request thread's holder, which then stays referenced on an aborted connection that the thread's next request reuses.
     */
    synchronized private void awaitWorkerAfterInterrupt(Thread worker)
    {
        long deadline = System.currentTimeMillis() + WORKER_EXIT_TIMEOUT_MS;
        while (!_finished)
        {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0)
            {
                _log.warn("Async query thread '{}' still running {} ms after its request was interrupted", worker.getName(), WORKER_EXIT_TIMEOUT_MS);
                // The worker still holds this thread's connection. Detaching keeps the thread's next request off it; marking
                // discards it even if this request's cleanup releases it while the worker is still using it.
                ConnectionWrapper.getConnectionsByScopeForEffectiveThread(Thread.currentThread()).values().forEach(c -> c.forEach(ConnectionWrapper::markCancelled));
                DbScope.detachConnectionHolders(Thread.currentThread());
                _detached.add(this);
                return;
            }
            try
            {
                wait(remaining);
            }
            catch (InterruptedException ignored)
            {
                // Already cancelling
            }
        }
    }

    /** Called by the request timeout sweep */
    static void resendCancelsToDetachedWorkers()
    {
        _detached.removeIf(AsyncQueryRequest::resendCancel);
    }

    /** @return true once the worker has finished */
    private boolean resendCancel()
    {
        Statement statement;
        synchronized (this)
        {
            if (_finished)
                return true;
            statement = _statement;
        }
        // Outside the lock, since the driver opens a connection to send it. A no-op until the statement is executing; it can't reach another statement on the connection.
        if (statement != null)
        {
            try
            {
                statement.cancel();
            }
            catch (SQLException e)
            {
                _log.debug("Failed to resend cancel to a detached async statement", e);
            }
        }
        return false;
    }

    synchronized public void setResult(T result)
    {
        if (_cancelled)
            closeResult(result);
        _result = result;
        _finished = true;
        notify();
    }

    synchronized public void setException(Throwable exception)
    {
        _exception = exception;
        _finished = true;
        notify();
    }

    private void closeResult(@Nullable T result)
    {
        if (result instanceof AutoCloseable ac)
        {
            try
            {
                ac.close();
            }
            catch (Exception e)
            {
                _log.warn("Failed to close result after cancellation", e);
            }
        }
    }

    private static boolean isCancelException(Throwable t)
    {
        for (; t != null; t = t.getCause())
        {
            if (t instanceof SQLException e && SqlDialect.isCancelException(e))
                return true;
        }
        return false;
    }

    synchronized private boolean isCancelled()
    {
        return _cancelled;
    }

    synchronized private void cancel(@Nullable RequestCancellation.Source source)
    {
        _cancelled = true;
        if (_span != null)
        {
            _span.setTag(CANCELLED_TAG, true);
            if (source != null)
                _span.setTag("labkey.cancel.source", source.toString());
        }
        RequestCancellation.statementCancelled(source, _statement != null);
        if (_statement != null)
        {
            try
            {
                _log.debug("Aborting async statement on connection {}", _statement.getConnection());
                if (_statement.getConnection() instanceof ConnectionWrapper connection)
                    connection.markCancelled();
                _statement.cancel();
            }
            catch (SQLException e)
            {
                if (isClosed(_statement))
                    _log.debug("Statement already closed; nothing to cancel");
                else
                    _log.error("Error cancelling statement", e);
            }
        }
    }

    private static boolean isClosed(Statement statement)
    {
        try
        {
            return statement.isClosed();
        }
        catch (SQLException e)
        {
            return false;
        }
    }

    /** @return the IOException from failing to write a space to the client, or null if the client is still connected */
    private IOException checkCancelled()
    {
        IOException ioe = pingClient();
        if (null != ioe)
        {
            RequestCancellation.requestCancelled(Thread.currentThread(), TransactionFilter.getRequestSummary(Thread.currentThread()), RequestCancellation.Source.CLIENT_DISCONNECT);
            cancel(RequestCancellation.Source.CLIENT_DISCONNECT);
        }
        return ioe;
    }

    private @Nullable IOException pingClient()
    {
        try
        {
            _rootResponse.getWriter().write(" ");
            _rootResponse.flushBuffer();
            _log.debug("ping client: success");
            return null;
        }
        catch (IOException ioe)
        {
            _log.debug("ping client: failure: {}", ioe.getMessage());
            // On the root span, since the AsyncRequest span has already finished when the final ping fails
            if (_rootSpan != null)
                _rootSpan.setTag(CLIENT_DISCONNECTED_TAG, true);
            return ioe;
        }
    }

    @Nullable
    public StackTraceElement[] getCreationStackTrace()
    {
        return _creationStackTrace;
    }

    public static class TestCase extends Assert
    {
        private static Map<String, String> queryTags(String query, @Nullable String schema)
        {
            Map<String, String> tags = new HashMap<>();
            tags.put("labkey.query", query);
            tags.put("labkey.db_schema", schema);
            return tags;
        }

        @Test
        public void testSlowestQueryTagsRootSpan()
        {
            Map<String, Object> tags = new HashMap<>();
            MutableSpan root = TagRecordingSpan.create(tags);

            // The first query's null must leave the root's own tag alone
            tags.put("labkey.db_schema", "job");
            tagRootSpan(root, 100, queryTags("Z", null), false);
            assertEquals("job", tags.get("labkey.db_schema"));

            tagRootSpan(root, 500, queryTags("A", "core"), false);
            tagRootSpan(root, 200, queryTags("B", "exp"), true);
            tagRootSpan(root, 500, queryTags("C", "exp"), true);
            assertEquals(Map.of(ELAPSED_TAG, 500L, "labkey.query", "A", "labkey.db_schema", "core", CANCELLED_TAG, false), tags);

            // A slower query with no schema must not inherit the faster one's
            tagRootSpan(root, 900, queryTags("D", null), true);
            assertEquals(Map.of(ELAPSED_TAG, 900L, "labkey.query", "D", CANCELLED_TAG, true), tags);
        }

        /** An interrupt must leave the request thread's connection holder released and its next query working */
        @Test
        public void testInterruptReleasesConnection() throws Exception
        {
            DbScope scope = DbScope.getLabKeyScope();

            for (int i = 0; i < 5; i++)
            {
                AtomicReference<Throwable> failure = new AtomicReference<>();
                AtomicBoolean holderReleased = new AtomicBoolean();
                AtomicReference<Throwable> nextQueryFailure = new AtomicReference<>();
                Set<Integer> spids = ConcurrentHashMap.newKeySet();

                Thread requestThread = new Thread(() -> {
                    try
                    {
                        AsyncQueryRequest<Object> request = new AsyncQueryRequest<>(new MockHttpServletResponse(), "testInterruptReleasesConnection", Map.of());
                        SqlSelector selector = new SqlSelector(scope, "SELECT pg_sleep(30)");
                        selector.setAsyncRequest(request);
                        request.waitForResult(() -> selector.getObject(Object.class));
                    }
                    catch (Throwable t)
                    {
                        failure.set(t);
                    }

                    try
                    {
                        // Checked before finishedWithThread(), whose cleanup would hide a leak
                        holderReleased.set(!scope.isThreadConnectionActive());
                        new SqlSelector(scope, "SELECT 1").getObject(Integer.class);
                    }
                    catch (Throwable t)
                    {
                        nextQueryFailure.set(t);
                    }
                    finally
                    {
                        DbScope.finishedWithThread();
                    }
                }, "AsyncQueryRequest interrupt test " + i);

                long start = System.currentTimeMillis();
                requestThread.start();
                while (ConnectionWrapper.getConnectionsByScopeForEffectiveThread(requestThread).isEmpty() && System.currentTimeMillis() - start < 10_000)
                {
                    //noinspection BusyWait
                    Thread.sleep(50);
                }
                // Let the statement reach the server
                Thread.sleep(300);
                ConnectionWrapper.getConnectionsByScopeForEffectiveThread(requestThread).values().forEach(c -> c.forEach(w -> spids.add(w.getSPID())));

                requestThread.interrupt();
                requestThread.join(30_000);

                assertFalse("Request thread didn't exit", requestThread.isAlive());
                assertTrue("Expected an aborted request, got " + failure.get(), failure.get() instanceof AbortedRequestException);
                assertTrue("Request thread's connection holder is still referenced", holderReleased.get());
                assertNull("Next query on the request thread failed", nextQueryFailure.get());
                assertFalse("Interrupted query had no connections", spids.isEmpty());
                assertEquals("Cancelled session went back to the pool", 0, RequestCancellation.TestCase.countLiveBackends(scope, spids));
            }
        }

        /** A worker that outlives the interrupt wait mustn't leave its connection for the request thread's next request */
        @Test
        public void testStuckWorkerDetached() throws Exception
        {
            DbScope scope = DbScope.getLabKeyScope();
            CountDownLatch holding = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch workerDone = new CountDownLatch(1);
            AtomicReference<Integer> workerSpid = new AtomicReference<>();
            AtomicReference<Throwable> workerFailure = new AtomicReference<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            AtomicBoolean holderReleased = new AtomicBoolean();
            AtomicReference<Integer> nextSpid = new AtomicReference<>();
            AtomicReference<Throwable> nextQueryFailure = new AtomicReference<>();

            Thread requestThread = new Thread(() -> {
                try
                {
                    AsyncQueryRequest<Object> request = new AsyncQueryRequest<>(new MockHttpServletResponse(), "testStuckWorkerDetached", Map.of());
                    request.waitForResult(() -> {
                        try (Connection conn = scope.getConnection())
                        {
                            workerSpid.set(((ConnectionWrapper) conn).getSPID());
                            holding.countDown();
                            // No statement for the interrupt to cancel, like a worker busy outside JDBC
                            release.await();
                        }
                        catch (Throwable t)
                        {
                            workerFailure.set(t);
                        }
                        finally
                        {
                            workerDone.countDown();
                        }
                        return null;
                    });
                }
                catch (Throwable t)
                {
                    failure.set(t);
                }

                try
                {
                    holderReleased.set(!scope.isThreadConnectionActive());
                    try (Connection conn = scope.getConnection())
                    {
                        nextSpid.set(((ConnectionWrapper) conn).getSPID());
                    }
                }
                catch (Throwable t)
                {
                    nextQueryFailure.set(t);
                }
                finally
                {
                    DbScope.finishedWithThread();
                }
            }, "AsyncQueryRequest stuck worker test");

            try
            {
                requestThread.start();
                assertTrue("Worker never got a connection", holding.await(10, TimeUnit.SECONDS));
                requestThread.interrupt();
                requestThread.join(WORKER_EXIT_TIMEOUT_MS + 10_000);

                assertFalse("Request thread didn't exit", requestThread.isAlive());
                assertTrue("Expected an aborted request, got " + failure.get(), failure.get() instanceof AbortedRequestException);
                assertTrue("Request thread's connection holder still references the worker's connection", holderReleased.get());
                assertNull("Next query on the request thread failed", nextQueryFailure.get());
                assertNotEquals("Next request reused the stuck worker's connection", workerSpid.get(), nextSpid.get());
            }
            finally
            {
                release.countDown();
            }

            assertTrue("Worker didn't finish", workerDone.await(10, TimeUnit.SECONDS));
            assertNull("Worker's connection release failed", workerFailure.get());
            assertEquals("Stuck worker's session went back to the pool", 0, RequestCancellation.TestCase.countLiveBackends(scope, Set.of(workerSpid.get())));
        }

        /** A statement that reaches the database only after its request stopped waiting, where the first cancel did nothing, must still be cancelled */
        @Test
        public void testDetachedWorkerCancelled() throws Exception
        {
            DbScope scope = DbScope.getLabKeyScope();
            CountDownLatch registered = new CountDownLatch(1);
            CountDownLatch requestDone = new CountDownLatch(1);
            CountDownLatch workerDone = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            AtomicReference<Throwable> queryFailure = new AtomicReference<>();
            AtomicLong queryMs = new AtomicLong();

            Thread requestThread = new Thread(() -> {
                AsyncQueryRequest<Object> request = new AsyncQueryRequest<>(new MockHttpServletResponse(), "testDetachedWorkerCancelled", Map.of());
                try
                {
                    request.waitForResult(() -> {
                        try (Connection conn = scope.getConnection(); Statement stmt = conn.createStatement())
                        {
                            // Like a worker starved of CPU between registering its statement and sending it
                            request.setStatement(stmt);
                            registered.countDown();
                            requestDone.await();
                            long start = System.currentTimeMillis();
                            try
                            {
                                stmt.executeQuery("SELECT pg_sleep(30)");
                            }
                            catch (SQLException e)
                            {
                                queryFailure.set(e);
                            }
                            queryMs.set(System.currentTimeMillis() - start);
                        }
                        finally
                        {
                            workerDone.countDown();
                        }
                        return null;
                    });
                }
                catch (Throwable t)
                {
                    failure.set(t);
                }
                finally
                {
                    requestDone.countDown();
                    DbScope.finishedWithThread();
                }
            }, "AsyncQueryRequest detached worker test");

            requestThread.start();
            assertTrue("Worker never registered its statement", registered.await(10, TimeUnit.SECONDS));
            requestThread.interrupt();
            requestThread.join(WORKER_EXIT_TIMEOUT_MS + 10_000);
            assertFalse("Request thread didn't exit", requestThread.isAlive());
            assertTrue("Expected an aborted request, got " + failure.get(), failure.get() instanceof AbortedRequestException);

            // The sweep's 5 s interval, with slack
            assertTrue("Worker didn't finish", workerDone.await(20, TimeUnit.SECONDS));
            assertTrue("Query wasn't cancelled: " + queryFailure.get(), queryFailure.get() instanceof SQLException e && SqlDialect.isCancelException(e));
            assertTrue("Cancel took too long: " + queryMs.get() + " ms", queryMs.get() < 15_000);
        }
    }
}
