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
<%@ page import="org.labkey.api.admin.sitevalidation.SiteValidationResult" %>
<%@ page import="org.labkey.api.admin.sitevalidation.SiteValidationResult.Level" %>
<%@ page import="org.labkey.api.admin.sitevalidation.SiteValidationResultList" %>
<%@ page import="org.labkey.api.admin.sitevalidation.SiteValidationService" %>
<%@ page import="org.labkey.api.services.ServiceRegistry" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    SiteValidationService validationService = ServiceRegistry.get().getService(SiteValidationService.class);
    if (null == validationService)
    { %>
        <span>SiteValidationService has not been registered.</span>
    <%}
    else {

        Map<String, SiteValidationResultList> siteResults = validationService.runSiteScopeValidators(getUser());
        Map<String, Map<String, SiteValidationResultList>> containerResults = validationService.runContainerScopeValidators(getContainer(), getUser());
    %>
    <style type="text/css">
        ul {list-style-type: none; padding-left: 5px;}
    </style>
    <strong>Site Level Validation Results</strong>
    <% if (!validationService.hasSiteValidators()) { %>
    <span>No site-wide validators have been registered.</span>
    <% }
        else {
    %>
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
                <li><br/></li>
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
    <% } %>

    <br/><br/>
    <strong>Folder Validation Results</strong>
    <% if (!validationService.hasContainerValidators()) { %>
        <span>No folder validators have been registered.</span>
    <%} else { %>
    <ul>
      <%
        List<SiteValidationResult> containerInfos;
        List<SiteValidationResult> containerErrors;
        List<SiteValidationResult> containerWarnings;

        for (Map.Entry<String, Map<String, SiteValidationResultList>> projectResult : containerResults.entrySet()) {
      %>
        <li><%=h("Project: " + projectResult.getKey())%>
            <ul>
                <% for (Map.Entry<String, SiteValidationResultList> subtreeResult : projectResult.getValue().entrySet()) { %>
                <li><%=h("Folder: " + subtreeResult.getKey())%>
                    <ul>
                        <% if (subtreeResult.getValue() != null)
                        {
                            containerInfos = subtreeResult.getValue().getResults(Level.INFO);
                            containerErrors = subtreeResult.getValue().getResults(Level.ERROR);
                            containerWarnings = subtreeResult.getValue().getResults(Level.WARN);
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
                        <% } %>
                    </ul>
                </li>
                <% } %>
            </ul><br/>
        </li>
        <% } %>
    </ul>
    <% } %>
<% } %>
