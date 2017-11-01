<%
/*
 * Copyright (c) 2005-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.exp.api.ExperimentUrls" %>
<%@ page import="org.labkey.api.pipeline.PipelineStatusUrls" %>
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.experiment.controllers.exp.ExperimentController" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ExpRun> me = (JspView<ExpRun>) HttpView.currentView();
    ExpRun run = me.getModelBean();
    ExpProtocol protocol = run.getProtocol();
    ExpRun replacedByRun = run.getReplacedByRun();
    List<? extends ExpRun> replacesRuns = run.getReplacesRuns();
%>

<table class="lk-fields-table">
    <tr>
        <td class="labkey-form-label">Name</td>
        <td><%= h(run.getName()) %></td>
    </tr>
    <tr>
        <td class="labkey-form-label">Protocol</td>
        <td><a href="<%=h(buildURL(ExperimentController.ProtocolDetailsAction.class, "rowId=" + protocol.getRowId()))%>"><%= h(protocol.getName()) %></a></td>
    </tr>
    <tr>
        <td class="labkey-form-label">Job Id</td>
        <% if(run.getJobId() != null) { %>
            <td><a href="<%=h(PageFlowUtil.urlProvider(PipelineStatusUrls.class).urlDetails(run.getContainer(), run.getJobId()))%>"><%= h(run.getJobId()) %></a></td>
        <% } %>
    </tr>
    <tr>
        <td class="labkey-form-label">Created</td>
        <td><%=formatDateTime(run.getCreated())%></td>
    </tr>
    <tr>
        <td class="labkey-form-label">Modified</td>
        <td><%=formatDateTime(run.getModified())%></td>
    </tr>
    <tr>
        <td class="labkey-form-label">Comments</td>
        <td><%= h(run.getComments()) %></td>
    </tr>
    <tr>
        <td class="labkey-form-label">Replaced By</td>
        <% if(replacedByRun != null) { %>
            <td><a href="<%= h(PageFlowUtil.urlProvider(ExperimentUrls.class).getRunGraphURL(replacedByRun)) %>"><%= h(replacedByRun.getName()) %></a></td>
        <% } %>
    </tr>
    <tr>
        <td class="labkey-form-label">Replaces</td>
        <% for (ExpRun replacesRun : replacesRuns) { %>
            <td><a href="<%= h(PageFlowUtil.urlProvider(ExperimentUrls.class).getRunGraphURL(replacesRun)) %>"><%= h(replacesRun.getName()) %></a></td>
        <% } %>
    </tr>
</table>

<div id="formTarget" />
