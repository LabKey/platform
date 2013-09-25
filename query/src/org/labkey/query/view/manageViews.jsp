<%
/*
 * Copyright (c) 2006-2013 LabKey Corporation
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
<%@ page import="org.labkey.query.persist.CstmView" %>
<%@ page import="org.labkey.query.persist.QueryManager" %>
<%@ page import="java.util.*" %>
<%@ page import="org.labkey.query.controllers.QueryController.*" %>
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.query.view.CustomViewSetKey" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%! String userIdToString(Integer userId, User currentUser)
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
    User user = getViewContext().getUser();
    Container c = getViewContext().getContainer();
    String schemaName = form.getSchemaName().equals("") ? null : form.getSchemaName();
    String queryName = form.getQueryName();
    QueryManager mgr = QueryManager.get();
    List<CstmView> views = new ArrayList<>();
    if (form.getViewContext().hasPermission(UpdatePermission.class))
    {
        views.addAll(Arrays.asList(mgr.getCstmViews(c, schemaName, queryName, null, null, false, true)));
    }
    if (!user.isGuest())
    {
        views.addAll(Arrays.asList(mgr.getCstmViews(c, schemaName, queryName, null, user, false, false)));
    }

    // UNDONE: Requires queryName for now.  We need a method to get all session views in a container.
    if (queryName != null)
    {
        views.addAll(CustomViewSetKey.getCustomViewsFromSession(getViewContext().getRequest(), c, queryName).values());
    }

    Collections.sort(views, new Comparator<CstmView>()
    {
        public int compare(CstmView o1, CstmView o2)
        {
            if (o1 == o2)
                return 0;
            Integer owner1 = o1.getCustomViewOwner();
            Integer owner2 = o2.getCustomViewOwner();
            if (owner1 != owner2)
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
        }
    });
%>
<p>This page is for troubleshooting custom grid views. It is not intended for general use.
<% if (schemaName != null) { %>
<br>Filtered by schema: <b><%= schemaName %></b>
<% } %>
<% if (queryName != null) { %>
<br>Filtered by query: <b><%= queryName %></b>
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
    <% if (form.getViewContext().hasPermission(UpdatePermission.class))
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
        <td><%=StringUtils.join(flags, ",")%></td>
        <td><%=userIdToString(view.getCustomViewOwner(), user)%>
        </td>
        <td><%=DateUtil.formatDateTime(view.getCreated()).replaceAll(" ", "&nbsp;")%></td>
        <td><%=userIdToString(view.getCreatedBy(), user)%></td>
        <td><%=DateUtil.formatDateTime(view.getModified()).replaceAll(" ", "&nbsp;")%></td>
        <td><%=userIdToString(view.getModifiedBy(), user)%></td>
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
