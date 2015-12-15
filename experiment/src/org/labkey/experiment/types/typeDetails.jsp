<%
/*
 * Copyright (c) 2006-2013 LabKey Corporation
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
<%@ page import="org.labkey.experiment.types.TypesController.TypeDetailsAction"%>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    TypeDetailsAction bean = (TypeDetailsAction)getModelBean();
    String typeName = bean.typeName;
    List<PropertyDescriptor> properties = bean.properties;
%><h3><%=h(typeName)%></h3>
<table>
<tr><th>Name</th><th>PropertyURI</th><th>Type</th></tr><%
    for (PropertyDescriptor pd : properties)
    {
        PropertyType t = pd.getPropertyType();
        %><tr><td><%=h(pd.getName())%></td><td><%=h(pd.getPropertyURI())%></td><td><%=h(t.getXmlName())%></td></tr><%
    }
%></table>
