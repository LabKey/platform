/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.namespace("LABKEY.vis");

Ext4.tip.QuickTipManager.init();

Ext4.define('LABKEY.vis.YAxisOptionsPanel', {

    extend : 'Ext.form.Panel',

    constructor : function(config){
        Ext4.apply(config, {
            header: false,
            autoHeight: true,
            autoWidth: true,
            bodyStyle: 'padding: 5px',
            border: false,
            labelAlign: 'top',
            items: [],
            buttonAlign: 'right',
            buttons: []
        });

        // set axis defaults, if not a saved chart
        Ext4.applyIf(config.axis, {
            name: "y-axis",
            side: "left",
            scale: "linear",
            range: {type: "automatic"}
        });

        // track if the axis label is something other than the default
        if(config.axis.label){
            config.userEditedLabel = (config.axis.label == config.defaultLabel ? false : true);
        } else {
            config.userEditedLabel = false;
        }

        this.callParent([config]);

        this.addEvents('chartDefinitionChanged', 'closeOptionsWindow');
    },

    initComponent : function() {
        // the y-axis editor panel will be laid out with 2 columns
        var columnOneItems = [];
        var columnTwoItems = [];

        // track if the panel has changed in a way that would require a chart refresh
        this.hasChanges = false;

        this.scaleCombo = Ext4.create('Ext.form.field.ComboBox', {
            triggerAction: 'all',
            queryMode: 'local',
            store: Ext4.create('Ext.data.ArrayStore', {
                fields: ['value', 'display'],
                data: [['linear', 'Linear'], ['log', 'Log']]
            }),
            valueField: 'value',
            displayField: 'display',
            fieldLabel: 'Scale',
            value: this.axis.scale,
            forceSelection: true,
            editable: false,
            listeners: {
                scope: this,
                'select': function(cmp) {
                    this.axis.scale = cmp.getValue();
                    this.hasChanges = true;
                }
            }
        });
        columnOneItems.push(this.scaleCombo);

        this.labelTextField = Ext4.create('Ext.form.field.Text', {
            fieldLabel: 'Axis label',
            value: this.axis.label,
            width: 300,
            enableKeyEvents: true,
            listeners: {
                scope: this,
                'change': function(cmp, newVal, oldVal) {
                    this.userEditedLabel = true;
                    this.axis.label = newVal;
                    this.hasChanges = true;
                }
            }
        });
        this.labelTextField.addListener('keyUp', function(){
                this.userEditedLabel = true;
                this.axis.label = this.labelTextField.getValue();
                this.hasChanges = true;
            }, this, {buffer: 500});
        columnOneItems.push(this.labelTextField);

        this.rangeAutomaticRadio = Ext4.create('Ext.form.field.Radio', {
            fieldLabel: 'Range',
            labelAlign: 'top',
            inputValue: 'automatic',
            boxLabel: 'Automatic',
            checked: this.axis.range.type == "automatic",
            listeners: {
                scope: this,
                'change': function(field, checked){
                    // if checked, remove any manual axis min value
                    if(checked) {
                        this.axis.range.type = 'automatic';
                        this.setRangeFormOptions(this.axis.range.type);
                        this.hasChanges = true;
                    }
                }
            }
        });
        columnTwoItems.push(this.rangeAutomaticRadio);

        this.rangeManualRadio = Ext4.create('Ext.form.field.Radio', {
            inputValue: 'manual',
            boxLabel: 'Manual',
            width: 85,
            flex: 1,
            checked: this.axis.range.type == "manual",
            listeners: {
                scope: this,
                'change': function(field, checked){
                    // if checked, enable the min and max textfields and give min focus
                    if(checked) {
                        this.axis.range.type = 'manual';
                        this.setRangeFormOptions(this.axis.range.type);
                        this.hasChanges = true;
                    }
                }
            }
        });

        this.rangeMinNumberField = Ext4.create('Ext.form.field.Number', {
            emptyText: 'Min',
            selectOnFocus: true,
            enableKeyEvents: true,
            width: 75,
            flex: 1,
            disabled: this.axis.range.type == "automatic",
            value: this.axis.range.min,
            hideTrigger: true,
            mouseWheelEnabled: false
        });

        this.rangeMinNumberField.addListener('keyup', function(cmp){
            var newVal = cmp.getValue();
            // check to make sure that, if set, the min value is <= to max
            if(typeof this.axis.range.max == "number" && typeof newVal == "number" && newVal > this.axis.range.max){
                Ext4.Msg.alert("ERROR", "Range 'min' value must be less than or equal to 'max' value.", function(){
                    this.rangeMinNumberField.focus();
                }, this);
                return;
            }

            this.axis.range.min = newVal;
            this.hasChanges = true;
        }, this, {buffer:500});

        this.rangeMaxNumberField = Ext4.create('Ext.form.field.Number', {
            emptyText: 'Max',
            selectOnFocus: true,
            enableKeyEvents: true,
            width: 75,
            flex: 1,
            disabled: this.axis.range.type == "automatic",
            value: this.axis.range.max,
            hideTrigger: true,
            mouseWheelEnabled: false
        });

        this.rangeMaxNumberField.addListener('keyup', function(cmp){
            var newVal = cmp.getValue();
            // check to make sure that, if set, the max value is >= to min
            this.axis.range.max = newVal;
            var min = typeof this.axis.range.min == "number" ? this.axis.range.min : 0;
            if(typeof this.axis.range.min == "number" && typeof newVal == "number" && newVal < this.axis.range.min){
                Ext4.Msg.alert("ERROR", "Range 'max' value must be greater than or equal to 'min' value.", function(){
                    this.rangeMaxNumberField.focus();
                }, this);
                return;
            }

            this.hasChanges = true;
        }, this, {buffer: 500});

        columnTwoItems.push({
            xtype: 'fieldcontainer',
            layout: 'hbox',
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
                xtype: 'form',
                border: false,
                padding: 5,
                items: columnOneItems
            },{
                columnWidth: .5,
                xtype: 'form',
                border: false,
                padding: 5,
                items: columnTwoItems
            }]
        }];

        this.buttons = [{
            text: 'Apply',
            handler: function(){
                this.fireEvent('closeOptionsWindow');
                this.checkForChangesAndFireEvents();
            },
            scope: this
        }];

        this.callParent();
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
    },

    checkForChangesAndFireEvents : function(){
        if (this.hasChanges)
            this.fireEvent('chartDefinitionChanged', false);

        // reset the changes flag
        this.hasChanges = false;
    }
});
