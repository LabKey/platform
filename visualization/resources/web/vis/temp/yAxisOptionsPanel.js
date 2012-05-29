/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.namespace("LABKEY.vis");

Ext4.tip.QuickTipManager.init();

Ext4.define('LABKEY.vis.YAxisOptionsPanel', {

    extend : 'LABKEY.vis.GenericOptionsPanel',

    constructor : function(config){
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
            labelWidth: 75,
            width: 165,
            value: this.axis.scale,
            forceSelection: true,
            editable: false,
            listeners: {
                scope: this,
                'select': function(cmp) {
                    this.hasChanges = true;
                }
            }
        });

        this.labelTextField = Ext4.create('Ext.form.field.Text', {
            fieldLabel: 'Axis label',
            labelWidth: 75,
            anchor: '100%',
            value: this.axis.label,
            enableKeyEvents: true,
            listeners: {
                scope: this,
                'change': function(cmp, newVal, oldVal) {
//                    this.userEditedLabel = true;
                    this.hasChanges = true;
                }
            }
        });
        this.labelTextField.addListener('keyUp', function(){
                this.userEditedLabel = true;
                this.hasChanges = true;
            }, this, {buffer: 500});

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
                        this.setRangeMinMaxDisplay('automatic');
                        this.hasChanges = true;
                    }
                }
            }
        });

        this.rangeManualRadio = Ext4.create('Ext.form.field.Radio', {
            fieldLabel: 'Range',
            hideLabel: true,
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
                        this.setRangeMinMaxDisplay('manual');
                        this.hasChanges = true;
                    }
                }
            }
        });

        this.rangeMinNumberField = Ext4.create('Ext.form.field.Number', {
            name: 'yaxis_rangemin', // for selenium test usage
            emptyText: 'Min',
            selectOnFocus: true,
            enableKeyEvents: true,
            width: 75,
            flex: 1,
            disabled: this.axis.range.type == "automatic",
            value: this.axis.range.min,
            hideTrigger: true,
            mouseWheelEnabled: false,
            listeners: {
                scope: this,
                'change': function(){
                    this.hasChanges = true;
                }
            }
        });

        this.rangeMaxNumberField = Ext4.create('Ext.form.field.Number', {
            name: 'yaxis_rangemax', // for selenium test usage
            emptyText: 'Max',
            selectOnFocus: true,
            enableKeyEvents: true,
            width: 75,
            flex: 1,
            disabled: this.axis.range.type == "automatic",
            value: this.axis.range.max,
            hideTrigger: true,
            mouseWheelEnabled: false,
            listeners: {
                scope: this,
                'change': function(){
                    this.hasChanges = true;
                }
            }
        });

        this.items = [
            this.labelTextField,
            this.scaleCombo,
            this.rangeAutomaticRadio,
            {
                xtype: 'fieldcontainer',
                layout: 'hbox',
                items: [
                    this.rangeManualRadio,
                    this.rangeMinNumberField,
                    this.rangeMaxNumberField
                ]
            }
        ];

        this.buttons = [{
            text: 'Apply',
            handler: function(){
                // check to make sure that, if set, the max value is >= to min
                var maxVal = this.rangeMaxNumberField.getValue();
                var minVal = this.rangeMinNumberField.getValue();
                if (this.rangeManualRadio.checked && typeof minVal == "number" && typeof maxVal == "number" && maxVal < minVal)
                {
                    Ext4.Msg.alert("ERROR", "Range 'max' value must be greater than or equal to 'min' value.", function(){
                        this.rangeMaxNumberField.focus();
                    }, this);
                    return;
                }
                
                this.fireEvent('closeOptionsWindow');
                this.checkForChangesAndFireEvents();
            },
            scope: this
        }];

        this.callParent();
    },

    setLabel: function(newLabel){
        if (!this.userEditedLabel)
        {
            this.labelTextField.setValue(newLabel);
        }
    },

    setRangeMinMaxDisplay: function(type){
        if (type == 'manual')
        {
            this.rangeMinNumberField.enable();
            this.rangeMaxNumberField.enable();
            this.rangeMinNumberField.focus();
        }
        else if (type == 'automatic')
        {
            this.rangeMinNumberField.disable();
            this.rangeMinNumberField.setValue("");

            this.rangeMaxNumberField.disable();
            this.rangeMaxNumberField.setValue("");
        }
    },

    getPanelOptionValues : function() {
        var values = {
            side : this.axis.side,
            name : "y-axis",
            scale : this.scaleCombo.getValue(),
            range : {
                type : this.rangeAutomaticRadio.checked ? this.rangeAutomaticRadio.inputValue : this.rangeManualRadio.inputValue
            },
            label : this.labelTextField.getValue()
        };

        if (this.rangeManualRadio.checked)
        {
            values.range.min = this.rangeMinNumberField.getValue();
            values.range.max = this.rangeMaxNumberField.getValue();
        }

        return values;
    },

    checkForChangesAndFireEvents : function(){
        if (this.hasChanges)
            this.fireEvent('chartDefinitionChanged', false);

        // reset the changes flag
        this.hasChanges = false;
    }
});
