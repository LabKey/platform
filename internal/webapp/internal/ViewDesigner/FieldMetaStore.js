/*
 * Copyright (c) 2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.ext4.designer.FieldProxy', {
    extend: 'Ext.data.proxy.Ajax',
    alias: 'proxy.field',
    doRequest: function(operation, callback, scope) {
        var writer = this.getWriter(),
                request = this.buildRequest(operation);

        if (operation.allowWrite()) {
            request = writer.write(request);
        }

        Ext4.apply(request, {
            binary: this.binary,
            headers: this.headers,
            timeout: this.timeout,
            callback: this.createRequestCallback(request, operation, callback, scope),
            method: this.getMethod(request),
            disableCaching: false,
            scope: this
        });

        // here is the special sauce, we include the node id as an
        // 'fk' parameter when making requests against getQueryDetails
        if (!Ext4.isEmpty(request.params.node) &&
                request.params.node !== LABKEY.internal.ViewDesigner.FieldMetaTreeStore.ROOT_ID)
        {
            request.params.fk = request.params.node;
            delete request.params.node;
        }

        Ext4.Ajax.request(request);

        return request;
    }
});

Ext4.define('LABKEY.internal.ViewDesigner.FieldMetaTreeStore', {

    extend: 'Ext.data.TreeStore',

    model: 'LABKEY.internal.ViewDesigner.FieldMetaRecord',

    statics: {
        ROOT_ID: '<ROOT>'
    },

    schemaName: undefined,

    queryName: undefined,

    constructor : function(config) {

        if (!config.schemaName || !config.queryName) {
            throw this.$className + ' requires \'schemaName\' and \'queryName\' properties to be constructed.'
        }

        var params = {
            schemaName: config.schemaName,
            queryName: config.queryName
        };

        if (config.fk) {
            params.fk = config.fk;
        }

        this.proxy = {
            type: 'field',
            url: LABKEY.ActionURL.buildURL('query', 'getQueryDetails.api', config.containerPath, params),
            reader: {
                type: 'json',
                root: 'columns',
                idProperty: function(json) {
                    return json.fieldKeyPath.toUpperCase();
                }
            }
        };

        this.root = {
            id: LABKEY.internal.ViewDesigner.FieldMetaTreeStore.ROOT_ID,
            expanded: true,
            expandable: false,
            draggable: false
        };

        this.selModel = Ext4.create('Ext.selection.CheckboxModel');

        this.callParent([config]);
    }
});

/**
 * An Ext.data.Store for LABKEY.internal.ViewDesigner.FieldMetaRecord json objects.
 */
Ext4.define('LABKEY.internal.ViewDesigner.FieldMetaStore', {

    extend: 'Ext.data.Store',

    model: 'LABKEY.internal.ViewDesigner.FieldMetaRecord',

    proxy: {
        type: 'memory',
        reader: {
            type: 'json',
            root: 'columns',
            idProperty: function (json) {
                return json.fieldKey.toUpperCase()
            }
        }
    },

    remoteSort: true,

    schemaName: undefined,

    queryName: undefined,

    statics: {
        ROOT_ID: '<ROOT>'
    },

    constructor : function (config) {

        if (!config.schemaName || !config.queryName) {
            throw this.$className + ' requires \'schemaName\' and \'queryName\' properties to be constructed.'
        }

        var params = {
            schemaName: config.schemaName,
            queryName: config.queryName
        };

        if (config.fk) {
            params.fk = config.fk;
        }

        this.url = LABKEY.ActionURL.buildURL('query', 'getQueryDetails.api', config.containerPath, params);

        this._loading = false;

        this.callParent([config]);

        this.on('beforeload', this.onBeforeLoad, this);
        this.on('load', this.onLoad, this);
        this.on('loadexception', this.onLoadException, this);
    },

    onBeforeLoad : function() {
        this._loading = true;
    },

    onLoad : function() {
        this._loading = false;
    },

    onLoadException : function(proxy, options, response, error) {
        this._loading = false;
        var loadError = { message: error };

        if (response && response.getResponseHeader && response.getResponseHeader('Content-Type').indexOf('application/json') >= 0)
        {
            var errorJson = Ext4.JSON.decode(response.responseText);
            if (errorJson && errorJson.exception) {
                loadError.message = errorJson.exception;
            }
        }

        this.loadError = loadError;
    },

    /**
     * Loads records for the given lookup fieldKey.  The fieldKey is the full path relative to the base query.
     * The special fieldKey '<ROOT>' returns the records in the base query.
     *
     * @param options
     * @param {String} [options.fieldKey] Either fieldKey or record is required.
     * @param {FieldMetaRecord} [options.record] Either fieldKey or FieldMetaRecord is required.
     * @param {Function} [options.callback] A function called when the records have been loaded.  The function accepts the following parameters:
     * <ul>
     *   <li><b>records:</b> The Array of records loaded.
     *   <li><b>options:</b> The options object passed into the this function.
     *   <li><b>success:</b> A boolean indicating success.
     * </ul>
     * @param {Object} [options.scope] The scope the callback will be called in.
     */
    loadLookup : function (options) {

        // The record's name is the fieldKey relative to the root query table.
        var fieldKey = options.fieldKey || (options.record && options.record.data.fieldKey);
        if (!fieldKey) {
            throw "fieldKey or record is required";
        }

        if (!this.lookupLoaded) {
            this.lookupLoaded = {};
        }

        var upperFieldKey = fieldKey.toUpperCase();
        if (upperFieldKey == LABKEY.internal.ViewDesigner.FieldMetaStore.ROOT_ID || this.lookupLoaded[upperFieldKey])
        {
            var r = this.queryLookup(upperFieldKey);
            if (options.callback) {
                options.callback.call(options.scope || this, r, options, true);
            }
        }
        else
        {
            var o = Ext4.applyIf({
                params: { fk: fieldKey },
                callback: options.callback.createSequence(function () { this.lookupLoaded[upperFieldKey] = true; }, this),
                add: true
            }, options);

            this.load(o);
        }
    },

    queryLookup : function (fieldKey) {
        var prefixMatch = fieldKey == LABKEY.internal.ViewDesigner.FieldMetaStore.ROOT_ID ? "" : (fieldKey + "/");
        var collection = this.queryBy(function (record, id) {
            var recordFieldKey = record.get("fieldKey");
            var idx = recordFieldKey.indexOf(prefixMatch);
            return (idx == 0 && recordFieldKey.substring(prefixMatch.length).indexOf("/") == -1);
        });
        return collection.getRange();
    }
});
