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
package org.labkey.api.data;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.labkey.api.util.ButtonBuilder;
import org.labkey.api.util.DOM;
import org.labkey.api.util.DOM.Attribute;
import org.labkey.api.util.JavaScriptFragment;
import org.labkey.api.util.LinkBuilder;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.HttpView;
import org.labkey.api.writer.HtmlWriter;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.labkey.api.util.DOM.Attribute.name;
import static org.labkey.api.util.DOM.Attribute.style;
import static org.labkey.api.util.DOM.DIV;
import static org.labkey.api.util.DOM.LI;
import static org.labkey.api.util.DOM.SCRIPT;
import static org.labkey.api.util.DOM.UL;
import static org.labkey.api.util.DOM.at;
import static org.labkey.api.util.DOM.cl;

/**
 * Shows a button with a drop-down arrow. When clicked, the button renders a subpanel
 * with a tabbed UI, similar to a ribbon bar in Office.
 */
public class PanelButton extends ActionButton
{
    private final String _panelName;
    private final Map<String, HttpView<?>> _subpanels = new LinkedHashMap<>();
    private final String _dataRegionName;

    private boolean _justified;
    private boolean _tabAlignTop;

    public PanelButton(String panelName, String caption, String dataRegionName)
    {
        super(caption, ActionButton.Action.LINK);
        _panelName = panelName;
        _dataRegionName = dataRegionName;
        setId("PanelButtonContent" + System.identityHashCode(this));
    }

    @Override
    public void render(RenderContext ctx, HtmlWriter out)
    {
        String requiresSelectionDataRegion = _requiresSelection ? ctx.getCurrentRegion().getName() : null;
        String id = getId();
        String panelId = getId();
        Map<String, String> attributes = new HashMap<>();

        attributes.put("data-labkey-panel-toggle", panelId);

        if (requiresSelectionDataRegion != null)
            attributes.put("data-labkey-requires-selection", requiresSelectionDataRegion);

        // Remember that we've already rendered the content once
        ctx.put(id, true);

        ButtonBuilder bb = PageFlowUtil.button(getCaption())
            .dropdown(true)
            .href("#")
            .iconCls(getIconCls())
            .onClick("(function(el) { " + DataRegion.getJavaScriptObjectReference(_dataRegionName) + ".toggleButtonPanelHandler(el);})(this); return false;")
            .attributes(attributes);
        out.write(bb);

        String script =
            "LABKEY.DataRegion.registerPane(" + PageFlowUtil.jsString(_dataRegionName) + ", function(dr) {\n" + // see DataRegion.js#_defaultShow()
            "     dr.publishPanel(" + PageFlowUtil.jsString(panelId) + ",null,null,null,null,"+ PageFlowUtil.jsString(_panelName) + ");\n" +
            "});\n";
        SCRIPT(JavaScriptFragment.unsafe(script)).appendTo(out);

        final MutableBoolean activeTab = new MutableBoolean(true);
        final MutableBoolean activeContents = new MutableBoolean(true);

        // register panel with friendly name as well as ID
        DIV(
            at(Attribute.id, panelId, name, getCaption() + "-panel", style, "display: none;").cl("tabbable" + (!_tabAlignTop ? " tabs-left" : "")),
            // render tabs
            UL(
                cl("nav nav-tabs" + (_justified ? " nav-justified" : "")),
                _subpanels.keySet().stream()
                    .map(key -> {
                        var ret = LI(
                            cl(activeTab.booleanValue(),"active").cl("tab-pane" + (!_tabAlignTop ? " tab-pane-bordered" : "")),
                            LinkBuilder.simpleLink(key, "#" + id + key).attributes(Map.of("data-tabName", key.toLowerCase(), "data-toggle", "tab"))
                        );
                        activeTab.setFalse();
                        return ret;
                    })
            ),
            // -- render tabs

            // render tab contents
            DIV(
                cl("tab-content"),
                _subpanels.entrySet().stream().map(entry -> {
                    var ret = DIV(
                        cl("tab-pane").cl(!_tabAlignTop, "tab-pane-bordered").cl(activeContents.booleanValue(), "active").id(id + entry.getKey()),
                        (DOM.Renderable) ret2 -> {
                            try
                            {
                                entry.getValue().render(ctx.getRequest(), ctx.getViewContext().getResponse());
                            }
                            catch (Exception e)
                            {
                                throw new RuntimeException(e);
                            }
                            return ret2;
                        }
                    );
                    activeContents.setFalse();
                    return ret;
                })
            )
            // --render tab contents
        ).appendTo(out);
    }

    public void addSubPanel(String caption, HttpView<?> view)
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
