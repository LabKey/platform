<%
/*
 * Copyright (c) 2009-2010 LabKey Corporation
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
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.query.QueryView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.util.GUID" %>
<%
JspView<QueryView.ExcelExportOptionsBean> me = (JspView<QueryView.ExcelExportOptionsBean>) HttpView.currentView();
QueryView.ExcelExportOptionsBean model = me.getModelBean();
String guid = GUID.makeGUID();
String onClickScript = null;
if (model.getIqyURL() != null)
{
    onClickScript = "window.location = document.getElementById('" + guid + "').checked ? " + PageFlowUtil.jsString(model.getXlsURL().getLocalURIString()) + " : " + PageFlowUtil.jsString(model.getIqyURL().getLocalURIString()) + "; return false;";
}
%>


<table class="labkey-export-tab-contents">
    <tr>
        <td class="labkey-export-tab-options">
            <table class="labkey-export-tab-layout">
                <tr>
                    <td valign="center">Export to Excel as:</td>
                    <td valign="center"><input type="radio" id="<%= guid %>" name="excelExportType" value="<%= PageFlowUtil.filter(model.getXlsURL()) %>" checked="true" /></td>
                    <td valign="center"><label for="<%=guid%>">Standard File (.xls)</label></td>
                </tr>
                <% if (model.getIqyURL() != null) { %>
                <tr>
                    <td/>
                    <td valign="center"><input type="radio" id="excelWebQuery" name="excelExportType" value="<%= PageFlowUtil.filter(model.getIqyURL()) %>" /></td>
                    <td valign="center"><label for="excelWebQuery">Refreshable Web Query (.iqy)</label></td>
                </tr>
                <% } %>
            </table>            
        </td>
        <td class="labkey-export-tab-buttons">
            <%=PageFlowUtil.generateButton("Export to Excel", model.getXlsURL(), onClickScript) %>
        </td>
    </tr>
</table>

