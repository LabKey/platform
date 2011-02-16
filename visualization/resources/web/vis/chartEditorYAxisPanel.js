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

        columnOneItems.push({
            id: 'y-axis-scale-combo',
            xtype: 'combo',
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

        columnTwoItems.push({
            id: 'y-axis-range-automatic-radio',
            xtype: 'radio',
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
                        yAxisRangeMinNumberField.disable();
                        yAxisRangeMinNumberField.reset();
                        delete this.axis.range.min;

                        yAxisRangeMaxNumberField.disable();
                        yAxisRangeMaxNumberField.reset();
                        delete this.axis.range.max;

                        this.axis.range.type = 'automatic';
                        this.fireEvent('chartDefinitionChanged', false);
                    }
                }
            }
        });

        var yAxisRangeMinNumberField = new Ext.form.NumberField({
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

        var yAxisRangeMaxNumberField = new Ext.form.NumberField({
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
                {
                    id: 'y-axis-range-manual-radio',
                    xtype: 'radio',
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
                                yAxisRangeMinNumberField.enable();
                                yAxisRangeMaxNumberField.enable();
                                //yAxisRangeMinNumberField.focus();

                                // if this is a saved chart with manual min and max set
                                if(typeof this.axis.range.min == "number"){
                                    yAxisRangeMinNumberField.setValue(this.axis.range.min);
                                }
                                if(typeof this.axis.range.max == "number"){
                                    yAxisRangeMaxNumberField.setValue(this.axis.range.max);
                                }

                                this.axis.range.type = 'manual';
                            }
                        }
                    }

                },
                yAxisRangeMinNumberField,
                yAxisRangeMaxNumberField
            ]
        });

        this.items = [{
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
            // if this is rendering with a saved chart, set the range options
            // since automatic is the default, only need to change it if type == manual
            if(this.axis.range.type && this.axis.range.type == 'manual'){
                Ext.getCmp('y-axis-range-manual-radio').setValue(true);
            }
        }, this);

        LABKEY.vis.ChartEditorYAxisPanel.superclass.initComponent.call(this);
    },

    getAxis: function(){
        return this.axis;
    },

    setLabel: function(newLabel){
        // if the label is not already set, use the newLabel
        if(this.axis.label == ""){
            this.axis.label = newLabel;
            Ext.getCmp('y-axis-label-textfield').setValue(this.axis.label);
        }
    }
});
