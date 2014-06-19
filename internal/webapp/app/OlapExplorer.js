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
        {name : 'subcount', type : 'int', defaultValue: 0},
        {name : 'hierarchy'},
        {name : 'value'},
        {name : 'level'},
        {name : 'levelUniqueName'},
        {name : 'ordinal', type: 'int', defaultValue: -1},
        {name : 'uniqueName'},
        {name : 'isGroup', type : 'boolean'},
        {name : 'collapsed', type : 'boolean'},
        {name : 'btnId'},

        // Global properties
        {name : 'maxcount', type : 'int'},
        {name : 'hasSelect', type : 'boolean', defaultValue: false}
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

    KEYED_LOAD: false,

    constructor : function(config) {

        // initialize flight locks
        this.flight = 0; // -- records
        this.mflight = 0; // -- selections

        this.callParent([config]);

        this.addEvents('selectrequest', 'subselect');
    },

    load : function(dimension, hIndex, selections, showEmpty) {
        this.KEYED_LOAD = true;

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
            this.loadDimension(selections);
        }
        else {
            // mark as stale, processed once previous request is unlocked
            this.stale = {
                dimension: dimension,
                hIndex: hIndex,
                selections: selections,
                showEmpty: showEmpty
            };
        }
    },

    loadDimension : function(selections) {
        var hierarchies = this.dim.getHierarchies();
        if (hierarchies.length > 0) {
            var hierarchy = hierarchies[this.hIndex];
            var uniqueName = hierarchy.getUniqueName();
            var me = this;
            if (!this.totals[uniqueName]) {
                // Asks for Total Count
                this.olapProvider.onMDXReady(function(mdx) {
                    me.mdx = mdx;
                    mdx.query({
                        onRows: [{hierarchy: uniqueName, members:'members'}],
                        showEmpty: me.showEmpty,
                        success: function(qr) {
                            me.totals[uniqueName] = me.processMaxCount.call(me, qr);
                            me.requestDimension(hierarchy, selections);
                        }
                    });

                }, this);
            }
            else {
                me.requestDimension(hierarchy, selections);
            }
        }
    },

    requestDimension : function(hierarchy, selections) {
        // Asks for the Gray area
        this.flight++;
        var hasSelection = Ext.isArray(selections) && selections.length > 0;
        if (hasSelection) {
            this.mflight++;
        }
        this.olapProvider.onMDXReady(function(mdx){
            var me = this;

            var scoped = {
                baseResult: undefined,
                selectionResult: undefined,
                useSelection: hasSelection
            };

            var check = function() {
                if (Ext.isDefined(scoped.baseResult)) {
                    if (hasSelection) {
                        if (Ext.isDefined(scoped.selectionResult)) {
                            me.requestsComplete(scoped);
                        }
                    }
                    else {
                        me.requestsComplete(scoped);
                    }
                }
            };

            mdx.query({
                onRows : [{hierarchy: hierarchy.getUniqueName(), members:'members'}],
                useNamedFilters : ['statefilter'],
                showEmpty : me.showEmpty,
                qFlight: this.flight,
                success: function(qr, _mdx, x) {
                    if (this.flight === x.qFlight) {
                        scoped.baseResult = qr;
                        check();
                    }
                },
                scope: this
            });
            if (hasSelection) {
                me.requestSelection(this.mflight, function(qr, _mdx, x) {
                    if (this.mflight === x.mflight) {
                        scoped.selectionResult = qr;
                        check();
                    }
                }, this);
            }
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

    requestsComplete : function(response) {

        // unlock for requests for other dimensions
        this.locked = false;
        if (this.eventsSuspended) {
            this.resumeEvents();
        }

        // first check for 'stale'
        if (Ext.isObject(this.stale)) {
            this.load(this.stale.dimension, this.stale.hIndex, this.stale.selections, this.stale.showEmpty);
            return;
        }

        var hierarchy = this.dim.getHierarchies()[this.hIndex];
        var baseResult = response.baseResult;
        var selectionResult = response.selectionResult;
        var targetLevels = hierarchy.levels;

        if (baseResult.metadata.cube.dimensions.length > 1) {
            targetLevels = baseResult.metadata.cube.dimensions[1].hierarchies[0].levels;
        }

        var recs = [],
                max = this.totals[hierarchy.getUniqueName()],
                target,
                pos = baseResult.axes[1].positions,
                activeGroup = '',
                isGroup = false,
                groupTarget;

        var hasSubjectLevel = targetLevels[targetLevels.length-1].name === this.subjectName;
        var hasGrpLevel = targetLevels.length > (hasSubjectLevel ? 3 : 2);
        var grpLevelID = targetLevels[1].id, subPosition;
        var customGroups = {};

        // skip (All)
        for (var x=1; x < pos.length; x++)
        {
            subPosition = pos[x][0];

            // Subjects should not be listed so do not roll up
            if ((!this.showEmpty && baseResult.cells[x][0].value === 0) || (subPosition.level.name === this.subjectName)) {
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
                maxcount: max,
                value: subPosition.name,
                hierarchy: hierarchy.getUniqueName(),
                isGroup: isGroup,
                level: subPosition.name,
                ordinal: subPosition.ordinal,
                levelUniqueName: subPosition.level.uniqueName,
                collapsed: activeGroup && pos.length > 15 ? true : false,
                btnShown: false,
                hasSelect: response.useSelection === true
            };

            if (response.useSelection) {
                target.subcount = this._calculateSubcount(selectionResult, target.uniqueName);
            }

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

        if (response.useSelection) {
            this.fireEvent('selectrequest');
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

    setEnableSelection : function(enableSelection) {
        this.enableSelection = enableSelection;
    },

    clearSelection : function() {
        if (this.enableSelection) {
            this.suspendEvents(true);
            this.queryBy(function(rec) {
                rec.set({
                    subcount: 0,
                    hasSelect: false
                });
                return true;
            }, this);
            this.resumeEvents();
            this.fireEvent('subselect', this);
        }
    },

    requestSelection : function(mflight, callback, scope) {
        this.mdx.query({
            onRows : [{
                hierarchy: this.dim.getHierarchies()[this.hIndex].getUniqueName(),
                members: 'members'
            }],
            useNamedFilters: ['stateSelectionFilter', 'hoverSelectionFilter', 'statefilter'],
            mflight: mflight,
            showEmpty: this.showEmpty,
            success: callback,
            scope : scope
        });
    },

    loadSelection : function() {
        if (this.enableSelection) {
            // asks for the subselected portion
            this.mflight++;
            this.requestSelection(this.mflight, this.onLoadSelection, this);
        }
    },

    onLoadSelection : function(cellset, mdx, x) {
        var me = this;
        if (x.mflight === me.mflight) {

            var ssf = mdx._filter['stateSelectionFilter'];
            var hsf = mdx._filter['hoverSelectionFilter'];

            if ((!ssf || ssf.length == 0) && (!hsf || hsf.length == 0)) {
                me.clearSelection();
            }
            else {
                this.suspendEvents(true);
                me.queryBy(function(rec) {
                    rec.set({
                        subcount: this._calculateSubcount(cellset, rec.get('uniqueName')),
                        hasSelect: true
                    });
                    return true;
                }, this);
                this.resumeEvents();
            }

            this.fireEvent('subselect', this);
        }
    },

    _calculateSubcount : function(cellset, uniqueName) {
        var cells = cellset.cells, cs, sc=0;
        for (var c=0; c < cells.length; c++) {
            cs = cells[c][0];
            if (uniqueName === cs.positions[1][0].uniqueName)
            {
                sc = cs.value;
            }
        }
        return sc;
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

    selectedItemCls: 'bar-selected',

    highlightItemCls: 'bar-highlight',

    barCls: 'bar',

    barLabelCls: 'barlabel',

    statics: {
        APPLY_ANIMATE: false
    },

    refresh : function() {
        if (this.store.KEYED_LOAD === true) {
            this.addAnimations();

            // 20637: This prevents an optimization made in Ext.view.AbstractView from getting
            // an incorrent number of DOM node references relative to the number of records provided by
            // the store.
            // Related: http://www.sencha.com/forum/showthread.php?133011-4.0.0-Bug-in-AbstractView.updateIndexes
            this.fixedNodes = 0;

            this.callParent(arguments);
            this.removeAnimations();
        }
    },

    addAnimations : function() {
        LABKEY.app.view.OlapExplorer.APPLY_ANIMATE = true;
    },

    removeAnimations : function() {
        LABKEY.app.view.OlapExplorer.APPLY_ANIMATE = false;
    },

    initComponent : function() {

        this.ordinal = LABKEY.devMode && Ext.isDefined(LABKEY.ActionURL.getParameter('ordinal'));

        this.initTemplate();

        this.loadTask = new Ext.util.DelayedTask(function() {
            if (Ext.isDefined(this.dimension)) {
                this.store.load(this.dimension, this.hierarchyIndex, this.selections, this.showEmpty);
            }
        }, this);

        this.positionTask = new Ext.util.DelayedTask(this.positionText, this);
        this.groupClickTask = new Ext.util.DelayedTask(this.groupClick, this);
        this.refreshTask = new Ext.util.DelayedTask(this.onDelayedRefresh, this);
        this.selectionTask = new Ext.util.DelayedTask(this.selection, this);

        this.callParent();

        this.store.on('selectrequest', function() { this.selectRequest = true; }, this);

        this.on('refresh', function() {
            this.onRefresh();
            this.refreshTask.delay(600); // wait until the animations are finished
        }, this);

        this.store.on('subselect', this.highlightSelections, this);
    },

    onRefresh : function() {
        //
        // Bind groups toggles
        //
        var groups = this.store.query('isGroup', true).getRange(); // assumed to be in order of index
        if (groups.length > 0) {
            var expandos = Ext.query('.saecollapse'), bar;
            Ext.each(groups, function(group, idx) {
                bar = Ext.get(expandos[idx]);
                if (bar) {
                    this.registerGroupClick(bar, group);
                }
            }, this);
        }
        this.highlightSelections();
    },

    onDelayedRefresh : function() {
        //
        // Remove animators
        //
        var anims = Ext.DomQuery.select('.animator');
        Ext.each(anims, function(a) {
            a = Ext.get(a);
            a.replaceCls('animator', '');
        });
    },

    initTemplate : function() {

        var barTpl = this.getBarTemplate();
        var countTpl = this.getCountTemplate();

        //
        // This template is meant to be bound to a set of LABKEY.app.model.OlapExplorer instances
        //
        this.tpl = new Ext.XTemplate(
            '<div class="', this.baseChartCls, '">',
                '<div class="', this.baseGroupCls, '">',
                    '<tpl for=".">',
                        '<tpl if="isGroup === true">',
                            '<div class="saeparent">',
                                '<div class="saecollapse {#}-collapse" id="{#}-collapse">',
                                    '<p><tpl if="collapsed === true">+<tpl else>-</tpl></p>',
                                '</div>',
                                '<div class="', this.barCls, ' large">',
                                    '<span class="', this.barLabelCls, '">{label:htmlEncode}',
                                    (this.ordinal ? '&nbsp;({ordinal:htmlEncode})' : ''),
                                    '</span>',
                                    '{[ this.renderCount(values) ]}',
                                    '{[ this.renderBars(values) ]}',
                                '</div>',
                            '</div>',
                        '<tpl else>',
                            '<div class="', this.barCls, ' small<tpl if="collapsed === true"> barcollapse</tpl><tpl if="level.length &gt; 0"> saelevel</tpl>">',
                                '<span class="', this.barLabelCls, '">{label:htmlEncode}',
                                (this.ordinal ? '&nbsp;({ordinal:htmlEncode})' : ''),
                                '</span>',
                                '{[ this.renderCount(values) ]}',
//                                '<span class="info" style="left: {[ this.calcLeft(values) ]}%"></span>',
//                                '<span class="info" style="left: 115%"></span>',
                                '{[ this.renderBars(values) ]}',
                            '</div>',
                        '</tpl>',
                    '</tpl>',
                '</div>',
            '</div>',
                {
//                    calcLeft : function(v) {
//                        return ((v.count / v.maxcount) * 100) + 15;
//                    },
                    renderBars : function(values) {
                        return barTpl.apply(values);
                    },
                    renderCount : function(values) {
                        return countTpl.apply(values);
                    }
                }
        );
    },

    getBarTemplate : function() {
        return new Ext.XTemplate(
                '<span class="index {[ this.doAnimate() ]}" style="width: {[ this.calcWidth(values) ]}%"></span>',
                '<span class="index-selected inactive {[ this.doAnimate() ]}" style="width: {[ this.calcSubWidth(values) ]}%"></span>',
                {
                    doAnimate : function() {
                        return LABKEY.app.view.OlapExplorer.APPLY_ANIMATE === true ? 'animator' : '';
                    },
                    calcWidth : function(v) {
                        return (v.count / v.maxcount) * 100;
                    },
                    calcSubWidth : function(v) {
                        var ps = (v.subcount / v.count);
                        var pt = (v.count / v.maxcount);
                        var pts;

                        if (isNaN(ps)) {
                            pts = 0;
                        }
                        else if (ps >= 1) {
                            pts = pt;
                        }
                        else {
                            pts = ps*pt;
                        }
                        return pts * 100;
                    }
                }
        );
    },

    getCountTemplate : function() {
        return new Ext.XTemplate('<span class="count">{count}</span>');
    },

    positionText: function(collapseMode) {
        this.positionHelper.call(this);
    },

    setDimension : function(dim, hierarchyIndex) {
        this.dimension = dim;
        this.setHierarchy(hierarchyIndex);
    },

    setHierarchy : function(index) {
        this.hierarchyIndex = index;
        this.loadStore();
    },

    loadStore : function() {
        this.loadTask.delay(50);
    },

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
                    this.selection();
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
        this.loadStore();
    },

    selection : function() {
        if (this.selectRequest || (Ext.isArray(this.selections) && this.selections.length > 0)) {
            this.store.loadSelection();
        }
        else {
            this.getSelectionModel().deselectAll();
            this.store.clearSelection();
        }
    },

    highlightSelections : function() {
        if (Ext.isArray(this.selections) && this.selections.length > 0) {
            var members, uniques = [];
            Ext.each(this.selections, function(sel) {
                members = Ext.isFunction(sel.get) ? sel.get('members') : sel.membersQuery.members;
                uniques = uniques.concat(Ext.Array.pluck(members, "uniqueName"));
            }, this);

            Ext.each(uniques, function(uniqueName) {
                var idx = this.store.findExact('uniqueName', uniqueName);
                if (idx > -1) {
                    var rec = this.store.getAt(idx);
                    var node = this.getNode(rec);
                    if (node) {
                        Ext.get(node).addCls(this.highlightItemCls);
                    }
                }
            }, this);
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
