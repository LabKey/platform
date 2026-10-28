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
import org.labkey.remoteapi.query.ExecuteSqlCommand;
import org.labkey.remoteapi.query.SelectRowsResponse;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.TestProperties;
import org.labkey.test.WebTestHelper;
import org.labkey.test.categories.Daily;
import org.labkey.test.components.ext4.Window;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.OptionalFeatureHelper;
import org.labkey.test.util.core.admin.ServerUsageUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

/**
 * Checks that an abandoned read-only request is canceled on the server end to end: the page tags the request, sends
 * its id over the notification WebSocket, and the server stops it.
 */
@Category({Daily.class})
public class RequestCancellationTest extends BaseWebDriverTest
{
    private static final String SLOW_SQL = "SELECT COUNT(*) AS n FROM core.Modules a, core.Modules b, core.Modules c, core.Modules d, core.Modules e, core.Modules f";
    private static final String SLOW_NAMES = "SlowNames";
    private static final String SLOW_NAMES_SQL = "SELECT a.Name, COUNT(*) AS n FROM core.Modules a, core.Modules b, core.Modules c, core.Modules d, core.Modules e, core.Modules f GROUP BY a.Name";
    private static final String SLOW_STATS = "SlowStats";
    private static final double SLOW_STATS_ROWS = 3e7;
    private static final int METRICS_TIMEOUT = 180_000;
    private static final String WS_MESSAGE = "wsMessage";
    private static final String WS_CLOSE = "wsClose";
    private static final String FEATURE_DISABLE_CANCELLING = "disableCancelAbandonedRequests";

    @BeforeClass
    public static void setupProject() throws Exception
    {
        RequestCancellationTest init = getCurrentTest();
        init._containerHelper.createProject(init.getProjectName(), null);
        init.createQuery(init.getProjectName(), SLOW_NAMES, "core", SLOW_NAMES_SQL, null, false);
        init.createQuery(init.getProjectName(), SLOW_STATS, "core", init.getSlowStatsSql(), null, false);
    }

    /**
     * A cross join of core.Modules with about {@link #SLOW_STATS_ROWS} rows, since the module count varies by server.
     * Limiting the last join to some modules sets the row count closely; a LIMIT would make Postgres build every row
     * before the grid's first page.
     */
    private String getSlowStatsSql() throws IOException, CommandException
    {
        SelectRowsResponse response = new ExecuteSqlCommand("core", "SELECT Name FROM core.Modules ORDER BY Name")
            .execute(createDefaultConnection(), getProjectName());
        List<String> names = response.getRows().stream().map(row -> (String) row.get("Name")).toList();
        int modules = names.size();
        // Val needs three of them
        int joins = Math.max(3, (int) Math.ceil(Math.log(SLOW_STATS_ROWS) / Math.log(modules)));
        int lastJoinModules = (int) Math.min(modules, Math.ceil(SLOW_STATS_ROWS / Math.pow(modules, joins - 1)));

        String from = IntStream.range(0, joins)
            .mapToObj(i -> "core.Modules m" + i)
            .collect(Collectors.joining(", "));
        String lastJoinNames = names.subList(0, lastJoinModules).stream()
            .map(name -> "'" + name.replace("'", "''") + "'")
            .collect(Collectors.joining(", "));
        return "SELECT m0.Name AS A, m1.Name AS B, CAST(LENGTH(m0.Name) * 10000 + LENGTH(m1.Name) * 100 + LENGTH(m2.Name) AS DOUBLE) AS Val" +
            " FROM " + from + " WHERE m" + (joins - 1) + ".Name IN (" + lastJoinNames + ")";
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
        abortSlowRequest("executeSql", Map.of("schemaName", "core", "sql", SLOW_SQL));
    }

    /** Unlike executeSql, which runs on the async query path, selectDistinct runs on the request thread */
    @Test
    public void testAbortedSelectDistinctIsCancelledOnServer() throws Exception
    {
        abortSlowRequest("selectDistinctRows", Map.of("schemaName", "core", "queryName", SLOW_NAMES, "column", "Name"));
    }

    /** GH Issue 1568 */
    @Test
    public void testClosingSummaryStatisticsCancelsItsQuery() throws Exception
    {
        long before = getCancelCount(WS_MESSAGE);

        beginAt(WebTestHelper.buildURL("query", getProjectName(), "executeQuery", Map.of("schemaName", "core", "query.queryName", SLOW_STATS)));
        waitForWebSocket();
        DataRegionTable.DataRegion(getDriver()).withName("query").waitFor().clickColumnMenu("Val", false, "Summary Statistics...");
        sleep(2000); // Let the server start the stats query before the dialog closes
        // SummaryStatisticsDialog waits for the stats, which never arrive
        new Window<>("Summary Statistics", getDriver()).clickButton("Cancel", true);

        waitForCancelCount(WS_MESSAGE, before + 1);
    }

    @Test
    public void testClosedTabCancelsItsRequests() throws Exception
    {
        long before = getCancelCount(WS_CLOSE);

        doInNewTab(() -> {
            goToProjectHome();
            waitForWebSocket();
            assertTrue("Request wasn't tagged as cancellable", startSlowRequest("executeSql", Map.of("schemaName", "core", "sql", SLOW_SQL)));
            sleep(2000); // Let the server start the request before the tab closes
            return null;
        });

        waitForCancelCount(WS_CLOSE, before + 1);
    }

    @Test
    public void testOptOutDisablesCancelling()
    {
        assumeFalse("An app admin can't change optional features", TestProperties.isPrimaryUserAppAdmin());
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

    private void abortSlowRequest(String apiFunction, Map<String, Object> config) throws Exception
    {
        long before = getCancelCount(WS_MESSAGE);

        assertTrue("Request wasn't tagged as cancellable", startSlowRequest(apiFunction, config));
        sleep(2000); // Let the server start the request before the cancel arrives
        executeScript("window.requestCancelTest.controller.abort();");

        waitForEquals("Wrong request outcome", () -> "aborted",
            () -> executeScript("return window.requestCancelTest.outcome;"), WAIT_FOR_JAVASCRIPT);
        waitForCancelCount(WS_MESSAGE, before + 1);
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
     * Starts a request through a LABKEY.Query function that is cancellable by default, abortable through
     * window.requestCancelTest.controller
     * @return whether the request was tagged for cancelling on the server
     */
    private boolean startSlowRequest(String apiFunction, Map<String, Object> config)
    {
        return (Boolean) executeScript("""
            const state = window.requestCancelTest = { controller: new AbortController(), outcome: null };
            const xhr = LABKEY.Query[arguments[0]](Object.assign({
                signal: state.controller.signal,
                success: function() { state.outcome = 'succeeded'; },
                failure: function(error, response) { state.outcome = LABKEY.Ajax.isAborted(response) ? 'aborted' : 'failed'; },
            }, arguments[1]));
            return LABKEY.Ajax.getCancellableRequests().includes(xhr);
            """, apiFunction, config);
    }

    /** At least, since the counter is server-wide and other cancels can land meanwhile */
    private void waitForCancelCount(String metric, long minimum)
    {
        waitFor(() -> getCancelCountUnchecked(metric) >= minimum, "Requests canceled (" + metric + ") never reached " + minimum, METRICS_TIMEOUT);
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
