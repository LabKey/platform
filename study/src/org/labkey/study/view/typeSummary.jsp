<%
/*
 * Copyright (c) 2006-2009 LabKey Corporation
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
<%@ page import="org.labkey.api.security.permissions.AdminPermission"%>
<%@ page import="org.labkey.api.study.Study"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.api.view.ViewContext"%>
<%@ page import="org.labkey.study.model.DataSetDefinition" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<DataSetDefinition> me = (JspView<DataSetDefinition>) HttpView.currentView();
    DataSetDefinition dataset = me.getModelBean();
    Study study = dataset.getStudy();

    ViewContext context = HttpView.currentContext();
    List<ColumnInfo> allCols = dataset.getTableInfo(context.getUser(), true, false).getColumns();

    List<ColumnInfo> systemColumns = new ArrayList<ColumnInfo>();
    List<ColumnInfo> userColumns = new ArrayList<ColumnInfo>();

    for (ColumnInfo col : allCols)
    {
        if (DataSetDefinition.isDefaultFieldName(col.getName(), study))
        {
            if (DataSetDefinition.showOnManageView(col.getName(), study))
                systemColumns.add(col);
        }
        else
        {
            if (!col.isHidden()) // MV indicator and raw columns shouldn't be displayed
                userColumns.add(col);
        }
    }
%>
<table>
    <tr>
<!--    <th>ID</th> -->
        <th>Name</th>
        <th>Label</th>
        <th>Type</th>
        <th>Format</th>
        <th>Required</th>
        <th>Allows MV</th>
        <th>Description</th>
    </tr><%

    for (ColumnInfo col : systemColumns)
    {
        %><tr>
            <td><%=h(col.getName())%></td>
            <td><%=h(col.getLabel())%></td>
            <td><%=h(col.getFriendlyTypeName())%></td>
            <td><%=h(col.getFormat())%></td>
            <td align="center"><input type=checkbox disabled <%=col.isNullable() ? "" : "checked"%>></td>
            <td align="center"><input type=checkbox disabled <%=col.isMvEnabled() ? "checked" : ""%>></td>
            <td><%=h(col.getDescription())%></td>
          </tr><%
    }

%><tr><td colspan=6><hr height=1></td></tr><%

    for (ColumnInfo col : userColumns)
    {
        boolean isKeyColumn = (StringUtils.equalsIgnoreCase(col.getName(), dataset.getKeyPropertyName()));
%>
        <tr>
            <td><%=isKeyColumn?"<b>":""%><%= col.getName()%><%=isKeyColumn?"</b>":""%></td>
            <td><%= col.getLabel() %></td>
            <td><%=h(col.getFriendlyTypeName())%></td>
            <td><%=h(col.getFormat())%></td>
            <td align="center"><input type=checkbox disabled <%=col.isNullable() ? "" : "checked"%>></td>
            <td align="center"><input type=checkbox disabled <%=col.isMvEnabled() ? "checked" : ""%>></td>
            <td><%=h(col.getDescription())%></td>
        </tr>
        <%
    }
%>
</table>

<%
    if (context.getContainer().getPolicy().hasPermission(context.getUser(), AdminPermission.class))
    {
        if (dataset.getTypeURI() == null)
        {
            %>[<a href="bulkImportDataTypes.view?">Bulk import dataset schemas</a>]<%
        }
    }
%>