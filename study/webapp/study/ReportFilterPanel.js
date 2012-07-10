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
 */
Ext4.define('LABKEY.ext4.filter.SelectList', {

    extend : 'Ext.panel.Panel',
    alias: 'widget.labkey-filterselectlist',

    border : false,
    frame  : false,
    bubbleEvents : ['select', 'selectionchange', 'itemmouseenter', 'itemmouseleave', 'initSelectionComplete'],

    initComponent : function() {
        Ext4.applyIf(this, {
            labelField: 'label',
            idField: 'id',
            bodyStyle: 'padding-bottom: 10px;'
        });

        this.addEvents('initSelectionComplete');

        this.items = [];

        if (this.description) {
            this.items.push({
                xtype : 'box',
                autoEl: {
                    tag : 'div',
                    html: this.description
                }
            });
        }

        if (this.allowAll) {
            var cfg = this.getGridCfg();
            Ext4.apply(cfg, {
                itemId: 'selectAllToggle',
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
                select: this.select,
                deselect: this.deselect,
                getGrid: function(){return this;}
            });
            cfg.height = 22;
            cfg.width = 75;
            this.items.push(cfg);

            this.on('selectionchange', function(){
                this.allSelected();
                return true;
            }, this, {stopPropogation: false});
        }

        if (this.store) {
            //TODO: investigate how this is used
            if (this.fn)
                this.store.filterBy(this.fn);

            this.mon(this.store, 'load', this.allSelected, this, {single: true});

            this.items.push(this.getGridCfg());
        }

        this.callParent([arguments]);

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

    getGridCfg : function() {
        function initToolTip(view) {
            var labelField = this.labelField;
            var _active;

            function renderTip(tip) {
                if (_active)
                    tip.update(Ext4.util.Format.htmlEncode(_active.data[labelField]));
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

        return {
            xtype       : 'grid',
            itemId      : 'selectGrid',
            gridId      : this.itemId,
            store       : this.store,
            border      : false, frame : false,
            bodyStyle   : 'border: none;',
            layout      : 'fit',
            hideHeaders : true,
            multiSelect : true,
            columns     : this.getColumnCfg(),
            viewConfig : {
                stripeRows : false,
                listeners  : {
                    render : initToolTip,
                    scope  : this
                }
            },
            selType     : 'checkboxmodel',
            bubbleEvents: ['select', 'selectionchange', 'itemmouseenter', 'itemmouseleave'],
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

    initSelection: function() {
        var target = this.getGrid();

        if(!target.store.getCount() || !target.getView().viewReady){
            this.initSelection.defer(10, this);
            return;
        }

        // if there is not a default number of selection to make initially, set it to select all
        if (!this.maxInitSelection)
            this.maxInitSelection = target.store.getCount();

        target.suspendEvents(); // queueing of events id depended on
        if (!this.selection || !this.selection.length) {
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
                if (!rec && this.selection[s].label)
                    rec = target.getStore().findRecord('label', this.selection[s].label);

                if (rec)
                {
                    // Compare ID && Label if dealing with virtual groups (e.g. not in cohorts, etc)
                    if (this.selection[s].id < 0 && (rec.data.label != this.selection[s].label))
                        continue;
                    target.getSelectionModel().select(rec, true);
                }
            }
        }

        target.resumeEvents();

        // fire event to tell the panel the initial selection is compelete, return the number of selected records
        this.fireEvent('initSelectionComplete', target.getSelectionModel().getCount());
    },

    getGrid: function(){
        return this.down('#selectGrid');
    },

    getColumnCfg : function() {
        return [{
            xtype     : 'templatecolumn',
            flex      : 1,
            dataIndex : this.labelField,
            tdCls     : 'x4-label-column-cell',
            tpl       : '{'+this.labelField+':htmlEncode}',
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

    bubbleEvents : ['select', 'selectionchange', 'itemmouseenter', 'itemmouseleave', 'initSelectionComplete'],

    constructor : function(config) {
        Ext4.applyIf(config, {
            border : false, frame : false,
            cls    : 'report-filter-panel',
            scroll   : 'vertical'
        });

        this.callParent([config]);
    },

    initComponent : function() {
        this.items = [this.initSelectionPanel()];
        this.callParent([arguments]);
    },

    initSelectionPanel : function() {
        var filterPanels = [];

        for (var f=0; f < this.sections.length; f++) {
            filterPanels.push(Ext4.apply(this.sections[f],{
                xtype : 'labkey-filterselectlist',
                allowAll : true,
                border : false,
                frame : false
            }));
        }

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
        var filterPanels = this.getFilterPanels();
        for (var i=0; i < filterPanels.length; i++) {
            filterPanels[i].initSelection();
        }
    },

    allSelected : function() {
        var filterPanels = this.getFilterPanels();
        for (var i=0; i < filterPanels.length; i++) {
            if(!filterPanels[i].allSelected())
                return false;
        }
        return true;
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

        if (this.relative)
            this.relative.on('resize', this.calculatePosition, this);
        this.on('show', this.calculatePosition, this);

        this.callParent([arguments]);
    },

    calculatePosition : function() {
        if (!this.relative) {
            console.warn('unable to show ReportFilter due to relative component not being provided.');
            this.hide();
            return;
        }

        // elements topleft to targets topright
        if (this.el)
            this.alignTo(this.relative, this.alignConfig.position, this.alignConfig.offsets);
    }
});