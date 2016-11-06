/*
 * Copyright (c) 2012-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.vis.GenericChartAxisPanel', {

    extend : 'LABKEY.vis.GenericOptionsPanel',

    border: false,

    axisName: null,

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
            this.userEditedLabel = this.axisLabelField.getValue() != '';
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

        this.scaleRangeMinField = Ext4.create('Ext.form.field.Number', {
            name: 'rangeMin',
            emptyText: 'Min',
            style: 'margin-right: 10px',
            width: 64
        });

        this.scaleRangeMaxField = Ext4.create('Ext.form.field.Number', {
            name: 'rangeMax',
            emptyText: 'Max',
            width: 64
        });

        this.scaleRangeRadioGroup = Ext4.create('Ext.form.RadioGroup', {
            fieldLabel: 'Range',
            columns: 1,
            vertical: true,
            submitEmptyText: false,
            items: [{
                    boxLabel: 'Automatic',
                    name: 'scale',
                    inputValue: 'automatic',
                    checked: true
                },
                {
                    xtype: 'container',
                    layout: 'hbox',
                    items: [
                        {
                            xtype: 'radio',
                            boxLabel: 'Manual',
                            name: 'scale',
                            inputValue: 'manual',
                            style: 'margin-right: 10px'
                        },
                        this.scaleRangeMinField,
                        this.scaleRangeMaxField
                    ]
                }
            ]
        });

        this.items = this.getInputFields();
        
        this.callParent();
    },

    getInputFields : function()
    {
        return [
            this.axisLabelField,
            this.scaleTypeRadioGroup,
            this.scaleRangeRadioGroup
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
            scaleTrans: this.getScaleType(),
            scaleRangeType: this.getScaleRangeType(),
            scaleRange: this.getScaleRange()
        };
    },

    setPanelOptionValues: function(config)
    {
        if (config.label)
            this.setAxisLabel(config.label);

        if (config.trans)
            this.setScaleTrans(config.trans);
        else if (config.scaleTrans)
            this.setScaleTrans(config.scaleTrans);

        this.setScaleRange(config);
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

    getScaleRangeType: function() {
        return this.scaleRangeRadioGroup.getValue().scale;
    },

    setScaleRangeType: function(value) {
        this.scaleRangeRadioGroup.setValue(value);
        var radioComp = this.scaleRangeRadioGroup.down('radio[inputValue="' + value + '"]');
        if (radioComp)
            radioComp.setValue(true);
    },

    getScaleRange: function () {
        var range = {};
        range.min = this.scaleRangeMinField.getValue();
        range.max = this.scaleRangeMaxField.getValue();
        return range;
    },

    setScaleRange: function(range) {
        var hasMin = range.min != null,
            hasMax = range.max != null;

        if (hasMin)
            this.scaleRangeMinField.setValue(range.min);
        if (hasMax)
            this.scaleRangeMaxField.setValue(range.max);

        this.setScaleRangeType(hasMin || hasMax ? 'manual' : 'automatic');
    },

    setScaleTrans: function(value){
        this.scaleTypeRadioGroup.setValue(value);
        var radioComp = this.scaleTypeRadioGroup.down('radio[inputValue="' + value + '"]');
        if (radioComp)
            radioComp.setValue(true);
    },

    validateManualScaleRange: function() {
        var range = this.getScaleRange();

        if (range.min != null || range.max != null)
            if (range.min != null && range.max != null && range.min >= range.max) {
                this.scaleRangeMinField.markInvalid("Value must be a number less than the Max");
                this.scaleRangeMaxField.markInvalid("Value must be a number greater than the Min");
                return false;
            } else {
                return true;
            }
        else {
            this.scaleRangeMinField.markInvalid("Value must be a number less than the Max");
            this.scaleRangeMaxField.markInvalid("Value must be a number greater than the Min");
        }
    },

    adjustScaleOptions: function(properties) {
        var type = LABKEY.vis.GenericChartHelper.getMeasureType(properties);
        //disable for non-numeric types
      if ((this.axisName == 'x' || this.axisName == 'y') && (type != "int" && type != "float")) {
          this.scaleRangeRadioGroup.hideForDatatype = true;
          this.scaleTypeRadioGroup.hideForDatatype = true;
      } else {
          this.scaleRangeRadioGroup.hideForDatatype = false;
          this.scaleTypeRadioGroup.hideForDatatype = false;
      }
    },

    validateChanges : function ()
    {
        if (this.getScaleRangeType() == 'manual') {
            return this.validateManualScaleRange();
        }
        return true; //else automatic scale, which is valid
    },

    onMeasureChange : function(measures, renderType)
    {
        if (!this.userEditedLabel)
        {
            var properties = measures[this.axisName];
            if (Ext4.isDefined(properties)) {
                this.setAxisLabel(LABKEY.vis.GenericChartHelper.getDefaultLabel(renderType, this.axisName, properties));
                this.adjustScaleOptions(properties);
            } else {
                this.setAxisLabel('');
            }
        }
    }
});
