/*
 * Copyright (c) 2015-2018 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.query.browser.view.SchemaDetails', {

    extend: 'LABKEY.query.browser.view.Base',

    constructor : function(config) {
        if (!config.schemaBrowser) {
            throw this.$className + ' configuration error. Requires \'schemaBrowser\'';
        }
        this.callParent([config]);
        this.addEvents('queryclick');
        this.addEvents('schemaclick');
    },

    initComponent : function() {
        this.cache = LABKEY.query.browser.cache.Query;

        this.items = [{
            xtype: 'box',
            itemId: 'loader',
            autoEl: {
                tag: 'div',
                cls: 'lk-qd-loading',
                html: 'loading...'
            }
        }];

        this.callParent(arguments);

        // listener for event fired by the schema tree whenever it finishes loading (either root info or schema info/children)
        if (this.selectedNode && this.selectedNode.hasChildNodes())
        {
            this.onQueries(this.selectedNode.childNodes);
        }
        else
        {
            this.schemaTree.on('schemasloaded', this.loadQueryCategories, this);
        }
    },

    loadQueryCategories: function (store, node)
    {
        if(this.schemaName.getName() === node.get("name"))
        {
            this.onQueries(node.childNodes);
            this.schemaTree.un('schemasloaded', this.loadQueryCategories, this);
        }
     },

    onQueries : function(schemaNodeChildren) {
        this.removeAll();

        this.cache.getSchema(this.schemaName, function(schema) {

            var items = [],
                links = this.formatSchemaLinks(schema),
                childSchemaNames = [],
                queries = [],
                rows = [];

            if (links) {
                items.push(links);
            }

            items.push({
                xtype: 'box',
                autoEl: {
                    tag: 'h2',
                    // cls: 'lk-qd-name',
                    html: Ext4.htmlEncode(this.schemaName.toDisplayString() + ' Schema')
                }
            },{
                xtype: 'box',
                autoEl: {
                    tag: 'div',
                    cls: 'lk-qd-description',
                    html: Ext4.htmlEncode(schema.description)
                }
            });

            Ext4.iterate(schema.schemas, function(childSchemaName) {
                childSchemaNames.push(childSchemaName);
            });
            if (!Ext4.isEmpty(childSchemaNames)) {
                childSchemaNames.sort(function(a, b) { return a.toLowerCase().localeCompare(b.toLowerCase()); });
            }

            // Each schemaNode has grouped the queries (built-in vs user-defined). Iterate through each group
            // and add the query node's data to the appropriate category.
            Ext4.each(schemaNodeChildren, function(schemaNodeChild)
            {
                if (schemaNodeChild.get('queryName'))
                    queries.push(Ext4.clone(schemaNodeChild.data));
            });

            if (childSchemaNames.length > 0) {
                rows.push(this.formatSchemaList(childSchemaNames, schema.schemas));
            }

            if (queries.length > 0) {
                queries.sort(function(a, b) { return a.queryName.localeCompare(b.queryName); });
                rows.push(this.formatQueryList(queries));
            }

            if (rows.length === 0) {
                items.push({
                    xtype: 'box',
                    autoEl: {
                        tag: 'div',
                        html: 'No queries, tables, or child schemas to show'
                    }
                });
            }

            items.push({
                xtype: 'box',
                autoEl: {
                    tag: 'table',
                    cls: 'lk-qd-coltable',
                    children: [{
                        tag: 'tbody',
                        children: rows
                    }]
                },
                listeners: {
                    afterrender: {
                        fn: function(box) {
                            // bind links
                            var queryLinks = Ext4.DomQuery.select('tbody tr td .schema-browser-query', box.getEl().id);
                            Ext4.each(queryLinks, function(link) {
                                Ext4.get(link).on('click', function(evt, t) {
                                    this.fireEvent('queryclick', this.schemaName, Ext4.htmlDecode(t.innerHTML));
                                }, this);
                            }, this);

                            // bind links
                            var childSchemaLinks = Ext4.DomQuery.select('tbody tr td .schema-browser-child-schema', box.getEl().id);
                            Ext4.each(childSchemaLinks, function(link) {
                                Ext4.get(link).on('click', function(evt, t) {
                                    this.fireEvent('schemaclick', this.schemaName, Ext4.htmlDecode(t.innerHTML));
                                }, this);
                            }, this);
                        },
                        scope: this,
                        single: true
                    }
                }
            });

            this.add(items);

        }, this);
    },

    formatSchemaLinks : function(schema) {
        if (!schema) {
            return;
        }

        var children = [];

        if (schema.menu && schema.menu.items) {
            for (var i=0; i < schema.menu.items.length; i++) {
                children.push(LABKEY.Utils.textLink(schema.menu.items[i]));
            }
        }

        if (LABKEY.devMode || LABKEY.Security.currentUser.isDeveloper) {
            children.push(LABKEY.Utils.textLink({
                href: LABKEY.ActionURL.buildURL('query', 'rawSchemaMetaData', undefined, {schemaName: schema.schemaName}),
                text: 'view raw schema metadata'
            }));
        }

        return {
            xtype: 'box',
            autoEl: {
                tag: 'div',
                cls: 'lk-qd-links',
                children: children
            }
        };
    },

    formatSchemaList : function (sortedNames, schemas) {
        var rows = [{
            tag: 'tr',
            children: [{
                tag: 'td',
                colspan: 3,
                cls: 'lk-qd-collist-title',
                html: 'Child Schemas'
            }]
        },{
            tag: 'tr',
            children: [{
                tag: 'td',
                cls: 'lk-qd-colheader',
                html: 'Name'
            },{
                tag: 'td',
                cls: 'lk-qd-colheader',
                html: 'Attributes'
            },{
                tag: 'td',
                cls: 'lk-qd-colheader',
                html: 'Description'
            }]
        }];

        for (var idx = 0; idx < sortedNames.length; ++idx)
        {
            schema = schemas[sortedNames[idx]];
            var attributes = [];

            if (schema.hidden) {
                if (!this.schemaTree.showHidden) {
                    continue;
                }
                attributes.push("Hidden");
            }

            rows.push({
                tag: 'tr',
                children: [
                    {
                        tag: 'td',
                        children: [
                            {
                                tag: 'span',
                                cls: 'labkey-link schema-browser-child-schema',
                                html: Ext4.htmlEncode(schema.schemaName),
                            }
                        ]
                    },
                    {
                        tag: 'td',
                        html: attributes.join(", ")
                    },
                    {
                        tag: 'td',
                        children: [
                            {
                                tag: 'span',
                                html: Ext4.htmlEncode(schema.description)
                            }
                        ]
                    }
                ]
            });
        }

        rows.push({
            tag: 'tr',
            children: [{
                tag: 'td',
                html: '<br/>'
            }]
        })

        return rows;
    },

    formatQueryList : function(queries) {
        var rows = [{
            tag: 'tr',
            children: [{
                tag: 'td',
                colspan: 4,
                cls: 'lk-qd-collist-title',
                html: 'Queries and Tables'
            }]
        },{
            tag: 'tr',
            children: [{
                tag: 'td',
                cls: 'lk-qd-colheader',
                html: ''
            },{
                tag: 'td',
                cls: 'lk-qd-colheader',
                html: 'Name'
            },{
                tag: 'td',
                cls: 'lk-qd-colheader',
                html: 'Attributes'
            },{
                tag: 'td',
                cls: 'lk-qd-colheader',
                html: 'Description'
            }]
        }];

        var query;
        for (var idx = 0; idx < queries.length; ++idx)
        {
            query = queries[idx];
            var attributes = [];
            if (query.hidden)
                attributes.push("Hidden");
            if (query.inherit)
                attributes.push("Inherit");
            if (query.snapshot)
                attributes.push("Snapshot");

            rows.push({
                tag: 'tr',
                children: [
                    {
                        tag: 'td',
                        children: [
                            {
                                tag: 'img',
                                src: query.icon
                            }
                        ]
                    },
                    {
                        tag: 'td',
                        children: [
                            {
                                tag: 'span',
                                cls: 'labkey-link schema-browser-query',
                                html: Ext4.htmlEncode(query.queryName)
                            }
                        ]
                    },
                    {
                        tag: 'td',
                        html: attributes.join(", ")
                    },
                    {
                        tag: 'td',
                        children: [
                            {
                                tag: 'span',
                                html: query.queryName.toLowerCase() != query.queryLabel.toLowerCase() ? ('<em>Query Label: ' + Ext4.htmlEncode(query.queryLabel) + '</em><br/>') : ''
                            },
                            {
                                tag: 'span',
                                html: Ext4.htmlEncode(query.description)
                            }
                        ]
                    }
                ]
            });
        }

        return rows;
    }
});