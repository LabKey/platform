<%
/*
 * Copyright (c) 2009-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.query.QueryView" %>
<%@ page import="org.labkey.api.util.GUID" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="java.util.LinkedHashMap" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<% /* DO NOT ADD DEPENDENCIES HERE, WOULD END UP LOADING WITH EACH DATA REGION */ %>
<%
    QueryView.ExcelExportOptionsBean model = (QueryView.ExcelExportOptionsBean) HttpView.currentModel();

    String guid = GUID.makeGUID();
    String xlsxGUID = "xlsx_" + guid;
    String xlsGUID = "xls_" + guid;
    String iqyGUID = "iqy_" + guid;
    String headerGUID = "header_" + guid;
    String headerType = "xls_header_type";
    String exportSelectedId = "exportSelected_";
    String exportButtonId = "export_" + guid;
    String signButtonId = "sign_" + guid;
    String exportButtonText = "Export";
    String signButtonText = "Sign Data";
    String checkboxGroupName = "excelExportType";

    Map<ColumnHeaderType, String> headerMap = new LinkedHashMap<>();
    StringBuilder sb = new StringBuilder();

    headerMap.put(ColumnHeaderType.None, ColumnHeaderType.None.getOptionText());
    sb.append("<span><span class='labkey-strong'>").append(ColumnHeaderType.None.getOptionText()).append("</span>:&nbsp;").append(ColumnHeaderType.None.getDescription()).append("</span><br>");
    headerMap.put(ColumnHeaderType.Caption, ColumnHeaderType.Caption.getOptionText());
    sb.append("<span><span class='labkey-strong'>").append(ColumnHeaderType.Caption.getOptionText()).append("</span>:&nbsp;").append(ColumnHeaderType.Caption.getDescription()).append("</span><br>");

    // Don't include Name or DisplayFieldKey by default since Caption of DisplayFieldKey are better defaults
    if (model.getHeaderType() == ColumnHeaderType.Name)
    {
        headerMap.put(ColumnHeaderType.Name, ColumnHeaderType.Name.getOptionText());
        sb.append("<span><span class='labkey-strong'>").append(ColumnHeaderType.Name.getOptionText()).append("</span>:&nbsp;").append(ColumnHeaderType.Name.getDescription()).append("</span><br>");
    }
    headerMap.put(ColumnHeaderType.FieldKey, ColumnHeaderType.FieldKey.getOptionText());
    sb.append("<span><span class='labkey-strong'>").append(ColumnHeaderType.FieldKey.getOptionText()).append("</span>:&nbsp;").append(ColumnHeaderType.FieldKey.getDescription()).append("</span><br>");

    boolean hasSelected = model.hasSelected(getViewContext());
    String exportRegionName = model.getExportRegionName();
%>
<table class="lk-fields-table">
    <tr>
        <td valign="center"><input type="radio" id="<%=h(xlsxGUID)%>" name="<%=h(checkboxGroupName)%>" checked="checked" /></td>
        <td valign="center"><label for="<%=h(xlsxGUID)%>">Excel Workbook (.xlsx)</label> <span style="font-size: smaller">Maximum 1,048,576 rows and 16,384 columns.</span></td>
    </tr>
    <tr>
        <td valign="center"><input type="radio" id="<%=h(xlsGUID)%>" name="<%=h(checkboxGroupName)%>" /></td>
        <td valign="center"><label for="<%=h(xlsGUID)%>">Excel Old Binary Workbook (.xls)</label> <span style="font-size: smaller">Maximum 65,536 rows and 256 columns.</span></td>
    </tr>
    <% if (model.getIqyURL() != null) { %>
        <tr>
            <td valign="center"><input type="radio" id="<%=h(iqyGUID)%>" name="<%=h(checkboxGroupName)%>"/></td>
            <td valign="center"><label for="<%=h(iqyGUID)%>">Refreshable Web Query (.iqy)</label></td>
        </tr>
    <% } %>
    <tr>
        <td colspan="2"><label>Column headers:<%= PageFlowUtil.helpPopup("Column Header Options", sb.toString(), true) %></label>
            <select id="<%=text(headerGUID)%>" name="<%=text(headerType)%>">
                <labkey:options value="<%=model.getHeaderType()%>" map="<%=headerMap%>" />
            </select>
        </td>
    </tr>
    <% if (model.isSelectable()) { %>
    <tr><td colspan="2"></td></tr>
    <tr>
        <td valign="center">
            <input <%=text(hasSelected ? "" : "disabled=\"\"")%> type="checkbox" id="<%=h(exportSelectedId)%>"
                                                                 value="exportSelected" <%=checked(hasSelected)%> <%=disabled(!hasSelected)%>/>
        </td>
        <td valign="center">
            <label class="<%=text(hasSelected ? "" : "labkey-disabled")%>" id="<%=h(exportSelectedId + "_label")%>"
                   for="<%=h(exportSelectedId)%>"> <%=h(model.isIncludeSignButton() ? "Export/Sign selected rows" : "Export selected rows")%>
            </label>
        </td>
    </tr>
    <% } %>
    <tr>
        <td colspan="2">
            <%= button(exportButtonText).primary(true).id(exportButtonId) %>
            <%= model.isIncludeSignButton() ? button(signButtonText).id(signButtonId) : " "%>
        </td>
    </tr>
</table>
<script type="text/javascript">
    (function($) {
        LABKEY.DataRegion.registerPane(<%=PageFlowUtil.jsString(model.getDataRegionName())%>, function(dr) {
            var xlsExportEl = $("#<%=h(xlsGUID)%>");
            var xlsxExportEl = $("#<%=h(xlsxGUID)%>");
            var iqyExportEl = $("#<%=h(iqyGUID)%>");
            var headerEl = $("#<%=h(headerGUID)%>");
            var includeSignButton = <%=model.isIncludeSignButton()%>;

            var exportSelectedEl = $("#<%=h(exportSelectedId)%>");
            var exportSelectedLabelEl = $("#<%=h(exportSelectedId + "_label")%>");

            var doExcelExport = function(isSign) {
                var exportUrl, exportParams, exportRegionName = <%=PageFlowUtil.jsString(exportRegionName)%>;

                if (xlsxExportEl.is(':checked')) {
                    if (isSign) {
                        exportUrl = <%=PageFlowUtil.jsString(model.getSignXlsxURL().getPath(false))%>;
                        exportParams = <%=text( new JSONObject(model.getSignXlsxURL().getParameterMap()).toString(2) )%>;
                    }
                    else {
                        exportUrl = <%=PageFlowUtil.jsString(model.getXlsxURL().getPath(false))%>;
                        exportParams = <%=text( new JSONObject(model.getXlsxURL().getParameterMap()).toString(2) )%>;
                    }
                }
                else if (xlsExportEl.is(':checked')) {
                    if (isSign) {
                        exportUrl = <%=PageFlowUtil.jsString(model.getSignXlsURL().getPath(false))%>;
                        exportParams = <%=text( new JSONObject(model.getSignXlsURL().getParameterMap()).toString(2) )%>;
                    }
                    else {
                        exportUrl = <%=PageFlowUtil.jsString(model.getXlsURL().getPath(false))%>;
                        exportParams = <%=text( new JSONObject(model.getXlsURL().getParameterMap()).toString(2) )%>;
                    }
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

                if (!isSign) {
                    dr.addMessage({
                        html: '<div class="labkey-message"><strong>Excel export started.</strong></div>',
                        part: 'excelExport', hideButtonPanel: true, duration: 5000
                    });
                }
                else {
                    LABKEY.requiresScript(['Ext4', 'SignSnapshotPanel.js'], function() {
                        Ext4.onReady(function() {
                            Ext4.create('LABKEY.Query.SignSnapshotPanel', {
                                emailInput: '<%=h(model.getEmail())%>',
                                params: exportParams,
                                url: exportUrl
                            });
                        });
                    });
                    return false;
                }

                //Destroy iframe from previous download
                var downloadIFrameId = 'downloadIFrame';
                var oldIFrame = document.getElementById(downloadIFrameId);
                if (oldIFrame) {
                    oldIFrame.parentNode.removeChild(oldIFrame);
                }

                <%-- Sometimes the GET URL gets too long, so use a POST instead. We have to create a separate <form> since we might --%>
                <%-- already be inside a form for the DataRegion itself. --%>
                var newIFrame = document.createElement('iframe');
                newIFrame.id = downloadIFrameId;
                newIFrame.style.display = 'none';
                document.body.appendChild(newIFrame);
                //Check for contentWindow vs contentDocument for cross browser support
                var contentDoc = newIFrame.contentWindow || newIFrame.contentDocument;
                if (contentDoc.document) {
                    contentDoc = contentDoc.document;
                }
                var newForm = contentDoc.createElement('form');
                newForm.method = 'post';
                contentDoc.body.appendChild(newForm);

                <%-- Add the CSRF form input --%>
                var csrfElement = document.createElement('input');
                csrfElement.setAttribute('name', 'X-LABKEY-CSRF');
                csrfElement.setAttribute('type', 'hidden');
                csrfElement.setAttribute('value', LABKEY.CSRF);
                newForm.appendChild(csrfElement);

                function addInput(form, property, value){
                    // Issue 25592. Browsers are required to canonicalize newlines to \r\n in form inputs, per the HTTP
                    // spec (https://www.w3.org/TR/html401/interact/forms.html#h-17.13.4). This mangles the desired filter,
                    // so put those values on the GET URL instead.
                    if (value && (value.indexOf('\n') != -1 || value.indexOf('\r') != -1))
                    {
                        exportUrl = exportUrl + (exportUrl.indexOf('?') == -1 ? '?' : '&') + encodeURIComponent(property) + '=' + encodeURIComponent(value);
                    }
                    else
                    {
                        var newElement = document.createElement('input');
                        newElement.setAttribute('name', property);
                        newElement.setAttribute('type', 'hidden');
                        newElement.setAttribute('value', value);
                        form.appendChild(newElement);
                    }
                }

                $.each(exportParams, function(prop, val) {
                    if ($.isArray(val)) {
                        $.each(val, function(i, v) { addInput(newForm, prop, v); });
                    }
                    else {
                        addInput(newForm, prop, val);
                    }
                });

                newForm.action = exportUrl;
                var downloadIFrame = $('#' + downloadIFrameId);
                if (downloadIFrame && downloadIFrame.contents().find('form') && downloadIFrame.contents().find('form').length > 0) {
                    downloadIFrame.on('load', function(){
                        var response = {
                                    responseText: '',
                                    responseXML: null
                                }, doc, firstChild;

                        try {
                            doc = newIFrame.contentWindow.document || newIFrame.contentDocument || window.frames[id].document;
                            if (doc) {
                                if (doc.body) {
                                    if (/textarea/i.test((firstChild = doc.body.firstChild || {}).tagName)) { // json response wrapped in textarea
                                        response.responseText = firstChild.value;
                                    } else {
                                        response.responseText = doc.body.innerHTML;
                                    }
                                }
                                //in IE the document may still have a body even if returns XML.
                                response.responseXML = doc.XMLDocument || doc;
                            }
                        }
                        catch (e) {
                        }

                        if (response.responseXML && response.responseXML.title) {
                            var title = response.responseXML.title;
                            var index = title.indexOf("Error Page -- ");
                            if (index != -1) {
                                var message = title.substring(index + "Error Page -- ".length);
                                dr.showErrorMessage("Error: " + message);
                            }
                        }

                    });

                    downloadIFrame.contents().find('form')[0].submit();
                }

                return false;
            };

            var enableExportSelected = function (enabled) {
                if (enabled) {
                    if (exportSelectedEl.is(':disabled')) {
                        exportSelectedEl.prop('checked', true);
                        exportSelectedEl.prop('disabled', false);
                        exportSelectedLabelEl.removeClass();
                    }
                }
                else {
                    exportSelectedEl.prop('checked', false);
                    exportSelectedEl.prop('disabled', true);
                    exportSelectedLabelEl.addClass('labkey-disabled');
                }
            };

            var exportButtonEl = $("#<%=h(exportButtonId)%>");
            exportButtonEl.click(function() {
                doExcelExport(false);
            });

            var signButtonEl;
            if (includeSignButton) {
                signButtonEl = $("#<%=h(signButtonId)%>");
                signButtonEl.click(function() {
                    doExcelExport(true);
                });
            }

            dr.on('selectchange', function(dr, selectedCount) {
                if (!iqyExportEl.is(':checked'))
                    enableExportSelected(selectedCount > 0);
            });

            var changeHandler = function (e) {
                if (signButtonEl)
                    signButtonEl.prop('hidden', false);
                var target = $(e.target);
                dr.getSelected({
                    success: function (d) {
                        if (d.selected && d.selected.length > 0)
                            enableExportSelected(target.val());
                    }
                });
            };

            <%-- When xls or xlsx option is chosen, enable the "export selected" checkbox if there is selection.
                 When iqy is chosen, diable the "export selected" checkbox. --%>
            xlsExportEl.change(changeHandler);
            xlsxExportEl.change(changeHandler);
            iqyExportEl.change(function () {
                enableExportSelected(false);
                if (signButtonEl)
                    signButtonEl.prop('hidden', true);
            });
        });
    })(jQuery);
</script>

