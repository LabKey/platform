/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

LABKEY.requiresExt4Sandbox(true);

Ext4.onReady(function(){
    Ext4.define('LABKEY.Portal.WebPartPermissionsPanel', {
        extend: 'Ext.window.Window',

        constructor: function(config){
            Ext4.applyIf(config, {
                modal: true,
                width: 500,
                layout: 'fit',
                closeAction: 'destroy',
                title: 'Permissions',
                containerPath: LABKEY.container.path
            });
            this.callParent([config]);
        },

        initComponent : function() {
            this.buttons = ['->'];

            this.buttons.push({
                text: 'Save',
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
                html: "<div>This WebPart can be shown or hidden based on the user's permission</div>",
                margin: '0 0 15 0'
            });

            this.permissionCombo = Ext4.create('Ext.form.field.ComboBox', {
                store: this.getPermissionStore(),
                allowBlank: false,
                valueField: 'uniqueName',
                value: this.permission,
                displayField: 'name',
                fieldLabel: 'Required Permission',
                triggerAction: 'all',
                labelWidth: 150,
                editable: false
            });

            var currentFolderRadio = Ext4.create('Ext.form.field.Radio', {
                inputValue: 'current',
                name: 'checkPermission',
                checked: (this.containerPath == null || this.containerPath === LABKEY.container.path),
                boxLabel: 'Current Folder'
            });

            var otherFolderRadio = Ext4.create('Ext.form.field.Radio', {
                inputValue: 'other',
                name: 'checkPermission',
                checked: (this.containerPath != null && this.containerPath !== LABKEY.container.path),
                boxLabel: 'Choose Folder'
            });

            this.folderRadioGroup = Ext4.create('Ext.form.RadioGroup', {
                fieldLabel: 'Check Permission On',
                labelWidth: 150,
                items: [currentFolderRadio, otherFolderRadio],
                listeners: {
                    scope: this,
                    change: function(radioGroup, newValue){
                        this.folderTree.toggleCollapse();
                        if(newValue.checkPermission === 'other'){
                            this.otherFolderTextBox.show();
                        } else {
                            this.otherFolderTextBox.hide();
                        }
                    }
                }
            });

            this.otherFolderTextBox = Ext4.create('Ext.form.field.Text', {
                fieldClass: 'x-form-empty-field',
                readOnly: true,
                hidden: (!this.containerPath || this.containerPath === LABKEY.container.path),
                fieldLabel: 'Folder Path',
                value: (this.containerPath != null && this.containerPath !== LABKEY.container.path) ? this.containerPath : ''
            });

            return {
                xtype: 'panel',
                padding: 10,
                border: false,
                layout: 'form',
                items: [divContainer, this.permissionCombo, this.folderRadioGroup, this.otherFolderTextBox, this.getFolderTree()]
            };
        },

        getPermissionStore: function(){
            Ext4.define('LABKEY.Portal.WebPartPermissionModel', {
                extend: 'Ext.data.Model',
                fields: [
                    {name: 'name', type:'string'},
                    {name: 'uniqueName', type: 'string'},
                    {name: 'sourceModule', type: 'string'},
                    {name: 'type', type: 'string', defaultValue: 'other'}
                ]
            });

            var store = Ext4.create('Ext.data.Store', {
                model: 'LABKEY.Portal.WebPartPermissionModel',
                autoLoad: true,
                proxy: {
                    type: 'ajax',
                    url: LABKEY.ActionURL.buildURL('security', 'getRoles',LABKEY.container.path),
                    reader: {
                        type: 'json',
                        root: 'permissions'
                    }
                }
            });

            store.on('load', function(store, records){
                // Change type on Administrate, Read, and Insert so they appear at the top after grouping.
                store.findRecord('name', 'Administrate').set('type', 'base');
                store.findRecord('name', 'Read').set('type', 'base');
                store.findRecord('name', 'Insert').set('type', 'base');
                
                store.group('type');
                store.sort([{property: 'name', direction : 'ASC'}]);
            }, this);

            return store;
        },

        getFolderTreeStore: function(){
            Ext4.define('LABKEY.Portal.FolderTreeStore', {
                extend: 'Ext.data.Model',
                fields: [
                    {name: 'containerPath', type: 'string'},
                    {name: 'text', type: 'string'},
                    {name: 'expanded', type: 'boolean'},
                    {name: 'isProject', type: 'boolean'},
                    {name: 'id'}
                ]
            });

            this.treeStore = Ext4.create('Ext.data.TreeStore', {
                autoLoad: true,
                model: 'LABKEY.Portal.FolderTreeStore',
                root: {
                    id : '1',
                    text : 'LabKey Server Projects',
                    nodeType : 'async',
                    expanded : true,
                    containerPath: '/'
                },
                proxy: {
                    type: 'ajax',
                    url : LABKEY.ActionURL.buildURL('core', 'getExtContainerAdminTree.api'),
                    baseParams : {requiredPermission : 'org.labkey.api.security.permissions.AdminPermission', showContainerTabs: false}
                },
                sorters: [{
                    property: 'leaf',
                    direction: 'ASC'
                }, {
                    property: 'text',
                    direction: 'ASC'
                }]
            });

            return this.treeStore
        },

        getFolderTree: function(){
            this.folderTree = Ext4.create('Ext.tree.Panel', {
                rootVisible: false,
                store: this.getFolderTreeStore(),
                height: 150,
                autoScroll: true,
                header: false,
                animate: true,
                collapsible: true,
                collapseMode: 'mini',
                collapsed: (!this.containerPath || this.containerPath === LABKEY.container.path),
                listeners: {
                    scope: this,
                    select: function(tree, node){
                        this.otherFolderTextBox.setValue(node.data.containerPath);
                    }
                }
            });

            return this.folderTree;
        },

        getRequestValues: function(){
            var containerPath;

            if(this.folderRadioGroup.getValue().checkPermission === 'other'){
                containerPath = this.otherFolderTextBox.getValue();
            } else {
                containerPath = LABKEY.container.path
            }
            return {
                permission: this.permissionCombo.getValue(),
                containerPath: containerPath,
                webPartId: this.webPartId
            };
        },

        handleSave: function(){
            var requestObj = this.getRequestValues();

            if(requestObj.containerPath == null || requestObj.permission == null){
                return;
            }

//            LABKEY.ActionURL.buildURL('project', 'moveWebPartAsync', config.containerPath);
//            LABKEY.ActionURL.buildURL('project', 'setWebPartPermissions');

            Ext4.Ajax.request({
                url: LABKEY.ActionURL.buildURL('project', 'setWebPartPermissions'),
                jsonData: requestObj,
                scope: this,
                success: function(response){
                    var json = Ext4.JSON.decode(response.responseText);
                    console.log(json);
                    this.close();
                },
                failure: function(response){
                    var json = Ext4.JSON.decode(response.responseText);
                    console.log(json);
                }
            });

        },

        handleCancel: function(){
            this.close();
        }
    });
});