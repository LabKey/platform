<%
/*
 * Copyright (c) 2007-2011 LabKey Corporation
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
<%@ page import="org.labkey.api.audit.AuditLogService"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    String currentView = (String)HttpView.currentModel();
    AuditLogService.AuditViewFactory[] factories = AuditLogService.get().getAuditViewFactories();

    if (currentView == null)
        currentView = factories[0].getEventType();

%>
<form action="" method="get">
    <select name="view" onchange="this.form.submit()">
<%
    for (AuditLogService.AuditViewFactory factory : factories)
    {
%>
        <option value="<%=h(factory.getEventType())%>"<%=h(factory.getEventType().equals(currentView) ? " selected" : "")%>><%=h(factory.getName())%></option>
<%
    }
%>
    </select>
</form>