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
<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page import="org.labkey.api.query.QueryForm" %>
<%@ page import="org.labkey.api.security.ACL" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.UserManager" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.query.persist.CstmView" %>
<%@ page import="org.labkey.query.persist.QueryManager" %>
<%@ page import="java.util.*" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%! String userIdToString(Integer userId)
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
    return user.toString();
}
%>

<% QueryForm form = (QueryForm) __form;
    QueryManager mgr = QueryManager.get();
    List<CstmView> views = new ArrayList();
    if (form.getContext().hasPermission(ACL.PERM_UPDATE))
    {
        views.addAll(Arrays.asList(mgr.getColumnLists(getContainer(), null, null, null, null, false)));
    }
    if (!getUser().isGuest())
    {
        views.addAll(Arrays.asList(mgr.getColumnLists(getContainer(), null, null, null, getUser(), false)));
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
<p>This page is for troubleshooting custom grid views. It is not intended for general use.</p>
<table>
    <tr>
        <th>Schema</th>
        <th>Query</th>
        <th>View Name</th>
        <th>Owner</th>
        <th>Inherit</th>
    </tr>
    <% if (form.getContext().hasPermission(ACL.PERM_UPDATE))
    {
        for (CstmView view : views)
        {%>
    <tr>
        <td><%=h(view.getSchema())%>
        </td>
        <td><%=h(view.getQueryName())%>
        </td>
        <td><%=h(view.getName())%>
        </td>
        <td><%=userIdToString(view.getCustomViewOwner())%>
        </td>
        <td><%=mgr.canInherit(view.getFlags()) ? "yes" : ""%></td>
        <td><% ActionURL urlDelete = new ActionURL("query", "internalDeleteView", getContainer());
        urlDelete.addParameter("customViewId", Integer.toString(view.getCustomViewId())); %>
            <labkey:link href="<%=urlDelete%>" text="delete" />
            <% ActionURL urlSource = new ActionURL("query", "internalSourceView", getContainer());
            urlSource.addParameter("customViewId", Integer.toString(view.getCustomViewId())); %>
            <labkey:link href="<%=urlSource%>" text="edit" />
        </td>
    </tr>
    <%
            }
        }%>
</table>

<% ActionURL urlNewView = new ActionURL("query", "internalNewView", getContainer()); %>
<labkey:button text="create new view" href="<%=urlNewView%>"/>
