/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.labkey.api.action.*;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.attachments.AttachmentForm;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ExcelWriter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.*;
import org.labkey.api.reports.*;
import org.labkey.api.reports.report.*;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.reports.report.r.ParamReplacementSvc;
import org.labkey.api.reports.report.view.*;
import org.labkey.api.security.*;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.settings.AdminConsole.SettingsLinkType;
import org.labkey.api.study.reports.CrosstabReport;
import org.labkey.api.util.IdentifierString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.*;
import org.labkey.query.ViewFilterItemImpl;
import org.labkey.query.reports.chart.ChartServiceImpl;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
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

    @RequiresPermission(ACL.PERM_READ)
    public class DesignChartAction extends SimpleViewAction<ChartDesignerBean>
    {
        public ModelAndView getView(ChartDesignerBean form, BindException errors) throws Exception
        {
            Map<String, String> props = new HashMap<String, String>();
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

            if (null != form.getReportId())
                report = form.getReportId().getReport();

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

    @RequiresPermission(ACL.PERM_READ)
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
                if (!report.getDescriptor().canEdit(getViewContext()))
                    return HttpView.throwUnauthorized();
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

    @RequiresPermission(ACL.PERM_ADMIN)
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

    @RequiresPermission(ACL.PERM_ADMIN)
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

    @RequiresPermission(ACL.PERM_ADMIN)
    public class ScriptEnginesDeleteAction extends ApiAction<LabkeyScriptEngineManager.EngineDefinition>
    {
        public ApiResponse execute(LabkeyScriptEngineManager.EngineDefinition def, BindException errors) throws Exception
        {
            LabkeyScriptEngineManager.deleteDefinition(def);
            return new ApiSimpleResponse("success", true);
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
            Report report = form.getReport();
            if (report instanceof RReport)
                ((RReport)report).clearCache();
            RunRReportView.updateReportCache(form, false);
            return true;
        }
    }

/*
    protected static class CreateRReportView extends RunRReportView
    {
        public CreateRReportView(Report report)
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
            tabs.add(new ScriptTabInfo(TAB_SYNTAX, TAB_SYNTAX, url, saveChanges));

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
            ActionURL runURL = getReport().getRunReportURL(getViewContext());
            ActionURL url = new ActionURL(RenderRReportAction.class, getViewContext().getContainer());

            // apply parameters already on the URL excluding those in to report bean (they will be applied on the post)
            for (Pair<String, String> param : getViewContext().getActionURL().getParameters())
            {
                if (!_formParams.containsKey(param.getKey()))
                    url.replaceParameter(param.getKey(), param.getValue());
            }

            if (runURL != null)
            {
                for (Pair<String, String> param : runURL.getParameters())
                    url.replaceParameter(param.getKey(), param.getValue());
            }
            return url;
        }
    }
*/

/*
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

        public List<NavTree> getTabList()
        {
            ActionURL url = getViewContext().cloneActionURL().
                    replaceParameter(CACHE_PARAM, String.valueOf(_reportId));

            List<NavTree> tabs = new ArrayList<NavTree>();
            boolean saveChanges = TAB_SOURCE.equals(url.getParameter(TAB_PARAM));

            tabs.add(new ScriptTabInfo(TAB_VIEW, TAB_VIEW, url, saveChanges));
            tabs.add(new ScriptTabInfo(TAB_SOURCE, TAB_SOURCE, url, saveChanges));
            tabs.add(new ScriptTabInfo(TAB_SYNTAX, TAB_SYNTAX, url, saveChanges));

            return tabs;
        }
    }

*/
/*
    */
/**
     * The view used to render external reports in design mode.
     */
/*
    protected static class RenderExternalReportView extends RunScriptReportView
    {
        public RenderExternalReportView(Report report)
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

        public List<NavTree> getTabList()
        {
            ActionURL url = getViewContext().cloneActionURL().
                    replaceParameter(CACHE_PARAM, String.valueOf(_reportId));

            List<NavTree> tabs = new ArrayList<NavTree>();
            boolean saveChanges = TAB_SOURCE.equals(url.getParameter(TAB_PARAM));

            tabs.add(new ScriptTabInfo(TAB_VIEW, TAB_VIEW, url, saveChanges));
            tabs.add(new ScriptTabInfo(TAB_SOURCE, TAB_SOURCE, url, saveChanges));

            return tabs;
        }
    }
*/

    @RequiresPermission(ACL.PERM_NONE)
    public class CreateRReportAction extends FormViewAction<RReportBean>
    {
        public void validateCommand(RReportBean target, Errors errors)
        {
        }

        public ModelAndView getView(RReportBean form, boolean reshow, BindException errors) throws Exception
        {
            validatePermissions();
            RunRReportView view = new RunRReportView(form.getReport());
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

    @RequiresPermission(ACL.PERM_NONE)
    public class CreateScriptReportAction extends FormViewAction<ScriptReportBean>
    {
        public void validateCommand(ScriptReportBean target, Errors errors)
        {
        }

        public ModelAndView getView(ScriptReportBean form, boolean reshow, BindException errors) throws Exception
        {
            RunScriptReportView view = new RunScriptReportView(form.getReport());
            view.setErrors(errors);

            return view;
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
            return root.addChild("Script View Builder");
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class RunReportAction extends SimpleViewAction<ReportDesignBean>
    {
        Report _report;
        public ModelAndView getView(ReportDesignBean form, BindException errors) throws Exception
        {
            _report = null;
            if(null != form.getReportId())
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

/*
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
*/

/*
    @RequiresPermission(ACL.PERM_READ)
    public class RenderScriptReportAction extends CreateScriptReportAction
    {
        public ModelAndView getView(ScriptReportBean form, boolean reshow, BindException errors) throws Exception
        {
            RenderExternalReportView view = new RenderExternalReportView(form.getReport());
            view.setErrors(errors);

            return view;
        }
    }

*/
    protected void validatePermissions() throws Exception
    {
        if (!RReport.canCreateScript(getViewContext()))
            HttpView.throwUnauthorized("Only members of the Site Admin and Site Developers groups are allowed to create and edit R views.");
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
                if (null == _report.getDescriptor().getReportId())
                {
                    if (reportNameExists(_report.getDescriptor().getReportName(), ReportUtil.getReportQueryKey(_report.getDescriptor())))
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
                ReportService.get().saveReport(getViewContext(), ReportUtil.getReportQueryKey(_report.getDescriptor()), _report);
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
            if (null == form.getReportId())
            {
                // report not saved yet, get state from the cache
                String key = getViewContext().getActionURL().getParameter(RunRReportView.CACHE_PARAM);
                if (key != null && RunRReportView.isCacheValid(key, context))
                    RunRReportView.initFormFromCache(form, key, context);
                report = form.getReport();
                job = new RReportJob(ReportsPipelineProvider.NAME, info, form);
            }
            else
            {
                report = form.getReportId().getReport();
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
            return new HtmlView("Requested Resource not found");
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
            QueryDefinition def = QueryService.get().getQueryDef(getContainer(), form.getSchemaName(), form.getQueryName());
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

                for (ReportService.DesignerInfo info : getAvailableReportDesigners(form))
                {
                    Map<String, String> record = new HashMap<String, String>();

                    record.put("reportType", info.getReportType());
                    record.put("reportLabel", info.getLabel());
                    record.put("reportDescription", info.getDescription());

                    if (filterItemMap.containsKey(info.getReportType()))
                        record.put("enabled", String.valueOf(filterItemMap.get(info.getReportType()).isEnabled()));
                    else
                        record.put("enabled", String.valueOf(baseItemMap.containsKey(info.getReportType())));

                    response.add(record);
                }
            }
            return new ApiSimpleResponse("viewOptions", response);
        }
    }

    private Collection<ReportService.DesignerInfo> getAvailableReportDesigners(ViewOptionsForm form)
    {
        Map<String, ReportService.DesignerInfo> designerMap = new HashMap<String, ReportService.DesignerInfo>();
        Map<String, String> baseItemMap = new HashMap<String, String>();

        if (!StringUtils.isBlank(form.getBaseFilterItems()))
        {
            String baseFilterItems = PageFlowUtil.decode(form.getBaseFilterItems());
            for (String item : baseFilterItems.split("&"))
                baseItemMap.put(item, item);
        }

        QuerySettings settings = new QuerySettings(getViewContext(), null, form.getQueryName());
        settings.setSchemaName(form.getSchemaName());

        // build the list available view types by combining the available types and the built in item filter types
        for (ReportService.UIProvider provider : ReportService.get().getUIProviders())
        {
            for (ReportService.DesignerInfo info : provider.getDesignerInfo(getViewContext(), settings))
            {
                if (!designerMap.containsKey(info.getLabel()) || baseItemMap.containsKey(info.getReportType()))
                    designerMap.put(info.getLabel(), info);
            }
        }
        return designerMap.values();
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class ManageViewsUpdateViewOptionsAction extends ExtFormAction<ViewOptionsForm>
    {
        public ApiResponse execute(ViewOptionsForm form, BindException errors) throws Exception
        {
            QueryDefinition def = QueryService.get().getQueryDef(getContainer(), form.getSchemaName(), form.getQueryName());
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

    @RequiresPermission(ACL.PERM_READ)
    public class ManageViewsSummaryAction extends ApiAction<ViewsSummaryForm>
    {
        public ApiResponse execute(ViewsSummaryForm form, BindException errors) throws Exception
        {
            return new ApiSimpleResponse("views", ReportUtil.getViews(getViewContext(), form.getSchemaName(), form.getQueryName(), true));
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class ManageViewsDeleteReportsAction extends ApiAction<DeleteViewsForm>
    {
        public ApiResponse execute(DeleteViewsForm form, BindException errors) throws Exception
        {
            for (ReportIdentifier id : form.getReportId())
            {
                Report report = id.getReport();

                if (report != null)
                {
                    if (!report.getDescriptor().canEdit(getViewContext()))
                        HttpView.throwUnauthorized();
                    ReportService.get().deleteReport(getViewContext(), report);
                }
            }

            for (QueryForm qf : form.getQueryForms(getViewContext()))
            {
                CustomView customView = qf.getCustomView();
                if (customView != null)
                {
                    if (customView.getOwner() == null)
                    {
                        if (!getViewContext().getContainer().hasPermission(getUser(), ACL.PERM_ADMIN))
                            HttpView.throwUnauthorized();
                    }
                    customView.delete(getUser(), getViewContext().getRequest());
                }
            }
            return new ApiSimpleResponse("success", true);
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class ManageViewsEditReportsAction extends ExtFormAction<EditViewsForm>
    {
        private Report _report;

        @Override
        public void validateForm(EditViewsForm form, Errors errors)
        {
            try {
                _report = form.getReportId().getReport();

                if (_report != null)
                {
                    if (!_report.getDescriptor().canEdit(getViewContext()))
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
            if (_report != null)
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
                Map<String, String> map = PageFlowUtil.mapFromQueryString(viewId);
                QueryForm form = new QueryForm();

                form.setSchemaName(new IdentifierString(map.get(QueryParam.schemaName.name())));
                form.setQueryName(map.get(QueryParam.queryName.name()));
                form.setViewName(map.get(QueryParam.viewName.name()));
                form.setViewContext(context);

                forms.add(form);
            }
            return forms;
        }
    }

    @RequiresPermission(ACL.PERM_READ)
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
                try {
                    if(null != reportId)
                        _report = reportId.getReport();
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
            ReportIdentifier reportId =  form.getReportId();
            try {
                if(null != reportId)
                    _report = reportId.getReport();
                if (_report != null)
                {
                    if (!_report.getDescriptor().canEdit(getViewContext()))
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

    @RequiresPermission(ACL.PERM_READ)
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
}
