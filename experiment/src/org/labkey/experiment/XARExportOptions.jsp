<%
/*
 * Copyright (c) 2005-2017 LabKey Corporation
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
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
JspView<ExperimentController.ExportBean> me = (JspView<ExperimentController.ExportBean>) HttpView.currentView();
ExperimentController.ExportBean bean = me.getModelBean();
%>

<p class="labkey-error"><b><%= h(bean.getError()) %></b></p>

<table cellspacing="4" class="lk-fields-table">
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
        <td><input type="text" size="45" name="xarFileName" value="<%= h(bean.getFileName()) %>" /></td>
    </tr>
    <tr>
        <td/>
        <td><%= button("Export").submit(true).onClick("return verifySelected(this.form, '" + bean.getPostURL() + "', 'POST', 'runs');") %></td>
    </tr>
</table>

<% if (bean.getExpRowId() != null)
{ %>
    <input type="hidden" name="expRowId" value="<%= bean.getExpRowId() %>" />
<% } %>
