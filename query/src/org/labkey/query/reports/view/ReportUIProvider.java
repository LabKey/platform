/*
 * Copyright (c) 2008-2009 LabKey Corporation
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

import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.snapshot.QuerySnapshotService;
import org.labkey.api.reports.ExternalScriptEngineFactory;
import org.labkey.api.reports.LabkeyScriptEngineManager;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.ChartQueryReport;
import org.labkey.api.reports.report.ExternalScriptEngineReport;
import org.labkey.api.reports.report.InternalScriptEngineReport;
import org.labkey.api.reports.report.RReport;
import org.labkey.api.reports.report.view.*;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.view.ViewContext;
import org.labkey.api.security.ACL;

import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import java.util.ArrayList;
import java.util.List;

/*
 * User: Karl Lum
 * Date: May 16, 2008
 * Time: 4:10:45 PM
 */

public class ReportUIProvider extends DefaultReportUIProvider
{
    public List<ReportService.DesignerInfo> getDesignerInfo(ViewContext context, QuerySettings settings)
    {
        List<ReportService.DesignerInfo> designers = new ArrayList<ReportService.DesignerInfo>();

        ChartDesignerBean chartBean = new ChartDesignerBean(settings);
        chartBean.setReportType(ChartQueryReport.TYPE);
        chartBean.setRedirectUrl(context.getActionURL().getLocalURIString());
        designers.add(new DesignerInfoImpl(ChartQueryReport.TYPE, "Chart View", "XY and Time Charts", ReportUtil.getChartDesignerURL(context, chartBean)));

        if (RReport.canCreateScript(context) && RReport.isEnabled())
        {
            RReportBean rBean = new RReportBean(settings);
            rBean.setReportType(RReport.TYPE);
            rBean.setRedirectUrl(context.getActionURL().getLocalURIString());
            designers.add(new DesignerInfoImpl(RReport.TYPE, "R View", ReportUtil.getRReportDesignerURL(context, rBean)));
        }
        ScriptEngineManager manager = ServiceRegistry.get().getService(ScriptEngineManager.class);

        for (ScriptEngineFactory factory : manager.getEngineFactories())
        {
            // don't add an entry for R, since we have a specific report type above.
            if (LabkeyScriptEngineManager.isFactoryEnabled(factory) && !factory.getLanguageName().equalsIgnoreCase("R"))
            {
                ScriptReportBean bean = new ScriptReportBean(settings);
                bean.setRedirectUrl(context.getActionURL().getLocalURIString());
                bean.setScriptExtension(factory.getExtensions().get(0));

                if (factory instanceof ExternalScriptEngineFactory)
                {
                    bean.setReportType(ExternalScriptEngineReport.TYPE);
                    designers.add(new DesignerInfoImpl(ExternalScriptEngineReport.TYPE, factory.getLanguageName() + " View", ReportUtil.getScriptReportDesignerURL(context, bean)));
                }
                else
                {
                    bean.setReportType(InternalScriptEngineReport.TYPE);
                    designers.add(new DesignerInfoImpl(InternalScriptEngineReport.TYPE, factory.getLanguageName() + " View", ReportUtil.getScriptReportDesignerURL(context, bean)));
                }
            }
        }

        // query snapshot
        if (context.hasPermission(ACL.PERM_ADMIN))
        {
            QuerySnapshotService.I provider = QuerySnapshotService.get(settings.getSchemaName());
            if (provider != null && !QueryService.get().isQuerySnapshot(context.getContainer(), settings.getSchemaName(), settings.getQueryName()))
                designers.add(new DesignerInfoImpl(QuerySnapshotService.TYPE, "Query Snapshot", provider.getCreateWizardURL(settings, context)));
        }
        return designers;
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