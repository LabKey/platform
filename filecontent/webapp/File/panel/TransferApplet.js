/*
 * Copyright (c) 2012-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('File.panel.TransferModel', {

    extend: 'Ext.data.Model',

    fields: [
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
    ]
});

Ext4.define('File.panel.ConsoleModel', {

    extend: 'Ext.data.Model',

    fields: ['level', 'text']
});

Ext4.define('File.panel.TransferApplet', {
    extend: 'File.panel.Applet',

    transferReader  : null,

    transfers       : null,

    // INT's taken from FileSystem.js
    FATAL_INT       : 50000,

    ERROR_INT       : 40000,

    WARN_INT        : 30000,

    INFO_INT        : 20000,

    DEBUG_INT       : 10000,

    TRANSFER_STATES: {
        success   : 1,
        info      : 0,
        failed    : -1,
        retryable : -2
    },

    constructor     : function(params){
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

        this.callParent([config]);
    },

    initComponent   : function(){
        this.callParent(this);

        this.addEvents(['update']);

        this.transfers = Ext4.create('Ext.data.Store', {
            model: 'File.panel.TransferModel',
            proxy: {
                type: 'memory',
                root: 'records',
                reader: {
                    type: 'json',
                    successProperty: 'success',
                    idProperty: 'target'
                }
            }
        });

        this.transfers.on('add', function(store, records){
            for(var i = 0; i < records.length; i++){
                console.debug('TransferApplet.add: ' + records[i].get('uri') + ' ' + records[i].get('status'));
            }
        });

        this.console = Ext4.create('Ext.data.Store', {
            model: 'File.panel.ConsoleModel',
            proxy: {
                type: 'memory',
                reader: {
                    type:'json',
                    successProperty: 'success',
                    totalProperty: 'records'
                }
            }
        });
    },

    destroy : function(){
        // This might not be needed. I'm not sure we're going to have a pollTask.
        Ext4.util.TaskManager.stop(this.pollTask);
        this.callParent(this);
    },

    onRender : function(ct, position){
        this.callParent(this, ct, position);
        this.pollTask = {interval: 100, scope: this, run:this._poll};
        Ext4.util.TaskManager.start(this.pollTask);
    },

    /* private */
    _poll : function(){
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

    changeWorkingDirectory: function(path){
        var applet = this.getApplet();
        if(applet){
            applet.changeWorkingDirectory(path);
        } else if(!this.rendered){
            this.params.directory = path;
        } else {
            console.error('TransferApplet not initialized yet. Do not call before isReady().');
        }
    },

    setEnabled  : function(enabled){
        var applet = this.getApplet();

        if(applet){
            applet.setEnabled(enabled);
        } else if(!this.rendered){
            this.params.enabled = enabled ? 'true' : 'false';
        } else {
            console.error('TransferApplet not initialized yet. Do not call before isReady().');
        }
    },

    setText     : function(text){
        var applet = this.getApplet();

        if(applet){

        } else if(!this.rendered){
            this.params.text = text;
        } else {
            console.error('TransferApplet not initialized yet. Do not call before isReady().');
        }
    },

    setAllowDirectoryUpload: function(allow){
        var applet = this.getApplet();
        if(applet){
            applet.setAllowDirectoryUpload(allow);
        } else if(!this.rendered){
            this.params.allowDirectoryUpload = allow;
        } else {
            console.error('TransferApplet not initialized yet. Do not call before isReady().');
        }
    },

    getTransfers: function(){
        return this.transfers;
    },

    getSummary  : function() {
        var success=0, info=0, failed=0, retryable=0;
        var transfers = this.transfers;
        var count = transfers.getCount();

        for (var i = 0 ; i < count ; i++)
        {
            var record = transfers.getAt(i);
            var state = record.data.state;
            switch (state)
            {
                case this.TRANSFER_STATES.success: success++; break;
                case this.TRANSFER_STATES.info: info++; break;
                case this.TRANSFER_STATES.failed: failed++; break;
                case this.TRANSFER_STATES.retryable: retryable++; break;
            }
        }
        return {success:success, info:info, failed:failed, retryable:retryable,
            total:count, totalActive:success+info+failed};
    },

    // EVENTS
    dragEnter   : function(){
        // no-op for now.
    },

    dragExit    : function(){
        // no-op for now.
    },

    _merge: function(store, records){
        var adds = [];
        for(var i = 0; i < records.length; i++){
            var update = records[i];
            var id = update.getId();
            var record = store.getById(id);

            if(null == record){
                adds.push(update);
            } else {
                record.beginEdit();
                record.set(update.data);
                record.endEdit();
            }
        }

        store.add(adds);
    },

    update : function(){
        var applet = this.getApplet();
        var r, result, records;
        var updated = false;

        if(applet.transfer_hasUpdates()){
            r = applet.transfer_getObjects();
            result = eval("(" + r + ")");

            if(!result.success){
                console.error(r);
            }

            records = this.transfers.getProxy().getReader().readRecords(result.records);
            this._merge(this.transfers, records.records);
            updated = true;
        }

        var consoleLineCount = applet.console_getLineCount();
        
        if(consoleLineCount > this.console.getCount()){
            r = applet.console_getRange(this.console.getCount(), consoleLineCount);
            result = eval("(" + r + ")");
            records = result.records;
            this.console.add(records);

            for(var i = 0; i < records.length; i++){
                var record = records[i];
                var level = record.level;
                var text = record.text;

                if(!text){
                    continue;
                }

                if(level >= this.ERROR_INT){
                    console.error(text);
                } else if(level >= this.WARN_INT){
                    console.warn(text);
                } else if(level >= this.INFO_INT) {
                    console.info(text);
                } else {
                    console.debug(text);
                }
            }
        }

        if(updated){
            this.fireEvent("update");
        }
    }
});
