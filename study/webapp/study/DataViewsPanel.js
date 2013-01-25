/*
 * Copyright (c) 2011-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

/** EXPERIMENTAL : Extend FilterBy to Ext.data.TreeStore **/
Ext4.override(Ext4.data.TreeStore, {

    // overridden
    filterBy: function(fn, scope) {
        var me = this;
        return me.filterLeavesBy(fn, scope);
    },

    filterLeavesBy: function(fn, scope) {
        var me = this,
            leaves = Ext4.Array.filter(me.tree.flatten(), function(node) { return node.isLeaf(); });

        me.snapshot = me.snapshot || me.getRootNode().copy(null, true);

        return Ext4.Array.filter(leaves, function(n) {
            var result = fn.call(scope, n);
            if (!result) {
                n.remove();
            }
            return result;
        }, scope);
    },

    clearFilter : function() {
        var me = this, i, tmp = [];

        if (me.snapshot) {

            for (i=0; i < me.snapshot.childNodes.length; i++) {
                tmp.push(me.snapshot.childNodes[i].copy(null, true));
            }

            me.getRootNode().removeAll();
            me.getRootNode().appendChild(tmp);
            delete me.snapshot;
        }
        return me;
    }
});

/**
 * This is an extended model used to render the TreeStore. Due to performance
 * the afterCommit method has been disabled. It is not recommneded you use this model.
**/
Ext4.define('Ext.data.FastModel', {
    extend : 'Ext.data.Model',
    afterCommit : function(){}
});

Ext4.define('LABKEY.ext4.DataViewsPanel', {

    extend : 'Ext.panel.Panel',

    constructor : function(config) {

        Ext4.QuickTips.init();

        // TODO : Make the following not required since it might not be its own webpart
        // REQUIRES:
        // pageId
        // index
        // webpartId

        Ext4.applyIf(config, {
            layout : 'border',
            minWidth: 625,
            frame  : false, border : false,
            allowCustomize : true
        });

        /* Experimental -- Set to true to view data as nested tree */
        this.asTree = false;

        // The following default to type 'string'
        var fields = [
            {name : 'id'},
            {name : 'category'},
            {name : 'categoryDisplayOrder', type : 'int'},
            {name : 'created',              type : 'date'},
            {name : 'createdBy'},
            {name : 'createdByUserId',      type : 'int'},
            {name : 'authorUserId',
                convert : function(v, record){
                    if (record.raw && record.raw.author)
                        return record.raw.author.userId;
                    else return 0;
                }
            },
            {name : 'authorDisplayName',
                convert : function(v, record){
                    if (record.raw && record.raw.author)
                        return record.raw.author.displayName;
                    else return '';
                }
            },
            {name : 'container'},
            {name : 'dataType'},
            {name : 'editable',             type : 'boolean'},
            {name : 'editUrl'},
            {name : 'type'},
            {name : 'description'},
            {name : 'displayOrder',         type : 'int'},
            {name : 'shared',               type : 'boolean'},
            {name : 'visible',              type : 'boolean'},
            {name : 'icon'},
            {name : 'modified',             type : 'date'},
            {name : 'modifiedBy'},
            {name : 'refreshDate',          type : 'date'},
            {name : 'name'},
            {name : 'access'},
            {name : 'runUrl'},
            {name : 'runTarget', defaultValue:undefined},
            {name : 'detailsUrl'},
            {name : 'thumbnail'},
            {name : 'thumbnailType'},
            {name : 'allowCustomThumbnail'},
            {name : 'status'}
        ];

        // define Models
        Ext4.define('Dataset.Browser.View', {
            extend : this.asTree ? 'Ext.data.FastModel' : 'Ext.data.Model',
            fields : fields
        });

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

        Ext4.define('LABKEY.data.User', {
            extend : 'Ext.data.Model',
            fields : [
                {name : 'userId',       type : 'int'},
                {name : 'displayName'               }
            ]
        });

        this.callParent([config]);

        if (this.isCustomizable())
            this.addEvents('enableCustomMode', 'disableCustomMode');
    },

    initComponent : function() {

        this.customMode = false;
        this.searchVal = "";
        this._height;

        this.items = [];
        this.editInfo = {};

        this.store = null;
        this.centerPanel = null;
        this.gridPanel = null;

        // primary display panels
        this.items = this.initializeBorderLayout();
        this.items.push(this.initCenterPanel());

        // secondary display panels
        this.customPanel = this.initCustomization();

        this.callParent();
    },

    initializeBorderLayout : function() {
        var regions = ['north']; // only need north at this time
        var items = [];

        for (var r=0; r < regions.length; r++) {
            this[regions[r]] = Ext4.create('Ext.panel.Panel', {
                layout : 'fit',
                region : regions[r],
                style : 'margin-bottom: 10px',
                hidden : true,
                preventHeader : true,
                border : false, frame : false
            });
            items.push(this[regions[r]]);
        }
        return items;
    },

    getViewProxy : function() {
        return {
            type   : 'ajax',
            url    : LABKEY.ActionURL.buildURL('study', 'browseData' + (this.asTree ? 'Tree' : '') + '.api'),
            extraParams : {
                // These parameters are required for specific webpart filtering
                pageId      : this.pageId,
                index       : this.index,
                returnUrl   : this.returnUrl
            },
            reader : this.asTree ? 'json' : { type : 'json', root : 'data' }
        };
    },

    initializeViewStore : function(useGrouping) {

        if (this.store)
            return this.store;

        var config = {
            pageSize: 100,
            model   : 'Dataset.Browser.View',
            autoLoad: true,
            proxy   : this.getViewProxy(),
            listeners : {
                beforeload : function(){
                    if (this.gridPanel)
                        this.gridPanel.setLoading(true);
                },
                load : {fn : this.onViewLoad, scope: this},
                scope: this
            },
            scope : this
        };

        this.store = Ext4.create(this.asTree ? 'Ext.data.TreeStore' : 'Ext.data.Store', config);

        if (useGrouping && !this.asTree) {

            // 15764
            this.store.groupers.add({
                property : 'category',
                dataProperty : 'categoryDisplayOrder',
                sorterFn : function(a, b) {
                    var me = this,
                        c1 = me.getRoot(a)[me.dataProperty],
                        c2 = me.getRoot(b)[me.dataProperty];

                    // If both are 0 display order than they are considered unintialized
                    // just return the alphabetical order
                    if (c1 == 0 && c2 == 0) {
                        var a1 = me.getRoot(a)[me.property],
                            a2 = me.getRoot(b)[me.property];
                        return a1 > a2 ? 1 : (a1 < a2 ? -1 : 0);
                    }
                    return c1 > c2 ? 1 : (c1 < c2 ? -1 : 0);
                }
            });
        }
        return this.store;
    },

    initializeCategoriesStore : function(categoryid) {

        var extraParams = {
            // These parameters are required for specific webpart filtering
            pageId : this.pageId,
            index  : this.index,
            parent : categoryid || -1
        };

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
            listeners : {
                load : function(s, recs, success, operation, ops) {
                    s.sort('displayOrder', 'ASC');
                }
            }
        };

        return Ext4.create('Ext.data.Store', config);
    },

    initializeUserStore : function() {

        var config = {
            model   : 'LABKEY.data.User',
            autoLoad: true,
            proxy   : {
                type   : 'ajax',
                url : LABKEY.ActionURL.buildURL('user', 'getUsers.api'),          
                reader : {
                    type : 'json',
                    root : 'users'
                }
            }
        };

        return Ext4.create('Ext.data.Store', config);
    },

    initCenterPanel : function() {

        this.centerPanel = Ext4.create('Ext.panel.Panel', {
            border   : false,
            frame    : false,
            layout   : 'fit'
        });

        this.centerPanel.on('render', this.configureGrid, this);

        return Ext4.create('Ext.panel.Panel', {
            border : false, frame : false,
            layout : 'fit',
            flex   : 4,
            region : 'center',
            items  : [this.centerPanel]
        });
    },

    /**
     * Invoked once when the grid is initially setup
     */
    configureGrid : function() {

        var handler = function(json){
            this.centerPanel.getEl().unmask();

            if (json.webpart) {
                this._height = parseInt(json.webpart.height);
                this.setHeight(this._height);
            }
            this.dateFormat = json.dateFormat;
            this.dateRenderer = Ext4.util.Format.dateRenderer(json.dateFormat);
            this.editInfo = json.editInfo;

            this.initGrid(true, json.visibleColumns);
        };
        
        this.centerPanel.getEl().mask('Initializing...');
        this.getConfiguration(handler, this);
    },

    /**
     * Invoked each time the column model is modified from the customize view
     */
    updateConfiguration : function() {

        var handler = function(json){
            this.centerPanel.getEl().unmask();
            this.gridPanel.on('reconfigure', function() {
                if (this._height)
                    this.setHeight(this._height);
            }, this, {single: true});
            this.gridPanel.reconfigure(this.gridPanel.getStore(), this.initGridColumns(json.visibleColumns));
            this.store.load();
        };

        this.centerPanel.getEl().mask('Initializing...');
        this.getConfiguration(handler, this);
    },

    getConfiguration : function(handler, scope) {

        var extraParams = {
            // These parameters are required for specific webpart filtering
            includeData : false,
            pageId : this.pageId,
            index  : this.index
        };

        Ext4.Ajax.request({
            url    : LABKEY.ActionURL.buildURL('study', 'browseData.api', null, extraParams),
            method : 'GET',
            success: function(response) {
                if (handler)
                {
                    var json = Ext4.decode(response.responseText);
                    handler.call(scope || this, json);
                }
            },
            failure : function() {
                Ext4.Msg.alert('Failure');
            },
            scope : this
        });
    },

    initGrid : function(useGrouping, visibleColumns) {

        /**
         * Enable Grouping by Category
         */
        var groupingFeature = Ext4.create('Ext4.grid.feature.Grouping', {
            groupHeaderTpl : '&nbsp;{name}' // &nbsp; allows '+/-' to show up
        });

        /**
         * Tooltip Template
         */
        var tipTpl = new Ext4.XTemplate('<tpl>' +
                '<div class="data-views-tip-content">' +
                '<table cellpadding="20" cellspacing="100">' +
                '<tpl if="data.category != undefined && data.category.length">' +
                '<tr><td>Source:</td><td>{[fm.htmlEncode(values.data.category)]}</td></tr>' +
                '</tpl>' +
                '<tpl if="data.createdBy != undefined && data.createdBy.length">' +
                '<tr><td>Created By:</td><td>{[fm.htmlEncode(values.data.createdBy)]}</td></tr>' +
                '</tpl>' +
                '<tpl if="data.authorDisplayName != undefined && data.authorDisplayName.length">' +
                '<tr><td>Author:</td><td>{[fm.htmlEncode(values.data.authorDisplayName)]}</td></tr>' +
                '</tpl>' +
                '<tpl if="data.type != undefined && data.type.length">' +
                '<tr><td>Type:</td><td>{[fm.htmlEncode(values.data.type)]}</td></tr>' +
                '</tpl>' +
                '<tpl if="data.status != undefined && data.status.length">' +
                '<tr><td>Status:</td><td>{[fm.htmlEncode(values.data.status)]}</td></tr>' +
                '</tpl>' +
                '<tpl if="data.refreshDate != undefined">' +
                '<tr><td valign="top">Data Cut Date:</td><td>{[this.renderDate(values.data.refreshDate)]}</td></tr>' +
                '</tpl>' +
                '<tpl if="data.description != undefined && data.description.length">' +
                '<tr><td valign="top">Description:</td><td>{[fm.htmlEncode(values.data.description)]}</td></tr>' +
                '</tpl>' +
                '<tpl if="data.thumbnail != undefined && data.thumbnail.length">' +
                '</tpl>' +
                '</table>' +
                '<div class="thumbnail"><img src="{data.thumbnail}"/></div>' +
                '</div>' +
                '</tpl>',
        {
            renderDate : function(data) {
                return this.initialConfig.dateRenderer(data);
            }
        }, {dateRenderer : this.dateRenderer});

        this._tipID = Ext4.id();
        var _tipID = this._tipID;

        function getTipPanel()
        {
            var tipPanel = Ext4.create('Ext.panel.Panel', {
                id     : _tipID,
                layout : 'fit',
                border : false, frame : false,
                height : '100%',
                cls    : 'tip-panel',
                tpl    : tipTpl,
                renderTipRecord : function(rec){
                    tipPanel.update(rec);
                }
            });

            return tipPanel;
        }

        function initToolTip(view)
        {
            var _w = 500;
            var _h = 325;
            var _active;

            function renderToolTip(tip)
            {
                if (_active)
                    tip.setTitle(_active.get('name'));
                var content = Ext4.getCmp(_tipID);
                if (content)
                {
                    content.renderTipRecord(_active);
                }
            }

            function loadRecord(t) {
                var r = view.getRecord(t.triggerElement.parentNode);
                if (r) _active = r;
                else {
                    /* This usually occurs when mousing over grouping headers */
                    _active = null;
                    return false;
                }
                return true;
            }

            view.tip = Ext4.create('Ext.tip.ToolTip', {
                target   : view.el,
                delegate : '.x4-name-column-cell',
                trackMouse: false,
                width    : _w,
                height   : _h,
                html     : null,
                autoHide : true,
                anchorToTarget : true,
                anchorOffset : 100,
                showDelay: 1000,
                cls      : 'data-views-tip-panel',
                defaults : { border: false, frame: false},
                items    : [getTipPanel()],
                listeners: {
                    // Change content dynamically depending on which element triggered the show.
                    beforeshow: function(tip) {
                        var loaded = loadRecord(tip);
                        renderToolTip(tip);
                        return loaded; // return false to not show tip
                    },
                    scope : this
                },
                scope : this
            });
        }

        this.gridPanel = Ext4.create(this.asTree ? 'Ext.tree.Panel' : 'Ext.grid.Panel', {
            id       : 'data-browser-grid-' + this.webpartId,
            store    : this.initializeViewStore(useGrouping),
            tbar     : this.initSearch(),
            border   : false, frame: false,
            layout   : 'fit',
            cls      : 'iScroll', // webkit custom scroll bars
            scroll   : 'vertical',
            columns  : this.initGridColumns(visibleColumns),
            multiSelect: true,
            region   : 'center',
            viewConfig : {
                stripeRows : true,
                listeners  : {
                    render : initToolTip,
                    scope  : this
                },
                emptyText : '0 Matching Results'
            },
            selType   : 'rowmodel',
            features  : [groupingFeature],
            listeners : {
                itemclick : function(view, record, item, index, e, opts) {
                    // TODO: Need a better way to determine the clicked item
                    var cls = e.target.className;
                    if (cls.search(/edit-views-link/i) >= 0)
                        this.onEditClick(view, record);
                },
                afterlayout : function(p) {
                    /* Apply selector for tests */
                    var el = Ext4.query("*[class=x4-grid-table x4-grid-table-resizer]");
                    if (el && el.length == 1) {
                        el = el[0];
                        if (el && el.setAttribute) {
                            el.setAttribute('name', 'data-browser-table');
                        }
                    }
                },
                scope : this
            },

            /* Tree Configurations */
            rootVisible : false,

            scope     : this
        });

        this.gridPanel.getStore().on('groupchange', this.onGroupChange, this);

        this.centerPanel.add(this.gridPanel);
    },

    initGridColumns : function(visibleColumns) {

        var detailsTpl =
                '<tpl if="detailsUrl">' +
                    '<a data-qtip="Click to navigate to the Detail View" href="{detailsUrl}">' +
                        '<img data-qtip="Details" height="16px" width="16px" src="' + LABKEY.ActionURL.getContextPath() + '/reports/details.png" alt="Details...">' +
                   '</a>' +
                '</tpl>';

        var nameTpl =
                '<div height="16px" width="100%">' +
                    '<tpl if="icon != undefined && icon != \'\'">' +
                        '<div style="float: left;">' +
                        '<img height="16px" width="16px" src="{icon}" alt="{type}" style="vertical-align: bottom; margin-right: 5px;">' +
                        '</div>' +
                    '</tpl>' +
                    '<div style="padding-left: 20px; white-space:normal !important;">' +
                    '<a href="{runUrl}" {[ values.runTarget ? "target=\'" + values.runTarget + "\'" : "" ]} > {name:htmlEncode}</a>' +
                    '</div>' +
                '</div>';

        var _columns = [];

        _columns.push({
            id       : 'edit-column-' + this.webpartId,
            text     : '&nbsp;',
            width    : 40,
            sortable : false,
            menuDisabled : true,
            renderer : function(view, meta, rec, idx, colIdx, store) {
                if (!this._inCustomMode()) {
                    meta.style = 'display: none;';  // what a nightmare
                }

                // an item need an edit info interface to be editable
                if (!this.editInfo[rec.data.dataType]) {
                    return '<span height="16px" class="edit-link-cls-' + this.webpartId + '"></span>';
                }
                return '<span height="16px" class="edit-link-cls-' + this.webpartId + ' edit-views-link"></span>';
            },
            hidden   : true,
            scope    : this
        },{
            xtype    : this.asTree ? 'treecolumn' : 'templatecolumn',
            text     : 'Name',
            flex     : 1,
            sortable : true,
            dataIndex: 'name',
            minWidth : 200,
            tdCls    : 'x4-name-column-cell',
            tpl      :  nameTpl,
            scope    : this
        },{
            id       : 'category-column-' + this.webpartId,
            text     : 'Category',
            flex     : 1,
            sortable : true,
            dataIndex: 'category',
            renderer : Ext4.util.Format.htmlEncode,
            hidden   : true
        });

        if (visibleColumns['Type'] && visibleColumns['Type'].checked)
        {
            _columns.push({
                xtype    : 'templatecolumn',
                text     : 'Type',
                width    : 75,
                sortable : true,
                dataIndex: 'type',
                tdCls    : 'type-column',
                tpl      : '<tpl>{type}</tpl>',
                scope    : this
            });
        }

        if (visibleColumns['Details'] && visibleColumns['Details'].checked)
        {
            _columns.push({
                xtype    : 'templatecolumn',
                text     : 'Details',
                width    : 60,
                sortable : true,
                dataIndex: 'detailsUrl',
                tdCls    : 'type-column',
                tpl      : detailsTpl,
                scope    : this
            });
        }

        if(visibleColumns['Data Cut Date'] && visibleColumns['Data Cut Date'].checked){
             _columns.push({
                 text     : 'Data Cut Date',
                 width    : 120,
                 sortable : true,
                 dataIndex: 'refreshDate',
                 renderer : this.dateRenderer,
                 scope    : this
             });
        }

        if (visibleColumns['Status'] && visibleColumns['Status'].checked)
        {
            var statusTpl = '<tpl if="status == \'Draft\'">' +
                    '<img data-qtip="Draft" height="16px" width="16px" src="' + LABKEY.ActionURL.getContextPath() + '/reports/icon_draft.png" alt="Draft">' +
                    '</tpl>' +
                    '<tpl if="status == \'Final\'">' +
                    '<img data-qtip="Final" height="16px" width="16px" src="' + LABKEY.ActionURL.getContextPath() + '/reports/icon_final.png" alt="Final">' +
                    '</tpl>' +
                    '<tpl if="status == \'Locked\'">' +
                    '<img data-qtip="Locked" height="16px" width="16px" src="' + LABKEY.ActionURL.getContextPath() + '/reports/icon_locked.png" alt="Locked">' +
                    '</tpl>' +
                    '<tpl if="status == \'Unlocked\'">' +
                    '<img data-qtip="Unlocked" height="16px" width="16px" src="' + LABKEY.ActionURL.getContextPath() + '/reports/icon_unlocked.png" alt="Unlocked">' +
                    '</tpl>';

            _columns.push({
                xtype    : 'templatecolumn',
                text     : 'Status',
                width    : 60,
                sortable : true,
                tdCls    : 'type-column',
                dataIndex: 'status',
                tpl      : statusTpl
            });
        }

        if (visibleColumns['Modified'] && visibleColumns['Modified'].checked)
        {
            _columns.push({
                text     : 'Modified',
                width    : 100,
                sortable : true,
                renderer : this.dateRenderer,
                dataIndex: 'modified'
            });
        }

        if (visibleColumns['Author'] && visibleColumns['Author'].checked)
        {
            _columns.push({
                text     : 'Author',
                width    : 100,
                sortable : false,
                dataIndex: 'authorDisplayName',
                scope    : this
            });
        }

        if (visibleColumns['Access'] && visibleColumns['Access'].checked)
        {
            _columns.push({
                header   : 'Access',
                width    : 100,
                sortable : false,
                dataIndex: 'access'
            });
        }

        return _columns;
    },

    initSearch : function() {

        var filterSearch = function() {
            this.searchVal = searchField.getValue();
            this.hiddenFilter();
        };

        var filterTask = new Ext4.util.DelayedTask(filterSearch, this);

        var searchField = Ext4.create('Ext.form.field.Text', {
            emptyText       : 'name, category, etc.',
            enableKeyEvents : true,
            cls             : 'dataset-search',
            size            : 57,
            height          : 25,
            width           : 400,
            border: false, frame : false,
            listeners       : {
                change       : function(cmp, e){
                    filterTask.delay(350);
                }
            }
        });

        // hahaha
        this.mineField = Ext4.create('Ext.form.field.Checkbox', {
            boxLabel        : '<span data-qtip="Check to show only views that have either been created by me or that list me as the author.">&nbsp;Mine</span>',
            boxLabelAlign   : 'before',
            border          : false, frame : false,
            listeners       : {
                change : function(cmp, checked){
                    this.searchMine = checked;
                    filterTask.delay(100);
                },
                scope : this
            },
            scope : this
        });

        // toolbar
        return {
            height  : 30,
            items   : [{
                xtype   : 'panel',
                border  : false,
                layout  : {type:'table'},
                items   : [searchField, {
                    xtype   : 'box',
                    border  : 0,
                    autoEl  : {
                        tag : 'img',
                        style : {
                            position : 'relative',
                            left     : '-20px'
                        },
                        src : LABKEY.ActionURL.getContextPath() + '/_images/search.png'
                    }}
                ]},
                '->', this.mineField
            ]
        };
    },

    initCustomization : function() {

        var customPanel = Ext4.create('Ext.panel.Panel', {
            layout : 'fit',
            border : false, frame : false
        });

        this.north.setHeight(220);
        this.north.add(customPanel);

        this.on('enableCustomMode',  this.onEnableCustomMode,  this);
        this.on('disableCustomMode', this.onDisableCustomMode, this);

        return customPanel;
    },

    onViewLoad : function(s, recs, success, operation, ops) {
        if (this.gridPanel)
            this.gridPanel.setLoading(false);

        this.hiddenFilter();
    },

    onGroupChange : function(store, groupers) {

        // 13878: Reverting to default in data browser -- make sure category column is visible if it is not being grouped by
        if (groupers.items && groupers.items.length == 1) {

            var catColumn = Ext4.getCmp('category-column-' + this.webpartId);

            if (catColumn) {
                if (groupers.items[0].property == catColumn.dataIndex) {
                    catColumn.hide();
                }
                else {
                    if (!catColumn.isVisible())
                        catColumn.show();
                }
            }

        }
    },

    /**
     * Aggregates the filters applied by search and by custom mode.
     */
    hiddenFilter : function() {

        this.store.clearFilter();
        var _custom = this._inCustomMode();
        this.store.sort([
            {
                property : 'name',
                direction: 'ASC'
            }
        ]);
        if (!this.asTree) {
            this.store.filterBy(function(rec, id){

            var answer = true;
            if (rec.data && this.searchVal && this.searchVal != "")
            {
                var t = new RegExp(Ext4.escapeRe(this.searchVal), 'i');
                var s = '';
                if (rec.data.name)
                    s += rec.data.name;
                if (rec.data.category)
                    s += rec.data.category;
                if (rec.data.type)
                    s += rec.data.type;
                if (rec.data.modified)
                    s += rec.data.modified;
                if (rec.data.authorDisplayName)
                    s += rec.data.authorDisplayName;
                if (rec.data.status)
                    s += rec.data.status;
                answer = t.test(s);
            }

            // the show mine checkbox will match if the current user is either the author or the creator
            if (rec.data && answer && this.searchMine)
            {
                if ((rec.data.authorUserId != LABKEY.user.id) && (rec.data.createdByUserId != LABKEY.user.id))
                    return false;
            }

            // custom mode will show hidden
            if (_custom)
                return answer;

            // otherwise never show hidden records
            if (!rec.data.visible)
                return false;

            return answer;
        }, this);
        }
        else {
            this.store.clearFilter();
            if (this.searchVal && !this.searchVal == "") {
                this.store.filterBy(function(node) {
                    if (!(node.data.name.indexOf(this.searchVal) > -1)) {
                        return false;
                    }
                    return true;
                }, this);
            }
        }
    },

    isCustomizable : function() {
        return this.allowCustomize;
    },

    // private
    _inCustomMode : function() {
        return this.customMode;
    },

    /**
     * Takes the panel into/outof customize mode. Customize mode allows users to view edit links,
     * adminstrate view categories and determine what data types should be shown.
     */
    customize : function() {

        if (!this.isCustomizable())
            return false;

        this.fireEvent((this._inCustomMode() ? 'disableCustomMode' : 'enableCustomMode'), this);
    },

    onEnableCustomMode : function() {

        var handler = function(json) {
            this.north.getEl().unmask();
            this._displayCustomMode(json);
        };

        this.customMode = true;
        this.north.show(null, function(){
            this.north.getEl().mask('Loading Customize...');
        }, this);
        this.getConfiguration(handler, this);
    },

    onDisableCustomMode : function() {
        if (this.customPanel && this.customPanel.isVisible())
        {
            this.north.hide();
        }

        this.customMode = false;

        this.hiddenFilter();

        // hide edit column
        this._getEditColumn().hide();
    },

    // private
    _displayCustomMode : function(data) {

        // reset the update config flag, we only want to re-fetch the configuration if the visible columns change
        this.updateConfig = false;

        // panel might already exist
        if (this.customPanel && this.customPanel.items.length > 0)
        {
            this.hiddenFilter();

            // show edit column
            this._getEditColumn().show();

            this.north.getEl().unmask();
            return;
        }

        var cbItems = [],
                cbColumns = [],
                sizeItems = [];

        var heights = {
            450 : 'small',
            700 : 'medium',
            1000 : 'large'
        };

        if (data.types)
        {
            for (var type in data.types) {
                if(data.types.hasOwnProperty(type))
                {
                    cbItems.push({boxLabel : type, name : type, checked : data.types[type], width: 150, uncheckedValue : '0'});
                }
            }
        }

        if (data.visibleColumns)
        {
            for (var col in data.visibleColumns) {
                var prop = data.visibleColumns[col];
                cbColumns.push({boxLabel : col, name : col, checked : prop.checked, uncheckedValue : '0', width: 115, maxWidth: 150, handler : function(){this.updateConfig = true;}, scope : this});
            }
        }

        for (var h in heights) {
            if (heights.hasOwnProperty(h)) {
                sizeItems.push({boxLabel : heights[h], name : 'webpart.height', inputValue : h, minWidth: 75, checked : false, handler : function(grp){
                    // this is called for each 'change' -- only bother with true case
                    if (grp.getValue()) {
                        this.updateConfig = true;
                        this._height = parseInt(grp.inputValue); // WATCH OUT : this MUST be an integer.
                    }
                }, scope : this});
                if (this._height == h)
                    sizeItems[sizeItems.length-1].checked = true;
            }
        }

        var namePanel = Ext4.create('Ext.form.Panel', {
            border : false,
            fieldDefaults  : {
                labelAlign : 'top',
                labelWidth : 130,
                labelSeparator : ''
            },
            items : [{
                xtype      : 'textfield',
                fieldLabel : 'Name',
                name       : 'webpart.title',
                allowBlank : false,
                width      : 225,
                value      : data.webpart.title ? data.webpart.title : data.webpart.name
            },{
                xtype      : 'radiogroup',
                fieldLabel : 'Display',
                columns    : 3,
                height     : 50,
                items      : sizeItems
            }]
        });
        
        var formPanel = Ext4.create('Ext.form.Panel',{
            border : false,
            layout : 'hbox',
            fieldDefaults  :{
                labelAlign : 'top',
                labelWidth : 130,
                labelSeparator : ''
            },
            items : [namePanel,
            {
                xtype      : 'checkboxgroup',
                fieldLabel : 'Types (All Users)',
                colspan    : 1,
                columns    : 1,
                flex       : 0.75,
                maxWidth   : 225,
                style      : 'margin-left: 20px;',
                items      : cbItems
            },{
                xtype      : 'checkboxgroup',
                fieldLabel : 'Columns (All Users)',
                columns    : 2,
                flex       : 1,
                maxWidth   : 300,
                minHeight  : 120,
                items      : cbColumns
            },{
                xtype   : 'hidden',
                name    : 'webPartId',
                value   : this.webpartId
            }],
            buttons : [{
                text    : 'Manage Categories',
                handler : this.onManageCategories,
                scope   : this
            }],
            buttonAlign : 'left',
            scope : this
        });

        var panel = Ext4.create('Ext.panel.Panel',{
            bodyPadding : 10,
            layout : 'fit',
            items : [formPanel],
            buttons : [{
                text    : 'Cancel',
                handler : function() {
                    this.fireEvent('disableCustomMode');
                },
                scope   : this
            },{
                text     : 'Save',
                formBind : true,
                handler  : function() {
                    var form = formPanel.getForm(); // this.up('form')
                    if (form.isValid())
                    {
                        this.north.getEl().mask('Saving...');
                        Ext4.Ajax.request({
                            url    : LABKEY.ActionURL.buildURL('project', 'customizeWebPartAsync.api', null, form.getValues()),
                            method : 'POST',
                            success : function() {
                                this.north.getEl().unmask();
                                this.north.hide();

                                // Modify Title
                                var titleEl = Ext4.query('span[class=labkey-wp-title-text]:first', 'webpart_' + this.webpartId);
                                if (titleEl && (titleEl.length >= 1))
                                {
                                    titleEl[0].innerHTML = LABKEY.Utils.encodeHtml(form.findField('webpart.title').getValue());
                                }
                                // else it will get displayed on refresh

                                this.fireEvent('disableCustomMode');

                                if (this.updateConfig)
                                    this.updateConfiguration();
                                else
                                    this.store.load();
                            },
                            failure : function() {
                                Ext4.Msg.alert('Failure');
                            },
                            scope : this
                        });
                    }
                },
                scope : this
            }],
            scope : this
        });

        this.hiddenFilter();

        // show edit column
        this._getEditColumn().show();

        this.customPanel.add(panel);
        this.north.getEl().unmask();
    },

    _getEditColumn : function() {
        return Ext4.getCmp('edit-column-' + this.webpartId);
    },

    onEditClick : function(view, record) {

        // grab the map of available fields from the edit info for the view type
        var editInfo = this.editInfo[record.data.dataType] || {};
        var formItems = [];

        /* Record 'id' is required */
        var editable = true;
        if (record.data.id == undefined || record.data.id == "")
        {
            console.warn('ID is required');
            editable = false;
        }

        // hidden items
        formItems.push({
            xtype : 'hidden',
            name  : 'id',
            value : record.data.id
        },{
            xtype : 'hidden',
            name  : 'dataType',
            value : record.data.dataType
        });

        var viewForm = Ext4.create('LABKEY.study.DataViewPropertiesPanel', {
            record          : record,
            extraItems      : formItems,
            dateFormat      : this.dateFormat,
            visibleFields   : {
                author  : editInfo['author'],
                status  : editInfo['status'],
                datacutdate : editInfo['refreshDate'],
                category    : editInfo['category'],
                description : editInfo['description'],
                type        : true,
                visible     : editInfo['visible'],
                created     : true,
                shared      : editInfo['shared'],
                modified    : true,
                customThumbnail : editInfo['customThumbnail']
            },
            buttons     : [{
                text : 'Save',
                formBind: true,
                handler : function(btn) {
                    var form = btn.up('form').getForm();
                    if (form.isValid())
                    {
                        editWindow.getEl().mask("Saving...");
                        form.submit({
                            url : LABKEY.ActionURL.buildURL('study', 'editView'),
                            method : 'POST',
                            success : function(){
                                this.onEditSave();
                                editWindow.getEl().unmask();
                                editWindow.close();
                            },
                            failure : function(response){
                                editWindow.getEl().unmask();
                                Ext4.Msg.alert('Failure', Ext4.decode(response.responseText).exception);
                            },
                            scope : this
                        });
                    }
                },
                scope   : this
            }]
        });

        var editWindow = Ext4.create('Ext.window.Window', {
            width  : 460,
            maxHeight : 750,
            layout : 'fit',
            cls    : 'data-window',
            draggable : false,
            modal  : true,
            title  : record.data.name,
            defaults: {
                border: false, frame: false
            },
            bodyPadding : 10,
            items : viewForm,
            scope : this
        });

        editWindow.show();
    },

    onEditSave : function() {
        this.store.load();
    },

    onManageCategories : function(btn) {

        var cellEditing = Ext4.create('Ext.grid.plugin.CellEditing', {
            pluginId : 'categorycell',
            clicksToEdit: 2
        });

        var confirm = false;
        var store = this.initializeCategoriesStore();
        var winID = Ext4.id();
        var subwinID = Ext4.id();

        var grid = Ext4.create('Ext.grid.Panel', {
            store    : store,
            border   : false, frame: false,
            scroll   : 'vertical',
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
                    click : function(col, grid, idx, evt, x, y, z)
                    {
                        var label = store.getAt(idx).data.label;

                        Ext4.Msg.show({
                            title : 'Delete Category',
                            msg   : 'Please confirm you would like to <b>DELETE</b> \'' + Ext4.htmlEncode(label) + '\' from the set of categories.',
                            buttons : Ext4.MessageBox.OKCANCEL,
                            icon    : Ext4.MessageBox.WARNING,
                            fn      : function(btn){
                                if (btn == 'ok') {
                                    store.removeAt(idx);
                                }
                            },
                            scope  : this
                        });
                    },
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
                            display++;
                        }, this);
                    }
                }
            },
            listeners : {
                select : function(g, rec) {
                    var w = Ext4.getCmp(winID);

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
                            Ext4.create('Ext.Window', {
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
                                items : [this.getCategoryGrid(rec.data.rowid, gid)],
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
                        }
                    }
                },
                scope : this
            },
            plugins   : [cellEditing],
            selType   : 'rowmodel',
            scope     : this
        });

        var categoryOrderWindow = Ext4.create('Ext.window.Window', {
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
                }
            },{
                text    : 'Done',
                handler : function() {
                    grid.getStore().sync();
                    categoryOrderWindow.close();
                }
            }],
            listeners : {
                beforeclose : function() {
                    if (confirm) {
                        this.onEditSave();
                    }
                    var sw = Ext4.getCmp(subwinID);
                    if (sw) {
                        sw.close();
                    }
                },
                scope : this
            },
            scope     : this
        });

        categoryOrderWindow.show();
    },

    getCategoryGrid : function(categoryid, gridid) {

        var cellEditing = Ext4.create('Ext.grid.plugin.CellEditing', {
            pluginId : 'subcategorycell',
            clicksToEdit : 2
        });

        var store = this.initializeCategoriesStore(categoryid);

        return Ext4.create('Ext.grid.Panel', {
            id : gridid || Ext.id(),
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
                    click : function(col, grid, idx, evt, x, y, z)
                    {
                        var label = store.getAt(idx).data.label;
                        var id    = store.getAt(idx).data.rowid;

                        Ext4.Msg.show({
                            title : 'Delete Category',
                            msg   : 'Please confirm you would like to <b>DELETE</b> \'' + Ext4.htmlEncode(label) + '\' from the set of categories.',
                            buttons : Ext4.MessageBox.OKCANCEL,
                            icon    : Ext4.MessageBox.WARNING,
                            fn      : function(btn){
                                if (btn == 'ok') {
                                    store.removeAt(idx);
                                }
                            },
                            scope  : this
                        });
                    },
                    scope : this
                }
            }],
            listeners : {
                edit : function(editor, e) {
                    e.grid.getStore().sync();
                }
            },
            mutliSelect : false,
            cls     : 'iScroll',
            plugins : [cellEditing],
            selType : 'rowmodel',
            scope   : this
        });
    }
});
