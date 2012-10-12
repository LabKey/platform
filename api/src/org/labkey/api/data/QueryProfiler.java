/*
 * Copyright (c) 2009-2012 LabKey Corporation
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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.ByteArrayHashKey;
import org.labkey.api.util.Compress;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.Formats;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.ShutdownListener;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewServlet;

import javax.servlet.ServletContextEvent;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;

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

    public static void track(String sql, @Nullable Collection<Object> parameters, long elapsed, @Nullable StackTraceElement[] stackTrace, boolean requestThread)
    {
        if (null == stackTrace)
            stackTrace = Thread.currentThread().getStackTrace();

        // Don't block if queue is full
        QUEUE.offer(new Query(sql, parameters, elapsed, stackTrace, requestThread));
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

                long upTime = 0;
                RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
                if (runtimeBean != null)
                {
                    upTime = runtimeBean.getUptime() - _upTimeAtLastReset;
                    upTime = upTime - (upTime % 1000);
                }
                _pw.printf("#Summary - unique queries: %,d, elapsed time: %s\n", _uniqueQueryCountEstimate, DateUtil.formatDuration(upTime));

                int requests = ViewServlet.getRequestCount() - _requestCountAtLastReset;
                _pw.printf("#HTTP Requests - query count: %,d, query time (ms): %,d, request count: %d\n", _requestQueryCount, _requestQueryTime, requests);
                _pw.printf("#Background Threads - query count: %,d, query time (ms): %,d\n", _backgroundQueryCount, _backgroundQueryTime);

                QueryTracker.exportRowHeader(_pw);
                _pw.println(rows);
            }
        }
    }

    private static class Query
    {
        private final String _sql;
        private final @Nullable Collection<Object> _parameters;
        private final long _elapsed;
        private final StackTraceElement[] _stackTrace;
        private final boolean _isRequestThread;

        private Query(String sql, @Nullable Collection<Object> parameters, long elapsed, StackTraceElement[] stackTrace, boolean isRequestThread)
        {
            _sql = sql;
            _parameters = null != parameters ? new ArrayList<Object>(parameters) : null;    // Make a copy... callers might modify the collection
            _elapsed = elapsed;
            _stackTrace = stackTrace;
            _isRequestThread = isRequestThread;
        }

        private String getSql()
        {
            // Do any transformations on the SQL on the way out, in the background thread
            return transform(_sql);
        }

        @Nullable
        private Collection<Object> getParameters()
        {
            return _parameters;  // TODO: Check parameters? Ignore InputStream, BLOBs, etc.?
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
                String line = _stackTrace[i].toString();

                // Ignore all the servlet container stuff, #11159
                // Ignore everything before HttpView.render, standard action classes, etc., #13753
                if  (
                        line.startsWith("org.labkey.api.view.HttpView.render") ||
                        line.startsWith("org.labkey.jsp.compiled.org.labkey.api.view.template.CommonTemplate_jsp._jspService") ||
                        line.startsWith("org.labkey.api.view.WebPartView.renderInternal") ||
                        line.startsWith("org.labkey.api.view.JspView.renderView") ||
                        line.startsWith("org.labkey.api.action.SimpleViewAction.handleRequest") ||
                        line.startsWith("org.labkey.api.action.FormViewAction.handleRequest") ||
                        line.startsWith("org.junit.internal.runners.TestMethodRunner.executeMethodBody") ||
                        line.startsWith("org.apache.catalina.core.ApplicationFilterChain.internalDoFilter") ||
                        line.startsWith("javax.servlet.http.HttpServlet.service")
                    )
                    break;

                sb.append("at ");  // Improves compatibility with IntelliJ "Analyze Stacktrace" feature
                sb.append(line);
                sb.append('\n');
            }

            return sb.toString();
        }

        public boolean isRequestThread()
        {
            return _isRequestThread;
        }


        private static final Pattern TEMP_TABLE_PATTERN = Pattern.compile("([ix_|temp\\.][\\w]+)\\$?\\p{XDigit}{32}");
        private static final Pattern SPECIMEN_TEMP_TABLE_PATTERN = Pattern.compile("(SpecimenUpload)\\d{9}");

        // Transform the SQL to help with coalescing
        private String transform(String sql)
        {
            // Remove the randomly-generated parts of temp table names
            sql = TEMP_TABLE_PATTERN.matcher(sql).replaceAll("$1");
            return SPECIMEN_TEMP_TABLE_PATTERN.matcher(sql).replaceAll("$1");
        }
    }

    private static class QueryTracker
    {
        private final String _sql;
        private @Nullable Collection<Object> _parameters = null;  // Keep parameters from the longest running query
        private final long _firstInvocation;
        private final Map<ByteArrayHashKey, AtomicInteger> _stackTraces = new HashMap<ByteArrayHashKey, AtomicInteger>();

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

            ByteArrayHashKey compressed = new ByteArrayHashKey(Compress.deflate(stackTrace));
            AtomicInteger frequency = _stackTraces.get(compressed);

            if (null == frequency)
                _stackTraces.put(compressed, new AtomicInteger(1));
            else
                frequency.incrementAndGet();
        }

        public String getSql()
        {
            return _sql;
        }

        public String getSqlAndParameters()
        {
            if (null == _parameters || _parameters.size() == 1)
                return null;

            List<Object> zeroBasedList = new LinkedList<Object>();
            Iterator<Object> iter = _parameters.iterator();

            iter.next();

            while (iter.hasNext())
                zeroBasedList.add(iter.next());

            SQLFragment sql = new SQLFragment(getSql(), zeroBasedList);

            try
            {
                return sql.toString();
            }
            catch (Exception e)
            {
                return null;
            }
        }

        private void setParameters(@Nullable Collection<Object> parameters)
        {
            _parameters = parameters;
        }

        @Nullable
        public Collection<Object> getParameters()
        {
            return _parameters;
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
            // Descending order by occurrences (the value)
            Set<Pair<String, AtomicInteger>> set = new TreeSet<Pair<String, AtomicInteger>>(new Comparator<Pair<String, AtomicInteger>>() {
                public int compare(Pair<String, AtomicInteger> e1, Pair<String, AtomicInteger> e2)
                {
                    int compare = e2.getValue().intValue() - e1.getValue().intValue();

                    if (0 == compare)
                        compare = e2.getKey().compareTo(e1.getKey());

                    return compare;
                }
            });

            // Save the stacktraces separately to find common prefix
            List<String> stackTraces = new LinkedList<String>();

            for (Map.Entry<ByteArrayHashKey, AtomicInteger> entry : _stackTraces.entrySet())
            {
                try
                {
                    String decompressed = Compress.inflate(entry.getKey().getBytes());
                    set.add(new Pair<String, AtomicInteger>(decompressed, entry.getValue()));
                    stackTraces.add(decompressed);
                }
                catch (DataFormatException e)
                {
                    ExceptionUtil.logExceptionToMothership(null, e);
                }
            }

            int commonLength = 0;
            String formattedCommonPrefix = "";

            if (set.size() > 1)
            {
                String commonPrefix = StringUtilsLabKey.findCommonPrefix(stackTraces);
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
            sb.append("</td>");
            sb.append("<td>");
            sb.append("SQL&nbsp;With&nbsp;Parameters");
            sb.append("</td>");
            sb.append("</tr>\n");
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
            pw.print(tab);
            pw.println("SQL With Parameters");
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
            row.append("<td style=\"padding-left:10;\">").append(PageFlowUtil.filter(getSqlAndParameters(), true)).append("</td>");
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

            row.append(tab).append(getSql().trim().replaceAll("(\\s)+", " "));
            row.append(tab).append(getSqlAndParameters().trim().replaceAll("(\\s)+", " ")).append("\n");
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

                            // First instance of this query, so always save its parameters
                            tracker.setParameters(query.getParameters());

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
                catch (IOException e)
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
