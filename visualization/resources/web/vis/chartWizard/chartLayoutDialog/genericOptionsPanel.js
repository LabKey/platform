/*
 * Copyright (c) 2012-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.vis.GenericOptionsPanel', {

    extend : 'Ext.form.Panel',

    constructor : function(config)
    {
        Ext4.applyIf(config, {
            header: false,
            autoHeight: true,
            autoWidth: true,
            border: false,
            padding: 10,
            labelAlign: 'top',
            items: [],
            buttonAlign: 'right',
            buttons: []
        });

        this.callParent([config]);
    },

    cancelChangesButtonClicked: function()
    {},

    getInputFields : function()
    {
        return [];
    },

    getPanelOptionValues : function()
    {
        return {};
    },

    validateChanges : function()
    {
        return true;
    },

    onMeasureChange : function(measures, renderType)
    {},

    onChartSubjectSelectionChange : function(asGroups)
    {},

    onChartLayoutChange : function(multipleCharts)
    {},

    restoreValues : function(initValues)
    {}
});
