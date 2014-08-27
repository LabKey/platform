/*
 * Copyright (c) 2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext.define('LABKEY.app.panel.MeasurePicker', {

    extend : 'Ext.panel.Panel',

    constructor : function(config) {
        this.initConfig = config; // passthrough the initial config for the measure picker to the core SplitPanels component
        this.callParent([{}]);
    },

    initComponent : function() {
        this.items = [this.getMeasurePicker()];

        this.callParent();
    },

    getMeasurePicker : function() {
        if (!this.picker)
        {
            this.picker = Ext.create('Connector.panel.MeasuresView', this.initConfig);
        }

        return this.picker;
    }
});

Ext.define('Connector.panel.MeasuresView', {

    extend: 'LABKEY.ext4.MeasuresDataView.SplitPanels',

    includeTimpointMeasures: false,

    allColumns: true,

    showHidden: false,

    multiSelect: true,

    flex: 1,

    sourceCls: undefined,

    constructor : function(config) {
        Ext.apply(config, {
            sourceGroupHeader : 'Datasets',
            measuresAllHeader : 'All columns for this assay',
            bubbleEvents: ['beforeMeasuresStoreLoad', 'measuresStoreLoaded', 'measureChanged']
        });

        this.callParent([config]);
    },

    initComponent : function() {
        this.callParent();

        // allows for a class to be added to the source selection panel
        if (Ext.isString(this.sourceCls)) {
            this.getSourcesView().on('afterrender', function(p) { p.addCls(this.sourceCls); }, this, {single: true});
        }
    },

    getAdditionalMeasuresArray : function() {
        var timePointQueryDescription = 'Creates a categorical x axis, unlike the other time axes that are ordinal.';

        return !this.includeTimpointMeasures ? [] :[{
            sortOrder: -4,
            schemaName: null,
            queryName: null,
            queryLabel: 'Time points',
            queryDescription: timePointQueryDescription,
            isKeyVariable: true,
            name: 'SubjectVisit/Visit/ProtocolDay',
            alias: 'Days',
            label: 'Study days',
            type: 'INTEGER',
            description: timePointQueryDescription + ' Each visit with data for the y axis is labeled separately with its study day.',
            variableType: 'TIME'
        },{
            sortOrder: -3,
            schemaName: null,
            queryName: null,
            queryLabel: 'Time points',
            name: 'SubjectVisit/Visit/ProtocolDay',
            alias: 'Weeks',
            label: 'Study weeks',
            type: 'DOUBLE',
            description: timePointQueryDescription + ' Each visit with data for the y axis is labeled separately with its study week.',
            variableType: 'TIME'
        },{
            sortOrder: -2,
            schemaName: null,
            queryName: null,
            queryLabel: 'Time points',
            name: 'SubjectVisit/Visit/ProtocolDay',
            alias: 'Months',
            label: 'Study months',
            type: 'DOUBLE',
            description: timePointQueryDescription + ' Each visit with data for the y axis is labeled separately with its study month.',
            variableType: 'TIME'
        },{
            sortOrder: -1,
            schemaName: 'study',
            queryName: 'SubjectGroupMap',
            queryLabel: 'User groups',
            queryDescription: 'Creates a categorical x axis of the selected user groups',
            name: 'GroupId',
            alias: 'SavedGroups',
            label: 'My saved groups',
            description: 'Creates a categorical x axis of the selected saved groups',
            type: 'VARCHAR',
            isDemographic: true, // use this to tell the visualization provider to only join on Subject (not Subject and Visit)
            variableType: 'USER_GROUPS'
        }];
    },

    getSessionMeasures : function() {
        var cols = [];
        Ext.iterate(Connector.getState().getSessionColumns(), function(k,col) {
            cols.push(col);
        });
        return cols;
    }
});