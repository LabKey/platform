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
<%@ page import="org.apache.commons.lang.StringUtils"%>
<%@ page import="org.labkey.api.data.ColumnInfo"%>
<%@ page import="org.labkey.api.security.ACL"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.api.view.ViewContext"%>
<%@ page import="org.labkey.study.model.DataSetDefinition"%>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<DataSetDefinition> me = (JspView<DataSetDefinition>) HttpView.currentView();
    DataSetDefinition dataset = me.getModelBean();

    ViewContext context = HttpView.currentContext();
    int permissions = context.getContainer().getAcl().getPermissions(context.getUser());
    List<ColumnInfo> cols = dataset.getTableInfo(context.getUser(), true, false).getColumns();

// UNDONE: clean way to get count of system fields???
    int systemCount = 7;
%>
<table>
    <tr>
<!--    <th>ID</th> -->
        <th>Name</th>
        <th>Label</th>
        <th>Type</th>
        <th>Format</th>
        <th>Required</th>
        <th>Description</th>
    </tr><%

    for (int i=0 ; i<systemCount ; i++)
    {
        ColumnInfo col = cols.get(i);
        %><tr>
            <td><%=h(col.getName())%></td>
            <td><%=h(col.getCaption())%></td>
            <td><%=h(col.getFriendlyTypeName())%></td>
            <td><%=h(col.getFormatString())%></td>
            <td align="center"><input type=checkbox disabled <%=col.isNullable() ? "" : "checked"%>></td>
            <td><%=h(col.getDescription())%></td>
          </tr><%
    }

%><tr><td colspan=6><hr height=1></td></tr><%

    for (int i = systemCount; i < cols.size(); i++)
    {
        ColumnInfo col = cols.get(i);
        boolean isKeyColumn = (StringUtils.equalsIgnoreCase(col.getName(), dataset.getKeyPropertyName()));
%>
        <tr>
            <td><%=isKeyColumn?"<b>":""%><%= col.getName()%><%=isKeyColumn?"</b>":""%></td>
            <td><%= col.getCaption() %></td>
            <td><%=h(col.getFriendlyTypeName())%></td>
            <td><%=h(col.getFormatString())%></td>
            <td align="center"><input type=checkbox disabled <%=col.isNullable() ? "" : "checked"%>></td>
            <td><%=h(col.getDescription())%></td>
        </tr>
        <%
    }
%>
</table>

<%
    if (0 != (permissions & ACL.PERM_ADMIN))
    {
        if (dataset.getTypeURI() == null)
        {
            %>[<a href="bulkImportDataTypes.view?">Bulk import dataset schemas</a>]<%
        }
    }
%>