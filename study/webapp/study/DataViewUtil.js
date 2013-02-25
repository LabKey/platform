/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.study.DataViewUtil', {

    singleton : true,

    getUsersStore : function() {

        // define data models
        if (!Ext4.ModelManager.isRegistered('LABKEY.data.User')) {
            Ext4.define('LABKEY.data.User', {
                extend : 'Ext.data.Model',
                fields : [
                    {name : 'userId',       type : 'int'},
                    {name : 'displayName'               }
                ]
            });
        }

        var config = {
            model   : 'LABKEY.data.User',
            autoLoad: true,
            pageSize: 10000,
            proxy   : {
                type   : 'ajax',
                url : LABKEY.ActionURL.buildURL('query', 'selectRows.api'),
                extraParams : {
                    schemaName  : 'core',
                    queryName   : 'UsersMsgPrefs'
                },
                reader : {
                    type : 'json',
                    root : 'rows'
                }
            },
            listeners : {
                load : function(s, recs, success, operation, ops) {
                    s.sort('DisplayName', 'ASC');
                    s.insert(0, {UserId : -1, DisplayName : 'None'});
                }
            }
        };

        return Ext4.create('Ext.data.Store', config);
    },

    getViewCategoriesStore : function(opts) {

        var options = {};
        Ext4.apply(options, opts, {index : 1});

        if (options.index == undefined) {
            options.index = 1;
        }

        // define data models
        if (!Ext4.ModelManager.isRegistered('Dataset.Browser.Category')) {
            Ext4.define('Dataset.Browser.Category', {
                extend : 'Ext.data.Model',
                fields : [
                    {name : 'created',      type : 'string'},
                    {name : 'createdBy'                  },
                    {name : 'displayOrder', type : 'int' },
                    {name : 'label'                      },
                    {name : 'modfied',      type : 'string'},
                    {name : 'modifiedBy'                 },
                    {name : 'rowid',        type : 'int' },
                    {name : 'subCategories' },
                    {name : 'parent',       type : 'int' }
                ]
            });
        }

        var config = {
            pageSize: 100,
            model   : 'Dataset.Browser.Category',
            autoLoad: true,
            autoSync: false,
            proxy   : {
                type   : 'ajax',
                api    : {
                    create  : LABKEY.ActionURL.buildURL('study', 'saveCategories.api'),
                    read    : LABKEY.ActionURL.buildURL('study', 'getCategories.api'),
                    update  : LABKEY.ActionURL.buildURL('study', 'saveCategories.api'),
                    destroy : LABKEY.ActionURL.buildURL('study', 'deleteCategories.api')
                },
                extraParams : {
                    // These parameters are required for specific webpart filtering
                    pageId : options.pageId,
                    index  : options.index
                },
                reader : {
                    type : 'json',
                    root : 'categories'
                },
                writer: {
                    type : 'json',
                    root : 'categories',
                    allowSingle : false
                }
            },
            inRawLoad : false,
            listeners : {
                load : function(s, recs) {

                    if (!s.inRawLoad) {
                        s.inRawLoad = true;
                        var parents = {}, keys = [],
                            newRecs = [], labels = [], r, d, p, u;

                        for (r=0; r < recs.length; r++) {
                            if (recs[r].get('parent') == -1) {
                                parents[recs[r].get('rowid')] = {record : recs[r], subs : []};
                                keys.push(recs[r].get('rowid'));
                            }
                        }

                        for (r=0; r < recs.length; r++) {
                            if (recs[r].get('parent') >= 0) {
                                parents[recs[r].get('parent')].subs.push(recs[r]);
                            }
                        }

                        for (p=0; p < keys.length; p++) {
                            d = parents[keys[p]].record.data;
                            newRecs.push(d);
                            labels.push(parents[keys[p]].record.get('label'));
                            for (u=0; u < parents[keys[p]].subs.length; u++) {
                                d = parents[keys[p]].subs[u].data;
                                newRecs.push(d);
                                labels.push(parents[keys[p]].subs[u].get('label'));
                            }
                        }

                        s.loadData(newRecs);
                    }

                    s.inRawLoad = false;
                }
            }
        };

        Ext4.applyIf(config, options);
        if (options.useGrouping) {
            config["groupField"] = 'category';
        }

        return Ext4.create('Ext.data.Store', config);
    },

    getManageCategoriesDialog : function(config) {

        var cellEditing = Ext4.create('Ext.grid.plugin.CellEditing', {
            pluginId : 'categorycell',
            clicksToEdit: 2
        });

        var store = this.initializeCategoriesStore();
        var winID = Ext4.id();
        this.catWinID = winID;
        var subwinID = Ext4.id();
        this._subwinID = subwinID;

        // Z-Index Manager
        var zix = new Ext4.ZIndexManager(this);

        var grid = Ext4.create('Ext.grid.Panel', {
            store    : store,
            border   : false, frame: false,
            scroll   : 'vertical',
            bubbleEvents : ['categorychange'],
            columns  : [{
                xtype    : 'templatecolumn',
                text     : 'Category',
                flex     : 1,
                sortable : true,
                dataIndex: 'label',
                tpl      : '{label:htmlEncode}',
                editor   : {
                    xtype:'textfield',
                    allowBlank:false
                }
            },{
                xtype    : 'actioncolumn',
                width    : 50,
                align    : 'center',
                sortable : false,
                items : [{
                    icon    : LABKEY.contextPath + '/' + LABKEY.extJsRoot_41 + '/resources/themes/images/access/qtip/close.gif',
                    tooltip : 'Delete'
                }],
                listeners : {
                    click : function(col, td, idx) { this.onDeleteCategory(grid, store, idx, zix); },
                    scope : this
                }
            }],
            multiSelect : true,
            cls         : 'iScroll', // webkit custom scroll bars
            viewConfig : {
                stripRows : true,
                plugins   : [{
                    ptype : 'gridviewdragdrop',
                    dragText: 'Drag and drop to reorganize'
                }],
                listeners : {
                    drop : function() {
                        var s = grid.getStore();
                        var display = 1;
                        s.each(function(rec){
                            rec.set('displayOrder', display);
                            rec.setDirty();
                            display++;
                        }, this);
                    }
                }
            },
            listeners : {
                edit : function(editor, e) {
                    e.grid.getStore().sync({
                        success : function() {
                            e.grid.getStore().load();
                        },
                        failure : function(batch, opts) {
                            if (batch.operations && batch.operations.length > 0) {
                                if (!batch.operations[0].request.scope.reader.jsonData.success) {
                                    var mb = Ext4.Msg.alert('Category Management', batch.operations[0].request.scope.reader.jsonData.message);
                                    zix.register(mb);
                                }
                            }
                            else {
                                Ext4.Msg.alert('Category Managment', 'Failed to save updates.');
                                zix.register(mb);
                            }
                            e.grid.getStore().load();
                        }
                    });

                    // hide subcategory
                    if (this._subwinID) {
                        var sw = Ext4.getCmp(this._subwinID);
                        if (sw && sw.isVisible())
                            sw.hide();
                    }
                },
                select : function(g, rec) {
                    var w = Ext4.getCmp(winID);

                    if (rec.data && rec.data.rowid == 0) { return; }

                    if (w && w.isVisible()) {
                        var box = w.getBox();

                        var sw = Ext4.getCmp(subwinID);
                        if (sw) {
                            var s = sw.getComponent(sw.gid).getStore();
                            s.getProxy().extraParams['parent'] = rec.data.rowid;
                            s.load();
                            sw.setParent(rec.data.rowid);
                            if (!sw.isVisible())
                                sw.show();
                        }
                        else {
                            var gid = Ext4.id();
                            var win = Ext4.create('Ext.Window', {
                                id : subwinID,
                                width : 250,
                                height : 300,
                                x : box.x+box.width, y : box.y,
                                autoShow : true,
                                cls : 'data-window',
                                title : 'Subcategories',
                                draggable : false,
                                resizable : false,
                                closable : true,
                                floatable : true,
                                gid : gid,
                                items : [this.getCategoryGrid(grid, rec.data.rowid, gid, zix)],
                                listeners : {
                                    close : function(p) {
                                        p.destroy();
                                    }
                                },
                                pid : rec.data.rowid,
                                setParent : function(pid) {
                                    this.pid = pid;
                                },
                                getParentId : function() { return this.pid; },
                                buttons : [{
                                    text : 'New Subcategory',
                                    handler : function(b) {
                                        var grid = Ext4.getCmp(gid);
                                        var store = grid.getStore();
                                        var p = b.up('window').getParentId();
                                        var r = Ext4.ModelManager.create({
                                            label        : 'New Subcategory',
                                            displayOrder : 0,
                                            parent       : p
                                        }, 'Dataset.Browser.Category');
                                        store.insert(0, r);
                                        grid.getPlugin('subcategorycell').startEditByPosition({row : 0, column : 0});
                                    }
                                }]
                            });
                            zix.register(win);
                        }
                    }
                },
                scope : this
            },
            plugins   : [cellEditing],
            selType   : 'rowmodel',
            scope     : this
        });

        var dialog = Ext4.create('Ext.window.Window', {
            title  : 'Manage Categories',
            id : winID,
            width  : 400,
            height : 400,
            layout : 'fit',
            cls    : 'data-window',
            modal  : true,
            draggable : false,
            defaults  : {
                frame : false
            },
            items   : [grid],
            buttons : [{
                text    : 'New Category',
                handler : function() {
                    var r = Ext4.ModelManager.create({
                        label        : 'New Category',
                        displayOrder : 0
                    }, 'Dataset.Browser.Category');
                    store.insert(0, r);
                    cellEditing.startEditByPosition({row : 0, column : 0});

                    // hide subcategory
                    if (this._subwinID) {
                        var sw = Ext4.getCmp(this._subwinID);
                        if (sw && sw.isVisible())
                            sw.hide();
                    }
                }
            },{
                text    : 'Done',
                handler : function() {
                    grid.getStore().sync({
                        success : function() {
                        },
                        scope : this
                    });
                    dialog.fireEvent('afterchange', dialog);
                    dialog.close();
                },
                scope : this
            }],
            listeners : {
                beforeclose : function() {
                    var sw = Ext4.getCmp(subwinID);
                    if (sw) {
                        sw.close();
                    }
                },
                scope : this
            },
            scope     : this
        });

        zix.register(dialog);
        dialog.addEvents('afterchange', 'done');

        return dialog;
    },

    /**
     * @private
     */
    getCategoryGrid : function(parentCmp, categoryid, gridid, idxMgr) {

        var cellEditing = Ext4.create('Ext.grid.plugin.CellEditing', {
            pluginId : 'subcategorycell',
            clicksToEdit : 2
        });

        var store = this.initializeCategoriesStore({categoryId : categoryid});

        var grid = Ext4.create('Ext.grid.Panel', {
            id : gridid || Ext4.id(),
            store   : store,
            columns : [{
                xtype    : 'templatecolumn',
                text     : 'Subcategory',

                flex     : 1,
                sortable : true,
                dataIndex: 'label',
                tpl      : '{label:htmlEncode}',
                editor   : {
                    xtype:'textfield',
                    allowBlank:false
                }
            },{
                xtype    : 'actioncolumn',
                width    : 50,
                align    : 'center',
                sortable : false,
                items : [{
                    icon    : LABKEY.contextPath + '/' + LABKEY.extJsRoot_41 + '/resources/themes/images/access/qtip/close.gif',
                    tooltip : 'Delete'
                }],
                listeners : {
                    click : function(col, td, idx) { this.onDeleteCategory(parentCmp, store, idx, idxMgr, true); },
                    scope : this
                }
            }],
            listeners : {
                edit : function(editor, e) {
                    e.grid.getStore().sync({
                        success : function() {
                            e.grid.getStore().load();
                        },
                        failure : function(batch) {
                            if (batch.operations && batch.operations.length > 0) {
                                if (!batch.operations[0].request.scope.reader.jsonData.success) {
                                    var mb = Ext4.Msg.alert('Category Management', batch.operations[0].request.scope.reader.jsonData.message);
                                    idxMgr.register(mb);
                                }
                            }
                            else {
                                Ext4.Msg.alert('Category Managment', 'Failed to save updates.');
                                idxMgr.register(mb);
                            }
                            e.grid.getStore().load();
                        }
                    });
                }
            },
            mutliSelect : false,
            cls     : 'iScroll',
            plugins : [cellEditing],
            selType : 'rowmodel',
            scope   : this
        });

        return grid;
    },

    /**
     * @private
     * @param store
     * @param idx
     * @param idxMgr
     * @param subcategory
     */
    onDeleteCategory : function(grid, store, idx, idxMgr, subcategory) {
        var label = store.getAt(idx).data.label;

        // hide subcategory
        if (!subcategory && this._subwinID) {
            var sw = Ext4.getCmp(this._subwinID);
            if (sw && sw.isVisible())
                sw.hide();
        }

        var msg = Ext4.Msg.show({
            title : 'Delete Category',
            msg   : 'Please confirm you would like to <b>DELETE</b> \'' + Ext4.htmlEncode(label) + '\' from the set of categories. Any views using this category will be marked uncategorized.',
            buttons : Ext4.MessageBox.OKCANCEL,
            icon    : Ext4.MessageBox.WARNING,
            fn      : function(btn){
                if (btn == 'ok') {
                    store.removeAt(idx);
                    store.sync({
                        success : function() {
                            store.load();
                            grid.fireEvent('categorychange', grid);
                        }
                    });
                }
            },
            scope  : this
        });

        if (idxMgr) {
            idxMgr.register(msg);
            idxMgr.bringToFront(msg);
        }
    },

    /**
     * @private
     */
    initializeCategoriesStore : function(opts) {

        var options = {};
        Ext4.apply(options, opts, {index : 1});

        var extraParams = {
            // These parameters are required for specific webpart filtering
            pageId : options.pageId,
            index  : options.index,
            parent : options.categoryId
        };

        console.log(extraParams);

        if (extraParams.parent == undefined) {
            extraParams.parent = -1;
        }

        var config = {
            pageSize: 100,
            model   : 'Dataset.Browser.Category',
            autoLoad: true,
            autoSync: false,
            proxy   : {
                type   : 'ajax',
                api    : {
                    create  : LABKEY.ActionURL.buildURL('study', 'saveCategories.api'),
                    read    : LABKEY.ActionURL.buildURL('study', 'getCategories.api'),
                    update  : LABKEY.ActionURL.buildURL('study', 'saveCategories.api'),
                    destroy : LABKEY.ActionURL.buildURL('study', 'deleteCategories.api')
                },
                extraParams : extraParams,
                reader : { type : 'json', root : 'categories' },
                writer : { type : 'json', root : 'categories', allowSingle : false }
            },
            listeners : {
                load : function(s) { s.sort('displayOrder', 'ASC'); }
            }
        };

        return Ext4.create('Ext.data.Store', config);
    }
});