/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.requiresExt4ClientAPI();

Ext4.namespace("LABKEY.vis");

Ext4.QuickTips.init();

Ext4.define('LABKEY.vis.ChartEditorStatisticsPanel', {

    extend : 'Ext.form.Panel',

    constructor : function(config){
        Ext4.apply(config, {
            header: false,
            autoHeight: true,
            autoWidth: true,
            border: false,
            bodyStyle: 'padding: 5px',
            labelAlign: 'top',
            items: [],
            buttonAlign: 'right',
            buttons: []
        });

        Ext4.applyIf(config, {
            displayIndividual: true,
            displayAggregate: false,
            errorBars: "None"
        });

        this.callParent([config]);

        this.addEvents(
            'chartDefinitionChanged',
            'closeOptionsWindow'
        );
    },

    initComponent : function(){
        // track if the panel has changed in a way that would require a chart/data refresh
        this.requireDataRefresh = false;

        this.displayIndividualCheckbox = Ext4.create('Ext.form.field.Checkbox', {
            boxLabel  : 'Show Individual Lines',
            name      : 'Show Individual Lines',
            checked   : this.displayIndividual,
            value     : this.displayIndividual,
            listeners : {
                change : function(cmp, checked){
                    this.requireDataRefresh = true;
                },
                scope : this
            }
        });

        this.displayAggregateCheckbox = Ext4.create('Ext.form.field.Checkbox', {
            boxLabel  : 'Show Mean',
            name      : 'Show Mean',
            checked   : this.displayAggregate,
            value     : this.displayAggregate,
            flex: 1,
            listeners : {
                change : function(cmp, checked){
                    // enable/disable the aggregate combo box accordingly
                    this.displayAggregateComboBox.setDisabled(!checked);
                    this.displayErrorComboBox.setDisabled(!checked);
                    if (!checked)
                        this.displayErrorComboBox.setValue("None");

                    this.requireDataRefresh = true;
                },
                scope : this
            }
        });

        this.displayErrorComboBox = Ext4.create('Ext.form.field.ComboBox', {
            triggerAction : 'all',
            mode          : 'local',
            store         : Ext4.create('Ext.data.ArrayStore', {
                   fields : ['value'],
                   data   : [['None'], ['SD'], ['SEM']]
            }),
            disabled      : !this.displayAggregate,
            forceSelection: 'true',
            editable: false,
            valueField    : 'value',
            displayField  : 'value',
            value         : this.errorBars,
            width         : 75,
            flex: 1,
            listeners     : {
                select    : function(){
                    this.requireDataRefresh = true;
                },
                scope     : this
            }
        });

        // combobox for selecting which aggregate to display when checkbox is selected
        this.displayAggregateComboBox = Ext4.create('Ext.form.field.ComboBox', {
            triggerAction : 'all',
            mode          : 'local',
            store         : Ext4.create('Ext.data.ArrayStore', {
                   fields : ['value'],
                   data   : [['Mean'], ['Count']]
            }),
            disabled      : !this.displayAggregate,
            hideLabel     : true,
            forceSelection: 'true',
            valueField    : 'value',
            displayField  : 'value',
            value         : 'Mean',
            width         : 75,
            listeners     : {
                select    : function(){
                    this.requireDataRefresh = true;
                },
                scope     : this
            }
        });

        this.items = [
            this.displayIndividualCheckbox,
            {
                xtype: 'fieldcontainer',
                layout: 'hbox',
                items: [
                    this.displayAggregateCheckbox,
                    this.displayErrorComboBox
                ]
            }
        ];

        this.buttons = [
            {
                text: 'Apply',
                handler: function(){
                    this.fireEvent('closeOptionsWindow');
                    this.checkForChangesAndFireEvents();
                },
                scope: this
            }
        ];

        this.callParent();
    },

    getDisplayIndividual : function() {
        return this.displayIndividualCheckbox.getValue();
    },

    getDisplayAggregate : function() {
        return this.displayAggregateCheckbox.getValue();
    },

    getAggregateType : function() {
        return this.displayAggregateComboBox.getValue();
    },

    getErrorBars : function() {
        return this.displayErrorComboBox.getValue();
    },

    checkForChangesAndFireEvents : function() {
        if (this.requireDataRefresh)
            this.fireEvent('chartDefinitionChanged', true);

        // reset the changes flags
        this.requireDataRefresh = false;
    }
});
