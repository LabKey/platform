<%
/*
 * Copyright (c) 2009-2013 LabKey Corporation
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
    String xlsxGUID = GUID.makeGUID();
    String xlsGUID = GUID.makeGUID();
    String onClickScript = "window.location = document.getElementById('" + xlsGUID + "').checked ? " + PageFlowUtil.jsString(model.getXlsURL().getLocalURIString()) + " : (document.getElementById('" + xlsxGUID + "').checked ? " + PageFlowUtil.jsString(model.getXlsxURL().getLocalURIString()) + " : " + PageFlowUtil.jsString(model.getIqyURL() == null ? "" : model.getIqyURL().getLocalURIString()) + "); return false;";
%>
<table class="labkey-export-tab-contents">
    <tr>
        <td valign="center"><input type="radio" id="<%=h(xlsxGUID)%>" name="excelExportType" value="<%=h(model.getXlsxURL()) %>" checked/></td>
        <td valign="center"><label for="<%=h(xlsxGUID)%>">Excel 2007 File (.xlsx)</label> <span style="font-size: smaller">Maximum 1,048,576 rows and 16,384 columns.</span></td>
    </tr>
    <tr>
        <td valign="center"><input type="radio" id="<%=h(xlsGUID)%>" name="excelExportType" value="<%=h(model.getXlsURL()) %>" /></td>
        <td valign="center"><label for="<%=h(xlsGUID)%>">Excel 97 File (.xls)</label> <span style="font-size: smaller">Maximum 65,536 rows and 256 columns.</span></td>
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

