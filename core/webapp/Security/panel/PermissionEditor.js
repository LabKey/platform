Ext4.define('Security.panel.PermissionEditor', {

    extend : 'Ext.panel.Panel',

//    requires : [
//        'Security.panel.GroupPicker',
//        'Security.panel.PolicyEditor',
//        'Security.window.UserInfoPopup'
//    ],

    initComponent : function() {

        if (!this.securityCache) {
            console.error('Configuration Error: A securityCache must be supplied to a Security.panel.PermissionEditor.');
            this.items = [];
            this.callParent(arguments);
            return;
        }

        Ext4.apply(this, {
            layout :'border',
            bodyStyle : 'background-color: transparent;',
            border :false,
            items  : this.getItems()
        });

        if (!this.treeConfig) {
            console.warn('A treeConfig was not supplied. Unable to show Folder Tree.');
        }

        this.callParent();
    },

    getItems : function() {

        var items = [{
            xtype      : 'tabpanel',
            id         : 'folderPermissionsTabPanel',
            region     : 'center',
            activeItem : 0,
            autoHeight : false,
            border     : true,
            defaults   : {style : {padding:'5px'}},
            plain      : true,
            items      : this.getTabItems()
        }];

        if (!this.isRoot && this.treeConfig) {

            items.push({
                xtype : 'treepanel',
                title : 'Folders',
                cls   : 'participant-filter-panel',
                store : {
                    xtype    : 'tree',
                    autoLoad : true,
                    proxy    : {
                        type : 'ajax',
                        url  : LABKEY.ActionURL.buildURL('core', 'getExtSecurityContainerTree.api')
                    },
                    baseParams : {
                        requiredPermission : this.treeConfig.permissionCls
                    },
                    root : {
                        id : this.treeConfig.project.id,
                        expanded   : true,
                        expandable : true,
                        text       : Ext4.String.htmlEncode(this.treeConfig.project.name),
                        href       : Ext4.String.htmlEncode(this.treeConfig.project.securityHref)
//                        ,cls : 'x-tree-node-current'
                    }
                },
                enableDrag : false,
                useArrows  : true,
                autoScroll : true,
                width      : 220,
                region     : 'west',
                split      : true,
                border     : true
            });

        }

        return items;
    },

    getTabItems : function() {

        var items = [this.getPolicyEditor()];

        if (this.isProjectRoot) {
            items.push(this.getGroupTabConfig('', true));
        }

        if (LABKEY.Security.currentUser.isSystemAdmin) {
            items.push(this.getGroupTabConfig(LABKEY.Security.currentContainer.path, true));
        }

        return items;
    },

    getPolicyEditor : function() {
        if (this.policyEditor)
            return this.policyEditor;

        this.policyEditor = Ext4.create('Security.panel.PolicyEditor', {
            title  : 'Permissions',
            cache  : this.securityCache,
            border : false,
            isSiteAdmin    : LABKEY.Security.currentUser.isSystemAdmin,
            isProjectAdmin : LABKEY.Security.currentUser.isAdmin,
            canInherit : this.canInherit,
            resourceId : LABKEY.container.id,
            doneURL    : this.doneURL
        });

        return this.policyEditor;
    },

    getGroupTabConfig : function(project, canEdit, ct) {

        var items = [];

        var showPopup = function(group) {
            var edit = (!group.Container && LABKEY.Security.currentUser.isSystemAdmin) || (group.Container && LABKEY.Security.currentUser.isAdmin);
            var w = Ext4.create('Security.window.UserInfoPopup', {
                userId  : group.UserId,
                cache   : this.securityCache,
                policy  : this.getPolicyEditor(),
                modal   : true,
                canEdit : edit,
                listeners: {
                    close: function(){
                        if (groupsList)
                            groupsList.onDataChanged();
                    },
                    scope: this
                },
                scope : this
            });
            w.show();
        }

        if (canEdit) {
            items.push({
                layout : 'hbox',
                border : false,
                style  : 'padding-bottom: 10px;',
                items  : [{
                    xtype : 'textfield',
                    emptyText: 'New group name',
                    width : 310,
                    padding: '10 10 10 0'
                },{
                    xtype : 'button',
                    text  : 'Create New Group',
                    margin  : '10 0 0 0',
                    handler : function(btn) {
                        var groupName = btn.up('panel').down('textfield').getValue();
                        if (!groupName)
                            return;

                        this.securityCache.createGroup((project||'/'), groupName, showPopup);
                    },
                    scope : this
                }],
                scope : this
            });
        }

        items.push({
            xtype  : 'labkey-grouppicker',
            cache  : this.securityCache,
            width  : 550,
            style  : 'padding-top: 10px; background: transparent;',
            border : false,
            autoHeight     : true,
            projectId      : project,
            deferredRender : true,
            listeners      : {
                select: function(list,group){
                    showPopup(group);
                },
                scope: this
            }
        });

        return {
            title  : project === '' ? 'Site Groups' : 'Project Groups',
            border : false,
            deferredRender : false,
            items  : items
        };
    }
});