/*
 * Copyright (c) 2012-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.requiresCss("study/ReportFilterPanel.css");

/**
 * Base class that will render one section of a FilterPanel, displaying a list of checkboxes.
 * In general, it will display the UI to filter a single table.
 * @name LABKEY.ext4.FilterSelectList
 * @cfg description If provided, this HTML will be displayed as a header above the list
 * @cfg store An ext store containing the records to display
 * @cfg fn A filter function that will be applied to the Ext store
 * @cfg labelField The name of the store field used to render the label for each item.  Defaults to 'label'
 * @cfg allowAll If true, a checkbox will be added to toggle selection across all items
 * @cfg showDisplayCol If true, an additional column will be shown between the checkbox and label to use for display
 * @cfg normalWrap if true, wrap the text of the grid cells normally (i.e. removing white-space:nowrap)
 */
Ext4.define('LABKEY.ext4.filter.SelectList', {

    extend : 'Ext.panel.Panel',
    alias: 'widget.labkey-filterselectlist',

    border : false,
    frame  : false,
    bubbleEvents : ['select', 'selectionchange', 'cellclick', 'itemmouseenter', 'itemmouseleave', 'beforeInitGroupConfig', 'afterInitGroupConfig'],

    initComponent : function() {
        Ext4.applyIf(this, {
            labelField: 'label',
            idField: 'id'
        });

        this.addEvents('beforeInitGroupConfig', 'afterInitGroupConfig', 'initSelectionComplete');

        this.items = [];

        if (this.store) {
            this.items.push(this.getGridCfg());
        }

        this.callParent();

        this.getGrid().on('viewready', this.initSelection, this, {single: true, delay: 50});
    },

    getGridCfg : function(isHeader) {

        var initToolTip = function(view) {
            var labelField = this.labelField;
            var _activeLabel;

            var renderTip = function(tip) {
                if (_activeLabel) {
                    tip.update('<div style="font-size:11px;"><b>' + Ext4.htmlEncode(_activeLabel) + '</b><br>Click the label to ' +
                            'select only this item. Click the checkbox to toggle this item and preserve other selections.</div>');
                }
            };

            var loadRecord = function(tip) {
                var parentNode = tip.triggerElement;
                if (parentNode)
                    _activeLabel = parentNode.innerText;

                return _activeLabel != null;
            };

            view.tip = Ext4.create('Ext.tip.ToolTip', {
                target   : view.el,
                delegate : '.lk-filter-panel-label',
                showDelay: 850,
                listeners: {
                    beforeshow : function(tip) {
                        var loaded = loadRecord(tip);
                        renderTip(tip);
                        return loaded;
                    },
                    scope : this
                },
                scope : this
            });
        };

        return {
            xtype       : 'grid',
            itemId      : 'selectGrid',
            gridId      : this.itemId,
            store       : this.store,
            border      : false, frame : false,
            bodyStyle   : 'border: none;',
            hideHeaders : true,
            multiSelect : true,
            columns     : this.getColumnCfg(isHeader),
            features    : this.getFeaturesCfg(),
            pageSelections : {}, // used to track selection across pages
            dockedItems: [this.getPagingToolbarCfg()],
            viewConfig : {
                stripeRows : false,
                listeners  : {
                    render : initToolTip,
                    cellclick : function(cmp, td, idx, record, tr, rowIdx, e) {
                        // clicking on the grid label selects only that item
                        if (e.getTarget('.lk-filter-panel-label')) {
                            this.updateCategorySingleSelection(record);
                        }

                        // clicked on the 'checkbox'
                        else if (e.getTarget('.x4-grid-row-checker') || e.getTarget('.x-grid-row-checker')) {
                            // clicking on the checkbox for a row, selects or deselects just that row
                            this[this.getGrid().getSelectionModel().isSelected(record) ? 'deselect' : 'select'](record, false);
                        }

                        this.updateCategorySelectAll(record.get("categoryName"));
                        return false;
                    },
                    beforecellmousedown: function() {
                        // disable "row" selection for this grid and defer to the cellclick handler above instead
                        return false;
                    },
                    groupclick : function(grid, field, value, e) {
                        var inputEl = this.getCategoryInputEl(value);
                        if (inputEl) {
                            // switch the state of the checkbox for the category group header
                            this.checkGroupHeaderCheckbox(inputEl, !this.isGroupHeaderCheckbox(inputEl));

                            this.getCategoryRecords(value).each(function(rec) {
                                this[this.isGroupHeaderCheckbox(inputEl) ? 'select' : 'deselect'](rec);
                            }, this);
                        }

                        return false; // to prevent the collapse/expand for the grid grouping
                    },
                    scope  : this
                }
            },
            selType: 'checkboxmodel',
            selModel: {
                checkOnly: true, // we handle the category/group label click elsewhere
                injectCheckbox: 1, // add checkbox after the spacer column (used for category identity)
                preventFocus: true // prevent jumping to selection
            },
            bubbleEvents: ['select', 'selectionchange', 'cellclick', 'itemmouseenter', 'itemmouseleave'],
            scope       : this
        };
    },

    getPagingToolbarCfg : function() {
        // issue 22254: add paging toolbar to support > 1000 participants
        return {
            xtype: 'pagingtoolbar',
            cls: 'paging-toolbar',
            store: this.store,
            dock: 'bottom',
            hidden: true,
            border: false,
            beforePageText: '',
            listeners: {
                afterrender: function() {
                    // hide the refresh button and last separator
                    this.items.items[9].hide();
                    this.items.items[10].hide();

                    // we check and show the paging toolbar if the store count matches the page size
                    var checkStoreSize = function(store, pager) {
                        if (store.pageSize == store.getCount()) {
                            pager.show();

                            // add listener to reselect rows on page change
                            var me = pager.up('labkey-filterselectlist');
                            me.getGrid().getView().on('refresh', function(){
                                var pageSelections = this.pageSelections[this.getStore().currentPage];
                                if (pageSelections)
                                {
                                    // need to select by index since the record id will change each time a page loads
                                    Ext4.each(Ext4.Array.pluck(pageSelections, "index"), function(index){
                                        this.getSelectionModel().select(index % store.pageSize, true, true);
                                    }, this);
                                }
                                else if (me.selection)
                                {
                                    this.suspendEvents();
                                    me.initGridSelection(this, me.selection);
                                    this.resumeEvents();
                                }
                            }, me.getGrid());
                        }
                    };
                    if (this.store.getCount() > 0) {
                        checkStoreSize(this.store, this);
                    }
                    else {
                        this.store.on('load', function(){
                            checkStoreSize(this.store, this);
                        }, this, {single: true});
                    }
                }
            }
        };
    },

    getFeaturesCfg : function() {

        var me = this;
        return [{
            ftype: 'grouping',
            enableGroupingMenu: false,
            enableNoGroups: false,
            groupHeaderTpl: [

                // Template
                '<table>',
                    '<tr>',
                    '{groupValue:this.formatValue}',
                    '</tr>',
                '</table>',

                // Helper Methods
                {
                    formatValue: function(v) {

                        var html = '';
                        var displayName = v;
                        var rec = me.store.findRecord('categoryName', v);
                        var classes = 'category-label lk-filter-panel-label' + (me.allowAll ? ' category-label-padding' : '') + (me.normalWrap ? ' normalwrap-gridcell' : '');

                        if (me.allowAll) {
                            html += "<td><div class='category-header' category='" + v + "' />&nbsp;</div></td>";
                        }

                        if (rec) {
                            displayName = rec.get('category') ? rec.get('category').label : 'Cohorts';
                        }

                        return html + '<td><div class="' + classes + '">' + Ext4.htmlEncode(displayName) + '</div></td>';
                    }
                }
            ]
        }];
    },

    initSelection: function() {
        var target = this.getGrid();

        if(!target.store.getCount() || !target.getView().viewReady){
            Ext4.defer(this.initSelection, 10, this);
            return;
        }

        // if there is not a default number of selection to make initially, set it to select all
        if (this.maxInitSelection === undefined)
            this.maxInitSelection = target.store.getCount();

        // default to not selecting a category if its selection is empty
        if (this.defaultSelectUncheckedCategory === undefined)
            this.defaultSelectUncheckedCategory = false;

        this.fireEvent('beforeInitGroupConfig', this, target.store);

        target.suspendEvents(); // queueing of events id depended on
        if (!this.selection) {
            if (this.maxInitSelection >= target.store.getCount()) {
                target.getSelectionModel().selectAll();
                if(this.allowAll){
                    this.updateCategorySelectAllCheckboxes(false);
                }
            }
            else {
                for (var i = 0; i < this.maxInitSelection; i++)
                {
                    var rec = target.store.getAt(i);
                    target.getSelectionModel().select(rec, true);

                    this.updateCategorySelectAll(rec.get("categoryName"));
                }
            }
        }
        else {
            this.initGridSelection(target, this.selection);
            this.updateCategorySelectAllCheckboxes(this.defaultSelectUncheckedCategory);
        }

        target.resumeEvents();

        // fire event to tell the panel the initial selection is compelete, return the number of selected records
        this.fireEvent('afterInitGroupConfig', target.getSelectionModel().getCount(), this);
    },

    initGridSelection: function(grid, selection) {

        var store = grid.getStore();
        var selModel = grid.getSelectionModel();
        var current, rec;

        selModel.deselectAll();

        if (!selection) {
            return;
        }

        for (var s=0; s < selection.length; s++)
        {
            rec = undefined;

            current = selection[s];

            // first, see if an idProperty is present (treat as model)
            if (current.idProperty)
            {
                rec = store.getById(current.get(current.idProperty.name));
            }

            // second, try matching on categoryId and record id
            if (!rec)
            {
                rec = store.getAt(store.findBy(function(record) {
                    return record.get('categoryId') == current.categoryId && record.get('id') == current.id;
                }, this));
            }

            // next try matching on categoryName and record label
            if (!rec)
            {
                rec = store.getAt(store.findBy(function(record) {
                    return record.get('categoryName') == current.categoryName && record.get('label') == current.label;
                }, this));
            }

            // finally try to find a matching record by just the label
            if (!rec)
            {
                var label = null;
                if (current.data && current.data.label)
                    label = current.data.label;
                else if (current.label)
                    label = current.label;

                if (label != null)
                    rec = grid.getStore().findRecord('label', label);
            }

            if (rec)
            {
                // Compare ID && Label if dealing with virtual groups (e.g. not in cohorts, etc)
                if (current.id < 0 && (rec.data.label != current.label))
                    continue;

                grid.getSelectionModel().select(rec, true);
            }
        }
    },

    getGrid: function(){
        return this.down('#selectGrid');
    },

    getColumnCfg : function(isHeader) {

        var field = '';
        // Issue 18619: mask ptid in demo mode
        if (this.panelName == 'participant')
            field = '{[Ext4.String.htmlEncode(LABKEY.id(values["' + this.labelField + '"]))]}';
        else
            field = '{'+this.labelField+':htmlEncode}';

        var classes = ['group-label','lk-filter-panel-label'];
        var style='';
        if (this.normalWrap)
            classes.push('normalwrap-gridcell');
        if (isHeader)
        {
            classes.push('filter-description');
            style = 'font-weight:bold;';  // CONSIDER add to filter-description class?
        }
        var tpl = [
            '<div ext:qtip=" "',
                'class="' + classes.join(' ') + '"',
                'style="' + style + '"',
                'data-id="{id}"',
                'data-type="{type}"',
            '>',
            field,
            '</div>'
        ];

        return [{
            // spacer column, used for category indenting
            width     : 20,
            hidden    : !this.allowAll
        }, {
            dataIndex : 'display',
            hidden    : this.showDisplayCol == undefined || !this.showDisplayCol,
            width     : 20
        },{
            xtype     : 'templatecolumn',
            flex      : 1,
            dataIndex : this.labelField,
            tdCls     : 'x4-label-column-cell',
            tpl       : tpl,
            scope     : this
        },{
            dataIndex : 'type',
            hidden    : true,
            scope     : this
        }];
    },

    getSelection : function(skipIfAllSelected) {

        var target = this.getGrid();
        if (!target)
            return;

        // issue 22254: update the given pages selection in the pageSelections object
        var selection = [];
        if (!target.down('pagingtoolbar').hidden)
        {
            target.pageSelections[target.getStore().currentPage] = target.getSelectionModel().getSelection();

            // concat the selections across pages
            for (var pageIndex in target.pageSelections) {
                selection = selection.concat(target.pageSelections[pageIndex]);
            }
        }
        else {
            selection = target.getSelectionModel().getSelection();
        }

        //if all are checked in a given category, this is treated the same as none checked
        if(skipIfAllSelected)
        {
            var categoriesWithAll = [];
            Ext4.each(target.getStore().collect("categoryName"), function(categoryName){

                var allSelected = true;
                this.getCategoryRecords(categoryName).each(function(rec){
                    if (!target.getSelectionModel().isSelected(rec))
                        allSelected = false;
                });

                if (allSelected)
                    categoriesWithAll.push(categoryName);
            }, this);

            // remove the selection records from those categories that have all selected
            for (var i = 0; i < selection.length; i++)
            {
                if (categoriesWithAll.indexOf(selection[i].get("categoryName")) > -1)
                {
                    selection.splice(i, 1);
                    i--;
                }
            }
        }

        return selection;

    },

    select : function(record, stopEvents, fieldNameForFind) {

        if (stopEvents)
            this.suspendEvents();

        var target = this.getGrid(), rec;

        if (Ext4.isPrimitive(record))
            rec = target.getStore().findRecord(fieldNameForFind || 'id', record);
        else
            rec = record; // assume it is a model instance

        if (rec)
            target.getSelectionModel().select(rec, true);

        if (stopEvents)
            this.resumeEvents();
    },

    deselect : function(record, stopEvents, fieldNameForFind) {

        if (stopEvents)
            this.suspendEvents();

        var target = this.getGrid(), rec;

        if (Ext4.isPrimitive(record))
            rec = target.getStore().findRecord(fieldNameForFind || 'id', record);
        else
            rec = record; // assume it is a model instance

        if (rec)
            target.getSelectionModel().deselect(rec);

        if (stopEvents)
            this.resumeEvents();
    },

    getDescription : function() {
        return this.description;
    },

    /**
     * Not currently used, since existing UI relies on the Ext records, rather than doing traditional server-side filtering.
     */
    getFilterArray : function() {
        return [];
    },

    /**
     * Used to determine if all of the records in the given grid are selected
     */
    isAllSelected : function() {
        var target = this.getGrid();
        return (target.getSelectionModel().getCount() == target.getStore().getCount());
    },

    /**
     * Used on click of group label to select a single record within a category
     */
    updateCategorySingleSelection : function(recordToSelect)
    {
        this.getCategoryRecords(recordToSelect.get("categoryName")).each(function(rec) {
            this[rec == recordToSelect ? 'select' : 'deselect'](rec);
        }, this);
    },

    /**
     * Used to update the state of the 'toggle all' checkbox for a given category (i.e. grid grouping).
     * The 'selectEmptyCategory' option lets a grid set a category to be selected by default if there are no selection for that category (used by dataset facet filter).
     */
    updateCategorySelectAll : function (value, selectEmptyCategory) {
        var el = this.getCategoryInputEl(value);
        if (el)
        {
            // check to see if all of the records in the category are checked or unchecked
            var allChecked = true;
            var allUnchecked = true;
            this.getCategoryRecords(value).each(function(rec) {
                if (!this.getGrid().getSelectionModel().isSelected(rec))
                    allChecked = false;
                else
                    allUnchecked = false;
            }, this);

            if (allChecked)
                this.checkGroupHeaderCheckbox(el, true);
            else if (selectEmptyCategory && allUnchecked)
            {
                this.checkGroupHeaderCheckbox(el, true);
                this.getCategoryRecords(value).each(function(rec) { this.select(rec); }, this);
            }
            else
                this.checkGroupHeaderCheckbox(el, false);
        }
    },

    getCategoryRecords : function(value) {
        return this.getGrid().getStore().queryBy(function(rec) { return rec.get("categoryName") == value; });
    },

    isGroupHeaderCheckbox : function(el) {
        return el.className.indexOf("category-checked") > -1;
    },

    checkGroupHeaderCheckbox : function(el, check) {
        el.setAttribute('class', 'category-header' + (check ? ' category-checked' : ''));
        this.doLayout();
    },

    getCategoryInputEl : function(value) {
        // query for the category header input using the root of the query as the current dom element
        var elArr = Ext4.query('div.category-header[category=' + value + ']', this.getEl().dom);
        return elArr.length == 1 ? elArr[0] : null;
    },

    updateCategorySelectAllCheckboxes : function(selectEmptyCategory) {
        Ext4.each(this.getGrid().getStore().collect("categoryName"), function(category) {
            this.updateCategorySelectAll(category, selectEmptyCategory);
        }, this);
    },

    deselectAll : function(stopEvents) {
        if(!this.getGrid().getView().viewReady)
            this.deselectAll.defer(100, this, [stopEvents]);
        else
        {
            if (stopEvents)
                this.suspendEvents();

            this.selection = undefined;
            this.getGrid().getSelectionModel().deselectAll();
            this.updateCategorySelectAllCheckboxes(false);

            if (stopEvents)
                this.resumeEvents();
        }
    },

    selectAll : function(stopEvents) {
        if(!this.getGrid().getView().viewReady)
            this.selectAll.defer(100, this, [stopEvents]);
        else
        {
            if (stopEvents)
                this.suspendEvents();

            this.selection = undefined;
            this.getGrid().getSelectionModel().selectAll();
            this.updateCategorySelectAllCheckboxes(false);

            if (stopEvents)
                this.resumeEvents();
        }
    }
});

/**
 * The basic unit of a filter panel.  Can contain one or more LABKEY.ext4.filter.SelectList components.
 * @name LABKEY.ext4.filter.SelectPanel
 * @cfg sections Array of config objects for LABKEY.ext4.filter.SelectList
 */
Ext4.define('LABKEY.ext4.filter.SelectPanel', {

    extend : 'Ext.panel.Panel',
    alias: 'widget.labkey-filterselectpanel',

    bubbleEvents : ['select', 'selectionchange', 'cellclick', 'itemmouseenter', 'itemmouseleave', 'initSelectionComplete', 'beginInitSelection'],

    statics : {
        all : function(selectpanel, methodName, stopEvents) {
            var filterPanels = selectpanel.getFilterPanels();
            for (var i=0; i < filterPanels.length; i++) {
                filterPanels[i][methodName](stopEvents);
            }
        }
    },

    constructor : function(config) {
        Ext4.applyIf(config, {
            border : false, frame : false,
            cls    : 'rpf',
            bodyCls: 'rpfbody'
        });

        this.addEvents('initSelectionComplete', 'beginInitSelection');
        this.callParent([config]);
    },

    initComponent : function() {
        this.items = [this.initSelectionPanel()];
        this.callParent();
    },

    initSelectionPanel : function() {
        var filterPanels = [];

        if (this.allowGlobalAll) {

            filterPanels.push({
                xtype       : 'grid',
                itemId      : 'globalSelectAll',
                border      : false, frame : false,
                bodyStyle   : 'border: none;',
                hideHeaders : true,
                multiSelect : true,
                columns     : [{
                    xtype     : 'templatecolumn',
                    dataIndex : 'label',
                    tdCls     : 'x4-label-column-cell',
                    tpl       : '<div style="font-weight:bold;" class="lk-filter-panel-label filter-description">{label:htmlEncode}</div>'
                }],
                selType     : 'checkboxmodel',
                selModel    : {
                    preventFocus: true // prevent jumping to selection
                },
                bubbleEvents: [],
                store  : {
                    xtype  : 'store',
                    fields : ['id', 'label'],
                    data   : [{id: -1, label : 'All'}]
                },
                listeners: {
                    selectionchange: function(model, selected) {
                        !selected.length ? this.deselectAll() : this.selectAll();
                    },
                    scope: this
                },
                scope: this
            });

            this.on('selectionchange', function() {
                this.allSelected();
                return true;
            }, this, {stopPropogation: false});
        }

        for (var f=0; f < this.sections.length; f++) {
            filterPanels.push(Ext4.apply(this.sections[f],{
                xtype : 'labkey-filterselectlist',
                allowAll : this.allowAll,
                showDisplayCol : this.showDisplayCol,
                border : false,
                frame : false,
                panelName : this.panelName
            }));
        }

        if (!filterPanels.length) {
            filterPanels.push({
                xtype : 'box',
                autoEl: {
                    tag : 'div',
                    html: 'No Groups defined'
                }
            });
        }

        this.on('beforeInitGroupConfig', this.handleBeforeInitGroupConfig, this);
        this.on('afterInitGroupConfig', this.handleAfterInitGroupConfig, this);

        return { border: false, frame: false, items: filterPanels };
    },

    /**
     * Returns an array of arrays matching the configuration stores indexes. See the 'collapsed' param for alternative
     * return value.
     * @param collapsed When true the function will return a single array of all selected records. Defaults to false.
     */
    getSelection : function(collapsed, skipIfAllSelected) {
        var selections = [], select;
        var filterPanels = this.getFilterPanels();
        if (filterPanels) {
            for (var i=0; i < filterPanels.length; i++) {
                select = filterPanels[i].getSelection(skipIfAllSelected);
                if (!collapsed) {
                    selections.push(select && select.length ? select : []);
                }
                else {
                    if (select) {
                        for (var j=0; j < select.length; j++) {
                            selections.push(select[j]);
                        }
                    }
                }
            }
        }
        return selections;
    },

    /**
     * @return An array of all filter sections in this panel.  Often this will only be a single panel.
     */
    getFilterPanels : function() {
        return this.query('labkey-filterselectlist[hidden=false]');
    },

    /**
     * Not currently used, since existing UI relies on the Ext records, rather than doing traditional server-side filtering.
     */
    getFilterArray : function() {
        var filterArray = [];
        var filterPanels = this.getFilterPanels();
        for (var i=0; i < filterPanels.length; i++) {
            var filters = filterPanels[i].getFilterArray();
            if(filters.length)
                filterArray = filterArray.concat(filters);
        }
    },

    deselectAll : function(stopEvents) {
        LABKEY.ext4.filter.SelectPanel.all(this, 'deselectAll', stopEvents);
    },

    selectAll : function(stopEvents) {
        LABKEY.ext4.filter.SelectPanel.all(this, 'selectAll', stopEvents);
    },

    initSelection : function() {
        this.fireEvent('beginInitSelection', this);

        var filterPanels = this.getFilterPanels();
        var count = 0;
        for (var i=0; i < filterPanels.length; i++) {
            filterPanels[i].initSelection();
            count += filterPanels[i].getGrid().getSelectionModel().getCount();
        }
        this.allSelected();

        // fire event to tell the panel the initial selection is compelete, return the number of selected records
        this.fireEvent('initSelectionComplete', count, this);
    },

    allSelected : function() {
        var filterPanels = this.getFilterPanels();
        for (var i=0; i < filterPanels.length; i++) {
            if(!filterPanels[i].isAllSelected())
            {
                if (this.allowGlobalAll) {
                    this.getGlobalSelectAllToggle().getSelectionModel().deselect(0, true);
                }

                return false;
            }
        }
        if (this.allowGlobalAll) {
            this.getGlobalSelectAllToggle().getSelectionModel().select(0, true);
        }

        return true;
    },

    /**
     * Nullable if 'allowGlobalAll' is false
     */
    getGlobalSelectAllToggle : function() {
        return this.down('#globalSelectAll');
    },

    handleBeforeInitGroupConfig : function() {

        if (!this.panelsToInit) {
            this.fireEvent('beginInitSelection', this);

            this.panelsToInit = [];
            this.panelSelectCount = 0;
            var filterPanels = this.getFilterPanels();
            for (var i=0; i < filterPanels.length; i++)
                this.panelsToInit.push(filterPanels[i].id);
        }
    },

    handleAfterInitGroupConfig : function(count, cmp) {

        if (this.panelsToInit) {
            var idx = this.panelsToInit.indexOf(cmp.id);
            if (idx > -1)
                this.panelsToInit.splice(idx, 1);
            this.panelSelectCount += count;
            if (this.panelsToInit.length == 0) {
                this.panelsToInit = null;
                this.allSelected();

                this.fireEvent('initSelectionComplete', this.panelSelectCount, this);
            }
        }
    }

});

Ext4.define('LABKEY.ext4.ReportFilterWindow', {
    extend : 'Ext.window.Window',

    constructor : function(config) {

        Ext4.applyIf(config, {
            width         : 250,
            minWidth      : 250,
            minHeight     : 150,
            maxHeight     : 500,
            collapsible   : true,
            collapsed     : true,
            expandOnShow  : true,
            titleCollapse : true,
            draggable     : false,
            cls           : 'report-filter-window',
            title         : 'Filter Report'
        });

        this.callParent([config]);
    },

    initComponent : function() {

        if (!this.items && this.sections) {
            this.filterPanel = Ext4.create('LABKEY.ext4.filter.SelectPanel', {
                filters : this.sections,
                border  : false, frame : false
            });

            this.items = [this.filterPanel];
        }

        if (this.relative) {
            this.relative.on('resize', this.calculatePosition, this);
        }
        this.on('show', this.calculatePosition, this);

        this.callParent();
    },

    calculatePosition : function() {
        if (!this.relative) {
            console.warn('unable to show ReportFilter due to relative component not being provided.');
            this.hide();
            return;
        }

        // elements topleft to targets topright
        if (this.el) {
            this.alignTo(this.relative, this.alignConfig.position, this.alignConfig.offsets);
        }
    }
});
