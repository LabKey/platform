/*
 * Copyright (c) 2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext.define('LABKEY.app.panel.MeasurePicker', {

    extend : 'Ext.panel.Panel',

    border : false,

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
            this.picker = Ext.create('LABKEY.ext4.MeasuresDataView.SplitPanels', this.initConfig);
        }

        return this.picker;
    }
});