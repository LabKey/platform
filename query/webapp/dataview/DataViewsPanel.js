/*
 * Copyright (c) 2011-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

// http://www.sencha.com/forum/showthread.php?184010-TreeStore-filtering
// TODO: pluggin no longer needed as filtering support has been added to TreeStore in ext-patches.js
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
            _propArray = propArray || ['text'],   // propArray is optional - will be set to the ['text'] property of the TreeStore record by default
            _re = re || new RegExp(value, "ig"),  // the regExp could be modified to allow for case-sensitive, starts  with, etc.
            visibleNodes = [];                    // array of nodes matching the search criteria + each parent non-leaf node up to root

        tree.expandAll();                         // expand all nodes for the the following iterative routines

        // iterate over all nodes in the tree in order to evaluate them against the search criteria
        tree.getRootNode().cascadeBy(function(node) {
            var p= 0, prop;
            for (; p < _propArray.length; p++) {
                prop = node.get(_propArray[p]);
                if (prop && prop.match && prop.match(_re)) {  // if the node matches the search criteria and is a leaf (could be modified to search non-leaf nodes)
                    matches.push(node);                       // add the node to the matches array
                    return;
                }
            }
        });

        me._checkMatches(matches, tree, visibleNodes);
    },

    filterBy: function(fn, scope) {
        if (!Ext4.isFunction(fn)) {
            return;
        }

        var me = this,
            tree = me.tree,
            matches = [],
            visibleNodes = [];

        tree.expandAll();                       // expand all nodes for the the following iterative routines

        tree.getRootNode().cascadeBy(function(node) {
            if (fn.call(scope || this, node) === true) { matches.push(node); }
        });

        me._checkMatches(matches, tree, visibleNodes);
    },

    clearFilter: function () {
        var me = this,
            tree = this.tree,
            root = tree.getRootNode();

        if (me.collapseOnClear) { tree.collapseAll(); }              // collapse the tree nodes
        root.cascadeBy(function (node) {                             // final loop to hide/show each node
            var viewNode = Ext4.fly(tree.getView().getNode(node));   // get the dom element associated with each node
            if (viewNode) {                                          // the first one is undefined ? escape it with a conditional and show  all nodes
                viewNode.show();
            }
        });
    },

    _checkMatches: function(matches, tree, visibleNodes) {

        var me = this,
            viewNode,
            root = tree.getRootNode();

        if (me.allowParentFolders === false) {     // if me.allowParentFolders is false (default) then remove any non-leaf nodes from the regex match
            Ext4.each(matches, function(match) {
                if (match && !match.isLeaf()) { Ext4.Array.remove(matches, match); }
            });
        }

        Ext4.each(matches, function(item, i, arr) {   // loop through all matching leaf nodes
            root.cascadeBy(function(node) {           // find each parent node containing the node from the matches array
                if (node.contains(item)) {
                    visibleNodes.push(node);           // if it's an ancestor of the evaluated node add it to the visibleNodes  array
                }
            });
            if (me.allowParentFolders === true &&  !item.isLeaf()) { // if me.allowParentFolders is true and the item is a non-leaf item
                item.cascadeBy(function(node) {                     // iterate over its children and set them as visible
                    visibleNodes.push(node)
                });
            }
            visibleNodes.push(item);   // also add the evaluated node itself to the visibleNodes array
        });

        root.cascadeBy(function(node) {                             // finally loop to hide/show each node
            viewNode = Ext4.fly(tree.getView().getNode(node));       // get the dom element associated with each node
            if (viewNode) {                                          // the first one is undefined ? escape it with a conditional
                viewNode.setVisibilityMode(Ext4.Element.DISPLAY);    // set the visibility mode of the dom node to display (vs offsets)
                viewNode.setVisible(Ext4.Array.contains(visibleNodes, node));
            }
        });

        if (matches.length === 0) {
            tree.fireEvent('nomatches');
        }
        else {
            tree.fireEvent('hasmatches');
        }
    }
});

Ext4.define('LABKEY.ext4.DataViewsPanel', {

    extend: 'Ext.panel.Panel',

    layout: 'border',

    frame: false,

    border: false,

    allowCustomize: true,

    allowEdit: true,

    pageId: -1,

    index: -1,

    statics: {
        MAX_HEIGHT: 3000,
        MAX_DYNAMIC_HEIGHT: 700,
        MIN_HEIGHT: 200
    },

    // delete views template
    deleteTpl: new Ext4.XTemplate(
            '<div><span>Are you sure you want to delete the following?</span></div><br/>' +
            '<tpl for=".">' +
                '<tpl if="data.type">' +
                    '<div><span' +
                    '<tpl if="data.iconCls">' +
                        ' class="{data.iconCls}"></span><span>' +
                    '<tpl else>' +
                        '><img src="{[this.getBlankImage()]}" style="background-image:url({data.icon})" class="x4-tree-icon dataview-icon">' +
                    '</tpl>' +
                    '&nbsp;&nbsp;{data.name}</span></div>' +
                '</tpl>' +
            '</tpl>',
            {
                getBlankImage : function() {
                    return Ext4.BLANK_IMAGE_URL;
                }
            }
    ),

    // delete unsupported template
    unsupportedDeleteTpl: new Ext4.XTemplate(
            '<div><span>Delete is not allowed for the following type(s), please omit them from your selection.</span></div><br/>' +
            '<tpl for=".">' +
                '<div><span' +
                '<tpl if="data.iconCls">' +
                    ' class="{data.iconCls}"></span><span>' +
                '<tpl else>' +
                    '><img src="{[this.getBlankImage()]}" style="background-image:url({data.icon})" class="x4-tree-icon dataview-icon">' +
                '</tpl>' +
                '&nbsp;&nbsp;{data.name}</span></div>' +
            '</tpl>',
            {
                getBlankImage : function() {
                    return Ext4.BLANK_IMAGE_URL;
                }
            }
    ),

    constructor : function(config) {
        LABKEY.ext4.DataViewUtil.defineModels();

        this.callParent([config]);

        this.addEvents(
            'enableCustomMode',
            'disableCustomMode',
            'enableEditMode',
            'disableEditMode',
            'initgrid'
        );
    },

    initComponent : function() {

        // private settings
        Ext4.apply(this, {
            customMode: false,
            editMode: this.manageView,
            searchVal: '',
            _height: null,
            _useDynamicHeight: true,
            editInfo: {},
            store: null,
            centerPanel: null,
            gridPanel: null
        });

        // secondary display panels
        this.customPanel = Ext4.create('Ext.panel.Panel', {
            layout: 'fit',
            border: false, frame: false
        });

        // primary display panels
        this.items = [{
            xtype: 'panel',
            region: 'north',
            itemId: 'north',
            layout: 'fit',
            style: 'margin-bottom: 10px',
            hidden: true,
            preventHeader: true,
            border: false, frame: false,
            height: 261,
            items: [ this.customPanel ]
        },{
            xtype: 'panel',
            region: 'center',
            border: false, frame: false,
            layout: 'fit',
            flex: 4,
            items: [ this.getCenter() ]
        }];

        this.callParent();

        this.on('enableCustomMode', this.onEnableCustomMode,  this);
        this.on('disableCustomMode', this.onDisableCustomMode, this);
        this.on('enableEditMode', this.onEnableEditMode, this);
        this.on('disableEditMode', this.onDisableEditMode, this);

        Ext4.QuickTips.init();

        // Initialize and bind "Full" store
        this.getFullStore().on('load', this.refreshViewStore, this);
    },

    /**
     * The "Full" Store contains all of the views/records from the server. This is not exposed directly
     * to the UI, but rather, it acts as the source for other stores (e.g. "View" store).
     */
    getFullStore : function() {

        if (!this.fullStore) {

            // The TreeStore does not async load properly when there are multiple outbound/inbound
            // requests that need to be handled.
            this.LOCK_FULL_STORE = false;

            this.fullStore = Ext4.create('Ext.data.TreeStore', {
                pageSize: 100,
                model   : 'Dataset.Browser.View',
                proxy   : {
                    type   : 'ajax',
                    url    : LABKEY.ActionURL.buildURL('reports', 'browseDataTree.api'),
                    extraParams : {
                        // These parameters are required for specific webpart filtering
                        pageId      : this.pageId,
                        index       : this.index,
                        returnUrl   : this.returnUrl,
                        manageView  : this.manageView
                    },
                    reader : 'json'
                },
                listeners: {
                    beforeload: function() {
                        if (this.LOCK_FULL_STORE) {
                            return false; // prevent load
                        }
                        this.LOCK_FULL_STORE = true;

                        if (this.catWinID) {
                            var w = Ext4.getCmp(this.catWinID);
                            if (w && !w.isVisible())
                                this.getCenter().getEl().mask('Loading...');
                        }
                        else {
                            this.getCenter().getEl().mask('Loading...');
                        }
                    },
                    load: function() {
                        this.LOCK_FULL_STORE = false;
                        this.getCenter().getEl().unmask();
                    },
                    scope: this
                },
                scope: this
            });
        }

        return this.fullStore;
    },

    /**
     * The "View" store is used to back the UI. It is a local store which sources it's nodes from the
     * "Full" store.
     */
    getViewStore : function() {

        if (!this.viewStore) {
            this.viewStore = Ext4.create('Ext.data.TreeStore', {
                pageSize: 100,
                model: 'Dataset.Browser.View',
                root: {
                    text: 'empty root node',
                    children: []
                },
                sortRoot: 'displayOrder'
            });
        }

        return this.viewStore;
    },

    /**
     * This refreshes the "View" stores results from the "Full" store.
     */
    refreshViewStore : function() {
        var viewStore = this.getViewStore();
        var fullStoreRoot = this.getFullStore().getRootNode();

        // Due to an error in ExtJS tree panel implementation, you cannot call tree store.removeAll()
        if (fullStoreRoot) {
            // Set preventLoad to true to avoid error when the tree store attempts to auto-expand
            // the root node upon set.
            viewStore.setRootNode(fullStoreRoot.copy(), true /* preventLoad */);
        }
        else if (!viewStore.getRootNode()) {
            viewStore.setRootNode({
                text: 'empty root node',
                children: []
            });
        }

        this.addVisibleNodes(fullStoreRoot, viewStore.getRootNode());

        if (!this.customMode) {
            this.adjustHeight();
        }
    },

    addVisibleNodes : function(fullStoreNode, viewStoreNode) {
        var visibleChildFlag = false;

        if (fullStoreNode) {

            fullStoreNode.eachChild(function(fullStoreChildNode) {
                var viewStoreChildNode = fullStoreChildNode.copy();

                if (!fullStoreChildNode.isLeaf()) {
                    var wasVisibleChildAdded = this.addVisibleNodes(fullStoreChildNode, viewStoreChildNode);
                    if (wasVisibleChildAdded) {
                        viewStoreNode.appendChild(viewStoreChildNode);  // don't append categories until we're sure they have children
                        visibleChildFlag = true;
                    }
                }
                else if (this.visibleFilter(fullStoreChildNode)) {  // visible leaf node
                    viewStoreNode.appendChild(viewStoreChildNode);
                    visibleChildFlag = true;
                }

                // otherwise is non-visible leaf node, so ignore

            }, this);
        }

        return visibleChildFlag;
    },

    visibleFilter : function(rec) {
        var createdByMe = (rec.data.createdByUserId == LABKEY.user.id);

        // match 'mine' if current user is either the author or creator
        if (this.searchMine && rec.data) {
            if ((rec.data.authorUserId != LABKEY.user.id) && !createdByMe) {
                return false;
            }
        }

        // Show hidden only in edit mode. Admins see all; authors & editors see only their own.
        if (!rec.data.visible) {
            if (this.editMode && (createdByMe || LABKEY.user.isAdmin)) {
                return true;
            }
            return false;
        }

        return true;
    },

    getCenter : function() {
        if (!this.centerPanel) {
            this.centerPanel = Ext4.create('Ext.panel.Panel', {
                border: false, frame: false,
                layout: 'fit',
                listeners: { render: this.configureGrid, scope: this }
            });
        }

        return this.centerPanel;
    },

    getNorth : function() {
        return this.getComponent('north');
    },

    /**
     * Invoked once when the grid is initially setup
     */
    configureGrid : function() {
        this.getCenter().getEl().mask('Initializing...');
        this.getConfiguration(this.onConfigure, this);
    },

    /**
     * Handles the json response from reports/browseData.api
     * @param json
     */
    onConfigure : function(json) {
        this.getCenter().getEl().unmask();

        if (json.webpart) {
            this._height = parseInt(json.webpart.height);
            this._useDynamicHeight = (json.webpart.useDynamicHeight === 'true');
            this.adjustHeight();
        }
        this.dateFormat = LABKEY.extDefaultDateFormat;
        this.dateRenderer = function(value) {
            var formattedVal = Ext4.util.Format.date(value, LABKEY.extDefaultDateFormat);
            return LABKEY.Utils.encodeHtml(formattedVal);
        };
        this.editInfo = json.editInfo;
        this.sortOrder = json.sortOrder;

        this.initGrid(json.visibleColumns);
    },

    /**
     * Invoked each time the column model is modified from the customize view
     */
    updateConfiguration : function() {
        this.getCenter().getEl().mask('Initializing...');

        this.getConfiguration(function(json) {
            this.getCenter().getEl().unmask();
            this._height = parseInt(json.webpart.height);
            this.getCenter().removeAll(true);
            this.adjustHeight();
            this.initGrid(json.visibleColumns);
            this.getFullStore().load();
        }, this);
    },

    getConfiguration : function(handler, scope) {

        var extraParams = {
            // These parameters are required for specific webpart filtering
            includeData : false,
            pageId : this.pageId,
            index  : this.index,
            manageView : this.manageView
        };

        Ext4.Ajax.request({
            url    : LABKEY.ActionURL.buildURL('reports', 'browseData.api', null, extraParams),
            method : 'GET',
            success: function(response) {
                if (handler) {
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
                '<table>' +
                '<tpl if="data.category != undefined && (data.category.length || data.category.label)">' +
                '<tr><td>Source:</td><td>{[this.renderCategory(values.data.category)]}</td></tr>' +
                '</tpl>' +
                '<tpl if="this.isValid(data.createdBy)">' +
                '<tr><td>Created By:</td><td>{[fm.htmlEncode(values.data.createdBy)]}</td></tr>' +
                '</tpl>' +
                '<tpl if="this.isValid(data.authorDisplayName)">' +
                '<tr><td>Author:</td><td>{[fm.htmlEncode(values.data.authorDisplayName)]}</td></tr>' +
                '</tpl>' +
                '<tpl if="this.isValid(data.type)">' +
                '<tr><td>Type:</td><td>{[fm.htmlEncode(values.data.type)]}</td></tr>' +
                '</tpl>' +
                '<tpl if="this.isValid(data.status)">' +
                '<tr><td>Status:</td><td>{[fm.htmlEncode(values.data.status)]}</td></tr>' +
                '</tpl>' +
                '<tpl if="data.refreshDate != undefined">' +
                '<tr><td valign="top">Data Cut Date:</td><td>{[this.renderDate(values.data.refreshDate)]}</td></tr>' +
                '</tpl>' +
                '<tpl if="this.isValid(data.description)">' +
                '<tr><td valign="top">Description:</td><td>{[fm.htmlEncode(values.data.description)]}</td></tr>' +
                '</tpl>' +
                '</table>' +
                '<div class="thumbnail"></div>' +
                '</div>' +
                '</tpl>',
                {
                    isValid : function(value) {
                        return Ext4.isDefined(value) && value.length > 0;
                    },
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
                items    : [{
                    xtype  : 'panel',
                    border : false, frame : false,
                    tpl    : tipTpl
                }],
                listeners: {
                    // Change content dynamically depending on which element triggered the show.
                    beforeshow : function(tip) {
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
                    show : function(tip) {
                        var h = tip.getHeader();
                        if (h && !Ext4.isEmpty(h.title)) {
                            h.setTitle(Ext4.htmlEncode(h.title));
                        }

                        // Have to load the image on show and force layout after image loads because Ext assumes 0
                        // width/height since the image has not been loaded when it calculates the layout.
                        var rec = this.getRecord(tip.triggerElement.parentNode);

                        if (rec) {
                            var thumbnail = new Image();
                            thumbnail.style.width = 'auto';
                            thumbnail.style.height = 'auto';
                            thumbnail.onload = function(){
                                var thumbnailDiv = tip.getEl().dom.querySelector('.thumbnail');
                                thumbnailDiv.appendChild(thumbnail);
                                tip.doLayout();
                            };
                            thumbnail.src = rec.get('thumbnail');
                        }
                    },
                    scope : view
                },
                scope : this
            });
        };

        this.gridPanel = Ext4.create('Ext.tree.Panel', {
            id       : 'data-browser-grid-' + this.webpartId,
            store    : this.getViewStore(),
            tbar     : this.getSearchToolbar(),
            border   : false, frame: false,
            cls      : 'iScroll', // webkit custom scroll bars
            scroll   : 'vertical',
            columns  : this.initGridColumns(visibleColumns),
            multiSelect: this.manageView,
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
                    if (cls.search(/edit-views-link/i) >= 0) {
                        this.onEditClick(view, record);
                    }
                },
                afterlayout : function(p) {
                    /* Apply selector for tests */     // TODO: is this used? I believe the class names have changed in Ext4.2.1
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

        this.getCenter().add(this.gridPanel);

        this.fireEvent('initgrid', this.gridPanel);
        this.getFullStore().load();
    },

    initGridColumns : function(visibleColumns) {

        var _columns = [{
            id       : 'edit-column-' + this.webpartId,
            text     : '&nbsp;',
            width    : 40,
            sortable : false,
            menuDisabled : true,
            renderer : function(view, meta, rec) {
                if (!this.editMode) {
                    meta.style = 'display: none;';  // what a nightmare
                }

                var editable = false;
                if (!rec.data.readOnly && this.editInfo[rec.data.dataType])
                    editable = this.editInfo[rec.data.dataType].actions['update'];

                // an item needs an edit info interface to be editable
                if (editable)
                    return '<span height="16px" class="edit-link-cls-' + this.webpartId + ' edit-views-link fa fa-pencil"></span>';
                else
                    return '<span height="16px" class="edit-link-cls-' + this.webpartId + '"></span>';
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
            hidden   : !this.manageView,
            scope    : this
        },{
            xtype    : 'fatreecolumn',
            text     : 'Name',
            flex     : 1,
            dataIndex: 'name',
            minWidth : 200,
            menuDisabled : true,
            sortable : false,
            tdCls    : 'x4-name-column-cell',
            renderer : Ext4.htmlEncode,
            scope    : this
        },{
            id       : 'category-column-' + this.webpartId,
            text     : 'Category',
            flex     : 1,
            menuDisabled : true,
            sortable : false,
            dataIndex: 'categorylabel',
            renderer : Ext4.htmlEncode,
            hidden   : true
        }];

        if (visibleColumns['Type'] && visibleColumns['Type'].checked) {
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

        if (visibleColumns['Details'] && visibleColumns['Details'].checked) {
            _columns.push({
                xtype    : 'templatecolumn',
                text     : 'Details',
                width    : 60,
                dataIndex: 'detailsUrl',
                tdCls    : 'type-column',
                menuDisabled : true,
                sortable : false,
                tpl      : '<tpl if="detailsUrl">' +
                                '<a data-qtip="Click to navigate to the Detail View" href="{detailsUrl}">' +
                                    '<span class="fa fa-list-ul" alt="Details..."></span>' +
                                '</a>' +
                            '</tpl>',
                scope    : this
            });
        }

        if (visibleColumns['Data Cut Date'] && visibleColumns['Data Cut Date'].checked) {
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

        if (visibleColumns['Status'] && visibleColumns['Status'].checked) {
            var statusTpl = '<tpl if="status == \'Draft\'">' +
                    '<span class="fa fa-pencil-square"></span>' +
                    '</tpl>' +
                    '<tpl if="status == \'Final\'">' +
                    '<span class="fa fa-check-square"></span>' +
                    '</tpl>' +
                    '<tpl if="status == \'Locked\'">' +
                    '<span class="fa fa-lock"</span>' +
                    '</tpl>' +
                    '<tpl if="status == \'Unlocked\'">' +
                    '<span class="fa fa-unlock-alt"></span>' +
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

        if (visibleColumns['Modified'] && visibleColumns['Modified'].checked) {
            _columns.push({
                text     : 'Modified',
                width    : 100,
                renderer : this.dateRenderer,
                menuDisabled : true,
                sortable : false,
                dataIndex: 'modified'
            });
        }

        if (visibleColumns['Content Modified'] && visibleColumns['Content Modified'].checked) {
            _columns.push({
                text     : 'Content Modified',
                width    : 140,
                renderer : this.dateRenderer,
                menuDisabled : true,
                sortable : false,
                dataIndex: 'contentModified'
            });
        }

        if (visibleColumns['Author'] && visibleColumns['Author'].checked) {
            _columns.push({
                text     : 'Author',
                width    : 100,
                dataIndex: 'authorDisplayName',
                menuDisabled : true,
                sortable : false,
                scope    : this
            });
        }

        if (visibleColumns['Access'] && visibleColumns['Access'].checked) {
            _columns.push({
                header   : 'Access',
                width    : 100,
                dataIndex: 'access',
                sortable : false,
                menuDisabled : true,
                renderer : this.accessRenderer,
                scope : this
            });
        }

        return _columns;
    },

    getSearchToolbar : function() {

        var msgField = Ext4.create('Ext.Component', {
            tpl: '<span>{msg:htmlEncode}</span>',
            data: {},
            flex: 4
        });

        var clearMessage = function() {
            msgField.update({});
        };

        var filterSearch = function() {
            this.searchVal = searchField.getValue();
            this.applySearchFilter();
        };

        var filterTask = new Ext4.util.DelayedTask(filterSearch, this);

        var searchField = Ext4.create('Ext.form.field.Text', {
            emptyText: 'Filter name, category, etc.',
            enableKeyEvents: true,
            cls: 'dataset-search',
            flex: 5,
            maxWidth: 400,
            border: false,
            frame: false,
            listeners: {
                change : function() {
                    filterTask.delay(350);
                    clearMessage();
                }
            }
        });

        var mineField = Ext4.create('Ext.form.field.Checkbox', {
            boxLabel: '<span data-qtip="Check to show only views that have either been created by me or that list me as the author.">&nbsp;Mine</span>',
            boxLabelAlign: 'before',
            border: false,
            frame: false,
            flex: 1,
            listeners: {
                change : function(cmp, checked) {
                    this.searchMine = checked === true;
                    this.refreshViewStore();
                    this.applySearchFilter();
                    clearMessage();
                },
                scope: this
            },
            scope: this
        });

        this.on('initgrid', function(grid) {
            this.on('disableCustomMode', clearMessage);

            grid.on({
                remove: clearMessage,
                hasmatches: clearMessage,
                nomatches: function() {
                    msgField.update({msg: 'No results found.'})
                }
            });
        }, this);

        // toolbar
        return {
            height: 30,
            border: false,
            layout: {
                type: 'hbox',
                align: 'stretch'
            },
            items: [
                searchField,
                msgField,
                '->',
                mineField
            ]
        };
    },

    adjustHeight : function(offset) {
        var newHeight = (offset ? offset : 0) + (this._useDynamicHeight ? this.getCalculatedPanelHeight() : this._height);
        if (newHeight !== this.getHeight()) {
            this.setHeight(newHeight);
        }
    },

    getCalculatedPanelHeight : function () {

        var count = 0;
        var dataViewPanelHeaderSize = 125;
        var heightPerRecord = 25;
        var rootNode = this.getFullStore().getRootNode();

        // will calculate height to include both hidden and non hidden views. This will make the height be
        // a bit big if there are many hidden views.
        if (rootNode) {
            rootNode.eachChild(function(rec) {
                count++;
                count += rec.childNodes.length;
            });
        }

        var height = count * heightPerRecord + dataViewPanelHeaderSize;
        // make the maximum height that can be dynamically computed be MAX_DYNAMIC_HEIGHT,
        // if user wants it bigger it can be customized up to MAX_HEIGHT
        if (height > LABKEY.ext4.DataViewsPanel.MAX_DYNAMIC_HEIGHT) {
            height = LABKEY.ext4.DataViewsPanel.MAX_DYNAMIC_HEIGHT;
        }
        else if (height < LABKEY.ext4.DataViewsPanel.MIN_HEIGHT) {
            height = LABKEY.ext4.DataViewsPanel.MIN_HEIGHT;
        }

        return height;
    }, 

    applySearchFilter : function() {

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

            return answer;
        };

        this.gridPanel.clearFilter();
        this.gridPanel.filterBy(filter, this);
    },

    isCustomizable : function() {
        return this.allowCustomize;
    },

    isEditable : function() {
        return this.allowEdit;
    },

    /* called from dataViews.jsp -- customizeDataViews */
    /**
     * Takes the panel into/out of customize mode. Customize mode allows users to view edit links,
     * administrate view categories and determine what data types should be shown.
     */
    customize : function() {

        if (!this.isCustomizable())
            return false;

        this.fireEvent((this.customMode ? 'disableCustomMode' : 'enableCustomMode'), this);
    },

    onEnableCustomMode : function() {

        var north = this.getNorth();
        north.show(null, function() {
            this.getEl().mask('Loading Customize...');
        }, north);

        this.adjustHeight(260);
        this.customMode = true;

        this.getConfiguration(function(json) {
            this.getNorth().getEl().unmask();
            this._displayCustomMode(json);
        }, this);
    },

    onDisableCustomMode : function() {

        if (this.customPanel && this.customPanel.isVisible()) {
            this.getNorth().hide();
        }
        this.customMode = false;
        this.refreshViewStore();
    },

    /* called from dataViews.jsp -- editDataViews */
    edit : function() {
        if (this.isEditable()) {
            this.fireEvent((this.editMode ? 'disableEditMode' : 'enableEditMode'), this);
        }
    },

    onEnableEditMode : function() {
        this.editMode = true;
        this._getEditColumn().show();
        this.refreshViewStore();
        this.applySearchFilter();
    },

    onDisableEditMode : function() {
        this.editMode = false;
        this._getEditColumn().hide();
        this.refreshViewStore();
        this.applySearchFilter();
    },

    /* called from dataViews.jsp -- deleteDataViews */
    deleteSelected : function() {

        var gpSelModel = this.gridPanel.getSelectionModel();

        if (gpSelModel) {

            var sel = gpSelModel.getSelection();
            if (sel.length > 0) {

                var unsupported = [];
                var viewsToDelete = [];

                // make sure the view provider supports delete
                Ext4.each(sel, function(rec){
                    var type = rec.data.dataType;

                    if (type) {
                        if (!this.editInfo[type] || !this.editInfo[type].actions['delete'])
                            unsupported.push(rec);
                        else
                            viewsToDelete.push({id : rec.data.id, dataType : rec.data.dataType});
                    }

                }, this);

                if (unsupported.length > 0) {

                    Ext4.Msg.show({
                        title   : 'Delete',
                        msg     : this.unsupportedDeleteTpl.apply(unsupported),
                        buttons : Ext4.Msg.OK,
                        icon    : Ext4.MessageBox.INFO
                    });
                }
                else if (viewsToDelete.length > 0) {

                    Ext4.Msg.show({
                        title   : 'Delete',
                        msg     : this.deleteTpl.apply(sel),
                        scope   : this,
                        buttons : Ext4.Msg.OKCANCEL,
                        icon    : Ext4.MessageBox.QUESTION,
                        fn      : function(id) {

                            if (id == 'ok') {
                                Ext4.Ajax.request({
                                    url     : LABKEY.ActionURL.buildURL("reports", "deleteViews.api"),
                                    scope   : this,
                                    jsonData: {views : viewsToDelete},
                                    success : function() {
                                        this.getFullStore().load();
                                    },
                                    failure : LABKEY.Utils.getCallbackWrapper(function(json, response, options) {
                                        Ext4.Msg.alert("Delete", "Deletion Failed: " + json.exception);
                                    }, null, true)
                                });
                            }
                        }
                    });
                }
            }
            else {
                Ext4.Msg.show({
                    title   : 'Delete',
                    msg     : 'No items have been selected',
                    buttons : Ext4.Msg.OK,
                    icon    : Ext4.MessageBox.INFO
                });
            }
        }
    },
    // private
    _displayCustomMode : function(data) {

        // reset the update config flag, we only want to re-fetch the configuration if the visible columns change
        this.updateConfig = false;

        // panel might already exist
        if (this.customPanel && this.customPanel.items.length > 0) {

            this.refreshViewStore();

            this.getNorth().getEl().unmask();
            return;
        }

        var cbItems = [],
            cbColumns = [],
            sizeItems = [];

        if (Ext4.isArray(data.types)) {
            for (var i=0; i < data.types.length; i++) {
                cbItems.push({
                    boxLabel: data.types[i].name,
                    name: data.types[i].name,
                    checked: data.types[i].visible,
                    width: 150,
                    uncheckedValue : '0'
                });
            }
        }

        if (data.visibleColumns) {
            Ext4.iterate(data.visibleColumns, function(col, prop) {
                cbColumns.push({
                    boxLabel: col,
                    name: col,
                    checked: prop.checked === true,
                    uncheckedValue: '0',
                    width: 125,
                    maxWidth: 150,
                    handler: function() {
                        this.updateConfig = true;
                    },
                    scope: this
                });
            }, this);
        }

        // default height is dynamically sized to number of rows
        sizeItems.push({
            boxLabel: 'Default (dynamic)',
            name: 'height',
            inputValue: true,
            minWidth: 75,
            checked: this._useDynamicHeight === true,
            handler: function(grp, chk) {
                var fields = this.query('numberfield');

                if (grp.getValue()) {
                    this._useDynamicHeight = grp.inputValue === true;
                    this.updateConfig = true;
                }

                // always show the custom height size selector even when dynamic is selected, but make it disabled
                if (fields && fields.length === 1) {
                    fields[0].setVisible(true);
                    fields[0].setDisabled(chk);
                }
            },
            scope: this
        });

        // custom height
        sizeItems.push({
            boxLabel: 'Custom',
            name: 'height',
            inputValue: 0,
            minWidth: 75,
            checked: !this._useDynamicHeight,
            handler: function(grp, chk) {
                var fields = this.query('numberfield');

                if (grp.getValue()) {
                    if (grp.inputValue === 0) {
                        this._useDynamicHeight = false;
                    }
                }
                else {
                    this._useDynamicHeight = true;
                    // remove any value the user may have placed in the height input box before they clicked the use dynamic height radio button
                    fields[0].setValue(this._height);
                }
                this.updateConfig = true;

                // always show the custom height size selector, and make it enabled
                if (fields && fields.length === 1) {
                    fields[0].setVisible(true);
                    fields[0].setDisabled(!chk);
                }
            },
            scope: this
        });

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
                fieldLabel : 'Display Height',
                columns    : 2,
                height     : 40,
                width      : 300,
                items      : sizeItems
            },{
                xtype           : 'numberfield',
                minValue        : LABKEY.ext4.DataViewsPanel.MIN_HEIGHT,
                maxValue        : LABKEY.ext4.DataViewsPanel.MAX_HEIGHT,
                value           : this._height,
                emptyText       : '200',
                disabled        : this._useDynamicHeight,
                name            : 'height',
                listeners: {
                    change: function(cmp, newValue) {
                        this.updateConfig = true;
                        var height = parseInt(newValue);
                        if (height >= LABKEY.ext4.DataViewsPanel.MIN_HEIGHT && height <= LABKEY.ext4.DataViewsPanel.MAX_HEIGHT) {
                            this._height = height;
                        }
                    },
                    scope: this
                }
            },{
                xtype      : 'radiogroup',
                fieldLabel : 'Sort',
                columns    : 2,
                height     : 50,
                width      : 250,
                items      : [{
                    boxLabel: 'Alphabetical',
                    name: 'sortOrder',
                    inputValue: 'ALPHABETICAL',
                    checked: this.sortOrder === 'ALPHABETICAL',
                    handler: function (grp) {
                        if (grp.getValue()) {
                            this.updateConfig = true;
                        }
                    },
                    scope: this
                },{
                    boxLabel: 'By Display Order',
                    name: 'sortOrder',
                    inputValue: 'BY_DISPLAY_ORDER',
                    checked: this.sortOrder === 'BY_DISPLAY_ORDER',
                    handler: function (grp) {
                        if (grp.getValue()) {
                            this.updateConfig = true;
                        }
                    },
                    scope: this
                }]
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
            minHeight : 250,
            bodyPadding : 10,
            layout : 'fit',
            items : [formPanel],
            buttons : [{
                text    : 'Cancel',
                handler : function() {
                    // remove any value the user may have placed in the height input box before they clicked the cancel button
                    var fields = this.query('numberfield');
                    fields[0].setValue(this._height);
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
                        var params = form.getValues();
                        params['webpart.height'] = this._height;
                        params['webpart.useDynamicHeight'] = this._useDynamicHeight;
                        this.getNorth().getEl().mask('Saving...');
                        Ext4.Ajax.request({
                            url    : LABKEY.ActionURL.buildURL('project', 'customizeWebPartAsync.api', null, params),
                            method : 'POST',
                            success : function() {
                                var north = this.getNorth();
                                north.getEl().unmask();
                                north.hide();

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
                                    this.getFullStore().load();
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

        this.refreshViewStore();

        this.customPanel.add(panel);
        this.getNorth().getEl().unmask();
    },

    _getEditColumn : function() {
        return Ext4.getCmp('edit-column-' + this.webpartId);
    },

    /**
     * Called when a user clicks to edit a specific data view record.
     * @param view
     * @param record
     */
    onEditClick : function(view, record) {

        // grab the map of available fields from the edit info for the view type
        var editInfo = {};

        if (this.editInfo[record.data.dataType])
            editInfo = this.editInfo[record.data.dataType].props;

        /* Record 'id' is required */
        if (record.data.id == undefined || record.data.id == "") {
            console.warn('ID is required');
        }

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
                        url : LABKEY.ActionURL.buildURL('reports', 'editView.api'),
                        method : 'POST',
                        submitEmptyText : false,
                        params  :  { 'X-LABKEY-CSRF': LABKEY.CSRF },
                        success : function() {
                            this.onEditSave();
                            editWindow.getEl().unmask();
                            editWindow.close();
                        },
                        failure : function(form, action) {
                            editWindow.getEl().unmask();
                            var msg = 'An error occurred saving the properties';
                            if (action.response && action.response.responseText) {
                                var json = Ext4.decode(action.response.responseText);

                                if (json.exception)
                                    msg = msg + ' - ' + json.exception;
                            }
                            Ext4.Msg.alert("Error", msg);
                        },
                        scope : this
                    });
                }
            },
            scope: this
        }];

        if (this.editInfo[record.data.dataType] && this.editInfo[record.data.dataType].actions['delete']) {
            var fullStore = this.getFullStore(); // Need because for some reason this.gridPanel is not in scope
                                                     // in the successCallback below.
            buttons.push({
                text: 'Delete',
                scope: this,
                handler: function ()
                {
                    var msg = 'Are you sure you want to delete "' + record.data.name + '"';
                    var successCallback = function (response)
                    {
                        editWindow.close();
                        // Refresh the store, so deleted reports go away.
                        fullStore.load();
                    };
                    var failureCallback = function (response)
                    {
                        Ext4.MessageBox.alert('Error Deleting Report', 'There was an error deleting the report "' + record.data.name + '"');
                        editWindow.close();
                    };
                    var confirmCallback = function (choice)
                    {
                        if (choice == "yes")
                        {
                            editWindow.close();
                            Ext4.Ajax.request({
                                url     : LABKEY.ActionURL.buildURL("reports", "deleteViews.api"),
                                scope   : this,
                                jsonData: {views : record.data},
                                success: successCallback,
                                failure: failureCallback
                            });
                        }
                    };

                    Ext4.MessageBox.confirm('Delete?', msg, confirmCallback, this);
                }
            });
        }

        var editWindow = Ext4.create('Ext.window.Window', {
            maxHeight : 750,
            layout : 'fit',
            cls    : 'data-window',
            draggable : false,
            modal  : true,
            title  : Ext4.htmlEncode(record.get('name')),
            defaults: {
                border: false, frame: false
            },
            bodyPadding : 10,
            items : [{
                xtype : 'dvproperties',
                record          : record,
                extraItems      : [{
                    xtype : 'hidden',
                    name  : 'id',
                    value : record.data.id
                },{
                    xtype : 'hidden',
                    name  : 'dataType',
                    value : record.data.dataType
                }],
                dateFormat      : this.dateFormat,
                visibleFields   : {
                    viewName: editInfo['viewName'],
                    author  : editInfo['author'],
                    status  : editInfo['status'],
                    datacutdate : editInfo['refreshDate'],
                    category    : editInfo['category'],
                    description : editInfo['description'],
                    type        : true,
                    visible     : editInfo['visible'] && !this.manageView,
                    created     : true,
                    shared      : editInfo['shared'],
                    modified    : true,
                    contentModified : true,
                    customThumbnail : editInfo['customThumbnail']
                },
                buttons     : buttons
            }],
            scope : this
        });

        editWindow.show();
    },

    onEditSave : function() {
        this.getFullStore().load();
    },

    onManageCategories : function() {

        var window = LABKEY.study.DataViewUtil.getManageCategoriesDialog();

        window.on('categorychange', function() {
            this.getFullStore().load();
        }, this);

        window.on('close', function() {
            this.getFullStore().reload();
        }, this);

        window.show();
    },

    /* called from dataViews.jsp -- reorderReports */
    onReorderReports : function() {

        var window = LABKEY.study.DataViewUtil.getReorderReportsDialog();
        
        window.on('close', function() {
            this.getFullStore().reload();
        }, this);

        window.show();
    },

    accessRenderer : function(value, meta, rec) {

        var tpl = new Ext4.XTemplate(
            '<a data-qtip="Click to customize the permissions for this view" href="{accessUrl}">{access}</a>');

        if (this.manageView && rec.data.access && rec.data.accessUrl)
            return tpl.apply(rec.data);
        return value;
    }
});
