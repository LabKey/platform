Ext4.define('File.system.Webdav', {

    extend : 'File.system.Abstract',

    constructor : function(config) {

        Ext4.apply(this, config, {
            baseUrl: LABKEY.contextPath + "/_webdav",
            rootPath: "/",
            rootName : (LABKEY.serverName || "LabKey Server"),
            fileLink: "/@files"
        });
        this.ready = false;
        this.initialConfig = config;

        this.callParent([config]);

        // TODO Implement rest of constructor
        this.init(config);
    },

    getBaseURL : function() {
        return this.baseUrl;
    },

    init : function(config) {

        Ext4.applyIf(this, {
            containerPath : LABKEY.ActionURL.getContainer(),
            fileLink : '',
            filePath : ''
        });

        if (!config.baseUrl) {
            this.baseUrl = this.concatPaths(LABKEY.contextPath + "/_webdav", encodeURI(this.containerPath));
            this.baseUrl = this.concatPaths(this.baseUrl, encodeURI(this.filePath));
            this.baseUrl = this.concatPaths(this.baseUrl, encodeURI(this.fileLink));
        }

        var prefix = this.concatPaths(this.baseUrl, this.rootPath);
        if (prefix.length > 0 && prefix.charAt(prefix.length-1) == this.separator)
            prefix = prefix.substring(0,prefix.length-1);
        this.prefixUrl = prefix;
        this.pendingPropfind = {};

        // TODO: Apply this to the 'path' in the model fields
        var prefixDecode  = decodeURIComponent(prefix);

//        if (config.extraPropNames && config.extraPropNames.length)
//            this.propNames = this.propNames.concat(config.extraPropNames);

        // TODO: Additonal params on model?
//        if (config.extraDataFields && config.extraDataFields.length)
//            recordCfg = recordCfg.concat(config.extraDataFields);
    },

    getModel : function(type) {
        if (type == 'xml')
            return 'File.data.webdav.XMLResponse';
        return 'File.data.webdav.JSONReponse';
    },

    getProxyCfg : function(url, type) {
        if (type == 'xml') {
            return this.getXMLProxyCfg(url);
        }
        return this.getJsonProxyCfg(url);
    },

    getJsonProxyCfg : function(url) {
        return {
            type : 'ajax',
            url  : url,
            extraParams : {
                method : 'JSON'
            },
            reader : {
                root : 'files',
                totalProperty : 'fileCount'
            }
        };
    },

    getXMLProxyCfg : function(url) {
        return {
            type : 'webdav',
            url  : url,
            reader : {
                type : 'xml',
                root : 'multistatus',
                record : 'response'
            }
        }
    },

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
            if (success && typeof config.success == 'function')
                config.success.call(config.scope, args.filesystem, args.path, response.records);
            else if (!success & typeof config.failure == 'function')
                config.failure.call(config.scope, response, options);
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

    //private
    _deleteListing: function(path, isFile)
    {
        /* NO-OP */
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

    /**
     * Will create a directory at the provided location.  This does not perform permission checking, which can be done using canMkDir().
     * @param config Configuration properties.
     * @param {String} config.path The path of the folder to create. This should be the full url webdav path
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
        config.scope = config.scope || this;

        Ext4.Ajax.request({
            method : 'MKCOL',
            url : config.path,
            success : function(response, options) {
                var success = false;
                if (200 == response.status || 201 == response.status) { // OK, CREATED
//                    success = (response.responseText ? true : false);
                    success = true;
                }
                else if (405 == response.status) { // METHOD NOT ALLOWED
                    success = false;
                }

                if (success) {
                    if (Ext4.isFunction(config.success)) {
                        config.success.call(this, config.path);
                    }
                }
                else if (Ext4.isFunction(config.failure)) {
                    config.failure.call(this, response, options);
                }
            },
            failure : config.failure,
            scope : config.scope
        });

        return true;
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

        Ext4.Ajax.request({
            method : 'DELETE',
            url : config.path,
            success : function(response, options){
                var success = false;
                LABKEY.FileSystem.Util._processAjaxResponse(response);
                if (204 == response.status || 404 == response.status) // NO_CONTENT (success)
                    success = true;
                else if (405 == response.status) // METHOD_NOT_ALLOWED
                    success = false;

                if (success) {
                    if (Ext4.isFunction(config.success)) {
                        config.success.call(this, config.path);
                    }
                }
                else if (Ext4.isFunction(config.failure)) {
                    config.failure.call(this, response, options);
                }
            },
            failure : function(response, options) {
                var success = false;
                if (response.status == 404) {  //NOT_FOUND - not sure if this is the correct behavior or not
                    success = true;
                }

                if (!success && Ext4.isFunction(config.failure)) {
                    config.failure.call(this, response, options);
                }
                else if (success && Ext4.isFunction(config.success)) {
                    config.success.call(this, config.path);
                }
            },
            scope : config.scope
        });

        return true;
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
                if(typeof config.failure == 'function')
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
                    var destParent = fileSystem.getParentPath(config.destination);
                    fileSystem.uncacheListing(destParent); //this will cover uncaching children too

                    // TODO: maybe support a config option that will to force the fileSystem to
                    // auto-reload this location, instead just uncaching and relying on consumers to do it??
                    this.fireEvent(LABKEY.FileSystem.FILESYSTEM_EVENTS.fileschanged, this, destParent);

                    if (typeof config.success == 'function')
                        config.success.call(config.scope, fileSystem, config.source, config.destination);
                }
                else {
                    if (typeof config.failure == 'function')
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

    //private
    processFile : function(result, args, success)
    {
        var update = null;
        if (success && result && !Ext4.isArray(result.records))
            success = false;
        if (success && result.records.length == 1)
        {
            update = result.records[0];
            this._updateRecord(update);
        }

        if (Ext4.isFunction(args.callback)) {
            args.callback(this, success && null != update, args.path, update);
        }
    },

    //private
    processFiles : function(result, args, success)
    {
        delete this.pendingPropfind[args.path];

        var path = args.path;

        var directory = null;
        var listing = [];
        if (success && result && !Ext4.isArray(result.records))
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
            if (typeof callback == 'function'){
                callback(this, path, listing);
            }
            else if (typeof callback == 'object') {
                var scope = callback.scope || this;
                if (success && typeof callback.success == 'function')
                    callback.success.call(scope, this, path, listing);
                else if (!success && typeof callback.failure == 'function')
                    callback.failure.call(scope, args.transId.conn);
            }
        }
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

    //private
    uncacheListing : function(record)
    {
        var path = (typeof record == "string") ? record : record.data.path;

        // want to uncache all subfolders of the parent folder
        for (var a in this.directoryMap)
        {
            if (typeof a == 'string')
            {
                var idx = a.indexOf(path);
                if (idx == 0)
                {
                    this.directoryMap[a] = null;
                }
            }
        }

        var args = this.pendingPropfind[path];
        if (args && args.transId)
        {
            this.connection.abort(args.transId);
            this.connection.url = args.url;
            this.proxy.doRequest("read", null, {method:"PROPFIND",depth:"1", propname : this.propNames}, this.transferReader, this.processFiles, this, args);
            args.transId = this.connection.transId;
        }
    }
});
