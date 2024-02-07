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
<%@ page import="org.labkey.api.security.AuthenticationManager" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.specimen.actions.ShowGroupMembersAction" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    JspView<ShowGroupMembersAction.GroupMembersBean> me = (JspView<ShowGroupMembersAction.GroupMembersBean>) HttpView.currentView();
    ShowGroupMembersAction.GroupMembersBean bean = me.getModelBean();
%>
<labkey:errors/>
<span>
<labkey:form action="<%=urlFor(ShowGroupMembersAction.class)%>" method="POST">
    <%=generateReturnUrlFormField(bean.getReturnUrl())%>
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
        <table>
            <tr>
                <th>Remove</th>
                <th>Email</th>
            </tr>
            <%
                for (User member : bean.getMembers())
                {
            %>
            <tr>
                <td align="center"><input type="checkbox" name="delete" value="<%=h(member)%>"></td>
                <td><%= unsafe(member.isActive() ? "" : "<del>")%><%=h(member.getAutocompleteName(getContainer(), getUser()))%><%=unsafe(member.isActive() ? "" : "</del> (inactive)") %></td>
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
        <labkey:autoCompleteTextArea name="names"
                                     url="<%=bean.getCompleteUsersPrefix()%>"
                                     rows="8" cols="30"/><br>
        <input type="checkbox" name="sendEmail" value="true" checked><%=AuthenticationManager.getStandardSendVerificationEmailsMessage()%><br><br>
        <input type="hidden" name="id" value="<%= bean.getActor().getRowId() %>">

        <%
            if (bean.getLocation() != null)
            {
        %>
            <input type="hidden" name="locationId" value="<%= bean.getLocation().getRowId() %>">
        <%
            }
        %>
        <%= button("Update Members").submit(true) %>
    </div>
</labkey:form>
</span>