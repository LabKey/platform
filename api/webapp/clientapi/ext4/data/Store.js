/*
 * Copyright (c) 2012-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

/**
 * Constructs an extended ExtJS 4.2.1 Ext.data.Store configured for use in LabKey client-side applications.
 * @name LABKEY.ext4.data.Store
 * @class
 * LabKey extension to the <a href="http://docs.sencha.com/ext-js/4-0/#!/api/Ext.data.Store">Ext.data.Store</a> class,
 * which can retrieve data from a LabKey server, track changes, and update the server upon demand. This is most typically
 * used with data-bound user interface widgets, such as the Ext.grid.Panel.
 *
 * <p>If you use any of the LabKey APIs that extend Ext APIs, you must either make your code open source or
 * <a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=extDevelopment">purchase an Ext license</a>.</p>
 *            <p>Additional Documentation:
 *              <ul>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=javascriptTutorial">Tutorial: Create Applications with the JavaScript API</a></li>
 *              </ul>
 *           </p>
 * @augments Ext.data.Store
 * @param config Configuration properties.
 * @param {String} config.schemaName The LabKey schema to query.
 * @param {String} config.queryName The query name within the schema to fetch.
 * @param {String} [config.sql] A LabKey SQL statement to execute to fetch the data. You may specify either a queryName or sql,
 * but not both. Note that when using sql, the store becomes read-only, as it has no way to know how to update/insert/delete the rows.
 * @param {String} [config.viewName] A saved custom view of the specified query to use if desired.
 * @param {String} [config.columns] A comma-delimited list of column names to fetch from the specified query. Note
 *  that the names may refer to columns in related tables using the form 'column/column/column' (e.g., 'RelatedPeptide/TrimmedPeptide').
 * @param {String} [config.sort] A base sort specification in the form of '[-]column,[-]column' ('-' is used for descending sort).
 * @param {Array} [config.filterArray] An array of LABKEY.Filter.FilterDefinition objects to use as the base filters.
 * @param {Boolean} [config.updatable] Defaults to true. Set to false to prohibit updates to this store.
 * @param {String} [config.containerPath] The container path from which to get the data. If not specified, the current container is used.
 * @param {Integer} [config.maxRows] The maximum number of rows returned by this query (defaults to showing all rows).
 * @param {Boolean} [config.ignoreFilter] True will ignore any filters applied as part of the view (defaults to false).
 * @param {String} [config.containerFilter] The container filter to use for this query (defaults to null).
 *      Supported values include:
 *       <ul>
 *           <li>"Current": Include the current folder only</li>
 *           <li>"CurrentAndSubfolders": Include the current folder and all subfolders</li>
 *           <li>"CurrentPlusProject": Include the current folder and the project that contains it</li>
 *           <li>"CurrentAndParents": Include the current folder and its parent folders</li>
 *           <li>"CurrentPlusProjectAndShared": Include the current folder plus its project plus any shared folders</li>
 *           <li>"AllFolders": Include all folders for which the user has read permission</li>
 *       </ul>
 * @param {Object} [config.metadata] A metadata object that will be applied to the default metadata returned by the server.  See example below for usage.
 * @param {Object} [config.metadataDefaults] A metadata object that will be applied to every field of the default metadata returned by the server.  Will be superceeded by the metadata object in case of conflicts. See example below for usage.
 * @param {boolean} [config.supressErrorAlert] If true, no dialog will appear if there is an exception.  Defaults to false.
 *
 * @example &lt;script type="text/javascript"&gt;
    var _store;

    Ext4.onReady(function(){

        // create a Store bound to the 'Users' list in the 'core' schema
        _store = Ext4.create('LABKEY.ext4.data.Store', {
            schemaName: 'core',
            queryName: 'users',
            autoLoad: true
        });
    });

&lt;/script&gt;
&lt;div id='grid'/&gt;
 */

Ext4.define('LABKEY.ext4.data.Store', {

    extend: 'Ext.data.Store',
    alternateClassName: 'LABKEY.ext4.Store',

    alias: ['store.labkeystore', 'store.labkey-store'],

    //the page size defaults to 25, which can give odd behavior for combos or other applications.
    //applications that want to use paging should modify this.  100K matches the implicit client API pagesize
    pageSize: 100000,

    constructor: function(config) {
        config = config || {};

        config.updatable = Ext4.isDefined(config.updatable) ? config.updatable : true;

        var baseParams = this.generateBaseParams(config);

        Ext4.apply(this, config);

        //specify an empty fields array instead of a model.  the reader will creates a model later
        this.fields = [];

        this.proxy = this.getProxyConfig();

        //see note below
        var autoLoad = config.autoLoad;
        config.autoLoad = false;
        this.autoLoad = false;
        this.loading = autoLoad; //allows combos to properly set initial value w/ asyc store load

        // call the superclass's constructor
        this.callParent([config]);

        //NOTE: if the config object contains a load lister it will be executed prior to this one...not sure if that's a problem or not
        this.on('beforeload', this.onBeforeLoad, this);
        this.on('load', this.onLoad, this);
        this.on('update', this.onStoreUpdate, this);
        this.on('add', this.onAdd, this);

        this.proxy.reader.on('datachange', this.onReaderLoad, this);

        //Add this here instead of allowing Ext.store to autoLoad to make sure above listeners are added before 1st load
        if(autoLoad){
            this.autoLoad = autoLoad;
            Ext4.defer(this.load, 10, this, [
                typeof this.autoLoad == 'object' ? this.autoLoad : undefined
            ]);
        }

        /**
         * @memberOf LABKEY.ext4.data.Store#
         * @name beforemetachange
         * @event
         * @description Fired when the initial query metadata is returned from the server. Provides an opportunity to manipulate it.
         * @param {Object} store A reference to the LABKEY store
         * @param {Object} metadata The metadata object that will be supplied to the Ext.data.Model.
         */

        /**
         * @memberOf LABKEY.ext4.data.Store#
         * @name exception
         * @event
         * @description Fired when there is an exception loading or saving data.
         * @param {Object} store A reference to the LABKEY store
         * @param {String} message The error message
         * @param {Object} response The response object
         * @param {Object} operation The Ext.data.Operation object
         */

        /**
         * @memberOf LABKEY.ext4.data.Store#
         * @name synccomplete
         * @event
         * @description Fired when a sync operation is complete, which can include insert/update/delete events
         * @param {Object} store A reference to the LABKEY store
         */
        this.addEvents('beforemetachange', 'exception', 'synccomplete');
    },

    //private
    getProxyConfig: function(){
        return {
            type: 'labkeyajax',
            store: this,
            timeout: this.timeout,
            listeners: {
                scope: this,
                exception: this.onProxyException
            },
            extraParams: this.generateBaseParams()
        }
    },

    generateBaseParams: function(config){
        if (config)
            this.initialConfig = Ext4.apply({}, config);

        config = config || this;
        var baseParams = {};
        baseParams.schemaName = config.schemaName;
        baseParams.apiVersion = 9.1;
        // Issue 32269 - force key and other non-requested columns to be sent back
        baseParams.minimalColumns = false;

        if (config.parameters) {
            Ext4.iterate(config.parameters, function(param, value) {
                baseParams['query.param.' + param] = value;
            });
        }

        if (config.containerFilter){
            //baseParams['query.containerFilterName'] = config.containerFilter;
            baseParams['containerFilter'] = config.containerFilter;
        }

        if (config.ignoreFilter)
            baseParams['query.ignoreFilter'] = 1;

        if (Ext4.isDefined(config.maxRows)){
            baseParams['query.maxRows'] = config.maxRows;
            if (config.maxRows < this.pageSize)
                this.pageSize = config.maxRows;

            if (config.maxRows === 0)
                this.pageSize = 0;
        }

        if (config.viewName)
            baseParams['query.viewName'] = config.viewName;

        if (config.columns)
            baseParams['query.columns'] = Ext4.isArray(config.columns) ? config.columns.join(",") : config.columns;

        if (config.queryName)
            baseParams['query.queryName'] = config.queryName;

        if (config.containerPath)
            baseParams.containerPath = config.containerPath;

        if (config.pageSize && config.maxRows !== 0 && this.maxRows !== 0)
            baseParams['limit'] = config.pageSize;

        //NOTE: sort() is a method in the store. it's awkward to support a param, but we do it since selectRows() uses it
        if (this.initialConfig && this.initialConfig.sort)
            baseParams['query.sort'] = this.initialConfig.sort;
        delete config.sort; //important...otherwise the native sort() method is overridden

        if (config.sql){
            baseParams.sql = config.sql;
            this.updatable = false;
        }
        else {
            this.updatable = true;
        }

        LABKEY.Filter.appendFilterParams(baseParams, config.filterArray);

        return baseParams;
    },

    //private
    //NOTE: the purpose of this is to provide a way to modify the server-supplied metadata and supplement with a client-supplied object
    onReaderLoad: function(meta){
        //this.model.prototype.idProperty = this.proxy.reader.idProperty;

        if (meta.fields && meta.fields.length){
            var fields = [];
            Ext4.each(meta.fields, function(f){
                this.translateMetadata(f);

                if (this.metadataDefaults){
                    Ext4.Object.merge(f, this.metadataDefaults);
                }

                if (this.metadata){
                    //allow more complex metadata, per field
                    if (this.metadata[f.name]){
                        Ext4.Object.merge(f, this.metadata[f.name]);
                    }
                }

                fields.push(f.name);
            }, this);

            if (meta.title)
                this.queryTitle = meta.title;

            //allow mechanism to add new fields via metadata
            if (this.metadata){
                var field;
                for (var i in this.metadata){
                    field = this.metadata[i];
                    //TODO: we should investigate how convert() works and probably use this instead
                    if (field.createIfDoesNotExist && Ext4.Array.indexOf(i)==-1){
                        field.name = field.name || i;
                        field.notFromServer = true;
                        this.translateMetadata(field);
                        if (this.metadataDefaults)
                            Ext4.Object.merge(field, this.metadataDefaults);

                        meta.fields.push(Ext4.apply({}, field));
                    }
                }
            }
            this.fireEvent('beforemetachange', this, meta);
        }
    },

    //private
    translateMetadata: function(field){
        LABKEY.ext4.Util.translateMetadata(field);
    },

    //private
    setModel: function(model){
        // NOTE: if the query lacks a PK, which can happen with queries that dont represent physical tables,
        // Ext adds a column to hold an Id.  In order to differentiate this from other fields we set defaults
        this.model.prototype.fields.each(function(field){
            if (field.name == '_internalId'){
                Ext4.apply(field, {
                    hidden: true,
                    calculatedField: true,
                    shownInInsertView: false,
                    shownInUpdateView: false,
                    userEditable: false
                });
            }
        });
        this.model = model;
        this.implicitModel = false;
    },

    //private
    load: function(){
        this.generateBaseParams();
        this.proxy.on('exception', this.onProxyException, this, {single: true});
        return this.callParent(arguments);
    },

    //private
    sync: function(){
        this.generateBaseParams();

        if (!this.updatable){
            alert('This store is not updatable');
            return;
        }

        if (!this.syncNeeded()){
            this.fireEvent('synccomplete', this);
            return;
        }

        this.proxy.on('exception', this.onProxyException, this, {single: true});
        return this.callParent(arguments);
    },

    //private
    update: function(){
        this.generateBaseParams();

        if (!this.updatable){
            alert('This store is not updatable');
            return;
        }
        return this.callParent(arguments);
    },

    //private
    create: function(){
        this.generateBaseParams();

        if (!this.updatable){
            alert('This store is not updatable');
            return;
        }
        return this.callParent(arguments);
    },

    //private
    destroy: function(){
        this.generateBaseParams();

        if (!this.updatable){
            alert('This store is not updatable');
            return;
        }
        return this.callParent(arguments);
    },

    /**
     * Returns the case-normalized fieldName.  The fact that field names are not normally case-sensitive, but javascript is case-sensitive can cause prolems.  This method is designed to allow you to convert a string into the casing used by the store.
     * @name getCanonicalFieldName
     * @function
     * @param {String} fieldName The name of the field to test
     * @returns {String} The normalized field name or null if not found
     * @memberOf LABKEY.ext4.data.Store#
     */
    getCanonicalFieldName: function(fieldName){
        var fields = this.getFields();
        if (fields.get(fieldName)){
            return fieldName;
        }

        var name;

        var properties = ['name', 'fieldKeyPath'];
        Ext4.each(properties, function(prop){
            fields.each(function(field){
                if (field[prop].toLowerCase() == fieldName.toLowerCase()){
                    name = field.name;
                    return false;
                }
            });

            if (name)
                return false;  //abort the loop
        }, this);

        return name;
    },

    //private
    //NOTE: the intent of this is to allow fields to have an initial value defined through a function.  see getInitialValue in LABKEY.ext4.Util.getDefaultEditorConfig
    onAdd: function(store, records, idx, opts){
        var val, record;
        this.getFields().each(function(meta){
            if (meta.getInitialValue){
                for (var i=0;i<records.length;i++){
                    record = records[i];
                    val = meta.getInitialValue(record.get(meta.name), record, meta);
                    record.set(meta.name, val);
                }
            }
        }, this);
    },

    //private
    onBeforeLoad: function(operation){
        if (this.sql){
            operation.sql = this.sql;
        }
        this.proxy.containerPath = this.containerPath;
        this.proxy.extraParams = this.generateBaseParams();
    },

    //private
    //NOTE: maybe this should be a plugin to combos??
    onLoad : function(store, records, success) {
        if (!success)
            return;
        //the intent is to let the client set default values for created fields
        var toUpdate = [];
        this.getFields().each(function(f){
            if (f.setValueOnLoad && (f.getInitialValue || f.defaultValue))
                toUpdate.push(f);
        }, this);
        if (toUpdate.length){
            var allRecords = this.getRange();
            for (var i=0;i<allRecords.length;i++){
                var rec = allRecords[i];
                for (var j=0;j<toUpdate.length;j++){
                    var meta = toUpdate[j];
                    if (meta.getInitialValue)
                        rec.set(meta.name, meta.getInitialValue(rec.get(meta.name), rec, meta));
                    else if (meta.defaultValue && !rec.get(meta.name))
                        rec.set(meta.name, meta.defaultValue)
                }
            }
        }
    },

    onProxyWrite: function(operation) {
        var me = this,
            success = operation.wasSuccessful(),
            records = operation.getRecords();

        switch (operation.action) {
            case 'saveRows':
                me.onSaveRows(operation, success);
                break;
            default:
                console.log('something other than saveRows happened: ' + operation.action)
        }

        if (success) {
            me.fireEvent('write', me, operation);
            me.fireEvent('datachanged', me);
        }
        //this is a callback that would have been passed to the 'create', 'update' or 'destroy' function and is optional
        Ext4.callback(operation.callback, operation.scope || me, [records, operation, success]);

        //NOTE: this was created to give a single event to follow, regardless of success
        this.fireEvent('synccomplete', this, operation, success);
    },

    //private
    processResponse : function(rows){
        var idCol = this.proxy.reader.getIdProperty();
        var row;
        var record;
        var index;
        for (var idx = 0; idx < rows.length; ++idx)
        {
            row = rows[idx];

            //an example row in this situation would be the response from a delete command
            if (!row || !row.values)
                return;

            //find the record using the id sent to the server
            record = this.getById(row.oldKeys[idCol]);

            //records created client-side might not have a PK yet, so we try to use internalId to find it
            //we defer to snapshot, since this will contain all records, even if the store is filtered
            if (!record)
                record = (this.snapshot || this.data).get(row.oldKeys['_internalId']);

            if (!record)
                return;

            //apply values from the result row to the sent record
            for (var col in record.data)
            {
                //since the sent record might contain columns form a related table,
                //ensure that a value was actually returned for that column before trying to set it
                if (undefined !== row.values[col]){
                    record.set(col, record.fields.get(col).convert(row.values[col], row.values));
                }

                //clear any displayValue there might be in the extended info
                if (record.json && record.json[col])
                    delete record.json[col].displayValue;
            }

            //if the id changed, fixup the keys and map of the store's base collection
            //HACK: this is using private data members of the base Store class. Unfortunately
            //Ext Store does not have a public API for updating the key value of a record
            //after it has been added to the store. This might break in future versions of Ext
            if (record.internalId != row.values[idCol])
            {
                //ISSUE 22289: we need to find the original index before changing the internalId, or the record will not get found
                index = this.data.indexOf(record);
                record.internalId = row.values[idCol];
                record.setId(row.values[idCol]);
                if (index > -1) {
                    this.data.removeAt(index);
                    this.data.insert(index, record);
                }
            }

            //reset transitory flags and commit the record to let
            //bound controls know that it's now clean
            delete record.saveOperationInProgress;

            record.phantom = false;
            record.commit();
        }
    },

    //private
    getJson : function(response) {
        return (response && undefined != response.getResponseHeader && undefined != response.getResponseHeader('Content-Type')
                && response.getResponseHeader('Content-Type').indexOf('application/json') >= 0)
                ? Ext4.JSON.decode(response.responseText)
                : null;
    },

    //private
    onSaveRows: function(operation, success){
        var json = this.getJson(operation.response);
        if (!json || !json.result)
            return;

        for (var commandIdx = 0; commandIdx < json.result.length; ++commandIdx)
        {
            this.processResponse(json.result[commandIdx].rows);
        }
    },

    //private
    onProxyException : function(proxy, response, operation, eOpts) {
        var loadError = {message: response.statusText};
        var json = this.getJson(response);

        if (json){
            if (json && json.exception)
                loadError.message = json.exception;

            response.errors = json;

            this.processErrors(json);
        }

        this.loadError = loadError;

        //TODO: is this the right behavior?
        if (response && (response.status === 200 || response.status == 0)){
            return;
        }

        var message = (json && json.exception) ? json.exception : response.statusText;

        var messageBody;
        switch(operation.action){
            case 'read':
                messageBody = 'Could not load records';
                break;
            case 'saveRows':
                messageBody = 'Could not save records';
                break;
            default:
                messageBody = 'There was an error';
        }

        if (message)
            messageBody += ' due to the following error:' + "<br>" + message;
        else
            messageBody += ' due to an unexpected error';

        if (false !== this.fireEvent("exception", this, messageBody, response, operation)){

            if (!this.supressErrorAlert)
                Ext4.Msg.alert("Error", messageBody);

            console.log(response);
        }
    },

    processErrors: function(json){
        Ext4.each(json.errors, function(error){
            //the error object for 1 row.  1-based row numbering
            if (Ext4.isDefined(error.rowNumber)){
                var record = this.getAt(error.rowNumber - 1);
                if (!record)
                    return;

                record.serverErrors = {};

                Ext4.each(error.errors, function(e){
                    if (!record.serverErrors[e.field])
                        record.serverErrors[e.field] = [];

                    if (record.serverErrors[e.field].indexOf(e.message) == -1)
                        record.serverErrors[e.field].push(e.message);
                }, this);
            }
        }, this);
    },

    //private
    // NOTE: these values are returned by the store in the 9.1 API format
    // They provide the display value and information used in Missing value indicators
    // They are used by the Ext grid when rendering or creating a tooltip.  They are deleted here prsumably b/c if the value
    // is changed then we cannot count on them being accurate
    onStoreUpdate : function(store, record, operation) {
        for (var field  in record.getChanges()){
            if (record.raw && record.raw[field]){
                delete record.raw[field].displayValue;
                delete record.raw[field].mvValue;
            }
        }
    },

    syncNeeded: function(){
        return this.getNewRecords().length > 0 ||
            this.getUpdatedRecords().length > 0 ||
            this.getRemovedRecords().length > 0
    },

    /**
     * @private
     * Using the store's metadata, this method returns an Ext config object suitable for creating an Ext field.
     * The resulting object is configured to be used in a form, as opposed to a grid.
     * This is a convenience wrapper around LABKEY.ext4.Util.getFormEditorConfig
     * <p>
     * For information on using metadata, see LABKEY.ext4.Util
     *
     * @name getFormEditorConfig
     * @function
     * @param (string) fieldName The name of the field
     * @param (object) config Optional. This object will be recursively applied to the default config object
     * @returns {object} An Ext config object suitable to create a field component
     */
    getFormEditorConfig: function(fieldName, config){
        var meta = this.findFieldMetadata(fieldName);
        return LABKEY.ext4.Util.getFormEditorConfig(meta, config);
    },

    /**
     * @private
     * Using the store's metadata, this method returns an Ext config object suitable for creating an Ext field.
     * The resulting object is configured to be used in a grid, as opposed to a form.
     * This is a convenience wrapper around LABKEY.ext4.Util.getGridEditorConfig
     * <p>
     * For information on using metadata, see LABKEY.ext4.Util
     * @name getGridEditorConfig
     * @function
     * @param (string) fieldName The name of the field
     * @param (object) config Optional. This object will be recursively applied to the default config object
     * @returns {object} An Ext config object suitable to create a field component
     */
    getGridEditorConfig: function(fieldName, config){
        var meta = this.findFieldMetadata(fieldName);
        return LABKEY.ext4.Util.getGridEditorConfig(meta, config);
    },

    /**
     * Returns an Ext.util.MixedCollection containing the fields associated with this store
     *
     * @name getFields
     * @function
     * @returns {Ext.util.MixedCollection} The fields associated with this store
     * @memberOf LABKEY.ext4.data.Store#
     *
     */
    getFields: function(){
        return this.proxy.reader.model.prototype.fields;
    },

    /**
     * Returns an array of the raw column objects returned from the server along with the query metadata
     *
     * @name getColumns
     * @function
     * @returns {Array} The columns associated with this store
     * @memberOf LABKEY.ext4.data.Store#
     *
     */
    getColumns: function(){
        return this.proxy.reader.rawData.columnModel;
    },

    /**
     * Returns a field metadata object of the specified field
     *
     * @name findFieldMetadata
     * @function
     * @param {String} fieldName The name of the field
     * @returns {Object} Metatdata for this field
     * @memberOf LABKEY.ext4.data.Store#
     *
     */
    findFieldMetadata : function(fieldName){
        var fields = this.getFields();
        if (!fields)
            return null;

        return fields.get(fieldName);
    },

    exportData : function(format) {
        format = format || "excel";
        if (this.sql)
        {
            LABKEY.Query.exportSql({
                schemaName: this.schemaName,
                sql: this.sql,
                format: format,
                containerPath: this.containerPath,
                containerFilter: this.containerFilter
            });
        }
        else
        {
            var config = this.getExportConfig(format);
            window.location = config.url;
        }
    },

    getExportConfig : function(format) {

        format = format || "excel";

        var params = {
            schemaName: this.schemaName,
            "query.queryName": this.queryName,
            "query.containerFilterName": this.containerFilter
        };

        if (this.columns) {
            params["query.columns"] = Ext4.isArray(this.columns) ? this.columns.join(',') : this.columns;
        }

        // These are filters that are custom created (aka not from a defined view).
        LABKEY.Filter.appendFilterParams(params, this.filterArray);

        if (this.sortInfo) {
            params["query.sort"] = ("DESC" === this.sortInfo.direction ? "-" : "") + this.sortInfo.field;
        }

        var config = {
            action: ("tsv" === format) ? "exportRowsTsv" : "exportRowsExcel",
            params: params
        };

        config.url = LABKEY.ActionURL.buildURL("query", config.action, this.containerPath, config.params);

        return config;
    },

    //Ext3 compatability??
    commitChanges: function(){
        this.sync();
    },

    //private
    getKeyField: function(){
        return this.model.prototype.idProperty;
    },

    //private, experimental
    getQueryConfig: function(){
        return {
            containerPath: this.containerPath,
            schemaName: this.schemaName,
            queryName: this.queryName,
            viewName: this.viewName,
            queryTitle: this.queryTitle,
            sql: this.sql,
            columns: this.columns,
            filterArray: this.filterArray,
            sort: this.initialConfig.sort,
            maxRows: this.maxRows,
            containerFilter: this.containerFilter
        }
    }

});