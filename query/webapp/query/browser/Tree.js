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

        if (!Ext4.ModelManager.isRegistered('SchemaBrowser.Queries')) {
            Ext4.define('SchemaBrowser.Queries', {
                extend: 'Ext.data.Model',
                proxy: {
                    type: 'ajax',
                    url: LABKEY.ActionURL.buildURL('query', 'getSchemaQueryTree.api', null, { showHidden : this.showHidden }),
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

        this.fbar = [{
            xtype: 'checkbox',
            boxLabel: '<span style="font-size: 9.5px;">Show Hidden Schemas and Queries</span>',
            checked: this.showHidden,
            handler: this.onShowHidden,
            scope: this
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

    onShowHidden : function(cb, showHidden) {
        this.showHidden = showHidden;

        // Until TreeStore filtering is supported, push this solution to the server
        var params = LABKEY.ActionURL.getParameters();
        params.showHidden = showHidden;

        var url = LABKEY.ActionURL.buildURL('query', 'begin', null, params);
        if (showHidden)
            window.location.href = url + window.location.hash;
        else
            window.location.href = url;
    }
});