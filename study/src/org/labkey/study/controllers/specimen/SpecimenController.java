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
import org.json.JSONObject;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.HasViewContext;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.QueryViewAction;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleRedirectAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.BaseDownloadAction;
import org.labkey.api.attachments.ByteArrayAttachmentFile;
import org.labkey.api.audit.TransactionAuditProvider;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.BeanViewForm;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.ExcelColumn;
import org.labkey.api.data.ExcelWriter;
import org.labkey.api.data.MenuButton;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TSVGridWriter;
import org.labkey.api.data.TSVWriter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusUrls;
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
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.User;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.specimen.AmbiguousLocationException;
import org.labkey.api.specimen.RequestEventType;
import org.labkey.api.specimen.RequestedSpecimens;
import org.labkey.api.specimen.SpecimenManager;
import org.labkey.api.specimen.SpecimenManagerNew;
import org.labkey.api.specimen.SpecimenRequestException;
import org.labkey.api.specimen.SpecimenRequestManager;
import org.labkey.api.specimen.SpecimenRequestManager.SpecimenRequestInput;
import org.labkey.api.specimen.SpecimenRequestStatus;
import org.labkey.api.specimen.SpecimenSearchWebPart;
import org.labkey.api.specimen.Vial;
import org.labkey.api.specimen.actions.HiddenFormInputGenerator;
import org.labkey.api.specimen.actions.ManageReqsBean;
import org.labkey.api.specimen.actions.ParticipantCommentForm;
import org.labkey.api.specimen.actions.ReportConfigurationBean;
import org.labkey.api.specimen.actions.SelectSpecimenProviderBean;
import org.labkey.api.specimen.actions.ShowGroupMembersAction;
import org.labkey.api.specimen.actions.ShowUploadSpecimensAction;
import org.labkey.api.specimen.actions.SpecimenReportActions;
import org.labkey.api.specimen.actions.ViewRequestsHeaderBean;
import org.labkey.api.specimen.importer.RequestabilityManager;
import org.labkey.api.specimen.importer.SimpleSpecimenImporter;
import org.labkey.api.specimen.location.LocationImpl;
import org.labkey.api.specimen.location.LocationManager;
import org.labkey.api.specimen.model.ExtendedSpecimenRequestView;
import org.labkey.api.specimen.model.SpecimenComment;
import org.labkey.api.specimen.model.SpecimenRequestActor;
import org.labkey.api.specimen.model.SpecimenRequestEvent;
import org.labkey.api.specimen.notifications.ActorNotificationRecipientSet;
import org.labkey.api.specimen.notifications.DefaultRequestNotification;
import org.labkey.api.specimen.notifications.NotificationRecipientSet;
import org.labkey.api.specimen.pipeline.SpecimenArchive;
import org.labkey.api.specimen.pipeline.SpecimenBatch;
import org.labkey.api.specimen.query.SpecimenEventQueryView;
import org.labkey.api.specimen.query.SpecimenQueryView;
import org.labkey.api.specimen.query.SpecimenRequestQueryView;
import org.labkey.api.specimen.requirements.SpecimenRequest;
import org.labkey.api.specimen.requirements.SpecimenRequestRequirement;
import org.labkey.api.specimen.requirements.SpecimenRequestRequirementProvider;
import org.labkey.api.specimen.requirements.SpecimenRequestRequirementType;
import org.labkey.api.specimen.security.permissions.EditSpecimenDataPermission;
import org.labkey.api.specimen.security.permissions.ManageDisplaySettingsPermission;
import org.labkey.api.specimen.security.permissions.ManageNewRequestFormPermission;
import org.labkey.api.specimen.security.permissions.ManageNotificationsPermission;
import org.labkey.api.specimen.security.permissions.ManageRequestRequirementsPermission;
import org.labkey.api.specimen.security.permissions.ManageRequestSettingsPermission;
import org.labkey.api.specimen.security.permissions.ManageRequestStatusesPermission;
import org.labkey.api.specimen.security.permissions.ManageRequestsPermission;
import org.labkey.api.specimen.security.permissions.ManageSpecimenActorsPermission;
import org.labkey.api.specimen.security.permissions.RequestSpecimensPermission;
import org.labkey.api.specimen.security.permissions.SetSpecimenCommentsPermission;
import org.labkey.api.specimen.settings.DisplaySettings;
import org.labkey.api.specimen.settings.RepositorySettings;
import org.labkey.api.specimen.settings.RequestNotificationSettings;
import org.labkey.api.specimen.settings.SettingsManager;
import org.labkey.api.specimen.settings.StatusSettings;
import org.labkey.api.specimen.view.SpecimenWebPart;
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
import org.labkey.api.study.security.permissions.ManageStudyPermission;
import org.labkey.api.util.Button;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.GUID;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.Link.LinkBuilder;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.AjaxCompletion;
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
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.study.CohortFilterFactory;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.BaseStudyController;
import org.labkey.study.designer.MapArrayExcelWriter;
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
        ShowUploadSpecimensAction.class,
        ShowUploadSpecimensAction.ImportCompleteAction.class,
        ShowGroupMembersAction.class,
        ParticipantCommentAction.SpecimenCommentInsertAction.class,
        ParticipantCommentAction.SpecimenCommentUpdateAction.class
    );

    public SpecimenController()
    {
        setActionResolver(_actionResolver);
    }

    protected SpecimenUtils getUtils()
    {
        return new SpecimenUtils(this);
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
            return getSpecimensURL(c).addParameter(SpecimenViewTypeForm.PARAMS.showVials, true);
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
        public ActionURL getOverviewURL(Container c)
        {
            return new ActionURL(OverviewAction.class, c);
        }

        @Override
        public ActionURL getShowCreateSpecimenRequestURL(Container c)
        {
            return new ActionURL(ShowCreateSpecimenRequestAction.class, c);
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
        public ActionURL getShowGroupMembersURL(Container c, int rowId, @Nullable Integer locationId, ActionURL returnUrl)
        {
            ActionURL url = new ActionURL(ShowGroupMembersAction.class, c);
            url.addParameter("id", Integer.toString(rowId));
            if (locationId != null)
                url.addParameter("locationId", locationId);
            url.addReturnURL(returnUrl);

            return url;
        }

        @Override
        public ActionURL getAutoReportListURL(Container c)
        {
            return new ActionURL(AutoReportListAction.class, c);
        }

        @Override
        public ActionURL getSpecimenEventsURL(Container c, ActionURL returnURL)
        {
            ActionURL url = new ActionURL(SpecimenEventsAction.class, c);
            url.addReturnURL(returnURL);

            return url;
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
        public ActionURL getUploadSpecimensURL(Container c)
        {
            return new ActionURL(ShowUploadSpecimensAction.class, c);
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
        public ActionURL getManageActorsURL(Container c)
        {
            return new ActionURL(ManageActorsAction.class, c);
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

        @Override
        public ActionURL getCompleteSpecimenURL(Container c, String type)
        {
            return new ActionURL(SpecimenController.CompleteSpecimenAction.class, c).addParameter("type", type);
        }

        @Override
        public ActionURL getTypeParticipantReportURL(Container c)
        {
            return new ActionURL(SpecimenReportActions.TypeParticipantReportAction.class, c);
        }

        @Override
        public void addSpecimenNavTrail(NavTree root, String childTitle, Container c)
        {
            ActionURL overviewURL = new ActionURL(OverviewAction.class, c);
            root.addChild("Specimen Overview", overviewURL);
            root.addChild("Available Reports", new ActionURL(SpecimenController.AutoReportListAction.class, c));
            root.addChild(childTitle);
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class OverviewAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object form, BindException errors)
        {
            if (null == StudyService.get().getStudy(getContainer()))
                return new HtmlView("This folder does not contain a study.");
            SpecimenSearchWebPart specimenSearch = new SpecimenSearchWebPart(true);
            SpecimenWebPart specimenSummary = new SpecimenWebPart(true, StudyService.get().getStudy(getContainer()));
            return new VBox(specimenSummary, specimenSearch);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addBaseSpecimenNavTrail(root);
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
        ActionURL overviewURL = new ActionURL(OverviewAction.class, getContainer());
        root.addChild("Specimen Overview", overviewURL);
    }

    private void addSpecimenRequestsNavTrail(NavTree root)
    {
        addBaseSpecimenNavTrail(root);
        root.addChild("Specimen Requests", new ActionURL(ViewRequestsAction.class, getContainer()));
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
        url.addParameter(org.labkey.api.specimen.actions.IdForm.PARAMS.id, Integer.toString(requestID));
        if (returnUrl != null)
            url.addParameter(ManageRequestForm.PARAMS.returnUrl, returnUrl);
        return url;
    }

    private ActionURL getExtendedRequestURL(int requestID, String returnUrl)
    {
        ActionURL url = new ActionURL(ExtendedSpecimenRequestAction.class, getContainer());
        url.addParameter(org.labkey.api.specimen.actions.IdForm.PARAMS.id, Integer.toString(requestID));
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
            StudyImpl study = getStudyThrowIfNull();

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

    public static class ViewEventForm extends org.labkey.api.specimen.actions.IdForm
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
        private Vial _vial;

        public SpecimenEventBean(Vial vial, String returnUrl)
        {
            _vial = vial;
            setReturnUrl(returnUrl);
        }

        public Vial getVial()
        {
            return _vial;
        }

        public void setVial(Vial vial)
        {
            _vial = vial;
        }
    }

    static class SpecimenEventForm
    {
        private String _id;
        private Container _targetStudy;

        public String getId()
        {
            return _id;
        }

        public void setId(String id)
        {
            _id = id;
        }

        public Container getTargetStudy()
        {
            return _targetStudy;
        }

        public void setTargetStudy(Container targetStudy)
        {
            _targetStudy = targetStudy;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class SpecimenEventsRedirectAction extends SimpleViewAction<SpecimenEventForm>
    {
        @Override
        public ModelAndView getView(SpecimenEventForm form, BindException errors)
        {
            if (form.getId() != null && form.getTargetStudy() != null)
            {
                Vial vial = SpecimenManagerNew.get().getVial(form.getTargetStudy(), getUser(), form.getId());
                if (vial != null)
                {
                    ActionURL url = new ActionURL(SpecimenEventsAction.class, form.getTargetStudy()).addParameter("id", vial.getRowId());
                    throw new RedirectException(url);
                }
            }
            return new HtmlView("<span class='labkey-error'>Unable to resolve the Specimen ID and target Study</span>");
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class SpecimenEventsAction extends SimpleViewAction<ViewEventForm>
    {
        private boolean _showingSelectedSpecimens;

        @Override
        public ModelAndView getView(ViewEventForm viewEventForm, BindException errors)
        {
            if (null == StudyManager.getInstance().getStudy(getContainer()))
            {
                throw new NotFoundException("Folder does not have a study.");
            }
            _showingSelectedSpecimens = viewEventForm.isSelected();
            Vial vial = SpecimenManagerNew.get().getVial(getContainer(), getUser(), viewEventForm.getId());
            if (vial == null)
                throw new NotFoundException("Specimen " + viewEventForm.getId() + " does not exist.");

            JspView<SpecimenEventBean> summaryView = new JspView<>("/org/labkey/study/view/specimen/specimen.jsp",
                    new SpecimenEventBean(vial, viewEventForm.getReturnUrl()));
            summaryView.setTitle("Vial Summary");

            SpecimenEventQueryView vialHistoryView = SpecimenEventQueryView.createView(getViewContext(), vial);
            vialHistoryView.setTitle("Vial History");

            VBox vbox;

            if (SettingsManager.get().getRepositorySettings(getStudyRedirectIfNull().getContainer()).isEnableRequests())
            {
                List<Integer> requestIds = SpecimenRequestManager.get().getRequestIdsForSpecimen(vial);
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
                    relevantRequests = new JspView("/org/labkey/specimen/view/relevantRequests.jsp");
                relevantRequests.setTitle("Relevant Vial Requests");
                vbox = new VBox(summaryView, vialHistoryView, relevantRequests);
            }
            else
            {
                vbox = new VBox(summaryView, vialHistoryView);
            }

            return vbox;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addBaseSpecimenNavTrail(root);
            if (_showingSelectedSpecimens)
            {
                root.addChild("Selected Specimens", new ActionURL(SelectedSpecimensAction.class,
                        getContainer()).addParameter(SpecimenViewTypeForm.PARAMS.showVials, "true"));
            }
            root.addChild("Vial History");
        }
    }

    private void requiresEditRequestPermissions(SpecimenRequest request)
    {
        if (!SpecimenRequestManager.get().hasEditRequestPermissions(getUser(), request))
            throw new UnauthorizedException();
    }

    public static class ModifySpecimenRequestForm extends org.labkey.api.specimen.actions.IdForm implements HiddenFormInputGenerator
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

    public static class ManageRequestForm extends org.labkey.api.specimen.actions.IdForm
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
                    ActionButton addButton = new ActionButton(new ActionURL(OverviewAction.class, getContainer()), "Specimen Search");
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


    @RequiresPermission(ReadPermission.class)
    public class ViewRequestsAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            requiresLogin();
            SpecimenRequestQueryView grid = SpecimenRequestQueryView.createView(getViewContext());
            grid.setExtraLinks(true);
            grid.setShowCustomizeLink(false);
            grid.setShowDetailsColumn(false);
            if (getContainer().hasPermission(getUser(), RequestSpecimensPermission.class))
            {
                ActionButton insertButton = new ActionButton(ShowCreateSpecimenRequestAction.class, "Create New Request", ActionButton.Action.LINK);
                grid.setButtons(Collections.singletonList(insertButton));
            }
            else
            {
                grid.setButtons(Collections.emptyList());
            }

            JspView<ViewRequestsHeaderBean> header = new JspView<>("/org/labkey/specimen/view/viewRequestsHeader.jsp",
                    new ViewRequestsHeaderBean(getViewContext(), grid));

            return new VBox(header, grid);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addSpecimenRequestsNavTrail(root);
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

    public static class ManageRequestStatusForm extends org.labkey.api.specimen.actions.IdForm
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

    public static class CreateSpecimenRequestForm extends ReturnUrlForm implements HiddenFormInputGenerator
    {
        public enum PARAMS
        {
            returnUrl,
            extendedRequestUrl,
            ignoreReturnUrl
        }

        private String[] _inputs;
        private int _destinationLocation;
        private long[] _specimenRowIds;
        private boolean[] _required;
        private boolean _fromGroupedView;
        private Integer _preferredLocation;
        private boolean _ignoreReturnUrl;
        private boolean _extendedRequestUrl;
        private String[] _specimenIds;

        @Override
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
            if (getReturnUrl() != null)
                builder.append("<input type=\"hidden\" name=\"returnUrl\" value=\"").append(PageFlowUtil.filter(getReturnUrl())).append("\">\n");
            if (_specimenRowIds != null)
            {
                for (long specimenId : _specimenRowIds)
                    builder.append("<input type=\"hidden\" name=\"specimenRowIds\" value=\"").append(specimenId).append("\">\n");
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

            if (_specimenIds != null)
            {
                for (String specimenId : _specimenIds)
                    builder.append("<input type=\"hidden\" name=\"specimenIds\" value=\"").append(PageFlowUtil.filter(specimenId)).append("\">\n");
            }

            builder.append("<input type=\"hidden\" name=\"fromGroupedView\" value=\"").append(_fromGroupedView).append("\">\n");
            return builder.toString();
        }

        public long[] getSpecimenRowIds()
        {
            return _specimenRowIds;
        }

        public void setSpecimenRowIds(long[] specimenRowIds)
        {
            _specimenRowIds = specimenRowIds;
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

        public void setFromGroupedView(boolean fromGroupedView)
        {
            _fromGroupedView = fromGroupedView;
        }

        public Integer getPreferredLocation()
        {
            return _preferredLocation;
        }

        public void setPreferredLocation(Integer preferredLocation)
        {
            _preferredLocation = preferredLocation;
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

        public String[] getSpecimenIds()
        {
            return _specimenIds;
        }

        public void setSpecimenIds(String[] specimenIds)
        {
            _specimenIds = specimenIds;
        }

        public RequestedSpecimens getSelectedSpecimens(SpecimenUtils utils) throws AmbiguousLocationException
        {
            // first check for explicitly listed specimen row ids (this is the case when posting the final
            // specimen request form):
            List<Vial> requestedSpecimens = utils.getSpecimensFromRowIds(getSpecimenRowIds());
            if (requestedSpecimens != null && requestedSpecimens.size() > 0)
                return new RequestedSpecimens(requestedSpecimens);

            Set<String> ids;
            if ("post".equalsIgnoreCase(utils.getViewContext().getRequest().getMethod()) &&
                    (utils.getViewContext().getRequest().getParameter(DataRegionSelection.DATA_REGION_SELECTION_KEY) != null))
            {
                ids = DataRegionSelection.getSelected(utils.getViewContext(), null, true);
                if (isFromGroupedView())
                    return utils.getRequestableBySpecimenHash(ids, getPreferredLocation());
                else
                    return utils.getRequestableByVialRowIds(ids);
            }
            else if (_specimenIds != null && _specimenIds.length > 0)
            {
                ids = new HashSet<>();
                Collections.addAll(ids, _specimenIds);
                if (isFromGroupedView())
                    return utils.getRequestableBySpecimenHash(ids, getPreferredLocation());
                else
                    return utils.getRequestableByVialGlobalUniqueIds(ids);
            }
            else
                return null;
        }
    }

    public static class NewRequestBean extends SpecimensViewBean
    {
        private final Container _container;
        private final SpecimenRequestInput[] _inputs;
        private final String[] _inputValues;
        private final int _selectedSite;
        private final BindException _errors;
        private final ActionURL _returnUrl;

        public NewRequestBean(ViewContext context, RequestedSpecimens requestedSpecimens, CreateSpecimenRequestForm form, BindException errors) throws SQLException
        {
            super(context, requestedSpecimens != null ? requestedSpecimens.getVials() : null, false, false, false, false);
            _errors = errors;
            _inputs = SpecimenRequestManager.get().getNewSpecimenRequestInputs(context.getContainer());
            _selectedSite = form.getDestinationLocation();
            _inputValues = form.getInputs();
            _container = context.getContainer();
            _returnUrl = form.getReturnActionURL();
        }

        public SpecimenRequestInput[] getInputs()
        {
            return _inputs;
        }

        public String getValue(int inputIndex) throws ValidationException
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

        public ActionURL getReturnUrl()
        {
            return _returnUrl;
        }
    }

    @RequiresPermission(RequestSpecimensPermission.class)
    public class HandleCreateSpecimenRequestAction extends FormViewAction<CreateSpecimenRequestForm>
    {
        private SpecimenRequest _specimenRequest;

        @Override
        public ModelAndView getView(CreateSpecimenRequestForm form, boolean reshow, BindException errors) throws Exception
        {
            return getCreateSpecimenRequestView(form, errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addSpecimenRequestsNavTrail(root);
            root.addChild("New Specimen Request");
        }

        @Override
        public void validateCommand(CreateSpecimenRequestForm form, Errors errors)
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

        @Override
        public boolean handlePost(CreateSpecimenRequestForm form, BindException errors) throws Exception
        {
            getUtils().ensureSpecimenRequestsConfigured(true);

            String[] inputs = form.getInputs();
            long[] specimenIds = form.getSpecimenRowIds();
            StringBuilder comments = new StringBuilder();
            SpecimenRequestInput[] expectedInputs =
                    SpecimenRequestManager.get().getNewSpecimenRequestInputs(getContainer());
            if (inputs.length != expectedInputs.length)
                throw new IllegalStateException("Expected a form element for each input.");

            for (int i = 0; i < expectedInputs.length; i++)
            {
                SpecimenRequestInput expectedInput = expectedInputs[i];
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

            _specimenRequest = new SpecimenRequest();
            _specimenRequest.setComments(comments.toString());
            _specimenRequest.setContainer(getContainer());
            Timestamp ts = new Timestamp(System.currentTimeMillis());
            _specimenRequest.setCreated(ts);
            _specimenRequest.setModified(ts);
            _specimenRequest.setEntityId(GUID.makeGUID());
            Integer defaultSiteId = SpecimenService.get().getRequestCustomizer().getDefaultDestinationSiteId();
            // Default takes precedence if set
            if (defaultSiteId != null)
            {
                _specimenRequest.setDestinationSiteId(defaultSiteId);
            }
            else if (form.getDestinationLocation() > 0)
            {
                _specimenRequest.setDestinationSiteId(form.getDestinationLocation());
            }
            _specimenRequest.setStatusId(SpecimenRequestManager.get().getInitialRequestStatus(getContainer(), getUser(), false).getRowId());

            DbScope scope = StudySchema.getInstance().getSchema().getScope();
            try (DbScope.Transaction transaction = scope.ensureTransaction())
            {
                if (!LocationManager.get().isSiteValidRequestingLocation(getContainer(), _specimenRequest.getDestinationSiteId()))
                {
                    errors.reject(ERROR_MSG, "The requesting location is not valid.");
                    return false;
                }

                User user = getUser();
                Container container = getContainer();
                _specimenRequest = SpecimenRequestManager.get().createRequest(getUser(), _specimenRequest, true);
                List<Vial> vials;
                if (specimenIds != null && specimenIds.length > 0)
                {
                    vials = new ArrayList<>();
                    for (long specimenId : specimenIds)
                    {
                        Vial vial = SpecimenManagerNew.get().getVial(container, user, specimenId);
                        if (vial != null)
                        {
                            boolean isAvailable = vial.isAvailable();
                            if (!isAvailable)
                            {
                                errors.reject(null, RequestabilityManager.makeSpecimenUnavailableMessage(vial, "This specimen has been removed from the list below."));
                            }
                            else
                                vials.add(vial);
                        }
                        else
                        {
                            ExceptionUtil.logExceptionToMothership(getViewContext().getRequest(),
                                    new IllegalStateException("Specimen ID " + specimenId + " was not found in container " + container.getId()));
                        }
                    }
                    if (errors.getErrorCount() == 0)
                    {
                        try
                        {
                            SpecimenRequestManager.get().createRequestSpecimenMapping(getUser(), _specimenRequest, vials, true, true);
                        }
                        catch (RequestabilityManager.InvalidRuleException e)
                        {
                            errors.reject(ERROR_MSG, "The request could not be created because a requestability rule is configured incorrectly. " +
                                    "Please report this problem to an administrator. Error details: " + e.getMessage());
                            return false;
                        }
                        catch (SpecimenRequestException e)
                        {
                            errors.reject(ERROR_MSG, "A vial that was available for request has become unavailable.");
                            return false;
                        }
                    }
                    else
                    {
                        long[] validSpecimenIds = new long[vials.size()];
                        int index = 0;
                        for (Vial vial : vials)
                            validSpecimenIds[index++] = vial.getRowId();
                        form.setSpecimenRowIds(validSpecimenIds);
                        return false;
                    }
                }
                transaction.commit();
            }

            if (!SettingsManager.get().isSpecimenShoppingCartEnabled(getContainer()))
            {
                try
                {
                    getUtils().sendNewRequestNotifications(_specimenRequest, errors);
                }
                catch (ConfigurationException | IOException e)
                {
                    errors.reject(ERROR_MSG, e.getMessage());
                }
            }

            if (errors.hasErrors())
                return false;

            StudyImpl study = getStudy();
            if (null == study)
                throw new NotFoundException("No study exists in this folder.");
            study.setLastSpecimenRequest(_specimenRequest.getRowId());
            return true;
        }

        @Override
        public ActionURL getSuccessURL(CreateSpecimenRequestForm createSpecimenRequestForm)
        {
            ActionURL modifiedReturnURL = null;
            if (createSpecimenRequestForm.isExtendedRequestUrl())
            {
                return getExtendedRequestURL(_specimenRequest.getRowId(), null);
            }
            if (createSpecimenRequestForm.getReturnUrl() != null)
            {
                modifiedReturnURL = createSpecimenRequestForm.getReturnActionURL();
            }
            if (modifiedReturnURL != null && !createSpecimenRequestForm.isIgnoreReturnUrl())
                return modifiedReturnURL;
            else
                return getManageRequestURL(_specimenRequest.getRowId(), modifiedReturnURL != null ? modifiedReturnURL.getLocalURIString() : null);
        }
    }

    private ModelAndView getCreateSpecimenRequestView(CreateSpecimenRequestForm form, BindException errors) throws SQLException
    {
        getUtils().ensureSpecimenRequestsConfigured(true);
        
        RequestedSpecimens requested;

        try
        {
            requested = form.getSelectedSpecimens(getUtils());
        }
        catch (AmbiguousLocationException e)
        {
            // Even though this method (getCreateSpecimenRequestView) is used from multiple places, only HandleCreateSpecimenRequestAction
            // receives a post; therefore, it's safe to say that the selectSpecimenProvider.jsp form should always post to
            // HandleCreateSpecimenRequestAction.
            return new JspView<>("/org/labkey/specimen/view/selectSpecimenProvider.jsp",
                    new SelectSpecimenProviderBean(form, e.getPossibleLocations(), new ActionURL(ShowCreateSpecimenRequestAction.class, getContainer())), errors);
        }

        return new JspView<>("/org/labkey/study/view/specimen/requestSpecimens.jsp",
                new NewRequestBean(getViewContext(), requested, form, errors));
    }

    @RequiresPermission(RequestSpecimensPermission.class)
    public class ShowCreateSpecimenRequestAction extends SimpleViewAction<CreateSpecimenRequestForm>
    {
        @Override
        public ModelAndView getView(CreateSpecimenRequestForm form, BindException errors) throws Exception
        {
            return getCreateSpecimenRequestView(form, errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("specimenShopping");
            addSpecimenRequestsNavTrail(root);
            root.addChild("New Specimen Request");
        }
    }

    @RequiresPermission(RequestSpecimensPermission.class)
    public class ShowAPICreateSpecimenRequestAction extends SimpleViewAction<CreateSpecimenRequestForm>
    {
        @Override
        public ModelAndView getView(CreateSpecimenRequestForm form, BindException errors) throws Exception
        {
            return getCreateSpecimenRequestView(form, errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addSpecimenRequestsNavTrail(root);
            root.addChild("New Specimen Request");
        }
    }

    @RequiresPermission(RequestSpecimensPermission.class)
    public class ExtendedSpecimenRequestAction extends SimpleViewAction<CreateSpecimenRequestForm>
    {
        @Override
        public ModelAndView getView(CreateSpecimenRequestForm form, BindException errors)
        {
            VBox vbox = new VBox();

            ExtendedSpecimenRequestView view = SpecimenManager.get().getExtendedSpecimenRequestView(getViewContext());
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

        @Override
        public void addNavTrail(NavTree root)
        {
            addSpecimenRequestsNavTrail(root);
            root.addChild("Extended Specimen Request");
        }
    }


    public static class RequirementForm extends org.labkey.api.specimen.actions.IdForm
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


    @RequiresPermission(ManageRequestRequirementsPermission.class)
    public class DeleteDefaultRequirementAction extends FormHandlerAction<org.labkey.api.specimen.actions.IdForm>
    {
        @Override
        public void validateCommand(org.labkey.api.specimen.actions.IdForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(org.labkey.api.specimen.actions.IdForm form, BindException errors) throws Exception
        {
            SpecimenRequestRequirement requirement =
                    SpecimenRequestRequirementProvider.get().getRequirement(getContainer(), form.getId());
            // we should only be deleting default requirements (those without an associated request):
            if (requirement != null && requirement.getRequestId() == -1)
            {
                SpecimenRequestManager.get().deleteRequestRequirement(getUser(), requirement, false);
                return true;
            }

            return false;
        }

        @Override
        public ActionURL getSuccessURL(org.labkey.api.specimen.actions.IdForm requirementForm)
        {
            return new ActionURL(ManageDefaultReqsAction.class, getContainer());
        }
    }

    @RequiresPermission(ManageRequestsPermission.class)
    public class DeleteMissingRequestSpecimensAction extends FormHandlerAction<org.labkey.api.specimen.actions.IdForm>
    {
        @Override
        public void validateCommand(org.labkey.api.specimen.actions.IdForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(org.labkey.api.specimen.actions.IdForm form, BindException errors) throws Exception
        {
            SpecimenRequest request = SpecimenRequestManager.get().getRequest(getContainer(), form.getId());
            if (request == null)
                throw new NotFoundException("Specimen request " + form.getId() + " does not exist.");

            SpecimenRequestManager.get().deleteMissingSpecimens(request);
            return true;
        }

        @Override
        public ActionURL getSuccessURL(org.labkey.api.specimen.actions.IdForm requirementForm)
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
    public class RequestHistoryAction extends SimpleViewAction<org.labkey.api.specimen.actions.IdForm>
    {
        private int _requestId;

        @Override
        public ModelAndView getView(org.labkey.api.specimen.actions.IdForm form, BindException errors)
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

    @RequiresPermission(ReadPermission.class)
    public class SpecimenRequestConfigRequired extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            return new JspView("/org/labkey/specimen/view/configurationRequired.jsp");
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            _addNavTrail(root);
            root.addChild("Unable to Request Specimens");
        }
    }

    @RequiresPermission(ManageRequestRequirementsPermission.class)
    public class ManageDefaultReqsAction extends FormViewAction<DefaultRequirementsForm>
    {
        @Override
        public void validateCommand(DefaultRequirementsForm target, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(DefaultRequirementsForm defaultRequirementsForm, boolean reshow, BindException errors)
        {
            getUtils().ensureSpecimenRequestsConfigured(true);
            return new JspView<>("/org/labkey/study/view/specimen/manageDefaultReqs.jsp",
                new ManageReqsBean(getUser(), getContainer()));
        }

        @Override
        public boolean handlePost(DefaultRequirementsForm form, BindException errors)
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
                SpecimenRequestRequirement requirement = new SpecimenRequestRequirement();
                requirement.setContainer(getContainer());
                requirement.setActorId(actorId);
                requirement.setDescription(description);
                requirement.setRequestId(-1);
                SpecimenRequestRequirementProvider.get().createDefaultRequirement(getUser(), requirement, type);
            }
        }

        @Override
        public ActionURL getSuccessURL(DefaultRequirementsForm form)
        {
            if (form.getNextPage() != null && form.getNextPage().length() > 0)
                return new ActionURL(form.getNextPage());
            else
                return getManageStudyURL();
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("coordinateSpecimens#requirements");
            _addManageStudy(root);
            root.addChild("Manage Default Requirements");
        }
    }

    private ActionURL getManageStudyURL()
    {
        return urlProvider(StudyUrls.class).getManageStudyURL(getContainer());
    }

    public static class EmailSpecimenListForm extends org.labkey.api.specimen.actions.IdForm
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
    public class SubmitRequestAction extends FormHandlerAction<org.labkey.api.specimen.actions.IdForm>
    {
        @Override
        public void validateCommand(org.labkey.api.specimen.actions.IdForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(org.labkey.api.specimen.actions.IdForm form, BindException errors) throws Exception
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
        public ActionURL getSuccessURL(org.labkey.api.specimen.actions.IdForm idForm)
        {
            ActionURL successURL = getManageRequestURL(idForm.getId());
            successURL.addParameter(ManageRequestForm.PARAMS.submissionResult, Boolean.TRUE.toString());
            return successURL;
        }
    }

    @RequiresPermission(RequestSpecimensPermission.class)
    public class DeleteRequestAction extends FormHandlerAction<org.labkey.api.specimen.actions.IdForm>
    {
        @Override
        public void validateCommand(org.labkey.api.specimen.actions.IdForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(org.labkey.api.specimen.actions.IdForm form, BindException errors) throws Exception
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
        public ActionURL getSuccessURL(org.labkey.api.specimen.actions.IdForm form)
        {
            return new ActionURL(ViewRequestsAction.class, getContainer());
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

    public static class ExportSiteForm extends org.labkey.api.specimen.actions.IdForm
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

    public static class LabSpecimenListsForm extends org.labkey.api.specimen.actions.IdForm
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

    @RequiresPermission(ReadPermission.class)
    public class AutoReportListAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object form, BindException errors)
        {
            return new JspView<>("/org/labkey/specimen/view/autoReportList.jsp", new ReportConfigurationBean(getViewContext()));
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("exploreSpecimens");
            addBaseSpecimenNavTrail(root);
            root.addChild("Specimen Reports");
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
            List<Vial> selectedVials = getUtils().getSpecimensFromPost(updateSpecimenCommentsForm.isFromGroupedView(), false);
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
            List<Vial> selectedVials = getUtils().getSpecimensFromPost(specimenCommentsForm.isFromGroupedView(), false);

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


    @RequiresPermission(AdminPermission.class)
    public class ImportSpecimenData extends SimpleViewAction<PipelineForm>
    {
        private String[] _filePaths = null;

        @Override
        public ModelAndView getView(PipelineForm form, BindException bindErrors)
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
            boolean isEmpty = SpecimenManagerNew.get().isSpecimensEmpty(getContainer(), getUser());
            if (isEmpty)
            {
                bean.setNoSpecimens(true);
            }
            else if (SettingsManager.get().getRepositorySettings(getStudyThrowIfNull().getContainer()).isSpecimenDataEditable())
            {
                bean.setDefaultMerge(true);         // Repository is editable; make Merge the default
                bean.setEditableSpecimens(true);
            }

            return new JspView<>("/org/labkey/study/view/specimen/importSpecimens.jsp", bean);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            String msg;
            if (_filePaths.length == 1)
                msg = _filePaths[0];
            else
                msg = _filePaths.length + " specimen archives";
            root.addChild("Import Study Batch - " + msg);
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class SubmitSpecimenBatchImport extends FormHandlerAction<PipelineForm>
    {
        @Override
        public void validateCommand(PipelineForm target, Errors errors)
        {
        }

        @Override
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

        @Override
        public ActionURL getSuccessURL(PipelineForm pipelineForm)
        {
            return urlProvider(PipelineStatusUrls.class).urlBegin(getContainer());
        }
    }

    /**
     * Legacy method hit via WGET/CURL to programmatically initiate a specimen import; no longer used by the UI,
     * but this method should be kept around until we receive verification that the URL is no longer being hit
     * programmatically.
     */
    @RequiresPermission(AdminPermission.class)
    public class SubmitSpecimenImport extends FormHandlerAction<PipelineForm>
    {
        @Override
        public void validateCommand(PipelineForm target, Errors errors)
        {
        }

        @Override
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


        @Override
        public ActionURL getSuccessURL(PipelineForm pipelineForm)
        {
            return urlProvider(PipelineStatusUrls.class).urlBegin(getContainer());
        }
    }


    public static void submitSpecimenBatch(Container c, User user, ActionURL url, File f, PipeRoot root, boolean merge) throws IOException
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


    public static class SpecimenEventAttachmentForm
    {
        private int _eventId;
        private String _name;

        public int getEventId()
        {
            return _eventId;
        }

        public void setEventId(int eventId)
        {
            _eventId = eventId;
        }

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }
    }


    public static ActionURL getDownloadURL(SpecimenRequestEvent event, String name)
    {
        return new ActionURL(DownloadAction.class, event.getContainer())
            .addParameter("eventId", event.getRowId())
            .addParameter("name", name);
    }

    @RequiresPermission(ReadPermission.class)
    public class DownloadAction extends BaseDownloadAction<SpecimenEventAttachmentForm>
    {
        @Override
        public @Nullable Pair<AttachmentParent, String> getAttachment(SpecimenEventAttachmentForm form)
        {
            SpecimenRequestEvent event = SpecimenRequestManager.get().getRequestEvent(getContainer(), form.getEventId());
            if (event == null)
                throw new NotFoundException("Specimen event not found");

            return new Pair<>(event, form.getName());
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class ShowManageRepositorySettingsAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            return new JspView<>("/org/labkey/study/view/specimen/manageRepositorySettings.jsp", SettingsManager.get().getRepositorySettings(getContainer()));
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("specimenAdminTutorial");
            _addManageStudy(root);
            root.addChild("Manage Repository Settings");
        }
    }

    @RequiresPermission(ManageStudyPermission.class)
    public class ManageRepositorySettingsAction extends FormHandlerAction<ManageRepositorySettingsForm>
    {
        @Override
        public void validateCommand(ManageRepositorySettingsForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(ManageRepositorySettingsForm form, BindException errors) throws Exception
        {
            RepositorySettings settings = SettingsManager.get().getRepositorySettings(getContainer());
            settings.setSimple(form.isSimple());
            settings.setEnableRequests(!form.isSimple() && form.isEnableRequests());
            settings.setSpecimenDataEditable(!form.isSimple() && form.isSpecimenDataEditable());
            SettingsManager.get().saveRepositorySettings(getContainer(), settings);

            return true;
        }

        @Override
        public URLHelper getSuccessURL(ManageRepositorySettingsForm manageRepositorySettingsForm)
        {
            return getManageStudyURL();
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

    @RequiresPermission(ManageSpecimenActorsPermission.class)
    public class ManageActorOrderAction extends DisplayManagementSubpageAction<BaseStudyController.BulkEditForm>
    {
        public ManageActorOrderAction()
        {
            super("manageActorOrder", "Manage Actor Order", "specimenRequest");
        }

        @Override
        protected JspView<StudyImpl> getJspView(StudyImpl study)
        {
            return new JspView<>("/org/labkey/study/view/specimen/manageActorOrder.jsp", study);
        }

        @Override
        public boolean handlePost(BulkEditForm form, BindException errors)
        {
            getUtils().ensureSpecimenRequestsConfigured(false);

            String order = form.getOrder();
            if (order != null && order.length() > 0)
            {
                String[] rowIds = order.split(",");
                // get a map of id to actor objects before starting our updates; this prevents us from
                // blowing then repopulating the cache with each update:
                Map<Integer, SpecimenRequestActor> idToActor = getIdToRequestActorMap(getContainer());
                for (int i = 0; i < rowIds.length; i++)
                {
                    int rowId = Integer.parseInt(rowIds[i]);
                    SpecimenRequestActor actor = idToActor.get(rowId);
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

        @Override
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

        @Override
        public void validateCommand(Form target, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(Form form, boolean reshow, BindException errors)
        {
            getUtils().ensureSpecimenRequestsConfigured(false);
            return getJspView(getStudyRedirectIfNull());
        }

        protected abstract JspView<StudyImpl> getJspView(StudyImpl study);

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic(new HelpTopic(_helpTopic));
            _addManageStudy(root);
            root.addChild(_title);
        }
    }

    private Map<Integer, SpecimenRequestActor> getIdToRequestActorMap(Container container)
    {
        SpecimenRequestActor[] actors = SpecimenRequestRequirementProvider.get().getActors(container);
        Map<Integer, SpecimenRequestActor> idToStatus = new HashMap<>();
        for (SpecimenRequestActor actor : actors)
            idToStatus.put(actor.getRowId(), actor);
        return idToStatus;
    }

    @RequiresPermission(ManageSpecimenActorsPermission.class)
    public class ManageActorsAction extends DisplayManagementSubpageAction<ActorEditForm>
    {
        public ManageActorsAction()
        {
            super("manageActors", "Manage Specimen Request Actors", "coordinateSpecimens#actor");
        }

        @Override
        protected JspView<StudyImpl> getJspView(StudyImpl study)
        {
            return new JspView<>("/org/labkey/study/view/specimen/manageActors.jsp", study);
        }

        @Override
        public boolean handlePost(ActorEditForm form, BindException errors)
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
                    Map<Integer, SpecimenRequestActor> idToActor = getIdToRequestActorMap(getContainer());
                    for (int i = 0; i < rowIds.length; i++)
                    {
                        int rowId = rowIds[i];
                        String label = labels[i];
                        SpecimenRequestActor actor = idToActor.get(rowId);
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
                    SpecimenRequestActor actor = new SpecimenRequestActor();
                    actor.setLabel(form.getNewLabel());
                    SpecimenRequestActor[] actors = SpecimenRequestRequirementProvider.get().getActors(getContainer());
                    actor.setSortOrder(actors.length);
                    actor.setContainer(getContainer());
                    actor.setPerSite(form.isNewPerSite());
                    actor.create(getUser());
                }
            }
            return !errors.hasErrors();
        }

        @Override
        public ActionURL getSuccessURL(ActorEditForm form)
        {
            if (form.getNextPage() != null && form.getNextPage().length() > 0)
                return new ActionURL(form.getNextPage());
            else
                return getManageStudyURL();
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

    @RequiresPermission(ManageSpecimenActorsPermission.class)
    public class ManageStatusOrderAction extends DisplayManagementSubpageAction<BulkEditForm>
    {
        public ManageStatusOrderAction()
        {
            super("manageStatusOrder", "Manage Status Order", "specimenRequest");
        }

        @Override
        protected JspView<StudyImpl> getJspView(StudyImpl study)
        {
            return new JspView<>("/org/labkey/study/view/specimen/manageStatusOrder.jsp", study);
        }

        @Override
        public boolean handlePost(BulkEditForm form, BindException errors)
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

        @Override
        public ActionURL getSuccessURL(BulkEditForm bulkEditForm)
        {
            return new ActionURL(ManageStatusesAction.class, getContainer());
        }
    }

    private void updateRequestStatusOrder(Container container, int[] rowIds, boolean fixedRowIncluded)
    {
        // get a map of id to status objects before starting our updates; this prevents us from
        // blowing then repopulating the cache with each update:
        Map<Integer, SpecimenRequestStatus> idToStatus = getIdToRequestStatusMap(container);
        for (int i = 0; i < rowIds.length; i++)
        {
            int rowId = rowIds[i];
            int statusOrder = fixedRowIncluded ? i : i + 1;     // One caller doesn't have the first (fixed) status
            SpecimenRequestStatus status = idToStatus.get(rowId);
            if (status != null && !status.isSystemStatus() && status.getSortOrder() != statusOrder)
            {
                status = status.createMutable();
                status.setSortOrder(statusOrder);
                SpecimenRequestManager.get().updateRequestStatus(getUser(), status);
            }
        }
    }

    @RequiresPermission(ManageRequestStatusesPermission.class)
    public class ManageStatusesAction extends DisplayManagementSubpageAction<StatusEditForm>
    {
        public ManageStatusesAction()
        {
            super("manageStatuses", "Manage Specimen Request Statuses", "specimenRequest#status");
        }

        @Override
        protected JspView<StudyImpl> getJspView(StudyImpl study)
        {
            return new JspView<>("/org/labkey/study/view/specimen/manageStatuses.jsp", study);
        }

        @Override
        public boolean handlePost(StatusEditForm form, BindException errors)
        {
            getUtils().ensureSpecimenRequestsConfigured(false);

            int[] rowIds = form.getIds();
            String[] labels = form.getLabels();
            if (rowIds != null && rowIds.length > 0)
            {
                // get a map of id to status objects before starting our updates; this prevents us from
                // blowing then repopulating the cache with each update:
                Map<Integer, SpecimenRequestStatus> idToStatus = getIdToRequestStatusMap(getContainer());
                Set<Integer> finalStates = new HashSet<>(form.getFinalStateIds().length);
                for (int id : form.getFinalStateIds())
                    finalStates.add(id);
                Set<Integer> lockedSpecimenStates = new HashSet<>(form.getSpecimensLockedIds().length);
                for (int id : form.getSpecimensLockedIds())
                    lockedSpecimenStates.add(id);

                for (int i = 0; i < rowIds.length; i++)
                {
                    int rowId = rowIds[i];
                    SpecimenRequestStatus status = idToStatus.get(rowId);
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
                            SpecimenRequestManager.get().updateRequestStatus(getUser(), status);
                        }
                    }
                }
            }

            if (form.getNewLabel() != null && form.getNewLabel().length() > 0)
            {
                SpecimenRequestStatus status = new SpecimenRequestStatus();
                status.setLabel(form.getNewLabel());
                List<SpecimenRequestStatus> statuses = SpecimenRequestManager.get().getRequestStatuses(getContainer(), getUser());
                status.setSortOrder(statuses.size());
                status.setContainer(getContainer());
                status.setFinalState(form.isNewFinalState());
                status.setSpecimensLocked(form.isNewSpecimensLocked());
                SpecimenRequestManager.get().createRequestStatus(getUser(), status);
            }

            StatusSettings settings = SettingsManager.get().getStatusSettings(getContainer());
            if (settings.isUseShoppingCart() != form.isUseShoppingCart())
            {
                settings.setUseShoppingCart(form.isUseShoppingCart());
                SettingsManager.get().saveStatusSettings(getContainer(), settings);
            }
            return true;
        }

        @Override
        public ActionURL getSuccessURL(StatusEditForm form)
        {
            if (form.getNextPage() != null && form.getNextPage().length() > 0)
                return new ActionURL(form.getNextPage());
            else
                return getManageStudyURL();
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

    private Map<Integer, SpecimenRequestStatus> getIdToRequestStatusMap(Container container)
    {
        List<SpecimenRequestStatus> statuses = SpecimenRequestManager.get().getRequestStatuses(container, getUser());
        Map<Integer, SpecimenRequestStatus> idToStatus = new HashMap<>();
        for (SpecimenRequestStatus status : statuses)
            idToStatus.put(status.getRowId(), status);
        return idToStatus;
    }

    @RequiresPermission(ManageRequestStatusesPermission.class)
    public class DeleteActorAction extends FormHandlerAction<org.labkey.api.specimen.actions.IdForm>
    {
        @Override
        public void validateCommand(org.labkey.api.specimen.actions.IdForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(org.labkey.api.specimen.actions.IdForm form, BindException errors) throws Exception
        {
            SpecimenRequestActor actor = SpecimenRequestRequirementProvider.get().getActor(getContainer(), form.getId());
            if (actor != null)
                actor.delete();

            return true;
        }

        @Override
        public ActionURL getSuccessURL(org.labkey.api.specimen.actions.IdForm idForm)
        {
            return new ActionURL(ManageActorsAction.class, getContainer());
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class DeleteStatusAction extends FormHandlerAction<org.labkey.api.specimen.actions.IdForm>
    {
        @Override
        public void validateCommand(org.labkey.api.specimen.actions.IdForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(org.labkey.api.specimen.actions.IdForm form, BindException errors) throws Exception
        {
            List<SpecimenRequestStatus> statuses = SpecimenRequestManager.get().getRequestStatuses(getContainer(), getUser());
            SpecimenRequestStatus status = SpecimenRequestManager.get().getRequestStatus(getContainer(), form.getId());
            if (status != null)
            {
                SpecimenRequestManager.get().deleteRequestStatus(status);
                int[] remainingIds = new int[statuses.size() - 1];
                int idx = 0;
                for (SpecimenRequestStatus remainingStatus : statuses)
                {
                    if (remainingStatus.getRowId() != form.getId())
                        remainingIds[idx++] = remainingStatus.getRowId();
                }
                updateRequestStatusOrder(getContainer(), remainingIds, true);
            }
            return true;
        }

        @Override
        public ActionURL getSuccessURL(org.labkey.api.specimen.actions.IdForm idForm)
        {
            return new ActionURL(ManageStatusesAction.class, getContainer());
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

    @RequiresPermission(ManageNotificationsPermission.class)
    public class ManageNotificationsAction extends FormViewAction<RequestNotificationSettings>
    {
        @Override
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

        @Override
        public ModelAndView getView(RequestNotificationSettings form, boolean reshow, BindException errors)
        {
            if (!SettingsManager.get().isSpecimenRequestEnabled(getContainer(), false))
                throw new RedirectException(new ActionURL(SpecimenController.SpecimenRequestConfigRequired.class, getContainer()));

            // try to get the settings from the form, just in case this is a reshow:
            RequestNotificationSettings settings = form;
            if (settings == null || settings.getReplyTo() == null)
                settings = SettingsManager.get().getRequestNotificationSettings(getContainer());

            return new JspView<>("/org/labkey/study/view/specimen/manageNotifications.jsp", settings, errors);
        }

        @Override
        public boolean handlePost(RequestNotificationSettings settings, BindException errors)
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

            SettingsManager.get().saveRequestNotificationSettings(getContainer(), settings);
            return true;
        }

        @Override
        public ActionURL getSuccessURL(RequestNotificationSettings form)
        {
            return getManageStudyURL();
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("coordinateSpecimens#notify");
            _addManageStudy(root);
            root.addChild("Manage Notifications");
        }
    }
    
    private boolean isNullOrBlank(String toCheck)
    {
        return ((toCheck == null) || toCheck.equals(""));
    }

    @RequiresPermission(ManageDisplaySettingsPermission.class)
    public class ManageDisplaySettingsAction extends FormViewAction<DisplaySettingsForm>
    {
        @Override
        public void validateCommand(DisplaySettingsForm target, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(DisplaySettingsForm form, boolean reshow, BindException errors)
        {
            // try to get the settings from the form, just in case this is a reshow:
            DisplaySettings settings = form.getBean();
            if (settings == null || settings.getLastVialEnum() == null)
                settings = SettingsManager.get().getDisplaySettings(getContainer());

            return new JspView<>("/org/labkey/study/view/specimen/manageDisplay.jsp", settings);
        }

        @Override
        public boolean handlePost(DisplaySettingsForm form, BindException errors)
        {
            DisplaySettings settings = form.getBean();
            SettingsManager.get().saveDisplaySettings(getContainer(), settings);

            return true;
        }

        @Override
        public ActionURL getSuccessURL(DisplaySettingsForm displaySettingsForm)
        {
            return getManageStudyURL();
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic(new HelpTopic("specimenRequest#display"));
            _addManageStudy(root);
            root.addChild("Manage Specimen Display Settings");
        }
    }

    public static class DisplaySettingsForm extends BeanViewForm<DisplaySettings>
    {
        public DisplaySettingsForm()
        {
            super(DisplaySettings.class);
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class GetSpecimenExcelAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            List<Map<String,Object>> defaultSpecimens = new ArrayList<>();
            SimpleSpecimenImporter importer = new SimpleSpecimenImporter(getContainer(), getUser(),
                    getStudyRedirectIfNull().getTimepointType(), StudyService.get().getSubjectNounSingular(getContainer()));
            MapArrayExcelWriter xlWriter = new MapArrayExcelWriter(defaultSpecimens, importer.getSimpleSpecimenColumns());
            for (ExcelColumn col : xlWriter.getColumns())
                col.setCaption(importer.label(col.getName()));

            xlWriter.write(getViewContext().getResponse());

            return null;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
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

    public static class UpdateRequestabilityRulesForm implements HasViewContext
    {
        private ViewContext _viewContext;
        private String[] _ruleType;
        private String[] _ruleData;
        private String[] _markType;

        @Override
        public ViewContext getViewContext()
        {
            return _viewContext;
        }

        @Override
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

    @RequiresPermission(ManageRequestSettingsPermission.class)
    public class UpdateRequestabilityRulesAction extends MutatingApiAction<UpdateRequestabilityRulesForm>
    {
        @Override
        public ApiResponse execute(UpdateRequestabilityRulesForm form, BindException errors)
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

            return new ApiSimpleResponse(Collections.<String, Object>singletonMap("savedCount", rules.size()));
        }
    }

    @RequiresPermission(ManageRequestSettingsPermission.class)
    public class ConfigureRequestabilityRulesAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            getUtils().ensureSpecimenRequestsConfigured(false);
            return new JspView("/org/labkey/specimen/view/configRequestabilityRules.jsp");
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("coordinateSpecimens#requestability");
            _addManageStudy(root);
            root.addChild("Configure Requestability Rules");
        }
    }

    @RequiresPermission(RequestSpecimensPermission.class)
    public class ImportVialIdsAction extends AbstractQueryImportAction<org.labkey.api.specimen.actions.IdForm>
    {
        private int _requestId = -1;

        @Override
        protected void initRequest(org.labkey.api.specimen.actions.IdForm form) throws ServletException
        {
            _requestId = form.getId();
            setHasColumnHeaders(false);
            setImportMessage("Upload a list of Global Unique Identifiers from a TXT, CSV or Excel file or paste the list directly into the text box below. " +
                    "The list must have only one column and no header row.");
            setNoTableInfo();
            setHideTsvCsvCombo(true);
        }

        @Override
        public ModelAndView getView(org.labkey.api.specimen.actions.IdForm form, BindException errors) throws Exception
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
        protected ActionURL getSuccessURL(org.labkey.api.specimen.actions.IdForm form)
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

    @RequiresPermission(AdminPermission.class)
    public class CompleteSpecimenAction extends ReadOnlyApiAction<CompleteSpecimenForm>
    {
        @Override
        public ApiResponse execute(CompleteSpecimenForm form, BindException errors)
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

    private List<AjaxCompletion> getAjaxCompletions(Study study)
    {
        List<AjaxCompletion> completions = new ArrayList<>();
        String allString = "All " + PageFlowUtil.filter(StudyService.get().getSubjectNounPlural(study.getContainer())) +  " (Large Report)";

        completions.add(new AjaxCompletion(allString, allString));

        for (String ptid : StudyManager.getInstance().getParticipantIds(study, getViewContext().getUser()))
            completions.add(new AjaxCompletion(ptid, ptid));

        return completions;
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
