/*
 * Copyright (c) 2013-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

/**
 * See http://docs.sencha.com/ext-js/4-1/#!/api/Ext.ux.CheckColumn
 */

Ext4.define('File.panel.Toolbar', {
    extend : 'Ext.panel.Panel',

    constructor : function(config){
        Ext4.QuickTips.init();
        Ext4.applyIf(config, {
            title : 'Toolbar and Grid Settings',
            tbarActions : [
                {id : 'folderTreeToggle', hideText : true, hideIcon : false},
                {id : 'parentFolder', hideText : true, hideIcon : false},
                {id : 'refresh', hideText : true, hideIcon : false},
                {id : 'createDirectory', hideText : true, hideIcon : false},
                {id : 'download', hideText : true, hideIcon : false},
                {id : 'deletePath', hideText : true, hideIcon : false},
                {id : 'renamePath', hideText : true, hideIcon : false},
                {id : 'movePath', hideText : true, hideIcon : false},
                {id : 'editFileProps', hideText : true, hideIcon : false},
                {id : 'upload', hideText : false, hideIcon : false},
                {id : 'importData', hideText : false, hideIcon : false},
                {id : 'emailPreferences', hideText : true, hideIcon : false},
                {id : 'auditLog', hideText : false, hideIcon : false},
                {id : 'customize', hideText : false, hideIcon : false}
            ],
            gridConfigs : {
                columns : [
                    {id : 1},
                    {id : 2},
                    {id : 3, sortable : true},                      // name
                    {id : 4, sortable : true},                      // last modified
                    {id : 5, sortable : true},                      // size
                    {id : 6, sortable : true},                      // created by
                    {id : 7, sortable : true},                      // description
                    {id : 8, sortable : false, sortDisabled : true},                     // usages
                    {id : 9, sortable : false, sortDisabled : true, hidden : true},      // download link
                    {id : 10, sortable : false, sortDisabled : true, hidden : true}      // file extension
                ]
            }
        });

        Ext4.apply(config, {
            border : false,
            padding : '10px',
            autoScroll : true
        });

        this.callParent([config]);
    },

    initComponent : function() {

        if (!Ext4.ModelManager.isRegistered('columnsModel')) {
            Ext4.define('columnsModel', {
                extend : 'Ext.data.Model',
                fields : [
                    {name : 'hidden', type : 'boolean'},
                    {name : 'sortable', type : 'boolean'},
                    {name : 'sortDisabled', type : 'boolean'},
                    {name : 'text', type : 'string'},
                    {name : 'id', type : 'int'}
                ]
            });
        }

        if (!Ext4.ModelManager.isRegistered('optionsModel')) {
            Ext4.define('optionsModel', {
                extend : 'Ext.data.Model',
                fields : [
                    {name : 'shown', type : 'boolean'},
                    {name : 'hideText', type : 'boolean'},
                    {name : 'hideIcon', type : 'boolean'},
                    {name : 'icon', type : 'string'},
                    {name : 'text', type : 'string'}
                ]
            });
        }

        var baseData = {
            auditLog         : {icon : 'audit_log.png',   text : 'Audit Log', used : false},
            createDirectory  : {icon : 'folder_new.png',  text : 'Create Folder', used : false},
            customize        : {icon : 'configure.png',   text : 'Admin', used : false},
            deletePath       : {icon : 'delete.png',      text : 'Delete', used : false},
            download         : {icon : 'download.png',    text : 'Download', used : false},
            editFileProps    : {icon : 'editprops.png',   text : 'Edit Properties', used : false},
            emailPreferences : {icon : 'email.png',       text : 'Email Preferences', used : false},
            importData       : {icon : 'db_commit.png',   text : 'Import Data', used : false},
            movePath         : {icon : 'move.png',        text : 'Move', used : false},
            parentFolder     : {icon : 'up.png',          text : 'Parent Folder', used : false},
            refresh          : {icon : 'reload.png',      text : 'Refresh', used : false},
            renamePath       : {icon : 'rename.png',      text : 'Rename', used : false},
            folderTreeToggle : {icon : 'folder_tree.png', text : 'Toggle Folder Tree', used : false},
            upload           : {icon : 'upload.png',      text : 'Upload Files', used : false}
        };

        var processedData = [];
        for (var i=0; i < this.tbarActions.length; i++) {
            var tbarAction = baseData[this.tbarActions[i].id];
            tbarAction.hideIcon = this.tbarActions[i].hideIcon;
            tbarAction.hideText = this.tbarActions[i].hideText;
            tbarAction.shown = !(this.tbarActions[i].hideIcon && this.tbarActions[i].hideText);
            tbarAction.id = this.tbarActions[i].id;
            tbarAction.used = true;
            processedData.push(tbarAction);
        }

        for (i in baseData) {
            if (baseData.hasOwnProperty(i)) {
                if(baseData[i].used != true && baseData[i].used != null){
                    baseData[i].hideIcon = false;
                    baseData[i].hideText = false;
                    baseData[i].shown = false;
                    baseData[i].id = i;
                    processedData.push(baseData[i]);
                }
            }
        }

        this.optionsStore = Ext4.create('Ext.data.Store', {
             model : 'optionsModel',
             data : processedData
        });

        var columnData = [];
        var baseColumnNames = ['Row Checker', 'File Icon', 'Name', 'Last Modified', 'Size', 'Created By', 'Description',
                'Usages', 'Download Link', 'File Extension'];
        if (this.useCustomProps)
        {
            for (var i = 0; i < this.fileProperties.length; i++)
            {
                if (this.fileProperties[i].label)
                    baseColumnNames.push(this.fileProperties[i].label);
                else
                    baseColumnNames.push(this.fileProperties[i].name);
            }
        }

        for (var i = 0; i < this.gridConfigs.columns.length; i++)
        {
            if (this.gridConfigs.columns[i].id != i)
                this.gridConfigs.columns[i].id = i;

            if (this.gridConfigs.columns[i].id == 'checker' || this.gridConfigs.columns[i].id == 0)
                continue;

            columnData.push({
                id   : this.gridConfigs.columns[i].id,
                text : baseColumnNames[i],
                hidden : this.gridConfigs.columns[i].hidden,
                sortable : this.gridConfigs.columns[i].sortable,
                sortDisabled : this.gridConfigs.columns[i].sortDisabled
            });
        }

        // if we have custom file properties and they were not already included with the gridConfigs.columns, add them here
        for (var i = 0; i < this.fileProperties.length; i++)
        {
            var index = i + 9;
            if (!columnData[index])
            {
                columnData.push({
                    id : index + 1,
                    text : this.fileProperties[i].label ? this.fileProperties[i].label : this.fileProperties[i].name,
                    hidden : this.fileProperties[i].hidden,
                    sortable : false,            // sorting of custom file properties are not supported
                    sortDisabled : true
                });
            }
        }

        this.columnsStore = Ext4.create('Ext.data.Store', {
            model : 'columnsModel',
            data : columnData
        });

        var optionsPanel = {
            xtype : 'grid',
            id : 'optionsPanel',
            width : 700,
            height: 290,
            padding : '0 0 15 0',
            store : this.optionsStore,
            expanded : true,
            viewConfig: {
                plugins: {
                    ptype: 'gridviewdragdrop',
                    dragText: 'Drag and drop to reorganize'
                }
            },
            columns :  [
                {header : 'Shown', dataIndex : 'shown', xtype : 'checkcolumn', flex : 1, renderer : function(value, meta, rec, rowIndex)
                {
                  if(rec.data.id === 'customize')
                  {
                      return (new Ext4.ux.CheckColumn()).renderer(value, {tdCls : "", style : "", disabled : true});
                  }
                  else
                      return (new Ext4.ux.CheckColumn()).renderer(value);
                },
                    listeners:{
                    beforecheckchange:function(col,rowIndex, isChecked){
                        if(this.optionsStore.getAt(rowIndex).data.id === 'customize')
                        {
                            return false;
                        }
                        else if(!isChecked)
                        {
                            this.optionsStore.getAt(rowIndex).set('hideText', true);
                            this.optionsStore.getAt(rowIndex).set('hideIcon', true);
                        }
                        else
                        {
                            this.optionsStore.getAt(rowIndex).set('hideText', false);
                            this.optionsStore.getAt(rowIndex).set('hideIcon', false);
                        }
                    },
                    scope : this
                }},
                {header : 'Hide Text', dataIndex : 'hideText', xtype : 'checkcolumn', flex : 1, listeners:{
                    beforecheckchange:function(col, rowIndex, isChecked){
                        if(isChecked){
                            if(this.optionsStore.getAt(rowIndex).data.hideIcon && this.optionsStore.getAt(rowIndex).data.id == 'customize')
                            {
                                var msg = Ext4.Msg.alert('Visibility Error', 'Invalid action (would cause Admin Button to be invisible).');
                                this.up('window').zIndexManager.register(msg);

                                return false;
                            }
                            else if(this.optionsStore.getAt(rowIndex).data.hideIcon && this.optionsStore.getAt(rowIndex).data.id != 'customize')
                            {
                                this.optionsStore.getAt(rowIndex).set('shown', false);
                            }
                        }
                    },
                    scope : this
                }},
                {header : 'Hide Icon', dataIndex : 'hideIcon', xtype : 'checkcolumn', flex : 1, listeners:{
                    beforecheckchange:function(col, rowIndex, isChecked){
                        if(isChecked){
                            if(this.optionsStore.getAt(rowIndex).data.hideText && this.optionsStore.getAt(rowIndex).data.id == 'customize')
                            {
                                var msg = Ext4.Msg.alert('Visibility Error', 'Invalid action (would cause Admin Button to be invisible).');
                                this.up('window').zIndexManager.register(msg);
                                return false;
                            }
                            else if(this.optionsStore.getAt(rowIndex).data.hideText && this.optionsStore.getAt(rowIndex).data.id != 'customize')
                            {
                                this.optionsStore.getAt(rowIndex).set('shown', false);
                            }
                        }
                    },
                    scope : this
                }},
                {header : 'Icon', dataIndex: 'icon', flex : 1, renderer : function(value){
                    var path = LABKEY.contextPath + '/_images/' + value;
                    return '<img src = "'+path+'" />';
                }},
                {header : 'Text', dataIndex : 'text', flex : 2}
            ]
        };

        var gridSettingsPanel = {
            xtype : 'grid',
            id : 'gridSettingsPanel',
            width : 700,
            maxHeight : 250,
            store : this.columnsStore,
            viewConfig: {
                plugins: {
                    ptype: 'gridviewdragdrop',
                    dragText: 'Drag and drop to reorganize'
                }
            },
            columns :  [
                {header : 'Hidden', dataIndex : 'hidden', xtype : 'checkcolumn', flex : 1},
                {header : 'Sortable', dataIndex : 'sortable', xtype : 'checkcolumn', flex : 1, renderer : function(value, meta, rec){
                    return (new Ext4.grid.column.CheckColumn({disabled : rec.data.sortDisabled})).renderer(value, meta);
                }, scope : this},
                {header : 'Text', dataIndex : 'text', flex : 1}
            ]
        };

        var optionsText = {
            xtype : 'label',
            html: '<span class="labkey-strong">Configure Toolbar Options</span>' +
                    '<p>Toolbar buttons are in display order, from top to bottom. ' +
                    'You can adjust their position by clicking and dragging them, and can set their visibility by toggling '+
                    'the checkboxes in the appropriate fields.</p>'
        };

        var gridColumnsText = {
            xtype : 'label',
            html: '<span class="labkey-strong">Configure Grid Column Settings</span>' +
                    '<p>Grid columns are in display order from top to bottom, but hidden columns do not appear in the grid. ' +
                    'The columns can be reorganized by clicking and dragging their respective rows, and can be hidden by checking ' +
                    'the appropriate box.  You may also set whether or not the column is sortable.</p>'
        };

        this.items = [optionsText, optionsPanel, gridColumnsText, gridSettingsPanel];

        this.callParent();
    },

    getGridConfigs : function(){
        var item;
        var gridConfigsRet = {};
        gridConfigsRet.columns = [this.gridConfigs.columns[0]];
        gridConfigsRet.importDataEnabled = this.gridConfigs.importDataEnabled;
        for(var i = 0; i < this.columnsStore.getCount(); i++){
            item = this.columnsStore.getAt(i).data;
            var gridConfigsRetcol = {
                id : item.id,
                hidden : item.hidden,
                sortable : item.sortable,
                width : this.gridConfigs.columns[i+1] ? this.gridConfigs.columns[i+1].width : 80
            };
            gridConfigsRet.columns.push(gridConfigsRetcol);
        }
        return gridConfigsRet;
    },

    gridConfigsChanged : function()
    {
        //TODO Get this to work (need to find a way to see if the rows were reordered)
        //return (this.columnsStore.getUpdatedRecords().length > 0);
        return true;
    },

    getTbarActions : function()
    {
        var item;
        var tBarRet = [];
        var position = 0;
        for(var i = 0; i < this.optionsStore.getCount(); i++)
        {
            item = this.optionsStore.getAt(i).data;
            if(item.id === 'customize')
            {
                item.shown = true;
            }

            tBarRet[position] = {
                position : position,
                id : item.id,
                hideIcon : !item.shown || item.hideIcon,
                hideText : !item.shown || item.hideText
            };
            position++;
        }

        return tBarRet;
    },

    tbarChanged : function()
    {
        return (this.optionsStore.getUpdatedRecords().length > 0);
    }
});

