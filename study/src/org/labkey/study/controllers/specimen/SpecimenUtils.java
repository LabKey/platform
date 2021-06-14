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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBarLineBreak;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.MenuButton;
import org.labkey.api.data.RenderContext;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.specimen.RequestEventType;
import org.labkey.api.specimen.SpecimenMigrationService;
import org.labkey.api.specimen.SpecimenRequestManager;
import org.labkey.api.specimen.actions.IdTypes;
import org.labkey.api.specimen.actions.SpecimenViewTypeForm;
import org.labkey.api.specimen.location.LocationImpl;
import org.labkey.api.specimen.location.LocationManager;
import org.labkey.api.specimen.model.SpecimenRequestEvent;
import org.labkey.api.specimen.notifications.DefaultRequestNotification;
import org.labkey.api.specimen.notifications.NotificationRecipientSet;
import org.labkey.api.specimen.query.SpecimenQueryView;
import org.labkey.api.specimen.requirements.SpecimenRequest;
import org.labkey.api.specimen.security.permissions.ManageRequestsPermission;
import org.labkey.api.specimen.security.permissions.RequestSpecimensPermission;
import org.labkey.api.specimen.security.permissions.SetSpecimenCommentsPermission;
import org.labkey.api.specimen.settings.RepositorySettings;
import org.labkey.api.specimen.settings.RequestNotificationSettings;
import org.labkey.api.specimen.settings.SettingsManager;
import org.labkey.api.specimen.view.NotificationBean;
import org.labkey.api.specimen.view.SpecimenRequestNotificationEmailTemplate;
import org.labkey.api.study.CohortFilter;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.model.ParticipantDataset;
import org.labkey.api.util.Button;
import org.labkey.api.util.MailHelper;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.emailTemplate.EmailTemplateService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DisplayElement;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.ParticipantGroupManager;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.mvc.Controller;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.internet.InternetAddress;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * User: brittp
 * Date: Dec 20, 2007
 * Time: 11:19:46 AM
 */
public class SpecimenUtils
{
    private final ViewContext _context;

    public SpecimenUtils(ViewContext context)
    {
        _context = context;
    }

    public ViewContext getViewContext()
    {
        return _context;
    }

    private Container getContainer()
    {
        return _context.getContainer();
    }

    private User getUser()
    {
        return _context.getUser();
    }

    private Study getStudy() throws IllegalStateException
    {
        return SpecimenController.getStudyThrowIfNull(getContainer());
    }

    private ActionURL urlFor(Class<? extends Controller> action)
    {
        return new ActionURL(action, getContainer());
    }

    private static boolean isCommentsMode(Container container, SpecimenQueryView.Mode selectedMode)
    {
        return (selectedMode == SpecimenQueryView.Mode.COMMENTS) ||
                (selectedMode == SpecimenQueryView.Mode.DEFAULT && SettingsManager.get().getDisplaySettings(container).isDefaultToCommentsMode());
    }

    public SpecimenQueryView getSpecimenQueryView(boolean showVials, boolean forExport, @Nullable Collection<? extends ParticipantDataset> cachedFilterData, SpecimenQueryView.Mode viewMode, CohortFilter cohortFilter)
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
            requestMenuButton.addMenuItem("View Existing Requests", SpecimenMigrationService.get().getViewRequestsURL(getContainer()));
            if (!commentsMode)
            {
                if (getViewContext().getContainer().hasPermission(getViewContext().getUser(), RequestSpecimensPermission.class))
                {
                    final String jsRegionObject = DataRegion.getJavaScriptObjectReference(gridView.getSettings().getDataRegionName());
                    String createRequestURL = urlFor(SpecimenMigrationService.get().getShowCreateSpecimenRequestActionClass()).addReturnURL(getViewContext().getActionURL()).toString();

                    requestMenuButton.addMenuItem("Create New Request",
                            "if (verifySelected(" + jsRegionObject + ".form, '" + createRequestURL +
                            "', 'post', 'rows')) " + jsRegionObject + ".form.submit();");

                    if (getViewContext().getContainer().hasPermission(getViewContext().getUser(), ManageRequestsPermission.class) ||
                            SettingsManager.get().isSpecimenShoppingCartEnabled(getViewContext().getContainer()))
                    {
                        requestMenuButton.addMenuItem("Add To Existing Request",
                                "if (verifySelected(" + jsRegionObject + ".form, '#', " +
                                "'get', 'rows')) { " + jsRegionObject + ".getSelected({success: function (data) { showRequestWindow(data.selected, '" + (showVials ? IdTypes.RowId
                                : IdTypes.SpecimenHash) + "');}})}");
                    }
                }
            }
            else
            {
                ActionURL endCommentsURL = getViewContext().getActionURL().clone();
                endCommentsURL.replaceParameter(SpecimenViewTypeForm.PARAMS.viewMode, SpecimenQueryView.Mode.REQUESTS.name());
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
                String setCommentsURL = urlFor(SpecimenMigrationService.get().getUpdateCommentsActionClass()).toString();
                NavTree setItem = commentsMenuButton.addMenuItem("Set Vial Comment " + (manualQCEnabled ? "or QC State " : "") + "for Selected",
                        "if (verifySelected(" + jsRegionObject + ".form, '" + setCommentsURL +
                        "', 'post', 'rows')) " + jsRegionObject + ".form.submit(); return false;");
                setItem.setId("Comments:Set");

                String clearCommentsURL = urlFor(SpecimenMigrationService.get().getClearCommentsActionClass()).toString();
                NavTree clearItem = commentsMenuButton.addMenuItem("Clear Vial Comments for Selected",
                        "if (verifySelected(" + jsRegionObject + ".form, '" + clearCommentsURL +
                        "', 'post', 'rows') && confirm('This will permanently clear comments for all selected vials. " +
                                (manualQCEnabled ? "Quality control states will remain unchanged. " : "" )+ "Continue?')) " +
                                jsRegionObject + ".form.submit();\nreturn false;");
                clearItem.setId("Comments:Clear");

                ActionURL endCommentsURL = getViewContext().getActionURL().clone();
                endCommentsURL.replaceParameter(SpecimenViewTypeForm.PARAMS.viewMode, SpecimenQueryView.Mode.REQUESTS.name());
                NavTree exitItem = commentsMenuButton.addMenuItem("Exit Comments " + (manualQCEnabled ? "and QC " : "") + "mode", endCommentsURL);
                exitItem.setId("Comments:Exit");

                StudyImpl study = StudyManager.getInstance().getStudy(getContainer());
                String subjectNoun = StudyService.get().getSubjectNounSingular(getContainer());
                boolean addSep = true;
                if (study.getParticipantCommentDatasetId() != null && study.getParticipantCommentDatasetId() != -1)
                {
                    DatasetDefinition def = StudyManager.getInstance().getDatasetDefinition(study, study.getParticipantCommentDatasetId());
                    if (def != null && def.canUpdate(getUser()))
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
                    if (def != null && def.canUpdate(getUser()))
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
                enableCommentsURL.replaceParameter(SpecimenViewTypeForm.PARAMS.viewMode, SpecimenQueryView.Mode.COMMENTS.name());
                ActionButton commentsButton = new ActionButton("Enable Comments" + (manualQCEnabled ? "/QC" : ""), enableCommentsURL);
                buttons.add(commentsButton);
            }
        }

        if (getViewContext().hasPermission(AdminPermission.class))
        {
            Button upload = new Button.ButtonBuilder("Import Specimens")
                .href(SpecimenMigrationService.get().getUploadSpecimensURL(getContainer()))
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
            out.write(PageFlowUtil.jsString(url + "&amp;excludeRequestedBySite="));
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

    private void sendNotification(DefaultRequestNotification notification, boolean includeInactiveUsers, BindException errors) throws Exception
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
}
