/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.visualization.report;

import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UniqueID;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.api.visualization.GenericChartReport;
import org.labkey.visualization.VisualizationController;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: May 31, 2012
 */
public class GenericChartReportImpl extends GenericChartReport
{
    @Override
    public HttpView renderReport(ViewContext context) throws Exception
    {
        VisualizationController.GenericReportForm form = new VisualizationController.GenericReportForm();

        form.setReportId(getReportId());
        form.setComponentId("generic-report-panel-" + UniqueID.getRequestScopedUID(context.getRequest()));

        JspView view = new JspView<VisualizationController.GenericReportForm>("/org/labkey/visualization/views/genericChartWizard.jsp", form);

        view.setTitle(getDescriptor().getReportName());
        view.setFrame(WebPartView.FrameType.PORTAL);

        if (canEdit(context.getUser(), context.getContainer()))
        {
            NavTree menu = new NavTree();
            menu.addChild("Manage Views", PageFlowUtil.urlProvider(ReportUrls.class).urlManageViews(context.getContainer()));
            view.setNavMenu(menu);
        }

        return view;
    }
}
