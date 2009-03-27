<%
/*
 * Copyright (c) 2007-2009 LabKey Corporation
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
<%@ page import="java.util.List" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.Comparator" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    String currentView = (String)request.getAttribute("currentView");
    List<AuditLogService.AuditViewFactory> factories = Arrays.asList(AuditLogService.get().getAuditViewFactories());

    Collections.sort(factories, new Comparator<AuditLogService.AuditViewFactory>(){
        public int compare(AuditLogService.AuditViewFactory o1, AuditLogService.AuditViewFactory o2)
        {
            return (o1.getName().compareToIgnoreCase(o2.getName()));
        }
    });
    
    if (currentView == null)
        currentView = factories.get(0).getEventType();

%>
<form action="" method="get">
    <select name="view" onchange="this.form.submit()">
<%
    for (AuditLogService.AuditViewFactory factory : factories)
    {
%>
        <option value="<%=factory.getEventType()%>" <%=factory.getEventType().equals(currentView) ? "selected" : ""%>><%=h(factory.getName())%></option>
<%
    }
%>
    </select>
</form>