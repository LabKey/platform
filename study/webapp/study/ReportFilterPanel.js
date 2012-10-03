/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.requiresExt4Sandbox(true);
LABKEY.requiresCss("study/DataViewsPanel.css");
Ext4.ns('LABKEY.ext4.filter');

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

    statics : {
        groupSelCache : {} // 15505
    },

    initComponent : function() {
        Ext4.applyIf(this, {
            labelField: 'label',
            idField: 'id',
            bodyStyle: 'padding-bottom: 10px;'
        });

        this.addEvents('beforeInitGroupConfig', 'afterInitGroupConfig');
        this.registerSelectionCache(this.sectionName);
        this.addEvents('initSelectionComplete');

        this.items = [];

        if (this.allowAll) {
            var cfg = this.getGridCfg(true);
            cfg.padding = undefined;
            Ext4.apply(cfg, {
                itemId: 'selectAllToggle',
                bubbleEvents: [],
                store  : Ext4.create('Ext.data.Store',{
                    fields : ['id', 'label'],
                    data   : [{id: -1, label : this.description}]
                }),
                listeners: {
                    selectionchange: function(model, selected) {
                        !selected.length ? this.deselectAll() : this.selectAll();
                    },
                    scope: this
                },
                select: this.select,
                deselect: this.deselect,
                getGrid: function(){return this;}
            });

            this.items.push(cfg);

            this.on('selectionchange', function() {
                this.allSelected();
                return true;
            }, this, {stopPropogation: false});
        }
        else {
            if (this.description) {
                this.items.push({
                    xtype : 'box',
                    autoEl: {
                        tag : 'div',
                        html: '<b class="filter-description">' + this.description + '</b>'
                    }
                });
            }
        }

        if (this.store) {

            this.mon(this.store, 'load', this.allSelected, this, {single: true});

            this.items.push(this.getGridCfg());
        }

        this.callParent();

        if(this.store){
            //perhaps should be {single: true}?
            this.mon(this.store, 'load', this.onStoreLoad, this);
        }

        //account for situation where store is already loaded
        if(!this.rendered){
            this.getGrid().on('afterrender', this.initSelection, this, {single: true, delay: 50});
        }
        else {
            this.initSelection();
        }
    },

    getGridCfg : function(isHeader) {
        function initToolTip(view) {
            var labelField = this.labelField;
            var _active;

            function renderTip(tip) {
                if (_active)
                    tip.update('<b>' + Ext4.util.Format.htmlEncode(_active.data[labelField]) + '</b><br>Click the label to ' +
                            'select only this item. Click the checkbox to toggle this item and preserve other selections.');
            }

            function loadRecord(tip) {
                var r = view.getRecord(tip.triggerElement.parentNode);
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
                delegate : '.x4-label-column-cell',
                showDelay: 1000,
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
        }

        var selectionModel = Ext4.create('Ext.selection.CheckboxModel', {checkOnly : true});

        return {
            xtype       : 'grid',
            itemId      : 'selectGrid',
            gridId      : this.itemId,
            store       : this.store,
            border      : false, frame : false,
            bodyStyle   : 'border: none;',
            padding     : '0 0 0 21px',
            hideHeaders : true,
            multiSelect : true,
            columns     : this.getColumnCfg(isHeader),
            viewConfig : {
                stripeRows : false,
                listeners  : {
                    render : initToolTip,
                    cellclick : function(cmp, td, idx, record, tr, rowIdx, e) {
                        var checker = e.getTarget('.lk-filter-panel-label');

                        // clicking on the grid label selects only that item
                        if (checker)
                            selectionModel.select(rowIdx);
                    },
                    scope  : this
                }
            },
            selModel    : selectionModel,
            bubbleEvents: ['select', 'selectionchange', 'cellclick', 'itemmouseenter', 'itemmouseleave'],
            scope       : this
        };
    },

    onStoreLoad: function(){
        // allows for proper selection even if render is delayed
        var target = this.getGrid();
        if (target) {
            if (target.rendered) {
                this.initSelection();
            }
            else {
                target.on('afterrender', this.initSelection, this, {single: true});
            }
        }
    },

    registerSelectionCache : function(key, value) {
        if (!LABKEY.ext4.filter.SelectList.groupSelCache[key]) {
            LABKEY.ext4.filter.SelectList.groupSelCache[key] = value ? value : [];
        }
        else if (value) {
            LABKEY.ext4.filter.SelectList.groupSelCache[key] = value;
        }
    },

    inSelectionCache : function(key) {
        return LABKEY.ext4.filter.SelectList.groupSelCache[key];
    },

    initSelection: function() {
        var target = this.getGrid();

        if(!target.store.getCount() || !target.getView().viewReady){
            this.initSelection.defer(10, this);
            return;
        }

        var cachedSelection = this.inSelectionCache(this.sectionName);
        if (cachedSelection && cachedSelection.length > 0) {
            this.selection = cachedSelection;
        }

        // if there is not a default number of selection to make initially, set it to select all
        if (!this.maxInitSelection)
            this.maxInitSelection = target.store.getCount();

        this.fireEvent('beforeInitGroupConfig', this, target.store);

        target.suspendEvents(); // queueing of events id depended on
        if (!this.noSelection) {

            if (!this.selection) {
                if (this.maxInitSelection >= target.store.getCount()) {
                    target.getSelectionModel().selectAll();
                    if(this.allowAll){
                        this.getSelectAllToogle().select(-1, true);
                    }
                }
                else {
                    for (var i = 0; i < this.maxInitSelection; i++)
                        target.getSelectionModel().select(i, true);
                }
            }
            else {
                target.getSelectionModel().deselectAll();
                for (var s=0; s < this.selection.length; s++) {
                    var rec = target.getStore().findRecord('id', this.selection[s].id);

                    // if no matching record by id, try to find a matching record by label (just for initial selection)
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
                this.registerSelectionCache(this.sectionName, []); // clear cache
            }
        }
        this.allSelected();
        target.resumeEvents();

        // fire event to tell the panel the initial selection is compelete, return the number of selected records
        this.fireEvent('afterInitGroupConfig', target.getSelectionModel().getCount(), this);
    },

    getGrid: function(){
        return this.down('#selectGrid');
    },

    getColumnCfg : function(isHeader) {
        var tpl = '<div><span ' + (this.normalWrap ? ' class="lk-filter-panel-label normalwrap-gridcell"' : 'class="lk-filter-panel-label"') + '>{'+this.labelField+':htmlEncode}</span></div>';

        if (isHeader)
            tpl =  '<div><span ' + (this.normalWrap ? ' class="lk-filter-panel-label normalwrap-gridcell"' : 'class="lk-filter-panel-label"') + '><b class="filter-description">{'+this.labelField+':htmlEncode}</b></span></div>';

        return [{
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
        //if all are checked, this is treated the same as none checked
        if(skipIfAllSelected && this.allSelected())
            return;

        var target = this.getGrid();
        if (target)
            return target.getSelectionModel().getSelection();
        // return undefined -- same as getSelection()
    },

    isSelectionEmpty : function() {

        var target = this.getGrid();
        if (target)
        {
            var selections = target.getSelectionModel().getSelection();
            return selections && selections.length == 0;
        }

        return true;
    },

    select : function(id, stopEvents) {
        if (stopEvents)
            this.suspendEvents();

        var target = this.getGrid();
        var rec = target.getStore().findRecord('id', id);
        if (rec)
            target.getSelectionModel().select(rec);

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
     * Used to update the state of the 'toogle all' checkbox whenever selection changes
     */
    allSelected : function() {
        var target = this.getGrid();
        if (target.getSelectionModel().getCount() != target.getStore().getCount()) {
            if (this.allowAll) {
                this.getSelectAllToogle().deselect(-1, true);
            }

            this.registerSelectionCache(this.sectionName, target.getSelectionModel().getSelection());
            return false;
        }

        if (this.allowAll) {
            this.getSelectAllToogle().select(-1, true);
        }
        return true;
    },

    getSelectAllToogle: function(){
        return this.down('#selectAllToggle');
    },

    deselectAll : function() {
        if(!this.getGrid().getView().viewReady)
            this.deselectAll.defer(100, this);
        else
            this.getGrid().getSelectionModel().deselectAll();
    },

    selectAll : function() {
        if(!this.getGrid().getView().viewReady)
            this.selectAll.defer(100, this);
        else
            this.getGrid().getSelectionModel().selectAll();
    }
});

/**
 * The basic unit of a filter panel.  Can contain one or more FilterSelectLists.
 * @name LABKEY.ext4.filter.SelectListPanel
 * @cfg sections Array of config objects for LABKEY.ext4.filter.SelectList
 */
Ext4.define('LABKEY.ext4.filter.SelectPanel', {

    extend : 'Ext.panel.Panel',
    alias: 'widget.labkey-filterselectpanel',


    bubbleEvents : ['select', 'selectionchange', 'cellclick', 'itemmouseenter', 'itemmouseleave', 'initSelectionComplete', 'beginInitSelection'],

    constructor : function(config) {
        Ext4.applyIf(config, {
            border : false, frame : false,
            cls    : 'report-filter-panel',
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
                    tpl       : '<div><b class="filter-description">{label:htmlEncode}</b></div>'
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
        var panels = this.query('labkey-filterselectlist');
        var filterPanels = [];
        for (var i=0;i<panels.length;i++){
            if(panels[i].itemId != 'selectAllToggle')
                filterPanels.push(panels[i]);
        }
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

    deselectAll : function() {
        var filterPanels = this.getFilterPanels();
        for (var i=0; i < filterPanels.length; i++) {
            filterPanels[i].deselectAll();
        }
    },

    selectAll : function() {
        var filterPanels = this.getFilterPanels();
        for (var i=0; i < filterPanels.length; i++) {
            filterPanels[i].selectAll();
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
        this.fireEvent('initSelectionComplete', count);
    },

    allSelected : function() {
        var filterPanels = this.getFilterPanels();
        for (var i=0; i < filterPanels.length; i++) {
            if(!filterPanels[i].allSelected())
            {
                if (this.allowGlobalAll)
                    this.getSelectAllToogle().getSelectionModel().deselect(0, true);
                return false;
            }
        }
        if (this.allowGlobalAll)
            this.getSelectAllToogle().getSelectionModel().select(0, true);
        return true;
    },

    getSelectAllToogle: function(){
        return this.down('#globalSelectAll');
    },

    getNoSelectionSections : function() {
        var filterPanels = this.getFilterPanels();
        var empty = [];
        for (var i=0; i < filterPanels.length; i++) {
            if(filterPanels[i].isSelectionEmpty())
                empty.push(filterPanels[i].sectionName);
        }
        return empty;
    },

    handleBeforeInitGroupConfig : function() {

        if (!this.panelsToInit){

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

                this.fireEvent('initSelectionComplete', this.panelSelectCount);
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