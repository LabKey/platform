<%
/*
 * Copyright (c) 2009-2015 LabKey Corporation
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
<%@ page import="org.json.JSONObject" %>
<%@ page import="org.labkey.api.data.ColumnHeaderType" %>
<%@ page import="org.labkey.api.data.DataRegion" %>
<%@ page import="org.labkey.api.query.QueryView" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.util.GUID" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="java.util.LinkedHashMap" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    QueryView.ExcelExportOptionsBean model = (QueryView.ExcelExportOptionsBean) HttpView.currentModel();

    String guid = GUID.makeGUID();
    String xlsxGUID = "xlsx_" + guid;
    String xlsGUID = "xls_" + guid;
    String iqyGUID = "iqy_" + guid;
    String headerGUID = "header_" + guid;
    String exportSelectedId = "exportSelected_" + guid;
    String exportButtonId = "export_" + guid;

    Map<ColumnHeaderType, String> headerMap = new LinkedHashMap<>();
    headerMap.put(ColumnHeaderType.None, ColumnHeaderType.None.getOptionText());
    headerMap.put(ColumnHeaderType.Caption, ColumnHeaderType.Caption.getOptionText());
    // Don't include Name by default since Caption of DisplayFieldKey are better defaults
    if (model.getHeaderType() == ColumnHeaderType.Name)
        headerMap.put(ColumnHeaderType.Name, ColumnHeaderType.Name.getOptionText());
    headerMap.put(ColumnHeaderType.DisplayFieldKey, ColumnHeaderType.DisplayFieldKey.getOptionText());
    headerMap.put(ColumnHeaderType.FieldKey, ColumnHeaderType.FieldKey.getOptionText());

    boolean hasSelected = model.hasSelected(getViewContext());
    String exportRegionName = model.getExportRegionName();
    String DRNamespace = DataRegion.useExperimentalDataRegion() ? "LABKEY.DataRegion2" : "LABKEY.DataRegion";
%>
<table class="labkey-export-tab-contents">
    <tr>
        <td valign="center"><input type="radio" id="<%=h(xlsxGUID)%>" name="excelExportType" checked/></td>
        <td valign="center"><label for="<%=h(xlsxGUID)%>">Excel Workbook (.xlsx)</label> <span style="font-size: smaller">Maximum 1,048,576 rows and 16,384 columns.</span></td>
    </tr>
    <tr>
        <td valign="center"><input type="radio" id="<%=h(xlsGUID)%>" name="excelExportType" /></td>
        <td valign="center"><label for="<%=h(xlsGUID)%>">Excel Old Binary Workbook (.xls)</label> <span style="font-size: smaller">Maximum 65,536 rows and 256 columns.</span></td>
    </tr>
    <% if (model.getIqyURL() != null) { %>
        <tr>
            <td valign="center"><input type="radio" id="<%=h(iqyGUID)%>" name="excelExportType"/></td>
            <td valign="center"><label for="<%=h(iqyGUID)%>">Refreshable Web Query (.iqy)</label></td>
        </tr>
    <% } %>
    <% if (AppProps.getInstance().isExperimentalFeatureEnabled(QueryView.EXPERIMENTAL_EXPORT_COLUMN_HEADER_TYPE)) { %>
    <tr>
        <td colspan="2"><label>Column headers:</label>
            <select id="<%=text(headerGUID)%>" name="headerType">
                <labkey:options value="<%=model.getHeaderType()%>" map="<%=headerMap%>" />
            </select>
        </td>
    </tr>
    <% } else { %>
    <tr style="display:none"><td>
    <input id="<%=text(headerGUID)%>" name="headerType" value="<%=h(model.getHeaderType().toString())%>">
    </td></tr>
    <% } %>
    <tr><td colspan="2"></td></tr>
    <tr>
        <td valign="center"><input type="checkbox" id="<%=h(exportSelectedId)%>" value="exportSelected" <%=checked(hasSelected)%> <%=disabled(!hasSelected)%>/></td>
        <td valign="center"><label class="<%=text(hasSelected ? "" : "labkey-disabled")%>" id="<%=h(exportSelectedId + "_label")%>" for="<%=h(exportSelectedId)%>">Export selected rows</label></td>
    </tr>
    <tr>
        <td colspan="2"><%= button("Export to Excel").id(exportButtonId) %></td>
    </tr>
</table>

<script type="text/javascript">
    (function($) {

        <%=text(DRNamespace)%>.registerPane(<%=PageFlowUtil.jsString(model.getDataRegionName())%>, function(dr) {
            var xlsExportEl = $("#<%=h(xlsGUID)%>");
            var xlsxExportEl = $("#<%=h(xlsxGUID)%>");
            var iqyExportEl = $("#<%=h(iqyGUID)%>");
            var headerEl = $("#<%=h(headerGUID)%>");

            var exportSelectedEl = $("#<%=h(exportSelectedId)%>");
            var exportSelectedLabelEl = $("#<%=h(exportSelectedId + "_label")%>");

            var doExcelExport = function() {
                var exportUrl, exportParams, exportRegionName = <%=PageFlowUtil.jsString(exportRegionName)%>;

                if (xlsxExportEl.is(':checked')) {
                    exportUrl = <%=PageFlowUtil.jsString(model.getXlsxURL().getPath(false))%>;
                    exportParams = <%=text( new JSONObject(model.getXlsxURL().getParameterMap()).toString(2) )%>;
                }
                else if (xlsExportEl.is(':checked')) {
                    exportUrl = <%=PageFlowUtil.jsString(model.getXlsURL().getPath(false))%>;
                    exportParams = <%=text( new JSONObject(model.getXlsURL().getParameterMap()).toString(2) )%>;
                <% if (model.getIqyURL() != null) { %>
                }
                else if (iqyExportEl.is(':checked')) {
                    <%-- Excel Web Query doesn't work with POSTs, so always do it as a GET.  It also is not supported for all tables. --%>
                    window.location = <%=PageFlowUtil.jsString(model.getIqyURL().toString())%>;
                    return false;
                <% } %>
                }

                if (!exportSelectedEl.is(':disabled') && exportSelectedEl.is(':checked')) {
                    // Replace 'showRows=ALL' parameter with 'showRows=SELECTED'
                    exportParams[exportRegionName + '.showRows'] = 'SELECTED';
                    exportParams[exportRegionName + '.selectionKey'] = dr.selectionKey;
                }

                if (headerEl && headerEl.val())
                    exportParams['headerType'] = headerEl.val();

                dr.addMessage({
                    html: '<div class="labkey-message"><strong>Excel export started.</strong></div>',
                    part: 'excelExport', hideButtonPanel: true, duration:5000
                });

                <%-- Sometimes the GET URL gets too long, so use a POST instead. We have to create a separate <form> since we might --%>
                <%-- already be inside a form for the DataRegion itself. --%>
                var newForm = document.createElement('form');
                document.body.appendChild(newForm);

                <%-- Add the CSRF form input --%>
                var csrfElement = document.createElement('input');
                csrfElement.setAttribute('name', 'X-LABKEY-CSRF');
                csrfElement.setAttribute('type', 'hidden');
                csrfElement.setAttribute('value', LABKEY.CSRF);
                newForm.appendChild(csrfElement);

                <%-- We need to build up all of the form elements ourselves because Ext.Ajax will concatentate multiple parameter values --%>
                <%-- into a single string when the 'isUpload: true' config option is used --%>
                function addInput(form, property, value){
                    var newElement = document.createElement('input');
                    newElement.setAttribute('name', property);
                    newElement.setAttribute('type', 'hidden');
                    newElement.setAttribute('value', value);
                    form.appendChild(newElement);
                }

                $.each(exportParams, function(prop, val) {
                    if ($.isArray(val)) {
                        $.each(val, function(i, v) { addInput(newForm, prop, v); });
                    }
                    else {
                        addInput(newForm, prop, val);
                    }
                });

                <%-- TODO: Either make LABKEY.Ajax handle a form or use jQuery --%>
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
            };

            var enableExportSelected = function() {
                if (exportSelectedEl.is(':disabled')) {
                    exportSelectedEl.prop('checked', true);
                    exportSelectedEl.prop('disabled', false);
                    exportSelectedLabelEl.removeClass();
                }
            };

            var disableExportSelected = function() {
                exportSelectedEl.prop('checked', false);
                exportSelectedEl.prop('disabled', true);
                exportSelectedLabelEl.addClass('labkey-disabled');
            };

            var exportButtonEl = $("#<%=h(exportButtonId)%>");
            exportButtonEl.click(doExcelExport);

            dr.on('selectchange', function(dr, selectedCount) {
                selectedCount > 0 ? enableExportSelected() : disableExportSelected();
            });
        });

    })(jQuery);
</script>

