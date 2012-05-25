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

            var dataGrid = Ext4.create('Ext.Component', {
                autoScroll  : true,
                cls         : 'iScroll',
                ui          : 'custom',
                listeners   : {
                    render : {fn : function(cmp){wp.render(cmp.getId());}, scope : this}
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
    }
});
