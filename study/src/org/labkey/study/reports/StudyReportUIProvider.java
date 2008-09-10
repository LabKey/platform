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
package org.labkey.study.reports;

import org.labkey.api.reports.report.view.DefaultReportUIProvider;
import org.labkey.api.reports.report.view.ChartDesignerBean;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.reports.report.view.RReportBean;
import org.labkey.api.reports.report.RReport;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.query.QuerySettings;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.controllers.reports.ReportsController;
import org.apache.commons.lang.math.NumberUtils;

import java.util.Map;/*
 * User: Karl Lum
 * Date: May 16, 2008
 * Time: 5:19:28 PM
 */

public class StudyReportUIProvider extends DefaultReportUIProvider
{
    public void getReportDesignURL(ViewContext context, QuerySettings settings, Map<String, String> designers)
    {
        // crosstab designer
        ActionURL crossTabURL = context.getActionURL().clone();
        crossTabURL.setAction(ReportsController.ParticipantCrosstabAction.class);
        designers.put(StudyCrosstabReport.TYPE, crossTabURL.getLocalURIString());

        // chart designer
        ChartDesignerBean chartBean = new ChartDesignerBean(settings);
        chartBean.setReportType(StudyChartQueryReport.TYPE);

        ActionURL url = ReportUtil.getChartDesignerURL(context, chartBean);
        url.addParameter(DataSetDefinition.DATASETKEY, NumberUtils.toInt(context.getActionURL().getParameter(DataSetDefinition.DATASETKEY), 0));
        url.setAction(ReportsController.DesignChartAction.class);

        designers.put(StudyChartQueryReport.TYPE, url.getLocalURIString());

        // r report
        if (RReport.isValidConfiguration())
        {
            int perms = RReport.getEditPermissions();
            if (context.hasPermission(perms))
            {
                RReportBean rBean = new RReportBean(settings);
                rBean.setReportType(StudyRReport.TYPE);
                rBean.setRedirectUrl(context.getActionURL().getLocalURIString());

                designers.put(StudyRReport.TYPE, ReportUtil.getRReportDesignerURL(context, rBean).getLocalURIString());
            }
            else
                designers.put(StudyRReport.TYPE, "javascript:alert('You do not have the required authorization to create R Views.')");
        }
        else
            designers.put(StudyRReport.TYPE, "javascript:alert('The R Program has not been configured properly, please request that an administrator configure R in the Admin Console.')");

        // external report
        if (context.getUser().isAdministrator())
        {
            ActionURL buttonURL = context.getActionURL().clone();
            buttonURL.setAction(ReportsController.ExternalReportAction.class);
            designers.put(ExternalReport.TYPE, buttonURL.getLocalURIString());
        }
    }

    public String getReportIcon(ViewContext context, String reportType)
    {
        if (StudyRReport.TYPE.equals(reportType))
            return context.getContextPath() + "/reports/r.gif";
        if (ChartReportView.TYPE.equals(reportType))
            return context.getContextPath() + "/reports/chart.gif";
        if (StudyQueryReport.TYPE.equals(reportType))
            return context.getContextPath() + "/reports/grid.gif";
        if (StudyChartQueryReport.TYPE.equals(reportType))
            return context.getContextPath() + "/reports/chart.gif";
        return super.getReportIcon(context, reportType);
    }
}