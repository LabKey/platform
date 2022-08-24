/*
 * Copyright (c) 2012-2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('File.panel.Upload', {

    extend : 'Ext.panel.Panel',

    layout : 'fit',

    bodyPadding: 5,

    border : false,

    separator: Ext4.isWindows ? "\\" : "/",

    rootPath: "/",

    baseURL : LABKEY.contextPath + "/_webdav",

    allowFileDrop : true,

    header : false,

    bodyCls: 'lk-file-upload-panel',

    constructor : function(config) {

        this.callParent([config]);

        this.addEvents('cwd', 'transferstarted', 'transfercomplete');
    },

    initComponent : function() {

        this.items = this.getItems();

        this.callParent();
    },

    // not sure if these is needed at all anymore. Looks like this logic may have been around
    // just to check if the unload is possible (we now mask the webpart, so no longer need unload event)
    isBusy : function() {
        return this.busy;
    },

    setBusy : function(busy) {
        this.busy = busy;
    },

    // From FileSystem.js
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

    // From FileSystem.js
    getParentPath : function(p)
    {
        if (!p)
            p = this.rootPath;
        if (p.length > 1 && p.charAt(p.length-1) == this.separator)
            p = p.substring(0,p.length-1);
        var i = p.lastIndexOf(this.separator);
        return i == -1 ? this.rootPath : p.substring(0,i+1);
    },

    getItems : function() {
        if (this.allowFileDrop && window.Dropzone && window.Dropzone.isBrowserSupported())
        {
            this.initDropzone();
        }
        this.getUploadStatusWindow();

        return this.getOuterPanel();
    },

    initDropzone : function () {
        var self = this;
        var dropzone = LABKEY.internal.FileDrop.registerDropzone({
            peer: function () {
                // Get the grid component from the outer Browser component
                var grid = self.ownerCt.getGrid();
                return grid ? grid.el : self.ownerCt.el;
            },

            url: 'bogus.view',
            clickable: true,
            createImageThumbnails: false,

            disabled: true,

            previewsContainer: false,

            maxFiles: 5000,
            // Allow uploads of 100GB files
            maxFilesize: 100*(1024*1024),

            // LabKey webdav only handles single POST per file
            uploadMultiple: false,

            params: {
                // Create any missing intermediate directories
                // UNDONE: Use allowDirectoryUpload configuration option here
                'createIntermediates': 'true'
            },

            acceptDirectory: function(file) {
                var root = this.uploadPanel.fileSystem.rootPath;
                if (root && file.isDirectory)
                {
                    root = this.uploadPanel.fileSystem.concatPaths(root, this.uploadPanel.getWorkingDirectory('path'));
                    var uri = this.uploadPanel.fileSystem.concatPaths(root, file.fullPath ? file.fullPath : file.name);

                    var parentFolderUri = this.uploadPanel.fileSystem.getParentPath(uri);
                    if (!LABKEY.Utils.endsWith(parentFolderUri, '/'))
                        parentFolderUri = parentFolderUri + '/';

                    var suppressDirConflictError = this.uploadPanel.isCloudPath;
                    this.uploadPanel.fileSystem.createDirectory({
                        path : this.uploadPanel.fileSystem.encodeForURL(parentFolderUri + file.name),
                        suppressDirConflictError: suppressDirConflictError,
                        createIntermediates: this.options.params.createIntermediates
                    });
                }
            },

            accept: function (file, done) {
                var record = this.uploadPanel.getWorkingDirectory('model');
                var path = this.uploadPanel.getWorkingDirectory('path');

                // Check permissions before sending
                var canWrite = this.uploadPanel.fileSystem.canWrite(record);
                var canMkdir = this.uploadPanel.fileSystem.canMkdir(record);

                if (!canWrite) {
                    done("You don't have permission to write files to '" + path + "'.");
                    return;
                }

                if (file.fullPath && file.fullPath.indexOf('/') !== -1 && !canMkdir) {
                    done("You don't have permission to create folders in '" + path + "'.");
                    return;
                }

                // success
                done();
            },

            canceled : function (file) {
                //console.info("canceled: ", file);
            },

            init : function () {

                var getMaskElement = function(uploadPanel) {
                    var browser = uploadPanel.up('filebrowser');
                    return browser ? browser.getEl() : uploadPanel.getEl();
                };

                this.on('processing', function (file) {
                    var cwd = this.uploadPanel.getWorkingDirectory('cwd');
                    if (cwd)
                    {
                        // Overwrite if explicitly set (in confirmation by user).
                        var overwrite = file.overwrite;

                        var uri = this.uploadPanel.fileSystem.concatPaths(cwd, file.fullPath ? file.fullPath : file.name);

                        // Save the file's uri for use in the 'transfercomplete' event
                        file.uri = this.uploadPanel.fileSystem.getURI(uri);

                        // Folder the file will be POSTed into
                        var folderUri = this.uploadPanel.fileSystem.getParentPath(file.uri);
                        this.options.url = folderUri + '?overwrite=' + (overwrite ? 'T' : 'F');
                    }
                });

                this.on('totaluploadprogress', function (progress, totalBytes, totalBytesSent) {
                    if (progress == 100 && totalBytes == 0 && totalBytesSent == 0) {
                        // Dropzone is telling us all transfers are complete
                        this.uploadPanel.hideUploadWindow();
                    } else {
                        this.uploadPanel.showUploadWindow();
                        this.uploadPanel.progressBar.updateProgress(progress/100);
                    }
                });

                this.on('sending', function (file, xhr, formData) {
                    if (!this.uploadPanel.isBusy()) {
                        this.uploadPanel.setBusy(true);
                        getMaskElement(this.uploadPanel).mask();

                        this.uploadPanel.statusText.setText('Uploading ' + file.name + '...');
                    }
                    // shouldn't we show some kind of message here? (else case)
                });

                this.on('success', function (file, response, evt) {

                    // success, bail early
                    if (response === "")
                    {
                        this.uploadPanel.statusText.setText('Uploaded ' + file.name + ' successfully.');
                        return;
                    }

                    if (response && Ext4.isString(response) && response.indexOf('<status>HTTP/1.1 200 OK</status>') > -1)
                    {
//          // UNDONE: Should read status from the xml response instead of just looking for <status>
//                var xhr = evt.target;
//                var reader = new Ext4.data.reader.Xml({
//                    record : 'response',
//                    root : 'multistatus',
//                    model : 'File.data.webdav.XMLResponse'
//                });
//
//                var results = reader.read(xhr);
//                if (results.success && results.count == 1) {
//                    var record = results.records[0];
//                }

                        this.uploadPanel.statusText.setText('Uploaded ' + file.name + ' successfully.');
                        return;
                    }

                    if (response && !response.success)
                    {
                        if (response.status === 208)
                        {
                            // File exists - mark the file as an error so the user isn't presented with the "Edit Properties" dialog
                            file.status = Dropzone.ERROR;
                            file.errorText = 'Already exists';
                            file.code = response.status;
                        }
                        else if (response.status === 401 || response.status === 403)
                        {
                            file.status = Dropzone.ERROR;
                            file.errorText = 'Unauthorized';
                            file.code = response.status;
                            file.message = "You do not have privileges to upload." + (LABKEY.user.isGuest ? " Verify that you are signed in appropriately." : "");
                            Ext4.Msg.show({
                                title: "Unauthorized",
                                msg: file.message,
                                cls : 'data-window',
                                icon: Ext4.Msg.ERROR,
                                buttons: Ext4.Msg.OK
                            });
                        }
                        else
                        {
                            file.status = Dropzone.ERROR;
                            file.errorText = 'Code: ' + response.status;
                            file.code = response.status;
                            file.message = response.exception;
                            var xhr = evt.target;
                            this.emit('error', file, response.exception, xhr);
                        }
                    }
                    else
                    {
                        this.uploadPanel.statusText.setText('Uploaded ' + file.name + ' successfully.');
                    }
                });

                this.on('error', function (file, message, xhr) {
                    var title = 'Error';
                    var code = null;
                    // NOTE: we do not get a xhr response from labkey/_webdav
                    if (xhr !== undefined && xhr.readyState === 4)
                    {
                        // here we should be able to provide a more meaningful message
                        if (xhr.status === 0 && xhr.statusText === "" && xhr.responseText === "")
                        {
                            title = "Locked/Connection";
                            message = "The file could not be uploaded. The file may be locked or the connection to the server may have been interrupted. Please try again, and if the problem persists, check the file, your firewall, proxy server, and antivirus web protection settings.";
                        }
                        else if (xhr.status === 200)
                        {
                            if (xhr.responseText)
                            {
                                var resp = Ext4.JSON.decode(xhr.responseText);
                                code = resp.status;
                                if (resp.status === 503)
                                    title = "Connection";
                                else
                                    title = "Unauthorized/Connection";
                            }
                            else {
                                title = "Unauthorized/Connection";
                            }
                            message = message || "You do not have privileges to upload." + (LABKEY.user.isGuest ? " Verify that you are signed in appropriately." : " Or the connection to the server may have been interrupted.")
                        }
                    }
                    else if (xhr === undefined && message && -1 !== message.indexOf("Drag-and-drop upload"))
                    {
                        title = "Drag-and-drop upload";
                    }

                    file.status = Dropzone.ERROR;
                    file.errorText = title;
                    file.code = code;
                    file.message = 'Error uploading ' +
                            (file.fullPath ? file.fullPath : file.name) + (message ? (': ' + message) : '');
                    this.uploadPanel.statusText.setText(file.message);
                    this.uploadPanel.showErrorMsg(title, file.message);
                });

                //this.on('complete', function (file) {
                //});

                this.on('canceled', function (file) {
                    this.uploadPanel.statusText.setText('Canceled upload of ' + file.name);
                    this.uploadPanel.setBusy(false);
                    getMaskElement(this.uploadPanel).unmask();
                });

                this.on('queuecomplete', function () {
                    this.uploadPanel.setBusy(false);
                    getMaskElement(this.uploadPanel).unmask();

                    var countErrorFiles = 0;
                    var errorFiles = [];
                    var fileRecords = [];
                    var file0 = null;
                    var dragDropError = false;
                    var firstMessage = null;
                    var alreadyExistFiles = [];

                    for (var i = 0; i < this.files.length; i++) {
                        var file = this.files[i];
                        if (file.status === Dropzone.SUCCESS) {
                            fileRecords.push({data: {name: file.name, id: file.uri, href: file.uri}});
                        }
                        else if (file.status === Dropzone.ERROR) {
                            if (file.errorText === 'Drag-and-drop upload') {
                                dragDropError = true;
                            }
                            else if (file.code === 208) {
                                alreadyExistFiles.push(file);
                            }
                            else {
                                if (!firstMessage) {
                                    firstMessage = file.message;
                                }
                                if (countErrorFiles % 2 === 0) {       // even
                                    file0 = file;
                                }
                                else {
                                    errorFiles.push({
                                        name0: file0.fullPath ? file0.fullPath : file0.name,
                                        text0: file0.errorText,
                                        code0: file0.code,
                                        name1: file.fullPath ? file.fullPath : file.name,
                                        text1: file.errorText,
                                        code1: file.code
                                    });
                                    file0 = null;
                                }
                                countErrorFiles += 1;
                            }
                        }
                    }
                    if (file0) {
                        errorFiles.push({
                            name0: file0.fullPath ? file0.fullPath : file.name,
                            text0: file0.errorText,
                            code0: file0.code,
                            name1: '',
                            text1: '',
                            code1: ''
                        });   // last if number is odd
                    }

                    if (!dragDropError && countErrorFiles > 1) {
                        var data = {
                            numErrors: countErrorFiles,
                            total: this.files.length,
                            files: errorFiles,
                            message: firstMessage
                        };
                        var tpl = Ext4.create('Ext.XTemplate',
                                '<div>',
                                '<p style="font-size:medium">{numErrors} of {total} files failed to upload.</p>',
                                '<p style="width: 700px;">Possible reason: {message}</p>',
                                '<table class="table-xtab-report">',
                                    '<tr><th>File</th><th>Reason</th><th>Status</th><th>&nbsp;</th><th>File</th><th>Reason</th><th>Status</th></tr>',
                                    '<tpl for="files">',
                                        '<tr><td>{name0:htmlEncode}</td><td>{text0:htmlEncode}</td><td>{code0:htmlEncode}</td><td>&nbsp;</td><td>{name1:htmlEncode}</td><td>{text1:htmlEncode}</td><td>{code1:htmlEncode}</td></tr>',
                                    '</tpl>',
                                '</table>',
                                '</div>');
                        Ext4.Msg.alert({
                            title: 'Upload Failure',
                            msg: tpl.apply(data),
                            maxHeight: 2000,
                            maxWidth: 1200,
                            buttons: Ext4.MessageBox.OK,
                            scope: this,
                            fn: function(btn, text) {
                                this.uploadPanel.fireEvent('transfercomplete', {fileRecords: fileRecords});
                            }
                        });

                    }
                    else if (!dragDropError && alreadyExistFiles.length > 0 && countErrorFiles == 0) {
                        var msg = '';
                        if (alreadyExistFiles.length === 1)
                            msg = "There is already a file named " + Ext4.htmlEncode(alreadyExistFiles[0].name) + ' in this location. Would you like to replace it?';
                        else if (alreadyExistFiles.length <=10) {
                            msg = "The following files already exist in this location. Would you like to replace them?<br><ul>";
                            Ext4.each(alreadyExistFiles, function(f) {
                                msg += "<li>" + Ext4.htmlEncode(f.name) + "</li>"
                            }, this);
                            msg += "</ul>";
                        }
                        else {
                            msg = "The following files already exist in this location. Would you like to replace them?<br><ul>";
                            var count = 0;
                            Ext4.each(alreadyExistFiles, function(f) {
                                if (count++ >= 9)
                                    return;
                                msg += "<li>" + Ext4.htmlEncode(f.name) + "</li>"
                            }, this);
                            msg += "</ul>";
                            msg += "and " + (alreadyExistFiles.length - 9) + " more...";
                        }

                        Ext4.Msg.show({
                            title : "File Conflict:",
                            msg : msg,
                            cls : 'data-window',
                            icon : Ext4.Msg.QUESTION,
                            buttons : Ext4.Msg.YESNO,
                            fn : function(btn) {
                                if (btn == 'yes') {
                                    Ext4.each(alreadyExistFiles, function(f) {
                                        f.overwrite = true;
                                        f.status = Dropzone.ADDED;
                                        this.processFile(f);
                                    }, this);
                                } else {
                                    this.uploadPanel.fireEvent('transfercomplete', {fileRecords: fileRecords});
                                }
                            },
                            scope : this
                        });
                    }
                    else {
                        this.uploadPanel.fireEvent('transfercomplete', {fileRecords: fileRecords});
                    }

                    this.removeAllFiles();
                });

            }
        });

        this.dropzone = dropzone;
        dropzone.uploadPanel = this;
    },

    getOuterPanel : function() {
        /**
         * This panel contains the single/multiple upload panels
         */
        var uploadsPanel = Ext4.create('Ext.panel.Panel', {
            layout : 'card',
            deferredRender : true,
            activeItem : 0,
            border : false,
            cls : 'single-upload-panel',
            items : [this.getSingleUpload(), this.getMultiUpload()]
        });

        var narrowUploadsPanel = Ext4.create('Ext.panel.Panel', {
            layout : 'card',
            deferredRender : true,
            activeItem : 0,
            width: 238,
            border : false,
            cls : 'single-upload-panel',
            items : [this.getMultiUpload()]
        });

        var uploadsContainer = Ext4.create('Ext.container.Container', {
            layout: this.narrowLayout ? 'vbox' : 'hbox',
            height: this.narrowLayout ? 60 : 30,
            items: [this.narrowLayout ? narrowUploadsPanel : uploadsPanel]
        });

        var closeBtn = Ext4.create('Ext.button.Button', {
            iconCls: 'fa fa-times',
            tooltip: 'Close the file upload panel',
            style: 'background-color: transparent;',
            scope: this,
            border : false,
            handler: function() {
                this.hide();
            }
        });

        var helpBtn = Ext4.create('Ext.button.Button', {
            iconCls: 'fa fa-info-circle',
            tooltip: 'File upload help',
            style: 'background-color: transparent;',
            allowDepress: false,
            scope: this,
            border : false,
            handler: this.showHelpMessage
        });

        var btnContainer = Ext4.create('Ext.container.Container', {
            layout: 'hbox',
            height: 30,
            items: [helpBtn, closeBtn]
        });

        var outerContainer = Ext4.create('Ext.container.Container', {
            layout: {type: 'hbox', align: 'stretch'},
            height: this.narrowLayout ? 60 : 30,
            // the flex box here eats up all the middle real estate to kick the btnContainer all the way right
            items: [uploadsContainer, {xtype: 'box', flex: 1}, btnContainer]
        });

        return [outerContainer];
    },

    getSingleUpload : function() {

        if (this.singleUpload) {
            return this.singleUpload;
        }

        var uploadId = Ext4.id();

        this.descriptionField = Ext4.create('Ext.form.field.Text', {
            name  : 'description',
            fieldLabel : 'Description',
            labelAlign : 'right',
            width : 300,
            margin: '2 0 0 0',
            disabled : true
        });

        this.singleUpload = Ext4.create('Ext.form.Panel', {
            border : false,
            frame : false,
            cls: this.bodyCls,
            items  : [{
                xtype: 'container',
                width: 900,
                layout: 'hbox',
                items: [{
                    xtype: 'filefield',
                    name : 'file',
                    width: 350,
                    fieldLabel: 'Choose a File',
                    labelAlign: 'right',
                    buttonText: 'Browse',
                    clearOnSubmit: false, // allows form to be resubmitted in case of file overwrite
                    listeners: {
                        render: function(f) { this.fileField = f; },
                        change: function() {
                            this.descriptionField.setDisabled(false);
                            this.descriptionField.focus();
                            Ext4.getCmp(uploadId).setDisabled(false);
                        },
                        scope : this
                    }
                },
                this.descriptionField,
                {
                    xtype: 'button',
                    id: uploadId,
                    text: 'Upload',
                    margin: '0 0 0 5',
                    disabled: true,
                    handler: this.submitFileUploadForm,
                    scope : this
                },
                { xtype: 'label', text: 'Or drag/drop files below.', margin: '4 0 0 10'}],
            },
            ]
        });

        return this.singleUpload;
    },


    getMultiUpload: function() {
        if (this.multiUpload) {
            return this.multiUpload;
        }

        var helpLinkHtml =  '[<a class="help-link" href="javascript:void(0);">upload help</a>]';

        var html;
        if (window.Dropzone && window.Dropzone.isBrowserSupported()) {
            html = "To upload, drag files " + (!Ext4.isIE ? "and folders " : "") +
                    "from your desktop onto this file browser.";
        }
        else {
            html = "Your web browser doesn't support drag and drop uploading of files.<br>" +
                    "You can upgrade your web browser or upload multiple files using an external " +
                    LABKEY.Utils.getSimpleLinkHtml('webdav', 'WebDAV client') + ".";
        }

        this.multiUpload = Ext4.create('Ext.panel.Panel', {
            border: false,
            cls: this.bodyCls,
            items: [{
                xtype: 'container',
                html: html + "<p>" + helpLinkHtml
            }],
            listeners: {
                afterrender: function (container) {
                    var helpLink = container.getEl().down('a.help-link');
                    if (helpLink) {
                        helpLink.on('click', this.showHelpMessage, this);
                    }
                },
                scope: this
            }
        });

        return this.multiUpload;
    },

    showHelpMessage : function ()
    {
        var url = this.getCurrentWebdavURL();

        var msg = [
            'To upload files from your computer, you can drag-and-drop them onto the file listing.',
            '<p>',
            'You can also use ',
            LABKEY.Utils.getSimpleLinkHtml('webdav', 'WebDAV'),
            'to transfer files to and from this folder using the Mac Finder, ' +
            'Windows Explorer or file transfer programs like <a target=_blank href="http://cyberduck.io/">CyberDuck</a>. The WebDav URL for this folder is:',
            '</p>',
            '<textarea style="font-family:monospace" readonly wrap="hard" cols="62" rows="3" size=' + url.length + '>' + Ext4.util.Format.htmlEncode(url) + '</textarea>',
            '<p>For more information on transferring files, please see the',
            LABKEY.Utils.getSimpleLinkHtml('fileUpload', 'file upload'),
            'help documentation.</p>'
        ];

        if (!Dropzone.isBrowserSupported()) {
            msg.push('<p><i><b>NOTE:</b> Your web browser does not support drag-and-drop upload.  Please consider upgrading your web browser or using a WebDAV client.</i>');
        }

        Ext4.Msg.show({
            title: "File Upload Help",
            msg: msg.join(' '),
            cls: "data-window",
            icon: Ext4.Msg.INFO,
            buttons: Ext4.Msg.OK
        });
    },

    cancelUpload : function () {
        if (this.dropzone) {
            this.dropzone.removeAllFiles(true);
        }
    },

    submitFileUploadForm : function(fb, v) {

        var cwd = this.getWorkingDirectory('cwd');

        if (cwd) {
            var form = this.singleUpload.getForm();
            var path = this.fileField.getValue();
            var i = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
            var name = path.substring(i+1);
            if (name.length == 0) {
                this.uploadPanel.showErrorMsg('Error', 'No file selected. Please choose one or more files to upload.');
                return;
            }

            var lastModifiedParam = '';
            var file = this.fileField.getEl().down('input[type=file]').dom.files[0];
            if (file && file.lastModified) {
                lastModifiedParam = '&X-LABKEY-Last-Modified=' + file.lastModified;
            }

            this.doPost = function(overwrite) {
                var options = {
                    method:'POST',
                    form : form,
                    url : this.fileSystem.getURI(cwd, true) + '?Accept=application/json&overwrite=' + (overwrite ? 'T' : 'F') + "&X-LABKEY-CSRF=" + LABKEY.CSRF + lastModifiedParam,
                    name : name,
                    success : function(f, action, message) {

                        // File upload success response is same as PROPFIND, error response is JSON.

                        // Issue 21100: IE8 responseText is the XML PROPFIND response
                        // ExtJS creates a fake response object for file upload forms in iframes so we can't check for a 'Content-Type' header before parsing as JSON.
                        // See: http://docs.sencha.com/extjs/4.2.1/#!/api/Ext.data.Connection-method-onUploadComplete
                        // Unfortunately in IE8, the responseText string is the PROPFIND xml instead of empty string as in other browsers.
                        // We can remove this block that checks for success after we drop support for IE8
                        var success = false;
                        if (action.response.responseXML && form.errorReader)
                        {
                            var records = form.errorReader.read(action.response.responseXML);
                            if (records && records.count > 0)
                            {
                                // If we parsed the PROPFIND xml response, the file was uploaded successfully
                                success = true;
                            }
                        }

                        var txt = (action.response.responseText || "").trim();
                        if (!success && txt)
                        {
                            var response = Ext4.JSON.decode(txt);
                            if (response && !response.success)
                            {
                                if (response.status == 208)
                                {
                                    Ext4.Msg.show({
                                        title : "File Conflict:",
                                        msg : "There is already a file named " + name + ' in this location. Would you like to replace it?',
                                        cls : 'data-window',
                                        icon : Ext4.Msg.QUESTION,
                                        buttons : Ext4.Msg.YESNO,
                                        fn : function(btn) {
                                            if (btn == 'yes')
                                                this.doPost(true);
                                        },
                                        scope : this
                                    });
                                }
                                else if (response.status == 401 || response.status == 403)
                                {
                                    Ext4.Msg.show({
                                        title: "Unauthorized",
                                        msg: "You do not have privileges to upload." + (LABKEY.user.isGuest ? " Verify that you are signed in appropriately." : ""),
                                        cls : 'data-window',
                                        icon: Ext4.Msg.ERROR,
                                        buttons: Ext4.Msg.OK
                                    });
                                }
                                else
                                    this.showErrorMsg('Error', response.exception);

                                return;
                            }
                        }

                        this.singleUpload.getForm().reset();
                        this.fileField.setRawValue(null);
                        this.descriptionField.setDisabled(true);
                        this.singleUpload.down('.button[text=Upload]').setDisabled(true);

                        this.fireEvent('transfercomplete', {fileNames : [{name:name}]});
                    },
                    failure : LABKEY.Utils.displayAjaxErrorResponse,
                    scope : this
                };
                form.errorReader = new Ext4.data.reader.Xml({
                    record : 'response',
                    root : 'multistatus',
                    model : 'File.data.webdav.XMLResponse'
                });
                // set errorReader, so that handleResponse() doesn't try to eval() the XML response
                // assume that we've got a WebdavFileSystem
//                form.errorReader = this.fileSystem.transferReader;
                form.doAction(new Ext4.form.action.Submit(options));
                this.fireEvent('transferstarted');
            };

            this.doPost(false);
        }
    },

    changeWorkingDirectory : function(path, model, cwd) {
        this.workingDirectory = {path: path, model: model, cwd: cwd};
        this.updateDropzoneEnabled();
        this.fireEvent('cwd', model, path);
    },

    onLoad : function() {
        this.updateDropzoneEnabled();
    },

    updateDropzoneEnabled : function () {
        if (this.dropzone) {
            var record = this.dropzone.uploadPanel.getWorkingDirectory('model');
            var canWrite = this.dropzone.uploadPanel.fileSystem.canWrite(record);
            this.dropzone.setEnabled(canWrite);
        }
    },

    getWorkingDirectory : function(variable) {
        if (this.workingDirectory) {
            return this.workingDirectory[variable];
        }
        console.error('Upload: working directory not set.');
    },

    getCurrentWebdavURL : function () {
        var cwd = this.getWorkingDirectory('cwd');
        if (cwd)
            return this.fileSystem.getURI(cwd);
        else
            return this.fileSystem.getAbsoluteURL();
    },

    showErrorMsg : function(title, msg) {
        Ext4.Msg.show({
            title: title,
            msg: msg,
            cls : 'data-window',
            icon: Ext4.Msg.ERROR,
            buttons: Ext4.Msg.OK
        });
    },

    getUploadStatusWindow : function() {
        if (this.uploadStatusWindow)
            return this.uploadStatusWindow;

        this.progressBar = Ext4.create('Ext.ProgressBar', {
            width: 500,
            height: 25,
            border: false,
            autoRender : true,
            style: 'background-color: transparent; -moz-border-radius: 5px; -webkit-border-radius: 5px; -o-border-radius: 5px; -ms-border-radius: 5px; -khtml-border-radius: 5px; border-radius: 5px;'
        });

        var progressBarContainer = Ext4.create('Ext.container.Container', {
            width: 500,
            margin: 4,
            items: [this.progressBar]
        });

        this.statusText = Ext4.create('Ext.form.Label', {
            text: '',
            style: 'display: inline-block ;text-align: center',
            width: 500,
            margin: 4,
            border: false
        });

        this.uploadStatusWindow = Ext4.create('Ext.window.Window', {
            title: 'Upload Progress',
            layout: 'vbox',
            bodyPadding: 5,
            closable: false,
            border: false,
            items: [this.statusText, progressBarContainer],
            buttons: [{
                text: 'Cancel Upload',
                handler: this.cancelUpload,
                scope: this
            }]
        });

        return this.uploadStatusWindow;
    },

    showUploadWindow : function()
    {
        if (this.uploadStatusWindow)
        {
            this.uploadStatusWindow.show();
            this.uploadStatusWindow.center();
        }
        if (this.progressBar)
            this.progressBar.setVisible(true);
    },

    hideUploadWindow : function()
    {
        if (this.uploadStatusWindow)
        {
            this.uploadStatusWindow.hide();
            this.uploadStatusWindow.center();
        }
        if (this.progressBar)
            this.progressBar.reset(true);
        if (this.statusText)
            this.statusText.setText('');
    }
});


