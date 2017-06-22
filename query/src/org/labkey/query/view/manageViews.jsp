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
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.query.QueryForm" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.UserManager" %>
<%@ page import="org.labkey.api.security.permissions.UpdatePermission" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.query.controllers.QueryController.InternalDeleteView" %>
<%@ page import="org.labkey.query.controllers.QueryController.InternalNewViewAction" %>
<%@ page import="org.labkey.query.controllers.QueryController.InternalSourceViewAction" %>
<%@ page import="org.labkey.query.persist.CstmView" %>
<%@ page import="org.labkey.query.persist.QueryManager" %>
<%@ page import="org.labkey.query.view.CustomViewSetKey" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Objects" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    String userIdToString(Integer userId, User currentUser)
    {
        if (userId == null)
        {
            return "";
        }
        User user = UserManager.getUser(userId);
        if (user == null)
            return "Unknown user #" + userId;
        if (user.isGuest())
            return "Guest";
        return user.getDisplayName(currentUser);
    }
%>
<%
    QueryForm form = (QueryForm) HttpView.currentModel();
    User user = getUser();
    Container c = getContainer();
    String schemaName = form.getSchemaName().equals("") ? null : form.getSchemaName();
    String queryName = form.getQueryName();
    QueryManager mgr = QueryManager.get();
    List<CstmView> views = new ArrayList<>();

    if (getViewContext().hasPermission(UpdatePermission.class))
    {
        views.addAll(mgr.getCstmViews(c, schemaName, queryName, null, null, false, true));
    }

    if (!user.isGuest())
    {
        views.addAll(mgr.getCstmViews(c, schemaName, queryName, null, user, false, false));
    }

    // UNDONE: Requires queryName for now.  We need a method to get all session views in a container.
    if (queryName != null)
    {
        views.addAll(CustomViewSetKey.getCustomViewsFromSession(getViewContext().getRequest(), c, queryName).values());
    }

    views.sort((o1, o2) ->
    {
        if (o1 == o2)
            return 0;
        Integer owner1 = o1.getCustomViewOwner();
        Integer owner2 = o2.getCustomViewOwner();
        if (!Objects.equals(owner1, owner2))
        {
            if (owner1 == null)
                return -1;
            if (owner2 == null)
                return 1;
            return owner1 - owner2;
        }
        int ret = StringUtils.trimToEmpty(o1.getSchema()).compareToIgnoreCase(StringUtils.trimToEmpty(o2.getSchema()));
        if (ret != 0)
            return ret;
        ret = StringUtils.trimToEmpty(o1.getQueryName()).compareToIgnoreCase(StringUtils.trimToEmpty(o2.getQueryName()));
        if (ret != 0)
            return ret;
        return StringUtils.trimToEmpty(o1.getName()).compareToIgnoreCase(StringUtils.trimToEmpty(o2.getName()));
    });
%>
<p>This page is for troubleshooting custom grid views. It is not intended for general use.
<% if (schemaName != null) { %>
<br>Filtered by schema: <b><%= h(schemaName) %></b>
<% } %>
<% if (queryName != null) { %>
<br>Filtered by query: <b><%= h(queryName) %></b>
<% } %>
</p>

<table>
    <tr>
        <th>Schema</th>
        <th>Query</th>
        <th>View Name</th>
        <th>Flags</th>
        <th>Owner</th>
        <th>Created</th>
        <th>Created&nbsp;By</th>
        <th>Modified</th>
        <th>Modified&nbsp;By</th>
    </tr>
    <% if (getViewContext().hasPermission(UpdatePermission.class))
    {
        for (CstmView view : views)
        {
            List<String> flags = new ArrayList<>();
            if (view.getCustomViewId() == 0)
                flags.add("<em>session</em>");
            if (mgr.canInherit(view.getFlags()))
                flags.add("inherit");
            if (mgr.isHidden(view.getFlags()))
                flags.add("hidden");
            if (mgr.isSnapshot(view.getFlags()))
                flags.add("shapshot");
    %>
    <tr>
        <td><%=h(view.getSchema())%>
        </td>
        <td><%=h(view.getQueryName())%>
        </td>
        <td><%=h(view.getName())%>
        </td>
        <td><%=text(StringUtils.join(flags, ","))%></td>
        <td><%=h(view.isShared() ? "<shared>" : userIdToString(view.getCustomViewOwner(), user))%>
        </td>
        <td><%=formatDateTime(view.getCreated())%></td>
        <td><%=h(userIdToString(view.getCreatedBy(), user))%></td>
        <td><%=formatDateTime(view.getModified())%></td>
        <td><%=h(userIdToString(view.getModifiedBy(), user))%></td>
        <td><% ActionURL urlDelete = new ActionURL(InternalDeleteView.class, c);
        urlDelete.addParameter("customViewId", Integer.toString(view.getCustomViewId())); %>
            <labkey:link href="<%=urlDelete%>" text="delete" />
            <% ActionURL urlSource = new ActionURL(InternalSourceViewAction.class, c);
            urlSource.addParameter("customViewId", Integer.toString(view.getCustomViewId())); %>
            <labkey:link href="<%=urlSource%>" text="edit" />
        </td>
    </tr>
    <%
            }
        }%>
</table>

<% ActionURL urlNewView = new ActionURL(InternalNewViewAction.class, c); %>
<labkey:button text="create new view" href="<%=urlNewView%>"/>
