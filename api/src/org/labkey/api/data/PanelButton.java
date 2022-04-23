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
package org.labkey.api.data;

import org.labkey.api.util.HttpUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.HttpView;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shows a button with a drop-down arrow. When clicked, the button renders a subpanel
 * with a tabbed UI, similar to a ribbon bar in Office.
 * User: jeckels
 * Date: Dec 11, 2009
 */
public class PanelButton extends ActionButton
{
    private final String _panelName;
    private boolean _justified;
    private boolean _tabAlignTop;
    private Map<String, HttpView> _subpanels = new LinkedHashMap<>();
    private final String _dataRegionName;

    public PanelButton(String panelName, String caption, String dataRegionName)
    {
        super(caption, ActionButton.Action.LINK);
        _panelName = panelName;
        _dataRegionName = dataRegionName;
        setId("PanelButtonContent" + String.valueOf(System.identityHashCode(this)));
    }

    @Override
    public void render(RenderContext ctx, Writer out) throws IOException
    {
        String requiresSelectionDataRegion = _requiresSelection ? ctx.getCurrentRegion().getName() : null;
        String id = getId();
        String panelId = getId();
        Map<String, String> attributes = new HashMap<>();

        attributes.put("data-labkey-panel-toggle", panelId);

        if (requiresSelectionDataRegion != null)
            attributes.put("data-labkey-requires-selection", requiresSelectionDataRegion);

        boolean active = true;
        // Remember that we've already rendered the content once
        ctx.put(id, true);

        String btn = PageFlowUtil.button(getCaption())
                .dropdown(true)
                .href("javascript:void(0)")
                .iconCls(getIconCls())
                .onClick("(function(el) { " + DataRegion.getJavaScriptObjectReference(_dataRegionName) + ".toggleButtonPanelHandler(el); })(this);")
                .attributes(attributes)
                .toString();

        out.write(btn);
        // register panel with friendly name as well as ID
        var page = HttpView.currentPageConfig();
        out.write("\n" + page.getScriptTagStart() +
                "LABKEY.DataRegion.registerPane(" + PageFlowUtil.jsString(_dataRegionName) + ", function(dr) {\n" + // see DataRegion.js#_defaultShow()
                "     dr.publishPanel(" + PageFlowUtil.jsString(panelId) + ",null,null,null,null,"+ PageFlowUtil.jsString(_panelName) + ");\n" +
                "});</script>\n");
        out.write("<div id=\"" + panelId + "\" name=\"" + getCaption() + "-panel\" "
                + "class=\"tabbable" + (!_tabAlignTop ? " tabs-left" : "") + "\" style=\"display: none;\">");

        // render tabs
        out.write("<ul class=\"nav nav-tabs" + (_justified ? " nav-justified" : "") + "\">");
        for (Map.Entry<String, HttpView> entry : _subpanels.entrySet())
        {
            String entryId = PageFlowUtil.filter(id + entry.getKey());
            out.write("<li" + (active ? " class=\"active\"" : "") + "><a data-tabName=\"" + PageFlowUtil.filter(entry.getKey().toLowerCase()) + "\" href=\"#" + entryId + "\" data-toggle=\"tab\">" + PageFlowUtil.filter(entry.getKey()) + "</a></li>");
            active = false;
        }
        out.write("</ul>");
        // -- render tabs

        // render tab contents
        active = true;
        out.write("<div class=\"tab-content\">");
        for (Map.Entry<String, HttpView> entry : _subpanels.entrySet())
        {
            String entryId = PageFlowUtil.filter(id + entry.getKey());
            out.write("<div id=\"" + entryId + "\" class=\"tab-pane"
                    + (!_tabAlignTop ? " tab-pane-bordered" : "")
                    + (active ? " active" : "") + "\">");
            active = false;
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
        out.write("</div>");
        // -- render tab contents

        out.write("</div>");
    }

    public void addSubPanel(String caption, HttpView view)
    {
        _subpanels.put(caption, view);
    }

    public boolean hasSubPanels()
    {
        return !_subpanels.isEmpty();
    }

    public void setJustified(boolean justified)
    {
        _justified = justified;
    }

    public void setTabAlignTop(boolean tabAlignTop)
    {
        _tabAlignTop = tabAlignTop;
    }
}
