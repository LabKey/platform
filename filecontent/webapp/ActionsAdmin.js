/*
 * Copyright (c) 2010 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

/**
 * Class to display an action configuration dialog box. Will fire the following events:
 *
 * success : if the update of the actions succeeded.
 * failure : if the action update failed.
 * runSelected : run the selected action, the data record is passed as a parameter
 *
 */
LABKEY.requiresScript("Reorderer.js");
LABKEY.requiresScript("ToolbarDroppable.js");
LABKEY.requiresScript("ToolbarReorderer.js");

LABKEY.ActionsCheckColumn = Ext.extend(Ext.grid.CheckColumn,{

    constructor : function(config)
    {
        Ext.grid.CheckColumn.prototype.constructor.call(this, config);
    },

    onMouseDown : function(e, t)
    {
        if(t.className && t.className.indexOf('x-grid3-cc-'+this.id) != -1)
        {
            if (this.listeners && this.listeners.mousedown)
            {
                var index = this.grid.getView().findRowIndex(t);
                var record = this.grid.store.getAt(index);

                LABKEY.ActionsCheckColumn.superclass.onMouseDown.call(this, e, t);
                if (record)
                {
                    var scope = this.listeners.mousedown.scope || this;
                    this.listeners.mousedown.apply(scope, [record, this.dataIndex]);
                }
            }
            else
                LABKEY.ActionsCheckColumn.superclass.onMouseDown.call(this, e, t);
        }
    }
}),


LABKEY.ActionsAdminPanel = Ext.extend(Ext.util.Observable, {

    actionsURL : LABKEY.ActionURL.buildURL('pipeline', 'actions', null, {allActions:true}),
    actionsUpdateURL : LABKEY.ActionURL.buildURL('pipeline', 'updatePipelineActionConfig'),
    actionsConfigURL : LABKEY.ActionURL.buildURL('pipeline', 'getPipelineActionConfig'),

    importDataEnabled : true,
    fileConfig : undefined,
    filePropertiesPanel : undefined,
    filePropsGrid : undefined,

    isPipelineRoot : false,
    
    events : {},
    actionConfig : {},

    tbarItemsConfig : [],   // array of config options for each standard toolbar button
    actions: {},            // map of action names to actions of all actions available
    newActions: {},         // map of newly customized actions

    constructor : function(config)
    {
        Ext.apply(this, config);
        Ext.util.Observable.prototype.constructor.call(this, config);

        if (config.path)
        {
            config.path;
            if (startsWith(config.path,"/"))
                config.path = config.path.substring(1);

            this.actionsURL = LABKEY.ActionURL.buildURL('pipeline', 'actions', null, {allActions:true, path:config.path});
        }
    },

    show : function(btn)
    {
        if (this.isPipelineRoot)
        {
            Ext.Ajax.request({
                autoAbort:true,
                url:this.actionsConfigURL,
                method:'GET',
                disableCaching:false,
                success : this.getActionConfiguration,
                scope: this
            });
        }
        else
        {
            var win = new Ext.Window({
                title: 'Manage File Browser Configuration',
                border: false,
                width: 400,
                height: 250,
                cls: 'extContainer',
                autoScroll: true,
                closeAction:'close',
                modal: true,
                items: [{
                    xtype: 'panel',
                    bodyStyle : 'padding: 30 10px;',
                    html: 'There is a Pipeline Override for this folder and actions are not available for the ' +
                          'default file location.<br/><br/>Customize this web part to use the pipeline location using the ' +
                          'customize web part button <img src="' + LABKEY.contextPath + '/_images/partedit.gif"/>'
                }],
                buttons: [{
                    text: 'Close',
                    id: 'btn_cancel',
                    handler: function(){win.close();}
                }]
            });
            win.show();

        }
    },

    // parse the configuration information
    getActionConfiguration : function(response)
    {
        var o = eval('var $=' + response.responseText + ';$;');
        var config = o.success ? o.config : {};

        // check whether the import data button is enabled
        this.importDataEnabled = config.importDataEnabled ? config.importDataEnabled : false;
        this.fileConfig = config.fileConfig ? config.fileConfig : 'useDefault';

        if ('object' == typeof config.actions)
        {
            for (var i=0; i < config.actions.length; i++)
            {
                var action = config.actions[i];
                this.actionConfig[action.id] = new LABKEY.PipelineActionConfig(action);
            }
        }

        Ext.Ajax.request({
            url: LABKEY.ActionURL.buildURL("filecontent", "getDefaultEmailPref"),
            method:'GET',
            disableCaching:false,
            success : this.getEmailConfiguration,
            failure: LABKEY.Utils.displayAjaxErrorResponse,
            updateSelection: true,
            scope: this
        });
    },

    getEmailConfiguration : function(response)
    {
        var o = eval('var $=' + response.responseText + ';$;');
        this.emailPref = o.success ? o.emailPref : 0;

        Ext.Ajax.request({
            autoAbort:true,
            url:this.actionsURL,
            method:'GET',
            disableCaching:false,
            success : this.getPipelineActions,
            scope: this
        });
    },

    // parse the response and create the data object

    getPipelineActions : function(response)
    {
        var o = eval('var $=' + response.responseText + ';$;');
        var actions = o.success ? o.actions : [];

        // parse the reponse and create the data object
        var data = {actions: []};
        if (actions && actions.length)
        {
            for (var i=0; i < actions.length; i++)
            {
                var pUtil = new LABKEY.PipelineActionUtil(actions[i]);
                var links = pUtil.getLinks();

                if (!links) continue;

                var config = this.actionConfig[pUtil.getId()];
                for (var j=0; j < links.length; j++)
                {
                    var link = links[j];

                    if (link.href)
                    {
                        var display = 'enabled';
                        if (config)
                        {
                            var linkConfig = config.getLink(link.id);
                            if (linkConfig)
                                display = linkConfig.display;
                        }

                        data.actions.push({
                            type: pUtil.getText(),
                            id: link.id,
                            actionId : pUtil.getId(),
                            display: display,
                            action: link.text,
                            href: link.href,
                            enabled: (display == 'enabled') || (display == 'toolbar'),
                            showOnToolbar: display == 'toolbar'
                        });
                    }
                }
            }
        }
        this.renderDialog(data);
    },

    renderDialog : function(data)
    {
        var actionPanel = this.createActionsPropertiesPanel(data);
        var filePanel = this.createFilePropertiesPanel();
        var toolbarPanel = this.createToolbarPanel();
        var emailPanel = this.createEmailPanel();

        var tabPanel = new Ext.TabPanel({
            activeTab: 'actionTab',
            stateful: false,
            items: [
                actionPanel,
                filePanel,
                toolbarPanel,
                emailPanel
            ]
        });
        tabPanel.on('tabchange', function(tp, panel){
            this.resetToolbarBtn.setVisible(panel.getId() == 'toolbarTab');}, this);

        this.resetToolbarBtn = new Ext.Button({
            text: 'Reset to Default',
            id: 'btn_reset',
            tooltip: 'Reset toolbar buttons to the default.',
            scope: this,
            hidden: true,
            handler: function(b, e){
                Ext.MessageBox.confirm("Confirm reset", 'All toolbar button customizations on this page will be deleted, continue?', function(answer)
                {
                    if (answer == "yes")
                    {
                        Ext.Ajax.request({
                            url: LABKEY.ActionURL.buildURL('filecontent', 'resetFilesToolbarOptions'),
                            method:'POST',
                            disableCaching:false,
                            success : function(){
                                b.initialConfig.scope.fireEvent('success');
                                win.close()},
                            failure: LABKEY.Utils.displayAjaxErrorResponse,
                            scope: this
                        });
                    }
                });
            }
        });

        var win = new Ext.Window({
            title: 'Manage File Browser Configuration',
            width: 600,
            height: 500,
            cls: 'extContainer',
            autoScroll: true,
            closeAction:'close',
            modal: true,
            layout: 'fit',
            items: [tabPanel],
            buttons: [{
                text: 'Submit',
                id: 'btn_submit',
                listeners: {click:function(button, event) {
                    this.saveActionConfig(button, event);
                    win.close();}, scope:this}
            },{
                text: 'Cancel',
                id: 'btn_cancel',
                handler: function(){win.close();}
            }, this.resetToolbarBtn]
        });
        win.show();
    },

    /**
     * Creates the actions properties tab
     */
    createActionsPropertiesPanel : function(data)
    {
        this.actionsStore = new Ext.data.GroupingStore({
            reader: new Ext.data.JsonReader({root:'actions',id:'id'},
                    [
                        {name: 'type'},
                        {name: 'action'},
                        {name: 'actionId'},
                        {name: 'description'},
                        {name: 'display'},
                        {name: 'enabled', type: 'boolean'},
                        {name: 'showOnToolbar', type: 'boolean'}]),
            data: data,
            sortInfo: {field:'type', direction:"ASC"},
            groupField:'type',
            autoLoad: true});

        var enabledColumn = new LABKEY.ActionsCheckColumn({
            header: 'Enabled', dataIndex: 'enabled',
            listeners: {mousedown:function(record, index)
            {
                if (!record.data['enabled'])
                    record.set('showOnToolbar', false);
            }, scope:this}
        });
        var onToolbarColumn = new LABKEY.ActionsCheckColumn({
            header: 'Show on Toolbar', dataIndex: 'showOnToolbar', width: 175,
            listeners: {mousedown:function(record, index)
            {
                if (!record.data['enabled'])
                    record.set('enabled', true);
            }, scope:this}
        });

         var cm = new Ext.grid.ColumnModel({
                // specify any defaults for each column
                columns: [
                    {header:'Type', dataIndex:'type'},
                    {header:'Action', dataIndex:'action', width:300},
                    enabledColumn,
                    onToolbarColumn
                ]
            });

        var selModel = new Ext.grid.CheckboxSelectionModel({moveEditorOnEnter: false});
        var grid = new Ext.grid.EditorGridPanel({
            loadMask:{msg:"Loading, please wait..."},
            store: this.actionsStore,
            selModel: selModel,
            stripeRows: true,
            clicksToEdit: 1,
            flex: 1,
            cm: cm,
            plugins: [enabledColumn, onToolbarColumn],
            view: new Ext.grid.GroupingView({
                startCollapsed:false,
                cls: 'extContainer',
                hideGroupedColumn:true,
                forceFit:true,
                groupTextTpl: '{values.group}'
            })
        });

        var actionPanel = new Ext.Panel({
            id: 'actionTab',
            title: 'Actions',
            bodyStyle : 'padding:10px;',
            layout: 'vbox',
            layoutConfig: {
                align: 'stretch',
                pack: 'start'
            },
            items: [
                new Ext.Panel({
                    border: false,
                    height: 60,
                    bodyStyle : 'padding:10 10 10 0px;',
                    items:{
                        xtype: 'checkbox',
                        checked: this.importDataEnabled,
                        labelSeparator: '',
                        boxLabel: 'Show Import Data<br/><i>Administrators will always see this button</i>)',
                        name: 'importAction',
                        listeners: {check: function(button, checked) {this.importDataEnabled = checked;}, scope:this}
                    }
                }),
                grid
            ]
        });

        return actionPanel;
    },

    /**
     * Creates the file properties tab
     */
    createFilePropertiesPanel : function()
    {
        var store = new Ext.data.Store({
            reader: new Ext.data.JsonReader({
                root:'fileProperties',
                fields: [
                    {name:'name'},
                    {name:'label'},
                    {name:'rangeURI'}]}),
            baseParams: {fileConfig: this.fileConfig},
            proxy: new Ext.data.HttpProxy({
                url: LABKEY.ActionURL.buildURL("pipeline", "getPipelineFileProperties"),
                method: 'GET'}),
            autoLoad: true});

        this.filePropsGrid = new Ext.grid.GridPanel({
            loadMask:{msg:"Loading, please wait..."},
            store: store,
            flex: 1,
            stripeRows: true,
            view: new Ext.grid.GridView({
                cls: 'extContainer',
                forceFit:true
            }),
            columns: [
                {header:'Name', dataIndex:'name', width:100},
                {header:'Label', dataIndex:'label'},
                {header:'Type', dataIndex:'rangeURI'}
            ]
        });

        this.editBtn = new Ext.Button({
            text: 'Edit Properties...',
            disabled: this.fileConfig != 'useCustom',
            listeners:{click:function(button, event){
                this.onEditFileProperties(button, event);
            }, scope:this}
        });

        this.filePropertiesPanel = new Ext.form.FormPanel({
            border: false,
            height: 150,
            items: [
                new Ext.form.RadioGroup({
                    xtype: 'radio',
                    itemCls: 'x-check-group',
                    columns: 1,
                    labelSeparator: '',
                    items: [
                        {boxLabel:'Use Default File Properties', name: 'fileOption', inputValue: 'useDefault', checked: this.fileConfig == 'useDefault'},
                        {boxLabel:'Use Same Settings as Parent', name: 'fileOption', inputValue: 'useParent', checked: this.fileConfig == 'useParent'},
                        {boxLabel:'Use Custom File Properties',
                            name: 'fileOption',
                            inputValue: 'useCustom',
                            checked: this.fileConfig == 'useCustom'
                        }
                    ],
                    listeners: {change:function(group, btn) {
                        this.onFilePropConfigChanged(group, btn);
                    }, scope:this}
                }),
                this.editBtn
            ]
        });

        var filePanel = new Ext.Panel({
            id: 'fileTab',
            title: 'File Properties',
            layout: 'vbox',
            layoutConfig: {
                align: 'stretch',
                pack: 'start'
            },
            bodyStyle : 'padding:10px;',
            items:[
                    {html: 'Define additional properties to be collected with each file:', border: false, height: 15},
                    this.filePropertiesPanel,
                    {html: 'Current Properties:', border: false, height: 15},
                    this.filePropsGrid]
        });

        return filePanel;
    },

    /**
     * Create the toolbar configuration panel
     */
    createToolbarPanel : function()
    {
        var buttons = [];
        if (this.tbarItemsConfig && this.tbarItemsConfig.length)
        {
            for (var i=0; i < this.tbarItemsConfig.length; i++)
            {
                var cfg = this.tbarItemsConfig[i];
                if (typeof this.actions[cfg.id] == "object")
                {
                    var action = this.actions[cfg.id];
                    var newAction = this.createToolbarAction(cfg.id, action.initialConfig);
                    this.newActions[cfg.id] = newAction;
                    buttons.push(newAction);
                }
            }
        }

        this.toolbar = new Ext.Toolbar({
            plugins: [
                new LABKEY.ext.ux.ToolbarDroppable({
                    canDrop: function(data) {
                        if (data.dragData.srcComponent && data.dragData.srcComponent.initialConfig)
                        {
                            var config = data.dragData.srcComponent.initialConfig;

                            return (!(config.actionId in this.toolbar.scope.newActions));
                        }
                        return false;
                    },

                    createItem: function(data) {
                        if (data.srcComponent && data.srcComponent.initialConfig)
                        {
                            var config = data.srcComponent.initialConfig;
                            var action = this.toolbar.scope.createToolbarAction(config.actionId, config);
                            this.toolbar.scope.newActions[config.actionId] = action;

                            return new Ext.Button(action);
                        }
                    }
                }),
                new LABKEY.ext.ux.ToolbarReorderer({defaultReorderable: false})
            ],
            items: buttons,
            enableOverflow: true,
            scope: this,
            border: true
        });

        var actions = [];
        for (var a in this.actions)
        {
            var action = this.actions[a];

            if (action && ('object' == typeof action))
            {
                var config = Ext.applyIf({xtype:'button', disabled:false, actionId:a}, action.initialConfig);

                config.handler = undefined;
                config.listeners = undefined;
                config.text = config.prevText;
                config.iconCls = config.prevIconCls;

                actions.push(config);
            }
        }

        var panel = new Ext.Panel({
            border: false,
            flex: 1,
            layout: 'table',
            bodyStyle:'padding:30px',
            tbar: this.toolbar,
            layoutConfig: {columns:4},
            items: [actions]
        });

        panel.on('render', function(v) {
            panel.dragZone = new Ext.dd.DragZone(v.getEl(), {

                getDragData: function(e) {
                    var sourceEl = e.getTarget('table.x-btn', 10, true);

                    if (sourceEl) {
                        d = sourceEl.dom.cloneNode(true);
                        d.id = Ext.id();
                        var comp = v.findById(sourceEl.id);

                        if (comp)
                        {
                            return {
                                ddel: d,
                                sourceEl: sourceEl,
                                repairXY: Ext.fly(sourceEl).getXY(),
                                srcComponent: comp
                            };
                        }
                    }
                },

                getRepairXY: function() {
                    return this.dragData.repairXY;
                }
            });
        });

        var toolbarPanel = new Ext.Panel({
            id: 'toolbarTab',
            title: 'Toolbar',
            bodyStyle : 'padding:10px;',
            layout: 'vbox',
            layoutConfig: {
                align: 'stretch',
                pack: 'start'
            },
            items: [
                {html: '<span class="labkey-strong">Configure Toolbar</span></br>Drag the buttons on the toolbar to customize the button order. ' +
                       'Buttons can be added by dragging from the list of available buttons below and dropping them on the toolbar. Buttons can be removed ' +
                       'by clicking on the toolbar button and selecting "remove" from the dropdown menu.', border: false, height: 70},
                panel
            ]
//                    bodyStyle : 'padding:10 10 10 0px;',
        });

        return toolbarPanel;
    },

    /**
     * Create the toolbar action for the configurable toolbar
     */
    createToolbarAction : function(actionId, cfg, defaultCfg)
    {
        var config = Ext.applyIf({disabled:false, actionId:actionId, reorderable:true}, cfg);

        config.handler = undefined;
        config.listeners = undefined;

        var items = [
            {text:'show/hide icon', scope:this, actionId: actionId, handler: function(b){
                var action = this.newActions[b.initialConfig.actionId];
                if (action && ('object' == typeof action))
                {
                    if (action.getIconClass())
                    {
                        action.setIconClass(undefined);
                        action.initialConfig.hideIcon = true;
                    }
                    else
                    {
                        action.setIconClass(action.initialConfig.prevIconCls);
                        action.initialConfig.hideIcon = false;
                    }
                }
            }},
            {text:'show/hide text', scope:this, actionId: actionId, handler: function(b){
                var action = this.newActions[b.initialConfig.actionId];
                if (action && ('object' == typeof action))
                {
                    if (action.getText())
                    {
                        action.setText(undefined);
                        action.initialConfig.hideText = true;
                    }
                    else
                    {
                        action.setText(action.initialConfig.prevText);
                        action.initialConfig.hideText = false;
                    }
                }
            }}];

        // prevent removal of admin button
        if (actionId != 'customize')
        {
            items.push({
                text:'remove', scope: this, actionId: actionId, handler: function(b){
                    var action = this.newActions[b.initialConfig.actionId];
                    if (action && ('object' == typeof action))
                    {
                        action.each(function(item, idx, all){item.destroy();}, this);
                        delete this.newActions[b.initialConfig.actionId];
                    }
            }});
        }

        config.menu = {cls: 'extContainer', items: items};
        return new Ext.Action(config);
    },

    /**
     * Add the email preferences configuration panel
     */
    createEmailPanel : function()
    {
        var radioItems = [];
        radioItems.push({xtype: 'radio',
            checked: this.emailPref == 0,
            handler: this.onEmailPrefChanged,
            scope: this,
            boxLabel: "<span class='labkey-strong'>None</span> - don't send any email for file changes in this folder.",
            name: 'emailPref', inputValue: 0});
        radioItems.push({xtype: 'radio',
            checked: this.emailPref == 1,
            handler: this.onEmailPrefChanged,
            scope: this,
            boxLabel: '<span class="labkey-strong">Individual</span> - send a separate email for files changes.',
            name: 'emailPref', inputValue: 1});
        radioItems.push({xtype: 'radio',
            checked: this.emailPref == 2,
            handler: this.onEmailPrefChanged,
            scope: this,
            disabled: true,
            boxLabel: '<span class="labkey-strong">Daily Digest</span> - send one email each day that summarizes file changes in this folder.',
            name: 'emailPref', inputValue: 2});

        var radioGroup = new Ext.form.RadioGroup({
            xtype: 'radiogroup',
            //fieldLabel: 'Email Notification Settings',
            itemCls: 'x-check-group',
            columns: 1,
            labelSeparator: '',
            items: radioItems
        });

        var panel = new Ext.form.FormPanel({
            bodyStyle : 'padding:10px;',
            labelWidth: 10,
            height: 150,
            border: false,
            defaultType: 'radio',
            items: radioGroup
        });

        var emailPanel = new Ext.Panel({
            id: 'emailTab',
            title: 'Email Admin',
            bodyStyle : 'padding:10px;',
            layout: 'vbox',
            layoutConfig: {
                align: 'stretch',
                pack: 'start'
            },
            items: [
                panel
            ]
        });

        return emailPanel;
    },

    onEmailPrefChanged : function(cb, checked)
    {
        if (checked)
            this.emailPref = cb.initialConfig.inputValue;
    },

    onFilePropConfigChanged : function(group, rb)
    {
        this.fileConfig = rb.getGroupValue();
        this.editBtn.setDisabled(this.fileConfig != 'useCustom');

        this.filePropsGrid.getStore().load({params:{fileConfig: this.fileConfig}});
    },

    onEditFileProperties : function(btn, evt)
    {
        //this.saveActionConfig(btn, evt);
        window.location = LABKEY.ActionURL.buildURL('fileContent', 'designer');
    },

    /**
     * Save changes to the manage action dialog
     */
    saveActionConfig : function(button, event)
    {
        var adminOptions = {actions: []};
        var records = this.actionsStore.getModifiedRecords();

        // pipeline action configuration
        if (records && records.length)
        {
            var actionConfig = {};

            for (var i=0; i <records.length; i++)
            {
                var record = records[i];
                var display;

                if (record.data.showOnToolbar)
                    display = 'toolbar';
                else if (record.data.enabled)
                    display = 'enabled';
                else
                    display = 'disabled';

                var config = actionConfig[record.data.actionId];
                if (!config)
                {
                    config = new LABKEY.PipelineActionConfig({id: record.data.actionId, display: 'enabled', label: record.data.type});
                    actionConfig[record.data.actionId] = config;
                }
                config.addLink(record.id, display, record.data.action);
            }

            for (config in actionConfig)
            {
                var a = actionConfig[config];
                if ('object' == typeof a )
                {
                    adminOptions.actions.push({
                        id: a.id,
                        display: a.display,
                        label: a.label,
                        links: a.links
                    });
                }
            }
        }

        // toolbar button configuration
        if (this.toolbar.items)
        {
            var tbarActions = [];
            var items = this.toolbar.items;
            for (var i=0; i < items.getCount(); i++)
            {
                var item = items.get(i);
                var action = this.newActions[item.initialConfig.actionId];
                if (action)
                {
                    tbarActions.push({
                        id: item.initialConfig.actionId,
                        position: i,
                        hideText: action.initialConfig.hideText,
                        hideIcon: action.initialConfig.hideIcon});
                }
            }
            adminOptions.tbarActions = tbarActions;
        }

        adminOptions.importDataEnabled = this.importDataEnabled;
        adminOptions.fileConfig = this.fileConfig;
        adminOptions.emailPref = this.emailPref;

        Ext.Ajax.request({
            url: this.actionsUpdateURL,
            method : 'POST',
            scope: this,
            success: function(){this.fireEvent('success');},
            failure: function(){this.fireEvent('failure');},
            jsonData : adminOptions,
            headers : {
                'Content-Type' : 'application/json'
            }
        });
    }
});

LABKEY.EmailPreferencesPanel = Ext.extend(Ext.util.Observable, {

    fileFields : [],    // array of extra field information to collect/display for each file uploaded
    files : [],         // array of file information for each file being transferred
    fileIndex : 0,
    emailPrefDefault : 0,

    constructor : function(config)
    {
        LABKEY.EmailPreferencesPanel.superclass.constructor.call(this, config);

        Ext.apply(this, config);

        this.addEvents(
            /**
             * @event emailPrefsChanged
             * Fires after the user's email preferences have been updated.
             */
            'emailPrefsChanged'
        );
    },

    show : function(btn)
    {
        Ext.Ajax.request({
            url: LABKEY.ActionURL.buildURL("filecontent", "getEmailPref"),
            method:'GET',
            disableCaching:false,
            success : this.getEmailPref,
            failure: LABKEY.Utils.displayAjaxErrorResponse,
            updateSelection: true,
            scope: this
        });
    },

    getEmailPref : function(response)
    {
        var o = eval('var $=' + response.responseText + ';$;');

        if (o.success)
        {
            var emailPref = o.emailPref;
            this.emailPrefDefault = o.emailPrefDefault;
            var radioItems = [];

            radioItems.push({xtype: 'radio',
                checked: emailPref == -1,
                handler: this.onFolderDefault,
                scope: this,
                boxLabel: "<span class='labkey-strong'>Folder Default</span> - use the defaults configured for this folder by an administrator.",
                name: 'emailPref', inputValue: -1});
            radioItems.push({xtype: 'radio',
                checked: emailPref == 0,
                boxLabel: "<span class='labkey-strong'>None</span> - don't send any email for file changes in this folder.",
                name: 'emailPref', inputValue: 0});
            radioItems.push({xtype: 'radio',
                checked: emailPref == 1,
                boxLabel: '<span class="labkey-strong">Individual</span> - send a separate email for files changes.',
                name: 'emailPref', inputValue: 1});
            radioItems.push({xtype: 'radio',
                checked: emailPref == 2,
                disabled: true,
                boxLabel: '<span class="labkey-strong">Daily Digest</span> - send one email each day that summarizes file changes in this folder.',
                name: 'emailPref', inputValue: 2});

            var radioGroup = new Ext.form.RadioGroup({
                xtype: 'radiogroup',
                //fieldLabel: 'Email Notification Settings',
                itemCls: 'x-check-group',
                columns: 1,
                labelSeparator: '',
                items: radioItems
            });

            var formPanel = new Ext.form.FormPanel({
                bodyStyle : 'padding:10px;',
                labelWidth: 5,
                flex: 1,
                border: false,
                defaultType: 'radio',
                items: radioGroup
            });

            var msgPanel = new Ext.Panel({
                id: 'email-pref-msg',
                border: false,
                height: 40
            });

            var items = [formPanel, msgPanel];

            if (emailPref == -1)
            {
                msgPanel.on('afterrender', function(){this.onFolderDefault(null, true);}, this);
            }

            var panel = new Ext.Panel({
                layout: 'vbox',
                layoutConfig: {
                    align: 'stretch',
                    pack: 'start'
                },
                bodyStyle : 'padding:10px;',
                items: items
            });

            win = new Ext.Window({
                title: 'Email Notification Settings',
                width: 575,
                height: 250,
                cls: 'extContainer',
                autoScroll: true,
                closeAction:'close',
                modal: true,
                layout: 'fit',
                items: panel,
                buttons: [
                    {text:'Submit', handler:function(){
                        formPanel.getForm().doAction('submit', {
                            url: LABKEY.ActionURL.buildURL("filecontent", "setEmailPref"),
                            waitMsg:'Saving Settings...',
                            method: 'POST',
                            success: function(){win.close();},
                            failure: LABKEY.Utils.displayAjaxErrorResponse,
                            scope: this,
                            clientValidation: false
                        });}
                    },
                    {text:'Cancel', handler:function(){win.close();}}
                ]
            });
            win.show();

        }
        else
            Ext.Msg.alert('Error', 'An error occurred getting the user email settings.');
    },

    onFolderDefault : function(cb, checked)
    {
        if (checked)
        {
            var msg = 'The default setting for this folder is: <span class="labkey-strong">None</span>';
            if (this.emailPrefDefault == 1)
                msg = 'The default setting for this folder is: <span class="labkey-strong">Individual</span>';
            else if (this.emailPrefDefault == 2)
                msg = 'The default setting for this folder is: <span class="labkey-strong">Daily Digest</span>';
        }
        else
            msg = '';

        var el = Ext.get('email-pref-msg');
        if (el)
            el.update(msg);
    }
});
