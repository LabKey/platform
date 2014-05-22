/*
 * Copyright (c) 2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext.define('LABKEY.app.model.OlapExplorer', {

    extend: 'Ext.data.Model',

    fields: [
        {name : 'label'},
        {name : 'count', type : 'int'},
        {name : 'subcount', type : 'int'},
        {name : 'hierarchy'},
        {name : 'value'},
        {name : 'level'},
        {name : 'levelUniqueName'},
        {name : 'ordinal', type: 'int', defaultValue: -1},
        {name : 'uniqueName'},
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

    locked: false,

    olapProvider: null,

    maxCount: 0,

    // Subject name is going to be specific to the cube definition. It is used to prevent us from showing
    // subject ids as bars.
    subjectName : '',

    constructor : function(config) {

        this.locked = false;

        this.callParent([config]);

        this.addEvents('maxcount', 'selectrequest', 'subselect');
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
            var uniqueName = hierarchy.getUniqueName();
            var me = this;
//            this.D1 = new Date();
//            console.log('LOAD STORE');
            if (!this.totals[uniqueName]) {
                // Asks for Total Count
                this.olapProvider.onMDXReady(function(mdx) {
                    me.mdx = mdx;
                    mdx.query({
                        onRows: [{hierarchy: uniqueName, members:'members'}],
                        showEmpty: me.showEmpty,
                        success: function(qr) {
                            me.totals[uniqueName] = me.processMaxCount.call(me, qr);
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
                onRows : [{hierarchy: hierarchy.getUniqueName(), members:'members'}],
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
            var baseResult = this.baseResult;
            var targetLevels = hierarchy.levels;
            if (baseResult.metadata.cube.dimensions.length > 1)
                targetLevels = baseResult.metadata.cube.dimensions[1].hierarchies[0].levels;

            var recs = [],
                    max = this.totals[hierarchy.getUniqueName()],
                    target,
                    pos = baseResult.axes[1].positions,
                    activeGroup = '',
                    isGroup = false,
                    groupTarget;

            var hasSubjectLevel = targetLevels[targetLevels.length-1].name == this.subjectName;
            var hasGrpLevel = false;

            if (hasSubjectLevel) {
                hasGrpLevel = targetLevels.length > 3;
            } else {
                hasGrpLevel = targetLevels.length > 2;
            }

            var grpLevelID = targetLevels[1].id, subPosition;
            var customGroups = {};

            // skip (All)
            for (var x=1; x < pos.length; x++)
            {
                subPosition = pos[x][0];

                // Subjects should not be listed so do not roll up
                if ((!this.showEmpty && baseResult.cells[x][0].value == 0) || (subPosition.level.name == this.subjectName)) {
                    continue;
                }

                isGroup = false;
                if (hasGrpLevel && subPosition.level.id == grpLevelID) {
                    activeGroup = subPosition.name;
                    isGroup = true;
                }

                target = {
                    label: LABKEY.app.model.Filter.getMemberLabel(subPosition.name),
                    uniqueName: subPosition.uniqueName,
                    count: baseResult.cells[x][0].value,
                    value: subPosition.name,
                    hierarchy: hierarchy.getUniqueName(),
                    isGroup: isGroup,
                    level: subPosition.name,
                    ordinal: subPosition.ordinal,
                    levelUniqueName: subPosition.level.uniqueName,
                    collapsed: activeGroup && pos.length > 15 ? true : false,
                    btnShown: false
                };

                var instance = Ext.create('LABKEY.app.model.OlapExplorer', target);

                if (target.isGroup) {
                    groupTarget = instance;
                    if (!customGroups[target.level]) {
                        customGroups[target.level] = [];
                    }
                }
                else {
                    instance.set('level', activeGroup);
                    if (!customGroups[activeGroup]) {
                        customGroups[activeGroup] = [];
                    }
                    customGroups[activeGroup].push(instance);
                }

                var collapse = this.checkCollapse(instance.data);
                instance.set('collapsed', collapse);

                if (groupTarget) {
                    groupTarget.set('collapsed', collapse);
                }

                recs.push(instance);
            }

            var groupOnly = true;
            for (var r=0; r < recs.length; r++) {
                if (!recs[r].get('isGroup')) {
                    groupOnly = false;
                }
            }

            if (!groupOnly) {
                this.loadRecords(recs);
            }
            else {
                max = 0;
                this.removeAll();
            }

            this.customGroups = customGroups;

            this.maxCount = max;
            this.fireEvent('maxcount', this.maxCount);
//            var D2 = new Date();
//            console.log('LOAD STORE COMPLETE:', D2 - this.D1);

            if (useSelection) {
                this.fireEvent('selectrequest');
            }
        }
    },

    getCustomGroups : function() {
        return this.customGroups;
    },

    checkCollapse : function(data) {
        var check = this.collapseTrack[this.getCollapseKey(data)];
        if (!Ext.isBoolean(check)) {
            check = data.collapsed;
        }
        return check;
    },

    setCollapse : function(data, collapsed) {
        this.collapseTrack[this.getCollapseKey(data)] = collapsed;
    },

    getCollapseKey : function(data) {
        return '' + data.hierarchy + '-' + data.level + '-' + data.value;
    },

    getMaxCount : function() {
        return this.maxCount;
    },

    setEnableSelection : function(enableSelection) {
        this.enableSelection = enableSelection;
    },

    clearSelection : function() {
        if (this.enableSelection) {
            this.suspendEvents();
            var recs = this.queryBy(function(rec, id){
                rec.set('subcount', 0);
                return true;
            }, this);
            this.resumeEvents();
            this.fireEvent('subselect', recs.items);
        }
    },

    loadSelection : function(useLast) {
        if (this.enableSelection) {
            // asks for the subselected portion
            var me = this;

            me.suspendEvents();

            me.mflight++;
            me.mdx.query({
                onRows : [{
                    hierarchy: me.dim.getHierarchies()[this.hIndex].getUniqueName(),
                    members: 'members'
                }],
                useNamedFilters: ['stateSelectionFilter', 'hoverSelectionFilter', 'statefilter'],
                mflight: me.mflight,
                showEmpty: me.showEmpty,
                success: this.selectionSuccess,
                scope : this
            });
        }
    },

    selectionSuccess : function(cellset, mdx, x) {
        var me = this;
        if (x.mflight === me.mflight) {

            var ssf = mdx._filter['stateSelectionFilter'];
            var hsf = mdx._filter['hoverSelectionFilter'];

            if ((!ssf || ssf.length == 0) && (!hsf || hsf.length == 0)) {
                me.clearSelection();
            }
            else {
                var recs = me.queryBy(function(rec) {

                    var updated = false, cellspan_value = 0; // to update rows not returned by the query
                    var cells = cellset.cells, cs;
                    var un = rec.data.uniqueName;
                    for (var c=0; c < cells.length; c++)
                    {
                        cs = cells[c][0];
                        if (un === cs.positions[1][0].uniqueName)
                        {
                            updated = true;
                            rec.set('subcount', cs.value);
                        }
                        else
                        {
                            if(cs.value > 0) {
                                cellspan_value++;
                            }
                        }
                    }
                    if (!updated)
                    {
                        rec.set('subcount', 0);
                    }
                    return true;

                });
                me.resumeEvents();

                me.fireEvent('subselect', recs.items ? recs.items : []);
            }

        }
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
        this.positionLookup = "." + this.baseChartCls + " ." + this.barCls;
        this.ordinal = LABKEY.devMode && Ext.isDefined(LABKEY.ActionURL.getParameter('ordinal'));

        this.initTemplate();

        this.positionTask = new Ext.util.DelayedTask(this.positionText, this);
        this.groupClickTask = new Ext.util.DelayedTask(this.groupClick, this);
        this.selectionTask = new Ext.util.DelayedTask(this.selection, this);

        this.callParent();

        this.store.on('maxcount', this.onMaxCount, this);
        this.store.on('subselect', this.renderSelection, this);
        this.store.on('selectrequest', function() { this.selectRequest = true; },   this);
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
                            '<div class="saeparent">',
                                '<div class="saecollapse {#}-collapse" id="{#}-collapse">',
                                    '<p>+</p>',
                                '</div>',
                                '<div class="', this.barCls, ' large">',
                                    '<span class="', this.barLabelCls, '">{label:htmlEncode}',
                                    (this.ordinal ? '&nbsp;({ordinal:htmlEncode})' : ''),
                                    '</span>',
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
                            '<div class="saeparent">',
                                '<div class="saecollapse {#}-collapse" id="{#}-collapse">',
                                    '<p>-</p>',
                                '</div>',
                                '<div class="', this.barCls, ' large">',
                                    '<span class="', this.barLabelCls, '">{label:htmlEncode}',
                                    (this.ordinal ? '&nbsp;({ordinal:htmlEncode})' : ''),
                                    '</span>',
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
                            '<div class="', this.barCls, ' small barcollapse <tpl if="level.length &gt; 0">saelevel</tpl>">',
                                '<span class="', this.barLabelCls, '">{label:htmlEncode}',
                                (this.ordinal ? '&nbsp;({ordinal:htmlEncode})' : ''),
                                '</span>',
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
                            '<div class="', this.barCls, ' small <tpl if="level.length &gt; 0">saelevel</tpl>">',
                                '<span class="', this.barLabelCls, '">{label:htmlEncode}',
                                (this.ordinal ? '&nbsp;({ordinal:htmlEncode})' : ''),
                                '</span>',
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
        var bars = Ext.query(this.positionLookup),
                b, i,
                bWidth,
                info,
                label,
                numpercent,
                percent,
                sets = [], _set,
                t;

        for (i=0; i < bars.length; i++) {
            b = bars[i];
            t = Ext.get(Ext.query("." + this.barLabelCls, b)[0]);
            sets.push({
                bar: Ext.get(Ext.query(".index", b)[0]),
                barLabel: t,
                barCount: Ext.get(Ext.query(".count", b)[0]),
                info: Ext.get(Ext.query(".info", b)[0]),
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
            var groupRecords = this.store.query('isGroup', true).items; // assumed to be in order of index
            var expandos = Ext.query('.saecollapse');
            Ext.each(groupRecords, function(record, idx) {
                var bar = Ext.get(expandos[idx]);
                if (bar) {
                    this.registerGroupClick(bar, record);
                }
            }, this);
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

    onMaxCount : function(count) { this.positionTask.delay(10); },

    groupClick : function(rec) {
        var groups = this.store.getCustomGroups();
        var f = rec.get('level');

        if (Ext.isDefined(groups[f])) {
            this.toggleGroup({ children: groups[f] }, false, true);
            if (this.resizeTask)
                this.resizeTask.delay(100);
        }
    },

    toggleGroup : function(grp, force, animate) {
        var animConfig, current, ext,
                first = true,
                listeners,
                node,
                me = this, c, child;
        this.store.suspendEvents();

        for (c=0; c < grp.children.length; c++) {

            child = grp.children[c];

            if (!child.data.isGroup) {

                node = this.getNodeByRecord(child);
                ext = Ext.get(node);
                current = ext.getActiveAnimation();
                if (current && !force)
                    ext.stopAnimation();
                animConfig = {};
                listeners  = {};

                if (!child.data.collapsed) // collapse
                {
                    animConfig = {
                        to : {opacity: 0, height: 0},
                        setDisplay : 'none',
                        collapsed : true,
                        sign  : '+',
                        scope : this
                    };
                }
                else // expand
                {
                    animConfig = {
                        to : {opacity: 1, height: 27},
                        setDisplay : 'block',
                        collapsed : false,
                        sign  : '-',
                        scope : this
                    };
                }

                if (c == grp.children.length-1)
                {
                    listeners.beforeanimate = function(anim) {
                        anim.target.target.dom.style.display = animConfig.setDisplay;
                        me.animate = false;
                        me.positionTask.delay(100, null, null, [true]);
                    };
                }
                else
                {
                    listeners.beforeanimate = function(anim) {
                        anim.target.target.dom.style.display = animConfig.setDisplay;
                    };
                }
                animConfig.listeners = listeners;

                if (animate)
                    ext.animate(animConfig);
                else
                {
                    animConfig.to.display = animConfig.setDisplay;
                    ext.setStyle(animConfig.to);
                }

                if (first)
                {
                    var prev = ext.prev().child('.saecollapse');
                    prev.update('<p unselectable="on">' + animConfig.sign + '</p>');
                    first = false;
                }

                child.data['collapsed'] = animConfig.collapsed;
                me.store.setCollapse(child.data, animConfig.collapsed);
            }
            else if (grp.children[c+1] && !grp.children[c+1].data.isGroup)
            {
                var rec = grp.children[c+1];
                node    = this.getNodeByRecord(rec);
                ext     = Ext.get(node);

                var prev = ext.prev().child('.saecollapse');
                if (prev) {
                    prev.update('<p unselectable="on">' + (rec.data.collapsed ? '+' : '-') + '</p>');
                }
            }
        }
        this.store.resumeEvents();
    },

    selectionChange : function(sel, isPrivate) {
        this.selections = sel;
        if (this.dimension) {
            Ext.defer(function() {
                if (sel.length > 0) {
                    this.selection(false);
                }
                else {
                    if (!isPrivate) {
                        this.getSelectionModel().deselectAll();
                    }
                    this.store.clearSelection();
                }
            }, 150, this);
        }
        else {
            console.warn('Dimension must be loaded before selection change');
        }
    },

    filterChange : function() {
        if (this.dimension) {
            this.animate = false;
            this.loadStore();
        }
    },

    selection : function(useLast) {
        if (this.selectRequest && this.selections && this.selections.length > 0) {
            this.store.loadSelection(useLast);
        }
        else {
            this.getSelectionModel().deselectAll();
            this.store.clearSelection();
        }
    },

    _renderHasAdd : function(sel, countNode, width, remove, trueCount, subCount) {
        var cls = 'inactive';
        if (sel) {
            sel.setWidth('' + width + 'px');
        }
        if (remove) {
            if (countNode.hasCls(cls)) {
                countNode.removeCls(cls);
            }
            countNode.update(trueCount);
        }
        else {
            if (!countNode.hasCls(cls)) {
                countNode.addCls(cls);
            }
            countNode.update(subCount);
        }
    },

    renderSelection : function(r) {
        var node;
        for (var i=0; i < r.length; i++) {
            node = this.getNode(r[i]);
            if (node) {

                var selBar = Ext.query('.index-selected', node);

                if (selBar) {
                    var countNode = Ext.get(Ext.query('.count', node)[0]);
                    var bar = Ext.get(Ext.query(".index", node)[0]);

                    var _w = parseFloat(bar.getStyle('width'));
                    var sub = r[i].data.subcount; var count = r[i].data.count;
                    var _c = sub / count;
                    var sel = Ext.get(selBar[0]);
                    if (_c == 0 || isNaN(_c)) {
                        this._renderHasAdd(sel, countNode, 0, true, count, sub);
                    }
                    else if (_c >= 1) {
                        this._renderHasAdd(sel, countNode, _w, false, count, sub);
                    }
                    else {
                        this._renderHasAdd(sel, countNode, (_c * _w), false, count, sub);
                    }
                }
            }
        }
    },

    registerGroupClick : function(node, rec) {
        node.on('click', function() {
            this.groupClickTask.delay(100, null, null, [rec]);
        }, this);
    },

    positionHelper : function() {
        this.selectRequest = true;
        this.selectionTask.delay(100);
    },

    toggleEmpty : function() {
        this.showEmpty = !this.showEmpty;
        this.loadStore();
        return this.showEmpty;
    }
});
