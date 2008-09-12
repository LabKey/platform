/*
 * Copyright (c) 2008 LabKey Corporation
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

import org.labkey.study.controllers.BaseStudyController;
import org.labkey.study.controllers.BaseController;
import org.labkey.study.controllers.StudyController;

import org.labkey.study.query.*;
import org.labkey.study.model.*;
import org.labkey.study.SampleManager;
import org.labkey.study.StudySchema;
import org.labkey.study.requirements.SpecimenRequestRequirementType;
import org.labkey.study.requirements.RequirementProvider;
import org.labkey.study.samples.notifications.ActorNotificationRecipientSet;
import org.labkey.study.samples.notifications.NotificationRecipientSet;
import org.labkey.study.samples.notifications.DefaultRequestNotification;
import org.labkey.study.samples.ByteArrayAttachmentFile;
import org.labkey.study.samples.report.participant.ParticipantSummaryReportFactory;
import org.labkey.study.samples.report.request.RequestReportFactory;
import org.labkey.study.samples.report.request.RequestSiteReportFactory;
import org.labkey.study.samples.report.request.RequestEnrollmentSiteReportFactory;
import org.labkey.study.samples.report.request.RequestParticipantReportFactory;
import org.labkey.study.samples.report.specimentype.TypeCohortReportFactory;
import org.labkey.study.samples.report.specimentype.TypeParticipantReportFactory;
import org.labkey.study.samples.report.specimentype.TypeSummaryReportFactory;
import org.labkey.study.samples.report.participant.ParticipantTypeReportFactory;
import org.labkey.study.samples.report.participant.ParticipantSiteReportFactory;
import org.labkey.study.samples.report.SpecimenVisitReportParameters;
import org.labkey.study.samples.report.SpecimenReportExcelWriter;
import org.labkey.api.action.*;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;
import org.labkey.api.view.*;
import org.labkey.api.data.*;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.common.util.Pair;

import org.springframework.web.servlet.ModelAndView;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.validation.ObjectError;

import javax.servlet.http.HttpSession;
import javax.servlet.ServletException;
import java.util.*;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.io.BufferedOutputStream;

/**
 * User: brittp
 * Date: Dec 20, 2007
 * Time: 11:08:31 AM
 */
public class SpringSpecimenController extends BaseStudyController
{
    private static DefaultActionResolver _actionResolver =
            new BeehivePortingActionResolver(SamplesController.class, SpringSpecimenController.class);

    public SpringSpecimenController()
    {
        setActionResolver(_actionResolver);
    }

    private SpecimenUtils getUtils()
    {
        return new SpecimenUtils(this);
    }

    @RequiresPermission(ACL.PERM_READ)
    public class BeginAction extends SimpleRedirectAction
    {
        public ActionURL getRedirectURL(Object o)
        {
            return new ActionURL(SamplesAction.class, getContainer());
        }
    }

    private NavTree appendBaseSpecimenNavTrail(NavTree root, boolean vialView)
    {
        root = _appendNavTrail(root);
        ActionURL specimenURL = new ActionURL(SamplesAction.class,  getContainer());
        specimenURL.addParameter(SampleViewTypeForm.PARAMS.showVials, Boolean.toString(vialView));
        root.addChild(vialView ? "Vials" : "Specimens", specimenURL);
        return root;
    }

    private NavTree appendSpecimenRequestsNavTrail(NavTree root)
    {
        root = appendBaseSpecimenNavTrail(root, false);
        return root.addChild("Specimen Requests", new ActionURL(ViewRequestsAction.class, getContainer()));
    }

    private NavTree appendSpecimenRequestNavTrail(NavTree root, int requestId)
    {
        root = appendSpecimenRequestsNavTrail(root);
        return root.addChild("Specimen Request " + requestId, getManageRequestURL(requestId));
    }


    private ActionURL getManageRequestURL(int requestID)
    {
        return new ActionURL(ManageRequestAction.class, getContainer()).addParameter(IdForm.PARAMS.id, Integer.toString(requestID));
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
            HttpView.throwRedirect(new ActionURL(SamplesAction.class, getContainer()));
            return null; // return null to remove intellij warning
        }

        if (newFilter)
            selectionCache = new Pair<Container, Set<String>>(getContainer(), lsids);

        session.setAttribute(SELECTED_SAMPLES_SESSION_ATTRIB_KEY, selectionCache);
        return selectionCache.getValue();
    }


    private static final String SELECTED_SAMPLES_SESSION_ATTRIB_KEY = SpringSpecimenController.class.getName() + "/SelectedSamples";
    @RequiresPermission(ACL.PERM_READ)
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
            Study study = getStudy();
            Set<Pair<String, String>> ptidVisits = new HashSet<Pair<String, String>>();
            if (getFilterPds() != null)
            {
                for (ParticipantDataset pd : getFilterPds())
                {
                    Visit visit = pd.getSequenceNum() != null ? StudyManager.getInstance().getVisitForSequence(study, pd.getSequenceNum()) : null;
                    ptidVisits.add(new Pair<String, String>(pd.getParticipantId(), visit != null ? visit.getLabel() : "" + pd.getSequenceNum()));
                }
            }
            SpecimenQueryView view = createInitializedQueryView(form, errors, form.getExportType() != null, null);
            JspView<SpecimenHeaderBean> header = new JspView<SpecimenHeaderBean>("/org/labkey/study/view/samples/samplesHeader.jsp",
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
            if (lsids != null)
            {
                view = getUtils().getSpecimenQueryView(form.isShowVials(), forExport, getFilterPds(), form.isCommentsMode());
            }
            else
                view = getUtils().getSpecimenQueryView(form.isShowVials(), forExport, form.isCommentsMode());
            view.setAllowExcelWebQuery(false);
            return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root = _appendNavTrail(root);
            root.addChild(_vialView ? "Selected Vials" : "Selected Specimens");
            return root;
        }
    }

    @RequiresPermission(ACL.PERM_READ)
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
            SpecimenQueryView view = getUtils().getSpecimenQueryView(_vialView, forExport, form.isCommentsMode());
            if (form.isCommentsMode())
                view.setRestrictRecordSelectors(false);
            return view;
        }

        protected ModelAndView getHtmlView(SampleViewTypeForm form, BindException errors) throws Exception
        {
            if (getStudy() == null)
                HttpView.throwNotFound("No study exists in this folder.");
            SpecimenQueryView view = createInitializedQueryView(form, errors, form.getExportType() != null, null);
            JspView<SpecimenHeaderBean> header = new JspView<SpecimenHeaderBean>("/org/labkey/study/view/samples/samplesHeader.jsp",
                    new SpecimenHeaderBean(getViewContext(), view));
            return new VBox(header, view);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendBaseSpecimenNavTrail(root, _vialView);
        }
    }

    public static final class SpecimenHeaderBean
    {
        private String _otherViewURL;
        private ViewContext _viewContext;
        private boolean _showingVials;
        private ActionURL _customizeURL;
        private Set<Pair<String, String>> _filteredPtidVisits;

        public SpecimenHeaderBean(ViewContext context, SpecimenQueryView view)
        {
            this(context, view, Collections.<Pair<String, String>>emptySet());
        }

        public SpecimenHeaderBean(ViewContext context, SpecimenQueryView view, Set<Pair<String, String>> filteredPtidVisits)
        {
            Map<String,String[]> params = context.getRequest().getParameterMap();

            String currentTable = view.isShowingVials() ? "SpecimenDetail" : "SpecimenSummary";
            String otherTable =   view.isShowingVials() ? "SpecimenSummary" : "SpecimenDetail";
            ActionURL otherView = context.cloneActionURL();
            otherView.deleteParameters();

            Study study = StudyManager.getInstance().getStudy(context.getContainer());
            StudyQuerySchema schema = new StudyQuerySchema(study, context.getUser(), true);

            TableInfo otherTableInfo = schema.getTable(otherTable, null);
            for (Map.Entry<String,String[]> param : params.entrySet())
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
            _otherViewURL = otherView.getLocalURIString();
            _viewContext = context;
            _showingVials = view.isShowingVials();
            _customizeURL = view.getCustomizeURL();
            _filteredPtidVisits = filteredPtidVisits;
        }

        public String getOtherViewURL()
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

        public ActionURL getCustomizeURL()
        {
            return _customizeURL;
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
                if (!safeStrEquals(firstVisit, visitIt.next().getValue()))
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
            commentsMode
        }

        private boolean _showVials;
        private boolean _commentsMode;

        public boolean isShowVials()
        {
            return _showVials;
        }

        public void setShowVials(boolean showVials)
        {
            _showVials = showVials;
        }

        public boolean isCommentsMode()
        {
            return _commentsMode;
        }

        public void setCommentsMode(boolean commentsMode)
        {
            _commentsMode = commentsMode;
        }
    }

    private static boolean safeStrEquals(String a, String b)
    {
        if (a == null && b == null)
            return true;
        if (a == null || b == null)
            return false;
        return a.equals(b);
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

    @RequiresPermission(ACL.PERM_READ)
    public class SampleEventsAction extends SimpleViewAction<ViewEventForm>
    {
        private boolean _showingSelectedSamples;

        public ModelAndView getView(ViewEventForm viewEventForm, BindException errors) throws Exception
        {
            _showingSelectedSamples = viewEventForm.isSelected();
            Specimen specimen = SampleManager.getInstance().getSpecimen(getContainer(), viewEventForm.getId());
            if (specimen == null)
                HttpView.throwNotFound("Specimen " + viewEventForm.getId() + " does not exist.");
            JspView<Specimen> summaryView = new JspView<Specimen>("/org/labkey/study/view/samples/sample.jsp", specimen);
            summaryView.setTitle("Vial Summary");

            Integer[] requestIds = SampleManager.getInstance().getRequestIdsForSpecimen(specimen);
            SimpleFilter requestFilter;
            WebPartView relevantRequests;
            if (requestIds != null && requestIds.length > 0)
            {
                requestFilter = new SimpleFilter();
                StringBuilder whereClause = new StringBuilder();
                for (int i = 0; i < requestIds.length; i++)
                {
                    if (i > 0)
                        whereClause.append(" OR ");
                    whereClause.append("RequestId = ?");
                }
                requestFilter.addWhereClause(whereClause.toString(), requestIds);
                SpecimenRequestQueryView queryView = SpecimenRequestQueryView.createView(getViewContext(), requestFilter);
                queryView.setExtraLinks(true);
                relevantRequests = queryView;
            }
            else
                relevantRequests = new JspView("/org/labkey/study/view/samples/relevantRequests.jsp");

            relevantRequests.setTitle("Relevant Vial Requests");
            SpecimenEventQueryView vialHistoryView = SpecimenEventQueryView.createView(getViewContext(), specimen);
            vialHistoryView.setTitle("Vial History");

            return new VBox(summaryView, vialHistoryView, relevantRequests);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root = _appendNavTrail(root);
            root.addChild("Specimens", new ActionURL(SamplesAction.class,
                    getContainer()).addParameter(SampleViewTypeForm.PARAMS.showVials, "true"));
            if (_showingSelectedSamples)
                root.addChild("Selected Specimens", new ActionURL(SelectedSamplesAction.class,
                        getContainer()).addParameter(SampleViewTypeForm.PARAMS.showVials, "true"));
            root.addChild("Vial History");
            return root;
        }
    }

    private void requiresEditRequestPermissions(SampleRequest request) throws SQLException, ServletException
    {
        if (!SampleManager.getInstance().hasEditRequestPermissions(getUser(), request))
            HttpView.throwUnauthorized();
    }

    public static interface HiddenFormInputGenerator
    {
        String getHiddenFormInputs();
    }

    public static class AddToSampleRequestForm extends IdForm implements HiddenFormInputGenerator
    {
        public enum PARAMS
        {
            specimenIds
        }

        private String _specimenIds;

        public String getSpecimenIds()
        {
            return _specimenIds;
        }

        public void setSpecimenIds(String specimenIds)
        {
            _specimenIds = specimenIds;
        }

        public String getHiddenFormInputs()
        {
            StringBuilder builder = new StringBuilder();
            if (getId() != 0)
                builder.append("<input type=\"hidden\" name=\"id\" value=\"").append(getId()).append("\">\n");
            if (_specimenIds != null)
                builder.append("<input type=\"hidden\" name=\"specimenIds\" value=\"").append(PageFlowUtil.filter(_specimenIds)).append("\">");
            return builder.toString();
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class HandleAddRequestSamplesAction extends RedirectAction<AddToSampleRequestForm>
    {
        public boolean doAction(AddToSampleRequestForm addToSampleRequestForm, BindException errors) throws Exception
        {
            SampleRequest request = SampleManager.getInstance().getRequest(getContainer(), addToSampleRequestForm.getId());
            requiresEditRequestPermissions(request);
            int ids[];
            if (addToSampleRequestForm.getSpecimenIds() != null && addToSampleRequestForm.getSpecimenIds().length() > 0)
                ids = toIntArray(addToSampleRequestForm.getSpecimenIds().split(","));
            else
                ids = toIntArray(DataRegionSelection.getSelected(getViewContext(), true));

            // get list of specimens that are not already part of the request: we don't want to double-add
            Specimen[] currentSpecimens = SampleManager.getInstance().getRequestSpecimens(request);
            Set<Integer> currentSpecimenIds = new HashSet<Integer>();
            for (Specimen specimen : currentSpecimens)
                currentSpecimenIds.add(specimen.getRowId());
            List<Specimen> specimensToAdd = new ArrayList<Specimen>();
            for (int id : ids)
            {
                if (!currentSpecimenIds.contains(id))
                    specimensToAdd.add(SampleManager.getInstance().getSpecimen(getContainer(), id));
            }

            SampleManager.getInstance().createRequestSampleMapping(getUser(), request, specimensToAdd, true);
            SampleManager.getInstance().getRequirementsProvider().generateDefaultRequirements(getUser(), request);
            return true;
        }

        public void validateCommand(AddToSampleRequestForm target, Errors errors)
        {
        }

        public ActionURL getSuccessURL(AddToSampleRequestForm addToSampleRequestForm)
        {
            return getManageRequestURL(addToSampleRequestForm.getId());
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
            submissionResult
        }
        private Integer _newSite;
        private Integer _newActor;
        private String _newDescription;
        private String _export;
        private Boolean _submissionResult;

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
    }

    public abstract static class SamplesViewBean
    {
        protected SpecimenQueryView _specimenQueryView;
        protected Specimen[] _samples;

        public SamplesViewBean(ViewContext context, Specimen[] samples, boolean showHistoryLinks,
                               boolean showRecordSelectors, boolean disableLowVialIndicators, boolean restrictRecordSelectors)
        {
            _samples = samples;
            if (samples != null && samples.length > 0)
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

        public Specimen[] getSamples()
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
        private Site[] _providingSites;

        public ManageRequestBean(ViewContext context, SampleRequest sampleRequest, boolean forExport, Boolean submissionResult) throws SQLException, ServletException
        {
            super(context, SampleManager.getInstance().getRequestSpecimens(sampleRequest), !forExport, !forExport, forExport, false);
            _submissionResult = submissionResult;
            _requestManager = context.getContainer().hasPermission(context.getUser(), ACL.PERM_ADMIN);
            _possibleNotifications = getUtils().getPossibleNotifications(sampleRequest);
            _sampleRequest = sampleRequest;
            _finalState = SampleManager.getInstance().isInFinalState(_sampleRequest);
            _requirementsComplete = true;
            _missingSpecimens = SampleManager.getInstance().getMissingSpecimens(_sampleRequest);
            for (int i = 0; i < sampleRequest.getRequirements().length && _requirementsComplete; i++)
            {
                SampleRequestRequirement requirement = sampleRequest.getRequirements()[i];
                _requirementsComplete = requirement.isComplete();
            }

            if (_specimenQueryView != null)
            {
                List<DisplayElement> buttons = new ArrayList<DisplayElement>();

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
                    ActionButton addButton = new ActionButton("showSearch.view?showVials=true", "Specimen Search");
                    ActionButton deleteButton = new ActionButton("handleRemoveRequestSamples.post", "Remove Selected");
                    _specimenQueryView.addHiddenFormField("id", "" + _sampleRequest.getRowId());
                    buttons.add(addButton);
                    buttons.add(deleteButton);
                }
                _specimenQueryView.setButtons(buttons);
            }
        }

        public synchronized List<ActorNotificationRecipientSet> getPossibleNotifications() throws SQLException
        {
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

        public SampleRequestStatus getStatus() throws SQLException
        {
            return SampleManager.getInstance().getRequestStatus(_sampleRequest.getContainer(), _sampleRequest.getStatusId());
        }

        public Site getDestinationSite() throws SQLException
        {
            Integer destinationSiteId = _sampleRequest.getDestinationSiteId();
            if (destinationSiteId != null)
            {
                Site[] sites = StudyManager.getInstance().getSites(_sampleRequest.getContainer());
                for (Site site : sites)
                {
                    if (destinationSiteId.intValue() == site.getRowId())
                        return site;
                }
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

        public Site[] getProvidingSites() throws SQLException
        {
            if (_providingSites == null)
            {
                Set<Integer> siteSet = new HashSet<Integer>();
                for (Specimen specimen : _sampleRequest.getSpecimens())
                    siteSet.add(specimen.getCurrentLocation());
                _providingSites = new Site[siteSet.size()];
                int i = 0;
                for (Integer siteId : siteSet)
                    _providingSites[i++] = StudyManager.getInstance().getSite(getContainer(), siteId);
            }
            return _providingSites;
        }
    }


    @RequiresPermission(ACL.PERM_READ)
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
                HttpView.throwNotFound();
            _requestId = request.getRowId();
            ManageRequestBean bean = new ManageRequestBean(getViewContext(), request, form.getExport() != null, form.isSubmissionResult());
            if (form.getExport() != null)
            {
                getUtils().writeExportData(bean.getSpecimenQueryView(), form.getExport());
                return null;
            }
            else
            {
                return new JspView<ManageRequestBean>("/org/labkey/study/view/samples/manageRequest.jsp", bean);
            }
        }

        public boolean handlePost(ManageRequestForm form, BindException errors) throws Exception
        {
            SampleRequest request = SampleManager.getInstance().getRequest(getContainer(), form.getId());
            if (request == null)
                HttpView.throwNotFound();
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
            return getManageRequestURL(manageRequestForm.getId());
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

        public SampleRequestStatus[] getStauses() throws SQLException
        {
            return SampleManager.getInstance().getRequestStatuses(_context.getContainer(), _context.getUser());
        }

        public boolean isFilteredStatus(SampleRequestStatus status)
        {
            return status.getLabel().equals(_context.getActionURL().getParameter(PARAM_STATUSLABEL));
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class ViewRequestsAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            requiresLogin();
            SpecimenRequestQueryView grid = SpecimenRequestQueryView.createView(getViewContext());
            grid.setExtraLinks(true);
            grid.setShowCustomizeLink(false);
            if (SampleManager.getInstance().isSpecimenShoppingCartEnabled(getContainer()))
            {
                ActionButton insertButton = new ActionButton("showCreateSampleRequest.view", "Create New Request", DataRegion.MODE_GRID, ActionButton.Action.LINK);
                insertButton.setDisplayPermission(ACL.PERM_INSERT);
                grid.setButtons(Collections.singletonList((DisplayElement) insertButton));
            }
            else
                grid.setButtons(Collections.<DisplayElement>emptyList());
            JspView<ViewRequestsHeaderBean> header = new JspView<ViewRequestsHeaderBean>("/org/labkey/study/view/samples/viewRequestsHeader.jsp",
                    new ViewRequestsHeaderBean(getViewContext(), grid));

            return new VBox(header, grid);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendSpecimenRequestsNavTrail(root);
        }
    }


    @RequiresPermission(ACL.PERM_READ)
    public class HandleRemoveRequestSamplesAction extends FormHandlerAction<AddToSampleRequestForm>
    {
        public void validateCommand(AddToSampleRequestForm target, Errors errors)
        {
        }

        public boolean handlePost(AddToSampleRequestForm form, BindException errors) throws Exception
        {
            SampleRequest request = SampleManager.getInstance().getRequest(getContainer(), form.getId());
            requiresEditRequestPermissions(request);
            int[] sampleIds = toIntArray(DataRegionSelection.getSelected(getViewContext(), true));
            SampleManager.getInstance().deleteRequestSampleMappings(getUser(), request, sampleIds, true);
            return true;
        }

        public ActionURL getSuccessURL(AddToSampleRequestForm addToSampleRequestForm)
        {
            return getManageRequestURL(addToSampleRequestForm.getId());
        }
    }

    public static class ManageRequestStatusForm extends IdForm
    {
        private int _status;
        private String _comments;
        private String[] _notificationIdPairs;

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
    }

    @RequiresPermission(ACL.PERM_ADMIN)
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
                HttpView.throwNotFound();
            return new JspView<ManageRequestBean>("/org/labkey/study/view/samples/manageRequestStatus.jsp",
                    new ManageRequestBean(getViewContext(), _sampleRequest, false, null));
        }

        public boolean handlePost(final ManageRequestStatusForm form, BindException errors) throws Exception
        {
            _sampleRequest = SampleManager.getInstance().getRequest(getContainer(), form.getId());
            if (_sampleRequest == null)
                HttpView.throwNotFound();

            boolean statusChanged = form.getStatus() != _sampleRequest.getStatusId();

            List<AttachmentFile> files = getAttachmentFileList();
            boolean hasAttachments = !files.isEmpty();

            boolean hasComments = form.getComments() != null && form.getComments().length() > 0;
            if (statusChanged || hasComments || hasAttachments)
            {
                SampleManager.RequestEventType changeType;
                String comment;
                String eventSummary;
                if (statusChanged)
                {
                    SampleRequestStatus prevStatus = SampleManager.getInstance().getRequestStatus(getContainer(), _sampleRequest.getStatusId());
                    SampleRequestStatus newStatus = SampleManager.getInstance().getRequestStatus(getContainer(), form.getStatus());
                    comment = "Status changed from \"" + (prevStatus != null ? prevStatus.getLabel() : "N/A") + "\" to \"" +
                            (newStatus != null ? newStatus.getLabel() : "N/A") + "\"";
                    eventSummary = comment;
                    if (hasComments)
                        comment += ": " + form.getComments();
                    _sampleRequest = _sampleRequest.createMutable();
                    _sampleRequest.setStatusId(form.getStatus());
                    _sampleRequest.setModified(new Date());
                    SampleManager.getInstance().updateRequest(getUser(), _sampleRequest);
                    changeType = SampleManager.RequestEventType.REQUEST_STATUS_CHANGED;
                }
                else
                {
                    changeType = SampleManager.RequestEventType.COMMENT_ADDED;
                    comment = form.getComments();
                    eventSummary = "Comments added.";
                }

                SampleRequestEvent event = SampleManager.getInstance().createRequestEvent(getUser(), _sampleRequest, changeType,
                        comment, files);
                List<? extends NotificationRecipientSet> recipients = getUtils().getNotifications(_sampleRequest, form.getNotificationIdPairs());
                final Attachment[] attachments = AttachmentService.get().getAttachments(event);
                getUtils().sendNotification(new DefaultRequestNotification(_sampleRequest, recipients, eventSummary)
                {
                    @Override
                    public Attachment[] getAttachments()
                    {
                        return attachments;
                    }

                    @Override
                    public String getComments()
                    {
                        return form.getComments();
                    }
                });
            }
            return true;
        }

        public ActionURL getSuccessURL(ManageRequestStatusForm manageRequestForm)
        {
            return getManageRequestURL(_sampleRequest.getRowId());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root = appendSpecimenRequestNavTrail(root, _sampleRequest.getRowId());
            return root.addChild("Request Status");
        }
    }

    public static class CreateSampleRequestForm extends ViewForm implements HiddenFormInputGenerator
    {
        private String[] _inputs;
        private int _destinationSite;
        private int[] _sampleIds;
        private boolean[] _required;
        private boolean _fromGroupedView;
        private Integer _preferredLocation;

        public String getHiddenFormInputs()
        {
            StringBuilder builder = new StringBuilder();
            if (_inputs != null)
            {
                for (String input : _inputs)
                    builder.append("<input type=\"hidden\" name=\"inputs\" value=\"").append(PageFlowUtil.filter(input)).append("\">\n");
            }
            if (_destinationSite != 0)
                builder.append("<input type=\"hidden\" name=\"destinationSite\" value=\"").append(_destinationSite).append("\">\n");
            if (_sampleIds != null)
            {
                for (int sampleId : _sampleIds)
                    builder.append("<input type=\"hidden\" name=\"sampleIds\" value=\"").append(sampleId).append("\">\n");
            }
            else
            {
                String dataRegionSelectionKey = getViewContext().getRequest().getParameter(DataRegionSelection.DATA_REGION_SELECTION_KEY);
                if (dataRegionSelectionKey != null)
                {
                    builder.append("<input type=\"hidden\" name=\"").append(DataRegionSelection.DATA_REGION_SELECTION_KEY);
                    builder.append("\" value=\"").append(dataRegionSelectionKey).append("\">\n");
                    Set<String> specimenFormValues = DataRegionSelection.getSelected(getViewContext(), false);
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
            builder.append("<input type=\"hidden\" name=\"fromGroupedView\" value=\"").append(_fromGroupedView).append("\">\n");
            return builder.toString();
        }

        public int[] getSampleIds()
        {
            return _sampleIds;
        }

        public void setSampleIds(int[] sampleIds)
        {
            _sampleIds = sampleIds;
        }

        public int getDestinationSite()
        {
            return _destinationSite;
        }

        public void setDestinationSite(int destinationSite)
        {
            _destinationSite = destinationSite;
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
    }

    public static class NewRequestBean extends SamplesViewBean
    {
        private Container _container;
        private SampleManager.SpecimenRequestInput[] _inputs;
        private String[] _inputValues;
        private int _selectedSite;
        private BindException _errors;

        public NewRequestBean(ViewContext context, SpecimenUtils.RequestedSpecimens requestedSpecimens, int selectedSite, String[] inputValues, BindException errors) throws SQLException
        {
            super(context, requestedSpecimens != null ? requestedSpecimens.getSpecimens() : null, false, false, false, false);
            _errors = errors;
            _inputs = SampleManager.getInstance().getNewSpecimenRequestInputs(context.getContainer());
            _selectedSite = selectedSite;
            _inputValues = inputValues;
            _container = context.getContainer();
        }

        public SampleManager.SpecimenRequestInput[] getInputs()
        {
            return _inputs;
        }

        public String getValue(int inputIndex) throws SQLException
        {
            if (_inputValues != null && _inputValues[inputIndex] != null && _inputValues[inputIndex].length() > 0)
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
    }

    @RequiresPermission(ACL.PERM_INSERT)
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
            if (form.getDestinationSite() <= 0)
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
            getUtils().ensureSpecimenRequestsConfigured();

            int[] sampleIds = form.getSampleIds();
            String[] inputs = form.getInputs();
            StringBuilder comments = new StringBuilder();
            SampleManager.SpecimenRequestInput[] expectedInputs =
                    SampleManager.getInstance().getNewSpecimenRequestInputs(getContainer());
            if (inputs.length != expectedInputs.length)
                throw new IllegalStateException("Expected a form element for each input.");

            for (int i = 0; i < expectedInputs.length; i++)
            {
                SampleManager.SpecimenRequestInput expectedInput = expectedInputs[i];
                if (form.getDestinationSite() != 0 && expectedInput.isRememberSiteValue())
                    expectedInput.setDefaultSiteValue(getContainer(), form.getDestinationSite(), inputs[i]);
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
            if (form.getDestinationSite() > 0)
                _sampleRequest.setDestinationSiteId(form.getDestinationSite());
            _sampleRequest.setStatusId(SampleManager.getInstance().getInitialRequestStatus(getContainer(), getUser(), false).getRowId());
            _sampleRequest = SampleManager.getInstance().createRequest(getUser(), _sampleRequest, true);
            List<Specimen> samples;
            if (sampleIds != null && sampleIds.length > 0)
            {
                samples = new ArrayList<Specimen>();
                for (int sampleId : sampleIds)
                {
                    Specimen specimen = SampleManager.getInstance().getSpecimen(getContainer(), sampleId);
                    if (specimen != null)
                        samples.add(specimen);
                    else
                    {
                        ExceptionUtil.logExceptionToMothership(getViewContext().getRequest(),
                                new IllegalStateException("Specimen ID " + sampleId + " was not found in container " + getContainer().getId()));
                    }
                }
                SampleManager.getInstance().createRequestSampleMapping(getUser(), _sampleRequest, samples, true);
            }
            SampleManager.getInstance().getRequirementsProvider().generateDefaultRequirements(getUser(), _sampleRequest);
            if (!SampleManager.getInstance().isSpecimenShoppingCartEnabled(getContainer()))
                getUtils().sendNewRequestNotifications(_sampleRequest);
            return true;
        }

        public ActionURL getSuccessURL(CreateSampleRequestForm createSampleRequestForm)
        {
            return getManageRequestURL(_sampleRequest.getRowId());
        }
    }

    public static class SelectSpecimenProviderBean
    {
        private HiddenFormInputGenerator _sourceForm;
        private Site[] _possibleSites;
        private ActionURL _formTarget;

        public SelectSpecimenProviderBean(HiddenFormInputGenerator sourceForm, Site[] possibleSites, ActionURL formTarget)
        {
            _sourceForm = sourceForm;
            _possibleSites = possibleSites;
            _formTarget = formTarget;
        }

        public Site[] getPossibleSites()
        {
            return _possibleSites;
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
        getUtils().ensureSpecimenRequestsConfigured();
        
        Specimen[] requestedSamples = getUtils().getSpecimensFromIds(form.getSampleIds());

        SpecimenUtils.RequestedSpecimens specimens = null;
        if ((requestedSamples == null || requestedSamples.length == 0) &&
                "post".equalsIgnoreCase(getViewContext().getRequest().getMethod()))
        {
            if (getViewContext().getRequest().getParameter(DataRegionSelection.DATA_REGION_SELECTION_KEY) != null)
            {
                if (form.isFromGroupedView())
                {
                    Set<String> specimenFormValues = DataRegionSelection.getSelected(getViewContext(), true);
                    try
                    {
                        specimens = getUtils().getRequestableBySampleFormValue(specimenFormValues, form.getPreferredLocation());
                    }
                    catch (SpecimenUtils.AmbiguousLocationException e)
                    {
                        // Even though this method (getCreateSampleRequestView) is used from multiple places, only HandleCreateSampleRequestAction
                        // receives a post; therefore, it's safe to say that the selectSpecimenProvider.jsp form should always post to
                        // HandleCreateSampleRequestAction.
                        return new JspView<SelectSpecimenProviderBean>("/org/labkey/study/view/samples/selectSpecimenProvider.jsp",
                                new SelectSpecimenProviderBean(form, e.getPossibleLocations(), new ActionURL(ShowCreateSampleRequestAction.class, getContainer())));
                    }
                }
                else
                    specimens = getUtils().getRequestableByVialFormValue(DataRegionSelection.getSelected(getViewContext(), true));
            }
        }
        else
            specimens = new SpecimenUtils.RequestedSpecimens(requestedSamples);

        return new JspView<NewRequestBean>("/org/labkey/study/view/samples/requestSamples.jsp",
                new NewRequestBean(getViewContext(), specimens, form.getDestinationSite(), form.getInputs(), errors));
    }

    @RequiresPermission(ACL.PERM_INSERT)
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
    
    public static class AddToExistingRequestBean extends SamplesViewBean
    {
        private SpecimenRequestQueryView _requestsGrid;
        private Site[] _providingLocations;

        public AddToExistingRequestBean(ViewContext context, SpecimenUtils.RequestedSpecimens requestedSpecimens) throws SQLException, ServletException
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
            if (!context.getContainer().hasPermission(context.getUser(), ACL.PERM_ADMIN))
            {
                if (!SampleManager.getInstance().isSpecimenShoppingCartEnabled(context.getContainer()))
                    HttpView.throwUnauthorized();
                SampleRequestStatus cartStatus = SampleManager.getInstance().getRequestShoppingCartStatus(context.getContainer(), context.getUser());
                filter = new SimpleFilter("StatusId", cartStatus.getRowId());
            }
            String addLink = context.getActionURL().relativeUrl("handleAddRequestSamples", params.toString());
            _requestsGrid = SpecimenRequestQueryView.createView(context, filter);
            _requestsGrid.setExtraLinks(false, new NavTree("Select", addLink));
            _requestsGrid.setShowCustomizeLink(false);
        }

        public SpecimenRequestQueryView getRequestsGridView()
        {
            return _requestsGrid;
        }

        public Site[] getProvidingLocations()
        {
            return _providingLocations;
        }
    }

    protected void requiresAdmin() throws ServletException
    {
        if (!getUser().isAdministrator() || !getContainer().hasPermission(getUser(), ACL.PERM_ADMIN))
            HttpView.throwUnauthorized();
    }

    @RequiresPermission(ACL.PERM_INSERT)
    public class ShowAddToSampleRequestAction extends SimpleViewAction<CreateSampleRequestForm>
    {
        public ModelAndView getView(CreateSampleRequestForm form, BindException errors) throws Exception
        {
            if (!SampleManager.getInstance().isSpecimenShoppingCartEnabled(getContainer()))
                requiresAdmin();
            SpecimenUtils.RequestedSpecimens specimens = null;
            if (getViewContext().getRequest().getParameter(DataRegionSelection.DATA_REGION_SELECTION_KEY) != null)
            {
                if (form.isFromGroupedView())
                {
                    Set<String> specimenFormValues = DataRegionSelection.getSelected(getViewContext(), true);
                    try
                    {
                        specimens = getUtils().getRequestableBySampleFormValue(specimenFormValues, form.getPreferredLocation());
                    }
                    catch (SpecimenUtils.AmbiguousLocationException e)
                    {
                        return new JspView<SelectSpecimenProviderBean>("/org/labkey/study/view/samples/selectSpecimenProvider.jsp",
                                new SelectSpecimenProviderBean(form, e.getPossibleLocations(), new ActionURL(ShowAddToSampleRequestAction.class, getContainer())));
                    }
                }
                else
                    specimens = getUtils().getRequestableByVialFormValue(DataRegionSelection.getSelected(getViewContext(), true));
            }
            return new JspView<AddToExistingRequestBean>("/org/labkey/study/view/samples/addSamplesToRequest.jsp",
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
    }

    @RequiresPermission(ACL.PERM_ADMIN)
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


    @RequiresPermission(ACL.PERM_ADMIN)
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

    @RequiresPermission(ACL.PERM_ADMIN)
    public class DeleteMissingRequestSpecimensAction extends RedirectAction<IdForm>
    {
        public void validateCommand(IdForm target, Errors errors)
        {
        }

        public boolean doAction(IdForm form, BindException errors) throws Exception
        {
            SampleRequest request = SampleManager.getInstance().getRequest(getContainer(), form.getId());
            if (request == null)
                HttpView.throwNotFound("Sample request " + form.getId() + " does not exist.");
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

        public ManageRequirementBean(ViewContext context, SampleRequest request, SampleRequestRequirement requirement) throws SQLException
        {
            _requirement = requirement;
            _possibleNotifications = getUtils().getPossibleNotifications(request);
            SimpleFilter filter = new SimpleFilter("RequestId", requirement.getRequestId());
            filter.addCondition("RequirementId", requirement.getRowId());
            _requestManager = context.getContainer().hasPermission(context.getUser(), ACL.PERM_ADMIN);
            _historyView = getUtils().getRequestEventGridView(context.getRequest(), filter);
            _finalState = SampleManager.getInstance().isInFinalState(request);
        }
        
        public boolean isDefaultNotification(ActorNotificationRecipientSet notification)
        {
            Integer requirementActorId = _requirement.getActorId();
            Integer notificationActorId = notification.getActor() != null ? notification.getActor().getRowId() : null;
            Integer requirementSiteId = _requirement.getSiteId();
            Integer notificationSiteId = notification.getSite() != null ? notification.getSite().getRowId() : null;
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

    @RequiresPermission(ACL.PERM_READ)
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
                HttpView.throwNotFound();
            return new JspView<ManageRequirementBean>("/org/labkey/study/view/samples/manageRequirement.jsp",
                    new ManageRequirementBean(getViewContext(), _sampleRequest, requirement));
        }

        public boolean handlePost(final ManageRequirementForm form, BindException errors) throws Exception
        {
            _sampleRequest = SampleManager.getInstance().getRequest(getContainer(), form.getId());
            final SampleRequestRequirement requirement =
                    SampleManager.getInstance().getRequirementsProvider().getRequirement(getContainer(), form.getRequirementId());
            if (_sampleRequest == null || requirement == null || requirement.getRequestId() != form.getId())
                HttpView.throwNotFound();

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
            SampleRequestEvent event = SampleManager.getInstance().createRequestEvent(getUser(), requirement,
                    eventType, comment.toString(), files);

            List<? extends NotificationRecipientSet> recipients = getUtils().getNotifications(_sampleRequest, form.getNotificationIdPairs());
            final Attachment[] attachments = AttachmentService.get().getAttachments(event);
            getUtils().sendNotification(new DefaultRequestNotification(_sampleRequest, recipients, eventSummary)
            {
                @Override
                public SampleRequestRequirement getRequirement()
                {
                    return requirement;
                }

                @Override
                public String getComments()
                {
                    return form.getComment();
                }

                @Override
                public Attachment[] getAttachments()
                {
                    return attachments;
                }
            });
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


    @RequiresPermission(ACL.PERM_READ)
    public class RequestHistoryAction extends SimpleViewAction<IdForm>
    {
        private int _requestId;
        public ModelAndView getView(IdForm form, BindException errors) throws Exception
        {
            _requestId = form.getId();
            HtmlView header = new HtmlView(PageFlowUtil.textLink("View Request", "manageRequest.view?id=" + form.getId()));
            SimpleFilter filter = new SimpleFilter("RequestId", form.getId());
            GridView historyGrid = getUtils().getRequestEventGridView(getViewContext().getRequest(), filter);
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

    @RequiresPermission(ACL.PERM_READ)
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
    @RequiresPermission(ACL.PERM_ADMIN)
    public class ManageDefaultReqsAction extends FormViewAction<DefaultRequirementsForm>
    {
        private String _nextPage;
        public void validateCommand(DefaultRequirementsForm target, Errors errors)
        {
        }

        public ModelAndView getView(DefaultRequirementsForm defaultRequirementsForm, boolean reshow, BindException errors) throws Exception
        {
            getUtils().ensureSpecimenRequestsConfigured();
            return new JspView<ManageReqsBean>("/org/labkey/study/view/samples/manageDefaultReqs.jsp",
                new ManageReqsBean(getUser(), getContainer()));
        }

        public boolean handlePost(DefaultRequirementsForm form, BindException errors) throws Exception
        {
            getUtils().ensureSpecimenRequestsConfigured();
            _nextPage = form.getNextPage();
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

        public ActionURL getSuccessURL(DefaultRequirementsForm defaultRequirementsForm)
        {
            if (_nextPage != null && _nextPage.length() > 0)
            {
                ActionURL url = getViewContext().cloneActionURL();
                url.deleteParameters();
                url.setAction(_nextPage);
                return url;
            }
            else
                return new ActionURL(StudyController.ManageStudyAction.class, getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return _appendManageStudy(root).addChild("Manage Default Requirements");
        }
    }


    public static class EmailSpecimenListForm extends BaseController.IdForm
    {
        private boolean _sendXls;
        private boolean _sendTsv;
        private String _comments;
        private String[] _notify;
        private String _listType;

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
    }

    public static class SubmissionForm extends ViewForm
    {
        private int _id;

        public SubmissionForm()
        {
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

    @RequiresPermission(ACL.PERM_READ)
    public class SubmitRequestAction extends RedirectAction<IdForm>
    {
        public void validateCommand(IdForm target, Errors errors)
        {
        }

        public boolean doAction(IdForm form, BindException errors) throws Exception
        {
            if (!SampleManager.getInstance().isSpecimenShoppingCartEnabled(getContainer()))
                HttpView.throwUnauthorized();
            SampleRequest request = SampleManager.getInstance().getRequest(getContainer(), form.getId());
            requiresEditRequestPermissions(request);
            Specimen[] specimens = request.getSpecimens();
            if (specimens != null && specimens.length > 0)
            {
                SampleRequestStatus newStatus = SampleManager.getInstance().getInitialRequestStatus(getContainer(), getUser(), true);
                request = request.createMutable();
                request.setStatusId(newStatus.getRowId());
                SampleManager.getInstance().updateRequest(getUser(), request);
                SampleManager.getInstance().createRequestEvent(getUser(), request,
                        SampleManager.RequestEventType.REQUEST_STATUS_CHANGED, "Request submitted for processing.", null);
                if (SampleManager.getInstance().isSpecimenShoppingCartEnabled(getContainer()))
                    getUtils().sendNewRequestNotifications(request);
                return true;
            }
            else
            {
                errors.addError(new ObjectError("Specimen Request", new String[] {"NullError"}, null, "Only requests containing specimens can be submitted."));
                return false;
            }
        }

        public ActionURL getSuccessURL(IdForm idForm)
        {
            ActionURL successURL = getManageRequestURL(idForm.getId());
            successURL.addParameter(ManageRequestForm.PARAMS.submissionResult, Boolean.TRUE.toString());
            return successURL;
        }
    }

    @RequiresPermission(ACL.PERM_READ)
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
                HttpView.throwUnauthorized();

            SampleManager.getInstance().deleteRequest(getUser(), request);
            return true;
        }

        public ActionURL getSuccessURL(IdForm idForm)
        {
            return new ActionURL(ViewRequestsAction.class, getContainer());
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class EmailLabSpecimenListsAction extends FormHandlerAction<EmailSpecimenListForm>
    {
        public void validateCommand(EmailSpecimenListForm target, Errors errors)
        {
        }

        public boolean handlePost(EmailSpecimenListForm form, BindException errors) throws Exception
        {
            final SampleRequest request = SampleManager.getInstance().getRequest(getContainer(), form.getId());
            if (request == null)
                HttpView.throwNotFound();

            Site receivingSite = StudyManager.getInstance().getSite(getContainer(), request.getDestinationSiteId());
            if (receivingSite == null)
                HttpView.throwNotFound();

            final LabSpecimenListsBean.Type type = LabSpecimenListsBean.Type.valueOf(form.getListType());

            Map<Site, List<ActorNotificationRecipientSet>> notifications = new HashMap<Site, List<ActorNotificationRecipientSet>>();
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
                    Site originatingOrProvidingSite = StudyManager.getInstance().getSite(getContainer(), ids[0]);
                    SampleRequestActor notifyActor = SampleManager.getInstance().getRequirementsProvider().getActor(getContainer(), ids[1]);
                    Site notifySite = null;
                    if (notifyActor.isPerSite() && ids[2] >= 0)
                        notifySite = StudyManager.getInstance().getSite(getContainer(), ids[2]);
                    List<ActorNotificationRecipientSet> emailRecipients = notifications.get(originatingOrProvidingSite);
                    if (emailRecipients == null)
                    {
                        emailRecipients = new ArrayList<ActorNotificationRecipientSet>();
                        notifications.put(originatingOrProvidingSite, emailRecipients);
                    }
                    emailRecipients.add(new ActorNotificationRecipientSet(notifyActor, notifySite));
                }

                List<AttachmentFile> formFiles = getAttachmentFileList();

                for (final Site originatingOrProvidingSite : notifications.keySet())
                {
                    TSVGridWriter tsvWriter = getUtils().getSpecimenListTsvWriter(request, originatingOrProvidingSite, receivingSite, type);
                    tsvWriter.setColumnHeaderType(TSVGridWriter.ColumnHeaderType.propertyName);
                    StringBuilder tsvBuilder = new StringBuilder();
                    tsvWriter.write(tsvBuilder);
                    if (form.isSendTsv())
                        formFiles.add(new ByteArrayAttachmentFile(tsvWriter.getFilenamePrefix() + ".tsv", tsvBuilder.toString().getBytes(), "text/tsv"));

                    if (form.isSendXls())
                    {
                        OutputStream ostream = null;
                        try
                        {
                            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                            ostream = new BufferedOutputStream(byteStream);
                            ExcelWriter xlsWriter = getUtils().getSpecimenListXlsWriter(request, originatingOrProvidingSite, receivingSite, type);
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

                    String header = type.getDisplay() + " location notification of specimen shipment to " + receivingSite.getDisplayName();
                    SampleRequestEvent event = SampleManager.getInstance().createRequestEvent(getUser(), request,
                            SampleManager.RequestEventType.SPECIMEN_LIST_GENERATED, header + "\n" + content.toString(), formFiles);

                    List<ActorNotificationRecipientSet> emailRecipients = notifications.get(originatingOrProvidingSite);
                    final Attachment[] attachments = AttachmentService.get().getAttachments(event);
                    getUtils().sendNotification(new DefaultRequestNotification(request, emailRecipients, header)
                    {
                        @Override
                        protected Specimen[] getSpecimenList() throws SQLException
                        {
                            SimpleFilter filter = getUtils().getSpecimenListFilter(getSampleRequest(), originatingOrProvidingSite, type);
                            return Table.select(StudySchema.getInstance().getTableInfoSpecimen(), Table.ALL_COLUMNS, filter, null, Specimen.class);
                        }

                        @Override
                        public Attachment[] getAttachments()
                        {
                            return attachments;
                        }

                        @Override
                        public String getComments()
                        {
                            return content.toString();
                        }
                    });
                }
            }
            return true;
        }

        public ActionURL getSuccessURL(EmailSpecimenListForm emailSpecimenListForm)
        {
            return getManageRequestURL(emailSpecimenListForm.getId());
        }
    }

    public static class ExportSiteForm extends BaseController.IdForm
    {
        private String _export;
        private String _specimenIds;
        private String _listType;
        private int _sourceSiteId;
        private int _destSiteId;


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
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class DownloadSpecimenListAction extends SimpleViewAction<ExportSiteForm>
    {
        public ModelAndView getView(ExportSiteForm form, BindException errors) throws Exception
        {
            SampleRequest sampleRequest = SampleManager.getInstance().getRequest(getContainer(), form.getId());
            Site sourceSite = StudyManager.getInstance().getSite(getContainer(), form.getSourceSiteId());
            Site destSite = StudyManager.getInstance().getSite(getContainer(), form.getDestSiteId());
            if (sampleRequest == null || sourceSite == null || destSite == null)
                HttpView.throwNotFound();

            LabSpecimenListsBean.Type type = LabSpecimenListsBean.Type.valueOf(form.getListType());
            if (null != form.getExport())
            {
                if ("tsv".equals(form.getExport()))
                {
                    TSVGridWriter writer = getUtils().getSpecimenListTsvWriter(sampleRequest, sourceSite, destSite, type);
                    writer.setColumnHeaderType(TSVGridWriter.ColumnHeaderType.propertyName);
                    writer.write(getViewContext().getResponse());
                }
                else if ("xls".equals(form.getExport()))
                {
                    ExcelWriter writer = getUtils().getSpecimenListXlsWriter(sampleRequest, sourceSite, destSite, type);
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

    @RequiresPermission(ACL.PERM_ADMIN)
    public class LabSpecimenListsAction extends SimpleViewAction<LabSpecimenListsForm>
    {
        private int _requestId;
        private LabSpecimenListsBean.Type _type;
        public ModelAndView getView(LabSpecimenListsForm form, BindException errors) throws Exception
        {
            SampleRequest request = SampleManager.getInstance().getRequest(getContainer(), form.getId());
            if (request == null)
                HttpView.throwNotFound();
            _requestId = request.getRowId();

            _type = LabSpecimenListsBean.Type.valueOf(form.getListType());
            return new JspView<LabSpecimenListsBean>("/org/labkey/study/view/samples/labSpecimenLists.jsp",
                    new LabSpecimenListsBean(getUtils(), request, _type));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendSpecimenRequestNavTrail(root, _requestId).addChild(_type.getDisplay() + " Lab Vial Lists");
        }
    }

    public static class LabSpecimenListsForm extends BaseController.IdForm
    {
        private String _listType;

        public String getListType()
        {
            return _listType;
        }

        public void setListType(String listType)
        {
            _listType = listType;
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

        public LabSpecimenListsBean(SpecimenUtils utils, SampleRequest sampleRequest, LabSpecimenListsBean.Type type) throws SQLException
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

        private synchronized Map<Integer, List<Specimen>> getSpecimensBySiteId() throws SQLException
        {
            if (_specimensBySiteId == null)
            {
                _specimensBySiteId = new HashMap<Integer, List<Specimen>>();
                Specimen[] specimens = _sampleRequest.getSpecimens();
                for (Specimen specimen : specimens)
                {
                    Site site;
                    if (_type == LabSpecimenListsBean.Type.ORIGINATING)
                        site = SampleManager.getInstance().getOriginatingSite(specimen);
                    else
                        site = SampleManager.getInstance().getCurrentSite(specimen);
                    if (site != null)
                    {
                        List<Specimen> current = _specimensBySiteId.get(site.getRowId());
                        if (current == null)
                        {
                            current = new ArrayList<Specimen>();
                            _specimensBySiteId.put(site.getRowId(), current);
                        }
                        current.add(specimen);
                    }
                }
            }
            return _specimensBySiteId;
        }

        public synchronized List<ActorNotificationRecipientSet> getPossibleNotifications() throws SQLException
        {
            if (_possibleNotifications == null)
                _possibleNotifications = _utils.getPossibleNotifications(_sampleRequest);
            return _possibleNotifications;
        }

        public Set<Site> getLabs() throws SQLException
        {
            Map<Integer, List<Specimen>> siteIdToSpecimens = getSpecimensBySiteId();
            Set<Site> sites = new HashSet<Site>(siteIdToSpecimens.size());
            for (Integer siteId : siteIdToSpecimens.keySet())
                sites.add(StudyManager.getInstance().getSite(_sampleRequest.getContainer(), siteId));
            return sites;
        }

        public List<Specimen> getSpecimens(Site site) throws SQLException
        {
            Map<Integer, List<Specimen>> siteSpecimenLists = getSpecimensBySiteId();
            return siteSpecimenLists.get(site.getRowId());
        }

        public Type getType()
        {
            return _type;
        }

        public boolean isRequirementsComplete()
        {
            return _requirementsComplete;
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
                JspView<FormType> reportView = new JspView<FormType>("/org/labkey/study/view/samples/specimenVisitReport.jsp", specimenVisitReportForm);
                if (this.isPrint())
                    return reportView;
                else
                {
                    return new VBox(new JspView<ReportConfigurationBean>("/org/labkey/study/view/samples/autoReportList.jsp",
                                    new ReportConfigurationBean(specimenVisitReportForm, false)), reportView);
                }
            }
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root = appendBaseSpecimenNavTrail(root, false);
            root.addChild("Available Reports", new ActionURL(AutoReportListAction.class, getContainer()));
            return root.addChild("Specimen Report: " + _form.getLabel());
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class TypeSummaryReportAction extends SpecimenVisitReportAction<TypeSummaryReportFactory>
    {
        public TypeSummaryReportAction()
        {
            super(TypeSummaryReportFactory.class);
        }
        // no implementation needed; this action exists only to provide an entry point
        // with request->bean translation for this report type.
    }

    @RequiresPermission(ACL.PERM_READ)
    public class TypeParticipantReportAction extends SpecimenVisitReportAction<TypeParticipantReportFactory>
    {
        public TypeParticipantReportAction()
        {
            super(TypeParticipantReportFactory.class);
        }
        // no implementation needed; this action exists only to provide an entry point
        // with request->bean translation for this report type.
    }

    @RequiresPermission(ACL.PERM_READ)
    public class RequestReportAction extends SpecimenVisitReportAction<RequestReportFactory>
    {
        public RequestReportAction()
        {
            super(RequestReportFactory.class);
        }
        // no implementation needed; this action exists only to provide an entry point
        // with request->bean translation for this report type.
    }

    @RequiresPermission(ACL.PERM_READ)
    public class TypeCohortReportAction extends SpecimenVisitReportAction<TypeCohortReportFactory>
    {
        public TypeCohortReportAction()
        {
            super(TypeCohortReportFactory.class);
        }
        // no implementation needed; this action exists only to provide an entry point
        // with request->bean translation for this report type.
    }

    @RequiresPermission(ACL.PERM_READ)
    public class RequestSiteReportAction extends SpecimenVisitReportAction<RequestSiteReportFactory>
    {
        public RequestSiteReportAction()
        {
            super(RequestSiteReportFactory.class);
        }
        // no implementation needed; this action exists only to provide an entry point
        // with request->bean translation for this report type.
    }

    @RequiresPermission(ACL.PERM_READ)
    public class ParticipantSummaryReportAction extends SpecimenVisitReportAction<ParticipantSummaryReportFactory>
    {
        public ParticipantSummaryReportAction()
        {
            super(ParticipantSummaryReportFactory.class);
        }
        // no implementation needed; this action exists only to provide an entry point
        // with request->bean translation for this report type.
    }

    @RequiresPermission(ACL.PERM_READ)
    public class ParticipantTypeReportAction extends SpecimenVisitReportAction<ParticipantTypeReportFactory>
    {
        public ParticipantTypeReportAction()
        {
            super(ParticipantTypeReportFactory.class);
        }
        // no implementation needed; this action exists only to provide an entry point
        // with request->bean translation for this report type.
    }

    @RequiresPermission(ACL.PERM_READ)
    public class ParticipantSiteReportAction extends SpecimenVisitReportAction<ParticipantSiteReportFactory>
    {
        public ParticipantSiteReportAction()
        {
            super(ParticipantSiteReportFactory.class);
        }
        // no implementation needed; this action exists only to provide an entry point
        // with request->bean translation for this report type.
    }

    @RequiresPermission(ACL.PERM_READ)
    public class RequestEnrollmentSiteReportAction extends SpecimenVisitReportAction<RequestEnrollmentSiteReportFactory>
    {
        public RequestEnrollmentSiteReportAction()
        {
            super(RequestEnrollmentSiteReportFactory.class);
        }
        // no implementation needed; this action exists only to provide an entry point
        // with request->bean translation for this report type.
    }

    @RequiresPermission(ACL.PERM_READ)
    public class RequestParticipantReportAction extends SpecimenVisitReportAction<RequestParticipantReportFactory>
    {
        public RequestParticipantReportAction()
        {
            super(RequestParticipantReportFactory.class);
        }
        // no implementation needed; this action exists only to provide an entry point
        // with request->bean translation for this report type.
    }

    public static class ReportListForm extends ViewForm
    {
        private String getURL(Class<? extends SpecimenVisitReportAction> actionClass, TypeSummaryReportFactory.Status status)
        {
            ActionURL url = new ActionURL(actionClass, getContainer());
            url.addParameter(TypeSummaryReportFactory.PARAMS.viewVialCount, Boolean.TRUE.toString());
            url.addParameter(TypeSummaryReportFactory.PARAMS.statusFilterName, status.name());
            return url.getLocalURIString();
        }

        public String getSummaryReportURL(TypeSummaryReportFactory.Status status)
        {
            return getURL(TypeSummaryReportAction.class, status);
        }
    }

    public static class ReportConfigurationBean
    {
        private static final String COUNTS_BY_DERIVATIVE_TYPE_TITLE = "Specimen Types by Timepoint";
        private static final String REQUESTS_BY_DERIVATIVE_TYPE_TITLE = "Requested Vials by Type and Timepoint";
        private static final String COUNTS_BY_PARTICIPANT_TITLE = "Participants By Timepoint";
        private Map<String, List<SpecimenVisitReportParameters>> _reportFactories = new LinkedHashMap<String, List<SpecimenVisitReportParameters>>();
        private boolean _listView = true;
        private ViewContext _viewContext;

        public ReportConfigurationBean(ViewContext viewContext)
        {
            _viewContext = viewContext;
            registerReportFactory(COUNTS_BY_DERIVATIVE_TYPE_TITLE, new TypeSummaryReportFactory());
            registerReportFactory(COUNTS_BY_DERIVATIVE_TYPE_TITLE, new TypeParticipantReportFactory());
            if (StudyManager.getInstance().showCohorts(_viewContext.getContainer(), _viewContext.getUser()))
                registerReportFactory(COUNTS_BY_DERIVATIVE_TYPE_TITLE, new TypeCohortReportFactory());
            registerReportFactory(REQUESTS_BY_DERIVATIVE_TYPE_TITLE, new RequestReportFactory());
            registerReportFactory(REQUESTS_BY_DERIVATIVE_TYPE_TITLE, new RequestSiteReportFactory());
            registerReportFactory(REQUESTS_BY_DERIVATIVE_TYPE_TITLE, new RequestEnrollmentSiteReportFactory());
            registerReportFactory(REQUESTS_BY_DERIVATIVE_TYPE_TITLE, new RequestParticipantReportFactory());
            registerReportFactory(COUNTS_BY_PARTICIPANT_TITLE, new ParticipantSummaryReportFactory());
            registerReportFactory(COUNTS_BY_PARTICIPANT_TITLE, new ParticipantTypeReportFactory());
            registerReportFactory(COUNTS_BY_PARTICIPANT_TITLE, new ParticipantSiteReportFactory());
        }

        public ReportConfigurationBean(SpecimenVisitReportParameters singleFactory, boolean listView)
        {
            _listView = listView;
            _viewContext = singleFactory.getViewContext();
            assert (_viewContext != null) : "Expected report factory to be instantiated by Spring.";
            registerReportFactory(COUNTS_BY_DERIVATIVE_TYPE_TITLE, singleFactory);
        }

        private void registerReportFactory(String category, SpecimenVisitReportParameters factory)
        {
            // we have to explicitly set the view context for these reports, since the factories aren't being newed-up by Spring in the usual way:
            factory.setViewContext(_viewContext);
            factory.reset(null, _viewContext.getRequest());
            List<SpecimenVisitReportParameters> factories = _reportFactories.get(category);
            if (factories == null)
            {
                factories = new ArrayList<SpecimenVisitReportParameters>();
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
    }

    @RequiresPermission(ACL.PERM_READ)
    public class AutoReportListAction extends SimpleViewAction<ReportListForm>
    {
        public ModelAndView getView(ReportListForm form, BindException errors) throws Exception
        {
            return new JspView<ReportConfigurationBean>("/org/labkey/study/view/samples/autoReportList.jsp", new ReportConfigurationBean(getViewContext()));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root = appendBaseSpecimenNavTrail(root, false);
            return root.addChild("Specimen Reports");
        }
    }

    public static class UpdateSpecimenCommentsForm extends ViewForm
    {
        private String _comments;
        private int[] _rowId;
        private boolean _fromGroupedView;
        private String _referrer;
        private boolean _saveCommentsPost;

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
    }
    
    public static class UpdateSpecimenCommentsBean extends SamplesViewBean
    {
        private String _referrer;
        private String _currentComment;

        public UpdateSpecimenCommentsBean(ViewContext context, Specimen[] samples, String referrer)
        {
            super(context, samples, false, false, true, true);
            _referrer = referrer;
            try
            {
                Map<Specimen, String> currentComments = SampleManager.getInstance().getSpecimenComments(samples);
                boolean sameComment = true;
                Iterator<String> it = currentComments.values().iterator();
                String prevComment = it.next();
                while (it.hasNext() && sameComment)
                {
                    String currentComment = it.next();
                    sameComment = safeStrEquals(prevComment, currentComment);
                    prevComment = currentComment;
                }
                if (sameComment)
                    _currentComment = prevComment;
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }

        public String getReferrer()
        {
            return _referrer;
        }

        public String getCurrentComment()
        {
            return _currentComment;
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class ClearCommentsAction extends RedirectAction<UpdateSpecimenCommentsForm>
    {
        public ActionURL getSuccessURL(UpdateSpecimenCommentsForm updateSpecimenCommentsForm)
        {
            return new ActionURL(updateSpecimenCommentsForm.getReferrer());
        }

        public boolean doAction(UpdateSpecimenCommentsForm updateSpecimenCommentsForm, BindException errors) throws Exception
        {
            Specimen[] selectedVials = getUtils().getSpecimensFromPost(updateSpecimenCommentsForm.isFromGroupedView(), false);
            if (selectedVials != null)
            {
                for (Specimen specimen : selectedVials)
                    SampleManager.getInstance().setSpecimenComment(getUser(), specimen, null);
            }
            return true;
        }

        public void validateCommand(UpdateSpecimenCommentsForm target, Errors errors)
        {
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class UpdateCommentsAction extends FormViewAction<UpdateSpecimenCommentsForm>
    {
        public void validateCommand(UpdateSpecimenCommentsForm specimenCommentsForm, Errors errors)
        {
            if (specimenCommentsForm.isSaveCommentsPost() &&
                    (specimenCommentsForm.getRowId() == null ||
                     specimenCommentsForm.getRowId().length == 0))
            {
                errors.reject(null, "No vials were selected.");
            }
        }

        public ModelAndView getView(UpdateSpecimenCommentsForm specimenCommentsForm, boolean reshow, BindException errors) throws Exception
        {
            Specimen[] selectedVials = getUtils().getSpecimensFromPost(specimenCommentsForm.isFromGroupedView(), false);

            if (selectedVials == null || selectedVials.length == 0)
                return new HtmlView("No vials selected.  [<a href=\"javascript:back()\">back</a>]");

            return new JspView<UpdateSpecimenCommentsBean>("/org/labkey/study/view/samples/updateComments.jsp",
                    new UpdateSpecimenCommentsBean(getViewContext(), selectedVials, specimenCommentsForm.getReferrer()), errors);
        }

        public boolean handlePost(UpdateSpecimenCommentsForm commentsForm, BindException errors) throws Exception
        {
            if (commentsForm.getRowId() == null)
                return false;
            for (int rowId : commentsForm.getRowId())
            {
                Specimen vial = SampleManager.getInstance().getSpecimen(getContainer(), rowId);
                SampleManager.getInstance().setSpecimenComment(getUser(), vial, commentsForm.getComments());
            }
            return true;
        }

        public ActionURL getSuccessURL(UpdateSpecimenCommentsForm commentsForm)
        {
            return new ActionURL(commentsForm.getReferrer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root = appendBaseSpecimenNavTrail(root, true);
            return root.addChild("Set vial comments");
        }
    }
    
}