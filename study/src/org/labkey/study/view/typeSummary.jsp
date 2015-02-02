<%
/*
 * Copyright (c) 2006-2015 LabKey Corporation
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
<%@ page import="org.apache.commons.lang3.StringUtils"%>
<%@ page import="org.labkey.api.data.ColumnInfo"%>
<%@ page import="org.labkey.api.exp.OntologyManager"%>
<%@ page import="org.labkey.api.exp.PropertyDescriptor"%>
<%@ page import="org.labkey.api.security.permissions.AdminPermission"%>
<%@ page import="org.labkey.api.study.Study"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.controllers.StudyController.BulkImportDataTypesAction" %>
<%@ page import="org.labkey.study.model.DatasetDefinition" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<DatasetDefinition> me = (JspView<DatasetDefinition>) HttpView.currentView();
    DatasetDefinition dataset = me.getModelBean();
    Study study = dataset.getStudy();

    List<ColumnInfo> allCols = dataset.getTableInfo(getUser(), true).getColumns();

    List<ColumnInfo> systemColumns = new ArrayList<>();
    List<ColumnInfo> userColumns = new ArrayList<>();

    for (ColumnInfo col : allCols)
    {
        if (DatasetDefinition.isDefaultFieldName(col.getName(), study))
        {
            if (DatasetDefinition.showOnManageView(col.getName(), study))
                systemColumns.add(col);
        }
        else
        {
             // MV indicator and raw columns shouldn't be displayed, since they're considered to be part of the primary
             // column for that property
            if (col.isMvIndicatorColumn() || col.isRawValueColumn())
                continue;

            // SystemProperties and properties from other containers should be listed above the line
            PropertyDescriptor pd = OntologyManager.getPropertyDescriptor(col.getPropertyURI(), study.getContainer());
            if (pd != null && !pd.getContainer().equals(dataset.getContainer()))
                systemColumns.add(col);
            else
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
        String type = col.getFriendlyTypeName();
        if (col.getName().equalsIgnoreCase("modifiedby")||col.getName().equalsIgnoreCase("createdby"))
            type = "User (Integer)";
        %><tr>
            <td><%=h(col.getName())%></td>
            <td><%=h(col.getLabel())%></td>
            <td><%=h(type)%></td>
            <td><%=h(col.getFormat())%></td>
            <td align="center"><input type=checkbox disabled<%=checked(!col.isNullable())%>></td>
            <td align="center"><input type=checkbox disabled<%=checked(col.isMvEnabled())%>></td>
            <td><%=h(col.getDescription())%></td>
          </tr><%
    }

%><tr><td colspan=6><hr height=1></td></tr><%

    for (ColumnInfo col : userColumns)
    {
        boolean isKeyColumn = (StringUtils.equalsIgnoreCase(col.getName(), dataset.getKeyPropertyName()));
%>
        <tr>
            <td><%=text(isKeyColumn ? "<b>" : "")%><%=h(col.getName())%><%=text(isKeyColumn ? "</b>" : "")%></td>
            <td><%=h(col.getLabel())%></td>
            <td><%=h(col.getFriendlyTypeName())%></td>
            <td><%=h(col.getFormat())%></td>
            <td align="center"><input type=checkbox disabled<%=checked(!col.isNullable())%>></td>
            <td align="center"><input type=checkbox disabled<%=checked(col.isMvEnabled())%>></td>
            <td><%=h(col.getDescription())%></td>
        </tr>
        <%
    }
%>
</table>

<%
    if (getViewContext().hasPermission(AdminPermission.class))
    {
        if (dataset.getTypeURI() == null)
        {
            %><%=textLink("Bulk import dataset schemas", BulkImportDataTypesAction.class)%><%
        }
    }
%>