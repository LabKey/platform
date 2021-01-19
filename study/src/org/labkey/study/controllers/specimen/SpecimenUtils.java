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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.annotations.Migrate;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBarLineBreak;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.ExcelWriter;
import org.labkey.api.data.MenuButton;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SimpleDisplayColumn;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TSVGridWriter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.specimen.AmbiguousLocationException;
import org.labkey.api.specimen.RequestEventType;
import org.labkey.api.specimen.RequestedSpecimens;
import org.labkey.api.specimen.SpecimenManagerNew;
import org.labkey.api.specimen.SpecimenRequestManager;
import org.labkey.api.specimen.SpecimenRequestStatus;
import org.labkey.api.specimen.SpecimenSchema;
import org.labkey.api.specimen.Vial;
import org.labkey.api.specimen.actions.VialRequestForm;
import org.labkey.api.specimen.location.LocationImpl;
import org.labkey.api.specimen.location.LocationManager;
import org.labkey.api.specimen.model.SpecimenRequestActor;
import org.labkey.api.specimen.model.SpecimenRequestEvent;
import org.labkey.api.specimen.notifications.ActorNotificationRecipientSet;
import org.labkey.api.specimen.notifications.NotificationRecipientSet;
import org.labkey.api.specimen.requirements.SpecimenRequest;
import org.labkey.api.specimen.requirements.SpecimenRequestRequirement;
import org.labkey.api.specimen.requirements.SpecimenRequestRequirementProvider;
import org.labkey.api.specimen.security.permissions.ManageRequestsPermission;
import org.labkey.api.specimen.security.permissions.RequestSpecimensPermission;
import org.labkey.api.specimen.security.permissions.SetSpecimenCommentsPermission;
import org.labkey.api.specimen.settings.RepositorySettings;
import org.labkey.api.specimen.settings.RequestNotificationSettings;
import org.labkey.api.specimen.settings.SettingsManager;
import org.labkey.api.study.CohortFilter;
import org.labkey.api.study.Location;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.Button;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.MailHelper;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.emailTemplate.EmailTemplateService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DisplayElement;
import org.labkey.api.view.GridView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.ViewContext;
import org.labkey.study.controllers.BaseStudyController;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.api.study.model.ParticipantDataset;
import org.labkey.study.model.ParticipantGroupManager;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.api.specimen.query.SpecimenQueryView;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.api.specimen.notifications.DefaultRequestNotification;
import org.labkey.study.view.specimen.SpecimenRequestNotificationEmailTemplate;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.mvc.Controller;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.internet.InternetAddress;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.Writer;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: brittp
 * Date: Dec 20, 2007
 * Time: 11:19:46 AM
 */
public class SpecimenUtils
{
    private final BaseStudyController _controller;

    public SpecimenUtils(BaseStudyController controller)
    {
        // private constructor to prevent external instantiation
        _controller = controller;
    }

    public ViewContext getViewContext()
    {
        return _controller.getViewContext();
    }

    private Container getContainer()
    {
        return _controller.getViewContext().getContainer();
    }

    private User getUser()
    {
        return _controller.getViewContext().getUser();
    }

    private Study getStudy() throws IllegalStateException
    {
        return _controller.getStudyThrowIfNull();
    }

    public SpecimenQueryView getSpecimenQueryView(boolean showVials, boolean forExport, SpecimenQueryView.Mode viewMode, CohortFilter cohortFilter)
    {
        return getSpecimenQueryView(showVials, forExport, null, viewMode, cohortFilter);
    }

    private ActionURL urlFor(Class<? extends Controller> action)
    {
        return new ActionURL(action, getContainer());
    }

    public static boolean isCommentsMode(Container container, SpecimenQueryView.Mode selectedMode)
    {
        return (selectedMode == SpecimenQueryView.Mode.COMMENTS) ||
                (selectedMode == SpecimenQueryView.Mode.DEFAULT && SettingsManager.get().getDisplaySettings(container).isDefaultToCommentsMode());
    }

    public SpecimenQueryView getSpecimenQueryView(boolean showVials, boolean forExport, ParticipantDataset[] cachedFilterData, SpecimenQueryView.Mode viewMode, CohortFilter cohortFilter)
    {
        boolean commentsMode = isCommentsMode(getContainer(), viewMode);

        SpecimenQueryView gridView;
        RepositorySettings settings = SettingsManager.get().getRepositorySettings(getContainer());

        if (cachedFilterData != null)
        {
            gridView = SpecimenQueryView.createView(getViewContext(), cachedFilterData,
                    showVials ? SpecimenQueryView.ViewType.VIALS :
                                SpecimenQueryView.ViewType.SUMMARY);
        }
        else
        {
            gridView = SpecimenQueryView.createView(getViewContext(),
                            showVials ? SpecimenQueryView.ViewType.VIALS :
                                        SpecimenQueryView.ViewType.SUMMARY, cohortFilter);
        }
        gridView.setShowHistoryLinks(showVials && !forExport && !settings.isSimple());
        gridView.setDisableLowVialIndicators(forExport || settings.isSimple());
        gridView.setShowRecordSelectors(settings.isEnableRequests() || commentsMode); // Need to allow buttons
        gridView.setShowDeleteButton(false);

        // if we're exporting, we can skip setting up the buttons:
        if (forExport)
            return gridView;

        List<DisplayElement> buttons = new ArrayList<>();

        ActionButton ptidListButton = ParticipantGroupManager.getInstance().createParticipantGroupButton(getViewContext(), gridView.getSettings().getDataRegionName(), cohortFilter, false);
        if (ptidListButton != null)
            buttons.add(ptidListButton);

        if (settings.isEnableRequests())
        {
            MenuButton requestMenuButton = new MenuButton("Request Options");
            requestMenuButton.addMenuItem("View Existing Requests", urlFor(SpecimenController.ViewRequestsAction.class));
            if (!commentsMode)
            {
                if (getViewContext().getContainer().hasPermission(getViewContext().getUser(), RequestSpecimensPermission.class))
                {
                    final String jsRegionObject = DataRegion.getJavaScriptObjectReference(gridView.getSettings().getDataRegionName());
                    String createRequestURL = urlFor(SpecimenController.ShowCreateSpecimenRequestAction.class).addReturnURL(getViewContext().getActionURL()).toString();

                    requestMenuButton.addMenuItem("Create New Request",
                            "if (verifySelected(" + jsRegionObject + ".form, '" + createRequestURL +
                            "', 'post', 'rows')) " + jsRegionObject + ".form.submit();");

                    if (getViewContext().getContainer().hasPermission(getViewContext().getUser(), ManageRequestsPermission.class) ||
                            SettingsManager.get().isSpecimenShoppingCartEnabled(getViewContext().getContainer()))
                    {
                        requestMenuButton.addMenuItem("Add To Existing Request",
                                "if (verifySelected(" + jsRegionObject + ".form, '#', " +
                                "'get', 'rows')) { " + jsRegionObject + ".getSelected({success: function (data) { showRequestWindow(data.selected, '" + (showVials ? VialRequestForm.IdTypes.RowId
                                : VialRequestForm.IdTypes.SpecimenHash) + "');}})}");
                    }
                }
            }
            else
            {
                ActionURL endCommentsURL = getViewContext().getActionURL().clone();
                endCommentsURL.replaceParameter(SpecimenController.SpecimenViewTypeForm.PARAMS.viewMode, SpecimenQueryView.Mode.REQUESTS.name());
                requestMenuButton.addMenuItem("Enable Request Mode", endCommentsURL);
            }

            buttons.add(requestMenuButton);
        }

        if (getViewContext().getContainer().hasPermission(getUser(), SetSpecimenCommentsPermission.class))
        {
            boolean manualQCEnabled = SettingsManager.get().getDisplaySettings(getViewContext().getContainer()).isEnableManualQCFlagging();
            if (commentsMode)
            {
                MenuButton commentsMenuButton = new MenuButton("Comments" + (manualQCEnabled ? " and QC" : ""));
                final String jsRegionObject = DataRegion.getJavaScriptObjectReference(gridView.getSettings().getDataRegionName());
                String setCommentsURL = urlFor(SpecimenController.UpdateCommentsAction.class).toString();
                NavTree setItem = commentsMenuButton.addMenuItem("Set Vial Comment " + (manualQCEnabled ? "or QC State " : "") + "for Selected",
                        "if (verifySelected(" + jsRegionObject + ".form, '" + setCommentsURL +
                        "', 'post', 'rows')) " + jsRegionObject + ".form.submit(); return false;");
                setItem.setId("Comments:Set");

                String clearCommentsURL = urlFor(SpecimenController.ClearCommentsAction.class).toString();
                NavTree clearItem = commentsMenuButton.addMenuItem("Clear Vial Comments for Selected",
                        "if (verifySelected(" + jsRegionObject + ".form, '" + clearCommentsURL +
                        "', 'post', 'rows') && confirm('This will permanently clear comments for all selected vials. " +
                                (manualQCEnabled ? "Quality control states will remain unchanged. " : "" )+ "Continue?')) " +
                                jsRegionObject + ".form.submit();\nreturn false;");
                clearItem.setId("Comments:Clear");

                ActionURL endCommentsURL = getViewContext().getActionURL().clone();
                endCommentsURL.replaceParameter(SpecimenController.SpecimenViewTypeForm.PARAMS.viewMode, SpecimenQueryView.Mode.REQUESTS.name());
                NavTree exitItem = commentsMenuButton.addMenuItem("Exit Comments " + (manualQCEnabled ? "and QC " : "") + "mode", endCommentsURL);
                exitItem.setId("Comments:Exit");

                StudyImpl study = StudyManager.getInstance().getStudy(getContainer());
                String subjectNoun = StudyService.get().getSubjectNounSingular(getContainer());
                boolean addSep = true;
                if (study.getParticipantCommentDatasetId() != null && study.getParticipantCommentDatasetId() != -1)
                {
                    DatasetDefinition def = StudyManager.getInstance().getDatasetDefinition(study, study.getParticipantCommentDatasetId());
                    if (def != null && def.canWrite(getUser()))
                    {
                        if (addSep)
                        {
                            commentsMenuButton.addSeparator();
                            addSep = false;
                        }
                        NavTree ptidComments = commentsMenuButton.addMenuItem("Manage " + PageFlowUtil.filter(subjectNoun) + " Comments", new ActionURL(StudyController.DatasetAction.class, getContainer()).
                                addParameter("datasetId", study.getParticipantCommentDatasetId()));
                        ptidComments.setId("Comments:SetParticipant");
                    }
                }

                if (study.getParticipantVisitCommentDatasetId() != null && study.getParticipantVisitCommentDatasetId() != -1)
                {
                    DatasetDefinition def = StudyManager.getInstance().getDatasetDefinition(study, study.getParticipantVisitCommentDatasetId());
                    if (def != null && def.canWrite(getUser()))
                    {
                        if (addSep)
                        {
                            commentsMenuButton.addSeparator();
                            addSep = false;
                        }
                        NavTree ptidComments = commentsMenuButton.addMenuItem("Manage " + PageFlowUtil.filter(subjectNoun) + "/Visit Comments", new ActionURL(StudyController.DatasetAction.class, getContainer()).
                                addParameter("datasetId", study.getParticipantVisitCommentDatasetId()));
                        ptidComments.setId("Comments:SetParticipantVisit");
                    }
                }
                buttons.add(commentsMenuButton);
            }
            else
            {
                ActionURL enableCommentsURL = getViewContext().getActionURL().clone();
                enableCommentsURL.replaceParameter(SpecimenController.SpecimenViewTypeForm.PARAMS.viewMode, SpecimenQueryView.Mode.COMMENTS.name());
                ActionButton commentsButton = new ActionButton("Enable Comments" + (manualQCEnabled ? "/QC" : ""), enableCommentsURL);
                buttons.add(commentsButton);
            }
        }

        if (getViewContext().hasPermission(AdminPermission.class))
        {
            Button upload = new Button.ButtonBuilder("Import Specimens")
                    .href(new ActionURL(ShowUploadSpecimensAction.class, getContainer()))
                    .build();
            buttons.add(upload);
        }

        if (settings.isEnableRequests() && getViewContext().getContainer().hasPermission(getViewContext().getUser(), RequestSpecimensPermission.class))
        {
            buttons.add(new ButtonBarLineBreak());
            buttons.add(new ExcludeSiteDropDown());
        }

        gridView.setButtons(buttons);
        return gridView;
    }

    public List<ActorNotificationRecipientSet> getPossibleNotifications(SpecimenRequest specimenRequest)
    {
        List<ActorNotificationRecipientSet> possibleNotifications = new ArrayList<>();
        // allow notification of all parties listed in the request requirements:
        for (SpecimenRequestRequirement requirement : specimenRequest.getRequirements())
            addIfNotPresent(requirement.getActor(), requirement.getLocation(), possibleNotifications);

        // allow notification of all site-based actors at the destination site, and all study-wide actors:
        Map<Integer, LocationImpl> relevantSites = new HashMap<>();
        if (specimenRequest.getDestinationSiteId() == null)
        {
            throw new IllegalStateException("Request " + specimenRequest.getRowId() + " in folder " +
                    specimenRequest.getContainer().getPath() + " does not have a valid destination site id.");
        }
        LocationImpl destLocation = LocationManager.get().getLocation(specimenRequest.getContainer(), specimenRequest.getDestinationSiteId().intValue());
        relevantSites.put(destLocation.getRowId(), destLocation);
        for (Vial vial : specimenRequest.getVials())
        {
            LocationImpl location = LocationManager.get().getCurrentLocation(vial);
            if (location != null && !relevantSites.containsKey(location.getRowId()))
                relevantSites.put(location.getRowId(), location);
        }

        SpecimenRequestActor[] allActors = SpecimenRequestRequirementProvider.get().getActors(specimenRequest.getContainer());
        // add study-wide actors and actors from all relevant sites:
        for (SpecimenRequestActor actor : allActors)
        {
            if (actor.isPerSite())
            {
                for (LocationImpl location : relevantSites.values())
                {
                    if (actor.isPerSite())
                        addIfNotPresent(actor, location, possibleNotifications);
                }
            }
            else
                addIfNotPresent(actor, null, possibleNotifications);
        }

        possibleNotifications.sort((first, second) ->
        {
            String firstSite = first.getLocation() != null ? first.getLocation().getLabel() : "";
            String secondSite = second.getLocation() != null ? second.getLocation().getLabel() : "";
            int comp = firstSite.compareToIgnoreCase(secondSite);
            if (comp == 0)
            {
                String firstActorLabel = first.getActor().getLabel();
                if (firstActorLabel == null)
                    firstActorLabel = "";
                String secondActorLabel = second.getActor().getLabel();
                if (secondActorLabel == null)
                    secondActorLabel = "";
                comp = firstActorLabel.compareToIgnoreCase(secondActorLabel);
            }
            return comp;
        });
        return possibleNotifications;
    }

    private boolean addIfNotPresent(SpecimenRequestActor actor, LocationImpl location, List<ActorNotificationRecipientSet> list)
    {
        for (ActorNotificationRecipientSet actorSite : list)
        {
            if (actorSite.getActor().getRowId() == actor.getRowId())
            {
                if (actorSite.getLocation() == null && location == null)
                    return false;
                else
                if (actorSite.getLocation() != null && location != null && actorSite.getLocation().getRowId() == location.getRowId())
                    return false;
            }
        }
        list.add(new ActorNotificationRecipientSet(actor, location));
        return true;
    }

    public static final class ExcludeSiteDropDown extends DisplayElement
    {
        @Override
        public void render(RenderContext ctx, Writer out) throws IOException
        {
            ActionURL url = ctx.getViewContext().cloneActionURL();
            url.deleteParameter(SpecimenQueryView.PARAMS.excludeRequestedBySite.name());
            out.write("Hide Previously Requested By ");
            out.write("<select ");
            out.write("onchange=\"window.location=");
            out.write(PageFlowUtil.jsString(url.toString()+"&amp;excludeRequestedBySite="));
            out.write(" + this.options[this.selectedIndex].value;\"");
            out.write(">");
            out.write("<option value=''>&lt;Show All&gt;</option>");
            String excludeStr = ctx.getRequest().getParameter(SpecimenQueryView.PARAMS.excludeRequestedBySite.name());
            int locationId = null == StringUtils.trimToNull(excludeStr) ? 0 : Integer.parseInt(excludeStr);
            List<LocationImpl> locations = LocationManager.get().getValidRequestingLocations(ctx.getContainer());
            for (LocationImpl location : locations)
            {
                out.write("<option value=\"");
                out.write(String.valueOf(location.getRowId()));
                out.write("\"");
                if (location.getRowId() == locationId)
                    out.write(" SELECTED ");
                out.write("\">");
                out.write(PageFlowUtil.filter(location.getDisplayName()));
                out.write("</option>");
            }
            out.write("</select>");
        }
    }

    public void writeExportData(SpecimenQueryView view, String type) throws IOException
    {
        switch (type)
        {
            case "excel":
                view.exportToExcel(_controller.getViewContext().getResponse());
                break;
            case "tsv":
                view.exportToTsv(_controller.getViewContext().getResponse());
                break;
            default:
                throw new IllegalArgumentException(type + " is not a supported export type.");
        }
    }

    public void sendNewRequestNotifications(SpecimenRequest request, BindException errors) throws Exception
    {
        RequestNotificationSettings settings = SettingsManager.get().getRequestNotificationSettings(request.getContainer());
        Address[] notify = settings.getNewRequestNotifyAddresses();
        if (notify != null && notify.length > 0)
        {
            SpecimenRequestEvent event = SpecimenRequestManager.get().createRequestEvent(getUser(), request,
                    RequestEventType.REQUEST_CREATED, null, Collections.emptyList());
            DefaultRequestNotification notification = new DefaultRequestNotification(request, Collections.singletonList(new NotificationRecipientSet(notify)),
                    "New Request Created", event, null, null, getViewContext());
            sendNotification(notification, true, errors);
        }
    }

    public List<? extends NotificationRecipientSet> getNotifications(SpecimenRequest specimenRequest, String[] notificationIdPairs)
    {
        List<ActorNotificationRecipientSet> siteActors = new ArrayList<>();
        if (notificationIdPairs == null || notificationIdPairs.length == 0)
            return siteActors;
        for (String notificationIdPair : notificationIdPairs)
            siteActors.add(ActorNotificationRecipientSet.getFromFormValue(specimenRequest.getContainer(), notificationIdPair));
        return siteActors;
    }

    public void sendNotification(DefaultRequestNotification notification, boolean includeInactiveUsers, BindException errors) throws Exception
    {
        RequestNotificationSettings settings = SettingsManager.get().getRequestNotificationSettings(getContainer());

        SpecimenRequest specimenRequest = notification.getSpecimenRequest();
        String specimenList = null;
        if (RequestNotificationSettings.SpecimensAttachmentEnum.InEmailBody == settings.getSpecimensAttachmentEnum())
        {
            specimenList = notification.getSpecimenListHTML(getViewContext());
        }

        NotificationBean notificationBean = new NotificationBean(getViewContext(), notification, specimenList, getStudy().getLabel());
        SpecimenRequestNotificationEmailTemplate template = EmailTemplateService.get().getEmailTemplate(SpecimenRequestNotificationEmailTemplate.class, getContainer());
        template.init(notificationBean);
        if (settings.isReplyToCurrentUser())
            template.setOriginatingUser(getUser());

        MailHelper.MultipartMessage message = MailHelper.createMultipartMessage();
        template.renderSenderToMessage(message, getContainer());
        message.setEncodedHtmlContent(template.renderBody(getContainer()));
        message.setSubject(template.renderSubject(getContainer()));

        boolean first = true;
        for (NotificationRecipientSet recipient : notification.getRecipients())
        {
            for (String email : recipient.getEmailAddresses(includeInactiveUsers))
            {
                if (first)
                {
                    Address[] ccAddresses = settings.getCCAddresses();
                    if (ccAddresses != null && ccAddresses.length > 0)
                        message.setRecipients(Message.RecipientType.CC, ccAddresses);
                    first = false;
                }
                else
                    message.setRecipients(Message.RecipientType.CC, "");

                try
                {
                    message.setRecipient(Message.RecipientType.TO, new InternetAddress(email));
                    MailHelper.send(message, getUser(), getContainer());
                }
                catch (javax.mail.internet.AddressException | NullPointerException e)
                {
                    errors.reject(SpringActionController.ERROR_MSG, e.getMessage() == null ? e.toString() : e.getMessage());      // Bad address; also InternetAddress constructor can throw null
                }
            }
            if (notification.getRequirement() != null)
                SpecimenRequestManager.get().createRequestEvent(getUser(), notification.getRequirement(),
                        RequestEventType.NOTIFICATION_SENT, "Notification sent to " + recipient.getLongRecipientDescription(), null);
            else
                SpecimenRequestManager.get().createRequestEvent(getUser(), specimenRequest,
                        RequestEventType.NOTIFICATION_SENT, "Notification sent to " + recipient.getLongRecipientDescription(), null);
        }
    }

    public static class NotificationBean
    {
        private final DefaultRequestNotification _notification;
        private final User _user;
        private final String _specimenList;
        private final String _studyName;
        private final String _requestURI;

        private boolean _includeSpecimensInBody;

        public NotificationBean(ViewContext context, DefaultRequestNotification notification, String specimenList, String studyName)
        {
            _notification = notification;
            _user = context.getUser();
            _specimenList = specimenList;
            _studyName = studyName;
            _requestURI = new ActionURL(SpecimenController.ManageRequestAction.class, context.getContainer()).getURIString();

            _includeSpecimensInBody = null != specimenList;
        }

        public @NotNull List<Attachment> getAttachments()
        {
            return _notification.getAttachments();
        }

        public String getComments()
        {
            return _notification.getComments();
        }

        public int getRequestId()
        {
            return _notification.getSpecimenRequest().getRowId();
        }

        public String getModifyingUser()
        {
            return _user.getEmail();
        }

        public String getRequestingSiteName()
        {
            Location destLocation = LocationManager.get().getLocation(_notification.getSpecimenRequest().getContainer(),
                    _notification.getSpecimenRequest().getDestinationSiteId());
            if (destLocation != null)
                return destLocation.getDisplayName();
            else
                return null;
        }

        public String getStatus()
        {
            SpecimenRequestStatus status = SpecimenRequestManager.get().getRequestStatus(_notification.getSpecimenRequest().getContainer(),
                    _notification.getSpecimenRequest().getStatusId());
            return status != null ? status.getLabel() : "Unknown";
        }

        public Container getContainer()
        {
            return _notification.getSpecimenRequest().getContainer();
        }

        public String getEventDescription()
        {
            return _notification.getEventSummary();
        }

        public String getRequestDescription()
        {
            return _notification.getSpecimenRequest().getComments();
        }

        public String getSpecimenList()
        {
            return _specimenList;
        }

        public String getStudyName()
        {
            return _studyName;
        }

        public String getRequestURI()
        {
            return _requestURI;
        }

        public boolean getIncludeSpecimensInBody()
        {
            return _includeSpecimensInBody;
        }

        public void setIncludeSpecimensInBody(boolean includeSpecimensInBody)
        {
            _includeSpecimensInBody = includeSpecimensInBody;
        }

        public SpecimenRequestEvent getEvent()
        {
            return _notification.getEvent();
        }
    }

    public void ensureSpecimenRequestsConfigured(boolean checkExistingStatuses)
    {
        if (!SettingsManager.get().isSpecimenRequestEnabled(getContainer(), checkExistingStatuses))
            throw new RedirectException(new ActionURL(SpecimenController.SpecimenRequestConfigRequired.class, getContainer()));
    }


    public List<Vial> getSpecimensFromRowIds(long[] requestedSampleIds)
    {
        List<Vial> requestedVials = null;

        if (requestedSampleIds != null)
        {
            List<Vial> vials = new ArrayList<>();
            for (long requestedSampleId : requestedSampleIds)
            {
                Vial current = SpecimenManagerNew.get().getVial(getContainer(), getUser(), requestedSampleId);
                if (current != null)
                    vials.add(current);
            }
            requestedVials = vials;
        }

        return requestedVials;
    }

    public List<Vial> getSpecimensFromGlobalUniqueIds(Set<String> globalUniqueIds)
    {
        User user = getUser();
        Container container = getContainer();
        List<Vial> requestedVials = null;

        if (globalUniqueIds != null)
        {
            List<Vial> vials = new ArrayList<>();
            for (String globalUniqueId : globalUniqueIds)
            {
                Vial match = SpecimenManagerNew.get().getVial(container, user, globalUniqueId);
                if (match != null)
                    vials.add(match);
            }
            requestedVials = new ArrayList<>(vials);
        }

        return requestedVials;
    }

    public List<Vial> getSpecimensFromRowIds(Collection<String> ids)
    {
        return getSpecimensFromRowIds(BaseStudyController.toLongArray(ids));
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
            selectedVials = getSpecimensFromRowIds(formValues);
        return selectedVials;
    }

    public RequestedSpecimens getRequestableByVialRowIds(Set<String> rowIds)
    {
        Set<Long> ids = new HashSet<>();
        Arrays.stream(BaseStudyController.toLongArray(rowIds)).forEach(ids::add);
        List<Vial> requestedSpecimens = SpecimenManagerNew.get().getRequestableVials(getContainer(), getUser(), ids);
        return new RequestedSpecimens(requestedSpecimens);
    }

    public RequestedSpecimens getRequestableByVialGlobalUniqueIds(Set<String> globalUniqueIds)
    {
        List<Vial> requestedSpecimens = getSpecimensFromGlobalUniqueIds(globalUniqueIds);
        return new RequestedSpecimens(requestedSpecimens);
    }

    @Migrate // TODO: Refactor SpecimenUtils and callers should use SpecimenRequestManager directly
    public RequestedSpecimens getRequestableBySpecimenHash(Set<String> formValues, Integer preferredLocation) throws AmbiguousLocationException
    {
        return SpecimenRequestManager.get().getRequestableBySpecimenHash(getContainer(), getUser(), formValues, preferredLocation);
    }

    public GridView getRequestEventGridView(HttpServletRequest request, BindException errors, SimpleFilter filter)
    {
        DataRegion rgn = new DataRegion();
        TableInfo tableInfoRequestEvent = SpecimenSchema.get().getTableInfoSampleRequestEvent();
        rgn.setTable(tableInfoRequestEvent);
        rgn.setColumns(tableInfoRequestEvent.getColumns("Created", "EntryType", "Comments", "CreatedBy", "EntityId"));
        rgn.getDisplayColumn("EntityId").setVisible(false);

        DataColumn commentsColumn = (DataColumn) rgn.getDisplayColumn("Comments");
        commentsColumn.setWidth("50%");
        commentsColumn.setPreserveNewlines(true);
        rgn.addDisplayColumn(new AttachmentDisplayColumn(request));
        GridView grid = new GridView(rgn, errors);
        grid.setFilter(filter);
        grid.setSort(new Sort("Created"));
        return grid;
    }

    private static class AttachmentDisplayColumn extends SimpleDisplayColumn
    {
        private final HttpServletRequest _request;

        public AttachmentDisplayColumn(HttpServletRequest request)
        {
            super();
            _request = request;
            setCaption("Attachments");
        }

        @Override
        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            Map<String, Object> cols = ctx.getRow();
            SpecimenRequestEvent event = ObjectFactory.Registry.getFactory(SpecimenRequestEvent.class).fromMap(cols);
            Collection<Attachment> attachments = AttachmentService.get().getAttachments(event);

            if (!attachments.isEmpty())
            {
                for (Attachment attachment : attachments)
                {
                    out.write("<a href=\"" + PageFlowUtil.filter(SpecimenController.getDownloadURL(event, attachment.getName())) + "\">");
                    out.write("<img style=\"padding-right:4pt;\" src=\"" + _request.getContextPath() + attachment.getFileIcon() + "\">");
                    out.write(PageFlowUtil.filter(attachment.getName()));
                    out.write("</a><br>");
                }
            }
            else
                out.write("&nbsp;");
        }
    }

    public GridView getRequestEventAttachmentsGridView(HttpServletRequest request, BindException errors, int requestId)
    {
        DataRegion rgn = new DataRegion() {
            private int i = 0;

            @Override
            protected void renderTableRow(RenderContext ctx, Writer out, boolean showRecordSelectors, List<DisplayColumn> renderers, int rowIndex) throws SQLException, IOException
            {
                // This is so we don't show rows that have no attachments
                SpecimenRequestEvent event = ObjectFactory.Registry.getFactory(SpecimenRequestEvent.class).fromMap(ctx.getRow());
                List<Attachment> attachments = AttachmentService.get().getAttachments(event);
                if (!attachments.isEmpty())
                {
                    super.renderTableRow(ctx, out, showRecordSelectors, renderers, i++);
                }
            }
        };

        TableInfo tableInfoRequestEvent = SpecimenSchema.get().getTableInfoSampleRequestEvent();
        rgn.setTable(tableInfoRequestEvent);
        rgn.setColumns(tableInfoRequestEvent.getColumns("Created", "EntityId"));
        rgn.getDisplayColumn("EntityId").setVisible(false);
        rgn.getDisplayColumn("Created").setVisible(false);
        rgn.setShowBorders(true);
        rgn.setShowPagination(false);
        DisplayColumn attachmentDisplayColumn = new AttachmentDisplayColumn(request);
        rgn.addDisplayColumn(attachmentDisplayColumn);
        GridView grid = new GridView(rgn, errors);
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("RequestId"), requestId);
        filter.addCondition(FieldKey.fromString("RequirementId"), null, CompareType.ISBLANK);     // if null, then event is NOT a requirement
        grid.setFilter(filter);
        FieldKey fieldKey = FieldKey.fromString("Created");
        Sort sort = new Sort();
        sort.insertSortColumn(fieldKey, Sort.SortDirection.DESC);
        grid.setSort(sort);
        return grid;
    }

    public SimpleFilter getSpecimenListFilter(SpecimenRequest specimenRequest, LocationImpl srcLocation, SpecimenController.LabSpecimenListsBean.Type type)
    {
        SpecimenController.LabSpecimenListsBean bean = new SpecimenController.LabSpecimenListsBean(this, specimenRequest, type);
        List<Vial> vials = bean.getSpecimens(srcLocation);
        Object[] params = new Object[vials.size() + 1];
        params[params.length - 1] = specimenRequest.getContainer().getId();
        StringBuilder whereClause = new StringBuilder();
        whereClause.append("(");
        int i = 0;
        for (Vial vial : vials)
        {
            if (i > 0)
                whereClause.append(" OR ");
            whereClause.append("RowId = ?");
            params[i++] = vial.getRowId();
        }
        whereClause.append(") AND Container = ?");

        SimpleFilter filter = new SimpleFilter();
        filter.addWhereClause(whereClause.toString(), params, FieldKey.fromParts("RowId"), FieldKey.fromParts("Container"));
        return filter;
    }

    private String getSpecimenListFileName(LocationImpl srcLocation, LocationImpl destLocation)
    {
        StringBuilder filename = new StringBuilder();
        filename.append(getShortSiteLabel(srcLocation)).append("_to_").append(getShortSiteLabel(destLocation));
        filename.append("_").append(DateUtil.formatDateISO8601());
        return filename.toString();
    }

    public TSVGridWriter getSpecimenListTsvWriter(SpecimenRequest specimenRequest, LocationImpl srcLocation,
                                                   LocationImpl destLocation, SpecimenController.LabSpecimenListsBean.Type type) throws SQLException, IOException
    {
        DataRegion dr = createDataRegionForWriters(specimenRequest);
        RenderContext ctx = new RenderContext(getViewContext());
        ctx.setContainer(specimenRequest.getContainer());
        ctx.setBaseFilter(getSpecimenListFilter(specimenRequest, srcLocation, type));
        List<DisplayColumn> cols = dr.getDisplayColumns();
        TSVGridWriter tsv = new TSVGridWriter(()->dr.getResults(ctx), cols);
        tsv.setFilenamePrefix(getSpecimenListFileName(srcLocation, destLocation));
        return tsv;
    }

    public ExcelWriter getSpecimenListXlsWriter(SpecimenRequest specimenRequest, LocationImpl srcLocation,
                                                 LocationImpl destLocation, SpecimenController.LabSpecimenListsBean.Type type)
    {
        DataRegion dr = createDataRegionForWriters(specimenRequest);
        RenderContext ctx = new RenderContext(getViewContext());
        ctx.setContainer(specimenRequest.getContainer());
        ctx.setBaseFilter(getSpecimenListFilter(specimenRequest, srcLocation, type));
        List<DisplayColumn> cols = dr.getDisplayColumns();
        ExcelWriter xl = new ExcelWriter(() -> dr.getResults(ctx), cols);
        xl.setFilenamePrefix(getSpecimenListFileName(srcLocation, destLocation));
        return xl;
    }

    private DataRegion createDataRegionForWriters(SpecimenRequest specimenRequest)
    {
        DataRegion dr = new DataRegion();
        Container container = specimenRequest.getContainer();
        StudyQuerySchema querySchema = StudyQuerySchema.createSchema(StudyManager.getInstance().getStudy(container), getViewContext().getUser(), true);
        TableInfo table = querySchema.getTable(StudyQuerySchema.LOCATION_SPECIMEN_LIST_TABLE_NAME, true);
        QueryDefinition queryDef = querySchema.getQueryDefForTable(StudyQuerySchema.LOCATION_SPECIMEN_LIST_TABLE_NAME);
        CustomView defaultView = QueryService.get().getCustomView(getViewContext().getUser(), container, getViewContext().getUser(), querySchema.getName(), queryDef.getName(), null);
        List<ColumnInfo> columns = queryDef.getColumns(defaultView, table);
        dr.setTable(table);
        dr.setColumns(columns);
        return dr;
    }

    private String getShortSiteLabel(LocationImpl location)
    {
        String label;
        if (location.getLabel() != null && location.getLabel().length() > 0)
            label = location.getLabel().substring(0, Math.min(location.getLabel().length(), 15));
        else if (location.getLdmsLabCode() != null)
            label = "ldmsId" + location.getLdmsLabCode();
        else if (location.getLabwareLabCode() != null && location.getLabwareLabCode().length() > 0)
            label = "labwareId" + location.getLabwareLabCode();
        else
            label = "rowId" + location.getRowId();
        return label.replaceAll("\\W+", "_");
    }
}
