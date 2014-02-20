/*
 * Copyright (c) 2009-2014 LabKey Corporation
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

import org.apache.commons.collections15.map.ReferenceMap;
import org.apache.log4j.Appender;
import org.apache.log4j.Logger;
import org.apache.log4j.RollingFileAppender;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TSVWriter;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.Formats;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.ShutdownListener;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewServlet;

import javax.servlet.ServletContextEvent;
import java.io.IOException;
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

    private BlockingQueue<Query> _queue = new LinkedBlockingQueue<>(1000);
    private Map<String, QueryTracker> _queries = new ReferenceMap<>(ReferenceMap.HARD, ReferenceMap.WEAK);
    private final Object _lock = new Object();
    /* package */ Collection<QueryTrackerSet> _trackerSets = new ArrayList<>();

    // All access to these guarded by LOCK
    private long _requestQueryCount;
    private long _requestQueryTime;
    private long _backgroundQueryCount;
    private long _backgroundQueryTime;
    private long _uniqueQueryCountEstimate;  // This is a ceiling; true unique count is likely less than this since we're limiting capacity
    private int _requestCountAtLastReset;
    private long _upTimeAtLastReset;
    private boolean _hasBeenReset = false;

    private List<Pair<String, DatabaseQueryListener>> _listeners = new CopyOnWriteArrayList<>();

    public static QueryProfiler getInstance()
    {
        return INSTANCE;
    }

    private QueryProfiler()
    {
        _trackerSets.add(new InvocationQueryTrackerSet());

        _trackerSets.add(new QueryTrackerSet("Total", "highest cumulative execution time", false, true, new QueryTrackerComparator()
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

        _trackerSets.add(new QueryTrackerSet("Avg", "highest average execution time", false, true, new QueryTrackerComparator()
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

        _trackerSets.add(new QueryTrackerSet("Max", "highest maximum execution time", false, true, new QueryTrackerComparator()
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

        _trackerSets.add(new QueryTrackerSet("Last", "most recent invocation time", false, true, new QueryTrackerComparator()
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

        // Not displayed, but gives new queries some time to get above one of the other thresholds.  Without this,
        // the first N unique queries would dominate the statistics.
        _trackerSets.add(new QueryTrackerSet("First", "first invocation time", true, false, new QueryTrackerComparator()
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
        final QueryProfilerThread thread = new QueryProfilerThread();
        thread.start();

        // It's a daemon thread, but add a shutdown listener so we don't leave it running if the webapp is redeployed
        ContextListener.addShutdownListener(new ShutdownListener()
        {
            @Override
            public void shutdownPre(ServletContextEvent servletContextEvent)
            {
                thread.interrupt();
            }

            @Override
            public void shutdownStarted(ServletContextEvent servletContextEvent) {}
        });

    }

    public void addListener(String substring, DatabaseQueryListener listener)
    {
        _listeners.add(new Pair<>(substring, listener));
    }

    public void track(@Nullable DbScope scope, String sql, @Nullable List<Object> parameters, long elapsed, @Nullable StackTraceElement[] stackTrace, boolean requestThread)
    {
        if (null == stackTrace)
            stackTrace = Thread.currentThread().getStackTrace();

        for (Pair<String, DatabaseQueryListener> listener : _listeners)
        {
            if (sql.contains(listener.getKey()))
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
                    listenerEnvironment = listenersEnvironment.get(listener.getValue());
                }
                listener.getValue().queryInvoked(scope, sql,
                        (User) QueryService.get().getEnvironment(QueryService.Environment.USER),
                        (Container)QueryService.get().getEnvironment(QueryService.Environment.CONTAINER), listenerEnvironment);
            }
        }

        // Don't block if queue is full
        _queue.offer(new Query(scope, sql, parameters, elapsed, stackTrace, requestThread));
    }

    public void resetAllStatistics()
    {
        synchronized (_lock)
        {
            for (QueryTrackerSet set : _trackerSets)
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

    public HttpView getReportView(String statName, String buttonHTML, ActionURLFactory captionURLFactory, ActionURLFactory stackTraceURLFactory)
    {
        for (QueryTrackerSet set : _trackerSets)
        {
            if (set.getCaption().equals(statName))
            {
                StringBuilder sb = new StringBuilder();

                sb.append("\n<table>\n");

                StringBuilder rows = new StringBuilder();

                // Don't update anything while we're rendering the report or vice versa
                synchronized (_lock)
                {
                    int requests = ViewServlet.getRequestCount() - _requestCountAtLastReset;

                    sb.append("  <tr><td>").append(buttonHTML).append("</td></tr>\n");

                    sb.append("  <tr><td style=\"border-top:1px solid\" colspan=5 align=center>Queries Executed Within HTTP Requests</td></tr>\n");
                    sb.append("  <tr><td>Query Count:</td><td align=\"right\">").append(Formats.commaf0.format(_requestQueryCount)).append("</td>");
                    sb.append("<td width=10>&nbsp;</td>");
                    sb.append("<td>Query Time:</td><td align=\"right\">").append(Formats.commaf0.format(_requestQueryTime)).append("</td>");
                    sb.append("</tr>\n  <tr>");
                    sb.append("<td>Queries per Request:</td><td align=\"right\">").append(Formats.f1.format((double) _requestQueryCount / requests)).append("</td>");
                    sb.append("<td width=10>&nbsp;</td>");
                    sb.append("<td>Query Time per Request:</td><td align=\"right\">").append(Formats.f1.format((double) _requestQueryTime / requests)).append("</td>");
                    sb.append("</tr>\n  <tr>");
                    sb.append("<td>").append(_hasBeenReset ? "Request Count Since Last Reset" : "Request Count").append(":</td><td align=\"right\">").append(Formats.commaf0.format(requests)).append("</td></tr>\n");
                    sb.append("  <tr><td style=\"border-top:1px solid\" colspan=5>&nbsp;</td></tr>\n");

                    sb.append("  <tr><td style=\"border-top:1px solid\" colspan=5 align=center>Queries Executed Within Background Threads</td></tr>\n");
                    sb.append("  <tr><td>Query Count:</td><td align=\"right\">").append(Formats.commaf0.format(_backgroundQueryCount)).append("</td>");
                    sb.append("<td width=10>&nbsp;</td>");
                    sb.append("<td>Query Time:</td><td align=\"right\">").append(Formats.commaf0.format(_backgroundQueryTime)).append("</td>");
                    sb.append("</tr>\n");
                    sb.append("  <tr><td style=\"border-top:1px solid\" colspan=5>&nbsp;</td></tr>\n");
                    sb.append("  <tr><td colspan=5>&nbsp;</td></tr>\n");

                    sb.append("  <tr><td>Total Unique Queries");

                    if (_uniqueQueryCountEstimate > QueryTrackerSet.STANDARD_LIMIT)
                        sb.append(" (Estimate)");

                    sb.append(":</td><td align=\"right\">").append(Formats.commaf0.format(_uniqueQueryCountEstimate)).append("</td>");
                    sb.append("<td width=10>&nbsp;</td>");

                    RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
                    if (runtimeBean != null)
                    {
                        long upTime = runtimeBean.getUptime() - _upTimeAtLastReset;
                        upTime = upTime - (upTime % 1000);
                        sb.append("<td>").append(_hasBeenReset ? "Elapsed Time Since Last Reset" : "Server Uptime").append(":</td><td align=\"right\">").append(DateUtil.formatDuration(upTime)).append("</td>");
                    }
                    sb.append("</tr>\n");
                    sb.append("</table><br><br>\n");

                    sb.append("<table>\n");
                    sb.append("  <tr><td>").append("Unique queries with the ").append(set.getDescription()).append(" (top ").append(Formats.commaf0.format(set.size())).append("):</td></tr>\n");
                    sb.append("</table><br>\n");

                    int row = 0;
                    for (QueryTracker tracker : set)
                        tracker.insertRow(rows, (0 == (++row) % 2) ? "labkey-alternate-row" : "labkey-row", stackTraceURLFactory);
                }

                sb.append("<table cellspacing=0 cellpadding=3>\n");
                QueryTracker.appendRowHeader(sb, set, captionURLFactory);
                sb.append(rows);
                sb.append("</table>\n");

                return new HtmlView(sb.toString());
            }
        }

        return new HtmlView("<font class=\"labkey-error\">Error: Query statistic \"" + PageFlowUtil.filter(statName) + "\" does not exist</font>");
    }


    public HttpView getStackTraceView(int hashCode, ActionURLFactory executeFactory)
    {
        // Don't update anything while we're rendering the report or vice versa
        synchronized (_lock)
        {
            QueryTracker tracker = findTracker(hashCode);

            if (null == tracker)
                return new HtmlView("<font class=\"labkey-error\">Error: That query no longer exists</font>");

            StringBuilder sb = new StringBuilder();

            sb.append("<table>\n");
            sb.append("  <tr>\n    <td><b>SQL</b></td>\n    <td style=\"padding-left: 20px;\"><b>SQL&nbsp;With&nbsp;Parameters</b></td>\n  </tr>\n");
            sb.append("  <tr>\n");
            sb.append("    <td>").append(PageFlowUtil.filter(tracker.getSql(), true)).append("</td>\n");
            sb.append("    <td style=\"padding-left: 20px;\">").append(PageFlowUtil.filter(tracker.getSqlAndParameters(), true)).append("</td>\n");
            sb.append("  </tr>\n");
            sb.append("</table>\n<br>\n");

            if (tracker.canShowExecutionPlan())
            {
                sb.append("<table>\n  <tr><td>");
                ActionURL url = executeFactory.getActionURL(tracker.getSql());
                sb.append(PageFlowUtil.textLink("Show Execution Plan", url));
                sb.append("  </td></tr></table>\n<br>\n");
            }

            sb.append("<table>\n");
            tracker.appendStackTraces(sb);
            sb.append("</table>\n");

            return new HtmlView(sb.toString());
        }
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
        StringBuilder html = new StringBuilder();

        for (String row : executionPlan)
        {
            html.append(PageFlowUtil.filter(row, true));
            html.append("</br>\n");
        }

        return new HtmlView(html.toString());
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
        if (QueryService.get().getEnvironment(QueryService.Environment.LISTENER_ENVIRONMENTS) == null)
        {
            Map<DatabaseQueryListener, Object> listenerEnvironment = new HashMap<>();
            for (Pair<String, DatabaseQueryListener> entry : _listeners)
            {
                DatabaseQueryListener listener = entry.getValue();
                listenerEnvironment.put(listener, listener.getEnvironment());
            }
            QueryService.get().setEnvironment(QueryService.Environment.LISTENER_ENVIRONMENTS, listenerEnvironment);
        }
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

            StringBuilder rows = new StringBuilder();

            // Don't update anything while we're rendering the report or vice versa
            synchronized (getInstance()._lock)
            {
                for (QueryTrackerSet set : getInstance()._trackerSets)
                    if (set.shouldDisplay())
                        export.addAll(set);

                for (QueryTracker tracker : export)
                    tracker.exportRow(rows);

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
                _pw.println(rows);
            }
        }
    }

    private class QueryProfilerThread extends Thread implements ShutdownListener
    {
        private QueryProfilerThread()
        {
            setDaemon(true);
            setName(QueryProfilerThread.class.getSimpleName());
            ContextListener.addShutdownListener(this);
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

                            for (QueryTrackerSet set : _trackerSets)
                                set.add(tracker);

                            _queries.put(query.getSql(), tracker);
                        }
                        else
                        {
                            for (QueryTrackerSet set : _trackerSets)
                                set.beforeUpdate(tracker);

                            tracker.addInvocation(query.getElapsed(), query.getStackTrace());

                            for (QueryTrackerSet set : _trackerSets)
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
        private final QueryProfiler.QueryStatTsvWriter shutdownWriter = new QueryProfiler.QueryStatTsvWriter();


        public void shutdownPre(ServletContextEvent servletContextEvent)
        {
            interrupt();
        }

        public void shutdownStarted(ServletContextEvent servletContextEvent)
        {
            Logger logger = Logger.getLogger(QueryProfilerThread.class);

            if (null != logger)
            {
                Appender appender = logger.getAppender("QUERY_STATS");
                if (null != appender && appender instanceof RollingFileAppender)
                    ((RollingFileAppender)appender).rollOver();
                else
                    LOG.warn("Could not rollover the query stats tsv file--there was no appender named QUERY_STATS, or it is not a RollingFileAppender.");

                StringBuilder buf = new StringBuilder();

                try
                {
                    shutdownWriter.write(buf);
                }
                catch (IOException e)
                {
                    LOG.error("Exception writing query stats", e);
                }

                logger.info(buf.toString());
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
