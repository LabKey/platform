/*
 * Copyright (c) 2015-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.query.browser.Tree', {
    extend: 'Ext.tree.Panel',

    alias: 'widget.qbrowser-tree',

    border: false,

    rootVisible: false,

    constructor : function(config) {

        this.showHidden = LABKEY.ActionURL.getParameters().showHidden === 'true';
        this.showUserDefined = LABKEY.ActionURL.getParameters().showUserDefined !== 'false';
        this.showModuleDefined = LABKEY.ActionURL.getParameters().showModuleDefined !== 'false';
        this.showBuiltInTables = LABKEY.ActionURL.getParameters().showBuiltInTables !== 'false';

        if (!Ext4.ModelManager.isRegistered('SchemaBrowser.Queries')) {
            Ext4.define('SchemaBrowser.Queries', {
                extend: 'Ext.data.Model',
                proxy: {
                    type: 'ajax',
                    url: LABKEY.ActionURL.buildURL('query', 'getSchemaQueryTree.api', null, {
                        showHidden : this.showHidden,
                        showUserDefined: this.showUserDefined,
                        showModuleDefined: this.showModuleDefined,
                        showBuiltInTables: this.showBuiltInTables
                    }),
                    // don't use the "_dc" defeat cache parameter
                    noCache: false,
                    listeners: {
                        exception: function(proxy, response) {
                            if (!this._unloading) {
                                LABKEY.Utils.displayAjaxErrorResponse(response);
                            }
                        }
                    }
                },
                fields : [
                    {name: 'description'},
                    {name: 'hidden', type: 'boolean', defaultValue: false},
                    {name: 'name'},
                    {name: 'qtip', convert: function(_, rec) { return Ext4.htmlEncode(rec.raw.description); } },
                    {name: 'schemaName'},
                    {name: 'queryName'},
                    {name: 'queryLabel'},
                    {name: 'text'},
                    {name: 'icon', convert: function(_, rec) {
                        if (rec.raw.table === undefined) { return undefined; }
                        return LABKEY.ActionURL.getContextPath() + '/reports/' + (rec.raw.table ? 'grid.gif' : (rec.raw.fromModule ? 'grid-sql.png' : 'grid-sql-editable.png'));
                    }},
                    {name: 'leaf', type: 'boolean', defaultValue: false}
                ]
            });
        }

        this.callParent([config]);
        this.addEvents('schemasloaded');
    },

    initComponent : function() {

        this.store = Ext4.create('Ext.data.TreeStore', {
            model : 'SchemaBrowser.Queries',
            root: {
                name: 'root',
                expanded: true,
                expandable: false,
                draggable: false
            },
            listeners: {
                load: function(store, node) {
                    // NOTE: there should be a better way to do this dance because I suspect it's time intensive.
                    this.fireEvent('schemasloaded',store, node);
                },
                beforeload: function(store, operation) {
                    var params = operation.params;

                    if (!params.node) {
                        params.node = operation.node.internalId;
                        params.schemaName = operation.node.data.schemaName;
                    }
                },
                scope: this
            }
        });

        this.fbar = [
            {
                xtype: 'panel',
                border: false,
                width: '100%',
                items: [{
                    xtype: 'checkbox',
                    boxLabel: '<span style="font-size: 9.5px;">Show Hidden Schemas and Queries</span>',
                    checked: this.showHidden,
                    handler: function(cb, value) { this.updateFilter(cb, value, 'showHidden') },
                    scope: this
                }, {
                    xtype: 'checkbox',
                    boxLabel: '<img src="' + LABKEY.ActionURL.getContextPath() + '/reports/grid-sql-editable.png" height="16" width="16" alt="User-defined queries"/>&nbsp;<span style="font-size: 9.5px;">Show User-Defined Queries</span>',
                    checked: this.showUserDefined,
                    handler: function(cb, value) { this.updateFilter(cb, value, 'showUserDefined') },
                    scope: this
                }, {
                    xtype: 'checkbox',
                    boxLabel: '<img src="' + LABKEY.ActionURL.getContextPath() + '/reports/grid-sql.png" height="16" width="16" alt="Module-defined queries"/>&nbsp;<span style="font-size: 9.5px;">Show Module-Defined Queries</span>',
                    checked: this.showModuleDefined,
                    handler: function(cb, value) { this.updateFilter(cb, value, 'showModuleDefined') },
                    scope: this
                }, {
                    xtype: 'checkbox',
                    boxLabel: '<img src="' + LABKEY.ActionURL.getContextPath() + '/reports/grid.gif" height="16" width="16" alt="Built-in tables"/>&nbsp;<span style="font-size: 9.5px;">Show Built-In Tables</span>',
                    checked: this.showBuiltInTables,
                    handler: function(cb, value) { this.updateFilter(cb, value, 'showBuiltInTables') },
                    scope: this
                }]
            }];

        Ext4.EventManager.on(window, 'beforeunload', function() {
            this._unloading = true;
        }, this);

        this.callParent();

        // Show hidden child nodes when expanding if 'Show Hidden Schemas and Queries' is checked.
        this.on('beforeappend', function(tree, parent, node) {
            if (this.showHidden) {
                node.hidden = false;
            }
        }, this);

        this.on('afterrender', function() {
            LABKEY.Utils.signalWebDriverTest('queryTreeRendered');
        }, this);

        this.on('selectionchange', function() {
            LABKEY.Utils.signalWebDriverTest('queryTreeSelectionChange');
        }, this);
    },

    updateFilter : function(cb, value, propertyName) {
        this[propertyName] = value;

        // Until TreeStore filtering is supported, push this solution to the server
        var params = LABKEY.ActionURL.getParameters();
        params[propertyName] = value;

        var url = LABKEY.ActionURL.buildURL('query', 'begin', null, params);
        window.location.href = url + window.location.hash;
    }
});