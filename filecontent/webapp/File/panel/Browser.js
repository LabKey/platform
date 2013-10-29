/*
 * Copyright (c) 2012-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.requiresCss("_images/icons.css");
LABKEY.requiresCss("dataview/DataViewsPanel.css");

Ext4.define('File.panel.Browser', {

    extend : 'Ext.panel.Panel',

    alias: ['widget.filebrowser'],

    /**
     * @cfg {String} bodyCls
     */
    bodyCls : 'fbody',

    /**
     * @cfg {Boolean} border
     */
    border : true,

    /**
     * @cfg {Boolean} bufferFiles
     */
    bufferFiles : true,

    /**
     * @cfg {Ext4.util.Format.dateRenderer} dateRenderer
     */
    dateRenderer : Ext4.util.Format.dateRenderer("Y-m-d H:i:s"),

    /**
     * @cfg {Boolean} frame
     */
    frame : false,

    /**
     * REQUIRED
     * @cfg {File.system.Abstract} fileSystem
     */
    fileSystem : undefined,

    /**
     * @cfg {Object} gridConfig
     */
    gridConfig : {},

    /**
     * @cfg {Boolean} layout
     */
    layout : 'border',

    /**
     * @cfg {String} cls
     */
    cls : 'fbrowser',

    /**
     * @cfg {Number} minWidth
     */
    minWidth : 650,

    /**
     * @cfg {Boolean} showFolders
     */
    showFolderTree : true,

    /**
     * @cfg {Boolean} expandFolderTree
     */
    expandFolderTree : true,

    /**
     * @cfg {Boolean} expandUpload
     */
    expandUpload : false,

    /**
     * @cfg {Boolean} isWebDav
     */
    isWebDav : false,

    /**
     * @cfg {Boolean} rootName
     */
    rootName : LABKEY.serverName || "LabKey Server",

    /**
     * @cfg {Boolean} showAddressBar
     */
    showAddressBar : true,

    /**
     * @cfg {Boolean} showDetails
     */
    showDetails : true,

    /**
     * @cfg {Boolean} showProperties
     */
    showProperties : false,

    /**
     * @cfg {Boolean} showUpload
     */
    showUpload : true,

    /**
     * An additional set of toolbar configurable items that can be supplied at runtime.
     * @cfg {Array} tbarItems
     */
    tbarItems : [],

    adminUser : false,

    statics : {
        /**
         * Requests the set of actions available for the given containerPath
         * @private
         */
        _getActions : function(cb, containerPath, scope) {
            Ext4.Ajax.request({
                url: LABKEY.ActionURL.buildURL('pipeline', 'actions', containerPath, {path : decodeURIComponent(scope.getFolderOffset()), allActions:true }),
                method: 'GET',
                disableCaching: false,
                success : Ext4.isFunction(cb) ? cb : undefined,
                failure: LABKEY.Utils.displayAjaxErrorResponse,
                scope: scope
            });
        },

        /**
         * This requests the pipeline action configuration from pipeline/getPipelineActionConfig.
         * NOTE: The shape of the returned configuration is as follows:
         * {
         *      expandFileUpload: {boolean}
         *      fileConfig: {string}
         *      gridConfig: {Object}
         *      importDataEnabled: {boolean}
         *      inheritedFileConfig: {boolean/object}
         *      inheritedTbarConfig: {boolean/object}
         *      tbarActions: {Array} - An Array of objects specifying tbar actions currently
         *                              available via the users configuration. NOTE: This is
         *                              not the permissions to show these actions.
         * }
         */
        _getPipelineConfiguration : function(cb, containerPath, scope) {
            var cacheResult = File.panel.Browser._pipelineConfigurationCache[containerPath];

            if (Ext4.isObject(cacheResult)) {
                // cache hit
                Ext4.isFunction(cb) ? cb.call(scope, cacheResult) : undefined
            }
            else if (Ext4.isArray(cacheResult)) {
                // cache miss -- inflight
                File.panel.Browser._pipelineConfigurationCache[containerPath].push({fn: cb, scope: scope});
            }
            else {
                // prep cache
                File.panel.Browser._pipelineConfigurationCache[containerPath] = [{fn: cb, scope: scope}];

                // cache miss
                Ext4.Ajax.request({
                    url: LABKEY.ActionURL.buildURL('pipeline', 'getPipelineActionConfig', containerPath),
                    method: 'GET',
                    disableCaching: false,
                    success : function(response) {

                        var callbacks = File.panel.Browser._pipelineConfigurationCache[containerPath];
                        File.panel.Browser._pipelineConfigurationCache[containerPath] = response;

                        for (var c=0; c < callbacks.length; c++) {
                            Ext4.isFunction(callbacks[c].fn) ? callbacks[c].fn.call(callbacks[c].fn.scope || this, response) : undefined
                        }

                        Ext4.isFunction(cb) ? cb.call(scope, response) : undefined;
                    },
                    failure: LABKEY.Utils.displayAjaxErrorResponse,
                    scope: scope
                });
            }
        },

        _clearPipelineConfiguration : function(containerPath) {
            if (File.panel.Browser._pipelineConfigurationCache[containerPath]) {
                File.panel.Browser._pipelineConfigurationCache[containerPath] = undefined;
            }
        },

        _pipelineConfigurationCache : {},

        _toggleActions : function(actions, selection) {
            var types = File.panel.Browser.actionTypes;
            for (var key in actions) {
                if (actions.hasOwnProperty(key)) {
                    if (!actions[key].initialConfig.actionType) {
                        continue;
                    }

                    var actionType = actions[key].initialConfig.actionType;

                    if (selection == 0) {
                        actions[key].setDisabled(!(actionType === types.NOMIN || actionType === types.NOFILE));
                    }
                    else if (selection == 1) {
                        actions[key].setDisabled(!(types.NOMIN ||  actionType === types.ATLEASTONE || actionType === types.ONLYONE));
                    }
                    else if (selection >= 2) {
                        actions[key].setDisabled(!(types.NOMIN ||  actionType === types.ATLEASTONE || actionType === types.ATLEASTTWO));
                    }
                }
            }
        },

        actionTypes : {
            NOMIN: 'No file required',
            ATLEASTONE: 'At least one file required',
            ONLYONE: 'Only one file allowed',
            ATLEASTTWO: 'Needs at least two files',
            NOFILE: 'Only works with no files'
        }
    },

    constructor : function(config) {

        //
        // Check for required configurations
        //
        if (!config.fileSystem) {
            console.error('File.panel.Browser requires a \'fileSystem\' for initialization.');
        }

        Ext4.QuickTips.init();

        // Clone the config so we don't modify the original config object
        var _config = Ext4.Object.merge({}, config);

        // Set the file system early
        this.fileSystem = _config.fileSystem;

        this.setFolderOffset(this.fileSystem.getOffsetURL());

        this.callParent([_config]);
    },

    initComponent : function() {

        //
        // Configure private flags
        //
        Ext4.apply(this, {
            actionsConfig : [],
            actionGroups: {},
            fileProps: [],
            importDataEnabled: true
        });

        //
        // Tests require a flag element to notify state
        // NOTE: This will not work for multiple browsers on single page
        //
        var testFlag = document.createElement("div");
        testFlag.id = 'testFlag';
        document.body.appendChild(testFlag);

        //
        // Initialize the actions that are available to the current user
        //
        this.createActions();
        this.initializeActions();


        this.items = this.getItems();

        this.initializeToolbar();

        // Attach listeners
        this.on('folderchange', this.onFolderChange, this);
        Ext4.Ajax.timeout = 60000;
        File.panel.Browser._toggleActions(this.actions, 0);
        this.callParent();
    },

    createActions : function() {
        this.actions = {};

        this.actions.parentFolder = new Ext4.Action({
            text: 'Parent Folder',
            itemId: 'parentFolder',
            tooltip: 'Navigate to parent folder',
            iconCls:'iconUp',
            disabledClass:'x-button-disabled',
            handler : this.onNavigateParent,
            actionType : File.panel.Browser.actionTypes.NOMIN,
            scope: this,
            hideText: true
        });

        this.actions.refresh = new Ext4.Action({
            text: 'Refresh',
            itemId: 'refresh',
            tooltip: 'Refresh the contents of the current folder',
            iconCls: 'iconReload',
            disabledClass: 'x-button-disabled',
            handler : this.onRefresh,
            actionType : File.panel.Browser.actionTypes.NOMIN,
            scope: this,
            hideText: true
        });

        this.actions.createDirectory = new Ext4.Action({
            text: 'Create Folder',
            itemId: 'createDirectory',
            iconCls:'iconFolderNew',
            tooltip: 'Create a new folder on the server',
            disabledClass: 'x-button-disabled',
            handler : this.onCreateDirectory,
            actionType : File.panel.Browser.actionTypes.NOMIN,
            scope: this,
            hideText: true
        });

        this.actions.download = new Ext4.Action({
            text: 'Download',
            itemId: 'download',
            tooltip: 'Download the selected files or folders',
            iconCls: 'iconDownload',
            disabledClass: 'x-button-disabled',
            disabled: true,
            handler: this.onDownload,
            actionType : File.panel.Browser.actionTypes.ATLEASTONE,
            scope: this,
            hideText: true
        });
        
        this.actions.deletePath = new Ext4.Action({
            text: 'Delete',
            itemId: 'deletePath',
            tooltip: 'Delete the selected files or folders',
            iconCls: 'iconDelete',
            disabledClass: 'x-button-disabled',
            handler: this.onDelete,
            actionType : File.panel.Browser.actionTypes.ATLEASTONE,
            scope: this,
            disabled: true,
            hideText: true
        });

        this.actions.renamePath = new Ext4.Action({
            text: 'Rename',
            itemId: 'renamePath',
            tooltip: 'Rename the selected file or folder',
            iconCls: 'iconRename',
            disabledClass: 'x-button-disabled',
            handler : this.onRename,
            actionType : File.panel.Browser.actionTypes.ONLYONE,
            scope: this,
            disabled: true,
            hideText: true
        });

        this.actions.movePath = new Ext4.Action({
            text: 'Move',
            itemId: 'movePath',
            tooltip: 'Move the selected file or folder',
            iconCls: 'iconMove',
            disabledClass: 'x-button-disabled',
            handler : this.onMovePath,
            actionType : File.panel.Browser.actionTypes.ATLEASTONE,
            scope: this,
            disabled: true,
            hideText: true
        });

        this.actions.help = new Ext4.Action({
            text: 'Help',
            itemId: 'help',
            scope: this
        });

        this.actions.showHistory = new Ext4.Action({
            text: 'Show History',
            itemId: 'showHistory',
            scope: this
        });

        this.actions.uploadTool = new Ext4.Action({
            text: 'Multi-file Upload',
            itemId: 'uploadTool',
            iconCls: 'iconUpload',
            tooltip: "Upload multiple files or folders using drag-and-drop<br>(requires Java)",
            disabled: true,
            scope: this
        });

        this.actions.upload = new Ext4.Action({
            text: 'Upload Files',
            itemId: 'upload',
            enableToggle: true,
            pressed: this.showUpload && this.expandUpload,
            iconCls: 'iconUpload',
            handler : this.onUpload,
            scope: this,
            disabledClass:'x-button-disabled',
            tooltip: 'Upload files or folders from your local machine to the server'
        });

        this.actions.appletFileAction = new Ext4.Action({
            text: '&nbsp;&nbsp;&nbsp;&nbsp;Choose File&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;',
            itemId: 'appletFileAction',
            scope: this,
            disabled: false,
            cls: 'applet-button'
        });

        this.actions.appletDirAction = new Ext4.Action({
            text: '&nbsp;Choose Folder&nbsp;',
            itemId: 'appletDirAction',
            scope: this,
            disabled: false,
            cls: 'applet-button'
        });

        this.actions.appletDragAndDropAction = new Ext4.Action({
            text: 'Drag and Drop&nbsp;',
            itemId: 'appletDragAndDropAction',
            scope: this,
            disabled: false,
            cls     : 'applet-button'
        });

        this.actions.folderTreeToggle = new Ext4.Action({
            text: 'Toggle Folder Tree',
            itemId: 'folderTreeToggle',
            enableToggle: true,
            iconCls: 'iconFolderTree',
            disabledClass:'x-button-disabled',
            tooltip: 'Show or hide the folder tree',
            hideText: true,
            handler : function() { this.tree.toggleCollapse(); },
            actionType : File.panel.Browser.actionTypes.NOMIN,
            scope: this
        });

        this.actions.importData = new Ext4.Action({
            text: 'Import Data',
            itemId: 'importData',
            handler: this.onImportData,
            iconCls: 'iconDBCommit',
            disabledClass:'x-button-disabled',
            tooltip: 'Import data from files into the database, or analyze data files',
            actionType : File.panel.Browser.actionTypes.NOMIN,
            scope: this
        });

        this.actions.customize = new Ext4.Action({
            text: 'Admin',
            itemId: 'customize',
            iconCls: 'iconConfigure',
            disabledClass:'x-button-disabled',
            tooltip: 'Configure the buttons shown on the toolbar',
            actionType : File.panel.Browser.actionTypes.NOMIN,
            handler: this.showAdminWindow,
            scope: this
        });

        this.actions.editFileProps = new Ext4.Action({
            text: 'Edit Properties',
            itemId: 'editFileProps',
            iconCls: 'iconEditFileProps',
            disabledClass:'x-button-disabled',
            tooltip: 'Edit properties on the selected file(s)',
            actionType : File.panel.Browser.actionTypes.ATLEASTONE,
            handler : this.onEditFileProps,
            disabled : true,
            hideText: true,
            scope: this
        });

        this.actions.emailPreferences = new Ext4.Action({
            text: 'Email Preferences',
            itemId: 'emailPreferences',
            iconCls: 'iconEmailSettings',
            disabledClass:'x-button-disabled',
            tooltip: 'Configure email notifications on file actions.',
            hideText: true,
            actionType : File.panel.Browser.actionTypes.NOMIN,
            handler : this.onEmailPreferences,
            scope: this
        });

        this.actions.auditLog = new Ext4.Action({
            text: 'Audit History',
            itemId: 'auditLog',
            iconCls: 'iconAuditLog',
            disabledClass:'x-button-disabled',
            tooltip: 'View the files audit log for this folder.',
            actionType : File.panel.Browser.actionTypes.NOMIN,
            handler : function() {
                window.location = LABKEY.ActionURL.buildURL('filecontent', 'showFilesHistory', this.containerPath);
            },
            scope: this
        });
    },

    initializeActions : function() {
        var action, a;
        for (a in this.actions) {
            if (this.actions.hasOwnProperty(a)) {
                action = this.actions[a];
                if (action && action.isAction) {
                    action.initialConfig.prevText = action.initialConfig.text;
                    action.initialConfig.prevIconCls = action.initialConfig.iconCls;

                    if (action.initialConfig.hideText) {
                        action.setText(undefined);
                    }

                    if (action.initialConfig.hideIcon) {
                        action.setIconClass(undefined);
                    }
                }
            }
        }
    },

    initializeToolbar : function() {
        if (this.isWebDav) {
            this.tbar = this.getWebDavToolbarItems();
        }
        else {
            this.configureActions();
        }
    },

    configureActions : function() {
        var configure = function(response) {
            var json = Ext4.JSON.decode(response.responseText);
            if (json.config) {
                this.updateActions();
                // First intialize all the actions prepping them to be shown

                // Configure the actions on the toolbar based on whether they should be shown
                this.configureTbarActions({tbarActions: json.config.tbarActions, actions: json.config.actions});
            }
        };
        File.panel.Browser._clearPipelineConfiguration(this.containerPath);
        File.panel.Browser._getPipelineConfiguration(configure, this.containerPath, this);
    },

    configureTbarActions : function(config) {
        if(Ext4.isArray(config))
            config = config[0];
        var tbarConfig = config.tbarActions;
        var actionButtons = config.actions;
        var toolbar = this.getDockedItems()[0];
        var buttons = [], i, action;

        if (toolbar) {
            // Remove the current toolbar incase we just customized it.
            this.removeDocked(toolbar);
        }

        // Use as a lookup
        var mapTbarItems = {};

        if (this.tbarItems) {
            for (i=0; i < this.tbarItems.length; i++) {
                mapTbarItems[this.tbarItems[i]] = true;
            }
        }

        if (tbarConfig) {
            var actionConfig;

            // Iterate across tbarConfig as button ordering is determined by array order
            for (i=0; i < tbarConfig.length; i++) {

                // check map to ensure that we should process this action
                if (mapTbarItems[tbarConfig[i].id]) {
                    actionConfig = tbarConfig[i];
                    action = this.actions[actionConfig.id];
                    action.initialConfig.hideText = actionConfig.hideText;
                    action.initialConfig.hideIcon = actionConfig.hideIcon;

                    if(action.initialConfig.hideText && action.initialConfig.text != undefined)
                    {
                        action.initialConfig.prevText = action.initialConfig.text;
                        action.initialConfig.text = undefined;
                    }
                    else if(!action.initialConfig.hideText && action.initialConfig.text == undefined)
                    {
                        action.initialConfig.text = action.initialConfig.prevText;
                    }

//                    // TODO: Why special processing?
//                    if (actionConfig.id == 'customize') {
//                        action.setDisabled(this.disableGeneralAdminSettings);
//                    }

                    buttons.push(action);
                }
            }
        }
        else if (this.tbarItems) {

            for (i=0; i < this.tbarItems.length; i++) {
                action = this.actions[this.tbarItems[i]];

//                // TODO: Why special processing?
//                if (this.tbarItems[i] == 'customize') {
//                    action.setDisabled(this.disableGeneralAdminSettings);
//                }

                buttons.push(action);
            }
        }

        if (actionButtons) {
            for (i=0; i < actionButtons.length; i++) {
                if (actionButtons[i].links[0].display === 'toolbar') {
                    var action = new Ext4.Action({
                        id : actionButtons[i].links[0].id,
                        itemId : actionButtons[i].links[0].id,
                        text : actionButtons[i].links[0].label,
                        handler: this.executeToolbarAction,
                        scope : this,
                        disabled : true
                    });
                    buttons.push(action);
                }
            }
        }

        this.addDocked({xtype: 'toolbar', dock: 'top', items: buttons, enableOverflow : true});
    },

    executeToolbarAction : function(item, e)
    {
        var action = this.actionMap[item.itemId];

        if (action)
            this.executeImportAction(action);
    },

    initGridColumns : function() {

        var columns = [{
            xtype : 'templatecolumn',
            text  : '',
            dataIndex : 'icon',
            sortable : true,
            width : 25,
            height : 20,
            tpl : '<img height="16px" width="16px" src="{icon}" alt="{type}">',
            scope : this
        },{
            xtype : 'templatecolumn',
            text  : 'Name',
            dataIndex : 'name',
            sortable : true,
            height : 20,
            minWidth : 188,
            flex : 3,
            tpl : '<div height="16px" width="100%">' +
                        '<div style="float: left;"></div>' +
                        '<div style="padding-left: 8px; white-space:normal !important;">' +
                            '<span style="display: inline-block;">{name:htmlEncode}</span>' +
                        '</div>' +
                   '</div>',
            scope : this
        },
        {header: "Last Modified",  flex: 1, dataIndex: 'lastmodified', sortable: true,  hidden: false, height : 20, renderer: this.dateRenderer},
        {header: "Size",           flex: 1, dataIndex: 'size',         sortable: true,  hidden: false, height : 20, renderer:Ext4.util.Format.fileSize, align : 'right'},
        {header: "Created By",     flex: 1, dataIndex: 'createdby',    sortable: true,  hidden: false, height : 20, renderer:Ext4.util.Format.htmlEncode},
        {header: "Description",    flex: 1, dataIndex: 'description',  sortable: true,  hidden: false, height : 20, renderer:Ext4.util.Format.htmlEncode},
        {header: "Usages",         flex: 1, dataIndex: 'actionHref',   sortable: true,  hidden: false, height : 20},// renderer:LABKEY.FileSystem.Util.renderUsage},
        {header: "Download Link",  flex: 1, dataIndex: 'fileLink',     sortable: true,  hidden: true, height : 20},
        {header: "File Extension", flex: 1, dataIndex: 'fileExt',      sortable: true,  hidden: true, height : 20, renderer:Ext4.util.Format.htmlEncode}
        ];
        this.setDefaultColumns(columns);
        File.panel.Browser._getPipelineConfiguration(this._onExtraColumns, this.containerPath, this);

        return columns;
    },

    _onExtraColumns: function(response) {
        var extraColumns = [];
        var columns = this.getDefaultColumns();
        var json = Ext4.JSON.decode(response.responseText);
        if (json && json.config && json.config.gridConfig) {
            var customColumns = json.fileProperties;
            json = json.config.gridConfig.columns;
            var finalColumns = [columns[0]], i= 0, g = this.getGrid(), customCol;
            for (; i < json.length; i++) {
                if(columns[json[i].id])
                    columns[json[i].id].hidden = json[i].hidden;
                else
                {
                    var index = json[i].id-9;
                    if(customColumns[index])
                    {
                        customCol = {
                            header : customColumns[index].label,
                            flex : 1,
                            dataIndex : customColumns[index].name,
                            height : 20,
                            hidden : json[i].hidden
                        };
                        columns.push(customCol);
                        extraColumns.push(customCol);
                    }

                }
                finalColumns[i] = columns[json[i].id-1];
            }
            //Unmodified extra columns do not appear in the json data, and therefore need to be added manually.
            //They are always the last items on the custom column list because they appear at the back.
            if(9 + customColumns.length > columns.length)
            {
                var offset = customColumns.length - ((9 + customColumns.length ) - columns.length);
                for(; offset < customColumns.length; offset++)
                {
                    customCol = {
                        header : customColumns[offset].label,
                        flex : 1,
                        dataIndex : customColumns[offset].name,
                        height : 20,
                        hidden : false
                    }
                }
                finalColumns.push(customCol);
                extraColumns.push(customCol);
            }
        }
        this.setDefaultColumns(finalColumns);
        this.setExtraColumns(extraColumns);
    },

    setFileObjects : function(fileObjects) {
        this.fileObjects = fileObjects;
    },

    getFileObjects : function() {
        if (this.fileObjects) {
            return this.fileObjects.slice(0);
        }
    },

    setExtraColumns : function(extraColumns) {
        this.extraColumns = extraColumns;
        this.fireEvent('columnchange', this.onColumnChange(this.getColumns()));
    },

    getExtraColumns : function() {
        if(this.extraColumns)
            return this.extraColumns.slice(0);
        else
            return [];
    },

    getExtraColumnNames : function()
    {
        var columns = this.getExtraColumns();
        var retCols = [];
        if(columns)
        {
            for(var i = 0; i < columns.length; i++)
            {
                retCols.push(columns[i].dataIndex);
            }
        }
        return retCols;
    },

    setDefaultColumns : function(defaultColumns) {
        this.defaultColumns = defaultColumns;
    },

    getDefaultColumns : function() {
        if(this.defaultColumns)
            return this.defaultColumns.splice(0);
    },

    getColumns : function() {
        return this.getDefaultColumns();
    },

    onColumnChange : function(allTheColumns) {
        this.getGrid().reconfigure(this.getGrid().getStore(), allTheColumns); // REMEMBER: Check if passing in the store fires reload
    },

    getItems : function() {
        var items = [this.getGridCfg()];

        if (this.showFolderTree) {
            items.push(this.getFolderTreeCfg());
            this.on('folderchange', this.expandPath, this);
        }

        if (this.showUpload) {
            items.push(this.getUploadPanel());
        }

        if (this.showDetails) {
            items.push(this.getDetailPanel());
        }

        return items;
    },

    getFileStore : function() {

        if (this.fileStore) {
            return this.fileStore;
        }

        var storeConfig = {
            model : this.fileSystem.getModel(),
            proxy : this.fileSystem.getProxyCfg('json')
        };

        if (this.bufferFiles) {
            Ext4.apply(storeConfig, {
                remoteSort : true,
                buffered : true,
                leadingBufferZone : 300,
                pageSize : 200,
                purgePageCount : 0
            });
        }

        Ext4.apply(storeConfig.proxy.extraParams, {
            paging : this.bufferFiles
        });

        this.fileStore = Ext4.create('Ext.data.Store', storeConfig);

        this.on('folderchange', this.updateGridProxy, this);

        // 'load' only works on non-buffered stores
        this.fileStore.on(this.bufferFiles ? 'prefetch' : 'load', function() {
            this.gridMask.cancel();
            if (this.grid) {
                this.getGrid().getEl().unmask();
            }
            this.attachCustomFileProperties();
        }, this);

        return this.fileStore;
    },


    attachCustomFileProperties : function(){
        //Copied array so as not to be include name/Row Id in extra columns
        var extraColumns = this.getExtraColumnNames();

        var config = {
            schemaName : 'exp',
            queryName : 'Data',
            columns : ['Name', 'Run', 'Data File URL'].concat(extraColumns),
            success : function(resp) {
                resp = resp.rows;
                this.setFileObjects(resp);

                var i, r, propName, rec, idx;

                for (i = 0; i < resp.length; i++) {
                    idx = this.fileStore.findExact('name', resp[i].Name);
                    if (idx >= 0) {
                        rec = this.fileStore.getAt(idx);
                        propName = this.fileProps[rec.get('name')];
                        if (!propName) {
                            propName = {};
                        }
                        for (r = 0; r < extraColumns.length; r++) {
                            rec.set(extraColumns[r], resp[i][extraColumns[r]]);
                            propName[extraColumns[r]] = resp[i][extraColumns[r]];
                        }
                        propName.rowId = resp[i]['RowId'];
                        propName.name = resp[i]['Name'];
                        this.fileProps[rec.get('name')] = propName;
                    }
                }
            },
            scope : this
        };

        LABKEY.Query.selectRows(config);
    },

    updateGridProxy : function() {
        if (!this.gridTask) {
            this.gridTask = new Ext4.util.DelayedTask(function() {
                this.fileStore.getProxy().url = LABKEY.ActionURL.encodePath(this.getFolderURL());
                this.gridMask.delay(250);
                this.fileStore.load();
            }, this);
        }
        this.gridTask.delay(50);
    },

    getBaseURL : function() {
        return this.fileSystem.getBaseURL();
    },

    getFolderURL : function() {
        return this.fileSystem.concatPaths(this.fileSystem.getContextBaseURL(), this.getFolderOffset());
    },

    getFolderOffset : function() {
        return this.rootOffset;
    },

    getCurrentDirectory : function() {
        return this.fileSystem.concatPaths(this.fileSystem.getBaseURL(), this.getFolderOffset());
    },

    setFolderOffset : function(offsetPath, model) {

        var path = offsetPath;

        // Replace the base URL so only offsets are used
        path = path.replace(this.fileSystem.getBaseURL(), '/');
        // If we don't go anywhere, don't fire a folder change
        if (this.rootOffset != path) {
            this.rootOffset = path;
            this.fireEvent('folderchange', path, model, offsetPath);
        }
        //If our offset has changed, we need to update our actions with the current path
        this.updateActions();
    },

    getFolderTreeStoreCfg : function(configs) {

        var storeCfg = {};

        Ext4.apply(storeCfg, configs, {
            model : this.fileSystem.getModel('xml'),
            proxy : this.fileSystem.getProxyCfg('xml'),
            root : {
                text : this.fileSystem.rootName,
                id : this.fileSystem.getBaseURL(),
                expanded : true,
                cls : 'fileset-root-node', // for selenium testing
                icon : LABKEY.contextPath + '/_images/labkey.png'
            }
        });

        return storeCfg;
    },

    onShowHidden : function(s, node) {
        if (node && node.data && Ext4.isString(node.data.name)) {
            if (node.data.name.indexOf('.') == 0) {
                return false;
            }
        }
    },

    getFolderTreeCfg : function(configs, skipStore) {

        if (!skipStore) {

            var listeners = {
                load : {
                    fn : function(s) {
                        this.ensureVisible(this.fileSystem.getOffsetURL());
                        this.onRefresh();
                    },
                    single: true
                },
                scope: this
            };

            if (!this.showHidden) {
                listeners['beforeappend'] = this.onShowHidden;
            }

            var store = Ext4.create('Ext.data.TreeStore', this.getFolderTreeStoreCfg({listeners: listeners}));
        }

        return Ext4.apply({}, configs, {
            xtype : 'treepanel',
            itemId : 'treenav',
            id : 'treeNav',
            region : 'west',
            cls : 'themed-panel',
            flex : 1,
            store : store,
            collapsed: !this.expandFolderTree,
            collapsible : true,
            collapseMode : 'mini',
            split : true,
            useArrows : true,
            listeners : {
                beforerender : function(t) { this.tree = t; },
                select : this.onTreeSelect,
                scope : this
            }
        });
    },

    /**
     * Helper method to ensure that the ID is wrapped by '/' on either side. Tree nodes require this.
     * @param id
     * @returns {String}
     */
    ensureNodeId : function(id) {
        var _id = id, sep = '/';
        if (_id[0] != sep)
            _id = sep + _id;
        if (_id[_id.length-1] != sep)
            _id += sep;
        return _id;
    },

    ensureVisible : function(id) {

        var nodeId = this.ensureNodeId(id);

        if (!this.vStack) {
            this.vStack = [];
        }
        var node = this.tree.getView().getTreeStore().getRootNode().findChild('id', nodeId, true);
        if (!node) {
            var p = this.fileSystem.getParentPath(nodeId);
            if (p == '/') {
                return;
            }
            if (nodeId.length > 0 && nodeId.substring(nodeId.length-1) != '/') {
                nodeId += '/';
            }
            this.vStack.push(nodeId);
            this.ensureVisible(p);
        }
        else {
            if (!node.isLeaf()) {
                var s = this.vStack.pop();
                var fn = s ? function() { this.ensureVisible(s);  } : undefined;
                if (!s) {
                    this.setFolderOffset(node.data.id, node);
                    this.tree.getSelectionModel().select(node, false, false);
                }
                node.expand(false, fn, this);
            }
        }
    },

    expandPath : function(p, model) {
        var path = this.ensureNodeId(model.data.id);
        var idx = this.tree.getView().getStore().find('id', path);
        if (idx) {
            var rec = this.tree.getView().getStore().getAt(idx);
            if (rec) {
                this.tree.getView().expand(rec);
                this.tree.getSelectionModel().select(rec, false, true);
            }
        }
    },

    onTreeSelect : function(selModel, rec) {
        this.changeFolder(rec);
        this.tree.getView().expand(rec);
    },

    getGrid : function() {
        if (this.grid) {
            return this.grid;
        }
    },

    /**
     * updateActions will request the set of actions available from both the server and the pipeline
     */
    updateActions : function()
    {
        if (this.isPipelineRoot)
        {
            File.panel.Browser._clearPipelineConfiguration(this.containerPath);
            var actionsReady = false;
            var pipelineReady = false;

            var actions;
            var me = this;

            var check = function() {
                if (actionsReady && pipelineReady)
                    me.updatePipelineActions(actions);
            };

            var actionCb = function(response) {
                var o = Ext4.decode(response.responseText);
                actions = o.success ? o.actions : [];
                actionsReady = true;
                check();
            };

            var pipeCb = function(response) {
                me.configureActionConfigs(response);
                pipelineReady = true;
                check();
            };

            File.panel.Browser._getActions(actionCb, this.containerPath, this);
            File.panel.Browser._getPipelineConfiguration(pipeCb, this.containerPath, this);
        }
    },

    // worst named method ever
    configureActionConfigs : function(response) {
        var resp = Ext4.decode(response.responseText);

        if (resp.config.actions) {
            this.importDataEnabled = resp.config.importDataEnabled ? resp.config.importDataEnabled : false;
            var actionConfigs = resp.config.actions;

            for (var i=0; i < actionConfigs.length; i++) {
                this.actionsConfig[actionConfigs[i].id] = actionConfigs[i];
            }
        }

        this.actions.importData[(!this.importDataEnabled && !this.adminUser ? 'hide' : 'show')]();
    },

    updatePipelineActions : function(actions) {
        this.pipelineActions = [];
        this.actionMap = {};
        this.actionGroups = {};
        this.fileMap = {};

        if (actions && actions.length && (this.importDataEnabled || this.adminUser)) {
            var pipelineActions = this.parseActions(actions), pa;
            for (var i=0; i < pipelineActions.length; i++) {
                if (!pipelineActions[i].link)
                    continue;

                pa = pipelineActions[i];

                var config = this.actionsConfig[pa.groupId];
                if (pa.link.href)
                {
                    pa.enabled = config ? (config.links[0].display === 'enabled') : true;
                }

                this.pipelineActions.push(pa);
                this.actionMap[pa.id] = pa;

                if (pa.groupId in this.actionGroups)
                    this.actionGroups[pa.groupId].actions.push(pa);
                else
                    this.actionGroups[pa.groupId] = {label: pa.groupLabel, actions: [pa]};

                // Populate this.fileMap
                for (var f=0; f < pa.files.length; f++) {
                    if (!this.fileMap[pa.files[f]]) {
                        this.fileMap[pa.files[f]] = {};
                    }
                    this.fileMap[pa.files[f]][pa.groupId] = 1;
                }
            }
        }

        this.updateActionButtons();
    },

    //TODO: Button logic should work for multiple file selection (MFS)
    updateActionButtons : function() {
        var selection = this.getGrid().getSelectionModel().getSelection();
        for (var key in this.actionMap) {

            if (!this.actionMap.hasOwnProperty(key) || !Ext4.getCmp(key)) {
                continue;
            }
            else if (!this.actionMap[key].multiSelect && selection.length > 1) {
                Ext4.getCmp(key).setDisabled(true);
                continue;
            }
            else if(selection.length == 0) {
                Ext4.getCmp(key).setDisabled(true);
                continue;
            }

            var disabled = true;
            for (var i = 0; i < this.actionMap[key].files.length; i++) {
                if (this.selectedRecord.data.name === this.actionMap[key].files[i]) {
                    disabled = false;
                    break;
                }
            }
            Ext4.getCmp(key).setDisabled(disabled);
        }
    },

    isValidFileCheck : function(actionFiles, filenames) {
        var valid = 0;
        for (var i = 0; i < actionFiles.length; i++) {
            for (var r = 0; r < filenames.length; r++) {
                if (actionFiles[i] === filenames[r]) {
                    valid++;
                }
            }
        }
        return valid;
    },

    getFileNames : function()
    {
        var files = [];
        var selection = this.getGrid().getSelectionModel().getSelection();

        //
        // If no selections are active then we assume they are trying to import the current directory
        // so we grab all the files
        //
        if (selection.length == 0) {
            var store = this.getGrid().getStore();
            selection = this.getGrid().getStore().getRange(0, store.getCount());
            files.push(this.fileSystem.getDirectoryName(this.getCurrentDirectory()));
        }

        for (var i = 0; i < selection.length; i++) {
            files.push(selection[i].data.name);
        }
        return files;
    },

    onImportData : function() {
        var actionMap = [],
                fileNames = this.getFileNames(),
                actions = [],
                validFiles,
                items   = [],
                alreadyChecked = false, // Whether we've already found an enabled action to make the default selection
                hasAdmin = false, pa, shrink, shortMessage, longMessage;

        for (var ag in this.actionGroups)
        {
            //If you somehow managed to click this button when there was no data selected, flag a warning.
            //THIS SHOULD PROBABLY NEVER APPEAR.  Button should disable like all the others if no viable data is selected.
//            if (!this.selectedRecord) {
//                console.warn('No record selected for data import');
//                break;
//            }

            var group = this.actionGroups[ag];
            pa = group.actions[0];

            var bad = 0, badFiles = [], goodActions = [];
            var potential = false;
            var invalidMultiselect = false;

            //Check to see if any file is applicable to this action (which is a member of an action group)
            for(var i=0; i < group.actions.length; i++){
                //If no files can be used for this action, it is marked as bad
                if(group.actions[i].files.length <= 0)
                {
                    bad++;
                    goodActions[i] = false;
                    continue;
                }
                //Flag if action is not compatible with multiselect and user has more than one file selected
                else if(!group.actions[i].multiSelect && fileNames.length > 1)
                {
                    bad++;
                    goodActions[i] = false;
                    invalidMultiselect = true;
                }
                //Otherwise, checks if the currently selected files can be used for this action.  If none, pushes the list of files which WOULD work for the tooltip.
                validFiles = this.isValidFileCheck(group.actions[i].files, fileNames);
                if(validFiles <= 0)
                {
                    badFiles.push(group.actions[i].files);
                }
                else if(validFiles == 1 && invalidMultiselect == true)
                {
                    goodActions[i] = true;
                }

                if(goodActions[i] != false)
                {
                    goodActions[i] = true;
                }
            }

            //If all the actions in this group are bad, ignore the rest of the loop
            if(bad == group.actions.length && !invalidMultiselect)
                continue;
            //Otherwise, if no files were valid, but there were possible files, we toggle a boolean to indicate that state.
            else if((bad + badFiles.length == group.actions.length) && !invalidMultiselect)
            {
                potential = true;

                //Goes through and collects all the unselected (but potentially usable) files into a single array
                badFiles['fileset'] = {};
                for(var i = 0; i < badFiles.length; i++)
                {
                    for(var j = 0; j < badFiles[i].length; j++)
                    {
                        badFiles['fileset'][badFiles[i][j]] = true;
                    }
                }
            }

            //Toggling messages for if we had potential files vs. actual files
            if(potential)
            {
                shortMessage = group.label + '<br>' + '<span style="margin-left:5px;" class="labkey-mv">None of the selected files can be used</span>';
                longMessage = 'The following files could be used for this action: <br>'
                for(var key in badFiles['fileset'])
                {
                    longMessage += key + '<br>';
                }
            }
            else if(invalidMultiselect)
            {
                shortMessage = group.label + '<br>' + '<span style="margin-left:5px;" class="labkey-mv">This action can only opperate on one file at a time</span>';
                longMessage = 'The following files could be used for this action: <br>'
                for(var key in badFiles['fileset'])
                {
                    longMessage += key + '<br>';
                }
            }
            else
            {
                shortMessage = group.label + '<br>' + '<span style="margin-left:5px;" class="labkey-mv"> using ' + validFiles + ' of ' + fileNames.length + ' files</span>';
//                longMessage = 'This action will use the selected file: <br>' + this.selectedRecord.data.name;
            }

            var radioGroup = Ext4.create('Ext.form.RadioGroup', {
                fieldLabel : shortMessage,
                labelWidth : 250,
                itemCls    : 'x-check-group',
                columns    : 1,
                labelSeparator: '',
                items      : [],
                scope : this,
                tooltip : longMessage,
                listeners : {
                    render : function(rg){
                        rg.setHeight(rg.getHeight()+10);
                        (!potential && !invalidMultiselect) ? this.setFormFieldTooltip(rg, 'info.png') : this.setFormFieldTooltip(rg, 'warning-icon-alt.png');
                    },
                    scope : this
                }
            });

            var radios = [];

            //Generate the radio buttons for each action group
            for (var i=0; i < group.actions.length; i++)
            {
                var action = group.actions[i];
                if (action.link.href && (action.enabled || this.adminUser))
                {
                    var label = action.link.text;

                    // administrators always see all actions
                    if (!action.enabled && this.adminUser)
                    {
                        label = label.concat(' <span class="labkey-error">*</span>');
                        hasAdmin = true;
                    }

                    actionMap[action.id] = action;
                    radios.push({
                        xtype: 'radio',
                        checked: action.enabled && !alreadyChecked,
                        labelSeparator: '',
                        boxLabel: label,
                        name: 'importAction',
                        inputValue: action.id,
                        disabled : !goodActions[i]
                    });

                    if (action.enabled)
                    {
                        alreadyChecked = true;
                    }
                }
            }
            actions.push({
                xtype: 'radiogroup',
                fieldLabel : shortMessage,
                labelWidth : 275,
                showAsWarning: potential || invalidMultiselect,
                border: '0 0 1 0',
                style: 'border-bottom: 1px dashed lightgray',
                columns    : 1,
                labelSeparator: '',
                items: radios,
                scope : this,
                tooltip : longMessage,
                listeners : {
                    render : function(rg) {
                        rg.setHeight(rg.getHeight()+10);
                        this.setFormFieldTooltip(rg, (rg.showAsWarning ? 'warning-icon-alt.png' : 'info.png'));
                    },
                    scope : this
                }
            });
        }

        var actionPanel = Ext4.create('Ext.form.FormPanel', {
            bodyStyle   : 'padding:10px;',
            labelWidth  : 250,
            defaultType : 'radio',
            items       : actions
        });
        items.push(actionPanel);

        if (hasAdmin)
        {
            items.push({
                html      : 'Actions marked with an asterisk <span class="labkey-error">*</span> are only visible to Administrators.',
                bodyStyle : 'padding:10px;',
                border    : false
            });
        }

        if (!this.importDataEnabled  && !this.adminUser)
        {
            items.push({
                html      : 'This dialog has been disabled from the admin panel and is only visible to Administrators.',
                bodyStyle : 'padding:10px;',
                border    : false
            });
            shrink = true;
        }
//        else if (!this.selectedRecord)
//        {
//            items.push({
//                html      : 'No files selected to process.',
//                bodyStyle : 'padding:10px;',
//                border    : false
//            });
//            shrink = true;
//        }
        else if (!radioGroup)
        {
            items.push({
                //TODO:  MFS
                html      : 'There are no actions capable of processing ' + this.selectedRecord.data.name,
                bodyStyle : 'padding:10px;',
                border    : false
            });
            shrink = true;
        }
        var buttons = [];
        if(!shrink)
        {
            buttons = [{
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
            }];
        }
        else
        {
            buttons = [{
                text: 'Cancel',
                id: 'btn_cancel',
                handler: function(){win.close();}
            }];
        }

        var win = Ext4.create('Ext.Window', {
            title: 'Import Data',
            cls: 'data-window',
            width: shrink ? 300 : 725,
            height: shrink ? 150 : undefined,
            autoShow: true,
            autoScroll: true,
            modal: true,
            items: items,
            buttons: buttons
        });
    },

    parseActions : function(actions) {

        var pipelineActions = [];
        if (actions && actions.length)
        {
            for (var i=0; i < actions.length; i++)
            {
                var action = actions[i];
                var config = {
                    files: action.files,
                    groupId: action.links.id,
                    groupLabel: action.links.text,
                    multiSelect: action.multiSelect,
                    emptySelect: action.emptySelect,
                    description: action.description
                };

                // only a single target for the action (no submenus)
                if (!action.links.items && action.links.text && action.links.href)
                {
                    config.id = action.links.id;
                    config.link = {text: action.links.text, id: action.links.id, href: action.links.href};

                    pipelineActions.push(config);
                }
                else
                {
                    for (var j=0; j < action.links.items.length; j++)
                    {
                        var item = action.links.items[j];

                        config.id = item.id;
                        config.link = item;

                        pipelineActions.push(config);
                    }
                }
            }
        }
        return pipelineActions;
    },

    submitForm : function(panel, actionMap) {
        // client side validation
        var selection = panel.getForm().getValues();
        var action = actionMap[selection.importAction];

        if (Ext4.isObject(action)) {
            this.executeImportAction(action);
        }
    },

    executeImportAction : function(action)
    {
        if (action)
        {
            var selections = this.getGrid().getSelectionModel().getSelection(), i;
            var link = action.link;

            // if there are no selections, treat as if all are selected
            if (selections.length == 0)
            {
                selections = [];
                var store = this.grid.getStore();

                for (i=0; i <store.getCount(); i++)
                {
                    selections.push(store.getAt(i));
                }
            }

            if (link && link.href)
            {
                if (selections.length == 0)
                {
                    Ext4.Msg.alert("Execute Action", "There are no files selected");
                    return false;
                }

                var form = document.createElement("form");
                form.setAttribute("method", "post");
                form.setAttribute("action", link.href);

                for (i=0; i < selections.length; i++)
                {
                    var files = action.files;
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
        }
    },

    onSelection : function(g, selectedRecords) {
        this.changeTestFlag(false, false);

        if (this.showDetails) {
            if (selectedRecords.length == 1)
                this.getDetailPanel().update(selectedRecords[0].data);
            else
                this.getDetailPanel().update('');
        }

        this.fireEvent('selectionchange', File.panel.Browser._toggleActions(this.actions, selectedRecords.length));

        this.selectedRecord = selectedRecords[0];
        this.changeTestFlag(true, false);
        this.updateActionButtons();
        this.changeTestFlag(true, true);
    },

    getGridCfg : function() {
        var config = Ext4.Object.merge({}, this.gridConfig);

        // Optional Configurations
        Ext4.applyIf(config, {
            flex : 4,
            region : 'center',
            border: false,
            style : 'border-top: 1px solid #b4b4b4;',
            viewConfig : {
                emptyText : '<span style="margin-left: 5px; opacity: 0.3;"><i>No Files Found</i></span>'
            }
        });

        if (!config.selModel && !config.selType) {
            config.selModel = {
                selType: 'checkboxmodel',
                mode: 'MULTI',
                pruneRemoved: false // designated 'private' on Ext.selection.Model in Ext 4.1.0
            };
        }

        Ext4.apply(config, {
            xtype   : 'grid',
            cls     : 'labkey-filecontent-grid',
            store   : this.getFileStore(),
            columns : this.initGridColumns(),
            listeners : {
                beforerender : function(g) { this.grid = g; },
                selectionchange : this.onSelection,
                itemdblclick : function(g, rec) {
                    if (rec && rec.data && rec.data.collection) {
                        this.changeFolder(rec);
                    }
//                    else {
//                        // Download the file
//                        this.onDownload({recs : [rec]});
//                    }
                },
                afterrender : function() {
                    this.changeTestFlag(true, true);
                },
                scope : this
            }
        });

        this.gridMask = new Ext4.util.DelayedTask(function() {
            this.getGrid().getEl().mask('Loading...');
        }, this);

        return config;
    },

    /**
     * Call this whenever you want to change folders under the root.
     * @param model - the record whose 'id' is the valid path
     */
    changeFolder : function(model) {
        var url = model.data.id;
        this.setFolderOffset(url, model);
    },

    getWebDavToolbarItems : function() {
        var baseItems = [];

        if (this.showFolderTree) {
            baseItems.push(this.actions.folderTreeToggle);
        }

        baseItems.push(
                this.actions.parentFolder,
                this.actions.refresh,
                this.actions.createDirectory,
                this.actions.download,
                this.actions.deletePath,
                this.actions.upload
        );

        if (Ext4.isArray(this.tbarItems)) {
            for (var i=0; i < this.tbarItems.length; i++) {
                baseItems.push(this.tbarItems[i]);
            }
        }

        return baseItems;
    },


    onFolderChange : function(path, model) {
        this.changeTestFlag(false, false);
        var tb = this.getDockedComponent(0), action;
        if (tb) {
            action = tb.getComponent('deletePath');
            if (action)
                action.setDisabled(!this.fileSystem.canDelete(model)); // TODO: Check grid selection

            action = tb.getComponent('createDirectory');
            if (action)
                action.setDisabled(!this.fileSystem.canMkdir(model));

            action = tb.getComponent('upload');
            if (action)
                action.setDisabled(!this.fileSystem.canWrite(model));

            if (this.actions.download) {
                this.actions.download.setDisabled(!this.fileSystem.canRead(model));
            }
            if (this.actions.renamePath) {
                this.actions.renamePath.setDisabled(true);
            }
            if (this.actions.movePath) {
                this.actions.movePath.setDisabled(true);
            }
            if (this.actions.deletePath) {
                this.actions.deletePath.setDisabled(true);
            }
        }
        this.getGrid().getSelectionModel().deselectAll();
        this.fireEvent('selectionchange', File.panel.Browser._toggleActions(this.actions, 0));
        this.currentDirectory = model;
        this.changeTestFlag(true, true);
    },

    // calling select does not fire 'selectionchange'
    clearGridSelection : function() {
        this.getGrid().getSelectionModel().select([]);
        this.onSelection(this.getGrid(), []);
    },

    getUploadPanel : function() {
        if (this.uploadPanel) {
            return this.uploadPanel;
        }

        this.uploadPanel = Ext4.create('File.panel.Upload', {
            region : 'north',
            header : false,
            hidden : !this.expandUpload,
            fileSystem : this.fileSystem,
            listeners : {
                transfercomplete : function() {
                    this.getFileStore().load();
                },
                render : {
                    fn : function(up) {
                        up.changeWorkingDirectory(this.getFolderOffset(), null, this.getCurrentDirectory());
                    },
                    single: true
                },
                scope : this
            }
        });

        // link upload panel to know when directory changes
        this.on('folderchange', this.uploadPanel.changeWorkingDirectory, this.uploadPanel);

        return this.uploadPanel;
    },

    getDetailPanel : function() {
        if (this.details)
            return this.details;

        var detailsTpl = new Ext4.XTemplate(
           '<table class="fb-details">' +
                '<tr><th>Name:</th><td>{name}</td></tr>' +
                '<tr><th>WebDav URL:</th><td><a target="_blank" href="{href}">{href}</a></td></tr>' +
                '<tpl if="lastmodified != undefined">' +
                    '<tr><th>Modified:</th><td>{lastmodified:this.renderDate}</td></tr>' +
                '</tpl>' +
                '<tpl if="createdby != undefined && createdby.length">' +
                    '<tr><th>Created By:</th><td>{createdby}</td></tr>' +
                '</tpl>' +
                '<tpl if="size != undefined && size">' +
                    '<tr><th>Size:</th><td>{size:this.renderSize}</td></tr>' +
                '</tpl>' +
           '</table>',
        {
            renderDate : function(d) {
                return this.dateRenderer(d);
            },
            renderSize : function(d) {
                return this.sizeRenderer(d);
            }
        }, {dateRenderer : this.dateRenderer, sizeRenderer : Ext4.util.Format.fileSize});

        this.details = Ext4.create('Ext.Panel', {
            region : 'south',
            minHeight : 100,
            tpl : detailsTpl
        });

        this.on('folderchange', function(){ this.details.update(''); }, this);

        return this.details;
    },

    reload : function() {
        // Reload stores
        this.getFileStore().load();
        var nodes = this.tree.getSelectionModel().getSelection();

        this.tree.getStore().on('load', function(s) {
            this.tree.getView().refresh();
        }, this, {single: true});

        if (nodes && nodes.length) {
            this.tree.getStore().load({node: nodes[0]});
        }
        else {
            this.tree.getStore().load({node: this.tree.getStore().getRootNode()});
        }
    },

    onCreateDirectory : function() {

        var onCreateDir = function(panel) {

            var path = this.getFolderURL();
            if (panel.getForm().isValid()) {
                var values = panel.getForm().getValues();
                if (values && values.folderName) {
                    var folder = values.folderName;
                    this.fileSystem.createDirectory({
                        path : path + folder,
                        success : function(path) {
                            win.close();
                            this.onRefresh();
                        },
                        failure : function(response) {
                            win.close();
                            Ext4.Msg.alert('Create Directory', 'Failed to create directory. This directory may already exist.');
                        },
                        scope : this
                    });
                }
            }

        };

        var win = Ext4.create('Ext.Window', {
            title : 'Create Folder',
            width : 300,
            height: 150,
            modal : true,
            autoShow : true,
            items : [{
                xtype: 'form',
                itemId: 'foldernameform',
                border: false, frame : false,
                items : [{
                    xtype : 'textfield',
                    name : 'folderName',
                    itemId : 'foldernamefield',
                    flex : 1,
                    allowBlank : false,
                    emptyText : 'Folder Name',
                    width : 250,
                    margin : '32 10 0 10',
                    validateOnBlur : false,
                    validateOnChange : false,
//                    validator : function(folder) {
//                        if (folder && folder.length) {
//                            console.log('would validate if folder is already present.');
//                        }
//                        return true;
//                    },
                    listeners : {
                        afterrender : function(field) {
                            var map = new Ext4.util.KeyMap({
                                target: field.el,
                                binding: [{
                                    key   : Ext4.EventObject.ENTER,
                                    fn    : function() { onCreateDir.call(this, field.up('form')); },
                                    scope : this
                                },{
                                    key   : Ext4.EventObject.ESC,
                                    fn    : function() { win.close(); },
                                    scope : this
                                }]
                            });
                        },
                        scope : this
                    },
                    scope : this
                }]
            }],
            cls : 'data-window',
            defaultFocus : 'foldernamefield',
            buttons : [
                {text : 'Submit', handler : function(b) {
                    onCreateDir.call(this, b.up('window').getComponent('foldernameform'));
                }, scope: this},
                {text : 'Cancel', handler : function() { win.close(); }, scope: this}
            ]
        });
    },

    onDelete : function() {

        var recs = this.getGrid().getSelectionModel().getSelection();
        var deleted = 0;
        if (recs && recs.length > 0) {

            var msg = recs.length == 1 ? 'Are you sure that you want to delete the ' + (recs[0].data.collection ? 'folder' : 'file') +' \'' + recs[0].data.name + '\'?'
                    : 'Are you sure you want to delete the ' + recs.length + ' selected files?';
            Ext4.Msg.show({
                title : 'Delete Files',
                cls : 'data-window',
                msg : msg,
                buttons : Ext4.Msg.YESNO,
                icon : Ext4.Msg.QUESTION,
                fn : function(btn) {
                    if (btn == 'yes') {
                        for (var i = 0; i < recs.length; i++) {
                            this.fileSystem.deletePath({
                                path : recs[i].data.href,
                                success : function(path) {
                                    deleted++;
                                    if (deleted == recs.length) {
                                        this.clearGridSelection();
                                        this.onRefresh();
                                    }
                                },
                                failure : function(response) {
                                    Ext4.Msg.alert('Delete', 'Failed to delete.');
                                },
                                scope : this
                            });
                        }
                    }
                },
                scope : this
            });

        }
    },

    onDownload : function(config) {

        var recs = this.getGrid().getSelectionModel().getSelection();

        if (recs.length >= 1) {
            this.fileSystem.downloadResource({record: recs, directory : this.getCurrentDirectory()});
        }
        else {
            Ext4.Msg.show({
                title : 'File Download',
                msg : 'Please select a file or folder on the right to download.',
                buttons : Ext4.Msg.OK
            });
        }
    },

    onEmailPreferences : function() {
        Ext4.create('File.panel.EmailProps', { containerPath: this.containerPath }).show();
    },

    onMovePath : function() {
        if (!this.getCurrentDirectory() || !this.selectedRecord) {
            console.error('onMovePath failed.');
            return;
        }

        var selections = this.getGrid().getSelectionModel().getSelection();

        var listeners = {};
        if (!this.showHidden) {
            listeners['beforeappend'] = this.onShowHidden;
        }

        //
        // Setup a custom tree to diplay for move selection
        //
        var tp = Ext4.create('Ext.tree.Panel', this.getFolderTreeCfg({
            itemId: Ext4.id(),
            id: Ext4.id(),
            store: Ext4.create('Ext.data.TreeStore', this.getFolderTreeStoreCfg({listeners: listeners})),
            height: 200,
            header: false,
            listeners : {},
            rootVisible     : true,
            autoScroll      : true,
            containerScroll : true,
            collapsible     : false,
            collapsed       : false,
            cmargins        : '0 0 0 0',
            border          : true,
            stateful        : false,
            pathSeparator   : ';'
        }, true));

        var okHandler = function(win) {
            var node = tp.getSelectionModel().getLastSelected();
            if (!node) {
                Ext4.Msg.alert('Move Error', 'Must pick a destination folder');
                return;
            }

            if (node.data.id == this.getCurrentDirectory()) {
                Ext4.Msg.alert('Move Error', 'Cannot move a file to the folder it is already in');
                return;
            }

            var destination = node.data.id;

            var toMove = [];
            for (var i=0; i < win.fileRecords.length; i++) {
                toMove.push({record : win.fileRecords[i]});
            }

            this.doMove(toMove, destination);
            win.close();
        };

        Ext4.create('Ext.window.Window', {
            title: "Choose Destination",
            modal: true,
            autoShow : true,
            cls: 'data-window',
            width: 270,
            closeAction: 'hide',
            origName: name,
            fileRecords: selections,
            draggable : false,
            items: [{
                bodyStyle: 'padding: 10px;',
                border : false, frame : false,
                items: [{
                    border: false,
                    html: 'Choose target location for ' + selections.length + ' files:'
                }, tp]
            }],
            buttons: [{
                text: 'Move',
                scope: this,
                handler: function(btn) {
                    okHandler.call(this, btn.findParentByType('window'));
                }
            },{
                text: 'Cancel',
                handler: function(btn) {
                    btn.findParentByType('window').hide();
                }
            }]
        });
    },

    //TODO Various validation tasks should be preformed on this.
    doMove : function(toMove, destination) {

        for (var i = 0; i < toMove.length; i++){
            var selected = toMove[i];

            var newPath = this.fileSystem.concatPaths(destination, (selected.newName || selected.record.data.name));

            this.fileSystem.movePath({
                fileRecord : selected,
                // TODO: Doesn't handle @cloud.  Shouldn't the fileSystem know this?
                source: selected.record.data.id,
                destination: newPath,
                isFile: !this.selectedRecord.data.collection,
                success: function(fs, src, dest) {
                    // Does this work for multiple file moves?
                    this.clearGridSelection();
                    this.onRefresh();
                },
                failure: LABKEY.Utils.displayAjaxErrorResponse,
                scope: this
            });
        }
    },

    onRename : function() {

        if (!this.selectedRecord || !this.getCurrentDirectory()) {
            console.error('onRename failed.');
            return;
        }

        var me = this;
        var okHandler = function() {
            var field = Ext4.getCmp('renameText');
            var newName = field.getValue();

            if (!newName || !field.isValid()) {
                alert('Must enter a valid filename');
            }

            if (newName != win.origName) {
                var destination = me.getCurrentDirectory();

                me.doMove([{
                    record: win.fileRecord,
                    newName: newName
                }], destination);
            }

            win.close();
        };

        var name = this.selectedRecord.data.name;

        var win = Ext4.create('Ext.window.Window', {
            title: "Rename",
            width: 280,
            autoHeight: true,
            modal: true,
            cls : 'data-window',
            closeAction: 'destroy',
            origName: name,
            fileRecord: this.selectedRecord,
            draggable : false,
            autoShow : true,
            items: [{
                xtype: 'form',
                labelAlign: 'top',
                bodyStyle: 'padding: 10px;',
                border : false, frame : false,
                items: [{
                    xtype: 'textfield',
                    id : 'renameText',
                    allowBlank: false,
                    regex: /^[^@\/\\;:?<>*|"^][^\/\\;:?<>*|"^]*$/,
                    regexText: "Folder must be a legal filename and not start with '@' or contain one of '/', '\\', ';', ':', '?', '<', '>', '*', '|', '\"', or '^'",
                    width: 250,
                    labelAlign: 'top',
                    itemId: 'nameField',
                    fieldLabel: 'Filename',
                    labelSeparator : '',
                    value: name,
                    listeners: {
                        afterrender: function(cmp) {
                            cmp.focus(false, 100);
                        }
                    }
                }]
            }],
            buttons: [{
                text: 'Rename',
                handler: function(btn) {
                    var win = btn.findParentByType('window');
                    okHandler.call(this, win);
                },
                scope: this
            },{
                text: 'Cancel',
                handler: function(btn) {
                    btn.findParentByType('window').close();
                }
            }],
            keys: [{
                key: Ext4.EventObject.ENTER,
                handler: okHandler,
                scope: this
            }]
        });

    },

    onRefresh : function() {
        this.gridMask.delay(0);
        if (!this.refreshTask) {
            this.refreshTask = new Ext4.util.DelayedTask(this.reload, this);
        }
        this.refreshTask.delay(250);
        this.selectedRecord = undefined;
    },

    // TODO: This should work even when a tree is not present
    onNavigateParent : function() {
        var tree = this.getComponent('treenav');
        if (tree && tree.getSelectionModel().hasSelection()) {
            var sm = tree.getSelectionModel();
            var node = sm.getSelection()[0];
            if (node && node.parentNode) {
                sm.select(node.parentNode);
            }
        }
    },

    onUpload : function() {
        var up = this.getUploadPanel();
        up.isVisible() ? up.hide() : up.show();
    },

    getAdminPanelCfg : function(pipelineFileProperties) {
        return {
            xtype : 'fileadmin',
            width : 750,
            height: 562,
            plain : true,
            border: false,
            pipelineFileProperties: pipelineFileProperties.config,
            fileProperties : pipelineFileProperties.fileProperties,
            isPipelineRoot : this.isPipelineRoot,
            containerPath : this.containerPath,
            listeners: {
                success: function(gridUpdated)
                {
                    this.configureActions();
                    if (gridUpdated) {
                        this.initGridColumns();
                    }
                },
                close: function() { this.adminWindow.close(); },
                scope: this
            }
        };
    },

    showAdminWindow : function() {
        if (this.adminWindow && !this.adminWindow.isDestroyed) {
            this.adminWindow.setVisible(true);
        }
        else {
            File.panel.Browser._getPipelineConfiguration(function(response) {
                var json = Ext4.JSON.decode(response.responseText);
                this.adminWindow = Ext4.create('Ext.window.Window', {
                    cls: 'data-window',
                    title: 'Manage File Browser Configuration',
                    closeAction: 'destroy',
                    layout: 'fit',
                    modal: true,
                    items: [this.getAdminPanelCfg(json)]
                }).show();
            }, this.containerPath, this);
        }
    },


    onEditFileProps : function() {
        Ext4.create('Ext.Window', {
            title : 'Edit File Properties',
            id : 'editFilePropsWin',
            cls : 'data-window',
            //height : 300,
            width : 400,
            items : Ext4.create('File.panel.EditCustomFileProps', {
                extraColumns : this.getExtraColumnNames(),
                sm : this.getGrid().getSelectionModel().getSelection(),
                winId : 'editFilePropsWin',
                fileProps : this.fileProps
            }),
            listeners : {
                successfulsave : function() {
                    //TODO:  Improve preformance
                    this.getGrid().getStore().load();
                },
                scope : this
            },
            autoShow : true
        });
    },

    setFormFieldTooltip : function(component, icon) {
        var label = component.getEl().down('label');
        if (label) {
            var helpImage = label.createChild({
                tag: 'img',
                src: LABKEY.contextPath + '/_images/' + icon,
                style: 'margin-bottom: 0px; margin-left: 8px; padding: 0px;',
                width: 12,
                height: 12
            });
            Ext4.QuickTips.register({
                target: helpImage,
                text: component.tooltip,
                title: ''
            });
        }
    },

    changeTestFlag : function(folderMove, importReady) {
        var flag = document.getElementById('testFlag');
        var appliedClass = "";
        if(folderMove)
        {
            appliedClass += 'labkey-file-grid-initialized';
            if(importReady)
            {
                appliedClass += ' labkey-import-enabled';
            }
        }
        else if(importReady)
        {
            appliedClass = 'labkey-import-enabled';
        }

        flag.setAttribute('class', appliedClass);
    }
});
