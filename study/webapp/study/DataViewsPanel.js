/*
 * Copyright (c) 2011-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

// http://www.sencha.com/forum/showthread.php?184010-TreeStore-filtering
Ext4.define('TreeFilter', {
    extend: 'Ext.AbstractPlugin',
    alias: 'plugin.jsltreefilter',

    collapseOnClear: true,       // collapse all nodes when clearing/resetting the filter
    allowParentFolders: false,   // allow nodes not designated as 'leaf' (and their child items) to  be matched by the filter

    init: function (tree) {
        var me = this;
        me.tree = tree;

        tree.filter = Ext4.Function.bind(me.filter, me);
        tree.filterBy = Ext4.Function.bind(me.filterBy, me);
        tree.clearFilter = Ext4.Function.bind(me.clearFilter, me);
    },

    filter: function (value, propArray, re) {

        if (Ext4.isEmpty(value)) {               // if the search field is empty
            this.clearFilter();
            return;
        }

        var me = this,
            tree = me.tree,
            matches = [],                         // array of nodes matching the search criteria
            root = tree.getRootNode(),            // root node of the tree
            _propArray = propArray || ['text'],   // propArray is optional - will be set to the ['text'] property of the TreeStore record by default
            _re = re || new RegExp(value, "ig"),  // the regExp could be modified to allow for case-sensitive, starts  with, etc.
            visibleNodes = [],                    // array of nodes matching the search criteria + each parent non-leaf  node up to root
            viewNode;

        tree.expandAll();                       // expand all nodes for the the following iterative routines

        // iterate over all nodes in the tree in order to evalute them against the search criteria
        root.cascadeBy(function (node) {
            for (var p=0; p < _propArray.length; p++) {
                if (node.get(_propArray[p]) &&
                    node.get(_propArray[p]).match &&
                    node.get(_propArray[p]).match(_re)) {  // if the node matches the search criteria and is a leaf (could be  modified to searh non-leaf nodes)
                        matches.push(node);                // add the node to the matches array
                        return;
                }
            }
        });

        if (me.allowParentFolders === false) {     // if me.allowParentFolders is false (default) then remove any  non-leaf nodes from the regex match
            Ext4.each(matches, function (match) {
                if (match && !match.isLeaf()) { Ext4.Array.remove(matches, match); }
            });
        }

        Ext4.each(matches, function (item, i, arr) {   // loop through all matching leaf nodes
            root.cascadeBy(function (node) {           // find each parent node containing the node from the matches array
                if (node.contains(item)) {
                    visibleNodes.push(node);           // if it's an ancestor of the evaluated node add it to the visibleNodes  array
                }
            });
            if (me.allowParentFolders === true &&  !item.isLeaf()) { // if me.allowParentFolders is true and the item is  a non-leaf item
                item.cascadeBy(function (node) {                     // iterate over its children and set them as visible
                    visibleNodes.push(node)
                });
            }
            visibleNodes.push(item);   // also add the evaluated node itself to the visibleNodes array
        });

        root.cascadeBy(function (node) {                             // finally loop to hide/show each node
            viewNode = Ext4.fly(tree.getView().getNode(node));       // get the dom element assocaited with each node
            if (viewNode) {                                          // the first one is undefined ? escape it with a conditional
                viewNode.setVisibilityMode(Ext4.Element.DISPLAY);    // set the visibility mode of the dom node to display (vs offsets)
                viewNode.setVisible(Ext4.Array.contains(visibleNodes, node));
            }
        });
    },

    filterBy: function(fn, scope) {
        if (!Ext4.isFunction(fn)) {
            return;
        }

        var me = this,
            tree = me.tree,
            root = tree.getRootNode(),
            matches = [],
            viewNode,
            visibleNodes = [];

        tree.expandAll();                       // expand all nodes for the the following iterative routines

        root.cascadeBy(function (node) {
            if (fn.call(scope || this, node) === true) { matches.push(node); }
        });

        if (me.allowParentFolders === false) {     // if me.allowParentFolders is false (default) then remove any  non-leaf nodes from the regex match
            Ext4.each(matches, function (match) {
                if (match && !match.isLeaf()) { Ext4.Array.remove(matches, match); }
            });
        }

        Ext4.each(matches, function (item, i, arr) {   // loop through all matching leaf nodes
            root.cascadeBy(function (node) {           // find each parent node containing the node from the matches array
                if (node.contains(item)) {
                    visibleNodes.push(node);           // if it's an ancestor of the evaluated node add it to the visibleNodes  array
                }
            });
            if (me.allowParentFolders === true &&  !item.isLeaf()) { // if me.allowParentFolders is true and the item is  a non-leaf item
                item.cascadeBy(function (node) {                     // iterate over its children and set them as visible
                    visibleNodes.push(node)
                });
            }
            visibleNodes.push(item);   // also add the evaluated node itself to the visibleNodes array
        });

        root.cascadeBy(function (node) {                             // finally loop to hide/show each node
            viewNode = Ext4.fly(tree.getView().getNode(node));       // get the dom element assocaited with each node
            if (viewNode) {                                          // the first one is undefined ? escape it with a conditional
                viewNode.setVisibilityMode(Ext4.Element.DISPLAY);    // set the visibility mode of the dom node to display (vs offsets)
                viewNode.setVisible(Ext4.Array.contains(visibleNodes, node));
            }
        });
    },

    clearFilter: function () {
        var me = this,
            tree = this.tree,
            root = tree.getRootNode();

        if (me.collapseOnClear) { tree.collapseAll(); }              // collapse the tree nodes
        root.cascadeBy(function (node) {                             // final loop to hide/show each node
            var viewNode = Ext4.fly(tree.getView().getNode(node));   // get the dom element assocaited with each node
            if (viewNode) {                                          // the first one is undefined ? escape it with a conditional and show  all nodes
                viewNode.show();
            }
        });
    }
});

Ext4.define('Ext.tree.DataViewsColumn', {
    extend: 'Ext.grid.column.Column',
    alias: 'widget.dataviewscolumn',

    tdCls: Ext4.baseCSSPrefix + 'grid-cell-treecolumn',

    initComponent: function() {
        var origRenderer = this.renderer || this.defaultRenderer,
            origScope    = this.scope || window;

        this.renderer = function(value, metaData, record, rowIdx, colIdx, store, view) {
            var buf   = [],
                format = Ext4.String.format,
                depth = record.getDepth(),
                treePrefix  = Ext4.baseCSSPrefix + 'tree-',
                elbowPrefix = treePrefix + 'elbow-',
                expanderCls = treePrefix + 'expander',
                imgText     = '<img src="{1}" class="{0}" />',
                checkboxText= '<input type="button" role="checkbox" class="{0}" {1} />',
                formattedValue = origRenderer.apply(origScope, arguments),
                href = record.get('href'),
                target = record.get('hrefTarget'),
                cls = record.get('cls');

            while (record) {
                if (!record.isRoot() || (record.isRoot() && view.rootVisible)) {
                    if (record.getDepth() === depth) {
                        buf.unshift(format(imgText,
                            treePrefix + 'icon ' +
                            treePrefix + 'icon' + (record.get('icon') ? '-inline ' : (record.isLeaf() ? '-leaf ' : '-parent ')) +
                            (record.get('iconCls') || ''),
                            record.get('icon') || Ext4.BLANK_IMAGE_URL
                        ));
                        if (record.get('checked') !== null) {
                            buf.unshift(format(
                                checkboxText,
                                (treePrefix + 'checkbox') + (record.get('checked') ? ' ' + treePrefix + 'checkbox-checked' : ''),
                                record.get('checked') ? 'aria-checked="true"' : ''
                            ));
                            if (record.get('checked')) {
                                metaData.tdCls += (' ' + treePrefix + 'checked');
                            }
                        }
                        if (record.isLast()) {
                            if (record.isExpandable()) {
                                buf.unshift(format(imgText, (elbowPrefix + 'end-plus ' + expanderCls), Ext4.BLANK_IMAGE_URL));
                            } else {
                                buf.unshift(format(imgText, (elbowPrefix + 'end'), Ext4.BLANK_IMAGE_URL));
                            }

                        } else {
                            if (record.isExpandable()) {
                                buf.unshift(format(imgText, (elbowPrefix + 'plus ' + expanderCls), Ext4.BLANK_IMAGE_URL));
                            } else {
                                buf.unshift(format(imgText, (treePrefix + 'elbow'), Ext4.BLANK_IMAGE_URL));
                            }
                        }
                    } else {
                        if (record.isLast() || record.getDepth() === 0) {
                            buf.unshift(format(imgText, (elbowPrefix + 'empty'), Ext4.BLANK_IMAGE_URL));
                        } else if (record.getDepth() !== 0) {
                            buf.unshift(format(imgText, (elbowPrefix + 'line'), Ext4.BLANK_IMAGE_URL));
                        }
                    }
                }
                record = record.parentNode;
            }
            if (href) {
                buf.push('<a href="', href, '" target="', target, '">', Ext4.htmlEncode(formattedValue), '</a>');
            } else {
                buf.push('<span>',Ext4.htmlEncode(formattedValue),'</span>');
            }
            if (cls) {
                metaData.tdCls += ' ' + cls;
            }
            return buf.join('');
        };
        this.callParent(arguments);
    },

    defaultRenderer: function(value) {
        return value;
    }
});

/**
 * This is an extended model used to render the TreeStore. Due to performance
 * the afterCommit method has been disabled. It is not recommended you use this model.
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

        // The following default to type 'string'
        var fields = [
            {name : 'id'},
            {name : 'category'},
            {name : 'categorylabel',
                convert : function(v, record) {
                    if (record.raw && record.raw.category)
                        return record.raw.category.label;
                    return 0;
                }
            },
            {name : 'created',              type : 'date'},
            {name : 'createdBy'},
            {name : 'createdByUserId',      type : 'int'},
            {name : 'authorUserId',
                convert : function(v, record){
                    if (record.raw && record.raw.author)
                        return record.raw.author.userId;
                    return 0;
                }
            },
            {name : 'authorDisplayName',
                convert : function(v, record){
                    if (record.raw && record.raw.author)
                        return record.raw.author.displayName;
                    return '';
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
            {name : 'href', mapping : 'runUrl'},
            {name : 'allowCustomThumbnail'},
            {name : 'status'},
            {name : 'reportId'}
        ];

        // define Models
        Ext4.define('Dataset.Browser.View', {
            extend : 'Ext.data.FastModel',
            idProperty : 'fakeid', // do not use the 'id' property, rather just make something up which Ext will then generate
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
            this.addEvents(
                    'enableCustomMode',
                    'disableCustomMode',
                    'enableEditMode',
                    'disableEditMode'
            );
    },

    initComponent : function() {

        this.customMode = false;
        this.editMode = false;
        this.searchVal = "";
        this._height = null;

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
            url    : LABKEY.ActionURL.buildURL('study', 'browseDataTree.api'),
            extraParams : {
                // These parameters are required for specific webpart filtering
                pageId      : this.pageId,
                index       : this.index,
                returnUrl   : this.returnUrl
            },
            reader : 'json'
        };
    },

    initializeViewStore : function() {

        if (this.store)
            return this.store;

        var config = {
            pageSize: 100,
            model   : 'Dataset.Browser.View',
            autoLoad: true,
            proxy   : this.getViewProxy(),
            listeners : {
                beforeload : function(){
                    if (this.gridPanel) {
                        if (this.catWinID) {
                            var w = Ext4.getCmp(this.catWinID);
                            if (w && !w.isVisible())
                                this.gridPanel.setLoading(true);
                        }
                        else
                            this.gridPanel.setLoading(true);
                    }
                },
                load : {fn : this.onViewLoad, scope: this},
                scope: this
            },
            sortRoot : 'displayOrder',
            scope : this
        };

        this.store = Ext4.create('Ext.data.TreeStore', config);
        return this.store;
    },

    initializeUserStore : function() {

        return Ext4.create('Ext.data.Store', {
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
        });

    },

    initCenterPanel : function() {

        this.centerPanel = Ext4.create('Ext.panel.Panel', {
            border   : false,
            frame    : false,
            layout   : 'fit'
        });

        this.centerPanel.on('render', this.configureGrid, this);

        return {
            xtype  : 'panel',
            border : false, frame : false,
            layout : 'fit',
            flex   : 4,
            region : 'center',
            items  : [this.centerPanel]
        };
    },

    /**
     * Invoked once when the grid is initially setup
     */
    configureGrid : function() {

        var handler = function(json) {
            this.centerPanel.getEl().unmask();

            if (json.webpart) {
                this._height = parseInt(json.webpart.height);
                this.setHeight(this._height);
            }
            this.dateFormat = json.dateFormat;
            this.dateRenderer = Ext4.util.Format.dateRenderer(json.dateFormat);
            this.editInfo = json.editInfo;

            this.initGrid(json.visibleColumns);
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
            this._height = parseInt(json.webpart.height);
            this.centerPanel.removeAll(true);
            this.setHeight(this._height);
            this.initGrid(json.visibleColumns);
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

    initGrid : function(visibleColumns) {

        /**
         * Tooltip Template
         */
        var tipTpl = new Ext4.XTemplate('<tpl>' +
                '<div class="data-views-tip-content">' +
                '<table cellpadding="20" cellspacing="100">' +
                '<tpl if="data.category != undefined && (data.category.length || data.category.label)">' +
                '<tr><td>Source:</td><td>{[this.renderCategory(values.data.category)]}</td></tr>' +
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
                    renderCategory : function(cat) {
                        return Ext4.htmlEncode(Ext4.isString(cat) ? cat : cat.label);
                    },
                    renderDate : function(data) {
                        return this.initialConfig.dateRenderer(data);
                    }
                }, {dateRenderer : this.dateRenderer});

        /**
         * Initializes the tooltip at grid panel render
         */
        var initToolTip = function(view)
        {
            view.tip = Ext4.create('Ext.tip.ToolTip', {
                target   : view.el,
                delegate : '.x4-name-column-cell',
                trackMouse: false,
                width    : 500, // 17257: Removed height property due to clipping
                html     : null,
                autoHide : true,
                anchorToTarget : true,
                anchorOffset : 100,
                showDelay: 850,
                cls      : 'data-views-tip-panel',
                defaults : { border: false, frame: false },
                items    : [{
                    xtype  : 'panel',
                    layout : 'fit',
                    border : false, frame : false,
                    height : '100%',
                    cls    : 'tip-panel',
                    tpl    : tipTpl
                }],
                listeners: {
                    // Change content dynamically depending on which element triggered the show.
                    beforeshow: function(tip) {
                        var rec = this.getRecord(tip.triggerElement.parentNode);

                        if (!rec || (rec.childNodes && rec.childNodes.length > 0)) {
                            tip.addCls('hide-tip');
                        }
                        else if (rec) {
                            tip.removeCls('hide-tip');
                            tip.setTitle(rec.get('name'));
                            tip.down('panel').update(rec);
                        }
                    },
                    scope : view
                },
                scope : this
            });
        };

        this.gridPanel = Ext4.create('Ext.tree.Panel', {
            id       : 'data-browser-grid-' + this.webpartId,
            store    : this.initializeViewStore(),
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
            listeners : {
                itemclick : function(view, record, item, index, e) {
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
            plugins : [ Ext4.create('TreeFilter', { collapseOnClear : false, allowParentFolders: true }) ],
            scope     : this
        });

        this.centerPanel.add(this.gridPanel);
    },

    initGridColumns : function(visibleColumns) {

        var detailsTpl =
                '<tpl if="detailsUrl">' +
                    '<a data-qtip="Click to navigate to the Detail View" href="{detailsUrl}">' +
                        '<img data-qtip="Details" height="16px" width="16px" src="' + LABKEY.ActionURL.getContextPath() + '/reports/details.png" alt="Details...">' +
                   '</a>' +
                '</tpl>';

        var _columns = [];

        _columns.push({
            id       : 'edit-column-' + this.webpartId,
            text     : '&nbsp;',
            width    : 40,
            sortable : false,
            menuDisabled : true,
            renderer : function(view, meta, rec) {
                if (!this.editMode) {
                    meta.style = 'display: none;';  // what a nightmare
                }

                // an item needs an edit info interface to be editable
                if (!this.editInfo[rec.data.dataType]) {
                    return '<span height="16px" class="edit-link-cls-' + this.webpartId + '"></span>';
                }
                return '<span height="16px" class="edit-link-cls-' + this.webpartId + ' edit-views-link"></span>';
            },
            listeners : {
                beforehide : function(c) {
                    this.gridPanel.getView().refresh();
                },
                beforeshow : function(c) {
                    this.gridPanel.getView().refresh();
                },
                scope : this
            },
            hidden   : true,
            scope    : this
        },{
            xtype    : 'dataviewscolumn',
            text     : 'Name',
            flex     : 1,
            dataIndex: 'name',
            minWidth : 200,
            menuDisabled : true,
            sortable : false,
            tdCls    : 'x4-name-column-cell',
            scope    : this
        },{
            id       : 'category-column-' + this.webpartId,
            text     : 'Category',
            flex     : 1,
            menuDisabled : true,
            sortable : false,
            dataIndex: 'categorylabel',
            renderer : Ext4.util.Format.htmlEncode,
            hidden   : true
        });

        if (visibleColumns['Type'] && visibleColumns['Type'].checked)
        {
            _columns.push({
                xtype    : 'templatecolumn',
                text     : 'Type',
                width    : 75,
                dataIndex: 'type',
                tdCls    : 'type-column',
                menuDisabled : true,
                sortable : false,
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
                dataIndex: 'detailsUrl',
                tdCls    : 'type-column',
                menuDisabled : true,
                sortable : false,
                tpl      : detailsTpl,
                scope    : this
            });
        }

        if(visibleColumns['Data Cut Date'] && visibleColumns['Data Cut Date'].checked){
             _columns.push({
                 text     : 'Data Cut Date',
                 width    : 120,
                 dataIndex: 'refreshDate',
                 menuDisabled : true,
                 sortable : false,
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
                tdCls    : 'type-column',
                menuDisabled : true,
                sortable : false,
                dataIndex: 'status',
                tpl      : statusTpl
            });
        }

        if (visibleColumns['Modified'] && visibleColumns['Modified'].checked)
        {
            _columns.push({
                text     : 'Modified',
                width    : 100,
                renderer : this.dateRenderer,
                menuDisabled : true,
                sortable : false,
                dataIndex: 'modified'
            });
        }

        if (visibleColumns['Author'] && visibleColumns['Author'].checked)
        {
            _columns.push({
                text     : 'Author',
                width    : 100,
                dataIndex: 'authorDisplayName',
                menuDisabled : true,
                sortable : false,
                scope    : this
            });
        }

        if (visibleColumns['Access'] && visibleColumns['Access'].checked)
        {
            _columns.push({
                header   : 'Access',
                width    : 100,
                dataIndex: 'access',
                sortable : false,
                menuDisabled : true
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
        this.on('enableEditMode', this.onEnableEditMode, this);
        this.on('disableEditMode', this.onDisableEditMode, this);

        return customPanel;
    },

    onViewLoad : function() {
        if (this.gridPanel)
            this.gridPanel.setLoading(false);

        this.hiddenFilter();
    },

    /**
     * Aggregates the filters applied by search and by custom mode.
     */
    hiddenFilter : function() {

        var _custom = this._inCustomMode();
        var searchFields = ['name', 'categorylabel', 'type', 'modified', 'authorDisplayName', 'status'];

        var filter = function(rec) {

            var answer = true;

            if (rec.data && this.searchVal && this.searchVal != "") {
                var t = new RegExp(Ext4.escapeRe(this.searchVal), 'i');
                var s = '', i=0;

                for (; i < searchFields.length; i++) {
                    if (rec.data[searchFields[i]]) {
                        s += rec.data[searchFields[i]];
                    }
                }

                answer = t.test(s);
            }

            // match 'mine' if current user is either the author or creator
            if (this.searchMine && rec.data && answer) {
                if ((rec.data.authorUserId != LABKEY.user.id) && (rec.data.createdByUserId != LABKEY.user.id)) {
                    return false;
                }
            }

            // custom mode will show hidden
            if (_custom) { return answer; }

            // otherwise never show hidden records
            if (!rec.data.visible) { return false; }

            return answer;
        };

        this.gridPanel.clearFilter();
        this.gridPanel.filterBy(filter, this);
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
        if (this.customPanel && this.customPanel.isVisible()) {
            this.north.hide();
        }

        this.customMode = false;

        this.hiddenFilter();
    },

    edit : function(){
        if(!this.isCustomizable())
            return false;

        this.fireEvent((this.editMode ? 'disableEditMode' : 'enableEditMode'), this);
    },

    onEnableEditMode : function(){
        this.editMode = true;
        this._getEditColumn().show();
    },

    onDisableEditMode : function(){
        this.editMode = false;
        this._getEditColumn().hide();
    },

    // private
    _displayCustomMode : function(data) {

        // reset the update config flag, we only want to re-fetch the configuration if the visible columns change
        this.updateConfig = false;

        // panel might already exist
        if (this.customPanel && this.customPanel.items.length > 0) {

            this.hiddenFilter();

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

        if (Ext4.isArray(data.types)) {
            for (var i=0; i < data.types.length; i++) {
                cbItems.push({boxLabel : data.types[i].name, name : data.types[i].name, checked : data.types[i].visible, width: 150, uncheckedValue : '0'});
            }
        }

        if (data.visibleColumns) {
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
                fieldLabel : 'View Types',
                colspan    : 1,
                columns    : 1,
                flex       : 0.75,
                maxWidth   : 225,
                style      : 'margin-left: 20px;',
                items      : cbItems
            },{
                xtype      : 'checkboxgroup',
                fieldLabel : 'Visible Columns',
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
                    var form = formPanel.getForm();
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

        var buttons = [{
            text: 'Save',
            formBind: true,
            handler : function(btn) {
                var form = btn.up('form').getForm();
                if (form.isValid())
                {
                    editWindow.getEl().mask("Saving...");
                    if (!form.getValues().category) {
                        // In order to clear the category
                        form.setValues({category: 0});
                    }
                    form.submit({
                        url : LABKEY.ActionURL.buildURL('study', 'editView.api'),
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
            scope: this
        }];

        if (record.data.dataType == 'reports')
        {
            var gpStore = this.gridPanel.getStore(); // Need because for some reason this.gridPanel is not in scope
            // in the successCallback below.
            buttons.push({
                text: 'Delete Report',
                scope: this,
                handler: function ()
                {
                    var msg = 'Are you sure you want to delete "' + record.data.name + '"';
                    var successCallback = function (response)
                    {
                        var jsonResp = Ext4.JSON.decode(response.responseText);
                        console.log(jsonResp);
                        editWindow.close();
                        // Refresh the store, so deleted reports go away.
                        gpStore.load();
                    };
                    var failureCallback = function (response)
                    {
                        var jsonResp = Ext4.JSON.decode(response.responseText);
                        console.error(jsonResp);
                        Ext4.MessageBox.alert('Error Deleting Report', 'There was an error deleting the report "' + record.data.name + '"');
                        editWindow.close();
                    };
                    var confirmCallback = function (choice)
                    {
                        if (choice == "yes")
                        {
                            editWindow.close();
                            var deleteURL = LABKEY.ActionURL.buildURL('reports', 'manageViewsDeleteReports', null, {
                                reportId: record.data.reportId
                            });
                            console.log(deleteURL);
                            Ext4.Ajax.request({
                                url: deleteURL,
                                method: 'POST',
                                success: successCallback,
                                failure: failureCallback
                            });
                        }
                    };

                    Ext4.MessageBox.confirm('Delete Report?', msg, confirmCallback, this);
                }
            });
        }

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
            buttons     : buttons
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

    onManageCategories : function() {

        var window = LABKEY.study.DataViewUtil.getManageCategoriesDialog();

        window.on('categorychange', function(cmp){
            this.gridPanel.getStore().load();
            console.log('category changed')
        }, this);
        window.show();
    }
});
