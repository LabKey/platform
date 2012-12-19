LABKEY.requiresCss("_images/icons.css");
LABKEY.requiresCss("study/DataViewsPanel.css");

Ext4.define('File.panel.Browser', {

    extend : 'Ext.panel.Panel',

    alias: ['widget.filebrowser'],

    /**
     * @cfg {Boolean} adminUser
     */
    adminUser : false,

    /**
     * @cfg {Boolean} border
     */
    border : false,

    /**
     * @cfg {Boolean} bufferFiles
     */
    bufferFiles : true,

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

    // provides a default color for backgrounds if they are shown
    bodyStyle : 'background-color: lightgray;',

    constructor : function(config) {

        Ext4.QuickTips.init();

        // Clone the config so we don't modify the original config object
        config = Ext4.Object.merge({}, config);
        this.setFolderURL(config.fileSystem.getBaseURL());

        this.callParent([config]);
    },

    initComponent : function() {

        this.items = this.getItems();

        // Configure Toolbar
        this.tbar = this.getToolbarItems();

        if (this.tbar) {
            // Attach listeners
            this.on('folderchange', this.onFolderChange, this);
        }

        this.callParent();
    },

    initGridColumns : function() {
        var columns = [];

        var nameTpl =
                '<div height="16px" width="100%">' +
                    '<div style="float: left;">' +
                        '<img height="16px" width="16px" src="{icon}" alt="{type}" style="vertical-align: bottom; margin-right: 5px;">' +
                    '</div>' +
                    '<div style="padding-left: 20px; white-space:normal !important;">' +
                        '<span>{name:htmlEncode}</span>' +
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
            {header: "Last Modified",  flex: 1, dataIndex: 'lastmodified', sortable: true,  hidden: false, renderer: Ext4.util.Format.dateRenderer("Y-m-d H:i:s")},
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

        return items;
    },

    getFileStore : function() {

        if (this.fileStore) {
            return this.fileStore;
        }

        var storeConfig = {
            model : this.fileSystem.getModel(),
            autoLoad : true,
            proxy : this.fileSystem.getProxyCfg(this.getFolderURL())
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
            this.getGrid().getEl().unmask();
        }, this);

        return this.fileStore;
    },

    updateGridProxy : function(url) {
        this.fileStore.getProxy().url = url;
        this.gridMask.delay(250);
        this.fileStore.load();
    },

    getFolderURL : function() {
        return this.rootURL;
    },

    setFolderURL : function(url, model) {
        this.rootURL = url;
        this.rootModel = model;
        this.fireEvent('folderchange', this.rootURL, this.rootModel);
    },

    getFolderTreeCfg : function() {

        var store = Ext4.create('Ext.data.TreeStore', {
            model : this.fileSystem.getModel('xml'),
            proxy : this.fileSystem.getProxyCfg(this.getFolderURL(), 'xml'),
            root : {
                text : this.fileSystem.rootName,
                expanded : true,
                icon : LABKEY.contextPath + '/_images/labkey.png'
            }
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
            if (this.targetPath) {
                this.expandPath(this.targetPath);
            }
        }, this, {single: true});

        this.on('gridchange', this.expandPath, this);

        return {
            xtype : 'treepanel',
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
//                    if (this.rootOffset) {
//                        var path = this.fileSystem.concatPaths(this.getFolderURL(), this.rootOffset);
//                        this.targetPath = 'http://localhost:8080' + path;
//                    }
                },
                afterrender : function(t) {
                },
                select : this.onTreeSelect,
                scope : this
            }
        };
    },

    expandPath : function(url) {
        var idx = this.tree.getView().getStore().find('uri', url);
        if (idx) {
            var rec = this.tree.getView().getStore().getAt(idx);
            if (rec) {
                this.tree.getView().expand(rec);
                this.tree.getSelectionModel().select(rec);
                return;
            }
        }
        console.warn('Unable to expand path: ' + url);
    },

    onTreeSelect : function(selModel, rec, idx) {
        // TODO: When user clicks on root, navigate back to normal root
        if (rec.isRoot())  {
            this.setFolderURL(rec.data.uri + LABKEY.contextPath + '/_webdav');
            this.fireEvent('treechange', this.getFolderURL());
        }
        else if (rec.data.uri && rec.data.uri.length > 0) {
            this.setFolderURL(rec.data.uri, rec);
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

    getGridCfg : function() {
        var config = Ext4.Object.merge({}, this.gridConfig);

        // Optional Configurations
        Ext4.applyIf(config, {
            flex : 4,
            region : 'center',
            selType : 'checkboxmodel',
            viewConfig : {
                emptyText : '<span style="margin-left: 5px; opacity: 0.3;"><i>No Files Found</i></span>'
            }
        });

        Ext4.apply(config, {
            xtype   : 'grid',
            store   : this.getFileStore(),
            columns : {
                items : this.initGridColumns()
            },
            listeners : {
                beforerender : function(g) {
                    this.grid = g;
                },
                itemdblclick : function(g, rec) {
                    if (rec.data.collection) {
                        this.setFolderURL(rec.data.href, rec);
                        this.fireEvent('gridchange', this.getFolderURL());
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

    getToolbarItems : function() {
        var baseItems = [];
        if (this.showFolderTree) {
            baseItems.push({
                iconCls : 'iconFolderTree',
                handler : function() { this.tree.toggleCollapse(); },
                scope: this
            });
        }
        baseItems.push(
//            {iconCls : 'iconUp', handler : this.onTreeUp, tooltip : 'Navigate to the parent folder', scope: this},
            {
                iconCls : 'iconReload',
                handler : this.onRefresh,
                tooltip : 'Refresh the contents of the current folder',
                scope: this
            },{
                iconCls : 'iconFolderNew',
                itemId : 'mkdir',
                handler : this.onCreateDirectory,
                tooltip : 'Create a new folder on the server',
                disabled : true,
                scope : this
            },{
                iconCls : 'iconDownload',
                itemId : 'download',
                handler: this.onDownload,
                tooltip : 'Download the selected files or folders',
                disabled : true,
                scope: this
            },{
                iconCls : 'iconDelete',
                itemId : 'delete',
                handler: this.onDelete,
                disabled : true,
                tooltip : 'Delete the selected files or folders',
                scope: this
            },{
                iconCls : 'iconUpload',
                text    : 'Upload Files',
                itemId : 'upload',
                handler : this.onUpload,
                tooltip : 'Upload files or folders from your local machine to the server',
                disabled : true,
                scope   : this
            }
        );

        if (Ext4.isArray(this.tbarItems)) {
            for (var i=0; i < this.tbarItems.length; i++) {
                baseItems.push(this.tbarItems[i]);
            }
        }

        return baseItems;
    },

    onFolderChange : function(path, model) {
        var d = model.data;
        var tb = this.getDockedComponent(0);
        if (tb) {
            tb.getComponent('delete').setDisabled(!this.fileSystem.canDelete(model));
            tb.getComponent('download').setDisabled(!this.fileSystem.canRead(model));
            tb.getComponent('mkdir').setDisabled(!this.fileSystem.canMkdir(model));
            tb.getComponent('upload').setDisabled(!this.fileSystem.canWrite(model));
        }
    },

    getUploadPanel : function() {
        if (this.uploadPanel) {
            return this.uploadPanel;
        }

        this.uploadPanel = Ext4.create('File.panel.Upload', {
            region : 'north',
            header : false,
            border : true,
            collapseMode : 'mini',
            collapsed : !this.expandUpload,
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
                            this.getFileStore().load();
                        },
                        failure : function(response) {
                            win.close();
                            Ext4.Msg.alert('Create Directory', 'Failed to create directory. This directory might already exist.');
                            console.log(response);
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
    onDownload : function() {

        var recs = this.getGrid().getSelectionModel().getSelection();

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

    onRefresh : function() {
        this.gridMask.delay(0);
        this.getFileStore().load();
    },

    onUpload : function() {
        this.getUploadPanel().toggleCollapse();
    }
});
