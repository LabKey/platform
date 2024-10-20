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
<%@ page import="org.labkey.api.specimen.Vial"%>
<%@ page import="org.labkey.api.specimen.location.LocationImpl"%>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.specimen.actions.SpecimenController" %>
<%@ page import="org.labkey.specimen.actions.SpecimenController.DownloadSpecimenListAction" %>
<%@ page import="org.labkey.specimen.actions.SpecimenController.EmailLabSpecimenListsAction" %>
<%@ page import="org.labkey.specimen.actions.SpecimenController.LabSpecimenListsBean" %>
<%@ page import="org.labkey.specimen.notifications.ActorNotificationRecipientSet" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<LabSpecimenListsBean> me = (JspView<LabSpecimenListsBean>) HttpView.currentView();
    LabSpecimenListsBean bean = me.getModelBean();
    boolean originating = bean.getType() == LabSpecimenListsBean.Type.ORIGINATING;
%>
<labkey:form action="<%=urlFor(EmailLabSpecimenListsAction.class)%>" method="POST" enctype="multipart/form-data">
<input type="hidden" name="id" value="<%= bean.getSpecimenRequest().getRowId() %>">
<input type="hidden" name="listType" value="<%= h(bean.getType().toString()) %>">

<table>
        <%
            if (!bean.isRequirementsComplete())
            {
        %>
    <tr>
        <td colspan="4" style="height:40px;">
            <span class="labkey-error">
                WARNING: The requirements for this request are incomplete.
            </span>
        </td>
    </tr>
        <%
            }
        %>
    <tr>
        <th align="left"><%= unsafe(originating ? "Originating" : "Providing") %> Location</th>
        <th align="left">Download options</th>
        <th align="left">Specimen IDs</th>
        <th align="left">Email Recipients</th>
    </tr>
    <%
        int rowCount = 0;
        for (LocationImpl location : bean.getLabs())
        {
            ActionURL downloadURL = urlFor(DownloadSpecimenListAction.class)
                .addParameter("id", bean.getSpecimenRequest().getRowId())
                .addParameter("destSiteId", bean.getSpecimenRequest().getDestinationSiteId())
                .addParameter("listType", bean.getType().toString())
                .addParameter("sourceSiteId", location.getRowId());
    %>
    <tr class="<%=getShadeRowClass(rowCount++) %>" valign="top">
        <td><%= h(location.getDisplayName()) %></td>
        <td>
            <table>
                <tr>
                    <td><%=link("Export to Excel").href(downloadURL.replaceParameter("export", "xls"))%></td>
                </tr>
                <tr>
                    <td><%=link("Export to text file").href(downloadURL.replaceParameter("export", "tsv")) %></td>
                </tr>
            </table>
            <br>
        </td>
        <td>
            <table>
                <%
                    for (Vial vial : bean.getSpecimens(location))
                    {
                %>
                    <tr valign="top">
                        <td><%= h(vial.getGlobalUniqueId()) %></td>
                    </tr>
                <%
                    }
                %>
            </table>
        </td>
        <td>
            <table>
                <%
                    boolean hasInactiveEmailAddress = false;
                    for (ActorNotificationRecipientSet possibleNotification : bean.getPossibleNotifications())
                    {
                        LocationImpl notifyLocation = possibleNotification.getLocation();
                        if (notifyLocation != null &&
                                notifyLocation.getRowId() != bean.getSpecimenRequest().getDestinationSiteId() &&
                                notifyLocation.getRowId() != location.getRowId())
                            continue;

                        boolean hasEmailAddresses = possibleNotification.getAllEmailAddresses().length > 0;
                        if (hasEmailAddresses)
                            hasInactiveEmailAddress |= possibleNotification.hasInactiveEmailAddress();

                %>
                <tr valign="top">
                    <td valign="middle">
                        <input type="checkbox"
                               name="notify"
                               value="<%= location.getRowId() %>,<%= h(possibleNotification.getFormValue()) %>"<%=disabled(!hasEmailAddresses)%>>
                    </td>
                    <td valign="middle">
                        <%=possibleNotification.getHtmlDescriptionAndLink(hasEmailAddresses, getActionURL())%>
                    </td>
                    <td valign="middle">
                        <%= unsafe(notifyLocation != null && notifyLocation.getRowId() == bean.getSpecimenRequest().getDestinationSiteId() ? "Requesting&nbsp;Location&nbsp;" : "") %>
                        <%= unsafe(notifyLocation != null && notifyLocation.getRowId() == location.getRowId() ? bean.getType().getDisplay() + "&nbsp;Location" : "") %>
                    </td>
                </tr>
                <%
                    }
                    if (hasInactiveEmailAddress)
                    {
                %>
                    <tr valign="top">
                        <td valign="middle">
                            <input type="checkbox"
                                   name="emailInactiveUsers">
                        </td>
                        <td>
                            Include inactive users<br>
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
<table>
    <tr>
        <td>&nbsp;</td>
        <td class='labkey-form-label'>
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
        <td class='labkey-form-label'>
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
        <td class='labkey-form-label'>
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
        <td><%= button("Send Email").submit(true) %> <%= button("Cancel").href(SpecimenController.getManageRequestURL(getContainer(), bean.getSpecimenRequest().getRowId(), null))%></td>
    </tr>
</table>
</labkey:form>