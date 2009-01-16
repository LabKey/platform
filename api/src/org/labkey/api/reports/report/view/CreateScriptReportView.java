/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.api.reports.report.view;

import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.*;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.TabStripView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.common.util.Pair;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/*
* User: Karl Lum
* Date: Dec 29, 2008
* Time: 10:49:00 PM
*/

/**
 * Tabbed view for creating script engine reports
 */
public class CreateScriptReportView extends RunScriptReportView
{
    public CreateScriptReportView(Report report)
    {
        super(report);
    }

    public List<NavTree> getTabList()
    {
        ActionURL url = getViewContext().cloneActionURL().
                replaceParameter(CACHE_PARAM, String.valueOf(_reportId));

        List<NavTree> tabs = new ArrayList<NavTree>();

        String currentTab = url.getParameter(TAB_PARAM);
        boolean saveChanges = currentTab == null || TAB_SOURCE.equals(currentTab);

        tabs.add(new ScriptTabInfo(TAB_SOURCE, TAB_SOURCE, url, saveChanges));

        return tabs;
    }

    static final Map<String, String> _formParams = new HashMap<String, String>();

    static
    {
        for (ReportDescriptor.Prop prop : ReportDescriptor.Prop.values())
            _formParams.put(prop.name(), prop.name());
        for (RReportDescriptor.Prop prop : RReportDescriptor.Prop.values())
            _formParams.put(prop.name(), prop.name());

        _formParams.put(RunReportView.CACHE_PARAM, RunReportView.CACHE_PARAM);
        _formParams.put(TabStripView.TAB_PARAM, TabStripView.TAB_PARAM);
    }

    protected ActionURL getRenderAction() throws Exception
    {
        ViewContext context = getViewContext();
        ActionURL url = PageFlowUtil.urlProvider(ReportUrls.class).urlRenderScriptReport(context.getContainer());

        // apply parameters already on the URL excluding those in to report bean (they will be applied on the post)
        for (Pair<String, String> param : getViewContext().getActionURL().getParameters())
        {
            if (!_formParams.containsKey(param.getKey()))
                url.replaceParameter(param.getKey(), param.getValue());
        }
        return url;
    }
}