/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.dataregion.panel.Facet', {

    extend : 'Ext.Panel',

    width : 260,

    statics : {
        LOADED : true
    },

    constructor : function(config) {

        if (!config.dataRegion) {
            console.error('A DataRegion object must be provided for Faceted Search.');
            return;
        }

        this.dataRegion = config.dataRegion;
        this.dataRegion.async = true;
        var renderTarget = config.dataRegion.domId + '-facet';
        var topEl = this.getContainerEl(config.dataRegion);
        var tableEl = this.getDataRegionTableEl(config.dataRegion);
        tableEl.setWidth(tableEl.getBox().width);
        if (topEl) {
            var targetHTML = '<div id="' + renderTarget + '" style="float: left;" lk-region-facet-name="' + config.dataRegion.name + '"></div>';
            topEl.insertHtml('beforeBegin', targetHTML);
        }

        Ext4.applyIf(config, {
            renderTo : renderTarget,
            collapsed : true,
            collapsible : true,
            collapseDirection : 'left',
            hidden : true,
            collapseMode : 'mini',
            frameHeader : false,
            regionName : config.dataRegion.name,
            style : { paddingRight : '5px' },
            autoScroll : true,
            bodyStyle : 'overflow-x: hidden !important;',
            header : {
                xtype : 'header',
                title : 'Filter',
                cls : 'facet_header'
            },
            cls : 'labkey-data-region-facet',
            height : tableEl.getBox().height,
            minHeight : 450
        });

        var studyCtx = LABKEY.getModuleContext('study');
        this.SUBJECT_PREFIX = studyCtx.subject.columnName + '/';
        this.COHORT_PREFIX  = studyCtx.subject.columnName + '/Cohort/Label';

        this.resizeTask = new Ext4.util.DelayedTask(this._resizeTask, this);

        this.callParent([config]);
    },

    initComponent : function() {

        this.items = [];

        // issue 18708: multiple facet filter changes may fire while QWP is loading
        this.filterChangeCounter = 0;

        this.callParent(arguments);

        var task = new Ext4.util.DelayedTask(function() {
            this.add(this.getFilterCfg());
        }, this);

        this.on('afterrender', function() {
            this.getDataRegion();
            task.delay(200); // animation time
        }, this, {single: true});

        this.on('beforeexpand', function() {
            this._beforeShow();
            this.show();
        }, this);
        this.on('collapse', function() {
            this.hide();
            this._afterHide();
        }, this);

        // Attach resize event listeners
        this.on('resize', this.onResize, this);
        Ext4.EventManager.onWindowResize(this._beforeShow, this);
    },

    getContainerEl : function(dr) {
        if (dr && dr.name) {
            var el = Ext4.get(dr.domId + '-form');
            if (el) {
                return el.up('div');
            }
        }
    },

    getDataRegionTableEl : function(dr) {
        if (dr && dr.name) {
            return Ext4.get(dr.domId);
        }
    },

    _beforeShow : function() {
        var region = this.getDataRegion();
        var el = this.getContainerEl(region);
        if (el) {
            var reqWidth = this.getRequiredWidth(region);
            if (el.getBox().width < reqWidth) {
                el.setWidth(reqWidth);
            }
        }
    },

    _afterHide : function() {
        var el = this.getContainerEl(this.getDataRegion());
        if (el) { el.setWidth(null); }
    },

    getRequiredWidth : function(dr) {
        return this.width + 10 + this.getDataRegionTableEl(dr).getBox().width;
    },

    getDataRegion : function() {

        var region = LABKEY.DataRegions[this.regionName];

        if (region && !this._isRegionBound) {
            this._isRegionBound = true;
            region.on('success', this.onRegionSuccess, this);
        }

        return region;
    },

    onRegionSuccess : function(dr) {
        // Give access to to this filter panel to the Data Region
        if (dr) {
            var tableEl = this.getDataRegionTableEl(dr);
            if (tableEl) {
                tableEl.setWidth(tableEl.getBox().width);
            }

            var box = this.getBox();
            this._resizeTask(this, box.width, box.height);

            // Filters might have been updated outside of facet
            if (this.filterChangeCounter == 0 && this.filterPanel) {
                if (dr.getUserFilterArray().length === 0) {
                    this.filterPanel.getFilterPanel().selectAll(true);
                }
                else {
                    this.filterPanel.getFilterPanel().initSelection();
                }
            }
            else {
                this.filterChangeCounter--;
            }
        }
    },

    getFilterCfg : function() {

        return {
           xtype: 'participantfilter',
           width: this.width,
           layout: 'fit',
           bodyStyle: 'padding: 8px;',
           normalWrap: true,
           overCls: 'iScroll',

           // Filter specific config
           filterType: 'group',
           subjectNoun: this.subjectNoun,
           defaultSelectUncheckedCategory : true,

           listeners: {
               afterrender: function(p) { this.filterPanel = p; },
               selectionchange: this.onFilterChange,
               beforeInitGroupConfig: this.applyFilters,
               buffer: 1000,
               scope: this
           },

           scope : this
        };
    },

    onResize : function(panel, w, h, oldW, oldH) {
        this.resizeTask.delay(100, null, null, arguments);
    },

    // DO NOT CALL DIRECTLY. Use resizeTask.delay
    _resizeTask : function(panel, w, h) {

        // Resize data region wrapper
        var wrap = this.getDataRegionTableEl(this.getDataRegion());

        if (wrap)
            wrap = wrap.parent('div.labkey-data-region-wrap');

        if (wrap) {
            var box = wrap.getBox();

            // Filter Panel taller than Data Region
            if (h > box.height) {
                wrap.setHeight(h);
            }

            // Filter Panel has been closed -- clear any specific height setting on wrapper
            if (w <= 1) {
                wrap.setHeight(null);
            }
        }
        this._beforeShow();
    },

    onFilterChange : function() {
        // Current all being selected === none being selected
        var filters = this.filterPanel.getSelection(true, true),
            filterMap = {},
            filterPrefix, f=0;

        for (; f < filters.length; f++) {
            if (filters[f].get('type') != 'participant') {

                // Build what a filter might look like
                if (filters[f].data.category) {
                    filterPrefix = this.SUBJECT_PREFIX + filters[f].data.category.label;
                }
                else {
                    // Assume it is a cohort
                    filterPrefix = this.COHORT_PREFIX;
                }

                if (!filterMap[filterPrefix]) {
                    filterMap[filterPrefix] = [];
                }
                filterMap[filterPrefix].push(filters[f].data.label);
            }
        }

        filters = [];
        Ext4.iterate(filterMap, function(column, values) {
            var filter;
            if (values.length > 1) {
                filter = LABKEY.Filter.create(column, values.join(';'), LABKEY.Filter.Types.IN);
            }
            else {
                filter = LABKEY.Filter.create(column, values);
            }
            filters.push(filter);
        });

        this.filterChangeCounter++;
        this.getDataRegion().replaceFilters(filters, [this.SUBJECT_PREFIX, this.COHORT_PREFIX]);
    },

    // This will get called for each separate group in the Filter Display
    applyFilters : function(fp, store) {
        if (store && store.getCount() > 0) {
            var userFilters = this.getDataRegion().getUserFilterArray();
            if (userFilters && userFilters.length > 0) {

                var uf, selection = [], rec, u, s;
                for (u=0; u < userFilters.length; u++) {

                    uf = userFilters[u];

                    for (s=0; s < store.getRange().length; s++) {
                        rec = store.getAt(s);

                        if (rec.data.label.toLowerCase() == uf.getValue().toLowerCase()) {

                            // Check Cohorts
                            if ((!rec.data.category || rec.data.category == '') && rec.data.type.toLowerCase() == 'cohort') {
                                selection.push(rec);
                            }
                            else if (rec.data.category && rec.data.category.label) {

                                // Check Participant Groups
                                var groupName = uf.getColumnName().split('/');
                                groupName = groupName[groupName.length-1];
                                if (rec.data.category.label.toLowerCase() == groupName.toLowerCase()) {
                                    selection.push(rec);
                                }
                            }
                        }
                    }

                }

                if (selection.length > 0) {
                    fp.selection = selection;
                }
            }
        }
    }
});