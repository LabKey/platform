/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2008-2017 LabKey Corporation
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p/>
 */

Ext.namespace("LABKEY", "LABKEY.ext");

/**
 * Constructs a new LabKey Store using the supplied configuration.
 * @class LabKey extension to the <a href="http://docs.sencha.com/extjs/3.4.0/#!/api/Ext.data.Store">Ext.data.Store</a> class,
 * which can retrieve data from a LabKey server, track changes, and update the server upon demand. This is most typically
 * used with data-bound user interface widgets, such as the <a href="http://docs.sencha.com/extjs/3.4.0/#!/api/Ext.grid.EditorGridPanel">Ext.grid.EditorGridPanel</a>.
 *
 * <p>If you use any of the LabKey APIs that extend Ext APIs, you must either make your code open source or
 * <a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=extDevelopment">purchase an Ext license</a>.</p>
 *            <p>Additional Documentation:
 *              <ul>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=javascriptTutorial">Tutorial: Create Applications with the JavaScript API</a></li>
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
 * @param {Object} [config.parameters] Specify parameters for parameterized queries.
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
 * @example &lt;div id="div1"/&gt;
 &lt;script type="text/javascript"&gt;

 // This sample code uses LABKEY.ext.Store to hold data from the server's Users table.
 // Ext.grid.EditorGridPanel provides a user interface for updating the Phone column.
 // On pressing the 'Submit' button, any changes made in the grid are submitted to the server.
 var _store = new LABKEY.ext.Store({
    schemaName: 'core',
    queryName: 'Users',
    columns: "DisplayName, Phone",
    autoLoad: true
});

 var _grid = new Ext.grid.EditorGridPanel({
    title: 'Users - Change Phone Number',
    store: _store,
    renderTo: 'div1',
    autoHeight: true,
    columnLines: true,
    viewConfig: {
        forceFit: true
    },
    colModel: new Ext.grid.ColumnModel({
        columns: [{
            header: 'User Name',
            dataIndex: 'DisplayName',
            hidden: false,
            width: 150
        }, {
            header: 'Phone Number',
            dataIndex: 'Phone',
            hidden: false,
            sortable: true,
            width: 100,
            editor: new Ext.form.TextField()
        }]
    }),
    buttons: [{
        text: 'Save',
        handler: function ()
        {
            _grid.getStore().commitChanges();
            alert("Number of records changed: "
                   + _grid.getStore().getModifiedRecords().length);
        }
    }, {
        text: 'Cancel',
        handler: function ()
        {
            _grid.getStore().rejectChanges();
        }
    }]
});

 &lt;/script&gt;
 */
LABKEY.ext.Store = Ext.extend(Ext.data.Store, {
    constructor: function(config) {

        // Issue 32269 - force key and other non-requested columns to be sent back
        var baseParams = {schemaName: config.schemaName, minimalColumns: false};
        var qsParams = {};

        if (config.queryName && !config.sql)
            baseParams['query.queryName'] = config.queryName;
        if (config.sql) {
            baseParams.sql = config.sql;
            config.updatable = false;
        }
        if (config.sort) {
            baseParams['query.sort'] = config.sort;

            if (config.sql) {
                qsParams['query.sort'] = config.sort;
            }
        }
        else if (config.sortInfo) {
            var sInfo = ('DESC' === config.sortInfo.direction ? '-' : '') + config.sortInfo.field;
            baseParams['query.sort'] = sInfo;

            if (config.sql) {
                qsParams['query.sort'] = sInfo;
            }
        }

        if (Ext.isObject(config.parameters)) {
            for (var n in config.parameters) {
                if (config.parameters.hasOwnProperty(n)) {
                    baseParams["query.param." + n] = config.parameters[n];
                }
            }
        }

        //important...otherwise the base Ext.data.Store interprets it
        delete config.sort;
        delete config.sortInfo;

        if (config.viewName && !config.sql)
            baseParams['query.viewName'] = config.viewName;

        if (config.columns && !config.sql)
            baseParams['query.columns'] = Ext.isArray(config.columns) ? config.columns.join(",") : config.columns;

        if (config.containerFilter)
            baseParams.containerFilter = config.containerFilter;

        if (config.ignoreFilter)
            baseParams['query.ignoreFilter'] = 1;

        if (config.maxRows) {
            // Issue 16076, executeSql needs maxRows not query.maxRows.
            if (config.sql)
                baseParams['maxRows'] = config.maxRows;
            else
                baseParams['query.maxRows'] = config.maxRows;
        }

        baseParams.apiVersion = 9.1;

        Ext.apply(this, config, {
            remoteSort: true,
            updatable: true
        });

        this.isLoading = false;

        LABKEY.ext.Store.superclass.constructor.call(this, {
            reader: new LABKEY.ext.ExtendedJsonReader(),
            proxy: this.proxy ||
                    new Ext.data.HttpProxy(new Ext.data.Connection({
                        method: 'POST',
                        url: (config.sql ? LABKEY.ActionURL.buildURL('query', 'executeSql.api', config.containerPath, qsParams)
                                : LABKEY.ActionURL.buildURL('query', 'selectRows.api', config.containerPath)),
                        listeners: {
                            beforerequest: {
                                fn: this.onBeforeRequest,
                                scope: this
                            }
                        },
                        timeout: Ext.Ajax.timeout
                    })),
            baseParams: baseParams,
            autoLoad: false
        });

        this.on('beforeload', this.onBeforeLoad, this);
        this.on('load', this.onLoad, this);
        this.on('loadexception', this.onLoadException, this);
        this.on('update', this.onUpdate, this);

        //Add this here instead of using Ext.store to make sure above listeners are added before 1st load
        if (config.autoLoad) {
            this.load.defer(10, this, [ Ext.isObject(this.autoLoad) ? this.autoLoad : undefined ]);
        }

        /**
         * @memberOf LABKEY.ext.Store#
         * @name beforecommit
         * @event
         * @description Fired just before the store sends updated records to the server for saving. Return
         * false from this event to stop the save operation.
         * @param {array} records An array of Ext.data.Record objects that will be saved.
         * @param {array} rows An array of simple row-data objects from those records. These are the actual
         * data objects that will be sent to the server.
         */
        /**
         * @memberOf LABKEY.ext.Store#
         * @name commitcomplete
         * @event
         * @description Fired after all modified records have been saved on the server.
         */
        /**
         * @memberOf LABKEY.ext.Store#
         * @name commitexception
         * @event
         * @description Fired if there was an exception during the save process.
         * @param {String} message The exception message.
         */
        this.addEvents("beforecommit", "commitcomplete", "commitexception");
    },

    /**
     * Adds a new record to the store based upon a raw data object.
     * @name addRecord
     * @function
     * @memberOf LABKEY.ext.Store#
     * @param {Object} data The raw data object containing a properties for each field.
     * @param {number} [index] The index at which to insert the record. If not supplied, the new
     * record will be added to the end of the store.
     * @returns {Ext.data.Record} The new Ext.data.Record object.
     */
    addRecord : function(data, index) {
        if (!this.updatable)
            throw "this LABKEY.ext.Store is not updatable!";

        if (undefined == index)
            index = this.getCount();

        var fields = this.reader.meta.fields;

        //if no data was passed, create a new object with
        //all nulls for the field values
        if (!data) {
            data = {};
        }

        //set any non-specified field to null
        //some bound control (like the grid) need a property
        //defined for each field
        var field;
        for (var idx = 0; idx < fields.length; ++idx)
        {
            field = fields[idx];
            if (!data[field.name])
                data[field.name] = null;
        }

        var recordConstructor = Ext.data.Record.create(fields);
        var record = new recordConstructor(data);

        //add an isNew property so we know that this is a new record
        record.isNew = true;
        this.insert(index, record);
        return record;
    },

    /**
     * Deletes a set of records from the store as well as the server. This cannot be undone.
     * @name deleteRecords
     * @function
     * @memberOf LABKEY.ext.Store#
     * @param {Array of Ext.data.Record objects} records The records to delete.
     */
    deleteRecords : function(records) {
        if (!this.updatable)
            throw "this LABKEY.ext.Store is not updatable!";

        if (!records || records.length === 0)
            return;

        var deleteRowsKeys = [];
        var key;
        for (var idx = 0; idx < records.length; ++idx)
        {
            key = {};
            key[this.idName] = records[idx].id;
            deleteRowsKeys[idx] = key;
        }

        //send the delete
        LABKEY.Query.deleteRows({
            schemaName: this.schemaName,
            queryName: this.queryName,
            containerPath: this.containerPath,
            rows: deleteRowsKeys,
            successCallback: this.getDeleteSuccessHandler(),
            action: "deleteRows" //hack for Query.js bug
        });
    },


    getChanges : function(records) {
        records = records || this.getModifiedRecords();

        if (!records || records.length === 0) {
            return [];
        }

        if (!this.updatable) {
            throw "this LABKEY.ext.Store is not updatable!";
        }

        //build the json to send to the server
        var insertCommand = {
            schemaName: this.schemaName,
            queryName: this.queryName,
            command: 'insertWithKeys',
            rows: []
        };
        var updateCommand = {
            schemaName: this.schemaName,
            queryName: this.queryName,
            command: 'updateChangingKeys',
            rows: []
        };
        for (var idx = 0; idx < records.length; ++idx) {
            var record = records[idx];

            //if we are already in the process of saving this record, just continue
            if (record.saveOperationInProgress)
                continue;

            //NOTE: this check could possibly be eliminated since the form/server should do the same thing
            if (!this.readyForSave(record))
                continue;

            record.saveOperationInProgress = true;
            //NOTE: modified since ext uses the term phantom for any record not saved to server
            if (record.isNew || record.phantom)
            {
                insertCommand.rows.push({
                    values: this.getRowData(record),
                    oldKeys : this.getOldKeys(record)
                });
            }
            else
            {
                updateCommand.rows.push({
                    values: this.getRowData(record),
                    oldKeys : this.getOldKeys(record)
                });
            }
        }

        var commands = [];
        if (insertCommand.rows.length > 0)
        {
            commands.push(insertCommand);
        }
        if (updateCommand.rows.length > 0)
        {
            commands.push(updateCommand);
        }

        for (var i=0;i<commands.length;i++) {
            if (commands[i].rows.length > 0 && false === this.fireEvent("beforecommit", records, commands[i].rows))
                return [];
        }

        return commands
    },

    /**
     * Commits all changes made locally to the server. This method executes the updates asynchronously,
     * so it will return before the changes are fully made on the server. Records that are being saved
     * will have a property called 'saveOperationInProgress' set to true, and you can test if a Record
     * is currently being saved using the isUpdateInProgress method. Once the record has been updated
     * on the server, its properties may change to reflect server-modified values such as Modified and ModifiedBy.
     * <p>
     * Before records are sent to the server, the "beforecommit" event will fire. Return false from your event
     * handler to prohibit the commit. The beforecommit event handler will be passed the following parameters:
     * <ul>
     * <li><b>records</b>: An array of Ext.data.Record objects that will be sent to the server.</li>
     * <li><b>rows</b>: An array of row data objects from those records.</li>
     * </ul>
     * <p>
     * The "commitcomplete" or "commitexception" event will be fired when the server responds. The former
     * is fired if all records are successfully saved, and the latter if an exception occurred. All modifications
     * to the server are transacted together, so all records will be saved or none will be saved. The "commitcomplete"
     * event is passed no parameters. The "commitexception" even is passed the error message as the only parameter.
     * You may return false form the "commitexception" event to supress the default display of the error message.
     * <p>
     * For information on the Ext event model, see the
     * <a href="http://www.extjs.com/deploy/dev/docs/?class=Ext.util.Observable">Ext API documentation</a>.
     * @name commitChanges
     * @function
     * @memberOf LABKEY.ext.Store#
     */    
    commitChanges : function(){
        var records = this.getModifiedRecords();
        var commands = this.getChanges(records);

        if (!commands.length) {
            return false;
        }

        Ext.Ajax.request({
            url : LABKEY.ActionURL.buildURL("query", "saveRows", this.containerPath),
            method : 'POST',
            success: this.onCommitSuccess,
            failure: this.getOnCommitFailure(records),
            scope: this,
            jsonData : {
                containerPath: this.containerPath,
                commands: commands
            },
            headers : {
                'Content-Type' : 'application/json'
            }
        });
    },

    /**
     * Returns true if the given record is currently being updated on the server, false if not.
     * @param {Ext.data.Record} record The record.
     * @returns {boolean} true if the record is currently being updated, false if not.
     * @name isUpdateInProgress
     * @function
     * @memberOf LABKEY.ext.Store#
     */
    isUpdateInProgress : function(record) {
        return record.saveOperationInProgress;
    },

    /**
     * Returns a LabKey.ext.Store filled with the lookup values for a given
     * column name if that column exists, and if it is a lookup column (i.e.,
     * lookup meta-data was supplied by the server).
     * @param columnName The column name
     * @param includeNullRecord Pass true to include a null record at the top
     * so that the user can set the column value back to null. Set the
     * lookupNullCaption property in this Store's config to override the default
     * caption of "[none]".
     */
    getLookupStore : function(columnName, includeNullRecord)
    {
        if (!this.lookupStores)
            this.lookupStores = {};

        var store = this.lookupStores[columnName];
        if (!store)
        {
            //find the column metadata
            var fieldMeta = this.findFieldMeta(columnName);
            if (!fieldMeta)
                return null;

            //create the lookup store and kick off a load
            var config = {
                schemaName: fieldMeta.lookup.schema,
                queryName: fieldMeta.lookup.table,
                containerPath: fieldMeta.lookup.containerPath || this.containerPath
            };
            if (includeNullRecord)
                config.nullRecord = {
                    displayColumn: fieldMeta.lookup.displayColumn,
                    nullCaption: this.lookupNullCaption || "[none]"
                };

            store = new LABKEY.ext.Store(config);
            this.lookupStores[columnName] = store;
        }
        return store;
    },

    exportData : function(format) {
        format = format || "excel";
        if (this.sql)
        {
            var exportCfg = {
                schemaName: this.schemaName,
                sql: this.sql,
                format: format,
                containerPath: this.containerPath,
                containerFilter: this.containerFilter
            };

            // TODO: Enable filtering on exportData
//            var filters = [];
//
//            // respect base filters
//            if (Ext.isArray(this.filterArray)) {
//                filters = this.filterArray;
//            }
//
//            // respect user/view filters
//            if (this.getUserFilters().length > 0) {
//                var userFilters = this.getUserFilters();
//                for (var f=0; f < userFilters.length; f++) {
//                    filters.push(userFilters[f]);
//                }
//            }
//
//            if (filters.length > 0) {
//                exportCfg['filterArray'] = filters;
//            }

            LABKEY.Query.exportSql(exportCfg);
        }
        else
        {
            var params = {
                schemaName: this.schemaName,
                "query.queryName": this.queryName,
                "query.containerFilterName": this.containerFilter,
                "query.showRows": 'all'
            };

            if (this.columns) {
                params['query.columns'] = this.columns;
            }

            // These are filters that are custom created (aka not from a defined view).
            LABKEY.Filter.appendFilterParams(params, this.filterArray);
            
            if (this.sortInfo) {
                params['query.sort'] = "DESC" == this.sortInfo.direction
                        ? "-" + this.sortInfo.field
                        : this.sortInfo.field;
            }

            // These are filters that are defined by the view.
            LABKEY.Filter.appendFilterParams(params, this.getUserFilters());

            var action = ('tsv' === format) ? 'exportRowsTsv' : 'exportRowsExcel';
            window.location = LABKEY.ActionURL.buildURL("query", action, this.containerPath, params);
        }
    },

    /*-- Private Methods --*/

    onCommitSuccess : function(response) {
        var json = this.getJson(response);
        if (!json || !json.result) {
            return;
        }

        for (var cmdIdx = 0; cmdIdx < json.result.length; ++cmdIdx) {
            this.processResponse(json.result[cmdIdx].rows);
        }
        this.fireEvent('commitcomplete');
    },

    processResponse : function(rows) {
        var idCol = this.reader.jsonData.metaData.id;
        var row;
        var record;
        for (var idx = 0; idx < rows.length; ++idx)
        {
            row = rows[idx];

            if (!row || !row.values)
                return;

            //find the record using the id sent to the server
            record = this.getById(row.oldKeys[this.reader.meta.id]);
            if (!record)
                return;

            //apply values from the result row to the sent record
            for (var col in record.data)
            {
                if (!record.data.hasOwnProperty(col)) {
                    continue;
                }

                //since the sent record might contain columns form a related table,
                //ensure that a value was actually returned for that column before trying to set it
                if (undefined !== row.values[col]) {
                    record.set(col, record.fields.get(col).convert(row.values[col], row.values));
                }

                //clear any displayValue there might be in the extended info
                if (record.json && record.json[col])
                    delete record.json[col].displayValue;
            }

            //if the id changed, fixup the keys and map of the store's base collection
            //HACK: this is using private data members of the base Store class. Unfortunately
            //Ext Store does not have a public API for updating the key value of a record
            //after it has been added to the store. This might break in future versions of Ext.
            if (record.id != row.values[idCol])
            {
                record.id = row.values[idCol];
                this.data.keys[this.data.indexOf(record)] = row.values[idCol];

                delete this.data.map[record.id];
                this.data.map[row.values[idCol]] = record;
            }

            //reset transitory flags and commit the record to let
            //bound controls know that it's now clean
            delete record.saveOperationInProgress;
            delete record.isNew;
            record.commit();
        }

    },

    getOnCommitFailure : function(records) {
        return function(response) {
            
            for (var idx = 0; idx < records.length; ++idx) {
                delete records[idx].saveOperationInProgress;
            }

            var json = this.getJson(response);
            var message = (json && json.exception) ? json.exception : response.statusText;

            if (false !== this.fireEvent('commitexception', message)) {
                Ext.Msg.alert('Error During Save', 'Could not save changes due to the following error:\n' + message);
            }
        };
    },

    getJson : function(response) {
        return (response && undefined != response.getResponseHeader && undefined != response.getResponseHeader('Content-Type')
                && response.getResponseHeader('Content-Type').indexOf('application/json') >= 0)
                ? Ext.util.JSON.decode(response.responseText)
                : null;
    },

    findFieldMeta : function(columnName)
    {
        var fields = this.reader.meta.fields;
        for (var idx = 0; idx < fields.length; ++idx) {
            if (fields[idx].name === columnName) {
                return fields[idx];
            }
        }
        return null;
    },

    onBeforeRequest : function(connection, options) {
        if (this.sql) {
            // need to adjust url
            var qsParams = {};
            if (options.params['query.sort']) {
                qsParams['query.sort'] = options.params['query.sort'];
            }

            options.url = LABKEY.ActionURL.buildURL('query', 'executeSql.api', this.containerPath, qsParams);
        }
    },

    onBeforeLoad : function(store, options) {
        this.isLoading = true;

        //the selectRows.api can't handle the 'sort' and 'dir' params
        //sent by Ext, so translate them into the expected form
        if (options.params && options.params.sort) {
            options.params['query.sort'] = 'DESC' === options.params.dir
                    ? "-" + options.params.sort
                    : options.params.sort;
            delete options.params.sort;
            delete options.params.dir;
        }

        // respect base filters
        var baseFilters = {};
        if (Ext.isArray(this.filterArray)) {
            LABKEY.Filter.appendFilterParams(baseFilters, this.filterArray);
        }

        // respect user filters
        var userFilters = {};
        LABKEY.Filter.appendFilterParams(userFilters, this.getUserFilters());

        Ext.applyIf(baseFilters, userFilters);

        // remove all query filters in base parameters
        for (var param in this.baseParams) {
            if (this.baseParams.hasOwnProperty(param) && this.isFilterParam(param)) {
                delete this.baseParams[param];
            }
        }

        for (param in baseFilters) {
            if (baseFilters.hasOwnProperty(param)) {
                this.setBaseParam(param, baseFilters[param]);
            }
        }
    },

    isFilterParam : function(param) {
        // Set of parameters that are reserved for query
        var prefixes = {
            columns: true,
            containerFilterName: true,
            ignoreFilter: true,
            maxRows: true,
            param: true,
            queryName: true,
            sort: true,
            viewName: true
        };

        // 31656: Ensure query parameters are passed along
        if (Ext.isString(param)) {
            if (param.indexOf('query.') === 0) {
                var prefix = param.replace('query.', '').split('.')[0];
                if (!prefixes[prefix]) {
                    return true;
                }
            }
        }
        return false;
    },

    onLoad : function() {
        this.isLoading = false;

        //remember the name of the id column
        this.idName = this.reader.meta.id;

        if (this.nullRecord) {
            //create an extra record with a blank id column
            //and the null caption in the display column
            var data = {};
            data[this.reader.meta.id] = "";
            data[this.nullRecord.displayColumn] = this.nullRecord.nullCaption || this.nullCaption || "[none]";

            var recordConstructor = Ext.data.Record.create(this.reader.meta.fields);
            var record = new recordConstructor(data, -1);
            this.insert(0, record);
        }
    },

    onLoadException : function(proxy, options, response, error)
    {
        this.isLoading = false;
        var loadError = {message: error};

        if (response && response.getResponseHeader
                && response.getResponseHeader("Content-Type").indexOf("application/json") >= 0)
        {
            var errorJson = Ext.util.JSON.decode(response.responseText);
            if (errorJson && errorJson.exception)
                loadError.message = errorJson.exception;
        }

        this.loadError = loadError;
    },

    onUpdate : function(store, record) {
        var changes = record.getChanges();
        for (var field in changes) {
            if (changes.hasOwnProperty(field) && record.json && record.json[field]) {
                delete record.json[field].displayValue;
                delete record.json[field].mvValue;
            }
        }
    },

    getDeleteSuccessHandler : function() {
        var store = this;
        return function(results) {
            store.fireEvent("commitcomplete");
            store.reload();
        };
    },

    getRowData : function(record) {
        //need to convert empty strings to null before posting
        //Ext components will typically set a cleared field to
        //empty string, but this messes-up Lists in LabKey 8.2 and earlier
        var data = {};
        Ext.apply(data, record.data);
        for (var field in data)
        {
            if (null != data[field] && data[field].toString().length == 0)
                data[field] = null;
        }
        return data;
    },

    getOldKeys : function(record) {
        var oldKeys = {};
        oldKeys[this.reader.meta.id] = record.id;
        return oldKeys;
    },

    readyForSave : function(record) {
        //this is kind of hacky, but it seems that checking
        //for required columns is the job of the store, not
        //the bound control. Since the required prop is in the
        //column model, go get that from the reader
        if (this.noValidationCheck)
            return true;

        var colmodel = this.reader.jsonData.columnModel;
        if (!colmodel)
            return true;

        var col;
        for (var idx = 0; idx < colmodel.length; ++idx)
        {
            col = colmodel[idx];

            if (col.dataIndex != this.reader.meta.id && col.required && !record.data[col.dataIndex])
                return false;
        }

        return true;
    },

    getUserFilters: function()
    {
        return this.userFilters || [];
    },

    setUserFilters: function(filters)
    {
        this.userFilters = filters;
    },

    getSql : function ()
    {
        return this.sql;
    },

    setSql : function (sql)
    {
        this.sql = sql;
        this.setBaseParam("sql", sql);
    }
});
Ext.reg("labkey-store", LABKEY.ext.Store);

