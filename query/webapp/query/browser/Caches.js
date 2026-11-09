/*
 * Copyright (c) 2015-2026 LabKey Corporation
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
        return schemaName + '.' + queryName.toLowerCase() + (fk ? '.' + fk : '');
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
                // Only fetch trigger scripts for the top-level table or query
                includeTriggers: fk === undefined,
                success: function(json) {
                    this.detailsCache[cacheKey] = json;
                    this.fireEvent('newdetails', json);
                    if (Ext4.isFunction(success)) {
                        success.call(scope || this, json);
                    }
                },
                failure: function(error) {
                    if (Ext4.isFunction(failure)) {
                        failure.call(scope || this, error);
                    }
                },
                scope: this
            });
        }
    }
});

Ext4.define('LABKEY.query.browser.cache.QueryDependencies', {
    singleton: true,

    /**
     * Maximum number of analyzeQueries.api requests to keep in flight at once. Each request builds the full
     * TableInfo/ColumnInfo graph for one folder and holds it for the life of the request, so dispatching one
     * request per folder can exhaust the server's request thread pool and its heap on a site-level analysis.
     */
    MAX_CONCURRENT_REQUESTS : 4,

    constructor : function() {
        this.callParent();

        this.queries = undefined;
    },

    clear : function() {
        this.queries = undefined;
        this.currentContainer = undefined;
        this.analyzedContainerPath = undefined;
        this.totalContainers = 0;
        this.containers = [];
        this.activeContainers = {};
        this.activeRequests = {};
        this.activeCount = 0;
        this.finishedCount = 0;
        this.analysisComplete = false;
        this.cancelled = false;
        this.lastResponse = undefined;
        this.errors = [];
    },

    getCacheKey : function(container, schemaName, queryName) {
        return container + '.' + schemaName + '.' + queryName.toLowerCase();
    },

    getDependencies : function(container, schemaName, queryName) {
        return this.queries[this.getCacheKey(container, schemaName, queryName)];
    },

    load : function(containerPath, success, failure, scope, containers) {

        if (!this.queries) {
            this.analyzeQueries({
                containerPath : containerPath,
                containers : containers,
                // callbacks take the accumulated result plus the response and options of the request that ended the
                // analysis; dropping either leaves the caller unable to report what actually went wrong
                success : function(result, response, options){
                    this.processDependencies(result);
                    if (Ext4.isFunction(success)){
                        success.call(scope || this, result, response, options);
                    }
                },
                failure : function(result, response, options) {
                    failure.call(scope || this, result, response, options);
                },
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

        // merge one container's response into the accumulated dependency lists
        function accumulateResponse(container, json, response, options) {
            // any response that isn't success:true failed, even when it carries no error message, and an unparseable
            // one leaves json null; swallowing either makes the dependency report look empty rather than broken
            if (!json || !json.success) {
                this.errors.push({containerPath: container, response: response, options: options});
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
        }

        // the analysis is finished only once the queue has drained AND every dispatched request has returned
        function checkComplete() {
            if (this.cancelled || this.analysisComplete || this.activeCount > 0 || this.containers.length > 0) {
                return;
            }
            this.analysisComplete = true;

            var failed = this.errors.length > 0;
            var callback = failed ? LABKEY.Utils.getOnFailure(config) : LABKEY.Utils.getOnSuccess(config);
            var last = this.lastResponse || {};
            var resp = last.response;
            var opts = last.options;
            if (failed) {
                resp = this.errors[0].response;
                opts = this.errors[0].options;
            }

            if (callback) {
                var success = failed ? false : (last.json ? last.json.success : false);
                callback.call(this, {success: success, dependants: this.dependantsList, dependees: this.dependeesList}, resp, opts);
            }
        }

        function requestComplete(container, json, response, options) {
            // ignore the abort callbacks that cancel() triggers, and any duplicate callback for a container that has
            // already been accounted for
            if (this.cancelled || !this.activeContainers[container]) {
                return;
            }
            delete this.activeContainers[container];
            delete this.activeRequests[container];
            this.activeCount--;
            this.finishedCount++;
            this.lastResponse = {response: response, options: options, json: json};

            accumulateResponse.call(this, container, json, response, options);
            pump.call(this);
        }

        function dispatch(container) {
            this.activeContainers[container] = true;
            this.activeCount++;
            this.currentContainer = container;

            this.activeRequests[container] = LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL('query', 'analyzeQueries.api', container),
                method: 'GET',
                scope: this,
                success: function(resp, options){
                    var json = null;
                    try {
                        json = LABKEY.Utils.decode(resp.responseText);
                    }
                    catch (e) {
                        console.warn('Invalid JSON returned from analyzeQueries.api : ' + resp.responseText);
                        console.warn('Response URL : ' + resp.responseURL);

                        // leave json null and finish processing this container
                    }
                    requestComplete.call(this, container, json, resp, options);
                },
                failure: function(resp, options){
                    console.warn('Analyze query request failed : ' + resp.responseText);
                    requestComplete.call(this, container, {error: true}, resp, options);
                }
            });
        }

        // keep up to MAX_CONCURRENT_REQUESTS requests in flight, then test for completion
        function pump() {
            while (this.activeCount < this.MAX_CONCURRENT_REQUESTS && this.containers.length > 0) {
                dispatch.call(this, this.containers.shift());
            }
            checkComplete.call(this);
        }

        // initialize class data structures
        this.dependantsList = [];
        this.dependeesList = [];
        this.containers = [];           // container paths queued but not yet requested
        this.activeContainers = {};     // container path -> true for each request in flight
        this.activeRequests = {};       // container path -> XMLHttpRequest, so cancel() can abort them
        this.activeCount = 0;
        this.finishedCount = 0;
        this.analysisComplete = false;
        this.cancelled = false;
        this.lastResponse = undefined;
        this.errors = [];
        this.analyzedContainerPath = config.containerPath;

        // the caller may have already resolved the scope via loadContainerCounts(), in which case there is nothing to look up
        if (config.containers) {
            this.containers = config.containers.slice();
            this.totalContainers = this.containers.length;
            pump.call(this);
            return;
        }

        this.containers.push(config.containerPath || LABKEY.container.path);
        let includeSubfolders = config.containerPath != null;

        // get the collection of container paths including child containers
        LABKEY.Security.getContainers({
            containerPath : config.containerPath,
            includeSubfolders : includeSubfolders,
            includeWorkbookChildren : false,
            includeStandardProperties : false,
            includeEffectivePermissions : false,
            scope : this,
            success : function(json){
                if (includeSubfolders) {
                    Ext4.each(json.children, function(c) {
                        this.addContainer(c, config.containerPath != null);
                    }, this);
                }

                // analyze queries for each container, at most MAX_CONCURRENT_REQUESTS at a time
                this.totalContainers = this.containers.length;
                pump.call(this);
            },
            failure : function(json, resp, options) {
                var callback = LABKEY.Utils.getOnFailure(config);
                if (callback) {
                    callback.call(this, {success: false, dependants: this.dependantsList, dependees: this.dependeesList}, resp, options);
                }
            }
        });
    },

    addContainer : function(container) {
        this.containers.push(container.path);
        Ext4.each(container.children, function(c){
            this.addContainer(c);
        }, this);
    },

    /**
     * Resolve, once per page load, the folders each analysis scope would process, so the UI can show the cost of an
     * analysis before starting one and then hand the resolved list straight to analyzeQueries(). The site-level tree is
     * a superset of the project-level one, so a single request covers both scopes.
     *
     * Workbooks are excluded: they hold no custom queries, but on some sites they outnumber real folders by orders of
     * magnitude, and each one would otherwise cost an analyzeQueries.api request.
     */
    loadContainerCounts : function(success, failure, scope) {
        if (this.containerScopes) {
            success.call(scope || this, this.containerScopes);
            return;
        }

        LABKEY.Security.getContainers({
            containerPath : '/',
            includeSubfolders : true,
            includeWorkbookChildren : false,
            // only id/name/path are needed, and resolving effective permissions for every folder on the site is by far
            // the most expensive part of this call
            includeStandardProperties : false,
            includeEffectivePermissions : false,
            scope : this,
            success : function(json) {
                let site = [];

                // a folder the user can't read is still in the tree if it has readable descendants, but analyzing it
                // would just 403, so key off the id that toJSON() only emits when the user has read permission
                function collect(container) {
                    if (container.id && container.path) {
                        site.push(container.path);
                    }
                    Ext4.each(container.children, collect);
                }
                collect(json);

                this.containerScopes = {'/' : site};

                if (LABKEY.project) {
                    let projectPath = LABKEY.project.path.replace(/\/+$/, '');
                    this.containerScopes[LABKEY.project.path] = Ext4.Array.filter(site, function(path) {
                        return path === projectPath || path.indexOf(projectPath + '/') === 0;
                    });
                }

                success.call(scope || this, this.containerScopes);
            },
            failure : function(json, resp, options) {
                if (Ext4.isFunction(failure)) {
                    failure.call(scope || this, json, resp, options);
                }
            }
        });
    },

    // one entry per container whose analysis failed, in the order the responses came back
    getErrors : function() {
        return this.errors || [];
    },

    // what the cached dependency graph covers: a null containerPath means only the folder the analysis was run from
    getAnalysisScope : function() {
        return {containerPath: this.analyzedContainerPath, folderCount: this.totalContainers};
    },

    // the folders loadContainerCounts() resolved for an analysis scope, or undefined if it hasn't run
    getScopeContainers : function(containerPath) {
        return this.containerScopes ? this.containerScopes[containerPath] : undefined;
    },

    // abort an analysis in progress; responses that are already in flight are ignored rather than accumulated
    cancel : function() {
        this.cancelled = true;
        this.containers = [];
        this.activeContainers = {};
        this.activeCount = 0;

        let requests = this.activeRequests || {};
        this.activeRequests = {};
        Ext4.Object.each(requests, function(container, request) {
            if (request && Ext4.isFunction(request.abort)) {
                request.abort();
            }
        });
    },

    // return the current progress for this loader
    getProgress : function() {
        return {
            currentContainer: this.currentContainer,
            // totalContainers isn't known until the getContainers() call returns
            progress: this.totalContainers ? (this.finishedCount / this.totalContainers) : 0
        };
    }
});