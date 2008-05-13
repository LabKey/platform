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

import org.apache.commons.lang.StringUtils;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.*;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.MailHelper;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;
import org.labkey.study.SampleManager;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.BaseStudyController;
import org.labkey.study.model.*;
import org.labkey.study.query.SpecimenQueryView;
import org.labkey.study.samples.notifications.ActorNotificationRecipientSet;
import org.labkey.study.samples.notifications.DefaultRequestNotification;
import org.labkey.study.samples.notifications.NotificationRecipientSet;
import org.labkey.study.samples.notifications.RequestNotification;
import org.springframework.web.servlet.mvc.Controller;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.internet.InternetAddress;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.Writer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * User: brittp
 * Date: Dec 20, 2007
 * Time: 11:19:46 AM
 */
public class SpecimenUtils
{
    private SpringSpecimenController _controller;

    public SpecimenUtils(SpringSpecimenController controller)
    {
        // private constructor to prevent external instantiation
        _controller = controller;
    }

    private ViewContext getViewContext()
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

    private Study getStudy() throws ServletException
    {
        return _controller.getStudy();
    }

    public SpecimenQueryView getSpecimenQueryView(boolean showVials, boolean forExport) throws ServletException, SQLException
    {
        return getSpecimenQueryView(showVials, forExport, null);
    }

    private String urlFor(Class<? extends Controller> action)
    {
        return new ActionURL(action, getContainer()).getLocalURIString();
    }

    public SpecimenQueryView getSpecimenQueryView(boolean showVials, boolean forExport, ParticipantDataset[] cachedFilterData) throws ServletException, SQLException
    {
        SpecimenQueryView gridView;
        SampleManager.RepositorySettings settings = SampleManager.getInstance().getRepositorySettings(getContainer());

        if (cachedFilterData != null)
            gridView = SpecimenQueryView.createView(getViewContext(), cachedFilterData, showVials);
        else
            gridView = SpecimenQueryView.createView(getViewContext(), showVials);
        gridView.setShowHistoryLinks(showVials && !forExport && !settings.isSimple());
        gridView.setDisableLowVialIndicators(forExport || settings.isSimple());
        gridView.setShowRecordSelectors(settings.isEnableRequests());
        gridView.setShowRReportButton(true);
        // if we're exporting, we can skip setting up the buttons:
        if (forExport)
            return gridView;

        List<DisplayElement> buttons = new ArrayList<DisplayElement>();

        if (settings.isEnableRequests())
        {
            MenuButton requestMenuButton = new MenuButton("Request Options");
            requestMenuButton.addMenuItem("View Existing Requests", urlFor(SpringSpecimenController.ViewRequestsAction.class));
            if (getViewContext().hasPermission(ACL.PERM_INSERT))
            {
                String dataRegionName = gridView.getSettings().getDataRegionName();
                String createRequestURL = urlFor(SpringSpecimenController.ShowCreateSampleRequestAction.class);
                requestMenuButton.addMenuItem("Create New Request", createRequestURL,
                        "if (verifySelected(document.forms[\"" + dataRegionName + "\"], \"" + createRequestURL +
                        "\", \"post\", \"rows\")) document.forms[\"" + dataRegionName + "\"].submit(); return false;");

                if (getUser().isAdministrator() || getViewContext().hasPermission(ACL.PERM_ADMIN) ||
                        SampleManager.getInstance().isSpecimenShoppingCartEnabled(getViewContext(). getContainer()))
                {
                    String addToRequestURL = urlFor(SpringSpecimenController.ShowAddToSampleRequestAction.class);
                    requestMenuButton.addMenuItem("Add To Existing Request", addToRequestURL,
                            "if (verifySelected(document.forms[\"" + dataRegionName + "\"], \"" + addToRequestURL +
                            "\", \"post\", \"rows\")) document.forms[\"" + dataRegionName + "\"].submit(); return false;");
                }
            }
            buttons.add(requestMenuButton);
        }

        if (getViewContext().hasPermission(ACL.PERM_ADMIN))
        {
            ActionButton upload = new ActionButton("button", "Import Specimens");
            upload.setURL(ActionURL.toPathString("Study-Samples", "showUploadSpecimens", getContainer()));
            buttons.add(upload);
        }

        if (settings.isEnableRequests() && getViewContext().hasPermission(ACL.PERM_INSERT))
        {
            buttons.add(new ButtonBarLineBreak());
            buttons.add(new ExcludeSiteDropDown());
        }

        gridView.setButtons(buttons);
        return gridView;
    }



    public List<ActorNotificationRecipientSet> getPossibleNotifications(SampleRequest sampleRequest) throws SQLException
    {
        List<ActorNotificationRecipientSet> possibleNotifications = new ArrayList<ActorNotificationRecipientSet>();
        // allow notification of all parties listed in the request requirements:
        for (SampleRequestRequirement requirement : sampleRequest.getRequirements())
            addIfNotPresent(requirement.getActor(), requirement.getSite(), possibleNotifications);

        // allow notification of all site-based actors at the destination site, and all study-wide actors:
        Map<Integer, Site> relevantSites = new HashMap<Integer, Site>();
        Site destSite = StudyManager.getInstance().getSite(sampleRequest.getContainer(), sampleRequest.getDestinationSiteId());
        relevantSites.put(destSite.getRowId(), destSite);
        for (Specimen specimen : sampleRequest.getSpecimens())
        {
            Site site = SampleManager.getInstance().getCurrentSite(specimen);
            if (site != null && !relevantSites.containsKey(site.getRowId()))
                relevantSites.put(site.getRowId(), site);
        }

        SampleRequestActor[] allActors = SampleManager.getInstance().getRequirementsProvider().getActors(sampleRequest.getContainer());
        // add study-wide actors and actors from all relevant sites:
        for (SampleRequestActor actor : allActors)
        {
            if (actor.isPerSite())
            {
                for (Site site : relevantSites.values())
                {
                    if (actor.isPerSite())
                        addIfNotPresent(actor, site, possibleNotifications);
                }
            }
            else
                addIfNotPresent(actor, null, possibleNotifications);
        }

        Collections.sort(possibleNotifications, new Comparator<ActorNotificationRecipientSet>()
        {
            public int compare(ActorNotificationRecipientSet first, ActorNotificationRecipientSet second)
            {
                String firstSite = first.getSite() != null ? first.getSite().getLabel() : "";
                String secondSite = second.getSite() != null ? second.getSite().getLabel() : "";
                int comp = firstSite.compareToIgnoreCase(secondSite);
                if (comp == 0)
                    comp = first.getActor().getLabel().compareToIgnoreCase(second.getActor().getLabel());
                return comp;
            }
        });
        return possibleNotifications;
    }

    private boolean addIfNotPresent(SampleRequestActor actor, Site site, List<ActorNotificationRecipientSet> list)
    {
        for (ActorNotificationRecipientSet actorSite : list)
        {
            if (actorSite.getActor().getRowId() == actor.getRowId())
            {
                if (actorSite.getSite() == null && site == null)
                    return false;
                else
                if (actorSite.getSite() != null && site != null && actorSite.getSite().getRowId() == site.getRowId())
                    return false;
            }
        }
        list.add(new ActorNotificationRecipientSet(actor, site));
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
            int siteId = null == StringUtils.trimToNull(excludeStr) ? 0 : Integer.parseInt(excludeStr);
            Site[] sites = StudyManager.getInstance().getSites(ctx.getContainer());
            for (Site site : sites)
            {
                out.write("<option value=\"");
                out.write(String.valueOf(site.getRowId()));
                out.write("\"");
                if (site.getRowId() == siteId)
                    out.write(" SELECTED ");
                out.write("\">");
                out.write(PageFlowUtil.filter(site.getDisplayName()));
                out.write("</option>");
            }
            out.write("</select>");
        }
    }

    public void writeExportData(SpecimenQueryView view, String type) throws Exception
    {
        if ("excel".equals(type))
            view.exportToExcel(_controller.getViewContext().getResponse());
        else if ("tsv".equals(type))
            view.exportToTsv(_controller.getViewContext().getResponse());
        else
            throw new IllegalArgumentException(type + " is not a supported export type.");
    }

    public void sendNewRequestNotifications(SampleRequest request) throws Exception
    {
        SampleManager.RequestNotificationSettings settings =
                SampleManager.getInstance().getRequestNotificationSettings(request.getContainer());
        Address[] notify = settings.getNewRequestNotifyAddresses();
        if (notify != null && notify.length > 0)
            sendNotification(new DefaultRequestNotification(request, Collections.singletonList(new NotificationRecipientSet(notify)), "New Request Created"));
    }

    public List<? extends NotificationRecipientSet> getNotifications(SampleRequest sampleRequest, String[] notificationIdPairs) throws SQLException
    {
        List<ActorNotificationRecipientSet> siteActors = new ArrayList<ActorNotificationRecipientSet>();
        if (notificationIdPairs == null || notificationIdPairs.length == 0)
            return siteActors;
        for (String notificationIdPair : notificationIdPairs)
            siteActors.add(ActorNotificationRecipientSet.getFromFormValue(sampleRequest.getContainer(), notificationIdPair));
        return siteActors;
    }

    public void sendNotification(RequestNotification notification) throws Exception
    {
        SampleRequest sampleRequest = notification.getSampleRequest();
        String specimenList = notification.getSpecimenListHTML(getViewContext());

        SampleManager.RequestNotificationSettings settings =
                SampleManager.getInstance().getRequestNotificationSettings(getContainer());
        MailHelper.ViewMessage message = MailHelper.createMessage(settings.getReplyTo(), null);
        String subject = settings.getSubjectSuffix().replaceAll("%requestId%", "" + sampleRequest.getRowId());
        message.setSubject(getStudy().getLabel() + ": " + subject);
        JspView<NotificationBean> notifyView = new JspView<NotificationBean>("/org/labkey/study/view/samples/notification.jsp",
                new NotificationBean(getViewContext(), notification, specimenList));
        message.setTemplateContent(getViewContext().getRequest(), notifyView, "text/html");

        boolean first = true;
        for (NotificationRecipientSet recipient : notification.getRecipients())
        {
            for (String email : recipient.getEmailAddresses())
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

                message.setRecipient(Message.RecipientType.TO, new InternetAddress(email));
                MailHelper.send(message);
            }
            if (notification.getRequirement() != null)
                SampleManager.getInstance().createRequestEvent(getUser(), notification.getRequirement(),
                        SampleManager.RequestEventType.NOTIFICATION_SENT, "Notification sent to " + recipient.getLongRecipientDescription(), null);
            else
                SampleManager.getInstance().createRequestEvent(getUser(), sampleRequest,
                        SampleManager.RequestEventType.NOTIFICATION_SENT, "Notification sent to " + recipient.getLongRecipientDescription(), null);
        }
    }

    public static class NotificationBean
    {
        private User _user;
        private String _baseServerURI;
        private String _specimenList;
        private RequestNotification _notification;

        public NotificationBean(ViewContext context, RequestNotification notification, String specimenList)
        {
            _notification = notification;
            _user = context.getUser();
            _baseServerURI = context.getActionURL().getBaseServerURI();
            _specimenList = specimenList;
        }

        public Attachment[] getAttachments()
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

        public String getRequestingSiteName() throws SQLException
        {
            Site destSite = StudyManager.getInstance().getSite(_notification.getSampleRequest().getContainer(),
                    _notification.getSampleRequest().getDestinationSiteId());
            if (destSite != null)
                return destSite.getDisplayName();
            else
                return null;
        }

        public String getStatus() throws SQLException
        {
            SampleRequestStatus status = SampleManager.getInstance().getRequestStatus(_notification.getSampleRequest().getContainer(),
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

        public String getBaseServerURI()
        {
            return _baseServerURI;
        }

        public String getRequestDescription()
        {
            return _notification.getSampleRequest().getComments();
        }

        public String getSpecimenList()
        {
            return _specimenList;
        }
    }

    public void ensureSpecimenRequestsConfigured() throws ServletException, SQLException
    {
        SampleRequestStatus[] statuses = SampleManager.getInstance().getRequestStatuses(getContainer(), getUser());
        if (statuses == null || statuses.length == 1)
            HttpView.throwRedirect(new ActionURL(SpringSpecimenController.SpecimenRequestConfigRequired.class, getContainer()));
    }

    public Specimen[] getSpecimensFromIds(int[] requestedSampleIds) throws SQLException, ServletException
    {
        Specimen[] requestedSpecimens = null;
        if (requestedSampleIds != null)
        {
            List<Specimen> specimens = new ArrayList<Specimen>();
            for (int requestedSampleId : requestedSampleIds)
            {
                Specimen current = SampleManager.getInstance().getSpecimen(getContainer(), requestedSampleId);
                if (current != null)
                    specimens.add(current);
            }
            requestedSpecimens = specimens.toArray(new Specimen[specimens.size()]);
        }
        return requestedSpecimens;

    }

    public Specimen[] getSpecimensFromIds(Collection<String> ids) throws SQLException, ServletException
    {
        return getSpecimensFromIds(BaseStudyController.toIntArray(ids));
    }

    public Specimen[] getSpecimensFromSamples(Collection<String> ids) throws SQLException, ServletException
    {
        List<SampleManager.SpecimenSummaryKey> keys = new ArrayList<SampleManager.SpecimenSummaryKey>();
        for (String s : ids)
            keys.add(new SampleManager.SpecimenSummaryKey(s));

        Map<SampleManager.SpecimenSummaryKey,List<Specimen>> map = SampleManager.getInstance().getVialsForSamples(getContainer(), keys.toArray(new SampleManager.SpecimenSummaryKey[ids.size()]));
        int[] rowIds = new int[map.size()];
        int i = 0;

        for (List<Specimen> specimenList : map.values())
            rowIds[i++] = specimenList.get(0).getRowId();
        
        return getSpecimensFromIds(rowIds);
    }

    public GridView getRequestEventGridView(HttpServletRequest request, SimpleFilter filter)
    {
        DataRegion rgn = new DataRegion();
        TableInfo tableInfoRequestEvent = StudySchema.getInstance().getTableInfoSampleRequestEvent();
        rgn.setTable(tableInfoRequestEvent);
        rgn.setColumns(tableInfoRequestEvent.getColumns("Created", "EntryType", "Comments", "CreatedBy", "EntityId"));
        rgn.getDisplayColumn("EntityId").setVisible(false);
        rgn.setShadeAlternatingRows(true);
        rgn.setShowColumnSeparators(true);
        DataColumn commentsColumn = (DataColumn) rgn.getDisplayColumn("Comments");
        commentsColumn.setWidth("50%");
        commentsColumn.setPreserveNewlines(true);
        rgn.addDisplayColumn(new AttachmentDisplayColumn(request));
        rgn.setButtonBar(ButtonBar.BUTTON_BAR_EMPTY);
        GridView grid = new GridView(rgn);
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
            Map cols = ctx.getRow();
            Attachment[] attachments;
            try
            {
                SampleRequestEvent event = ObjectFactory.Registry.getFactory(SampleRequestEvent.class).fromMap(cols);
                attachments = AttachmentService.get().getAttachments(event);
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }

            if (attachments != null && attachments.length > 0)
            {
                for (Attachment attachment : attachments)
                {
                    out.write("<a href=\"" + PageFlowUtil.filter(attachment.getDownloadUrl("Study-Samples")) + "\">");
                    out.write("<img src=\"" + _request.getContextPath() + attachment.getFileIcon() + "\">&nbsp;");
                    out.write(PageFlowUtil.filter(attachment.getName()));
                    out.write("</a><br>");
                }
            }
            else
                out.write("&nbsp;");
        }
    }

    public SimpleFilter getSpecimenListFilter(SampleRequest sampleRequest, Site srcSite,
                                              SpringSpecimenController.LabSpecimenListsBean.Type type) throws SQLException
    {
        SpringSpecimenController.LabSpecimenListsBean bean = new SpringSpecimenController.LabSpecimenListsBean(this, sampleRequest, type);
        List<Specimen> specimens = bean.getSpecimens(srcSite);
        Object[] params = new Object[specimens.size() + 1];
        params[params.length - 1] = sampleRequest.getContainer().getId();
        StringBuilder whereClause = new StringBuilder();
        whereClause.append("(");
        int i = 0;
        for (Specimen specimen : specimens)
        {
            if (i > 0)
                whereClause.append(" OR ");
            whereClause.append("RowId = ?");
            params[i++] = specimen.getRowId();
        }
        whereClause.append(") AND Container = ?");

        SimpleFilter filter = new SimpleFilter();
        filter.addWhereClause(whereClause.toString(), params);
        return filter;
    }

    private String getSpecimenListFileName(Site srcSite, Site destSite)
    {
        StringBuilder filename = new StringBuilder();
        filename.append(getShortSiteLabel(srcSite)).append("_to_").append(getShortSiteLabel(destSite));
        filename.append("_").append(DateUtil.formatDate());
        return filename.toString();
    }

    public TSVGridWriter getSpecimenListTsvWriter(SampleRequest sampleRequest, Site srcSite,
                                                   Site destSite, SpringSpecimenController.LabSpecimenListsBean.Type type) throws SQLException, IOException
    {
        DataRegion dr = new DataRegion();
        dr.setTable(StudySchema.getInstance().getTableInfoSpecimen());
        dr.setColumns(StudySchema.getInstance().getTableInfoSpecimen().getColumns(
                "SpecimenNumber, GlobalUniqueId, Ptid, VisitValue, Volume, VolumeUnits, " +
                        "DrawTimestamp, ProtocolNumber"));
        RenderContext ctx = new RenderContext(getViewContext());
        ctx.setContainer(sampleRequest.getContainer());
        ctx.setBaseFilter(getSpecimenListFilter(sampleRequest, srcSite, type));
        ResultSet rs = dr.getResultSet(ctx);
        List<DisplayColumn> cols = dr.getDisplayColumns();
        TSVGridWriter tsv = new TSVGridWriter(rs, cols);
        tsv.setFilenamePrefix(getSpecimenListFileName(srcSite, destSite));
        tsv.setColumnHeaderType(TSVGridWriter.ColumnHeaderType.propertyName);
        return tsv;
    }

    public ExcelWriter getSpecimenListXlsWriter(SampleRequest sampleRequest, Site srcSite,
                                                 Site destSite, SpringSpecimenController.LabSpecimenListsBean.Type type) throws SQLException, IOException
    {
        DataRegion dr = new DataRegion();
        dr.setTable(StudySchema.getInstance().getTableInfoSpecimen());
        dr.setColumns(StudySchema.getInstance().getTableInfoSpecimen().getColumns(
                "SpecimenNumber, GlobalUniqueId, Ptid, VisitValue, Volume, VolumeUnits, " +
                        "DrawTimestamp, ProtocolNumber"));
        RenderContext ctx = new RenderContext(getViewContext());
        ctx.setContainer(sampleRequest.getContainer());
        ctx.setBaseFilter(getSpecimenListFilter(sampleRequest, srcSite, type));
        ResultSet rs = dr.getResultSet(ctx);
        List<DisplayColumn> cols = dr.getDisplayColumns();
        ExcelWriter xl = new ExcelWriter(rs, cols);
        xl.setFilenamePrefix(getSpecimenListFileName(srcSite, destSite));
        return xl;
    }

    private String getShortSiteLabel(Site site)
    {
        String label;
        if (site.getLabel() != null && site.getLabel().length() > 0)
            label = site.getLabel().substring(0, Math.min(site.getLabel().length(), 15));
        else if (site.getLdmsLabCode() != null)
            label = "ldmsId" + site.getLdmsLabCode();
        else if (site.getLabwareLabCode() != null && site.getLabwareLabCode().length() > 0)
            label = "labwareId" + site.getLabwareLabCode();
        else
            label = "rowId" + site.getRowId();
        return label.replaceAll("\\W+", "_");
    }
    
}
