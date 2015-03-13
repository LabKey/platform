
/**
 * An Ext.data.Store for LABKEY.ext.FieldMetaRecord json objects.
 */
LABKEY.ext.FieldMetaStore = Ext.extend(Ext.data.Store, {

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
        this.remoteSort = true;
        this.reader = new Ext.data.JsonReader({
            idProperty: function (json) { return json.fieldKeyPath.toUpperCase(); },
            root: 'columns',
            fields: LABKEY.ext.FieldMetaRecord
        });

        LABKEY.ext.FieldMetaStore.superclass.constructor.call(this, config);
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
            var errorJson = Ext.util.JSON.decode(response.responseText);
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
            var o = Ext.applyIf({
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

/** An Ext.data.Record constructor for LABKEY.Query.FieldMetaData json objects. */
LABKEY.ext.FieldMetaRecord = Ext.data.Record.create([
    'name',
    {name: 'fieldKey', mapping: 'fieldKeyPath' },
    'description',
    'friendlyType',
    'type',
    'jsonType',
    'autoIncrement',
    'hidden',
    'keyField',
    'mvEnabled',
    'nullable',
    'readOnly',
    'userEditable',
    'versionField',
    'selectable',
    'showInInsertView',
    'showInUpdateView',
    'showInDetailsView',
    'importAliases',
    'tsvFormat',
    'format',
    'excelFormat',
    'inputType',
    'caption',
    'lookup',
    'crosstabColumnDimension',
    'crosstabColumnMember'
]);

LABKEY.ext.FieldMetaRecord.getToolTipHtml = function (fieldMetaRecord, fieldKey) {
    var body = "<table>";
    if (fieldMetaRecord)
    {
        var field = fieldMetaRecord.data;
        if (field.description) {
            body += "<tr><td valign='top'><strong>Description:</strong></td><td>" + Ext.util.Format.htmlEncode(field.description) + "</td></tr>";
        }
        body += "<tr><td valign='top'><strong>Field&nbsp;key:</strong></td><td>" + Ext.util.Format.htmlEncode(LABKEY.FieldKey.fromString(field.fieldKey).toDisplayString()) + "</td></tr>";
        if (field.friendlyType) {
            body += "<tr><td valign='top'><strong>Data&nbsp;type:</strong></td><td>" + field.friendlyType + "</td></tr>";
        }
        if (field.hidden) {
            body += "<tr><td valign='top'><strong>Hidden:</strong></td><td>" + field.hidden + "</td></tr>";
        }
    }
    else {
        body += "<tr><td><strong>Field not found:</strong></td></tr><tr><td>" + Ext.util.Format.htmlEncode(fieldKey) + "</td></tr>";
    }
    body += "</table>";
    return body;
};
