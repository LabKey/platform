<%
/*
 * Copyright (c) 2015 LabKey Corporation
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
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.admin.sitevalidation.SiteValidationResult" %>
<%@ page import="org.labkey.api.admin.sitevalidation.SiteValidationResult.Level" %>
<%@ page import="org.labkey.api.admin.sitevalidation.SiteValidationService" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.api.admin.sitevalidation.SiteValidationResultList" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    Map<String, SiteValidationResultList> siteResults = SiteValidationService.get().runSiteScopeValidators(getUser());
    Map<Container, SiteValidationResultList> containerResults = SiteValidationService.get().runContainerScopeValidators(getContainer(), getUser());
%>
<style type="text/css">
    ul {list-style-type: none; padding-left: 5px;}
</style>
<strong>Site Level Validation Results</strong>
<ul>
    <%  List<SiteValidationResult> moduleInfos;
        List<SiteValidationResult> moduleErrors;
        List<SiteValidationResult> moduleWarnings;
        for (Map.Entry<String, SiteValidationResultList> moduleResults : siteResults.entrySet())
        {
            moduleInfos = moduleResults.getValue().getResults(Level.INFO);
            moduleWarnings = moduleResults.getValue().getResults(Level.WARN);
            moduleErrors = moduleResults.getValue().getResults(Level.ERROR);
    %>
    <li><%=h("Module: " + moduleResults.getKey())%>
        <ul>
    <% for (SiteValidationResult result : moduleInfos) { %>
    <li>
        <%=h(result.getMessage())%>
    </li>
    <% } %>
    <% if (moduleErrors.size() > 0) { %>
            <li><br/></li>
            <li>Errors:
    <ul>
    <% for (SiteValidationResult result : moduleErrors) { %>
    <li>
        <span class="labkey-error"><%=h(result.getMessage())%></span>
    </li>
    <% } %>
    <% } %></ul></li>
    <% if (moduleWarnings.size() > 0) { %>
            <li>><br/></li>
            <li>Warnings:
    <ul>
    <% for (SiteValidationResult result : moduleWarnings) { %>
    <li>
        <%=h(result.getMessage())%>
    </li>
    <% } %></ul></li>
    <% } %></ul></li>
    <% } %>
</ul>

<br/><br/>
<strong>Folder Validation Results</strong>

<ul>
    <% if (containerResults.isEmpty()) { %>
       <li>No folder validators have been registered for configured folders.</li>
    <%}%>
    <%
        List<SiteValidationResult> containerInfos;
        List<SiteValidationResult> containerErrors;
        List<SiteValidationResult> containerWarnings;
        Container titleProject = null;
        Container currentProject;
        boolean seenFirstProject = false;
        for (Map.Entry<Container, SiteValidationResultList> containerResult : containerResults.entrySet()) {
            Container c = containerResult.getKey();
            currentProject = c.getProject();
            if (null != currentProject && !currentProject.equals(titleProject))
            {
                if (seenFirstProject)
                {   %>
                    </ul></li>
                <%}
                    seenFirstProject = true;
                titleProject = currentProject;
    %>
            <li><br/><%=h("Project: " + StringUtils.substringAfter(titleProject.getPath(), "/"))%>
                <ul>
        <% } %>
            <li><%=h("Folder: " + StringUtils.substringAfter(c.getPath(), "/"))%>
                <ul>
        <% if (containerResult.getValue() != null)
        {
            containerInfos = containerResult.getValue().getResults(Level.INFO);
            containerErrors = containerResult.getValue().getResults(Level.ERROR);
            containerWarnings = containerResult.getValue().getResults(Level.WARN);
            for (SiteValidationResult result : containerInfos) { %>
            <li><%=h(result.getMessage())%></li>
            <% } %>
            <% if (containerErrors.size() > 0) { %>
                <li>Errors:
                <ul>
                <% for (SiteValidationResult result : containerErrors) { %>
                <li><span class="labkey-error"><%=h(result.getMessage())%></span></li>
                <% } %></ul></li>
            <% } %>
            <% if (containerWarnings.size() > 0) { %>
                <li>Warnings:
                    <ul>
                    <% for (SiteValidationResult result : containerWarnings) { %>
                    <li><%=h(result.getMessage())%></li>
                <% } %></ul></li>
            <% } %>
        <% } %></ul>
    <% } %></li>
    <% if (!containerResults.isEmpty()) { %>
            </ul></li>
    <% } %>
</ul>

