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

        var renderTarget = 'dataregion_facet_' + config.dataRegion.name;
        var targetHTML = '<div id="' + renderTarget + '" style="float: left;"></div>';

        var topEl = this.getContainerEl(config.dataRegion);
        topEl.insertHtml('beforeBegin', targetHTML);
        topEl.setWidth(topEl.getBox().width + this.width + 5);
//        var reqWidth = this.getRequiredWidth(config.dataRegion);
//        if (topEl.getBox().width < reqWidth) {
//            topEl.setWidth(reqWidth);
//        }

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
            header : {
                xtype : 'header',
                title : 'Filter',
                cls : 'facet_header'
            },
            cls : 'labkey-data-region-facet',
            height : Ext4.get('dataregion_' + config.dataRegion.name).getBox().height,
            minHeight : 450
        });

        this.filterTask = new Ext4.util.DelayedTask(this._filterTask, this);
        this.resizeTask = new Ext4.util.DelayedTask(this._resizeTask, this);

        this.callParent([config]);
    },

    initComponent : function() {

        this.items = [];

        this.callParent(arguments);

        var task = new Ext4.util.DelayedTask(function(){
            this.add(this.getFilterCfg());
        }, this);

        this.on('afterrender',  function() {
            this.getWrappedDataRegion();
            task.delay(200); // animation time
        }, this, {single: true});

        this.on('beforeexpand', function() {
            this._beforeShow(); this.show();
        }, this);
        this.on('collapse',     function() {
            this.hide(); this._afterHide();
        }, this);
        this.on('resize',       this.onResize, this);
    },

    getContainerEl : function(dr) {
        return Ext4.get(dr.name).up('div');
    },

    _beforeShow : function() {
        var el = this.getContainerEl(this.dataRegion);
        el.setWidth(el.getBox().width + this.width + 5);
//        var reqWidth = this.getRequiredWidth(this.dataRegion);
//        if (el.getBox().width < reqWidth) {
//            el.setWidth(reqWidth);
//        }
    },

    _afterHide : function() {
        var el = this.getContainerEl(this.dataRegion);
        el.setWidth(null);
    },

    getRequiredWidth : function(dr) {
        return 265 + Ext4.get('dataregion_' + dr.name).getBox().width;
    },

    getWrappedDataRegion : function() {
        if (!this.qwp) {
            // Wrap the corresponding Data Region with a QWP
            this.qwp = new LABKEY.QueryWebPart({
                dataRegion : this.dataRegion,
                parameters : {
                    facet : true
                },
                success : function(dr) {
                    // Give access to to this filter panel to the Data Region
                    if (dr) {
                        this.dataRegion = LABKEY.DataRegions[dr.name];
                        LABKEY.DataRegions[dr.name].setFacet(this);
                        var box = this.getBox();
                        this.onResize(this, box.width, box.height);
                    }
                },
                scope : this
            });

            this.qwp.on('beforeclearallfilters', function(dr) {
                this.filterPanel.getFilterPanel().selectAll(true);
            }, this);
        }

        return this.qwp;
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

            listeners : {
                afterrender : this.onFilterRender,
                selectionchange : this.onFilterChange,
                beforeInitGroupConfig : this.applyFilters,
                scope : this
            },

            scope : this
        };
    },

    onDataRegionResize : function() {
        var newHeight = Ext4.get('dataregion_' + this.dataRegion.name).getBox().height;
        this.animate({
            to : { height: newHeight }
        });
    },

    onResize : function(panel, w, h, oldW, oldH) {
        this.resizeTask.delay(100, null, null, arguments);
    },

    // DO NOT CALL DIRECTLY. Use resizeTask.delay
    _resizeTask : function(panel, w, h, oldW, oldH) {

        // Resize data region wrapper
        var wrap = Ext4.get('dataregion_' + this.dataRegion.name).parent('div.labkey-data-region-wrap');
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
    },

    // DO NOT CALL DIRECTLY. Use filterTask.delay
    _filterTask : function() {

        // Current all being selected === none being selected
        var filters = this.filterPanel.getSelection(true, true),
            filterMap = {},
            filterPrefix, f=0;

        for (; f < filters.length; f++) {
            if (filters[f].get('type') != 'participant') {

                // Build what a filter might look like
                if (filters[f].data.category) {
                    filterPrefix = 'ParticipantId/' + filters[f].data.category.label;
                }
                else {
                    // Assume it is a cohort
                    filterPrefix = 'ParticipantId/Cohort/Label';
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

    onFilterChange : function() {
        this.filterTask.delay(350);
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
            dr.changeFilter(valueArray, LABKEY.DataRegion.buildQueryString(valueArray));
        }
    },

    // This will get called for each separate group in the Filter Display
    applyFilters : function(fp, store) {
        if (store && store.getCount() > 0) {
            var userFilters = LABKEY.DataRegions[this.regionName].getUserFilterArray();
            if (userFilters && userFilters.length > 0) {

                var uf, selection = [];
                for (var u=0; u < userFilters.length; u++) {

                    uf = userFilters[u];

                    for (var s=0; s < store.getRange().length; s++) {
                        var rec = store.getAt(s);

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

        var newValArray = [];
        var urlFilters = [];

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

                var filter = LABKEY.Filter.create(f, value, type);
                urlFilters.push(filter);
            }
        }

        // Using the set of filters, merge this against the urlParameters
        var filterFound;
        for (var u in urlParameters) {
            filterFound = false;
            if (urlParameters.hasOwnProperty(u)) {

                var columnFilter = u.split('~');
                if (columnFilter.length > 1) {

                    columnFilter = columnFilter[0];
                    columnFilter = columnFilter.replace(dr.name + '.', '');

                    if (filterMap[columnFilter]) {
                        filterFound = true;
                    }
                }

                if (!filterFound) {
                    newValArray.push([u, urlParameters[u]]);
                }
            }
        }

        // Now iterate across the urlFilters and add each to the value array
        var fa;
        for (f=0; f < urlFilters.length; f++) {
            fa = urlFilters[f];
            newValArray.push([fa.getURLParameterName(dr.name), fa.getURLParameterValue()]);
        }

        return newValArray;
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