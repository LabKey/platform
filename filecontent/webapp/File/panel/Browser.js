Ext4.define('File.panel.Browser', {

    extend : 'Ext.panel.Panel',

    /**
     * @cfg {Boolean} border
     */
    adminUser : false,

    /**
     * @cfg {Boolean} border
     */
    border : false,

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
     * @cfg {Number} height
     */
    height : 400,

    /**
     * @cfg {Number} minWidth
     */
    minWidth : 625,

    /**
     * @cfg {String} rootOffset
     */
    rootOffset : '',

    /**
     * @cfg {Boolean} showFolders
     */
    showFolderTree : true,

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
    showUpload : false,

    /**
     * @cfg {Boolean} bufferFiles
     */
    bufferFiles : true,

    constructor : function(config) {
        // Clone the config so we don't modify the original config object
        config = Ext4.Object.merge({}, config);

        // TODO: Need to integrate rootOffset
        if (config.rootURL) {
            this.setFolderURL(config.rootURL);
        }
        else {
            this.setFolderURL(LABKEY.ActionURL.getBaseURL() + '_webdav' + LABKEY.ActionURL.getContainer() + '/');
        }

        this.callParent([config]);
    },

    initComponent : function() {

        this.defineModels();

        this.items = this.getItems();

        // Configure Toolbar
        this.tbar = this.getToolbarItems();

        this.callParent();

        b = this;
    },

    defineModels : function() {

        Ext4.define('FileModel', {
            extend : 'Ext.data.Model',
            fields : [
                {name : 'creationdate', type : 'date'},
                {name : 'contentlength', type : 'int'},
                {name : 'contenttype'},
                {name : 'etag'},
                {name : 'href'},
                {name : 'id'},
                {name : 'lastmodified', type : 'date'},
                {name : 'leaf', type : 'boolean'},
                {name : 'size', type : 'int'},
                {name : 'name', mapping : 'text'},
                {name : 'icon', mapping : 'iconHref'}
            ]
        });

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

//        if (this.adminUser) {
//            columns.push({
//                text : '&nbsp;',
//                width: 40,
//                sortable : false,
//                menuDisabled : true,
//                renderer : function(view, meta, rec) {
//                    return '<span height="16px" class="edit-views-link"></span>';
//                },
//                scope : this
//            });
//        }

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
//            {header: "",               width: 20,  dataIndex: 'iconHref',     sortable: false, hidden: false},// renderer:iconRenderer},
//            {header: "Name",           width: 250, dataIndex: 'name',         sortable: true,  hidden: false, renderer:Ext4.util.Format.htmlEncode},
            {header: "Last Modified",  flex: 1, dataIndex: 'lastmodified', sortable: true,  hidden: false},// renderer:LABKEY.FileSystem.Util.renderDateTime},
            {header: "Size",           flex: 1, dataIndex: 'size',         sortable: true,  hidden: false},// align:'right', renderer:LABKEY.FileSystem.Util.renderFileSize},
            {header: "Created By",     flex: 1, dataIndex: 'createdBy',    sortable: true,  hidden: false, renderer:Ext4.util.Format.htmlEncode},
            {header: "Description",    flex: 1, dataIndex: 'description',  sortable: true,  hidden: false, renderer:Ext4.util.Format.htmlEncode},
            {header: "Usages",         flex: 1, dataIndex: 'actionHref',   sortable: true,  hidden: false},// renderer:LABKEY.FileSystem.Util.renderUsage},
            {header: "Download Link",  flex: 1, dataIndex: 'fileLink',     sortable: true,  hidden: true},
            {header: "File Extension", flex: 1, dataIndex: 'fileExt',      sortable: true,  hidden: true,  renderer:Ext4.util.Format.htmlEncode}
        );

        return columns;
    },

    getItems : function() {
        var items = [this.getGrid()];

        if (this.showFolderTree) {
            items.push(this.getFolderTree());
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
            model : 'FileModel',
            autoLoad : true,
            proxy : this.getFileProxyCfg()
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

        this.fileStore = Ext4.create('Ext.data.Store', storeConfig);

        return this.fileStore;
    },

    /**
     * This proxy will be used to populate the file store.
     */
    getFileProxyCfg : function() {
        return {
            type : 'ajax',
            url  : this.getFolderURL(),
            extraParams : {
                method : 'JSON'
            },
            reader : {
                root : 'files',
                totalProperty : 'fileCount'
            }
        };
    },

    getFolderURL : function() {
        return this.rootURL;
    },

    setFolderURL : function(url) {
        this.rootURL = url;
    },

    getFolderTree : function() {
        if (this.tree) {
            return this.tree;
        }

        var store = Ext4.create('Ext.data.TreeStore', {
            model : 'File.data.webdav.Response',
            proxy : {
                type : 'webdav',
                url  : this.getFolderURL(),
                reader : {
                    type : 'xml',
                    root : 'multistatus',
                    record : 'response'
//                    readRecords : function(d) {
//                        console.log('BOOM');
//                        return this.callParent([d]);
//                    }
                }
            },
            root : {
                text : 'localhost'
            }
        });

        this.tree = Ext4.create('Ext.tree.Panel', {
            title : 'Folders',
            region : 'west',
            flex : 1,
            store : store,
            collapsed: true,
            listeners : {
                select : this.onTreeSelect,
                scope : this
            }
        });

        return this.tree;
    },

    onTreeSelect : function(tree, rec, idx) {
        // TODO: When user clicks on root, navigate back to normal root
        if (rec.data.uri && rec.data.uri.length > 0) {
            this.setFolderURL(rec.data.uri);
            this.getFileStore().getProxy().url = this.getFolderURL();
            this.getFileStore().load();
        }
    },

    getGrid : function() {

        if (this.grid) {
            return this.grid;
        }

        this.grid = Ext4.create('Ext.grid.Panel', this.getGridCfg());

        return this.grid;
    },

    getGridCfg : function() {
        var config = Ext4.Object.merge({}, this.gridConfig);

        // Optional Configurations
        Ext4.applyIf(config, {
//            border : false,
            flex : 3,
            region : 'center',
            selType : 'checkboxmodel',
            viewConfig : {
                emptyText : '<span style="margin-left: 5px; opacity: 0.3;"><i>No Files Found</i></span>'
            }
        });

        Ext4.apply(config, {
            store   : this.getFileStore(),
            columns : {
                items : this.initGridColumns()
            }
        });

        return config;
    },

    getToolbarItems : function() {
        return [
            {iconCls : 'iconFolderTree', handler : function() { this.getFolderTree().toggleCollapse(); }, scope: this},
            {iconCls : 'iconUp'},
            {iconCls : 'iconReload'},
            {iconCls : 'iconFolderNew'},
            {iconCls : 'iconDownload'},
            {iconCls : 'iconDelete'},
            {iconCls : 'iconUpload', text : 'Upload Files'}
        ];
    },

    getUploadPanel : function() {
        if (this.uploadPanel) {
            return this.uploadPanel;
        }

        this.uploadPanel = Ext4.create('File.panel.Upload', {
            region : 'north',
            header : false,
            border : true,
            flex : 1
        });

        return this.uploadPanel;
    }
});
