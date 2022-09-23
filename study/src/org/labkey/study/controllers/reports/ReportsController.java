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

package org.labkey.study.controllers.reports;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.Nullable;
import org.json.old.JSONArray;
import org.json.old.JSONException;
import org.json.old.JSONObject;
import org.labkey.api.action.Action;
import org.labkey.api.action.ActionType;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ExportAction;
import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.BeanViewForm;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.views.DataViewInfo;
import org.labkey.api.data.views.DataViewProvider;
import org.labkey.api.data.views.DataViewService;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationError;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.ReportIdentifier;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.reports.report.view.ReportDesignBean;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.reports.report.view.ScriptReportBean;
import org.labkey.api.security.RequiresLogin;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.MapArrayExcelWriter;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.Visit;
import org.labkey.api.study.reports.CrosstabReport;
import org.labkey.api.study.reports.CrosstabReportDescriptor;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.UniqueID;
import org.labkey.api.util.element.CsrfInput;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewForm;
import org.labkey.api.view.WebPartView;
import org.labkey.api.writer.ContainerUser;
import org.labkey.study.StudyModule;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.BaseStudyController;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.study.reports.AssayProgressReport;
import org.labkey.study.reports.ExternalReport;
import org.labkey.study.reports.ParticipantReport;
import org.labkey.study.reports.ReportManager;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReportsController extends BaseStudyController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(ReportsController.class);

    public ReportsController()
    {
        setActionResolver(_actionResolver);
    }

    protected HttpServletRequest getRequest()
    {
        return getViewContext().getRequest();
    }

    /**
     * This method represents the point of entry into the pageflow
     */
    @RequiresPermission(ReadPermission.class)
    public class BeginAction extends SimpleViewAction
    {
        private Study _study;

        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            _study = getStudy();
            return StudyModule.reportsPartFactory.getWebPartView(getViewContext(), StudyModule.reportsPartFactory.createWebPart());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            if (_study == null)
                root.addChild("No Study In Folder");
            else if (getContainer().hasPermission(getUser(), AdminPermission.class))
                root.addChild("Manage Views", urlProvider(ReportUrls.class).urlManageViews(getContainer()));
            else
                root.addChild("Views");
        }
    }

    // Need insert and developer permissions, #35215. Keep these checks in sync with the "external report" permissions checks in StudyReportUIProvider.getDesignerInfo()
    @RequiresPermission(InsertPermission.class)
    public class ExternalReportAction extends FormViewAction<ExternalReportForm>
    {
        @Override
        public void checkPermissions() throws UnauthorizedException
        {
            super.checkPermissions();

            if (!getUser().isPlatformDeveloper())
                throw new UnauthorizedException();
        }

        private ExternalReportForm _postedForm = new ExternalReportForm();

        @Override
        public ModelAndView getView(ExternalReportForm doNotUse, boolean reshow, BindException errors)
        {
            ExternalReport extReport = _postedForm.getBean();
            JspView<ExternalReportBean> designer = new JspView<>("/org/labkey/study/view/externalReportDesigner.jsp", new ExternalReportBean(getViewContext(), extReport, "Dataset"));
            HttpView resultView = extReport.renderReport(getViewContext());

            VBox v = new VBox(designer, resultView);
            v.addView(new SaveReportWidget(extReport));

            return v;
        }

        @Override
        public void validateCommand(ExternalReportForm form, Errors errors)
        {
        }

        @Override
        public boolean handlePost(ExternalReportForm form, BindException errors)
        {
            _postedForm = form;
            return true;
        }

        @Override
        public ActionURL getSuccessURL(ExternalReportForm externalReportForm)
        {
            return null;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("advancedReport");
            _addNavTrail(root, "External Report Builder");
        }
    }

    public class ExternalReportBean extends CreateQueryReportBean
    {
        private ExternalReport extReport;

        public ExternalReportBean(ViewContext context, ExternalReport extReport, String queryName)
        {
            super(context, queryName);
            this.extReport = extReport;
        }

        public ExternalReport getExtReport()
        {
            return extReport;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class StreamFileAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            String sessionKey = (String) getViewContext().get("sessionKey");
            if (null == sessionKey)
            {
                //TODO: Return a GIF that says not found??
                return null;
            }

            File file = (File) getViewContext().getRequest().getSession().getAttribute(sessionKey);
            if (file.exists())
            {
                PageFlowUtil.streamFile(getViewContext().getResponse(), file, false);
                file.delete();
            }
            return null;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }

    @RequiresLogin
    @RequiresPermission(ReadPermission.class)
    /*
      Action for non-query based views (static, xls export, advanced)
     */
    public class SaveReportAction extends FormHandlerAction<SaveReportForm>
    {
        private ActionURL _successURL;

        @Override
        public void validateCommand(SaveReportForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(SaveReportForm form, BindException errors) throws Exception
        {
            Report report = form.getReport(getViewContext());
            if (report != null)
            {
                final String key = ReportUtil.getReportQueryKey(report.getDescriptor());

                int reportId = ReportService.get().saveReport(getViewContext(), key, report);

                if (form.isRedirectToReport())
                {
                    _successURL = new ActionURL(ShowReportAction.class, getContainer()).addParameter("reportId", reportId);
                }
                else
                {
                    if (form.getShowWithDataset() != 0)
                    {
                        _successURL = getDatasetURL(reportId, form.getShowWithDataset());
                    }
                    else if (form.getRedirectToDataset() != null && !form.getRedirectToDataset().equals(-1))
                    {
                        _successURL = getDatasetURL(reportId, form.getRedirectToDataset());
                    }
                    else
                    {
                        _successURL = urlProvider(ReportUrls.class).urlManageViews(getContainer());
                    }
                }

                return true;
            }
            else
            {
                errors.reject(ERROR_MSG, "The report to save is either invalid or was not specified.");
                return false;
            }
        }

        @Override
        public URLHelper getSuccessURL(SaveReportForm saveReportForm)
        {
            return _successURL;
        }
    }

    private ActionURL getDatasetURL(int reportId, Integer dataset)
    {
        ActionURL url = getViewContext().cloneActionURL();
        url.setAction(StudyController.DatasetReportAction.class);

        url.replaceParameter(StudyController.DATASET_REPORT_ID_PARAMETER_NAME, reportId);
        url.replaceParameter(Dataset.DATASETKEY, dataset);

        return url;
    }

    @RequiresLogin
    @RequiresPermission(ReadPermission.class)
    public class SaveReportViewAction extends FormViewAction<SaveReportViewForm>
    {
        private int _savedReportId = -1;

        @Override
        public ModelAndView getView(SaveReportViewForm form, boolean reshow, BindException errors)
        {
            form.setErrors(errors);
            if (form.getReport(getViewContext()) != null)
            {
                return new JspView<>("/org/labkey/study/view/saveReportView.jsp", form);
            }
            else
            {
                return new HtmlView("<span class='labkey-error'>The report to save is either invalid or was not specified.</span>");
            }
        }

        @Override
        public void validateCommand(SaveReportViewForm form, Errors errors)
        {
            if (ReportService.get().reportNameExists(getViewContext(), form.getLabel(), ReportUtil.getReportKey(form.getSchemaName(), form.getQueryName())))
                errors.reject("saveReportView", "There is already a report with the name of: '" + form.getLabel() +
                        "'. Please specify a different name.");
        }

        @Override
        public boolean handlePost(SaveReportViewForm form, BindException errors)
        {
            Report report = form.getReport(getViewContext());
            if (report != null)
            {
                _savedReportId = ReportService.get().saveReport(getViewContext(), ReportUtil.getReportKey(form.getSchemaName(), form.getQueryName()), report);
                return true;
            }
            return false;
        }

        @Override
        public ActionURL getSuccessURL(SaveReportViewForm form)
        {
            if (!StringUtils.isBlank(form.getRedirectUrl()))
                return new ActionURL(form.getRedirectUrl());

            // after the save just redirect to the newly created view, ask the report for its run URL
            Report r = ReportService.get().getReport(getContainer(), _savedReportId);
            if (r != null)
                return r.getRunReportURL(getViewContext());
            else
                return getViewContext().cloneActionURL().deleteParameters().setAction(BeginAction.class);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ShowReportAction extends SimpleViewAction<ShowReportForm>
    {
        @Override
        public ModelAndView getView(ShowReportForm form, BindException errors) throws Exception
        {
            Report report = null;

            if (form.getReportId() != -1)
                report = ReportManager.get().getReport(getContainer(), form.getReportId());

            if (report == null)
            {
                String message = "Report " + (form.getReportId() != -1 ? form.getReportId() : form.getReportView()) + " not found";
                throw new NotFoundException(message);
            }

            return report.renderReport(getViewContext());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }

    public static class CrosstabDesignBean extends ReportDesignBean<CrosstabReport>
    {
        private Map<String, ColumnInfo> columns;
        private int _visitRowId = -1;
        private String _rowField;
        private String _colField;
        private String _statField;
        private String[] _stats = new String[0];


        public int getVisitRowId()
        {
            return _visitRowId;
        }

        public void setVisitRowId(int visitRowId)
        {
            _visitRowId = visitRowId;
        }

        public Map<String, ColumnInfo> getColumns()
        {
            return columns;
        }

        public void setColumns(Map<String, ColumnInfo> columns)
        {
            this.columns = columns;
        }

        public String getRowField()
        {
            return _rowField;
        }

        public void setRowField(String rowField)
        {
            _rowField = rowField;
        }

        public String getColField()
        {
            return _colField;
        }

        public void setColField(String colField)
        {
            _colField = colField;
        }

        public String getStatField()
        {
            return _statField;
        }

        public void setStatField(String statField)
        {
            _statField = statField;
        }

        public String[] getStats()
        {
            return _stats;
        }

        public void setStats(String[] stats)
        {
            _stats = stats;
        }

        @Override
        public CrosstabReport getReport(ContainerUser cu) throws Exception
        {
            CrosstabReport report = super.getReport(cu);
            if (null == report)
                return null;

            CrosstabReportDescriptor descriptor = (CrosstabReportDescriptor) report.getDescriptor();

            if (_visitRowId != -1) descriptor.setProperty(VisitImpl.VISITKEY, Integer.toString(_visitRowId));
            if (!StringUtils.isEmpty(_rowField)) descriptor.setProperty("rowField", _rowField);
            if (!StringUtils.isEmpty(_colField)) descriptor.setProperty("colField", _colField);
            if (!StringUtils.isEmpty(_statField)) descriptor.setProperty("statField", _statField);
            if (_stats.length > 0) descriptor.setStats(_stats);

            return report;
        }
    }

    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.SelectData.class)
    public class ParticipantCrosstabAction extends FormViewAction<CrosstabDesignBean>
    {
        @Override
        public ModelAndView getView(CrosstabDesignBean form, boolean reshow, BindException errors) throws Exception
        {
            form.setColumns(getColumns(form));

            JspView<CrosstabDesignBean> view = new JspView<>("/org/labkey/study/view/crosstabDesigner.jsp", form);
            VBox v = new VBox(view);

            if (reshow)
            {
                CrosstabReport report = form.getReport(getViewContext());
                if (report != null)
                {
                    try
                    {
                        v.addView(report.renderReport(getViewContext()));
                    }
                    catch (RuntimeException e)
                    {
                        errors.reject(ERROR_MSG, e.getMessage());
                    }

                    SaveReportViewForm bean = new SaveReportViewForm(report);
                    bean.setShareReport(true);
                    bean.setSchemaName(form.getSchemaName());
                    bean.setQueryName(form.getQueryName());
                    bean.setDataRegionName(form.getDataRegionName());
                    bean.setViewName(form.getViewName());
                    bean.setRedirectUrl(form.getRedirectUrl());
                    bean.setErrors(errors);

                    if (!getUser().isGuest())
                    {
                        JspView<SaveReportViewForm> saveWidget = new JspView<>("/org/labkey/study/view/saveReportView.jsp", bean);
                        v.addView(saveWidget);
                    }
                }
            }
            return v;
        }

        private Map<String, ColumnInfo> getColumns(CrosstabDesignBean form)
        {
            UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), form.getSchemaName());
            Map<String, ColumnInfo> colMap = new CaseInsensitiveHashMap<>();

            if (schema != null)
            {
                QuerySettings settings = schema.getSettings(form.getViewContext(), "Dataset", form.getQueryName());

                QueryView qv = schema.createView(getViewContext(), settings);
                List<DisplayColumn> cols = qv.getDisplayColumns();
                for (DisplayColumn col : cols)
                {
                    ColumnInfo colInfo = col.getColumnInfo();
                    if (colInfo != null)
                        colMap.put(colInfo.getAlias(), colInfo);
                }
            }
            return colMap;
        }

        @Override
        public boolean handlePost(CrosstabDesignBean crosstabDesignBean, BindException errors)
        {
            return false;
        }

        @Override
        public void validateCommand(CrosstabDesignBean target, Errors errors)
        {
        }

        @Override
        public ActionURL getSuccessURL(CrosstabDesignBean crosstabDesignBean)
        {
            return null;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
/*
            ViewContext context = getViewContext();
            int datasetId = null == context.get(DatasetDefinition.DATASETKEY) ? 0 : Integer.parseInt((String) context.get(DatasetDefinition.DATASETKEY));
            int visitRowId = null == context.get("visitRowId") ? 0 : Integer.parseInt((String) context.get("visitRowId"));

            return _appendNavTrail(root, "Crosstab View Builder", datasetId, visitRowId);
*/
            setHelpTopic("crosstabReports");
            root.addChild("Crosstab Report Builder");
        }
    }

    public static class ExportForm
    {
        private int locationId = 0;
        private ReportIdentifier reportId;

        public int getLocationId()
        {
            return locationId;
        }

        public void setLocationId(int locationId)
        {
            this.locationId = locationId;
        }

        public ReportIdentifier getReportId()
        {
            return reportId;
        }

        public void setReportId(ReportIdentifier reportId)
        {
            this.reportId = reportId;
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class CreateQueryReportAction extends SimpleViewAction<QueryReportForm>
    {
        @Override
        public ModelAndView getView(QueryReportForm form, BindException errors)
        {
            setHelpTopic("datasetViews");
            return new JspView<>("/org/labkey/study/view/createQueryReport.jsp",
                    new CreateQueryReportBean(getViewContext(), form.getQueryName()));
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            _addNavTrail(root, "Create Grid View");
        }
    }

    @RequiresNoPermission
    public class CreateCrosstabReportAction extends SimpleViewAction
    {

        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            setHelpTopic("crosstabReports");
            return new JspView<>("/org/labkey/study/view/createCrosstabReport.jsp",
                    new CreateCrosstabBean(getViewContext()));
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            _addNavTrail(root, "Create Crosstab Report");
        }
    }

    public static class QueryReportForm
    {
        private String _queryName;

        public String getQueryName()
        {
            return _queryName;
        }

        public void setQueryName(String queryName)
        {
            _queryName = queryName;
        }
    }

    public static class CreateCrosstabBean
    {
        private List<DatasetDefinition> _datasets;
        private List<VisitImpl> _visits;

        public CreateCrosstabBean(ViewContext context) throws IllegalStateException
        {
            Study study = getStudyThrowIfNull(context.getContainer());
            _datasets = StudyManager.getInstance().getDatasetDefinitions(study);
            _visits = StudyManager.getInstance().getVisits(study, Visit.Order.DISPLAY);
        }

        public List<DatasetDefinition> getDatasets()
        {
            return _datasets;
        }

        public List<VisitImpl> getVisits()
        {
            return _visits;
        }
    }

    public static class CreateQueryReportBean
    {
        private List<String> _tableAndQueryNames;
        private Container _container;
        private User _user;
        private String _queryName;
        private ActionURL _returnUrl;
        private Map<String, DatasetDefinition> _datasetMap;

        public CreateQueryReportBean(ViewContext context, String queryName) throws IllegalStateException
        {
            _tableAndQueryNames = getTableAndQueryNames(context);
            _container = context.getContainer();
            _user = context.getUser();
            _queryName = queryName;
            _returnUrl = context.getActionURL();
        }

        private List<String> getTableAndQueryNames(ViewContext context) throws IllegalStateException
        {
            StudyImpl study = getStudyThrowIfNull(context.getContainer());
            StudyQuerySchema studySchema = StudyQuerySchema.createSchema(study, context.getUser());
            return studySchema.getTableAndQueryNames(true);
        }

        public List<String> getTableAndQueryNames()
        {
            return _tableAndQueryNames;
        }

        public Map<String, DatasetDefinition> getDatasetDefinitions() throws IllegalStateException
        {
            if (_datasetMap == null)
            {
                _datasetMap = new HashMap<>();
                final Study study = getStudyThrowIfNull(_container);

                for (DatasetDefinition def : StudyManager.getInstance().getDatasetDefinitions(study))
                {
                    _datasetMap.put(def.getName(), def);
                }
            }
            return _datasetMap;
        }

        public ActionURL getQueryCustomizeURL()
        {
            return QueryService.get().urlQueryDesigner(_user, _container,
                    StudySchema.getInstance().getSchema().getName());
        }

        public String getQueryName()
        {
            return _queryName;
        }

        public ActionURL getReturnUrl()
        {
            return _returnUrl;
        }
    }

    public static class ShowReportForm
    {
        private int reportId = -1;
        private String _reportView;

        public int getReportId()
        {
            return reportId;
        }

        public void setReportId(int reportId)
        {
            this.reportId = reportId;
        }

        public void setReportView(String label){_reportView = label;}
        public String getReportView(){return _reportView;}
    }


    public static class SaveReportWidget extends HttpView
    {
        private final Report _report;
        private final boolean _confirm;
        private final ActionURL _returnUrl;
        private final boolean _redirToReport;

        public SaveReportWidget(Report report)
        {
            this(report, false, null, false);
        }

        public SaveReportWidget(Report report, boolean confirm, ActionURL returnUrl, boolean redirToReport)
        {
            _report = report;
            _confirm = confirm;
            _returnUrl = returnUrl;
            _redirToReport = redirToReport;
        }

        @Override
        protected void renderInternal(Object model, PrintWriter out)
        {
            out.write("<form method='post' name='saveReport' action='");
            out.write(PageFlowUtil.filter(new ActionURL(SaveReportViewAction.class, getViewContext().getContainer())));
            out.write("'>");
            out.write("<table><tr>");
            if (_confirm)
            {
                out.write("<td>");
                out.write("There is already a report called: <i>");
                out.write(PageFlowUtil.filter(_report.getDescriptor().getReportName()));
                out.write("</i>.<br/>Overwrite the existing report?");
                out.write("<input type=hidden name=confirmed value=1>");
                out.write("<input type=hidden name=label value='");
            }
            else
            {
                out.write("<td><b>Save Report&nbsp;</b> Name:&nbsp;");
                out.write("<input name='label' value='");
            }
            out.write(PageFlowUtil.filter(_report.getDescriptor().getReportName()));
            out.write("'></td>");
            out.write("<td>");
            if (!_confirm)
            {
                out.write(ReturnUrlForm.generateHiddenFormField(getViewContext().getActionURL()).toString());
            }
            out.write("<input type=hidden name=redirectToReport value='");
            out.write(Boolean.toString(_redirToReport));
            out.write("'>");
            out.write("<input type=hidden name=reportType value='");
            out.write(_report.getDescriptor().getReportType());
            out.write("'>");
            out.write("<input type=hidden name=params value='");
            out.write(PageFlowUtil.filter(_report.getDescriptor().toQueryString()));
            out.write("'></td>");

            Container c = getViewContext().getContainer();
            Study study = getStudyThrowIfNull(c);
            List<DatasetDefinition> defs = StudyManager.getInstance().getDatasetDefinitions(study);
            out.write("<td>Add as Custom Report For: ");
            out.write("<select name=\"showWithDataset\">");
            //out.write("<option value=\"0\">Views and Reports Web Part</option>");
            int showWithDataset = NumberUtils.toInt(_report.getDescriptor().getProperty("showWithDataset"));
            for (Dataset def : defs)
            {
                out.write("<option ");
                if (def.getDatasetId() == showWithDataset)
                    out.write(" selected ");
                out.write("value=\"");
                out.write(String.valueOf(def.getDatasetId()));
                out.write("\">");
                out.write(PageFlowUtil.filter(def.getLabel()));
                out.write("</option>");
            }
            out.write("</select></td>");

            out.write("<td>" + PageFlowUtil.button("Save").submit(true));
            out.write(new CsrfInput(getViewContext()).toString());
            out.write("</form>");

            if (_confirm)
            {
                out.write("&nbsp;" + PageFlowUtil.button("Cancel").href(_returnUrl));
            }
            out.write("</td></tr></table>");
        }
    }

    public static class ExternalReportForm extends BeanViewForm<ExternalReport>
    {
        public ExternalReportForm()
        {
            super(ExternalReport.class);
        }
    }

    public static class SaveReportForm extends ViewForm
    {
        protected String label;
        protected String params;
        protected String reportType;
        protected String confirmed;
        protected int showWithDataset;
        protected boolean redirectToReport;
        protected Integer redirectToDataset;
        protected String description;
        protected String dataRegionName;
        private BindException _errors;

        public SaveReportForm()
        {
        }

        public SaveReportForm(Report report)
        {
            this.label = report.getDescriptor().getReportName();
            this.params = report.getDescriptor().toQueryString();
            this.reportType = report.getDescriptor().getReportType();
            this.description = report.getDescriptor().getReportDescription();
        }

        public String getLabel()
        {
            return label;
        }

        public void setLabel(String label)
        {
            this.label = label;
        }

        public String getParams()
        {
            return params;
        }

        public void setParams(String params)
        {
            this.params = params;
        }

        public String getReportType()
        {
            return reportType;
        }

        public void setReportType(String reportType)
        {
            this.reportType = reportType;
        }

        @Nullable
        public Report getReport(ContainerUser cu)
        {
            Report report = ReportManager.get().createReport(reportType);
            if (report != null)
            {
                ReportDescriptor descriptor = report.getDescriptor();
                descriptor.setReportName(label);
                descriptor.initFromQueryString(params);
                descriptor.setProperty("showWithDataset", String.valueOf(showWithDataset));
                descriptor.setReportDescription(description);
                descriptor.setProperty(ReportDescriptor.Prop.dataRegionName, dataRegionName);

                return report;
            }
            return null;
        }

        public String getConfirmed()
        {
            return confirmed;
        }

        public void setConfirmed(String confirmed)
        {
            this.confirmed = confirmed;
        }

        public boolean isRedirectToReport()
        {
            return redirectToReport;
        }

        public void setRedirectToReport(boolean redirectToReport)
        {
            this.redirectToReport = redirectToReport;
        }

        public int getShowWithDataset()
        {
            return showWithDataset;
        }

        public void setShowWithDataset(int showWithDataset)
        {
            this.showWithDataset = showWithDataset;
        }

        public void setRedirectToDataset(Integer dataset){redirectToDataset = dataset;}
        public Integer getRedirectToDataset(){return redirectToDataset;}

        public void setDescription(String description){this.description = description;}
        public String getDescription(){return this.description;}
        public void setErrors(BindException errors){_errors = errors;}
        public BindException getErrors(){return _errors;}

        public String getDataRegionName()
        {
            return dataRegionName;
        }

        public void setDataRegionName(String dataRegionName)
        {
            this.dataRegionName = dataRegionName;
        }
    }

    public static class SaveReportViewForm extends SaveReportForm
    {
        private boolean _shareReport;
        private String _queryName;
        private String _schemaName;
        private String _viewName;
        private String _dataRegionName;
        private String _redirectUrl;

        public SaveReportViewForm()
        {
        }

        public SaveReportViewForm(Report report)
        {
            label = report.getDescriptor().getReportName();
            params = report.getDescriptor().toQueryString();
            reportType = report.getDescriptor().getReportType();
        }

        @Override
        @Nullable
        public Report getReport(ContainerUser cu)
        {
            Report report = super.getReport(cu);
            if (report != null)
            {
                ReportDescriptor descriptor = report.getDescriptor();

                if (!StringUtils.isEmpty(getSchemaName()))
                    descriptor.setProperty(QueryParam.schemaName.toString(), getSchemaName());
                if (!StringUtils.isEmpty(getQueryName()))
                    descriptor.setProperty(QueryParam.queryName.toString(), getQueryName());
                if (!StringUtils.isEmpty(getViewName()))
                    descriptor.setProperty(QueryParam.viewName.toString(), getViewName());
                if (!StringUtils.isEmpty(getDataRegionName()))
                    descriptor.setProperty(QueryParam.dataRegionName.toString(), getDataRegionName());
                if (!getShareReport())
                    descriptor.setOwner(getViewContext().getUser().getUserId());

                return report;
            }
            return null;
        }

        public void setShareReport(boolean shareReport){_shareReport = shareReport;}
        public boolean getShareReport(){return _shareReport;}

        public void setSchemaName(String schemaName){_schemaName = schemaName;}
        public String getSchemaName(){return _schemaName;}
        public void setQueryName(String queryName){_queryName = queryName;}
        public String getQueryName(){return _queryName;}
        public void setViewName(String viewName){_viewName = viewName;}
        public String getViewName(){return _viewName;}
        @Override
        public void setDataRegionName(String dataRegionName){_dataRegionName = dataRegionName;}
        @Override
        public String getDataRegionName(){return _dataRegionName;}

        public String getRedirectUrl()
        {
            return _redirectUrl;
        }

        public void setRedirectUrl(String redirectUrl)
        {
            _redirectUrl = redirectUrl;
        }
    }

    public static HttpView getParticipantNavTrail(ViewContext context, List<String> participantGroup)
    {
        String participantId = context.getActionURL().getParameter("participantId");

        ActionURL previousParticipantURL = null;
        ActionURL nextParticipantURL = null;
        String title = null;

        if (!participantGroup.isEmpty())
        {
            if (participantId == null || !participantGroup.contains(participantId))
            {
                participantId = participantGroup.get(0);
                context.put("participantId", participantId);
            }
            int idx = participantGroup.indexOf(participantId);
            if (idx != -1)
            {
                title = StudyService.get().getSubjectNounSingular(context.getContainer()) + " : " + participantId;

                if (idx > 0)
                {
                    final String ptid = participantGroup.get(idx - 1);
                    nextParticipantURL = context.cloneActionURL();
                    nextParticipantURL.replaceParameter("participantId", ptid);
                }

                if (idx < participantGroup.size() - 1)
                {
                    final String ptid = participantGroup.get(idx + 1);
                    previousParticipantURL = context.cloneActionURL();
                    previousParticipantURL.replaceParameter("participantId", ptid);
                }
            }
        }
        StudyController.ParticipantNavView view = new StudyController.ParticipantNavView(previousParticipantURL, nextParticipantURL, null, title);
        view.setShowCustomizeLink(false);

        return view;
    }

    @RequiresPermission(ReadPermission.class)
    public class RunRReportAction extends SimpleViewAction<ScriptReportBean>
    {
        protected Report _report;
        protected Dataset _def;

        protected Report getReport(ScriptReportBean form)
        {
            String reportIdParam = form.getViewContext().getActionURL().getParameter(StudyController.DATASET_REPORT_ID_PARAMETER_NAME);
            ReportIdentifier reportId = ReportService.get().getReportIdentifier(reportIdParam, getViewContext().getUser(), getViewContext().getContainer());
            if (null != reportId)
            {
                form.setReportId(reportId);
                return reportId.getReport(getViewContext());
            }
            return null;
        }

        @Override
        public ModelAndView getView(ScriptReportBean form, BindException errors) throws Exception
        {
            _report = getReport(form);
            if (_report == null)
                return new HtmlView("Unable to locate the specified report");

            Dataset def = getDatasetDefinition();
            if (def != null && _report != null)
            {
                ActionURL url = getViewContext().cloneActionURL().setAction(StudyController.DatasetAction.class).
                        replaceParameter(StudyController.DATASET_REPORT_ID_PARAMETER_NAME, _report.getDescriptor().getReportId().toString()).
                        replaceParameter(Dataset.DATASETKEY, def.getDatasetId());

                return HttpView.redirect(url);
            }

            if (ReportManager.get().canReadReport(getUser(), getContainer(), _report))
                return _report.getRunReportView(getViewContext());
            else
                return new HtmlView("User does not have read permission on this report.");
        }

        protected Dataset getDatasetDefinition()
        {
            if (_def == null && _report != null)
            {
                final Study study = getStudy(getContainer());
                if (study != null)
                {
                    _def = StudyManager.getInstance().
                            getDatasetDefinitionByQueryName(study, _report.getDescriptor().getProperty(ReportDescriptor.Prop.queryName));
                }
            }
            return _def;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            Dataset def = getDatasetDefinition();

            if (def != null)
                _addNavTrail(root, def.getDatasetId(), 0, null);
        }
    }

    private void _addNavTrail(NavTree root, String name)
    {
        try
        {
            addRootNavTrail(root);

            if (getContainer().hasPermission(getUser(), AdminPermission.class))
                root.addChild("Manage Views", urlProvider(ReportUrls.class).urlManageViews(getContainer()));
        }
        catch (Exception e)
        {
        }
        root.addChild(name);
    }

    private void _addNavTrail(NavTree root, String name, int datasetId, int visitRowId)
    {
        try
        {
            Study study = addRootNavTrail(root);

            if (getContainer().hasPermission(getUser(), AdminPermission.class))
                root.addChild("Manage Views", urlProvider(ReportUrls.class).urlManageViews(getContainer()));

            VisitImpl visit = null;

            if (visitRowId > 0)
                visit = StudyManager.getInstance().getVisitForRowId(study, visitRowId);

            if (datasetId > 0)
            {
                Dataset dataset = StudyManager.getInstance().getDatasetDefinition(study, datasetId);

                if (dataset != null)
                {
                    String label = dataset.getLabel() != null ? dataset.getLabel() : "" + dataset.getDatasetId();

                    if (0 == visitRowId && study.getTimepointType() != TimepointType.CONTINUOUS)
                        label += " (All Visits)";

                    ActionURL datasetUrl = getViewContext().getActionURL().clone();
                    datasetUrl.deleteParameter(VisitImpl.VISITKEY);
                    datasetUrl.setAction(StudyController.DatasetAction.class);
                    root.addChild(label, datasetUrl.getLocalURIString());
                }
            }

            if (null != visit)
                root.addChild(visit.getDisplayString(), getViewContext().getActionURL().clone().setAction(StudyController.DatasetAction.class));
        }
        catch (Exception e)
        {
        }
        root.addChild(name);
    }

    public static class ReportsWebPart extends JspView<Object>
    {
        public ReportsWebPart(boolean isWide)
        {
            super("/org/labkey/study/view/manageReports.jsp");
            setTitle("Views");

            StudyManageReportsBean bean = new StudyManageReportsBean();
            bean.setAdminView(false);
            bean.setWideView(isWide);

            setModelBean(bean);
        }

        public void setAdminMode(boolean mode)
        {
            Object model = getModelBean();
            if (model instanceof StudyManageReportsBean)
                ((StudyManageReportsBean) model).setAdminView(mode);
        }
    }

    @RequiresLogin
    @RequiresPermission(ReadPermission.class)
    public class ParticipantReportAction extends SimpleViewAction<ParticipantReportForm>
    {
        @Override
        public ModelAndView getView(ParticipantReportForm form, BindException errors)
        {
            form.setComponentId("participant-report-panel-" + UniqueID.getRequestScopedUID(getRequest()));
            form.setExpanded(!(getViewContext().get("reportWebPart") != null));

            if (StudyService.get().getStudy(getContainer()) != null)
            {
                JspView<ParticipantReportForm> view = new JspView<>("/org/labkey/study/view/participantReport.jsp", form);

                view.setTitle(StudyService.get().getSubjectNounSingular(getContainer()) + " Report");
                view.setFrame(WebPartView.FrameType.PORTAL);

                String script = String.format("javascript:customizeParticipantReport('%s');", form.getComponentId());
                NavTree edit = new NavTree("Edit", script, null, "fa fa-pencil");
                view.addCustomMenu(edit);

                if (getViewContext().hasPermission(InsertPermission.class))
                {
                    NavTree menu = new NavTree();
                    menu.addChild("New " + StudyService.get().getSubjectNounSingular(getContainer()) + " Report", new ActionURL(this.getClass(), getContainer()));
                    menu.addChild("Manage Views", urlProvider(ReportUrls.class).urlManageViews(getContainer()));
                    view.setNavMenu(menu);
                }
                return view;
            }
            else
                return new HtmlView("A study does not exist in this folder");
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("participantReport");
            _addNavTrail(root, StudyService.get().getSubjectNounSingular(getContainer()) + " Report");
        }
    }

    @RequiresLogin
    @RequiresPermission(ReadPermission.class)
    public class SaveParticipantReportAction extends MutatingApiAction<ParticipantReportForm>
    {
        @Override
        public void validateForm(ParticipantReportForm form, Errors errors)
        {
            List<ValidationError> reportErrors = new ArrayList<>();

            if (form.getName() == null)
                errors.reject(ERROR_MSG, "A report name is required");

            if (form.getMeasures() == null)
                errors.reject(ERROR_MSG, "Report measures information cannot be blank");
            else
            {
                try
                {
                    JSONArray config = new JSONArray(form.getMeasures());
                }
                catch (JSONException e)
                {
                    errors.reject(ERROR_MSG, e.getMessage());
                }
            }

            try
            {
                // check for duplicates on new reports
                if (form.getReportId() == null)
                {
                    if (ReportUtil.doesReportNameExist(getContainer(), getUser(), form.getSchemaName(), form.getQueryName(), form.getName()))
                        errors.reject(ERROR_MSG, "Another report with the same name already exists.");

                    if (form.isPublic())
                    {
                        Report report = getParticipantReport(form);
                        if (!report.canShare(getUser(), getContainer(), reportErrors))
                            ReportUtil.addErrors(reportErrors, errors);
                    }
                }
                else
                {
                    Report report = form.getReportId().getReport(getViewContext());

                    if (report != null)
                    {
                        if (!report.canEdit(getUser(), getContainer(), reportErrors))
                            ReportUtil.addErrors(reportErrors, errors);

                        if (form.isPublic() && !report.canShare(getUser(), getContainer(), reportErrors))
                            ReportUtil.addErrors(reportErrors, errors);
                    }
                }
            }
            catch (Exception e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
            }
        }

        @Override
        public ApiResponse execute(ParticipantReportForm form, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            String key = ReportUtil.getReportKey(form.getSchemaName(), form.getQueryName());
            Report report = getParticipantReport(form);

            int rowId = ReportService.get().saveReport(getViewContext(), key, report);
            ReportIdentifier reportId = ReportService.get().getReportIdentifier(String.valueOf(rowId), getViewContext().getUser(), getViewContext().getContainer());

            response.put("success", true);
            response.put("reportId", reportId);

            return response;
        }

        private Report getParticipantReport(ParticipantReportForm form)
        {
            Report report;

            if (form.getReportId() != null)
                report = form.getReportId().getReport(getViewContext());
            else
                report = ReportService.get().createReportInstance(ParticipantReport.TYPE);

            if (report != null)
            {
                report = report.clone();
                ReportDescriptor descriptor = report.getDescriptor();

                if (form.getName() != null)
                    descriptor.setReportName(form.getName());
                if (form.getDescription() != null)
                    descriptor.setReportDescription(form.getDescription());
                if (form.getSchemaName() != null)
                    descriptor.setProperty(ReportDescriptor.Prop.schemaName, form.getSchemaName());
                if (form.getQueryName() != null)
                    descriptor.setProperty(ReportDescriptor.Prop.queryName, form.getQueryName());
                if (form.getMeasures() != null)
                    descriptor.setProperty(ParticipantReport.MEASURES_PROP, form.getMeasures());

                //Issue 15078: always set the groups.  null in groups indicates all participants should be displayed
                descriptor.setProperty(ParticipantReport.GROUPS_PROP, form.getGroups());

                if (!form.isPublic())
                    descriptor.setOwner(getUser().getUserId());
                else
                    descriptor.setOwner(null);
            }
            return report;
        }

    }

    public static class ParticipantReportForm extends ReportUtil.JsonReportForm
    {
        private String _measures;
        private boolean _expanded;
        private String _groups;
        private boolean _allowOverflow = true;

        public boolean isExpanded()
        {
            return _expanded;
        }

        public void setExpanded(boolean expanded)
        {
            _expanded = expanded;
        }

        public String getMeasures()
        {
            return _measures;
        }

        public void setMeasures(String measures)
        {
            _measures = measures;
        }

        public String getGroups()
        {
            return _groups;
        }

        public void setGroups(String groups)
        {
            _groups = groups;
        }

        public boolean isAllowOverflow()
        {
            return _allowOverflow;
        }

        public void setAllowOverflow(boolean allowOverflow)
        {
            _allowOverflow = allowOverflow;
        }

        @Override
        public void bindProperties(Map<String, Object> props)
        {
            super.bindProperties(props);

            Object measures = props.get(ParticipantReport.MEASURES_PROP);
            if (measures instanceof JSONArray)
            {
                _measures = measures.toString();
            }
            Object groups = props.get(ParticipantReport.GROUPS_PROP);
            if (groups instanceof JSONArray)
            {
                _groups = groups.toString();
            }
        }
    }

    public static class ProgressReportForm extends ReportUtil.JsonReportForm
    {
        private Integer _assayId;
        private String _jsonData;

        public Integer getAssayId()
        {
            return _assayId;
        }

        public void setAssayId(Integer assayId)
        {
            _assayId = assayId;
        }

        public String getJsonData()
        {
            return _jsonData;
        }

        @Override
        public void bindProperties(Map<String, Object> props)
        {
            super.bindProperties(props);

            // used for export to excel
            _assayId = (Integer) props.get("assayId");

            Object json = props.get("jsonData");
            if (json != null)
                _jsonData = json.toString();
        }
    }

    @RequiresLogin
    @RequiresPermission(ReadPermission.class)
    public class AssayProgressReportAction extends SimpleViewAction<ProgressReportForm>
    {
        private String _actionName = "Create ";

        @Override
        public ModelAndView getView(ProgressReportForm form, BindException errors)
        {
            if (form.getReportId() != null)
                _actionName = "Edit ";
            JspView<ProgressReportForm> view = new JspView<>("/org/labkey/study/view/assayProgressReport.jsp", form, errors);

            return view;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild(_actionName + AssayProgressReport.REPORT_LABEL);
        }
    }

    @RequiresLogin
    @RequiresPermission(ReadPermission.class)
    public class SaveAssayProgressReportAction extends MutatingApiAction<ProgressReportForm>
    {
        @Override
        public ApiResponse execute(ProgressReportForm form, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            String key = ReportUtil.getReportKey(StudySchema.getInstance().getSchemaName(), null);
            Report report = getReport(form);

            int rowId = ReportService.get().saveReport(getViewContext(), key, report);
            ReportIdentifier reportId = ReportService.get().getReportIdentifier(String.valueOf(rowId), getViewContext().getUser(), getViewContext().getContainer());

            response.put("success", true);
            response.put("reportId", reportId);

            return response;
        }

        private Report getReport(ProgressReportForm form)
        {
            Report report;

            if (form.getReportId() != null)
                report = form.getReportId().getReport(getViewContext());
            else
                report = ReportService.get().createReportInstance(AssayProgressReport.TYPE);

            if (report != null)
            {
                report = report.clone();
                ReportDescriptor descriptor = report.getDescriptor();

                if (form.getName() != null)
                    descriptor.setReportName(form.getName());
                if (form.getDescription() != null)
                    descriptor.setReportDescription(form.getDescription());
                if (form.getJsonData() != null)
                    descriptor.setProperty(ReportDescriptor.Prop.json, form.getJsonData());
                if (!form.isPublic())
                    descriptor.setOwner(getUser().getUserId());
                else
                    descriptor.setOwner(null);
            }
            return report;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class ExportAssayProgressReportAction extends ExportAction<ProgressReportForm>
    {
        @Override
        public void export(ProgressReportForm form, HttpServletResponse response, BindException errors)
        {
            ReportIdentifier identifier = form.getReportId();
            if (identifier != null)
            {
                Report report = identifier.getReport(getViewContext());
                if (report instanceof AssayProgressReport)
                {
                    AssayProgressReport progressReport = (AssayProgressReport)report;

                    Map<String, Object> assayData = progressReport
                            .getAssayReportData(getViewContext(), errors)
                            .get(form.getAssayId());

                    if (assayData != null)
                    {
                        List<Integer> visits = (List<Integer>)assayData.get(AssayProgressReport.VISITS);
                        List<String> visitLabels = (List<String>)assayData.get(AssayProgressReport.VISITS_LABELS);
                        List<String> participants = (List<String>)assayData.get(AssayProgressReport.PARTICIPANTS);
                        Map<String, Map<String, String>> heatMap = (Map<String, Map<String, String>>)assayData.get(AssayProgressReport.HEAT_MAP);
                        Map<Integer, String> visitLabelMap = new HashMap<>();

                        int idx = 0;
                        for (Integer visit : visits)
                        {
                            visitLabelMap.put(visit, visitLabels.get(idx++));
                        }

                        // create the columns
                        List<ColumnDescriptor> cols = new ArrayList<>();
                        cols.add(new ColumnDescriptor("ParticipantId", String.class));
                        for (String visit : visitLabels)
                        {
                            cols.add(new ColumnDescriptor(visit, String.class));
                        }

                        // populate the data
                        List<Map<String,Object>> rows = new ArrayList<>();
                        for (String ptid : participants)
                        {
                            Map<String, Object> row = new HashMap<>();

                            row.put("ParticipantId", ptid);
                            for (Integer visit : visits)
                            {
                                String key = AssayProgressReport.ParticipantVisit.getKey(ptid, visit);
                                Map<String, String> cell = heatMap.get(key);
                                if (cell != null)
                                {
                                    row.put(visitLabelMap.get(visit), cell.get("status"));
                                }
                                else
                                {
                                    // default to expected
                                    row.put(visitLabelMap.get(visit), AssayProgressReport.SPECIMEN_EXPECTED);
                                }
                            }
                            rows.add(row);
                        }

                        MapArrayExcelWriter xlWriter = new MapArrayExcelWriter(rows, cols.toArray(new ColumnDescriptor[cols.size()]));
                        xlWriter.setHeaders(Arrays.asList("#Progress Report for Assay: " + assayData.get("name"), "#"));
                        xlWriter.renderWorkbook(response);
                    }
                }
            }
        }
    }

    @RequiresLogin
    @RequiresPermission(ReadPermission.class)
    public class GetAssayReportDataAction extends ReadOnlyApiAction<ProgressReportForm>
    {
        @Override
        public ApiResponse execute(ProgressReportForm form, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            ReportIdentifier reportIdentifier = form.getReportId();
            if (reportIdentifier != null)
            {
                Report report = reportIdentifier.getReport(getViewContext());
                if (report instanceof AssayProgressReport)
                {
                    Map<Integer, Map<String, Object>> assayData = ((AssayProgressReport)report).getAssayReportData(getViewContext(), errors);
                    if (!errors.hasErrors())
                    {
                        response.put("success", true);
                        response.put("assayData", assayData);
                    }
                }
            }
            return response;
        }
    }


    @RequiresPermission(ReadPermission.class)
    public class GetReportInfosAction extends ReadOnlyApiAction<GetReportInfosForm>
    {
        @Override
        public ApiResponse execute(GetReportInfosForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            List<JSONObject> json = new ArrayList<>();

            DataViewProvider.Type type = DataViewService.get().getDataTypeByName("reports");
            if (type != null)
            {
                DataViewProvider provider = DataViewService.get().getProvider(type, getViewContext());
                if (provider != null)
                {
                    List<DataViewInfo> reports = provider.getViews(getViewContext(), form.getSchemaName(), form.getQueryName());
                    for (DataViewInfo report : reports)
                    {
                        json.add(DataViewService.get().toJSON(getContainer(), getUser(), report));
                    }
                }
            }

            response.put("schemaName", form.getSchemaName());
            response.put("queryName", form.getQueryName());
            response.put("reports", json);
            response.put("success", true);
            return response;
        }
    }

    private static class GetReportInfosForm
    {
        private String _schemaName;
        private String _queryName;

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
    }
}
