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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.experiment.LSIDRelativizer" %>
<%@ page import="org.labkey.experiment.XarExportType" %>
<%@ page import="org.labkey.experiment.controllers.exp.ExperimentController" %>
<%@ page import="org.labkey.api.data.DataRegionSelection" %>
<%@ page import="org.labkey.api.data.DataRegion" %>

<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
JspView<ExperimentController.ExportBean> me = (JspView<ExperimentController.ExportBean>) HttpView.currentView();
ExperimentController.ExportBean bean = me.getModelBean();
%>

<p class="labkey-error"><b><%= h(bean.getError()) %></b></p>

<form action="<%= bean.getPostURL().toString() %>" method="post">

<table>
    <tr>
        <td>LSID output type:</td>
        <td>
            <select name="lsidOutputType">
                <% for(LSIDRelativizer lsidOutputType : LSIDRelativizer.values()) { %>
                    <option value="<%= lsidOutputType %>" <% if (lsidOutputType == bean.getSelectedRelativizer()) { %>selected<% } %>><%= lsidOutputType.getDescription() %></option>
                <% } %>
            </select>
        </td>
    </tr>
    <tr>
        <td>Export type:</td>
        <td>
            <select name="exportType">
                <% for(XarExportType exportType : XarExportType.values()) { %>
                    <option value="<%= exportType %>" <% if (exportType == bean.getSelectedExportType()) { %>selected<% } %>><%= exportType.getDescription() %></option>
                <% } %>
            </select>
        </td>
    </tr>
    <tr>
        <td>Filename:</td>
        <td><input type="text" size="45" name="fileName" value="<%= h(bean.getFileName()) %>" /></td>
    </tr>
</table>

<input type="hidden" name="<%= DataRegionSelection.DATA_REGION_SELECTION_KEY %>" value="<%= bean.getDataRegionSelectionKey() %>" />
<% for (String selectIds : request.getParameterValues(DataRegion.SELECT_CHECKBOX_NAME) == null ? new String[0] : request.getParameterValues(DataRegion.SELECT_CHECKBOX_NAME))
{ %>
    <input type="hidden" name="<%= DataRegion.SELECT_CHECKBOX_NAME %>" value="<%= h(selectIds)%>" />
<% }
if (bean.getExpRowId() != null)
{ %>
    <input type="hidden" name="expRowId" value="<%= bean.getExpRowId() %>" />
<% } %>
<input type="hidden" name="protocolId" value="<%= bean.getProtocolId() == null ? "" : bean.getProtocolId() %>" />
<%= buttonImg("Export") %>
</form>
