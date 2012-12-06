<%
/*
 * Copyright (c) 2006-2012 LabKey Corporation
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
<%@ page import="org.labkey.study.controllers.samples.SpecimenController"%>
<%@ page import="org.labkey.study.model.SiteImpl"%>
<%@ page import="org.labkey.study.model.Specimen"%>
<%@ page import="org.labkey.study.samples.notifications.ActorNotificationRecipientSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<SpecimenController.LabSpecimenListsBean> me = (JspView<SpecimenController.LabSpecimenListsBean>) HttpView.currentView();
    SpecimenController.LabSpecimenListsBean bean = me.getModelBean();
    boolean originating = bean.getType() == SpecimenController.LabSpecimenListsBean.Type.ORIGINATING;
%>
<form action="<%=h(buildURL(SpecimenController.EmailLabSpecimenListsAction.class))%>" method="POST" enctype="multipart/form-data">
<input type="hidden" name="id" value="<%= bean.getSampleRequest().getRowId() %>">
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
        <th align="left"><%= text(originating ? "Originating" : "Providing") %> Location</th>
        <th align="left">Download options</th>
        <th align="left">Specimen IDs</th>
        <th align="left">Email Recipients</th>
    </tr>
    <%
        int rowCount = 0;
        for (SiteImpl site : bean.getLabs())
        {
            String downloadURLPrefix =  buildURL(SpecimenController.DownloadSpecimenListAction.class) + "id=" + bean.getSampleRequest().getRowId() +
                    "&destSiteId=" + bean.getSampleRequest().getDestinationSiteId() +
                    "&listType=" + bean.getType().toString() +
                    "&sourceSiteId=" + site.getRowId();

            if (bean.isExportAsWebPage())
                downloadURLPrefix += "&exportAsWebPage=true&export=";
            else
                downloadURLPrefix += "&export=";
    %>
    <tr class="<%= text(rowCount++ % 2 == 0 ? "labkey-alternate-row" : "labkey-row") %>" valign="top">
        <td><%= h(site.getDisplayName()) %></td>
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
                        <td><%= h(specimen.getGlobalUniqueId()) %></td>
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
                        SiteImpl notifySite = possibleNotification.getSite();
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
                               value="<%= site.getRowId() %>,<%= h(possibleNotification.getFormValue()) %>" <%= text(hasEmailAddresses ? "" : "DISABLED") %>>
                    </td>
                    <td valign="middle">
                        <%= h(possibleNotification.getShortRecipientDescription())%><%= hasEmailAddresses ?
                            helpPopup("Group Members", possibleNotification.getEmailAddresses("<br>") + "<br>" +
                                    possibleNotification.getConfigureEmailsLinkHTML(), true) :
                            " " + possibleNotification.getConfigureEmailsLinkHTML() %>
                    </td>
                    <td valign="middle">
                        <%= text(notifySite != null && notifySite.getRowId() == bean.getSampleRequest().getDestinationSiteId() ? "Requesting&nbsp;Location&nbsp;" : "") %>
                        <%= text(notifySite != null && notifySite.getRowId() == site.getRowId() ? bean.getType().getDisplay() + "&nbsp;Location" : "") %>
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
        <td><%= generateSubmitButton("Send Email")%> <%= generateButton("Cancel", buildURL(SpecimenController.ManageRequestAction.class) + "id=" + bean.getSampleRequest().getRowId())%></td>
    </tr>
</table>
</form>