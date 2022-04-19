<%
/*
 * Copyright (c) 2009-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.experiment.controllers.exp.ExperimentController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
JspView<ExperimentController.ExportBean> me = (JspView<ExperimentController.ExportBean>) HttpView.currentView();
ExperimentController.ExportBean bean = me.getModelBean();
HtmlString guid = HtmlString.of(GUID.makeGUID());
%>

<script type="text/javascript" nonce="<%=getScriptNonce()%>">
LABKEY.FileExportOptions = {};
(function($) {
    LABKEY.FileExportOptions.setFileDownloadEnabled = function(enabled, guid)
    {
        var elements = $('input.file-download-role-checkbox-' + guid);
        elements.each(function(i, el) {
            el.disabled = !enabled;
        });
    };

    LABKEY.FileExportOptions.validateFileExportOptions = function (guid, form, submitURL)
    {
        if (document.getElementById(guid).checked)
        {
            var roleCount = 0;
            var elements = $('input.file-download-role-checkbox-' + guid);
            elements.each(function(i, el) {
                if (el.checked) {
                    roleCount++;
                }
            });
            if (roleCount === 0) {
                LABKEY.Utils.alert("Error", "Please select at least one file usage type.");
                return false;
            }
        }
        return verifySelected(form, submitURL, 'POST', 'runs');
    };
})(jQuery);
</script>

<table cellspacing="4" class="lk-fields-table" style="overflow-y: visible;">
    <tr>
        <td valign="middle"><input type="radio" name="fileExportType" value="all" checked onclick="LABKEY.FileExportOptions.setFileDownloadEnabled(document.getElementById('<%=guid%>').checked, '<%=guid%>');" /></td>
        <td valign="middle">Include all files</td>
    </tr>
    <% if (!bean.getRoles().isEmpty()) { %>
        <tr>
            <td valign="middle"><input type="radio" id="<%=guid%>"name="fileExportType" value="role" onclick="LABKEY.FileExportOptions.setFileDownloadEnabled(document.getElementById('<%=guid%>').checked, '<%=guid%>');" /></td>
            <td valign="middle">Include only selected files based on usage in run:</td>
        </tr>
        <tr>
            <td></td>
            <td>
                <table>
                    <tr>
                    <%
                        int index = 0;
                        for (String role : bean.getRoles())
                        {
                            if (index != 0 && index % 3 == 0)
                                %></tr><tr><%
                    %>
                            <td>
                                <input type="checkbox" class="file-download-role-checkbox-<%=guid%>" disabled="true" name="roles" id="role<%= h(role) %>" value="<%= h(role) %>" />
                                <span style="padding-left: .4em; padding-right: 1.5em;"><%= h(role) %></span>
                            </td>
                    <%
                            index++;
                        }
                    %>
                    </tr>
                </table>
            </td>
        </tr>
    <% } %>
    <tr>
        <td colspan="2">File name: <input type="text" size="45" name="zipFileName" value="<%= h(bean.getFileName()) %>" /></td>
    </tr>

    <tr>
        <td colspan="2"><%= button("Export").submit(true).onClick("return LABKEY.FileExportOptions.validateFileExportOptions('" + guid + "', this.form, '" + bean.getPostURL() + "');") %></td>
    </tr>
</table>
