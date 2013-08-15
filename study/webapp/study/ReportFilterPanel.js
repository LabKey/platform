/*
 * Copyright (c) 2012-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.requiresCss("study/DataViewsPanel.css");

/**
 * Base class that will render one section of a FilterPanel, displaying a list of checkboxes.
 * In general, it will display the UI to filter a single table.
 * @name LABKEY.ext4.FilterSelectList
 * @cfg description If provided, this HTML will be displayed as a header above the list
 * @cfg store An ext store containing the records to display
 * @cfg fn A filter function that will be applied to the Ext store
 * @cfg labelField The name of the store field used to render the label for each item.  Defaults to 'label'
 * @cfg allowAll If true, a checkbox will be added to toggle selection across all items
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
            idField: 'id',
            bodyStyle: 'padding-bottom: 3px;'
        });

        this.addEvents('beforeInitGroupConfig', 'afterInitGroupConfig');
        this.addEvents('initSelectionComplete');

        this.items = [];

        if (this.store)
            this.items.push(this.getGridCfg());

        this.callParent();

        this.getGrid().on('viewready', this.initSelection, this, {single: true, delay: 50});
    },

    getGridCfg : function(isHeader) {

        var initToolTip = function(view) {
            var labelField = this.labelField;
            var _activeLabel;

            var renderTip = function(tip) {
                if (_activeLabel) {
                    tip.update('<b>' + Ext4.htmlEncode(_activeLabel) + '</b><br>Click the label to ' +
                            'select only this item. Click the checkbox to toggle this item and preserve other selections.');
                }
            };

            var loadRecord = function(tip) {
                var parentNode = tip.triggerElement.parentNode.parentNode;
                if (parentNode.className.indexOf("x4-grid-group-hd") > -1)
                {
                    _activeLabel = parentNode.innerText;
                }
                else
                {
                    var r = view.getRecord(parentNode);
                    _activeLabel = (r != null ? r.data[labelField] : null);
                }

                return _activeLabel != null;
            };

            view.tip = Ext4.create('Ext.tip.ToolTip', {
                target   : view.el,
                delegate : '.x4-grid-cell-inner',
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

        var selectionModel = Ext4.create('Ext.selection.CheckboxModel', {
            checkOnly : true,
            injectCheckbox : 1  // add checkbox after the spacer column (used for category indenting)
        });

        return {
            xtype       : 'grid',
            itemId      : 'selectGrid',
            gridId      : this.itemId,
            store       : this.store,
            border      : false, frame : false,
            bodyStyle   : 'border: none;',
            padding     : '0 0 0 1px',
            hideHeaders : true,
            multiSelect : true,
            columns     : this.getColumnCfg(isHeader),
            features    : this.getGroupingFeatureCfg(),
            viewConfig : {
                stripeRows : false,
                listeners  : {
                    render : initToolTip,
                    cellclick : function(cmp, td, idx, record, tr, rowIdx, e) {
                        // clicking on the grid label selects only that item
                        if (e.getTarget('.lk-filter-panel-label'))
                            this.updateCategorySingleSelection(record);
                        else if (e.getTarget('.x4-grid-row-checker'))
                        {
                            // clicking on the checkbox for a row, selects or deselects just that row
                            if (!this.getGrid().getSelectionModel().isSelected(record))
                                this.getGrid().getSelectionModel().select(record, true);
                            else
                                this.getGrid().getSelectionModel().deselect(record);
                        }

                        this.updateCategorySelectAll(record.get("categoryName"));
                    },
                    beforecellmousedown: function(view, cell, cellIdx, record, row, rowIdx, eOpts){
                        // disable "row" selection for this grid and defer to the cellclick handler above instead
                        return false;
                    },
                    groupclick : function(grid, field, value, e) {
                        var inputEl = this.getCategoryInputEl(value);
                        if (inputEl)
                        {
                            // switch the state of the checkbox for the category group header
                            this.checkGroupHeaderCheckbox(inputEl, !this.isGroupHeaderCheckbox(inputEl));

                            this.getCategoryRecords(value).each(function(rec) {
                                if (this.isGroupHeaderCheckbox(inputEl))
                                    grid.getSelectionModel().select(rec, true); // true to keepExisting selection of other category groups
                                else
                                    grid.getSelectionModel().deselect(rec);
                            }, this);
                        }

                        return false; // to prevent the collapse/expand for the grid grouping
                    },
                    scope  : this
                }
            },
            selModel    : selectionModel,
            bubbleEvents: ['select', 'selectionchange', 'cellclick', 'itemmouseenter', 'itemmouseleave'],
            scope       : this
        };
    },

    getGroupingFeatureCfg : function() {
        var me = this;
        return [Ext4.create('Ext4.grid.feature.Grouping', {
            groupHeaderTpl : [
                '{groupValue:this.formatValue}',
                {
                    formatValue: function(value)
                    {
                        var headerHtml = "<table><tr>";

                        // use the grouping header as the 'select all' option for this category
                        if (me.allowAll)
                            headerHtml += "<td><input type='button' class='category-header x4-form-checkbox' category='" + value + "' /></td>";

                        var classNames = me.allowAll ? "category-label-padding category-label" : "category-label";

                        var displayName = value;
                        var record = me.store.findRecord('categoryName', value);
                        if (record)
                        {
                            displayName = record.get("category") ? record.get("category").label : 'Cohorts';
                        }

                        return headerHtml
                            + "<td><div class='" + classNames + " lk-filter-panel-label" + (me.normalWrap ? " normalwrap-gridcell" : "") + "'>"
                            + Ext4.htmlEncode(displayName) + "</div></td></tr></table>";
                    }
                }
            ]
        })];
    },

    initSelection: function() {
        var target = this.getGrid();

        if(!target.store.getCount() || !target.getView().viewReady){
            this.initSelection.defer(10, this);
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
            target.getSelectionModel().deselectAll();
            for (var s=0; s < this.selection.length; s++)
            {
                // first try matching on cateogryId and record id
                var rec = target.getStore().getAt(target.getStore().findBy(function(record){
                    return record.get("categoryId") == this.selection[s].categoryId && record.get("id") == this.selection[s].id;
                }, this));

                // next try matching on categoryName and record label
                if (!rec)
                {
                    rec = target.getStore().getAt(target.getStore().findBy(function(record){
                        return record.get("categoryName") == this.selection[s].categoryName && record.get("label") == this.selection[s].label;
                    }, this));
                }

                // finally try to find a matching record by just the label
                if (!rec)
                {
                    var label = null;
                    if (this.selection[s].data && this.selection[s].data.label)
                        label = this.selection[s].data.label;
                    else if (this.selection[s].label)
                        label = this.selection[s].label;

                    if (label != null)
                        rec = target.getStore().findRecord('label', label);
                }

                if (rec)
                {
                    // Compare ID && Label if dealing with virtual groups (e.g. not in cohorts, etc)
                    if (this.selection[s].id < 0 && (rec.data.label != this.selection[s].label))
                        continue;

                    target.getSelectionModel().select(rec, true);
                }
            }

            this.updateCategorySelectAllCheckboxes(this.defaultSelectUncheckedCategory);
        }

        target.resumeEvents();

        // fire event to tell the panel the initial selection is compelete, return the number of selected records
        this.fireEvent('afterInitGroupConfig', target.getSelectionModel().getCount(), this);
    },

    getGrid: function(){
        return this.down('#selectGrid');
    },

    getColumnCfg : function(isHeader) {

        var field = '{'+this.labelField+':htmlEncode}';

        var tpl = [
            '<div><span ext:qtip="testing" class="' + (this.normalWrap ? 'lk-filter-panel-label normalwrap-gridcell' : 'lk-filter-panel-label') + '">',
            (isHeader) ? '<b class="filter-description">' + field + '</b>' : field,
            '</span></div>'
        ];

        return [{
            // spacer column, used for category indenting
            width     : 20,
            hidden    : !this.allowAll
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

        var selection = target.getSelectionModel().getSelection();

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

    select : function(id, stopEvents) {
        if (stopEvents)
            this.suspendEvents();

        var target = this.getGrid();
        var rec = target.getStore().findRecord('id', id);
        if (rec)
            target.getSelectionModel().select(rec, true);

        if (stopEvents)
            this.resumeEvents();
    },

    deselect : function(id, stopEvents) {

        if (stopEvents)
            this.suspendEvents();

        var target = this.getGrid();
        var rec = target.getStore().findRecord('id', id);
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
    getFilterArray: function(){
        return [];
    },

    /**
     * Used to determine if all of the records in the given grid are selected
     */
    isAllSelected: function() {
        var target = this.getGrid();
        return (target.getSelectionModel().getCount() == target.getStore().getCount());
    },

    /**
     * Used on click of group label to select a single record within a category
     */
    updateCategorySingleSelection : function(recordToSelect)
    {
        this.getCategoryRecords(recordToSelect.get("categoryName")).each(function(rec){
            if (rec == recordToSelect)
                this.getGrid().getSelectionModel().select(rec, true);
            else
                this.getGrid().getSelectionModel().deselect(rec);
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
            this.getCategoryRecords(value).each(function(rec){
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
                this.getCategoryRecords(value).each(function(rec){
                    this.getGrid().getSelectionModel().select(rec, true);
                }, this);
            }
            else
                this.checkGroupHeaderCheckbox(el, false);
        }
    },

    getCategoryRecords : function(value) {
        return this.getGrid().getStore().queryBy(function(rec, id){
            return rec.get("categoryName") == value;
        });
    },

    isGroupHeaderCheckbox : function(el) {
        return el.className.indexOf("category-checked") > -1;
    },

    checkGroupHeaderCheckbox : function(el, check) {
        if (check)
            el.setAttribute("class", "category-header x4-form-checkbox category-checked");
        else
            el.setAttribute("class", "category-header x4-form-checkbox");
    },

    getCategoryInputEl : function(value) {
        // query for the category header input using the root of the query as the current dom element
        var elArr = Ext4.query('input.category-header[category=' + value + ']', this.getEl().dom);
        return elArr.length == 1 ? elArr[0] : null;
    },

    updateCategorySelectAllCheckboxes : function(selectEmptyCategory) {
        Ext4.each(this.getGrid().getStore().collect("categoryName"), function(category){
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

            this.getGrid().getSelectionModel().selectAll();
            this.updateCategorySelectAllCheckboxes(false);

            if (stopEvents)
                this.resumeEvents();
        }
    }
});

/**
 * The basic unit of a filter panel.  Can contain one or more FilterSelectLists.
 * @name LABKEY.ext4.filter.SelectPanel
 * @cfg sections Array of config objects for LABKEY.ext4.filter.SelectList
 */
Ext4.define('LABKEY.ext4.filter.SelectPanel', {

    extend : 'Ext.panel.Panel',
    alias: 'widget.labkey-filterselectpanel',


    bubbleEvents : ['select', 'selectionchange', 'cellclick', 'itemmouseenter', 'itemmouseleave', 'initSelectionComplete', 'beginInitSelection'],

    constructor : function(config) {
        Ext4.applyIf(config, {
            border : false, frame : false,
            cls    : 'rpf',
            scroll   : 'vertical'
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
                    tpl       : '<div><b class="lk-filter-panel-label filter-description">{label:htmlEncode}</b></div>'
                }],
                selType     : 'checkboxmodel',
                bubbleEvents: [],
                store  : Ext4.create('Ext.data.Store',{
                    fields : ['id', 'label'],
                    data   : [{id: -1, label : 'All'}]
                }),
                listeners: {
                    selectionchange: function(model, selected) {
                        !selected.length ? this.deselectAll() : this.selectAll();
                    },
                    scope: this
                },
                scope       : this
            });

            this.on('selectionchange', function(){
                this.allSelected();
                return true;
            }, this, {stopPropogation: false});
        }

        for (var f=0; f < this.sections.length; f++) {
            filterPanels.push(Ext4.apply(this.sections[f],{
                xtype : 'labkey-filterselectlist',
                allowAll : this.allowAll,
                border : false,
                frame : false
            }));
        }

        if (!filterPanels.length)
        {
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

        return {
            border : false, frame : false,
            items  : filterPanels
        };
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
                    if (select && select.length > 0)
                        selections.push(select);
                    else {
                        selections.push([]);
                    }
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
    getFilterPanels: function(){
        var panels = this.query('labkey-filterselectlist[hidden=false]');
        var filterPanels = [];
        for (var i=0;i<panels.length;i++)
            filterPanels.push(panels[i]);

        return filterPanels;
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
        var filterPanels = this.getFilterPanels();
        for (var i=0; i < filterPanels.length; i++) {
            filterPanels[i].deselectAll(stopEvents);
        }
    },

    selectAll : function(stopEvents) {
        var filterPanels = this.getFilterPanels(stopEvents);
        for (var i=0; i < filterPanels.length; i++) {
            filterPanels[i].selectAll(stopEvents);
        }
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
                if (this.allowGlobalAll)
                    this.getGlobalSelectAllToggle().getSelectionModel().deselect(0, true);

                return false;
            }
        }
        if (this.allowGlobalAll)
            this.getGlobalSelectAllToggle().getSelectionModel().select(0, true);

        return true;
    },

    getGlobalSelectAllToggle: function(){
        return this.down('#globalSelectAll');
    },

    handleBeforeInitGroupConfig : function() {

        if (!this.panelsToInit){
            this.fireEvent('beginInitSelection', this);

            this.panelsToInit = [];
            this.panelSelectCount = 0;
            var filterPanels = this.getFilterPanels();
            for (var i=0; i < filterPanels.length; i++)
                this.panelsToInit.push(filterPanels[i].id);
        }
    },

    handleAfterInitGroupConfig : function(count, cmp) {

        if (this.panelsToInit){
            this.panelsToInit.remove(cmp.id);
            this.panelSelectCount += count;
            if (this.panelsToInit.length == 0){
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
            maxHeight     : 500,
            collapsible   : true,
            collapsed     : true,
            expandOnShow  : true,
            titleCollapse : true,
            draggable     : false,
            cls           : 'report-filter-window',
            title         : 'Filter Report',
            alignConfig   : {
                position : 'tl-tr',
                offsets  : [-300, 27]
            }
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