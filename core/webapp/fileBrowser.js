/*
 * Copyright (c) 2008-2010 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

LABKEY.requiresCss("_images/icons.css");

var $ = Ext.get;
var $h = Ext.util.Format.htmlEncode;
var $dom = Ext.DomHelper;


var FATAL_INT = 50000;
var ERROR_INT = 40000;
var WARN_INT  = 30000;
var INFO_INT  = 20000;
var DEBUG_INT = 10000;


/*
	parseUri 1.2.1
	(c) 2007 Steven Levithan <stevenlevithan.com>
	MIT License
*/
function parseUri(str)
{
    var	o   = parseUri.options;
    var m   = o.parser[o.strictMode ? "strict" : "loose"].exec(str);
    var uri = this || {};
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
    uri.href = this.protocol + "://" + this.host + this.pathname + this.search;
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

var URI = function(u)
{
    this.toString = function()
    {
        return this.protocol + "://" + this.host + this.pathname + this.search;
    };
    if (typeof u == "string")
        this.parse(u);
    else if (typeof u == "object")
        Ext.apply(this,u);
};
URI.prototype = {parse: parseUri};


// we want to encode everything save / so this is not like encodeURI() or encodeURIComponent()
function encodePath(s)
{
    var a = s.split('/');
    for (var i=0 ; i<a.length ; i++)
        a[i] = encodeURIComponent(a[i]);
    return a.join('/');
}


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
var WINDOW_EVENTS = Ext.apply({
    resize:'resize', maximize:'maximize', minimize:'minimize', restore:'restore'
},PANEL_EVENTS);
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

var ROWSELETION_EVENTS =
{
    selectionchange:"selectionchange",
    beforerowselect:"beforerowselect",
    rowselect:"rowselect",
    rowdeselect:"rowdeselect"
};

var STORE_EVENTS =
{
    datachanged:'datachanged',
    metachange:'metachange',
    add:'add',
    remove:'remove',
    update:'update',
    clear:'clear',
    beforeload:'beforeload',
    load:'load',
    loadexception:'loadexception'
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
    return "<span title='" + f + "'>" + formatWithCommas(value) + "</span>";
}

function formatWithCommas(value)
{
    var x = value;
    var formatted = (x == 0) ? '0' : '';
    var separator = '';
    while (x > 0)
    {
        // Comma separate between thousands
        formatted = separator + formatted;
        formatted = (x % 10) + formatted;
        x -= (x % 10);
        if (x > 0)
        {
            formatted = ((x % 100) / 10) + formatted;
            x -= (x % 100);
        }
        if (x > 0)
        {
            formatted = ((x % 1000) / 100) + formatted;
            x -= (x % 1000);
        }
        x = x / 1000;
        separator = ',';
    }
    return formatted;
}


function renderUsage(value, metadata, record, rowIndex, colIndex, store)
{
    if (!value || value.length == 0) return "";
    var result = "<span title='";
    for (var i = 0; i < value.length; i++)
    {
        if (i > 0)
        {
            result = result + ", ";
        }
        result = result + Ext.util.Format.htmlEncode(value[i].message);
    }
    result = result + "'>";
    for (i = 0; i < value.length; i++)
    {
        if (i > 0)
        {
            result = result + ", ";
        }
        if (value[i].href)
        {
            result = result + "<a href=\'" + Ext.util.Format.htmlEncode(value[i].href) + "'>";
        }
        result = result + Ext.util.Format.htmlEncode(value[i].message);
        if (value[i].href)
        {
            result = result + "</a>";
        }
    }
    result = result + "</span>";
    return result;
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

var _previewScope = "previewAncor";

var PreviewResource = Ext.extend(LABKEY.ext.PersistentToolTip, {

    baseCls: 'x-panel',
    minWidth : 40,
    maxWidth : 800,
    frame : true,

    destroy : function()
    {
        PreviewResource.superclass.destroy.call(this);
    },

    // we're not really ready to show anything, we have to get the resource still
    showAt : function(xy)
    {
        this.showAt_xy = xy;
        this.loadResource();
    },

    previewAt : function(xy)
    {
        PreviewResource.superclass.showAt.call(this, xy);
    },

    render : function(ct)
    {
        PreviewResource.superclass.render.call(this, ct);
    },

    onRender : function(ct)
    {
        this.title = false;
        PreviewResource.superclass.onRender.call(this, ct);
        this.body.update($dom.markup(this.html));
    },

    doAutoWidth : function()
    {
        PreviewResource.superclass.doAutoWidth.call(this);
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

        if (startsWith(contentType,'image/'))
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
                    var contentType = response.getResponseHeader["Content-Type"] || "text/html";
                    if (startsWith(contentType,"text/"))
                    {
                        var id = 'iframePreview' + (++Ext.Component.AUTO_ID);
                        var body = response.responseText;
                        body = Ext.util.Format.stripScripts(body);
                        //this.html = {tag:'div', style:{width:'600px', height:'400px', overflow:'auto'}, children:body};
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
                                body = '<base href="' + $h(base) + '" />' + body;
                            doc.write(body);
                            doc.close();
                        }
                    }
                }).createDelegate(this)
            });
        }
// DIV
//        else if (contentType == 'text/html')
//        {
//            var headers = {};
//            var requestid = this.connection.request({
//                autoAbort:true,
//                url:uri,
//                headers:headers,
//                method:'GET',
//                disableCaching:false,
//                success : (function(response)
//                {
//                    var contentType = response.getResponseHeader["Content-Type"] || "text/html";
//                    if (startsWith(contentType,"text/"))
//                    {
//                        var body = response.responseText;
//                        body = Ext.util.Format.stripScripts(body);
//                        this.html = {tag:'div', style:{width:'600px', height:'400px', overflow:'auto'}, children:body};
//                        this.previewAt(this.showAt_xy);
//                    }
//                }).createDelegate(this)
//            });
//        }
        else if (startsWith(contentType,'text/') || contentType == 'application/javascript' || endsWith(name,".log"))
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
                    var contentType = response.getResponseHeader["Content-Type"] || "text/plain";
                    if (startsWith(contentType,"text/"))
                    {
                        var text = response.responseText;
                        if (headers['Range']) text += "\n. . .";
                        this.html = {tag:'div', style:{width:'600px', height:'400px', overflow:'auto'}, children:{tag:'pre', children:$h(text)}};
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
    },

    connection : new Ext.data.Connection({autoAbort:true, method:'GET', disableCaching:false})
});


var allPreviews = [];

function _attachPreview(id,record)
{
    if (this && !this.canRead(record))
        return;
    if (!record.data.file)
        return;
    var img = Ext.fly(id,_previewScope);
    if (!img) return;
    var preview = new PreviewResource({title:id, target:id, record:record});
    allPreviews.push(preview);
}


function destroyPreviews()
{
    Ext.destroy.apply(null, allPreviews);
    allPreviews = [];
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
//      actionHref(string)
//      contentType(string,optional)

var FILESYSTEM_EVENTS = {listfiles:"listfiles", ready:"ready"};

var FileSystem = Ext.extend(Ext.util.Observable, {

    constructor : function(config)
    {
        Ext.util.Observable.prototype.constructor.call(this);
        this.directoryMap = {};
        this.addEvents(FILESYSTEM_EVENTS.listfiles, FILESYSTEM_EVENTS.ready);
    },

    rootPath : "/",
    separator : "/",

    listFiles : function(path, callback, scope)    // callback(filesystem, success, path, records)
    {
        var files = this.directoryFromCache(path);
        if (files)
        {
            if (typeof callback == "function")
                callback.defer(1, scope||this, [this, true, path, files]);
        }
        else
        {
            var ok = this.reloadFiles(path, callback, scope);
            if (!ok && typeof callback == "function")
                callback.defer(1, scope||this, [this, false, path, null]);
        }
    },

    // force reload on next listFiles call
    uncacheListing : function(record)
    {
        var path = (typeof record == "string") ? record : record.data.path;
        this.directoryMap[path] = null;
    },


    canRead : function(record)
    {
        return true;
    },

    canWrite: function(record)
    {
        return true;
    },

    canMkdir: function(record)
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
    reloadFiles : function(path, callback, scope)
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
        this.fireEvent(FILESYSTEM_EVENTS.listfiles, this, path, records);
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
        var uri = rec.uriOBJECT || new URI(v);
        if (!Ext.isIE && !rec.uriOBJECT)
            try {rec.uriOBJECT = uri;} catch (e) {};
        return uri;
    };

    this.HistoryRecord = Ext.data.Record.create(['user', 'date', 'message', 'href']);
    this.historyReader = new Ext.data.XmlReader({record : "entry"}, this.HistoryRecord);

    this.propNames = ["creationdate", "displayname", "createdby", "getlastmodified", "modifiedby", "getcontentlength",
                 "getcontenttype", "getetag", "resourcetype", "source", "path", "iconHref"];
    if (config.extraPropNames != undefined)
    {
        this.propNames = this.propNames.concat(config.extraPropNames);
    }

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
            {name: 'name', mapping: 'propstat/prop/displayname', sortType:'asUCString'},
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
            {name: 'description', mapping: 'propstat/prop/description'},
            {name: 'actionHref', mapping: 'propstat/prop/actions',
                convert : function (v, rec)
                {
                    var result = [];
                    var actionsElements = Ext.DomQuery.compile('propstat/prop/actions').call(this, rec);
                    if (actionsElements.length > 0)
                    {
                        var actionElements = actionsElements[0].getElementsByTagName('action');
                        for (var i = 0; i < actionElements.length; i++)
                        {
                            var action = new Object();
                            var childNodes = actionElements[i].childNodes;
                            for (var n = 0; n < childNodes.length; n++)
                            {
                                var childNode = childNodes[n];
                                if (childNode.nodeName == 'message')
                                {
                                    action.message = childNode.textContent;
                                }
                                else if (childNode.nodeName == 'href')
                                {
                                    action.href = childNode.textContent;
                                }
                            }
                            result[result.length] = action;
                        }
                    }
                    return result;
                }
            },
            {name: 'iconHref'},
            {name: 'contentType', mapping: 'propstat/prop/getcontenttype'},
            {name: 'options'}
        ]);
    this.connection = new Ext.data.Connection({method: "GET", timeout: 600000, headers: {"Method" : "PROPFIND", "Depth" : "1", propname : this.propNames}});
    this.proxy = new Ext.data.HttpProxy(this.connection);
    this.transferReader = new Ext.data.XmlReader({record : "response", id : "href"}, this.FileRecord);

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
    getHistory : function(path, callback) // callback(filesystem, success, path, history[])
    {
        var body =  "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<propfind xmlns=\"DAV:\"><prop><history/></prop></propfind>";

        var proxy = new Ext.data.HttpProxy(
            {
                url: this.concatPaths(this.prefixUrl, path),
                xmlData : body,
                method: "PROPFIND",
                headers: {"Depth" : "0"}
            });
        var cb = function(response, args, success)
        {
            callback.call(args.filesystem, success, args.path, response.records);
        };
        proxy.load({method:"PROPFIND", depth:"0", propname : this.propNames}, this.historyReader, cb, this, {filesystem:this, path:path});
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

    canMkdir : function(record)
    {
        var options = record.data.options;
        return !options || -1 != options.indexOf("MKCOL");
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
            var resourcePath = this.concatPaths(this.prefixUrl, encodePath(path));
            var fileSystem = this;
            var connection = new Ext.data.Connection();
            connection.handleResponse = function(response)
            {
                if (204 == response.status || 404 == response.status) // NO_CONTENT (success), NOT_FOUND (actually goes to handleFailure)
                {
                    // CONSIDER just delete one entry
                    fileSystem.uncacheListing(fileSystem.parentPath(path));
                    callback(fileSystem, true, path, response);
                }
                else if (405 == response.status) // METHOD_NOT_ALLOWED
                    callback(fileSystem, false, path, response);
                else
                    window.alert(response.statusText);
            }
            connection.request({method:"DELETE", url:resourcePath});
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
                            window.alert("Unable to created directory, status code " + client.status + ". " + client.statusText);
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
        var url = this.concatPaths(this.prefixUrl, encodePath(path));
        this.connection.url = url;
        var args = {url: url, path: path, callback:callback};
        this.proxy.load({method:"PROPFIND",depth:"0", propname : this.propNames}, this.transferReader, this.processFile, this, args);
        return true;
    },


    _updateRecord : function(update)
    {
        var path = update.data.path;
        if (path == '/')
        {
            Ext.apply(this.rootRecord.data, update.data);
        }
        else
        {
            var record = this.recordFromCache(path);
            if (record)
                Ext.apply(record.data, update.data);
        }
    },


    processFile : function(result, args, success)
    {
        var update = null;
        if (success && result && !Ext.isArray(result.records))
            success = false;
        if (success && result.records.length == 1)
        {
            update = result.records[0];
            this._updateRecord(update);
        }
        if (typeof args.callback == "function")
            args.callback(this, success && null != update, args.path, update);
    },



    pendingPropfind : {},


    uncacheListing : function(record)
    {
        var path = (typeof record == "string") ? record : record.data.path;
        this.directoryMap[path] = null;

        var args = this.pendingPropfind[path];
        if (args && args.transId)
        {
            this.connection.abort(args.transId);
            this.connection.url = args.url;
            this.proxy.load({method:"PROPFIND",depth:"1", propname : this.propNames}, this.transferReader, this.processFiles, this, args);
            args.transId = this.connection.transId;
        }
    },


    reloadFiles : function(path, callback, scope)
    {
        var cb = callback.createDelegate(scope||this);

        var args = this.pendingPropfind[path];
        if (args)
        {
            console.debug("pending " + args.url);
            args.callbacks.push(cb);
            return;
        }

        var url = this.concatPaths(this.prefixUrl, encodePath(path));
        this.connection.url = url;
        console.debug("requesting " + url);
        this.pendingPropfind[path] = args = {url: url, path: path, callbacks:[cb]};
        this.proxy.load({method:"PROPFIND",depth:"1", propname : this.propNames}, this.transferReader, this.processFiles, this, args);
        args.transId = this.connection.transId;
        return true;
    },


    processFiles : function(result, args, success)
    {
        delete this.pendingPropfind[args.path];

        var path = args.path;

        var directory = null;
        var listing = [];
        if (success && result && !Ext.isArray(result.records))
            success = false;
        if (success)
        {
            var records = result.records;
            for (var r=0 ; r<records.length ; r++)
            {
                var record = records[r];
                if (record.data.path == path)
                    directory = record;
                else
                    listing.push(record);
            }
            if (directory)
                this._updateRecord(directory);
            this._addFiles(path, listing);
        }

        var callbacks = args.callbacks;
        for (var i=0 ; i<callbacks.length ; i++)
        {
            var callback = callbacks[i];
            if (typeof callback == "function")
                callback(this, success, path, listing);
        }
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
    this.transferReader = new Ext.data.JsonReader({totalProperty:'recordCount', root:'records', id:'uri'}, this.AppletRecord),

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

    AppletRecord : Ext.data.Record.create(
    [
        {name:'uri'},
        {name:'path'},
        {name:'name'},
        {name:'file', mapping:'isFile'},
        {name:'size', mapping:'length'},
        {name:'modified', mapping:'lastModified'}
    ]),

    transferReader : null,

    createDirectory : function(path, callback)
    {
        // UNDONE:
        alert("NYI");
    },

    reloadFiles : function(directory, callback, scope)
    {
        var applet = this.getDropApplet();
        if (!applet)
        {
            this.retry++;
            this.reloadFiles.defer(100, this, [directory, callback, scope]);
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
        var json = eval("var $=" + js + ";$;");
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
            callback.defer(1, scope||this, [this, true, directory, records]);
        return true;
    }
});





//
// FileStore
//

var FileStore = Ext.extend(Ext.data.Store,
{
    constructor : function(config)
    {
        FileStore.superclass.constructor.call(this,config);
        this.setDefaultSort("name","ASC");
    },

    sortData : function(f, direction)
    {
        direction = direction || 'ASC';
        var st = this.fields.get(f).sortType;
        var d = direction=="DESC" ? -1 : 1;
        var fn = function(r1, r2)
        {
            if (r1.data.file != r2.data.file)
                return d * (r1.data.file ? 1 : -1);
            var v1 = st(r1.data[f]), v2 = st(r2.data[f]);
            return v1 > v2 ? 1 : (v1 < v2 ? -1 : 0);
        };
        this.data.sort(direction, fn);
        if (this.snapshot && this.snapshot != this.data)
        {
            this.snapshot.sort(direction, fn);
        }
    }
});




//
// FileListMenu
//
var FileListMenu = function(fileSystem, path, folderFilter, fn)
{
    FileListMenu.superclass.constructor.call(this, {items:[], cls:'extContainer'});
    this.showFiles = false;
    this.fileSystem = fileSystem;
    this.path = path;
    this.folderFilter = folderFilter;

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

                else if (this.folderFilter && !this.folderFilter.test(data))
                    continue;

                this.addMenuItem(new Ext.menu.Item({text:data.name, icon:data.iconHref, path:record.data.path}));
            }
        };
    if (records)
        populate.call(this, null, true, path, records);
    else
        fileSystem.listFiles(path, populate, this);
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

    this.fileSystem.on(FILESYSTEM_EVENTS.listfiles, this.onListfiles, this)
};

Ext.extend(FileSystemTreeLoader, Ext.tree.TreeLoader,
{
    debugTree : null,

    fileFilter : null,
    folderFilter : null,
    displayFiles: true,
    url: true, // hack for Ext.tree.TreeLoader.load()

    requestData : function(node, callback)
    {
        if (this.fireEvent("beforeload", this, node, callback) !== false)
        {
            var args = {node:node, callback:callback};
            this.fileSystem.listFiles(node.id, this.listCallback.createDelegate(this, [args], true));
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


    // on refresh we want to keep the tree in sync
    onListfiles : function(filesystem, path, records)
    {
        var dir = this.fileSystem.recordFromCache(path);

        // just return for nodes we haven't loaded yet
        if (!dir || !dir.treeNode || !dir.treeNode.loaded)
            return;

        if (this.debugTree)
        {
            var n = this.debugTree.getNodeById(path);
            if (n && n !== dir.treeNode)
                console.warn("node is not in tree: " + path);
        }

        var reload = this.mergeRecords(dir.treeNode, records);
        // UNDONE: I don't know why removeChild() is not working!
        if (reload)
            dir.treeNode.reload();
    },
    

    mergeRecords : function(node, records)
    {
        var i, p, n, r;
        var nodesMap = {}, recordsMap = {};
        node.eachChild(function(child){nodesMap[child.record.data.path]=child;});
        for (i=0 ; i<records.length ; i++)
        {
            var record = records[i];
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
                node.removeChild(nodesMap[p]);
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
        return removes;
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
            this.mergeRecords(node, records);

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
    },

    ancestry : function(id, root)
    {
        var path = id;
        var a = [path];
        while (true)
        {
            var parent = this.fileSystem.parentPath(path);
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

var TRANSFER_EVENTS = {update:'update'};
var TRANSFER_STATES = {
    success:1,
    info:0,
    failed:-1,
    retryable:-2
}

var TransferApplet;
if (LABKEY.Applet)
{
    TransferApplet = Ext.extend(LABKEY.Applet,
    {
        constructor : function(params)
        {
            var callbackname = Ext.id(null, 'transferapplet');

            var config =
            {
                id: params.id,
                archive: LABKEY.contextPath + '/_applets/applets-' + LABKEY.versionString + '.jar',
                code: 'org.labkey.applets.drop.DropApplet',
                width: params.width,
                height: params.height,
                params:
                {
                    url : params.url || (window.location.protocol + "//" + window.location.host + LABKEY.contextPath + '/_webdav/'),
                    events : "window._evalContext.",
                    webdavPrefix: LABKEY.contextPath+'/_webdav/',
                    user: LABKEY.user.email,
                    password: LABKEY.user.sessionid,
                    text : params.text,
                    allowDirectoryUpload : params.allowDirectoryUpload,
                    overwrite : "true",
                    enabled : !('enabled' in params) || params.enabled,
                    dropFileLimit : 5000
                }
            };
            TransferApplet.superclass.constructor.call(this, config);
            console.log("TransferApplet.url: " + config.params.url);
        },

        initComponent : function()
        {
            TransferApplet.superclass.initComponent.call(this);

            this.addEvents([TRANSFER_EVENTS.update]);

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

        onRender : function(ct, position)
        {
            TransferApplet.superclass.onRender.call(this, ct, position);
            // callbacks work terribly on some browsers, just poll for updates
            Ext.TaskMgr.start({interval:100, scope:this, run:this._poll});
        },

        transferReader : null,
        transfers : null,

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


        TransferRecord : Ext.data.Record.create([
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
                case TRANSFER_STATES.success: success++; break;
                case TRANSFER_STATES.info: info++; break;
                case TRANSFER_STATES.failed: failed++; break;
                case TRANSFER_STATES.retryable: retryable++; break;
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
                    if (level >= ERROR_INT)
                        console.error(text);
                    else if (level >= WARN_INT)
                        console.warn(text);
                    else if (level >= INFO_INT)
                        console.info(text);
                    else
                        console.debug(text);
                }
            }

            if (updated)
                this.fireEvent(TRANSFER_EVENTS.update);
        }
    });
}


///
// TOOLBAR
//

// ToolBar.TextItem is sort of broken
var _TextItem = function(config)
{
    if (typeof config == 'string')
        config = {text:config};
    Ext.apply(this,config);
    if (config.id)
        Ext.ComponentMgr.register(this);
};
Ext.extend(_TextItem, Ext.Toolbar.Item,
{
    hidden:false,
    enable:Ext.emptyFn,
    disable:Ext.emptyFn,
    focus:Ext.emptyFn,
    setText : function(text)
    {
        this.text = text;
        this._update();
    },
    last : {width:0, text:null},
    _update : function()
    {
        if (!this.dom)
            return;
        if (this.last.width == this.width && this.last.text == this.text)
            return;
        var w = Ext.Element.addUnits(this.width,"px");
        var html = [{tag:'img', src:(LABKEY.contextPath + '/_.gif'), width:w, height:1}, "<br>", this.text ? Ext.util.Format.htmlEncode(this.text) : '&nbsp;'];
        Ext.fly(this.dom).setSize(w).update($dom.markup(html));
        Ext.fly(this.td).setSize(w);
        this.last.width = this.width;
        this.last.text = this.text;
    },
    render:function(td)
    {
        this.dom = $dom.append(td, {id:this.id, className:'ytb-text'});
        this.td = td;
        Ext.fly(this.td).addClass('x-status-text-panel');
        this._update();
    },
    setSize:function(w,h)
    {
        if (typeof w == 'object')
            w = w.width;
        this.width = w;
        this._update();
    }
});


//
//  FILE BROWSER UI
//

/**
 * A version of a tab panel that doesn't render the tab strip, used to swap
 * in panels programmatically
 * @param w
 * @param h
 */
LABKEY.TinyTabPanel = Ext.extend(Ext.TabPanel, {

    adjustBodyWidth : function(w){
        if(this.header){
            this.header.setWidth(w);
            this.header.setDisplayed(false);
        }
        if(this.footer){
            this.footer.setWidth(w);
            this.footer.setDisplayed(false);
        }
        return w;
    }
});

var BROWSER_EVENTS = {selectionchange:"selectionchange", directorychange:"directorychange", doubleclick:"doubleclick",  transferstarted:'transferstarted', transfercomplete:'transfercomplete'};


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
LABKEY.FileBrowser = Ext.extend(Ext.Panel,
{
    FOLDER_ICON: LABKEY.contextPath + "/" + LABKEY.extJsRoot + "/resources/images/default/tree/folder.gif",

    // instance/config variables
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

    // collapsible tab panel used to display dialog-like content
    fileUploadPanel : undefined,

    // file upload form field
    fileUploadField : undefined,

    tbarItems : undefined,

    fileFilter : undefined,

    //
    // actions
    //

    actions : {},
    //tbar : null,

    getDownloadAction : function()
    {
        return new Ext.Action({tooltip: 'Download the selected files or folders', iconCls:'iconDownload', scope: this, handler: function()
            {
                var selections = this.grid.selModel.getSelections();

                if (selections.length == 1 && selections[0].data.file)
                {
                    if (this.selectedRecord.data.uri)
                    {
                        window.location = this.selectedRecord.data.uri + "?contentDisposition=attachment";
                    }
                }
                else if (fileBrowser.currentDirectory.data.uri)
                {
                    var url = fileBrowser.currentDirectory.data.uri + "?method=zip&depth=-1";
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
        return new Ext.Action({iconCls:'iconFolderNew', tooltip: 'Create a new folder on the server', scope: this, handler: function()
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
        {
            this.selectFile(path);
            var record = this.fileSystem.recordFromCache(this.currentDirectory.data.path);
            if (record && record.treeNode)
                record.treeNode.reload();
            else if (this.currentDirectory.data.path == this.fileSystem.rootPath)
                this.root.reload();
        }
    },


    _refreshOnCallback : function(fs, success, path)
    {
        if (this.actions.refresh)
            this.actions.refresh.execute();
    },


    getParentFolderAction : function()
    {
        return new Ext.Action({tooltip: 'Navigate to parent folder', iconCls:'iconUp', scope: this, handler: function()
        {
            // CONSIDER: PROPFIND to ensure this link is still good?
            var p = this.currentDirectory.data.path;
            var dir = this.fileSystem.parentPath(p) || this.fileSystem.rootPath;
            this.changeDirectory(dir);
        }});
    },


    getRefreshAction : function()
    {
        return new Ext.Action({tooltip: 'Refresh the contents of the current folder', iconCls:'iconReload', scope:this, handler: this.refreshDirectory});
    },


    getZipFolderAction : function()
    {
        return new Ext.Action({text: 'Zip Folder', tooltip: 'Download folder as a .zip file', iconCls:'iconZip', scope: this, handler: function()
        {
            var uri = this.currentDirectory.data.uri;
            window.location = uri + "?method=zip";
        }});
    },


    // UNDONE: use uri or path as id
    _deleteOnCallback : function(fs, success, path, response, record)
    {
        //this.store.remove(record);
        var id = record.id;
        var rem = this.store.getById(id);
        if (rem)
            this.store.remove(rem);
    },

    getDeleteAction : function()
    {
        return new Ext.Action({tooltip: 'Delete the selected files or folders', iconCls:'iconDelete', scope:this, disabled:true, handler: function()
        {
            if (!this.currentDirectory || !this.selectedRecord)
                return;

            var selections = this.grid.selModel.getSelections();
            var fnDelete = (function()
            {
                for (var i = 0; i < selections.length; i++)
                {
                    var selectedRecord = selections[i];
                    this.fileSystem.deletePath(selectedRecord.data.path, this._deleteOnCallback.createDelegate(this,[selectedRecord],true));
                    this.selectFile(null);
                    if (this.tree)
                    {
                        var node = this.tree.getNodeById(this.currentDirectory.data.path);
                        if (node)
                        {
                            // If the user deleted a directory, we need to clean up the folder tree
                            if (!selectedRecord.data.file)
                            {
                                var parentNode;
                                var childNodeToRemove;
                                // The selection might be from the tree, or from the file list
                                if (node.attributes.path == selectedRecord.data.path)
                                {
                                    // If the path of the selected node in the tree matches what we deleted, use that node
                                    parentNode = node.parentNode;
                                    childNodeToRemove = node;
                                }
                                else
                                {
                                    // Otherwise, the selection came from the list and we need to find the right child
                                    // node in the tree
                                    childNodeToRemove = node.findChild("path", selectedRecord.data.path);
                                    if (childNodeToRemove)
                                    {
                                        parentNode = node;
                                    }
                                }
                                // Check if we found the child and parent nodes in the tree
                                if (parentNode && childNodeToRemove)
                                {
                                    // Want to make sure that the parent gets selected after the child is deleted
                                    parentNode.select();
                                    // Remove the child from the tree since it's been deleted from the disk
                                    parentNode.removeChild(childNodeToRemove);
                                }
                            }
                            else
                            {
                                node.reload();
                            }
                        }
                    }
                }

                // Refresh the list view to make sure that we get rid of the deleted entry there too
                this.refreshDirectory();

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


    progressRecord : null,

    updateProgressBarRecord : function(store,record)
    {
        var state = record.get('state');
        var progress = (record.get('percent')||0)/100;

        if (state == 0 && 0 < progress && progress < 1.0)
            this.progressRecord = record;
        else if (state != 0 && this.progressRecord == record)
            this.progressRecord = null;
    },


    lastSummary: {info:0, success:0, file:'', pct:0},

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
//                Ext.getCmp('appletStatusFiles').setText('');
            }
            else
            {
                this.appletStatusBar.busyText = 'Copying... ' + summary.info + ' file' + (summary.info>1?'s':'');
                this.appletStatusBar.showBusy();
//                Ext.getCmp('appletStatusFiles').setText('' + summary.info);
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


    notifyStates : {},

    //private
    fireUploadEvents : function()
    {
        var transfers = this.applet.getTransfers();
        var count = transfers.getCount();
        var incompleteCount = 0;
        var newlyAdded = [];
        var newlyComplete = [];

        //Try to notify in batches.
        //If new files are added fire a transferstarted event
        //If all started files are finished, file a transfercomplete event for all of them
        
        for (var i = 0 ; i<count ; i++)
        {
            var record = transfers.getAt(i);
            var id = new URI(record.id).pathname; //Normalize paths...
            var name = record.get("name");
            var state = record.get("state");

            var notifyInfo =  this.notifyStates[id];
            if (null == notifyInfo)
            {
                notifyInfo = {current:state, notified:null, name:name};
                if (state == TRANSFER_STATES.info || state == TRANSFER_STATES.success)
                    newlyAdded.push({id:id, name:name});

                if (state == TRANSFER_STATES.success)
                    newlyComplete.push({id:id, name:name});

                this.notifyStates[id] = notifyInfo;
            }
            else
            {
                notifyInfo.current = state;
                if (state == TRANSFER_STATES.success && notifyInfo.notified != TRANSFER_STATES.success)
                    newlyComplete.push({id:id, name:name});
            }

            if (state == TRANSFER_STATES.info)
                incompleteCount++;
        }

        if (newlyAdded.length != 0)
        {
            this.fireEvent(BROWSER_EVENTS.transferstarted, {uploadType:"applet", files:newlyAdded});
            for (var i = 0; i < newlyAdded.length; i++)
                this.notifyStates[newlyAdded[i].id].notified = TRANSFER_STATES.info;
        }

        //If all complete, notify all the completed guys at once
        if (incompleteCount == 0 && newlyComplete.length > 0)
        {
            this.fireEvent(BROWSER_EVENTS.transfercomplete, {uploadType:"applet", files:newlyComplete});
            for (var i = 0; i < newlyComplete.length; i++)
                this.notifyStates[newlyComplete[i].id].notified = TRANSFER_STATES.success;
        }
    },

    getUploadToolAction : function()
    {
        return new Ext.Action({text: 'Multi-file Upload', tooltip:"Upload multiple files or folders using drag-and-drop<br>(requires Java)", scope:this, disabled:true, handler: function()
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
                    parent = this.fileSystem.parentPath(currPath);
                    if (parent && currPath && !this.fileSystem.recordFromCache(currPath))
                        parents.push(parent);
                    currPath = parent;
                } while (currPath && currPath != '/');
                var cb = function(filesystem,success,parentPath,records)
                {
                    if (parents.length)
                        this.fileSystem.listFiles(parents.pop(), cb, this);
                    else
                    {
                       record = this.fileSystem.recordFromCache(fullPath);
                       if (record)
                           this.changeDirectory(record);
                        return;
                    }
                };
                if (parents.length)
                    this.fileSystem.listFiles(parents.pop(), cb, this);
                return;
            }
        }

        if (record && !record.data.file && (force || this.currentDirectory != record))
        {
            this.currentDirectory = record;
            if (this.statePrefix)
                Ext.state.Manager.set(this.statePrefix+'.currentDirectory', record.data.path);
            this.fireEvent(BROWSER_EVENTS.directorychange, record);
       }
    },


    refreshDirectory : function()
    {
        if (!this.currentDirectory)
            return;
        this.fileSystem.uncacheListing(this.currentDirectory);
        this.changeDirectory(this.currentDirectory, true);
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
                }, this);
            }
        }
        if (this.selectedRecord && record && this.selectedRecord.data.path == record.data.path)
            return;
        if (!this.selectedRecord && !record)
            return;
        this.selectedRecord = record || this.currentDirectory;
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
        this.fireEvent(BROWSER_EVENTS.selectionchange, record);
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
        }
        this.grid.focus();
        this.fireEvent(BROWSER_EVENTS.doubleclick, record);
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
        this.fileSystem.getHistory(path, this.history.createDelegate(this));
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
            text = '';
            //text = $h(this.fileSystem.rootRecord.data.name);
        else
            text = 'folder: ' + $h(path);
        el.update('<table height=100% width=100%><tr><td height=100% width=100% valign=middle align=left>' + text + '</td></tr></table>');
        this.addressBarHandler = this.showFileListMenu.createDelegate(this, [path]);
        el.on("click", this.addressBarHandler);
    },

    showFileListMenu : function(path)
    {
        var el = $('addressBar');
        var menu = new FileListMenu(this.fileSystem, path, this.fileFilter, this.changeDirectory.createDelegate(this));
        menu.show(el);
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
                html.push(":</th><td style='padding-left: 5px'>");
                html.push(value);
                html.push("</td></tr>");
            };

            html.push("<table style='padding-left:30px;'>");
            row("Name", data.name);
            row("WebDav URL", data.uri);
            if (data.modified)
                row("Date modified", _longDateTime(data.modified));
            if (data.file)
                {
                    var sizeValue = Ext.util.Format.fileSize(data.size);
                    if (data.size > 1024)
                    {
                        sizeValue += " (" + formatWithCommas(data.size) + " bytes)";
                    }
                    row("Size", sizeValue);
                }
            if (data.createdBy && data.createdBy != 'Guest')
                row("Created By", $h(data.createdBy));
            if (startsWith(data.contentType,"image/"))
            {
                row("Dimensions","<span id=detailsImgSize></span>");
                var image = new Image();
                image.onload = function()
                {
                    $('detailsImgSize').update(image.width + "x" + image.height);
                };
                image.src = data.uri;
            }
//            html.push('<tr><td colspan=2 style="color:#404040;">');
//            html.push($h(data.uri));
//            html.push('</td></tr>');
//
            html.push('</table>');
            el.update(html.join(""));
        }
        catch (x)
        {
            log(x);
        }
    },


    // UNDONE: move part of this into FileStore?
    loadRecords : function(filesystem, success, path, records)
    {
        var i, len;
        if (success && this.currentDirectory.data.path == path)
        {
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
        }
    },


    start : function(wd)
    {
        if (!this.fileSystem.ready)
        {
            this.fileSystem.onReady(this.start.createDelegate(this, [wd]));
            return;
        }
        if (this.tree)
        {
            this.tree.getRootNode().expand();
        }
        if (typeof wd == "string")
        {
            this.changeDirectory(wd);
            this.selectFile(wd);
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
        var uri = new URI(this.fileSystem.prefixUrl);  // implementation leaking here
        var url = uri.toString();
        this.applet = new TransferApplet({url:url, directory:this.currentDirectory.data.path});
        this.progressBar = new Ext.ProgressBar({id:'appletStatusProgressBar'});

        var toolbar = new Ext.Toolbar({buttons:[
            this.actions.appletFileAction, this.actions.appletDirAction
        ]});
        this.appletStatusBar = new Ext.StatusBar({id:'appletStatusBar', defaultText:'Ready', busyText:'Copying...', items:[
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


        this.applet.on(TRANSFER_EVENTS.update, this.updateProgressBar, this);
        this.applet.on(TRANSFER_EVENTS.update, this.fireUploadEvents, this);
        this.applet.getTransfers().on(STORE_EVENTS.update, this.updateProgressBarRecord, this);

        // make sure that the applet still matches the current directory when it appears
        this.applet.onReady(function()
        {
            this.updateAppletState(this.currentDirectory);
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
        if (this.actions.appletDirAction)
            this.actions.appletDirAction[canMkdir?'enable':'disable']();
        try
        {
            this.applet.changeWorkingDirectory(record.data.path);
            if (canWrite || canMkdir)
            {
                this.applet.setEnabled(true);
                this.applet.setAllowDirectoryUpload(canMkdir);
                this.applet.setText( (canMkdir ? "Drop files and folders here" : "Drop files here")); // + "\nFolder: " +record.data.name);
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

    constructor : function(config)
    {
        config = config || {};
        Ext.apply(this, config);
        this.actions = Ext.apply({}, this.actions,
        {
            parentFolder: this.getParentFolderAction(),
            refresh: this.getRefreshAction(),
            createDirectory: this.getCreateDirectoryAction(),
            download: this.getDownloadAction(),
            deletePath: this.getDeleteAction(),
            help: this.getHelpAction(),
            //drop : this.getOldDropAction(),
            showHistory : this.getShowHistoryAction(),
            uploadTool: this.getUploadToolAction()
        });
        delete config.actions;  // so superclass.constructor doesn't overwrite this.actions

        // add any other actions
        this.initializeActions();

        var me = this; // for anonymous inner functions

        //
        // GRID
        //

        this.store = new FileStore({recordType:this.fileSystem.FileRecord});
        this.grid = this.createGrid();

        this.grid.getSelectionModel().on(ROWSELETION_EVENTS.rowselect, this.Grid_onRowselect, this);
        this.grid.getSelectionModel().on(ROWSELETION_EVENTS.selectionchange, this.Grid_onSelectionChange, this);
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

        Ext.apply(config, {layout:'border', tbar:tbarConfig, items: layoutItems, renderTo:null}, {id:'fileBrowser', height:600, width:800});
        LABKEY.FileBrowser.superclass.constructor.call(this, config);
    },

    initComponent : function()
    {
        LABKEY.FileBrowser.superclass.initComponent.call(this);

        //
        // EVENTS (tie together components)
        //
        this.addEvents( [ BROWSER_EVENTS.selectionchange, BROWSER_EVENTS.directorychange, BROWSER_EVENTS.doubleclick, BROWSER_EVENTS.transferstarted, BROWSER_EVENTS.transfercomplete ]);


        this.on(BROWSER_EVENTS.selectionchange, function()
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

            if (selections.length == 1 && startsWith(record.data.uri,"http"))
            {
                this.actions.download.enable();
            }
            else if (selections.length > 0 && startsWith(this.currentDirectory.data.uri,"http"))
            {
                this.actions.download.enable();
            }
            else
            {
                this.actions.download.disable();
            }

            if (record && this.fileSystem.canDelete(record))
                this.actions.deletePath.enable();
            else
                this.actions.deletePath.disable();
        }, this);


        // actions
        this.on(BROWSER_EVENTS.directorychange, function(record)
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
        this.on(BROWSER_EVENTS.directorychange,function(record)
        {
            // data store
            this.store.removeAll();
            this.grid.view.scrollToTop();
            this.fileSystem.listFiles(record.data.path, this.loadRecords, this);

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
        }, this);


        // applet
        this.on(BROWSER_EVENTS.directorychange, this.updateAppletState, this);

        this.on(BROWSER_EVENTS.transfercomplete, function(result) {
            this.hideProgressBar();
            this.refreshDirectory();
        }, this);

        this.on(BROWSER_EVENTS.transferstarted, function(result) {this.showProgressBar();}, this);
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
        LABKEY.FileBrowser.superclass.onRender.apply(this, arguments);

        if (this.resizable)
        {
            this.resizer = new Ext.Resizable(this.el, {pinned: false});
            this.resizer.on("resize", function(o, width, height){
                this.setWidth(width);
                this.setHeight(height)
            }, this);
        }
    },

    getTbarConfig : function()
    {
        var tbarConfig = [];
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
                    tbarConfig.push(this.actions[a]);
        }
        else
        {
            for (var i=0 ; i<this.tbarItems.length ; i++)
            {
                var item = this.tbarItems[i];
                if (typeof item == "string" && typeof this.actions[item] == "object")
                    tbarConfig.push(this.actions[item]);
                else
                    tbarConfig.push(item);
            }
        }
        return tbarConfig;
    },

    /**
     * Initialize additional actions and components
     */
    initializeActions : function()
    {
        this.actions.upload = new Ext.Action({
            text: 'Upload Files',
            iconCls: 'iconUpload',
            tooltip: 'Upload files or folders from your local machine to the server',
            listeners: {click:function(button, event) {this.toggleTabPanel('uploadFileTab');}, scope:this}
        });

        this.actions.appletFileAction = new Ext.Action({
            text:'Choose File...', scope:this, disabled:false, iconCls:'iconFileNew',
            handler:function(){
                if (this.applet)
                {
                    var a = this.applet.getApplet();
                    if (a) a.showFileChooser();
                }
            }
        });

        this.actions.appletDirAction = new Ext.Action({
            text:'Choose Folder...', scope:this, disabled:false,  iconCls:'iconFileOpen',
            handler:function(){
                if (this.applet)
                {
                    var a = this.applet.getApplet();
                    if (a) a.showDirectoryChooser();
                }
            }
        });

        this.actions.folderTreeToggle = new Ext.Action({
            iconCls: 'iconFolderTree',
            tooltip: 'Show or hide the folder tree',
            listeners: {click:function(button, event) {
                if (this.tree) {
                    if (this.tree.isVisible()) this.tree.collapse();
                    else                       this.tree.expand();
                }
            }, scope:this}
        })
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
            this.fileUploadField = new Ext.form.FileUploadField(
            {
                id: this.id ? this.id + 'Upload' : 'fileUpload',
                buttonText: "Browse...",
                fieldLabel: 'Choose a file'
            });

            var uploadPanel_rb1 = new Ext.form.Radio({
                style: 'background-color:#f0f0f0;',
                boxLabel: 'Single file', name: 'rb-auto', inputValue: 1, checked: true
            });
            var uploadPanel_rb2 = new Ext.form.Radio({
                boxLabel: 'Multiple files', name: 'rb-auto', inputValue: 2,
                listeners:{check:function(button, checked) {
                    if (checked)
                        this.onMultipleFileUpload();
                }, scope:this}
            });

            var uploadPanel = new Ext.FormPanel({
                id: 'uploadFileTab',
                formId : this.id ? this.id + 'Upload-form' : 'fileUpload-form',
                method : 'POST',
                fileUpload: true,
                enctype:'multipart/form-data',
                border:false,
                bodyStyle : 'background-color:#f0f0f0; padding:10px;',
                items: [
                    {
                    xtype: 'radiogroup',
                    fieldLabel: 'File Upload Type',
                    width: 200,
                    groupCls: 'labkey-transparent-panel',
                    items: [
                        uploadPanel_rb1,
                        uploadPanel_rb2
                    ]},
                    this.fileUploadField,
                    {xtype: 'textfield', name: 'description', fieldLabel: 'Description', width: 350}
                ],
                buttons:[
                    {text: 'Submit', handler:this.submitFileUploadForm, scope:this},
                    {text: 'Close', listeners:{click:function(button, event) {this.toggleTabPanel('uploadFileTab');}, scope:this}}
                ],
                listeners: {
                    "actioncomplete" : {fn: this.uploadSuccess, scope: this},
                    "actionfailed" : {fn: this.uploadFailed, scope: this}
                }
            });
            uploadPanel.on('beforeshow', function(c){uploadPanel_rb1.setValue(true); uploadPanel_rb2.setValue(false);}, this);

            var uploadMultiPanel_rb1 = new Ext.form.Radio({
                boxLabel: 'Single file', name: 'rb-auto', inputValue: 1,
                listeners:{check:function(button, checked) {
                    if (checked)
                        this.toggleTabPanel('uploadFileTab');
                }, scope:this}
            });
            var uploadMultiPanel_rb2 = new Ext.form.Radio({
                boxLabel: 'Multiple files', name: 'rb-auto', inputValue: 2, checked: true
            });

            this.progressBar = new Ext.ProgressBar({id:'appletStatusProgressBar'});
            this.appletStatusBar = new Ext.StatusBar({
                id:'appletStatusBar', defaultText:'', busyText:'Copying...',
                width: 200,
                hidden: true,
                statusAlign: 'right',
                style : 'background-color:#f0f0f0;',
                items:[{
                    xtype:'panel', layout:'fit', border:false, items:this.progressBar, width:120, minWidth:120
                }]
            });

            this.appletPanel = new Ext.Panel({
                fieldLabel: 'File and Folder Drop Target',
                isFormField: true,
                height: 60,
                width: 325
            });

            var uploadMultiPanel = new Ext.FormPanel({
                id: 'uploadMultiFileTab',
                layout: 'form',
                border:false,
                bodyStyle : 'background-color:#f0f0f0; padding:10px;',
                items: [{
                    xtype: 'radiogroup',
                    width: 200,
                    groupCls: 'labkey-transparent-panel',
                    fieldLabel: 'File Upload Type',
                    items: [
                        uploadMultiPanel_rb1,
                        uploadMultiPanel_rb2
                    ]},
                    this.appletPanel
                ],
                buttons:[
                    new Ext.Button(this.actions.appletFileAction),
                    new Ext.Button(this.actions.appletDirAction),
                    {text: 'Close', listeners:{click:function(button, event) {this.toggleTabPanel('uploadMultiFileTab');}, scope:this}},
                    this.appletStatusBar
                ]
            });
            uploadMultiPanel.on('beforeshow', function(c){uploadMultiPanel_rb1.setValue(false); uploadMultiPanel_rb2.setValue(true);}, this);

            this.fileUploadPanel = new LABKEY.TinyTabPanel({
                region: 'north',
                collapseMode: 'mini',
                height: 130,
                header: false,
                margins:'0 0 0 0',
                border: false,
                bodyStyle: 'background-color:#f0f0f0;',
                cmargins:'0 0 0 0',
                collapsible: true,
                collapsed: true,
                hideCollapseTool: true,
                activeTab: 'uploadFileTab',
                deferredRender: false,
                stateful: false,
                items: [
                    uploadPanel,
                    uploadMultiPanel
                ]});

            layoutItems.push(this.fileUploadPanel);
        }

        var addressBar = {
            region: 'south',
            height: 24,
            margins: '0 0 5 5',
            layout: 'fit',
            border: true,
            stateful: false,
            items: [{id:'addressBar', html: '<div style="background-color:#f0f0f0;height:100%;width:100%">&nbsp</div>'}]};

        if (this.showDetails)
        {
            var detailItems = [];
            var panelHeight = 80;
            var layout = 'fit';

            if (this.showAddressBar)
            {
                addressBar.margins = '0';
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
            this.tree.region='west';
            this.tree.split=true;
            this.tree.width = 200;
            layoutItems.push(this.tree);
        }

        var centerItems = [];

        centerItems.push({region:'center', layout:'fit', border:false, items:[this.grid]});
        layoutItems.push(
            {
                region:'center',
                margins: '0 ' + (this.propertiesPanel ? '0' : '0') + ' ' + (this.showDetails ? '0' : '0') + ' 0',
                minSize: 200,
                layout:'border',
                items: centerItems
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
            var form = this.fileUploadPanel.getActiveTab().getForm();
            var path = this.fileUploadField.getValue();
            var i = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
            var name = path.substring(i+1);
            var target = this.fileSystem.concatPaths(this.currentDirectory.data.path,name);
            var file = this.fileSystem.recordFromCache(target);
            if (file)
            {
                alert('file already exists on server: ' + name);
            }
            else
            {
                var options = {method:'POST', url:this.currentDirectory.data.uri, record:this.currentDirectory, name:this.fileUploadField.getValue()};
                // set errorReader, so that handleResponse() doesn't try to eval() the XML response
                // assume that we've got a WebdavFileSystem
                form.errorReader = this.fileSystem.transferReader;
                form.doAction(new Ext.form.Action.Submit(form, options));
                this.fireEvent(BROWSER_EVENTS.transferstarted, {uploadType:"webform", files:[{name:name, id:new URI(this.fileSystem.concatPaths(options.url, path)).pathname}]});
                Ext.getBody().dom.style.cursor = "wait";
            }
        }
    },

    // handler for a file upload complete event
    uploadSuccess : function(f, action)
    {
        this.fileUploadField.reset();
        Ext.getBody().dom.style.cursor = "pointer";
        console.log("upload actioncomplete");
        console.log(action);
        var options = action.options;
        // UNDONE: update data store directly
        //this.toggleTabPanel();
        this.refreshDirectory();
        this.selectFile(this.fileSystem.concatPaths(options.record.data.path, options.name));
        this.fireEvent(BROWSER_EVENTS.transfercomplete, {uploadType:"webform", files:[{name:options.name, id:new URI(this.fileSystem.concatPaths(options.record.data.uri, options.name)).pathname}]});
    },

    // handler for a file upload failed event
    uploadFailed : function(f, action)
    {
        this.fileUploadField.reset();
        Ext.getBody().dom.style.cursor = "pointer";
        console.log("upload actionfailed");
        console.log(action);
        this.refreshDirectory();
    },

    toggleTabPanel : function(tabId)
    {
        if (!tabId)
            this.fileUploadPanel.collapse();

        if (this.fileUploadPanel.isVisible())
        {
            var activeTab = this.fileUploadPanel.getActiveTab();

            if (activeTab && activeTab.getId() == tabId)
                this.fileUploadPanel.collapse();
            else
                this.fileUploadPanel.setActiveTab(tabId);
        }
        else
        {
            this.fileUploadPanel.setActiveTab(tabId);
            this.fileUploadPanel.expand();
        }
    },

    onMultipleFileUpload : function()
    {
        this.hideProgressBar();
        this.lastSummary= {info:0, success:0, file:'', pct:0};
        this.progressRecord = null;
        this.toggleTabPanel('uploadMultiFileTab');
        if (!this.applet)
        {
            var uri = new URI(this.fileSystem.prefixUrl);  // implementation leaking here
            var url = uri.toString();
            this.applet = new TransferApplet({url:url, directory:this.currentDirectory.data.path});

            this.applet.on(TRANSFER_EVENTS.update, this.updateProgressBar, this);
            this.applet.on(TRANSFER_EVENTS.update, this.fireUploadEvents, this);
            this.applet.getTransfers().on(STORE_EVENTS.update, this.updateProgressBarRecord, this);

            // make sure that the applet still matches the current directory when it appears
            this.applet.onReady(function()
            {
                this.updateAppletState(this.currentDirectory);
            }, this);

            this.appletPanel.add(this.applet);
            this.appletPanel.doLayout();
        }
        else
        {
            var task = {
                interval:100,
                scope: this,
                run : function()
                {
                    if (this.applet.isActive())
                    {
                        this.updateAppletState(this.currentDirectory);
                        this.applet.getTransfers().removeAll();
                        Ext.TaskMgr.stop(task);
                    }
                }
            };
            Ext.TaskMgr.start(task);
        }
    },

    createTreePanel : function()
    {
        var treeloader = new FileSystemTreeLoader({fileSystem: this.fileSystem, displayFiles:false, folderFilter:this.fileFilter});
        this.root = treeloader.createNodeFromRecord(this.fileSystem.rootRecord, this.fileSystem.rootPath);
        var tree = new Ext.tree.TreePanel(
        {
            loader:treeloader,
            root: this.root,
            rootVisible:true,
            //title: 'Folders',
            useArrows:true,
            margins: this.showDetails ? '5 0 0 5' : '5 0 5 5',
            autoScroll:true,
            animate:true,
            enableDD:false,
            containerScroll:true,
            collapsible: true,
            collapseMode: 'mini',
            collapsed: this.folderTreeCollapsed,
            cmargins:'0 0 0 0',
            border:false,
            stateful: false,
            pathSeparator:';'
        });
        treeloader.debugTree = tree;
        tree.getSelectionModel().on(TREESELECTION_EVENTS.selectionchange, this.Tree_onSelectionchange, this);
        window.DEBUGTREE = tree;
        return tree;
    },

    createGrid : function()
    {
        // mild convolution to pass fileSystem to the _attachPreview function
        var iconRenderer = renderIcon.createDelegate(null,_attachPreview.createDelegate(this.fileSystem,[],true),true);

        var grid = new Ext.grid.GridPanel(
        {
            store: this.store,
            border:false,
            selModel : new Ext.grid.RowSelectionModel({singleSelect:true}),
            loadMask:{msg:"Loading, please wait..."},
            columns: [
                {header: "", width:20, dataIndex: 'iconHref', sortable: false, hidden:false, renderer:iconRenderer},
                {header: "Name", width: 250, dataIndex: 'name', sortable: true, hidden:false, renderer:Ext.util.Format.htmlEncode},
//                {header: "Created", width: 150, dataIndex: 'created', sortable: true, hidden:false, renderer:renderDateTime},
                {header: "Last Modified", width: 150, dataIndex: 'modified', sortable: true, hidden:false, renderer:renderDateTime},
                {header: "Size", width: 80, dataIndex: 'size', sortable: true, hidden:false, align:'right', renderer:renderFileSize},
                {header: "Created By", width: 100, dataIndex: 'createdBy', sortable: true, hidden:false, renderer:Ext.util.Format.htmlEncode}
            ]
        });
        return grid;
    }
});

