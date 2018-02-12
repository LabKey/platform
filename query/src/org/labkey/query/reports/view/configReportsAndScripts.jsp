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
<%@ page import="org.apache.commons.lang3.StringUtils"%>
<%@ page import="org.labkey.api.files.FileContentService"%>
<%@ page import="org.labkey.api.reports.ExternalScriptEngine"%>
<%@ page import="org.labkey.api.reports.report.ExternalScriptEngineReport" %>
<%@ page import="org.labkey.api.reports.report.RReport" %>
<%@ page import="org.labkey.api.reports.report.ScriptEngineReport" %>
<%@ page import="org.labkey.api.rstudio.RStudioService" %>
<%@ page import="org.labkey.api.security.permissions.AdminOperationsPermission" %>
<%@ page import="org.labkey.api.services.ServiceRegistry" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.util.FileUtil" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext3");
    }
%>
<%
    boolean isRemoteEnabled = AppProps.getInstance().isExperimentalFeatureEnabled(AppProps.EXPERIMENTAL_RSERVE_REPORTING);
    boolean isRDockerAvailable = false;
    if (AppProps.getInstance().isExperimentalFeatureEnabled(RStudioService.R_DOCKER_SANDBOX))
    {
        RStudioService rs = ServiceRegistry.get(RStudioService.class);
        if (null != rs)
            isRDockerAvailable = rs.isConfigured();
    }
    boolean hasAdminOpsPerms = getContainer().hasPermission(getUser(), AdminOperationsPermission.class);
%>
<style type="text/css">

    .bmenu {
        background-image: url(<%=getContextPath() + "/_icons/exe.png"%>) !important;
    }

</style>

<script type="text/javascript">
    Ext.QuickTips.init();
    var R_EXTENSIONS = 'R,r';
    var PERL_EXTENSIONS = 'pl';
    var R_ENGINE_NAME = 'R Scripting Engine';
    var REMOTE_R_ENGINE_NAME = 'Remote R Scripting Engine';
    var R_DOCKER_ENGINE_NAME = 'R Docker Scripting Engine';

    function renderNameColumn(value, p, record)
    {
        var txt;

        if (record.data.enabled)
            txt = '<b>' + value + '</b><br>';
        else
            txt = '<b><div class="labkey-disabled">' + value + '</div></b><br>';

        txt = txt.concat('<i>enabled : ' + record.data.enabled + '</i><br>');
        txt = txt.concat('<i>external : ' + record.data.external + '</i><br>');

        return txt;
    }

    function recordsLoaded(records)
    {
        testItem.enable();
        for (var i in records)
        {
            if (records[i].data)
            {
                if (records[i].data.extensions == 'R,r')
                {
                    testItem.disable();
                }
            }
        }

    }

    function getEngineSpecificItems(record) {
        if (record.name === R_ENGINE_NAME || record.name === REMOTE_R_ENGINE_NAME)
        {
            return getRSpecificFields(record.pandocEnabled)
        }
        else if (record.name === R_DOCKER_ENGINE_NAME)
        {
            return [{
                name: 'pandocEnabled',
                xtype: 'hidden',
                value: true
            }];
        }

        return null;
    }

    function getRSpecificFields(enabled) {
        return [{
            fieldLabel: 'Use pandoc & rmarkdown',
            name: 'pandocEnabled',
            id: 'editEngine_pandocEnabled',
            xtype: 'checkbox',
            checked: enabled,
            tooltip: {text: 'Select this option if you have rmarkdown and pandoc installed. Please see knitr help documentation on labkey.org for more information.', title: 'Enable rmarkdown v2'},
            listeners: {render: setFormFieldTooltip}
        }];
    }

    function showEngines()
    {
        // pre defined engine templates

        var rEngineItem = new Ext.menu.Item({
            id: 'add_rEngine',
            text:'New R Engine',
            listeners:{
                click:function(button, event) {
                    var record = {
                        name: R_ENGINE_NAME,
                        extensions: R_EXTENSIONS,
                        exeCommand:'<%=text(RReport.DEFAULT_R_CMD)%>',
                        <% if (!StringUtils.isEmpty(RReport.getDefaultRPath())) { %>
                            exePath: <%=q(RReport.getDefaultRPath())%>,
                        <% } %>
                        outputFileName: <%= q(ExternalScriptEngine.SCRIPT_NAME_REPLACEMENT + ".Rout") %>,
                        external: true,
                        enabled: true,
                        remote: false,
                        languageName:'R'
                    };

                    editRecord(button, grid, record);
                }
            }
        });

    <% if (isRemoteEnabled) { %>
        var rserveEngineItem = new Ext.menu.Item({
            id: 'add_rserveEngine',
            text:'New Remote R Engine',
            listeners:{
                click:function(button, event) {
                    var record = {
                        name: REMOTE_R_ENGINE_NAME,
                        extensions: R_EXTENSIONS,
                        machine:'<%=text(RReport.DEFAULT_R_MACHINE)%>',
                        port:<%=RReport.DEFAULT_R_PORT%>,
                        exeCommand:'<%=text(RReport.DEFAULT_RSERVE_CMD)%>',
                        outputFileName: <%= q(ExternalScriptEngine.SCRIPT_NAME_REPLACEMENT + ".Rout") %>,
                        external: true,
                        enabled: true,
                        remote : true,
                        languageName:'R'
                    };

                    record['pathMap'] = {
                        localIgnoreCase: <%= FileUtil.isCaseInsensitiveFileSystem() %>,
                        remoteIgnoreCase: false,
                        paths: [
                            {
                                localURI: <%=PageFlowUtil.jsString(ScriptEngineReport.getDefaultTempRoot().toURI().toString())%>,
                                remoteURI: ''
                            }
                            <% if (null != FileContentService.get()) { %>
                            ,{
                                localURI: <%=PageFlowUtil.jsString(FileContentService.get().getSiteDefaultRoot().toURI().toString())%>,
                                remoteURI: ''
                            }
                            <% } %>
                        ]
                    };

                    editRecord(button, grid, record);
                }
            }
        });
    <% } %>

    <% if (isRDockerAvailable) { %>
        var rDockerEngineItem = new Ext.menu.Item({
            id: 'add_rDockerEngine',
            text:'New R Docker Engine',
            listeners:{
                click:function(button, event) {
                    var record = {
                        name: R_DOCKER_ENGINE_NAME,
                        extensions: R_EXTENSIONS,
                        external: true,
                        outputFileName: <%= q(ExternalScriptEngine.SCRIPT_NAME_REPLACEMENT + ".Rout") %>,
                        enabled: true,
                        docker: true,
                        remote: true,
                        languageName:'R'
                    };
                    editRecord(button, grid, record);
                }
            }
        });
    <% } %>
        var perlEngineItem = new Ext.menu.Item({
            id: 'add_perlEngine',
            text:'New Perl Engine',
            listeners:{click:function(button, event) {editRecord(button, grid,{
                name:'Perl Scripting Engine',
                extensions: PERL_EXTENSIONS,
                external: true,
                <% if (!StringUtils.isEmpty(ExternalScriptEngineReport.getDefaultPerlPath())) { %>
                    exePath: <%=q(ExternalScriptEngineReport.getDefaultPerlPath())%>,
                <% } %>
                enabled: true,
                remote : false,
                languageName:'Perl'});}}}
            );


        var con = new Ext.data.HttpProxy(new Ext.data.Connection({
                url: LABKEY.ActionURL.buildURL("reports", "scriptEnginesSummary", <%=q(getContainer().getPath())%>),
                method: 'GET'
            }));

        var store = new Ext.data.Store({
            reader: new Ext.data.JsonReader({root:'views',id:'key'},
                    [
                        {name:'name'},
                        {name:'exePath'},
                        {name:'exeCommand'},
                        {name:'machine'},
                        {name:'port'},
                        {name:'pathMap'},
                        {name:'user'},
                        {name:'password'},
                        {name:'extensions'},
                        {name:'languageName'},
                        {name:'languageVersion'},
                        {name:'key'},
                        {name:'enabled', type:'boolean'},
                        {name:'external', type:'boolean'},
                        {name:'remote', type:'boolean'},
                        {name:'docker', type:'boolean'},
                        {name:'outputFileName'},
                        {name:'pandocEnabled', type:'boolean'}
                    ]),
            proxy: con,
            autoLoad: true,
            listeners:{load:function(store, records, options) {
                rEngineItem.enable();
        <% if (isRemoteEnabled) { %>
                rserveEngineItem.enable();
        <%  }
            if (isRDockerAvailable) { %>
                rDockerEngineItem.enable();
        <%  } %>
                perlEngineItem.enable();
                for (var i in records) {
                    if (records[i].data) {
                        if (records[i].data.extensions == R_EXTENSIONS)  {
                            <% if (isRemoteEnabled) { %>
                                if (records[i].data.remote)
                                    rserveEngineItem.disable();
                            <%  } %>
                            if (!records[i].data.remote)
                                rEngineItem.disable();
                            <% if (isRDockerAvailable) { %>
                            if (records[i].data.docker)
                                rDockerEngineItem.disable();
                            <%  } %>
                        }

                        if (records[i].data.extensions == PERL_EXTENSIONS)
                            perlEngineItem.disable();
                    }
                }
            }},
            sortInfo: {field:'name', direction:"ASC"}});

        var newMenu = new Ext.menu.Menu({
            id: 'mainMenu',
            cls:'extContainer',
            items: [rEngineItem,
        <% if (isRemoteEnabled) { %>
                rserveEngineItem,
        <% } %>
        <% if (isRDockerAvailable) { %>
                rDockerEngineItem,
        <% } %>
                perlEngineItem,
                {
                id: 'add_externalEngine',
                text:'New External Engine',
                listeners:{click:function(button, event) {editRecord(button, grid, {
                    name:"External",
                    enabled:true,
                    external: true});}}
            }] });

        var grid = new Ext.grid.GridPanel({
            el:'enginesGrid',
            autoScroll:false,
            autoHeight:true,
            enableHdMenu: false,
            width:800,
            store: store,
            listeners: {
                rowdblclick: function(g, rowIndex, event) {
                    event.stopEvent();
                    editSelected(event, g);
                }
            },
            columns:[
                {header:'Name', dataIndex:'name', renderer:renderNameColumn},
                {header:'Language', dataIndex:'languageName'},
                {header:'Language Version', dataIndex:'languageVersion'},
                {header:'File Extensions', dataIndex:'extensions'},
            <% if (isRemoteEnabled) { %>
                {header:'Remote', dataIndex:'remote'},
            <% } %>
                {header:'Application Location', dataIndex:'exePath', hidden:true},
                {header:'Run Command', dataIndex:'exeCommand', hidden:true},
                {header:'Output File Name', dataIndex:'outputFileName', hidden:true}
            ],
            view: new Ext.grid.GridView({
                forceFit:true
            }),
            buttons: [
                {text:'Add', id: 'btn_addEngine', menu: newMenu, tooltip: {text:'Configure a new external script engine', title:'Add Engine'}, hidden: <%=!hasAdminOpsPerms%>},
                {text:'Delete', id: 'btn_deleteEngine', tooltip: {text:'Delete the selected script engine', title:'Delete Engine'}, listeners:{click:function(button, event) {deleteSelected(grid);}}, hidden: <%=!hasAdminOpsPerms%>},
                {text:'Edit', id: 'btn_editEngine', tooltip: {text:'Edit an existing script engine', title:'Edit Engine'}, listeners:{click:function(button, event) {editSelected(button, grid);}}, hidden: <%=!hasAdminOpsPerms%>},
                {text:'Done', id: 'btn_done', tooltip: {text:'Return back to the Admin Console'}, listeners:{click:function(button, event) {window.location = LABKEY.ActionURL.buildURL('admin', 'showAdmin');}}}
            ],
            buttonAlign:'left',
            selModel: new Ext.grid.RowSelectionModel({singleSelect: true})
        });

        grid.render();
    }

    function deleteSelected(grid)
    {
        var selections = grid.selModel.getSelections();

        if (selections.length == 0)
        {
            Ext.Msg.alert("Delete Engine Configuration", "There is no engine selected");
            return false;
        }

        var record = selections[0].data;
        var params = [];

        if (!record.external)
        {
            Ext.Msg.alert("Delete Engine Configuration", "Java 6 script engines cannot be deleted but you can disable them.");
            return false;
        }
        params.push("key=" + record.key);
        params.push("extensions=" + record.extensions);

        Ext.Msg.confirm('Delete Engine Configuration', "Are you sure you wish to delete the selected Configuration? : " + record.name, function(btn, text) {
            if (btn == 'yes')
            {
                Ext.Ajax.request({

                    url: LABKEY.ActionURL.buildURL("reports", "scriptEnginesDelete") + '?' + params.join('&'),
                    method: "POST",
                    success: function(){grid.store.load();},
                    failure: function(){Ext.Msg.alert("Delete Engine Configuration", "Deletion Failed");}
                });
            }
        });
    }

    function setFormFieldTooltip(component)
    {
        var label = Ext.get('x-form-el-' + component.id).prev('label');
        Ext.QuickTips.register({
            target: label,
            text: component.tooltip.text,
            title: ''
        });
    }

    function editSelected(button, grid)
    {
        var selections = grid.selModel.getSelections();
        if (selections.length == 0)
        {
            Ext.Msg.alert("Edit Engine Configuration", "There is no engine selected");
            return false;
        }

        editRecord(button, grid, selections[0].data);
    }

    function editRecord(button, grid, record)
    {
        var itemPath = {
            fieldLabel: 'Program Path',
            name: 'exePath',
            id: 'editEngine_exePath',
            allowBlank: false,
            value: record.exePath,
            tooltip: {text: 'Specify the absolute path to the program including the program itself', title: 'Program Path'},
            listeners: {render: setFormFieldTooltip},
            disabled: !record.external,
            width: 275
        };

        var itemMachine = {
            fieldLabel: 'Machine Name',
            name: 'machine',
            id: 'editEngine_machine',
            allowBlank: false,
            value: record.machine,
            tooltip: {text: 'Specify the machine name or IP address that Rserve is running on', title: 'Machine Name'},
            listeners: {render: setFormFieldTooltip},
            disabled: !record.external,
            width: 275
        };

        var itemCmd = {
            fieldLabel: 'Program Command',
            name: 'exeCommand',
            id: 'editEngine_exeCommand',
            tooltip: {text: 'The command used when the program is invoked', title: 'Program Command'},
            listeners: {render: setFormFieldTooltip},
            disabled: !record.external,
            value: record.exeCommand,
            width: 275
        };

        var itemPort = {
            fieldLabel: 'Port',
            name: 'port',
            id: 'editEngine_port',
            tooltip: {text: 'The port used to connect to Rserve', title: 'Port'},
            listeners: {render: setFormFieldTooltip},
            disabled: !record.external,
            value: record.port,
            width: 275
        };

        var pathMapStore = new Ext.data.JsonStore({
            fields: [
                {
                    name: 'localURI',
                    allowBlank: false
                },
                {
                    name: 'remoteURI',
                    allowBlank: false
                }
            ],
            idProperty: 'localURI',
            root: 'paths'
        });

        if (record.pathMap)
        {
            pathMapStore.loadData(record.pathMap);
        }

        var editor = new Ext.form.TextField();

        var itemPathGrid = new Ext.grid.EditorGridPanel({
            xtype: 'grid',
            fieldLabel: 'Path Mapping',
            name: 'pathMap',
            id: 'editEngine_pathMap',
            tooltip: {text: 'Add or remove local to remote path mappings', title: 'Local to Remote Path Mapping'},
            listeners: {render: setFormFieldTooltip},
            disabled: !record.external,
            stripeRows: true,
            autoEncode: true,
            enableColumnHide: false,
            store: pathMapStore,
            colModel: new Ext.grid.ColumnModel({
                defaults: {
                    sortable: false
                },
                columns: [
                    {id: 'localURI', header: 'Local', dataIndex: 'localURI', editable: true, editor: editor, width: 200, renderer: Ext.util.Format.htmlEncode},
                    {id: 'remoteURI', header: 'Remote', dataIndex: 'remoteURI', editable: true, editor: editor, width: 200, renderer: Ext.util.Format.htmlEncode}
                ]
            }),
            tbar: [
                {
                    text: 'Add',
                    handler: function ()
                    {
                        var data = {'localURI': '', 'remoteURI': ''};
                        var record = new pathMapStore.recordType(data);
                        pathMapStore.add(record);
                    }
                },
                {
                    text: 'Remove',
                    handler: function (btn, evt)
                    {
                        var record = itemPathGrid.getSelectionModel().getSelected();
                        pathMapStore.remove(record);
                    }
                }
            ],
            sm: new Ext.grid.RowSelectionModel({singleSelect: true}),
            width: 430,
            height: 160,
            viewConfig: {forceFit: true},

            // Get the JSON object used to submit
            getValue: function ()
            {
                console.log("grid");
            }
        });

        var itemUser = {
            fieldLabel: 'Remote User',
            name: 'user',
            id: 'editEngine_user',
            tooltip: {text: 'The user for the remote service login', title: 'Remote User'},
            listeners: {render: setFormFieldTooltip},
            disabled: !record.external,
            value: record.user,
            width: 275
        };

        var itemPassword = {
            fieldLabel: 'Remote Password',
            name: 'password',
            id: 'editEngine_password',
            tooltip: {text: 'The password for the remote service login account', title: 'Remote Password'},
            listeners: {render: setFormFieldTooltip},
            inputType: 'password',
            disabled: !record.external,
            value: record.password,
            width: 275
        };

        var itemName = {
            fieldLabel: "Name",
            name: 'name',
            id: 'editEngine_name',
            allowBlank: false,
            readOnly: !record.external,
            value: record.name
        };

        var itemLanguageName = {
            fieldLabel: 'Language',
            name: 'languageName',
            id: 'editEngine_languageName',
            allowBlank: false,
            readOnly: !record.external || record.docker,
            value: record.languageName
        };

        var itemLanguageVersion = {
            fieldLabel: 'Language Version',
            name: 'languageVersion',
            id: 'editEngine_languageVersion',
            readOnly: !record.external,
            value: record.languageVersion
        };

        var itemExtensions = {
            fieldLabel: 'File Extensions',
            name: 'extensions',
            id: 'editEngine_extensions',
            allowBlank: false,
            tooltip: {text: 'The list of file extensions (separated by commas) that this engine is associated with', title: 'File Extensions'},
            listeners: {render: setFormFieldTooltip},
            readOnly: !record.external || record.docker,
            value: record.extensions
        };

        var itemOutputFileName = {
            fieldLabel: 'Output File Name',
            name: 'outputFileName',
            id: 'editEngine_outputFileName',
            value: record.outputFileName,
            tooltip: {text: 'If the console output is written to a file, the name should be specified here. The substitution syntax \\${scriptName} will be replaced with the name (minus the extension) of the script being executed.', title: 'Output File Name'},
            disabled: !record.external,
            readOnly:  record.docker,
            listeners: {render: setFormFieldTooltip}
        };

        var itemEnabled = {
            fieldLabel: 'Enabled',
            name: 'enabled',
            id: 'editEngine_enabled',
            xtype: 'checkbox',
            checked: record.enabled,
            tooltip: {text: 'If a script engine is disabled, it cannot be used to run reports and scripts', title: 'Enable Engine'},
            listeners: {render: setFormFieldTooltip}
        };

        var itemExternal = {
            name: 'external',
            xtype: 'hidden',
            value: record.external
        };

        var itemKey = {
            name: 'key',
            xtype: 'hidden',
            value: record.key
        };

        var itemRemote = {
            name: 'remote',
            xtype: 'hidden',
            value: record.remote
        };

        var itemDocker = {
            name: 'docker',
            xtype: 'hidden',
            value: record.docker
        };
    
        // common items for both local and remote
        var panelItems = [
            itemName,
            itemLanguageName
        ];
        if (!record.docker)
            panelItems.push(itemLanguageVersion);

        panelItems.push(itemExtensions);

        if (!record.docker) {
            if (record.remote) {
                panelItems.push(itemMachine);
                panelItems.push(itemPort);
                panelItems.push(itemPathGrid);
                panelItems.push(itemUser);
                panelItems.push(itemPassword);
            }
            else {
                panelItems.push(itemPath);
            }
            panelItems.push(itemCmd);
        }
        // common items for both local and remote

        panelItems.push(itemOutputFileName);

        //add engine specific fields
        var engineItems = getEngineSpecificItems(record);
        if (engineItems && engineItems.length > 0) {
            panelItems = panelItems.concat(engineItems);
        }

        panelItems.push(itemEnabled);
        panelItems.push(itemExternal);
        panelItems.push(itemKey);
        panelItems.push(itemRemote);
        panelItems.push(itemDocker);

        var formPanel = new Ext.FormPanel({
            bodyStyle:'padding:5px 5px 0',
            defaultType: 'textfield',
            items: panelItems
        });

        var win = new Ext.Window({
            title: 'Edit Engine Configuration',
            layout:'form',
            border: false,
            width: record.remote ? 575 : 475,
            autoHeight : true,
            closeAction:'close',
            modal: false,
            items: formPanel,
            resizable: false,
            buttons: [{
                text: 'Submit',
                id: 'btn_submit',
                hidden: <%=!hasAdminOpsPerms%>,
                handler: function(){submitForm(win, formPanel, grid);}
            },{
                text: <%=q(!hasAdminOpsPerms ? "Close" : "Cancel")%>,
                id: 'btn_cancel',
                handler: function(){win.close();}
            }],
            bbar: [{ xtype: 'tbtext', text: '',id:'statusTxt' }]
        });

        win.show(button);
    }

    function submitForm(win, panel, grid)
    {
        // client side validation
        var form = panel.getForm();
        if (form && !form.isValid())
        {
                Ext.Msg.alert('Engine Definition', 'Not all fields have been properly completed');
            return false;
        }

        var values = form.getFieldValues();

        // Get the pathMap store data as an array of JSON objects of the form: {'localURI':'A', 'remoteURI':'B'}
        var pathMapItem = panel.items.get('editEngine_pathMap');
        if (pathMapItem)
        {
            var pathMapDatas = Ext.pluck(pathMapItem.store.data.items, 'data');
            values['pathMap'] = {
                paths: pathMapDatas
            };
        }

        win.getBottomToolbar().get(0).setText("");
        Ext.Ajax.request({
            url: LABKEY.ActionURL.buildURL("reports", "scriptEnginesSave"),
            method: 'POST',
            jsonData: values,
            success: function(resp, opt){
                var o = Ext.decode(resp.responseText);
                if (o.success)
                {
                    win.close();
                    grid.store.load();
                }
                else
                    handleAjaxError(o.errors, win);
            },
            failure: function(form, action){handleError(win, action);}
        })
    }

    function handleAjaxError(errors, win)
    {
        if (errors)
        {
            var errorText = '<span style="color:red; white-space:normal;">';
            for (var p in errors)
            {
                if (errors.hasOwnProperty(p))
                {
                    errorText += errors[p] + '\n';
                }
            }

            errorText += "</span>";
            win.getBottomToolbar().get(0).setText(errorText);
        }
    }

    function handleError(win, action)
    {
        var errorTxt = 'An error occurred saving the engine configuration.';

        if (action.failureType == Ext.form.Action.SERVER_INVALID)
        {
            errorTxt = 'An error occurred, move your mouse over the fields highlighted<br>in red to see detailed information.';
        }
        else if (action.failureType == Ext.form.Action.CONNECT_FAILURE)
        {
            var jsonResponse = Ext.util.JSON.decode(action.response.responseText);
            if (jsonResponse && jsonResponse.exception)
                errorTxt = jsonResponse.exception;
        }
        win.getBottomToolbar().get(0).setText(errorTxt);
    }

    Ext.onReady(function()
    {
        showEngines();
    });
</script>

<labkey:errors/>

<labkey:panel title="Scripting Engine Configurations">
    <p>
        A scripting engine enables the execution of scripting code in a report or a QC validation script.
        A scripting engine can be exposed as a <a href="https://scripting.dev.java.net/" target="_blank">Java 6 script engine implementation</a>,
        or as an external script engine. Java 6 script engine implementations are exposed by configuring the Java runtime the webserver is running
        against. External engine implementations are added in this view. For example, scripting languages like R and Perl
        can be configured here in order to create and run scripts and reports using these languages.
    </p>

    <div id="enginesGrid" class="extContainer"></div>
</labkey:panel>


