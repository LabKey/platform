/*
 * Copyright (c) 2007-2011 LabKey Corporation
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

package org.labkey.query.reports;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.json.JSONArray;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ExtFormAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.GWTServiceAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.attachments.AttachmentForm;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ExcelWriter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ViewOptions;
import org.labkey.api.reports.ExternalScriptEngineDefinition;
import org.labkey.api.reports.ExternalScriptEngineFactory;
import org.labkey.api.reports.LabkeyScriptEngineManager;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.model.ViewInfo;
import org.labkey.api.reports.report.ChartQueryReport;
import org.labkey.api.reports.report.ChartReport;
import org.labkey.api.reports.report.RReport;
import org.labkey.api.reports.report.RReportJob;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.ReportIdentifier;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.reports.report.ScriptReportDescriptor;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.reports.report.r.ParamReplacementSvc;
import org.labkey.api.reports.report.view.AjaxScriptReportView;
import org.labkey.api.reports.report.view.AjaxScriptReportView.Mode;
import org.labkey.api.reports.report.view.ChartDesignerBean;
import org.labkey.api.reports.report.view.RReportBean;
import org.labkey.api.reports.report.view.RenderBackgroundRReportView;
import org.labkey.api.reports.report.view.ReportDesignBean;
import org.labkey.api.reports.report.view.ReportDesignerSessionCache;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.reports.report.view.RunReportView;
import org.labkey.api.reports.report.view.ScriptReportBean;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.EditSharedViewPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.settings.AdminConsole.SettingsLinkType;
import org.labkey.api.study.reports.CrosstabReport;
import org.labkey.api.thumbnail.BaseThumbnailAction;
import org.labkey.api.thumbnail.DynamicThumbnailProvider;
import org.labkey.api.thumbnail.StaticThumbnailProvider;
import org.labkey.api.thumbnail.ThumbnailService;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.IdentifierString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.GWTView;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.query.ViewFilterItemImpl;
import org.labkey.query.reports.chart.ChartServiceImpl;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.servlet.ServletException;
import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: Karl Lum
 * Date: Apr 19, 2007
 */
public class ReportsController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(ReportsController.class);

    public static final String TAB_SOURCE = "source";
    public static final String TAB_VIEW = "view";

    public static class ReportUrlsImpl implements ReportUrls
    {
        public ActionURL urlDownloadData(Container c)
        {
            return new ActionURL(DownloadInputDataAction.class, c);
        }

        public ActionURL urlRunReport(Container c)
        {
            return new ActionURL(RunReportAction.class, c);
        }

        public ActionURL urlSaveScriptReportState(Container c)
        {
            return new ActionURL(SaveScriptReportStateAction.class, c);
        }

        @Override
        public ActionURL urlAjaxSaveScriptReport(Container c)
        {
            return new ActionURL(AjaxSaveScriptReportAction.class, c);
        }

        public ActionURL urlUpdateRReportState(Container c)
        {
            return new ActionURL(UpdateScriptReportStateAction.class, c);
        }

        public ActionURL urlDesignChart(Container c)
        {
            return new ActionURL(DesignChartAction.class, c);
        }

        @Override
        public ActionURL urlViewScriptReport(Container c)
        {
            return new ActionURL(ViewScriptReportAction.class, c);
        }

        public ActionURL urlCreateScriptReport(Container c)
        {
            return new ActionURL(CreateScriptReportAction.class, c);
        }

        public ActionURL urlStreamFile(Container c)
        {
            return new ActionURL(StreamFileAction.class, c);
        }
        
        public ActionURL urlReportSections(Container c)
        {
            return new ActionURL(ReportSectionsAction.class, c);
        }

        public ActionURL urlManageViews(Container c)
        {
            return new ActionURL(ManageViewsAction.class, c);
        }

        public ActionURL urlPlotChart(Container c)
        {
            return new ActionURL(PlotChartAction.class, c);
        }

        public ActionURL urlDeleteReport(Container c)
        {
            return new ActionURL(DeleteReportAction.class, c);
        }

        public ActionURL urlManageViewsSummary(Container c)
        {
            return new ActionURL(ManageViewsSummaryAction.class, c);
        }

        public ActionURL urlExportCrosstab(Container c)
        {
            return new ActionURL(CrosstabExportAction.class, c);
        }

        @Override
        public ActionURL urlThumbnail(Container c, Report r)
        {
            ActionURL url = new ActionURL(ThumbnailAction.class, c);
            url.addParameter("reportId", r.getDescriptor().getReportId().toString());
            return url;
        }

        @Override
        public Class<? extends Controller> getDownloadClass()
        {
            return DownloadAction.class;
        }

        @Override
        public ActionURL urlReportInfo(Container c)
        {
            return new ActionURL(ReportInfoAction.class, c);
        }

        @Override
        public ActionURL urlAttachmentReport(Container c, ActionURL returnURL)
        {
            return getAttachmentReportURL(c, returnURL);
        }

        @Override
        public ActionURL urlReportDetails(Container c, Report r)
        {
            return new ActionURL(DetailsAction.class, c).addParameter("reportId", r.getDescriptor().getReportId().toString());
        }
    }

    public ReportsController() throws Exception
    {
        super();
        setActionResolver(_actionResolver);
    }

    public static void registerAdminConsoleLinks()
    {
        AdminConsole.addLink(SettingsLinkType.Configuration, "views and scripting", new ActionURL(ConfigureReportsAndScriptsAction.class, ContainerManager.getRoot()));
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class DesignChartAction extends SimpleViewAction<ChartDesignerBean>
    {
        public ModelAndView getView(ChartDesignerBean form, BindException errors) throws Exception
        {
            Map<String, String> props = new HashMap<String, String>();
            for (Pair<String, String> param : form.getParameters())
            {
                props.put(param.getKey(), param.getValue());
            }
            props.put("isAdmin", String.valueOf(getContainer().hasPermission(getUser(), AdminPermission.class)));
            props.put("isGuest", String.valueOf(getUser().isGuest()));
            HttpView view = new GWTView("org.labkey.reports.designer.ChartDesigner", props);
            return new VBox(view);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Create Chart View");
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ChartServiceAction extends GWTServiceAction
    {
        protected BaseRemoteService createService()
        {
            return new ChartServiceImpl(getViewContext());
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class PlotChartAction extends SimpleViewAction<ChartDesignerBean>
    {
        public ModelAndView getView(ChartDesignerBean form, BindException errors) throws Exception
        {
            final ViewContext context = getViewContext();
            Report report = null;

            if (null != form.getReportId())
                report = form.getReportId().getReport();

            if (report == null)
            {
                List<String> reportIds = context.getList("reportId");
                if (reportIds != null && !reportIds.isEmpty())
                    report = ReportService.get().getReport(NumberUtils.toInt(reportIds.get(0)));
            }

            if (report == null)
            {
                report = ReportService.get().createFromQueryString(context.getActionURL().getQueryString());
                if (report != null)
                {
                    // set the container in case we need to get a securable resource for the report descriptor
                    if (report.getDescriptor().lookupContainer() == null)
                        report.getDescriptor().setContainer(context.getContainer().getId());
                }
            }

            if (report instanceof Report.ImageReport)
                ((Report.ImageReport)report).renderImage(context);
            else if (report != null)
                throw new RuntimeException("Report must implement Report.ImageReport to use the plot chart action");
            
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class PlotChartApiAction extends ApiAction<ChartDesignerBean>
    {
        public ApiResponse execute(ChartDesignerBean form, BindException errors) throws Exception
        {
            verifyBean(form);
            ApiSimpleResponse response = new ApiSimpleResponse();
            Report report = form.getReport();
            if (report != null)
            {
                ActionURL url;
                if (null != report.getDescriptor().getReportId())
                    url = ReportUtil.getPlotChartURL(getViewContext(), form.getReport());
                else
                {
                    url = new ActionURL(PlotChartAction.class, getContainer());
                    for (Pair<String, String> param : form.getParameters())
                    {
                        url.addParameter(param.getKey(), param.getValue());
                    }
                }
                response.put("imageURL", url.getLocalURIString());

                if (report instanceof Report.ImageMapGenerator && !StringUtils.isEmpty(form.getImageMapName()))
                {
                    String map = ((Report.ImageMapGenerator)report).generateImageMap(getViewContext(), form.getImageMapName(),
                            form.getImageMapCallback(), form.getImageMapCallbackColumns());
                    response.put("imageMap", map);
                }
                return response;
            }
            throw new ServletException("Unable to render the specified chart");
        }

        private ChartDesignerBean verifyBean(ChartDesignerBean form) throws Exception
        {
            // a saved report
            if (null != form.getReportId())
                return form;

            UserSchema schema = (UserSchema) DefaultSchema.get(getUser(), getContainer()).getSchema(form.getSchemaName());
            if (schema != null)
            {
                QuerySettings settings = schema.getSettings(getViewContext(), form.getDataRegionName());
                QueryView view = schema.createView(getViewContext(), settings);
                if (view.getTable() == null)
                    throw new IllegalArgumentException("the specified query name: '" + form.getQueryName() + "' does not exist");
            }
            else
                throw new IllegalArgumentException("the specified schema: '" + form.getSchemaName() + "' does not exist");

            if (form.getReportType() == null)
            {
                // need to find a better way to handle this, if they are querying a study schema they have
                // to use a study report type in order to get study security in their chart.
                form.setReportType("study".equals(form.getSchemaName()) ? "Study.chartQueryReport" : ChartQueryReport.TYPE);
            }
            return form;
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class DeleteReportAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            String reportId = getViewContext().getRequest().getParameter(ReportDescriptor.Prop.reportId.name());
            String forwardUrl = getViewContext().getRequest().getParameter(ReportUtil.FORWARD_URL);
            Report report = null;

            if (reportId != null)
                report = ReportService.get().getReport(NumberUtils.toInt(reportId));

            if (report != null)
            {
                if (!report.getDescriptor().canEdit(getViewContext().getUser(), getViewContext().getContainer()))
                    throw new UnauthorizedException();
                ReportService.get().deleteReport(getViewContext(), report);
            }
            return HttpView.redirect(forwardUrl);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class ConfigureReportsAndScriptsAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new JspView("/org/labkey/query/reports/view/configReportsAndScripts.jsp");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Admin Console", PageFlowUtil.urlProvider(AdminUrls.class).getAdminConsoleURL());
            return root.addChild("Views and Scripting Configuration");
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class ScriptEnginesSummaryAction extends ApiAction
    {
        public ApiResponse execute(Object o, BindException errors) throws Exception
        {
            List<Map<String, String>> views = new ArrayList<Map<String, String>>();

            ScriptEngineManager manager = ServiceRegistry.get().getService(ScriptEngineManager.class);

            for (ScriptEngineFactory factory : manager.getEngineFactories())
            {
                Map<String, String> record = new HashMap<String, String>();

                record.put("name", factory.getEngineName());
                record.put("extensions", StringUtils.join(factory.getExtensions(), ','));
                record.put("languageName", factory.getLanguageName());
                record.put("languageVersion", factory.getLanguageVersion());

                boolean isExternal = factory instanceof ExternalScriptEngineFactory;
                record.put("external", String.valueOf(isExternal));
                record.put("enabled", String.valueOf(LabkeyScriptEngineManager.isFactoryEnabled(factory)));

                if (isExternal)
                {
                    // extra metadata for external engines
                    ExternalScriptEngineDefinition def = ((ExternalScriptEngineFactory)factory).getDefinition();

                    if (def instanceof LabkeyScriptEngineManager.EngineDefinition)
                        record.put("key", ((LabkeyScriptEngineManager.EngineDefinition)def).getKey());
                    record.put("exePath", def.getExePath());
                    record.put("exeCommand", def.getExeCommand());
                    record.put("outputFileName", def.getOutputFileName());
                }
                views.add(record);
            }
            return new ApiSimpleResponse("views", views);
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class ScriptEnginesSaveAction extends ExtFormAction<LabkeyScriptEngineManager.EngineDefinition>
    {
        @Override
        public void validateForm(LabkeyScriptEngineManager.EngineDefinition def, Errors errors)
        {
            // validate definition
            if (StringUtils.isEmpty(def.getName()))
                errors.rejectValue("name", ERROR_MSG, "The Name field cannot be empty");

            if (def.isExternal())
            {
                File rexe = new File(def.getExePath());
                if (!rexe.exists())
                    errors.rejectValue("exePath", ERROR_MSG, "The program location: '" + def.getExePath() + "' does not exist");
                if (rexe.isDirectory())
                    errors.rejectValue("exePath", ERROR_MSG, "Please specify the entire path to the program, not just the directory (e.g., 'c:/Program Files/R/R-2.7.1/bin/R.exe)");
            }
        }

        public ApiResponse execute(LabkeyScriptEngineManager.EngineDefinition def, BindException errors) throws Exception
        {
            LabkeyScriptEngineManager.saveDefinition(def);

            return new ApiSimpleResponse("success", true);
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class ScriptEnginesDeleteAction extends ApiAction<LabkeyScriptEngineManager.EngineDefinition>
    {
        public ApiResponse execute(LabkeyScriptEngineManager.EngineDefinition def, BindException errors) throws Exception
        {
            LabkeyScriptEngineManager.deleteDefinition(def);
            return new ApiSimpleResponse("success", true);
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class SaveScriptReportStateAction extends FormViewAction<RReportBean>
    {
        public ModelAndView getView(RReportBean bean, boolean reshow, BindException errors) throws Exception
        {
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;  
        }

        public void validateCommand(RReportBean bean, Errors errors)
        {
        }

        public boolean handlePost(RReportBean bean, BindException errors) throws Exception
        {
            bean.setIsDirty(true);
            ReportDesignerSessionCache.updateReportCache(bean, true);
            return true;
        }

        public ActionURL getSuccessURL(RReportBean bean)
        {
            return null;
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class UpdateScriptReportStateAction extends SaveScriptReportStateAction
    {
        public boolean handlePost(RReportBean form, BindException errors) throws Exception
        {
            //form.setIsDirty(true);    // TODO: Remove RReport dependencies?
            assert false : "This code should not be executed";
            Report report = form.getReport();
            if (report instanceof RReport)
                report.clearCache();
            ReportDesignerSessionCache.updateReportCache(form, false);
            return true;
        }
    }


    @RequiresNoPermission
    public class CreateScriptReportAction extends FormViewAction<ScriptReportBean>
    {
        private Report _report;

        public void validateCommand(ScriptReportBean form, Errors errors)
        {
        }

        public ModelAndView getView(ScriptReportBean form, boolean reshow, BindException errors) throws Exception
        {
            validatePermissions();
            _report = form.getReport();

            return new AjaxScriptReportView(null, form, Mode.create);
        }

        public boolean handlePost(ScriptReportBean form, BindException errors) throws Exception
        {
            return true;
        }

        public ActionURL getSuccessURL(ScriptReportBean form)
        {
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild(_report.getTypeDescription() + " Builder");
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class ViewScriptReportAction extends ApiAction<ScriptReportBean>
    {
        @Override
        public ApiResponse execute(ScriptReportBean bean, BindException errors) throws Exception
        {
            // TODO: Do something with errors?

            // ApiAction doesn't seem to bind URL parameters on POST... so manually populate them into the bean.
            errors.addAllErrors(defaultBindParameters(bean, getViewContext().getBindPropertyValues()));

            HttpView resultsView = null;
            Report report = bean.getReport();

            // for now, limit pipeline view to saved R reports
            if (null != bean.getReportId() && bean.isRunInBackground())
            {
                if (report instanceof RReport)
                {
                    resultsView = new JspView<RReport>("/org/labkey/api/reports/report/view/ajaxReportRenderBackground.jsp", (RReport)report);
                }
            }
            else
            {
                if (report != null)
                {
                    if (bean.getIsDirty())
                        report.clearCache();
                    resultsView = report.renderReport(getViewContext());
                }
            }

            // TODO: assert?
            if (null != resultsView)
                resultsView.render(getViewContext().getRequest(), getViewContext().getResponse());

            return null;
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class GetBackgroundReportResultsAction extends ApiAction<ScriptReportBean>
    {
        @Override
        public ApiResponse execute(ScriptReportBean bean, BindException errors) throws Exception
        {
            Report report = bean.getReport();
            File logFile = new File(((RReport)report).getReportDir(), RReportJob.LOG_FILE_NAME);
            PipelineStatusFile statusFile = PipelineService.get().getStatusFile(logFile.getAbsolutePath());

            VBox vbox = new VBox();

            StringBuilder html = new StringBuilder("<table>\n");

            if (null != statusFile)
            {
                html.append("<tr><td class=\"labkey-form-label\">Description</td><td>");
                html.append(PageFlowUtil.filter(statusFile.getDescription()));
                html.append("</td></tr>\n");
                html.append("<tr><td class=\"labkey-form-label\">Status</td><td>");
                html.append(PageFlowUtil.filter(statusFile.getStatus()));
                html.append("</td></tr>\n");
                html.append("<tr><td class=\"labkey-form-label\">Email</td><td>");
                html.append(PageFlowUtil.filter(statusFile.getEmail()));
                html.append("</td></tr>\n");
                html.append("<tr><td class=\"labkey-form-label\">Info</td><td>");
                html.append(PageFlowUtil.filter(StringUtils.defaultString(statusFile.getInfo(), "")));
                html.append("</td></tr>\n");
                html.append("<tr><td colspan=\"2\">&nbsp;</td></tr>\n");
            }
            else
            {
                html.append("<tr><td class=\"labkey-form-label\">Status</td><td>Not Run</td></tr>");
            }

            html.append("<table>\n");
            vbox.addView(new HtmlView(html.toString()));

            if (statusFile != null &&
                    !(statusFile.getStatus().equals(PipelineJob.WAITING_STATUS) ||
                      statusFile.getStatus().equals(RReportJob.PROCESSING_STATUS)))
                vbox.addView(new RenderBackgroundRReportView((RReport)report));

            ApiSimpleResponse response = new ApiSimpleResponse();
            response.put("results", HttpView.renderToString(vbox, getViewContext().getRequest()));

            if (null != statusFile)
                response.put("status", statusFile.getStatus());

            return response;
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class RunReportAction extends SimpleViewAction<ReportDesignBean>
    {
        Report _report;

        public ModelAndView getView(ReportDesignBean form, BindException errors) throws Exception
        {
            _report = null;

            if (null != form.getReportId())
                _report = form.getReportId().getReport();

            if (_report != null)
                return _report.getRunReportView(getViewContext());
            
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            if (_report != null)
                return root.addChild(_report.getDescriptor().getReportName());
            return null;
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class DetailsAction extends SimpleViewAction<ReportDesignBean>
    {
        public ModelAndView getView(ReportDesignBean form, BindException errors) throws Exception
        {
            if (form.getReport() != null)
                return new JspView<ReportDesignBean>("/org/labkey/query/reports/view/reportDetails.jsp", form);
            else
                return new HtmlView("Specified report not found");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Report Details");
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ReportInfoAction extends SimpleViewAction<ReportDesignBean>
    {
        public ModelAndView getView(ReportDesignBean form, BindException errors) throws Exception
        {
            return new ReportInfoView(form.getReport());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Report Debug Information");
        }
    }


    public static class ReportInfoView extends HttpView
    {
        private Report _report;

        public ReportInfoView(Report report)
        {
            _report = report;
        }

        protected void renderInternal(Object model, PrintWriter out) throws Exception
        {
            if (_report != null)
            {
                out.write("<table>");
                addRow(out, "Name", PageFlowUtil.filter(_report.getDescriptor().getReportName()));

                User user = UserManager.getUser(_report.getDescriptor().getCreatedBy());
                if (user != null)
                    addRow(out, "Created By", PageFlowUtil.filter(user.getDisplayName(getViewContext().getUser())));

                addRow(out, "Key", PageFlowUtil.filter(_report.getDescriptor().getReportKey()));
                for (Map.Entry<String, Object> prop : _report.getDescriptor().getProperties().entrySet())
                {
                    addRow(out, PageFlowUtil.filter(prop.getKey()), PageFlowUtil.filter(ObjectUtils.toString(prop.getValue())));
                }
                out.write("<table>");
            }
            else
                out.write("Report not found");
        }

        private void addRow(PrintWriter out, String key, String value)
        {
            out.write("<tr><td>");
            out.write(key);
            out.write("</td><td>");
            out.write(value);
            out.write("</td></tr>");
        }
    }


    protected void validatePermissions() throws Exception
    {
        if (!ReportUtil.canCreateScript(getViewContext()))
            throw new UnauthorizedException("Only members of the Site Admin and Site Developers groups are allowed to create script views.");
    }


    @RequiresNoPermission
    public class AjaxSaveScriptReportAction extends ApiAction<RReportBean>
    {
        @Override
        public ApiResponse execute(RReportBean form, BindException errors) throws Exception
        {
            Report report = null;

            try
            {
                if (getViewContext().getUser().isGuest())
                {
                    errors.reject("saveScriptReport", "You must be logged in to be able to save reports");
                    return null;
                }

                report = form.getReport();

                if (null == report)
                {
                    errors.reject("saveScriptReport", "Report not found.");
                }
                // on new reports, check for duplicates
                else if (null == report.getDescriptor().getReportId())
                {
                    if (reportNameExists(report.getDescriptor().getReportName(), ReportUtil.getReportQueryKey(report.getDescriptor())))
                    {
                        errors.reject("saveScriptReport", "There is already a report with the name of: '" + report.getDescriptor().getReportName() +
                                "'. Please specify a different name.");
                    }
                }
            }
            catch (Exception e)
            {
                errors.reject("saveScriptReport", e.getMessage());
            }

            if (errors.hasErrors())
                return null;

            validatePermissions();
            ReportService.get().saveReport(getViewContext(), ReportUtil.getReportQueryKey(report.getDescriptor()), report);

            if (report instanceof DynamicThumbnailProvider)
            {
                ThumbnailService svc = ServiceRegistry.get().getService(ThumbnailService.class);

                if (null != svc)
                {
                    DynamicThumbnailProvider provider = (DynamicThumbnailProvider) report;
                    svc.replaceThumbnail(provider, getViewContext());
                }
            }

            ApiSimpleResponse response = new ApiSimpleResponse();

            response.put("success", true);
            response.put("redirect", form.getRedirectUrl());

            return response;
        }

        // TODO: Use shared method instead?
        private boolean reportNameExists(String reportName, String key)
        {
            try
            {
                ViewContext context = getViewContext();

                for (Report report : ReportService.get().getReports(context.getUser(), context.getContainer(), key))
                {
                    if (StringUtils.equals(reportName, report.getDescriptor().getReportName()))
                        return true;
                }

                return false;
            }
            catch (Exception e)
            {
                return false;
            }
        }
    }


    /**
     * Ajax action to start a pipeline-based R view.
     */
    @RequiresPermissionClass(ReadPermission.class)
    public class StartBackgroundRReportAction extends ApiAction<RReportBean>
    {
        public ApiResponse execute(RReportBean form, BindException errors) throws Exception
        {
            final ViewContext context = getViewContext();
            final Container c = getContainer();
            final ViewBackgroundInfo info = new ViewBackgroundInfo(c, context.getUser(), context.getActionURL());
            ApiSimpleResponse response = new ApiSimpleResponse();

            Report report;
            PipelineJob job;
            PipeRoot root = PipelineService.get().findPipelineRoot(getContainer());

            if (null == form.getReportId())
            {
                // report not saved yet, get state from the cache
                String key = getViewContext().getActionURL().getParameter(RunReportView.CACHE_PARAM);
                if (key != null && ReportDesignerSessionCache.isCacheValid(key, context))
                    ReportDesignerSessionCache.populateBeanFromCache(form, key, context);
                report = form.getReport();
                job = new RReportJob(ReportsPipelineProvider.NAME, info, form, root);
            }
            else
            {
                report = form.getReportId().getReport();
                job = new RReportJob(ReportsPipelineProvider.NAME, info, form.getReportId(), root);
            }

            if (report instanceof RReport)
            {
                ((RReport)report).createInputDataFile(getViewContext());
                PipelineService.get().getPipelineQueue().addJob(job);
                response.put("success", true);
            }

            return response;
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class DownloadInputDataAction extends SimpleViewAction<RReportBean>
    {
        public ModelAndView getView(RReportBean form, BindException errors) throws Exception
        {
            Report report = form.getReport();
            if (report instanceof RReport)
            {
                File file = ((RReport)report).createInputDataFile(getViewContext());
                if (file.exists())
                {
                    PageFlowUtil.streamFile(getViewContext().getResponse(), file, true);
                }
            }
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class StreamFileAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            String sessionKey = (String) getViewContext().get("sessionKey");
            String deleteFile = (String) getViewContext().get("deleteFile");
            String attachment = (String) getViewContext().get("attachment");
            String cacheFile = (String) getViewContext().get("cacheFile");
            if (sessionKey != null)
            {
                File file = (File) getViewContext().getRequest().getSession().getAttribute(sessionKey);
                if (file != null && file.exists())
                {
                    Map<String, String> responseHeaders = Collections.emptyMap();
                    if (BooleanUtils.toBoolean(cacheFile))
                    {
                        responseHeaders = new HashMap<String, String>();

                        responseHeaders.put("Pragma", "private");
                        responseHeaders.put("Cache-Control", "private");
                        responseHeaders.put("Cache-Control", "max-age=3600");
                    }
                    PageFlowUtil.streamFile(getViewContext().getResponse(), responseHeaders, file, BooleanUtils.toBoolean(attachment));
                    if (BooleanUtils.toBoolean(deleteFile))
                        file.delete();
                    return null;
                }
            }
            return new HtmlView("Requested Resource not found");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class DownloadAction extends SimpleViewAction<AttachmentForm>
    {
        public ModelAndView getView(AttachmentForm form, BindException errors) throws Exception
        {
            SimpleFilter filter = new SimpleFilter("ContainerId", getContainer().getId());
            filter.addCondition("EntityId", form.getEntityId());

            Report[] report = ReportService.get().getReports(filter);
            if (report.length != 1)
            {
                throw new NotFoundException("Unable to find report");
            }

            if (report[0] instanceof RReport || report[0] instanceof AttachmentReport)
                AttachmentService.get().download(getViewContext().getResponse(), report[0], form.getName());

            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    public static class UploadForm extends ReturnUrlForm
    {
        private ReportIdentifier _reportId;
        private String label;
        private String reportDate;
        private String filePath;
        private String description;
        private Integer category;
        private Boolean shared;
        private BindException _errors;

        public String getReportDateString()
        {
            return reportDate;
        }

        public void setReportDateString(String reportDate)
        {
            this.reportDate = reportDate;
        }

        public ReportIdentifier getReportId()
        {
            return _reportId;
        }

        public void setReportId(ReportIdentifier reportId)
        {
            _reportId = reportId;
        }

        public String getLabel()
        {
            return label;
        }

        public void setLabel(String label)
        {
            this.label = label;
        }

        public String getFilePath()
        {
            return filePath;
        }

        public void setFilePath(String filePath)
        {
            this.filePath = filePath;
        }

        public String getDescription()
        {
            return description;
        }

        public void setDescription(String description)
        {
            this.description = description;
        }

        public Integer getCategory()
        {
            return category;
        }

        public void setCategory(Integer category)
        {
            this.category = category;
        }

        public Boolean getShared()
        {
            return shared;
        }

        public void setShared(Boolean shared)
        {
            this.shared = shared;
        }

        public void setErrors(BindException errors){_errors = errors;}
        public BindException getErrors(){return _errors;}
    }


    public static ActionURL getAttachmentReportURL(Container c, ActionURL returnURL)
    {
        ActionURL url = new ActionURL(UploadReportAction.class, c);
        url.addReturnURL(returnURL);

        return url;
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class UploadReportAction extends FormViewAction<UploadForm>
    {
        public ModelAndView getView(UploadForm form, boolean reshow, BindException errors) throws Exception
        {
            setHelpTopic(new HelpTopic("staticReports"));
            form.setErrors(errors);

            if (form.getReportId() != null)
            {
                Report report = form.getReportId().getReport();

                if (report != null)
                {
                    form.setLabel(report.getDescriptor().getReportName());
                    form.setReportId(form.getReportId());
                }
            }

            return new JspView<UploadForm>("/org/labkey/query/reports/view/uploadAttachmentReport.jsp", form);
        }

        public void validateCommand(UploadForm form, Errors errors)
        {
            Map<String, MultipartFile> fileMap = getFileMap();
            MultipartFile[] formFiles = fileMap.values().toArray(new MultipartFile[fileMap.size()]);

            if (null == StringUtils.trimToNull(form.getLabel()))
                errors.reject("uploadForm", "You must enter a report name.");

            String filePath = null;
            if (null != form.getFilePath())
                filePath = StringUtils.trimToNull(form.getFilePath());
            if (null == filePath && (0 == formFiles.length || formFiles[0].isEmpty()))
                errors.reject("uploadForm", "You must specify a file");

            String dateStr = form.getReportDateString();
            if (dateStr != null && dateStr.length() > 0)
            {
                try
                {
                    Long l = DateUtil.parseDateTime(dateStr);
                    Date reportDate = new Date(l);
                }
                catch (ConversionException x)
                {
                    errors.reject("uploadForm", "You must enter a legal report date");
                }
            }
        }

        public boolean handlePost(UploadForm form, BindException errors) throws Exception
        {
            AttachmentReport report = (AttachmentReport)ReportService.get().createReportInstance(AttachmentReport.TYPE);

            report.getDescriptor().setContainer(getContainer().getId());
            report.getDescriptor().setReportName(form.getLabel());
            if (!StringUtils.isEmpty(form.getReportDateString()))
                report.setModified(new Date(DateUtil.parseDateTime(form.getReportDateString())));
            report.setFilePath(form.getFilePath());
            report.setDescription(form.getDescription());
            report.setCategory(form.getCategory());
            if(!form.getShared())
            {
                report.setOwner(getUser().getUserId());
            } else {
                report.setOwner(null);
            }

            int id = ReportService.get().saveReport(getViewContext(), form.getLabel(), report);

            report = (AttachmentReport)ReportService.get().getReport(id);
            AttachmentService.get().addAttachments(report, getAttachmentFileList(), getViewContext().getUser());

            ThumbnailService svc = ServiceRegistry.get().getService(ThumbnailService.class);

            if (null != svc)
                svc.queueThumbnailRendering(report);

            return true;
        }

        public ActionURL getSuccessURL(UploadForm uploadForm)
        {
            return uploadForm.getReturnActionURL();
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Upload Report");
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class DownloadReportFileAction extends SimpleViewAction<UploadForm>
    {
        public ModelAndView getView(UploadForm form, BindException errors) throws Exception
        {
            ReportIdentifier reportId = form.getReportId();

            if (null == reportId)
                throw new NotFoundException("ReportId not specified");

            Report report = reportId.getReport();

            if (null == report)
                throw new NotFoundException("Report not found");

            if (report instanceof AttachmentReport)
            {
                AttachmentReport aReport = (AttachmentReport)report;

                if (null == aReport.getFilePath())
                    throw new NotFoundException();

                File file = new File(aReport.getFilePath());
                if (!file.exists())
                    throw new NotFoundException("Could not find file with name " + aReport.getFilePath());

                PageFlowUtil.streamFile(getViewContext().getResponse(), file, true);
            }
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class ManageViewsAction extends SimpleViewAction<ViewsSummaryForm>
    {
        public ModelAndView getView(ViewsSummaryForm form, BindException errors) throws Exception
        {
            return new JspView<ViewsSummaryForm>("/org/labkey/query/reports/view/manageViews.jsp", form, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Manage Views");
        }
    }


    public static class ViewOptionsForm extends ViewsSummaryForm
    {
        private String[] _viewItemTypes = new String[0];

        public String[] getViewItemTypes()
        {
            return _viewItemTypes;
        }

        public void setViewItemTypes(String[] viewItemTypes)
        {
            _viewItemTypes = viewItemTypes;
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class ViewOptionsAction extends ApiAction<ViewOptionsForm>
    {
        public ApiResponse execute(ViewOptionsForm form, BindException errors) throws Exception
        {
            List<Map<String, String>> response = new ArrayList<Map<String, String>>();
            QueryDefinition def = QueryService.get().getQueryDef(getUser(), getContainer(), form.getSchemaName(), form.getQueryName());
            if (def == null)
            {
                UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), form.getSchemaName());
                def = QueryService.get().createQueryDefForTable(schema, form.getQueryName());
            }

            if (def != null)
            {
                Map<String, ViewOptions.ViewFilterItem> filterItemMap = new HashMap<String, ViewOptions.ViewFilterItem>();
                Map<String, String> baseItemMap = new HashMap<String, String>();

                if (!StringUtils.isBlank(form.getBaseFilterItems()))
                {
                    String baseFilterItems = PageFlowUtil.decode(form.getBaseFilterItems());
                    for (String item : baseFilterItems.split("&"))
                        baseItemMap.put(item, item);
                }

                for (ViewOptions.ViewFilterItem item : def.getViewOptions().getViewFilterItems())
                    filterItemMap.put(item.getViewType(), item);

                Collection<ReportService.DesignerInfo> designers = getAvailableReportDesigners(form);
                Map<String, Integer> duplicates = new HashMap<String, Integer>();

                for (ReportService.DesignerInfo info : designers)
                {
                    Integer count = 0;
                    if (duplicates.containsKey(info.getLabel()))
                    {
                        count = duplicates.get(info.getLabel());
                        count++;
                    }
                    duplicates.put(info.getLabel(), count);
                }

                UserSchema schema = QueryService.get().getUserSchema(getViewContext().getUser(), getViewContext().getContainer(), form.getSchemaName());
                QuerySettings settings = schema.getSettings(getViewContext(), null, form.getQueryName());
                QueryView view = schema.createView(getViewContext(), settings, errors);

                if (view != null)
                {
                    ReportService.ItemFilter filter = view.getItemFilter();

                    for (ReportService.DesignerInfo info : getAvailableReportDesigners(form))
                    {
                        Map<String, String> record = new HashMap<String, String>();
                        String label = info.getLabel();

                        // if there are duplicates, let the view item filter choose which one to display
                        if (duplicates.get(label) > 0)
                        {
                            if (!filter.accept(info.getReportType(), info.getLabel()))
                                continue;
                        }
                        record.put("reportType", info.getReportType());
                        record.put("reportLabel", label);
                        record.put("reportDescription", info.getDescription());

                        if (filterItemMap.containsKey(info.getReportType()))
                            record.put("enabled", String.valueOf(filterItemMap.get(info.getReportType()).isEnabled()));
                        else
                            record.put("enabled", String.valueOf(baseItemMap.containsKey(info.getReportType())));

                        response.add(record);
                    }
                }
            }
            return new ApiSimpleResponse("viewOptions", response);
        }
    }

    private Collection<ReportService.DesignerInfo> getAvailableReportDesigners(ViewOptionsForm form)
    {
        List<ReportService.DesignerInfo> designers = new ArrayList<ReportService.DesignerInfo>();
        UserSchema schema = QueryService.get().getUserSchema(getViewContext().getUser(), getViewContext().getContainer(), form.getSchemaName());
        QuerySettings settings = schema.getSettings(getViewContext(), null, form.getQueryName());

        // build the list available view types by combining the available types and the built in item filter types
        for (ReportService.UIProvider provider : ReportService.get().getUIProviders())
        {
            for (ReportService.DesignerInfo info : provider.getDesignerInfo(getViewContext(), settings))
            {
                designers.add(info);
            }
        }

        Collections.sort(designers, new Comparator<ReportService.DesignerInfo>(){
            @Override
            public int compare(ReportService.DesignerInfo o1, ReportService.DesignerInfo o2)
            {
                return o1.getLabel().compareToIgnoreCase(o2.getLabel());
            }
        });
        return designers;
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class ManageViewsUpdateViewOptionsAction extends ExtFormAction<ViewOptionsForm>
    {
        public ApiResponse execute(ViewOptionsForm form, BindException errors) throws Exception
        {
            QueryDefinition def = QueryService.get().getQueryDef(getUser(), getContainer(), form.getSchemaName(), form.getQueryName());
            if (def == null)
            {
                UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), form.getSchemaName());
                def = QueryService.get().createQueryDefForTable(schema, form.getQueryName());
            }
            ViewOptions options = def.getViewOptions();
            List<ViewOptions.ViewFilterItem> filterItems = new ArrayList<ViewOptions.ViewFilterItem>();
            Map<String, String> viewItemMap = new HashMap<String, String>();

            for (String type : form.getViewItemTypes())
                viewItemMap.put(type, type);

            for (ReportService.DesignerInfo info : getAvailableReportDesigners(form))
                filterItems.add(new ViewFilterItemImpl(info.getReportType(), viewItemMap.containsKey(info.getReportType())));

            options.setViewFilterItems(filterItems);
            options.save(getUser());

            return new ApiSimpleResponse("success", true);
        }
    }

    public static class ViewsSummaryForm
    {
        private String _schemaName;
        private String _queryName;
        private String _baseFilterItems;

        public String getSchemaName()
        {
            return _schemaName;
        }

        public void setSchemaName(String schemaName)
        {
            _schemaName = schemaName;
        }

        public String getQueryName()
        {
            return _queryName;
        }

        public void setQueryName(String queryName)
        {
            _queryName = queryName;
        }

        public String getBaseFilterItems()
        {
            return _baseFilterItems;
        }

        public void setBaseFilterItems(String baseFilterItems)
        {
            _baseFilterItems = baseFilterItems;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ManageViewsSummaryAction extends ApiAction<ViewsSummaryForm>
    {
        public ApiResponse execute(ViewsSummaryForm form, BindException errors) throws Exception
        {
            boolean isAdmin = getViewContext().getContainer().hasPermission(getViewContext().getUser(), AdminPermission.class);
            JSONArray views = new JSONArray();

            for (ViewInfo info :  ReportUtil.getViews(getViewContext(), form.getSchemaName(), form.getQueryName(), isAdmin))
                views.put(info.toJSON(getUser()));

            return new ApiSimpleResponse("views", views);
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ManageViewsDeleteReportsAction extends ApiAction<DeleteViewsForm>
    {
        public ApiResponse execute(DeleteViewsForm form, BindException errors) throws Exception
        {
            for (ReportIdentifier id : form.getReportId())
            {
                Report report = id.getReport();

                if (report != null)
                {
                    if (!report.getDescriptor().canEdit(getViewContext().getUser(), getViewContext().getContainer()))
                        throw new UnauthorizedException();
                    ReportService.get().deleteReport(getViewContext(), report);
                }
            }

            for (QueryForm qf : form.getQueryForms(getViewContext()))
            {
                CustomView customView = qf.getCustomView();
                if (customView != null)
                {
                    if (customView.isShared())
                    {
                        if (!getViewContext().getContainer().hasPermission(getUser(), EditSharedViewPermission.class))
                            throw new UnauthorizedException();
                    }
                    customView.delete(getUser(), getViewContext().getRequest());
                }
            }
            return new ApiSimpleResponse("success", true);
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ManageViewsEditReportsAction extends ExtFormAction<EditViewsForm>
    {
        private CustomView _view;
        private Report _report;

        @Override
        public void validateForm(EditViewsForm form, Errors errors)
        {
            if (form.getViewId() != null)
                validateEditView(form, errors);
            else
                validateEditReport(form, errors);
        }

        private void validateEditView(EditViewsForm form, Errors errors)
        {
            try
            {
                QueryForm queryForm = getQueryForm(getViewContext(), form.getViewId());
                _view = queryForm.getCustomView();
                if (_view != null)
                {
                    _view.canEdit(getContainer(), errors);

                    if (_view.getOwner() == null && !getContainer().hasPermission(getUser(), EditSharedViewPermission.class))
                        errors.reject(null, "You don't have permission to edit shared views");

                    if (!StringUtils.equals(_view.getName(), form.getViewName()))
                    {
                        if (null != QueryService.get().getCustomView(getUser(), getContainer(), _view.getSchemaName(), _view.getQueryName(), form.getViewName()))
                            errors.rejectValue("viewName", ERROR_MSG, "There is already a view with the name of: " + form.getViewName());
                    }
                }
                else
                    errors.rejectValue("viewName", ERROR_MSG, "An error occurred saving the view.");
            }
            catch (Exception e)
            {
                errors.rejectValue("viewName", ERROR_MSG, "An error occurred saving the view.");
            }
        }

        private void validateEditReport(EditViewsForm form, Errors errors)
        {
            try {
                _report = form.getReportId().getReport();

                if (_report != null)
                {
                    if (!_report.getDescriptor().canEdit(getViewContext().getUser(), getViewContext().getContainer()))
                        errors.rejectValue("viewName", ERROR_MSG, "You are not allowed to edit this view.");

                    if (!StringUtils.equals(_report.getDescriptor().getReportName(), form.getViewName()))
                    {
                        if (reportNameExists(getViewContext(), form.getViewName(), _report.getDescriptor().getReportKey()))
                            errors.rejectValue("viewName", ERROR_MSG, "There is already a view with the name of: " + form.getViewName() +
                                    ". Please specify a different name.");
                    }
                }
                else
                    errors.rejectValue("viewName", ERROR_MSG, "An error occurred saving the view.");
            }
            catch (Exception e)
            {
                errors.rejectValue("viewName", ERROR_MSG, "An error occurred saving the view.");
            }
        }

        public ApiResponse execute(EditViewsForm form, BindException errors) throws Exception
        {
            if (_view != null)
            {
                if (!StringUtils.equals(_view.getName(), form.getViewName()))
                {
                    _view.setName(form.getViewName());
                    _view.save(getUser(), getViewContext().getRequest());
                }
            }
            else if (_report != null)
            {
                boolean doSave = false;

                if (!StringUtils.equals(_report.getDescriptor().getReportName(), form.getViewName()))
                {
                    _report.getDescriptor().setReportName(form.getViewName());
                    doSave = true;
                }

                if (!StringUtils.equals(_report.getDescriptor().getReportDescription(), form.getDescription()))
                {
                    _report.getDescriptor().setReportDescription(StringUtils.trimToNull(form.getDescription()));
                    doSave = true;
                }

                if (doSave)
                    ReportService.get().saveReport(getViewContext(), _report.getDescriptor().getReportKey(), _report);
            }
            
            return new ApiSimpleResponse("success", true);
        }
    }

    static class EditViewsForm
    {
        ReportIdentifier _reportId;
        String _viewId;
        String _viewName;
        String _description;

        public ReportIdentifier getReportId()
        {
            return _reportId;
        }

        public void setReportId(ReportIdentifier reportId)
        {
            _reportId = reportId;
        }

        public String getViewId()
        {
            return _viewId;
        }

        public void setViewId(String viewId)
        {
            _viewId = viewId;
        }

        public String getViewName()
        {
            return _viewName;
        }

        public void setViewName(String viewName)
        {
            _viewName = viewName;
        }

        public String getDescription()
        {
            return _description;
        }

        public void setDescription(String description)
        {
            _description = description;
        }
    }

    static class DeleteViewsForm
    {
        ReportIdentifier[] _reportId = new ReportIdentifier[0];
        String[] _viewId = new String[0];

        public ReportIdentifier[] getReportId()
        {
            return _reportId;
        }

        public void setReportId(ReportIdentifier[] reportId)
        {
            _reportId = reportId;
        }

        public String[] getViewId()
        {
            return _viewId;
        }

        public void setViewId(String[] viewId)
        {
            _viewId = viewId;
        }

        public List<QueryForm> getQueryForms(ViewContext context)
        {
            List<QueryForm> forms = new ArrayList<QueryForm>();

            for (String viewId : _viewId)
            {
                QueryForm form = getQueryForm(context, viewId);
                forms.add(form);
            }
            return forms;
        }
    }

    private static QueryForm getQueryForm(ViewContext context, String viewId)
    {
        Map<String, String> map = PageFlowUtil.mapFromQueryString(viewId);
        QueryForm form = new QueryForm();

        form.setSchemaName(new IdentifierString(map.get(QueryParam.schemaName.name())));
        form.setQueryName(map.get(QueryParam.queryName.name()));
        form.setViewName(map.get(QueryParam.viewName.name()));
        form.setViewContext(context);

        return form;
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class RenameReportAction extends FormViewAction<ReportDesignBean>
    {
        private String _newReportName;
        private Report _report;

        public void validateCommand(ReportDesignBean form, Errors errors)
        {
            ReportIdentifier reportId =  form.getReportId();
            _newReportName =  form.getReportName();

            if (!StringUtils.isEmpty(_newReportName))
            {
                try
                {
                    if (null != reportId)
                        _report = reportId.getReport();

                    if (_report != null)
                    {
                        if (!_report.getDescriptor().canEdit(getViewContext().getUser(), getViewContext().getContainer()))
                        {
                            errors.reject("renameReportAction", "Unauthorized operation");
                            return;
                        }

                        if (reportNameExists(getViewContext(), _newReportName, _report.getDescriptor().getReportKey()))
                            errors.reject("renameReportAction", "There is already a view with the name of: " + _newReportName +
                                    ". Please specify a different name.");
                    }
                    else
                        errors.reject("renameReportAction", "Unable to find the specified report");
                }
                catch (Exception e)
                {
                    errors.reject("renameReportAction", "An error occurred trying to rename the specified report");
                }
            }
            else
                errors.reject("renameReportAction", "The view name cannot be blank");
        }

        public ModelAndView getView(ReportDesignBean form, boolean reshow, BindException errors) throws Exception
        {
            ManageViewsAction action = new ManageViewsAction();
            action.setViewContext(getViewContext());
            action.setPageConfig(getPageConfig());
            return action.getView(null, errors);
        }

        public boolean handlePost(ReportDesignBean form, BindException errors) throws Exception
        {
            _report.getDescriptor().setReportName(_newReportName);
            ReportService.get().saveReport(getViewContext(), _report.getDescriptor().getReportKey(), _report);

            return true;
        }

        public ActionURL getSuccessURL(ReportDesignBean form)
        {
            return new ActionURL(ManageViewsAction.class, getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    private boolean reportNameExists(ViewContext context, String reportName, String key)
    {
        try
        {
            for (Report report : ReportService.get().getReports(context.getUser(), context.getContainer(), key))
            {
                if (StringUtils.equals(reportName, report.getDescriptor().getReportName()))
                    return true;
            }
            return false;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ReportDescriptionAction extends FormViewAction<ReportDesignBean>
    {
        private Report _report;

        public void validateCommand(ReportDesignBean form, Errors errors)
        {
            ReportIdentifier reportId =  form.getReportId();
            try
            {
                if (null != reportId)
                    _report = reportId.getReport();

                if (_report != null)
                {
                    if (!_report.getDescriptor().canEdit(getViewContext().getUser(), getViewContext().getContainer()))
                        errors.reject("reportDescription", "Unauthorized operation");
                }
            }
            catch (Exception e)
            {
                errors.reject("reportDescription", "An error occurred trying to change the report description");
            }
        }

        public ModelAndView getView(ReportDesignBean renameReportForm, boolean reshow, BindException errors) throws Exception
        {
            ManageViewsAction action = new ManageViewsAction();
            action.setViewContext(getViewContext());
            action.setPageConfig(getPageConfig());
            return action.getView(null, errors);
        }

        public boolean handlePost(ReportDesignBean form, BindException errors) throws Exception
        {
            String reportDescription =  form.getReportDescription();

            if (_report != null)
            {
                _report.getDescriptor().setReportDescription(StringUtils.trimToNull(reportDescription));
                ReportService.get().saveReport(getViewContext(), _report.getDescriptor().getReportKey(), _report);
                return true;
            }
            else
            {
                errors.reject("reportDescription", "Unable to change the description for the specified report");
                return false;
            }
        }

        public ActionURL getSuccessURL(ReportDesignBean renameReportForm)
        {
            return new ActionURL(ManageViewsAction.class, getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ReportSectionsAction extends ApiAction
    {
        public ApiResponse execute(Object o, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            ReportIdentifier reportId = ReportService.get().getReportIdentifier((String)getViewContext().get(ReportDescriptor.Prop.reportId.name()));
            String sections = (String)getViewContext().get(Report.renderParam.showSection.name());
            if (reportId != null)
            {
                Report report = reportId.getReport();

                // may need a better way to determine sections, do we want to add to the interface?
                response.put("success", true);

                if (report instanceof RReport)
                {
                    List<String> sectionNames = Collections.emptyList();

                    if (sections != null)
                    {
                        sections = PageFlowUtil.decode(sections);
                        sectionNames = Arrays.asList(sections.split("&"));
                    }

                    String script = report.getDescriptor().getProperty(ScriptReportDescriptor.Prop.script);
                    StringBuilder sb = new StringBuilder();

                    for (ParamReplacement param : ParamReplacementSvc.get().getParamReplacements(script))
                    {
                        sb.append("<option value=\"");
                        sb.append(PageFlowUtil.filter(param.getName()));

                        if (sectionNames.contains(param.getName()))
                            sb.append("\" selected>");
                        else
                            sb.append("\">");

                        sb.append(PageFlowUtil.filter(param.toString()));
                        sb.append("</option>");
                    }

                    if (sb.length() > 0)
                        response.put("sectionNames", sb.toString());
                }
            }
            return response;
        }
    }

    protected static class PlotView extends WebPartView
    {
        private Report _report;
        public PlotView(Report report)
        {
            setFrame(FrameType.NONE);
            _report = report;
        }

        @Override
        protected void renderView(Object model, PrintWriter out) throws Exception
        {
            if (_report instanceof ChartReport)
            {
                ActionURL url = getViewContext().cloneActionURL();
                url.setAction("plotChart");
                url.addParameter("reportId", _report.getDescriptor().getReportId().toString());

                out.write("<img src='" + url + "'>");
            }
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class CrosstabExportAction extends SimpleViewAction<ReportDesignBean>
    {
        public ModelAndView getView(ReportDesignBean form, BindException errors) throws Exception
        {
            ReportIdentifier reportId = form.getReportId();
            if (reportId != null)
            {
                Report report = reportId.getReport();
                if (report instanceof CrosstabReport)
                {
                    ExcelWriter writer = ((CrosstabReport)report).getExcelWriter(getViewContext());
                    writer.write(getViewContext().getResponse());
                }
            }
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class ThumbnailAction extends BaseThumbnailAction<ThumbnailForm>
    {
        @Override
        public StaticThumbnailProvider getProvider(ThumbnailForm form) throws Exception
        {
            Report report = form.getReportId().getReport();

            if (null != report && report.getDescriptor().canRead(getUser()))
                return report;
            else
                return null;
        }
    }


    public static class ThumbnailForm
    {
        private ReportIdentifier _reportId;

        public ReportIdentifier getReportId()
        {
            return _reportId;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setReportId(ReportIdentifier reportId)
        {
            _reportId = reportId;
        }
    }
}
