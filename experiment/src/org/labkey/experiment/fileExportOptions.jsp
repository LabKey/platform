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
<%@ page import="org.labkey.api.util.GUID" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.experiment.controllers.exp.ExperimentController" %>
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

<table cellspacing="4" class="lk-fields-table" style="overflow-y: visible;">
    <tr>
        <td valign="middle"><input type="radio" name="fileExportType" value="all" checked onclick="setFileDownloadEnabled(document.getElementById('<%=h(guid) %>').checked, '<%= h(guid) %>');" /></td>
        <td valign="middle">Include all files</td>
    </tr>
    <% if (!bean.getRoles().isEmpty()) { %>
        <tr>
            <td valign="middle"><input type="radio" id="<%=h(guid) %>"name="fileExportType" value="role" onclick="setFileDownloadEnabled(document.getElementById('<%= h(guid) %>').checked, '<%= h(guid) %>');" /></td>
            <td valign="middle">Include only selected files based on usage in run:</td>
        </tr>
        <tr>
            <td></td>
            <td>
                <% for (String role : bean.getRoles()) { %>
                    <div style="white-space:nowrap; width: 15em; float: left;"><input type="checkbox" class="file-download-role-checkbox-<%= guid %>" disabled="true" name="roles" id="role<%= h(role) %>" value="<%= h(role) %>" /><span style="padding-left: .4em; padding-right: 1.5em;"><%= h(role) %></span></div>
                <% } %>
            </td>
        </tr>
    <% } %>
    <tr>
        <td colspan="2">File name: <input type="text" size="45" name="zipFileName" value="<%= h(bean.getFileName()) %>" /></td>
    </tr>

    <tr>
        <td colspan="2"><%= button("Export As ZIP").submit(true).onClick("return validateFileExportOptions('" + guid + "', this.form, '" + bean.getPostURL() + "');") %></td>
    </tr>
</table>
