/*
 * Copyright (c) 2015-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.query.browser.view.Home', {

    extend: 'LABKEY.query.browser.view.Base',

    alias: 'widget.qbrowser-view-home',

    title: 'Home',

    constructor : function(config) {
        this.callParent([config]);
        this.addEvents('schemaclick');
        this.showHidden = LABKEY.ActionURL.getParameters().showHidden === 'true';
    },

    initComponent : function() {

        this.queryCache = LABKEY.query.browser.cache.Query;

        this.items = [{
            itemId: 'loader',
            xtype: 'box',
            autoEl: {
                tag: 'div',
                cls: 'lk-qd-loading',
                html: 'loading...'
            }
        },{
            xtype: 'box',
            itemId: 'instructions',
            hidden: true,
            autoEl: {
                tag: 'div',
                cls: 'lk-sb-instructions',
                html: 'Use the tree on the left to select a query, or select a schema below to expand that schema in the tree.'
            }
        },{
            xtype: 'box',
            itemId: 'summary',
            hidden: true,
            tpl: new Ext4.XTemplate(
                '<table class="lk-qd-coltable">',
                    '<thead>',
                        //'<tpl if="this.hasTitle(title)">',
                        //'<tr>',
                        //    '<td colspan="3" class="lk-qd-collist-title">{title:htmlEncode}</td>',
                        //'</tr>',
                        //'</tpl>',
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
                    //hasTitle : function(title) {
                    //    return !Ext4.isEmpty(title);
                    //},
                    schemaHidden : function(hidden) {
                        return Ext4.htmlEncode((hidden === true ? 'Hidden': ''));
                    },
                    description : function(description) {
                        return Ext4.htmlEncode((!Ext4.isEmpty(description) ? description : ''));
                    }
                }
            ),
            data: {}
        }];

        this.callParent();

        this.on('afterrender', function() {
            this.queryCache.getSchemas(this.setSchemas, this);
        }, this);
    },

    setSchemas : function(schemaTree) {
        // clear loading message
        this.getComponent('loader').hide();
        this.getComponent('instructions').show();

        // create a sorted list of schema names
        var sortedNames = [],
            schemas = {};

        Ext4.iterate(schemaTree.schemas, function(schemaName, o) {
            if (!o.hidden || this.showHidden){
                sortedNames.push(schemaName);
                schemas[schemaName] = this.queryCache.lookupSchema(schemaTree, schemaName);
            }
        }, this);
        sortedNames.sort(function(a, b) { return a.toLowerCase().localeCompare(b.toLowerCase()); }); // 10572

        // IE won't let you create the table rows incrementally
        // so build the rows as a data structure first and then
        // do one createChild() for the whole table
        var rows = [];
        Ext4.each(sortedNames, function(name) {
            rows.push({
                name: name,
                schema: schemas[name]
            });
        });

        var summary = this.getComponent('summary');
        summary.update({ schemas: rows });
        summary.show();

        // bind links
        var links = Ext4.DomQuery.select('span.labkey-link', summary.getEl().id);
        if (links.length > 0) {
            Ext4.each(links, function(link) {
                var linkEl = Ext4.get(link);
                linkEl.on('click', function(evt, t) { this.onSchemaLinkClick(schemas, evt, t); }, this);
            }, this);
        }
    }
});
