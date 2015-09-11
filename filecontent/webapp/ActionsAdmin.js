/*
 * Copyright (c) 2010-2015 LabKey Corporation
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

    importDataEnabled : true,
    fileConfig : undefined,
    filePropertiesPanel : undefined,
    filePropsGrid : undefined,

    isPipelineRoot : false,
    events : {},

    constructor : function(config)
    {
        this.actionConfig = {};

        this.tbarItemsConfig = [];  // array of config options for each standard toolbar button
        this.actions = {};          // map of action names to actions of all actions available
        this.newActions = {};       // map of newly customized actions

        this.columnModel = {};      // the current file browser grid column model
        this.gridConfig = {};       // the saved grid column header configuration
        this.inheritedTbarConfig = false;

        Ext.apply(this, config);
        Ext.util.Observable.prototype.constructor.call(this, config);

        //we explicitly set the containerPath
        Ext.apply(this, {
            actionsURL : LABKEY.ActionURL.buildURL('pipeline', 'actions', this.containerPath, {allActions:true}),
            actionsUpdateURL : LABKEY.ActionURL.buildURL('pipeline', 'updatePipelineActionConfig', this.containerPath),
            actionsConfigURL : LABKEY.ActionURL.buildURL('pipeline', 'getPipelineActionConfig', this.containerPath)
        });

        if (config.path)
        {
            if (LABKEY.FileSystem.Util.startsWith(config.path,"/"))
                config.path = config.path.substring(1);

            this.actionsURL = LABKEY.ActionURL.buildURL('pipeline', 'actions', this.containerPath, {allActions:true, path:config.path});
        }
    },

    show : function(btn)
    {
        Ext.Ajax.request({
            autoAbort:true,
            url:this.actionsConfigURL,
            method:'GET',
            disableCaching:false,
            success : this.getActionConfiguration,
            failure: LABKEY.Utils.displayAjaxErrorResponse,
            scope: this
        });
    },

    // parse the configuration information
    getActionConfiguration : function(response)
    {
        var o = eval('var $=' + response.responseText + ';$;');
        var config = o.success ? o.config : {};

        // check whether the import data button is enabled
        this.importDataEnabled = config.importDataEnabled ? config.importDataEnabled : false;
        this.fileConfig = config.fileConfig ? config.fileConfig : 'useDefault';
        this.expandFileUpload = config.expandFileUpload != undefined ? config.expandFileUpload : true;
        this.showFolderTree = config.showFolderTree;
        this.inheritedTbarConfig = config.inheritedTbarConfig;

        if ('object' == typeof config.actions)
        {
            for (var i=0; i < config.actions.length; i++)
            {
                var action = config.actions[i];
                this.actionConfig[action.id] = new LABKEY.PipelineActionConfig(action);
            }
        }

        if (this.isPipelineRoot)
        {
            Ext.Ajax.request({
                autoAbort:true,
                url:this.actionsURL,
                method:'GET',
                disableCaching:false,
                success : this.getPipelineActions,
                failure: this.isPipelineRoot ? LABKEY.Utils.displayAjaxErrorResponse : undefined,
                scope: this
            });
        }
        else
            this.renderDialog();
    },

    getPipelineActions : function(response)
    {
        if (!this.isPipelineRoot) return;

        var o = eval('var $=' + response.responseText + ';$;');
        var actions = o.success ? o.actions : [];

        // parse the response and create the data object
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
        var items = [];

        items.push(this.createActionsPropertiesPanel(data));
        items.push(this.createFilePropertiesPanel());
        items.push(this.createToolbarPanel());

        if (!this.disableGeneralAdminSettings)
            items.push(this.createGeneralPanel());

        var tabPanel = new Ext.TabPanel({
            activeTab: 'actionTab',
            stateful: false,
            items: items
        });

        var tbarResetHandler = function(b){
            Ext.MessageBox.confirm("Confirm reset", 'All grid and toolbar button customizations on this page will be deleted, continue?', function(answer)
            {
                if (answer == "yes")
                {
                    Ext.Ajax.request({
                        url: LABKEY.ActionURL.buildURL('filecontent', 'resetFileOptions', null, {type:'tbar'}),
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
        };
        var actionResetHandler = function(b){
            Ext.MessageBox.confirm("Confirm reset", 'All action customizations on this page will be deleted, continue?', function(answer)
            {
                if (answer == "yes")
                {
                    Ext.Ajax.request({
                        url: LABKEY.ActionURL.buildURL('filecontent', 'resetFileOptions', null, {type:'actions'}),
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
        };

        tabPanel.on('tabchange', function(tp, panel){
            if (panel.getId() == 'toolbarTab') {

                this.resetToolbarBtn.setVisible(true);
                this.resetToolbarBtn.setHandler(tbarResetHandler);

                // Do this here so that the panel gets laid out properly, and then we hide the elements
                Ext.ComponentMgr.get('dragHelpHTML').setVisible(!this.inheritedTbarConfig);
                Ext.ComponentMgr.get('allButtonsPanel').setVisible(!this.inheritedTbarConfig);
                Ext.ComponentMgr.get('exampleFileGrid').setVisible(!this.inheritedTbarConfig);
            }
            else if (panel.getId() == 'actionTab') {

                this.resetToolbarBtn.setVisible(true);
                this.resetToolbarBtn.setHandler(actionResetHandler);
            }
            else
                this.resetToolbarBtn.setVisible(false);
        }, this);


        this.resetToolbarBtn = new Ext.Button({
            text: 'Reset to Default',
            id: 'btn_reset',
            tooltip: 'Reset options to the default.',
            scope: this,
            hidden: true
        });

        var win = new Ext.Window({
            title: 'Manage File Browser Configuration',
            width: 750,
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
        var items = [];

        if (this.isPipelineRoot)
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

            items.push(
                new Ext.Panel({
                    border: false,
                    height: 60,
                    bodyStyle : 'padding:10 10 10 0px;',
                    items:{
                        xtype: 'checkbox',
                        checked: this.importDataEnabled,
                        labelSeparator: '',
                        boxLabel: "Show 'Import Data' toolbar button<br/>(<i>Administrators will always see this button</i>)",
                        name: 'importAction',
                        listeners: {check: function(button, checked) {this.importDataEnabled = checked;}, scope:this}
                    }
                }));
            items.push(grid);
        }
        else {
            items.push({
                //xtype: 'panel',
                bodyStyle : 'padding: 30 10px;',
                autoHeight: true,
                layout: 'fit',
                border: false,
                html: 'File Actions are only available for files in the pipeline directory. An administrator has defined ' +
                      'a "pipeline override" for this folder, so actions are not available in the default file location.' +
                      '<br/><br/>Customize this web part to use the pipeline directory by clicking on the ' +
                      '"more" button in the web part title area and selecting the "customize" option. You can then set this ' +
                      'web part to show files from the pipeline directory.<br>' +
                      '<img src="' + LABKEY.contextPath + '/_images/customize-example.png"/>'
            });
        }

        var actionPanel = new Ext.Panel({
            id: 'actionTab',
            title: 'Actions',
            bodyStyle : 'padding:10px;',
            layout: 'vbox',
            layoutConfig: {
                align: 'stretch',
                pack: 'start'
            },
            items: items
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
                url: LABKEY.ActionURL.buildURL("pipeline", "getPipelineFileProperties", this.containerPath),
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
                        {boxLabel:'Use Default (none)', name: 'fileOption', inputValue: 'useDefault', checked: this.fileConfig == 'useDefault'},
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
        var buttons = [], i;
        if (this.tbarItemsConfig && this.tbarItemsConfig.length)
        {
            for (i=0; i < this.tbarItemsConfig.length; i++)
            {
                var cfg = this.tbarItemsConfig[i];
                if (Ext.isObject(this.actions[cfg.id]))
                {
                    var action = this.actions[cfg.id];
                    action.initialConfig.pressed = false;
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
            cls: 'test-custom-toolbar',
            enableOverflow: true,
            scope: this,
            border: true
        });

        var actions = [];
        for (i in this.actions)
        {
            var action = this.actions[i];

            if (Ext.isObject(action))
            {
                var config = Ext.applyIf({xtype:'button', disabled:false, pressed: false, actionId:i}, action.initialConfig);

                config.handler = undefined;
                config.listeners = undefined;
                config.text = config.prevText;
                config.iconCls = config.prevIconCls;
                config.hideIcon = false;
                config.hideText = false;

                actions.push(config);
            }
        }

        var panel = new Ext.Panel({
            border: false,
            flex: 1,
            layout: 'table',
            //bodyStyle:'padding:30px',
            //tbar: this.toolbar,
            layoutConfig: {columns:5},
            items: [actions],
            id: 'allButtonsPanel'
        });

        panel.on('render', function(v) {
            panel.dragZone = new Ext.dd.DragZone(v.getEl(), {

                getDragData: function(e) {
                    var sourceEl = e.getTarget('table.x-btn', 10, true);

                    if (sourceEl) {
                        var d = sourceEl.dom.cloneNode(true);
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

        // substitute in some renderers
        for (i=0; i < this.columnModel.length; i++)
        {
            var col = this.columnModel[i];

            if (col.dataIndex == 'modified')
                col.renderer = Ext.util.Format.dateRenderer("Y-m-d H:i:s");
            if (col.dataIndex == 'iconHref')
                col.renderer = function(){
                    var img = {tag:'img', width:16, height:16, src:LABKEY.contextPath + "/_icons/xls.gif"};
                    return Ext.DomHelper.markup(img);
                };
            else if (col.renderer)
                col.renderer = undefined;
        }

        this.grid = new Ext.grid.GridPanel({
            tbar: this.toolbar,
            layout: 'fit',
            store: {
                xtype: 'arraystore',
                fields: [
                    {name: 'name'},
                    {name: 'modified'},
                    {name: 'size'},
                    {name: 'createdBy'},
                    {name: 'description'},
                    {name: 'fileExt'}
                ],
                data: [
                    ['file1.xls', '4/15/2010', 10150, 'test user', 'first file', 'xls'],
                    ['file2.xls', '4/16/2010', 13155, 'administrator', 'second file', 'xls']
                ]
            },
            border: true,
            height: 150,
            columns: this.columnModel,
            id: 'exampleFileGrid'
        });

        if (this.gridConfig)
            this.grid.applyState(this.gridConfig);

        return new Ext.Panel({
            id: 'toolbarTab',
            title: 'Toolbar and Grid Settings',
            bodyStyle : 'padding:10px;',
            layout: 'vbox',
            layoutConfig: {
                align: 'stretch',
                pack: 'start'
            },
            items: [
                {html: '<span class="labkey-strong">Configure Grid columns and Toolbar</span>', border: false, height: 20, autoScroll:true},
                {
                    xtype: 'radiogroup',
                    height: 20,
                    items: [{
                        boxLabel: 'Inherit configuration from parent folder/project',
                        // Workbooks always inherit from parent config, but pull the config directly so
                        // be sure the UI reflects that
                        checked: this.inheritedTbarConfig || LABKEY.Security.currentContainer.type == 'workbook',
                        name: 'inheritedTbarConfig',
                        value: 'true',
                        disabled: LABKEY.Security.currentContainer.type == 'project'
                    },{
                        boxLabel: 'Define configuration for this folder/project',
                        // Workbooks always inherit from parent config, but pull the config directly so
                        // be sure the UI reflects that
                        checked: !this.inheritedTbarConfig && !LABKEY.Security.currentContainer.type == 'workbook',
                        name: 'inheritedTbarConfig',
                        value: 'false',
                        disabled: LABKEY.Security.currentContainer.type == 'workbook'
                    }],
                    listeners: {
                        change: { fn: function(field, checked) {
                            this.inheritedTbarConfig = checked.value == 'true';
                            // Show/hide the actual config based on whether it's being set in this container or being inherited
                            Ext.ComponentMgr.get('dragHelpHTML').setVisible(!this.inheritedTbarConfig);
                            Ext.ComponentMgr.get('allButtonsPanel').setVisible(!this.inheritedTbarConfig);
                            Ext.ComponentMgr.get('exampleFileGrid').setVisible(!this.inheritedTbarConfig);
                        }, scope: this }
                    }
                },
                {html: 'Drag the buttons on the toolbar to customize the button order. ' +
                       'Buttons can be added by dragging from the list of available buttons below and dropping them on the toolbar. Buttons can be removed ' +
                       'by clicking on the toolbar button and selecting "remove" from the dropdown menu.<br><br>' +
                       'Grid columns can be customized by dragging to reorder, adjusting the width, and controlling the sort or show state using the drop down ' +
                       'menus on each column.', border: false, height: 110, autoScroll:true, id: "dragHelpHTML"},
                panel,
                this.grid
            ]
        });
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

                    //if this item is in the overflow area of the toolbar, we need to remove it differently
                    var overflowMenu = b.ownerCt.parentMenu;
                    if(overflowMenu)
                    {
                        overflowMenu.items.each(function(i){
                            if(i.actionId == b.initialConfig.actionId){
                                overflowMenu.hide();
                                overflowMenu.remove(i);
                            }
                        }, this);
                    }

                    if (action && ('object' == typeof action))
                    {
                        action.each(function(item, idx, all){
                            item.destroy();
                        }, this);
                        delete this.newActions[b.initialConfig.actionId];
                    }
            }});
        }

        config.menu = {cls: 'extContainer', items: items};
        return new Ext.Action(config);
    },

    onFilePropConfigChanged : function(group, rb)
    {
        this.fileConfig = rb.getGroupValue();
        this.editBtn.setDisabled(this.fileConfig != 'useCustom');

        this.filePropsGrid.getStore().load({params:{fileConfig: this.fileConfig}});
    },

    onEditFileProperties : function(btn, evt)
    {
        var handler = function(){window.location = LABKEY.ActionURL.buildURL('fileContent', 'designer', this.containerPath, {'returnURL':window.location});};
        this.saveActionConfig(btn, evt, handler);
    },

    /**
     * Save changes to the manage action dialog
     */
    saveActionConfig : function(button, event, handler)
    {
        var adminOptions = {actions: []};
        var records = this.actionsStore ? this.actionsStore.getModifiedRecords() : undefined;

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

        // grid column model changes
        adminOptions.gridConfig = this.grid.getState();
        adminOptions.inheritedTbarConfig = this.inheritedTbarConfig;

        // general settings
        adminOptions.expandFileUpload = this.expandFileUpload;
        adminOptions.showFolderTree = this.showFolderTree;

        var defaultHandler = function(){this.fireEvent('success')};

        Ext.Ajax.request({
            url: this.actionsUpdateURL,
            method : 'POST',
            scope: this,
            success: handler ? handler : defaultHandler,
            failure: function(){this.fireEvent('failure');},
            jsonData : adminOptions,
            headers : {
                'Content-Type' : 'application/json'
            }
        });
    },

    /**
     * Add the general setttings preferences configuration panel
     */
    createGeneralPanel : function()
    {
        var checkItems = [];
        checkItems.push({xtype: 'checkbox',
            checked: this.expandFileUpload != undefined ? this.expandFileUpload : true,
            id: 'ck-expand-file-upload',
            handler: function(cmp, checked){this.expandFileUpload = checked;},
            scope: this,
            boxLabel: 'Show the file upload panel by default.'});
/*
        checkItems.push({xtype: 'checkbox',
            checked: this.showFolderTree,
            id: 'ck-expand-folder-tree',
            handler: function(cmp, checked){this.showFolderTree = checked;},
            scope: this,
            boxLabel: 'Show folder tree by default.'});
*/

        var panel = new Ext.form.FormPanel({
            bodyStyle : 'padding:10px;',
            labelWidth: 10,
            height: 150,
            border: false,
            defaultType: 'checkbox',
            items: checkItems
        });

        var generalPanel = new Ext.Panel({
            id: 'generalTab',
            title: 'General Settings',
            bodyStyle : 'padding:10px;',
            layout: 'vbox',
            layoutConfig: {
                align: 'stretch',
                pack: 'start'
            },
            items: [
                {html: '<span class="labkey-strong">Configure General Settings</span><br>Set the default File UI preferences for this folder.', border: false, height: 55, autoScroll:true},
                panel
            ]
        });

        return generalPanel;
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
            url: LABKEY.ActionURL.buildURL("filecontent", "getEmailPref", this.containerPath),
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
                checked: emailPref == 513,
                boxLabel: '<span class="labkey-strong">15 Minute Digest</span> - send a email for file changes within a fifteen minute span.',
                name: 'emailPref', inputValue: 513});
            radioItems.push({xtype: 'radio',
                checked: emailPref == 514,
                boxLabel: '<span class="labkey-strong">Daily Digest</span> - send one email each day that summarizes file changes in this folder.',
                name: 'emailPref', inputValue: 514});
            radioItems.push({xtype: 'radio',
                checked: emailPref == 512,
                boxLabel: "<span class='labkey-strong'>None</span> - don't send any email for file changes in this folder.",
                name: 'emailPref', inputValue: 512});

            var radioGroup = new Ext.form.RadioGroup({
                xtype: 'radiogroup',
                //fieldLabel: 'Email Notification Settings',
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

            if (this.renderTo)
            {
                var p = new Ext.Panel({
                    renderTo: this.renderTo,
                    border: false,
                    height: 175,
                    items: items,
                    buttonAlign: 'left',
                    buttons: [
                        {text:'Submit', scope: this, handler:function(){
                            formPanel.getForm().doAction('submit', {
                                url: LABKEY.ActionURL.buildURL("filecontent", "setEmailPref", this.containerPath),
                                waitMsg:'Saving Settings...',
                                method: 'POST',
                                success: function(){window.location = LABKEY.ActionURL.buildURL('filecontent', 'begin', this.containerPath);},
                                failure: LABKEY.Utils.displayAjaxErrorResponse,
                                scope: this,
                                clientValidation: false
                            });}
                        },
                        {text:'Cancel', scope: this, handler:function(){
                            window.location = LABKEY.ActionURL.buildURL('filecontent', 'begin', this.containerPath);
                        }}
                    ]
                })
            }
            else
            {
                var win = new Ext.Window({
                    title: 'Email Notification Settings',
                    width: 650,
                    height: 250,
                    cls: 'extContainer',
                    autoScroll: true,
                    closeAction:'close',
                    modal: true,
                    layout: 'fit',
                    items: panel,
                    buttons: [
                        {text:'Submit', scope: this, handler:function(){
                            formPanel.getForm().doAction('submit', {
                                url: LABKEY.ActionURL.buildURL("filecontent", "setEmailPref", this.containerPath),
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
        }
        else
            Ext.Msg.alert('Error', 'An error occurred getting the user email settings.');
    },

    onFolderDefault : function(cb, checked)
    {
        if (checked)
        {
            var msg = 'The default setting for this folder is: <span class="labkey-strong">None</span>';
            if (this.emailPrefDefault == 513)
                msg = 'The default setting for this folder is: <span class="labkey-strong">15 Minute Digest</span>';
            else if (this.emailPrefDefault == 514)
                msg = 'The default setting for this folder is: <span class="labkey-strong">Daily Digest</span>';
        }
        else
            msg = '';

        var el = Ext.get('email-pref-msg');
        if (el)
            el.update(msg);
    }
});
