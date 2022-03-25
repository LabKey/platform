<%
/*
 * Copyright (c) 2011-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.assay.AssayProvider" %>
<%@ page import="org.labkey.api.assay.AssayRunUploadContext" %>
<%@ page import="org.labkey.api.assay.AssayUrls" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.assay.view.PlateMetadataDataCollector" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<PlateMetadataDataCollector> me = (JspView<PlateMetadataDataCollector>) HttpView.currentView();
    PlateMetadataDataCollector<? extends AssayRunUploadContext<? extends AssayProvider>> bean = me.getModelBean();
    AssayProvider provider = bean.getContext().getProvider();
    ActionURL plateUrl = urlProvider(AssayUrls.class).getPlateMetadataTemplateURL(getContainer(), provider);
%>

<script type="text/javascript" nonce="<%=getScriptNonce()%>">
LABKEY.Utils.onReady(function () {
    var plateTemplateEl;
    var nl = document.getElementsByName('plateTemplate');
    if (nl.length > 0)
        plateTemplateEl = nl[0];

    var plateMetadataExampleEl = document.getElementById('plateMetadataExample');

    if (plateTemplateEl) {
        var plateUrl = <%=jsURL(plateUrl)%>;
        plateTemplateEl.addEventListener('change', function () {
            var templateLsid = plateTemplateEl.value;
            if (templateLsid)
                plateUrl.searchParams.set('template', templateLsid);
            else
                plateUrl.searchParams.delete('template');
            plateMetadataExampleEl.href = plateUrl.toString();
        });
    }
});
</script>
<table id="plate-metadata-file-upload-tbl">
    <tr><td>Plate metadata should be uploaded as a specifically formatted JSON file.</td></tr>
    <% if (plateUrl != null) { %>
        <tr><td><%= link("download example metadata file", plateUrl).id("plateMetadataExample") %></td></tr>
    <% } %>
    <tr>
        <td><input type="file" name="<%=h(bean.getInputName())%>" size="40" style="border: none"></td>
    </tr>
</table><br>

