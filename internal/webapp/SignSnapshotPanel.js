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
        this.errorMsg = Ext4.create('Ext.form.Label', {
            name: 'errorMsg',
            disabled: true,
            cls: 'labkey-error'
        });
        this.email = Ext4.create('Ext.form.field.Text', {
            name: 'email',
            allowBlank: false,
            fieldLabel: 'Email',
            value: this.emailInput,
            labelWidth: 70
        });
        this.password = Ext4.create('Ext.form.field.Text', {
            name: 'password',
            allowBlank: false,
            inputType: 'password',
            fieldLabel: 'Password',
            labelWidth: 70
        });
        this.reason = Ext4.create('Ext.form.field.Text', {
            name: 'reason',
            allowBlank: false,
            fieldLabel: 'Reason',
            labelWidth: 70
        });

        return {
            xtype: 'panel',
            padding: 10,
            border: false,
            layout: 'form',
            items: [
                this.errorMsg,
                this.email,
                this.password,
                this.reason
            ]
        };
    },

    handleSave: function(){
        var params = this.params ? this.params : {};
        params.reason = this.reason.getValue();
        params.email = this.email.getValue();
        params.password = this.password.getValue();
        params['X-LABKEY-CSRF'] = this['X-LABKEY-CSRF'];
//                urlHash: document.getElementById('urlhash'),

        LABKEY.Ajax.request({
            url: this.url,
            method: 'POST',
            params: params,
            success: LABKEY.Utils.getCallbackWrapper(function (response) {
                this.close();
                window.location = LABKEY.ActionURL.buildURL('query', 'executeQuery', this.containerPath, {
                    schemaName: 'compliance',
                    queryName: 'SignedSnapshot'
                });
            }, this),
            failure: LABKEY.Utils.getCallbackWrapper(function (response) {
                if(response && response.exception){
                    this.errorMsg.setText(response.exception, true);
                    this.errorMsg.enable();
                }
            }, this)
        });
    },

    handleCancel: function(){
        this.close();
    }
});
