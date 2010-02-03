/*
 * Copyright (c) 2010 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// subclass the filebrowser panel

LABKEY.FilesWebPartPanel = Ext.extend(LABKEY.FileBrowser, {

    actionsConnection : new Ext.data.Connection({autoAbort:true}),

    // pipeline actions
    pipelineActions : undefined,
    importActions : undefined,

    // toolbar buttons
    toolbarButtons : [],
    toolbar : undefined,

    importDataEnabled : true,
    adminUser : false,

    constructor : function(config)
    {
        config.fileFilter = {test: function(record){
            if (!record.file)
                return record.name.indexOf('.') != 0;
            return true;
        }};
        LABKEY.FilesWebPartPanel.superclass.constructor.call(this, config);
    },

    initComponent : function()
    {
        LABKEY.FilesWebPartPanel.superclass.initComponent.call(this);

        this.on(BROWSER_EVENTS.directorychange,function(record){this.onDirectoryChange(record);}, this);
        this.grid.getSelectionModel().on(BROWSER_EVENTS.selectionchange,function(record){this.onSelectionChange(record);}, this);
    },

    /**
     * Initialize additional actions and components
     */
    initializeActions : function()
    {
        LABKEY.FilesWebPartPanel.superclass.initializeActions.call(this);

        this.actions.importData = new Ext.Action({
            text: 'Import Data',
            listeners: {click:function(button, event) {this.onImportData(button);}, scope:this},
            iconCls: 'iconDBCommit',
            tooltip: 'Import data from files into the database, or analyze data files'
        });

        this.actions.customize = new Ext.Action({
            text: 'Admin',
            iconCls: 'iconConfigure',
            tooltip: 'Configure the buttons shown on the toolbar',
            listeners: {click:function(button, event) {this.onAdmin(button);}, scope:this}
        });
    },

    /**
     * Override base class to add components to the file browser
     * @override
     */
    getItems : function()
    {
        var items = LABKEY.FilesWebPartPanel.superclass.getItems.call(this);

        return items;
    },

    createGrid : function()
    {
        // mild convolution to pass fileSystem to the _attachPreview function
        var iconRenderer = renderIcon.createDelegate(null,_attachPreview.createDelegate(this.fileSystem,[],true),true);
        var sm = new Ext.grid.CheckboxSelectionModel();

        var grid = new Ext.grid.GridPanel(
        {
            store: this.store,
            border:false,
            selModel : sm,
            loadMask:{msg:"Loading, please wait..."},
            columns: [
                sm,
                {header: "", width:20, dataIndex: 'iconHref', sortable: false, hidden:false, renderer:iconRenderer},
                {header: "Name", width: 250, dataIndex: 'name', sortable: true, hidden:false, renderer:Ext.util.Format.htmlEncode},
                {header: "Modified", width: 150, dataIndex: 'modified', sortable: true, hidden:false, renderer:renderDateTime},
                {header: "Size", width: 80, dataIndex: 'size', sortable: true, hidden:false, align:'right', renderer:renderFileSize},
                {header: "Usages", width: 150, dataIndex: 'actionHref', sortable: true, hidden:false, renderer:renderUsage},
                {header: "Created By", width: 150, dataIndex: 'createdBy', sortable: true, hidden:false, renderer:Ext.util.Format.htmlEncode}
            ]
        });
        // hack to get the file input field to size correctly
        grid.on('render', function(c){this.fileUploadField.setSize(350);}, this);
        grid.on('dblclick', function(e){this.renderFile(e);}, this);

        return grid;
    },

    onDirectoryChange : function(record)
    {
        var path = record.data.path;
        if (startsWith(path,"/"))
            path = path.substring(1);
        var requestid = this.actionsConnection.request({
            autoAbort:true,
            url:actionsURL + encodeURIComponent(path),
            method:'GET',
            disableCaching:false,
            success : this.updatePipelineActions,
            scope: this
        });
    },

    updatePipelineActions : function(response)
    {
        var o = eval('var $=' + response.responseText + ';$;');
        var actions = o.success ? o.actions : [];

        // check whether the import data button is enabled
        this.importDataEnabled = o.success ? o.importDataEnabled : false;
        this.adminUser = o.success ? o.adminUser : false;

        var toolbarActions = [];
        var importActions = [];

        if (actions && actions.length && this.canImportData())
        {
            for (var i=0; i < actions.length; i++)
            {
                var pUtil = new LABKEY.PipelineActionUtil(actions[i]);
                var links = pUtil.getLinks();

                if (!links) continue;

                for (var j=0; j < links.length; j++)
                {
                    var link = links[j];

                    if (link.display != 'disabled' && link.href)
                    {
                        link.handler = this.executePipelineAction;
                        link.scope = this;

                        // toolbar actions are always available in import data
                        if (link.display == 'toolbar')
                        {
                            this.addActionLink(toolbarActions, pUtil, link);
                            this.addActionLink(importActions, pUtil, link);
                        }
                        else
                            this.addActionLink(importActions, pUtil, link);
                    }
                }
            }
        }

        this.displayPipelineActions(toolbarActions, importActions)
    },

    /**
     * Helper to add pipeline action items to an object array
     */
    addActionLink : function(list, parent, link)
    {
        var action= list[parent.getText()];

        if (!action)
        {
            // create a new data object to hold this link
            action = new LABKEY.PipelineActionUtil({
                multiSelect: parent.multiSelect,
                description: parent.description,
                files: parent.getFiles(),
                links: {
                    id: parent.links.id,
                    text: parent.links.text,
                    href: parent.links.href
                }
            });
            action.clearLinks();
            list[parent.getText()] = action;
        }
        action.addLink(link);
    },

    displayPipelineActions : function(toolbarActions, importActions)
    {
        var toolbar = this.getTopToolbar();
        if (toolbar && toolbar.items)
        {
            for (var i=0; i < toolbar.items.getCount(); i++)
            {
                var o = toolbar.items.item(i);
                o.destroy();
            }
            toolbar.items.clear();
        }

        var tbarButtons = [];
        var importDataButtons = [];

        this.pipelineActions = [];
        this.importActions = [];

        // add any import data actions
        for (action in importActions)
        {
            var a = importActions[action];
            if ('object' == typeof a )
            {
                var importAction = new LABKEY.PipelineAction(a.getActionConfig());
                importDataButtons.push(importAction);

                this.importActions.push(importAction);
                this.pipelineActions.push(importAction);
            }
        }

        // add the standard buttons to the toolbar
        if (this.tbarItems && this.tbarItems.length)
        {
            for (var i=0; i < this.tbarItems.length; i++)
            {
                var item = this.tbarItems[i];
                if (typeof item == "string" && typeof this.actions[item] == "object")
                {
                    // don't add the import data button if there are no import data actions
                    if (item == 'importData' && !this.showImportData())
                    {
                        continue;
                    }
                    tbarButtons.push(new Ext.Button(this.actions[item]));
                    toolbar.addButton(this.actions[item]);
                }
                else
                    tbarButtons.push(item);
            }
        }

        // now add the configurable pipleline actions
        for (action in toolbarActions)
        {
            var a = toolbarActions[action];
            if ('object' == typeof a )
            {
                var tbarAction = new LABKEY.PipelineAction(a.getActionConfig());
                tbarButtons.push(new Ext.Button(tbarAction));

                toolbar.addButton(tbarAction);
                this.pipelineActions.push(tbarAction);
            }
        }
    },

    showImportData : function()
    {
        if (this.adminUser)
            return true;

        if (this.importDataEnabled && this.importActions.length)
            return true;

        return false;
    },

    // selection change handler
    onSelectionChange : function(record)
    {
        if (this.pipelineActions)
        {
            var selections = this.grid.selModel.getSelections();
            if (!selections.length && this.grid.store.data)
            {
                selections = this.grid.store.data.items;
            }

            if (selections.length)
            {
                var selectionMap = {};

                for (var i=0; i < selections.length; i++)
                    selectionMap[selections[i].data.name] = true;

                for (var i=0; i <this.pipelineActions.length; i++)
                {
                    var action = this.pipelineActions[i];
                    if (action.initialConfig.files && action.initialConfig.files.length)
                    {
                        var selectionCount = 0;
                        for (var j=0; j <action.initialConfig.files.length; j++)
                        {
                            if (action.initialConfig.files[j] in selectionMap)
                            {
                                selectionCount++;
                            }
                        }
                        if (action.initialConfig.multiSelect)
                        {
                            selectionCount > 0 ? action.enable() : action.disable();
                        }
                        else
                        {
                            selectionCount == 1 ? action.enable() : action.disable();
                        }
                    }
                }
            }
        }

        this.actions.importData.disable();
        if (this.importActions && this.importActions.length)
        {
            for (var i=0; i < this.importActions.length; i++)
            {
                var action = this.importActions[i];
                if (!action.isDisabled())
                    this.actions.importData.enable();
            }
        }
    },

    executePipelineAction : function(item, e)
    {
        var selections = this.grid.selModel.getSelections();
        var action = item.isAction ? item : new LABKEY.PipelineAction(item);

        // if there are no selections, treat as if all are selected
        if (selections.length == 0)
        {
            var selections = [];
            var store = this.grid.getStore();

            for (var i=0; i <store.getCount(); i++)
            {
                var record = store.getAt(i);
                if (record.data.file)
                    selections.push(record);
            }
        }

        if (action && action.initialConfig.href)
        {
            if (selections.length == 0)
            {
                Ext.Msg.alert("Rename Views", "There are no views selected");
                return false;
            }

            var form = document.createElement("form");
            form.setAttribute("method", "post");
            form.setAttribute("action", item.initialConfig.href);

            for (var i=0; i < selections.length; i++)
            {
                var files = action.getFiles();
                for (var j = 0; j < files.length; j++)
                {
                    if (files[j] == selections[i].data.name)
                    {
                        var fileField = document.createElement("input");
                        fileField.setAttribute("name", "file");
                        fileField.setAttribute("value", selections[i].data.name);
                        form.appendChild(fileField);
                        break;
                    }
                }
            }

            document.body.appendChild(form);    // Not entirely sure if this is necessary
            form.submit();
        }
    },

    onAdmin : function(btn)
    {
        var configDlg = new LABKEY.ActionsAdminPanel({path: this.currentDirectory.data.path});

        configDlg.on('success', function(c){this.onDirectoryChange(this.currentDirectory);}, this);
        configDlg.on('failure', function(){Ext.Msg.alert("Update Action Config", "Update Failed")});

        configDlg.show();
    },

    onImportData : function(btn)
    {
        var actionMap = [];
        var actions = [];
        var checked = true;
        var hasAdmin = false;

        for (var i=0; i < this.importActions.length; i++)
        {
            var pa = this.importActions[i];

            if (!pa.isDisabled())
            {
                var links = pa.getLinks();
                var fieldLabel = pa.getText();
                for (var j=0; j < links.length; j++)
                {
                    var link = links[j];
                    var label = link.text;

                    if (link.display == 'admin')
                    {
                        label = label.concat(' <span class="labkey-error">*</span>');
                        hasAdmin = true;
                    }

                    actionMap[link.id] = link;
                    actions.push({
                        //xtype: 'radio',
                        fieldLabel: fieldLabel,
                        checked: checked,
                        labelSeparator: '',
                        boxLabel: label,
                        name: 'importAction',
                        inputValue: link.id
                        //width: 250
                    });
                    fieldLabel = '';
                    checked = false;
                }
            }
        }
        var actionPanel = new Ext.form.FormPanel({
            bodyStyle : 'padding:10px;',
            labelWidth: 150,
            defaultType: 'radio',
            items: actions
        });

        var items = [];
        items.push(actionPanel);
        if (hasAdmin)
        {
            items.push({
                html: 'Actions marked with an asterisk <span class="labkey-error">*</span> are only visible to Administrators.',
                bodyStyle: 'padding:10px;',
                border: false});
        }

        if (!this.importDataEnabled)
        {
            items.push({
                html: 'This button has been disabled from the admin panel and is only visible to Administrators.',
                bodyStyle: 'padding:10px;',
                border: false});
        }

        var win = new Ext.Window({
            title: 'Import Data',
            width: 450,
            height: 300,
            cls: 'extContainer',
            autoScroll: true,
            closeAction:'close',
            modal: true,
            items: items,
            buttons: [{
                text: 'Import',
                id: 'btn_submit',
                listeners: {click:function(button, event) {
                    this.submitForm(actionPanel, actionMap);
                    win.close();
                }, scope:this}
            },{
                text: 'Cancel',
                id: 'btn_cancel',
                handler: function(){win.close();}
            }]
        });
        win.show();
    },

    submitForm : function (panel, actionMap)
    {
        // client side validation
        var form = panel.getForm();
        var selection = form.getValues();
        var action = actionMap[selection.importAction];

        if ('object' == typeof action)
        {
            var a = new LABKEY.PipelineAction(action);
            a.execute(a);
        }
    },

    canImportData : function()
    {
        for (var i=0; i < this.tbarItems.length; i++)
        {
            if (this.tbarItems[i] == 'importData')
                return true;
        }
        return false;
    },

    renderFile : function(e)
    {
        var selections = this.grid.selModel.getSelections();

        if (selections.length == 1)
        {
            var item = selections[0].data;

            if (item.file)
            {
                var params = [];

                params.push('name=' + encodeURIComponent(selections[0].data.name));
                params.push('baseUrl=' + encodeURIComponent(this.fileSystem.baseUrl));
                var renderFileURL = LABKEY.ActionURL.buildURL('filecontent', 'renderFile') + '?' + params.join('&');
                window.location = renderFileURL;
            }
        }
    }
});
