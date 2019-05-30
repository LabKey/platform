<%
/*
 * Copyright (c) 2006-2018 LabKey Corporation
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
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.api.exp.property.Domain"%>
<%@ page import="org.labkey.api.exp.property.DomainKind" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.experiment.types.TypesController" %>
<%@ page import="org.labkey.experiment.types.TypesController.TypeBean" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    TypeBean bean = (TypeBean)getModelBean();
%>

<b>Domain Kinds:</b>
<form method="GET" action="experiment-types-types.view">
    <labkey:select label="Domain Kinds" name="domainKind" onChange="this.form.submit();">
        <labkey:options value="<%=h(bean.domainKind)%>" set="<%=bean.domainKinds.keySet()%>"/>
    </labkey:select>
</form>

<h3>local types</h3>
<table>
    <% for (Domain type : bean.locals.values()) { %>
<tr>
    <td><%=PageFlowUtil.unstyledTextLink(type.getName(), new ActionURL(TypesController.TypeDetailsAction.class, getContainer()).addParameter("type", type.getTypeURI()))%></td>
    <%
        DomainKind kind = type.getDomainKind();
        if (kind != null)
        {
            ActionURL showData = kind.urlShowData(type, getViewContext());
            ActionURL editType = kind.urlEditDefinition(type, getViewContext());
            %>
    <td><small><%=h(kind.getKindName())%></small></td>
    <td><% if (showData != null) { %><%=link("view data", showData)%><% } %></td>
    <td><% if (editType != null) { %><%=link("edit type", editType)%><% } %></td>
            <%
        }
    %>
</tr>
    <% } %>
</table>

<h3>project types</h3>
<table>
    <% for (Domain type : bean.project.values()) { %>
    <tr>
        <td><%=PageFlowUtil.unstyledTextLink(type.getName(), new ActionURL(TypesController.TypeDetailsAction.class, type.getContainer()).addParameter("type", type.getTypeURI()))%></td>
        <%
            DomainKind kind = type.getDomainKind();
            if (kind != null)
            {
                ActionURL showData = kind.urlShowData(type, getViewContext());
                ActionURL editType = kind.urlEditDefinition(type, getViewContext());
        %>
        <td><small><%=h(kind.getKindName())%></small></td>
        <td><% if (showData != null) { %><%=link("view data", showData)%><% } %></td>
        <td><% if (editType != null) { %><%=link("edit type", editType)%><% } %></td>
        <%
            }
        %>
    </tr>
    <% } %>
</table>

<h3>global types</h3>
<table>
    <% for (Domain type : bean.globals.values()) { %>
    <tr>
        <td><%=PageFlowUtil.unstyledTextLink(type.getName(), new ActionURL(TypesController.TypeDetailsAction.class, type.getContainer()).addParameter("type", type.getTypeURI()))%></td>
        <%
            DomainKind kind = type.getDomainKind();
            if (kind != null)
            {
                ActionURL showData = kind.urlShowData(type, getViewContext());
                ActionURL editType = kind.urlEditDefinition(type, getViewContext());
        %>
        <td><small><%=h(kind.getKindName())%></small></td>
        <td><% if (showData != null) { %><%=link("view data", showData)%><% } %></td>
        <td><% if (editType != null) { %><%=link("edit type", editType)%><% } %></td>
        <%
            }
        %>
    </tr>
    <% } %>
</table>


