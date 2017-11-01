/*
 * Copyright (c) 2015-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.internal.ViewDesigner.QueryDetailsCache', {

    singleton: true,

    mixins : {
        observable: 'Ext.util.Observable'
    },

    constructor : function() {
        this.cache = {};
        this.results = {};

        this.mixins.observable.constructor.apply(this, arguments);

        this.addEvents('beforecacheresponse');
    },

    callback : function(callback, scope, result) {
        if (Ext4.isFunction(callback)) {
            callback.call(scope, result);
        }
    },

    add : function(keyConfig, result) {
        this.populateCache(this.getKey(keyConfig), result);
    },

    getKey : function(config) {

        if (!Ext4.isString(config.schema) || !Ext4.isString(config.query) ||
                Ext4.isEmpty(config.schema) || Ext4.isEmpty(config.query)) {
            throw 'Invalid cache key. Require a schema and a query.';
        }

        return JSON.stringify({
            schema: config.schema,
            query: config.query,
            view: !Ext4.isEmpty(config.view) ? config.view: undefined,
            fk: Ext4.isString(config.fk) ? config.fk : undefined
        });
    },

    inCache : function(key) {
        return this.cache[key] === true;
    },

    inFlightCache : function(key) {
        return Ext4.isArray(this.cache[key]);
    },

    loadCache : function(key, callback, scope) {
        // The value as an array denotes the cache resource is in flight
        if (!this.cache[key]) {
            this.cache[key] = [];
        }

        if (Ext4.isFunction(callback)) {
            this.cache[key].push({fn: callback, scope: scope});
        }
    },

    populateCache : function(key, result) {

        //console.log('# cols (start):', result.columns.length);
        this.fireEvent('beforecacheresponse', result);
        //console.log('# cols (end)  :', result.columns.length);

        this.results[key] = result;

        var cbs = this.cache[key];
        this.cache[key] = true;

        if (!Ext4.isEmpty(cbs)) {
            Ext4.each(cbs, function(config) {
                this.callback(config.fn, config.scope, result);
            }, this);
        }
    },

    getDetails : function(keyConfig, callback, scope) {
        var key = this.getKey(keyConfig);

        if (this.inCache(key)) {
            // -- cache hit
            this.callback(callback, scope, this.results[key]);
        }
        else if (this.inFlightCache(key)) {
            // -- cache miss, in flight
            this.loadCache(key, callback, scope);
        }
        else {
            // -- cache miss
            this.loadCache(key, callback, scope);

            // Request Details -- assume the current container
            LABKEY.Query.getQueryDetails({
                schemaName: keyConfig.schema,
                queryName: keyConfig.query,
                containerPath: keyConfig.containerPath,
                fk: keyConfig.fk,
                success: function(result) {
                    this.populateCache(key, result);
                },
                scope: this
            });
        }
    }
});

Ext4.define('LABKEY.internal.ViewDesigner.QueryDetailsProxy', {
    extend: 'Ext.data.proxy.Ajax',
    alias: 'proxy.querydetails',
    schema: undefined,
    query: undefined,
    view: undefined,
    url: '',
    read: function(operation, callback, scope) {
        var request = this.buildRequest(operation),
            params = {
                schema: this.schema,
                query: this.query,
                view: this.view,
                containerPath: this.containerPath
            },
            cacheCallback = this.createCacheCallback(request, operation, callback, scope);

        // get the paramters for this request
        if (!Ext4.isEmpty(request.params)) {
            // see if the 'node' parameter is in-use, could be a tree store
            if (!Ext4.isEmpty(request.params.node) &&
                    request.params.node !== LABKEY.internal.ViewDesigner.FieldMetaTreeStore.ROOT_ID) {
                if (operation.node && operation.node.get('fieldKey') !== undefined) {
                    params.fk = operation.node.get('fieldKey');
                }
            }
        }

        LABKEY.internal.ViewDesigner.QueryDetailsCache.getDetails(params, cacheCallback, this);
    },

    createCacheCallback : function(request, operation, callback, scope) {
        var me = this;
        return function(response) {
            me.processResponse(true, operation, request, response, callback, scope);
        }
    }
});