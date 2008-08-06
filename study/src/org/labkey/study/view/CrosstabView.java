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

        pw.write("<table id=\"report\" class=\"labkey-data-region labkey-show-borders labkey-has-row-totals labkey-has-col-totals\">\n<colgroup>");
        if (statSet.size() > 1)
            pw.write("<col>");
        for (int i = 0; i < colHeaders.size() + 1; i++)
        {
             pw.write("<col>");
        }

        pw.write("</colgroup>\n<tr>\n");
        if (null != colField)
        {
            pw.write("\t<th class=\"labkey-data-region-title\">&nbsp;</th>\n");
            if (statSet.size() > 1)
                pw.write("\t<th class=\"labkey-data-region-title\">&nbsp;</th>\n");

            pw.printf("\t<th class=\"labkey-data-region-title\" colspan=\"%d\">%s</th>\n", colHeaders.size(), str(getFieldLabel(colField)));
            pw.write("\t<th class=\"labkey-data-region-title\" style=\"border-right:hidden\">&nbsp;</th>\n</tr>\n");
        }
        pw.printf("\t<th class=\"labkey-data-region-title\">%s</th>\n", str(getFieldLabel(rowField)));
        if (statSet.size() > 1)
            pw.write("\t<td class=\"labkey-col-header\">&nbsp;</td>\n");

        if (null != colField)
            for (Object colVal : colHeaders)
                pw.printf("\t<td class=\"labkey-col-header\">%s</td>\n", str(colVal));

        pw.printf("<td class=\"labkey-col-header\"");
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
                pw.printf("<tr>\n\t<td class=\"labkey-row-header\" rowspan=\"%d\">%s</td>\n", statSet.size(), rowVal);

                int statRow = 0;
                for (Stats.StatDefinition rowStat : statSet.toArray(new Stats.StatDefinition[0]))
                {
                    if (statSet.size() > 1)
                    {
                        if (statRow > 0)
                            pw.write("<tr>");
                        pw.printf("\t<td class=\"labkey-stat-title\">%s</td>\n", rowStat.getName());
                    }

                    for (Object colVal : colHeaders)
                    {
                        pw.printf("\t<td>%s</td>\n", crossTab.getStats(rowVal, colVal).getFormattedStat(rowStat));
                    }

                    pw.printf("\t<td class=\"labkey-row-total\">%s</td>\n", crossTab.getStats(rowVal, Crosstab.TOTAL_COLUMN).getFormattedStat(rowStat));

                    statRow++;
                    if (statSet.size() > 1 && statRow < statSet.size())
                        pw.write("</tr>");
                }
                pw.write("</tr>");

            }
        }

        //Now totals for the cols
        pw.printf("<tr>\n\t<td class=\"labkey-row-header\" rowspan=\"%d\">Total</td>\n", statSet.size());

        int statRow = 0;
        for (Stats.StatDefinition rowStat : statSet)
        {
            if (statSet.size() > 1)
            {
                if (statRow > 0)
                {
                    pw.write("<tr>");
                }

                pw.printf("\t<td class=\"labkey-stat-title\">%s</td>\n", rowStat.getName());
            }


            if (null != colField)
                for (Object colVal : colHeaders)
                {
                    pw.printf("\t<td class=\"labkey-col-total\">%s</td>\n", crossTab.getStats(Crosstab.TOTAL_ROW, colVal).getFormattedStat(rowStat));
                }
            pw.write("<td class=\"labkey-col-total\">");
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

}
