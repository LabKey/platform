/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

import org.apache.beehive.netui.pageflow.FormData;
import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.GWTServiceAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.attachments.AttachmentForm;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.*;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.query.*;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.actions.ReportForm;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.view.ChartDesignerBean;
import org.labkey.api.reports.report.view.ChartUtil;
import org.labkey.api.reports.report.view.RReportBean;
import org.labkey.api.reports.report.view.ReportDesignBean;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;
import org.labkey.common.util.Pair;
import org.labkey.study.StudyModule;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.BaseStudyController;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.Study;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.Visit;
import org.labkey.study.query.DataSetQueryView;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.study.reports.*;
import org.labkey.study.view.DataHeader;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;


public class ReportsController extends BaseStudyController
{
    static DefaultActionResolver _actionResolver = new DefaultActionResolver(ReportsController.class);
    transient Study _study = null;

    public ReportsController() throws Exception
    {
        super();
        setActionResolver(_actionResolver);
    }

    public Study getStudy() throws ServletException
    {
        return StudyManager.getInstance().getStudy(getContainer());
    }

    protected HttpServletRequest getRequest()
    {
        return getViewContext().getRequest();
    }

    /**
     * This method represents the point of entry into the pageflow
     */
    @RequiresPermission(ACL.PERM_READ)
    public class BeginAction extends SimpleViewAction
    {
        private Study _study;
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            _study = getStudy(true);
            return StudyModule.reportsPartFactory.getWebPartView(getViewContext(), null);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            if (_study == null)
                return root.addChild("No Study In Folder");
            else if (getUser().isAdministrator())
                return root.addChild("Manage Reports and Views", ActionURL.toPathString("Study-Reports", "manageReports.view", getContainer()));
            else
                return root.addChild("Reports and Views");
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class DeleteReportAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            String reportId = getRequest().getParameter(ReportDescriptor.Prop.reportId.name());
            Report report = null;

            if (reportId != null)
                report = ReportManager.get().getReport(getContainer(), NumberUtils.toInt(reportId));

            if (report != null)
            {
                ReportManager.get().deleteReport(getViewContext(), report);
            }
            String redirectUrl = getRequest().getParameter(ReportDescriptor.Prop.redirectUrl.name());
            if (redirectUrl != null)
                return HttpView.redirect(redirectUrl);
            else
                return HttpView.redirect(new ActionURL(ManageReportsAction.class, getContainer()));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class DeleteCustomQueryAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            String viewName = getRequest().getParameter("reportView");
            String defName = getRequest().getParameter("defName");
            if (viewName != null && defName != null)
            {
                final ViewContext context = getViewContext();
                final UserSchema schema = QueryService.get().getUserSchema(context.getUser(), context.getContainer(), "study");
                final Study study = StudyManager.getInstance().getStudy(context.getContainer());
                QueryDefinition qd = QueryService.get().getQueryDef(study.getContainer(), "study", defName);
                if (qd == null)
                    qd = schema.getQueryDefForTable(defName);

                if (qd != null)
                {
                    CustomView view = qd.getCustomView(context.getUser(), context.getRequest(), viewName);
                    if (view != null)
                        view.delete(context.getUser(), context.getRequest());
                }
            }
            return HttpView.redirect(new ActionURL("Study-Reports", "manageReports.view", getContainer()));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null; 
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class ManageReportsAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            setHelpTopic(new HelpTopic("manageReportsAndViews", HelpTopic.Area.STUDY));
            StudyManageReportsBean bean = new StudyManageReportsBean(getViewContext(), true, false);
            bean.setErrors(errors);

            return new StudyJspView<StudyManageReportsBean>(getStudy(), "manageReports.jsp", bean, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return _appendNavTrail(root, "Manage Reports and Views");
        }
    }

    public static class RenameReportForm
    {
        private int _reportId;
        private String _reportName;
        private String _reportDescription;

        public int getReportId(){return _reportId;}
        public void setReportId(int reportId){_reportId = reportId;}
        public String getReportName(){return _reportName;}
        public void setReportName(String reportName){_reportName = reportName;}

        public String getReportDescription()
        {
            return _reportDescription;
        }

        public void setReportDescription(String reportDescription)
        {
            _reportDescription = reportDescription;
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class RenameReportAction extends FormViewAction<RenameReportForm>
    {
        private String _newReportName;
        private Report _report;

        public void validateCommand(RenameReportForm form, Errors errors)
        {
            int reportId =  form.getReportId();
            _newReportName =  form.getReportName();
            if (!StringUtils.isEmpty(_newReportName))
            {
                try {
                    _report = ReportService.get().getReport(reportId);
                    if (_report != null)
                    {
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

        public ModelAndView getView(RenameReportForm form, boolean reshow, BindException errors) throws Exception
        {
            ManageReportsAction action = new ManageReportsAction();
            action.setViewContext(getViewContext());
            action.setPageConfig(getPageConfig());
            return action.getView(null, errors);
        }

        public boolean handlePost(RenameReportForm form, BindException errors) throws Exception
        {
            String key = _report.getDescriptor().getReportKey();
            String newKey;
            int idx = key.lastIndexOf('/');
            if (idx != -1)
            {
                newKey = key.substring(0, idx + 1) + _newReportName;
            }
            else
                newKey = _newReportName;

            if (_report instanceof StudyQueryReport)
                ((StudyQueryReport)_report).renameReport(getViewContext(), newKey, _newReportName);
            else
            {
                _report.getDescriptor().setReportName(_newReportName);
                ReportService.get().saveReport(getViewContext(), key, _report);
            }
            return true;
        }

        public ActionURL getSuccessURL(RenameReportForm form)
        {
            return new ActionURL(ManageReportsAction.class, getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class ReportDescriptionAction extends FormViewAction<RenameReportForm>
    {
        public void validateCommand(RenameReportForm target, Errors errors)
        {
        }

        public ModelAndView getView(RenameReportForm renameReportForm, boolean reshow, BindException errors) throws Exception
        {
            ManageReportsAction action = new ManageReportsAction();
            action.setViewContext(getViewContext());
            action.setPageConfig(getPageConfig());
            return action.getView(null, errors);
        }

        public boolean handlePost(RenameReportForm form, BindException errors) throws Exception
        {
            int reportId =  form.getReportId();
            String reportDescription =  form.getReportDescription();
            Report report = ReportService.get().getReport(reportId);
            if (report != null)
            {
                report.getDescriptor().setReportDescription(StringUtils.trimToNull(reportDescription));
                ReportService.get().saveReport(getViewContext(), report.getDescriptor().getReportKey(), report);
                return true;
            }
            else
            {
                errors.reject("reportDescription", "Unable to change the description for the specified report");
                return false;
            }
        }

        public ActionURL getSuccessURL(RenameReportForm renameReportForm)
        {
            return new ActionURL(ManageReportsAction.class, getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class EnrollmentReportAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            org.labkey.api.reports.Report report = EnrollmentReport.getEnrollmentReport(getUser(), getStudy(), true);
            if (report.getDescriptor().getProperty(DataSetDefinition.DATASETKEY) == null)
            {
                if (!getViewContext().hasPermission(ACL.PERM_ADMIN))
                    return new HtmlView("<font color=red>This view must be configured by an administrator.</font>");

                return HttpView.redirect(getViewContext().getActionURL().relativeUrl("configureEnrollmentReport", null));
            }
            return new EnrollmentReport.EnrollmentView(report);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return _appendNavTrail(root, "Enrollment View");
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class ConfigureEnrollmentReportAction extends FormViewAction<ColumnPickerForm>
    {
        public ModelAndView getView(ColumnPickerForm form, boolean reshow, BindException errors) throws Exception
        {
            setHelpTopic(new HelpTopic("enrollmentView", HelpTopic.Area.STUDY));
            org.labkey.api.reports.Report report = EnrollmentReport.getEnrollmentReport(getUser(), getStudy(), true);
            final ReportDescriptor descriptor = report.getDescriptor();

            if (form.getDatasetId() != null)
                descriptor.setProperty(DataSetDefinition.DATASETKEY, Integer.toString(form.getDatasetId()));
            if (form.getSequenceNum() >= 0)
                descriptor.setProperty(Visit.SEQUENCEKEY, Visit.formatSequenceNum(form.getSequenceNum()));

            if (reshow)
            {
                EnrollmentReport.saveEnrollmentReport(getViewContext(), report);
                return HttpView.redirect(getViewContext().getActionURL().relativeUrl("enrollmentReport", null));
            }

            int datasetId = NumberUtils.toInt(descriptor.getProperty(DataSetDefinition.DATASETKEY));
            double sequenceNum = NumberUtils.toDouble(descriptor.getProperty(Visit.SEQUENCEKEY));

            form.setDatasetId(datasetId);
            form.setSequenceNum(sequenceNum);

            DataPickerBean bean = new DataPickerBean(
                    getStudy(), form,
                    "Choose the form and column to use for the enrollment view.",
                    PropertyType.DATE_TIME);
            bean.pickColumn = false;
            return new GroovyView<DataPickerBean>("/org/labkey/study/view/columnPicker.gm", bean);
        }

        public void validateCommand(ColumnPickerForm target, Errors errors)
        {
        }

        public boolean handlePost(ColumnPickerForm columnPickerForm, BindException errors) throws Exception
        {
            return true;
        }

        public ActionURL getSuccessURL(ColumnPickerForm columnPickerForm)
        {
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return _appendNavTrail(root, "Customize Enrollment View");
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class RenderConfigureEnrollmentReportAction extends ConfigureEnrollmentReportAction
    {
        public ModelAndView getView(ColumnPickerForm form, boolean reshow, BindException errors) throws Exception
        {
            return super.getView(form, reshow, errors);
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class ExternalReportAction extends FormViewAction<ExternalReportForm>
    {
        public ModelAndView getView(ExternalReportForm form, boolean reshow, BindException errors) throws Exception
        {
            ExternalReport extReport = form.getBean();
            JspView<ExternalReportBean> designer = new JspView<ExternalReportBean>("/org/labkey/study/view/externalReportDesigner.jsp", new ExternalReportBean(getViewContext(), extReport, "Dataset"));
            HttpView resultView = extReport.renderReport(getViewContext());

            VBox v = new VBox(designer, resultView);

            if (getViewContext().hasPermission(ACL.PERM_ADMIN))
                v.addView(new SaveReportWidget(extReport));

            return v;
        }

        public void validateCommand(ExternalReportForm target, Errors errors)
        {
        }

        public boolean handlePost(ExternalReportForm externalReportForm, BindException errors) throws Exception
        {
            return true;
        }

        public ActionURL getSuccessURL(ExternalReportForm externalReportForm)
        {
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return _appendNavTrail(root, "External View Builder");
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

    @RequiresPermission(ACL.PERM_READ)
    public class StreamFileAction extends SimpleViewAction
    {
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

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class QueryConversionAction extends SimpleViewAction<SaveReportViewForm>
    {
        private int _reportId;

        public ModelAndView getView(SaveReportViewForm form, BindException errors) throws Exception
        {
            Report report = form.getReport();
            Report oldReport;
            final String redirect = (String)getViewContext().get("redirect");
            final String key = ChartUtil.getReportQueryKey(report.getDescriptor());

            if (!reportNameExists(getViewContext(), form.getViewName(), key))
            {
                if (report instanceof StudyQueryReport)
                {
                    _reportId = ((StudyQueryReport)report).renameReport(getViewContext(), key, form.getViewName());

                    if (!StringUtils.isEmpty(redirect))
                    {
                        ActionURL url = new ActionURL(redirect);
                        url.addParameter("reportId", _reportId);

                        return HttpView.redirect(url);
                    }
                    return HttpView.redirect(new ActionURL("Study-Reports", "manageReports.view", getContainer()));
                }
            }
            HttpView.throwUnauthorized("A report of the same name already exists");
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    /**
     * Action for non-query based views (static, xls export, advanced)
     */
    public class SaveReportAction extends SimpleViewAction<SaveReportForm>
    {
        public ModelAndView getView(SaveReportForm form, BindException errors) throws Exception
        {
            Report report = form.getReport();
            final String key = ReportManager.getReportViewKey(form.getShowWithDataset(), form.getLabel());

            int reportId = ReportService.get().saveReport(getViewContext(), key, report);

            if (form.isRedirectToReport())
                HttpView.throwRedirect("showReport.view?reportId=" + reportId);

            if (form.getShowWithDataset() != 0)
            {
                return getDatasetForward(reportId, form.getShowWithDataset());
            }
            else if (form.getRedirectToDataset() != null && !form.getRedirectToDataset().equals(-1))
            {
                return getDatasetForward(reportId, form.getRedirectToDataset());
            }
            else
                return HttpView.redirect(new ActionURL("Study-Reports", "manageReports.view", getContainer()));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }
    }

    private ModelAndView getDatasetForward(int reportId, Integer dataset) throws Exception
    {
        ActionURL url = getViewContext().cloneActionURL();
        url.setPageFlow("Study").setAction("datasetReport");

        url.replaceParameter("Dataset.viewName", String.valueOf(reportId));
        url.replaceParameter(DataSetDefinition.DATASETKEY,  String.valueOf(dataset));
        return HttpView.redirect(url);
    }

    @RequiresPermission(ACL.PERM_READ)
    public class SaveReportViewAction extends FormViewAction<SaveReportViewForm>
    {
        int _savedReportId = -1;

        public ModelAndView getView(SaveReportViewForm form, boolean reshow, BindException errors) throws Exception
        {
            form.setErrors(errors);
            return new JspView<SaveReportViewForm>("/org/labkey/study/view/saveReportView.jsp", form);
        }

        public void validateCommand(SaveReportViewForm form, Errors errors)
        {
            try {
                if (reportNameExists(getViewContext(), form.getLabel(), getReportKey(form)))
                    errors.reject("saveReportView", "There is already a report with the name of: '" + form.getLabel() +
                            "'. Please specify a different name.");
            }
            catch (ServletException e)
            {
                errors.reject("saveReportView", e.getMessage());
            }
        }

        public boolean handlePost(SaveReportViewForm form, BindException errors) throws Exception
        {
            Report report = form.getReport();
            _savedReportId = ReportService.get().saveReport(getViewContext(), getReportKey(form), report);

            return true;
        }

        private String getReportKey(SaveReportViewForm form) throws ServletException
        {
            int showWithDataset = form.getShowWithDataset();
            if (showWithDataset != 0)
            {
                if (ReportManager.ALL_DATASETS == showWithDataset)
                    return ReportManager.ALL_DATASETS_KEY;

                String queryName = null;
                DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(getStudy(), showWithDataset);
                if (def != null)
                    queryName = def.getLabel();
                return ChartUtil.getReportKey(StudyManager.getSchemaName(), queryName);
            }
            return ChartUtil.getReportQueryKey(form.getReport().getDescriptor());
        }

        public ActionURL getSuccessURL(SaveReportViewForm form)
        {
            int dataset = form.getShowWithDataset();
            if (dataset != 0)
            {
                ActionURL url = getViewContext().cloneActionURL();
                url.setPageFlow("Study").setAction("datasetReport");
                url.replaceParameter("Dataset.viewName", String.valueOf(_savedReportId));
                if (dataset == ReportManager.ALL_DATASETS)
                    url.replaceParameter(DataSetDefinition.DATASETKEY,  String.valueOf(form.getDatasetId()));
                else
                    url.replaceParameter(DataSetDefinition.DATASETKEY,  String.valueOf(form.getShowWithDataset()));
                return url;
            }
            return getViewContext().cloneActionURL().deleteParameters().setAction("begin.view");
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
    public class ShowReportAction extends SimpleViewAction<ShowReportForm>
    {
        public ModelAndView getView(ShowReportForm form, BindException errors) throws Exception
        {
            Report report = null;

            if (form.getReportId() != -1)
                report = ReportManager.get().getReport(getContainer(), form.getReportId());

            if (report == null)
                HttpView.throwNotFound("Report " + (form.getReportId() != -1 ? form.getReportId() : form.getReportView()) + " not found");
            if (!report.getDescriptor().canRead(getUser()))
                HttpView.throwUnauthorized();

            return report.renderReport(getViewContext());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    public static class CrosstabDesignBean extends ReportDesignBean
    {
        private ColumnInfo[] columns;
        private int _datasetId = -1;
        private int _visitRowId = -1;
        private String _rowField;
        private String _colField;
        private String _statField;
        private String[] _stats = new String[0];


        public int getDatasetId()
        {
            return _datasetId;
        }

        public void setDatasetId(int datasetId)
        {
            _datasetId = datasetId;
        }

        public int getVisitRowId()
        {
            return _visitRowId;
        }

        public void setVisitRowId(int visitRowId)
        {
            _visitRowId = visitRowId;
        }

        public ColumnInfo[] getColumns()
        {
            return columns;
        }

        public void setColumns(ColumnInfo[] columns)
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

        public Report getReport() throws Exception
        {
            Report report = super.getReport();
            CrosstabReportDescriptor descriptor = (CrosstabReportDescriptor)report.getDescriptor();

            if (_datasetId != -1) descriptor.setProperty(DataSetDefinition.DATASETKEY, Integer.toString(_datasetId));
            if (_visitRowId != -1) descriptor.setProperty(Visit.VISITKEY, Integer.toString(_visitRowId));
            if (!StringUtils.isEmpty(_rowField)) descriptor.setProperty("rowField", _rowField);
            if (!StringUtils.isEmpty(_colField)) descriptor.setProperty("colField", _colField);
            if (!StringUtils.isEmpty(_statField)) descriptor.setProperty("statField", _statField);
            if (_stats.length > 0) descriptor.setStats(_stats);

            return report;
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class ParticipantCrosstabAction extends FormViewAction<CrosstabDesignBean>
    {
        public ModelAndView getView(CrosstabDesignBean form, boolean reshow, BindException errors) throws Exception
        {
            if (form.getSchemaName() == null)
            {
                DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(getStudy(), form.getDatasetId());

                form.setSchemaName(StudyManager.getSchemaName());
                form.setQueryName(def.getLabel());
                String viewName = getViewContext().getActionURL().getParameter("Dataset.viewName");
                if (!StringUtils.isEmpty(viewName))
                    form.setViewName(viewName);
            }
            final List<ColumnInfo> columns = getDatasetColumns(form); //ReportManager.get().getDatasetColumns(getViewContext(), getViewContext().getActionURL(), def);

            form.setColumns(columns.toArray(new ColumnInfo[0]));

            JspView<CrosstabDesignBean> view = new JspView<CrosstabDesignBean>("/org/labkey/study/view/crosstabDesigner.jsp", form);
            VBox v = new VBox(view);

            if (reshow)
            {
                Report report = form.getReport();
                if (report != null)
                {
                    v.addView(report.renderReport(getViewContext()));

                    SaveReportViewForm bean = new SaveReportViewForm(report);
                    bean.setDatasetId(form.getDatasetId());
                    bean.setShareReport(true);
                    bean.setShowWithDataset(form.getDatasetId());

                    JspView<SaveReportViewForm> saveWidget = new JspView<SaveReportViewForm>("/org/labkey/study/view/saveReportView.jsp", bean);
                    v.addView(saveWidget);
                }
            }
            return v;
        }

        private List<ColumnInfo> getDatasetColumns(CrosstabDesignBean form)
        {
            QuerySettings settings = new QuerySettings(getViewContext().getActionURL(), "Dataset");
            settings.setQueryName(form.getQueryName());
            settings.setSchemaName(form.getSchemaName());
            settings.setViewName(form.getViewName());

            UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), "study");
            QueryView qv = new QueryView(schema, settings);
            List<DisplayColumn> cols = qv.getDisplayColumns();
            List<ColumnInfo> colInfos = new ArrayList<ColumnInfo>(cols.size());
            for (DisplayColumn col : cols)
                colInfos.add(col.getColumnInfo());

            return colInfos;
        }

        public boolean handlePost(CrosstabDesignBean crosstabDesignBean, BindException errors) throws Exception
        {
            return false;
        }

        public void validateCommand(CrosstabDesignBean target, Errors errors)
        {
        }

        public ActionURL getSuccessURL(CrosstabDesignBean crosstabDesignBean)
        {
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            ViewContext context = getViewContext();
            int datasetId = null == context.get(DataSetDefinition.DATASETKEY) ? 0 : Integer.parseInt((String) context.get(DataSetDefinition.DATASETKEY));
            int visitRowId = null == context.get("visitRowId") ? 0 : Integer.parseInt((String) context.get("visitRowId"));

            return _appendNavTrail(root, "Crosstab View Builder", datasetId, visitRowId);
        }
    }

    public static class ExportForm extends FormData
    {
        private int siteId = 0;
        private int reportId = 0;

        public int getSiteId()
        {
            return siteId;
        }

        public void setSiteId(int siteId)
        {
            this.siteId = siteId;
        }

        public int getReportId()
        {
            return reportId;
        }

        public void setReportId(int reportId)
        {
            this.reportId = reportId;
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class ExportExcelConfigureAction extends SimpleViewAction
    {

        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            setHelpTopic(new HelpTopic("exportExcel", HelpTopic.Area.STUDY));
            Study study = getStudy();

            HttpView v = new GroovyView("/org/labkey/study/reports/configureExportExcel.gm");
            v.addObject("study", study);

            return v;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return _appendNavTrail(root, "Export study data to spreadsheet");
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class ExportExcelAction extends SimpleViewAction<ExportForm>
    {
        public ModelAndView getView(ExportForm form, BindException errors) throws Exception
        {
            ExportExcelReport report;
            if (form.getReportId() != 0)
            {
                Report r = ReportManager.get().getReport(getContainer(), form.getReportId());
                if (!(r instanceof ExportExcelReport))
                {
                    HttpView.throwNotFound();
                    return null;
                }
                report = (ExportExcelReport)r;
            }
            else
            {
                report = new ExportExcelReport();
                report.setSiteId(form.getSiteId());
            }

            User user = getUser();
            Study study = getStudy();

            report.runExportToExcel(getViewContext().getResponse(), study, user);
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresPermission(ACL.PERM_INSERT)
    public class ShowUploadReportAction extends FormViewAction<UploadForm>
    {
        public ModelAndView getView(UploadForm form, boolean reshow, BindException errors) throws Exception
        {
            setHelpTopic(new HelpTopic("staticReports", HelpTopic.Area.STUDY));
            form.setErrors(errors);
            if (form.getReportId() != 0)
            {
                Report report = ReportManager.get().getReport(getContainer(), form.getReportId());
                if (report != null)
                {
                    form.setLabel(report.getDescriptor().getReportName());
                    form.setReportId(report.getDescriptor().getReportId());
                }
            }
            return new JspView<UploadForm>("/org/labkey/study/view/uploadAttachmentReport.jsp", form);
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

            int id = ReportService.get().saveReport(getViewContext(), form.getLabel(), report);

            report = (AttachmentReport)ReportService.get().getReport(id);
            AttachmentService.get().addAttachments(getViewContext().getUser(), report, getAttachmentFileList());

            return true;
        }

        public ActionURL getSuccessURL(UploadForm uploadForm)
        {
            return getViewContext().cloneActionURL().setAction("begin");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return _appendNavTrail(root, "Upload Report");
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class DownloadReportFileAction extends SimpleViewAction<UploadForm>
    {
        public ModelAndView getView(UploadForm form, BindException errors) throws Exception
        {
            Integer reportId = form.getReportId();
            if (null == reportId)
            {
                HttpView.throwNotFound();
                return null;
            }
            AttachmentReport report = (AttachmentReport) ReportManager.get().getReport(getContainer(), reportId);
            if (null == report || null == report.getFilePath())
                HttpView.throwNotFound();

            if (!ReportManager.get().canReadReport(getUser(), getContainer(), report))
                HttpView.throwUnauthorized();

            File file = new File(report.getFilePath());
            if (!file.exists())
                HttpView.throwNotFound("Could not find file with name " + report.getFilePath());

            PageFlowUtil.streamFile(getViewContext().getResponse(), file, true);
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }

        public boolean handlePost(UploadForm form, BindException errors) throws Exception
        {
            Integer reportId = form.getReportId();
            if (null == reportId)
            {
                HttpView.throwNotFound();
                return false;
            }
            AttachmentReport report = (AttachmentReport) ReportManager.get().getReport(getContainer(), reportId);
            if (null == report || null == report.getFilePath())
                HttpView.throwNotFound();

            if (!ReportManager.get().canReadReport(getUser(), getContainer(), report))
                HttpView.throwUnauthorized();

            File file = new File(report.getFilePath());
            if (!file.exists())
                HttpView.throwNotFound("Could not find file with name " + report.getFilePath());

            PageFlowUtil.streamFile(getViewContext().getResponse(), file, true);
            return true;
        }

        public void validateCommand(UploadForm target, Errors errors)
        {
        }

        public ActionURL getSuccessURL(UploadForm uploadForm)
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

            if (!ReportManager.get().canReadReport(getUser(), getContainer(), report[0]))
                HttpView.throwUnauthorized();

            if (report[0] instanceof AttachmentReport)
                AttachmentService.get().download(getViewContext().getResponse(), (AttachmentReport)report[0], form.getName());

            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }

        public boolean handlePost(AttachmentForm form, BindException errors) throws Exception
        {
            SimpleFilter filter = new SimpleFilter("ContainerId", getContainer().getId());
            filter.addCondition("EntityId", form.getEntityId());

            Report[] report = ReportService.get().getReports(filter);
            if (report.length == 0)
            {
                HttpView.throwNotFound("Unable to find report");
                return false;
            }

            //if (!report.getDescriptor().getACL().hasPermission(getUser(), ACL.PERM_READ))
            //    HttpView.throwUnauthorized();

            if (report[0] instanceof AttachmentReport)
            AttachmentService.get().download(getViewContext().getResponse(), (AttachmentReport)report[0], form.getName());
            return true;
        }

        public void validateCommand(AttachmentForm target, Errors errors)
        {
        }

        public ActionURL getSuccessURL(AttachmentForm uploadForm)
        {
            return null;
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class CreateQueryReportAction extends SimpleViewAction<QueryReportForm>
    {

        public ModelAndView getView(QueryReportForm form, BindException errors) throws Exception
        {
            setHelpTopic(new HelpTopic("datasetViews", HelpTopic.Area.STUDY));
            return new JspView<CreateQueryReportBean>("/org/labkey/study/view/createQueryReport.jsp",
                    new CreateQueryReportBean(getViewContext(), form.getQueryName()));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return _appendNavTrail(root, "Create Grid View");
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class CreateCrosstabReportAction extends SimpleViewAction
    {

        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            setHelpTopic(new HelpTopic("crosstabReports", HelpTopic.Area.STUDY));
            return new JspView<CreateCrosstabBean>("/org/labkey/study/view/createCrosstabReport.jsp",
                    new CreateCrosstabBean(getViewContext()));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return _appendNavTrail(root, "Create Crosstab View");
        }
    }

    public static class QueryReportForm extends FormData
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
        private DataSetDefinition[] _datasets;
        private Visit[] _visits;

        public CreateCrosstabBean(ViewContext context)
        {
            Study study = StudyManager.getInstance().getStudy(context.getContainer());
            _datasets = StudyManager.getInstance().getDataSetDefinitions(study);
            _visits = StudyManager.getInstance().getVisits(study);
        }

        public DataSetDefinition[] getDatasets()
        {
            return _datasets;
        }

        public Visit[] getVisits()
        {
            return _visits;
        }
    }

    public static class CreateQueryReportBean
    {
        private List<String> _tableAndQueryNames;
        private Container _container;
        private String _queryName;
        private ActionURL _srcURL;
        private Map<String, DataSetDefinition> _datasetMap;

        public CreateQueryReportBean(ViewContext context, String queryName)
        {
            _tableAndQueryNames = getTableAndQueryNames(context);
            _container = context.getContainer();
            _queryName = queryName;
            _srcURL = context.getActionURL();
        }

        private List<String> getTableAndQueryNames(ViewContext context)
        {
            Study study = StudyManager.getInstance().getStudy(context.getContainer());
            StudyQuerySchema studySchema = new StudyQuerySchema(study, context.getUser(), true);
            return studySchema.getTableAndQueryNames(true);
        }

        public List<String> getTableAndQueryNames()
        {
            return _tableAndQueryNames;
        }

        public Map<String, DataSetDefinition> getDatasetDefinitions()
        {
            if (_datasetMap == null)
            {
                _datasetMap = new HashMap<String, DataSetDefinition>();
                final Study study = StudyManager.getInstance().getStudy(_container);

                for (DataSetDefinition def : StudyManager.getInstance().getDataSetDefinitions(study))
                {
                    _datasetMap.put(def.getLabel(), def);
                }
            }
            return _datasetMap;
        }

        public ActionURL getQueryCustomizeURL()
        {
            return QueryService.get().urlQueryDesigner(_container,
                    StudySchema.getInstance().getSchema().getName());
        }

        public String getQueryName()
        {
            return _queryName;
        }

        public ActionURL getSrcURL()
        {
            return _srcURL;
        }
    }

    public static class UploadForm extends FormData
    {
        private int reportId;
        private String label;
        private String reportDate;
        private String message;
        private String filePath;
        private BindException _errors;

        public String getReportDateString()
        {
            return reportDate;
        }

        public void setReportDateString(String reportDate)
        {
            this.reportDate = reportDate;
        }

        public int getReportId()
        {
            return reportId;
        }

        public void setReportId(int reportId)
        {
            this.reportId = reportId;
        }

        public String getMessage()
        {
            return message;
        }

        public void setMessage(String message)
        {
            this.message = message;
        }

        public void appendMessage(String message)
        {
            if (null == this.message)
                this.message = message;
            else
                this.message = this.message + "<br>" + message;
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
        public void setErrors(BindException errors){_errors = errors;}
        public BindException getErrors(){return _errors;}
    }

    public static class ShowReportForm extends FormData
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


    public static class DataPickerBean
    {
        public Study study;
        public ColumnPickerForm form;
        public String caption;
        public PropertyType propertyType;
        public boolean pickColumn = true;

        public DataPickerBean(Study study, ColumnPickerForm form, String caption, PropertyType type)
        {
            this.study = study;
            this.form = form;
            this.caption = caption;
            this.propertyType = type;
        }
    }


    public static class ColumnPickerForm extends FormData
    {
        private Integer datasetId = null;
        private double sequenceNum = -1;
        private int propertyId = -1;

        public Integer getDatasetId()
        {
            return datasetId;
        }

        public void setDatasetId(Integer datasetId)
        {
            this.datasetId = datasetId;
        }

        public double getSequenceNum()
        {
            return sequenceNum;
        }

        /** @deprecated needed so saved maps (e.g. EnrollmentReport configuration) still  work */
        public void setVisitId(double sequenceNum)
        {
            this.sequenceNum = sequenceNum;
        }

        public void setSequenceNum(double sequenceNum)
        {
            this.sequenceNum = sequenceNum;
        }

        public int getPropertyId()
        {
            return propertyId;
        }

        public void setPropertyId(int propertyId)
        {
            this.propertyId = propertyId;
        }
    }


    public static class SaveReportWidget extends HttpView
    {
        Report report;
        private boolean confirm = false;
        private String srcURL;
        private boolean redirToReport;

        public SaveReportWidget(Report report)
        {
            this(report, false, null, false);
        }

        public SaveReportWidget(Report report, boolean confirm, String srcURL, boolean redirToReport)
        {
            this.report = report;
            this.confirm = confirm;
            this.srcURL = srcURL;
            this.redirToReport = redirToReport;
        }

        @Override
        protected void renderInternal(Object model, PrintWriter out) throws Exception
        {
            out.write("<form method='post' action='");
            out.write(PageFlowUtil.filter(getViewContext().getActionURL().relativeUrl("saveReportView", null, "Study-Reports")));
            out.write("'>");
            out.write("<table border='0' class='normal'><tr>");
            if (confirm)
            {
                out.write("<td>");
                out.write("There is already a view called: <i>");
                out.write(PageFlowUtil.filter(report.getDescriptor().getReportName()));
                out.write("</i>.<br/>Overwrite the existing view?");
                out.write("<input type=hidden name=confirmed value=1>");
                out.write("<input type=hidden name=label value='");
            }
            else
            {
                out.write("<td><b>Save View&nbsp;</b> Name:&nbsp;");
                out.write("<input name='label' value='");
            }
            out.write(PageFlowUtil.filter(report.getDescriptor().getReportName()));
            out.write("'></td>");
            out.write("<td>");
            if (!confirm)
            {
                out.write("<input type=hidden name=srcURL value='");
                out.write(PageFlowUtil.filter(getViewContext().getActionURL().getLocalURIString()));
                out.write("'>");
            }
            out.write("<input type=hidden name=redirectToReport value='");
            out.write(Boolean.toString(redirToReport));
            out.write("'>");
            out.write("<input type=hidden name=reportType value='");
            out.write(report.getDescriptor().getReportType());
            out.write("'>");
            out.write("<input type=hidden name=params value='");
            out.write(PageFlowUtil.filter(report.getDescriptor().toQueryString()));
            out.write("'></td>");

            Container c = getViewContext().getContainer();
            Study study = StudyManager.getInstance().getStudy(c);
            DataSetDefinition[] defs = StudyManager.getInstance().getDataSetDefinitions(study);
            out.write("<td>Add as Custom View For: ");
            out.write("<select name=\"showWithDataset\">");
            //out.write("<option value=\"0\">Views and Reports Web Part</option>");
            int showWithDataset = NumberUtils.toInt(report.getDescriptor().getProperty("showWithDataset"));
            for (DataSetDefinition def : defs)
            {
                out.write("<option ");
                if (def.getDataSetId() == showWithDataset)
                    out.write(" selected ");
                out.write("value=\"");
                out.write(String.valueOf(def.getDataSetId()));
                out.write("\">");
                out.write(PageFlowUtil.filter(def.getLabel()));
                out.write("</option>");
            }
            out.write("</select></td>");

            out.write("<td><input type=image src='");
            out.write(PageFlowUtil.buttonSrc("Save"));
            out.write("'></form>");

            if (confirm)
            {
                out.write("&nbsp;<a href='");
                out.write(PageFlowUtil.filter(srcURL));
                out.write("'><img border=0 src='");
                out.write(PageFlowUtil.buttonSrc("Cancel"));
                out.write("'></a>");
            }
            out.write("</td></tr></table>");
        }
    }

    public static class ExternalReportForm extends BeanViewForm<ExternalReport>
    {
        @Override
        public ExternalReport getBean()
        {
            ExternalReport rpt = super.getBean();
            rpt.setContainer(getContainer());
            return rpt;
        }

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
        protected String srcURL;
        protected String confirmed;
        protected int showWithDataset;
        protected boolean redirectToReport;
        protected Integer redirectToDataset;
        protected String description;
        private BindException _errors;

        public SaveReportForm(){}
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

        public Report getReport()
        {
            Report report = ReportManager.get().createReport(reportType);
            ReportDescriptor descriptor = report.getDescriptor();
            descriptor.setReportName(label);
            descriptor.initFromQueryString(params);
            descriptor.setProperty("showWithDataset", String.valueOf(showWithDataset));
            descriptor.setReportDescription(description);

            return report;
        }

        public String getSrcURL()
        {
            return srcURL;
        }

        public void setSrcURL(String srcURL)
        {
            this.srcURL = srcURL;
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
    }

    public static class SaveReportViewForm extends SaveReportForm
    {
        private boolean _isPlotView;
        private int _datasetId;
        private boolean _shareReport;
        private int _chartsPerRow;
        private boolean _hasMultipleChartsPerView;
        private String _queryName;
        private String _schemaName;
        private String _viewName;
        private String _dataRegionName;

        public SaveReportViewForm(){}
        public SaveReportViewForm(Report report)
        {
            super();
            label = report.getDescriptor().getReportName();
            params = report.getDescriptor().toQueryString();
            reportType = report.getDescriptor().getReportType();
        }

        public Report getReport()
        {
            Report report = super.getReport();
            ReportDescriptor descriptor = report.getDescriptor();

            if (!StringUtils.isEmpty(getSchemaName()))
                descriptor.setProperty(QueryParam.schemaName.toString(), getSchemaName());
            if (!StringUtils.isEmpty(getQueryName()))
                descriptor.setProperty(QueryParam.queryName.toString(), getQueryName());
            if (!StringUtils.isEmpty(getViewName()))
                descriptor.setProperty(QueryParam.viewName.toString(), getViewName());
            if (!StringUtils.isEmpty(getDataRegionName()))
                descriptor.setProperty(QueryParam.dataRegionName.toString(), getDataRegionName());

            descriptor.setProperty("datasetId", String.valueOf(getDatasetId()));
            if (!getShareReport())
                descriptor.setOwner(getViewContext().getUser().getUserId());
            descriptor.setProperty("chartsPerRow", String.valueOf(getChartsPerRow()));
            if (getIsPlotView())
                descriptor.setProperty("filterParam", "participantId");

            return report;
        }

        public void setIsPlotView(boolean isPlotView){_isPlotView = isPlotView;}
        public boolean getIsPlotView(){return _isPlotView;}

        public void setDatasetId(int datasetId){_datasetId = datasetId;}
        public int getDatasetId(){return _datasetId;}

        public void setShareReport(boolean shareReport){_shareReport = shareReport;}
        public boolean getShareReport(){return _shareReport;}

        public void setChartsPerRow(int chartsPerRow){_chartsPerRow = chartsPerRow;}
        public int getChartsPerRow(){return _chartsPerRow;}

        public void setHasMultipleChartsPerView(boolean hasMultipleChartsPerView){_hasMultipleChartsPerView = hasMultipleChartsPerView;}
        public boolean getHasMultipleChartsPerView(){return _hasMultipleChartsPerView;}
        public void setSchemaName(String schemaName){_schemaName = schemaName;}
        public String getSchemaName(){return _schemaName;}
        public void setQueryName(String queryName){_queryName = queryName;}
        public String getQueryName(){return _queryName;}
        public void setViewName(String viewName){_viewName = viewName;}
        public String getViewName(){return _viewName;}
        public void setDataRegionName(String dataRegionName){_dataRegionName = dataRegionName;}
        public String getDataRegionName(){return _dataRegionName;}
    }

    public static class PlotForm extends ViewForm
    {
        private int reportId;
        private int datasetId = 0;
        private int visitRowId = 0;
        private String _action;
        private int _chartsPerRow = 3;
        private Report[] _reports;
        private boolean _isPlotView; // = true;
        private String _participantId;
        private String _queryName;
        private String _schemaName;
        private String _filterParam;
        private String _viewName;

        public int getDatasetId()
        {
            return datasetId;
        }

        public void setDatasetId(int datasetId)
        {
            this.datasetId = datasetId;
        }

        public int getVisitRowId()
        {
            return visitRowId;
        }

        public void setVisitRowId(int visitRowId)
        {
            this.visitRowId = visitRowId;
        }

        public int getReportId()
        {
            return reportId;
        }

        public void setReportId(int reportId)
        {
            this.reportId = reportId;
        }

        public Report[] getReports()
        {
            return _reports;
        }

        public void setReports(Report[] reports){_reports = reports;}

        public void setAction(String action){_action = action;}
        public String getAction(){return _action;}

        public void setChartsPerRow(int chartsPerRow){_chartsPerRow = chartsPerRow;}
        public int getChartsPerRow(){return _chartsPerRow;}

        public void setIsPlotView(boolean isPlotView){_isPlotView = isPlotView;}
        public boolean getIsPlotView(){return _isPlotView;}

        public void setParticipantId(String participantId){_participantId = participantId;}
        public String getParticipantId(){return _participantId;}

        public void setSchemaName(String schemaName){_schemaName = schemaName;}
        public String getSchemaName(){return _schemaName;}
        public void setQueryName(String queryName){_queryName = queryName;}
        public String getQueryName(){return _queryName;}
        public void setFilterParam(String filterParam){_filterParam = filterParam;}
        public String getFilterParam(){return _filterParam;}
        public void setViewName(String viewName){_viewName = viewName;}
        public String getViewName(){return _viewName;}
    }


    public static class ReportData
    {
        private TableInfo tableInfo;
        private ResultSet resultSet;

        public ReportData(Study study, int datasetId, int visitRowId, org.labkey.api.security.User user, ActionURL filterUrl) throws ServletException, SQLException
        {
            DataSetDefinition def = study.getDataSet(datasetId);
            if (def == null)
            {
                HttpView.throwNotFound();
                return; // silence intellij warnings
            }
            Visit visit = null;
            if (0 != visitRowId)
            {
                visit = StudyManager.getInstance().getVisitForRowId(study, visitRowId);
                if (null == visit)
                {
                    HttpView.throwNotFound();
                    return; // silence intellij warnings
                }
            }

            tableInfo = def.getTableInfo(user);

            SimpleFilter filter = new SimpleFilter();
            if (null != visit)
                visit.addVisitFilter(filter);
            filter.addUrlFilters(filterUrl, tableInfo.getName());

            resultSet = Table.selectForDisplay(tableInfo, Table.ALL_COLUMNS, filter, null, 0, 0);
        }

        public TableInfo getTableInfo()
        {
            return tableInfo;
        }

        public ResultSet getResultSet()
        {
            return resultSet;
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class DesignChartAction extends SimpleViewAction<ChartDesignerBean>
    {
        int _datasetId = 0;
        public ModelAndView getView(ChartDesignerBean form, BindException errors) throws Exception
        {
            Map<String, String> props = new HashMap<String, String>();
            for (Pair<String, String> param : form.getParameters())
                props.put(param.getKey(), param.getValue());

            for (ReportDesignBean.ExParam param : form.getExParam())
                props.put(param.getKey(), param.getValue());
            props.put("isAdmin", String.valueOf(getContainer().hasPermission(getUser(), ACL.PERM_ADMIN)));
            props.put("isGuest", String.valueOf(getUser().isGuest()));

            _datasetId = NumberUtils.toInt((String)getViewContext().get(DataSetDefinition.DATASETKEY));
            DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(StudyManager.getInstance().getStudy(getContainer()), _datasetId);
            if (def != null)
                props.put("datasetId", String.valueOf(_datasetId));

            HttpView view = new GWTView("org.labkey.study.chart.StudyChartDesigner", props);
            return new VBox(view);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return _appendNavTrail(root, "Create Chart View", _datasetId, 0);
            //return root.addChild("Create Chart View");
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class ChartServiceAction extends GWTServiceAction
    {
        protected BaseRemoteService createService()
        {
            return new StudyChartServiceImpl(getViewContext());
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class PlotChartAction extends SimpleViewAction<PlotForm>
    {
        public ModelAndView getView(PlotForm form, BindException errors) throws Exception
        {
            final ViewContext context = getViewContext();
            Report report = ReportService.get().getReport(form.getReportId());

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

    private void addParticipantListToCache(int datasetId, String queryName, String viewName)
    {
        ViewContext context = getViewContext();

        final Study study = StudyManager.getInstance().getStudy(getContainer());
        final StudyQuerySchema querySchema = new StudyQuerySchema(study, getUser(), true);
        QuerySettings qs = new QuerySettings(context.getActionURL(), DataSetQueryView.DATAREGION);
        qs.setSchemaName(querySchema.getSchemaName());
        qs.setQueryName(queryName);
        qs.setViewName(viewName);
        DataSetQueryView queryView = new DataSetQueryView(datasetId, querySchema, qs, null, null);

        addParticipantListToCache(datasetId, queryView, viewName);
    }

    private void addParticipantListToCache(int datasetId, QueryView queryView, String viewName)
    {
        List<String> participants = StudyController.generateParticipantList(queryView);
        StudyController.addParticipantListToCache(getViewContext(), datasetId, viewName, participants, null);
    }

    public static HttpView getParticipantNavTrail(ViewContext context, List<String> participantList)
    {
        String participantId = context.getActionURL().getParameter("participantId");

        String previousParticipantURL = null;
        String nextParticipantURL = null;
        String title = null;

        if (!participantList.isEmpty())
        {
            if (participantId == null)
            {
                participantId = participantList.get(0);
                context.put("participantId", participantId);
            }
            int idx = participantList.indexOf(participantId);
            if (idx != -1)
            {
                title = "Participant : " + participantId;

                if (idx > 0)
                {
                    final String ptid = participantList.get(idx-1);
                    ActionURL prevUrl = context.cloneActionURL();
                    prevUrl.replaceParameter("participantId", ptid);
                    previousParticipantURL = prevUrl.getEncodedLocalURIString();
                }

                if (idx < participantList.size()-1)
                {
                    final String ptid = participantList.get(idx+1);
                    ActionURL nextUrl = context.cloneActionURL();
                    nextUrl.replaceParameter("participantId", ptid);
                    nextParticipantURL = nextUrl.getEncodedLocalURIString();
                }
            }
        }
        return new StudyController.ParticipantNavView(previousParticipantURL, nextParticipantURL, title);
    }

    @RequiresPermission(ACL.PERM_READ)
    public class RunRReportAction extends SimpleViewAction<RReportBean>
    {
        protected Report _report;
        protected DataSetDefinition _def;

        protected Report getReport(RReportBean form) throws Exception
        {
            String reportId = form.getViewContext().getActionURL().getParameter("Dataset.viewName");
            if (NumberUtils.isDigits(reportId))
            {
                form.setReportId(NumberUtils.toInt(reportId));
                return ReportManager.get().getReport(form.getContainer(), NumberUtils.toInt(reportId));
            }
            return null;
        }

        public ModelAndView getView(RReportBean form, BindException errors) throws Exception
        {
            VBox view = new VBox();

            _report = getReport(form);
            DataSetDefinition def = getDataSetDefinition();
            if (def != null && _report != null)
            {
                ActionURL url = getViewContext().cloneActionURL().setAction(StudyController.DatasetAction.class).
                                        replaceParameter("Dataset.viewName", QueryView.REPORTID_PARAM + _report.getDescriptor().getReportId()).
                                        replaceParameter(DataSetDefinition.DATASETKEY, String.valueOf(def.getDataSetId()));

                return HttpView.redirect(url);
//                view.addView(new DataHeader(getViewContext().getActionURL(), null, getDataSetDefinition(), false));
            }

            if (_report != null)
            {
                view.addView(_report.getRunReportView(getViewContext()));
            }
            else
            {
                view.addView(new HtmlView("Unable to locate the specified report"));
            }
            return view;
        }

        protected DataSetDefinition getDataSetDefinition()
        {
            if (_def == null && _report != null)
            {
                final Study study = StudyManager.getInstance().getStudy(getContainer());
                if (study != null)
                {
                    _def = StudyManager.getInstance().
                            getDataSetDefinition(study, _report.getDescriptor().getProperty(ReportDescriptor.Prop.queryName));
                }
            }
            return _def;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            DataSetDefinition def = getDataSetDefinition();

            if (def != null)
                _appendNavTrail(root, def.getDataSetId(), 0, 0);
            return root;
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class DatasetReportAction extends SimpleViewAction<ReportForm>
    {
        protected DataSetDefinition _def;
        protected Report _report;

        protected Report getReport(ReportForm form) throws Exception
        {
            String reportId = form.getViewContext().getActionURL().getParameter("Dataset.viewName");
            if (NumberUtils.isDigits(reportId))
                return ReportManager.get().getReport(form.getContainer(), NumberUtils.toInt(reportId));

            return null;
        }

        public ModelAndView getView(ReportForm form, BindException errors) throws Exception
        {
            VBox view = new VBox();

            _report = getReport(form);
            DataSetDefinition def = getDataSetDefinition();
            if (def != null)
            {
                view.addView(new DataHeader(getViewContext().getActionURL(), null, getDataSetDefinition(), false));
            }

            if (_report != null)
            {
                view.addView(_report.getRunReportView(getViewContext()));
            }
            else
            {
                view.addView(new HtmlView("Unable to locate the specified report"));
            }
            return view;
        }

        protected DataSetDefinition getDataSetDefinition()
        {
            if (_def == null)
            {
                try {
                    int datasetId = NumberUtils.toInt(getViewContext().getActionURL().getParameter(DataSetDefinition.DATASETKEY));
                    if (_report != null)
                        datasetId = NumberUtils.toInt(_report.getDescriptor().getProperty(DataSetDefinition.DATASETKEY));
                    _def = StudyManager.getInstance().getDataSetDefinition(getStudy(), datasetId);
                }
                catch (Exception e) {_def = null;}
            }
            return _def;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            DataSetDefinition def = getDataSetDefinition();

            if (def != null)
                _appendNavTrail(root, def.getDataSetId(), 0, 0);
            return root;
        }
    }

    public static class TimePlotForm extends FormData
    {
        private int reportId;
        private int datasetId;
        /** UNDONE: should this be renamed sequenceNum? */ 
        private double visitId;
        private String columnX;

        public int getDatasetId()
        {
            return datasetId;
        }

        public void setDatasetId(int datasetId)
        {
            this.datasetId = datasetId;
        }

        public String getColumnX()
        {
            return columnX;
        }

        public void setColumnX(String columnX)
        {
            this.columnX = columnX;
        }

        public double getVisitId()
        {
            return visitId;
        }

        public void setVisitId(double visitId)
        {
            this.visitId = visitId;
        }

        public int getReportId()
        {
            return reportId;
        }

        public void setReportId(int reportId)
        {
            this.reportId = reportId;
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class TimePlotAction extends SimpleViewAction<TimePlotForm>
    {
        public ModelAndView getView(TimePlotForm form, BindException errors) throws Exception
        {
            Report report = ReportService.get().getReport(form.getReportId());
            if (report != null)
            {
                report.renderReport(getViewContext());
            }
            else
            {
                HttpView.throwNotFound();
                return null;
            }
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    private NavTree _appendNavTrail(NavTree root, String name)
    {
        try {
            Study study = getStudy();
            ActionURL url = getViewContext().getActionURL();
            root.addChild(study.getLabel(), url.relativeUrl("overview", null, "Study"));

            if (getUser().isAdministrator())
                root.addChild("Manage Reports and Views", ActionURL.toPathString("Study-Reports", "manageReports.view", getContainer()));
        }
        catch (Exception e)
        {
            return root.addChild(name);
        }
        return root.addChild(name);
    }

    private NavTree _appendNavTrail(NavTree root, String name, int datasetId, int visitRowId)
    {
        try {
            Study study = getStudy();
            ActionURL url = getViewContext().getActionURL();
            root.addChild(study.getLabel(), url.relativeUrl("overview", null, "Study"));
            if (getUser().isAdministrator())
                root.addChild("Manage Reports and Views", ActionURL.toPathString("Study-Reports", "manageReports.view", getContainer()));
            
            Visit visit = null;
            if (visitRowId > 0)
                visit = StudyManager.getInstance().getVisitForRowId(getStudy(), visitRowId);
            if (datasetId > 0)
            {
                DataSetDefinition dataSet = StudyManager.getInstance().getDataSetDefinition(getStudy(), datasetId);
                if (dataSet != null)
                {
                    String label = dataSet.getLabel() != null ? dataSet.getLabel() : "" + dataSet.getDataSetId();
                    if (0 == visitRowId)
                        label += " (All Visits)";
                    ActionURL datasetUrl = url.clone();
                    datasetUrl.deleteParameter(Visit.VISITKEY);
                    datasetUrl.setAction("dataset");
                    datasetUrl.setPageFlow("Study");
                    root.addChild(label, datasetUrl.getLocalURIString());
                }
            }
            if (null != visit)
                root.addChild(visit.getDisplayString(), url.relativeUrl("dataset", null, "Study", false));
        }
        catch (Exception e)
        {
            return root.addChild(name);
        }
        return root.addChild(name);
    }

    public static class ReportsWebPart extends JspView<Object>
    {
        public ReportsWebPart(boolean isWide)
        {
            super("/org/labkey/study/view/manageReports.jsp");
            setTitle("Reports and Views");

            StudyManageReportsBean bean = new StudyManageReportsBean(getViewContext(), false, isWide);
            setModelBean(bean);
        }

        public void setAdminMode(boolean mode)
        {
            Object model = getModelBean();
            if (model instanceof StudyManageReportsBean)
                ((StudyManageReportsBean)model).setAdminView(mode);
        }
    }

    public static class StudyRReportViewFactory implements ReportService.ViewFactory
    {
        public HttpView createView(ViewContext context, RReportBean bean)
        {
            if (StudyManager.getInstance().getStudy(context.getContainer()) != null)
                return new StudyRReportView(bean);

            return null;
        }
    }

    public static class StudyRReportView extends WebPartView
    {
        public StudyRReportView(RReportBean bean)
        {
            super(bean);
            this.setTitle("Study module options");
        }

        protected void renderView(Object model, PrintWriter out) throws Exception
        {
            if (model instanceof RReportBean)
            {
                out.print("<table><tr><td>");
                out.print("<input type=\"checkbox\" value=\"participantId\" name=\"");
                out.print(ReportDescriptor.Prop.filterParam);
                out.print("\"");
                out.print("participantId".equals(((RReportBean)model).getFilterParam()) ? "checked" : "");
                out.print(">");
                out.print("Participant chart.&nbsp;" + PageFlowUtil.helpPopup("participant chart", "A participant chart view shows measures for only one participant at a time. A participant chart view allows the user to step through charts for each participant shown in any dataset grid."));
                out.print("</td></tr></table>");
            }
        }
    }
}
