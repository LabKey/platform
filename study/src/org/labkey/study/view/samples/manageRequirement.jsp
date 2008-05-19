<%
/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.study.model.SampleRequestRequirement"%>
<%@ page import="org.labkey.study.model.Site"%>
<%@ page import="org.labkey.study.controllers.samples.SpringSpecimenController"%>
<%@ page import="java.util.Set"%>
<%@ page import="java.util.HashSet"%>
<%@ page import="org.labkey.study.model.SampleRequestActor"%>
<%@ page import="org.labkey.common.util.Pair"%>
<%@ page import="java.util.List"%>
<%@ page import="org.labkey.study.samples.notifications.ActorNotificationRecipientSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<SpringSpecimenController.ManageRequirementBean> me = (JspView<SpringSpecimenController.ManageRequirementBean>) HttpView.currentView();
    org.labkey.study.controllers.samples.SpringSpecimenController.ManageRequirementBean bean = me.getModelBean();
    SampleRequestRequirement requirement = bean.getRequirement();
    Site site = requirement.getSite();
    String siteLabel = site != null ? site.getDisplayName() : "N/A";

    String deleteURL = "deleteRequirement.view?id=" + requirement.getRequestId() +
            "&requirementId=" + requirement.getRowId();
%>
<table cellspacing="5" class="normal">
    <tr>
        <td align="left"><%= textLink("View Request", "manageRequest.view?id=" + requirement.getRequestId())%></td>
    </tr>
<%
    if (bean.isRequestManager() && bean.isFinalState())
    {
%>
    <tr class="wpHeader">
        <th align="left">Requirement Notes</th>
    </tr>
    <tr>
        <td class="ms-searchform">
            This request is in a final state; no changes are allowed.<br>
            To make changes, you must <a href="manageRequestStatus.view?id=<%= requirement.getRequestId() %>">
            change the request's status</a> to a non-final state.
        </td>
    </tr>
<%
    }
%>
    <tr class="wpHeader">
        <th align="left">Requirement Details</th>
    </tr>
    <tr>
        <td>
            <table>
                <tr>
                    <th align="left">Actor</th>
                    <td><%= requirement.getActor().getLabel() %></td>
                </tr>
                <tr>
                    <th align="left">Site</th>
                    <td><%= siteLabel %></td>
                </tr>
                <tr>
                    <th align="left">Description</th>
                    <td><%= requirement.getDescription() %></td>
                </tr>
                <%
                    if (!bean.isRequestManager())
                    {
                %>
                <tr>
                    <th align="left">Status</th>
                    <td>
                        <span style="color:<%= requirement.isComplete() ? "green" : "red"%>;font-weight:bold;">
                            <%= requirement.isComplete() ? "Complete" : "Incomplete" %>
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
    <tr class="wpHeader">
        <th align="left">Change Status</th>
    </tr>
<%
            if (!bean.isFinalState())
            {
    %>
    <tr>
        <td>
            <form action="manageRequirement.post" enctype="multipart/form-data" method="post">
                <input type="hidden" name="id" value="<%= requirement.getRequestId() %>">
                <input type="hidden" name="requirementId" value="<%= requirement.getRowId() %>">
                <table class="normal">
                    <tr>
                        <td>&nbsp;</td>
                        <th align="left">
                            <input type="checkbox"
                                   name="complete" <%= requirement.isComplete() ? "CHECKED" : ""%>>
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
                            <%= buttonImg("Save Changes and Send Notifications") %>&nbsp;
                            <%= buttonLink("Delete Requirement", deleteURL)%>&nbsp;
                            <%= buttonLink("Cancel", "manageRequest.view?id=" + requirement.getRequestId())%>
                        </td>
                    </tr>
                </table>
            </form>
        </td>
    </tr>
    <%
            }
        }
    %>
    <tr class="wpHeader">
        <th align="left">History</th>
    </tr>
    <tr>
        <td><% me.include(bean.getHistoryView(), out); %></td>
    </tr>
</table>
