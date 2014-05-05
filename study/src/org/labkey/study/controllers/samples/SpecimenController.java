/*
 * Copyright (c) 2008-2014 LabKey Corporation
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

package org.labkey.study.controllers.samples;

import gwt.client.org.labkey.study.StudyApplication;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.GWTServiceAction;
import org.labkey.api.action.HasViewContext;
import org.labkey.api.action.QueryViewAction;
import org.labkey.api.action.RedirectAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleRedirectAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentForm;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.ByteArrayAttachmentFile;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.BeanViewForm;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.ExcelColumn;
import org.labkey.api.data.ExcelWriter;
import org.labkey.api.data.MenuButton;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TSVGridWriter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusUrls;
import org.labkey.api.pipeline.browse.PipelinePathForm;
import org.labkey.api.query.AbstractQueryImportAction;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateForm;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.UserSchemaAction;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.User;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.EditSpecimenDataPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.Location;
import org.labkey.api.study.SamplesUrls;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.Visit;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.GUID;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.ReturnURLString;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.*;
import org.labkey.study.CohortFilter;
import org.labkey.study.CohortFilterFactory;
import org.labkey.study.SampleManager;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.BaseStudyController;
import org.labkey.study.controllers.DatasetController;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.designer.MapArrayExcelWriter;
import org.labkey.study.importer.RequestabilityManager;
import org.labkey.study.importer.SimpleSpecimenImporter;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.ExtendedSpecimenRequestView;
import org.labkey.study.model.LocationImpl;
import org.labkey.study.model.ParticipantDataset;
import org.labkey.study.model.SampleRequest;
import org.labkey.study.model.SampleRequestActor;
import org.labkey.study.model.SampleRequestEvent;
import org.labkey.study.model.SampleRequestRequirement;
import org.labkey.study.model.SampleRequestStatus;
import org.labkey.study.model.SecurityType;
import org.labkey.study.model.Specimen;
import org.labkey.study.model.SpecimenComment;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.pipeline.SpecimenArchive;
import org.labkey.study.pipeline.SpecimenBatch;
import org.labkey.study.query.DataSetQuerySettings;
import org.labkey.study.query.DataSetQueryView;
import org.labkey.study.query.SpecimenDetailTable;
import org.labkey.study.query.SpecimenEventQueryView;
import org.labkey.study.query.SpecimenQueryView;
import org.labkey.study.query.SpecimenRequestQueryView;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.study.requirements.RequirementProvider;
import org.labkey.study.requirements.SpecimenRequestRequirementType;
import org.labkey.study.samples.SampleSearchWebPart;
import org.labkey.study.samples.SamplesWebPart;
import org.labkey.study.samples.notifications.ActorNotificationRecipientSet;
import org.labkey.study.samples.notifications.DefaultRequestNotification;
import org.labkey.study.samples.notifications.NotificationRecipientSet;
import org.labkey.study.samples.report.SpecimenReportExcelWriter;
import org.labkey.study.samples.report.SpecimenVisitReportParameters;
import org.labkey.study.samples.report.participant.ParticipantSiteReportFactory;
import org.labkey.study.samples.report.participant.ParticipantSummaryReportFactory;
import org.labkey.study.samples.report.participant.ParticipantTypeReportFactory;
import org.labkey.study.samples.report.request.RequestEnrollmentSiteReportFactory;
import org.labkey.study.samples.report.request.RequestLocationReportFactory;
import org.labkey.study.samples.report.request.RequestParticipantReportFactory;
import org.labkey.study.samples.report.request.RequestReportFactory;
import org.labkey.study.samples.report.specimentype.TypeCohortReportFactory;
import org.labkey.study.samples.report.specimentype.TypeParticipantReportFactory;
import org.labkey.study.samples.report.specimentype.TypeSummaryReportFactory;
import org.labkey.study.samples.settings.DisplaySettings;
import org.labkey.study.samples.settings.RepositorySettings;
import org.labkey.study.samples.settings.RequestNotificationSettings;
import org.labkey.study.samples.settings.StatusSettings;
import org.labkey.study.security.permissions.ManageDisplaySettingsPermission;
import org.labkey.study.security.permissions.ManageNewRequestFormPermission;
import org.labkey.study.security.permissions.ManageNotificationsPermission;
import org.labkey.study.security.permissions.ManageRequestRequirementsPermission;
import org.labkey.study.security.permissions.ManageRequestSettingsPermission;
import org.labkey.study.security.permissions.ManageRequestStatusesPermission;
import org.labkey.study.security.permissions.ManageRequestsPermission;
import org.labkey.study.security.permissions.ManageSpecimenActorsPermission;
import org.labkey.study.security.permissions.ManageStudyPermission;
import org.labkey.study.security.permissions.RequestSpecimensPermission;
import org.labkey.study.security.permissions.SetSpecimenCommentsPermission;
import org.labkey.study.view.StudyGWTView;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.validation.ObjectError;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletException;
import javax.servlet.http.HttpSession;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * User: brittp
 * Date: Dec 20, 2007
 * Time: 11:08:31 AM
 */
public class SpecimenController extends BaseStudyController
{
    private static final Logger _log = Logger.getLogger(SpecimenController.class);

    private static DefaultActionResolver _actionResolver = new DefaultActionResolver(SpecimenController.class,
            ShowUploadSpecimensAction.class,
            ShowUploadSpecimensAction.ImportCompleteAction.class,
            ShowGroupMembersAction.class,
            ShowSearchAction.class,
            AutoCompleteAction.class,
            ParticipantCommentAction.SpecimenCommentInsertAction.class,
            ParticipantCommentAction.SpecimenCommentUpdateAction.class
    );

    public SpecimenController()
    {
        setActionResolver(_actionResolver);
    }

    public static class SamplesUrlsImpl implements SamplesUrls
    {
        @Override
        public ActionURL getSamplesURL(Container c)
        {
            return SpecimenController.getSamplesURL(c);
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class OverviewAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object form, BindException errors) throws Exception
        {
            if (null == StudyService.get().getStudy(getContainer()))
                return new HtmlView("This folder does not contain a study.");
            SampleSearchWebPart sampleSearch = new SampleSearchWebPart(true);
            SamplesWebPart sampleSummary = new SamplesWebPart(true, (StudyImpl)StudyService.get().getStudy(getContainer()));
            return new VBox(sampleSummary, sampleSearch);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return appendBaseSpecimenNavTrail(root);
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class BeginAction extends SimpleRedirectAction
    {
        public ActionURL getRedirectURL(Object o)
        {
            return getSamplesURL();
        }
    }

    private NavTree appendBaseSpecimenNavTrail(NavTree root)
    {
        root = _appendNavTrail(root);
        ActionURL overviewURL = new ActionURL(OverviewAction.class,  getContainer());
        root.addChild("Specimen Overview", overviewURL);
        return root;
    }

    private NavTree appendSpecimenRequestsNavTrail(NavTree root)
    {
        root = appendBaseSpecimenNavTrail(root);
        return root.addChild("Specimen Requests", new ActionURL(ViewRequestsAction.class, getContainer()));
    }

    private NavTree appendSpecimenRequestNavTrail(NavTree root, int requestId)
    {
        root = appendSpecimenRequestsNavTrail(root);
        return root.addChild("Specimen Request " + requestId, getManageRequestURL(requestId));
    }


    private ActionURL getManageRequestURL(int requestID)
    {
        return getManageRequestURL(requestID, null);
    }

    private ActionURL getManageRequestURL(int requestID, ReturnURLString returnUrl)
    {
        ActionURL url = new ActionURL(ManageRequestAction.class, getContainer());
        url.addParameter(IdForm.PARAMS.id, Integer.toString(requestID));
        if (returnUrl != null)
            url.addParameter(ManageRequestForm.PARAMS.returnUrl, returnUrl);
        return url;
    }

    private ActionURL getExtendedRequestURL(int requestID, ReturnURLString returnUrl)
    {
        ActionURL url = new ActionURL(ExtendedSpecimenRequestAction.class, getContainer());
        url.addParameter(IdForm.PARAMS.id, Integer.toString(requestID));
        if (returnUrl != null)
            url.addParameter(ManageRequestForm.PARAMS.returnUrl, returnUrl);
        return url;
    }

    private Set<String> getSelectionLsids() throws ServletException
    {
        // save the selected set of participantdataset lsids in the session; this is the only obvious way
        // to let the user apply subsequent filters and switch back and forth between vial and specimen view
        // without losing their original participant/visit selection.
        Set<String> lsids = null;
        if ("POST".equalsIgnoreCase(getViewContext().getRequest().getMethod()))
            lsids = DataRegionSelection.getSelected(getViewContext(), true);
        HttpSession session = getViewContext().getRequest().getSession(true);
        Pair<Container, Set<String>> selectionCache = (Pair<Container, Set<String>>) session.getAttribute(SELECTED_SAMPLES_SESSION_ATTRIB_KEY);

        boolean newFilter = (lsids != null && !lsids.isEmpty());
        boolean cachedFilter = selectionCache != null && getContainer().equals(selectionCache.getKey());
        if (!newFilter && !cachedFilter)
        {
            throw new RedirectException(getSamplesURL());
        }

        if (newFilter)
            selectionCache = new Pair<>(getContainer(), lsids);

        session.setAttribute(SELECTED_SAMPLES_SESSION_ATTRIB_KEY, selectionCache);
        return selectionCache.getValue();
    }


    private static final String SELECTED_SAMPLES_SESSION_ATTRIB_KEY = SpecimenController.class.getName() + "/SelectedSamples";

    @RequiresPermissionClass(ReadPermission.class)
    public class SelectedSamplesAction extends QueryViewAction<SampleViewTypeForm, SpecimenQueryView>
    {
        private boolean _vialView;
        private ParticipantDataset[] _filterPds = null;

        public SelectedSamplesAction()
        {
            super(SampleViewTypeForm.class);
        }

        protected ModelAndView getHtmlView(SampleViewTypeForm form, BindException errors) throws Exception
        {
            Study study = getStudyRedirectIfNull();
            Set<Pair<String, String>> ptidVisits = new HashSet<>();
            if (getFilterPds() != null)
            {
                for (ParticipantDataset pd : getFilterPds())
                {
                    if (pd.getSequenceNum() == null)
                    {
                        ptidVisits.add(new Pair<String, String>(pd.getParticipantId(), null));
                    }
                    else if (study.getTimepointType() != TimepointType.VISIT && pd.getVisitDate() != null)
                    {
                        ptidVisits.add(new Pair<>(pd.getParticipantId(), DateUtil.formatDate(pd.getContainer(), pd.getVisitDate())));
                    }
                    else
                    {
                        VisitImpl visit = pd.getSequenceNum() != null ? StudyManager.getInstance().getVisitForSequence(study, pd.getSequenceNum()) : null;
                        ptidVisits.add(new Pair<>(pd.getParticipantId(), visit != null ? visit.getLabel() : "" + VisitImpl.formatSequenceNum(pd.getSequenceNum())));
                    }
                }
            }
            SpecimenQueryView view = createInitializedQueryView(form, errors, form.getExportType() != null, null);
            JspView<SpecimenHeaderBean> header = new JspView<>("/org/labkey/study/view/samples/samplesHeader.jsp",
                    new SpecimenHeaderBean(getViewContext(), view, ptidVisits));
            return new VBox(header, view);
        }

        private ParticipantDataset[] getFilterPds() throws ServletException, SQLException
        {
            if (_filterPds == null)
            {
                Set<String> lsids = getSelectionLsids();
                _filterPds = StudyManager.getInstance().getParticipantDatasets(getContainer(), lsids);
            }
            return _filterPds;
        }

        protected SpecimenQueryView createQueryView(SampleViewTypeForm form, BindException errors, boolean forExport, String dataRegion) throws Exception
        {
            _vialView = form.isShowVials();
            Set<String> lsids = getSelectionLsids();
            SpecimenQueryView view;
            CohortFilter cohortFilter = CohortFilterFactory.getFromURL(getContainer(), getUser(), getViewContext().getActionURL(), SpecimenQueryView.ViewType.SUMMARY.getQueryName());
            if (lsids != null)
            {
                view = getUtils().getSpecimenQueryView(form.isShowVials(), forExport, getFilterPds(), form.getViewModeEnum(), cohortFilter);
            }
            else
                view = getUtils().getSpecimenQueryView(form.isShowVials(), forExport, form.getViewModeEnum(), cohortFilter);
            view.setAllowExportExternalQuery(false);
            return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root = _appendNavTrail(root);
            root.addChild(_vialView ? "Selected Vials" : "Selected Specimens");
            return root;
        }
    }


    private ActionURL getSamplesURL()
    {
        return getSamplesURL(getContainer());
    }


    public static ActionURL getSamplesURL(Container c)
    {
        return new ActionURL(SamplesAction.class, c);
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class SamplesAction extends QueryViewAction<SampleViewTypeForm, SpecimenQueryView>
    {
        private boolean _vialView;

        public SamplesAction()
        {
            super(SampleViewTypeForm.class);
        }

        protected SpecimenQueryView createQueryView(SampleViewTypeForm form, BindException errors, boolean forExport, String dataRegion) throws Exception
        {
            _vialView = form.isShowVials();
            CohortFilter cohortFilter = CohortFilterFactory.getFromURL(getContainer(), getUser(), getViewContext().getActionURL(), _vialView ? "SpecimenDetail" : "SpecimenSummary");
            SpecimenQueryView view = getUtils().getSpecimenQueryView(_vialView, forExport, form.getViewModeEnum(), cohortFilter);
            if (SpecimenUtils.isCommentsMode(getContainer(), form.getViewModeEnum()))
                view.setRestrictRecordSelectors(false);
            return view;
        }

        protected ModelAndView getHtmlView(SampleViewTypeForm form, BindException errors) throws Exception
        {
            StudyImpl study = getStudyRedirectIfNull();

            SpecimenQueryView view = createInitializedQueryView(form, errors, form.getExportType() != null, null);
            SpecimenHeaderBean bean = new SpecimenHeaderBean(getViewContext(), view);
            // Get last selected request
            if (null != study.getLastSpecimenRequest())
                bean.setSelectedRequest(study.getLastSpecimenRequest());
            JspView<SpecimenHeaderBean> header = new JspView<>("/org/labkey/study/view/samples/samplesHeader.jsp", bean);
            return new VBox(header, view);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root = appendBaseSpecimenNavTrail(root);
            root.addChild(_vialView ? "Vials" : "Grouped Vials");
            return root;
        }
    }

    public static final class SpecimenHeaderBean
    {
        private ActionURL _otherViewURL;
        private ViewContext _viewContext;
        private boolean _showingVials;
        private Integer _selectedRequest;
        private Set<Pair<String, String>> _filteredPtidVisits;

        public SpecimenHeaderBean(ViewContext context, SpecimenQueryView view) throws ServletException
        {
            this(context, view, Collections.<Pair<String, String>>emptySet());
        }

        public SpecimenHeaderBean(ViewContext context, SpecimenQueryView view, Set<Pair<String, String>> filteredPtidVisits) throws RuntimeException
        {
            Map<String, String[]> params = context.getRequest().getParameterMap();

            String currentTable = view.isShowingVials() ? "SpecimenDetail" : "SpecimenSummary";
            String otherTable =   view.isShowingVials() ? "SpecimenSummary" : "SpecimenDetail";
            ActionURL otherView = context.cloneActionURL();
            otherView.deleteParameters();

            StudyImpl study = getStudy(context.getContainer());
            if (null == study)
                throw new NotFoundException("No study exists in this folder.");
            StudyQuerySchema schema = StudyQuerySchema.createSchema(study, context.getUser(), true);

            TableInfo otherTableInfo = schema.getTable(otherTable);

            for (Map.Entry<String, String[]> param : params.entrySet())
            {
                int dotIndex = param.getKey().indexOf('.');

                if (dotIndex >= 0)
                {
                    String table = param.getKey().substring(0, dotIndex);
                    String columnClause = param.getKey().substring(dotIndex + 1);
                    String[] columnClauseParts = columnClause.split("~");
                    String column = columnClauseParts[0];

                    if (table.equals(currentTable))
                    {
                        // use the query service to check to see if the current filter column is present
                        // in the other view.  If so, we'll add a filter parameter with the same value on the
                        // other view.  Otherwise, we'll keep the parameter, but we won't map it to the other view:
                        boolean translatable = column.equals("sort");

                        if (!translatable)
                        {
                            Map<FieldKey, ColumnInfo> presentCols = QueryService.get().getColumns(otherTableInfo,
                                    Collections.singleton(FieldKey.fromString(column)));
                            translatable = !presentCols.isEmpty();
                        }

                        if (translatable)
                        {
                            String key = otherTable + "." + columnClause;
                            otherView.addParameter(key, param.getValue()[0]);
                            continue;
                        }
                    }

                    if (table.equals(currentTable) || table.equals(otherTable))
                        otherView.addParameter(param.getKey(), param.getValue()[0]);
                }
            }

            otherView.replaceParameter("showVials", Boolean.toString(!view.isShowingVials()));
            if (null != params.get(SpecimenQueryView.PARAMS.excludeRequestedBySite.name()))
                otherView.replaceParameter(SpecimenQueryView.PARAMS.excludeRequestedBySite.name(),
                        params.get(SpecimenQueryView.PARAMS.excludeRequestedBySite.name())[0]);
            _otherViewURL = otherView;
            _viewContext = context;
            _showingVials = view.isShowingVials();
            _filteredPtidVisits = filteredPtidVisits;
        }

        public Integer getSelectedRequest()
        {
            return _selectedRequest;
        }

        public void setSelectedRequest(Integer selectedRequest)
        {
            _selectedRequest = selectedRequest;
        }

        public ActionURL getOtherViewURL()
        {
            return _otherViewURL;
        }

        public ViewContext getViewContext()
        {
            return _viewContext;
        }

        public boolean isShowingVials()
        {
            return _showingVials;
        }

        public Set<Pair<String, String>> getFilteredPtidVisits()
        {
            return _filteredPtidVisits;
        }

        public boolean isSingleVisitFilter()
        {
            if (getFilteredPtidVisits().isEmpty())
                return false;
            Iterator<Pair<String, String>> visitIt = getFilteredPtidVisits().iterator();
            String firstVisit = visitIt.next().getValue();
            while (visitIt.hasNext())
            {
                if (!Objects.equals(firstVisit, visitIt.next().getValue()))
                    return false;
            }
            return true;
        }
    }

    public static class SampleViewTypeForm extends QueryViewAction.QueryExportForm
    {
        public enum PARAMS
        {
            showVials,
            viewMode
        }

        private boolean _showVials;
        private SpecimenQueryView.Mode _viewMode = SpecimenQueryView.Mode.DEFAULT;

        public boolean isShowVials()
        {
            return _showVials;
        }

        public void setShowVials(boolean showVials)
        {
            _showVials = showVials;
        }

        public String getViewMode()
        {
            return _viewMode.name();
        }

        public SpecimenQueryView.Mode getViewModeEnum()
        {
            return _viewMode;
        }

        public void setViewMode(String viewMode)
        {
            if (viewMode != null)
                _viewMode = SpecimenQueryView.Mode.valueOf(viewMode);
        }
    }

    public static class ViewEventForm extends IdForm
    {
        private boolean _selected;
        private boolean _vialView;

        public boolean isSelected()
        {
            return _selected;
        }

        public void setSelected(boolean selected)
        {
            _selected = selected;
        }

        public boolean isVialView()
        {
            return _vialView;
        }

        public void setVialView(boolean vialView)
        {
            _vialView = vialView;
        }
    }

    public static class SpecimenEventBean extends ReturnUrlForm
    {
        private Specimen _specimen;

        public SpecimenEventBean(Specimen specimen, ReturnURLString returnUrl)
        {
            _specimen = specimen;
            setReturnUrl(returnUrl);
        }

        public Specimen getSpecimen()
        {
            return _specimen;
        }

        public void setSpecimen(Specimen specimen)
        {
            _specimen = specimen;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class SampleEventsAction extends SimpleViewAction<ViewEventForm>
    {
        private boolean _showingSelectedSamples;

        public ModelAndView getView(ViewEventForm viewEventForm, BindException errors) throws Exception
        {
            _showingSelectedSamples = viewEventForm.isSelected();
            Specimen specimen = SampleManager.getInstance().getSpecimen(getContainer(), getUser(), viewEventForm.getId());
            if (specimen == null)
                throw new NotFoundException("Specimen " + viewEventForm.getId() + " does not exist.");

            JspView<SpecimenEventBean> summaryView = new JspView<>("/org/labkey/study/view/samples/sample.jsp",
                    new SpecimenEventBean(specimen, viewEventForm.getReturnUrl()));
            summaryView.setTitle("Vial Summary");

            SpecimenEventQueryView vialHistoryView = SpecimenEventQueryView.createView(getViewContext(), specimen);
            vialHistoryView.setTitle("Vial History");

            VBox vbox;

            if (getStudyRedirectIfNull().getRepositorySettings().isEnableRequests())
            {
                List<Integer> requestIds = SampleManager.getInstance().getRequestIdsForSpecimen(specimen);
                SimpleFilter requestFilter;
                WebPartView relevantRequests;

                if (!requestIds.isEmpty())
                {
                    requestFilter = new SimpleFilter();
                    StringBuilder whereClause = new StringBuilder();
                    for (int i = 0; i < requestIds.size(); i++)
                    {
                        if (i > 0)
                            whereClause.append(" OR ");
                        whereClause.append("RequestId = ?");
                    }
                    requestFilter.addWhereClause(whereClause.toString(), requestIds.toArray());
                    SpecimenRequestQueryView queryView = SpecimenRequestQueryView.createView(getViewContext(), requestFilter);
                    queryView.setExtraLinks(true);
                    relevantRequests = queryView;
                }
                else
                    relevantRequests = new JspView("/org/labkey/study/view/samples/relevantRequests.jsp");
                relevantRequests.setTitle("Relevant Vial Requests");
                vbox = new VBox(summaryView, vialHistoryView, relevantRequests);
            }
            else
            {
                vbox = new VBox(summaryView, vialHistoryView);
            }

            return vbox;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root = appendBaseSpecimenNavTrail(root);
            if (_showingSelectedSamples)
            {
                root.addChild("Selected Specimens", new ActionURL(SelectedSamplesAction.class,
                        getContainer()).addParameter(SampleViewTypeForm.PARAMS.showVials, "true"));
            }
            root.addChild("Vial History");
            return root;
        }
    }

    private void requiresEditRequestPermissions(SampleRequest request)
    {
        if (!SampleManager.getInstance().hasEditRequestPermissions(getUser(), request))
            throw new UnauthorizedException();
    }

    public static interface HiddenFormInputGenerator
    {
        String getHiddenFormInputs(ViewContext ctx);
    }

    public static class AddToSampleRequestForm extends IdForm implements HiddenFormInputGenerator
    {
        public enum PARAMS
        {
            specimenIds,
            returnUrl
        }

        private String _specimenIds;
        private ReturnURLString _returnUrl;

        public String getSpecimenIds()
        {
            return _specimenIds;
        }

        public void setSpecimenIds(String specimenIds)
        {
            _specimenIds = specimenIds;
        }

        public String getHiddenFormInputs(ViewContext ctx)
        {
            StringBuilder builder = new StringBuilder();
            if (getId() != 0)
                builder.append("<input type=\"hidden\" name=\"id\" value=\"").append(getId()).append("\">\n");
            if (_specimenIds != null)
                builder.append("<input type=\"hidden\" name=\"specimenIds\" value=\"").append(PageFlowUtil.filter(_specimenIds)).append("\">");
            return builder.toString();
        }

        public ReturnURLString getReturnUrl()
        {
            return _returnUrl;
        }

        public void setReturnUrl(ReturnURLString returnUrl)
        {
            _returnUrl = returnUrl;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class HandleAddRequestSamplesAction extends RedirectAction<AddToSampleRequestForm>
    {
        public boolean doAction(AddToSampleRequestForm addToSampleRequestForm, BindException errors) throws Exception
        {
            SampleRequest request = SampleManager.getInstance().getRequest(getContainer(), addToSampleRequestForm.getId());
            requiresEditRequestPermissions(request);
            long ids[];
            if (addToSampleRequestForm.getSpecimenIds() != null && addToSampleRequestForm.getSpecimenIds().length() > 0)
                ids = toLongArray(addToSampleRequestForm.getSpecimenIds().split(","));
            else
                ids = toLongArray(DataRegionSelection.getSelected(getViewContext(), true));

            // get list of specimens that are not already part of the request: we don't want to double-add
            List<Specimen> currentSpecimens = SampleManager.getInstance().getRequestSpecimens(request);
            Set<Long> currentSpecimenIds = new HashSet<>();
            for (Specimen specimen : currentSpecimens)
                currentSpecimenIds.add(specimen.getRowId());
            List<Specimen> specimensToAdd = new ArrayList<>();
            for (long id : ids)
            {
                if (!currentSpecimenIds.contains(id))
                    specimensToAdd.add(SampleManager.getInstance().getSpecimen(getContainer(), getUser(), id));
            }

            SampleManager.getInstance().createRequestSampleMapping(getUser(), request, specimensToAdd, true, true);
            return true;
        }

        public void validateCommand(AddToSampleRequestForm target, Errors errors)
        {
        }

        public ActionURL getSuccessURL(AddToSampleRequestForm addToSampleRequestForm)
        {
            return getManageRequestURL(addToSampleRequestForm.getId(), addToSampleRequestForm.getReturnUrl());
        }
    }

    public static class ManageRequestForm extends IdForm
    {
        public enum PARAMS
        {
            newSite,
            newActor,
            newDescription,
            export,
            submissionResult,
            returnUrl
        }
        private Integer _newSite;
        private Integer _newActor;
        private String _newDescription;
        private String _export;
        private Boolean _submissionResult;
        private ReturnURLString _returnUrl;

        public Integer getNewActor()
        {
            return _newActor;
        }

        public void setNewActor(Integer newActor)
        {
            _newActor = newActor;
        }

        public Integer getNewSite()
        {
            return _newSite;
        }

        public void setNewSite(Integer newSite)
        {
            _newSite = newSite;
        }

        public String getNewDescription()
        {
            return _newDescription;
        }

        public void setNewDescription(String newDescription)
        {
            _newDescription = newDescription;
        }

        public String getExport()
        {
            return _export;
        }

        public void setExport(String export)
        {
            _export = export;
        }

        public Boolean isSubmissionResult()
        {
            return _submissionResult;
        }

        public void setSubmissionResult(Boolean submissionResult)
        {
            _submissionResult = submissionResult;
        }

        public ReturnURLString getReturnUrl()
        {
            return _returnUrl;
        }

        public void setReturnUrl(ReturnURLString returnUrl)
        {
            _returnUrl = returnUrl;
        }
    }

    public abstract static class SamplesViewBean
    {
        protected SpecimenQueryView _specimenQueryView;
        protected List<Specimen> _samples;

        public SamplesViewBean(ViewContext context, List<Specimen> samples, boolean showHistoryLinks,
                               boolean showRecordSelectors, boolean disableLowVialIndicators, boolean restrictRecordSelectors)
        {
            _samples = samples;
            if (samples != null && samples.size() > 0)
            {
                _specimenQueryView = SpecimenQueryView.createView(context, samples, SpecimenQueryView.ViewType.VIALS);
                _specimenQueryView.setShowHistoryLinks(showHistoryLinks);
                _specimenQueryView.setShowRecordSelectors(showRecordSelectors);
                _specimenQueryView.setDisableLowVialIndicators(disableLowVialIndicators);
                _specimenQueryView.setRestrictRecordSelectors(restrictRecordSelectors);
            }
        }

        public SpecimenQueryView getSpecimenQueryView()
        {
            return _specimenQueryView;
        }

        public List<Specimen> getSamples()
        {
            return _samples;
        }
    }

    public class ManageRequestBean extends SamplesViewBean
    {
        public static final String SUBMISSION_WARNING = "Once a request is submitted, its specimen list may no longer be modified.  Continue?";
        public static final String CANCELLATION_WARNING = "Canceling will permanently delete this pending request.  Continue?";
        protected SampleRequest _sampleRequest;
        protected boolean _requestManager;
        protected boolean _requirementsComplete;
        protected boolean _finalState;
        private List<ActorNotificationRecipientSet> _possibleNotifications;
        protected List<String> _missingSpecimens = null;
        private Boolean _submissionResult;
        private Location[] _providingLocations;
        private ReturnURLString _returnUrl;

        public ManageRequestBean(ViewContext context, SampleRequest sampleRequest, boolean forExport, Boolean submissionResult, ReturnURLString returnUrl) throws SQLException, ServletException
        {
            super(context, SampleManager.getInstance().getRequestSpecimens(sampleRequest), !forExport, !forExport, forExport, false);
            _submissionResult = submissionResult;
            _requestManager = context.getContainer().hasPermission(context.getUser(), ManageRequestsPermission.class);
            _sampleRequest = sampleRequest;
            _finalState = SampleManager.getInstance().isInFinalState(_sampleRequest);
            _requirementsComplete = true;
            _missingSpecimens = SampleManager.getInstance().getMissingSpecimens(_sampleRequest);
            _returnUrl = returnUrl;
            SampleRequestRequirement[] requirements = sampleRequest.getRequirements();
            for (int i = 0; i < requirements.length && _requirementsComplete; i++)
            {
                SampleRequestRequirement requirement = requirements[i];
                _requirementsComplete = requirement.isComplete();
            }

            if (_specimenQueryView != null)
            {
                List<DisplayElement> buttons = new ArrayList<>();

                MenuButton exportMenuButton = new MenuButton("Export");
                ActionURL exportExcelURL = context.getActionURL().clone().addParameter("export", "excel");
                ActionURL exportTextURL = context.getActionURL().clone().addParameter("export", "tsv");
                exportMenuButton.addMenuItem("Export all to Excel (.xls)", exportExcelURL.getLocalURIString());
                exportMenuButton.addMenuItem("Export all to text file (.tsv)", exportTextURL.getLocalURIString());
                buttons.add(exportMenuButton);
                _specimenQueryView.setShowExportButtons(false);
                _specimenQueryView.getSettings().setAllowChooseView(false);

                if (SampleManager.getInstance().hasEditRequestPermissions(context.getUser(), sampleRequest) && !_finalState)
                {
                    ActionButton addButton = new ActionButton(new ActionURL(OverviewAction.class, getContainer()), "Specimen Search");
                    ActionButton deleteButton = new ActionButton(HandleRemoveRequestSamplesAction.class, "Remove Selected");
                    _specimenQueryView.addHiddenFormField("id", "" + sampleRequest.getRowId());
                    buttons.add(addButton);

                    ActionURL importActionURL = new ActionURL(ImportVialIdsAction.class, getContainer());
                    importActionURL.addParameter("id", sampleRequest.getRowId());
                    ActionButton importButton = new ActionButton(importActionURL, "Upload Specimen Ids");
                    importButton.setActionType(ActionButton.Action.GET);
                    buttons.add(importButton);

                    buttons.add(deleteButton);
                }
                _specimenQueryView.setButtons(buttons);
            }
        }

        public synchronized List<ActorNotificationRecipientSet> getPossibleNotifications()
        {
            if (_possibleNotifications == null)
                _possibleNotifications = getUtils().getPossibleNotifications(_sampleRequest);
            return _possibleNotifications;
        }

        public SampleRequest getSampleRequest()
        {
            return _sampleRequest;
        }

        public boolean hasMissingSpecimens()
        {
            return _missingSpecimens != null && !_missingSpecimens.isEmpty();
        }

        public List<String> getMissingSpecimens()
        {
            return _missingSpecimens;
        }

        public SampleRequestStatus getStatus()
        {
            return SampleManager.getInstance().getRequestStatus(_sampleRequest.getContainer(), _sampleRequest.getStatusId());
        }

        public Location getDestinationSite()
        {
            Integer destinationSiteId = _sampleRequest.getDestinationSiteId();
            if (destinationSiteId != null)
            {
                return StudyManager.getInstance().getLocation(_sampleRequest.getContainer(), destinationSiteId.intValue());
            }
            return null;
        }

        public boolean isRequestManager()
        {
            return _requestManager;
        }

        public boolean isFinalState()
        {
            return _finalState;
        }

        public boolean isRequirementsComplete()
        {
            return _requirementsComplete;
        }

        public Boolean isSuccessfulSubmission()
        {
            return _submissionResult != null && _submissionResult.booleanValue();
        }

        public Location[] getProvidingLocations()
        {
            if (_providingLocations == null)
            {
                Set<Integer> locationSet = new HashSet<>();
                for (Specimen specimen : _samples)
                {
                    Integer locationId = specimen.getCurrentLocation();
                    if (locationId != null)
                        locationSet.add(locationId);
                }
                _providingLocations = new Location[locationSet.size()];
                int i = 0;
                for (Integer locationId : locationSet)
                    _providingLocations[i++] = StudyManager.getInstance().getLocation(getContainer(), locationId);
            }
            return _providingLocations;
        }

        public ReturnURLString getReturnUrl()
        {
            return _returnUrl;
        }

        public void setReturnUrl(ReturnURLString returnUrl)
        {
            _returnUrl = returnUrl;
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class ManageRequestAction extends FormViewAction<ManageRequestForm>
    {
        private int _requestId;
        public void validateCommand(ManageRequestForm target, Errors errors)
        {
        }

        public ModelAndView getView(ManageRequestForm form, boolean reshow, BindException errors) throws Exception
        {
            SampleRequest request = SampleManager.getInstance().getRequest(getContainer(), form.getId());
            if (request == null)
                throw new NotFoundException();

            _requestId = request.getRowId();
            ManageRequestBean bean = new ManageRequestBean(getViewContext(), request, form.getExport() != null,
                    form.isSubmissionResult(), form.getReturnUrl());
            if (form.getExport() != null)
            {
                getUtils().writeExportData(bean.getSpecimenQueryView(), form.getExport());
                return null;
            }
            else
            {
                GridView attachmentsGrid = getUtils().getRequestEventAttachmentsGridView(getViewContext().getRequest(), errors, _requestId);
                SpecimenQueryView queryView = bean.getSpecimenQueryView();
                if (null != queryView)
                    queryView.setTitle("Associated Specimens");
                HBox hbox = new HBox(new JspView<>("/org/labkey/study/view/samples/manageRequest.jsp", bean), attachmentsGrid);
                hbox.setTableWidth("");
                return new VBox(hbox, queryView);
            }
        }

        public boolean handlePost(ManageRequestForm form, BindException errors) throws Exception
        {
            if (!getContainer().hasPermission(getUser(), ManageRequestsPermission.class))
                throw new UnauthorizedException("You do not have permissions to create new specimen request requirements!");

            SampleRequest request = SampleManager.getInstance().getRequest(getContainer(), form.getId());
            if (request == null)
                throw new NotFoundException();

            if (form.getNewActor() != null && form.getNewActor() > 0)
            {
                SampleRequestActor actor = SampleManager.getInstance().getRequirementsProvider().getActor(getContainer(), form.getNewActor());
                if (actor != null)
                {
                    // an actor is valid if a site has been provided for a per-site actor, or if no site
                    // has been provided for a non-site-specific actor.  The UI should enforce this already,
                    // so this is just a backup check.
                    boolean validActor = (actor.isPerSite() && form.getNewSite() != null && form.getNewSite() > 0) ||
                            (!actor.isPerSite() && (form.getNewSite() == null || form.getNewSite() <= 0));
                    if (validActor)
                    {
                        SampleRequestRequirement requirement = new SampleRequestRequirement();
                        requirement.setContainer(getContainer());
                        if (form.getNewSite() != null && form.getNewSite() > 0)
                            requirement.setSiteId(form.getNewSite());
                        requirement.setActorId(form.getNewActor());
                        requirement.setDescription(form.getNewDescription());
                        requirement.setRequestId(request.getRowId());
                        SampleManager.getInstance().createRequestRequirement(getUser(), requirement, true, true);
                    }
                }
            }
            return true;
        }

        public ActionURL getSuccessURL(ManageRequestForm manageRequestForm)
        {
            return getManageRequestURL(manageRequestForm.getId(), manageRequestForm.getReturnUrl());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendSpecimenRequestNavTrail(root, _requestId);
        }
    }


    public static class ViewRequestsHeaderBean
    {
        public static final String PARAM_STATUSLABEL = "SpecimenRequest.Status/Label~eq";
        public static final String PARAM_CREATEDBY = "SpecimenRequest.CreatedBy/DisplayName~eq";
        private SpecimenRequestQueryView _view;
        private ViewContext _context;

        public ViewRequestsHeaderBean(ViewContext context, SpecimenRequestQueryView view)
        {
            _view = view;
            _context = context;
        }

        public SpecimenRequestQueryView getView()
        {
            return _view;
        }

        public List<SampleRequestStatus> getStauses() throws SQLException
        {
            return SampleManager.getInstance().getRequestStatuses(_context.getContainer(), _context.getUser());
        }

        public boolean isFilteredStatus(SampleRequestStatus status)
        {
            return status.getLabel().equals(_context.getActionURL().getParameter(PARAM_STATUSLABEL));
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ViewRequestsAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            requiresLogin();
            SpecimenRequestQueryView grid = SpecimenRequestQueryView.createView(getViewContext());
            grid.setExtraLinks(true);
            grid.setShowCustomizeLink(false);
            grid.setShowDetailsColumn(false);
            if (getContainer().hasPermission(getUser(), RequestSpecimensPermission.class))
            {
                ActionButton insertButton = new ActionButton(SpecimenController.ShowCreateSampleRequestAction.class, "Create New Request", DataRegion.MODE_GRID, ActionButton.Action.LINK);
                grid.setButtons(Collections.singletonList((DisplayElement) insertButton));
            }
            else
                grid.setButtons(Collections.<DisplayElement>emptyList());
            JspView<ViewRequestsHeaderBean> header = new JspView<>("/org/labkey/study/view/samples/viewRequestsHeader.jsp",
                    new ViewRequestsHeaderBean(getViewContext(), grid));

            return new VBox(header, grid);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendSpecimenRequestsNavTrail(root);
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class HandleRemoveRequestSamplesAction extends FormHandlerAction<AddToSampleRequestForm>
    {
        public void validateCommand(AddToSampleRequestForm target, Errors errors)
        {
        }

        public boolean handlePost(AddToSampleRequestForm form, BindException errors) throws Exception
        {
            SampleRequest request = SampleManager.getInstance().getRequest(getContainer(), form.getId());
            requiresEditRequestPermissions(request);
            long[] ids = toLongArray(DataRegionSelection.getSelected(getViewContext(), true));
            List<Long> sampleIds = new ArrayList<>();
            for (long id : ids)
                sampleIds.add(id);
            try
            {
                SampleManager.getInstance().deleteRequestSampleMappings(getUser(), request, sampleIds, true);
            }
            catch (RequestabilityManager.InvalidRuleException e)
            {
                errors.reject(ERROR_MSG, "The samples could not be removed because a requestability rule is configured incorrectly. " +
                            "Please report this problem to an administrator.  Error details: "  + e.getMessage());
                return false;
            }
            return true;
        }

        public ActionURL getSuccessURL(AddToSampleRequestForm addToSampleRequestForm)
        {
            return getManageRequestURL(addToSampleRequestForm.getId(), addToSampleRequestForm.getReturnUrl());
        }
    }

    public static class ManageRequestStatusForm extends IdForm
    {
        private int _status;
        private String _comments;
        private String _requestDescription;
        private String[] _notificationIdPairs;
        private boolean  _emailInactiveUsers;

        public int getStatus()
        {
            return _status;
        }

        public void setStatus(int status)
        {
            _status = status;
        }

        public String getComments()
        {
            return _comments;
        }

        public void setComments(String comments)
        {
            _comments = comments;
        }

        public String[] getNotificationIdPairs()
        {
            return _notificationIdPairs;
        }

        public void setNotificationIdPairs(String[] notificationIdPairs)
        {
            _notificationIdPairs = notificationIdPairs;
        }

        public String getRequestDescription()
        {
            return _requestDescription;
        }

        public void setRequestDescription(String requestDescription)
        {
            _requestDescription = requestDescription;
        }

        public boolean isEmailInactiveUsers()
        {
            return _emailInactiveUsers;
        }

        public void setEmailInactiveUsers(boolean emailInactiveUsers)
        {
            _emailInactiveUsers = emailInactiveUsers;
        }
    }

    @RequiresPermissionClass(RequestSpecimensPermission.class)
    public class ManageRequestStatusAction extends FormViewAction<ManageRequestStatusForm>
    {
        private SampleRequest _sampleRequest;
        public void validateCommand(ManageRequestStatusForm target, Errors errors)
        {
        }

        public ModelAndView getView(ManageRequestStatusForm form, boolean reshow, BindException errors) throws Exception
        {
            _sampleRequest = SampleManager.getInstance().getRequest(getContainer(), form.getId());
            if (_sampleRequest == null)
                throw new NotFoundException();

            return new JspView<>("/org/labkey/study/view/samples/manageRequestStatus.jsp",
                    new ManageRequestBean(getViewContext(), _sampleRequest, false, null, null), errors);
        }

        public boolean handlePost(final ManageRequestStatusForm form, BindException errors) throws Exception
        {
            _sampleRequest = SampleManager.getInstance().getRequest(getContainer(), form.getId());
            if (_sampleRequest == null)
                throw new NotFoundException();

            boolean statusChanged = form.getStatus() != _sampleRequest.getStatusId();
            boolean detailsChanged = !nullSafeEqual(form.getRequestDescription(), _sampleRequest.getComments());

            List<AttachmentFile> files = getAttachmentFileList();
            boolean hasAttachments = !files.isEmpty();

            boolean hasComments = form.getComments() != null && form.getComments().length() > 0;
            if (statusChanged || detailsChanged || hasComments || hasAttachments)
            {
                SampleManager.RequestEventType changeType;
                String comment = "";
                String eventSummary;
                if (statusChanged || detailsChanged)
                {
                    if (statusChanged)
                    {
                        SampleRequestStatus prevStatus = SampleManager.getInstance().getRequestStatus(getContainer(), _sampleRequest.getStatusId());
                        SampleRequestStatus newStatus = SampleManager.getInstance().getRequestStatus(getContainer(), form.getStatus());
                        comment += "Status changed from \"" + (prevStatus != null ? prevStatus.getLabel() : "N/A") + "\" to \"" +
                                (newStatus != null ? newStatus.getLabel() : "N/A") + "\"\n";
                    }
                    if (detailsChanged)
                    {
                        String prevDetails = _sampleRequest.getComments();
                        String newDetails = form.getRequestDescription();
                        comment += "Description changed from \"" + (prevDetails != null ? prevDetails : "N/A") + "\" to \"" +
                                (newDetails != null ? newDetails : "N/A") + "\"\n";
                    }
                    eventSummary = comment;
                    if (hasComments)
                        comment += form.getComments();
                    _sampleRequest = _sampleRequest.createMutable();
                    _sampleRequest.setStatusId(form.getStatus());
                    _sampleRequest.setComments(form.getRequestDescription());
                    _sampleRequest.setModified(new Date());
                    try
                    {
                        SampleManager.getInstance().updateRequest(getUser(), _sampleRequest);
                    }
                    catch (RequestabilityManager.InvalidRuleException e)
                    {
                        errors.reject(ERROR_MSG, "The request could not be updated because a requestability rule is configured incorrectly. " +
                                    "Please report this problem to an administrator.  Error details: "  + e.getMessage());
                        return false;
                    }
                    changeType = SampleManager.RequestEventType.REQUEST_STATUS_CHANGED;
                }
                else
                {
                    changeType = SampleManager.RequestEventType.COMMENT_ADDED;
                    comment = form.getComments();
                    eventSummary = "Comments added.";
                }

                SampleRequestEvent event;
                try
                {
                    event = SampleManager.getInstance().createRequestEvent(getUser(), _sampleRequest, changeType, comment, files);
                }
                catch (Exception e)
                {
                    errors.reject(ERROR_MSG, "The request could not be updated because of an unexpected error. " +
                            "Please report this problem to an administrator. Error details: "  + e.getMessage());
                    return false;
                }
                try
                {
                    List<? extends NotificationRecipientSet> recipients = getUtils().getNotifications(_sampleRequest, form.getNotificationIdPairs());
                    DefaultRequestNotification notification = new DefaultRequestNotification(_sampleRequest, recipients,
                            eventSummary, event, form.getComments(), null, getViewContext());
                    getUtils().sendNotification(notification, form.isEmailInactiveUsers(), errors);
                }
                catch (ConfigurationException | IOException e)
                {
                    errors.reject(ERROR_MSG, "The request was updated successfully, but the notification failed: " +  e.getMessage());
                    return false;
                }
            }

            if (errors.hasErrors())
                return false;
            return true;
        }

        public ActionURL getSuccessURL(ManageRequestStatusForm manageRequestForm)
        {
            return getManageRequestURL(_sampleRequest.getRowId());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root = appendSpecimenRequestNavTrail(root, _sampleRequest.getRowId());
            return root.addChild("Update Request");
        }
    }

    public static class CreateSampleRequestForm implements HiddenFormInputGenerator
    {
        public enum PARAMS
        {
            returnUrl,
            extendedRequestUrl,
            ignoreReturnUrl
        }

        private String[] _inputs;
        private int _destinationLocation;
        private long[] _sampleRowIds;
        private boolean[] _required;
        private boolean _fromGroupedView;
        private Integer _preferredLocation;
        private ReturnURLString _returnUrl;
        private boolean _ignoreReturnUrl;
        private boolean _extendedRequestUrl;
        private String[] _sampleIds;

        public String getHiddenFormInputs(ViewContext ctx)
        {
            StringBuilder builder = new StringBuilder();
            if (_inputs != null)
            {
                for (String input : _inputs)
                    builder.append("<input type=\"hidden\" name=\"inputs\" value=\"").append(PageFlowUtil.filter(input)).append("\">\n");
            }
            if (_destinationLocation != 0)
                builder.append("<input type=\"hidden\" name=\"destinationLocation\" value=\"").append(_destinationLocation).append("\">\n");
            if (_returnUrl != null)
                builder.append("<input type=\"hidden\" name=\"returnUrl\" value=\"").append(PageFlowUtil.filter(_returnUrl)).append("\">\n");
            if (_sampleRowIds != null)
            {
                for (long sampleId : _sampleRowIds)
                    builder.append("<input type=\"hidden\" name=\"sampleRowIds\" value=\"").append(sampleId).append("\">\n");
            }
            else
            {
                String dataRegionSelectionKey = ctx.getRequest().getParameter(DataRegionSelection.DATA_REGION_SELECTION_KEY);
                if (dataRegionSelectionKey != null)
                {
                    builder.append("<input type=\"hidden\" name=\"").append(DataRegionSelection.DATA_REGION_SELECTION_KEY);
                    builder.append("\" value=\"").append(dataRegionSelectionKey).append("\">\n");
                    Set<String> specimenFormValues = DataRegionSelection.getSelected(ctx, false);
                    for (String formValue : specimenFormValues)
                    {
                        builder.append("<input type=\"hidden\" name=\"").append(DataRegion.SELECT_CHECKBOX_NAME).append("\" value=\"");
                        builder.append(PageFlowUtil.filter(formValue)).append("\">\n");
                    }
                }
            }

            if (_required != null)
            {
                for (boolean required : _required)
                    builder.append("<input type=\"hidden\" name=\"required\" value=\"").append(required).append("\">\n");
            }

            if (_sampleIds != null)
            {
                for (String sampleId : _sampleIds)
                    builder.append("<input type=\"hidden\" name=\"sampleIds\" value=\"").append(PageFlowUtil.filter(sampleId)).append("\">\n");
            }

            builder.append("<input type=\"hidden\" name=\"fromGroupedView\" value=\"").append(_fromGroupedView).append("\">\n");
            return builder.toString();
        }

        public long[] getSampleRowIds()
        {
            return _sampleRowIds;
        }

        public void setSampleRowIds(long[] sampleRowIds)
        {
            _sampleRowIds = sampleRowIds;
        }

        public int getDestinationLocation()
        {
            return _destinationLocation;
        }

        public void setDestinationLocation(int destinationLocation)
        {
            _destinationLocation = destinationLocation;
        }

        public String[] getInputs()
        {
            return _inputs;
        }

        public void setInputs(String[] inputs)
        {
            _inputs = inputs;
        }

        public boolean[] getRequired()
        {
            return _required;
        }

        public void setRequired(boolean[] required)
        {
            _required = required;
        }

        public boolean isFromGroupedView()
        {
            return _fromGroupedView;
        }

        public void setFromGroupedView(boolean _fromGroupedView)
        {
            this._fromGroupedView = _fromGroupedView;
        }

        public Integer getPreferredLocation()
        {
            return _preferredLocation;
        }

        public void setPreferredLocation(Integer preferredLocation)
        {
            _preferredLocation = preferredLocation;
        }

        public ReturnURLString getReturnUrl()
        {
            return _returnUrl;
        }

        public void setReturnUrl(ReturnURLString returnUrl)
        {
            _returnUrl = returnUrl;
        }

        public boolean isIgnoreReturnUrl()
        {
            return _ignoreReturnUrl;
        }

        public void setIgnoreReturnUrl(boolean ignoreReturnUrl)
        {
            _ignoreReturnUrl = ignoreReturnUrl;
        }

        public boolean isExtendedRequestUrl()
        {
            return _extendedRequestUrl;
        }

        public void setExtendedRequestUrl(boolean extendedRequestUrl)
        {
            _extendedRequestUrl = extendedRequestUrl;
        }

        public String[] getSampleIds()
        {
            return _sampleIds;
        }

        public void setSampleIds(String[] sampleIds)
        {
            _sampleIds = sampleIds;
        }

        public SpecimenUtils.RequestedSpecimens getSelectedSpecimens(SpecimenUtils utils) throws SQLException,SpecimenUtils.AmbiguousLocationException
        {
            // first check for explicitly listed specimen row ids (this is the case when posting the final
            // specimen request form):
            List<Specimen> requestedSamples = utils.getSpecimensFromRowIds(getSampleRowIds());
            if (requestedSamples != null && requestedSamples.size() > 0)
                return new SpecimenUtils.RequestedSpecimens(requestedSamples);

            Set<String> ids;
            if ("post".equalsIgnoreCase(utils.getViewContext().getRequest().getMethod()) &&
                    (utils.getViewContext().getRequest().getParameter(DataRegionSelection.DATA_REGION_SELECTION_KEY) != null))
            {
                ids = DataRegionSelection.getSelected(utils.getViewContext(), true);
                if (isFromGroupedView())
                    return utils.getRequestableBySampleHash(ids, getPreferredLocation());
                else
                    return utils.getRequestableByVialRowIds(ids);
            }
            else if (_sampleIds != null && _sampleIds.length > 0)
            {
                ids = new HashSet<>();
                Collections.addAll(ids, _sampleIds);
                if (isFromGroupedView())
                    return utils.getRequestableBySampleHash(ids, getPreferredLocation());
                else
                    return utils.getRequestableByVialGlobalUniqueIds(ids);
            }
            else
                return null;
        }
    }

    public static class NewRequestBean extends SamplesViewBean
    {
        private Container _container;
        private SampleManager.SpecimenRequestInput[] _inputs;
        private String[] _inputValues;
        private int _selectedSite;
        private BindException _errors;
        private ReturnURLString _returnUrl;

        public NewRequestBean(ViewContext context, SpecimenUtils.RequestedSpecimens requestedSpecimens, CreateSampleRequestForm form, BindException errors) throws SQLException
        {
            super(context, requestedSpecimens != null ? requestedSpecimens.getSpecimens() : null, false, false, false, false);
            _errors = errors;
            _inputs = SampleManager.getInstance().getNewSpecimenRequestInputs(context.getContainer());
            _selectedSite = form.getDestinationLocation();
            _inputValues = form.getInputs();
            _container = context.getContainer();
            _returnUrl = form.getReturnUrl();
        }

        public SampleManager.SpecimenRequestInput[] getInputs()
        {
            return _inputs;
        }

        public String getValue(int inputIndex) throws SQLException
        {
            if (_inputValues != null && inputIndex < _inputValues.length && _inputValues[inputIndex] != null)
                return _inputValues[inputIndex];
            if (_inputs[inputIndex].isRememberSiteValue() && _selectedSite > 0)
                return _inputs[inputIndex].getDefaultSiteValues(_container).get(_selectedSite);
            return "";
        }

        public int getSelectedSite()
        {
            return _selectedSite;
        }

        public BindException getErrors()
        {
            return _errors;
        }

        public ReturnURLString getReturnUrl()
        {
            return _returnUrl;
        }
    }

    @RequiresPermissionClass(RequestSpecimensPermission.class)
    public class HandleCreateSampleRequestAction extends FormViewAction<CreateSampleRequestForm>
    {
        private SampleRequest _sampleRequest;

        public ModelAndView getView(CreateSampleRequestForm form, boolean reshow, BindException errors) throws Exception
        {
            return getCreateSampleRequestView(form, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendSpecimenRequestsNavTrail(root).addChild("New Specimen Request");
        }

        public void validateCommand(CreateSampleRequestForm form, Errors errors)
        {
            boolean missingRequiredInput = false;
            if (form.getDestinationLocation() <= 0)
                missingRequiredInput = true;

            for (int i = 0; i < form.getInputs().length && !missingRequiredInput; i++)
            {
                if (form.getRequired()[i] && (form.getInputs()[i] == null || form.getInputs()[i].length() == 0))
                    missingRequiredInput = true;
            }

            if (missingRequiredInput)
                errors.reject(null, "Please provide all required input.");
        }

        public boolean handlePost(CreateSampleRequestForm form, BindException errors) throws Exception
        {
            getUtils().ensureSpecimenRequestsConfigured(true);

            String[] inputs = form.getInputs();
            long[] sampleIds = form.getSampleRowIds();
            StringBuilder comments = new StringBuilder();
            SampleManager.SpecimenRequestInput[] expectedInputs =
                    SampleManager.getInstance().getNewSpecimenRequestInputs(getContainer());
            if (inputs.length != expectedInputs.length)
                throw new IllegalStateException("Expected a form element for each input.");

            for (int i = 0; i < expectedInputs.length; i++)
            {
                SampleManager.SpecimenRequestInput expectedInput = expectedInputs[i];
                if (form.getDestinationLocation() != 0 && expectedInput.isRememberSiteValue())
                    expectedInput.setDefaultSiteValue(getContainer(), form.getDestinationLocation(), inputs[i]);
                if (i > 0)
                    comments.append("\n\n");
                comments.append(expectedInput.getTitle()).append(":\n");
                if (inputs[i] != null && inputs[i].length() > 0)
                    comments.append(inputs[i]);
                else
                    comments.append("[Not provided]");
            }

            _sampleRequest = new SampleRequest();
            _sampleRequest.setComments(comments.toString());
            _sampleRequest.setContainer(getContainer());
            Timestamp ts = new Timestamp(System.currentTimeMillis());
            _sampleRequest.setCreated(ts);
            _sampleRequest.setModified(ts);
            _sampleRequest.setEntityId(GUID.makeGUID());
            if (form.getDestinationLocation() > 0)
                _sampleRequest.setDestinationSiteId(form.getDestinationLocation());
            _sampleRequest.setStatusId(SampleManager.getInstance().getInitialRequestStatus(getContainer(), getUser(), false).getRowId());

            DbScope scope = StudySchema.getInstance().getSchema().getScope();
            try (DbScope.Transaction transaction = scope.ensureTransaction())
            {
                if (!StudyManager.getInstance().isSiteValidRequestingLocation(getContainer(), _sampleRequest.getDestinationSiteId()))
                {
                    errors.reject(ERROR_MSG, "The requesting location is not valid.");
                    return false;
                }

                User user = getUser();
                Container container = getContainer();
                _sampleRequest = SampleManager.getInstance().createRequest(getUser(), _sampleRequest, true);
                List<Specimen> samples;
                if (sampleIds != null && sampleIds.length > 0)
                {
                    samples = new ArrayList<>();
                    for (long sampleId : sampleIds)
                    {
                        Specimen specimen = SampleManager.getInstance().getSpecimen(container, user, sampleId);
                        if (specimen != null)
                        {
                            boolean isAvailable = specimen.isAvailable();
                            if (!isAvailable)
                            {
                                errors.reject(null, RequestabilityManager.makeSpecimenUnavailableMessage(specimen, "This sample has been removed from the list below."));
                            }
                            else
                                samples.add(specimen);
                        }
                        else
                        {
                            ExceptionUtil.logExceptionToMothership(getViewContext().getRequest(),
                                    new IllegalStateException("Specimen ID " + sampleId + " was not found in container " + container.getId()));
                        }
                    }
                    if (errors.getErrorCount() == 0)
                    {
                        try
                        {
                            SampleManager.getInstance().createRequestSampleMapping(getUser(), _sampleRequest, samples, true, true);
                        }
                        catch (RequestabilityManager.InvalidRuleException e)
                        {
                            errors.reject(ERROR_MSG, "The request could not be created because a requestability rule is configured incorrectly. " +
                                    "Please report this problem to an administrator.  Error details: " + e.getMessage());
                            return false;
                        }
                    }
                    else
                    {
                        long[] validSampleIds = new long[samples.size()];
                        int index = 0;
                        for (Specimen sample : samples)
                            validSampleIds[index++] = sample.getRowId();
                        form.setSampleRowIds(validSampleIds);
                        return false;
                    }
                }
                transaction.commit();
            }

            if (!SampleManager.getInstance().isSpecimenShoppingCartEnabled(getContainer()))
            {
                try
                {
                    getUtils().sendNewRequestNotifications(_sampleRequest, errors);
                }
                catch (ConfigurationException e)
                {
                    errors.reject(ERROR_MSG, e.getMessage());
                }
                catch (IOException e)
                {
                    errors.reject(ERROR_MSG, e.getMessage());
                }
            }

            if (errors.hasErrors())
                return false;

            StudyImpl study = getStudy();
            if (null == study)
                throw new NotFoundException("No study exists in this folder.");
            study.setLastSpecimenRequest(_sampleRequest.getRowId());
            return true;
        }

        public ActionURL getSuccessURL(CreateSampleRequestForm createSampleRequestForm)
        {
            ActionURL modifiedReturnURL = null;
            if (createSampleRequestForm.isExtendedRequestUrl())
            {
                return getExtendedRequestURL(_sampleRequest.getRowId(), modifiedReturnURL != null ? new ReturnURLString(modifiedReturnURL.getLocalURIString()) : null);
            }
            if (createSampleRequestForm.getReturnUrl() != null)
            {
                modifiedReturnURL = createSampleRequestForm.getReturnUrl().getActionURL();
            }
            if (modifiedReturnURL != null && !createSampleRequestForm.isIgnoreReturnUrl())
                return modifiedReturnURL;
            else
                return getManageRequestURL(_sampleRequest.getRowId(), modifiedReturnURL != null ? new ReturnURLString(modifiedReturnURL.getLocalURIString()) : null);
        }
    }

    public static class SelectSpecimenProviderBean
    {
        private HiddenFormInputGenerator _sourceForm;
        private LocationImpl[] _possibleLocations;
        private ActionURL _formTarget;

        public SelectSpecimenProviderBean(HiddenFormInputGenerator sourceForm, LocationImpl[] possibleLocations, ActionURL formTarget)
        {
            _sourceForm = sourceForm;
            _possibleLocations = possibleLocations;
            _formTarget = formTarget;
        }

        public LocationImpl[] getPossibleLocations()
        {
            return _possibleLocations;
        }

        public ActionURL getFormTarget()
        {
            return _formTarget;
        }

        public HiddenFormInputGenerator getSourceForm()
        {
            return _sourceForm;
        }
    }

    private ModelAndView getCreateSampleRequestView(CreateSampleRequestForm form, BindException errors) throws SQLException, ServletException
    {
        getUtils().ensureSpecimenRequestsConfigured(true);
        
        SpecimenUtils.RequestedSpecimens requested;

        try
        {
            requested = form.getSelectedSpecimens(getUtils());
        }
        catch (SpecimenUtils.AmbiguousLocationException e)
        {
            // Even though this method (getCreateSampleRequestView) is used from multiple places, only HandleCreateSampleRequestAction
            // receives a post; therefore, it's safe to say that the selectSpecimenProvider.jsp form should always post to
            // HandleCreateSampleRequestAction.
            return new JspView<>("/org/labkey/study/view/samples/selectSpecimenProvider.jsp",
                    new SelectSpecimenProviderBean(form, e.getPossibleLocations(), new ActionURL(ShowCreateSampleRequestAction.class, getContainer())), errors);
        }

        return new JspView<>("/org/labkey/study/view/samples/requestSamples.jsp",
                new NewRequestBean(getViewContext(), requested, form, errors));
    }

    @RequiresPermissionClass(RequestSpecimensPermission.class)
    public class ShowCreateSampleRequestAction extends SimpleViewAction<CreateSampleRequestForm>
    {
        public ModelAndView getView(CreateSampleRequestForm form, BindException errors) throws Exception
        {
            return getCreateSampleRequestView(form, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendSpecimenRequestsNavTrail(root).addChild("New Specimen Request");
        }
    }

    @RequiresPermissionClass(RequestSpecimensPermission.class)
    public class ShowAPICreateSampleRequestAction extends SimpleViewAction<CreateSampleRequestForm>
    {
        public ModelAndView getView(CreateSampleRequestForm form, BindException errors) throws Exception
        {
            return getCreateSampleRequestView(form, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendSpecimenRequestsNavTrail(root).addChild("New Specimen Request");
        }
    }

    @RequiresPermissionClass(RequestSpecimensPermission.class)
    public class ExtendedSpecimenRequestAction extends SimpleViewAction<CreateSampleRequestForm>
    {
        public ModelAndView getView(CreateSampleRequestForm form, BindException errors) throws Exception
        {
            VBox vbox = new VBox();

            ExtendedSpecimenRequestView view = SampleManager.getInstance().getExtendedSpecimenRequestView(getViewContext());
            if (view != null && view.isActive())
            {
                HtmlView requestView = new HtmlView(view.getBody());
                vbox.addView(requestView);
            }
            else
            {
                vbox.addView(new HtmlView("An extended specimen request view has not been provided for this folder."));
            }
            return vbox;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendSpecimenRequestsNavTrail(root).addChild("Extended Specimen Request");
        }
    }
    

    public static class AddToExistingRequestBean extends SamplesViewBean
    {
        private SpecimenRequestQueryView _requestsGrid;
        private List<Location> _providingLocations;

        public AddToExistingRequestBean(ViewContext context, SpecimenUtils.RequestedSpecimens requestedSpecimens) throws ServletException
        {
            super(context, requestedSpecimens.getSpecimens(), false, false, false, true);

            _providingLocations = requestedSpecimens.getProvidingLocations();
            StringBuilder params = new StringBuilder();
            params.append(IdForm.PARAMS.id.name()).append("=${requestId}&" + AddToSampleRequestForm.PARAMS.specimenIds + "=");
            String separator = "";

            if (requestedSpecimens.getSpecimens() != null)
            {
                for (Specimen specimen : requestedSpecimens.getSpecimens())
                {
                    params.append(separator).append(specimen.getRowId());
                    separator=",";
                }
            }

            SimpleFilter filter = null;

            if (!context.getContainer().hasPermission(context.getUser(), ManageRequestsPermission.class))
            {
                if (!SampleManager.getInstance().isSpecimenShoppingCartEnabled(context.getContainer()))
                    throw new UnauthorizedException();

                SampleRequestStatus cartStatus = SampleManager.getInstance().getRequestShoppingCartStatus(context.getContainer(), context.getUser());
                filter = new SimpleFilter(FieldKey.fromParts("StatusId"), cartStatus.getRowId());
            }

            String addLink = new ActionURL(HandleAddRequestSamplesAction.class, context.getContainer()).toString() + params.toString();
            _requestsGrid = SpecimenRequestQueryView.createView(context, filter);
            _requestsGrid.setExtraLinks(false, new NavTree("Select", addLink));
            _requestsGrid.setShowCustomizeLink(false);
        }

        public SpecimenRequestQueryView getRequestsGridView()
        {
            return _requestsGrid;
        }

        public List<Location> getProvidingLocations()
        {
            return _providingLocations;
        }
    }

    protected void requiresManageSpecimens() throws ServletException
    {
        if (!getContainer().hasPermission(getUser(), ManageRequestsPermission.class))
        {
            throw new UnauthorizedException();
        }
    }

    @RequiresPermissionClass(InsertPermission.class)
    public class ShowAddToSampleRequestAction extends SimpleViewAction<CreateSampleRequestForm>
    {
        public ModelAndView getView(CreateSampleRequestForm form, BindException errors) throws Exception
        {
            if (!SampleManager.getInstance().isSpecimenShoppingCartEnabled(getContainer()))
                requiresManageSpecimens();
            SpecimenUtils.RequestedSpecimens specimens = null;
            if (getViewContext().getRequest().getParameter(DataRegionSelection.DATA_REGION_SELECTION_KEY) != null)
            {
                if (form.isFromGroupedView())
                {
                    Set<String> specimenFormValues = DataRegionSelection.getSelected(getViewContext(), true);
                    try
                    {
                        specimens = getUtils().getRequestableBySampleHash(specimenFormValues, form.getPreferredLocation());
                    }
                    catch (SpecimenUtils.AmbiguousLocationException e)
                    {
                        return new JspView<>("/org/labkey/study/view/samples/selectSpecimenProvider.jsp",
                                new SelectSpecimenProviderBean(form, e.getPossibleLocations(), new ActionURL(ShowAddToSampleRequestAction.class, getContainer())));
                    }
                }
                else
                    specimens = getUtils().getRequestableByVialRowIds(DataRegionSelection.getSelected(getViewContext(), true));
            }
            return new JspView<>("/org/labkey/study/view/samples/addSamplesToRequest.jsp",
                    new AddToExistingRequestBean(getViewContext(), specimens));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendSpecimenRequestsNavTrail(root).addChild("Add Specimens To Existing Request");
        }
    }

    public static class RequirementForm extends IdForm
    {
        private int _requirementId;

        public int getRequirementId()
        {
            return _requirementId;
        }

        public void setRequirementId(int requirementId)
        {
            _requirementId = requirementId;
        }
    }

    public static class ManageRequirementForm extends RequirementForm
    {
        private boolean _complete;
        private String _comment;
        private String[] _notificationIdPairs;
        private boolean _emailInactiveUsers;

        public String getComment()
        {
            return _comment;
        }

        public void setComment(String comment)
        {
            _comment = comment;
        }

        public boolean isComplete()
        {
            return _complete;
        }

        public void setComplete(boolean complete)
        {
            _complete = complete;
        }

        public String[] getNotificationIdPairs()
        {
            return _notificationIdPairs;
        }

        public void setNotificationIdPairs(String[] notificationIdPairs)
        {
            _notificationIdPairs = notificationIdPairs;
        }

        public boolean isEmailInactiveUsers()
        {
            return _emailInactiveUsers;
        }

        public void setEmailInactiveUsers(boolean emailInactiveUsers)
        {
            _emailInactiveUsers = emailInactiveUsers;
        }
    }

    @RequiresPermissionClass(ManageRequestsPermission.class)
    public class DeleteRequirementAction extends RedirectAction<RequirementForm>
    {
        public void validateCommand(RequirementForm target, Errors errors)
        {
        }

        public boolean doAction(RequirementForm form, BindException errors) throws Exception
        {
            SampleRequestRequirement requirement =
                    SampleManager.getInstance().getRequirementsProvider().getRequirement(getContainer(), form.getRequirementId());
            if (requirement.getRequestId() == form.getId())
            {
                SampleManager.getInstance().deleteRequestRequirement(getUser(), requirement);
                return true;
            }
            else
                return false;
        }

        public ActionURL getSuccessURL(RequirementForm requirementForm)
        {
            return getManageRequestURL(requirementForm.getId());
        }
    }


    @RequiresPermissionClass(ManageRequestRequirementsPermission.class)
    public class DeleteDefaultRequirementAction extends RedirectAction<IdForm>
    {
        public void validateCommand(IdForm target, Errors errors)
        {
        }

        public boolean doAction(IdForm form, BindException errors) throws Exception
        {
            SampleRequestRequirement requirement =
                    SampleManager.getInstance().getRequirementsProvider().getRequirement(getContainer(), form.getId());
            // we should only be deleting default requirements (those without an associated request):
            if (requirement != null && requirement.getRequestId() == -1)
            {
                SampleManager.getInstance().deleteRequestRequirement(getUser(), requirement, false);
                return true;
            }
            else
                return false;
        }

        public ActionURL getSuccessURL(IdForm requirementForm)
        {
            return new ActionURL(ManageDefaultReqsAction.class, getContainer());
        }
    }

    @RequiresPermissionClass(ManageRequestsPermission.class)
    public class DeleteMissingRequestSpecimensAction extends RedirectAction<IdForm>
    {
        public void validateCommand(IdForm target, Errors errors)
        {
        }

        public boolean doAction(IdForm form, BindException errors) throws Exception
        {
            SampleRequest request = SampleManager.getInstance().getRequest(getContainer(), form.getId());
            if (request == null)
                throw new NotFoundException("Sample request " + form.getId() + " does not exist.");

            SampleManager.getInstance().deleteMissingSpecimens(request);
            return true;
        }

        public ActionURL getSuccessURL(IdForm requirementForm)
        {
            return getManageRequestURL(requirementForm.getId());
        }
    }

    public class ManageRequirementBean
    {
        private GridView _historyView;
        private SampleRequestRequirement _requirement;
        private boolean _requestManager;
        private List<ActorNotificationRecipientSet> _possibleNotifications;
        private boolean _finalState;

        public ManageRequirementBean(ViewContext context, SampleRequest request, SampleRequestRequirement requirement)
        {
            _requirement = requirement;
            _possibleNotifications = getUtils().getPossibleNotifications(request);
            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("RequestId"), requirement.getRequestId());
            filter.addCondition(FieldKey.fromParts("RequirementId"), requirement.getRowId());
            _requestManager = context.getContainer().hasPermission(context.getUser(), ManageRequestsPermission.class);
            _historyView = getUtils().getRequestEventGridView(context.getRequest(), null, filter);
            _finalState = SampleManager.getInstance().isInFinalState(request);
        }
        
        public boolean isDefaultNotification(ActorNotificationRecipientSet notification)
        {
            RequestNotificationSettings settings = SampleManager.getInstance().getRequestNotificationSettings(getContainer());
            if (settings.getDefaultEmailNotifyEnum() == RequestNotificationSettings.DefaultEmailNotifyEnum.All)
                return true;        // All should be checked
            else if (settings.getDefaultEmailNotifyEnum() == RequestNotificationSettings.DefaultEmailNotifyEnum.None)
                return false;       // None should be checked
            // Otherwise use Actor Notification

            Integer requirementActorId = _requirement.getActorId();
            Integer notificationActorId = notification.getActor() != null ? notification.getActor().getRowId() : null;
            Integer requirementSiteId = _requirement.getSiteId();
            Integer notificationSiteId = notification.getLocation() != null ? notification.getLocation().getRowId() : null;
            return nullSafeEqual(requirementActorId, notificationActorId) &&
                    nullSafeEqual(requirementSiteId, notificationSiteId);
        }

        public GridView getHistoryView()
        {
            return _historyView;
        }

        public SampleRequestRequirement getRequirement()
        {
            return _requirement;
        }

        public List<ActorNotificationRecipientSet> getPossibleNotifications()
        {
            return _possibleNotifications;
        }

        public boolean isRequestManager()
        {
            return _requestManager;
        }

        public boolean isFinalState()
        {
            return _finalState;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ManageRequirementAction extends FormViewAction<ManageRequirementForm>
    {
        private SampleRequest _sampleRequest;
        public void validateCommand(ManageRequirementForm target, Errors errors)
        {
        }

        public ModelAndView getView(ManageRequirementForm form, boolean reshow, BindException errors) throws Exception
        {
            _sampleRequest = SampleManager.getInstance().getRequest(getContainer(), form.getId());
            final SampleRequestRequirement requirement =
                    SampleManager.getInstance().getRequirementsProvider().getRequirement(getContainer(), form.getRequirementId());
            if (_sampleRequest == null || requirement == null || requirement.getRequestId() != form.getId())
                throw new NotFoundException();

            return new JspView<>("/org/labkey/study/view/samples/manageRequirement.jsp",
                    new ManageRequirementBean(getViewContext(), _sampleRequest, requirement), errors);
        }

        public boolean handlePost(final ManageRequirementForm form, BindException errors) throws Exception
        {
            if (!getContainer().hasPermission(getUser(), ManageRequestsPermission.class))
                throw new UnauthorizedException("You do not have permission to update requirements!");

            _sampleRequest = SampleManager.getInstance().getRequest(getContainer(), form.getId());
            final SampleRequestRequirement requirement =
                    SampleManager.getInstance().getRequirementsProvider().getRequirement(getContainer(), form.getRequirementId());
            if (_sampleRequest == null || requirement == null || requirement.getRequestId() != form.getId())
                throw new NotFoundException();

            List<AttachmentFile> files = getAttachmentFileList();
            SampleManager.RequestEventType eventType;
            StringBuilder comment = new StringBuilder();
            comment.append(requirement.getRequirementSummary());
            String eventSummary;
            if (form.isComplete() != requirement.isComplete())
            {
                SampleRequestRequirement clone = requirement.createMutable();
                clone.setComplete(form.isComplete());
                SampleManager.getInstance().updateRequestRequirement(getUser(), clone);
                eventType = SampleManager.RequestEventType.REQUEST_STATUS_CHANGED;
                comment.append("\nStatus changed to ").append(form.isComplete() ? "complete" : "incomplete");
                eventSummary = comment.toString();
            }
            else
            {
                eventType = SampleManager.RequestEventType.COMMENT_ADDED;
                eventSummary = "Comment added.";
            }

            if (form.getComment() != null && form.getComment().length() > 0)
                comment.append("\n").append(form.getComment());

            SampleRequestEvent event;
            try
            {
                event = SampleManager.getInstance().createRequestEvent(getUser(), requirement, eventType, comment.toString(), files);
            }
            catch (Exception e)
            {
                errors.reject(ERROR_MSG, "The request could not be updated because of an unexpected error. " +
                        "Please report this problem to an administrator. Error details: "  + e.getMessage());
                return false;
            }
            try
            {

                List<? extends NotificationRecipientSet> recipients = getUtils().getNotifications(_sampleRequest, form.getNotificationIdPairs());
                DefaultRequestNotification notification = new DefaultRequestNotification(_sampleRequest, recipients,
                        eventSummary, event, form.getComment(), requirement, getViewContext());
                getUtils().sendNotification(notification, form.isEmailInactiveUsers(), errors);
            }
            catch (ConfigurationException | IOException e)
            {
                errors.reject(ERROR_MSG, "The request was updated successfully, but the notification failed: " +  e.getMessage());
                return false;
            }

            if (errors.hasErrors())
                return false;
            return true;
        }

        public ActionURL getSuccessURL(ManageRequirementForm manageRequirementForm)
        {
            return getManageRequestURL(_sampleRequest.getRowId());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendSpecimenRequestNavTrail(root, _sampleRequest.getRowId()).addChild("Manage Requirement");
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class RequestHistoryAction extends SimpleViewAction<IdForm>
    {
        private int _requestId;
        public ModelAndView getView(IdForm form, BindException errors) throws Exception
        {
            _requestId = form.getId();
            HtmlView header = new HtmlView(PageFlowUtil.textLink("View Request", new ActionURL(ManageRequestAction.class,getContainer()).addParameter("id",form.getId())));
            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("RequestId"), form.getId());
            GridView historyGrid = getUtils().getRequestEventGridView(getViewContext().getRequest(), errors, filter);
            return new VBox(header, historyGrid);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendSpecimenRequestNavTrail(root, _requestId).addChild("Request History");
        }
    }

    public static class DefaultRequirementsForm
    {
        private int _originatorActor;
        private String _originatorDescription;
        private int _providerActor;
        private String _providerDescription;
        private int _receiverActor;
        private String _receiverDescription;
        private int _generalActor;
        private String _generalDescription;
        private String _nextPage;

        public int getGeneralActor()
        {
            return _generalActor;
        }

        public void setGeneralActor(int generalActor)
        {
            _generalActor = generalActor;
        }

        public String getGeneralDescription()
        {
            return _generalDescription;
        }

        public void setGeneralDescription(String generalDescription)
        {
            _generalDescription = generalDescription;
        }

        public int getProviderActor()
        {
            return _providerActor;
        }

        public void setProviderActor(int providerActor)
        {
            _providerActor = providerActor;
        }

        public String getProviderDescription()
        {
            return _providerDescription;
        }

        public void setProviderDescription(String providerDescription)
        {
            _providerDescription = providerDescription;
        }

        public int getReceiverActor()
        {
            return _receiverActor;
        }

        public void setReceiverActor(int receiverActor)
        {
            _receiverActor = receiverActor;
        }

        public String getReceiverDescription()
        {
            return _receiverDescription;
        }

        public void setReceiverDescription(String receiverDescription)
        {
            _receiverDescription = receiverDescription;
        }

        public int getOriginatorActor()
        {
            return _originatorActor;
        }

        public void setOriginatorActor(int originatorActor)
        {
            _originatorActor = originatorActor;
        }

        public String getOriginatorDescription()
        {
            return _originatorDescription;
        }

        public void setOriginatorDescription(String originatorDescription)
        {
            _originatorDescription = originatorDescription;
        }

        public String getNextPage()
        {
            return _nextPage;
        }

        public void setNextPage(String nextPage)
        {
            _nextPage = nextPage;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class SpecimenRequestConfigRequired extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new JspView("/org/labkey/study/view/samples/configurationRequired.jsp");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return _appendNavTrail(root).addChild("Unable to Request Specimens");
        }
    }

    public static class ManageReqsBean
    {
        private SampleRequestRequirement[] _providerRequirements;
        private SampleRequestRequirement[] _receiverRequirements;
        private SampleRequestRequirement[] _generalRequirements;
        private SampleRequestRequirement[] _originatorRequirements;
        private SampleRequestActor[] _actors;

        public ManageReqsBean(User user, Container container) throws SQLException
        {
            RequirementProvider<SampleRequestRequirement, SampleRequestActor> provider =
                    SampleManager.getInstance().getRequirementsProvider();
            _originatorRequirements = provider.getDefaultRequirements(container,
                    SpecimenRequestRequirementType.ORIGINATING_SITE);
            _providerRequirements = provider.getDefaultRequirements(container,
                    SpecimenRequestRequirementType.PROVIDING_SITE);
            _receiverRequirements = provider.getDefaultRequirements(container,
                    SpecimenRequestRequirementType.RECEIVING_SITE);
            _generalRequirements = provider.getDefaultRequirements(container,
                    SpecimenRequestRequirementType.NON_SITE_BASED);
            _actors = provider.getActors(container);
        }

        public SampleRequestRequirement[] getGeneralRequirements()
        {
            return _generalRequirements;
        }

        public SampleRequestRequirement[] getReceiverRequirements()
        {
            return _receiverRequirements;
        }

        public SampleRequestRequirement[] getProviderRequirements()
        {
            return _providerRequirements;
        }

        public SampleRequestRequirement[] getOriginatorRequirements()
        {
            return _originatorRequirements;
        }

        public SampleRequestActor[] getActors()
        {
            return _actors;
        }
    }
    @RequiresPermissionClass(ManageRequestRequirementsPermission.class)
    public class ManageDefaultReqsAction extends FormViewAction<DefaultRequirementsForm>
    {
        public void validateCommand(DefaultRequirementsForm target, Errors errors)
        {
        }

        public ModelAndView getView(DefaultRequirementsForm defaultRequirementsForm, boolean reshow, BindException errors) throws Exception
        {
            getUtils().ensureSpecimenRequestsConfigured(true);
            return new JspView<>("/org/labkey/study/view/samples/manageDefaultReqs.jsp",
                new ManageReqsBean(getUser(), getContainer()));
        }

        public boolean handlePost(DefaultRequirementsForm form, BindException errors) throws Exception
        {
            getUtils().ensureSpecimenRequestsConfigured(true);
            createDefaultRequirement(form.getOriginatorActor(), form.getOriginatorDescription(), SpecimenRequestRequirementType.ORIGINATING_SITE);
            createDefaultRequirement(form.getProviderActor(), form.getProviderDescription(), SpecimenRequestRequirementType.PROVIDING_SITE);
            createDefaultRequirement(form.getReceiverActor(), form.getReceiverDescription(), SpecimenRequestRequirementType.RECEIVING_SITE);
            createDefaultRequirement(form.getGeneralActor(), form.getGeneralDescription(), SpecimenRequestRequirementType.NON_SITE_BASED);
            return true;
        }

        private void createDefaultRequirement(Integer actorId, String description, SpecimenRequestRequirementType type)
        {
            if (actorId != null && actorId.intValue() > 0 && description != null && description.length() > 0)
            {
                SampleRequestRequirement requirement = new SampleRequestRequirement();
                requirement.setContainer(getContainer());
                requirement.setActorId(actorId);
                requirement.setDescription(description);
                requirement.setRequestId(-1);
                SampleManager.getInstance().getRequirementsProvider().createDefaultRequirement(getUser(), requirement, type);
            }
        }

        public ActionURL getSuccessURL(DefaultRequirementsForm form)
        {
            if (form.getNextPage() != null && form.getNextPage().length() > 0)
                return new ActionURL(form.getNextPage());
            else
                return new ActionURL(StudyController.ManageStudyAction.class, getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("coordinateSpecimens#requirements");
            return _appendManageStudy(root).addChild("Manage Default Requirements");
        }
    }


    public static class EmailSpecimenListForm extends IdForm
    {
        private boolean _sendXls;
        private boolean _sendTsv;
        private String _comments;
        private String[] _notify;
        private String _listType;
        private boolean _emailInactiveUsers;

        public String getComments()
        {
            return _comments;
        }

        public void setComments(String comments)
        {
            _comments = comments;
        }

        public String[] getNotify()
        {
            return _notify;
        }

        public void setNotify(String[] notify)
        {
            _notify = notify;
        }

        public boolean isSendTsv()
        {
            return _sendTsv;
        }

        public void setSendTsv(boolean sendTsv)
        {
            _sendTsv = sendTsv;
        }

        public boolean isSendXls()
        {
            return _sendXls;
        }

        public void setSendXls(boolean sendXls)
        {
            _sendXls = sendXls;
        }

        public String getListType()
        {
            return _listType;
        }

        public void setListType(String listType)
        {
            _listType = listType;
        }

        public boolean isEmailInactiveUsers()
        {
            return _emailInactiveUsers;
        }

        public void setEmailInactiveUsers(boolean emailInactiveUsers)
        {
            _emailInactiveUsers = emailInactiveUsers;
        }
    }

    @RequiresPermissionClass(RequestSpecimensPermission.class)
    public class SubmitRequestAction extends RedirectAction<IdForm>
    {
        public void validateCommand(IdForm target, Errors errors)
        {
        }

        public boolean doAction(IdForm form, BindException errors) throws Exception
        {
            if (!SampleManager.getInstance().isSpecimenShoppingCartEnabled(getContainer()))
                throw new UnauthorizedException();

            SampleRequest request = SampleManager.getInstance().getRequest(getContainer(), form.getId());
            requiresEditRequestPermissions(request);
            List<Specimen> specimens = request.getSpecimens();
            if (specimens != null && specimens.size() > 0)
            {
                SampleRequestStatus newStatus = SampleManager.getInstance().getInitialRequestStatus(getContainer(), getUser(), true);
                request = request.createMutable();
                request.setStatusId(newStatus.getRowId());
                try
                {
                    SampleManager.getInstance().updateRequest(getUser(), request);
                    SampleManager.getInstance().createRequestEvent(getUser(), request,
                            SampleManager.RequestEventType.REQUEST_STATUS_CHANGED, "Request submitted for processing.", null);
                    if (SampleManager.getInstance().isSpecimenShoppingCartEnabled(getContainer()))
                    {
                        try
                        {
                            getUtils().sendNewRequestNotifications(request, errors);
                        }
                        catch (ConfigurationException e)
                        {
                            errors.reject(ERROR_MSG, e.getMessage());
                        }
                    }
                }
                catch (RequestabilityManager.InvalidRuleException e)
                {
                    errors.reject(ERROR_MSG, "The request could not be submitted because a requestability rule is configured incorrectly. " +
                                "Please report this problem to an administrator.  Error details: "  + e.getMessage());
                }
                catch (IOException e)
                {
                    errors.reject(ERROR_MSG, e.getMessage());
                }
            }
            else
            {
                errors.addError(new ObjectError("Specimen Request", new String[] {"NullError"}, null, "Only requests containing specimens can be submitted."));
            }

            if (errors.hasErrors())
                return false;
            return true;
        }

        public ActionURL getSuccessURL(IdForm idForm)
        {
            ActionURL successURL = getManageRequestURL(idForm.getId());
            successURL.addParameter(ManageRequestForm.PARAMS.submissionResult, Boolean.TRUE.toString());
            return successURL;
        }
    }

    @RequiresPermissionClass(RequestSpecimensPermission.class)
    public class DeleteRequestAction extends RedirectAction<IdForm>
    {
        public void validateCommand(IdForm target, Errors errors)
        {
        }

        public boolean doAction(IdForm form, BindException errors) throws Exception
        {
            SampleRequest request = SampleManager.getInstance().getRequest(getContainer(), form.getId());
            requiresEditRequestPermissions(request);
            SampleRequestStatus cartStatus = SampleManager.getInstance().getRequestShoppingCartStatus(getContainer(), getUser());
            if (request.getStatusId() != cartStatus.getRowId())
                throw new UnauthorizedException();

            try
            {
                SampleManager.getInstance().deleteRequest(getUser(), request);
            }
            catch (RequestabilityManager.InvalidRuleException e)
            {
                errors.reject(ERROR_MSG, "The request could not be canceled because a requestability rule is configured incorrectly. " +
                            "Please report this problem to an administrator.  Error details: "  + e.getMessage());
            }
            return true;
        }

        public ActionURL getSuccessURL(IdForm idForm)
        {
            return new ActionURL(ViewRequestsAction.class, getContainer());
        }
    }

    @RequiresPermissionClass(ManageRequestsPermission.class)
    public class EmailLabSpecimenListsAction extends FormHandlerAction<EmailSpecimenListForm>
    {
        public void validateCommand(EmailSpecimenListForm target, Errors errors)
        {
        }

        public boolean handlePost(EmailSpecimenListForm form, BindException errors) throws Exception
        {
            final SampleRequest request = SampleManager.getInstance().getRequest(getContainer(), form.getId());
            if (request == null)
                throw new NotFoundException();

            LocationImpl receivingLocation = StudyManager.getInstance().getLocation(getContainer(), request.getDestinationSiteId());
            if (receivingLocation == null)
                throw new NotFoundException();

            final LabSpecimenListsBean.Type type;
            try
            {
                type = LabSpecimenListsBean.Type.valueOf(form.getListType());
            }
            catch (IllegalArgumentException e)
            {
                throw new NotFoundException();
            }

            Map<LocationImpl, List<ActorNotificationRecipientSet>> notifications = new HashMap<>();
            if (form.getNotify() != null)
            {
                for (String tuple : form.getNotify())
                {
                    String[] idStrs = tuple.split(",");
                    if (idStrs.length != 3)
                        throw new IllegalStateException("Expected triple.");
                    int[] ids = new int[3];
                    for (int i = 0; i < 3; i++)
                        ids[i] = Integer.parseInt(idStrs[i]);
                    LocationImpl originatingOrProvidingLocation = StudyManager.getInstance().getLocation(getContainer(), ids[0]);
                    SampleRequestActor notifyActor = SampleManager.getInstance().getRequirementsProvider().getActor(getContainer(), ids[1]);
                    LocationImpl notifyLocation = null;
                    if (notifyActor.isPerSite() && ids[2] >= 0)
                        notifyLocation = StudyManager.getInstance().getLocation(getContainer(), ids[2]);
                    List<ActorNotificationRecipientSet> emailRecipients = notifications.get(originatingOrProvidingLocation);
                    if (emailRecipients == null)
                    {
                        emailRecipients = new ArrayList<>();
                        notifications.put(originatingOrProvidingLocation, emailRecipients);
                    }
                    emailRecipients.add(new ActorNotificationRecipientSet(notifyActor, notifyLocation));
                }


                for (final LocationImpl originatingOrProvidingLocation : notifications.keySet())
                {
                    List<AttachmentFile> formFiles = getAttachmentFileList();
                    if (form.isSendTsv())
                    {
                        TSVGridWriter tsvWriter = getUtils().getSpecimenListTsvWriter(request, originatingOrProvidingLocation, receivingLocation, type);
                        StringBuilder tsvBuilder = new StringBuilder();
                        tsvWriter.write(tsvBuilder);
                        formFiles.add(new ByteArrayAttachmentFile(tsvWriter.getFilenamePrefix() + ".tsv", tsvBuilder.toString().getBytes(), "text/tsv"));
                    }

                    if (form.isSendXls())
                    {
                        OutputStream ostream = null;
                        try
                        {
                            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                            ostream = new BufferedOutputStream(byteStream);
                            ExcelWriter xlsWriter = getUtils().getSpecimenListXlsWriter(request, originatingOrProvidingLocation, receivingLocation, type);
                            xlsWriter.write(ostream);
                            ostream.flush();
                            formFiles.add(new ByteArrayAttachmentFile(xlsWriter.getFilenamePrefix() + ".xls", byteStream.toByteArray(), "application/vnd.ms-excel"));
                        }
                        finally
                        {
                            if (ostream != null)
                                ostream.close();
                        }
                    }

                    final StringBuilder content = new StringBuilder();
                    if (form.getComments() != null)
                        content.append(form.getComments());

                    String header = type.getDisplay() + " location notification of specimen shipment to " + receivingLocation.getDisplayName();
                    try
                    {
                        SampleRequestEvent event = SampleManager.getInstance().createRequestEvent(getUser(), request,
                            SampleManager.RequestEventType.SPECIMEN_LIST_GENERATED, header + "\n" + content.toString(), formFiles);

                        final Container container = getContainer();
                        final User user = getUser();
                        List<ActorNotificationRecipientSet> emailRecipients = notifications.get(originatingOrProvidingLocation);
                            DefaultRequestNotification notification = new DefaultRequestNotification(request, emailRecipients,
                                header, event, content.toString(), null, getViewContext())
                        {
                            @Override
                            protected List<Specimen> getSpecimenList()
                            {
                                SimpleFilter filter = getUtils().getSpecimenListFilter(getSampleRequest(), originatingOrProvidingLocation, type);
                                return SampleManager.getInstance().getSpecimens(container, user, filter);
//                                return new TableSelector(StudySchema.getInstance().getTableInfoSpecimenDetail(container), filter, null).getArrayList(Specimen.class);
                            }

                        };
                        getUtils().sendNotification(notification, form.isEmailInactiveUsers(), errors);
                    }
                    catch (ConfigurationException | IOException e)
                    {
                        errors.reject(ERROR_MSG, e.getMessage());
                    }

                    if (errors.hasErrors())
                        break;
                }
            }

            if (errors.hasErrors())
                return false;
            return true;
        }

        public ActionURL getSuccessURL(EmailSpecimenListForm emailSpecimenListForm)
        {
            return getManageRequestURL(emailSpecimenListForm.getId());
        }
    }

    public static class ExportSiteForm extends IdForm
    {
        private String _export;
        private String _specimenIds;
        private String _listType;
        private int _sourceSiteId;
        private int _destSiteId;
        private boolean _exportAsWebPage;       // test helper


        public String getExport()
        {
            return _export;
        }

        public void setExport(String export)
        {
            _export = export;
        }

        public String getSpecimenIds()
        {
            return _specimenIds;
        }

        public void setSpecimenIds(String specimenIds)
        {
            _specimenIds = specimenIds;
        }

        public int getDestSiteId()
        {
            return _destSiteId;
        }

        public void setDestSiteId(int destSiteId)
        {
            _destSiteId = destSiteId;
        }

        public int getSourceSiteId()
        {
            return _sourceSiteId;
        }

        public void setSourceSiteId(int sourceSiteId)
        {
            _sourceSiteId = sourceSiteId;
        }

        public String getListType()
        {
            return _listType;
        }

        public void setListType(String listType)
        {
            _listType = listType;
        }

        public boolean isExportAsWebPage()
        {
            return _exportAsWebPage;
        }

        public void setExportAsWebPage(boolean exportAsWebPage)
        {
            _exportAsWebPage = exportAsWebPage;
        }
    }

    @RequiresPermissionClass(ManageRequestsPermission.class)
    public class DownloadSpecimenListAction extends SimpleViewAction<ExportSiteForm>
    {
        public ModelAndView getView(ExportSiteForm form, BindException errors) throws Exception
        {
            SampleRequest sampleRequest = SampleManager.getInstance().getRequest(getContainer(), form.getId());
            LocationImpl sourceLocation = StudyManager.getInstance().getLocation(getContainer(), form.getSourceSiteId());
            LocationImpl destLocation = StudyManager.getInstance().getLocation(getContainer(), form.getDestSiteId());
            if (sampleRequest == null || sourceLocation == null || destLocation == null)
                throw new NotFoundException();

            LabSpecimenListsBean.Type type = LabSpecimenListsBean.Type.valueOf(form.getListType());
            if (null != form.getExport())
            {
                if (EXPORT_TSV.equals(form.getExport()))
                {
                    TSVGridWriter writer = getUtils().getSpecimenListTsvWriter(sampleRequest, sourceLocation, destLocation, type);
                    writer.setExportAsWebPage(form.isExportAsWebPage());
                    writer.write(getViewContext().getResponse());
                }
                else if (EXPORT_XLS.equals(form.getExport()))
                {
                    ExcelWriter writer = getUtils().getSpecimenListXlsWriter(sampleRequest, sourceLocation, destLocation, type);
                    writer.write(getViewContext().getResponse());
                }
            }
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            throw new UnsupportedOperationException("Not Yet Implemented");
        }
    }

    private static final String EXPORT_TSV = "tsv";
    private static final String EXPORT_XLS = "xls";

    @RequiresPermissionClass(ManageRequestsPermission.class)
    public class LabSpecimenListsAction extends SimpleViewAction<LabSpecimenListsForm>
    {
        private int _requestId;
        private LabSpecimenListsBean.Type _type;
        public ModelAndView getView(LabSpecimenListsForm form, BindException errors) throws Exception
        {
            SampleRequest request = SampleManager.getInstance().getRequest(getContainer(), form.getId());
            if (request == null  || form.getListType() == null)
                throw new NotFoundException();

            _requestId = request.getRowId();

            try
            {
                _type = LabSpecimenListsBean.Type.valueOf(form.getListType());
            }
            catch (IllegalArgumentException e)
            {
                // catch malformed/old URL case, where the posted value of 'type' isn't a valid Type:
                throw new NotFoundException("Unrecognized list type.");
            }
            LabSpecimenListsBean bean = new LabSpecimenListsBean(getUtils(), request, _type);
            bean.setExportAsWebPage(form.isExportAsWebPage());

            return new JspView<>("/org/labkey/study/view/samples/labSpecimenLists.jsp", bean);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendSpecimenRequestNavTrail(root, _requestId).addChild(_type.getDisplay() + " Lab Vial Lists");
        }
    }

    public static class LabSpecimenListsForm extends IdForm
    {
        private String _listType;
        private boolean _exportAsWebPage;

        public String getListType()
        {
            return _listType;
        }

        public void setListType(String listType)
        {
            _listType = listType;
        }

        public boolean isExportAsWebPage()
        {
            return _exportAsWebPage;
        }

        public void setExportAsWebPage(boolean exportAsWebPage)
        {
            _exportAsWebPage = exportAsWebPage;
        }
    }

    public static class LabSpecimenListsBean
    {
        public static enum Type
        {
            PROVIDING("Providing"),
            ORIGINATING("Originating");

            private String _display;
            private Type(String display)
            {
                _display = display;
            }

            public String getDisplay()
            {
                return _display;
            }
        }

        private SampleRequest _sampleRequest;
        private Map<Integer, List<Specimen>> _specimensBySiteId;
        private List<ActorNotificationRecipientSet> _possibleNotifications;
        private Type _type;
        private boolean _requirementsComplete;
        private SpecimenUtils _utils;
        private boolean _exportAsWebPage;

        public LabSpecimenListsBean(SpecimenUtils utils, SampleRequest sampleRequest, LabSpecimenListsBean.Type type)
        {
            _sampleRequest = sampleRequest;
            _utils = utils;
            _type = type;
            _requirementsComplete = true;
            for (int i = 0; i < sampleRequest.getRequirements().length && _requirementsComplete; i++)
            {
                SampleRequestRequirement requirement = sampleRequest.getRequirements()[i];
                _requirementsComplete = requirement.isComplete();
            }
        }

        public SampleRequest getSampleRequest()
        {
            return _sampleRequest;
        }

        private synchronized Map<Integer, List<Specimen>> getSpecimensBySiteId()
        {
            if (_specimensBySiteId == null)
            {
                _specimensBySiteId = new HashMap<>();
                List<Specimen> specimens = _sampleRequest.getSpecimens();
                for (Specimen specimen : specimens)
                {
                    LocationImpl location;
                    if (_type == LabSpecimenListsBean.Type.ORIGINATING)
                        location = SampleManager.getInstance().getOriginatingLocation(specimen);
                    else
                        location = SampleManager.getInstance().getCurrentLocation(specimen);
                    if (location != null)
                    {
                        List<Specimen> current = _specimensBySiteId.get(location.getRowId());
                        if (current == null)
                        {
                            current = new ArrayList<>();
                            _specimensBySiteId.put(location.getRowId(), current);
                        }
                        current.add(specimen);
                    }
                }
            }
            return _specimensBySiteId;
        }

        public synchronized List<ActorNotificationRecipientSet> getPossibleNotifications()
        {
            if (_possibleNotifications == null)
                _possibleNotifications = _utils.getPossibleNotifications(_sampleRequest);
            return _possibleNotifications;
        }

        public Set<LocationImpl> getLabs()
        {
            Map<Integer, List<Specimen>> siteIdToSpecimens = getSpecimensBySiteId();
            Set<LocationImpl> locations = new HashSet<>(siteIdToSpecimens.size());
            for (Integer locationId : siteIdToSpecimens.keySet())
                locations.add(StudyManager.getInstance().getLocation(_sampleRequest.getContainer(), locationId));
            return locations;
        }

        public List<Specimen> getSpecimens(LocationImpl location)
        {
            Map<Integer, List<Specimen>> siteSpecimenLists = getSpecimensBySiteId();
            return siteSpecimenLists.get(location.getRowId());
        }

        public Type getType()
        {
            return _type;
        }

        public boolean isRequirementsComplete()
        {
            return _requirementsComplete;
        }

        public boolean isExportAsWebPage()
        {
            return _exportAsWebPage;
        }

        public void setExportAsWebPage(boolean exportAsWebPage)
        {
            _exportAsWebPage = exportAsWebPage;
        }
    }

    public abstract class SpecimenVisitReportAction<FormType extends SpecimenVisitReportParameters> extends SimpleViewAction<FormType>
    {
        private FormType _form;

        public SpecimenVisitReportAction(Class<FormType> beanClass)
        {
            super(beanClass);
        }

        public ModelAndView getView(FormType specimenVisitReportForm, BindException errors) throws Exception
        {
            _form = specimenVisitReportForm;
            if (specimenVisitReportForm.isExcelExport())
            {
                SpecimenReportExcelWriter writer = new SpecimenReportExcelWriter(specimenVisitReportForm);
                writer.write(getViewContext().getResponse());
                return null;
            }
            else
            {
                JspView<FormType> reportView = new JspView<>("/org/labkey/study/view/samples/specimenVisitReport.jsp", specimenVisitReportForm);
                reportView.setIsWebPart(false);
                if (this.isPrint())
                    return reportView;
                else
                {
                    return new VBox(new JspView<>("/org/labkey/study/view/samples/autoReportList.jsp",
                                    new ReportConfigurationBean(specimenVisitReportForm, false)), reportView);
                }
            }
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root = appendBaseSpecimenNavTrail(root);
            root.addChild("Available Reports", new ActionURL(AutoReportListAction.class, getContainer()));
            return root.addChild("Specimen Report: " + _form.getLabel());
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class TypeSummaryReportAction extends SpecimenVisitReportAction<TypeSummaryReportFactory>
    {
        public TypeSummaryReportAction()
        {
            super(TypeSummaryReportFactory.class);
        }
        // no implementation needed; this action exists only to provide an entry point
        // with request->bean translation for this report type.
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class TypeParticipantReportAction extends SpecimenVisitReportAction<TypeParticipantReportFactory>
    {
        public TypeParticipantReportAction()
        {
            super(TypeParticipantReportFactory.class);
        }
        // no implementation needed; this action exists only to provide an entry point
        // with request->bean translation for this report type.
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class RequestReportAction extends SpecimenVisitReportAction<RequestReportFactory>
    {
        public RequestReportAction()
        {
            super(RequestReportFactory.class);
        }
        // no implementation needed; this action exists only to provide an entry point
        // with request->bean translation for this report type.
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class TypeCohortReportAction extends SpecimenVisitReportAction<TypeCohortReportFactory>
    {
        public TypeCohortReportAction()
        {
            super(TypeCohortReportFactory.class);
        }
        // no implementation needed; this action exists only to provide an entry point
        // with request->bean translation for this report type.
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class RequestSiteReportAction extends SpecimenVisitReportAction<RequestLocationReportFactory>
    {
        public RequestSiteReportAction()
        {
            super(RequestLocationReportFactory.class);
        }
        // no implementation needed; this action exists only to provide an entry point
        // with request->bean translation for this report type.
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ParticipantSummaryReportAction extends SpecimenVisitReportAction<ParticipantSummaryReportFactory>
    {
        public ParticipantSummaryReportAction()
        {
            super(ParticipantSummaryReportFactory.class);
        }
        // no implementation needed; this action exists only to provide an entry point
        // with request->bean translation for this report type.
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ParticipantTypeReportAction extends SpecimenVisitReportAction<ParticipantTypeReportFactory>
    {
        public ParticipantTypeReportAction()
        {
            super(ParticipantTypeReportFactory.class);
        }
        // no implementation needed; this action exists only to provide an entry point
        // with request->bean translation for this report type.
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ParticipantSiteReportAction extends SpecimenVisitReportAction<ParticipantSiteReportFactory>
    {
        public ParticipantSiteReportAction()
        {
            super(ParticipantSiteReportFactory.class);
        }
        // no implementation needed; this action exists only to provide an entry point
        // with request->bean translation for this report type.
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class RequestEnrollmentSiteReportAction extends SpecimenVisitReportAction<RequestEnrollmentSiteReportFactory>
    {
        public RequestEnrollmentSiteReportAction()
        {
            super(RequestEnrollmentSiteReportFactory.class);
        }
        // no implementation needed; this action exists only to provide an entry point
        // with request->bean translation for this report type.
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class RequestParticipantReportAction extends SpecimenVisitReportAction<RequestParticipantReportFactory>
    {
        public RequestParticipantReportAction()
        {
            super(RequestParticipantReportFactory.class);
        }
        // no implementation needed; this action exists only to provide an entry point
        // with request->bean translation for this report type.
    }

    public static class ReportConfigurationBean
    {
        private static final String COUNTS_BY_DERIVATIVE_TYPE_TITLE = "Collected Vials by Type and Timepoint";
        private static final String REQUESTS_BY_DERIVATIVE_TYPE_TITLE = "Requested Vials by Type and Timepoint";
        private Map<String, List<SpecimenVisitReportParameters>> _reportFactories = new LinkedHashMap<>();
        private boolean _listView = true;
        private ViewContext _viewContext;
        private boolean _hasReports = true;

        public ReportConfigurationBean(ViewContext viewContext) throws ServletException
        {
            Study study = getStudy(viewContext.getContainer());
            _viewContext = viewContext;
            registerReportFactory(COUNTS_BY_DERIVATIVE_TYPE_TITLE, new TypeSummaryReportFactory());
            registerReportFactory(COUNTS_BY_DERIVATIVE_TYPE_TITLE, new TypeParticipantReportFactory());
            if (StudyManager.getInstance().showCohorts(_viewContext.getContainer(), _viewContext.getUser()))
                registerReportFactory(COUNTS_BY_DERIVATIVE_TYPE_TITLE, new TypeCohortReportFactory());
            if (study != null)
            {
                boolean enableSpecimenRequest = SampleManager.getInstance().getRepositorySettings(study.getContainer()).isEnableRequests();
                if (!study.isAncillaryStudy() && !study.isSnapshotStudy() && enableSpecimenRequest)
                {
                    registerReportFactory(REQUESTS_BY_DERIVATIVE_TYPE_TITLE, new RequestReportFactory());
                    registerReportFactory(REQUESTS_BY_DERIVATIVE_TYPE_TITLE, new RequestLocationReportFactory());
                    registerReportFactory(REQUESTS_BY_DERIVATIVE_TYPE_TITLE, new RequestEnrollmentSiteReportFactory());
                    registerReportFactory(REQUESTS_BY_DERIVATIVE_TYPE_TITLE, new RequestParticipantReportFactory());
                }
                String subjectNoun = StudyService.get().getSubjectNounSingular(viewContext.getContainer());
                registerReportFactory("Collected Vials by " + subjectNoun + " By Timepoint", new ParticipantSummaryReportFactory());
                registerReportFactory("Collected Vials by " + subjectNoun + " By Timepoint", new ParticipantTypeReportFactory());
                registerReportFactory("Collected Vials by " + subjectNoun + " By Timepoint", new ParticipantSiteReportFactory());
            }
        }

        public ReportConfigurationBean(SpecimenVisitReportParameters singleFactory, boolean listView) throws ServletException
        {
            _listView = listView;
            _viewContext = singleFactory.getViewContext();
            assert (_viewContext != null) : "Expected report factory to be instantiated by Spring.";
            registerReportFactory(COUNTS_BY_DERIVATIVE_TYPE_TITLE, singleFactory);
            _hasReports = getStudy(_viewContext.getContainer()) != null && singleFactory.getReports().size() > 0;
        }

        private void registerReportFactory(String category, SpecimenVisitReportParameters factory)
        {
            // we have to explicitly set the view context for these reports, since the factories aren't being newed-up by Spring in the usual way:
            factory.setViewContext(_viewContext);
            List<SpecimenVisitReportParameters> factories = _reportFactories.get(category);

            if (factories == null)
            {
                factories = new ArrayList<>();
                _reportFactories.put(category, factories);
            }

            factories.add(factory);
        }

        public Set<String> getCategories()
        {
            return _reportFactories.keySet();
        }

        public List<SpecimenVisitReportParameters> getFactories(String category)
        {
            return _reportFactories.get(category);
        }

        public boolean isListView()
        {
            return _listView;
        }

        public Map<String, CustomView> getCustomViews(ViewContext context)
        {
            // 13485 - Use provider to handle NULL study
            UserSchema schema = QueryService.get().getUserSchema(context.getUser(), context.getContainer(), SchemaKey.fromParts(StudyQuerySchema.SCHEMA_NAME));
            QueryDefinition def = QueryService.get().createQueryDefForTable(schema, "SpecimenDetail");
            return def.getCustomViews(context.getUser(), context.getRequest(), false, false);
        }

        public boolean hasReports()
        {
            return _hasReports;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class AutoReportListAction extends SimpleViewAction
    {
        public ModelAndView getView(Object form, BindException errors) throws Exception
        {
            return new JspView<>("/org/labkey/study/view/samples/autoReportList.jsp", new ReportConfigurationBean(getViewContext()));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root = appendBaseSpecimenNavTrail(root);
            return root.addChild("Specimen Reports");
        }
    }

    private static enum CommentsConflictResolution
    {
        SKIP,
        APPEND,
        REPLACE
    }

    public static class UpdateSpecimenCommentsForm
    {
        private String _comments;
        private int[] _rowId;
        private boolean _fromGroupedView;
        private String _referrer;
        private boolean _saveCommentsPost;
        private String _conflictResolve;
        private Boolean _qualityControlFlag;

        public String getComments()
        {
            return _comments;
        }

        public void setComments(String comments)
        {
            _comments = comments;
        }

        public int[] getRowId()
        {
            return _rowId;
        }

        public void setRowId(int[] rowId)
        {
            _rowId = rowId;
        }

        public boolean isFromGroupedView()
        {
            return _fromGroupedView;
        }

        public void setFromGroupedView(boolean fromGroupedView)
        {
            _fromGroupedView = fromGroupedView;
        }

        public String getReferrer()
        {
            return _referrer;
        }

        public void setReferrer(String referrer)
        {
            _referrer = referrer;
        }

        public boolean isSaveCommentsPost()
        {
            return _saveCommentsPost;
        }

        public void setSaveCommentsPost(boolean saveCommentsPost)
        {
            _saveCommentsPost = saveCommentsPost;
        }

        public String getConflictResolve()
        {
            return _conflictResolve;
        }

        public void setConflictResolve(String conflictResolve)
        {
            _conflictResolve = conflictResolve;
        }

        public CommentsConflictResolution getConflictResolveEnum()
        {
            return _conflictResolve == null ? CommentsConflictResolution.REPLACE : CommentsConflictResolution.valueOf(_conflictResolve);
        }

        public Boolean isQualityControlFlag()
        {
            return _qualityControlFlag;
        }

        public void setQualityControlFlag(Boolean qualityControlFlag)
        {
            _qualityControlFlag = qualityControlFlag;
        }
    }
    
    public static class UpdateParticipantCommentsForm extends UpdateSpecimenCommentsForm
    {
        private boolean _copyToParticipant;
        private boolean _deleteVialComment;
        private int _copySampleId;
        private String _copyParticipantId;

        public boolean isCopyToParticipant()
        {
            return _copyToParticipant;
        }

        public void setCopyToParticipant(boolean copyToParticipant)
        {
            _copyToParticipant = copyToParticipant;
        }

        public boolean isDeleteVialComment()
        {
            return _deleteVialComment;
        }

        public void setDeleteVialComment(boolean deleteVialComment)
        {
            _deleteVialComment = deleteVialComment;
        }

        public int getCopySampleId()
        {
            return _copySampleId;
        }

        public void setCopySampleId(int copySampleId)
        {
            _copySampleId = copySampleId;
        }

        public String getCopyParticipantId()
        {
            return _copyParticipantId;
        }

        public void setCopyParticipantId(String copyParticipantId)
        {
            _copyParticipantId = copyParticipantId;
        }
    }

    public static class UpdateSpecimenCommentsBean extends SamplesViewBean
    {
        private String _referrer;
        private String _currentComment;
        private boolean _mixedComments;
        private boolean _currentFlagState;
        private boolean _mixedFlagState;
        private Map<String, Map<String, Long>> _participantVisitMap = new TreeMap<>();

        public UpdateSpecimenCommentsBean(ViewContext context, List<Specimen> samples, String referrer) throws ServletException
        {
            super(context, samples, false, false, true, true);
            _referrer = referrer;
            try
            {
                Map<Specimen, SpecimenComment> currentComments = SampleManager.getInstance().getSpecimenComments(samples);
                _participantVisitMap = generateParticipantVisitMap(samples, BaseStudyController.getStudyRedirectIfNull(context.getContainer()));
                _mixedComments = false;
                _mixedFlagState = false;
                SpecimenComment prevComment = currentComments.get(samples.get(0));
                for (int i = 1; i < samples.size() && (!_mixedFlagState || !_mixedComments); i++)
                {
                    SpecimenComment comment = currentComments.get(samples.get(i));

                    // a missing comment indicates a 'false' for history conflict:
                    boolean currentFlagState = comment != null && comment.isQualityControlFlag();
                    boolean previousFlagState = prevComment != null && prevComment.isQualityControlFlag();
                    _mixedFlagState = _mixedFlagState || currentFlagState != previousFlagState;
                    String currentCommentString = comment != null ? comment.getComment() : null;
                    String previousCommentString = prevComment != null ? prevComment.getComment() : null;
                    _mixedComments = _mixedComments || !Objects.equals(previousCommentString, currentCommentString);
                    prevComment = comment;
                }
                if (!_mixedComments && prevComment != null)
                    _currentComment = prevComment.getComment();
                _currentFlagState = _mixedFlagState || (prevComment != null && prevComment.isQualityControlFlag());
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }

        protected Map<String, Map<String, Long>> generateParticipantVisitMap(List<Specimen> samples, Study study)
        {
            Map<String, Map<String, Long>> pvMap = new TreeMap<>();

            for (Specimen sample : samples)
            {
                Double visit = sample.getVisitValue();
                if (visit != null)
                {
                    String ptid = sample.getPtid();
                    Visit v = StudyManager.getInstance().getVisitForSequence(study, visit.doubleValue());
                    if (ptid != null && v != null)
                    {
                        if (!pvMap.containsKey(ptid))
                            pvMap.put(ptid, new HashMap<String, Long>());
                        pvMap.get(ptid).put(v.getDisplayString(), sample.getRowId());
                    }
                }
            }
            return pvMap;
        }

        public String getReferrer()
        {
            return _referrer;
        }

        public String getCurrentComment()
        {
            return _currentComment;
        }

        public boolean isMixedComments()
        {
            return _mixedComments;
        }

        public boolean isCurrentFlagState()
        {
            return _currentFlagState;
        }

        public boolean isMixedFlagState()
        {
            return _mixedFlagState;
        }

        public Map<String, Map<String, Long>> getParticipantVisitMap()
        {
            return _participantVisitMap;
        }
    }

    @RequiresPermissionClass(SetSpecimenCommentsPermission.class)
    public class ClearCommentsAction extends RedirectAction<UpdateSpecimenCommentsForm>
    {
        public ActionURL getSuccessURL(UpdateSpecimenCommentsForm updateSpecimenCommentsForm)
        {
            return new ActionURL(updateSpecimenCommentsForm.getReferrer());
        }

        public boolean doAction(UpdateSpecimenCommentsForm updateSpecimenCommentsForm, BindException errors) throws Exception
        {
            List<Specimen> selectedVials = getUtils().getSpecimensFromPost(updateSpecimenCommentsForm.isFromGroupedView(), false);
            if (selectedVials != null)
            {
                for (Specimen specimen : selectedVials)
                {
                    // use the currently saved history conflict state; if it's been forced before, this will prevent it
                    // from being cleared.
                    SpecimenComment comment = SampleManager.getInstance().getSpecimenCommentForVial(specimen);
                    if (comment != null)
                    {
                        SampleManager.getInstance().setSpecimenComment(getUser(), specimen, null,
                                comment.isQualityControlFlag(), comment.isQualityControlFlagForced());
                    }
                }
            }
            return true;
        }

        public void validateCommand(UpdateSpecimenCommentsForm target, Errors errors)
        {
        }
    }

    @RequiresPermissionClass(SetSpecimenCommentsPermission.class)
    public class UpdateCommentsAction extends FormViewAction<UpdateParticipantCommentsForm>
    {
        private ActionURL _successUrl;

        public void validateCommand(UpdateParticipantCommentsForm specimenCommentsForm, Errors errors)
        {
            if (specimenCommentsForm.isSaveCommentsPost() &&
                    (specimenCommentsForm.getRowId() == null ||
                     specimenCommentsForm.getRowId().length == 0))
            {
                errors.reject(null, "No vials were selected.");
            }
        }

        public ModelAndView getView(UpdateParticipantCommentsForm specimenCommentsForm, boolean reshow, BindException errors) throws Exception
        {
            List<Specimen> selectedVials = getUtils().getSpecimensFromPost(specimenCommentsForm.isFromGroupedView(), false);

            if (selectedVials == null || selectedVials.size() == 0)
            {
                // are the vial IDs on the URL?
                int[] rowId = specimenCommentsForm.getRowId();
                if (rowId != null && rowId.length > 0)
                {
                    List<Specimen> specimens = SampleManager.getInstance().getSpecimens(getContainer(), getUser(), rowId);
                    selectedVials = new ArrayList<>(specimens);
                }
                if (selectedVials == null || selectedVials.size() == 0)
                    return new HtmlView("No vials selected.  " + PageFlowUtil.textLink("back", "javascript:back()"));
            }

            return new JspView<>("/org/labkey/study/view/samples/updateComments.jsp",
                    new UpdateSpecimenCommentsBean(getViewContext(), selectedVials, specimenCommentsForm.getReferrer()), errors);
        }

        public boolean handlePost(UpdateParticipantCommentsForm commentsForm, BindException errors) throws Exception
        {
            if (commentsForm.getRowId() == null)
                return false;

            User user = getUser();
            Container container = getContainer();
            List<Specimen> vials = new ArrayList<>();
            for (int rowId : commentsForm.getRowId())
                vials.add(SampleManager.getInstance().getSpecimen(container, user, rowId));

            Map<Specimen, SpecimenComment> currentComments = SampleManager.getInstance().getSpecimenComments(vials);

            // copying or moving vial comments to participant level comments
            if (commentsForm.isCopyToParticipant())
            {
                if (commentsForm.getCopySampleId() != -1)
                {
                    Specimen vial = SampleManager.getInstance().getSpecimen(container, user, commentsForm.getCopySampleId());
                    if (vial != null)
                    {
                        _successUrl = new ActionURL(CopyParticipantCommentAction.class, container).
                                addParameter(ParticipantCommentForm.params.participantId, vial.getPtid()).
                                addParameter(ParticipantCommentForm.params.visitId, String.valueOf(vial.getVisitValue())).
                                addParameter(ParticipantCommentForm.params.comment, commentsForm.getComments()).
                                addReturnURL(new URLHelper(commentsForm.getReferrer()));
                    }
                }
                else
                {
                    _successUrl = new ActionURL(CopyParticipantCommentAction.class, container).
                            addParameter(ParticipantCommentForm.params.participantId, commentsForm.getCopyParticipantId()).
                            addParameter(ParticipantCommentForm.params.comment, commentsForm.getComments()).
                            addReturnURL(new URLHelper(commentsForm.getReferrer()));
                }

                // delete existing vial comments if move is specified
                if (commentsForm.isDeleteVialComment())
                {
                    for (Specimen specimen : vials)
                    {
                        SpecimenComment comment = SampleManager.getInstance().getSpecimenCommentForVial(specimen);
                        if (comment != null)
                        {
                            _successUrl.addParameter(ParticipantCommentForm.params.vialCommentsToClear, specimen.getRowId());
                        }
                    }
                }
                return true;
            }

            for (Specimen vial : vials)
            {
                SpecimenComment previousComment = currentComments.get(vial);

                boolean newConflictState;
                boolean newForceState;
                if (commentsForm.isQualityControlFlag() != null && SampleManager.getInstance().getDisplaySettings(container).isEnableManualQCFlagging())
                {
                    // if a state has been specified in the post, we consider this to be 'forcing' the state:
                    newConflictState = commentsForm.isQualityControlFlag().booleanValue();
                    newForceState = true;
                }
                else
                {
                    // if we aren't forcing the state, just re-save whatever was previously saved:
                    newConflictState = previousComment != null && previousComment.isQualityControlFlag();
                    newForceState = previousComment != null && previousComment.isQualityControlFlagForced();
                }
                
                if (previousComment == null || commentsForm.getConflictResolveEnum() == CommentsConflictResolution.REPLACE)
                {
                    SampleManager.getInstance().setSpecimenComment(getUser(), vial, commentsForm.getComments(),
                            newConflictState, newForceState);
                }
                else if (commentsForm.getConflictResolveEnum() == CommentsConflictResolution.APPEND)
                {
                    SampleManager.getInstance().setSpecimenComment(getUser(), vial, previousComment.getComment() + "\n" + commentsForm.getComments(),
                            newConflictState, newForceState);
                }
                // If we haven't updated by now, our user has selected CommentsConflictResolution.SKIP and previousComments is non-null
                // so we no-op for this vial.
            }
            _successUrl = new ActionURL(commentsForm.getReferrer());
            return true;
        }

        public ActionURL getSuccessURL(UpdateParticipantCommentsForm commentsForm)
        {
            return _successUrl;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root = appendBaseSpecimenNavTrail(root);
            return root.addChild("Set vial comments");
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class ImportSpecimenData extends SimpleViewAction<PipelineForm>
    {
        private String[] _filePaths = null;
        public ModelAndView getView(PipelineForm form, BindException bindErrors) throws Exception
        {
            List<File> dataFiles = form.getValidatedFiles(getContainer());
            List<SpecimenArchive> archives = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            _filePaths = form.getFile();
            for (File dataFile : dataFiles)
            {
                if (null == dataFile || !dataFile.exists() || !dataFile.isFile())
                {
                    throw new NotFoundException();
                }

                if (!dataFile.canRead())
                    errors.add("Can't read data file: " + dataFile);

                SpecimenArchive archive = new SpecimenArchive(dataFile);
                archives.add(archive);
            }

            ImportSpecimensBean bean = new ImportSpecimensBean(getContainer(), archives, form.getPath(), form.getFile(), errors);
            boolean isEmpty = SampleManager.getInstance().isSpecimensEmpty(getContainer(), getUser());
            if (isEmpty)
            {
                bean.setNoSpecimens(true);
            }
            else if (getStudyThrowIfNull().getRepositorySettings().isSpecimenDataEditable())
            {
                bean.setDefaultMerge(true);         // Repository is editable; make Merge the default
                bean.setEditableSpecimens(true);
            }

            return new JspView<>("/org/labkey/study/view/samples/importSpecimens.jsp", bean);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            String msg;
            if (_filePaths.length == 1)
                msg = _filePaths[0];
            else
                msg = _filePaths.length + " specimen archives";
            root.addChild("Import Study Batch - " + msg);
            return root;
        }
    }



    @RequiresPermissionClass(AdminPermission.class)
    public class SubmitSpecimenBatchImport extends FormHandlerAction<PipelineForm>
    {
        public void validateCommand(PipelineForm target, Errors errors)
        {
        }

        public boolean handlePost(PipelineForm form, BindException errors) throws Exception
        {
            Container c = getContainer();
            PipeRoot root = PipelineService.get().findPipelineRoot(c);
            boolean first = true;
            for (File f : form.getValidatedFiles(c))
            {
                // Only possibly overwrite when the first archive is loaded:
                boolean merge = !first || form.isMerge();
                submitSpecimenBatch(c, getUser(), getViewContext().getActionURL(), f, root, merge);
                first = false;
            }
            return true;
        }


        public ActionURL getSuccessURL(PipelineForm pipelineForm)
        {
            return PageFlowUtil.urlProvider(PipelineStatusUrls.class).urlBegin(getContainer());
        }
    }

    /**
     * Legacy method hit via WGET/CURL to programmatically initiate a specimen import; no longer used by the UI,
     * but this method should be kept around until we receive verification that the URL is no longer being hit
     * programmatically.
     */
    @RequiresPermissionClass(AdminPermission.class)
    public class SubmitSpecimenImport extends FormHandlerAction<PipelineForm>
    {
        public void validateCommand(PipelineForm target, Errors errors)
        {
        }

        public boolean handlePost(PipelineForm form, BindException errors) throws Exception
        {
            Container c = getContainer();
            String path = form.getPath();
            File f = null;

            PipeRoot root = PipelineService.get().findPipelineRoot(c);
            if (path != null)
            {
                if (root != null)
                    f = root.resolvePath(path);
            }

            submitSpecimenBatch(c, getUser(), getViewContext().getActionURL(), f, root, form.isMerge());
            return true;
        }


        public ActionURL getSuccessURL(PipelineForm pipelineForm)
        {
            return PageFlowUtil.urlProvider(PipelineStatusUrls.class).urlBegin(getContainer());
        }
    }


    public static void submitSpecimenBatch(Container c, User user, ActionURL url, File f, PipeRoot root, boolean merge) throws IOException, SQLException
    {
        if (null == f || !f.exists() || !f.isFile())
            throw new NotFoundException();

        SpecimenBatch batch = new SpecimenBatch(new ViewBackgroundInfo(c, user, url), f, root, merge);
        batch.submit();
    }


    public static class ImportSpecimensBean
    {
        private String _path;
        private List<SpecimenArchive> _archives;
        private List<String> _errors;
        private Container _container;
        private String[] _files;
        private boolean noSpecimens = false;
        private boolean _defaultMerge = false;
        private boolean _isEditableSpecimens = false;

        public ImportSpecimensBean(Container container, List<SpecimenArchive> archives,
                                   String path, String[] files, List<String> errors)
        {
            _path = path;
            _files = files;
            _archives = archives;
            _errors = errors;
            _container = container;
        }

        public List<SpecimenArchive> getArchives()
        {
            return _archives;
        }

        public String getPath()
        {
            return _path;
        }

        public String[] getFiles()
        {
            return _files;
        }

        public List<String> getErrors()
        {
            return _errors;
        }

        public Container getContainer()
        {
            return _container;
        }

        public boolean isNoSpecimens()
        {
            return noSpecimens;
        }

        public void setNoSpecimens(boolean noSpecimens)
        {
            this.noSpecimens = noSpecimens;
        }

        public boolean isDefaultMerge()
        {
            return _defaultMerge;
        }

        public void setDefaultMerge(boolean defaultMerge)
        {
            _defaultMerge = defaultMerge;
        }

        public boolean isEditableSpecimens()
        {
            return _isEditableSpecimens;
        }

        public void setEditableSpecimens(boolean editableSpecimens)
        {
            _isEditableSpecimens = editableSpecimens;
        }
    }



    @RequiresPermissionClass(ReadPermission.class)
    public class DownloadAction extends SimpleViewAction<AttachmentForm>
    {
        public ModelAndView getView(AttachmentForm form, BindException errors) throws Exception
        {
            SampleRequestEvent event = new SampleRequestEvent();  // TODO: Need to verify that entityId represents a valid SampleRequestEvent
            event.setContainer(getContainer());
            event.setEntityId(form.getEntityId());

            AttachmentService.get().download(getViewContext().getResponse(), event, form.getName());
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class ShowManageRepositorySettingsAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new JspView<>("/org/labkey/study/view/samples/manageRepositorySettings.jsp", SampleManager.getInstance().getRepositorySettings(getContainer()));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("specimenAdminTutorial");
            _appendManageStudy(root);
            root.addChild("Manage Repository Settings");

            return root;
        }
    }

    @RequiresPermissionClass(ManageStudyPermission.class)
    public class ManageRepositorySettingsAction extends SimpleViewAction<ManageRepositorySettingsForm>
    {
        public ModelAndView getView(ManageRepositorySettingsForm form, BindException errors) throws Exception
        {
            RepositorySettings settings = SampleManager.getInstance().getRepositorySettings(getContainer());
            settings.setSimple(form.isSimple());
            settings.setEnableRequests(!form.isSimple() && form.isEnableRequests());
            settings.setSpecimenDataEditable(!form.isSimple() && form.isSpecimenDataEditable());
            SampleManager.getInstance().saveRepositorySettings(getContainer(), settings);

            return HttpView.redirect(new ActionURL(StudyController.ManageStudyAction.class, getContainer()));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    public static class ManageRepositorySettingsForm
    {
        private boolean _simple;
        private boolean _enableRequests;
        private boolean _specimenDataEditable;

        public boolean isSimple()
        {
            return _simple;
        }

        public void setSimple(boolean simple)
        {
            _simple = simple;
        }

        public boolean isEnableRequests()
        {
            return _enableRequests;
        }

        public void setEnableRequests(boolean enableRequests)
        {
            _enableRequests = enableRequests;
        }

        public boolean isSpecimenDataEditable()
        {
            return _specimenDataEditable;
        }

        public void setSpecimenDataEditable(boolean specimenDataEditable)
        {
            _specimenDataEditable = specimenDataEditable;
        }
    }

    @RequiresPermissionClass(ManageSpecimenActorsPermission.class)
    public class ManageActorOrderAction extends DisplayManagementSubpageAction<BaseStudyController.BulkEditForm>
    {
        public ManageActorOrderAction()
        {
            super("manageActorOrder", "Manage Actor Order", "specimenRequest");
        }

        public boolean handlePost(BulkEditForm form, BindException errors) throws Exception
        {
            getUtils().ensureSpecimenRequestsConfigured(false);

            String order = form.getOrder();
            if (order != null && order.length() > 0)
            {
                String[] rowIds = order.split(",");
                // get a map of id to actor objects before starting our updates; this prevents us from
                // blowing then repopulating the cache with each update:
                Map<Integer, SampleRequestActor> idToActor = getIdToRequestActorMap(getContainer());
                for (int i = 0; i < rowIds.length; i++)
                {
                    int rowId = Integer.parseInt(rowIds[i]);
                    SampleRequestActor actor = idToActor.get(rowId);
                    if (actor != null && actor.getSortOrder() != i)
                    {
                        actor = actor.createMutable();
                        actor.setSortOrder(i);
                        actor.update(getUser());
                    }
                }
            }
            return true;
        }

        public ActionURL getSuccessURL(BulkEditForm bulkEditForm)
        {
            return new ActionURL(ManageActorsAction.class, getContainer());
        }
    }

    private abstract class DisplayManagementSubpageAction<Form extends BulkEditForm> extends FormViewAction<Form>
    {
        private String _jsp;
        private String _title;
        private String _helpTopic;

        public DisplayManagementSubpageAction(String jsp, String title, String helpTopic)
        {
            _jsp = jsp;
            _title = title;
            _helpTopic = helpTopic;
        }

        public void validateCommand(Form target, Errors errors)
        {
        }

        public ModelAndView getView(Form form, boolean reshow, BindException errors) throws Exception
        {
            getUtils().ensureSpecimenRequestsConfigured(false);
            return new JspView<>("/org/labkey/study/view/samples/" + _jsp + ".jsp", getStudyRedirectIfNull());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic(new HelpTopic(_helpTopic));

            _appendManageStudy(root);
            root.addChild(_title);

            return root;
        }
    }

    private Map<Integer, SampleRequestActor> getIdToRequestActorMap(Container container) throws SQLException
    {
        SampleRequestActor[] actors = SampleManager.getInstance().getRequirementsProvider().getActors(container);
        Map<Integer, SampleRequestActor> idToStatus = new HashMap<>();
        for (SampleRequestActor actor : actors)
            idToStatus.put(actor.getRowId(), actor);
        return idToStatus;
    }

    @RequiresPermissionClass(ManageSpecimenActorsPermission.class)
    public class ManageActorsAction extends DisplayManagementSubpageAction<ActorEditForm>
    {
        public ManageActorsAction()
        {
            super("manageActors", "Manage Specimen Request Actors", "coordinateSpecimens#actor");
        }

        public boolean handlePost(ActorEditForm form, BindException errors) throws Exception
        {
            getUtils().ensureSpecimenRequestsConfigured(false);

            int[] rowIds = form.getIds();
            String[] labels = form.getLabels();
            if (labels != null)
            {
                for (String label : labels)
                {
                    if (label == null || label.length() == 0)
                        errors.reject(ERROR_MSG, "Actor name cannot be empty.");
                }
            }
            if (!errors.hasErrors())
            {
                if (rowIds != null && rowIds.length > 0)
                {
                    // get a map of id to actor objects before starting our updates; this prevents us from
                    // blowing then repopulating the cache with each update:
                    Map<Integer, SampleRequestActor> idToActor = getIdToRequestActorMap(getContainer());
                    for (int i = 0; i < rowIds.length; i++)
                    {
                        int rowId = rowIds[i];
                        String label = labels[i];
                        SampleRequestActor actor = idToActor.get(rowId);
                        if (actor != null && !nullSafeEqual(label, actor.getLabel()))
                        {
                            actor = actor.createMutable();
                            actor.setLabel(label);
                            actor.update(getUser());
                        }
                    }
                }

                if (form.getNewLabel() != null && form.getNewLabel().length() > 0)
                {
                    SampleRequestActor actor = new SampleRequestActor();
                    actor.setLabel(form.getNewLabel());
                    SampleRequestActor[] actors = SampleManager.getInstance().getRequirementsProvider().getActors(getContainer());
                    actor.setSortOrder(actors.length);
                    actor.setContainer(getContainer());
                    actor.setPerSite(form.isNewPerSite());
                    actor.create(getUser());
                }
            }
            return !errors.hasErrors();
        }

        public ActionURL getSuccessURL(ActorEditForm form)
        {
            if (form.getNextPage() != null && form.getNextPage().length() > 0)
                return new ActionURL(form.getNextPage());
            else
                return new ActionURL(StudyController.ManageStudyAction.class, getContainer());
        }
    }

    public static class ActorEditForm extends BulkEditForm
    {
        boolean _newPerSite;

        public boolean isNewPerSite()
        {
            return _newPerSite;
        }

        public void setNewPerSite(boolean newPerSite)
        {
            _newPerSite = newPerSite;
        }
    }

    @RequiresPermissionClass(ManageSpecimenActorsPermission.class)
    public class ManageStatusOrderAction extends DisplayManagementSubpageAction<BulkEditForm>
    {
        public ManageStatusOrderAction()
        {
            super("manageStatusOrder", "Manage Status Order", "specimenRequest");
        }

        public boolean handlePost(BulkEditForm form, BindException errors) throws Exception
        {
            getUtils().ensureSpecimenRequestsConfigured(false);

            String order = form.getOrder();
            if (order != null && order.length() > 0)
            {
                String[] rowIdStrings = order.split(",");
                int[] rowIds = new int[rowIdStrings.length];
                for (int i = 0; i < rowIdStrings.length; i++)
                    rowIds[i] = Integer.parseInt(rowIdStrings[i]);
                updateRequestStatusOrder(getContainer(), rowIds, false);
            }
            return true;
        }

        public ActionURL getSuccessURL(BulkEditForm bulkEditForm)
        {
            return new ActionURL(ManageStatusesAction.class, getContainer());
        }
    }

    private void updateRequestStatusOrder(Container container, int[] rowIds, boolean fixedRowIncluded) throws SQLException
    {
        // get a map of id to status objects before starting our updates; this prevents us from
        // blowing then repopulating the cache with each update:
        Map<Integer, SampleRequestStatus> idToStatus = getIdToRequestStatusMap(container);
        for (int i = 0; i < rowIds.length; i++)
        {
            int rowId = rowIds[i];
            int statusOrder = fixedRowIncluded ? i : i + 1;     // One caller doesn't have the first (fixed) status
            SampleRequestStatus status = idToStatus.get(rowId);
            if (status != null && !status.isSystemStatus() && status.getSortOrder() != statusOrder)
            {
                status = status.createMutable();
                status.setSortOrder(statusOrder);
                SampleManager.getInstance().updateRequestStatus(getUser(), status);
            }
        }
    }

    @RequiresPermissionClass(ManageRequestStatusesPermission.class)
    public class ManageStatusesAction extends DisplayManagementSubpageAction<StatusEditForm>
    {
        public ManageStatusesAction()
        {
            super("manageStatuses", "Manage Specimen Request Statuses", "specimenRequest#status");
        }

        public boolean handlePost(StatusEditForm form, BindException errors) throws Exception
        {
            getUtils().ensureSpecimenRequestsConfigured(false);

            int[] rowIds = form.getIds();
            String[] labels = form.getLabels();
            if (rowIds != null && rowIds.length > 0)
            {
                // get a map of id to status objects before starting our updates; this prevents us from
                // blowing then repopulating the cache with each update:
                Map<Integer, SampleRequestStatus> idToStatus = getIdToRequestStatusMap(getContainer());
                Set<Integer> finalStates = new HashSet<>(form.getFinalStateIds().length);
                for (int id : form.getFinalStateIds())
                    finalStates.add(id);
                Set<Integer> lockedSpecimenStates = new HashSet<>(form.getSpecimensLockedIds().length);
                for (int id : form.getSpecimensLockedIds())
                    lockedSpecimenStates.add(id);

                for (int i = 0; i < rowIds.length; i++)
                {
                    int rowId = rowIds[i];
                    SampleRequestStatus status = idToStatus.get(rowId);
                    if (status != null && !status.isSystemStatus())
                    {
                        String label = labels[i];
                        boolean isFinalState = finalStates.contains(rowId);
                        boolean specimensLocked = lockedSpecimenStates.contains(rowId);
                        if (!nullSafeEqual(label, status.getLabel()) ||
                                isFinalState != status.isFinalState() ||
                                specimensLocked != status.isSpecimensLocked())
                        {
                            status = status.createMutable();
                            status.setLabel(label);
                            status.setFinalState(isFinalState);
                            status.setSpecimensLocked(specimensLocked);
                            SampleManager.getInstance().updateRequestStatus(getUser(), status);
                        }
                    }
                }
            }

            if (form.getNewLabel() != null && form.getNewLabel().length() > 0)
            {
                SampleRequestStatus status = new SampleRequestStatus();
                status.setLabel(form.getNewLabel());
                List<SampleRequestStatus> statuses = SampleManager.getInstance().getRequestStatuses(getContainer(), getUser());
                status.setSortOrder(statuses.size());
                status.setContainer(getContainer());
                status.setFinalState(form.isNewFinalState());
                status.setSpecimensLocked(form.isNewSpecimensLocked());
                SampleManager.getInstance().createRequestStatus(getUser(), status);
            }

            StatusSettings settings = SampleManager.getInstance().getStatusSettings(getContainer());
            if (settings.isUseShoppingCart() != form.isUseShoppingCart())
            {
                settings.setUseShoppingCart(form.isUseShoppingCart());
                SampleManager.getInstance().saveStatusSettings(getContainer(), settings);
            }
            return true;
        }

        public ActionURL getSuccessURL(StatusEditForm form)
        {
            if (form.getNextPage() != null && form.getNextPage().length() > 0)
                return new ActionURL(form.getNextPage());
            else
                return new ActionURL(StudyController.ManageStudyAction.class, getContainer());
        }
    }

    public static class StatusEditForm extends BulkEditForm
    {
        private int[] _finalStateIds = new int[0];
        private int[] _specimensLockedIds = new int[0];
        private boolean _newSpecimensLocked;
        private boolean _newFinalState;
        private boolean _useShoppingCart;

        public int[] getFinalStateIds()
        {
            return _finalStateIds;
        }

        public void setFinalStateIds(int[] finalStateIds)
        {
            _finalStateIds = finalStateIds;
        }

        public boolean isNewFinalState()
        {
            return _newFinalState;
        }

        public void setNewFinalState(boolean newFinalState)
        {
            _newFinalState = newFinalState;
        }

        public boolean isNewSpecimensLocked()
        {
            return _newSpecimensLocked;
        }

        public void setNewSpecimensLocked(boolean newSpecimensLocked)
        {
            _newSpecimensLocked = newSpecimensLocked;
        }

        public int[] getSpecimensLockedIds()
        {
            return _specimensLockedIds;
        }

        public void setSpecimensLockedIds(int[] specimensLockedIds)
        {
            _specimensLockedIds = specimensLockedIds;
        }

        public boolean isUseShoppingCart()
        {
            return _useShoppingCart;
        }

        public void setUseShoppingCart(boolean useShoppingCart)
        {
            _useShoppingCart = useShoppingCart;
        }
    }

    private Map<Integer, SampleRequestStatus> getIdToRequestStatusMap(Container container) throws SQLException
    {
        List<SampleRequestStatus> statuses = SampleManager.getInstance().getRequestStatuses(container, getUser());
        Map<Integer, SampleRequestStatus> idToStatus = new HashMap<>();
        for (SampleRequestStatus status : statuses)
            idToStatus.put(status.getRowId(), status);
        return idToStatus;
    }

    @RequiresPermissionClass(ManageRequestStatusesPermission.class)
    public class DeleteActorAction extends RedirectAction<IdForm>
    {
        public ActionURL getSuccessURL(IdForm idForm)
        {
            return new ActionURL(ManageActorsAction.class, getContainer());
        }

        public boolean doAction(IdForm form, BindException errors) throws Exception
        {
            SampleRequestActor actor = SampleManager.getInstance().getRequirementsProvider().getActor(getContainer(), form.getId());
            if (actor != null)
                actor.delete();

            return true;
        }

        public void validateCommand(IdForm target, Errors errors)
        {
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class DeleteStatusAction extends RedirectAction<IdForm>
    {
        public ActionURL getSuccessURL(IdForm idForm)
        {
            return new ActionURL(ManageStatusesAction.class, getContainer());
        }

        public boolean doAction(IdForm form, BindException errors) throws Exception
        {
            List<SampleRequestStatus> statuses = SampleManager.getInstance().getRequestStatuses(getContainer(), getUser());
            SampleRequestStatus status = SampleManager.getInstance().getRequestStatus(getContainer(), form.getId());
            if (status != null)
            {
                SampleManager.getInstance().deleteRequestStatus(getUser(), status);
                int[] remainingIds = new int[statuses.size() - 1];
                int idx = 0;
                for (SampleRequestStatus remainingStatus : statuses)
                {
                    if (remainingStatus.getRowId() != form.getId())
                        remainingIds[idx++] = remainingStatus.getRowId();
                }
                updateRequestStatusOrder(getContainer(), remainingIds, true);
            }
            return true;
        }

        public void validateCommand(IdForm target, Errors errors)
        {
        }
    }

    @RequiresPermissionClass(ManageNewRequestFormPermission.class)
    public class HandleUpdateRequestInputsAction extends RedirectAction<ManageRequestInputsForm>
    {
        public ActionURL getSuccessURL(ManageRequestInputsForm manageRequestInputsForm)
        {
            return new ActionURL(StudyController.ManageStudyAction.class, getContainer());
        }

        public boolean doAction(ManageRequestInputsForm form, BindException errors) throws Exception
        {
            SampleManager.SpecimenRequestInput[] inputs = new SampleManager.SpecimenRequestInput[form.getTitle().length];
            for (int i = 0; i < form.getTitle().length; i++)
            {
                String title = form.getTitle()[i];
                String helpText = form.getHelpText()[i];
                inputs[i] = new SampleManager.SpecimenRequestInput(title, helpText, i);
            }

            if (form.getMultiline() != null)
            {
                for (int index : form.getMultiline())
                    inputs[index].setMultiLine(true);
            }
            if (form.getRequired() != null)
            {
                for (int index : form.getRequired())
                    inputs[index].setRequired(true);
            }
            if (form.getRememberSiteValue() != null)
            {
                for (int index : form.getRememberSiteValue())
                    inputs[index].setRememberSiteValue(true);
            }
            SampleManager.getInstance().saveNewSpecimenRequestInputs(getContainer(), inputs);
            return true;
        }

        public void validateCommand(ManageRequestInputsForm target, Errors errors)
        {
        }
    }

    public static final class ManageRequestInputsForm
    {
        private String[] _title;
        private String[] _helpText;
        private int[] _multiline;
        private int[] _required;
        private int[] _rememberSiteValue;

        public String[] getHelpText()
        {
            return _helpText;
        }

        public void setHelpText(String[] helpText)
        {
            _helpText = helpText;
        }

        public String[] getTitle()
        {
            return _title;
        }

        public void setTitle(String[] title)
        {
            _title = title;
        }

        public int[] getMultiline()
        {
            return _multiline;
        }

        public void setMultiline(int[] multiline)
        {
            _multiline = multiline;
        }

        public int[] getRememberSiteValue()
        {
            return _rememberSiteValue;
        }

        public void setRememberSiteValue(int[] rememberSiteValue)
        {
            _rememberSiteValue = rememberSiteValue;
        }

        public int[] getRequired()
        {
            return _required;
        }

        public void setRequired(int[] required)
        {
            _required = required;
        }
    }

    @RequiresPermissionClass(ManageNewRequestFormPermission.class)
    public class ManageRequestInputsAction extends SimpleViewAction<PipelineForm>
    {
        public ModelAndView getView(PipelineForm pipelineForm, BindException errors) throws Exception
        {
            getUtils().ensureSpecimenRequestsConfigured(false);
            return new JspView<>("/org/labkey/study/view/samples/manageRequestInputs.jsp",
                    new ManageRequestInputsBean(getViewContext()));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("coordinateSpecimens#form");
            _appendManageStudy(root);
            root.addChild("Manage New Request Form");

            return root;
        }
    }

    public static class PipelineForm extends PipelinePathForm
    {
        private String replaceOrMerge = "replace";

        public String getReplaceOrMerge()
        {
            return replaceOrMerge;
        }

        public void setReplaceOrMerge(String replaceOrMerge)
        {
            this.replaceOrMerge = replaceOrMerge;
        }

        public boolean isMerge()
        {
            return "merge".equals(this.replaceOrMerge);
        }
    }

    public static class ManageRequestInputsBean
    {
        private SampleManager.SpecimenRequestInput[] _inputs;
        private Container _container;
        private String _contextPath;

        public ManageRequestInputsBean(ViewContext context) throws SQLException
        {
            _container = context.getContainer();
            _inputs = SampleManager.getInstance().getNewSpecimenRequestInputs(_container);
            _contextPath = context.getContextPath();
        }

        public SampleManager.SpecimenRequestInput[] getInputs()
        {
            return _inputs;
        }

        public Container getContainer()
        {
            return _container;
        }

        public String getContextPath()
        {
            return _contextPath;
        }
    }

    @RequiresPermissionClass(ManageNotificationsPermission.class)
    public class ManageNotificationsAction extends FormViewAction<RequestNotificationSettings>
    {
        public void validateCommand(RequestNotificationSettings form, Errors errors)
        {
            String replyTo = form.getReplyTo();
            if (replyTo == null || replyTo.length() == 0)
            {
                errors.reject(SpringActionController.ERROR_MSG, "Reply-to cannot be empty.");
            }
            else if (!RequestNotificationSettings.REPLY_TO_CURRENT_USER_VALUE.equals(replyTo))
            {
                try
                {
                    new ValidEmail(replyTo);
                }
                catch(ValidEmail.InvalidEmailException e)
                {
                    errors.reject(SpringActionController.ERROR_MSG, replyTo + " is not a valid email address.");
                }
            }

            String subjectSuffix = form.getSubjectSuffix();
            if (subjectSuffix == null || subjectSuffix.length() == 0)
            {
                errors.reject(SpringActionController.ERROR_MSG, "Subject suffix cannot be empty.");
            }

            try
            {
                form.getNewRequestNotifyAddresses();
            }
            catch (ValidEmail.InvalidEmailException e)
            {
                errors.reject(SpringActionController.ERROR_MSG, e.getBadEmail() + " is not a valid email address.");
            }

            try
            {
                form.getCCAddresses();
            }
            catch (ValidEmail.InvalidEmailException e)
            {
                errors.reject(SpringActionController.ERROR_MSG, e.getBadEmail() + " is not a valid email address.");
            }
        }

        public ModelAndView getView(RequestNotificationSettings form, boolean reshow, BindException errors)
        {
            if (!SampleManager.getInstance().isSampleRequestEnabled(getContainer(), false))
                throw new RedirectException(new ActionURL(SpecimenController.SpecimenRequestConfigRequired.class, getContainer()));

            // try to get the settings from the form, just in case this is a reshow:
            RequestNotificationSettings settings = form;
            if (settings == null || settings.getReplyTo() == null)
                settings = SampleManager.getInstance().getRequestNotificationSettings(getContainer());

            return new JspView<>("/org/labkey/study/view/samples/manageNotifications.jsp", settings, errors);
        }

        public boolean handlePost(RequestNotificationSettings settings, BindException errors) throws Exception
        {
            getUtils().ensureSpecimenRequestsConfigured(false);

            if (!settings.isNewRequestNotifyCheckbox())
                settings.setNewRequestNotify(null);
            else
            {
                if (isNullOrBlank(settings.getNewRequestNotify()))
                    errors.reject(ERROR_MSG, "New request notify is blank and send email is checked");
            }
            if (!settings.isCcCheckbox())
                settings.setCc(null);
            else
            {
                if (isNullOrBlank(settings.getCc()))
                    errors.reject(ERROR_MSG, "Always CC is blank and send email is checked");
            }
            if (errors.hasErrors())
                return false;

            SampleManager.getInstance().saveRequestNotificationSettings(getContainer(), settings);
            return true;
        }

        public ActionURL getSuccessURL(RequestNotificationSettings form)
        {
            return new ActionURL(StudyController.ManageStudyAction.class, getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("coordinateSpecimens#notify");
            _appendManageStudy(root);
            root.addChild("Manage Notifications");

            return root;
        }
    }
    
    private boolean isNullOrBlank(String toCheck)
    {
        return ((toCheck == null) || toCheck.equals(""));
    }

    @RequiresPermissionClass(ManageDisplaySettingsPermission.class)
    public class ManageDisplaySettingsAction extends FormViewAction<DisplaySettingsForm>
    {
        public void validateCommand(DisplaySettingsForm target, Errors errors)
        {
        }

        public ModelAndView getView(DisplaySettingsForm form, boolean reshow, BindException errors)
        {
            // try to get the settings from the form, just in case this is a reshow:
            DisplaySettings settings = form.getBean();
            if (settings == null || settings.getLastVialEnum() == null)
                settings = SampleManager.getInstance().getDisplaySettings(getContainer());

            return new JspView<>("/org/labkey/study/view/samples/manageDisplay.jsp", settings);
        }

        public boolean handlePost(DisplaySettingsForm form, BindException errors) throws Exception
        {
            DisplaySettings settings = form.getBean();
            SampleManager.getInstance().saveDisplaySettings(getContainer(), settings);

            return true;
        }

        public ActionURL getSuccessURL(DisplaySettingsForm displaySettingsForm)
        {
            return new ActionURL(StudyController.ManageStudyAction.class, getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic(new HelpTopic("specimenRequest#display"));
            _appendManageStudy(root);
            root.addChild("Manage Specimen Display Settings");

            return root;
        }
    }

    public static class DisplaySettingsForm extends BeanViewForm<DisplaySettings>
    {
        public DisplaySettingsForm()
        {
            super(DisplaySettings.class);
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class GetSpecimenExcelAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            List<Map<String,Object>> defaultSpecimens = new ArrayList<>();
            SimpleSpecimenImporter importer = new SimpleSpecimenImporter(getContainer(), getUser(),
                    getStudyRedirectIfNull().getTimepointType(),  StudyService.get().getSubjectNounSingular(getContainer()));
            MapArrayExcelWriter xlWriter = new MapArrayExcelWriter(defaultSpecimens, importer.getSimpleSpecimenColumns());
            for (ExcelColumn col : xlWriter.getColumns())
                col.setCaption(importer.label(col.getName()));

            xlWriter.write(getViewContext().getResponse());

            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    public static class IdForm extends ReturnUrlForm
    {
        public enum PARAMS
        {
            id
        }

        private int _id;

        public IdForm()
        {
        }

        public IdForm(int id)
        {
            _id = id;
        }

        public int getId()
        {
            return _id;
        }

        public void setId(int id)
        {
            _id = id;
        }
    }

    public static class ManageCommentsForm
    {
        private Integer participantCommentDataSetId;
        private String participantCommentProperty;
        private Integer participantVisitCommentDataSetId;
        private String participantVisitCommentProperty;
        private boolean _reshow;

        public Integer getParticipantCommentDataSetId()
        {
            return participantCommentDataSetId;
        }

        public void setParticipantCommentDataSetId(Integer participantCommentDataSetId)
        {
            this.participantCommentDataSetId = participantCommentDataSetId;
        }

        public String getParticipantCommentProperty()
        {
            return participantCommentProperty;
        }

        public void setParticipantCommentProperty(String participantCommentProperty)
        {
            this.participantCommentProperty = participantCommentProperty;
        }

        public Integer getParticipantVisitCommentDataSetId()
        {
            return participantVisitCommentDataSetId;
        }

        public void setParticipantVisitCommentDataSetId(Integer participantVisitCommentDataSetId)
        {
            this.participantVisitCommentDataSetId = participantVisitCommentDataSetId;
        }

        public String getParticipantVisitCommentProperty()
        {
            return participantVisitCommentProperty;
        }

        public void setParticipantVisitCommentProperty(String participantVisitCommentProperty)
        {
            this.participantVisitCommentProperty = participantVisitCommentProperty;
        }

        public boolean isReshow()
        {
            return _reshow;
        }

        public void setReshow(boolean reshow)
        {
            _reshow = reshow;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class ManageSpecimenCommentsAction extends FormViewAction<ManageCommentsForm>
    {
        public void validateCommand(ManageCommentsForm form, Errors errors)
        {
            String subjectNoun = StudyService.get().getSubjectNounSingular(getContainer());
            final Study study = BaseStudyController.getStudyRedirectIfNull(getContainer());
            if (form.getParticipantCommentDataSetId() != null && form.getParticipantCommentDataSetId() != -1)
            {
                DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, form.getParticipantCommentDataSetId());
                if (def != null && !def.isDemographicData())
                {
                    errors.reject(ERROR_MSG, "The Dataset specified to contain " + subjectNoun + " comments must be a demographics dataset.");
                }

                if (form.getParticipantCommentProperty() == null)
                    errors.reject(ERROR_MSG, "A Comment field name must be specified for the " + subjectNoun + " Comment Assignment.");
            }

            if (study.getTimepointType() != TimepointType.CONTINUOUS)
            {
                if (form.getParticipantVisitCommentDataSetId() != null && form.getParticipantVisitCommentDataSetId() != -1)
                {
                    DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, form.getParticipantVisitCommentDataSetId());
                    if (def != null && def.isDemographicData())
                    {
                        errors.reject(ERROR_MSG, "The Dataset specified to contain " + subjectNoun + "/Visit comments cannot be a demographics dataset.");
                    }

                    if (form.getParticipantVisitCommentProperty() == null)
                        errors.reject(ERROR_MSG, "A Comment field name must be specified for the " + subjectNoun + "/Visit Comment Assignment.");
                }
            }
        }

        public ModelAndView getView(ManageCommentsForm form, boolean reshow, BindException errors) throws Exception
        {
            StudyImpl study = getStudyRedirectIfNull();
            SecurityType securityType = study.getSecurityType();

            if (securityType == SecurityType.ADVANCED_READ || securityType == SecurityType.BASIC_READ)
                return new HtmlView("Comments can only be configured for studies with editable datasets.");

            if (!form.isReshow())
            {
                form.setParticipantCommentDataSetId(study.getParticipantCommentDataSetId());
                form.setParticipantCommentProperty(study.getParticipantCommentProperty());

                if (study.getTimepointType() != TimepointType.CONTINUOUS)
                {
                    form.setParticipantVisitCommentDataSetId(study.getParticipantVisitCommentDataSetId());
                    form.setParticipantVisitCommentProperty(study.getParticipantVisitCommentProperty());
                }
            }
            StudyJspView<Object> view = new StudyJspView<Object>(study, "manageComments.jsp", form, errors);
            view.setTitle("Comment Configuration");

            return view;
        }

        public boolean handlePost(ManageCommentsForm form, BindException errors) throws Exception
        {
            if (null == getStudy())
                throw new IllegalStateException("No study found.");
            StudyImpl study = getStudy().createMutable();

            // participant comment dataset
            study.setParticipantCommentDataSetId(form.getParticipantCommentDataSetId());
            study.setParticipantCommentProperty(form.getParticipantCommentProperty());

            // participant/visit comment dataset
            if (study.getTimepointType() != TimepointType.CONTINUOUS)
            {
                study.setParticipantVisitCommentDataSetId(form.getParticipantVisitCommentDataSetId());
                study.setParticipantVisitCommentProperty(form.getParticipantVisitCommentProperty());
            }

            StudyManager.getInstance().updateStudy(getUser(), study);
            return true;
        }

        public ActionURL getSuccessURL(ManageCommentsForm form)
        {
            return new ActionURL(StudyController.ManageStudyAction.class, getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("manageComments");
            _appendManageStudy(root);
            return root.addChild("Manage Comments");
        }
    }

    public static class ParticipantCommentForm extends DatasetController.EditDatasetRowForm
    {
        private String _participantId;
        private int _visitId;
        private String _comment;
        private String _oldComment;
        private int[] _vialCommentsToClear = new int[0];

        enum params {
            participantId,
            visitId,
            comment,
            oldComment,
            vialCommentsToClear,
            lsid,
            datasetId,
            returnUrl,
        }

        public String getParticipantId()
        {
            return _participantId;
        }

        public void setParticipantId(String participantId)
        {
            _participantId = participantId;
        }

        public int getVisitId()
        {
            return _visitId;
        }

        public void setVisitId(int visitId)
        {
            _visitId = visitId;
        }

        public String getComment()
        {
            return _comment;
        }

        public void setComment(String comment)
        {
            _comment = comment;
        }

        public String getOldComment()
        {
            return _oldComment;
        }

        public void setOldComment(String oldComment)
        {
            _oldComment = oldComment;
        }

        public int[] getVialCommentsToClear()
        {
            return _vialCommentsToClear;
        }

        public void setVialCommentsToClear(int[] vialsCommentsToClear)
        {
            _vialCommentsToClear = vialsCommentsToClear;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class CopyParticipantCommentAction extends SimpleViewAction<ParticipantCommentForm>
    {
        public ModelAndView getView(final ParticipantCommentForm form, BindException errors) throws Exception
        {
            StudyImpl study = getStudyRedirectIfNull();
            DataSetDefinition def;

            if (form.getVisitId() != 0)
            {
                def = StudyManager.getInstance().getDataSetDefinition(study, study.getParticipantVisitCommentDataSetId());
            }
            else
            {
                def = StudyManager.getInstance().getDataSetDefinition(study, study.getParticipantCommentDataSetId());
            }

            if (def != null)
            {
                StudyQuerySchema querySchema = StudyQuerySchema.createSchema(study, getUser(), true);
                DataSetQuerySettings qs = (DataSetQuerySettings)querySchema.getSettings(getViewContext(), DataSetQueryView.DATAREGION, def.getName());
                qs.setUseQCSet(false);

                DataSetQueryView queryView = new DataSetQueryView(querySchema, qs, errors)
                {
                    @Override
                    public DataView createDataView()
                    {
                        DataView view = super.createDataView();

                        SimpleFilter filter = (SimpleFilter) view.getRenderContext().getBaseFilter();
                        if (null == filter)
                        {
                            filter = new SimpleFilter();
                            view.getRenderContext().setBaseFilter(filter);
                        }
                        filter.addCondition(StudyService.get().getSubjectColumnName(view.getViewContext().getContainer()), form.getParticipantId());
                        if (form.getVisitId() != 0)
                            filter.addCondition(FieldKey.fromParts("sequenceNum"), form.getVisitId());

                        return view;
                    }
                };

                String lsid = getExistingComment(queryView);
                ActionURL url;
                if (lsid != null)
                    url = new ActionURL(ParticipantCommentAction.SpecimenCommentUpdateAction.class, getContainer()).
                            addParameter(ParticipantCommentForm.params.lsid, lsid);
                else
                    url = new ActionURL(ParticipantCommentAction.SpecimenCommentInsertAction.class, getContainer()).
                            addParameter(ParticipantCommentForm.params.participantId, form.getParticipantId());

                url.addParameter(ParticipantCommentForm.params.datasetId, def.getDataSetId()).
                        addParameter(ParticipantCommentForm.params.comment, form.getComment()).
                        addReturnURL(form.getReturnActionURL()).
                        addParameter(ParticipantCommentForm.params.visitId, form.getVisitId());

                for (int rowId : form.getVialCommentsToClear())
                    url.addParameter(ParticipantCommentForm.params.vialCommentsToClear, rowId);
                return HttpView.redirect(url);
            }
            return new HtmlView("Dataset could not be found");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        private String getExistingComment(QueryView queryView)
        {
            final TableInfo table = queryView.getTable();
            if (table != null)
            {
                ResultSet rs = null;
                try {
                    rs = queryView.getResultSet();
                    while (rs.next())
                    {
                        String lsid = rs.getString("lsid");
                        if (lsid != null)
                            return lsid;
                    }
                }
                catch (Exception e)
                {
                    _log.error("Error encountered trying to get " + StudyService.get().getSubjectNounSingular(getContainer()) + " comments", e);
                }
                finally
                {
                    if (null != rs)
                        try { rs.close(); } catch (SQLException e){}
                }
            }
            return null;
        }
    }

    public static class UpdateRequestabilityRulesForm implements HasViewContext
    {
        private ViewContext _viewContext;
        private String[] _ruleType;
        private String[] _ruleData;
        private String[] _markType;

        public ViewContext getViewContext()
        {
            return _viewContext;
        }

        public void setViewContext(ViewContext viewContext)
        {
            _viewContext = viewContext;
        }

        public String[] getRuleType()
        {
            return _ruleType;
        }

        public void setRuleType(String[] ruleType)
        {
            _ruleType = ruleType;
        }

        public String[] getRuleData()
        {
            return _ruleData;
        }

        public void setRuleData(String[] ruleData)
        {
            _ruleData = ruleData;
        }

        public String[] getMarkType()
        {
            return _markType;
        }

        public void setMarkType(String[] markType)
        {
            _markType = markType;
        }
    }

    @RequiresPermissionClass(ManageRequestSettingsPermission.class)
    public class UpdateRequestabilityRulesAction extends ApiAction<UpdateRequestabilityRulesForm>
    {
        @Override
        public ApiResponse execute(UpdateRequestabilityRulesForm form, BindException errors) throws Exception
        {
            final List<RequestabilityManager.RequestableRule> rules = new ArrayList<>();
            for (int i = 0; i < form.getRuleType().length; i++)
            {
                String typeName = form.getRuleType()[i];
                RequestabilityManager.RuleType type = RequestabilityManager.RuleType.valueOf(typeName);
                String dataString = form.getRuleData()[i];
                rules.add(type.createRule(getContainer(), dataString));
            }
            RequestabilityManager.getInstance().saveRules(getContainer(), getUser(), rules);

            return new ApiResponse()
            {
                public Map<String, ?> getProperties()
                {
                    return Collections.<String, Object>singletonMap("savedCount", rules.size());
                }
            };
        }
    }

    @RequiresPermissionClass(ManageRequestSettingsPermission.class)
    public class ConfigureRequestabilityRulesAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            getUtils().ensureSpecimenRequestsConfigured(false);
            return new JspView("/org/labkey/study/view/samples/configRequestabilityRules.jsp");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("coordinateSpecimens#requestability");
            _appendManageStudy(root);
            root.addChild("Configure Requestability Rules");
            return root;
        }
    }



    /** WEB PARTS **/

    public static class SpecimenReportWebPartFactory extends BaseWebPartFactory
    {
        public SpecimenReportWebPartFactory()
        {
            super("Specimen Report", LOCATION_BODY, false, false);
        }

        @Override
        public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
        {
            TypeSummaryReportFactory specimenVisitReportForm = new TypeSummaryReportFactory();

            JspView<TypeSummaryReportFactory> reportView = new JspView<>("/org/labkey/study/view/samples/specimenVisitReport.jsp", specimenVisitReportForm);
            WebPartView configView = new JspView<>("/org/labkey/study/view/samples/autoReportList.jsp", new ReportConfigurationBean(specimenVisitReportForm, false));
            HtmlView emptySpace = new HtmlView("<div id=\"specimenReportEmptySpace\">&nbsp;</div>");

            VBox outer = new VBox(configView, reportView);

            outer.setFrame(WebPartView.FrameType.PORTAL);
            outer.setTitle("Specimen Report");
            outer.setTitleHref(new ActionURL(TypeSummaryReportAction.class, portalCtx.getContainer()));
            return outer;
        }
    }

    @RequiresPermissionClass(RequestSpecimensPermission.class)
    public class ImportVialIdsAction extends AbstractQueryImportAction<IdForm>
    {
        private Study _study;
        private int _requestId = -1;

        public ImportVialIdsAction()
        {
            super(IdForm.class);
        }

        @Override
        protected void initRequest(IdForm form) throws ServletException
        {
            _requestId = form.getId();
            setHasColumnHeaders(false);
            setImportMessage("Upload a list of Global Unique Identifiers from a TXT, CSV or Excel file or paste the list directly into the text box below. " +
                    "The list must have only one column and no header row.");
            setNoTableInfo();
            setHideTsvCsvCombo(true);
        }

        public ModelAndView getView(IdForm form, BindException errors) throws Exception
        {
            initRequest(form);
            SampleRequest request = SampleManager.getInstance().getRequest(getContainer(), _requestId);
            if (request == null)
                throw new NotFoundException();

            if (!SampleManager.getInstance().hasEditRequestPermissions(getUser(), request) ||
                    SampleManager.getInstance().isInFinalState(request))
            {
                return new HtmlView("<div class=\"labkey-error\">You do not have permissions to modify this request.</div>");
            }

            _study = getStudyRedirectIfNull();
            return getDefaultImportView(form, errors);
        }

        @Override
        protected void validatePermission(User user, BindException errors)
        {
            checkPermissions();
            SampleRequest request = SampleManager.getInstance().getRequest(getContainer(), _requestId);

            if (!SampleManager.getInstance().hasEditRequestPermissions(getUser(), request) ||
                    SampleManager.getInstance().isInFinalState(request))
            {
                // No permission
                errors.reject(SpringActionController.ERROR_MSG, "You do not have permission to modify this request.");
            }
        }

        @Override
        protected int importData(DataLoader dl, FileStream file, String originalName, BatchValidationException errors) throws IOException
        {
            List<String> errorList = new LinkedList<>();

            ColumnDescriptor[] columns = new ColumnDescriptor[2];   // Only 1 actual column of type String
            columns[0] = new ColumnDescriptor("Actual", String.class);
            columns[1] = new ColumnDescriptor("Dummy", String.class);
            dl.setColumns(columns);

            List<Map<String, Object>> rows = dl.load();
            String[] globalIds = new String[rows.size()];
            int i = 0;
            for (Map<String, Object> row : rows)
            {
                String idActual = (String)row.get("Actual");
                if (idActual == null)
                {
                    errorList.add("Malformed input data.");
                    break;
                }

                String idDummy = (String)row.get("Dummy");
                if (idDummy != null)
                {
                    errorList.add("Only one id per line is allowed.");
                    break;
                }
                globalIds[i] = idActual;
                i += 1;
            }

            if (errorList.size() == 0)
            {
                // Get all the specimen objects. If it throws an exception then there was an error and we'll
                // root around to figure out what to report
                SampleManager sampleManager = SampleManager.getInstance();
                try
                {
                    SampleRequest request = sampleManager.getRequest(getContainer(), _requestId);
                    List<Specimen> specimens = sampleManager.getSpecimens(getContainer(), getUser(), globalIds);

                    if (specimens != null && specimens.size() == globalIds.length)
                    {
                        // All the specimens exist;
                        // Check for Availability and then add them to the request.
                        // There still may be errors (like someone already has requested that specimen) which will be
                        // caught by createRequestSampleMapping

                        for (Specimen s : specimens)
                        {
                            if (!s.isAvailable()) // || s.isLockedInRequest())
                            {
                                errorList.add(RequestabilityManager.makeSpecimenUnavailableMessage(s, null));
                            }
                        }

                        if (errorList.size() == 0)
                        {
                            ArrayList<Specimen> specimenList = new ArrayList<>(specimens.size());
                            specimenList.addAll(specimens);
                            sampleManager.createRequestSampleMapping(getUser(), request, specimenList, true, true);
                        }
                    }
                    else
                    {
                        errorList.add("Duplicate Ids found.");
                    }
                }
                catch (SQLException e)
                {
                    errorList.add(e.getMessage());
                }
                catch (RequestabilityManager.InvalidRuleException e)
                {
                    errorList.add("The request could not be created because a requestability rule is configured incorrectly. " +
                            "Please report this problem to an administrator.  Error details: " + e.getMessage());
                }
                catch (SampleManager.SpecimenRequestException e)
                {
                    // There was an error; some id had no specimen matching
                    boolean hasSpecimenError = false;
                    for (String id : globalIds)
                    {
                        Specimen specimen = sampleManager.getSpecimen(getContainer(), getUser(), id);
                        if (specimen == null)
                        {
                            errorList.add("Specimen " + id + " not found.");
                            hasSpecimenError = true;
                        }
                    }

                    if (!hasSpecimenError)
                    {   // Expected one of them to not be found, so this is unusual
                        errorList.add("Error adding all of the specimens together.");
                    }
                }
            }

            if (!errorList.isEmpty())
            {
                for (String error : errorList)
                    errors.addRowError(new ValidationException(error));
            }

            if (errors.hasErrors())
                return 0;
            return rows.size();
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root = appendSpecimenRequestNavTrail(root, _requestId);
            root.addChild("Upload Specimen Identifiers");
            return root;
        }

        @Override
        protected ActionURL getSuccessURL(IdForm form)
        {
            ActionURL requestDetailsURL = new ActionURL(ManageRequestAction.class, getContainer());
            requestDetailsURL.addParameter("id", _requestId);
            return requestDetailsURL;
        }

    }

    public static class CompleteSpecimenForm
    {
        private String _prefix;

        public String getPrefix()
        {
            return _prefix;
        }

        public void setPrefix(String prefix)
        {
            _prefix = prefix;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class CompleteSpecimenAction extends ApiAction<CompleteSpecimenForm>
    {
        @Override
        public ApiResponse execute(CompleteSpecimenForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            Study study = getStudy();
            if (study == null)
                throw new NotFoundException("No study exists in this folder.");

            List<JSONObject> completions = new ArrayList<>();
            for (AjaxCompletion completion : getAjaxCompletions(study))
                completions.add(completion.toJSON());

            response.put("completions", completions);
            return response;
        }
    }

    public static List<AjaxCompletion> getAjaxCompletions(Study study) throws SQLException
    {
        List<AjaxCompletion> completions = new ArrayList<>();
        String allString = "All " + PageFlowUtil.filter(StudyService.get().getSubjectNounPlural(study.getContainer())) +  " (Large Report)";

        completions.add(new AjaxCompletion(allString, allString));

        for (String ptid : StudyManager.getInstance().getParticipantIds(study))
            completions.add(new AjaxCompletion(ptid, ptid));

        return completions;
    }

    @RequiresPermissionClass(ManageDisplaySettingsPermission.class)
    public class ManageSpecimenWebPartAction extends SimpleViewAction<SpecimenWebPartForm>
    {
        public ModelAndView getView(SpecimenWebPartForm form, BindException errors)
        {
            RepositorySettings settings = SampleManager.getInstance().getRepositorySettings(getContainer());
            ArrayList<String[]> groupings = settings.getSpecimenWebPartGroupings();
            form.setGrouping1(groupings.get(0));
            form.setGrouping2(groupings.get(1));
            form.setColumns(SampleManager.getInstance().getGroupedValueAllowedColumns());
            return new JspView<>("/org/labkey/study/view/samples/manageSpecimenWebPart.jsp", form);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("manageSpecimens#group");
            _appendManageStudy(root);
            root.addChild("Configure Specimen Web Part");

            return root;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class SaveSpecimenWebPartSettingsAction extends ApiAction<SpecimenWebPartForm>
    {
        @Override
        public ApiResponse execute(SpecimenWebPartForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            Container container = getContainer();
            StudyImpl study = getStudy(container);
            if (study != null)
            {
                RepositorySettings settings = SampleManager.getInstance().getRepositorySettings(container);
                ArrayList<String[]> groupings = new ArrayList<>(2);
                groupings.add(form.getGrouping1());
                groupings.add(form.getGrouping2());
                settings.setSpecimenWebPartGroupings(groupings);
                SampleManager.getInstance().saveRepositorySettings(container, settings);
                response.put("success", true);
                return response;
            }
            else
                throw new IllegalStateException("A study does not exist in this folder");
        }
    }

    public static class SpecimenWebPartForm
    {
        private String[] _grouping1;
        private String[] _grouping2;
        private String[] _columns;

        public String[] getGrouping1()
        {
            return _grouping1;
        }

        public void setGrouping1(String[] grouping1)
        {
            _grouping1 = grouping1;
        }

        public String[] getGrouping2()
        {
            return _grouping2;
        }

        public void setGrouping2(String[] grouping2)
        {
            _grouping2 = grouping2;
        }

        public String[] getColumns()
        {
            return _columns;
        }

        public void setColumns(String[] columns)
        {
            _columns = columns;
        }
    }

    @RequiresPermissionClass(EditSpecimenDataPermission.class)
    public static class UpdateSpecimenQueryRowAction extends UserSchemaAction
    {
        public ModelAndView getView(QueryUpdateForm tableForm, boolean reshow, BindException errors) throws Exception
        {
            // Don't allow GlobalUniqueId to be edited
            TableInfo tableInfo = tableForm.getTable();
            if (null != tableInfo.getColumn("GlobalUniqueId"))
                tableInfo.getColumn("GlobalUniqueId").setReadOnly(true);

            ColumnInfo vialComments = tableInfo.getColumn("VialComments");
            if (null != vialComments)
            {
                vialComments.setUserEditable(false);
                vialComments.setHidden(true);
            }

            SpecimenController.fixSpecimenRequestableColumn(tableForm);
            ButtonBar bb = createSubmitCancelButtonBar(tableForm);
            UpdateView view = new UpdateView(tableForm, errors);
            view.getDataRegion().setButtonBar(bb);
            return view;
        }

        public boolean handlePost(QueryUpdateForm tableForm, BindException errors) throws Exception
        {
            doInsertUpdate(tableForm, errors, false);
            return 0 == errors.getErrorCount();
        }

        public NavTree appendNavTrail(NavTree root)
        {
            super.appendNavTrail(root);
            root.addChild("Edit " + _form.getQueryName());
            return root;
        }
    }

    @RequiresPermissionClass(EditSpecimenDataPermission.class)
    public static class InsertSpecimenQueryRowAction extends UserSchemaAction
    {
        public ModelAndView getView(QueryUpdateForm tableForm, boolean reshow, BindException errors) throws Exception
        {
            TableInfo tableInfo = tableForm.getTable();
            ColumnInfo vialComments = tableInfo.getColumn("VialComments");
            if (null != vialComments)
            {
                vialComments.setUserEditable(false);
                vialComments.setHidden(true);
            }

            SpecimenController.fixSpecimenRequestableColumn(tableForm);
            InsertView view = new InsertView(tableForm, errors);
            view.getDataRegion().setButtonBar(createSubmitCancelButtonBar(tableForm));
            return view;
        }

        public boolean handlePost(QueryUpdateForm tableForm, BindException errors) throws Exception
        {
            doInsertUpdate(tableForm, errors, true);
            return 0 == errors.getErrorCount();
        }

        public NavTree appendNavTrail(NavTree root)
        {
            super.appendNavTrail(root);
            root.addChild("Insert " + _form.getQueryName());
            return root;
        }
    }

    private static void fixSpecimenRequestableColumn(QueryUpdateForm tableForm)
    {
        TableInfo tableInfo = tableForm.getTable(); //TODO: finish fixing bug
        if (tableInfo instanceof SpecimenDetailTable)
            ((SpecimenDetailTable)tableInfo).changeRequestableColumn();
    }


    public static class DesignerForm extends ReturnUrlForm
    {
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class DesignerAction extends SimpleViewAction<DesignerForm>
    {
        private DesignerForm _form;

        public ModelAndView getView(DesignerForm form, BindException errors) throws Exception
        {
            _form = form;
            Map<String, String> properties = new HashMap<>();

            if (form.getReturnUrl() != null)
            {
                properties.put(ActionURL.Param.returnUrl.name(), form.getReturnUrl().getSource());
            }

            // hack for 4404 : Lookup picker performance is terrible when there are many containers
            ContainerManager.getAllChildren(ContainerManager.getRoot());

            return new StudyGWTView(new StudyApplication.SpecimenDesigner(), properties);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("manageSpecimens#editProperties");
            _appendManageStudy(root);
            root.addChild("Specimen Properties");
            return root;
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class ServiceAction extends GWTServiceAction
    {
        protected BaseRemoteService createService()
        {
            return new SpecimenServiceImpl(getViewContext());
        }
    }

}
