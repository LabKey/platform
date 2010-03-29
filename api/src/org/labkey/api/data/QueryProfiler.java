/*
 * Copyright (c) 2009-2010 LabKey Corporation
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

import org.apache.commons.collections15.map.ReferenceMap;
import org.apache.log4j.Appender;
import org.apache.log4j.Logger;
import org.apache.log4j.RollingFileAppender;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.util.*;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewServlet;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/*
* User: adam
* Date: Oct 14, 2009
* Time: 6:31:40 PM
*/
public class QueryProfiler
{
    private static final Logger LOG = Logger.getLogger(QueryProfiler.class);
    private static final BlockingQueue<Query> QUEUE = new LinkedBlockingQueue<Query>(1000);
    private static final QueryProfilerThread THREAD = new QueryProfilerThread();
    private static final Map<String, QueryTracker> QUERIES = new ReferenceMap<String, QueryTracker>(ReferenceMap.HARD, ReferenceMap.WEAK);
    private static final Object LOCK = new Object();
    private static final Collection<QueryTrackerSet> TRACKER_SETS = new ArrayList<QueryTrackerSet>();

    // All access to these guarded by LOCK
    private static long _requestQueryCount;
    private static long _requestQueryTime;
    private static long _backgroundQueryCount;
    private static long _backgroundQueryTime;
    private static long _uniqueQueryCountEstimate;  // This is a ceiling; true unique count is likely less than this since we're limiting capacity
    private static int _requestCountAtLastReset;
    private static long _upTimeAtLastReset;
    private static boolean _hasBeenReset = false;

    static
    {
        TRACKER_SETS.add(new InvocationQueryTrackerSet());

        TRACKER_SETS.add(new QueryTrackerSet("Cumulative", "highest cumulative execution time", false, true, new QueryTrackerComparator()
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

        TRACKER_SETS.add(new QueryTrackerSet("Average", "highest average execution time", false, true, new QueryTrackerComparator()
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

        TRACKER_SETS.add(new QueryTrackerSet("Max", "highest maximum execution time", false, true, new QueryTrackerComparator()
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

        // Not displayed, but gives new queries some time to get above one of the other thresholds.  Without this,
        // the first N unique queries would dominate the statistics.
        TRACKER_SETS.add(new QueryTrackerSet("FirstInvocation", "most recent invocation time", true, false, new QueryTrackerComparator()
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
        THREAD.start();
    }

    private QueryProfiler()
    {
    }

    static void track(String sql, long elapsed)
    {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        boolean isRequestThread = ViewServlet.isRequestThread();

        // Don't block if queue is full
        QUEUE.offer(new Query(sql, elapsed, stackTrace, isRequestThread));
    }

    public static void resetAllStatistics()
    {
        synchronized (LOCK)
        {
            for (QueryTrackerSet set : TRACKER_SETS)
                set.clear();

            QUERIES.clear();

            initializeCounters();

            _hasBeenReset = true;
        }
    }

    private static void initializeCounters()
    {
        synchronized (LOCK)
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

    public static HttpView getReportView(String statName, String buttonHTML, ActionURLFactory captionURLFactory, ActionURLFactory stackTraceURLFactory)
    {
        for (QueryTrackerSet set : TRACKER_SETS)
        {
            if (set.getCaption().equals(statName))
            {
                StringBuilder sb = new StringBuilder();

                sb.append("\n<table>\n");

                StringBuilder rows = new StringBuilder();

                // Don't update anything while we're rendering the report or vice versa
                synchronized (LOCK)
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
                    sb.append("</tr>\n  </tr>");
                    sb.append("<td>").append(_hasBeenReset ? "Request Count Since Last Reset" : "Request Count").append(":</td><td align=\"right\">").append(Formats.commaf0.format(requests)).append("</td>");
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


    public static HttpView getStackTraceView(int hashCode)
    {
        // Don't update anything while we're rendering the report or vice versa
        synchronized (LOCK)
        {
            QueryTracker tracker = null;

            for (QueryTracker candidate : QUERIES.values())
            {
                if (candidate.hashCode() == hashCode)
                {
                    tracker = candidate;
                    break;
                }
            }

            if (null == tracker)
                return new HtmlView("<font class=\"labkey-error\">Error: That query no longer exists</font>");

            StringBuilder sb = new StringBuilder();

            sb.append("<table>\n");
            sb.append("<tr><td><b>SQL</b></td></tr>\n");
            sb.append("<tr><td colspan=2>").append(PageFlowUtil.filter(tracker.getSql(), true)).append("<br><br></td></tr>");

            tracker.appendStackTraces(sb);

            sb.append("</table>\n");

            return new HtmlView(sb.toString());
        }
    }


    public static class QueryStatTsvWriter extends TSVWriter
    {
        public QueryStatTsvWriter()
        {
        }

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
            synchronized (LOCK)
            {
                for (QueryTrackerSet set : TRACKER_SETS)
                    if (set.shouldDisplay())
                        export.addAll(set);

                for (QueryTracker tracker : export)
                    tracker.exportRow(rows);

                QueryTracker.exportRowHeader(_pw);
                _pw.println(rows);
            }
        }
    }

    private static class Query
    {
        private final String _sql;
        private final long _elapsed;
        private final StackTraceElement[] _stackTrace;
        private final boolean _isRequestThread;

        private Query(String sql, long elapsed, StackTraceElement[] stackTrace, boolean isRequestThread)
        {
            _sql = sql;
            _elapsed = elapsed;
            _stackTrace = stackTrace;
            _isRequestThread = isRequestThread;
        }

        private String getSql()
        {
            return _sql;
        }

        private long getElapsed()
        {
            return _elapsed;
        }

        private String getStackTrace()
        {
            StringBuilder sb = new StringBuilder();

            for (int i = 3; i < _stackTrace.length; i++)
            {
                sb.append(_stackTrace[i]);
                sb.append('\n');
            }

            return sb.toString();
        }

        public boolean isRequestThread()
        {
            return _isRequestThread;
        }
    }

    private static class QueryTracker
    {
        private final String _sql;
        private final long _firstInvocation;
        private final Map<String, AtomicInteger> _stackTraces = new HashMap<String, AtomicInteger>();

        private long _count = 0;
        private long _max = 0;
        private long _cumulative = 0;

        private QueryTracker(@NotNull String sql, long elapsed, String stackTrace)
        {
            _sql = sql;
            _firstInvocation = System.currentTimeMillis();

            addInvocation(elapsed, stackTrace);
        }

        private void addInvocation(long elapsed, String stackTrace)
        {
            _count++;
            _cumulative += elapsed;

            if (elapsed > _max)
                _max = elapsed;

            AtomicInteger frequency = _stackTraces.get(stackTrace);

            if (null == frequency)
                _stackTraces.put(stackTrace, new AtomicInteger(1));
            else
                frequency.incrementAndGet();
        }

        public String getSql()
        {
            return _sql;
        }

        public long getCount()
        {
            return _count;
        }

        public long getMax()
        {
            return _max;
        }

        public long getCumulative()
        {
            return _cumulative;
        }

        public long getFirstInvocation()
        {
            return _firstInvocation;
        }

        public long getAverage()
        {
            return _cumulative / _count;
        }

        public int getStackTraceCount()
        {
            return _stackTraces.size();
        }

        public void appendStackTraces(StringBuilder sb)
        {
            // Descending order by occurrences (the key)
            Set<Map.Entry<String, AtomicInteger>> set = new TreeSet<Map.Entry<String, AtomicInteger>>(new Comparator<Map.Entry<String, AtomicInteger>>() {
                public int compare(Map.Entry<String, AtomicInteger> e1, Map.Entry<String, AtomicInteger> e2)
                {
                    int compare = e2.getValue().intValue() - e1.getValue().intValue();

                    if (0 == compare)
                        compare = e2.getKey().compareTo(e1.getKey());

                    return compare;
                }
            });

            set.addAll(_stackTraces.entrySet());

            int commonLength = 0;
            String formattedCommonPrefix = "";

            if (set.size() > 1)
            {
                String commonPrefix = StringUtils.findCommonPrefix(_stackTraces.keySet());
                commonLength = commonPrefix.lastIndexOf('\n');
                formattedCommonPrefix = "<b>" + PageFlowUtil.filter(commonPrefix.substring(0, commonLength), true) + "</b>";
            }

            sb.append("<tr><td>").append("<b>Occurrences</b>").append("</td><td style=\"padding-left:10;\">").append("<b>Stack&nbsp;Traces</b>").append("</td></tr>\n");

            int alt = 0;
            String[] classes = new String[]{"labkey-alternate-row", "labkey-row"};

            for (Map.Entry<String, AtomicInteger> entry : set)
            {
                String stackTrace = entry.getKey();
                String formattedStackTrace = formattedCommonPrefix + PageFlowUtil.filter(stackTrace.substring(commonLength), true);
                int count = entry.getValue().get();

                sb.append("<tr class=\"").append(classes[alt]).append("\"><td valign=top align=right>").append(count).append("</td><td style=\"padding-left:10;\">").append(formattedStackTrace).append("</td></tr>\n");
                alt = 1 - alt;
            }
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            QueryTracker that = (QueryTracker) o;

            return _sql.equals(that._sql);
        }

        @Override
        public int hashCode()
        {
            return _sql.hashCode();
        }

        private static void appendRowHeader(StringBuilder sb, QueryTrackerSet currentSet, ActionURLFactory factory)
        {
            sb.append("  <tr>");

            for (QueryTrackerSet set : TRACKER_SETS)
                if (set.shouldDisplay())
                    appendColumnHeader(set.getCaption(), set == currentSet, sb, factory);

            sb.append("<td>");
            sb.append("Stack&nbsp;Traces");
            sb.append("</td><td style=\"padding-left:10;\">");
            sb.append("SQL");
            sb.append("</td></tr>\n");
        }

        private static void appendColumnHeader(String name, boolean highlight, StringBuilder sb, ActionURLFactory factory)
        {
            sb.append("<td><a href=\"");
            sb.append(PageFlowUtil.filter(factory.getActionURL(name)));
            sb.append("\">");

            if (highlight)
                sb.append("<b>");

            sb.append(name);

            if (highlight)
                sb.append("</b>");

            sb.append("</a></td>");
        }

        private static void exportRowHeader(PrintWriter pw)
        {
            String tab = "";

            for (QueryTrackerSet set : TRACKER_SETS)
            {
                if (set.shouldDisplay())
                {
                    pw.print(tab);
                    pw.print(set.getCaption());
                    tab = "\t";
                }
            }

            pw.print(tab);
            pw.println("SQL");
        }

        private void insertRow(StringBuilder sb, String className, ActionURLFactory factory)
        {
            StringBuilder row = new StringBuilder();
            row.append("  <tr class=\"").append(className).append("\">");

            for (QueryTrackerSet set : TRACKER_SETS)
                if (set.shouldDisplay())
                    row.append("<td valign=top align=right>").append(Formats.commaf0.format(((QueryTrackerComparator)set.comparator()).getPrimaryStatisticValue(this))).append("</td>");

            ActionURL url = factory.getActionURL(getSql());
            row.append("<td valign=top align=right><a href=\"").append(PageFlowUtil.filter(url.getLocalURIString())).append("\">").append(Formats.commaf0.format(getStackTraceCount())).append("</a></td>");
            row.append("<td style=\"padding-left:10;\">").append(PageFlowUtil.filter(getSql(), true)).append("</td>");
            row.append("</tr>\n");
            sb.insert(0, row);
        }

        private void exportRow(StringBuilder sb)
        {
            StringBuilder row = new StringBuilder();
            String tab = "";

            for (QueryTrackerSet set : TRACKER_SETS)
            {
                if (set.shouldDisplay())
                {
                    row.append(tab).append(Formats.commaf0.format(((QueryTrackerComparator)set.comparator()).getPrimaryStatisticValue(this)));
                    tab = "\t";
                }
            }

            row.append(tab).append(getSql().trim().replaceAll("(\\s)+", " ")).append("\n");
            sb.insert(0, row);
        }
    }

    private static class QueryProfilerThread extends Thread implements ShutdownListener
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
                    Query query = QUEUE.take();

                    // Don't update or add while we're rendering the report or vice versa
                    synchronized (LOCK)
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

                        QueryTracker tracker = QUERIES.get(query.getSql());

                        if (null == tracker)
                        {
                            tracker = new QueryTracker(query.getSql(), query.getElapsed(), query.getStackTrace());

                            _uniqueQueryCountEstimate++;

                            for (QueryTrackerSet set : TRACKER_SETS)
                                set.add(tracker);

                            QUERIES.put(query.getSql(), tracker);
                        }
                        else
                        {
                            for (QueryTrackerSet set : TRACKER_SETS)
                                set.beforeUpdate(tracker);

                            tracker.addInvocation(query.getElapsed(), query.getStackTrace());

                            for (QueryTrackerSet set : TRACKER_SETS)
                                set.update(tracker);
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
        private static final QueryProfiler.QueryStatTsvWriter shutdownWriter = new QueryProfiler.QueryStatTsvWriter();


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
                catch (ServletException e)
                {
                    LOG.error("Exception writing query stats", e);
                }

                logger.info(buf.toString());
            }
        }
    }

    private static class QueryTrackerSet extends TreeSet<QueryTracker>
    {
        private static final int STANDARD_LIMIT = 1000;

        private final String _caption;
        private final String _description;
        private final boolean _stable;   // Is this statistic stable, i.e., will it never change once a QueryTracker has been added to the set?
        private final boolean _display;  // Should we display this statistic in the report?

        private QueryTrackerSet(String caption, String description, boolean stable, boolean display, Comparator<? super QueryTracker> comparator)
        {
            super(comparator);
            _caption = caption;
            _description = description;
            _display = display;
            _stable = stable;
        }

        private String getCaption()
        {
            return _caption;
        }

        private String getDescription()
        {
            return _description;
        }

        private boolean shouldDisplay()
        {
            return _display;
        }

        private void beforeUpdate(QueryTracker tracker)
        {
            // If the statistic changes at each update, then we need to remove and re-add it
            if (!_stable)
                remove(tracker);
        }

        private void update(QueryTracker tracker)
        {
            // If the statistic changes at each update, then we need to remove and re-add it
            if (!_stable)
                add(tracker);
        }

        protected int getLimit()
        {
            return STANDARD_LIMIT;
        }

        @Override
        public boolean add(QueryTracker tracker)
        {
            assert size() <= getLimit();

            if (size() == getLimit())
            {
                if (comparator().compare(tracker, first()) < 0)
                    return false;

                remove(first());
            }

            return super.add(tracker);
        }

        @Override
        public String toString()
        {
            return getCaption();
        }
    }

    // Static class since we use this in a couple places
    private static class InvocationQueryTrackerSet extends QueryTrackerSet
    {
        InvocationQueryTrackerSet()
        {
            super("Invocations", "highest number of invocations", false, true, new QueryTrackerComparator()
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

    // Comparator that allows defining a primary and secondary sort order, and ensures the Set
    // "consistent with equals" requirement.  If we didn't compare the sql, the set would reject new
    // queries where a statistic happens to match the value of that statistic in an existing query.
    private static abstract class QueryTrackerComparator implements Comparator<QueryTracker>
    {
        public int compare(QueryTracker qt1, QueryTracker qt2)
        {
            // Can use simple subtraction here since we won't have MAX_VALUE, MIN_VALUE, etc.
            int ret = Long.signum(getPrimaryStatisticValue(qt1) - getPrimaryStatisticValue(qt2));

            if (0 == ret)
                ret = Long.signum(getSecondaryStatisticValue(qt1) - getSecondaryStatisticValue(qt2));

            if (0 == ret)
                ret = qt1.getSql().compareTo(qt2.getSql());

            return ret;
        }

        abstract long getPrimaryStatisticValue(QueryTracker qt);
        abstract long getSecondaryStatisticValue(QueryTracker qt);
    }
}
