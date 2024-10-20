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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.ByteArrayHashKey;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TSVWriter;
import org.labkey.api.data.dialect.SqlDialect.ExecutionPlanType;
import org.labkey.api.util.Compress;
import org.labkey.api.util.DOM;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.Formats;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.view.ActionURL;

import java.io.PrintWriter;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.DataFormatException;

/**
 * Information about a specific query that has been issued against the database. Intended to have one instance
 * per unique SQL. Tracks information about executions to date, and code that invoked it.
 */
class QueryTracker
{
    private final @Nullable DbScope _scope;
    private String _sql;
    private boolean _truncated;
    private final String _hash;
    private boolean _validSql;
    private final long _firstInvocation;
    private final Map<ByteArrayHashKey, AtomicInteger> _stackTraces = new ReferenceMap<>(ReferenceStrength.SOFT, ReferenceStrength.HARD, true); // Not sure about purgeValues

    private @Nullable List<Object> _parameters = null;  // Keep parameters from the longest running query

    private long _count = 0;
    private long _max = 0;
    private long _cumulative = 0;
    private long _lastInvocation;

    QueryTracker(@Nullable DbScope scope, @NotNull String sql, @NotNull String hash, long elapsed, String stackTrace, boolean validSql, boolean truncated)
    {
        _scope = scope;
        _sql = sql;
        _hash = hash;
        _validSql = validSql;
        _firstInvocation = System.currentTimeMillis();
        _truncated = truncated;

        addInvocation(elapsed, stackTrace);
    }

    public void addInvocation(long elapsed, String stackTrace)
    {
        _count++;
        _cumulative += elapsed;
        _lastInvocation = System.currentTimeMillis();

        if (elapsed > _max)
            _max = elapsed;

        ByteArrayHashKey compressed = new ByteArrayHashKey(Compress.deflate(stackTrace));
        AtomicInteger frequency = _stackTraces.get(compressed);

        if (null == frequency)
            _stackTraces.put(compressed, new AtomicInteger(1));
        else
            frequency.incrementAndGet();
    }

    @Nullable
    public DbScope getScope()
    {
        return _scope;
    }

    public String getHash()
    {
        return _hash;
    }

    public boolean isTruncated()
    {
        return _truncated;
    }

    public String getSql()
    {
        return _sql;
    }

    public SQLFragment getSQLFragment()
    {
        // Avoid tripping up semicolon and unmatched quote detection, Issue 48731
        SQLFragment sql = SQLFragment.unsafe(getSql());
        if (null != _parameters)
            sql.addAll(_parameters);
        return sql;
    }

    public String getSqlAndParameters()
    {
        return getSQLFragment().toDebugString();
    }

    public void setParameters(@Nullable List<Object> parameters)
    {
        _parameters = parameters;
    }

    @Nullable
    public List<Object> getParameters()
    {
        return _parameters;
    }

    public boolean canShowExecutionPlan(ExecutionPlanType type)
    {
        return null != _scope && _scope.getSqlDialect().canShowExecutionPlan(type) && _validSql && !_truncated && type.canShowExecutionPlan(_sql);
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

    public long getLastInvocation()
    {
        return _lastInvocation;
    }

    public long getAverage()
    {
        return _cumulative / _count;
    }

    public int getStackTraceCount()
    {
        return _stackTraces.size();
    }

    public DOM.Renderable renderStackTraces()
    {
        // Descending order by occurrences (the value)
        Set<Pair<String, AtomicInteger>> set = new TreeSet<>((e1, e2) ->
        {
            int compare = e2.getValue().intValue() - e1.getValue().intValue();

            if (0 == compare)
                compare = e2.getKey().compareTo(e1.getKey());

            return compare;
        });

        // Save the stacktraces separately to find common prefix
        List<String> stackTraces = new LinkedList<>();

        for (Map.Entry<ByteArrayHashKey, AtomicInteger> entry : _stackTraces.entrySet())
        {
            try
            {
                String decompressed = Compress.inflate(entry.getKey().getBytes());
                set.add(new Pair<>(decompressed, entry.getValue()));
                stackTraces.add(decompressed);
            }
            catch (DataFormatException e)
            {
                ExceptionUtil.logExceptionToMothership(null, e);
            }
        }

        int commonLength;
        DOM.Renderable formattedCommonPrefix;

        if (set.size() > 1)
        {
            String commonPrefix = StringUtilsLabKey.findCommonPrefix(stackTraces);
            int idx = commonPrefix.lastIndexOf('\n');

            if (-1 != idx)
            {
                commonLength = idx;
                formattedCommonPrefix = DOM.STRONG(HtmlString.of(commonPrefix.substring(0, commonLength), true));
            }
            else
            {
                commonLength = 0;
                formattedCommonPrefix = HtmlString.EMPTY_STRING;
            }
        }
        else
        {
            commonLength = 0;
            formattedCommonPrefix = HtmlString.EMPTY_STRING;
        }

        AtomicBoolean alt = new AtomicBoolean();

        return DOM.TABLE(
                DOM.TR(
                        DOM.TD(DOM.STRONG("Count")),
                        DOM.TD(DOM.STRONG(DOM.at(DOM.Attribute.style, "padding-left: 1em;"), "Traces"))
                ),
                set.stream().map(entry -> {
                    String stackTrace = entry.getKey();
                    int count = entry.getValue().get();
                    alt.set(!alt.get());
                    return DOM.TR(DOM.cl(alt.get() ? "labkey-alternate-row" : "labkey-row"),
                            DOM.TD(DOM.at(DOM.Attribute.style, "text-align: right; vertical-align: top"), count),
                            DOM.TD(DOM.at(DOM.Attribute.style, "padding-left: 1em;"), formattedCommonPrefix, HtmlString.of(stackTrace.substring(commonLength), true))
                            );
                })
        );
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

    public static void renderRowHeader(PrintWriter out, QueryTrackerSet currentSet, QueryProfiler.ActionURLFactory factory)
    {
        out.print("  <tr>");

        for (QueryTrackerSet set : QueryProfiler.getInstance().getTrackerSets())
            if (set.shouldDisplay())
                renderColumnHeader(set.getCaption(), set == currentSet, out, factory);

        out.print("<td class=\"labkey-column-header\">");
        out.print("Traces");
        out.print("</td><td class=\"labkey-column-header\" style=\"padding-left:10;\">");
        out.print("SQL");
        out.print("</td>");
        out.println("</tr>");
    }

    private static void renderColumnHeader(String name, boolean highlight, PrintWriter out, QueryProfiler.ActionURLFactory factory)
    {
        out.print("<td class=\"labkey-column-header\"><a href=\"");
        out.print(PageFlowUtil.filter(factory.getActionURL(name)));
        out.print("\">");

        if (highlight)
            out.print("<b>");

        out.print(name);

        if (highlight)
            out.print("</b>");

        out.print("</a></td>");
    }

    public static void exportRowHeader(PrintWriter out)
    {
        String tab = "";

        for (QueryTrackerSet set : QueryProfiler.getInstance().getTrackerSets())
        {
            if (set.shouldDisplay())
            {
                out.print(tab);
                out.print(set.getCaption());
                tab = "\t";
            }
        }

        out.print(tab);
        out.print("SQL\n");
    }

    public void renderRow(PrintWriter out, String className, QueryProfiler.ActionURLFactory factory)
    {
        out.println("  <tr class=\"" + className + "\">");

        for (QueryTrackerSet set : QueryProfiler.getInstance().getTrackerSets())
            if (set.shouldDisplay())
                out.println("<td style=\"text-align:right;vertical-align:top;\">" + ((QueryTrackerComparator) set.comparator()).getFormattedPrimaryStatistic(this) + "</td>");

        ActionURL url = factory.getActionURL(getHash());
        out.println("<td style=\"text-align:right;vertical-align:top;\"><a href=\"" + PageFlowUtil.filter(url.getLocalURIString()) + "\">" + Formats.commaf0.format(getStackTraceCount()) + "</a></td>");
        // In the full grid view, limit SQL to 2,000 characters (before encoding). Individual detail view still shows full SQL with and without parameters. See #29642.
        out.println("<td style=\"padding-left:10;\">" + PageFlowUtil.filter(StringUtils.abbreviate(getSql(), 2000), true) + "</td>");
        out.println("</tr>");
    }

    public void exportRow(TSVWriter tsvWriter)
    {
        PrintWriter out = tsvWriter.getPrintWriter();

        String tab = "";

        for (QueryTrackerSet set : QueryProfiler.getInstance().getTrackerSets())
        {
            if (set.shouldDisplay())
            {
                out.print(tab + (((QueryTrackerComparator) set.comparator()).getFormattedPrimaryStatistic(this)));
                tab = "\t";
            }
        }

        out.print(tab);
        out.print(tsvWriter.quoteValue(getSql()));
        out.print('\n');
    }

    public void truncate(int longSqlLimit)
    {
        if (_sql.length() > longSqlLimit)
        {
            _truncated = true;
            _validSql = false;
            _sql = _sql.substring(0, longSqlLimit);
        }

    }
}
