/*
 * Copyright (c) 2014-2017 LabKey Corporation
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

    alternateClassName: 'LABKEY.olapStore',

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

    perspective: undefined,

    KEYED_LOAD: false,

    statics: {
        /**
         * These sort functions assume sorting an Array of LABKEY.app.model.OlapExplorer instances
         */
        sorters: {
            /**
             * A valid Array.sort() function that sorts an array of LABKEY.app.model.OlapExplorer instances
             * alphanumerically according to the 'label' field.
             * @param recA
             * @param recB
             * @returns {number}
             */
            sortAlphaNum : function(recA, recB) {
                return LABKEY.app.model.Filter.sorters.alphaNum(recA.get('label'), recB.get('label'));
            },
            /**
             * An valid Array.sort() function that sorts an array of LABKEY.app.model.OlapExplorer instances
             * alphanumerically according to the 'label' field. The 'Range' feature is meant to split on values that
             * are indicative of a range (e.g. 10-20, 32.1-98.2). In these cases, the sort will only occur on the value
             * before the '-' character.
             * @param recA
             * @param recB
             * @returns {number}
             */
            sortAlphaNumRange : function(recA, recB) {
                return LABKEY.app.model.Filter.sorters.alphaNum(recA.get('label').split('-')[0], recB.get('label').split('-')[0]);
            },
            /**
             * A valid Array.sort() function that sorts an array of LABKEY.app.model.OlapExplorer instances
             * using natural sort according to the instance's 'label' field.
             * @param recA
             * @param recB
             * @returns {number}
             */
            sortNatural : function(recA, recB) {
                return LABKEY.app.model.Filter.sorters.natural(recA.get('label'), recB.get('label'));
            }
        }
    },

    constructor : function(config) {

        // initialize flight locks
        this.flight = 0; // -- records
        this.mflight = 0; // -- selections

        this.callParent([config]);

        this.addEvents('selectrequest', 'subselect');
    },

    load : function(dimension, hIndex, selections, showEmpty, altRequestDimNamedFilters) {
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
                // reset selection ignoring in-flight requests
                this.mflight = 0;
            }
            this.loadDimension(selections, altRequestDimNamedFilters);
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

    loadDimension : function(selections, altRequestDimNamedFilters) {
        var hierarchies = this.dim.getHierarchies();
        var distinctLevel = this.dim.distinctLevel;

        if (hierarchies.length > 0) {
            var hierarchy = hierarchies[this.hIndex];
            var uniqueName = hierarchy.getUniqueName();
            var me = this;
            if (!this.totals[uniqueName]) {
                // Asks for Total Count
                this.olapProvider.onMDXReady(function(mdx) {
                    me.mdx = mdx;

                    var queryConfig = {
                        onRows: [{hierarchy: uniqueName, members:'members'}],
                        showEmpty: me.showEmpty,
                        success: function(qr) {
                            me.setTotalHierarchyMembers(uniqueName, qr);
                            me.totals[uniqueName] = me.processMaxCount.call(me, qr);
                            me.requestDimension(hierarchy, selections, distinctLevel, altRequestDimNamedFilters);
                        }
                    };

                    if (Ext.isString(this.perspective)) {
                        queryConfig.perspective = this.perspective;
                    }

                    if (Ext.isDefined(distinctLevel)) {
                        queryConfig.countDistinctLevel = distinctLevel;
                    }

                    mdx.query(queryConfig);

                }, this);
            }
            else {
                me.requestDimension(hierarchy, selections, distinctLevel, altRequestDimNamedFilters);
            }
        }
    },

    /**
     * A function to allow preprocessing the requested hierarchy with its full cube members.
     * @param hierarchyUniqueName
     * @param cubeResult The query result of the hierarchy with no filters passed in (except container filter)
     */
    setTotalHierarchyMembers:  Ext.emptyFn, //Subclass to override.

    requestDimension : function(hierarchy, selections, distinctLevel, altRequestDimNamedFilters) {
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
                useSelection: hasSelection,
                mdx: mdx
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

            var queryConfig = {
                onRows : [{
                    hierarchy: hierarchy.getUniqueName(),
                    members: 'members'
                }],
                useNamedFilters: altRequestDimNamedFilters || ['statefilter'],
                showEmpty: me.showEmpty,
                qFlight: this.flight,
                success: function(qr, _mdx, x) {
                    if (this.flight === x.qFlight) {
                        scoped.baseResult = qr;
                        check();
                    }
                },
                scope: this
            };

            if (Ext.isString(this.perspective)) {
                queryConfig.perspective = this.perspective;
            }

            if (Ext.isDefined(distinctLevel)) {
                queryConfig.countDistinctLevel = distinctLevel;
            }

            var config = this.appendAdditionalQueryConfig(queryConfig);
            mdx.query(config);

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

    appendAdditionalQueryConfig : function(config) {
        // overrides can add additional properties (ex. joinLevel and whereFilter)
        return config;
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

        var hierarchy = this.dim.getHierarchies()[this.hIndex],
                baseResult = response.baseResult,
                dims = baseResult.metadata.cube.dimensions,
                selectionResult = response.selectionResult,
                targetLevels = dims.length > 1 ? dims[1].hierarchies[0].levels : hierarchy.levels,
                max = this.totals[hierarchy.getUniqueName()],
                target,
                pos = baseResult.axes[1].positions,
                activeGroup = '',
                isGroup = false,
                groupTarget,
                hasSubjectLevel = targetLevels[targetLevels.length-1].name === this.subjectName,
                hasGrpLevel = targetLevels.length > (hasSubjectLevel ? 3 : 2),
                grpLevelID = targetLevels[1] ? targetLevels[1].id : null,
                subPosition,
                customGroups = {},
                groupRecords = [],
                childRecords = [],
        //
        // Support for 'sortStrategy' being declared on the MDX.Level. See this app's cube metadata documentation
        // to see if this app supports the 'sortStrategy' be declared.
        //
                sortStrategy = 'SERVER',
                sortLevelUniqueName,
                sortLevel;

        if (hasGrpLevel) {
            Ext.each(targetLevels, function(level) {
                if (level.id === grpLevelID) {
                    sortLevelUniqueName = level.uniqueName;
                    return false;
                }
            });
        }
        else {
            sortLevelUniqueName = targetLevels[targetLevels.length-1].uniqueName;
        }

        sortLevel = response.mdx.getLevel(sortLevelUniqueName);
        if (sortLevel && !Ext.isEmpty(sortLevel.sortStrategy)) {
            sortStrategy = sortLevel.sortStrategy;
        }

        // skip (All)
        for (var x=1; x < pos.length; x++) {
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

            if (!this.shouldIncludeMember(hierarchy.getUniqueName(), subPosition.level.uniqueName, subPosition.uniqueName))
                continue;

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
                groupRecords.push(instance);
            }
            else {
                instance.set('level', activeGroup);
                if (!customGroups[activeGroup]) {
                    customGroups[activeGroup] = [];
                }
                customGroups[activeGroup].push(instance);
                childRecords.push(instance);
            }

            var collapse = this.checkCollapse(instance.data);
            instance.set('collapsed', collapse);

            if (groupTarget) {
                groupTarget.set('collapsed', collapse);
            }
        }

        var groupOnly = true;
        for (var i=0; i < childRecords.length; i++) {
            if (!childRecords[i].get('isGroup')) {
                groupOnly = false;
                break;
            }
        }

        if (groupOnly) {
            max = 0;
            this.removeAll();
        }
        else {
            this.loadRecords(this._applySort(sortStrategy, groupRecords, childRecords, customGroups));
        }

        this.customGroups = customGroups;
        this.maxCount = max;

        if (response.useSelection) {
            this.fireEvent('selectrequest');
        }
    },

    /**
     * Determine if a member should be included or filtered out. False return value will skip the member.
     * @param hierarchyUniqName
     * @param levelUniquName
     * @param memberName
     * @returns {boolean}
     */
    shouldIncludeMember: function(hierarchyUniqName, levelUniquName, memberName) {
        return true; //subclass to override
    },

    /**
     * The purpose of this method is to do a two-level sort where the groups are sorted first, followed
     * by the associated children being sorted in turn.
     */
    _applySort : function(sortStrategy, groupRecords, childRecords, groupMap) {
        var sorted = [], children;

        var sortFn = this._resolveSortFunction(sortStrategy);

        if (Ext.isEmpty(groupRecords)) {
            if (sortFn) {
                childRecords.sort(sortFn);
            }
            sorted = childRecords;
        }
        else {
            if (sortFn) {
                groupRecords.sort(sortFn);
            }
            Ext.each(groupRecords, function(group) {
                sorted.push(group);
                children = groupMap[group.get('level')];
                if (!Ext.isEmpty(children)) {
                    if (sortFn) {
                        children.sort(sortFn);
                    }
                    sorted = sorted.concat(children);
                }
            });
        }

        return sorted;
    },

    /**
     * Resolve the sorting function to use based on the given 'strategy' parameter. Currently, supports
     * 'ALPHANUM', 'ALPHANUM-RANGE', 'NATURAL', and 'SERVER'. This function can return the boolean 'false' in the case of no-op
     * strategy or if the strategy is not found.
     * @param strategy
     * @returns {*}
     * @private
     */
    _resolveSortFunction : function(strategy) {
        switch (strategy) {
            case 'ALPHANUM':
                return LABKEY.olapStore.sorters.sortAlphaNum;
            case 'ALPHANUM-RANGE':
                return LABKEY.olapStore.sorters.sortAlphaNumRange;
            case 'NATURAL':
                return LABKEY.olapStore.sorters.sortNatural;
            case 'SERVER':
            default:
                return false;
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

        if (Ext.isDefined(this.dim)) {
            var queryConfig = {
                onRows : [{
                    hierarchy: this.dim.getHierarchies()[this.hIndex].getUniqueName(),
                    members: 'members'
                }],
                useNamedFilters: ['stateSelectionFilter', 'hoverSelectionFilter', 'statefilter'],
                mflight: mflight,
                showEmpty: this.showEmpty,
                success: callback,
                scope : scope
            };

            if (Ext.isString(this.perspective)) {
                queryConfig.perspective = this.perspective;
            }

            if (Ext.isDefined(this.dim.distinctLevel)) {
                queryConfig.countDistinctLevel = this.dim.distinctLevel;
            }

            var config = this.appendAdditionalQueryConfig(queryConfig);
            this.mdx.query(config);
        }
    },

    loadSelection : function() {
        if (this.enableSelection) {
            // asks for the sub-selected portion
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
                        return this.setLoadSelection(rec, cellset);
                    }, this);
                this.resumeEvents();
            }

            this.fireEvent('subselect', this);
        }
    },

    setLoadSelection: function(rec, cellset) {
        rec.set({
            subcount: this._calculateSubcount(cellset, rec.get('uniqueName')),
            hasSelect: true
        });
        return true;
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

    barExpandHeight: 27,

    altRequestDimNamedFilters : undefined,

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
                this.store.load(this.dimension, this.hierarchyIndex, this.selections, this.showEmpty, this.altRequestDimNamedFilters);
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
        Ext.each(Ext.DomQuery.select('.animator'), function(a) {
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
                '{[ this.renderBars(values) ]}',
                '</div>',
                '</tpl>',
                '</tpl>',
                '</div>',
                '</div>',
                {
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
            this.toggleGroup(groups[f]);
            if (this.resizeTask)
                this.resizeTask.delay(100);
        }
    },

    toggleGroup : function(children) {
        var animConfig, current, ext,
                first = true,
                listeners,
                node,
                me = this, c, child;
        this.store.suspendEvents();

        for (c=0; c < children.length; c++) {

            child = children[c];

            if (!child.data.isGroup) {

                node = this.getNodeByRecord(child);
                ext = Ext.get(node);
                current = ext.getActiveAnimation();
                if (current)
                    ext.stopAnimation();
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
                        to : {opacity: 1, height: this.barExpandHeight},
                        setDisplay : 'block',
                        collapsed : false,
                        sign  : '-',
                        scope : this
                    };
                }

                if (c == children.length-1)
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

                ext.animate(animConfig);

                if (first)
                {
                    var prev = ext.prev().child('.saecollapse');
                    prev.update('<p unselectable="on">' + animConfig.sign + '</p>');
                    first = false;
                }

                child.data.collapsed = animConfig.collapsed;
                me.store.setCollapse(child.data, animConfig.collapsed);
            }
            else if (children[c+1] && !children[c+1].data.isGroup)
            {
                child = children[c+1];
                node = this.getNodeByRecord(child);
                ext = Ext.get(node);

                var prev = ext.prev().child('.saecollapse');
                if (prev) {
                    prev.update('<p unselectable="on">' + (child.data.collapsed ? '+' : '-') + '</p>');
                }
            }
        }
        this.store.resumeEvents();
    },

    selectionChange : function(sel, isPrivate) {
        this.selections = sel;
        if (this.dimension && this.store.KEYED_LOAD === true) {
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
    },

    filterChange : function() {
        this.loadStore();
    },

    selection : function() {
        if (this.selectRequest || !Ext.isEmpty(this.selections)) {
            this.store.loadSelection();
        }
        else {
            this.getSelectionModel().deselectAll();
            this.store.clearSelection();
        }
    },

    highlightSelections : function() {
        if (!Ext.isEmpty(this.selections)) {
            var members, uniques = [];
            Ext.each(this.selections, function(sel) {

                // initialize members
                if (Ext.isFunction(sel.get)) {
                    members = sel.get('members');
                }
                else if (Ext.isArray(sel.arguments)) {
                    members = sel.arguments[0].membersQuery.members;
                }
                else {
                    members = sel.membersQuery.members;
                }

                uniques = uniques.concat(Ext.Array.pluck(members, 'uniqueName'));
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
