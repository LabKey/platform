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
<%@ page import="org.labkey.study.controllers.samples.SamplesController"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<script type="text/javascript">LABKEY.requiresScript('completion.js');</script>
<%
    JspView<SamplesController.GroupMembersBean> me = (org.labkey.api.view.JspView<SamplesController.GroupMembersBean>) HttpView.currentView();
    SamplesController.GroupMembersBean bean = me.getModelBean();
    String messages = bean.getMessages().length() > 0 ? bean.getMessages() + "<br>" : "";
%>
<%= messages %>
<span class="normal">
<form action="showGroupMembers.post" method="POST">
    <input type="hidden" name="returnUrl" value="<%= h(bean.getReturnUrl()) %>">
<%
    if (bean.getMembers() == null || bean.getMembers().length <= 0)
    {
    %><p>This group currently has no members.</p><%
    }
    else
    {
%>
    <div id="current-members">
        Group members
        <br>
        <table valign="top" class="normal">
            <tr>
                <th>Remove</th>
                <th>Email</th>
            </tr>
            <%
                for (User member : bean.getMembers())
                {
            %>
            <tr>
                <td><input type="checkbox" name="delete" value="<%= member %>"></td>
                <td><%= member.getEmail() %></td>
            </tr>
            <%
                }
            %>
        </table>
    </div>
    <%
        }
    %>
    <br>

    <div id="add-members">
        Add New Members (enter one email address per line):<br>
        <textarea name="names" cols="30" rows="8"
                  onKeyDown="return ctrlKeyCheck(event);"
                  onBlur="hideCompletionDiv();"
                  autocomplete="off"
                  onKeyUp="return handleChange(this, event, '<%= bean.getCompleteUsersPrefix() %>');"></textarea><br>
        <input type="checkbox" name="sendEmail" value="true" checked>Send notification emails to all
        new<%
            if (bean.getLdapDomain() != null && bean.getLdapDomain().length() > 0)
            {
        %>, non-<%= bean.getLdapDomain() %>
        <%
            }
        %> users<br><br>
        <input type="hidden" name="id" value="<%= bean.getActor().getRowId() %>">

        <%
            if (bean.getSite() != null)
            {
        %>
            <input type="hidden" name="siteId" value="<%= bean.getSite().getRowId() %>">
        <%
            }
        %>
        <%= buttonImg("Update Members") %>
    </div>
</form>
</span>