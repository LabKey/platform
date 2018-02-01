/*
 * Copyright (c) 2012-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('Security.panel.PermissionEditor', {

    extend : 'Ext.panel.Panel',

    initComponent : function() {

        if (!this.securityCache) {
            console.error('Configuration Error: A securityCache must be supplied to a Security.panel.PermissionEditor.');
            this.items = [];
            this.callParent(arguments);
            return;
        }

        Ext4.apply(this, {
            layout: 'border',
            bodyStyle: 'background-color: transparent;',
            border: false,
            items: this.getItems()
        });

        this.callParent();
    },

    getItems : function() {

        var items = [ this.getBtnPanel(), this.getTabPanel()];

        if (!this.isRoot && this.treeConfig) {

            items.push({
                xtype : 'treepanel',
                title : 'Folders',
                cls   : 'themed-panel',
                store : {
                    xtype    : 'tree',
                    autoLoad : true,
                    proxy    : {
                        type : 'ajax',
                        url  : LABKEY.ActionURL.buildURL('core', 'getExtSecurityContainerTree.api'),
                        extraParams : {
                            requiredPermission : this.treeConfig.requiredPermission,
                            showContainerTabs : this.treeConfig.showContainerTabs
                        }
                    },
                    root : {
                        id : this.treeConfig.project.id,
                        expanded   : true,
                        expandable : true,
                        text       : Ext4.String.htmlEncode(this.treeConfig.project.name),
                        href       : Ext4.String.htmlEncode(this.treeConfig.project.securityHref)
                    }
                },
                enableDrag : false,
                useArrows  : true,
                autoScroll : true,
                width      : 220,
                region     : 'west',
                split      : true,
                border     : true,
                listeners  : {
                    load : function(store, node) {
                        Ext4.defer(function() {
                            // if we don't have a selected tree node, select the root node
                            var target = store.getRootNode().findChild('cls', "tree-node-selected", true);
                            if (!target)
                                store.getRootNode().set('cls', "tree-node-selected");
                        }, 100);
                    }
                }
            });

        }

        return items;
    },

    getBtnPanel : function() {

        return Ext4.create('Ext.Panel', {
            region : 'north',
            border: false,
            style : 'padding: 5px;',
            defaults: {
                style: 'margin-right: 10px;'
            },
            items : [{
                xtype : 'button',
                text : 'Save and Finish',
                handler : this.onSaveFinish,
                scope : this
            },{
                xtype : 'button',
                text : 'Save',
                handler : this.onSave,
                scope : this
            },{
                xtype : 'button',
                text : 'Cancel',
                handler : this.onCancel,
                scope : this
            }],
            scope : this
        });

    },

    onSaveFinish : function() {
        this.onSaveConfirm(this.onCancel);
    },

    onSave : function() {
        this.onSaveConfirm();
    },

    onSaveConfirm : function(onSuccess) {
        var policyEditor = this.getPolicyEditor(),
            me = this;

        policyEditor.save(false, function(response) {
            // check if the response succeeded or has a confirmation message to show
            if (!response.success && response.needsConfirmation) {
                Ext4.Msg.confirm("Confirm", response.message, function (btnId) {
                    if (btnId === 'yes') {
                        this.policy.confirm = true;
                        me.onSaveConfirm(onSuccess);
                    }
                    else {
                        policyEditor.getEl().unmask();
                    }
                }, this);
            }
            // success
            else {
                if (Ext4.isFunction(onSuccess)) {
                    onSuccess.call(this);
                }
                else {
                    policyEditor.saveSuccess();
                }
            }
        });
    },

    onCancel : function() {
        LABKEY.setSubmit(true);
        window.location = this.doneURL;
    },

    getTabPanel : function() {

        if (this.tabPanel)
            return this.tabPanel;

        this.tabPanel = Ext4.create('Ext.tab.Panel', {
            xtype      : 'tabpanel',
            region     : 'center',
            activeTab  : this.resolveActiveTab(),
            autoHeight : false,
            border     : true,
            defaults   : {style : {padding:'5px'}},
            plain      : true,
            items      : this.getTabItems()
        });

        return this.tabPanel;
    },

    resolveActiveTab : function() {

        var valids = {
            'permissions'  : true,
            'sitegroups'   : true,
            'projectgroups': true
        };

        var params = LABKEY.ActionURL.getParameters();
        if (params['t'] && valids[params['t']]) {
            return params['t'];
        }
        return 0;
    },

    getTabItems : function() {

        var items = [this.getPolicyEditor()];

        // Not shown at root level since a root group is a site group
        if ((this.isRootUserManager || this.isProjectAdmin) && !this.isSiteRoot) {
            items.push(this.getGroupTabConfig(this.securityCache.projectId, true));
        }

        if (this.isRootUserManager) {
            items.push(this.getGroupTabConfig('', true));
        }

        return items;
    },

    getPolicyEditor : function() {
        if (this.policyEditor)
            return this.policyEditor;

        this.policyEditor = Ext4.create('Security.panel.PolicyEditor', {
            title  : 'Permissions',
            itemId : 'permissions',
            cache  : this.securityCache,
            border : false,
            isRootUserManager : this.isRootUserManager,
            isProjectAdmin : this.isProjectAdmin,
            canInherit : this.canInherit,
            resourceId : LABKEY.container.id,
            globalPolicy : true,
            doneURL: this.doneURL
        });

        return this.policyEditor;
    },

    getGroupTabConfig : function(projectId, canEdit) {

        var items = [], groupList;

        var showPopup = function(group) {
            var edit = (!group.Container && LABKEY.Security.currentUser.isRootAdmin) || (group.Container && LABKEY.Security.currentUser.isAdmin);
            var w = Ext4.create('Security.window.UserInfoPopup', {
                userId  : group.UserId,
                cache   : this.securityCache,
                policy  : this.getPolicyEditor().getPolicy(),
                modal   : true,
                canEdit : edit,
                listeners: {
                    close: function() {
                        if (groupList)
                            groupList.onDataChanged();
                    },
                    scope: this
                },
                scope : this
            });
            w.show();
        };

        if (canEdit) {
            var btnId = Ext4.id();
            items.push({
                layout : 'hbox',
                border : false,
                style  : 'padding-bottom: 10px;',
                items  : [{
                    xtype     : 'textfield',
                    name      : projectId === '' ? 'sitegroupsname' : 'projectgroupsname',
                    emptyText : 'New group name',
                    width     : 396,
                    padding   : '10 10 10 0',
                    listeners : {
                        afterrender : {
                            fn: function(field) {
                                new Ext4.util.KeyMap(field.getEl(), [{
                                    key: Ext4.EventObject.ENTER,
                                    fn: function() {
                                        this.onCreateGroup(Ext4.getCmp(btnId), showPopup);
                                    },
                                    scope: this
                                }]);
                            },
                            single: true,
                            scope: this
                        }
                    }
                },{
                    id    : btnId,
                    xtype : 'button',
                    text  : 'Create New Group',
                    margin  : '10 0 0 0',
                    projectId : projectId,
                    handler : function(b) { this.onCreateGroup(b, showPopup); },
                    scope : this
                }],
                scope : this
            });
        }

        groupList = Ext4.create('Security.panel.GroupPicker', {
            cache  : this.securityCache,
            width  : 550,
            style  : 'padding-top: 10px; background: transparent;',
            border : false,
            autoHeight     : true,
            projectId      : projectId,
            deferredRender : true,
            listeners      : {
                select: function(list,group){
                    showPopup.call(this, group);
                },
                scope: this
            },
            scope : this
        });

        // issue 13875
        groupList.on('afterlayout', function(cmp) {
            if (cmp.hasGrid())
            {
                // set initial height based on tab panel height minus room for create new header panel
                var height = this.getHeight() - 135;
                if (cmp.getGrid().getHeight() > height)
                    cmp.getGrid().setHeight(height);
            }
        }, this);

        items.push(groupList);

        return {
            title  : projectId === '' ? 'Site Groups' : 'Project Groups',
            itemId : projectId === '' ? 'sitegroups' : 'projectgroups', // required for URL lookup
            border : false,
            autoScroll: true, // 16379
            deferredRender : false,
            items  : items
        };
    },

    onCreateGroup :  function(btn, callback) {
        var field = btn.up('panel').down('textfield');
        var groupName = field.getValue();
        if (!groupName) {
            Ext4.Msg.show({
                title: 'Error',
                msg: 'No group name provided.',
                icon: Ext4.Msg.ERROR,
                buttons: Ext4.Msg.OK
            });
            return;
        }

        this.securityCache.createGroup(btn.projectId, groupName, callback, this);
        field.reset();
    }
});
