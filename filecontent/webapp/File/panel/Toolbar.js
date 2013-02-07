/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

/**
 * See http://docs.sencha.com/ext-js/4-1/#!/api/Ext.ux.CheckColumn
 */
Ext4.define('Ext.ux.CheckColumn', {
    extend: 'Ext.grid.column.Column',
    alias: 'widget.checkcolumn',

    /**
     * @cfg {Boolean} [stopSelection=true]
     * Prevent grid selection upon mousedown.
     */
    stopSelection: true,

    tdCls: Ext4.baseCSSPrefix + 'grid-cell-checkcolumn',

    constructor: function(config) {
        this.addEvents(
                /**
                 * @event beforecheckchange
                 * Fires when before checked state of a row changes.
                 * The change may be vetoed by returning `false` from a listener.
                 * @param {Ext.ux.CheckColumn} this CheckColumn
                 * @param {Number} rowIndex The row index
                 * @param {Boolean} checked True if the box is to be checked
                 */
                'beforecheckchange',
                /**
                 * @event checkchange
                 * Fires when the checked state of a row changes
                 * @param {Ext.ux.CheckColumn} this CheckColumn
                 * @param {Number} rowIndex The row index
                 * @param {Boolean} checked True if the box is now checked
                 */
                'checkchange'
        );
        this.callParent(arguments);
    },

    /**
     * @private
     * Process and refire events routed from the GridView's processEvent method.
     */
    processEvent: function(type, view, cell, recordIndex, cellIndex, e, record, row) {
        var me = this,
                key = type === 'keydown' && e.getKey(),
                mousedown = type == 'mousedown';

        if (mousedown || (key == e.ENTER || key == e.SPACE)) {
            var dataIndex = me.dataIndex,
                    checked = !record.get(dataIndex);

            // Allow apps to hook beforecheckchange
            if (me.fireEvent('beforecheckchange', me, recordIndex, checked) !== false) {
                record.set(dataIndex, checked);
                me.fireEvent('checkchange', me, recordIndex, checked);

                // Mousedown on the now nonexistent cell causes the view to blur, so stop it continuing.
                if (mousedown) {
                    e.stopEvent();
                }

                // Selection will not proceed after this because of the DOM update caused by the record modification
                // Invoke the SelectionModel unless configured not to do so
                if (!me.stopSelection) {
                    view.selModel.selectByPosition({
                        row: recordIndex,
                        column: cellIndex
                    });
                }

                // Prevent the view from propagating the event to the selection model - we have done that job.
                return false;
            } else {
                // Prevent the view from propagating the event to the selection model if configured to do so.
                return !me.stopSelection;
            }
        } else {
            return me.callParent(arguments);
        }
    },

    // Note: class names are not placed on the prototype bc renderer scope
    // is not in the header.
    renderer : function(value){
        var cssPrefix = Ext4.baseCSSPrefix,
                cls = [cssPrefix + 'grid-checkheader'];

        if (value) {
            cls.push(cssPrefix + 'grid-checkheader-checked');
        }
        return '<div class="' + cls.join(' ') + '">&#160;</div>';
    }
});

Ext4.define('File.panel.ToolbarPanel', {
    extend : 'Ext.panel.Panel',

    constructor : function(config){
        Ext4.QuickTips.init();
        Ext4.applyIf(config, {
            title : 'Toolbar and Grid Settings',
            tbarActions : [
                {id : 'folderTreeToggle', hideText : true, hideIcon : false},
                {id : 'parentFolder', hideText : true, hideIcon : false},
                {id : 'createDirectory', hideText : true, hideIcon : false},
                {id : 'download', hideText : true, hideIcon : false},
                {id : 'deletePath', hideText : true, hideIcon : false},
                {id : 'importData', hideText : false, hideIcon : false},
                {id : 'customize', hideText : false, hideIcon : false}
            ],
            gridConfigs : {
                columns : [
                    {id : 1},
                    {id : 2},
                    {id : 3, sortable : true},
                    {id : 4, sortable : true},
                    {id : 5, sortable : true},
                    {id : 6, sortable : true},
                    {id : 7, sortable : true},
                    {id : 8, sortable : true},
                    {id : 9, sortable : true, hidden : true},
                    {id : 10, sortable : true, hidden : true}
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

        var topText = {
            xtype : 'label',
            html: '<h2>Configure Grid columns and Toolbar</h2><p>Toolbar buttons are in display order, from top to bottom. ' +
                    'You can adjust their position by clicking and dragging them, and can set their visibility by toggling '+
                    'the checkboxes in the appropriate fields. '+
                    '</p><br><p>Grid columns are in display order from top to bottom, but hidden columns do not appear in the grid. ' +
                    'The columns can be reorganized by clicking and dragging their respective rows, and can be hidden by checking ' +
                    'the appropriate box.  You may also set whether or not the column is sortable.</p> ' +
                    '</br><p>Either table can be expanded or collapsed by pressing the button at the right of blue top bar.</p>',
            padding : '5 5 10 5'
        };

        if (!Ext4.ModelManager.isRegistered('columnsModel')) {
            Ext4.define('columnsModel', {
                extend : 'Ext.data.Model',
                fields : [
                    {name : 'hidden', type : 'boolean'},
                    {name : 'sortable', type : 'boolean'},
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
            tbarAction.shown = true;
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
        var baseColumnData = ['File Icon', 'Name', 'Last Modified', 'Size', 'Created By', 'Description',
                'Usages', 'Download Link', 'File Extension'];
        var reverseBaseColumnData = [];
        for(i=0; i < baseColumnData.length; i++){
            reverseBaseColumnData[baseColumnData[i]] = i+1;
        }

        for(i=1; i < this.gridConfigs.columns.length; i++) {
            var text = baseColumnData[this.gridConfigs.columns[i].id-1];
            columnData.push({
                id   : reverseBaseColumnData[text],
                text : text,
                hidden : this.gridConfigs.columns[i].hidden,
                sortable : this.gridConfigs.columns[i].sortable
            });
        }

        this.columnsStore = Ext4.create('Ext.data.Store', {
            model : 'columnsModel',
            data : columnData
        });

        var optionsPanel = {
            xtype : 'grid',
            title : 'Toolbar Options',
            id : 'optionsPanel',
            titleCollapse : true,
            collapsible : true,
            width : '100%',
            height: 295,
            padding : '10 5 10 5',
            store : this.optionsStore,
            expanded : true,
            viewConfig: {
                plugins: {
                    ptype: 'gridviewdragdrop',
                    dragText: 'Drag and drop to reorganize'
                }
            },
            columns :  [
                {header : 'Shown', dataIndex : 'shown', xtype : 'checkcolumn', flex : 1, listeners:{
                    beforecheckchange:function(col,rowIndex){
                        if(this.optionsStore.getAt(rowIndex).data.id === 'customize')
                        {
                            alert('You cannot modify the shown state of the admin button.');
                            return false;
                        }
                        else
                        {
                            this.optionsStore.getAt(rowIndex).set('hideText', true);
                            this.optionsStore.getAt(rowIndex).set('hideIcon', true);
                        }
                    },
                    scope : this
                }},
                {header : 'Hide Text', dataIndex : 'hideText', xtype : 'checkcolumn', flex : 1, listeners:{
                    beforecheckchange:function(col, rowIndex, isChecked){
                        if(isChecked){
                            if(this.optionsStore.getAt(rowIndex).data.hideIcon && this.optionsStore.getAt(rowIndex).data.id == 'customize')
                            {
                               alert('Action impossible (would cause Admin Button to be invisible).');
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
                                alert('Action impossible (would cause Admin Button to be invisible).');
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
            ],
            listeners : {
                beforeexpand : function(g) {
                    Ext4.getCmp('gridSettingsPanel').collapse();
                },
                collapse : function(g) {
                    Ext4.getCmp('gridSettingsPanel').expand();
                }
            }
        };

        var gridPanel = {
            xtype : 'grid',
            title : 'Grid Settings',
            id : 'gridSettingsPanel',
            titleCollapse : true,
            collapsible : true,
            collapsed : true,
            width : '100%',
            padding : '10 5 10 5',
            store : this.columnsStore,
            viewConfig: {
                plugins: {
                    ptype: 'gridviewdragdrop',
                    dragText: 'Drag and drop to reorganize'
                }
            },
            columns :  [
                {header : 'Hidden', dataIndex : 'hidden', xtype : 'checkcolumn', flex : 1},
                {header : 'Sortable', dataIndex : 'sortable', xtype : 'checkcolumn', flex : 1},
                {header : 'Text', dataIndex : 'text', flex : 1}
            ],
            listeners : {
                beforeexpand : function(g) {
                    Ext4.getCmp('optionsPanel').collapse();
                },
                collapse : function(g) {
                    Ext4.getCmp('optionsPanel').expand();
                }
            }
        };


        this.items = [topText, optionsPanel, gridPanel];

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
                width : this.gridConfigs.columns[i+1].width
            };
            gridConfigsRet.columns.push(gridConfigsRetcol);
        }
        return gridConfigsRet;
    },

    getTbarActions : function(){
        var item;
        var tBarRet = [];
        var position = 0;
        for(var i = 0; i < this.optionsStore.getCount(); i++){
            item = this.optionsStore.getAt(i).data;
            if(item.id === 'customize'){
                item.shown = true;
            }
            if(item.shown){
                if(item.hideIcon && item.hideText)
                    continue;
                tBarRet[position] = {
                    position : position,
                    id : item.id,
                    hideIcon : item.hideIcon,
                    hideText : item.hideText
                };
                position++;
            }
        }

        return tBarRet;
    }
});

