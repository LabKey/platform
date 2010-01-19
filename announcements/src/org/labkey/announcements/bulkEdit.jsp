<%
/*
 * Copyright (c) 2007-2010 LabKey Corporation
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
<%@ page import="org.labkey.announcements.AnnouncementsController.BulkEditView.BulkEditBean"%>
<%@ page import="org.labkey.announcements.model.AnnouncementManager"%>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.action.ReturnUrlForm" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    HttpView<BulkEditBean> me = (HttpView<BulkEditBean>) HttpView.currentView();
    BulkEditBean bean = me.getModelBean();
%>
<%=formatMissedErrors("form")%>
<form action="bulkEdit.post" method="POST">

<table>
    <tr><td></td></tr>
    <tr>
        <td>The current folder default setting is: <%=bean.folderEmailOption%></td>
    </tr>
    <tr><td></td></tr>
</table>


<table class="labkey-data-region labkey-show-borders">
    <colgroup><col><col><col><col><col><col></colgroup>
    <tr class="labkey-col-header-filter">
        <th>Email</th>
        <th>First Name</th>
        <th>Last Name</th>
        <th>Display Name</th>
        <th>Email Option</th>
        <th>Project Member?</th>
    </tr>

<%
    int i = 1;
    for (AnnouncementManager.EmailPref emailPref : bean.emailPrefList)
    {

        String rowClass = "labkey-row";
        //shade odd rows in gray
        if (i % 2 == 1)
            rowClass = "labkey-alternate-row";

        int userId = emailPref.getUserId();
        String email = emailPref.getEmail();
        String firstName = emailPref.getFirstName();
        String lastName = emailPref.getLastName();
        String displayName = emailPref.getDisplayName();
        //integer value
        Integer emailOptionId = emailPref.getEmailOptionId();

        int emailOptionValue = -1;
        if (emailOptionId != null)
            emailOptionValue = emailOptionId.intValue();
%>
            <tr class="<%=rowClass%>">
                <td>
                    <input type="hidden" name="userId" value="<%=userId%>">
                    <%= email %>
                </td>
                <td>
                    <%= firstName %>&nbsp;
                </td>
                <td>
                    <%= lastName %>&nbsp;
                </td>
                <td>
                    <%= displayName %>
                </td>
                <td>
                    <select name="emailOptionId"><%

                        if (emailPref.isProjectMember())
                        { %>
                        <option value='<%=AnnouncementManager.EMAIL_PREFERENCE_DEFAULT%>'<%=emailOptionValue == AnnouncementManager.EMAIL_PREFERENCE_DEFAULT ? " selected" : "" %>>&lt;folder default&gt;</option><%
                        } %>
                        <option value='<%=AnnouncementManager.EMAIL_PREFERENCE_NONE%>'<%=emailOptionValue == AnnouncementManager.EMAIL_PREFERENCE_NONE ? " selected" : "" %>>No email</option>
                        <option value='<%=AnnouncementManager.EMAIL_PREFERENCE_ALL%>'<%=emailOptionValue == AnnouncementManager.EMAIL_PREFERENCE_ALL ? " selected" : "" %>>All conversations</option>
                        <option value='<%=AnnouncementManager.EMAIL_PREFERENCE_MINE%>'<%=emailOptionValue == AnnouncementManager.EMAIL_PREFERENCE_MINE ? " selected" : "" %>>My conversations</option>
                        <option value='<%=AnnouncementManager.EMAIL_NOTIFICATION_TYPE_DIGEST | AnnouncementManager.EMAIL_PREFERENCE_ALL%>'<%=emailOptionValue == (AnnouncementManager.EMAIL_NOTIFICATION_TYPE_DIGEST | AnnouncementManager.EMAIL_PREFERENCE_ALL) ? " selected" : "" %>>Daily digest of all conversations</option>
                        <option value='<%=AnnouncementManager.EMAIL_NOTIFICATION_TYPE_DIGEST | AnnouncementManager.EMAIL_PREFERENCE_MINE%>'<%=emailOptionValue == (AnnouncementManager.EMAIL_NOTIFICATION_TYPE_DIGEST | AnnouncementManager.EMAIL_PREFERENCE_MINE) ? " selected" : "" %>>Daily digest of my conversations</option>
                    </select>
                </td>
                <td align="center">
                    <%  if (emailPref.isProjectMember())
                            out.write("Yes");
                        else
                            out.write("No");
                    %>
                </td>
            </tr>
            <%
            i++;
    }%>
</table>
    <%=generateReturnUrlFormField(bean.returnURL)%>
    <%=generateSubmitButton("Submit")%>
    &nbsp;
    <%=PageFlowUtil.generateSubmitButton("Cancel", "javascript:window.history.back(); return false;")%>
</form>
