/*
 * Copyright (c) 2014-2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext.define('LABKEY.app.constant', {
    singleton: true,
    STATE_FILTER: 'statefilter',
    SELECTION_FILTER: 'stateSelectionFilter'
});

Ext.define('LABKEY.app.model.State', {

    extend : 'Ext.data.Model',

    fields : [
        {name : 'name'},
        {name : 'activeView'},
        {name : 'viewState'},
        {name : 'customState'},
        {name : 'filters'},
        {name : 'selections'},
        {name : 'detail'}
    ],

    proxy : {
        type : 'sessionstorage',
        id   : 'connectorStateProxy'
    }
});

Ext.define('LABKEY.app.controller.State', {

    extend : 'Ext.app.Controller',

    requires : [
        'LABKEY.app.model.State'
    ],

    preventRedundantHistory: true,

    subjectName: '',

    _ready: false,

    supportColumnServices: false,

    /**
     * Flag that determines if filters should use the merge strategy when joining groups of filters.
     * This can be overridden by subclasses if different behavior is desired.
     * @type {boolean} useMergeFilters
     */
    useMergeFilters: false,

    olap: undefined,

    init : function() {

        if (LABKEY.devMode) {
            STATE = this;
        }

        if (this.application.olap) {
            this.olap = this.application.olap;
        }

        this.callbackCache = [];

        if (LABKEY.devMode) {
            this.onMDXReady(function(mdx) { MDX = mdx; });
        }

        this.state = Ext.create('Ext.data.Store', {
            model : 'LABKEY.app.model.State'
        });

        this.customState = {};
        this.filters = []; this.selections = [];
        this.privatefilters = {};

        if (this.supportColumnServices) {
            this.initColumnService();
        }

        this.addStateNameFilter();

        this.state.load();

        this.application.on('route', function() { this.loadState(); }, this, {single: true});
    },

    addStateNameFilter : function() {
        // issue 22475: if we have multiple containers used in this session, use a state filter to remove records that don't match the name provided
        var stateNameFilter = this.getStateFilterName();
        if (stateNameFilter != null) {
            this.state.on('load', function (store, records) {
                for (var i = 0; i < records.length; i++)
                {
                    var rec = records[i];
                    if (stateNameFilter != rec.get('name')) {
                        store.remove(rec);
                    }
                }

                if (store.getCount() != records.length) {
                    this._sync();
                }
            }, this, {single: true});
        }
    },

    /**
     * Provided to be overridden to provide a way to filter the state store on load
     * @returns {name}
     */
    getStateFilterName : function() {
        return null;
    },

    setDataSource : function(olap) {
        this.olap = olap;
        Ext.each(this.callbackCache, function(onReadyObj) {
            this.olap.onReady(onReadyObj.fn, onReadyObj.scope);
        }, this);
        this.callbackCache = [];
    },

    loadDataSource : function() {
        if (this.olap) {
            this.olap.load();
        }
        else {
            console.error('Unable to loadDataSource(). Not defined.');
        }
    },

    getCurrentState : function() {
        var c = this.state.getCount();
        if (c > 0) {
            return this.state.getAt(c-1);
        }
    },

    getPreviousState : function() {
        var index = -1, c = this.state.getCount();
        if (c > 1) {
            index = c-2;
        }
        return index;
    },

    onMDXReady : function(callback, scope) {
        var s = scope || this;
        if (this.olap) {
            this.olap.onReady(callback, s);
        }
        else {
            this.callbackCache.push({fn: callback, scope: s});
        }
    },

    isMDXReady : function() {
        return this.olap && this.olap._isReady === true;
    },

    loadState : function(idx) {

        if (!idx) {
            idx = this.state.getCount()-1; // load most recent state
        }

        if (idx >= 0) {
            var s = this.state.getAt(idx).data;

            // Apply state
            Ext.apply(this, s.viewState);

            if (s.customState) {
                this.customState = s.customState;
            }

            // Apply Filters
            if (Ext.isArray(s.filters) && s.filters.length > 0) {

                var _filters = [];
                Ext.each(s.filters, function(_f) {
                    _filters.push(_f);
                });

                this.setFilters(_filters, true);
            }

            // Apply Selections
            if (s.selections && s.selections.length > 0) {
                this.setSelections(s.selections, true);
            }
        }

        this.manageState();

        this._ready = true;
        this.checkReady();
    },

    /**
     * Can be overridden to allow for other services to block on state being ready.
     * When actually ready, call this.fireReady().
     */
    checkReady : function() {
        this.fireReady();
    },

    fireReady : function() {
        if (this._ready === true) {
            this.application.fireEvent('stateready', this);
        }
    },

    onReady : function(callback, scope) {
        if (Ext.isFunction(callback)) {
            if (this._ready === true) {
                callback.call(scope, this);
            }
            else {
                this.application.on('stateready', function() {
                    callback.call(scope, this);
                }, this, {single: true});
            }
        }

    },

    manageState : function() {
        var size = this.state.getCount(), limit = 5;
        if (size > limit) {
            this.state.removeAt(0, size-limit);
            this._sync();
        }
    },

    /**
     * Managed sync that attempts to recover in case the storage fails.
     * Ideally, this should be pushed down into the extended store instance in it's own
     * sync method.
     * @param records
     * @private
     */
    _sync : function(records) {

        try
        {
            if (Ext.isArray(records)) {
                this.state.add(records);
            }
            this.state.sync();
        }
        catch (e) // QuotaExceededError
        {
            if (this.__LOCK__ !== true) {
                this.__LOCK__ = true;
                this.manageState();
                this._sync(records);
                this.__LOCK__ = false;
            }
        }
    },

    getState : function(lookup, defaultState) {
        if (this.state.getCount() > 0) {
            var s = this.state.getAt(this.state.getCount()-1);
            if (s.customState && s.customState[lookup.view]) {
                if (s.customState[lookup.view].hasOwnProperty(lookup.key)) {
                    return s.customState[lookup.view][lookup.key];
                }
            }
        }
        return defaultState;
    },

    findState : function(fn, scope, startIndex) {

        if (this.state.getCount() > 0) {
            var idx = this.state.getCount() - 1;
            var _scope = scope || this;

            if (startIndex && startIndex < idx)
                idx = startIndex;

            var rec = this.state.getAt(idx).data;
            while (!fn.call(_scope, idx, rec) && idx > 0) {
                idx--;
                rec = this.state.getAt(idx).data;
            }
            return idx;
        }
        return -1;
    },

    setCustomState : function(lookup, state) {
        if (!this.customState.hasOwnProperty(lookup.view))
            this.customState[lookup.view] = {};
        this.customState[lookup.view][lookup.key] = state;
    },

    getCustomState : function(view, key) {
        var custom = undefined;
        if (this.customState.hasOwnProperty(view)) {
            custom = this.customState[view][key];
        }
        return custom;
    },

    /**
     * Provided to be overridden to provide a custom title for view states.
     * @param viewname
     * @returns {*}
     */
    getTitle : function(viewname) {
        return viewname;
    },

    updateState : function() {

        // prepare filters
        var jsonReadyFilters = [];
        Ext.each(this.filters, function(f) {
            jsonReadyFilters.push(f.jsonify());
        });

        // prepare selections
        var jsonReadySelections = [];
        Ext.each(this.selections, function(s) {
            jsonReadySelections.push(s.jsonify());
        });

        this._sync([{
            name: this.getStateFilterName(),
            viewState: {},
            customState: this.customState,
            filters: jsonReadyFilters,
            selections: jsonReadySelections
        }]);
    },

    /**
     * This method allows for updating a filter that is already being tracked.
     * Given a filter id, the datas parameter will replace that filter's value for
     * the given key in datas. Note: This will only replace those values specified
     * leaving all other values on the filter as they were.
     * @param id
     * @param datas
     */
    updateFilter : function(id, datas) {

        Ext.each(this.filters, function(filter) {
            if (filter.id === id) {

                Ext.iterate(datas, function(key, val) {
                    filter.set(key, val);
                });

                filter.commit();

                this.requestFilterUpdate(false);
            }
        }, this);
    },

    /**
     * This method allows for updating a selection that is already being tracked.
     * Given a selection id, the datas parameter will replace that selection's value for
     * the given key in datas. Note: This will only replace those values specified
     * leaving all other values on the selection as they were.
     * @param id
     * @param datas
     */
    updateSelection : function(id, datas) {

        Ext.each(this.selections, function(selection) {
            if (selection.id === id) {

                Ext.iterate(datas, function(key, val) {
                    selection.set(key, val);
                });

                selection.commit();

                this.requestSelectionUpdate(false);
            }
        }, this);
    },

    _is : function(filterset, id) {
        var found = false;
        Ext.each(filterset, function(f) {
            if (id === f.id) {
                found = true;
                return false;
            }
        });
        return found;
    },

    isFilter : function(id) {
        return this._is(this.filters, id);
    },

    isSelection : function(id) {
        return this._is(this.selections, id);
    },

    /**
     * You must call updateFilterMembersComplete() once done updating filter members
     * @param id
     * @param members
     */
    updateFilterMembers : function(id, members) {
        for (var f=0; f < this.filters.length; f++) {
            if (this.filters[f].id == id)
            {
                this.filters[f].set('members', members);
            }
        }
    },

    updateFilterMembersComplete : function(skipState, callback, scope) {
        this.requestFilterUpdate(skipState, false, true, callback, scope);
        // since it is silent we need to update the count seperately
        this.updateFilterCount();
    },

    getFilters : function(flat) {
        if (!this.filters || this.filters.length == 0)
            return [];

        if (!flat)
            return this.filters;

        var flatFilters = [],
            f = 0,
            data;

        for (; f < this.filters.length; f++) {
            data = Ext.clone(this.filters[f].data);
            flatFilters.push(data);
        }

        return flatFilters;
    },

    getFilterModelName : function() {
        console.error('Failed to get filter model name.');
    },

    _getFilterSet : function(filters) {

        var newFilters = [],
            filterClass = this.getFilterModelName(),
            f, s, data;

        for (s = 0; s < filters.length; s++) {
            f = filters[s];

            // decipher object structure
            if (!f.$className) {
                data = f.data ? f.data : f;
                newFilters.push(Ext.create(filterClass, data));
            }
            else if (f.$className == filterClass) {
                newFilters.push(f);
            }
        }
        return newFilters;

    },

    hasFilters : function() {
        return this.filters.length > 0;
    },

    /**
     * Adds a LABKEY.app.model.Filter to the current state
     * @param {LABKEY.app.model.Filter} filter Filter that will be added to the state
     * @param {boolean} [skipState=false] Flag if this action should cause the state to update
     * @returns {*}
     */
    addFilter : function(filter, skipState) {
        return this.addFilters([filter], skipState);
    },

    /**
     * Adds the array of 'filters' to the current state
     * @param {LABKEY.app.model.Filter[]} filters Filters that will be added to the state
     * @param {boolean} [skipState=false] Flag if this action should cause the state to update
     * @param {boolean} [clearSelection=false] Flag if adding filters should also clear the selection state
     * @param callback
     * @param scope
     * @returns {*}
     */
    addFilters : function(filters, skipState, clearSelection, callback, scope) {
        var _f = this.getFilters();
        if (!_f)
            _f = [];

        var newFilters = this._getFilterSet(filters);

        this.filters = this._mergeFilters(_f, newFilters);

        if (clearSelection) {
            // explicitly skipState since only one state update should occur
            // when modifying filters. Effectively merging the two actions
            // of clearing selections and adding filters into one update
            this.clearSelections(true /* skipState */);
        }

        this.requestFilterUpdate(skipState, false, false, callback, scope);

        return newFilters;
    },

    prependFilter : function(filter, skipState, callback, scope) {
        this.setFilters([filter].concat(this.filters), skipState, callback, scope);
    },

    /**
     * This helper function will merge or concatenate filters depending on the
     * 'useMergeFilters' flag. Utilizes the LABKEY.app.model.Filter canMerge()
     * and merge() functionality to merge like-filters.
     * @param {LABKEY.app.model.Filter[]} oldFilters Filters that will be merged into
     * @param {LABKEY.app.model.Filter[]} newFilters Filters that will be merged from
     * @returns {LABKEY.app.model.Filter[]}
     * @private
     */
    _mergeFilters : function(oldFilters, newFilters) {

        var filters = oldFilters,
            nf, merged;

        if (this.useMergeFilters) {
            // see if each new filter can be merged, if not just append it
            for (var n=0; n < newFilters.length; n++) {
                nf = newFilters[n];
                merged = false;

                for (var i=0; i < filters.length && !merged; i++) {
                    if (nf.canMerge(filters[i])) {
                        filters[i].merge(nf);
                        merged = true;
                    }
                }

                if (!merged) {
                    filters.push(nf);
                }
            }
        }
        else {
            // new filters are always appended
            filters = filters.concat(newFilters);
        }

        return filters;
    },

    loadFilters : function(stateIndex) {
        var previousState =  this.state.getAt(stateIndex);
        if (Ext.isDefined(previousState)) {
            var filters = previousState.get('filters');
            this.setFilters(filters);
        }
        else {
            console.warn('Unable to find previous filters: ', stateIndex);
        }
    },

    setFilters : function(filters, skipState, callback, scope) {
        this.filters = this._getFilterSet(filters);
        this.requestFilterUpdate(skipState, false, false, callback, scope);
    },

    clearFilters : function(skipState) {
        this.filters = [];
        this.requestFilterUpdate(skipState, false);
    },

    _removeHelper : function(target, filterId, hierarchyName, uniqueName) {

        var filterset = [];
        for (var t=0; t < target.length; t++) {

            if (target[t].id != filterId) {
                filterset.push(target[t]);
            }
            else {

                // Check if removing group/grid
                if (target[t].isGrid() || target[t].isPlot())
                    continue;

                // Found the targeted filter to be removed
                var newMembers = target[t].removeMember(uniqueName);
                if (newMembers.length > 0) {
                    target[t].set('members', newMembers);
                    filterset.push(target[t]);
                }
            }
        }

        return filterset;
    },

    removeFilter : function(filterId, hierarchyName, uniqueName) {
        var filters = this.getFilters();
        var fs = this._removeHelper(filters, filterId, hierarchyName, uniqueName);

        if (fs.length > 0) {
            this.setFilters(fs);
        }
        else {
            this.clearFilters();
        }

        this.fireEvent('filterremove', this.getFilters());
    },

    removeSelection : function(filterId, hierarchyName, uniqueName) {

        var ss = this._removeHelper(this.selections, filterId, hierarchyName, uniqueName);

        if (ss.length > 0) {
            this.addSelection(ss, false, true, true);
        }
        else {
            this.clearSelections(false);
        }

        this.fireEvent('selectionremove', this.getSelections());
    },

    addGroup : function(grp) {
        if (grp.data.filters) {
            var filters = grp.data.filters;
            for (var f=0; f < filters.length; f++) {
                filters[f].groupLabel = grp.data.label;
            }
            this.addPrivateSelection(grp.data.filters, 'groupselection');
        }
    },

    setFilterOperator : function(filterId, value) {
        for (var s=0; s < this.selections.length; s++) {
            if (this.selections[s].id == filterId) {
                this.selections[s].set('operator', value);
                this.requestSelectionUpdate(false, true);
                return;
            }
        }

        for (s=0; s < this.filters.length; s++) {
            if (this.filters[s].id == filterId) {
                this.filters[s].set('operator', value);
                this.requestFilterUpdate(false, true);
                return;
            }
        }
    },

    configureCacheListener : function(mdx) {
        if (mdx.allowMemberCaching()) {
            this.on('filterclear', function() {
                var sets = mdx.serverGetNamedSets();
                Ext.iterate(sets, function(key) {
                    mdx.serverDeleteNamedSet(key);
                }, this);
            });
        }
    },

    requestFilterUpdate : function(skipState, opChange, silent, callback, scope) {

        this.onReady(function() { // wtb promises
            this.onMDXReady(function(mdx) {

                var olapFilters = [];
                Ext.each(this.filters, function(ff) {
                    olapFilters.push(ff.getOlapFilter(mdx, this.subjectName));
                }, this);

                var proceed = true;
                Ext.each(olapFilters, function(of) {
                    if (!of.getData && of.arguments.length == 0) {
                        alert('EMPTY ARGUMENTS ON FILTER');
                        proceed = false;
                    }
                });

                if (proceed) {
                    if (olapFilters.length == 0) {
                        mdx.clearNamedFilter(LABKEY.app.constant.STATE_FILTER);
                    }
                    else {
                        mdx.setNamedFilter(LABKEY.app.constant.STATE_FILTER, olapFilters);
                    }

                    this._filterUpdateHelper(skipState, silent, callback, scope);
                }

            }, this);
        }, this);
    },

    /**
     * @private
     */
    _filterUpdateHelper : function(skipState, silent, callback, scope) {
        if (!skipState) {
            this.updateState();
        }

        if (Ext.isFunction(callback)) {
            callback.call(scope || this);
        }

        if (!silent) {
            this.fireEvent('filterchange', this.filters);
            if (this.filters.length == 0) {
                this.fireEvent('filterclear');
            }
        }
    },

    getSelections : function(flat) {
        if (!this.selections || this.selections.length == 0)
            return [];

        if (!flat)
            return this.selections;

        var flatSelections = [];
        for (var f=0; f < this.selections.length; f++) {
            flatSelections.push(this.selections[f].data);
        }

        return flatSelections;
    },

    hasSelections : function() {
        return this.selections.length > 0;
    },

    mergeFilters : function(newFilters, oldFilters, opFilters) {

        var match;
        for (var n=0; n < newFilters.length; n++) {

            match = false;
            for (var i=0; i < oldFilters.length; i++) {

                if (this.shouldMergeFilters(oldFilters[i], newFilters[n])) {

                    this.handleMergeRangeFilters(oldFilters[i], newFilters[n]);

                    for (var j=0; j < newFilters[n].data.members.length; j++) {

                        match = true;

                        if (!this.isExistingMemberByUniqueName(oldFilters[i].data.members, newFilters[n].data.members[j]))
                            oldFilters[i].data.members.push(newFilters[n].data.members[j]);
                    }
                }
            }

            // did not find match
            if (!match) {
                oldFilters.push(newFilters[n]);
            }
        }

        // Issue: 15359
        if (Ext.isArray(opFilters)) {

            for (n=0; n < opFilters.length; n++) {

                for (var i=0; i < oldFilters.length; i++) {

                    if (oldFilters[i].getHierarchy() == opFilters[n].getHierarchy()) {
                        var op = opFilters[n].data;
                        if (!LABKEY.app.model.Filter.dynamicOperatorTypes) {
                            op = LABKEY.app.model.Filter.lookupOperator(op);
                        }
                        else {
                            op = op.operator;
                        }
                        oldFilters[i].set('operator', op);
                    }
                }
            }
        }

        return oldFilters;
    },

    shouldMergeFilters : function(oldFilter, newFilter) {
        return (oldFilter.data.hierarchy == newFilter.data.hierarchy);
    },

    handleMergeRangeFilters : function(oldFilter, newFilter) {
        // if the old filter is a member list and the new filter is a range, drop the range from new filter and merge will be a member list
        // if the old filter is a range and the new filter is a member list, drop the range from old filter and merge will be a member list
        // else concatenate the array of range filters for the old and new filters (note: most cases will result in empty array)
        if (oldFilter.getRanges().length == 0 && newFilter.getRanges().length > 0)
            newFilter.set('ranges', []);
        else if (oldFilter.getRanges().length > 0 && newFilter.getRanges().length == 0)
            oldFilter.set('ranges', []);
        else
            oldFilter.set('ranges', oldFilter.getRanges().concat(newFilter.getRanges()));
    },

    isExistingMemberByUniqueName : function(memberArray, newMember) {
        // issue 19999: don't push duplicate member if reselecting
        for (var k = 0; k < memberArray.length; k++)
        {
            if (!memberArray[k].hasOwnProperty("uniqueName") || !newMember.hasOwnProperty("uniqueName"))
                continue;

            if (memberArray[k].uniqueName == newMember.uniqueName)
                return true;
        }

        return false;
    },

    /**
     * Returns the set of filters found in newFilters that were not present in oldFilters.
     * Think of it as newFilters LEFT OUTER JOIN oldFilters
     * @param newFilters
     * @param oldFilters
     */
    pruneFilters : function(newFilters, oldFilters) {
        // 15464
        var prunedSelections = [], found;
        for (var s=0; s < newFilters.length; s++) {
            found = false;
            for (var f=0; f < oldFilters.length; f++) {
                if (newFilters[s].isEqual(oldFilters[f])) {
                    found = true;
                }
            }
            if (!found) {
                prunedSelections.push(newFilters[s]);
            }
        }
        return prunedSelections;
    },

    addSelection : function(selections, skipState, merge, clear) {

        var newSelectors = this._getFilterSet(selections);
        var oldSelectors = this.selections;

        /* First check if a clear is requested*/
        if (clear) {
            this.selections = [];
        }

        /* Second Check if a merge is requested */
        if (merge) {
            this.selections = this.mergeFilters(newSelectors, this.selections, oldSelectors);
        }
        else {
            this.selections = newSelectors;
        }

        this.requestSelectionUpdate(skipState, false);
    },

    updateFilterCount : function() {
        this.fireEvent('filtercount', this.filters);
    },

    requestSelectionUpdate : function(skipState, opChange) {

        this.onMDXReady(function(mdx) {

            var sels = [];

            for (var s=0; s < this.selections.length; s++) {
                // construct the query
                sels.push(this.selections[s].getOlapFilter(mdx, this.subjectName));
            }

            if (sels.length == 0) {
                mdx.clearNamedFilter(LABKEY.app.constant.SELECTION_FILTER);
            }
            else {
                mdx.setNamedFilter(LABKEY.app.constant.SELECTION_FILTER, sels);
            }

            if (!skipState)
                this.updateState();

            this.fireEvent('selectionchange', this.selections, opChange);

        }, this);
    },

    // NOTE: This is overridden in subclasses
    moveSelectionToFilter : function() {
        this.addFilters(this.pruneFilters(this.selections, this.filters), false, true);
    },

    getPrivateSelection : function(name) {
        return this.privatefilters[name];
    },

    addPrivateSelection : function(selection, name, callback, scope) {

        this.onMDXReady(function(mdx){

            var filters = [];
            if (Ext.isArray(selection))
            {
                var newSelectors = [];
                for (var s=0; s < selection.length; s++) {

                    if (!selection[s].$className)
                        newSelectors.push(Ext.create(this.getFilterModelName(), selection[s]));
                    else if (selection[s].$className && selection[s].$className == this.getFilterModelName())
                        newSelectors.push(selection[s]);
                }

                this.privatefilters[name] = newSelectors;

                for (s=0; s < newSelectors.length; s++) {
                    filters.push(newSelectors[s].getOlapFilter(mdx, this.subjectName));
                }
            }

            if (Ext.isArray(selection))
            {
                mdx.setNamedFilter(name, filters);
                this.fireEvent('privateselectionchange', mdx._filter[name], name);
            }
            else
            {
                // TODO: This is wrong for when working with perspectives
                mdx.setNamedFilter(name, [{
                    hierarchy : this.subjectName,
                    membersQuery : selection
                }]);
            }

            if (Ext.isFunction(callback)) {
                callback.call(scope || this);
            }

        }, this);
    },

    removePrivateSelection : function(name) {
        var me = this;
        this.onMDXReady(function(mdx){

            mdx.setNamedFilter(name, []);
            me.privatefilters[name] = undefined;
            me.fireEvent('privateselectionchange', [], name);

        }, this);
    },

    clearSelections : function(skipState) {
        if (this.selections.length > 0) {
            this.selections = [];
            this.requestSelectionUpdate(skipState, false);
        }
    },

    setSelections : function(selections, skipState) {
        this.addSelection(selections, skipState);
    },

    /*** Column Services ***/
    initColumnService : function() {

        this.SESSION_COLUMNS = {};

        this.initColumnListeners();
    },

    /* Meant to be overridden with listeners specific to the app */
    initColumnListeners : function() {},

    addSessionColumn : function(column) {
        if (column && Ext.isString(column.alias) && !this.SESSION_COLUMNS[column.alias]) {
            this.SESSION_COLUMNS[column.alias] = column;
        }
    },

    /* WARNING: Not currently clone safe */
    getSessionColumns : function() {
        return this.SESSION_COLUMNS;
    }
});
