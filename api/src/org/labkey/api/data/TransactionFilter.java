/*
 * Copyright (c) 2004-2018 Fred Hutchinson Cancer Research Center
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
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.HeartBeat;
import org.labkey.api.util.StartupListener;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ViewServlet;

import java.io.IOException;
import java.security.Principal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;

import static org.apache.commons.lang3.StringUtils.trimToEmpty;

public class TransactionFilter implements Filter
{
    private static final Logger _log = LogHelper.getLogger(ViewServlet.class, "DB connection status for HTTP requests");

    // NOTE: can't use ThreadLocal for this as you can't inspect the values for other threads
    private static final Map<Thread, RequestTracker> _pendingRequests = Collections.synchronizedMap(new WeakHashMap<>());

    public static final String READ_ONLY_ATTRIBUTE_NAME = "ReadOnlyHttpRequest";

    public record RequestTracker(HttpServletRequest request, long startTime)
    {
        private RequestTracker(HttpServletRequest request)
        {
            this(request, System.currentTimeMillis());
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
                                
                                Object readOnly = Boolean.FALSE;
                                try
                                {
                                    readOnly = tracker.request.getAttribute(READ_ONLY_ATTRIBUTE_NAME);
                                }
                                catch (IllegalStateException ignored) {} // Ignore when the request has already been recycled

                                if (Boolean.TRUE.equals(readOnly) && tracker.startTime < cutoff)
                                {
                                    try (DbScope.ConnectionSharingCloseable ignored = DbScope.shareConnections(thread, Thread.currentThread()))
                                    {
                                        _log.info("Timing out request for {} on thread {}", tracker.toLogString(), thread);
                                        DbScope.closeConnectionsForCurrentThreadWithoutReleasingLocks();
                                        PipelineJobService.get().killProcessesForThread(thread);
                                    }
                                    thread.interrupt();
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
            previousSummary = _pendingRequests.put(t, new RequestTracker(request));
            chain.doFilter(req, resp);
        }
        finally
        {
            if (null == previousSummary)
                _pendingRequests.remove(t);
            else
                _pendingRequests.put(t, previousSummary);
            DbScope.finishedWithThread();

            // Clear the interrupted flag before we wrap up
            if (Thread.interrupted())
            {
                _log.debug("HTTP request was interrupted during its execution");
            }
        }
        FileUtil.stopRequest();
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
} 
