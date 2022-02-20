<%
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
%>
<%@ page import="org.labkey.specimen.SpecimenRequestManager" %>
<%@ page import="org.labkey.api.specimen.SpecimenRequestStatus" %>
<%@ page import="org.labkey.api.study.SpecimenService"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.specimen.actions.ManageRequestBean" %>
<%@ page import="org.labkey.specimen.actions.SpecimenController" %>
<%@ page import="org.labkey.specimen.notifications.ActorNotificationRecipientSet" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    JspView<ManageRequestBean> me = (JspView<ManageRequestBean>) HttpView.currentView();
    ManageRequestBean bean = me.getModelBean();
    List<SpecimenRequestStatus> statuses = SpecimenRequestManager.get().getRequestStatuses(getContainer(), getUser());
%>
<labkey:errors />
<labkey:form action="<%=urlFor(SpecimenController.ManageRequestStatusAction.class)%>" enctype="multipart/form-data" method="POST">
    <input type="hidden" name="id" value="<%= bean.getSpecimenRequest().getRowId()%>">
    <table  class="labkey-manage-display">
        <tr>
            <th align="right">Request Description</th>
            <td>
                <textarea rows="10" cols="50" name="requestDescription"><%= h(bean.getSpecimenRequest().getComments()) %></textarea>
            </td>
        </tr>
        <tr>
            <th align="right">Current Status</th>
            <td>
                <% if (SpecimenService.get().getRequestCustomizer().canChangeStatus(getUser())) { %>
                <select name="status"><%
                        for (SpecimenRequestStatus status : statuses)
                        {
                    %>
                    <option value="<%= status.getRowId() %>"<%=selected(bean.getSpecimenRequest().getStatusId() == status.getRowId())%>>
                        <%= h(status.getLabel()) %>
                    </option><% } %>
                </select> <%
            }
            else
            {
                for (SpecimenRequestStatus status : statuses)
                {
                    if(bean.getSpecimenRequest().getStatusId() == status.getRowId())
                    { %>
                        <input type='hidden' name='status' value="<%= bean.getSpecimenRequest().getStatusId() %>"/><%= h(status.getLabel()) %><%
                    }
                }
            } %>
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
                    boolean hasInactiveEmailAddress = false;
                    List<ActorNotificationRecipientSet> possibleNotifications = bean.getPossibleNotifications();
                    for (ActorNotificationRecipientSet possibleNotification : possibleNotifications)
                    {
                        boolean hasEmailAddresses = possibleNotification.getAllEmailAddresses().length > 0;
                        if (hasEmailAddresses)
                            hasInactiveEmailAddress |= possibleNotification.hasInactiveEmailAddress();
                %>
                <input type="checkbox"
                       name="notificationIdPairs"
                       value="<%= text(possibleNotification.getFormValue()) %>"<%=disabled(!hasEmailAddresses)%>>
                <%=possibleNotification.getHtmlDescriptionAndLink(hasEmailAddresses, getActionURL())%><br>
                <%
                    }
                    if (hasInactiveEmailAddress)
                    {
                %>
                    <input type="checkbox"
                           name="emailInactiveUsers">
                    Include inactive users<br>
                <%
                    }
                %>
            </td>
        </tr>
        <tr>
            <th>&nbsp;</th>
            <td>
                <%= button("Save Changes and Send Notifications").submit(true) %>&nbsp;
                <%= button("Cancel").href(SpecimenController.getManageRequestURL(getContainer(), bean.getSpecimenRequest().getRowId(), null)) %>
            </td>
        </tr>
    </table>
</labkey:form>
