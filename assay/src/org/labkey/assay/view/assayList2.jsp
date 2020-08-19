<%
/*
 * Copyright (c) 2009-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.assay.AssayService" %>
<%@ page import="org.labkey.api.assay.AssayUrls" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerFilter" %>
<%@ page import="org.labkey.api.exp.api.ExpProtocol" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.security.permissions.InsertPermission" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    Container c = getContainer();
    Container proj = c.getProject();
    AssayUrls urls = urlProvider(AssayUrls.class);

    List<ExpProtocol> protocols = AssayService.get().getAssayProtocols(c);
    for (ExpProtocol protocol : protocols)
    {
        ActionURL url;
        if (protocol.getContainer().equals(proj))
        {
            url = urls.getAssayRunsURL(proj, protocol);
            url.addParameter(protocol.getName() + " Runs.containerFilterName", ContainerFilter.AllInProject.class.getSimpleName());
        }
        else
            url = urls.getSummaryRedirectURL(c);

        url.replaceParameter("rowId", protocol.getRowId());
        %>
<b><a href="<%=url%>"><%=h(protocol.getName())%></a></b>
<%      if (null != protocol.getDescription())
        {   %>
            <br><%=h(protocol.getDescription())%>
     <% }  %>
        <br>
<%
    }
%>

<table>
    <tr>
<%
    if (proj.hasPermission(getUser(), InsertPermission.class))
    {
%>
        <td style="padding-right: 5px;"><%= button("Import Data").href(new ActionURL("assay", "assayDataImport", proj)) %></td>
<%
    }

    if (proj.hasPermission(getUser(), AdminPermission.class))
    {
%>
        <td><%= button("Manage Assays").href(urls.getBeginURL(proj)) %></td>
<%
    }
%>
    </tr>
</table>