/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext.namespace("LABKEY.vis");

Ext.QuickTips.init();

LABKEY.vis.ChartEditorXAxisPanel = Ext.extend(Ext.FormPanel, {
    constructor : function(config){
        Ext.apply(config, {
            title: 'X-Axis',
            autoHeight: true,
            autoWidth: true,
            bodyStyle: 'padding:5px',
            border: false,
            labelAlign: 'top',
            items: []
        });

        this.addEvents(
            'xAxisIncrementChanged',
            'xAxisLabelChanged',
            'measureDateChanged',
            'zeroDateChanged',
            'xAxisRangeAutomaticChecked',
            'xAxisRangeManualMinChanged',
            'xAxisRangeManualMaxChanged'
        );

        LABKEY.vis.ChartEditorXAxisPanel.superclass.constructor.call(this, config);
    },

    initComponent : function() {
        // the x-axis editor panel will be laid out with 2 columns
        var columnOneItems = [];
        var columnTwoItems = [];

        // combobox for the selection of the date axis increment unit
        this.incrementCombo = new Ext.form.ComboBox({
            id: 'x-axis-increment-combo',
            triggerAction: 'all',
            mode: 'local',
            store: new Ext.data.ArrayStore({
                fields: ['value'],
                data: [['Days'], ['Weeks'], ['Months'], ['Years']],
                listeners: {
                    scope: this,
                    'load': function(cmp, records, options) {
                        this.fireEvent('xAxisIncrementChanged', 'Days', false);

                        // if the zeroDateCol value has loaded, then set the default axis label
                        if(this.zeroDateCombo && this.zeroDateCombo.getValue()) {
                            var zeroDateLabel = this.zeroDateCombo.getStore().getAt(this.zeroDateCombo.getStore().find('name', this.zeroDateCombo.getValue())).data.label;
                            var newLabel = "Days Since " + zeroDateLabel;
                            Ext.getCmp('x-axis-label-textfield').setValue(newLabel);
                            this.fireEvent('xAxisLabelChanged', newLabel, false);
                        }
                    }
                }
            }),
            value: 'Days',
            valueField: 'value',
            displayField: 'value',
            fieldLabel: 'Draw x-axis as',
            forceSelection: true,
            listeners: {
                scope: this,
                'select': function(cmp, record, index) {
                    // change the axis label if it has not been customized by the user
                    // note: assume unchanged if contains part of the original label, i.e. " Since <Zero Date Label>"
                    var zeroDateLabel = this.zeroDateCombo.getStore().getAt(this.zeroDateCombo.getStore().find('name', this.zeroDateCombo.getValue())).data.label;
                    var ending = " Since " + zeroDateLabel;
                    if(Ext.getCmp('x-axis-label-textfield').getValue().indexOf(ending) > -1) {
                       var newLabel = record.data.value + " Since " + zeroDateLabel;
                       Ext.getCmp('x-axis-label-textfield').setValue(newLabel);
                       this.fireEvent('xAxisLabelChanged', newLabel, false);
                    }

                    this.fireEvent('xAxisIncrementChanged', cmp.getValue(), true);
                }
            }
        });
        columnOneItems.push(this.incrementCombo);

        // combobox for the selection of the date to use for the given measure on the x-axis
        this.measureDateCombo = new Ext.form.ComboBox({
            id: 'measure-date-combo',
            triggerAction: 'all',
            mode: 'local',
            store: this.newMeasureDateStore(this.yAxisMeasure.schemaName, this.yAxisMeasure.queryName),
            valueField: 'name',
            displayField: 'label',
            forceSelection: true,
            width: 175,
            listeners: {
                scope: this,
                'select': function(cmp, record, index) {
                    this.fireEvent('measureDateChanged', record.data, true);
                }
            }
        });

        // combobox to select the "starting date" to be used for the x-axis increment calculation
        this.zeroDateCombo = new Ext.form.ComboBox({
            id: 'zero-date-combo',
            triggerAction: 'all',
            mode: 'local',
            store: this.newZeroDateStore(this.yAxisMeasure.schemaName),
            valueField: 'name',
            displayField: 'label',
            forceSelection: true,
            width: 175,
            listeners: {
                scope: this,
                'select': function(cmp, record, index) {
                    // change the axis label if it has not been customized by the user
                    // note: assume unchanged if contains part of the original label, i.e. " Since <Zero Date Label>"
                    var beginning = this.incrementCombo.getValue() + " Since ";
                    if(Ext.getCmp('x-axis-label-textfield').getValue().indexOf(beginning) == 0) {
                       var newLabel = this.incrementCombo.getValue() + " Since " + record.data.label;
                       Ext.getCmp('x-axis-label-textfield').setValue(newLabel);
                       this.fireEvent('xAxisLabelChanged', newLabel, false);
                    }

                    this.fireEvent('zeroDateChanged', record.data, true);
                }
            }
        });

        columnOneItems.push({
            xtype: 'compositefield',
            fieldLabel: 'Calculate interval between',
            defaults: {flex: 1},
            items: [
                this.zeroDateCombo,
                {
                    xtype:'label',
                    text:'and',
                    width: 30,
                    style: {textAlign: 'center'}
                },
                this.measureDateCombo
            ]
        });

        columnTwoItems.push({
            xtype: 'textfield',
            id: 'x-axis-label-textfield',
            fieldLabel: 'Axis label',
            width: 300,
            listeners: {
                scope: this,
                'change': function(cmp, newVal, oldVal) {
                    this.fireEvent('xAxisLabelChanged', newVal, true);
                }
            }
        });

        columnTwoItems.push({
            id: 'x-axis-range-automatic-radio',
            xtype: 'radio',
            name: 'xaxis_range',
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
                        Ext.getCmp('xaxis-range-min-textfield').disable();
                        Ext.getCmp('xaxis-range-min-textfield').reset();
                        Ext.getCmp('xaxis-range-max-textfield').disable();
                        Ext.getCmp('xaxis-range-max-textfield').reset();

                        this.fireEvent('xAxisRangeAutomaticChecked');
                    }
                }
            }
        });

        columnTwoItems.push({
            xtype: 'compositefield',
            defaults: {flex: 1},
            items: [
                {
                    xtype: 'radio',
                    name: 'xaxis_range',
                    inputValue: 'manual',
                    boxLabel: 'Manual',
                    width: 85,
                    height: 1,
                    listeners: {
                        scope: this,
                        'check': function(field, checked){
                            // if checked, enable the min and max fields and give min focus
                            if(checked) {
                                Ext.getCmp('xaxis-range-min-textfield').enable();
                                Ext.getCmp('xaxis-range-max-textfield').enable();
                                Ext.getCmp('xaxis-range-min-textfield').focus();
                            }
                        }
                    }
                },
                {
                    xtype: 'numberfield',
                    id: 'xaxis-range-min-textfield',
                    emptyText: 'Min',
                    selectOnFocus: true,
                    width: 75,
                    diabled: true,
                    listeners: {
                        scope: this,
                        'change': function(cmp, newVal, oldVal) {
                            this.fireEvent('xAxisRangeManualMinChanged', newVal);
                        }
                    }
                },
                {
                    xtype: 'numberfield',
                    id: 'xaxis-range-max-textfield',
                    emptyText: 'Max',
                    selectOnFocus: true,
                    width: 75,
                    diabled: true,
                    listeners: {
                        scope: this,
                        'change': function(cmp, newVal, oldVal) {
                            this.fireEvent('xAxisRangeManualMaxChanged', newVal);
                        }
                    }
                }
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


        LABKEY.vis.ChartEditorXAxisPanel.superclass.initComponent.call(this);
    },

    newZeroDateStore: function(schemaName) {
        return new Ext.data.Store({
            autoLoad: true,
            reader: new Ext.data.JsonReader({
                    root:'measures',
                    idProperty:'id'
                },
                [{name: 'id'}, {name:'name'},{name:'label'},{name:'description'}, {name:'isUserDefined'}, {name:'queryName'}, {name:'schemaName'}, {name:'type'}]
            ),
            proxy: new Ext.data.HttpProxy({
                method: 'GET',
                url : LABKEY.ActionURL.buildURL('visualization', 'getZeroDate', LABKEY.ActionURL.getContainer(), {
                    filters: [LABKEY.Visualization.Filter.create({
                        schemaName: schemaName
                    })],
                    dateMeasures: false
                })
            }),
            sortInfo: {
                field: 'label',
                direction: 'ASC'
            },
            listeners: {
                scope: this,
                'load': function(store, records, options) {
                    // initial value is StartDate if it exists, else first record should be used as default
                    if(store.find('name', 'StartDate') > -1) {
                        this.zeroDateCombo.setValue('StartDate');
                    }
                    else {
                        this.zeroDateCombo.setValue(records[0].get('name'));
                    }
                    this.fireEvent('zeroDateChanged', store.getAt(store.find('name', this.zeroDateCombo.getValue())).data, false);

                    // if the increment value has loaded, then set the default axis label
                    if(this.incrementCombo && this.incrementCombo.getValue()) {
                        var zeroDateLabel = store.getAt(store.find('name', this.zeroDateCombo.getValue())).data.label;
                        var newLabel = this.incrementCombo.getValue() + " Since " + zeroDateLabel;
                        Ext.getCmp('x-axis-label-textfield').setValue(newLabel);
                        this.fireEvent('xAxisLabelChanged', newLabel, false);
                    }

//                        // this is one of the requests being tracked, see if the rest are done
//                        this.requestCounter--;
//                        if(this.requestCounter == 0) {
//                            this.getEl().unmask();
//                            this.getChartData();
//                        }
                }
            }
        })
    },

    newMeasureDateStore: function(schemaName, queryName) {
        return new Ext.data.Store({
            autoLoad: true,
            reader: new Ext.data.JsonReader({
                    root:'measures',
                    idProperty:'id'
                },
                [{name: 'id'}, {name:'name'},{name:'label'},{name:'description'}, {name:'isUserDefined'}, {name:'queryName'}, {name:'schemaName'}, {name:'type'}]
            ),
            proxy: new Ext.data.HttpProxy({
                method: 'GET',
                url : LABKEY.ActionURL.buildURL('visualization', 'getMeasures', LABKEY.ActionURL.getContainer(), {
                    filters: [LABKEY.Visualization.Filter.create({
                        schemaName: schemaName,
                        queryName: queryName
                    })],
                    dateMeasures: true
                })
            }),
            sortInfo: {
                field: 'label',
                direction: 'ASC'
            },
            listeners: {
                scope: this,
                'load': function(store, records, options){
                    // initial value is VisitDate if it exists, else first record should be used as default
                    if(store.find('name', 'ParticipantVisit/VisitDate') > -1) {
                        this.measureDateCombo.setValue('ParticipantVisit/VisitDate');
                    }
                    else {
                        this.measureDateCombo.setValue(records[0].get('name'));
                    }
                    this.fireEvent('measureDateChanged', store.getAt(store.find('name', this.measureDateCombo.getValue())).data, false);

//                        // this is one of the requests being tracked, see if the rest are done
//                        this.requestCounter--;
//                        if(this.requestCounter == 0) {
//                            this.getEl().unmask();
//                            this.getChartData();
//                        }
                }
            }
        });
    }
});
