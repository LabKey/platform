/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.*;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.study.Location;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
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
import org.labkey.study.CohortFilter;
import org.labkey.study.SpecimenManager;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.BaseStudyController;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.LocationImpl;
import org.labkey.study.model.ParticipantDataset;
import org.labkey.study.model.ParticipantGroupManager;
import org.labkey.study.model.SpecimenRequest;
import org.labkey.study.model.SpecimenRequestActor;
import org.labkey.study.model.SpecimenRequestEvent;
import org.labkey.study.model.SpecimenRequestRequirement;
import org.labkey.study.model.SpecimenRequestStatus;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.Vial;
import org.labkey.study.query.SpecimenQueryView;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.study.security.permissions.ManageRequestsPermission;
import org.labkey.study.security.permissions.RequestSpecimensPermission;
import org.labkey.study.security.permissions.SetSpecimenCommentsPermission;
import org.labkey.study.specimen.notifications.ActorNotificationRecipientSet;
import org.labkey.study.specimen.notifications.DefaultRequestNotification;
import org.labkey.study.specimen.notifications.NotificationRecipientSet;
import org.labkey.study.specimen.settings.RepositorySettings;
import org.labkey.study.specimen.settings.RequestNotificationSettings;
import org.labkey.study.view.specimen.SpecimenRequestNotificationEmailTemplate;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.mvc.Controller;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.internet.InternetAddress;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.Writer;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
    private BaseStudyController _controller;


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

    private String urlFor(Class<? extends Controller> action)
    {
        return urlFor(action, null);
    }

    private String urlFor(Class<? extends Controller> action, Map<Enum, String> parameters)
    {
        ActionURL url = new ActionURL(action, getContainer());
        if (parameters != null)
        {
            for (Map.Entry<Enum, String> entry : parameters.entrySet())
                url.addParameter(entry.getKey(), entry.getValue());
        }
        return url.getLocalURIString();
    }

    public static boolean isCommentsMode(Container container, SpecimenQueryView.Mode selectedMode)
    {
        return (selectedMode == SpecimenQueryView.Mode.COMMENTS) ||
                (selectedMode == SpecimenQueryView.Mode.DEFAULT && SpecimenManager.getInstance().getDisplaySettings(container).isDefaultToCommentsMode());
    }

    public SpecimenQueryView getSpecimenQueryView(boolean showVials, boolean forExport, ParticipantDataset[] cachedFilterData, SpecimenQueryView.Mode viewMode, CohortFilter cohortFilter)
    {
        boolean commentsMode = isCommentsMode(getContainer(), viewMode);

        SpecimenQueryView gridView;
        RepositorySettings settings = SpecimenManager.getInstance().getRepositorySettings(getContainer());

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
                    String createRequestURL = urlFor(SpecimenController.ShowCreateSampleRequestAction.class,
                            Collections.singletonMap(SpecimenController.CreateSampleRequestForm.PARAMS.returnUrl,
                                    getViewContext().getActionURL().getLocalURIString()));

                    requestMenuButton.addMenuItem("Create New Request", null,
                            "if (verifySelected(" + jsRegionObject + ".form, '" + createRequestURL +
                            "', 'post', 'rows')) " + jsRegionObject + ".form.submit();");

                    if (getViewContext().getContainer().hasPermission(getViewContext().getUser(), ManageRequestsPermission.class) ||
                            SpecimenManager.getInstance().isSpecimenShoppingCartEnabled(getViewContext().getContainer()))
                    {
                        requestMenuButton.addMenuItem("Add To Existing Request", null,
                                "if (verifySelected(" + jsRegionObject + ".form, '#', " +
                                "'get', 'rows')) showRequestWindow(" + jsRegionObject + ".getChecked(), '" + (showVials ? SpecimenApiController.VialRequestForm.IdTypes.RowId
                                : SpecimenApiController.VialRequestForm.IdTypes.SpecimenHash) + "');");
                    }
                }
            }
            else
            {
                ActionURL endCommentsURL = getViewContext().getActionURL().clone();
                endCommentsURL.replaceParameter(SpecimenController.SampleViewTypeForm.PARAMS.viewMode, SpecimenQueryView.Mode.REQUESTS.name());
                requestMenuButton.addMenuItem("Enable Request Mode", endCommentsURL);
            }

            buttons.add(requestMenuButton);
        }

        if (getViewContext().getContainer().hasPermission(getUser(), SetSpecimenCommentsPermission.class))
        {
            boolean manualQCEnabled = SpecimenManager.getInstance().getDisplaySettings(getViewContext().getContainer()).isEnableManualQCFlagging();
            if (commentsMode)
            {
                MenuButton commentsMenuButton = new MenuButton("Comments" + (manualQCEnabled ? " and QC" : ""));
                final String jsRegionObject = DataRegion.getJavaScriptObjectReference(gridView.getSettings().getDataRegionName());
                String setCommentsURL = urlFor(SpecimenController.UpdateCommentsAction.class);
                NavTree setItem = commentsMenuButton.addMenuItem("Set Vial Comment " + (manualQCEnabled ? "or QC State " : "") + "for Selected", "#",
                        "if (verifySelected(" + jsRegionObject + ".form, '" + setCommentsURL +
                        "', 'post', 'rows')) " + jsRegionObject + ".form.submit(); return false;");
                setItem.setId("Comments:Set");

                String clearCommentsURL = urlFor(SpecimenController.ClearCommentsAction.class);
                NavTree clearItem = commentsMenuButton.addMenuItem("Clear Vial Comments for Selected", "#",
                        "if (verifySelected(" + jsRegionObject + ".form, '" + clearCommentsURL +
                        "', 'post', 'rows') && confirm('This will permanently clear comments for all selected vials.  " +
                                (manualQCEnabled ? "Quality control states will remain unchanged.  " : "" )+ "Continue?')) " +
                                jsRegionObject + ".form.submit();\nreturn false;");
                clearItem.setId("Comments:Clear");

                ActionURL endCommentsURL = getViewContext().getActionURL().clone();
                endCommentsURL.replaceParameter(SpecimenController.SampleViewTypeForm.PARAMS.viewMode, SpecimenQueryView.Mode.REQUESTS.name());
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
                enableCommentsURL.replaceParameter(SpecimenController.SampleViewTypeForm.PARAMS.viewMode, SpecimenQueryView.Mode.COMMENTS.name());
                ActionButton commentsButton = new ActionButton("Enable Comments" + (manualQCEnabled ? "/QC" : ""), enableCommentsURL);
                buttons.add(commentsButton);
            }
        }


        if (getViewContext().hasPermission(AdminPermission.class))
        {
            ActionButton upload = new ActionButton(new ActionURL(ShowUploadSpecimensAction.class, getContainer()), "Import Specimens");
            upload.setActionType(ActionButton.Action.GET);
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
        LocationImpl destLocation = StudyManager.getInstance().getLocation(specimenRequest.getContainer(), specimenRequest.getDestinationSiteId().intValue());
        relevantSites.put(destLocation.getRowId(), destLocation);
        for (Vial vial : specimenRequest.getVials())
        {
            LocationImpl location = SpecimenManager.getInstance().getCurrentLocation(vial);
            if (location != null && !relevantSites.containsKey(location.getRowId()))
                relevantSites.put(location.getRowId(), location);
        }

        SpecimenRequestActor[] allActors = SpecimenManager.getInstance().getRequirementsProvider().getActors(specimenRequest.getContainer());
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
            List<LocationImpl> locations = StudyManager.getInstance().getValidRequestingLocations(ctx.getContainer());
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
        RequestNotificationSettings settings =
                SpecimenManager.getInstance().getRequestNotificationSettings(request.getContainer());
        Address[] notify = settings.getNewRequestNotifyAddresses();
        if (notify != null && notify.length > 0)
        {
            SpecimenRequestEvent event = SpecimenManager.getInstance().createRequestEvent(getUser(), request,
                    SpecimenManager.RequestEventType.REQUEST_CREATED, null, Collections.emptyList());
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
        RequestNotificationSettings settings =
                SpecimenManager.getInstance().getRequestNotificationSettings(getContainer());

        SpecimenRequest specimenRequest = notification.getSampleRequest();
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
                SpecimenManager.getInstance().createRequestEvent(getUser(), notification.getRequirement(),
                        SpecimenManager.RequestEventType.NOTIFICATION_SENT, "Notification sent to " + recipient.getLongRecipientDescription(), null);
            else
                SpecimenManager.getInstance().createRequestEvent(getUser(), specimenRequest,
                        SpecimenManager.RequestEventType.NOTIFICATION_SENT, "Notification sent to " + recipient.getLongRecipientDescription(), null);
        }
    }

    public static class NotificationBean
    {
        private User _user;
        private String _specimenList;
        private String _studyName;
        private String _requestURI;
        private DefaultRequestNotification _notification;
        private boolean _includeSpecimensInBody;

        public NotificationBean(ViewContext context, DefaultRequestNotification notification, String specimenList, String studyName)
        {
            _notification = notification;
            _user = context.getUser();
            if (null != specimenList)
            {
                _specimenList = specimenList;
                _includeSpecimensInBody = true;
            }
            _studyName = studyName;
            _requestURI = new ActionURL(SpecimenController.ManageRequestAction.class, context.getContainer()).getURIString();
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
            return _notification.getSampleRequest().getRowId();
        }

        public String getModifyingUser()
        {
            return _user.getEmail();
        }

        public String getRequestingSiteName()
        {
            Location destLocation = StudyManager.getInstance().getLocation(_notification.getSampleRequest().getContainer(),
                    _notification.getSampleRequest().getDestinationSiteId());
            if (destLocation != null)
                return destLocation.getDisplayName();
            else
                return null;
        }

        public String getStatus()
        {
            SpecimenRequestStatus status = SpecimenManager.getInstance().getRequestStatus(_notification.getSampleRequest().getContainer(),
                    _notification.getSampleRequest().getStatusId());
            return status != null ? status.getLabel() : "Unknown";
        }

        public Container getContainer()
        {
            return _notification.getSampleRequest().getContainer();
        }

        public String getEventDescription()
        {
            return _notification.getEventSummary();
        }

        public String getRequestDescription()
        {
            return _notification.getSampleRequest().getComments();
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

    @NotNull
    public static <T> Set<T> intersect(@NotNull Set<T> left, @NotNull Set<T> right)
    {
        Set<T> intersection = new HashSet<>();
        for (T item : left)
        {
            if (right.contains(item))
                intersection.add(item);
        }
        return intersection;
    }

    @NotNull
    public static Collection<Integer> getPreferredProvidingLocations(Collection<List<Vial>> specimensBySample)
    {
        Set<Integer> locationIntersection = null;
        for (List<Vial> vials : specimensBySample)
        {
            Set<Integer> currentLocations = new HashSet<>();
            for (Vial vial : vials)
            {
                if (vial.getCurrentLocation() != null)
                    currentLocations.add(vial.getCurrentLocation());
            }
            if (locationIntersection == null)
                locationIntersection = currentLocations;
            else
            {
                locationIntersection = intersect(locationIntersection, currentLocations);
                if (locationIntersection.isEmpty())
                    return locationIntersection;
            }
        }
        if (null != locationIntersection)
            return locationIntersection;
        return Collections.emptySet();
    }

    public void ensureSpecimenRequestsConfigured(boolean checkExistingStatuses) throws ServletException
    {
        if (!SpecimenManager.getInstance().isSampleRequestEnabled(getContainer(), checkExistingStatuses))
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
                Vial current = SpecimenManager.getInstance().getVial(getContainer(), getUser(), requestedSampleId);
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
                Vial match = SpecimenManager.getInstance().getVial(container, user, globalUniqueId);
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
                    SpecimenManager.getInstance().getVialsForSampleHashes(getContainer(), getUser(),  formValues, onlyAvailable);
            List<Vial> vials = new ArrayList<>();
            for (List<Vial> vialList : keyToVialMap.values())
                vials.addAll(vialList);
            selectedVials = new ArrayList<>(vials);
        }
        else
            selectedVials = getSpecimensFromRowIds(formValues);
        return selectedVials;
    }

    public static class AmbiguousLocationException extends Exception
    {
        private Container _container;
        private Collection<Integer> _possibleLocationIds;
        private LocationImpl[] _possibleLocations = null;

        public AmbiguousLocationException(Container container, Collection<Integer> possibleLocationIds)
        {
            _container = container;
            _possibleLocationIds = possibleLocationIds;
        }

        public Collection<Integer> getPossibleLocationIds()
        {
            return _possibleLocationIds;
        }

        public LocationImpl[] getPossibleLocations()
        {
            if (_possibleLocations == null)
            {
                _possibleLocations = new LocationImpl[_possibleLocationIds.size()];
                int idx = 0;

                for (Integer id : _possibleLocationIds)
                    _possibleLocations[idx++] = StudyManager.getInstance().getLocation(_container, id.intValue());
            }
            return _possibleLocations;
        }
    }

    public static class RequestedSpecimens
    {
        private Collection<Integer> _providingLocationIds;
        private List<Vial> _vials;
        private List<Location> _providingLocations;

        public RequestedSpecimens(List<Vial> vials, Collection<Integer> providingLocationIds)
        {
            _vials = vials;
            _providingLocationIds = providingLocationIds;
        }

        public RequestedSpecimens(List<Vial> vials)
        {
            _vials = vials;
            _providingLocationIds = new HashSet<>();
            if (vials != null)
            {
                for (Vial vial : vials)
                    _providingLocationIds.add(vial.getCurrentLocation());
            }
        }

        public List<Location> getProvidingLocations()
        {
            if (_providingLocations == null)
            {
                if (_vials == null || _vials.size() == 0)
                    _providingLocations = Collections.emptyList();
                else
                {
                    Container container = _vials.get(0).getContainer();
                    _providingLocations = new ArrayList<>(_providingLocationIds.size());

                    for (Integer locationId : _providingLocationIds)
                        _providingLocations.add(StudyManager.getInstance().getLocation(container, locationId.intValue()));
                }
            }
            return _providingLocations;
        }

        public List<Vial> getVials()
        {
            return _vials;
        }
    }

    public RequestedSpecimens getRequestableByVialRowIds(Set<String> rowIds)
    {
        List<Vial> requestedSamples = getSpecimensFromRowIds(rowIds);
        return new RequestedSpecimens(requestedSamples);
    }

    public RequestedSpecimens getRequestableByVialGlobalUniqueIds(Set<String> globalUniqueIds)
    {
        List<Vial> requestedSamples = getSpecimensFromGlobalUniqueIds(globalUniqueIds);
        return new RequestedSpecimens(requestedSamples);
    }

    public RequestedSpecimens getRequestableBySampleHash(Set<String> formValues, Integer preferredLocation) throws AmbiguousLocationException
    {
        Map<String, List<Vial>> vialsByHash = SpecimenManager.getInstance().getVialsForSampleHashes(getContainer(), getUser(), formValues, true);

        if (vialsByHash == null || vialsByHash.isEmpty())
            return new RequestedSpecimens(Collections.emptyList());

        if (preferredLocation == null)
        {
            Collection<Integer> preferredLocations = getPreferredProvidingLocations(vialsByHash.values());
            if (preferredLocations.size() == 1)
                preferredLocation = preferredLocations.iterator().next();
            else if (preferredLocations.size() > 1)
                throw new AmbiguousLocationException(getContainer(), preferredLocations);
        }
        List<Vial> requestedSamples = new ArrayList<>(vialsByHash.size());

        int i = 0;
        Set<Integer> providingLocations = new HashSet<>();
        for (List<Vial> vials : vialsByHash.values())
        {
            Vial selectedVial = null;
            if (preferredLocation == null)
                selectedVial = vials.get(0);
            else
            {
                for (Iterator<Vial> it = vials.iterator(); it.hasNext() && selectedVial == null;)
                {
                    Vial vial = it.next();
                    if (vial.getCurrentLocation() != null && vial.getCurrentLocation().intValue() == preferredLocation.intValue())
                        selectedVial = vial;
                }
            }
            if (selectedVial == null)
                throw new IllegalStateException("Vial was not available from specified location " + preferredLocation);
            providingLocations.add(selectedVial.getCurrentLocation());
            requestedSamples.add(selectedVial);
        }
        return new RequestedSpecimens(requestedSamples, providingLocations);
    }
    
    public GridView getRequestEventGridView(HttpServletRequest request, BindException errors, SimpleFilter filter)
    {
        DataRegion rgn = new DataRegion();
        TableInfo tableInfoRequestEvent = StudySchema.getInstance().getTableInfoSampleRequestEvent();
        rgn.setTable(tableInfoRequestEvent);
        rgn.setColumns(tableInfoRequestEvent.getColumns("Created", "EntryType", "Comments", "CreatedBy", "EntityId"));
        rgn.getDisplayColumn("EntityId").setVisible(false);

        DataColumn commentsColumn = (DataColumn) rgn.getDisplayColumn("Comments");
        commentsColumn.setWidth("50%");
        commentsColumn.setPreserveNewlines(true);
        rgn.addDisplayColumn(new AttachmentDisplayColumn(request));
        rgn.setButtonBar(ButtonBar.BUTTON_BAR_EMPTY);
        GridView grid = new GridView(rgn, errors);
        grid.setFilter(filter);
        grid.setSort(new Sort("Created"));
        return grid;
    }

    private static class AttachmentDisplayColumn extends SimpleDisplayColumn
    {
        private HttpServletRequest _request;
        public AttachmentDisplayColumn(HttpServletRequest request)
        {
            super();
            _request = request;
            setCaption("Attachments");
        }

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
                    out.write("<img src=\"" + _request.getContextPath() + attachment.getFileIcon() + "\">&nbsp;");
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

        TableInfo tableInfoRequestEvent = StudySchema.getInstance().getTableInfoSampleRequestEvent();
        rgn.setTable(tableInfoRequestEvent);
        rgn.setColumns(tableInfoRequestEvent.getColumns("Created", "EntityId"));
        rgn.getDisplayColumn("EntityId").setVisible(false);
        rgn.getDisplayColumn("Created").setVisible(false);
        rgn.setShowBorders(true);
        rgn.setShowPagination(false);
        DisplayColumn attachmentDisplayColumn = new AttachmentDisplayColumn(request);
        rgn.addDisplayColumn(attachmentDisplayColumn);
        rgn.setButtonBar(ButtonBar.BUTTON_BAR_EMPTY);
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
        Results rs = dr.getResultSet(ctx);
        List<DisplayColumn> cols = dr.getDisplayColumns();
        TSVGridWriter tsv = new TSVGridWriter(rs, cols);
        tsv.setFilenamePrefix(getSpecimenListFileName(srcLocation, destLocation));
        return tsv;
    }

    public ExcelWriter getSpecimenListXlsWriter(SpecimenRequest specimenRequest, LocationImpl srcLocation,
                                                 LocationImpl destLocation, SpecimenController.LabSpecimenListsBean.Type type) throws SQLException, IOException
    {
        DataRegion dr = createDataRegionForWriters(specimenRequest);
        RenderContext ctx = new RenderContext(getViewContext());
        ctx.setContainer(specimenRequest.getContainer());
        ctx.setBaseFilter(getSpecimenListFilter(specimenRequest, srcLocation, type));
        Results rs = dr.getResultSet(ctx);
        List<DisplayColumn> cols = dr.getDisplayColumns();
        ExcelWriter xl = new ExcelWriter(rs, cols);
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


    public static boolean isFieldTrue(RenderContext ctx, String fieldName)
    {
        Object value = ctx.getRow().get(fieldName);
        if (value instanceof Integer)
            return ((Integer) value).intValue() != 0;
        else if (value instanceof Boolean)
            return ((Boolean) value).booleanValue();
        return false;
    }
}
