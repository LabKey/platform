<%
/*
 * Copyright (c) 2009-2014 LabKey Corporation
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
<%@ page import="org.json.JSONObject" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    QueryView.ExcelExportOptionsBean model = (QueryView.ExcelExportOptionsBean) HttpView.currentModel();
    String guid = GUID.makeGUID();
    String xlsxGUID = "xlsx_" + guid;
    String xlsGUID = "xls_" + guid;
    String iqyGUID = "iqy_" + guid;
    String exportSelectedId = "exportSelected_" + guid;
    String exportButtonId = "export_" + guid;

    boolean hasSelected = model.hasSelected(getViewContext());
    String exportRegionName = model.getExportRegionName();
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
            <td valign="center"><input type="radio" id="<%=h(iqyGUID)%>" name="excelExportType"/></td>
            <td valign="center"><label for="<%=h(iqyGUID)%>">Refreshable Web Query (.iqy)</label></td>
        </tr>
    <% } %>
    <tr><td colspan="2"></td></tr>
    <tr>
        <td valign="center"><input type="checkbox" id="<%=h(exportSelectedId)%>" value="exportSelected" <%=checked(hasSelected)%> <%=disabled(!hasSelected)%>/></td>
        <td valign="center"><label class="<%=text(hasSelected ? "" : "labkey-disabled")%>" id="<%=h(exportSelectedId + "_label")%>" for="<%=h(exportSelectedId)%>">Export selected rows</label></td>
    </tr>
    <tr>
        <td colspan="2">
            <%= button("Export to Excel").id(exportButtonId) %>
        </td>
    </tr>
</table>

<script type="text/javascript">
Ext.onReady(function () {
    var xlsExportEl = document.getElementById("<%=h(xlsGUID)%>");
    var xlsxExportEl = document.getElementById("<%=h(xlsxGUID)%>");
    var iqyExportEl = document.getElementById("<%=h(iqyGUID)%>");

    var exportSelectedEl = document.getElementById("<%=h(exportSelectedId)%>");
    var exportSelectedLabelEl = document.getElementById("<%=h(exportSelectedId + "_label")%>");

    <%-- CONSIDER: Add a universal export function to LABKEY.DataRegion clientapi --%>
    function doExcelExport()
    {
        var dr = LABKEY.DataRegions[<%=PageFlowUtil.jsString(model.getDataRegionName())%>];
        var exportRegionName = <%=PageFlowUtil.jsString(exportRegionName)%>;
        var exportUrl;
        var exportParams;

        if (xlsxExportEl.checked) {
            exportUrl = <%=PageFlowUtil.jsString(model.getXlsxURL().getPath(false))%>;
            exportParams = <%=text( new JSONObject(model.getXlsxURL().getParameterMap()).toString(2) )%>;
        }
        else if (xlsExportEl.checked) {
            exportUrl = <%=PageFlowUtil.jsString(model.getXlsURL().getPath(false))%>;
            exportParams = <%=text( new JSONObject(model.getXlsURL().getParameterMap()).toString(2) )%>;
        <% if (model.getIqyURL() != null) { %>
        } else if (iqyExportEl.checked) {
            <%-- Excel Web Query doesn't work with POSTs, so always do it as a GET.  It also is not supported for all tables. --%>
            window.location = <%=PageFlowUtil.jsString(model.getIqyURL().toString())%>;
            return false;
        <% } %>
        }

        if (!exportSelectedEl.disabled && exportSelectedEl.checked) {
            // Replace 'showRows=ALL' parameter with 'showRows=SELECTED'
            exportParams[exportRegionName + '.showRows'] = 'SELECTED';
            exportParams[exportRegionName + '.selectionKey'] = dr.selectionKey;
        }

        dr.addMessage({
            html: '<div class=\"labkey-message\"><strong>Excel export started.</strong></div>',
            part: 'excelExport', hideButtonPanel: true, duration:5000
        });

        <%-- Sometimes the GET URL gets too long, so use a POST instead. We have to create a separate <form> since we might --%>
        <%-- already be inside a form for the DataRegion itself. --%>
        var newForm = document.createElement('form');
        document.body.appendChild(newForm);

        // Add the CSRF form input
        var csrfElement = document.createElement('input');
        csrfElement.setAttribute('name', 'X-LABKEY-CSRF');
        csrfElement.setAttribute('type', 'hidden');
        csrfElement.setAttribute('value', LABKEY.CSRF);
        newForm.appendChild(csrfElement);

        // We need to build up all of the form elements ourselves because Ext.Ajax will concatentate multiple parameter values
        // into a single string when the 'isUpload: true' config option is used
        function addInput(form, property, value){
            var newElement = document.createElement('input');
            newElement.setAttribute('name', property);
            newElement.setAttribute('type', 'hidden');
            newElement.setAttribute('value', value);
            form.appendChild(newElement);
        }

        for (var property in exportParams) {
            if (exportParams.hasOwnProperty(property)) {
                if (Ext.isArray(exportParams[property])) {
                    for (var i = 0; i < exportParams[property].length; i++) {
                        addInput(newForm, property, exportParams[property][i]);
                    }
                }
                else
                {
                    addInput(newForm, property, exportParams[property]);
                }
            }
        }

        Ext.Ajax.request({
            url: exportUrl,
            method: 'POST',
            form: newForm,
            isUpload: true,
            callback: function (options, success, response) {
                dr.removeAllMessages();
                if (!success) {
                    dr.showErrorMessage("Error exporting to Excel.");
                }
                if (response.responseXML && response.responseXML.title) {
                    var title = response.responseXML.title;
                    var index = title.indexOf("Error Page -- ");
                    if (index != -1) {
                        var message = title.substring(index + "Error Page -- ".length);
                        dr.showErrorMessage("Error: " + message);
                    }
                }
                document.body.removeChild(newForm);
            }
        });

        return false;
    }

    function enableExportSelected()
    {
        if (exportSelectedEl.disabled) {
            exportSelectedEl.checked = true;
            exportSelectedEl.disabled = false;
            exportSelectedLabelEl.className = "";
        }
    }

    // TODO: disable exportSelectedEl when iqy is chosen
    function disableExportSelected()
    {
        exportSelectedEl.checked = false;
        exportSelectedEl.disabled = true;
        exportSelectedLabelEl.className = "labkey-disabled";
    }

    var exportButtonEl = document.getElementById("<%=h(exportButtonId)%>");
    if (exportButtonEl.addEventListener)
        exportButtonEl.addEventListener('click', doExcelExport, false);
    else if (exportButtonEl.attachEvent)
        exportButtonEl.attachEvent('onclick', doExcelExport);

    Ext.ComponentMgr.onAvailable(<%=PageFlowUtil.jsString(model.getDataRegionName())%>, function (dr) {
        dr.on('selectchange', function (dr, selectedCount) {
            if (selectedCount > 0) {
                enableExportSelected();
            } else {
                disableExportSelected();
            }
        });
    });
});
</script>

