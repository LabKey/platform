<%
/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!

    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromFilePath("clientapi/ext4"));
        return resources;
    }
%>
<script type="text/javascript">
    Ext4.onReady(function ()
    {
        var qwp = new LABKEY.QueryWebPart({
            renderTo: 'transformHistoryDiv',
            schemaName: 'dataintegration',
            queryName: 'transformhistory',
            frame : 'none'
        });

        Ext4.create('Ext.Button', {
            text: 'Scheduler',
            renderTo: 'schedulerButton',
            handler: function ()
            {
                window.location = LABKEY.ActionURL.buildURL('DataIntegration', 'begin');
            }
        });

    });
</script>
<span>
<div id="transformHistoryDiv"></div>
<p/>
<div id = "schedulerButton"></div>
</span>