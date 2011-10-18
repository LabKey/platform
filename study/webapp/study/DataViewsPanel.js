/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.requiresExt4Sandbox(true);

Ext4.define('LABKEY.ext4.DataViewsPanel', {

    extend : 'Ext.panel.Panel',

    constructor : function(config) {

        // TODO : Make the following not required since it might not be its own webpart
        // REQUIRES:
        // pageId
        // index
        // webpartId

        Ext4.applyIf(config, {
            layout : 'fit',
            border : false,
            frame  : false,
            allowCustomize : true
        });

        Ext4.QuickTips.init();

        // Define an override for RowModel to fix page jumping on first click
        // LabKey Issue : 12940: Data Browser - First click on a row scrolls down slightly, doesn't trigger metadata popup
        // Ext issue    : http://www.sencha.com/forum/showthread.php?142291-Grid-panel-jump-to-start-of-the-panel-on-click-of-any-row-in-IE.In-mozilla-it-is-fine&p=640415
        Ext4.define('Ext.selection.RowModelFixed', {
            extend : 'Ext.selection.RowModel',
            alias  : 'selection.rowmodelfixed',

            onRowMouseDown: function(view, record, item, index, e) {
                this.selectWithEvent(record, e);
            }
        });

        var fields = [
            {name : 'category'},
            {name : 'categoryDisplayOrder', type : 'int'},
            {name : 'created',              type : 'date'},
            {name : 'createdBy'},
            {name : 'container'},
            {name : 'editable',             type : 'boolean'},
            {name : 'editUrl'},
            {name : 'description'},
            {name : 'displayOrder',         type : 'int'},
            {name : 'hidden',               type : 'boolean'},
            {name : 'icon'},
            {name : 'inherited',            type : 'boolean'},
            {name : 'modfied',              type : 'date'},
            {name : 'modifiedBy'},
            {name : 'name'},
            {name : 'permissions'},
            {name : 'reportId'},
            {name : 'runUrl'},
            {name : 'schema'},
            {name : 'thumbnail'},
            {name : 'type'},
            {name : 'version'}
        ];

        // define Model
        Ext4.define('Dataset.Browser.View', {
            extend : 'Ext.data.Model',
            fields : fields
        });

        this.callParent([config]);

        if (this.isCustomizable())
            this.addEvents('enableCustomMode', 'disableCustomMode');
    },

    initComponent : function() {

        this.customMode = false;
        this.editLinkCls = 'edit-views-link';

        this.items = [];

        this.store  = this.initializeViewStore();
        this.searchPanel = this.initSearch();
        this.gridPanel   = this.initGrid();
        this.customPanel = this.initCustomization();

        this.items.push(this.customPanel, this.searchPanel, this.gridPanel);

        this.callParent([arguments]);
    },

    initializeViewStore : function() {

        return Ext4.create('Ext.data.Store', {
            pageSize: 100,
            model   : 'Dataset.Browser.View',
            autoLoad: true,
            proxy   : {
                type   : 'ajax',
                url    : LABKEY.ActionURL.buildURL('study', 'browseData.api'),
                extraParams : {
                    // These parameters are required for specific webpart filtering
                    pageId : this.pageId,
                    index  : this.index
                },
                reader : {
                    type : 'json',
                    root : 'data'
                }
            },
            groupField : 'category', // this applies to the grouping feature
            listeners : {
                load : function(s, recs, success, operation, ops) {
                    s.sort('category', 'ASC');
                    s.sort('name', 'ASC');
                }
            }
        });
    },

    initSearch : function() {

        function filterSearch() {
            var val = searchField.getValue();
            var s   = this.store;
            s.clearFilter();
            if (val) {
                s.filter([{
                    fn : function(rec) {
                        if (rec.data)
                        {
                            var t = new RegExp(Ext4.escapeRe(val), 'i');
                            var s = '';
                            if (rec.data.name)
                                s += rec.data.name;
                            if (rec.data.category)
                                s += rec.data.category;
                            if (rec.data.type)
                                s += rec.data.type;
                            return t.test(s);
                        }
                    }
                }]);
            }
        }

        var filterTask = new Ext.util.DelayedTask(filterSearch, this);

        var searchField = Ext4.create('Ext.form.field.Text', {
            emptyText       : 'name, category, etc.',
            enableKeyEvents : true,
            cls             : 'dataset-search',
            size            : 57,
            border: false, frame : false,
            listeners       : {
                change       : function(cmp, e){
                    filterTask.delay(350);
                }
            }
        });

        return Ext4.create('Ext.panel.Panel',{
            bodyStyle : 'border: none !important;',
            border: false, frame : false,
            layout: { type: 'table' },
            defaults : {
                border: false, frame : false
            },
            items : [searchField, {
                xtype : 'box',
                border: false, frame : false,
                autoEl: {
                    tag : 'img',
                    style : {
                        position : 'relative',
                        left     : '-20px'
                    },
                    src : LABKEY.ActionURL.getContextPath() + '/_images/search.png'
                }
            }]
        });
    },

    initGrid : function() {

        /**
         * Enable Grouping by Category
         */
        var groupingFeature = Ext4.create('Ext4.grid.feature.Grouping', {
            groupHeaderTpl : 'Category: {name}'
        });

        /**
         * Tooltip Template
         */
        var tipTpl = new Ext4.XTemplate('<tpl>' +
                '<div class="tip-content">' +
                '<table cellpadding="20" cellspacing="100" width="100%">' +
                '<tpl if="data.category != undefined && data.category.length">' +
                '<tr><td>Source:</td><td>{data.category}</td></tr>' +
                '</tpl>' +
                '<tpl if="data.createdBy != undefined && data.createdBy.length">' +
                '<tr><td>Author:</td><td>{data.createdBy}</td></tr>' +
                '</tpl>' +
                '<tpl if="data.type != undefined && data.type.length">' +
                '<tr><td>Type:</td><td>{data.type}&nbsp;&nbsp;' +
                '<tpl if="data.icon != undefined && data.icon.length"><img src="{data.icon}"/></tpl>' +
                '</td></tr>' +
                '</tpl>' +
                '<tpl if="data.description != undefined && data.description.length">' +
                '<tr><td valign="top">Description:</td><td>{data.description}</td></tr>' +
                '</tpl>' +
                '<tpl if="data.thumbnail != undefined && data.thumbnail.length">' +
                '<tr><td colspan="2" align="center"><img style="height:200px;max-width:300px;" src="{data.thumbnail}"/></td></tr>' +
                '</tpl>' +
                '</table>' +
                '</div>' +
                '</tpl>').compile();

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
                defaults : {
                    style : 'padding: 5px;'
                },
                renderTipRecord : function(rec){
                    tipPanel.update(rec);
                }
            });

            return tipPanel;
        }

        function initToolTip(view)
        {
            var _w = 200;
            var _h = 200;
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
                anchorToTarget : true,
                anchorOffset : 100,
                showDelay: 1000,
                autoHide : false,
                defaults : { border: false, frame: false},
                items    : [getTipPanel()],
                listeners: {
                    activate  : loadRecord,
                    // Change content dynamically depending on which element triggered the show.
                    beforeshow: function(tip) {
                        var loaded = loadRecord(tip);
                        renderToolTip(tip);
                        return loaded;
                    },
                    scope : this
                },
                scope : this
            });
        }

        var grid = Ext4.create('Ext.grid.Panel', {
            id       : 'data-browser-grid-' + this.webpartId,
            store    : this.store,
            border   : false, frame: false,
            layout   : 'fit',
            height   : 550,
            cls      : 'iScroll', // webkit custom scroll bars
            autoScroll: true,
            columns  : this.initGridColumns(),
            multiSelect: true,
            viewConfig : {
                stripRows : true,
                plugins   : {
                    ptype : 'gridviewdragdrop'
                },
                listeners : {
                    render : initToolTip,
                    scope : this
                }
            },
            selType   : 'rowmodelfixed',
            features  : [groupingFeature],
            listeners : {
                itemclick : function(view, record, item, index, e, opts) {
                    // TODO: Need a better way to determine the clicked item
                    var cls = e.target.className;
                    if (cls.search(/edit-views-link/i) >= 0)
                        this.onEditClick(view, record);
                },
                afterlayout : function(p) {
                    grid.setLoading(false);
                    /* Apply selector for tests */
                    var el = Ext4.query("*[class=x4-grid-table x4-grid-table-resizer]");
                    if (el && el.length == 1) {
                        el = el[0];
                        if (el && el.setAttribute) {
                            el.setAttribute('name', 'data-browser-table');
                        }
                    }
                },
                reconfigure : function() {
//                    var s = groupingFeature.view.getStore();
                    this.store.sort('category', 'DESC');
                    this.customize();
                },
                scope : this
            },
            scope     : this
        });

        return grid;
    },

    initGridColumns : function() {

        var typeTpl = '<tpl if="icon == undefined || icon == \'\'">{type}</tpl><tpl if="icon != undefined && icon != \'\'">' +
                '<img height="18px" width="18px" src="{icon}" alt="{type}">' + // must set height/width explicitly for layout engine to work properly
                '</tpl>';

        var _columns = [];

        _columns.push({
            id       : 'edit-column-' + this.webpartId,
            xtype    : 'templatecolumn',
            text     : '',
            width    : 40,
            sortable : false,
            tpl      : '<span height="18px" class="edit-link-cls-' + this.webpartId + '"></span>', // see this.editLinkCls usage
            hidden   : true,
            scope    : this
        },{
            xtype    : 'templatecolumn',
            text     : 'Name',
            flex     : 1,
            sortable : true,
            dataIndex: 'name',
            tdCls    : 'x4-name-column-cell',
            tpl      : '<div height="18px" width="100%"><a href="{runUrl}">{name}</a></div>',
            scope    : this
        },{
            text     : 'Category',
            flex     : 1,
            sortable : true,
            dataIndex: 'category',
            hidden   : true
        },{
            xtype    : 'templatecolumn',
            text     : 'Type',
            width    : 100,
            sortable : true,
            dataIndex: 'type',
            tdCls    : 'type-column',
            tpl      : typeTpl,
            scope    : this
        },{
            header   : 'Access',
            width    : 100,
            sortable : false,
            dataIndex: 'permissions'
        });

        return _columns;
    },

    initCustomization : function() {

         var customPanel = Ext4.create('Ext.panel.Panel', {
            height : 130,
            layout : 'fit',
            border : false, frame : false,
            hidden : true,
            disabled : !this.isCustomizable()
        });

        this.on('enableCustomMode',  this.onEnableCustomMode,  this);
        this.on('disableCustomMode', this.onDisableCustomMode, this);

        return customPanel;
    },

    isCustomizable : function() {
        return this.allowCustomize;
    },

    // private
    _inCustomMode : function() {
        return this.customMode;
    },

    onEnableCustomMode : function() {

        this.customMode = true;
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
                var json = Ext4.decode(response.responseText);
                this._displayCustomMode(json);
            },
            failure : function() {
                Ext4.Msg.alert('Failure');
            },
            scope : this
        });
    },

    onDisableCustomMode : function() {
        if (this.customPanel && this.customPanel.isVisible())
        {
            this.customPanel.hide();
        }

        // show edit column
        var editColumn = Ext4.getCmp('edit-column-' + this.webpartId);
        if (editColumn)
        {
            var els = Ext.query('span[class=edit-link-cls-' + this.webpartId + ' edit-views-link]');
            Ext.each(els, function(el){
                var _el = Ext.get(el);
                _el.removeClass(this.editLinkCls);
            }, this);
            editColumn.hide();
            this.gridPanel.doLayout(false, true);
        }

        this.customMode = false;
    },

    // private
    _displayCustomMode : function(data) {

        var target = this.customPanel;

        var cbItems = [];

        if (data.types)
        {
            for (var type in data.types) {
                if(data.types.hasOwnProperty(type))
                {
                    cbItems.push({boxLabel : type, name : type, checked : data.types[type], uncheckedValue : '0'});
                }
            }
        }

        var panel = Ext4.create('Ext.form.Panel',{
            bodyPadding: 10,
            layout: {
                type: 'table',
                columns: 3,
                tableAttrs: {
                    style: {
                        width: '100%'
                    }
                }
            },
            fieldDefaults  :{
                labelAlign : 'top',
                labelWidth : 130,
                labelSeparator : ''
            },
            items : [{
                xtype      : 'textfield',
                fieldLabel : 'Data Views Name',
                name       : 'webpart.title',
                colspan    : 1,
                allowBlank : false,
                width      : 250,
                style      : 'padding-bottom: 10px;',
                value      : data.webpart.title ? data.webpart.title : data.webpart.name
            },{
                xtype      : 'checkboxgroup',
                fieldLabel : 'Data Types to Display in this Data Views (All Users)',
                colspan    : 2,
                width      : 400,
                columns    : 2,
                items      : cbItems
            },{
                xtype   : 'button',
                text    : 'Order Categories'
            },{
                xtype   : 'hidden',
                name    : 'webPartId',
                value   : this.webpartId
            }],
            buttons : [{
                text     : 'Cancel',
                handler  : function() {
                    this.fireEvent('disableCustomMode');
                },
                scope : this
            },{
                text     : 'Save',
                formBind : true,
                handler  : function() {
                    var form = panel.getForm(); // this.up('form')
                    if (form.isValid())
                    {
                        Ext4.Ajax.request({
                            url    : LABKEY.ActionURL.buildURL('project', 'customizeWebPartAsync.api', null, form.getValues()),
                            method : 'POST',
                            success : function(){
                                target.hide();
                                this.store.load();

                                // Modify Title
                                var titleEl = Ext.query('th[class=labkey-wp-title-left]:first', 'webpart_' + this.webpartId);
                                if (titleEl && (titleEl.length >= 1))
                                {
                                    titleEl[0].innerHTML = form.findField('webpart.title').getValue();
                                }
                                // else it will get displayed on refresh

                                this.fireEvent('disableCustomMode');
                            },
                            failure : function(){
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

        // show edit column
        var editColumn = Ext4.getCmp('edit-column-' + this.webpartId);
        if (editColumn)
        {
            editColumn.show(null, function(){
                var els = Ext.query('span[class=edit-link-cls-' + this.webpartId + ']');
                Ext.each(els, function(el){
                    var _el = Ext.get(el);
                    _el.addClass(this.editLinkCls);
                }, this);
                this.gridPanel.doLayout(false, true);
            }, this);
        }

        target.add(panel);
        target.doLayout(false, true);
        target.show();
    },

    customize : function() {

        if (!this.isCustomizable())
            return false;

        this.fireEvent((this._inCustomMode() ? 'disableCustomMode' : 'enableCustomMode'), this);
    },

    onEditClick : function(view, record) {

        var tip = Ext.getCmp(this._tipID);
        if (tip)
            tip.hide();

        var formItems = [];
        formItems.push({
            xtype      : (record.data.type.toLowerCase() == 'report' ? 'textfield' : 'displayfield'),
            fieldLabel : 'Name',
            value      : record.data.name
        },{
            xtype      : 'textfield',
            fieldLabel : 'Category',
            value      : record.data.category
        },{
            xtype      : 'textarea',
            fieldLabel : 'Description',
            value      : record.data.description
        },{
            xtype      : 'displayfield',
            fieldLabel : 'Type',
            value      : record.data.type,
            readOnly   : true
        },{
            xtype      : 'radiogroup',
            fieldLabel : 'Visibility',
            items      : [{boxLabel : 'Public',  name : 'visibility', checked : true},
                          {boxLabel : 'Private', name : 'visibility', checked : false}]
        });

        if (record.data.created) {
            formItems.push({
                xtype      : 'displayfield',
                fieldLabel : 'Created On',
                value      : record.data.created,
                readOnly   : true
            });
        }

        if (record.data.modified) {
            formItems.push({
                xtype      : 'displayfield',
                fieldLabel : 'Last Modified',
                value      : record.data.modified,
                readOnly   : true
            });
        }

        var editWindow = Ext4.create('Ext.window.Window', {
            width  : 450,
            height : 500,
            layout : 'fit',
            draggable : false,
            modal  : true,
            title  : record.data.name,
            defaults: {
                border: false, frame: false
            },
            bodyPadding : 20,
            items  : [{
                xtype : 'form',
                fieldDefaults  : {
                    labelWidth : 100,
                    width      : 375,
                    style      : 'padding: 4px 0',
                    labelSeparator : ''
                },
                items : formItems,
                buttonAlign : 'left',
                buttons : [{
                    text : 'Save',
                    formBind: true,
                    handler : function() {
                        editWindow.close();
                    }
                }]
            }]
        });

        editWindow.show();
    }
});