<%
/*
 * Copyright (c) 2009-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.data.TableInfo"%>
<%@ page import="org.labkey.api.exp.DomainDescriptor"%>
<%@ page import="org.labkey.api.exp.PropertyDescriptor"%>
<%@ page import="org.labkey.api.exp.PropertyType" %>
<%@ page import="org.labkey.api.exp.TemplateInfo" %>
<%@ page import="org.labkey.api.exp.property.Domain" %>
<%@ page import="org.labkey.api.exp.property.DomainKind" %>
<%@ page import="org.labkey.api.exp.property.PropertyService" %>
<%@ page import="org.labkey.api.query.QueryUrls" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.experiment.types.TypesController.TypeDetailsAction" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Set" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    TypeDetailsAction bean = (TypeDetailsAction)getModelBean();
    String typeName = bean.typeName;
    List<PropertyDescriptor> properties = bean.properties;
    DomainDescriptor dd = bean.dd;

    Domain d = dd != null ? d = PropertyService.get().getDomain(dd.getDomainId()) : null;
    DomainKind kind = d != null ? d.getDomainKind() : null;

    String name = dd == null ? typeName : dd.getName();

    ActionURL urlView = kind != null ? kind.urlShowData(d, getViewContext()) : null;
    ActionURL urlEdit = kind != null ? kind.urlEditDefinition(d, getViewContext()) : null;

    TableInfo table = kind != null ? kind.getTableInfo(getUser(), getContainer(), d.getName(), null) : null;
    ActionURL urlSchemaBrowser = table != null ? PageFlowUtil.urlProvider(QueryUrls.class).urlSchemaBrowser(getContainer(), table.getPublicSchemaName(), table.getPublicName()) : null;

%>
<h2><%=h(name)%></h2>
<% if (dd != null) { %>
<table class="labkey-data-region-legacy labkey-show-borders">
    <tr class="labkey-row">
        <td class="lk-form-label"><b>Domain Id</b></td>
        <td><%=dd.getDomainId()%></td>
    </tr>
    <tr class="labkey-row">
        <td class="lk-form-label"><b>Type URI</b></td>
        <td><%=h(dd.getDomainURI())%></td>
    </tr>
    <tr class="labkey-row">
        <td class="lk-form-label"><b>Description</b></td>
        <td><%=h(dd.getDescription())%></td>
    </tr>
    <tr class="labkey-row">
        <td class="lk-form-label"><b>Kind</b></td>
        <td><%=h(kind != null ? kind.getKindName() : "")%></td>
    </tr>
    <tr>
        <td><b>Definition Container</b></td>
        <td><%=h(dd.getContainer().getPath())%></td>
    </tr>
    <tr class="labkey-row">
        <td class="lk-form-label"><b>Template</b></td>
        <td>
            <%
                TemplateInfo template = dd.getTemplateInfo();
                if (template != null)
                {
                    %>
            Module: <%=h(template.getModuleName())%> <%=h(template.getCreatedModuleVersion())%><br>
            Group: <%=h(template.getTemplateGroupName())%><br>
            Table: <%=h(template.getTableName())%><br>
                    <%
                }
            %>
        </td>
    </tr>
    <tr class="labkey-row">
        <td class="lk-form-label"><b>Provisioned</b></td>
        <td><%=dd.isProvisioned()%></td>
    </tr>
    <tr class="labkey-row">
        <td class="lk-form-label"><b>Query Table</b></td>
        <td>
            <% if (table != null) { %>
                <a href="<%=h(urlSchemaBrowser)%>">
                <%=h(table.getPublicSchemaName() + " " + table.getPublicName())%>
                </a>
            <% } %>
        </td>
    </tr>
</table>
<% } %>

<% if (urlView != null || urlEdit != null) { %>
<div style="margin-top:0.5em; margin-left: 0.5em;">
    <% if (urlView != null) { out.println(link("view", urlView)); } %>
    <% if (urlEdit != null) { out.println(link("edit", urlEdit)); } %>
</div>
<% } %>

<br/>
<h3>Properties</h3>
<table class="labkey-data-region-legacy labkey-show-borders">
    <thead>
    <tr>
        <td class="labkey-column-header"><b>ID</b></td>
        <td class="labkey-column-header"><b>Name</b></td>
        <td class="labkey-column-header"><b>Type</b></td>
        <td class="labkey-column-header"><b>Required</b></td>
        <td class="labkey-column-header"><b>Mandatory</b></td>
        <td class="labkey-column-header"><b>PropertyURI</b></td>
    </tr>
    </thead>
    <tbody>
    </tbody>
    <%
        Set<String> mandatoryNames = kind != null ? kind.getMandatoryPropertyNames(d) : Collections.emptySet();

        int i = 0;
        for (PropertyDescriptor pd : properties)
        {
            i++;
            PropertyType t = pd.getPropertyType();
    %>
    <tr class="<%=text(i % 2 == 0 ? "labkey-row" : "labkey-alternate-row")%>">
        <td><%=h(pd.getPropertyId())%></td>
        <td><%=h(pd.getName())%></td>
        <td><%=h(pd.getPropertyType().getXmlName())%></td>
        <td><%=pd.isRequired()%></td>
        <td><%=mandatoryNames.contains(pd.getName())%></td>
        <td><%=h(pd.getPropertyURI())%></td>
    </tr>
    <%
        }
    %>
</table>
