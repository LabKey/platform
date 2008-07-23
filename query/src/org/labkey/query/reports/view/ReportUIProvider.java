/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.query.reports.view;

import org.labkey.api.reports.report.view.DefaultReportUIProvider;
import org.labkey.api.reports.report.view.ChartDesignerBean;
import org.labkey.api.reports.report.view.ChartUtil;
import org.labkey.api.reports.report.view.RReportBean;
import org.labkey.api.reports.report.ChartQueryReport;
import org.labkey.api.reports.report.RReport;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.snapshot.QuerySnapshotService;
import org.labkey.query.controllers.QueryControllerSpring;

import java.util.Map;

/*
 * User: Karl Lum
 * Date: May 16, 2008
 * Time: 4:10:45 PM
 */

public class ReportUIProvider extends DefaultReportUIProvider
{
    public void getReportDesignURL(ViewContext context, QuerySettings settings, Map<String, String> designers)
    {
        ChartDesignerBean chartBean = new ChartDesignerBean(settings);
        chartBean.setReportType(ChartQueryReport.TYPE);
        chartBean.setRedirectUrl(context.getActionURL().getLocalURIString());

        designers.put(ChartQueryReport.TYPE, ChartUtil.getChartDesignerURL(context, chartBean).getLocalURIString());

        if (RReport.isValidConfiguration())
        {
            int perms = RReport.getEditPermissions();
            if (context.hasPermission(perms))
            {
                RReportBean rBean = new RReportBean(settings);
                rBean.setReportType(RReport.TYPE);
                rBean.setRedirectUrl(context.getActionURL().getLocalURIString());

                designers.put(RReport.TYPE, ChartUtil.getRReportDesignerURL(context, rBean).getLocalURIString());
            }
            else
                designers.put(RReport.TYPE, "javascript:alert('You do not have the required authorization to create R Views.')");
        }
        else
            designers.put(RReport.TYPE, "javascript:alert('The R Program has not been configured properly, please request that an administrator configure R in the Admin Console.')");

        // query snapshot
        QuerySnapshotService.I provider = QuerySnapshotService.get(settings.getSchemaName());
        if (provider != null && !QueryService.get().isQuerySnapshot(context.getContainer(), settings.getSchemaName(), settings.getQueryName()))
        {
            designers.put(QuerySnapshotService.TYPE, provider.getCreateWizardURL(settings, context).getLocalURIString());
        }
    }

    public String getReportIcon(ViewContext context, String reportType)
    {
        if (RReport.TYPE.equals(reportType))
            return context.getContextPath() + "/reports/r.gif";
        if (ChartQueryReport.TYPE.equals(reportType))
            return context.getContextPath() + "/reports/chart.gif";
        return super.getReportIcon(context, reportType);
    }
}