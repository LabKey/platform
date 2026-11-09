/*
 * Copyright (c) 2009-2026 LabKey Corporation
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

import org.labkey.api.util.DOM;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.Stats;
import org.labkey.api.view.Stats.StatDefinition;
import org.labkey.api.view.WebPartView;
import org.labkey.api.writer.HtmlWriter;

import java.util.List;
import java.util.Set;

import static org.labkey.api.util.DOM.Attribute.colspan;
import static org.labkey.api.util.DOM.Attribute.rowspan;
import static org.labkey.api.util.DOM.Attribute.style;
import static org.labkey.api.util.DOM.B;
import static org.labkey.api.util.DOM.DIV;
import static org.labkey.api.util.DOM.TABLE;
import static org.labkey.api.util.DOM.TD;
import static org.labkey.api.util.DOM.TH;
import static org.labkey.api.util.DOM.TR;
import static org.labkey.api.util.DOM.at;
import static org.labkey.api.util.DOM.cl;

public class CrosstabView extends WebPartView<Object>
{
    private final ActionURL _exportAction;
    private final Crosstab _crosstab;

    @Override
    protected void renderView(Object model, HtmlWriter out)
    {
        if (null == _crosstab.getStatField())
        {
            DOM.createHtmlFragment(
                B("Crosstab Error"),
                HtmlString.BR,
                "Stat field is not defined.",
                HtmlString.BR
            ).appendTo(out);
        }
        else
        {
            if (_exportAction != null)
            {
                DIV(
                    at(style, "margin-bottom:20px;"),
                    PageFlowUtil.button("Export to Excel (.xls)").href(_exportAction)
                ).appendTo(out);
            }

            // Precalculate vars that are used below
            List<Object> colHeaders = _crosstab.getColHeaders();
            Set<StatDefinition> statSet = _crosstab.getStatSet();
            boolean multipleStats = statSet.size() > 1;
            StatDefinition firstStat = statSet.iterator().next();
            StatDefinition stat = null;
            if (statSet.size() == 1)
                stat = statSet.toArray(new StatDefinition[1])[0];
            Stats totalStats = _crosstab.getStats(Crosstab.TOTAL_ROW, Crosstab.TOTAL_COLUMN);

            TABLE(
                cl("table-xtab-report").id("report"),

                // Top header
                null != _crosstab.getColField() ? TR(
                    TH(HtmlString.NBSP),
                    multipleStats ? TH(HtmlString.NBSP) : null,
                    TH(at(colspan, colHeaders.size()), _crosstab.getFieldLabel(_crosstab.getColField())),
                    TH(HtmlString.NBSP)
                ) : null,

                // Column headers
                TR(
                    TH(_crosstab.getFieldLabel(_crosstab.getRowField())),
                    multipleStats ? TD(cl("xtab-col-header"), HtmlString.NBSP) : null,
                    null != _crosstab.getColField() ? colHeaders.stream().map(colVal -> TD(cl("xtab-col-header"), HtmlString.of(colVal))) : null,
                    TD(cl("xtab-col-header"), null == _crosstab.getColField() && null != stat ? stat.getName() : "Total")
                ),

                // Value rows (one per stat per value row)
                null != _crosstab.getRowField() ?
                    _crosstab.getRowHeaders().stream()
                        .flatMap(rowVal -> statSet.stream().map(rowStat -> TR(
                            rowStat == firstStat ? TD(cl("xtab-row-header").at(rowspan, statSet.size()), HtmlString.of(rowVal)) : null,
                            multipleStats ? TD(cl("xtab-stat-title"), rowStat.getName()) : null,
                            colHeaders.stream().map(colVal -> TD(_crosstab.getStats(rowVal, colVal).getFormattedStat(rowStat))),
                            TD(cl("xtab-row-total"), _crosstab.getStats(rowVal, Crosstab.TOTAL_COLUMN).getFormattedStat(rowStat))
                        ))) : null,

                // Column totals (one per stat)
                statSet.stream()
                    .map(rowStat -> TR(
                        rowStat == firstStat ? TD(cl("xtab-row-header").at(rowspan, statSet.size()), "Total") : null,
                        multipleStats ? TD(cl("xtab-stat-title"), rowStat.getName()) : null,
                        null != _crosstab.getColField() ? colHeaders.stream().map(colVal -> TD(cl("xtab-col-total"), _crosstab.getStats(Crosstab.TOTAL_ROW, colVal).getFormattedStat(rowStat))) : null,
                        TD(cl("xtab-col-total"), totalStats.getFormattedStat(rowStat))
                    ))
            ).appendTo(out);
        }
    }

    public CrosstabView(Crosstab crosstab, ActionURL exportAction)
    {
        super(crosstab.getDescription());
        _crosstab = crosstab;
        _exportAction = exportAction;
    }
}
