<%
/*
 * Copyright (c) 2015-2016 LabKey Corporation
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
<%@ page import="org.labkey.api.admin.sitevalidation.SiteValidatorDescriptor" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<style type="text/css">
    ul {list-style-type: none; padding-left: 5px;}
</style>
<%
    final String LINK_HEADING = "More info";
    SiteValidationService validationService = ServiceRegistry.get().getService(SiteValidationService.class);
    if (null == validationService)
    { %>
        <span>SiteValidationService has not been registered.</span>
    <%}
    else { %>
        <% if (getContainer().isRoot()) {
            Map<String,Map<SiteValidatorDescriptor, SiteValidationResultList>> siteResults = validationService.runSiteScopeValidators(getUser());
        %>
            <strong>Site Level Validation Results</strong>
            <% if (siteResults.isEmpty()) { %>
            <span>No site-wide validators have been registered.</span>
            <% }
                else {
            %>
            <ul>
                <%  List<SiteValidationResult> infos;
                    List<SiteValidationResult> errors;
                    List<SiteValidationResult> warnings;
                    for (Map.Entry<String, Map<SiteValidatorDescriptor, SiteValidationResultList>> moduleResults : siteResults.entrySet())
                    {
                %>
                <li><strong>Module: </strong><%=h(moduleResults.getKey())%>
                    <ul>
                    <% for (Map.Entry<SiteValidatorDescriptor, SiteValidationResultList> results : moduleResults.getValue().entrySet()) { %>
                        <li><strong>Validator: </strong><%=h(results.getKey().getName() + " ")%><span style="font-style: italic;"><%=h(results.getKey().getDescription())%></span>
                            <ul>
                                <% if (results.getValue().getResults().isEmpty()) { %>
                                   <li>Nothing to report</li>
                                <% } else {
                                    infos = results.getValue().getResults(Level.INFO);
                                    warnings = results.getValue().getResults(Level.WARN);
                                    errors = results.getValue().getResults(Level.ERROR);
                                    for (SiteValidationResult result : infos) { %>
                                <li>
                                    <%=h(result.getMessage())%>
                                    <% if (null != result.getLink()) { %>
                                    <span><%=textLink(LINK_HEADING, result.getLink())%></span>
                                    <% } %>
                                </li>
                                <% } %>
                                <% if (errors.size() > 0) { %>
                                        <li><br/></li>
                                        <li>Errors:
                                <ul>
                                <% for (SiteValidationResult result : errors) { %>
                                <li>
                                    <span class="labkey-error"><%=h(result.getMessage())%></span>
                                    <% if (null != result.getLink()) { %>
                                    <span><%=textLink(LINK_HEADING, result.getLink())%></span>
                                    <% } %>
                                </li>
                                <% } %></ul>
                                <% } %>
                                <% if (warnings.size() > 0) { %>
                                        <%--<li><br/></li>--%>
                                        <li>Warnings:
                                <ul>
                                <% for (SiteValidationResult result : warnings) { %>
                                <li>
                                    <%=h(result.getMessage())%>
                                    <% if (null != result.getLink()) { %>
                                    <span><%=textLink(LINK_HEADING, result.getLink())%></span>
                                    <% } %>
                                </li>
                                <% } %></ul></li>
                                <% } %>
                    <% } %></ul></li>
                <% } %> </ul><br/></li>
             <% } %>
            </ul><% } %>
        <% } %>

        <strong>Folder Validation Results</strong>
        <%  Map<String, Map<SiteValidatorDescriptor, Map<String, Map<String, SiteValidationResultList>>>> containerResults = validationService.runContainerScopeValidators(getContainer(), getUser());
            if (containerResults.isEmpty()) { %>
            <span>No folder validators have been registered.</span>
        <%} else {
        %>
        <ul>
          <%
            List<SiteValidationResult> containerInfos;
            List<SiteValidationResult> containerErrors;
            List<SiteValidationResult> containerWarnings;

            for (Map.Entry<String, Map<SiteValidatorDescriptor, Map<String, Map<String, SiteValidationResultList>>>> moduleResults : containerResults.entrySet()) {
          %>
          <li><strong>Module: </strong><%=h(moduleResults.getKey())%>
              <ul>
                  <% for (Map.Entry<SiteValidatorDescriptor, Map<String, Map<String, SiteValidationResultList>>> validatorResults : moduleResults.getValue().entrySet()) { %>
                  <li><strong>Validator: </strong><%=h(validatorResults.getKey().getName() + " ")%><span style="font-style: italic;"><%=h(validatorResults.getKey().getDescription())%></span>
                      <ul>
                          <% if (validatorResults.getValue().isEmpty()) { %>
                          <li>Nothing to report</li>
                          <% } else {
                              for (Map.Entry<String, Map<String, SiteValidationResultList>> projectResult : validatorResults.getValue().entrySet()) { %>
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
                                <li><%=h(result.getMessage())%>
                                    <% if (null != result.getLink()) { %>
                                    <span><%=textLink(LINK_HEADING, result.getLink())%></span>
                                    <% } %>
                                </li>
                                <% } %>
                                <% if (containerErrors.size() > 0) { %>
                                    <li>Errors:
                                    <ul>
                                    <% for (SiteValidationResult result : containerErrors) { %>
                                        <li><span class="labkey-error"><%=h(result.getMessage())%></span>
                                        <% if (null != result.getLink()) { %>
                                        <span><%=textLink(LINK_HEADING, result.getLink())%></span>
                                        <% } %>
                                    </li>
                                    <% } %></ul></li>
                                <% } %>
                                <% if (containerWarnings.size() > 0) { %>
                                    <li>Warnings:
                                        <ul>
                                        <% for (SiteValidationResult result : containerWarnings) { %>
                                        <li><%=h(result.getMessage())%>
                                            <% if (null != result.getLink()) { %>
                                            <span><%=textLink(LINK_HEADING, result.getLink())%></span>
                                            <% } %>
                                        </li>
                                    <% } %></ul></li>
                                <% } %>
                            <% } %>
                        </ul>
                    </li>
                    <% } %>
                        </ul>
                    </li>
                  <% } %>
                        </ul>
                    </li>
                  <% } %>

                    <% } %>
                </ul><br/>
            </li>
            <% } %>
        </ul>
    <% } %>
<% } %>
