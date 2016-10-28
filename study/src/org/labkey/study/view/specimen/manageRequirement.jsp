<%
/*
 * Copyright (c) 2006-2016 LabKey Corporation
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
<%@ page import="org.labkey.api.study.Location"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.api.view.template.ClientDependencies"%>
<%@ page import="org.labkey.study.controllers.specimen.SpecimenController"%>
<%@ page import="org.labkey.study.model.SpecimenRequestRequirement" %>
<%@ page import="org.labkey.study.specimen.notifications.ActorNotificationRecipientSet" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        // TODO: --Ext3-- This should be declared as part of the included views
        dependencies.add("clientapi/ext3");
    }
%>
<%
    JspView<SpecimenController.ManageRequirementBean> me = (JspView<SpecimenController.ManageRequirementBean>) HttpView.currentView();
    SpecimenController.ManageRequirementBean bean = me.getModelBean();
    SpecimenRequestRequirement requirement = bean.getRequirement();
    Location location = requirement.getLocation();
    String locationLabel = location != null ? location.getDisplayName() : "N/A";

    String deleteURL = buildURL(SpecimenController.DeleteRequirementAction.class, "id=" + requirement.getRequestId() +
            "&requirementId=" + requirement.getRowId());
%>
<labkey:errors />
<table class="labkey-manage-display">
    <tr>
        <td align="left"><%= textLink("View Request", buildURL(SpecimenController.ManageRequestAction.class) + "id=" + requirement.getRequestId())%></td>
    </tr>
<%
    if (bean.isRequestManager() && bean.isFinalState())
    {
%>
    <tr class="labkey-wp-header">
        <th align="left">Requirement Notes</th>
    </tr>
    <tr>
        <td class="labkey-form-label">
            This request is in a final state; no changes are allowed.<br>
            To make changes, you must <a href="<%=text(buildURL(SpecimenController.ManageRequestStatusAction.class) + "id=" + requirement.getRequestId()) %>">
            change the request's status</a> to a non-final state.
        </td>
    </tr>
<%
    }
%>
    <tr class="labkey-wp-header">
        <th align="left">Requirement Details</th>
    </tr>
    <tr>
        <td>
            <table>
                <tr>
                    <th align="left">Actor</th>
                    <td><%= h(requirement.getActor().getLabel()) %></td>
                </tr>
                <tr>
                    <th align="left">Location</th>
                    <td><%= h(locationLabel) %></td>
                </tr>
                <tr>
                    <th align="left">Description</th>
                    <td><%= text(requirement.getDescription()) %></td>
                </tr>
                <%
                    if (!bean.isRequestManager())
                    {
                %>
                <tr>
                    <th align="left">Status</th>
                    <td>
                        <span class="<%= text(requirement.isComplete() ? "labkey-message" : "labkey-error")%>" style="font-weight:bold;">
                            <%= text(requirement.isComplete() ? "Complete" : "Incomplete") %>
                        </span>
                    </td>
                </tr>
                <%
                    }
                %>
            </table>
        </td>
    </tr>
    <%
        if (bean.isRequestManager())
        {
%>
    <tr class="labkey-wp-header">
        <th align="left">Change Status</th>
    </tr>
<%
            if (!bean.isFinalState())
            {
    %>
    <tr>
        <td>
            <labkey:form action="<%=h(buildURL(SpecimenController.ManageRequirementAction.class))%>" enctype="multipart/form-data" method="post">
                <input type="hidden" name="id" value="<%= requirement.getRequestId() %>">
                <input type="hidden" name="requirementId" value="<%= requirement.getRowId() %>">
                <table>
                    <tr>
                        <td>&nbsp;</td>
                        <th align="left">
                            <input type="checkbox"
                                   name="complete"<%=checked(requirement.isComplete())%>>
                            Complete
                        </th>
                    </tr>
                    <tr>
                        <th align="right">Comments</th>
                        <td>
                            <textarea name="comment" rows="10" cols="50"></textarea>
                        </td>
                    </tr>
                    <tr>
                        <th align="right">Attachments</th>
                        <td>
                            <input type="file" name="formFiles[0]"><br>
                            <input type="file" name="formFiles[1]"><br>
                            <input type="file" name="formFiles[2]">
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
                                   value="<%= text(possibleNotification.getFormValue()) %>" <%=disabled(!hasEmailAddresses) %>
                                   <%=checked(hasEmailAddresses && bean.isDefaultNotification(possibleNotification)) %>>
                            <%= text(possibleNotification.getHtmlDescriptionAndLink(hasEmailAddresses)) %><br>
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
                            <%= button("Delete Requirement").submit(true).onClick("this.form.action='" + deleteURL + "'") %>&nbsp;
                            <%= button("Cancel").href(buildURL(SpecimenController.ManageRequestAction.class, "id=" + requirement.getRequestId())) %>
                        </td>
                    </tr>
                </table>
            </labkey:form>
        </td>
    </tr>
    <%
            }
        }
    %>
    <tr class="labkey-wp-header">
        <th align="left">History</th>
    </tr>
    <tr>
        <td><% me.include(bean.getHistoryView(), out); %></td>
    </tr>
</table>
