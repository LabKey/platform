/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.requiresExt4Sandbox(true);
LABKEY.requiresScript('/extWidgets/MetaHelper.js');

Ext4.namespace('LABKEY.ext4');

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


LABKEY.ext4.Store = Ext4.define('LABKEY.ext4.Store', {
    extend: 'Ext.data.Store',
    alias: 'store.labkey-store',
    pageSize: 10000,
    constructor: function(config) {
        config = config || {};

        config.updatable = Ext4.isDefined(config.updatable) ? config.updatable : true;

        var baseParams = this.generateBaseParams(config);

        Ext4.apply(this, config);

        //specify an empty fields array instead of a model.  the reader will creates a model later
        this.fields = [];

        this.proxy = {
            type: 'LabkeyProxy',
            store: this,
            listeners: {
                scope: this,
                exception: this.onProxyException
            },
            extraParams: baseParams
        };

        //see note below
        var autoLoad = config.autoLoad;
        config.autoLoad = false;
        this.autoLoad = false;

        // call the superclass's constructor
        this.callParent([config]);

        //NOTE: if the config object contains a load lister it will be executed prior to this one...not sure if that's a problem or not
        this.on('beforeload', this.onBeforeLoad, this);
        this.on('load', this.onLoad, this);
        this.on('update', this.onUpdate, this);
        this.on('add', this.onAdd, this);

        this.proxy.reader.on('metadataload', this.onMetaDataLoad, this);

        //Add this here instead of allowing Ext.store to autoLoad to make sure above listeners are added before 1st load
        if(autoLoad){
            this.autoLoad = autoLoad;
            this.load.defer(10, this, [
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

        this.addEvents('beforemetachange', 'syncexception', 'synccomplete');
    },
    //private
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

        if (config.containerFilter)
            baseParams['query.containerFilterName'] = config.containerFilter;

        if(config.ignoreFilter)
            baseParams['query.ignoreFilter'] = 1;

        if(Ext4.isDefined(config.maxRows))
            baseParams['query.maxRows'] = config.maxRows;

        if (config.viewName)
            baseParams['query.viewName'] = config.viewName;

        if (config.columns)
            baseParams['query.columns'] = Ext4.isArray(config.columns) ? config.columns.join(",") : config.columns;

        if (config.queryName)
            baseParams['query.queryName'] = config.queryName;

        if (config.containerPath)
            baseParams.containerPath = config.containerPath;

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
    onMetaDataLoad: function(meta){
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

            //allow mechanism to add new fields via metadata
            if(this.metadata){
                var field;
                for (var i in this.metadata){
                    field = this.metadata[i];
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
    //NOTE: the intention of this method is to provide a standard, low-level way to translating Labkey metadata names into ext ones.
    translateMetadata: function(field){
        field.fieldLabel = Ext.util.Format.htmlEncode(field.label || field.caption || field.header || field.name);
        field.dataIndex = field.dataIndex || field.name;
        field.editable = (field.userEditable!==false && !field.readOnly && !field.autoIncrement);
        field.allowBlank = field.nullable;
        field.jsonType = field.jsonType || LABKEY.ext.MetaHelper.findJsonType(field);

    },

    //private
    setModel: function(model){
        this.model = model;
        this.implicitModel = false;
    },

    //private
    load: function(){
        this.generateBaseParams();
        return this.callParent(arguments);
    },

    //private
    sync: function(){
        this.generateBaseParams();

        if(!this.updatable){
            alert('This store is not updatable');
            return;
        }
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
     * Returns true if the store has loaded data from the serer at least once.  The purpose is primarily to idenitfy whether metadata is present.
     * It does not test whether the store is currently loading (after the initial load, metadata is present).  To identify whether a load is in progress, see the
     * Ext.data.Store method isLoading()
     *
     * @name hasLoaded
     * @function
     * @returns {boolean} Returns true if the store has loaded, false if not.
     * @memberOf LABKEY.ext.Store#
     *
     */
    hasLoaded: function(){
        //NOTE: rawData is the JSON returned by the server.  if present, this store has loaded at least once
        return this.proxy && this.proxy.reader && this.proxy.reader.rawData !== undefined;
    },

    //private
    //NOTE: the intent of this is to allow fields to have an initial value defined through a function.  see getInitialValue in LABKEY.ext.MetaHelper.getDefaultEditorConfig
    onAdd: function(store, records, idx, opts){
        var val;
        this.getFields().each(function(meta){
            if(meta.getInitialValue){
                Ext4.each(records, function(record){
                    val = meta.getInitialValue(record.get(meta.name), record, meta);
                    record.set(meta.name, val);
                }, this);
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
            this.each(function(rec){
                Ext4.each(toUpdate, function(meta){
                    if(meta.getInitialValue)
                        rec.set(meta.name, meta.getInitialValue(rec.get(meta.name), rec, meta));
                    else if (meta.defaultValue && !rec.get(meta.name))
                        rec.set(meta.name, meta.defaultValue)
                }, this);
            });
        }
        //this is primarily used for comboboxes
        //create an extra record with a blank id column
        //and the null caption in the display column
        if(this.nullRecord){
            var data = {};
            data[this.model.idProperty] = "";

            //NOTE: unlike LABKEY.ext.Store, this does not default to the string [none].
            // we should rely on Ext tpls to do this since supplying a non-null string
            // defeats the purpose of a null record when the valueColumn is the same as the displayColumn
            data[this.nullRecord.displayColumn] = this.nullRecord.nullCaption || this.nullCaption;

            var record = this.model.create(data);
            this.insert(0, record);
        }
    },

    //private
    onProxyException : function(proxy, response, operation, eOpts) {
        var loadError = {message: response.statusText};
        if(response && response.getResponseHeader
                && Ext4.Array.indexOf(response.getResponseHeader("Content-Type"), "application/json") >= 0)
        {
            var errorJson = Ext4.JSON.decode(response.responseText);
            if(errorJson && errorJson.exception)
                loadError.message = errorJson.exception;

            response.errors = errorJson;

            this.validateRecords(errorJson);
        }

        this.loadError = loadError;

        this.fireEvent('syncexception', response, operation);
    },

    validateRecords: function(errors){
        Ext4.each(errors.errors, function(error){
            //the error object for 1 row:
            if(Ext4.isDefined(error.rowNumber)){
                var record = this.getAt(error.rowNumber);
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

    /**
     * Using the store's metadata, this method returns an Ext config object suitable for creating an Ext field.
     * The resulting object is configured to be used in a form, as opposed to a grid.
     * This is a convenience wrapper around LABKEY.ext.MetaHelper.getFormEditorConfig
     * <p>
     * For information on using metadata, see LABKEY.ext.MetaHelper
     *
     * @name getFormEditorConfig
     * @function
     * @param (string) fieldName The name of the field
     * @param (object) config Optional. This object will be recursively applied to the default config object
     * @returns {object} An Ext config object suitable to create a field component
     * @memberOf LABKEY.ext.Store#
     *
     */
    getFormEditorConfig: function(fieldName, config){
        var meta = this.findFieldMetadata(fieldName);
        return LABKEY.ext.MetaHelper.getFormEditorConfig(meta, config);
    },

    /**
     * Using the store's metadata, this method returns an Ext config object suitable for creating an Ext field.
     * The resulting object is configured to be used in a grid, as opposed to a form.
     * This is a convenience wrapper around LABKEY.ext.MetaHelper.getGridEditorConfig
     * <p>
     * For information on using metadata, see LABKEY.ext.MetaHelper
     *
     * @name getGridEditorConfig
     * @function
     * @param (string) fieldName The name of the field
     * @param (object) config Optional. This object will be recursively applied to the default config object
     * @returns {object} An Ext config object suitable to create a field component
     * @memberOf LABKEY.ext.Store#
     *
     */
    getGridEditorConfig: function(fieldName, config){
        var meta = this.findFieldMetadata(fieldName);
        return LABKEY.ext.MetaHelper.getGridEditorConfig(meta, config);
    },

    /**
     * Returns an Ext.util.MixedCollection containing the fields associated with this store
     *
     * @name getFields
     * @function
     * @returns {Ext.util.MixedCollection} The fields associated with this store
     * @memberOf LABKEY.ext.Store#
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
     * @memberOf LABKEY.ext.Store#
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
     * @memberOf LABKEY.ext.Store#
     *
     */
    findFieldMetadata : function(fieldName){
        var fields = this.getFields();
        if(!fields)
            return null;

        return fields.get(fieldName);
    },

    //Ext3 compatability??
    commitChanges: function(){
        this.sync();
    }

});


Ext4.define('LABKEY.ext4.ExtendedJsonReader', {
    extend: 'Ext.data.reader.Json',
    alias: 'reader.ExtendedJsonReader',
    config: {
        userFilters: null
    },
    mixins: {
        observable: 'Ext.util.Observable'
    },
    constructor: function(){
        this.callParent(arguments);
        this.addEvents('metadataload');
    },
    readRecords: function(data) {
        if(data.metaData){
            this.idProperty = data.metaData.id; //NOTE: normalize which field holds the PK.

            Ext4.each(data.metaData.fields, function(meta){
                if(meta.jsonType == 'int' || meta.jsonType=='float' || meta.jsonType=='boolean')
                    meta.useNull = true;  //prevents Ext from assigning 0's to field when record created
            });

            this.fireEvent('metadataload', data.metaData); //NOTE: provide an event the store can consume in order to modify the server-supplied metadata
        }

        return this.callParent([data]);
    },

    //NOTE: because our 9.1 API format returns results as objects, we transform them here
    buildFieldExtractors: function() {
        //now build the extractors for all the fields
        var me = this,
            fields = me.getFields(),
            ln = fields.length,
            i  = 0,
            extractorFunctions = [],
            field, map;

        for (; i < ln; i++) {
            field = fields[i];
            map   = field.fieldKey || field.name;
            if(!field.notFromServer)
                extractorFunctions.push(me.createAccessor('["'+map+'"].value'));  //NOTE: modified to support 9.1 API format and to support lookups, ie. field1/field2.
            else
                extractorFunctions.push(me.createAccessor(map));  //if this field doesnt exist on the server, it wont have a value
        }
        me.fieldCount = ln;

        me.extractorFunctions = extractorFunctions;
    },
    /*
    NOTE: see above comment on 9.1 API.  In addition to extracting the values, Ext creates an accessor for the record's ID
    this must also be modified to support the 9.1 API.  Because I believe getId() can be called both on initial load (prior to
    when we transform the data) and after, I modified the method to test whether the field's value is an object instead of
    looking for '.value' exclusively.
     */
    buildExtractors: function(force) {
        this.callParent(arguments);

        var idProp = this.getIdProperty();
        var me = this;
        if (idProp) {
            var accessor = me.createAccessor(idProp);

            me.getId = function(record) {
                var id = accessor.call(me, record);
                return (id === undefined || id === '') ? null
                : (id && Ext4.isObject(id)) ? id.value  //NOTE: added line to support 9.1 API
                : id;
            };
        } else {
            me.getId = function() {
                return null;
            };
        }
    }
});


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

        this.callParent([config]);
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

    //NOTE: these are overriden so we can batch insert/update/deletes into a single request, rather than submitting 3 sequential ones
    batch: function(operations, listeners) {
        var batch = this.buildBatch(operations, listeners);

        batch.start();
        return batch;
    },
    buildBatch: function(operations, listeners){
        var me = this,
            batch = Ext4.create('Ext.data.Batch', {
                proxy: me,
                listeners: listeners || {}
            }),
            useBatch = me.batchActions,
            records;

        var commands = [];
        Ext4.each(me.batchOrder.split(','), function(action) {
            records = operations[action];
            if (records) {
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
            }
        }, me);
        if(commands.length){
            var request = Ext4.create('Ext.data.Request', {
                action: 'saveRows',
                url: LABKEY.ActionURL.buildURL("query", 'saveRows', this.extraParams.containerPath),
                jsonData: Ext.apply(this.extraParams, {
                    commands: commands
                })
            });

            var b = Ext4.create('Ext.data.Operation', {
                action: 'saveRows',
                request: request
            });
            batch.add(b);
        }

        return batch;
    },
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
        if(request.method != 'GET')
            delete request.params;  //would be applied to the URL

        //morph request into the commands expected by saverows:
        request.jsonData.commands = request.jsonData.commands || [];

        var command = this.buildCommand(operation);
        if(command && command.rows.length){
            request.jsonData.commands.push(command);
        }

        return request;
    },
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

            Ext4.each(operation.records, function(record){
                var oldKeys = {};
                oldKeys[this.reader.getIdProperty()] = record.internalId;

                command.rows.push({
                    values: this.getRowData(record),
                    oldKeys : oldKeys
                });
            }, this);

            return command;
        }
    },
    buildUrl: function(request) {
        var url = this.callParent(arguments);
        return LABKEY.ActionURL.buildURL("query", url, request.params.containerPath);
    },
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
    encodeSorters: function(sorters){
         var length   = sorters.length,
             sortStrs = [],
             sorter, i;

         for (i = 0; i < length; i++) {
             sorter = sorters[i];

             sortStrs[i] = (sorter.direction=='DESC' ? '-' : '') + sorter.property
         }

         return sortStrs.join(",");
    }
    //NOTE: perhaps this could be used to translate Ext filters into the expected labkey filters?
//    encodeFilters: function(filters){
//        return this.callParent(arguments);
//    }
});
