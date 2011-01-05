<%
/*
 * Copyright (c) 2008-2011 LabKey Corporation
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
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.pipeline.status.StatusController" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<StatusController.EnterprisePipelineBean> view = (JspView<StatusController.EnterprisePipelineBean>) HttpView.currentView();
    StatusController.EnterprisePipelineBean bean = view.getModelBean();
%>

<p>You are running the Enterprise Pipeline.</p>
<p>
    <labkey:button text="Force Status Refresh" href="<%= new ActionURL(StatusController.ForceRefreshAction.class, ContainerManager.getRoot()) %>" />
    <labkey:helpPopup title="Force Status Refresh">Under normal operations, this should not be required, but if there were network problems
    that prevented a callback with status information, this can update a job.</labkey:helpPopup>
</p>
Your configuration references the following execution locations:
<ul>
<% for (String location : bean.getLocations())
{    %>
    <li><%=h(location) %></li>
<%
}
%>
</ul>
