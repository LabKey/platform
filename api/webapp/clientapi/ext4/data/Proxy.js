/*
 * Copyright (c) 2012-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

/**
 * @name LABKEY.ext4.data.AjaxProxy
 * @-class
 * The primary reason to create a custom proxy is to support batching CRUD requests into a single request to saveRows().  Otherwise Ext
 * would perform each action as a separate request.
 */
Ext4.define('LABKEY.ext4.data.AjaxProxy', {
    /**
     * @-lends LABKEY.ext4.data.AjaxProxy
     */
    extend: 'Ext.data.proxy.Ajax',
    alias: 'proxy.labkeyajax',
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
    reader: 'labkeyjson',
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
     * Overridden so we can batch insert/update/deletes into a single request using saveRows, rather than submitting 3 sequential ones
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
        if (commands.length){
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

        if (action == 'read'){
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
        if (this.extraParams.sql){
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
        if (command && command.rows.length){
            request.jsonData.commands.push(command);
        }

        return request;
    },

    //does not override an Ext method - used internally
    buildCommand: function(operation){
        if (operation.action!='read'){
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

            if (operation.action=='create')
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

                if (command.command == 'delete'){
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
        for (var field in data)
        {
            if (Ext4.isEmpty(data[field]))
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
        if (params.filter && params.filter.length){
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
     * @name encodeSorters
     * @function
     * @memberOf LABKEY.ext4.data.AjaxProxy#
     *
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
        if (filters && filters.length){
            Ext4.each(filters, function(filter){
                if (filter.filterType)
                    result.push(Ext4.htmlEncode('query.' + filter.property + '~' + filter.filterType.getURLSuffix()) + '=' + Ext4.htmlEncode(filter.value));
            }, this);
        }
        return result;
    }
});
