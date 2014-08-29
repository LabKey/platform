/*
 * Copyright (c) 2012-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.vis.GenericChartGroupingPanel', {

    extend : 'LABKEY.vis.GenericOptionsPanel',

    constructor : function(config){
        Ext4.applyIf(config, {
            width: 300
        });

        this.callParent([config]);

        this.addEvents(
                'chartDefinitionChanged',
                'closeOptionsWindow'
        );
    },

    initComponent : function() {
        var groupingItems = [];

        var singleRadio = {
            xtype: 'radio',
            name: 'colorType',
            inputValue: 'single',
            boxLabel: 'With a single color',
            checked: this.colorType ? this.colorType === 'single' : true,
            width: 150
        };

        var categoricalRadio = {
            xtype: 'radio',
            id: 'colorCategory',
            name: 'colorType',
            inputValue: 'measure',
            boxLabel: 'By category',
            checked: this.colorType ? this.colorType === 'measure' : false,
            width: 150
        };

        this.colorTypeRadioGroup = Ext4.create('Ext.form.RadioGroup', {
            fieldLabel: 'Color Points',
            vertical: false,
            width: 300,
            columns: 2,
            items: [singleRadio, categoricalRadio],
            value: this.colorType ? this.colorType : 'single',
            listeners: {
                change: function(radioGroup, newVal){
                    if(!this.suppressEvents){
                        this.hasChanges = true;
                    }

                    this.colorCombo.setDisabled(newVal.colorType === 'single');
                },
                scope: this
            }
        });
        
        groupingItems.push(this.colorTypeRadioGroup);

        // TODO: fix this. We shouldn't need to have two stores but this occurs because the combobox fires a clearFilters event
        this.colorStore = Ext4.create('Ext.data.Store', {
            model: this.store.model.$className,
            proxy: {
                type: 'memory',
                reader: {
                    type: 'json'
                }
            },
            scope: this
        });

        this.colorCombo = Ext4.create('Ext.form.field.ComboBox', {
            fieldLabel: 'Color Category',
            name: 'colorMeasure',
            store: this.colorStore,
            disabled: this.colorType ? this.colorType === 'single' : true,
            editable: false,
            valueField: 'name',
            displayField: 'label',
            value: this.colorMeasure ? this.colorMeasure : null,
            queryMode: 'local',
            labelWidth: 105,
            width: 385,
            listeners: {
                change: function(combo, oldVal, newVal){
                    if(!this.suppressEvents){
                        this.hasChanges = true;
                    }
                },
                scope: this
            }
        });

        groupingItems.push(this.colorCombo);

        // Point Items

        var singlePointRadio = {
            xtype: 'radio',
            name: 'shapeType',
            inputValue: 'single',
            boxLabel: 'Single shape',
            checked: this.colorType ? this.colorType === 'single' : true,
            width: 150
        };

        var categoricalPointRadio = {
            xtype: 'radio',
            name: 'shapeType',
            id: 'shapeCategory',
            inputValue: 'measure',
            boxLabel: 'By category',
            checked: this.colorType ? this.colorType === 'measure' : false,
            width: 150
        };

        this.shapeRadioGroup = Ext4.create('Ext.form.RadioGroup', {
            fieldLabel: 'Point Shape',
            vertical: false,
            width: 300,
            columns: 2,
            items: [singlePointRadio, categoricalPointRadio],
            value: this.shapeType ? this.shapeType : 'single',
            listeners: {
                change: function(radioGroup, newVal){
                    if(!this.suppressEvents){
                        this.hasChanges = true;
                    }

                    this.pointCombo.setDisabled(newVal.shapeType === 'single');
                },
                scope: this
            }
        });

        groupingItems.push(this.shapeRadioGroup);

        this.pointCombo = Ext4.create('Ext.form.field.ComboBox', {
            fieldLabel: 'Point Category',
            name: 'pointMeasure',
            store: this.colorStore,
            disabled: this.shapeType ? this.shapeType === 'single' : true,
            editable: false,
            valueField: 'name',
            displayField: 'label',
            value: this.pointMeasure ? this.pointMeasure : null,
            queryMode: 'local',
            labelWidth: 105,
            width: 385,
            listeners: {
                change: function(combo, oldVal, newVal){
                    if(!this.suppressEvents){
                        this.hasChanges = true;
                    }
                },
                scope: this
            }
        });

        groupingItems.push(this.pointCombo);

        this.items = groupingItems;

        this.buttons = [{
            text: 'OK',
            handler: this.applyChangesButtonClicked,
             scope: this
        },{
            text: 'Cancel',
            handler: this.cancelChangesButtonClicked,
            scope: this
        }];

        this.callParent();
    },

    applyChangesButtonClicked: function() {
        this.fireEvent('closeOptionsWindow', false);
        this.checkForChangesAndFireEvents();
    },

    cancelChangesButtonClicked: function(){
        this.fireEvent('closeOptionsWindow', true);
    },

    checkForChangesAndFireEvents: function(){
        if(this.hasChanges){
            this.fireEvent('chartDefinitionChanged')
        }
        this.hasChanges = false;
    },

    getPanelOptionValues: function() {
        var values = {};

        if (this.getColorType() == "measure") {
            values.color = this.getColorMeasure();
        }

        if (this.getShapeType() == "measure") {
            values.shape = this.getPointMeasure();
        }

        return values;
    },

    restoreValues: function(initValues) {

        if(initValues.hasOwnProperty('colorType')){
            this.setColorType(initValues.colorType);
        }

        if(initValues.hasOwnProperty('colorMeasure')){
            this.setColorMeasure(initValues.colorMeasure);
        }

        if(initValues.hasOwnProperty('shapeType')){
            this.setShapeType(initValues.shapeType);
        }

        if(initValues.hasOwnProperty('pointMeasure')){
            this.setPointMeasure(initValues.pointMeasure);
        }

        this.hasChanges = false;
    },

    setPanelOptionValues: function(config){
        this.suppressEvents = true;

        if(config.color){
            this.setColorType('measure');
            this.setColorMeasure(config.color);
        }

        if(config.shape){
            this.setShapeType('measure');
            this.setPointMeasure(config.shape);
        }

        this.suppressEvents = false;
    },

    getStore: function(){
        return this.colorStore;
    },

    getColorType: function(){
        return this.colorTypeRadioGroup.getValue().colorType;
    },

    setColorType: function(value){
        this.colorTypeRadioGroup.setValue({colorType: value});
    },

    getColorMeasure: function(){
        return {
            name: this.colorCombo.getValue(),
            label: this.colorCombo.getRawValue()
        };
    },

    setColorMeasure: function(value){
        this.colorCombo.setValue(value.name);
    },

    getShapeType: function(){
        return this.shapeRadioGroup.getValue().shapeType;
    },

    setShapeType: function(value){
        this.shapeRadioGroup.setValue({shapeType: value});
    },

    getPointMeasure: function(){
        return {
            name: this.pointCombo.getValue(),
            label: this.pointCombo.getRawValue()
        };
    },

    setPointMeasure: function(value){
        // Have to set value.name because we store an object with name and label,
        // and name is the value field of the combo box.
        this.pointCombo.setValue(value.name);
    },

    loadStore: function(store){
        this.colorStore.removeAll();
        this.colorStore.loadRecords(store.getRange());
    }
});