/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.RReport;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.view.DefaultReportUIProvider;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.reports.report.view.ScriptReportBean;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.study.Study;
import org.labkey.api.study.reports.CrosstabReport;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.visualization.TimeChartReport;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.reports.ReportsController;
import org.labkey.study.model.StudyManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * User: Karl Lum
 * Date: May 16, 2008
 * Time: 5:19:28 PM
 */
public class StudyReportUIProvider extends DefaultReportUIProvider
{
    private static Map<String, String> _typeToIconMap = new HashMap<>();
    private static Map<String, String> _typeToIconClsMap = new HashMap<>();

    static
    {
        _typeToIconMap.put(StudyRReport.TYPE, "/reports/r_logo.svg");
        _typeToIconMap.put(StudyQueryReport.TYPE, "/reports/grid.gif");
        _typeToIconMap.put(ExternalReport.TYPE, "/reports/advanced.png");
        _typeToIconMap.put(ParticipantReport.TYPE, "/reports/participantReport.png");
        _typeToIconMap.put(StudyCrosstabReport.TYPE, "/reports/crosstab.png");
        _typeToIconMap.put(CrosstabReport.TYPE, "/reports/crosstab.png");
        _typeToIconMap.put(AssayProgressReport.TYPE, "/reports/grid.png");

        // font icons - some report image icons dont have corresponding font icon replacements yet
//      _typeToIconClsMap.put(StudyRReport.TYPE, "/reports/r_logo.svg");
        _typeToIconClsMap.put(StudyQueryReport.TYPE, "fa fa-table");
        _typeToIconClsMap.put(ExternalReport.TYPE, "fa fa-cogs");
        _typeToIconClsMap.put(ParticipantReport.TYPE, "fa fa-clipboard");
//      _typeToIconClsMap.put(StudyCrosstabReport.TYPE, "/reports/crosstab.png");
//      _typeToIconClsMap.put(CrosstabReport.TYPE, "/reports/crosstab.png");
        _typeToIconClsMap.put(AssayProgressReport.TYPE, "fa fa-calendar");
    }

    private static final ReportService.ItemFilter _filter = (type, label) -> {
        if (ReportService.get().getGlobalItemFilterTypes().contains(type)) return true;
        if (StudyCrosstabReport.TYPE.equals(type)) return true;
        if (StudyRReport.TYPE.equals(type)) return true;
        if (ExternalReport.TYPE.equals(type)) return true;
        if (StudyQueryReport.TYPE.equals(type)) return true;
        if (TimeChartReport.TYPE.equals(type)) return true;
        if (ParticipantReport.TYPE.equals(type)) return true;
        return false;
    };

    /**
     * Add report creation to UI's that aren't associated with a query (manage views, data views)
     */
    @Override
    public List<ReportService.DesignerInfo> getDesignerInfo(ViewContext context)
    {
        List<ReportService.DesignerInfo> designers = new ArrayList<>();

        Study study = StudyManager.getInstance().getStudy(context.getContainer());

        if (study != null)
        {
            try
            {
                DesignerInfoImpl gridInfo = new DesignerInfoImpl(StudyQueryReport.TYPE, "Grid View", null,
                        new ActionURL(ReportsController.CreateQueryReportAction.class, context.getContainer()),
                        _getIconPath(StudyQueryReport.TYPE), ReportService.DesignerType.DEFAULT, _getIconCls(StudyQueryReport.TYPE));
                gridInfo.setId("create_gridView");
                gridInfo.setDisabled(!context.hasPermission(AdminPermission.class));
                designers.add(gridInfo);

                DesignerInfoImpl crosstabInfo = new DesignerInfoImpl(StudyCrosstabReport.TYPE, "Crosstab Report", null,
                        new ActionURL(ReportsController.CreateCrosstabReportAction.class, context.getContainer()),
                        _getIconPath(StudyCrosstabReport.TYPE), ReportService.DesignerType.DEFAULT, _getIconCls(StudyCrosstabReport.TYPE));
                crosstabInfo.setId("create_crosstabReport");
                designers.add(crosstabInfo);

                DesignerInfoImpl prInfo = new DesignerInfoImpl(ParticipantReport.TYPE, study.getSubjectNounSingular() + " Report", null,
                        new ActionURL(ReportsController.ParticipantReportAction.class, context.getContainer()),
                        _getIconPath(ParticipantReport.TYPE), ReportService.DesignerType.DEFAULT, _getIconCls(ParticipantReport.TYPE));
                prInfo.setId("create_participantReport");
                designers.add(prInfo);

                DesignerInfoImpl progressReportInfo = new DesignerInfoImpl(AssayProgressReport.TYPE, AssayProgressReport.REPORT_LABEL, null,
                        new ActionURL(ReportsController.AssayProgressReportAction.class, context.getContainer()),
                        _getIconPath(AssayProgressReport.TYPE), ReportService.DesignerType.DEFAULT, _getIconCls(AssayProgressReport.TYPE));
                prInfo.setId("create_assayProgressReport");
                designers.add(progressReportInfo);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
        return designers;
    }

    @Override
    public List<ReportService.DesignerInfo> getDesignerInfo(ViewContext context, QuerySettings settings)
    {
        List<ReportService.DesignerInfo> designers = new ArrayList<>();

        // crosstab designer
        ActionURL crossTabURL = new ActionURL(ReportsController.ParticipantCrosstabAction.class, context.getContainer());
        crossTabURL.addParameters(context.getActionURL().getParameters());
        crossTabURL.replaceParameter(QueryParam.schemaName, settings.getSchemaName());
        crossTabURL.replaceParameter(QueryParam.queryName, settings.getQueryName());
        crossTabURL.replaceParameter(QueryParam.viewName, settings.getViewName());
        crossTabURL.replaceParameter(QueryParam.dataRegionName, settings.getDataRegionName());

        URLHelper returnUrl = settings.getReturnURLHelper(context.getActionURL());
        crossTabURL.addParameter(ActionURL.Param.redirectUrl, returnUrl.getLocalURIString());

        if (StudySchema.getInstance().getSchemaName().equals(settings.getSchemaName()))
        {
            // crosstab report
            crossTabURL.addParameter(ReportDescriptor.Prop.reportType, StudyCrosstabReport.TYPE);
            designers.add(new DesignerInfoImpl(StudyCrosstabReport.TYPE, "Crosstab Report", null, crossTabURL, _getIconPath(StudyCrosstabReport.TYPE), ReportService.DesignerType.DEFAULT, _getIconCls(StudyCrosstabReport.TYPE)));

            // r report
            if (ReportUtil.canCreateScript(context, "r") && RReport.isEnabled())
            {
                ScriptReportBean rBean = new ScriptReportBean(settings);
                rBean.setReportType(StudyRReport.TYPE);
                rBean.setRedirectUrl(returnUrl.getLocalURIString());

                designers.add(new DesignerInfoImpl(StudyRReport.TYPE, "R Report", null, ReportUtil.getScriptReportDesignerURL(context, rBean),
                        _getIconPath(StudyRReport.TYPE), ReportService.DesignerType.DEFAULT, _getIconCls(StudyRReport.TYPE)));
            }

            // external report - keep in sync with ExternalReportAction permissions checks
            if (context.getContainer().hasPermission(context.getUser(), InsertPermission.class) && context.getUser().isPlatformDeveloper())
            {
                ActionURL buttonURL = context.getActionURL().clone();
                buttonURL.setAction(ReportsController.ExternalReportAction.class);
                designers.add(new DesignerInfoImpl(ExternalReport.TYPE, "Advanced Report", "An External Command Report",
                        buttonURL, _getIconPath(ExternalReport.TYPE), ReportService.DesignerType.DEFAULT, _getIconCls(ExternalReport.TYPE)));
            }
        }
        else
        {
            crossTabURL.addParameter(ReportDescriptor.Prop.reportType, CrosstabReport.TYPE);
            designers.add(new DesignerInfoImpl(CrosstabReport.TYPE, "Crosstab Report", null, crossTabURL, _getIconPath(CrosstabReport.TYPE), ReportService.DesignerType.DEFAULT, _getIconCls(CrosstabReport.TYPE)));
        }

        return designers;
    }

    private String _getIconPath(String type)
    {
        return _typeToIconMap.get(type);
    }

    public String getIconPath(Report report)
    {
        if (report != null)
        {
            return _getIconPath(report.getType());
        }
        return super.getIconPath(report);
    }

    private String _getIconCls(String type)
    {
        return _typeToIconClsMap.get(type);
    }

    public String getIconCls(Report report)
    {
        if (report != null)
        {
            return _getIconCls(report.getType());
        }
        return super.getIconCls(report);
    }

    public static ReportService.ItemFilter getItemFilter()
    {
        return _filter;
    }
}