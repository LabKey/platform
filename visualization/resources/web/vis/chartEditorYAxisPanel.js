/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext.namespace("LABKEY.vis");

Ext.QuickTips.init();

LABKEY.vis.ChartEditorYAxisPanel = Ext.extend(Ext.FormPanel, {
    constructor : function(config){
        Ext.apply(config, {
            autoHeight: true,
            autoWidth: true,
            bodyStyle: 'padding: 5px',
            border: false,
            labelAlign: 'top',
            items: []
        });

        // set axis defaults, if not a saved chart
        Ext.applyIf(config.axis, {
            name: "y-axis",
            side: "left",
            scale: "linear",
            range: {type: "automatic"}
        });

        // track if the axis label is something other than the default
        config.userEditedLabel = (config.axis.label ? true : false);

        this.addEvents('chartDefinitionChanged');

        LABKEY.vis.ChartEditorYAxisPanel.superclass.constructor.call(this, config);
    },

    initComponent : function() {
        // the y-axis editor panel will be laid out with 2 columns
        var columnOneItems = [];
        var columnTwoItems = [];

        this.scaleCombo = new Ext.form.ComboBox({
            triggerAction: 'all',
            mode: 'local',
            store: new Ext.data.ArrayStore({
                fields: ['value', 'display'],
                data: [['linear', 'Linear'], ['log', 'Log']]
            }),
            valueField: 'value',
            displayField: 'display',
            fieldLabel: 'Scale',
            value: this.axis.scale,
            forceSelection: true,
            listeners: {
                scope: this,
                'select': function(cmp, record, index) {
                    this.axis.scale = cmp.getValue();
                    this.fireEvent('chartDefinitionChanged', false);
                }
            }
        });
        columnOneItems.push(this.scaleCombo);

        this.labelTextField = new Ext.form.TextField({
            fieldLabel: 'Axis label',
            value: this.axis.label,
            width: 300,
            enableKeyEvents: true,
            listeners: {
                scope: this,
                'change': function(cmp, newVal, oldVal) {
                    this.userEditedLabel = true;
                    this.axis.label = newVal;
                    this.fireEvent('chartDefinitionChanged', false);
                }
            }
        });
        this.labelTextField.addListener('keyUp', function(){
                this.userEditedLabel = true;
                this.axis.label = this.labelTextField.getValue();
                this.fireEvent('chartDefinitionChanged', false);
            }, this, {buffer: 500});
        columnOneItems.push(this.labelTextField);

        this.rangeAutomaticRadio = new Ext.form.Radio({
            fieldLabel: 'Range',
            inputValue: 'automatic',
            boxLabel: 'Automatic',
            height: 1,
            checked: this.axis.range.type == "automatic",
            listeners: {
                scope: this,
                'check': function(field, checked){
                    // if checked, remove any manual axis min value
                    if(checked) {
                        this.axis.range.type = 'automatic';
                        this.fireEvent('chartDefinitionChanged', false);
                    }
                }
            }
        });
        columnTwoItems.push(this.rangeAutomaticRadio);

        this.rangeManualRadio = new Ext.form.Radio({
            inputValue: 'manual',
            boxLabel: 'Manual',
            width: 85,
            height: 1,
            checked: this.axis.range.type == "manual",
            listeners: {
                scope: this,
                'check': function(field, checked){
                    // if checked, enable the min and max textfields and give min focus
                    if(checked) {
                        this.axis.range.type = 'manual';
                        this.setRangeFormOptions(this.axis.range.type);
                    }
                }
            }
        });

        this.rangeMinNumberField = new Ext.form.NumberField({
            emptyText: 'Min',
            selectOnFocus: true,
            width: 75,
            disabled: this.axis.range.type == "automatic",
            value: this.axis.range.min,
            listeners: {
                scope: this,
                'change': function(cmp, newVal, oldVal) {
                    // check to make sure that, if set, the min value is <= to max
                    if(typeof this.axis.range.max == "number" && typeof newVal == "number" && newVal > this.axis.range.max){
                        Ext.Msg.alert("ERROR", "Range 'min' value must be less than or equal to 'max' value.", function(){
                            this.rangeMinNumberField.focus();
                        }, this);
                        return;
                    }

                    this.axis.range.min = newVal;
                    // fire change event, (max value may or may not be set)
                    this.fireEvent('chartDefinitionChanged', false);
                }
            }
        });

        this.rangeMaxNumberField = new Ext.form.NumberField({
            emptyText: 'Max',
            selectOnFocus: true,
            width: 75,
            disabled: this.axis.range.type == "automatic",
            value: this.axis.range.max,
            listeners: {
                scope: this,
                'change': function(cmp, newVal, oldVal) {
                    // check to make sure that, if set, the max value is >= to min
                    if(typeof this.axis.range.min == "number" && typeof newVal == "number" && newVal < this.axis.range.min){
                        Ext.Msg.alert("ERROR", "Range 'max' value must be greater than or equal to 'min' value.", function(){
                            this.rangeMaxNumberField.focus();
                        }, this);
                        return;
                    }

                    this.axis.range.max = newVal;
                    // fire change event, (min value may or may not be set)
                    this.fireEvent('chartDefinitionChanged', false);
                }
            }
        });

        columnTwoItems.push({
            xtype: 'compositefield',
            defaults: {flex: 1},
            items: [
                this.rangeManualRadio,
                this.rangeMinNumberField,
                this.rangeMaxNumberField
            ]
        });

        this.items = [{
            border: false,
            layout: 'column',
            items: [{
                columnWidth: .5,
                layout: 'form',
                border: false,
                bodyStyle: 'padding: 5px',
                items: columnOneItems
            },{
                columnWidth: .5,
                layout: 'form',
                border: false,
                bodyStyle: 'padding: 5px',
                items: columnTwoItems
            }]
        }];

        this.on('activate', function(){
            this.doLayout();
        }, this);

        LABKEY.vis.ChartEditorYAxisPanel.superclass.initComponent.call(this);
    },

    getAxis: function(){
        return this.axis;
    },

    setLabel: function(newLabel){
        if (!this.userEditedLabel)
        {
            this.axis.label = newLabel;
            this.labelTextField.setValue(this.axis.label);
        }
    },

    setScale: function(newScale){
        this.axis.scale = newScale;
        this.scaleCombo.setValue(newScale);
    },

    setRange: function(rangeType){
        // select the given radio option without firing events
        if(rangeType == 'manual'){
            this.rangeManualRadio.suspendEvents(false);
            this.rangeManualRadio.setValue(true);
            this.rangeAutomaticRadio.setValue(false);
            this.rangeManualRadio.resumeEvents();
        }
        else if(rangeType == 'automatic'){
            this.rangeAutomaticRadio.suspendEvents(false);
            this.rangeAutomaticRadio.setValue(true);
            this.rangeManualRadio.setValue(false);
            this.rangeAutomaticRadio.resumeEvents();
        }

        this.setRangeFormOptions(rangeType);
    },

    setRangeFormOptions: function(rangeType){
        if(rangeType == 'manual'){
            this.axis.range.type = 'manual';

            this.rangeMinNumberField.enable();
            this.rangeMaxNumberField.enable();

            // if this is a saved chart with manual min and max set
            if(typeof this.axis.range.min == "number"){
                this.rangeMinNumberField.setValue(this.axis.range.min);
            }
            if(typeof this.axis.range.max == "number"){
                this.rangeMaxNumberField.setValue(this.axis.range.max);
            }
        }
        else if(rangeType == 'automatic'){
            this.axis.range.type = 'automatic';

            this.rangeMinNumberField.disable();
            this.rangeMinNumberField.setValue("");
            delete this.axis.range.min;

            this.rangeMaxNumberField.disable();
            this.rangeMaxNumberField.setValue("");
            delete this.axis.range.max;
        }
    }
});
