/*
 * Copyright (c) 2012-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('File.system.Abstract', {

    mixins : {
        observable: 'Ext.util.Observable'
    },

    statics : {
        processAjaxResponse : function(r) {
            if (r && r.responseText &&  r.getResponseHeader('Content-Type') && r.getResponseHeader('Content-Type').indexOf('application/json') >= 0) {
                try {
                    r.jsonResponse = Ext4.JSON.decode(r.responseText);
                    if (r.jsonResponse.status) {
                        r.status = r.jsonResponse.status;
                    }
                }
                catch (error) {
                    //ignore
                }
            }
        }
    },

   /**
    * @cfg {Boolean} ready
    */
    ready     : true,

    rootPath : "/",

    separator : "/",

    directoryMap : {},

    constructor : function(config) {

        Ext4.apply(this, config);

        this.mixins.observable.constructor.apply(this, arguments);

        this.addEvents(
            'fileschanged',
            'filesremoved',
            'ready'
        )
    },

    // protected
    _addFiles : function(path, records)
    {
        this.directoryMap[path] = records;
        this.fireEvent(LABKEY.FileSystem.FILESYSTEM_EVENTS.fileschanged, this, path, records);
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
    canMkdir: function(record)
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
     * A utility method to concatenate 2 strings into a normalized filepath
     * @param {String} a The first path
     * @param {String} b The first path
     * @returns {String} The concatenated path
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

    getDirectoryName : function(dirPath) {
        var sep = this.separator, val = '';
        var _dir = dirPath.split(sep);

        if (_dir.length == 1) {
            // cases:
            // did not contain 'sep'
            // did not contain any values == ''
            val = _dir[0];
        }
        else if (_dir.length > 1) {
            // check ending on 'sep'
            val = _dir[_dir.length-1];
            if (val == "") {
                val = _dir[_dir.length-2];
            }
        }

        return val;
    },

    /**
     * @ignore
     * @param config
     */
    createDirectory : function(config) // callback(filesystem, success, path)
    {
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
            success: function (fs, path, records){
                var rec = this.recordFromCache(filename);
                if (Ext4.isFunction(config.success))
                    config.success.defer(1, config.scope, [this, config.name, config.path, rec]);
            },
            failure: config.failure,
            scope: this,
            forceReload: config.forceReload
        });
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
     * @ignore
     * @param config
     */
    getHistory : function(config) // callback(filesystem, success, path, history[])
    {
    },

    downloadResource : function (config)
    {
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
     * A utility to test if a path is a direct child of another path
     * @param {String} a The first path to test
     * @param {String} b The second path to test
     * @returns {Boolean} Returns true if the first path is a direct child of the second
     * @methodOf LABKEY.FileSystem.AbstractFileSystem#
     */
    isChild: function(a, b){
        return a.indexOf(b) == 0;
        //return a.match(new RegExp('^' + b + '.+', 'i'));
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
        if (files && !config.forceReload) {

            if (Ext4.isFunction(config.success)) {
                config.success.defer(1, config.scope, [this, config.path, files]);
            }
        }
        else {
            this.reloadFiles(config);
        }
    },

    onReady : function(fn)
    {
        if (this.ready)
            fn.call();
        else
            this.on(LABKEY.FileSystem.FILESYSTEM_EVENTS.ready, fn);
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

    /**
     * Called by listFiles(), return false on immediate fail
     * @ignore
     */
    reloadFiles : function(config)
    {
        return false;
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
    }
});
