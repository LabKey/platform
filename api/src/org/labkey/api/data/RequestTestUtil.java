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

import jakarta.servlet.ServletException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.function.ThrowingRunnable;
import org.labkey.api.util.JunitUtil;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/** Test helpers for driving in-JVM requests through TransactionFilter and observing their database sessions */
public class RequestTestUtil
{
    /** {@link JunitUtil#runOnNewThread} with a fixed thread name; doRequest supplies the request itself */
    public static void runOnRequestThread(ThrowingRunnable body) throws Exception
    {
        JunitUtil.runOnNewThread("Test request", body);
    }

    /** Runs body inside a TransactionFilter request on the current thread, cancelled first if cancelledBy is set */
    public static void doRequest(@NotNull MockHttpServletRequest request, @Nullable RequestCancellation.Source cancelledBy, @NotNull Runnable body)
    {
        try
        {
            new TransactionFilter().doFilter(request, new MockHttpServletResponse(), (_, _) -> {
                if (cancelledBy != null)
                {
                    Thread thread = Thread.currentThread();
                    Assert.assertTrue(RequestCancellation.requestCancelled(thread, TransactionFilter.getRequestSummary(thread), cancelledBy));
                }
                body.run();
            });
        }
        catch (IOException | ServletException e)
        {
            throw new RuntimeException(e);
        }
    }

    public static void doRequest(@Nullable RequestCancellation.Source cancelledBy, @NotNull Runnable body)
    {
        doRequest(new MockHttpServletRequest("GET", "/test"), cancelledBy, body);
    }

    /**
     * Waits up to 10 seconds for thread to hold a connection, then briefly for its statement to reach the server.
     * Returns the thread's connections, which are empty if it never got one.
     */
    public static Map<DbScope, Set<ConnectionWrapper>> awaitStatementStarted(Thread thread) throws InterruptedException
    {
        long start = System.currentTimeMillis();
        Map<DbScope, Set<ConnectionWrapper>> connections;
        while ((connections = ConnectionWrapper.getConnectionsByScopeForEffectiveThread(thread)).isEmpty() && System.currentTimeMillis() - start < 10_000)
            //noinspection BusyWait
            Thread.sleep(50);
        if (!connections.isEmpty())
            Thread.sleep(500);
        return ConnectionWrapper.getConnectionsByScopeForEffectiveThread(thread);
    }

    /** Polls briefly, since a discarded session's backend exits only after the pool closes its connection */
    public static long countLiveBackends(DbScope scope, Set<Integer> spids) throws InterruptedException
    {
        SQLFragment live = SQLFragment.unsafe("SELECT COUNT(*) FROM pg_stat_activity WHERE pid ").appendInClause(spids, scope.getSqlDialect());
        long start = System.currentTimeMillis();
        long count;
        while ((count = new SqlSelector(scope, live).getObject(Long.class)) > 0 && System.currentTimeMillis() - start < 5_000)
            //noinspection BusyWait
            Thread.sleep(100);
        return count;
    }
}
