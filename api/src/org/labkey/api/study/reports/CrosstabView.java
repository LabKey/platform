/*
 * Copyright (c) 2006-2014 LabKey Corporation
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
            pw.write(PageFlowUtil.button("Export to Excel (.xls)").href(_exportAction).toString());

        List<Object> colHeaders = _crosstab.getColHeaders();
        Set<Stats.StatDefinition> statSet = _crosstab.getStatSet();

        pw.write("<table id=\"report\" class=\"labkey-data-region labkey-show-borders labkey-has-row-totals labkey-has-col-totals\">\n<colgroup>");
        if (statSet.size() > 1)
            pw.write("<col>");
        for (int i = 0; i < colHeaders.size() + 1; i++)
        {
             pw.write("<col>");
        }

        pw.write("</colgroup>\n<tr>\n");
        if (null != _crosstab.getColField())
        {
            pw.write("\t<th class=\"labkey-data-region-title\">&nbsp;</th>\n");
            if (statSet.size() > 1)
                pw.write("\t<th class=\"labkey-data-region-title\">&nbsp;</th>\n");

            pw.printf("\t<th class=\"labkey-data-region-title\" colspan=\"%d\">%s</th>\n", colHeaders.size(), str(_crosstab.getFieldLabel(_crosstab.getColField())));
            pw.write("\t<th class=\"labkey-data-region-title\" style=\"border-right:hidden\">&nbsp;</th>\n</tr>\n");
        }
        pw.printf("\t<th class=\"labkey-data-region-title\">%s</th>\n", str(_crosstab.getFieldLabel(_crosstab.getRowField())));
        if (statSet.size() > 1)
            pw.write("\t<td class=\"labkey-col-header\">&nbsp;</td>\n");

        if (null != _crosstab.getColField())
            for (Object colVal : colHeaders)
                pw.printf("\t<td class=\"labkey-col-header\">%s</td>\n", str(colVal));

        pw.printf("<td class=\"labkey-col-header\"");
        Stats.StatDefinition stat = null;
        if (statSet.size() == 1)
            stat = statSet.toArray(new Stats.StatDefinition[1])[0];

        if (null == _crosstab.getColField() && null != stat)
            pw.write(stat.getName());
        else
            pw.write("Total");

        pw.write("</td>");

        pw.write("</tr>");

        if (null != _crosstab.getRowField())
        {
            for (Object rowVal : _crosstab.getRowHeaders())
            {
                pw.printf("<tr>\n\t<td class=\"labkey-row-header\" rowspan=\"%d\">%s</td>\n", statSet.size(), rowVal == null ? "" : rowVal);

                int statRow = 0;
                for (Stats.StatDefinition rowStat : statSet)
                {
                    if (statSet.size() > 1)
                    {
                        if (statRow > 0)
                            pw.write("<tr>");
                        pw.printf("\t<td class=\"labkey-stat-title\">%s</td>\n", rowStat.getName());
                    }

                    for (Object colVal : colHeaders)
                    {
                        pw.printf("\t<td>%s</td>\n", _crosstab.getStats(rowVal, colVal).getFormattedStat(rowStat));
                    }

                    pw.printf("\t<td class=\"labkey-row-total\">%s</td>\n", _crosstab.getStats(rowVal, Crosstab.TOTAL_COLUMN).getFormattedStat(rowStat));

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


            if (null != _crosstab.getColField())
                for (Object colVal : colHeaders)
                {
                    pw.printf("\t<td class=\"labkey-col-total\">%s</td>\n", _crosstab.getStats(Crosstab.TOTAL_ROW, colVal).getFormattedStat(rowStat));
                }
            pw.write("<td class=\"labkey-col-total\">");
            Stats stats = _crosstab.getStats(Crosstab.TOTAL_ROW, Crosstab.TOTAL_COLUMN);
            pw.write(stats.getFormattedStat(rowStat));
            pw.write("</td>");

            statRow++;
            if (statSet.size() > 1 && statRow < statSet.size())
                pw.write("</tr>");
        }
        pw.write("</tr>");
        pw.write("</table>");
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
