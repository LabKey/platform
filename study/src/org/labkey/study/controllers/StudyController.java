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

package org.labkey.study.controllers;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.commons.lang.time.FastDateFormat;
import org.apache.log4j.Logger;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionMapping;
import org.labkey.api.action.*;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.*;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.LsidManager;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusUrls;
import org.labkey.api.query.*;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.ChartQueryReport;
import org.labkey.api.reports.report.ChartReportDescriptor;
import org.labkey.api.reports.report.RReport;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.view.ChartDesignerBean;
import org.labkey.api.reports.report.view.ChartUtil;
import org.labkey.api.reports.report.view.RReportBean;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.assay.AssayPublishService;
import org.labkey.api.util.*;
import org.labkey.api.view.*;
import org.labkey.api.view.template.DialogTemplate;
import org.labkey.api.view.template.PrintTemplate;
import org.labkey.common.tools.TabLoader;
import org.labkey.common.util.Pair;
import org.labkey.study.SampleManager;
import org.labkey.study.StudyModule;
import org.labkey.study.StudySchema;
import org.labkey.study.StudyServiceImpl;
import org.labkey.study.assay.AssayPublishManager;
import org.labkey.study.assay.query.AssayAuditViewFactory;
import org.labkey.study.controllers.reports.ReportsController;
import org.labkey.study.importer.VisitMapImporter;
import org.labkey.study.model.*;
import org.labkey.study.pipeline.DatasetBatch;
import org.labkey.study.pipeline.StudyPipeline;
import org.labkey.study.query.DataSetQueryView;
import org.labkey.study.query.PublishedRecordQueryView;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.study.reports.ChartReportView;
import org.labkey.study.reports.ReportManager;
import org.labkey.study.reports.StudyChartQueryReport;
import org.labkey.study.reports.StudyRReport;
import org.labkey.study.view.DataHeader;
import org.labkey.study.visitmanager.VisitManager;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Nov 28, 2007
 */
public class StudyController extends BaseStudyController
{
    static Logger _log = Logger.getLogger(StudyController.class);

    private static ActionResolver ACTION_RESOLVER = new DefaultActionResolver(StudyController.class);
    private static final String PARTICIPANT_CACHE_PREFIX = "Study_participants/participantCache";
    private static final String EXPAND_CONTAINERS_KEY = StudyController.class.getName() + "/expandedContainers";

    public StudyController()
    {
        super();
        setActionResolver(ACTION_RESOLVER);
    }

    @RequiresPermission(ACL.PERM_READ)
    public class BeginAction extends SimpleViewAction
    {
        private Study _study;
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            _study = getStudy(true);

            WebPartView overview = StudyModule.manageStudyPartFactory.getWebPartView(getViewContext(), null);
            PrintTemplate template = new PrintTemplate(overview);
            template.setView("right", StudyModule.reportsPartFactory.getWebPartView(getViewContext(), null));

            return template;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild(_study == null ? "No Study In Folder" : _study.getLabel());
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class DefineDatasetTypeAction extends FormViewAction<ImportTypeForm>
    {
        private DataSetDefinition _def;
        public ModelAndView getView(ImportTypeForm form, boolean reshow, BindException errors) throws Exception
        {
            form.setErrors(errors);
            return new StudyJspView<ImportTypeForm>(getStudy(false), "importDataType.jsp", form, errors);
        }

        public void validateCommand(ImportTypeForm form, Errors errors)
        {
            if (form.isCreate())
            {
                if (null == form.getDataSetId() && !form.isAutoDatasetId())
                    errors.reject("defineDatasetType", "You must supply an integer Dataset Id");
                if (null != form.getDataSetId())
                {
                    DataSetDefinition dsd = StudyManager.getInstance().getDataSetDefinition(StudyManager.getInstance().getStudy(getContainer()), form.getDataSetId().intValue());
                    if (null != dsd)
                        errors.reject("defineDatasetType", "There is already a dataset with id " + form.getDataSetId());
                }
                if (null == StringUtils.trimToNull(form.getTypeName()))
                    errors.reject("defineDatasetType", "Dataset must have type name.");
                else
                {
                    String typeURI = AssayPublishManager.getInstance().getDomainURIString(StudyManager.getInstance().getStudy(getContainer()), form.getTypeName());
                    DomainDescriptor dd = OntologyManager.getDomainDescriptor(typeURI, getContainer());
                    if (null != dd)
                        errors.reject("defineDatasetType", "There is already a dataset named " + form.getTypeName() + " in this folder.");
                }
            }
            else if (null != form.getDataSetId())
            {
                //It's a bug in the code if this ever happens since editing a dataset
                DataSetDefinition dsd = StudyManager.getInstance().getDataSetDefinition(StudyManager.getInstance().getStudy(getContainer()), form.getDataSetId().intValue());
                if (null == dsd)
                    errors.reject("defineDatasetType", "This dataset appears to have been deleted id=" + form.getDataSetId());
            }
            else
                errors.reject("defineDatasetType", "DataSet ID not supplied.");
        }

        public boolean handlePost(ImportTypeForm form, BindException derrors) throws Exception
        {
            Integer datasetId = form.getDataSetId();
            if (form.isCreate())
            {
                if (form.autoDatasetId)
                    _def = AssayPublishManager.getInstance().createAssayDataset(getUser(), getStudy(), form.getTypeName(), null, null, form.isDemographicData());
                else
                    _def = AssayPublishManager.getInstance().createAssayDataset(getUser(), getStudy(), form.getTypeName(), null, datasetId, form.isDemographicData());
            }
            else
                _def = StudyManager.getInstance().getDataSetDefinition(getStudy(), datasetId.intValue());

            if (_def != null)
            {
                String domainURI = _def.getTypeURI();
                OntologyManager.ensureDomainDescriptor(domainURI, form.getTypeName(), getContainer());
                return true;
            }
            return false;
        }

        public ActionURL getSuccessURL(ImportTypeForm form)
        {
            if (_def == null)
            {
                HttpView.throwNotFound();
                return null;
            }
            return new ActionURL(EditTypeAction.class, getContainer()).
                    addParameter("datasetId", _def.getDataSetId()).
                    addParameter("create", String.valueOf(form.isCreate()));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendNavTrailDatasetAdmin(root);
            return root.addChild("Define Dataset Properties");
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    @SuppressWarnings("unchecked")
    public class EditTypeAction extends SimpleViewAction<DataSetForm>
    {
        private DataSetDefinition _def;
        public ModelAndView getView(DataSetForm form, BindException errors) throws Exception
        {
            Study study = getStudy();
            DataSetDefinition def = study.getDataSet(form.getDatasetId());
            _def = def;
            if (null == def)
            {
                HttpView.throwNotFound();
                return null;
            }
            if (null == def.getTypeURI())
            {
                def = def.createMutable();
                String domainURI = getDomainURI(study.getContainer(), def);
                OntologyManager.ensureDomainDescriptor(domainURI, def.getName(), study.getContainer());
                def.setTypeURI(domainURI);
            }
            Map props = PageFlowUtil.map(
                    "studyId", ""+study.getRowId(),
                    "datasetId", ""+form.getDatasetId(),
                    "typeURI", def.getTypeURI(),
                    "dateBased", ""+study.isDateBased(),
                    "returnURL", new ActionURL(DatasetDetailsAction.class, getContainer()).addParameter("id", form.getDatasetId()).toString(),
                    "create", ""+form.isCreate());

            HtmlView text = new HtmlView("Modify the properties and schema (form fields/properties) for this dataset.");
            HttpView view = new GWTView("org.labkey.study.dataset.Designer", props);

            // hack for 4404 : Lookup picker performance is terrible when there are many containers
            ContainerManager.getAllChildren(ContainerManager.getRoot());

            return new VBox(text, view);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendNavTrailDatasetAdmin(root);
            root.addChild(_def.getName(), new ActionURL(DatasetDetailsAction.class, getContainer()).addParameter("id", _def.getDataSetId()));
            return root.addChild("Edit Dataset");
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class DatasetDetailsAction extends SimpleViewAction<IdForm>
    {
        private DataSetDefinition _def;
        public ModelAndView getView(IdForm form, BindException errors) throws Exception
        {
            _def = StudyManager.getInstance().getDataSetDefinition(getStudy(), form.getId());
            if (_def == null)
            {
                HttpView.throwNotFound("Invalid Dataset ID");
                return null;
            }
            return  new StudyJspView<DataSetDefinition>(StudyManager.getInstance().getStudy(getContainer()),
                    "datasetDetails.jsp", _def, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return _appendNavTrailDatasetAdmin(root).addChild(_def.getLabel() + " Dataset Properties");
        }
    }

    public static class CohortForm extends ViewForm
    {
        private Integer _cohortId;

        public Integer getCohortId()
        {
            return _cohortId;
        }

        public void setCohortId(Integer cohortId)
        {
            if (StudyManager.getInstance().showCohorts(getContainer(), getUser()))
                _cohortId = cohortId;
        }

        public Cohort getCohort()
        {
            if (_cohortId != null)
                return StudyManager.getInstance().getCohortForRowId(getContainer(), getUser(), _cohortId.intValue());
            else
                return null;
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class OverviewAction extends SimpleViewAction<CohortForm>
    {
        private Study _study;
        public ModelAndView getView(CohortForm form, BindException errors) throws Exception
        {
            _study = getStudy();
            OverviewBean bean = new OverviewBean();
            bean.study = _study;
            bean.showAll = "1".equals(getViewContext().get("showAll"));
            bean.canManage = getContainer().hasPermission(getUser(), ACL.PERM_ADMIN);
            bean.showCohorts = StudyManager.getInstance().showCohorts(getContainer(), getUser());
            if (!bean.showCohorts)
                bean.cohortId = null;
            else
                bean.cohortId = form.getCohortId();

            bean.visitMapSummary = StudyManager.getInstance().getVisitManager(bean.study).getVisitSummary(form.getCohort());
            return new StudyJspView<OverviewBean>(getStudy(), "overview.jsp", bean, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Overview:" + _study.getLabel());
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class DatasetReportAction extends SimpleViewAction<CohortForm>
    {
        StringBuilder _label;

        public ModelAndView getView(CohortForm form, BindException errors) throws Exception
        {
            ViewContext context = getViewContext();
            String viewName = (String) context.get("Dataset.viewName");
            int datasetId = null == context.get(DataSetDefinition.DATASETKEY) ? 0 : Integer.parseInt((String) context.get(DataSetDefinition.DATASETKEY));

            Report reportView = null;
            if (NumberUtils.isDigits(viewName))
                reportView = ReportManager.get().getReport(context.getContainer(), NumberUtils.toInt(viewName));

            // is not a report (either the default grid view or a custom view)...
            if (null == reportView)
            {
                HttpView.throwRedirect(createRedirectURLfrom(DatasetAction.class, context));
                return null;
            }

            DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(getStudy(), datasetId);

            final String type = reportView.getDescriptor().getReportType();
            if (ChartReportView.TYPE.equals(type))
            {
                HttpView.throwRedirect(createRedirectURLfrom(ReportsController.DatasetReportAction.class, context));
                return null;
            }
            else if (StudyRReport.TYPE.equals(type))
            {
                HttpView.throwRedirect(createRedirectURLfrom(ReportsController.RunRReportAction.class, context));
                return null;
            }

            _label = new StringBuilder();
            _label.append("Dataset: ");
            if (def != null)
            {
                if (def.getLabel() != null)
                    _label.append(def.getLabel());
                else
                    _label.append("CRF/Assay ").append(def.getDataSetId());
            }

            HttpView view = reportView.renderReport(getViewContext());
            DataHeader header = new DataHeader(getViewContext().getActionURL(), null, def, false);

            return new VBox(header,
                    new ReportHeader(reportView), view);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return _appendNavTrail(root).addChild(_label.toString());
        }
    }

    private ActionURL createRedirectURLfrom(Class<? extends Controller> action, ViewContext context)
    {
        ActionURL newUrl = new ActionURL(action, context.getContainer());
        return newUrl.addParameters(context.getActionURL().getParameters());
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class DeleteDatasetReportAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            String viewName = (String) getViewContext().get("Dataset.viewName");
            int datasetId = NumberUtils.toInt((String)getViewContext().get(DataSetDefinition.DATASETKEY));

            if (NumberUtils.isDigits(viewName))
            {
                Report report = ReportService.get().getReport(NumberUtils.toInt(viewName));
                if (report != null)
                    ReportService.get().deleteReport(getViewContext(), report);
            }
            HttpView.throwRedirect(new ActionURL(DatasetAction.class, getContainer()).
                    addParameter(DataSetDefinition.DATASETKEY, datasetId));
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class DatasetAction extends SimpleViewAction<CohortForm>
    {
        private Integer _cohortId;
        private int _datasetId;
        private int _visitId;

        public ModelAndView getView(CohortForm form, BindException errors) throws Exception
        {
            // the full resultset is a join of all datasets for each participant
            // each dataset is determined by a visitid/datasetid

            Study study = getStudy();
            _cohortId = form.getCohortId();
            Cohort cohort = form.getCohort();
            ViewContext context = getViewContext();
            final ActionURL url = context.getActionURL();

            String export = StringUtils.trimToNull((String) context.get("export"));
            _datasetId = NumberUtils.toInt((String)context.get(DataSetDefinition.DATASETKEY), 0);
            String viewName = (String)context.get("Dataset.viewName");
            final DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, _datasetId);
            if (null == def)
                return new TypeNotFoundAction().getView(form, errors);
            String typeURI = def.getTypeURI();
            if (null == typeURI)
                return new TypeNotFoundAction().getView(form, errors);

            _visitId = NumberUtils.toInt((String)context.get(Visit.VISITKEY), 0);
            Visit visit = null;
            if (_visitId != 0)
            {
                visit = StudyManager.getInstance().getVisitForRowId(getStudy(), _visitId);
                if (null == visit)
                    HttpView.throwNotFound();
            }

            final StudyQuerySchema querySchema = new StudyQuerySchema(study, getUser(), true);
            QuerySettings qs = new QuerySettings(url, DataSetQueryView.DATAREGION);
            qs.setSchemaName(querySchema.getSchemaName());
            qs.setQueryName(def.getLabel());
            DataSetQueryView queryView = new DataSetQueryView(_datasetId, querySchema, qs, visit, cohort);
            queryView.setForExport(export != null);
            setColumnURL(url, queryView, querySchema, def);

            // clear the property map cache and the sort map cache
            getParticipantPropsMap(context).clear();
            getDatasetSortColumnMap(context).clear();

            final TableInfo table = queryView.getTable();
            if (table != null)
            {
                addParticipantListToCache(context, _datasetId, viewName, generateParticipantList(queryView), cohort);
                getExpandedState(context, _datasetId).clear();

                queryView.setShowSourceLinks(hasSourceLsids(table));
            }

            List<ActionButton> buttonBar = new ArrayList<ActionButton>();

            MenuButton exportMenuButton = new MenuButton("Export");

            exportMenuButton.addMenuItem("Export all to Excel (.xls)", url.getEncodedLocalURIString() + "&amp;export=xls");
            exportMenuButton.addMenuItem("Export all to text file (.tsv)", url.getEncodedLocalURIString() + "&amp;export=tsv");
            exportMenuButton.addMenuItem("Excel Web Query (.iqy)", queryView.urlFor(QueryAction.excelWebQueryDefinition).getLocalURIString());
            buttonBar.add(exportMenuButton);

            ActionURL insertURL = new ActionURL(DatasetController.InsertAction.class, getContainer());
            insertURL.addParameter("datasetId", _datasetId);
            ActionButton insertButton = new ActionButton(insertURL.getLocalURIString(), "Insert New", DataRegion.MODE_GRID, ActionButton.Action.LINK);
            buttonBar.add(insertButton);

            ActionButton uploadButton = new ActionButton("showImportDataset.view?datasetId=" + _datasetId, "Import Data", DataRegion.MODE_GRID, ActionButton.Action.LINK);
            uploadButton.setDisplayPermission(ACL.PERM_INSERT);
            buttonBar.add(uploadButton);

            ActionButton deleteRows = new ActionButton("button", "Delete Selected");
            ActionURL deleteRowsURL = new ActionURL(DeleteDatasetRowsAction.class, getContainer());
            deleteRowsURL.addParameter("datasetId", _datasetId);

            deleteRows.setScript("return confirm(\"Delete selected rows of this dataset?\") && verifySelected(this.form, \"" + deleteRowsURL.getLocalURIString() + "\", \"post\", \"rows\")");
            deleteRows.setActionType(ActionButton.Action.GET);
            deleteRows.setDisplayPermission(ACL.PERM_DELETE);
            buttonBar.add(deleteRows);

            if (null == visit)
            {
                ActionButton purgeButton = new ActionButton("purgeDataset.view?datasetId=" + _datasetId, "Delete All Rows", DataRegion.MODE_GRID, ActionButton.Action.LINK);
                purgeButton.setDisplayPermission(ACL.PERM_ADMIN);
                purgeButton.setScript("if(confirm(\"Delete all rows of this dataset?\")){ form.action=\"purgeDataset.view?datasetId=" + _datasetId + "\";return true;}else return false;");
                purgeButton.setActionType(ActionButton.Action.GET);
                buttonBar.add(purgeButton);
            }

            ActionButton viewSamples = new ActionButton("button", "View Specimens");
            String viewSamplesURL = ActionURL.toPathString("Study-Samples", "selectedSamples", getContainer());
            viewSamples.setScript("return verifySelected(this.form, \"" + viewSamplesURL + "\", \"post\", \"rows\")");
            viewSamples.setActionType(ActionButton.Action.GET);
            viewSamples.setDisplayPermission(ACL.PERM_READ);
            buttonBar.add(viewSamples);

            buttonBar.add(getCreateViewButton(def, viewName, cohort));

            queryView.setButtons(buttonBar);

            if (null != export)
            {
                if ("tsv".equals(export))
                    queryView.exportToTsv(context.getResponse());
                else if ("xls".equals(export))
                    queryView.exportToExcel(context.getResponse());
                return null;
            }
            DataHeader header = new DataHeader(url, queryView.getCustomizeURL(), def, true);
            header.setShowCohortSelector(true);
            header.setSelectedCohort(cohort);
            HttpView view = new VBox(header, queryView);
            if (!def.canRead(getUser()))
            {
                view = new HtmlView("User does not have read permission on this dataset.");
            }
            return view;
        }

        private MenuButton getCreateViewButton(DataSetDefinition def, String viewName, Cohort cohort)
        {
            MenuButton createViewButton = new MenuButton("Create Views");

            if (!getViewContext().getUser().isGuest() && getViewContext().hasPermission(ACL.PERM_READ))
            {
                final List<String> plist = getParticipantListFromCache(getViewContext(), def.getDataSetId(), viewName, cohort);
                Map<String, String> params = plist.size() > 0 ?
                        Collections.singletonMap("participantId", plist.get(0)) :
                        Collections.<String,String>emptyMap();

                ActionURL url = getViewContext().getActionURL().clone();
                url.setAction(ReportsController.ParticipantCrosstabAction.class);
                url.addParameters(params);
                createViewButton.addMenuItem("Crosstab View", url.getLocalURIString());
            }

            // create chart link
            ChartDesignerBean chartBean = new ChartDesignerBean();

            chartBean.setReportType(StudyChartQueryReport.TYPE);
            chartBean.setSchemaName("study");
            chartBean.setQueryName(def.getLabel());
            chartBean.setViewName(viewName);
            chartBean.setDataRegionName("Dataset");

            ActionURL url = ChartUtil.getChartDesignerURL(getViewContext(), chartBean);
            url.addParameter(DataSetDefinition.DATASETKEY, def.getDataSetId());
            url.setPageFlow("Study-Reports");
            //for (Pair<String, String> param : chartBean.getParameters())
            //    url.addParameter(param.getKey(), param.getValue());
            createViewButton.addMenuItem("Chart View", url.toString());

            // create R report link
            RReportBean bean = new RReportBean();
            bean.setReportType(StudyRReport.TYPE);
            bean.setSchemaName("study");
            bean.setQueryName(def.getLabel());
            bean.setViewName(viewName);
            bean.setDataRegionName("Dataset");

            bean.setRedirectUrl(getViewContext().getActionURL().toString());

            // todo : refactor CreateChartButton to extend MenuButton so it can be used here instead of
            // having to duplicate the permissions logic
            if (RReport.isValidConfiguration())
            {
                int perms = RReport.getEditPermissions();
                if (getViewContext().hasPermission(perms))
                    createViewButton.addMenuItem("R View", ChartUtil.getRReportDesignerURL(getViewContext(), bean).toString());
                else
                    createViewButton.addMenuItem("R View", "javascript:void(0)", "javascript:alert(\"You do not have the required authorization to create R Views.\")");
            }
            else
                createViewButton.addMenuItem("R View", "javascript:void(0)", "javascript:alert(\"The R Program has not been configured properly, please request that an administrator configure R in the Admin Console.\")");

            if (getViewContext().getUser().isAdministrator())
            {
                ActionURL buttonURL = getViewContext().getActionURL().clone();
                buttonURL.setAction(ReportsController.ExternalReportAction.class);
                createViewButton.addMenuItem("Advanced View", buttonURL.getLocalURIString());
            }
            return createViewButton;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return _appendNavTrail(root, _datasetId, _visitId,  _cohortId != null ? _cohortId.intValue() : -1);
        }
    }

    @RequiresPermission(ACL.PERM_NONE)
    public class ExpandStateNotifyAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            final ActionURL url = getViewContext().getActionURL();
            final String collapse = url.getParameter("collapse");
            final String datasetId = url.getParameter("datasetId");
            final String id = url.getParameter("id");

            if (datasetId != null && id != null)
            {
                Map<Integer, String> expandedMap = getExpandedState(getViewContext(), Integer.parseInt(id));
                // collapse param is only set on a collapse action
                if (collapse != null)
                    expandedMap.remove(Integer.parseInt(datasetId));
                else
                    expandedMap.put(Integer.parseInt(datasetId), "expand");
            }
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class DatasetPlotAction extends SimpleViewAction<ParticipantForm>
    {
        ParticipantForm _bean;

        @SuppressWarnings("deprecation")
        public ModelAndView getView(ParticipantForm form, BindException errors) throws Exception
        {
            _bean = form;
            ViewContext context = getViewContext();
            String viewName = (String) context.get("Dataset.viewName");
            int datasetId = null == context.get(DataSetDefinition.DATASETKEY) ? 0 : Integer.parseInt((String) context.get(DataSetDefinition.DATASETKEY));

            final String plotPrefix = ReportManager.getDatasetReportKeyPrefix(ReportManager.ALL_DATASETS);
            if (viewName.startsWith(plotPrefix))
            {
                viewName = viewName.substring(plotPrefix.length());
            }

            String previousParticipantURL = null;
            String nextParticiapantURL = null;

            Report reportView = null;
            if (NumberUtils.isDigits(viewName))
                reportView = ReportManager.get().getReport(context.getContainer(), NumberUtils.toInt(viewName));

            // TODO: replace references by view name to report id
/*
            if (reportView == null && viewName != null)
            {
                reportView = ReportManager.get().getReport(getUser(), context.getContainer(), reportDatasetId, viewName);
            }
*/
            if (null == reportView)
            {
                HttpView.redirect(new ActionURL(DatasetAction.class, getContainer()));
                return null;
            }
            //if (!reportView.getDescriptor().canRead(getUser()))
            //    HttpView.throwUnauthorized();

            if ("delete".equals(context.get("action")))
            {
                if (!ReportManager.get().canDeleteReport(getUser(), getContainer(), reportView.getDescriptor().getReportId()))
                    HttpView.throwUnauthorized();

                ReportManager.get().deleteReport(getViewContext(), reportView);
                ActionURL url = getViewContext().cloneActionURL();
                url.setAction(DatasetAction.class);

                HttpView.redirect(url);
                return null;
            }

            DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(getStudy(), datasetId);
            VBox box = new VBox();

            // display the next and previous buttons only if we have a cached participant index and the
            // report has a filter param set
            if (!StringUtils.isEmpty(reportView.getDescriptor().getProperty("filterParam")))
            {
                String prevView = (String) context.get("prevView");
                Cohort cohort = null;
                if (form.getCohortId() != null)
                    cohort = StudyManager.getInstance().getCohortForRowId(getContainer(), getUser(), form.getCohortId().intValue());
                List<String> participants = getParticipantListFromCache(context, datasetId, prevView, cohort);
                if (!participants.isEmpty())
                {
                    if (form.getParticipantId() == null)
                    {
                        form.setParticipantId(participants.get(0));
                    }
                    int idx = participants.indexOf(form.getParticipantId());
                    if (idx != -1)
                    {
                        if (idx > 0)
                        {
                            final String ptid = participants.get(idx-1);
                            ActionURL prevUrl = getViewContext().cloneActionURL();
                            prevUrl.replaceParameter("participantId", ptid);
                            previousParticipantURL = prevUrl.getEncodedLocalURIString();
                        }

                        if (idx < participants.size()-1)
                        {
                            final String ptid = participants.get(idx+1);
                            ActionURL nextUrl = getViewContext().cloneActionURL();
                            nextUrl.replaceParameter("participantId", ptid);
                            nextParticiapantURL = nextUrl.getEncodedLocalURIString();
                        }
                    }
                }
                box.addView(new ParticipantNavView(previousParticipantURL, nextParticiapantURL, form.getParticipantId()));
            }
            box.addView(new DataHeader(getViewContext().getActionURL(), null, def, false));
            box.addView(new ReportHeader(reportView));
            box.addView(reportView.renderReport(getViewContext()));

            return box;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return _appendNavTrail(root, _bean.getDatasetId(), 0).
                    addChild("Participant - " + _bean.getParticipantId());
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class ParticipantAction extends SimpleViewAction<ParticipantForm>
    {
        ParticipantForm _bean;

        public ModelAndView getView(ParticipantForm form, BindException errors) throws Exception
        {
            _bean = form;
            String previousParticipantURL = null;
            String nextParticiapantURL = null;

            String viewName = (String) getViewContext().get("Dataset.viewName");

            // display the next and previous buttons only if we have a cached participant index
            Cohort cohort = null;
            if (form.getCohortId() != null)
                cohort = StudyManager.getInstance().getCohortForRowId(getContainer(), getUser(), form.getCohortId().intValue());
            List<String> participants = getParticipantListFromCache(getViewContext(), form.getDatasetId(), viewName, cohort);
            if (participants != null)
            {
                int idx = participants.indexOf(form.getParticipantId());
                if (idx != -1)
                {
                    if (idx > 0)
                    {
                        final String ptid = participants.get(idx-1);
                        ActionURL prevUrl = getViewContext().cloneActionURL();
                        prevUrl.replaceParameter("participantId", ptid);
                        previousParticipantURL = prevUrl.getEncodedLocalURIString();
                    }

                    if (idx < participants.size()-1)
                    {
                        final String ptid = participants.get(idx+1);
                        ActionURL nextUrl = getViewContext().cloneActionURL();
                        nextUrl.replaceParameter("participantId", ptid);
                        nextParticiapantURL = nextUrl.getEncodedLocalURIString();
                    }
                }
            }

            CustomParticipantView customParticipantView = StudyManager.getInstance().getCustomParticipantView(StudyManager.getInstance().getStudy(getContainer()));
            HttpView participantView;
            if (customParticipantView != null && customParticipantView.isActive())
                participantView = new HtmlView(customParticipantView.getBody());
            else
                participantView = StudyManager.getInstance().getParticipantView(getContainer(), form, errors);

            ParticipantNavView navView = new ParticipantNavView(previousParticipantURL, nextParticiapantURL, form.getParticipantId());

            return new VBox(navView, participantView);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return _appendNavTrail(root, _bean.getDatasetId(), 0, _bean.getCohortId() != null ? _bean.getCohortId().intValue() : -1).
                    addChild("Participant - " + _bean.getParticipantId());
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class UploadVisitMapAction extends FormViewAction<BaseController.TSVForm>
    {
        public ModelAndView getView(BaseController.TSVForm tsvForm, boolean reshow, BindException errors) throws Exception
        {
            ModelAndView view = new StudyJspView<Object>(getStudy(), "uploadVisitMap.jsp", null, errors);
            view.addObject("errors", errors);
            return view;
        }

        public void validateCommand(BaseController.TSVForm target, Errors errors)
        {
        }

        public boolean handlePost(BaseController.TSVForm form, BindException errors) throws Exception
        {
            VisitMapImporter importer = new VisitMapImporter();
            List<String> errorMsg = new LinkedList<String>();
            if (!importer.process(getViewContext().getUser(), getStudy(), form.getContent(), errorMsg))
            {
                for (String error : errorMsg)
                    errors.reject("uploadVisitMap", error);
                return false;
            }
            return true;
        }

        public ActionURL getSuccessURL(BaseController.TSVForm tsvForm)
        {
            return new ActionURL(BeginAction.class, getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Create New Study: Visit Map Upload");
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class ExportVisitMapAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            Study study = getStudy();

            getViewContext().getResponse().setContentType("text/tsv");
            writeVisitMap(study, getViewContext().getResponse().getWriter());
            return null;
        }

        private void writeVisitMap(Study study, PrintWriter out)
        {
            Visit[] visits = study.getVisits();

            for (Visit v : visits)
            {
                List<VisitDataSet> vds = v.getVisitDataSets();

                if (v.getSequenceNumMin() == v.getSequenceNumMax())
                    out.printf("%f\t%c\t%s\t\t\t\t\t", v.getSequenceNumMin(), v.getTypeCode(), v.getLabel());
                else
                    out.printf("%f-%f\t%c\t%s\t\t\t\t\t", v.getSequenceNumMin(), v.getSequenceNumMax(), v.getTypeCode(), v.getLabel());
                String s = "";
                for (VisitDataSet vd : vds)
                {
                    if (vd.isRequired())
                    {
                        out.print(s);
                        out.print(vd.getDataSetId());
                        s = " ";
                    }
                }
                out.print("\t");
                for (VisitDataSet vd : vds)
                {
                    if (vd.isRequired())
                    {
                        out.print(s);
                        out.print(vd.getDataSetId());
                        s = " ";
                    }
                }
                out.println("\t\t");
            }
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class ShowCreateStudyAction extends SimpleViewAction<StudyPropertiesForm>
    {
        public ModelAndView getView(StudyPropertiesForm form, BindException errors) throws Exception
        {
            if (null != getStudy(true))
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
            return new StudyJspView<StudyPropertiesForm>(null, "createStudy.jsp", form, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Create Study");
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class CreateStudyAction extends FormHandlerAction<StudyPropertiesForm>
    {
        public void validateCommand(StudyPropertiesForm target, Errors errors)
        {
            if (target.isDateBased() && null == target.getStartDate())
                errors.reject(ERROR_MSG, "Start date must be supplied for a date-based study.");

            target.setLabel(StringUtils.trimToNull(target.getLabel()));
            if (null == target.getLabel())
                errors.reject(ERROR_MSG, "Please supply a label");
        }

        public boolean handlePost(StudyPropertiesForm form, BindException errors) throws Exception
        {
            if (null == getStudy(true))
            {
                Study study = new Study(getContainer(), form.getLabel());
                study.setDateBased(form.isDateBased());
                study.setStartDate(form.getStartDate());
                StudyManager.getInstance().createStudy(getUser(), study);
                SampleManager.RepositorySettings reposSettings = SampleManager.getInstance().getRepositorySettings(getContainer());
                reposSettings.setSimple(form.isSimpleRepository());
                reposSettings.setEnableRequests(!form.isSimpleRepository());
                SampleManager.getInstance().saveRepositorySettings(getContainer(), reposSettings);
            }
            return true;
        }

        public ActionURL getSuccessURL(StudyPropertiesForm studyPropertiesForm)
        {
            return new ActionURL(ManageStudyAction.class, getContainer());
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class ManageStudyAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new StudyJspView<Object>(getStudy(true), "manageStudy.jsp", null, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            try {
                Study study = getStudy();
                if (study != null)
                    root.addChild(study.getLabel(), new ActionURL(BeginAction.class, getContainer()));
            }
            catch (ServletException e)
            {
            }
            return root.addChild("Manage Study");
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class DeleteStudyAction extends FormViewAction<DeleteStudyForm>
    {
        public void validateCommand(DeleteStudyForm form, Errors errors)
        {
            if (!form.isConfirm())
                errors.reject("deleteStudy", "Need to confirm Study deletion");
        }

        public ModelAndView getView(DeleteStudyForm form, boolean reshow, BindException errors) throws Exception
        {
            return new StudyJspView<Object>(getStudy(), "confirmDeleteStudy.jsp", null, errors);
        }

        public boolean handlePost(DeleteStudyForm form, BindException errors) throws Exception
        {
            StudyManager.getInstance().deleteAllStudyData(getContainer(), getUser(), true, false);
            return true;
        }

        public ActionURL getSuccessURL(DeleteStudyForm deleteStudyForm)
        {
            return getContainer().getFolderType().getStartURL(getContainer(), getUser());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Confirm Delete Study");
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


    @RequiresPermission(ACL.PERM_ADMIN)
    public class UpdateStudyPropertiesAction extends FormHandlerAction<StudyPropertiesForm>
    {
        public void validateCommand(StudyPropertiesForm target, Errors errors)
        {
            if (target.isDateBased() && null == target.getStartDate())
                errors.reject(ERROR_MSG, "Start date must be supplied for a date-based study.");

            target.setLabel(StringUtils.trimToNull(target.getLabel()));
            if (null == target.getLabel())
                errors.reject(ERROR_MSG, "Please supply a label");
        }

        public boolean handlePost(StudyPropertiesForm form, BindException errors) throws Exception
        {
            if (getStudy(true) != null)
            {
                Study updated = getStudy().createMutable();
                updated.setLabel(form.getLabel());
                StudyManager.getInstance().updateStudy(getUser(), updated);
            }
            return true;
        }

        public ActionURL getSuccessURL(StudyPropertiesForm studyPropertiesForm)
        {
            try {
                if (getStudy(true) == null)
                    return new ActionURL(CreateStudyAction.class, getContainer());
            }
            catch (ServletException e){}

            return new ActionURL(ManageStudyAction.class, getContainer());
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class ManageStudyProperties extends SimpleViewAction<StudyPropertiesForm>
    {
        public ModelAndView getView(StudyPropertiesForm form, BindException errors) throws Exception
        {
            Study study = getStudy(true);
            if (null == study)
            {
                ShowCreateStudyAction action = (ShowCreateStudyAction)initAction(this, new ShowCreateStudyAction());
                return action.getView(form, errors);
            }
            return new StudyJspView<StudyPropertiesForm>(getStudy(), "manageStudyProperties.jsp", form, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendManageStudy(root);
            return root.addChild("Study Properties");
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class ManageVisitsAction extends FormViewAction<StudyPropertiesForm>
    {
        public void validateCommand(StudyPropertiesForm target, Errors errors)
        {
            if (target.isDateBased() && null == target.getStartDate())
                errors.reject(ERROR_MSG, "Start date must be supplied for a date-based study.");

            target.setLabel(StringUtils.trimToNull(target.getLabel()));
            if (null == target.getLabel())
                errors.reject(ERROR_MSG, "Please supply a label");
        }

        public ModelAndView getView(StudyPropertiesForm form, boolean reshow, BindException errors) throws Exception
        {
            Study study = getStudy(true);
            if (null == study)
            {
                ShowCreateStudyAction action = (ShowCreateStudyAction)initAction(this, new ShowCreateStudyAction());
                return action.getView(form, errors);
            }
            return new StudyJspView<StudyPropertiesForm>(getStudy(), _jspName(), form, errors);
        }

        public boolean handlePost(StudyPropertiesForm form, BindException errors) throws Exception
        {
            Study study = getStudy().createMutable();
            study.setStartDate(form.getStartDate());
            StudyManager.getInstance().updateStudy(getUser(), study);

            return true;
        }

        public ActionURL getSuccessURL(StudyPropertiesForm studyPropertiesForm)
        {
            return new ActionURL(ManageStudyAction.class, getContainer());
        }

        public ModelAndView getView(StudyPropertiesForm form, BindException errors) throws Exception
        {
            Study study = getStudy(true);
            if (null == study)
            {
                ShowCreateStudyAction action = (ShowCreateStudyAction)initAction(this, new ShowCreateStudyAction());
                return action.getView(form, errors);
            }
            return new StudyJspView<StudyPropertiesForm>(getStudy(), _jspName(), form, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendManageStudy(root);
            return root.addChild("Manage " + getVisitLabelPlural());
        }

        private String _jspName() throws ServletException
        {
            return getStudy().isDateBased() ? "manageTimepoints.jsp" : "manageVisits.jsp";
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class ManageTypesAction extends FormViewAction<ManageTypesBean>
    {
        public void validateCommand(ManageTypesBean target, Errors errors)
        {
        }

        public ModelAndView getView(ManageTypesBean manageTypesBean, boolean reshow, BindException errors) throws Exception
        {
            ModelAndView view = new StudyJspView<ManageTypesAction>(getStudy(), "manageTypes.jsp", this, errors);
            view.addObject("errors", errors);
            return view;
        }

        public boolean handlePost(ManageTypesBean form, BindException errors) throws Exception
        {
            String dateFormat = form.getDateFormat();
            String numberFormat = form.getNumberFormat();

            try {
                if (!StringUtils.isEmpty(dateFormat))
                {
                    FastDateFormat.getInstance(dateFormat);
                    StudyManager.getInstance().setDefaultDateFormatString(getContainer(), dateFormat);
                }
                else
                    StudyManager.getInstance().setDefaultDateFormatString(getContainer(), null);

                if (!StringUtils.isEmpty(numberFormat))
                {
                    new DecimalFormat(numberFormat);
                    StudyManager.getInstance().setDefaultNumberFormatString(getContainer(), numberFormat);
                }
                else
                    StudyManager.getInstance().setDefaultNumberFormatString(getContainer(), null);
                return true;
            }
            catch (IllegalArgumentException e)
            {
                errors.reject("manageTypes", e.getMessage());
                return false;
            }
        }

        public ActionURL getSuccessURL(ManageTypesBean manageTypesBean)
        {
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendManageStudy(root);
            return root.addChild("Manage Datasets");
        }
    }

    public static class ManageTypesBean
    {
        private String _dateFormat;
        private String _numberFormat;

        public String getDateFormat()
        {
            return _dateFormat;
        }

        public void setDateFormat(String dateFormat)
        {
            _dateFormat = dateFormat;
        }

        public String getNumberFormat()
        {
            return _numberFormat;
        }

        public void setNumberFormat(String numberFormat)
        {
            _numberFormat = numberFormat;
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class ManageSitesAction extends FormViewAction<BaseController.BulkEditForm>
    {
        public void validateCommand(BaseController.BulkEditForm target, Errors errors)
        {
        }

        public ModelAndView getView(BaseController.BulkEditForm bulkEditForm, boolean reshow, BindException errors) throws Exception
        {
            ModelAndView view = new StudyJspView<Study>(getStudy(),  "manageSites.jsp", getStudy(), errors);
            view.addObject("errors", errors);
            return view;
        }

        public boolean handlePost(BaseController.BulkEditForm form, BindException errors) throws Exception
        {
            int[] ids = form.getIds();
            if (ids != null && ids.length > 0)
            {
                String[] labels = form.getLabels();
                Map<Integer, String> labelLookup = new HashMap<Integer, String>();
                for (int i = 0; i < ids.length; i++)
                    labelLookup.put(ids[i], labels[i]);

                boolean emptyLabel = false;
                for (Site site : getStudy().getSites())
                {
                    String label = labelLookup.get(site.getRowId());
                    if (label == null)
                        emptyLabel = true;
                    else if (!label.equals(site.getLabel()))
                    {
                        site = site.createMutable();
                        site.setLabel(label);
                        StudyManager.getInstance().updateSite(getUser(), site);
                    }
                }
                if (emptyLabel)
                {
                    errors.reject("manageSites", "Some site labels could not be updated: empty labels are not allowed.");
                }

            }
            if (form.getNewId() != null || form.getNewLabel() != null)
            {
                if (form.getNewId() == null)
                    errors.reject("manageSites", "Unable to create site: an ID is required for all sites.");
                else if (form.getNewLabel() == null)
                    errors.reject("manageSites", "Unable to create site: a label is required for all sites.");
                else
                {
                    try
                    {
                        Site site = new Site();
                        site.setLabel(form.getNewLabel());
                        site.setLdmsLabCode(Integer.parseInt(form.getNewId()));
                        site.setContainer(getContainer());
                        StudyManager.getInstance().createSite(getUser(), site);
                    }
                    catch (NumberFormatException e)
                    {
                        errors.reject("manageSites", "Unable to create site: ID must be an integer.");
                    }
                }
            }
            return errors.getErrorCount() == 0;
        }

        public ActionURL getSuccessURL(BaseController.BulkEditForm bulkEditForm)
        {
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendManageStudy(root);
            return root.addChild("Manage Sites");
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class VisitSummaryAction extends FormViewAction<VisitForm>
    {
        private Visit _v;

        public void validateCommand(VisitForm target, Errors errors)
        {
            target.validate(errors);
            if(errors.getErrorCount() > 0)
                return;
            
            //check for overlapping visits
            try
            {
                VisitManager visitMgr = StudyManager.getInstance().getVisitManager(getStudy());
                if(null != visitMgr)
                {
                    if(visitMgr.isVisitOverlapping(target.getBean()))
                        errors.reject(null, "Visit range overlaps an existing visit in this study. Please enter a different range.");
                }
            }
            catch(ServletException e)
            {
                errors.reject(null, e.getMessage());
            }
            catch(SQLException e)
            {
                errors.reject(null, e.getMessage());
            }
        }

        public ModelAndView getView(VisitForm form, boolean reshow, BindException errors) throws Exception
        {
            int id = NumberUtils.toInt((String)getViewContext().get("id"));
            _v = StudyManager.getInstance().getVisitForRowId(getStudy(), id);
            if (_v == null)
            {
                return HttpView.redirect(new ActionURL(BeginAction.class, getContainer()));
            }
            VisitSummaryBean visitSummary = new VisitSummaryBean();
            visitSummary.setVisit(_v);
            ModelAndView view = new StudyJspView<VisitSummaryBean>(getStudy(), getVisitJsp("edit"), visitSummary, errors);
            view.addObject("errors", errors);

            return view;
        }

        public boolean handlePost(VisitForm form, BindException errors) throws Exception
        {
            Visit postedVisit = form.getBean();
            if (!getContainer().getId().equals(postedVisit.getContainer().getId()))
                    HttpView.throwUnauthorized();
            // UNDONE: how do I get struts to handle this checkbox?
            postedVisit.setShowByDefault(null != StringUtils.trimToNull((String)getViewContext().get("showByDefault")));

            // UNDONE: reshow is broken for this form, but we have to validate
            TreeMap<Double,Visit> visits = StudyManager.getInstance().getVisitManager(getStudy()).getVisitSequenceMap();
            boolean validRange = true;
            // make sure there is no overlapping visit
            for (Visit v : visits.values())
            {
                if (v.getRowId() == postedVisit.getRowId())
                    continue;
                double maxL = Math.max(v.getSequenceNumMin(),postedVisit.getSequenceNumMin());
                double minR = Math.min(v.getSequenceNumMax(),postedVisit.getSequenceNumMax());
                if (maxL<=minR)
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

            HashMap<Integer,VisitDataSetType> visitTypeMap = new HashMap<Integer,VisitDataSetType>();
            for (VisitDataSet vds :  postedVisit.getVisitDataSets())
                visitTypeMap.put(vds.getDataSetId(), vds.isRequired() ? VisitDataSetType.REQUIRED : VisitDataSetType.OPTIONAL);

            for (int i = 0; i < form.getDataSetIds().length; i++)
            {
                int dataSetId = form.getDataSetIds()[i];
                VisitDataSetType type = VisitDataSetType.valueOf(form.getDataSetStatus()[i]);
                VisitDataSetType oldType = visitTypeMap.get(dataSetId);
                if (oldType == null)
                    oldType = VisitDataSetType.NOT_ASSOCIATED;
                if (type != oldType)
                {
                    StudyManager.getInstance().updateVisitDataSetMapping(getUser(), getContainer(),
                            postedVisit.getRowId(), dataSetId, type);
                }
            }
            return true;
        }

        public ActionURL getSuccessURL(VisitForm form)
        {
            return new ActionURL(ManageVisitsAction.class, getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendManageStudy(root);
            root.addChild("Manage " + getVisitLabelPlural(), new ActionURL(ManageVisitsAction.class, getContainer()));
            return root.addChild(_v.getLabel());
        }
    }

    public static class VisitSummaryBean
    {
        private Visit visit;

        public Visit getVisit()
        {
            return visit;
        }

        public void setVisit(Visit visit)
        {
            this.visit = visit;
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class DeleteVisitAction extends FormHandlerAction<IdForm>
    {
        public void validateCommand(IdForm target, Errors errors)
        {
        }

        public boolean handlePost(IdForm form, BindException errors) throws Exception
        {
            int visitId = form.getId();
            Study study = getStudy();
            Visit visit = StudyManager.getInstance().getVisitForRowId(study, visitId);
            if (visit != null)
            {
                StudyManager.getInstance().deleteVisit(getStudy(), visit);
                return true;
            }
            HttpView.throwNotFound();
            return false;
        }

        public ActionURL getSuccessURL(IdForm idForm)
        {
            return new ActionURL(ManageVisitsAction.class, getContainer());
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class ConfirmDeleteVisitAction extends SimpleViewAction<IdForm>
    {
        private Visit _visit;
        public ModelAndView getView(IdForm form, BindException errors) throws Exception
        {
            int visitId = form.getId();
            Study study = getStudy();
            _visit = StudyManager.getInstance().getVisitForRowId(study, visitId);
            if (null == _visit)
                return HttpView.throwNotFoundMV();

            ModelAndView view = new StudyJspView<Visit>(study, "confirmDeleteVisit.jsp", _visit, errors);
            return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Delete visit -- " + _visit.getDisplayString());
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class CreateVisitAction extends FormViewAction<VisitForm>
    {
        public void validateCommand(VisitForm target, Errors errors)
        {
            target.validate(errors);
            if(errors.getErrorCount() > 0)
                return;

            //check for overlapping visits
            try
            {
                VisitManager visitMgr = StudyManager.getInstance().getVisitManager(getStudy());
                if(null != visitMgr)
                {
                    if(visitMgr.isVisitOverlapping(target.getBean()))
                        errors.reject(null, "Visit range overlaps an existing visit in this study. Please enter a different range.");
                }
            }
            catch(ServletException e)
            {
                errors.reject(null, e.getMessage());
            }
            catch(SQLException e)
            {
                errors.reject(null, e.getMessage());
            }
        }

        public ModelAndView getView(VisitForm form, boolean reshow, BindException errors) throws Exception
        {
            ModelAndView view = new StudyJspView<Object>(getStudy(), getVisitJsp("create"), null, errors);
            view.addObject("form", form);

            return view;
        }

        public boolean handlePost(VisitForm form, BindException errors) throws Exception
        {
            Visit visit = form.getBean();
            if (visit != null)
                StudyManager.getInstance().createVisit(getStudy(), getUser(), visit);
            return true;
        }

        public ActionURL getSuccessURL(VisitForm visitForm)
        {
            return new ActionURL(ManageVisitsAction.class, getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendManageStudy(root);
            root.addChild("Manage " + getVisitLabelPlural(), new ActionURL(ManageVisitsAction.class, getContainer()));
            return root.addChild("Create New " + getVisitLabel());
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class UpdateDatasetFormAction extends FormViewAction<DataSetForm>
    {
        DataSetDefinition _def;

        public void validateCommand(DataSetForm target, Errors errors)
        {
        }

        public ModelAndView getView(DataSetForm form, boolean reshow, BindException errors) throws Exception
        {
            _def = StudyManager.getInstance().getDataSetDefinition(getStudy(), form.getDatasetId());
            if (_def == null)
            {
                BeginAction action = (BeginAction)initAction(this, new BeginAction());
                return action.getView(form, errors);
            }
            ModelAndView view = new JspView<DataSetDefinition>("/org/labkey/study/view/updateDataset.jsp", _def);
            view.addObject("errors", errors);
            return view;
        }

        public boolean handlePost(DataSetForm form, BindException errors) throws Exception
        {
            DataSetDefinition original = StudyManager.getInstance().getDataSetDefinition(getStudy(), form.getDatasetId());
            DataSetDefinition modified = original.createMutable();
/*
            BeanUtils.copyProperties(modified, form);
            if (modified.isDemographicData() && !original.isDemographicData() && !StudyManager.getInstance().isDataUniquePerParticipant(original))
            {
                errors.reject("updateDataset", "This dataset currently contains more than one row of data per participant. Demographic data includes one row of data per participant.");
                return false;
            }
            try
            {
                StudyManager.getInstance().updateDataSetDefinition(getUser(), modified);
            }
            catch (SQLException x)
            {
                if (!(x.getSQLState().equals("23000") || x.getSQLState().equals("23505")))    // constraint violation
                    throw new RuntimeSQLException(x);
                errors.reject("updateDataset", "Dataset name already exists: " + form.getName());
                // UNDONE: reshow entered values
                return false;
            }
*/
            if (null != form.getVisitRowIds())
            {
                for (int i = 0; i < form.getVisitRowIds().length; i++)
                {
                    int visitRowId = form.getVisitRowIds()[i];
                    VisitDataSetType type = VisitDataSetType.valueOf(form.getVisitStatus()[i]);
                    if (modified.getVisitType(visitRowId) != type)
                    {
                        StudyManager.getInstance().updateVisitDataSetMapping(getUser(), getContainer(),
                                visitRowId, form.getDatasetId(), type);
                    }
                }
            }
            return true;
        }

        public ActionURL getSuccessURL(DataSetForm dataSetForm)
        {
            return new ActionURL(DatasetDetailsAction.class, getContainer()).addParameter("id", dataSetForm.getDatasetId());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendNavTrailDatasetAdmin(root);
            return root.addChild("Edit " + _def.getLabel() + " Visits");
        }
    }

    @RequiresPermission(ACL.PERM_INSERT)
    public class ShowImportDatasetAction extends FormViewAction<ImportDataSetForm>
    {
        public void validateCommand(ImportDataSetForm target, Errors errors)
        {
        }

        @SuppressWarnings("deprecation")
        public ModelAndView getView(ImportDataSetForm form, boolean reshow, BindException errors) throws Exception
        {
            Study study = getStudy();
            DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, form.getDatasetId());
            if (null == def || def.getTypeURI() == null)
            {
                return new HtmlView("Error",
                        "Dataset is not yet defined. <a href=\"datasetDetails.view?id=%d\">Show Dataset Details</a>", form.getDatasetId());
            }

            if (null == PipelineService.get().findPipelineRoot(getContainer()))
                return new RequirePipelineView(getStudy(), true, errors);

            form.setTypeURI(StudyManager.getInstance().getDatasetType(getContainer(), form.getDatasetId()));
            if (form.getTypeURI() == null)
                return HttpView.throwNotFoundMV();
            form.setKeys(StringUtils.join(def.getKeyNames(), ", "));

            return new JspView<ImportDataSetForm>("/org/labkey/study/view/importDataset.jsp", form, errors);
        }

        public boolean handlePost(ImportDataSetForm form, BindException errors) throws Exception
        {
            String[] keys = new String[]{"ParticipantId", "SequenceNum"};

            if (null != PipelineService.get().findPipelineRoot(getContainer()))
            {
                String formKeys = StringUtils.trimToEmpty(form.getKeys());
                if (formKeys != null && formKeys.length() > 0)
                {
                    String[] keysPOST = formKeys.split(",");
                    if (keysPOST.length >= 1)
                        keys[0] = keysPOST[0];
                    if (keysPOST.length >= 2)
                        keys[1] = keysPOST[1];
                }

                Map<String,String> columnMap = new CaseInsensitiveHashMap<String>();
                columnMap.put(keys[0], DataSetDefinition.getParticipantIdURI());
                columnMap.put(keys[1], DataSetDefinition.getSequenceNumURI());
                // 2379
                // see DatasetBatch.prepareImport()
                columnMap.put("visit", DataSetDefinition.getSequenceNumURI());
                columnMap.put("date", DataSetDefinition.getVisitDateURI());
                columnMap.put("ptid", DataSetDefinition.getParticipantIdURI());
                columnMap.put("dfcreate", DataSetDefinition.getCreatedURI());     // datafax field name
                columnMap.put("dfmodify", DataSetDefinition.getModifiedURI());    // datafax field name
                List<String> errorList = new LinkedList<String>();

                DataSetDefinition dsd = StudyManager.getInstance().getDataSetDefinition(getStudy(), form.getDatasetId());
                Pair<String[],UploadLog> result = AssayPublishManager.getInstance().importDatasetTSV(getUser(), getStudy(), dsd, form.getTsv(), columnMap, errorList);

                if (result.getKey().length > 0)
                {
                    // Log the import
                    String comment = "Dataset data imported. " + result.getKey().length + " rows imported";
                    StudyServiceImpl.addDatasetAuditEvent(
                            getUser(), getContainer(), dsd, comment, result.getValue());
                }
                for (String error : errorList)
                {
                    errors.reject("showImportDataset", error);
                }
                return errorList.isEmpty();
            }
            return false;
        }

        public ActionURL getSuccessURL(ImportDataSetForm form)
        {
            return new ActionURL(DatasetAction.class, getContainer()).
                    addParameter(DataSetDefinition.DATASETKEY, form.getDatasetId());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Import Dataset");
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class BulkImportDataTypesAction extends FormViewAction<BulkImportTypesForm>
    {
        public void validateCommand(BulkImportTypesForm target, Errors errors)
        {
        }

        public ModelAndView getView(BulkImportTypesForm form, boolean reshow, BindException errors) throws Exception
        {
            ModelAndView view = new StudyJspView<BulkImportTypesForm>(getStudy(), "bulkImportDataTypes.jsp", form, errors);
            view.addObject("errors", errors);
            return view;
        }

        @SuppressWarnings("unchecked")
        public boolean handlePost(BulkImportTypesForm form, BindException errors) throws Exception
        {
            TabLoader loader = new TabLoader(form.tsv, true);
            loader.setLowerCaseHeaders(true);
            loader.setParseQuotes(true);
            Map<String, Object>[] mapsLoad = (Map<String, Object>[]) loader.load();

            // CONSIDER: move all this into StudyManager
            ArrayList<Map<String, Object>> mapsImport = new ArrayList<Map<String, Object>>(mapsLoad.length);
            PropertyDescriptor[] pds;

            if (mapsLoad.length > 0)
            {
                Map<Integer, DataSetImportInfo> datasetInfoMap = new HashMap<Integer, DataSetImportInfo>();
                int missingTypeNames = 0;
                int missingTypeIds = 0;

                for (Map<String, Object> props : mapsLoad)
                {
                    String typeName = (String) props.get(form.getTypeNameColumn());
                    Object typeIdObj = props.get(form.getTypeIdColumn());
                    String propName = (String) props.get("Property");

                    if (typeName == null || typeName.length() == 0)
                    {
                        missingTypeNames++;
                        continue;
                    }

                    if (!(typeIdObj instanceof Integer))
                    {
                        missingTypeIds++;
                        continue;
                    }

                    boolean isHidden = false;
                    String hiddenValue = (String)props.get("hidden");
                    if ("true".equalsIgnoreCase(hiddenValue))
                        isHidden = true;

                    Integer typeId = (Integer) typeIdObj;
                    DataSetImportInfo info = datasetInfoMap.get(typeId);
                    if (info != null)
                    {
                        if (!info.name.equals(typeName))
                        {
                            errors.reject("bulkImportDataTypes", "Type ID " + typeName + " is associated with multiple " +
                                    "type names ('" + typeName + "' and '" + info.name + "').");
                            return false;
                        }
                        if (!info.isHidden == isHidden)
                        {
                            errors.reject("bulkImportDataTypes", "Type ID " + typeName + " is set as both hidden and "
                            + "not hidden in different fields.");
                            return false;
                        }
                    }

                    // we've got a good entry
                    if (null == info)
                    {
                        info = new DataSetImportInfo(typeName);
                        info.label = (String) props.get(form.getLabelColumn());

                        info.isHidden = isHidden;
                        datasetInfoMap.put((Integer) typeIdObj, info);
                    }

                    // filter out the built-in types
                    // UNDONE: need enum or something also see bug 1945
                    if ("ParticipantId".equalsIgnoreCase(propName) ||
                            "ptid".equalsIgnoreCase(propName) ||
                            "VisitSequenceNum".equalsIgnoreCase(propName) ||
                            "DatasetId".equalsIgnoreCase(propName) ||
                            "VisitDate".equalsIgnoreCase(propName) ||
                            "SiteId".equalsIgnoreCase(propName) ||
                            "Created".equalsIgnoreCase(propName) ||
                            "Modified".equalsIgnoreCase(propName) ||
                            "Key".equalsIgnoreCase(propName) ||
                            "Hidden".equalsIgnoreCase(propName))
                        continue;

                    // look for visitdate column
                    String conceptURI = (String)props.get("ConceptURI");
                    if (null == conceptURI)
                    {
                        String vtype = (String)props.get("vtype");  // datafax special case
                        if (null != vtype && vtype.toLowerCase().contains("visitdate"))
                            conceptURI = DataSetDefinition.getVisitDateURI();
                    }
                    if (DataSetDefinition.getVisitDateURI().equalsIgnoreCase(conceptURI))
                    {
                        if (info.visitDatePropertyName == null)
                            info.visitDatePropertyName = propName;
                        else
                        {
                            errors.reject("bulkImportDataTypes", "Type ID " + typeName + " has multiple visitdate fields (" + info.visitDatePropertyName + " and " + propName+").");
                            return false;
                        }
                    }

                    // Deal with extra key field
                    Integer keyField = (Integer)props.get("key");
                    if (keyField != null && keyField.intValue() == 1)
                    {
                        if (info.keyPropertyName == null)
                            info.keyPropertyName = propName;
                        else
                        {
                            // It's already been set
                            errors.reject("bulkImportDataTypes", "Type ID " + typeName + " has multiple fields with key set to 1.");
                            return false;
                        }
                    }

                    mapsImport.add(props);
                }

                if (missingTypeNames > 0)
                {
                    errors.reject("bulkImportDataTypes", "Couldn't find type name in column " + form.getTypeNameColumn() + " in " + missingTypeNames + " rows.");
                    return false;
                }
                if (missingTypeIds > 0)
                {
                    errors.reject("bulkImportDataTypes", "Couldn't find type id in column " + form.getTypeIdColumn() + " in " + missingTypeIds + " rows.");
                    return false;
                }

                String domainURI = getDomainURI(getContainer(), null);
                Map[] m = mapsImport.toArray(new Map[mapsImport.size()]);

                List<String> importErrors = new LinkedList<String>();
                pds = OntologyManager.importTypes(domainURI, form.getTypeNameColumn(), m, importErrors, getContainer(), true);

                if (!importErrors.isEmpty())
                {
                    for (String error : importErrors)
                        errors.reject("bulkImportDataTypes", error);
                    return false;
                }

                if (pds != null && pds.length > 0)
                {
                    StudyManager manager = StudyManager.getInstance();
                    for (Map.Entry<Integer, DataSetImportInfo> entry : datasetInfoMap.entrySet())
                    {
                        int id = entry.getKey().intValue();
                        DataSetImportInfo info = entry.getValue();
                        String name = info.name;

                        DataSetDefinition def = manager.getDataSetDefinition(getStudy(), id);
                        Container c = getContainer();
                        if (def == null)
                        {
                            def = new DataSetDefinition(getStudy(), id, name, info.label, null, getDomainURI(c, name, id));
                            def.setVisitDatePropertyName(info.visitDatePropertyName);
                            def.setShowByDefault(!info.isHidden);
                            def.setKeyPropertyName(info.keyPropertyName);
                            manager.createDataSetDefinition(getUser(), def);
                        }
                        else
                        {
                            def = def.createMutable();
                            if (null != info.label)
                                def.setLabel(info.label);
                            def.setName(name);
                            def.setTypeURI(getDomainURI(c, def));
                            def.setVisitDatePropertyName(info.visitDatePropertyName);
                            def.setShowByDefault(!info.isHidden);
                            def.setKeyPropertyName(info.keyPropertyName);
                            manager.updateDataSetDefinition(getUser(), def);
                        }
                    }
                }
            }
            return true;
        }

        public ActionURL getSuccessURL(BulkImportTypesForm bulkImportTypesForm)
        {
            return new ActionURL(ManageTypesAction.class, getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendNavTrailDatasetAdmin(root);
            return root.addChild("Bulk Import");
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

    private static class DataSetImportInfo
    {
        DataSetImportInfo(String name)
        {
            this.name = name;
        }
        String name;
        String label;
        String visitDatePropertyName;
        String startDatePropertyName;
        boolean isHidden;
        String keyPropertyName;
    }

    @RequiresPermission(ACL.PERM_UPDATE)
    public class ShowUploadHistoryAction extends SimpleViewAction<IdForm>
    {
        String _datasetName;

        public ModelAndView getView(IdForm form, BindException errors) throws Exception
        {
            TableInfo tInfo = StudySchema.getInstance().getTableInfoUploadLog();
            DataRegion dr = new DataRegion();
            dr.addColumns(tInfo, "RowId,Created,CreatedBy,Status,Description");
            GridView gv = new GridView(dr);
            DisplayColumn dc = new SimpleDisplayColumn(null) {
                @Override
                public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
                {
                    out.write("[<a href=\"downloadTsv.view?id=" + ctx.get("RowId") + "\">Download&nbsp;Data&nbsp;File</a>]");
                }
            };
            dr.addDisplayColumn(dc);
            dr.setButtonBar(ButtonBar.BUTTON_BAR_EMPTY);

            SimpleFilter filter = new SimpleFilter("container", getContainer().getId());
            if (form.getId() != 0)
            {
                filter.addCondition("datasetId", form.getId());
                _datasetName = StudyManager.getInstance().getDataSetDefinition(getStudy(), form.getId()).getLabel();
            }

            gv.setFilter(filter);
            return gv;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Upload History" + (null != _datasetName ? " for " + _datasetName : ""));
        }
    }

    @RequiresPermission(ACL.PERM_UPDATE)
    public class DownloadTsvAction extends SimpleViewAction<IdForm>
    {
        public ModelAndView getView(IdForm form, BindException errors) throws Exception
        {
            UploadLog ul = AssayPublishManager.getInstance().getUploadLog(getContainer(), form.getId());
            PageFlowUtil.streamFile(getViewContext().getResponse(), ul.getFilePath(), true);

            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class DatasetItemDetailsAction extends SimpleViewAction<SourceLsidForm>
    {
        public ModelAndView getView(SourceLsidForm form, BindException errors) throws Exception
        {
            String url = LsidManager.get().getDisplayURL(form.getSourceLsid());
            if (url == null)
            {
                return new HtmlView("The assay run that produced the data has been deleted.");
            }
            return HttpView.redirect(url);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class PublishHistoryDetailsAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            final Study study = getStudy();
            final ViewContext context = getViewContext();
            final ActionURL url = context.getActionURL();

            VBox view = new VBox();

            int datasetId = null == context.get(DataSetDefinition.DATASETKEY) ? 0 : Integer.parseInt((String) context.get(DataSetDefinition.DATASETKEY));
            final DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, datasetId);
            final StudyQuerySchema querySchema = new StudyQuerySchema(study, getUser(), true);
            QuerySettings qs = new QuerySettings(url, DataSetQueryView.DATAREGION);
            qs.setSchemaName(querySchema.getSchemaName());
            qs.setQueryName(def.getLabel());

            if (!def.canRead(getUser()))
            {
                //requiresLogin();
                view.addView(new HtmlView("User does not have read permission on this dataset."));
            }
            else
            {
                String protocolId = (String)getViewContext().get("protocolId");
                String sourceLsid = (String)getViewContext().get("sourceLsid");
                String recordCount = (String)getViewContext().get("recordCount");

                ActionButton deleteRows = new ActionButton("button", "Recall Selected Rows");
                ActionURL deleteURL = new ActionURL(DeletePublishedRowsAction.class, getContainer());
                deleteURL.addParameter("datasetId", datasetId);
                deleteURL.addParameter("protocolId", protocolId);
                deleteURL.addParameter("sourceLsid", sourceLsid);

                //String deleteRowsURL = ActionURL.toPathString("Study", "deletePublishedRows", getContainer()) + "?datasetId=" + datasetId;
                deleteRows.setScript("return confirm(\"Recall selected rows of this dataset?\") && verifySelected(this.form, \"" + deleteURL.getLocalURIString() + "\", \"post\", \"rows\")");
                deleteRows.setActionType(ActionButton.Action.GET);
                deleteRows.setDisplayPermission(ACL.PERM_DELETE);

                PublishedRecordQueryView qv = new PublishedRecordQueryView(datasetId, querySchema, qs, sourceLsid,
                        NumberUtils.toInt(protocolId), NumberUtils.toInt(recordCount));
                qv.setButtons(Collections.singletonList(deleteRows));
                view.addView(qv);
            }
            return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Copy-to-Study History Details");
        }
    }

    @RequiresPermission(ACL.PERM_DELETE)
    public class DeletePublishedRowsAction extends FormHandlerAction<DeleteDatasetRowsForm>
    {
        public void validateCommand(DeleteDatasetRowsForm target, Errors errors)
        {
        }

        public boolean handlePost(DeleteDatasetRowsForm form, BindException errors) throws Exception
        {
            Set<String> lsids = DataRegionSelection.getSelected(getViewContext(), true);
            String protocolId = (String)getViewContext().get("protocolId");
            String sourceLsid = (String)getViewContext().get("sourceLsid");
            Container sourceContainer = getContainer();
            if (sourceLsid != null)
            {
                ExpRun expRun = ExperimentService.get().getExpRun(sourceLsid);
                if (expRun != null && expRun.getContainer() != null)
                    sourceContainer = expRun.getContainer();
            }

            // log
            if (!lsids.isEmpty())
            {
                final DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(getStudy(), form.getDatasetId());

                if (protocolId != null)
                {
                    AuditLogEvent event = new AuditLogEvent();

                    event.setCreatedBy(getUser().getUserId());
                    event.setEventType(AssayPublishManager.ASSAY_PUBLISH_AUDIT_EVENT);
                    event.setContainerId(sourceContainer.getId());
                    event.setKey1(getContainer().getId());

                    // TODO: eventually require a protocol instead of an assayname so that we can use a protocol row id to
                    // uniquely identify an assay.
                    String assayName = "";
                    if (def != null)
                        assayName = def.getLabel();

                    event.setIntKey1(NumberUtils.toInt(protocolId));
                    event.setComment(lsids.size() + " row(s) were deleted from the assay: " + assayName);

                    Map<String,Object> dataMap = Collections.<String,Object>singletonMap("datasetId", form.getDatasetId());

                    AssayAuditViewFactory.getInstance().ensureDomain(getUser());
                    AuditLogService.get().addEvent(event, dataMap, AuditLogService.get().getDomainURI(AssayPublishManager.ASSAY_PUBLISH_AUDIT_EVENT));
                }
                List<String> lsidList = new ArrayList<String>(lsids);
                StudyManager.getInstance().deleteDatasetRows(getStudy(), def, lsidList);
            }

            ExpProtocol protocol = ExperimentService.get().getExpProtocol(NumberUtils.toInt(protocolId));
            if (protocol != null)
            {
                HttpView.throwRedirect(AssayPublishService.get().getPublishHistory(sourceContainer, protocol));
            }
            return true;
        }

        public ActionURL getSuccessURL(DeleteDatasetRowsForm form)
        {
            return new ActionURL(DatasetAction.class, getContainer()).
                    addParameter(DataSetDefinition.DATASETKEY, form.getDatasetId());
        }
    }

    public static class DeleteDatasetRowsForm
    {
        private int datasetId;

        public int getDatasetId()
        {
            return datasetId;
        }

        public void setDatasetId(int datasetId)
        {
            this.datasetId = datasetId;
        }
    }

    @RequiresPermission(ACL.PERM_DELETE)
    public class DeleteDatasetRowsAction extends FormHandlerAction<DeleteDatasetRowsForm>
    {
        public void validateCommand(DeleteDatasetRowsForm target, Errors errors)
        {
        }

        public boolean handlePost(DeleteDatasetRowsForm form, BindException errors) throws Exception
        {
            int datasetId = form.getDatasetId();
            DataSetDefinition dataset = StudyManager.getInstance().getDataSetDefinition(getStudy(), datasetId);
            if (null == dataset)
                HttpView.throwNotFound();

            // Operate on each individually for audit logging purposes, but transact the whole thing
            DbScope scope =  StudySchema.getInstance().getSchema().getScope();
            scope.beginTransaction();
            boolean inTransaction = true;

            try
            {
                Set<String> lsids = DataRegionSelection.getSelected(getViewContext(), true);
                for (String lsid : lsids)
                {
                    StudyService.get().deleteDatasetRow(getUser(), getContainer(), datasetId, lsid);
                }

                scope.commitTransaction();
                inTransaction = false;
                return true;
            }
            finally
            {
                if (inTransaction)
                    scope.rollbackTransaction();
            }
        }

        public ActionURL getSuccessURL(DeleteDatasetRowsForm form)
        {
            return new ActionURL(DatasetAction.class, getContainer()).
                    addParameter(DataSetDefinition.DATASETKEY, form.getDatasetId());
        }
    }

    private String getDomainURI(Container c, DataSetDefinition def)
    {
        if (null == def)
            return getDomainURI(c, null, 0);
        else
            return getDomainURI(c, def.getName(), def.getDataSetId());
    }

    private String getDomainURI(Container c, String name, int datasetId)
    {
        return (new DatasetDomainKind()).generateDomainURI(c, name, datasetId);
    }

    public static class ImportTypeForm
    {
        private String typeName;
        private String label;
        private String tsv;
        private Integer dataSetId;
        private String keyPropertyName;
        private String category;
        private boolean autoDatasetId;
        private boolean create;
        private boolean demographicData;
        String datasetIdStr;
        private BindException _errors;

        public String getTypeName()
        {
            return typeName;
        }

        public void setTypeName(String typeName)
        {
            this.typeName = typeName;
        }

        public String getTsv()
        {
            return tsv;
        }

        public void setTsv(String tsv)
        {
            this.tsv = tsv;
        }

        public String getDataSetIdStr()
        {
            if (null != datasetIdStr)
                return datasetIdStr;
            return null == dataSetId ? null : dataSetId.toString();
        }

        public void setDataSetIdStr(String strId)
        {
            datasetIdStr = strId;
            try
            {
                dataSetId = (Integer) ConvertUtils.convert(strId, Integer.class);
            }
            catch (Exception x)
            {
                dataSetId = null;
            }
        }

        public Integer getDataSetId()
        {
            return dataSetId;
        }

        public void setDataSetId(Integer dataSetId)
        {
            this.dataSetId = dataSetId;
        }

        public String getLabel()
        {
            return label;
        }

        public void setLabel(String label)
        {
            this.label = label;
        }

        public String getKeyPropertyName()
        {
            return keyPropertyName;
        }

        public void setKeyPropertyName(String keyPropertyName)
        {
            this.keyPropertyName = keyPropertyName;
        }

        public String getCategory()
        {
            return category;
        }

        public void setCategory(String category)
        {
            this.category = category;
        }

        public boolean isAutoDatasetId()
        {
            return autoDatasetId;
        }

        public void setAutoDatasetId(boolean autoDatasetId)
        {
            this.autoDatasetId = autoDatasetId;
        }

        public boolean isCreate()
        {
            return create;
        }

        public void setCreate(boolean create)
        {
            this.create = create;
        }

        public boolean isDemographicData()
        {
            return demographicData;
        }

        public void setDemographicData(boolean demographicData)
        {
            this.demographicData = demographicData;
        }

        public BindException getErrors()
        {
            return _errors;
        }

        public void setErrors(BindException errors)
        {
            _errors = errors;
        }
    }

    public static class OverviewBean
    {
        public Study study;
        public Map<VisitMapKey,Integer> visitMapSummary;
        public boolean showAll;
        public boolean canManage;
        public Integer cohortId;
        public boolean showCohorts;
    }

    /**
     * Tweak the link url for participant view so that it contains enough information to regenerate
     * the cached list of participants.
     */
    private void setColumnURL(final ActionURL url, final QueryView queryView,
                              final UserSchema querySchema, final DataSetDefinition def)
    {
        List<DisplayColumn> columns = queryView.getDisplayColumns();
        for (DisplayColumn col : columns)
        {
            if ("ParticipantId".equalsIgnoreCase(col.getName()))
            {
                col.getColumnInfo().setFk(new QueryForeignKey(querySchema, "Participant", "ParticipantId", "ParticipantId")
                {
                    public StringExpressionFactory.StringExpression getURL(ColumnInfo parent)
                    {
                        ActionURL base = new ActionURL(ParticipantAction.class, querySchema.getContainer());
                        base.addParameter(DataSetDefinition.DATASETKEY, Integer.toString(def.getDataSetId()));

                        // push any filter, sort params, and viewname
                        for (Pair<String, String> param : url.getParameters())
                        {
                            if ((param.getKey().contains(".sort")) ||
                                (param.getKey().contains("~")) ||
                                ("cohortId".equals(param.getKey())) ||
                                ("Dataset.viewName".equals(param.getKey())))
                            {
                                base.addParameter(param.getKey(), param.getValue());
                            }
                        }
                        Map<Object,ColumnInfo> params = new HashMap<Object,ColumnInfo>();
                        params.put("participantId", parent);

                        return new LookupURLExpression(base, params);
                    }
                });
                return;
            }
        }
    }

    private boolean hasSourceLsids(TableInfo datasetTable) throws SQLException
    {
        SimpleFilter sourceLsidFilter = new SimpleFilter();
        sourceLsidFilter.addCondition("SourceLsid", null, CompareType.NONBLANK);
        ResultSet rs = null;
        try
        {
            rs = Table.select(datasetTable, Collections.singleton("SourceLsid"), sourceLsidFilter, null);
            if (rs.next())
                return true;
        }
        finally
        {
            if (rs != null) try { rs.close(); } catch (SQLException e) {}
        }
        return false;
    }

    private static final String PARTICIPANT_PROPS_CACHE = "Study_participants/propertyCache";
    private static final String DATASET_SORT_COLUMN_CACHE = "Study_participants/datasetSortColumnCache";
    @SuppressWarnings("unchecked")
    private static Map<String, PropertyDescriptor[]> getParticipantPropsMap(ViewContext context)
    {
        HttpSession session = context.getRequest().getSession(true);
        Map<String, PropertyDescriptor[]> map = (Map<String, PropertyDescriptor[]>) session.getAttribute(PARTICIPANT_PROPS_CACHE);
        if (map == null)
        {
            map = new HashMap<String, PropertyDescriptor[]>();
            session.setAttribute(PARTICIPANT_PROPS_CACHE, map);
        }
        return map;
    }

    public static PropertyDescriptor[] getParticipantPropsFromCache(ViewContext context, String typeURI)
    {
        Map<String, PropertyDescriptor[]> map = getParticipantPropsMap(context);
        PropertyDescriptor[] props = map.get(typeURI);
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
            map = new HashMap<String, Map<String, Integer>>();
            session.setAttribute(DATASET_SORT_COLUMN_CACHE, map);
        }
        return map;
    }

    public static Map<String, Integer> getSortedColumnList(ViewContext context, DataSetDefinition dsd)
    {
        Map<String, Map<String, Integer>> map = getDatasetSortColumnMap(context);
        Map<String, Integer> sortMap = map.get(dsd.getLabel());

        if (sortMap == null)
        {
            QueryDefinition qd = QueryService.get().getQueryDef(dsd.getContainer(), "study", dsd.getLabel());
            if (qd == null)
            {
                UserSchema schema = QueryService.get().getUserSchema(context.getUser(), context.getContainer(), "study");
                qd = schema.getQueryDefForTable(dsd.getLabel());
            }
            CustomView cview = qd.getCustomView(context.getUser(), context.getRequest(), null);
            if (cview != null)
            {
                sortMap = new HashMap<String, Integer>();
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
                map.put(dsd.getLabel(), Collections.<String,Integer>emptyMap());
            }
        }
        return sortMap;
    }

    private static String getParticipantListCacheKey(int dataset, String viewName, Cohort cohort)
    {
        String key = Integer.toString(dataset);
        // if there is also a view associated with the dataset, incorporate it into the key as well
        if (viewName != null && !StringUtils.isEmpty(viewName))
            key = key + viewName;
        if (cohort != null)
            key = key + "cohort" + cohort.getRowId();
        return key;
    }

    public static void addParticipantListToCache(ViewContext context, int dataset, String viewName, List<String> participants, Cohort cohort)
    {

        Map<String, List<String>> map = getParticipantMapFromCache(context);
        map.put(getParticipantListCacheKey(dataset, viewName, cohort), participants);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> getParticipantMapFromCache(ViewContext context)
    {
        HttpSession session = context.getRequest().getSession(true);
        Map<String, List<String>> map = (Map<String, List<String>>) session.getAttribute(PARTICIPANT_CACHE_PREFIX);
        if (map == null)
        {
            map = new HashMap<String, List<String>>();
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
            map = new HashMap<Integer, Map<Integer, String>>();
            session.setAttribute(EXPAND_CONTAINERS_KEY, map);
        }

        Map<Integer, String> expandedMap = map.get(datasetId);
        if (expandedMap == null)
        {
            expandedMap = new HashMap<Integer, String>();
            map.put(datasetId, expandedMap);
        }
        return expandedMap;
    }

    public static List<String> getParticipantListFromCache(ViewContext context, int dataset, String viewName, Cohort cohort)
    {
        Map<String, List<String>> map = getParticipantMapFromCache(context);
        String key = getParticipantListCacheKey(dataset, viewName, cohort);
        List<String> plist = map.get(key);
        if (plist == null)
        {
            // not in cache, or session expired, try to regenerate the list
            plist = generateParticipantListFromURL(context);
            map.put(key, plist);
        }
        return plist;
    }

    private static List<String> generateParticipantListFromURL(ViewContext context)
    {
        try {
            final StudyManager studyMgr = StudyManager.getInstance();
            final Study study = studyMgr.getStudy(context.getContainer());
            String cohortIdStr = context.getActionURL().getParameter("CohortId");
            Cohort cohort = null;
            if (cohortIdStr != null && StudyManager.getInstance().showCohorts(context.getContainer(), context.getUser()))
            {
                try
                {
                    cohort = StudyManager.getInstance().getCohortForRowId(context.getContainer(), context.getUser(), Integer.parseInt(cohortIdStr));
                }
                catch (NumberFormatException e)
                {
                    // fall through with null cohort
                }
            }

            int datasetId = null == context.get(DataSetDefinition.DATASETKEY) ? 0 : Integer.parseInt((String) context.get(DataSetDefinition.DATASETKEY));
            DataSetDefinition def = studyMgr.getDataSetDefinition(study, datasetId);
            if (null == def)
                return Collections.emptyList();
            String typeURI = def.getTypeURI();
            if (null == typeURI)
                return Collections.emptyList();

            int visitRowId = null == context.get(Visit.VISITKEY) ? 0 : Integer.parseInt((String) context.get(Visit.VISITKEY));
            Visit visit = null;
            if (visitRowId != 0)
            {
                visit = studyMgr.getVisitForRowId(study, visitRowId);
                if (null == visit)
                    return Collections.emptyList();
            }
            StudyQuerySchema querySchema = new StudyQuerySchema(study, context.getUser(), true);
            QuerySettings qs = new QuerySettings(context.getActionURL(), DataSetQueryView.DATAREGION);
            qs.setSchemaName(querySchema.getSchemaName());
            qs.setQueryName(def.getLabel());
            DataSetQueryView queryView = new DataSetQueryView(datasetId, querySchema, qs, visit, cohort);
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
            Set<String> participantSet = new LinkedHashSet<String>();
            ResultSet rs = null;
            try
            {
                rs = queryView.getResultset();
                while (rs.next())
                {
                    String ptid = rs.getString("ParticipantId");
                    participantSet.add(ptid);
                }
                return new ArrayList<String>(participantSet);
            }
            catch (Exception e)
            {
                _log.error(e);
            }
            finally
            {
                if (null != rs)
                    try { rs.close(); } catch (SQLException e){}
            }
        }
        return Collections.emptyList();
    }

    public static class VisitForm extends ViewForm
    {
        private int[] _dataSetIds;
        private String[] _dataSetStatus;
        private Double _sequenceNumMin;
        private Double _sequenceNumMax;
        private Character _typeCode;
        private boolean _showByDefault;
        private Integer _cohortId;
        private String _label;
        private Visit _visit;

        public VisitForm()
        {
        }

        /** NOTE: BeanViewForm doesn't handle int[], String[] */
        @Override
        public void reset(ActionMapping arg0, HttpServletRequest request)
        {
            super.reset(arg0, request);
            if (null != StringUtils.trimToNull(request.getParameter(".oldValues")))
                try
                {
                    _visit = (Visit) PageFlowUtil.decodeObject(request.getParameter(".oldValues"));
                }
                catch (IOException x)
                {
                    throw new RuntimeException(x);
                }

            _dataSetIds = new int[getParameterCount(request, "dataSetIds")];
            String[] values = request.getParameterValues("dataSetIds");
            for (int i=0 ; i<_dataSetIds.length ; i++)
                _dataSetIds[i] = Integer.parseInt(values[i]);
            _dataSetStatus = request.getParameterValues("dataSetStatus");
        }

        public void validate(Errors errors)
        {
            //check for null min/max sequence numbers
            if(null == getSequenceNumMax() && null == getSequenceNumMin())
                errors.reject(null, "You must specify at least a minimum or a maximum value for the visit range.");

            //if min is null but max is not, set min to max
            //and vice-versa
            if(null == getSequenceNumMin() && null != getSequenceNumMax())
                setSequenceNumMin(getSequenceNumMax());
            if(null == getSequenceNumMax() && null != getSequenceNumMin())
                setSequenceNumMax(getSequenceNumMin());

            Visit visit = getBean();
            if (visit.getSequenceNumMin() > visit.getSequenceNumMax())
            {
                double min = visit.getSequenceNumMax();
                double max = visit.getSequenceNumMin();
                visit.setSequenceNumMax(max);
                visit.setSequenceNumMin(min);
            }
            setBean(visit);
            if (visit.getSequenceNumMin() < 0 || visit.getSequenceNumMax() < 0)
                errors.reject(null, "Sequence numbers must be greater than or equal to zero.");
        }

        public Visit getBean()
        {
            if (null == _visit)
                _visit = new Visit();
            _visit.setContainer(getContainer());
            if (getTypeCode() != null)
                _visit.setTypeCode(getTypeCode());
            _visit.setLabel(getLabel());
            try
            {
                if(null != getSequenceNumMax())
                    _visit.setSequenceNumMax(getSequenceNumMax().doubleValue());
                if(null != getSequenceNumMin())
                    _visit.setSequenceNumMin(getSequenceNumMin().doubleValue());
            }
            catch (NumberFormatException x)
            {
                addActionError("Sequence numbers must be numeric");
            }
            _visit.setCohortId(getCohortId());

            return _visit;
        }

        public void setBean(Visit bean)
        {
            if (0 != bean.getSequenceNumMax())
                setSequenceNumMax(bean.getSequenceNumMax());
            if (0 != bean.getSequenceNumMin())
                setSequenceNumMin(bean.getSequenceNumMin());
            if (null != bean.getType())
                setTypeCode(bean.getTypeCode());
            setLabel(bean.getLabel());
            setCohortId(bean.getCohortId());
        }

        public String[] getDataSetStatus()
        {
            return _dataSetStatus;
        }

        public void setDataSetStatus(String[] dataSetStatus)
        {
            _dataSetStatus = dataSetStatus;
        }

        public int[] getDataSetIds()
        {
            return _dataSetIds;
        }

        public void setDataSetIds(int[] dataSetIds)
        {
            _dataSetIds = dataSetIds;
        }

        public Double getSequenceNumMin()
        {
            return _sequenceNumMin;
        }

        public void setSequenceNumMin(Double sequenceNumMin)
        {
            _sequenceNumMin = sequenceNumMin;
        }

        public Double getSequenceNumMax()
        {
            return _sequenceNumMax;
        }

        public void setSequenceNumMax(Double sequenceNumMax)
        {
            _sequenceNumMax = sequenceNumMax;
        }

        public Character getTypeCode()
        {
            return _typeCode;
        }

        public void setTypeCode(Character typeCode)
        {
            _typeCode = typeCode;
        }

        public boolean isShowByDefault()
        {
            return _showByDefault;
        }

        public void setShowByDefault(boolean showByDefault)
        {
            this._showByDefault = showByDefault;
        }

        public String getLabel()
        {
            return _label;
        }

        public void setLabel(String label)
        {
            this._label = label;
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

    private static int getParameterCount(HttpServletRequest r, String s)
    {
        String[] values = r.getParameterValues(s);
        return values == null ? 0 : values.length;
    }

    public static class ManageCohortsForm extends ViewForm
    {
        private int[] _ids;
        private String[] _labels;
        private String _newLabel;
        private Integer _participantCohortDataSetId;
        private String _participantCohortProperty;
        private boolean _reshowPage;
        private boolean _clearParticipants;

        public int[] getIds()
        {
            return _ids;
        }

        public void setIds(int[] ids)
        {
            _ids = ids;
        }

        public String[] getLabels()
        {
            return _labels;
        }

        public void setLabels(String[] labels)
        {
            _labels = labels;
        }

        public String getNewLabel()
        {
            return _newLabel;
        }

        public void setNewLabel(String newLabel)
        {
            _newLabel = newLabel;
        }

        public Integer getParticipantCohortDataSetId()
        {
            return _participantCohortDataSetId;
        }

        public void setParticipantCohortDataSetId(Integer participantCohortDataSetId)
        {
            _participantCohortDataSetId = participantCohortDataSetId;
        }

        public String getParticipantCohortProperty()
        {
            return _participantCohortProperty;
        }

        public void setParticipantCohortProperty(String participantCohortProperty)
        {
            _participantCohortProperty = participantCohortProperty;
        }

        public boolean isReshowPage()
        {
            return _reshowPage;
        }

        public void setReshowPage(boolean reshowPage)
        {
            _reshowPage = reshowPage;
        }

        public boolean isClearParticipants()
        {
            return _clearParticipants;
        }

        public void setClearParticipants(boolean clearParticipants)
        {
            _clearParticipants = clearParticipants;
        }
    }

    public static class ManageCohortsBean
    {
        private BindException _errors;
        private Cohort[] _cohorts;
        private Container _container;

        public ManageCohortsBean(Container container, BindException errors)
        {
            _container = container;
            _errors = errors;
        }

        public Cohort[] getCohorts()
        {
            if (_cohorts == null)
            {
                SimpleFilter filter = new SimpleFilter("Container", _container);
                try
                {
                    _cohorts = Table.select(StudySchema.getInstance().getTableInfoCohort(), Table.ALL_COLUMNS,
                            filter, new Sort("Label"), Cohort.class);
                }
                catch (SQLException e)
                {
                    throw new RuntimeSQLException(e);
                }
            }
            return _cohorts;
        }

        public DataSetDefinition[] getParticipantCohortDatasets()
        {
            Study study = StudyManager.getInstance().getStudy(_container);
            return StudyManager.getInstance().getDataSetDefinitions(study);
        }

        public Integer getParticipantCohortDataSetId()
        {
            Study study = StudyManager.getInstance().getStudy(_container);
            return study.getParticipantCohortDataSetId();
        }

        public String getParticipantCohortProperty()
        {
            Study study = StudyManager.getInstance().getStudy(_container);
            return study.getParticipantCohortProperty();
        }

        public PropertyDescriptor[] getParticipantCohortDatasetCols()
        {
            Integer particpantCohortDataSetId = getParticipantCohortDataSetId();
            Study study = StudyManager.getInstance().getStudy(_container);
            if (particpantCohortDataSetId == null || particpantCohortDataSetId.intValue() < 0)
                return new PropertyDescriptor[0];
            else
            {
                DataSetDefinition dataset = StudyManager.getInstance().getDataSetDefinition(study, particpantCohortDataSetId.intValue());
                return OntologyManager.getPropertiesForType(dataset.getTypeURI(), _container);
            }
        }

        public BindException getErrors()
        {
            return _errors;
        }

        public void setErrors(BindException errors)
        {
            _errors = errors;
        }

        public Participant[] getParticipants()
        {
            Study study = StudyManager.getInstance().getStudy(_container);
            try
            {
                return StudyManager.getInstance().getParticipants(study);
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class ManageCohortsAction extends FormViewAction<ManageCohortsForm>
    {
        public void validateCommand(ManageCohortsForm form, Errors errors)
        {
            Set<String> labels = new HashSet<String>();
            if (form.getLabels() != null)
            {
                for (String label : form.getLabels())
                {
                    if (labels.contains(label))
                    {
                        errors.reject(null, "Cohort \"" + label + "\" is defined more than once.");
                        return;
                    }
                    else
                        labels.add(label);
                }
            }
            if (labels.contains(form.getNewLabel()))
                errors.reject(null, "Cohort \"" + form.getNewLabel() + "\" is defined more than once.");
        }

        public ModelAndView getView(ManageCohortsForm manageCohortsForm, boolean reshow, BindException errors) throws Exception
        {
            ManageCohortsBean bean = new ManageCohortsBean(getContainer(), errors);
            return new JspView<ManageCohortsBean>("/org/labkey/study/view/manageCohorts.jsp", bean);
        }

        public boolean handlePost(ManageCohortsForm form, BindException errors) throws Exception
        {
            if (form.getNewLabel() != null && form.getNewLabel().length() > 0)
            {
                Cohort newCohort = new Cohort();
                newCohort.setContainer(getContainer());
                newCohort.setLabel(form.getNewLabel());
                Table.insert(getUser(), StudySchema.getInstance().getTableInfoCohort(), newCohort);
            }
            if (form.getIds() != null)
            {
                for (int i = 0; i < form.getIds().length; i++)
                {
                    int rowId = form.getIds()[i];
                    Cohort cohort = new Cohort();
                    cohort.setRowId(rowId);
                    cohort.setLabel(form.getLabels()[i]);
                    cohort.setContainer(getContainer());
                    Table.update(getUser(), StudySchema.getInstance().getTableInfoCohort(), cohort, rowId, null);
                }
            }

            Study study = StudyManager.getInstance().getStudy(getContainer());
            if (!form.isClearParticipants() && 
                    (!nullSafeEqual(study.getParticipantCohortProperty(), form.getParticipantCohortProperty()) ||
                    !nullSafeEqual(study.getParticipantCohortDataSetId(), form.getParticipantCohortDataSetId())))
            {
                study = study.createMutable();
                study.setParticipantCohortDataSetId(form.getParticipantCohortDataSetId());
                study.setParticipantCohortProperty(form.getParticipantCohortProperty());
                StudyManager.getInstance().updateStudy(getUser(), study);
                StudyManager.getInstance().updateParticipantCohorts(getUser(), study);
            }

            if (form.isClearParticipants())
            {
                study = study.createMutable();
                study.setParticipantCohortDataSetId(null);
                study.setParticipantCohortProperty(null);
                StudyManager.getInstance().updateStudy(getUser(), study);
            }

            if (form.isClearParticipants() ||
                    form.getParticipantCohortProperty() == null || 
                    form.getParticipantCohortDataSetId() == null)
            {
                StudyManager.getInstance().clearParticipantCohorts(getUser(), study);
            }
            return true;
        }

        public ActionURL getSuccessURL(ManageCohortsForm manageCohortsForm)
        {
            if (manageCohortsForm.isReshowPage())
                return new ActionURL(ManageCohortsAction.class, getContainer());
            else
                return new ActionURL(ManageStudyAction.class, getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendManageStudy(root);
            return root.addChild("Manage Cohorts");
        }
    }

    public static class DeleteCohortFrom extends IdForm
    {
        private boolean _all = false;

        public boolean isAll()
        {
            return _all;
        }

        public void setAll(boolean all)
        {
            _all = all;
        }
    }
    @RequiresPermission(ACL.PERM_ADMIN)
    public class DeleteCohortAction extends RedirectAction<DeleteCohortFrom>
    {
        public boolean doAction(DeleteCohortFrom form, BindException errors) throws Exception
        {
            if (form.isAll())
            {
                Cohort[] cohorts = StudyManager.getInstance().getCohorts(getContainer(), getUser());
                for (Cohort cohort : cohorts)
                {
                    if (!cohort.isInUse())
                        StudyManager.getInstance().deleteCohort(cohort);
                }
            }
            else
            {
                Cohort cohort = StudyManager.getInstance().getCohortForRowId(getContainer(), getUser(), form.getId());
                if (cohort != null)
                    StudyManager.getInstance().deleteCohort(cohort);
            }
            return true;
        }

        public void validateCommand(DeleteCohortFrom target, Errors errors)
        {
        }

        public ActionURL getSuccessURL(DeleteCohortFrom form)
        {
            return new ActionURL(ManageCohortsAction.class, getContainer());
        }
    }

    // GWT Action
    @RequiresPermission(ACL.PERM_ADMIN)
    public class DatasetServiceAction extends GWTServiceAction
    {
        protected BaseRemoteService createService()
        {
            try
            {
                return new DatasetServiceImpl(getViewContext(), getStudy(), StudyManager.getInstance());
            }
            catch (ServletException se)
            {
                throw new UnexpectedException(se);
            }
        }
    }


    @RequiresPermission(ACL.PERM_ADMIN)
    public class ResetPipelineAction extends SimpleRedirectAction
    {
        public ActionURL getRedirectURL(Object o) throws Exception
        {
            Container c = getContainer();
            String path = (String)getViewContext().get("path");
            String redirect = (String)getViewContext().get("redirect");

            File f = null;

            if (path != null)
            {

                PipeRoot root = PipelineService.get().findPipelineRoot(c);
                if (root != null)
                    f = root.resolvePath(path);
            }

            if (null != f && f.exists() && f.isFile() && f.getPath().endsWith(".lock"))
            {
                f.delete();
            }

            if (null != redirect)
                HttpView.throwRedirect(redirect);
            return PageFlowUtil.urlProvider(PipelineStatusUrls.class).urlBegin(c);
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    /**
     * called from the participanAll.jsp view
     */
    public class SavedChartAction extends SimpleViewAction<ParticipantForm>
    {
        ParticipantForm _bean = null;

        @SuppressWarnings("deprecation")
        public ModelAndView getView(ParticipantForm form, BindException errors) throws Exception
        {
            _bean = form;
            final String participantId = form.getParticipantId();
            final int reportId = form.getReportId();

            Report report = ReportService.get().getReport(reportId);

            if ("plot".equals(form.getAction()))
            {
                if (report != null)
                {
                    report.getDescriptor().setProperty("participantId", participantId);
                    return report.renderReport(getViewContext());
                }
            }
            else if ("delete".equals(form.getAction()))
            {
                ReportService.get().deleteReport(getViewContext(), report);
                ActionURL url;
                if (form.getRedirectUrl() == null)
                    url = new ActionURL(ParticipantAction.class, getContainer());
                else
                    url = new ActionURL(form.getRedirectUrl());
                HttpView.throwRedirect(url);
            }
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return _appendNavTrail(root, _bean.getDatasetId(), 0).
                    addChild("Participant - " + _bean.getParticipantId());
        }
    }


    @RequiresPermission(ACL.PERM_READ)
    public class DefaultDatasetReportAction extends SimpleRedirectAction
    {
        public ActionURL getRedirectURL(Object o) throws Exception
        {
            ViewContext context = getViewContext();
            int datasetId = null == context.get(DataSetDefinition.DATASETKEY) ? 0 : Integer.parseInt((String) context.get(DataSetDefinition.DATASETKEY));

            ActionURL url = getViewContext().cloneActionURL();
            url.setAction(DatasetReportAction.class);

            String defaultView = getDefaultView(context, datasetId);
            if (!StringUtils.isEmpty(defaultView))
                url.addParameter("Dataset.viewName", defaultView);
            return url;
        }
    }



    @RequiresPermission(ACL.PERM_ADMIN)
    public class ManageUndefinedTypes extends SimpleViewAction
    {
        Study study;

        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            study = getStudy();
            return new StudyJspView<Object>(getStudy(), "manageUndefinedTypes.jsp", o, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendNavTrailDatasetAdmin(root);
            return root.addChild("Define Dataset Schemas");
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class TemplateAction extends ExportAction
    {
        public void export(Object o, HttpServletResponse response) throws Exception
        {
            Study study = getStudy();
            ViewContext context = getViewContext();

            int datasetId = null == context.get(DataSetDefinition.DATASETKEY) ? 0 : Integer.parseInt((String) context.get(DataSetDefinition.DATASETKEY));
            DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, datasetId);
            if (null == def)
            {
                redirectTypeNotFound(datasetId);
                return;
            }
            String typeURI = def.getTypeURI();
            if (null == typeURI)
                redirectTypeNotFound(datasetId);

            //TODO: This may unnecessarily select into temp table.
            //Make public entry point for tableInfo without temp table
            TableInfo tinfo = def.getTableInfo(getUser());

            DataRegion dr = new DataRegion();
            dr.setTable(tinfo);

            Set<String> ignoreColumns = new CaseInsensitiveHashSet("lsid", "datasetid", "visitdate", "sourcelsid", "created", "modified", "visitrowid", "day");
            if (study.isDateBased())
                ignoreColumns.add("SequenceNum");

            for (ColumnInfo col : tinfo.getColumnsList())
            {
                if (ignoreColumns.contains(col.getName()))
                    continue;
                DataColumn dc = new DataColumn(col);
                //DO NOT use friendly names. We will import this later.
                dc.setCaption(col.getAlias());
                dr.addDisplayColumn(dc);
            }
            DisplayColumn replaceColumn = new SimpleDisplayColumn();
            replaceColumn.setCaption("replace");
            dr.addDisplayColumn(replaceColumn);

            SimpleFilter filter = new SimpleFilter();
            filter.addWhereClause("0 = 1", new Object[]{});

            RenderContext ctx = new RenderContext(getViewContext());
            ctx.setContainer(getContainer());
            ctx.setBaseFilter(filter);

            ResultSet rs = dr.getResultSet(ctx);
            List<DisplayColumn> cols = dr.getDisplayColumns();
            ExcelWriter xl = new ExcelWriter(rs, cols);
            xl.write(response);
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class ViewPreferencesAction extends SimpleViewAction
    {
        Study study;
        DataSetDefinition def;

        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            study = getStudy();


            String id = getViewContext().getRequest().getParameter(DataSetDefinition.DATASETKEY);
            String defaultView = getViewContext().getRequest().getParameter("defaultView");

            if (NumberUtils.isNumber(id))
            {
                int dsid = NumberUtils.toInt(id);
                def = StudyManager.getInstance().getDataSetDefinition(study, dsid);
                if (def != null)
                {
                    List<Pair<String, String>> views = ReportManager.get().getReportLabelsForDataset(getViewContext(), def);
                    if (defaultView != null)
                    {
                        setDefaultView(getViewContext(), dsid, defaultView);
                    }
                    else
                    {
                        defaultView = getDefaultView(getViewContext(), def.getDataSetId());
                        if (!StringUtils.isEmpty(defaultView))
                        {
                            boolean defaultExists = false;
                            for (Pair<String, String> view : views)
                            {
                                if (StringUtils.equals(view.getValue(), defaultView))
                                {
                                    defaultExists = true;
                                    break;
                                }
                            }
                            if (!defaultExists)
                                setDefaultView(getViewContext(), dsid, "");
                        }
                    }

                    ViewPrefsBean bean = new ViewPrefsBean(views, def);
                    return new StudyJspView<ViewPrefsBean>(study, "viewPreferences.jsp", bean, errors);
                }
            }
            HttpView.throwNotFound("Invalid dataset ID");
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic(new HelpTopic("Set Default View", HelpTopic.Area.STUDY));

            root.addChild(study.getLabel(), new ActionURL(BeginAction.class, getContainer()));

            ActionURL datasetURL = getViewContext().getActionURL().clone();
            datasetURL.setAction(DatasetAction.class);

            String label = def.getLabel() != null ? def.getLabel() : "" + def.getDataSetId();
            root.addChild(new NavTree(label, datasetURL.getLocalURIString()));
            
            root.addChild(new NavTree("View Preferences"));
            return root;
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class ImportStudyBatchAction extends SimpleViewAction<PipelineForm>
    {
        String path;

        public ModelAndView getView(PipelineForm form, BindException errors) throws Exception
        {
            Container c = getContainer();
            path = form.getPath();
            File definitionFile = null;

            if (path != null)
            {
                PipeRoot root = PipelineService.get().findPipelineRoot(c);
                if (root != null)
                    definitionFile = root.resolvePath(path);
            }

            if (path == null || null == definitionFile || !definitionFile.exists() || !definitionFile.isFile())
            {
                HttpView.throwNotFound();
                return null;
            }

            File lockFile = StudyPipeline.lockForDataset(getStudy(), definitionFile);

            if (!definitionFile.canRead())
                errors.reject("importStudyBatch", "Can't read dataset file: " + path);
            if (lockFile.exists())
                errors.reject("importStudyBatch", "Lock file exists.  Delete file before running import. " + lockFile.getName());

            DatasetBatch batch = new DatasetBatch(
                    new ViewBackgroundInfo(getContainer(), getUser(), getViewContext().getActionURL()), definitionFile);
            if (!errors.hasErrors())
            {
                List<String> parseErrors = new ArrayList<String>();
                batch.prepareImport(parseErrors);
                for (String error : parseErrors)
                    errors.reject("importStudyBatch", error);
            }

            return new StudyJspView<ImportStudyBatchBean>(
                    getStudy(), "importStudyBatch.jsp", new ImportStudyBatchBean(batch, form), errors);

        }

        public NavTree appendNavTrail(NavTree root)
        {
            try
            {
                root.addChild(getStudy().getLabel(), new ActionURL(StudyController.BeginAction.class, getContainer()));
                root.addChild("Import Study Batch - " + path);
                return root;
            }
            catch (ServletException se)
            {
                throw UnexpectedException.wrap(se);
            }
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class SubmitStudyBatchAction extends SimpleRedirectAction<PipelineForm>
    {
        public ActionURL getRedirectURL(PipelineForm form) throws Exception
        {
            Study study = getStudy();
            Container c = getContainer();
            String path = form.getPath();
            File f = null;

            if (path != null)
            {
                PipeRoot root = PipelineService.get().findPipelineRoot(c);
                if (root != null)
                    f = root.resolvePath(path);
            }

            if (null == f || !f.exists() || !f.isFile())
            {
                HttpView.throwNotFound();
                return null;
            }

            File lockFile = StudyPipeline.lockForDataset(study, f);
            if (!f.canRead() || lockFile.exists())
            {
                // Something has changed since the user first viewed this form.
                // Send them back to validate
                ActionURL importURL = new ActionURL(ImportStudyBatchAction.class, getContainer());
                importURL.addParameter("path", form.getPath());
                return importURL;
            }

            DatasetBatch batch = new DatasetBatch(new ViewBackgroundInfo(
                    getContainer(), getUser(), getViewContext().getActionURL()), f);
            batch.submit();

            return PageFlowUtil.urlProvider(PipelineStatusUrls.class).urlBegin(c);
        }
    }

    @RequiresPermission(ACL.PERM_DELETE)
    public class PurgeDatasetAction extends SimpleRedirectAction
    {
        public ActionURL getRedirectURL(Object o) throws Exception
        {
            ViewContext context = getViewContext();
            int datasetId = null == context.get(DataSetDefinition.DATASETKEY) ? 0 : Integer.parseInt((String) context.get(DataSetDefinition.DATASETKEY));

            if ("POST".equalsIgnoreCase(getViewContext().getRequest().getMethod()))
            {
                DataSetDefinition dataset = StudyManager.getInstance().getDataSetDefinition(getStudy(), datasetId);
                if (null == dataset)
                {
                    HttpView.throwNotFound();
                    return null;
                }

                String typeURI = dataset.getTypeURI();
                if (typeURI == null)
                {
                    return new ActionURL(TypeNotFoundAction.class, getContainer());
                }

                DbScope scope = StudySchema.getInstance().getSchema().getScope();
                try
                {
                    scope.beginTransaction();
                    int numRowsDeleted = StudyManager.getInstance().purgeDataset(getStudy(), dataset);
                    scope.commitTransaction();

                    // Log the purge
                    String comment = "Dataset purged. " + numRowsDeleted + " rows deleted";
                    StudyServiceImpl.addDatasetAuditEvent(getUser(), getContainer(), dataset, comment, null);
                }
                finally
                {
                    if (scope.isTransactionActive())
                        scope.rollbackTransaction();
                }
                DataRegionSelection.clearAll(getViewContext());
            }
            ActionURL datasetURL = new ActionURL(DatasetAction.class, getContainer());
            datasetURL.addParameter(DataSetDefinition.DATASETKEY, datasetId);
            return datasetURL;
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class UpdateParticipantVisitsAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            StudyManager.getInstance().recomputeStudyDataVisitDate(getStudy());
            StudyManager.getInstance().getVisitManager(getStudy()).updateParticipantVisits();

            TableInfo tinfoParticipantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();
            Integer visitDates = Table.executeSingleton(StudySchema.getInstance().getSchema(),
                    "SELECT Count(VisitDate) FROM " + tinfoParticipantVisit + "\nWHERE Container = ?",
                    new Object[] {getContainer()}, Integer.class);
            int count = null == visitDates ? 0 : visitDates.intValue();

            HttpView view = new HtmlView(
                    "<div class=normal>" + count + " rows were updated.<p/>" +
                    PageFlowUtil.buttonLink("Done", "manageVisits.view") +
                    "</div>");
            return new DialogTemplate(view);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class VisitDisplayOrderAction extends FormViewAction<ReorderForm>
    {

        public ModelAndView getView(ReorderForm reorderForm, boolean reshow, BindException errors) throws Exception
        {
            return new StudyJspView<Object>(getStudy(), "visitDisplayOrder.jsp", reorderForm, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendManageStudy(root);
            root.addChild("Manage Visits", new ActionURL(ManageVisitsAction.class, getContainer()));
            root.addChild("Display Order");
            return root;
        }

        public void validateCommand(ReorderForm target, Errors errors) {}

        public boolean handlePost(ReorderForm form, BindException errors) throws Exception
        {
            String order = form.getOrder();
            if (order != null && order.length() > 0)
            {
                String[] orderedIds = order.split(",");
                for (int i = 0; i < orderedIds.length; i++)
                {
                    int id = Integer.parseInt(orderedIds[i]);
                    Visit visit = StudyManager.getInstance().getVisitForRowId(getStudy(), id);
                    if (visit.getDisplayOrder() != i)
                    {
                        visit = visit.createMutable();
                        visit.setDisplayOrder(i);
                        StudyManager.getInstance().updateVisit(getUser(), visit);
                    }
                }
            }
            return true;
        }

        public ActionURL getSuccessURL(ReorderForm reorderForm)
        {
            return new ActionURL(ManageVisitsAction.class, getContainer());
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class VisitVisibilityAction extends FormViewAction<VisitPropertyForm>
    {
        public ModelAndView getView(VisitPropertyForm visitPropertyForm, boolean reshow, BindException errors) throws Exception
        {
            return new StudyJspView<Object>(getStudy(), "visitVisibility.jsp", visitPropertyForm, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendManageStudy(root);
            root.addChild("Manage Visits", new ActionURL(ManageVisitsAction.class, getContainer()));
            root.addChild("Properties");
            return root;
        }

        public void validateCommand(VisitPropertyForm target, Errors errors) {}

        public boolean handlePost(VisitPropertyForm form, BindException errors) throws Exception
        {
            int[] allIds = form.getIds() == null ? new int[0] : form.getIds();
            int[] visibleIds = form.getVisible() == null ? new int[0] : form.getVisible();
            Set<Integer> visible = new HashSet<Integer>(visibleIds.length);
            for (int id : visibleIds)
                visible.add(id);
            if (allIds.length != form.getLabel().length)
                throw new IllegalStateException("Arrays must be the same length.");
            for (int i = 0; i < allIds.length; i++)
            {
                Visit def = StudyManager.getInstance().getVisitForRowId(getStudy(), allIds[i]);
                boolean show = visible.contains(allIds[i]);
                String label = form.getLabel()[i];
                String typeStr = form.getExtraData()[i];
                Integer cohortId = form.getCohort()[i];
                if (cohortId.intValue() == -1)
                    cohortId = null;
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

        public ActionURL getSuccessURL(VisitPropertyForm visitPropertyForm)
        {
            return new ActionURL(ManageVisitsAction.class, getContainer());
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class DataSetVisibilityAction extends FormViewAction<DatasetPropertyForm>
    {
        public ModelAndView getView(DatasetPropertyForm form, boolean reshow, BindException errors) throws Exception
        {
            return new StudyJspView<Object>(getStudy(), "dataSetVisibility.jsp", form, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendManageStudy(root);
            root.addChild("Manage Datasets", new ActionURL(ManageTypesAction.class, getContainer()));
            root.addChild("Properties");
            return root;
        }

        public void validateCommand(DatasetPropertyForm target, Errors errors) {}

        public boolean handlePost(DatasetPropertyForm form, BindException errors) throws Exception
        {
            int[] allIds = form.getIds();
            int[] visibleIds = form.getVisible();
            Set<Integer> visible = new HashSet<Integer>(visibleIds.length);
            for (int id : visibleIds)
                  visible.add(id);
            for (int i = 0; i < allIds.length; i++)
            {
                DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(getStudy(), allIds[i]);
                boolean show = visible.contains(allIds[i]);
                String category = form.getExtraData()[i];
                Integer cohortId = null;
                if (form.getCohort() != null)
                    cohortId = form.getCohort()[i];
                if (cohortId != null && cohortId.intValue() == -1)
                    cohortId = null;
                String label = form.getLabel()[i];
                if (def.isShowByDefault() != show || !nullSafeEqual(category, def.getCategory()) || !nullSafeEqual(label, def.getLabel()) || !BaseStudyController.nullSafeEqual(cohortId, def.getCohortId()))
                {
                    def = def.createMutable();
                    def.setShowByDefault(show);
                    def.setCategory(category);
                    def.setCohortId(cohortId);
                    def.setLabel(label);
                    StudyManager.getInstance().updateDataSetDefinition(getUser(), def);
                }
            }
            return true;
        }

        public ActionURL getSuccessURL(DatasetPropertyForm form)
        {
            return new ActionURL(ManageTypesAction.class, getContainer());
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class DataSetDisplayOrderAction extends FormViewAction<ReorderForm>
    {
        public ModelAndView getView(ReorderForm form, boolean reshow, BindException errors) throws Exception
        {
            return new StudyJspView<Object>(getStudy(), "dataSetDisplayOrder.jsp", form, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendManageStudy(root);
            root.addChild("Manage Datasets", new ActionURL(ManageTypesAction.class, getContainer()));
            root.addChild("Display Order");
            return root;
        }

        public void validateCommand(ReorderForm target, Errors errors) {}

        public boolean handlePost(ReorderForm form, BindException errors) throws Exception
        {
            String order = form.getOrder();
            if (order != null && order.length() > 0)
            {
                String[] orderedIds = order.split(",");
                for (int i = 0; i < orderedIds.length; i++)
                {
                    int id = Integer.parseInt(orderedIds[i]);
                    DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(getStudy(), id);
                    if (def.getDisplayOrder() != i)
                    {
                        def = def.createMutable();
                        def.setDisplayOrder(i);
                        StudyManager.getInstance().updateDataSetDefinition(getUser(), def);
                    }
                }
            }
            return true;
        }

        public ActionURL getSuccessURL(ReorderForm visitPropertyForm)
        {
            return new ActionURL(ManageTypesAction.class, getContainer());
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class DeleteDatasetAction extends SimpleViewAction<IdForm>
    {
        public ModelAndView getView(IdForm form, BindException errors) throws Exception
        {
            DataSetDefinition ds = StudyManager.getInstance().getDataSetDefinition(getStudy(), form.getId());
            if (null == ds)
                redirectTypeNotFound(form.getId());

            DbScope scope = StudySchema.getInstance().getSchema().getScope();
            try
            {
                scope.beginTransaction();
                StudyManager.getInstance().deleteDataset(getStudy(), getUser(), ds);
                scope.commitTransaction();
                HttpView.throwRedirect(new ActionURL(ManageTypesAction.class, getContainer()));
                return null;
            }
            finally
            {
                if (scope.isTransactionActive())
                    scope.rollbackTransaction();
            }
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class SnapshotAction extends SimpleViewAction<SnapshotForm>
    {
        public ModelAndView getView(SnapshotForm form, BindException errors) throws Exception
        {
            StudyManager.SnapshotBean snapshotBean;
            try
            {
                snapshotBean = StudyManager.getInstance().getSnapshotInfo(HttpView.currentContext().getUser(), HttpView.currentContext().getContainer());
            }
            catch (ServletException e)
            {
                throw new RuntimeException(e);
            }
            if (!isPost())
            {
                // First time through, just set the bean and display the form
                int tableCount = 0;
                for (String category : snapshotBean.getCategories())
                    tableCount += snapshotBean.getSourceNames(category).size();

                form.setCategory(new String[tableCount]);
                form.setDestName(new String[tableCount]);
                form.setSourceName(new String[tableCount]);
                StudySnapshotBean studySnapshotBean = new StudySnapshotBean(snapshotBean, form);
                return new StudyJspView<StudySnapshotBean>(getStudy(), "snapshotData.jsp", studySnapshotBean, errors);
            }
            // Update the bean with the form entries
            snapshotBean.setSchemaName(form.getSchemaName());

            String[] category = form.getCategory();
            String[] source = form.getSourceName();
            boolean[] snapshot = form.getSnapshot();
            String[] destName = form.getDestName();
            for (int i = 0; i < category.length; i++)
            {
                snapshotBean.setSnapshot(category[i], source[i], snapshot[i]);
                snapshotBean.setDestTableName(category[i], source[i], destName[i]);
            }

            // validate, and then process
            String schemaName = StringUtils.trimToNull(form.getSchemaName());
            if (null == schemaName)
            {
                errors.reject("manageSnapshot", "You must supply a schema name.");
            }
            else if (!AliasManager.isLegalName(schemaName))
            {
                errors.reject("manageSnapshot", "Schema name must be a legal database identifier");
            }
            else
            {
                boolean badName = false;
                for (Module module : ModuleLoader.getInstance().getModules())
                {
                    for (String schema : module.getSchemaNames())
                    {
                        if (schemaName.equalsIgnoreCase(schema))
                        {
                            errors.reject("manageSnapshot", "The schema name " + schema + " is already in use by the " + module.getName() + " module. Please pick a new name");
                            badName = true;
                            break;
                        }
                    }
                    if (badName)
                    {
                        break;
                    }
                }
                if (schemaName.equalsIgnoreCase("temp"))
                {
                    errors.reject("manageSnapshot", "'Temp' is a reserved schema name. Please choose a new name");
                }
            }

            CaseInsensitiveHashSet names = new CaseInsensitiveHashSet();
            for (String categoryName : snapshotBean.getCategories())
            {
                for (String sourceName : snapshotBean.getSourceNames(categoryName))
                {
                    String destTableName = snapshotBean.getDestTableName(categoryName, sourceName);
                    if (!AliasManager.isLegalName(destTableName))
                        errors.reject("manageSnapshot", "Not a legal table name: " + destTableName);
                    if (snapshotBean.isSaveTable(categoryName, sourceName) && !names.add(destTableName))
                        errors.reject("manageSnapshot", "Duplicate table name: " + destTableName);
                }
            }
            if (errors.hasErrors())
            {
                return new StudyJspView<StudySnapshotBean>(getStudy(), "snapshotData.jsp",
                        new StudySnapshotBean(snapshotBean, form), errors);
            }
            // Actually process
            StudyManager.getInstance().createSnapshot(getUser(), snapshotBean);
            form.setComplete(true);

            return new StudyJspView<StudySnapshotBean>(getStudy(), "snapshotData.jsp",
                        new StudySnapshotBean(snapshotBean, form), errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendManageStudy(root);
            root.addChild("Manage Snapshot");
            return root;
        }
    }

    private static final String DEFAULT_PARTICIPANT_VIEW_SOURCE =
            "<script type=\"text/javascript\">\n" +
            "   /* Include all headers necessary for client API usage: */\n" +
            "   LABKEY.requiresClientAPI();\n" +
            "</script>\n" +
            "\n" +
            "<div id=\"participantData\">Loading...</div>\n" +
            "\n" +
            "<script type=\"text/javascript\">\n" +
            "    /* get the participant id from the request URL: this parameter is required. */\n" +
            "    var participantId = LABKEY.ActionURL.getParameter('participantId');\n" +
            "    /* get the dataset id from the request URL: this is used to remember expand/collapse\n" +
            "       state per-dataset.  This parameter is optional; we use -1 if it isn't provided. */\n" +
            "    var datasetId = LABKEY.ActionURL.getParameter('datasetId');\n" +
            "    if (!datasetId)\n" +
            "        datasetId = -1;\n" +
            "\n" +
            "    /* create the participant details webpart: */\n" +
            "    var participantWebPart = new LABKEY.WebPart({\n" +
            "    partName: 'Participant Details',\n" +
            "    renderTo: 'participantData',\n" +
            "    frame : 'false',\n" +
            "    partConfig: {\n" +
            "        participantId: participantId,\n" +
            "        datasetId: datasetId,\n" +
            "        currentUrl: '' + window.location\n" +
            "        }\n" +
            "    });\n" +
            "\n" +
            "    /* place the webpart into the 'participantData' div: */\n" +
            "    participantWebPart.render();\n" +
            "</script>";
    
    public static class CustomizeParticipantViewForm
    {
        private String _returnUrl;
        private String _customScript;
        private String _participantId;
        private boolean _useCustomView;
        private boolean _reshow;

        public String getReturnUrl()
        {
            return _returnUrl;
        }

        public void setReturnUrl(String returnUrl)
        {
            _returnUrl = returnUrl;
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

    @RequiresPermission(ACL.PERM_ADMIN)
    public class CustomizeParticipantViewAction extends FormViewAction<CustomizeParticipantViewForm>
    {
        public void validateCommand(CustomizeParticipantViewForm target, Errors errors)
        {
        }

        public ModelAndView getView(CustomizeParticipantViewForm form, boolean reshow, BindException errors) throws Exception
        {
            Study study = getStudy();
            CustomParticipantView view = StudyManager.getInstance().getCustomParticipantView(study);
            if (view != null)
            {
                form.setCustomScript(view.getBody());
                form.setUseCustomView(view.isActive());
            }

            return new JspView<CustomizeParticipantViewForm>("/org/labkey/study/view/customizeParticipantView.jsp", form);
        }

        public boolean handlePost(CustomizeParticipantViewForm form, BindException errors) throws Exception
        {
            Study study = getStudy();
            CustomParticipantView view = StudyManager.getInstance().getCustomParticipantView(study);
            if (view == null)
                view = new CustomParticipantView();
            view.setBody(form.getCustomScript());
            view.setActive(form.isUseCustomView());
            view = StudyManager.getInstance().saveCustomParticipantView(study, getUser(), view);
            return view != null;
        }

        public ActionURL getSuccessURL(CustomizeParticipantViewForm form)
        {
            if (form.isReshow())
            {
                ActionURL reshowURL = new ActionURL(CustomizeParticipantViewAction.class, getContainer());
                if (form.getParticipantId() != null && form.getParticipantId().length() > 0)
                    reshowURL.addParameter("participantId", form.getParticipantId());
                if (form.getReturnUrl() != null && form.getReturnUrl().length() > 0)
                    reshowURL.addParameter("returnUrl", form.getReturnUrl());
                return reshowURL;
            }
            else if (form.getReturnUrl() != null && form.getReturnUrl().length() > 0)
                return new ActionURL(form.getReturnUrl());
            else
                return new ActionURL(ReportsController.ManageReportsAction.class, getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendManageStudy(root);
            root.addChild("Manage Reports and Views", new ActionURL(ReportsController.ManageReportsAction.class, getContainer()));
            return root.addChild("Customize Participant View");
        }
    }

    public static class StudySnapshotBean
    {
        private final StudyManager.SnapshotBean bean;
        private final SnapshotForm form;

        public StudySnapshotBean(StudyManager.SnapshotBean bean, SnapshotForm form)
        {
            this.bean = bean;
            this.form = form;
        }

        public StudyManager.SnapshotBean getBean() {return bean;}
        public SnapshotForm getForm() {return form;}   
    }

    public static class SnapshotForm
    {
        private String schemaName;
        private boolean complete;
        private String[] sourceName;
        private String[] destName;
        private String[] category;
        private boolean[] snapshot;
        
        public String getSchemaName()
        {
            return schemaName;
        }

        public void setSchemaName(String schemaName)
        {
            this.schemaName = schemaName;
        }

        public String[] getSourceName()
        {
            return sourceName;
        }

        public void setSourceName(String[] sourceName)
        {
            this.sourceName = sourceName;
        }

        public String[] getDestName()
        {
            return destName;
        }

        public void setDestName(String[] destName)
        {
            this.destName = destName;
        }

        public String[] getCategory()
        {
            return category;
        }

        public void setCategory(String[] category)
        {
            this.category = category;
        }

        public boolean isComplete()
        {
            return complete;
        }

        public void setComplete(boolean complete)
        {
            this.complete = complete;
        }

        public void setSnapshot(boolean[] snapshot)
        {
            this.snapshot = snapshot;
        }

        public boolean[] getSnapshot()
        {
            return snapshot;
        }
    }

    public static class DatasetPropertyForm extends PropertyForm
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

    public static class RequirePipelineView extends StudyJspView<Boolean>
    {
        public RequirePipelineView(Study study, boolean showGoBack, BindException errors)
        {
            super(study, "requirePipeline.jsp", showGoBack, errors);
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


    public static class ReorderForm
    {
        private String order;

        public String getOrder() {return order;}

        public void setOrder(String order) {this.order = order;}
    }

    public static class ImportStudyBatchBean
    {
        private final DatasetBatch batch;
        private final PipelineForm form;

        public ImportStudyBatchBean(DatasetBatch batch, PipelineForm form)
        {
            this.batch = batch;
            this.form = form;
        }

        public DatasetBatch getBatch()
        {
            return batch;
        }

        public PipelineForm getForm()
        {
            return form;
        }
    }

    public static class PipelineForm
    {
        private String _path;

        public String getPath() {return _path;}

        public void setPath(String path) {this._path = path;}
    }

    public static class ViewPrefsBean
    {
        private List<Pair<String, String>> _views;
        private DataSetDefinition _def;

        public ViewPrefsBean(List<Pair<String, String>> views, DataSetDefinition def)
        {
            _views = views;
            _def = def;
        }

        public List<Pair<String, String>> getViews(){return _views;}
        public DataSetDefinition getDataSetDefinition(){return _def;}
    }


    private static final String DEFAULT_DATASET_VIEW = "Study.defaultDatasetView";

    public static String getDefaultView(ViewContext context, int datasetId)
    {
        Map<String, String> viewMap = PropertyManager.getProperties(context.getUser().getUserId(),
                context.getContainer().getId(), DEFAULT_DATASET_VIEW, false);

        final String key = Integer.toString(datasetId);
        if (viewMap != null && viewMap.containsKey(key))
        {
            return viewMap.get(key);
        }
        return "";
    }

    private void setDefaultView(ViewContext context, int datasetId, String view)
    {
        Map<String, String> viewMap = PropertyManager.getWritableProperties(context.getUser().getUserId(),
                context.getContainer().getId(), DEFAULT_DATASET_VIEW, true);

        viewMap.put(Integer.toString(datasetId), view);
        PropertyManager.saveProperties(viewMap);
    }

    private String getVisitLabel()
    {
        try
        {
            return StudyManager.getInstance().getVisitManager(getStudy()).getLabel();
        }
        catch (ServletException e)
        {
            return "Visit";
        }
    }


    private String getVisitLabelPlural()
    {
        try
        {
            return StudyManager.getInstance().getVisitManager(getStudy()).getPluralLabel();
        }
        catch (ServletException e)
        {
            return "Visits";
        }
    }

    private String getVisitJsp(String prefix) throws ServletException
    {
        return prefix + (getStudy().isDateBased() ? "Timepoint" : "Visit") + ".jsp";
    }

    public static class ParticipantForm implements StudyManager.ParticipantViewConfig
    {
        private String participantId;
        private int datasetId;
        private double sequenceNum;
        private String action;
        private int reportId;
        private Integer cohortId;
        private String _redirectUrl;

        public String getParticipantId(){return participantId;}

        public void setParticipantId(String participantId){this.participantId = participantId;}

        public int getDatasetId(){return datasetId;}
        public void setDatasetId(int datasetId){this.datasetId = datasetId;}

        public double getSequenceNum(){return sequenceNum;}
        public void setSequenceNum(double sequenceNum){this.sequenceNum = sequenceNum;}

        public String getAction(){return action;}
        public void setAction(String action){this.action = action;}

        public int getReportId(){return reportId;}
        public void setReportId(int reportId){this.reportId = reportId;}

        public Integer getCohortId(){return cohortId;}

        public void setCohortId(Integer cohortId){this.cohortId = cohortId;}

        public String getRedirectUrl() { return _redirectUrl; }

        public void setRedirectUrl(String redirectUrl) { _redirectUrl = redirectUrl; }
    }


    public static class StudyPropertiesForm
    {
        private String _label;
        private boolean _dateBased;
        private Date _startDate;
        private boolean _simpleRepository = true;

        public String getLabel()
        {
            return _label;
        }

        public void setLabel(String label)
        {
            _label = label;
        }

        public boolean isDateBased()
        {
            return _dateBased;
        }

        public void setDateBased(boolean dateBased)
        {
            _dateBased = dateBased;
        }

        public Date getStartDate()
        {
            return _startDate;
        }

        public void setStartDate(Date startDate)
        {
            _startDate = startDate;
        }


        public boolean isSimpleRepository()
        {
            return _simpleRepository;
        }

        public void setSimpleRepository(boolean simpleRepository)
        {
            _simpleRepository = simpleRepository;
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
        private Report _report;

        public ReportHeader(Report report)
        {
            _report = report;
        }

        protected void renderInternal(Object model, PrintWriter out) throws Exception
        {
            if (!StringUtils.isEmpty(_report.getDescriptor().getReportDescription()))
            {
                out.print("<table class='normal'>");
                out.print("<tr><td><span class='navPageHeader'>Report Description:</span>&nbsp;</td>");
                out.print("<td>" + _report.getDescriptor().getReportDescription() + "</td></tr>");
                out.print("</table>");
            }
        }
    }

    public static class StudyChartReport extends ChartQueryReport
    {
        public static final String TYPE = "Study.chartReport";

        public String getType()
        {
            return TYPE;
        }

        private TableInfo getTable(ViewContext context, ReportDescriptor descriptor) throws Exception
        {
            final int datasetId = Integer.parseInt(descriptor.getProperty("datasetId"));
            final Study study = StudyManager.getInstance().getStudy(context.getContainer());
            DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, datasetId);

            return def.getTableInfo(context.getUser());
        }

        public ResultSet generateResultSet(ViewContext context) throws Exception
        {
            ReportDescriptor descriptor = getDescriptor();
            final String participantId = descriptor.getProperty("participantId");
            final TableInfo tableInfo = getTable(context, descriptor);
            DataRegion dr = new DataRegion();
            dr.setTable(tableInfo);

            SimpleFilter filter = new SimpleFilter();
            filter.addCondition("participantId", participantId, CompareType.EQUAL);

            RenderContext ctx = new RenderContext(context);
            ctx.setContainer(context.getContainer());
            ctx.setBaseFilter(filter);

            return dr.getResultSet(ctx);
        }

        public ChartReportDescriptor.LegendItemLabelGenerator getLegendItemLabelGenerator()
        {
            return new ChartReportDescriptor.LegendItemLabelGenerator() {
                public String generateLabel(ViewContext context, ReportDescriptor descriptor, String itemName) throws Exception
                {
                    TableInfo table = getTable(context, descriptor);
                    if (table != null)
                    {
                        ColumnInfo info = table.getColumn(itemName);
                        return info != null ? info.getCaption() : itemName;
                    }
                    return itemName;
                }
            };
        }
    }

    /**
     * Adds next and prev buttons to the participant view
     */
    public static class ParticipantNavView extends HttpView
    {
        private String _prevURL;
        private String _nextURL;
        private String _display;
        private String _currentParticipantId;

        public ParticipantNavView(String prevURL, String nextURL, String currentPartitipantId, String display)
        {
            _prevURL = prevURL;
            _nextURL = nextURL;
            _display = display;
            _currentParticipantId = currentPartitipantId;
        }

        public ParticipantNavView(String prevURL, String nextURL, String currentPartitipantId)
        {
            this(prevURL, nextURL, currentPartitipantId,  null);
        }

        @Override
        protected void renderInternal(Object model, PrintWriter out) throws Exception
        {
            out.print("<table><tr><td align=\"left\">");
            if (_prevURL == null)
                out.print("[< Previous Participant]");
            else
                out.print("[<a href=\"" + _prevURL + "\">< Previous Participant</a>]");
            out.print("&nbsp;");

            if (_nextURL == null)
                out.print("[Next Participant >]");
            else
                out.print("[<a href=\"" + _nextURL + "\">Next Participant ></a>]");

            Container container = getViewContext().getContainer();
            if (container.hasPermission(getViewContext().getUser(), ACL.PERM_ADMIN))
            {
                ActionURL customizeURL = new ActionURL(CustomizeParticipantViewAction.class, container);
                customizeURL.addParameter("returnUrl", getViewContext().getActionURL().getLocalURIString());
                customizeURL.addParameter("participantId", _currentParticipantId);
                out.print("</td><td>");
                out.print(PageFlowUtil.textLink("Customize View", customizeURL));
            }

            if (_display != null)
            {
                out.print("</td><td class=\"ms-searchform\">");
                out.print(PageFlowUtil.filter(_display));
            }
            out.print("</td></tr></table>");
        }
    }

    public static class ImportDataSetForm
    {
        private int datasetId = 0;
        private String typeURI;
        private String tsv;
        private String keys;


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
    }

    public static class DataSetForm extends ViewForm
    {
        private String _name;
        private String _label;
        private int _datasetId;
        private String _category;
        private boolean _showByDefault;
        private String _visitDatePropertyName;
        private String[] _visitStatus;
        private int[] _visitRowIds;
        private String _description;
        private Integer _cohortId;
        private boolean _demographicData;
        private boolean _create;

        public ActionErrors validate(ActionMapping actionMapping, HttpServletRequest httpServletRequest)
        {
            if (_datasetId < 1)
                addActionError("DatasetId must be greater than zero.");
            if (null == StringUtils.trimToNull(_label))
                addActionError("Label is required.");
            return getActionErrors();
        }


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
         * @param dataSetIdStr
         */
        public void setDatasetIdStr(String dataSetIdStr)
        {
            try
            {
                if (null == StringUtils.trimToNull(dataSetIdStr))
                    _datasetId = 0;
                else
                    _datasetId = Integer.parseInt(dataSetIdStr);
            }
            catch (Exception x)
            {
                _datasetId = 0;
            }
        }

        public int getDatasetId()
        {
            return _datasetId;
        }

        public void setDatasetId(int datasetId)
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

        public void setVisitDatePropertyName(String _visitDatePropertyName)
        {
            this._visitDatePropertyName = _visitDatePropertyName;
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
}
