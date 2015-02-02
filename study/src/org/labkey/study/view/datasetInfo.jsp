<%
/*
 * Copyright (c) 2007-2015 LabKey Corporation
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
<%@ page import="org.labkey.study.model.*" %>
<%@ page import="org.labkey.api.view.*" %>
<%@ page import="org.labkey.api.study.Dataset" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<DatasetDefinition> me = (JspView<DatasetDefinition>) HttpView.currentView();
    DatasetDefinition dataset = me.getModelBean();
%>
<table>
    <tr><td class=labkey-form-label>Name</td><th align=left><%= h(dataset.getName()) %></th></tr>
    <tr><td class=labkey-form-label>Label</td><td><%= h(dataset.getLabel()) %></td></tr>
    <tr><td class=labkey-form-label>Display String</td><td><%= h(dataset.getDisplayString()) %></td></tr>
    <tr><td class=labkey-form-label>Category</td><td><%= h(dataset.getViewCategory() != null ? dataset.getViewCategory().getLabel() : null) %></td></tr>
    <tr><td class=labkey-form-label>Visit Date Column</td><td><%= h(dataset.getVisitDateColumnName()) %></td></tr>
</table>