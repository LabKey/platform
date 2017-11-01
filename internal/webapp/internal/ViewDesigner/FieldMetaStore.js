/*
 * Copyright (c) 2015-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.internal.ViewDesigner.FieldMetaTreeStore', {

    extend: 'Ext.data.TreeStore',

    model: 'LABKEY.internal.ViewDesigner.FieldMetaRecord',

    statics: {
        ROOT_ID: '<ROOT>'
    },

    schemaName: undefined,

    queryName: undefined,

    viewName: undefined,

    autoLoad: false,

    constructor : function(config) {

        if (!config.schemaName || !config.queryName) {
            throw this.$className + ' requires \'schemaName\' and \'queryName\' properties to be constructed.'
        }

        this.proxy = {
            type: 'querydetails',
            schema: config.schemaName,
            query: config.queryName,
            view: config.viewName,
            containerPath: config.containerPath,
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

    remoteSort: true,

    schemaName: undefined,

    queryName: undefined,

    viewName: undefined,

    statics: {
        ROOT_ID: '<ROOT>'
    },

    constructor : function (config) {

        if (!config.schemaName || !config.queryName) {
            throw this.$className + ' requires \'schemaName\' and \'queryName\' properties to be constructed.'
        }

        config.proxy = {
            type: 'querydetails',
            schema: config.schemaName,
            query: config.queryName,
            view: config.viewName,
            containerPath: config.containerPath,
            reader: {
                type: 'json',
                root: 'columns',
                idProperty: function(json) {
                    return json.fieldKeyPath.toUpperCase();
                }
            }
        };

        config._loading = false;

        this.callParent([config]);

        this.on('beforeload', this.onBeforeLoad, this);
        this.on('load', this.onLoad, this);
        this.on('loadexception', this.onLoadException, this);
    },

    getById : function(id) {
        var _id;
        if (Ext4.isString(id)) {
            _id = id.toUpperCase();
        }
        else {
            _id = id;
        }

        return this.callParent([_id]);
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

        if (response && response.getResponseHeader && response.getResponseHeader('Content-Type').indexOf('application/json') >= 0) {
            var errorJson = Ext4.decode(response.responseText);
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
        if (upperFieldKey == LABKEY.internal.ViewDesigner.FieldMetaStore.ROOT_ID || this.lookupLoaded[upperFieldKey]) {
            var r = this.queryLookup(upperFieldKey);
            if (options.callback) {
                options.callback.call(options.scope || this, r, options, true);
            }
        }
        else {
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
