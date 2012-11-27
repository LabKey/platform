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
        // track if the title is defined
        config.userEditedTitle = (config.mainTitle != undefined);

        this.callParent([config]);

        this.addEvents(
            'chartDefinitionChanged',
            'closeOptionsWindow',
            'resetTitle'
        );
    },

    initComponent : function(){
        // track if the panel has changed
        this.hasChanges = false;

        this.chartTitleTextField = Ext4.create('Ext.form.field.Text', {
            name: 'chart-title-textfield', // for selenium testing
            hideLabel: true,
            value: this.mainTitle,
            flex: 1,
            enableKeyEvents: true,
            listeners: {
                scope: this,
                'change': function(cmp, newVal, oldVal) {
                    this.hasChanges = true;
                },
                'specialkey': this.specialKeyPressed
            }
        });
        this.chartTitleTextField.addListener('keyUp', function(){
            this.userEditedTitle = true;
            this.hasChanges = true;
            this.titleResetButton.enable();
        }, this, {buffer: 500});

        // button to reset a user defined label to the default
        this.titleResetButton = Ext4.create('Ext.Button', {
            disabled: !this.userEditedTitle,
            cls: 'revertMainTitle',
            iconCls:'iconReload',
            tooltip: 'Reset the label to the default value based on the selected measures.',
            handler: function() {
                this.titleResetButton.disable();
                this.userEditedTitle = false;
                this.fireEvent('resetTitle');
            },
            scope: this
        });

        this.items = [
            Ext4.create('Ext.form.Label', {text: 'Chart Title:'}),
            {
                xtype: 'fieldcontainer',
                layout: 'hbox',
                anchor: '100%',
                style: {marginTop: '5px'},
                items: [
                    this.chartTitleTextField,
                    this.titleResetButton
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
    
    applyChangesButtonClicked: function() {
        this.fireEvent('closeOptionsWindow', false);
        this.checkForChangesAndFireEvents();
    },

    cancelChangesButtonClicked: function(){
        this.fireEvent('closeOptionsWindow', true);
    },

    setMainTitle: function(newMainTitle, suspendEvents){
        if (!this.userEditedTitle)
        {
            if(suspendEvents){
                this.chartTitleTextField.suspendEvents(false);
            }
            this.chartTitleTextField.setValue(newMainTitle);
            this.chartTitleTextField.resumeEvents();
        }
    },

    getPanelOptionValues : function() {
        return {title: this.chartTitleTextField.getValue()};
    },

    restoreValues : function(initValues) {
        if (initValues.hasOwnProperty("title"))
            this.chartTitleTextField.setValue(initValues.title);

        this.hasChanges = false;
    },

    checkForChangesAndFireEvents : function() {
        if (this.hasChanges)
            this.fireEvent('chartDefinitionChanged', false);

        // reset the changes flags
        this.hasChanges = false;
    }
});
