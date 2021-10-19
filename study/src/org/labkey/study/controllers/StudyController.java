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

package org.labkey.study.controllers;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.collections4.FactoryUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ConfirmAction;
import org.labkey.api.action.CustomApiForm;
import org.labkey.api.action.FormApiAction;
import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.HasViewContext;
import org.labkey.api.action.Marshal;
import org.labkey.api.action.Marshaller;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.QueryViewAction;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleErrorView;
import org.labkey.api.action.SimpleRedirectAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.admin.ImportException;
import org.labkey.api.admin.ImportOptions;
import org.labkey.api.admin.notification.NotificationService;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.assay.AssayUrls;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentForm;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.BaseDownloadAction;
import org.labkey.api.audit.TransactionAuditProvider;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.compliance.ComplianceService;
import org.labkey.api.data.*;
import org.labkey.api.data.views.DataViewService;
import org.labkey.api.exp.LsidManager;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.module.ModuleHtmlView;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusUrls;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.pipeline.browse.PipelinePathForm;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.qc.AbstractDeleteDataStateAction;
import org.labkey.api.qc.AbstractManageDataStatesForm;
import org.labkey.api.qc.AbstractManageQCStatesAction;
import org.labkey.api.qc.AbstractManageQCStatesBean;
import org.labkey.api.qc.DataState;
import org.labkey.api.qc.DataStateHandler;
import org.labkey.api.qc.DeleteDataStateForm;
import org.labkey.api.qc.QCStateManager;
import org.labkey.api.query.AbstractQueryImportAction;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.query.snapshot.QuerySnapshotDefinition;
import org.labkey.api.query.snapshot.QuerySnapshotForm;
import org.labkey.api.query.snapshot.QuerySnapshotService;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.model.ReportPropsManager;
import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.reports.model.ViewCategoryManager;
import org.labkey.api.reports.report.AbstractReportIdentifier;
import org.labkey.api.reports.report.QueryReport;
import org.labkey.api.reports.report.ReportIdentifier;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.search.SearchService;
import org.labkey.api.search.SearchUrls;
import org.labkey.api.security.RequiresLogin;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.PlatformDeveloperPermission;
import org.labkey.api.security.permissions.QCAnalystPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.specimen.importer.RequestabilityManager;
import org.labkey.api.specimen.location.LocationImpl;
import org.labkey.api.specimen.location.LocationManager;
import org.labkey.api.study.CohortFilter;
import org.labkey.api.study.CompletionType;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Dataset.KeyManagementType;
import org.labkey.api.study.MasterPatientIndexService;
import org.labkey.api.study.ParticipantCategory;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.StudyUrls;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.Visit;
import org.labkey.api.study.model.ParticipantGroup;
import org.labkey.api.study.publish.StudyPublishService;
import org.labkey.api.study.security.permissions.ManageStudyPermission;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.DemoMode;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.GridView;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.Portal;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewForm;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.writer.FileSystemFile;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.CohortFilterFactory;
import org.labkey.study.MasterPatientIndexMaintenanceTask;
import org.labkey.study.StudyModule;
import org.labkey.study.StudySchema;
import org.labkey.study.StudyServiceImpl;
import org.labkey.study.assay.AssayPublishConfirmAction;
import org.labkey.study.assay.AssayPublishStartAction;
import org.labkey.study.assay.StudyPublishManager;
import org.labkey.study.controllers.publish.SampleTypePublishConfirmAction;
import org.labkey.study.controllers.publish.SampleTypePublishStartAction;
import org.labkey.study.controllers.security.SecurityController;
import org.labkey.study.dataset.DatasetSnapshotProvider;
import org.labkey.study.dataset.DatasetViewProvider;
import org.labkey.study.designer.StudySchedule;
import org.labkey.study.importer.DatasetImportUtils;
import org.labkey.study.importer.SchemaReader;
import org.labkey.study.importer.SchemaTsvReader;
import org.labkey.study.importer.StudyReload.ReloadStatus;
import org.labkey.study.importer.StudyReload.ReloadTask;
import org.labkey.study.importer.VisitMapImporter;
import org.labkey.study.model.*;
import org.labkey.study.pipeline.DatasetFileReader;
import org.labkey.study.pipeline.MasterPatientIndexUpdateTask;
import org.labkey.study.pipeline.StudyPipeline;
import org.labkey.study.qc.StudyQCStateHandler;
import org.labkey.study.query.DatasetQuerySettings;
import org.labkey.study.query.DatasetQueryView;
import org.labkey.study.query.LocationTable;
import org.labkey.study.query.PublishedRecordQueryView;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.study.query.StudyQueryView;
import org.labkey.study.reports.ReportManager;
import org.labkey.study.view.SubjectsWebPart;
import org.labkey.study.visitmanager.VisitManager;
import org.labkey.study.visitmanager.VisitManager.VisitStatistic;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.labkey.api.util.PageFlowUtil.filter;
import static org.labkey.study.model.QCStateSet.PUBLIC_STATES_LABEL;
import static org.labkey.study.model.QCStateSet.getQCStateFilteredURL;
import static org.labkey.study.model.QCStateSet.getQCUrlFilterKey;
import static org.labkey.study.model.QCStateSet.getQCUrlFilterValue;
import static org.labkey.study.model.QCStateSet.selectedQCStateLabelFromUrl;

/**
 * User: Karl Lum
 * Date: Nov 28, 2007
 */
public class StudyController extends BaseStudyController
{
    private static final Logger _log = LogManager.getLogger(StudyController.class);
    private static final String PARTICIPANT_CACHE_PREFIX = "Study_participants/participantCache";
    private static final String EXPAND_CONTAINERS_KEY = StudyController.class.getName() + "/expandedContainers";
    private static final String DATASET_DATAREGION_NAME = "Dataset";

    private static final ActionResolver ACTION_RESOLVER = new DefaultActionResolver(
        StudyController.class,
        CreateChildStudyAction.class,
        AutoCompleteAction.class
    );

    public static final String DATASET_REPORT_ID_PARAMETER_NAME = "Dataset.reportId";
    public static final String DATASET_VIEW_NAME_PARAMETER_NAME = "Dataset.viewName";

    public static class StudyUrlsImpl implements StudyUrls
    {
        @Override
        public ActionURL getBeginURL(Container container)
        {
            return new ActionURL(BeginAction.class, container);
        }

        @Override
        public ActionURL getCompletionURL(Container studyContainer, CompletionType type)
        {
            if (studyContainer == null)
                return null;

            ActionURL url = new ActionURL(AutoCompleteAction.class, studyContainer);
            url.addParameter("type", type.name());
            url.addParameter("prefix", "");
            return url;
        }

        @Override
        public ActionURL getCreateStudyURL(Container container)
        {
            return new ActionURL(CreateStudyAction.class, container);
        }

        @Override
        public ActionURL getManageStudyURL(Container container)
        {
            return new ActionURL(ManageStudyAction.class, container);
        }

        @Override
        public Class<? extends Controller> getManageStudyClass()
        {
            return ManageStudyAction.class;
        }

        @Override
        public ActionURL getStudyOverviewURL(Container container)
        {
            return new ActionURL(OverviewAction.class, container);
        }

        @Override
        public ActionURL getDatasetURL(Container container, int datasetId)
        {
            return new ActionURL(DatasetAction.class, container).addParameter(Dataset.DATASETKEY, datasetId);
        }

        @Override
        public ActionURL getDatasetsURL(Container container)
        {
            return new ActionURL(DatasetsAction.class, container);
        }

        @Override
        public ActionURL getManageDatasetsURL(Container container)
        {
            return new ActionURL(ManageTypesAction.class, container);
        }

        @Override
        public ActionURL getManageReportPermissions(Container container)
        {
            return new ActionURL(SecurityController.ReportPermissionsAction.class, container);
        }

        @Override
        public ActionURL getManageAssayScheduleURL(Container container, boolean useAlternateLookupFields)
        {
            ActionURL url = new ActionURL(StudyDesignController.ManageAssayScheduleAction.class, container);
            url.addParameter("useAlternateLookupFields", useAlternateLookupFields);
            return url;
        }

        @Override
        public ActionURL getManageTreatmentsURL(Container container, boolean useSingleTableEditor)
        {
            ActionURL url = new ActionURL(StudyDesignController.ManageTreatmentsAction.class, container);
            url.addParameter("singleTable", useSingleTableEditor);
            return url;
        }

        @Override
        public ActionURL getManageFileWatchersURL(Container container)
        {
            return new ActionURL(StudyController.ManageFilewatchersAction.class, container);
        }

        @Override
        public ActionURL getLinkToStudyURL(Container container, ExpSampleType sampleType)
        {
            ActionURL url = new ActionURL(SampleTypePublishStartAction.class, container);
            if (sampleType != null)
                url.addParameter("rowId", sampleType.getRowId());
            return url;
        }

        @Override
        public ActionURL getLinkToStudyURL(Container container, ExpProtocol protocol)
        {
            return urlProvider(AssayUrls.class).getProtocolURL(container, protocol, AssayPublishStartAction.class);
        }

        @Override
        public ActionURL getLinkToStudyConfirmURL(Container container, ExpProtocol protocol)
        {
            return urlProvider(AssayUrls.class).getProtocolURL(container, protocol, AssayPublishConfirmAction.class);
        }

        @Override
        public ActionURL getLinkToStudyConfirmURL(Container container, ExpSampleType sampleType)
        {
            ActionURL url = new ActionURL(SampleTypePublishConfirmAction.class, container);
            if (sampleType != null)
                url.addParameter("rowId", sampleType.getRowId());
            return url;
        }

        @Override
        public void addManageStudyNavTrail(NavTree root, Container container, User user)
        {
            _addManageStudy(root, container, user);
        }

        @Override
        public ActionURL getTypeNotFoundURL(Container container, int datasetId)
        {
            return new ActionURL(TypeNotFoundAction.class, container).addParameter("id", datasetId);
        }
    }

    public StudyController()
    {
        setActionResolver(ACTION_RESOLVER);
    }

    protected void _addNavTrailVisitAdmin(NavTree root)
    {
        _addManageStudy(root);

        StringBuilder sb = new StringBuilder("Manage ");

        Study visitStudy = StudyManager.getInstance().getStudyForVisits(getStudy());
        if (visitStudy != null && visitStudy.getShareVisitDefinitions() == Boolean.TRUE)
            sb.append("Shared ");

        sb.append(getVisitLabelPlural());

        root.addChild(sb.toString(), new ActionURL(ManageVisitsAction.class, getContainer()));
    }

    @RequiresPermission(ReadPermission.class)
    public class BeginAction extends SimpleViewAction
    {
        private Study _study;

        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            _study = getStudy();

            WebPartView overview = StudyModule.manageStudyPartFactory.getWebPartView(getViewContext(), StudyModule.manageStudyPartFactory.createWebPart());
            WebPartView views = StudyModule.reportsPartFactory.getWebPartView(getViewContext(), StudyModule.reportsPartFactory.createWebPart());
			return new VBox(overview, views);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild(_study == null ? "No Study In Folder" : _study.getLabel());
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class DefineDatasetTypeAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            getStudyRedirectIfNull();
            return ModuleHtmlView.get(ModuleLoader.getInstance().getModule("core"), ModuleHtmlView.getGeneratedViewPath("datasetDesigner"));
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("createDataset");
            _addNavTrailDatasetAdmin(root);
            root.addChild("Create Dataset Definition");
        }
    }

    @Marshal(Marshaller.Jackson)
    @RequiresPermission(ReadPermission.class)
    public class GetDatasetAction extends ReadOnlyApiAction<DatasetForm>
    {
        @Override
        public Object execute(DatasetForm form, BindException errors) throws Exception
        {
            DatasetDomainKindProperties properties = DatasetManager.get().getDatasetDomainKindProperties(getContainer(), form.getDatasetId());
            if (properties != null)
                return properties;
            else
                throw new NotFoundException("Dataset does not exist in this container for datasetId " + form.getDatasetIdStr() + ".");
        }
    }

    @RequiresPermission(AdminPermission.class)
    @SuppressWarnings("unchecked")
    public class EditTypeAction extends SimpleViewAction<DatasetForm>
    {
        private Dataset _def;

        @Override
        public ModelAndView getView(DatasetForm form, BindException errors)
        {
            StudyImpl study = getStudyRedirectIfNull();
            if (null == form.getDatasetId())
                throw new NotFoundException("No datasetId parameter provided.");

            DatasetDefinition def = study.getDataset(form.getDatasetId());
            _def = def;
            if (null == def)
                throw new NotFoundException("No dataset found for datasetId " + form.getDatasetId() + ".");

            if (!def.canUpdateDefinition(getUser()))
            {
                ActionURL details = new ActionURL(DatasetDetailsAction.class,getContainer()).addParameter("id",def.getDatasetId());
                throw new RedirectException(details);
            }

            if (null == def.getTypeURI())
            {
                def = def.createMutable();
                String domainURI = StudyManager.getInstance().getDomainURI(study.getContainer(), getUser(), def);
                OntologyManager.ensureDomainDescriptor(domainURI, def.getName(), study.getContainer());
                def.setTypeURI(domainURI);
            }

            return ModuleHtmlView.get(ModuleLoader.getInstance().getModule("core"), ModuleHtmlView.getGeneratedViewPath("datasetDesigner"));
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("datasetProperties");
            _addNavTrailDatasetAdmin(root);
            root.addChild(_def.getName(), new ActionURL(DatasetDetailsAction.class, getContainer()).addParameter("id", _def.getDatasetId()));
            root.addChild("Edit Dataset Definition");
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class DatasetDetailsAction extends SimpleViewAction<IdForm>
    {
        private DatasetDefinition _def;

        @Override
        public ModelAndView getView(IdForm form, BindException errors)
        {
            _def = StudyManager.getInstance().getDatasetDefinition(getStudyRedirectIfNull(), form.getId());
            if (_def == null)
            {
                throw new NotFoundException("Invalid Dataset ID");
            }
            return  new StudyJspView<>(StudyManager.getInstance().getStudy(getContainer()),
                    "/org/labkey/study/view/datasetDetails.jsp", _def, errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("datasetProperties");
            _addNavTrailDatasetAdmin(root);
            root.addChild(_def.getLabel() + " Dataset Properties");
        }
    }

    public static class DatasetFilterForm extends QueryViewAction.QueryExportForm implements HasViewContext
    {
        private ViewContext _viewContext;

        @Override
        public void setViewContext(ViewContext context)
        {
            _viewContext = context;
        }

        @Override
        public ViewContext getViewContext()
        {
            return _viewContext;
        }
    }


    public static class OverviewForm extends DatasetFilterForm
    {
        private String _qcState;
        private String[] _visitStatistic = new String[0];

        public String getQCState()
        {
            return _qcState;
        }

        public void setQCState(String qcState)
        {
            _qcState = qcState;
        }

        public String[] getVisitStatistic()
        {
            return _visitStatistic;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setVisitStatistic(String[] visitStatistic)
        {
            _visitStatistic = visitStatistic;
        }

        private Set<VisitStatistic> getVisitStatistics()
        {
            Set<VisitStatistic> set = EnumSet.noneOf(VisitStatistic.class);

            for (String statName : _visitStatistic)
                set.add(VisitStatistic.valueOf(statName));

            if (set.isEmpty())
                set.add(VisitStatistic.values()[0]);

            return set;
        }
    }


    @RequiresPermission(ReadPermission.class)
    public class OverviewAction extends SimpleViewAction<OverviewForm>
    {
        private StudyImpl _study;

        @Override
        public ModelAndView getView(OverviewForm form, BindException errors) throws Exception
        {
            _study = getStudyRedirectIfNull();
            OverviewBean bean = new OverviewBean();
            bean.study = _study;
            bean.showAll = "1".equals(getViewContext().get("showAll"));
            bean.canManage = getContainer().hasPermission(getUser(), ManageStudyPermission.class);
            bean.showCohorts = StudyManager.getInstance().showCohorts(getContainer(), getUser());
            bean.stats = form.getVisitStatistics();
            bean.showSpecimens = SpecimenService.get() != null;

            if (QCStateManager.getInstance().showStates(getContainer()))
                bean.qcStates = QCStateSet.getSelectedStates(getContainer(), form.getQCState());

            if (!bean.showCohorts)
                bean.cohortFilter = null;
            else
                bean.cohortFilter = CohortFilterFactory.getFromURL(getContainer(), getUser(), getViewContext().getActionURL(), DatasetQueryView.DATAREGION);

            VisitManager visitManager = StudyManager.getInstance().getVisitManager(bean.study);
            bean.visitMapSummary = visitManager.getVisitSummary(getUser(), bean.cohortFilter, bean.qcStates, bean.stats, bean.showAll);

            return new StudyJspView<>(_study, "/org/labkey/study/view/overview.jsp", bean, errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("studyDashboard#navigator");
            root.addChild("Overview: " + _study.getLabel());
        }
    }


    static class QueryReportForm extends QueryViewAction.QueryExportForm
    {
        ReportIdentifier _reportId;

        public ReportIdentifier getReportId()
        {
            return _reportId;
        }

        public void setReportId(ReportIdentifier reportId)
        {
            _reportId = reportId;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class QueryReportAction extends QueryViewAction<QueryReportForm, QueryView>
    {
        protected Report _report;

        public QueryReportAction()
        {
            super(QueryReportForm.class);
        }

        @Override
        protected ModelAndView getHtmlView(QueryReportForm form, BindException errors) throws Exception
        {
            Report report = getReport(form);

            if (report != null)
                return report.getRunReportView(getViewContext());
            else
                throw new NotFoundException("Unable to locate the requested report: " + form.getReportId());
        }

        @Override
        protected QueryView createQueryView(QueryReportForm form, BindException errors, boolean forExport, String dataRegion) throws Exception
        {
            Report report = getReport(form);
            if (report instanceof QueryReport)
                return ((QueryReport)report).getQueryViewGenerator().generateQueryView(getViewContext(), report.getDescriptor());

            return null;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            if (_report != null)
                root.addChild(_report.getDescriptor().getReportName());
            else
                root.addChild("Study Query Report");
        }

        protected Report getReport(QueryReportForm form)
        {
            if (_report == null)
            {
                ReportIdentifier identifier = form.getReportId();
                if (identifier != null)
                    _report = identifier.getReport(getViewContext());
            }
            return _report;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class DatasetReportAction extends QueryReportAction
    {
        @Override
        protected Report getReport(QueryReportForm form)
        {
            if (_report == null)
            {
                String reportId = (String)getViewContext().get(DATASET_REPORT_ID_PARAMETER_NAME);

                ReportIdentifier identifier = ReportService.get().getReportIdentifier(reportId, getViewContext().getUser(), getViewContext().getContainer());
                if (identifier != null)
                    _report = identifier.getReport(getViewContext());
            }
            return _report;
        }

        @Override
        protected ModelAndView getHtmlView(QueryReportForm form, BindException errors) throws Exception
        {
            ViewContext context = getViewContext();
            Report report = getReport(form);

            // is not a report (either the default grid view or a custom view)...
            if (report == null)
            {
                return HttpView.redirect(createRedirectURLfrom(DatasetAction.class, context));
            }

            int datasetId = NumberUtils.toInt((String)context.get(Dataset.DATASETKEY), -1);
            Dataset def = StudyManager.getInstance().getDatasetDefinition(getStudyRedirectIfNull(), datasetId);

            if (def != null)
            {
                ActionURL url = getViewContext().cloneActionURL().setAction(StudyController.DatasetAction.class).
                                        replaceParameter(DATASET_REPORT_ID_PARAMETER_NAME, report.getDescriptor().getReportId().toString()).
                                        replaceParameter(Dataset.DATASETKEY, def.getDatasetId());

                return HttpView.redirect(url);
            }
            else if (ReportManager.get().canReadReport(getUser(), getContainer(), report))
                return report.getRunReportView(getViewContext());
            else
                return new HtmlView("User does not have read permission on this report.");
        }
    }

    private ActionURL createRedirectURLfrom(Class<? extends Controller> action, ViewContext context)
    {
        ActionURL newUrl = new ActionURL(action, context.getContainer());
        return newUrl.addParameters(context.getActionURL().getParameters());
    }

    @RequiresPermission(ReadPermission.class)
    public class DatasetAction extends QueryViewAction<DatasetFilterForm, QueryView>
    {
        private CohortFilter _cohortFilter;
        private int _visitId;
        private DatasetDefinition _def;

        public DatasetAction()
        {
            super(DatasetFilterForm.class);
        }

        private DatasetDefinition getDatasetDefinition()
        {
            if (null == _def)
            {
                Object datasetKeyObject = getViewContext().get(Dataset.DATASETKEY);
                if (datasetKeyObject instanceof List)
                {
                    // bug 7365: It's been specified twice -- once in the POST, once in the GET. Just need one of them.
                    List<?> list = (List<?>)datasetKeyObject;
                    datasetKeyObject = list.get(0);
                }
                if (null != datasetKeyObject)
                {
                    try
                    {
                        int id = NumberUtils.toInt(String.valueOf(datasetKeyObject), 0);
                        _def = StudyManager.getInstance().getDatasetDefinition(getStudyRedirectIfNull(), id);
                    }
                    catch (ConversionException x)
                    {
                        throw new NotFoundException();
                    }
                }
                else
                {
                    String entityId = (String)getViewContext().get("entityId");
                    if (null != entityId)
                        _def = StudyManager.getInstance().getDatasetDefinitionByEntityId(getStudyRedirectIfNull(), entityId);
                }
            }
            if (null == _def)
                throw new NotFoundException();
            return _def;
        }

        @Override
        public ModelAndView getView(DatasetFilterForm form, BindException errors) throws Exception
        {
            ActionURL url = getViewContext().getActionURL();
            String viewName = url.getParameter(DATASET_VIEW_NAME_PARAMETER_NAME);


            // if the view name refers to a report id (legacy style), redirect to use the newer report id parameter
            if (NumberUtils.isDigits(viewName))
            {
                // one last check to see if there is a view with that name before trying to redirect to the report
                DatasetDefinition def = getDatasetDefinition();

                if (def != null &&
                    QueryService.get().getCustomView(getUser(), getContainer(), getUser(), StudySchema.getInstance().getSchemaName(), def.getName(), viewName) == null)
                {
                    ReportIdentifier reportId = AbstractReportIdentifier.fromString(viewName, getViewContext().getUser(), getViewContext().getContainer());
                    if (reportId != null && reportId.getReport(getViewContext()) != null)
                    {
                        ActionURL newURL = url.clone().deleteParameter(DATASET_VIEW_NAME_PARAMETER_NAME).
                                addParameter(DATASET_REPORT_ID_PARAMETER_NAME, reportId.toString());
                        return HttpView.redirect(newURL);
                    }
                }
            }
            return super.getView(form, errors);
        }

        @Override
        protected ModelAndView getHtmlView(DatasetFilterForm form, BindException errors) throws Exception
        {
            // the full resultset is a join of all datasets for each participant
            // each dataset is determined by a visitid/datasetid
            Study study = getStudyRedirectIfNull();
            ViewContext context = getViewContext();

            String export = StringUtils.trimToNull(context.getActionURL().getParameter("export"));

            String viewName = (String)context.get(DATASET_VIEW_NAME_PARAMETER_NAME);
            DatasetDefinition def = getDatasetDefinition();
            if (null == def)
                return new TypeNotFoundAction().getView(form, errors);
            String typeURI = def.getTypeURI();
            if (null == typeURI)
                return new TypeNotFoundAction().getView(form, errors);

            _visitId = NumberUtils.toInt((String)context.get(VisitImpl.VISITKEY), 0);
            VisitImpl visit;
            if (_visitId != 0)
            {
                assert study.getTimepointType() != TimepointType.CONTINUOUS;
                visit = StudyManager.getInstance().getVisitForRowId(study, _visitId);
                if (null == visit)
                    throw new NotFoundException();
            }

            boolean showEditLinks = !QueryService.get().isQuerySnapshot(getContainer(), StudySchema.getInstance().getSchemaName(), def.getName()) &&
                !def.isPublishedData();

            UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), StudyQuerySchema.SCHEMA_NAME);
            DatasetQuerySettings settings = (DatasetQuerySettings)schema.getSettings(getViewContext(), DatasetQueryView.DATAREGION, def.getName());

            settings.setShowEditLinks(showEditLinks);
            settings.setShowSourceLinks(true);

            QueryView queryView = schema.createView(getViewContext(), settings, errors);
            if (queryView instanceof StudyQueryView)
                _cohortFilter = ((StudyQueryView)queryView).getCohortFilter();

            final ActionURL url = context.getActionURL();

            // clear the property map cache and the sort map cache
            getParticipantPropsMap(context).clear();
            getDatasetSortColumnMap(context).clear();

            final TableInfo table = queryView.getTable();
            if (table != null)
            {
                setColumnURL(url, queryView, schema, def);

                // Clear any cached participant lists, since the filter/sort may have changed
                String qcParam = QCStateSet.getQCParameter(DatasetQueryView.DATAREGION, url);
                removeParticipantListFromCache(context, def.getDatasetId(), viewName, _cohortFilter, qcParam != null ? url.getParameter(qcParam) : null);
                getExpandedState(context, def.getDatasetId()).clear();
            }

            if (null != export)
            {
                if ("tsv".equals(export))
                    queryView.exportToTsv(context.getResponse());
                else if ("xls".equals(export))
                    queryView.exportToExcel(context.getResponse());
                return null;
            }

            StringBuilder sb = new StringBuilder();
            if (def.getDescription() != null && def.getDescription().length() > 0)
                sb.append(PageFlowUtil.filter(def.getDescription(), true, true)).append("<br/>");
            if (_cohortFilter != null)
                sb.append("<br/><span><b>Cohort :</b> ").append(filter(_cohortFilter.getDescription(getContainer(), getUser()))).append("</span>");

            if (QCStateManager.getInstance().showStates(getContainer()))
            {
                String publicQCUrlFilterValue = getQCUrlFilterValue(QCStateSet.getPublicStates(getContainer()));
                String privateQCUrlFilterValue = getQCUrlFilterValue(QCStateSet.getPrivateStates(getContainer()));

                for (QCStateSet set : QCStateSet.getSelectableSets(getContainer()))
                {
                    String selectedQCLabel = selectedQCStateLabelFromUrl(getViewContext().getActionURL(), settings.getDataRegionName(), set.getLabel(), publicQCUrlFilterValue, privateQCUrlFilterValue);
                    if (selectedQCLabel != null && selectedQCLabel.equals(set.getLabel()))
                    {
                        sb.append("<br/><span><b>QC States:</b> ").append(filter(set.getLabel())).append("</span>");
                        break;
                    }
                }
            }
            if (ReportPropsManager.get().getPropertyValue(def.getEntityId(), getContainer(), "refreshDate") != null)
            {
                sb.append("<br/><span><b>Data Cut Date:</b> ");
                Object refreshDate = (ReportPropsManager.get().getPropertyValue(def.getEntityId(), getContainer(), "refreshDate"));
                if (refreshDate instanceof Date)
                {
                    sb.append(filter(DateUtil.formatDate(getContainer(), (Date)refreshDate)));
                }
                else
                {
                    sb.append(ReportPropsManager.get().getPropertyValue(def.getEntityId(), getContainer(), "refreshDate").toString());
                }
            }
            HtmlView header = new HtmlView(sb.toString());
            VBox view = new VBox(header, queryView);

            String status = (String)ReportPropsManager.get().getPropertyValue(def.getEntityId(), getContainer(), "status");
            if (status != null)
            {
                // inject the dataset status marker class, but it is up to the client to style the page accordingly
                String statusCls = "labkey-dataset-status-" + PageFlowUtil.filter(status.toLowerCase());
                HtmlView scriptLock = new HtmlView("<script type=\"text/javascript\">(function($) { $(LABKEY.DataRegions['Dataset'].form).addClass(" + PageFlowUtil.jsString(statusCls) + "); })(jQuery);</script>");
                view.addView(scriptLock);
            }

            Report report = queryView.getSettings().getReportView(context);
            if (report != null && !ReportManager.get().canReadReport(getUser(), getContainer(), report))
            {
                return new HtmlView("User does not have read permission on this report.");
            }
            else if (report == null && !def.canRead(getUser()))
            {
                return new HtmlView("User does not have read permission on this dataset.");
            }
            else if (DiscussionService.get() != null)
            {
                // add discussions
                DiscussionService service = DiscussionService.get();

                if (report != null)
                {
                    // discuss the report
                    String title = "Discuss report - " + report.getDescriptor().getReportName();
                    HttpView discussion = service.getDiscussionArea(getViewContext(), report.getEntityId(), getViewContext().getActionURL(), title, true, false);
                    if (discussion != null)
                        view.addView(discussion);
                }
                else
                {
                    // discuss the dataset
                    String title = "Discuss dataset - " + def.getLabel();
                    HttpView discussion = service.getDiscussionArea(getViewContext(), def.getEntityId(), getViewContext().getActionURL(), title, true, false);
                    if (discussion != null)
                        view.addView(discussion);
                }
            }
            return view;
        }

        @Override
        protected QueryView createQueryView(DatasetFilterForm datasetFilterForm, BindException errors, boolean forExport, String dataRegion) throws Exception
        {
            QuerySettings qs = new QuerySettings(getViewContext(), DATASET_DATAREGION_NAME);
            Report report = qs.getReportView(getViewContext());
            if (report instanceof QueryReport)
            {
                return ((QueryReport)report).getQueryViewGenerator().generateQueryView(getViewContext(), report.getDescriptor());
            }
            return null;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("gridBasics");
            _addNavTrail(root, getDatasetDefinition().getDatasetId(), _visitId, _cohortFilter);
        }
    }


    @RequiresNoPermission
    public class ExpandStateNotifyAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            final ActionURL url = getViewContext().getActionURL();
            final String collapse = url.getParameter("collapse");
            final int datasetId = NumberUtils.toInt(url.getParameter(Dataset.DATASETKEY), -1);
            final int id = NumberUtils.toInt(url.getParameter("id"), -1);

            if (datasetId != -1 && id != -1)
            {
                Map<Integer, String> expandedMap = getExpandedState(getViewContext(), id);
                // collapse param is only set on a collapse action
                if (collapse != null)
                    expandedMap.put(datasetId, "collapse");
                else
                    expandedMap.put(datasetId, "expand");
            }
            return null;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }


    Participant findParticipant(Study study, String particpantId) throws StudyManager.ParticipantNotUniqueException
    {
        Participant participant = StudyManager.getInstance().getParticipant(study, particpantId);
        if (participant == null)
        {
            if (study.isDataspaceStudy())
            {
                Container c = StudyManager.getInstance().findParticipant(study, particpantId);
                Study s = null == c ? null : StudyManager.getInstance().getStudy(c);
                if (null != s && c.hasPermission(getUser(), ReadPermission.class))
                {
                    participant = StudyManager.getInstance().getParticipant(s, particpantId);
                }
            }
        }
        return participant;
    }


    @RequiresPermission(ReadPermission.class)
    public class ParticipantAction extends SimpleViewAction<ParticipantForm>
    {
        private ParticipantForm _bean;
        private CohortFilter _cohortFilter;

        @Override
        public ModelAndView getView(ParticipantForm form, BindException errors)
        {
            Study study = getStudyRedirectIfNull();
            _bean = form;
            ActionURL previousParticipantURL = null;
            ActionURL nextParticipantURL = null;

            if (form.getParticipantId() == null)
            {
                throw new NotFoundException("No " + study.getSubjectNounSingular() + " specified");
            }

            Participant participant = null;
            try
            {
                participant = findParticipant(study, form.getParticipantId());
                if (null == participant)
                    throw new NotFoundException("Could not find " + study.getSubjectNounSingular() + " " + form.getParticipantId());
            }
            catch (StudyManager.ParticipantNotUniqueException x)
            {
                return new HtmlView(PageFlowUtil.filter(x.getMessage()));
            }

            String viewName = (String) getViewContext().get(DATASET_VIEW_NAME_PARAMETER_NAME);

            _cohortFilter = CohortFilterFactory.getFromURL(getContainer(), getUser(), getViewContext().getActionURL(), DatasetQueryView.DATAREGION);
            // display the next and previous buttons only if we have a cached participant index
            if (_cohortFilter != null && !StudyManager.getInstance().showCohorts(getContainer(), getUser()))
                throw new UnauthorizedException("User does not have permission to view cohort information");

            final ActionURL url = getViewContext().getActionURL();
            String qcParam = QCStateSet.getQCParameter(DatasetQueryView.DATAREGION, url);
            List<String> participants = getParticipantListFromCache(getViewContext(), form.getDatasetId(), viewName,
                    _cohortFilter, qcParam != null ? url.getParameter(qcParam) : null);

            if (participants != null)
            {
                if (isDebug())
                {
                    _log.info("Cached participants: " + participants);
                }
                int idx = participants.indexOf(form.getParticipantId());
                if (idx != -1)
                {
                    if (idx > 0)
                    {
                        final String ptid = participants.get(idx-1);
                        previousParticipantURL = getViewContext().cloneActionURL();
                        previousParticipantURL.replaceParameter("participantId", ptid);
                    }

                    if (idx < participants.size()-1)
                    {
                        final String ptid = participants.get(idx+1);
                        nextParticipantURL = getViewContext().cloneActionURL();
                        nextParticipantURL.replaceParameter("participantId", ptid);
                    }
                }
            }

            VBox vbox = new VBox();
            ParticipantNavView navView = new ParticipantNavView(previousParticipantURL, nextParticipantURL, form.getParticipantId(), null);
            vbox.addView(navView);

            CustomParticipantView customParticipantView = StudyManager.getInstance().getCustomParticipantView(study);
            if (customParticipantView != null && customParticipantView.isActive())
            {
                // issue : 18595 chrome will complain that the script we are executing matches the script sent down in the request
                getViewContext().getResponse().setHeader("X-XSS-Protection", "0");

                vbox.addView(customParticipantView.getView());
            }
            else
            {
                ModelAndView characteristicsView = StudyManager.getInstance().getParticipantDemographicsView(getContainer(), form, errors);
                ModelAndView dataView = StudyManager.getInstance().getParticipantView(getContainer(), form, errors);
                vbox.addView(characteristicsView);
                vbox.addView(dataView);
            }

            return vbox;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("participantViews");
            _addNavTrail(root, _bean.getDatasetId(), 0, _cohortFilter);
            root.addChild(StudyService.get().getSubjectNounSingular(getContainer()) + " - " + id(_bean.getParticipantId()));
        }
    }


    public static class Participant2Form
    {
        String participantId;

        public String getParticipantId()
        {
            return participantId;
        }

        public void setParticipantId(String participantId)
        {
            this.participantId = participantId;
        }
    }


    @RequiresPermission(ReadPermission.class)
    public class Participant2Action extends SimpleViewAction<ParticipantForm>
    {
        // TODO participant list support? cohortfilter support?
        // TODO define participant context
//        {
//            particpantId:"",
//            participantGroup:""
//            demoMode:false
//        }


        @Override
        public ModelAndView getView(ParticipantForm form, BindException errors) throws Exception
        {
            ViewContext context = getViewContext();
            Study study = getStudyRedirectIfNull();
            ActionURL previousParticipantURL = null;
            ActionURL nextParticipantURL = null;

            if (form.getParticipantId() == null)
            {
                throw new NotFoundException("No " + study.getSubjectNounSingular() + " specified");
            }

            Participant participant = null;
            try
            {
                participant = findParticipant(study, form.getParticipantId());
                if (null == participant)
                    throw new NotFoundException("Could not find " + study.getSubjectNounSingular() + " " + form.getParticipantId());
            }
            catch (StudyManager.ParticipantNotUniqueException x)
            {
                return new HtmlView(PageFlowUtil.filter(x.getMessage()));
            }

            PageConfig page = getPageConfig();

            // add participant to view context for java/jsp based web parts
            context.put(Participant.class.getName(), participant);
            // add to javascript context for file based web parts
            page.getPortalContext().put("participantId", participant.getParticipantId());

            String pageId = Participant.class.getName();
            boolean canCustomize = context.getContainer().hasPermission("populatePortalView",context.getUser(), AdminPermission.class);

            HttpView template = PageConfig.Template.Home.getTemplate(getViewContext(), new VBox(), page);
            int parts = Portal.populatePortalView(getViewContext(), pageId, template, isPrint(), canCustomize, false, true, Portal.STUDY_PARTICIPANT_PORTAL_PAGE);

            if (parts == 0 && canCustomize)
            {
                // TODO: make webparts out of default views and actually save portal config
//                ParticipantAction pa = new ParticipantAction();
//                pa.setViewContext(context);
//                ModelAndView v = pa.getView(form, errors);
//                Portal.addViewToRegion(template, WebPartFactory.LOCATION_BODY, (HttpView)v);

                // force page admin mode
                template = PageConfig.Template.Home.getTemplate(getViewContext(), new VBox(), page);
                Portal.populatePortalView(getViewContext(), pageId, template, isPrint(), canCustomize, true, true, Participant.class.getName());

            }

            getPageConfig().setTemplate(PageConfig.Template.None);
            return template;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }



    // Obfuscate the passed in test if this user is in "demo" mode in this container
    private String id(String id)
    {
        return id(id, getContainer(), getUser());
    }

    // Obfuscate the passed in test if this user is in "demo" mode in this container
    private static String id(String id, Container c, User user)
    {
        return DemoMode.id(id, c, user);
    }


    @RequiresPermission(AdminPermission.class)
    public class ImportVisitMapAction extends FormViewAction<ImportVisitMapForm>
    {
        @Override
        public ModelAndView getView(ImportVisitMapForm form, boolean reshow, BindException errors)
        {
            StudyImpl study = getStudyThrowIfNull();
            redirectToSharedVisitStudy(study, getViewContext().getActionURL());
            return new StudyJspView<>(getStudyRedirectIfNull(), "/org/labkey/study/view/importVisitMap.jsp", null, errors);
        }

        @Override
        public void validateCommand(ImportVisitMapForm form, Errors errors)
        {
        }

        @Override
        public boolean handlePost(ImportVisitMapForm form, BindException errors) throws Exception
        {
            VisitMapImporter importer = new VisitMapImporter();
            List<String> errorMsg = new LinkedList<>();
            if (!importer.process(getUser(), getStudyThrowIfNull(), form.getContent(), VisitMapImporter.Format.Xml, errorMsg, _log))
            {
                for (String error : errorMsg)
                    errors.reject("uploadVisitMap", error);
                return false;
            }
            return true;
        }

        @Override
        public ActionURL getSuccessURL(ImportVisitMapForm form)
        {
            return new ActionURL(BeginAction.class, getContainer());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("importVisitMap");
            _addNavTrailVisitAdmin(root);
            root.addChild("Import Visit Map");
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class CreateStudyAction extends FormViewAction<StudyPropertiesForm>
    {
        @Override
        public ModelAndView getView(StudyPropertiesForm form, boolean reshow, BindException errors) throws Exception
        {
            if (null != getStudy())
            {
                BeginAction action = (BeginAction)initAction(this, new BeginAction());
                return action.getView(form, errors);
            }
            // Set default values for the form
            if (form.getLabel() == null)
            {
                form.setLabel(HttpView.currentContext().getContainer().getName() + " Study");
            }
            if (form.getStartDate() == null)
            {
                form.setStartDate(new Date());
            }
            if (form.getDefaultTimepointDuration() == 0)
            {
                form.setDefaultTimepointDuration(1);
            }
            // NOTE: should be a better way to do this (e.g. get the correct value in the form/backend to begin with)
            Study sharedStudy = getStudy(getContainer().getProject());
            if (sharedStudy != null && sharedStudy.getShareVisitDefinitions() == Boolean.TRUE)
            {
                form.setShareVisits(sharedStudy.getShareVisitDefinitions());
                form.setTimepointType(sharedStudy.getTimepointType());
                form.setStartDate(sharedStudy.getStartDate());
                form.setDefaultTimepointDuration(sharedStudy.getDefaultTimepointDuration());
            }
            return new StudyJspView<>(null, "/org/labkey/study/view/createStudy.jsp", form, errors);
        }

        @Override
        public void validateCommand(StudyPropertiesForm target, Errors errors)
        {
            if (target.getTimepointType() == TimepointType.DATE && null == target.getStartDate())
                errors.reject(ERROR_MSG, "Start date must be supplied for a date-based study.");

            target.setLabel(StringUtils.trimToNull(target.getLabel()));
            if (null == target.getLabel())
                errors.reject(ERROR_MSG, "Please supply a label");

            if (!StudyService.get().isValidSubjectColumnName(getContainer(), target.getSubjectColumnName()))
                errors.reject(ERROR_MSG, "\"" + target.getSubjectColumnName() + "\" is not a valid subject column name.");

            if (!StudyService.get().isValidSubjectNounSingular(getContainer(), target.getSubjectNounSingular()))
                errors.reject(ERROR_MSG, "\"" + target.getSubjectNounSingular() + "\" is not a valid singular subject noun.");

            // For now, apply the same check to the plural noun as to the singular- there rules should be exactly the same.
            if (!StudyService.get().isValidSubjectNounSingular(getContainer(), target.getSubjectNounPlural()))
                errors.reject(ERROR_MSG, "\"" + target.getSubjectNounPlural() + "\" is not a valid plural subject noun.");
        }

        @Override
        public boolean handlePost(StudyPropertiesForm form, BindException errors)
        {
            createStudy(getStudy(), getContainer(), getUser(), form);
            return true;
        }

        @Override
        public ActionURL getSuccessURL(StudyPropertiesForm form)
        {
            return form.getSuccessActionURL(new ActionURL(ManageStudyAction.class, getContainer()));
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Create Study");
        }
    }

    public static StudyImpl createStudy(@Nullable StudyImpl study, Container c, User user, StudyPropertiesForm form)
    {
        if (null == study)
        {
            study = new StudyImpl(c, form.getLabel());
            study.setTimepointType(form.getTimepointType());
            study.setStartDate(form.getStartDate());
            study.setEndDate(form.getEndDate());
            study.setSecurityType(form.getSecurityType());
            study.setSubjectNounSingular(form.getSubjectNounSingular());
            study.setSubjectNounPlural(form.getSubjectNounPlural());
            study.setSubjectColumnName(form.getSubjectColumnName());
            study.setAssayPlan(form.getAssayPlan());
            study.setDescription(form.getDescription());
            study.setDefaultTimepointDuration(form.getDefaultTimepointDuration() < 1 ? 1 : form.getDefaultTimepointDuration());
            if (form.getDescriptionRendererType() != null)
                study.setDescriptionRendererType(form.getDescriptionRendererType());
            study.setGrant(form.getGrant());
            study.setInvestigator(form.getInvestigator());
            study.setSpecies(form.getSpecies());
            study.setAlternateIdPrefix(form.getAlternateIdPrefix());
            study.setAlternateIdDigits(form.getAlternateIdDigits());
            study.setAllowReqLocRepository(form.isAllowReqLocRepository());
            study.setAllowReqLocClinic(form.isAllowReqLocClinic());
            study.setAllowReqLocSal(form.isAllowReqLocSal());
            study.setAllowReqLocEndpoint(form.isAllowReqLocEndpoint());
            if (c.isProject())
            {
                study.setShareDatasetDefinitions(form.isShareDatasets());
                study.setShareVisitDefinitions(form.isShareVisits());
            }

            study = StudyManager.getInstance().createStudy(user, study);
            RequestabilityManager.getInstance().setDefaultRules(c, user);
        }
        return study;
    }

    @RequiresPermission(ManageStudyPermission.class)
    public class ManageStudyAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            return new StudyJspView<>(getStudy(), "/org/labkey/study/view/manageStudy.jsp", null, errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("manageStudy");
            _addManageStudy(root);
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class DeleteStudyAction extends FormViewAction<DeleteStudyForm>
    {
        @Override
        public void validateCommand(DeleteStudyForm form, Errors errors)
        {
            if (!form.isConfirm())
                errors.reject("deleteStudy", "Need to confirm Study deletion");
        }

        @Override
        public ModelAndView getView(DeleteStudyForm form, boolean reshow, BindException errors)
        {
            return new StudyJspView<>(getStudyRedirectIfNull(), "/org/labkey/study/view/confirmDeleteStudy.jsp", null, errors);
        }

        @Override
        public boolean handlePost(DeleteStudyForm form, BindException errors)
        {
            StudyManager.getInstance().deleteAllStudyData(getContainer(), getUser());
            return true;
        }

        @Override
        public ActionURL getSuccessURL(DeleteStudyForm deleteStudyForm)
        {
            return getContainer().getFolderType().getStartURL(getContainer(), getUser());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Confirm Delete Study");
        }
    }

    public static class DeleteStudyForm
    {
        private boolean confirm;

        public boolean isConfirm()
        {
            return confirm;
        }

        public void setConfirm(boolean confirm)
        {
            this.confirm = confirm;
        }
    }

    public static class RemoveProtocolDocumentForm
    {
        private String _name;

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class RemoveProtocolDocumentAction extends FormHandlerAction<RemoveProtocolDocumentForm>
    {
        @Override
        public void validateCommand(RemoveProtocolDocumentForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(RemoveProtocolDocumentForm removeProtocolDocumentForm, BindException errors) throws Exception
        {
            Study study = getStudyThrowIfNull();
            study.removeProtocolDocument(removeProtocolDocumentForm.getName(), getUser());
            return true;
        }

        @Override
        public URLHelper getSuccessURL(RemoveProtocolDocumentForm removeProtocolDocumentForm)
        {
            return new ActionURL(ManageStudyPropertiesAction.class, getContainer());
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ManageStudyPropertiesAction extends FormApiAction<TableViewForm>
    {
        @Override
        protected @NotNull TableViewForm getCommand(HttpServletRequest request)
        {
            User user = getUser();
            UserSchema schema = QueryService.get().getUserSchema(user, getContainer(), SchemaKey.fromParts(StudyQuerySchema.SCHEMA_NAME));
            TableViewForm form = new TableViewForm(schema.getTable("StudyProperties"));
            form.setViewContext(getViewContext());
            return form;
        }

        @Override
        public ModelAndView getView(TableViewForm form, BindException errors)
        {
            Study study = getStudy();
            if (null == study)
                throw new RedirectException(new ActionURL(CreateStudyAction.class, getContainer()));
            return new StudyJspView<>(getStudy(), "/org/labkey/study/view/manageStudyPropertiesExt.jsp", study, null);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("manageStudy");
            _addManageStudy(root);
            root.addChild("Study Properties");
        }

        @Override
        public void validateForm(TableViewForm form, Errors errors)
        {
            // Issue 43898: Validate that the subject column name is not a user defined field in one of the datasets
            String subjectColName = form.get("SubjectColumnName");
            if (null != subjectColName)
            {
                Study study = StudyService.get().getStudy(getContainer());
                if (null != study)
                {
                    for (Dataset dataset : study.getDatasets())
                    {
                        Domain domain = dataset.getDomain();
                        if (null != domain)
                        {
                            for (DomainProperty property : domain.getProperties())
                            {
                                if (property.getName().equalsIgnoreCase(subjectColName))
                                {
                                    errors.reject(ERROR_MSG, "Cannot set Subject Column Name to a user defined dataset field. " + subjectColName + " is already defined in " + dataset.getName() + ". ");
                                    return;
                                }
                            }
                        }
                    }
                }
            }
        }

        @Override
        public ApiResponse execute(TableViewForm form, BindException errors) throws Exception
        {
            if (!getContainer().hasPermission(getUser(),AdminPermission.class))
                throw new UnauthorizedException();

            Map<String,Object> values = form.getTypedValues();
            values.put("container", getContainer().getId());

            TableInfo studyProperties = form.getTable();
            QueryUpdateService qus = studyProperties.getUpdateService();
            if (null == qus)
                throw new UnauthorizedException();
            try (DbScope.Transaction transaction = studyProperties.getSchema().getScope().ensureTransaction())
            {
                qus.updateRows(getUser(), getContainer(), Collections.singletonList(values), Collections.singletonList(values), null, null);
                List<AttachmentFile> files = getAttachmentFileList();
                getStudyThrowIfNull().attachProtocolDocument(files, getUser());
                transaction.commit();
            }
            catch (BatchValidationException x)
            {
                x.addToErrors(errors);
                return null;
            }
            catch (AttachmentService.DuplicateFilenameException x)
            {
                JSONObject json = new JSONObject();
                json.put("failure", true);
                json.put("msg", x.getMessage());
                return new ApiSimpleResponse(json);
            }

            JSONObject json = new JSONObject();
            json.put("success", true);
            return new ApiSimpleResponse(json);
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class ManageVisitsAction extends FormViewAction<StudyPropertiesForm>
    {
        @Override
        public void validateCommand(StudyPropertiesForm target, Errors errors)
        {
            if (target.getTimepointType() == TimepointType.DATE && null == target.getStartDate())
                errors.reject(ERROR_MSG, "Start date must be supplied for a date-based study.");
            if (target.getDefaultTimepointDuration() < 1)
                errors.reject(ERROR_MSG, "Default timepoint duration must be a positive number.");
        }

        @Override
        public ModelAndView getView(StudyPropertiesForm form, boolean reshow, BindException errors) throws Exception
        {
            StudyImpl study = getStudy();
            if (null == study)
            {
                CreateStudyAction action = (CreateStudyAction)initAction(this, new CreateStudyAction());
                return action.getView(form, false, errors);
            }

            if (study.getTimepointType() == TimepointType.CONTINUOUS)
                return new HtmlView("<span class='labkey-error'>Unsupported operation for continuous study</span>");

            redirectToSharedVisitStudy(study, getViewContext().getActionURL());

            return new StudyJspView<>(study, _jspName(study), form, errors);
        }

        @Override
        public boolean handlePost(StudyPropertiesForm form, BindException errors)
        {
            StudyImpl study = getStudyThrowIfNull().createMutable();
            redirectToSharedVisitStudy(study, getViewContext().getActionURL());

            study.setStartDate(form.getStartDate());
            study.setDefaultTimepointDuration(form.getDefaultTimepointDuration());
            StudyManager.getInstance().updateStudy(getUser(), study);

            return true;
        }

        @Override
        public ActionURL getSuccessURL(StudyPropertiesForm studyPropertiesForm)
        {
            return new ActionURL(ManageStudyAction.class, getContainer());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("manageVisits");
            _addNavTrailVisitAdmin(root);
        }

        private String _jspName(Study study)
        {
            assert study.getTimepointType() != TimepointType.CONTINUOUS;
            return study.getTimepointType() == TimepointType.DATE ? "/org/labkey/study/view/manageTimepoints.jsp" : "/org/labkey/study/view/manageVisits.jsp";
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class ManageTypesAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            return new StudyJspView<>(getStudyRedirectIfNull(), "/org/labkey/study/view/manageTypes.jsp", this, errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("manageDatasets");
            _addManageStudy(root);
            root.addChild("Manage Datasets");
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class ManageFilewatchersAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            return new StudyJspView<>(getStudyRedirectIfNull(), "/org/labkey/study/view/manageFilewatchers.jsp", this, errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("fileWatcher");
            _addManageStudy(root);
            root.addChild("Manage File Watchers");
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class ManageLocationsAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), StudyQuerySchema.SCHEMA_NAME);
            QuerySettings settings = schema.getSettings(getViewContext(), QueryView.DATAREGIONNAME_DEFAULT, StudyQuerySchema.LOCATION_TABLE_NAME);

            return schema.createView(getViewContext(), settings, errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("manageLocations");
            _addManageStudy(root);
            root.addChild("Manage Locations");
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class DeleteAllUnusedLocationsAction extends ConfirmAction<LocationForm>
    {
        @Override
        public ModelAndView getConfirmView(LocationForm form, BindException errors)
        {
            List<String> temp = new ArrayList<>();
            for (Container c : getContainers(form))
            {
                if (c.hasPermission(getUser(), AdminPermission.class))
                {
                    LocationManager mgr = LocationManager.get();
                    for (LocationImpl loc : mgr.getLocations(c))
                    {
                        if (!mgr.isLocationInUse(loc))
                        {
                            temp.add(c.getName() + "/" + loc.getLabel());
                        }
                    }
                }
            }
            String[] labels = new String[temp.size()];
            for(int i = 0; i<temp.size(); i++)
            {
                labels[i] = temp.get(i);
            }
            form.setLabels(labels);
            return new JspView<>("/org/labkey/study/view/confirmDeleteLocation.jsp", form, errors);
        }

        @Override
        public boolean handlePost(LocationForm form, BindException errors) throws Exception
        {
            for (Container c : getContainers(form))
            {
                if (c.hasPermission(getUser(), AdminPermission.class))
                {
                    LocationManager mgr = LocationManager.get();
                    for (LocationImpl loc : mgr.getLocations(c))
                    {
                        if (!mgr.isLocationInUse(loc))
                        {
                            mgr.deleteLocation(loc);
                        }
                    }
                }
            }
            return true;
        }

        @Override
        public void validateCommand(LocationForm locationEditForm, Errors errors)
        {
        }

        @NotNull
        @Override
        public URLHelper getSuccessURL(LocationForm form)
        {
            return form.getReturnURLHelper();
        }

        private Collection<Container> getContainers(LocationForm form)
        {
            String containerFilterName = form.getContainerFilter();

            if (null != containerFilterName)
                return LocationTable.getStudyContainers(getContainer(), ContainerFilter.getContainerFilterByName(form.getContainerFilter(), getContainer(), getUser()));
            else
                return Collections.singleton(getContainer());
        }
    }

    public static class LocationForm extends ViewForm
    {
        private int[] _ids;
        private String[] _labels;
        private String _containerFilter;
        public String[] getLabels()
        {
            return _labels;
        }

        public void setLabels(String[] labels)
        {
            _labels = labels;
        }

        public int[] getIds()
        {
            return _ids;
        }

        public void setIds(int[] ids)
        {
            _ids = ids;
        }

        public String getContainerFilter()
        {
            return _containerFilter;
        }

        public void setContainerFilter(String containerFilter)
        {
            _containerFilter = containerFilter;
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class VisitSummaryAction extends FormViewAction<VisitForm>
    {
        private VisitImpl _v;

        @Override
        public void validateCommand(VisitForm target, Errors errors)
        {
            StudyImpl study = getStudyRedirectIfNull();
            if (study.getTimepointType() == TimepointType.CONTINUOUS)
                errors.reject(null, "Unsupported operation for continuous date study");

            Study sharedStudy = StudyManager.getInstance().getSharedStudy(study);
            if (sharedStudy != null && sharedStudy.getShareVisitDefinitions())
                errors.reject(null, "Can't edit visits in a study with shared visits");

            target.validate(errors, study);
            if (errors.getErrorCount() > 0)
                return;

            VisitImpl visitBean = target.getBean();

            //check for overlapping visits that the target num is within the range
            VisitManager visitMgr = StudyManager.getInstance().getVisitManager(study);
            if (null != visitMgr)
            {
                if (visitMgr.isVisitOverlapping(visitBean))
                    errors.reject(null, "Visit range overlaps with an existing visit in this study. Please enter a different range.");
            }
        }

        @Override
        public ModelAndView getView(VisitForm form, boolean reshow, BindException errors)
        {
            StudyImpl study = getStudyRedirectIfNull();
            if (study.getTimepointType() == TimepointType.CONTINUOUS)
                return new HtmlView("<span class='labkey-error'>Unsupported operation for continuous date study</span>");

            redirectToSharedVisitStudy(study, getViewContext().getActionURL());

            int id = NumberUtils.toInt((String)getViewContext().get("id"));
            _v = StudyManager.getInstance().getVisitForRowId(study, id);
            if (_v == null)
            {
                return HttpView.redirect(new ActionURL(BeginAction.class, getContainer()));
            }
            VisitSummaryBean visitSummary = new VisitSummaryBean();
            visitSummary.setVisit(_v);

            return new StudyJspView<>(study, "/org/labkey/study/view/editVisit.jsp", visitSummary, errors);
        }

        @Override
        public boolean handlePost(VisitForm form, BindException errors)
        {
            VisitImpl postedVisit = form.getBean();
            if (!getContainer().getId().equals(postedVisit.getContainer().getId()))
                throw new UnauthorizedException();

            StudyImpl study = getStudyThrowIfNull();
            redirectToSharedVisitStudy(study, getViewContext().getActionURL());

            // UNDONE: how do I get struts to handle this checkbox?
            postedVisit.setShowByDefault(null != StringUtils.trimToNull((String)getViewContext().get("showByDefault")));

            // UNDONE: reshow is broken for this form, but we have to validate
            Collection<VisitImpl> visits = StudyManager.getInstance().getVisitManager(study).getVisits();
            boolean validRange = true;
            // make sure there is no overlapping visit
            for (VisitImpl v : visits)
            {
                if (v.getRowId() == postedVisit.getRowId())
                    continue;
                BigDecimal maxL = v.getSequenceNumMin().max(postedVisit.getSequenceNumMin());
                BigDecimal minR = v.getSequenceNumMax().min(postedVisit.getSequenceNumMax());
                if (maxL.compareTo(minR) <= 0)
                {
                    errors.reject("visitSummary", getVisitLabel() + " range overlaps with '" + v.getDisplayString() + "'");
                    validRange = false;
                }
            }

            if (!validRange)
            {
                return false;
            }

            StudyManager.getInstance().updateVisit(getUser(), postedVisit);

            HashMap<Integer,VisitDatasetType> visitTypeMap = new HashMap<>();
            for (VisitDataset vds :  postedVisit.getVisitDatasets())
                visitTypeMap.put(vds.getDatasetId(), vds.isRequired() ? VisitDatasetType.REQUIRED : VisitDatasetType.OPTIONAL);

            if (form.getDatasetIds() != null)
            {
                for (int i = 0; i < form.getDatasetIds().length; i++)
                {
                    int datasetId = form.getDatasetIds()[i];
                    VisitDatasetType type = VisitDatasetType.valueOf(form.getDatasetStatus()[i]);
                    VisitDatasetType oldType = visitTypeMap.get(datasetId);
                    if (oldType == null)
                        oldType = VisitDatasetType.NOT_ASSOCIATED;
                    if (type != oldType)
                    {
                        StudyManager.getInstance().updateVisitDatasetMapping(getUser(), getContainer(),
                                postedVisit.getRowId(), datasetId, type);
                    }
                }
            }
            return true;
        }

        @Override
        public ActionURL getSuccessURL(VisitForm form)
        {
            return new ActionURL(ManageVisitsAction.class, getContainer());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            _addNavTrailVisitAdmin(root);
            root.addChild(_v.getDisplayString());
        }
    }

    public static class VisitSummaryBean
    {
        private VisitImpl visit;

        public VisitImpl getVisit()
        {
            return visit;
        }

        public void setVisit(VisitImpl visit)
        {
            this.visit = visit;
        }
    }

    @RequiresPermission(ManageStudyPermission.class)
    public class StudyScheduleAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return StudyModule.studyScheduleWebPartFactory.getWebPartView(getViewContext(), StudyModule.studyScheduleWebPartFactory.createWebPart());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("studySchedule");
            _addManageStudy(root);
            root.addChild("Study Schedule");
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class DeleteVisitAction extends FormHandlerAction<IdForm>
    {
        @Override
        public void validateCommand(IdForm target, Errors errors)
        {
            StudyImpl study = getStudyThrowIfNull();
            if (study.getTimepointType() == TimepointType.CONTINUOUS)
                errors.reject(null, "Unsupported operation for continuous date study");


            Study sharedStudy = StudyManager.getInstance().getSharedStudy(study);
            if (sharedStudy != null && sharedStudy.getShareVisitDefinitions())
                errors.reject(null, "Can't edit visits in a study with shared visits");
        }

        @Override
        public boolean handlePost(IdForm form, BindException errors)
        {
            int visitId = form.getId();
            StudyImpl study = getStudyThrowIfNull();

            redirectToSharedVisitStudy(study, getViewContext().getActionURL());

            VisitImpl visit = StudyManager.getInstance().getVisitForRowId(study, visitId);
            if (visit != null)
            {
                StudyManager.getInstance().deleteVisit(study, visit, getUser());
                return true;
            }
            throw new NotFoundException();
        }

        @Override
        public ActionURL getSuccessURL(IdForm idForm)
        {
            return new ActionURL(ManageVisitsAction.class, getContainer());
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class DeleteUnusedVisitsAction extends ConfirmAction<IdForm>
    {
        @Override
        public void validateCommand(IdForm target, Errors errors)
        {
            StudyImpl study = getStudyThrowIfNull();
            if (study.getTimepointType() == TimepointType.CONTINUOUS)
                errors.reject(null, "Unsupported operation for continuous date study");

            Study sharedStudy = StudyManager.getInstance().getSharedStudy(study);
            if (sharedStudy != null && sharedStudy.getShareVisitDefinitions())
                errors.reject(null, "Can't delete visits from a study with shared visits");
        }

        @Override
        public ModelAndView getConfirmView(IdForm idForm, BindException errors)
        {
            if (getPageConfig().getTitle() == null)
                setTitle("Delete Unused Visits");

            StudyImpl study = getStudyThrowIfNull();

            redirectToSharedVisitStudy(study, getViewContext().getActionURL());

            Collection<VisitImpl> visits = getUnusedVisits(study);
            StringBuilder sb = new StringBuilder();

            if (visits.isEmpty())
            {
                sb.append("No unused visits found.<br>");
            }
            else
            {
                // Put them in a table to help with StudyTest verification
                sb.append("<table id=\"visitsToDelete\">\n");
                sb.append("<tr><td>Are you sure you want to delete the unused visits listed below?</td></tr>\n<tr><td>&nbsp;</td></tr>\n");

                for (VisitImpl visit : visits)
                {
                    sb.append("<tr><td>")
                        .append(PageFlowUtil.filter(visit.getLabel()))
                        .append(" (")
                        .append(PageFlowUtil.filter(visit.getSequenceString()))
                        .append(")")
                        .append("</td></tr>\n");
                }

                sb.append("</table>\n");
            }

            return new HtmlView(sb.toString());
        }

        @Override
        public boolean handlePost(IdForm form, BindException errors)
        {
            long start = System.currentTimeMillis();
            StudyImpl study = getStudyThrowIfNull();

            StudyManager.getInstance().deleteVisits(study, getUnusedVisits(study), getUser(), true);

            _log.info("Delete unused visits took: " + DateUtil.formatDuration(System.currentTimeMillis() - start));

            return true;
        }

        private @NotNull Collection<VisitImpl> getUnusedVisits(final StudyImpl study)
        {
            final List<VisitImpl> visits = new LinkedList<>();

            new SqlSelector(StudySchema.getInstance().getSchema(), new SQLFragment
                (
                    "SELECT v.RowId FROM study.Visit v WHERE Container = ? AND NOT EXISTS (SELECT * FROM study.ParticipantVisit pv WHERE pv.Container = ? and pv.VisitRowId = v.RowId)",
                    getContainer(), getContainer()
                )
            ).forEach(Integer.class, rowId -> {
                VisitImpl visit = StudyManager.getInstance().getVisitForRowId(study, rowId);

                if (null != visit)
                    visits.add(visit);
            });

            return visits;
        }

        @Override
        @NotNull
        public ActionURL getSuccessURL(IdForm idForm)
        {
            return new ActionURL(ManageVisitsAction.class, getContainer());
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class BulkDeleteVisitsAction extends FormViewAction<DeleteVisitsForm>
    {
        private TimepointType _timepointType;
        private List<VisitImpl> _visitsToDelete;

        @Override
        public ModelAndView getView(DeleteVisitsForm form, boolean reshow, BindException errors)
        {
            StudyImpl study = getStudyRedirectIfNull();
            _timepointType = study.getTimepointType();

            Study sharedStudy = StudyManager.getInstance().getSharedStudy(study);
            if (sharedStudy != null && sharedStudy.getShareVisitDefinitions())
                return new HtmlView("<span class='labkey-error'>Can't delete visits from a study with shared visits.</span>");

            if (_timepointType == TimepointType.CONTINUOUS)
                return new HtmlView("<span class='labkey-error'>Unsupported operation for continuous study.</span>");

            return new StudyJspView<>(getStudyRedirectIfNull(), "/org/labkey/study/view/bulkVisitDelete.jsp", form, errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            _addNavTrailVisitAdmin(root);
            root.addChild("Delete " + (_timepointType == TimepointType.DATE ? "Timepoints" : "Visits"));
        }

        @Override
        public void validateCommand(DeleteVisitsForm form, Errors errors)
        {
            StudyImpl study = getStudyThrowIfNull();

            Study sharedStudy = StudyManager.getInstance().getSharedStudy(study);
            if (sharedStudy != null && sharedStudy.getShareVisitDefinitions())
            {
                errors.reject(null, "Can't delete visits from a study with shared visits.");
                return;
            }

            int[] visitIds = form.getVisitIds();
            if (visitIds == null || visitIds.length == 0)
            {
                errors.reject(ERROR_MSG, "No " + (_timepointType == TimepointType.DATE ? "timepoints" : "visits") + " selected.");
                return;
            }

            _visitsToDelete = new ArrayList<>();
            for (int id : visitIds)
            {
                VisitImpl visit = StudyManager.getInstance().getVisitForRowId(study, id);
                if (visit == null)
                    errors.reject(ERROR_MSG, "Unable to find visit for id " + id);
                else
                    _visitsToDelete.add(visit);
            }
        }

        @Override
        public boolean handlePost(DeleteVisitsForm form, BindException errors)
        {
            long start = System.currentTimeMillis();
            StudyImpl study = getStudyThrowIfNull();
            StudyManager.getInstance().deleteVisits(study, _visitsToDelete, getUser(), false);
            _log.info("Bulk delete visits took: " + DateUtil.formatDuration(System.currentTimeMillis() - start));
            return true;
        }

        @Override
        public ActionURL getSuccessURL(DeleteVisitsForm form)
        {
            return new ActionURL(ManageVisitsAction.class, getContainer());
        }
    }

    public static class DeleteVisitsForm extends ReturnUrlForm
    {
        private int[] _visitIds;

        public int[] getVisitIds()
        {
            return _visitIds;
        }

        public void setVisitIds(int[] visitIds)
        {
            _visitIds = visitIds;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class ConfirmDeleteVisitAction extends SimpleViewAction<IdForm>
    {
        private VisitImpl _visit;
        private TimepointType _timepointType;

        @Override
        public ModelAndView getView(IdForm form, BindException errors)
        {
            int visitId = form.getId();
            StudyImpl study = getStudyRedirectIfNull();
            _timepointType = study.getTimepointType();

            if (_timepointType == TimepointType.CONTINUOUS)
                return new HtmlView("<span class='labkey-error'>Unsupported operation for continuous study</span>");

            redirectToSharedVisitStudy(study, getViewContext().getActionURL());

            _visit = StudyManager.getInstance().getVisitForRowId(study, visitId);
            if (null == _visit)
                throw new NotFoundException();

            return new StudyJspView<>(study, "/org/labkey/study/view/confirmDeleteVisit.jsp", _visit, errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            String noun = _timepointType == TimepointType.DATE ? "Timepoint" : "Visit";
            root.addChild("Delete " + noun + " -- " + _visit.getDisplayString());
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class CreateVisitAction extends FormViewAction<VisitForm>
    {
        @Override
        public void validateCommand(VisitForm target, Errors errors)
        {
            StudyImpl study = getStudyThrowIfNull();
            if (study.getTimepointType() == TimepointType.CONTINUOUS)
                errors.reject(null, "Unsupported operation for continuous date study");

            Study sharedStudy = StudyManager.getInstance().getSharedStudy(study);
            if (sharedStudy != null && sharedStudy.getShareVisitDefinitions())
                errors.reject(null, "Can't create visits in a study with shared visits");

            target.validate(errors, study);
            if (errors.getErrorCount() > 0)
                return;

            //check for overlapping visits
            VisitManager visitMgr = StudyManager.getInstance().getVisitManager(study);
            if (null != visitMgr)
            {
                if (visitMgr.isVisitOverlapping(target.getBean()))
                    errors.reject(null, "Visit range overlaps with an existing visit in this study. Please enter a different range.");
            }
        }

        @Override
        public ModelAndView getView(VisitForm form, boolean reshow, BindException errors)
        {
            StudyImpl study = getStudyRedirectIfNull();

            if (study.getTimepointType() == TimepointType.CONTINUOUS)
                errors.reject(null, "Unsupported operation for continuous date study");

            redirectToSharedVisitStudy(study, getViewContext().getActionURL());

            form.setReshow(reshow);
            return new StudyJspView<>(study, "/org/labkey/study/view/createVisit.jsp", form, errors);
        }

        @Override
        public boolean handlePost(VisitForm form, BindException errors)
        {
            VisitImpl visit = form.getBean();
            if (visit != null)
                StudyManager.getInstance().createVisit(getStudyThrowIfNull(), getUser(), visit);
            return true;
        }

        @Override
        public ActionURL getSuccessURL(VisitForm visitForm)
        {
            return visitForm.getReturnActionURL();
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            _addNavTrailVisitAdmin(root);
            root.addChild("Create New " + getVisitLabel());
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class UpdateDatasetVisitMappingAction extends FormViewAction<DatasetForm>
    {
        private DatasetDefinition _def;

        @Override
        public void validateCommand(DatasetForm form, Errors errors)
        {
            if (null == form.getDatasetId() || form.getDatasetId() < 1)
            {
                errors.reject(SpringActionController.ERROR_MSG, "DatasetId must be a positive integer.");
            }
            else
            {
                _def = StudyManager.getInstance().getDatasetDefinition(getStudyRedirectIfNull(), form.getDatasetId());
                if (null == _def)
                    errors.reject(SpringActionController.ERROR_MSG, "Dataset not found.");
            }
        }

        @Override
        public ModelAndView getView(DatasetForm form, boolean reshow, BindException errors) throws Exception
        {
            validateCommand(form, errors);

            if (errors.hasErrors())
            {
                getPageConfig().setTemplate(PageConfig.Template.Dialog);
                return new SimpleErrorView(errors);
            }

            return new JspView<>("/org/labkey/study/view/updateDatasetVisitMapping.jsp", _def, errors);
        }

        @Override
        public boolean handlePost(DatasetForm form, BindException errors)
        {
            DatasetDefinition modified = _def.createMutable();
            if (null != form.getVisitRowIds())
            {
                for (int i = 0; i < form.getVisitRowIds().length; i++)
                {
                    int visitRowId = form.getVisitRowIds()[i];
                    VisitDatasetType type = VisitDatasetType.valueOf(form.getVisitStatus()[i]);
                    if (modified.getVisitType(visitRowId) != type)
                    {
                        StudyManager.getInstance().updateVisitDatasetMapping(getUser(), getContainer(),
                                visitRowId, form.getDatasetId(), type);
                    }
                }
            }
            return true;
        }

        @Override
        public ActionURL getSuccessURL(DatasetForm datasetForm)
        {
            return new ActionURL(DatasetDetailsAction.class, getContainer()).addParameter("id", datasetForm.getDatasetId());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            _addNavTrailDatasetAdmin(root);
            if (_def != null)
            {
                VisitManager visitManager = StudyManager.getInstance().getVisitManager(getStudyThrowIfNull());
                root.addChild("Edit " + _def.getLabel() + " " + visitManager.getPluralLabel());
            }
        }
    }


    @RequiresPermission(InsertPermission.class)
    public class ImportAction extends AbstractQueryImportAction<ImportDatasetForm>
    {
        private ImportDatasetForm _form = null;
        private StudyImpl _study = null;
        private DatasetDefinition _def = null;

        @Override
        protected void initRequest(ImportDatasetForm form) throws ServletException
        {
            _form = form;
            _study = getStudyRedirectIfNull();

            if ((_study.getParticipantAliasDatasetId() != null) && (_study.getParticipantAliasDatasetId() == form.getDatasetId()))
            {
                super.setImportMessage("This is the Alias Dataset.  You do not need to include information for the date column.");
            }

            _def = StudyManager.getInstance().getDatasetDefinition(_study, form.getDatasetId());
            if (null == _def && null != form.getName())
                _def = StudyManager.getInstance().getDatasetDefinitionByName(_study, form.getName());
            if (null == _def)
               throw new NotFoundException("Dataset not found");
            if (null == _def.getTypeURI())
                return;

            User user = getUser();
            // Go through normal getTable() codepath to be sure all metadata is applied
            TableInfo t = StudyQuerySchema.createSchema(_study, user, true).getTable(_def.getName(), null);
            if (t == null)
                throw new NotFoundException("Dataset not found");
            setTarget(t);

            if (!t.hasPermission(user, InsertPermission.class) && getUser().isGuest())
                throw new UnauthorizedException();
        }

        @Override
        protected boolean canInsert(User user)
        {
            return _def.canInsert(user);
        }

        @Override
        protected boolean canUpdate(User user)
        {
            return _def.canUpdate(user);
        }

        @Override
        public ModelAndView getView(ImportDatasetForm form, BindException errors) throws Exception
        {
            initRequest(form);

            // TODO need a shorthand for this check
            if (_def.isShared() && _def.getContainer().equals(_def.getDefinitionContainer()))
                return new HtmlView("Error", HtmlString.of("Cannot insert dataset data in this folder.  Use a sub-study to import data."));

            if (_def.getTypeURI() == null)
                throw new NotFoundException("Dataset is not yet defined.");

            if (null == PipelineService.get().findPipelineRoot(getContainer()))
                return new RequirePipelineView(_study, true, errors);

            setShowImportOptions(true);
            setSuccessMessageSuffix("imported");  //Works for when the merge option is selected (may include updates) vs default "inserted"
            return getDefaultImportView(form, errors);
        }

        @Override
        protected int importData(DataLoader dl, FileStream file, String originalName, BatchValidationException errors, @Nullable AuditBehaviorType auditBehaviorType, @Nullable  TransactionAuditProvider.TransactionAuditEvent auditEvent)
        {
            if (null == PipelineService.get().findPipelineRoot(getContainer()))
            {
                errors.addRowError(new ValidationException("Pipeline file system is not setup."));
                return -1;
            }

            // Allow for mapping of the ParticipantId and Sequence Num (i.e. timepoint column),
            // these are passed in for the "create dataset from a file and import data" case
            Map<String,String> columnMap = new CaseInsensitiveHashMap<>();
            if (null != _form.getParticipantId())
                columnMap.put(_form.getParticipantId(),"ParticipantId");
            if (null != _form.getSequenceNum())
            {
                String column = _def.getDomainKind().getKindName().equalsIgnoreCase(DateDatasetDomainKind.KIND_NAME) ? "Date" : "SequenceNum";
                columnMap.put(_form.getSequenceNum(), column);
            }

            Pair<List<String>, UploadLog> result = StudyPublishManager.getInstance().importDatasetTSV(getUser(), _study, _def, dl, _importLookupByAlternateKey, file, originalName, columnMap, errors, _form.getInsertOption(), auditBehaviorType);

            if (!result.getKey().isEmpty())
            {
                // Log the import
                String comment = "Dataset data imported. " + result.getKey().size() + " rows imported";
                StudyServiceImpl.addDatasetAuditEvent(
                        getUser(), getContainer(), _def, comment, result.getValue());
            }

            return result.getKey().size();
        }

        @Override
        public ActionURL getSuccessURL(ImportDatasetForm form)
        {
            ActionURL url = new ActionURL(DatasetAction.class, getContainer()).
                    addParameter(Dataset.DATASETKEY, form.getDatasetId());
            return url;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild(_study.getLabel(), new ActionURL(BeginAction.class, getContainer()));
            ActionURL datasetURL = new ActionURL(DatasetAction.class, getContainer()).
                    addParameter(Dataset.DATASETKEY, _form.getDatasetId());
            root.addChild(_def.getName(), datasetURL);
            root.addChild("Import Data");
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class BulkImportDataTypesAction extends FormViewAction<BulkImportTypesForm>
    {
        @Override
        public void validateCommand(BulkImportTypesForm target, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(BulkImportTypesForm form, boolean reshow, BindException errors)
        {
            return new StudyJspView<>(getStudyRedirectIfNull(), "/org/labkey/study/view/bulkImportDataTypes.jsp", form, errors);
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean handlePost(BulkImportTypesForm form, BindException errors)
        {
            if (form.getLabelColumn() == null)
                errors.reject(null, "Column containing dataset Label must be identified.");
            if (form.getTypeNameColumn() == null)
                errors.reject(null, "Column containing dataset Name must be identified.");
            if (form.getTypeIdColumn() == null)
                errors.reject(null, "Column containing dataset ID must be identified.");
            if (form.getTsv() == null)
                errors.reject(null, "Type definition is required.");

            if (errors.hasErrors())
                return false;

            _log.warn("DataFax schema definition format is deprecated and scheduled for removal. Contact LabKey immediately if your organization requires this support.");
            SchemaReader reader = new SchemaTsvReader(getStudyThrowIfNull(), form.tsv, form.getLabelColumn(), form.getTypeNameColumn(), form.getTypeIdColumn(), errors);

            ComplianceService complianceService = ComplianceService.get();
            return StudyManager.getInstance().importDatasetSchemas(getStudyThrowIfNull(), getUser(), reader, errors, false, true,
                                                                   null != complianceService ? complianceService.getCurrentActivity(getViewContext()) : null);
        }

        @Override
        public ActionURL getSuccessURL(BulkImportTypesForm bulkImportTypesForm)
        {
            return new ActionURL(ManageTypesAction.class, getContainer());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("DatasetBulkDefinition");
            _addNavTrailDatasetAdmin(root);
            root.addChild("Bulk Import");
        }
    }

    public static class BulkImportTypesForm
    {
        private String typeNameColumn;
        private String labelColumn;
        private String typeIdColumn;
        private String tsv;

        public String getTsv()
        {
            return tsv;
        }

        public void setTsv(String tsv)
        {
            this.tsv = tsv;
        }

        public String getTypeIdColumn()
        {
            return typeIdColumn;
        }

        public void setTypeIdColumn(String typeIdColumn)
        {
            this.typeIdColumn = typeIdColumn;
        }

        public String getTypeNameColumn()
        {
            return typeNameColumn;
        }

        public void setTypeNameColumn(String typeNameColumn)
        {
            this.typeNameColumn = typeNameColumn;
        }

        public String getLabelColumn()
        {
            return labelColumn;
        }

        public void setLabelColumn(String labelColumn)
        {
            this.labelColumn = labelColumn;
        }
    }

    @RequiresPermission(UpdatePermission.class)
    public class ShowUploadHistoryAction extends SimpleViewAction<IdForm>
    {
        String _datasetLabel;

        @Override
        public ModelAndView getView(IdForm form, BindException errors)
        {
            TableInfo tInfo = StudySchema.getInstance().getTableInfoUploadLog();
            DataRegion dr = new DataRegion();
            dr.addColumns(tInfo, "RowId,Created,CreatedBy,Status,Description");
            GridView gv = new GridView(dr, errors);
            DisplayColumn dc = new SimpleDisplayColumn(null) {
                @Override
                public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
                {
                    ActionURL url = new ActionURL(DownloadTsvAction.class, ctx.getContainer()).addParameter("id", String.valueOf(ctx.get("RowId")));
                    out.write(PageFlowUtil.link("Download Data File").href(url).toString());
                }
            };
            dr.addDisplayColumn(dc);

            SimpleFilter filter = SimpleFilter.createContainerFilter(getContainer());
            if (form.getId() != 0)
            {
                filter.addCondition(Dataset.DATASETKEY, form.getId());
                DatasetDefinition dsd = StudyManager.getInstance().getDatasetDefinition(getStudyRedirectIfNull(), form.getId());
                if (dsd != null)
                    _datasetLabel = dsd.getLabel();
            }

            gv.setFilter(filter);
            return gv;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Upload History" + (null != _datasetLabel ? " for " + _datasetLabel : ""));
        }
    }

    @RequiresPermission(UpdatePermission.class)
    public class DownloadTsvAction extends SimpleViewAction<IdForm>
    {
        @Override
        public ModelAndView getView(IdForm form, BindException errors) throws Exception
        {
            UploadLog ul = StudyPublishManager.getInstance().getUploadLog(getContainer(), form.getId());
            PageFlowUtil.streamFile(getViewContext().getResponse(), new File(ul.getFilePath()), true);

            return null;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class DatasetItemDetailsAction extends SimpleViewAction<SourceLsidForm>
    {
        @Override
        public ModelAndView getView(SourceLsidForm form, BindException errors)
        {
            ActionURL url = LsidManager.get().getDisplayURL(form.getSourceLsid());
            if (url == null)
            {
                return new HtmlView("The assay run that produced the data has been deleted.");
            }
            return HttpView.redirect(url);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }

    public static class PublishHistoryDetailsForm
    {
        private @Nullable Integer _protocolId;
        private @Nullable Integer _sampleTypeId;
        private int _datasetId;
        private String _sourceLsid;
        private int _recordCount;

        public Integer getProtocolId()
        {
            return _protocolId;
        }

        public void setProtocolId(Integer protocolId)
        {
            _protocolId = protocolId;
        }

        public Integer getSampleTypeId()
        {
            return _sampleTypeId;
        }

        public void setSampleTypeId(Integer sampleTypeId)
        {
            _sampleTypeId = sampleTypeId;
        }

        public int getDatasetId()
        {
            return _datasetId;
        }

        public void setDatasetId(int datasetId)
        {
            _datasetId = datasetId;
        }

        public String getSourceLsid()
        {
            return _sourceLsid;
        }

        public void setSourceLsid(String sourceLsid)
        {
            _sourceLsid = sourceLsid;
        }

        public int getRecordCount()
        {
            return _recordCount;
        }

        public void setRecordCount(int recordCount)
        {
            _recordCount = recordCount;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class PublishHistoryDetailsAction extends SimpleViewAction<PublishHistoryDetailsForm>
    {
        @Override
        public ModelAndView getView(PublishHistoryDetailsForm form, BindException errors)
        {
            final StudyImpl study = getStudyRedirectIfNull();

            VBox view = new VBox();

            int datasetId = form.getDatasetId();
            final DatasetDefinition def = StudyManager.getInstance().getDatasetDefinition(study, datasetId);

            if (def != null)
            {
                final StudyQuerySchema querySchema = StudyQuerySchema.createSchema(study, getUser(), true);
                DatasetQuerySettings qs = (DatasetQuerySettings)querySchema.getSettings(getViewContext(), DatasetQueryView.DATAREGION, def.getName());

                if (!def.canRead(getUser()))
                {
                    //requiresLogin();
                    view.addView(new HtmlView("User does not have read permission on this dataset."));
                }
                else
                {
                    Integer protocolId = form.getProtocolId();
                    Integer sampleTypeId = form.getSampleTypeId();
                    assert protocolId != null || sampleTypeId != null : "Expected one protocolId or sampleTypeId parameters";
                    String sourceLsid = form.getSourceLsid(); // the assay protocol or sample type LSID
                    int recordCount = form.getRecordCount();

                    ActionURL deleteURL = new ActionURL(DeletePublishedRowsAction.class, getContainer());
                    deleteURL.addParameter("publishSourceId", protocolId != null ? protocolId : sampleTypeId);
                    deleteURL.addParameter("sourceLsid", sourceLsid);
                    final ActionButton deleteRows = new ActionButton(deleteURL, "Recall Rows");

                    deleteRows.setRequiresSelection(true, "Recall selected row of this dataset?", "Recall selected rows of this dataset?");
                    deleteRows.setActionType(ActionButton.Action.POST);
                    deleteRows.setDisplayPermission(DeletePermission.class);

                    PublishedRecordQueryView qv = new PublishedRecordQueryView(querySchema, qs, sourceLsid, def.getPublishSource(),
                            protocolId != null ? protocolId : sampleTypeId, recordCount) {

                        @Override
                        protected void populateButtonBar(DataView view, ButtonBar bar)
                        {
                            bar.add(deleteRows);
                        }
                    };

                    view.addView(qv);
                }
            }
            else
                view.addView(new HtmlView(HtmlString.of("The Dataset does not exist.")));
            return view;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Link to Study History Details");
        }
    }

    @RequiresPermission(DeletePermission.class)
    public class DeletePublishedRowsAction extends FormHandlerAction<DeleteDatasetRowsForm>
    {
        private DatasetDefinition _def;
        private Collection<String> _allLsids;
        private MultiValuedMap<String,Pair<String,Integer>> _sourceLsidToLsidPair;
        private Integer _sourceRowId = null;

        @Override
        public void validateCommand(DeleteDatasetRowsForm target, Errors errors)
        {
            _def = StudyManager.getInstance().getDatasetDefinition(getStudyThrowIfNull(), target.getDatasetId());
            if (_def == null)
                throw new IllegalArgumentException("Could not find a dataset definition for id: " + target.getDatasetId());
            if (!target.isDeleteAllData())
            {
                _allLsids = DataRegionSelection.getSelected(getViewContext(), true);

                if (_allLsids.isEmpty())
                {
                    errors.reject("deletePublishedRows", "No rows were selected");
                }
            }
            else
            {
                _allLsids = StudyManager.getInstance().getDatasetLSIDs(getUser(), _def);
            }

            // Need to handle this by groups of source lsids -- each assay or SampleType container needs logging
            _sourceLsidToLsidPair = new ArrayListValuedHashMap<>();
            List<Integer> rowIds = new ArrayList<>();
            List<Map<String,Object>> data = _def.getDatasetRows(getUser(), _allLsids);

            for (Map<String,Object> row : data)
            {
                String sourceLSID = (String)row.get(StudyPublishService.SOURCE_LSID_PROPERTY_NAME);
                String datasetRowLsid = (String)row.get(StudyPublishService.LSID_PROPERTY_NAME);
                Integer rowId = (Integer)row.get(StudyPublishService.ROWID_PROPERTY_NAME);
                rowIds.add(rowId);
                if (sourceLSID != null && datasetRowLsid != null)
                    _sourceLsidToLsidPair.put(sourceLSID, Pair.of(datasetRowLsid, rowId));

                if (_sourceRowId == null && rowId != null)
                    _sourceRowId = rowId;
            }

            String errorMsg = StudyPublishService.get().checkForLockedLinks(_def, rowIds);
            if (!StringUtils.isEmpty(errorMsg))
                errors.reject(ERROR_MSG, errorMsg);
        }

        @Override
        public boolean handlePost(DeleteDatasetRowsForm form, BindException errors)
        {
            String originalSourceLsid = (String)getViewContext().get("sourceLsid");

            Dataset.PublishSource publishSource = _def.getPublishSource();
            if (form.getPublishSourceId() != null && publishSource != null)
            {
                for (Map.Entry<String, Collection<Pair<String,Integer>>> entry : _sourceLsidToLsidPair.asMap().entrySet())
                {
                    String sourceLsid = entry.getKey();
                    Collection<Pair<String, Integer>> pairs = entry.getValue();
                    Container sourceContainer = publishSource.resolveSourceLsidContainer(sourceLsid, _sourceRowId);
                    if (sourceContainer != null)
                        StudyPublishService.get().addRecallAuditEvent(sourceContainer, getUser(), _def, pairs.size(), pairs);
                }
            }

            _def.deleteDatasetRows(getUser(), _allLsids);

            // if the recall was initiated from link to study details view of the publish source, redirect back to the same view
            if (publishSource != null && originalSourceLsid != null && form.getPublishSourceId() != null)
            {
                Container container = publishSource.resolveSourceLsidContainer(originalSourceLsid, _sourceRowId);
                if (container != null)
                    throw new RedirectException(StudyPublishService.get().getPublishHistory(container, publishSource, form.getPublishSourceId()));
            }
            return true;
        }

        @Override
        public ActionURL getSuccessURL(DeleteDatasetRowsForm form)
        {
            return new ActionURL(DatasetAction.class, getContainer()).
                    addParameter(Dataset.DATASETKEY, form.getDatasetId());
        }
    }

    public static class DeleteDatasetRowsForm
    {
        private int datasetId;
        private boolean deleteAllData;
        private Integer _publishSourceId;

        public int getDatasetId()
        {
            return datasetId;
        }

        public void setDatasetId(int datasetId)
        {
            this.datasetId = datasetId;
        }

        public boolean isDeleteAllData()
        {
            return deleteAllData;
        }

        public void setDeleteAllData(boolean deleteAllData)
        {
            this.deleteAllData = deleteAllData;
        }

        public Integer getPublishSourceId()
        {
            return _publishSourceId;
        }

        public void setPublishSourceId(Integer publishSourceId)
        {
            _publishSourceId = publishSourceId;
        }
    }

    // Dataset.canDelete() permissions check is below. This accommodates dataset security, where user might not have delete permission in the folder.
    @RequiresPermission(ReadPermission.class)
    public class DeleteDatasetRowsAction extends FormHandlerAction<DeleteDatasetRowsForm>
    {
        @Override
        public void validateCommand(DeleteDatasetRowsForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(DeleteDatasetRowsForm form, BindException errors) throws Exception
        {
            int datasetId = form.getDatasetId();
            StudyImpl study = getStudyThrowIfNull();
            Dataset dataset = StudyManager.getInstance().getDatasetDefinition(study, datasetId);
            if (null == dataset)
                throw new NotFoundException();

            if (!dataset.canDelete(getUser()))
                throw new UnauthorizedException("User does not have permission to delete rows from this dataset");

            // Operate on each individually for audit logging purposes, but transact the whole thing
            DbScope scope = StudySchema.getInstance().getSchema().getScope();

            try (DbScope.Transaction transaction = scope.ensureTransaction())
            {
                Set<String> lsids = DataRegionSelection.getSelected(getViewContext(), null, false);
                List<Map<String, Object>> keys = new ArrayList<>(lsids.size());
                for (String lsid : lsids)
                    keys.add(Collections.singletonMap("lsid", lsid));

                StudyQuerySchema schema = StudyQuerySchema.createSchema(study, getUser(), true);
                TableInfo datasetTable = schema.createDatasetTableInternal((DatasetDefinition) dataset, null);

                QueryUpdateService qus = datasetTable.getUpdateService();
                assert qus != null;

                qus.deleteRows(getUser(), getContainer(), keys, null, null);

                transaction.commit();
                return true;
            }
            finally
            {
                DataRegionSelection.clearAll(getViewContext(), null);
            }
        }

        @Override
        public ActionURL getSuccessURL(DeleteDatasetRowsForm form)
        {
            return new ActionURL(DatasetAction.class, getContainer()).
                    addParameter(Dataset.DATASETKEY, form.getDatasetId());
        }
    }

    public static class OverviewBean
    {
        public StudyImpl study;
        public Map<VisitMapKey, VisitManager.VisitStatistics> visitMapSummary;
        public boolean showAll;
        public boolean canManage;
        public CohortFilter cohortFilter;
        public boolean showCohorts;
        public QCStateSet qcStates;
        public Set<VisitStatistic> stats;
        public boolean showSpecimens;
    }

    /**
     * Tweak the link url for participant view so that it contains enough information to regenerate
     * the cached list of participants.
     */
    private void setColumnURL(final ActionURL url, final QueryView queryView,
                              final UserSchema querySchema, final Dataset def)
    {
        List<DisplayColumn> columns;
        try
        {
            columns = queryView.getDisplayColumns();
        }
        catch (QueryParseException qpe)
        {
            return;
        }

        // push any filter, sort params, and viewname
        ActionURL base = new ActionURL(ParticipantAction.class, querySchema.getContainer());
        base.addParameter(Dataset.DATASETKEY, Integer.toString(def.getDatasetId()));
        for (Pair<String, String> param : url.getParameters())
        {
            if ((param.getKey().contains(".sort")) ||
                (param.getKey().contains("~")) ||
//                (CohortFilterFactory.isCohortFilterParameterName(param.getKey(), queryView.getDataRegionName())) ||
                (DATASET_VIEW_NAME_PARAMETER_NAME.equals(param.getKey())))
            {
                base.addParameter(param.getKey(), param.getValue());
            }
        }
        if (queryView instanceof StudyQueryView && null != ((StudyQueryView)queryView).getCohortFilter())
            ((StudyQueryView)queryView).getCohortFilter().addURLParameters(getStudyThrowIfNull(), base, null);

        for (DisplayColumn col : columns)
        {
            String subjectColName = StudyService.get().getSubjectColumnName(def.getContainer());
            if (subjectColName.equalsIgnoreCase(col.getName()))
            {
                StringExpression old = col.getURLExpression();
                ContainerContext cc = old instanceof DetailsURL ? ((DetailsURL)old).getContainerContext() : null;
                DetailsURL dets = new DetailsURL(base, "participantId", col.getColumnInfo().getFieldKey());
                dets.setContainerContext(null != cc ? cc : getContainer());
                col.setURLExpression(dets);
            }
        }
    }

    private boolean hasSourceLsids(TableInfo datasetTable)
    {
        SimpleFilter sourceLsidFilter = new SimpleFilter();
        sourceLsidFilter.addCondition(FieldKey.fromParts("SourceLsid"), null, CompareType.NONBLANK);

        return new TableSelector(datasetTable, Collections.singleton("SourceLsid"), sourceLsidFilter, null).exists();
    }

    public static ActionURL getProtocolDocumentDownloadURL(Container c, String name)
    {
        ActionURL url = new ActionURL(ProtocolDocumentDownloadAction.class, c);
        url.addParameter("name", name);

        return url;
    }

    @RequiresPermission(ReadPermission.class)
    public class ProtocolDocumentDownloadAction extends BaseDownloadAction<AttachmentForm>
    {
        @Override
        public @Nullable Pair<AttachmentParent, String> getAttachment(AttachmentForm form)
        {
            StudyImpl study = getStudyRedirectIfNull();
            return new Pair<>(study.getProtocolDocumentAttachmentParent(), form.getName());
        }
    }

    private static final String PARTICIPANT_PROPS_CACHE = "Study_participants/propertyCache";
    private static final String DATASET_SORT_COLUMN_CACHE = "Study_participants/datasetSortColumnCache";
    @SuppressWarnings("unchecked")
    private static Map<String, List<PropertyDescriptor>> getParticipantPropsMap(ViewContext context)
    {
        HttpSession session = context.getRequest().getSession(true);
        Map<String, List<PropertyDescriptor>> map = (Map<String, List<PropertyDescriptor>>) session.getAttribute(PARTICIPANT_PROPS_CACHE);
        if (map == null)
        {
            map = new HashMap<>();
            session.setAttribute(PARTICIPANT_PROPS_CACHE, map);
        }
        return map;
    }

    public static List<PropertyDescriptor> getParticipantPropsFromCache(ViewContext context, String typeURI)
    {
        Map<String, List<PropertyDescriptor>> map = getParticipantPropsMap(context);
        List<PropertyDescriptor> props = map.get(typeURI);
        if (props == null)
        {
            props = OntologyManager.getPropertiesForType(typeURI, context.getContainer());
            map.put(typeURI, props);
        }
        return props;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Integer>> getDatasetSortColumnMap(ViewContext context)
    {
        HttpSession session = context.getRequest().getSession(true);
        Map<String, Map<String, Integer>> map = (Map<String, Map<String, Integer>>) session.getAttribute(DATASET_SORT_COLUMN_CACHE);
        if (map == null)
        {
            map = new HashMap<>();
            session.setAttribute(DATASET_SORT_COLUMN_CACHE, map);
        }
        return map;
    }

    public static @NotNull Map<String, Integer> getSortedColumnList(ViewContext context, Dataset dsd)
    {
        Map<String, Map<String, Integer>> map = getDatasetSortColumnMap(context);
        Map<String, Integer> sortMap = map.get(dsd.getLabel());

        if (sortMap == null)
        {
            QueryDefinition qd = QueryService.get().getQueryDef(context.getUser(), dsd.getContainer(), "study", dsd.getName());
            if (qd == null)
            {
                UserSchema schema = QueryService.get().getUserSchema(context.getUser(), context.getContainer(), "study");
                qd = schema.getQueryDefForTable(dsd.getName());
            }
            CustomView cview = qd.getCustomView(context.getUser(), context.getRequest(), null);
            if (cview != null)
            {
                sortMap = new HashMap<>();
                int i = 0;
                for (FieldKey key : cview.getColumns())
                {
                    final String name = key.toString();
                    if (!sortMap.containsKey(name))
                        sortMap.put(name, i++);
                }
                map.put(dsd.getLabel(), sortMap);
            }
            else
            {
                // there is no custom view for this dataset
                sortMap = Collections.emptyMap();
                map.put(dsd.getLabel(), Collections.emptyMap());
            }
        }
        return new CaseInsensitiveHashMap<>(sortMap);
    }

    private static String getParticipantListCacheKey(int dataset, String viewName, CohortFilter cohortFilter, String encodedQCState)
    {
        String key = Integer.toString(dataset);
        // if there is also a view associated with the dataset, incorporate it into the key as well
        if (viewName != null && !StringUtils.isEmpty(viewName))
            key = key + viewName;
        if (cohortFilter != null)
            key = key + "cohort" + cohortFilter.getCacheKey();
        if (encodedQCState != null)
            key = key + "qcState" + encodedQCState;
        return key;
    }

    public static void removeParticipantListFromCache(ViewContext context, int dataset, String viewName, CohortFilter cohortFilter, String encodedQCState)
    {
        Map<String, List<String>> map = getParticipantMapFromCache(context);
        map.remove(getParticipantListCacheKey(dataset, viewName, cohortFilter, encodedQCState));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> getParticipantMapFromCache(ViewContext context)
    {
        HttpSession session = context.getRequest().getSession(true);
        Map<String, List<String>> map = (Map<String, List<String>>) session.getAttribute(PARTICIPANT_CACHE_PREFIX);
        if (map == null)
        {
            map = new HashMap<>();
            session.setAttribute(PARTICIPANT_CACHE_PREFIX, map);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    public static Map<Integer, String> getExpandedState(ViewContext viewContext, int datasetId)
    {
        HttpSession session = viewContext.getRequest().getSession(true);
        Map<Integer, Map<Integer, String>> map = (Map<Integer, Map<Integer, String>>) session.getAttribute(EXPAND_CONTAINERS_KEY);
        if (map == null)
        {
            map = new HashMap<>();
            session.setAttribute(EXPAND_CONTAINERS_KEY, map);
        }

        Map<Integer, String> expandedMap = map.get(datasetId);
        if (expandedMap == null)
        {
            expandedMap = new HashMap<>();
            map.put(datasetId, expandedMap);
        }
        return expandedMap;
    }

    public static List<String> getParticipantListFromCache(ViewContext context, int dataset, String viewName, CohortFilter cohortFilter, String encodedQCState)
    {
        Map<String, List<String>> map = getParticipantMapFromCache(context);
        String key = getParticipantListCacheKey(dataset, viewName, cohortFilter, encodedQCState);
        List<String> plist = map.get(key);
        if (plist == null)
        {
            // not in cache, or session expired, try to regenerate the list
            plist = generateParticipantListFromURL(context, dataset, viewName, cohortFilter);
            map.put(key, plist);
        }
        return plist;
    }

    private static List<String> generateParticipantListFromURL(ViewContext context, int dataset, String viewName, CohortFilter cohortFilter)
    {
        try
        {
            final StudyManager studyMgr = StudyManager.getInstance();
            final StudyImpl study = studyMgr.getStudy(context.getContainer());

            DatasetDefinition def = studyMgr.getDatasetDefinition(study, dataset);
            if (null == def)
                return Collections.emptyList();
            String typeURI = def.getTypeURI();
            if (null == typeURI)
                return Collections.emptyList();

            int visitRowId = null == context.get(VisitImpl.VISITKEY) ? 0 : Integer.parseInt((String) context.get(VisitImpl.VISITKEY));
            if (visitRowId != 0)
            {
                VisitImpl visit = studyMgr.getVisitForRowId(study, visitRowId);
                if (null == visit)
                    return Collections.emptyList();
            }

            StudyQuerySchema querySchema = StudyQuerySchema.createSchema(study, context.getUser(), true);
            QuerySettings qs = querySchema.getSettings(context, DatasetQueryView.DATAREGION, def.getName());
            qs.setViewName(viewName);

            QueryView queryView = querySchema.createView(context, qs, null);

            return generateParticipantList(queryView);
        }
        catch (Exception e)
        {
            _log.error(e);
        }
        return Collections.emptyList();
    }

    public static List<String> generateParticipantList(QueryView queryView)
    {
        final TableInfo table = queryView.getTable();

        if (table != null)
        {
            try
            {
                // Do a single-column query to get the list of participants that match the filter criteria for this
                // dataset
                FieldKey ptidKey = FieldKey.fromParts(StudyService.get().getSubjectColumnName(queryView.getContainer()));
                Map<FieldKey, ColumnInfo> columns = QueryService.get().getColumns(table, Collections.singleton(ptidKey));
                ColumnInfo ptidColumnInfo = columns.get(ptidKey);
                // Don't bother unless we actually found the participant column (we always should)
                if (ptidColumnInfo != null)
                {
                    // Go through the RenderContext directly to get the ResultSet so that we don't also end up calculating
                    // row counts or other aggregates we don't care about
                    DataView dataView = queryView.createDataView();
                    RenderContext ctx = dataView.getRenderContext();
                    DataRegion dataRegion = dataView.getDataRegion();
                    queryView.getSettings().setShowRows(ShowRows.ALL);
                    try (Results results = ctx.getResults(columns, dataRegion.getDisplayColumns(), table, queryView.getSettings(), dataRegion.getQueryParameters(), Table.ALL_ROWS, dataRegion.getOffset(), dataRegion.getName(), false))
                    {
                        int ptidIndex = results.findColumn(ptidColumnInfo.getAlias());

                        Set<String> participantSet = new LinkedHashSet<>();
                        while (results.next() && ptidIndex > 0)
                        {
                            String ptid = results.getString(ptidIndex);
                            participantSet.add(ptid);
                        }

                        return new ArrayList<>(participantSet);
                    }
                }
            }
            catch (Exception x)
            {
                throw new RuntimeException(x);
            }
        }
        return Collections.emptyList();
    }

    public class ManageQCStatesBean extends AbstractManageQCStatesBean
    {
        ManageQCStatesBean(ActionURL returnUrl)
        {
            super(returnUrl);
            _qcStateHandler = new StudyQCStateHandler();
            _manageAction = new ManageQCStatesAction();
            _deleteAction = DeleteQCStateAction.class;
            _noun = "dataset";
            _dataNoun = "study";
        }
    }

    public static class ManageQCStatesForm extends AbstractManageDataStatesForm
    {
        private Integer _defaultPipelineQCState;
        private Integer _defaultPublishDataQCState;
        private Integer _defaultDirectEntryQCState;
        private boolean _showPrivateDataByDefault;

        public Integer getDefaultPipelineQCState()
        {
            return _defaultPipelineQCState;
        }

        public void setDefaultPipelineQCState(Integer defaultPipelineQCState)
        {
            _defaultPipelineQCState = defaultPipelineQCState;
        }

        public Integer getDefaultPublishDataQCState()
        {
            return _defaultPublishDataQCState;
        }

        public void setDefaultPublishDataQCState(Integer defaultPublishDataQCState)
        {
            _defaultPublishDataQCState = defaultPublishDataQCState;
        }

        public Integer getDefaultDirectEntryQCState()
        {
            return _defaultDirectEntryQCState;
        }

        public void setDefaultDirectEntryQCState(Integer defaultDirectEntryQCState)
        {
            _defaultDirectEntryQCState = defaultDirectEntryQCState;
        }

        public boolean isShowPrivateDataByDefault()
        {
            return _showPrivateDataByDefault;
        }

        public void setShowPrivateDataByDefault(boolean showPrivateDataByDefault)
        {
            _showPrivateDataByDefault = showPrivateDataByDefault;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class ManageQCStatesAction extends AbstractManageQCStatesAction<ManageQCStatesForm>
    {
        public ManageQCStatesAction()
        {
            super(new StudyQCStateHandler(), ManageQCStatesForm.class);
        }

        @Override
        public boolean hasQcStateDefaultsPanel()
        {
            return true;
        }

        @Override
        public boolean hasDataVisibilityPanel()
        {
            return true;
        }

        @Override
        public ModelAndView getView(ManageQCStatesForm manageQCStatesForm, boolean reshow, BindException errors)
        {
            return new JspView<>("/org/labkey/api/qc/view/manageQCStates.jsp",
                    new ManageQCStatesBean(manageQCStatesForm.getReturnActionURL()), errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("manageQC");
            _addManageStudy(root);
            root.addChild("Manage Dataset QC States");
        }

        @Override
        public URLHelper getSuccessURL(ManageQCStatesForm manageQCStatesForm)
        {
            ActionURL successUrl = getSuccessURL(manageQCStatesForm, ManageQCStatesAction.class, ManageStudyAction.class);
            if (!manageQCStatesForm.isReshowPage() && !manageQCStatesForm.isShowPrivateDataByDefault())
                return getQCStateFilteredURL(successUrl, PUBLIC_STATES_LABEL, DATASET_DATAREGION_NAME, getContainer());

            return successUrl;
        }

        @Override
        public String getQcStateDefaultsPanel(Container container, DataStateHandler qcStateHandler)
        {
            _study = StudyController.getStudyThrowIfNull(container);

            StringBuilder panelHtml = new StringBuilder();
            panelHtml.append("  <table class=\"lk-fields-table\">");
            panelHtml.append("      <tr>");
            panelHtml.append("          <td colspan=\"2\">These settings allow different default QC states depending on data source.");
            panelHtml.append("              If set, all imported data without an explicit QC state will have the selected state automatically assigned.</td>");
            panelHtml.append("      </tr>");
            panelHtml.append("      <tr>");
            panelHtml.append("          <th align=\"right\" width=\"300px\">Pipeline imported datasets:</th>");
            panelHtml.append(getQcStateHtml(container, qcStateHandler, "defaultPipelineQCState", _study.getDefaultPipelineQCState()));
            panelHtml.append("      </tr>");
            panelHtml.append("      <tr>");
            panelHtml.append("          <th align=\"right\" width=\"300px\">Data linked to this study:</th>");
            panelHtml.append(getQcStateHtml(container, qcStateHandler, "defaultPublishDataQCState", _study.getDefaultPublishDataQCState()));
            panelHtml.append("      </tr>");
            panelHtml.append("      <tr>");
            panelHtml.append("          <th align=\"right\" width=\"300px\">Directly inserted/updated dataset data:</th>");
            panelHtml.append(getQcStateHtml(container, qcStateHandler, "defaultDirectEntryQCState", _study.getDefaultDirectEntryQCState()));
            panelHtml.append("      </tr>");
            panelHtml.append("  </table>");

            return panelHtml.toString();
        }

        @Override
        public String getDataVisibilityPanel(Container container, DataStateHandler qcStateHandler)
        {
            StringBuilder panelHtml = new StringBuilder();
            panelHtml.append("  <table class=\"lk-fields-table\">");
            panelHtml.append("      <tr>");
            panelHtml.append("          <td colspan=\"2\">This setting determines whether users see non-public data by default.");
            panelHtml.append("              Users can always explicitly choose to see data in any QC state.</td>");
            panelHtml.append("      </tr>");
            panelHtml.append("      <tr>");
            panelHtml.append("          <th align=\"right\" width=\"300px\">Default visibility:</th>");
            panelHtml.append("          <td>");
            panelHtml.append("              <select name=\"showPrivateDataByDefault\">");
            panelHtml.append("                  <option value=\"false\">Public data</option>");
            String selectedText = (_study.isShowPrivateDataByDefault()) ? " selected" : "";
            panelHtml.append("                  <option value=\"true\"").append(selectedText).append(">All data</option>");
            panelHtml.append("              </select>");
            panelHtml.append("          </td>");
            panelHtml.append("      </tr>");
            panelHtml.append("  </table>");

            return panelHtml.toString();
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class DeleteQCStateAction extends AbstractDeleteDataStateAction
    {
        public DeleteQCStateAction()
        {
            super();
            _dataStateHandler = new StudyQCStateHandler();
        }

        @Override
        public DataStateHandler getDataStateHandler()
        {
            return _dataStateHandler;
        }

        @Override
        public ActionURL getSuccessURL(DeleteDataStateForm form)
        {
            ActionURL returnUrl = new ActionURL(ManageQCStatesAction.class, getContainer());
            if (form.getManageReturnUrl() != null)
                returnUrl.addParameter(ActionURL.Param.returnUrl, form.getManageReturnUrl());
            return returnUrl;
        }
    }

    public static class UpdateQCStateForm
    {
        private String _comments;
        private boolean _update;
        private int _datasetId;
        private String _dataRegionSelectionKey;
        private Integer _newState;
        private DatasetQueryView _queryView;
        private String _dataRegionName;

        public String getComments()
        {
            return _comments;
        }

        public void setComments(String comments)
        {
            _comments = comments;
        }

        public boolean isUpdate()
        {
            return _update;
        }

        public void setUpdate(boolean update)
        {
            _update = update;
        }

        public int getDatasetId()
        {
            return _datasetId;
        }

        public void setDatasetId(int datasetId)
        {
            _datasetId = datasetId;
        }

        public String getDataRegionSelectionKey()
        {
            return _dataRegionSelectionKey;
        }

        public void setDataRegionSelectionKey(String dataRegionSelectionKey)
        {
            _dataRegionSelectionKey = dataRegionSelectionKey;
        }

        public Integer getNewState()
        {
            return _newState;
        }

        public void setNewState(Integer newState)
        {
            _newState = newState;
        }

        public void setQueryView(DatasetQueryView queryView)
        {
            _queryView = queryView;
        }

        public DatasetQueryView getQueryView()
        {
            return _queryView;
        }

        public String getDataRegionName()
        {
            return _dataRegionName;
        }

        public void setDataRegionName(String dataRegionName)
        {
            _dataRegionName = dataRegionName;
        }
    }

    @RequiresPermission(QCAnalystPermission.class)
    public class UpdateQCStateAction extends FormViewAction<UpdateQCStateForm>
    {
        private int _datasetId;

        @Override
        public void validateCommand(UpdateQCStateForm updateQCForm, Errors errors)
        {
            if (updateQCForm.isUpdate())
            {
                if (updateQCForm.getComments() == null || updateQCForm.getComments().length() == 0)
                    errors.reject(null, "Comments are required.");
            }
        }

        @Override
        public ModelAndView getView(UpdateQCStateForm updateQCForm, boolean reshow, BindException errors)
        {
            StudyImpl study = getStudyRedirectIfNull();
            _datasetId = updateQCForm.getDatasetId();
            DatasetDefinition def = StudyManager.getInstance().getDatasetDefinition(study, _datasetId);
            if (def == null)
            {
                throw new NotFoundException("No dataset found for id: " + _datasetId);
            }
            Set<String> lsids = null;
            if (isPost())
                lsids = DataRegionSelection.getSelected(getViewContext(), updateQCForm.getDataRegionSelectionKey(), false);
            if (lsids == null || lsids.isEmpty())
                return new HtmlView("No data rows selected.  " + PageFlowUtil.link("back").href("javascript:back()"));

            StudyQuerySchema querySchema = StudyQuerySchema.createSchema(study, getUser(), true);
            DatasetQuerySettings qs = new DatasetQuerySettings(getViewContext().getBindPropertyValues(), DatasetQueryView.DATAREGION);

            qs.setSchemaName(querySchema.getSchemaName());
            qs.setQueryName(def.getName());
            qs.setMaxRows(Table.ALL_ROWS);
            qs.setShowSourceLinks(false);
            qs.setShowEditLinks(false);

            final Set<String> finalLsids = lsids;

            DatasetQueryView queryView = new DatasetQueryView(querySchema, qs, errors)
            {
                @Override
                public DataView createDataView()
                {
                    DataView view = super.createDataView();
                    view.getDataRegion().setSortable(false);
                    view.getDataRegion().setShowFilters(false);
                    view.getDataRegion().setShowRecordSelectors(false);
                    view.getDataRegion().setShowPagination(false);
                    SimpleFilter filter = (SimpleFilter) view.getRenderContext().getBaseFilter();
                    if (null == filter)
                    {
                        filter = new SimpleFilter();
                        view.getRenderContext().setBaseFilter(filter);
                    }
                    filter.addInClause(FieldKey.fromParts("lsid"), new ArrayList<>(finalLsids));
                    return view;
                }
            };
            queryView.setShowDetailsColumn(false);
            updateQCForm.setQueryView(queryView);
            updateQCForm.setDataRegionSelectionKey(DataRegionSelection.getSelectionKeyFromRequest(getViewContext()));
            updateQCForm.setDataRegionName(queryView.getSettings().getDataRegionName());
            return new JspView<>("/org/labkey/study/view/updateQCState.jsp", updateQCForm, errors);
        }

        @Override
        public boolean handlePost(UpdateQCStateForm updateQCForm, BindException errors)
        {
            if (!updateQCForm.isUpdate())
                return false;
            Set<String> lsids = DataRegionSelection.getSelected(getViewContext(), updateQCForm.getDataRegionSelectionKey(), false);

            DataState newState = null;
            if (updateQCForm.getNewState() != null)
            {
                newState = QCStateManager.getInstance().getStateForRowId(getContainer(), updateQCForm.getNewState());
                if (newState == null)
                {
                    errors.reject(null, "The selected state could not be found.  It may have been deleted from the database.");
                    return false;
                }
            }
            StudyManager.getInstance().updateDataQCState(getContainer(), getUser(),
                    updateQCForm.getDatasetId(), lsids, newState, updateQCForm.getComments());

            // if everything has succeeded, we can clear our saved checkbox state now:
            DataRegionSelection.clearAll(getViewContext(), updateQCForm.getDataRegionSelectionKey());
            return true;
        }

        @Override
        public ActionURL getSuccessURL(UpdateQCStateForm updateQCForm)
        {
            ActionURL url = new ActionURL(DatasetAction.class, getContainer());
            url.addParameter(Dataset.DATASETKEY, updateQCForm.getDatasetId());
            if (updateQCForm.getNewState() != null)
                url.replaceParameter(getQCUrlFilterKey(CompareType.EQUAL, updateQCForm.getDataRegionName()), QCStateManager.getInstance().getStateForRowId(getContainer(), updateQCForm.getNewState().intValue()).getLabel());
            return url;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root = _addNavTrail(root, _datasetId, -1);
            root.addChild("Change QC State");
        }
    }

    public static class ResetPipelinePathForm extends PipelinePathForm
    {
        private String _redirect;

        public String getRedirect()
        {
            return _redirect;
        }

        public void setRedirect(String redirect)
        {
            _redirect = redirect;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class ResetPipelineAction extends FormHandlerAction<ResetPipelinePathForm>
    {
        @Override
        public void validateCommand(ResetPipelinePathForm form, Errors errors)
        {
        }

        @Override
        public boolean handlePost(ResetPipelinePathForm form, BindException errors) throws Exception
        {
            for (File f : form.getValidatedFiles(getContainer()))
            {
                if (f.isFile() && f.getName().endsWith(".lock"))
                {
                    f.delete();
                }
            }
            return true;
        }

        @Override
        public URLHelper getSuccessURL(ResetPipelinePathForm form)
        {
            String redirect = form.getRedirect();
            if (null != redirect)
            {
                try
                {
                    return new URLHelper(redirect);
                }
                catch (URISyntaxException e)
                {
                    _log.warn("ResetPipelineAction redirect string invalid: " + redirect);
                }
            }
            return urlProvider(PipelineStatusUrls.class).urlBegin(getContainer());
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class DefaultDatasetReportAction extends SimpleRedirectAction
    {
        @Override
        public ActionURL getRedirectURL(Object o)
        {
            ViewContext context = getViewContext(); //_study.isShowPrivateDataByDefault()
            Object unparsedDatasetId = context.get(Dataset.DATASETKEY);

            try
            {
                int datasetId = null == unparsedDatasetId ? 0 : Integer.parseInt(unparsedDatasetId.toString());

                ActionURL url = context.cloneActionURL();
                url.setAction(DatasetReportAction.class);

                String defaultView = getDefaultView(context, datasetId);
                if (!StringUtils.isEmpty(defaultView))
                {
                    ReportIdentifier reportId = ReportService.get().getReportIdentifier(defaultView, getViewContext().getUser(), getViewContext().getContainer());
                    if (reportId != null)
                        url.addParameter(DATASET_REPORT_ID_PARAMETER_NAME, defaultView);
                    else
                        url.addParameter(DATASET_VIEW_NAME_PARAMETER_NAME, defaultView);
                }

                if (!"1".equals(url.getParameter("skipDataVisibility")))
                {
                    StudyImpl studyImpl = StudyManager.getInstance().getStudy(getContainer());
                    if (studyImpl != null && !studyImpl.isShowPrivateDataByDefault())
                        url = getQCStateFilteredURL(url, PUBLIC_STATES_LABEL, DATASET_DATAREGION_NAME, getContainer());
                }
                return url;
            }
            catch (NumberFormatException e)
            {
                throw new NotFoundException("No such dataset with ID: " + unparsedDatasetId);
            }
        }
    }



    @RequiresPermission(AdminPermission.class)
    public class ManageUndefinedTypesAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            return new StudyJspView<>(getStudyRedirectIfNull(), "/org/labkey/study/view/manageUndefinedTypes.jsp", o, errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            _addNavTrailDatasetAdmin(root);
            root.addChild("Define Dataset Schemas");
        }
    }

    public static ActionURL getViewPreferencesURL(Container c, int id, String viewName)
    {
        // Issue 26030: we don't distinguish null vs empty string for url parameters.
        // Empty string will be converted to null for beans so "" shouldn't be used as the url param for Default Grid View.
        return new ActionURL(ViewPreferencesAction.class, c).addParameter(Dataset.DATASETKEY, id).addParameter("defaultView", viewName != null ? (viewName.equals("") ? "defaultGrid": viewName) : null);
    }

    public static class ViewPreferencesForm extends DatasetController.DatasetIdForm
    {
        private String _defaultView;

        public String getDefaultView()
        {
            return _defaultView;
        }

        @SuppressWarnings("unused")
        public void setDefaultView(String defaultView)
        {
            _defaultView = "defaultGrid".equals(defaultView) ? "" : defaultView;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ViewPreferencesAction extends FormViewAction<ViewPreferencesForm>
    {
        private StudyImpl _study;
        private Dataset _def;

        private int init(ViewPreferencesForm form)
        {
            int dsid = form.getDatasetId();
            _study = getStudyRedirectIfNull();
            _def = StudyManager.getInstance().getDatasetDefinition(_study, dsid);
            return dsid;
        }

        @Override
        public ModelAndView getView(ViewPreferencesForm form, boolean reshow, BindException errors) throws Exception
        {
            init(form);
            if (_def != null)
            {
                List<Pair<String, String>> views = ReportManager.get().getReportLabelsForDataset(getViewContext(), _def);
                ViewPrefsBean bean = new ViewPrefsBean(views, _def);
                return new StudyJspView<>(_study, "/org/labkey/study/view/viewPreferences.jsp", bean, errors);
            }
            throw new NotFoundException("Invalid dataset ID");
        }

        @Override
        public boolean handlePost(ViewPreferencesForm form, BindException errors) throws Exception
        {
            int dsid = init(form);
            String defaultView = form.getDefaultView();
            if ((_def != null) && (defaultView != null))
            {
                setDefaultView(dsid, defaultView);
                return true;
            }
            throw new NotFoundException("Invalid dataset ID");
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("customViews");

            root.addChild(_study.getLabel(), new ActionURL(BeginAction.class, getContainer()));

            ActionURL datasetURL = getViewContext().getActionURL().clone();
            datasetURL.setAction(DatasetAction.class);

            String label = _def.getLabel() != null ? _def.getLabel() : "" + _def.getDatasetId();
            root.addChild(new NavTree(label, datasetURL));

            root.addChild(new NavTree("View Preferences"));
        }

        @Override
        public URLHelper getSuccessURL(ViewPreferencesForm viewPreferencesForm) { return null; }

        @Override
        public void validateCommand(ViewPreferencesForm target, Errors errors) { }
    }

    @RequiresPermission(AdminPermission.class)
    public class ImportStudyBatchAction extends SimpleViewAction<PipelinePathForm>
    {
        private String path;

        @Override
        public ModelAndView getView(PipelinePathForm form, BindException errors) throws Exception
        {
            Container c = getContainer();

            File definitionFile = form.getValidatedSingleFile(c);
            path = form.getPath();
            if (!path.endsWith("/"))
            {
                path += "/";
            }
            path += definitionFile.getName();

            if (!definitionFile.isFile())
            {
                throw new NotFoundException();
            }

            File lockFile = StudyPipeline.lockForDataset(getStudyRedirectIfNull(), definitionFile);

            if (!definitionFile.canRead())
                errors.reject("importStudyBatch", "Can't read dataset file: " + path);
            if (lockFile.exists())
                errors.reject("importStudyBatch", "Lock file exists.  Delete file before running import. " + lockFile.getName());

            VirtualFile datasetsDir = new FileSystemFile(definitionFile.getParentFile());
            DatasetFileReader reader = new DatasetFileReader(datasetsDir, definitionFile.getName(), getStudyRedirectIfNull());

            if (!errors.hasErrors())
            {
                List<String> parseErrors = new ArrayList<>();
                reader.validate(parseErrors);
                for (String error : parseErrors)
                    errors.reject("importStudyBatch", error);
            }

            return new StudyJspView<>(
                    getStudyRedirectIfNull(), "/org/labkey/study/view/importStudyBatch.jsp", new ImportStudyBatchBean(reader, path), errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild(getStudyRedirectIfNull().getLabel(), new ActionURL(StudyController.BeginAction.class, getContainer()));
            root.addChild("Import Study Batch - " + path);
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class SubmitStudyBatchAction extends FormHandlerAction<PipelinePathForm>
    {
        private ActionURL _successUrl = null;

        @Override
        public void validateCommand(PipelinePathForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(PipelinePathForm form, BindException errors) throws Exception
        {
            Study study = getStudyRedirectIfNull();
            Container c = getContainer();
            String path = form.getPath();
            File f = null;

            PipeRoot root = PipelineService.get().findPipelineRoot(c);
            if (path != null)
            {
                if (root != null)
                    f = root.resolvePath(path);
            }

            try
            {
                if (f != null)
                {
                    VirtualFile datasetsDir = new FileSystemFile(f.getParentFile());
                    DatasetImportUtils.submitStudyBatch(study, datasetsDir, f.getName(), c, getUser(), getViewContext().getActionURL(), root);
                }
                _successUrl = urlProvider(PipelineStatusUrls.class).urlBegin(getContainer());
            }
            catch (DatasetImportUtils.DatasetLockExistsException e)
            {
                ActionURL importURL = new ActionURL(ImportStudyBatchAction.class, getContainer());
                importURL.addParameter("path", form.getPath());
                _successUrl = importURL;
            }

            return true;
        }

        @Override
        public URLHelper getSuccessURL(PipelinePathForm pipelinePathForm)
        {
            return _successUrl;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class ImportStudyFromPipelineAction extends SimpleRedirectAction<PipelinePathForm>
    {
        @Override
        public ActionURL getRedirectURL(PipelinePathForm form)
        {
            Container c = getContainer();
            Path studyPath = form.getValidatedSinglePath(c);
            return urlProvider(PipelineUrls.class).urlStartFolderImport(c, studyPath, true, null, false);
        }

        @Override
        protected ModelAndView getErrorView(Exception e, BindException errors) throws Exception
        {
            try
            {
                throw e;
            }
            catch (ImportException sie)
            {
                errors.reject("studyImport", e.getMessage());
                return new SimpleErrorView(errors);
            }
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class TypeNotFoundAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            return new StudyJspView<StudyImpl>(getStudyRedirectIfNull(), "/org/labkey/study/view/typeNotFound.jsp", null, errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Type Not Found");
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class UpdateParticipantVisitsAction extends FormViewAction
    {
        private int _count;

        @Override
        public void validateCommand(Object target, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(Object o, boolean reshow, BindException errors) throws Exception
        {
            return new HtmlView(
                    "<div>" + _count + " rows were updated.<p/>" +
                            PageFlowUtil.button("Done").href(new ActionURL(ManageVisitsAction.class,getContainer())) +
                            "</div>");
        }

        @Override
        public boolean handlePost(Object o, BindException errors) throws Exception
        {
            StudyManager.getInstance().getVisitManager(getStudyRedirectIfNull()).updateParticipantVisits(getUser(), getStudyRedirectIfNull().getDatasets());

            TableInfo tinfoParticipantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();
            _count = new SqlSelector(StudySchema.getInstance().getSchema(),
                    "SELECT COUNT(VisitDate) FROM " + tinfoParticipantVisit + "\nWHERE Container = ?",
                    getContainer()).getObject(Integer.class);

            return true;
        }

        @Override
        public URLHelper getSuccessURL(Object o)
        {
            return null;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            _addNavTrailVisitAdmin(root);
            root.addChild("Recalculate Visit Dates");
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class VisitOrderAction extends FormViewAction<VisitReorderForm>
    {
        @Override
        public ModelAndView getView(VisitReorderForm reorderForm, boolean reshow, BindException errors)
        {
            StudyImpl study = getStudyRedirectIfNull();
            redirectToSharedVisitStudy(study, getViewContext().getActionURL());

            return new StudyJspView<Object>(study, "/org/labkey/study/view/visitOrder.jsp", reorderForm, errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            _addNavTrailVisitAdmin(root);
            root.addChild("Visit Order");
        }

        @Override
        public void validateCommand(VisitReorderForm target, Errors errors) {}

        private Map<Integer, Integer> getVisitIdToOrderIndex(String orderedIds)
        {
            Map<Integer, Integer> order = null;
            if (orderedIds != null && orderedIds.length() > 0)
            {
                order = new HashMap<>();
                String[] idArray = orderedIds.split(",");
                for (int i = 0; i < idArray.length; i++)
                {
                    int id = Integer.parseInt(idArray[i]);
                    // 1-index display orders, since 0 is the database default, and we'd like to know
                    // that these were set explicitly for all visits:
                    order.put(id, i + 1);
                }
            }
            return order;
        }

        private Map<Integer, Integer> getVisitIdToZeroMap(List<VisitImpl> visits)
        {
            Map<Integer, Integer> order = new HashMap<>();
            for (VisitImpl visit : visits)
                order.put(visit.getRowId(), 0);
            return order;
        }

        @Override
        public boolean handlePost(VisitReorderForm form, BindException errors)
        {
            StudyImpl study = getStudyRedirectIfNull();
            redirectToSharedVisitStudy(study, getViewContext().getActionURL());

            Map<Integer, Integer> displayOrder = null;
            Map<Integer, Integer> chronologicalOrder = null;
            List<VisitImpl> visits = StudyManager.getInstance().getVisits(study, Visit.Order.SEQUENCE_NUM);

            if (form.isExplicitDisplayOrder())
                displayOrder = getVisitIdToOrderIndex(form.getDisplayOrder());
            if (displayOrder == null)
                displayOrder = getVisitIdToZeroMap(visits);

            if (form.isExplicitChronologicalOrder())
                chronologicalOrder = getVisitIdToOrderIndex(form.getChronologicalOrder());
            if (chronologicalOrder == null)
                chronologicalOrder = getVisitIdToZeroMap(visits);

            for (VisitImpl visit : visits)
            {
                // it's possible that a new visit has been created between when the update page was rendered
                // and posted.  This will result in a visit that isn't in our ID maps.  There's no great way
                // to handle this, so we'll just skip setting display/chronological order on these visits for now.
                if (displayOrder.containsKey(visit.getRowId()) && chronologicalOrder.containsKey(visit.getRowId()))
                {
                    int displayIndex = displayOrder.get(visit.getRowId()).intValue();
                    int chronologicalIndex = chronologicalOrder.get(visit.getRowId()).intValue();

                    if (visit.getDisplayOrder() != displayIndex || visit.getChronologicalOrder() != chronologicalIndex)
                    {
                        visit = visit.createMutable();
                        visit.setDisplayOrder(displayIndex);
                        visit.setChronologicalOrder(chronologicalIndex);
                        StudyManager.getInstance().updateVisit(getUser(), visit);
                    }
                }
            }

            // Changing visit order can cause cohort assignments to change when advanced cohort tracking is enabled:
            if (study.isAdvancedCohorts())
                CohortManager.getInstance().updateParticipantCohorts(getUser(), study);
            return true;
        }

        @Override
        public ActionURL getSuccessURL(VisitReorderForm reorderForm)
        {
            return reorderForm.getReturnActionURL();
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class VisitVisibilityAction extends FormViewAction<VisitPropertyForm>
    {
        @Override
        public ModelAndView getView(VisitPropertyForm visitPropertyForm, boolean reshow, BindException errors)
        {
            StudyImpl study = getStudyRedirectIfNull();
            redirectToSharedVisitStudy(study, getViewContext().getActionURL());

            return new StudyJspView<Object>(study, "/org/labkey/study/view/visitVisibility.jsp", visitPropertyForm, errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            _addNavTrailVisitAdmin(root);
            root.addChild("Properties");
        }

        @Override
        public void validateCommand(VisitPropertyForm target, Errors errors) {}

        @Override
        public boolean handlePost(VisitPropertyForm form, BindException errors)
        {
            StudyImpl study = getStudyRedirectIfNull();
            redirectToSharedVisitStudy(study, getViewContext().getActionURL());

            int[] allIds = form.getIds() == null ? new int[0] : form.getIds();
            int[] visibleIds = form.getVisible() == null ? new int[0] : form.getVisible();
            String[] labels = form.getLabel() == null ? new String[0] : form.getLabel();
            String[] typeStrs = form.getExtraData()== null ? new String[0] : form.getExtraData();

            Set<Integer> visible = new HashSet<>(visibleIds.length);
            for (int id : visibleIds)
                visible.add(id);
            if (allIds.length != form.getLabel().length)
                throw new IllegalStateException("Arrays must be the same length.");
            for (int i = 0; i < allIds.length; i++)
            {
                VisitImpl def = StudyManager.getInstance().getVisitForRowId(study, allIds[i]);
                boolean show = visible.contains(allIds[i]);
                String label = (i < labels.length) ? labels[i] : null;
                String typeStr = (i < typeStrs.length) ? typeStrs[i] : null;

                Integer cohortId = null;
                if (form.getCohort() != null && form.getCohort()[i] != -1)
                    cohortId = form.getCohort()[i];
                Character type = typeStr != null && typeStr.length() > 0 ? typeStr.charAt(0) : null;
                if (def.isShowByDefault() != show || !nullSafeEqual(label, def.getLabel()) || type != def.getTypeCode() || !nullSafeEqual(cohortId, def.getCohortId()))
                {
                    def = def.createMutable();
                    def.setShowByDefault(show);
                    def.setLabel(label);
                    def.setCohortId(cohortId);
                    def.setTypeCode(type);
                    StudyManager.getInstance().updateVisit(getUser(), def);
                }
            }
            return true;
        }

        @Override
        public ActionURL getSuccessURL(VisitPropertyForm visitPropertyForm)
        {
            return new ActionURL(ManageVisitsAction.class, getContainer());
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class DatasetVisibilityAction extends FormViewAction<DatasetPropertyForm>
    {
        @Override
        public ModelAndView getView(DatasetPropertyForm form, boolean reshow, BindException errors)
        {
            _study = getStudyRedirectIfNull();
            Map<Integer, DatasetVisibilityData> bean = new HashMap<>();
            for (Dataset def : _study.getDatasets())
            {
                DatasetVisibilityData data = new DatasetVisibilityData();
                data.label = def.getLabel();
                data.categoryId = def.getViewCategory() != null ? def.getViewCategory().getRowId() : null;
                data.cohort = def.getCohortId();
                data.visible = def.isShowByDefault();
                data.shared = def.isShared();
                data.inherited = ((DatasetDefinition)def).isInherited();
                data.status = (String)ReportPropsManager.get().getPropertyValue(def.getEntityId(), getContainer(), "status");
                if ("None".equals(data.status))
                    data.status = null;
                TableInfo t = def.getTableInfo(getViewContext().getUser(), false, _study.isDataspaceStudy());
                long rowCount = new TableSelector(t).getRowCount();
                data.rowCount = rowCount;
                data.empty = 0 == rowCount;
                bean.put(def.getDatasetId(), data);
            }

            // Merge with form data
            Map<Integer, DatasetVisibilityData> formDataset = form.getDataset();
            if (formDataset != null)
            {
                for (Map.Entry<Integer, DatasetVisibilityData> entry : formDataset.entrySet())
                {
                    DatasetVisibilityData formData = entry.getValue();
                    DatasetVisibilityData beanData = bean.get(entry.getKey());
                    if (formData == null || beanData == null)
                        continue;

                    beanData.label = formData.label;
                    beanData.categoryId = formData.categoryId;
                    beanData.cohort = formData.cohort;
                    beanData.visible = formData.visible;
                }
            }

            return new StudyJspView<>(
                    getStudyRedirectIfNull(), "/org/labkey/study/view/datasetVisibility.jsp", bean, errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            _addManageStudy(root);
            root.addChild("Manage Datasets", new ActionURL(ManageTypesAction.class, getContainer()));
            root.addChild("Properties");
        }

        @Override
        public void validateCommand(DatasetPropertyForm form, Errors errors)
        {
            // Check for bad labels
            Set<String> labels = new HashSet<>();
            for (DatasetVisibilityData data : form.getDataset().values())
            {
                String label = data.getLabel();
                if (StringUtils.isBlank(label))
                {
                    errors.reject("datasetVisibility", "Label cannot be blank");
                }
                if (labels.contains(label))
                {
                    errors.reject("datasetVisibility", "Labels must be unique. Found two or more labels called '" + label + "'.");
                }
                labels.add(label);
            }
        }

        @Override
        public boolean handlePost(DatasetPropertyForm form, BindException errors) throws Exception
        {
            for (Map.Entry<Integer,DatasetVisibilityData> entry : form.getDataset().entrySet())
            {
                Integer id = entry.getKey();
                DatasetVisibilityData data = entry.getValue();

                if (id == null)
                    throw new IllegalArgumentException("id required");

                DatasetDefinition def = StudyManager.getInstance().getDatasetDefinition(getStudyThrowIfNull(), id);
                if (def == null)
                    throw new NotFoundException("dataset");

                String label = data.getLabel();
                boolean show = data.isVisible();
                Integer categoryId = data.getCategoryId();
                Integer cohortId = data.getCohort();
                if (cohortId != null && cohortId.intValue() == -1)
                    cohortId = null;

                if (def.isShowByDefault() != show || !nullSafeEqual(categoryId, def.getCategoryId()) || !nullSafeEqual(label, def.getLabel()) || !BaseStudyController.nullSafeEqual(cohortId, def.getCohortId()))
                {
                    def = def.createMutable();
                    def.setShowByDefault(show);
                    def.setCategoryId(categoryId);
                    def.setCohortId(cohortId);
                    def.setLabel(label);
                    List<String> saveErrors = new ArrayList<>();
                    StudyManager.getInstance().updateDatasetDefinition(getUser(), def, saveErrors);
                    for (String error : saveErrors)
                    {
                        errors.reject(ERROR_MSG, error);
                        return false;
                    }
                }
                ReportPropsManager.get().setPropertyValue(def.getEntityId(), getContainer(), "status", data.getStatus());
            }

            return true;
        }

        @Override
        public ActionURL getSuccessURL(DatasetPropertyForm form)
        {
            return new ActionURL(ManageTypesAction.class, getContainer());
        }
    }

    // Bean will be an map of these
    public static class DatasetVisibilityData
    {
        // form POSTed values
        public String label;
        public Integer cohort; // null for none
        public String status;
        public Integer categoryId;
        public boolean visible;

        // not form POSTed -- used to render view
        public long rowCount;
        public boolean empty;
        public boolean shared;
        public boolean inherited;

        public String getLabel()
        {
            return label;
        }

        public void setLabel(String label)
        {
            this.label = label;
        }

        public Integer getCohort()
        {
            return cohort;
        }

        public void setCohort(Integer cohort)
        {
            this.cohort = cohort;
        }

        public String getStatus()
        {
            return status;
        }

        public void setStatus(String status)
        {
            this.status = status;
        }

        public boolean isVisible()
        {
            return visible;
        }

        public void setVisible(boolean visible)
        {
            this.visible = visible;
        }

        public Integer getCategoryId()
        {
            return categoryId;
        }

        public void setCategoryId(Integer categoryId)
        {
            this.categoryId = categoryId;
        }
    }

    @Marshal(Marshaller.Jackson)
    @RequiresPermission(AdminPermission.class)
    public class DeleteDatasetPropertyOverrideAction extends MutatingApiAction
    {
        @Override
        public Object execute(Object o, BindException errors)
        {
            StudyManager.getInstance().deleteDatasetPropertyOverrides(getUser(), getContainer(), errors);
            return errors.hasErrors() ? null : success();
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class DatasetDisplayOrderAction extends FormViewAction<DatasetReorderForm>
    {
        @Override
        public ModelAndView getView(DatasetReorderForm form, boolean reshow, BindException errors)
        {
            return new StudyJspView<Object>(getStudyRedirectIfNull(), "/org/labkey/study/view/datasetDisplayOrder.jsp", form, errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            _addManageStudy(root);
            root.addChild("Manage Datasets", new ActionURL(ManageTypesAction.class, getContainer()));
            root.addChild("Display Order");
        }

        @Override
        public void validateCommand(DatasetReorderForm target, Errors errors) {}

        @Override
        public boolean handlePost(DatasetReorderForm form, BindException errors)
        {
            String order = form.getOrder();

            if (order != null && order.length() > 0 && !form.isResetOrder())
            {
                String[] ids = order.split(",");
                List<Integer> orderedIds = new ArrayList<>(ids.length);

                for (String id : ids)
                    orderedIds.add(Integer.parseInt(id));

                DatasetReorderer reorderer = new DatasetReorderer(getStudyThrowIfNull(), getUser());
                reorderer.reorderDatasets(orderedIds);
            }
            else if (form.isResetOrder())
            {
                DatasetReorderer reorderer = new DatasetReorderer(getStudyThrowIfNull(), getUser());
                reorderer.resetOrder();
            }

            return true;
        }

        @Override
        public ActionURL getSuccessURL(DatasetReorderForm visitPropertyForm)
        {
            return new ActionURL(ManageTypesAction.class, getContainer());
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class DeleteDatasetAction extends FormHandlerAction<IdForm>
    {
        @Override
        public void validateCommand(IdForm target, Errors errors)
        {

        }

        @Override
        public boolean handlePost(IdForm form, BindException errors) throws Exception
        {
            Study study = getStudyRedirectIfNull(getContainer());

            DatasetDefinition ds = StudyManager.getInstance().getDatasetDefinition(study, form.getId());
            if (null == ds)
                redirectTypeNotFound(form.getId());
            if (!ds.canDeleteDefinition(getUser()))
                errors.reject(ERROR_MSG, "Can't delete this dataset: " + ds.getName());

            if (errors.hasErrors())
                return false;

            DbScope scope = StudySchema.getInstance().getSchema().getScope();
            try (DbScope.Transaction transaction = scope.ensureTransaction())
            {
                // performStudyResync==false so we can do this out of the transaction
                StudyManager.getInstance().deleteDataset(getStudyRedirectIfNull(), getUser(), ds, false);
                transaction.commit();
            }

            StudyManager.getInstance().getVisitManager((StudyImpl)study).updateParticipantVisits(getUser(), Collections.emptySet());
            return true;
        }

        @Override
        public URLHelper getSuccessURL(IdForm idForm)
        {
            throw new RedirectException(new ActionURL(ManageTypesAction.class, getContainer()));
        }
    }


    private static final String DEFAULT_PARTICIPANT_VIEW_SOURCE =
            "<div id=\"participantData\">Loading...</div>\n" +
            "\n" +
            "<script type=\"text/javascript\">\n" +
            "    LABKEY.requiresClientAPI(function() {\n" +
            "       /* get the participant id from the request URL: this parameter is required. */\n" +
            "       var participantId = LABKEY.ActionURL.getParameter('participantId');\n" +
            "       /* get the dataset id from the request URL: this is used to remember expand/collapse\n" +
            "           state per-dataset.  This parameter is optional; we use -1 if it isn't provided. */\n" +
            "       var datasetId = LABKEY.ActionURL.getParameter('datasetId');\n" +
            "       if (!datasetId)\n" +
            "           datasetId = -1;\n" +
            "       var dataType = 'ALL';\n" +
            "       /* Additional options for dataType 'DEMOGRAPHIC' or 'NON_DEMOGRAPHIC'. */" +
            "\n" +
            "       var QCState = LABKEY.ActionURL.getParameter('QCState');\n" +
            "\n" +
            "       /* create the participant details webpart: */\n" +
            "       var participantWebPart = new LABKEY.WebPart({\n" +
            "       partName: 'Participant Details',\n" +
            "       renderTo: 'participantData',\n" +
            "       frame : 'false',\n" +
            "       partConfig: {\n" +
            "           participantId: participantId,\n" +
            "           datasetId: datasetId,\n" +
            "           dataType: dataType,\n" +
            "           QCState: QCState,\n" +
            "           currentUrl: '' + window.location\n" +
            "           }\n" +
            "       });\n" +
            "\n" +
            "       /* place the webpart into the 'participantData' div: */\n" +
            "       participantWebPart.render();\n" +
            "   });\n" +
            "</script>";

    public static class CustomizeParticipantViewForm extends ReturnUrlForm
    {
        private String _returnUrl;
        private String _customScript;
        private String _participantId;
        private boolean _useCustomView;
        private boolean _reshow;
        private boolean _editable = true;

        public boolean isEditable()
        {
            return _editable;
        }

        public void setEditable(boolean editable)
        {
            _editable = editable;
        }

        public String getCustomScript()
        {
            return _customScript;
        }

        public String getDefaultScript()
        {
            return DEFAULT_PARTICIPANT_VIEW_SOURCE;
        }

        public void setCustomScript(String customScript)
        {
            _customScript = customScript;
        }

        public String getParticipantId()
        {
            return _participantId;
        }

        public void setParticipantId(String participantId)
        {
            _participantId = participantId;
        }

        public boolean isReshow()
        {
            return _reshow;
        }

        public void setReshow(boolean reshow)
        {
            _reshow = reshow;
        }

        public boolean isUseCustomView()
        {
            return _useCustomView;
        }

        public void setUseCustomView(boolean useCustomView)
        {
            _useCustomView = useCustomView;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class CustomizeParticipantViewAction extends FormViewAction<CustomizeParticipantViewForm>
    {
        @Override
        public void validateCommand(CustomizeParticipantViewForm target, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(CustomizeParticipantViewForm form, boolean reshow, BindException errors)
        {
            // We know that the user is at least a folder admin - they must also be either a developer
            if (!getUser().isPlatformDeveloper())
                throw new UnauthorizedException();
            Study study = getStudyRedirectIfNull();
            CustomParticipantView view = StudyManager.getInstance().getCustomParticipantView(study);
            if (view != null)
            {
                // issue : 18595 chrome will complain that the script we are executing matches the script sent down in the request
                getViewContext().getResponse().setHeader("X-XSS-Protection", "0");

                form.setCustomScript(view.getBody());
                form.setUseCustomView(view.isActive());
                form.setEditable(!view.isModuleParticipantView());
            }

            return new JspView<>("/org/labkey/study/view/customizeParticipantView.jsp", form);
        }

        @Override
        public boolean handlePost(CustomizeParticipantViewForm form, BindException errors)
        {
            Study study = getStudyThrowIfNull();
            CustomParticipantView view = StudyManager.getInstance().getCustomParticipantView(study);
            if (view == null)
                view = new CustomParticipantView();
            view.setBody(form.getCustomScript());
            view.setActive(form.isUseCustomView());
            view = StudyManager.getInstance().saveCustomParticipantView(study, getUser(), view);
            return view != null;
        }

        @Override
        public ActionURL getSuccessURL(CustomizeParticipantViewForm form)
        {
            if (form.isReshow())
            {
                ActionURL reshowURL = new ActionURL(CustomizeParticipantViewAction.class, getContainer());
                if (form.getParticipantId() != null && form.getParticipantId().length() > 0)
                    reshowURL.addParameter("participantId", form.getParticipantId());
                if (form.getReturnUrl() != null && form.getReturnUrl().length() > 0)
                    reshowURL.addParameter(ActionURL.Param.returnUrl, form.getReturnUrl());
                return reshowURL;
            }
            else if (form.getReturnUrl() != null && form.getReturnUrl().length() > 0)
                return new ActionURL(form.getReturnUrl());
            else
                return urlProvider(ReportUrls.class).urlManageViews(getContainer());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            _addManageStudy(root);
            root.addChild("Manage Views", urlProvider(ReportUrls.class).urlManageViews(getContainer()));
            root.addChild("Customize " + StudyService.get().getSubjectNounSingular(getContainer()) + " View");
        }
    }

    public static class StudySnapshotForm extends QuerySnapshotForm
    {
        private int _snapshotDatasetId = -1;
        private String _action;

        public static final String EDIT_DATASET = "editDataset";
        public static final String CREATE_SNAPSHOT = "createSnapshot";
        public static final String CANCEL = "cancel";

        public int getSnapshotDatasetId()
        {
            return _snapshotDatasetId;
        }

        public void setSnapshotDatasetId(int snapshotDatasetId)
        {
            _snapshotDatasetId = snapshotDatasetId;
        }

        public String getAction()
        {
            return _action;
        }

        public void setAction(String action)
        {
            _action = action;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class CreateSnapshotAction extends FormViewAction<StudySnapshotForm>
    {
        ActionURL _successURL;

        @Override
        public void validateCommand(StudySnapshotForm form, Errors errors)
        {
            if (StudySnapshotForm.CANCEL.equals(form.getAction()))
                return;

            Study study = StudyManager.getInstance().getStudy(getContainer());
            if (null == study)
                throw new NotFoundException("No study in this folder");

            String name = StringUtils.trimToNull(form.getSnapshotName());

            if (name != null)
            {
                QuerySnapshotDefinition def = QueryService.get().getSnapshotDef(getContainer(), form.getSchemaName(), name);
                if (def != null)
                {
                    errors.reject("snapshotQuery.error", "A Snapshot with the same name already exists");
                    return;
                }

                // check for a dataset with the same label/name unless it's one that we created
                Dataset dataset = StudyManager.getInstance().getDatasetDefinitionByQueryName(study, name);
                if (dataset != null)
                {
                    if (dataset.getDatasetId() != form.getSnapshotDatasetId())
                        errors.reject("snapshotQuery.error", "A Dataset with the same name/label already exists");
                }
            }
            else
                errors.reject("snapshotQuery.error", "The Query Snapshot name cannot be blank");
        }

        @Override
        public ModelAndView getView(StudySnapshotForm form, boolean reshow, BindException errors)
        {
            if (!reshow || errors.hasErrors())
            {
                ActionURL url = getViewContext().getActionURL();

                if (StringUtils.isEmpty(form.getSnapshotName()))
                    form.setSnapshotName(url.getParameter("ff_snapshotName"));
                form.setUpdateDelay(NumberUtils.toInt(url.getParameter("ff_updateDelay")));
                form.setSnapshotDatasetId(NumberUtils.toInt(url.getParameter("ff_snapshotDatasetId"), -1));

                return new JspView<QueryForm>("/org/labkey/study/view/createDatasetSnapshot.jsp", form, errors);
            }
            else if (StudySnapshotForm.EDIT_DATASET.equals(form.getAction()))
            {
                throw new NotFoundException("Unable to edit the created dataset definition.");
            }
            return null;
        }

        private void deletePreviousDatasetDefinition(StudySnapshotForm form)
        {
            if (form.getSnapshotDatasetId() != -1)
            {
                StudyImpl study = StudyManager.getInstance().getStudy(getContainer());

                // a dataset definition was edited previously, but under a different name, need to delete the old one
                DatasetDefinition dsDef = StudyManager.getInstance().getDatasetDefinition(study, form.getSnapshotDatasetId());
                if (dsDef != null)
                {
                    StudyManager.getInstance().deleteDataset(study, getUser(), dsDef, true);
                    form.setSnapshotDatasetId(-1);
                }
            }
        }

        private Dataset createDataset(StudySnapshotForm form, BindException errors) throws Exception
        {
            StudyImpl study = StudyManager.getInstance().getStudy(getContainer());
            Dataset dsDef = StudyManager.getInstance().getDatasetDefinitionByName(study, form.getSnapshotName());

            if (dsDef == null)
            {
                deletePreviousDatasetDefinition(form);

                // if this snapshot is being created from an existing dataset, copy key field settings
                int datasetId = NumberUtils.toInt(getViewContext().getActionURL().getParameter(Dataset.DATASETKEY), -1);
                String additionalKey = null;
                DatasetDefinition.KeyManagementType keyManagementType = KeyManagementType.None;
                boolean isDemographicData = false;
                boolean useTimeKeyField = false;
                List<ColumnInfo> columnsToProvision = new ArrayList<>();

                if (datasetId != -1)
                {
                    DatasetDefinition sourceDef = study.getDataset(datasetId);
                    if (sourceDef != null)
                    {
                        additionalKey = sourceDef.getKeyPropertyName();
                        keyManagementType = sourceDef.getKeyManagementType();
                        isDemographicData = sourceDef.isDemographicData();
                        useTimeKeyField = sourceDef.getUseTimeKeyField();

                        // make sure we provision any managed key fields
                        if ((additionalKey != null) && (keyManagementType != KeyManagementType.None))
                        {
                            TableInfo sourceTable = sourceDef.getTableInfo(getUser());
                            ColumnInfo col = sourceTable.getColumn(FieldKey.fromParts(additionalKey));
                            if (col != null)
                                columnsToProvision.add(col);
                        }
                    }
                }
                DatasetDefinition def = StudyPublishManager.getInstance().createDataset(getUser(), new DatasetDefinition.Builder(form.getSnapshotName())
                        .setStudy(study)
                        .setKeyPropertyName(additionalKey)
                        .setDemographicData(isDemographicData)
                        .setUseTimeKeyField(useTimeKeyField));
                form.setSnapshotDatasetId(def.getDatasetId());
                if (keyManagementType != KeyManagementType.None)
                {
                    def = def.createMutable();
                    def.setKeyManagementType(keyManagementType);

                    StudyManager.getInstance().updateDatasetDefinition(getUser(), def);
                }

                // NOTE getDisplayColumns() indirectly causes a query of the datasets,
                // Do this before provisionTable() so we don't query the dataset we are about to create
                // causes a problem on postgres (bug 11153)
                for (DisplayColumn dc : QuerySnapshotService.get(form.getSchemaName()).getDisplayColumns(form, errors))
                {
                    ColumnInfo col = dc.getColumnInfo();
                    if (col != null && !DatasetDefinition.isDefaultFieldName(col.getName(), study))
                        columnsToProvision.add(col);
                }

                // def may not be provisioned yet, create before we start adding properties
                def.provisionTable();
                Domain d = def.getDomain();

                for (ColumnInfo col : columnsToProvision)
                {
                    DatasetSnapshotProvider.addAsDomainProperty(d, col);
                }
                d.save(getUser());

                return def;
            }

            return dsDef;
        }

        @Override
        public boolean handlePost(StudySnapshotForm form, BindException errors) throws Exception
        {
            DbSchema schema = StudySchema.getInstance().getSchema();

            try (DbScope.Transaction transaction = schema.getScope().ensureTransaction())
            {
                if (StudySnapshotForm.EDIT_DATASET.equals(form.getAction()))
                {
                    Dataset def = createDataset(form, errors);
                    if (!errors.hasErrors() && def != null)
                    {
                        ActionURL returnUrl = getViewContext().cloneActionURL()
                            .replaceParameter("ff_snapshotName", form.getSnapshotName())
                            .replaceParameter("ff_updateDelay", form.getUpdateDelay())
                            .replaceParameter("ff_snapshotDatasetId", form.getSnapshotDatasetId());

                        _successURL = new ActionURL(StudyController.EditTypeAction.class, getContainer())
                                .addParameter("datasetId", def.getDatasetId())
                                .addReturnURL(returnUrl);
                    }
                }
                else if (StudySnapshotForm.CREATE_SNAPSHOT.equals(form.getAction()))
                {
                    createDataset(form, errors);
                    if (!errors.hasErrors())
                        _successURL = QuerySnapshotService.get(form.getSchemaName()).createSnapshot(form, errors);
                }
                else if (StudySnapshotForm.CANCEL.equals(form.getAction()))
                {
                    deletePreviousDatasetDefinition(form);
                    String redirect = getViewContext().getActionURL().getParameter(ActionURL.Param.redirectUrl);
                    if (redirect != null)
                        _successURL = new ActionURL(PageFlowUtil.decode(redirect));
                }

                if (!errors.hasErrors())
                    transaction.commit();
            }

            return !errors.hasErrors();
        }

        @Override
        public ActionURL getSuccessURL(StudySnapshotForm queryForm)
        {
            return _successURL;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("querySnapshot");
            root.addChild("Create Query Snapshot");
        }
    }

    /**
     * Provides a view to update study query snapshots. Since query snapshots are implemented as datasets, the
     * dataset properties editor can be shown in this view.
     */
    @RequiresPermission(AdminPermission.class)
    public class EditSnapshotAction extends FormViewAction<StudySnapshotForm>
    {
        ActionURL _successURL;

        @Override
        public void validateCommand(StudySnapshotForm form, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(StudySnapshotForm form, boolean reshow, BindException errors) throws Exception
        {
            form.setEdit(true);
            if (!reshow)
                form.init(QueryService.get().getSnapshotDef(getContainer(), form.getSchemaName(), form.getSnapshotName()), getUser());

            VBox box = new VBox();

            QuerySnapshotService.Provider provider = QuerySnapshotService.get(form.getSchemaName());
            if (provider != null)
            {
                box.addView(new JspView<QueryForm>("/org/labkey/study/view/editSnapshot.jsp", form));
                box.addView(new JspView<QueryForm>("/org/labkey/study/view/createDatasetSnapshot.jsp", form, errors));

                boolean showHistory = BooleanUtils.toBoolean(getViewContext().getActionURL().getParameter("showHistory"));
                if (showHistory)
                {
                    HttpView historyView = provider.createAuditView(form);
                    if (historyView != null)
                        box.addView(historyView);
                }
            }
            return box;
        }

        @Override
        public boolean handlePost(StudySnapshotForm form, BindException errors) throws Exception
        {
            if (StudySnapshotForm.CANCEL.equals(form.getAction()))
            {
                String redirect = getViewContext().getActionURL().getParameter(ActionURL.Param.redirectUrl);
                if (redirect != null)
                    _successURL = new ActionURL(PageFlowUtil.decode(redirect));
            }
            else if (form.isUpdateSnapshot())
            {
                _successURL = QuerySnapshotService.get(form.getSchemaName()).updateSnapshot(form, errors);

                return !errors.hasErrors();
            }
            else
            {
                QuerySnapshotDefinition def = QueryService.get().getSnapshotDef(getContainer(), form.getSchemaName(), form.getSnapshotName());
                if (def != null)
                {
                    def.setUpdateDelay(form.getUpdateDelay());
                    _successURL = QuerySnapshotService.get(form.getSchemaName()).updateSnapshotDefinition(getViewContext(), def, errors);
                    return !errors.hasErrors();
                }
                else
                {
                    errors.reject("snapshotQuery.error", "Unable to create QuerySnapshotDefinition");
                    return false;
                }
            }
            return true;
        }

        @Override
        public ActionURL getSuccessURL(StudySnapshotForm form)
        {
            return _successURL;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Edit Query Snapshot");
        }
    }

    public static class DatasetPropertyForm
    {
        private Map<Integer, DatasetVisibilityData> _map = MapUtils.lazyMap(new HashMap<>(), FactoryUtils.instantiateFactory(DatasetVisibilityData.class));

        public Map<Integer, DatasetVisibilityData> getDataset()
        {
            return _map;
        }

        public void setDataset(Map<Integer, DatasetVisibilityData> map)
        {
            _map = map;
        }
    }

    public static class RequirePipelineView extends StudyJspView<Boolean>
    {
        public RequirePipelineView(StudyImpl study, boolean showGoBack, BindException errors)
        {
            super(study, "/org/labkey/study/view/requirePipeline.jsp", showGoBack, errors);
        }
    }

    public static class VisitPropertyForm extends PropertyForm
    {
        private int[] _ids;
        private int[] _visible;

        public int[] getIds()
        {
            return _ids;
        }

        public void setIds(int[] ids)
        {
            _ids = ids;
        }

        public int[] getVisible()
        {
            return _visible;
        }

        public void setVisible(int[] visible)
        {
            _visible = visible;
        }
    }

    public abstract static class PropertyForm
    {
        private String[] _label;
        private String[] _extraData;
        private int[] _cohort;

        public String[] getExtraData()
        {
            return _extraData;
        }

        public void setExtraData(String[] extraData)
        {
            _extraData = extraData;
        }

        public String[] getLabel()
        {
            return _label;
        }

        public void setLabel(String[] label)
        {
            _label = label;
        }

        public int[] getCohort()
        {
            return _cohort;
        }

        public void setCohort(int[] cohort)
        {
            _cohort = cohort;
        }
    }


    public static class DatasetReorderForm
    {
        private String order;
        private boolean resetOrder = false;

        public String getOrder() {return order;}

        public void setOrder(String order) {this.order = order;}

        public boolean isResetOrder()
        {
            return resetOrder;
        }

        public void setResetOrder(boolean resetOrder)
        {
            this.resetOrder = resetOrder;
        }
    }

    public static class VisitReorderForm extends ReturnUrlForm
    {
        private boolean _explicitDisplayOrder;
        private boolean _explicitChronologicalOrder;
        private String _displayOrder;
        private String _chronologicalOrder;

        public String getDisplayOrder()
        {
            return _displayOrder;
        }

        public void setDisplayOrder(String displayOrder)
        {
            _displayOrder = displayOrder;
        }

        public String getChronologicalOrder()
        {
            return _chronologicalOrder;
        }

        public void setChronologicalOrder(String chronologicalOrder)
        {
            _chronologicalOrder = chronologicalOrder;
        }

        public boolean isExplicitDisplayOrder()
        {
            return _explicitDisplayOrder;
        }

        public void setExplicitDisplayOrder(boolean explicitDisplayOrder)
        {
            _explicitDisplayOrder = explicitDisplayOrder;
        }

        public boolean isExplicitChronologicalOrder()
        {
            return _explicitChronologicalOrder;
        }

        public void setExplicitChronologicalOrder(boolean explicitChronologicalOrder)
        {
            _explicitChronologicalOrder = explicitChronologicalOrder;
        }
    }

    public static class ImportStudyBatchBean
    {
        private final DatasetFileReader reader;
        private final String path;

        public ImportStudyBatchBean(DatasetFileReader reader, String path)
        {
            this.reader = reader;
            this.path = path;
        }

        public DatasetFileReader getReader()
        {
            return reader;
        }

        public String getPath()
        {
            return path;
        }
    }

    public static class ViewPrefsBean
    {
        private final List<Pair<String, String>> _views;
        private final Dataset _def;

        public ViewPrefsBean(List<Pair<String, String>> views, Dataset def)
        {
            _views = views;
            _def = def;
        }

        public List<Pair<String, String>> getViews(){return _views;}
        public Dataset getDatasetDefinition(){return _def;}
    }


    private static final String DEFAULT_DATASET_VIEW = "Study.defaultDatasetView";

    public static String getDefaultView(ViewContext context, int datasetId)
    {
        Map<String, String> viewMap = PropertyManager.getProperties(context.getUser(),
                context.getContainer(), DEFAULT_DATASET_VIEW);

        final String key = Integer.toString(datasetId);
        if (viewMap.containsKey(key))
        {
            return viewMap.get(key);
        }
        return "";
    }

    private void setDefaultView(int datasetId, String view)
    {
        PropertyManager.PropertyMap viewMap = PropertyManager.getWritableProperties(getUser(),
                getContainer(), DEFAULT_DATASET_VIEW, true);

        viewMap.put(Integer.toString(datasetId), view);
        viewMap.save();
    }

    private String getVisitLabel()
    {
        StudyImpl study = getStudy();
        if (study != null)
        {
            return StudyManager.getInstance().getVisitManager(getStudyRedirectIfNull()).getLabel();
        }
        return "Visit";
    }


    private String getVisitLabelPlural()
    {
        StudyImpl study = getStudy();
        if (study != null)
        {
            return StudyManager.getInstance().getVisitManager(getStudyRedirectIfNull()).getPluralLabel();
        }
        return "Visits";
    }

    public static class ParticipantForm extends ViewForm implements StudyManager.ParticipantViewConfig
    {
        private String participantId;
        private int datasetId;
        private double sequenceNum;
        private String action;
        private int reportId;
        private String _redirectUrl;
        private Map<String, String> aliases;

        @Override
        public String getParticipantId(){return participantId;}

        public void setParticipantId(String participantId)
        {
            this.participantId = participantId;
            aliases = StudyManager.getInstance().getAliasMap(StudyManager.getInstance().getStudy(getContainer()), getUser(), participantId);
        }

        @Override
        public Map<String, String> getAliases()
        {
            return aliases;
        }

        @Override
        public int getDatasetId(){return datasetId;}
        public void setDatasetId(int datasetId){this.datasetId = datasetId;}

        public double getSequenceNum(){return sequenceNum;}
        public void setSequenceNum(double sequenceNum){this.sequenceNum = sequenceNum;}

        public String getAction(){return action;}
        public void setAction(String action){this.action = action;}

        public int getReportId(){return reportId;}
        public void setReportId(int reportId){this.reportId = reportId;}

        @Override
        public String getRedirectUrl() { return _redirectUrl; }

        public void setRedirectUrl(String redirectUrl) { _redirectUrl = redirectUrl; }
    }


    public static class StudyPropertiesForm extends ReturnUrlForm
    {
        private String _label;
        private TimepointType _timepointType;
        private Date _startDate;
        private Date _endDate;
        private SecurityType _securityType;
        private String _subjectNounSingular = "Participant";
        private String _subjectNounPlural = "Participants";
        private String _subjectColumnName = "ParticipantId";
        private String _assayPlan;
        private String _description;
        private String _descriptionRendererType;
        private String _grant;
        private String _investigator;
        private String _species;
        private int _defaultTimepointDuration = 0;
        private String _alternateIdPrefix;
        private int _alternateIdDigits;
        private boolean _allowReqLocRepository = true;
        private boolean _allowReqLocClinic = true;
        private boolean _allowReqLocSal = true;
        private boolean _allowReqLocEndpoint = true;
        private boolean _shareDatasets = false;
        private boolean _shareVisits = false;

        public String getLabel()
        {
            return _label;
        }

        public void setLabel(String label)
        {
            _label = label;
        }

        public TimepointType getTimepointType()
        {
            return _timepointType;
        }

        public void setTimepointType(TimepointType timepointType)
        {
            _timepointType = timepointType;
        }

        public Date getStartDate()
        {
            return _startDate;
        }

        public void setStartDate(Date startDate)
        {
            _startDate = startDate;
        }

        public void setSecurityString(String security)
        {
            _securityType = SecurityType.valueOf(security);
        }

        public String getSecurityString()
        {
            return _securityType == null ? null : _securityType.name();
        }

        public void setSecurityType(SecurityType securityType)
        {
            _securityType = securityType;
        }

        public SecurityType getSecurityType()
        {
            return _securityType;
        }

        public String getSubjectNounSingular()
        {
            return _subjectNounSingular;
        }

        public void setSubjectNounSingular(String subjectNounSingular)
        {
            _subjectNounSingular = subjectNounSingular;
        }

        public String getSubjectNounPlural()
        {
            return _subjectNounPlural;
        }

        public void setSubjectNounPlural(String subjectNounPlural)
        {
            _subjectNounPlural = subjectNounPlural;
        }

        public String getSubjectColumnName()
        {
            return _subjectColumnName;
        }

        public void setSubjectColumnName(String subjectColumnName)
        {
            _subjectColumnName = subjectColumnName;
        }

        public String getDescription()
        {
            return _description;
        }

        public void setDescription(String description)
        {
            _description = description;
        }

        public String getDescriptionRendererType()
        {
            return _descriptionRendererType;
        }

        public void setDescriptionRendererType(String descriptionRendererType)
        {
            _descriptionRendererType = descriptionRendererType;
        }

        public String getInvestigator()
        {
            return _investigator;
        }

        public void setInvestigator(String investigator)
        {
            _investigator = investigator;
        }

        public String getGrant()
        {
            return _grant;
        }

        public void setGrant(String grant)
        {
            _grant = grant;
        }

        public int getDefaultTimepointDuration()
        {
            return _defaultTimepointDuration;
        }

        public void setDefaultTimepointDuration(int defaultTimepointDuration)
        {
            _defaultTimepointDuration = defaultTimepointDuration;
        }

        public String getAlternateIdPrefix()
        {
            return _alternateIdPrefix;
        }

        public void setAlternateIdPrefix(String alternateIdPrefix)
        {
            _alternateIdPrefix = alternateIdPrefix;
        }

        public int getAlternateIdDigits()
        {
            return _alternateIdDigits;
        }

        public void setAlternateIdDigits(int alternateIdDigits)
        {
            _alternateIdDigits = alternateIdDigits;
        }

        public boolean isAllowReqLocRepository()
        {
            return _allowReqLocRepository;
        }

        public void setAllowReqLocRepository(boolean allowReqLocRepository)
        {
            _allowReqLocRepository = allowReqLocRepository;
        }

        public boolean isAllowReqLocClinic()
        {
            return _allowReqLocClinic;
        }

        public void setAllowReqLocClinic(boolean allowReqLocClinic)
        {
            _allowReqLocClinic = allowReqLocClinic;
        }

        public boolean isAllowReqLocSal()
        {
            return _allowReqLocSal;
        }

        public void setAllowReqLocSal(boolean allowReqLocSal)
        {
            _allowReqLocSal = allowReqLocSal;
        }

        public boolean isAllowReqLocEndpoint()
        {
            return _allowReqLocEndpoint;
        }

        public void setAllowReqLocEndpoint(boolean allowReqLocEndpoint)
        {
            _allowReqLocEndpoint = allowReqLocEndpoint;
        }

        public Date getEndDate()
        {
            return _endDate;
        }

        public void setEndDate(Date endDate)
        {
            _endDate = endDate;
        }

        public String getAssayPlan()
        {
            return _assayPlan;
        }

        public void setAssayPlan(String assayPlan)
        {
            _assayPlan = assayPlan;
        }

        public String getSpecies()
        {
            return _species;
        }

        public void setSpecies(String species)
        {
            _species = species;
        }

        public boolean isShareDatasets()
        {
            return _shareDatasets;
        }

        public void setShareDatasets(boolean shareDatasets)
        {
            _shareDatasets = shareDatasets;
        }

        public boolean isShareVisits()
        {
            return _shareVisits;
        }

        public void setShareVisits(boolean shareDatasets)
        {
            _shareVisits = shareDatasets;
        }
    }

    public static class IdForm
    {
        private int _id;

        public int getId() {return _id;}

        public void setId(int id) {_id = id;}
    }

    public static class SourceLsidForm
    {
        private String _sourceLsid;

        public String getSourceLsid() {return _sourceLsid;}

        public void setSourceLsid(String sourceLsid) {_sourceLsid = sourceLsid;}
    }

    public static class ReportHeader extends HttpView
    {
        private final Report _report;

        public ReportHeader(Report report)
        {
            _report = report;
        }

        @Override
        protected void renderInternal(Object model, PrintWriter out)
        {
            if (!StringUtils.isEmpty(_report.getDescriptor().getReportDescription()))
            {
                out.print("<table>");
                out.print("<tr><td><span class='navPageHeader'>Report Description:</span>&nbsp;</td>");
                out.print("<td>" + _report.getDescriptor().getReportDescription() + "</td></tr>");
                out.print("</table>");
            }
        }
    }

    /**
     * Adds next and prev buttons to the participant view
     */
    public static class ParticipantNavView extends HttpView
    {
        private final ActionURL _prevURL;
        private final ActionURL _nextURL;
        private final String _display;
        private final String _currentParticipantId;
        private boolean _showCustomizeLink = true;

        public ParticipantNavView(ActionURL prevURL, ActionURL nextURL, String currentParticipantId, String display)
        {
            _prevURL = prevURL;
            _nextURL = nextURL;
            _display = display;
            _currentParticipantId = currentParticipantId;
        }

        public ParticipantNavView(ActionURL prevURL, ActionURL nextURL, String currentParticipantId)
        {
            this(prevURL, nextURL, currentParticipantId,  null);
        }

        @Override
        protected void renderInternal(Object model, PrintWriter out)
        {
            Container c = getViewContext().getContainer();
            User user = getViewContext().getUser();

            String subjectNoun = PageFlowUtil.filter(StudyService.get().getSubjectNounSingular(getViewContext().getContainer()));
            out.print("<table><tr><td align=\"left\">");
            if (_prevURL != null)
            {
                out.print(PageFlowUtil.textLink("Previous " + subjectNoun, _prevURL));
                out.print("&nbsp;");
            }

            if (_nextURL != null)
            {
                out.print(PageFlowUtil.textLink("Next " + subjectNoun, _nextURL));
                out.print("&nbsp;");
            }

            SearchService ss = SearchService.get();

            if (null != _currentParticipantId && null != ss)
            {
                ActionURL search = urlProvider(SearchUrls.class).getSearchURL(c, "+" + ss.escapeTerm(_currentParticipantId));
                out.print(PageFlowUtil.textLink("Search for '" + id(_currentParticipantId, c, user) + "'", search));
                out.print("&nbsp;");
            }

            // Show customize link to site admins (who are always developers) and folder admins who are developers:
            Set<Class<? extends Permission>> permissions = new HashSet<>();
            permissions.add(AdminPermission.class);
            permissions.add(PlatformDeveloperPermission.class);
            if (_showCustomizeLink && c.hasPermissions(getViewContext().getUser(), permissions))
            {
                ActionURL customizeURL = new ActionURL(CustomizeParticipantViewAction.class, c);
                customizeURL.addReturnURL(getViewContext().getActionURL());
                customizeURL.addParameter("participantId", _currentParticipantId);
                out.print("</td><td>");
                out.print(PageFlowUtil.textLink("Customize View", customizeURL));
            }

            if (_display != null)
            {
                out.print("</td><td class=\"labkey-form-label\">");
                out.print(PageFlowUtil.filter(_display));
            }
            out.print("</td></tr></table>");
        }

        public void setShowCustomizeLink(boolean showCustomizeLink)
        {
            _showCustomizeLink = showCustomizeLink;
        }
    }

    public static class ImportDatasetForm
    {
        private int datasetId = 0;
        private String typeURI;
        private String tsv;
        private String keys;
        private String _participantId;
        private String _sequenceNum;
        private String _name;
        private QueryUpdateService.InsertOption _insertOption = QueryUpdateService.InsertOption.IMPORT;

        public int getDatasetId()
        {
            return datasetId;
        }

        public void setDatasetId(int datasetId)
        {
            this.datasetId = datasetId;
        }

        public String getTsv()
        {
            return tsv;
        }

        public void setTsv(String tsv)
        {
            this.tsv = tsv;
        }

        public String getKeys()
        {
            return keys;
        }

        public void setKeys(String keys)
        {
            this.keys = keys;
        }

        public String getTypeURI()
        {
            return typeURI;
        }

        public void setTypeURI(String typeURI)
        {
            this.typeURI = typeURI;
        }

        public String getParticipantId()
        {
            return _participantId;
        }

        public void setParticipantId(String participantId)
        {
            _participantId = participantId;
        }

        public String getSequenceNum()
        {
            return _sequenceNum;
        }

        public void setSequenceNum(String sequenceNum)
        {
            _sequenceNum = sequenceNum;
        }

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }

        public QueryUpdateService.InsertOption getInsertOption()
        {
            return _insertOption;
        }

        public void setInsertOption(QueryUpdateService.InsertOption insertOption)
        {
            _insertOption = insertOption;
        }
    }

    public static class DatasetForm
    {
        private String _name;
        private String _label;
        private Integer _datasetId;
        private String _category;
        private boolean _showByDefault;
        private String _visitDatePropertyName;
        private String[] _visitStatus;
        private int[] _visitRowIds;
        private String _description;
        private Integer _cohortId;
        private boolean _demographicData;
        private boolean _create;

        public boolean isShowByDefault()
        {
            return _showByDefault;
        }

        public void setShowByDefault(boolean showByDefault)
        {
            _showByDefault = showByDefault;
        }

        public String getCategory()
        {
            return _category;
        }

        public void setCategory(String category)
        {
            _category = category;
        }

        public String getDatasetIdStr()
        {
            return _datasetId > 0 ? String.valueOf(_datasetId) : "";
        }

        /**
         * Don't blow up when posting bad value
         * @param datasetIdStr
         */
        public void setDatasetIdStr(String datasetIdStr)
        {
            try
            {
                if (null == StringUtils.trimToNull(datasetIdStr))
                    _datasetId = 0;
                else
                    _datasetId = Integer.parseInt(datasetIdStr);
            }
            catch (Exception x)
            {
                _datasetId = 0;
            }
        }

        public Integer getDatasetId()
        {
            return _datasetId;
        }

        public void setDatasetId(Integer datasetId)
        {
            _datasetId = datasetId;
        }

        public String getLabel()
        {
            return _label;
        }

        public void setLabel(String label)
        {
            _label = label;
        }

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }

        public String[] getVisitStatus()
        {
            return _visitStatus;
        }

        public void setVisitStatus(String[] visitStatus)
        {
            _visitStatus = visitStatus;
        }

        public int[] getVisitRowIds()
        {
            return _visitRowIds;
        }

        public void setVisitRowIds(int[] visitIds)
        {
            _visitRowIds = visitIds;
        }

        public String getVisitDatePropertyName()
        {
            return _visitDatePropertyName;
        }

        public void setVisitDatePropertyName(String visitDatePropertyName)
        {
            _visitDatePropertyName = visitDatePropertyName;
        }

        public String getDescription()
        {
            return _description;
        }

        public void setDescription(String description)
        {
            _description = description;
        }

        public boolean isDemographicData()
        {
            return _demographicData;
        }

        public void setDemographicData(boolean demographicData)
        {
            _demographicData = demographicData;
        }

        public boolean isCreate()
        {
            return _create;
        }

        public void setCreate(boolean create)
        {
            _create = create;
        }

        public Integer getCohortId()
        {
            return _cohortId;
        }

        public void setCohortId(Integer cohortId)
        {
            _cohortId = cohortId;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class DatasetsAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return StudyModule.datasetsPartFactory.getWebPartView(getViewContext(), StudyModule.datasetsPartFactory.createWebPart());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            _addNavTrail(root);
            root.addChild("Datasets");
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ViewDataAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new VBox(
                StudyModule.reportsPartFactory.getWebPartView(getViewContext(), StudyModule.reportsPartFactory.createWebPart()),
                StudyModule.datasetsPartFactory.getWebPartView(getViewContext(), StudyModule.datasetsPartFactory.createWebPart())
            );
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }

    public static class ReloadForm
    {
        private boolean _queryValidation;
        private boolean _failForUndefinedVisits;

        public boolean isQueryValidation()
        {
            return _queryValidation;
        }

        public void setQueryValidation(boolean queryValidation)
        {
            _queryValidation = queryValidation;
        }

        public boolean isFailForUndefinedVisits()
        {
            return _failForUndefinedVisits;
        }

        public void setFailForUndefinedVisits(boolean failForUndefinedVisits)
        {
            _failForUndefinedVisits = failForUndefinedVisits;
        }
    }

    private static class DatasetDetailRedirectForm extends ReturnUrlForm
    {
        private String _datasetId;
        private String _lsid;

        public String getDatasetId()
        {
            return _datasetId;
        }

        public void setDatasetId(String datasetId)
        {
            _datasetId = datasetId;
        }

        public String getLsid()
        {
            return _lsid;
        }

        public void setLsid(String lsid)
        {
            _lsid = lsid;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class ManageExternalReloadAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object form, BindException errors)
        {
            return new StudyJspView<>(getStudyRedirectIfNull(), "/org/labkey/study/view/manageExternalReload.jsp", form, errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            _addManageStudy(root);
            root.addChild("Manage External Reloading");
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class DatasetDetailRedirectAction extends SimpleRedirectAction<DatasetDetailRedirectForm>
    {
        @Override
        public URLHelper getRedirectURL(DatasetDetailRedirectForm form)
        {
            StudyImpl study = StudyManager.getInstance().getStudy(getContainer());
            if (study == null)
            {
                throw new NotFoundException("No study found");
            }
            // First try the dataset id as an entityid
            DatasetDefinition dataset = StudyManager.getInstance().getDatasetDefinitionByEntityId(study, form.getDatasetId());
            if (dataset == null)
            {
                try
                {
                    // Then try the dataset id as an integer
                    int id = Integer.parseInt(form.getDatasetId());
                    dataset = StudyManager.getInstance().getDatasetDefinition(study, id);
                }
                catch (NumberFormatException ignored) {}

                if (dataset == null)
                {
                    throw new NotFoundException("Could not find dataset " + form.getDatasetId());
                }
            }

            if (form.getLsid() == null)
            {
                throw new NotFoundException("No LSID specified");
            }

            StudyQuerySchema schema = StudyQuerySchema.createSchema(study, getUser(), true);

            QueryDefinition queryDef = QueryService.get().createQueryDefForTable(schema, dataset.getName());
            assert queryDef != null : "Dataset was found but couldn't get a corresponding TableInfo";

            ActionURL url = queryDef.urlFor(QueryAction.detailsQueryRow, getContainer(), Collections.singletonMap("lsid", form.getLsid()));
            String referrer = getViewContext().getRequest().getHeader("Referer");
            if (referrer != null)
            {
                url.addParameter(ActionURL.Param.returnUrl, referrer);
            }

            return url;
        }
    }


    @RequiresLogin
    @RequiresPermission(AdminPermission.class)
    public class CheckForReloadAction extends MutatingApiAction<ReloadForm>
    {
        @Override
        public ApiResponse execute(ReloadForm form, BindException errors) throws Exception
        {
            ReloadTask task = new ReloadTask();
            String message;

            try
            {
                User user = getUser();
                ImportOptions options = new ImportOptions(getContainer().getId(), user.getUserId());
                options.setFailForUndefinedVisits(form.isFailForUndefinedVisits());
                options.setSkipQueryValidation(!form.isQueryValidation());

                final String source;

                source = "a script invoking the \"CheckForReload\" action while authenticated as user \"" + user.getDisplayName(null) + "\"";

                ReloadStatus status = task.attemptReload(options, source);

                message = status.getMessage();
            }
            catch (ImportException e)
            {
                message = "Error: " + e.getMessage();
            }

            // Plain text response for scripts
            sendPlainText(message);
            return null;
        }
    }

    public static class ImportVisitMapForm
    {
        private String _content;

        public String getContent()
        {
            return _content;
        }

        public void setContent(String content)
        {
            _content = content;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class DemoModeAction extends FormViewAction<DemoModeForm>
    {
        @Override
        public URLHelper getSuccessURL(DemoModeForm form)
        {
            return null;
        }

        @Override
        public void validateCommand(DemoModeForm form, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(DemoModeForm form, boolean reshow, BindException errors)
        {
            return new JspView("/org/labkey/study/view/demoMode.jsp");
        }

        @Override
        public boolean handlePost(DemoModeForm form, BindException errors)
        {
            DemoMode.setDemoMode(getContainer(), getUser(), form.getMode());
            return false;  // Reshow page
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("demoMode");
            _addManageStudy(root);
            root.addChild("Demo Mode");
        }
    }


    public static class DemoModeForm
    {
        private boolean mode;

        public boolean getMode()
        {
            return mode;
        }

        public void setMode(boolean mode)
        {
            this.mode = mode;
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class ShowVisitImportMappingAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            return new JspView<>("/org/labkey/study/view/visitImportMapping.jsp", new ImportMappingBean(getStudyRedirectIfNull()));
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            _addNavTrailVisitAdmin(root);
            root.addChild("Visit Import Mapping");
        }
    }


    public static class ImportMappingBean
    {
        private final Collection<StudyManager.VisitAlias> _customMapping;
        private final Collection<StudyManager.VisitAlias> _standardMapping;

        public ImportMappingBean(Study study)
        {
            _customMapping = StudyManager.getInstance().getCustomVisitImportMapping(study);
            _standardMapping = StudyManager.getInstance().getStandardVisitImportMapping(study);
        }

        public Collection<StudyManager.VisitAlias> getCustomMapping()
        {
            return _customMapping;
        }

        public Collection<StudyManager.VisitAlias> getStandardMapping()
        {
            return _standardMapping;
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class ImportVisitAliasesAction extends FormViewAction<VisitAliasesForm>
    {
        @Override
        public URLHelper getSuccessURL(VisitAliasesForm form)
        {
            return new ActionURL(ShowVisitImportMappingAction.class, getContainer());
        }

        @Override
        public void validateCommand(VisitAliasesForm form, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(VisitAliasesForm form, boolean reshow, BindException errors)
        {
            getPageConfig().setFocusId("tsv");
            return new JspView<>("/org/labkey/study/view/importVisitAliases.jsp", null, errors);
        }

        @Override
        public boolean handlePost(VisitAliasesForm form, BindException errors)
        {
            boolean hadCustomMapping = !StudyManager.getInstance().getCustomVisitImportMapping(getStudyThrowIfNull()).isEmpty();

            try
            {
                String tsv = form.getTsv();

                if (null == tsv)
                {
                    errors.reject(ERROR_MSG, "Please insert tab-separated data with two columns, Name and SequenceNum");
                    return false;
                }

                StudyManager.getInstance().importVisitAliases(getStudyThrowIfNull(), getUser(), new TabLoader(form.getTsv(), true));
            }
            catch (RuntimeSQLException e)
            {
                if (e.isConstraintException())
                {
                    errors.reject(ERROR_MSG, "The visit import mapping includes duplicate visit names: " + e.getMessage());
                    return false;
                }
                else
                {
                    throw e;
                }
            }
            catch (ValidationException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                return false;
            }

            // TODO: Change to audit log
            _log.info("The visit import custom mapping was " + (hadCustomMapping ? "replaced" : "imported"));

            return true;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            _addNavTrailVisitAdmin(root);
            root.addChild("Import Visit Aliases");
        }
    }


    public static class VisitAliasesForm
    {
        private String _tsv;

        public String getTsv()
        {
            return _tsv;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setTsv(String tsv)
        {
            _tsv = tsv;
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class ClearVisitAliasesAction extends ConfirmAction
    {
        @Override
        public ModelAndView getConfirmView(Object o, BindException errors)
        {
            if (getPageConfig().getTitle() == null)
                setTitle("Clear Custom Mapping");

            return new HtmlView("Are you sure you want to delete the visit import custom mapping for this study?");
        }

        @Override
        public boolean handlePost(Object o, BindException errors)
        {
            StudyManager.getInstance().clearVisitAliases(getStudyThrowIfNull());
            // TODO: Change to audit log
            _log.info("The visit import custom mapping was cleared");

            return true;
        }

        @Override
        public void validateCommand(Object o, Errors errors)
        {
        }

        @Override
        public URLHelper getSuccessURL(Object o)
        {
            return new ActionURL(ShowVisitImportMappingAction.class, getContainer());
        }
    }

    @RequiresPermission(ReadPermission.class) @RequiresLogin
    public class ManageParticipantCategoriesAction extends SimpleViewAction<SentGroupForm>
    {
        @Override
        public ModelAndView getView(SentGroupForm form, BindException errors)
        {
            // if the user is viewing a sent participant group, remove any notifications related to it
            if (form.getGroupId() != null)
            {
                NotificationService.get().removeNotifications(getContainer(), form.getGroupId().toString(),
                    Collections.singletonList(ParticipantCategory.SEND_PARTICIPANT_GROUP_TYPE), getUser().getUserId());
            }

            return new JspView("/org/labkey/study/view/manageParticipantCategories.jsp");
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("participantGroups");
            _addManageStudy(root);
            root.addChild("Manage " + getStudyRedirectIfNull().getSubjectNounSingular() + " Groups");
        }
    }

    public static class SentGroupForm
    {
        private Integer _groupId;

        public Integer getGroupId()
        {
            return _groupId;
        }

        public void setGroupId(Integer groupId)
        {
            _groupId = groupId;
        }
    }

    @RequiresLogin @RequiresPermission(ReadPermission.class)
    public class SendParticipantGroupAction extends FormViewAction<SendParticipantGroupForm>
    {
        List<User> _validRecipients = new ArrayList<>();

        @Override
        public URLHelper getSuccessURL(SendParticipantGroupForm form)
        {
            return form.getReturnActionURL(form.getDefaultUrl(getContainer()));
        }

        @Override
        public ModelAndView getView(SendParticipantGroupForm form, boolean reshow, BindException errors)
        {
            if (form.getRowId() == null)
            {
                return new HtmlView("<span class='labkey-error'>No participant group RowId provided.</span>");
            }
            else
            {
                ParticipantGroup group = ParticipantGroupManager.getInstance().getParticipantGroup(getContainer(), getUser(), form.getRowId());
                if (group != null)
                {
                    ParticipantCategoryImpl category = ParticipantGroupManager.getInstance().getParticipantCategory(getContainer(), getUser(), group.getCategoryId());
                    if (category != null && category.canRead(getContainer(), getUser()))
                    {
                        form.setLabel(group.getLabel());
                        return new JspView<>("/org/labkey/study/view/sendParticipantGroup.jsp", form, errors);
                    }
                }

                return new HtmlView("<span class='labkey-error'>Could not find participant group for RowId " + form.getRowId()
                        + " or you do not have permission to read it.</span>");
            }
        }

        @Override
        public void validateCommand(SendParticipantGroupForm form, Errors errors)
        {
            _validRecipients = SecurityManager.parseRecipientListForContainer(getContainer(), form.getRecipientList(), errors);
        }

        @Override
        public boolean handlePost(SendParticipantGroupForm form, BindException errors) throws Exception
        {
            if (!errors.hasErrors() && !_validRecipients.isEmpty())
            {
                for (User recipient : _validRecipients)
                {
                    NotificationService.get().sendMessageForRecipient(
                        getContainer(), getUser(), recipient,
                        form.getMessageSubject(), form.getMessageBody(), form.getSendGroupUrl(getContainer()),
                        form.getRowId().toString(), ParticipantCategory.SEND_PARTICIPANT_GROUP_TYPE
                    );

                    String auditMsg = "The following participant group was shared: recipient: " + recipient.getName() + " (" + recipient.getUserId() + ")"
                            + ", groupId: " + form.getRowId() + ", name: " + form.getLabel();
                    StudyService.get().addStudyAuditEvent(getContainer(), getUser(), auditMsg);
                }
            }

            return !errors.hasErrors();
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("participantGroups");
            String manageGroupsTitle = "Manage " + getStudyRedirectIfNull().getSubjectNounSingular() + " Groups";
            root.addChild(manageGroupsTitle, new ActionURL(ManageParticipantCategoriesAction.class, getContainer()));
            root.addChild("Send Participant Group");
        }
    }

    public static class SendParticipantGroupForm extends ReturnUrlForm
    {
        private Integer _rowId;
        private String _label;
        private String _recipientList;
        private String _messageSubject;
        private String _messageBody;

        public Integer getRowId()
        {
            return _rowId;
        }

        public void setRowId(Integer rowId)
        {
            _rowId = rowId;
        }

        public String getLabel()
        {
            return _label;
        }

        public void setLabel(String label)
        {
            _label = label;
        }

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
            return new ActionURL(ManageParticipantCategoriesAction.class, container);
        }

        public ActionURL getSendGroupUrl(Container container)
        {
            ActionURL sendGroupUrl = getReturnActionURL(getDefaultUrl(container));
            sendGroupUrl.addParameter("groupId", getRowId());
            return sendGroupUrl;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class ManageAlternateIdsAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object form, BindException errors)
        {
            ChangeAlternateIdsForm changeAlternateIdsForm = getChangeAlternateIdForm(getStudyRedirectIfNull());
            return new JspView<>("/org/labkey/study/view/manageAlternateIds.jsp", changeAlternateIdsForm);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("alternateIDs");
            _addManageStudy(root);
            String subjectNoun = getStudyRedirectIfNull().getSubjectNounSingular();
            root.addChild("Manage Alternate " + subjectNoun + " IDs and " + subjectNoun + " Aliases");
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class MergeParticipantsAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object form, BindException errors)
        {
           return new JspView<>("/org/labkey/study/view/mergeParticipants.jsp");
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            _addManageStudy(root);
            String subjectColumnName = getStudyRedirectIfNull().getSubjectColumnName();
            root.addChild("Merge " + subjectColumnName + "s");
          }
    }

    @RequiresPermission(ReadPermission.class)
    public class SubjectListAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            return new SubjectsWebPart(getViewContext(), true, 0);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class BrowseStudyScheduleAction extends MutatingApiAction<BrowseStudyForm>
    {
        @Override
        public ApiResponse execute(BrowseStudyForm browseDataForm, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            StudyManager manager = StudyManager.getInstance();
            Study study = manager.getStudy(getContainer());
            StudySchedule schedule = new StudySchedule();
            CohortImpl cohort = null;

            if (browseDataForm.getCohortId() != null)
            {
                cohort = manager.getCohortForRowId(getContainer(), getUser(), browseDataForm.getCohortId());
            }

            if (cohort == null && browseDataForm.getCohortLabel() != null)
            {
                cohort = manager.getCohortByLabel(getContainer(), getUser(), browseDataForm.getCohortLabel());
            }

            if (study != null)
            {
                schedule.setVisits(manager.getVisits(study, cohort, getUser(), Visit.Order.DISPLAY));
                schedule.setDatasets(
                        manager.getDatasetDefinitions(study, cohort, Dataset.TYPE_STANDARD, Dataset.TYPE_PLACEHOLDER),
                        DataViewService.get().getViews(getViewContext(), Collections.singletonList(DatasetViewProvider.TYPE)));

                response.put("schedule", schedule.toJSON(getUser()));
                response.put("success", true);

                return response;
            }
            else
                throw new IllegalStateException("A study does not exist in this folder");
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class GetStudyTimepointsAction extends MutatingApiAction<BrowseStudyForm>
    {
        @Override
        public ApiResponse execute(BrowseStudyForm browseDataForm, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            StudyManager manager = StudyManager.getInstance();
            Study study = manager.getStudy(getContainer());
            StudySchedule schedule = new StudySchedule();
            CohortImpl cohort = null;

            if (browseDataForm.getCohortId() != null)
            {
                cohort = manager.getCohortForRowId(getContainer(), getUser(), browseDataForm.getCohortId());
            }

            if (cohort == null && browseDataForm.getCohortLabel() != null)
            {
                cohort = manager.getCohortByLabel(getContainer(), getUser(), browseDataForm.getCohortLabel());
            }

            if (study != null)
            {
                schedule.setVisits(manager.getVisits(study, cohort, getUser(), Visit.Order.DISPLAY));

                response.put("schedule", schedule.toJSON(getUser()));
                response.put("success", true);

                return response;
            }
            else
                throw new IllegalStateException("A study does not exist in this folder");
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class UpdateStudyScheduleAction extends MutatingApiAction<StudySchedule>
    {
        @Override
        public void validateForm(StudySchedule form, Errors errors)
        {
            if (form.getSchedule().size() <= 0)
                errors.reject(ERROR_MSG, "No study schedule records have been specified");
        }

        @Override
        public ApiResponse execute(StudySchedule form, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            Study study = StudyManager.getInstance().getStudy(getContainer());

            if (study != null)
            {
                for (Map.Entry<Integer, List<VisitDataset>> entry : form.getSchedule().entrySet())
                {
                    Dataset ds = StudyService.get().getDataset(getContainer(), entry.getKey());
                    if (ds != null)
                    {
                        for (VisitDataset visit : entry.getValue())
                        {
                            VisitDatasetType type = visit.isRequired() ? VisitDatasetType.REQUIRED : VisitDatasetType.NOT_ASSOCIATED;

                            StudyManager.getInstance().updateVisitDatasetMapping(getUser(), getContainer(),
                                    visit.getVisitRowId(), ds.getDatasetId(), type);
                        }
                    }
                }
                response.put("success", true);

                return response;
            }
            else
                throw new IllegalStateException("A study does not exist in this folder");
        }
    }

    public static class BrowseStudyForm
    {
        private Integer _cohortId;
        private String _cohortLabel;

        public Integer getCohortId()
        {
            return _cohortId;
        }

        public void setCohortId(Integer cohortId)
        {
            _cohortId = cohortId;
        }

        public String getCohortLabel()
        {
            return _cohortLabel;
        }

        public void setCohortLabel(String cohortLabel)
        {
            _cohortLabel = cohortLabel;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class DefineDatasetAction extends MutatingApiAction<DefineDatasetForm>
    {
        private StudyImpl _study;

        @Override
        public void validateForm(DefineDatasetForm form, Errors errors)
        {
            _study = StudyManager.getInstance().getStudy(getContainer());

            if (_study != null)
            {
                switch (form.getType())
                {
                    case defineManually:
                    case placeHolder:
                        if (StringUtils.isEmpty(form.getName()))
                            errors.reject(ERROR_MSG, "A Dataset name must be specified.");
                        else if (StudyManager.getInstance().getDatasetDefinitionByName(_study, form.getName()) != null)
                            errors.reject(ERROR_MSG, "A Dataset named: " + form.getName() + " already exists in this folder.");
                        break;

                    case linkToTarget:
                        if (form.getExpectationDataset() == null || form.getTargetDataset() == null)
                            errors.reject(ERROR_MSG, "An expectation Dataset and target Dataset must be specified.");
                        break;

                    case linkManually:
                        if (form.getExpectationDataset() == null)
                            errors.reject(ERROR_MSG, "An expectation Dataset must be specified.");
                        break;
                }
            }
            else
                errors.reject(ERROR_MSG, "A study does not exist in this folder");
        }

        @Override
        public ApiResponse execute(DefineDatasetForm form, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            DatasetDefinition def;

            DbScope scope = StudySchema.getInstance().getSchema().getScope();

            try (DbScope.Transaction transaction = scope.ensureTransaction())
            {
                Integer categoryId = null;

                if (form.getCategory() != null)
                {
                    ViewCategory category = ViewCategoryManager.getInstance().ensureViewCategory(getContainer(), getUser(), form.getCategory().getLabel());
                    categoryId = category.getRowId();
                }

                switch (form.getType())
                {
                    case defineManually:
                        def = StudyPublishManager.getInstance().createDataset(getUser(), new DatasetDefinition.Builder(form.getName())
                                .setStudy(_study)
                                .setDemographicData(false)
                                .setCategoryId(categoryId));
                        def.provisionTable();

                        ActionURL redirect = new ActionURL(EditTypeAction.class, getContainer()).addParameter(Dataset.DATASETKEY, def.getDatasetId());
                        response.put("redirectUrl", redirect.getLocalURIString());
                        break;
                    case placeHolder:
                        def = StudyPublishManager.getInstance().createDataset(getUser(), new DatasetDefinition.Builder(form.getName())
                                .setStudy(_study)
                                .setDemographicData(false)
                                .setType(Dataset.TYPE_PLACEHOLDER)
                                .setCategoryId(categoryId));
                        def.provisionTable();
                        response.put("datasetId", def.getDatasetId());
                        break;

                    case linkManually:
                        def = StudyManager.getInstance().getDatasetDefinition(_study, form.getExpectationDataset());
                        if (def != null)
                        {
                            def = def.createMutable();

                            def.setType(Dataset.TYPE_STANDARD);
                            def.save(getUser());

                            // add a cancel url to rollback either the manual link or import from file link
                            ActionURL cancelURL = new ActionURL(CancelDefineDatasetAction.class, getContainer()).addParameter("expectationDataset", form.getExpectationDataset());

                            redirect = new ActionURL(EditTypeAction.class, getContainer()).addParameter(Dataset.DATASETKEY, form.getExpectationDataset());
                            redirect.addCancelURL(cancelURL);
                            response.put("redirectUrl", redirect.getLocalURIString());
                        }
                        else
                            throw new IllegalArgumentException("The expectation Dataset did not exist");
                        break;

                    case linkToTarget:
                        DatasetDefinition expectationDataset = StudyManager.getInstance().getDatasetDefinition(_study, form.getExpectationDataset());
                        DatasetDefinition targetDataset = StudyManager.getInstance().getDatasetDefinition(_study, form.getTargetDataset());

                        StudyManager.getInstance().linkPlaceHolderDataset(_study, getUser(), expectationDataset, targetDataset);
                        break;
                }
                response.put("success", true);
                transaction.commit();
            }

            return response;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class CancelDefineDatasetAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object form, BindException errors)
        {
            // switch the dataset back to a placeholder type
            Study study = getStudy(getContainer());
            if (study != null)
            {
                String expectationDataset = getViewContext().getActionURL().getParameter("expectationDataset");
                if (NumberUtils.isDigits(expectationDataset))
                {
                    DatasetDefinition def = StudyManager.getInstance().getDatasetDefinition(study, NumberUtils.toInt(expectationDataset));
                    if (def != null)
                    {
                        def = def.createMutable();

                        def.setType(Dataset.TYPE_PLACEHOLDER);
                        def.save(getUser());
                    }
                }
            }
            throw new RedirectException(new ActionURL(StudyScheduleAction.class, getContainer()));
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }

    public static class DefineDatasetForm implements CustomApiForm, HasViewContext
    {
        enum Type
        {
            defineManually,
            placeHolder,
            linkToTarget,
            linkManually,
        }

        private ViewContext _context;
        private DefineDatasetForm.Type _type;
        private String _name;
        private ViewCategory _category;
        private Integer _expectationDataset;
        private Integer _targetDataset;

        public Type getType()
        {
            return _type;
        }

        public String getName()
        {
            return _name;
        }

        public ViewCategory getCategory()
        {
            return _category;
        }

        public Integer getExpectationDataset()
        {
            return _expectationDataset;
        }

        public Integer getTargetDataset()
        {
            return _targetDataset;
        }

        @Override
        public void bindProperties(Map<String, Object> props)
        {
            Object categoryProp = props.get("category");
            if (categoryProp instanceof JSONObject)
            {
                _category = ViewCategory.fromJSON(_context.getContainer(), (JSONObject)categoryProp);
            }

            _name = (String)props.get("name");

            Object type = props.get("type");
            if (type instanceof String)
                _type = Type.valueOf((String)type);

            _expectationDataset = (Integer)props.get("expectationDataset");
            _targetDataset = (Integer)props.get("targetDataset");
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

    public static class ChangeAlternateIdsForm
    {
        private String _prefix = "";
        private int _numDigits = StudyManager.ALTERNATEID_DEFAULT_NUM_DIGITS;
        private int _aliasDatasetId = -1;
        private String _aliasColumn = "";
        private String _sourceColumn = "";

        public String getAliasColumn()
        {
            return _aliasColumn;
        }

        public void setAliasColumn(String aliasColumn)
        {
            _aliasColumn = aliasColumn;
        }

        public String getSourceColumn()
        {
            return _sourceColumn;
        }

        public void setSourceColumn(String sourceColumn)
        {
            _sourceColumn = sourceColumn;
        }

        public String getPrefix()
        {
            return _prefix;
        }

        public void setPrefix(String prefix)
        {
            _prefix = prefix;
        }

        public int getNumDigits()
        {
            return _numDigits;
        }

        public void setNumDigits(int numDigits)
        {
            _numDigits = numDigits;
        }

        public int getAliasDatasetId()
        {
            return _aliasDatasetId;
        }
        public void setAliasDatasetId(int aliasDatasetId)
        {
            _aliasDatasetId = aliasDatasetId;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class ChangeAlternateIdsAction extends MutatingApiAction<ChangeAlternateIdsForm>
    {
        @Override
        public ApiResponse execute(ChangeAlternateIdsForm form, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            StudyImpl study = StudyManager.getInstance().getStudy(getContainer());
            if (study != null)
            {
                setAlternateIdProperties(study, form.getPrefix(), form.getNumDigits());
                StudyManager.getInstance().clearAlternateParticipantIds(study);
                response.put("success", true);
                return response;
            }
            else
                throw new IllegalStateException("A study does not exist in this folder");
        }
    }

    public static class MapAliasIdsForm
    {
        private int _datasetId;
        private String _aliasColumn = "";
        private String _sourceColumn = "";

        public int getDatasetId()
        {
            return _datasetId;
        }

        public void setDatasetId(int datasetId)
        {
            _datasetId = datasetId;
        }

        public String getAliasColumn()
        {
            return _aliasColumn;
        }

        public void setAliasColumn(String aliasColumn)
        {
            _aliasColumn = aliasColumn;
        }

        public String getSourceColumn()
        {
            return _sourceColumn;
        }

        public void setSourceColumn(String sourceColumn)
        {
            _sourceColumn = sourceColumn;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class MapAliasIdsAction extends MutatingApiAction<MapAliasIdsForm>
    {
        @Override
        public ApiResponse execute(MapAliasIdsForm form, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            StudyImpl study = StudyManager.getInstance().getStudy(getContainer());
            if (study != null)
            {
                setAliasMappingProperties(study, form.getDatasetId(), form.getAliasColumn(), form.getSourceColumn());
                StudyManager.getInstance().clearAlternateParticipantIds(study);
                response.put("success", true);
                return response;
            }
            else
                throw new IllegalStateException("A study does not exist in this folder");
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class ExportParticipantTransformsAction extends FormHandlerAction<Object>
    {
        @Override
        public void validateCommand(Object target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(Object o, BindException errors) throws Exception
        {
            Study study = StudyManager.getInstance().getStudy(getContainer());
            if (study != null)
            {
                // Ensure alternateIds are generated for all participants
                StudyManager.getInstance().generateNeededAlternateParticipantIds(study, getUser());

                TableInfo ti = StudySchema.getInstance().getTableInfoParticipant();
                List<ColumnInfo> cols = new ArrayList<>();
                cols.add(ti.getColumn("participantid"));
                cols.add(ti.getColumn("alternateid"));
                cols.add(ti.getColumn("dateoffset"));
                SimpleFilter filter = new SimpleFilter();
                filter.addCondition(ti.getColumn("container"), getContainer());
                ResultsFactory factory = ()->QueryService.get().select(ti, cols, filter, new Sort("participantid"));

                // NOTE: TSVGridWriter closes PrintWriter and ResultSet
                try (TSVGridWriter writer = new TSVGridWriter(factory))
                {
                    writer.setApplyFormats(false);
                    writer.setFilenamePrefix("ParticipantTransforms");
                    writer.setColumnHeaderType(ColumnHeaderType.DisplayFieldKey); // CONSIDER: Use FieldKey instead
                    writer.write(getViewContext().getResponse());
                }

                return false;  // Don't redirect
            }
            else
                throw new IllegalStateException("A study does not exist in this folder");
        }

        @Override
        public URLHelper getSuccessURL(Object o)
        {
            throw new IllegalStateException();
        }
    }

    public static ChangeAlternateIdsForm getChangeAlternateIdForm(StudyImpl study)
    {
        ChangeAlternateIdsForm changeAlternateIdsForm = new ChangeAlternateIdsForm();
        changeAlternateIdsForm.setPrefix(study.getAlternateIdPrefix());
        changeAlternateIdsForm.setNumDigits(study.getAlternateIdDigits());
        if (study.getParticipantAliasDatasetId() != null)
        {
            changeAlternateIdsForm.setAliasDatasetId(study.getParticipantAliasDatasetId());
            changeAlternateIdsForm.setAliasColumn(study.getParticipantAliasProperty());
            changeAlternateIdsForm.setSourceColumn(study.getParticipantAliasSourceProperty());
        }

        return changeAlternateIdsForm;
    }

    private void setAlternateIdProperties(StudyImpl study, String prefix, int numDigits)
    {
        study = study.createMutable();
        study.setAlternateIdPrefix(prefix);
        study.setAlternateIdDigits(numDigits);
        StudyManager.getInstance().updateStudy(getUser(), study);
    }

    private void setAliasMappingProperties(StudyImpl study, int datasetId, String aliasColumn, String sourceColumn)
    {
        study = study.createMutable();
        study.setParticipantAliasDatasetId(datasetId);
        study.setParticipantAliasProperty(aliasColumn);
        study.setParticipantAliasSourceProperty(sourceColumn);
        StudyManager.getInstance().updateStudy(getUser(), study);
    }

    @RequiresPermission(ManageStudyPermission.class)
    public class ImportAlternateIdMappingAction extends AbstractQueryImportAction<IdForm>
    {
        private Study _study;
        private int _requestId = -1;

        @Override
        protected void initRequest(IdForm form) throws ServletException
        {
            _requestId = form.getId();
            setHasColumnHeaders(true);
            if (null != getStudy())
            {
                _study = getStudy();
                setImportMessage("Upload a mapping of " + _study.getSubjectNounPlural() + " to Alternate IDs and date offsets from a TXT, CSV or Excel file or paste the mapping directly into the text box below. " +
                    "There must be a header row, which must contain ParticipantId and either AlternateId, DateOffset or both. Click the button below to export the current mapping.");
            }
            setTarget(StudySchema.getInstance().getTableInfoParticipant());
            setHideTsvCsvCombo(true);
            setSuccessMessageSuffix("uploaded");
        }

        @Override
        public ModelAndView getView(IdForm form, BindException errors) throws Exception
        {
            _study = getStudyThrowIfNull();
            initRequest(form);
            return getDefaultImportView(form, errors);
        }

        @Override
        protected void validatePermission(User user, BindException errors)
        {
            checkPermissions();
        }

        @Override
        protected boolean canInsert(User user)
        {
            return getContainer().hasPermission(user, ManageStudyPermission.class);
        }

        @Override
        protected int importData(DataLoader dl, FileStream file, String originalName, BatchValidationException errors, @Nullable AuditBehaviorType auditBehaviorType, TransactionAuditProvider.@Nullable TransactionAuditEvent auditEvent) throws IOException
        {
            if (null == _study)
                return 0;
            int rows = StudyManager.getInstance().setImportedAlternateParticipantIds(_study, dl, errors);

            // Insert a clear warning at the top that the mappings have not been imported, #36517
            if (errors.hasErrors())
            {
                List<ValidationException> rowErrors = errors.getRowErrors();
                int count = rowErrors.size();
                rowErrors.add(0, new ValidationException("Warning: NONE of participant mappings have been imported because this mapping file contains " + (1 == count ? "an error" : "errors") + "! Please correct the following:"));
            }

            return rows;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Upload " + _study.getSubjectNounSingular() + " Mapping");
        }

        @Override
        protected ActionURL getSuccessURL(IdForm form)
        {
            ActionURL actionURL = new ActionURL(ManageAlternateIdsAction.class, getContainer());
            return actionURL;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class SnapshotSettingsAction extends FormViewAction<SnapshotSettingsForm>
    {
        private StudyImpl _study;

        @Override
        public ModelAndView getView(SnapshotSettingsForm form, boolean reshow, BindException errors)
        {
            _study = getStudyRedirectIfNull();
            StudySnapshot snapshot = StudyManager.getInstance().getStudySnapshot(_study.getStudySnapshot());

            if (null == snapshot)
            {
                errors.reject(null, "This is not a published or ancillary study");
                return new SimpleErrorView(errors);
            }
            else
            {
                return new JspView<>("/org/labkey/study/view/snapshotSettings.jsp", snapshot);
            }
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("studyPubRefresh");
            _addManageStudy(root);
            root.addChild((_study.getStudySnapshotType() != null ? _study.getStudySnapshotType().getTitle() : "") + " Study Settings");
        }

        @Override
        public void validateCommand(SnapshotSettingsForm form, Errors errors)
        {
        }

        @Override
        public boolean handlePost(SnapshotSettingsForm form, BindException errors)
        {
            StudyImpl study = getStudyRedirectIfNull();
            StudySnapshot snapshot = StudyManager.getInstance().getStudySnapshot(study.getStudySnapshot());
            assert null != snapshot;
            snapshot.setRefresh(form.isRefresh());
            StudyManager.getInstance().updateStudySnapshot(snapshot, getUser());
            return false;
        }

        @Override
        public URLHelper getSuccessURL(SnapshotSettingsForm form)
        {
            return new ActionURL(getClass(), getContainer());
        }
    }

    public static class SnapshotSettingsForm
    {
        private boolean _refresh = false;

        public boolean isRefresh()
        {
            return _refresh;
        }

        public void setRefresh(boolean refresh)
        {
            _refresh = refresh;
        }
    }

    /**
     * Set up the site wide settings for a master patient provider
     */
    @RequiresPermission(AdminPermission.class)
    public class MasterPatientProviderAction extends FormViewAction<MasterPatientProviderSettings>
    {
        @Override
        public void validateCommand(MasterPatientProviderSettings form, Errors errors)
        {
            if (!form.isValid())
                errors.reject(ERROR_MSG, "All required fields are not specified");
        }

        @Override
        public ModelAndView getView(MasterPatientProviderSettings form, boolean reshow, BindException errors) throws Exception
        {
            return new JspView<>("/org/labkey/study/view/masterPatientProvider.jsp", form, errors);
        }

        @Override
        public boolean handlePost(MasterPatientProviderSettings form, BindException errors) throws Exception
        {
            if (form.getType() != null)
            {
                try (DbScope.Transaction transaction = StudySchema.getInstance().getScope().ensureTransaction())
                {
                    MasterPatientIndexService svc = MasterPatientIndexService.getProvider(form.getType());
                    if (svc != null)
                    {
                        PropertyManager.PropertyMap map = PropertyManager.getNormalStore().getWritableProperties(MasterPatientProviderSettings.CATEGORY, true);

                        map.put(MasterPatientProviderSettings.TYPE, form.getType());
                        map.save();

                        svc.setServerSettings(form);
                        transaction.commit();
                    }
                }
            }
            return true;
        }

        @Override
        public URLHelper getSuccessURL(MasterPatientProviderSettings form)
        {
            return urlProvider(AdminUrls.class).getAdminConsoleURL();
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            urlProvider(AdminUrls.class).addAdminNavTrail(root, "Configure Master Patient Index", getClass(), getContainer());
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class TestMasterPatientProviderAction extends MutatingApiAction<MasterPatientProviderSettings>
    {
        @Override
        public void validateForm(MasterPatientProviderSettings form, Errors errors)
        {
            if (!form.isValid())
                errors.reject(ERROR_MSG, "All required fields are not specified");
        }

        @Override
        public Object execute(MasterPatientProviderSettings form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            if (form.getType() != null)
            {
                MasterPatientIndexService svc = MasterPatientIndexService.getProvider(form.getType());
                if (svc != null)
                {
                    if (svc.checkServerSettings(form))
                    {
                        response.put("success", true);
                        response.put("message", "The specified settings are valid.");
                    }
                    else
                    {
                        response.put("success", false);
                        response.put("message", "The specified settings are not valid.");
                    }
                }
            }
            return response;
        }
    }

    public static class MasterPatientProviderSettings extends MasterPatientIndexService.ServerSettings
    {
        public static final String CATEGORY = "MASTER_PATIENT_PROVIDER";
        public static final String TYPE = "TYPE";

        private String _type;

        public String getType()
        {
            return _type;
        }

        public void setType(String type)
        {
            _type = type;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class ConfigureMasterPatientSettingsAction extends FormViewAction<MasterPatientIndexService.FolderSettings>
    {
        private MasterPatientIndexService _svc;

        @Override
        public void validateCommand(MasterPatientIndexService.FolderSettings form, Errors errors)
        {
            if (!form.isValid())
                errors.reject(ERROR_MSG, "All required fields are not specified");
        }

        @Override
        public ModelAndView getView(MasterPatientIndexService.FolderSettings form, boolean reshow, BindException errors) throws Exception
        {
            return new JspView<>("/org/labkey/study/view/manageMasterPatientConfig.jsp", getService(), errors);
        }

        @Override
        public boolean handlePost(MasterPatientIndexService.FolderSettings form, BindException errors) throws Exception
        {
            MasterPatientIndexService svc = getService();
            if (svc != null)
            {
                form.setReloadUser(getUser().getUserId());
                svc.setFolderSettings(getContainer(), form);
            }
            return true;
        }

        @Override
        public URLHelper getSuccessURL(MasterPatientIndexService.FolderSettings form)
        {
            return new ActionURL(ManageStudyAction.class, getContainer());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            MasterPatientIndexService svc = getService();
            if (svc != null)
                root.addChild("Manage " + svc.getName() + " Configuration");
            else
                root.addChild("Manage Master Patient Index Configuration");
        }

        private MasterPatientIndexService getService()
        {
            if (_svc == null)
            {
                _svc = MasterPatientIndexMaintenanceTask.getConfiguredService();
            }
            return _svc;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class RefreshMasterPatientIndexAction extends MutatingApiAction<Object>
    {
        @Override
        public ApiResponse execute(Object o, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            try
            {
                ViewBackgroundInfo info = new ViewBackgroundInfo(getContainer(), getUser(), getViewContext().getActionURL());
                MasterPatientIndexService svc = MasterPatientIndexMaintenanceTask.getConfiguredService();

                MasterPatientIndexService.FolderSettings settings = svc.getFolderSettings(getContainer());
                if (settings.isEnabled())
                {
                    PipelineJob job = new MasterPatientIndexUpdateTask(info, PipelineService.get().findPipelineRoot(getContainer()), svc);

                    PipelineService.get().queueJob(job);

                    response.put("success", true);
                    response.put(ActionURL.Param.returnUrl.name(), urlProvider(PipelineUrls.class).urlBegin(getContainer()));
                }
                else
                {
                    response.put("success", false);
                    response.put("message", "The specified configuration is not enabled.");
                }
            }
            catch (PipelineValidationException e)
            {
                throw new IOException(e);
            }
            return response;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class DeleteMasterPatientRecordsAction extends MutatingApiAction<DeleteMPIForm>
    {
        @Override
        public ApiResponse execute(DeleteMPIForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            List<Pair<String, String>> params = form.getParams();
            MasterPatientIndexService svc = MasterPatientIndexMaintenanceTask.getConfiguredService();
            if (svc != null && params.size() > 0)
            {
                int count = svc.deleteMatchingRecords(params);

                response.put("success", true);
                response.put("count", count);
            }
            return response;
        }
    }

    public static class DeleteMPIForm implements CustomApiForm
    {
        private final List<Pair<String, String>> _params = new ArrayList<>();

        public List<Pair<String, String>> getParams()
        {
            return _params;
        }

        @Override
        public void bindProperties(Map<String, Object> props)
        {
            for (Map.Entry<String, Object> entry : props.entrySet())
            {
                _params.add(new Pair<>(entry.getKey(), String.valueOf(entry.getValue())));
            }
        }
    }

    // Hidden action that allows re-running of the specimen module enabling upgrade process. Could be useful on
    // deployments that add the specimen module after the original upgrade runs.
    @RequiresSiteAdmin
    public static class EnableSpecimenModuleAction extends ConfirmAction<Object>
    {
        @Override
        public ModelAndView getConfirmView(Object o, BindException errors)
        {
            return new HtmlView(HtmlString.of("Are you sure you want to enable the specimen module in all study folders that have specimen rows?"));
        }

        @Override
        public boolean handlePost(Object o, BindException errors) throws Exception
        {
            StudyManager.getInstance().enableSpecimenModuleInStudyFolders(getUser());
            return true;
        }

        @Override
        public void validateCommand(Object o, Errors errors)
        {
        }

        @Override
        public @NotNull URLHelper getSuccessURL(Object o)
        {
            return urlProvider(ProjectUrls.class).getBeginURL(getContainer());
        }
    }
}
