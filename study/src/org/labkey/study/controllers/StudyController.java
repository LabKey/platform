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

package org.labkey.study.controllers;

import org.apache.commons.collections15.MultiMap;
import org.apache.commons.collections15.multimap.MultiHashMap;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.commons.lang.time.FastDateFormat;
import org.apache.log4j.Logger;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionMapping;
import org.apache.xmlbeans.XmlException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.*;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.*;
import org.labkey.api.exp.*;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusUrls;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.*;
import org.labkey.api.query.snapshot.QuerySnapshotDefinition;
import org.labkey.api.query.snapshot.QuerySnapshotForm;
import org.labkey.api.query.snapshot.QuerySnapshotService;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.*;
import org.labkey.api.security.*;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.Visit;
import org.labkey.api.study.assay.AssayPublishService;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.util.*;
import static org.labkey.api.util.PageFlowUtil.filter;
import org.labkey.api.view.*;
import org.labkey.api.view.template.AppBar;
import org.labkey.api.view.template.DialogTemplate;
import org.labkey.api.view.template.PageConfig;
import org.labkey.study.*;
import org.labkey.study.assay.AssayPublishManager;
import org.labkey.study.assay.query.AssayAuditViewFactory;
import org.labkey.study.controllers.reports.ReportsController;
import org.labkey.study.controllers.samples.SpringSpecimenController;
import org.labkey.study.dataset.DatasetSnapshotProvider;
import org.labkey.study.dataset.client.Designer;
import org.labkey.study.importer.*;
import org.labkey.study.importer.StudyReload.*;
import org.labkey.study.model.*;
import org.labkey.study.pipeline.DatasetBatch;
import org.labkey.study.pipeline.StudyPipeline;
import org.labkey.study.query.DataSetQueryView;
import org.labkey.study.query.PublishedRecordQueryView;
import org.labkey.study.query.StudyPropertiesQueryView;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.study.reports.ReportManager;
import org.labkey.study.reports.StudyReportUIProvider;
import org.labkey.study.samples.settings.RepositorySettings;
import org.labkey.study.security.permissions.ManageStudyPermission;
import org.labkey.study.visitmanager.VisitManager;
import org.labkey.study.writer.ExportContext;
import org.labkey.study.writer.FileSystemFile;
import org.labkey.study.writer.StudyWriter;
import org.labkey.study.writer.ZipFile;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;
import org.xml.sax.SAXException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.zip.ZipException;

/**
 * User: Karl Lum
 * Date: Nov 28, 2007
 */
public class StudyController extends BaseStudyController
{
    private static final Logger _log = Logger.getLogger(StudyController.class);

    private static final ActionResolver ACTION_RESOLVER = new DefaultActionResolver(StudyController.class);
    private static final String PARTICIPANT_CACHE_PREFIX = "Study_participants/participantCache";
    private static final String EXPAND_CONTAINERS_KEY = StudyController.class.getName() + "/expandedContainers";

    public StudyController()
    {
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
            WebPartView right = StudyModule.reportsPartFactory.getWebPartView(getViewContext(), null);
			return new SimpleTemplate(overview,right);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild(_study == null ? "No Study In Folder" : _study.getLabel());
        }
    }


	class SimpleTemplate extends HttpView
	{
		SimpleTemplate(HttpView body, HttpView right)
		{
			setBody(body);
			setView("right",right);
		}
		
		@Override
		protected void renderInternal(Object model, PrintWriter out) throws Exception
		{
			out.print("<table width=100%><tr><td align=left valign=top class=labkey-body-panel><img height=1 width=400 src=\""+getViewContext().getContextPath()+"\"/_.gif\"><br>");
			include(getBody());
			out.print("</td><td align=left valign=top class=labkey-side-panel><img height=1 width=240 src=\""+getViewContext().getContextPath()+"/_.gif\"><br>");
			include(getView("right"));
			out.print("</td></tr></table>");
		}
	}
	

    @RequiresPermission(ACL.PERM_ADMIN)
    public class DefineDatasetTypeAction extends FormViewAction<ImportTypeForm>
    {
        private DataSet _def;
        public ModelAndView getView(ImportTypeForm form, boolean reshow, BindException errors) throws Exception
        {
            return new StudyJspView<ImportTypeForm>(getStudy(false), "importDataType.jsp", form, errors);
        }

        public void validateCommand(ImportTypeForm form, Errors errors)
        {

            if (null == form.getDataSetId() && !form.isAutoDatasetId())
                errors.reject("defineDatasetType", "You must supply an integer Dataset Id");
            if (null != form.getDataSetId())
            {
                DataSet dsd = StudyManager.getInstance().getDataSetDefinition(StudyManager.getInstance().getStudy(getContainer()), form.getDataSetId().intValue());
                if (null != dsd)
                    errors.reject("defineDatasetType", "There is already a dataset with id " + form.getDataSetId());
            }
            if (null == StringUtils.trimToNull(form.getTypeName()))
                errors.reject("defineDatasetType", "Dataset must have a name.");
            else
            {
                String typeURI = AssayPublishManager.getInstance().getDomainURIString(StudyManager.getInstance().getStudy(getContainer()), form.getTypeName());
                DomainDescriptor dd = OntologyManager.getDomainDescriptor(typeURI, getContainer());
                if (null != dd)
                {
                    errors.reject("defineDatasetType", "There is a dataset named " + form.getTypeName() + " already defined in this folder.");
                }
                else
                {
                    // Check if a query or table exists with the same name
                    StudyImpl study = StudyManager.getInstance().getStudy(getContainer());
                    StudyQuerySchema studySchema = new StudyQuerySchema(study, getUser(), true);
                    if (studySchema.getTableNames().contains(form.getTypeName()) ||
                        QueryService.get().getQueryDef(getContainer(), "study", form.getTypeName()) != null)
                    {
                        errors.reject("defineDatasetType", "There is a query named " + form.getTypeName() + " already defined in this folder.");
                    }
                }
            }

        }

        public boolean handlePost(ImportTypeForm form, BindException derrors) throws Exception
        {
            Integer datasetId = form.getDataSetId();

            if (form.autoDatasetId)
                _def = AssayPublishManager.getInstance().createAssayDataset(getUser(), getStudy(), form.getTypeName(), null, null, false, null);
            else
                _def = AssayPublishManager.getInstance().createAssayDataset(getUser(), getStudy(), form.getTypeName(), null, datasetId, false, null);


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
            if (!form.isFileImport())
            {
                return new ActionURL(EditTypeAction.class, getContainer()).
                    addParameter(DataSetDefinition.DATASETKEY, _def.getDataSetId());
            }
            else
            {
                return new ActionURL(DatasetController.DefineAndImportDatasetAction.class, getContainer()).
                    addParameter(DataSetDefinition.DATASETKEY, _def.getDataSetId());
            }
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendNavTrailDatasetAdmin(root);
            return root.addChild("Define Dataset");
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    @SuppressWarnings("unchecked")
    public class EditTypeAction extends SimpleViewAction<DataSetForm>
    {
        private DataSet _def;
        public ModelAndView getView(DataSetForm form, BindException errors) throws Exception
        {
            StudyImpl study = getStudy();
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
                String domainURI = StudyManager.getInstance().getDomainURI(study.getContainer(), def);
                OntologyManager.ensureDomainDescriptor(domainURI, def.getName(), study.getContainer());
                def.setTypeURI(domainURI);
            }
            Map<String,String> props = PageFlowUtil.map(
                    "studyId", ""+study.getRowId(),
                    "datasetId", ""+form.getDatasetId(),
                    "typeURI", def.getTypeURI(),
                    "dateBased", ""+study.isDateBased(),
                    "returnURL", new ActionURL(DatasetDetailsAction.class, getContainer()).addParameter("id", form.getDatasetId()).toString());

            HtmlView text = new HtmlView("Modify the properties and schema (form fields/properties) for this dataset.");
            HttpView view = new GWTView(Designer.class, props);

            // hack for 4404 : Lookup picker performance is terrible when there are many containers
            ContainerManager.getAllChildren(ContainerManager.getRoot());

            return new VBox(text, view);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendNavTrailDatasetAdmin(root);
            root.addChild(_def.getName(), new ActionURL(DatasetDetailsAction.class, getContainer()).addParameter("id", _def.getDataSetId()));
            return root.addChild("Edit Dataset Definition");
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

    public static class DatasetFilterForm extends QueryViewAction.QueryExportForm implements HasViewContext
    {
        private Integer _cohortId;
        private String _qcState;
        private ViewContext _viewContext;

        public Integer getCohortId()
        {
            return _cohortId;
        }

        public void setCohortId(Integer cohortId)
        {
            if (StudyManager.getInstance().showCohorts(HttpView.currentContext().getContainer(), HttpView.currentContext().getUser()))
                _cohortId = cohortId;
        }

        public String getQCState()
        {
            return _qcState;
        }

        public void setQCState(String qcState)
        {
            _qcState = qcState;
        }

        public CohortImpl getCohort()
        {
            if (_cohortId != null)
                return StudyManager.getInstance().getCohortForRowId(HttpView.currentContext().getContainer(), HttpView.currentContext().getUser(), _cohortId.intValue());
            else
                return null;
        }

        public void setViewContext(ViewContext context)
        {
            _viewContext = context;
        }

        public ViewContext getViewContext()
        {
            return _viewContext;
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class OverviewAction extends SimpleViewAction<DatasetFilterForm>
    {
        private StudyImpl _study;
        public ModelAndView getView(DatasetFilterForm form, BindException errors) throws Exception
        {
            _study = getStudy();
            OverviewBean bean = new OverviewBean();
            bean.study = _study;
            bean.showAll = "1".equals(getViewContext().get("showAll"));
            bean.canManage = getContainer().hasPermission(getUser(), ManageStudyPermission.class);
            bean.showCohorts = StudyManager.getInstance().showCohorts(getContainer(), getUser());
            if (StudyManager.getInstance().showQCStates(getContainer()))
                bean.qcStates = QCStateSet.getSelectedStates(getContainer(), form.getQCState());
            if (!bean.showCohorts)
                bean.cohortId = null;
            else
                bean.cohortId = form.getCohortId();

            VisitManager visitManager = StudyManager.getInstance().getVisitManager(bean.study);
            bean.visitMapSummary = visitManager.getVisitSummary(bean.showCohorts ? form.getCohort() : null, bean.qcStates);
            return new StudyJspView<OverviewBean>(getStudy(), "overview.jsp", bean, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Overview:" + _study.getLabel());
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class DatasetReportAction extends QueryViewAction<QueryViewAction.QueryExportForm, QueryView>
    {
        Report _report;

        public DatasetReportAction()
        {
            super(QueryExportForm.class);
        }

        private Report getReport() throws Exception
        {
            String reportId = (String)getViewContext().get("Dataset.reportId");

            ReportIdentifier identifier = ReportService.get().getReportIdentifier(reportId);
            if (identifier != null)
                return identifier.getReport();

            return null;
        }

        protected ModelAndView getHtmlView(QueryViewAction.QueryExportForm form, BindException errors) throws Exception
        {
            ViewContext context = getViewContext();
            _report = getReport();

            // is not a report (either the default grid view or a custom view)...
            if (_report == null)
            {
                return HttpView.redirect(createRedirectURLfrom(DatasetAction.class, context));
            }

            int datasetId = NumberUtils.toInt((String)context.get(DataSetDefinition.DATASETKEY), -1);
            DataSet def = StudyManager.getInstance().getDataSetDefinition(getStudy(), datasetId);

            if (def != null)
            {
                ActionURL url = getViewContext().cloneActionURL().setAction(StudyController.DatasetAction.class).
                                        replaceParameter("Dataset.reportId", _report.getDescriptor().getReportId().toString()).
                                        replaceParameter(DataSetDefinition.DATASETKEY, String.valueOf(def.getDataSetId()));

                return HttpView.redirect(url);
            }
            else if (ReportManager.get().canReadReport(getUser(), getContainer(), _report))
                return _report.getRunReportView(getViewContext());
            else
                return new HtmlView("User does not have read permission on this report.");
        }

        protected QueryView createQueryView(QueryExportForm form, BindException errors, boolean forExport, String dataRegion) throws Exception
        {
            Report report = getReport();
            if (report instanceof QueryReport)
            {
                return ((QueryReport)report).getQueryViewGenerator().generateQueryView(getViewContext(), report.getDescriptor());                
            }
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild(_report.getDescriptor().getReportName());
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
            String viewName = (String) getViewContext().get("Dataset.reportId");
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
    public class DatasetAction extends QueryViewAction<DatasetFilterForm, QueryView>
    {
        private Integer _cohortId;
        private int _datasetId;
        private int _visitId;
        private String _encodedQcState;

        public DatasetAction()
        {
            super(DatasetFilterForm.class);
        }

        protected ModelAndView getHtmlView(DatasetFilterForm form, BindException errors) throws Exception
        {
            // the full resultset is a join of all datasets for each participant
            // each dataset is determined by a visitid/datasetid

            StudyImpl study = getStudy();
            _cohortId = form.getCohortId();
            _encodedQcState = form.getQCState();
            QCStateSet qcStateSet = null;
            if (StudyManager.getInstance().showQCStates(getContainer()))
                qcStateSet = QCStateSet.getSelectedStates(getContainer(), form.getQCState());
            CohortImpl cohort = form.getCohort();
            ViewContext context = getViewContext();

            String export = StringUtils.trimToNull(context.getActionURL().getParameter("export"));
            
            Object datasetKeyObject = context.get(DataSetDefinition.DATASETKEY);
            if (datasetKeyObject instanceof List)
            {
                // bug 7365: It's been specified twice -- once in the POST, once in the GET. Just need one of them.
                List<?> list = (List<?>)datasetKeyObject;
                datasetKeyObject = list.get(0);
            }
            _datasetId = NumberUtils.toInt(datasetKeyObject.toString(), 0);

            String viewName = (String)context.get("Dataset.viewName");
            final DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, _datasetId);
            if (null == def)
                return new TypeNotFoundAction().getView(form, errors);
            String typeURI = def.getTypeURI();
            if (null == typeURI)
                return new TypeNotFoundAction().getView(form, errors);

            _visitId = NumberUtils.toInt((String)context.get(VisitImpl.VISITKEY), 0);
            VisitImpl visit = null;
            if (_visitId != 0)
            {
                visit = StudyManager.getInstance().getVisitForRowId(getStudy(), _visitId);
                if (null == visit)
                    HttpView.throwNotFound();
            }

            final StudyQuerySchema querySchema = new StudyQuerySchema(study, getUser(), true);
            QuerySettings qs = querySchema.getSettings(context, DataSetQueryView.DATAREGION);
            qs.setSchemaName(querySchema.getSchemaName());
            qs.setQueryName(def.getLabel());
            DataSetQueryView queryView = new DataSetQueryView(def, querySchema, qs, visit, cohort, qcStateSet);
            queryView.setForExport(export != null);
            queryView.disableContainerFilterSelection();
            boolean showEditLinks = !QueryService.get().isQuerySnapshot(getContainer(), StudyManager.getSchemaName(), def.getLabel()) &&
                def.getProtocolId() == null;
            queryView.setShowEditLinks(showEditLinks);

            final ActionURL url = context.getActionURL();
            setColumnURL(url, queryView, querySchema, def);

            // clear the property map cache and the sort map cache
            getParticipantPropsMap(context).clear();
            getDatasetSortColumnMap(context).clear();

            final TableInfo table = queryView.getTable();
            if (table != null)
            {
                addParticipantListToCache(context, _datasetId, viewName, generateParticipantList(queryView), cohort, form.getQCState());
                getExpandedState(context, _datasetId).clear();

                queryView.setShowSourceLinks(hasSourceLsids(table));
            }

            if (null != export)
            {
                if ("tsv".equals(export))
                    queryView.exportToTsv(context.getResponse());
                else if ("xls".equals(export))
                    queryView.exportToExcel(context.getResponse());
                return null;
            }

            List<ActionButton> buttonBar = new ArrayList<ActionButton>();
            populateButtonBar(buttonBar, def, queryView, cohort, qcStateSet);
            queryView.setButtons(buttonBar);

            StringBuffer sb = new StringBuffer();
            if (def.getDescription() != null && def.getDescription().length() > 0)
                sb.append(PageFlowUtil.filter(def.getDescription(), true, true)).append("<br/>");
            sb.append("<br/><span><b>View :</b> ").append(filter(getViewName())).append("</span>");
            if (cohort != null)
                sb.append("<br/><span><b>Cohort :</b> ").append(filter(cohort.getLabel())).append("</span>");
            if (qcStateSet != null)
                sb.append("<br/><span><b>QC States:</b> ").append(filter(qcStateSet.getLabel())).append("</span>");
            HtmlView header = new HtmlView(sb.toString());

            HttpView view = new VBox(header, queryView);
            Report report = queryView.getSettings().getReportView();
            if (report != null && !ReportManager.get().canReadReport(getUser(), getContainer(), report))
            {
                return new HtmlView("User does not have read permission on this report.");
            }
            else if (report == null && !def.canRead(getUser()))
            {
                return new HtmlView("User does not have read permission on this dataset.");
            }
            return view;
        }

        protected QueryView createQueryView(DatasetFilterForm datasetFilterForm, BindException errors, boolean forExport, String dataRegion) throws Exception
        {
            QuerySettings qs = new QuerySettings(getViewContext(), "Dataset");
            Report report = qs.getReportView();
            if (report instanceof QueryReport)
            {
                return ((QueryReport)report).getQueryViewGenerator().generateQueryView(getViewContext(), report.getDescriptor());
            }
            return null;
        }

        private String getViewName()
        {
            QuerySettings qs = new QuerySettings(getViewContext(), "Dataset");
            if (qs.getViewName() != null)
                return qs.getViewName();
            else
            {
                Report report = qs.getReportView();
                if (report != null)
                    return report.getDescriptor().getReportName();
                else
                    return "default";
            }
        }

        private void populateButtonBar(List<ActionButton> buttonBar, DataSetDefinition def, DataSetQueryView queryView, CohortImpl cohort, QCStateSet currentStates)
        {
            createViewButton(buttonBar, queryView);
            createCohortButton(buttonBar, cohort);
            if (StudyManager.getInstance().showQCStates(queryView.getContainer()))
                createQCStateButton(queryView, buttonBar, currentStates);

            // TODO: Consolidate this with QueryView.createExportMenuButton()
            MenuButton exportMenuButton = new MenuButton("Export");

            exportMenuButton.addMenuItem("Export all to Excel (.xls)", getViewContext().cloneActionURL().replaceParameter("export", "xls"));
            exportMenuButton.addMenuItem("Export all to text file (.tsv)", getViewContext().cloneActionURL().replaceParameter("export", "tsv"));
            exportMenuButton.addMenuItem("Excel Web Query (.iqy)", queryView.urlFor(QueryAction.excelWebQueryDefinition).getLocalURIString());
            exportMenuButton.addSeparator();
            queryView.addExportScriptItems(exportMenuButton);
            buttonBar.add(exportMenuButton);

            buttonBar.add(queryView.createPageSizeMenuButton());

            User user = getUser();
            boolean canWrite = def.canWrite(user) && def.getContainer().getPolicy().hasPermission(user, UpdatePermission.class);
            boolean isSnapshot = QueryService.get().isQuerySnapshot(getContainer(), StudyManager.getSchemaName(), def.getLabel());
            boolean isAssayDataset = def.getProtocolId() != null;
            ExpProtocol protocol = null;
            if (isAssayDataset)
            {
                protocol = ExperimentService.get().getExpProtocol(def.getProtocolId().intValue());
                if (protocol == null)
                    isAssayDataset = false;
            }

            if (!isSnapshot && canWrite && !isAssayDataset)
            {
                // Insert single entry
                ActionURL insertURL = new ActionURL(DatasetController.InsertAction.class, getContainer());
                insertURL.addParameter(DataSetDefinition.DATASETKEY, _datasetId);
                ActionButton insertButton = new ActionButton(insertURL.getLocalURIString(), "Insert New", DataRegion.MODE_GRID, ActionButton.Action.LINK);
                insertButton.setDisplayPermission(ACL.PERM_INSERT);
                buttonBar.add(insertButton);
            }

            if (!isSnapshot)
            {
                if (!isAssayDataset && (user.isAdministrator() || canWrite)) // admins always get the import and delete buttons
                {
                    // bulk import
                    ActionButton uploadButton = new ActionButton("showImportDataset.view?datasetId=" + _datasetId, "Import Data", DataRegion.MODE_GRID, ActionButton.Action.LINK);
                    uploadButton.setDisplayPermission(ACL.PERM_INSERT);
                    buttonBar.add(uploadButton);

                    ActionButton deleteRows = new ActionButton("button", "Delete");
                    ActionURL deleteRowsURL = new ActionURL(DeleteDatasetRowsAction.class, getContainer());
                    deleteRows.setURL(deleteRowsURL);
                    deleteRows.setRequiresSelection(true, "Delete selected rows of this dataset?");
                    deleteRows.setActionType(ActionButton.Action.POST);
                    deleteRows.setDisplayPermission(ACL.PERM_DELETE);
                    buttonBar.add(deleteRows);
                }
                else if (isAssayDataset)
                {
                    List<ActionButton> buttons = AssayService.get().getImportButtons(protocol, getUser(), getContainer(), true);
                    buttonBar.addAll(buttons);

                    if (user.isAdministrator() || canWrite)
                    {
                        ActionButton deleteRows = new ActionButton("button", "Recall");
                        ActionURL deleteRowsURL = new ActionURL(DeletePublishedRowsAction.class, getContainer());
                        deleteRowsURL.addParameter("protocolId", protocol.getRowId());
                        deleteRows.setURL(deleteRowsURL);
                        deleteRows.setRequiresSelection(true, "Recall selected rows of this dataset?");
                        deleteRows.setActionType(ActionButton.Action.POST);
                        deleteRows.setDisplayPermission(ACL.PERM_DELETE);
                        buttonBar.add(deleteRows);
                    }
                }
            }

            ActionButton viewSamples = new ActionButton("button", "View Specimens");
            ActionURL viewSamplesURL = new ActionURL(SpringSpecimenController.SelectedSamplesAction.class, getContainer());
            viewSamples.setURL(viewSamplesURL);
            viewSamples.setRequiresSelection(true);
            viewSamples.setActionType(ActionButton.Action.POST);
            viewSamples.setDisplayPermission(ACL.PERM_READ);
            buttonBar.add(viewSamples);

            if (isAssayDataset)
            {
                // provide a link to the source assay
                Container c = protocol.getContainer();
                if (c.hasPermission(getUser(), ACL.PERM_READ))
                {
                    ActionURL url = PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(
                            c,
                            protocol, 
                            new ContainerFilter.CurrentAndSubfolders(getUser()));
                    ActionButton viewAssayButton = new ActionButton("View Source Assay", url);
                    buttonBar.add(viewAssayButton);
                }
            }
        }

        private void createViewButton(List<ActionButton> buttonBar, DataSetQueryView queryView)
        {
            MenuButton button = queryView.createViewButton(StudyReportUIProvider.getItemFilter());
            button.addMenuItem("Set Default View", getViewContext().cloneActionURL().setAction(ViewPreferencesAction.class));

            buttonBar.add(button);
        }

        private void createCohortButton(List<ActionButton> buttonBar, CohortImpl currentCohort)
        {
            if (StudyManager.getInstance().showCohorts(getContainer(), getUser()))
            {
                CohortImpl[] cohorts = StudyManager.getInstance().getCohorts(getContainer(), getUser());
                if (cohorts.length > 0)
                {
                    MenuButton button = new MenuButton("Cohorts");
                    NavTree item = new NavTree("All", getViewContext().cloneActionURL().replaceParameter(SharedFormParameters.cohortId, "").toString());
                    item.setId("Cohorts:All");
                    if (currentCohort == null)
                        item.setSelected(true);
                    button.addMenuItem(item);

                    for (CohortImpl cohort : cohorts)
                    {
                        item = new NavTree(cohort.getLabel(),
                                getViewContext().cloneActionURL().replaceParameter(SharedFormParameters.cohortId, String.valueOf(cohort.getRowId())).toString());
                        item.setId("Cohorts:" + cohort.getLabel());
                        if (currentCohort != null && currentCohort.getRowId() == cohort.getRowId())
                            item.setSelected(true);

                        button.addMenuItem(item);
                    }

                    if (getViewContext().hasPermission(ACL.PERM_ADMIN))
                    {
                        button.addSeparator();
                        button.addMenuItem("Manage Cohorts", new ActionURL(CohortController.ManageCohortsAction.class, getContainer()));
                    }
                    buttonBar.add(button);
                }
            }
        }

        private void createQCStateButton(DataSetQueryView view, List<ActionButton> buttonBar, QCStateSet currentSet)
        {
            List<QCStateSet> stateSets = QCStateSet.getSelectableSets(getContainer());
            MenuButton button = new MenuButton("QC State");

            for (QCStateSet set : stateSets)
            {
                NavTree setItem = new NavTree(set.getLabel(), getViewContext().cloneActionURL().replaceParameter(SharedFormParameters.QCState, set.getFormValue()).toString());
                setItem.setId("QCState:" + set.getLabel());
                if (set.equals(currentSet))
                    setItem.setSelected(true);
                button.addMenuItem(setItem);
            }
            if (getContainer().hasPermission(getUser(), ACL.PERM_ADMIN))
            {
                button.addSeparator();
                ActionURL updateAction = new ActionURL(UpdateQCStateAction.class, getContainer());
                NavTree updateItem = button.addMenuItem("Update state of selected rows", "#", "if (verifySelected(document.forms[\"" +
                        view.getDataRegionName() + "\"], \"" + updateAction.getLocalURIString() + "\", \"post\", \"rows\")) document.forms[\"" +
                        view.getDataRegionName() + "\"].submit()");
                updateItem.setId("QCState:updateSelected");
                
                button.addMenuItem("Manage states", new ActionURL(ManageQCStatesAction.class,
                        getContainer()).addParameter("returnUrl", getViewContext().getActionURL().getLocalURIString()));
            }
            buttonBar.add(button);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return _appendNavTrail(root, _datasetId, _visitId,  _cohortId != null ? _cohortId.intValue() : -1, _encodedQcState);
        }
    }

    @RequiresPermission(ACL.PERM_NONE)
    public class ExpandStateNotifyAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            final ActionURL url = getViewContext().getActionURL();
            final String collapse = url.getParameter("collapse");
            final String datasetId = url.getParameter(DataSetDefinition.DATASETKEY);
            final String id = url.getParameter("id");

            if (datasetId != null && id != null)
            {
                Map<Integer, String> expandedMap = getExpandedState(getViewContext(), Integer.parseInt(id));
                // collapse param is only set on a collapse action
                if (collapse != null)
                    expandedMap.put(Integer.parseInt(datasetId), "collapse");
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
    public class ParticipantAction extends SimpleViewAction<ParticipantForm>
    {
        ParticipantForm _bean;

        public ModelAndView getView(ParticipantForm form, BindException errors) throws Exception
        {
            Study study = getStudy();
            _bean = form;
            String previousParticipantURL = null;
            String nextParticiapantURL = null;

            String viewName = (String) getViewContext().get("Dataset.viewName");

            // display the next and previous buttons only if we have a cached participant index
            CohortImpl cohort = null;
            if (form.getCohortId() != null)
            {
                if (!StudyManager.getInstance().showCohorts(getContainer(), getUser()))
                {
                    HttpView.throwUnauthorized("User does not have permission to view cohort information");
                }
                cohort = StudyManager.getInstance().getCohortForRowId(getContainer(), getUser(), form.getCohortId().intValue());
            }
            List<String> participants = getParticipantListFromCache(getViewContext(), form.getDatasetId(), viewName, cohort, form.getQCState());
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

            CustomParticipantView customParticipantView = StudyManager.getInstance().getCustomParticipantView(study);
            ModelAndView participantView;
            if (customParticipantView != null && customParticipantView.isActive())
            {
                participantView = new HtmlView(customParticipantView.getBody());
            }
            else
            {
                ModelAndView characteristicsView = StudyManager.getInstance().getParticipantDemographicsView(getContainer(), form, errors);
                ModelAndView dataView = StudyManager.getInstance().getParticipantView(getContainer(), form, errors);

                participantView = new VBox(characteristicsView, dataView);
            }

            ParticipantNavView navView = new ParticipantNavView(previousParticipantURL, nextParticiapantURL, form.getParticipantId(), form.getQCState());

            return new VBox(navView, participantView);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return _appendNavTrail(root, _bean.getDatasetId(), 0, _bean.getCohortId() != null ? _bean.getCohortId().intValue() : -1, _bean.getQCState()).
                    addChild("Participant - " + _bean.getParticipantId());
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class UploadVisitMapAction extends FormViewAction<TSVForm>
    {
        public ModelAndView getView(TSVForm tsvForm, boolean reshow, BindException errors) throws Exception
        {
            ModelAndView view = new StudyJspView<Object>(getStudy(), "uploadVisitMap.jsp", null, errors);
            view.addObject("errors", errors);
            return view;
        }

        public void validateCommand(TSVForm target, Errors errors)
        {
        }

        public boolean handlePost(TSVForm form, BindException errors) throws Exception
        {
            VisitMapImporter importer = new VisitMapImporter();
            List<String> errorMsg = new LinkedList<String>();
            if (!importer.process(getUser(), getStudy(), form.getContent(), VisitMapImporter.Format.DataFax, errorMsg))
            {
                for (String error : errorMsg)
                    errors.reject("uploadVisitMap", error);
                return false;
            }
            return true;
        }

        public ActionURL getSuccessURL(TSVForm tsvForm)
        {
            return new ActionURL(BeginAction.class, getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Create New Study: Visit Map Upload");
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
            createStudy(getStudy(true), getContainer(), getUser(), form);
            updateRepositorySettings(getContainer(), form.isSimpleRepository());
            return true;
        }

        public ActionURL getSuccessURL(StudyPropertiesForm studyPropertiesForm)
        {
            return new ActionURL(ManageStudyAction.class, getContainer());
        }
    }

    public static void createStudy(StudyImpl study, Container c, User user, StudyPropertiesForm form) throws SQLException, ServletException
    {
        if (null == study)
        {
            study = new StudyImpl(c, form.getLabel());
            study.setDateBased(form.isDateBased());
            study.setStartDate(form.getStartDate());
            study.setSecurityType(form.getSecurityType());
            StudyManager.getInstance().createStudy(user, study);
        }
    }

    public static void updateRepositorySettings(Container c, boolean simple) throws SQLException
    {
        RepositorySettings reposSettings = SampleManager.getInstance().getRepositorySettings(c);
        reposSettings.setSimple(simple);
        reposSettings.setEnableRequests(!simple);
        SampleManager.getInstance().saveRepositorySettings(c, reposSettings);
    }

    @RequiresPermissionClass(ManageStudyPermission.class)
    public class ManageStudyAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            StudyPropertiesQueryView propView = new StudyPropertiesQueryView(getUser(), getStudy(), HttpView.currentContext(), true);

            return new StudyJspView<StudyPropertiesQueryView>(getStudy(true), "manageStudy.jsp", propView, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            try
            {
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
                StudyImpl updated = getStudy().createMutable();
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
    public class ManageStudyPropertiesAction extends SimpleViewAction<StudyPropertiesForm>
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
            StudyImpl study = getStudy().createMutable();
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
    public class ManageTypesAction extends FormViewAction<ManageTypesForm>
    {
        public void validateCommand(ManageTypesForm target, Errors errors)
        {
        }

        public ModelAndView getView(ManageTypesForm manageTypesForm, boolean reshow, BindException errors) throws Exception
        {
            ModelAndView view = new StudyJspView<ManageTypesAction>(getStudy(), "manageTypes.jsp", this, errors);
            view.addObject("errors", errors);
            return view;
        }

        public boolean handlePost(ManageTypesForm form, BindException errors) throws Exception
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

        public ActionURL getSuccessURL(ManageTypesForm manageTypesForm)
        {
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendManageStudy(root);
            return root.addChild("Manage Datasets");
        }
    }

    public static class ManageTypesForm
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
    public class ManageSitesAction extends FormViewAction<BulkEditForm>
    {
        public void validateCommand(BulkEditForm target, Errors errors)
        {
        }

        public ModelAndView getView(BulkEditForm bulkEditForm, boolean reshow, BindException errors) throws Exception
        {
            ModelAndView view = new StudyJspView<StudyImpl>(getStudy(), "manageSites.jsp", getStudy(), errors);
            view.addObject("errors", errors);
            return view;
        }

        public boolean handlePost(BulkEditForm form, BindException errors) throws Exception
        {
            int[] ids = form.getIds();
            if (ids != null && ids.length > 0)
            {
                String[] labels = form.getLabels();
                Map<Integer, String> labelLookup = new HashMap<Integer, String>();
                for (int i = 0; i < ids.length; i++)
                    labelLookup.put(ids[i], labels[i]);

                boolean emptyLabel = false;
                for (SiteImpl site : getStudy().getSites())
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
                        SiteImpl site = new SiteImpl();
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

        public ActionURL getSuccessURL(BulkEditForm bulkEditForm)
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
        private VisitImpl _v;

        public void validateCommand(VisitForm target, Errors errors)
        {

            try
            {
                StudyImpl study = getStudy();
                target.validate(errors, study);
                if(errors.getErrorCount() > 0)
                    return;

                //check for overlapping visits

                VisitManager visitMgr = StudyManager.getInstance().getVisitManager(study);
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
            VisitImpl postedVisit = form.getBean();
            if (!getContainer().getId().equals(postedVisit.getContainer().getId()))
                    HttpView.throwUnauthorized();
            // UNDONE: how do I get struts to handle this checkbox?
            postedVisit.setShowByDefault(null != StringUtils.trimToNull((String)getViewContext().get("showByDefault")));

            // UNDONE: reshow is broken for this form, but we have to validate
            TreeMap<Double, VisitImpl> visits = StudyManager.getInstance().getVisitManager(getStudy()).getVisitSequenceMap();
            boolean validRange = true;
            // make sure there is no overlapping visit
            for (VisitImpl v : visits.values())
            {
                if (v.getRowId() == postedVisit.getRowId())
                    continue;
                double maxL = Math.max(v.getSequenceNumMin(), postedVisit.getSequenceNumMin());
                double minR = Math.min(v.getSequenceNumMax(), postedVisit.getSequenceNumMax());
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
            VisitImpl visit = StudyManager.getInstance().getVisitForRowId(study, visitId);
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
        private VisitImpl _visit;
        public ModelAndView getView(IdForm form, BindException errors) throws Exception
        {
            int visitId = form.getId();
            StudyImpl study = getStudy();
            _visit = StudyManager.getInstance().getVisitForRowId(study, visitId);
            if (null == _visit)
                return HttpView.throwNotFound();

            ModelAndView view = new StudyJspView<VisitImpl>(study, "confirmDeleteVisit.jsp", _visit, errors);
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

            try
            {
                StudyImpl study = getStudy();
                target.validate(errors, study);
                if(errors.getErrorCount() > 0)
                    return;

                //check for overlapping visits
                VisitManager visitMgr = StudyManager.getInstance().getVisitManager(study);
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
            VisitImpl visit = form.getBean();
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
            if (_def != null)
            {
                try
                {
                    VisitManager visitManager = StudyManager.getInstance().getVisitManager(getStudy());
                    return root.addChild("Edit " + _def.getLabel() + " " + visitManager.getPluralLabel());
                }
                catch (ServletException se)
                {
                    throw new UnexpectedException(se);
                }
            }
            return root;
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
                return HttpView.throwNotFound();
            form.setKeys(StringUtils.join(def.getDisplayKeyNames(), ", "));

            return new JspView<ImportDataSetForm>("/org/labkey/study/view/importDataset.jsp", form, errors);
        }

        public boolean handlePost(ImportDataSetForm form, BindException errors) throws Exception
        {
            String tsvData = form.getTsv();
            if (tsvData == null)
            {
                errors.reject("showImportDataset", "Form contains no data");
                return false;
            }
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

                if (getStudy().isDateBased())
                    columnMap.put("date", DataSetDefinition.getVisitDateURI());
                columnMap.put("ptid", DataSetDefinition.getParticipantIdURI());
                columnMap.put("qcstate", DataSetDefinition.getQCStateURI());
                columnMap.put("dfcreate", DataSetDefinition.getCreatedURI());     // datafax field name
                columnMap.put("dfmodify", DataSetDefinition.getModifiedURI());    // datafax field name
                List<String> errorList = new LinkedList<String>();

                DataSetDefinition dsd = StudyManager.getInstance().getDataSetDefinition(getStudy(), form.getDatasetId());
                Pair<String[],UploadLog> result = AssayPublishManager.getInstance().importDatasetTSV(getUser(), getStudy(), dsd, tsvData, columnMap, errorList);

                if (result.getKey().length > 0)
                {
                    // Log the import
                    String comment = "Dataset data imported. " + result.getKey().length + " rows imported";
                    StudyServiceImpl.addDatasetAuditEvent(
                            getUser(), getContainer(), dsd, comment, result.getValue());
                }
                for (String error : errorList)
                {
                    errors.reject("showImportDataset", PageFlowUtil.filter(error));
                }
                return errorList.isEmpty();
            }
            return false;
        }

        public ActionURL getSuccessURL(ImportDataSetForm form)
        {
            ActionURL url = new ActionURL(DatasetAction.class, getContainer()).
                    addParameter(DataSetDefinition.DATASETKEY, form.getDatasetId());
            if (StudyManager.getInstance().showQCStates(form.getContainer()))
                url.addParameter(SharedFormParameters.QCState, QCStateSet.getAllStates(form.getContainer()).getFormValue());
            return url;
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
            SchemaReader reader = new SchemaTsvReader(getStudy(), form.tsv, form.getLabelColumn(), form.getTypeNameColumn(), form.getTypeIdColumn(), errors);
            return StudyManager.getInstance().importDatasetSchemas(getStudy(), getUser(), reader, errors);
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
                filter.addCondition(DataSetDefinition.DATASETKEY, form.getId());
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
            final StudyImpl study = getStudy();
            final ViewContext context = getViewContext();

            VBox view = new VBox();

            int datasetId = null == context.get(DataSetDefinition.DATASETKEY) ? 0 : Integer.parseInt((String) context.get(DataSetDefinition.DATASETKEY));
            final DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, datasetId);
            final StudyQuerySchema querySchema = new StudyQuerySchema(study, getUser(), true);
            QuerySettings qs = new QuerySettings(context, DataSetQueryView.DATAREGION);
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

                ActionButton deleteRows = new ActionButton("button", "Recall Rows");
                ActionURL deleteURL = new ActionURL(DeletePublishedRowsAction.class, getContainer());
                deleteURL.addParameter("protocolId", protocolId);
                deleteURL.addParameter("sourceLsid", sourceLsid);

                //String deleteRowsURL = ActionURL.toPathString("Study", "deletePublishedRows", getContainer()) + "?datasetId=" + datasetId;
                deleteRows.setURL(deleteURL);
                deleteRows.setRequiresSelection(true, "Recall selected rows of this dataset?");
                deleteRows.setActionType(ActionButton.Action.POST);
                deleteRows.setDisplayPermission(ACL.PERM_DELETE);

                PublishedRecordQueryView qv = new PublishedRecordQueryView(def, querySchema, qs, sourceLsid,
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
            final DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(getStudy(), form.getDatasetId());
            if (def == null)
                throw new IllegalArgumentException("Could not find a dataset definition for id: " + form.getDatasetId());

            Collection<String> allLsids;
            if (!form.isDeleteAllData())
            {
                allLsids = DataRegionSelection.getSelected(getViewContext(), true);

                if (allLsids.isEmpty())
                {
                    errors.reject("deletePublishedRows", "No rows were selected");
                    return false;
                }
            }
            else
            {
                allLsids = StudyManager.getInstance().getDatasetLSIDs(getUser(), def);
            }

            String protocolId = (String)getViewContext().get("protocolId");
            String originalSourceLsid = (String)getViewContext().get("sourceLsid");

            // Need to handle this by groups of source lsids -- each assay container needs logging
            MultiMap<String,String> sourceLsid2datasetLsid = new MultiHashMap<String,String>();


            if (originalSourceLsid != null)
            {
                sourceLsid2datasetLsid.putAll(originalSourceLsid, allLsids);
            }
            else
            {
                Map<String,Object>[] data = StudyService.get().getDatasetRows(getUser(), getContainer(), form.getDatasetId(), allLsids);
                for (Map<String,Object> row : data)
                {
                    sourceLsid2datasetLsid.put(row.get("sourcelsid").toString(), row.get("lsid").toString());
                }
            }

            if (protocolId != null)
            {
                for (Map.Entry<String,Collection<String>> entry : sourceLsid2datasetLsid.entrySet())
                {
                    String sourceLsid = entry.getKey();
                    Container sourceContainer;
                    ExpRun expRun = ExperimentService.get().getExpRun(sourceLsid);
                    if (expRun != null && expRun.getContainer() != null)
                        sourceContainer = expRun.getContainer();
                    else
                        continue; // No logging if we can't find a matching run

                    AuditLogEvent event = new AuditLogEvent();

                    event.setCreatedBy(getUser());
                    event.setEventType(AssayPublishManager.ASSAY_PUBLISH_AUDIT_EVENT);
                    event.setContainerId(sourceContainer.getId());
                    event.setKey1(getContainer().getId());

                    String assayName = def.getLabel();
                    if (def.getProtocolId() != null)
                    {
                        ExpProtocol protocol = ExperimentService.get().getExpProtocol(def.getProtocolId().intValue());
                        if (protocol != null)
                            assayName = protocol.getName();
                    }

                    event.setIntKey1(NumberUtils.toInt(protocolId));
                    Collection<String> lsids = entry.getValue();
                    event.setComment(lsids.size() + " row(s) were recalled to the assay: " + assayName);

                    Map<String,Object> dataMap = Collections.<String,Object>singletonMap(DataSetDefinition.DATASETKEY, form.getDatasetId());

                    AssayAuditViewFactory.getInstance().ensureDomain(getUser());
                    AuditLogService.get().addEvent(event, dataMap, AuditLogService.get().getDomainURI(AssayPublishManager.ASSAY_PUBLISH_AUDIT_EVENT));
                }
            }
            StudyManager.getInstance().deleteDatasetRows(getStudy(), def, allLsids);
            
            ExpProtocol protocol = ExperimentService.get().getExpProtocol(NumberUtils.toInt(protocolId));
            if (protocol != null && originalSourceLsid != null)
            {
                ExpRun expRun = ExperimentService.get().getExpRun(originalSourceLsid);
                if (expRun != null && expRun.getContainer() != null)
                    HttpView.throwRedirect(AssayPublishService.get().getPublishHistory(expRun.getContainer(), protocol));
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
        private boolean deleteAllData;

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
            DataSet dataset = StudyManager.getInstance().getDataSetDefinition(getStudy(), datasetId);
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

    public static class ImportTypeForm
    {
        private String typeName;
        private Integer dataSetId;
        private boolean autoDatasetId;
        private boolean fileImport;

        public String getTypeName()
        {
            return typeName;
        }

        public void setTypeName(String typeName)
        {
            this.typeName = typeName;
        }

        public Integer getDataSetId()
        {
            return dataSetId;
        }

        public void setDataSetId(Integer dataSetId)
        {
            this.dataSetId = dataSetId;
        }

        public boolean isAutoDatasetId()
        {
            return autoDatasetId;
        }

        public void setAutoDatasetId(boolean autoDatasetId)
        {
            this.autoDatasetId = autoDatasetId;
        }

        public boolean isFileImport()
        {
            return fileImport;
        }

        public void setFileImport(boolean fileImport)
        {
            this.fileImport = fileImport;
        }
    }

    public static class OverviewBean
    {
        public StudyImpl study;
        public Map<VisitMapKey,Integer> visitMapSummary;
        public boolean showAll;
        public boolean canManage;
        public Integer cohortId;
        public boolean showCohorts;
        public QCStateSet qcStates;
    }

    /**
     * Tweak the link url for participant view so that it contains enough information to regenerate
     * the cached list of participants.
     */
    private void setColumnURL(final ActionURL url, final QueryView queryView,
                              final UserSchema querySchema, final DataSet def)
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
                                (SharedFormParameters.cohortId.name().equals(param.getKey())) ||
                                (SharedFormParameters.QCState.name().equals(param.getKey())) ||
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
            ResultSetUtil.close(rs);
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

    public static Map<String, Integer> getSortedColumnList(ViewContext context, DataSet dsd)
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

    private static String getParticipantListCacheKey(int dataset, String viewName, CohortImpl cohort, String encodedQCState)
    {
        String key = Integer.toString(dataset);
        // if there is also a view associated with the dataset, incorporate it into the key as well
        if (viewName != null && !StringUtils.isEmpty(viewName))
            key = key + viewName;
        if (cohort != null)
            key = key + "cohort" + cohort.getRowId();
        if (encodedQCState != null)
            key = key + "qcState" + encodedQCState;
        return key;
    }

     public static void addParticipantListToCache(ViewContext context, int dataset, String viewName, List<String> participants, CohortImpl cohort, String encodedQCState)
    {

        Map<String, List<String>> map = getParticipantMapFromCache(context);
        map.put(getParticipantListCacheKey(dataset, viewName, cohort, encodedQCState), participants);
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

    public static List<String> getParticipantListFromCache(ViewContext context, int dataset, String viewName, CohortImpl cohort, String encodedQCState)
    {
        Map<String, List<String>> map = getParticipantMapFromCache(context);
        String key = getParticipantListCacheKey(dataset, viewName, cohort, encodedQCState);
        List<String> plist = map.get(key);
        if (plist == null)
        {
            // not in cache, or session expired, try to regenerate the list
            plist = generateParticipantListFromURL(context, dataset, viewName, cohort, encodedQCState);
            map.put(key, plist);
        }
        return plist;
    }

    private static List<String> generateParticipantListFromURL(ViewContext context, int dataset, String viewName, CohortImpl cohort, String encodedQCState)
    {
        try {
            final StudyManager studyMgr = StudyManager.getInstance();
            final StudyImpl study = studyMgr.getStudy(context.getContainer());
            QCStateSet qcStateSet = null;
            if (StudyManager.getInstance().showQCStates(context.getContainer()))
            {
                qcStateSet = QCStateSet.getSelectedStates(context.getContainer(), encodedQCState);
            }

            DataSetDefinition def = studyMgr.getDataSetDefinition(study, dataset);
            if (null == def)
                return Collections.emptyList();
            String typeURI = def.getTypeURI();
            if (null == typeURI)
                return Collections.emptyList();

            int visitRowId = null == context.get(VisitImpl.VISITKEY) ? 0 : Integer.parseInt((String) context.get(VisitImpl.VISITKEY));
            VisitImpl visit = null;
            if (visitRowId != 0)
            {
                visit = studyMgr.getVisitForRowId(study, visitRowId);
                if (null == visit)
                    return Collections.emptyList();
            }
            StudyQuerySchema querySchema = new StudyQuerySchema(study, context.getUser(), true);
            QuerySettings qs = new QuerySettings(context, DataSetQueryView.DATAREGION);
            qs.setSchemaName(querySchema.getSchemaName());
            qs.setQueryName(def.getLabel());
            qs.setViewName(viewName);
            DataSetQueryView queryView = new DataSetQueryView(def, querySchema, qs, visit, cohort, qcStateSet);

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

    public static class VisitForm extends ViewFormData
    {
        private int[] _dataSetIds;
        private String[] _dataSetStatus;
        private Double _sequenceNumMin;
        private Double _sequenceNumMax;
        private Character _typeCode;
        private boolean _showByDefault;
        private Integer _cohortId;
        private String _label;
        private VisitImpl _visit;
        private int _visitDateDatasetId;

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
                    _visit = (VisitImpl) PageFlowUtil.decodeObject(request.getParameter(".oldValues"));
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

        public void validate(Errors errors, Study study)
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

            VisitImpl visit = getBean();
            if (visit.getSequenceNumMin() > visit.getSequenceNumMax())
            {
                double min = visit.getSequenceNumMax();
                double max = visit.getSequenceNumMin();
                visit.setSequenceNumMax(max);
                visit.setSequenceNumMin(min);
            }
            setBean(visit);
            // for date-based studies, values can be negative, but it's not allowed in visit-based studies
            if ( (visit.getSequenceNumMin() < 0 || visit.getSequenceNumMax() < 0) && !study.isDateBased())
                errors.reject(null, "Sequence numbers must be greater than or equal to zero.");
        }

        public VisitImpl getBean()
        {
            if (null == _visit)
                _visit = new VisitImpl();
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
            _visit.setVisitDateDatasetId(getVisitDateDatasetId());

            return _visit;
        }

        public void setBean(VisitImpl bean)
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

        public int getVisitDateDatasetId()
        {
            return _visitDateDatasetId;
        }

        public void setVisitDateDatasetId(int visitDateDatasetId)
        {
            _visitDateDatasetId = visitDateDatasetId;
        }
    }

    private static int getParameterCount(HttpServletRequest r, String s)
    {
        String[] values = r.getParameterValues(s);
        return values == null ? 0 : values.length;
    }
    
    public static class ManageQCStatesBean
    {
        private StudyImpl _study;
        private QCState[] _states;
        private String _returnUrl;

        public ManageQCStatesBean(StudyImpl study, String returnUrl)
        {
            _study = study;
            _returnUrl = returnUrl;
        }

        public QCState[] getQCStates()
        {
            if (_states == null)
                _states = StudyManager.getInstance().getQCStates(_study.getContainer());
            return _states;
        }

        public StudyImpl getStudy()
        {
            return _study;
        }

        public String getReturnUrl()
        {
            return _returnUrl;
        }
    }

    public static class ManageQCStatesForm extends ViewFormData
    {
        private int[] _ids;
        private String[] _labels;
        private String[] _descriptions;
        private int[] _publicData;
        private String _newLabel;
        private String _newDescription;
        private boolean _newPublicData;
        private boolean _reshowPage;
        private Integer _defaultPipelineQCState;
        private Integer _defaultAssayQCState;
        private Integer _defaultDirectEntryQCState;
        private boolean _showPrivateDataByDefault;
        private String _returnUrl;

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

        public String[] getDescriptions()
        {
            return _descriptions;
        }

        public void setDescriptions(String[] descriptions)
        {
            _descriptions = descriptions;
        }

        public int[] getPublicData()
        {
            return _publicData;
        }

        public void setPublicData(int[] publicData)
        {
            _publicData = publicData;
        }

        public String getNewLabel()
        {
            return _newLabel;
        }

        public void setNewLabel(String newLabel)
        {
            _newLabel = newLabel;
        }

        public String getNewDescription()
        {
            return _newDescription;
        }

        public void setNewDescription(String newDescription)
        {
            _newDescription = newDescription;
        }

        public boolean isNewPublicData()
        {
            return _newPublicData;
        }

        public void setNewPublicData(boolean newPublicData)
        {
            _newPublicData = newPublicData;
        }

        public boolean isReshowPage()
        {
            return _reshowPage;
        }

        public void setReshowPage(boolean reshowPage)
        {
            _reshowPage = reshowPage;
        }

        public Integer getDefaultPipelineQCState()
        {
            return _defaultPipelineQCState;
        }

        public void setDefaultPipelineQCState(Integer defaultPipelineQCState)
        {
            _defaultPipelineQCState = defaultPipelineQCState;
        }

        public Integer getDefaultAssayQCState()
        {
            return _defaultAssayQCState;
        }

        public void setDefaultAssayQCState(Integer defaultAssayQCState)
        {
            _defaultAssayQCState = defaultAssayQCState;
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

        public String getReturnUrl()
        {
            return _returnUrl;
        }

        public void setReturnUrl(String returnUrl)
        {
            _returnUrl = returnUrl;
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class ManageQCStatesAction extends FormViewAction<ManageQCStatesForm>
    {
        public ModelAndView getView(ManageQCStatesForm manageQCStatesForm, boolean reshow, BindException errors) throws Exception
        {
            return new JspView<ManageQCStatesBean>("/org/labkey/study/view/manageQCStates.jsp", 
                    new ManageQCStatesBean(getStudy(), manageQCStatesForm.getReturnUrl()), errors);
        }

        public void validateCommand(ManageQCStatesForm form, Errors errors)
        {
            Set<String> labels = new HashSet<String>();
            if (form.getLabels() != null)
            {
                for (String label : form.getLabels())
                {
                    if (labels.contains(label))
                    {
                        errors.reject(null, "QC state \"" + label + "\" is defined more than once.");
                        return;
                    }
                    else
                        labels.add(label);
                }
            }
            if (labels.contains(form.getNewLabel()))
                errors.reject(null, "QC state \"" + form.getNewLabel() + "\" is defined more than once.");
        }

        public boolean handlePost(ManageQCStatesForm form, BindException errors) throws Exception
        {
            if (form.getNewLabel() != null && form.getNewLabel().length() > 0)
            {
                QCState newState = new QCState();
                newState.setContainer(getContainer());
                newState.setLabel(form.getNewLabel());
                newState.setDescription(form.getNewDescription());
                newState.setPublicData(form.isNewPublicData());
                StudyManager.getInstance().insertQCState(getUser(), newState);
            }
            if (form.getIds() != null)
            {
                // use a map to store the IDs of the public QC states; since checkboxes are
                // ommitted from the request entirely if they aren't checked, we use a different
                // method for keeping track of the checked values (by posting the rowid of the item as the
                // checkbox value).
                Set<Integer> publicDataSet = new HashSet<Integer>();
                if (form.getPublicData() != null)
                {
                    for (int i = 0; i < form.getPublicData().length; i++)
                        publicDataSet.add(form.getPublicData()[i]);
                }

                for (int i = 0; i < form.getIds().length; i++)
                {
                    int rowId = form.getIds()[i];
                    QCState state = new QCState();
                    state.setRowId(rowId);
                    state.setLabel(form.getLabels()[i]);
                    if (form.getDescriptions() != null)
                        state.setDescription(form.getDescriptions()[i]);
                    state.setPublicData(publicDataSet.contains(state.getRowId()));
                    state.setContainer(getContainer());
                    StudyManager.getInstance().updateQCState(getUser(), state);
                }
            }

            updateQcState(getStudy(), getUser(), form);

            return true;
        }

        public ActionURL getSuccessURL(ManageQCStatesForm manageQCStatesForm)
        {
            if (manageQCStatesForm.isReshowPage())
            {
                ActionURL url = new ActionURL(ManageQCStatesAction.class, getContainer());
                if (manageQCStatesForm.getReturnUrl() != null)
                    url.addParameter("returnUrl", manageQCStatesForm.getReturnUrl());
                return url;
            }
            else if (manageQCStatesForm.getReturnUrl() != null)
                return new ActionURL(manageQCStatesForm.getReturnUrl());
            else
                return new ActionURL(ManageStudyAction.class, getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendManageStudy(root);
            return root.addChild("Manage QC States");
        }
    }

    // TODO: Move to StudyManager?
    public static void updateQcState(StudyImpl study, User user, ManageQCStatesForm form) throws SQLException
    {
        if (!nullSafeEqual(study.getDefaultAssayQCState(), form.getDefaultAssayQCState()) ||
            !nullSafeEqual(study.getDefaultPipelineQCState(), form.getDefaultPipelineQCState()) ||
            !nullSafeEqual(study.getDefaultDirectEntryQCState(), form.getDefaultDirectEntryQCState()) ||
            study.isShowPrivateDataByDefault() != form.isShowPrivateDataByDefault())
        {
            study = study.createMutable();
            study.setDefaultAssayQCState(form.getDefaultAssayQCState());
            study.setDefaultPipelineQCState(form.getDefaultPipelineQCState());
            study.setDefaultDirectEntryQCState(form.getDefaultDirectEntryQCState());
            study.setShowPrivateDataByDefault(form.isShowPrivateDataByDefault());
            StudyManager.getInstance().updateStudy(user, study);
        }
    }

    public static class DeleteQCStateForm extends IdForm
    {
        private boolean _all = false;
        private String _manageReturnUrl;

        public boolean isAll()
        {
            return _all;
        }

        public void setAll(boolean all)
        {
            _all = all;
        }

        public String getManageReturnUrl()
        {
            return _manageReturnUrl;
        }

        public void setManageReturnUrl(String manageReturnUrl)
        {
            _manageReturnUrl = manageReturnUrl;
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class DeleteQCStateAction extends RedirectAction<DeleteQCStateForm>
    {
        public boolean doAction(DeleteQCStateForm form, BindException errors) throws Exception
        {
            if (form.isAll())
            {
                QCState[] states = StudyManager.getInstance().getQCStates(getContainer());
                for (QCState state : states)
                {
                    if (!StudyManager.getInstance().isQCStateInUse(state))
                        StudyManager.getInstance().deleteQCState(state);
                }
            }
            else
            {
                QCState state = StudyManager.getInstance().getQCStateForRowId(getContainer(), form.getId());
                if (state != null)
                    StudyManager.getInstance().deleteQCState(state);
            }
            return true;
        }

        public void validateCommand(DeleteQCStateForm target, Errors errors)
        {
        }

        public ActionURL getSuccessURL(DeleteQCStateForm form)
        {
            ActionURL returnUrl = new ActionURL(ManageQCStatesAction.class, getContainer());
            if (form.getManageReturnUrl() != null)
                returnUrl.addParameter("returnUrl", form.getManageReturnUrl());
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
        private DataSetQueryView _queryView;

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

        public void setQueryView(DataSetQueryView queryView)
        {
            _queryView = queryView;
        }

        public DataSetQueryView getQueryView()
        {
            return _queryView;
        }
    }
    
    @RequiresPermission(ACL.PERM_ADMIN)
    public class UpdateQCStateAction extends FormViewAction<UpdateQCStateForm>
    {
        private int _datasetId;

        public void validateCommand(UpdateQCStateForm updateQCForm, Errors errors)
        {
            if (updateQCForm.isUpdate())
            {
                if (updateQCForm.getComments() == null || updateQCForm.getComments().length() == 0)
                    errors.reject(null, "Comments are required.");
            }
        }

        public ModelAndView getView(UpdateQCStateForm updateQCForm, boolean reshow, BindException errors) throws Exception
        {
            StudyImpl study = getStudy();
            _datasetId = updateQCForm.getDatasetId();
            DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, _datasetId);
            if (def == null)
            {
                HttpView.throwNotFound("No dataset found for id: " + _datasetId);
                return null;
            }
            Set<String> lsids = null;
            if ("POST".equalsIgnoreCase(getViewContext().getRequest().getMethod()))
                lsids = DataRegionSelection.getSelected(getViewContext(), updateQCForm.getDataRegionSelectionKey(), true, false);
            if (lsids == null || lsids.isEmpty())
                return new HtmlView("No data rows selected.  [<a href=\"javascript:back()\">back</a>]");
            StudyQuerySchema querySchema = new StudyQuerySchema(study, getUser(), true);
            QuerySettings qs = new QuerySettings(getViewContext(), DataSetQueryView.DATAREGION);
            qs.setSchemaName(querySchema.getSchemaName());
            qs.setQueryName(def.getLabel());
            final Set<String> finalLsids = lsids;
            DataSetQueryView queryView = new DataSetQueryView(def, querySchema, qs, null, null, null)
            {
                public DataView createDataView()
                {
                    DataView view = super.createDataView();
                    view.getDataRegion().setSortable(false);
                    view.getDataRegion().setShowFilters(false);
                    view.getDataRegion().setShowRecordSelectors(false);
                    view.getDataRegion().setMaxRows(0);
                    view.getDataRegion().setShowPagination(false);
                    SimpleFilter filter = (SimpleFilter) view.getRenderContext().getBaseFilter();
                    if (null == filter)
                    {
                        filter = new SimpleFilter();
                        view.getRenderContext().setBaseFilter(filter);
                    }
                    filter.addInClause("lsid", new ArrayList<String>(finalLsids));
                    return view;
                }
            };
            queryView.setShowDetailsColumn(false);
            queryView.setShowSourceLinks(false);
            queryView.setShowEditLinks(false);
            updateQCForm.setQueryView(queryView);
            updateQCForm.setDataRegionSelectionKey(DataRegionSelection.getSelectionKeyFromRequest(getViewContext()));
            return new JspView<UpdateQCStateForm>("/org/labkey/study/view/updateQCState.jsp", updateQCForm, errors);
        }

        public boolean handlePost(UpdateQCStateForm updateQCForm, BindException errors) throws Exception
        {
            if (!updateQCForm.isUpdate())
                return false;
            Set<String> lsids = DataRegionSelection.getSelected(getViewContext(), updateQCForm.getDataRegionSelectionKey(), true, false);

            QCState newState = null;
            if (updateQCForm.getNewState() != null)
            {
                newState = StudyManager.getInstance().getQCStateForRowId(getContainer(), updateQCForm.getNewState().intValue());
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

        public ActionURL getSuccessURL(UpdateQCStateForm updateQCForm)
        {
            ActionURL url = new ActionURL(DatasetAction.class, getContainer());
            url.addParameter(DataSetDefinition.DATASETKEY, updateQCForm.getDatasetId());
            if (updateQCForm.getNewState() != null)
                url.addParameter(SharedFormParameters.QCState, updateQCForm.getNewState().intValue());
            return url;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root = _appendNavTrail(root, _datasetId, -1);
            return root.addChild("Change QC State");
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
            {
                ReportIdentifier reportId = ReportService.get().getReportIdentifier(defaultView);
                if (reportId != null)
                    url.addParameter("Dataset.reportId", defaultView);
                else
                    url.addParameter("Dataset.viewName", defaultView);
            }
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
        public void export(Object o, HttpServletResponse response, BindException errors) throws Exception
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

            TableInfo tinfo = def.getTableInfo(getUser(), true, false);

            DataRegion dr = new DataRegion();
            dr.setTable(tinfo);

            Set<String> ignoreColumns = new CaseInsensitiveHashSet("lsid", "datasetid", "visitdate", "sourcelsid", "created", "modified", "visitrowid", "day", "qcstate", "dataset");
            if (study.isDateBased())
                ignoreColumns.add("SequenceNum");

            // If this is demographic data, user doesn't need to enter visit info -- we have defaults.
            if (def.isDemographicData())
            {
                if (study.isDateBased())
                    ignoreColumns.add("Date");
                else
                    ignoreColumns.add("SequenceNum");
            }
            if (def.isKeyPropertyManaged())
            {
                // Do not include a server-managed key field
                ignoreColumns.add(def.getKeyPropertyName());
            }

            // Need to ignore field-level qc columns that are generated
            for (ColumnInfo col : tinfo.getColumns())
            {
                if (col.isMvEnabled())
                {
                    ignoreColumns.add(col.getMvColumnName());
                    ignoreColumns.add(col.getName() + RawValueColumn.RAW_VALUE_SUFFIX);
                }
            }

            for (ColumnInfo col : tinfo.getColumns())
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
        StudyImpl study;
        DataSet def;

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
        private String path;

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

            try
            {
                DatasetImporter.submitStudyBatch(study, f, c, getUser(), getViewContext().getActionURL());
            }
            catch (StudyImporter.DatasetLockExistsException e)
            {
                ActionURL importURL = new ActionURL(ImportStudyBatchAction.class, getContainer());
                importURL.addParameter("path", form.getPath());
                return importURL;
            }

            return PageFlowUtil.urlProvider(PipelineStatusUrls.class).urlBegin(c);
        }
    }


    @RequiresPermission(ACL.PERM_ADMIN)
    public class ImportStudyAction extends FormViewAction<ImportForm>
    {
        private ActionURL redirect;

        public void validateCommand(ImportForm form, Errors errors)
        {
        }

        public ModelAndView getView(ImportForm form, boolean reshow, BindException errors) throws Exception
        {
            Container c = getContainer();
            Study study = StudyManager.getInstance().getStudy(c);

            if (!reshow && null != study)
            {
                return new HtmlView("Existing study: " + study.getLabel() + "<br><form method=\"post\">" + PageFlowUtil.generateSubmitButton("Delete Study") + "</form>");
            }
            else if (null == StudyReload.getPipelineRoot(c))
            {
                return new HtmlView("You must set a pipeline root before importing a study.<br>" + PageFlowUtil.generateButton("Pipeline Setup", PageFlowUtil.urlProvider(PipelineUrls.class).urlSetup(getContainer())));
            }
            else
            {
                return new JspView<ImportForm>("/org/labkey/study/view/importStudy.jsp", form, errors);
            }
        }

        public boolean handlePost(ImportForm form, BindException errors) throws Exception
        {
            Container c = getContainer();

            if (null != StudyManager.getInstance().getStudy(c))
            {
                return false;
            }

            File pipelineRoot = StudyReload.getPipelineRoot(c);

            if (null == pipelineRoot)
            {
                return false;   // getView() will show an appropriate message in this case
            }

            // Assuming success starting the import process, redirect to pipeline status
            redirect = PageFlowUtil.urlProvider(PipelineStatusUrls.class).urlBegin(c);

            if ("pipeline".equals(form.getSource()))
            {
                return importStudy(errors, pipelineRoot);
            }
            else
            {
                Map<String, MultipartFile> map = getFileMap();

                if (map.isEmpty())
                {
                    errors.reject("studyImport", "You must select a .zip file to import.");
                }
                else if (map.size() > 1)
                {
                    errors.reject("studyImport", "Only one file is allowed.");
                }
                else
                {
                    MultipartFile file = map.values().iterator().next();

                    if (0 == file.getSize() || StringUtils.isBlank(file.getOriginalFilename()))
                    {
                        errors.reject("studyImport", "You must select a .zip file to import.");
                    }
                    else
                    {
                        InputStream is = file.getInputStream();

                        File zipFile = File.createTempFile("study", ".zip");
                        FileOutputStream fos = new FileOutputStream(zipFile);

                        try
                        {
                            FileUtil.copyData(is, fos);
                        }
                        finally
                        {
                            if (is != null) try { is.close(); } catch (IOException e) {  }
                            try { fos.close(); } catch (IOException e) {  }
                        }

                        File importDir = new File(pipelineRoot, "unzip");
                        FileUtil.deleteDir(importDir);

                        try
                        {
                            ZipUtil.unzipToDirectory(zipFile, importDir);
                            importStudy(errors, importDir);
                        }
                        catch (ZipException e)
                        {
                            errors.reject("studyImport", "This file does not appear to be a valid .zip file.");
                        }
                    }
                }

                return !errors.hasErrors();
            }
        }

        public ActionURL getSuccessURL(ImportForm form)
        {
            return redirect;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Import Study");
        }
    }


    public static class ImportForm
    {
        private String _source;

        public String getSource()
        {
            return _source;
        }

        public void setSource(String source)
        {
            _source = source;
        }
    }


    public boolean importStudy(BindException errors, File root) throws ServletException, SQLException, IOException, ParserConfigurationException, SAXException, XmlException
    {
        Container c = getContainer();
        User user = getUser();
        ActionURL url = getViewContext().getActionURL();

        StudyImporter importer = new StudyImporter(c, user, url, root, errors);

        try
        {
            return importer.process();
        }
        catch (StudyImporter.StudyImportException e)
        {
            errors.reject("studyImport", e.getMessage());
            return false;
        }
    }


    @RequiresPermission(ACL.PERM_ADMIN)
    public class ExportStudyAction extends FormViewAction<ExportForm>
    {
        private ActionURL _successURL = null;

        public ModelAndView getView(ExportForm form, boolean reshow, BindException errors) throws Exception
        {
            // In export-to-browser case, base action will attempt to reshow the view since we returned null as the success
            // URL; returning null here causes the base action to stop pestering the action. 
            if (reshow)
                return null;

            return new JspView<ExportForm>("/org/labkey/study/view/exportStudy.jsp", form, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendManageStudy(root);
            return root.addChild("Export Study");
        }

        public void validateCommand(ExportForm form, Errors errors)
        {
        }

        public boolean handlePost(ExportForm form, BindException errors) throws Exception
        {
            StudyImpl study = getStudy();
            StudyWriter writer = new StudyWriter(form.getTypes());

            switch(form.getLocation())
            {
                case 0:
                {
                    File rootDir = PipelineService.get().findPipelineRoot(getContainer()).getRootPath();
                    File exportDir = new File(rootDir, "export");
                    writer.write(study, new ExportContext(getUser(), getContainer(), "old".equals(form.getFormat())), new FileSystemFile(exportDir));
                    _successURL = new ActionURL(ManageStudyAction.class, getContainer());
                    break;
                }
                case 1:
                {
                    File rootDir = PipelineService.get().findPipelineRoot(getContainer()).getRootPath();
                    File exportDir = new File(rootDir, "export");
                    exportDir.mkdir();
                    ZipFile zip = new ZipFile(exportDir, study.getLabel() + "_" + StudyPipeline.getTimestamp() + ".zip");
                    writer.write(study, new ExportContext(getUser(), getContainer(), "old".equals(form.getFormat())), zip);
                    zip.close();
                    _successURL = new ActionURL(ManageStudyAction.class, getContainer());
                    break;
                }
                case 2:
                {
                    ZipFile zip = new ZipFile(getViewContext().getResponse(), study.getLabel() + "_" + StudyPipeline.getTimestamp() + ".zip");
                    writer.write(study, new ExportContext(getUser(), getContainer(), "old".equals(form.getFormat())), zip);
                    zip.close();
                    break;
                }
            }

            return true;
        }

        public ActionURL getSuccessURL(ExportForm form)
        {
            return _successURL;
        }
    }

    public static class ExportForm
    {
        private String[] _types;
        private int _location;
        private String _format;

        public String[] getTypes()
        {
            return _types;
        }

        public void setTypes(String[] types)
        {
            _types = types;
        }

        public int getLocation()
        {
            return _location;
        }

        public void setLocation(int location)
        {
            _location = location;
        }

        public String getFormat()
        {
            return _format;
        }

        public void setFormat(String format)
        {
            _format = format;
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

    @RequiresPermission(ACL.PERM_READ)
    public class TypeNotFoundAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new StudyJspView<StudyImpl>(getStudy(), "typeNotFound.jsp", null, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Type Not Found");
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class UpdateParticipantVisitsAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            StudyManager.getInstance().recomputeStudyDataVisitDate(getStudy());
            StudyManager.getInstance().getVisitManager(getStudy()).updateParticipantVisits(getUser());

            TableInfo tinfoParticipantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();
            Integer visitDates = Table.executeSingleton(StudySchema.getInstance().getSchema(),
                    "SELECT Count(VisitDate) FROM " + tinfoParticipantVisit + "\nWHERE Container = ?",
                    new Object[] {getContainer()}, Integer.class);
            int count = null == visitDates ? 0 : visitDates.intValue();

            HttpView view = new HtmlView(
                    "<div>" + count + " rows were updated.<p/>" +
                    PageFlowUtil.generateButton("Done", "manageVisits.view") +
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
                    VisitImpl visit = StudyManager.getInstance().getVisitForRowId(getStudy(), id);
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
                VisitImpl def = StudyManager.getInstance().getVisitForRowId(getStudy(), allIds[i]);
                boolean show = visible.contains(allIds[i]);
                String label = form.getLabel()[i];
                String typeStr = form.getExtraData()[i];
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
            Map<Integer, DatasetVisibilityData> bean = new HashMap<Integer,DatasetVisibilityData>();
            DataSet[] defs = getStudy().getDataSets();
            for (DataSet def : defs)
            {
                DatasetVisibilityData data = new DatasetVisibilityData();
                data.label = def.getLabel();
                data.category = def.getCategory();
                data.cohort = def.getCohortId();
                data.visible = def.isShowByDefault();
                bean.put(def.getDataSetId(), data);
            }

            // Merge with form data
            int[] ids = form.getIds();
            if (ids != null)
            {
                String[] labels = form.getLabel();
                int[] visibleIds = form.getVisible();
                if (visibleIds == null)
                    visibleIds = new int[0];
                Set<Integer> visible = new HashSet<Integer>(visibleIds.length);
                for (int id : visibleIds)
                    visible.add(id);
                int[] cohorts = form.getCohort();
                if (cohorts == null)
                    cohorts = new int[ids.length];
                String[] categories = form.getExtraData();

                for (int i=0; i<ids.length; i++)
                {
                    int id = ids[i];
                    DatasetVisibilityData data = bean.get(id);
                    data.label = labels[i];
                    data.category = categories[i];
                    data.cohort = cohorts[i] == -1 ? null : cohorts[i];
                    data.visible = visible.contains(id);
                }
            }
            return new StudyJspView<Map<Integer,StudyController.DatasetVisibilityData>>(
                    getStudy(), "dataSetVisibility.jsp", bean, errors);
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
            // Check for bad labels
            Set<String> labels = new HashSet<String>();
            for (String label : form.getLabel())
            {
                if (label == null)
                {
                    errors.reject("datasetVisibility", "Label cannot be blank");
                    return false;
                }
                if (labels.contains(label))
                {
                    errors.reject("datasetVisibility", "Labels must be unique. Found two or more labels called '" + label + "'.");
                    return false;
                }
                labels.add(label);
            }

            int[] allIds = form.getIds();
            if (allIds == null)
                allIds = new int[0];
            int[] visibleIds = form.getVisible();
            if (visibleIds == null)
                visibleIds = new int[0];
            Set<Integer> visible = new HashSet<Integer>(visibleIds.length);
            for (int id : visibleIds)
                  visible.add(id);
            for (int i = 0; i < allIds.length; i++)
            {
                DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(getStudy(), allIds[i]);
                boolean show = visible.contains(allIds[i]);
                String[] extraData = form.getExtraData();
                String category = extraData == null ? null : extraData[i];
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

    // Bean will be an map of these
    public static class DatasetVisibilityData
    {
        public String label;
        public String category;
        public Integer cohort; // null for none
        public boolean visible;
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
                String[] ids = order.split(",");
                List<Integer> orderedIds = new ArrayList<Integer>(ids.length);

                for (String id : ids)
                    orderedIds.add(Integer.parseInt(id));

                DatasetReorderer reorderer = new DatasetReorderer(getStudy(), getUser());
                reorderer.reorderDatasets(orderedIds);
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
            Set<String> categoryAndSourceToSnapshot;
            if (null == form.getCategoryAndSourceToSnapshot() || form.getCategoryAndSourceToSnapshot().length == 0)
            {
                errors.reject("manageSnapshot", "No datasets selected. Please select datasets to snapshot.");
                categoryAndSourceToSnapshot = Collections.emptySet();
            }
            else
                categoryAndSourceToSnapshot = new HashSet<String>(Arrays.asList(form.getCategoryAndSourceToSnapshot()));

            String[] destName = form.getDestName();
            for (int i = 0; i < category.length; i++)
            {
                boolean shouldSnapshot = categoryAndSourceToSnapshot.contains(category[i] + ";" + source[i]);
                snapshotBean.setSnapshot(category[i], source[i], shouldSnapshot);
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
            "    var dataType = 'ALL';\n" +
            "    /* Additional options for dataType 'DEMOGRAPHIC' or 'NON_DEMOGRAPHIC'. */" +
            "\n" +
            "    var QCState = LABKEY.ActionURL.getParameter('QCState');\n" +
            "\n" +
            "    /* create the participant details webpart: */\n" +
            "    var participantWebPart = new LABKEY.WebPart({\n" +
            "    partName: 'Participant Details',\n" +
            "    renderTo: 'participantData',\n" +
            "    frame : 'false',\n" +
            "    partConfig: {\n" +
            "        participantId: participantId,\n" +
            "        datasetId: datasetId,\n" +
            "        dataType: dataType,\n" +
            "        QCState: QCState,\n" +
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
            root.addChild("Manage Views", new ActionURL(ReportsController.ManageReportsAction.class, getContainer()));
            return root.addChild("Customize Participant View");
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class QuickCreateStudyAction extends ApiAction<SimpleApiJsonForm>
    {
        public ApiResponse execute(SimpleApiJsonForm simpleApiJsonForm, BindException errors) throws Exception
        {
            JSONObject json = simpleApiJsonForm.getJsonObject();
            if (!json.has("name"))
                throw new IllegalArgumentException("name is a required attribute.");

            String folderName  = json.getString("name");
            String startDateStr;
            if (json.has("startDate"))
                startDateStr = json.getString("startDate");
            else
                startDateStr = DateUtil.formatDate();
            Date startDate = new Date(DateUtil.parseDateTime(startDateStr));

            String cohortDatasetName = json.getString("cohortDataset");
            String cohortProperty = json.getString("cohortProperty");
            if (null != cohortDatasetName && null == cohortProperty)
                throw new IllegalArgumentException("Specified cohort dataset, but not property");

            JSONArray visits = null;
            if (json.has("visits"))
                visits = json.getJSONArray("visits");

            JSONArray jsonDatasets = null;
            if (json.has("dataSets"))
            {
                boolean hasCohortDataset = false;
                jsonDatasets = json.getJSONArray("dataSets");
                for (JSONObject jdataset : jsonDatasets.toJSONObjectArray())
                {
                    if (!jdataset.has("name"))
                        throw new IllegalArgumentException("Dataset name required.");

                    if (jdataset.get("name").equals(cohortDatasetName))
                        hasCohortDataset = true;
                }

                if (null != cohortDatasetName && !hasCohortDataset)
                    throw new IllegalArgumentException("Couldn't find cohort dataset");
            }


            JSONArray jsonWebParts = null;
            if (json.has("webParts"))
                jsonWebParts = json.getJSONArray("webParts");


            Container parent = getContainer();
            Container studyFolder = parent.getChild(folderName);
            if (null == studyFolder)
                studyFolder = ContainerManager.createContainer(parent, folderName);
            if (null != StudyManager.getInstance().getStudy(studyFolder))
                throw new IllegalStateException("Study already exists in folder");

            SecurityManager.setInheritPermissions(studyFolder);
            studyFolder.setFolderType(ModuleLoader.getInstance().getFolderType(StudyFolderType.NAME));

            StudyImpl study = new StudyImpl(studyFolder, folderName + " Study");
            study.setDateBased(true);
            study.setStartDate(startDate);
            study = StudyManager.getInstance().createStudy(getUser(), study);

            if (null != visits)
                for (JSONObject obj : visits.toJSONObjectArray())
                {
                    VisitImpl visit = new VisitImpl(studyFolder, obj.getDouble("minDays"), obj.getDouble("maxDays"), obj.getString("label"), Visit.Type.REQUIRED_BY_TERMINATION);
                    StudyManager.getInstance().createVisit(study, getUser(), visit);
                }


            DbScope scope = StudySchema.getInstance().getSchema().getScope();
            boolean ownsTransaction = !scope.isTransactionActive();

            List<DataSetDefinition> datasets = new ArrayList<DataSetDefinition>();
            if (null != jsonDatasets)
            {
                try
                {
                    if (ownsTransaction)
                        scope.beginTransaction();
                    for (JSONObject jdataset : jsonDatasets.toJSONObjectArray())
                    {
                        DataSetDefinition dataset = AssayPublishManager.getInstance().createAssayDataset(getUser(), study, jdataset.getString("name"),
                                jdataset.getString("keyPropertyName"),
                                jdataset.has("id") ? jdataset.getInt("id") : null,
                                jdataset.has("demographicData") && jdataset.getBoolean("demographicData"),
                                null);

                        if (jdataset.has("keyPropertyManaged") && jdataset.getBoolean("keyPropertyManaged"))
                        {
                            dataset = dataset.createMutable();
                            dataset.setKeyPropertyManaged(true);
                            StudyManager.getInstance().updateDataSetDefinition(getUser(), dataset);
                        }

                        if (dataset.getName().equals(cohortDatasetName))
                        {
                            study = study.createMutable();
                            study.setParticipantCohortDataSetId(dataset.getDataSetId());
                            study.setParticipantCohortProperty(cohortProperty);
                            StudyManager.getInstance().updateStudy(getUser(), study);
                        }

                        OntologyManager.ensureDomainDescriptor(dataset.getTypeURI(), dataset.getName(), study.getContainer());
                        datasets.add(dataset);
                    }
                    if (ownsTransaction)
                        scope.commitTransaction();
                }
                finally
                {
                    if (ownsTransaction)
                        scope.closeConnection();
                }
            }

            if (null != jsonWebParts)
            {
                List<Portal.WebPart> webParts = new ArrayList<Portal.WebPart>();
                for (JSONObject obj : jsonWebParts.toJSONObjectArray())
                {
                    WebPartFactory factory = Portal.getPortalPartCaseInsensitive(obj.getString("partName"));
                    if (null == factory)
                        continue; //Silently ignore
                    String location = obj.getString("location");
                    if (null == location || "body".equals(location))
                        location = HttpView.BODY;
                    JSONObject partConfig = null;
                    if (obj.has("partConfig"))
                        partConfig = obj.getJSONObject("partConfig");

                    Portal.WebPart part = factory.createWebPart();
                    part.setLocation(location);
                    if (null != partConfig)
                    {
                        for (Map.Entry<String,Object> entry : partConfig.entrySet())
                            part.setProperty(entry.getKey(), entry.getValue().toString());
                    }

                    webParts.add(part);
                }
                Portal.saveParts(studyFolder.getId(), webParts.toArray(new Portal.WebPart[webParts.size()]));
            }

            ApiSimpleResponse response = new ApiSimpleResponse();
            response.put("label", study.getLabel());
            response.put("containerId", study.getContainer().getId());
            response.put("containerPath", study.getContainer().getPath());
            response.putBeanList("dataSets", datasets, "name", "typeURI", "dataSetId");


            return response;
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

    @RequiresPermission(ACL.PERM_ADMIN)
    public class CreateSnapshotAction extends FormViewAction<StudySnapshotForm>
    {
        ActionURL _successURL;

        public void validateCommand(StudySnapshotForm form, Errors errors)
        {
            if (StudySnapshotForm.CANCEL.equals(form.getAction()))
                return;
            
            String name = StringUtils.trimToNull(form.getSnapshotName());

            if (name != null)
            {
                QuerySnapshotDefinition def = QueryService.get().getSnapshotDef(getContainer(), form.getSchemaName().toString(), name);
                if (def != null)
                {
                    errors.reject("snapshotQuery.error", "A Snapshot with the same name already exists");
                    return;
                }

                // check for a dataset with the same name unless it's one that we created
                DataSet dataset = StudyManager.getInstance().getDataSetDefinition(StudyManager.getInstance().getStudy(getContainer()), name);
                if (dataset != null)
                {
                    if (dataset.getDataSetId() != form.getSnapshotDatasetId())
                        errors.reject("snapshotQuery.error", "A Dataset with the same name already exists");
                }
            }
            else
                errors.reject("snapshotQuery.error", "The Query Snapshot name cannot be blank");
        }

        public ModelAndView getView(StudySnapshotForm form, boolean reshow, BindException errors) throws Exception
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
                StudyImpl study = getStudy();
                DataSet dsDef = StudyManager.getInstance().getDataSetDefinition(study, form.getSnapshotName());

                ActionURL url = getViewContext().cloneActionURL().replaceParameter("ff_snapshotName", form.getSnapshotName()).
                        replaceParameter("ff_updateDelay", String.valueOf(form.getUpdateDelay())).
                        replaceParameter("ff_snapshotDatasetId", String.valueOf(form.getSnapshotDatasetId()));

                if (dsDef == null)
                    return HttpView.throwNotFound("Unable to edit the created DataSet Definition");

                Map<String,String> props = PageFlowUtil.map(
                        "studyId", String.valueOf(study.getRowId()),
                        "datasetId", String.valueOf(dsDef.getDataSetId()),
                        "typeURI", dsDef.getTypeURI(),
                        "dateBased", String.valueOf(study.isDateBased()),
                        "returnURL", url.getLocalURIString(),
                        "cancelURL", url.getLocalURIString(),
                        "create", "false");

                HtmlView text = new HtmlView("Modify the properties and schema (form fields/properties) for this dataset.");
                HttpView view = new GWTView("org.labkey.study.dataset.Designer", props);

                // hack for 4404 : Lookup picker performance is terrible when there are many containers
                ContainerManager.getAllChildren(ContainerManager.getRoot());

                return new VBox(text, view);
            }
            return null;
        }

        private void deletePreviousDatasetDefinition(StudySnapshotForm form) throws SQLException
        {
            if (form.getSnapshotDatasetId() != -1)
            {
                Study study = StudyManager.getInstance().getStudy(getContainer());

                // a dataset definition was edited previously, but under a different name, need to delete the old one
                DataSetDefinition dsDef = StudyManager.getInstance().getDataSetDefinition(study, form.getSnapshotDatasetId());
                if (dsDef != null)
                {
                    StudyManager.getInstance().deleteDataset(study, getUser(), dsDef);
                    form.setSnapshotDatasetId(-1);
                }
            }
        }

        private void createDataset(StudySnapshotForm form, BindException errors) throws Exception
        {
            StudyImpl study = StudyManager.getInstance().getStudy(getContainer());
            DataSet dsDef = StudyManager.getInstance().getDataSetDefinition(study, form.getSnapshotName());

            if (dsDef == null)
            {
                deletePreviousDatasetDefinition(form);

                // if this snapshot is being created from an existing dataset, copy key field settings
                int datasetId = NumberUtils.toInt(getViewContext().getActionURL().getParameter(DataSetDefinition.DATASETKEY), -1);
                String additionalKey = null;
                boolean isKeyManaged = false;
                boolean isDemographicData = false;

                if (datasetId != -1)
                {
                    DataSetDefinition sourceDef = study.getDataSet(datasetId);
                    if (sourceDef != null)
                    {
                        additionalKey = sourceDef.getKeyPropertyName();
                        isKeyManaged = sourceDef.isKeyPropertyManaged();
                        isDemographicData = sourceDef.isDemographicData();
                    }
                }
                DataSetDefinition def = AssayPublishManager.getInstance().createAssayDataset(getUser(),
                        study, form.getSnapshotName(), additionalKey, null, isDemographicData, null);
                if (def != null)
                {
                    form.setSnapshotDatasetId(def.getDataSetId());
                    if (isKeyManaged)
                    {
                        def = def.createMutable();
                        def.setKeyPropertyManaged(true);

                        StudyManager.getInstance().updateDataSetDefinition(getUser(), def);
                    }

                    String domainURI = def.getTypeURI();
                    OntologyManager.ensureDomainDescriptor(domainURI, form.getSnapshotName(), form.getViewContext().getContainer());
                    Domain d = PropertyService.get().getDomain(form.getViewContext().getContainer(), domainURI);

                    for (DisplayColumn dc : QuerySnapshotService.get(form.getSchemaName()).getDisplayColumns(form))
                    {
                        ColumnInfo col = dc.getColumnInfo();
                        if (!DataSetDefinition.isDefaultFieldName(col.getName(), study))
                            DatasetSnapshotProvider.addAsDomainProperty(d, col);
                    }
                    d.save(getUser());
                }
            }
        }

        public boolean handlePost(StudySnapshotForm form, BindException errors) throws Exception
        {
            List<String> errorList = new ArrayList<String>();

            DbSchema schema = StudyManager.getSchema();
            boolean startedTransaction = false;

            try {
                if (!schema.getScope().isTransactionActive())
                {
                    schema.getScope().beginTransaction();
                    startedTransaction = true;
                }

                if (StudySnapshotForm.EDIT_DATASET.equals(form.getAction()))
                {
                    createDataset(form, errors);
                }
                else if (StudySnapshotForm.CREATE_SNAPSHOT.equals(form.getAction()))
                {
                    createDataset(form, errors);
                    if (!errors.hasErrors())
                        _successURL = QuerySnapshotService.get(form.getSchemaName()).createSnapshot(form, errorList);
                }
                else if (StudySnapshotForm.CANCEL.equals(form.getAction()))
                {
                    deletePreviousDatasetDefinition(form);
                    String redirect = getViewContext().getActionURL().getParameter("redirectURL");
                    if (redirect != null)
                        _successURL = new ActionURL(PageFlowUtil.decode(redirect));
                }

                if (startedTransaction)
                    schema.getScope().commitTransaction();
            }
            finally
            {
                if (startedTransaction && schema.getScope().isTransactionActive())
                    schema.getScope().rollbackTransaction();
            }

            if (!errorList.isEmpty())
            {
                for (String error : errorList)
                    errors.reject("snapshotQuery.error", error);
                return false;
            }
            return true;
        }

        public ActionURL getSuccessURL(StudySnapshotForm queryForm)
        {
            return _successURL;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Create Query Snapshot");
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class EditSnapshotAction extends FormViewAction<StudySnapshotForm>
    {
        ActionURL _successURL;

        public void validateCommand(StudySnapshotForm form, Errors errors)
        {
        }

        public ModelAndView getView(StudySnapshotForm form, boolean reshow, BindException errors) throws Exception
        {
            form.setEdit(true);
            if (!reshow)
                form.init(QueryService.get().getSnapshotDef(getContainer(), form.getSchemaName().toString(), form.getSnapshotName()), getUser());

            VBox box = new VBox();
            QuerySnapshotService.I provider = QuerySnapshotService.get(form.getSchemaName());

            if (provider != null)
            {
                boolean showHistory = BooleanUtils.toBoolean(getViewContext().getActionURL().getParameter("showHistory"));
                boolean showDataset = BooleanUtils.toBoolean(getViewContext().getActionURL().getParameter("showDataset"));

                box.addView(new JspView<QueryForm>("/org/labkey/study/view/editSnapshot.jsp", form));
                box.addView(new JspView<QueryForm>("/org/labkey/study/view/createDatasetSnapshot.jsp", form, errors));

                if (showHistory)
                {
                    HttpView historyView = provider.createAuditView(form);
                    if (historyView != null)
                        box.addView(historyView);
                }

                if (showDataset)
                {
                    StudyImpl study = getStudy();
                    DataSet dsDef = StudyManager.getInstance().getDataSetDefinition(study, form.getSnapshotName());

                    if (dsDef == null)
                        return HttpView.throwNotFound("Unable to edit the created DataSet Definition");

                    ActionURL returnURL = getViewContext().cloneActionURL().replaceParameter("showDataset", "0");
                    Map<String,String> props = PageFlowUtil.map(
                            "studyId", String.valueOf(study.getRowId()),
                            "datasetId", String.valueOf(dsDef.getDataSetId()),
                            "typeURI", dsDef.getTypeURI(),
                            "dateBased", "false",
                            "returnURL", returnURL.toString(),
                            "cancelURL", returnURL.toString(),
                            "create", "false");

                    HtmlView text = new HtmlView("Modify the properties and schema (form fields/properties) for this dataset.<br>Click the save button to " +
                            "save any changes, else click the cancel button to complete the snapshot.");
                    HttpView view = new GWTView("org.labkey.study.dataset.Designer", props);

                    // hack for 4404 : Lookup picker performance is terrible when there are many containers
                    ContainerManager.getAllChildren(ContainerManager.getRoot());

                    box.addView(text);
                    box.addView(view);
                }
            }
            return box;
        }

        public boolean handlePost(StudySnapshotForm form, BindException errors) throws Exception
        {
            List<String> errorList = new ArrayList<String>();

            if (StudySnapshotForm.CANCEL.equals(form.getAction()))
            {
                String redirect = getViewContext().getActionURL().getParameter("redirectURL");
                if (redirect != null)
                    _successURL = new ActionURL(PageFlowUtil.decode(redirect));
            }
            else if (form.isUpdateSnapshot())
            {
                _successURL = QuerySnapshotService.get(form.getSchemaName()).updateSnapshot(form, errorList);

                if (!errorList.isEmpty())
                {
                    for (String error : errorList)
                        errors.reject(SpringActionController.ERROR_MSG, error);
                    return false;
                }
            }
            else
            {
                QuerySnapshotDefinition def = QueryService.get().getSnapshotDef(getContainer(), form.getSchemaName().toString(), form.getSnapshotName());
                if (def != null)
                {
                    def.setUpdateDelay(form.getUpdateDelay());
                    _successURL = QuerySnapshotService.get(form.getSchemaName()).updateSnapshotDefinition(getViewContext(), def, errorList);
                    if (!errorList.isEmpty())
                    {
                        for (String error : errorList)
                            errors.reject(SpringActionController.ERROR_MSG, error);
                        return false;
                    }
                }
                else
                {
                    errors.reject("snapshotQuery.error", "Unable to create QuerySnapshotDefinition");
                    return false;
                }
            }
            return true;
        }

        public ActionURL getSuccessURL(StudySnapshotForm form)
        {
            return _successURL;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Edit Query Snapshot");
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
        private String[] categoryAndSourceToSnapshot; // format: "CATEGORY;SOURCE"
        
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

        public String[] getCategoryAndSourceToSnapshot()
        {
            return categoryAndSourceToSnapshot;
        }

        public void setCategoryAndSourceToSnapshot(String[] categoryAndSourceToSnapshot)
        {
            this.categoryAndSourceToSnapshot = categoryAndSourceToSnapshot;
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
        public RequirePipelineView(StudyImpl study, boolean showGoBack, BindException errors)
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
        private DataSet _def;

        public ViewPrefsBean(List<Pair<String, String>> views, DataSet def)
        {
            _views = views;
            _def = def;
        }

        public List<Pair<String, String>> getViews(){return _views;}
        public DataSet getDataSetDefinition(){return _def;}
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

    public static class ParticipantForm extends ViewFormData implements StudyManager.ParticipantViewConfig
    {
        private String participantId;
        private int datasetId;
        private double sequenceNum;
        private String action;
        private int reportId;
        private Integer cohortId;
        private String _qcState;
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

        public QCStateSet getQCStateSet()
        {
            if (_qcState != null && StudyManager.getInstance().showQCStates(getContainer()))
                return QCStateSet.getSelectedStates(getContainer(), getQCState());
            return null;
        }

        public void setRedirectUrl(String redirectUrl) { _redirectUrl = redirectUrl; }

        public String getQCState() { return _qcState; }

        public void setQCState(String qcState) { _qcState = qcState; }
    }


    public static class StudyPropertiesForm
    {
        private String _label;
        private boolean _dateBased;
        private Date _startDate;
        private boolean _simpleRepository = true;
        private SecurityType _securityType;

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
                out.print("<table>");
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
            final int datasetId = Integer.parseInt(descriptor.getProperty(DataSetDefinition.DATASETKEY));
            final Study study = StudyManager.getInstance().getStudy(context.getContainer());
            DataSet def = StudyManager.getInstance().getDataSetDefinition(study, datasetId);

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
        private String _encodedQcState;
        private boolean _showCustomizeLink = true;

        public ParticipantNavView(String prevURL, String nextURL, String currentPartitipantId, String encodedQCState, String display)
        {
            _prevURL = prevURL;
            _nextURL = nextURL;
            _display = display;
            _currentParticipantId = currentPartitipantId;
            _encodedQcState = encodedQCState;
        }

        public ParticipantNavView(String prevURL, String nextURL, String currentPartitipantId, String encodedQCState)
        {
            this(prevURL, nextURL, currentPartitipantId,  encodedQCState, null);
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
            if (_showCustomizeLink && container.hasPermission(getViewContext().getUser(), ACL.PERM_ADMIN))
            {
                ActionURL customizeURL = new ActionURL(CustomizeParticipantViewAction.class, container);
                customizeURL.addParameter("returnUrl", getViewContext().getActionURL().getLocalURIString());
                customizeURL.addParameter("participantId", _currentParticipantId);
                customizeURL.addParameter(SharedFormParameters.QCState, _encodedQcState);
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

    public static class ImportDataSetForm extends ViewFormData
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

    public static class DataSetForm extends ViewFormData
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

    @RequiresPermission(ACL.PERM_READ)
    public class DatasetsAction extends SimpleViewAction<Object>
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return StudyModule.datasetsPartFactory.getWebPartView(getViewContext(), StudyModule.datasetsPartFactory.createWebPart());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            try
            {
                Study study = getStudy();
                if (study != null)
                    root.addChild(study.getLabel(), new ActionURL(BeginAction.class, getContainer()));
            }
            catch (ServletException e)
            {
            }
            root.addChild("Datasets");
            return root;
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class SamplesAction extends SimpleViewAction<Object>
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return StudyModule.samplesWidePartFactory.getWebPartView(getViewContext(), StudyModule.samplesWidePartFactory.createWebPart());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            try
            {
                Study study = getStudy();
                if (study != null)
                    root.addChild(study.getLabel(), new ActionURL(BeginAction.class, getContainer()));
            }
            catch (ServletException e)
            {
            }
            root.addChild("Samples");
            return root;
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class ViewDataAction extends SimpleViewAction<Object>
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new VBox(
                StudyModule.reportsWidePartFactory.getWebPartView(getViewContext(), StudyModule.reportsPartFactory.createWebPart()),
                StudyModule.datasetsPartFactory.getWebPartView(getViewContext(), StudyModule.datasetsPartFactory.createWebPart())
            );
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class ManageReloadAction extends FormViewAction<ReloadForm>
    {
        public void validateCommand(ReloadForm target, Errors errors)
        {
        }

        public ModelAndView getView(ReloadForm form, boolean reshow, BindException errors) throws Exception
        {
            return new StudyJspView<ReloadForm>(getStudy(), "manageReload.jsp", form, errors);
        }

        public boolean handlePost(ReloadForm form, final BindException errors) throws Exception
        {
            StudyImpl study = getStudy();

            // If the "allow reload" state or the interval changes then update the study and initialize the timer
            if (form.isAllowReload() != study.isAllowReload() || !nullSafeEqual(form.getInterval(), study.getReloadInterval()))
            {
                study = study.createMutable();
                study.setAllowReload(form.isAllowReload());
                study.setReloadInterval(0 != form.getInterval() ? form.getInterval() : null);
                study.setReloadUser(getUser().getUserId());
                study.setLastReload(new Date());
                StudyManager.getInstance().updateStudy(getUser(), study);
                StudyReload.initializeTimer(study);
            }

            return true;
        }

        public ActionURL getSuccessURL(ReloadForm reloadForm)
        {
            return new ActionURL(StudyController.ManageStudyAction.class, getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendManageStudy(root);
            return root.addChild("Manage Reloading");
        }
    }


    public static class ReloadForm
    {
        private boolean allowReload = false;
        private int interval = 0;
        private boolean _ui = false;

        public boolean isAllowReload()
        {
            return allowReload;
        }

        public void setAllowReload(boolean allowReload)
        {
            this.allowReload = allowReload;
        }

        public int getInterval()
        {
            return interval;
        }

        public void setInterval(int interval)
        {
            this.interval = interval;
        }

        public boolean isUi()
        {
            return _ui;
        }

        public void setUi(boolean ui)
        {
            _ui = ui;
        }
    }


    @RequiresPermission(ACL.PERM_NONE)
    public class CheckForReload extends ManageReloadAction    // Subclassing makes it easier to redisplay errors, etc.
    {
        @Override
        public ModelAndView getView(ReloadForm form, boolean reshow, BindException errors) throws Exception
        {
            ReloadTask task = new ReloadTask(getContainer().getId());
            String message;

            try
            {
                ReloadStatus status = task.attemptReload();

                if (status.isReloadQueued() && form.isUi())
                    return HttpView.redirect(PageFlowUtil.urlProvider(PipelineUrls.class).urlBegin(getContainer()));

                message = status.getMessage();
            }
            catch (StudyImporter.StudyImportException e)
            {
                message = "Error: " + e.getMessage();
            }

            // If this was initiated from the UI and reload was not queued up then reshow the form and display the message
            if (form.isUi())
            {
                errors.reject(ERROR_MSG, message);
                return super.getView(form, false, errors);
            }
            else
            {
                // Plain text response for scripts
                HttpServletResponse response = getViewContext().getResponse();
                response.setContentType("text/plain");
                PrintWriter out = response.getWriter();
                out.print(message);
                out.close();
                response.flushBuffer();

                return null;
            }
        }
    }


    @Override
    public AppBar getAppBar(Controller action)
    {
        try
        {
            Study study = getStudy(true);
            if (study == null)
            {
                List<NavTree> buttons;
                if (getViewContext().hasPermission(ACL.PERM_ADMIN))
                    buttons = Collections.singletonList(new NavTree("Create Study", new ActionURL(ShowCreateStudyAction.class, getContainer())));
                else
                    buttons = Collections.emptyList();
                return new AppBar("Study: None", buttons);
            }
            else
            {
                List<NavTree> buttons = new ArrayList<NavTree>();
                buttons.add(new NavTree("Overview", PageFlowUtil.urlProvider(ProjectUrls.class).getStartURL(getContainer())));
                buttons.add(new NavTree("Participants", "#"));
                buttons.add(new NavTree("View Data", new ActionURL(ViewDataAction.class, getContainer())));
                if (getContainer().hasPermission(getUser(), ACL.PERM_ADMIN))
                    buttons.add(new NavTree("Manage", new ActionURL(ManageStudyAction.class, getContainer())));

                return new AppBar("Study: " + study.getLabel(), buttons);
            }
        }
        catch (ServletException e)
        {
            _log.error("getAppBar", e);
            return null;
        }
    }

    public static class TSVForm
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
}
