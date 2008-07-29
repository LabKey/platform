<%
/*
 * Copyright (c) 2005-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.exp.AbstractParameter"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="java.util.Map" %>

<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<Map<String, ? extends AbstractParameter>> me = (JspView<Map<String, ? extends AbstractParameter>>) HttpView.currentView();
    Map<String, ? extends AbstractParameter> params = me.getModelBean();
%>

<% if (params.isEmpty())
{ %>
<em>No data to show.</em>
<% } %>
<table>
<% for (String name : params.keySet())
{
    AbstractParameter param = params.get(name); %>
    <tr>
        <td class="labkey-form-label"><%= h(param.getName()) %></td>
        <td>
            <%= h(param.getValue()) %>
        </td>
    </tr>
<% } %>
</table>