<%
/*
 * Copyright (c) 2009-2017 LabKey Corporation
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
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.pipeline.PipelineService" %>
<%@ page import="org.labkey.api.pipeline.PipelineUrls" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.study.permissions.DesignAssayPermission" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.study.assay.AssayManager" %>
<%@ page import="org.labkey.study.controllers.assay.AssayController" %>
<%@ page import="org.labkey.api.security.permissions.AdminOperationsPermission" %>
<%@ page import="org.labkey.study.xml.impl.AssayDefinitionImpl" %>
<%@ page import="org.labkey.api.util.HelpTopic" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("clientapi/ext3");
    }
%>
<%
    Container c = getContainer();
    User user = getUser();

    boolean hasAssayProtocols = !AssayManager.get().getAssayProtocols(c).isEmpty();
    boolean canDesignAssays = c.hasPermission(user, DesignAssayPermission.class);

    PipelineService pipeService = PipelineService.get();
    boolean hasPipelineRoot = pipeService.hasValidPipelineRoot(c);
    boolean canSetPipelineRoot = c.hasPermission(user, AdminOperationsPermission.class);
%>

<% if (!hasAssayProtocols) { %>
<p>
    <div>
        <em>No assay designs are available in this folder. </em>
        <%=text(new HelpTopic("defineAssaySchema").getLinkHtml("Assay Help"))%>
    </div>
</p>
<% } %>

<% if (!hasPipelineRoot) { %>
<p>
    <em>Pipeline root has not been set.</em>
    <% if (canSetPipelineRoot) { %>
        <labkey:link href="<%=urlProvider(PipelineUrls.class).urlSetup(c).getLocalURIString()%>" text="setup pipeline"/>
    <% } else { %>
        Please ask an administrator for assistance.
    <% } %>
<p class="labkey-indented">
    Assay data cannot be uploaded until you configure a <a target="_blank" href="https://www.labkey.org/wiki/home/Documentation/page.view?name=pipelineSetup">pipeline root</a>
    directory that will contain the files uploaded as part of assays.
<% } %>
