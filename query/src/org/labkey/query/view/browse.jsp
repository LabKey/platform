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
        resources.add(ClientDependency.fromFilePath("schemaBrowser.css"));
        resources.add(ClientDependency.fromFilePath("schemaBrowser.js"));
        return resources;
    }
%>
<div id="browserContainer" style="width:100%;">
    <div id="browser"></div>
</div>
<script type="text/javascript">
    var _browser = null;

    Ext4.onReady(function(){
        _browser = new LABKEY.ext.SchemaBrowser({
            renderTo: 'browser',
            boxMinHeight: 600,
            boxMinWidth: 900,
            useHistory: true,
            listeners: {
                schemasloaded: {
                    fn: onSchemasLoaded,
                    scope: this
                }
            }
        });
    });

    function onSchemasLoaded(browser)
    {
        var params = LABKEY.ActionURL.getParameters();

        var schemaName = params.schemaName;
        var queryName = params['queryName'] || params['query.queryName'];
        if (queryName && schemaName)
        {
            browser.selectQuery(schemaName, queryName, function(){
                browser.showQueryDetails(schemaName, queryName);
            });
        }
        else if (schemaName)
            browser.selectSchema(schemaName, queryName);

        if (window.location.hash && window.location.hash.length > 1)
        {
            //window.location.hash returns an decoded value, which
            //is different from what Ext.History.getToken() returns
            //so use the same technique Ext does for getting the hash
            var href = top.location.href;
            var idx = href.indexOf("#");
            var hash = idx >= 0 ? href.substr(idx + 1) : null;
            if (hash)
                browser.onHistoryChange(hash);
        }
    }
</script>

<!-- Fields required for history management -->
<form id="history-form" class="x-hidden">
    <input type="hidden" id="x-history-field" />
    <iframe id="x-history-frame"></iframe>
</form>
