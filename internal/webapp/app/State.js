/*
 * Copyright (c) 2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext.define('LABKEY.app.model.State', {

    extend : 'Ext.data.Model',

    fields : [
        {name : 'activeView'},
        {name : 'viewState'},
        {name : 'views'},
        {name : 'filters'}, //,    type : Ext.data.Types.FILTER},
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

    init : function() {
        if (LABKEY.devMode) {
            STATE = this;
        }
        this.olap = this.application.olap;

        this.state = Ext.create('Ext.data.Store', {
            model : 'LABKEY.app.model.State'
        });

        this.viewController = this._getViewController();

        this.viewController.on('afterchangeview', this.afterViewChange, this);

        this.views = {};
        if (this.preventRedundantHistory) {
            this.lastAppState = '';
        }

        this.filters = []; this.selections = [];
        this.privatefilters = {};

//        if (Ext.supports.History) {
//            var me = this;
//            window.addEventListener('popstate', function(evt) {
//                me._popState(evt);
//            }, false);
//        }

        this.state.load();
    },

    _getViewController : function() {
        console.error('Failed to register a view controller.');
    },

    /**
     * @private
     * The listener method for when the state is popped by the window object (Browser back button).
     * See https://developer.mozilla.org/en/DOM/window.onpopstate
     * @param evt
     */
    _popState : function(evt) {
        if (evt && evt.state && evt.state.activeView) {
            this.viewController.changeView(evt.state.activeView, [], this.defaultTitle, true);
        }
        else {
            // still in our history -- go back to beginning
            this.viewController.changeView(this.defaultView, [], this.defaultTitle);
        }
    },

    getCurrentState : function() {
        if (this.state.getCount() > 0) {
            return this.state.getAt(this.state.getCount()-1);
        }
    },

    getPreviousState : function() {
        var index = -1;
        if (this.state.getCount() > 1) {
            index = this.state.getCount()-2;
        }
        return index;
    },

    onMDXReady : function(callback, scope) {
        var s = scope || this;
        this.olap.onReady(callback, s);
    },

    loadState : function(controller, view, viewContext, idx, useLast, popState) {

        if (!controller && !view) {
            controller = this.defaultController;
        }

        if (popState) {
            this.POP_STATE = true;
        }

        if (!idx) {
            idx = this.state.getCount()-1; // load most recent state
        }

        if (idx >= 0) {
            var s = this.state.getAt(idx).data;

            // Apply state
            Ext.apply(this, s.viewState);

            if (s.views) {
                this.views = s.views;
            }

            // Apply Filters
            if (s.filters && s.filters.length > 0) {

                // TODO: Remove this an apply grid filters properly from state
                var nonGridFilters = [];
                for (var f=0; f < s.filters.length; f++) {
                    if (s.filters[f] && !s.filters[f].isGrid)
                        nonGridFilters.push(s.filters[f]);
                }

                this.setFilters(nonGridFilters, true);
            }

            // Activate view
            this._controller = controller;
            this._view = view;
            this._viewContext = viewContext;

            // Change view and do not save state since a prior state is being loaded.
            this.viewController._changeView(controller, view, viewContext, this.defaultTitle, true, false);

            // Apply Selections
            if (s.selections && s.selections.length > 0) {
                this.setSelections(s.selections, true);
            }

            if (s.detail) {
                console.warn('would have set the details');
//                this.setDetail(s.detail);
            }
        }
        else if (useLast) {

            // Activate view
            this._controller = controller;
            this._view = view;
            this._viewContext = viewContext;

            this.viewController._changeView(controller, view, viewContext, this.defaultTitle, true, false);
        }

        this.manageState();
    },

    manageState : function() {
        var size = this.state.getCount();
        if (size > 20) {
            var recs = this.state.getRange(size-10, size-1);
            this.state.removeAll();
            this.state.sync();
            this.state.getProxy().clear();
            this.state.add(recs);
            this.state.sync();
        }
    },

    getState : function(lookup, defaultState) {
        if (this.state.getCount() > 0) {
            var s = this.state.getAt(this.state.getCount()-1);
            if (s.views && s.views[lookup.view]) {
                if (s.views[lookup.view].hasOwnProperty(lookup.key)) {
                    return s.views[lookup.view][lookup.key];
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

    setState : function(lookup, state) {
        if (!this.views.hasOwnProperty(lookup.view))
            this.views[lookup.view] = {};
        this.views[lookup.view][lookup.key] = state;
    },

    afterViewChange : function(controller, viewname, viewContext, title, skipState) {

        this._controller = controller;
        this._view = viewname;
        this._viewContext = viewContext;

        if (!skipState) {
            this.updateState();
        }
    },

    /**
     * Provided to be overridden to provide a custom title for view states.
     * @param viewname
     * @returns {*}
     */
    getTitle : function(viewname) {
        return viewname;
    },

    clearAppState : function() {
        this.lastAppState = undefined;
    },

    updateState : function() {
        this.state.add({
            controller: this._controller,
            view: this._view,
            viewContext: this._viewContext,
            viewState: {},
            views: this.views,
            filters: this.getFilters(true),
            selections: this.getSelections(true)
        });
        this.state.sync();
    },

    updateFilterMembers : function(id, members) {
        for (var f=0; f < this.filters.length; f++) {
            if (this.filters[f].id == id)
            {
                this.filters[f].set('members', members);
            }
        }
        this.requestFilterUpdate(true, false, true);

        // since it is silent we need to update the count seperately
        this.updateFilterCount();
    },

    getFilters : function(flat) {
        if (!this.filters || this.filters.length == 0)
            return [];

        if (!flat)
            return this.filters;

        var flatFilters = [];

        for (var f=0; f < this.filters.length; f++) {
            var data = Ext.clone(this.filters[f].data);
            if (this.filters[f].isGroup()) {
                // Connector.model.FilterGroup
                var _f = this.filters[f].get('filters');
                for (var i=0; i < _f.length; i++) {
                    data.filters[i] = _f[i].data;
                }
            }
            flatFilters.push(data);
        }

        return flatFilters;
    },

    getFlatFilters : function() {
        if (!this.filters || this.filters.length == 0)
            return [];

        var flatFilters = [];

        for (var f=0; f < this.filters.length; f++) {
            if (this.filters[f].isGroup()) {
                // Connector.model.FilterGroup
                var _f = this.filters[f].get('filters');
                for (var i=0; i < _f.length; i++) {
                    flatFilters.push(Ext.clone(_f[i].data));
                }
            }
            else {
                // Connector.model.Filter
                flatFilters.push(Ext.clone(this.filters[f].data));
            }
        }

        return flatFilters;
    },

    getFilterGroupModelName : function() {
        console.error('Failed to get filter group model name.');
    },

    getFilterModelName : function() {
        console.error('Failed to get filter model name.');
    },

    _getFilterSet : function(filters) {

        var newFilters = [],
                grpClass = this.getFilterGroupModelName(),
                filterClass = this.getFilterModelName();

        for (var s=0; s < filters.length; s++) {
            var f = filters[s];

            // decipher object structure
            if (!f.$className) {
                if (f.filters) {
                    // -- Filter Group
                    var subfilters = [];
                    for (var i=0; i < f.filters.length; i++) {
                        subfilters.push(Ext.create(filterClass, f.filters[i]));
                    }
                    f.filters = subfilters;

                    newFilters.push(Ext.create(grpClass, f));
                }
                else if (f.data) {
                    if (f.data.filters) {
                        var subfilters = [];
                        for (var i=0; i < f.data.filters.length; i++) {
                            subfilters.push(Ext.create(filterClass, f.data.filters[i].data));
                        }
                        f.data.filters = subfilters;

                        newFilters.push(Ext.create(grpClass, f.data));
                    }
                    else {
                        newFilters.push(Ext.create(filterClass, f.data));
                    }
                }
                else {
                    newFilters.push(Ext.create(filterClass, f));
                }
            }
            else if (f.$className == filterClass) {
                newFilters.push(f);
            }
            else if (f.$className == grpClass) {
                var grp = f;
                grp.set('filters', this._getFilterSet(grp.get('filters')));
                newFilters.push(grp);
            }
        }
        return newFilters;

    },

    hasFilters : function() {
        return this.filters.length > 0;
    },

    addFilter : function(filter, skipState) {
        return this.addFilters([filter], skipState);
    },

    addFilters : function(filters, skipState, clearSelection) {
        var _f = this.getFilters();
        if (!_f)
            _f = [];

        var newFilters = this._getFilterSet(filters);

        // new filters are always appended
        for (var f=0; f < newFilters.length; f++)
            _f.push(newFilters[f]);

        this.filters = _f;

        if (clearSelection)
            this.clearSelections(true);

        this.requestFilterUpdate(skipState, false);

        return newFilters;
    },

    prependFilter : function(filter, skipState) {
        this.setFilters([filter].concat(this.filters), skipState);
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

    setFilters : function(filters, skipState) {
        this.filters = this._getFilterSet(filters);
        this.requestFilterUpdate(skipState, false);
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
                if (target[t].isGroup() || target[t].isGrid() || target[t].isPlot())
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

    requestFilterUpdate : function(skipState, opChange, silent) {
        var olapFilters = [], ff;
        for (var f=0; f < this.filters.length; f++) {

            if (this.filters[f].isGroup()) {
                var grpFilters = this.filters[f].get('filters');
                for (var g=0; g < grpFilters.length; g++) {
                    if (grpFilters[g].isGroup && grpFilters[g].isGroup()) {
                        var _g = grpFilters[g].get('filters');
                        // have a subgroup
                        for (var i=0; i < _g.length; i++) {
                            ff = _g[i].data ? _g[i].data : _g[i];
                            olapFilters.push(LABKEY.app.model.Filter.getOlapFilter(ff, this.subjectName));
                        }
                    }
                    else {
                        ff = grpFilters[g].data ? grpFilters[g].data : grpFilters[g];
                        olapFilters.push(LABKEY.app.model.Filter.getOlapFilter(ff, this.subjectName));
                    }
                }
            }
            else {
                olapFilters.push(LABKEY.app.model.Filter.getOlapFilter(this.filters[f].data, this.subjectName));
            }
        }

        var proceed = true;
        for (f=0; f < olapFilters.length; f++) {
            if (olapFilters[f].arguments.length == 0) {
                alert('EMPTY ARGUMENTS ON FILTER');
                proceed = false;
            }
        }

        if (proceed) {
            this.onMDXReady(function(mdx){
                mdx.setNamedFilter('statefilter', olapFilters);
                if (!skipState) {
                    this.updateState();
                }

                if (!silent) {
                    this.fireEvent('filterchange', this.filters);
                }
            }, this);
        }
    },

    getSelections : function(flat) {
        if (!this.selections || this.selections.length == 0)
            return [];

        if (!flat)
            return this.selections;

        var flatSelections = [];
        for (var f=0; f < this.selections.length; f++) {

            if (this.selections[f].isGroup()) {

                for (var s=0; s < this.selections[f].data.filters.length; s++) {
                    flatSelections.push(this.selections[f].data.filters[s]);
                }
            }
            else {
                flatSelections.push(this.selections[f].data);
            }

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

                if (oldFilters[i].data.hierarchy == newFilters[n].data.hierarchy &&
                        oldFilters[i].data.isGroup == newFilters[n].data.isGroup) {

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

                    if (!oldFilters[i].isGroup() && !opFilters[n].isGroup()) {

                        if (oldFilters[i].getHierarchy() == opFilters[n].getHierarchy()) {
                            oldFilters[i].set('operator', LABKEY.app.model.Filter.lookupOperator(opFilters[n].data));
                        }
                    }
                }
            }
        }

        return oldFilters;
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
     * Returns the set of filters found in newFilters that were not present in oldFilters
     * @param newFilters
     * @param oldFilters
     */
    pruneFilters : function(newFilters, oldFilters) {
        // 15464
        var prunedSelections = [], found;
        for (var s=0; s < newFilters.length; s++) {
            found = false;
            for (var f=0; f < oldFilters.length; f++) {
                if (Ext.isFunction(newFilters[s].isGroup) && newFilters[s].isGroup()) {
                    if (oldFilters[f].isGroup() && oldFilters[f].get('name') == newFilters[s].get('name')) {
                        found = true;
                    }
                }
                else if (newFilters[s].isEqual(oldFilters[f])) {
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

        var sels = [];

        for (var s=0; s < this.selections.length; s++) {

            // construct the query
            sels.push(this.selections[s].getOlapFilter(this.subjectName));
        }

        this.onMDXReady(function(mdx){
            mdx.setNamedFilter('stateSelectionFilter', sels);
        }, this);

        if (!skipState)
            this.updateState();

        this.fireEvent('selectionchange', this.selections, opChange);
    },

    moveSelectionToFilter : function() {
        this.addFilters(this.pruneFilters(this.selections, this.filters), false, true);
    },

    getPrivateSelection : function(name) {
        return this.privatefilters[name];
    },

    addPrivateSelection : function(selection, name) {

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
                filters.push(newSelectors[s].getOlapFilter(this.subjectName))
            }
        }

        var me = this;
        this.onMDXReady(function(mdx){

            if (Ext.isArray(selection))
            {
                mdx.setNamedFilter(name, filters);
            }
            else
            {
                mdx.setNamedFilter(name, [{
                    hierarchy : this.subjectName,
                    membersQuery : selection
                }]);
            }
            me.fireEvent('privateselectionchange', mdx._filter[name], name);

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
        this.selections = [];
        this.requestSelectionUpdate(skipState, false);
    },

    setSelections : function(selections, skipState) {
        this.addSelection(selections, skipState);
    }
});
