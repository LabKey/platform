/*
 * Copyright (c) 2011-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

LABKEY.requiresExt4Sandbox(true);

Ext4.define('LABKEY.ext4.GenericChartPanel', {

    extend : 'Ext.tab.Panel',

    constructor : function(config) {

        Ext4.QuickTips.init();

        Ext4.applyIf(config, {
            minWidth: 625,
            frame   : false,
            border  : false
        });

        this.callParent([config]);
    },

    initComponent : function() {

        this.items = [];

        this.items.push(this.getViewPanel());
        this.items.push(this.getDataPanel());

        this.callParent();

        this.on('tabchange', this.onTabChange, this);
        this.on('render', this.ensureQuerySettings, this);
    },

    getViewPanel : function() {

        if (!this.viewPanel)
        {
            this.viewPanel = Ext4.create('Ext.panel.Panel', {
                flex        : 1,
                layout      : 'fit',
                title       : 'View',
                bodyStyle   : 'overflow-y: auto;',
                cls         : 'iScroll',
                ui          : 'custom'
            });
        }
        return this.viewPanel;
    },

    getDataPanel : function() {

        if (!this.dataPanel)
        {
            var dataGrid = Ext4.create('Ext.Component', {
                autoScroll  : true,
                cls         : 'iScroll',
                ui          : 'custom',
                listeners   : {
                    render : {fn : function(cmp){this.renderDataGrid(cmp.getId());}, scope : this}
                }
            });

            this.dataPanel = Ext4.create('Ext.panel.Panel', {
                flex        : 1,
                layout      : 'fit',
                title       : 'Data',
                padding     : '10',
                border      : false,
                frame       : false,
                cls         : 'iScroll',
                ui          : 'custom',
                items       : dataGrid
            });
        }
        return this.dataPanel;
    },

    renderDataGrid : function(renderTo) {
        var urlParams = LABKEY.ActionURL.getParameters(this.baseUrl);
        var filterUrl = urlParams['filterUrl'];

        var userFilters = LABKEY.Filter.getFiltersFromUrl(filterUrl, this.dataRegionName);
        var userSort = LABKEY.Filter.getSortFromUrl(filterUrl, this.dataRegionName);

        var wp = new LABKEY.QueryWebPart({
            schemaName  : this.schemaName,
            queryName   : this.queryName,
            frame       : 'none',
            showBorders : false,
            removeableFilters       : userFilters,
            removeableSort          : userSort,
            buttonBarPosition       : 'none',
            showSurroundingBorder   : false,
            showDetailsColumn       : false,
            showUpdateColumn        : false,
            showRecordSelectors     : false
        });

        // save the dataregion
        this.panelDataRegionName = wp.dataRegionName;

        wp.render(renderTo);
    },

    onTabChange : function(cmp, newCard, oldCard) {

        if (!this.dataPanel.isVisible())
        {
            //var config = this.getQueryConfig();
            //LABKEY.Query.selectRows(config);
        }
    },

    // Returns a configuration based on the baseUrl plus any filters applied on the dataregion panel
    // the configuration can be used to make a selectRows request
    getQueryConfig : function() {

        var dataRegion = LABKEY.DataRegions[this.panelDataRegionName];
        var config = {
            schemaName  : this.schemaName,
            queryName   : this.queryName
        };

        if (dataRegion)
        {
            config['filterArray'] = dataRegion.getUserFilterArray();
        }

        return config;
    },

    ensureQuerySettings : function() {

        if (this.schemaName == 'null' || !this.queryName == 'null')
        {
            var formItems = [];
            var queryStore = this.initializeQueryStore();
            var queryId = Ext.id();

            this.schemaName = 'study';

            formItems.push({
                xtype       : 'combo',
                fieldLabel  : 'Schema',
                name        : 'schema',
                store       : this.initializeSchemaStore(),
                editable    : false,
                value       : this.schemaName,
                queryMode      : 'local',
                displayField   : 'name',
                valueField     : 'name',
                emptyText      : 'None',
                listeners   : {
                    change : {fn : function(cmp, newValue) {
                        this.schemaName = newValue;
                        this.queryName = null;
                        var proxy = queryStore.getProxy();
                        if (proxy)
                            queryStore.load({params : {schemaName : newValue}});

                        var queryCombo = Ext4.getCmp(queryId);
                        if (queryCombo)
                            queryCombo.clearValue();
                    }, scope : this}
                }
            });

            formItems.push({
                xtype       : 'combo',
                id          : queryId,
                fieldLabel  : 'Query',
                name        : 'query',
                store       : queryStore,
                editable    : false,
                allowBlank  : false,
                displayField   : 'name',
                triggerAction  : 'all',
                typeAhead      : true,
                valueField     : 'name',
                emptyText      : 'None',
                listeners   : {
                    change : {fn : function(cmp, newValue) {this.queryName = newValue;}, scope : this}
                }
            });

            var formPanel = Ext4.create('Ext.form.Panel', {
                border  : false,
                frame   : false,
                buttonAlign : 'left',
                fieldDefaults  : {
                    labelWidth : 100,
                    width      : 375,
                    style      : 'padding: 4px 0',
                    labelSeparator : ''
                },
                items   : formItems,
                buttons     : [{
                    text : 'Save',
                    formBind: true,
                    handler : function(btn) {
                        var form = btn.up('form').getForm();
                        if (form.isValid())
                        {
                            dialog.hide();

                            // fire an event or call some method to render data
                        }
                    },
                    scope   : this
                }]
            });

            var dialog = Ext4.create('Ext.window.Window', {
                width  : 450,
                height : 200,
                layout : 'fit',
                border : false,
                frame  : false,
                closable : false,
                draggable : false,
                modal  : true,
                title  : 'Select Chart Query',
                bodyPadding : 20,
                items : formPanel,
                scope : this
            });

            dialog.show();
        }
    },

    /**
     * Create the store for the schema
     */
    initializeSchemaStore : function() {

        Ext4.define('LABKEY.data.Schema', {
            extend : 'Ext.data.Model',
            fields : [
                {name : 'name'},
                {name : 'description'}
            ]
        });

        // manually define for now, we could query at some point
        var schemaStore = Ext4.create('Ext.data.Store', {
            model : 'LABKEY.data.Schema',
            data  : [
                {name : 'study'},
                {name : 'assay'},
                {name : 'lists'}
            ]
        });

        return schemaStore;
    },

    /**
     * Create the store for the schema
     */
    initializeQueryStore : function() {

        Ext4.define('LABKEY.data.Queries', {
            extend : 'Ext.data.Model',
            fields : [
                {name : 'name'},
                {name : 'description'},
                {name : 'isUserDefined', type : 'boolean'}
            ]
        });

        var config = {
            model   : 'LABKEY.data.Queries',
            autoLoad: false,
            pageSize: 10000,
            proxy   : {
                type   : 'ajax',
                url : LABKEY.ActionURL.buildURL('query', 'getQueries'),
                extraParams : {
                    schemaName  : 'study'
                },
                reader : {
                    type : 'json',
                    root : 'queries'
                }
            }
        };

        return Ext4.create('Ext.data.Store', config);
    }
});
