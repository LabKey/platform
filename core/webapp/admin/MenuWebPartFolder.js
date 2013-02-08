/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

Ext4.define('panel.MenuWebPartFolder', {

    extend : 'Ext.panel.Panel',

    constructor : function(config) {

        Ext4.QuickTips.init();
        Ext4.Ajax.timeout = 300000; // 5 minutes.

        Ext4.applyIf(config, {
            layout : 'border',
            frame  : false, border : false
        });

        // define Models
        Ext4.define('MenuFolder.Folder', {
            extend : 'Ext.data.Model',
            proxy : {
                type        : 'ajax',
                url         : LABKEY.ActionURL.buildURL('core', 'getMenuFolderTree.api'),
                extraParams    : {
                    rootPath: config.rootPath,
                    filterType: config.filterType,
                    urlBase: config.urlBase,
                    includeChildren: config.includeChildren
                }
            },
            fields : [
                {name : 'isProject',    type : 'boolean' },
                {name : 'text'                           }
            ]
        });

        this.callParent([config]);
    },

    initComponent : function() {

        Ext4.applyIf(this, {
            rootId : 1
        });

        this.confirmation = false;

        this.items = [];
        this.items.push(this.initCenterPanel());

        this.callParent([arguments]);
    },

    initCenterPanel : function() {

        this.centerPanel = Ext4.create('Ext.panel.Panel', {
            layout : 'fit',
            border : false, frame : false,
            items  : this.getCenterPanelItems(),
            scope  : this,
            region : 'center'
        });

        return this.centerPanel;
    },

    initFolderStore : function() {

        if (this.folderStore)
            return this.folderStore;

        this.folderStore = Ext4.create('Ext.data.TreeStore', {

            model : 'MenuFolder.Folder',
            defaultRootId : this.rootId,
            root : {
                expanded : true,
                text : 'LabKey Server Projects'
            }

        });

        return this.folderStore;
    },

    getCenterPanelItems : function() {

        var treeConfig = {
            store       : this.initFolderStore(),
            cls         : 'menu-webpart-tree', // used by selenium helper
            rootVisible : false,
            multiSelect : false,
            scope : this
        };

        treeConfig.viewConfig = {
            scope : this
        };

        this.treepanel = Ext4.create('Ext.tree.Panel', treeConfig);

        // select the
        this.treepanel.on('load', function(grid, root, success){
            this.ensureVisible(this.selected);
        }, this, {single: true});

        return [this.treepanel];
    },

    ensureVisible : function(id) {
        var target = this.treepanel.getView().getTreeStore().getRootNode().findChild('id', id, true);
        if (target)
        {
            if (!target.isLeaf())
                target.expand();
            this.treepanel.selectPath(target.getPath());
        }
    },

    mask : function(msg) {
        this.getEl().mask(msg || 'Loading...');
    },

    unmask : function() {
        this.getEl().unmask();
    }
});