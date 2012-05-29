/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.namespace("LABKEY.vis");

Ext4.tip.QuickTipManager.init();

Ext4.define('LABKEY.vis.MainTitleOptionsPanel', {

    extend : 'LABKEY.vis.GenericOptionsPanel',

    constructor : function(config){
        // track if the title is something other than the default
        config.userEditedTitle = (config.mainTitle ? true : false);

        this.callParent([config]);

        this.addEvents(
            'chartDefinitionChanged',
            'closeOptionsWindow'
        );
    },

    initComponent : function(){
        // track if the panel has changed
        this.hasChanges = false;

        this.chartTitleTextField = Ext4.create('Ext.form.field.Text', {
            name: 'chart-title-textfield',
            labelAlign: 'top',
            fieldLabel: 'Chart Title',
            value: this.mainTitle,
            anchor: '100%',
            enableKeyEvents: true,
            listeners: {
                scope: this,
                'change': function(cmp, newVal, oldVal) {
                    this.hasChanges = true;
                }
            }
        });
        this.chartTitleTextField.addListener('keyUp', function(){
            this.userEditedTitle = true;
            this.hasChanges = true;
        }, this, {buffer: 500});

        this.items = [this.chartTitleTextField];

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

    setMainTitle: function(newMainTitle){
        if (!this.userEditedTitle)
        {
            this.chartTitleTextField.setValue(newMainTitle);
        }
    },

    getPanelOptionValues : function() {
        return {title: this.chartTitleTextField.getValue()};
    },

    checkForChangesAndFireEvents : function() {
        if (this.hasChanges)
            this.fireEvent('chartDefinitionChanged', false);

        // reset the changes flags
        this.hasChanges = false;
    }
});
