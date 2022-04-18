<%
/*
 * Copyright (c) 2018-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.docker.DockerService"%>
<%@ page import="org.labkey.api.files.FileContentService"%>
<%@ page import="org.labkey.api.premium.PremiumService" %>
<%@ page import="org.labkey.api.reports.ExternalScriptEngine" %>
<%@ page import="org.labkey.api.reports.ExternalScriptEngineDefinition" %>
<%@ page import="org.labkey.api.reports.report.ExternalScriptEngineReport" %>
<%@ page import="org.labkey.api.reports.report.RReport" %>
<%@ page import="org.labkey.api.reports.report.ScriptEngineReport" %>
<%@ page import="org.labkey.api.rstudio.RStudioService" %>
<%@ page import="org.labkey.api.security.permissions.AdminOperationsPermission" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.util.FileUtil" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
    }
%>
<%
    boolean isRemoteEnabled = PremiumService.get().isRemoteREnabled();
    boolean isRDockerAvailable = false;
    if (AppProps.getInstance().isExperimentalFeatureEnabled(RStudioService.R_DOCKER_SANDBOX))
    {
        DockerService ds = DockerService.get();
        if (null != ds)
            isRDockerAvailable = ds.isDockerEnabled();
    }
    boolean hasAdminOpsPerms = getContainer().hasPermission(getUser(), AdminOperationsPermission.class);

    boolean baseServerUrlSet = !AppProps.getInstance().getBaseServerUrl().contains("localhost");
%>
<style type="text/css">

    #enginesGrid .x4-grid-body {
        border-top-width: 0;
    }

    .readonlyField {
        opacity: 0.7;
    }

</style>

<script type="text/javascript" nonce="<%=getScriptNonce()%>">
    Ext4.QuickTips.init();
    var R_EXTENSIONS = 'R,r';
    var PERL_EXTENSIONS = 'pl';
    var R_ENGINE_NAME = 'R Scripting Engine';
    var REMOTE_R_ENGINE_NAME = 'Remote R Scripting Engine';
    var R_DOCKER_ENGINE_NAME = 'R Docker Scripting Engine';
    var defaultR, countR = 0;

    var DockerImageFields = {
        imageName: {label: 'Docker Image Name', defaultVal: 'labkey/rsandbox', description: "Enter the Docker image name to use for R. Default is &apos;labkey/rsandbox&apos;, which includes Rlabkey."},
        mount: {label: 'Home Directory', defaultVal: '/home/docker', description: "Enter the volume (image directory) inside the Docker container to mount as the docker R user&apos;s home directory. Default is &apos;/home/rdocker&apos;."},
        hostReadOnlyMount: {label: 'Mount (ro): host directory', description: "Additional mount: host directory (read-only). Optional read-only mount point"},
        containerReadOnlyMount: {label: 'Mount (ro): container directory', description: "Additional mount: container directory (read-only). Optional read-only mount point"},
        hostReadWriteMount: {label: 'Mount (rw): host directory', description: "Additional mount: host directory (read and write). Optional read/write mount point"},
        containerReadWriteMount: {label: 'Mount (rw): container directory', description: "Additional mount: container directory (read and write). Optional read/write mount point"},
        appArmorProfile: {label: 'AppArmor Profile', description: ''},
        extraENVs: {label: 'Extra Variables', description: 'Additional environment variables to be passed in when running a container. Usage example: &apos;USERID=1000,USER=rstudio&apos;, which will be converted to &apos;-e USERID=1000 -e USER=rstudio&apos; for docker run command. ' +
                    'A special variable &apos;DETACH=TRUE&apos; will force container to run in detached mode, with &apos;--detach&apos;'}
    };

    function renderNameColumn(value, p, record)
    {
        var txt, data = record.data;

        if (data.enabled)
            txt = '<b>' + value + '</b><br>';
        else
            txt = '<b><span class="labkey-disabled">' + value + '</span></b><br>';

        if (data.extensions === R_EXTENSIONS)
        {
            if (data.default)
                txt = txt.concat('<i style="font-weight: bold">default : true</i><br>');
            if (data.sandboxed)
                txt = txt.concat('<i>sandboxed : true</i><br>');
        }
        txt = txt.concat('<i>enabled : ' + data.enabled + '</i><br>');
        txt = txt.concat('<i>external : ' + data.external + '</i><br>');

        return txt;
    }

    function getEngineSpecificItems(record) {

        if (record.extensions === R_EXTENSIONS)
        {
            var items = [], isCurrentDefault = record.default && record.rowId;

            if (record.docker) {
                items = items.concat([{
                    name: 'pandocEnabled',
                    xtype: 'hidden',
                    value: true
                },{
                    name: 'docker',
                    xtype: 'hidden',
                    value: record.docker
                },{
                    name: 'dockerImageRowId',
                    xtype: 'hidden',
                    value: record.dockerImageRowId
                }]);

                var dockerConditionHtmlTpl = '<table>' +
                        '<tr>' +
                        '<td width="105"><label>' + '?LABEL?' + ':</label>' +
                        '</td>' +
                        '<td>' + '?CONTENT?' + '</td>' +
                        '</tr>' +
                        '</table>';

                var configuredHtml = '<span style="color:green;" class="fa fa-check-circle"></span>';
                var notConfiguredHtml = '<span style="color:red;" class="fa fa-times-circle"></span>\<a href="admin-customizeSite.view">site settings</a>';

                items = items.concat([{
                    xtype: 'box',
                    html: dockerConditionHtmlTpl.replace('?LABEL?', 'Base server URL (not localhost)').replace('?CONTENT?', <%=baseServerUrlSet%> ? configuredHtml : notConfiguredHtml)
                },{
                    xtype: 'box',
                    html: dockerConditionHtmlTpl.replace('?LABEL?', 'Docker').replace('?CONTENT?', '<a href="docker-configure.view">docker</a>')
                }]);

                var dockerConfig = record.dockerImageConfig ? JSON.parse(record.dockerImageConfig) : {};
                var imageFields = [];
                Ext4.iterate(DockerImageFields, function(key, val) {
                    var config = {
                        fieldLabel: val.label,
                        id: 'dockerimage-' + key,
                        name: 'dockerimage-' + key,
                        labelWidth: 200,
                        width: 500,
                        value: record.dockerImageConfig ? dockerConfig[key] : val.defaultVal
                    };
                    if (val.hidden)
                        config.hidden = true;
                    if (val.description) {
                        config.labelAttrTpl = " data-qtitle='" + val.label + "' data-qtip='" + val.description + "'";
                    }
                    imageFields.push(config);
                });
                items = items.concat(imageFields);
            }

            items.push({
                fieldLabel: 'Site Default',
                name: 'default',
                id: 'editEngine_default',
                labelAttrTpl: isCurrentDefault ? " data-qtitle='Default engine' data-qtip='This engine is set as site default. To change site default, select another engine to set as default.'"
                        : " data-qtitle='Set as default' data-qtip='Specify the default R engine to use'",
                xtype: 'checkbox',
                checked: record.default,
                readOnly: isCurrentDefault,
                readOnlyCls: 'readonlyField'
            },{
                fieldLabel: 'Sandboxed',
                name: 'sandboxed',
                id: 'editEngine_sandboxed',
                labelAttrTpl: " data-qtitle='Mark as sandboxed' data-qtip='Mark the R engine as sandboxed'",
                xtype: 'checkbox',
                checked: record.sandboxed
            });

            if (!record.docker) {
                items = items.concat(getNonSandboxRSpecificFields(record.pandocEnabled));
            }

            return items;
        }

        return null;
    }

    function getNonSandboxRSpecificFields(enabled) {
        return [{
            fieldLabel: 'Use pandoc & rmarkdown',
            name: 'pandocEnabled',
            id: 'editEngine_pandocEnabled',
            labelAttrTpl: " data-qtitle='Enable rmarkdown v2' data-qtip='Select this option if you have rmarkdown and pandoc installed. Please see knitr help documentation on labkey.org for more information.'",
            xtype: 'checkbox',
            checked: enabled
        }];
    }

    function showEngines()
    {
        // pre defined engine templates

        var rEngineItem = new Ext4.menu.Item({
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
                        'default': !defaultR,
                        external: true,
                        enabled: true,
                        remote: false,
                        languageName:'R',
                        type : <%=q(ExternalScriptEngineDefinition.Type.R.name())%>
                    };
                    if (countR > 0 && !defaultR) {
                        Ext4.Msg.confirm('Site default missing', "None of the existing R engine(s) has been set as 'Site Default'. A site default must be specified in order to add additional R engines. Continue?", function (btn, text) {
                            if (btn == 'yes')
                                editRecord(button, grid, record);
                        });
                    }
                    else
                        editRecord(button, grid, record);
                }
            }
        });

    <% if (isRemoteEnabled) { %>
        var rserveEngineItem = new Ext4.menu.Item({
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
                        'default': !defaultR,
                        external: true,
                        enabled: true,
                        remote : true,
                        languageName:'R',
                        type : <%=q(ExternalScriptEngineDefinition.Type.R.name())%>
                    };

                    record['pathMap'] = {
                        localIgnoreCase: <%= FileUtil.isCaseInsensitiveFileSystem() %>,
                        remoteIgnoreCase: false,
                        paths: [
                            {
                                localURI: <%=q(ScriptEngineReport.getDefaultTempRoot().toURI().toString())%>,
                                remoteURI: ''
                            }
                            <% if (null != FileContentService.get()) { %>
                            ,{
                                localURI: <%=q(FileContentService.get().getSiteDefaultRoot().toURI().toString())%>,
                                remoteURI: ''
                            }
                            <% } %>
                        ]
                    };
                    if (countR > 0 && !defaultR) {
                        Ext4.Msg.confirm('Site default missing', "None of the existing R engine(s) has been set as 'Site Default'. A site default must be specified in order to add additional R engines. Continue?", function (btn, text) {
                            if (btn == 'yes')
                                editRecord(button, grid, record);
                        });
                    }
                    else
                        editRecord(button, grid, record);
                }
            }
        });
    <% } %>

    <% if (isRDockerAvailable) { %>
        var rDockerEngineItem = new Ext4.menu.Item({
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
                        'default': !defaultR,
                        docker: true,
                        sandboxed: true,
                        remote: true,
                        languageName:'R',
                        type : <%=q(ExternalScriptEngineDefinition.Type.R.name())%>
                    };
                    if (countR > 0 && !defaultR) {
                        Ext4.Msg.confirm('Site default missing', "None of the existing R engine(s) has been set as 'Site Default'. A site default must be specified in order to add additional R engines.  Continue?", function (btn, text) {
                            if (btn == 'yes')
                                editRecord(button, grid, record);
                        });
                    }
                    else
                        editRecord(button, grid, record);

                }
            }
        });
    <% } %>
        var perlEngineItem = new Ext4.menu.Item({
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
                type : <%=q(ExternalScriptEngineDefinition.Type.Perl.name())%>,
                languageName:'Perl'});}}}
            );

        var store = new Ext4.data.Store({
            model: 'LABKEY.model.ScriptEngineModel',
            proxy: {
                type: 'ajax',
                url: LABKEY.ActionURL.buildURL("core", "scriptEnginesSummary", <%=q(getContainer().getPath())%>),
                reader: {
                    type: 'json',
                    root:'views'
                }
            },
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
                defaultR = null;
                countR = 0;
                for (var i in records) {
                    if (records[i].data) {
                        var data = records[i].data;
                        if (data.extensions == R_EXTENSIONS)  {
                            if (data.default)
                                defaultR = records[i].data;
                            countR++;
                        }

                        if (records[i].data.extensions == PERL_EXTENSIONS)
                            perlEngineItem.disable();
                    }
                }
            }},
            sorters: [{property:'languageName', direction:"ASC"},
                {property:'name', direction:"ASC"}]});

        var newMenu = new Ext4.menu.Menu({
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
                    type : <%=q(ExternalScriptEngineDefinition.Type.External.name())%>,
                    external: true});}}
            }] });

        var grid = new Ext4.grid.Panel({
            el:'enginesGrid',
            autoScroll:false,
            autoHeight:true,
            enableHdMenu: false,
            width:800,
            store: store,
            listeners: {
                itemdblclick: function(comp, record, item, index, e, eOpts) {
                    e.stopEvent();
                    editSelected(e, comp);
                }
            },
            columns:[
                {header:'Name', dataIndex:'name', minWidth: 180, renderer:renderNameColumn},
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
            forceFit: true,
            buttons: [
                {text:'Add', id: 'btn_addEngine', menu: newMenu, hidden: <%=!hasAdminOpsPerms%>},
                {text:'Delete', id: 'btn_deleteEngine', listeners:{click:function(button, event) {deleteSelected(grid);}}, hidden: <%=!hasAdminOpsPerms%>},
                {text:'Edit', id: 'btn_editEngine', listeners:{click:function(button, event) {editSelected(button, grid);}}, hidden: <%=!hasAdminOpsPerms%>},
                {text:'Done', id: 'btn_done', listeners:{click:function(button, event) {window.location = LABKEY.ActionURL.buildURL('admin', 'showAdmin');}}}
            ],
            buttonAlign:'left'
        });

        grid.render();
    }

    function deleteSelected(grid)
    {
        var selections = grid.getSelectionModel().getSelection();

        if (selections.length == 0)
        {
            Ext4.Msg.alert("Delete Engine Configuration", "There is no engine selected");
            return false;
        }

        var record = selections[0].data;
        var params = [];

        if (!record.external)
        {
            Ext4.Msg.alert("Delete Engine Configuration", "JVM script engines cannot be deleted but you can disable them.");
            return false;
        }

        if (record.default && countR > 1) {
            // deletion of site default engine is not allowed, unless there is only one engine present
            Ext4.Msg.alert("Delete Engine Configuration", "The site default R engine cannot be deleted. Please choose another engine as the site default prior to deleting this configuration.");
            return false;
        }

        params.push("rowId=" + record.rowId);
        params.push("extensions=" + record.extensions);

        Ext4.Msg.confirm('Delete Engine Configuration', "Are you sure you wish to delete the selected configuration: " + record.name + "?", function(btn, text) {
            if (btn == 'yes')
            {
                Ext4.Ajax.request({

                    url: LABKEY.ActionURL.buildURL("core", "scriptEnginesDelete") + '?' + params.join('&'),
                    method: "POST",
                    success: function(){grid.store.load();},
                    failure: function(){Ext4.Msg.alert("Delete Engine Configuration", "Deletion Failed");}
                });
            }
        });
    }

    function editSelected(button, grid)
    {
        var selections = grid.getSelectionModel().getSelection();
        if (selections.length == 0)
        {
            Ext4.Msg.alert("Edit Engine Configuration", "There is no engine selected");
            return false;
        }

        editRecord(button, grid, selections[0].data);
    }

    function editRecord(button, grid, record)
    {
        let itemPath = {
            fieldLabel: 'Program Path',
            name: 'exePath',
            id: 'editEngine_exePath',
            labelAttrTpl: " data-qtitle='Program Path' data-qtip='Specify the absolute path to the program including the program itself'",
            allowBlank: false,
            value: record.exePath,
            disabled: !record.external,
            width: 275
        };

        let itemMachine = {
            fieldLabel: 'Machine Name',
            name: 'machine',
            id: 'editEngine_machine',
            labelAttrTpl: " data-qtitle='Machine Name' data-qtip='Specify the machine name or IP address that Rserve is running on'",
            allowBlank: false,
            value: record.machine,
            disabled: !record.external,
            width: 275
        };

        let itemCmd = {
            fieldLabel: 'Program Command',
            name: 'exeCommand',
            id: 'editEngine_exeCommand',
            labelAttrTpl: " data-qtitle='Program Command' data-qtip='The command used when the program is invoked'",
            disabled: !record.external,
            value: record.exeCommand,
            width: 275
        };

        let itemPort = {
            fieldLabel: 'Port',
            name: 'port',
            id: 'editEngine_port',
            labelAttrTpl: " data-qtitle='Port' data-qtip='The port used to connect to Rserve'",
            disabled: !record.external,
            value: record.port,
            width: 275
        };

        let pathMapStore = new Ext4.data.JsonStore({
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
            pathMapStore.loadData(record.pathMap.paths);
        }

        let fieldDisplayRenderer = function(val){
            return Ext4.util.Format.htmlEncode(val);
        };

        let itemPathGridInput = new Ext4.grid.Panel({
            xtype: 'grid',
            name: 'pathMap',
            id: 'editEngine_pathMap',
            disabled: !record.external,
            stripeRows: true,
            autoEncode: true,
            enableColumnHide: false,
            store: pathMapStore,
            plugins: [
                Ext4.create('Ext.grid.plugin.CellEditing', {
                    clicksToEdit: 1
                })
            ],
            columns: [
                {id: 'localURI', header: 'Local', dataIndex: 'localURI', editable: true, editor: 'textfield', width: 200, renderer: fieldDisplayRenderer},
                {id: 'remoteURI', header: 'Remote', dataIndex: 'remoteURI', editable: true, editor: 'textfield', width: 200, renderer: fieldDisplayRenderer}
            ],
            tbar: [
                {
                    text: 'Add',
                    handler: function ()
                    {
                        var data = {'localURI': '', 'remoteURI': ''};
                        pathMapStore.add(data);
                    }
                },
                {
                    text: 'Remove',
                    handler: function (btn, evt)
                    {
                        var record = itemPathGridInput.getSelectionModel().getSelection();
                        pathMapStore.remove(record);
                    }
                }
            ],
            width: 430,
            height: 160,
            viewConfig: {forceFit: true}
        });

        let itemPathGrid = new Ext4.form.FieldContainer({
            fieldLabel: 'Path Mapping',
            id: 'editEngine_pathMapContainer',
            labelAttrTpl: " data-qtitle='Local to Remote Path Mapping' data-qtip='Add or remove local to remote path mappings'",
            items: [itemPathGridInput]
        });

        let itemChangePassword = {
            xtype       : 'checkbox',
            fieldLabel  : 'Change Password',
            id          : 'editEngine_changePassword',
            name        : 'changePassword',
            uncheckedValue : 'false',
            listeners : {
                scope: this,
                'change': function (cb, value){
                    cb.up('panel').down('textfield[name=user]').setDisabled(!value);
                    cb.up('panel').down('textfield[name=password]').setDisabled(!value);
                }
            }
        };

        let itemUser = {
            fieldLabel: 'Remote User',
            name: 'user',
            id: 'editEngine_user',
            labelAttrTpl: " data-qtitle='Remote User' data-qtip='The user for the remote service login'",
            disabled: true,
            value: record.user,
            width: 275
        };

        let itemPassword = {
            fieldLabel: 'Remote Password',
            name: 'password',
            id: 'editEngine_password',
            labelAttrTpl: " data-qtitle='Remote Password' data-qtip='The password for the remote service login account'",
            inputType: 'password',
            disabled: true,
            width: 275
        };

        let itemName = {
            fieldLabel: "Name",
            name: 'name',
            id: 'editEngine_name',
            allowBlank: false,
            readOnly: !record.external,
            value: record.name
        };

        let itemLanguageName = {
            fieldLabel: 'Language',
            name: 'languageName',
            id: 'editEngine_languageName',
            allowBlank: false,
            readOnly: !record.external || record.docker,
            value: record.languageName
        };

        let itemLanguageVersion = {
            fieldLabel: 'Language Version',
            name: 'languageVersion',
            id: 'editEngine_languageVersion',
            readOnly: !record.external,
            value: record.languageVersion
        };

        let itemExtensions = {
            fieldLabel: 'File Extensions',
            name: 'extensions',
            id: 'editEngine_extensions',
            allowBlank: false,
            labelAttrTpl: " data-qtitle='File Extensions' data-qtip='The list of file extensions (separated by commas) that this engine is associated with'",
            readOnly: !record.external || record.docker,
            value: record.extensions
        };

        let itemOutputFileName = {
            fieldLabel: 'Output File Name',
            name: 'outputFileName',
            id: 'editEngine_outputFileName',
            value: record.outputFileName,
            labelAttrTpl: " data-qtitle='Output File Name' data-qtip='If the console output is written to a file, the name should be specified here. The substitution syntax \\${scriptName} will be replaced with the name (minus the extension) of the script being executed.'",
            disabled: !record.external,
            readOnly:  record.docker
        };

        let itemEnabled = {
            fieldLabel: 'Enabled',
            name: 'enabled',
            id: 'editEngine_enabled',
            labelAttrTpl: " data-qtitle='Enable Engine' data-qtip='If a script engine is disabled, it cannot be used to run reports and scripts'",
            xtype: 'checkbox',
            checked: record.enabled
        };

        let itemExternal = {
            name: 'external',
            xtype: 'hidden',
            value: record.external
        };

        let itemKey = {
            name: 'rowId',
            xtype: 'hidden',
            value: record.rowId
        };

        let itemType = {
            name: 'type',
            xtype: 'hidden',
            value: record.type
        };

        let itemRemote = {
            name: 'remote',
            xtype: 'hidden',
            value: record.remote
        };

        // common items for both local and remote
        let panelItems = [
            itemName,
            itemLanguageName
        ];
        panelItems.push(itemLanguageVersion);

        panelItems.push(itemExtensions);

        if (!record.docker) {
            if (record.remote) {
                panelItems.push(itemMachine);
                panelItems.push(itemPort);
                panelItems.push(itemPathGrid);
                panelItems.push(itemChangePassword);
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
        let engineItems = getEngineSpecificItems(record);
        if (engineItems && engineItems.length > 0) {
            panelItems = panelItems.concat(engineItems);
        }

        panelItems.push(itemEnabled);
        panelItems.push(itemExternal);
        panelItems.push(itemKey);
        panelItems.push(itemType);
        panelItems.push(itemRemote);

        let formPanel = new Ext4.form.Panel({
            bodyStyle:'padding:5px 5px 0',
            defaultType: 'textfield',
            items: panelItems
        });

        let win = new Ext4.Window({
            title: 'Edit Engine Configuration',
            layout:'form',
            border: false,
            width: record.remote ? 575 : 475,
            autoHeight : true,
            closeAction:'destroy',
            modal: true,
            items: formPanel,
            resizable: false,
            buttons: [{
                text: 'Submit',
                id: 'btn_submit',
                hidden: <%=!hasAdminOpsPerms%>,
                disabled: record.docker && <%=(!baseServerUrlSet)%>,
                handler: function(){submitForm(win, formPanel, grid);}
            },{
                text: <%=q(!hasAdminOpsPerms ? "Close" : "Cancel")%>,
                id: 'btn_cancel',
                handler: function(){win.close();}
            }]
        });

        win.show(button);
    }

    function submitForm(win, panel, grid)
    {
        // client side validation
        var form = panel.getForm();
        if (form && !form.isValid())
        {
            Ext4.Msg.alert('Engine Definition', 'Not all fields have been properly completed');
            return false;
        }

        var values = form.getFieldValues();

        // confirm site default R engine modification
        if (values.extensions  === R_EXTENSIONS) {
            var rowId = values.rowId ? parseInt(values.rowId) : -1;
            if (values.default && !values.enabled) {
                Ext4.Msg.alert("Engine Definition", "Site default engine must be enabled.");
                return false;
            }

            if (!values.default && (defaultR && (rowId === defaultR.rowId))) {
                Ext4.Msg.alert("Engine Definition", "This engine is used as the site default. To change the site default, select another engine to use as the default.");
                return false;
            }

            var confirmChange;
            if (defaultR) {
                if (values.default && rowId !== defaultR.rowId) {
                    confirmChange = "Are you sure you want to set '" + values.name + "' as the default site wide R engine? The current default is '" + defaultR.name + "'."
                }
            }
            else if (!values.default) {
                Ext4.Msg.alert('Engine Definition', 'Site default R engine missing. You must specify one R engine to be the site default.');
                return false;
            }
            if (confirmChange && !confirm(confirmChange))
                return;
        }

        // Get the pathMap store data as an array of JSON objects of the form: {'localURI':'A', 'remoteURI':'B'}
        if (panel.items.get('editEngine_pathMapContainer')) {
            var pathMapItem = panel.items.get('editEngine_pathMapContainer').items.get('editEngine_pathMap');
            if (pathMapItem)
            {
                var pathMapDatas = Ext4.pluck(pathMapItem.store.data.items, 'data');
                values['pathMap'] = {
                    paths: pathMapDatas
                };
            }
        }

        if (values.docker) {
            var imageConfig = {};
            Ext4.iterate(DockerImageFields, function(key, val) {
                var field = panel.items.get('dockerimage-' + key);
                if (field) {
                    imageConfig[key] = field.getValue();
                }
            });
            values['dockerImageConfig'] = JSON.stringify(imageConfig);
        }

        Ext4.Ajax.request({
            url: LABKEY.ActionURL.buildURL("core", "scriptEnginesSave"),
            method: 'POST',
            jsonData: values,
            success: function(resp, opt){
                var o = Ext4.decode(resp.responseText);
                if (o.success)
                {
                    win.close();
                    grid.store.load();
                }
                else
                    handleFailure(resp, opt);
            },
            failure: handleFailure
        })
    }

    function handleFailure(resp, opt) {
        var jsonResp = Ext4.decode(resp.responseText);
        if (jsonResp) {
            var errorHTML = '';
            if (jsonResp.errors) {
                for (let i = 0; i < jsonResp.errors.length; i++) {
                    let error = jsonResp.errors[i];
                    if (error.msg) {
                        errorHTML += error.msg + '\n';
                    }
                }
            }
            else if (jsonResp.exception) {
                errorHTML = jsonResp.exception;
            }
            Ext4.Msg.alert('Error', errorHTML);
        }
        else {
            LABKEY.Utils.displayAjaxErrorResponse(resp, opt);
        }
    }

    Ext4.onReady(function()
    {
        Ext4.define('LABKEY.model.ScriptEngineModel', {
            extend: 'Ext.data.Model',
            fields: [
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
                {name:'rowId'},
                {name:'type'},
                {name:'default', type:'boolean'},
                {name:'sandboxed', type:'boolean'},
                {name:'enabled', type:'boolean'},
                {name:'external', type:'boolean'},
                {name:'remote', type:'boolean'},
                {name:'docker', type:'boolean'},
                {name:'dockerImageRowId'},
                {name:'dockerImageConfig'},
                {name:'outputFileName'},
                {name:'pandocEnabled', type:'boolean'}
            ]
        });
        showEngines();
    });
</script>

<labkey:errors/>

<labkey:panel title="Scripting Engine Configurations">
    <%=getTroubleshooterWarning(hasAdminOpsPerms, HtmlString.unsafe("<br>"))%>
    <p>
        A scripting engine enables the execution of scripting code on the server, for example, in a report or a QC validation script.
        Scripting languages like JavaScript, R, and Perl can be configured below.
        For details see <%=helpLink("configureScripting", "Configure Scripting Engines")%>.
    </p>

    <div id="enginesGrid" class="extContainer"></div>
</labkey:panel>


