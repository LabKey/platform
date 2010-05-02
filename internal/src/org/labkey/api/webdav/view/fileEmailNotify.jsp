<%
/*
 * Copyright (c) 2010 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.webdav.FileSystemResource" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    FileSystemResource.FileEmailForm bean = ((JspView<FileSystemResource.FileEmailForm>)HttpView.currentView()).getModelBean();
    ViewContext context = HttpView.currentContext();
    User user = context.getUser();
%>

<table>
    <tr class="labkey-alternate-row">
        <td>User</td><td><%=user.getDisplayName(context)%></td></tr>
<%
    if (bean.getResource().getFile().exists()) {
%>
    <tr class="labkey-row">
        <td>File</td><td><a href="<%=bean.getResource().getExecuteHref(context)%>"><%=bean.getResource().getName()%></a></td></tr>
<%
    } else {
%>
    <tr class="labkey-row">
        <td>File</td><td><%=bean.getResource().getName()%></td></tr>
<%
    }
%>
    <tr class="labkey-alternate-row">
        <td>Action</td><td><%=bean.getAction()%></td></tr>
</table>