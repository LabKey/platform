
/*
 * Copyright (c) 2011-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.requiresScript('/extWidgets/Ext4Helper.js');

/**
 * Constructs a new LabKey Store using the supplied configuration.
 * @class LabKey extension to the <a href="http://docs.sencha.com/ext-js/4-0/#!/api/Ext.data.Store">Ext.data.Store</a> class,
 * which can retrieve data from a LabKey server, track changes, and update the server upon demand. This is most typically
 * used with data-bound user interface widgets, such as the LABKEY.ext.Grid.
 *
 * <p>If you use any of the LabKey APIs that extend Ext APIs, you must either make your code open source or
 * <a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=extDevelopment">purchase an Ext license</a>.</p>
 *            <p>Additional Documentation:
 *              <ul>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=javascriptTutorial">LabKey JavaScript API Tutorial</a></li>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=labkeyExt">Tips for Using Ext to Build LabKey Views</a></li>
 *              </ul>
 *           </p>
 * @constructor
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
 * @param (boolean) [config.supressErrorAlert] If true, no dialog will appear if there is an exception.  Defaults to false.
 *
 * @example &lt;script type="text/javascript"&gt;
    var _grid, _store;
    Ext.onReady(function(){

        //create a Store bound to the 'Users' list in the 'core' schema
        _store = new LABKEY.ext4.Store({
            schemaName: 'core',
            queryName: 'users'
        });

        //create a grid using that store as the data source
        _grid = new LABKEY.ext4.GridPanel({
            store: _store,
            renderTo: 'grid',
            width: 800,
            autoHeight: true,
            title: 'Example',
            editable: true
        });
    });

    //More advanced.  Override default field attributes:
    _store = new LABKEY.ext4.Store({
        schemaName: 'core',
        queryName: 'users',
        metadata: {
            UserId: {
                //this changes the field name
                caption: 'Changed',
                editorConfig: {
                    //add config that will be applied when an Ext form or grid editor is created using the metadata
                    minValue: 0,
                    maxValue: 2000
                }
            },
            //this field will be created in the store
            NewField: {
                type: 'int',
                createIfDoesNotExist: true,
                defaultValue: 100
            }
        },
        metadataDefaults: {
            width: 200,
            editorConfig: {
                allowBlank: false
            }
        }
    });

&lt;/script&gt;
&lt;div id='grid'/&gt;
 */


Ext4.define('LABKEY.ext4.Store', {
    extend: 'Ext.data.Store',
    alias: 'store.labkey-store',
    //the page size defaults to 25, which can give odd behavior for combos or other applications.
    //applications that want to use paging should modify this.
    pageSize: 10000,
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
        this.on('update', this.onUpdate, this);
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
         * @memberOf LABKEY.ext4.Store#
         * @name beforemetachange
         * @event
         * @description Fired when the initial query metadata is returned from the server. Provides an opportunity to manipulate it.
         * @param {Object} store A reference to the LABKEY store
         * @param {Object} metadata The metadata object that will be supplied to the Ext.data.Model.
         */

        /**
         * @memberOf LABKEY.ext4.Store#
         * @name exception
         * @event
         * @description Fired when there is an exception loading or saving data.
         * @param {Object} store A reference to the LABKEY store
         * @param {String} message The error message
         * @param {Object} response The response object
         * @param {Object} operation The Ext.data.Operation object
         */

        /**
         * @memberOf LABKEY.ext4.Store#
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
            type: 'LabkeyProxy',
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
        if(config)
            this.initialConfig = Ext4.apply({}, config);

        config = config || this;
        var baseParams = {};
        baseParams.schemaName = config.schemaName;
        baseParams.apiVersion = 9.1;

        if (config.parameters){
            for (var n in config.parameters)
                baseParams["query.param." + n] = config.parameters[n];
        }

        if (config.containerFilter){
            //baseParams['query.containerFilterName'] = config.containerFilter;
            baseParams['containerFilter'] = config.containerFilter;
        }

        if(config.ignoreFilter)
            baseParams['query.ignoreFilter'] = 1;

        if(Ext4.isDefined(config.maxRows)){
            baseParams['query.maxRows'] = config.maxRows;
            if(config.maxRows < this.pageSize)
                this.pageSize = config.maxRows;

            if(config.maxRows === 0)
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

        if(config.pageSize && config.maxRows !== 0 && this.maxRows !== 0)
            baseParams['limit'] = config.pageSize;

        //NOTE: sort() is a method in the store.  it's awkward to support a param, but we do it since selectRows() uses it
        if(this.initialConfig && this.initialConfig.sort)
            baseParams['query.sort'] = this.initialConfig.sort;
        delete config.sort; //important...otherwise the native sort() method is overridden

        if(config.sql){
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

        if(meta.fields && meta.fields.length){
            var fields = [];
            Ext4.each(meta.fields, function(f){
                this.translateMetadata(f);

                if(this.metadataDefaults){
                    Ext4.Object.merge(f, this.metadataDefaults);
                }

                if(this.metadata){
                    //allow more complex metadata, per field
                    if(this.metadata[f.name]){
                        Ext4.Object.merge(f, this.metadata[f.name]);
                    }
                }

                fields.push(f.name);
            }, this);

            if (meta.title)
                this.queryTitle = meta.title;

            //allow mechanism to add new fields via metadata
            if(this.metadata){
                var field;
                for (var i in this.metadata){
                    field = this.metadata[i];
                    //TODO: we should investigate how convert() works and probably use this instead
                    if(field.createIfDoesNotExist && Ext4.Array.indexOf(i)==-1){
                        field.name = field.name || i;
                        field.notFromServer = true;
                        this.translateMetadata(field);
                        if(this.metadataDefaults)
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
        LABKEY.ext.Ext4Helper.translateMetadata(field);
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

        if(!this.updatable){
            alert('This store is not updatable');
            return;
        }

        if(!this.syncNeeded()){
            this.fireEvent('synccomplete', this);
            return;
        }

        this.proxy.on('exception', this.onProxyException, this, {single: true});
        return this.callParent(arguments);
    },

    //private
    update: function(){
        this.generateBaseParams();

        if(!this.updatable){
            alert('This store is not updatable');
            return;
        }
        return this.callParent(arguments);
    },

    //private
    create: function(){
        this.generateBaseParams();

        if(!this.updatable){
            alert('This store is not updatable');
            return;
        }
        return this.callParent(arguments);
    },

    //private
    destroy: function(){
        this.generateBaseParams();

        if(!this.updatable){
            alert('This store is not updatable');
            return;
        }
        return this.callParent(arguments);
    },

    /**
     * Returns the case-normalized fieldName.  The fact that field names are not normally case-sensitive, but javascript is case-sensitive can cause prolems.  This method is designed to allow you to convert a string into the casing used by the store.
     * @param {String} fieldName The name of the field to test
     * @returns {String} The normalized field name or null if not found
     */
    getCanonicalFieldName: function(fieldName){
        var fields = this.getFields();
        if(fields.get(fieldName)){
            return fieldName;
        }

        var name;

        var properties = ['name', 'fieldKeyPath'];
        Ext4.each(properties, function(prop){
            fields.each(function(field){
                if(field[prop].toLowerCase() == fieldName.toLowerCase()){
                    name = field.name;
                    return false;
                }
            });

            if(name)
                return false;  //abort the loop
        }, this);

        return name;
    },

    //private
    //NOTE: the intent of this is to allow fields to have an initial value defined through a function.  see getInitialValue in LABKEY.ext.Ext4Helper.getDefaultEditorConfig
    onAdd: function(store, records, idx, opts){
        var val, record;
        this.getFields().each(function(meta){
            if(meta.getInitialValue){
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
        if(this.sql){
            operation.sql = this.sql;
        }
        this.proxy.containerPath = this.containerPath;
        this.proxy.extraParams = this.generateBaseParams();
    },

    //private
    //NOTE: maybe this should be a plugin to combos??
    onLoad : function(store, records, success) {
        if(!success)
            return;
        //the intent is to let the client set default values for created fields
        var toUpdate = [];
        this.getFields().each(function(f){
            if(f.setValueOnLoad && (f.getInitialValue || f.defaultValue))
                toUpdate.push(f);
        }, this);
        if(toUpdate.length){
            var allRecords = this.getRange();
            for (var i=0;i<allRecords.length;i++){
                var rec = allRecords[i];
                for (var j=0;j<toUpdate.length;j++){
                    var meta = toUpdate[j];
                    if(meta.getInitialValue)
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
        for(var idx = 0; idx < rows.length; ++idx)
        {
            row = rows[idx];

            if(!row || !row.values)
                return;

            //find the record using the id sent to the server
            record = this.getById(row.oldKeys[idCol]);

            //records created client-side might not have a PK yet, so we try to use internalId to find it
            //we defer to snapshot, since this will contain all records, even if the store is filtered
            record = (this.snapshot || this.data).get(row.oldKeys['_internalId']);

            if(!record)
                return;

            //apply values from the result row to the sent record
            for(var col in record.data)
            {
                //since the sent record might contain columns form a related table,
                //ensure that a value was actually returned for that column before trying to set it
                if(undefined !== row.values[col]){
                    var x = record.fields.get(col);
                    record.set(col, record.fields.get(col).convert(row.values[col], row.values));
                }

                //clear any displayValue there might be in the extended info
                if(record.json && record.json[col])
                    delete record.json[col].displayValue;
            }

            //if the id changed, fixup the keys and map of the store's base collection
            //HACK: this is using private data members of the base Store class. Unfortunately
            //Ext Store does not have a public API for updating the key value of a record
            //after it has been added to the store. This might break in future versions of Ext
            if(record.internalId != row.values[idCol])
            {
                record.setId(row.values[idCol]);
                record.internalId = row.values[idCol];
                index = this.data.indexOf(record);
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
        if(!json || !json.result)
            return;

        for(var commandIdx = 0; commandIdx < json.result.length; ++commandIdx)
        {
            this.processResponse(json.result[commandIdx].rows);
        }
    },

    //private
    onProxyException : function(proxy, response, operation, eOpts) {
        var loadError = {message: response.statusText};
        var json = this.getJson(response);

        if(json){
            if(json && json.exception)
                loadError.message = json.exception;

            response.errors = json;

            this.processErrors(json);
        }

        this.loadError = loadError;

        //TODO: is this the right behavior?
        if(response && (response.status === 200 || response.status == 0)){
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

        if(message)
            messageBody += ' due to the following error:' + "<br>" + message;
        else
            messageBody += ' due to an unexpected error';

        if(false !== this.fireEvent("exception", this, messageBody, response, operation)){

            if(!this.supressErrorAlert)
                Ext4.Msg.alert("Error", messageBody);

            console.log(response);
        }
    },

    processErrors: function(json){
        Ext4.each(json.errors, function(error){
            //the error object for 1 row.  1-based row numbering
            if(Ext4.isDefined(error.rowNumber)){
                var record = this.getAt(error.rowNumber - 1);
                if (!record)
                    return;

                record.serverErrors = {};

                Ext4.each(error.errors, function(e){
                    if(!record.serverErrors[e.field])
                        record.serverErrors[e.field] = [];

                    if(record.serverErrors[e.field].indexOf(e.message) == -1)
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
    onUpdate : function(store, record, operation) {
        for(var field  in record.getChanges()){
            if(record.raw && record.raw[field]){
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
     * Using the store's metadata, this method returns an Ext config object suitable for creating an Ext field.
     * The resulting object is configured to be used in a form, as opposed to a grid.
     * This is a convenience wrapper around LABKEY.ext.Ext4Helper.getFormEditorConfig
     * <p>
     * For information on using metadata, see LABKEY.ext.Ext4Helper
     *
     * @name getFormEditorConfig
     * @function
     * @param (string) fieldName The name of the field
     * @param (object) config Optional. This object will be recursively applied to the default config object
     * @returns {object} An Ext config object suitable to create a field component
     * @memberOf LABKEY.ext4.Store#
     *
     */
    getFormEditorConfig: function(fieldName, config){
        var meta = this.findFieldMetadata(fieldName);
        return LABKEY.ext.Ext4Helper.getFormEditorConfig(meta, config);
    },

    /**
     * Using the store's metadata, this method returns an Ext config object suitable for creating an Ext field.
     * The resulting object is configured to be used in a grid, as opposed to a form.
     * This is a convenience wrapper around LABKEY.ext.Ext4Helper.getGridEditorConfig
     * <p>
     * For information on using metadata, see LABKEY.ext.Ext4Helper
     *
     * @name getGridEditorConfig
     * @function
     * @param (string) fieldName The name of the field
     * @param (object) config Optional. This object will be recursively applied to the default config object
     * @returns {object} An Ext config object suitable to create a field component
     * @memberOf LABKEY.ext4.Storee#
     *
     */
    getGridEditorConfig: function(fieldName, config){
        var meta = this.findFieldMetadata(fieldName);
        return LABKEY.ext.Ext4Helper.getGridEditorConfig(meta, config);
    },

    /**
     * Returns an Ext.util.MixedCollection containing the fields associated with this store
     *
     * @name getFields
     * @function
     * @returns {Ext.util.MixedCollection} The fields associated with this store
     * @memberOf LABKEY.ext4.Store#
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
     * @returns {array} The columns associated with this store
     * @memberOf LABKEY.ext4.Store#
     *
     */
    getColumns: function(){
        return this.proxy.reader.rawData.columnModel;
    },

    /**
     * Returns a field metadata object fo the specified field
     *
     * @name findFieldMetadata
     * @function
     * @param (string) fieldName The name of the field
     * @returns {object} Metatdata for this field
     * @memberOf LABKEY.ext4.Store#
     *
     */
    findFieldMetadata : function(fieldName){
        var fields = this.getFields();
        if(!fields)
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
            var params = {
                schemaName: this.schemaName,
                "query.queryName": this.queryName,
                "query.containerFilterName": this.containerFilter
            };

            if (this.columns)
                params['query.columns'] = Ext4.isArray(this.columns) ? this.columns.join(",") : this.columns;

            // These are filters that are custom created (aka not from a defined view).
            LABKEY.Filter.appendFilterParams(params, this.filterArray);

            if (this.sortInfo)
                params['query.sort'] = "DESC" == this.sortInfo.direction
                        ? "-" + this.sortInfo.field
                        : this.sortInfo.field;

            var action = ("tsv" == format) ? "exportRowsTsv" : "exportRowsExcel";
            window.location = LABKEY.ActionURL.buildURL("query", action, this.containerPath, params);
        }
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


Ext4.define('LABKEY.ext4.ExtendedJsonReader', {
    extend: 'Ext.data.reader.Json',
    alias: 'reader.ExtendedJsonReader',
    config: {
        userFilters: null,
        useSimpleAccessors: true
    },
    mixins: {
        observable: 'Ext.util.Observable'
    },
    constructor: function(){
        this.callParent(arguments);
        this.addEvents('dataload');
    },
    readRecords: function(data) {
        if(data.metaData){
            // NOTE: normalize which field holds the PK.  this is a little unfortunate b/c ext will automatically create this field if it doesnt exist,
            // such as a query w/o a PK.  therefore we fall back to a standard name, which we can ignore when drawing grids
            this.idProperty = data.metaData.id || this.idProperty || '_internalId';
            this.totalProperty = data.metaData.totalProperty; //NOTE: normalize which field holds total rows.
            if (this.model){
                this.model.prototype.idProperty = this.idProperty;
                this.model.prototype.totalProperty = this.totalProperty;
            }

            //NOTE: it would be interesting to convert this JSON into a more functional object here
            //for example, columns w/ lookups could actually reference their target
            //we could add methods like getDisplayString(), which accept the ext record and return the appropriate display string
            Ext4.each(data.metaData.fields, function(meta){
                if(meta.jsonType == 'int' || meta.jsonType=='float' || meta.jsonType=='boolean')
                    meta.useNull = true;  //prevents Ext from assigning 0's to field when record created

                //convert string into function
                if(meta.extFormatFn){
                    try {
                        meta.extFormatFn = eval(meta.extFormatFn);
                    }
                    catch (ex)
                    {
                        //this is potentially the sort of thing we'd want to log to mothership??
                    }
                }

                if (meta.jsonType)
                    meta.extType = LABKEY.ext.Ext4Helper.EXT_TYPE_MAP[meta.jsonType];
            });
        }

        return this.callParent([data]);
    },

    //added event to allow store to modify metadata before it is applied
    onMetaChange : function(meta) {
        this.fireEvent('datachange', meta);

        this.callParent(arguments);
    },

    /*
    because our 9.1 API format returns results as objects, we transform them here.  In addition to extracting the values, Ext creates an accessor for the record's ID
    this must also be modified to support the 9.1 API.  Because I believe getId() can be called both on initial load (prior to
    when we transform the data) and after, I modified the method to test whether the field's value is an object instead of
    looking for '.value' exclusively.
    */
    createFieldAccessExpression: (function() {
        var re = /[\[\.]/;

        return function(field, fieldVarName, dataName) {
            var me     = this,
                hasMap = (field.mapping !== null),
                map    = hasMap ? field.mapping : field.name,
                result,
                operatorSearch;

            if (typeof map === 'function') {
                result = fieldVarName + '.mapping(' + dataName + ', this)';
            } else if (this.useSimpleAccessors === true || ((operatorSearch = String(map).search(re)) < 0)) {
                if (!hasMap || isNaN(map)) {
                    // If we don't provide a mapping, we may have a field name that is numeric
                    map = '"' + map + '"';
                }
                //TODO: account for field.notFromServer here...
                //also: we should investigate how convert() works and probably use this instead
                result = dataName + "[" + map + "] !== undefined ? " + dataName + "[" + map + "].value : ''";
            } else {
                result = dataName + (operatorSearch > 0 ? '.' : '') + map;
            }
            return result;
        };
    }()),

    //see note for createFieldAccessExpression()
    buildExtractors: function(force) {
        this.callParent(arguments);

        var me = this,
            idProp      = me.getIdProperty(),
            accessor,
            idField,
            map;

        if (idProp) {
            idField = me.model.prototype.fields.get(idProp);
            if (idField) {
                map = idField.mapping;
                idProp = (map !== undefined && map !== null) ? map : idProp;
            }
            accessor = me.createAccessor('["' + idProp + '"].value');

            me.getId = function(record) {
                var id = accessor.call(me, record);
                return (id === undefined || id === '') ? null : id;
            };
        }
    }
});

/**
 * The primary reason to create a custom proxy is to support batching CRUD requests into a single request to saveRows().  Otherwise Ext
 * would perform each action as a separate request.
 */
Ext4.define('LABKEY.ext4.AjaxProxy', {
    extend: 'Ext.data.proxy.Ajax',
    alias: 'proxy.LabkeyProxy',
    constructor: function(config){
        config = config || {};

        Ext4.apply(config, {
            api: {
                create: "saveRows.view",
                read: "selectRows.api",
                update: "saveRows.view",
                destroy: "saveRows.view",
                //NOTE: added in order to batch create/update/destroy into 1 request
                saveRows: "saveRows.view"
            },
            actionMethods: {
                create: "POST",
                read: "POST",
                update: "POST",
                destroy: "POST",
                saveRows: "POST"
            }
        });
        this.addEvents('exception');
        this.callParent(arguments);
    },

    saveRows: function(operation, callback, scope){
        var request = operation.request;
        Ext4.apply(request, {
            timeout       : this.timeout,
            scope         : this,
            callback      : this.createRequestCallback(request, operation, callback, scope),
            method        : this.getMethod(request),
            disableCaching: false // explicitly set it to false, ServerProxy handles caching
        });

        Ext4.Ajax.request(request);

        return request;
    },
    reader: 'ExtendedJsonReader',
    writer: {
        type: 'json',
        write: function(request){
            return request;
        }
    },
    headers: {
        'Content-Type' : 'application/json'
    },

    /**
     * @Override Ext.data.proxy.Proxy (4.1.0)
     * Overriden so we can batch insert/update/deletes into a single request using saveRows, rather than submitting 3 sequential ones
     */
    batch: function(options, listeners) {
        var me = this,
            useBatch = me.batchActions,
            batch,
            records,
            actions, aLen, action, a, r, rLen, record;

        var commands = []; //this array is not from Ext

        if (options.operations === undefined) {
            // the old-style (operations, listeners) signature was called
            // so convert to the single options argument syntax
            options = {
                operations: options,
                listeners: listeners
            };
        }

        if (options.batch) {
            if (Ext4.isDefined(options.batch.runOperation)) {
                batch = Ext4.applyIf(options.batch, {
                    proxy: me,
                    listeners: {}
                });
            }
        } else {
            options.batch = {
                proxy: me,
                listeners: options.listeners || {}
            };
        }

        if (!batch) {
            batch = new Ext4.data.Batch(options.batch);
        }

        batch.on('complete', Ext4.bind(me.onBatchComplete, me, [options], 0));

        actions = me.batchOrder.split(',');
        aLen    = actions.length;

        for (a = 0; a < aLen; a++) {
            action  = actions[a];
            records = options.operations[action];
            if (records) {
                //the body of this is changed compared to Ext4.1
                this.processRecord(action, batch, records, commands);
            }
        }

        //this is added compared to Ext 4.1
        if(commands.length){
            var request = Ext4.create('Ext.data.Request', {
                action: 'saveRows',
                url: LABKEY.ActionURL.buildURL("query", 'saveRows', this.extraParams.containerPath),
                jsonData: Ext4.apply(this.extraParams, {
                    commands: commands
                })
            });

            var b = Ext4.create('Ext.data.Operation', {
                action: 'saveRows',
                request: request
            });
            batch.add(b);
        }

        batch.start();
        return batch;
    },

    /**
     * Not an Ext method.
     */
    processRecord: function(action, batch, records, commands){
        var operation = Ext4.create('Ext.data.Operation', {
            action: action,
            records: records
        });

        if(action == 'read'){
            batch.add(operation);
        }
        else {
            commands.push(this.buildCommand(operation));
        }
    },

    /**
     * @Override Ext.data.proxy.Server (4.1.0)
     */
    buildRequest: function(operation) {
        if(this.extraParams.sql){
            this.api.read = "executeSql.api";
        }
        else {
            this.api.read = "selectRows.api";
        }

        var request = this.callParent(arguments);
        request.jsonData = request.jsonData || {};
        Ext4.apply(request.jsonData, request.params);
        if (request.method == 'POST' || request.url.indexOf('selectRows') > -1 || request.url.indexOf('saveRows') > -1) {
            delete request.params;  //would be applied to the URL
        }

        //morph request into the commands expected by saverows:
        request.jsonData.commands = request.jsonData.commands || [];

        var command = this.buildCommand(operation);
        if(command && command.rows.length){
            request.jsonData.commands.push(command);
        }

        return request;
    },

    //does not override an Ext method - used internally
    buildCommand: function(operation){
        if(operation.action!='read'){
            var command = {
                schemaName: this.extraParams.schemaName,
                queryName: this.extraParams['query.queryName'],
                rows: [],
                extraContext: {
                    storeId: this.storeId,
                    queryName: this.extraParams['query.queryName'],
                    schemaName: this.extraParams.schemaName,
                    keyField: this.reader.getIdProperty()
                }
            };

            if(operation.action=='create')
                command.command = "insertWithKeys";
            else if (operation.action=='update')
                command.command = "updateChangingKeys";
            else if (operation.action=='destroy')
                command.command = "delete";

            for (var i=0;i<operation.records.length;i++){
                var record = operation.records[i];
                var oldKeys = {};

                //NOTE: if the PK of this table is editable (like a string), then we need to submit
                //this record using the unmodified value as the PK
                var id = record.getId();
                if (record.modified && !Ext4.isEmpty(record.modified[this.reader.getIdProperty()])){
                    id = record.modified[this.reader.getIdProperty()];
                }

                oldKeys[this.reader.getIdProperty()] = id;
                oldKeys['_internalId'] = record.internalId;  //NOTE: also include internalId for records that do not have a server-assigned PK yet

                if(command.command == 'delete'){
                    command.rows.push(this.getRowData(record));
                }
                else {
                    command.rows.push({
                        values: this.getRowData(record),
                        oldKeys : oldKeys
                    });
                }
            }

            return command;
        }
    },

    /**
     * @Override Ext.data.proxy.Server (4.1.0)
     */
    getUrl: function(request) {
        var url = this.callParent(arguments);
        return LABKEY.ActionURL.buildURL("query", url, request.params.containerPath);
    },

    //does not override an Ext method - used internally
    getRowData : function(record) {
        //convert empty strings to null before posting
        var data = {};
        Ext4.apply(data, record.data);
        for(var field in data)
        {
            if(Ext4.isEmpty(data[field]))
                data[field] = null;
        }
        return data;
    },

    /**
     * @Override Ext.data.proxy.Server (4.1.0)
     */
    getParams: function(operation){
        var params = this.callParent(arguments);

        //this is a little funny.  Ext expects to encode filters into a filter 'filter' param.
        //if present, we split it apart here.
        if(params.filter && params.filter.length){
            var val;
            Ext4.each(params.filter, function(f){
                val = f.split('=');
                params[val[0]] = val[1];
            }, this);
            delete params.filter;
        }
        return params;
    },

    /**
     * @Override Ext.data.proxy.Server (4.1.0)
     */
    sortParam: 'query.sort',

    /**
     * @Override Ext.data.proxy.Server (4.1.0)
     */
    encodeSorters: function(sorters){
         var length   = sorters.length,
             sortStrs = [],
             sorter, i;

         for (i = 0; i < length; i++) {
             sorter = sorters[i];

             sortStrs[i] = (sorter.direction=='DESC' ? '-' : '') + sorter.property
         }

         return sortStrs.join(",");
    },

    /**
     * @Override Ext.data.proxy.Server (4.1.0)
     * See note in body of getParams()
     */
    encodeFilters: function(filters){
        var result = [];
        if(filters && filters.length){
            Ext4.each(filters, function(filter){
                if(filter.filterType)
                    result.push(Ext4.htmlEncode('query.' + filter.property + '~' + filter.filterType.getURLSuffix()) + '=' + Ext4.htmlEncode(filter.value));
            }, this);
        }
        return result;
    }
});
