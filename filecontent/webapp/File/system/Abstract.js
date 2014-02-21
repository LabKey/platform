/*
 * Copyright (c) 2012-2014 LabKey Corporation
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

    constructor : function(config) {

        Ext4.apply(this, config);

        this.mixins.observable.constructor.apply(this, arguments);

        this.addEvents(
            'fileschanged',
            'filesremoved',
            'ready'
        )
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
     * @ignore
     * @param config
     */
    deletePath : function(config)   // callback(filesystem, success, path)
    {
        return false;
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

    onReady : function(fn)
    {
        if (this.ready)
            fn.call();
        else
            this.on(LABKEY.FileSystem.FILESYSTEM_EVENTS.ready, fn);
    }

});
