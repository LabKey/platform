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

        var items = [this.getTabPanel()];

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
                        url  : LABKEY.ActionURL.buildURL('core', 'getExtSecurityContainerTree.api'),
                        extraParams : {
                            requiredPermission : this.treeConfig.requiredPermission
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
                    load : function(tree, node, recs) {
                        for (var r=0; r < recs.length; r++) {
                            if (recs[r].data.cls != '')
                                return;
                        }
                        tree.getRootNode().set('cls', 'x-tree-node-current');
                    }
                }
            });

        }

        return items;
    },

    getTabPanel : function() {

        if (this.tabPanel)
            return this.tabPanel;

        this.tabPanel = Ext4.create('Ext.tab.Panel', {
            xtype      : 'tabpanel',
            region     : 'center',
            activeItem : 0,
            autoHeight : false,
            border     : true,
            defaults   : {style : {padding:'5px'}},
            plain      : true,
            items      : this.getTabItems()
        });

        return this.tabPanel;
    },

    getTabItems : function() {

        var items = [this.getPolicyEditor()];

        if (LABKEY.Security.currentUser.isSystemAdmin) {
            items.push(this.getGroupTabConfig(this.securityCache.projectId, true));
        }

        if (this.isSiteAdmin) {
            items.push(this.getGroupTabConfig('', true));
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

    getGroupTabConfig : function(projectId, canEdit) {

        var items = [], groupList;

        var showPopup = function(group) {
            var edit = (!group.Container && LABKEY.Security.currentUser.isSystemAdmin) || (group.Container && LABKEY.Security.currentUser.isAdmin);
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
        }

        if (canEdit) {
            items.push({
                layout : 'hbox',
                border : false,
                style  : 'padding-bottom: 10px;',
                items  : [{
                    xtype     : 'textfield',
                    name      : projectId === '' ? 'sitegroupsname' : 'projectgroupsname',
                    emptyText : 'New group name',
                    width     : 310,
                    padding   : '10 10 10 0'
                },{
                    xtype : 'button',
                    text  : 'Create New Group',
                    margin  : '10 0 0 0',
                    handler : function(btn) {
                        var groupName = btn.up('panel').down('textfield').getValue();
                        if (!groupName)
                            return;

                        this.securityCache.createGroup(projectId, groupName, showPopup, this);

                        btn.up('panel').down('textfield').reset();
                    },
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

        items.push(groupList);

        return {
            title  : projectId === '' ? 'Site Groups' : 'Project Groups',
            border : false,
            deferredRender : false,
            items  : items
        };
    }
});
