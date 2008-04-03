<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.model.SampleRequestStatus"%>
<%@ page import="org.labkey.study.SampleManager"%>
<%@ page import="java.util.List"%>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.study.samples.notifications.ActorNotificationRecipientSet" %>
<%@ page import="org.labkey.study.controllers.samples.SpringSpecimenController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<SpringSpecimenController.ManageRequestBean> me = (JspView<SpringSpecimenController.ManageRequestBean>) HttpView.currentView();
    SpringSpecimenController.ManageRequestBean bean = me.getModelBean();
    ViewContext context = me.getViewContext();
    SampleRequestStatus[] statuses = SampleManager.getInstance().getRequestStatuses(context.getContainer(), context.getUser());
%>
<form action="manageRequestStatus.post" enctype="multipart/form-data" method="POST">
    <input type="hidden" name="id" value="<%= bean.getSampleRequest().getRowId()%>">
    <table cellspacing="5" class="normal">
        <tr>
            <th align="right">Status</th>
            <td>
                <select name="status">
                    <%
                        for (SampleRequestStatus status : statuses)
                        {
                    %>
                    <option value="<%= status.getRowId() %>" <%= bean.getSampleRequest().getStatusId() == status.getRowId() ? "SELECTED" : ""%>>
                        <%= h(status.getLabel()) %>
                    </option>
                    <%
                        }
                    %>
                </select>
            </td>
        </tr>
        <tr>
            <th align="right">Comments</th>
            <td><textarea name="comments" rows="10" cols="50"></textarea></td>
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
            <th>Notify</th>
            <td>
                <%
                    List<ActorNotificationRecipientSet> possibleNotifications = bean.getPossibleNotifications();
                    for (ActorNotificationRecipientSet possibleNotification : possibleNotifications)
                    {
                        boolean hasEmailAddresses = possibleNotification.getEmailAddresses().length > 0;
                %>
                <input type="checkbox"
                       name="notificationIdPairs"
                       value="<%= possibleNotification.getFormValue() %>" <%= hasEmailAddresses ? "" : "DISABLED" %>>
                <%= h(possibleNotification.getShortRecipientDescription())%><%= hasEmailAddresses ?
                    helpPopup("Group Members", possibleNotification.getEmailAddresses("<br>") + "<br>" +
                            possibleNotification.getConfigureEmailsLinkHTML(), true) :
                    " " + possibleNotification.getConfigureEmailsLinkHTML() %><br>
                <%
                    }
                %>
            </td>
        </tr>
        <tr>
            <th>&nbsp;</th>
            <td>
                <%= buttonImg("Save Changes and Send Notifications")%>&nbsp;
                <%= buttonLink("Cancel", "manageRequest.view?id=" + bean.getSampleRequest().getRowId())%>
            </td>
        </tr>
    </table>
</form>
