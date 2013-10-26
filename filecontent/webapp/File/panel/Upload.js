/*
 * Copyright (c) 2012-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('File.panel.Upload', {

    extend : 'Ext.panel.Panel',

    layout : 'table',

    columns : 2,

    bodyPadding: 5,

    border : false,

    separator: Ext4.isWindows ? "\\" : "/",

    rootPath: "/",

    baseURL : LABKEY.contextPath + "/_webdav",

    allowSingleDrop : true,

    lastSummary: {info:0, success:0, file:'', pct:0},

    header : false,

    TRANSFER_STATES: {
        success   : 1,
        info      : 0,
        failed    : -1,
        retryable : -2
    },

    constructor : function(config) {

        this.callParent([config]);

        this.addEvents('cwd', 'transferstarted', 'transfercomplete');
    },

    initComponent : function() {

        this.items = this.getItems();

        this.callParent();
    },

    // From FileSystem.js
    getPrefixUrl: function() {
        var prefix = '';

        prefix = this.concatPaths(this.baseURL, this.rootPath);

        if (prefix.length > 0 && prefix.charAt(prefix.length-1) == this.separator){
            prefix = prefix.substring(0,prefix.length-1);
        }

        return prefix;
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
        return this.getOuterPanel();
    },

    getOuterPanel : function() {
        /**
         * This panel contains the radio buttons to select single/multi
         */
        var radioPanel = {
            xtype   : 'panel',
            layout  : 'form',
            width: 140,
            border : false,
            margins: '0 0 0 40',
            items : [{
                xtype     : 'radiogroup',
                width     : 110,
                columns   : 1,
                hideLabel : true,
                items     : [{
                    boxLabel : 'Single&nbsp;file',
                    name     : 'rb-file-upload-type',
                    checked  : true,
                    handler  : function(cmp, checked) {
                        if(checked){
                            uploadsPanel.getLayout().setActiveItem(this.getSingleUpload());
                        }
                    },
                    scope    : this
                },{
                    boxLabel : 'Multiple&nbsp;files',
                    name     : 'rb-file-upload-type',
                    handler  : function(cmp, checked) {
                        if(checked){
                            uploadsPanel.getLayout().setActiveItem(this.getMultiUpload());
                            this.onMultiUpload();
                        }
                    },
                    scope    : this
                }]
            }]
        };

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

        // Configure Drag / Drop Events
//        if (this.allowSingleDrop) {
//            Ext4.getBody().on('dragenter', function(ev) {
//                if (ev.currentTarget.id == 'bodyElement') {
//                    this.getEl().mask('Drop File Here');
//                }
//            }, this);
//            Ext4.getBody().on('dragleave', function(evt) {
//                if (evt.pageX < 10 || evt.pageY < 10 || Ext4.getBody().getWidth() - evt.pageX < 10 || Ext4.getBody().getHeight() - evt.pageY < 10) {
//                    this.getEl().unmask();
//                }
//            });
//            Ext4.getBody().on('dragexit', function(ev) { console.log('drag exit.'); });
//        }


//        Ext4.create('Ext.panel.Panel', {
//            items: [this.getAppletStatusBar()]
//        });

        var uploadsContainer = Ext4.create('Ext.container.Container', {
            layout: 'hbox',
            height: 100,
            items: [radioPanel, uploadsPanel]
        });

        var outerContainer = Ext4.create('Ext.container.Container', {
            layout: 'vbox',
            height: 130,
            items: [this.getAppletStatusBar(), uploadsContainer]
        });

        return [outerContainer];
    },

    getSingleUpload : function() {

        if (this.singleUpload) {
            return this.singleUpload;
        }

        var uploadId = Ext4.id();

        var descriptionField = Ext4.create('Ext.form.field.Text', {
            name  : 'description',
            fieldLabel : 'Description',
            labelSeparator : '',
            labelAlign : 'right',
            width : 382,
            margin: '5 0 0 0',
            disabled : true
        });

        this.singleUpload = Ext4.create('Ext.form.Panel', {
            border : false,
            frame : false,
            items  : [{
                xtype: 'container',
                width: 800,
                layout: 'hbox',
                items: [{
                    xtype: 'filefield',
                    name : 'file',
                    width: 452,
                    fieldLabel: 'Choose a File',
                    labelAlign: 'right',
                    buttonText: 'Browse',
                    labelSeparator: '',
                    listeners: {
                        render: function(f) { this.fileField = f; },
                        change: function() {
                            descriptionField.setDisabled(false);
                            descriptionField.focus();
                            Ext4.getCmp(uploadId).setDisabled(false);
                        },
                        scope : this
                    }
                },{
                    xtype: 'button',
                    id: uploadId,
                    text: 'Upload',
                    cls: 'upload-button',
                    disabled: true,
                    handler: this.submitFileUploadForm,
                    scope : this
                }]
            }, descriptionField]
        });

        return this.singleUpload;
    },


    getMultiUpload: function() {
        if(this.multiUpload){
            return this.multiUpload;
        }

        var testJavaHtml =  '<span id="testJavaLink"><br>[<a target=_blank href="http://www.java.com/en/download/testjava.jsp">test java plugin</a>]</span>';
        var loadingImageSrc = LABKEY.contextPath + "/" + LABKEY.extJsRoot + "/resources/images/default/shared/large-loading.gif";

        var buttonPanel = Ext4.create('Ext.panel.Panel', {
            border: false,
            layout: 'vbox',
            width: 135,
            bodyPadding: 5,
            defaults: {
                margins: '0 0 5 0'
            },
            items: [
                {xtype:'button', text: 'Choose File', handler: function(){
                    if (this.transferApplet) {
                        var appletEl = this.transferApplet.getApplet();
                        if (appletEl){
                            appletEl.showFileChooser();
                        }
                    }
                }, scope: this},
                {xtype:'button', text:'Choose folder', handler: function(){
                    if (this.transferApplet) {
                        var appletEl = this.transferApplet.getApplet();
                        if (appletEl){
                            appletEl.showDirectoryChooser();
                        }
                    }
                }, scope: this},
                {xtype:'button', text:'Drag and drop', handler: function(){
                    if (this.transferApplet) {
                        var appletEl = this.transferApplet.getApplet();
                        if (appletEl){
                            appletEl.openDragAndDropWindow();
                        }
                    }
                }, scope: this}
            ]
        });

        this.appletPanel = Ext4.create('Ext.form.Panel', {
            border: false,
            fieldLabel: 'Upload Tool',
            width: 90,
            height: 90,
            items: [{
                xtype: 'container',
                html:'<img src="' + loadingImageSrc + '"><br>Loading Java applet...' + testJavaHtml
            }]
        });

        this.appletContainer = Ext4.create('Ext.form.Panel', {
            border: false,
            margins: '0 0 0 10',
            buttonAlign: 'left',
            layout: 'hbox',
            width: 230,
            height: 100,
            items: [this.appletPanel, buttonPanel]
        });

        this.multiUpload = Ext4.create('Ext.panel.Panel', {
            border: false,
            items: [this.appletContainer]
        });

        return this.multiUpload;
    },

    onMultiUpload: function(){
//        this.hideProgressBar(); // need to implement.
//        this.lastSummary= {info:0, success:0, file:'', pct:0}; // need to implement
//        this.progressRecord = null; // need to implement
        if (!this.transferApplet) {

            this.transferApplet = Ext4.create('File.panel.TransferApplet',{
                id: Ext4.id(),
                url: this.fileSystem.getAbsoluteBaseURL(),
                directory: '',
                text: 'initializing...',
                width: 90,
                height: 90
            });

            this.transferApplet.on('update', this.updateProgressBar, this);
            this.transferApplet.on("progressRecordUpdate", this.updateProgressBarRecord, this);

            // Not calling fireUploadEvents as it does not seem to be necessary. It was used to detect duplicate files
            // and display warnings, but that doesn't seem to actually work anymore.
//            this.transferApplet.on('update', this.fireUploadEvents, this);

            this.transferApplet.onReady(function(){
                // Update applet state.
                this.updateAppletState(this.getWorkingDirectory());
                this.on('cwd', this.updateAppletState);

            }, this);

            this.appletPanel.removeAll();
            this.appletPanel.add(this.transferApplet);
        }
    },

    updateAppletState: function(record){
        // TODO: actually finish this. Need to port it from fileBrowser.js line 2011

        if(!this.transferApplet || !record){
            return;
        }

        var canWrite = this.fileSystem.canWrite(record);
        var canMkdir = this.fileSystem.canMkdir(record);
        // Enable or disable applet buttons depending on permissions (canWrite and canMkDir)

        try {
            this.transferApplet.changeWorkingDirectory(record.data.id);
            if (canWrite || canMkdir) {
                this.transferApplet.setEnabled(true);
                this.transferApplet.setAllowDirectoryUpload(canMkdir);
                // I think setText is not used anymore. Doesnt seem to actually work on old filebrowser.
//                this.transferApplet..setText(canMkdir ? "Drag and drop files and folders here\ndirectly from your computer" : "Drag and drop files here directly\nfrom your computer")); // + "\nFolder: " +record.data.name);
            } else {
                this.transferApplet.setEnabled(false);
                // I think setText is deprecated or non-working on the old file browser.
//                this.transferApplet.setText("(read-only)\nFolder: " +record.data.name);
            }
        } catch (e){
            console.error(e);
        }
    },

    getAppletStatusBar: function(){
        if (this.appletStatusBar) {
            return this.appletStatusBar;
        }

        this.progressBar = Ext4.create('Ext.ProgressBar', {
            width: 200,
            height: 25,
            border: false,
            hidden: true
        });

        this.progressBarContainer = Ext4.create('Ext.container.Container', {
            width: 200,
            items: [this.progressBar]
        });

        this.statusText = Ext4.create('Ext.form.Label', {
            text: '',
            margins: '0 0 0 10',
            width: 150,
            border: false
        });

        this.appletStatusBar = Ext4.create('Ext.panel.Panel', {
            width: 350,
            border: false,
            height: 25,
            layout: 'hbox',
            items: [this.progressBarContainer, this.statusText]
        });

        return this.appletStatusBar;
    },

    updateProgressBarRecord: function(store, record){
        var state = record.get('state');
        var progress = record.get('percent' || 0) / 100;

        if(state === 0 && 0 < progress && progress < 1.0){
            this.progressRecord = record;
        } else if (state != 0 && this.progressRecord == record){
            this.progressRecord = null;
        }
    },

    updateProgressBar: function()
    {
        var record = this.progressRecord && this.progressRecord.get('state') == 0 ? this.progressRecord : null;
        var pct = record ? record.get('percent')/100 : 0;
        var file = record ? record.get('name') : '';

        var summary = this.transferApplet.getSummary();

        if (summary.info != this.lastSummary.info)
        {
            if (summary.info == 0)
            {
                this.statusText.setText('Ready');
            }
            else
            {
                this.statusText.setText('Copying... ' + summary.info + ' file' + (summary.info > 1 ? 's' : ''));
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
        {
            this.progressBar.hide();
        }

        // UNDONE: failed transfers
        this.lastSummary = summary;
        this.lastSummary.pct = pct;
        this.lastSummary.file = file;
    },

    submitFileUploadForm : function(fb, v) {

        var cwd = this.getWorkingDirectory();

        if (cwd) {
            var form = this.singleUpload.getForm();
            var path = this.fileField.getValue();
            var i = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
            var name = path.substring(i+1);
            if (name.length == 0) {
                Ext4.Msg.alert('Error', 'No file selected. Please choose one or more files to upload.');
                return;
            }

            var file = false; // TODO: Check if the file already exists
            var uri = this.fileSystem.getURI(cwd);

            this.doPost = function(overwrite) {
                var options = {
                    method:'POST',
                    form : form,
                    url : overwrite ? uri + '?overwrite=t' : uri, // TODO: This is not correct
                    name : name,
                    success : function() {
                        form.reset();
                        this.getEl().unmask();
                        this.fireEvent('transfercomplete');
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
//                this.fireEvent(LABKEY.FileSystem.BROWSER_EVENTS.transferstarted, {uploadType:"webform", files:[{name:name, id:new LABKEY.URI(this.fileSystem.concatPaths(options.url, encodeURIComponent(options.name))).pathname}]});
                this.getEl().mask("Uploading " + name + '...');
            };

            if (file) {
                Ext4.Msg.confirm('File: ' + name + ' already exists', 'There is already a file with the same name in this location, do you want to replace it? ', function(btn){
                    if (btn == 'yes') {
                        this.doPost(true);
                    }
                }, this);
            }
            else {
                this.doPost(false);
            }
        }
    },

    changeWorkingDirectory : function(path, model, cwd) {
        this.workingDirectory = cwd;
        this.fireEvent('cwd', this.getWorkingDirectory());
    },

    getWorkingDirectory : function() {
        if (this.workingDirectory) {
            return this.workingDirectory;
        }
        console.error('Upload: working directory not set.');
    }
});


