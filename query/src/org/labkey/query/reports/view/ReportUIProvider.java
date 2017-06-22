/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.snapshot.QuerySnapshotService;
import org.labkey.api.reports.ExternalScriptEngineFactory;
import org.labkey.api.reports.LabKeyScriptEngineManager;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.ChartQueryReport;
import org.labkey.api.reports.report.ExternalScriptEngineReport;
import org.labkey.api.reports.report.InternalScriptEngineReport;
import org.labkey.api.reports.report.JavaScriptReport;
import org.labkey.api.reports.report.QueryReport;
import org.labkey.api.reports.report.RReport;
import org.labkey.api.reports.report.view.ChartDesignerBean;
import org.labkey.api.reports.report.view.DefaultReportUIProvider;
import org.labkey.api.reports.report.view.RReportBean;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.reports.report.view.ScriptReportBean;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ViewContext;
import org.labkey.query.reports.AttachmentReport;
import org.labkey.query.reports.LinkReport;
import org.labkey.query.reports.ReportsController;

import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * User: Karl Lum
 * Date: May 16, 2008
 * Time: 4:10:45 PM
 */
public class ReportUIProvider extends DefaultReportUIProvider
{
    private static Map<String, String> _typeToIconMap = new HashMap<>();
    private static Map<String, String> _typeToIconClsMap = new HashMap<>();

    static
    {
        _typeToIconMap.put(RReport.TYPE, "/reports/r_logo.svg");
        _typeToIconMap.put(ChartQueryReport.TYPE, "/reports/chart.gif");
        _typeToIconMap.put(JavaScriptReport.TYPE, "/reports/js.png");
        _typeToIconMap.put(AttachmentReport.TYPE, "/reports/attachment.png");
        _typeToIconMap.put(LinkReport.TYPE, "/reports/external-link.png");
        _typeToIconMap.put(QueryReport.TYPE, "/reports/grid.gif");

        // font icons - some report image icons dont have corresponding font icon replacements yet
//      _typeToIconClsMap.put(RReport.TYPE, "/reports/r_logo.svg");
        _typeToIconClsMap.put(ChartQueryReport.TYPE, "fa fa-area-chart");
//      _typeToIconClsMap.put(JavaScriptReport.TYPE, "/reports/js.png");
        _typeToIconClsMap.put(AttachmentReport.TYPE, "fa fa-paperclip");
        _typeToIconClsMap.put(LinkReport.TYPE, "fa fa-external-link-square");
        _typeToIconClsMap.put(QueryReport.TYPE, "fa fa-table");
        _typeToIconClsMap.put(QuerySnapshotService.TYPE, "fa fa-camera");
    }

    /**
     * Add report creation to UI's that aren't associated with a query (manage views, data views)
     */
    @Override
    public List<ReportService.DesignerInfo> getDesignerInfo(ViewContext context)
    {
        List<ReportService.DesignerInfo> designers = new ArrayList<>();

        if (RReport.isEnabled())
        {
            RReportBean bean = new RReportBean();
            bean.setReportType(RReport.TYPE);
            bean.setRedirectUrl(context.getActionURL().getLocalURIString());

            DesignerInfoImpl di = new DesignerInfoImpl(RReport.TYPE, "R Report", null, ReportUtil.getScriptReportDesignerURL(context, bean),
                    _getIconPath(RReport.TYPE), ReportService.DesignerType.DEFAULT, _getIconCls(RReport.TYPE));
            di.setId("create_rReport");
            di.setDisabled(!ReportUtil.canCreateScript(context));

            designers.add(di);
        }

        DesignerInfoImpl attachmentDesigner = new DesignerInfoImpl(AttachmentReport.TYPE, "Attachment Report", null,
                ReportsController.getCreateAttachmentReportURL(context.getContainer(), context.getActionURL()),
                _getIconPath(AttachmentReport.TYPE), ReportService.DesignerType.DEFAULT, _getIconCls(AttachmentReport.TYPE));
        attachmentDesigner.setId("create_attachment_report");
        attachmentDesigner.setDisabled(!context.hasPermission(InsertPermission.class));
        designers.add(attachmentDesigner);

        DesignerInfoImpl linkDesigner = new DesignerInfoImpl(LinkReport.TYPE, "Link Report", null,
                ReportsController.getCreateLinkReportURL(context.getContainer(), context.getActionURL()),
                _getIconPath(LinkReport.TYPE), ReportService.DesignerType.DEFAULT, _getIconCls(LinkReport.TYPE));
        linkDesigner.setId("create_link_report");
        linkDesigner.setDisabled(!context.hasPermission(InsertPermission.class));
        designers.add(linkDesigner);

        DesignerInfoImpl queryDesigner = new DesignerInfoImpl(QueryReport.TYPE, "Query Report", null,
                ReportsController.getCreateQueryReportURL(context.getContainer(), context.getActionURL()),
                _getIconPath(QueryReport.TYPE), ReportService.DesignerType.DEFAULT, _getIconCls(QueryReport.TYPE));
        queryDesigner.setId("create_query_report");
        queryDesigner.setDisabled(!context.hasPermission(InsertPermission.class));
        designers.add(queryDesigner);

        return designers;
    }

    @Override
    public List<ReportService.DesignerInfo> getDesignerInfo(ViewContext context, QuerySettings settings)
    {
        List<ReportService.DesignerInfo> designers = new ArrayList<>();

        ChartDesignerBean chartBean = new ChartDesignerBean(settings);
        chartBean.setReportType(ChartQueryReport.TYPE);

        URLHelper returnUrl = settings.getReturnUrl();
        if (returnUrl == null)
            returnUrl = context.getActionURL();
        chartBean.setRedirectUrl(returnUrl.getLocalURIString());

        StudyService svc = StudyService.get();
        Study study = null;
        if (svc != null)
        {
            study = svc.getStudy(context.getContainer());
        }

        if (study == null)
        {
            // Study registers its own 'Chart View'
            designers.add(new DesignerInfoImpl(ChartQueryReport.TYPE, "Chart View (deprecated)", "XY and Time Charts",
                    ReportUtil.getChartDesignerURL(context, chartBean), _getIconPath(ChartQueryReport.TYPE), ReportService.DesignerType.VISUALIZATION, _getIconCls(ChartQueryReport.TYPE)));
        }

        boolean canCreateScript = ReportUtil.canCreateScript(context);

        if (canCreateScript && RReport.isEnabled())
        {
            RReportBean rBean = new RReportBean(settings);
            rBean.setReportType(RReport.TYPE);
            rBean.setRedirectUrl(returnUrl.getLocalURIString());
            designers.add(new DesignerInfoImpl(RReport.TYPE, "R Report", null, ReportUtil.getScriptReportDesignerURL(context, rBean),
                    _getIconPath(RReport.TYPE), ReportService.DesignerType.DEFAULT, _getIconCls(RReport.TYPE)));
        }

        ScriptEngineManager manager = ServiceRegistry.get().getService(ScriptEngineManager.class);

        for (ScriptEngineFactory factory : manager.getEngineFactories())
        {
            // don't add an entry for R, since we have a specific report type above.
            if (canCreateScript && LabKeyScriptEngineManager.isFactoryEnabled(factory) && !factory.getLanguageName().equalsIgnoreCase("R"))
            {
                ScriptReportBean bean = new ScriptReportBean(settings);
                bean.setRedirectUrl(returnUrl.getLocalURIString());
                bean.setScriptExtension(factory.getExtensions().get(0));

                if (factory instanceof ExternalScriptEngineFactory)
                {
                    bean.setReportType(ExternalScriptEngineReport.TYPE);
                    designers.add(new DesignerInfoImpl(ExternalScriptEngineReport.TYPE, factory.getLanguageName() + " Report", null,
                            ReportUtil.getScriptReportDesignerURL(context, bean), _getIconPath(ExternalScriptEngineReport.TYPE), ReportService.DesignerType.DEFAULT, _getIconCls(ExternalScriptEngineReport.TYPE)));
                }
                else
                {
                    bean.setReportType(InternalScriptEngineReport.TYPE);
                    designers.add(new DesignerInfoImpl(InternalScriptEngineReport.TYPE, factory.getLanguageName() + " Report", null,
                            ReportUtil.getScriptReportDesignerURL(context, bean), _getIconPath(InternalScriptEngineReport.TYPE), ReportService.DesignerType.DEFAULT, _getIconCls(InternalScriptEngineReport.TYPE)));
                }
            }
        }

        // query snapshot
        if (context.hasPermission(AdminPermission.class))
        {
            QuerySnapshotService.Provider provider = QuerySnapshotService.get(settings.getSchemaName());
            if (provider != null && !QueryService.get().isQuerySnapshot(context.getContainer(), settings.getSchemaName(), settings.getQueryName()))
                designers.add(new DesignerInfoImpl(QuerySnapshotService.TYPE, "Query Snapshot", null,
                        provider.getCreateWizardURL(settings, context), _getIconPath(QuerySnapshotService.TYPE), ReportService.DesignerType.DEFAULT, _getIconCls(QuerySnapshotService.TYPE)));
        }

        if (canCreateScript)
        {
            ScriptReportBean bean = new ScriptReportBean(settings);
            bean.setRedirectUrl(returnUrl.getLocalURIString());
            bean.setScriptExtension(".js");
            bean.setReportType(JavaScriptReport.TYPE);
            designers.add(new DesignerInfoImpl(JavaScriptReport.TYPE, "JavaScript Report", "JavaScript Report",
                    ReportUtil.getScriptReportDesignerURL(context, bean), _getIconPath(JavaScriptReport.TYPE), ReportService.DesignerType.DEFAULT, _getIconCls(JavaScriptReport.TYPE)));
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
            if (report instanceof AttachmentReport)
            {
                Container c = ContainerManager.getForId(report.getContainerId());
                AttachmentReport attachmentReport = (AttachmentReport)report;
                String filename = attachmentReport.getFilePath();

                if (null == filename)
                {
                    Attachment attachment = attachmentReport.getLatestVersion();
                    filename = attachment == null ? null : attachment.getName();
                }

                return Attachment.getFileIcon(StringUtils.trimToEmpty(filename));
            }

            if (report instanceof LinkReport)
            {
                Container c = ContainerManager.getForId(report.getContainerId());
                LinkReport linkReport = (LinkReport)report;
                // external link versus internal link
                String url = linkReport.getUrl(c);
                if (url != null)
                {
                    // XXX: Is there a better way to check if a link is local to this server?
                    if (linkReport.isInternalLink())
                        return "/reports/internal-link.png";
                    else if (linkReport.isLocalLink())
                        return "/reports/local-link.png";
                    else
                        return "/reports/external-link.png";
                }
            }

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
            if (report instanceof AttachmentReport)
            {
                Container c = ContainerManager.getForId(report.getContainerId());
                AttachmentReport attachmentReport = (AttachmentReport)report;
                String filename = attachmentReport.getFilePath();

                if (null == filename)
                {
                    Attachment attachment = attachmentReport.getLatestVersion();
                    filename = attachment == null ? null : attachment.getName();
                }

                return Attachment.getFileIconFontCls(StringUtils.trimToEmpty(filename));
            }

            if (report instanceof LinkReport)
            {
                Container c = ContainerManager.getForId(report.getContainerId());
                LinkReport linkReport = (LinkReport)report;
                // external link versus internal link
                String url = linkReport.getUrl(c);
                if (url != null)
                {
                    // XXX: Is there a better way to check if a link is local to this server?
                    if (linkReport.isInternalLink())
                        return "fa fa-external-link-square fa-rotate-180";
                    else if (linkReport.isLocalLink())
                        return "fa fa-external-link-square fa-rotate-180";
                    else
                        return "fa fa-external-link-square";
                }
            }

            return _getIconCls(report.getType());
        }

        return super.getIconCls(report);
    }


}
