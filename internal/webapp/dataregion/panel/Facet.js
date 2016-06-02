/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.dataregion.panel.Facet', {

    extend : 'Ext.Panel',

    width : 260,

    statics : {
        LOADED : true,

        display : function(dataRegion) {
            return Ext4.create('LABKEY.dataregion.panel.Facet', {
                dataRegion: dataRegion
            });
        }
    },

    collapsed : true,
    collapsible : true,
    collapseDirection : 'left',
    hidden : true,
    collapseMode : 'mini',
    frameHeader : false,
    autoScroll : true,
    bodyStyle : 'overflow-x: hidden !important;',
    cls : 'labkey-data-region-facet',
    minHeight : 450,
    style: 'padding-right: 5px;',

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

        Ext4.apply(config, {
            renderTo: renderTarget,
            regionName: config.dataRegion.name,
            height: tableEl.getBox().height
        });

        Ext4.applyIf(config, {
            header: {
                xtype: 'header',
                title: 'Filter',
                cls: 'facet_header'
            }
        });

        var studyCtx = LABKEY.getModuleContext('study');
        this.SUBJECT_PREFIX = studyCtx.subject.columnName + '/';
        this.COHORT_PREFIX  = studyCtx.subject.columnName + '/Cohort/Label';

        this.callParent([config]);

        this.resizeTask = new Ext4.util.DelayedTask(this._resizeTask, this);
    },

    initComponent : function() {

        this.items = [];

        // issue 18708: multiple facet filter changes may fire while QWP is loading
        this.filterChangeCounter = 0;

        this.callParent(arguments);

        // bind the data region and add the filter panel. This is done
        // after render to allow for animations to not interfere with layout.
        this.on('afterrender', function() {
            this._bindDataRegion();
            Ext4.defer(function() {
                this.add({
                    xtype: 'participantfilter',
                    itemId: 'filterPanel',
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
                        selectionchange: {
                            fn: this.onSelectionChange.bind(this),
                            buffer: 750
                        },
                        beforeInitGroupConfig: this.onBeforeInitGroupConfig.bind(this)
                    },

                    scope : this
                });
            }, 225, this); // animation time
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
        this.on('resize', function() { this.resizeTask.delay(100, null, null, arguments); }, this);
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
        if (el) {
            el.setWidth(null);
        }
    },

    _bindDataRegion : function() {
        var dr = this.getDataRegion();

        if (dr && !this._isRegionBound) {
            this._isRegionBound = true;
            dr.on('render', this.onRegionRender, this);
        }
    },

    getRequiredWidth : function(dr) {
        return this.width + 10 + this.getDataRegionTableEl(dr).getBox().width;
    },

    getDataRegion : function() {
        return LABKEY.DataRegions[this.regionName];
    },

    /**
     * Called when the Data Region 'render' event fires.
     * @param dr
     */
    onRegionRender : function(dr) {
        // Give access to to this filter panel to the Data Region
        if (dr) {
            var tableEl = this.getDataRegionTableEl(dr);
            if (tableEl) {
                tableEl.setWidth(tableEl.getBox().width);
            }

            var box = this.getBox();
            this._resizeTask(this, box.width, box.height);

            // Filters might have been updated outside of facet
            var filterPanel = this.getComponent('filterPanel');
            if (this.filterChangeCounter == 0 && filterPanel) {
                if (dr.getUserFilterArray().length === 0) {
                    filterPanel.getFilterPanel().selectAll(true /* stopEvents */);
                }
                else {
                    filterPanel.getFilterPanel().initSelection();
                }
            }
            else {
                this.filterChangeCounter--;
            }
        }
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

    /**
     * Event listener for the 'selectionchange' event on the
     */
    onSelectionChange : function() {
        // Current all being selected === none being selected
        var filterPanel = this.getComponent('filterPanel'),
            filters = [],
            filterMap = {};

        Ext4.each(filterPanel.getSelection(true /* collapsed */, true /* skipIfAllSelected */), function(filter) {
            if (filter.get('type') != 'participant') {

                var filterPrefix;

                // Build what a filter might look like
                if (filter.data.category) {
                    filterPrefix = this.SUBJECT_PREFIX + filter.data.category.label;
                }
                else {
                    // Assume it is a cohort
                    filterPrefix = this.COHORT_PREFIX;
                }

                // Not in any cohort/group
                // NOTE: This filter is exclusively outside the set of any other value filters for this
                // cohort/group as we cannot express IN=1;NULL;2;3. Rather IN=1;2;3 AND ISBLANK is created
                // which is not logically the same. We cannot express ORs explicitly outside of an IN clause.
                if (filter.get('id') === -1) {
                    filters.push(LABKEY.Filter.create(filterPrefix, undefined, LABKEY.Filter.Types.MISSING));
                    return;
                }

                if (!filterMap[filterPrefix]) {
                    filterMap[filterPrefix] = [];
                }
                filterMap[filterPrefix].push(filter.get('label'));
            }
        }, this);

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

    /**
     * How data region filters get reflected back into the filter panel.
     * This is called during the initSelection() of a LABKEY.ext4.filter.SelectPanel giving listeners a
     * chance to modify which records are selected. This could likely use a refactor but it
     * is how the LABKEY.ext4.filter.SelectPanel operates.
     */
    onBeforeInitGroupConfig : function(fp, store) {

        var regionFilters = this.getDataRegion().getUserFilterArray();

        if (store && store.getCount() > 0) {

            var selections = [];
            var groupFilters = [];
            var cohortFilters = [];
            var cohortFieldKey = this.COHORT_PREFIX.toLowerCase();
            var groupFieldKey = this.SUBJECT_PREFIX.toLowerCase();

            Ext4.each(regionFilters, function(filter) {
                var fieldKey = filter.getColumnName().toLowerCase();
                if (fieldKey.indexOf(cohortFieldKey) === 0) {
                    cohortFilters.push(filter);
                }
                else if (fieldKey.indexOf(groupFieldKey) === 0) {
                    groupFilters.push(filter);
                }
            });

            // process cohort filters
            Ext4.each(cohortFilters, function(filter) {
                var values = filter.getValue().toLowerCase().split(';'); // EQ or IN
                for (var s=0; s < store.getRange().length; s++) {
                    var rec = store.getAt(s);
                    if (values.indexOf(rec.get('label').toLowerCase()) > -1) {
                        selections.push(rec);
                    }
                }
            });

            // process group filters
            Ext4.each(groupFilters, function(filter) {
                var grpCategory = filter.getColumnName().split('/');
                grpCategory = grpCategory[grpCategory.length-1].toLowerCase();

                var values = filter.getValue().toLowerCase().split(';'); // EQ or IN
                for (var s=0; s < store.getRange().length; s++) {
                    var rec = store.getAt(s);
                    var category = rec.get('category');

                    if (Ext4.isObject(category) && category.label.toLowerCase() === grpCategory) {
                        if (values.indexOf(rec.get('label') && rec.get('label').toLowerCase()) > -1) {
                            selections.push(rec);
                        }
                    }
                }
            });

            if (selections.length > 0) {
                fp.selection = selections;
            }
        }
    }
});