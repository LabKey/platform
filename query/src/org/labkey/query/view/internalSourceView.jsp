<%
/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
<%@ page import="org.labkey.query.controllers.InternalSourceViewForm" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.query.persist.CstmView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.query.controllers.QueryController.*" %>
<%@ page import="org.labkey.api.security.UserManager" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    String userIdToString(Integer userId)
    {
        if (userId == null)
        {
            return "";
        }
        User user = UserManager.getUser(userId);
        if (user == null)
        {
            return "Unknown user #" + userId;
        }
        return user.getDisplayName(user);
    }
%>
<%
    InternalSourceViewForm form = (InternalSourceViewForm) HttpView.currentModel();
    ActionURL urlPost = new ActionURL(InternalSourceViewAction.class, getContainer());
    urlPost.addParameter("customViewId", Integer.toString(form.getCustomViewId()));
    ActionURL urlCancel = new ActionURL(ManageViewsAction.class, getContainer());
    CstmView view = form.getViewAndCheckPermission();
%>
<labkey:errors />
<labkey:form method = "POST" action="<%=h(urlPost)%>">
    <p>Schema: <%=h(view.getSchema())%><br>
        Query: <%=h(view.getQueryName())%><br>
        Name: <%=h(view.getName())%><br>
        Owner: <%=h(userIdToString(view.getCustomViewOwner()))%><br>
        <br>
        Inherit: <labkey:checkbox id="ff_inherit" name="ff_inherit" value="true" checked="<%=form.ff_inherit%>" /><br>
        Hidden: <labkey:checkbox id="ff_hidden" name="ff_hidden" value="true" checked="<%=form.ff_hidden%>" /><br>
        Shared: <%=text(view.getCustomViewOwner() == null ? "yes" : "no")%><br>
        <br>
        Container: <%=h(view.getContainerPath())%><br>
        Created: <%=formatDateTime(view.getCreated())%><br>
        Created By: <%=h(userIdToString(view.getCreatedBy()))%><br>
        Modified: <%=formatDateTime(view.getModified())%><br>
        Modified: <%=h(userIdToString(view.getModifiedBy()))%><br>
    </p>
    <table><tr><th>Columns</th><th>Filter/Sort</th></tr>
        <tr><td><textarea name="ff_columnList" rows="20" cols="40"><%=h(form.ff_columnList)%></textarea></td>
        <td><textarea name="ff_filter" rows="20" cols="40"><%=h(form.ff_filter)%></textarea></td>
        </tr>
    </table>

    <labkey:button text="Save" /> <labkey:button text="Cancel" href="<%=urlCancel%>" />
</labkey:form>
