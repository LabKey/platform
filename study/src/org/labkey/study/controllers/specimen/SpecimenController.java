/*
 * Copyright (c) 2014-2019 LabKey Corporation
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

package org.labkey.study.controllers.specimen;

import org.apache.commons.io.Charsets;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.QueryViewAction;
import org.labkey.api.action.SimpleRedirectAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.ByteArrayAttachmentFile;
import org.labkey.api.audit.TransactionAuditProvider;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.data.ExcelWriter;
import org.labkey.api.data.MenuButton;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TSVGridWriter;
import org.labkey.api.data.TSVWriter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.pipeline.browse.PipelinePathForm;
import org.labkey.api.query.AbstractQueryImportAction;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateForm;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchemaAction;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.security.ActionNames;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.specimen.RequestEventType;
import org.labkey.api.specimen.SpecimenManager;
import org.labkey.api.specimen.SpecimenManagerNew;
import org.labkey.api.specimen.SpecimenMigrationService;
import org.labkey.api.specimen.SpecimenRequestException;
import org.labkey.api.specimen.SpecimenRequestManager;
import org.labkey.api.specimen.SpecimenRequestManager.SpecimenRequestInput;
import org.labkey.api.specimen.SpecimenRequestStatus;
import org.labkey.api.specimen.Vial;
import org.labkey.api.specimen.actions.HiddenFormInputGenerator;
import org.labkey.api.specimen.actions.IdForm;
import org.labkey.api.specimen.actions.ParticipantCommentForm;
import org.labkey.api.specimen.importer.RequestabilityManager;
import org.labkey.api.specimen.location.LocationImpl;
import org.labkey.api.specimen.location.LocationManager;
import org.labkey.api.specimen.model.SpecimenComment;
import org.labkey.api.specimen.model.SpecimenRequestActor;
import org.labkey.api.specimen.model.SpecimenRequestEvent;
import org.labkey.api.specimen.notifications.ActorNotificationRecipientSet;
import org.labkey.api.specimen.notifications.DefaultRequestNotification;
import org.labkey.api.specimen.notifications.NotificationRecipientSet;
import org.labkey.api.specimen.query.SpecimenQueryView;
import org.labkey.api.specimen.requirements.SpecimenRequest;
import org.labkey.api.specimen.requirements.SpecimenRequestRequirement;
import org.labkey.api.specimen.requirements.SpecimenRequestRequirementProvider;
import org.labkey.api.specimen.security.permissions.EditSpecimenDataPermission;
import org.labkey.api.specimen.security.permissions.ManageNewRequestFormPermission;
import org.labkey.api.specimen.security.permissions.ManageRequestsPermission;
import org.labkey.api.specimen.security.permissions.RequestSpecimensPermission;
import org.labkey.api.specimen.security.permissions.SetSpecimenCommentsPermission;
import org.labkey.api.specimen.settings.RequestNotificationSettings;
import org.labkey.api.specimen.settings.SettingsManager;
import org.labkey.api.study.CohortFilter;
import org.labkey.api.study.Location;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.study.SpecimenUrls;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.StudyUrls;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.Visit;
import org.labkey.api.study.model.ParticipantDataset;
import org.labkey.api.util.Button;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.Link.LinkBuilder;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.DisplayElement;
import org.labkey.api.view.GridView;
import org.labkey.api.view.HBox;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.InsertView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.UpdateView;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.study.CohortFilterFactory;
import org.labkey.study.controllers.BaseStudyController;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.SecurityType;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.query.DatasetQuerySettings;
import org.labkey.study.query.DatasetQueryView;
import org.labkey.study.query.SpecimenDetailTable;
import org.labkey.study.query.StudyQuerySchema;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.validation.ObjectError;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletException;
import javax.servlet.http.HttpSession;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
@SuppressWarnings("UnusedDeclaration")
public class SpecimenController extends BaseStudyController
{
    private static final Logger _log = LogManager.getLogger(SpecimenController.class);

    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(
        SpecimenController.class,
        ParticipantCommentAction.SpecimenCommentInsertAction.class,
        ParticipantCommentAction.SpecimenCommentUpdateAction.class
    );

    public SpecimenController()
    {
        setActionResolver(_actionResolver);
    }

    private static long[] toLongArray(Collection<String> intStrings)
    {
        if (intStrings == null)
            return null;
        long[] converted = new long[intStrings.size()];
        int index = 0;
        for (String intString : intStrings)
            converted[index++] = Long.parseLong(intString);
        return converted;
    }

    protected SpecimenUtils getUtils()
    {
        return new SpecimenUtils(this);
    }

    @Override
    protected void _addManageStudy(NavTree root)
    {
        urlProvider(StudyUrls.class).addManageStudyNavTrail(root, getContainer(), getUser());
    }

    public static class SpecimenUrlsImpl implements SpecimenUrls
    {
        @Override
        public ActionURL getSpecimensURL(Container c)
        {
            return SpecimenController.getSpecimensURL(c);
        }

        @Override
        public ActionURL getSpecimensURL(Container c, boolean showVials)
        {
            return getSpecimensURL(c).addParameter(SpecimenViewTypeForm.PARAMS.showVials, showVials);
        }

        @Override
        public ActionURL getSelectedSpecimensURL(Container c, boolean showVials)
        {
            return new ActionURL(SelectedSpecimensAction.class, c).addParameter(SpecimenViewTypeForm.PARAMS.showVials, showVials);
        }

        @Override
        public ActionURL getCommentURL(Container c, String globalUniqueId)
        {
            return getSpecimensURL(c)
                .addParameter(SpecimenController.SpecimenViewTypeForm.PARAMS.showVials, true)
                .addParameter(SpecimenController.SpecimenViewTypeForm.PARAMS.viewMode, SpecimenQueryView.Mode.COMMENTS.name())
                .addParameter("SpecimenDetail.GlobalUniqueId~eq", globalUniqueId);
        }

        @Override
        public ActionURL getManageRequestURL(Container c)
        {
            return new ActionURL(ManageRequestAction.class, c);
        }

        @Override
        public ActionURL getManageRequestStatusURL(Container c, int requestId)
        {
            return new ActionURL(ManageRequestStatusAction.class, c).addParameter("id", requestId);
        }

        @Override
        public ActionURL getRequestDetailsURL(Container c, int requestId)
        {
            return new ActionURL(ManageRequestAction.class, c).addParameter("id", requestId);
        }

        @Override
        public ActionURL getRequestDetailsURL(Container c, String requestId)
        {
            return new ActionURL(ManageRequestAction.class, c).addParameter("id", requestId);
        }

        @Override
        public ActionURL getUpdateSpecimenQueryRowURL(Container c, String schemaName, TableInfo table)
        {
            ActionURL url = new ActionURL(UpdateSpecimenQueryRowAction.class, c);
            url.addParameter("schemaName", schemaName);
            url.addParameter(QueryView.DATAREGIONNAME_DEFAULT + "." + QueryParam.queryName, table.getName());

            return url;
        }

        @Override
        public ActionURL getInsertSpecimenQueryRowURL(Container c, String schemaName, TableInfo table)
        {
            ActionURL url = new ActionURL(InsertSpecimenQueryRowAction.class, c);
            url.addParameter("schemaName", schemaName);
            url.addParameter(QueryView.DATAREGIONNAME_DEFAULT + "." + QueryParam.queryName, table.getName());

            return url;
        }

        @Override
        public ActionURL getSubmitRequestURL(Container c, String id)
        {
            return new ActionURL(SubmitRequestAction.class, c).addParameter("id", "${requestId}");
        }

        @Override
        public ActionURL getDeleteRequestURL(Container c, String id)
        {
            return new ActionURL(DeleteRequestAction.class, c).addParameter("id", "${requestId}");
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class BeginAction extends SimpleRedirectAction
    {
        @Override
        public ActionURL getRedirectURL(Object o)
        {
            return getSpecimensURL();
        }
    }

    private void addBaseSpecimenNavTrail(NavTree root)
    {
        _addNavTrail(root);
        ActionURL overviewURL = SpecimenMigrationService.get().getOverviewURL(getContainer());
        root.addChild("Specimen Overview", overviewURL);
    }

    private void addSpecimenRequestsNavTrail(NavTree root)
    {
        addBaseSpecimenNavTrail(root);
        root.addChild("Specimen Requests", SpecimenMigrationService.get().getViewRequestsURL(getContainer()));
    }

    private void addSpecimenRequestNavTrail(NavTree root, int requestId)
    {
        addSpecimenRequestsNavTrail(root);
        root.addChild("Specimen Request " + requestId, getManageRequestURL(requestId));
    }


    private ActionURL getManageRequestURL(int requestID)
    {
        return getManageRequestURL(requestID, null);
    }

    private ActionURL getManageRequestURL(int requestID, String returnUrl)
    {
        ActionURL url = new ActionURL(ManageRequestAction.class, getContainer());
        url.addParameter(IdForm.PARAMS.id, Integer.toString(requestID));
        if (returnUrl != null)
            url.addParameter(ManageRequestForm.PARAMS.returnUrl, returnUrl);
        return url;
    }

    private Set<String> getSelectionLsids()
    {
        // save the selected set of participantdataset lsids in the session; this is the only obvious way
        // to let the user apply subsequent filters and switch back and forth between vial and specimen view
        // without losing their original participant/visit selection.
        Set<String> lsids = null;
        if (isPost())
            lsids = DataRegionSelection.getSelected(getViewContext(), true);
        HttpSession session = getViewContext().getRequest().getSession(true);
        Pair<Container, Set<String>> selectionCache = (Pair<Container, Set<String>>) session.getAttribute(SELECTED_SPECIMENS_SESSION_ATTRIB_KEY);

        boolean newFilter = (lsids != null && !lsids.isEmpty());
        boolean cachedFilter = selectionCache != null && getContainer().equals(selectionCache.getKey());
        if (!newFilter && !cachedFilter)
        {
            throw new RedirectException(getSpecimensURL());
        }

        if (newFilter)
            selectionCache = new Pair<>(getContainer(), lsids);

        session.setAttribute(SELECTED_SPECIMENS_SESSION_ATTRIB_KEY, selectionCache);
        return selectionCache.getValue();
    }

    private static final String SELECTED_SPECIMENS_SESSION_ATTRIB_KEY = SpecimenController.class.getName() + "/SelectedSpecimens";

    @RequiresPermission(ReadPermission.class)
    public class SelectedSpecimensAction extends QueryViewAction<SpecimenViewTypeForm, SpecimenQueryView>
    {
        private boolean _vialView;
        private ParticipantDataset[] _filterPds = null;

        public SelectedSpecimensAction()
        {
            super(SpecimenViewTypeForm.class);
        }

        @Override
        protected ModelAndView getHtmlView(SpecimenViewTypeForm form, BindException errors) throws Exception
        {
            Study study = getStudyRedirectIfNull();
            Set<Pair<String, String>> ptidVisits = new HashSet<>();
            if (getFilterPds() != null)
            {
                for (ParticipantDataset pd : getFilterPds())
                {
                    if (pd.getSequenceNum() == null)
                    {
                        ptidVisits.add(new Pair<>(pd.getParticipantId(), null));
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
            JspView<SpecimenHeaderBean> header = new JspView<>("/org/labkey/study/view/specimen/specimenHeader.jsp",
                    new SpecimenHeaderBean(getViewContext(), view, ptidVisits));
            return new VBox(header, view);
        }

        private ParticipantDataset[] getFilterPds()
        {
            if (_filterPds == null)
            {
                Set<String> lsids = getSelectionLsids();
                _filterPds = StudyManager.getInstance().getParticipantDatasets(getContainer(), lsids);
            }
            return _filterPds;
        }

        @Override
        protected SpecimenQueryView createQueryView(SpecimenViewTypeForm form, BindException errors, boolean forExport, String dataRegion)
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

        @Override
        public void addNavTrail(NavTree root)
        {
            _addNavTrail(root);
            root.addChild(_vialView ? "Selected Vials" : "Selected Specimens");
        }
    }


    private ActionURL getSpecimensURL()
    {
        return getSpecimensURL(getContainer());
    }


    public static ActionURL getSpecimensURL(Container c)
    {
        return new ActionURL(SpecimensAction.class, c);
    }


    @RequiresPermission(ReadPermission.class)
    @ActionNames("specimens,samples")
    public class SpecimensAction extends QueryViewAction<SpecimenViewTypeForm, SpecimenQueryView>
    {
        private boolean _vialView;

        public SpecimensAction()
        {
            super(SpecimenViewTypeForm.class);
        }

        @Override
        protected SpecimenQueryView createQueryView(SpecimenViewTypeForm form, BindException errors, boolean forExport, String dataRegion)
        {
            Study study = getStudyThrowIfNull();

            _vialView = form.isShowVials();
            CohortFilter cohortFilter = CohortFilterFactory.getFromURL(getContainer(), getUser(), getViewContext().getActionURL(), _vialView ? "SpecimenDetail" : "SpecimenSummary");
            SpecimenQueryView view = getUtils().getSpecimenQueryView(_vialView, forExport, form.getViewModeEnum(), cohortFilter);
            if (SpecimenUtils.isCommentsMode(getContainer(), form.getViewModeEnum()))
                view.setRestrictRecordSelectors(false);
            return view;
        }

        @Override
        protected ModelAndView getHtmlView(SpecimenViewTypeForm form, BindException errors) throws Exception
        {
            StudyImpl study = getStudyRedirectIfNull();

            SpecimenQueryView view = createInitializedQueryView(form, errors, form.getExportType() != null, null);
            SpecimenHeaderBean bean = new SpecimenHeaderBean(getViewContext(), view);
            // Get last selected request
            if (null != study.getLastSpecimenRequest())
                bean.setSelectedRequest(study.getLastSpecimenRequest());
            JspView<SpecimenHeaderBean> header = new JspView<>("/org/labkey/study/view/specimen/specimenHeader.jsp", bean);
            return new VBox(header, view);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addBaseSpecimenNavTrail(root);
            root.addChild(_vialView ? "Vials" : "Grouped Vials");
        }
    }

    public static final class SpecimenHeaderBean
    {
        private final ActionURL _otherViewURL;
        private final ViewContext _viewContext;
        private final boolean _showingVials;
        private final Set<Pair<String, String>> _filteredPtidVisits;

        private Integer _selectedRequest;

        public SpecimenHeaderBean(ViewContext context, SpecimenQueryView view)
        {
            this(context, view, Collections.emptySet());
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
                        // in the other view. If so, we'll add a filter parameter with the same value on the
                        // other view. Otherwise, we'll keep the parameter, but we won't map it to the other view:
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

    public static class SpecimenViewTypeForm extends QueryViewAction.QueryExportForm
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

    private void requiresEditRequestPermissions(SpecimenRequest request)
    {
        if (!SpecimenRequestManager.get().hasEditRequestPermissions(getUser(), request))
            throw new UnauthorizedException();
    }

    public static class ModifySpecimenRequestForm extends IdForm implements HiddenFormInputGenerator
    {
        public enum PARAMS
        {
            specimenIds,
            returnUrl
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

        @Override
        public String getHiddenFormInputs(ViewContext ctx)
        {
            StringBuilder builder = new StringBuilder();
            if (getId() != 0)
                builder.append("<input type=\"hidden\" name=\"id\" value=\"").append(getId()).append("\">\n");
            if (_specimenIds != null)
                builder.append("<input type=\"hidden\" name=\"specimenIds\" value=\"").append(PageFlowUtil.filter(_specimenIds)).append("\">");
            return builder.toString();
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

    public abstract static class SpecimensViewBean
    {
        protected SpecimenQueryView _specimenQueryView;
        protected List<Vial> _vials;

        public SpecimensViewBean(ViewContext context, List<Vial> vials, boolean showHistoryLinks,
                                 boolean showRecordSelectors, boolean disableLowVialIndicators, boolean restrictRecordSelectors)
        {
            _vials = vials;
            if (vials != null && vials.size() > 0)
            {
                _specimenQueryView = SpecimenQueryView.createView(context, vials, SpecimenQueryView.ViewType.VIALS);
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

        public List<Vial> getVials()
        {
            return _vials;
        }
    }

    public class ManageRequestBean extends SpecimensViewBean
    {
        protected SpecimenRequest _specimenRequest;
        protected boolean _requestManager;
        protected boolean _requirementsComplete;
        protected boolean _finalState;
        private List<ActorNotificationRecipientSet> _possibleNotifications;
        protected List<String> _missingSpecimens = null;
        private final Boolean _submissionResult;
        private Location[] _providingLocations;
        private String _returnUrl;

        public ManageRequestBean(ViewContext context, SpecimenRequest specimenRequest, boolean forExport, Boolean submissionResult, String returnUrl)
        {
            super(context, specimenRequest.getVials(), !forExport, !forExport, forExport, false);
            _submissionResult = submissionResult;
            _requestManager = context.getContainer().hasPermission(context.getUser(), ManageRequestsPermission.class);
            _specimenRequest = specimenRequest;
            _finalState = SpecimenRequestManager.get().isInFinalState(_specimenRequest);
            _requirementsComplete = true;
            _missingSpecimens = SpecimenRequestManager.get().getMissingSpecimens(_specimenRequest);
            _returnUrl = returnUrl;
            SpecimenRequestRequirement[] requirements = specimenRequest.getRequirements();
            for (int i = 0; i < requirements.length && _requirementsComplete; i++)
            {
                SpecimenRequestRequirement requirement = requirements[i];
                _requirementsComplete = requirement.isComplete();
            }

            if (_specimenQueryView != null)
            {
                List<DisplayElement> buttons = new ArrayList<>();

                MenuButton exportMenuButton = new MenuButton("Export");
                ActionURL exportExcelURL = context.getActionURL().clone().addParameter("export", "excel");
                ActionURL exportTextURL = context.getActionURL().clone().addParameter("export", "tsv");
                exportMenuButton.addMenuItem("Export all to Excel (.xls)", exportExcelURL);
                exportMenuButton.addMenuItem("Export all to text file (.tsv)", exportTextURL);
                buttons.add(exportMenuButton);
                _specimenQueryView.setShowExportButtons(false);
                _specimenQueryView.getSettings().setAllowChooseView(false);

                if (SpecimenRequestManager.get().hasEditRequestPermissions(context.getUser(), specimenRequest) && !_finalState)
                {
                    ActionButton addButton = new ActionButton(SpecimenMigrationService.get().getOverviewURL(getContainer()), "Specimen Search");
                    ActionButton deleteButton = new ActionButton(RemoveRequestSpecimensAction.class, "Remove Selected");
                    _specimenQueryView.addHiddenFormField("id", "" + specimenRequest.getRowId());
                    buttons.add(addButton);

                    ActionURL importActionURL = new ActionURL(ImportVialIdsAction.class, getContainer());
                    importActionURL.addParameter("id", specimenRequest.getRowId());
                    Button importButton = new Button.ButtonBuilder("Upload Specimen Ids")
                        .href(importActionURL)
                        .submit(false)
                        .build();
                    buttons.add(importButton);
                    buttons.add(deleteButton);
                }
                _specimenQueryView.setButtons(buttons);
            }
        }

        public synchronized List<ActorNotificationRecipientSet> getPossibleNotifications()
        {
            if (_possibleNotifications == null)
                _possibleNotifications = getUtils().getPossibleNotifications(_specimenRequest);
            return _possibleNotifications;
        }

        public SpecimenRequest getSpecimenRequest()
        {
            return _specimenRequest;
        }

        public boolean hasMissingSpecimens()
        {
            return _missingSpecimens != null && !_missingSpecimens.isEmpty();
        }

        public List<String> getMissingSpecimens()
        {
            return _missingSpecimens;
        }

        public SpecimenRequestStatus getStatus()
        {
            return SpecimenRequestManager.get().getRequestStatus(_specimenRequest.getContainer(), _specimenRequest.getStatusId());
        }

        public Location getDestinationSite()
        {
            Integer destinationSiteId = _specimenRequest.getDestinationSiteId();
            if (destinationSiteId != null)
            {
                return LocationManager.get().getLocation(_specimenRequest.getContainer(), destinationSiteId.intValue());
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
                for (Vial vial : _vials)
                {
                    Integer locationId = vial.getCurrentLocation();
                    if (locationId != null)
                        locationSet.add(locationId);
                }
                _providingLocations = new Location[locationSet.size()];
                int i = 0;
                for (Integer locationId : locationSet)
                    _providingLocations[i++] = LocationManager.get().getLocation(getContainer(), locationId);
            }
            return _providingLocations;
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


    @RequiresPermission(ReadPermission.class)
    public class ManageRequestAction extends FormViewAction<ManageRequestForm>
    {
        private int _requestId;

        @Override
        public void validateCommand(ManageRequestForm target, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(ManageRequestForm form, boolean reshow, BindException errors) throws Exception
        {
            SpecimenRequest request = SpecimenRequestManager.get().getRequest(getContainer(), form.getId());
            if (request == null)
                throw new NotFoundException();

            _requestId = request.getRowId();
            ManageRequestBean bean = new ManageRequestBean(getViewContext(), request, form.getExport() != null,
                    form.isSubmissionResult(), form.getReturnUrl());
            if (form.getExport() != null)
            {
                bean.getSpecimenQueryView().getSettings().setMaxRows(Table.ALL_ROWS);   // #34998; exporting specimens in a request should include all of them
                getUtils().writeExportData(bean.getSpecimenQueryView(), form.getExport());
                return null;
            }
            else
            {
                GridView attachmentsGrid = getUtils().getRequestEventAttachmentsGridView(getViewContext().getRequest(), errors, _requestId);
                SpecimenQueryView queryView = bean.getSpecimenQueryView();
                if (null != queryView)
                    queryView.setTitle("Associated Specimens");
                HBox hbox = new HBox(new JspView<>("/org/labkey/study/view/specimen/manageRequest.jsp", bean), attachmentsGrid);
                hbox.setTableWidth("");
                return new VBox(hbox, queryView);
            }
        }

        @Override
        public boolean handlePost(ManageRequestForm form, BindException errors) throws Exception
        {
            if (!getContainer().hasPermission(getUser(), ManageRequestsPermission.class))
                throw new UnauthorizedException("You do not have permissions to create new specimen request requirements!");

            SpecimenRequest request = SpecimenRequestManager.get().getRequest(getContainer(), form.getId());
            if (request == null)
                throw new NotFoundException();

            if (form.getNewActor() != null && form.getNewActor() > 0)
            {
                SpecimenRequestActor actor = SpecimenRequestRequirementProvider.get().getActor(getContainer(), form.getNewActor());
                if (actor != null)
                {
                    // an actor is valid if a site has been provided for a per-site actor, or if no site
                    // has been provided for a non-site-specific actor. The UI should enforce this already,
                    // so this is just a backup check.
                    boolean validActor = (actor.isPerSite() && form.getNewSite() != null && form.getNewSite() > 0) ||
                            (!actor.isPerSite() && (form.getNewSite() == null || form.getNewSite() <= 0));
                    if (validActor)
                    {
                        SpecimenRequestRequirement requirement = new SpecimenRequestRequirement();
                        requirement.setContainer(getContainer());
                        if (form.getNewSite() != null && form.getNewSite() > 0)
                            requirement.setSiteId(form.getNewSite());
                        requirement.setActorId(form.getNewActor());
                        requirement.setDescription(form.getNewDescription());
                        requirement.setRequestId(request.getRowId());
                        SpecimenRequestManager.get().createRequestRequirement(getUser(), requirement, true, true);
                    }
                }
            }
            return true;
        }

        @Override
        public ActionURL getSuccessURL(ManageRequestForm manageRequestForm)
        {
            return getManageRequestURL(manageRequestForm.getId(), manageRequestForm.getReturnUrl());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addSpecimenRequestNavTrail(root, _requestId);
        }
    }

    // Additional permission checking takes place before handlePost()
    @RequiresPermission(ReadPermission.class)
    public class RemoveRequestSpecimensAction extends FormHandlerAction<ModifySpecimenRequestForm>
    {
        @Override
        public void validateCommand(ModifySpecimenRequestForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(ModifySpecimenRequestForm form, BindException errors) throws Exception
        {
            SpecimenRequest request = SpecimenRequestManager.get().getRequest(getContainer(), form.getId());
            requiresEditRequestPermissions(request);
            long[] ids = toLongArray(DataRegionSelection.getSelected(getViewContext(), true));
            List<Long> specimenIds = new ArrayList<>();
            for (long id : ids)
                specimenIds.add(id);
            try
            {
                SpecimenRequestManager.get().deleteRequestSpecimenMappings(getUser(), request, specimenIds, true);
            }
            catch (RequestabilityManager.InvalidRuleException e)
            {
                errors.reject(ERROR_MSG, "The specimens could not be removed because a requestability rule is configured incorrectly. " +
                            "Please report this problem to an administrator. Error details: "  + e.getMessage());
                return false;
            }
            return true;
        }

        @Override
        public ActionURL getSuccessURL(ModifySpecimenRequestForm addToSpecimenRequestForm)
        {
            return getManageRequestURL(addToSpecimenRequestForm.getId(), addToSpecimenRequestForm.getReturnUrl());
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

    @RequiresPermission(RequestSpecimensPermission.class)
    public class ManageRequestStatusAction extends FormViewAction<ManageRequestStatusForm>
    {
        private SpecimenRequest _specimenRequest;

        @Override
        public void validateCommand(ManageRequestStatusForm target, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(ManageRequestStatusForm form, boolean reshow, BindException errors)
        {
            _specimenRequest = SpecimenRequestManager.get().getRequest(getContainer(), form.getId());
            if (_specimenRequest == null)
                throw new NotFoundException();

            return new JspView<>("/org/labkey/study/view/specimen/manageRequestStatus.jsp",
                    new ManageRequestBean(getViewContext(), _specimenRequest, false, null, null), errors);
        }

        @Override
        public boolean handlePost(final ManageRequestStatusForm form, BindException errors) throws Exception
        {
            _specimenRequest = SpecimenRequestManager.get().getRequest(getContainer(), form.getId());
            if (_specimenRequest == null)
                throw new NotFoundException();

            boolean statusChanged = form.getStatus() != _specimenRequest.getStatusId();
            boolean detailsChanged = !nullSafeEqual(form.getRequestDescription(), _specimenRequest.getComments());

            List<AttachmentFile> files = getAttachmentFileList();
            boolean hasAttachments = !files.isEmpty();

            boolean hasComments = form.getComments() != null && form.getComments().length() > 0;
            if (statusChanged || detailsChanged || hasComments || hasAttachments)
            {
                RequestEventType changeType;
                String comment = "";
                String eventSummary;
                if (statusChanged || detailsChanged)
                {
                    if (statusChanged)
                    {
                        SpecimenRequestStatus prevStatus = SpecimenRequestManager.get().getRequestStatus(getContainer(), _specimenRequest.getStatusId());
                        SpecimenRequestStatus newStatus = SpecimenRequestManager.get().getRequestStatus(getContainer(), form.getStatus());
                        comment += "Status changed from \"" + (prevStatus != null ? prevStatus.getLabel() : "N/A") + "\" to \"" +
                                (newStatus != null ? newStatus.getLabel() : "N/A") + "\"\n";
                    }
                    if (detailsChanged)
                    {
                        String prevDetails = _specimenRequest.getComments();
                        String newDetails = form.getRequestDescription();
                        comment += "Description changed from \"" + (prevDetails != null ? prevDetails : "N/A") + "\" to \"" +
                                (newDetails != null ? newDetails : "N/A") + "\"\n";
                    }
                    eventSummary = comment;
                    if (hasComments)
                        comment += form.getComments();
                    _specimenRequest = _specimenRequest.createMutable();
                    _specimenRequest.setStatusId(form.getStatus());
                    _specimenRequest.setComments(form.getRequestDescription());
                    _specimenRequest.setModified(new Date());
                    try
                    {
                        SpecimenRequestManager.get().updateRequest(getUser(), _specimenRequest);
                    }
                    catch (RequestabilityManager.InvalidRuleException e)
                    {
                        errors.reject(ERROR_MSG, "The request could not be updated because a requestability rule is configured incorrectly. " +
                                    "Please report this problem to an administrator. Error details: "  + e.getMessage());
                        return false;
                    }
                    changeType = RequestEventType.REQUEST_STATUS_CHANGED;
                }
                else
                {
                    changeType = RequestEventType.COMMENT_ADDED;
                    comment = form.getComments();
                    eventSummary = "Comments added.";
                }

                SpecimenRequestEvent event;
                try
                {
                    event = SpecimenRequestManager.get().createRequestEvent(getUser(), _specimenRequest, changeType, comment, files);
                }
                catch (Exception e)
                {
                    errors.reject(ERROR_MSG, "The request could not be updated because of an unexpected error. " +
                            "Please report this problem to an administrator. Error details: "  + e.getMessage());
                    return false;
                }
                try
                {
                    List<? extends NotificationRecipientSet> recipients = getUtils().getNotifications(_specimenRequest, form.getNotificationIdPairs());
                    DefaultRequestNotification notification = new DefaultRequestNotification(_specimenRequest, recipients,
                            eventSummary, event, form.getComments(), null, getViewContext());
                    getUtils().sendNotification(notification, form.isEmailInactiveUsers(), errors);
                }
                catch (ConfigurationException | IOException e)
                {
                    errors.reject(ERROR_MSG, "The request was updated successfully, but the notification failed: " +  e.getMessage());
                    return false;
                }
            }

            return !errors.hasErrors();
        }

        @Override
        public ActionURL getSuccessURL(ManageRequestStatusForm manageRequestForm)
        {
            return getManageRequestURL(_specimenRequest.getRowId());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addSpecimenRequestNavTrail(root, _specimenRequest.getRowId());
            root.addChild("Update Request");
        }
    }

    public static List<Vial> getSpecimensFromRowIds(long[] requestedSampleIds, Container container, User user)
    {
        List<Vial> requestedVials = null;

        if (requestedSampleIds != null)
        {
            List<Vial> vials = new ArrayList<>();
            for (long requestedSampleId : requestedSampleIds)
            {
                Vial current = SpecimenManagerNew.get().getVial(container, user, requestedSampleId);
                if (current != null)
                    vials.add(current);
            }
            requestedVials = vials;
        }

        return requestedVials;
    }

    public static List<Vial> getSpecimensFromRowIds(Collection<String> ids, Container container, User user)
    {
        return getSpecimensFromRowIds(SpecimenController.toLongArray(ids), container, user);
    }

    public List<Vial> getSpecimensFromPost(boolean fromGroupedView, boolean onlyAvailable)
    {
        Set<String> formValues = null;
        if ("POST".equalsIgnoreCase(getViewContext().getRequest().getMethod()))
            formValues = DataRegionSelection.getSelected(getViewContext(), true);

        if (formValues == null || formValues.isEmpty())
            return null;

        List<Vial> selectedVials;
        if (fromGroupedView)
        {
            Map<String, List<Vial>> keyToVialMap =
                    SpecimenManagerNew.get().getVialsForSpecimenHashes(getContainer(), getUser(),  formValues, onlyAvailable);
            List<Vial> vials = new ArrayList<>();
            for (List<Vial> vialList : keyToVialMap.values())
                vials.addAll(vialList);
            selectedVials = new ArrayList<>(vials);
        }
        else
            selectedVials = getSpecimensFromRowIds(formValues, getContainer(), getUser());
        return selectedVials;
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

    @RequiresPermission(ManageRequestsPermission.class)
    public class DeleteRequirementAction extends FormHandlerAction<RequirementForm>
    {
        @Override
        public void validateCommand(RequirementForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(RequirementForm form, BindException errors) throws Exception
        {
            SpecimenRequestRequirement requirement =
                    SpecimenRequestRequirementProvider.get().getRequirement(getContainer(), form.getRequirementId());
            if (requirement.getRequestId() == form.getId())
            {
                SpecimenRequestManager.get().deleteRequestRequirement(getUser(), requirement);
                return true;
            }

            return false;
        }

        @Override
        public ActionURL getSuccessURL(RequirementForm requirementForm)
        {
            return getManageRequestURL(requirementForm.getId());
        }
    }

    @RequiresPermission(ManageRequestsPermission.class)
    public class DeleteMissingRequestSpecimensAction extends FormHandlerAction<IdForm>
    {
        @Override
        public void validateCommand(IdForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(IdForm form, BindException errors) throws Exception
        {
            SpecimenRequest request = SpecimenRequestManager.get().getRequest(getContainer(), form.getId());
            if (request == null)
                throw new NotFoundException("Specimen request " + form.getId() + " does not exist.");

            SpecimenRequestManager.get().deleteMissingSpecimens(request);
            return true;
        }

        @Override
        public ActionURL getSuccessURL(IdForm requirementForm)
        {
            return getManageRequestURL(requirementForm.getId());
        }
    }

    public class ManageRequirementBean
    {
        private final GridView _historyView;
        private final SpecimenRequestRequirement _requirement;
        private final boolean _requestManager;
        private final List<ActorNotificationRecipientSet> _possibleNotifications;
        private final boolean _finalState;

        public ManageRequirementBean(ViewContext context, SpecimenRequest request, SpecimenRequestRequirement requirement)
        {
            _requirement = requirement;
            _possibleNotifications = getUtils().getPossibleNotifications(request);
            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("RequestId"), requirement.getRequestId());
            filter.addCondition(FieldKey.fromParts("RequirementId"), requirement.getRowId());
            _requestManager = context.getContainer().hasPermission(context.getUser(), ManageRequestsPermission.class);
            _historyView = getUtils().getRequestEventGridView(context.getRequest(), null, filter);
            _finalState = SpecimenRequestManager.get().isInFinalState(request);
        }
        
        public boolean isDefaultNotification(ActorNotificationRecipientSet notification)
        {
            RequestNotificationSettings settings = SettingsManager.get().getRequestNotificationSettings(getContainer());
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

        public SpecimenRequestRequirement getRequirement()
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

    @RequiresPermission(ReadPermission.class)
    public class ManageRequirementAction extends FormViewAction<ManageRequirementForm>
    {
        private SpecimenRequest _specimenRequest;

        @Override
        public void validateCommand(ManageRequirementForm target, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(ManageRequirementForm form, boolean reshow, BindException errors)
        {
            _specimenRequest = SpecimenRequestManager.get().getRequest(getContainer(), form.getId());
            final SpecimenRequestRequirement requirement =
                    SpecimenRequestRequirementProvider.get().getRequirement(getContainer(), form.getRequirementId());
            if (_specimenRequest == null || requirement == null || requirement.getRequestId() != form.getId())
                throw new NotFoundException();

            return new JspView<>("/org/labkey/study/view/specimen/manageRequirement.jsp",
                    new ManageRequirementBean(getViewContext(), _specimenRequest, requirement), errors);
        }

        @Override
        public boolean handlePost(final ManageRequirementForm form, BindException errors) throws Exception
        {
            if (!getContainer().hasPermission(getUser(), ManageRequestsPermission.class))
                throw new UnauthorizedException("You do not have permission to update requirements!");

            _specimenRequest = SpecimenRequestManager.get().getRequest(getContainer(), form.getId());
            final SpecimenRequestRequirement requirement =
                    SpecimenRequestRequirementProvider.get().getRequirement(getContainer(), form.getRequirementId());
            if (_specimenRequest == null || requirement == null || requirement.getRequestId() != form.getId())
                throw new NotFoundException();

            List<AttachmentFile> files = getAttachmentFileList();
            RequestEventType eventType;
            StringBuilder comment = new StringBuilder();
            comment.append(requirement.getRequirementSummary());
            String eventSummary;
            if (form.isComplete() != requirement.isComplete())
            {
                SpecimenRequestRequirement clone = requirement.createMutable();
                clone.setComplete(form.isComplete());
                SpecimenRequestManager.get().updateRequestRequirement(getUser(), clone);
                eventType = RequestEventType.REQUEST_STATUS_CHANGED;
                comment.append("\nStatus changed to ").append(form.isComplete() ? "complete" : "incomplete");
                eventSummary = comment.toString();
            }
            else
            {
                eventType = RequestEventType.COMMENT_ADDED;
                eventSummary = "Comment added.";
            }

            if (form.getComment() != null && form.getComment().length() > 0)
                comment.append("\n").append(form.getComment());

            SpecimenRequestEvent event;
            try
            {
                event = SpecimenRequestManager.get().createRequestEvent(getUser(), requirement, eventType, comment.toString(), files);
            }
            catch (Exception e)
            {
                errors.reject(ERROR_MSG, "The request could not be updated because of an unexpected error. " +
                        "Please report this problem to an administrator. Error details: "  + e.getMessage());
                return false;
            }
            try
            {

                List<? extends NotificationRecipientSet> recipients = getUtils().getNotifications(_specimenRequest, form.getNotificationIdPairs());
                DefaultRequestNotification notification = new DefaultRequestNotification(_specimenRequest, recipients,
                        eventSummary, event, form.getComment(), requirement, getViewContext());
                getUtils().sendNotification(notification, form.isEmailInactiveUsers(), errors);
            }
            catch (ConfigurationException | IOException e)
            {
                errors.reject(ERROR_MSG, "The request was updated successfully, but the notification failed: " +  e.getMessage());
                return false;
            }

            return !errors.hasErrors();
        }

        @Override
        public ActionURL getSuccessURL(ManageRequirementForm manageRequirementForm)
        {
            return getManageRequestURL(_specimenRequest.getRowId());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addSpecimenRequestNavTrail(root, _specimenRequest.getRowId());
            root.addChild("Manage Requirement");
        }
    }


    @RequiresPermission(ReadPermission.class)
    public class RequestHistoryAction extends SimpleViewAction<IdForm>
    {
        private int _requestId;

        @Override
        public ModelAndView getView(IdForm form, BindException errors)
        {
            _requestId = form.getId();
            HtmlView header = new HtmlView(new LinkBuilder("View Request").href(new ActionURL(ManageRequestAction.class, getContainer()).addParameter("id", form.getId())));
            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("RequestId"), form.getId());
            GridView historyGrid = getUtils().getRequestEventGridView(getViewContext().getRequest(), errors, filter);
            return new VBox(header, historyGrid);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addSpecimenRequestNavTrail(root, _requestId);
            root.addChild("Request History");
        }
    }

    private ActionURL getManageStudyURL()
    {
        return urlProvider(StudyUrls.class).getManageStudyURL(getContainer());
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

    @RequiresPermission(RequestSpecimensPermission.class)
    public class SubmitRequestAction extends FormHandlerAction<IdForm>
    {
        @Override
        public void validateCommand(IdForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(IdForm form, BindException errors) throws Exception
        {
            if (!SettingsManager.get().isSpecimenShoppingCartEnabled(getContainer()))
                throw new UnauthorizedException();

            SpecimenRequest request = SpecimenRequestManager.get().getRequest(getContainer(), form.getId());
            requiresEditRequestPermissions(request);
            List<Vial> vials = request.getVials();
            if (!vials.isEmpty() || SpecimenService.get().getRequestCustomizer().allowEmptyRequests())
            {
                SpecimenRequestStatus newStatus = SpecimenRequestManager.get().getInitialRequestStatus(getContainer(), getUser(), true);
                request = request.createMutable();
                request.setStatusId(newStatus.getRowId());
                try
                {
                    SpecimenRequestManager.get().updateRequest(getUser(), request);
                    SpecimenRequestManager.get().createRequestEvent(getUser(), request,
                            RequestEventType.REQUEST_STATUS_CHANGED, "Request submitted for processing.", null);
                    if (SettingsManager.get().isSpecimenShoppingCartEnabled(getContainer()))
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
                                "Please report this problem to an administrator. Error details: "  + e.getMessage());
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

            return !errors.hasErrors();
        }

        @Override
        public ActionURL getSuccessURL(IdForm idForm)
        {
            ActionURL successURL = getManageRequestURL(idForm.getId());
            successURL.addParameter(ManageRequestForm.PARAMS.submissionResult, Boolean.TRUE.toString());
            return successURL;
        }
    }

    @RequiresPermission(RequestSpecimensPermission.class)
    public class DeleteRequestAction extends FormHandlerAction<IdForm>
    {
        @Override
        public void validateCommand(IdForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(IdForm form, BindException errors) throws Exception
        {
            SpecimenRequest request = SpecimenRequestManager.get().getRequest(getContainer(), form.getId());
            requiresEditRequestPermissions(request);
            SpecimenRequestStatus cartStatus = SpecimenRequestManager.get().getRequestShoppingCartStatus(getContainer(), getUser());
            if (request.getStatusId() != cartStatus.getRowId())
                throw new UnauthorizedException();

            try
            {
                SpecimenRequestManager.get().deleteRequest(getUser(), request);
            }
            catch (RequestabilityManager.InvalidRuleException e)
            {
                errors.reject(ERROR_MSG, "The request could not be canceled because a requestability rule is configured incorrectly. " +
                        "Please report this problem to an administrator. Error details: "  + e.getMessage());
            }
            return true;
        }

        @Override
        public ActionURL getSuccessURL(IdForm form)
        {
            return SpecimenMigrationService.get().getViewRequestsURL(getContainer());
        }
    }

    @RequiresPermission(ManageRequestsPermission.class)
    public class EmailLabSpecimenListsAction extends FormHandlerAction<EmailSpecimenListForm>
    {
        @Override
        public void validateCommand(EmailSpecimenListForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(EmailSpecimenListForm form, BindException errors) throws Exception
        {
            final SpecimenRequest request = SpecimenRequestManager.get().getRequest(getContainer(), form.getId());
            if (request == null)
                throw new NotFoundException();

            LocationImpl receivingLocation = LocationManager.get().getLocation(getContainer(), request.getDestinationSiteId());
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
                    LocationImpl originatingOrProvidingLocation = LocationManager.get().getLocation(getContainer(), ids[0]);
                    SpecimenRequestActor notifyActor = SpecimenRequestRequirementProvider.get().getActor(getContainer(), ids[1]);
                    LocationImpl notifyLocation = null;
                    if (notifyActor.isPerSite() && ids[2] >= 0)
                        notifyLocation = LocationManager.get().getLocation(getContainer(), ids[2]);
                    List<ActorNotificationRecipientSet> emailRecipients = notifications.computeIfAbsent(originatingOrProvidingLocation, k -> new ArrayList<>());
                    emailRecipients.add(new ActorNotificationRecipientSet(notifyActor, notifyLocation));
                }


                for (final LocationImpl originatingOrProvidingLocation : notifications.keySet())
                {
                    List<AttachmentFile> formFiles = getAttachmentFileList();
                    if (form.isSendTsv())
                    {
                        try (TSVGridWriter tsvWriter = getUtils().getSpecimenListTsvWriter(request, originatingOrProvidingLocation, receivingLocation, type))
                        {
                            StringBuilder tsvBuilder = new StringBuilder();
                            tsvWriter.write(tsvBuilder);
                            formFiles.add(new ByteArrayAttachmentFile(tsvWriter.getFilenamePrefix() + ".tsv", tsvBuilder.toString().getBytes(Charsets.UTF_8), TSVWriter.DELIM.TAB.contentType));
                        }
                    }

                    if (form.isSendXls())
                    {
                        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream(); OutputStream ostream = new BufferedOutputStream(byteStream); ExcelWriter xlsWriter = getUtils().getSpecimenListXlsWriter(request, originatingOrProvidingLocation, receivingLocation, type))
                        {
                            xlsWriter.write(ostream);
                            ostream.flush();
                            formFiles.add(new ByteArrayAttachmentFile(xlsWriter.getFilenamePrefix() + "." + xlsWriter.getDocumentType().name(), byteStream.toByteArray(), xlsWriter.getDocumentType().getMimeType()));
                        }
                    }

                    final StringBuilder content = new StringBuilder();
                    if (form.getComments() != null)
                        content.append(form.getComments());

                    String header = type.getDisplay() + " location notification of specimen shipment to " + receivingLocation.getDisplayName();
                    try
                    {
                        SpecimenRequestEvent event = SpecimenRequestManager.get().createRequestEvent(getUser(), request,
                            RequestEventType.SPECIMEN_LIST_GENERATED, header + "\n" + content.toString(), formFiles);

                        final Container container = getContainer();
                        final User user = getUser();
                        List<ActorNotificationRecipientSet> emailRecipients = notifications.get(originatingOrProvidingLocation);
                            DefaultRequestNotification notification = new DefaultRequestNotification(request, emailRecipients,
                                header, event, content.toString(), null, getViewContext())
                        {
                            @Override
                            protected List<Vial> getSpecimenList()
                            {
                                SimpleFilter filter = getUtils().getSpecimenListFilter(getSpecimenRequest(), originatingOrProvidingLocation, type);
                                return SpecimenManagerNew.get().getVials(container, user, filter);
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

            return !errors.hasErrors();
        }

        @Override
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

    @RequiresPermission(ManageRequestsPermission.class)
    public class DownloadSpecimenListAction extends SimpleViewAction<ExportSiteForm>
    {
        @Override
        public ModelAndView getView(ExportSiteForm form, BindException errors) throws Exception
        {
            SpecimenRequest specimenRequest = SpecimenRequestManager.get().getRequest(getContainer(), form.getId());
            LocationImpl sourceLocation = LocationManager.get().getLocation(getContainer(), form.getSourceSiteId());
            LocationImpl destLocation = LocationManager.get().getLocation(getContainer(), form.getDestSiteId());
            if (specimenRequest == null || sourceLocation == null || destLocation == null)
                throw new NotFoundException();

            LabSpecimenListsBean.Type type = LabSpecimenListsBean.Type.valueOf(form.getListType());
            if (null != form.getExport())
            {
                if (EXPORT_TSV.equals(form.getExport()))
                {
                    try (TSVGridWriter writer = getUtils().getSpecimenListTsvWriter(specimenRequest, sourceLocation, destLocation, type))
                    {
                        writer.write(getViewContext().getResponse());
                    }
                }
                else if (EXPORT_XLS.equals(form.getExport()))
                {
                    try (ExcelWriter writer = getUtils().getSpecimenListXlsWriter(specimenRequest, sourceLocation, destLocation, type))
                    {
                        writer.write(getViewContext().getResponse());
                    }
                }
            }
            return null;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            throw new UnsupportedOperationException("Not Yet Implemented");
        }
    }

    private static final String EXPORT_TSV = "tsv";
    private static final String EXPORT_XLS = "xls";

    @RequiresPermission(ManageRequestsPermission.class)
    public class LabSpecimenListsAction extends SimpleViewAction<LabSpecimenListsForm>
    {
        private int _requestId;
        private LabSpecimenListsBean.Type _type;

        @Override
        public ModelAndView getView(LabSpecimenListsForm form, BindException errors)
        {
            SpecimenRequest request = SpecimenRequestManager.get().getRequest(getContainer(), form.getId());
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
            return new JspView<>("/org/labkey/study/view/specimen/labSpecimenLists.jsp", new LabSpecimenListsBean(getUtils(), request, _type));
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addSpecimenRequestNavTrail(root, _requestId);
            root.addChild(_type.getDisplay() + " Lab Vial Lists");
        }
    }

    public static class LabSpecimenListsForm extends IdForm
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
        public enum Type
        {
            PROVIDING("Providing"),
            ORIGINATING("Originating");

            private String _display;
            Type(String display)
            {
                _display = display;
            }

            public String getDisplay()
            {
                return _display;
            }
        }

        private SpecimenRequest _specimenRequest;
        private Map<Integer, List<Vial>> _specimensBySiteId;
        private List<ActorNotificationRecipientSet> _possibleNotifications;
        private Type _type;
        private boolean _requirementsComplete;
        private SpecimenUtils _utils;

        public LabSpecimenListsBean(SpecimenUtils utils, SpecimenRequest specimenRequest, LabSpecimenListsBean.Type type)
        {
            _specimenRequest = specimenRequest;
            _utils = utils;
            _type = type;
            _requirementsComplete = true;
            for (int i = 0; i < specimenRequest.getRequirements().length && _requirementsComplete; i++)
            {
                SpecimenRequestRequirement requirement = specimenRequest.getRequirements()[i];
                _requirementsComplete = requirement.isComplete();
            }
        }

        public SpecimenRequest getSpecimenRequest()
        {
            return _specimenRequest;
        }

        private synchronized Map<Integer, List<Vial>> getSpecimensBySiteId()
        {
            if (_specimensBySiteId == null)
            {
                _specimensBySiteId = new HashMap<>();
                List<Vial> vials = _specimenRequest.getVials();
                for (Vial vial : vials)
                {
                    LocationImpl location;
                    if (_type == LabSpecimenListsBean.Type.ORIGINATING)
                        location = LocationManager.get().getOriginatingLocation(vial);
                    else
                        location = LocationManager.get().getCurrentLocation(vial);
                    if (location != null)
                    {
                        List<Vial> current = _specimensBySiteId.get(location.getRowId());
                        if (current == null)
                        {
                            current = new ArrayList<>();
                            _specimensBySiteId.put(location.getRowId(), current);
                        }
                        current.add(vial);
                    }
                }
            }
            return _specimensBySiteId;
        }

        public synchronized List<ActorNotificationRecipientSet> getPossibleNotifications()
        {
            if (_possibleNotifications == null)
                _possibleNotifications = _utils.getPossibleNotifications(_specimenRequest);
            return _possibleNotifications;
        }

        public Set<LocationImpl> getLabs()
        {
            Map<Integer, List<Vial>> siteIdToSpecimens = getSpecimensBySiteId();
            Set<LocationImpl> locations = new HashSet<>(siteIdToSpecimens.size());
            for (Integer locationId : siteIdToSpecimens.keySet())
                locations.add(LocationManager.get().getLocation(_specimenRequest.getContainer(), locationId));
            return locations;
        }

        public List<Vial> getSpecimens(LocationImpl location)
        {
            Map<Integer, List<Vial>> siteSpecimenLists = getSpecimensBySiteId();
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
    }

    private enum CommentsConflictResolution
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
        private int _copySpecimenId;
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
            return _copySpecimenId;
        }

        public void setCopySampleId(int copySpecimenId)
        {
            _copySpecimenId = copySpecimenId;
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

    public static class UpdateSpecimenCommentsBean extends SpecimensViewBean
    {
        private final String _referrer;
        private final String _currentComment;
        private final boolean _mixedComments;
        private final boolean _currentFlagState;
        private final boolean _mixedFlagState;
        private final Map<String, Map<String, Long>> _participantVisitMap;

        public UpdateSpecimenCommentsBean(ViewContext context, List<Vial> vials, String referrer)
        {
            super(context, vials, false, false, true, true);
            _referrer = referrer;
            Map<Vial, SpecimenComment> currentComments = SpecimenManager.get().getSpecimenComments(vials);
            boolean mixedComments = false;
            boolean mixedFlagState = false;
            SpecimenComment prevComment = currentComments.get(vials.get(0));

            for (int i = 1; i < vials.size() && (!mixedFlagState || !mixedComments); i++)
            {
                SpecimenComment comment = currentComments.get(vials.get(i));

                // a missing comment indicates a 'false' for history conflict:
                boolean currentFlagState = comment != null && comment.isQualityControlFlag();
                boolean previousFlagState = prevComment != null && prevComment.isQualityControlFlag();
                mixedFlagState = mixedFlagState || currentFlagState != previousFlagState;
                String currentCommentString = comment != null ? comment.getComment() : null;
                String previousCommentString = prevComment != null ? prevComment.getComment() : null;
                mixedComments = mixedComments || !Objects.equals(previousCommentString, currentCommentString);
                prevComment = comment;
            }

            _currentComment = !mixedComments && prevComment != null ? prevComment.getComment() : null;
            _currentFlagState = mixedFlagState || (prevComment != null && prevComment.isQualityControlFlag());
            _mixedComments = mixedComments;
            _mixedFlagState = mixedFlagState;
            _participantVisitMap = generateParticipantVisitMap(vials, BaseStudyController.getStudyRedirectIfNull(context.getContainer()));
        }

        protected Map<String, Map<String, Long>> generateParticipantVisitMap(List<Vial> vials, Study study)
        {
            Map<String, Map<String, Long>> pvMap = new TreeMap<>();

            if (TimepointType.CONTINUOUS == study.getTimepointType())
                return Collections.emptyMap();

            boolean isDateStudy = TimepointType.DATE == study.getTimepointType();
            Date startDate = isDateStudy ? study.getStartDate() : new Date();

            for (Vial vial : vials)
            {
                Double visit;
                if (isDateStudy)
                    if (null != vial.getDrawTimestamp())
                        visit = Double.valueOf((vial.getDrawTimestamp().getTime() - startDate.getTime()) / DateUtils.MILLIS_PER_DAY);
                    else
                        visit = null;
                else
                    visit = vial.getVisitValue();

                if (visit != null)
                {
                    String ptid = vial.getPtid();
                    Visit v = StudyManager.getInstance().getVisitForSequence(study, visit);
                    if (ptid != null && v != null)
                    {
                        if (!pvMap.containsKey(ptid))
                            pvMap.put(ptid, new HashMap<>());
                        pvMap.get(ptid).put(v.getDisplayString(), vial.getRowId());
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

    @RequiresPermission(SetSpecimenCommentsPermission.class)
    public class ClearCommentsAction extends FormHandlerAction<UpdateSpecimenCommentsForm>
    {
        @Override
        public void validateCommand(UpdateSpecimenCommentsForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(UpdateSpecimenCommentsForm updateSpecimenCommentsForm, BindException errors) throws Exception
        {
            List<Vial> selectedVials = getSpecimensFromPost(updateSpecimenCommentsForm.isFromGroupedView(), false);
            if (selectedVials != null)
            {
                for (Vial vial : selectedVials)
                {
                    // use the currently saved history conflict state; if it's been forced before, this will prevent it
                    // from being cleared.
                    SpecimenComment comment = SpecimenManager.get().getSpecimenCommentForVial(vial);
                    if (comment != null)
                    {
                        SpecimenManager.get().setSpecimenComment(getUser(), vial, null,
                                comment.isQualityControlFlag(), comment.isQualityControlFlagForced());
                    }
                }
            }
            return true;
        }

        @Override
        public ActionURL getSuccessURL(UpdateSpecimenCommentsForm updateSpecimenCommentsForm)
        {
            return new ActionURL(updateSpecimenCommentsForm.getReferrer());
        }
    }

    @RequiresPermission(SetSpecimenCommentsPermission.class)
    public class UpdateCommentsAction extends FormViewAction<UpdateParticipantCommentsForm>
    {
        private ActionURL _successUrl;

        @Override
        public void validateCommand(UpdateParticipantCommentsForm specimenCommentsForm, Errors errors)
        {
            if (specimenCommentsForm.isSaveCommentsPost() &&
                    (specimenCommentsForm.getRowId() == null ||
                     specimenCommentsForm.getRowId().length == 0))
            {
                errors.reject(null, "No vials were selected.");
            }
        }

        @Override
        public ModelAndView getView(UpdateParticipantCommentsForm specimenCommentsForm, boolean reshow, BindException errors)
        {
            List<Vial> selectedVials = getSpecimensFromPost(specimenCommentsForm.isFromGroupedView(), false);

            if (selectedVials == null || selectedVials.size() == 0)
            {
                // are the vial IDs on the URL?
                int[] rowId = specimenCommentsForm.getRowId();
                if (rowId != null && rowId.length > 0)
                {
                    try
                    {
                        List<Vial> vials = SpecimenManagerNew.get().getVials(getContainer(), getUser(), rowId);
                        selectedVials = new ArrayList<>(vials);
                    }
                    catch (SpecimenRequestException e)
                    {
                        errors.reject(ERROR_MSG, e.getMessage());
                    }
                }
                if (selectedVials == null || selectedVials.size() == 0)
                    return new HtmlView("No vials selected. " + PageFlowUtil.link("back").href("javascript:back()"));
            }

            return new JspView<>("/org/labkey/study/view/specimen/updateComments.jsp",
                    new UpdateSpecimenCommentsBean(getViewContext(), selectedVials, specimenCommentsForm.getReferrer()), errors);
        }

        @Override
        public boolean handlePost(UpdateParticipantCommentsForm commentsForm, BindException errors) throws Exception
        {
            if (commentsForm.getRowId() == null)
                return false;

            User user = getUser();
            Container container = getContainer();
            List<Vial> vials = new ArrayList<>();
            for (int rowId : commentsForm.getRowId())
                vials.add(SpecimenManagerNew.get().getVial(container, user, rowId));

            Map<Vial, SpecimenComment> currentComments = SpecimenManager.get().getSpecimenComments(vials);

            // copying or moving vial comments to participant level comments
            if (commentsForm.isCopyToParticipant())
            {
                if (commentsForm.getCopySampleId() != -1)
                {
                    Vial vial = SpecimenManagerNew.get().getVial(container, user, commentsForm.getCopySampleId());
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
                    for (Vial vial : vials)
                    {
                        SpecimenComment comment = SpecimenManager.get().getSpecimenCommentForVial(vial);
                        if (comment != null)
                        {
                            _successUrl.addParameter(ParticipantCommentForm.params.vialCommentsToClear, vial.getRowId());
                        }
                    }
                }
                return true;
            }

            for (Vial vial : vials)
            {
                SpecimenComment previousComment = currentComments.get(vial);

                boolean newConflictState;
                boolean newForceState;
                if (commentsForm.isQualityControlFlag() != null && SettingsManager.get().getDisplaySettings(container).isEnableManualQCFlagging())
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
                    SpecimenManager.get().setSpecimenComment(getUser(), vial, commentsForm.getComments(),
                            newConflictState, newForceState);
                }
                else if (commentsForm.getConflictResolveEnum() == CommentsConflictResolution.APPEND)
                {
                    SpecimenManager.get().setSpecimenComment(getUser(), vial, previousComment.getComment() + "\n" + commentsForm.getComments(),
                            newConflictState, newForceState);
                }
                // If we haven't updated by now, our user has selected CommentsConflictResolution.SKIP and previousComments is non-null
                // so we no-op for this vial.
            }
            _successUrl = new ActionURL(commentsForm.getReferrer());
            return true;
        }

        @Override
        public ActionURL getSuccessURL(UpdateParticipantCommentsForm commentsForm)
        {
            return _successUrl;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addBaseSpecimenNavTrail(root);
            root.addChild("Set vial comments");
        }
    }

    @RequiresPermission(ManageNewRequestFormPermission.class)
    public class HandleUpdateRequestInputsAction extends FormHandlerAction<ManageRequestInputsForm>
    {
        @Override
        public void validateCommand(ManageRequestInputsForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(ManageRequestInputsForm form, BindException errors) throws Exception
        {
            SpecimenRequestInput[] inputs = new SpecimenRequestInput[form.getTitle().length];
            for (int i = 0; i < form.getTitle().length; i++)
            {
                String title = form.getTitle()[i];
                String helpText = form.getHelpText()[i];
                inputs[i] = new SpecimenRequestInput(title, helpText, i);
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
            SpecimenRequestManager.get().saveNewSpecimenRequestInputs(getContainer(), inputs);
            return true;
        }

        @Override
        public ActionURL getSuccessURL(ManageRequestInputsForm manageRequestInputsForm)
        {
            return getManageStudyURL();
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

    @RequiresPermission(ManageNewRequestFormPermission.class)
    public class ManageRequestInputsAction extends SimpleViewAction<PipelineForm>
    {
        @Override
        public ModelAndView getView(PipelineForm pipelineForm, BindException errors) throws Exception
        {
            getUtils().ensureSpecimenRequestsConfigured(false);
            return new JspView<>("/org/labkey/study/view/specimen/manageRequestInputs.jsp",
                    new ManageRequestInputsBean(getViewContext()));
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("coordinateSpecimens#form");
            _addManageStudy(root);
            root.addChild("Manage New Request Form");
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
        private final SpecimenRequestInput[] _inputs;
        private final Container _container;
        private final String _contextPath;

        public ManageRequestInputsBean(ViewContext context) throws SQLException
        {
            _container = context.getContainer();
            _inputs = SpecimenRequestManager.get().getNewSpecimenRequestInputs(_container);
            _contextPath = context.getContextPath();
        }

        public SpecimenRequestInput[] getInputs()
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

    public static class ManageCommentsForm
    {
        private Integer participantCommentDatasetId;
        private String participantCommentProperty;
        private Integer participantVisitCommentDatasetId;
        private String participantVisitCommentProperty;
        private boolean _reshow;

        public Integer getParticipantCommentDatasetId()
        {
            return participantCommentDatasetId;
        }

        public void setParticipantCommentDatasetId(Integer participantCommentDatasetId)
        {
            this.participantCommentDatasetId = participantCommentDatasetId;
        }

        public String getParticipantCommentProperty()
        {
            return participantCommentProperty;
        }

        public void setParticipantCommentProperty(String participantCommentProperty)
        {
            this.participantCommentProperty = participantCommentProperty;
        }

        public Integer getParticipantVisitCommentDatasetId()
        {
            return participantVisitCommentDatasetId;
        }

        public void setParticipantVisitCommentDatasetId(Integer participantVisitCommentDatasetId)
        {
            this.participantVisitCommentDatasetId = participantVisitCommentDatasetId;
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

    @RequiresPermission(AdminPermission.class)
    public class ManageSpecimenCommentsAction extends FormViewAction<ManageCommentsForm>
    {
        @Override
        public void validateCommand(ManageCommentsForm form, Errors errors)
        {
            String subjectNoun = StudyService.get().getSubjectNounSingular(getContainer());
            final Study study = BaseStudyController.getStudyRedirectIfNull(getContainer());
            if (form.getParticipantCommentDatasetId() != null && form.getParticipantCommentDatasetId() != -1)
            {
                DatasetDefinition def = StudyManager.getInstance().getDatasetDefinition(study, form.getParticipantCommentDatasetId());
                if (def != null && !def.isDemographicData())
                {
                    errors.reject(ERROR_MSG, "The Dataset specified to contain " + subjectNoun + " comments must be a demographics dataset.");
                }

                if (form.getParticipantCommentProperty() == null)
                    errors.reject(ERROR_MSG, "A Comment field name must be specified for the " + subjectNoun + " Comment Assignment.");
            }

            if (study.getTimepointType() != TimepointType.CONTINUOUS)
            {
                if (form.getParticipantVisitCommentDatasetId() != null && form.getParticipantVisitCommentDatasetId() != -1)
                {
                    DatasetDefinition def = StudyManager.getInstance().getDatasetDefinition(study, form.getParticipantVisitCommentDatasetId());
                    if (def != null && def.isDemographicData())
                    {
                        errors.reject(ERROR_MSG, "The Dataset specified to contain " + subjectNoun + "/Visit comments cannot be a demographics dataset.");
                    }

                    if (form.getParticipantVisitCommentProperty() == null)
                        errors.reject(ERROR_MSG, "A Comment field name must be specified for the " + subjectNoun + "/Visit Comment Assignment.");
                }
            }
        }

        @Override
        public ModelAndView getView(ManageCommentsForm form, boolean reshow, BindException errors)
        {
            StudyImpl study = getStudyRedirectIfNull();
            SecurityType securityType = study.getSecurityType();

            if (securityType == SecurityType.ADVANCED_READ || securityType == SecurityType.BASIC_READ)
                return new HtmlView("Comments can only be configured for studies with editable datasets.");

            if (!form.isReshow())
            {
                form.setParticipantCommentDatasetId(study.getParticipantCommentDatasetId());
                form.setParticipantCommentProperty(study.getParticipantCommentProperty());

                if (study.getTimepointType() != TimepointType.CONTINUOUS)
                {
                    form.setParticipantVisitCommentDatasetId(study.getParticipantVisitCommentDatasetId());
                    form.setParticipantVisitCommentProperty(study.getParticipantVisitCommentProperty());
                }
            }
            StudyJspView<Object> view = new StudyJspView<>(study, "/org/labkey/study/view/manageComments.jsp", form, errors);
            view.setTitle("Comment Configuration");

            return view;
        }

        @Override
        public boolean handlePost(ManageCommentsForm form, BindException errors)
        {
            if (null == getStudy())
                throw new IllegalStateException("No study found.");
            StudyImpl study = getStudy().createMutable();

            // participant comment dataset
            study.setParticipantCommentDatasetId(form.getParticipantCommentDatasetId());
            study.setParticipantCommentProperty(form.getParticipantCommentProperty());

            // participant/visit comment dataset
            if (study.getTimepointType() != TimepointType.CONTINUOUS)
            {
                study.setParticipantVisitCommentDatasetId(form.getParticipantVisitCommentDatasetId());
                study.setParticipantVisitCommentProperty(form.getParticipantVisitCommentProperty());
            }

            StudyManager.getInstance().updateStudy(getUser(), study);
            return true;
        }

        @Override
        public ActionURL getSuccessURL(ManageCommentsForm form)
        {
            return getManageStudyURL();
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("manageComments");
            _addManageStudy(root);
            root.addChild("Manage Comments");
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class CopyParticipantCommentAction extends SimpleViewAction<ParticipantCommentForm>
    {
        @Override
        public ModelAndView getView(final ParticipantCommentForm form, BindException errors)
        {
            StudyImpl study = getStudyRedirectIfNull();
            DatasetDefinition def;

            if (form.getVisitId() != 0)
            {
                def = StudyManager.getInstance().getDatasetDefinition(study, study.getParticipantVisitCommentDatasetId());
            }
            else
            {
                def = StudyManager.getInstance().getDatasetDefinition(study, study.getParticipantCommentDatasetId());
            }

            if (def != null)
            {
                StudyQuerySchema querySchema = StudyQuerySchema.createSchema(study, getUser(), true);
                DatasetQuerySettings qs = (DatasetQuerySettings)querySchema.getSettings(getViewContext(), DatasetQueryView.DATAREGION, def.getName());
                qs.setUseQCSet(false);

                DatasetQueryView queryView = new DatasetQueryView(querySchema, qs, errors)
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

                url.addParameter(ParticipantCommentForm.params.datasetId, def.getDatasetId()).
                        addParameter(ParticipantCommentForm.params.comment, form.getComment()).
                        addReturnURL(form.getReturnActionURL()).
                        addParameter(ParticipantCommentForm.params.visitId, form.getVisitId());

                for (int rowId : form.getVialCommentsToClear())
                    url.addParameter(ParticipantCommentForm.params.vialCommentsToClear, rowId);
                return HttpView.redirect(url);
            }
            return new HtmlView("Dataset could not be found");
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }

        private String getExistingComment(QueryView queryView)
        {
            final TableInfo table = queryView.getTable();
            if (table != null)
            {
                try (ResultSet rs = queryView.getResultSet())
                {
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
            }
            return null;
        }
    }

    @RequiresPermission(RequestSpecimensPermission.class)
    public class ImportVialIdsAction extends AbstractQueryImportAction<IdForm>
    {
        private int _requestId = -1;

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

        @Override
        public ModelAndView getView(IdForm form, BindException errors) throws Exception
        {
            initRequest(form);
            SpecimenRequest request = SpecimenRequestManager.get().getRequest(getContainer(), _requestId);
            if (request == null)
                throw new NotFoundException();

            if (!SpecimenRequestManager.get().hasEditRequestPermissions(getUser(), request) ||
                    SpecimenRequestManager.get().isInFinalState(request))
            {
                return new HtmlView("<div class=\"labkey-error\">You do not have permissions to modify this request.</div>");
            }

            return getDefaultImportView(form, errors);
        }

        @Override
        protected void validatePermission(User user, BindException errors)
        {
            checkPermissions();
            SpecimenRequest request = SpecimenRequestManager.get().getRequest(getContainer(), _requestId);

            if (!SpecimenRequestManager.get().hasEditRequestPermissions(getUser(), request) ||
                    SpecimenRequestManager.get().isInFinalState(request))
            {
                // No permission
                errors.reject(SpringActionController.ERROR_MSG, "You do not have permission to modify this request.");
            }
        }

        @Override
        protected boolean canInsert(User user)
        {
            return getContainer().hasPermission(user, RequestSpecimensPermission.class);
        }

        @Override
        protected int importData(DataLoader dl, FileStream file, String originalName, BatchValidationException errors, @Nullable AuditBehaviorType auditBehaviorType, @Nullable TransactionAuditProvider.TransactionAuditEvent auditEvent) throws IOException
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
                SpecimenManagerNew specimenManager = SpecimenManagerNew.get();
                try
                {
                    SpecimenRequest request = SpecimenRequestManager.get().getRequest(getContainer(), _requestId);
                    List<Vial> vials = SpecimenManagerNew.get().getVials(getContainer(), getUser(), globalIds);

                    if (vials != null && vials.size() == globalIds.length)
                    {
                        // All the specimens exist;
                        // Check for Availability and then add them to the request.
                        // There still may be errors (like someone already has requested that specimen) which will be
                        // caught by createRequestSpecimenMapping

                        for (Vial s : vials)
                        {
                            if (!s.isAvailable()) // || s.isLockedInRequest())
                            {
                                errorList.add(RequestabilityManager.makeSpecimenUnavailableMessage(s, null));
                            }
                        }

                        if (errorList.size() == 0)
                        {
                            ArrayList<Vial> vialList = new ArrayList<>(vials.size());
                            vialList.addAll(vials);
                            SpecimenRequestManager.get().createRequestSpecimenMapping(getUser(), request, vialList, true, true);
                        }
                    }
                    else
                    {
                        errorList.add("Duplicate Ids found.");
                    }
                }
                catch (RequestabilityManager.InvalidRuleException e)
                {
                    errorList.add("The request could not be created because a requestability rule is configured incorrectly. " +
                            "Please report this problem to an administrator. Error details: " + e.getMessage());
                }
                catch (SpecimenRequestException e)
                {
                    // There was an error; some id had no specimen matching
                    boolean hasSpecimenError = false;
                    for (String id : globalIds)
                    {
                        Vial vial = specimenManager.getVial(getContainer(), getUser(), id);
                        if (vial == null)
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

        @Override
        public void addNavTrail(NavTree root)
        {
            addSpecimenRequestNavTrail(root, _requestId);
            root.addChild("Upload Specimen Identifiers");
        }

        @Override
        protected ActionURL getSuccessURL(IdForm form)
        {
            ActionURL requestDetailsURL = new ActionURL(ManageRequestAction.class, getContainer());
            requestDetailsURL.addParameter("id", _requestId);
            return requestDetailsURL;
        }
    }

    @RequiresPermission(EditSpecimenDataPermission.class)
    public static class UpdateSpecimenQueryRowAction extends UserSchemaAction
    {
        @Override
        public ModelAndView getView(QueryUpdateForm tableForm, boolean reshow, BindException errors)
        {
            // Don't allow GlobalUniqueId to be edited
            TableInfo tableInfo = tableForm.getTable();
            if (null != tableInfo.getColumn("GlobalUniqueId"))
                ((BaseColumnInfo)tableInfo.getColumn("GlobalUniqueId")).setReadOnly(true);

            var vialComments = tableInfo.getColumn("VialComments");
            if (null != vialComments)
            {
                ((BaseColumnInfo)vialComments).setUserEditable(false);
                ((BaseColumnInfo)vialComments).setHidden(true);
            }

            SpecimenController.fixSpecimenRequestableColumn(tableForm);
            ButtonBar bb = createSubmitCancelButtonBar(tableForm);
            UpdateView view = new UpdateView(tableForm, errors);
            view.getDataRegion().setButtonBar(bb);
            return view;
        }

        @Override
        public boolean handlePost(QueryUpdateForm tableForm, BindException errors)
        {
            doInsertUpdate(tableForm, errors, false);
            return 0 == errors.getErrorCount();
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            super.addNavTrail(root);
            root.addChild("Edit " + _form.getQueryName());
        }
    }

    @RequiresPermission(EditSpecimenDataPermission.class)
    public static class InsertSpecimenQueryRowAction extends UserSchemaAction
    {
        @Override
        public ModelAndView getView(QueryUpdateForm tableForm, boolean reshow, BindException errors)
        {
            TableInfo tableInfo = tableForm.getTable();
            var vialComments = tableInfo.getColumn("VialComments");
            if (null != vialComments)
            {
                ((BaseColumnInfo)vialComments).setUserEditable(false);
                ((BaseColumnInfo)vialComments).setHidden(true);
            }

            SpecimenController.fixSpecimenRequestableColumn(tableForm);
            InsertView view = new InsertView(tableForm, errors);
            view.getDataRegion().setButtonBar(createSubmitCancelButtonBar(tableForm));
            return view;
        }

        @Override
        public boolean handlePost(QueryUpdateForm tableForm, BindException errors)
        {
            doInsertUpdate(tableForm, errors, true);
            return 0 == errors.getErrorCount();
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            super.addNavTrail(root);
            root.addChild("Insert " + _form.getQueryName());
        }
    }

    private static void fixSpecimenRequestableColumn(QueryUpdateForm tableForm)
    {
        TableInfo tableInfo = tableForm.getTable(); //TODO: finish fixing bug
        if (tableInfo instanceof SpecimenDetailTable)
            ((SpecimenDetailTable)tableInfo).changeRequestableColumn();
    }

/*
    // Used for testing
    @RequiresSiteAdmin
    public class DropVialIndices extends SimpleRedirectAction
    {
        @Override
        public URLHelper getRedirectURL(Object o) throws Exception
        {
            new SpecimenTablesProvider(getContainer(), getUser(), null).dropTableIndices(SpecimenTablesProvider.VIAL_TABLENAME);
            return new ActionURL(BeginAction.class, getContainer());
        }
    }

    // Used for testing
    @RequiresSiteAdmin
    public class AddVialIndices extends SimpleRedirectAction
    {
        @Override
        public URLHelper getRedirectURL(Object o) throws Exception
        {
            new SpecimenTablesProvider(getContainer(), getUser(), null).addTableIndices(SpecimenTablesProvider.VIAL_TABLENAME);
            return new ActionURL(BeginAction.class, getContainer());
        }
    }
*/
}
