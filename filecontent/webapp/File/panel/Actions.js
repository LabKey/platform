/*
 * Copyright (c) 2013-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('File.panel.Action', {
    extend: 'Ext.Action',

    actionType: null,

    statics: {
        Type: {
            NOMIN: 'NOMIN', // 'No file required',
            ATLEASTONE: 'ATLEASTONE', // 'At least one file required',
            ONLYONE: 'ONLYONE', // 'Only one file allowed',
            ATLEASTTWO: 'ATLEASTTWO', // 'Needs at least two files',
            NOFILE: 'NOFILE' // 'Only works with no files'
        },

        ItemType: {
            ONLY_FILE: 'ONLY_FILE', // Only works on files
            ONLY_FOLDER: 'ONLY_FOLDER', // Only works on folders
            FILE_OR_FOLDER: 'FILE_OR_FOLDER' // Either files or folders (same as having no actionItemType set)
        }
    },

    hideIcon: false,

    hideText: false,

    constructor : function(config) {

        if (config.itemId)
            config.cls = config.itemId + "Btn";

        Ext4.apply(this, {
            hardText: config.hardText,
            hardIconCls: config.hardIconCls
        });

        this.resetProps = {
            hideIcon: config.hideIcon === true,
            hideText: config.hideText === true
        };

        this.callParent([config]);
    },

    updateEnabled : function (fileSystem, selection) {
        var actionType = this.initialConfig.actionType;
        var actionItemType = this.initialConfig.actionItemType;

        if (!actionType)
            return;

        var types = File.panel.Action.Type;
        var itemTypes = File.panel.Action.ItemType;

        if (actionType == types.NOMIN) {
            // no nothing
        }
        else if (selection.length == 0) {
            // Set disabled when case is: ATLEASTONE or ONLYONE or ATLEASTTWO
            this.setDisabled((actionType == types.ATLEASTONE || actionType == types.ONLYONE || actionType == types.ATLEASTTWO));
        }
        else if (selection.length == 1) {
            // Set disabled when case is: ATLEASTTWO or NOFILE
            this.setDisabled((actionType == types.ATLEASTTWO || actionType == types.NOFILE));

            // Set disabled if the any of the selected don't match the actionItemType
            if (actionItemType) {
                if (actionItemType == itemTypes.ONLY_FILE && selection[0].data.collection)
                    this.disable();
                if (actionItemType == itemTypes.ONLY_FOLDER && !selection[0].data.collection)
                    this.disable();
            }

            // Let the action instance decide if it should be disabled
            if (Ext4.isFunction(this.initialConfig.shouldDisable)) {
                if (this.initialConfig.shouldDisable.call(this, fileSystem, selection[0]))
                    this.disable();
            }
        }
        else if (selection.length >= 2) {
            // Set disabled when case is: ONLYONE or NOFILE
            this.setDisabled((actionType == types.ONLYONE || actionType == types.NOFILE));

            // Set disabled if the any of the selected don't match the actionItemType
            for (var i = 0, len = selection.length; i < len; i++) {
                if (actionItemType) {
                    if (actionItemType == itemTypes.ONLY_FILE && selection[i].data.collection) {
                        this.disable();
                        break;
                    }
                    if (actionItemType == itemTypes.ONLY_FOLDER && !selection[i].data.collection) {
                        this.disable();
                        break;
                    }
                }

                // Let the action instance decide if it should be disabled
                if (Ext4.isFunction(this.initialConfig.shouldDisable)) {
                    if (this.initialConfig.shouldDisable.call(this, fileSystem, selection[i])) {
                        this.disable();
                        break;
                    }
                }
            }
        }

    }
});

Ext4.define('File.panel.Actions', {
    extend : 'Ext.panel.Panel',

    border: false,
    frame: false,
    padding: 10,

    isPipelineRoot: false,

    importDataEnabled: false,

    constructor : function(config) {

        // Define models
        if (!Ext4.ModelManager.isRegistered('File.Model.ActionModel')) {
            Ext4.define('File.Model.ActionModel', {
                extend: 'Ext.data.Model',
                fields: [
                    { name: 'action' },
                    { name: 'actionId' },
                    { name: 'enabled', type: 'boolean' },
                    { name: 'id' },
                    { name: 'showOnToolbar', type: 'boolean' },
                    { name: 'type' }
                ]
            });
        }

        if (Ext4.isEmpty(config.containerPath)) {
            throw this.$className + ' requires a containerPath be specified.';
        }

        this.actionConfig = {};

        this.callParent([config]);
    },

    initComponent : function() {

        this.items = [{
            xtype: 'box',
            tpl: new Ext4.XTemplate('<span class="labkey-strong">Configure Actions</span>'),
            data: {},
            border: false
        }];

        this.callParent();

        var hasConfig = false,
            pipeActions = false;

        // onReady will be called on the pipeline config and actions have been received
        var onReady = function() {
            if (hasConfig && pipeActions) {

                if (this.isPipelineRoot) {
                    this.add({
                        xtype: 'checkbox',
                        itemId: 'showImportCheckbox',
                        id: 'importAction',
                        checked: this.importDataEnabled,
                        border: false,
                        frame: false,
                        labelSeparator: '',
                        boxLabel: 'Show "Import Data" toolbar button<br/>(<i>Administrators will always see this button</i>)',
                        width: 500,
                        height: 50,
                        padding: '10 0 0 0'
                    });
                }
                else {
                    var descriptionText = 'File Actions are only available for files in the pipeline directory. An administrator has defined ' +
                            'a "pipeline override" for this folder, so actions are not available in the default file location.<br/><br/>';
                    if (LABKEY.ActionURL.getController() != 'filecontent') {
                        descriptionText += 'Customize this web part to use the pipeline directory by clicking on the ' +
                        '"more" button in the web part title area and selecting the "customize" option. You can then set this ' +
                        'web part to show files from the pipeline directory.<br>' +
                        '<img src="' + LABKEY.contextPath + '/_images/customize-example.png"/>';
                    }
                    else {
                        descriptionText += LABKEY.Utils.textLink({text: "Go To Pipeline Directory", href: LABKEY.ActionURL.buildURL('pipeline', 'browse')});
                    }

                    this.add({
                        border: false,
                        height: 300,
                        padding : '10 0 0 0',
                        html: descriptionText
                    });
                }

                if (Ext4.isObject(pipeActions)) {
                    this.add(this.getActionGrid(pipeActions));
                }
            }
        };

        // request configuration
        File.panel.Browser._getPipelineConfiguration(function(response) {
            this.parseActionConfiguration(response);
            hasConfig = true;
            onReady.call(this);
        }, this.containerPath, this);

        // request actions
        if (this.isPipelineRoot) {
            Ext4.Ajax.request({
                url: LABKEY.ActionURL.buildURL('pipeline', 'actions', this.containerPath, { allActions: true }),
                method: 'GET',
                disableCaching: false,
                success: function(response) {
                    pipeActions = this.getPipelineActions(response);
                    if (!Ext4.isDefined(pipeActions)) {
                        pipeActions = true;
                    }
                    onReady.call(this);
                },
                failure: LABKEY.Utils.displayAjaxErrorResponse,
                scope: this
            });
        }
        else {
            pipeActions = true;
            onReady.call(this);
        }
    },

    parseActionConfiguration : function(response) {

        var o = Ext4.decode(response.responseText);
        var config = o.success ? o.config : {};

        // check whether the import data button is enabled
        Ext4.apply(this, {
            importDataEnabled: config.importDataEnabled === true,
            fileConfig: config.fileConfig ? config.fileConfig : 'useDefault',
            expandFileUpload: config.expandFileUpload === true,
            showFolderTree: config.showFolderTree,
            inheritedTbarConfig: config.inheritedTbarConfig
        });

        if (config.actions) {
            for (var i=0; i < config.actions.length; i++) {
                var action = config.actions[i];
                this.actionConfig[action.id] = action;
            }
        }
    },

    getPipelineActions : function(response) {
         if (!this.isPipelineRoot) return;

        var o = Ext4.decode(response.responseText);
        var actions = o.success ? o.actions : [];

        // parse the response and create the data object
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

        return data;
    },

    getActionGrid : function(data) {

        if (!this.actionGrid) {
            this.actionGrid = Ext4.create('Ext.grid.Panel', {
                store: {
                    storeId: 'actionStore',
                    model: 'File.Model.ActionModel',
                    groupField: 'type',
                    data: data.actions
                },
                columns: [
                    { text: 'Action', dataIndex: 'action', flex: 1 },
                    { text: 'Enabled', dataIndex: 'enabled', xtype : 'checkcolumn', width : 100 },
                    { text: 'Show on Toolbar', dataIndex: 'showOnToolbar', xtype : 'checkcolumn', width : 150 }
                ],
                features: [{
                    ftype: 'grouping',
                    groupHeaderTpl: '{name}', //print the number of items in the group
                    startCollapsed: false // start all groups collapsed
                }],
                height: 400
            });
        }

        return this.actionGrid;
    },

    getActionsForSubmission : function() {
        var adminOptions = [];
        var grid = this.actionGrid;

        //
        // 19122 - If pipeline is configured, yet the browser is still configured to point at the files directory
        // it can cause a configuration where a grid is not supplied.
        //
        if (!this.isPipelineRoot || !grid) {
            return adminOptions;
        }

        var records = grid.getStore() ? grid.getStore().getModifiedRecords() : undefined;

        // pipeline action configuration
        if (!Ext4.isEmpty(records))
        {
            var actionConfig = {},
                actionId,
                config,
                display,
                record;

            for (var i=0; i < records.length; i++)
            {
                record = records[i];
                actionId = record.get('actionId');

                if (record.get('showOnToolbar'))
                    display = 'toolbar';
                else if (record.get('enabled'))
                    display = 'enabled';
                else
                    display = 'disabled';

                config = actionConfig[actionId];
                if (!config)
                {
                    config = {
                        id: actionId,
                        display: 'enabled',
                        label: record.get('type')
                    };
                    actionConfig[actionId] = config;
                }
                if (!config.links)
                    config.links = [];
                config.links.push({
                    id: record.get('id'),
                    display: display,
                    label: record.get('action')
                });
            }

            Ext4.iterate(actionConfig, function(id, config) {
                if (Ext4.isObject(config)) {
                    adminOptions.push({
                        id: i.id,
                        display: i.display,
                        label: i.label,
                        links: i.links
                    });
                }
            });
        }

        return adminOptions;
    },

    getShowImportCheckbox : function() {
        return this.down('#showImportCheckbox');
    },

    isImportDataEnabled : function() {
        var cb = this.getShowImportCheckbox(),
                value = this.importDataEnabled; // default value

        if (cb) {
            value = cb.getValue();
        }

        return value;
    }
});
