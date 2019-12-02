/*
 * Copyright (c) 2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.query.browser.cache.Query', {
    singleton: true,

    clearAll : function() {
        delete this.schemaTree;
    },

    getQueries : function(schemaName, callback, scope) {
        if (this.schemaTree) {
            var schema = this.lookupSchema(this.schemaTree, schemaName);
            if (!schema)
                throw "schema name '" + schemaName + "' does not exist!";

            if (schema.queriesMap) {
                if (Ext4.isFunction(callback)) {
                    callback.call(scope || this, schema.queriesMap);
                }
            }
            else {
                LABKEY.Query.getQueries({
                    schemaName: '' + schemaName, // stringify LABKEY.SchemaKey
                    includeColumns: false,
                    includeUserQueries: true,
                    successCallback: function(data) {
                        var schema = this.lookupSchema(this.schemaTree, schemaName);
                        schema.queriesMap = {};
                        var query;
                        for (var idx = 0; idx < data.queries.length; ++idx) {
                            query = data.queries[idx];
                            schema.queriesMap[query.name] = query;
                        }
                        this.getQueries(schemaName, callback, scope);
                    },
                    scope: this
                });
            }
        }
        else {
            this.getSchemas(function() {
                this.getQueries(schemaName, callback, scope);
            }, this);
        }
    },

    getSchema : function (schemaName, callback, scope) {
        if (Ext4.isFunction(callback)) {
            this.getSchemas(function(schemaTree) {
                callback.call(scope || this, this.lookupSchema(schemaTree, schemaName));
            }, this);
        }
        else {
            return this.lookupSchema(this.schemaTree, schemaName);
        }
    },

    getSchemas : function (callback, scope) {
        if (this.schemaTree) {
            if (Ext4.isFunction(callback)) {
                callback.call(scope || this, this.schemaTree);
            }
        }
        else {
            LABKEY.Query.getSchemas({
                apiVersion: 9.3,
                successCallback: function(schemaTree) {
                    this.schemaTree = {schemas: schemaTree};
                    this.getSchemas(callback, scope);
                },
                scope: this
            });
        }
    },

    // Find the schema named by schemaPath in the schemaTree.
    lookupSchema : function (schemaTree, schemaName)
    {
        if (!schemaTree)  {
            return null;
        }

        if (!(schemaName instanceof LABKEY.SchemaKey)) {
            schemaName = LABKEY.SchemaKey.fromString(schemaName);
        }

        var schema = schemaTree,
            parts = schemaName.getParts(), i;

        for (i = 0; i < parts.length; i++) {
            schema = schema.schemas[parts[i]];
            if (!schema) {
                break;
            }
        }

        return schema;
    }
});

Ext4.define('LABKEY.query.browser.cache.QueryDetails', {
    singleton: true,

    mixins: {
        observable: 'Ext.util.Observable'
    },

    constructor : function(config) {
        this.callParent([config]);

        this.mixins.observable.constructor.call(this, config);

        this.addEvents('newdetails');

        this.detailsCache = {};
    },

    clear : function(schemaName, queryName, fk) {
        this.detailsCache[this.getCacheKey(schemaName, queryName, fk)] = undefined;
    },

    clearAll : function() {
        this.detailsCache = {};
    },

    getCacheKey : function(schemaName, queryName, fk) {
        return schemaName + '.' + queryName + (fk ? '.' + fk : '');
    },

    getQueryDetails : function(schemaName, queryName, fk) {
        return this.detailsCache[this.getCacheKey(schemaName, queryName, fk)];
    },

    loadQueryDetails : function(schemaName, queryName, fk, success, failure, scope) {
        var cacheKey = this.getCacheKey(schemaName, queryName, fk);
        if (this.detailsCache[cacheKey]) {
            if (Ext4.isFunction(success)) {
                success.call(scope || this, this.detailsCache[cacheKey]);
            }
        }
        else {
            LABKEY.Query.getQueryDetails({
                schemaName: '' + schemaName, // stringify LABKEY.SchemaKey
                queryName: queryName,
                fk: fk,
                success: function(json) {
                    this.detailsCache[cacheKey] = json;
                    this.fireEvent('newdetails', json);
                    if (Ext4.isFunction(success)) {
                        success.call(scope || this, json);
                    }
                },
                failure: failure,
                scope: this
            });
        }
    }
});

Ext4.define('LABKEY.query.browser.cache.QueryDependencies', {
    singleton: true,

    constructor : function(config) {
        this.callParent([config]);

        this.queries = undefined;
    },

    clear : function() {
        this.queries = undefined;
    },

    getCacheKey : function(schemaName, queryName) {
        return schemaName + '.' + queryName;
    },

    getDependencies : function(schemaName, queryName) {
        return this.queries[this.getCacheKey(schemaName, queryName)];
    },

    load : function(success, failure, scope) {

        if (!this.queries) {
            this.analyzeQueries({
                success : function(resp){
                    this.processDependencies(resp);
                    if (Ext4.isFunction(success)){
                        success.call(scope || this)
                    }
                },
                failure : failure,
                scope : this
            });
        }
        else {
            if (Ext4.isFunction(success)){
                success.call(scope || this)
            }
        }
    },

    processDependencies : function(o){

        this.queries = {};
        Ext4.each(o.dependants, function(d){
            const key = this.getCacheKey(d.to.schemaName, d.to.name);
            let query = this.queries[key] || this.createQuery(key, d.to);

            Ext4.each(d.from, function(item){
                query.dependents.push(item);
            }, this);
        }, this);

        Ext4.each(o.dependees, function(d){
            const key = this.getCacheKey(d.from.schemaName, d.from.name);
            let query = this.queries[key] || this.createQuery(key, d.from);

            Ext4.each(d.to, function(item){
                query.dependees.push(item);
            }, this);
        }, this);
    },

    createQuery : function(cacheKey, q) {
        this.queries[cacheKey] = {q : Ext4.clone(q), dependents : [], dependees : []};
        return this.queries[cacheKey];
    },

    // hit's the server enpoint (premium only) to create the dependency graph
    analyzeQueries : function(config) {
        function fixupJsonResponse(json, response, options) {
            var callback = LABKEY.Utils.getOnSuccess(config);

            if (!json || !json.success) {
                if (callback)
                    callback.call(this, json, response, options);
                return;
            }

            var key,toKey,fromKey;
            var objects = json.objects;

            var dependantsMap = {};
            var dependeesMap  = {};

            for (var edge = 0; edge < json.graph.length; edge++) {
                fromKey = json.graph[edge][0];
                toKey = json.graph[edge][1];

                // objects I am dependant on are my dependees
                dependeesMap[fromKey] = dependeesMap[fromKey] || [];
                dependeesMap[fromKey].push(objects[toKey]);

                // objects are dependant on me are my dependants
                dependantsMap[toKey] = dependantsMap[toKey] || [];
                dependantsMap[toKey].push(objects[fromKey]);
            }

            var dependeesList = [];
            for (key in dependeesMap) {
                if (dependeesMap.hasOwnProperty(key))
                    dependeesList.push({from:objects[key], to:dependeesMap[key]});
            }

            var dependantsList = [];
            for (key in dependantsMap) {
                if (dependantsMap.hasOwnProperty(key))
                    dependantsList.push({to:objects[key], from:dependantsMap[key]});
            }

            if (callback)
                callback.call(this, {success:json.success, dependants:dependantsList, dependees:dependeesList}, response, options);
        }

        return LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('query', 'analyzeQueries.api', config.containerPath),
            method : 'GET',
            success: LABKEY.Utils.getCallbackWrapper(fixupJsonResponse, config.scope),
            failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true)
        });
    }
});