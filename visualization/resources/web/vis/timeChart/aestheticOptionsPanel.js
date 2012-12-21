/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.vis.AestheticOptionsPanel', {

    extend : 'LABKEY.vis.GenericOptionsPanel',

    constructor : function(config){
        this.callParent([config]);

        this.addEvents(
            'chartDefinitionChanged',
            'closeOptionsWindow'
        );
    },

    initComponent : function(){
        // track if the panel has changed
        this.hasChanges = false;

        // slider field to set the line width for the chart(s)
        this.lineWidthSlider = Ext4.create('Ext.slider.Single', {
            anchor: '95%',
            fieldLabel: 'Line Width',
            labelWidth: 85,
            value: this.lineWidth || 3, // default to 3 if not specified
            increment: 1,
            minValue: 1,
            maxValue: 10,
            listeners: {
                scope: this,
                'changecomplete': function(cmp, newVal, thumb) {
                    this.hasChanges = true;
                }
            }
        });

        // checkbox to hide/show data points
        this.hideDataPointCheckbox = Ext4.create('Ext.form.field.Checkbox', {
            fieldLabel: 'Hide Data Points',
            labelWidth: 120,
            checked: this.hideDataPoints || false, // default to show data points
            value: this.hideDataPoints || false, // default to show data points
            listeners: {
                scope: this,
                'change': function(cmp, checked){
                    this.hasChanges = true;
                }
            }
        });

        this.items = [
            this.lineWidthSlider,
            this.hideDataPointCheckbox
        ];

        this.buttons = [{
            text: 'OK',
            handler: this.applyChangesButtonClicked,
            scope: this
        },{
            text: 'Cancel',
            handler: this.cancelChangesButtonClicked,
            scope: this
        }];

        this.callParent();
    },

    applyChangesButtonClicked: function() {
        this.fireEvent('closeOptionsWindow', false);
        this.checkForChangesAndFireEvents();
    },

    cancelChangesButtonClicked: function(){
        this.fireEvent('closeOptionsWindow', true);
    },

    getPanelOptionValues : function() {
        return {
            lineWidth: this.lineWidthSlider.getValue(),
            hideDataPoints: this.hideDataPointCheckbox.getValue()
        };
    },

    restoreValues : function(initValues) {
        if (initValues.hasOwnProperty("lineWidth"))
            this.lineWidthSlider.setValue(initValues.lineWidth);

        if (initValues.hasOwnProperty("hideDataPoints"))
            this.hideDataPointCheckbox.setValue(initValues.hideDataPoints);

        this.hasChanges = false;
    },

    checkForChangesAndFireEvents : function() {
        if (this.hasChanges)
            this.fireEvent('chartDefinitionChanged', false);

        // reset the changes flags
        this.hasChanges = false;
    }
});
