/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.requiresExt4ClientAPI(true);
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
           title : 'Toolbar and Grid Settings'
        });
        Ext4.apply(config, {
            xtype : 'panel',
            border : false,
            padding : '10px',
            autoScroll : true
        });
        this.callParent([config]);
    },

    initComponent : function(){

        var topText = Ext4.create('Ext.form.Label', {
            html: '<h2>Configure Grid columns and Toolbar</h2><p>Toolbar buttons are in display order, from top to bottom.' +
                    'You can adjust their position by clicking and dragging them, and can set their visibility by toggling'+
                    'the checkboxes in the appropriate fields.'+
                    '</p><br><p>Grid columns are in display order from top to bottom, but hidden columns do not appear in the grid.' +
                    'The columns can be reorganized by clicking and dragging their respective rows, and can be hidden by checking' +
                    'the appropriate box.  You may also set whether or not the column is sortable.</p>',
            padding : '5 5 10 5'
        });

        Ext4.define('columnsModel', {
            extend : 'Ext.data.Model',
            fields : [
                {name : 'hidden', type : 'boolean'},
                {name : 'sortable', type : 'boolean'},
                {name : 'text', type : 'string'},
                {name : 'id', type : 'int'}
            ]
        });

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

        var baseDataArray = [];
        baseDataArray['customize'] = {icon : 'configure.png', text : 'Admin', used : false};
        baseDataArray['auditLog'] = {icon : 'audit_log.png', text : 'Audit Log', used : false};
        baseDataArray['createDirectory'] = {icon : 'folder_new.png', text : 'Create Folder', used : false} ;
        baseDataArray['deletePath'] = {icon : 'delete.png', text : 'Delete', used : false};
        baseDataArray['download'] = {icon : 'download.png', text : 'Download', used : false};
        baseDataArray['editFileProps'] = {icon : 'editprops.png', text : 'Edit Properties', used : false};
        baseDataArray['emailPreferences'] = {icon : 'email.png', text : 'Email Preferences', used : false};
        baseDataArray['importData'] = {icon : 'db_commit.png', text : 'Import Data', used : false};
        baseDataArray['movePath'] = {icon : 'move.png', text : 'Move', used : false};
        baseDataArray['parentFolder'] = {icon : 'up.png', text : 'Parent Folder', used : false};
        baseDataArray['refresh'] = {icon : 'reload.png', text : 'Refresh', used : false};
        baseDataArray['renamePath'] = {icon : 'rename.png', text : 'Rename', used : false};
        baseDataArray['folderTreeToggle'] = {icon : 'folder_tree.png', text : 'Toggle Folder Tree', used : false};
        baseDataArray['upload'] = {icon : 'upload.png', text : 'Upload Files', used : false};

        var processedData = [];
        for(var i = 0; i < this.tbarActions.length; i++){
            processedData[i] = baseDataArray[this.tbarActions[i].id];
            processedData[i].hideIcon = this.tbarActions[i].hideIcon;
            processedData[i].hideText = this.tbarActions[i].hideText;
            processedData[i].shown = true;
            processedData[i].id = this.tbarActions[i].id;
            processedData[i].used = true;
        }
        for(var remainingItem in baseDataArray){

            if(baseDataArray[remainingItem].used != true && baseDataArray[remainingItem].used != null){
                baseDataArray[remainingItem].hideIcon = false;
                baseDataArray[remainingItem].hideText = false;
                baseDataArray[remainingItem].shown = false;
                baseDataArray[remainingItem].id = remainingItem;
                processedData.push(baseDataArray[remainingItem]);
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
        for(var i = 0; i < baseColumnData.length; i++){
            reverseBaseColumnData[baseColumnData[i]] = i+1;
        }

        for(var i = 0; i < this.gridConfigs.columns.length-1; i++){
            columnData[i] = {};
            columnData[i].text = baseColumnData[this.gridConfigs.columns[i+1].id-1];
            columnData[i].id = reverseBaseColumnData[columnData[i].text];
            this.gridConfigs.columns[i+1].hidden ? columnData[i].hidden = true : columnData[i].hidden = false;
            this.gridConfigs.columns[i+1].sortable ? columnData[i].sortable = true : columnData[i].sortable = false;
        }

        this.columnsStore = Ext4.create('Ext.data.Store', {
            model : 'columnsModel',
            data : columnData
        });

        var optionsPanel = Ext4.create('Ext.grid.Panel', {
            title : 'Toolbar Options',
            id : 'optionsPanel',
            overflowY : 'scroll',
            collapsible : true,
            collapsed : true,
            width : 615,
            height: 350,
            padding : '10 5 10 5',
            store : this.optionsStore,
            viewConfig: {
                plugins: {
                    ptype: 'gridviewdragdrop',
                    dragText: 'Drag and drop to reorganize'
                }
            },
            columns :  [
                {header : 'Shown', dataIndex : 'shown', xtype : 'checkcolumn', width : 100},
                {header : 'Hide Text', dataIndex : 'hideText', xtype : 'checkcolumn', width : 100},
                {header : 'Hide Icon', dataIndex : 'hideIcon', xtype : 'checkcolumn', width : 100},
                {header : 'Icon', width : 100, dataIndex: 'icon', renderer : function(value){
                    var path = '/labkey/_images/' + value;
                    return '<img src = "'+path+'" />';
                }},
                {header : 'Text', dataIndex : 'text', width : 200}
            ]
        });

        var gridPanel = Ext4.create('Ext.grid.Panel', {
            title : 'Grid Settings',
            collapsible : true,
            collapsed : true,
            width : 615,
            overflowY : 'scroll',
            padding : '10 5 10 5',
            store : this.columnsStore,
            viewConfig: {
                plugins: {
                    ptype: 'gridviewdragdrop',
                    dragText: 'Drag and drop to reorganize'
                }
            },
            columns :  [
                {header : 'Hidden', dataIndex : 'hidden', xtype : 'checkcolumn', width : 200},
                {header : 'Sortable', dataIndex : 'sortable', xtype : 'checkcolumn', width : 200},
                {header : 'Text', dataIndex : 'text', width : 200}
            ]
        });


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
            gridConfigsRet.columns[i+1] = {
                id : item.id,
                hidden : item.hidden,
                sortable : item.sortable,
                width : this.gridConfigs.columns[i+1].width
            }
        }
        return gridConfigsRet;
    },

    getTbarActions : function(){
        var item;
        var tBarRet = [];
        var position = 0;
        for(var i = 0; i < this.optionsStore.getCount(); i++){
            item = this.optionsStore.getAt(i).data;
            if(item.shown){
                tBarRet[position] = {
                    position : position,
                    id : item.id,
                    hideIcon : item.hideIcon,
                    hideText : item.hideText
                }
                position++;
            }
        }

        return tBarRet;
    }
});

