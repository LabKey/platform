/*
 * Copyright (c) 2012-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

(function()
{
    // private constants
    var OK = 200;
    var CREATED = 201;
    var NO_CONTENT = 204;
    var FILE_MATCH = 208;
    var NOT_FOUND = 404;
    var METHOD_NOT_ALLOWED = 405;


    Ext4.define('File.system.Webdav', {

        extend: 'File.system.Abstract',

        statics: {
            rootOptions: ['_webdav', '_users', '_webfiles']
        },

        constructor: function (config)
        {
            Ext4.apply(this, config,
                    {
                        containerPath: LABKEY.ActionURL.getContainer(),
                        rootPath: LABKEY.contextPath + '/_webdav/',
                        rootName: (LABKEY.serverName || "LabKey Server")
                    });
            this.ready = false;
            this.initialConfig = config;

            // this is for identifying instances of the filebrowser which might share a session
            // or even two instances on the same page
            this.pageId = LABKEY.Utils.generateUUID();

            // this is the webdav "extended context path", e.g. the root of the webdav file tree
            // usually one of /labkey/_webdav, /labkey/_users, /labkey/_webfiles, /_webdav, /_users, /_webfiles
            if (!this.contextUrl)
            {
                var rootContextUrl = this.getRootContextUrl();
                if (rootContextUrl)
                    this.contextUrl = rootContextUrl;
            }

            // '/home/@files' -- a stripped version of the rootPath
            this.baseUrl = LABKEY.ActionURL.decodePath(this.rootPath.replace(this.contextUrl, ''));

            this.offsetUrl = '/';

            if (this.rootOffset)
            {
                if (this.rootOffset.indexOf(this.contextUrl) == 0)
                {
                    this.rootOffset = this.rootOffset.substring(this.contextUrl.length);
                }
                this.offsetUrl = LABKEY.ActionURL.decodePath(this.rootOffset.replace(this.baseUrl, ''));
            }

            this.callParent([config]);

            this.init(config);
        },

        getRootContextUrl: function()
        {
            var url = null, rootPath = this.rootPath;
            Ext4.each(File.system.Webdav.rootOptions, function (root) {
                if (rootPath.indexOf(LABKEY.contextPath + "/" + root + "/") == 0)
                {
                    url = LABKEY.contextPath + "/" + root;
                    return false;
                }
            });
            return url;
        },

        changeFromURL : function(rawURL)
        {
            var decoded = LABKEY.ActionURL.decodePath(rawURL),
                newRootOffset = decoded.replace(this.rootPath, '/');

            this.changeOffsetURL(newRootOffset);

            return this.rootOffset;
        },

        changeOffsetURL : function(newRootOffset, encode)
        {
            this.rootOffset = newRootOffset;
            this.offsetUrl = LABKEY.ActionURL.decodePath(this.rootOffset.replace(this.baseUrl, ''));

            var path = this.concatPaths(this.rootPath, this.rootOffset);

            if (encode !== false)
            {
                path = LABKEY.ActionURL.encodePath(path);
            }

            return path;
        },

        getURI: function (url)
        {
            return this.concatPaths(LABKEY.ActionURL.getBaseURL(true), this.concatPaths(this.contextUrl, LABKEY.ActionURL.encodePath(url)));
        },

        getAbsoluteBaseURL: function ()
        {
            return this.concatPaths(LABKEY.ActionURL.getBaseURL(true), this.rootPath);
        },

        /**
         * Returns the absolute URL including the offsetUrl.
         * @returns {String}
         */
        getAbsoluteURL: function ()
        {
            return this.concatPaths(LABKEY.ActionURL.getBaseURL(true), this.getURL());
        },

        /**
         * Returns the base relative URL which is after the context URL. The 'root' for this instance
         * e.g. "/home/@files"
         */
        getBaseURL: function ()
        {
            return this.baseUrl;
        },

        getContextBaseURL: function ()
        {
            return this.concatPaths(this.contextUrl, this.baseUrl);
        },

        /**
         * Returns the base relative offset URL which is after the base URL. Defaults to '/'
         * e.g. "/" or "/firstSubFolder"
         */
        getOffsetURL: function ()
        {
            return this.offsetUrl;
        },

        getURL: function ()
        {
            return this.concatPaths(this.contextUrl, this.concatPaths(this.baseUrl, this.offsetUrl));
        },

        init: function (config)
        {

            Ext4.applyIf(this, {
                containerPath: LABKEY.ActionURL.getContainer()
            });

            var prefix = this.concatPaths(this.baseUrl, this.rootPath);
            if (prefix.length > 0 && prefix.charAt(prefix.length - 1) == this.separator)
                prefix = prefix.substring(0, prefix.length - 1);
            this.prefixUrl = prefix;
        },

        getModel: function (type)
        {
            return (type == 'xml' ? 'File.data.webdav.XMLResponse' : 'File.data.webdav.JSONResponse');
        },

        getProxyCfg: function (type, options)
        {
            return (type == 'xml' ? this.getXMLProxyCfg(options) : this.getJsonProxyCfg(options));
        },

        getJsonProxyCfg: function (options)
        {
            var jsonProxy = {
                type: 'ajax',
                url: this.concatPaths(LABKEY.ActionURL.getBaseURL(true), LABKEY.ActionURL.encodePath(this.getURL())),
                extraParams: {
                    method: 'JSON'
                },
                reader: {
                    root: 'files',
                    totalProperty: 'fileCount'
                }
            };

            if (options && options.collections === false) {
                jsonProxy.extraParams.collections = false;
            }

            return jsonProxy;
        },

        getXMLProxyCfg: function ()
        {
            return {
                type: 'webdav',
                url: this.concatPaths(LABKEY.ActionURL.getBaseURL(true), LABKEY.ActionURL.encodePath(this.getContextBaseURL())),
                reader: {
                    type: 'xml',
                    root: 'multistatus',
                    record: 'response'
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
         */
        getHistory: function (config)
        {
            console.warn('Get History NYI');
        },

        _check: function (record, option)
        {
            if (record && record.data && record.data.options)
            {
                if (record.data.options[option])
                {
                    return true;
                }
            }
            return false;
        },

        /**
         * Returns true if the current user can read the passed file
         * @param {Ext.Record} records The Ext record associated with the file.  See LABKEY.AbstractFileSystem.FileRecord for more information.
         * @methodOf LABKEY.FileSystem.WebdavFileSystem#
         */
        canRead: function (records)
        {
            if (Ext4.isArray(records))
            {
                for (var i = 0; i < records.length; i++)
                {
                    if (this._check(records[i], 'GET') == false)
                        return false;
                }
            }
            return this._check(records, 'GET');
        },

        /**
         * Returns true if the current user can write to the passed file or location
         * @param {Ext.Record} record The Ext record associated with the file.  See LABKEY.AbstractFileSystem.FileRecord for more information.
         * @methodOf LABKEY.FileSystem.WebdavFileSystem#
         */
        canWrite: function (record)
        {
            return this._check(record, 'PUT');
        },

        /**
         * Returns true if the current user can create a folder in the passed location
         * @param {Ext.Record} record The Ext record associated with the file.  See LABKEY.AbstractFileSystem.FileRecord for more information.
         * @methodOf LABKEY.FileSystem.WebdavFileSystem#
         */
        canMkdir: function (record)
        {
            return this._check(record, 'MKCOL');
        },

        /**
         * Returns true if the current user can delete the passed file.
         * @param {Ext.Record} record The Ext record associated with the file.  See LABKEY.AbstractFileSystem.FileRecord for more information.
         * @methodOf LABKEY.FileSystem.WebdavFileSystem#
         */
        canDelete: function (record)
        {
            return this._check(record, 'DELETE');
        },

        /**
         * Returns true if the current user can move or rename the passed file
         * @param {Ext.Record} record The Ext record associated with the file.  See LABKEY.AbstractFileSystem.FileRecord for more information.
         * @methodOf LABKEY.FileSystem.WebdavFileSystem#
         */
        canMove: function (record)
        {
            return this._check(record, 'MOVE');
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
        createDirectory: function (config)
        {
            Ext4.Ajax.request(
                    {
                        method: 'MKCOL',
                        url: config.path + "?pageId="+this.pageId,
                        success: function (response, options)
                        {
                            var success = false;
                            if (OK == response.status || CREATED == response.status)
                            {
                                success = true;
                            }
                            else if (METHOD_NOT_ALLOWED == response.status)
                            {
                                success = false;
                            }

                            if (success)
                            {
                                if (Ext4.isFunction(config.success))
                                {
                                    config.success.call(config.scope||this, config.path);
                                }
                            }
                            else if (Ext4.isFunction(config.failure))
                            {
                                this.handleFailureCallback(config, response, options);
                            }
                        },
                        failure: config.failure,
                        scope: this
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
        deletePath: function (config)
        {
            Ext4.Ajax.request(
                    {
                        method: 'POST',
                        url: config.path + "?method=DELETE&pageId=" + this.pageId,
                        success: function (response, options)
                        {
                            var success = false;
                            File.system.Abstract.processAjaxResponse(response);
                            if (NO_CONTENT == response.status || NOT_FOUND == response.status)
                                success = true;
                            else if (METHOD_NOT_ALLOWED == response.status)
                                success = false;

                            if (success)
                            {
                                if (Ext4.isFunction(config.success))
                                {
                                    config.success.call(config.scope||this, config.path);
                                }
                            }
                            else
                            {
                                this.handleFailureCallback(config, response, options);
                            }
                        },
                        failure: function (response, options)
                        {
                            var success = false;
                            if (NOT_FOUND == response.status)
                            {
                                success = true;
                            }

                            if (success)
                            {
                                if (Ext4.isFunction(config.success))
                                    config.success.call(config.scope||this, config.path);
                            }
                            else
                            {
                                this.handleFailureCallback(config, response, options);
                            }
                        },
                        scope: this
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
        movePath: function (config)
        {
            var resourcePath = this.getURI(config.source);
            var destinationPath = this.getURI(config.destination);
            var headers = {
                Destination: destinationPath
            };

            if (config.overwrite)
            {
                headers.Overwrite = 'T';
            }

            var me = this;
            Ext4.Ajax.request(ajaxRequestConfig ={
                method: "MOVE",
                url: resourcePath + "?pageId=" + this.pageId,
                failure: function (response, options)
                {
                    File.system.Abstract.processAjaxResponse(response);
                    // handleFailureCallback is expecting full URL in config.path
                    config.path = resourcePath;
                    me.handleFailureCallback(config, response, options);

                    if (Ext4.isFunction(config.failure))
                    {
                        config.failure.apply(config.scope, arguments);
                    }
                },
                success: function (response, options)
                {
                    File.system.Abstract.processAjaxResponse(response);
                    var success = (CREATED == response.status || NO_CONTENT == response.status || OK == response.status);
                    if (!success && FILE_MATCH == response.status)
                    {
                        var noun = config.isFile ? "File" : "Folder";
                        var name = config.fileRecord.newName || config.fileRecord.record.get('name');

                        Ext4.Msg.show({
                            title: noun + " Conflict:",
                            msg: "There is already a " + noun.toLowerCase() + " named " + name + ' in that location. Would you like to replace it?',
                            cls: 'data-window',
                            icon: Ext4.Msg.QUESTION,
                            buttons: Ext4.Msg.YESNO,
                            fn: function (btn)
                            {
                                if (btn == 'yes')
                                {
                                    this.fileSystem.movePath(Ext4.apply(config, {overwrite: true}));
                                }
                            },
                            scope: this
                        });
                    }
                    else if (success)
                    {
                        // TODO: maybe support a config option that will to force the fileSystem to
                        // auto-reload this location, instead just uncaching and relying on consumers to do it??
//                    this.fireEvent(LABKEY.FileSystem.FILESYSTEM_EVENTS.fileschanged, this, destParent);

                        if (Ext4.isFunction(config.success))
                        {
                            config.success.call(config.scope||this, this, config.source, config.destination);
                        }
                    }
                    else
                    {
                        this.handleFailureCallback(config, response, options);
                    }
                },
                headers: headers,
                scope: this
            });

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
         */
        renamePath: function (config)
        {
            //allow user to submit either full path for rename, or just the new filename
            if (config.source.indexOf(this.separator) > -1 && config.destination.indexOf(this.separator) == -1)
            {
                config.destination = this.concatPaths(this.getParentPath(config.source), config.destination);
            }

            var _move = {
                source: config.source,
                destination: config.destination,
                isFile: config.isFile,
                success: config.success,
                failure: config.failure,
                scope: config.scope,
                overwrite: config.overwrite
            };

            this.movePath(_move);
        },

        /**
         * Download the resource.
         * @param config
         * @param config.record Resource record which may have 'directget' request information.
         */
        downloadResource: function (config)
        {
            var record = config.record[0];
            var records = config.record;
            var directget = record.data.directget;
            if (directget)
            {
                // TODO: downloading files using directget doesn't currently work
                throw new Error("Downloading resources via 'directget' is not yet implemented");

                // make direct request
                //Ext4.Ajax.request({
                //    method: directget.method,
                //    url: directget.endpoint,
                //    headers: directget.headers,
                //    disableCaching: false,
                //    success: function (response, options)
                //    {
                //        var success = false;
                //        if (OK == response.status || CREATED == response.status)
                //        {
                //            success = true;
                //        }
                //        else if (METHOD_NOT_ALLOWED == response.status)
                //        {
                //            success = false;
                //        }
                //
                //        if (success)
                //        {
                //            if (Ext4.isFunction(config.success))
                //            {
                //                config.success.call(config.scope||this, config.path);
                //            }
                //        }
                //        else
                //        {
                //            this.handleFailureCallback(config, response, options);
                //        }
                //    },
                //    failure: config.failure,
                //    scope: this
                //});
            }
            else
            {
                if (records.length == 1 && record && !record.data.collection)
                {
                    // hack to prevent Chrome from turning both test.txt.gz and test.tar.gz into test.gz see: https://code.google.com/p/chromium/issues/detail?id=136305
                    if (navigator.userAgent.indexOf("Chrome")!==-1 && record.data.href.indexOf(".gz")!==-1)
                    {
                        url = record.data.href;
                    }
                    else
                    {
                        url = record.data.href + "?contentDisposition=attachment";
                    }
                }
                else if (config.directoryURL)
                {
                    var url = config.directoryURL + "?method=zip&depth=-1";
                    for (var i = 0; i < records.length; i++)
                    {
                        url = url + "&file=" + encodeURIComponent(records[i].data.name);
                    }

                    window.location = url;
                }

                window.location = url;
            }
        },


        handleFailureCallback : function(config, responseOrig, optionsOrig)
        {
            if (!Ext4.isFunction(config.failure))
                return;

            // see if we can decorate the report with more info
            // careful or error handling here, we don't want to mask the original error response

            Ext4.Ajax.request(
            {
                method: 'GET',
                url: config.path,
                params : {pageId: this.pageId, method:"LASTERROR"},
                success: function (responseLastError, optionsLastError)
                {
                    try
                    {
                        var json = JSON.parse(responseLastError.responseText);
                        if (json.errors && json.errors.length)
                            responseOrig.errors = json.errors;
                    }
                    catch (ex)
                    {
                    }
                    config.failure.call(config.scope||this, responseOrig, optionsOrig);
                },
                failure: function (responseLastError, optionsLastError)
                {
                    config.failure.call(config.scope||this, responseOrig, optionsOrig);
                },
                scope: this
            });
        }

});     // Ext4.define()

})();   // function() - scope wrapper