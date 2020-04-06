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

    constructor : function() {
        this.callParent();

        this.queries = undefined;
    },

    clear : function() {
        this.queries = undefined;
        this.currentContainer = undefined;
        this.totalContainers = 0;
        this.containers = [];
    },

    getCacheKey : function(container, schemaName, queryName) {
        return container + '.' + schemaName + '.' + queryName;
    },

    getDependencies : function(container, schemaName, queryName) {
        return this.queries[this.getCacheKey(container, schemaName, queryName)];
    },

    load : function(containerPath, success, failure, scope) {

        if (!this.queries) {
            this.analyzeQueries({
                containerPath : containerPath,
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
            const key = this.getCacheKey(d.to.containerId, d.to.schemaName, d.to.name);
            let query = this.queries[key] || this.createQuery(key, d.to);

            Ext4.each(d.from, function(item){
                query.dependents.push(item);
            }, this);
        }, this);

        Ext4.each(o.dependees, function(d){
            const key = this.getCacheKey(d.from.containerId, d.from.schemaName, d.from.name);
            let query = this.queries[key] || this.createQuery(key, d.from);

            Ext4.each(d.to, function (item) {
                query.dependees.push(item);
            }, this);
        }, this);
    },

    createQuery : function(cacheKey, q) {
        this.queries[cacheKey] = {q : Ext4.clone(q), dependents : [], dependees : []};
        return this.queries[cacheKey];
    },

    // hits the server endpoint (premium only) to create the dependency graph
    analyzeQueries : function(config) {
        function fixupJsonResponse(json, response, options, container) {
            var callback = LABKEY.Utils.getOnSuccess(config);
            this.currentContainer = container;

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

            for (key in dependeesMap) {
                if (dependeesMap.hasOwnProperty(key)) {
                    let from = objects[key];
                    // limit dependants to only queries in the current folder
                    if (LABKEY.container.id === from.containerId) {
                        this.dependeesList.push({from: from, to: dependeesMap[key]});
                    }
                }
            }

            for (key in dependantsMap) {
                if (dependantsMap.hasOwnProperty(key)) {
                    let to = objects[key];
                    // limit dependants to only queries in the current folder
                    if (LABKEY.container.id === to.containerId) {
                        this.dependantsList.push({to:to, from:dependantsMap[key]});
                    }
                }
            }

            this.removeContainer(container);
            if (this.containers.length === 0){
                if (callback)
                    callback.call(this, {success:json.success, dependants:this.dependantsList, dependees:this.dependeesList}, response, options);
            }
        }

        // initialize class data structures
        this.dependantsList = [];
        this.dependeesList = [];
        this.containers = [];
        this.containers.push(config.containerPath || LABKEY.container.path);
        let includeSubfolders = config.containerPath != null;

        // get the collection of container paths including child containers
        LABKEY.Security.getContainers({
            containerPath : config.containerPath,
            includeSubfolders : includeSubfolders,
            scope : this,
            success : function(resp){
                if (includeSubfolders) {
                    Ext4.each(resp.children, function(c) {
                        this.addContainer(c, config.containerPath != null);
                    }, this);
                }

                // analyze queries for each container
                this.totalContainers = this.containers.length;
                Ext4.each(this.containers, function(c){
                    LABKEY.Ajax.request({
                        url: LABKEY.ActionURL.buildURL('query', 'analyzeQueries.api', c),
                        method: 'GET',
                        scope: this,
                        success: function(resp, options){
                            fixupJsonResponse.call(this, LABKEY.Utils.decode(resp.responseText), resp, options, c);
                        },
                        failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), this, true)
                    });
                }, this);
            },
            failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), this, true)
        });
    },

    addContainer : function(container) {
        this.containers.push(container.path);
        Ext4.each(container.children, function(c){
            this.addContainer(c);
        }, this);
    },

    removeContainer : function(container) {
        let idx = this.containers.indexOf(container);
        if (idx != -1) {
            this.containers.splice(idx, 1);
        }
    },

    // return the current progress for this loader
    getProgress : function() {
        return {
            currentContainer: this.currentContainer,
            progress: 1.0 - (this.containers.length / this.totalContainers)
        };
    }
});