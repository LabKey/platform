/*
 * Copyright (c) 2014-2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext.define('LABKEY.app.model.Message', {

    extend: 'Ext.data.Model',

    fields: [
        {name: 'key'},
        {name: 'message'}, // This is the text of the message
        {name: 'type'}     // Info, Warning, Error
    ],

    proxy: {
        type: 'sessionstorage',
        id: 'messaging'
    }
});

Ext.define('LABKEY.app.controller.Messaging', {

    extend: 'Ext.app.Controller',

    requires: [
        'LABKEY.app.model.Message'
    ],

    isService: true,

    statics: {
        TYPES: {
            INFO: 'INFO',
            WARN: 'WARN',
            ERROR: 'ERROR'
        }
    },

    _localStorageKey: 'Connector.Messages',

    init : function() {

        if (LABKEY.devMode) {
            MESSAGING = this;
        }

        this.msgStore = Ext.create('Ext.data.Store', {
            model: 'LABKEY.app.model.Message'
        });

        this.msgStore.load();

        this.callParent();
    },

    popMessages : function(key) {

        var msgs = [];

        if (!Ext.isEmpty(key)) {
            var msgSet = this.msgStore.getRange(), filter = key, requireSync = false;

            Ext.each(msgSet, function(msg) {
                if (msg.data.key === filter) {
                    msgs.push(msg);
                    this.msgStore.remove(msg);
                    requireSync = true;
                }
            }, this);

            if (requireSync) {
                this._sync();
            }
        }
        else {
            // just hand them all the messages
            msgs = this.msgStore.getRange();
            this.msgStore.removeAll();
            this._sync();
        }

        // just hand them the datas
        var ret = [];
        Ext.each(msgs, function(msg) {
            ret.push(msg.data);
        });

        return ret;
    },

    getMessageCount : function(type) {

    },

    pushMessage : function(key, message, type) {
        if (!Ext.isEmpty(key) && !Ext.isEmpty(message)) {
            this._sync([{
                key: key,
                message: message,
                type: this.getType(type)
            }]);
        }
    },

    getType : function(type) {

        var TYPE = LABKEY.app.controller.Messaging.TYPES.INFO;

        if (!Ext.isEmpty(type)) {
            var caps = type.toUpperCase();

            if (LABKEY.app.controller.Messaging.TYPES[caps]) {
                TYPE = LABKEY.app.controller.Messaging.TYPES[caps];
            }
            else {
                console.warn('Messaging Service: Unrecognized message type "' + type + '". See list of valid types. Defaults to', TYPE + '.');
            }
        }

        return TYPE;
    },

    _sync : function(records) {
        try
        {
            if (Ext.isArray(records)) {
                this.msgStore.add(records);
            }
            this.msgStore.sync();
        }
        catch (e) // QuotaExceededError
        {
            console.warn('Messaging Service: Unable to persist messages. Local storage quota exceeded.');
        }
    },

    _getCache : function() {
        var cache = {};

        if (Ext.isDefined(localStorage)) {
            var _cache = localStorage.getItem(this._localStorageKey);
            if (_cache) {
                cache = Ext.decode(_cache);
            }
        }

        return cache;
    },

    _setCache : function(cache) {
        if (Ext.isDefined(localStorage)) {
            localStorage.setItem(this._localStorageKey, Ext.encode(cache));
        }
        return cache;
    },

    isAllowed : function(key) {
        return this._getCache()[key] !== 1;
    },

    block : function(key) {
        var cache = this._getCache();
        cache[key] = 1;
        this._setCache(cache);
    },

    unblock : function(key) {
        var cache = this._getCache();
        if (cache[key]) {
            delete cache[key];
        }
        this._setCache(cache);
    }
});