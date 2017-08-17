/*
 * Copyright (c) 2013-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.Query.SignSnapshotPanel', {
    extend: 'Ext.window.Window',

    autoShow: true,

    border: false,

    modal: true,

    width: 400,

    layout: 'fit',

    title: 'Sign Data Snapshot',

    url: undefined, /* required */

    emailInput: undefined,

    initComponent : function() {
        if (!this.url) {
            throw new Error('"url" is required when initializing a ' + this.$className);
        }

        this.containerPath = Ext4.htmlDecode(this.containerPath);

        this.buttons = ['->', {
            text: 'Cancel',
            scope: this,
            handler: function() {
                this.close();
            }
        },{
            text: 'Submit',
            scope: this,
            handler: this.handleSave
        }];

        this.items = [this.getPanel()];

        this.callParent();
    },

    getPanel: function() {
        this.errorMsg = Ext4.create('Ext.form.Label', {
            name: 'errorMsg',
            disabled: true,
            cls: 'labkey-error'
        });

        return {
            xtype: 'form',
            itemId: 'signature-form',
            padding: 10,
            border: false,
            items: [this.errorMsg, {
                name: 'email',
                allowBlank: false,
                fieldLabel: 'Email',
                value: this.emailInput,
                labelWidth: 70
            },{
                name: 'password',
                allowBlank: false,
                inputType: 'password',
                fieldLabel: 'Password',
                labelWidth: 70
            },{
                name: 'reason',
                allowBlank: false,
                fieldLabel: 'Reason',
                labelWidth: 70
            }]
        };
    },

    handleSave: function() {
        LABKEY.Ajax.request({
            url: this.url,
            method: 'POST',
            params: Ext4.apply({}, this.getComponent('signature-form').getForm().getValues(), this.params),
            success: LABKEY.Utils.getCallbackWrapper(function() {
                this.close();
                window.location = LABKEY.ActionURL.buildURL('query', 'executeQuery', this.containerPath, {
                    schemaName: 'compliance',
                    queryName: 'SignedSnapshot'
                });
            }, this),
            failure: LABKEY.Utils.getCallbackWrapper(function(response) {
                if (response && response.exception) {
                    this.errorMsg.setText(response.exception, true);
                    this.errorMsg.enable();
                }
            }, this)
        });
    }
});
