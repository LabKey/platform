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
            'chartDefinitionChanged',
            'measureMetadataRequestPending',
            'measureMetadataRequestComplete'
        );

        LABKEY.vis.ChartEditorXAxisPanel.superclass.constructor.call(this, config);
    },

    initComponent : function() {
        // the x-axis editor panel will be laid out with 2 columns
        var columnOneItems = [];
        var columnTwoItems = [];

        // combobox for the selection of the date axis interval unit
        this.intervalCombo = new Ext.form.ComboBox({
            id: 'x-axis-interval-combo',
            triggerAction: 'all',
            mode: 'local',
            store: new Ext.data.ArrayStore({
                fields: ['value'],
                data: [['Days'], ['Weeks'], ['Months'], ['Years']],
                listeners: {
                    scope: this,
                    'load': function(cmp, records, options) {
                        // if this is not a saved chart and the zerodatecol value has loaded, then set the default axis label
                        if(!this.axis.label && this.zeroDateCombo && this.zeroDateCombo.getValue()) {
                            var zeroDateLabel = this.zeroDateCombo.getStore().getAt(this.zeroDateCombo.getStore().find('name', this.zeroDateCombo.getValue())).data.label;
                            var newLabel = "Days Since " + zeroDateLabel;
                            Ext.getCmp('x-axis-label-textfield').setValue(newLabel);

                            this.axis.label = newLabel;
                        }
                    }
                }
            }),
            value: this.dateOptions.interval,
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

                        this.axis.label = newLabel;
                    }

                    this.dateOptions.interval = cmp.getValue();
                    this.fireEvent('chartDefinitionChanged', true);
                }
            }
        });
        columnOneItems.push(this.intervalCombo);

        // combobox for the selection of the date to use for the given measure on the x-axis
        this.measureDateCombo = new Ext.form.ComboBox({
            id: 'measure-date-combo',
            triggerAction: 'all',
            mode: 'local',
            store: new Ext.data.Store(),
            valueField: 'name',
            displayField: 'label',
            forceSelection: true,
            width: 175,
            listeners: {
                scope: this,
                'select': function(cmp, record, index) {
                    this.measure = record.data;
                    this.fireEvent('chartDefinitionChanged', true);
                }
            }
        });

        // combobox to select the "starting date" to be used for the x-axis interval calculation
        this.zeroDateCombo = new Ext.form.ComboBox({
            id: 'zero-date-combo',
            triggerAction: 'all',
            mode: 'local',
            store: new Ext.data.Store(),
            valueField: 'name',
            displayField: 'label',
            forceSelection: true,
            width: 175,
            listeners: {
                scope: this,
                'select': function(cmp, record, index) {
                    // change the axis label if it has not been customized by the user
                    // note: assume unchanged if contains part of the original label, i.e. " Since <Zero Date Label>"
                    var beginning = this.intervalCombo.getValue() + " Since ";
                    if(Ext.getCmp('x-axis-label-textfield').getValue().indexOf(beginning) == 0) {
                       var newLabel = this.intervalCombo.getValue() + " Since " + record.data.label;
                        Ext.getCmp('x-axis-label-textfield').setValue(newLabel);

                        this.axis.label = newLabel;
                    }

                    this.dateOptions.zeroDateCol = record.data;
                    this.fireEvent('chartDefinitionChanged', true);
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
                        this.axis.range.type = 'automatic';
                        this.setRangeFormOptions(this.axis.range.type);
                        this.fireEvent('chartDefinitionChanged', false);
                    }
                }
            }
        });
        columnTwoItems.push(this.rangeAutomaticRadio);

        this.rangeManualRadio = new Ext.form.Radio({
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
                    filters: [LABKEY.Visualization.Filter.create({schemaName: schemaName})],
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
                    // if this is a saved report, we will have a zero date to select
                    var index = 0;
                    if(this.dateOptions.zeroDateCol){
                        index = store.find('name', this.dateOptions.zeroDateCol.name);
                    }
                    // otherwise, try a few hard-coded options
                    else if(store.find('name', 'StartDate') > -1) {
                        index = store.find('name', 'StartDate');
                    }
                    else if(store.find('name', 'EnrollmentDt') > -1) {
                        index = store.find('name', 'EnrollmentDt');
                    }
                    else if(store.find('name', 'Date') > -1) {
                        index = store.find('name', 'Date');
                    }
                    this.zeroDateCombo.setValue(store.getAt(index).get('name'));
                    this.dateOptions.zeroDateCol = store.getAt(store.find('name', store.getAt(index).get('name'))).data;

                    // if this is not a saved chart and the interval value has loaded, then set the default axis label
                    if(!this.axis.label && this.intervalCombo && this.intervalCombo.getValue()) {
                        var zeroDateLabel = store.getAt(store.find('name', this.zeroDateCombo.getValue())).data.label;
                        var newLabel = this.intervalCombo.getValue() + " Since " + zeroDateLabel;
                        Ext.getCmp('x-axis-label-textfield').setValue(newLabel);

                        this.axis.label = newLabel;
                    }

                    // this is one of the requests being tracked, see if the rest are done
                    this.fireEvent('measureMetadataRequestComplete');
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
                    filters: [LABKEY.Visualization.Filter.create({schemaName: schemaName, queryName: queryName})],
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
                        // if this is a saved report, we will have a measure date to select
                        var index = 0;
                        if(this.measure.name){
                            index = store.find('name', this.measure.name);
                        }
                        // otherwise, try a few hard-coded options
                        else if(store.find('name', 'ParticipantVisit/VisitDate') > -1) {
                            index = store.find('name', 'ParticipantVisit/VisitDate');
                        }
                        else if(store.find('name', 'Date') > -1) {
                            index = store.find('name', 'Date');
                        }
                        this.measureDateCombo.setValue(store.getAt(index).get('name'));
                        this.measure = store.getAt(store.find('name', store.getAt(index).get('name'))).data;

                    // this is one of the requests being tracked, see if the rest are done
                    this.fireEvent('measureMetadataRequestComplete');
                }
            }
        });
    },

    getAxis: function(){
        return this.axis;
    },

    getDateOptions: function(){
        return this.dateOptions;
    },

    getMeasure: function(){
        return this.measure;
    },

    setZeroDateStore: function(schema){
        this.fireEvent('measureMetadataRequestPending');
        var newZStore = this.newZeroDateStore(schema);
        this.zeroDateCombo.bindStore(newZStore);
    },

    setMeasureDateStore: function(schema, query){
        this.fireEvent('measureMetadataRequestPending');
        var newMStore = this.newMeasureDateStore(schema, query);
        this.measureDateCombo.bindStore(newMStore);
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
