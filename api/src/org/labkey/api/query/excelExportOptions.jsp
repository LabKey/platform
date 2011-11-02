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
<%@ page extends="org.labkey.api.jsp.JspBase" %>
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
        <td valign="center"><input type="radio" id="<%=guid%>" name="excelExportType" value="<%=h(model.getXlsURL()) %>" checked="true" /></td>
        <td valign="center"><label for="<%=guid%>">Standard Excel File (.xls)</label><span style="padding-left: 4em; font-size: smaller">Note: The Excel export format is limited to 65,536 rows and 256 columns of data.</span></td>
    </tr>
    <% if (model.getIqyURL() != null) { %>
        <tr>
            <td valign="center"><input type="radio" id="excelWebQuery" name="excelExportType" value="<%=h(model.getIqyURL()) %>" /></td>
            <td valign="center"><label for="excelWebQuery">Refreshable Web Query (.iqy)</label></td>
        </tr>
    <% } %>
    <tr>
        <td colspan="2">
            <%=generateButton("Export to Excel", model.getXlsURL(), onClickScript) %>
        </td>
    </tr>
</table>

