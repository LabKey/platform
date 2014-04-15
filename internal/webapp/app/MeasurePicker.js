
Ext.define('LABKEY.app.panel.MeasurePicker', {

    extend : 'Ext.panel.Panel',

    initConfig : {}, // passthrough the initial config for the measure picker to the core SplitPanels component

    constructor : function(config) {
        this.initConfig = config;
        this.callParent([config]);
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