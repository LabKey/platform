/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.labkey.api.action.*;
import org.labkey.api.attachments.AttachmentForm;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.*;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.query.*;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.ReportIdentifier;
import org.labkey.api.reports.report.view.ChartDesignerBean;
import org.labkey.api.reports.report.view.RReportBean;
import org.labkey.api.reports.report.view.ReportDesignBean;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.Study;


import org.labkey.api.study.reports.CrosstabReportDescriptor;
import org.labkey.api.study.Visit;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.*;
import org.labkey.study.StudyModule;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.BaseStudyController;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.study.reports.*;
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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ReportsController extends BaseStudyController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(ReportsController.class);

    public ReportsController() throws Exception
    {
        super();
        setActionResolver(_actionResolver);
    }

    public StudyImpl getStudy() throws ServletException
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
                return root.addChild("Manage Views", new ActionURL(ManageReportsAction.class, getContainer()));
            else
                return root.addChild("Views");
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class DeleteReportAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            String reportIdParam = getRequest().getParameter(ReportDescriptor.Prop.reportId.name());
            ReportIdentifier reportId = ReportService.get().getReportIdentifier(reportIdParam);

            Report report = null;

            if (reportId != null)
                report = reportId.getReport();

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
            return HttpView.redirect(new ActionURL(ManageReportsAction.class, getContainer()));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null; 
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ManageReportsAction extends SimpleViewAction<StudyManageReportsBean>
    {
        public ModelAndView getView(StudyManageReportsBean form, BindException errors) throws Exception
        {
            setHelpTopic(new HelpTopic("manageReportsAndViews", HelpTopic.Area.STUDY));
            return new StudyJspView<StudyManageReportsBean>(getStudy(), "manageViews.jsp", form, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return _appendNavTrail(root, "Manage Views");
        }
    }

    public static class ViewsSummaryForm
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

    @RequiresPermission(ACL.PERM_READ)
    public class ManageViewsSummaryAction extends ApiAction<ViewsSummaryForm>
    {
        public ApiResponse execute(ViewsSummaryForm form, BindException errors) throws Exception
        {
            boolean isAdmin = getContainer().hasPermission(getUser(), AdminPermission.class);
            return new ApiSimpleResponse("views", ReportManager.get().getViews(getViewContext(), form.getSchemaName(), form.getQueryName(), isAdmin, true));
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
                    return new HtmlView("<font class=labkey-error>This view must be configured by an administrator.</font>");

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
                descriptor.setProperty(VisitImpl.SEQUENCEKEY, VisitImpl.formatSequenceNum(form.getSequenceNum()));

            if (reshow)
            {
                EnrollmentReport.saveEnrollmentReport(getViewContext(), report);
                return HttpView.redirect(getViewContext().getActionURL().relativeUrl("enrollmentReport", null));
            }

            int datasetId = NumberUtils.toInt(descriptor.getProperty(DataSetDefinition.DATASETKEY));
            double sequenceNum = NumberUtils.toDouble(descriptor.getProperty(VisitImpl.SEQUENCEKEY));

            form.setDatasetId(datasetId);
            form.setSequenceNum(sequenceNum);

            DataPickerBean bean = new DataPickerBean(
                    getStudy(), form,
                    "Choose the form and column to use for the enrollment view.",
                    PropertyType.DATE_TIME);
            bean.pickColumn = false;
            return new JspView<DataPickerBean>("/org/labkey/study/view/columnPicker.jsp", bean);
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
    public class ConvertQueryToReportAction extends ApiAction<SaveReportViewForm>
    {
        public ApiResponse execute(SaveReportViewForm form, BindException errors) throws Exception
        {
            Report report = form.getReport();
            final String key = ReportUtil.getReportQueryKey(report.getDescriptor());

            if (!reportNameExists(getViewContext(), form.getViewName(), key))
            {
                if (report instanceof StudyQueryReport)
                {
                    // add the dataset id
                    DataSet def = StudyManager.getInstance().getDataSetDefinition(getStudy(), form.getQueryName());
                    if (def != null)
                    {
                        report.getDescriptor().setProperty("showWithDataset", String.valueOf(def.getDataSetId()));
                        ((StudyQueryReport)report).renameReport(getViewContext(), key, form.getViewName());
                        return new ApiSimpleResponse("success", true);
                    }
                }
            }
            else
                HttpView.throwUnauthorized("A report of the same name already exists");
            return new ApiSimpleResponse("success", false);
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
                return HttpView.redirect(new ActionURL(ManageReportsAction.class, getContainer()));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    private ModelAndView getDatasetForward(int reportId, Integer dataset) throws Exception
    {
        ActionURL url = getViewContext().cloneActionURL();
        url.setAction(StudyController.DatasetReportAction.class);

        url.replaceParameter("Dataset.reportId", String.valueOf(reportId));
        url.replaceParameter(DataSetDefinition.DATASETKEY,  String.valueOf(dataset));
        return HttpView.redirect(url);
    }

    @RequiresPermissionClass(ReadPermission.class)
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
            if (reportNameExists(getViewContext(), form.getLabel(), ReportUtil.getReportKey(form.getSchemaName(), form.getQueryName())))
                errors.reject("saveReportView", "There is already a report with the name of: '" + form.getLabel() +
                        "'. Please specify a different name.");
        }

        public boolean handlePost(SaveReportViewForm form, BindException errors) throws Exception
        {
            Report report = form.getReport();
            _savedReportId = ReportService.get().saveReport(getViewContext(), ReportUtil.getReportKey(form.getSchemaName(), form.getQueryName()), report);

            return true;
        }

        public ActionURL getSuccessURL(SaveReportViewForm form)
        {
            if (!StringUtils.isBlank(form.getRedirectUrl()))
                return new ActionURL(form.getRedirectUrl());
            else if (form.getRedirectToDataset() != null)
            {
                if (StudyManager.getInstance().getStudy(getContainer()) != null)
                {
                    return new ActionURL(StudyController.DatasetAction.class, getContainer()).
                            addParameter("Dataset.reportId", String.valueOf(_savedReportId)).
                            addParameter(DataSetDefinition.DATASETKEY, form.getRedirectToDataset());
                }
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

        public Report getReport() throws Exception
        {
            Report report = super.getReport();
            CrosstabReportDescriptor descriptor = (CrosstabReportDescriptor)report.getDescriptor();

            if (_visitRowId != -1) descriptor.setProperty(VisitImpl.VISITKEY, Integer.toString(_visitRowId));
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
            form.setColumns(getColumns(form));

            JspView<CrosstabDesignBean> view = new JspView<CrosstabDesignBean>("/org/labkey/study/view/crosstabDesigner.jsp", form);
            VBox v = new VBox(view);

            if (reshow)
            {
                Report report = form.getReport();
                if (report != null)
                {
                    v.addView(report.renderReport(getViewContext()));

                    SaveReportViewForm bean = new SaveReportViewForm(report);
                    bean.setShareReport(true);
                    bean.setSchemaName(form.getSchemaName());
                    bean.setQueryName(form.getQueryName());
                    bean.setDataRegionName(form.getDataRegionName());
                    bean.setViewName(form.getViewName());
                    bean.setRedirectUrl(form.getRedirectUrl());

                    JspView<SaveReportViewForm> saveWidget = new JspView<SaveReportViewForm>("/org/labkey/study/view/saveReportView.jsp", bean);
                    v.addView(saveWidget);
                }
            }
            return v;
        }

        private Map<String, ColumnInfo> getColumns(CrosstabDesignBean form) throws ServletException
        {

            QuerySettings settings = new QuerySettings(getViewContext(), "Dataset");
            settings.setQueryName(form.getQueryName());
            settings.setSchemaName(form.getSchemaName());
            settings.setViewName(form.getViewName());

            UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), form.getSchemaName());
            QueryView qv = schema.createView(getViewContext(), settings);
            List<DisplayColumn> cols = qv.getDisplayColumns();
            Map<String, ColumnInfo> colMap = new CaseInsensitiveHashMap<ColumnInfo>();
            for (DisplayColumn col : cols)
            {
                ColumnInfo colInfo = col.getColumnInfo();
                if (colInfo != null)
                    colMap.put(colInfo.getAlias(), colInfo);
            }
            return colMap;
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
/*
            ViewContext context = getViewContext();
            int datasetId = null == context.get(DataSetDefinition.DATASETKEY) ? 0 : Integer.parseInt((String) context.get(DataSetDefinition.DATASETKEY));
            int visitRowId = null == context.get("visitRowId") ? 0 : Integer.parseInt((String) context.get("visitRowId"));

            return _appendNavTrail(root, "Crosstab View Builder", datasetId, visitRowId);
*/
            return root.addChild("Crosstab View Builder");
        }
    }

    public static class ExportForm
    {
        private int siteId = 0;
        private ReportIdentifier reportId;

        public int getSiteId()
        {
            return siteId;
        }

        public void setSiteId(int siteId)
        {
            this.siteId = siteId;
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

    @RequiresPermission(ACL.PERM_READ)
    public class ExportExcelConfigureAction extends SimpleViewAction
    {

        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            setHelpTopic(new HelpTopic("exportExcel", HelpTopic.Area.STUDY));

            return new JspView<StudyImpl>("/org/labkey/study/reports/configureExportExcel.jsp", getStudy());
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
            if (form.getReportId() != null)
            {
                Report r = form.getReportId().getReport();
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
            StudyImpl study = getStudy();

            report.runExportToExcel(getViewContext().getResponse(), study, user);
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
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
                    form.setReportId(form.getReportId());
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
        private DataSetDefinition[] _datasets;
        private VisitImpl[] _visits;

        public CreateCrosstabBean(ViewContext context)
        {
            Study study = StudyManager.getInstance().getStudy(context.getContainer());
            _datasets = StudyManager.getInstance().getDataSetDefinitions(study);
            _visits = StudyManager.getInstance().getVisits(study, Visit.Order.DISPLAY);
        }

        public DataSetDefinition[] getDatasets()
        {
            return _datasets;
        }

        public VisitImpl[] getVisits()
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
        private ActionURL _srcURL;
        private Map<String, DataSetDefinition> _datasetMap;

        public CreateQueryReportBean(ViewContext context, String queryName)
        {
            _tableAndQueryNames = getTableAndQueryNames(context);
            _container = context.getContainer();
            _user = context.getUser();
            _queryName = queryName;
            _srcURL = context.getActionURL();
        }

        private List<String> getTableAndQueryNames(ViewContext context)
        {
            StudyImpl study = StudyManager.getInstance().getStudy(context.getContainer());
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
            return QueryService.get().urlQueryDesigner(_user, _container,
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

    public static class UploadForm
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


    public static class DataPickerBean
    {
        public StudyImpl study;
        public ColumnPickerForm form;
        public String caption;
        public PropertyType propertyType;
        public boolean pickColumn = true;

        public DataPickerBean(StudyImpl study, ColumnPickerForm form, String caption, PropertyType type)
        {
            this.study = study;
            this.form = form;
            this.caption = caption;
            this.propertyType = type;
        }
    }


    public static class ColumnPickerForm
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
            out.write("<table><tr>");
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
            DataSet[] defs = StudyManager.getInstance().getDataSetDefinitions(study);
            out.write("<td>Add as Custom View For: ");
            out.write("<select name=\"showWithDataset\">");
            //out.write("<option value=\"0\">Views and Reports Web Part</option>");
            int showWithDataset = NumberUtils.toInt(report.getDescriptor().getProperty("showWithDataset"));
            for (DataSet def : defs)
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

            out.write("<td>" + PageFlowUtil.generateSubmitButton("Save"));
            out.write("</form>");

            if (confirm)
            {
                out.write("&nbsp;" + PageFlowUtil.generateButton("Cancel", srcURL));
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
        private boolean _shareReport;
        private String _queryName;
        private String _schemaName;
        private String _viewName;
        private String _dataRegionName;
        private String _redirectUrl;

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
           if (!getShareReport())
                descriptor.setOwner(getViewContext().getUser().getUserId());

            return report;
        }

        public void setShareReport(boolean shareReport){_shareReport = shareReport;}
        public boolean getShareReport(){return _shareReport;}

        public void setSchemaName(String schemaName){_schemaName = schemaName;}
        public String getSchemaName(){return _schemaName;}
        public void setQueryName(String queryName){_queryName = queryName;}
        public String getQueryName(){return _queryName;}
        public void setViewName(String viewName){_viewName = viewName;}
        public String getViewName(){return _viewName;}
        public void setDataRegionName(String dataRegionName){_dataRegionName = dataRegionName;}
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

    public static class PlotForm
    {
        private ReportIdentifier reportId;
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

        public ReportIdentifier getReportId()
        {
            return reportId;
        }

        public void setReportId(ReportIdentifier reportId)
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
            DataSet def = study.getDataSet(datasetId);
            if (def == null)
            {
                HttpView.throwNotFound();
                return; // silence intellij warnings
            }
            VisitImpl visit = null;
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
			ViewContext context = getViewContext();
			if (StringUtils.isEmpty(form.getSchemaName()))
				form.setSchemaName("study");
			if (null == DefaultSchema.get(context.getUser(), context.getContainer()).getSchema(form.getSchemaName()))
				HttpView.throwNotFound();

            Map<String, String> props = new HashMap<String, String>();
            for (Pair<String, String> param : form.getParameters())
                props.put(param.getKey(), param.getValue());

            props.put("isAdmin", String.valueOf(getContainer().hasPermission(getUser(), ACL.PERM_ADMIN)));
            props.put("isGuest", String.valueOf(getUser().isGuest()));
            props.put("isParticipantChart", getViewContext().getActionURL().getParameter("isParticipantChart"));
            props.put("participantId", getViewContext().getActionURL().getParameter("participantId"));

            _datasetId = NumberUtils.toInt((String)getViewContext().get(DataSetDefinition.DATASETKEY));
            DataSet def = StudyManager.getInstance().getDataSetDefinition(StudyManager.getInstance().getStudy(getContainer()), _datasetId);
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
            ReportIdentifier reportId = form.getReportId();

            if (reportId != null)
            {
                Report report = reportId.getReport();
                if (report != null)
                    return report.renderReport(context);
            }
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    public static HttpView getParticipantNavTrail(ViewContext context, List<String> participantList)
    {
        String participantId = context.getActionURL().getParameter("participantId");
        String qcState = context.getActionURL().getParameter(SharedFormParameters.QCState);

        String previousParticipantURL = null;
        String nextParticipantURL = null;
        String title = null;

        if (!participantList.isEmpty())
        {
            if (participantId == null || !participantList.contains(participantId))
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
        StudyController.ParticipantNavView view =  new StudyController.ParticipantNavView(previousParticipantURL, nextParticipantURL, null, qcState, title);
        view.setShowCustomizeLink(false);

        return view;
    }

    @RequiresPermission(ACL.PERM_READ)
    public class RunRReportAction extends SimpleViewAction<RReportBean>
    {
        protected Report _report;
        protected DataSet _def;

        protected Report getReport(RReportBean form) throws Exception
        {
            String reportIdParam = form.getViewContext().getActionURL().getParameter("Dataset.reportId");
            ReportIdentifier reportId = ReportService.get().getReportIdentifier(reportIdParam);
            if (null != reportId)
            {
                form.setReportId(reportId);
                return reportId.getReport();
            }
            return null;
        }

        public ModelAndView getView(RReportBean form, BindException errors) throws Exception
        {
            _report = getReport(form);
            if (_report == null)
                return new HtmlView("Unable to locate the specified report");

            DataSet def = getDataSetDefinition();
            if (def != null && _report != null)
            {
                ActionURL url = getViewContext().cloneActionURL().setAction(StudyController.DatasetAction.class).
                                        replaceParameter("Dataset.reportId", _report.getDescriptor().getReportId().toString()).
                                        replaceParameter(DataSetDefinition.DATASETKEY, String.valueOf(def.getDataSetId()));

                return HttpView.redirect(url);
            }

            if (ReportManager.get().canReadReport(getUser(), getContainer(), _report))
                return _report.getRunReportView(getViewContext());
            else
                return new HtmlView("User does not have read permission on this report.");
        }

        protected DataSet getDataSetDefinition()
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
            DataSet def = getDataSetDefinition();

            if (def != null)
            {
                String qcState = getViewContext().getActionURL().getParameter(SharedFormParameters.QCState);
                _appendNavTrail(root, def.getDataSetId(), 0, null, qcState);
            }
            return root;
        }
    }

    public static class TimePlotForm
    {
        private ReportIdentifier reportId;
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

        public ReportIdentifier getReportId()
        {
            return reportId;
        }

        public void setReportId(ReportIdentifier reportId)
        {
            this.reportId = reportId;
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class TimePlotAction extends SimpleViewAction<TimePlotForm>
    {
        public ModelAndView getView(TimePlotForm form, BindException errors) throws Exception
        {
            Report report = form.getReportId().getReport();
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
                root.addChild("Manage Views", new ActionURL(ManageReportsAction.class, getContainer()));
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
                root.addChild("Manage Views", new ActionURL(ManageReportsAction.class, getContainer()));
            
            VisitImpl visit = null;
            if (visitRowId > 0)
                visit = StudyManager.getInstance().getVisitForRowId(getStudy(), visitRowId);
            if (datasetId > 0)
            {
                DataSet dataSet = StudyManager.getInstance().getDataSetDefinition(getStudy(), datasetId);
                if (dataSet != null)
                {
                    String label = dataSet.getLabel() != null ? dataSet.getLabel() : "" + dataSet.getDataSetId();
                    if (0 == visitRowId)
                        label += " (All Visits)";
                    ActionURL datasetUrl = url.clone();
                    datasetUrl.deleteParameter(VisitImpl.VISITKEY);
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
                RReportBean bean = (RReportBean)model;
                boolean hasQuery = bean.getQueryName() != null || bean.getSchemaName() != null || bean.getViewName() != null;

                out.print("<table>");
                if (hasQuery)
                {
                    out.print("<tr><td>");
                    out.print("<input type=\"checkbox\" value=\"participantId\" name=\"");
                    out.print(ReportDescriptor.Prop.filterParam);
                    out.print("\"");
                    out.print("participantId".equals(bean.getFilterParam()) ? "checked" : "");
                    out.print(" onchange=\"LABKEY.setDirty(true);return true;\">");
                    out.print("Participant chart.&nbsp;" + PageFlowUtil.helpPopup("participant chart", "A participant chart view shows measures for only one participant at a time. A participant chart view allows the user to step through charts for each participant shown in any dataset grid."));
                    out.print("</td></tr>");
                }
                out.print("<tr><td><input type=\"checkbox\" name=\"cached\" " + (bean.isCached() ? "checked" : "") + " onchange=\"LABKEY.setDirty(true);return true;\">Automatically cache this report for faster reloading.</td></tr>");
                out.print("</table>");
            }
        }
    }
}