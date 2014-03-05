/*
 * Copyright (c) 2013-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.Security.ImpersonateUserPanel', {
    extend: 'Ext.window.Window',

    modal: true,
    border: false,
    width: 500,
    layout: 'fit',
    closeAction: 'destroy',
    title: 'Impersonate User',

    initComponent : function() {
        this.buttons = ['->'];

        this.buttons.push({
            text: 'Impersonate',
            scope: this,
            handler: this.handleImpersonate
        });

        this.buttons.push({
            text: 'Cancel',
            scope: this,
            handler: this.handleCancel
        });

        this.items = [this.getPanel()];

        this.callParent();
        this.on('show', function() {
            this.impersonateCombo.focus(false, 500);
        }, this)
    },

    getPanel: function(){
        var divContainer = Ext4.create('Ext.container.Container', {
            html: "<div>Select a user and click the 'Impersonate' button</div>",
            margin: '0 0 15 0'
        });

        this.impersonateCombo = Ext4.create('Ext.form.field.ComboBox', {
            store: this.getImpersonationStore(),
            name: 'impersonate',
            allowBlank: false,
            valueField: 'userId',
            displayField: 'displayName',
            fieldLabel: 'User',
            triggerAction: 'all',
            labelWidth: 50,
            queryMode: 'local',
            typeAhead: true,
            forceSelection: true
        });

        return {
            xtype: 'panel',
            padding: 10,
            border: false,
            layout: 'form',
            items: [divContainer, this.impersonateCombo]
        };
    },

    getImpersonationStore: function(){
        // define data models
        if (!Ext4.ModelManager.isRegistered('LABKEY.Security.ImpersonationUsers')) {
            Ext4.define('LABKEY.Security.ImpersonationUsers', {
                extend: 'Ext.data.Model',
                fields: [
                    {name: 'userId', type: 'integer'},
                    {name: 'displayName', type: 'string'}
                ]
            });
        }

        return Ext4.create('Ext.data.Store', {
            model: 'LABKEY.Security.ImpersonationUsers',
            autoLoad: true,
            proxy: {
                type: 'ajax',
                url: LABKEY.ActionURL.buildURL('user', 'getImpersonationUsers', LABKEY.container.path),
                reader: {
                    type: 'json',
                    root: 'users'
                }
            }
        });
    },

    handleImpersonate: function(){
        if (!this.impersonateCombo.isValid())
            return;

        var userId = this.impersonateCombo.getValue();

        window.location = LABKEY.ActionURL.buildURL('user', 'impersonateUser', null, {
            userId: userId,
            returnUrl: window.location,
            stayOnCurrentPage: true
        });
    },

    handleCancel: function(){
        this.close();
    }
});
