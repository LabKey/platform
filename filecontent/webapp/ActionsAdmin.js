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

    isPipelineRoot : false,
    
    events : {},

    actionConfig : {},

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
                title: 'Manage Pipeline Actions',
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

        if ('object' == typeof config.actions)
        {
            for (var i=0; i < config.actions.length; i++)
            {
                var action = config.actions[i];
                this.actionConfig[action.id] = new LABKEY.PipelineActionConfig(action);
            }
        }

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
        this.renderPipelineActions(data);
    },

    renderPipelineActions : function(data)
    {
        var store = new Ext.data.GroupingStore({
            reader: new Ext.data.JsonReader({root:'actions',id:'id'},
                    [
                        {name: 'type'},
                        {name: 'action'},
                        {name: 'actionId'},
                        {name: 'description'},
                        {name: 'display'},
                        {name: 'enabled', type: 'boolean'},
                        {name: 'showOnToolbar', type: 'boolean'}]),
//            proxy: new Ext.data.HttpProxy(dataConnection),
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
                    //{header:'Category', dataIndex:'category', width:200},
                    //{header:'Description', dataIndex:'description', width:300},
                    enabledColumn,
                    onToolbarColumn
                ]
            });

        var selModel = new Ext.grid.CheckboxSelectionModel({moveEditorOnEnter: false});
        var grid = new Ext.grid.EditorGridPanel({
            loadMask:{msg:"Loading, please wait..."},
            store: store,
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

        var filePanel = new Ext.Panel({
                id: 'fileTab',
                title: 'File Properties',
                bodyStyle : 'padding:10px;',
                autoHeight: true,
                items: [
                    new Ext.form.RadioGroup({
                        xtype: 'radiogroup',
                        itemCls: 'x-check-group',
                        columns: 1,
                        labelSeparator: '',
                        items: [
                            {xtype:'radio', boxLabel:'Use Default File Properties'},
                            {xtype:'radio', boxLabel:'Use Custom File Properties'},
                            {xtype:'radio', boxLabel:'Use Same Settings as Parent'}
                        ]
                    })
                ]
        });

        var tabPanel = new Ext.TabPanel({
            //header: false,
            //margins:'0 0 0 0',
            //border: false,
            bodyStyle: 'background-color:#f0f0f0;',
            activeTab: 'actionTab',
            stateful: false,
            items: [
                actionPanel,
                filePanel
            ]
        });

        var win = new Ext.Window({
            title: 'Manage Pipeline Actions',
            width: 500,
            height: 400,
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
                    win.close();
                    this.saveActionConfig(store, button, event);}, scope:this}
            },{
                text: 'Cancel',
                id: 'btn_cancel',
                handler: function(){win.close();}
            }]
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
            var adminOptions = {actions: []};
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

            adminOptions.importDataEnabled = this.importDataEnabled;

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
    }
});

