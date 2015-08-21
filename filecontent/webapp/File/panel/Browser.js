/*
 * Copyright (c) 2012-2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.requiresCss("_images/icons.css");
LABKEY.requiresCss("dataview/DataViewsPanel.css");

Ext4.define('File.panel.Browser', {

    extend : 'Ext.panel.Panel',

    alias: ['widget.filebrowser'],

    /**
     * @cfg {Boolean} adminUser
     */
    adminUser : false,

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
    dateRenderer : Ext4.util.Format.dateRenderer(LABKEY.extDefaultDateTimeFormat || "Y-m-d H:i:s"),

    /**
     * Size renderer used in the details panel.
     * @cfg {Ext4.util.Format.fileSize} fileSize
     */
    detailsSizeRenderer : function(value, metaData, record) {
        var render = null;

        // Details panel renders a template without a record.
        if ((record && !record.get("collection")) || value) {
            render = Ext4.util.Format.fileSize(value);

            // If larger than 1k, include the total bytes
            if (value > 1024) {
                var fmt = LABKEY.extDefaultNumberFormat || '0,000';
                var bytes = Ext4.util.Format.number(value, fmt);
                render = render + ' (' + bytes + ' bytes)';
            }
        }

        return render;
    },

    /**
     * Size renderer used in the grid file listing.
     * @cfg {Ext4.util.Format.fileSize} fileSize
     */
    gridSizeRenderer : function(value, metaData, record) {
        var render = null;

        // Details panel renders a template without a record.
        if (record && !record.get("collection")) {
            render = Ext4.util.Format.fileSize(value);

            // If larger than 1k, include the total bytes
            if (value > 1024) {
                var fmt = LABKEY.extDefaultNumberFormat || '0,000';
                var bytes = Ext4.util.Format.number(value, fmt);
                render = '<span title="' + bytes + ' bytes">' + render + '</span>';
            }
        }

        return render;
    },

    /**
     *
     */
    usageRenderer : function(value) {
        if (!value || value.length == 0) return "";
        var result = "<span title='";
        for (var i = 0; i < value.length; i++)
        {
            if (i > 0)
            {
                result += ", ";
            }
            result += Ext4.htmlEncode(value[i].message);
        }
        result += "'>";
        for (i = 0; i < value.length; i++)
        {
            if (i > 0)
            {
                result += ", ";
            }
            if (value[i].href)
            {
                result += "<a href=\'" + value[i].href + "'>";
            }
            result += Ext4.htmlEncode(value[i].message);
            if (value[i].href)
            {
                result += "</a>";
            }
        }
        result += "</span>";
        return result;
    },

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
     * @cfg {Object} folderSeparator
     */
    folderSeparator : '/',

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
     * @cfg (Boolean) showToolbar
     */
    showToolbar : true,

    /**
     * @cfg {String} statePrefix
     */
    statePrefix : undefined,
    STATE_LOCK: false,

    /**
     * The directory that should be expanded to when the browser is initialized. This will only be used
     * if a previous path has not been saved. This should be a path relative to the current base URL.
     * @cfg {String} [startDirectory]
     */
    startDirectory: undefined,

    /**
     * An additional set of toolbar configurable items that can be supplied at runtime.
     * @cfg {Array} tbarItems
     */
    tbarItems : [],

    statics : {
        /**
         * Requests the set of actions available for the given containerPath
         * @private
         */
        _getActions : function(cb, containerPath, scope) {
            Ext4.Ajax.request({
                url: LABKEY.ActionURL.buildURL('pipeline', 'actions', containerPath, {path : decodeURIComponent(scope.getFolderOffset()) }),
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

                        var callbacks = File.panel.Browser._pipelineConfigurationCache[containerPath], cb;
                        File.panel.Browser._pipelineConfigurationCache[containerPath] = response;

                        for (var c=0; c < callbacks.length; c++) {
                            cb = callbacks[c];
                            if (Ext4.isFunction(cb.fn)) {
                                cb.fn.call(cb.scope || window, response);
                            }
                        }
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

        _toggleActions : function(fileSystem, actions, selection) {
            Ext4.iterate(actions, function(key, action) {
                action.updateEnabled(fileSystem, selection);
            });
        }

    },

    constructor : function(config) {

        //
        // Check for required configurations
        //
        if (!config.fileSystem) {
            console.error(this.$className, ' requires a \'fileSystem\' for initialization.');
        }

        Ext4.QuickTips.init();

        // Clone the config so we don't modify the original config object
        var _config = Ext4.Object.merge({}, config);

        // Set the file system early
        this.fileSystem = _config.fileSystem;

        this.setFolderOffset(this.fileSystem.getOffsetURL());

        this.callParent([_config]);

        this.eventsConfigured = true;

        this.addEvents('folderchange', 'pipelineconfigured');
    },

    initComponent : function() {

        var fileListTemplate =
            '<tpl for="files">' +
                '<tpl if="xindex &lt; 11">' +
                    '- {.}&lt;br/&gt;' +
                '</tpl>' +
                '<tpl if="xindex == 11">' +
                    '... too many files to display&lt;br/&gt;' +
                '</tpl>' +
            '</tpl>';

        //
        // Configure private flags
        //
        Ext4.apply(this, {
            actionsConfig : {},
            actionProviders: {}, // index of Action Providers
            fileProps: [],
            importDataEnabled: true,
            pipelineActions: [], // array of pipeline models that are currently available. See File.data.Pipeline

            //
            // Message Templates
            //
            longMsgNoSelectionTpl: new Ext4.XTemplate('This action requires selection from this list of file(s):&lt;br/&gt;', fileListTemplate).compile(),
            longMsgEnabledTpl: new Ext4.XTemplate('This action will use the selected file(s):&lt;br/&gt;', fileListTemplate).compile(),
            longMsgNoMultiSelectTpl: new Ext4.XTemplate('This action can only operate on one file at a time from the selected list:&lt;br/&gt;', fileListTemplate).compile(),
            longMsgNoMatchTpl: new Ext4.XTemplate('This action can only operate on this list of file(s):&lt;br/&gt;', fileListTemplate).compile(),

            shortMsgTpl: new Ext4.XTemplate('<span style="margin-left: 5px;" class="labkey-mv">{msg}</span>').compile(),
            shortMsgEnabledTpl : new Ext4.XTemplate('<span style="margin-left:5px;" class="labkey-mv">using {count} out of {total} file(s)</span>').compile()
        });

        if (!this.hasStatePrefix()) {
            this.statePrefix = undefined;
        }
        else {
            Ext4.state.Manager.setProvider(new Ext4.state.CookieProvider());
        }

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
        this.getActions();

        this.items = this.getItems();

        if (this.showToolbar) {
            this.isWebDav ? this.configureWebDavToolbar() : this.configureActions();
        }

        // Attach listeners
        this.on('folderchange', this.onFolderChange, this);

        File.panel.Browser._toggleActions(this.fileSystem, this.actions, []);

        this.callParent();
    },

    getActions : function() {
        if (!this.actions) {
            this.actions = {};

            this.actions.parentFolder = Ext4.create('File.panel.Action', {
                text: 'Parent Folder',
                hardText: 'Parent Folder',
                itemId: 'parentFolder',
                tooltip: 'Navigate to parent folder',
                fontCls: 'fa-arrow-up',
                handler: this.onNavigateParent,
                actionType: File.panel.Action.Type.NOMIN,
                scope: this,
                hideText: true
            });

            this.actions.refresh = Ext4.create('File.panel.Action', {
                text: 'Refresh',
                hardText: 'Refresh',
                itemId: 'refresh',
                tooltip: 'Refresh the contents of the current folder',
                fontCls: 'fa-refresh',
                handler : this.onRefresh,
                actionType : File.panel.Action.Type.NOMIN,
                scope: this,
                hideText: true
            });

            this.actions.createDirectory = Ext4.create('File.panel.Action', {
                text: 'Create Folder',
                hardText: 'Create Folder',
                itemId: 'createDirectory',
                tooltip: 'Create a new folder on the server',
                fontCls: 'fa-folder-o',
                stacked: true,
                stackedCls: 'fa-plus-circle labkey-fa-plus-circle-folder',
                handler : this.onCreateDirectory,
                actionType : File.panel.Action.Type.NOMIN,
                scope: this,
                hideText: true
            });

            this.actions.download = Ext4.create('File.panel.Action', {
                text: 'Download',
                hardText: 'Download',
                itemId: 'download',
                tooltip: 'Download the selected files or folders',
                fontCls: 'fa-download',
                disabled: true,
                handler: this.onDownload,
                actionType: File.panel.Action.Type.ATLEASTONE,
                scope: this,
                hideText: true
            });

            this.actions.viewFile = Ext4.create('File.panel.Action', {
                text: 'View File',
                hardText: 'View File',
                itemId: 'viewFile',
                tooltip: 'View file in new window',
                disabled: true,
                handler: this.onViewFile,
                actionType : File.panel.Action.Type.ONLYONE,
                actionItemType : File.panel.Action.ItemType.ONLY_FILE,
                scope: this
            });

            this.actions.deletePath = Ext4.create('File.panel.Action', {
                text: 'Delete',
                hardText: 'Delete',
                itemId: 'deletePath',
                tooltip: 'Delete the selected files or folders',
                 fontCls: 'fa-trash-o',
                handler: this.onDelete,
                actionType : File.panel.Action.Type.ATLEASTONE,
                scope: this,
                disabled: true,
                hideText: true,
                shouldDisable : function (fileSystem, record) {
                    return !fileSystem.canDelete(record);
                }
            });

            this.actions.renamePath = Ext4.create('File.panel.Action', {
                text: 'Rename',
                hardText: 'Rename',
                itemId: 'renamePath',
                tooltip: 'Rename the selected file or folder',
                fontCls: 'fa-pencil',
                handler : this.onRename,
                actionType : File.panel.Action.Type.ONLYONE,
                scope: this,
                disabled: true,
                hideText: true,
                shouldDisable : function (fileSystem, record) {
                    return !fileSystem.canMove(record);
                }
            });

            this.actions.movePath = Ext4.create('File.panel.Action', {
                text: 'Move',
                hardText: 'Move',
                itemId: 'movePath',
                tooltip: 'Move the selected file or folder',
                fontCls: 'fa-sign-out',
                handler : this.onMovePath,
                actionType : File.panel.Action.Type.ATLEASTONE,
                scope: this,
                disabled: true,
                hideText: true
            });

            this.actions.help = Ext4.create('File.panel.Action', {
                text: 'Help',
                hardText: 'Help',
                itemId: 'help',
                scope: this
            });

            this.actions.showHistory = Ext4.create('File.panel.Action', {
                text: 'Show History',
                hardText: 'Show History',
                itemId: 'showHistory',
                scope: this
            });

            this.actions.upload = Ext4.create('File.panel.Action', {
                text: 'Upload Files',
                hardText: 'Upload Files',
                itemId: 'upload',
                fontCls: 'fa-file-o',
                stacked: true,
                stackedCls: 'fa-arrow-circle-o-up labkey-fa-upload-files',
                enableToggle: true,
                pressed: this.showUpload && this.expandUpload,
                handler : this.onUpload,
                scope: this,
                hideText: true,
                tooltip: 'Upload files or folders from your local machine to the server'
            });

            this.actions.folderTreeToggle = Ext4.create('File.panel.Action', {
                text: 'Toggle Folder Tree',
                hardText: 'Toggle Folder Tree',
                itemId: 'folderTreeToggle',
                enableToggle: true,
                fontCls: 'fa-sitemap',
                tooltip: 'Show or hide the folder tree',
                hideText: true,
                handler : function() { this.tree.toggleCollapse(); },
                actionType : File.panel.Action.Type.NOMIN,
                scope: this
            });

            this.actions.importData = Ext4.create('File.panel.Action', {
                text: 'Import Data',
                hardText: 'Import Data',
                itemId: 'importData',
                handler: this.onImportData,
                fontCls: 'fa-database',
                tooltip: 'Import data from files into the database, or analyze data files',
                actionType : File.panel.Action.Type.NOMIN,
                scope: this,
                hideText: true
            });

            this.actions.customize = Ext4.create('File.panel.Action', {
                text: 'Admin',
                hardText: 'Admin',
                itemId: 'customize',
                fontCls: 'fa-cog',
                tooltip: 'Configure the buttons shown on the toolbar',
                actionType: File.panel.Action.Type.NOMIN,
                handler: this.showAdminWindow,
                scope: this
            });

            this.actions.editFileProps = Ext4.create('File.panel.Action', {
                text: 'Edit Properties',
                hardText: 'Edit Properties',
                itemId: 'editFileProps',
                fontCls: 'fa-pencil',
                tooltip: 'Edit properties on the selected file(s)',
                actionType: File.panel.Action.Type.ATLEASTONE,
                handler: this.onEditFileProps,
                disabled : true,
                hideText: true,
                scope: this
            });

            this.actions.emailPreferences = Ext4.create('File.panel.Action', {
                text: 'Email Preferences',
                hardText: 'Email Preferences',
                itemId: 'emailPreferences',
                fontCls: 'fa-envelope',
                tooltip: 'Configure email notifications on file actions.',
                hideText: true,
                actionType: File.panel.Action.Type.NOMIN,
                handler: this.onEmailPreferences,
                scope: this
            });

            this.actions.auditLog = Ext4.create('File.panel.Action', {
                text: 'Audit History',
                hardText: 'Audit History',
                itemId: 'auditLog',
                fontCls: 'fa fa-users',
                tooltip: 'View the files audit log for this folder.',
                actionType : File.panel.Action.Type.NOMIN,
                handler : function() {
                    window.location = LABKEY.ActionURL.buildURL('filecontent', 'showFilesHistory', this.containerPath);
                },
                hideText: true,
                scope: this
            });
        }

        return this.actions;
    },

    /**
     * Configure all actions according the current pipeline configuration.
     * @param {boolean} [clearCache=false] Clear the pipeline configuration cache for the current containerPath.
     * This should be done any time the configuration is modified.
     */
    configureActions : function(clearCache) {
        if (!this.isWebDav) {
            var configure = function(response) {
                var json = Ext4.decode(response.responseText);
                if (json.config) {
                    // Configure the actions on the toolbar based on whether they should be shown
                    this.initializeToolbar({
                        tbarActions: json.config.tbarActions,
                        actions: json.config.actions
                    });
                    this.updateActions();
                }
            };

            if (clearCache === true) {
                File.panel.Browser._clearPipelineConfiguration(this.containerPath);
            }
            File.panel.Browser._getPipelineConfiguration(configure, this.containerPath, this);
        }
    },

    configureWebDavToolbar : function() {
        var baseItems = [],
            actions = this.getActions();

        if (this.showFolderTree) {
            baseItems.push(actions.folderTreeToggle);
        }

        baseItems.push(
            actions.parentFolder,
            actions.refresh,
            actions.createDirectory,
            actions.download,
            actions.deletePath,
            actions.upload
        );

        if (Ext4.isArray(this.tbarItems)) {
            for (var i=0; i < this.tbarItems.length; i++) {
                baseItems.push(this.tbarItems[i]);
            }
        }

        this.dockedItems = [{
            xtype: 'toolbar',
            itemId: 'actionToolbar',
            dock: 'top',
            items: baseItems,
            enableOverflow: true
        }];
    },

    initializeToolbar : function(config) {
        if (Ext4.isArray(config)) {
            config = config[0];
        }

        var toolbar = this.getDockedItems()[0],
            tbarConfig = config.tbarActions,
            actionButtons = config.actions,
            buttons = [], i, action,
            // Use as a lookup
            mapTbarItems = {};

        if (toolbar) {
            // remove docked items, and reset actions
            this.removeDocked(toolbar);
            this.actions = undefined;
        }

        var actions = this.getActions(),
            reset = false;

        if (Ext4.isArray(tbarConfig)) {
            // initialize only actions specified in these configs
            Ext4.each(tbarConfig, function(config) {
                var action = actions[config.id];
                if (action) {
                    action.hideText = config.hideText;
                    action.hideIcon = config.hideIcon;
                }
                else {
                    console.warn('Unable to find action for: ', config.id + '. Skipping configuration.');
                }
            });
        }
        else {
            reset = true;
        }

        // initialize all actions
        Ext4.iterate(actions, function(key, action) {
            if (reset) {
                action.hideText = action.resetProps.hideText;
                action.hideIcon = action.resetProps.hideIcon;
            }
            action.setText(action.hideText ? undefined : action.hardText);
            if (action.hideIcon) {
                action.fontCls = undefined;
            }
        });

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

                    // issue 19166
                    if (!(actionConfig.hideText && actionConfig.hideIcon)) {
                        // get the real action
                        action = actions[actionConfig.id];
                        if (action) {
                            buttons.push(action);
                        }
                        else {
                            console.warn('Unable to find action:', actionConfig.id);
                        }
                    }
                }
            }
        }
        else if (!Ext4.isEmpty(this.tbarItems)) {
            Ext4.each(this.tbarItems, function(item) {
                action = this.actions[item];
                buttons.push(action ? action : item);
            }, this);
        }

        this.linkIdMap = {};

        if (Ext4.isArray(actionButtons)) {
            var link, id;
            Ext4.each(actionButtons, function(button) {
                link = button.links[0];

                if (link.display === 'toolbar') {

                    //
                    // Store an id lookup for the component id of this button
                    // based on it's provider id
                    //
                    id = Ext4.id();
                    this.linkIdMap[link.id] = id;

                    action = Ext4.create('File.panel.Action', {
                        id: id,
                        providerItemId: link.id,
                        text: link.label,
                        disabled: true,
                        handler: this.executeToolbarAction,
                        scope: this
                    });
                    buttons.push(action);
                }
            }, this);
        }

        this.addDocked({
            xtype: 'toolbar',
            itemId: 'actionToolbar',
            dock: 'top',
            items: buttons,
            enableOverflow: true
        });
    },

    getToolbar : function() {
        return this.getComponent('actionToolbar');
    },

    executeToolbarAction : function(item, e) {
        if (Ext4.isString(item.providerItemId)) {
            var action = this.actionMap[item.providerItemId];

            if (action) {
                this.executeImportAction(action);
            }
        }
    },

    attachPreview : function(id, record)
    {
        if (this && !this.fileSystem.canRead(record))
            return;
        if (record.get("collection"))
            return;
        var img = Ext4.fly(id,"previewAnchor");
        if (!img)
            return;
        var preview = Ext4.create('File.panel.Preview',{title:false, target:id, record:record});
        //var preview = Ext4.create('Ext.tip.ToolTip',{target:id, html:'tip '+ id + ' ' + record.get("name")});
    },

    // minor hack call with scope having decorateIcon functions
    iconRenderer : function(value, metadata, record, rowIndex, colIndex, store, grid)
    {
        if (!value)
        {
            if (record.get("collection"))
            {
                value = LABKEY.FileSystem.FOLDER_ICON;
            }
            else
            {
                var name = record.get("name");
                var i = name.lastIndexOf(".");
                var ext = i >= 0 ? name.substring(i) : name;
                value = LABKEY.contextPath + "/project/icon.view?name=" + ext;
            }
        }
        var img = {tag:'img', width:16, height:16, src:value, id:Ext4.id(null,'icon')};
        var html = Ext4.DomHelper.markup(img);
        Ext4.defer(this.attachPreview,10,this,[img.id,record]);
        return html;
    },

    initGridColumns : function()
    {
        var columns = [{
            //xtype : 'templatecolumn', tpl : '<img height="16px" width="16px" src="{icon}" alt="{type}">',
            renderer : this.iconRenderer,
            text  : '',
            dataIndex : 'icon',
            sortable : !this.bufferFiles,
            width : 25,
            height : 20,
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
                            '<span style="display: inline-block; white-space: nowrap;">{name:htmlEncode}</span>' +
                        '</div>' +
                   '</div>',
            scope : this
        },
        {header: "Last Modified",  flex: 1, dataIndex: 'lastmodified', sortable: true,  hidden: false, height : 20, renderer: this.dateRenderer},
        {header: "Size",           flex: 1, dataIndex: 'size',         sortable: true,  hidden: false, height : 20, renderer: this.gridSizeRenderer, align : 'right'},
        {header: "Created By",     flex: 1, dataIndex: 'createdby',    sortable: true,  hidden: false, height : 20},
        {header: "Description",    flex: 1, dataIndex: 'description',  sortable: true,  hidden: false, height : 20, renderer: Ext4.htmlEncode},
        {header: "Usages",         flex: 1, dataIndex: 'actions',      sortable: !this.bufferFiles,  hidden: false, height : 20, renderer: this.usageRenderer, showContextMenu: false},
        {header: "Download Link",  flex: 1, dataIndex: 'fileLink',     sortable: !this.bufferFiles,  hidden: true, height : 20, showContextMenu: false},
        {header: "File Extension", flex: 1, dataIndex: 'fileExt',      sortable: !this.bufferFiles,  hidden: true, height : 20, renderer: Ext4.htmlEncode}
        ];

        this.setDefaultColumns(columns);

        if (!this.isWebDav) {
            File.panel.Browser._getPipelineConfiguration(this._onExtraColumns, this.containerPath, this);
        }

        return columns;
    },

    _onExtraColumns : function(response) {
        var finalColumns = [];
        var json = Ext4.decode(response.responseText);

            // initially populate the finalColumns array with the defaults
        var defaultColumns = this.getDefaultColumns();
        var customColumns = json.fileProperties, i;

        for (i = 0; i < defaultColumns.length; i++) {
            finalColumns.push(defaultColumns[i]);
        }

        for (i = 0; i < customColumns.length; i++) {
            var customCol = {
                header : customColumns[i].label || customColumns[i].name,
                flex: 1,
                dataIndex : customColumns[i].name,
                height: 20,
                hidden : customColumns[i].hidden
            };
            finalColumns.push(customCol);
        }

        // apply the gridConfig metadata, i.e. hidden and sortable state, if it exists
        if (json.config && json.config.gridConfig) {
            var gridConfigColInfo = json.config.gridConfig.columns;
            for (i = 0; i < gridConfigColInfo.length; i++) {
                var index = gridConfigColInfo[i].id - 1;

                if (finalColumns[index]) {
                    finalColumns[index].hidden = gridConfigColInfo[i].hidden;
                    finalColumns[index].sortable = gridConfigColInfo[i].sortable;
                }
            }
        }

        this.setDefaultColumns(finalColumns);
        this.setExtraColumns(customColumns);
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
        this.extraColumns = extraColumns.slice(0);
        this.fireEvent('columnchange', this.onColumnChange(this.getColumns()));
    },

    getExtraColumns : function() {
        if (this.extraColumns) {
            return this.extraColumns.slice(0);
        }
        return [];
    },

    getExtraColumnNames : function() {
        return Ext4.Array.pluck(this.getExtraColumns(), 'name');
    },

    setDefaultColumns : function(defaultColumns) {
        this.defaultColumns = defaultColumns.slice(0);
    },

    getDefaultColumns : function() {
        if(this.defaultColumns)
            return this.defaultColumns.slice(0);
    },

    getColumns : function() {
        return this.getDefaultColumns();
    },

    onColumnChange : function(allTheColumns) {
        this.getGrid().reconfigure(this.getGrid().getStore(), allTheColumns); // REMEMBER: Check if passing in the store fires reload
    },

    getItems : function() {
        var items = [this.getGridCfg()];

        if (this.showFolderTree === true) {
            items.push(this.getFolderTreeCfg());
            this.on('folderchange', this.expandPath, this);
        }

        if (this.showUpload === true) {
            items.push(this.getUploadPanel());
        }

        if (this.showDetails === true) {
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

        this.fileStore = Ext4.create('File.store.GridStore', storeConfig);

        this.on('folderchange', this.updateGridProxy, this);

        //
        // 'load' only works on non-buffered stores
        //
        this.fileStore.on(this.bufferFiles ? 'prefetch' : 'load', function() {
            this.attachCustomFileProperties();
        }, this);

        this.fileStore.on('load', function() {
            this.onSelection(this.getGrid(), this.getGridSelection());
        }, this, {single: true});

        return this.fileStore;
    },


    attachCustomFileProperties : function() {

        if (this.isWebDav) {
            return;
        }

        //Copied array so as not to be include name/Row Id in extra columns
        var extraColumnNames = this.getExtraColumnNames();

        var config = {
            schemaName : 'exp',
            queryName : 'Data',
            columns : ['Name', 'Flag/Comment', 'Run', 'Data File URL'].concat(extraColumnNames),
            requiredVersion : '9.1',
            success : function(resp) {
                this.processCustomFileProperies(resp.rows, extraColumnNames);
            },
            scope : this
        };

        LABKEY.Query.selectRows(config);
    },

    processCustomFileProperies : function(rows, extraColumnNames) {
        this.setFileObjects(rows);
        var propName, rec, recId, idx,
                baseUrl = this.getBaseURL(), dataFileUrl, values, value, cell,
                strStartIndex, fileStore = this.getFileStore();

        Ext4.each(rows, function(row) {
            // use the record 'Id' to lookup the fileStore record because it includes the relative path and file/folder name
            dataFileUrl = Ext4.Object.fromQueryString('url=' + row.DataFileUrl.value).url;
            strStartIndex = dataFileUrl.indexOf(baseUrl);
            recId = dataFileUrl.substring(strStartIndex);
            idx = fileStore.findExact('id', recId);

            if (idx >= 0) {
                rec = fileStore.getAt(idx);
                recId = rec.get('id');
                propName = this.fileProps[recId];
                if (!propName) {
                    propName = {};
                }

                values = {};
                Ext4.each(extraColumnNames, function(columnName) {

                    cell = row[columnName];
                    value = Ext4.htmlEncode(cell.displayValue ? cell.displayValue : cell.value);

                    if (cell.url) {
                        value = "<a href='" + cell.url + "'>" + value + "</a>";
                    }

                    values[columnName] = value;
                    propName[columnName] = cell.value;

                }, this);
                rec.set(values);

                propName.rowId = row['RowId'].value;
                propName.name = row['Name'].value;
                this.fileProps[recId] = propName;
            }
        }, this);
    },

    updateGridProxy : function() {
        if (!this.gridTask) {
            this.gridTask = new Ext4.util.DelayedTask(function() {
                if (this.getGrid()) {
                    this.getGrid().getSelectionModel().select([]);
                }
                var fileStore = this.getFileStore();
                fileStore.getProxy().url = LABKEY.ActionURL.encodePath(this.getFolderURL());
                fileStore.load();
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
        path = path.replace(this.fileSystem.getBaseURL(), this.folderSeparator);
        // If we don't go anywhere, don't fire a folder change
        if (this.rootOffset != path) {
            this.rootOffset = path;
            this.fireEvent('folderchange', path, model, offsetPath);
        }

        //
        // If the offset has changed, update the actions with the current path
        //
        if (this.eventsConfigured)
            this.on('pipelineconfigured', function() { this.clearGridSelection(); }, this, {single: true});
        this.updateActions();
    },

    getFolderTreeStoreCfg : function(configs) {
        return Ext4.apply({}, configs, {
            model : this.fileSystem.getModel('xml'),
            proxy : this.fileSystem.getProxyCfg('xml'),
            root : {
                text : this.fileSystem.rootName,
                // name property used by details panel template
                name : this.fileSystem.rootName,
                id : this.fileSystem.getBaseURL(),
                // uri property used by details panel template
                uri : this.fileSystem.getAbsoluteURL(),
                expanded : true,
                cls : 'fileset-root-node', // for selenium testing
                icon : LABKEY.contextPath + '/_images/labkey.png'
            }
        });
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
                load: {
                    fn : function(s) {
                        this.loadRootNode();
                        if (!Ext4.isEmpty(this.startDirectory)) {
                            this.getFileStore().LOCKED = true;
                            this.STATE_LOCK = true;
                            this.ensureVisible(this.fileSystem.getBaseURL() + this.startDirectory);

                            if (this.hasStatePrefix()) {
                                Ext4.state.Manager.clear(this.statePrefix + ".currentDirectory");
                            }
                        }
                        else if (this.hasStatePrefix()) {
                            // Retrieve the last visited folder from the container cookie
                            var startFolder = Ext4.state.Manager.get(this.statePrefix + ".currentDirectory");
                            if (startFolder) {
                                this.getFileStore().LOCKED = true;
                                this.STATE_LOCK = true;
                                this.ensureVisible(startFolder);
                            }
                        }
                        else {
                            this.ensureVisible(this.fileSystem.getOffsetURL());
                        }
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
            store.sort('name', 'ASC');
        }

        return Ext4.apply({}, configs, {
            xtype : 'treepanel',
            itemId : 'treenav',
            region : 'west',
            cls : 'themed-panel treenav-panel',
            width : 225,
            store : store,
            collapsed: !this.expandFolderTree,
            collapsible : true,
            collapseMode : 'mini',
            split : true,
            useArrows : true,
            border: false,
            listeners : {
                beforerender : function(t) { this.tree = t; },
                select : this.onTreeSelect,
                scope : this
            }
        });
    },

    loadRootNode : function() {
        var proxy = Ext4.create( 'File.data.webdav.Proxy', {
            url: this.fileSystem.getURI(this.fileSystem.getBaseURL()),
            model: 'File.data.webdav.XMLResponse',
            reader: new Ext4.data.reader.Xml({
                record: 'response',
                root: 'multistatus',
                model: 'File.data.webdav.XMLResponse'
            }),
            depth: 0
        });

        var tree = this.getComponent('treenav');
        if (tree) {
            var store = tree.getView().getTreeStore();
            var uploadPanel = this.uploadPanel;
            proxy.read(new Ext4.data.Operation({action: 'read'}), function(s) {
                var options;
                if (s.success) {
                    // only one result (depth=0)
                    options = s.resultSet.records[0].data.options;
                }
                else {
                    options = {COPY: false, DELETE: false, GET: false, HEAD: false, LOCK: false, MKCOL: false, MOVE: false, OPTIONS: false, POST: false, PROPFIND: false, PUT: false, UNLOCK: false };
                }
                store.tree.root.data.options = options;
                uploadPanel.onLoad();
            });
        }
    },

    /**
     * Helper method to ensure that the ID is wrapped by this.folderSeparator on either side. Tree nodes require this.
     * @param id
     * @returns {String}
     */
    ensureNodeId : function(id) {
        var _id = id, sep = this.folderSeparator;
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

        var tree = this.tree;
        var node = tree.getView().getTreeStore().getRootNode().findChild('id', nodeId, true);
        if (!node) {
            var p = this.fileSystem.getParentPath(nodeId);
            if (p == this.folderSeparator) {
                this.unlockState();
                return;
            }
            if (nodeId.length > 0 && nodeId.substring(nodeId.length-1) != this.folderSeparator) {
                nodeId += this.folderSeparator;
            }
            this.vStack.push(nodeId);
            this.ensureVisible(p);
        }
        else
        {
            if (!node.isLeaf())
            {
                var s = this.vStack.pop();
                if (!s)
                {
                    tree.getSelectionModel().select(node);
                    this.unlockState();
                }
                else
                {
                    if (tree.getSelectionModel().isSelected(node) && this.getFileStore().LOCKED && this.STATE_LOCK) {
                        // degenerate case
                        this.unlockState(true);
                    }
                    else {
                        tree.getView().getTreeStore().on('load', function (cmp, node, recs) {
                            this.ensureVisible(s);
                        }, this, {single: true});

                        tree.getSelectionModel().select(node);
                    }
                }
            }
        }
    },

    // clean up once initial folder selection has finished
    unlockState : function(refresh) {
        this.getFileStore().LOCKED = false;
        this.STATE_LOCK = false;
        this.vStack = undefined;

        if (refresh)
            this.onRefresh();
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

    getGridSelection : function() {
        var grid = this.getGrid();
        var selection = [];
        if (grid) {
            selection = grid.getSelectionModel().getSelection();
        }
        return selection;
    },

    /**
     * updateActions will request the set of actions available from both the server and the pipeline
     * Note: You should probably consider calling configureActions instead
     */
    updateActions : function() {
        if (this.isPipelineRoot && !this.isWebDav) {
            var actionsReady = false,
                pipelineReady = false,
                actions,
                me = this;

            var check = function() {
                if (actionsReady && pipelineReady) {
                    me.updatePipelineActions(actions);
                }
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
        else {
            this.updatePipelineActions([]);
        }
    },

    // worst named method ever
    configureActionConfigs : function(response) {
        var resp = Ext4.decode(response.responseText);
        var actionConfigs = resp.config.actions;

        if (actionConfigs) {
            this.importDataEnabled = resp.config.importDataEnabled ? true : false;

            for (var i=0; i < actionConfigs.length; i++) {
                this.actionsConfig[actionConfigs[i].id] = Ext4.create('File.data.PipelineAction', actionConfigs[i]);
            }
        }

        this.actions.importData[(!this.importDataEnabled && !this.adminUser ? 'hide' : 'show')]();
    },

    updatePipelineActions : function(actions) {

        this.pipelineActions = []; // reset pipeline actions
        this.actionMap = {};
        this.actionProviders = {};

        if ((this.importDataEnabled || this.adminUser) && !Ext4.isEmpty(actions)) {
            var pipelineActions = File.data.Pipeline.parseActions(actions);
            var pa, config, providerId, link, enabled;

            for (var i=0; i < pipelineActions.length; i++) {

                pa = pipelineActions[i];
                link = pa.getLink();
                enabled = false;

                if (link) {

                    providerId = pa.get('groupId');
                    config = this.actionsConfig[providerId];

                    if (link.href) {
                        var display = 'enabled';
                        if (config) {
                            var linkConfig = config.getLink(link.id);
                            if (linkConfig) {
                                display = linkConfig.display;
                            }
                        }

                        //
                        // Set the actions enabled state
                        //
                        enabled = display != 'disabled';
                        pa.data.link.enabled = enabled;
                    }

                    if (this.adminUser || enabled) {
                        this.pipelineActions.push(pa);
                        this.actionMap[pa.getId()] = pa;

                        if (!this.actionProviders[providerId]) {
                            this.actionProviders[providerId] = {
                                label: pa.get('groupLabel'),
                                actions: []
                            }
                        }

                        this.actionProviders[providerId].actions.push(pa);
                    }
                }
                else {
                    console.warn('improperly configured pipeline action (id):', pa.id);
                }
            }
        }

        this.fireEvent('pipelineconfigured', this.pipelineActions);
    },

    onImportData : function() {

        //
        // Build up an array of actions which are grouped by Provider
        // These are then handed to a form to be rendered
        //
        var pa, alreadyChecked = false, radios,
            provider, actions = [],
            actionMap = {}, hasAdmin = false, link, label, enabled;

        //
        // make sure we've processed the current selection
        // ensureSelection()
        //
        this.onSelection(this.getGrid(), this.getGridSelection());

        //
        // Iterator over the action providers building the set of radios
        //
        Ext4.iterate(this.actionProviders, function(ag, provider) {
            pa = provider.actions[0];
            radios = [];

            Ext4.each(provider.actions, function(action) {

                link = action.getLink();
                enabled = action.getEnabled();

                if (link.href && (link.enabled || this.adminUser)) {
                    label = link.text;

                    //
                    // Administrators always see all actions
                    //
                    if (!link.enabled && this.adminUser) {
                        label = label.concat(' <span class="labkey-error">*</span>');
                        hasAdmin = true;
                    }

                    actionMap[action.getId()] = action;

                    radios.push({
                        xtype: 'radio',
                        checked: enabled && !alreadyChecked,
                        disabled: !enabled,
                        labelSeparator: '',
                        boxLabel: label,
                        name: 'importAction',
                        inputValue: action.getId(),
                        width : '100%',
                        validateOnChange: false,
                        validateOnBlur: false
                    });

                    if (enabled) {
                        alreadyChecked = true;
                    }
                }

            }, this);

            actions.push({
                xtype: 'radiogroup',
                fieldLabel: provider.label + '<br>' + pa.getShortMessage(),
                tooltip: pa.getLongMessage(),
                minHeight: 40,
                columns: 1,
                labelSeparator: '',
                items: radios,
                labelWidth: 275,
                paEnabled: pa.getEnabled(),
                disabled: !pa.getEnabled(),
                disabledCls: 'import-provider',
                bodyStyle: 'margin-bottom: 4px;',
                validateOnChange: false,
                listeners : {
                    render: function(rg) {
                        this.setFormFieldTooltip(rg, rg.paEnabled ? 'info.png' : 'warning-icon-alt.png');
                    },
                    scope: this
                }
            });

        }, this);

        var actionPanelId = Ext4.id();

        var items = [{
            xtype: 'form',
            id: actionPanelId,
            bodyStyle: 'padding: 10px;',
            labelAlign: 'left',
            itemCls: 'x-check-group',
            items: actions
        }];

        if (hasAdmin) {
            items.push({
                html: 'Actions marked with an asterisk <span class="labkey-error">*</span> are only visible to Administrators.',
                bodyStyle: 'padding: 10px',
                border: false
            });
        }

        var win = Ext4.create('Ext.Window', {
            title: 'Import Data',
            cls: 'data-window',
            width: 725,
            minHeight: 200,
            maxHeight: 500,
            autoScroll: true,
            closeAction: 'destroy',
            modal: true,
            items: items,
            autoShow: true,
            buttons: [{
                text: 'Import',
                handler: function() {
                    this.submitForm(Ext4.getCmp(actionPanelId), actionMap);
                    win.close();
                },
                scope: this
            },{
                text: 'Cancel',
                handler: function() {
                    win.close();
                },
                scope: this
            }]
        });
    },

    submitForm : function(panel, actionMap) {
        // client side validation
        var selection = panel.getForm().getValues();
        var action = actionMap[selection.importAction];

        if (Ext4.isObject(action)) {
            this.executeImportAction(action);
        }
        else {
            console.warn('failed to find action for submission.');
        }
    },

    executeImportAction : function(action) {
        if (action) {
            var selections = this.getGridSelection(), i;
            var link = action.getLink();

            //
            // if there are no selections, treat as if all are selected
            //
            if (selections.length == 0) {
                var store = this.getGrid().getStore();
                selections = store.getRange(0, store.getCount()-1);
            }

            if (link && link.href) {
                if (selections.length == 0) {
                    Ext4.Msg.alert("Execute Action", "There are no files selected");
                    return false;
                }

                var form = document.createElement("form");
                form.setAttribute("method", "post");
                form.setAttribute("action", link.href);

                for (i=0; i < selections.length; i++)
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
                var hiddenField = document.createElement("input");
                hiddenField.setAttribute("name", "X-LABKEY-CSRF");
                hiddenField.setAttribute("value", LABKEY.CSRF);
                form.appendChild(hiddenField);
                document.body.appendChild(form);
                form.submit();
            }
        }
    },

    /**
     * Helper to enable and disable the import data action, marker classes
     * are used to help with automated tests.
     */
    enableImportData : function(enabled) {
        var actions = this.getActions();

        if (this.showToolbar && actions.importData) {
            var el = this.getToolbar().getEl(),
                cls = 'labkey-import-enabled';

            if (enabled) {
                el.addCls(cls);
                actions.importData.enable();
            }
            else {
                el.removeCls(cls);
                actions.importData.disable();
            }
        }
    },

    onSelection : function(grid, selectedRecords) {

        this.enableImportData(false);

        this.changeTestFlag(false, false);

        if (this.pipelineActions.length > 0) {

            var selections = selectedRecords,
                emptySelection = false,
                action,
                files,
                selectionCount,
                selectedFiles,
                selectionMap = {},
                currentDir = this.fileSystem.getDirectoryName(this.getCurrentDirectory()),
                store = grid.getStore(),
                count = store.getCount();

            if (!selections.length && count > 0) {
                emptySelection = true;
                selections = store.getRange(0, count-1); // get all the available records
            }

            for (var i=0; i < selections.length; i++) {
                selectionMap[selections[i].data.name] = true;
            }

            //
            // Iterate over the actions comparing the selections
            //
            for (i=0; i < this.pipelineActions.length; i++) {
                action = this.pipelineActions[i];
                files = action.getFiles();
                selectionCount = 0;
                selectedFiles = [];

                if (emptySelection && !action.supportsEmptySelect()) {
                    action.setEnabled(false);
                    action.setMessage(
                            this.shortMsgTpl.apply({msg: 'a file must be selected first'}),
                            this.longMsgNoSelectionTpl.apply({files: files})
                    );
                }
                else if (files && files.length > 0) {
                    for (var f=0; f < files.length; f++) {
                        //
                        // Check if this file has been selected
                        //
                        if (files[f] in selectionMap) {
                            selectionCount++;
                            selectedFiles.push(files[f]);
                        }
                        else if (emptySelection && action.supportsEmptySelect()) {
                            // special case for flow actions, TODO: get the flow guys to buy into the
                            // idea of either sending down all files in the folder or running the action
                            // from the parent folder (with the child folder selected)
                            if (files[f] == currentDir) {
                                selectionCount++;
                                selectedFiles.push(files[f]);
                            }
                        }
                    }

                    if (selectionCount >= 1) {
                        if (selectionCount == 1 || action.supportsMultiSelect()) {
                            action.setEnabled(true);
                            action.setMessage(
                                    this.shortMsgEnabledTpl.apply({count: selectionCount, total: selections.length}),
                                    this.longMsgEnabledTpl.apply({files: selectedFiles})
                            );
                        }
                        else {
                            action.setEnabled(false);
                            action.setMessage(
                                    this.shortMsgTpl.apply({msg: 'only one file can be selected at a time'}),
                                    this.longMsgNoMultiSelectTpl.apply({files: selectedFiles})
                            );
                        }
                    }
                    else {
                        action.setEnabled(false);
                        action.setMessage(
                                this.shortMsgTpl.apply({msg: 'none of the selected files can be used'}),
                                this.longMsgNoMatchTpl.apply({files: files})
                        );
                    }
                }
            }

            if (this.pipelineActions.length > 0)
                this.enableImportData(true);

            //
            // Update any action buttons that have been activated
            //
            var btn, disable;

            Ext4.iterate(this.actionMap, function(providerId, action) {
                btn = Ext4.getCmp(this.linkIdMap[providerId]);

                //
                // Check if the action button supports multiple selection
                //
                if (btn) {
                    disable = selections.length == 0 || (!action.supportsMultiSelect() && selections.length > 1);
                    btn.setDisabled(disable);
                }

            }, this);
        }

        if (this.showDetails) {
            if (selectedRecords.length == 1)
                this.getDetailPanel().update(selectedRecords[0].data);
            else if (this.currentDirectory)
                this.getDetailPanel().update(this.currentDirectory.data);
        }

        this.fireEvent('selectionchange', File.panel.Browser._toggleActions(this.fileSystem, this.actions, selectedRecords));

        this.selectedRecord = selectedRecords[0];

        this.changeTestFlag(true, true);

        this.selectionProcessed = true;
    },

    getGridCfg : function() {
        var config = Ext4.Object.merge({}, this.gridConfig);

        // Optional Configurations
        Ext4.applyIf(config, {
            flex : 4,
            region : 'center',
            border: false,
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
                    if (rec && rec.data) {
                        if (this.fireEvent('doubleclick', rec)) {
                            this.viewFile(rec);
                        }
                    }
                },

                cellcontextmenu : function (grid, td, cellIndex, record, tr, rowIndex, e, eOpts) {
                    if (record && record.data) {

                        // Issue 21404: Can no longer copy download link from files in file browser
                        // Don't show the custom context menu for columns containing a link (by marking the column
                        // with 'showContextMenu = false') and let the browser show it's right-click menu instead.
                        var showContext = true;
                        var columns = grid.getGridColumns();
                        if (cellIndex > 0 && cellIndex < columns.length) {
                            var column = columns[cellIndex];
                            if (column.initialConfig && column.initialConfig.showContextMenu === false)
                                showContext = false;
                        }

                        if (showContext)
                            this.showContextMenu(record, e);
                    }
                },

                containercontextmenu : function (grid, e, eOpts) {
                    this.showContextMenu(null, e);
                },
                afterrender : function() {
                    this.changeTestFlag(true, true);
                },
                scope : this
            }
        });

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

    /**
     * Is called immediately after a folder change has occurred. NOTE: This does
     * not occur necessarily after the grid has loaded so do not modify the grid state.
     * @param path
     * @param model
     */
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
        this.currentDirectory = model;
        this.changeTestFlag(true, true);

        if (this.showDetails) {
            if (this.currentDirectory)
                this.getDetailPanel().update(this.currentDirectory.data);
        }

        // Save the current folder in a state (cookie) for use on start
        if (this.hasStatePrefix() && this.STATE_LOCK !== true) {
            Ext4.state.Manager.set(this.statePrefix + '.currentDirectory', model.data.id);
        }
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
            cls : 'upload-files-panel',
            hidden : !this.expandUpload,
            fileSystem : this.fileSystem,
            style: 'border-bottom: 1px solid #b4b4b4;',
            listeners : {
                transfercomplete : function(options) {
                    this.reload({
                        callback: function() {
                            //
                            // Reconfigure the actions based on any new files that are present
                            //
                            this.on('pipelineconfigured', function() {
                                this.clearGridSelection();

                                // Only show file property editor after transfercomplete if extra properties exist
                                if (this.getExtraColumns().length > 0) {
                                    this.onCustomFileProperties(options);
                                }
                            }, this, {single: true});
                            this.configureActions(true);
                        },
                        scope: this
                    });
                },
                render : {
                    fn : function(up) {
                        var store = this.getComponent('treenav').getView().getTreeStore();
                        up.changeWorkingDirectory(this.getFolderOffset(), store.tree.root, this.getCurrentDirectory());
                    },
                    single: true
                },
                show : function() {
                    Ext4.defer(function() {
                        var btn = this.actions.upload;
                        if (!btn.pressed) {
                            btn.toggle(true);
                        }
                    }, 100, this);
                },
                hide : function() {
                    Ext4.defer(function() {
                        var btn = this.actions.upload;
                        if (btn.pressed) {
                            btn.toggle(false);
                        }
                    }, 100, this);
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
                '<tpl if="description != undefined && description.length">' +
                    '<tr><th>Description:</td><td>{description:htmlEncode}</td></tr>' +
                '</tpl>' +
                '<tpl if="lastmodified != undefined">' +
                    '<tr><th>Modified:</th><td>{lastmodified:this.renderDate}</td></tr>' +
                '</tpl>' +
                '<tpl if="createdby != undefined && createdby.length">' +
                    '<tr><th>Created By:</th><td>{createdby}</td></tr>' +
                '</tpl>' +
                '<tpl if="size != undefined && size">' +
                    '<tr><th>Size:</th><td>{size:this.renderSize}</td></tr>' +
                '</tpl>' +
                '<tr><th>WebDav URL:</th><td colspan="3"><a target="_blank" href="{[values.href||values.uri]}">{[values.href||values.uri]}</a></td></tr>' +
           '</table>',
        {
            renderDate : function(d) {
                return this.dateRenderer(d);
            },
            renderSize : function(d) {
                return this.detailsSizeRenderer(d);
            }
        },{
            dateRenderer : this.dateRenderer,
            detailsSizeRenderer : this.detailsSizeRenderer
        });

        this.details = Ext4.create('Ext.Panel', {
            region : 'south',
            minHeight : 100,
            tpl : detailsTpl,
            border: false,
            style: 'border-top: 1px solid #b4b4b4;',
            listeners: {
                afterrender: {
                    fn: function(p) { Ext4.defer(this.detailCheck, 250, this); },
                    single: true,
                    scope: this
                },
                scope: this
            },
            scope: this
        });

        this.on('folderchange', function(){ this.details.update(''); }, this);

        return this.details;
    },

    detailCheck : function() {
        if (this.showDetails) {
            if (this.getHeight() < 300) {
                this.getDetailPanel().hide();
            }
            else {
                this.getDetailPanel().show();
            }
        }
    },

    reload : function(options) {
        // Reload stores
        if (this.STATE_LOCK !== true)
        {
            this.getFileStore().load(options);
            var nodes = this.tree.getSelectionModel().getSelection(),
                    treeStore = this.tree.getStore();

            treeStore.on('load', function (s)
            {
                this.tree.getView().refresh();
            }, this, {single: true});

            var node = (nodes && nodes.length ? nodes[0] : treeStore.getRootNode());
            treeStore.load({node: node});

            if (this.showDetails)
            {
                this.getDetailPanel().update(node.data);
            }
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
                        path : path + this.folderSeparator + folder,
                        success : function(path) {
                            win.close();
                            this.onRefresh();
                        },
                        failure : function(response, options) {
                            var extraErrorInfo = response.errors && response.errors.length ? response.errors[0] : null;
                            var message;
                            if (response.status == 405)
                                message = 'Failed to create directory on server. This directory already exists.';
                            else if (extraErrorInfo && extraErrorInfo.message)
                            {
                                if (extraErrorInfo.resourceName)
                                    message = extraErrorInfo.resourceName + ": ";
                                message += extraErrorInfo.message;
                            }
                            else
                                message = 'Failed to create directory on server. This directory may already exist '
                                        + 'or this may be a server configuration problem. Please contact the site administrator.';
                            this.showErrorMsg('Error', message);
                        },
                        scope : this
                    });
                }
            }

        };

        var win = Ext4.create('Ext.Window', {
            title : 'Create Folder',
            modal : true,
            autoShow : true,
            items : [{
                xtype: 'form',
                itemId: 'foldernameform',
                border: false, frame : false,
                padding: 10,
                items : [{
                    xtype : 'textfield',
                    name : 'folderName',
                    itemId : 'foldernamefield',
                    flex : 1,
                    allowBlank : false,
                    emptyText : 'Folder Name',
                    width : 250,
                    regex: /^[^@\/\\;:?<>*|"^][^\/\\;:?<>*|"^]*$/,
                    regexText: "Folder must be a legal filename and not start with '@' or contain one of '/', '\\', ';', ':', '?', '<', '>', '*', '|', '\"', or '^'",
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

        var recs = this.getGridSelection();
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
                                        this.on('pipelineconfigured', function() {
                                            this.clearGridSelection();

                                        }, this, {single: true});
                                        this.configureActions(true);

                                        // Delete is a bit of a special case -- we want to reset the store
                                        // completely so there are no longer any cached records

                                        var grid = this.getGrid();
                                        if (grid) {
                                            grid.getStore().data.clear();
                                            this.onRefresh();
                                        }
                                    }
                                },
                                failure : function(response) {
                                    var extraErrorInfo = response.errors && response.errors.length ? response.errors[0] : null;
                                    var message = null;
                                    if (extraErrorInfo && extraErrorInfo.message)
                                    {
                                        if (extraErrorInfo.resourceName)
                                            message = extraErrorInfo.resourceName + ": ";
                                        message += extraErrorInfo.message;
                                    }
                                    else
                                        message = 'Failed to delete.';
                                    this.showErrorMsg('Error', message);
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

        var recs = this.getGridSelection();

        if (recs.length >= 1) {
            this.fileSystem.downloadResource({
                record: recs,
                directoryURL : LABKEY.ActionURL.getBaseURL(true) + LABKEY.ActionURL.encodePath(this.getFolderURL())
            });
        }
        else {
            this.showErrorMsg('Error', 'Please select a file or folder to download.');
        }
    },

    onViewFile : function () {
        var recs = this.getGridSelection();
        if (recs.length == 1) {
            var rec = recs[0];
            this.viewFile(rec);
        }
        else {
            this.showErrorMsg('Error', 'Please select a file to view.');
        }
    },

    viewFile : function (rec) {
        if (rec.data.collection)
            this.changeFolder(rec);
        else if (rec.data.href)
            window.open(rec.data.href, '_blank');
    },

    onEmailPreferences : function() {
        Ext4.create('File.panel.EmailProps', { containerPath: this.containerPath }).show();
    },

    onMovePath : function() {
        if (!this.getCurrentDirectory()) {
            console.error('onMovePath failed.');
            return;
        }

        var selections = this.getGridSelection();

        var listeners = {};
        if (!this.showHidden) {
            listeners['beforeappend'] = this.onShowHidden;
        }

        //
        // Setup a custom tree to display for move selection
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
                this.showErrorMsg('Error', 'Please pick a destination folder.');
                return;
            }

            var currentDir = this.getCurrentDirectory();
            if (node.data.id == currentDir || node.data.id == (currentDir+"/")) {
                this.showErrorMsg('Error', 'The selection is already in the selected destination folder.');
                return;
            }

            var allowMove = true;
            Ext4.each(selections, function(rec){
                if (node.data.id.indexOf(rec.data.id) == 0) {
                    this.showErrorMsg('Error', 'The selection can not be moved to itself or a subfolder within the selection.');
                    allowMove = false;
                }
            }, this);
            if (!allowMove)
                return;

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
            // XXX: Setting default focus to the treepanel doesn't seem to actual set focus to it
            defaultFocus: tp,
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
            }],
            listeners: {
                afterrender: function (win) {
                    Ext4.create("Ext.util.KeyNav", win.getEl(), {
                        enter: function () {
                            okHandler.call(this, win);
                        }
                    })
                },
                scope: this
            }
        });
    },

    doMove : function(toMove, destination) {

        for (var i = 0; i < toMove.length; i++){
            var selected = toMove[i];

            var newPath = this.fileSystem.concatPaths(destination, (selected.newName || selected.record.data.name));

            // WebDav.movePath handles the "do you want to overwrite" case
            this.fileSystem.movePath({
                fileRecord : selected,
                // TODO: Doesn't handle @cloud.  Shouldn't the fileSystem know this?
                source: selected.record.data.id,
                destination: newPath,
                isFile: !selected.record.data.collection,
                success: function(fs, src, dest) {
                    // Does this work for multiple file moves?
                    this.on('pipelineconfigured', function() {
                        this.clearGridSelection();
                        var grid = this.getGrid();
                        if (grid) {
                            grid.getStore().data.clear();
                            this.onRefresh();
                            this.refreshTreePath(dest);
                        }
                    }, this, {single: true});
                    this.configureActions(true);
                },
                failure: function(response) {
                    var extraErrorInfo = response.errors && response.errors.length ? response.errors[0] : null;
                    var message = null;
                    if (response.status == 412)
                        message = 'Failed to move file on server. File already exists in destination folder. Please remove the file in the destination folder and try again.';
                    else if (extraErrorInfo && extraErrorInfo.message)
                    {
                        if (extraErrorInfo.resourceName)
                            message = extraErrorInfo.resourceName + ": ";
                        message += extraErrorInfo.message;
                    }
                    else if (response.status == 500)
                        message = 'Failed to move file on server. This may be a server configuration problem. Please contact the site administrator.';
                    if (message)
                        this.showErrorMsg('Error', message);
                    else
                        LABKEY.Utils.displayAjaxErrorResponse(response);
                },
                scope: this
            });
        }
    },

    onRename : function() {

        if (!this.getCurrentDirectory()) {
            console.error('onRename failed.');
            return;
        }

        var me = this;
        var okHandler = function() {
            var field = Ext4.getCmp('renameText');
            var newName = field.getValue();

            if (!newName || !field.isValid()) {
                return;
            }

            if (newName.toLowerCase() == win.origName.toLowerCase())
            {
                var noun = win.fileRecord.get('collection') ? "folder" : "file";
                field.markInvalid("Unable to rename " + noun + " by casing only");
                return;
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

        var name = this.selectedRecord.get('name');

        var win = Ext4.create('Ext.window.Window', {
            title: "Rename",
            autoHeight: true,
            modal: true,
            cls : 'data-window',
            closeAction: 'destroy',
            origName: name,
            fileRecord: this.selectedRecord,
            draggable : false,
            autoShow : true,
            defaultFocus: '#nameField',
            items: [{
                xtype: 'form',
                labelAlign: 'top',
                border : false, frame : false,
                padding : 10,
                items: [{
                    xtype: 'textfield',
                    id : 'renameText',
                    itemId: 'nameField',
                    allowBlank: false,
                    regex: /^[^@\/\\;:?<>*|"^][^\/\\;:?<>*|"^]*$/,
                    regexText: "Folder must be a legal filename and not start with '@' or contain one of '/', '\\', ';', ':', '?', '<', '>', '*', '|', '\"', or '^'",
                    width: 250,
                    labelAlign: 'top',
                    value: name
                }]
            }],
            buttons: [{
                text: 'Rename',
                handler: function(btn) {
                    okHandler.call(this, win);
                },
                scope: this
            },{
                text: 'Cancel',
                handler: function() {
                    win.close();
                }
            }],
            listeners: {
                afterrender: function (win) {
                    Ext4.create("Ext.util.KeyNav", win.getEl(), {
                        enter: function () {
                            okHandler.call(this, win);
                        }
                    })
                },
                scope: this
            }
        });

    },

    onRefresh : function() {
        if (this.STATE_LOCK !== true) {

            if (!this.refreshTask) {
                this.refreshTask = new Ext4.util.DelayedTask(this.reload, this);
            }
            this.refreshTask.delay(100);
            this.selectedRecord = undefined;
        }
    },

    refreshTreePath : function(path) {
        var sep = this.folderSeparator;
        var d = path.split(sep);
        var destId = '';
        for (var i=0; i < d.length-1; i++) {
            if (d[i].length > 0)
                destId += sep + d[i];
        }

        var nodeId = this.ensureNodeId(destId);
        if (nodeId) {
            var root = this.tree.getStore().getRootNode();
            if (root) {
                var target;
                // check if the root node matches
                if (nodeId == root.getId()) {
                    target = root;
                }
                else {
                    var node = root.findChild('id', nodeId, true);
                    if (node) {
                        target = node;
                    }
                }

                if (target) {
                    this.tree.getStore().load({node: target});
                }
            }
        }

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
        up.setVisible(!up.isVisible());
    },

    showAdminWindow : function() {
        if (!this.isWebDav) {
            if (this.adminWindow && !this.adminWindow.isDestroyed) {
                this.adminWindow.show();
            }
            else {
                File.panel.Browser._getPipelineConfiguration(function(response) {
                    var pipeConfig = Ext4.decode(response.responseText);
                    this.adminWindow = Ext4.create('Ext.window.Window', {
                        cls: 'data-window',
                        title: 'Manage File Browser Configuration',
                        closeAction: 'destroy',
                        layout: 'fit',
                        modal: true,
                        autoShow: true,
                        items: [{
                            xtype : 'fileadmin',
                            width : 750,
                            height: 562,
                            plain : true,
                            border: false,
                            pipelineFileProperties: pipeConfig.config,
                            fileProperties : pipeConfig.fileProperties,
                            isPipelineRoot : this.isPipelineRoot,
                            containerPath : this.containerPath,
                            listeners: {
                                success: function(fileAdmin, updates) {

                                    this.configureActions(true);

                                    if (updates.gridChanged) {
                                        this.initGridColumns();
                                    }

                                    if (updates.expandUploadChanged) {
                                        // probably a better way, for now just set it here
                                        this.expandUpload = !this.expandUpload;

                                        if (this.showUpload === true) {
                                            // only update the panel if necessary
                                            var upload = this.getUploadPanel(),
                                                upVisible = upload.isVisible();

                                            if (this.expandUpload && !upVisible) {
                                                upload.show();
                                            }
                                            else if (!this.expandUpload && upVisible) {
                                                upload.hide();
                                            }
                                        }
                                    }

                                    this.adminWindow.close();
                                },
                                cancel: function() { this.adminWindow.close(); },
                                scope: this
                            }
                        }]
                    });
                }, this.containerPath, this);
            }
        }
    },


    onEditFileProps : function() {
        this.onCustomFileProperties({
            fileRecords : this.getGridSelection(),
            showErrors : true
        });
    },

    onCustomFileProperties : function(options) {

        if (!options) {
            return;
        }

        // check for file names instead of file records
        if (options.fileNames)
        {
            options.fileRecords = [];
            for (var i = 0; i < options.fileNames.length; i++)
            {
                var index = this.getFileStore().findExact('name', options.fileNames[i].name);
                if (index > -1)
                {
                    var record = this.getFileStore().getAt(index);
                    options.fileRecords.push(record);
                }
            }
        }

        // no selected files in options
        if (!options.fileRecords || options.fileRecords.length == 0)
        {
            if (options.showErrors)
                this.showErrorMsg('Error', 'No files selected.');
            return;
        }

        Ext4.each(options.fileRecords, function(record){
            if (!this.fileProps[record.data.id]) {
                this.fileProps[record.data.id] = record.data;
            }

            // Copy 'description' from fileRecord to fileProps.
            this.fileProps[record.data.id]["Flag/Comment"] = record.data.description;
        }, this);

        Ext4.create('Ext.Window', {
            title : 'Extended File Properties',
            id : 'editFilePropsWin',
            cls : 'data-window',
            modal : true,
            width : 400,
            autoShow: true,
            items : Ext4.create('File.panel.EditCustomFileProps', {
                itemId: 'editFilePropsPanel',
                extraColumns : this.getExtraColumns(),
                fileRecords : options.fileRecords,
                winId : 'editFilePropsWin',
                fileProps : this.fileProps
            }),
            listeners : {
                afterrender: function (win) {
                    Ext4.create("Ext.util.KeyNav", win.getEl(), {
                        enter: function () {
                            var editPropsPanel = win.down('#editFilePropsPanel');
                            if (editPropsPanel) {
                                editPropsPanel.doSave();
                            }
                        }
                    });
                },
                successfulsave : function() {
                    //TODO:  Improve preformance
                    this.getGrid().getStore().load();
                },
                scope : this
            }
        });
    },

    setFormFieldTooltip : function(component, icon) {
        var label = component.getEl().down('label');
        if (label) {
            var helpImage = label.createChild({
                tag: 'img',
                src: LABKEY.contextPath + '/_images/' + icon,
                style: 'margin-bottom: 0px; margin-left: 8px; padding: 0px;',
                'data-qtip': component.tooltip,
                width: 12,
                height: 12
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
    },

    showErrorMsg : function(title, msg) {
        Ext4.Msg.show({
            title: title,
            msg: msg,
            cls : 'data-window',
            icon: Ext4.Msg.ERROR, buttons: Ext4.Msg.OK
        });
    },

    showContextMenu : function(record, e) {
        var menu = this.getContextMenu();

        // iterate the menu items actions to restore original action's text
        // We have to do this every time we reshow the menu. After some actions
        // (e.g., renaming a file), the actions will unset their text causing
        // the context menu items to lose their label... :(
        menu.items.each(function (item) {
            if (item.itemId) {
                var action = this.actions[item.itemId];
                if (action) {
                    item.setText(action.initialConfig.hardText);
                }
            }
        }, this);

        menu.setItem(record);
        menu.showAt(e.getX(), e.getY());
        e.preventDefault();
    },

    getContextMenu : function () {
        if (!this.contextMenu) {
            // TODO: This should respect the visibility/availability as specified by the pipeline configuration
            var actions = this.getActions(),
                items;

            if (this.isWebDav) {
                items = [
                    actions.upload,
                    actions.download,
                    '-',
                    actions.viewFile,
                    actions.createDirectory,
                    '-',
                    actions.movePath,
                    actions.deletePath,
                    actions.renamePath,
                    '-',
                    actions.refresh
                ];
            }
            else {
                items = [
                    actions.upload,
                    actions.download,
                    actions.importData,
                    '-',
                    actions.viewFile,
                    actions.createDirectory,
                    '-',
                    actions.movePath,
                    actions.deletePath,
                    actions.renamePath,
                    actions.editFileProps,
                    '-',
                    actions.refresh
                ];
            }

            this.contextMenu = Ext4.create('File.panel.ContextMenu', {
                items: items
            });
        }

        return this.contextMenu;
    },

    hasStatePrefix : function() {
        return Ext4.isString(this.statePrefix);
    }
});

Ext4.define('File.store.GridStore', {
    extend: 'Ext.data.Store',

    LOCKED: false,

    QUEUED: false,

    CURRENT: false,

    load : function() {
        if (this.LOCKED !== true) {
            if (this.isLoading()) {
                this.QUEUED = this.getProxy().url;
                if (this.QUEUED != this.CURRENT)
                {
                    this.on('load', function() { this.QUEUED = false; this.load(); }, this, {single: true});
                }
            }
            else {
                this.CURRENT = this.getProxy().url;
                this.callParent(arguments);
            }
        }
    }
});
