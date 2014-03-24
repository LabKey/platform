/*
 * Copyright (c) 2012-2014 LabKey Corporation
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
     * @cfg {Ext4.util.Format.fileSize} fileSize
     */
    sizeRenderer : function(value, metaData, record) {
        var render = null;

        // Details panel renders a template without a record.
        if ((record && !record.get("collection")) || value) {
             render = Ext4.util.Format.fileSize(value);
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
            result += Ext4.util.Format.htmlEncode(value[i].message);
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
            result += Ext4.util.Format.htmlEncode(value[i].message);
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

        _toggleActions : function(fileSystem, actions, selection) {

            // update action button state based on the number of selected records
            var types = File.panel.Browser.actionTypes;
            for (var key in actions) {
                if (actions.hasOwnProperty(key)) {
                    var actionType = actions[key].initialConfig.actionType;
                    if (!actionType) {
                        continue;
                    }

                    if (actionType == types.NOMIN) {
                        // do nothing
                    }
                    else if (selection.length == 0) {
                        // Set disabled when case is: ATLEASTONE or ONLYONE or ATLEASTTWO
                        actions[key].setDisabled((actionType == types.ATLEASTONE || actionType == types.ONLYONE || actionType == types.ATLEASTTWO));
                    }
                    else if (selection.length == 1) {
                        // Set disabled when case is: ATLEASTTWO or NOFILE
                        actions[key].setDisabled((actionType == types.ATLEASTTWO || actionType == types.NOFILE));
                    }
                    else if (selection.length >= 2) {
                        // Set disabled when case is: ONLYONE or NOFILE
                        actions[key].setDisabled((actionType == types.ONLYONE || actionType == types.NOFILE));
                    }
                }
            }

            // update the action button state based on the selected record options
            for (var i = 0; i < selection.length; i++)
            {
                if (!fileSystem.canDelete(selection[i]))
                    actions.deletePath.disable();

                if (!fileSystem.canMove(selection[i]))
                    actions.movePath.disable();
            }
        },

        actionTypes : {
            NOMIN: 'NOMIN', // 'No file required',
            ATLEASTONE: 'ATLEASTONE', // 'At least one file required',
            ONLYONE: 'ONLYONE', // 'Only one file allowed',
            ATLEASTTWO: 'ATLEASTTWO', // 'Needs at least two files',
            NOFILE: 'NOFILE' // 'Only works with no files'
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

        this.items = this.getItems();

        if (this.showToolbar)
            this.initializeToolbar();

        // Attach listeners
        this.on('folderchange', this.onFolderChange, this);
        //Ext4.Ajax.timeout = 60000;
        File.panel.Browser._toggleActions(this.fileSystem, this.actions, []);
        this.callParent();
    },

    createActions : function() {
        this.actions = {};

        this.actions.parentFolder = new Ext4.Action({
            text: 'Parent Folder',
            hardText: 'Parent Folder',
            itemId: 'parentFolder',
            tooltip: 'Navigate to parent folder',
            iconCls: 'iconUp',
            hardIconCls: 'iconUp',
            disabledClass:'x-button-disabled',
            handler : this.onNavigateParent,
            actionType : File.panel.Browser.actionTypes.NOMIN,
            scope: this,
            hideText: true
        });

        this.actions.refresh = new Ext4.Action({
            text: 'Refresh',
            hardText: 'Refresh',
            itemId: 'refresh',
            tooltip: 'Refresh the contents of the current folder',
            iconCls: 'iconReload',
            hardIconCls: 'iconReload',
            disabledClass: 'x-button-disabled',
            handler : this.onRefresh,
            actionType : File.panel.Browser.actionTypes.NOMIN,
            scope: this,
            hideText: true
        });

        this.actions.createDirectory = new Ext4.Action({
            text: 'Create Folder',
            hardText: 'Create Folder',
            itemId: 'createDirectory',
            iconCls:'iconFolderNew',
            hardIconCls: 'iconFolderNew',
            tooltip: 'Create a new folder on the server',
            disabledClass: 'x-button-disabled',
            handler : this.onCreateDirectory,
            actionType : File.panel.Browser.actionTypes.NOMIN,
            scope: this,
            hideText: true
        });

        this.actions.download = new Ext4.Action({
            text: 'Download',
            hardText: 'Download',
            itemId: 'download',
            tooltip: 'Download the selected files or folders',
            iconCls: 'iconDownload',
            hardIconCls: 'iconDownload',
            disabledClass: 'x-button-disabled',
            disabled: true,
            handler: this.onDownload,
            actionType : File.panel.Browser.actionTypes.ATLEASTONE,
            scope: this,
            hideText: true
        });
        
        this.actions.deletePath = new Ext4.Action({
            text: 'Delete',
            hardText: 'Delete',
            itemId: 'deletePath',
            tooltip: 'Delete the selected files or folders',
            iconCls: 'iconDelete',
            hardIconCls: 'iconDelete',
            disabledClass: 'x-button-disabled',
            handler: this.onDelete,
            actionType : File.panel.Browser.actionTypes.ATLEASTONE,
            scope: this,
            disabled: true,
            hideText: true
        });

        this.actions.renamePath = new Ext4.Action({
            text: 'Rename',
            hardText: 'Rename',
            itemId: 'renamePath',
            tooltip: 'Rename the selected file or folder',
            iconCls: 'iconRename',
            hardIconCls: 'iconRename',
            disabledClass: 'x-button-disabled',
            handler : this.onRename,
            actionType : File.panel.Browser.actionTypes.ONLYONE,
            scope: this,
            disabled: true,
            hideText: true
        });

        this.actions.movePath = new Ext4.Action({
            text: 'Move',
            hardText: 'Move',
            itemId: 'movePath',
            tooltip: 'Move the selected file or folder',
            iconCls: 'iconMove',
            hardIconCls: 'iconMove',
            disabledClass: 'x-button-disabled',
            handler : this.onMovePath,
            actionType : File.panel.Browser.actionTypes.ATLEASTONE,
            scope: this,
            disabled: true,
            hideText: true
        });

        this.actions.help = new Ext4.Action({
            text: 'Help',
            hardText: 'Help',
            itemId: 'help',
            scope: this
        });

        this.actions.showHistory = new Ext4.Action({
            text: 'Show History',
            hardText: 'Show History',
            itemId: 'showHistory',
            scope: this
        });

        this.actions.uploadTool = new Ext4.Action({
            text: 'Multi-file Upload',
            hardText: 'Multi-file Upload',
            itemId: 'uploadTool',
            iconCls: 'iconUpload',
            hardIconCls: 'iconUpload',
            tooltip: "Upload multiple files or folders using drag-and-drop<br>(requires Java)",
            disabled: true,
            scope: this
        });

        this.actions.upload = new Ext4.Action({
            text: 'Upload Files',
            hardText: 'Upload Files',
            itemId: 'upload',
            enableToggle: true,
            pressed: this.showUpload && this.expandUpload,
            iconCls: 'iconUpload',
            hardIconCls: 'iconUpload',
            handler : this.onUpload,
            scope: this,
            disabledClass:'x-button-disabled',
            tooltip: 'Upload files or folders from your local machine to the server'
        });

        this.actions.appletFileAction = new Ext4.Action({
            text: '&nbsp;&nbsp;&nbsp;&nbsp;Choose File&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;',
            hardText: '&nbsp;&nbsp;&nbsp;&nbsp;Choose File&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;',
            itemId: 'appletFileAction',
            scope: this,
            disabled: false,
            cls: 'applet-button'
        });

        this.actions.appletDirAction = new Ext4.Action({
            text: '&nbsp;Choose Folder&nbsp;',
            hardText: '&nbsp;Choose Folder&nbsp;',
            itemId: 'appletDirAction',
            scope: this,
            disabled: false,
            cls: 'applet-button'
        });

        this.actions.appletDragAndDropAction = new Ext4.Action({
            text: 'Drag and Drop&nbsp;',
            hardText: 'Drag and Drop&nbsp;',
            itemId: 'appletDragAndDropAction',
            scope: this,
            disabled: false,
            cls: 'applet-button'
        });

        this.actions.folderTreeToggle = new Ext4.Action({
            text: 'Toggle Folder Tree',
            hardText: 'Toggle Folder Tree',
            itemId: 'folderTreeToggle',
            enableToggle: true,
            iconCls: 'iconFolderTree',
            hardIconCls: 'iconFolderTree',
            disabledClass:'x-button-disabled',
            tooltip: 'Show or hide the folder tree',
            hideText: true,
            handler : function() { this.tree.toggleCollapse(); },
            actionType : File.panel.Browser.actionTypes.NOMIN,
            scope: this
        });

        this.actions.importData = new Ext4.Action({
            text: 'Import Data',
            hardText: 'Import Data',
            itemId: 'importData',
            handler: this.onImportData,
            iconCls: 'iconDBCommit',
            hardIconCls: 'iconDBCommit',
            disabledClass:'x-button-disabled',
            tooltip: 'Import data from files into the database, or analyze data files',
            actionType : File.panel.Browser.actionTypes.NOMIN,
            scope: this
        });

        this.actions.customize = new Ext4.Action({
            text: 'Admin',
            hardText: 'Admin',
            itemId: 'customize',
            iconCls: 'iconConfigure',
            hardIconCls: 'iconConfigure',
            disabledClass:'x-button-disabled',
            tooltip: 'Configure the buttons shown on the toolbar',
            actionType : File.panel.Browser.actionTypes.NOMIN,
            handler: this.showAdminWindow,
            scope: this
        });

        this.actions.editFileProps = new Ext4.Action({
            text: 'Edit Properties',
            hardText: 'Edit Properties',
            itemId: 'editFileProps',
            iconCls: 'iconEditFileProps',
            hardIconCls: 'iconEditFileProps',
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
            hardText: 'Email Preferences',
            itemId: 'emailPreferences',
            iconCls: 'iconEmailSettings',
            hardIconCls: 'iconEmailSettings',
            disabledClass:'x-button-disabled',
            tooltip: 'Configure email notifications on file actions.',
            hideText: true,
            actionType : File.panel.Browser.actionTypes.NOMIN,
            handler : this.onEmailPreferences,
            scope: this
        });

        this.actions.auditLog = new Ext4.Action({
            text: 'Audit History',
            hardText: 'Audit History',
            itemId: 'auditLog',
            iconCls: 'iconAuditLog',
            hardIconCls: 'iconAuditLog',
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

                    action.setText(action.initialConfig.hideText ? undefined : action.initialConfig.hardText);
                    action.setIconCls(action.initialConfig.hideIcon ? undefined : action.initialConfig.hardIconCls);
                }
            }
        }
    },

    initializeToolbar : function() {
        this.isWebDav ? this.configureWebDavActions() : this.configureActions();
    },

    configureActions : function() {
        if (!this.isWebDav) {
            var configure = function(response) {
                var json = Ext4.JSON.decode(response.responseText);
                if (json.config) {
                    this.initializeActions();
                    this.updateActions();
                    // First intialize all the actions prepping them to be shown

                    // Configure the actions on the toolbar based on whether they should be shown
                    this.configureTbarActions({tbarActions: json.config.tbarActions, actions: json.config.actions});
                }
            };
            File.panel.Browser._clearPipelineConfiguration(this.containerPath);
            File.panel.Browser._getPipelineConfiguration(configure, this.containerPath, this);
        }
    },

    configureWebDavActions : function() {
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

        this.dockedItems = [{
            xtype: 'toolbar', itemId: 'actionToolbar', dock: 'top', items: baseItems, enableOverflow : true
        }];
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

                    // issue 19166
                    if (actionConfig.hideText && actionConfig.hideIcon)
                        continue;

                    action = this.actions[actionConfig.id];

                    action.initialConfig.hideText = actionConfig.hideText;
                    if(action.initialConfig.hideText && action.initialConfig.text != undefined)
                    {
                        action.initialConfig.prevText = action.initialConfig.text;
                        action.initialConfig.text = undefined;
                    }
                    else if(!action.initialConfig.hideText && action.initialConfig.text == undefined)
                    {
                        action.initialConfig.text = action.initialConfig.prevText;
                    }

                    action.initialConfig.hideIcon = actionConfig.hideIcon;
                    if(action.initialConfig.hideIcon && action.initialConfig.iconCls != undefined)
                    {
                        action.initialConfig.prevIconCls = action.initialConfig.iconCls;
                        action.initialConfig.iconCls = undefined;
                    }
                    else if(!action.initialConfig.hideIcon && action.initialConfig.iconCls == undefined)
                    {
                        action.initialConfig.iconCls = action.initialConfig.prevIconCls;
                    }

                    buttons.push(action);
                }
            }
        }
        else if (this.tbarItems) {

            for (i=0; i < this.tbarItems.length; i++) {
                action = this.actions[this.tbarItems[i]];
                buttons.push(action);
            }
        }

        this.linkIdMap = {};
        if (actionButtons) {
            for (i=0; i < actionButtons.length; i++) {
                if (actionButtons[i].links[0].display === 'toolbar') {

                    //
                    // Store an id lookup for the component id of this button
                    // based on it's provider id
                    //
                    var id = Ext4.id();
                    this.linkIdMap[actionButtons[i].links[0].id] = id;

                    var action = new Ext4.Action({
                        id : id,
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

        this.addDocked({xtype: 'toolbar', itemId: 'actionToolbar', dock: 'top', items: buttons, enableOverflow : true});
    },

    getToolbar : function() {
        return this.getComponent('actionToolbar');
    },

    executeToolbarAction : function(item, e) {
        var action = this.actionMap[item.itemId];

        if (action) {
            this.executeImportAction(action);
        }
    },

    initGridColumns : function() {

        var columns = [{
            xtype : 'templatecolumn',
            text  : '',
            dataIndex : 'icon',
            sortable : !this.bufferFiles,
            width : 25,
            height : 20,
            tpl : '<img height="16px" width="16px" src="{icon}" alt="{type}">',
            scope : this
        },{
            xtype : 'templatecolumn',
            text  : 'Name',
            dataIndex : 'name',
            sortable : !this.bufferFiles,
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
        {header: "Last Modified",  flex: 1, dataIndex: 'lastmodified', sortable: !this.bufferFiles,  hidden: false, height : 20, renderer: this.dateRenderer},
        {header: "Size",           flex: 1, dataIndex: 'size',         sortable: !this.bufferFiles,  hidden: false, height : 20, renderer: this.sizeRenderer, align : 'right'},
        {header: "Created By",     flex: 1, dataIndex: 'createdby',    sortable: !this.bufferFiles,  hidden: false, height : 20, renderer:Ext4.util.Format.htmlEncode},
        {header: "Description",    flex: 1, dataIndex: 'description',  sortable: !this.bufferFiles,  hidden: false, height : 20, renderer:Ext4.util.Format.htmlEncode},
        {header: "Usages",         flex: 1, dataIndex: 'actions',      sortable: !this.bufferFiles,  hidden: false, height : 20, renderer: this.usageRenderer},
        {header: "Download Link",  flex: 1, dataIndex: 'fileLink',     sortable: !this.bufferFiles,  hidden: true, height : 20},
        {header: "File Extension", flex: 1, dataIndex: 'fileExt',      sortable: !this.bufferFiles,  hidden: true, height : 20, renderer:Ext4.util.Format.htmlEncode}
        ];
        this.setDefaultColumns(columns);
        if (!this.isWebDav) {
            File.panel.Browser._getPipelineConfiguration(this._onExtraColumns, this.containerPath, this);
        }

        return columns;
    },

    _onExtraColumns : function(response) {
        var finalColumns = [];
        var json = Ext4.JSON.decode(response.responseText);

        // initially populate the finalColumns array with the defaults
        var defaultColumns = this.getDefaultColumns();
        var customColumns = json.fileProperties, i;

        for (i = 0; i < defaultColumns.length; i++) {
            finalColumns.push(defaultColumns[i]);
        }

        for (i = 0; i < customColumns.length; i++) {
            var customCol = {
                header : customColumns[i].label || customColumns[i].name,
                flex : 1,
                dataIndex : customColumns[i].name,
                height : 20,
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
                retCols.push(columns[i].name);
            }
        }
        return retCols;
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
            columns : ['Name', 'Run', 'Data File URL'].concat(extraColumnNames),
            requiredVersion : '9.1',
            success : function(resp) {
                this.setFileObjects(resp.rows);
                var i, r, propName, rec, idx;

                for (i = 0; i < resp.rows.length; i++)
                {
                    // use the record 'Id' to lookup the fileStore record because it includes the relative path and file/folder name
                    var dataFileUrl = Ext4.Object.fromQueryString("url=" + resp.rows[i].DataFileUrl.value);
                    var strStartIndex = dataFileUrl.url.indexOf(this.getBaseURL());
                    var recId = dataFileUrl.url.substring(strStartIndex);
                    idx = this.getFileStore().findExact('id', recId);

                    if (idx >= 0)
                    {
                        rec = this.getFileStore().getAt(idx);
                        propName = this.fileProps[rec.get('id')];
                        if (!propName) {
                            propName = {};
                        }

                        var values = {};
                        for (r = 0; r < extraColumnNames.length; r++) {
                            var value = Ext4.util.Format.htmlEncode(resp.rows[i][extraColumnNames[r]].value);
                            if (resp.rows[i][extraColumnNames[r]].displayValue)
                                value = Ext4.util.Format.htmlEncode(resp.rows[i][extraColumnNames[r]].displayValue);
                            if (resp.rows[i][extraColumnNames[r]].url)
                                value = "<a href='" + resp.rows[i][extraColumnNames[r]].url + "'>" + value + "</a>";

                            values[extraColumnNames[r]] = value;

                            propName[extraColumnNames[r]] = resp.rows[i][extraColumnNames[r]].value;
                        }
                        rec.set(values);

                        propName.rowId = resp.rows[i]['RowId'].value;
                        propName.name = resp.rows[i]['Name'].value;
                        this.fileProps[rec.get('id')] = propName;
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
                if (this.getGrid()) {
                    this.getGrid().getSelectionModel().select([]);
                }
                this.fileStore.getProxy().url = LABKEY.ActionURL.encodePath(this.getFolderURL());
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

        var storeCfg = {};

        Ext4.apply(storeCfg, configs, {
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
        var node = this.tree.getView().getTreeStore().getRootNode().findChild('id', nodeId, true);
        if (!node) {
            var p = this.fileSystem.getParentPath(nodeId);
            if (p == this.folderSeparator) {
                return;
            }
            if (nodeId.length > 0 && nodeId.substring(nodeId.length-1) != this.folderSeparator) {
                nodeId += this.folderSeparator;
            }
            this.vStack.push(nodeId);
            this.ensureVisible(p);
        }
        else {
            if (!node.isLeaf()) {
                var s = this.vStack.pop();
                var fn = s ? function() { this.ensureVisible(s);  } : undefined;
                if (!s) {
                    this.changeFolder(node);
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
            var actionsReady = false;
            var pipelineReady = false;

            var actions;
            var me = this;

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

        if ((this.importDataEnabled || this.adminUser) && actions && actions.length) {
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
        var ag, pa, alreadyChecked = false,
            provider, action, actions = [], items = [],
            actionMap = {}, hasAdmin = false, link, label, enabled;

        //
        // make sure we've processed the current selection
        // ensureSelection()
        //
        this.onSelection(this.getGrid(), this.getGridSelection());

        //
        // Iterator over the action providers building the set of radios
        //
        for (ag in this.actionProviders) {

            if (this.actionProviders.hasOwnProperty(ag)) {
                provider = this.actionProviders[ag];
                pa = provider.actions[0];

                var radios = [];

                for (var i=0; i < provider.actions.length; i++) {
                    action = provider.actions[i];
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
                            width : '100%'
                        });

                        if (enabled) {
                            alreadyChecked = true;
                        }
                    }
                }

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
                    listeners : {
                        render: function(rg) {
                            this.setFormFieldTooltip(rg, rg.paEnabled ? 'info.png' : 'warning-icon-alt.png');
                        },
                        scope: this
                    }
                });
            }
        }

        var actionPanelId = Ext4.id();

        items.push({
            xtype: 'form',
            id: actionPanelId,
            bodyStyle: 'padding: 10px;',
            labelAlign: 'left',
            itemCls: 'x-check-group',
            items: actions
        });

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
            closeAction: 'close',
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
        if (this.actions && this.actions.importData && this.showToolbar) {
            var el = this.getToolbar().getEl();
            var cls = 'labkey-import-enabled';

            if (enabled) {
                el.addCls(cls);
                this.actions.importData.enable();
            }
            else {
                el.removeCls(cls);
                this.actions.importData.disable();
            }
        }
    },

    onSelection : function(grid, selectedRecords) {

        this.enableImportData(false);

        this.changeTestFlag(false, false);

        if (this.pipelineActions.length > 0) {

            var selections = selectedRecords;
            var emptySelection = false;

            var store = grid.getStore();
            var count = store.getCount();

            if (!selections.length && count > 0) {
                emptySelection = true;
                selections = store.getRange(0, count-1); // get all the available records
            }

            var action, files, selectionCount, selectedFiles, selectionMap = {};
            var currentDir = this.fileSystem.getDirectoryName(this.getCurrentDirectory());

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
            var btn, providerId, action;

            for (providerId in this.actionMap) {

                if (this.actionMap.hasOwnProperty(providerId)) {

                    btn = Ext4.getCmp(this.linkIdMap[providerId]);

                    if (btn) {

                        action = this.actionMap[providerId];

                        //
                        // Check if the action button supports multiple selection
                        //
                        if (selections.length == 0 || (!action.supportsMultiSelect() && selections.length > 1)) {
                            btn.setDisabled(true);
                            continue;
                        }

                        var files = this.actionMap[providerId].getFiles();

                        btn.setDisabled(false);
                    }
                }
            }
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

                            if (rec.data.collection)
                                this.changeFolder(rec);
                            else if (rec.data.href)
                                window.location = rec.data.href;
                        }
                    }
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
                closeUploadPanel : function() {
                    this.onUpload();
                    Ext4.each(this.actions.upload.items, function(action){
                        action.toggle(false);
                    });
                },
                transfercomplete : function(options) {
                    this.reload({
                        callback: function() {
                            //
                            // Reconfigure the actions based on any new files that are present
                            //
                            this.on('pipelineconfigured', function() {
                                this.clearGridSelection();
                                this.onCustomFileProperties(options);
                            }, this, {single: true});
                            this.configureActions();
                        },
                        scope: this
                    });
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
                '<tr><th>WebDav URL:</th><td><a target="_blank" href="{[values.href||values.uri]}">{[values.href||values.uri]}</a></td></tr>' +
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
        }, {dateRenderer : this.dateRenderer, sizeRenderer : this.sizeRenderer});

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
        this.getFileStore().load(options);
        var nodes = this.tree.getSelectionModel().getSelection(),
            treeStore = this.tree.getStore();

        treeStore.on('load', function(s) {
            this.tree.getView().refresh();
        }, this, {single: true});

        var node = (nodes && nodes.length ? nodes[0] : treeStore.getRootNode());
        treeStore.load({node: node});

        if (this.showDetails) {
            this.getDetailPanel().update(node.data);
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
                        failure : function(response) {
                            if (response.status == 405)
                            {
                                this.showErrorMsg('Error', 'Failed to create directory on server. This directory already exists.');
                            }
                            else
                            {
                                this.showErrorMsg('Error', 'Failed to create directory on server. This directory may already exist '
                                        + 'or this may be a server configuration problem. Please contact the site administrator.');
                            }
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

                                            //
                                            // Delete is a bit of a special case -- we want to reset the store
                                            // completly so there are no longer any cached records
                                            //
                                            var grid = this.getGrid();
                                            if (grid) {
                                                grid.getStore().data.clear();
                                                this.onRefresh();
                                            }
                                        }, this, {single: true});
                                        this.configureActions();
                                    }
                                },
                                failure : function(response) {
                                    this.showErrorMsg('Error', 'Failed to delete.');
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
                    this.configureActions();
                },
                failure: function(response) {
                    if (response.status == 500)
                        this.showErrorMsg('Error', 'Failed to move file on server. This may be a server configuration problem. Please contact the site administrator.');
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

        var name = this.selectedRecord.data.name;

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
            items: [{
                xtype: 'form',
                labelAlign: 'top',
                border : false, frame : false,
                padding : 10,
                items: [{
                    xtype: 'textfield',
                    id : 'renameText',
                    allowBlank: false,
                    regex: /^[^@\/\\;:?<>*|"^][^\/\\;:?<>*|"^]*$/,
                    regexText: "Folder must be a legal filename and not start with '@' or contain one of '/', '\\', ';', ':', '?', '<', '>', '*', '|', '\"', or '^'",
                    width: 250,
                    labelAlign: 'top',
                    itemId: 'nameField',
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
        if (!this.refreshTask) {
            this.refreshTask = new Ext4.util.DelayedTask(this.reload, this);
        }
        this.refreshTask.delay(100);
        this.selectedRecord = undefined;
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
                success: function(gridUpdated) {
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
        if (!this.isWebDav) {
            if (this.adminWindow && !this.adminWindow.isDestroyed) {
                this.adminWindow.show();
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
        }
    },


    onEditFileProps : function() {
        this.onCustomFileProperties({fileRecords : this.getGridSelection(), showErrors : true});
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
            this.showErrorMsg('Error', 'No files selected.');
            return;
        }
        else
        {
            Ext4.each(options.fileRecords, function(record){
                if (!this.fileProps[record.data.id])
                    this.fileProps[record.data.id] = record.data;
            }, this);
        }

        // no file fields specified yet
        if (this.getExtraColumns().length == 0)
        {
            if (options.showErrors)
                this.showErrorMsg("Error", "There are no file properties defined yet, please contact your administrator.");
            return;
        }

        Ext4.create('Ext.Window', {
            title : 'Extended File Properties',
            id : 'editFilePropsWin',
            cls : 'data-window',
            modal : true,
            width : 400,
            autoShow: true,
            items : Ext4.create('File.panel.EditCustomFileProps', {
                extraColumns : this.getExtraColumns(),
                fileRecords : options.fileRecords,
                winId : 'editFilePropsWin',
                fileProps : this.fileProps
            }),
            listeners : {
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
    }
});
