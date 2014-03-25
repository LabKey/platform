/*
 * Copyright (c) 2013-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.Security.ImpersonateUser', {
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
            this.userCombo.focus(false, 500);
        }, this)
    },

    getPanel: function(){
        var instructions = LABKEY.Security.currentUser.isSystemAdmin ?
            "As a site administrator, you can impersonate any user on the site." :

            "As a project administrator, you can impersonate any project user within this project. While impersonating you will be " +
            "restricted to this project and will not inherit the user's site-level roles (e.g., Site Administrator, Developer).";

        var divContainer = Ext4.create('Ext.container.Container', {
            html: "<div>" + instructions + "<br><br>Select a user from the list below and click the 'Impersonate' button</div>",
            margin: '0 0 15 0'
        });

        this.userCombo = Ext4.create('Ext.form.field.ComboBox', {
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
            forceSelection: true,
            tpl: Ext4.create('Ext.XTemplate',
                '<tpl for=".">',
                    '<div class="x4-boundlist-item">{displayName:htmlEncode}</div>',
                '</tpl>')
        });

        return {
            xtype: 'panel',
            padding: 10,
            border: false,
            layout: 'form',
            items: [divContainer, this.userCombo]
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
        if (!this.userCombo.isValid())
            return;

        var userId = this.userCombo.getValue();
        Ext4.Ajax.request({
            url: LABKEY.ActionURL.buildURL('user', 'impersonateUser'),
            method: 'POST',
            params: {
                userId: userId,
                returnUrl: window.location
            },
            scope: this,
            success: function(response){
                location = location;
            },
            failure: function(response){
                var jsonResp = LABKEY.ExtAdapter.decode(response.responseText);
                if (jsonResp && jsonResp.errors)
                {
                    var errorHTML = jsonResp.errors[0].message;
                    LABKEY.ExtAdapter.Msg.alert('Error', errorHTML);
                }
            }
        });
    },

    handleCancel: function(){
        this.close();
    }
});

Ext4.define('LABKEY.Security.ImpersonateGroup', {
    extend: 'Ext.window.Window',
    modal: true,
    border: false,
    width: 500,
    layout: 'fit',
    closeAction: 'destroy',
    title: 'Impersonate Group',

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
            this.groupCombo.focus(false, 500);
        }, this)
    },

    getPanel: function(){
        var instructions = LABKEY.Security.currentUser.isSystemAdmin ?
            "As a site administrator, you can impersonate any site or project group." :
            "As a project administrator, you can impersonate any project group in within this project. While impersonating you will be restricted to this project.";

        var divContainer = Ext4.create('Ext.container.Container', {
            html: "<div>" + instructions + "<br><br>Select a group from the list below and click the 'Impersonate' button</div>",
            margin: '0 0 15 0'
        });

        this.groupCombo = Ext4.create('Ext.form.field.ComboBox', {
            store: this.getImpersonationStore(),
            name: 'impersonate',
            allowBlank: false,
            valueField: 'groupId',
            displayField: 'displayName',
            fieldLabel: 'Group',
            triggerAction: 'all',
            labelWidth: 50,
            queryMode: 'local',
            typeAhead: true,
            forceSelection: true,
            tpl: Ext4.create('Ext.XTemplate',
                '<tpl for=".">',
                    '<div class="x4-boundlist-item">{displayName:htmlEncode}</div>',
                '</tpl>')
        });

        return {
            xtype: 'panel',
            padding: 10,
            border: false,
            layout: 'form',
            items: [divContainer, this.groupCombo]
        };
    },

    getImpersonationStore: function(){
        // define data models
        if (!Ext4.ModelManager.isRegistered('LABKEY.Security.ImpersonationGroups')) {
            Ext4.define('LABKEY.Security.ImpersonationGroups', {
                extend: 'Ext.data.Model',
                fields: [
                    {name: 'groupId', type: 'integer'},
                    {name: 'displayName', type: 'string'}
                ]
            });
        }

        return Ext4.create('Ext.data.Store', {
            model: 'LABKEY.Security.ImpersonationGroups',
            autoLoad: true,
            proxy: {
                type: 'ajax',
                url: LABKEY.ActionURL.buildURL('user', 'getImpersonationGroups', LABKEY.container.path),
                reader: {
                    type: 'json',
                    root: 'groups'
                }
            }
        });
    },

    handleImpersonate: function(){
        if (!this.groupCombo.isValid())
            return;

        var groupId = this.groupCombo.getValue();
        Ext4.Ajax.request({
            url: LABKEY.ActionURL.buildURL('user', 'impersonateGroup'),
            method: 'POST',
            params: {
                groupId: groupId,
                returnUrl: window.location
            },
            scope: this,
            success: function(response){
                location = location;
            },
            failure: function(response){
                var jsonResp = LABKEY.ExtAdapter.decode(response.responseText);
                if (jsonResp && jsonResp.errors)
                {
                    var errorHTML = jsonResp.errors[0].message;
                    LABKEY.ExtAdapter.Msg.alert('Error', errorHTML);
                }
            }
        });
    },

    handleCancel: function(){
        this.close();
    }
});

// TODO: Message for no read scenario
// TODO: Fix up combo?
Ext4.define('LABKEY.Security.ImpersonateRoles', {
    extend: 'Ext.window.Window',
    modal: true,
    border: false,
    width: 500,
    layout: 'fit',
    closeAction: 'destroy',
    title: 'Impersonate Roles',

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
            this.roleCombo.focus(false, 500);
        }, this)
    },

    getPanel: function(){
        var instructions = LABKEY.Security.currentUser.isSystemAdmin ?
            "As a site administrator, you can impersonate one or more security roles. While impersonating you will have access to " +
                "the entire site, limited to the permissions provided by the selected roles(s)." :
            "As a project administrator, you can impersonate one or more security roles. While impersonating you will be restricted to this project.";

        var divContainer = Ext4.create('Ext.container.Container', {
            html: "<div>" + instructions + "<br><br>Select roles from the list below and click the 'Impersonate' button</div>",
            margin: '0 0 15 0'
        });

//        this.impersonateCombo = Ext4.create('Ext.form.field.ComboBox', {
//            store: this.getRoleStore(),
//            name: 'impersonate',
//            allowBlank: false,
//            valueField: 'roleName',
//            displayField: 'displayName',
//            fieldLabel: 'Role',
//            triggerAction: 'all',
//            labelWidth: 50,
//            queryMode: 'local',
//            typeAhead: true,
//            forceSelection: true,
//            tpl: Ext4.create('Ext.XTemplate',
//                '<tpl for=".">',
//                    '<div class="x4-boundlist-item">{displayName:htmlEncode}</div>',
//                '</tpl>')
//        });

        this.roleCombo = new Ext4.ux.CheckCombo({
            id : 'ImpersonateRolesCombo',
            fieldLabel  : 'Roles',
            name        : 'role',
            allowBlank: false,
            store       : this.getRoleStore(),
            queryMode   : 'local',
            valueField: 'roleName',
            displayField: 'displayName',
            editable    : false
        });

        return {
            xtype: 'panel',
            padding: 10,
            border: false,
            layout: 'form',
            items: [divContainer, this.roleCombo]
        };
    },

    getRoleStore: function(){
        // define data models
        if (!Ext4.ModelManager.isRegistered('LABKEY.Security.ImpersonationRoles')) {
            Ext4.define('LABKEY.Security.ImpersonationRoles', {
                extend: 'Ext.data.Model',
                fields: [
                    {name: 'roleName', type: 'string'},
                    {name: 'displayName', type: 'string'}
                ]
            });
        }

        return Ext4.create('Ext.data.Store', {
            model: 'LABKEY.Security.ImpersonationRoles',
            autoLoad: true,
            proxy: {
                type: 'ajax',
                url: LABKEY.ActionURL.buildURL('user', 'getImpersonationRoles', LABKEY.container.path),
                reader: {
                    type: 'json',
                    root: 'roles'
                }
            }
        });
    },

    handleImpersonate: function(){
        if (!this.roleCombo.isValid())
            return;

        var roleNames = this.roleCombo.getValue();
        Ext4.Ajax.request({
            url: LABKEY.ActionURL.buildURL('user', 'impersonateRoles'),
            method: 'POST',
            params: {
                roleNames: roleNames,
                returnUrl: window.location
            },
            scope: this,
            success: function(response){
                location = location;
            },
            failure: function(response){
                var jsonResp = LABKEY.ExtAdapter.decode(response.responseText);
                if (jsonResp && jsonResp.errors)
                {
                    var errorHTML = jsonResp.errors[0].message;
                    LABKEY.ExtAdapter.Msg.alert('Error', errorHTML);
                }
            }
        });
    },

    handleCancel: function(){
        this.close();
    }
});
