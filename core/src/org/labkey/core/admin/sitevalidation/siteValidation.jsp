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
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    List<SiteValidationResult> siteResults = SiteValidationService.get().runSiteScopeValidators(getUser());
    Map<Container, List<SiteValidationResult>> containerResults = SiteValidationService.get().runContainerScopeValidators(getContainer(), getUser());
%>
<table>
    <tr>
        <td colspan="2"><b>Site Level Validation Results</b></td>
    </tr>
    <% for (SiteValidationResult result : siteResults) { %>
    <tr>
        <td>&nbsp;</td>
        <td><span class="<%=result.getLevel().equals(Level.ERROR) ? "labkey-error" : ""%>"><%=h(result.getMessage())%></span></td>
    </tr>
    <% } %>
</table>
<table>
    <tr>
        <td colspan="3"><b>Folder Validation Results</b></td>
    </tr>
    <% for (Map.Entry<Container, List<SiteValidationResult>> containerResult : containerResults.entrySet()) { %>
    <tr>
        <td>&nbsp;</td>
        <td><%=h(StringUtils.substringAfter(containerResult.getKey().getPath(), "/"))%></td>
    </tr>
        <% if (containerResult.getValue() != null) { for (SiteValidationResult result : containerResult.getValue()) { %>
        <tr>
            <td>&nbsp;</td>
            <td>&nbsp;</td>
            <td><span class="<%=result.getLevel().equals(Level.ERROR) ? "labkey-error" : ""%>"><%=h(result.getMessage())%></span></td>
        </tr>
        <% } } %>
    <% } %>
</table>

