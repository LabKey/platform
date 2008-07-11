/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

import org.apache.beehive.netui.pageflow.FormData;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.labkey.api.action.*;
import org.labkey.api.attachments.AttachmentForm;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.*;
import org.labkey.api.reports.report.r.ParamReplacementSvc;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.reports.report.view.*;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;
import org.labkey.common.util.Pair;
import org.labkey.query.reports.chart.ChartServiceImpl;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletException;
import java.io.File;
import java.io.PrintWriter;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Apr 19, 2007
 */

public class ReportsController extends SpringActionController
{
    static DefaultActionResolver _actionResolver = new DefaultActionResolver(ReportsController.class);

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

        public ActionURL urlSaveRReportState(Container c)
        {
            return new ActionURL(SaveRReportStateAction.class, c);
        }

        public ActionURL urlUpdateRReportState(Container c)
        {
            return new ActionURL(UpdateRReportStateAction.class, c);
        }

        public ActionURL urlDesignChart(Container c)
        {
            return new ActionURL(DesignChartAction.class, c);
        }

        public ActionURL urlCreateRReport(Container c)
        {
            return new ActionURL(CreateRReportAction.class, c);
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
    }

    public ReportsController() throws Exception
    {
        super();
        setActionResolver(_actionResolver);
    }

    @RequiresPermission(ACL.PERM_READ)
    public class DesignChartAction extends SimpleViewAction<ChartDesignerBean>
    {
        public ModelAndView getView(ChartDesignerBean form, BindException errors) throws Exception
        {
            Map props = new HashMap();
            for (Pair<String, String> param : form.getParameters())
            {
                props.put(param.getKey(), param.getValue());
            }
            props.put("isAdmin", String.valueOf(getContainer().hasPermission(getUser(), ACL.PERM_ADMIN)));
            props.put("isGuest", String.valueOf(getUser().isGuest()));
            HttpView view = new GWTView("org.labkey.reports.designer.ChartDesigner", props);
            return new VBox(view);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Create Chart View");
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class ChartServiceAction extends GWTServiceAction
    {
        protected BaseRemoteService createService()
        {
            return new ChartServiceImpl(getViewContext());
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class PlotChartAction extends SimpleViewAction<ChartDesignerBean>
    {
        public ModelAndView getView(ChartDesignerBean form, BindException errors) throws Exception
        {
            final ViewContext context = getViewContext();
            Report report = null;

            if (form.getReportId() != -1)
                report = ReportService.get().getReport(form.getReportId());

            if (report == null)
            {
                List<String> reportIds = context.getList("reportId");
                if (reportIds != null && !reportIds.isEmpty())
                    report = ReportService.get().getReport(NumberUtils.toInt(reportIds.get(0)));
            }

            if (report == null)
                report = ReportService.get().createFromQueryString(context.getActionURL().getQueryString());

            if (report != null)
                return report.renderReport(context);
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresPermission(ACL.PERM_READ)
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
                if (report.getDescriptor().getReportId() != -1)
                    url = ChartUtil.getPlotChartURL(getViewContext(), form.getReport());
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
            if (form.getReportId() != -1)
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

    @RequiresPermission(ACL.PERM_READ)
    public class DeleteReportAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            String reportId = getViewContext().getRequest().getParameter(ChartUtil.REPORT_ID);
            String forwardUrl = getViewContext().getRequest().getParameter(ChartUtil.FORWARD_URL);
            Report report = null;

            if (reportId != null)
                report = ReportService.get().getReport(NumberUtils.toInt(reportId));

            if (report != null)
            {
                if (!report.getDescriptor().canEdit(getViewContext()))
                    return HttpView.throwUnauthorizedMV();
                ReportService.get().deleteReport(getViewContext(), report);
            }
            return HttpView.redirect(forwardUrl);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class ConfigureRReportAction extends FormViewAction<ConfigureRForm>
    {
        public ModelAndView getView(ConfigureRForm form, boolean reshow, BindException errors) throws Exception
        {
            form.setErrors(errors);
            JspView<ConfigureRForm> view = new JspView<ConfigureRForm>("/org/labkey/query/reports/view/rReportConfiguration.jsp", form);
            VBox v = new VBox();
            if (!errors.hasErrors() && reshow)
                v.addView(new HtmlView("The R View configuration has been updated."));
            v.addView(view);

            return v;
        }

        public void validateCommand(ConfigureRForm form, Errors errors)
        {
            // will allow a blank path to allow removal of R Reporting
            if (StringUtils.isEmpty(form.getProgramPath()))
                return;

            // validate the parameters
            String error = RReport.validateConfiguration(form.getProgramPath(), form.getCommand(), form.getTempFolder(), form.getScriptHandler());
            if (error != null)
                errors.reject("RConfigurationError", error);
        }

        public ActionURL getSuccessURL(ConfigureRForm rChartBean)
        {
            return null;
        }

        public boolean handlePost(ConfigureRForm form, BindException errors) throws Exception
        {
            RReport.setRExe(form.getProgramPath());
            RReport.setRCmd(form.getCommand());
            RReport.setTempFolder(form.getTempFolder());
            RReport.setEditPermissions(form.getPermissions());
            RReport.setRScriptHandler(form.getScriptHandler());

            if (DefaultScriptRunner.ID.equals(form.getScriptHandler()))
                RServeScriptRunner.stopRServer();
            else if (RServeScriptRunner.ID.equals(form.getScriptHandler()))
                RServeScriptRunner.ensureServer();

            return true;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Admin Console", new ActionURL("admin", "showAdmin", (String)null));
            return root.addChild("R View Configuration");
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class SaveRReportStateAction extends FormViewAction<RReportBean>
    {
        public ModelAndView getView(RReportBean rReportBean, boolean reshow, BindException errors) throws Exception
        {
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;  
        }

        public void validateCommand(RReportBean target, Errors errors)
        {
        }

        public boolean handlePost(RReportBean form, BindException errors) throws Exception
        {
            form.setIsDirty(true);
            RunRReportView.updateReportCache(form, true);
            return true;
        }

        public ActionURL getSuccessURL(RReportBean rReportBean)
        {
            return null;
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class UpdateRReportStateAction extends SaveRReportStateAction
    {
        public boolean handlePost(RReportBean form, BindException errors) throws Exception
        {
            //form.setIsDirty(true);
            RunRReportView.updateReportCache(form, false);
            return true;
        }
    }

    protected static class CreateRReportView extends RunRReportView
    {
        public CreateRReportView(Report report)
        {
            super(report);
        }

        protected List<TabInfo> getTabList()
        {
            ActionURL url = getViewContext().cloneActionURL().
                    replaceParameter(CACHE_PARAM, String.valueOf(_reportId));

            List<TabInfo> tabs = new ArrayList<TabInfo>();

            String currentTab = url.getParameter(TAB_PARAM);
            boolean saveChanges = currentTab == null || TAB_SOURCE.equals(currentTab);

            tabs.add(new RTabInfo(TAB_SOURCE, TAB_SOURCE, url, saveChanges));
            tabs.add(new RTabInfo(TAB_SYNTAX, TAB_SYNTAX, url, saveChanges));

            return tabs;
        }

        protected ActionURL getRenderAction() throws Exception
        {
            ActionURL runURL = getReport().getRunReportURL(getViewContext());
            ActionURL url = new ActionURL(RenderRReportAction.class, getViewContext().getContainer());
            if (runURL != null)
                url.addParameters(runURL.getParameters());
            return url;
        }
    }

    protected static class RenderRReportView extends RunRReportView
    {
        public RenderRReportView(Report report)
        {
            super(report);
            if (_report == null)
            {
                _report = initFromCache();
                if (_report != null)
                    _reportId = _report.getDescriptor().getReportId();
            }
        }

        private Report initFromCache()
        {
            try {
                RReportBean form = new RReportBean();
                form.reset(null, getViewContext().getRequest());
                form.setErrors(new BindException(form, "form"));
                initReportCache(form);

                return form.getReport();
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }

        protected List<TabInfo> getTabList()
        {
            ActionURL url = getViewContext().cloneActionURL().
                    replaceParameter(CACHE_PARAM, String.valueOf(_reportId));

            List<TabInfo> tabs = new ArrayList<TabInfo>();
            boolean saveChanges = TAB_SOURCE.equals(url.getParameter(TAB_PARAM));

            tabs.add(new RTabInfo(TAB_VIEW, TAB_VIEW, url, saveChanges));
            tabs.add(new RTabInfo(TAB_SOURCE, TAB_SOURCE, url, saveChanges));
            tabs.add(new RTabInfo(TAB_SYNTAX, TAB_SYNTAX, url, saveChanges));

            return tabs;
        }
    }

    @RequiresPermission(ACL.PERM_NONE)
    public class CreateRReportAction extends FormViewAction<RReportBean>
    {
        public void validateCommand(RReportBean target, Errors errors)
        {
        }

        public ModelAndView getView(RReportBean form, boolean reshow, BindException errors) throws Exception
        {
            validatePermissions();
            CreateRReportView view = new CreateRReportView(form.getReport());
            view.setErrors(errors);

            return view;
        }

        public boolean handlePost(RReportBean rReportBean, BindException errors) throws Exception
        {
            return true;
        }

        public ActionURL getSuccessURL(RReportBean rReportBean)
        {
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("R View Builder");
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class RunReportAction extends SimpleViewAction<ReportDesignBean>
    {
        Report _report;
        public ModelAndView getView(ReportDesignBean form, BindException errors) throws Exception
        {
            _report = ReportService.get().getReport(form.getReportId());
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

    @RequiresPermission(ACL.PERM_READ)
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
                    addRow(out, "Created By", PageFlowUtil.filter(user.getDisplayName(getViewContext())));

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

    @RequiresPermission(ACL.PERM_READ)
    public class RenderRReportAction extends CreateRReportAction
    {
        public ModelAndView getView(RReportBean form, boolean reshow, BindException errors) throws Exception
        {
            RenderRReportView view = new RenderRReportView(form.getReport());
            view.setErrors(errors);

            return view;
        }
    }

    protected void validatePermissions() throws Exception
    {
        int perms = RReport.getEditPermissions();
        if (!getViewContext().hasPermission(perms))
            HttpView.throwUnauthorized();
    }

    @RequiresPermission(ACL.PERM_NONE)
    public class SaveRReportAction extends CreateRReportAction
    {
        private Report _report;

        public void validateCommand(RReportBean form, Errors errors)
        {
            try {
                if (getViewContext().getUser().isGuest())
                {
                    errors.reject("saveRReport", "you must be logged in to be able to save reports");
                    return;
                }
                _report = form.getReport();
                // on new reports, check for duplicates
                if (_report.getDescriptor().getReportId() == -1)
                {
                    if (reportNameExists(_report.getDescriptor().getReportName(), ChartUtil.getReportQueryKey(_report.getDescriptor())))
                    {
                        errors.reject("saveRReport", "There is already a report with the name of: '" + _report.getDescriptor().getReportName() +
                                "'. Please specify a different name.");
                        form.setReportName(null);
                    }
                }
            }
            catch (Exception e)
            {
                errors.reject("saveRReport", e.getMessage());
            }
        }

        public boolean handlePost(RReportBean form, BindException errors) throws Exception
        {
            if (_report != null)
            {
                validatePermissions();
                ReportService.get().saveReport(getViewContext(), ChartUtil.getReportQueryKey(_report.getDescriptor()), _report);
            }
            return true;
        }

        public ActionURL getSuccessURL(RReportBean form)
        {
            if (form.getRedirectUrl() != null)
                return new ActionURL(form.getRedirectUrl()).addParameter(RunReportView.MSG_PARAM, "Report: " + form.getReportName() + " successfully saved");
            return null;
        }

        private boolean reportNameExists(String reportName, String key)
        {
            try {
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

/*
    @RequiresPermission(ACL.PERM_READ)
    public class RunBackgroundRReportAction extends SimpleViewAction<RReportBean>
    {
        public ModelAndView getView(RReportBean form, BindException errors) throws Exception
        {
            final ViewContext context = getViewContext();
            Report report = null;

            if (form.getReportId() != 0)
                report = ReportService.get().getReport(form.getReportId());

            if (report instanceof RReport)
            {
                final Container c = getContainer();
                final ViewBackgroundInfo info = new ViewBackgroundInfo(c,
                        context.getUser(), context.getActionURL());
                ((RReport)report).createInputDataFile(getViewContext());
                PipelineJob job = new RReportJob(ReportsPipelineProvider.NAME, info, form.getReportId());
                PipelineService.get().getPipelineQueue().addJob(job);

                return HttpView.redirect(PageFlowUtil.urlProvider(PipelineStatusUrls.class).urlBegin(c));
            }
            else
                throw new IllegalArgumentException("The view must be an instance of RReport to be run as a background task");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

*/
    /**
     * Ajax action to start a pipeline-based R view.
     */
    @RequiresPermission(ACL.PERM_READ)
    public class StartBackgroundRReportAction extends ApiAction<RReportBean>
    {
        public ApiResponse execute(RReportBean form, BindException errors) throws Exception
        {
            final ViewContext context = getViewContext();
            final Container c = getContainer();
            final ViewBackgroundInfo info = new ViewBackgroundInfo(c,
                    context.getUser(), context.getActionURL());
            ApiSimpleResponse response = new ApiSimpleResponse();

            Report report;
            PipelineJob job;
            if (form.getReportId() == -1)
            {
                // report not saved yet, get state from the cache
                String key = getViewContext().getActionURL().getParameter(RunRReportView.CACHE_PARAM);
                if (key != null)
                    RunRReportView.initFormFromCache(form, key, context);
                report = form.getReport();
                job = new RReportJob(ReportsPipelineProvider.NAME, info, form);
            }
            else
            {
                report = ReportService.get().getReport(form.getReportId());
                job = new RReportJob(ReportsPipelineProvider.NAME, info, form.getReportId());
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

/*
    @RequiresPermission(ACL.PERM_READ)
    public class RenderBackgroundRReportAction extends SimpleViewAction
    {
        private PipelineStatusFile _sf;
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            String path = (String)getViewContext().get("path");
            String reportId = (String)getViewContext().get("reportId");
            Report report = ReportService.get().getReport(NumberUtils.toInt(reportId));

            if (report instanceof RReport)
            {
                if (!StringUtils.isEmpty(path))
                {
                    _sf = PipelineService.get().getStatusFile(path);
                    if (_sf != null)
                    {
                        File filePath = new File(_sf.getFilePath());
                        File substitutionMap = new File(filePath.getParentFile(), RReport.SUBSTITUTION_MAP);

                        if (substitutionMap != null && substitutionMap.exists())
                        {
                            BufferedReader br = null;
                            List<Pair<String, String>> outputSubst = new ArrayList();

                            try {
                                br = new BufferedReader(new FileReader(substitutionMap));
                                String l;
                                while ((l = br.readLine()) != null)
                                {
                                    String[] parts = l.split("\\t");
                                    if (parts.length == 2)
                                    {
                                        outputSubst.add(new Pair(parts[0], parts[1]));
                                    }
                                }
                            }
                            finally
                            {
                                if (br != null)
                                    try {br.close();} catch(IOException ioe) {}
                            }

                            VBox view = new VBox();
                            RReport.renderViews((RReport)report, view, outputSubst, false);
                            return view;
                        }
                    }
                }
            }
            HttpView.throwNotFound("Unable to find the specified view");
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            if (_sf != null)
                return root.addChild(_sf.getTypeDescription());
            else
                return null;
        }
    }
*/

    @RequiresPermission(ACL.PERM_NONE)
    public class DownloadInputDataAction extends SimpleViewAction<RReportBean>
    {
        public ModelAndView getView(RReportBean form, BindException errors) throws Exception
        {
            validatePermissions();
            Report report = form.getReport();
            if (report instanceof RReport)
            {
                File file = DefaultScriptRunner.createInputDataFile((RReport)report, getViewContext());
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

    @RequiresPermission(ACL.PERM_READ)
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
            ChartUtil.renderErrorImage(getViewContext().getResponse().getOutputStream(), 450, 100, "Resource not found");
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class DownloadAction extends SimpleViewAction<AttachmentForm>
    {
        public ModelAndView getView(AttachmentForm form, BindException errors) throws Exception
        {
            SimpleFilter filter = new SimpleFilter("ContainerId", getContainer().getId());
            filter.addCondition("EntityId", form.getEntityId());

            Report[] report = ReportService.get().getReports(filter);
            if (report.length == 0)
            {
                HttpView.throwNotFound("Unable to find report");
                return null;
            }

            //if (!report.getDescriptor().getACL().hasPermission(getUser(), ACL.PERM_READ))
            //    HttpView.throwUnauthorized();

            if (report[0] instanceof RReport)
                AttachmentService.get().download(getViewContext().getResponse(), (RReport)report[0], form.getName());
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class RExpandStateNotifyAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class ManageViewsAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            ManageReportsBean bean = new ManageReportsBean(getViewContext());
            bean.setErrors(errors);

            return new JspView<ManageReportsBean>("/org/labkey/query/reports/view/manageViews.jsp", bean, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Manage Views");
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class RenameReportAction extends FormViewAction<ReportDesignBean>
    {
        private String _newReportName;
        private Report _report;

        public void validateCommand(ReportDesignBean form, Errors errors)
        {
            int reportId =  form.getReportId();
            _newReportName =  form.getReportName();
            if (!StringUtils.isEmpty(_newReportName))
            {
                try {
                    _report = ReportService.get().getReport(reportId);
                    if (_report != null)
                    {
                        if (!_report.getDescriptor().canEdit(getViewContext()))
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
        try {
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

    @RequiresPermission(ACL.PERM_READ)
    public class ReportDescriptionAction extends FormViewAction<ReportDesignBean>
    {
        Report _report;
        public void validateCommand(ReportDesignBean form, Errors errors)
        {
            int reportId =  form.getReportId();
            try {
                _report = ReportService.get().getReport(reportId);
                if (_report != null)
                {
                    if (!_report.getDescriptor().canEdit(getViewContext()))
                        errors.reject("reportDescription", "Unauthorized operation");
                }
            }
            catch (Exception e)
            {
                errors.reject("reportDescription", "An error occured trying to change the report description");
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

    @RequiresPermission(ACL.PERM_READ)
    public class ReportSectionsAction extends ApiAction
    {
        public ApiResponse execute(Object o, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            int reportId = NumberUtils.toInt((String)getViewContext().get(ReportDescriptor.Prop.reportId.name()), -1);
            String sections = (String)getViewContext().get(Report.renderParam.showSection.name());
            if (reportId != -1)
            {
                Report report = ReportService.get().getReport(reportId);

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
                    String script = report.getDescriptor().getProperty(RReportDescriptor.Prop.script);
                    StringBuffer sb = new StringBuffer();
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

    public static class ConfigureRForm extends FormData
    {
        private String _programPath = RReport.getRExe();
        private String _command = RReport.getRCmd();
        private String _tempFolder = RReport.getTempFolder();
        private int _perms = RReport.getEditPermissions();
        private BindException _errors;
        private String _scriptHandler = RReport.getRScriptHandler();

        public void setProgramPath(String path){_programPath = path;}
        public String getProgramPath(){return _programPath;}
        public void setCommand(String command){_command = command;}
        public String getCommand(){return _command;}
        public void setTempFolder(String folder){_tempFolder = folder;}
        public String getTempFolder(){return _tempFolder;}
        public void setErrors(BindException errors){_errors = errors;}
        public BindException getErrors(){return _errors;}
        public void setPermissions(int perms){_perms = perms;}
        public int getPermissions(){return _perms;}
        public void setScriptHandler(String scriptHandler){_scriptHandler = scriptHandler;}
        public String getScriptHandler(){return _scriptHandler;}
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
                url.addParameter("reportId", String.valueOf(_report.getDescriptor().getReportId()));

                out.write("<img border=0 src='" + url + "'>");
            }
        }
    }
}
