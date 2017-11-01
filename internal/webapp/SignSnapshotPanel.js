/*
 * Copyright (c) 2017 LabKey Corporation
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
            defaultType: 'textfield',
            defaults: {
                allowBlank: false,
                anchor: '100%',
                labelWidth: 70
            },
            items: [this.errorMsg, {
                name: 'email',
                fieldLabel: 'Email',
                value: this.emailInput
            },{
                name: 'password',
                inputType: 'password',
                fieldLabel: 'Password'
            },{
                name: 'reason',
                fieldLabel: 'Reason'
            }]
        };
    },

    handleSave: function() {
        LABKEY.Ajax.request({
            url: this.url,
            method: 'POST',
            params: Ext4.apply({}, this.getComponent('signature-form').getForm().getValues(), this.params),
            success: LABKEY.Utils.getCallbackWrapper(function(result) {
                this.close();
                window.location = LABKEY.ActionURL.buildURL('query', 'detailsQueryRow', this.containerPath, {
                    schemaName: 'compliance',
                    queryName: 'SignedSnapshot',
                    rowId: result.rowId
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
