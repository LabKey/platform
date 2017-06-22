/*
 * Copyright (c) 2012-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.vis.GenericChartAxisPanel', {

    extend : 'LABKEY.vis.GenericOptionsPanel',

    border: false,

    axisName: null,
    multipleCharts: false,
    isSavedReport: false,

    initComponent : function()
    {
        this.userEditedLabel = this.isSavedReport;

        this.axisLabelField =  Ext4.create('Ext.form.field.Text', {
            name: 'label',
            hideFieldLabel: true,
            enableKeyEvents: true,
            width: 232
        });

        this.axisLabelField.addListener('keyup', function()
        {
            if (this.getDefaultAxisLabel() != null)
            {
                this.userEditedLabel = this.axisLabelField.getValue() != '';
                this.axisLabelResetButton.setDisabled(!this.userEditedLabel);
            }
        }, this, {buffer: 500});

        this.axisLabelResetButton = Ext4.create('Ext.Button', {
            disabled: !this.userEditedLabel,
            cls: 'revert-label-button',
            iconCls: 'fa fa-refresh',
            tooltip: 'Reset the label to the default value based on the Chart Type dialog selections.',
            handler: function() {
                this.userEditedLabel = false;
                this.axisLabelResetButton.setDisabled(!this.userEditedLabel);
                this.resetAxisLabel();
            },
            scope: this
        });

        this.axisLabelFieldContainer = Ext4.create('Ext.form.FieldContainer', {
            fieldLabel: 'Label',
            layout: 'hbox',
            width: 360,
            items: [this.axisLabelField, this.axisLabelResetButton]
        });

        this.scaleTypeRadioGroup = Ext4.create('Ext.form.RadioGroup', {
            fieldLabel: 'Scale Type',
            columns: 2,
            width: 250,
            layoutOptions: ['point', 'time'],
            items: [
                Ext4.create('Ext.form.field.Radio', {
                    boxLabel: 'Linear',
                    inputValue: 'linear',
                    name: 'scaleType',
                    checked: 'true'
                }),
                Ext4.create('Ext.form.field.Radio', {
                    boxLabel: 'Log',
                    inputValue: 'log',
                    name: 'scaleType'
                })
            ]
        });

        this.scaleRangeMinField = Ext4.create('Ext.form.field.Number', {
            name: 'rangeMin',
            emptyText: 'Min',
            style: 'margin-right: 10px',
            width: 85,
            disabled: true
        });

        this.scaleRangeMaxField = Ext4.create('Ext.form.field.Number', {
            name: 'rangeMax',
            emptyText: 'Max',
            width: 85,
            disabled: true
        });

        this.scaleRangeRadioGroup = Ext4.create('Ext.form.RadioGroup', {
            fieldLabel: 'Range',
            columns: 1,
            vertical: true,
            submitEmptyText: false,
            padding: '0 0 10px 0',
            items: [{
                    boxLabel: 'Automatic' + (this.multipleCharts ? ' Across Charts' : ''),
                    name: 'scale',
                    inputValue: 'automatic',
                    checked: true
                },{
                    boxLabel: 'Automatic Within Chart',
                    name: 'scale',
                    inputValue: 'automatic_per_chart',
                    hidden: !this.multipleCharts
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
            ],
            listeners: {
                scope: this,
                change: function(rg, newValue)
                {
                    var isAutomatic = newValue.scale != 'manual';
                    this.scaleRangeMinField.setDisabled(isAutomatic);
                    this.scaleRangeMaxField.setDisabled(isAutomatic);
                    if (isAutomatic)
                    {
                        this.scaleRangeMinField.setValue(null);
                        this.scaleRangeMaxField.setValue(null);
                    }
                }
            }
        });

        this.items = this.getInputFields();
        
        this.callParent();
    },

    getInputFields : function()
    {
        return [
            this.axisLabelFieldContainer,
            this.scaleTypeRadioGroup,
            this.scaleRangeRadioGroup
        ];
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
        if (!Ext4.isDefined(config))
        {
            this.userEditedLabel = false;
            this.axisLabelResetButton.setDisabled(!this.userEditedLabel);
            return;
        }

        if (config.label)
            this.setAxisLabel(config.label);

        if (config.trans)
            this.setScaleTrans(config.trans);
        else if (config.scaleTrans)
            this.setScaleTrans(config.scaleTrans);

        this.setScaleRange(config);

        this.adjustScaleAndRangeOptions();
    },

    getAxisLabel: function(){
        return this.axisLabelField.getValue();
    },

    setAxisLabel: function(value){
        this.axisLabelField.setValue(value);

        if (this.getDefaultAxisLabel() != value)
        {
            this.userEditedLabel = true;
            this.axisLabelResetButton.setDisabled(!this.userEditedLabel);

            if (this.getDefaultAxisLabel() == null)
                this.defaultAxisLabel = value;
        }
    },

    resetAxisLabel: function()
    {
        var label = this.getDefaultAxisLabel();
        if (label != null)
            this.setAxisLabel(label);
    },

    getDefaultAxisLabel : function()
    {
        return Ext4.isString(this.defaultAxisLabel) ? this.defaultAxisLabel : null;
    },

    getScaleType: function(){
        return this.scaleTypeRadioGroup.getValue().scaleType;
    },

    getScaleRangeType: function() {
        return this.scaleRangeRadioGroup.getValue().scale;
    },

    setScaleRangeType: function(value) {
        this.scaleRangeRadioGroup.setValue(value);
        var radioComp = this.getScaleRangeTypeRadioByValue(value);
        if (radioComp)
            radioComp.setValue(true);
    },

    getScaleRangeTypeRadioByValue: function(value) {
        return this.scaleRangeRadioGroup.down('radio[inputValue="' + value + '"]');
    },

    getScaleRange: function () {
        var range = {};
        range.min = this.scaleRangeMinField.getValue();
        range.max = this.scaleRangeMaxField.getValue();
        return range;
    },

    setScaleRange: function(config)
    {
        var range = config;
        if (Ext4.isObject(config.range))
            range = config.range;
        else if (Ext4.isObject(config.scaleRange))
            range = config.scaleRange;

        var hasMin = range.min != null,
            hasMax = range.max != null;

        if (hasMin)
            this.scaleRangeMinField.setValue(range.min);
        if (hasMax)
            this.scaleRangeMaxField.setValue(range.max);

        if (Ext4.isString(range.type))
            this.setScaleRangeType(range.type);
        else if (Ext4.isString(range.scaleRangeType))
            this.setScaleRangeType(range.scaleRangeType);
        else
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

    adjustScaleAndRangeOptions: function(isNumeric, overrideAsHidden)
    {
        //disable for non-numeric types
        if (Ext4.isBoolean(isNumeric))
        {
            this.setRangeOptionVisible(isNumeric);
            this.setScaleTypeOptionVisible(isNumeric);
        }

        //some render type axis options should always be hidden
        if ((this.axisName == 'x' && (this.renderType == 'bar_chart' || this.renderType == 'box_plot')) || overrideAsHidden)
        {
            this.setRangeOptionVisible(false);
            this.setScaleTypeOptionVisible(false);
        }
    },

    setRangeOptionVisible : function(visible)
    {
        this.scaleRangeRadioGroup.hideForDatatype = !visible;
    },

    setScaleTypeOptionVisible : function(visible)
    {
        this.scaleTypeRadioGroup.hideForDatatype = !visible;
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
        this.renderType = renderType;

        if (renderType == 'time_chart')
            this.onMeasureChangeTimeChart(measures);
        else
            this.onMeasureChangesGenericChart(measures, renderType);
    },

    onMeasureChangeTimeChart : function(measures)
    {
        if (this.axisName == 'x')
        {
            this.adjustScaleAndRangeOptions(false);

            // if visit based time chart, set range type to Automatic and hide range options
            var isVisitBased = measures.x.time == 'visit';
            this.setRangeOptionVisible(!isVisitBased);
            if (isVisitBased)
                this.setScaleRangeType('automatic');

            if (isVisitBased)
                this.defaultAxisLabel = 'Visit';
            else
                this.defaultAxisLabel = measures.x.interval + (Ext4.isObject(measures.x.zeroDateCol) ? ' Since ' + measures.x.zeroDateCol.label : '');

            if (!this.userEditedLabel)
                this.resetAxisLabel();
        }
        else
        {
            this.adjustScaleAndRangeOptions(true);

            var side = this.axisName == 'y' ? 'left' : 'right';
            this.defaultAxisLabel = LABKEY.vis.TimeChartHelper.getMeasuresLabelBySide(measures.y, side);
            if (!this.userEditedLabel)
                this.resetAxisLabel();
        }
    },

    onMeasureChangesGenericChart : function(measures, renderType)
    {
        var properties;
        if (renderType === 'bar_chart' && this.axisName === 'x' && measures['xSub']) {
            properties = measures['xSub'];
        } else {
            properties = measures[this.axisName];
        }
        var type = LABKEY.vis.GenericChartHelper.getMeasureType(properties),
            isNumeric = LABKEY.vis.GenericChartHelper.isNumericType(type);

        this.adjustScaleAndRangeOptions(isNumeric);

        this.defaultAxisLabel = LABKEY.vis.GenericChartHelper.getSelectedMeasureLabel(renderType, this.axisName, properties);
        if (!this.userEditedLabel)
        {
            if (Ext4.isDefined(properties))
                this.resetAxisLabel();
            else
                this.setAxisLabel('');
        }
    },

    onChartLayoutChange : function(multipleCharts)
    {
        var automaticRadio = this.getScaleRangeTypeRadioByValue('automatic'),
            automaticPerChartRadio = this.getScaleRangeTypeRadioByValue('automatic_per_chart');

        automaticRadio.setBoxLabel(multipleCharts ? 'Automatic Across Charts' : 'Automatic');
        automaticPerChartRadio.setVisible(multipleCharts);
        if (automaticPerChartRadio.checked && automaticPerChartRadio.isHidden())
            this.setScaleRangeType('automatic');
    }
});