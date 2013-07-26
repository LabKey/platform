/*
 * Copyright (c) 2012-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.requiresCss("_images/icons.css");
LABKEY.requiresCss("study/DataViewsPanel.css");

Ext4.define('File.panel.Browser', {

    extend : 'Ext.panel.Panel',

    alias: ['widget.filebrowser'],

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
     * @cfg {Boolean} layout
     */
    layout : 'border',

    /**
     * @cfg {Object} gridConfig
     */
    gridConfig : {},

    /**
     * @cfg {Boolean} frame
     */
    frame : false,

    /**
     * @cfg {File.system.Abstract} fileSystem
     */
    fileSystem : undefined,

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
     * @cfg {String} rootOffset
     */
    rootOffset : '',

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
     * @cfg {Array} tbarItems
     */
    tbarItems : [],

    actionsConfig : [],

    actionsUpToDate : false,

    // provides a default color for backgrounds if they are shown
    bodyStyle : 'background-color: lightgray;',

    constructor : function(config) {

        Ext4.QuickTips.init();

        // Clone the config so we don't modify the original config object
        config = Ext4.Object.merge({}, config);
        this.setFolderOffset(config.fileSystem.getOffsetURL());

        this.callParent([config]);
    },

    initComponent : function() {

        var testFlag = document.createElement("div");
        testFlag.id = 'testFlag';

        document.body.appendChild(testFlag);

        this.createActions();
        this.items = this.getItems();
        this.importDataEnabled = true;

        // Configure Toolbar
        if (this.isWebDav) {
            this.tbar = this.getWebDavToolbarItems();
        } else {
            this.getAdminActionsConfig();
        }

        // Attach listeners
        this.on('folderchange', this.onFolderChange, this);

        this.callParent();
    },

    createActions: function() {
        this.actions = {};

        this.actions.parentFolder = new Ext4.Action({
            text: 'Parent Folder',
            itemId: 'parentFolder',
            tooltip: 'Navigate to parent folder',
            iconCls:'iconUp',
            disabledClass:'x-button-disabled',
            handler : this.onTreeUp,
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
            handler : this.onMoveClick,
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
            scope: this
        });

        this.actions.importData = new Ext4.Action({
            text: 'Import Data',
            itemId: 'importData',
            listeners: {click:function(button, event) {this.onImportData(button);}, scope:this},
            iconCls: 'iconDBCommit',
            disabledClass:'x-button-disabled',
            disabled: true,
            tooltip: 'Import data from files into the database, or analyze data files',
            scope: this
        });

        this.actions.customize = new Ext4.Action({
            text: 'Admin',
            itemId: 'customize',
            iconCls: 'iconConfigure',
            disabledClass:'x-button-disabled',
//            disabled : true,
            tooltip: 'Configure the buttons shown on the toolbar',
            handler: this.showAdminWindow,
            scope: this
        });

        this.actions.editFileProps = new Ext4.Action({
            text: 'Edit Properties',
            itemId: 'editFileProps',
            iconCls: 'iconEditFileProps',
            disabledClass:'x-button-disabled',
            tooltip: 'Edit properties on the selected file(s)',
            handler : function() { Ext4.Msg.alert('Edit File Properties', 'This feature is not yet implemented.'); },
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
            handler : this.onEmailPreferences,
            disabled : true,
            scope: this
        });

        this.actions.auditLog = new Ext4.Action({
            text: 'Audit History',
            itemId: 'auditLog',
            iconCls: 'iconAuditLog',
            disabledClass:'x-button-disabled',
            tooltip: 'View the files audit log for this folder.',
            handler : function() {
                window.location = LABKEY.ActionURL.buildURL('filecontent', 'showFilesHistory', this.containerPath);
            },
            scope: this
        });

        this.initializeActions();
    },

    initializeActions: function() {
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

    getAdminActionsConfig : function(){
        Ext4.Ajax.request({
            url: LABKEY.ActionURL.buildURL('pipeline', 'getPipelineActionConfig', this.containerPath),
            method:'GET',
            disableCaching:false,
            success : this.configureFileBrowser,
            failure: LABKEY.Utils.displayAjaxErrorResponse,
            scope: this
//            updatePipelineActions: updatePipelineActions,
//            updateSelection: updateSelection
        });
    },

    configureFileBrowser : function(response) {
        var json = Ext4.JSON.decode(response.responseText);
        if (json.config) {
            this.updateActions();
            this.addTbarActions(json.config);
        }
    },

    addTbarActions : function(config){
        var tbarConfig = config.tbarActions;
        var actionButtons = config.actions;
        var toolbar = this.getDockedItems()[0];
        var buttons = [];

        if(toolbar){
            // Remove the current toolbar incase we just custmized it.
            this.removeDocked(toolbar);
        }

        if(tbarConfig){


            for(var i = 0; i < tbarConfig.length; i++){
                var actionConfig = tbarConfig[i];
                var action = this.actions[actionConfig.id];
                if(actionConfig.hideText){
                    action.setText(undefined);
                } else {
                    action.setText(action.initialConfig.prevText);
                }

                if(actionConfig.hideIcon){
                    action.setIconCls(undefined);
                } else {
                    action.setIconCls(action.initialConfig.prevIconCls);
                }

                if(actionConfig.id == 'customize'){
                    action.setDisabled(this.disableGeneralAdminSettings);
                }

                buttons.push(action);
            }
        } else {
            buttons = this.getDefaultWebPartToolbarItems(this.tbarItems);
        }
        if(actionButtons)
        {
            for(var i = 0; i < actionButtons.length; i++)
            {

                if(actionButtons[i].links[0].display === 'toolbar')
                {
                    var action = Ext4.create('Ext.Action', {
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

        this.addDocked({xtype:'toolbar', dock: 'top', items: buttons});
    },

    executeToolbarAction : function(item, e)
    {
        var action = this.actionMap[item.itemId];

        if (action)
            this.executeImportAction(action);
    },

    initGridColumns : function() {
        var columns = [];

        var nameTpl =
            '<div height="16px" width="100%">' +
                '<div style="float: left;">' +
                    '<div style="float: left;">' +
                        '<img height="16px" width="16px" src="{icon}" alt="{type}" style="vertical-align: bottom; margin-right: 5px;">' +
                    '</div>' +
                '</div>' +
                '<div style="padding-left: 20px; white-space:normal !important;">' +
                    '<span style="display: inline-block;">{name:htmlEncode}</span>' +
                '</div>' +
            '</div>';

        columns.push({
            xtype : 'templatecolumn',
            text  : 'Name',
            dataIndex : 'name',
            sortable : true,
            minWidth : 200,
            flex : 3,
            tpl : nameTpl,
            scope : this
        });

        columns.push(
            {header: "Last Modified",  flex: 1, dataIndex: 'lastmodified', sortable: true,  hidden: false, renderer: this.dateRenderer},
            {header: "Size",           flex: 1, dataIndex: 'size',         sortable: true,  hidden: false, renderer:Ext4.util.Format.fileSize, align : 'right'},
            {header: "Created By",     flex: 1, dataIndex: 'createdby',    sortable: true,  hidden: false, renderer:Ext4.util.Format.htmlEncode},
            {header: "Description",    flex: 1, dataIndex: 'description',  sortable: true,  hidden: false, renderer:Ext4.util.Format.htmlEncode},
            {header: "Usages",         flex: 1, dataIndex: 'actionHref',   sortable: true,  hidden: false},// renderer:LABKEY.FileSystem.Util.renderUsage},
            {header: "Download Link",  flex: 1, dataIndex: 'fileLink',     sortable: true,  hidden: true},
            {header: "File Extension", flex: 1, dataIndex: 'fileExt',      sortable: true,  hidden: true,  renderer:Ext4.util.Format.htmlEncode}
        );

        return columns;
    },

    getItems : function() {
        var items = [this.getGridCfg()];

        if (this.showFolderTree) {
            items.push(this.getFolderTreeCfg());
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
            autoLoad : true,
            proxy : this.fileSystem.getProxyCfg(this.getRootURL())
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

        this.on('gridchange', this.updateGridProxy, this);
        this.on('treechange', this.updateGridProxy, this);

        // 'load' only works on non-buffered stores
        this.fileStore.on(this.bufferFiles ? 'prefetch' : 'load', function(){
            this.gridMask.cancel();
            if (this.grid) {
                this.getGrid().getEl().unmask();
            }
        }, this);

        return this.fileStore;
    },

    updateGridProxy : function(url) {
        if (!this.gridTask) {
            this.gridTask = new Ext4.util.DelayedTask(function() {
                this.fileStore.getProxy().url = this.gridURL;
                this.gridMask.delay(250);
                this.fileStore.load();
            }, this);
        }
        this.gridURL = url;
        this.gridTask.delay(50);
    },

    getRootURL : function() {
        return this.fileSystem.concatPaths(LABKEY.ActionURL.getBaseURL(), this.fileSystem.getBaseURL().replace(LABKEY.contextPath, ''));
    },

    getFolderURL : function() {
        return this.fileSystem.concatPaths(this.getRootURL(), this.getFolderOffset());
    },

    getFolderOffset : function() {
        return this.rootOffset;
    },

    setFolderOffset : function(offsetPath, model) {

        if (model && Ext4.isString(offsetPath)) {
            var splitUrl = offsetPath.split(this.getRootURL());
            if (splitUrl && splitUrl.length > 1) {
                offsetPath = splitUrl[1];
            }
        }

        this.rootOffset = offsetPath;
        this.currentFolder = model;
        this.fireEvent('folderchange', offsetPath, model);
    },

    getFolderTreeCfg : function() {

        var store = Ext4.create('Ext.data.TreeStore', {
            model : this.fileSystem.getModel('xml'),
            proxy : this.fileSystem.getProxyCfg(this.getRootURL(), 'xml'),
            root : {
                text : this.fileSystem.rootName,
                id : '/',
                expanded : true,
                icon : LABKEY.contextPath + '/_images/labkey.png'
            }
        });

        // Request Root Node Information
        Ext4.Ajax.request({
            url    : this.getRootURL(),
            headers: store.getProxy().headers,
            method : 'GET',
            params : store.getProxy().getPropParams({action: 'read'}) + '&depth=0',
            success: function(response) {
                if (response && response.responseXML) {
                    var records = store.getProxy().getReader().readRecords(response.responseXML).records;
                    if (Ext4.isArray(records)) {
                        var data = records[0].data;
                        Ext4.apply(store.tree.root.data, {
                            options : data.options,
                            uri     : data.uri
                        });
                        this.setFolderOffset(store.tree.root.data.id, store.tree.root);
                        return;
                    }
                }
                console.warn('Failed to initialize root. See Browser.getFolderTreeCfg');
            },
            scope : this
        });

        if (!this.showHidden) {
            store.on('beforeappend', function(s, node) {
                if (node && node.data && Ext4.isString(node.data.name)) {
                    if (node.data.name.indexOf('.') == 0) {
                        return false;
                    }
                }
            }, this);
        }

        store.on('load', function() {
            var p = this.getFolderOffset();
            if (p && p[p.length-1] != '/')
                p += '/';
            this.ensureVisible(this.fileSystem.getOffsetURL());
        }, this, {single: true});

        this.on('gridchange', this.expandPath, this);

        return {
            xtype : 'treepanel',
            itemId : 'treenav',
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
                beforerender : function(t) {
                    this.tree = t;
                },
                select : this.onTreeSelect,
                scope : this
            }
        };
    },

    ensureVisible : function(id) {

        if (!this.vStack) {
            this.vStack = [];
        }
        var node = this.tree.getView().getTreeStore().getRootNode().findChild('id', id, true);
        if (!node) {
            var p = this.fileSystem.getParentPath(id);
            if (p == '/') {
                return;
            }
            if (id.length > 0 && id.substring(id.length-1) != '/') {
                id += '/';
            }
            this.vStack.push(id);
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

    expandPath : function(p) {
        var path = this.getFolderOffset();
        var idx = this.tree.getView().getStore().find('id', path);
        if (idx) {
            var rec = this.tree.getView().getStore().getAt(idx);
            if (rec) {
                this.tree.getView().expand(rec);
                this.tree.getSelectionModel().select(rec);
                return;
            }
        }
        console.warn('Unable to expand path: ' + path);
    },

    onTreeSelect : function(selModel, rec) {
        if (rec.isRoot())  {
            this.setFolderOffset(rec.data.id, rec);
            this.fireEvent('treechange', this.getFolderURL());
        }
        else if (rec.data.uri && rec.data.uri.length > 0) {
            this.setFolderOffset(rec.data.uri, rec);
            this.fireEvent('treechange', this.getFolderURL());
        }
        this.tree.getView().expand(rec);
    },

    getGrid : function() {

        if (this.grid) {
            return this.grid;
        }

        return this.getGridCfg();
    },

    onImportData : function()
    {
        this.updateActions();
        this.processImportData();
    },

    updateActions : function()
    {
        var actionsURL = LABKEY.ActionURL.buildURL('pipeline', 'actions', this.containerPath, {allActions:true});
        if (this.isPipelineRoot)
        {
            Ext4.Ajax.request({
                autoAbort:true,
                url: actionsURL,
                method:'GET',
                disableCaching:false,
                success : this.updateWithConfigs,
                failure: this.isPipelineRoot ? LABKEY.Utils.displayAjaxErrorResponse : undefined,
                scope: this
            });
        }

    },

    updateWithConfigs : function(response){
        var actionsConfigURL = LABKEY.ActionURL.buildURL('pipeline', 'getPipelineActionConfig', this.containerPath);

        var o = Ext4.decode(response.responseText);
        var actions = o.success ? o.actions : [];

        Ext4.Ajax.request({
            autoAbort:true,
            url: actionsConfigURL,
            method:'GET',
            disableCaching:false,
            success : function(response){
                var actionConfigs = Ext4.decode(response.responseText);
                actionConfigs = actionConfigs.config.actions;
                for(var i = 0; i < actionConfigs.length; i++)
                {
                    this.actionsConfig[actionConfigs[i].id] = actionConfigs[i];
                }
                this.updatePipelineActions(actions);
            },
            failure: LABKEY.Utils.displayAjaxErrorResponse,
            scope: this
        });

    },

    updatePipelineActions : function(actions)
    {
        this.pipelineActions = [];
        this.actionMap = {};
        this.actionGroups = {};

        if (actions && actions.length && this.importDataEnabled)
        {
            var pipelineActions = this.parseActions(actions);
            for (var i=0; i < pipelineActions.length; i++)
            {
                var pa = pipelineActions[i];
                var isEnabled = false;

                if (!pa.link) continue;

                var config = this.actionsConfig[pa.groupId];
                if (pa.link.href)
                {
                    if(config)
                    {
                        pa.enabled = (config.links[0].display === 'enabled');
                    }
                    else
                    {
                        pa.enabled = true;
                    }
                }

//                if (this.adminUser || isEnabled)
//                {
                    this.pipelineActions.push(pa);
                    this.actionMap[pa.id] = pa;

                    if (pa.groupId in this.actionGroups)
                        this.actionGroups[pa.groupId].actions.push(pa);
                    else
                        this.actionGroups[pa.groupId] = {label: pa.groupLabel, actions: [pa]};
//                }
            }
        }

//        if (this.pipelineActions && this.pipelineActions.length)
//            this.enableImportData(true);

        this.updateActionButtons();

        this.actionsUpToDate = true;
    },

    //TODO: Button logic should work for multiple file selection (MFS)
    updateActionButtons : function()
    {
        for(var key in this.actionMap)
        {
            if(!this.selectedRecord)
                break;

            if(Ext4.getCmp(key))
            {
                var disabled = true;
                for(var i = 0; i < this.actionMap[key].files.length; i++)
                {

                    if(this.selectedRecord.data.name === this.actionMap[key].files[i])
                    {
                        disabled = false;
                        break;
                    }
                }
                Ext4.getCmp(key).setDisabled(disabled);
            }
        }
    },

    isValidFileCheck : function(files, filename)
    {
        for(var i = 0; i < files.length; i++)
        {
            if(files[i] === filename)
            {
                return true;
            }
        }
        return false;
    },

    //TODO THIS IS A MARKER FOR YOU TO KNOW WHERE THIS THING IS
    processImportData : function(btn)
    {
        var actionMap = [],
                actions = [],
                items   = [],
                alreadyChecked = false, // Whether we've already found an enabled action to make the default selection
                hasAdmin = false, pa, links;

        //TODO make this work for multiple file selection (MFS)
        for (var ag in this.actionGroups)
        {
            var group = this.actionGroups[ag];
            pa = group.actions[0];
            this.adminUser = true;

            var bad = 0;
            for(var i=0; i < group.actions.length; i++){
                if(group.actions[i].files.length <= 0)
                {
                    bad++;
                }
                else if(!this.isValidFileCheck(group.actions[i].files, this.selectedRecord.data.name))
                {
                    bad++;
                }
            }
            if(bad == group.actions.length)
                continue;

            var radioGroup = Ext4.create('Ext.form.RadioGroup', {
                fieldLabel : group.label + '<br>' + 'using ' + '1' + ' of ' + pa.files.length + ' files',
                itemCls    : 'x-check-group',
                columns    : 1,
                labelSeparator: '',
                items      : [],
                scope : this
            });



            for (var i=0; i < group.actions.length; i++)
            {
                var action = group.actions[i];
                if (action.link.href && (action.link.enabled || this.adminUser))
                {
                    var label = action.link.text;

                    // administrators always see all actions
                    if (!action.enabled && this.adminUser)
                    {
                        label = label.concat(' <span class="labkey-error">*</span>');
                        hasAdmin = true;
                    }

                    actionMap[action.id] = action;
                    radioGroup.add({
                        xtype: 'radio',
                        checked: action.enabled && !alreadyChecked,
                        disabled: !action.enabled,
                        labelSeparator: '',
                        boxLabel: label,
                        name: 'importAction',
                        inputValue: action.id
                        //width: 250
                    });
                    if (action.enabled)
                    {
                        alreadyChecked = true;
                    }
                }
            }
            if (!pa.enabled)
            {
                radioGroup.disabled = true;

//                radioGroup.on('render', function(c){this.setFormFieldTooltip(c, 'warning-icon-alt.png');}, this);
            }
            else
            {
//                radioGroup.on('render', function(c){this.setFormFieldTooltip(c, 'info.png');}, this);
            }
            actions.push(radioGroup);
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

        if (!this.importDataEnabled)
        {
            items.push({
                html      : 'This dialog has been disabled from the admin panel and is only visible to Administrators.',
                bodyStyle : 'padding:10px;',
                border    : false
            });
        }

        var win = Ext4.create('Ext.Window', {
            title: 'Import Data',
            width: 725,
            height: 400,
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

    submitForm : function(panel, actionMap)
    {
        // client side validation
        var selection = panel.getForm().getValues();
        var action = actionMap[selection.importAction];

        if ('object' == typeof action)
            this.executeImportAction(action);
    },

    executeImportAction : function(action)
    {
        if (action)
        {
            //TODO convert for multiple file selection (MFS)
            var selections = [this.selectedRecord], i;
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

    onItemClick : function(g, rec) {
        this.changeTestFlag(false, false);
        if (this.showDetails) {
            this.getDetailPanel().update(rec.data);
        }

        var tb = this.getDockedComponent(0);
        if (tb) {
            if (this.actions.download) {
                this.actions.download.setDisabled(!this.fileSystem.canRead(rec));
            }
            if (this.actions.renamePath) {
                this.actions.renamePath.setDisabled(false);
            }
            if (this.actions.movePath) {
                this.actions.movePath.setDisabled(false);
            }
            if (this.actions.deletePath) {
                this.actions.deletePath.setDisabled(false);
            }
            if (this.actions.importData) {
                this.actions.importData.setDisabled(false);
            }
        }

        this.selectedRecord = rec;
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
            config.selModel = Ext4.create('Ext.selection.CheckboxModel', {
                mode : 'SINGLE'
            });
        }

        Ext4.apply(config, {
            xtype   : 'grid',
            store   : this.getFileStore(),
            columns : {
                items : this.initGridColumns()
            },
            listeners : {
                beforerender : function(g) { this.grid = g; },
                itemclick    : this.onItemClick,
                itemdblclick : function(g, rec) {
                    if (rec.data.collection) {
                        this.setFolderOffset(rec.data.id, rec);
                        this.fireEvent('gridchange', this.getFolderURL());
                    }
                    else {
                        // Download the file
                        this.onDownload({recs : [rec]});
                    }
                },
                scope : this
            }
        });

        this.gridMask = new Ext4.util.DelayedTask(function(){
            this.getGrid().getEl().mask('Loading...');
        }, this);

        return config;
    },

    getDefaultWebPartToolbarItems : function(tbarItems){
        var tbarButtons = [];

        for(var i = 0; i < tbarItems.length; i++){
            tbarButtons.push(this.actions[tbarItems[i]]);
        }

        return tbarButtons;
    },

    getWebDavToolbarItems : function(){
        var baseItems = [];

        this.actions.folderTreeToggle.setText('');
        this.actions.parentFolder.setText('');
        this.actions.refresh.setText('');
        this.actions.createDirectory.setText('');
        this.actions.download.setText('');
        this.actions.deletePath.setText('');

        this.actions.createDirectory.setDisabled(true);
        this.actions.download.setDisabled(true);
        this.actions.deletePath.setDisabled(true);
        this.actions.upload.setDisabled(true);

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
        var tb = this.getDockedComponent(0);
        if (tb) {
            tb.getComponent('deletePath').setDisabled(!this.fileSystem.canDelete(model)); // TODO: Check grid selection
            this.actions.download.setDisabled(!this.fileSystem.canRead(model));
            tb.getComponent('createDirectory').setDisabled(!this.fileSystem.canMkdir(model));
            tb.getComponent('upload').setDisabled(!this.fileSystem.canWrite(model));
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
            flex : 1,
            maxHeight : 100,
            tpl : detailsTpl
        });

        this.on('folderchange', function(){ this.details.update(''); }, this);

        return this.details;
    },

    onCreateDirectory : function() {

        var onCreateDir = function() {

            var path = this.getFolderURL();
            if (panel.getForm().isValid()) {
                var values = panel.getForm().getValues();
                if (values && values.folderName) {
                    var folder = values.folderName;
                    this.fileSystem.createDirectory({
                        path : path + folder,
                        success : function(path) {
                            win.close();

                            // Reload stores
                            this.getFileStore().load();
                            var nodes = this.tree.getSelectionModel().getSelection();
                            if (nodes && nodes.length)
                                this.tree.getStore().load({node: nodes[0]});
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

        var panel = Ext4.create('Ext.form.Panel', {
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
                validator : function(folder) {
                    if (folder && folder.length) {
                        console.log('would validate if folder is already present.');
                    }
                    return true;
                },
                listeners : {
                    afterrender : function(f) {
                        var km = new Ext4.util.KeyMap(f.el, [
                            {
                                key   : Ext4.EventObject.ENTER,
                                fn    : onCreateDir,
                                scope : this
                            },{
                                key   : Ext4.EventObject.ESC,
                                fn    : function() { win.close(); },
                                scope : this
                            }
                        ]);
                    },
                    scope : this
                },
                scope : this
            }]
        });

        var win = Ext4.create('Ext.Window', {
            title : 'Create Folder',
            width : 300,
            height: 150,
            modal : true,
            autoShow : true,
            items : [panel],
            cls : 'data-window',
            defaultFocus : 'foldernamefield',
            buttons : [
                {text : 'Submit', handler : onCreateDir, scope: this},
                {text : 'Cancel', handler : function() { win.close(); }, scope: this}
            ]
        });
    },

    onDelete : function() {

        var recs = this.getGrid().getSelectionModel().getSelection();

        if (recs && recs.length > 0) {

            Ext4.Msg.show({
                title : 'Delete Files',
                cls : 'data-window',
                msg : 'Are you sure that you want to delete the ' + (recs[0].data.collection ? 'folder' : 'file') +' \'' + recs[0].data.name + '\'?',
                buttons : Ext4.Msg.YESNO,
                icon : Ext4.Msg.QUESTION,
                fn : function(btn) {
                    if (btn == 'yes') {
                        this.fileSystem.deletePath({
                            path : recs[0].data.href,
                            success : function(path) {
                                this.getFileStore().load();
                            },
                            failure : function(response) {
                                 Ext4.Msg.alert('Delete', 'Failed to delete.');
                            },
                            scope : this
                        });
                    }
                },
                scope : this
            });

        }
    },

    // TODO: Support multiple selection download -- migrate to file system
    onDownload : function(config) {

        var recs = (config && config.recs) ? config.recs : this.getGrid().getSelectionModel().getSelection();

        if (recs.length == 1) {

            var url;
            if (!recs[0].data.collection) {
                url = recs[0].data.href + "?contentDisposition=attachment";
            }
            else {
                url = recs[0].data.href + "?method=zip&depth=-1";
                url += "&file=" + encodeURIComponent(recs[0].data.name);
            }

            window.location = url;
        }
        else {
            Ext4.Msg.show({
                title : 'File Download',
                msg : 'Please select a file or folder on the right to download.',
                buttons : Ext4.Msg.OK
            });
        }
    },

    onEmailPreferences : function(btn) {
        Ext4.Msg.alert('Email Preferences', 'This feature is not yet implemented.');
//        var prefDlg = new LABKEY.EmailPreferencesPanel({containerPath: this.containerPath});
//
//        prefDlg.show();
    },

    _moveOnCallback : function(fs, src, dest, rec) {
        this.onRefresh();
        var tb = this.getDockedItems()[0];
        if (tb) {
            tb.getComponent('renamePath').setDisabled(true);
            tb.getComponent('movePath').setDisabled(true);
        }
    },

    onMoveClick : function() {
        if (!this.currentDirectory || !this.selectedRecord)
            return;

        //TODO Make this not a workaround for a single file (MFS)
        var selections = [this.selectedRecord];

//        function validateNode(record, node){
//            if(record.data.options.indexOf('MOVE') == -1){
//                node.disabled = true;
//            }
//
//            var path;
//            Ext4.each(selections, function(rec){
//                path = rec.get('path');
//                if(this.fileSystem.isChild(node.id, path) || node.id == path || node.id == this.fileSystem.getParentPath(rec.get('path'))){
//                    node.disabled = true;
//                    return false;
//                }
//            }, this);
//        }


        var treePanel = Ext4.create('Ext.tree.Panel', {
            itemId          : 'treepanel',
            id              : 'treePanel',
            height          : 200,
            root            : this.tree.getRootNode(),
            rootVisible     : true,
            autoScroll      : true,
            animate         : true,
            enableDD        : false,
            containerScroll : true,
            collapsible     : false,
            collapseMode    : 'mini',
            collapsed       : false,
            cmargins        :'0 0 0 0',
            border          : true,
            stateful        : false,
            pathSeparator   : ';'
        });
        treePanel.getRootNode().expand();

        var okHandler = function(win) {
            var panel = Ext4.getCmp('treePanel');
            var node = panel.getSelectionModel().getLastSelected();
            if (!node) {
                Ext4.Msg.alert('Move Error', 'Must pick a destination folder');
                return;
            }

            if (node.data.id == this.currentDirectory.data.id) {
                Ext4.Msg.alert('Move Error', 'Cannot move a file to the folder it is already in');
                return;
            }

            var destination = node.data.id;
            if (destination != '/') {
                destination = destination.split('@files')[1]; // Worrisome
            }

            var toMove = [];
            for (var i=0; i < win.fileRecords.length; i++) {
                toMove.push({record : win.fileRecords[i]});
            }

            this.doMove(toMove, destination, function() { win.close(); }, this);
        };

        var win = Ext4.create('Ext.window.Window', {
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
                },
                    treePanel
                ]
            }],
            buttons: [{
                text: 'Move',
                scope: this,
                handler: function(btn){
                    var win = btn.findParentByType('window');
                    okHandler.call(this, win);
                }
            },{
                text: 'Cancel',
                handler: function(btn){
                    btn.findParentByType('window').hide();
                }
            }]
        });
    },

    //TODO Various validation tasks should be preformed on this.
    doMove : function(toMove, destination, callback, scope) {

        for (var i = 0; i < toMove.length; i++){
            var selected = toMove[i];

            var newPath = this.fileSystem.concatPaths(destination, (selected.newName || selected.record.data.name));

            //used to close the ext window
            if(callback)
                callback.call(this);

            this.fileSystem.movePath({
                fileRecord : selected,
                source: selected.record.data.id.split('@files')[1],
                destination: newPath,
                isFile: !this.selectedRecord.data.collection,
                success: function(fs, source, destination, response){
                    this._moveOnCallback(fs, source, destination, selected.record);
                },
                failure: LABKEY.Utils.displayAjaxErrorResponse,
                scope: this
            });
        }
    },

    onRename : function() {

        if (!this.selectedRecord || !this.currentDirectory) {
            return;
        }

        var me = this;
        var okHandler = function() {
            var field = Ext4.getCmp('renameText');
            var newName = field.getValue();

            if (!newName || !field.isValid()) {
                alert('Must enter a valid filename');
            }

            if (newName == win.origName) {
                win.close();
                return;
            }

            var destination = me.currentDirectory.data.id;
            if (destination != '/') {
                destination = destination.split('@files')[1];
            }

            me.doMove([{
                record: win.fileRecord,
                newName: newName
            }], destination, function(){
                win.close();
            }, me);
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
            this.refreshTask = new Ext4.util.DelayedTask(function() {
                this.getFileStore().load();
            }, this);
        }
        this.refreshTask.delay(250);
    },

    onTreeUp : function() {
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
                success: this.getAdminActionsConfig,
                close: function() { this.adminWindow.close(); },
                scope: this
            }
        };
    },

    showAdminWindow: function() {
        if (this.adminWindow && !this.adminWindow.isDestroyed) {
            this.adminWindow.setVisible(true);
        }
        else {
            Ext4.Ajax.request({
                scope: this,
                url: LABKEY.ActionURL.buildURL('pipeline', 'getPipelineActionConfig', this.containerPath),
                success: function(response){
                    var json = Ext4.JSON.decode(response.responseText);
                    this.adminWindow = Ext4.create('Ext.window.Window', {
                        cls: 'data-window',
                        title: 'Manage File Browser Configuration',
                        closeAction: 'destroy',
                        layout: 'fit',
                        modal: true,
                        items: [this.getAdminPanelCfg(json)]
                    }).show();
                },
                failure: function(){}
            });
        }
    },

    changeTestFlag : function(folderMove, importReady)
    {
        var flag = document.getElementById('testFlag');
        var appliedClass = "";
        if(folderMove)
        {
            appliedClass += 'test-grid-ready';
            if(importReady)
            {
                appliedClass += ' test-import-ready';
            }
        }
        else if(importReady)
        {
            appliedClass = 'test-import-ready';
        }

        flag.setAttribute('class', appliedClass);
    }
});
