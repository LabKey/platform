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

        //Set time to 'date' if not already set.
        Ext.applyIf(config, {
            time: 'date'
        });

        // set axis defaults, if not a saved chart
        Ext.applyIf(config.axis, {
            name: "x-axis",
            range: {type: "automatic"}
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

        //Radio Buttons for Date/Visit based chart.
        this.dateChartRadio = new Ext.form.Radio({
            name: 'chartType',
            fieldLabel: 'Chart Type',
            inputValue: 'date',
            boxLabel: 'Date Based Chart',
            checked: this.time == "date", //|| !this.time, //For old charts we default to date based chart.
            listeners: {
                scope: this,
                'check': function(field, checked){
                    if(checked) {
                        this.time = "date"; //This will have to be changed to take into account the new data configuration.
                        this.zeroDateCombo.enable();
                        this.intervalCombo.enable();

                        this.fireEvent('chartDefinitionChanged', true);
                    }
                }
            }
        });

        this.visitChartRadio = new Ext.form.Radio({
            name: 'chartType',
            inputValue: 'visit',
            boxLabel: 'Visit Based Chart',
            checked: this.time == "visit",
            listeners: {
                scope: this,
                'check': function(field, checked){
                    if(checked) {
                        this.time = "visit";
                        this.zeroDateCombo.disable();
                        this.intervalCombo.disable();

                        this.fireEvent('chartDefinitionChanged', true);
                    }
                }
            }
        });


        columnOneItems.push({
            xtype: 'compositefield',
            defaults: {flex: 1},
            items: [
                this.dateChartRadio,
                this.visitChartRadio
            ]
        });
        
        // combobox for the selection of the date axis interval unit
        this.intervalCombo = new Ext.form.ComboBox({
            id: 'x-axis-interval-combo',
            disabled: this.axis.type == 'visit', //disable combo if the chart is visit based.
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
                            var zeroDateLabel = this.zeroDateCombo.getStore().getAt(this.zeroDateCombo.getStore().find('longlabel', this.zeroDateCombo.getValue())).data.label;
                            var newLabel = "Days Since " + zeroDateLabel;
                            this.labelTextField.setValue(newLabel);

                            this.axis.label = newLabel;
                        }
                    }
                }
            }),
            value: this.interval,
            valueField: 'value',
            displayField: 'value',
            fieldLabel: 'Draw x-axis as',
            forceSelection: true,
            listeners: {
                scope: this,
                'select': function(cmp, record, index) {
                    // change the axis label if it has not been customized by the user
                    // note: assume unchanged if contains part of the original label, i.e. " Since <Zero Date Label>"
                    var zeroDateLabel = this.zeroDateCombo.getStore().getAt(this.zeroDateCombo.getStore().find('longlabel', this.zeroDateCombo.getValue())).data.label;
                    var ending = " Since " + zeroDateLabel;
                    if(this.labelTextField.getValue().indexOf(ending) > -1) {
                        var newLabel = record.data.value + " Since " + zeroDateLabel;
                        this.labelTextField.setValue(newLabel);

                        this.axis.label = newLabel;
                    }

                    this.interval = cmp.getValue();
                    this.fireEvent('chartDefinitionChanged', true);
                }
            }
        });
        columnOneItems.push(this.intervalCombo);

        // combobox to select the "starting date" to be used for the x-axis interval calculation
        this.zeroDateCombo = new Ext.form.ComboBox({
            id: 'zero-date-combo',
            disabled: this.axis.type == 'visit', //disable combo if the chart is visit based.
            fieldLabel: 'Calculate time interval(s) relative to',
            triggerAction: 'all',
            mode: 'local',
            store: new Ext.data.Store(),
            valueField: 'longlabel',
            displayField: 'longlabel',
            forceSelection: true,
            width: 250,
            minListWidth : 350,
            listeners: {
                scope: this,
                'select': function(cmp, record, index) {
                    // change the axis label if it has not been customized by the user
                    // note: assume unchanged if contains part of the original label, i.e. " Since <Zero Date Label>"
                    var beginning = this.intervalCombo.getValue() + " Since ";
                    if(this.labelTextField.getValue().indexOf(beginning) == 0) {
                       var newLabel = this.intervalCombo.getValue() + " Since " + record.data.label;
                        this.labelTextField.setValue(newLabel);

                        this.axis.label = newLabel;
                    }

                    Ext.apply(this.zeroDateCol, record.data);
                    this.fireEvent('chartDefinitionChanged', true);
                }
            }
        });

        columnOneItems.push(this.zeroDateCombo);

        this.labelTextField = new Ext.form.TextField({
            id: 'x-axis-label-textfield',            
            fieldLabel: 'Axis label',
            value: this.axis.label,
            width: 300,
            enableKeyEvents: true,
            listeners: {
                scope: this,
                'change': function(cmp, newVal, oldVal) {
                    this.axis.label = newVal;
                    this.fireEvent('chartDefinitionChanged', false);
                }
            }
        });
        this.labelTextField.addListener('keyUp', function(){
                this.axis.label = this.labelTextField.getValue();
                this.fireEvent('chartDefinitionChanged', false);
            }, this, {buffer: 500});
        columnTwoItems.push(this.labelTextField);

        this.rangeAutomaticRadio = new Ext.form.Radio({
            name: 'xaxis_range',
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
            checked: this.axis.range.type == "manual",
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

        LABKEY.vis.ChartEditorXAxisPanel.superclass.initComponent.call(this);
    },

    newZeroDateStore: function(schemaName) {
        return new Ext.data.Store({
            autoLoad: true,
            reader: new Ext.data.JsonReader({
                    root:'measures',
                    idProperty:'id'
                },
                [{name: 'id'}, {name:'name'},{name:'label'},{name:'longlabel'},{name:'description'}, {name:'isUserDefined'}, {name:'queryName'}, {name:'schemaName'}, {name:'type'}]
            ),
            proxy: new Ext.data.HttpProxy({
                method: 'GET',
                url : LABKEY.ActionURL.buildURL('visualization', 'getZeroDate', LABKEY.ActionURL.getContainer(), {
                    filters: [LABKEY.Visualization.Filter.create({schemaName: schemaName})],
                    dateMeasures: false
                })
            }),
            sortInfo: {
                field: 'longlabel',
                direction: 'ASC'
            },
            listeners: {
                scope: this,
                'load': function(store, records, options) {
                    // if there are no zero date option for this study, warn the user
                    if (store.getTotalCount() == 0)
                    {
                        Ext.Msg.alert("Error", "There are no demographic date options available in this study. "
                            + "Please contact an administrator to have them configure the study to work with the Time Chart wizard.");
                    }

                    // if this is a saved report, we will have a zero date to select
                    var index = 0;
                    if(this.zeroDateCol.name){
                        // need to get the index by the variable name and query name
                        index = store.findBy(function(record, id){
                            return (record.get('name') == this.zeroDateCol.name
                               && record.get('queryName') == this.zeroDateCol.queryName)
                        }, this);
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

                    if(store.getAt(index)){
                        this.zeroDateCombo.setValue(store.getAt(index).get('longlabel'));
                        Ext.apply(this.zeroDateCol, store.getAt(index).data);
                    }

                    // if this is not a saved chart and the interval value has loaded, then set the default axis label
                    if(!this.axis.label && this.intervalCombo && this.intervalCombo.getValue() && store.find('longlabel', this.zeroDateCombo.getValue()) > -1) {
                        var zeroDateLabel = store.getAt(store.find('longlabel', this.zeroDateCombo.getValue())).data.label;
                        var newLabel = this.intervalCombo.getValue() + " Since " + zeroDateLabel;
                        this.labelTextField.setValue(newLabel);

                        this.axis.label = newLabel;
                    }

                    // this is one of the requests being tracked, see if the rest are done
                    this.fireEvent('measureMetadataRequestComplete');
                }
            }
        })
    },

    getAxis: function(){
        return this.axis;
    },

    getTime: function(){
        return this.time;
    },

    getZeroDateCol: function(){
        return this.zeroDateCol;
    },

    getInterval: function(){
        return this.interval;
    },

    setZeroDateStore: function(schema){
        this.fireEvent('measureMetadataRequestPending');
        var newZStore = this.newZeroDateStore(schema);
        this.zeroDateCombo.bindStore(newZStore);
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
