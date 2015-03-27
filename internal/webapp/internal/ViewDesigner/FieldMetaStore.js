/*
 * Copyright (c) 2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

/**
 * An Ext.data.Store for LABKEY.internal.ViewDesigner.FieldMetaRecord json objects.
 */
Ext4.define('LABKEY.internal.ViewDesigner.FieldMetaStore', {

    extend: 'Ext.data.Store',

    model: 'LABKEY.internal.ViewDesigner.FieldMetaRecord',

    constructor : function (config) {

        if (config.schemaName && config.queryName)
        {
            var params = {schemaName: config.schemaName, queryName: config.queryName};
            if (config.fk) {
                params.fk = config.fk;
            }
            this.url = LABKEY.ActionURL.buildURL("query", "getQueryDetails", config.containerPath, params);
        }

        this.isLoading = false;

        config.remoteSort = true;
        config.proxy = {
            type: 'memory',
            reader: {
                type: 'json',
                root: 'columns',
                idProperty: function (json) {
                    return json.fieldKey.toUpperCase()
                }
            }
        };

        this.callParent([config]);

        this.on('beforeload', this.onBeforeLoad, this);
        this.on('load', this.onLoad, this);
        this.on('loadexception', this.onLoadException, this);
    },

    onBeforeLoad : function() {
        this.isLoading = true;
    },

    onLoad : function(store, records, options) {
        this.isLoading = false;
    },

    onLoadException : function(proxy, options, response, error)
    {
        this.isLoading = false;
        var loadError = {message: error};

        if(response && response.getResponseHeader && response.getResponseHeader("Content-Type").indexOf("application/json") >= 0)
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
    loadLookup : function (options)
    {

        // The record's name is the fieldKey relative to the root query table.
        var fieldKey = options.fieldKey || (options.record && options.record.data.fieldKey);
        if (!fieldKey) {
            throw new Error("fieldKey or record is required");
        }

        if (!this.lookupLoaded) {
            this.lookupLoaded = {};
        }

        var upperFieldKey = fieldKey.toUpperCase();
        if (upperFieldKey == "<ROOT>" || this.lookupLoaded[upperFieldKey])
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

    queryLookup : function (fieldKey)
    {
        var prefixMatch = fieldKey == "<ROOT>" ? "" : (fieldKey + "/");
        var collection = this.queryBy(function (record, id) {
            var recordFieldKey = record.get("fieldKey");
            var idx = recordFieldKey.indexOf(prefixMatch);
            return (idx == 0 && recordFieldKey.substring(prefixMatch.length).indexOf("/") == -1);
        });
        return collection.getRange();
    }
});
