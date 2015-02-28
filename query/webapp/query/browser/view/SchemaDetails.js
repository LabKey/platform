Ext4.define('LABKEY.query.browser.view.SchemaDetails', {

    extend: 'LABKEY.query.browser.view.Base',

    constructor : function(config) {
        if (!config.schemaBrowser) {
            throw this.$className + ' configuration error. Requires \'schemaBrowser\'';
        }
        this.callParent([config]);
        this.addEvents('queryclick');
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

        this.on('schemaclick', this.onSchemaClick, this);
        this.cache.getQueries(this.schemaName, this.onQueries, this);
    },

    onSchemaClick : function(schemaName) {
        this.schemaBrowser.selectSchema(schemaName);
        this.schemaBrowser.showPanel(this.schemaBrowser.sspPrefix + schemaName);
    },

    onQueries : function(queriesMap) {
        this.queries = queriesMap;
        this.removeAll();

        var items = [],
            schema = this.cache.getSchema(this.schemaName),
            links = this.formatSchemaLinks(schema),
            childSchemaNames = [],
            userDefined = [],
            builtIn = [],
            rows = [];

        if (links) {
            items.push(links);
        }

        items.push({
            xtype: 'box',
            autoEl: {
                tag: 'div',
                cls: 'lk-qd-name',
                html: Ext4.htmlEncode(this.schemaName.toDisplayString() + ' Schema')
            }
        });
        items.push({
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

        Ext4.iterate(queriesMap, function(name, query) {
            query.name = name;
            if (query.isUserDefined) {
                userDefined.push(query);
            }
            else {
                builtIn.push(query);
            }
        });

        if (userDefined.length > 0) {
            userDefined.sort(function(a, b) { return a.name.localeCompare(b.name); });
        }
        if (builtIn.length > 0) {
            builtIn.sort(function(a, b) { return a.name.localeCompare(b.name); });
        }

        if (childSchemaNames.length > 0) {
            rows.push(this.formatSchemaList(childSchemaNames, schema.schemas, 'Child Schemas'));
        }
        if (userDefined.length > 0) {
            rows.push(this.formatQueryList(userDefined, 'User-Defined Queries'));
        }
        if (builtIn.length > 0) {
            rows.push(this.formatQueryList(builtIn, 'Built-In Queries and Tables'));
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
                        var nameLinks = Ext4.DomQuery.select('tbody tr td span', box.getEl().id);
                        if (!Ext4.isEmpty(nameLinks)) {
                            for (var i = 0; i < nameLinks.length; i++) {
                                Ext4.get(nameLinks[i]).on('click', function(evt, t) {
                                    this.fireEvent('queryclick', this.schemaName, Ext4.htmlDecode(t.innerHTML));
                                }, this);
                            }
                        }
                    },
                    scope: this,
                    single: true
                }
            }
        });

        this.add(items);
    },

    formatSchemaLinks : function(schema) {
        if (!schema || !schema.menu || !schema.menu.items || 0 == schema.menu.items.length) {
            return;
        }

        var children = [];
        for (var i=0; i < schema.menu.items.length; i++) {
            children.push(LABKEY.Utils.textLink(schema.menu.items[i]));
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

    formatSchemaList : function (sortedNames, schemas, title) {
        var rows = []; // make an object more consuable by XTemplate
        Ext4.each(sortedNames, function(name) {
            rows.push({
                name: name,
                schema: schemas[name]
            });
        });

        var table = Ext4.create('Ext.Component', {
            tpl: new Ext4.XTemplate(
                    '<table class="lk-qd-coltable">',
                        '<thead>',
                            '<tpl if="this.hasTitle(title)">',
                            '<tr>',
                                '<td colspan="3" class="lk-qd-collist-title">{title:htmlEncode}</td>',
                            '</tr>',
                            '</tpl>',
                            '<tr>',
                                '<th>Name</th><th>Attributes</th><th>Description</th>',
                            '</tr>',
                        '</thead>',
                        '<tbody>',
                            '<tpl for="schemas">',
                            '<tr>',
                                '<td><span class="labkey-link">{name:htmlEncode}</span></td>',
                                '<td>{schema.hidden:this.schemaHidden}</td>',
                                '<td>{schema.description:this.description}</td>',
                            '<tr>',
                            '</tpl>',
                        '</tbody>',
                    '</table>',
                    {
                        hasTitle : function(title) {
                            return !Ext4.isEmpty(title);
                        },
                        schemaHidden : function(hidden) {
                            return Ext4.htmlEncode((hidden === true ? 'Hidden': ''));
                        },
                        description : function(description) {
                            return Ext4.htmlEncode((!Ext4.isEmpty(description) ? description : ''));
                        }
                    }
            ),
            data: {
                title: title,
                schemas: rows
            }
        });
        //// bind links
        //var links = Ext4.DomQuery.select('span.labkey-link', table);
        //if (links.length > 0) {
        //    Ext4.each(links, function(link) {
        //        var linkEl = Ext4.get(link);
        //        linkEl.on('click', function(evt, t) { this.onSchemaLinkClick(schemas, evt, t); }, this);
        //    }, this);
        //}
    },

    formatQueryList : function(queries, title) {
        var rows = [{
            tag: 'tr',
            children: [{
                tag: 'td',
                colspan: 3,
                cls: 'lk-qd-collist-title',
                html: title
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
                                tag: 'span',
                                cls: 'labkey-link',
                                html: Ext4.htmlEncode(query.name)
                            },
                            {
                                tag: 'span',
                                html: Ext4.htmlEncode((query.name.toLowerCase() != query.title.toLowerCase() ? ' (' + query.title + ')' : ''))
                            }
                        ]
                    },
                    {
                        tag: 'td',
                        html: attributes.join(", ")
                    },
                    {
                        tag: 'td',
                        html: Ext4.htmlEncode(query.description)
                    }
                ]
            });
        }

        return rows;
    }
});