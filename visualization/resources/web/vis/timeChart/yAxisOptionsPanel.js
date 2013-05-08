/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.vis.YAxisOptionsPanel', {

    extend : 'LABKEY.vis.BaseAxisOptionsPanel',

    constructor : function(config){
        // set axis defaults, if not a saved chart
        Ext4.applyIf(config.axis, {
            name: "y-axis",
            side: "left",
            scale: "linear",
            range: {type: "automatic"}
        });

        // track if the axis label is defined
        config.userEditedLabel = config.axis.label != undefined;

        this.callParent([config]);

        this.addEvents('chartDefinitionChanged', 'closeOptionsWindow', 'resetLabel');
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
            name: this.inputPrefix + "-axis-label-textfield",
            fieldLabel: 'Axis label',
            labelWidth: 75,
            value: this.axis.label,
            enableKeyEvents: true,
            flex: 1,
            listeners: {
                scope: this,
                'change': function(cmp, newVal, oldVal) {
                    this.hasChanges = true;
                },
                'specialkey': this.specialKeyPressed
            }
        });
        this.labelTextField.addListener('keyUp', function(){
            this.userEditedLabel = true;
            this.hasChanges = true;
            this.labelResetButton.enable();
        }, this, {buffer: 500});

        // button to reset a user defined label to the default based on the selected measures
        this.labelResetButton = Ext4.create('Ext.Button', {
            disabled: !this.userEditedLabel,
            iconCls:'iconReload',
            tooltip: 'Reset the label to the default value based on the selected measures.',
            handler: function() {
                this.labelResetButton.disable();
                this.userEditedLabel = false;
                this.fireEvent('resetLabel');
            },
            scope: this
        });

        this.rangeAutomaticRadio = Ext4.create('Ext.form.field.Radio', {
            name: this.inputPrefix + "axis_range",
            fieldLabel: 'Range',
            labelAlign: 'top',
            inputValue: 'automatic',
            boxLabel: this.multipleCharts ? 'Automatic across charts' : 'Automatic',
            width: 200,
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

        this.rangeAutomaticPerChartRadio = Ext4.create('Ext.form.field.Radio', {
            name: this.inputPrefix + "axis_range",
            inputValue: 'automatic_per_chart',
            boxLabel: 'Automatic within chart',
            width: 200,
            checked: this.axis.range.type == "automatic_per_chart",
            hidden: !this.multipleCharts,
            listeners: {
                scope: this,
                'change': function(field, checked){
                    // if checked, remove any manual axis min value
                    if(checked) {
                        this.setRangeMinMaxDisplay('automatic_per_chart');
                        this.hasChanges = true;
                    }
                }
            }
        });

        this.rangeManualRadio = Ext4.create('Ext.form.field.Radio', {
            name: this.inputPrefix + "axis_range",
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
            name: this.inputPrefix + "axis_rangemin",
            emptyText: 'Min',
            selectOnFocus: true,
            enableKeyEvents: true,
            width: 75,
            flex: 1,
            disabled: this.axis.range.type != "manual",
            value: this.axis.range.min,
            hideTrigger: true,
            mouseWheelEnabled: false,
            listeners: {
                scope: this,
                'change': function(){
                    this.hasChanges = true;
                },
                'specialkey': this.specialKeyPressed
            }
        });

        this.rangeMaxNumberField = Ext4.create('Ext.form.field.Number', {
            name: this.inputPrefix + "axis_rangemax",
            emptyText: 'Max',
            selectOnFocus: true,
            enableKeyEvents: true,
            width: 75,
            flex: 1,
            disabled: this.axis.range.type != "manual",
            value: this.axis.range.max,
            hideTrigger: true,
            mouseWheelEnabled: false,
            listeners: {
                scope: this,
                'change': function(){
                    this.hasChanges = true;
                },
                'specialkey': this.specialKeyPressed
            }
        });

        this.items = [
            {
                xtype: 'fieldcontainer',
                layout: 'hbox',
                anchor: '100%',
                items: [
                    this.labelTextField,
                    this.labelResetButton
                ]
            },
            this.scaleCombo,
            this.rangeAutomaticRadio,
            this.rangeAutomaticPerChartRadio,
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

    setLabel: function(newLabel){
        if (!this.userEditedLabel)
        {
            this.labelTextField.setValue(newLabel);
        }
    },

    getPanelOptionValues : function() {
        var values = {
            side : this.axis.side,
            name : "y-axis",
            scale : this.scaleCombo.getValue(),
            range : {
                type : (this.rangeAutomaticRadio.checked ? this.rangeAutomaticRadio.inputValue
                        : (this.rangeAutomaticPerChartRadio.checked ? this.rangeAutomaticPerChartRadio.inputValue
                        : this.rangeManualRadio.inputValue))
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

    restoreValues : function(initValues) {
        if (initValues.hasOwnProperty("label"))
            this.labelTextField.setValue(initValues.label);
        if (initValues.hasOwnProperty("scale"))
            this.scaleCombo.setValue(initValues.scale);
        if (initValues.hasOwnProperty("range") && initValues.range.hasOwnProperty("type"))
        {
            this.restoreRangeRadioValues(initValues.range);
        }

        this.hasChanges = false;
    },

    checkForChangesAndFireEvents : function(){
        if (this.hasChanges)
            this.fireEvent('chartDefinitionChanged', false);

        // reset the changes flag
        this.hasChanges = false;
    }
});
