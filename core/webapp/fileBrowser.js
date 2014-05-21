/*
 * Copyright (c) 2008-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

LABKEY.requiresCss("_images/icons.css");
LABKEY.requiresScript("StatusBar.js");

Ext.namespace('LABKEY.ext');
Ext.namespace('LABKEY.FileSystem');

/**
 * Creates an Ext TreeLoader used to display the contents of a LABKEY.FileSystem.  This is used internally by the FileBrowser and is not part of the public API.  Do not rely on its existence.
 * @class LABKEY.FileSystem.TreeLoader
 * @param [config.filesystem] An object this is a LABKEY.FileSystem.AbstractFileSystem
 * @param [config.fileFilter] Optional.  A Regex that will be tested against the name of each file.  If the regex tests false, that file will be disabled in the tree.
 * @param [config.folderFilter] Optional.  A Regex that will be tested against the name of each folder.  If the regex tests false, that folder will be omitted from the tree.
 * @param [config.displayFiles] Set to true to display both files and folders.  Defaults to true.
 * @private
 * @ignore
 */
LABKEY.FileSystem.TreeLoader = function (config)
{
    Ext.apply(this, config);
    this.id = Ext.id();
    this.addEvents("beforeload", "load", "loadexception");
    LABKEY.FileSystem.TreeLoader.superclass.constructor.call(this);

    this.fileSystem.on(LABKEY.FileSystem.FILESYSTEM_EVENTS.filesremoved, this.onRemovefiles, this);
    this.fileSystem.on(LABKEY.FileSystem.FILESYSTEM_EVENTS.fileschanged, this.onFilesChanged, this);
};

Ext.extend(LABKEY.FileSystem.TreeLoader, Ext.tree.TreeLoader,
{
    debugTree    : null,
    fileFilter   : null,
    folderFilter : null,
    displayFiles : true,
    url          : true, // hack for Ext.tree.TreeLoader.load()

    requestData : function(node, callback)
    {
        if (this.fireEvent("beforeload", this, node, callback) !== false)
        {
            //NOTE: the following is designed to avoid the problem where
            // a child node it loaded before the parent node is loaded, or if the parent
            // was uncached while the child was in the process of loading.
            var parentPath = this.fileSystem.getParentPath(node.id);
            if(parentPath != node.id && !this.fileSystem.directoryFromCache(parentPath))
            {
                //the parent must exist before we load the child
                node.parentNode.on('load', function(){
                    this.requestData(node, callback);
                }, this, {single: true});
            }
            else
            {
                var args = {node:node, callback:callback};
                this.fileSystem.listFiles({
                    path: node.id,
                    scope: this,
                    success: function(filesystem, path, records){
                        this.listCallback(filesystem, path, records, args);
                    },
                    failure: function(response, options){
                        console.log('not found, maybe it was moved?');
                        console.log(options)
                    }
                });
            }
            return;
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
        else if (this.folderFilter && !this.folderFilter.test(data))
            return null;

        var n = Ext.apply({},{id:data.id || data.path, leaf:data.file, text:data.name, href:null}, data);
        return LABKEY.FileSystem.TreeLoader.superclass.createNode.call(this, n);
    },

    createNodeFromRecord : function(record)
    {
        var n = this.createNode(record.data);
        if (n)
        {
            n.record = record;
            if (record.data.iconHref)
                n.attributes.icon = record.data.iconHref;
        }
        return n;
    },

    // on refresh we want to keep the tree in sync
    onFilesChanged : function(filesystem, path, records)
    {
        filesystem.listFiles({
            path: path,
            scope: this,
            success: this.onListFiles,
            failure: LABKEY.Utils.displayAjaxErrorResponse
        });
    },

    onListFiles: function(filesystem, path, records)
    {
        var dir = this.fileSystem.recordFromCache(path);

        // just return for nodes we haven't loaded yet
        if (!dir || !dir.treeNode || !dir.treeNode.loaded){
            return;
        }

        if (this.debugTree)
        {
            var n = this.debugTree.getNodeById(path);
            //NOTE: I think this will occur if a child node is attempting to reload while the parent also does
            //
            if (n && n !== dir.treeNode)
            {
                console.warn("node is not in tree: " + path + ' using alternate');
                dir.treeNode.remove(true);
                dir.treeNode = n;
            }
        }

        var reload = this.mergeRecords(dir.treeNode, records);
        // UNDONE: I don't know why removeChild() is not working!
        if (reload){
            dir.treeNode.reload();
        }
    },

    mergeRecords : function(node, records)
    {
        var i, p, n, r, record;
        var nodesMap = {}, recordsMap = {};
        node.eachChild(function(child){
            nodesMap[child.record.data.path]=child;
        });
        for (i=0 ; i<records.length ; i++)
        {
            record = records[i];
            n = this.createNodeFromRecord(records[i]);
            if (!n) continue;
            recordsMap[record.data.path] = {r:record, n:n};
        }

        var changed = false;
        var removes = false;
        var inserts = false;
        var change = function()
        {
            if (changed) return;
            node.collapse(false,false);
            changed = true;
        };

        for (p in nodesMap)
        {
            if (!(p in recordsMap))
            {
                change();
                node.removeChild(nodesMap[p], true);
                removes = true;
            }
        }

        for (p in recordsMap)
        {
            if (!(p in nodesMap))
            {
                change();
                r = recordsMap[p].r;
                n = recordsMap[p].n;
                var newNode = node.insertBefore(n, null);
                r.treeNode = newNode;
                inserts = true;
            }
        }

        if (changed)
        {
            node.sort(function(a,b)
            {
                var A = a.record.data.name.toUpperCase(), B = b.record.data.name.toUpperCase();
                return A < B ? -1 : 1;
            });
            node.expand(false,false);
        }
        if(removes)
            console.log('has removes: ' + removes)
        return removes;
    },

    errorHandler: function(error, options){
        console.log(error);
        var msg = error ? error.statusText : null;
        Ext4.Msg.alert('Error', msg || 'unknown problem');
    },

    //the callback for fileSystem.listFiles
    listCallback : function (filesystem, path, records, args)
    {
        var node = args.node;
        // NOTE: i think this occurs if a parent node if uncached and
        // the child is subsequently loaded.  requestData() should handle this situation by loading the parent
        if(!node.parentNode && node.id != this.fileSystem.rootPath){
            node.destroy(true);
            //console.warn('destroying orphan node: ' + path)
            return;
        }
        this.mergeRecords(node, records);

        if (typeof args.callback == "function"){
            //NOTE: this seems like a hack, but if something else uncaches the parent of this node before
            //listFiles() returns, we have problems.  when the parent loads it should take care of loading this.
            if(!node.ui){
                console.warn('This node is not part of the tree: ' + path);
                return;
            }

            args.callback(this, node);
        }
        if (node.childNodes.length == 1)
        {
            var child = node.firstChild;
            if (!child.isLeaf())
                child.expand();
        }
    },

    //the callback for fileSystem.removeFiles
    onRemovefiles: function(fileSystem, path, records)
    {
        Ext.each(records, function(rec){
            if(rec.treeNode){
                rec.treeNode.remove(true);
            }
        }, this);
    },

    ancestry : function(id, root)
    {
        var path = id;
        var a = [path];
        while (true)
        {
            var parent = this.fileSystem.getParentPath(path);
            if (!parent || parent == path)
                break;
            a.push(parent);
            if (root && parent == root)
                break;
            path = parent;
        }
        a.reverse();
        return a;
    },

    treePathFromPath : function(path, root)
    {
        var a = this.ancestry(path, root);
        return ";" + a.join(";");
    }
});


//
// TransferApplet
//

LABKEY.FileSystem.TRANSFER_STATES = {
    success   : 1,
    info      : 0,
    failed    : -1,
    retryable : -2
};

LABKEY.FileSystem.TransferApplet;

if (LABKEY.Applet)
{
    LABKEY.FileSystem.TransferApplet = Ext.extend(LABKEY.Applet,
    {
        transferReader : null,
        transfers      : null,
        TransferRecord : Ext.data.Record.create([
            {name:'transferId', mapping:'id'},
            {name:'src'},
            {name:'uri', mapping:'target'},
            {name:'name'},
            {name:'state'},
            {name:'status'},
            {name:'size', mapping:'length'},
            {name:'transferred'},
            {name:'percent'},
            {name:'updated'},
            {name:'md5', mapping:'digest'}
        ]),
        ConsoleRecord : Ext.data.Record.create(['level', 'text']),

        constructor : function(params)
        {
            var config = {
                id: params.id,
                archive: LABKEY.contextPath + '/_applets/applets.jar',
                code: 'org.labkey.applets.drop.DropApplet',
                width: params.width,
                height: params.height,
                params:
                {
                    url : params.url || (window.location.protocol + "//" + window.location.host + LABKEY.contextPath + '/_webdav/'),
                    events : "window._evalContext.",
                    webdavPrefix: LABKEY.contextPath+'/_webdav/',
                    user: LABKEY.user.email,
                    password: LABKEY.Utils.getSessionID(),
                    text : params.text,
                    allowDirectoryUpload : params.allowDirectoryUpload !== false,
                    overwrite : "true",
                    autoStart : "true",
                    enabled : !('enabled' in params) || params.enabled,
                    dropFileLimit : 5000,
                    "Common.WindowMode":"true"
                }
            };
            LABKEY.FileSystem.TransferApplet.superclass.constructor.call(this, config);
//            console.log("TransferApplet.url: " + config.params.url);
        },

        initComponent : function()
        {
            LABKEY.FileSystem.TransferApplet.superclass.initComponent.call(this);

            this.addEvents(["update"]);

            this.transferReader = new Ext.data.JsonReader({successProperty:'success', totalProperty:'recordCount', root:'records', id:'target'}, this.TransferRecord);
            this.transfers = new Ext.data.Store();
            this.consoleReader = new Ext.data.JsonReader({successProperty:'success', totalProperty:'recordCount', root:'records'}, this.ConsoleRecord);
            var consoleCounter = 0;
            this.consoleReader.getId = function(){return ++consoleCounter;};
            this.console = new Ext.data.Store();

            this.transfers.on("add", function(store, records)
            {
                for (var i=0 ; i<records.length ; i++)
                    console.debug('TransferApplet.add: ' + records[i].get('uri') + ' ' + records[i].get('status'));
            });
        },

        destroy : function()
        {
            Ext.TaskMgr.stop(this.pollTask);
            LABKEY.FileSystem.TransferApplet.superclass.destroy.call(this);
        },

        onRender : function(ct, position)
        {
            LABKEY.FileSystem.TransferApplet.superclass.onRender.call(this, ct, position);
            // callbacks work terribly on some browsers, just poll for updates
            this.pollTask = {interval:100, scope:this, run:this._poll};
            Ext.TaskMgr.start(this.pollTask);
        },

        /* private */
        _poll : function()
        {
            var a = this.getApplet();
            if (null == a)
                return;
            var event;
            while (event = a.getEvent())
            {
                window._evalContext = this;
                window.eval("" + event);
            }
        },

        /* private */
        _merge : function(store,records)
        {
            var adds = [];
            for (var i = 0 ; i<records.length ; i++)
            {
                var update = records[i];
                var id = update.id;
                var record = store.getById(id);
                if (null == record)
                {
                    adds.push(update);
                }
                else //if (update.get('updated'))
                {
                    Ext.apply(record.data,update.data);
                    store.afterEdit(record);
                }
            }
            store.add(adds);
        },

        changeWorkingDirectory : function(path)
        {
            var applet = this.getApplet();
            if (applet)
                applet.changeWorkingDirectory(path);
            else if (!this.rendered)
                this.params.directory = path;
            else
                console.error("NYI: Do not call before isReady()!");
        },

        setEnabled : function(b)
        {
            var applet = this.getApplet();
            if (applet)
                applet.setEnabled(b ? true : false);
            else if (!this.rendered)
                this.params.enabled = b ? 'true' : 'false';
            else
                console.error("NYI: Do not call before isReady()!");
        },

        setText : function(text)
        {
            var applet = this.getApplet();
            if (applet)
                applet.setText(text);
            else if (!this.rendered)
                this.params.text = text;
            else
                console.error("NYI: Do not call before isReady()!");
        },

        setAllowDirectoryUpload : function(b)
        {
            var applet = this.getApplet();
            if (applet)
                applet.setAllowDirectoryUpload(b ? true : false);
            else if (!this.rendered)
                this.params.allowDirectoryUpload = b ? 'true' : 'false';
            else
                console.error("NYI: Do not call before isReady()!");
        },

        getTransfers : function()
        {
            return this.transfers;
        },


        getSummary : function()
        {
            var success=0, info=0, failed=0, retryable=0;
            var transfers = this.transfers;
            var count = transfers.getCount();
            //var records = transfers.getRecords();
            for (var i = 0 ; i<count ; i++)
            {
                var record = transfers.getAt(i);
                var state = record.data.state;
                switch (state)
                {
                case LABKEY.FileSystem.TRANSFER_STATES.success: success++; break;
                case LABKEY.FileSystem.TRANSFER_STATES.info: info++; break;
                case LABKEY.FileSystem.TRANSFER_STATES.failed: failed++; break;
                case LABKEY.FileSystem.TRANSFER_STATES.retryable: retryable++; break;
                }
            }
            return {success:success, info:info, failed:failed, retryable:retryable,
                total:count, totalActive:success+info+failed};
        },


        // EVENTS
        dragEnter : function()
        {
            //console.debug('TransferApplet.dragEnter');
        },
        dragExit : function()
        {
            //console.debug('TransferApplet.dragExit');
        },
        update : function()
        {
            var a = this.getApplet();
            var r,result,records;
            var updated = false;

            if (a.transfer_hasUpdates())
            {
                // merge updates into data store
                r = a.transfer_getObjects();
                result = eval("(" + r + ")");
                if (!result.success)
                    console.error(r);
                records = this.transferReader.readRecords(result);
                this._merge(this.transfers,records.records);
                updated = true;
            }

            var consoleLineCount = a.console_getLineCount();
            if (consoleLineCount > this.console.getCount())
            {
                r = a.console_getRange(this.console.getCount(), consoleLineCount);
                result = eval("(" + r + ")");
                records = this.consoleReader.readRecords(result);
                this.console.add(records.records);
                for (var i=0 ; i<records.records.length ; i++)
                {
                    var data = records.records[i].data;
                    var level = data.level;
                    var text = data.text;
                    if (!text) continue;
                    if (level >= LABKEY.FileSystem.Util.ERROR_INT)
                        console.error(text);
                    else if (level >= LABKEY.FileSystem.Util.WARN_INT)
                        console.warn(text);
                    else if (level >= LABKEY.FileSystem.Util.INFO_INT)
                        console.info(text);
                    else
                        console.debug(text);
                }
            }

            if (updated)
                this.fireEvent("update");
        }
    });
}


/**
 * This class creates the LabKey FileBrowser.  It is not part of the public API and should not be used directly.
 * If you would like to add a FileBrowser to a custom page, see the files webpart instead.
 * @class LABKEY.ext.FileBrowser
 * @param [config.fileSystem] Required.  A LabKey Filesystem object, such as LABKEY.FileSystem.WebdavFileSystem
 * @param [config.resizable] Optional, defaults to true
 * @param [config.showFolders] Optional, defaults to true
 * @param [config.showAddressBar] Optional, defaults to true
 * @param [config.showDetails] Optional, defaults to true
 * @param [config.showProperties] Optional, defaults to false
 * @private
 * @ignore
 */
LABKEY.ext.FileBrowser = function(config){
    LABKEY.ext.FileBrowser.superclass.constructor.call(this, config);
};

Ext.extend(LABKEY.ext.FileBrowser, Ext.Panel,
{
    FOLDER_ICON : LABKEY.FileSystem.FOLDER_ICON,

    // instance/config variables
    allPreviews : [],
    resizable : true,
    allowChangeDirectory : true,
    showFolderTree: true,
    folderTreeCollapsed: false,
    showAddressBar: true,
    showDetails: true,
    showProperties: true,
    showFileUpload: true,
    propertiesPanel : null,
    statePrefix : null,
    cls: 'labkey-file-browser-elem',

    grid: null,
    store: null,

    currentDirectory: null,
    selectedRecord: null,

    helpEl : null,
    helpWindow : null,

    history : null,

    // collapsible tab panel used to display dialog-like content
    fileUploadPanel : undefined,

    // file upload form field
    fileUploadField : undefined,

    tbarItems : undefined,

    fileFilter : undefined,

    progressRecord : null,

    lastSummary: {info:0, success:0, file:'', pct:0},

    notifyStates : {},

    //
    // actions
    //

    actions : {},

    errorHandler: function(response, options){
        console.log(response);
        console.log(options);

        var msg = error ? error.statusText : null;
        Ext4.Msg.alert('Error', msg || 'unknown problem');
    },

    getDownloadAction : function()
    {
        return new Ext.Action({text: 'Download', tooltip: 'Download the selected files or folders', iconCls:'iconDownload', 
            disabledClass:'x-button-disabled',
            scope: this,
            hideText: true,
            handler: function() {
                var selections = this.grid.selModel.getSelections();

                if (selections.length == 1 && selections[0].data.file)
                {
                    if (this.selectedRecord.data.uri)
                    {
                        window.location = this.selectedRecord.data.uri + "?contentDisposition=attachment";
                    }
                }
                else if (this.currentDirectory.data.uri)
                {
                    var url = this.currentDirectory.data.uri + "?method=zip&depth=-1";
                    for (var i = 0; i < selections.length; i++)
                    {
                        url = url + "&file=" + encodeURIComponent(selections[i].data.name);
                    }

                    window.location = url;
                }
            }});
    },

    getCreateDirectoryAction : function()
    {
        return new Ext.Action({text: 'Create Folder', iconCls:'iconFolderNew', tooltip: 'Create a new folder on the server',
            disabledClass:'x-button-disabled',
            scope: this,
            hideText: true,
            handler: function() {
                var panel = new Ext.form.FormPanel({
                    bodyStyle : 'padding:20px;',
                    border: false,
                    items: [{
                        xtype: 'textfield',
                        allowBlank: false,
                        name: 'folderName',
                        fieldLabel: 'Folder Name',
                        value: 'New Folder',
                        scope: this,
                        validator: function(folder){
                            var scope = this.initialConfig.scope;
                            if (folder && folder.length && scope)
                            {
                                var p = scope.currentDirectory.data.path;
                                var path = scope.fileSystem.concatPaths(p, folder);
                                var file = scope.fileSystem.recordFromCache(path);
                                if (file)
                                    return 'The folder already exists';
                            }
                            return true;
                        }
                    }]
                });

                var win = new Ext.Window({
                    title: 'Create Folder',
                    width: 300,
                    height: 150,
                    cls: 'extContainer',
                    autoScroll: true,
                    closeAction:'close',
                    modal: true,
                    layout: 'fit',
                    items: panel,
                    buttons: [
                        {text:'Submit', scope: this, handler:function() {
                            if (panel.getForm().isValid())
                            {
                                var values = panel.getForm().getValues();
                                if (values && values.folderName)
                                {
                                    var folder = values.folderName;
                                    var p = this.currentDirectory.data.path;
                                    var path = this.fileSystem.concatPaths(p, folder);
                                    this.fileSystem.createDirectory({
                                        path: path,
                                        success: this._refreshOnCallbackSelectPath.createDelegate(this),
                                        failure: LABKEY.Utils.displayAjaxErrorResponse
                                    });
                                }
                                win.close();
                            }
                        }},
                        {text:'Cancel', handler:function(){win.close();}}
                    ]
                });
                win.show();

            }.createDelegate(this)});
    },

    _refreshOnCallbackSelectPath : function(fs, path)
    {
        if (this.actions.refresh)
            this.actions.refresh.execute();
        if (path)
        {
            this.selectFile(path);
        }
    },

    getParentFolderAction : function()
    {
        return new Ext.Action({text: 'Parent Folder', tooltip: 'Navigate to parent folder', iconCls:'iconUp',
            disabledClass:'x-button-disabled',
            scope: this,
            hideText: true,
            handler: function() {
                // CONSIDER: PROPFIND to ensure this link is still good?
                var p = this.currentDirectory.data.path;
                var dir = this.fileSystem.getParentPath(p) || this.fileSystem.rootPath;
                this.changeDirectory(dir);
            }});
    },

    getRefreshAction : function()
    {
        return new Ext.Action({text: 'Refresh', tooltip: 'Refresh the contents of the current folder', iconCls:'iconReload',
            disabledClass:'x-button-disabled',
            scope:this,
            hideText: true,
            handler: function() {
                this.refreshDirectory();
                this.refreshFolderTree();
        }});
    },

    getZipFolderAction : function()
    {
        return new Ext.Action({text: 'Zip Folder', tooltip: 'Download folder as a .zip file', iconCls:'iconZip',
            disabledClass:'x-button-disabled',
            scope: this,
            handler: function() {
                var uri = this.currentDirectory.data.uri;
                window.location = uri + "?method=zip";
            }});
    },

    _moveOnCallback : function(fs, source, destination, record)
    {
        this._deleteOnCallback(fs, source, record);

        this.fileSystem.listFiles({
            path: this.fileSystem.getParentPath(destination),
            scope: this,
            success: function(){
                this.refreshFolderTree();
            }
        });


        this.fireEvent(LABKEY.FileSystem.BROWSER_EVENTS.movecomplete);
    },

    // UNDONE: use uri or path as id
    _deleteOnCallback : function(fs, path, record)
    {
        //this.store.remove(record);
        var id = record.id;
        var rem = this.store.getById(id);
        if (rem)
            this.store.remove(rem);

        if (this.tree)
        {
            var node = this.tree.getNodeById(this.currentDirectory.data.path);
            if (node)
            {
                // If the user deleted a directory, we need to clean up the folder tree
                if (!record.data.file)
                {
                    var parentNode;
                    var childNodeToRemove;
                    // The selection might be from the tree, or from the file list
                    if (node.attributes.path == record.data.path)
                    {
                        // If the path of the selected node in the tree matches what we deleted, use that node
                        parentNode = node.parentNode;
                        childNodeToRemove = node;
                    }
                    else
                    {
                        // Otherwise, the selection came from the list and we need to find the right child
                        // node in the tree
                        childNodeToRemove = node.findChild("path", record.data.path);
                        if (childNodeToRemove)
                        {
                            parentNode = node;
                        }
                    }
                    // Check if we found the child and parent nodes in the tree
                    if (parentNode && childNodeToRemove)
                    {
                        // Remove the child from the tree since it's been deleted from the disk
                        parentNode.removeChild(childNodeToRemove);
                    }
                }
                else
                {
                    node.reload();
                }

                // Refresh the list view to make sure that we get rid of the deleted entry there too
                this.refreshDirectory();
            }
        }

        this.fireEvent(LABKEY.FileSystem.BROWSER_EVENTS.deletecomplete);
    },

    getDeleteAction : function()
    {
        return new Ext.Action({text: 'Delete', tooltip: 'Delete the selected files or folders', iconCls:'iconDelete',
            disabledClass:'x-button-disabled',
            scope:this,
            disabled:true,
            hideText: true,
            handler: function() {
                if (!this.currentDirectory || !this.selectedRecord)
                    return;

                var selections = this.grid.selModel.getSelections();
                var fnDelete = (function()
                {
                    this.fireEvent(LABKEY.FileSystem.BROWSER_EVENTS.deletestarted);
                    for (var i = 0; i < selections.length; i++)
                    {
                        var selectedRecord = selections[i];
                        this.fileSystem.deletePath({
                            path: selectedRecord.data.path,
                            isFile: selectedRecord.data.file,
                            success: this._deleteOnCallback.createDelegate(this,[selectedRecord],true),
                            failure: LABKEY.Utils.displayAjaxErrorResponse,
                            scope: this
                        });
                    }
                    this.selectFile(null);

                }).createDelegate(this);

                if (selections.length)
                {
                    var fileCount = 0;
                    var fileName;
                    var dirCount = 0;
                    var dirName;
                    for (var i = 0; i < selections.length; i++)
                    {
                        var selectedRecord = selections[i];
                        if (selectedRecord.data.file)
                        {
                            fileCount++;
                            fileName = selectedRecord.data.name;
                        }
                        else
                        {
                            dirCount++;
                            dirName = selectedRecord.data.name;
                        }
                    }
                    var message = "Are you sure that you want to delete the ";
                    if (fileCount == 1)
                    {
                        message += "file '" + fileName + "'";
                    }
                    else if (fileCount > 1)
                    {
                        message += fileCount + " selected files";
                    }

                    if (fileCount > 0 && dirCount > 0)
                    {
                        message += " and ";
                    }

                    if (dirCount == 1)
                    {
                        message += "directory '" + dirName + "' (including its content)";
                    }
                    else if (dirCount > 1)
                    {
                        message += dirCount + " selected directories (including their contents)";
                    }
                    message += "?";

                    Ext.MessageBox.confirm("Confirm delete", message, function(answer)
                    {
                        if (answer == "yes")
                        {
                            fnDelete();
                        }
                    });
                }
            }});
    },

    getRenameAction : function()
    {
        return new Ext.Action({
            text: 'Rename',
            tooltip: 'Rename the selected file or folder',
            iconCls:'iconRename',
            disabledClass:'x-button-disabled',
            scope:this,
            disabled:true,
            hideText: true,
            handler: function() {
                if (!this.currentDirectory || !this.selectedRecord)
                    return;

                var selections = this.grid.selModel.getSelections();
                if(selections.length != 1)
                {
                    Ext.Msg.alert('Error', 'Must select a single file');
                    return;
                }

                var fnRename = (function(destination)
                {
                    this.fireEvent(LABKEY.FileSystem.BROWSER_EVENTS.movestarted);
                    var selectedRecord = selections[0];
                    this.fileSystem.renamePath({
                        source: selectedRecord.data.path,
                        destination: destination,
                        isFile: selectedRecord.data.file,
                        success: this._moveOnCallback.createDelegate(this,[selectedRecord],true),
                        failure: LABKEY.Utils.displayAjaxErrorResponse,
                        scope: this
                    });
                    this.selectFile(null);

                }).createDelegate(this);

                var selectedRecord = selections[0];
                var name = selectedRecord.data.name;

                function okHandler(){
                    var field = win.find('itemId', 'nameField')[0];
                    var newName = field.getValue();

                    if(!newName || !field.isValid()){
                        Ext4.Msg.alert("Warning", 'Must enter a valid filename');
                    }

                    if(newName == win.origName){
                        win.close();
                        return;
                    }

                    var newPath;
                    var regex;
                    var destination = this.fileSystem.getParentPath(win.fileRecord.get('path'));

                    this.doMove([{
                        record: win.fileRecord,
                        newName: newName
                    }], destination, function(){
                        win.close();
                    }, this);
                }

                var win = new Ext.Window({
                    title: "Rename",
                    width: 280,
                    autoHeight: true,
                    modal: true,
                    closeAction: 'hide',
                    origName: name,
                    fileRecord: selectedRecord,
                    renameFile: fnRename,
                    items: [{
                        xtype: 'form',
                        labelAlign: 'top',
                        bodyStyle: 'padding: 10px;',
                        items: [{
                            xtype: 'textfield',
                            allowBlank: false,
                            regex: /^[^@\/\\;:?<>*|"^][^\/\\;:?<>*|"^]*$/,
                            regexText: "Folder must be a legal filename and not start with '@' or contain one of '/', '\\', ';', ':', '?', '<', '>', '*', '|', '\"', or '^'",
                            width: 250,
                            labelAlign: 'top',
                            itemId: 'nameField',
                            fieldLabel: 'Filename',
                            value: name
                        }]
                    }],
                    buttons: [{
                        text: 'Rename',
                        scope: this,
                        handler: function(btn){
                            var win = btn.findParentByType('window');
                            okHandler.call(this, win);
                        }
                    },{
                        text: 'Cancel',
                        handler: function(btn){
                            btn.findParentByType('window').close();
                        }
                    }],
                    keys: [{
                        key: Ext.EventObject.ENTER,
                        scope: this,
                        handler: okHandler
                    }],
                    listeners: {
                        scope: this,
                        afterrender: function(win){
                            var field = win.find('itemId', 'nameField')[0];
                            field.focus(false, 50);
                        }
                    }
                }).show();
            }
        });
    },

    getMoveAction : function()
    {
        return new Ext.Action({
            text: 'Move',
            tooltip: 'Move the selected file or folder',
            iconCls:'iconMove',
            disabledClass:'x-button-disabled',
            scope:this,
            disabled:true,
            hideText: true,
            handler: function() {
                if (!this.currentDirectory || !this.selectedRecord)
                    return;

                var selections = this.grid.selModel.getSelections();
                if(selections.length == 0)
                {
                    return;
                }

                function validateNode(record, node){
                    if(record.data.options.indexOf('MOVE') == -1){
                        node.disabled = true;
                    }

                    var path;
                    Ext.each(selections, function(rec){
                        path = rec.get('path');
                        if(this.fileSystem.isChild(node.id, path) || node.id == path || node.id == this.fileSystem.getParentPath(rec.get('path'))){
                            node.disabled = true;
                            return false;
                        }
                    }, this);
                }

                var treeLoader = this.tree.loader;
                var root = treeLoader.createNodeFromRecord(this.fileSystem.rootRecord, this.fileSystem.rootPath);

                validateNode.call(this, this.fileSystem.rootRecord, root);

                var treePanel = new Ext.tree.TreePanel({
                    xtype           : 'treepanel',
                    itemId          : 'treepanel',
                    height          : 200,
                    loader          : treeLoader,
                    root            : root,
                    rootVisible     : true,
                    //useArrows       : true,
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
                    pathSeparator   : ';',
                    listeners: {
                        scope: this,
                        beforeappend: function(tree, parentNode, node){
                            var record = this.fileSystem.recordFromCache(node.id);
                            if(!record){
                                //console.log('folder not found: ' + node.id);
                                node.disabled = true;
                                return;
                            }

                            validateNode.call(this, record, node)
                        }
                    }
                });
                treePanel.getRootNode().expand();

                function okHandler(win){
                    var panel = win.find('itemId', 'treepanel')[0];
                    var node = panel.getSelectionModel().getSelectedNode();
                    if(!node){
                        Ext4.Msg.alert('Warning', 'Must pick a destination folder');
                        return;
                    }

                    var source = '';
                    var destination = node.id;

                    var toMove = [];
                    Ext.each(win.fileRecords, function(r){
                        toMove.push({
                            record: r
                        });
                    }, this);

                    this.doMove(toMove, destination, function(){
                        win.close();
                    }, this);
                }

                var win = new Ext.Window({
                    title: "Choose Destination",
                    modal: true,
                    cls: 'extContainer',
                    width: 270,
                    //autoHeight: true,
                    closeAction: 'hide',
                    origName: name,
                    fileRecords: selections,
                    items: [{
                        bodyStyle: 'padding: 10px;',
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
                }).show();
            }
        });
    },

    doMove: function(toMove, destination, callback, scope)
    {
        //verify the contents of destination are loaded
        var filesVerified = 0;
        var nameConflicts = [];

        //check for name conflicts
        for (var i = 0; i < toMove.length; i++){
            var selected = toMove[i];

            this.fileSystem.checkForNameConflict({
                name: selected.newName || selected.record.get('name'),
                path: destination,
                success: checkCallback,
                failure: LABKEY.Utils.displayAjaxErrorResponse,
                scope: this
            });
        }

        function checkCallback(fs, name, dest, record){
            filesVerified++;
            if(record){
                nameConflicts.push(name);
            }

            if(filesVerified == toMove.length){
                onValidateFinished.call(this, toMove, nameConflicts);
            }
        }

        function onValidateFinished(toMove, nameConflicts){
            if(nameConflicts.length){
                var msg = 'Files already exist in the target folder with the following names: <br>' +
                    nameConflicts.join(',<br>') +
                    '<br>These will be overwritten.  Do you want to continue?';

                Ext.Msg.confirm('Name Conflicts', msg, function(btn){
                    if(btn == 'yes'){
                        performMove.call(this);
                    }
                    else {
                        callback.call(scope || this);
                    }
                }, this);
            }
            else {
                performMove.call(this);
            }

            function performMove(){
                this.fireEvent(LABKEY.FileSystem.BROWSER_EVENTS.movestarted);
                var filesMoved = 0;

                for (var i = 0; i < toMove.length; i++){
                    var selected = toMove[i];
                    var newPath = this.fileSystem.concatPaths(destination, (selected.newName || selected.record.data.name));

                    //used to close the ext window
                    if(callback)
                        callback.call(this);

                    this.fileSystem.movePath({
                        source: selected.record.get('path'),
                        destination: newPath,
                        isFile: selected.record.get('file'),
                        success: function(fs, source, destination, response){
                            this._moveOnCallback(fs, source, destination, selected.record);
                        },
                        failure: LABKEY.Utils.displayAjaxErrorResponse,
                        scope: this,
                        overwrite: true
                    });
                }
                this.selectFile(null);
            }
        }
    },

    getHelpAction : function()
    {
        if (!this.helpEl)
            return null;
        return new Ext.Action({text: 'Help', scope:this, handler: function()
        {
            if (!this.helpWindow)
            {
                this.helpWindow = new Ext.Window({contentEl:this.helpEl, closeAction:'hide'});
            }
            this.helpWindow.show();
        }});
    },

    getShowHistoryAction : function()
    {
        return new Ext.Action({text: 'Show History', scope:this, handler: function()
        {
            if (!this.history || this.history.length == 0)
                return;
            var items = this.history;
            var html = [];
            for (var i=0 ; i<items.length ; i++)
            {
                var item = items[i];
                html.push("<b>"); html.push(item.date); html.push("</b><br>");
                html.push(Ext.util.Format.htmlEncode(item.user)); html.push("<br>");
                html.push(Ext.util.Format.htmlEncode(item.message));
                if (html.href)
                {
                    html.push("<a color=green href='"); html.push(Ext.util.Format.htmlEncode(html.href)); html.push("'>link</a><br>");
                }
            }
        }});
    },

    updateProgressBarRecord : function(store,record)
    {
        var state = record.get('state');
        var progress = (record.get('percent')||0)/100;

        if (state == 0 && 0 < progress && progress < 1.0)
            this.progressRecord = record;
        else if (state != 0 && this.progressRecord == record)
            this.progressRecord = null;
    },

    updateProgressBar : function()
    {
        var record = this.progressRecord && this.progressRecord.get('state') == 0 ? this.progressRecord : null;
        var pct = record ? record.get('percent')/100 : 0;
        var file = record ? record.get('name') : '';

        var summary = this.applet.getSummary();

        if (summary.info != this.lastSummary.info)
        {
            if (summary.info == 0)
            {
                this.appletStatusBar.clearStatus();
                this.appletStatusBar.setText('Ready');
            }
            else
            {
                this.appletStatusBar.busyText = 'Copying... ' + summary.info + ' file' + (summary.info>1?'s':'');
                this.appletStatusBar.showBusy();
            }
        }

        if (record)
        {
            if (!this.progressBar.isVisible())
                this.progressBar.show();
            if (pct != this.lastSummary.pct || file != this.lastSummary.file)
                this.progressBar.updateProgress(pct, file);
        }
        else
            this.progressBar.hide();

        // UNDONE: failed transfers
        this.lastSummary = summary;
        this.lastSummary.pct = pct;
        this.lastSummary.file = file;
    },

    /**
     * Adds a marker class to determine when a directory change
     * event is complete with respect to the file content tool.
     * This is used to help with the selenium tests.
     */
    onGridDataChanged : function(complete) {

        var el = this.getTopToolbar().getEl();

        if (el)
        {
            if (complete)
                el.addClass('labkey-file-grid-initialized');
            else
                el.removeClass('labkey-file-grid-initialized');
        }
    },

    //private
    fireUploadEvents : function()
    {
        var transfers   = this.applet.getTransfers();
        var count       = transfers.getCount(),
        incompleteCount = 0, i = 0,
        newlyAdded      = [],
        newlyComplete   = [],
        pending         = []; // files and folders just dropped that need to be checked for overwrite

        //Try to notify in batches.
        //If new files are added fire a transferstarted event
        //If all started files are finished, file a transfercomplete event for all of them
        
        for (i = 0 ; i<count ; i++)
        {
            var record = transfers.getAt(i);
            var id = new LABKEY.URI(record.id).pathname; //Normalize paths...
            var name = record.get("name");
            var state = record.get("state");
            var transferId = record.get('transferId');

            var notifyInfo =  this.notifyStates[id];
            if (!notifyInfo)
            {
                notifyInfo = {notified:null, name:name};
                this.notifyStates[id] = notifyInfo;
            }
            notifyInfo.current = state;

            if (state == LABKEY.FileSystem.TRANSFER_STATES.info || state == LABKEY.FileSystem.TRANSFER_STATES.success)
                newlyAdded.push({id:id, name:name});
            else if (state == LABKEY.FileSystem.TRANSFER_STATES.retryable)
                pending.push({id:id, name:name, recordId:record.id});

            if (state == LABKEY.FileSystem.TRANSFER_STATES.success && notifyInfo.notified != LABKEY.FileSystem.TRANSFER_STATES.success)
                newlyComplete.push({id:id, name:name, recordId:record.id});

            if (state != LABKEY.FileSystem.TRANSFER_STATES.retryable)
                this.notifyStates[id] = notifyInfo;

            if (state == LABKEY.FileSystem.TRANSFER_STATES.info)
                incompleteCount++;
        }

        if (pending.length != 0)
        {
            var pendingFiles = [];
            var pendingFolders = {};

            for (i=0; i < pending.length; i++)
            {
                var rec = pending[i];
                var target = rec.id.substring(this.fileSystem.prefixUrl.length);
                rec.target = decodeURIComponent(target);

                // group by files and folders
                var relativePath = decodeURIComponent(rec.recordId.substring(this.currentDirectory.data.uri.length));

                if (relativePath == rec.name)
                    pendingFiles.push(rec);
                else
                {
                    var parent = this.fileSystem.getParentPath(rec.target);
                    if (!pendingFolders[parent])
                        pendingFolders[parent] = {parent:parent};
                }
            }

            // make sure folders have been listed by the client, else we need to make an async request
            for (var dir in pendingFolders)
            {
                if (!this.fileSystem.directoryFromCache(dir))
                {
                    this.fileSystem.listFiles({
                        path: dir,
                        success: function(filesystem, parentPath, records){
                            if (pendingFolders[parentPath])
                                pendingFolders[parentPath].ready = true;

                            this.checkForTransferConflicts(pending, pendingFolders, pendingFiles);
                        },
                        failure: LABKEY.Utils.displayAjaxErrorResponse,
                        scope: this
                    });
                }
                else
                    pendingFolders[dir].ready = true;
            }
            this.checkForTransferConflicts(pending, pendingFolders, pendingFiles);
        }

        if (newlyAdded.length != 0)
        {
            this.fireEvent(LABKEY.FileSystem.BROWSER_EVENTS.transferstarted, {uploadType:"applet", files:newlyAdded});
            for (i = 0; i < newlyAdded.length; i++)
                this.notifyStates[newlyAdded[i].id].notified = LABKEY.FileSystem.TRANSFER_STATES.info;
        }

        //If all complete, notify all the completed guys at once
        if (incompleteCount == 0 && newlyComplete.length > 0)
        {
            this.fireEvent(LABKEY.FileSystem.BROWSER_EVENTS.transfercomplete, {uploadType:"applet", files:newlyComplete});
            var rec;
            for (i = 0; i < newlyComplete.length; i++)
            {
                rec = newlyComplete[i];
                this.notifyStates[rec.id].notified = LABKEY.FileSystem.TRANSFER_STATES.success;

                // remove this transfer record from the list so it doesn't appear in subsequent status requests
                this.clearTransferFile(rec.recordId);
            }
        }
    },

    /**
     * Removes the transfer record from both the local cache and the list maintained in the drop applet.
     * @param recordId - the id of the record in the local store.
     * @private
     */
    clearTransferFile : function(recordId)
    {
        var transfers = this.applet.getTransfers();
        var tr = transfers.getById(recordId);
        if (tr)
        {
            transfers.remove(tr);
            var idx = this.applet.getApplet().transfer_getIndex(tr.get('transferId'));
            if (idx != -1)
                this.applet.getApplet().transfer_removeFile(idx);
        }
    },

    checkForTransferConflicts : function(pending, pendingFolders, pendingFiles)
    {
        // folders may not have been listed in the filesystem yet, wait until they are all processed
        for (var dir in pendingFolders)
        {
            var rec = pendingFolders[dir];
            if (rec && !rec.ready)
                return;
        }

        var conflicted = [];

        for (var i=0; i < pendingFiles.length; i++)
        {
            rec = pendingFiles[i];
            if (this.fileSystem.recordFromCache(rec.target))
            {
                var target = rec.id.substring(this.fileSystem.prefixUrl.length);
                target = target.substring(1);
                //conflicted.push({name:decodeURIComponent(target)});
                conflicted.push({name:rec.name});
            }
        }

        for (dir in pendingFolders)
        {
            if (this.fileSystem.directoryFromCache(dir))
                conflicted.push({name:dir});
        }

        var transfers = this.applet.getTransfers();
        if (conflicted.length != 0)
        {
            var txt = this.fileReplaceTemplate.apply({files:conflicted});

            Ext.MessageBox.confirm('Confirm replacement', txt, function(btn){
                if (btn == 'yes')
                {
                    this.doStartTransfer(transfers, pending);
                }
                else
                {
                    for (i=0; i < pending.length; i++)
                    {
                        var item = pending[i];
                        this.clearTransferFile(item.recordId);
                    }
                }
            }, this);
        }
        else
            this.doStartTransfer(transfers, pending);
    },

    /**
     * Initiate file upload for pending dropped files
     */
    doStartTransfer : function(transfers, pendingFiles)
    {
        for (var i=0; i < pendingFiles.length; i++)
        {
            var item = pendingFiles[i];
            var record = transfers.getById(item.recordId);
            if (record)
            {
                record.state = LABKEY.FileSystem.TRANSFER_STATES.info;
                var idx = this.applet.getApplet().transfer_getIndex(record.get('transferId'));
                if (idx != -1)
                    this.applet.getApplet().transfer_overwrite(idx);
            }
        }
    },

    getUploadToolAction : function()
    {
        return new Ext.Action({text: 'Multi-file Upload', iconCls: 'iconUpload', tooltip:"Upload multiple files or folders using drag-and-drop<br>(requires Java)", scope:this, disabled:true, handler: function()
        {
            if (!this.applet || !this.appletWindow)
                this.layoutAppletWindow();
            this.appletWindow.show();
        }});
    },

    changeDirectory : function(record, force)
    {
        if (!this.fileSystem.ready)
        {
            this.fileSystem.onReady(function(){this.changeDirectory(record,force);},this);
            return;
        }

        if (typeof record == "string")
        {
            var fullPath = record;
            record = this.fileSystem.recordFromCache(fullPath);
            if (!record)
            {
                var parents = [];
                var parent, currPath = fullPath;
                do
                {
                    parent = this.fileSystem.getParentPath(currPath);
                    if (parent && currPath && !this.fileSystem.recordFromCache(currPath))
                        parents.push(parent);
                    currPath = parent;
                } while (currPath && currPath != '/');
                var cb = function(filesystem,parentPath,records)
                {
                    if (parents.length)
                        this.fileSystem.listFiles({
                            path: parents.pop(),
                            success: cb,
                            failure: LABKEY.Utils.displayAjaxErrorResponse,
                            scope: this
                        });
                    else
                    {
                       record = this.fileSystem.recordFromCache(fullPath);
                       if (record)
                           this.changeDirectory(record);
                        return;
                    }
                };
                if (parents.length){
                    this.fileSystem.listFiles({
                        path: parents.pop(),
                        success: cb,
                        failure: LABKEY.Utils.displayAjaxErrorResponse,
                        scope: this
                    });
                }
                return;
            }
        }

        if (record && !record.data.file && (force || this.currentDirectory != record))
        {
            this.currentDirectory = record;
            if (this.statePrefix)
                Ext.state.Manager.set(this.statePrefix+'.currentDirectory', record.data.path);
            this.fireEvent(LABKEY.FileSystem.BROWSER_EVENTS.directorychange, record);
       }
    },

    refreshDirectory : function()
    {
        if (!this.currentDirectory)
            return;
        this.fileSystem.uncacheListing(this.currentDirectory);
        this.changeDirectory(this.currentDirectory, true);
    },

    refreshFolderTree : function(path)
    {
        path = path || this.currentDirectory.data.path
        var record = this.fileSystem.recordFromCache(path);
        if (record && record.treeNode) {
            if(record.treeNode.ui) {
                record.treeNode.reload();
            }
            else {

            }
        }
        else if (path == this.fileSystem.rootPath)
            this.root.reload();
        else {
            this.fileSystem.listFiles({
                path: path
            });
            this.root.reload();  //hack??
        }
    },

    selectFile : function(record)
    {
        if (typeof record == "string")
        {
            var path = record;
            record = this.fileSystem.recordFromCache(path);
            if (!record)
            {
                var parent = this.fileSystem.getParentPath(path);
                this.fileSystem.listFiles({
                    path: parent,
                    success: function(filesystem, parentPath, records)
                    {
                        record = this.fileSystem.recordFromCache(path);
                        if (record)
                            this.selectFile(record);
                    },
                    failure: LABKEY.Utils.displayAjaxErrorResponse,
                    scope: this
                });
            }
        }
        if (this.selectedRecord && record && this.selectedRecord.data.path == record.data.path)
            return;
        if (!this.selectedRecord && !record)
            return;
        this.selectedRecord = record || this.currentDirectory;
        this.fireEvent(LABKEY.FileSystem.BROWSER_EVENTS.selectionchange, record);
    },

    //
    // event handlers
    //

    Grid_onRowselect : function(sm, rowIdx, record)
    {
        if (this.tree)
            this.tree.getSelectionModel().clearSelections();
        if (record)
            this.selectFile(record);
    },

    // UNDONE: multiselect?
    Grid_onSelectionChange : function(event)
    {
        this.fireEvent(LABKEY.FileSystem.BROWSER_EVENTS.selectionchange, (event.selections.getCount() == 0 ? null : event.selections.itemAt(0)));
    },

    Grid_onKeypress : function(e)
    {
        switch (e.keyCode)
        {
            case e.ENTER:
                var record = this.selectedRecord;
                if (record && !record.data.file)
                    this.changeDirectory(record);
                this.grid.focus();
                break;
            case e.ESC:
                this.grid.getSelectionModel().clearSelections();
            default:
                break;
        }
    },

    Grid_onCelldblclick : function(grid, rowIndex, columnIndex, e)
    {
        var record = grid.getStore().getAt(rowIndex);
        this.selectFile(record);
        if (!record.data.file)
        {
            this.changeDirectory(record);
        }
        this.grid.focus();
        this.fireEvent(LABKEY.FileSystem.BROWSER_EVENTS.doubleclick, record);
    },

    Tree_onSelectionchange : function(sm, node)
    {
        if (node)
        {
            if (this.grid)
                this.grid.getSelectionModel().clearSelections();
            this.selectFile(node.record);
            this.changeDirectory(node.record);
        }
    },

    getHistory : function(path)
    {
        this.fileSystem.getHistory({
            path: path,
            success: this.history.createDelegate(this)
        });
    },

    updateAddressBar : function(path)
    {
        var i;
        var el = Ext.get('addressBar');
        if (!el)
            return;
        var elStyle = el.dom.style;
        elStyle.backgroundColor = "#f0f0f0";
        elStyle.height = "100%";
        elStyle.width = "100%";

        var parts = [];
        while (path)
        {
            var name = this.fileSystem.getFileName(path);
            if (!name)
                break;
            if (name == '/')
                name = this.fileSystem.rootName || '/';
            parts.push({path:path, name:name, id:Ext.id(null,'name'), idImg:Ext.id(null,'img')});
            var parent = this.fileSystem.getParentPath(path);
            if (parent == path)
                break;
            path = parent;
        }
        parts = parts.reverse();

        var html = [];
        html.push('<table><tr>');
        for (i=0 ; i<parts.length ; i++)
        {
            html.push('<td id="' + parts[i].idImg + '">');
            html.push('<img src="' + this.FOLDER_ICON + '" border=0>');
            html.push('</td><td id="' + parts[i].id + '">');
            html.push(Ext.util.Format.htmlEncode(parts[i].name));
            html.push("</td><td>&nbsp;</td>");
        }
        html.push("</tr></table>");

        el.update('<table height=100% width=100%><tr><td height=100% width=100% valign=middle align=left>' + html.join('') + '</td></tr></table>');

        for (i=0 ; i<parts.length ; i++)
        {
            var addressBarHandler = this.showFileListMenu.createDelegate(this, [parts[i]]);
            var changeDirectory = function(path)
            {
                this.hideFileListMenu();
                this.changeDirectory(path);
            }.createDelegate(this, [parts[i].path]);
            Ext.get(parts[i].id).on("click", addressBarHandler);
            Ext.get(parts[i].id).on("dblclick", changeDirectory);
            Ext.get(parts[i].idImg).on("click", addressBarHandler);
            Ext.get(parts[i].idImg).on("dblclick", changeDirectory);
        }
    },

    _filelistmenu : null,


    showFileListMenu : function(part)
    {
        this.hideFileListMenu();
        this._filelistmenu = new LABKEY.FileSystem.FileListMenu(this.fileSystem, part.path, this.fileFilter, this.changeDirectory.createDelegate(this));
        this._filelistmenu.show(Ext.get(part.idImg) || Ext.get('addressBar'));
    },


    hideFileListMenu : function()
    {
        if (this._filelistmenu)
        {
            this._filelistmenu.destroy();
            this._filelistmenu = null;
        }
    },

    updateFileDetails : function(record)
    {
        try
        {
            var el = Ext.get('file-details');
            if (!el)
                return;
            var elStyle = el.dom.style;
            elStyle.backgroundColor = "#f0f0f0";
            elStyle.height = "100%";
            elStyle.width = "100%";
            if (!record || !record.data)
            {
                el.update('&nbsp;');
                return;
            }
            var html = [];
            var data = record.data;
            var row = function(label,value)
            {
                html.push("<tr><th style='text-align:right; color:#404040'>");
                html.push(label);
                html.push(":</th><td style='padding-left: 5px'>");
                html.push(value);
                html.push("</td></tr>");
            };

            html.push("<table style='padding-left:30px;'>");
            row("Name", data.name);
            row("WebDav URL", data.uri);
            if (data.modified)
                row("Date modified", LABKEY.FileSystem.Util._longDateTime(data.modified));
            if (data.file)
                {
                    var sizeValue = Ext.util.Format.fileSize(data.size);
                    if (data.size > 1024)
                    {
                        sizeValue += " (" + LABKEY.FileSystem.Util.formatWithCommas(data.size) + " bytes)";
                    }
                    row("Size", sizeValue);
                }
            if (data.createdBy && data.createdBy != 'Guest')
                row("Created By", Ext.util.Format.htmlEncode(data.createdBy));
            if (LABKEY.FileSystem.Util.startsWith(data.contentType,"image/"))
            {
                row("Dimensions","<span id=detailsImgSize></span>");
                var image = new Image();
                image.onload = function()
                {
                    Ext.get('detailsImgSize').update(image.width + "x" + image.height);
                };
                image.src = data.uri;
            }

            html.push('</table>');
            el.update(html.join(""));
        }
        catch (x)
        {
            console.log(x);
        }
    },


    // UNDONE: move part of this into FileStore?
    loadRecords : function(filesystem, path, records)
    {
        var i, len;
        if (this.currentDirectory.data.path == path)
        {
            this.onGridDataChanged(false);
            var t = records;
            records = [];
            for (i=0 ; i < t.length ; i++)
            {
                if (!this.allowChangeDirectory && !t[i].data.file)
                    continue;

                if (this.fileFilter && !this.fileFilter.test(t[i].data))
                    continue;

                records.push(t[i]);
            }

            this.store.modified = [];
            for (i = 0, len = records.length; i < len; i++)
                records[i].join(this.store);
            this.store.data.clear();
            this.store.data.addAll(records);
            this.store.totalLength = records.length;
            this.store.applySort();
            this.store.fireEvent("datachanged", this.store);
            this.onGridDataChanged(true);
        }
        this.grid.getEl().unmask();
    },


    start : function(wd, file)
    {
        if (!this.fileSystem.ready)
        {
            this.fileSystem.onReady(this.start.createDelegate(this, [wd, file]));
            return;
        }
        if (this.tree)
        {
            this.tree.getRootNode().expand();
        }
        if (typeof wd == "string")
        {
            this.changeDirectory(wd);
            this.selectFile(file || wd);
            return;
        }
        if (this.statePrefix)
        {
            var path = Ext.state.Manager.get(this.statePrefix + ".currentDirectory");
            if (path)
            {
                this.changeDirectory(path);
                return;
            }
            // fall through
        }
        // select root
        var root = this.fileSystem.recordFromCache("/");
        this.selectFile(root);
        this.changeDirectory(root);
    },

    layoutAppletWindow : function()
    {
        var uri = new LABKEY.URI(this.fileSystem.prefixUrl);  // implementation leaking here
        var url = uri.toString();
        return;
        this.applet = new LABKEY.FileSystem.TransferApplet({id: Ext.id(), url:url, directory:this.currentDirectory.data.path, width:64, height:64});
        this.progressBar = new Ext.ProgressBar();

        var toolbar = new Ext.Toolbar({buttons:[
            this.actions.appletFileAction, this.actions.appletDirAction
        ]});
        this.appletStatusBar = new LABKEY.ext.StatusBar({defaultText:'Ready', busyText:'Copying...', items:[
            {xtype:'panel', layout:'fit', border:false, items:this.progressBar, width:120, minWidth:120}
        ]});

        this.appletWindow = new Ext.Window(
        {
            title: "Multi-file Upload",
            closable:true, animateTarget:true,
            closeAction :'hide',
            constrain:true,
            height: 200, width:240, minHeight:200, minWidth:240,
            plain: true,
            layout:'fit',
            tbar: toolbar,
            items: this.applet,
            //items:{layout:'fit', margins:'1 1 1 1', items:this.applet},
            bbar: this.appletStatusBar
        });


        this.applet.on("update", this.updateProgressBar, this);
        this.applet.on("update", this.fireUploadEvents, this);
        this.applet.getTransfers().on("update", this.updateProgressBarRecord, this);

        // make sure that the applet still matches the current directory when it appears
        this.applet.onReady(function()
        {
            this.updateAppletState(this.currentDirectory);
            var el = Ext.get('testJavaLink');
            if (el)
                el.update("");
        }, this);
    },


    updateAppletState : function(record)
    {
        if (!this.applet)
            return;

        var canWrite = this.fileSystem.ready && this.fileSystem.canWrite(record);
        var canMkdir = this.fileSystem.ready && this.fileSystem.canMkdir(record);
        if (this.actions.appletFileAction)
            this.actions.appletFileAction[canWrite?'enable':'disable']();
        if (this.actions.appletDragAndDropAction)
            this.actions.appletDragAndDropAction[canWrite?'enable':'disable']();
        if (this.actions.appletDirAction)
            this.actions.appletDirAction[canMkdir?'enable':'disable']();
        try
        {
            this.applet.changeWorkingDirectory(record.data.path);
            if (canWrite || canMkdir)
            {
                this.applet.setEnabled(true);
                this.applet.setAllowDirectoryUpload(canMkdir);
                this.applet.setText( (canMkdir ? "Drag and drop files and folders here\ndirectly from your computer" : "Drag and drop files here directly\nfrom your computer")); // + "\nFolder: " +record.data.name);
            }
            else
            {
                this.applet.setEnabled(false);
                this.applet.setText("(read-only)\nFolder: " +record.data.name);
            }
        }
        catch (e)
        {
            console.error(e);
        }
    },

    initComponent : function()
    {
        Ext.useShims = true;     // so floating elements can appear over the applet drop target

        // create the set of actions and initialize any configurable state
        this.actions = this.createActions();
        this.initializeActions();

        //delete this.actions;  // so superclass.constructor doesn't overwrite this.actions

        //
        // GRID
        //

        this.store = new LABKEY.FileSystem.FileStore({recordType:this.fileSystem.FileRecord});
        this.grid = this.createGrid();

        this.grid.getSelectionModel().on("rowselect", this.Grid_onRowselect, this);
        this.grid.getSelectionModel().on("selectionchange", this.Grid_onSelectionChange, this);
        this.grid.on("celldblclick", this.Grid_onCelldblclick, this);
        this.grid.on("keypress", this.Grid_onKeypress, this);

        // this is kind of nasty, if the column model changes, we need to re-hook all the listeners for the selection
        // model because in the process, all listeners for each column get purged
        this.grid.getColumnModel().on('configchange', function()
        {
            this.grid.getSelectionModel().on("rowselect", this.Grid_onRowselect, this);
            this.grid.getSelectionModel().on("selectionchange", this.Grid_onSelectionChange, this);
        }, this);

        //
        // TREE
        //

        if (this.showFolderTree)
        {
            this.tree = this.createTreePanel();
        }

        //
        // Toolbar and Actions
        //
        var tbarConfig = this.getTbarConfig();

        //
        // Layout top panel
        //

        var layoutItems = this.getItems();

        Ext.apply(this, {layout:'border', tbar:tbarConfig, items: layoutItems, renderTo:null}, {id:'fileBrowser', height:600, width:800});

        LABKEY.ext.FileBrowser.superclass.initComponent.call(this);

        //
        // EVENTS (tie together components)
        //
        this.addEvents( [ LABKEY.FileSystem.BROWSER_EVENTS.selectionchange, LABKEY.FileSystem.BROWSER_EVENTS.directorychange, LABKEY.FileSystem.BROWSER_EVENTS.doubleclick, LABKEY.FileSystem.BROWSER_EVENTS.transferstarted, LABKEY.FileSystem.BROWSER_EVENTS.transfercomplete ]);


        this.on(LABKEY.FileSystem.BROWSER_EVENTS.selectionchange, function()
        {
            this.history = null;
            if (this.actions.showHistory)
                this.actions.showHistory.disable();

            var selections = this.grid.selModel.getSelections();

            var record;
            if (selections.length > 0 && !record)
            {
                record = selections[0];
            }

            this.updateFileDetails(record);

            if (selections.length == 1 && LABKEY.FileSystem.Util.startsWith(record.data.uri,"http"))
            {
                this.actions.download.enable();
            }
            else if (selections.length > 0 && LABKEY.FileSystem.Util.startsWith(this.currentDirectory.data.uri,"http"))
            {
                this.actions.download.enable();
            }
            else
            {
                this.actions.download.disable();
            }

            if (record && this.fileSystem.canMove(record))
            {
                this.actions.renamePath.setDisabled(selections.length != 1);
                this.actions.movePath.enable();
            }
            else
            {
                this.actions.renamePath.disable();
                this.actions.movePath.disable();
            }

            if (record && this.fileSystem.canDelete(record))
                this.actions.deletePath.enable();
            else
                this.actions.deletePath.disable();
        }, this);


        // actions
        this.on(LABKEY.FileSystem.BROWSER_EVENTS.directorychange, function(record)
        {
            if (this.actions.parentFolder)
            {
                if (record && record.data && record.data.path != this.fileSystem.rootPath)
                    this.actions.parentFolder.enable();
                else
                    this.actions.parentFolder.disable();
            }

            var dav = this.fileSystem.ready && this.fileSystem.prefixUrl;
            var canWrite = dav && this.fileSystem.canWrite(record);
            var canMkdir = dav && this.fileSystem.canMkdir(record);

            this.actions.upload[canWrite?'enable':'disable']();
            this.actions.uploadTool[canWrite?'enable':'disable']();
            this.actions.createDirectory[canMkdir?'enable':'disable']();
        }, this);


        // main ui
        this.on(LABKEY.FileSystem.BROWSER_EVENTS.directorychange,function(record)
        {
            // data store
            this.store.removeAll();
            this.grid.getEl().mask("loading...", "x-mask-loading");
            this.grid.view.scrollToTop();
            this.fileSystem.listFiles({
                path: record.data.path,
                success: this.loadRecords,
                failure: LABKEY.Utils.displayAjaxErrorResponse,
                scope: this
            });

            // address bar
            this.updateAddressBar(record.data.path);

            // expand tree
            if (this.tree)
            {
                var treePath = this.tree.loader.treePathFromPath(record.data.path, this.tree.getRootNode().id);
                this.tree.expandPath(treePath, undefined, (function(success,node)
                {
                    if (node)
                    {
                        if (node != this.tree.root)
                        {
                            try { node.ensureVisible(); } catch (x) { }
                        }
                        if (node.id == record.data.path)
                            node.select();
                    }
                }).createDelegate(this));
            }
//            this.grid.getEl().unmask();
        }, this);


        // applet
        this.on(LABKEY.FileSystem.BROWSER_EVENTS.directorychange, this.updateAppletState, this);

        this.on(LABKEY.FileSystem.BROWSER_EVENTS.transfercomplete, function(result) {
            this.hideProgressBar();
            this.refreshDirectory();
            this.refreshFolderTree();
        }, this);

        this.on(LABKEY.FileSystem.BROWSER_EVENTS.transferstarted, function(result) {this.showProgressBar();}, this);
        this.fileReplaceTemplate = new Ext.XTemplate('The following files(s) or folders already exist, and will be overwritten. Do you want to replace them?<br><br>',
                '<tpl for="files">' +
                    '<tpl if="xindex &lt; 11">' +
                        '<span style="margin-left:8px;">{name}</span><br>' +
                    '</tpl>' +
                    '<tpl if="xindex == 11">' +
                        '<span style="margin-left:8px;">... too many to display</span><br>' +
                    '</tpl>' +
                '</tpl>').compile();

        var deletesInProgress = 0;

        this.on(LABKEY.FileSystem.BROWSER_EVENTS.deletestarted, function(result){
            this.actions.deletePath.setIconClass("iconAjaxLoadingRed");
            deletesInProgress ++;
        }, this);

        this.on(LABKEY.FileSystem.BROWSER_EVENTS.deletecomplete, function(result){
            if ( -- deletesInProgress < 1)
                this.actions.deletePath.setIconClass("iconDelete");
        }, this);
    },

    showProgressBar : function()
    {
        if (this.appletStatusBar)
            this.appletStatusBar.setVisible(true);
    },

    hideProgressBar : function()
    {
        if (this.progressBar)
            this.progressBar.reset();
        if (this.appletStatusBar)
            this.appletStatusBar.setVisible(false);
    },

    onRender : function()
    {
        LABKEY.ext.FileBrowser.superclass.onRender.apply(this, arguments);

        if (this.resizable)
        {
            this.resizer = new Ext.Resizable(this.el, {pinned: false});
            this.resizer.on("resize", function(o, width, height){
                LABKEY.ext.Utils.resizeToViewport(this, width, height);
            }, this);
        }
    },

    getTbarConfig : function()
    {
        var items = [];
        var tbarConfig = {enableOverflow: true, items: items};
        this.uploadEnabled = false;

        if (!this.tbarItems)
        {
            // UNDONE: need default ordering
            if (!this.allowChangeDirectory)
            {
                delete this.actions.createDirectory;
                delete this.actions.parentFolder;
            }
            for (var a in this.actions)
                if (this.actions[a] && this.actions[a].isAction)
                    items.push(this.actions[a]);
        }
        else
        {
            for (var i=0 ; i<this.tbarItems.length ; i++)
            {
                var item = this.tbarItems[i];

                if (item == 'upload')
                    this.uploadEnabled = true;

                if (typeof item == "string" && typeof this.actions[item] == "object")
                    items.push(this.actions[item]);
                else
                    items.push(item);
            }
        }
        return tbarConfig;
    },

    /**
     * Create the set of available actions
     */
    createActions : function()
    {
        var actions = Ext.apply({}, this.actions, {
            parentFolder: this.getParentFolderAction(),
            refresh: this.getRefreshAction(),
            createDirectory: this.getCreateDirectoryAction(),
            download: this.getDownloadAction(),
            deletePath: this.getDeleteAction(),
            renamePath: this.getRenameAction(),
            movePath: this.getMoveAction(),
            help: this.getHelpAction(),
            showHistory : this.getShowHistoryAction(),
            uploadTool: this.getUploadToolAction()
        });

        actions.upload = new Ext.Action({
            text: 'Upload Files',
            enableToggle: true,
            pressed: this.expandFileUpload,
            iconCls: 'iconUpload',
            disabledClass:'x-button-disabled',
            tooltip: 'Upload files or folders from your local machine to the server',
            listeners: {click:function(button, event) {this.fileUploadPanel.toggleCollapse();}, scope:this}
        });

        actions.appletFileAction = new Ext.Action({
            text    : '&nbsp;&nbsp;&nbsp;&nbsp;Choose File&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;', scope:this, disabled:false,
            cls     : 'applet-button',
            handler : function() {
                if (this.applet) {
                    var a = this.applet.getApplet();
                    if (a) a.showFileChooser();
                }
            }
        });

        actions.appletDirAction = new Ext.Action({
            text    : '&nbsp;Choose Folder&nbsp;', scope:this, disabled:false,
            cls     : 'applet-button',
            handler : function() {
                if (this.applet) {
                    var a = this.applet.getApplet();
                    if (a) a.showDirectoryChooser();
                }
            }
        });

        actions.appletDragAndDropAction = new Ext.Action({
            text    : 'Drag and Drop&nbsp;', scope:this, disabled:false,
            cls     : 'applet-button',
            handler : function() {
                if (this.applet) {
                    var a = this.applet.getApplet();
                    if (a) a.openDragAndDropWindow();
                }
            }
        });

        actions.folderTreeToggle = new Ext.Action({
            text: 'Toggle Folder Tree',
            enableToggle: true,
            iconCls: 'iconFolderTree',
            disabledClass:'x-button-disabled',
            tooltip: 'Show or hide the folder tree',
            listeners: {click:function(button, event) {
                if (this.tree) {
                    if (this.tree.isVisible()) this.tree.collapse();
                    else                       this.tree.expand();
                }
            }, scope:this},
            hideText: true
        })

        return actions;
    },
    
    /**
     * Initialize additional actions and components
     */
    initializeActions : function()
    {
        for (var a in this.actions)
        {
            if (this.actions[a] && this.actions[a].isAction)
            {
                var action = this.actions[a];

                action.initialConfig.prevText = action.initialConfig.text;
                action.initialConfig.prevIconCls = action.initialConfig.iconCls;

                if (action.initialConfig.hideText)
                    action.setText(undefined);

                if (action.initialConfig.hideIcon)
                    action.setIconClass(undefined);
            }
        }
    },

    /**
     * Returns the array of items contained by this panel.
     */
    getItems : function()
    {
        var layoutItems = [];

        if (this.showFileUpload)
        {
            // the file upload collapsible panel
            this.fileUploadField = new Ext.form.FileUploadField({
                buttonText: "Browse",
                fieldLabel: 'Choose a File',
                width: 350
            });

            // the description field is hidden by default until a file is selected
            this.descriptionField = new Ext.form.TextField({
                name       : 'description',
                disabled   : true,
                fieldLabel : 'Description',
                width      : 290,
                listeners  : { render : function(field) { field.label.addClass('labkey-disabled'); } }
            });

            // disable the upload button until a file is selected
            var btn = new Ext.Button({
                text     : 'Upload',
                disabled : true,
                handler  : this.submitFileUploadForm,
                scope    : this
            });

            this.fileUploadField.on('fileselected', function(){
                if (btn)
                    btn.enable();
                this.descriptionField.label.removeClass('labkey-disabled');
                this.descriptionField.setDisabled(false);
            }, this);

            this.uploadPanel = new Ext.FormPanel({
                method : 'POST',
                fileUpload: true,
                enctype:'multipart/form-data',
                cls   : 'single-upload-panel',
                border:false,
                stateful: false,
                bodyStyle : 'background-color:#f0f0f0;',
                buttonAlign: 'left',
                items: [{xtype:'compositefield', items:[this.fileUploadField, btn]},
                    this.descriptionField
                ],
                listeners: {
                    "actioncomplete" : {fn: this.uploadSuccess, scope: this},
                    "actionfailed"   : {fn: this.uploadFailed,  scope: this}
                }
            });

            var loadingImageSrc = LABKEY.contextPath + "/" + LABKEY.extJsRoot + "/resources/images/default/shared/large-loading.gif";

            this.progressBar = new Ext.ProgressBar();
            this.appletStatusBar = new LABKEY.ext.StatusBar({
                defaultText:'', busyText:'Copying...',
                width: 400,
                hidden: true,
                statusAlign: 'right',
                style : 'background-color:#f0f0f0;',
                items:[{
                    xtype:'panel', layout:'fit', border:false, items:this.progressBar, width:250, minWidth:250
                }]
            });

            var qtipHtml =  '<span id="testJavaLink"><br>[<a target=_blank href="http://www.java.com/en/download/testjava.jsp">test java plugin</a>]</span>';

            this.appletPanel = new Ext.Panel({
                fieldLabel  : 'Upload Tool' + qtipHtml,
                isFormField : true,
                height      : 85,
                width       : 85,
                items       : {html:'<img src="' + loadingImageSrc + '"><br>loading Java applet...'}
            });

            this.uploadMultiPanel = new Ext.FormPanel({
                border:false,
                stateful: false,
                buttonAlign: 'left',
                items: [
                    {xtype:'compositefield',items:[
                        this.appletPanel,
                        new Ext.Panel({border:false, bodyStyle : 'background-color:#f0f0f0', items:[
                            new Ext.Button(this.actions.appletFileAction),
                            new Ext.Button(this.actions.appletDirAction),
                            new Ext.Button(this.actions.appletDragAndDropAction)]})]}
                ]
            });
            this.uploadMultiPanel.doLayout();

            // default settings
            var defaults = {
                labelWidth: 110,
                labelAlign: 'right',
                labelPad: 15,
                bodyStyle : 'background-color:#f0f0f0'
            };

            var baseHeight = 100;

            // panel to contain the 2 upload panels
            var uploadPanelOuter = new Ext.Panel({
                width : 550,
                border: false,
                flex  : 2,
                layout: 'card',
                height: baseHeight,
                deferredRender: true,
                defaults: defaults,
                activeItem: this.uploadPanel.getId(),
                items: [this.uploadPanel, this.uploadMultiPanel]
            });

            // main panel with the radio button selectors and the upload panel
            var panel = new Ext.Panel({
                border  : false,
                layout  : 'table',
                columns : 2,
                height  : baseHeight,
                items   : [{
                    xtype : 'panel',
                    layout: 'form',
                    height  : baseHeight * .9,
                    flex    : 1,
                    buttonAlign : 'left',
                    bodyStyle : 'background-color:#f0f0f0; padding-left: 25px;',
                    border : false,
                    items : [{
                        xtype      : 'radiogroup',
                        width      : 110,
                        columns    : 1,
                        fieldLabel : 'Upload Type',
                        hideLabel  : true,
                        items      : [{
                            boxLabel : 'Single&nbsp;file', name: 'rb-file-upload-type', checked: true,
                            handler  : function(cmp, checked) {
                                if (checked) {
                                    this.applet.setEnabled(false);
                                    uploadPanelOuter.getLayout().setActiveItem(this.uploadPanel.getId());
                                }
                            }, scope:this
                        },{
                            boxLabel : 'Multiple&nbsp;files', name: 'rb-file-upload-type',
                            handler  : function(cmp, checked) {
                                if (checked) {
                                    uploadPanelOuter.getLayout().setActiveItem(this.uploadMultiPanel.getId());
                                    this.onMultipleFileUpload();
                                }
                            }, scope: this
                        }]
                    }]
                },uploadPanelOuter]
            });

            this.fileUploadPanel = new Ext.Panel({
                region: 'north',
                collapseMode: 'mini',
                height: baseHeight + 20,
                defaults: defaults,
                header: false,
                margins:'0 0 0 0',
                border: false,
                bodyStyle: 'background-color:#f0f0f0;',
                cmargins:'0 0 0 0',
                collapsible: true,
                hidden: !this.uploadEnabled,
                collapsed: !this.expandFileUpload || !this.uploadEnabled,
                hideCollapseTool: true,
                deferredRender: false,
                items: [panel],
                tbar: {
                    height: 25,
                    style:{backgroundColor :'#f0f0f0'},
                    items:[ this.appletStatusBar,
                            '->',{iconCls:'iconClose', tooltip:'Close the file upload panel', scope: this,
                            handler: function(){
                                this.fileUploadPanel.collapse();
                                this.actions.upload.each(function(cmp){
                                    cmp.toggle(false);                                    
                                });
                            }}
                    ]
                }
            });

            // clean up and initialize the transfer applet when the panel is collapsed/expanded
            this.fileUploadPanel.on('beforecollapse', function(cmp)
            {
                if (this.applet)
                {
                    this.applet.destroy();
                    this.applet = null;
                }
            }, this);
            this.fileUploadPanel.on('expand', function(cmp)
            {
                if (uploadPanelOuter.getLayout().activeItem.getId() == this.uploadMultiPanel.getId())
                    this.onMultipleFileUpload();
                panel.doLayout();
            }, this);

            layoutItems.push(this.fileUploadPanel);
        }

        var addressBar = {
            region: 'south',
            height: 24,
            margins: '5',
            layout: 'fit',
            border: true,
            stateful: false,
            items: [{id:'addressBar', html: '<div style="background-color:#f0f0f0;height:100%;width:100%">&nbsp</div>'}]
        };

        if (this.showDetails)
        {
            var detailItems = [];
            var panelHeight = 80;
            var layout = 'fit';

            if (this.showAddressBar)
            {
                addressBar.margins = '0 -1 -1 -1';
                layout = 'border';
                detailItems.push({height: 80, region: 'center', html:'<div style="background-color:#f0f0f0;height:100%;width:100%">&nbsp</div>', id:'file-details'});
                detailItems.push(addressBar);

                panelHeight += 24;
            }
            else
                detailItems.push({html:'<div style="background-color:#f0f0f0;height:100%;width:100%">&nbsp</div>', id:'file-details'});

            layoutItems.push(
            {
                region: 'south',
                split: true,
                height: panelHeight,
                minSize: 0,
                maxSize: 250,
                margins: '0 5 5 5',
                layout: layout,
                items: detailItems
            });
        }
        else if (this.showAddressBar)
        {
            layoutItems.push(addressBar);
        }

        if (this.showFolderTree)
        {
            this.tree.region = 'west';
            this.tree.split  = true;
            this.tree.width  = 200;
            layoutItems.push(this.tree);
        }

        var centerItems = [];

        centerItems.push({region:'center', layout:'fit', border:false, items:[this.grid]});
        layoutItems.push({
            region  : 'center',
            margins : '0 ' + (this.propertiesPanel ? '0' : '5') + ' ' + (this.showDetails ? '0' : '0') + ' 0',
            minSize : 200,
            layout  : 'border',
            items   : centerItems
        });

        if (this.propertiesPanel)
        {
            var panels = !'length' in this.propertiesPanel ? [this.propertiesPanel] : this.propertiesPanel;
            layoutItems.push(
            {
                region:'east',
                split:true,
                margins: this.showDetails ? '5 5 0 0' : '5 5 5 0',
                width: 200,
                minSize: 100,
                border: false,
                layout: 'accordion',
                items: panels
            });
        }
        return layoutItems;
    },

    submitFileUploadForm : function(fb, v)
    {
        if (this.currentDirectory)
        {
            var form = this.uploadPanel.getForm();
            var path = this.fileUploadField.getValue();
            var i = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
            var name = path.substring(i+1);
            if (name.length == 0) {
                Ext.MessageBox.alert('Error', 'No file selected. Please choose one or more files to upload.');
                return;
            }
            var target = this.fileSystem.concatPaths(this.currentDirectory.data.path,name);
            var file = this.fileSystem.recordFromCache(target);

            this.doPost = function(overwrite) {
                var options = {method:'POST',
                    // success response is same as PROPFIND, error response is JSON
                    url: this.currentDirectory.data.uri + '?Accept=application/json' + (overwrite ? '&overwrite=t' : ''),
                    record:this.currentDirectory,
                    name:this.fileUploadField.getValue(),
                    failure: LABKEY.Utils.displayAjaxErrorResponse
                };
                // set errorReader, so that handleResponse() doesn't try to eval() the XML response
                // assume that we've got a WebdavFileSystem
                form.errorReader = this.fileSystem.transferReader;
                form.doAction(new Ext.form.Action.Submit(form, options));
                this.fireEvent(LABKEY.FileSystem.BROWSER_EVENTS.transferstarted, {uploadType:"webform", files:[{name:name, id:new LABKEY.URI(this.fileSystem.concatPaths(options.url, encodeURIComponent(options.name))).pathname}]});
                this.fileUploadPanel.getEl().mask("Uploading " + name + '...');
            };

            if (file)
            {
                Ext.MessageBox.confirm('File: ' + name + ' already exists', 'There is already a file with the same name in this location, do you want to replace it? ', function(btn){
                    if (btn == 'yes')
                        this.doPost(true);
                }, this);
            }
            else
                this.doPost(false);
        }
    },

    // gets just the name portion of a path (if any)
    getName : function(path)
    {
        var i = path.lastIndexOf("\\");
        if (i > -1)
            path = path.substring(i+1);
        else if (!Ext.isWindows)
        {
            // try both types of separators on non-windows systems (issue: 13516)
            i = path.lastIndexOf("/");
            if (i > -1)
                path = path.substring(i+1);
        }

        return path;
    },

    // handler for a file upload complete event
    uploadSuccess : function(f, action)
    {
        var txt = (action.response.responseText || "").trim();
        var json = {success : true};
        if (txt && txt.charAt(0) == '{' )
            json = Ext.util.JSON.decode(txt);

        if (!json.success)
        {
            this.uploadFailed(f, action, json.exception);
            return;
        }

        this.fileUploadPanel.getEl().unmask();
        var form = this.uploadPanel.getForm();
        if (form)
            form.reset();
//        console.log("upload actioncomplete");
//        console.log(action);
        var options = action.options;
        // UNDONE: update data store directly
        //this.toggleTabPanel();
        this.refreshDirectory();
        var name = this.getName(options.name);

        this.selectFile(this.fileSystem.concatPaths(options.record.data.path, name));
        this.fireEvent(LABKEY.FileSystem.BROWSER_EVENTS.transfercomplete, {uploadType:"webform", files:[{name:name, id:new LABKEY.URI(this.fileSystem.concatPaths(options.record.data.uri, encodeURIComponent(name))).pathname}]});
    },

    // handler for a file upload failed event
    uploadFailed : function(f, action, message)
    {
        this.fileUploadPanel.getEl().unmask();
        var form = this.uploadPanel.getForm();
        if (form)
            form.reset();
        var message = message || "File upload failed.";
        Ext4.Msg.alert("Error", Ext4.htmlEncode(message).replace("\n","<br>"));
        console.log(message);
        console.log(action);
        this.refreshDirectory();
    },

    onMultipleFileUpload : function()
    {
        this.hideProgressBar();
        this.lastSummary= {info:0, success:0, file:'', pct:0};
        this.progressRecord = null;
        if (!this.applet)
        {
            var uri = new LABKEY.URI(this.fileSystem.prefixUrl);  // implementation leaking here
            var url = uri.toString();
            this.applet = new LABKEY.FileSystem.TransferApplet({id: Ext.id(), url:url, directory:this.currentDirectory.data.path, text:'initializing...', width:80, height:80});
            this.applet.on("update", this.updateProgressBar, this);
            this.applet.on("update", this.fireUploadEvents, this);
            this.applet.getTransfers().on("update", this.updateProgressBarRecord, this);

            // make sure that the applet still matches the current directory when it appears
            this.applet.onReady(function() {
                this.updateAppletState(this.currentDirectory);
                var el = Ext.get('testJavaLink');
                if (el)
                    el.update("");
            }, this);

            this.appletPanel.removeAll();
            this.appletPanel.add(this.applet);
            this.appletPanel.doLayout();
        }
        else
        {
            var task = {
                interval : 100,
                scope    : this,
                run      : function() {
                    if (this.applet.isActive())
                    {
                        this.updateAppletState(this.currentDirectory);
                        this.applet.getTransfers().removeAll();

                        return false;
                    }
                }
            };
            Ext.TaskMgr.start(task);
        }
    },

    createTreePanel : function()
    {
        var treeloader = new LABKEY.FileSystem.TreeLoader({fileSystem: this.fileSystem, displayFiles:false, folderFilter:this.fileFilter});
        this.root = treeloader.createNodeFromRecord(this.fileSystem.rootRecord, this.fileSystem.rootPath);
        var tree = new Ext.tree.TreePanel({
            loader          : treeloader,
            root            : this.root,
            rootVisible     : true,
            useArrows       : true,
            margins         : (this.showDetails || this.showAddressBar) ? '0 0 0 5' : '0 0 5 5',
            autoScroll      : true,
            animate         : true,
            enableDD        : false,
            containerScroll : true,
            collapsible     : true,
            collapseMode    : 'mini',
            collapsed       : this.folderTreeCollapsed,
            cmargins        :'0 0 0 0',
            border          : true,
            stateful        : false,
            pathSeparator   : ';'
        });
        treeloader.debugTree = tree;
        tree.getSelectionModel().on("selectionchange", this.Tree_onSelectionchange, this);
        return tree;
    },

    attachPreview : function(id, record) {
        if (this && !this.canRead(record))
            return;
        if (!record.data.file)
            return;
        var img = Ext.fly(id,"previewAncor");
        if (!img) return;
        var preview = new LABKEY.FileSystem.PreviewResource({title:id, target:id, record:record});
        if (!this.allPreviews)
            this.allPreviews = [];
        this.allPreviews.push(preview);
    },

    destroyPreviews : function()
    {
        Ext.destroy.apply(null, this.allPreviews);
        this.allPreviews = [];
    },

    createGrid : function()
    {
        // mild convolution to pass fileSystem to the _attachPreview function
        var iconRenderer = LABKEY.FileSystem.Util.renderIcon.createDelegate(null,this.attachPreview.createDelegate(this.fileSystem,[],true),true);

        var grid = new Ext.grid.GridPanel({
            store    : this.store,
            cls      : 'labkey-filecontent-grid',
            border   : false,
            selModel : new Ext.grid.RowSelectionModel({singleSelect:true}),
            loadMask : {msg:"Loading, please wait..."},
            columns  : [
                {header: "",              width: 20,  dataIndex: 'iconHref',  sortable: false, hidden: false, renderer: iconRenderer},
                {header: "Name",          width: 250, dataIndex: 'name',      sortable: true,  hidden: false, renderer: Ext.util.Format.htmlEncode},
                {header: "Last Modified", width: 150, dataIndex: 'modified',  sortable: true,  hidden: false, renderer: LABKEY.FileSystem.Util.renderDateTime},
                {header: "Size",          width: 80,  dataIndex: 'size',      sortable: true,  hidden: false, renderer: LABKEY.FileSystem.Util.renderFileSize, align:'right'},
                {header: "Created By",    width: 100, dataIndex: 'createdBy', sortable: true,  hidden: false, renderer: Ext.util.Format.htmlEncode}
            ]
        });
        return grid;
    }
});

// PREVIEW
//
LABKEY.FileSystem.PreviewResource = Ext.extend(LABKEY.ext.PersistentToolTip, {

    baseCls    : 'x-panel',
    minWidth   : 40,
    maxWidth   : 800,
    frame      : true,
    connection : new Ext.data.Connection({autoAbort:true, method:'GET', disableCaching:false}),

    // we're not really ready to show anything, we have to get the resource still
    show : function ()
    {
        this.showAt(this.getTargetXY());
    },

    // we're not really ready to show anything, we have to get the resource still
    showAt : function(xy)
    {
        this.showAt_xy = xy;
        this.loadResource();
    },

    previewAt : function(xy)
    {
        LABKEY.FileSystem.PreviewResource.superclass.showAt.call(this, xy);
    },

    onRender : function(ct, position)
    {
        this.title = false;
        LABKEY.FileSystem.PreviewResource.superclass.onRender.call(this, ct, position);
        this.body.update(Ext.DomHelper.markup(this.html));
    },

    loadResource : function()
    {
        var record = this.record;
        var name = record.data.name;
        var uri = record.data.uri;
        var contentType = record.data.contentType;
        var size = record.data.size;

        if (!uri || !contentType || !size)
            return;

        if (LABKEY.FileSystem.Util.startsWith(contentType,'image/'))
        {
            var image = new Image();
            image.onload = (function()
            {
                var img = {tag:'img', src:uri, border:'0', width:image.width, height:image.height};
                this.constrain(img, 400, 400);
                this.html = img;
                this.previewAt(this.showAt_xy);
            }).createDelegate(this);
            image.src = uri;
        }
        //IFRAME
        else if (contentType == 'text/html')
        {
            var base = uri.substr(0,uri.lastIndexOf('/')+1)
            var headers = {};
            var requestid = this.connection.request({
                autoAbort:true,
                url:uri,
                headers:headers,
                method:'GET',
                disableCaching:false,
                success : (function(response)
                {
                    var contentType = response.getResponseHeader("Content-Type") || "text/html";
                    if (LABKEY.FileSystem.Util.startsWith(contentType,"text/"))
                    {
                        var id = 'iframePreview' + (++Ext.Component.AUTO_ID);
                        var body = response.responseText;
                        body = Ext.util.Format.stripScripts(body);
                        this.html = {tag:'iframe', id:id, name:id, width:600, height:400, frameborder:'no', src:(Ext.isIE ? Ext.SSL_SECURE_URL : "javascript:;")};
                        this.previewAt(this.showAt_xy);
                        var frame = Ext.getDom(id);
                        if (!frame)
                        {
                            this.hide();
                        }
                        else
                        {
                            var doc = Ext.isIE ? frame.contentWindow.document : frame.contentDocument || window.frames[id].document;
                            doc.open();
                            if (base)
                                body = '<base href="' + Ext.util.Format.htmlEncode(base) + '" />' + body;
                            doc.write(body);
                            doc.close();
                        }
                    }
                }).createDelegate(this)
            });
        }
        // DIV
        else if (LABKEY.FileSystem.Util.startsWith(contentType,'text/') || contentType == 'application/javascript' || LABKEY.FileSystem.Util.endsWith(name,".log"))
        {
            var headers = {};
            if (contentType != 'text/html' && size > 10000)
                headers['Range'] = 'bytes 0-10000';
            var requestid = this.connection.request({
                autoAbort:true,
                url:uri,
                headers:headers,
                method:'GET',
                disableCaching:false,
                success : (function(response)
                {
                    var contentType = response.getResponseHeader("Content-Type") || "text/plain";
                    if (LABKEY.FileSystem.Util.startsWith(contentType,"text/"))
                    {
                        var text = response.responseText;
                        if (headers['Range']) text += "\n. . .";
                        this.html = {tag:'div', style:{width:'600px', height:'400px', overflow:'auto'}, children:{tag:'pre', children:Ext.util.Format.htmlEncode(text)}};
                        this.previewAt(this.showAt_xy);
                    }
                }).createDelegate(this)
            });
        }
    },

    constrain : function(img,w,h)
    {
        var X = img.width;
        var Y = img.height;
        if (X > w)
        {
            img.width = w;
            img.height = Math.round(Y * (1.0*w/X));
        }
        X = img.width;
        Y = img.height;
        if (Y > h)
        {
            img.height = h;
            img.width = Math.round(X * (1.0*h/Y));
        }
    }
});

LABKEY.FileSystem.FileListMenu = Ext.extend(Ext.menu.Menu, {
    constructor: function(fileSystem, path, folderFilter, fn)
    {
        LABKEY.FileSystem.FileListMenu.superclass.constructor.call(this, {items:[], cls:'extContainer'});
        this.showFiles = false;
        this.fileSystem = fileSystem;
        this.path = path;
        this.folderFilter = folderFilter;

        var records = fileSystem.directoryFromCache(path);
        var populate = function(filesystem, success, path, records)
            {
                for (var i=0 ; i<records.length ; i++)
                {
                    var record = records[i];
                    var data = record.data;
                    if (!this.showFiles && data.file)
                        continue;

                    else if (this.folderFilter && !this.folderFilter.test(data))
                        continue;

                    this.addMenuItem(new Ext.menu.Item({text:data.name, icon:data.iconHref, path:record.data.path}));
                }
            };
        if (records)
            populate.call(this, null, true, path, records);
        else
            fileSystem.listFiles({
                path: path,
                success: populate,
                scope: this
            });
        if (typeof fn == "function")
        {
            this.on("click", function(menu,item,event)
            {
                var path = item.initialConfig.path;
                fn(path);
            });
        }
    }
});

LABKEY.FileSystem.FileStore = Ext.extend(Ext.data.Store, {
    constructor : function(config)
    {
        LABKEY.FileSystem.FileStore.superclass.constructor.call(this,config);
        this.setDefaultSort("name","ASC");
    },

    sortData : function()
    {
        this.sortInfo.direction = this.sortInfo.direction || 'ASC';
        var f = this.sortInfo.field;
        var st = this.fields.get(f).sortType;
        var d = this.sortInfo.direction=="DESC" ? -1 : 1;
        var fn = function(r1, r2)
        {
            if (r1.data.file != r2.data.file)
                return d * (r1.data.file ? 1 : -1);
            var v1 = st(r1.data[f]), v2 = st(r2.data[f]);
            return v1 > v2 ? 1 : (v1 < v2 ? -1 : 0);
        };
        this.data.sort(this.sortInfo.direction, fn);
        if (this.snapshot && this.snapshot != this.data)
        {
            this.snapshot.sort(this.sortInfo.direction, fn);
        }
    }
});
