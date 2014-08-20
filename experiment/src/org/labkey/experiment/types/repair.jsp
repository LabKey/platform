<%
/*
 * Copyright (c) 2012-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.exp.api.StorageProvisioner" %>
<%@ page import="org.labkey.api.exp.property.Domain" %>
<%@ page import="org.labkey.api.exp.property.DomainKind" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.experiment.types.TypesController" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
TypesController.RepairForm form = (TypesController.RepairForm) HttpView.currentModel();
Domain domain = form.domain;
DomainKind kind = domain ==null ? null : domain.getDomainKind();
StorageProvisioner.ProvisioningReport.DomainReport report = form.report;
ActionURL edit = null==kind ? null : kind.urlEditDefinition(domain, getViewContext());
ActionURL show = null==kind ? null : kind.urlShowData(domain, getViewContext());
boolean hasFix = false;
if (report != null)
{
for (StorageProvisioner.ProvisioningReport.ColumnStatus st : report.getColumns())
    if (!StringUtils.isEmpty(st.fix))
        hasFix = true;
}
%><table>
        <tr><th align=right>DomainKind</th><td><%=toCell(null==kind?"":kind.getKindName())%></td></tr>
        <tr><th align=right>DomainURI</th><td><%=toCell(form.uri)%></td></tr>
        <tr><th align=right>Database Table</th><td><%=toCell(null==report?"":report.getSchemaName() + "." +report.getTableName())%></td></tr>
    <tr><td colspan=2><table><tr>
<%
    if (null != show)
    {
        %><td><%= button("View data").href(show) %></td><%
    }
    if (null != edit)
    {
        %><td><%= button("Edit definition").href(edit) %></td><%
    }
    if (hasFix)
    {
        %><td><labkey:form method="POST"><%= button("Fix!").submit(true) %></labkey:form></td><%
    }
%></tr></table></td></tr></table>
<labkey:errors/>
<p></p>
<%
if (null == report)
{
    %>Could not generate report.  Contact LabKey support for more help.<%
}
else
{

    %><table><tr style="background-color:#cccccc;"><th>&nbsp;</th><th align=left>Property</th><th align=left>mv</th><th align=left>Column</th><th align=left>mv Column</th><th>Proposed fix</th></tr><%
    for (StorageProvisioner.ProvisioningReport.ColumnStatus st : report.getColumns())
    {
        if (st.hasProblem)
        {
            %><tr style="background-color:#ffcccc;"><%
        }
        else if (null != st.spec)
        {
            %><tr style="background-color:#eeeeee;"><%
        }
        else
        {
            %><tr style=""><%
        }
        %><td align=right><span style="font-size:7pt;"><%=null!=st.prop?""+st.prop.getPropertyId():"&nbsp;"%></span></td>
        <td><%=toCell(st.getName())%></td>
        <td><%=st.hasMv()?"X":"&nbsp;"%></td>
        <td><%=toCell(st.colName)%></td>
        <td><%=toCell(st.mvColName)%></td>
        <td><%=toCell(st.fix)%></td>
        </tr><%
    }
    %></table>
    <p></p>
<%
if (!report.getErrors().isEmpty())
{
    %><div><span class="labkey-error">errors</span><ul style="margin:0;"><%
    for (String error : report.getErrors())
    {
        if (error.contains("repair.view?"))
            continue;
        %><li><%=h(error)%></li><%
    }
    %></ul></div><%
}
}
%>
<%!
    String toCell(String s)
    {
        if (StringUtils.isEmpty(s))
            return "&nbsp;";
        return h(s);
    }
%>