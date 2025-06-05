/*
 * Copyright (c) 2009-2019 LabKey Corporation
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
import org.labkey.api.writer.HtmlWriter;

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
    protected void renderView(Object model, PrintWriter oldWriter, HtmlWriter out)
    {
        StringBuilder errStr = new StringBuilder();
        if (null == _crosstab.getStatField())
            errStr.append("Stat field is not defined.<br>");

        if (errStr.length() > 0)
        {
            oldWriter.write("<b>Crosstab Error<b><br>");
            oldWriter.write(errStr.toString());
            return;
        }

        if (_exportAction != null)
        {
            oldWriter.write("<div style=\"margin-bottom:20px;\">");
            oldWriter.write(PageFlowUtil.button("Export to Excel (.xls)").href(_exportAction).toString());
            oldWriter.write("</div>");
        }

        List<Object> colHeaders = _crosstab.getColHeaders();
        Set<Stats.StatDefinition> statSet = _crosstab.getStatSet();

        oldWriter.write("<table id=\"report\" class=\"table-xtab-report\"><tr>");
        if (null != _crosstab.getColField())
        {
            oldWriter.write("<th>&nbsp;</th>");
            if (statSet.size() > 1)
                oldWriter.write("<th>&nbsp;</th>");

            oldWriter.printf("<th colspan=\"%d\">%s</th>", colHeaders.size(), str(_crosstab.getFieldLabel(_crosstab.getColField())));
            oldWriter.write("<th>&nbsp;</th></tr>");
        }
        oldWriter.printf("<th>%s</th>", str(_crosstab.getFieldLabel(_crosstab.getRowField())));
        if (statSet.size() > 1)
            oldWriter.write("<td class=\"xtab-col-header\">&nbsp;</td>");

        if (null != _crosstab.getColField())
            for (Object colVal : colHeaders)
                oldWriter.printf("<td class=\"xtab-col-header\">%s</td>", str(colVal));

        oldWriter.printf("<td class=\"xtab-col-header\">");
        Stats.StatDefinition stat = null;
        if (statSet.size() == 1)
            stat = statSet.toArray(new Stats.StatDefinition[1])[0];

        if (null == _crosstab.getColField() && null != stat)
            oldWriter.write(stat.getName());
        else
            oldWriter.write("Total");

        oldWriter.write("</td></tr>");

        if (null != _crosstab.getRowField())
        {
            for (Object rowVal : _crosstab.getRowHeaders())
            {
                oldWriter.printf("<tr><td class=\"xtab-row-header\" rowspan=\"%d\">%s</td>", statSet.size(), rowVal == null ? "" : str(rowVal));

                int statRow = 0;
                for (Stats.StatDefinition rowStat : statSet)
                {
                    if (statSet.size() > 1)
                    {
                        if (statRow > 0)
                            oldWriter.write("<tr>");
                        oldWriter.printf("<td class=\"xtab-stat-title\">%s</td>", rowStat.getName());
                    }

                    for (Object colVal : colHeaders)
                    {
                        oldWriter.printf("<td>%s</td>", _crosstab.getStats(rowVal, colVal).getFormattedStat(rowStat));
                    }

                    oldWriter.printf("<td class=\"xtab-row-total\">%s</td>", _crosstab.getStats(rowVal, Crosstab.TOTAL_COLUMN).getFormattedStat(rowStat));

                    statRow++;
                    if (statSet.size() > 1 && statRow < statSet.size())
                        oldWriter.write("</tr>");
                }
                oldWriter.write("</tr>");

            }
        }

        //Now totals for the cols
        oldWriter.printf("<tr><td class=\"xtab-row-header\" rowspan=\"%d\">Total</td>", statSet.size());

        int statRow = 0;
        for (Stats.StatDefinition rowStat : statSet)
        {
            if (statSet.size() > 1)
            {
                if (statRow > 0)
                    oldWriter.write("<tr>");

                oldWriter.printf("<td class=\"xtab-stat-title\">%s</td>", rowStat.getName());
            }

            if (null != _crosstab.getColField())
            {
                for (Object colVal : colHeaders)
                    oldWriter.printf("<td class=\"xtab-col-total\">%s</td>", _crosstab.getStats(Crosstab.TOTAL_ROW, colVal).getFormattedStat(rowStat));
            }

            oldWriter.write("<td class=\"xtab-col-total\">");
            Stats stats = _crosstab.getStats(Crosstab.TOTAL_ROW, Crosstab.TOTAL_COLUMN);
            oldWriter.write(stats.getFormattedStat(rowStat));
            oldWriter.write("</td>");

            statRow++;
            if (statSet.size() > 1 && statRow < statSet.size())
                oldWriter.write("</tr>");
        }

        oldWriter.write("</tr></table>");
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
