<%
/*
 * Copyright (c) 2013 LabKey Corporation
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
<%@ page import="org.labkey.api.pipeline.TaskFactory" %>
<%@ page import="org.labkey.api.pipeline.PipelineJobService" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="java.util.Collection" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.pipeline.analysis.AnalysisController" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    Collection<TaskFactory> factories = PipelineJobService.get().getTaskFactories(null);
%>

<labkey:errors />

<p>Registered Tasks:</p>

<table>
    <tr>
        <td>&nbsp;</td>
        <td><b>Task Id</b></td>
        <td><b>Status Name</b></td>
        <td><b>Execution Location</b></td>
        <td><b>Group Parameter Name</b></td>
        <td><b>Join?</b></td>
        <td><b>Auto Retry</b></td>
        <td><b>Input Types</b></td>
        <td><b>Protocol Action Names</b></td>
    </tr>

<% for (TaskFactory factory : factories) { %>
    <tr>
        <td><%=PageFlowUtil.textLink("details", new ActionURL(AnalysisController.InternalDetailsAction.class, getContainer()).addParameter("taskId", factory.getId().toString()))%></td>
        <td><%=h(factory.getId())%></td>
        <td><%=h(factory.getStatusName())%></td>
        <td><%=h(factory.getExecutionLocation())%></td>
        <td><%=h(factory.getGroupParameterName() != null ? factory.getGroupParameterName() : "")%></td>
        <td><%=h(factory.isJoin() ? "true" : "")%></td>
        <td><%=h(factory.getAutoRetry() > 0 ? factory.getAutoRetry() : "")%></td>
        <td><%=h(StringUtils.join(factory.getInputTypes(), ", "))%></td>
        <td><%=h(StringUtils.join(factory.getProtocolActionNames(), ", "))%></td>
    </tr>
<% } %>

</table>

