/*
 * Copyright (c) 2013-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.Portal.WebPartPermissionsPanel', {
        extend: 'Ext.window.Window',

        border: false,

        constructor: function(config){

            config.containerPath = Ext4.htmlDecode(config.containerPath);

            Ext4.applyIf(config, {
                modal: true,
                width: 500,
                layout: 'fit',
                closeAction: 'destroy',
                title: 'WebPart Permissions'
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
                html: "<div>This WebPart can be shown or hidden based on the user's permission.</div>",
                margin: '0 0 5px 0'
            });

            this.permissionCombo = Ext4.create('Ext.form.field.ComboBox', {
                store: this.getPermissionStore(),
                name: 'permission',
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
                        if(newValue.checkPermission === 'other'){
                            this.otherFolderTextBox.setDisabled(false);
                            this.folderTree.setDisabled(false);
                        } else {
                            this.otherFolderTextBox.setDisabled(true);
                            this.folderTree.setDisabled(true);
                        }
                    }
                }
            });

            this.otherFolderTextBox = Ext4.create('Ext.form.field.Text', {
                fieldClass: 'x-form-empty-field',
                name: 'permissionContainer',
                readOnly: true,
                disabled: (!this.containerPath || this.containerPath === LABKEY.container.path),
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
                store.findRecord('name', 'Update').set('type', 'base');
                
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
                disabled: (!this.containerPath || this.containerPath === LABKEY.container.path),
                listeners: {
                    scope: this,
                    load: function(folderTree){
                        if(this.containerPath && !this.pathExpanded){
                            this.pathExpanded = true;
                            this.expandAndSelectFolderPath(this.containerPath);
                        }
                    },
                    select: function(tree, node){
                        this.otherFolderTextBox.setValue(node.data.containerPath);
                    }
                }
            });

            return this.folderTree;
        },

        expandAndSelectFolderPath: function(path){
            var nodes = path.split('/');

            if(nodes[0] === ''){
                nodes.shift();
            }

            var expandNode = function(childNodes){
                if(nodes.length > 0){
                    for(var i = 0; i < childNodes.length; i++){
                        if(nodes[0] === Ext4.htmlDecode(childNodes[i].data.text)){
                            nodes.shift();
                            childNodes[i].expand(false, expandNode, this);
                            break;
                        }
                    }
                }
            };

            var child = this.folderTree.getRootNode().findChildBy(function(node){
                return nodes[0] === Ext4.htmlDecode(node.data.text);
            }, this);

            if(child){
                nodes.shift();
                child.expand(false, expandNode, this);
            }
        },

        getRequestValues: function(){
            var containerPath = null; // Null is used for current container.

            if(this.folderRadioGroup.getValue().checkPermission === 'other'){
                containerPath = this.otherFolderTextBox.getValue();
            }

            return {
                permission: this.permissionCombo.getValue(),
                containerPath: containerPath,
                webPartId: this.webPartId
            };
        },

        handleSave: function(){
            var requestObj = this.getRequestValues();

            if(requestObj.permission == null){
                return;
            }

            Ext4.Ajax.request({
                url: LABKEY.ActionURL.buildURL('project', 'setWebPartPermissions'),
                jsonData: requestObj,
                scope: this,
                success: function(response){
                    var json = Ext4.JSON.decode(response.responseText);
                    this.replaceHREF(json.permission, requestObj.containerPath);
                    this.close();
                },
                failure: function(response){
                    var json = Ext4.JSON.decode(response.responseText);
                }
            });

        },

        replaceHREF: function(perm, path){
            // Only wrap in quotes if not null
            if(path != null){
                path = "'" + Ext4.htmlEncode(path) + "'";
            }

            var query = Ext4.query('#permissions_'+this.webPartId),
                href =  "javascript:(function(){Ext4.create('LABKEY.Portal.WebPartPermissionsPanel', {" +
                        "webPartId: '" + this.webPartId + "'," +
                        "permission: '" + perm + "'," +
                        "containerPath: " + path +
                        "}).show();}())";

            if(query.length > 0){
                query[0].setAttribute('href', href);
            }
        },

        handleCancel: function(){
            this.close();
        }
    });
