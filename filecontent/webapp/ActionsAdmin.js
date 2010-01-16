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
LABKEY.ActionsAdminPanel = Ext.extend(Ext.util.Observable, {

    actionsConfigURL : LABKEY.ActionURL.buildURL("pipeline", "pipelineActionsConfig"),

    actionsUpdateURL : LABKEY.ActionURL.buildURL("pipeline", "updatePipelineActionConfig"),

    // run selected action
    runAction : undefined,

    events : {},

    constructor : function(config)
    {
        Ext.util.Observable.prototype.constructor.call(this, config);

        if (config.path)
        {
            config.path;
            if (startsWith(config.path,"/"))
                config.path = config.path.substring(1);

//            this.actionsConfigURL = LABKEY.ActionURL.buildURL('pipeline', 'actions', null, {allActions:true, path:config.path});
            this.actionsConfigURL = LABKEY.ActionURL.buildURL('pipeline', 'actions', null, {path:config.path});
            this.actionsUpdateURL = LABKEY.ActionURL.buildURL('pipeline', 'updatePipelineActionConfig');
        }
    },

    show : function(btn)
    {
        var connection = new Ext.data.Connection({autoAbort:true});
        connection.request({
            autoAbort:true,
            url:this.actionsConfigURL,
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
                var action = actions[i];
                if (action.links.items != undefined)
                {
                    for (var j=0; j < action.links.items.length; j++)
                    {
                        var item = action.links.items[j];
                        if (item.text && item.href)
                        {
                            data.actions.push({
                                type: action.links.text,
                                id: item.id,
                                display: item.display,
                                action: item.text,
                                href: item.href,
                                enabled: item.display == 'enabled' || 'toolbar',
                                showOnToolbar: item.display == 'toolbar'
                            });
                        }
                    }
                }
                else
                {
                    data.actions.push({
                        type: action.links.text,
                        id: action.links.id,
                        display: action.links.display,
                        action: action.links.text,
                        href: action.links.href,
                        enabled: action.links.display == 'enabled' || 'toolbar',
                        showOnToolbar: action.links.display == 'toolbar'
                    });
                }
            }
        }
        this.renderPipelineActions(data);
    },

    renderPipelineActions : function(data)
    {
        var comboStore = new Ext.data.SimpleStore({
            fields: [{name: 'displayLabel'}, {name: 'displayState'}],
            data: [['Enabled', 'enabled'],['Disabled', 'disabled'],['Show on toolbar', 'toolbar']]
        });

        var dataConnection = new Ext.data.Connection({
            url: this.actionsConfigURL,
            method: 'GET',
            timeout: 300000
        });

        var store = new Ext.data.GroupingStore({
            reader: new Ext.data.JsonReader({root:'actions',id:'id'},
                    [
                        {name: 'type'},
                        {name: 'action'},
                        {name: 'description'},
                        {name: 'display'},
                        {name: 'enabled', type: 'boolean'},
                        {name: 'showOnToolbar', type: 'boolean'}]),
//            proxy: new Ext.data.HttpProxy(dataConnection),
            data: data,
            sortInfo: {field:'type', direction:"ASC"},
            groupField:'type',
            autoLoad: true});

        var enabledColumn = new Ext.grid.CheckColumn({header: 'Enabled', dataIndex: 'enabled'});
        var onToolbarColumn = new Ext.grid.CheckColumn({header: 'Show on Toolbar', dataIndex: 'showOnToolbar', width: 175});

         var cm = new Ext.grid.ColumnModel({
                // specify any defaults for each column
                columns: [
                    {header:'Type', dataIndex:'type'},
                    {header:'Action', dataIndex:'action', width:200},
                    {header:'Category', dataIndex:'category', width:200},
                    //{header:'Description', dataIndex:'description', width:300},
                    enabledColumn,
                    onToolbarColumn,
/*
                        {header:'Display State', dataIndex: 'displayState',
                            editor: new Ext.form.ComboBox({
                            editable: false,
                            valueField: 'displayState',
                            displayField: 'displayLabel',
                            mode: 'local',
                            forceSelection: true,
                            triggerAction: 'all',
                            store: comboStore,
                            listClass: 'x-combo-list-small'
                        })}
*/
                ]
            });

        this.runAction = new Ext.Action({
            text: 'Run Selected Action',
            disabled: true,
            id: 'btn_runAction',
            listeners: {click:function(button, event) {
                win.close();
                this.runSelectedAction(grid, button, event);}, scope:this}
        });

        var selModel = new Ext.grid.CheckboxSelectionModel({moveEditorOnEnter: false});
        selModel.on('selectionchange', function(sel){
            if (sel.hasSelection())
                this.runAction.enable();
            else
                this.runAction.disable();
        }, this);

        var grid = new Ext.grid.EditorGridPanel({
            loadMask:{msg:"Loading, please wait..."},
            store: store,
            selModel: selModel,
            stripeRows: true,
            clicksToEdit: 1,
            cm: cm,
            plugins: [enabledColumn, onToolbarColumn],
            view: new Ext.grid.GroupingView({
                startCollapsed:false,
                hideGroupedColumn:true,
                forceFit:true,
                groupTextTpl: '{values.group}'
            })
        });

        var actionPanel = new Ext.Panel({
            bodyStyle : 'padding:10px;',
            items: [grid]
        });

        var win = new Ext.Window({
            title: 'Manage Pipeline Actions',
//                header: false,
//                closable: false,
            border: false,
            width: 650,
            height: 400,
            cls: 'extContainer',
            autoScroll: true,
            closeAction:'close',
            modal: true,
            items: [actionPanel],
            buttons: [{
                text: 'Submit',
                id: 'btn_submit',
                listeners: {click:function(button, event) {
                    win.close();
                    this.saveActionConfig(store, button, event);}, scope:this}
            },{
                text: 'Cancel',
                id: 'btn_cancel',
                handler: function(){win.close();}
            },
            this.runAction]
        });
        win.show();
    },

    /**
     * Save changes to the manage action dialog
     */
    saveActionConfig : function(store, button, event)
    {
        var records = store.getRange(0);//getModifiedRecords();

        if (records && records.length)
        {
            var params = [];

            for (var i=0; i <records.length; i++)
            {
                var record = records[i];

                if (record.data.showOnToolbar)
                    params.push('toolbar=' + encodeURIComponent(record.id));
                else if (record.data.enabled)
                    params.push('enabled=' + encodeURIComponent(record.id));
                else
                    params.push('disabled=' + encodeURIComponent(record.id));
            }
            Ext.Ajax.request({

                url: this.actionsUpdateURL + '?' + params.join('&'),
                method: "POST",
                scope: this,
                success: function(){this.fireEvent('success');},
                failure: function(){this.fireEvent('failure');}
            });
        }
    },

    runSelectedAction : function(grid, button, event)
    {
        var selModel = grid.getSelectionModel();
        var record = selModel.getSelected();

        if (record)
        {
            this.fireEvent('runSelected', record);
        }
    }
});

