/*
 * Copyright (c) 2009-2014 LabKey Corporation
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
package org.labkey.study.query;

import org.labkey.api.data.SimpleDisplayColumn;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.study.controllers.specimen.SpecimenUtils;

import java.io.Writer;
import java.io.IOException;
import java.util.Set;/*
 * User: brittp
 * Date: Dec 18, 2008
 * Time: 11:22:25 AM
 */

public class SpecimenRequestDisplayColumn extends SimpleDisplayColumn
{
    private boolean _showOneVialIndicator;
    private boolean _showZeroVialIndicator;
    private TableInfo _table;
    private boolean _showCartLinks;
    private SpecimenQueryView _specimenQueryView;

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
    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        String hash = (String) ctx.getRow().get("SpecimenHash");
        String globalUniqueId = (String) ctx.getRow().get("GlobalUniqueId");
        int count = -1;
        if (ctx.getRow().get("AvailableCount") != null)
        {
            count = ((Number)ctx.getRow().get("AvailableCount")).intValue();
        }
        else
        {
            Integer c = _specimenQueryView.getSampleCounts(ctx).get(hash);
            if (c != null)
                count = c.intValue();
        }


        boolean vialView = _specimenQueryView.isShowingVials();
        boolean available = (!vialView && count > 0) || (vialView && SpecimenUtils.isFieldTrue(ctx, "Available"));
        boolean showCart = _showCartLinks && available;
        String script = null;
        out.write("<center>");
        if (showCart)
        {
            script = globalUniqueId == null ? "requestByHash('" + hash + "'); return false;"
                        : "requestByGlobalUniqueId('" + globalUniqueId + "'); return false;";
            out.write("<span id=\"" + (globalUniqueId != null ? globalUniqueId : hash) + "\">");
        }

        if (_showOneVialIndicator && count == 1)
        {
            out.write(getVialCountHtml(ctx, "<img src=\"" + ctx.getViewContext().getContextPath() + "/_images/one.png\">",
                    "One Vial Available", "Only one vial of this primary specimen is available.", script));
        }
        else if (_showZeroVialIndicator && count == 0)
        {
            out.write(getVialCountHtml(ctx, "<img src=\"" + ctx.getViewContext().getContextPath() + "/_images/zero.png\">",
                    "Zero Vials Available", "No vials of this primary specimen are currently available for request.", null));
        }
        else
        {
            out.write(getVialCountHtml(ctx, "<div style='color:gray'>" + String.valueOf(count)  + "</div>",
                    count + " Vials Available", count + " vials of this primary specimen are currently available for new requests.", script));
        }

        if (showCart)
            out.write("</a></span>");
        out.write("</center>");
    }

    private String getVialCountHtml(RenderContext ctx, String cellHtml, String popupTitle, String popupBody, String requestScript)
    {
        StringBuilder builder = new StringBuilder();
        if (requestScript != null)
        {
            cellHtml += "<img src=\"" + ctx.getViewContext().getContextPath() + "/_images/cart.png\">";
            popupBody += "<br><br>Click the shopping cart icon to request this specimen.";
        }
        builder.append(PageFlowUtil.helpPopup(popupTitle, popupBody, true, cellHtml, requestScript));
        return builder.toString();
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