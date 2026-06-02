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
package org.labkey.specimen.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SimpleDisplayColumn;
import org.labkey.api.data.TableInfo;
import org.labkey.api.study.StudyUtils;
import org.labkey.api.util.DOM;
import org.labkey.api.util.DOM.Renderable;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.PageFlowUtil.HelpPopupBuilder;
import org.labkey.api.writer.HtmlWriter;

import java.util.Set;

import static org.labkey.api.util.DOM.Attribute.id;
import static org.labkey.api.util.DOM.Attribute.src;
import static org.labkey.api.util.DOM.Attribute.style;
import static org.labkey.api.util.DOM.DIV;
import static org.labkey.api.util.DOM.IMG;
import static org.labkey.api.util.DOM.SPAN;
import static org.labkey.api.util.DOM.at;

public class SpecimenRequestDisplayColumn extends SimpleDisplayColumn
{
    private final boolean _showOneVialIndicator;
    private final boolean _showZeroVialIndicator;
    private final TableInfo _table;
    private final boolean _showCartLinks;
    private final SpecimenQueryView _specimenQueryView;

    public SpecimenRequestDisplayColumn(SpecimenQueryView specimenQueryView, TableInfo table, boolean showOneVialIndicator,
                                        boolean showZeroVialIndicator, boolean showCartLinks)
    {
        _specimenQueryView = specimenQueryView;
        _showOneVialIndicator = showOneVialIndicator;
        _showZeroVialIndicator = showZeroVialIndicator;
        _table = table;
        _showCartLinks = showCartLinks;
    }

    @Override
    public void renderGridCellContents(RenderContext ctx, HtmlWriter out)
    {
        String hash = (String) ctx.getRow().get("SpecimenHash");
        String globalUniqueId = (String) ctx.getRow().get("GlobalUniqueId");
        int c = -1;
        if (ctx.getRow().get("AvailableCount") != null)
        {
            c = ((Number)ctx.getRow().get("AvailableCount")).intValue();
        }
        else
        {
            Integer sampleCount = _specimenQueryView.getSampleCounts(ctx).get(hash);
            if (sampleCount != null)
                c = sampleCount.intValue();
        }

        int count = c;
        boolean vialView = _specimenQueryView.isShowingVials();
        boolean available = (!vialView && count > 0) || (vialView && StudyUtils.isFieldTrue(ctx, "Available"));
        boolean showCart = _showCartLinks && available;

        SPAN(
            at(style,"text-align: center").at(showCart, id, (globalUniqueId != null ? globalUniqueId : hash)),
            (Renderable) ret -> {
                String script = null;

                if (showCart)
                {
                    script = (globalUniqueId == null ? "requestByHash('" + hash + "'); return false;"
                            : "requestByGlobalUniqueId('" + globalUniqueId + "'); return false;");
                }

                if (_showOneVialIndicator && count == 1)
                {
                    out.write(getVialCountHtml(ctx, IMG(at(src, ctx.getViewContext().getContextPath() + "/_images/one.png")),
                        "One Vial Available", HtmlString.of("Only one vial of this primary specimen is available."), script));
                }
                else if (_showZeroVialIndicator && count == 0)
                {
                    out.write(getVialCountHtml(ctx, IMG(at(src, ctx.getViewContext().getContextPath() + "/_images/zero.png")),
                        "Zero Vials Available", HtmlString.of("No vials of this primary specimen are currently available for request."), null));
                }
                else
                {
                    out.write(getVialCountHtml(ctx, DIV(at(style, "color:gray"), count),
                        count + " Vials Available", HtmlString.of(count + " vials of this primary specimen are currently available for new requests."), script));
                }
                return ret;
            }
        ).appendTo(out);
    }

    private HelpPopupBuilder getVialCountHtml(RenderContext ctx, Renderable cellHtml, String popupTitle, Renderable popupBody, String requestScript)
    {
        if (requestScript != null)
        {
            cellHtml = DOM.createHtmlFragment(cellHtml, IMG(at(src, ctx.getViewContext().getContextPath() + "/_images/cart.png")));
            popupBody = DOM.createHtmlFragment(popupBody, HtmlString.BR, HtmlString.BR, "Click the shopping cart icon to request this specimen.");
        }
        return PageFlowUtil.popupHelp(popupBody, popupTitle).link(cellHtml).script(requestScript);
    }

    @Override
    public void addQueryColumns(Set<ColumnInfo> set)
    {
        set.add(_table.getColumn("SpecimenHash"));
        // fix for https://cpas.fhcrc.org/Issues/home/issues/details.view?issueId=3116
        ColumnInfo atRepositoryColumn = _table.getColumn("AtRepository");
        if (atRepositoryColumn != null)
            set.add(atRepositoryColumn);
        ColumnInfo lockedInRequestColumn = _table.getColumn("LockedInRequest");
        if (lockedInRequestColumn != null)
            set.add(lockedInRequestColumn);
        ColumnInfo availableCountColumn = _table.getColumn("AvailableCount");
        if (availableCountColumn != null)
            set.add(availableCountColumn);
        ColumnInfo availableColumn = _table.getColumn("Available");
        if (availableColumn != null)
            set.add(availableColumn);
    }
}