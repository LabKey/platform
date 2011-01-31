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

        this.addEvents(
            'yAxisScaleChanged',
            'yAxisLabelChanged',
            'yAxisRangeAutomaticChecked',
            'yAxisRangeManualMinChanged',
            'yAxisRangeManualMaxChanged'
        );

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
            value: 'linear',
            forceSelection: true,
            listeners: {
                scope: this,
                'select': function(cmp, record, index) {
                    this.fireEvent('yAxisScaleChanged', cmp.getValue());
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
                    this.fireEvent('yAxisLabelChanged', newVal);
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
                        yAxisRangeMaxNumberField.disable();
                        yAxisRangeMaxNumberField.reset();

                        this.fireEvent('yAxisRangeAutomaticChecked');
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
                    this.fireEvent('yAxisRangeManualMinChanged', newVal);
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
                    this.fireEvent('yAxisRangeManualMaxChanged', newVal);
                }
            }
        });

        columnTwoItems.push({
            xtype: 'compositefield',
            defaults: {flex: 1},
            items: [
                {
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
                                yAxisRangeMinNumberField.focus();
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

        LABKEY.vis.ChartEditorYAxisPanel.superclass.initComponent.call(this);
    },
});
