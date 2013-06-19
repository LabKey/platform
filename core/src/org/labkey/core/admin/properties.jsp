<%
/*
 * Copyright (c) 2008-2013 LabKey Corporation
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
<%@ page import="org.labkey.api.collections.CaseInsensitiveTreeMap" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<Map<String, String>> me = (JspView<Map<String, String>>) HttpView.currentView();
    Map<String, String> properties = me.getModelBean();
    Map<String, String> sortedProperties = new CaseInsensitiveTreeMap<>(properties);
%>
<table>
    <% for (Map.Entry<String, String> entry : sortedProperties.entrySet())
    { %>
        <tr>
            <td class='labkey-form-label' valign="top"><%=h(entry.getKey())%></td>
            <td><%=h(entry.getValue())%></td>
        </tr>
    <% }
    %>
</table>