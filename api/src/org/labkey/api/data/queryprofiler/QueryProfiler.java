/*
 * Copyright (c) 2009-2017 LabKey Corporation
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

package org.labkey.api.data.queryprofiler;

import org.apache.commons.collections4.map.AbstractReferenceMap.ReferenceStrength;
import org.apache.commons.collections4.map.ReferenceMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Appender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.RollingFileAppender;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.QueryLogging;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TSVWriter;
import org.labkey.api.miniprofiler.MiniProfiler;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.Formats;
import org.labkey.api.util.LogPrintWriter;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ShutdownListener;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewServlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

/*
* User: adam
* Date: Oct 14, 2009
* Time: 6:31:40 PM
*/
public class QueryProfiler
{
    private static final Logger LOG = Logger.getLogger(QueryProfiler.class);
    private static final QueryProfiler INSTANCE = new QueryProfiler();

    private final BlockingQueue<Query> _queue = new LinkedBlockingQueue<>(1000);
    private final Map<String, QueryTracker> _queries = new ReferenceMap<>(ReferenceStrength.HARD, ReferenceStrength.WEAK);
    private final Object _lock = new Object();
    private final Collection<QueryTrackerSet> _trackerSets = new ArrayList<>();

    // All access to these guarded by LOCK
    private long _requestQueryCount;
    private long _requestQueryTime;
    private long _backgroundQueryCount;
    private long _backgroundQueryTime;
    private long _uniqueQueryCountEstimate;  // This is a ceiling; true unique count is likely less than this since we're limiting capacity
    private int _requestCountAtLastReset;
    private long _upTimeAtLastReset;
    private boolean _hasBeenReset = false;

    private final List<DatabaseQueryListener> _listeners = new CopyOnWriteArrayList<>();

    public static QueryProfiler getInstance()
    {
        return INSTANCE;
    }

    private QueryProfiler()
    {
        getTrackerSets().add(new InvocationQueryTrackerSet());

        getTrackerSets().add(new QueryTrackerSet("Total", "highest cumulative execution time", false, true, new QueryTrackerComparator()
        {
            long getPrimaryStatisticValue(QueryTracker qt)
            {
                return qt.getCumulative();
            }

            long getSecondaryStatisticValue(QueryTracker qt)
            {
                return qt.getCount();
            }
        }));

        getTrackerSets().add(new QueryTrackerSet("Avg", "highest average execution time", false, true, new QueryTrackerComparator()
        {
            long getPrimaryStatisticValue(QueryTracker qt)
            {
                return qt.getAverage();
            }

            long getSecondaryStatisticValue(QueryTracker qt)
            {
                return qt.getCumulative();
            }
        }));

        getTrackerSets().add(new QueryTrackerSet("Max", "highest maximum execution time", false, true, new QueryTrackerComparator()
        {
            long getPrimaryStatisticValue(QueryTracker qt)
            {
                return qt.getMax();
            }

            long getSecondaryStatisticValue(QueryTracker qt)
            {
                return qt.getCumulative();
            }
        }));

        getTrackerSets().add(new QueryTrackerSet("Last", "most recent invocation time", false, true, new QueryTrackerComparator()
        {
            long getPrimaryStatisticValue(QueryTracker qt)
            {
                return qt.getLastInvocation();
            }

            long getSecondaryStatisticValue(QueryTracker qt)
            {
                return qt.getCumulative();
            }

            @Override
            String getFormattedPrimaryStatistic(QueryTracker qt)
            {
                return PageFlowUtil.filter(DateUtil.formatDateTime(ContainerManager.getRoot(), new Date(getPrimaryStatisticValue(qt))));
            }
        }));

        // Not displayed, but gives new queries some time to get above one of the other thresholds. Without this,
        // the first N unique queries would dominate the statistics.
        getTrackerSets().add(new QueryTrackerSet("First", "first invocation time", true, false, new QueryTrackerComparator()
        {
            long getPrimaryStatisticValue(QueryTracker qt)
            {
                return qt.getFirstInvocation();
            }

            long getSecondaryStatisticValue(QueryTracker qt)
            {
                return 0;   // Don't care about secondary sort -- we don't display this anyway
            }
        }));

        initializeCounters();
        QueryProfilerThread thread = new QueryProfilerThread();
        // It's a daemon thread, but shutdown listener ensures orderly shutdown and logs query stats at shutdown
        ContextListener.addShutdownListener(thread);

        thread.start();
    }

    public void addListener(DatabaseQueryListener listener)
    {
        _listeners.add(listener);
    }

    public void track(@Nullable DbScope scope, String sql, @Nullable List<Object> parameters, long elapsed,
                      @Nullable StackTraceElement[] stackTrace, boolean requestThread, QueryLogging queryLogging)
    {
        if (null == stackTrace)
            stackTrace = Thread.currentThread().getStackTrace();

        for (DatabaseQueryListener listener : _listeners)
        {
            if (listener.matches(scope, sql, queryLogging))
            {
                Map<DatabaseQueryListener, Object> listenersEnvironment = (Map)QueryService.get().getEnvironment(QueryService.Environment.LISTENER_ENVIRONMENTS);
                Object listenerEnvironment;
                if (listenersEnvironment == null)
                {
                    listenerEnvironment = null;
                    LOG.warn("No DatabaseQueryListener environment available", new Exception());
                }
                else
                {
                    listenerEnvironment = listenersEnvironment.get(listener);
                }
                listener.queryInvoked(scope, sql,
                        (User) QueryService.get().getEnvironment(QueryService.Environment.USER),
                        (Container)QueryService.get().getEnvironment(QueryService.Environment.CONTAINER), listenerEnvironment, queryLogging);
            }
        }

        MiniProfiler.addQuery(elapsed, sql, stackTrace);

        // Don't block if queue is full
        _queue.offer(new Query(scope, sql, parameters, elapsed, stackTrace, requestThread));
    }

    public void resetAllStatistics()
    {
        synchronized (_lock)
        {
            for (QueryTrackerSet set : getTrackerSets())
                set.clear();

            _queries.clear();

            initializeCounters();

            _hasBeenReset = true;
        }
    }

    private void initializeCounters()
    {
        synchronized (_lock)
        {
            _requestQueryCount = 0;
            _requestQueryTime = 0;
            _backgroundQueryCount = 0;
            _backgroundQueryTime = 0;
            _uniqueQueryCountEstimate = 0;
            _requestCountAtLastReset = ViewServlet.getRequestCount();

            RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
            if (runtimeBean != null)
                _upTimeAtLastReset = runtimeBean.getUptime();
        }
    }

    private class ReportView extends HttpView
    {
        private final String _statName;
        private final String _buttonHTML;
        private final ActionURLFactory _captionURLFactory;
        private final ActionURLFactory _stackTraceURLFactory;

        private ReportView(String statName, String buttonHTML, ActionURLFactory captionURLFactory, ActionURLFactory stackTraceURLFactory)
        {
            _statName = statName;
            _buttonHTML = buttonHTML;
            _captionURLFactory = captionURLFactory;
            _stackTraceURLFactory = stackTraceURLFactory;
        }

        @Override
        protected void renderInternal(Object model, PrintWriter out)
        {
            for (QueryTrackerSet set : getTrackerSets())
            {
                if (set.getCaption().equals(_statName))
                {
                    out.println("\n<table>");

                    // Don't update anything while we're rendering the report or vice versa
                    synchronized (_lock)
                    {
                        int requests = ViewServlet.getRequestCount() - _requestCountAtLastReset;

                        out.println("  <tr><td colspan=5>" + _buttonHTML + "</td></tr>");

                        out.println("  <tr><td style=\"border-top:1px solid;text-align:center\" colspan=5>Queries Executed Within HTTP Requests</td></tr>");
                        out.println("  <tr><td>Query Count:</td><td style=\"text-align:right\">" + Formats.commaf0.format(_requestQueryCount) + "</td>");
                        out.println("<td style=\"width:10px\">&nbsp;</td>");
                        out.println("<td>Query Time:</td><td style=\"text-align:right\">" + Formats.commaf0.format(_requestQueryTime) + "</td>");
                        out.println("</tr>\n  <tr>");
                        out.println("<td>Queries per Request:</td><td style=\"text-align:right\">" + Formats.f1.format((double) _requestQueryCount / requests) + "</td>");
                        out.println("<td style=\"width:10px\">&nbsp;</td>");
                        out.println("<td>Query Time per Request:</td><td style=\"text-align:right\">" + Formats.f1.format((double) _requestQueryTime / requests) + "</td>");
                        out.println("</tr>\n  <tr>");
                        out.println("<td>" + (_hasBeenReset ? "Request Count Since Last Reset" : "Request Count") + ":</td><td style=\"text-align:right\">" + Formats.commaf0.format(requests) + "</td><td colspan=3></td></tr>");
                        out.println("  <tr><td style=\"border-top:1px solid\" colspan=5>&nbsp;</td></tr>");

                        out.println("  <tr><td style=\"border-top:1px solid;text-align:center\" colspan=5>Queries Executed Within Background Threads</td></tr>");
                        out.println("  <tr><td>Query Count:</td><td style=\"text-align:right\">" + Formats.commaf0.format(_backgroundQueryCount) + "</td>");
                        out.println("<td style=\"width:10px\">&nbsp;</td>");
                        out.println("<td>Query Time:</td><td style=\"text-align:right\">" + Formats.commaf0.format(_backgroundQueryTime) + "</td>");
                        out.println("</tr>");
                        out.println("  <tr><td style=\"border-top:1px solid\" colspan=5>&nbsp;</td></tr>");
                        out.println("  <tr><td colspan=5>&nbsp;</td></tr>");

                        out.println("  <tr><td>Total Unique Queries");

                        if (_uniqueQueryCountEstimate > QueryTrackerSet.STANDARD_LIMIT)
                            out.println(" (Estimate)");

                        out.println(":</td><td style=\"text-align:right\">" + Formats.commaf0.format(_uniqueQueryCountEstimate) + "</td>");
                        out.println("<td style=\"width:10px\">&nbsp;</td>");

                        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
                        if (runtimeBean != null)
                        {
                            long upTime = runtimeBean.getUptime() - _upTimeAtLastReset;
                            upTime = upTime - (upTime % 1000);
                            out.println("<td>" + (_hasBeenReset ? "Elapsed Time Since Last Reset" : "Server Uptime") + ":</td><td style=\"text-align:right\">" + DateUtil.formatDuration(upTime) + "</td>");
                        }
                        out.println("</tr>");
                        out.println("</table><br><br>");

                        out.println("<table>");
                        out.println("  <tr><td>Unique queries with the " + set.getDescription() + " (top " + Formats.commaf0.format(set.size()) + "):</td></tr>");
                        out.println("</table><br>");

                        out.println("<table class=\"labkey-data-region-legacy labkey-show-borders\">");
                        QueryTracker.renderRowHeader(out, set, _captionURLFactory);

                        int row = 0;

                        for (QueryTracker tracker : set.descendingSet())
                            tracker.renderRow(out, (0 == (++row) % 2) ? "labkey-alternate-row" : "labkey-row", _stackTraceURLFactory);

                        out.println("</table>");
                    }

                    return;
                }
            }

            out.println("<font class=\"labkey-error\">Error: Query statistic \"" + PageFlowUtil.filter(_statName) + "\" does not exist</font>");
        }
    }

    public HttpView getReportView(String statName, String buttonHTML, ActionURLFactory captionURLFactory, ActionURLFactory stackTraceURLFactory)
    {
        return new ReportView(statName, buttonHTML, captionURLFactory, stackTraceURLFactory);
    }

    public HttpView getStackTraceView(final int hashCode, final ActionURLFactory executeFactory)
    {
        return new HttpView()
        {
            @Override
            protected void renderInternal(Object model, PrintWriter out)
            {
                // Don't update anything while we're rendering the report or vice versa
                synchronized (_lock)
                {
                    QueryTracker tracker = findTracker(hashCode);

                    if (null == tracker)
                    {
                        out.print("<font class=\"labkey-error\">Error: That query no longer exists</font>");
                        return;
                    }

                    out.println("<table>\n");
                    out.println("  <tr>\n    <td><b>SQL</b></td>\n    <td style=\"padding-left: 20px;\"><b>SQL&nbsp;With&nbsp;Parameters</b></td>\n  </tr>\n");
                    out.println("  <tr>\n");
                    out.println("    <td>" + PageFlowUtil.filter(tracker.getSql(), true) + "</td>\n");
                    out.println("    <td style=\"padding-left: 20px;\">" + PageFlowUtil.filter(tracker.getSqlAndParameters(), true) + "</td>\n");
                    out.println("  </tr>\n");
                    out.println("</table>\n<br>\n");

                    if (tracker.canShowExecutionPlan())
                    {
                        out.println("<table>\n  <tr><td>");
                        ActionURL url = executeFactory.getActionURL(tracker.getSql());
                        out.println(PageFlowUtil.textLink("Show Execution Plan", url));
                        out.println("  </td></tr></table>\n<br>\n");
                    }

                    out.println("<table>\n");
                    tracker.renderStackTraces(out);
                    out.println("</table>\n");
                }
            }
        };
    }


    public HttpView getExecutionPlanView(int hashCode)
    {
        SQLFragment sql;
        DbScope scope;

        // Don't update anything while we're gathering the SQL and parameters
        synchronized (_lock)
        {
            QueryTracker tracker = findTracker(hashCode);

            if (null == tracker)
                return new HtmlView("<font class=\"labkey-error\">Error: That query no longer exists</font>");

            if (!tracker.canShowExecutionPlan())
                throw new IllegalStateException("Can't show the execution plan for this query");

            scope = tracker.getScope();

            if (null == scope)
                throw new IllegalStateException("Scope should not be null");

            sql = tracker.getSQLFragment();
        }

        Collection<String> executionPlan = scope.getSqlDialect().getExecutionPlan(scope, sql);

        String fullPlan = StringUtils.join(executionPlan, "\n");

        return new HtmlView("<pre>" + PageFlowUtil.filter(fullPlan, true) + "</pre>");
    }


    private @Nullable QueryTracker findTracker(int hashCode)
    {
        QueryTracker tracker = null;

        for (QueryTracker candidate : _queries.values())
        {
            if (candidate.hashCode() == hashCode)
            {
                tracker = candidate;
                break;
            }
        }

        return tracker;
    }

    /**
     * Makes sure that the environment from each registered DatabaseQueryListener is available via
     * QueryService.get().getEnvironment(). Will preserve the existing environment if it has already been registered.
     */
    public void ensureListenerEnvironment()
    {
        // The environment is stored in a ThreadLocal, so there's no need for synchronization on the get/set combination
        if (QueryService.get().getEnvironment(QueryService.Environment.LISTENER_ENVIRONMENTS) == null)
        {
            Map<DatabaseQueryListener, Object> listenerEnvironment = new HashMap<>();
            QueryService.get().setEnvironment(QueryService.Environment.LISTENER_ENVIRONMENTS, listenerEnvironment);     // Set environment here to avoid stack overflow (Issue #22277)
            for (DatabaseQueryListener listener : _listeners)
            {
                listenerEnvironment.put(listener, listener.getEnvironment());
            }
        }
    }

    public Collection<QueryTrackerSet> getTrackerSets()
    {
        return _trackerSets;
    }

    public static class QueryStatTsvWriter extends TSVWriter
    {
        protected void write()
        {
            QueryTrackerSet export = new InvocationQueryTrackerSet() {
                protected int getLimit()
                {
                    return Integer.MAX_VALUE;
                }
            };

            // Don't update anything while we're rendering the report or vice versa
            synchronized (getInstance()._lock)
            {
                getInstance().getTrackerSets()
                    .stream()
                    .filter(QueryTrackerSet::shouldDisplay)
                    .forEach(export::addAll);

                long upTime = 0;
                RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
                if (runtimeBean != null)
                {
                    upTime = runtimeBean.getUptime() - getInstance()._upTimeAtLastReset;
                    upTime = upTime - (upTime % 1000);
                }
                _pw.printf("#Summary - unique queries: %,d, elapsed time: %s\n", getInstance()._uniqueQueryCountEstimate, DateUtil.formatDuration(upTime));

                int requests = ViewServlet.getRequestCount() - getInstance()._requestCountAtLastReset;
                _pw.printf("#HTTP Requests - query count: %,d, query time (ms): %,d, request count: %d\n", getInstance()._requestQueryCount, getInstance()._requestQueryTime, requests);
                _pw.printf("#Background Threads - query count: %,d, query time (ms): %,d\n", getInstance()._backgroundQueryCount, getInstance()._backgroundQueryTime);

                QueryTracker.exportRowHeader(_pw);

                for (QueryTracker tracker : export.descendingSet())
                    tracker.exportRow(_pw);
            }
        }
    }

    private class QueryProfilerThread extends Thread implements ShutdownListener
    {
        private QueryProfilerThread()
        {
            setDaemon(true);
            setName(QueryProfilerThread.class.getSimpleName());
        }

        @Override
        public void run()
        {
            try
            {
                //noinspection InfiniteLoopStatement
                while (!interrupted())
                {
                    Query query = _queue.take();

                    // Don't update or add while we're rendering the report or vice versa
                    synchronized (_lock)
                    {
                        if (query.isRequestThread())
                        {
                            _requestQueryCount++;
                            _requestQueryTime += query.getElapsed();
                        }
                        else
                        {
                            _backgroundQueryCount++;
                            _backgroundQueryTime += query.getElapsed();
                        }

                        QueryTracker tracker = _queries.get(query.getSql());

                        if (null == tracker)
                        {
                            tracker = new QueryTracker(query.getScope(), query.getSql(), query.getElapsed(), query.getStackTrace(), query.isValidSql());

                            // First instance of this query, so always save its parameters
                            tracker.setParameters(query.getParameters());

                            _uniqueQueryCountEstimate++;

                            for (QueryTrackerSet set : getTrackerSets())
                                set.add(tracker);

                            _queries.put(query.getSql(), tracker);
                        }
                        else
                        {
                            for (QueryTrackerSet set : getTrackerSets())
                                set.beforeUpdate(tracker);

                            tracker.addInvocation(query.getElapsed(), query.getStackTrace());

                            for (QueryTrackerSet set : getTrackerSets())
                                set.update(tracker);

                            // Save the parameters of the longest running query
                            if (tracker.getMax() == query.getElapsed())
                                tracker.setParameters(query.getParameters());
                        }
                    }
                }
            }
            catch (InterruptedException e)
            {
                LOG.debug(getClass().getSimpleName() + " is terminating due to interruption");
            }
        }

        // stupid tomcat won't let me construct one of these at shutdown, so stash one statically
        private final QueryStatTsvWriter shutdownWriter = new QueryStatTsvWriter();


        public void shutdownPre()
        {
            interrupt();
        }

        public void shutdownStarted()
        {
            Logger logger = Logger.getLogger(QueryProfilerThread.class);

            if (null != logger)
            {
                LOG.info("Starting to log statistics for queries prior to web application shut down");
                Appender appender = logger.getAppender("QUERY_STATS");
                if (null != appender && appender instanceof RollingFileAppender)
                    ((RollingFileAppender)appender).rollOver();
                else
                    LOG.warn("Could not rollover the query stats tsv file--there was no appender named QUERY_STATS, or it is not a RollingFileAppender.");

                try (PrintWriter logWriter = new LogPrintWriter(logger, Level.INFO))
                {
                    shutdownWriter.write(logWriter);
                }
                catch (IOException e)
                {
                    LOG.error("Exception writing query stats", e);
                }
                LOG.info("Completed logging statistics for queries prior to web application shut down");
            }
        }
    }

    // Static class since we use this in a couple places
    private static class InvocationQueryTrackerSet extends QueryTrackerSet
    {
        InvocationQueryTrackerSet()
        {
            super("Count", "highest number of invocations", false, true, new QueryTrackerComparator()
            {
                long getPrimaryStatisticValue(QueryTracker qt)
                {
                    return qt.getCount();
                }

                long getSecondaryStatisticValue(QueryTracker qt)
                {
                    return qt.getCumulative();
                }
            });
        }
    }

    public interface ActionURLFactory
    {
        ActionURL getActionURL(String name);
    }
}
