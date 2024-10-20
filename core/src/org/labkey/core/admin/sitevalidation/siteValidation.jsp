<%
/*
 * Copyright (c) 2015-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.admin.sitevalidation.SiteValidatorDescriptor" %>
<%@ page import="org.labkey.core.admin.AdminController.SiteValidationForm" %>
<%@ page import="java.lang.String" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    void info(SiteValidationForm form, String message)
    {
        form.getLogger().accept(message);
    }
%>
<style type="text/css">
    ul {list-style-type: none; padding-left: 5px;}
</style>
<%
    SiteValidationForm form = (SiteValidationForm)getModelBean();
    final String LINK_HEADING = "More info";
    SiteValidationService validationService = SiteValidationService.get();
    if (null == validationService)
    {
%>
        <span>SiteValidationService has not been registered.</span>
<%
    }
    else
    {
        if (getContainer().isRoot())
        {
%>
            <strong>Site Level Validation Results</strong>
<%
            if (validationService.getSiteFactories().isEmpty())
            {
                info(form, "No site-wide validators are registered");
%>
                <p>No site-wide validators are registered.</p>
<%
            }
            else
            {
                info(form, "Running selected site-wide validators");
                Map<String, Map<SiteValidatorDescriptor, SiteValidationResultList>> siteResults = validationService.runSiteScopeValidators(form.getProviders(), getUser());
                if (siteResults.isEmpty())
                {
                    info(form, "No site-wide validators were selected");
%>
                    <p>No site-wide validators were selected.</p>
<%
                }
                else
                {
%>
                <ul>
<%
                    List<SiteValidationResult> infos;
                    List<SiteValidationResult> errors;
                    List<SiteValidationResult> warnings;

                    for (Map.Entry<String, Map<SiteValidatorDescriptor, SiteValidationResultList>> moduleResults : siteResults.entrySet())
                    {
%>
                    <li><strong>Module: </strong><%=h(moduleResults.getKey())%>
                        <ul>
<%
                        for (Map.Entry<SiteValidatorDescriptor, SiteValidationResultList> results : moduleResults.getValue().entrySet())
                        {
%>
                            <li><strong>Validator: </strong><%=h(results.getKey().getName() + " ")%><span style="font-style: italic;"><%=h(results.getKey().getDescription())%></span>
                                <ul>
<%
                            if (results.getValue().getResults().isEmpty())
                            {
%>
                                   <li>Nothing to report</li>
<%
                            }
                            else
                            {
                                infos = results.getValue().getResults(Level.INFO);
                                warnings = results.getValue().getResults(Level.WARN);
                                errors = results.getValue().getResults(Level.ERROR);

                                for (SiteValidationResult result : infos)
                                {
%>
                                    <li>
                                        <%=result.getMessage()%>
<%
                                    if (null != result.getLink())
                                    {
%>
                                        <span><%=link(LINK_HEADING, result.getLink())%></span>
<%
                                    }
%>
                                    </li>
<%
                                    }

                                    if (!errors.isEmpty())
                                    {
%>
                                    <li>Errors:
                                        <ul>
<%
                                        for (SiteValidationResult result : errors)
                                        {
%>
                                            <li>
                                        <span class="labkey-error"><%=result.getMessage()%></span>
<%
                                            if (null != result.getLink())
                                            {
%>
                                        <span><%=link(LINK_HEADING, result.getLink())%></span>
<%
                                            }
%>
                                            </li>
<%
                                        }
%>
                                        </ul>
<%
                                    }

                                    if (!warnings.isEmpty())
                                    {
%>
                                            <li>Warnings:
                                                <ul>
<%
                                        for (SiteValidationResult result : warnings)
                                        {
%>
                                                    <li>
                                        <%=result.getMessage()%>
<%
                                            if (null != result.getLink())
                                            {
%>
                                        <span><%=link(LINK_HEADING, result.getLink())%></span>
<%
                                            }
%>
                                                    </li>
<%
                                        }
%>                                              </ul>
                                            </li>
<%
                                    }
                                }
%>                              </ul>
                            </li>
<%
                            }
%>                      </ul><br/>
                    </li>
<%
                        }
%>
                </ul>
<%
                }
            }
        }
%>
        <strong>Folder Validation Results</strong>
<%
        info(form, "Running all selected folder validators for all selected folders");
        Map<String, Map<SiteValidatorDescriptor, Map<String, Map<String, SiteValidationResultList>>>> containerResults = validationService.runContainerScopeValidators(getContainer(), form.isIncludeSubfolders(), form.getProviders(), getUser());
        if (containerResults.isEmpty())
        {
%>
            <p>No folder validators were selected.</p>
<%
        }
        else
        {
%>
        <ul>
<%
            List<SiteValidationResult> containerInfos;
            List<SiteValidationResult> containerErrors;
            List<SiteValidationResult> containerWarnings;

            for (Map.Entry<String, Map<SiteValidatorDescriptor, Map<String, Map<String, SiteValidationResultList>>>> moduleResults : containerResults.entrySet())
            {
%>
            <li><strong>Module: </strong><%=h(moduleResults.getKey())%>
                <ul>
<%
                for (Map.Entry<SiteValidatorDescriptor, Map<String, Map<String, SiteValidationResultList>>> validatorResults : moduleResults.getValue().entrySet())
                {
%>
                    <li><strong>Validator: </strong><%=h(validatorResults.getKey().getName() + " ")%><span style="font-style: italic;"><%=h(validatorResults.getKey().getDescription())%></span>
                        <ul>
<%
                    if (validatorResults.getValue().isEmpty())
                    {
%>
                            <li>Nothing to report</li>
<%
                    }
                    else
                    {
                        for (Map.Entry<String, Map<String, SiteValidationResultList>> projectResult : validatorResults.getValue().entrySet())
                        {
                            String projectKey = projectResult.getKey();
%>
                            <li><%=h(!projectKey.isEmpty() ? "Project: " + projectKey : "Root: /")%>
                                <ul>
<%
                            for (Map.Entry<String, SiteValidationResultList> subtreeResult : projectResult.getValue().entrySet())
                            {
                                String subtreeKey = subtreeResult.getKey();
                                SiteValidationResultList resultList = subtreeResult.getValue();
%>
                                    <li><%=h(!projectKey.equals(subtreeKey) ? "Folder: " + subtreeKey : "")%>
                                        <ul>
<%
                                if (resultList != null)
                                {
                                    containerInfos = resultList.getResults(Level.INFO);
                                    containerErrors = resultList.getResults(Level.ERROR);
                                    containerWarnings = resultList.getResults(Level.WARN);
                                    for (SiteValidationResult result : containerInfos)
                                    {
%>
                                    <li><%=result.getMessage()%>
                                        <% if (null != result.getLink()) { %>
                                        <span><%=link(LINK_HEADING, result.getLink())%></span>
                                        <% } %>
                                    </li>
                                    <% } %>
                                    <% if (!containerErrors.isEmpty()) { %>
                                    <li>Errors:
                                        <ul>
                                        <% for (SiteValidationResult result : containerErrors) { %>
                                            <li><span class="labkey-error"><%=result.getMessage()%></span>
                                            <% if (null != result.getLink()) { %>
                                            <span><%=link(LINK_HEADING, result.getLink())%></span>
                                            <% } %>
                                            </li>
                                        <% } %>
                                        </ul>
                                    </li>
<%
                                    }
                                    if (!containerWarnings.isEmpty())
                                    {
%>
                                    <li>Warnings:
                                        <ul>
                                        <%  for (SiteValidationResult result : containerWarnings)
                                            { %>
                                        <li><%=result.getMessage()%>
                                            <% if (null != result.getLink()) { %>
                                            <span><%=link(LINK_HEADING, result.getLink())%></span>
                                            <% } %>
                                        </li>
                                        <% } %>
                                        </ul>
                                    </li>
<%
                                    }
                                }
%>
                                </ul>
                            </li>
<%
                            }
%>
                                </ul>
                            </li>
<%
                        }
                    }
%>
                        </ul>
                    </li>
<%
                }
%>
                </ul><br/>
            </li>
<%
            }
%>
        </ul>
<%
        }
    }
%>
