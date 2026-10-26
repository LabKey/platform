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
package org.labkey.test.tests;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.Connection;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.categories.Daily;
import org.labkey.test.util.OptionalFeatureHelper;
import org.labkey.test.util.core.admin.ServerUsageUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Checks that an abandoned read-only request is canceled on the server end to end: the page tags the request, sends
 * its id over the notification WebSocket, and the server stops it.
 */
@Category({Daily.class})
public class RequestCancellationTest extends BaseWebDriverTest
{
    // Runs for minutes with any module set, so it can only finish early by being canceled
    private static final String SLOW_SQL = "SELECT COUNT(*) AS n FROM core.Modules a, core.Modules b, core.Modules c, core.Modules d, core.Modules e, core.Modules f";
    private static final int METRICS_TIMEOUT = 180_000;
    private static final String WS_MESSAGE = "wsMessage";
    private static final String WS_CLOSE = "wsClose";
    private static final String FEATURE_DISABLE_CANCELLING = "disableCancelAbandonedRequests";

    @BeforeClass
    public static void setupProject()
    {
        RequestCancellationTest init = getCurrentTest();
        init._containerHelper.createProject(init.getProjectName(), null);
    }

    @Before
    public void preTest()
    {
        goToProjectHome();
        assertTrue("Cancelling abandoned requests is disabled", isCancellingEnabledOnPage());
        waitForWebSocket();
    }

    @Test
    public void testAbortedRequestIsCancelledOnServer() throws Exception
    {
        long before = getCancelCount(WS_MESSAGE);

        assertTrue("Request wasn't tagged as cancellable", startSlowQuery());
        sleep(2000); // Let the server start the request before the cancel arrives
        executeScript("window.requestCancelTest.controller.abort();");

        waitForEquals("Wrong request outcome", () -> "aborted",
            () -> executeScript("return window.requestCancelTest.outcome;"), WAIT_FOR_JAVASCRIPT);
        waitForCancelCount(WS_MESSAGE, before + 1);
    }

    @Test
    public void testClosedTabCancelsItsRequests() throws Exception
    {
        long before = getCancelCount(WS_CLOSE);

        doInNewTab(() -> {
            goToProjectHome();
            waitForWebSocket();
            assertTrue("Request wasn't tagged as cancellable", startSlowQuery());
            sleep(2000); // Let the server start the request before the tab closes
            return null;
        });

        waitForCancelCount(WS_CLOSE, before + 1);
    }

    @Test
    public void testOptOutDisablesCancelling()
    {
        Connection connection = createDefaultConnection();
        OptionalFeatureHelper.enableOptionalFeature(connection, FEATURE_DISABLE_CANCELLING);
        try
        {
            refresh();
            assertFalse("Cancelling abandoned requests is still enabled on the page", isCancellingEnabledOnPage());
            assertFalse("Request was tagged as cancellable", (Boolean) executeScript("""
                const xhr = LABKEY.Query.executeSql({ schemaName: 'core', sql: 'SELECT 1 AS n' });
                return LABKEY.Ajax.getCancellableRequests().includes(xhr);
                """));
        }
        finally
        {
            OptionalFeatureHelper.resetOptionalFeature(connection, FEATURE_DISABLE_CANCELLING);
        }
    }

    private boolean isCancellingEnabledOnPage()
    {
        return (Boolean) executeScript("return LABKEY.experimental.cancelAbandonedRequests === true;");
    }

    private void waitForWebSocket()
    {
        // The server ignores any message type but "cancel"
        waitFor(() -> (Boolean) executeScript("return LABKEY.WebSocket.send({ type: 'ping' });"), "The notification WebSocket didn't open", WAIT_FOR_JAVASCRIPT);
    }

    /**
     * Starts {@link #SLOW_SQL} through LABKEY.Query.executeSql, which is cancellable by default
     * @return whether the request was tagged for cancelling on the server
     */
    private boolean startSlowQuery()
    {
        return (Boolean) executeScript("""
            const state = window.requestCancelTest = { controller: new AbortController(), outcome: null };
            const xhr = LABKEY.Query.executeSql({
                schemaName: 'core',
                sql: arguments[0],
                signal: state.controller.signal,
                success: function() { state.outcome = 'succeeded'; },
                failure: function(error, response) { state.outcome = LABKEY.Ajax.isAborted(response) ? 'aborted' : 'failed'; },
            });
            return LABKEY.Ajax.getCancellableRequests().includes(xhr);
            """, SLOW_SQL);
    }

    private void waitForCancelCount(String metric, long expected)
    {
        waitForEquals("Requests canceled (" + metric + ")", () -> expected, () -> getCancelCountUnchecked(metric), METRICS_TIMEOUT);
    }

    private long getCancelCountUnchecked(String metric)
    {
        try
        {
            return getCancelCount(metric);
        }
        catch (IOException | CommandException e)
        {
            throw new RuntimeException(e);
        }
    }

    /** Server-wide count of requests canceled from one source, e.g. a page's WebSocket message, from Core's usage metrics */
    @SuppressWarnings("unchecked")
    private long getCancelCount(String metric) throws IOException, CommandException
    {
        // The usage report gathers every module's metrics, which can take over a minute on a large database
        Connection connection = createDefaultConnection().setTimeout(METRICS_TIMEOUT);
        Map<String, Object> core = ServerUsageUtils.getModuleMetrics(connection, "Core");
        Map<String, Object> counts = (Map<String, Object>) core.getOrDefault("simpleMetricCounts", Map.of());
        Map<String, Object> requestCancel = (Map<String, Object>) counts.getOrDefault("requestCancel", Map.of());
        return ((Number) requestCancel.getOrDefault(metric, 0)).longValue();
    }

    @Override
    protected String getProjectName()
    {
        return "RequestCancellationTest Project";
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return List.of("Core");
    }
}
