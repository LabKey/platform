<%
/*
 * Copyright (c) 2009 LabKey Corporation
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
<%@ page import="org.labkey.api.study.actions.BulkPropertiesDisplayColumn" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.study.actions.BulkPropertiesUploadForm" %>

<%
    BulkPropertiesUploadForm form = ((JspView<BulkPropertiesUploadForm>)HttpView.currentView()).getModelBean();
    String existingValue = form.getRawBulkProperties();
    boolean useBulk = form.isBulkUploadAttempted();
%>
<script type="text/javascript">
    function toggleBulkProperties()
    {
        if (document.getElementById('<%= BulkPropertiesDisplayColumn.ENABLED_FIELD_NAME %>On').checked)
        {
            document.getElementById('<%= BulkPropertiesDisplayColumn.PROPERTIES_FIELD_NAME%>').style.display='block';
        }
        else
        {
            document.getElementById('<%= BulkPropertiesDisplayColumn.PROPERTIES_FIELD_NAME%>').style.display='none';
        }
    }
</script>

<table>
    <tr>
        <td>
            <input type="radio" id="<%= BulkPropertiesDisplayColumn.ENABLED_FIELD_NAME %>Off" <%= !useBulk ? "checked" : "" %> value="off"
            onclick="toggleBulkProperties()" name="<%= BulkPropertiesDisplayColumn.ENABLED_FIELD_NAME %>">
        </td>
        <td>
            Enter run properties for each run separately by entering values into a form
        </td>
    </tr>
    <tr>
        <td>
            <input type="radio" id="<%= BulkPropertiesDisplayColumn.ENABLED_FIELD_NAME %>On" <%= useBulk ? "checked" : "" %> value="on"
            onclick="toggleBulkProperties()" name="<%= BulkPropertiesDisplayColumn.ENABLED_FIELD_NAME %>">
        </td>
        <td>
            Specify run properties for all runs at once with tab-separated values (TSV)<%= PageFlowUtil.helpPopup("Bulk Properties", "<p>You may use a set of TSV (tab-separated values) to specify run metadata.<p>The barcode column in the TSV is matched with the barcode value in the MageML file. The sample name columns, configured in the assay design, will be used to look for matching samples by name in all visible sample sets.</p><p>Any additional run level properties may be specified as separate columns.</p>", true)%>
        </td>
    </tr>
    <tr>
        <td/>
        <td id="<%= BulkPropertiesDisplayColumn.PROPERTIES_FIELD_NAME %>" <%= !useBulk ? "style=\"display: none;\"" : "" %>>
            <% if (form.getTemplateURL() != null) { %>[<a href="<%= form.getTemplateURL() %>">download Excel template</a>]<br/><% } %>
            <textarea style="width: 100%"
                    rows="5" cols="80"
                    name="<%= BulkPropertiesDisplayColumn.PROPERTIES_FIELD_NAME %>"><%= PageFlowUtil.filter(existingValue) %></textarea>
        </td>
    </tr>
</table>
<script type="text/javascript">
    // Allow tabs in the TSV text area
    Ext.EventManager.on('<%= BulkPropertiesDisplayColumn.PROPERTIES_FIELD_NAME%>', 'keydown', handleTabsInTextArea);
</script>
