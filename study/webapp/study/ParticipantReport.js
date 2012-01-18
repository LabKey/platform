/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.requiresExt4Sandbox(true);

Ext4.define('LABKEY.ext4.ParticipantReport', {

    extend : 'Ext.panel.Panel',

    constructor : function(config) {

        Ext4.QuickTips.init();

        Ext4.applyIf(config, {
            layout : 'border',
            frame  : false,
            border : true
        });

        this.callParent([config]);
    },

    initComponent : function() {

        this.items = [];
        this.centerPanel = Ext4.create('Ext.panel.Panel', {
            border   : false,
            frame    : false,
            layout   : 'fit',
            region   : 'center',
            items    : [{html:'Preview'}]
        });
        this.westPanel = Ext4.create('Ext.panel.Panel', {
            bodyPadding : 20,
            border   : false,
            frame    : false,
            collapsible : true,
            region   : 'west'
        });
        this.northPanel = Ext4.create('Ext.panel.Panel', {
            border   : false,
            frame    : false,
            region   : 'north'
        });

        this.items.push(this.centerPanel);
        this.items.push(this.northPanel);
        this.items.push(this.westPanel);

        // offer a choice of non-demographic datasets without an external key field
        LABKEY.Query.selectRows({
            requiredVersion : 12.1,
            schemaName      : 'study',
            queryName       : 'Datasets',
            scope           : this,
            filterArray : [
                LABKEY.Filter.create('DemographicData', false),
                LABKEY.Filter.create('KeyPropertyName', false, LABKEY.Filter.Types.ISBLANK)
            ],
            success : function(rs)
            {
                this.westPanel.add(this.initWestPanel(rs));
            }
        });

        this.callParent([arguments]);
    },

    initWestPanel : function(queryResults) {

        var formItems = [];

        var config = {
            autoLoad: true,
            data: queryResults.rows,
            fields : [
                {name : 'DataSetId',       type : 'int', mapping : 'DataSetId.value'},
                {name : 'Name',                          mapping : 'Name.value'},
                {name : 'DemographicData', type : 'boolean'},
                {name : 'KeyPropertyName'}
            ]
        };
        var store = Ext4.create('Ext.data.Store', config);

        formItems.push({
            xtype      : 'textfield',
            fieldLabel : 'Name'
        });

        formItems.push({
            xtype       : 'combo',
            fieldLabel  : 'Dataset',
            name        : 'dataset',
            store       : store,
            editable    : false,
            queryMode      : 'local',
            displayField   : 'Name',
            valueField     : 'Name',
            triggerAction  : 'all',
            emptyText      : 'Unknown',
            listeners      : {change : this.onChangeQuery, scope : this}
        });

        var panel = Ext4.create('Ext.form.Panel', {
            border : false, frame : false,
            items  : formItems
        });

        return panel;
    },

    onChangeQuery : function(cmp, newVal, oldVal) {
        LABKEY.initializeViewDesigner(function() {

            if (this.customizeView)
            {
                this.customizeView.hide();
                this.customizeView.destroy();
            }

            LABKEY.Query.getQueryDetails(
            {
                schemaName: 'study',
                queryName: newVal,
                scope: this,
                success: function (json, response, options) {

                    this.customizeView = new LABKEY.DataRegion.ViewDesigner({
                        renderTo: this.customizeViewId,
                        activeGroup: 1,
                        dataRegion: null,
                        schemaName: 'study',
                        queryName: newVal,
                        includeRevert: false,
                        includeViewGrid: false,
                        query: json
                    });

                    // Need to trigger a relayout that makes the split pane visible
                    this.customizeView.setWidth(this.customizeView.getWidth());
                }
            }, this);
        }, this);
    }
});
