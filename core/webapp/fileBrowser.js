/*
 * Copyright (c) 2008-2009 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */


var $ = Ext.get;
var $h = Ext.util.Format.htmlEncode;
var $dom = Ext.DomHelper

$dom.append(document.getElementsByTagName("head")[0],"<style>.refreshIcon {background-image: url(" + LABKEY.contextPath + "/_images/reload.png)}</style>");

/*
	parseUri 1.2.1
	(c) 2007 Steven Levithan <stevenlevithan.com>
	MIT License
*/
function parseUri(str)
{
    var	o   = parseUri.options;
    var m   = o.parser[o.strictMode ? "strict" : "loose"].exec(str);
    var uri = {};
    var i   = 14;

    while (i--)
        uri[o.key[i]] = m[i] || "";

    if (!uri.protocol)
    {
        var l = window.location;
        uri.protocol = uri.protocol || l.protocol;
        uri.port = uri.port || l.port;
        uri.hostname = uri.hostname || l.hostname;
        uri.host = uri.host || l.host;
    }
    if (uri.protocol && uri.protocol.charAt(uri.protocol.length-1) == ":")
        uri.protocol = uri.protocol.substr(0,uri.protocol.length - 1);

    uri[o.q.name] = {};
    uri[o.key[12]].replace(o.q.parser, function ($0, $1, $2)
    {
        if ($1) uri[o.q.name][$1] = $2;
    });
    uri.toString = function()
    {
        return this.protocol + "://" + this.host + this.pathname + this.search;
    };
    uri.href = uri.toString();
    return uri;
}
parseUri.options =
{
	strictMode: false,
	key: ["source","protocol","host","userInfo","user","password","hostname","port","relative","pathname","directory","file","search","hash"],
	q:
    {
		name:   "query",
		parser: /(?:^|&)([^&=]*)=?([^&]*)/g
	},
	parser:
    {
		strict: /^(?:([^:\/?#]+):)?(?:\/\/((?:(([^:@]*):?([^:@]*))?@)?([^:\/?#]*)(?::(\d*))?))?((((?:[^?#\/]*\/)*)([^?#]*))(?:\?([^#]*))?(?:#(.*))?)/,
		loose:  /^(?:(?![^:@]+:[^:@\/]*@)([^:\/?#.]+):)?(?:\/\/)?((?:(([^:@]*):?([^:@]*))?@)?([^:\/?#]*)(?::(\d*))?)(((\/(?:[^?#](?![^?#\/]*\.[^?#\/.]+(?:[?#]|$)))*\/?)?([^?#\/]*))(?:\?([^#]*))?(?:#(.*))?)/
	}
};
/* end parseUri */

var TREESELECTION_EVENTS =
{
    selectionchange:"selectionchange",
    beforeselect:"beforeselect"
};
var PANEL_EVENTS =
{
	active:'activate',
	add:'add',
	afterlayout:'afterlayout',
	beforeadd:'beforeadd',
	beforeclose:'beforeclose',
	beforecollapse:'beforecollapse',
	beforedestroy:'beforedestroy',
	beforeexpand:'beforeexpand',
	beforehide:'beforehide',
	beforeremove:'beforeremove',
	beforerender:'beforerender',
	beforeshow:'beforeshow',
	beforestaterestore:'beforestaterestore',
	beforestatesave:'beforestatesave',
	bodyresize:'bodyresize',
	close:'close',
	collapse:'collapse',
	deactivate:'deactivate',
	destroy:'destroy',
	disable:'disable',
	enable:'enable',
	expand:'expand',
	hide:'hide',
	move:'move',
	remove:'remove',
	render:'render',
	resize:'resize',
	show:'show',
	staterestore:'staterestore',
	statesave:'statesave',
	titlechange:'titlechange'
};
var GRIDPANEL_EVENTS =
{
    click:"click",
    dblclick:"dblclick",
    contextmenu:"contextmenu",
    mousedown:"mousedown",
    mouseup:"mouseup",
    mouseover:"mouseover",
    mouseout:"mouseout",
    keypress:"keypress",
    keydown:"keydown",
    cellmousedown:"cellmousedown",
    rowmousedown:"rowmousedown",
    headermousedown:"headermousedown",
    cellclick:"cellclick",
    celldblclick:"celldblclick",
    rowclick:"rowclick",
    rowdblclick:"rowdblclick",
    headerclick:"headerclick",
    headerdblclick:"headerdblclick",
    rowcontextmenu:"rowcontextmenu",
    cellcontextmenu:"cellcontextmenu",
    headercontextmenu:"headercontextmenu",
    bodyscroll:"bodyscroll",
    columnresize:"columnresize",
    columnmove:"columnmove",
    sortchange:"sortchange"
};
Ext.apply(GRIDPANEL_EVENTS,PANEL_EVENTS);

var ROWSELECTION_MODEL =
{
    selectionchange:"selectionchange",
    beforerowselect:"beforerowselect",
    rowselect:"rowselect",
    rowdeselect:"rowdeselect"
};


function log(o)
{
    if ("console" in window)
        console.log(o);
}
function startsWith(s, f)
{
    var len = f.length;
    if (s.length < len) return false;
    if (len == 0)
        return true;
    return s.charAt(0) == f.charAt(0) && s.charAt(len-1) == f.charAt(len-1) && s.indexOf(f) == 0;
}
function endsWith(s, f)
{
    var len = f.length;
    var slen = s.length;
    if (slen < len) return false;
    if (len == 0)
        return true;
    return s.charAt(slen-len) == f.charAt(0) && s.charAt(slen-1) == f.charAt(len-1) && s.indexOf(f) == slen-len;
}

var imgCounter = 0;

// minor hack call with scope having decorateIcon functions
function renderIcon(value, metadata, record, rowIndex, colIndex, store, decorateFN)
{
    var file = record.get("file");
    if (!value)
    {
        if (!file)
        {
            value = LABKEY.FileBrowser.prototype.FOLDER_ICON;
        }
        else
        {
            var name = record.get("name");
            var i = name.lastIndexOf(".");
            var ext = i >= 0 ? name.substring(i) : name;
            value = LABKEY.contextPath + "/project/icon.view?name=" + ext;
        }
    }
    var img = {tag:'img', width:16, height:16, src:value, id:'img'+(++imgCounter)};
    if (decorateFN)
        decorateFN.defer(1,this,[img.id,record]);
    return $dom.markup(img);
}

function renderFileSize(value, metadata, record, rowIndex, colIndex, store)
{
    if (!record.get('file')) return "";
    var f =  Ext.util.Format.fileSize(value);
    return "<span title='" + f + "'>" + value + "</span>";
}

var _rDateTime = Ext.util.Format.dateRenderer("Y-m-d H:i:s");
var _longDateTime = Ext.util.Format.dateRenderer("l, F d, Y g:i:s A");
function renderDateTime(value, metadata, record, rowIndex, colIndex, store)
{
    if (!value) return "";
    if (value.getTime() == 0) return "";
    return "<span title='" + _longDateTime(value) + "'>" + _rDateTime(value) + "<span>";
}


//
// PREVIEW
//

var _previewConnection = new Ext.data.Connection({autoAbort:true, method:'GET', disableCaching:false});
var _previewWindow = null;
var _previewAsync = null;


function _attachPreview(id,record)
{
    if (this && !this.canRead(record))
        return;
    if (!record.data.file)
        return;
    var elImg = $(id);
    elImg.on("mouseover",preview.createCallback(elImg,record));
    elImg.on("mouseout",unpreviewWindow);
}


function preview(el, record)
{
    cancelAsyncPreview();
    closePreviewWindow();
    var timeout = previewFN.defer(200,null,[el,record]);
    _previewAsync = {timeout:timeout, cancel:function() {clearTimeout(this.timeout);}};
}


function previewFN(el, record)
{
    var uri = record.data.uri;
    var contentType = record.data.contentType;
    var size = record.data.size;
    if (!uri || !contentType || !size)
        return;

    if (startsWith(contentType,'image/'))                                                                                                        
    {
        //dynamicToolTip(el, {tag:'img', src:uri});
        //previewWindow(el, {tag:'img', src:uri});
        var image = new Image();
        image.onload = function()
        {
            var img = {tag:'img', src:uri, border:'0', width:image.width, height:image.height};
            constrain(img, 400, 400);
            previewWindow(el, img);
        };
        image.src = uri;
        _previewAsync = {image:image, cancel:function(){image.onload=null}};
    }
    else if (startsWith(contentType,'text/') || contentType == 'application/javascript' || endsWith(record.data.name,".log"))
    {
        var headers = {};
        if (contentType != 'text/html' && size > 10000)
            headers['Content-Range'] = 'bytes 0-10000/'+size;
        var requestid = _previewConnection.request({
            autoAbort:true,
            url:uri,
            headers:headers,
            method:'GET',
            disableCaching:false,
            success : function(response)
            {
                var contentType = response.getResponseHeader["Content-Type"] || "text/plain";
                var html;
                if (contentType == "text/html")
                    html = response.responseText;
                else if (startsWith(contentType,"text/") || contentType == 'application/javascript')
                    html = "<div style='width:640px;'><pre>" + $h(response.responseText) + "</pre></div>";
                previewWindow(el, html);
                if (_previewAsync && _previewAsync.requestid == requestid)
                    _previewAsync = null;
            }
        });
        _previewAsync = {requestid:requestid, cancel:function(){_previewConnection.abort(this.requestid);}};
    }
}


function dynamicToolTip(el, html)
{
    el = $(el);
    html = $dom.markup(html);
    var tt = new Ext.ToolTip({target:el, html:html, trackMouse:true});
    tt.onTargetOver(Ext.EventObject);
}


function previewWindow(el, html)
{
    el = $(el);
    html = $dom.markup(html);
    if (_previewWindow)
        _previewWindow.close();
    var xy = el.getAnchorXY('tr');
    _previewWindow = new Ext.Window({html:html, target:el, closable:false, constrain:true, x:xy[0]+2, y:xy[1]});
    _previewWindow.show();
    _previewWindow.getEl().on("mouseout",unpreviewWindow);
}


function closePreviewWindow()
{
    if (_previewWindow)
    {
        _previewWindow.close();
        _previewWindow = null;
    }
}


function cancelAsyncPreview()
{
    if (_previewAsync && typeof _previewAsync.cancel == 'function')
        _previewAsync.cancel.call(_previewAsync);
    _previewAsync = null;
}


function unpreviewWindow(e)
{
    cancelAsyncPreview();
    if (_previewWindow)
    {
        if (e  && _previewWindow.getEl().getRegion().contains(e.getPoint()))
        {
            // defer()?
        }
        else
        {
            closePreviewWindow();
        }
    }
}

function constrain(img,w,h)
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


//
// FileSystem
//

// FileRecord should look like
//      uri (string, urlencoded),
//      path (string, not encoded),
//      name (string),
//      file (bool),
//      created (date),
//      modified (date),
//      size (int),
// optional
//      createdBy(string)
//      modifiedBy(string)
//      iconHref(string)
//      contentType(string,optional)

var FILESYSTEM_EVENTS = {listfiles:"listfiles", ready:"ready"};

var FileSystem = function(config)
{
    this.directoryMap = {}; //  map<path,(time,[records])>
    this.addEvents(FILESYSTEM_EVENTS.listfiles, FILESYSTEM_EVENTS.ready);
};

Ext.extend(FileSystem, Ext.util.Observable,
{
    rootPath : "/",
    separator : "/",

    listFiles : function(path, callback)    // callback(filesystem, success, path, records)
    {
        var files = this.directoryFromCache(path);
        if (files)
        {
            if (typeof callback == "function")
                callback.defer(1, null, [this, true, path, files]);
        }
        else
        {
            var ok = this.reloadFiles(path, callback);
            if (!ok && typeof callback == "function")
                callback.defer(1, null, [this, false, path]);
        }
    },

    canRead : function(record)
    {
        return true;
    },

    canWrite: function(record)
    {
        return true;
    },
    
    canDelete : function(record)
    {
        return true;
    },

    deletePath : function(path, callback)   // callback(filesystem, success, path)
    {
        return false;
    },


    createDirectory : function(path, callback) // callback(filesystem, success, path)
    {
        callback(this, path, false);
    },


    // called by listFiles(), return false on immediate fail
    reloadFiles : function(path, callback)
    {
        return false;
    },

    getHistory : function(path, callback) // callback(filesystem, success, path, history[])
    {
    },

    // protected

    _addFiles : function(path, records)
    {
        this.directoryMap[path] = records;
        this.fireEvent(FILESYSTEM_EVENTS.listfiles, path, records);
    },

    directoryFromCache : function(path)
    {
        var files = this.directoryMap[path];
        if (!files && path && path.length>0 && path.charAt(path.length-1) == this.separator)
            path = path.substring(0,path.length-1);
        files = this.directoryMap[path];
        return files;
    },

    recordFromCache : function(path)
    {
        if (!path || path == this.rootPath)
            return this.rootRecord;
        var parent = this.parentPath(path) || this.rootPath;
        var name = this.fileName(path);
        var files = this.directoryFromCache(parent);
        if (!files)
            return null;
        for (var i=0 ; i<files.length ; i++)
        {
            var r = files[i];
            if (r.data.name == name)
                return r;
        }
        return null;
    },

    ready: true,
    
    onReady : function(fn)
    {
        if (this.ready)
            fn.call();
        else
            this.on(FILESYSTEM_EVENTS.ready, fn);
    },

    // util

    concatPaths : function(a,b)
    {
        var c = 0;
        if (a.length > 0 && a.charAt(a.length-1)==this.separator) c++;
        if (b.length > 0 && b.charAt(0)==this.separator) c++;
        if (c == 0)
            return a + this.separator + b;
        else if (c == 1)
            return a + b;
        else
            return a + b.substring(1);
    },

    parentPath : function(p)
    {
        if (!p)
            p = this.rootPath;
        if (p.length > 1 && p.charAt(p.length-1) == this.separator)
            p = p.substring(0,p.length-1);
        var i = p.lastIndexOf(this.separator);
        return i == -1 ? this.rootPath : p.substring(0,i+1);
    },

    fileName : function(p)
    {
        if (!p || p == this.rootPath)
            return this.rootPath;
        if (p.length > 1 && p.charAt(p.length-1) == this.separator)
            p = p.substring(0,p.length-1);
        var i = p.lastIndexOf(this.separator);
        if (i > -1)
            p = p.substring(i+1);
        return p;
    }
});



//
// WebdavFileSystem
//


// config
// baseUrl: root of the webdav tree (http://localhost:8080/labkey/_webdav)
// rootPath: root of the tree we want to browse e.g. /home/@pipeline/
// rootName: display name for the root

LABKEY.WebdavFileSystem = function(config)
{
    config = config || {};
    Ext.apply(this, config, {
        baseUrl: LABKEY.contextPath + "/_webdav",
        rootPath: "/",
        rootName : (LABKEY.serverName || "LabKey Server")
    });
    this.ready = false;
    var prefix = this.concatPaths(this.baseUrl, this.rootPath);
    if (prefix.length > 0 && prefix.charAt(prefix.length-1) == this.separator)
        prefix = prefix.substring(0,prefix.length-1);
    this.prefixUrl = prefix;
    var prefixDecode  = decodeURIComponent(prefix);
    LABKEY.WebdavFileSystem.superclass.constructor.call(this);

    var getURI = function(v,rec)
    {
        if (!rec.uriOBJECT)
            rec.uriOBJECT = parseUri(v);
        return rec.uriOBJECT;
    };

    this.HistoryRecord = Ext.data.Record.create(['user', 'date', 'message', 'href']);
    this.historyReader = new Ext.data.XmlReader({record : "entry"}, this.HistoryRecord);
    
    this.FileRecord = Ext.data.Record.create(
        [
            {name: 'uri', mapping: 'href',
                convert : function(v, rec)
                {
                    var uri = getURI(v,rec);
                    return uri ? uri.href : "";
                }
            },
            {name: 'path', mapping: 'href',
                convert : function (v, rec)
                {
                    var uri = getURI(v,rec);
                    var path = decodeURIComponent(uri.pathname);
                    if (path.length >= prefixDecode.length && path.substring(0,prefixDecode.length) == prefixDecode)
                        path = path.substring(prefixDecode.length);
                    return path;
                }
            },
            {name: 'name', mapping: 'propstat/prop/displayname'},
            {name: 'file', mapping: 'href', type: 'boolean',
                convert : function (v, rec)
                {
                    // UNDONE: look for <collection>
                    var uri = getURI(v, rec);
                    var path = uri.pathname;
                    return path.length > 0 && path.charAt(path.length-1) != '/';
                }
            },
            {name: 'created', mapping: 'propstat/prop/creationdate', type: 'date', dateFormat : "c"},
            {name: 'createdBy', mapping: 'propstat/prop/createdby'},
            {name: 'modified', mapping: 'propstat/prop/getlastmodified', type: 'date'},
            {name: 'modifiedBy', mapping: 'propstat/prop/modifiedby'},
            {name: 'size', mapping: 'propstat/prop/getcontentlength', type: 'int'},
            {name: 'iconHref'},
            {name: 'contentType', mapping: 'propstat/prop/getcontenttype'},
            {name: 'options'}
        ]);
    this.connection = new Ext.data.Connection({method: "GET", headers: {"Method" : "PROPFIND", "Depth" : "1,noroot"}});
    this.proxy = new Ext.data.HttpProxy(this.connection);
    this.reader = new Ext.data.XmlReader({record : "response", id : "href"}, this.FileRecord);

    this.rootRecord = new this.FileRecord({
        id:"/",
        path:"/",
        name: this.rootName,
        file:false,
        uri:this.prefixUrl,
        iconHref: LABKEY.contextPath + "/_images/labkey.png"
    }, "/");
    this.reloadFile("/", (function()
    {
        this.ready = true;
        this.fireEvent(FILESYSTEM_EVENTS.ready);
    }).createDelegate(this));
};


Ext.extend(LABKEY.WebdavFileSystem, FileSystem,
{
    getHistory : function(path, callback) // calback(filesystem, success, path, history[])
    {
        var body =  "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<propfind xmlns=\"DAV:\"><prop><history></prop></propfind>";

        var proxy = new Ext.data.HttpProxy(
            {
                url: this.concatPaths(this.prefixUrl, path),
                xmlData : body,
//                method: "GET",
//                headers: {"Method" : "PROPFIND", "Depth" : "0"}
                method: "PROPFIND",
                headers: {"Depth" : "0"}
            });
        var cb = function(response, args, success)
        {
            callback.call(args.filesystem, success, args.path, response.records);
        }
        proxy.load({method:"PROPFIND", depth:"0"}, this.historyReader, cb, this, {filesystem:this, path:path});
    },

    canRead : function(record)
    {
        var options = record.data.options;
        return !options || -1 != options.indexOf('GET');
    },

    canWrite : function(record)
    {
        var options = record.data.options;
        return !options || -1 != options.indexOf("PUT");
    },
    
    canDelete : function(record)
    {
        var options = record.data.options;
        return !options || -1 != options.indexOf('DELETE');
    },                           

    deletePath : function(path, callback)
    {
        try
        {
            var fileSystem = this;
            var client = new XMLHttpRequest();
            client.onreadystatechange = function()
                {
                    if (client.readyState == 4)
                    {
                        if (204 == client.status)   // NO_CONTENT
                            callback(fileSystem, true, path);
                        else if (405 == client.status) // METHOD_NOT_ALLOWED
                            callback(fileSystem, false, path);
                        else
                            window.alert(client.statusText);
                    }
                };
            var resourcePath = this.concatPaths(this.prefixUrl, path);
            client.open("POST", resourcePath);
            client.setRequestHeader("method", "DELETE");
            client.send(null);
        }
        catch (x)
        {
            window.alert(x);
        }
        return true;
    },


    createDirectory : function(path, callback)
    {
        try
        {
            var fileSystem = this;
            var client = new XMLHttpRequest();
            client.onreadystatechange = function()
                {
                    if (client.readyState == 4)
                    {
                        if (200 == client.status || 201 == client.status)   // OK, CREATED
                            callback(fileSystem, true, path);
                        else if (405 == client.status) // METHOD_NOT_ALLOWED
                            callback(fileSystem, false, path);
                        else
                            window.alert(client.statusText);
                    }
                };
            var resourcePath = this.concatPaths(this.prefixUrl, path);
            client.open("POST", resourcePath);
            client.setRequestHeader("method", "MKCOL");
            client.send(null);
        }
        catch (x)
        {
            window.alert(x);
        }
        return true;
    },


    reloadFile : function(path, callback)
    {
        var url = this.concatPaths(this.prefixUrl, encodeURI(path));
        this.connection.url = url;
        var args = {url: url, path: path, callback:callback};
        this.proxy.load({method:"PROPFIND",depth:"0"}, this.reader, this.processFile, this, args);
        return true;
    },


    processFile : function(result, args, success)
    {
        if (success && result.records.length == 1)
        {
            var update = result.records[0];
            if (args.path == '/')
            {
                Ext.apply(this.rootRecord, update.data);
            }
            else
            {
                var record = this.recordFromCache(args.path);
                if (record)
                    Ext.apply(record.data, update.data);
            }
        }
        if (typeof args.callback == "function")
            args.callback(this, success && null != record, args.path, record);
    },


    reloadFiles : function(path, callback)
    {
        var url = this.concatPaths(this.prefixUrl, encodeURI(path));
        this.connection.url = url;

        var args = {url: url, path: path, callback:callback};
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

// CONFIG
//  required
//      getDropApplet : function()

var AppletFileSystem = function(config)
{
    config = config || {};
    Ext.apply(this, config, {
        path: Ext.isWindows ? "\\" : "/",
        rootName: "My Computer"
    });
    AppletFileSystem.superclass.constructor.call(this);
    this.FileRecord = Ext.data.Record.create(['uri', 'path', 'name', 'file', 'created', 'modified', 'modifiedBy', 'size', 'iconHref']);
    this.connection = new Ext.data.Connection({method: "GET", headers: {"Method" : "PROPFIND", "Depth" : "1,noroot"}});
    this.proxy = new Ext.data.HttpProxy(this.connection);
    this.reader = new Ext.data.XmlReader({record : "response", id : "path"}, this.FileRecord);

    this.rootRecord = new this.FileRecord(
    {
        id:"/",
        path:"/",
        name:"My Computer",
        file:false,
        iconHref: LABKEY.contextPath + "/_images/computer.png"
    }, "/");
};

Ext.extend(AppletFileSystem, FileSystem,
{
    rootPath : "/",
    separator : Ext.isWindows ? "\\" : "/",
    retry : 0,

    createDirectory : function(path, callback)
    {
        // UNDONE:
        alert("NYI");
    },

    reloadFiles : function(directory, callback)
    {
        var applet = this.getDropApplet();
        if (!applet)
        {
            this.retry++;
            this.reloadFiles.defer(100, this, [directory, callback]);
            return true;
        }
        this.retry = 0;
        if (!directory)
            return false;
        var root = directory == "/";
        if (root)
        {
            if (!applet.local_listRoots())
                return false;
        }
        if (!root || 1==applet.local_getFileCount())
        {
            if (!applet.local_changeDirectory(directory))
                return false;
        }
        var count = applet.local_getFileCount();
        var js = applet.local_getObjects();
        var datas = eval("var $=" + js + ";$;");
        var records = [];
        for (var i=0 ; i<datas.length ; i++)
        {
            var data = datas[i];
            if (data.name.charAt(data.name.length-1) == '\\')
                data.name = data.name.substring(0,data.name.length-1);
            data.file = !data.isDirectory;
            data.size = data.length;
            data.id = path;
            data.modified = data.lastModified;
            data.modifiedBy = null;
            data.iconHref = data.isDirectory ? this.FOLDER_ICON : null;
            records.push(new this.FileRecord(data, path));
        }
        this._addFiles(directory, records);
        if (typeof callback == "function")
            callback.defer(1, null, [this, true, directory, records]);
        return true;
    }
});


//
// FileListMenu
//
var FileListMenu = function(fileSystem, path, fn)
{
    FileListMenu.superclass.constructor.call(this, {items:[], cls:'extContainer'});
    this.showFiles = false;
    this.fileSystem = fileSystem;
    this.path = path;

    if (path && path != fileSystem.rootPath)
    {
        this.addMenuItem(new Ext.menu.Item({text: '[up]', path:this.fileSystem.parentPath(path)}));
    }

    var records = fileSystem.directoryFromCache(path);
    var populate = function(filesystem, success, path, records)
        {
            for (var i=0 ; i<records.length ; i++)
            {
                var record = records[i];
                var data = record.data;
                if (!this.showFiles && data.file)
                    continue;
                this.addMenuItem(new Ext.menu.Item({text:data.name, icon:data.iconHref, path:record.data.path}));
            }
        };
    if (records)
        populate.call(this, null, true, path, records);
    else
        fileSystem.listFiles(path, populate.createDelegate(this));
    if (typeof fn == "function")
    {
        this.on("click", function(menu,item,event)
        {
            var path = item.initialConfig.path;
            fn(path);
        });
    }
};
Ext.extend(FileListMenu, Ext.menu.Menu);


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
            if (this.fileSystem.listFiles(node.id, this.listCallback.createDelegate(this, [args], true)))
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
        var n = Ext.apply({},{id:data.id || data.path, leaf:data.file, text:data.name, href:null}, data);
        return FileSystemTreeLoader.superclass.createNode.call(this, n);
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
                var n = this.createNodeFromRecord(record);
                if (n)
                    node.appendChild(n);
            }
            node.endUpdate();
            if (typeof args.callback == "function")
                args.callback(this, node);
            if (node.childNodes.length == 1)
            {
                var child = node.firstChild;
                if (!child.isLeaf())
                    child.expand();
            }
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
// DavSubmitAction
//

var DavSubmitAction = function(form, options)
{
    Ext.form.Action.Submit.superclass.constructor.call(this, form, options);
};
Ext.extend(DavSubmitAction, Ext.form.Action.Submit, {});


//
//  FILE BROWSER UI
//


var BROWSER_EVENTS = {selectionchange:"selectionchange", directorychange:"directorychange", doubleclick:"doubleclick"};


// configuration
//  required
//      filesystem
//  optional
//      resizable : true
//      resizable : true,
//      showFolders: true,
//      showAddressBar: true,
//      showDetails: true,
//      showProperties: false,
//
LABKEY.FileBrowser = function(config)
{
    this.actions =
    {
        download: this.getDownloadAction(),
        parentFolder: this.getParentFolderAction(),
        refresh: this.getRefreshAction(),
        help: this.getHelpAction(),
        createDirectory: this.getCreateDirectoryAction(),
        //drop : this.getOldDropAction(),
        showHistory : this.getShowHistoryAction(),
        deletePath: this.getDeleteAction()
    };
    this.addEvents( [ BROWSER_EVENTS.selectionchange, BROWSER_EVENTS.directorychange, BROWSER_EVENTS.doubleclick ]);

    config = config || {};                                                                 
    Ext.apply(this.actions, config.actions || {});
    delete config.actions;
    Ext.apply(this, config);
    this.__init__(config);
};


Ext.extend(LABKEY.FileBrowser, Ext.Panel,
{
    FOLDER_ICON: LABKEY.contextPath + "/" + LABKEY.extJsRoot + "/resources/images/default/tree/folder.gif",

    // instance/config variables
    resizable : true,
    allowChangeDirectory : true,
    showFolderTree: true,
    showAddressBar: true,
    showDetails: true,
    showProperties: true,
    propertiesPanel : null,

    grid: null,
    store: null,

    currentDirectory: null,
    selectedRecord: null,

    //
    // actions
    //

    actions : {},
    tbar : null,

    getDownloadAction : function()
    {
        return new Ext.Action({text: 'Download', scope: this, handler: function()
            {
                if (this.selectedRecord && this.selectedRecord.data.file && this.selectedRecord.data.uri)
                    window.location = this.selectedRecord.data.uri + "?contentDisposition=attachment";
            }});
    },


    getCreateDirectoryAction : function()
    {
        return new Ext.Action({text: 'Create Folder', scope: this, handler: function()
        {
            var p = this.currentDirectory.data.path;
            var folder = prompt( "Folder Name", "New Folder");
            if (!folder)
                return;
            var path = this.fileSystem.concatPaths(p, folder);
            this.fileSystem.createDirectory(path, this._refreshOnCallbackSelectPath.createDelegate(this));
        }.createDelegate(this)});
    },


    _refreshOnCallbackSelectPath : function(fs, success, path)
    {
        if (this.actions.refresh)
            this.actions.refresh.execute();
        if (path)
            this.selectFile(path);
    },


    _refreshOnCallback : function(fs, success, path)
    {
        if (this.actions.refresh)
            this.actions.refresh.execute();
    },


    getParentFolderAction : function()
    {
        return new Ext.Action({text: 'Up', scope: this, handler: function()
        {
            // CONSIDER: PROPFIND to ensure this link is still good?
            var p = this.currentDirectory.data.path;
            var dir = this.fileSystem.parentPath(p) || this.fileSystem.rootPath;
            this.changeDirectory(dir);
        }});
    },


    getRefreshAction : function()
    {
        return new Ext.Action({text: 'Refresh', scope:this, iconCls:'refreshIcon', handler: this.refreshDirectory});
    },


    getDeleteAction : function()
    {
        return new Ext.Action({text: 'Delete', scope:this, iconCls:'deleteIcon', disabled:true, handler: function()
        {
            if (!this.currentDirectory)
                return;
            if (this.selectedRecord && this.selectedRecord.data.file)
                this.fileSystem.deletePath(this.selectedRecord.data.path, this._refreshOnCallback.createDelegate(this));
            this.selectFile(null);
        }});
    },


//    getOldDropAction : function()
//    {
//        return new Ext.Action({text: 'Upload multiple files', scope:this, disabled:true, handler: function()
//        {
//            if (!this.currentDirectory)
//                return;
//            var prefix = this.fileSystem.prefixUrl;
//            var url = this.fileSystem.concatPaths(prefix,this.currentDirectory.data.path);
//            window.open(url, '_blank', 'height=600,width=1000,resizable=yes');
//        }});
//    },


    helpEl : null,
    helpWindow : null,

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
                html.push($h(item.user)); html.push("<br>");
                html.push($h(item.message));
                if (html.href)
                {
                    html.push("<a color=green href='"); html.push($h(html.href)); html.push("'>link</a><br>");
                }
            }
        }});
    },


    changeDirectory : function(record)
    {
        if (typeof record == "string")
        {
            var path = record;
            record = this.fileSystem.recordFromCache(path);
            if (!record)
            {
                var parent = this.fileSystem.parentPath(path);
                this.fileSystem.listFiles(parent, function(filesystem, success, parentPath, records)
                {
                    record = this.fileSystem.recordFromCache(path);
                    if (record)
                        this.changeDirectory(record);
                }.createDelegate(this));
            }
        }

        if (record && !record.data.file && this.currentDirectory != record)
        {
            this.currentDirectory = record;
            this.fireEvent(BROWSER_EVENTS.directorychange, record);
        }
    },


    refreshDirectory : function()
    {
        if (!this.currentDirectory)
            return;
        this.fileSystem.reloadFiles(this.currentDirectory.data.path, this.loadRecords.createDelegate(this));
        var sel = this.tree.getSelectionModel().getSelectedNode();
        if (sel)
            sel.reload();
    },


    selectFile : function(record)
    {
        if (typeof record == "string")
        {
            var path = record;
            record = this.fileSystem.recordFromCache(path);
            if (!record)
            {
                var parent = this.fileSystem.parentPath(path);
                this.fileSystem.listFiles(parent, function(filesystem, success, parentPath, records)
                {
                    record = this.fileSystem.recordFromCache(path);
                    if (record)
                        this.selectFile(record);
                }.createDelegate(this));
            }
        }
        if (this.selectedRecord && record && this.selectedRecord.data.path == record.data.path)
            return;
        if (!this.selectedRecord && !record)
            return;

        this.selectedRecord = record;
        this.fireEvent(BROWSER_EVENTS.selectionchange, record);
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

    Grid_onSelectionChange : function(rowIndex, keepExisting, record)
    {
        console.log(rowIndex);
        console.log(keepExisting);
        console.log(record);
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

    Grid_onClick : function(e)
    {
        //this.grid.getSelectionModel().clearSelections();
    },

    Grid_onCelldblclick : function(grid, rowIndex, columnIndex, e)
    {
        var record = grid.getStore().getAt(rowIndex);
        this.selectFile(record);
        if (!record.data.file)
        {
            this.changeDirectory(record);
            if (this.tree)
            {
                var treePath = this.treePathFromId(record.id);
                this.tree.expandPath(treePath);
                var node = this.tree.getNodeById(record.id);
                if (node)
                {
                    node.ensureVisible();
                    node.select();
                }
            }
        }
        this.grid.focus();
        this.fireEvent(BROWSER_EVENTS.doubleclick, record);
    },

    Tree_onSelectionchange : function(sm, node)
    {
        if (this.grid)
            this.grid.getSelectionModel().clearSelections();
        if (node)
        {
            this.selectFile(node.record);
            this.changeDirectory(node.record);
        }
    },


    addressBarHandler : null,


    history : null,
    
    _historyCallback : function(filesystem, path, success, records)
    {
        if (path == this.selectedRecord.data.path)
        {
            this.history = records;
            if (this.actions.showHistory)
                this.actions.showHistory.enable();
        }
    },

    getHistory : function(path)
    {
        this.fileSystem.getHistory(path, this._history.createDelegate(this));
    },

    updateAddressBar : function(path)
    {
        var el = $('addressBar');
        if (!el)
            return;
        var elStyle = el.dom.style;
        elStyle.backgroundColor = "#f0f0f0";                                           
        elStyle.height = "100%";
        elStyle.width = "100%";
        
        if (this.addressBarHandler)
        {
            el.un("click", this.addressBarHandler);
            this.addressBarHandler = null;
        }
        var text;
        if (path == this.fileSystem.rootPath)
            text = $h(this.fileSystem.rootRecord.data.name);
        else
            text = $h(path);
        el.update('<table height=100% width=100%><tr><td height=100% width=100% valign=middle align=left>' + text + '</td></tr></table>');
        this.addressBarHandler = this.showFileListMenu.createDelegate(this, [path]);
        el.on("click", this.addressBarHandler);
    },

    showFileListMenu : function(path)
    {
        var el = $('addressBar');
        var menu = new FileListMenu(this.fileSystem, path, this.changeDirectory.createDelegate(this));
        menu.show(el);
    },

    ancestry : function(id)
    {
        var path = id;
        var a = [path];
        while (true)
        {
            var parent = this.fileSystem.parentPath(path);
            if (!parent || parent == path)
                break;
            a.push(parent);
            path = parent;
        }
        a.reverse();
        return a;
    },

    treePathFromId : function(id)
    {
        var a = this.ancestry(id);
        return a.join(";");
    },

    updateFileDetails : function(record)
    {
        try
        {
            var el = $('file-details');
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
                html.push(":</th><td style='font-size:110%;'>");
                html.push(value);
                html.push("</td></tr>");
            };
            html.push("<p style='font-size:133%; padding:8px;'>");
            html.push($h(data.name));
            html.push("</p>");
            html.push("<table style='padding-left:30px;'>");
            if (data.modified)
                row("Date Modified", _longDateTime(data.modified));
            if (data.file)
                row("Size",data.size);
            if (data.createdBy && data.createdBy != 'Guest')
                row("Created By", data.createdBy);
            html.push("</table>");
            el.update(html.join(""));
        }
        catch (x)
        {
            log(x);
        }
    },


    loadRecords : function(filesystem, success, path, records)
    {
        if (success && this.currentDirectory.data.path == path)
        {
            this.store.removeAll();
            if (!this.allowChangeDirectory)
            {
                var t = records;
                records = [];
                for (var i=0 ; i<t.length ; i++)
                    if (t[i].data.file) records.push(t[i]);
            }
            this.store.add(records);
        }
    },


    start : function(wd)
    {
        if (!this.fileSystem.ready)
        {
            this.fileSystem.onReady(this.start.createDelegate(this));
            return;
        }
        var root = this.tree.getRootNode();
        if (this.showFolderTree)
            root.expand();
        if (typeof wd == "string")
        {
            this.changeDirectory(wd);
            this.selectFile(wd);
        }
        else
        {
            this.selectFile(root.record);
            this.changeDirectory(root.record);
        }
    },


    __init__ : function(config)
    {
        var me = this; // for anonymous inner functions

        //
        // GRID
        //

        // mild convolution to pass fileSystem to the _attachPreview function
        var iconRenderer = renderIcon.createDelegate(null,_attachPreview.createDelegate(this.fileSystem,[],true),true);

        this.store = new Ext.data.Store({recordType:this.fileSystem.FileRecord});
        this.grid = new Ext.grid.GridPanel(
        {
            store: this.store,
            border:false,
            columns: [
                {header: "", width:20, dataIndex: 'iconHref', sortable: false, hiddenn:false, renderer:iconRenderer},
                {header: "Name", width: 150, dataIndex: 'name', sortable: true, hidden:false, renderer:Ext.util.Format.htmlEncode},
//                {header: "Created", width: 150, dataIndex: 'created', sortable: true, hidden:false, renderer:renderDateTime},
                {header: "Modified", width: 150, dataIndex: 'modified', sortable: true, hidden:false, renderer:renderDateTime},
                {header: "Size", width: 80, dataIndex: 'size', sortable: true, hidden:false, align:'right', renderer:renderFileSize}
            ]
        });
        this.grid.getSelectionModel().on(ROWSELECTION_MODEL.rowselect, this.Grid_onRowselect, this);
        this.grid.getSelectionModel().on(ROWSELECTION_MODEL.selectionchange, this.Grid_onSelectionChange, this);
        this.grid.on(GRIDPANEL_EVENTS.celldblclick, this.Grid_onCelldblclick, this);
        this.grid.on(GRIDPANEL_EVENTS.keypress, this.Grid_onKeypress, this);
        this.grid.on(GRIDPANEL_EVENTS.click, this.Grid_onClick, this);
        this.grid.on(PANEL_EVENTS.render, function()
        {
            this.getView().hmenu.getEl().addClass("extContainer");
            this.getView().colMenu.getEl().addClass("extContainer");
        }, this.grid);

        //
        // TREE
        //

        var treeloader = new FileSystemTreeLoader({fileSystem: this.fileSystem, displayFiles:false});
        var root = treeloader.createNodeFromRecord(this.fileSystem.rootRecord, this.fileSystem.rootPath);
        this.tree = new Ext.tree.TreePanel(
        {
            loader:treeloader,
            root:root,
            rootVisible:true,
            title: 'Folders',
            useArrows:true,
            autoScroll:true,
            animate:true,
            enableDD:false,
            containerScroll:true,
            border:false,
            pathSeparator:';'
        });
        this.tree.getSelectionModel().on(TREESELECTION_EVENTS.selectionchange, this.Tree_onSelectionchange, this);

        //
        // Toolbar
        //

        var tbarConfig = [];
        if (!this.tbar)
        {
            // UNDONE: need default ordering
            if (!this.allowChangeDirectory)
            {
                delete this.actions.createDirectory;
                delete this.actions.parentFolder;
            }
            for (var a in this.actions)
                if (this.actions[a] && this.actions[a].isAction)
                    tbarConfig.push(this.actions[a]);
        }
        else
        {
            for (var i=0 ; i<this.tbar.length ; i++)
            {
                var item = this.tbar[i];
                if (typeof item == "string" && typeof this.actions[item] == "object")
                    tbarConfig.push(this.actions[item]);
                else
                    tbarConfig.push(item);
            }
        }

        //
        // Upload
        //

        this.fileUploadField = new Ext.form.FileUploadField(
        {
            id: this.id ? this.id + 'Upload' : 'fileUpload',
            buttonText: "Upload File...",
            buttonOnly: true,
            buttonCfg: {cls: "labkey-button"},
            listeners: {"fileselected": function (fb, v)
            {
              if (me.currentDirectory)
              {
                  var form = me.uploadPanel.getForm();
                  var options = {url:me.currentDirectory.data.uri, record:me.currentDirectory, name:me.fileUploadField.getValue()};
                  var action = new DavSubmitAction(form, options);
                  form.doAction(action);
              }
            }}
        });
        
        this.uploadPanel = new Ext.FormPanel({
            id : 'uploadPanel',
            formId : this.id ? this.id + 'Upload-form' : 'fileUpload-form',
            method : 'POST',
            fileUpload: true,
            enctype:'multipart/form-data',
            layout:'fit',
            border:false,
            margins:'5 0 0 5',
            bodyStyle : 'background-color:#f0f0f0; padding:5px;',
            defaults: {bodyStyle : 'background-color:#f0f0f0'},
            items: [this.fileUploadField],
            listeners: {
                "actioncomplete" : function (f, action)
                {
                    console.log("upload actioncomplete");
                    console.log(action);
                    var options = action.options;
                    me.refreshDirectory();
                    me.selectFile(me.fileSystem.concatPaths(options.record.data.path, options.name));
                },
                "actionfailed" : function (f, action)
                {
                    console.log("upload actionfailed");
                    console.log(action);
                    me.refreshDirectory();
                }
            }
        });


        //
        // Layout top panel
        //

        var layoutItems = [];
        if (this.showAddressBar)
        {
            layoutItems.push(
            {
                region: 'north',
                height: 24,
                margins: '5 5 0 5',
                layout: 'fit',
                border: true,
                items: [{id:'addressBar', html: '<div style="background-color:#f0f0f0;height:100%;width:100%">&nbsp</div>'}]
            });
        }
        if (this.showDetails)
        {
            layoutItems.push(
            {
                region: 'south',
                height: 100,
                minSize: 75,
                maxSize: 250,
                margins: '0 5 5 5',
                layout: 'fit',
                items: [{html:'<div style="background-color:#f0f0f0;height:100%;width:100%">&nbsp</div>', id:'file-details'}]
            });
        }
        if (this.showFolderTree)
        {
            layoutItems.push(
            {
                region:'west',
                id:'west-panel',
                split:true,
                width: 200,
                minSize: 100,
                collapsible: true,
                margins:'5 0 5 5',
                layoutConfig:{animate:true},
                items: [this.tree]
            });
        }
        layoutItems.push(
            {
                region:'center',
                margins: this.propertiesPanel ? '5 0 5 0' : '5 5 5 0',
                minSize: 200,
                layout:'border',
                items:
                    [
                        {region:'center', layout:'fit', border:false, items:[this.grid]},
                        {region:'south', layout:'fit', border:false, height:40, minSize:40, margins:'1 0 0 0', items:[this.uploadPanel]}
                    ]
            });
        if (this.propertiesPanel)
        {
            var panels = !'length' in this.propertiesPanel ? [this.propertiesPanel] : this.propertiesPanel;
            layoutItems.push(
            {
                region:'east',
                split:true,
                margins:'5 5 5 0',
                width: 200,
                minSize: 100,
                border: false,
                layout: 'accordion',
                items: panels
            });
        }

        var renderTo = config.renderTo || null;
        Ext.apply(config, {layout:'border', tbar:tbarConfig, items: layoutItems, renderTo:null}, {id:'fileBrowser', height:600, width:800});
        LABKEY.FileBrowser.superclass.constructor.call(this, config);

        //
        // EVENTS (tie together components)
        //

        this.on(BROWSER_EVENTS.selectionchange, function(record)
        {
            this.history = null;
            if (this.actions.showHistory)
                this.actions.showHistory.disable();

            this.updateFileDetails(record);

            if (record && record.data && record.data.file && startsWith(record.data.uri,"http"))
                this.actions.download.enable();
            else
                this.actions.download.disable();

            if (record && this.fileSystem.canDelete(record))
                this.actions.deletePath.enable();
            else
                this.actions.deletePath.disable();
        }, this);

        if (this.actions.parentFolder)
        {
            this.on(BROWSER_EVENTS.directorychange, function(record)
            {
                if (record && record.data && record.data.path != this.fileSystem.rootPath)
                    this.actions.parentFolder.enable();
                else
                    this.actions.parentFolder.disable();
            }, this);
        }

        this.on(BROWSER_EVENTS.directorychange,function(record)
        {
            this.store.removeAll();
            this.fileSystem.listFiles(record.data.path, this.loadRecords.createDelegate(this));
            this.updateAddressBar(record.data.path);
            if (this.fileSystem.canWrite(record))
            {
                //this.uploadPanel.setVisible(false);
                this.fileUploadField.enable();
            }
            else
            {
                //this.uploadPanel.setVisible(true);
                this.fileUploadField.disable();
            }
        }, this);
    }
});



function generateButton(config)
{
    if (typeof config == "text")
        config = {text:config};
    var html = '<span';
    if (config.id)
        html += ' id="' + config.id + '"';
    if (config.onclick)
        html += ' onclick="' + onclick + '"';
    html += '>' + config.text + '</span>';
    return html;
}


/*
var appletEvents =
{
    ready: function() {},
    update: function() {},
    dragEnter : function() {},
    dragExit : function() {}
};

LABKEY.writeApplet(
{
    id:"dropApplet",
    archive:"<%=request.getContextPath()%>/_applets/applets-9.1.jar?guid=<%=GUID.makeHash()%><%=AppProps.getInstance().getServerSessionGUID()%>",
    code:"org.labkey.applets.drop.DropApplet",
    width:200,
    height:200,
    params:
    {
        url:<%=PageFlowUtil.jsString(baseUrl)%>,
        webdavPrefix:<%=PageFlowUtil.jsString(webdavPrefix)%>,
        user:<%=PageFlowUtil.jsString(context.getUser().getEmail())%>,
        password:<%=PageFlowUtil.jsString(request.getSession(true).getId())%>,
        events:'appletEvents'
    }
});

function getDropApplet()
{
    try
    {
        var el = $("dropApplet");
        var applet = el ? el.dom : null;
        if (applet && 'isActive' in applet && applet.isActive())
            return applet;
    }
    catch (x)
    {
    }
    return null;
}
*/
