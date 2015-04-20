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
<table>
    <thead>
        <tr>
            <th>Site Level Validation Results</th>
        </tr>
    </thead>
        <tbody>
        <%  List<SiteValidationResult> moduleInfos;
            List<SiteValidationResult> moduleErrors;
            List<SiteValidationResult> moduleWarnings;
            for (Map.Entry<String, SiteValidationResultList> moduleResults : siteResults.entrySet())
            {
                moduleInfos = moduleResults.getValue().getResults(Level.INFO);
                moduleWarnings = moduleResults.getValue().getResults(Level.WARN);
                moduleErrors = moduleResults.getValue().getResults(Level.ERROR);
        %>
            <tr>
                <td><%=h("Module: " + moduleResults.getKey())%></td>
            </tr>
        <% for (SiteValidationResult result : moduleInfos) { %>
        <tr>
            <td>&nbsp;&nbsp;<%=h(result.getMessage())%></td>
        </tr>
        <% } %>
        <% if (moduleErrors.size() > 0) { %>
            <tr><td>&nbsp;</td></tr>
            <tr>
                <td>&nbsp;&nbsp;Errors:</td>
            </tr>
            <% for (SiteValidationResult result : moduleErrors) { %>
            <tr>
                <td><span class="labkey-error">&nbsp;&nbsp;&nbsp;&nbsp;<%=h(result.getMessage())%></span></td>
            </tr>
            <% } %>
        <% } %>
        <% if (moduleWarnings.size() > 0) { %>
            <tr><td>&nbsp;</td></tr>
            <tr>
                <td>&nbsp;&nbsp;Warnings:</td>
            </tr>
            <% for (SiteValidationResult result : moduleWarnings) { %>
            <tr>
                <td>&nbsp;&nbsp;&nbsp;&nbsp;<%=h(result.getMessage())%></td>
            </tr>
            <% } %>
        <% } %>
        <% } %>
    </tbody>
</table>
<br/><br/>
<table>
    <thead>
    <tr>
        <th>Folder Validation Results</th>
    </tr>
    </thead>
    <tbody>
    <%
        List<SiteValidationResult> containerInfos;
        List<SiteValidationResult> containerErrors;
        List<SiteValidationResult> containerWarnings;
        Container titleProject = null;
        Container currentProject;
        for (Map.Entry<Container, SiteValidationResultList> containerResult : containerResults.entrySet()) {
            Container c = containerResult.getKey();
            currentProject = c.getProject();
            if (null != currentProject && !currentProject.equals(titleProject))
            {
                titleProject = currentProject;
    %>
            <tr><td>&nbsp;</td></tr>
            <tr>
                <td><%=h("Project: " + StringUtils.substringAfter(titleProject.getPath(), "/"))%></td>
            </tr>
        <% } %>
        <tr>
            <td>&nbsp;&nbsp;<%=h("Folder: " + StringUtils.substringAfter(c.getPath(), "/"))%></td>
        </tr>
        <% if (containerResult.getValue() != null)
        {
            containerInfos = containerResult.getValue().getResults(Level.INFO);
            containerErrors = containerResult.getValue().getResults(Level.ERROR);
            containerWarnings = containerResult.getValue().getResults(Level.WARN);
            for (SiteValidationResult result : containerInfos) { %>
            <tr>
                <td>&nbsp;&nbsp;&nbsp;&nbsp;<%=h(result.getMessage())%></td>
            </tr>
            <% } %>
            <% if (containerErrors.size() > 0) { %>
                <tr>
                    <td>&nbsp;&nbsp;&nbsp;&nbsp;Errors:</td>
                </tr>
                <% for (SiteValidationResult result : containerErrors) { %>
                <tr>
                    <td><span class="labkey-error">&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<%=h(result.getMessage())%></span></td>
                </tr>
                <% } %>
            <% } %>
            <% if (containerWarnings.size() > 0) { %>
                <tr>
                    <td>&nbsp;&nbsp;&nbsp;&nbsp;Warnings:</td>
                </tr>
                <% for (SiteValidationResult result : containerWarnings) { %>
                <tr>
                    <td>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<%=h(result.getMessage())%></td>
                </tr>
                <% } %>
            <% } %>
        <% } %>
    <% } %>
    </tbody>
</table>

