<%
/*
 * Copyright (c) 2005-2010 LabKey Corporation
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
<%@ page import="org.labkey.experiment.controllers.exp.ExperimentController" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.GUID" %>

<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
JspView<ExperimentController.ExportBean> me = (JspView<ExperimentController.ExportBean>) HttpView.currentView();
ExperimentController.ExportBean bean = me.getModelBean();
String guid = GUID.makeGUID();
%>

<script type="text/javascript">
    function setFileDownloadEnabled(enabled, guid)
    {
        var elements = Ext.DomQuery.select("input[class=file-download-role-checkbox-" + guid + "]");
        for (var i = 0; i < elements.length; i++)
        {
            elements[i].disabled = !enabled;
        }
    }

    function validateFileExportOptions(guid, form, submitURL)
    {
        if (document.getElementById(guid).checked)
        {
            var elements = Ext.DomQuery.select("input[class=file-download-role-checkbox-" + guid + "]");
            var roleCount = 0;
            for (var i = 0; i < elements.length; i++)
            {
                if (elements[i].checked)
                {
                    roleCount++;
                }
            }
            if (roleCount == 0)
            {
                alert("Please select at least one file usage type.");
                return false;
            }
        }
        return verifySelected(form, submitURL, 'POST', 'runs');
    }
</script>

<table cellspacing="4" class="labkey-export-tab-contents">
    <tr>
        <td valign="middle" rowspan="2">Export as a ZIP file:</td>
        <td valign="middle"><input type="radio" name="fileExportType" value="all" checked="true" onclick="setFileDownloadEnabled(document.getElementById('<%= PageFlowUtil.filter(guid) %>').checked, '<%= PageFlowUtil.filter(guid) %>');" /></td>
        <td valign="middle">Include all files</td>
    </tr>
    <% if (!bean.getRoles().isEmpty()) { %>
        <tr>
            <td valign="middle"><input type="radio" id="<%= PageFlowUtil.filter(guid) %>"name="fileExportType" value="role" onclick="setFileDownloadEnabled(document.getElementById('<%= PageFlowUtil.filter(guid) %>').checked, '<%= PageFlowUtil.filter(guid) %>');" /></td>
            <td valign="middle">Include only selected files based on usage in run:</td>
        </tr>
        <tr>
            <td colspan="2" />
            <td>
                <% for (String role : bean.getRoles()) { %>
                    <span style="white-space:nowrap"><input type="checkbox" class="file-download-role-checkbox-<%= guid %>" disabled="true" name="roles" id="role<%= PageFlowUtil.filter(role) %>" value="<%= PageFlowUtil.filter(role) %>" /><%= PageFlowUtil.filter(role) %>&nbsp;</span>
                <% } %>
            </td>
        </tr>
    <% } %>
    <tr>
        <td>ZIP file name:</td>
        <td colspan="2"><input type="text" size="45" name="zipFileName" value="<%= h(bean.getFileName()) %>" /></td>
    </tr>

    <tr>
        <td/>
        <td colspan="2"><%= PageFlowUtil.generateSubmitButton("Export", "return validateFileExportOptions('" + guid + "', this.form, '" + bean.getPostURL() + "');") %></td>
    </tr>
</table>
