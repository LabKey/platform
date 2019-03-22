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
<%@ page import="org.labkey.api.data.ColumnHeaderType" %>
<%@ page import="org.labkey.api.data.TSVWriter" %>
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
    QueryView.TextExportOptionsBean model = (QueryView.TextExportOptionsBean)HttpView.currentModel();

    String guid = GUID.makeGUID();
    String delimGUID = "delim_" + guid;
    String quoteGUID = "quote_" + guid;
    String headerGUID = "header_" + guid;
    String headerType = "txt_header_type";
    String delim = "delim";
    String quote = "quote";
    String exportSelectedId = "exportSelected_" + guid;
    String exportButtonId = "export_" + guid ;
    String signButtonId = "sign_" + guid;
    String exportButtonText = "Export";
    String signButtonText = "Sign Data";

    Map<String, String> delimiterMap = new LinkedHashMap<>();
    delimiterMap.put(TSVWriter.DELIM.TAB.name(), TSVWriter.DELIM.TAB.text);
    delimiterMap.put(TSVWriter.DELIM.COMMA.name(), TSVWriter.DELIM.COMMA.text);
    delimiterMap.put(TSVWriter.DELIM.COLON.name(), TSVWriter.DELIM.COLON.text);
    delimiterMap.put(TSVWriter.DELIM.SEMICOLON.name(), TSVWriter.DELIM.SEMICOLON.text);

    Map<String, String> quoteMap = new LinkedHashMap<>();
    quoteMap.put(TSVWriter.QUOTE.DOUBLE.name(), "Double (" + TSVWriter.QUOTE.DOUBLE.quoteChar + ")");
    quoteMap.put(TSVWriter.QUOTE.SINGLE.name(), "Single (" + TSVWriter.QUOTE.SINGLE.quoteChar + ")");

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
        <td><label for="<%=text(delimGUID)%>">Separator:</label></td>
        <td>
            <select id="<%=text(delimGUID)%>" name="<%=text(delim)%>">
                <labkey:options value="<%=TSVWriter.DELIM.TAB%>" map="<%=delimiterMap%>" />
            </select>
        </td>
    </tr>
    <tr>
        <td><label for="<%=text(quoteGUID)%>">Quote:</label></td>
        <td>
            <select id="<%=text(quoteGUID)%>" name="<%=text(quote)%>">
                <labkey:options value="<%=TSVWriter.QUOTE.DOUBLE%>" map="<%=quoteMap%>" />
            </select>
        </td>
    </tr>
    <tr>
        <td><label>Column headers:<%= PageFlowUtil.helpPopup("Column Header Options", sb.toString(), true) %></label></td>
        <td>
            <select id="<%=text(headerGUID)%>" name="<%=text(headerType)%>">
                <labkey:options value="<%=model.getHeaderType()%>" map="<%=headerMap%>" />
            </select>
        </td>
    </tr>
    <% if (model.isSelectable()) { %>
        <tr><td colspan="2"></td></tr>
        <tr>
            <td valign="center" colspan="2">
                <input type="checkbox" id="<%=h(exportSelectedId)%>" value="exportSelected" <%=checked(hasSelected)%> <%=disabled(!hasSelected)%>/>
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
<script>
    (function($) {
        LABKEY.DataRegion.registerPane(<%=PageFlowUtil.jsString(model.getDataRegionName())%>, function(dr) {
            var delimEl = $("#<%=h(delimGUID)%>"),
                quoteEl = $("#<%=h(quoteGUID)%>"),
                exportSelectedEl = $("#<%=h(exportSelectedId)%>"),
                exportSelectedLabelEl = $("#<%=h(exportSelectedId + "_label")%>"),
                headerEl = $("#<%=h(headerGUID)%>");
            var includeSignButton = <%=model.isIncludeSignButton()%>;

            var doTsvExport = function(isSign) {
                var exportRegionName = <%=PageFlowUtil.jsString(exportRegionName)%>;
                var selectedParam = exportRegionName + '.showRows=SELECTED';
                var url = isSign ?
                        <%=PageFlowUtil.jsString(model.getSignTsvURL().toString())%> :
                        <%=PageFlowUtil.jsString(model.getTsvURL().toString())%>;
                if (exportSelectedEl.is(':checked')) {
                    if (url.indexOf(exportRegionName + '.showRows=ALL') == -1) {
                        url = url+'&'+selectedParam;
                    }
                    else {
                        url = url.replace(exportRegionName + '.showRows=ALL', selectedParam);
                    }
                    url = url + '&' + exportRegionName + '.selectionKey=' + dr.selectionKey;
                }

                url = url + '&delim=' + delimEl.val() + '&quote=' + quoteEl.val();
                if (headerEl && headerEl.val())
                    url = url + '&headerType=' + headerEl.val();

                if (!isSign) {
                    dr.addMessage({
                        html: '<div class=\"labkey-message\"><strong>Text export started.</strong></div>',
                        part: 'excelExport', hideButtonPanel: true, duration: 5000
                    });
                    window.location = url;
                }
                else {
                    LABKEY.requiresScript(['Ext4', 'SignSnapshotPanel.js'], function() {
                        Ext4.onReady(function() {
                            Ext4.create('LABKEY.Query.SignSnapshotPanel', {
                                url: url,
                                emailInput: '<%=h(model.getEmail())%>'
                            });
                        });
                    });
                }

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
            exportButtonEl.click(function() {
                doTsvExport(false)
            });

            if (includeSignButton) {
                var signButtonEl = $("#<%=h(signButtonId)%>");
                signButtonEl.click(function() {
                    doTsvExport(true);
                });
            }

            dr.on('selectchange', function(dr, selectedCount) {
                selectedCount > 0 ? enableExportSelected() : disableExportSelected();
            });
        });
    })(jQuery);
</script>

