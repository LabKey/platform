/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('Ext.ux.CheckColumn', {
    extend: 'Ext.grid.column.Column',
    alias: 'widget.checkcolumn',

    /**
     * @cfg {Boolean} [stopSelection=true]
     * Prevent grid selection upon mousedown.
     */
    stopSelection: true,

    tdCls: Ext4.baseCSSPrefix + 'grid-cell-checkcolumn',

    constructor: function(config) {
        this.addEvents(
                /**
                 * @event beforecheckchange
                 * Fires when before checked state of a row changes.
                 * The change may be vetoed by returning `false` from a listener.
                 * @param {Ext.ux.CheckColumn} this CheckColumn
                 * @param {Number} rowIndex The row index
                 * @param {Boolean} checked True if the box is to be checked
                 */
                'beforecheckchange',
                /**
                 * @event checkchange
                 * Fires when the checked state of a row changes
                 * @param {Ext.ux.CheckColumn} this CheckColumn
                 * @param {Number} rowIndex The row index
                 * @param {Boolean} checked True if the box is now checked
                 */
                'checkchange'
        );
        this.callParent(arguments);
    },

    /**
     * @private
     * Process and refire events routed from the GridView's processEvent method.
     */
    processEvent: function(type, view, cell, recordIndex, cellIndex, e, record, row) {
        var me = this,
                key = type === 'keydown' && e.getKey(),
                mousedown = type == 'mousedown';

        if (mousedown || (key == e.ENTER || key == e.SPACE)) {
            var dataIndex = me.dataIndex,
                    checked = !record.get(dataIndex);

            // Allow apps to hook beforecheckchange
            if (me.fireEvent('beforecheckchange', me, recordIndex, checked) !== false) {
                record.set(dataIndex, checked);
                me.fireEvent('checkchange', me, recordIndex, checked);

                // Mousedown on the now nonexistent cell causes the view to blur, so stop it continuing.
                if (mousedown) {
                    e.stopEvent();
                }

                // Selection will not proceed after this because of the DOM update caused by the record modification
                // Invoke the SelectionModel unless configured not to do so
                if (!me.stopSelection) {
                    view.selModel.selectByPosition({
                        row: recordIndex,
                        column: cellIndex
                    });
                }

                // Prevent the view from propagating the event to the selection model - we have done that job.
                return false;
            } else {
                // Prevent the view from propagating the event to the selection model if configured to do so.
                return !me.stopSelection;
            }
        } else {
            return me.callParent(arguments);
        }
    },

    // Note: class names are not placed on the prototype bc renderer scope
    // is not in the header.
    renderer : function(value, metadata){
        var cssPrefix = Ext4.baseCSSPrefix,
                cls = [cssPrefix + 'grid-checkheader'];

        if (value) {
            cls.push(cssPrefix + 'grid-checkheader-checked');
        }

        if(metadata && metadata.disabled){
            return '<div style="opacity : .5" class="' + cls.join(' ') + '">&#160;</div>';
        }
        else return '<div class="' + cls.join(' ') + '">&#160;</div>';
    }
});


Ext4.define('File.panel.ActionsPanel',  {
    extend : 'Ext.panel.Panel',
    id : 'actionsPanel',
    items : [
        {
            xtype : 'checkbox',
            id : 'showImportCheckbox',
            checked: this.importDataEnabled,
            labelSeparator: '',
            boxLabel: "Show 'Import Data' toolbar button<br/>(<i>Administrators will always see this button</i>)",
            width : 500,
            height : 75
        },
        this.actionGrid],
    listeners : {
        gridloaded : function(grid){
            this.add(grid);
            this.doLayout();
        }
    },

    getImportEnabled : function() {
        return Ext4.getCmp('showImportCheckBox').getValue();
    },

    constructor : function(config){

        this.actionConfig = {};

        Ext4.apply(this, {
            actionsURL : LABKEY.ActionURL.buildURL('pipeline', 'actions', this.containerPath, {allActions:true}),
            actionsUpdateURL : LABKEY.ActionURL.buildURL('pipeline', 'updatePipelineActionConfig', this.containerPath),
            actionsConfigURL : LABKEY.ActionURL.buildURL('pipeline', 'getPipelineActionConfig', this.containerPath)
        });

        Ext4.Ajax.request({
            autoAbort:true,
            url:this.actionsConfigURL,
            method:'GET',
            disableCaching:false,
            success : this.getActionConfiguration,
            failure: LABKEY.Utils.displayAjaxErrorResponse,
            scope: this
        });
        //this.actionsURL = LABKEY.ActionURL.buildURL('pipeline', 'actions', LABKEY.container.path, {allActions:true});

        this.callParent([config]);
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
                this.actionConfig[action.id] = action;
            }
        }



        if (this.isPipelineRoot)
        {
            Ext4.Ajax.request({
                autoAbort:true,
                url:this.actionsURL,
                method:'GET',
                disableCaching:false,
                success : this.getPipelineActions,
                failure: this.isPipelineRoot ? LABKEY.Utils.displayAjaxErrorResponse : undefined,
                scope: this
            });
        }
    },

    getPipelineActions : function(response)
    {
         if (!this.isPipelineRoot) return;

        var o = eval('var $=' + response.responseText + ';$;');
        var actions = o.success ? o.actions : [];

        // parse the reponse and create the data object
        var data = {actions: []};
        if (actions && actions.length)
        {
            for (var i=0; i < actions.length; i++)
            {
                var pUtil = actions[i];
                var links = pUtil.links.items;
                if(!links)
                    links = [pUtil.links];

                if (!links) continue;

                var config = this.actionConfig[pUtil.links.id];
                for (var j=0; j < links.length; j++)
                {
                    var link = links[j];

                    if (link.href)
                    {
                        var display = 'enabled';
                        if (config)
                        {
                            var linkConfig = config.links[0];
                            if (linkConfig)
                                display = linkConfig.display;
                        }

                        data.actions.push({
                            type: pUtil.links.text,
                            id: link.id,
                            actionId : pUtil.links.id,
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
        this.createActionsPropertiesPanel(data);

    },

    createActionsPropertiesPanel : function(data)
    {
        Ext4.define('ActionModel', {
           extend : 'Ext.data.Model',
            fields:[
                {name : 'type', type : 'string'},
                {name : 'action', type : 'string'},
                {name : 'enabled', type : 'boolean'},
                {name : 'showOnToolbar', type : 'boolean'},
                {name : 'actionId', type : 'string'},
                {name : 'id', type : 'string'}
            ]
        });
        var store = Ext4.create('Ext.data.Store', {
            storeId:'actionStore',
            model : 'ActionModel',
            groupField: 'type',
            data: data.actions
        });

        var groupingFeature = Ext4.create('Ext.grid.feature.Grouping', {
            groupHeaderTpl: '{name}', //print the number of items in the group
            startCollapsed: false // start all groups collapsed
        });

        this.actionGrid = Ext4.create('Ext.grid.Panel', {
            store: store,
            columns: [
                { text: 'Action',     dataIndex: 'action', flex : 1 },
                { text: 'Enabled', dataIndex: 'enabled', xtype : 'checkcolumn', width : 100 },
                { text: 'Show on Toolbar', dataIndex: 'showOnToolbar', xtype : 'checkcolumn', width : 150 }
            ],
            features: [groupingFeature],
            algin : 'bottom',
            height : 400
        });

        this.fireEvent('gridloaded', this.actionGrid);
        Ext4.getCmp('showImportCheckbox').setValue(this.importDataEnabled);

    },

    getActionsForSubmission : function()
    {
        var adminOptions = [];
        var records = this.actionGrid.getStore() ? this.actionGrid.getStore().getModifiedRecords() : undefined;

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
                    config = {id: record.data.actionId, display: 'enabled', label: record.data.type};
                    actionConfig[record.data.actionId] = config;
                }
                if(!config.links)
                    config.links = [];
                config.links.push({id: record.data.id, display: display, label: record.data.action});
            }

            for (config in actionConfig)
            {
                var a = actionConfig[config];
                if ('object' == typeof a )
                {
                    adminOptions.push({
                        id: a.id,
                        display: a.display,
                        label: a.label,
                        links: a.links
                    });
                }
            }
        }

        return adminOptions;
    }
});