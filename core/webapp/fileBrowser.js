var h = Ext.util.Format.htmlEncode;


function renderIcon(value, metadata, record, rowIndex, colIndex, store)
{
    return value ? "<img src='" + value + "'>" : "";
}

function renderFileSize(value, metadata, record, rowIndex, colIndex, store)
{
    if (!record.get('file')) return "";
    var f =  Ext.util.Format.fileSize(value);
    return "<span title='" + f + "'>" + value + "</span>";
}

var _rDateTime = Ext.util.Format.dateRenderer("Y-m-d H:i:s");
function renderDateTime(value, metadata, record, rowIndex, colIndex, store)
{
    if (!value) return "";
    if (value.getTime() == 0) return "";
    return _rDateTime(value, metadata, record, rowIndex, colIndex, store);
}



function treePathFromId(id)
{
    var dir = id.charAt(id.length-1) == '/';
    if (dir) id = id.substring(0,id.length-1);
    var parts = id.split("/");
    var folder = "";;
    var treePath = "";
    for (var i=0 ; i<parts.length ; i++)
    {
        folder += parts[i] + ((i<parts.length-1 || dir) ? "/" : "");
        treePath += ";" + folder;
    }
    return treePath;
}


//
// FileSystem
//

// FileRecord should look like
//      path (string), name (string), file (bool), creationdate (date), lastmodified (date), size (int), iconHref(string)

var FILESYSTEM_EVENTS = {listfiles:"listfiles", history:"history"};

var FileSystem = function(config)
{
    this.directoryMap = {}; //  map<path,(time,[records])>
    this.addEvents(FILESYSTEM_EVENTS.listfiles);
    this.addEvents(FILESYSTEM_EVENTS.history);
};

Ext.extend(FileSystem, Ext.util.Observable,
{
    // causes directory listFiles to be fired
    listFiles : function(path, callback)    // callback(filesystem, success, path, records)
    {
        if (path in this.directoryMap)
        {
            callback(this, success, path, this.directoryMap[path]);
        }
        else
        {
            var ok = this.reloadFiles(path, callback);
            if (!ok && typeof callback == "function")
                callback(this, ok, path, []);
        }
    },

    // causes directory listFiles to be fired, forces refresh from underlying store
    // return false on immediate fail
    reloadFiles : function(path, callback)
    {
    },

    // causes "history" event to be fired
    getHistory : function(path, callback)
    {
        this.fireEvent(FILESYSTEM_EVENTS.history, []);
    },

    // protected
    _addFiles : function(path, records)
    {
        this.directoryMap[path] = records;
        this.fireEvent(FILESYSTEM_EVENTS.listfiles, path, records);
    },

    _c : function(a,b)
    {
        var c = 0;
        if (a.length > 0 && a.charAt(a.length-1)=='/') c++;
        if (b.length > 0 && b.charAt(0)=='/') c++;
        if (c == 0)
            return a + "/" + b;
        else if (c == 1)
            return a + b;
        else
            return a + b.substring(1);
    }
});



//
// WebdavFileSystem
//


// config
// baseUrl: root of the webdav tree (http://localhost:8080/labkey/_webdav)
// rootPath: root of the tree we want to browse e.g. /home/@pipeline/

var WebdavFileSystem = function(config)
{
    config = config || {};
    Ext.apply(this, config, {
        baseUrl: LABKEY.contextPath + "/_webdav",
        rootPath: "/"
    });
    this.prefixUrl = this._c(this.baseUrl, this.rootPath);
    WebdavFileSystem.superclass.constructor.call(this);

    var baseUrl = this.baseUrl;
    this.FileRecord = Ext.data.Record.create(
        [
            {name: 'path', mapping: 'href',
                convert : function (v, rec)
                {
                    return baseUrl ? v.replace(baseUrl, "") : v;
                }
            },
            {name: 'name', mapping: 'propstat/prop/displayname'},
            {name: 'file', mapping: 'href', type: 'boolean',
                convert : function (v, rec)
                {
                    return v.length > 0 && v.charAt(v.length-1) != '/';
                }
            },
            {name: 'creationdate', mapping: 'propstat/prop/creationdate', type: 'date', dateFormat : "c"},
            {name: 'lastmodified', mapping: 'propstat/prop/getlastmodified', type: 'date'},
            {name: 'size', mapping: 'propstat/prop/getcontentlength', type: 'int'},
            {name: 'iconHref'}
        ]);
    this.connection = new Ext.data.Connection({method: "GET", headers: {"Method" : "PROPFIND", "Depth" : "1,noroot"}});
    this.proxy = new Ext.data.HttpProxy(this.connection);
    this.reader = new Ext.data.XmlReader({record : "response", id : "href"}, this.FileRecord);
};

Ext.extend(WebdavFileSystem, FileSystem,
{
    reloadFiles : function(path, callback)
    {
        var url = FileSystem.prototype._c(this.prefixUrl, path);
        this.connection.url = url;

        var args = {url: url, path: path, callback:callback};
        //load(params, reader, callback, scope, arg)
        this.proxy.load({method:"PROPFIND",depth:"1,noroot"}, this.reader, this.processFiles, this, args);
        return true;
    },

    processFiles : function(result, args, success)
    {
        var path = args.path;
        var callback = args.callback;
        var records = [];
        if (success)
        {
            records = result.records;
            this._addFiles(path, records);
        }
        if (typeof callback == "function")
            callback(this, success, path, records);
    }
});


//
// AppletFileSystem
//

// getDropApplet=function()
var AppletFileSystem = function(config)
{
    config = config || {};
    Ext.apply(this, config, {path:"/"});
    WebdavFileSystem.superclass.constructor.call(this);
    this.FileRecord = Ext.data.Record.create(['path', 'name', 'file', 'creationdate', 'lastmodified', 'size', 'iconHref']);
    this.connection = new Ext.data.Connection({method: "GET", headers: {"Method" : "PROPFIND", "Depth" : "1,noroot"}});
    this.proxy = new Ext.data.HttpProxy(this.connection);
    this.reader = new Ext.data.XmlReader({record : "response", id : "href"}, this.FileRecord);
};

Ext.extend(AppletFileSystem, FileSystem,
{
    reloadFiles : function(directory, callback)
    {
        if (!directory)
            return false;
        var applet = this.getDropApplet();
        if (!applet)
            return false;
        if (!applet.local_changeDirectory(directory))
            return false;
        var count = applet.local_getFileCount();
        var records = [];
        for (var i=0 ; i<count ; i++)
        {
            var r = new this.FileRecord();
            r.data = {};
            var name = applet.local_getName(i);
            var file = !applet.local_isDirectory(i);
            var path = file ? this._c(directory,name) : this._c(directory,name+"/");
            var lastModified = applet.local_getTimestamp(i);
            var size = applet.local_getSize(i);
            r.set("name", name);
            r.set("path", path);
            r.set("file", file);
            r.set("lastModified", lastModified);
            r.set("size", size);
            records.push(r);
        }
        this._addFiles(path, records);
        if (typeof callback == "function")
            callback(this, true, path, records);
        return true;
    }
});


//
// FileSystemTreeLoader
//


var FileSystemTreeLoader = function (config)
{
    Ext.apply(this, config);
    this.addEvents("beforeload", "load", "loadexception");
    FileSystemTreeLoader.superclass.constructor.call(this);
};

Ext.extend(FileSystemTreeLoader, Ext.tree.TreeLoader,
{
    fileFilter : null,
    displayFiles: true,
    url: true, // hack for Ext.tree.TreeLoader.load()

    requestData : function(node, callback)
    {
        if (this.fireEvent("beforeload", this, node, callback) !== false)
        {
            var args = {node:node, callback:callback};
            if (this.fileSystem.listFiles(node.id, this.listCallback.createDelegate(this, [args], true)) != false)
                return true;
        }
        if (typeof callback == "function")
            callback();
    },

    createNode : function (data)
    {
        if (data.file)
        {
            if (!this.displayFiles)
                return null;
            if (this.fileFilter)
                data.disabled = !this.fileFilter.test(data.text);
        }
        var n = Ext.apply({},{id:data.path, leaf:data.file, text:data.name},data);
        return FileSystemTreeLoader.superclass.createNode.call(this, n);
    },

    listCallback : function (filesystem, success, path, records, args)
    {
        if (!success)
        {
            if (typeof args.callback == "function")
                args.callback();
            return;
        }
        try
        {
            var node = args.node;
            node.beginUpdate();
            for (var i = 0; i < records.length; i++)
            {
                var record = records[i];
                var data = record.data;
                var n = this.createNode(data);
                if (n)
                    node.appendChild(n);
            }
            node.endUpdate();
            if (typeof args.callback == "function")
                args.callback(this, node);
        }
        catch (e)
        {
//          UNDONE:
//          this.handleFailure(response);
            window.alert(path + " " + e);
        }
    }
});




//
//  FILE BROWSER UI
//
// UNDONE: convert to a proper 'class'


var BROWSER_EVENTS = {selectionchange:"selectionchange", directorychange:"directorychange"};

var FileBrowser = function(config)
{
    this.events.addEvents( [ BROWSER_EVENTS.selectionchange, BROWSER_EVENTS.directorychange ]);
    this.init(config);
};
FileBrowser.prototype =
{
    _debugName : 'fileBrowser',

    // instance/config variables
    showFolders: true,
    showAddressBar: true,
    showDetails: true,
    showProperties: true,

    url: "/labkey/_webdav",
    grid: null,
    store: null,

    currentDirectory: null,
    selectedPath: null,

    //
    // actions
    //

    action : new Ext.Action({text: 'Alert', iconCls: 'blist', scope: this, handler: function()
    {
        window.alert('Click','You clicked on "Action 1".');
    }}),

    getDownloadAction : function()
    {
        return new Ext.Action({text: 'Download', scope: this, handler: function()
            {
                // CONSIDER: PROPFIND to ensure this link is still good?
                var p = this.selectedPath;
                if (p && p.charAt(p.length-1) != '/')
                    window.location = this.url + p + "?contentDisposition=attachment";
            }});
    },

    getParentFolderAction : function()
    {
        return new Ext.Action({text: 'Up', scope: this, handler: function()
        {
            // CONSIDER: PROPFIND to ensure this link is still good?
            var p = this.currentDirectory;
            if (!p)
                p = "/";
            if (p.length > 1 && p.charAt(p.length-1) == '/')
                p = p.substring(0,p.length-1);
            var i = p.lastIndexOf('/');
            if (i > -1)
                p = p.substring(0,i+1);
            this.changeDirectory(p || "/");
        }});
    },

    getRefreshAction : function()
    {
        return new Ext.Action({text: 'Refresh', scope:this, handler: function()
        {
            if (!this.currentDirectory)
                this.currentDirectory = "/";
            this.fileSystem.listFiles(this.currentDirectory, function(filesystem, success, path, records)
            {
                this.store.removeAll();
                this.store.add(records);
            }.createDelegate(this));
            this.tree.getRootNode().reload();
            var sel = this.tree.getSelectionModel().getSelectedNode();
            if (sel)
                sel.reload();
        }});
    },


    changeDirectory : function(path)
    {
        if (this.currentDirectory != path)
        {
            this.currentDirectory = path;
            this.events.fireEvent(BROWSER_EVENTS.directorychange, path);
        }
    },

    selectPath : function(path)
    {
        if (this.selectedPath != path)
        {
            this.selectedPath = path;
            this.events.fireEvent(BROWSER_EVENTS.selectionchange, path);
        }
    },

    //
    // event handlers
    //
    Grid_onRowselect : function(sm, rowIdx, r)
    {
        if (this.tree)
            this.tree.getSelectionModel().clearSelections();
        if (r)
        {
            var path = r.get("path");
            var collection = r.get("collection");
            if (collection && path.charAt(path.length-1) != '/')
                path = path + "/";
            this.selectPath(path);
        }
    },

    Grid_onCelldblclick : function(grid, rowIndex, columnIndex, e)
    {
        var p = this.selectedPath;
        if (p.charAt(p.length-1) == '/')
        {
            this.changeDirectory(p);
            if (this.tree)
            {
                var treePath = treePathFromId(p);
                this.tree.expandPath(treePath);
                var node = this.tree.getNodeById(p);
                if (node)
                {
                    node.ensureVisible();
                    node.select();
                }
            }
        }
    },

    Tree_onSelectionchange : function(sm, node)
    {
        if (this.grid)
            this.grid.getSelectionModel().clearSelections();
        if (node)
        {
            var folder = node.id;
            this.selectPath(folder);
            this.changeDirectory(folder);
        }
    },

    events : new Ext.util.Observable(),

    init : function(config)
    {
        this.fileSystem = config.fileSystem;

        //
        // GRID
        //
        this.store = new Ext.data.Store();
        this.grid = new Ext.grid.GridPanel(
        {
            store: this.store,
            border:false,
            columns: [
                {header: "", width:20, dataIndex: 'iconHref', sortable: false, hiddenn:false, renderer:renderIcon},
                {header: "Name", width: 150, dataIndex: 'name', sortable: true, hidden:false, renderer:Ext.util.Format.htmlEncode},
                {header: "Created", width: 150, dataIndex: 'creationdate', sortable: true, hidden:false, renderer:renderDateTime},
                {header: "Size", width: 80, dataIndex: 'size', sortable: true, hidden:false, align:'right', renderer:renderFileSize}
            ]
        });
        this.grid.getSelectionModel().on('rowselect', this.Grid_onRowselect, this);
        this.grid.on("celldblclick", this.Grid_onCelldblclick, this);

        //
        // TREE
        //

        var treeloader = new FileSystemTreeLoader({fileSystem: this.fileSystem, displayFiles:false});
        var root = treeloader.createNode({path:"/", file:false, name:"/"});
        this.tree = new Ext.tree.TreePanel(
        {
            loader:treeloader,
            root:root,
            rootVisible:false,
            title: 'File Browser',
            useArrows:true,
            autoScroll:true,
            animate:true,
            enableDD:false,
            containerScroll:true,
            border:false,
            pathSeparator:';'
        });
        this.tree.getSelectionModel().on("selectionchange", this.Tree_onSelectionchange, this);

        //
        // LAYOUT
        //
        this.actions =
        {
            downloadAction: this.getDownloadAction(),
            parentFolderAction: this.getParentFolderAction(),
            refreshAction: this.getRefreshAction()
        };
        var tbarConfig =
            [
                {
                    text: 'Action Menu',
                    menu: [this.action]
                },
                this.actions.downloadAction,
                this.actions.parentFolderAction,
                this.actions.refreshAction
            ];
        var layoutItems = [];
        if (this.showAddressBar)
            layoutItems.push(
            {
                region: 'north',
                height: 24,
                margins: '5 5 0 5',
                layout: 'fit',
                border: false,
                items: [{id:'addressBar', html: 'address bar'}]
            });
        if (this.showDetails)
            layoutItems.push(
            {
                title: 'South Panel',
                region: 'south',
                height: 100,
                minSize: 75,
                maxSize: 250,
                margins: '0 5 5 5',
                layout: 'fit',
                items: [{html:'south', id:'file-details'}]
            });
        if (this.showAddressBar)
            layoutItems.push(
            {
                region:'west',
                id:'west-panel',
                split:true,
                width: 200,
                minSize: 100,
                collapsible: true,
                margins:'5 0 5 5',
                layout:'accordion',
                layoutConfig:{animate:true},
                items: [
                    this.tree,
                    {
                        title:'Settings',
                        html:'<p>Some settings in here.</p>',
                        border:false,
                        iconCls:'settings'
                    }]
            });
        layoutItems.push(
            {
                region:'center',
                margins:'5 0 5 0',
                minSize: 200,
                layout:'fit',
                items: [this.grid]
            });
        if (this.showProperties)
            layoutItems.push(
            {
                title: 'Properties',
                region:'east',
                split:true,
                margins:'5 5 5 0',
                width: 150,
                minSize: 100,
                border: false,
                layout: 'fit',
                items: [{html:'<iframe id=auditFrame height=100% width=100% border=0 style="border:0px;" src="about:blank"></iframe>'}]
            });

        var border = new Ext.Panel(
        {
            id:'borderLayout',
            height:600, width:800,
            layout:'border',
            tbar: tbarConfig,
            items: layoutItems
        });

        border.render(config.renderTo);

        var resizer = new Ext.Resizable('borderLayout', {
            width:800, height:600,
            minWidth:640,
            minHeight:400});
        resizer.on("resize", function(o,width,height){
            border.setWidth(width);
            border.setHeight(height);
            resizer.setWidth(border.getWidth());
            resizer.setHeight(border.getHeight());
        });

        //
        // EVENTS (tie together components)
        //

        this.events.on(BROWSER_EVENTS.selectionchange, function(path)
        {
            var el = Ext.get('file-details');
            if (el) el.update(path + "<br>" + treePathFromId(path));
            if (path.charAt(path.length-1) == '/')
                this.actions.downloadAction.disable();
            else
                this.actions.downloadAction.enable();
        }, this);

        this.events.on(BROWSER_EVENTS.directorychange,function(path)
        {
            this.fileSystem.listFiles(path, (function(filesystem, success, path, records)
            {
                if (success)
                {
                    this.store.removeAll();
                    this.store.add(records);
                }
            }).createDelegate(this));
        }, this);

        this.events.on(BROWSER_EVENTS.directorychange, function(path)
        {
            Ext.get('addressBar').update(h(unescape(path)));
        }, this);
    }
};