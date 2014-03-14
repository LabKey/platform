Ext.define('LABKEY.app.model.OlapExplorer', {

    extend: 'Ext.data.Model',

    fields: [
        {name : 'label'},
        {name : 'count', type : 'int'},
        {name : 'subcount', type : 'int'},
        {name : 'hierarchy'},
        {name : 'value'},
        {name : 'level'},
        {name : 'isGroup', type : 'boolean'},
        {name : 'collapsed', type : 'boolean'},
        {name : 'btnId'}
    ]
});

Ext.define('LABKEY.app.store.OlapExplorer', {
    extend: 'Ext.data.Store',

    model: 'LABKEY.app.model.OlapExplorer',

    enableSelection: true,

    collapseTrack: {},

    totals: {},

    groupField: 'level',

    locked: false,

    olapProvider: null,

    maxCount: 0,

    constructor : function(config) {

        this.locked = false;

        this.callParent([config]);

        this.addEvents('maxcount');
    },

    load : function(dimension, hIndex, useSelection, showEmpty) {

        if (!this.olapProvider) {
            console.error('Explorer must initialize olapProvider object.');
            return;
        }

        if (!this.locked) {
            this.locked = true;
            this.stale = undefined;

            this.dim = dimension;
            this.flight = 0;
            this.showEmpty = (showEmpty ? true : false);
            this.hIndex = hIndex || 0;

            if (this.enableSelection) {
                // reset selection ignoring inflight requests
                this.mflight = 0;
            }
            this.loadDimension(useSelection);
        }
        else {
            // mark as stale, processed once previous request is unlocked
            this.stale = {
                dimension: dimension,
                hIndex: hIndex,
                useSelection: useSelection,
                showEmpty: showEmpty
            };
        }
    },

    loadDimension : function(useSelection) {
        var hierarchies = this.dim.getHierarchies();
        if (hierarchies.length > 0) {
            var hierarchy = hierarchies[this.hIndex];
            var me = this;

            if (!this.totals[hierarchy.getName()]) {
                // Asks for Total Count
                this.olapProvider.onMDXReady(function(mdx) {
                    me.mdx = mdx;
                    mdx.query({
                        onRows: [{hierarchy: hierarchy.getName(), members:'members'}],
                        showEmpty: me.showEmpty,
                        success: function(qr) {
                            me.totals[hierarchy.getName()] = me.processMaxCount.call(me, qr);
                            me.requestDimension(hierarchy, useSelection);
                        }
                    });

                }, this);
            }
            else {
                me.requestDimension(hierarchy, useSelection);
            }
        }
    },

    requestDimension : function(hierarchy, useSelection) {
        // Asks for the Gray area
        this.flight++;
        this.olapProvider.onMDXReady(function(mdx){
            var me = this;
            mdx.query({
                onRows : [{hierarchy: hierarchy.getName(), members:'members'}],
                useNamedFilters : ['statefilter'],
                showEmpty : me.showEmpty,
                success: function(qr) {
                    me.baseResult = qr;
                    me.requestsComplete(useSelection);
                }
            });
        }, this);
    },

    processMaxCount : function(qr) {
        var t = -1, x=1;
        for (; x < qr.axes[1].positions.length; x++) {
            if (qr.cells[x][0].value > t) {
                t = qr.cells[x][0].value;
            }
        }
        return t;
    },

    requestsComplete : function(useSelection) {
        this.flight--;
        if (this.flight == 0) {

            // unlock for requests for other dimensions
            this.locked = false;
            if (this.eventsSuspended) {
                this.resumeEvents();
            }

            // first check for 'stale'
            if (Ext.isObject(this.stale)) {
                this.load(this.stale.dimension, this.stale.hIndex, this.stale.useSelection, this.stale.showEmpty);
                return;
            }

            var hierarchy = this.dim.getHierarchies()[this.hIndex];
            var set = this.baseResult;

            var targetLevels = set.metadata.cube.dimensions[1].hierarchies[0].levels;

            var recs = [],
                    max = this.totals[hierarchy.getName()],
                    target,
                    pos = set.axes[1].positions,
                    activeGroup = '',
                    isGroup = false,
                    groupTarget;

            // skip (All)
            for (var x=1; x < pos.length; x++)
            {
                if (!this.showEmpty && set.cells[x][0].value == 0) {
                    continue;
                }

                // Subjects should not be listed so do not roll up
                if (hierarchy.getName().indexOf('Subject.') != -1)
                {
                    activeGroup = '';
                    isGroup = false;
                    if (pos[x][0].level.id != targetLevels[1].id) {
                        continue;
                    }
                }
                else if (targetLevels.length > 2 && pos[x][0].level.id == targetLevels[1].id) {
                    activeGroup = pos[x][0].name;
                    isGroup = true;
                }

                target = {
                    label: pos[x][0].name == '#null' ? 'Unknown' : pos[x][0].name,
                    count: set.cells[x][0].value,
                    value: pos[x][0].name,
                    hierarchy: hierarchy.getName(),
                    isGroup: isGroup,
                    level: pos[x][0].name,
                    collapsed: activeGroup && pos.length > 15 ? true : false,
                    btnShown: false
                };

                if (!target.isGroup) {
                    target.level = activeGroup;
                }

                if (target.isGroup) {
                    groupTarget = target;
                }

                target.collapsed = this.checkCollapse(target);
                if (groupTarget) {
                    groupTarget.collapsed = target.collapsed;
                }

                recs.push(target);

                isGroup = false;
            }

            var groupOnly = true;
            for (var r=0; r < recs.length; r++) {
                if (!recs[r].isGroup) {
                    groupOnly = false;
                }
            }

            if (!groupOnly) {
                // This must be called before any events are fired -- eventSuspended
                this.loadData(recs);
            }
            else {
                max = 0;
                this.removeAll();
            }

            this.group(this.groupField);

            this.maxCount = max;
            this.fireEvent('maxcount', this.maxCount);

            if (useSelection) {
                this.fireEvent('selectrequest');
            }
        }
    },

    loadRecords : function(records, options) {
        options = options || {};


        if (!options.addRecords) {
            delete this.snapshot;
            this.clearData();
        }

        this.data.addAll(records);

        for (var i=0; i < records.length; i++) {
            if (options.start !== undefined) {
                records[i].index = options.start + i;

            }
            records[i].join(this);
        }
    },

    checkCollapse : function(target) {

        var check = this.collapseTrack['' + target.hierarchy + '-' + target.level + '-' + target.value];
        if (check === true || check === false)
            return check;
        return target.collapsed;
    },

    setCollapse : function(record, collapsed) {
        this.collapseTrack['' + record.data.hierarchy + '-' + record.data.level + '-' + record.data.value] = collapsed;
    },

    getMaxCount : function() {
        return this.maxCount;
    }
});

Ext.define('LABKEY.app.view.OlapExplorer', {
    extend: 'Ext.view.View',

    alias : 'widget.olapexplorerview',

    multiSelect : true,

    animate: true,

    itemSelector : 'div.bar',

    baseChartCls: 'barchart',

    baseGroupCls: 'bargroup',

    barCls: 'bar',

    barLabelCls: 'barlabel',

    initComponent : function() {

        this.textCache = {};

        this.initTemplate();

        this.positionTask = new Ext.util.DelayedTask(this.positionText, this);

        this.callParent();

        this.store.on('maxcount', this.onMaxCount, this);
    },

    initTemplate : function() {
        //
        // This template is meant to be bound to a set of LABKEY.app.model.OlapExplorer instances
        //
        this.tpl = new Ext.XTemplate(
            '<div class="', this.baseChartCls, '">',
                '<div class="', this.baseGroupCls, '">',
                    // For each record
                    '<tpl for=".">',
                        //
                        // Collapsed Group
                        //
                        '<tpl if="isGroup === true && collapsed == true">',
                            '<div>',
                                '<div class="saecollapse {#}-collapse" id="{#}-collapse">',
                                    '<p>+</p>',
                                '</div>',
                                '<div class="', this.barCls, ' large">',
                                    '<span class="', this.barLabelCls, '">{label:htmlEncode}</span>',
                                    '<span class="count">{count}</span>',
                                    '<span class="index"></span>',
                                    '<span class="index-selected inactive"></span>',
                                '</div>',
                            '</div>',
                        '</tpl>',

                        //
                        // Expanded Group
                        //
                        '<tpl if="isGroup === true && collapsed == false">',
                            '<div>',
                                '<div class="saecollapse {#}-collapse" id="{#}-collapse">',
                                    '<p>-</p>',
                                '</div>',
                                '<div class="', this.barCls, ' large">',
                                    '<span class="', this.barLabelCls, '">{label:htmlEncode}</span>',
                                    '<span class="count">{count}</span>',
                                    '<span class="index"></span>',
                                    '<span class="index-selected inactive"></span>',
                                '</div>',
                            '</div>',
                        '</tpl>',

                        //
                        // Collpased Ungrouped
                        //
                        '<tpl if="isGroup === false && collapsed == true">',
                            '<div class="', this.barCls, ' small barcollapse">',
                                '<span class="', this.barLabelCls, '">{label:htmlEncode}</span>',
                                '<span class="count">{count}</span>',
                                '<span class="info"></span>',
                                '<span class="index"></span>',
                                '<span class="index-selected inactive"></span>',
                            '</div>',
                        '</tpl>',

                        //
                        // Expanded Ungrouped
                        //
                        '<tpl if="isGroup === false && collapsed == false">',
                            '<div class="', this.barCls, ' small">',
                                '<span class="', this.barLabelCls, '">{label:htmlEncode}</span>',
                                '<span class="count">{count}</span>',
                                '<span class="info"></span>',
                                '<span class="index"></span>',
                                '<span class="index-selected inactive"></span>',
                            '</div>',
                        '</tpl>',
                    '</tpl>',
                '</div>',
            '</div>'
        );
    },

    positionText: function(collapseMode) {
        var bar,
                bars = Ext.query("." + this.baseChartCls + " ." + this.barCls),
                grps = this.store.getGroups(),
                i, g=0,
                bWidth,
                info,
                label,
                numpercent,
                percent,
                sets = [], _set,
                t;

        for (i=0; i < bars.length; i++) {
            t = Ext.get(Ext.query("." + this.barLabelCls, bars[i])[0]);
            sets.push({
                bar: Ext.get(Ext.query(".index", bars[i])[0]),
                barLabel: t,
                barCount: Ext.get(Ext.query(".count", bars[i])[0]),
                info: Ext.get(Ext.query(".info", bars[i])[0]),
                label: t.dom.innerHTML
            });
        }

        this.suspendLayout = true;
        var count = this.store.getMaxCount();

        for (i=0; i < sets.length; i++) {

            _set = sets[i];
            label = _set.label;
            if (this.textCache[label]) {
                t = this.textCache[label];
            }
            else {
                t = this.textCache[label] = sets[i].barLabel.getTextWidth();
            }

            // optimization for 0 case
            if (_set.barCount.dom.innerHTML == '0') {
                _set.bar.setWidth('0%');
                _set.barCount.setLeft(t + 15);
                if (_set.info)
                    _set.info.setLeft(t + 60);
                continue;
            }

            // barCount.dom.innerText is a number like '100'
            numpercent = (_set.barCount.dom.innerHTML / count) * 100;
            percent = '' + numpercent + '%';

            _set.bar.setWidth(percent);
            bWidth = _set.bar.getWidth(); // returns width in pixels
            if (bWidth > t) {
                t = bWidth;
            }

            _set.barCount.setLeft(t + 15);
            if (_set.info) {
                _set.info.setLeft(t + 60);
            }

            if (this.animate) {
                _set.bar.setWidth("0%");
                _set.bar.setWidth(percent, {
                    duration: 300,
                    easing: 'linear',
                    callback: this.positionHelper,
                    scope: this
                });
            } else {
                this.positionHelper.call(this);
            }
        }
        this.suspendLayout = false;

        if (!collapseMode) {
            for (; g < grps.length; g++) {
                bar = Ext.get(Ext.query('.saecollapse')[g]);
                if (bar) {
                    i = bar.dom.id.split('-collapse')[0]-1;
                    this.registerGroupClick(bar, this.store.getAt(i));
                }
            }
        }
    },

    setDimension : function(dim, hierarchyIndex) {
        this.dimension = dim;
        this.setHierarchy(hierarchyIndex);
    },

    setHierarchy : function(index) {
        this.animate = true;
        this.hierarchyIndex = index;
        this.loadStore();
    },

    loadStore : function() {
        this.store.load(this.dimension, this.hierarchyIndex, true, this.showEmpty);
    },

    onMaxCount : function(count) { },

    positionHelper : function() { },

    registerGroupClick : function(node, rec) { }
});
