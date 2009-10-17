/*
 * Copyright (c) 2009 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.apache.commons.collections15.map.ReferenceMap;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Formats;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.ShutdownListener;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.jetbrains.annotations.NotNull;

import javax.servlet.ServletContextEvent;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

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

    private static long _totalQueryCount = 0;
    private static long _totalQueryTime = 0;
    private static long _uniqueQueryCountEstimate = 0;  // This is a ceiling; true unique count is likely less than this since we're limiting capacity

    static
    {
        TRACKER_SETS.add(new QueryTrackerSet("Invocations", "highest number of invocations", false, true, new QueryTrackerComparator()
        {
            long getPrimaryStatisticValue(QueryTracker qt)
            {
                return qt.getCount();
            }

            long getSecondaryStatisticValue(QueryTracker qt)
            {
                return qt.getCumulative();
            }
        }));

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

        THREAD.start();
    }

    private QueryProfiler()
    {
    }

    static boolean track(String sql, long elapsed)
    {
        // Don't block if queue is full
        QUEUE.offer(new Query(sql, elapsed));
        return true;
    }

    public static HttpView getReportView(String statName, ActionURLFactory factory)
    {
        for (QueryTrackerSet set : TRACKER_SETS)
        {
            if (set.getCaption().equals(statName))
            {
                StringBuilder sb = new StringBuilder();

                sb.append("<table>\n");

                StringBuilder rows = new StringBuilder();

                // Don't update anything while we're rendering the report or vice versa
                synchronized (LOCK)
                {
                    sb.append("  <tr><td>Total Query Invocation Count:</td><td align=\"right\">").append(Formats.commaf0.format(_totalQueryCount)).append("</td></tr>\n");
                    sb.append("  <tr><td>Total Query Time:</td><td align=\"right\">").append(Formats.commaf0.format(_totalQueryTime)).append("</td></tr>\n");
                    sb.append("  <tr><td>Total Unique Queries");

                    if (_uniqueQueryCountEstimate > QueryTrackerSet.LIMIT)
                        sb.append(" (Estimate)");

                    sb.append(":</td><td align=\"right\">").append(Formats.commaf0.format(_uniqueQueryCountEstimate)).append("</td></tr>\n");
                    sb.append("</table><br><br>\n");

                    sb.append("<table>\n");
                    sb.append("  <tr><td>").append("Unique queries with the ").append(set.getDescription()).append(" (top ").append(Formats.commaf0.format(set.size())).append("):</td></tr>\n");
                    sb.append("</table><br>\n");

                    for (QueryTracker tracker : set)
                        tracker.insertRow(rows);
                }

                sb.append("<table>\n");
                QueryTracker.appendRowHeader(sb, set, factory);
                sb.append(rows);
                sb.append("</table>\n");

                return new HtmlView(sb.toString());
            }
        }

        throw new IllegalArgumentException("Query statistic \"" + statName + "\" does not exist");
    }

    private static class Query
    {
        private final String _sql;
        private final long _elapsed;

        private Query (String sql, long elapsed)
        {
            _sql = sql;
            _elapsed = elapsed;
        }

        private String getSql()
        {
            return _sql;
        }

        private long getElapsed()
        {
            return _elapsed;
        }
    }

    private static class QueryTracker
    {
        private final String _sql;
        private final long _firstInvocation;

        private long _count = 1;
        private long _max;
        private long _cumulative;

        private QueryTracker(@NotNull String sql, long elapsed)
        {
            _sql = sql;
            _max = elapsed;
            _cumulative = elapsed;
            _firstInvocation = System.currentTimeMillis();
        }

        private void addInvocation(long elapsed)
        {
            _count++;
            _cumulative += elapsed;

            if (elapsed > _max)
                _max = elapsed;
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

            sb.append("</b></a></td>");
        }

        private void insertRow(StringBuilder sb)
        {
            StringBuilder row = new StringBuilder();
            row.append("  <tr>");

            for (QueryTrackerSet set : TRACKER_SETS)
                if (set.shouldDisplay())
                    row.append("<td>").append(Formats.commaf0.format(((QueryTrackerComparator)set.comparator()).getPrimaryStatisticValue(this))).append("</td>");

            row.append("<td>").append(PageFlowUtil.filter(getSql())).append("</td>");
            row.append("</tr>\n");
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
                while (true)
                {
                    Query query = QUEUE.take();

                    synchronized (LOCK)
                    {
                        _totalQueryCount++;
                        _totalQueryTime += query.getElapsed();
                    }

                    QueryTracker tracker = QUERIES.get(query.getSql());

                    if (null == tracker)
                    {
                        tracker = new QueryTracker(query.getSql(), query.getElapsed());

                        // Don't update or add while we're rendering the report or vice versa
                        synchronized (LOCK)
                        {
                            _uniqueQueryCountEstimate++;

                            for (QueryTrackerSet set : TRACKER_SETS)
                                set.add(tracker);
                        }

                        QUERIES.put(query.getSql(), tracker);
                    }
                    else
                    {
                        // Don't update while we're rendering the report or vice versa
                        synchronized (LOCK)
                        {
                            for (QueryTrackerSet set : TRACKER_SETS)
                                set.beforeUpdate(tracker);

                            tracker.addInvocation(query.getElapsed());

                            for (QueryTrackerSet set : TRACKER_SETS)
                                set.update(tracker);
                        }
                    }
                }
            }
            catch (InterruptedException e)
            {
                LOG.info(getClass().getSimpleName() + " is terminating due to interruption");
            }
        }

        public void shutdownStarted(ServletContextEvent servletContextEvent)
        {
            interrupt();
        }
    }

    private static class QueryTrackerSet extends TreeSet<QueryTracker>
    {
        private static final int LIMIT = 1000;     // Set to MAX_INT for no limit

        private final String _caption;
        private final String _description;
        private final boolean _stable;   // Is this statistic stable, i.e., will it change after a QueryTracker has been added to the set?
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

        @Override
        public boolean add(QueryTracker tracker)
        {
            assert size() <= LIMIT;

            if (size() == LIMIT)
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
            int ret = (int)(getPrimaryStatisticValue(qt1) - getPrimaryStatisticValue(qt2));

            if (0 == ret)
                ret = (int)(getSecondaryStatisticValue(qt1) - getSecondaryStatisticValue(qt2));

            if (0 == ret)
                ret = qt1.getSql().compareTo(qt2.getSql());

            return ret;
        }

        abstract long getPrimaryStatisticValue(QueryTracker qt);
        abstract long getSecondaryStatisticValue(QueryTracker qt);
    }
}
