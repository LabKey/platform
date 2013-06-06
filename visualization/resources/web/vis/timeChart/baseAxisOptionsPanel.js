/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('LABKEY.vis.BaseAxisOptionsPanel', {

    extend : 'LABKEY.vis.GenericOptionsPanel',

    constructor : function(config)
    {
        this.callParent([config]);
    },

    // Shared functions for time chart x-axis and y-axis optoins panels

    applyChangesButtonClicked: function() {
        // check to make sure that, if set, the max value is >= to min
        var maxVal = this.rangeMaxNumberField ? this.rangeMaxNumberField.getValue() : null;
        var minVal = this.rangeMinNumberField ? this.rangeMinNumberField.getValue() : null;
        if (this.rangeManualRadio && this.rangeManualRadio.checked
                && typeof minVal == "number" && typeof maxVal == "number" && maxVal < minVal)
        {
            Ext4.Msg.alert("ERROR", "Range 'max' value must be greater than or equal to 'min' value.", function(){
                this.rangeMaxNumberField.focus();
            }, this);
            return;
        }

        this.fireEvent('closeOptionsWindow', false);
        this.checkForChangesAndFireEvents();
    },

    cancelChangesButtonClicked: function(){
        this.fireEvent('closeOptionsWindow', true);
    },

    restoreRangeRadioValues: function(values){
        if (values.type == 'automatic' && this.rangeAutomaticRadio)
        {
            this.rangeAutomaticRadio.setValue(true);
        }
        else if (values.type == 'automatic_per_chart' && this.rangeAutomaticPerChartRadio)
        {
            this.rangeAutomaticPerChartRadio.setValue(true);
        }
        else if (values.type == 'manual' && this.rangeManualRadio)
        {
            this.rangeManualRadio.setValue(true);
            if (values.hasOwnProperty("min") && this.rangeMinNumberField)
                this.rangeMinNumberField.setValue(values.min);
            if (values.hasOwnProperty("max") && this.rangeMaxNumberField)
                this.rangeMaxNumberField.setValue(values.max);
        }
    },

    setRangeMinMaxDisplay: function(type){
        if (this.rangeMinNumberField && this.rangeMaxNumberField)
        {
            if (type == 'manual')
            {
                this.rangeMinNumberField.enable();
                this.rangeMaxNumberField.enable();
                this.rangeMinNumberField.focus();
            }
            else
            {
                this.rangeMinNumberField.disable();
                this.rangeMinNumberField.setValue("");

                this.rangeMaxNumberField.disable();
                this.rangeMaxNumberField.setValue("");
            }
        }
    },

    setRangeAutomaticOptions: function(chartLayout, isYaxis){
        if (this.rangeAutomaticPerChartRadio)
        {
            // hide/show the automatic per chart radio option
            this.rangeAutomaticPerChartRadio.setVisible(chartLayout != "single");

            // change y-axis default to 'automatic within chart' for per measure/dimension chart layout
            if (this.rangeAutomaticRadio.checked || this.rangeAutomaticPerChartRadio.checked)
            {
                this.rangeAutomaticPerChartRadio.setValue(isYaxis && chartLayout == "per_dimension");
                this.rangeAutomaticRadio.setValue(!isYaxis || chartLayout != "per_dimension");
            }
        }

        if (this.rangeAutomaticRadio)
        {
            // update the automatic (across chart) radio option box label
            this.rangeAutomaticRadio.boxLabel = chartLayout != "single" ? 'Automatic across charts' : 'Automatic';
            if (this.rangeAutomaticRadio.rendered)
            {
                this.rangeAutomaticRadio.boxLabelEl.update(this.rangeAutomaticRadio.boxLabel);
            }
        }
    }
});