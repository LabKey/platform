Ext.define('LABKEY.app.model.Message', {

    extend: 'Ext.data.Model',

    fields: [
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

    popMessages : function(type) {

        var msgs = [];

        if (!Ext.isEmpty(type)) {
            var msgSet = this.msgStore.getRange(), filter = this.getType(type), requireSync = false;

            Ext.each(msgSet, function(msg) {
                if (msg.data.type === filter) {
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

    pushMessage : function(message, type) {
        if (!Ext.isEmpty(message)) {
            this._sync([{
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
    }
});