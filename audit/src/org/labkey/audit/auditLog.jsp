<%
/*
 * Copyright (c) 2007-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.audit.AuditTypeProvider" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="java.util.List" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    String currentView = (String)HttpView.currentModel();

    List<AuditTypeProvider> providers = AuditLogService.get().getAuditProviders();

    AuditTypeProvider selectedProvider = null;
    if (currentView == null)
    {
        currentView = providers.get(0).getEventName();
        selectedProvider = providers.get(0);
    }

%>
<labkey:form action="" method="GET">
    <select name="view" onchange="this.form.submit()">
<%
    for (AuditTypeProvider provider : providers)
    {
        if (provider.getEventName().equals(currentView))
            selectedProvider = provider;
%>
        <option value="<%=h(provider.getEventName())%>"<%=selected(provider.getEventName().equals(currentView))%> title="<%=h(provider.getDescription())%>"><%=h(provider.getLabel())%></option>
<%
    }
%>
%>
    </select>
    &nbsp;&nbsp;<%=h(selectedProvider.getDescription())%>
    <br/><br/>
</labkey:form>
