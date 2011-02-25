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
            title: 'Y-Axis',
            autoHeight: true,
            autoWidth: true,
            bodyStyle: 'padding: 5px',
            border: false,
            labelAlign: 'top',
            items: []
        });

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

        columnOneItems.push({
            id: 'y-axis-label-textfield',
            xtype: 'textfield',
            fieldLabel: 'Axis label',
            value: this.axis.label,
            width: 300,
            listeners: {
                scope: this,
                'change': function(cmp, newVal, oldVal) {
                    this.axis.label = newVal;
                    this.fireEvent('chartDefinitionChanged', false);
                }
            }
        });

        this.rangeAutomaticRadio = new Ext.form.Radio({
            name: 'yaxis_range',
            fieldLabel: 'Range',
            inputValue: 'automatic',
            boxLabel: 'Automatic',
            height: 1,
            checked: true,
            listeners: {
                scope: this,
                'check': function(field, checked){
                    // if checked, remove any manual axis min value
                    if(checked) {
                        this.axis.range.type = 'automatic';
                        this.setRangeFormOptions(this.axis.range.type);
                        this.fireEvent('chartDefinitionChanged', false);
                    }
                }
            }
        });
        columnTwoItems.push(this.rangeAutomaticRadio);

        this.rangeManualRadio = new Ext.form.Radio({
            name: 'yaxis_range',
            inputValue: 'manual',
            boxLabel: 'Manual',
            width: 85,
            height: 1,
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
            diabled: true,
            listeners: {
                scope: this,
                'change': function(cmp, newVal, oldVal) {
                    this.axis.range.min = newVal;
                    // fire change event if max is also set
                    if(typeof this.axis.range.max == "number"){
                        this.fireEvent('chartDefinitionChanged', false);
                    }
                }
            }
        });

        this.rangeMaxNumberField = new Ext.form.NumberField({
            emptyText: 'Max',
            selectOnFocus: true,
            width: 75,
            diabled: true,
            listeners: {
                scope: this,
                'change': function(cmp, newVal, oldVal) {
                    this.axis.range.max = newVal;
                    // fire change event if min is also set
                    if(typeof this.axis.range.min == "number"){
                        this.fireEvent('chartDefinitionChanged', false);
                    }
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
        this.axis.label = newLabel;
        Ext.getCmp('y-axis-label-textfield').setValue(this.axis.label);
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
