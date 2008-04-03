<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.study.controllers.samples.SpringSpecimenController"%>
<%@ page import="org.labkey.study.model.Site"%>
<%@ page import="org.labkey.study.model.Specimen"%>
<%@ page import="org.labkey.study.samples.notifications.ActorNotificationRecipientSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<SpringSpecimenController.LabSpecimenListsBean> me = (JspView<SpringSpecimenController.LabSpecimenListsBean>) HttpView.currentView();
    SpringSpecimenController.LabSpecimenListsBean bean = me.getModelBean();
    boolean originating = bean.getType() == SpringSpecimenController.LabSpecimenListsBean.Type.ORIGINATING;
%>
<form action="emailLabSpecimenLists.post" method="POST" enctype="multipart/form-data">
<input type="hidden" name="id" value="<%= bean.getSampleRequest().getRowId() %>">
<input type="hidden" name="listType" value="<%= bean.getType().toString() %>">
<%
    if (!bean.isRequirementsComplete())
    {
%>
    <span class="labkey-error">
        WARNING: The requirements for this request are incomplete.<br>
        Sending specimen lists before completion of all requirements is not recommended.
    </span>
<%
    }
%>

<table cellspacing="0" cellpadding="4" class="normal">
    <tr>
        <th align="left"><%= originating ? "Originating" : "Providing" %> Location</th>
        <th align="left">Download options</th>
        <th align="left">Specimen IDs</th>
        <th align="left">Email Recipients</th>
    </tr>
    <%
        int rowCount = 0;
        for (Site site : bean.getLabs())
        {
            String downloadURLPrefix = "downloadSpecimenList.view?id=" + bean.getSampleRequest().getRowId() +
                    "&destSiteId=" + bean.getSampleRequest().getDestinationSiteId() +
                    "&listType=" + bean.getType().toString() +
                    "&sourceSiteId=" + site.getRowId() + "&export=";
    %>
    <tr bgcolor="<%= rowCount++ % 2 == 0 ? "#EEEEEE" : "#FFFFFF"%>" valign="top">
        <td><%= site.getDisplayName()%></td>
        <td>
            <table>
                <tr>
                    <td><%= textLink("Export to Excel", downloadURLPrefix + "xls") %></td>
                </tr>
                <tr>
                    <td><%= textLink("Export to text file", downloadURLPrefix + "tsv") %></td>
                </tr>
            </table>
            <br>
        </td>
        <td>
            <table>
                <%
                    for (Specimen specimen : bean.getSpecimens(site))
                    {
                %>
                    <tr valign="top">
                        <td><%= specimen.getGlobalUniqueId()%></td>
                    </tr>
                <%
                    }
                %>
            </table>
        </td>
        <td>
            <table>
                <%
                    for (ActorNotificationRecipientSet possibleNotification : bean.getPossibleNotifications())
                    {
                        Site notifySite = possibleNotification.getSite();
                        if (notifySite != null &&
                                notifySite.getRowId() != bean.getSampleRequest().getDestinationSiteId() &&
                                notifySite.getRowId() != site.getRowId())
                            continue;

                        boolean hasEmailAddresses = possibleNotification.getEmailAddresses().length > 0;

                %>
                <tr valign="top">
                    <td valign="middle">
                        <input type="checkbox"
                               name="notify"
                               value="<%= site.getRowId() %>,<%= possibleNotification.getFormValue() %>" <%= hasEmailAddresses ? "" : "DISABLED" %>>
                    </td>
                    <td valign="middle">
                        <%= h(possibleNotification.getShortRecipientDescription())%><%= hasEmailAddresses ?
                            helpPopup("Group Members", possibleNotification.getEmailAddresses("<br>") + "<br>" +
                                    possibleNotification.getConfigureEmailsLinkHTML(), true) :
                            " " + possibleNotification.getConfigureEmailsLinkHTML() %>
                    </td>
                    <td valign="middle">
                        <%= notifySite != null && notifySite.getRowId() == bean.getSampleRequest().getDestinationSiteId() ? "Requesting&nbsp;Location&nbsp;" : "" %>
                        <%= notifySite != null && notifySite.getRowId() == site.getRowId() ? bean.getType().getDisplay() + "&nbsp;Location" : "" %>
                    </td>
                </tr>
                <%
                    }
                %>
            </table>
        </td>
    </tr>
    <%
        }
    %>
</table>
<table class="normal">
    <tr>
        <td>&nbsp;</td>
        <td class='ms-searchform'>
            Any comments will be included in all notifications that are sent.  Additionally, they<br>
            will appear in the request history.
        </td>
    </tr>
    <tr>
        <th align="right">Comments</th>
        <td><textarea name="comments" rows="10" cols="50"></textarea></td>
    </tr>
    <tr>
        <td>&nbsp;</td>
        <td class='ms-searchform'>
            Selecting an attachment format will automatically attach appropriate specimen lists to<br>
            any notifications. It is not necessary to attach the lists manually.</td>
    </tr>
    <tr>
        <th>Attachment Format(s)</th>
        <td>
            <input type="checkbox" name="sendXls">Excel<br>
            <input type="checkbox" name="sendTsv">Text<br>
        </td>
    </tr>
    <tr>
        <td>&nbsp;</td>
        <td class='ms-searchform'>
            Supporting documents are optional.  If added, they will be available to all notification<br>
            recipients, as well as in the request history.</td>
    </tr>
    <tr>
        <th align="right">Supporting<br>Documents</th>
        <td>
            <input type="file" size="40" name="formFiles[0]"><br>
            <input type="file" size="40" name="formFiles[1]"><br>
            <input type="file" size="40" name="formFiles[2]"><br>
            <input type="file" size="40" name="formFiles[3]"><br>
            <input type="file" size="40" name="formFiles[4]">
        </td>
    </tr>
    <tr>
        <th>&nbsp;</th>
        <td><%= buttonImg("Send Email")%> <%= buttonLink("Cancel", "manageRequest.view?id=" + bean.getSampleRequest().getRowId())%></td>
    </tr>
</table>
</form>