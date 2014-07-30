<%
/*
 * Copyright (c) 2013 LabKey Corporation
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
        resources.add(ClientDependency.fromFilePath("Ext4"));
        return resources;
    }
%>
<div id='errorLog'></div>
<div id='runsChart'></div>
<div id='schedulerButton'></div>
<script type="text/javascript">

    function redirectToPipeline(id)
    {
        if (id)
        {
            window.location = LABKEY.ActionURL.buildURL("pipeline-status", "details", null, {rowId: id});
        }
        else
        {
            this.errorText.setText('No pipeline information available for selected run.');
            this.errorText.show();
        }
    }

    function redirectToExperiment(id)
    {
        if (id)
        {
            window.location = LABKEY.ActionURL.buildURL("experiment", "showRunText", null, {rowId: id});
        }
        else
        {
            this.errorText.setText('No experiment information available for selected run.');
            this.errorText.show();
        }
    }

    Ext4.onReady(function ()
    {

        Ext4.define('jobModel', {
            extend: 'Ext.data.Model',
            fields: [
                {name: 'Created', type: 'string'},
                {name: 'TransformId', type: 'string'},
                {name: 'JobId', type: 'string'},
                {name: 'Status', type: 'string'},
                {name: 'exprunid', type: 'string'}
            ]
        });

        this.errorText = Ext4.create('Ext.form.Label', {
            renderTo: 'errorLog',
            hidden: true,
            text: '',
            style: 'color : red'
        });

        var jobStore = Ext4.create('Ext.data.Store', {
            model: 'jobModel',
            sortInfo: [
                {field: 'TransformId', direction: 'ASC'},
                {field: 'Created', direction: 'ASC'}
            ]
        });
        var pipelineCol = Ext4.create('Ext.grid.column.Template', {
            text: 'Pipeline Info',
            width: 95,
            sortable: true,
            dataIndex: 'label',
            tpl: '<a class="labkey-button" href="#" onClick="redirectToPipeline({JobId})" ><span>Pipeline</span></a>'
        });

        var expCol = Ext4.create('Ext.grid.column.Template', {
            text: 'Experiment Info',
            width: 120,
            sortable: true,
            dataIndex: 'label',
            tpl: '<a class="labkey-button" href="#" onClick="redirectToExperiment({exprunid})" ><span>Experiment</span></a>'
        });

        var grid = Ext4.create('Ext.grid.Panel', {
            renderTo: 'runsChart',
            name: 'infoGrid',
            maxHeight: 2000,
            width: 650,
            store: jobStore,
            columns: [
                {header: 'Description', dataIndex: 'TransformId', width: 150},
                {header: 'Created At', dataIndex: 'Created', flex: 1},
                {header: 'Status', dataIndex: 'Status', width: 75},
                pipelineCol,
                expCol
            ]
        });

        Ext4.create('Ext.Button', {
            text: 'Scheduler',
            renderTo: 'schedulerButton',
            handler: function ()
            {
                window.location = LABKEY.ActionURL.buildURL('DataIntegration', 'begin');
            }
        });

        LABKEY.Query.selectRows({
            schemaName: 'dataintegration',
            queryName: 'transformrun',
            success: function (data)
            {
                jobStore.loadData(data.rows);
            },
            failure: function ()
            { /**/
            },
            filterArray: [LABKEY.Filter.create('Description', ['ETL'], LABKEY.Filter.Types.CONTAINS)]
        });

    });
</script>