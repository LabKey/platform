/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.miniprofiler.MiniProfiler;
import org.labkey.api.miniprofiler.RequestInfo;
import org.labkey.api.query.QueryService;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.MockHttpResponseWithRealPassthrough;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.Callable;

public class AsyncQueryRequest<T>
{
    private static final Logger _log = LogHelper.getLogger(AsyncQueryRequest.class, "Runs DB queries on a background thread");

    private static class CancelledException extends RuntimeException
    {
    }

    @Nullable
    private final StackTraceElement[] _creationStackTrace;
    private final HttpServletResponse _rootResponse;

    boolean _cancelled;
    @Nullable Statement _statement;

    T _result;
    Throwable _exception;

    public AsyncQueryRequest(HttpServletResponse response)
    {
        _creationStackTrace = MiniProfiler.getTroubleshootingStackTrace();

        _rootResponse = getRootResponse(response);
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
        final QueryService qs = QueryService.get();
        final Object state = qs.cloneEnvironment();
        final RequestInfo current = MemTracker.getInstance().current();

        String traceId = CorrelationIdentifier.getTraceId();

        // Create the span, so we can connect the async query with its owning thread
        final Tracer tracer = GlobalTracer.get();
        final Span span = tracer.buildSpan("AsyncRequest").start();
        String spanId = CorrelationIdentifier.getSpanId();

        Runnable runnable = () -> {
            if (current != null)
                 MemTracker.get().startProfiler("async query");

            assert ThreadContext.isEmpty();  // Prevent/detect leaks
            // Connect log messages with the active trace and span
            ThreadContext.put(CorrelationIdentifier.getTraceIdKey(), traceId);
            ThreadContext.put(CorrelationIdentifier.getSpanIdKey(), spanId);

            qs.copyEnvironment(state);
            try
            {
                setResult(callable.call());
            }
            catch (Throwable t)
            {
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

        Thread thread = new Thread(runnable, "AsyncQueryRequest: " + Thread.currentThread().getName());
        // We want the async thread to use the same database connection, in case we have a transaction open, and
        // so that when the original thread finishes processing the results it ends up closing the right connection
        try (DbScope.ConnectionSharingCloseable ignored = DbScope.shareConnections(Thread.currentThread(), thread);
             Scope ignore = tracer.activateSpan(span))
        {
            thread.start();

            // Stash the original disconnect exception if the client has dropped off
            IOException clientDisconnectException = null;

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
                        throw UnexpectedException.wrap(ie);
                    }
                }

                if ((_result != null || _exception != null) && clientDisconnectException != null)
                {
                    // The client has disconnected, now that we're done with our async work, rethrow the IOException
                    throw clientDisconnectException;
                }

                if (_result != null)
                {
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
                }
            }
        }
    }

    synchronized public void setResult(T result)
    {
        _result = result;
        notify();
    }
    
    synchronized public void setException(Throwable exception)
    {
        _exception = exception;
        notify();
    }

    synchronized private void cancel()
    {
        _cancelled = true;
        if (_statement != null)
        {
            try
            {
                _log.debug("Aborting async statement on connection " + _statement.getConnection());
                _statement.cancel();
            }
            catch (SQLException e)
            {
                _log.error("Error cancelling statement", e);
            }
        }
    }

    /** @return the IOException from failing to write a space to the client, or null if the client is still connected */
    private IOException checkCancelled()
    {
        try
        {
            _rootResponse.getWriter().write(" ");
            _rootResponse.flushBuffer();
            _log.debug("ping client: success");
        }
        catch (IOException ioe)
        {
            _log.debug("ping client: failure: " + ioe.getMessage());
            cancel();
            return ioe;
        }
        return null;
    }

    @Nullable
    public StackTraceElement[] getCreationStackTrace()
    {
        return _creationStackTrace;
    }
}
