/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
package org.labkey.api.study.reports;

import org.apache.commons.beanutils.ConvertUtils;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.Stats;
import org.labkey.api.view.WebPartView;

import java.io.PrintWriter;
import java.util.List;
import java.util.Set;

/**
 * User: migra
 * Date: Mar 2, 2006
 * Time: 4:32:20 PM
 */
public class CrosstabView extends WebPartView
{
    ActionURL _exportAction;
    Crosstab _crosstab;

    @Override
    protected void renderView(Object model, PrintWriter pw) throws Exception
    {
        StringBuilder errStr = new StringBuilder();
        if (null == _crosstab.getStatField())
            errStr.append("Stat field is not defined.<br>");

        if (errStr.length() > 0)
        {
            pw.write("<b>Crosstab Error<b><br>");
            pw.write(errStr.toString());
            return;
        }

        if (_exportAction != null)
        {
            pw.write("<div style=\"margin-bottom:20px;\">");
            pw.write(PageFlowUtil.button("Export to Excel (.xls)").href(_exportAction).toString());
            pw.write("</div>");
        }

        List<Object> colHeaders = _crosstab.getColHeaders();
        Set<Stats.StatDefinition> statSet = _crosstab.getStatSet();

        pw.write("<table id=\"report\" class=\"table-xtab-report\"><tr>");
        if (null != _crosstab.getColField())
        {
            pw.write("<th>&nbsp;</th>");
            if (statSet.size() > 1)
                pw.write("<th>&nbsp;</th>");

            pw.printf("<th colspan=\"%d\">%s</th>", colHeaders.size(), str(_crosstab.getFieldLabel(_crosstab.getColField())));
            pw.write("<th>&nbsp;</th></tr>");
        }
        pw.printf("<th>%s</th>", str(_crosstab.getFieldLabel(_crosstab.getRowField())));
        if (statSet.size() > 1)
            pw.write("<td class=\"xtab-col-header\">&nbsp;</td>");

        if (null != _crosstab.getColField())
            for (Object colVal : colHeaders)
                pw.printf("<td class=\"xtab-col-header\">%s</td>", str(colVal));

        pw.printf("<td class=\"xtab-col-header\">");
        Stats.StatDefinition stat = null;
        if (statSet.size() == 1)
            stat = statSet.toArray(new Stats.StatDefinition[1])[0];

        if (null == _crosstab.getColField() && null != stat)
            pw.write(stat.getName());
        else
            pw.write("Total");

        pw.write("</td></tr>");

        if (null != _crosstab.getRowField())
        {
            for (Object rowVal : _crosstab.getRowHeaders())
            {
                pw.printf("<tr><td class=\"xtab-row-header\" rowspan=\"%d\">%s</td>", statSet.size(), rowVal == null ? "" : str(rowVal));

                int statRow = 0;
                for (Stats.StatDefinition rowStat : statSet)
                {
                    if (statSet.size() > 1)
                    {
                        if (statRow > 0)
                            pw.write("<tr>");
                        pw.printf("<td class=\"xtab-stat-title\">%s</td>", rowStat.getName());
                    }

                    for (Object colVal : colHeaders)
                    {
                        pw.printf("<td>%s</td>", _crosstab.getStats(rowVal, colVal).getFormattedStat(rowStat));
                    }

                    pw.printf("<td class=\"xtab-row-total\">%s</td>", _crosstab.getStats(rowVal, Crosstab.TOTAL_COLUMN).getFormattedStat(rowStat));

                    statRow++;
                    if (statSet.size() > 1 && statRow < statSet.size())
                        pw.write("</tr>");
                }
                pw.write("</tr>");

            }
        }

        //Now totals for the cols
        pw.printf("<tr><td class=\"xtab-row-header\" rowspan=\"%d\">Total</td>", statSet.size());

        int statRow = 0;
        for (Stats.StatDefinition rowStat : statSet)
        {
            if (statSet.size() > 1)
            {
                if (statRow > 0)
                    pw.write("<tr>");

                pw.printf("<td class=\"xtab-stat-title\">%s</td>", rowStat.getName());
            }

            if (null != _crosstab.getColField())
            {
                for (Object colVal : colHeaders)
                    pw.printf("<td class=\"xtab-col-total\">%s</td>", _crosstab.getStats(Crosstab.TOTAL_ROW, colVal).getFormattedStat(rowStat));
            }

            pw.write("<td class=\"xtab-col-total\">");
            Stats stats = _crosstab.getStats(Crosstab.TOTAL_ROW, Crosstab.TOTAL_COLUMN);
            pw.write(stats.getFormattedStat(rowStat));
            pw.write("</td>");

            statRow++;
            if (statSet.size() > 1 && statRow < statSet.size())
                pw.write("</tr>");
        }

        pw.write("</tr></table>");
    }

    public CrosstabView(Crosstab crosstab, ActionURL exportAction)
    {
        super(crosstab.getDescription());
        _crosstab = crosstab;
        _exportAction = exportAction;
    }

    private String str(Object val)
    {
        return PageFlowUtil.filter(ConvertUtils.convert(val));
    }
}
