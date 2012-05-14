/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext.namespace("LABKEY.vis");

Ext.QuickTips.init();

LABKEY.vis.ChartEditorStatisticsPanel = Ext.extend(Ext.FormPanel, {
    constructor : function(config){
        Ext.apply(config, {
            header: false,
            autoHeight: true,
            autoWidth: true,
            border: false,
            labelAlign: 'top',
            items: [],
            buttonAlign: 'right',
            buttons: []
        });

        Ext.applyIf(config, {
            displayIndividual: true,
            displayAggregate: false,
            errorBars: "None"
        });

        this.addEvents(
            'chartDefinitionChanged',
            'closeOptionsWindow'
        );

        LABKEY.vis.ChartEditorStatisticsPanel.superclass.constructor.call(this, config);
    },

    initComponent : function(){
        // track if the panel has changed in a way that would require a chart/data refresh
        this.requireDataRefresh = false;

        this.displayIndividualCheckbox = new Ext.form.Checkbox({
            boxLabel  : 'Show Individual Lines',
            name      : 'Show Individual Lines',
            checked   : this.displayIndividual,
            value     : this.displayIndividual,
            listeners : {
                check : function(cmp, checked){
                    this.requireDataRefresh = true;
                },
                scope : this
            }
        });

        this.displayAggregateCheckbox = new Ext.form.Checkbox({
            boxLabel  : 'Show Mean',
            name      : 'Show Mean',
            checked   : this.displayAggregate,
            value     : this.displayAggregate,
            listeners : {
                check : function(cmp, checked){
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

        this.displayErrorComboBox = new Ext.form.ComboBox({
            triggerAction : 'all',
            mode          : 'local',
            store         : new Ext.data.ArrayStore({
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
            listeners     : {
                select    : function(cb){
                    this.requireDataRefresh = true;
                },
                scope     : this
            }
        });

        // combobox for selecting which aggregate to display when checkbox is selected
        this.displayAggregateComboBox = new Ext.form.ComboBox({
            triggerAction : 'all',
            mode          : 'local',
            store         : new Ext.data.ArrayStore({
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
                select    : function(cb){
                    this.requireDataRefresh = true;
                },
                scope     : this
            }
        });

        this.items = [
            this.displayIndividualCheckbox,
            {
                xtype: 'compositefield',
                hideLabel: true,
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

        LABKEY.vis.ChartEditorStatisticsPanel.superclass.initComponent.call(this);
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
