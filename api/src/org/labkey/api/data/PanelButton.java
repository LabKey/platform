/*
 * Copyright (c) 2009-2010 LabKey Corporation
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

import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.HttpView;

import java.io.Writer;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shows a button with a drop-down arrow. When clicked, the button renders a subpanel
 * with a tabbed UI, similar to a ribbon bar in Office
 * User: jeckels
 * Date: Dec 11, 2009
 */
public class PanelButton extends ActionButton
{
    private Map<String, HttpView> _subpanels = new LinkedHashMap<String, HttpView>();
    private final String _dataRegionName;

    public PanelButton(String caption, String dataRegionName)
    {
        super("PanelButton", caption, DataRegion.MODE_GRID, ActionButton.Action.LINK);
        _dataRegionName = dataRegionName;
    }

    private String getId()
    {
        return "PanelButtonContent" + String.valueOf(System.identityHashCode(this));
    }

    @Override
    public void render(RenderContext ctx, Writer out) throws IOException
    {
        String requiresSelectionDataRegion = _requiresSelection ? ctx.getCurrentRegion().getName() : null;
        String attributes = "id=\"" + getId() + "\"";
        if (requiresSelectionDataRegion != null)
            attributes += " labkey-requires-selection=\"" + PageFlowUtil.filter(requiresSelectionDataRegion) + "\"";

        boolean includeContent = !ctx.containsKey(getId());
        // Remember that we've already rendered the content once
        ctx.put(getId(), true);

        StringBuilder config = new StringBuilder("{ activeTab: 0, items: [");
        String separator = "";
        for (Map.Entry<String, HttpView> entry : _subpanels.entrySet())
        {
            config.append(separator);
            separator = ", ";
            config.append("{contentEl:");
            String subPanelId = getId() + PageFlowUtil.filter(entry.getKey());
            config.append(PageFlowUtil.jsString(subPanelId));
            config.append(", title:");
            config.append(PageFlowUtil.jsString(entry.getKey()));
            config.append("}");
            if (includeContent)
            {
                out.write("<div class=\"x-hide-display\" id=\"" + subPanelId + "\">");
                try
                {
                    entry.getValue().render(ctx.getRequest(), ctx.getViewContext().getResponse());
                }
                catch (IOException e)
                {
                    throw e;
                }
                catch (Exception e)
                {
                    throw new UnexpectedException(e);
                }
                out.write("</div>");
            }
        }
        config.append("]}");
        out.write("<script language=\"javascript\">Ext.onReady(function() {\n" +
                "LABKEY.requiresCss(\"verticalTabPanel/VerticalTabPanel.css\");\n" +
                "LABKEY.requiresScript(\"verticalTabPanel/VerticalTabPanel.js\", true);\n" +
                "});</script>");
        out.append(PageFlowUtil.generateDropDownButton(getCaption(), "javascript:void(0)",
                (requiresSelectionDataRegion != null ? "if (this.className.indexOf('labkey-disabled-button') == -1)\n" : "") +
                "LABKEY.DataRegions[" + PageFlowUtil.filterQuote(_dataRegionName) + "].showButtonPanel(this, " + config + ");", attributes));
    }

    public void addSubPanel(String caption, HttpView view)
    {
        _subpanels.put(caption, view);
    }
}
