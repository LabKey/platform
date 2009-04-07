<%
/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.exp.PropertyDescriptor"%>
<%@ page import="org.labkey.api.exp.PropertyType"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%
String typeName = (String)HttpView.currentView().getViewContext().get("typeName");
PropertyDescriptor[] properties = (PropertyDescriptor[])HttpView.currentView().getViewContext().get("properties");
%><h3><%=PageFlowUtil.filter(typeName)%></h3>
<table>
<tr><th>Name</th><th>PropertyURI</th><th>Type</th></tr><%
for (PropertyDescriptor pd : properties)
    {
    PropertyType t = pd.getPropertyType();
    %><tr><td><%=PageFlowUtil.filter(pd.getName())%></td><td><%=PageFlowUtil.filter(pd.getPropertyURI())%></td><td><%=PageFlowUtil.filter(t.getXmlName())%></td></tr><%
    }
%></table>
<!-- <p>
[ <a href="types.view">View Types</a> ]<br>
[ <a href="importData.view?typeURI=<%=PageFlowUtil.encode(typeName)%>">Import Data</a> ]<br>&nbsp;<br>
<%=PageFlowUtil.generateButton("Delete data", "deleteData.view?typeURI=" + PageFlowUtil.encode(typeName) + "&deleteType=0")%>
&nbsp;<%=PageFlowUtil.generateButton("Delete type", "deleteData.view?typeURI=" + PageFlowUtil.encode(typeName) + "&deleteType=1")%>
</p> --!>
