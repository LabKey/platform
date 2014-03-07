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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.util.GUID" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    QueryView.ExcelExportOptionsBean model = (QueryView.ExcelExportOptionsBean) HttpView.currentModel();
    String xlsxGUID = GUID.makeGUID();
    String xlsGUID = GUID.makeGUID();
    // Sometimes the GET URL gets too long, so use a POST instead. We have to create a separate <form> since we might
    // already be inside a form for the DataRegion itself.
    String onClickScript = "var formText; " +
            "if (document.getElementById('" + xlsGUID + "').checked) { formText = " + PageFlowUtil.jsString(model.convertToForm(model.getXlsURL())) + ";} " +
            "else if (document.getElementById('" + xlsxGUID + "').checked) { formText = " + PageFlowUtil.jsString(model.convertToForm(model.getXlsxURL())) + ";} " +
            // Excel Web Query doesn't work with POSTs, so always do it as a GET.  It also is not supported for all tables
            (model.getIqyURL() == null ? "" :
                "else { window.location = " + PageFlowUtil.jsString(model.getIqyURL().toString()) + "; return false; } "
            ) +
            "LABKEY.DataRegions['" + model.getDataRegionName() + "'].addMessage({html:'<div class=\"labkey-message\"><strong>" +
            "Excel export started.</strong></div>', part: 'excelExport', hideButtonPanel: true, duration:5000}); " +
            "var newForm = LABKEY.ExtAdapter.DomHelper.append(document.getElementsByTagName('body')[0], formText); " +
            "newForm.submit();" +
            "return false;";
%>
<table class="labkey-export-tab-contents">
    <tr>
        <td valign="center"><input type="radio" id="<%=h(xlsxGUID)%>" name="excelExportType" checked/></td>
        <td valign="center"><label for="<%=h(xlsxGUID)%>">Excel 2007 File (.xlsx)</label> <span style="font-size: smaller">Maximum 1,048,576 rows and 16,384 columns.</span></td>
    </tr>
    <tr>
        <td valign="center"><input type="radio" id="<%=h(xlsGUID)%>" name="excelExportType" /></td>
        <td valign="center"><label for="<%=h(xlsGUID)%>">Excel 97 File (.xls)</label> <span style="font-size: smaller">Maximum 65,536 rows and 256 columns.</span></td>
    </tr>
    <% if (model.getIqyURL() != null) { %>
        <tr>
            <td valign="center"><input type="radio" id="excelWebQuery" name="excelExportType" /></td>
            <td valign="center"><label for="excelWebQuery">Refreshable Web Query (.iqy)</label></td>
        </tr>
    <% } %>
    <tr>
        <td colspan="2">
            <%= button("Export to Excel").href(model.getXlsURL()).onClick(onClickScript) %>
        </td>
    </tr>
</table>

