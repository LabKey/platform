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