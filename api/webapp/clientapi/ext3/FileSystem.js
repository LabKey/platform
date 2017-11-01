/*
 * Copyright (c) 2008-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

/**
 * A static class for interacting with files and filesystems
 * @name LABKEY.FileSystem
 * @ignore
 * @class
 */
Ext.ns('LABKEY.FileSystem');


/**
 *  A helper for manipulating URIs.  This is not part of the public API, so do not rely on its existence
 *  from parseUri 1.2.1
 *  (c) 2007 Steven Levithan <stevenlevithan.com>
 *  MIT License
 *  @ignore
 */
LABKEY.URI = Ext.extend(Object,
{
    constructor : function(u)
    {
        this.toString = function()
        {
            return this.protocol + "://" + this.host + this.pathname + this.search;
        };
        if (typeof u == "string")
            this.parse(u);
        else if (typeof u == "object")
            Ext.apply(this,u);

        this.options = Ext.apply({},this.options);  // clone
    },
    parse: function(str)
    {
        var	o   = this.options;
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
    },
    options:
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
    }
});

/**
 * Static map of events used internally by LABKEY.FileSystem
 * @memberOf LABKEY.FileSystem#
 * @ignore
 * @private
 */
LABKEY.FileSystem.FILESYSTEM_EVENTS = {
    ready: "ready",
    filesremoved: "filesremoved",
    fileschanged: "fileschanged"
};


/**
 * Returns a listing of all file roots available for a given folder
 * @memberOf LABKEY.FileSystem#
 * @param config Configuration properties.
 * @param {String} config.containerPath The container to test.  If null, the current container will be used.
 * @param {Function} config.success Success callback function.  It will be called with the following arguments:
 * <li>Results: A map linking the file root name to the WebDAV URL</li>
 * <li>Response: The XMLHttpRequest object containing the response data.</li>
 * @param {Function} [config.failure] Error callback function.  It will be called with the following arguments:
 * <li>Response: The XMLHttpRequest object containing the response data.</li>
 * <li>Options: The parameter to the request call.</li>
 * @param {Object} [config.scope] The scope for the callback function.  Defaults to 'this'
 */
LABKEY.FileSystem.getFileRoots = function(config){
    config = config || {};

    LABKEY.Ajax.request({
        url: LABKEY.ActionURL.buildURL('filecontent', 'getFileRoots', config.containerPath),
        scope: config.scope || this,
        success: LABKEY.Utils.getCallbackWrapper(config.success, config.scope),
        failure: LABKEY.Utils.getCallbackWrapper(config.failure, config.scope),
    });
}


/**
 * Static map of events used internally by LABKEY.FileSystem
 * @memberOf LABKEY.FileSystem#
 * @ignore
 * @private
 */
LABKEY.FileSystem.BROWSER_EVENTS = {
    selectionchange:"selectionchange",
    directorychange:"directorychange",
    doubleclick:"doubleclick",
    transferstarted:'transferstarted',
    transfercomplete:'transfercomplete',
    movestarted:'movestarted',
    movecomplete:'movecomplete',
    deletestarted:'deletestarted',
    deletecomplete:'deletecomplete'
};

LABKEY.FileSystem.FOLDER_ICON = LABKEY.contextPath + "/" + LABKEY.extJsRoot + "/resources/images/default/tree/folder.gif";


/**
 * Private heper methods used internally by LABKEY.Filesystem.
 * @ignore
 * @private
 */
LABKEY.FileSystem.Util = new function(){
    var $ = Ext.get, $h = Ext.util.Format.htmlEncode, $dom = Ext.DomHelper,
    imgSeed = 0,
    FATAL_INT = 50000,
    ERROR_INT = 40000,
    WARN_INT  = 30000,
    INFO_INT  = 20000,
    DEBUG_INT = 10000;

    function formatWithCommas(value)
    {
        var x = value;
        var formatted = (x == 0) ? '0' : '';
        var sep = '';
        while (x > 0)
        {
            // Comma separate between thousands
            formatted = sep + formatted;
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
            sep = ',';
        }
        return formatted;
    }

    return {
        startsWith : function(s, f){
            var len = f.length;
            if (s.length < len) return false;
            if (len == 0)
                return true;
            return s.charAt(0) == f.charAt(0) && s.charAt(len-1) == f.charAt(len-1) && s.indexOf(f) == 0;
        },

        endsWith : function(s, f){
            var len = f.length;
            var slen = s.length;
            if (slen < len) return false;
            if (len == 0)
                return true;
            return s.charAt(slen-len) == f.charAt(0) && s.charAt(slen-1) == f.charAt(len-1) && s.indexOf(f) == slen-len;
        },

        // minor hack call with scope having decorateIcon functions
        renderIcon : function(value, metadata, record, rowIndex, colIndex, store, decorateFN) {
            var file = record.get("file");
            if (!value)
            {
                if (!file)
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
            var img = {tag:'img', width:16, height:16, src:value, id:'img'+(++imgSeed)};
            if (decorateFN)
                decorateFN.defer(1,this,[img.id,record]);
            return $dom.markup(img);
        },

        renderFileSize : function(value, metadata, record, rowIndex, colIndex, store){
            if (!record.get('file')) return "";
            var f =  Ext.util.Format.fileSize(value);
            return "<span title='" + f + "'>" + formatWithCommas(value) + "</span>";
        },

        /* Used as a field renderer */
        renderUsage : function(value, metadata, record, rowIndex, colIndex, store){
            if (!value || value.length == 0) return "";
            var result = "<span title='";
            for (var i = 0; i < value.length; i++)
            {
                if (i > 0)
                {
                    result = result + ", ";
                }
                result = result + $h(value[i].message);
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
                    result = result + "<a href=\'" + $h(value[i].href) + "'>";
                }
                result = result + $h(value[i].message);
                if (value[i].href)
                {
                    result = result + "</a>";
                }
            }
            result = result + "</span>";
            return result;
        },

        renderDateTime : function(value, metadata, record, rowIndex, colIndex, store){
            if (!value) return "";
            if (value.getTime() == 0) return "";
            return "<span title='" + LABKEY.FileSystem.Util._longDateTime(value) + "'>" + LABKEY.FileSystem.Util. _rDateTime(value) + "<span>";
        },

        _longDateTime : Ext.util.Format.dateRenderer("l, F d, Y g:i:s A"),
        _rDateTime : Ext.util.Format.dateRenderer("Y-m-d H:i:s"),

        formatWithCommas : function(value) {
            return formatWithCommas(value);
        },

        _processAjaxResponse: function(response){
            if (response &&
                response.responseText &&
                response.getResponseHeader('Content-Type') &&
                response.getResponseHeader('Content-Type').indexOf('application/json') >= 0)
            {
                try
                {
                    response.jsonResponse = Ext.util.JSON.decode(response.responseText);
                    if(response.jsonResponse.status)
                        response.status = response.jsonResponse.status
                }
                catch (error){
                    //ignore
                }
            }
        }
    }
};

/**
 * This is a base class that is extended by LABKEY.FileSystem.WebdavFileSystem and others.  It is not intended to be used directly.
 * @class LABKEY.FileSystem.AbstractFileSystem
 * @name LABKEY.FileSystem.AbstractFileSystem
 * @param config Configuration properties.
 */

/**
 * The Ext.Record type used to store files in the fileSystem
 * @name FileRecord
 * @fieldOf LABKEY.FileSystem.AbstractFileSystem#
 * @description
 * The file record should contain the following fields:
    <li>uri (string, urlencoded)</li>
    <li>path (string, not encoded)</li>
    <li>name (string)</li>
    <li>file (bool)</li>
    <li>created (date)</li>
    <li>modified (date)</li>
    <li>size (int)</li>
    <li>createdBy(string, optional)</li>
    <li>modifiedBy(string, optional)</li>
    <li>iconHref(string, optional)</li>
    <li>actionHref(string, optional)</li>
    <li>contentType(string, optional)</li>
    <li>absolutePath(string, optional)</li>
 */
LABKEY.FileSystem.AbstractFileSystem = function(config){
    LABKEY.FileSystem.AbstractFileSystem.superclass.constructor.apply(this, arguments);
};

Ext.extend(LABKEY.FileSystem.AbstractFileSystem, Ext.util.Observable, {

    /**
     * Set to true if the fileSystem has loaded.
     * @type Boolean
     * @property
     * @memberOf LABKEY.FileSystem.AbstractFileSystem#
     */
    ready     : true,
    rootPath  : "/",
    separator : "/",
    directoryMap : {},

    constructor : function(config)
    {
        Ext.util.Observable.prototype.constructor.call(this);
        this.directoryMap = {};
        /**
         * @memberOf LABKEY.FileSystem.AbstractFileSystem#
         * @event
         * @name ready
         * @param {Filesystem} fileSystem A reference to the fileSystem
         * @description Fires when the file system has loaded.
         */
        /**
         * @memberOf LABKEY.FileSystem.AbstractFileSystem#
         * @event
         * @name fileschanged
         * @param {FileSystem} [fileSystem] A reference to the fileSystem.
         * @param {String} [path] The path that was changed.
         * @description Fires when the a path has been changed.
         */
        /**
         * @memberOf LABKEY.FileSystem.AbstractFileSystem#
         * @event
         * @name filesremoved
         * @param {FileSystem} [fileSystem] A reference to the fileSystem.
         * @param {Record[]} [records] An array of Ext.Record objects representing the files that were removed.  These can be files and/or directories.
         * @description Fires when one or more files or folders have been removed, either by a delete or move action.  It is not fired when files are uncached for other reasons.
         */
        this.addEvents(
            LABKEY.FileSystem.FILESYSTEM_EVENTS.filesremoved,
            LABKEY.FileSystem.FILESYSTEM_EVENTS.fileschanged,
            LABKEY.FileSystem.FILESYSTEM_EVENTS.ready
        );
    },

    /**
     * Will list all the contents of the supplied path.  If this path has already been loaded, the local cache will be used.
     * @param config Configuration properties.
     * @param {String} config.path The path to load
     * @param {Function} config.success Success callback function.  It will be called with the following arguments:
     * <li>Filesystem: A reference to the filesystem</li>
     * <li>Path: The path that was loaded</li>
     * <li>Records: An array of record objects</li>
     * @param {Function} [config.failure] Error callback function.  It will be called with the following arguments:
     * <li>Response: The XMLHttpRequest object containing the response data.</li>
     * <li>Options: The parameter to the request call.</li>
     * @param {Object} [config.scope] The scope for the callback functions
     * @param {Boolean} [config.forceReload] If true, the path will always be reloaded instead of relying on the cache
     * @methodOf LABKEY.FileSystem.AbstractFileSystem#
     */
    listFiles : function(config)
    {
        config.scope = config.scope || this;
        var files = this.directoryFromCache(config.path);
        if (files && !config.forceReload)
        {
            if (typeof config.success == "function")
                config.success.defer(1, config.scope, [this, config.path, files]);
        }
        else
        {
            this.reloadFiles(config);
        }
    },

    /**
     * A helper to test if a file of the same name exists at a given path.  If this path has not already been loaded, the local cache will be used unless forceReload is true.
     * @param config Configuration properties.
     * @param {String} config.name The name to test.  This can either be a filename or a full path.  If the latter is supplied, getFileName() will be used to extract the filename
     * @param {String} config.path The path to check
     * @param {Function} config.success Success callback function.  It will be called with the following arguments:
     * <li>Filesystem: A reference to the filesystem</li>
     * <li>Name: The name to be tested</li>
     * <li>Path: The path to be checked</li>
     * <li>Record: If a record of the same name exists, the record object will be returned.  Null indicates no name conflict exists</li>
     * @param {Function} [config.failure] Error callback function.  It will be called with the following arguments:
     * <li>Response: The XMLHttpRequest object containing the response data.</li>
     * <li>Options: The parameter to the request call.</li>
     * @param {Object} [config.scope] The scope for the callback function.  Defaults to 'this'
     * @param {Boolean} [config.forceReload] If true, the cache will be reloaded prior to performing the check
     * @methodOf LABKEY.FileSystem.AbstractFileSystem#
     */
    checkForNameConflict: function(config)
    {
        var filename = this.concatPaths(config.path, this.getFileName(config.name));
        config.scope = config.scope || this;

        this.listFiles({
            path: config.path,
            success: function (fs, path, records) {
                if (Ext.isFunction(config.success))
                    config.success.defer(1, config.scope, [this, config.name, config.path, this.recordFromCache(filename)]);
            },
            failure: config.failure,
            scope: this,
            forceReload: config.forceReload
        });
    },

    /**
     * Force reload on next listFiles call
     * @ignore
     * @param record
     */
    uncacheListing : function(record)
    {
        var path = (typeof record == "string") ? record : record.data.path;
        this.directoryMap[path] = null;
    },

    /**
     * @ignore
     * @param record
     */
    canRead : function(record)
    {
        return true;
    },

    /**
     * @ignore
     * @param record
     */
    canWrite: function(record)
    {
        return true;
    },

    /**
     * @ignore
     * @param record
     */
    canMkdir: function(record)
    {
        return true;
    },

    /**
     * @ignore
     * @param record
     */
    canDelete : function(record)
    {
        return true;
    },

    /**
     * @ignore
     * @param record
     */
    canMove : function(record)
    {
        return true;
    },

    /**
     * @ignore
     * @param config
     */
    deletePath : function(config)   // callback(filesystem, success, path)
    {
        return false;
    },

    /**
     * @ignore
     * @param config
     */
    createDirectory : function(config) // callback(filesystem, success, path)
    {
    },

    /**
     * Called by listFiles(), return false on immediate fail
     * @ignore
     */
    reloadFiles : function(config)
    {
        return false;
    },

    /**
     * @ignore
     * @param config
     */
    getHistory : function(config) // callback(filesystem, success, path, history[])
    {
    },

    // protected

    _addFiles : function(path, records)
    {
        this.directoryMap[path] = records;
        this.fireEvent(LABKEY.FileSystem.FILESYSTEM_EVENTS.fileschanged, this, path, records);
    },

    /**
     * For a supplied path, returns an array corresponding Ext Record from the cache
     * @param {String} path The path of the directory
     * @returns {Ext.Record[]} An array of Ext.Records representing the contents of the directory.  Returns null if the directory is not in the cache.
     * @methodOf LABKEY.FileSystem.AbstractFileSystem#
     * @name directoryFromCache
     */
    directoryFromCache : function(path)
    {
        var files = this.directoryMap[path];
        if (!files && path && path.length>0 && path.charAt(path.length-1) == this.separator)
            path = path.substring(0,path.length-1);
        files = this.directoryMap[path];
        return files;
    },

    /**
     * For a supplied path, returns the corresponding Ext Record from the cache
     * @param {String} path The path of the file or directory
     * @returns {Ext.Record} The Ext.Record for this file.  Returns null if the file is not found.
     * @methodOf LABKEY.FileSystem.AbstractFileSystem#
     * @name recordFromCache
     */
    recordFromCache : function(path)
    {
        if (!path || path == this.rootPath)
            return this.rootRecord;
        var parent = this.getParentPath(path) || this.rootPath;
        var name = this.getFileName(path);
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

    onReady : function(fn)
    {
        if (this.ready)
            fn.call();
        else
            this.on(LABKEY.FileSystem.FILESYSTEM_EVENTS.ready, fn);
    },

    // util

    /**
     * A utility method to concatenate 2 strings into a normalized filepath
     * @param {String} a The first path
     * @param {String} b The first path
     * @returns {String} The concatenated path
     * @methodOf LABKEY.FileSystem.AbstractFileSystem#
     */
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

    /**
     * A utility method to extract the parent path from a file or folder path
     * @param {String} p The path to the file or directory
     * @returns {String} The parent path
     * @methodOf LABKEY.FileSystem.AbstractFileSystem#
     */
    getParentPath : function(p)
    {
        if (!p)
            p = this.rootPath;
        if (p.length > 1 && p.charAt(p.length-1) == this.separator)
            p = p.substring(0,p.length-1);
        var i = p.lastIndexOf(this.separator);
        return i == -1 ? this.rootPath : p.substring(0,i+1);
    },

    /**
     * A utility method to extract the filename from a file path.
     * @param {String} p The path to the file or directory
     * @returns {String} The file name
     * @methodOf LABKEY.FileSystem.AbstractFileSystem#
     */
    getFileName : function(p)
    {
        if (!p || p == this.rootPath)
            return this.rootPath;
        if (p.length > 1 && p.charAt(p.length-1) == this.separator)
            p = p.substring(0,p.length-1);
        var i = p.lastIndexOf(this.separator);
        if (i > -1)
            p = p.substring(i+1);
        return p;
    },

    /**
     * A utility to test if a path is a direct child of another path
     * @param {String} a The first path to test
     * @param {String} b The second path to test
     * @returns {Boolean} Returns true if the first path is a direct child of the second
     * @methodOf LABKEY.FileSystem.AbstractFileSystem#
     */
    isChild: function(a, b){
        return a.indexOf(b) == 0;
        //return a.match(new RegExp('^' + b + '.+', 'i'));
    }

});


/**
 * This class enables interaction with WebDav filesystems, such as the one exposed through LabKey Server.
 * In addition to the properties and methods documented here, all methods from LABKEY.FileSystem.AbstractFileSystem are available.
 * @class LABKEY.FileSystem.WebdavFileSystem
 * @augments LABKEY.FileSystem.AbstractFileSystem
 * @constructor
 * @param config Configuration properties.
 * @param {String} [config.containerPath] The path to the container to load (ie. '/home')
 * @param {String} [config.filePath] The file path, relative to the containerPath (ie. '/mySubfolder'). Optional.
 * @param {String} [config.fileLink] A folder name that is appended after the container and filePath.  By default, LabKey stores files in a subfolder called '/@files'.  If the pipeline root of your container differs from the file root, the files may be stored in '/@pipeline'.  This defaults to '/@files'.  To omit this, use 'fileLink: null'.  For non-standard URLs, also see config.baseUrl.
 * @param {string} [config.baseUrl] Rather than supplying a containerPath and filePath, you can supply the entire baseUrl to use as the root of the webdav tree (ie. http://localhost:8080/labkey/_webdav/home/@files/), must be an ABSOLUTE URL ("/_webdav" NOT "_webdav").  If provided, this will be preferentially used instead of creating a baseUrl from containerPath, filePath and fileLink
 * @param {string} [config.rootName] The display name for the root (ie. 'Fileset'). Optional.
 * @param {array} [config.extraDataFields] An array of extra Ext.data.Field config objects that will be appended to the FileRecord. Optional.
 * @example &lt;script type="text/javascript"&gt;

    //this example loads files from: /_webdav/home/mySubfolder/@files
    var fileSystem = new LABKEY.FileSystem.WebdavFileSystem({
        containerPath: '/home',
        filePath: '/mySubfolder'  //optional.
    });

     //this would be identical to the example above
     new LABKEY.FileSystem.WebdavFileSystem({
         baseUrl: "/_webdav/home/mySubfolder/@files"
     });

     //this creates the URL: /webdav/myProject/@pipeline
     new LABKEY.FileSystem.WebdavFileSystem({
         containerPath: '/myProject',
         fileLink: '/@pipeline'
     });


    fileSystem.on('ready', function(fileSystem){
        fileSystem.listFiles({
            path: '/mySubfolder',
            success: function(fileSystem, path, records){
                alert('It worked!');
                console.log(records);
            },
            scope: this
        }, this);

        fileSystem.movePath({
            source: '/myFile.xls',
            destination: '/foo/myFile.xls',
            isFile: true,
            success: function(fileSystem, path, records){
                alert('It worked!');
            },
            failure: function(response, options){
                alert('It didnt work.  The error was ' + response.statusText);
                console.log(response);
            },
            scope: this
        });
    }, this);


 &lt;/script&gt;
 */
LABKEY.FileSystem.WebdavFileSystem = function(config)
{
    config = config || {};
    Ext.apply(this, config, {
        baseUrl: LABKEY.contextPath + "/_webdav",
        rootPath: "/",
        rootName : (LABKEY.serverName || "LabKey Server"),
        fileLink: "/@files"
    });
    this.ready = false;
    this.initialConfig = config;

    LABKEY.FileSystem.WebdavFileSystem.superclass.constructor.call(this);

    this.HistoryRecord = Ext.data.Record.create(['user', 'date', 'message', 'href']);
    this.historyReader = new Ext.data.XmlReader({record : "entry"}, this.HistoryRecord);

    this.init(config);
    this.reloadFile("/", (function()
    {
        this.ready = true;
        this.fireEvent(LABKEY.FileSystem.FILESYSTEM_EVENTS.ready, this);
    }).createDelegate(this));
};

Ext.extend(LABKEY.FileSystem.WebdavFileSystem, LABKEY.FileSystem.AbstractFileSystem,
{
    /**
     * Returns the history for the file or directory at the supplied path
     * @param config Configuration properties.
     * @param {String} config.path Path to the file or directory
     * @param {Function} config.success Success callback function.  It will be called with the following arguments:
     * <li>Filesystem: A reference to the filesystem</li>
     * <li>Path: The path that was loaded</li>
     * <li>History: An array of records representing the history</li>
     * @param {Function} [config.failure] Error callback function.  It will be called with the following arguments:
     * <li>Response: the response object</li>
     * @param {Object} [config.scope] The scope of the callback function
     * @methodOf LABKEY.FileSystem.WebdavFileSystem#
     */
    getHistory : function(config)
    {
        config.scope = config.scope || this;
        var body =  "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<propfind xmlns=\"DAV:\"><prop><history/></prop></propfind>";

        var proxy = new Ext.data.HttpProxy(
        {
            url: this.concatPaths(this.prefixUrl, config.path),
            xmlData : body,
            method: "PROPFIND",
            headers: {"Depth" : "0"}
        });
        proxy.api.read.method = 'PROPFIND';

        var cb = function(response, args, success)
        {
            LABKEY.FileSystem.Util._processAjaxResponse(response);
            if (success && Ext.isFunction(config.success))
                config.success.call(config.scope, args.filesystem, args.path, response.records);
            else if (!success && Ext.isFunction(config.failure))
                config.failure.call(config.scope, response);
        };
        proxy.request('read', null, {method:"PROPFIND", depth:"0", propname : this.propNames}, this.historyReader, cb, this, {filesystem:this, path:config.path});
    },

    /**
     * Returns true if the current user can read the passed file
     * In order to obtain the record for the desired file, recordFromCache() is normally used.
     * @param {Ext.Record} record The Ext record associated with the file.  See LABKEY.AbstractFileSystem.FileRecord for more information.
     * @methodOf LABKEY.FileSystem.WebdavFileSystem#
     */
    canRead : function(record)
    {
        var options = record.data.options;
        return !options || -1 != options.indexOf('GET');
    },

    /**
     * Returns true if the current user can write to the passed file or location
     * In order to obtain the record for the desired file, recordFromCache() is normally used.
     * @param {Ext.Record} record The Ext record associated with the file.  See LABKEY.AbstractFileSystem.FileRecord for more information.
     * @methodOf LABKEY.FileSystem.WebdavFileSystem#
     */
    canWrite : function(record)
    {
        var options = record.data.options;
        return !options || -1 != options.indexOf("PUT");
    },

    /**
     * Returns true if the current user can create a folder in the passed location
     * In order to obtain the record for the desired file, recordFromCache() is normally used.
     * @param {Ext.Record} record The Ext record associated with the file.  See LABKEY.AbstractFileSystem.FileRecord for more information.
     * @methodOf LABKEY.FileSystem.WebdavFileSystem#
     */
    canMkdir : function(record)
    {
        var options = record.data.options;
        return !options || -1 != options.indexOf("MKCOL");
    },

    /**
     * Returns true if the current user can delete the passed file.
     * In order to obtain the record for the desired file, recordFromCache() is normally used.
     * @param {Ext.Record} record The Ext record associated with the file.  See LABKEY.AbstractFileSystem.FileRecord for more information.
     * @methodOf LABKEY.FileSystem.WebdavFileSystem#
     */
    canDelete : function(record)
    {
        var options = record.data.options;
        return !options || -1 != options.indexOf('DELETE');
    },

    /**
     * Returns true if the current user can move or rename the passed file
     * In order to obtain the record for the desired file, recordFromCache() is normally used.
     * @param {Ext.Record} record The Ext record associated with the file.  See LABKEY.AbstractFileSystem.FileRecord for more information.
     * @methodOf LABKEY.FileSystem.WebdavFileSystem#
     */
    canMove : function(record)
    {
        var options = record.data.options;
        return !options || -1 != options.indexOf('MOVE');
    },

    /**
     * Can be used to delete a file or folder.
     * @param config Configuration properties.
     * @param {String} config.path The source file, which should be a URL relative to the fileSystem's rootPath
     * @param {Boolean} config.isFile Set to true is this represent a file, as opposed to a folder
     * @param {Function} config.success Success callback function.  It will be called with the following arguments:
     * <li>Filesystem: A reference to the filesystem</li>
     * <li>Path: The path that was loaded</li>
     * @param {Object} [config.failure] The error callback function.  It will be called with the following arguments:
     * <li>Response: The XMLHttpRequest object containing the response data.</li>
     * <li>Options: The parameter to the request call.</li>
     * @param {Object} [config.scope] The scope of the callback functions
     * @methodOf LABKEY.FileSystem.WebdavFileSystem#
     */
    deletePath : function(config)
    {
        config.scope = config.scope || this;
        var resourcePath = this.concatPaths(this.prefixUrl, LABKEY.ActionURL.encodePath(config.path));
        var fileSystem = this;
        var connection = new Ext.data.Connection();

        connection.request({
            method: "DELETE",
            url: resourcePath,
            scope: this,
            success: function(response, options){
                var success = false;
                LABKEY.FileSystem.Util._processAjaxResponse(response);
                if (204 == response.status || 404 == response.status) // NO_CONTENT (success)
                    success = true;
                else if (405 == response.status) // METHOD_NOT_ALLOWED
                    success = false;

                if (success)
                {
                    fileSystem._deleteListing(config.path, config.isFile);

                    if (Ext.isFunction(config.success))
                        config.success.call(config.scope, fileSystem, config.path);
                }
                else {
                    if (Ext.isFunction(config.failure))
                        config.failure.call(config.scope, response, options);
                }
            },
            failure: function(response, options)
            {
                var success = false;
                LABKEY.FileSystem.Util._processAjaxResponse(response);
                if (response.status == 404)  //NOT_FOUND - not sure if this is the correct behavior or not
                    success = true;

                if (!success && Ext.isFunction(config.failure))
                    config.failure.call(config.scope, response, options);
                if (success && Ext.isFunction(config.success))
                    config.success.call(config.scope, fileSystem, config.path);
            },
            headers: {
                'Content-Type': 'application/json'
            }
        });

        return true;
    },

    //private
    _deleteListing: function(path, isFile)
    {
        var deleted = [];

        //always delete the record itself
        var record = this.recordFromCache(path);
        if (record) {
            isFile = record.data.file;
            deleted.push(record);
            var parentPath = this.getParentPath(path);
            var parentFolder = this.directoryMap[parentPath];
            if (parentFolder){
                parentFolder.remove(record);
            }
        }

        // find all paths modified by this delete
        if (!isFile)
        {
            var pathsRemoved = [];
            for (var a in this.directoryMap)
            {
                if (typeof a == 'string')
                {
                    var idx = a.indexOf(path);
                    if (idx == 0)
                    {
                        pathsRemoved.push(a);
                        var r = this.recordFromCache(a);
                        if (r)
                            deleted.push(r);

                        parentPath = this.getParentPath(a);
                        parentFolder = this.directoryMap[parentPath];
                        if (parentFolder && r){
                            parentFolder.remove(r);
                        }
                        if (this.directoryMap[a] && this.directoryMap[a].length)
                            deleted = deleted.concat(this.directoryMap[a]);
                    }
                }
            }
            this.uncacheListing(path);
        }

        deleted = Ext.unique(deleted);
        this.fireEvent(LABKEY.FileSystem.FILESYSTEM_EVENTS.filesremoved, this, path, deleted);
    },

    /**
     * Can be used to rename a file or folder.  This is simply a convenience wrapper for movePath().
     * @param config Configuration properties.
     * @param {String} config.source The source file, which should be relative to the fileSystem's rootPath
     * @param {String} config.destination The target path, which should be the full path for the new file, relative to the fileSystem's rootPath
     * @param {Boolean} config.isFile Set to true if the path is a file, as opposed to a directory
     * @param {Function} config.success Success callback function.  It will be called with the following arguments:
     * <li>Filesystem: A reference to the filesystem</li>
     * <li>SourcePath: The path to the file/folder to be renamed</li>
     * <li>DestPath: The new path for the renamed file/folder</li>
     * @param {Object} [config.failure] The failure callback function.  Will be called with the following arguments:
     * <li>Response: The XMLHttpRequest object containing the response data.</li>
     * <li>Options: The parameter to the request call.</li>
     * @param {Object} [config.scope] The scope of the callback function
     * @param {Boolean} [config.overwrite] If true, files at the target location
     * @methodOf LABKEY.FileSystem.WebdavFileSystem#
* @example &lt;script type="text/javascript"&gt;
    var fileSystem = new new LABKEY.FileSystem.WebdavFileSystem({
        containerPath: '/home',
        filePath: '/@files'  //optional.  this is the same as the default
    });

    fileSystem.on('ready', function(fileSystem){
        fileSystem.listFiles({
            path: '/mySubfolder/',
            success: function(fileSystem, path, records){
                alert('It worked!');
                console.log(records);
            },
            scope: this
        }, this);

        fileSystem.renamePath({
            source: 'myFile.xls',
            destination: 'renamedFile.xls',
            isFile: true,
            scope: this
        });


        //if you renamed a file in a subfolder, you can optionally supply the fileName only
        //this file will be renamed to: '/subfolder/renamedFile.xls'
        fileSystem.renamePath({
            source: '/subfolder/myFile.xls',
            destination: 'renamedFile.xls',
            isFile: true,
            scope: this
        });

        //or provide the entire path
        fileSystem.renamePath({
            source: '/subfolder/myFile.xls',
            destination: '/subfolder/renamedFile.xls',
            isFile: true,
            scope: this
        });
    }, this);


&lt;/script&gt;
     */
    renamePath : function(config)
    {
        //allow user to submit either full path for rename, or just the new filename
        if (config.source.indexOf(this.separator) > -1 && config.destination.indexOf(this.separator) == -1){
            config.destination = this.concatPaths(this.getParentPath(config.source), config.destination);
        }

        this.movePath({
            source: config.source,
            destination: config.destination,
            isFile: config.isFile,
            success: config.success,
            failure: config.failure,
            scope: config.scope,
            overwrite: config.overwrite
        });
    },

    /**
     * Can be used to move a file or folder from one location to another.
     * @param config Configuration properties.
     * @param {String} config.path The source file, which should be a URL relative to the fileSystem's rootPath
     * @param {String} config.destination The target path, which should be a URL relative to the fileSystem's rootPath
     * @param {Boolean} config.isFile True if the file to move is a file, as opposed to a directory
     * @param {Function} config.success Success callback function.  It will be called with the following arguments:
     * <li>Filesystem: A reference to the filesystem</li>
     * <li>SourcePath: The path that was loaded</li>
     * <li>DestPath: The path that was loaded</li>
     * @param {Object} [config.failure] The failure callback function.  Will be called with the following arguments:
     * <li>Response: The XMLHttpRequest object containing the response data.</li>
     * <li>Options: The parameter to the request call.</li>
     * @param {Object} [config.scope] The scope of the callbacks
     * @param {Boolean} [config.overwrite] If true, files at the target location
     * @methodOf LABKEY.FileSystem.WebdavFileSystem#
     */
    movePath : function(config)
    {
        config.scope  = config.scope || this;

        var resourcePath = this.concatPaths(this.prefixUrl, LABKEY.ActionURL.encodePath(config.source));
        var destinationPath = this.concatPaths(this.prefixUrl, LABKEY.ActionURL.encodePath(config.destination));
        var fileSystem = this;
        var connection = new Ext.data.Connection();

        var cfg = {
            method: "MOVE",
            url: resourcePath,
            scope: this,
            failure: function(response){
                LABKEY.FileSystem.Util._processAjaxResponse(response);
                if(Ext.isFunction(config.failure))
                    config.failure.apply(config.scope, arguments);
            },
            success: function(response, options){
                LABKEY.FileSystem.Util._processAjaxResponse(response);
                var success = false;
                if (201 == response.status || 204 == response.status) //CREATED,  NO_CONTENT (success)
                    success = true;
                else
                    success = false;

                if (success)
                {
                    //the move is performed as a delete / lazy-insert
                    fileSystem._deleteListing(config.source, config.isFile);

                    var destParent = fileSystem.getParentPath(config.destination);
                    fileSystem.uncacheListing(destParent); //this will cover uncaching children too

                    // TODO: maybe support a config option that will to force the fileSystem to
                    // auto-reload this location, instead just uncaching and relying on consumers to do it??
                    this.fireEvent(LABKEY.FileSystem.FILESYSTEM_EVENTS.fileschanged, this, destParent);

                    if (Ext.isFunction(config.success))
                        config.success.call(config.scope, fileSystem, config.source, config.destination);
                }
                else {
                    if (Ext.isFunction(config.failure))
                        config.failure.call(config.scope, response, options);
                }
            },
            headers: {
                Destination: destinationPath,
                'Content-Type': 'application/json'
            }
        };

        if (config.overwrite)
            cfg.headers.Overwrite = 'T';

        connection.request(cfg);

        return true;
    },

    /**
     * Will create a directory at the provided location.  This does not perform permission checking, which can be done using canMkDir().
     * @param config Configuration properties.
     * @param {String} config.path The path of the folder to create.  This should be relative to the rootPath of the FileSystem.  See constructor for examples.
     * @param {Function} config.success Success callback function.  It will be called with the following arguments:
     * <li>Filesystem: A reference to the filesystem</li>
     * <li>Path: The path that was created</li>
     * @param {Object} [config.failure] Failure callback function.  It will be called with the following arguments:
     * <li>Response: the response object</li>
     * <li>Options: The parameter to the request call.</li>
     * @param {Object} [config.scope] The scope of the callback functions.
     * @methodOf LABKEY.FileSystem.WebdavFileSystem#
     */
    createDirectory : function(config)
    {
        var fileSystem = this;
        config.scope = config.scope || this;

        var resourcePath = this.concatPaths(this.prefixUrl, config.path);
        var connection = new Ext.data.Connection();

        connection.request({
            method: "MKCOL",
            url: resourcePath,
            scope: this,
            success: function(response, options){
                LABKEY.FileSystem.Util._processAjaxResponse(response);
                var success = false;
                if (200 == response.status || 201 == response.status){   // OK, CREATED
                    if(!response.responseText)
                        success = true;
                    else
                        success = false;
                }
                else if (405 == response.status) // METHOD_NOT_ALLOWED
                    success = false;

                if (success && Ext.isFunction(config.success))
                    config.success.call(config.scope, fileSystem, config.path);
                if (!success && Ext.isFunction(config.failure))
                    config.failure.call(config.scope, response, options);
            },
            failure: function(response){
                LABKEY.FileSystem.Util._processAjaxResponse(response);
                if (Ext.isFunction(config.failure))
                    config.failure.apply(config.scope, arguments);
            },
            headers: {
                'Content-Type': 'application/json'
            }
        });

        return true;
    },

    //private
    // not sure why both this and reloadFiles() exist?  reloadFile() seems to be used internally only
    reloadFile : function(path, callback)
    {
        var url = this.concatPaths(this.prefixUrl, LABKEY.ActionURL.encodePath(path));
        this.connection.url = url;
        var args = {url: url, path: path, callback:callback};
        this.proxy.doRequest("read", null, {method:"PROPFIND",depth:"0", propname : this.propNames}, this.transferReader, this.processFile, this, args);
        return true;
    },

    //private
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

    //private
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

        if (Ext.isFunction(args.callback))
            args.callback(this, success && null != update, args.path, update);
    },

    //private
    uncacheListing : function(record)
    {
        var path = (typeof record == "string") ? record : record.data.path;

        // want to uncache all subfolders of the parent folder
        Ext.iterate(this.directoryMap, function(key, value) {
            if (Ext.isString(key)) {
                if (key.indexOf(path) === 0) {
                    this.directoryMap[key] = null;
                }
            }
        }, this);

        var args = this.pendingPropfind[path];
        if (args && args.transId)
        {
            this.connection.abort(args.transId);
            this.connection.url = args.url;
            this.proxy.doRequest("read", null, {method:"PROPFIND",depth:"1", propname : this.propNames}, this.transferReader, this.processFiles, this, args);
            args.transId = this.connection.transId;
        }
    },

    //private
    reloadFiles : function(config)
    {
        config.scope = config.scope || this;

        var cb = {
            success: config.success,
            failure: config.failure,
            scope: config.scope
        };

        var args = this.pendingPropfind[config.path];
        if (args)
        {
            args.callbacks.push(cb);
            return;
        }

        var url = this.concatPaths(this.prefixUrl, LABKEY.ActionURL.encodePath(config.path));
        this.connection.url = url;
        this.pendingPropfind[config.path] = args = {url: url, path: config.path, callbacks:[cb]};
        this.proxy.doRequest("read", null, {method:"PROPFIND",depth:"1", propname : this.propNames}, this.transferReader, this.processFiles, this, args);
        args.transId = this.connection.transId;
        return true;
    },

    //private
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
            if (Ext.isFunction(callback)) {
                callback(this, path, listing);
            }
            else if (typeof callback == 'object') {
                var scope = callback.scope || this;
                if (success && Ext.isFunction(callback.success))
                    callback.success.call(scope, this, path, listing);
                else if (!success && Ext.isFunction(callback.failure))
                    callback.failure.call(scope, args.transId.conn);
            }
        }
    },

    //private
    init : function(config)
    {
        //support either containerPath + path OR baseUrl (which is the concatenation of these 2)
        this.filePath = this.filePath || '';
        this.fileLink = this.fileLink || '';
        this.containerPath = this.containerPath || LABKEY.ActionURL.getContainer();

        if (!config.baseUrl){
            this.baseUrl = this.concatPaths(LABKEY.contextPath + "/_webdav", encodeURI(this.containerPath));
            this.baseUrl = this.concatPaths(this.baseUrl, encodeURI(this.filePath));
            this.baseUrl = this.concatPaths(this.baseUrl, encodeURI(this.fileLink));
        }

        var prefix = this.concatPaths(this.baseUrl, this.rootPath);
        if (prefix.length > 0 && prefix.charAt(prefix.length-1) == this.separator)
            prefix = prefix.substring(0,prefix.length-1);
        this.prefixUrl = prefix;
        this.pendingPropfind = {};

        var prefixDecode  = decodeURIComponent(prefix);

        var getURI = function(v,rec)
        {
            var uri = rec.uriOBJECT || new LABKEY.URI(v);
            if (!Ext.isIE && !rec.uriOBJECT)
                try {rec.uriOBJECT = uri;} catch (e) {};
            return uri;
        };

        this.propNames = ["creationdate", "displayname", "createdby", "getlastmodified", "modifiedby", "getcontentlength",
                     "getcontenttype", "getetag", "resourcetype", "source", "path", "iconHref", "options", "absolutePath"];

        if (config.extraPropNames && config.extraPropNames.length)
            this.propNames = this.propNames.concat(config.extraPropNames);

        var recordCfg = [
            {name: 'uri', mapping: 'href',
                convert : function(v, rec)
                {
                    var uri = getURI(v,rec);
                    return uri ? uri.href : "";
                }
            },
            {name: 'fileLink', mapping: 'href',
                convert : function(v, rec)
                {
                    var uri = getURI(v,rec);

                    if (uri && uri.file)
                        return Ext.DomHelper.markup({
                            tag  :'a',
                            href : Ext.util.Format.htmlEncode(uri.href + '?contentDisposition=attachment'),
                            html : Ext.util.Format.htmlEncode(decodeURIComponent(uri.file))});
                    else
                        return '';
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
            {name: 'fileExt', mapping: 'propstat/prop/displayname',
                convert : function (v, rec)
                {
                    // parse the file extension from the file name
                    var idx = v.lastIndexOf('.');
                    if (idx != -1)
                        return v.substring(idx+1);
                    return '';
                }
            },

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
            {name: 'absolutePath', mapping: 'propstat/prop/absolutePath', type: 'string'},
            {name: 'iconHref'},
            {name: 'contentType', mapping: 'propstat/prop/getcontenttype'},
            {name: 'options'}
        ];

        if (config.extraDataFields && config.extraDataFields.length)
            recordCfg = recordCfg.concat(config.extraDataFields);

        this.FileRecord = Ext.data.Record.create(recordCfg);
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
    }
});



