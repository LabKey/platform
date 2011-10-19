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

        // The following default to type 'string'
        var fields = [
            {name : 'category'},
            {name : 'categoryDisplayOrder', type : 'int'},
            {name : 'created',              type : 'date'},
            {name : 'createdBy'},
            {name : 'container'},
            {name : 'dataType'},
            {name : 'editable',             type : 'boolean'},
            {name : 'editUrl'},
            {name : 'entityId'},
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

        // define Models
        Ext4.define('Dataset.Browser.View', {
            extend : 'Ext.data.Model',
            fields : fields
        });

        Ext4.define('Dataset.Browser.Category', {
            extend : 'Ext.data.Model',
            fields : [
                {name : 'created',      type : 'date'},
                {name : 'createdBy'                  },
                {name : 'displayOrder', type : 'int' },
                {name : 'label'                      },
                {name : 'modfied',      type : 'date'},
                {name : 'modifiedBy'                 },
                {name : 'rowid',        type : 'int' }
            ]
        });

        this.callParent([config]);

        if (this.isCustomizable())
            this.addEvents('enableCustomMode', 'disableCustomMode');
    },

    initComponent : function() {

        this.customMode = false;
        this.searchVal = "";

        this.items = [];

        this.store  = this.initializeViewStore(true);
        this.searchPanel = this.initSearch();
        this.gridPanel   = this.initGrid();
        this.customPanel = this.initCustomization();

        this.items.push(this.customPanel, this.searchPanel, this.gridPanel);

        this.callParent([arguments]);
    },

    initializeViewStore : function(useGrouping) {

        var config = {
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
            listeners : {
                load : this.onViewLoad,
                scope: this
            },
            scope : this
        };

        if (useGrouping) {
            config["groupField"] = 'category';
        }

        return Ext4.create('Ext.data.Store', config);
    },

    initializeCategoriesStore : function(useGrouping)
    {
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
                    pageId : this.pageId,
                    index  : this.index
                },
                reader : {
                    type : 'json',
                    root : 'categories'
                },
                writer: {
                    type : 'json',
                    root : 'categories'
                },
                listeners : {
                    exception : function(p, response, operations, eOpts)
                    {
                    }
                }
            },
            listeners : {
                load : function(s, recs, success, operation, ops) {
                    s.sort('displayOrder', 'ASC');
                }
            }
        };

        if (useGrouping)
            config["groupField"] = 'category';

        return Ext4.create('Ext.data.Store', config);
    },

    initSearch : function() {

        function filterSearch() {
            this.searchVal = searchField.getValue();
            this.hiddenFilter();
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
            groupHeaderTpl : '&nbsp;{name}' // &nbsp; allows '+/-' to show up
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
                '<tr><td>Type:</td><td>{data.type}</td></tr>' +
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
            var _w = 500;
            var _h = 300;
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
//            height   : 550,
//            cls      : 'iScroll', // webkit custom scroll bars
            autoScroll: true,
            columns  : this.initGridColumns(),
            multiSelect: true,
            viewConfig : {
                stripRows : true,
                listeners : {
                    render : initToolTip,
                    scope : this
                },
                emptyText : '0 Matching Results'
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
                    this.store.sort([
                        {
                            property : 'name',
                            direction: 'ASC'
                        }
                    ]);
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
            text     : '',
            width    : 40,
            sortable : false,
            renderer : function(view, meta, rec, idx, colIdx, store) {
                if (!this._inCustomMode())
                    meta.style = 'display: none;';  // what a nightmare
                if (!rec.data.entityId) {
                    return '<span height="18px" class="edit-link-cls-' + this.webpartId + '"></span>'; // entityId is required for editing
                }
                return '<span height="18px" class="edit-link-cls-' + this.webpartId + ' edit-views-link"></span>';
            },
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

    onViewLoad : function(s, recs, success, operation, ops) {
        this.hiddenFilter();
        var s = this.store;
        for (var i = 0; i < s.groupers.items.length; i++) {
            s.groupers.items[i].updateSortFunction(function(rec1, rec2){
                var cdo1 = rec1.data.categoryDisplayOrder,
                    cdo2 = rec2.data.categoryDisplayOrder;

                if (cdo1 < cdo2)
                    return -1;
                else if (cdo1 == cdo2)
                    return 0;
                return 1;
            });
        }
        s.sort([
            {
                property : 'name',
                direction: 'ASC'
            }
        ]);
        this.doLayout(false, true);
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

        this.customMode = false;

        this.hiddenFilter();

        // hide edit column
        var editColumn = Ext4.getCmp('edit-column-' + this.webpartId);
        if (editColumn)
        {
            editColumn.hide();
        }
    },

    // private
    _displayCustomMode : function(data) {

        // panel might already exist
        if (this.customPanel && this.customPanel.items.length > 0)
        {
            this.hiddenFilter();

            // show edit column
            var editColumn = Ext4.getCmp('edit-column-' + this.webpartId);
            if (editColumn)
            {
                editColumn.show();
            }

            this.customPanel.show();
            this.doLayout(false, true);
            return;
        }

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
                columns: 2
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
                colspan    : 1,
                columns    : 2,
                width      : 325,
                style      : 'padding-left: 25px;',
                items      : cbItems
            },{
                xtype   : 'button',
                text    : 'Manage Categories',
                handler : this.onManageCategories,
                colspan : 1,
                scope   : this
            },{
                xtype   : 'hidden',
                name    : 'webPartId',
                value   : this.webpartId
            }],
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
                    var form = panel.getForm(); // this.up('form')
                    if (form.isValid())
                    {
                        Ext4.Ajax.request({
                            url    : LABKEY.ActionURL.buildURL('project', 'customizeWebPartAsync.api', null, form.getValues()),
                            method : 'POST',
                            success : function(){
                                this.customPanel.hide();
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

        this.hiddenFilter();

        // show edit column
        var editColumn = Ext4.getCmp('edit-column-' + this.webpartId);
        if (editColumn)
        {
            editColumn.show();
        }

        this.customPanel.add(panel);
        this.customPanel.show();
        this.doLayout(false, true);
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

    /**
     * Aggregates the filters applied by search and by custom mode.
     */
    hiddenFilter : function() {

        this.store.clearFilter();
        var _custom = this._inCustomMode();
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
                answer = t.test(s);
                console.log(answer);
            }

            // custom mode will show hidden
            if (_custom)
                return answer;

            // otherwise never show hidden records
            if (rec.data.hidden)
                return false;

            return answer;
        }, this);
    },

    onEditClick : function(view, record) {

        var tip = Ext.getCmp(this._tipID);
        if (tip)
            tip.hide();

        var formItems = [];

        /* Record 'entityId' is required*/
        var editable = true;
        if (record.data.entityId == undefined || record.data.entityId == "")
        {
            console.warn('Entity ID is required');
            editable = false;
        }

        // hidden items
        formItems.push({
            xtype : 'hidden',
            name  : 'reportId',
            value : record.data.reportId
        },{
            xtype : 'hidden',
            name  : 'entityId',
            value : record.data.entityId
        },{
            xtype : 'hidden',
            name  : 'dataType',
            value : record.data.dataType
        });

        // displayed items
        formItems.push({
            xtype      : (record.data.type.toLowerCase() == 'report' ? 'textfield' : 'displayfield'),
            fieldLabel : 'Name',
            value      : record.data.name
        },{
            xtype       : 'combo',
            fieldLabel  : 'Category',
            name        : 'category',
            store       : this.initializeCategoriesStore(),
            typeAhead   : true,
            hideTrigger : true,
            readOnly    : !editable,
            typeAheadDelay : 75,
            minChars       : 1,
            autoSelect     : false,
            queryMode      : 'remote',
            displayField   : 'label',
            valueField     : 'label',
            emptyText      : 'Uncategorized',
            listeners      : {
                render     : function(combo) {
                    combo.setRawValue(record.data.category);
                }
            }
        },{
            xtype      : (editable == true ? 'textarea' : 'displayfield'), // TODO: Should hook editable to model editable
            fieldLabel : 'Description',
            name       : 'description',
            value      : record.data.description
        },{
            xtype      : 'displayfield',
            fieldLabel : 'Type',
            value      : record.data.type,
            readOnly   : true
        },{
            xtype      : 'radiogroup',
            fieldLabel : 'Visibility',
            items      : [{boxLabel : 'Visible',  name : 'hidden', checked : !record.data.hidden, inputValue : false},
                          {boxLabel : 'Hidden',   name : 'hidden', checked : record.data.hidden,  inputValue : true}]
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
            height : 425,
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
                items       : formItems,
                buttonAlign : 'left',
                buttons     : [{
                    text : 'Save',
                    formBind: true,
                    handler : function(btn) {
                        var form = btn.up('form').getForm();
                        if (form.isValid())
                        {
                            Ext4.Ajax.request({
                                url     : LABKEY.ActionURL.buildURL('study', 'editView.api'),
                                method  : 'POST',
                                params  : form.getValues(),
                                success : function(){
                                    this.onEditSave(record, form.getValues());
                                    editWindow.close();
                                },
                                failure : function(response){
                                    Ext4.Msg.alert('Failure', Ext4.decode(response.responseText).exception);
                                },
                                scope : this
                            });
                        }
                    },
                    scope   : this
                }]
            }],
            scope : this
        });

        editWindow.show();
    },

    onEditSave : function(record, values) {
        this.store.load();
    },

    onManageCategories : function(btn) {

        var cellEditing = Ext4.create('Ext.grid.plugin.CellEditing', {
            clicksToEdit: 2
        });

        var confirm = false;
        var store = this.initializeCategoriesStore();

        var grid = Ext4.create('Ext.grid.Panel', {
            store    : store,
            border   : false, frame: false,
            autoScroll : true,
            columns  : [{
                text     : 'Category',
                flex     : 1,
                sortable : true,
                dataIndex: 'label',
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
                    icon    : LABKEY.contextPath + '/ext-4.0.2a/resources/themes/images/access/qtip/close.gif',
                    tooltip : 'Delete'
                }],
                listeners : {
                    click : function(col, grid, idx, evt, x, y, z)
                    {
                        store.sync();

                        var label = store.getAt(idx).data.label;
                        var id    = store.getAt(idx).data.rowid;

                        var cats = {
                            categories : [{label : label, rowid: id}]
                        };

                        Ext4.Msg.show({
                            title : 'Delete Category',
                            msg   : 'Please confirm you would like to <b>DELETE</b> \'' + Ext4.htmlEncode(label) + '\' from the set of categories.',
                            buttons : Ext4.MessageBox.OKCANCEL,
                            icon    : Ext4.MessageBox.WARNING,
                            fn      : function(btn){
                                if (btn == 'ok') {
                                    // TODO: This is deprected -- should use proxy/model 'destroy' api
                                    Ext4.Ajax.request({
                                        url    : LABKEY.ActionURL.buildURL('study', 'deleteCategories.api'),
                                        method : 'POST',
                                        jsonData : cats,
                                        success: function() {
                                            store.load();
                                        },
                                        failure: function(response) {
                                           Ext4.Msg.alert('Failure', Ext4.decode(response.responseText).exception);
                                        }
                                    });
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
                    drop : function(node, data, model, pos) {
                        var s = grid.getStore();
                        var display = 1;
                        s.each(function(rec){
                            rec.set('displayOrder', display);
                            display++;
                        }, this);
                    }
                }
            },
            plugins   : [cellEditing],
            selType   : 'rowmodelfixed',
            scope     : this
        });

        var categoryOrderWindow = Ext4.create('Ext.window.Window', {
            title  : 'Manage Categories',
            width  : 550,
            height : 400,
            layout : 'fit',
            modal  : true,
            defaults  : {
                frame : false
            },
            items   : [grid],
            buttons : [{
                text    : 'Create New Category',
                handler : function(btn) {
                    var r = Ext4.ModelManager.create({
                        label        : 'New Category',
                        displayOrder : 0
                    }, 'Dataset.Browser.Category');
                    store.insert(0, r);
                    cellEditing.startEditByPosition({row : 0, column : 0});
                }
            },{
                text    : 'Done',
                handler : function(btn) {
                    grid.getStore().sync();
                    categoryOrderWindow.close();
                }
            }],
            listeners : {
                beforeclose : function()
                {
                    if (confirm)
                        this.onEditSave();
                },
                scope : this
            },
            scope     : this
        });

        categoryOrderWindow.show();
    }
});