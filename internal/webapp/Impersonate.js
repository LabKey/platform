/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
    defaultFocus: '#impersonate',

    initComponent : function() {
        this.buttons = ['->', {
            text: 'Cancel',
            scope: this,
            handler: this.close
        }, {
            text: 'Impersonate',
            scope: this,
            handler: this.handleImpersonateUser
        }];

        this.items = [this.getPanel()];

        this.callParent();
    },

    getPanel: function(){
        var instructions = LABKEY.Security.currentUser.isRootAdmin ?
            "As a site or application administrator, you can impersonate any user on the site." +
            (!LABKEY.Security.currentUser.isSystemAdmin ? " While impersonating you will not inherit the user's "
                + "site-level roles (e.g., Site Administrator, Developer)." : "") :

            "As a project administrator, you can impersonate any project user within this project. While impersonating you will be " +
            "restricted to this project and will not inherit the user's site-level roles (e.g., Site Administrator, Developer).";

        var divContainer = Ext4.create('Ext.container.Container', {
            html: "<div>" + instructions + "<br><br>Select a user from the list below and click the 'Impersonate' button</div>",
            margin: '0 0 15 0'
        });

        this.userCombo = Ext4.create('Ext.form.field.ComboBox', {
            store: this.getUserStore(),
            name: 'impersonate',
            itemId: 'impersonate',
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
                    '<tpl if="active">',
                        '<div class="x4-boundlist-item">{displayName:htmlEncode}</div>',
                    '<tpl else>',
                        '<div class="x4-boundlist-item" style="color: #999999;">{displayName:htmlEncode} (inactive)</div>',
                    '</tpl>',
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

    getUserStore: function(){
        // define data models
        if (!Ext4.ModelManager.isRegistered('LABKEY.Security.ImpersonationUsers')) {
            Ext4.define('LABKEY.Security.ImpersonationUsers', {
                extend: 'Ext.data.Model',
                fields: [
                    {name: 'userId', type: 'integer'},
                    {name: 'displayName', type: 'string'},
                    {name: 'active', type: 'boolean'}
                ]
            });
        }

        return Ext4.create('Ext.data.Store', {
            model: 'LABKEY.Security.ImpersonationUsers',
            autoLoad: true,
            proxy: {
                type: 'ajax',
                url: LABKEY.ActionURL.buildURL('user', 'getImpersonationUsers', LABKEY.user.isAdmin || LABKEY.project === undefined ? LABKEY.container.path : LABKEY.project.path),
                reader: {
                    type: 'json',
                    root: 'users'
                }
            }
        });
    },

    handleImpersonateUser: function() {
        if (!this.userCombo.isValid())
            return;

        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('user', 'impersonateUser.api'),
            method: 'POST',
            params: {
                userId: this.userCombo.getValue(),
                returnUrl: window.location
            },
            scope: this,
            success: function() {
                window.location.reload();
            },
            failure: function(response) {
                var jsonResp = LABKEY.Utils.decode(response.responseText);
                if (jsonResp && jsonResp.errors)
                {
                    var errorHTML = jsonResp.errors[0].message;
                    Ext4.Msg.alert('Error', errorHTML);
                }
            }
        });
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
    defaultFocus: '#impersonate',

    initComponent : function() {
        this.buttons = ['->',{
            text: 'Cancel',
            scope: this,
            handler: this.close
        }, {
            text: 'Impersonate',
            scope: this,
            handler: this.handleImpersonateGroup
        }];

        this.items = [this.getPanel()];

        this.callParent();
    },

    getPanel: function(){
        var instructions = LABKEY.Security.currentUser.isRootAdmin ?
            "As a site or application administrator, you can impersonate any site or project group." :
            "As a project administrator, you can impersonate any project group in within this project. While impersonating you will be restricted to this project.";

        var divContainer = Ext4.create('Ext.container.Container', {
            html: "<div>" + instructions + "<br><br>Select a group from the list below and click the 'Impersonate' button</div>",
            margin: '0 0 15 0'
        });

        this.groupCombo = Ext4.create('Ext.form.field.ComboBox', {
            store: this.getGroupStore(),
            name: 'impersonate',
            itemId: 'impersonate',
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

    getGroupStore: function(){
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
                url: LABKEY.ActionURL.buildURL('user', 'getImpersonationGroups', LABKEY.user.isAdmin || LABKEY.project === undefined ? LABKEY.container.path : LABKEY.project.path),
                reader: {
                    type: 'json',
                    root: 'groups'
                }
            }
        });
    },

    handleImpersonateGroup: function(){
        if (!this.groupCombo.isValid())
            return;

        var groupId = this.groupCombo.getValue();
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('user', 'impersonateGroup.api'),
            method: 'POST',
            params: {
                groupId: groupId,
                returnUrl: window.location
            },
            scope: this,
            success: function() {
                window.location.reload();
            },
            failure: function(response) {
                var jsonResp = LABKEY.Utils.decode(response.responseText);
                if (jsonResp && jsonResp.errors)
                {
                    var errorHTML = jsonResp.errors[0].message;
                    Ext4.Msg.alert('Error', errorHTML);
                }
            }
        });
    }
});

Ext4.define('LABKEY.Security.ImpersonateRoles', {
    extend: 'Ext.window.Window',
    modal: true,
    border: false,
    width: 475,
    layout: 'fit',
    closeAction: 'destroy',
    title: 'Impersonate Roles',

    initComponent : function() {
        this.impersonateButton = Ext4.create('Ext.Button', {
            text: 'Impersonate',
            disabled: true,
            scope: this,
            handler: this.handleImpersonateRole
        });

        this.buttons = ['->', {
            text: 'Cancel',
            scope: this,
            handler: this.close
        }, this.impersonateButton];

        this.items = [this.getPanel()];

        this.callParent();
    },

    getPanel: function(){
        var instructions = LABKEY.Security.currentUser.isRootAdmin ?
            "As a site or application administrator, you can impersonate one or more security roles. While impersonating you will have access to " +
                "the entire site, limited to the permissions provided by the selected roles(s)." :
            "As a project administrator, you can impersonate one or more security roles. While impersonating you will be restricted to this project.";

        var divContainer = Ext4.create('Ext.container.Container', {
            html: "<div>" + instructions + "<br><br>Select roles from the list below and click the 'Impersonate' button</div>",
            margin: '0 0 15 0'
        });

        this.roleGrid = Ext4.create('Ext.grid.Panel', {
            id           : 'ImpersonateRolesGrid',
            hideHeaders  : true,
            height       : 226, // Issue 31889
            name         : 'roles',
            store        : this.getRoleStore(),
            columns: [
                {text: 'Role', flex: 1, dataIndex: 'displayName'}
            ],
            selModel: Ext4.create('Ext.selection.CheckboxModel', {
                mode: 'SIMPLE'
            })
        });

        // Select any previously selected roles, present only in the "adjust impersonation" case
        this.roleGrid.store.on("load", function(store){
            sm = this.roleGrid.selModel;
            Ext4.each(store.data.items, function(item){
                if (item.data.selected){
                    sm.select(item.index, true);
                }
            });
        }, this);

        this.roleGrid.on("selectionChange", function(grid){
            var selected = grid.getSelection();
            var includesRead = false;

            for (var i = 0; i < selected.length; i++)
            {
                var record = selected[i].data;
                if (record.hasRead)
                {
                    includesRead = true;
                    break;
                }
            }

            // Enable/disable the "read permissions" warning if one or more roles are selected, but they don't include read permissions
            this.divWarning.setVisible(selected.length > 0 && !includesRead);

            // Enable/disable "Impersonate" button
            this.impersonateButton.setDisabled(0 === selected.length);
        }, this);

        this.divWarning = Ext4.create('Ext.container.Container', {
            html: "<div>Warning: The selected roles do not include read permissions; impersonating without read permissions is generally not useful.</div>",
            margin: '5 0 0 0',
            hidden: true
        });

        return {
            xtype: 'panel',
            padding: 10,
            border: false,
            layout: 'form',
            items: [divContainer, this.roleGrid, this.divWarning]
        };
    },

    getRoleStore: function(){
        // define data models
        if (!Ext4.ModelManager.isRegistered('LABKEY.Security.ImpersonationRoles')) {
            Ext4.define('LABKEY.Security.ImpersonationRoles', {
                extend: 'Ext.data.Model',
                fields: [
                    {name: 'roleName', type: 'string'},
                    {name: 'displayName', type: 'string'},
                    {name: 'hasRead', type: 'boolean'},
                    {name: 'selected', type: 'boolean'}  // True for previously selected roles, when already impersonating roles
                ]
            });
        }

        return Ext4.create('Ext.data.Store', {
            model: 'LABKEY.Security.ImpersonationRoles',
            autoLoad: true,
            proxy: {
                type: 'ajax',
                url: LABKEY.ActionURL.buildURL('user', 'getImpersonationRoles', LABKEY.user.isAdmin || LABKEY.project === undefined ? LABKEY.container.path : LABKEY.project.path),
                reader: {
                    type: 'json',
                    root: 'roles'
                }
            }
        });
    },

    handleImpersonateRole: function(){
        var roleNames = [];
        var selected = this.roleGrid.getSelectionModel().selected.items;

        for (var i = 0; i < selected.length; i++)
            roleNames.push(selected[i].data.roleName);

        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('user', 'impersonateRoles.api'),
            method: 'POST',
            params: {
                roleNames: roleNames,
                returnUrl: window.location
            },
            scope: this,
            success: function() {
                window.location.reload();
            },
            failure: function(response) {
                var jsonResp = LABKEY.Utils.decode(response.responseText);
                if (jsonResp && jsonResp.errors)
                {
                    var errorHTML = jsonResp.errors[0].message;
                    Ext4.Msg.alert('Error', errorHTML);
                }
            }
        });
    }
});
