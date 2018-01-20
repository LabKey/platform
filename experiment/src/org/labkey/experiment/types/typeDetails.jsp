<%
/*
 * Copyright (c) 2006-2016 LabKey Corporation
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
<%@ page import="org.labkey.api.exp.PropertyDescriptor"%>
<%@ page import="org.labkey.api.exp.PropertyType"%>
<%@ page import="org.labkey.experiment.types.TypesController.TypeDetailsAction"%>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.exp.DomainDescriptor" %>
<%@ page import="org.labkey.api.exp.TemplateInfo" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    TypeDetailsAction bean = (TypeDetailsAction)getModelBean();
    String typeName = bean.typeName;
    List<PropertyDescriptor> properties = bean.properties;
    DomainDescriptor dd = bean.dd;

    String name = dd == null ? typeName : dd.getName();
%><h2><%=h(name)%></h2>

<% if (dd != null) { %>
<table class="labkey-data-region-legacy labkey-show-borders">
    <tr class="labkey-row">
        <td class="lk-form-label"><b>Description</b></td>
        <td><%=h(dd.getDescription())%></td>
    </tr>
    <tr class="labkey-row">
        <td class="lk-form-label"><b>Type URI</b></td>
        <td><%=h(dd.getDomainURI())%></td>
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
        <td><%=h(dd.isProvisioned())%></td>
    </tr>
</table>
<% } %>

<br/>
<h3>Properties</h3>
<table class="labkey-data-region-legacy labkey-show-borders">
    <thead>
    <tr>
        <td class="labkey-column-header">Name</td>
        <td class="labkey-column-header">PropertyURI</td>
        <td class="labkey-column-header">Type</td>
    </tr>
    </thead>
    <tbody>
    </tbody>
    <%
        int i = 0;
        for (PropertyDescriptor pd : properties)
        {
            i++;
            PropertyType t = pd.getPropertyType();
    %>
    <tr class="<%=text(i % 2 == 0 ? "labkey-row" : "labkey-alternate-row")%>">
        <td><%=h(pd.getName())%>
        </td>
        <td><%=h(pd.getPropertyURI())%>
        </td>
        <td><%=h(t.getXmlName())%>
        </td>
    </tr>
    <%
        }
    %>
</table>
