<%
/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.audit.AuditLogEvent"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.api.exp.OntologyManager" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.audit.AuditLogService" %>
<%@ page import="org.labkey.experiment.list.ListManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    JspView<AuditLogEvent> me = (JspView<AuditLogEvent>) HttpView.currentView();
    AuditLogEvent bean = me.getModelBean();

    Map<String, Object> dataMap = OntologyManager.getProperties(ContainerManager.getSharedContainer().getId(), bean.getLsid());
%>


<table><tr><td class="ms-searchform"><%=bean.getComment()%></td></tr></table><br/>
<%=dataMap.get(AuditLogService.get().getPropertyURI(ListManager.LIST_AUDIT_EVENT, "modifications"))%>