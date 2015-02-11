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
<%@ page import="org.labkey.api.data.DataRegion" %>
<%@ page import="org.labkey.api.data.TSVWriter" %>
<%@ page import="org.labkey.api.query.QueryView" %>
<%@ page import="org.labkey.api.util.GUID" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="java.util.LinkedHashMap" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    String guid = GUID.makeGUID();
    String delimGUID = "delim_" + guid;
    String quoteGUID = "quote_" + guid;
    String exportSelectedId = "exportSelected_" + guid;
    String exportButtonId = "export_" + guid;

    Map<String, String> delimiterMap = new LinkedHashMap<>();
    delimiterMap.put(TSVWriter.DELIM.TAB.name(), TSVWriter.DELIM.TAB.text);
    delimiterMap.put(TSVWriter.DELIM.COMMA.name(), TSVWriter.DELIM.COMMA.text);
    delimiterMap.put(TSVWriter.DELIM.COLON.name(), TSVWriter.DELIM.COLON.text);
    delimiterMap.put(TSVWriter.DELIM.SEMICOLON.name(), TSVWriter.DELIM.SEMICOLON.text);

    Map<String, String> quoteMap = new LinkedHashMap<>();
    quoteMap.put(TSVWriter.QUOTE.DOUBLE.name(), "Double (" + TSVWriter.QUOTE.DOUBLE.quoteChar + ")");
    quoteMap.put(TSVWriter.QUOTE.SINGLE.name(), "Single (" + TSVWriter.QUOTE.SINGLE.quoteChar + ")");

    QueryView.TextExportOptionsBean model = (QueryView.TextExportOptionsBean)HttpView.currentModel();
    boolean hasSelected = model.hasSelected(getViewContext());
    String exportRegionName = model.getExportRegionName();
    String DRNamespace = DataRegion.useExperimentalDataRegion() ? "LABKEY.DataRegion2" : "LABKEY.DataRegion";
%>
<table class="labkey-export-tab-contents">
    <tr>
        <td><label for="<%=text(delimGUID)%>">Separator:</label></td>
        <td>
            <select id="<%=text(delimGUID)%>" name="delim">
                <labkey:options value="<%=TSVWriter.DELIM.TAB%>" map="<%=delimiterMap%>" />
            </select>
        </td>
    </tr>
    <tr>
        <td><label for="<%=text(quoteGUID)%>">Quote:</label></td>
        <td>
            <select id="<%=text(quoteGUID)%>" name="quote">
                <labkey:options value="<%=TSVWriter.QUOTE.DOUBLE%>" map="<%=quoteMap%>" />
            </select>
        </td>
    </tr>
    <tr><td colspan="2"></td></tr>
    <tr>
        <td valign="center" colspan="2">
            <input type="checkbox" id="<%=h(exportSelectedId)%>" value="exportSelected" <%=checked(hasSelected)%> <%=disabled(!hasSelected)%>/>
            <label class="<%=text(hasSelected ? "" : "labkey-disabled")%>" id="<%=h(exportSelectedId + "_label")%>" for="<%=h(exportSelectedId)%>">Export selected rows</label>
        </td>
    </tr>
    <tr>
        <td colspan="2"><%= button("Export to Text").id(exportButtonId) %></td>
    </tr>
</table>
<script>
    (function($) {

        <%=text(DRNamespace)%>.registerPane(<%=PageFlowUtil.jsString(model.getDataRegionName())%>, function(dr) {
            var delimEl = $("#<%=h(delimGUID)%>"),
                quoteEl = $("#<%=h(quoteGUID)%>"),
                exportSelectedEl = $("#<%=h(exportSelectedId)%>"),
                exportSelectedLabelEl = $("#<%=h(exportSelectedId + "_label")%>");

            var doTsvExport = function() {
                var exportRegionName = <%=PageFlowUtil.jsString(exportRegionName)%>;
                var selectedParam = exportRegionName + '.showRows=SELECTED';
                var url = <%=PageFlowUtil.jsString(model.getTsvURL().toString())%>;
                if (exportSelectedEl.is(':checked')) {
                    if (url.indexOf(exportRegionName + '.showRows=ALL') == -1) {
                        url = url+'&'+selectedParam;
                    }
                    else {
                        url = url.replace(exportRegionName + '.showRows=ALL', selectedParam);
                    }
                    url = url + '&' + exportRegionName + '.selectionKey=' + dr.selectionKey;
                }

                url = url + '&delim=' + delimEl.value + '&quote=' + quoteEl.value;

                dr.addMessage({
                    html: '<div class=\"labkey-message\"><strong>Text export started.</strong></div>',
                    part: 'excelExport', hideButtonPanel: true, duration:5000
                });

                window.location = url;
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
            exportButtonEl.click(doTsvExport);

            dr.on('selectchange', function(dr, selectedCount) {
                selectedCount > 0 ? enableExportSelected() : disableExportSelected();
            });
        });

    })(jQuery);
</script>

