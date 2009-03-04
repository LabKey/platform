<%
/*
 * Copyright (c) 2005-2009 LabKey Corporation
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
<%@ page import="org.labkey.api.exp.api.ExpProtocol"%>
<%@ page import="org.labkey.api.exp.api.ExpRun" %>
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ExpRun> me = (JspView<ExpRun>) HttpView.currentView();
    ExpRun run = me.getModelBean();
    ExpProtocol protocol = run.getProtocol();
%>

<table>
    <tr>
        <td class="labkey-form-label">Name</td>
        <td><%= h(run.getName()) %></td>
    </tr>
    <tr>
        <td class="labkey-form-label">Protocol</td>
        <td><a href="protocolDetails.view?rowId=<%= protocol.getRowId() %>"><%= h(protocol.getName()) %></a></td>
    </tr>
    <tr>
        <td class="labkey-form-label">Created on</td>
        <td><%=h(DateUtil.formatDateTime(run.getCreated())) %></td>
    </tr>
    <tr>
        <td class="labkey-form-label">Comments</td>
        <td><%= h(run.getComments()) %></td>
    </tr>
</table>
