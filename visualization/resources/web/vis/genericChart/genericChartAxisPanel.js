/*
 * Copyright (c) 2012-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.vis.GenericChartAxisPanel', {

    extend : 'LABKEY.vis.GenericOptionsPanel',

    border: false,

    constructor : function(config){

        config.userEditedLabel = (config.label ? true : false);

        this.callParent([config]);
    },

    initComponent : function() {

        this.axisLabelField =  Ext4.create('Ext.form.field.Text', {
            name: 'label',
            fieldLabel: 'Label',
            enableKeyEvents: true,
            width: 360
        });

        this.axisLabelField.addListener('keyup', function(){
            this.userEditedLabel = true;
        }, this, {buffer: 500});

        this.scaleTypeRadioGroup = Ext4.create('Ext.form.RadioGroup', {
            fieldLabel: 'Scale Type',
            columns: 1,
            layoutOptions: 'point',
            items: [
                Ext4.create('Ext.form.field.Radio', {
                    width: 100,
                    boxLabel: 'linear',
                    inputValue: 'linear',
                    name: 'scaleType',
                    checked: 'true'
                }),
                Ext4.create('Ext.form.field.Radio', {
                    boxLabel: 'log',
                    inputValue: 'log',
                    name: 'scaleType'
                })
            ]
        });

        this.items = this.getInputFields();
        
        this.callParent();
    },

    getInputFields : function()
    {
        return [
            this.axisLabelField,
            this.scaleTypeRadioGroup
        ];
    },

    getDefaultLabel: function(){
        var label;
        if(this.measure) {
            label = this.measure.label;
        } else {
            label = this.queryName;
        }
        return label;
    },

    getPanelOptionValues: function()
    {
        return {
            label: this.getAxisLabel(),
            scaleTrans: this.getScaleType()
        };
    },

    setPanelOptionValues: function(config)
    {
        if (config) {
        if (config.label)
            this.setAxisLabel(config.label);

        if (config.trans)
            this.setScaleTrans(config.trans);
        else if (config.scaleTrans)
            this.setScaleTrans(config.scaleTrans);
        }
    },

    getAxisLabel: function(){
        return this.axisLabelField.getValue();
    },

    setAxisLabel: function(value){
        this.axisLabelField.setValue(value);
    },

    getScaleType: function(){
        return this.scaleTypeRadioGroup.getValue().scaleType;
    },

    setScaleTrans: function(value){
        this.scaleTypeRadioGroup.setValue(value);
        var radioComp = this.scaleTypeRadioGroup.down('radio[inputValue="' + value + '"]');
        if (radioComp)
            radioComp.setValue(true);
    },

    onMeasureChange : function(properties)
    {
        if (!this.userEditedLabel)
        {
            if (properties.label)
                this.axisLabelField.setValue(properties.label);
            else if (properties.queryName)
                this.axisLabelField.setValue(properties.queryName);
        }
    }
});
