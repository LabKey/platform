/*
 * Copyright (c) 2013-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.Query.SignSnapshotPanel', {
    extend: 'Ext.window.Window',

    border: false,

    constructor: function(config){

        config.containerPath = Ext4.htmlDecode(config.containerPath);

        Ext4.applyIf(config, {
            modal: true,
            width: 400,
            layout: 'fit',
            closeAction: 'destroy',
            title: 'Sign Data Snapshot'
        });
        this.callParent([config]);
    },

    initComponent : function() {
        this.buttons = ['->'];

        this.buttons.push({
            text: 'Submit',
            scope: this,
            handler: this.handleSave
        });

        this.buttons.push({
            text: 'Cancel',
            scope: this,
            handler: this.handleCancel
        });

        this.items = [this.getPanel()];

        this.callParent();
    },

    getPanel: function(){
        var divContainer = Ext4.create('Ext.container.Container', {
            html: "<div>Hi Folks!</div>",
            margin: '0 0 5px 0'
        });

        this.reason = Ext4.create('Ext.form.field.Text', {
            name: 'reason',
            allowBlank: true,
            fieldLabel: 'Reason',
            labelWidth: 70
        });

        return {
            xtype: 'panel',
            padding: 10,
            border: false,
            layout: 'form',
            items: [this.reason]
        };
    },

    getRequestValues: function(){
        return {
            reason: this.reason.getValue()
        };
    },

    handleSave: function(){
        if (this.params)
            this.url += '?' + Ext4.Object.toQueryString(this.params);
        var requestObj = this.getRequestValues();
        this.url += '&' + Ext4.Object.toQueryString(requestObj);
        window.location = this.url;
        this.close();
    },

    handleCancel: function(){
        this.close();
    }
});
