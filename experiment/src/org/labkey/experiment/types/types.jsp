<%
/*
 * Copyright (c) 2006-2009 LabKey Corporation
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
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.api.exp.DomainDescriptor" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%
Map<String,DomainDescriptor> g = (Map<String,DomainDescriptor>)HttpView.currentView().getViewContext().get("globals");
Map<String,DomainDescriptor> l = (Map<String,DomainDescriptor>)HttpView.currentView().getViewContext().get("locals");
%><h3>global types</h3><%
for (DomainDescriptor type : g.values())
    {
    %><a href="typeDetails.view?type=<%=PageFlowUtil.filter(PageFlowUtil.encode(type.getDomainURI()))%>"><%=PageFlowUtil.filter(type.getName())%></a><br /><%
    }
%><h3>local types</h3><%
for (DomainDescriptor type : l.values())
    {
    %><a href="typeDetails.view?type=<%=PageFlowUtil.filter(PageFlowUtil.encode(type.getDomainURI()))%>"><%=PageFlowUtil.filter(type.getName())%></a><br /><%
    }
%>
<!--<p>
[ <a href="importTypes.view">Import Types</a> ]
</p>-->