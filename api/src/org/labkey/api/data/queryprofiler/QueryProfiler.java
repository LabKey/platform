/*
 * Copyright (c) 2014-2019 LabKey Corporation
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
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.QueryLogging;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TSVWriter;
import org.labkey.api.data.dialect.SqlDialect.ExecutionPlanType;
import org.labkey.api.miniprofiler.MiniProfiler;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.DOM;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.Formats;
import org.labkey.api.util.HashHelpers;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.Link;
import org.labkey.api.util.LogPrintWriter;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ShutdownListener;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewServlet;
import org.labkey.api.view.template.ClientDependency;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

public class QueryProfiler
{
    private static final Logger LOG = LogHelper.getLogger(QueryProfiler.class, "Tracks SQL query execution and duration");
    private static final QueryProfiler INSTANCE = new QueryProfiler();

    /** Limit to the size of queries that we keep in memory */
    private static final int LONG_SQL_LIMIT = 100_000;
    /** How many queries longer than our threshold above to keep around at a time */
    private static final int MAX_LONG_SQL_QUERIES = 5;
    private final List<WeakReference<QueryTracker>> _longSqlTextQueries = new ArrayList<>();

    private final BlockingQueue<Query> _queue = new LinkedBlockingQueue<>(1000);
    /** Hash of SQL -> query tracking info */
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
            @Override
            long getPrimaryStatisticValue(QueryTracker qt)
            {
                return qt.getCumulative();
            }

            @Override
            long getSecondaryStatisticValue(QueryTracker qt)
            {
                return qt.getCount();
            }
        }));

        getTrackerSets().add(new QueryTrackerSet("Avg", "highest average execution time", false, true, new QueryTrackerComparator()
        {
            @Override
            long getPrimaryStatisticValue(QueryTracker qt)
            {
                return qt.getAverage();
            }

            @Override
            long getSecondaryStatisticValue(QueryTracker qt)
            {
                return qt.getCumulative();
            }
        }));

        getTrackerSets().add(new QueryTrackerSet("Max", "highest maximum execution time", false, true, new QueryTrackerComparator()
        {
            @Override
            long getPrimaryStatisticValue(QueryTracker qt)
            {
                return qt.getMax();
            }

            @Override
            long getSecondaryStatisticValue(QueryTracker qt)
            {
                return qt.getCumulative();
            }
        }));

        getTrackerSets().add(new QueryTrackerSet("Last", "most recent invocation time", false, true, new QueryTrackerComparator()
        {
            @Override
            long getPrimaryStatisticValue(QueryTracker qt)
            {
                return qt.getLastInvocation();
            }

            @Override
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
            @Override
            long getPrimaryStatisticValue(QueryTracker qt)
            {
                return qt.getFirstInvocation();
            }

            @Override
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

    @Nullable
    public StackTraceElement[] track(@Nullable DbScope scope, String sql, @Nullable List<Object> parameters, long elapsed,
          @Nullable StackTraceElement[] stackTrace, boolean requestThread, QueryLogging queryLogging)
    {
        if (null == stackTrace)
            stackTrace = MiniProfiler.getTroubleshootingStackTrace();

        for (DatabaseQueryListener listener : _listeners)
        {
            if (listener.matches(scope, sql, queryLogging))
            {
                listener.queryInvoked(scope, sql,
                        (User) QueryService.get().getEnvironment(QueryService.Environment.USER),
                        (Container)QueryService.get().getEnvironment(QueryService.Environment.CONTAINER), queryLogging);
            }
        }

        MiniProfiler.addQuery(elapsed, sql, stackTrace);

        // Don't block if queue is full
        _queue.offer(new Query(scope, sql, parameters, elapsed, stackTrace, requestThread));
        return stackTrace;
    }

    public void resetAllStatistics()
    {
        synchronized (_lock)
        {
            for (QueryTrackerSet set : getTrackerSets())
                set.clear();

            _queries.clear();
            _longSqlTextQueries.clear();

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

    private class ReportView extends HttpView<Object>
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

                        out.println("<table class=\"labkey-data-region-legacy labkey-show-borders labkey-data-region-header-lock\">");
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

    public HttpView<?> getReportView(String statName, String buttonHTML, ActionURLFactory captionURLFactory, ActionURLFactory stackTraceURLFactory)
    {
        return new ReportView(statName, buttonHTML, captionURLFactory, stackTraceURLFactory);
    }

    public HttpView<?> getStackTraceView(final String sqlHash, final ActionURLFactory executeFactory)
    {
        // Don't update anything while we're rendering the report or vice versa
        synchronized (_lock)
        {
            QueryTracker tracker = _queries.get(sqlHash);

            if (null == tracker)
            {
                return new HtmlView(DOM.DIV(DOM.cl("labkey-error"), "Error: That query no longer exists"));
            }

            HttpView<?> result = new HtmlView(DOM.DIV(
                DOM.TABLE(
                    DOM.at(DOM.Attribute.style, "width: 100%"),
                    DOM.TR(
                        DOM.TD(DOM.at(DOM.Attribute.style, "white-space: nowrap; width: 50%;"), DOM.STRONG("SQL" + (tracker.isTruncated() ? " (truncated)" : ""))),
                        DOM.TD(DOM.at(DOM.Attribute.style, "white-space: nowrap; width: 50%;"), DOM.STRONG("SQL With Parameters" + (tracker.isTruncated() ? " (truncated)" : "")))
                    ),
                    DOM.TR(
                        DOM.TD(copyToClipboardLink("copyToClipboardNoParams", "sqlNoParams")),
                        DOM.TD(copyToClipboardLink("copyToClipboardWithParams", "sqlWithParams"))
                    ),
                    DOM.TR(
                        DOM.TD(DOM.at(DOM.Attribute.id, "sqlNoParams"), HtmlString.of(tracker.getSql(), true)),
                        DOM.TD(DOM.at(DOM.Attribute.id, "sqlWithParams"), HtmlString.of(tracker.getSqlAndParameters(), true))
                    )
                ),

                DOM.SCRIPT(HtmlString.unsafe("new Clipboard('#copyToClipboardNoParams');new Clipboard('#copyToClipboardWithParams');")),
                DOM.BR(),
                Arrays.stream(ExecutionPlanType.values()).
                    filter(tracker::canShowExecutionPlan).
                    map(type -> DOM.DIV(
                        new Link.LinkBuilder("Show " + type.getDescription()).
                            href(executeFactory.getActionURL(tracker.getHash()).addParameter("type", type.name())).build(),
                        ExecutionPlanType.Actual == type ? DOM.DIV(new Link.LinkBuilder("Log " + type.getDescription() + " to primary site log").
                            href(executeFactory.getActionURL(tracker.getHash()).addParameter("type", type.name()).addParameter("log", true)).build()) : null
                    )),
                DOM.BR(),
                tracker.renderStackTraces()
            ));
            result.addClientDependencies(Set.of(ClientDependency.fromPath("internal/clipboard/clipboard-1.5.9.min.js")));
            return result;
        }
    }

    private Link copyToClipboardLink(String linkId, String targetId)
    {
        return new Link.LinkBuilder("copy to clipboard").
            onClick("return false;").
            id(linkId).
            attributes(Collections.singletonMap("data-clipboard-target", "#" + targetId)).
            build();
    }

    public HttpView<?> getExecutionPlanView(String sqlHash, ExecutionPlanType type, boolean log)
    {
        SQLFragment sql;
        String sqlWithParameters;
        DbScope scope;

        // Don't update anything while we're gathering the SQL and parameters
        synchronized (_lock)
        {
            QueryTracker tracker = _queries.get(sqlHash);

            if (null == tracker)
                return new HtmlView(DOM.P(DOM.cl("labkey-error"), "Error: That query no longer exists"));

            if (!tracker.canShowExecutionPlan(type))
                throw new NotFoundException("Can't show the \"" + type.name() + "\" execution plan for this query");

            scope = tracker.getScope();

            if (null == scope)
                throw new IllegalStateException("Scope should not be null");

            sql = tracker.getSQLFragment();
            sqlWithParameters = tracker.getSqlAndParameters() + "\n";
        }

        Collection<String> executionPlan = scope.getSqlDialect().getExecutionPlan(scope, sql, type);
        String fullPlan = StringUtils.join(executionPlan, "\n");
        final HttpView<?> view;

        if (log)
        {
            // The log option is useful for retrieving actual timing information about very long-running queries when
            // proxy timeouts prevent viewing the plan via the web page
            LOG.info("An administrator initiated the logging of this query execution plan with actual timing:\n" + sqlWithParameters + fullPlan);
            view = new HtmlView(HtmlString.of("Execution plan with actual timing was logged to the primary site log file"));
        }
        else
        {
            view = new HtmlView(
                DOM.DIV(
                    DOM.DIV(copyToClipboardLink("copyToClipboard", "executionPlan")),
                    DOM.PRE(DOM.at(DOM.Attribute.id, "executionPlan"), sqlWithParameters, "\n\n", fullPlan),
                    DOM.SCRIPT(HtmlString.unsafe("new Clipboard('#copyToClipboard');"))
                )
            );
            view.addClientDependencies(Set.of(ClientDependency.fromPath("internal/clipboard/clipboard-1.5.9.min.js")));
        }

        return view;
    }

    public Collection<QueryTrackerSet> getTrackerSets()
    {
        return _trackerSets;
    }

    public static class QueryStatTsvWriter extends TSVWriter
    {
        @Override
        protected int write()
        {
            QueryTrackerSet export = new InvocationQueryTrackerSet() {
                @Override
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
                    tracker.exportRow(this);
            }

            return export.size();
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

                        String sql = query.getSql();
                        String hash = HashHelpers.hash(sql);
                        QueryTracker tracker = _queries.get(hash);

                        if (null == tracker)
                        {
                            tracker = new QueryTracker(query.getScope(), sql, hash, query.getElapsed(), query.getStackTrace(), query.isValidSql(), query.isTruncated());
                            if (sql.length() > LONG_SQL_LIMIT)
                            {
                                manageLongSql(tracker);
                            }

                            // First instance of this query, so always save its parameters
                            tracker.setParameters(query.getParameters());

                            _uniqueQueryCountEstimate++;

                            for (QueryTrackerSet set : getTrackerSets())
                                set.add(tracker);

                            _queries.put(hash, tracker);
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

        private void manageLongSql(QueryTracker tracker)
        {
            synchronized (_lock)
            {
                while (_longSqlTextQueries.size() >= MAX_LONG_SQL_QUERIES)
                {
                    WeakReference<QueryTracker> ref = _longSqlTextQueries.remove(0);
                    QueryTracker oldTracker = ref.get();
                    if (oldTracker != null)
                    {
                        oldTracker.truncate(LONG_SQL_LIMIT);
                    }
                }
                _longSqlTextQueries.add(new WeakReference<>(tracker));
            }
        }

        // stupid tomcat won't let me construct one of these at shutdown, so stash one statically
        private final QueryStatTsvWriter shutdownWriter = new QueryStatTsvWriter();

        @Override
        public void shutdownPre()
        {
            interrupt();
        }

        @Override
        public void shutdownStarted()
        {
            Logger logger = LogManager.getLogger(QueryProfilerThread.class);

            if (null != logger)
            {
//                LOG.info("Starting to log statistics for queries prior to web application shut down");
//                LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
//                Configuration config = ctx.getConfiguration();
//                Appender appender = config.getAppender("QUERY_STATS");
//                if (null != appender && appender instanceof RollingFileAppender)
//                    ((RollingFileAppender)appender).rollOver();
//                else
//                    LOG.warn("Could not rollover the query stats tsv file--there was no appender named QUERY_STATS, or it is not a RollingFileAppender.");

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
                @Override
                long getPrimaryStatisticValue(QueryTracker qt)
                {
                    return qt.getCount();
                }

                @Override
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
