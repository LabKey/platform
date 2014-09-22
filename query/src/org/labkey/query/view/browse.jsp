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
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromFilePath("clientapi/ext4"));
        resources.add(ClientDependency.fromFilePath("schemaBrowser.css"));
        resources.add(ClientDependency.fromFilePath("_images/icons.css"));
        resources.add(ClientDependency.fromFilePath("schemaBrowser.js"));
        return resources;
    }
%>
<div id="browserContainer">
    <div id="browser" class="schemabrowser"></div>
</div>
<!-- Fields required for history management -->
<form id="history-form" class="x-hidden">
    <input type="hidden" id="x-history-field" />
    <iframe id="x-history-frame"></iframe>
</form>
<script type="text/javascript">

    Ext4.onReady(function(){
        Ext4.create('LABKEY.ext4.SchemaBrowser', {
            renderTo: 'browser',
            boxMinHeight: 600,
            boxMinWidth: 900,
            useHistory: true,
            bindURLParams: true
        });
    });
</script>
