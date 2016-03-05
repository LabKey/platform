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
            this.getWrappedDataRegion();
            task.delay(200); // animation time
        }, this, {single: true});

        this.on('beforeexpand', function() {
            this._beforeShow(); this.show();
        }, this);
        this.on('collapse', function() {
            this.hide(); this._afterHide();
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
        var el = this.getContainerEl(this.dataRegion);
        if (el) {
            var reqWidth = this.getRequiredWidth(this.dataRegion);
            if (el.getBox().width < reqWidth) {
                el.setWidth(reqWidth);
            }
        }
    },

    _afterHide : function() {
        var el = this.getContainerEl(this.dataRegion);
        if (el) { el.setWidth(null); }
    },

    getRequiredWidth : function(dr) {
        return this.width + 10 + this.getDataRegionTableEl(dr).getBox().width;
    },

    getWrappedDataRegion : function() {
        if (!this.qwp) {

            if (this.dataRegion.getQWP()) {
                this.qwp = this.dataRegion.getQWP();
                this.qwp.updateRenderElement(this.dataRegion);
                this.qwp.on('success', this.onQueryWebPartSuccess, this);
            }
            else {
                // Wrap the corresponding Data Region with a QWP
                this.qwp = new LABKEY.QueryWebPart({
                    dataRegion : this.dataRegion,
                    success : this.onQueryWebPartSuccess,
                    scope : this
                });
            }

            this.qwp.on('beforeclearallfilters', function(dr) {
                this.filterPanel.getFilterPanel().selectAll(true);
            }, this);
            this.qwp.on('beforerefresh', function(dr) {
                this.remove(0);
                this.filterPanel = undefined;
                this.add(this.getFilterCfg());
            }, this);
        }

        return this.qwp;
    },

    onQueryWebPartSuccess : function(dr) {
        // Give access to to this filter panel to the Data Region
        if (dr) {
            this.dataRegion = LABKEY.DataRegions[dr.name];
            LABKEY.DataRegions[dr.name].setFacet(this);

            var tableEl = this.getDataRegionTableEl(this.dataRegion);
            if (tableEl) {
                tableEl.setWidth(tableEl.getBox().width);
            }

            var box = this.getBox();
            this._resizeTask(this, box.width, box.height);

            // Filters might have been updated outside of facet
            if (this.filterChangeCounter == 0 && this.filterPanel) {
                var fp = this.filterPanel.getFilterPanel();
                if (fp) {
                    fp.initSelection();
                }
            }
            else {
                this.filterChangeCounter--;
            }
        }
    },

    getFilterCfg : function() {

        return {
            xtype : 'participantfilter',
            width     : this.width,
            layout    : 'fit',
            bodyStyle : 'padding: 8px;',
            normalWrap : true,
            overCls   : 'iScroll',

            // Filter specific config
            filterType  : 'group',
            subjectNoun : this.subjectNoun,
            defaultSelectUncheckedCategory : true,

            listeners : {
                afterrender : this.onFilterRender,
                selectionchange : this.onFilterChange,
                beforeInitGroupConfig : this.applyFilters,
                buffer : 1000,
                scope : this
            },

            scope : this
        };
    },

    onResize : function(panel, w, h, oldW, oldH) {
        this.resizeTask.delay(100, null, null, arguments);
    },

    // DO NOT CALL DIRECTLY. Use resizeTask.delay
    _resizeTask : function(panel, w, h, oldW, oldH) {

        // Resize data region wrapper
        var wrap = this.getDataRegionTableEl(this.dataRegion);

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
            // deal with participant case
        }

        this.onResolveFilter(filterMap);
    },

    onFilterRender : function(panel) {
        this.filterPanel = panel;
    },

    onResolveFilter : function(filterMap) {

        var qwp = this.getWrappedDataRegion();

        var dr = LABKEY.DataRegions[this.regionName];
        // Have a QWP, Ajax as a normal filter
        if (dr) {
            var valueArray = this.constructFilter(filterMap, dr, qwp.userFilters);
            this.filterChangeCounter++;
            dr.fireEvent("beforefilterchange", dr, valueArray);
        }
    },

    // This will get called for each separate group in the Filter Display
    applyFilters : function(fp, store) {
        if (store && store.getCount() > 0) {
            var userFilters = LABKEY.DataRegions[this.regionName].getUserFilterArray();
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
    },

    constructFilter : function(filterMap, dr, urlParameters) {

        var newValues = [];
        var urlFilters = [];
        var NOT = 'Not in any group';

        // Build LABKEY.Filters from filterMap
        for (var f in filterMap) {
            if (filterMap.hasOwnProperty(f)) {
                var type, value;
                if (filterMap[f].length > 1) {
                    type = LABKEY.Filter.Types.IN;
                    value = filterMap[f].join(';');
                }
                else {
                    type = LABKEY.Filter.Types.EQUAL;
                    value = filterMap[f][0];
                }

                value = value.replace(NOT, ''); // 17279: Working for 'Not in any group'
                var filter = LABKEY.Filter.create(f, value, type);
                urlFilters.push(filter);
            }
        }

        // Using the set of filters, merge this against the urlParameters
        var filterFound;
        for (var u in urlParameters) {
            if (urlParameters.hasOwnProperty(u)) {

                filterFound = false;
                var columnFilter = u.split('~');
                if (columnFilter.length > 1) {

                    columnFilter = columnFilter[0];
                    columnFilter = columnFilter.replace(dr.name + '.', '');

                    if (filterMap[columnFilter]) {
                        filterFound = true;
                    }
                }

                if (!filterFound) {
                    // 17280: Can't remove facet for one category
                    // Drop any matching filters related to Groups or Cohorts
                    if (u.indexOf(this.SUBJECT_PREFIX) < 0 && u.indexOf(this.COHORT_PREFIX) < 0) {
                        newValues.push([u, urlParameters[u]]);
                    }
                }
            }
        }

        // Now iterate across the urlFilters and add each to the value array
        var fa;
        for (f=0; f < urlFilters.length; f++) {
            fa = urlFilters[f];
            newValues.push([fa.getURLParameterName(dr.name), fa.getURLParameterValue()]);
        }

        return newValues;
    },

    onFailure : function(resp) {
        var o;
        try {
            o = Ext4.decode(resp.responseText);
        }
        catch (error) {
            Ext4.Msg.alert('Failure', 'An unknown error occurred.');
        }

        var msg = "";
        if(resp.status == 401){
            msg = resp.statusText || "Unauthorized";
        }
        else if(o != undefined && o.exception){
            msg = o.exception;
        }
        else {
            msg = "There was a failure. If the problem persists please contact your administrator.";
        }
        this.unmask();
        Ext4.Msg.alert('Failure', msg);
    }
});