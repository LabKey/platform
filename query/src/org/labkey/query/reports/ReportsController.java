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

package org.labkey.query.reports;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.old.JSONArray;
import org.json.old.JSONException;
import org.json.old.JSONObject;
import org.junit.Test;
import org.labkey.api.action.Action;
import org.labkey.api.action.ActionType;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.CustomApiForm;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.HasViewContext;
import org.labkey.api.action.Marshal;
import org.labkey.api.action.Marshaller;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.notification.NotificationService;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentForm;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.BaseDownloadAction;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.ExcelWriter;
import org.labkey.api.data.views.DataViewInfo;
import org.labkey.api.data.views.DataViewProvider;
import org.labkey.api.data.views.DataViewProvider.EditInfo.ThumbnailType;
import org.labkey.api.data.views.DataViewService;
import org.labkey.api.exceptions.OptimisticConflictException;
import org.labkey.api.message.digest.DailyMessageDigest;
import org.labkey.api.message.digest.ReportAndDatasetChangeDigestProvider;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.premium.PremiumFeatureNotEnabledException;
import org.labkey.api.premium.PremiumService;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reports.report.r.RConnectionHolder;
import org.labkey.api.reports.report.r.RemoteRNotEnabledException;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportContentEmailManager;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.r.RserveScriptEngine;
import org.labkey.api.reports.actions.ReportForm;
import org.labkey.api.reports.model.DataViewEditForm;
import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.reports.model.ViewCategoryManager;
import org.labkey.api.reports.model.ViewInfo;
import org.labkey.api.reports.permissions.ShareReportPermission;
import org.labkey.api.reports.report.AbstractReport;
import org.labkey.api.reports.report.AbstractReportIdentifier;
import org.labkey.api.reports.report.ModuleReportIdentifier;
import org.labkey.api.reports.report.QueryReport;
import org.labkey.api.reports.report.r.RReport;
import org.labkey.api.reports.report.r.RReportJob;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.ReportIdentifier;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.reports.report.ScriptEngineReport;
import org.labkey.api.reports.report.ScriptOutput;
import org.labkey.api.reports.report.ScriptReport;
import org.labkey.api.reports.report.ScriptReportDescriptor;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.reports.report.r.ParamReplacementSvc;
import org.labkey.api.reports.report.view.AjaxScriptReportView;
import org.labkey.api.reports.report.view.AjaxScriptReportView.Mode;
import org.labkey.api.reports.report.view.RenderBackgroundRReportView;
import org.labkey.api.reports.report.view.ReportDesignBean;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.reports.report.view.ScriptReportBean;
import org.labkey.api.reports.report.view.ScriptReportDesignBean;
import org.labkey.api.resource.FileResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.StudyUrls;
import org.labkey.api.study.reports.CrosstabReport;
import org.labkey.api.thumbnail.BaseThumbnailAction;
import org.labkey.api.thumbnail.ThumbnailProvider;
import org.labkey.api.thumbnail.ThumbnailService;
import org.labkey.api.thumbnail.ThumbnailService.ImageType;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;
import org.labkey.api.util.MimeMap;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.SortHelpers;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpRedirectView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.Portal;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewForm;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.query.DataViewsWebPartFactory;
import org.labkey.query.persist.QueryManager;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.labkey.api.reports.model.ViewCategoryManager.UNCATEGORIZED_ROWID;
import static org.labkey.api.util.DOM.DIV;
import static org.labkey.api.util.DOM.SPAN;
import static org.labkey.api.util.DOM.cl;

/**
 * User: Karl Lum
 * Date: Apr 19, 2007
 */
public class ReportsController extends SpringActionController
{
    private static final Logger _log = LogManager.getLogger(ReportsController.class);
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(ReportsController.class);
    private static final MimeMap _mimeMap = new MimeMap();

    public static class ReportUrlsImpl implements ReportUrls
    {
        @Override
        public ActionURL urlDownloadData(Container c)
        {
            return new ActionURL(DownloadInputDataAction.class, c);
        }

        @Override
        public ActionURL urlModuleThumbnail(Container c)
        {
            return new ActionURL(DownloadModuleReportThumbnailAction.class, c);
        }

        @Override
        public ActionURL urlRunReport(Container c)
        {
            return new ActionURL(RunReportAction.class, c);
        }

        @Override
        public ActionURL urlAjaxSaveScriptReport(Container c)
        {
            return new ActionURL(AjaxSaveScriptReportAction.class, c);
        }

        @Override
        public ActionURL urlViewScriptReport(Container c)
        {
            return new ActionURL(ViewScriptReportAction.class, c);
        }

        @Override
        public ActionURL urlDesignScriptReport(Container c)
        {
            return new ActionURL(DesignScriptReportAction.class, c);
        }

        @Override
        public ActionURL urlViewBackgroundScriptReport(Container c)
        {
            return new ActionURL(GetBackgroundReportResultsAction.class, c);
        }

        @Override
        public ActionURL urlCreateScriptReport(Container c)
        {
            return new ActionURL(CreateScriptReportAction.class, c);
        }

        @Override
        public ActionURL urlStreamFile(Container c)
        {
            return new ActionURL(StreamFileAction.class, c);
        }
        
        @Override
        public ActionURL urlReportSections(Container c)
        {
            return new ActionURL(ReportSectionsAction.class, c);
        }

        @Override
        public ActionURL urlManageViews(Container c)
        {
            return new ActionURL(ManageViewsAction.class, c);
        }

        @Override
        public ActionURL urlExportCrosstab(Container c)
        {
            return new ActionURL(CrosstabExportAction.class, c);
        }

        @Override
        public ActionURL urlShareReport(Container c, Report r)
        {
            if (r.getDescriptor().getReportId() == null)
                return null;
            ActionURL url = new ActionURL(ShareReportAction.class, c);
            url.addParameter("reportId", r.getDescriptor().getReportId().toString());
            return url;
        }

        @Override
        public ActionURL urlImage(Container c, Report r, ImageType type, @Nullable Integer revision)
        {
            ActionURL url = new ActionURL(ThumbnailAction.class, c);
            url.addParameter("reportId", r.getDescriptor().getReportId().toString());

            if (ImageType.Small == type)
                url.addParameter("imageType", "Small");

            // Add "revision" to defeat client-side caching
            url.addParameter("revision", null != revision ? revision : 0);

            return url;
        }

        @Override
        public ActionURL urlReportInfo(Container c)
        {
            return new ActionURL(ReportInfoAction.class, c);
        }

        @Override
        public ActionURL urlAttachmentReport(Container c, ActionURL returnURL)
        {
            return getCreateAttachmentReportURL(c, returnURL);
        }

        @Override
        public ActionURL urlLinkReport(Container c, ActionURL returnURL)
        {
            return getCreateLinkReportURL(c, returnURL);
        }

        @Override
        public ActionURL urlReportDetails(Container c, Report r)
        {
            return new ActionURL(DetailsAction.class, c).addParameter(ReportDescriptor.Prop.reportId, r.getDescriptor().getReportId().toString());
        }

        @Override
        public ActionURL urlQueryReport(Container c, Report r)
        {
            return new ActionURL(RenderQueryReport.class, c).addParameter(ReportDescriptor.Prop.reportId, r.getDescriptor().getReportId().toString());
        }

        @Override
        public ActionURL urlManageNotifications(Container c)
        {
            return new ActionURL(ManageNotificationsAction.class, c);
        }

        @Override
        public Pair<ActionURL, Map<String, Object>> urlAjaxExternalEditScriptReport(ViewContext viewContext, Report r)
        {
            Map<String, Object> externalEditor = r.getExternalEditorConfig(viewContext);
            if (null != externalEditor)
                return new Pair<>(new ActionURL(AjaxExternalEditScriptReportAction.class, viewContext.getContainer()), externalEditor);
            else
                return null;
        }
    }

    public ReportsController()
    {
        setActionResolver(_actionResolver);
    }

    public static class CreateSessionForm
    {
        private Object _clientContext;

        public Object getClientContext()
        {
            return _clientContext;
        }

        public void setClientContext(Object clientContext)
        {
            _clientContext = clientContext;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class CreateSessionAction extends MutatingApiAction<CreateSessionForm>
    {
        @Override
        public ApiResponse execute(CreateSessionForm form, BindException errors) throws Exception
        {
            String reportSessionId;
            //
            // create a unique key for this session.  Note that a report session id can never
            // span sessions but does span multiple requests within a session
            //
            if (PremiumService.get().isRemoteREnabled())
            {
                reportSessionId = ReportUtil.createReportSessionId();
                getViewContext().getSession().setAttribute(reportSessionId,
                        new RConnectionHolder(reportSessionId, form.getClientContext()));
            }
            else
            {
                //
                // consider: don't throw an exception
                //
                throw new ScriptException(RemoteRNotEnabledException.BASE_MESSAGE);
            }

            return new ApiSimpleResponse(Report.renderParam.reportSessionId.name(), reportSessionId);
        }
    }

    public static class DeleteSessionForm
    {
        private String _reportSessionId;

        public String getReportSessionId()
        {
            return _reportSessionId;
        }

        public void setReportSessionId(String reportSessionId)
        {
            _reportSessionId = reportSessionId;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class DeleteSessionAction extends MutatingApiAction<DeleteSessionForm>
    {
        @Override
        public ApiResponse execute(DeleteSessionForm form , BindException errors) throws Exception
        {
            if (PremiumService.get().isRemoteREnabled())
            {
                String reportSessionId = form.getReportSessionId();

                if (null != reportSessionId)
                {
                    RConnectionHolder rh = (RConnectionHolder) getViewContext().getSession().getAttribute(reportSessionId);
                    if (rh != null)
                    {
                        synchronized(rh)
                        {
                            if (!rh.isInUse())
                            {
                                rh.setConnection(null);
                                getViewContext().getSession().removeAttribute(reportSessionId);
                            }
                            else
                            {
                                throw new ScriptException("Cannot delete a report session that is currently in use");
                            }
                        }
                    }
                }
                //
                //  Don't error if rh could not be found.  This could happen because the session timed out.
                //
            }
            else
            {
                throw new ScriptException(RemoteRNotEnabledException.BASE_MESSAGE);
            }

            return new ApiSimpleResponse("success", true);
        }
    }

    public static class ExecuteScriptForm
    {
        private String _reportSessionId;
        private String _reportId;
        private String _reportName;
        private String _schemaName;
        private String _queryName;
        private String _functionName;
        private Map<String, Object> _inputParams;

        public ExecuteScriptForm()
        {
            _inputParams = new ArrayListMap<>();
        }

        public String getReportSessionId()
        {
            return _reportSessionId;
        }

        public void setReportSessionId(String reportSessionId)
        {
            _reportSessionId = reportSessionId;
        }

        public String getReportId()
        {
            return _reportId;
        }

        public void setReportId(String reportId)
        {
            _reportId = reportId;
        }

        public String getReportName()
        {
            return _reportName;
        }

        public void setReportName(String reportName)
        {
            _reportName = reportName;
        }

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

        public String getFunctionName()
        {
            return _functionName;
        }

        public void setFunctionName(String functionName)
        {
            _functionName = functionName;
        }

        public Map<String, Object> getInputParams()
        {
            return _inputParams;
        }

        public void setInputParams(Map<String, Object> inputParams)
        {
            _inputParams = inputParams;
        }
    }

    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.SelectData.class)
    public class ExecuteAction extends MutatingApiAction<ExecuteScriptForm>
    {
        //
        // returned object from the execute action must have these fields
        //
        public static final String OUTPUT_CONSOLE = "console";
        public static final String OUTPUT_ERROR = "errors";
        public static final String OUTPUT_PARAMS = "outputParams";

        @Override
        public ApiResponse execute(ExecuteScriptForm form, BindException errors) throws Exception
        {
            List<ScriptOutput> outputs;
            String reportSessionId = form.getReportSessionId();
            Map<String, Object> inputParams = form.getInputParams();

            // if we have a script (instead of report name) then execute it directly
            if (null != form.getFunctionName())
            {
                outputs = execFunction(form.getFunctionName(), reportSessionId, inputParams);
            }
            else
            {
                outputs = execReport(getReport(form), reportSessionId, inputParams);
            }

            //
            // break the outputs into console, error, and output params
            //
            return buildResponse(outputs);
        }

        private List<ScriptOutput> execReport(Report report, String reportSessionId, Map<String, Object> inputParams) throws Exception
        {
            //
            // validate that the underlying report is present and based on a script
            //
            if (null == report)
                throw new IllegalArgumentException("Unknown report id or report name");

            if (!(report instanceof Report.ScriptExecutor))
                throw new IllegalArgumentException("The specified report is not based upon a script and therefore cannot be executed.");

            //
            // used a shared sesssion if specfied and the feature is turned on
            //
            if (PremiumService.get().isRemoteREnabled())
            {
                getViewContext().put(Report.renderParam.reportSessionId.name(), reportSessionId);
            }

            //
            // execute the script
            //
            Report.ScriptExecutor exec = (Report.ScriptExecutor) report;
            return exec.executeScript(getViewContext(), inputParams);
        }

        private List<ScriptOutput> execFunction(String functionName, String reportSessionId, Map<String, Object> inputParams) throws Exception
        {
            List<ScriptOutput> scriptOutputs = new ArrayList<>();

            //
            // we must be using Rserve for this
            //
            if (PremiumService.get().isRemoteREnabled())
            {
                try
                {
                    Object result = RserveScriptEngine.eval(getViewContext(), functionName, reportSessionId, inputParams);
                    //
                    // currently only support a single json return value
                    //
                    scriptOutputs.add(new ScriptOutput(ScriptOutput.ScriptOutputType.json, "jsonout:", result.toString()));
                }
                catch(Exception e)
                {
                    String message = ReportUtil.makeExceptionString(e, "%s: %s");
                    scriptOutputs.add(new ScriptOutput(ScriptOutput.ScriptOutputType.error, e.getClass().getName(), message));
                }
            }
            else
            {
                throw new ScriptException(RemoteRNotEnabledException.BASE_MESSAGE);
            }

            return scriptOutputs;
        }

        //
        // Build our response object.  It must look like:
        // {
        //      console: string[]
        //      errors: string[]
        //      outputParams: ScriptOutput[]
        // }
        //
        private ApiResponse buildResponse(List<ScriptOutput> outputs) throws Exception
        {
            ArrayList<String> consoleOutputs = new ArrayList<>();
            ArrayList<String> errorOutputs = new ArrayList<>();
            ArrayList<ScriptOutput> removeItems = new ArrayList<>();

            // collect any console and error output types and put them in their own collections
            for (ScriptOutput output : outputs)
            {
                if (output.getType() == ScriptOutput.ScriptOutputType.console)
                {
                    consoleOutputs.add(output.getValue());
                    removeItems.add(output);
                }

                if (output.getType() == ScriptOutput.ScriptOutputType.error)
                {
                    errorOutputs.add(output.getValue());
                    removeItems.add(output);
                }
            }

            // remove console and error outputs
            outputs.removeAll(removeItems);

            // build the response object
            ApiSimpleResponse response = new ApiSimpleResponse();
            response.put(OUTPUT_CONSOLE, consoleOutputs);
            response.put(OUTPUT_ERROR, errorOutputs);
            response.putBeanList(OUTPUT_PARAMS, outputs, "name", "type", "value");
            return response;
        }

        private Report getReport(ExecuteScriptForm form)
        {
            Report report;
            String reportId = form.getReportId();

            if (reportId != null)
            {
                report = ReportUtil.getReportById(getViewContext(), reportId);
            }
            else
            {
                String reportName = form.getReportName();
                String key = ReportUtil.getReportKey(form.getSchemaName(), form.getQueryName());

                // consider:  moving this logic into the getReportKey function for 14.2
                // see issue 19206 for more details
                if (isBlank(key))
                    key = reportName;

                report = ReportUtil.getReportByName(getViewContext(), reportName, key);
            }
            return report;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class getSessionsAction extends MutatingApiAction
    {
        public static final String REPORT_SESSIONS = "reportSessions";
        @Override
        public ApiResponse execute(Object o, BindException errors) throws Exception
        {
            ArrayList<ReportSession> outputReportSessions = new ArrayList<>();

            if (PremiumService.get().isRemoteREnabled())
            {
                synchronized (RConnectionHolder.getReportSessions())
                {
                    for (String reportSessionId : RConnectionHolder.getReportSessions())
                    {
                        // ensure we only return valid sessions for this session state
                        RConnectionHolder rh = (RConnectionHolder) getViewContext().getSession().getAttribute(reportSessionId);
                        if (rh != null)
                        {
                            outputReportSessions.add(new ReportSession(rh));
                        }
                    }
                }
            }
            else
            {
                //
                // consider: don't throw an exception
                //
                throw new ScriptException(RemoteRNotEnabledException.BASE_MESSAGE);
            }

            ApiSimpleResponse response = new ApiSimpleResponse();
            response.putBeanList(REPORT_SESSIONS, outputReportSessions, "reportSessionId", "inUse", "clientContext");
            return response;
        }

        public class ReportSession
        {
            private String _reportSessionId;
            private boolean _inUse;
            private Object _clientContext;

            public ReportSession(RConnectionHolder rh)
            {
                _reportSessionId = rh.getReportSessionId();
                _inUse = rh.isInUse();
                _clientContext = rh.getClientContext();
            }

            public boolean getInUse()
            {
                return _inUse;
            }

            public String getReportSessionId()
            {
                return _reportSessionId;
            }

            public Object getClientContext()
            {
                return _clientContext;
            }
        }
    }

    @RequiresPermission(InsertPermission.class)  // Need insert AND developer (checked below)
    public class CreateScriptReportAction extends FormViewAction<ScriptReportDesignBean>
    {
        private ScriptReport _report;

        @Override
        public void validateCommand(ScriptReportDesignBean form, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(ScriptReportDesignBean form, boolean reshow, BindException errors) throws Exception
        {
            _report = form.getReport(getViewContext());
            List<ValidationError> reportErrors = new ArrayList<>();
            validatePermissions(getViewContext(), _report, reportErrors);

            if (reportErrors.isEmpty())
                return new AjaxScriptReportView(null, form, Mode.create);
            else
            {
                HtmlStringBuilder sb = HtmlStringBuilder.of();

                for (ValidationError error : reportErrors)
                    sb.append(error.getMessage()).append(HtmlString.unsafe("<br>"));

                return new HtmlView(sb.getHtmlString());
            }
        }

        @Override
        public boolean handlePost(ScriptReportDesignBean form, BindException errors)
        {
            return true;
        }

        @Override
        public ActionURL getSuccessURL(ScriptReportDesignBean form)
        {
            return null;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("jsViews");
            if (_report != null)
                root.addChild(_report.getTypeDescription() + " Builder");
        }
    }


    abstract class BaseViewScriptReportAction<BEAN extends ScriptReportBean> extends MutatingApiAction<BEAN>
    {
        @Override
        public ApiResponse execute(BEAN bean, BindException errors) throws Exception
        {
            // TODO: Do something with errors?

            // ApiAction doesn't seem to bind URL parameters on POST... so manually populate them into the bean.
            errors.addAllErrors(defaultBindParameters(bean, getViewContext().getBindPropertyValues()));
            VBox resultsView = new VBox();
            Report report = bean.getReport(getViewContext());
            if (report != null)
            {
                _log.trace("Executing report: " + report.getClass().getSimpleName());
                if (bean.getIsDirty())
                    report.clearCache();

                // for now, limit pipeline view to saved R reports
                if (null != bean.getReportId() && bean.isRunInBackground())
                {
                    if (report instanceof RReport)
                        resultsView.addView(new RenderBackgroundRReportView((RReport)report));
                    else
                        resultsView.addView(new HtmlView(DIV(cl("labkey-error"), "Report type not support background execution: " + report.getClass().getSimpleName())));
                }
                else
                {
                    HttpView<?> renderedReport = report.renderReport(getViewContext());
                    _log.trace("Report views: " + (renderedReport == null ? null : (
                            renderedReport.getViews() == null ? renderedReport.getClass().getSimpleName() :
                                    renderedReport.getViews().stream().map(mv -> mv.getClass().getSimpleName()).collect(Collectors.joining(", ")))));
                    resultsView.addView(renderedReport);
                }
            }

            Map<String, Object> resultProperties = new HashMap<>();

            LinkedHashSet<ClientDependency> dependencies = resultsView.getClientDependencies();
            LinkedHashSet<String> cssScripts = new LinkedHashSet<>();
            addScriptDependencies(bean, dependencies, cssScripts);

            LinkedHashSet<String> includes = new LinkedHashSet<>();
            LinkedHashSet<String> implicitIncludes = new LinkedHashSet<>();
            PageFlowUtil.getJavaScriptFiles(getContainer(), dependencies, includes, implicitIncludes);

            MockHttpServletResponse mr = new MockHttpServletResponse();
            mr.setCharacterEncoding(StringUtilsLabKey.DEFAULT_CHARSET.displayName());

            ViewContext nested = new ViewContext(getViewContext());
            nested.setResponse(mr);
            try (var init = HttpView.initForRequest(nested, getViewContext().getRequest(), mr))
            {
                _log.trace("Rendering report");
                resultsView.render(getViewContext().getRequest(), mr);
                var config = HttpView.currentPageConfig();
                var sw = new StringWriter();
                config.endOfBodyScript(sw);
                var script = sw.toString();
                if (!script.isBlank())
                {
                    _log.trace("Append end of body script.");
                    var out = mr.getWriter();
                    out.print("<script type=\"text/javascript\" nonce=\"" + config.getScriptNonce() + "\">");
                    out.print(script);
                    out.print("</script>");
                }
            }

            if (mr.getStatus() != HttpServletResponse.SC_OK)
            {
                _log.trace("Report error. Status: " + mr.getStatus());
                resultsView.render(getViewContext().getRequest(), getViewContext().getResponse());
                return null;
            }

            final String html = mr.getContentAsString();
            _log.trace("Report rendered. Size: " + html.length());
            resultProperties.put("html", html);
            resultProperties.put("requiredJsScripts", includes);
            resultProperties.put("requiredCssScripts", cssScripts);
            resultProperties.put("implicitJsIncludes", implicitIncludes);
            resultProperties.put("moduleContext", PageFlowUtil.getModuleClientContext(getViewContext(), dependencies));
            return new ApiSimpleResponse(resultProperties);
        }

        private void addScriptDependencies(ScriptReportBean bean, LinkedHashSet<ClientDependency> clientDependencies, LinkedHashSet<String> cssScripts)
        {
            Set<ClientDependency> scriptDependencies = ClientDependency.fromList(bean.getScriptDependencies());

            // add any css dependencies we have
            for (ClientDependency cd : scriptDependencies)
                cssScripts.addAll(cd.getCssPaths(getContainer()));

            // add these to our client dependencies
            clientDependencies.addAll(scriptDependencies);
        }
    }

    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.SelectData.class)
    public class ViewScriptReportAction extends BaseViewScriptReportAction<ScriptReportBean>
    {
    }

    @RequiresPermission(UpdatePermission.class)  // At least update; canEdit() will perform additional permissions checks
    @Action(ActionType.Configure.class)
    public class DesignScriptReportAction extends BaseViewScriptReportAction<ScriptReportDesignBean>
    {
        @Override
        public ApiResponse execute(ScriptReportDesignBean bean, BindException errors) throws Exception
        {
            ScriptReport report = bean.getReport(getViewContext());
            if (null == report || !report.canEdit(getUser(), getContainer()))
                throw new UnauthorizedException();

            return super.execute(bean, errors);
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class GetBackgroundReportResultsAction extends ReadOnlyApiAction<ScriptReportBean>
    {
        @Override
        public ApiResponse execute(ScriptReportBean bean, BindException errors) throws Exception
        {
            ScriptReport report = bean.getReport(getViewContext());
            File logFile = new File(((RReport)report).getReportDir(this.getViewContext().getContainer().getId()), RReportJob.LOG_FILE_NAME);
            PipelineStatusFile statusFile = PipelineService.get().getStatusFile(logFile);

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
            vbox.addView(new HtmlView(HtmlString.unsafe(html.toString())));

            if (statusFile != null &&
                    !(PipelineJob.TaskStatus.waiting.matches(statusFile.getStatus()) ||
                      statusFile.getStatus().equals(RReportJob.PROCESSING_STATUS)))
                vbox.addView(new RenderBackgroundRReportView((RReport)report));

            ApiSimpleResponse response = new ApiSimpleResponse();
            response.put("results", HttpView.renderToString(vbox, getViewContext().getRequest()));

            if (null != statusFile)
                response.put("status", statusFile.getStatus());

            return response;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class RunReportAction extends SimpleViewAction<ReportDesignBean>
    {
        private Report _report;

        @Override
        public ModelAndView getView(ReportDesignBean form, BindException errors) throws Exception
        {
            _report = null;

            if (null != form.getReportId())
                _report = form.getReportId().getReport(getViewContext());

            if (null == _report)
                throw new NotFoundException("Invalid report identifier, unable to create report.");

            HttpView reportView;
            try
            {
                reportView = _report.getRunReportView(getViewContext());
            }
            catch (RuntimeException e)
            {
                return new HtmlView(SPAN(cl("labkey-error"), e.getMessage(), ". Unable to create report."));
            }

            VBox vbox = new VBox();
            vbox.addView(reportView);

            if (!isPrint() && !(reportView instanceof HttpRedirectView) && DiscussionService.get() != null)
            {
                DiscussionService service = DiscussionService.get();
                String title = "Discuss report - " + _report.getDescriptor().getReportName();
                DiscussionService.DiscussionView discussion = service.getDiscussionArea(getViewContext(), _report.getEntityId(), new ActionURL(CreateScriptReportAction.class, getContainer()), title, true, false);
                if (discussion != null)
                {
                    vbox.addView(discussion);
                }
            }
            return vbox;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("reportsAndViews");
            if (_report != null)
                root.addChild(_report.getDescriptor().getReportName());
        }
    }

    @RequiresPermission(ShareReportPermission.class)
    public class ShareReportAction extends FormViewAction<ShareReportForm>
    {
        Report _report = null;
        List<User> _validRecipients = new ArrayList<>();

        @Override
        public ModelAndView getView(ShareReportForm form, boolean reshow, BindException errors)
        {
            return new JspView<>("/org/labkey/query/reports/view/shareReport.jsp", form, errors);
        }

        @Override
        public void validateCommand(ShareReportForm form, Errors errors)
        {
            _validRecipients = SecurityManager.parseRecipientListForContainer(getContainer(), form.getRecipientList(), errors);
        }

        @Override
        public boolean handlePost(ShareReportForm form, BindException errors) throws Exception
        {
            if (null != form.getReportId())
                _report = form.getReportId().getReport(getViewContext());

            if (!errors.hasErrors() && !_validRecipients.isEmpty() && _report != null)
            {
                for (User recipient : _validRecipients)
                {
                    NotificationService.get().sendMessageForRecipient(
                        getContainer(), getUser(), recipient,
                        form.getMessageSubject(), form.getMessageBody(), _report.getRunReportURL(getViewContext()),
                        form.getReportId().toString(), Report.SHARE_REPORT_TYPE
                    );

                    // if the report is already public, send the notification but don't update the policy
                    if (!ReportDescriptor.REPORT_ACCESS_PUBLIC.equals(_report.getDescriptor().getAccess()))
                        ReportUtil.updateReportSecurityPolicy(getViewContext(), _report, recipient.getUserId(), true);

                    String auditMsg = "The following report was shared: recipient: " + recipient.getName() + " (" + recipient.getUserId() + ")"
                            + ", reportId: " + _report.getDescriptor().getReportId()
                            + ", name: " + _report.getDescriptor().getReportName();
                    StudyService.get().addStudyAuditEvent(getContainer(), getUser(), auditMsg);
                }
            }

            return !errors.hasErrors();
        }

        @Override
        public URLHelper getSuccessURL(ShareReportForm form)
        {
            if (_report != null && getContainer().hasPermission(getUser(), AdminPermission.class))
            {
                return urlProvider(StudyUrls.class).getManageReportPermissions(getContainer()).
                        addParameter(ReportDescriptor.Prop.reportId, _report.getDescriptor().getReportId().toString());
            }

            return form.getReturnActionURL(form.getDefaultUrl(getContainer()));
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Share Report");
        }
    }

    public static class ShareReportForm extends ReportDesignBean
    {
        private String _recipientList;
        private String _messageSubject;
        private String _messageBody;

        public String getRecipientList()
        {
            return _recipientList;
        }

        public void setRecipientList(String recipientList)
        {
            _recipientList = recipientList;
        }

        public String getMessageSubject()
        {
            return _messageSubject;
        }

        public void setMessageSubject(String messageSubject)
        {
            _messageSubject = messageSubject;
        }

        public String getMessageBody()
        {
            return _messageBody;
        }

        public void setMessageBody(String messageBody)
        {
            _messageBody = messageBody;
        }

        public ActionURL getDefaultUrl(Container container)
        {
            return new ActionURL(ManageViewsAction.class, container);
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class DetailsAction extends SimpleViewAction<ReportDesignBean>
    {
        @Override
        public ModelAndView getView(ReportDesignBean form, BindException errors) throws Exception
        {
            Report report = form.getReport(getViewContext());
            if (null != report)
            {
                VBox box = new VBox(new JspView<>("/org/labkey/query/reports/view/reportDetails.jsp", form));

                DiscussionService service = DiscussionService.get();
                if (service != null)
                {
                    String title = "Discuss report - " + report.getDescriptor().getReportName();
                    DiscussionService.DiscussionView discussion = service.getDiscussionArea(getViewContext(), report.getEntityId(), new ActionURL(CreateScriptReportAction.class, getContainer()), title, true, false);
                    if (discussion != null)
                        box.addView(discussion);
                }

                return box;
            }
            else
                return new HtmlView(HtmlString.of("Specified report not found"));
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Report Details");
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ReportInfoAction extends SimpleViewAction<ReportDesignBean>
    {
        @Override
        public ModelAndView getView(ReportDesignBean form, BindException errors) throws Exception
        {
            return new ReportInfoView(form.getReport(getViewContext()));
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Report Debug Information");
        }
    }


    public static class ReportInfoView extends HttpView
    {
        private Report _report;

        public ReportInfoView(Report report)
        {
            _report = report;
        }

        @Override
        protected void renderInternal(Object model, PrintWriter out)
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
                    addRow(out, PageFlowUtil.filter(prop.getKey()), PageFlowUtil.filter(Objects.toString(prop.getValue(), "")));
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


    protected void validatePermissions(ViewContext context, ScriptReport report, List<ValidationError> errors)
    {
        if (report != null)
        {
            if (report.getDescriptor().isNew() && report instanceof ScriptEngineReport scriptEngineReport)
            {
                try
                {
                    ScriptEngine engine = scriptEngineReport.getScriptEngine(context.getContainer());

                    if (engine != null)
                    {
                        if (!ReportUtil.canCreateScript(context, engine.getFactory().getExtensions().get(0)))
                            //TODO update the message text
                            errors.add(new SimpleValidationError("Only users with the site Analyst permission are allowed to create script views."));
                    }
                    else
                        errors.add(new SimpleValidationError("Unable to find a configured script engine for this report."));
                }
                catch (PremiumFeatureNotEnabledException e)
                {
                    errors.add(new SimpleValidationError(e.getMessage()));
                }
            }
            else
                report.canEdit(context.getUser(), context.getContainer(), errors);
        }
        else
            errors.add(new SimpleValidationError("Unable to locate the report, it may have been deleted."));
    }


    @RequiresNoPermission
    public class AjaxSaveScriptReportAction extends MutatingApiAction<ScriptReportDesignBean>
    {
        @Override
        public void validateForm(ScriptReportDesignBean form, Errors errors)
        {
            try
            {
                ReportIdentifier id = form.getReportId();
                Report report;

                if (id != null)
                    report = id.getReport(getViewContext());
                else
                    report = form.getReport(getViewContext());

                List<ValidationError> reportErrors = new ArrayList<>();
                validatePermissions(getViewContext(), (ScriptReport)report, reportErrors);

                if (!reportErrors.isEmpty())
                {
                    for (ValidationError error : reportErrors)
                    {
                        String message = error.getMessage() != null ? error.getMessage() : "A validation error occurred";
                        errors.reject(ERROR_MSG, message);
                    }
                }
            }
            catch (Exception e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
            }
        }

        protected String getErrorCode()
        {
            return "saveScriptReport";
        }

        protected boolean isManageThumbnails()
        {
            return true;
        }

        @Override
        public ApiResponse execute(ScriptReportDesignBean form, BindException errors) throws Exception
        {
            Report report = null;

            try
            {
                if (getUser().isGuest())
                {
                    errors.reject(getErrorCode(), "You must be logged in to be able to save reports");
                    return null;
                }

                report = form.getReport(getViewContext());

                if (null == report)
                {
                    errors.reject(getErrorCode(), "Report not found.");
                }
                // on new reports, check for duplicates and non-blank name (issue 32912)
                else if (null == report.getDescriptor().getReportId())
                {
                    if (StringUtils.isEmpty(report.getDescriptor().getReportName()))
                    {
                        errors.reject(getErrorCode(), "A report name is required. Please specify a name.");
                    }
                    else if (ReportService.get().reportNameExists(getViewContext(), report.getDescriptor().getReportName(), ReportUtil.getReportQueryKey(report.getDescriptor())))
                    {
                        errors.reject(getErrorCode(), "There is already a report with the name of: '"
                                + report.getDescriptor().getReportName() + "'. Please specify a different name.");
                    }
                }
            }
            catch (Exception e)
            {
                errors.reject(getErrorCode(), e.getMessage());
            }

            if (errors.hasErrors())
                return null;

            ReportIdentifier newId = ReportService.get().saveReportEx(getViewContext(), ReportUtil.getReportQueryKey(report.getDescriptor()), report);
            report = newId.getReport(getViewContext());

            if (isManageThumbnails() && !report.getDescriptor().isModuleBased())
            {
                ThumbnailService svc = ThumbnailService.get();

                if (null != svc && (form.getThumbnailType() != null))
                {
                    if (form.getThumbnailType().equals(ThumbnailType.NONE.name()))
                    {
                        // User checked the "no thumbnail" radio... need to proactively delete the thumbnail
                        svc.deleteThumbnail(report, ImageType.Large);
                    }
                    else if (form.getThumbnailType().equals(ThumbnailType.AUTO.name()))
                    {
                        svc.replaceThumbnail(report, ImageType.Large, ThumbnailType.AUTO, getViewContext());
                    }
                }
            }

            return createResponse(form, report, errors);
        }

        protected ApiSimpleResponse createResponse(ScriptReportDesignBean form, Report report, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            response.put("success", true);

            // Issue 32095: don't need a redirect on the redirect URl we are creating for the report save
            ActionURL defaultRedirectUrl = report.getRunReportURL(getViewContext()).deleteParameter(ReportDescriptor.Prop.redirectUrl);
            response.put("redirect", form.getRedirectUrl() != null ? form.getRedirectUrl() : defaultRedirectUrl);

            return response;
        }
    }

    @RequiresNoPermission
    public class AjaxExternalEditScriptReportAction extends AjaxSaveScriptReportAction
    {
        @Override
        protected String getErrorCode()
        {
            return "externalEditScriptReport";
        }

        @Override
        protected boolean isManageThumbnails()
        {
            return false;
        }

        @Override
        protected ApiSimpleResponse createResponse(ScriptReportDesignBean form, Report report, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            Pair<String, String> externalEditor = report.startExternalEditor(getViewContext(), form.getScript(), errors);
            if (null == externalEditor) // TODO: Process errors?
            {
                response.put("success", false);
            }
            else
            {
                response.put("success", true);
                response.put("externalUrl", externalEditor.getKey());
                response.put("externalWindowTitle", externalEditor.getValue());
                response.put("redirectUrl", ReportUtil.getRunReportURL(getViewContext(), report, false)
                        .addParameter("tabId", "Source"));
                response.put("entityId", report.getEntityId());
            }
            return response;
        }
    }


    /**
     * Ajax action to start a pipeline-based R view.
     */
    @RequiresPermission(ReadPermission.class)
    public class StartBackgroundRReportAction extends MutatingApiAction<ScriptReportBean>
    {
        @Override
        public ApiResponse execute(ScriptReportBean form, BindException errors) throws Exception
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
                report = form.getReport(getViewContext());
                job = new RReportJob(ReportsPipelineProvider.NAME, info, report, root);
            }
            else
            {
                report = form.getReportId().getReport(getViewContext());
                job = new RReportJob(ReportsPipelineProvider.NAME, info, form.getReportId(), root);
            }

            if (report instanceof RReport)
            {
                ((RReport)report).deleteReportDir(context);
                ((RReport)report).createInputDataFile(getViewContext());
                PipelineService.get().queueJob(job);
                response.put("success", true);
            }

            return response;
        }
    }


    @RequiresPermission(ReadPermission.class)
    public class DownloadInputDataAction extends SimpleViewAction<ScriptReportBean>
    {
        @Override
        public ModelAndView getView(ScriptReportBean form, BindException errors) throws Exception
        {
            ScriptReport report = form.getReport(getViewContext());
            if (report instanceof RReport)
            {
                try
                {
                    File file = ((RReport)report).createInputDataFile(getViewContext());
                    if (file.exists())
                    {
                        PageFlowUtil.streamFile(getViewContext().getResponse(), file, true);
                    }
                }
                catch (SQLException e)
                {
                    _log.error("failed trying to download input RReport data", e);
                }
            }
            return null;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }


    @RequiresPermission(ReadPermission.class)
    public class StreamFileAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            String sessionKey = (String) getViewContext().get("sessionKey");
            String deleteFile = (String) getViewContext().get("deleteFile");
            String attachment = (String) getViewContext().get("attachment");
            String cacheFile = (String) getViewContext().get("cacheFile");
            if (sessionKey != null)
            {
                File file = (File) getViewContext().getRequest().getSession().getAttribute(sessionKey);
                if (file != null && file.isFile())
                {
                    Map<String, String> responseHeaders = Collections.emptyMap();
                    if (BooleanUtils.toBoolean(cacheFile))
                    {
                        responseHeaders = new HashMap<>();

                        responseHeaders.put("Pragma", "private");
                        responseHeaders.put("Cache-Control", "private");
                        responseHeaders.put("Cache-Control", "max-age=3600");
                        _log.debug("Caching file: " + file.getAbsolutePath());
                    }
                    PageFlowUtil.streamFile(getViewContext().getResponse(), responseHeaders, file, BooleanUtils.toBoolean(attachment));
                    if (BooleanUtils.toBoolean(deleteFile))
                    {
                        file.delete();
                        _log.debug("Deleting file: " + file.getAbsolutePath());
                    }
                    return null;
                }
            }
            return new HtmlView(HtmlString.of("Requested Resource not found"));
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }


    public static ActionURL getDownloadURL(Report report, Attachment attachment)
    {
        return new ActionURL(DownloadAction.class, ContainerManager.getForId(report.getContainerId()))
            .addParameter("entityId", report.getEntityId())
            .addParameter("name", attachment.getName());
    }

    @RequiresPermission(ReadPermission.class)
    public class DownloadAction extends BaseDownloadAction<AttachmentForm>
    {
        @Override
        public @Nullable Pair<AttachmentParent, String> getAttachment(AttachmentForm form)
        {
            Report report = ReportService.get().getReportByEntityId(getContainer(), form.getEntityId());

            if (null == report)
            {
                throw new NotFoundException("Unable to find report");
            }

            return report instanceof RReport || report instanceof AttachmentReport ? new Pair<>(report, form.getName()) : null;
        }
    }


    public static class AttachmentReportForm extends DataViewEditForm
    {
        public enum AttachmentReportType { local, server }

        private AttachmentReportType attachmentType;
        private String filePath; // only valid for AttachmentReportType.server
        private String uploadFileName; // only valid for AttachmentReportType.local

        public AttachmentReportType getAttachmentType()
        {
            return attachmentType;
        }

        public void setAttachmentType(AttachmentReportType attachmentType)
        {
            this.attachmentType = attachmentType;
        }

        public String getFilePath()
        {
            return filePath;
        }

        public void setFilePath(String filePath)
        {
            this.filePath = filePath;
        }

        public String getUploadFileName()
        {
            return uploadFileName;
        }

        public void setUploadFileName(String fileName)
        {
            this.uploadFileName = fileName;
        }

        public static String getHelpTopic() { return "thumbnails"; }
    }

    public static ActionURL getCreateAttachmentReportURL(Container c, ActionURL returnURL)
    {
        ActionURL url = new ActionURL(CreateAttachmentReportAction.class, c);
        url.addReturnURL(returnURL);
        return url;
    }

    public static ActionURL getCreateLinkReportURL(Container c, ActionURL returnURL)
    {
        ActionURL url = new ActionURL(CreateLinkReportAction.class, c);
        url.addReturnURL(returnURL);
        return url;
    }

    public static ActionURL getCreateQueryReportURL(Container c, ActionURL returnURL)
    {
        ActionURL url = new ActionURL(CreateQueryReportAction.class, c);
        url.addReturnURL(returnURL);
        return url;
    }

    protected abstract class BaseReportAction<F extends DataViewEditForm, R extends AbstractReport> extends FormViewAction<F>
    {
        protected void initialize(F form) throws Exception
        {
            setHelpTopic("staticReports");

            // we can share if we own the report.  if we don't
            // own the report then we'll disable the checkbox
            form.setCanChangeSharing(true);

            if (form.getReportId() != null)
            {
                R report = (R)form.getReportId().getReport(getViewContext());

                if (report != null)
                {
                    initializeForm(form, report);
                }
            }
        }

        protected void initializeForm(F form, R report) throws Exception
        {
            form.setViewName(report.getDescriptor().getReportName());
            form.setReportId(form.getReportId());
            form.setDescription(report.getDescriptor().getReportDescription());
            form.setShared(report.getDescriptor().isShared());

            ViewCategory category = report.getDescriptor().getCategory(getContainer());

            if (null != category)
                form.setCategory(category.getRowId());

            Integer authorId = report.getDescriptor().getAuthor();
            if (null != authorId)
                form.setAuthor(authorId);

            String status = report.getDescriptor().getStatus();
            form.setStatus(null != status ? ViewInfo.Status.valueOf(status) : ViewInfo.Status.None);
            form.setRefreshDate(report.getDescriptor().getRefreshDate());

            ReportService.get().validateReportPermissions(getViewContext(), report);
            form.setCanChangeSharing(report.canShare(getUser(), getContainer()));
        }


        @Override
        public void validateCommand(F form, Errors errors)
        {
            if (null == StringUtils.trimToNull(form.getViewName()))
                errors.reject("viewName", "You must enter a report name.");
        }

        protected String getReportKey(R report, F form)
        {
            return form.getViewName();
        }

        public boolean saveReport(F form, BindException errors) throws Exception
        {
            DbScope scope = CoreSchema.getInstance().getSchema().getScope();

            try (DbScope.Transaction tx = scope.ensureTransaction())
            {
                // save the category information then the report
                Integer categoryId = form.getCategory();

                R report = initializeReportForSave(form);
                ReportDescriptor descriptor = report.getDescriptor();

                descriptor.setContainer(getContainer().getId());
                descriptor.setReportName(form.getViewName());
                descriptor.setReportDescription(form.getDescription());

                // Validate that categoryId matches a category in this container
                ViewCategory category = null != categoryId ? ViewCategoryManager.getInstance().getCategory(getContainer(), categoryId) : null;
                if (null != category)
                    descriptor.setCategoryId(category.getRowId());

                // Note: Keep this code in sync with ReportViewProvider.updateProperties()
                boolean isPrivate = !form.getShared();

                if (isPrivate)
                {
                    // If switching from shared to private then set owner back to original creator.
                    if (descriptor.isShared())
                    {
                        // Convey previous state to save code, otherwise admins will be denied the ability to unshare.
                        descriptor.setWasShared();
                        descriptor.setOwner(descriptor.getCreatedBy());
                    }
                }
                else
                {
                    descriptor.setOwner(null);
                }

                User author = UserManager.getUser(form.getAuthor());
                ViewInfo.Status status = form.getStatus();
                Date refreshDate = form.getRefreshDate();

                if (author != null)
                    descriptor.setAuthor(author.getUserId());
                if (status != null)
                    descriptor.setStatus(status.name());
                if (refreshDate != null)
                    descriptor.setRefreshDate(refreshDate);

                int id = ReportService.get().saveReport(getViewContext(), getReportKey(report, form), report);

                report = (R)ReportService.get().getReport(getContainer(), id);

                afterReportSave(form, report);

                tx.commit();

                ThumbnailService svc = ThumbnailService.get();

                if (null != svc)
                    svc.queueThumbnailRendering(report, ImageType.Large, ThumbnailType.AUTO);

                return true;
            }
        }


        abstract protected R initializeReportForSave(F form);

        protected void afterReportSave(F form, R report) throws Exception
        {
        }

        @Override
        public ActionURL getSuccessURL(F uploadForm)
        {
            ActionURL defaultURL = null;
            ReportIdentifier id = uploadForm.getReportId();

            if (null != id)
            {
                Report r = id.getReport(getViewContext());
                defaultURL = new ReportUrlsImpl().urlReportDetails(getContainer(), r);
            }

            if (defaultURL == null)
                defaultURL = new ActionURL(ManageViewsAction.class, getContainer());

            return uploadForm.getReturnActionURL(defaultURL);
        }
    }

    @RequiresPermission(InsertPermission.class)
    public abstract class BaseAttachmentReportAction extends BaseReportAction<AttachmentReportForm, AttachmentReport>
    {
        @Override
        public ModelAndView getView(AttachmentReportForm form, boolean reshow, BindException errors) throws Exception
        {
            initialize(form);
            return new JspView<>("/org/labkey/query/reports/view/attachmentReport.jsp", form, errors);
        }

        @Override
        public void validateCommand(AttachmentReportForm form, Errors errors)
        {
            super.validateCommand(form, errors);
            if (errors.hasErrors())
                return;

            Map<String, MultipartFile> fileMap = getFileMap();
            MultipartFile[] formFiles = fileMap.values().toArray(new MultipartFile[fileMap.size()]);

            if (form.getAttachmentType() == AttachmentReportForm.AttachmentReportType.server)
            {
                String filePath = StringUtils.trimToNull(form.getFilePath());

                // Only site administrators can specify a path, #14445
                if (null != filePath)
                    if (!getContainer().hasPermission(getUser(), AdminOperationsPermission.class))
                        throw new UnauthorizedException();

                // NOTE: no need to check if this is set because enforced in UI.
                File file = new File(form.getFilePath());
                if (!file.exists())
                    errors.reject("filePath", "File path is not a valid path on the server.");
            }
            else if (form.getAttachmentType() == AttachmentReportForm.AttachmentReportType.local)
            {
                // Require a file if we are creating a new report or if the report we are updating
                // does not already have an attached file.  This could occur if the user updated
                // the AttachmentReportType from server to local
                boolean requireFile = true;

                if (form.isUpdate())
                {
                    // if the form we are updating already has an attachment then it is okay not to submit one
                    AttachmentReport report = (AttachmentReport) form.getReportId().getReport(getViewContext());
                    requireFile = (null == report.getLatestVersion());
                }

                if (requireFile)
                {
                    if (0 == formFiles.length || formFiles[0].isEmpty())
                    {
                        errors.reject("filePath", "You must specify a file");
                    }
                    else
                    {
                        String filename = formFiles[0].getOriginalFilename();
                        for (String reserved : ThumbnailService.ImageFilenames)
                        {
                            if (reserved.equalsIgnoreCase(filename))
                            {
                                errors.reject("filePath", "You may not specify a file named Thumbnail or SmallThumbnail");
                                break;
                            }
                        }
                    }
                }
            }
            else
            {
                errors.reject(ERROR_MSG, "Unknown attachment report type");
            }
        }

        @Override
        public boolean handlePost(AttachmentReportForm form, BindException errors) throws Exception
        {
            return saveReport(form, errors);
        }

        @Override
        protected AttachmentReport initializeReportForSave(AttachmentReportForm form)
        {
            AttachmentReport report = (AttachmentReport) (form.isUpdate() ? form.getReportId().getReport(getViewContext()) : ReportService.get().createReportInstance(AttachmentReport.TYPE));

            if (getContainer().hasPermission(getUser(), AdminOperationsPermission.class))
            {
                // only a site admin can create or update an attachment report with a server path
                if (form.getAttachmentType() == AttachmentReportForm.AttachmentReportType.server)
                {
                    report.setFilePath(form.getFilePath());
                }
                else
                {
                    // a site admin may edit an attachment report and change its type from server to local
                    // for this case be sure to remove the file path before save
                    report.setFilePath(null);
                }
            }

            return report;
        }

    }

    @RequiresPermission(InsertPermission.class)
    public class CreateAttachmentReportAction extends BaseAttachmentReportAction
    {
        @Override
        protected void afterReportSave(AttachmentReportForm form, AttachmentReport report) throws Exception
        {
            if (form.getAttachmentType() == AttachmentReportForm.AttachmentReportType.local)
            {
                List<AttachmentFile> attachments = getAttachmentFileList();
                if (attachments != null && attachments.size() > 0)
                {
                    AttachmentService.get().addAttachments(report, attachments, getUser());
                }
            }

            super.afterReportSave(form, report);
        }


        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Create Attachment Report");
        }
    }

    @RequiresPermission(InsertPermission.class)
    public class UpdateAttachmentReportAction extends BaseAttachmentReportAction
    {
        @Override
        protected void initializeForm(AttachmentReportForm form, AttachmentReport report) throws Exception
        {
            super.initializeForm(form, report);
            String filePath = report.getFilePath();

            if (StringUtils.isNotEmpty(filePath))
            {
                form.setAttachmentType(AttachmentReportForm.AttachmentReportType.server);
                form.setFilePath(filePath);
            }
            else
            {
                form.setAttachmentType(AttachmentReportForm.AttachmentReportType.local);
                Attachment latest = report.getLatestVersion();
                if (latest != null)
                {
                    form.setUploadFileName(latest.getName());
                }
                else
                {
                    // a report must have an attachment or server link somewhere
                    throw new IllegalStateException();
                }
            }
        }

        @Override
        protected void afterReportSave(AttachmentReportForm form, AttachmentReport report) throws Exception
        {
            //
            // We need to deal with attachments because the following cases could occur on update
            // 1) local -> server, remove existing attachments [admin only]
            // 2) local -> local, remove existing attachments, add new ones
            // 3) server -> local, no existing attachments, add new ones [admin only]
            // 4) server -> server, no existing attachments, no new ones [admin only]
            //
            if (form.getAttachmentType() == AttachmentReportForm.AttachmentReportType.local)
            {
                List<AttachmentFile> attachments = getAttachmentFileList();

                //
                // if the user has provided an attachment, then remove previous and add new
                // otherwise, keep the existing attachment.  There is no way to "clear" an attachment.  An
                // attachment report must either specify a local or server attachment.
                //
                if (attachments != null && attachments.size() > 0)
                {
                    // be sure to remove any existing local attachments
                    AttachmentService.get().deleteAttachments(report);
                    AttachmentService.get().addAttachments(report, attachments, getUser());

                    // see NOTE in AttachmentReport.hasContentModified
                    report.getDescriptor().setContentModified();
                    ReportService.get().saveReport(getViewContext(), getReportKey(report, form), report);
                }
            }
            else
            {
                //
                // updated attachment type is server so be sure to discard any attachments in case this
                // attachment type was local previously
                //
                AttachmentService.get().deleteAttachments(report);
            }

            super.afterReportSave(form, report);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Update Attachment Report");
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class DownloadModuleReportThumbnailAction extends SimpleViewAction<ModuleReportForm>
    {
        @Override
        public ModelAndView getView(ModuleReportForm form, BindException errors) throws Exception
        {
            ReportIdentifier reportId = form.getReportId();
            String imageFilePrefix = form.getImageFilePrefix();
            if (null == reportId)
                throw new NotFoundException("ReportId not specified");

            Report report = reportId.getReport(getViewContext());
            if (null == report)
                throw new NotFoundException("Report not found");

            Resource imageResource = ReportUtil.getModuleImageFile(report, imageFilePrefix);

            if (null != imageResource)
                PageFlowUtil.streamFile(getViewContext().getResponse(), ((FileResource) imageResource).getFile(), true);

            return null;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }

    public static class ModuleReportForm extends DataViewEditForm
    {
        private String imageFilePrefix;

        public String getImageFilePrefix()
        {
            return imageFilePrefix;
        }

        public void setImageFilePrefix(String fileName)
        {
            this.imageFilePrefix = fileName;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class DownloadReportFileAction extends SimpleViewAction<AttachmentReportForm>
    {
        @Override
        public ModelAndView getView(AttachmentReportForm form, BindException errors) throws Exception
        {
            ReportIdentifier reportId = form.getReportId();

            if (null == reportId)
                throw new NotFoundException("ReportId not specified");

            Report report = reportId.getReport(getViewContext());

            if (null == report)
                throw new NotFoundException("Report not found");

            if (report instanceof AttachmentReport)
            {
                AttachmentReport aReport = (AttachmentReport)report;

                if (null == aReport.getFilePath())
                    throw new NotFoundException("Report is not a server file attachment report");

                File file = new File(aReport.getFilePath());
                if (!file.exists())
                    throw new NotFoundException("Could not find file with name " + aReport.getFilePath());

                boolean isInlineImage = _mimeMap.isInlineImageFor(aReport.getFilePath());
                boolean asAttachment = !isInlineImage;

                PageFlowUtil.streamFile(getViewContext().getResponse(), file, asAttachment);
            }
            return null;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }


    public static class LinkReportForm extends DataViewEditForm
    {
        private String linkUrl;
        private boolean targetNewWindow = true;

        public String getLinkUrl()
        {
            return linkUrl;
        }

        public void setLinkUrl(String linkUrl)
        {
            this.linkUrl = linkUrl;
        }

        public boolean isTargetNewWindow()
        {
            return targetNewWindow;
        }

        public void setTargetNewWindow(boolean b)
        {
            this.targetNewWindow = b;
        }
    }

    @RequiresPermission(InsertPermission.class)
    public abstract class BaseLinkReportAction extends BaseReportAction<LinkReportForm, LinkReport>
    {
        @Override
        public ModelAndView getView(LinkReportForm form, boolean reshow, BindException errors) throws Exception
        {
            initialize(form);
            return new JspView<>("/org/labkey/query/reports/view/linkReport.jsp", form, errors);
        }

        @Override
        public void validateCommand(LinkReportForm form, Errors errors)
        {
            super.validateCommand(form, errors);

            String linkUrl = StringUtils.trimToNull(form.getLinkUrl());
            if (null == linkUrl)
            {
                errors.reject("linkUrl", "You must specify a link URL");
            }
            else
            {
                if (linkUrl.startsWith("/") || linkUrl.startsWith("http://") || linkUrl.startsWith("https://"))
                {
                    try
                    {
                        URLHelper url = new URLHelper(linkUrl);
                    }
                    catch (URISyntaxException e)
                    {
                        errors.reject("linkUrl", "You must specify a valid link URL: " + e.getMessage());
                    }
                }
                else
                {
                    errors.reject("linkUrl", "Link URL must be either absolute (starting with http or https) or relative to this server (start with '/')");
                }
            }
        }

        @Override
        public boolean handlePost(LinkReportForm form, BindException errors) throws Exception
        {
            return saveReport(form, errors);
        }

        @Override
        protected LinkReport initializeReportForSave(LinkReportForm form)
        {
            LinkReport report;

            if (form.isUpdate())
            {
                Report r = form.getReportId().getReport(getViewContext());

                if (r instanceof LinkReport)
                    report = (LinkReport)r.clone();
                else
                    throw new NotFoundException("Report does not exist");
            }
            else
            {
                report = (LinkReport)ReportService.get().createReportInstance(LinkReport.TYPE);
            }

            report.setRunReportTarget(form.isTargetNewWindow() ? "_blank" : null);

            try
            {
                URLHelper url = new URLHelper(form.getLinkUrl());
                report.setUrl(url);
            }
            catch (URISyntaxException e)
            {
                // Shouldn't happen -- we've already checked the URL is not malformed in validateCommand.
                throw new IllegalArgumentException(e.getMessage());
            }

            return report;
        }
    }

    @RequiresPermission(InsertPermission.class)
    public class CreateLinkReportAction extends BaseLinkReportAction
    {
        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Create Link Report");
        }
    }

    @RequiresPermission(InsertPermission.class)
    public class UpdateLinkReportAction extends BaseLinkReportAction
    {
        @Override
        protected void initializeForm(LinkReportForm form, LinkReport report) throws Exception
        {
            super.initializeForm(form, report);

            URL url = report.getURL();
            if (null != url)
                form.setLinkUrl(url.toString());
            form.setTargetNewWindow(null != report.getRunReportTarget());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Update Link Report");
        }
    }

    public static class QueryReportForm extends DataViewEditForm
    {
        private String selectedSchemaName;
        private String selectedQueryName;
        private String selectedViewName;

        public String getSelectedSchemaName()
        {
            return selectedSchemaName;
        }

        public void setSelectedSchemaName(String selectedSchemaName)
        {
            this.selectedSchemaName = selectedSchemaName;
        }

        public String getSelectedQueryName()
        {
            return selectedQueryName;
        }

        public void setSelectedQueryName(String selectedQueryName)
        {
            this.selectedQueryName = selectedQueryName;
        }

        public String getSelectedViewName()
        {
            return selectedViewName;
        }

        public void setSelectedViewName(String selectedViewName)
        {
            this.selectedViewName = selectedViewName;
        }

    }

    @RequiresPermission(InsertPermission.class)
    public class CreateQueryReportAction extends BaseReportAction<QueryReportForm, QueryReport>
    {
        @Override
        public ModelAndView getView(QueryReportForm form, boolean reshow, BindException errors) throws Exception
        {
            initialize(form);
            return new JspView<>("/org/labkey/query/reports/view/createQueryReport.jsp", form, errors);
        }

        @Override
        public void validateCommand(QueryReportForm form, Errors errors)
        {
            super.validateCommand(form, errors);

            String schemaName = StringUtils.trimToNull(form.getSelectedSchemaName());
            String queryName = StringUtils.trimToNull(form.getSelectedQueryName());

            if (null == schemaName)
            {
                errors.reject("selectedSchemaName", "You must specify a schema");
            }

            if (null == queryName)
            {
                errors.reject("selectedQueryName", "You must specify a query");
            }
        }

        @Override
        public boolean handlePost(QueryReportForm form, BindException errors) throws Exception
        {
            return saveReport(form, errors);
        }

        @Override
        protected QueryReport initializeReportForSave(QueryReportForm form)
        {
            QueryReport report = (QueryReport)ReportService.get().createReportInstance(QueryReport.TYPE);

            report.setSchemaName(form.getSelectedSchemaName());
            report.setQueryName(form.getSelectedQueryName());
            report.setViewName(form.getSelectedViewName());

            return report;
        }

        @Override
        protected String getReportKey(QueryReport report, QueryReportForm form)
        {
            return ReportUtil.getReportQueryKey(report.getDescriptor());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Create Query Report");
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ManageViewsAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object form, BindException errors) throws Exception
        {
            WebPartFactory factory = Portal.getPortalPart(DataViewsWebPartFactory.NAME);
            if (factory != null)
            {
                Portal.WebPart part = factory.createWebPart();
                part.getPropertyMap().put("manageView", "true");

                WebPartView view = factory.getWebPartView(getViewContext(), part);

                setTitle("Manage Views");
                setHelpTopic("manageViews");
                view.setTitle("Manage Views");
                view.setIsWebPart(false);

                return view;
            }
            return null;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class RenameReportAction extends FormViewAction<ReportDesignBean>
    {
        private String _newReportName;
        private Report _report;

        @Override
        public void validateCommand(ReportDesignBean form, Errors errors)
        {
            ReportIdentifier reportId =  form.getReportId();
            _newReportName =  form.getReportName();

            if (!StringUtils.isEmpty(_newReportName))
            {
                try
                {
                    if (null != reportId)
                        _report = reportId.getReport(getViewContext());

                    if (_report != null)
                    {
                        if (!_report.canEdit(getUser(), getContainer()))
                        {
                            errors.reject("renameReportAction", "Unauthorized operation");
                            return;
                        }

                        if (ReportService.get().reportNameExists(getViewContext(), _newReportName, _report.getDescriptor().getReportKey()))
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

        @Override
        public ModelAndView getView(ReportDesignBean form, boolean reshow, BindException errors) throws Exception
        {
            ManageViewsAction action = new ManageViewsAction();
            action.setViewContext(getViewContext());
            action.setPageConfig(getPageConfig());
            return action.getView(null, errors);
        }

        @Override
        public boolean handlePost(ReportDesignBean form, BindException errors)
        {
            _report.getDescriptor().setReportName(_newReportName);
            ReportService.get().saveReport(getViewContext(), _report.getDescriptor().getReportKey(), _report);

            return true;
        }

        @Override
        public ActionURL getSuccessURL(ReportDesignBean form)
        {
            return new ActionURL(ManageViewsAction.class, getContainer());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ReportSectionsAction extends ReadOnlyApiAction
    {
        @Override
        public ApiResponse execute(Object o, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            ReportIdentifier reportId = ReportService.get().getReportIdentifier((String)getViewContext().get(ReportDescriptor.Prop.reportId.name()), getViewContext().getUser(), getViewContext().getContainer());
            String sections = (String)getViewContext().get(Report.renderParam.showSection.name());
            if (reportId != null)
            {
                Report report = reportId.getReport(getViewContext());

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

    @RequiresPermission(ReadPermission.class)
    public class CrosstabExportAction extends SimpleViewAction<ReportDesignBean>
    {
        @Override
        public ModelAndView getView(ReportDesignBean form, BindException errors) throws Exception
        {
            ReportIdentifier reportId = form.getReportId();
            if (reportId != null)
            {
                Report report = reportId.getReport(getViewContext());
                if (report instanceof CrosstabReport)
                {
                    ExcelWriter writer = ((CrosstabReport)report).getExcelWriter(getViewContext());
                    writer.renderWorkbook(getViewContext().getResponse());
                }
            }
            return null;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }


    @RequiresPermission(ReadPermission.class)
    public class ThumbnailAction extends BaseThumbnailAction<ThumbnailForm>
    {
        @Override
        public ThumbnailProvider getProvider(ThumbnailForm form)
        {
            return form.getReportId().getReport(getViewContext());
        }

        @Override
        protected ImageType getImageType(ThumbnailForm form)
        {
            if ("Small".equals(form.getImageType()))
                return ImageType.Small;
            else
                return ImageType.Large;
        }
    }


    public static class ThumbnailForm
    {
        private ReportIdentifier _reportId;
        private String _imageType;

        public ReportIdentifier getReportId()
        {
            return _reportId;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setReportId(ReportIdentifier reportId)
        {
            _reportId = reportId;
        }

        public String getImageType()
        {
            return _imageType;
        }

        @SuppressWarnings("UnusedDeclaration")
        public void setImageType(String imageType)
        {
            _imageType = imageType;
        }
    }

    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.SelectData.class)
    public class RenderQueryReport extends SimpleViewAction<ReportDesignBean>
    {
        String _reportName;

        @Override
        public ModelAndView getView(ReportDesignBean form, BindException errors)
        {
            ReportIdentifier id = form.getReportId();

            if (id != null)
            {
                Report report = id.getReport(getViewContext());
                if (report != null)
                {
                    _reportName = report.getDescriptor().getReportName();

                    VBox view = new VBox(new JspView<>("/org/labkey/api/reports/report/view/renderQueryReport.jsp", report));

                    if (!isPrint() && DiscussionService.get() != null)
                    {
                        DiscussionService service = DiscussionService.get();
                        String title = "Discuss report - " + _reportName;
                        DiscussionService.DiscussionView discussion = service.getDiscussionArea(getViewContext(), report.getEntityId(), new ActionURL(CreateScriptReportAction.class, getContainer()), title, true, false);
                        if (discussion != null)
                            view.addView(discussion);
                    }
                    return view;
                }
            }
            return new HtmlView(HtmlString.unsafe("<span class=\"labkey-error\">Invalid report identifier, unable to render report.</span>"));
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            if (_reportName != null)
                root.addChild(_reportName);
            else
                root.addChild("Query Report");
        }
    }

    public static class BrowseDataForm extends ReturnUrlForm implements CustomApiForm
    {
        private int index;
        private String pageId;
        private boolean includeData = true;
        private boolean includeMetadata = true;
        private boolean manageView;
        private int _parent = -2;
        Map<String, Object> _props;
        private boolean includeUncategorized = false;

        private ViewInfo.DataType[] _dataTypes = new ViewInfo.DataType[]{ViewInfo.DataType.reports, ViewInfo.DataType.datasets, ViewInfo.DataType.queries};

        public ViewInfo.DataType[] getDataTypes()
        {
            return _dataTypes;
        }

        public void setDataTypes(ViewInfo.DataType[] dataTypes)
        {
            _dataTypes = dataTypes;
        }

        public int getIndex()
        {
            return index;
        }

        public void setIndex(int index)
        {
            this.index = index;
        }

        public String getPageId()
        {
            return pageId;
        }

        public void setPageId(String pageId)
        {
            this.pageId = pageId;
        }

        public boolean includeData()
        {
            return includeData;
        }

        public void setIncludeData(boolean includedata)
        {
            includeData = includedata;
        }

        public boolean includeMetadata()
        {
            return includeMetadata;
        }

        public void setIncludeMetadata(boolean includeMetadata)
        {
            this.includeMetadata = includeMetadata;
        }

        public void setParent(int parent)
        {
            _parent = parent;
        }

        public int getParent()
        {
            return _parent;
        }

        public boolean isManageView()
        {
            return manageView;
        }

        public void setManageView(boolean manageView)
        {
            this.manageView = manageView;
        }

        public boolean includeUncategorized() { return includeUncategorized; }

        public void setIncludeUncategorized(boolean includeUncategorized) { this.includeUncategorized = includeUncategorized; }

        @Override
        public void bindProperties(Map<String, Object> props)
        {
            _props = props;
        }

        public Map<String, Object> getProps()
        {
            return _props;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class BrowseDataAction extends ReadOnlyApiAction<BrowseDataForm>
    {
        @Override
        public ApiResponse execute(BrowseDataForm form, BindException errors) throws Exception
        {
            Map<String, Map<String, Object>> types = new TreeMap<>();
            ApiSimpleResponse response = new ApiSimpleResponse();

            Portal.WebPart webPart = Portal.getPart(getContainer(), form.getPageId(), form.getIndex());

            Map<String, String> props;
            if (webPart != null)
            {
                props = webPart.getPropertyMap();
                Map<String, String> webPartProps = new HashMap<>();
                webPartProps.put("name", webPart.getName());
                if (props.containsKey("webpart.title"))
                    webPartProps.put("title", props.get("webpart.title"));
                if (props.containsKey("webpart.height"))
                    webPartProps.put("height", props.get("webpart.height"));
                else
                    webPartProps.put("height", String.valueOf(700));
                if (props.containsKey("webpart.useDynamicHeight"))
                {
                    webPartProps.put("useDynamicHeight", props.get("webpart.useDynamicHeight"));
                }
                // older installs wont have a useDynamicHeight prop but thats ok
                else if (props.containsKey("webpart.height") && null != props.get("webpart.height") && !props.get("webpart.height").equals("0"))
                {
                    webPartProps.put("useDynamicHeight", "false");
                }
                else
                {
                    // only default to true if height does not already exist
                    // this allows older installs to keep their already specific Data View panel heights
                    // while allowing new installs to default to using dynamic heights based on number of rows
                    webPartProps.put("useDynamicHeight", "true");
                }
                response.put("webpart", new JSONObject(webPartProps));
            }
            else if (form.isManageView())
            {
                props = getAdminConfiguration();
            }
            else
            {
                props = resolveJSONProperties(form.getProps());
            }

            SortOrder sortOrder;
            if (props.containsKey("sortOrder"))
            {
                sortOrder = SortOrder.valueOf(props.get("sortOrder"));
            }
            else
            {
                sortOrder = SortOrder.BY_DISPLAY_ORDER;  // default
            }
            response.put("sortOrder", sortOrder.name());

            List<DataViewProvider.Type> visibleDataTypes = new ArrayList<>();
            for (DataViewProvider.Type type : DataViewService.get().getDataTypes(getContainer(), getUser()))
            {
                Map<String, Object> info = new HashMap<>();
                boolean visible = getCheckedState(type.getName(), props, type.isShowByDefault());

                info.put("name", type.getName());
                info.put("visible", visible);
                info.put("description", type.getDescription());

                types.put(type.getName(), info);

                if (visible)
                    visibleDataTypes.add(type);
            }

            response.put("types", new JSONArray(types.values()));

            //The purpose of this flag is so LABKEY.Query.getDataViews() can omit additional information only used to render the
            //webpart.  this also leaves flexibility to change that metadata
            if (form.includeMetadata())
            {
                // visible columns
                Map<String, Map<String, Boolean>> columns = new LinkedHashMap<>();

                columns.put("Type", Collections.singletonMap("checked", getCheckedState("Type", props, false)));
                columns.put("Author", Collections.singletonMap("checked", getCheckedState("Author", props, false)));
                columns.put("Modified", Collections.singletonMap("checked", getCheckedState("Modified", props, false)));
                columns.put("Content Modified", Collections.singletonMap("checked", getCheckedState("Content Modified", props, false)));
                columns.put("Status", Collections.singletonMap("checked", getCheckedState("Status", props, false)));
                columns.put("Access", Collections.singletonMap("checked", getCheckedState("Access", props, true)));
                columns.put("Details", Collections.singletonMap("checked", getCheckedState("Details", props, true)));
                columns.put("Data Cut Date", Collections.singletonMap("checked", getCheckedState("Data Cut Date", props, false)));

                response.put("visibleColumns", columns);

                // provider editor information
                Map<String, Map<String, Object>> viewTypeProps = new HashMap<>();
                for (DataViewProvider.Type type : visibleDataTypes)
                {
                    DataViewProvider provider = DataViewService.get().getProvider(type, getViewContext());
                    DataViewProvider.EditInfo editInfo = provider.getEditInfo();
                    if (editInfo != null)
                        viewTypeProps.put(type.getName(), DataViewService.get().toJSON(getContainer(), getUser(), editInfo));
                }
                response.put("editInfo", viewTypeProps);
            }

            if (form.includeData())
            {
                int startingDefaultDisplayOrder = 0;
                Set<String> defaultCategories = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

                getViewContext().put(ActionURL.Param.returnUrl.name(), form.getReturnActionURL());

                // get the data view information from all visible providers
                List<DataViewInfo> views = new ArrayList<>();

                for (DataViewProvider.Type type : visibleDataTypes)
                {
                    DataViewProvider provider = DataViewService.get().getProvider(type, getViewContext());
                    views.addAll(provider.getViews(getViewContext()));
                }

                for (DataViewInfo info : views)
                {
                    ViewCategory category = info.getCategory();

                    if (category != null)
                    {
                        if (category.getDisplayOrder() != ReportUtil.DEFAULT_CATEGORY_DISPLAY_ORDER)
                            startingDefaultDisplayOrder = Math.max(startingDefaultDisplayOrder, category.getDisplayOrder());
                        else
                            defaultCategories.add(category.getLabel());
                    }
                }

                // add the default categories after the explicit categories
                Map<String, Integer> defaultCategoryMap = new HashMap<>();
                for (String defaultCategory : defaultCategories)
                {
                    defaultCategoryMap.put(defaultCategory, ++startingDefaultDisplayOrder);
                }

                for (DataViewInfo info : views)
                {
                    ViewCategory category = info.getCategory();

                    if (category != null)
                    {
                        if (category.getDisplayOrder() == ReportUtil.DEFAULT_CATEGORY_DISPLAY_ORDER && defaultCategoryMap.containsKey(category.getLabel()))
                            category.setDisplayOrder(defaultCategoryMap.get(category.getLabel()));
                    }
                }
                response.put("data", DataViewService.get().toJSON(getContainer(), getUser(), views));
            }

            return response;
        }

        private Boolean getCheckedState(String prop, Map<String, String> propMap, boolean defaultState)
        {
            if (propMap.containsKey(prop))
            {
                return !propMap.get(prop).equals("0");
            }
            return defaultState;
        }

        private Map<String, String> resolveJSONProperties(Map<String, Object> formProps)
        {
            JSONObject jsonProps = (JSONObject) formProps;
            Map<String, String> props = new HashMap<>();
            boolean explicit = false;

            if (null != jsonProps && jsonProps.size() > 0)
            {
                try
                {
                    // Data Types Filter
                    JSONArray dataTypes = jsonProps.getJSONArray("dataTypes");
                    for (int i=0; i < dataTypes.length(); i++)
                    {
                        props.put((String) dataTypes.get(i), "on");
                        explicit = true;
                    }
                }
                catch (JSONException x)
                {
                    /* No-op */
                }

                if (explicit)
                {
                    for (ViewInfo.DataType t : ViewInfo.DataType.values())
                    {
                        if (!props.containsKey(t.name()))
                        {
                            props.put(t.name(), "0");
                        }
                    }
                }
            }

            return props;
        }
    }

    private enum SortOrder
    {
        ALPHABETICAL,
        BY_DISPLAY_ORDER
    }

    private Map<String, String> getAdminConfiguration()
    {
        Map<String, String> props = new HashMap<>();

        for (DataViewProvider.Type type : DataViewService.get().getDataTypes(getContainer(), getUser()))
        {
            String typeName = type.getName();

            if (typeName.equalsIgnoreCase("reports") ||
                typeName.equalsIgnoreCase("grid views"))
            {
                props.put(typeName, "1");
            }
            else
                props.put(typeName, "0");
        }

        props.put("sortOrder", SortOrder.BY_DISPLAY_ORDER.name());

        // visible columns
        props.put("Type", "1");
        props.put("Author", "1");
        props.put("Modified", "1");
        props.put("Status", "1");
        props.put("Access", "1");
        props.put("Details", "1");
        props.put("Data Cut Date", "1");

        return props;
    }


    static final Comparator<ViewCategory> categoryComparator = (c1, c2) ->
    {
        int order = ((Integer) c1.getDisplayOrder()).compareTo(c2.getDisplayOrder());
        if (order == 0)
            return c1.getLabel().compareToIgnoreCase(c2.getLabel());
        else if (c1.getLabel().equalsIgnoreCase("Uncategorized"))
            return 1;
        else if (c2.getLabel().equalsIgnoreCase("Uncategorized"))
            return -1;
        else if (c1.getDisplayOrder() == 0)
            return 1;
        else if (c2.getDisplayOrder() == 0)
            return -1;
        return order;
    };

    static class CategoryHelper implements Comparable<CategoryHelper>
    {
        public final ViewCategory category;
        public final Path path;
        public final List<DataViewInfo> views = new ArrayList<>();
        public final TreeSet<CategoryHelper> children = new TreeSet<>();

        CategoryHelper(ViewCategory vc, Path p)
        {
            category = vc;
            path = p;
        }

        @Override
        public int compareTo(@NotNull ReportsController.CategoryHelper other)
        {
            return categoryComparator.compare(this.category, other.category);
        }
    }

    static CategoryHelper addCategoryHelper(Map<Integer,CategoryHelper> map, ViewCategory vc)
    {
        if (null==vc) return null;
        if (map.containsKey(vc.getRowId()))
            return map.get(vc.getRowId());
        ViewCategory parent = vc.getParentCategory();
        CategoryHelper parentHelper = addCategoryHelper(map,parent);
        Path path = null==parentHelper ? new Path(vc.getLabel()) : parentHelper.path.append(vc.getLabel());
        CategoryHelper ret = new CategoryHelper(vc, path);
        map.put(vc.getRowId(),ret);
        return ret;
    }


    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.SelectMetaData.class)
    public class BrowseDataTreeAction extends ReadOnlyApiAction<BrowseDataForm>
    {
        SortOrder _sortOrder;

        @Override
        public ApiResponse execute(BrowseDataForm form, BindException errors) throws Exception
        {
            Portal.WebPart webPart = Portal.getPart(getContainer(), form.getPageId(), form.getIndex());

            Map<String, String> props;
            if (webPart != null)
            {
                props = webPart.getPropertyMap();
                String sortOrderString = props.get("sortOrder");
                if(sortOrderString != null)
                {
                    _sortOrder = SortOrder.valueOf(sortOrderString);
                }
                else  // sort order has never been set before
                {
                    _sortOrder = SortOrder.BY_DISPLAY_ORDER;  // so use default
                }
            }
            else
            {
                _sortOrder = SortOrder.BY_DISPLAY_ORDER;  // use default
            }

            HttpServletResponse resp = getViewContext().getResponse();
            resp.setContentType("application/json");

            List<DataViewInfo> views = getTreeData(form);
            resp.getWriter().write(buildTree(views, true).toString());

            return null;
        }

        private List<DataViewInfo> getTreeData(BrowseDataForm form) throws Exception
        {
            List<DataViewProvider.Type> visibleDataTypes = getVisibleDataTypes(form);

            int startingDefaultDisplayOrder = 0;
            Set<String> defaultCategories = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

            if (null != form.getReturnUrl())
                getViewContext().put(ActionURL.Param.returnUrl.name(), form.getReturnActionURL());

            // get the data view information from all visible providers
            List<DataViewInfo> views = new ArrayList<>();

            for (DataViewProvider.Type type : visibleDataTypes)
            {
                for (DataViewInfo info : DataViewService.get().getProvider(type, getViewContext()).getViews(getViewContext()))
                {
                    if (!form.isManageView() || !info.isReadOnly())
                        views.add(info);
                }
            }

            for (DataViewInfo info : views)
            {
                ViewCategory category = info.getCategory();

                if (category != null)
                {
                    if (category.getDisplayOrder() != ReportUtil.DEFAULT_CATEGORY_DISPLAY_ORDER)
                    {
                        startingDefaultDisplayOrder = Math.max(startingDefaultDisplayOrder, category.getDisplayOrder());
                    }
                    else
                        defaultCategories.add(category.getLabel());
                }
            }

            // add the default categories after the explicit categories
            Map<String, Integer> defaultCategoryMap = new HashMap<>();
            for (String cat : defaultCategories)
            {
                defaultCategoryMap.put(cat, ++startingDefaultDisplayOrder);
            }

            for (DataViewInfo info : views)
            {
                ViewCategory category = info.getCategory();

                if (category != null)
                {
                    if (category.getDisplayOrder() == ReportUtil.DEFAULT_CATEGORY_DISPLAY_ORDER && defaultCategoryMap.containsKey(category.getLabel()))
                        category.setDisplayOrder(defaultCategoryMap.get(category.getLabel()));
                }
            }
            return views;
        }


        private JSONObject buildTree(List<DataViewInfo> views, boolean collapseByLabel)
        {
            ViewCategoryManager vcm = ViewCategoryManager.getInstance();

            // get the collection of all distinct categories
            Map<Integer, CategoryHelper> categories = new HashMap<>();
            for (DataViewInfo view : views)
            {
                ViewCategory og = view.getCategory();
                if (null != og)
                {
                    if (og.getLabel().equalsIgnoreCase("Uncategorized"))
                        addCategoryHelper(categories, og);
                    else
                        addCategoryHelper(categories, vcm.getCategory(og.getContainerId(), og.getRowId()));
                }
            }

            Map<Integer, Path> mapRowidPath = new HashMap<>();
            Map<Path, CategoryHelper> mapPathHelper = new HashMap<>();
            if (collapseByLabel)
            {
                // For each distinct path pick one CategoryHelper
                for (CategoryHelper helper : categories.values())
                    mapRowidPath.put(helper.category.getRowId(), helper.path);
                String localContainerId = getContainer().getId();
                for (CategoryHelper helper : categories.values())
                {
                    CategoryHelper existing = mapPathHelper.putIfAbsent(helper.path, helper);
                    if (null != existing)
                    {
                        // and here is the whole point, pick one, probably doesn't matter that much, but pick the local one if possible
                        if (!localContainerId.equals(existing.category.getContainerId()) && localContainerId.equals(helper.category.getContainerId()))
                            mapPathHelper.put(helper.path, helper);
                        else if (helper.compareTo(existing) < 0)   // try to be a little deterministic, choose the 'first' one
                            mapPathHelper.put(helper.path, helper);
                    }
                }
            }

            // virtual map RowId->Helper
            // if collapseByLabel==true use mapPathHelper indirection so ViewCategory objects with the sample path map to the same CategoryHelper
            // else if collapseByLabel==false just use simple map by rowid so each distinct ViewCategory has its own CategoryHelper
            Function<Integer, CategoryHelper> mapRowIdHelper = collapseByLabel ?
                    (i) -> mapPathHelper.get(mapRowidPath.get(i))   :
                    (i) -> categories.get(i);

            // add views to their assigned categories
            for (DataViewInfo view : views)
            {
                CategoryHelper h = mapRowIdHelper.apply(view.getCategory().getRowId());
                h.views.add(view);
            }

            // construct tree by adding categories as children to their parents
            CategoryHelper rootHelper = new CategoryHelper(null, Path.rootPath);
            for (CategoryHelper helper : categories.values())
            {
                CategoryHelper parent = mapRowIdHelper.apply(helper.category.getParent());
                if (null == parent)
                    parent = rootHelper;
                parent.children.add(helper);
            }

            // Construct response
            JSONObject root = new JSONObject();
            JSONObject tree = convertTree(rootHelper);
            JSONArray children = (JSONArray)tree.get("children");
            root.put("name", ".");
            root.put("expanded", true);
            root.put("children", children);
            return root;
        }

        private JSONObject convertTree(CategoryHelper helper) // ViewCategory vc, Map<Integer, List<DataViewInfo>> groups, Map<Integer, TreeSet<ViewCategory>> tree)
        {
            // generate category JSON
            JSONObject category = new JSONObject();
            category.put("icon", false);
            category.put("expanded", true);
            category.put("cls", "dvcategory");
            if (null != helper.category)
            {
                category.put("name", helper.category.getLabel());
                category.put("rowId", helper.category.getRowId());
            }

            // process sub-categories
            JSONArray children = new JSONArray();
            for (CategoryHelper h : helper.children)
                children.put(convertTree(h));

            // process views
            ArrayList<JSONObject> views = new ArrayList<>();
            for (DataViewInfo view : helper.views)
            {
                JSONObject viewJson = DataViewService.get().toJSON(getContainer(), getUser(), view);
                viewJson.put("name", view.getName());
                viewJson.put("leaf", true);
                viewJson.put("icon", view.getIconUrl().getLocalURIString());
                viewJson.put("iconCls", view.getIconCls());
                views.add(viewJson);
            }
            Comparator<JSONObject>naturalOrderComparator = (JSONObject a, JSONObject b) ->
                    SortHelpers.compareNatural(a.get("name"), b.get("name"));
            if(_sortOrder == SortOrder.ALPHABETICAL)
                views.sort(naturalOrderComparator);

            for (JSONObject view : views)
                children.put(view);

            category.put("children", children);
            return category;
        }

        private List<DataViewProvider.Type> getVisibleDataTypes(BrowseDataForm form)
        {
            List<DataViewProvider.Type> visibleDataTypes = new ArrayList<>();
            Map<String, String> props;
            Portal.WebPart webPart = getWebPart(form);

            if (null != webPart)
                props = webPart.getPropertyMap();
            else if (form.isManageView())
                props = getAdminConfiguration();
            else
                props = resolveJSONProperties(form.getProps());

            for (DataViewProvider.Type type : DataViewService.get().getDataTypes(getContainer(), getUser()))
            {
                boolean visible = getCheckedState(type.getName(), props, type.isShowByDefault());

                if (visible)
                    visibleDataTypes.add(type);
            }

            return visibleDataTypes;
        }

        @Nullable
        private Portal.WebPart getWebPart(BrowseDataForm form)
        {
            return Portal.getPart(getContainer(), form.getPageId(), form.getIndex());
        }

        private Boolean getCheckedState(String prop, Map<String, String> propMap, boolean defaultState)
        {
            if (propMap.containsKey(prop))
            {
                return !propMap.get(prop).equals("0");
            }
            return defaultState;
        }

        private Map<String, String> resolveJSONProperties(Map<String, Object> formProps)
        {
            JSONObject jsonProps = (JSONObject) formProps;
            Map<String, String> props = new HashMap<>();
            boolean explicit = false;

            if (null != jsonProps && jsonProps.size() > 0)
            {
                try
                {
                    // Data Types Filter
                    JSONArray dataTypes = jsonProps.getJSONArray("dataTypes");
                    for (int i=0; i < dataTypes.length(); i++)
                    {
                        props.put((String) dataTypes.get(i), "on");
                        explicit = true;
                    }
                }
                catch (JSONException x)
                {
                    /* No-op */
                }

                if (explicit)
                {
                    for (ViewInfo.DataType t : ViewInfo.DataType.values())
                    {
                        if (!props.containsKey(t.name()))
                        {
                            props.put(t.name(), "0");
                        }
                    }
                }
            }

            return props;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class GetCategoriesAction extends ReadOnlyApiAction<BrowseDataForm>
    {
        @Override
        public ApiResponse execute(BrowseDataForm form, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            List<JSONObject> categoryList = new ArrayList<>();

            List<ViewCategory> categoriesWithDisplayOrder = new ArrayList<>();
            List<ViewCategory> categoriesWithoutDisplayOrder = new ArrayList<>();

            List<ViewCategory> categories;
            int parent = form.getParent();

            switch (parent)
            {
                case -2:  // Return ALL categories (top-level and subcategories)
                    categories = ViewCategoryManager.getInstance().getAllCategories(getContainer());
                    break;
                case -1:  // Return just the top-level categories
                    categories = ViewCategoryManager.getInstance().getTopLevelCategories(getContainer());
                    break;
                case 0:   // Nothing
                    categories = Collections.emptyList();
                    break;
                default:
                    ViewCategory parentCategory = ViewCategoryManager.getInstance().getCategory(getContainer(), parent);

                    if (null != parentCategory)
                        categories = parentCategory.getSubcategories();
                    else
                        categories = Collections.emptyList();
                    break;
            }

            // add dummy category for uncategorized if requested

            if(form.includeUncategorized())
            {
                // need to copy categories locally to modify
                List<ViewCategory> categoriesPlusUncategorized = new ArrayList<>(categories);

                ViewCategory uncategorizedCategory = new ViewCategory();
                uncategorizedCategory.setRowId(0);
                uncategorizedCategory.setLabel("Uncategorized");
                uncategorizedCategory.setDisplayOrder(Integer.MAX_VALUE);  // always sort after other categories
                uncategorizedCategory.setContainerId(getContainer().getId());
                categoriesPlusUncategorized.add(uncategorizedCategory);

                // retain unmodifiable property of list
                categories = Collections.unmodifiableList(categoriesPlusUncategorized);
            }

            for (ViewCategory c : categories)
            {
                if (c.getDisplayOrder() != 0)
                    categoriesWithDisplayOrder.add(c);
                else
                    categoriesWithoutDisplayOrder.add(c);
            }

            categoriesWithDisplayOrder.sort(Comparator.comparingInt(ViewCategory::getDisplayOrder));

            if (!categoriesWithoutDisplayOrder.isEmpty())
            {
                categoriesWithoutDisplayOrder.sort(Comparator.comparing(ViewCategory::getLabel, String.CASE_INSENSITIVE_ORDER));
            }
            for (ViewCategory vc : categoriesWithDisplayOrder)
                categoryList.add(vc.toJSON(getUser()));

            // assign an order to all categories returned to the client
            int count = categoriesWithDisplayOrder.size() + 1;
            for (ViewCategory vc : categoriesWithoutDisplayOrder)
            {
                vc.setDisplayOrder(count++);
                categoryList.add(vc.toJSON(getUser()));
            }
            response.put("categories", categoryList);

            return response;
        }
    }

    public static class CategoriesForm implements CustomApiForm, HasViewContext
    {
        private List<ViewCategory> _categories = new ArrayList<>();
        private ViewContext _context;

        public List<ViewCategory> getCategories()
        {
            return _categories;
        }

        public void setCategories(List<ViewCategory> categories)
        {
            _categories = categories;
        }

        @Override
        public void bindProperties(Map<String, Object> props)
        {
            Object categoriesProp = props.get("categories");
            if (categoriesProp != null)
            {
                for (JSONObject categoryInfo : ((JSONArray) categoriesProp).toJSONObjectArray())
                {
                    _categories.add(ViewCategory.fromJSON(_context.getContainer(), categoryInfo));
                }
            }
        }

        @Override
        public void setViewContext(ViewContext context)
        {
            _context = context;
        }

        @Override
        public ViewContext getViewContext()
        {
            return _context;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class SaveCategoriesAction extends MutatingApiAction<CategoriesForm>
    {
        @Override
        public ApiResponse execute(CategoriesForm form, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            DbScope scope = QueryManager.get().getDbSchema().getScope();

            try (DbScope.Transaction transaction = scope.ensureTransaction())
            {
                for (ViewCategory category : form.getCategories())
                    ViewCategoryManager.getInstance().saveCategory(getContainer(), getUser(), category);

                transaction.commit();

                response.put("success", true);
                return response;
            }
            catch (Exception e)
            {
                response.put("success", false);
                response.put("message", e.getMessage());
            }
            return response;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class DeleteCategoriesAction extends MutatingApiAction<CategoriesForm>
    {
        @Override
        public void validateForm(CategoriesForm form, Errors errors)
        {
            for (ViewCategory category : form.getCategories())
            {
                if (!category.canDelete(getContainer(), getUser()))
                    errors.reject(ERROR_MSG, "You must be an administrator to delete a view category");
            }
        }

        @Override
        public ApiResponse execute(CategoriesForm form, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            DbScope scope = QueryManager.get().getDbSchema().getScope();

            try (DbScope.Transaction transaction = scope.ensureTransaction())
            {
                for (ViewCategory category : form.getCategories())
                    ViewCategoryManager.getInstance().deleteCategory(getContainer(), getUser(), category);

                transaction.commit();

                response.put("success", true);
                return response;
            }
            catch (OptimisticConflictException oce)
            {
                errors.reject(ERROR_MSG, oce.getMessage());
            }
            return null;
        }
    }

    public static class EditViewsForm
    {
        String _id;
        String _dataType;

        public String getId()
        {
            return _id;
        }

        public void setId(String id)
        {
            _id = id;
        }

        public String getDataType()
        {
            return _dataType;
        }

        public void setDataType(String dataType)
        {
            _dataType = dataType;
        }

        public Map<String, Object> getPropertyMap(PropertyValues pv, List<String> editableValues, Map<String, MultipartFile> files) throws ValidationException
        {
            Map<String, Object> map = new HashMap<>();

            for (PropertyValue value : pv.getPropertyValues())
            {
                if (editableValues.contains(value.getName()))
                    map.put(value.getName(), value.getValue());
            }

            for (String fileName : files.keySet())
            {
                if (editableValues.contains(fileName) && !files.get(fileName).isEmpty())
                {
                    try {
                        map.put(fileName, files.get(fileName).getInputStream());
                    }
                    catch(IOException e)
                    {
                        throw new ValidationException("Unable to read file: " + fileName);
                    }
                }
            }

            return map;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class EditViewAction extends MutatingApiAction<EditViewsForm>
    {
        private DataViewProvider _provider;
        private Map<String, Object> _propertiesMap;

        public EditViewAction()
        {
            //because this will typically be called from a hidden iframe
            //we must respond with a content-type of text/html or the
            //browser will prompt the user to save the response, as the
            //browser won't natively show application/json content-type
            setContentTypeOverride("text/html");
        }

        @Override
        public void validateForm(EditViewsForm form, Errors errors)
        {
            DataViewProvider.Type type = DataViewService.get().getDataTypeByName(form.getDataType());
            if (type != null)
            {
                _provider = DataViewService.get().getProvider(type, getViewContext());
                DataViewProvider.EditInfo editInfo = _provider.getEditInfo();

                if (editInfo != null)
                {
                    List<String> editable = Arrays.asList(editInfo.getEditableProperties(getContainer(), getUser()));
                    try
                    {
                        _propertiesMap = form.getPropertyMap(getPropertyValues(), editable, getFileMap());
                        editInfo.validateProperties(getContainer(), getUser(), form.getId(), _propertiesMap);
                    }
                    catch (ValidationException e)
                    {
                        for (ValidationError error : e.getErrors())
                            errors.reject(ERROR_MSG, error.getMessage());
                    }
                }
                else
                    errors.reject(ERROR_MSG, "This data view does not support editing");
            }
            else
                errors.reject(ERROR_MSG, "Unable to find the specified data view type");
        }

        @Override
        public ApiResponse execute(EditViewsForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            DataViewProvider.EditInfo editInfo = _provider.getEditInfo();
            if (editInfo != null && _propertiesMap != null)
            {
                editInfo.updateProperties(getViewContext(), form.getId(), _propertiesMap);
                response.put("success", true);
            }
            else
                response.put("success", false);

            return response;
        }
    }

    public static class DeleteDataViewsForm implements CustomApiForm
    {
        List<Pair<String, String>> _views = new ArrayList<>();

        @Override
        public void bindProperties(Map<String, Object> props)
        {
            if (props.containsKey("views"))
            {
                Object views = props.get("views");
                if (views instanceof JSONArray)
                {
                    for (JSONObject view : ((JSONArray)views).toJSONObjectArray())
                        _views.add(new Pair<>(view.getString("dataType"), view.getString("id")));
                }
                else if (views instanceof JSONObject)
                {
                    JSONObject view = (JSONObject)views;
                    _views.add(new Pair<>(view.getString("dataType"), view.getString("id")));
                }
            }
        }

        public List<Pair<String, String>> getViews()
        {
            return _views;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class DeleteViewsAction extends MutatingApiAction<DeleteDataViewsForm>
    {
        @Override
        public void validateForm(DeleteDataViewsForm form, Errors errors)
        {
            Map<String, Boolean> validMap = new HashMap<>();

            for (Pair<String, String> view : form.getViews())
            {
                if (validMap.containsKey(view.getKey()))
                {
                    continue;
                }
                else
                {
                    DataViewProvider.Type type = DataViewService.get().getDataTypeByName(view.getKey());
                    if (type != null)
                    {
                        DataViewProvider provider = DataViewService.get().getProvider(type, getViewContext());
                        DataViewProvider.EditInfo editInfo = provider.getEditInfo();

                        if (editInfo != null)
                        {
                            List<DataViewProvider.EditInfo.Actions> actions = Arrays.asList(editInfo.getAllowableActions(getContainer(), getUser()));
                            if (actions.contains(DataViewProvider.EditInfo.Actions.delete))
                                validMap.put(view.getKey(), true);
                            else
                            {
                                errors.reject(ERROR_MSG, "This data view does not support deletes");
                                return;
                            }
                        }
                        else
                        {
                            errors.reject(ERROR_MSG, "This data view does not support deletes");
                            return;
                        }
                    }
                    else
                    {
                        errors.reject(ERROR_MSG, "Unable to find the specified data view type");
                        return;
                    }
                }
            }
        }

        @Override
        public ApiResponse execute(DeleteDataViewsForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            DbScope scope = QueryManager.get().getDbSchema().getScope();

            try (DbScope.Transaction transaction = scope.ensureTransaction())
            {
                for (Pair<String, String> view : form.getViews())
                {
                    DataViewProvider.Type type = DataViewService.get().getDataTypeByName(view.getKey());
                    if (type != null)
                    {
                        DataViewProvider provider = DataViewService.get().getProvider(type, getViewContext());
                        DataViewProvider.EditInfo editInfo = provider.getEditInfo();

                        if (editInfo != null)
                        {
                            editInfo.deleteView(getContainer(), getUser(), view.getValue());
                        }
                    }
                }
                transaction.commit();
            }
            response.put("success", true);
            return response;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class GetReportAction extends ReadOnlyApiAction<ReportForm>
    {
        @Override
        public ApiResponse execute(ReportForm form, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            Report report = null;
            if (form.getReportId() != null)
                report = form.getReportId().getReport(getViewContext());

            if (report != null)
            {
                response.put("reportConfig", report.serialize(getContainer(), getUser()));
                response.put("success", true);
            }
            else
                throw new NotFoundException("Unable to find specified report");

            return response;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ManageNotificationsAction extends SimpleViewAction<NotificationsForm>
    {
        private SortedSet<Integer> _subscriptionSet;
        private ReportContentEmailManager.NotifyOption _notifyOption;

        @Override
        public ModelAndView getView(NotificationsForm form, BindException errors)
        {
            _subscriptionSet = ReportContentEmailManager.getSubscriptionSet(getContainer(), getUser());
            String notifyOptionStr = ReportContentEmailManager.removeNotifyOption(_subscriptionSet).name().toLowerCase();
            _notifyOption = ReportContentEmailManager.NotifyOption.getNotifyOption(notifyOptionStr);
            List<Map<String, Object>> categories = new ArrayList<>();
            List<Map<String, Object>> datasets = new ArrayList<>();

            // build the list of categories assuming a max of 2 category levels
            ViewCategoryManager mgr = ViewCategoryManager.getInstance();
            List<ViewCategory> viewCategories = new ArrayList<>(mgr.getTopLevelCategories(getContainer()));
            ViewCategoryManager.sortViewCategories(viewCategories);

            ViewCategory uncategorizedCategory = ReportUtil.getDefaultCategory(getContainer(), null, null);
            categories.add(Map.of("label", uncategorizedCategory.getLabel(),
                    "rowid", UNCATEGORIZED_ROWID,
                    "subscribed", getSubscribed(ReportContentEmailManager.NotifyOption.CATEGORY, UNCATEGORIZED_ROWID)));

            for (ViewCategory vc : viewCategories)
            {
                categories.add(Map.of("label", vc.getLabel(),
                        "rowid", vc.getRowId(),
                        "subscribed", getSubscribed(ReportContentEmailManager.NotifyOption.CATEGORY, vc.getRowId())));

                List<ViewCategory> children = new ArrayList<>(vc.getSubcategories());
                ViewCategoryManager.sortViewCategories(children);
                for (ViewCategory child : children)
                {
                    categories.add(Map.of("label", "&nbsp;&nbsp;&nbsp;&nbsp;" + child.getLabel(),
                            "rowid", child.getRowId(),
                            "subscribed", getSubscribed(ReportContentEmailManager.NotifyOption.CATEGORY, child.getRowId())));
                }
            }

            // build the list of datasets
            StudyService studyService = StudyService.get();
            if (studyService != null)
            {
                Study study = studyService.getStudy(getContainer());
                if (study != null)
                {
                    List<? extends Dataset> datasetList = new ArrayList<>(study.getDatasets());
                    datasetList.sort((d1, d2) -> d1.getLabel().compareToIgnoreCase(d2.getLabel()));

                    for (Dataset ds : datasetList)
                    {
                        datasets.add(Map.of("label", ds.getLabel(),
                                "rowid", ds.getDatasetId(),
                                "subscribed", getSubscribed(ReportContentEmailManager.NotifyOption.DATASET, ds.getDatasetId())));
                    }
                }
            }

            form.setNotifyOption(notifyOptionStr);
            form.setCategories(categories);
            form.setDatasets(datasets);
            return new JspView<>("/org/labkey/query/reports/view/manageNotifications.jsp", form, errors);
        }

        private boolean getSubscribed(ReportContentEmailManager.NotifyOption notifyOption, int id)
        {
            if (notifyOption == _notifyOption)
            {
                return _subscriptionSet.contains(id);
            }
            return false;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("manageViews#notifications");
            root.addChild("Manage Study Notifications");
        }
    }

    public static class NotificationsForm extends ViewForm
    {
        private String _notifyOption;                   // the type of notification all, none, categories etc.
        private SortedSet<Integer> _selections;         // the specific categories or datasets selected.
        private List<Map<String, Object>> _categories = new ArrayList<>();
        private List<Map<String, Object>> _datasets = new ArrayList<>();

        public String getNotifyOption()
        {
            return _notifyOption;
        }

        public void setNotifyOption(String notifyOption)
        {
            _notifyOption = notifyOption;
        }

        public SortedSet<Integer> getSelections()
        {
            return _selections;
        }

        public void setSelections(SortedSet<Integer> selections)
        {
            _selections = selections;
        }

        public List<Map<String, Object>> getCategories()
        {
            return _categories;
        }

        public void setCategories(List<Map<String, Object>> categories)
        {
            _categories = categories;
        }

        public List<Map<String, Object>> getDatasets()
        {
            return _datasets;
        }

        public void setDatasets(List<Map<String, Object>> datasets)
        {
            _datasets = datasets;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class SaveNotificationSettingsAction extends MutatingApiAction<NotificationsForm>
    {
        @Override
        public ApiResponse execute(NotificationsForm form, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            Set<Integer> selections = form.getSelections();
            ReportContentEmailManager.NotifyOption notifyOption = ReportContentEmailManager.NotifyOption.getNotifyOption(form.getNotifyOption());
            selections.add(notifyOption.getSpecialCategoryId());

            ReportContentEmailManager.setSubscriptionSet(getContainer(), getUser(), selections);
            response.put("success", true);
            return response;
        }
    }

    // Used for testing the daily digest email notifications
    @Marshal(Marshaller.Jackson)
    @RequiresSiteAdmin
    public class SendDailyDigestAction extends MutatingApiAction
    {
        @Override
        public Object execute(Object o, BindException errors) throws Exception
        {
            // Spawn a new thread so the digest creation doesn't have the Spring Action context available.
            Thread digestThread = new Thread(() -> {
                // Normally, daily digest stops at previous midnight; override to include all messages through now
                DailyMessageDigest messageDigest = new DailyMessageDigest() {
                    @Override
                    protected Date getEndRange(Date current, Date last)
                    {
                        return current;
                    }
                };

                messageDigest.addProvider(ReportAndDatasetChangeDigestProvider.get());

                try
                {
                    messageDigest.sendMessageDigest();
                }
                catch (Exception e)
                {
                    ExceptionUtil.logExceptionToMothership(null, e);
                }
            });
            digestThread.start();

            return success("Reports daily digest sent");
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class UpdateReportDisplayOrderAction extends MutatingApiAction<ReportsForm>
    {
        @Override
        public ApiResponse execute(ReportsForm form, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            DbScope scope = QueryManager.get().getDbSchema().getScope();

            try (DbScope.Transaction transaction = scope.ensureTransaction())
            {
                int displayOrder = 1;
                for (Report report : form.getReports())
                {
                    ReportService.get().setReportDisplayOrder(getViewContext(), report, displayOrder++);
                }
                transaction.commit();

                response.put("success", true);
                return response;
            }
            catch (Exception e)
            {
                response.put("success", false);
                response.put("message", e.getMessage());
            }
            return response;
        }
    }

    public static class ReportsForm implements CustomApiForm, HasViewContext
    {
        private List<Report> _reports = new ArrayList<>();
        private ViewContext _context;

        public List<Report> getReports()
        {
            return _reports;
        }

        @Override
        public void bindProperties(Map<String, Object> props)
        {
            Object reportsProp = props.get("reports");
            if (reportsProp != null)
            {
                for (JSONObject reportInfo : ((JSONArray) reportsProp).toJSONObjectArray())
                {
                    ReportIdentifier reportId = AbstractReportIdentifier.fromString(reportInfo.getString("reportId"), getViewContext().getUser(), getViewContext().getContainer());
                    if (reportId != null)
                    {
                        Report report = reportId.getReport(getViewContext());

                        // for now only support reordering reports in the database
                        if (report != null && !report.getDescriptor().isModuleBased())
                        {
                            _reports.add(report);
                        }
                    }
                }
            }
        }

        @Override
        public void setViewContext(ViewContext context)
        {
            _context = context;
        }

        @Override
        public ViewContext getViewContext()
        {
            return _context;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class UpdateReportParticipantViewPropertyAction extends MutatingApiAction<ParticipantViewReportForm>
    {
        private Report _report;

        @Override
        public void validateForm(ParticipantViewReportForm form, Errors errors)
        {
            if (form.getReportId() == null)
            {
                errors.reject(ERROR_MSG, "Missing reportId parameter.");
            }
            else
            {
                _report = form.getReportId().getReport(getViewContext());
                if (_report == null)
                {
                    errors.reject(ERROR_MSG, "Report not found for reportId: " + form.getReportId());
                }
                else if (form.isShowInParticipantView() && !_report.getDescriptor().isShared())
                {
                    errors.reject(ERROR_MSG, "Unable to include a private report in the participant view.");
                }
            }
        }

        @Override
        public Object execute(ParticipantViewReportForm form, BindException errors) throws Exception
        {
            ReportDescriptor reportDescriptor = _report.getDescriptor();
            reportDescriptor.setProperty(ReportDescriptor.Prop.showInParticipantView, form.isShowInParticipantView());
            ReportService.get().saveReport(getViewContext(), reportDescriptor.getReportKey(), _report);

            ApiSimpleResponse response = new ApiSimpleResponse();
            response.put("success", true);
            response.put("reportId", form.getReportId());
            return response;
        }
    }

    public static class ParticipantViewReportForm extends ReportForm
    {
        private boolean _showInParticipantView;

        public boolean isShowInParticipantView()
        {
            return _showInParticipantView;
        }

        public void setShowInParticipantView(boolean showInParticipantView)
        {
            _showInParticipantView = showInParticipantView;
        }
    }


    public static class SerializationTest extends PipelineJob.TestSerialization
    {
        @Test
        public void testSerialization()
        {
            TestContext ctx = TestContext.get();
            ViewContext viewContext = new ViewContext();
            Container c = ContainerManager.getSharedContainer();
            viewContext.setContainer(c);
            viewContext.setUser(ctx.getUser());
            ViewBackgroundInfo info = new ViewBackgroundInfo(c, viewContext.getUser(), viewContext.getActionURL());
            PipeRoot root = PipelineService.get().findPipelineRoot(c);

            RReport report = new RReport();
            report.getDescriptor().setProperty(ScriptReportDescriptor.Prop.runInBackground, "true");
            report.getDescriptor().setProperty(ScriptReportDescriptor.Prop.knitrFormat, "r");

            RReportJob job = new RReportJob(ReportsPipelineProvider.NAME, info, report, root);
            testSerialize(job, _log);

            job = new RReportJob(ReportsPipelineProvider.NAME, info,
                    new ModuleReportIdentifier(ModuleLoader.getInstance().getModule("query"), Path.parse("/test/test.R")), root);
            testSerialize(job, _log);
        }
    }
}
