/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
package org.labkey.study.view;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang.StringUtils;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Crosstab;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.Stats;
import org.labkey.api.view.WebPartView;
import org.labkey.study.reports.CrosstabReportDescriptor;

import java.io.PrintWriter;
import java.sql.ResultSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: migra
 * Date: Mar 2, 2006
 * Time: 4:32:20 PM
 */
public class CrosstabView extends WebPartView
{
    String rowField;
    String colField;
    String statField;
    Set<Stats.StatDefinition> statSet;
    ResultSet resultSet;
    Map<String, ColumnInfo> colMap;

    private static final String BLACK = "black";
    private static final String GRAY = "gray";
    private static final CellStyle COL_HEADER_STYLE = new CellStyle(true, BLACK, null, BLACK, null);
    private static final CellStyle ROW_HEADER_STYLE = new CellStyle(true, null, BLACK, GRAY, GRAY);
    private static final CellStyle DATA_STYLE = new CellStyle(false, null, null, GRAY, null);
    private static final CellStyle STAT_NAME_STYLE = new CellStyle(false, null, null, GRAY, GRAY);
    private static final CellStyle ROW_TOTAL_STYLE = new CellStyle(true, null, BLACK, GRAY, BLACK);
    private static final CellStyle COL_TOTAL_STYLE = new CellStyle(true, null, null, BLACK, null);

    @Override
    protected void renderView(Object model, PrintWriter pw) throws Exception
    {
        StringBuilder errStr = new StringBuilder();
        if (null == StringUtils.trimToNull(statField))
            errStr.append("Stat field is not defined.<br>");

        if (errStr.length() > 0)
        {
            pw.write("<b>Crosstab Error<b><br>");
            pw.write(errStr.toString());
            return;
        }

        Crosstab crossTab = new Crosstab(resultSet, rowField, colField, statField, statSet);
        List<Object> colHeaders = crossTab.getColHeaders();

        pw.write("<table id=\"report\" class=\"labkey-crosstab-report\"><tr>\n");
        if (null != colField)
        {
            pw.write("\t<td>&nbsp;</td>\n");
            if (statSet.size() > 1)
                pw.write("\t<td>&nbsp;</td>\n");

            pw.printf("\t<td style=\"%s\" colspan=\"%d\">%s</td>\n", "font-weight:bold;border-bottom 1px solid black", colHeaders.size(), str(getFieldLabel(colField)));
            pw.write("\t<td style=\"font-weight:bold;border-bottom:1px solid black;\">&nbsp;</td>\n</tr>\n");
        }
        pw.printf("\t<td style=\"font-weight:bold;border-bottom:1px solid black;border-right:1px solid black\">%s</td>\n", str(getFieldLabel(rowField)));
        if (statSet.size() > 1)
            pw.write("\t<td style=\"font-weight:bold;border:1px solid black\">&nbsp;</td>\n");

        if (null != colField)
            for (Object colVal : colHeaders)
                pw.printf("\t<td style=\"%s\">%s</td>\n", COL_HEADER_STYLE, str(colVal));

        CellStyle style = COL_HEADER_STYLE.clone().right(BLACK).left(BLACK);
        pw.printf("<td style=\"%s\">", style);
        Stats.StatDefinition stat = null;
        if (statSet.size() == 1)
            stat = statSet.toArray(new Stats.StatDefinition[1])[0];

        if (null == colField && null != stat)
            pw.write(stat.getName());
        else
            pw.write("Total");

        pw.write("</td>");

        pw.write("</tr>");

        if (null != rowField)
        {
            for (Object rowVal : crossTab.getRowHeaders())
            {
                pw.printf("<tr>\n\t<td style=\"%s\" rowspan=\"%d\">%s</td>\n", ROW_HEADER_STYLE, statSet.size(), rowVal);

                int statRow = 0;
                for (Stats.StatDefinition rowStat : statSet.toArray(new Stats.StatDefinition[0]))
                {
                    if (statSet.size() > 1)
                    {
                        if (statRow > 0)
                            pw.write("<tr>");
                        pw.printf("\t<td style=\"%s\">%s</td>\n", STAT_NAME_STYLE, rowStat.getName());
                    }

                    for (Object colVal : colHeaders)
                        pw.printf("\t<td style=\"%s\">%s</td>\n", DATA_STYLE, crossTab.getStats(rowVal, colVal).getFormattedStat(rowStat));

                    pw.printf("\t<td style=\"%s\">%s</td>\n", ROW_TOTAL_STYLE, crossTab.getStats(rowVal, Crosstab.TOTAL_COLUMN).getFormattedStat(rowStat));

                    statRow++;
                    if (statSet.size() > 1 && statRow < statSet.size())
                        pw.write("</tr>");
                }
                pw.write("</tr>");

            }
        }

        //Now totals for the cols
        pw.printf("<tr>\n\t<td style=\"%s\" rowspan=\"%d\">Total</td>\n", ROW_HEADER_STYLE.clone().bottom(BLACK), statSet.size());

        int statRow = 0;
        for (Stats.StatDefinition rowStat : statSet)
        {
            if (statSet.size() > 1)
            {
                CellStyle cs = STAT_NAME_STYLE;
                if (statRow > 0)
                {
                    pw.write("<tr>");
                    if (statRow == statSet.size() -1)
                        cs = cs.clone().bottom(BLACK);
                }
                else
                    cs = cs.clone().top(BLACK);

                pw.printf("\t<td style=\"%s\">%s</td>\n", cs, rowStat.getName());
            }


            if (null != colField)
                for (Object colVal : colHeaders)
                {
                    CellStyle cs = COL_TOTAL_STYLE;
                    if (statRow == 0)
                        cs = cs.clone().top(BLACK).bottom(GRAY);
                    else if (statRow < statSet.size() - 1)
                        cs = cs.clone().bottom(BLACK);

                    pw.printf("\t<td style=\"%s\">%s</td>\n", cs, crossTab.getStats(Crosstab.TOTAL_ROW, colVal).getFormattedStat(rowStat));
                }
            pw.write("<td style=\"font-weight:bold;border-left: 1px solid black; border-right:1px solid black; border-bottom:1px solid black\">");
            Stats stats = crossTab.getStats(Crosstab.TOTAL_ROW, Crosstab.TOTAL_COLUMN);
            pw.write(stats.getFormattedStat(rowStat));
            pw.write("</td>");

            statRow++;
            if (statSet.size() > 1 && statRow < statSet.size())
                pw.write("</tr>");
        }
        pw.write("</tr>");
        pw.write("</table>");
    }

    public CrosstabView(ResultSet rs, Map<String, ColumnInfo> cols, CrosstabReportDescriptor descriptor)
    {
        resultSet = rs;
        colMap = cols;

        rowField = descriptor.getProperty("rowField");
        colField = descriptor.getProperty("colField");
        statField = descriptor.getProperty("statField");

        statSet = new LinkedHashSet<Stats.StatDefinition>();
        for (String stat : descriptor.getStats())
        {
            if ("Count".equals(stat))
                statSet.add(Stats.COUNT);
            else if ("Sum".equals(stat))
                statSet.add(Stats.SUM);
            else if ("Sum".equals(stat))
                statSet.add(Stats.SUM);
            else if ("Mean".equals(stat))
                statSet.add(Stats.MEAN);
            else if ("Min".equals(stat))
                statSet.add(Stats.MIN);
            else if ("Max".equals(stat))
                statSet.add(Stats.MAX);
            else if ("StdDev".equals(stat))
                statSet.add(Stats.STDDEV);
            else if ("Var".equals(stat))
                statSet.add(Stats.VAR);
            else if ("Median".equals(stat))
                statSet.add(Stats.MEDIAN);
        }
        setTitle(getDescription());
    }

    public CrosstabView(ResultSet rs, Map<String,ColumnInfo> cols, String rowField, String colField, String statField, Set<Stats.StatDefinition> statSet)
    {
        this.resultSet = rs;
        this.colMap = cols;
        this.rowField = rowField;
        this.colField = colField;
        this.statField = statField;
        this.statSet = statSet;
        setTitle(getDescription());
    }

    public String getDescription()
    {
        Stats.StatDefinition stat = null;
        if (statSet.size() == 1)
            stat = statSet.toArray(new Stats.StatDefinition[1])[0];

        String statFieldLabel = getFieldLabel(statField);
        String rowFieldLabel = getFieldLabel(rowField);
        String colFieldLabel = getFieldLabel(colField);

        String title = (null != stat ? stat.getName() + " " : "") + statFieldLabel + " by " + rowFieldLabel;
        if (null != colField)
            title += ", " + colFieldLabel;

        return title;
    }

    private String getFieldLabel(String fieldName)
    {
        if (null == fieldName)
            return "";

        ColumnInfo col = colMap.get(fieldName);
        if (null != col)
            return col.getCaption();

        return fieldName;
    }

    private String str(Object val)
    {
        return PageFlowUtil.filter(ConvertUtils.convert(val));
    }

    private static class CellBorder
    {
        String color;
        int weight = 1;
        String edge;

        public CellBorder(String edge)
        {
            this.edge = edge;
        }

        public String toString()
        {
            if (null != color)
                return "border-" + edge + ":" +  weight + "px solid " + color + ";";
            else
                return "";
        }
    }

    private static class CellStyle implements Cloneable
    {
        CellBorder top = new CellBorder("top");
        CellBorder left = new CellBorder("left");
        CellBorder bottom = new CellBorder("bottom");
        CellBorder right = new CellBorder("right");

        boolean bold;

        public CellStyle()
        {

        }

        public CellStyle(boolean bold, String topColor, String leftColor, String bottomColor, String rightColor)
        {
            this.bold = bold;
            top.color = topColor;
            left.color = leftColor;
            bottom.color = bottomColor;
            right.color = rightColor;
        }

        public CellStyle top(String color)
        {
            this.top.color = color;
            return this;
        }
        public CellStyle left(String color)
        {
            this.left.color = color;
            return this;
        }

        public CellStyle bottom(String color)
        {
            this.bottom.color = color;
            return this;
        }

        public CellStyle right(String color)
        {
            this.right.color = color;
            return this;
        }


        public String toString()
        {
            return (bold ? "font-weight:bold;" : "") +
                    top + left + bottom + right;
        }

        public CellStyle clone()
        {
            return new CellStyle(bold, top.color, left.color, bottom.color, right.color);
        }
    }
}
