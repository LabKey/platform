/*
 * Copyright (c) 2014-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.dataregion.filter.Faceted', {

    extend: 'LABKEY.dataregion.filter.Base',

    border: false,

    ui: 'custom',

    useGrouping: false,

    useStoreCache: true,

    alias: 'widget.labkey-faceted-filterpanel',
    
    maxGroup: 20,
    
    maxRows: 250,

    /*** Overridden Methods ***/
    beforeInit : function() {

        this.gridReady = false;
        this.storeReady = false;

        if (!this.filters) {
            this.filters = [];
        }
        if (this.useGrouping === true && !Ext4.isArray(this.groupFilters)) {
            this.groupFilters = [];
        }

        this.items = [this.getGrid()];
    },

    getOriginalFilters : function() {
        var clonedFilters = [];

        Ext.each(this.filters, function(filter) {
            clonedFilters.push(LABKEY.Filter.create(filter.getColumnName(), filter.getValue(), filter.getFilterType()));
        });

        return clonedFilters;
    },

    getFilters : function() {
        var grid = this.getGrid();
        var filters = [];

        var store = grid.store;
        var count = store.getCount();
        var selected = grid.getSelectionModel().getSelection();

        if (selected.length > 0 && selected.length !== count) {
            var unselected = this.filterOptimization ? this.difference(store.getRange(), selected) : [];
            filters = [this.constructFilter(selected, unselected)];
        }

        return filters;
    },

    setFilters : function(filterArray, groupingFilterArray) {

        if (this.useGrouping === true && Ext4.isArray(groupingFilterArray)) {
            this.groupFilters = groupingFilterArray;
        }

        if (Ext4.isArray(filterArray)) {
            this.filters = filterArray;
            this.onViewReady();
        }
    },

    /*** Faceted Methods ***/

    getGrid : function() {
        if (!Ext4.isDefined(this.grid)) {

            var gridConfig = {
                itemId: 'membergrid',
                store: this.getLookupStore(),
                viewConfig : { stripeRows : false },

                /* Selection configuration */
                selType: 'checkboxmodel',
                selModel: {
                    checkSelector: 'td.x-grid-cell-row-checker'
                },
                multiSelect: true,

                /* Column configuration */
                enableColumnHide: false,
                enableColumnResize: false,
                columns: [{
                    xtype: 'templatecolumn',
                    header: 'All',
                    dataIndex: 'displayValue',
                    flex: 1,
                    sortable: false,
                    menuDisabled: true,
                    tpl: new Ext4.XTemplate('{displayValue:htmlEncode}')
                }],

                /* Styling configuration */
                border: false,
                ui: 'custom',
                cls: 'measuresgrid filterpanegrid',

                listeners : {
                    viewready : {
                        fn: function() { this.gridReady = true; this.onViewReady(); },
                        scope: this,
                        single: true
                    },
                    selectionchange : {
                        fn: function() { this.changed = true; },
                        scope: this
                    }
                }
            };

            /* Grouping configuration */
            if (this.useGrouping === true) {
                gridConfig['requires'] = ['Ext.grid.feature.Grouping'];
                gridConfig['features'] = [{
                    ftype: 'grouping',
                    collapsible: false,
                    groupHeaderTpl: new Ext.XTemplate(
                        '{name:this.renderHeader}', // 'name' is actually the value of the groupField
                        {
                            renderHeader: function(v) {
                                return v ? 'Has data in current selection' : 'No data in current selection';
                            }
                        }
                    )
                }];
            }

            this.grid = Ext4.create('Ext.grid.Panel', gridConfig);
        }

        return this.grid;
    },

    getLookupStore : function() {

        var model = this.getModel();
        var storeId = [model.get('schemaName'), model.get('queryName'), model.get('fieldKey')].join('||');

        // cache
        if (this.useStoreCache === true) {
            var store = Ext4.StoreMgr.get(storeId);
            if (store) {
                this.storeReady = true;
                return store;
            }
        }

        var storeConfig = {
            fields: [
                'value', 'strValue', 'displayValue',
                {name: 'hasData', type: 'boolean', defaultValue: true}
            ],
            storeId: storeId
        };

        if (this.useGrouping === true) {
            storeConfig['groupField'] = 'hasData';
        }

        store = Ext4.create('Ext.data.ArrayStore', storeConfig);

        var baseConfig = {
            method: 'POST',
            schemaName: model.get('schemaName'),
            queryName: model.get('queryName'),
            dataRegionName: model.get('dataRegionName'),
            viewName: model.get('viewName'),
            column: model.get('fieldKey'),
            container: model.get('container'),
            parameters: model.get('parameters'),
            maxRows: this.maxRows + 1
        };

        var onSuccess = function() {
            if (Ext4.isDefined(this.distinctValues) && Ext4.isDefined(this.groupedValues)) {
                var d = this.distinctValues;
                var g = this.groupedValues;
                var gmap = {};

                if(Ext4.isDefined(this.onOverValueLimit) && Ext4.isFunction(this.onOverValueLimit) &&
                        ((d.values.length > this.maxRows) || (g.values.length > this.maxRows))) {
                    this.onOverValueLimit(this, this.scope);
                    return;
                }

                if (g && g.values) {
                    Ext4.each(g.values, function(_g) {
                        if (_g === null) {
                            gmap[_g] = true;
                        }
                        else {
                            gmap[_g.toString()] = true;
                        }
                    });
                }

                if (d && d.values) {
                    var recs = [], v, i=0, hasBlank = false, hasBlankGrp = false, isString, formattedValue;
                    for (; i < d.values.length; i++) {
                        v = d.values[i];
                        formattedValue = this.formatValue(v);
                        isString = Ext4.isString(formattedValue);

                        if (formattedValue == null || (isString && formattedValue.length == 0) || (!isString && isNaN(formattedValue))) {
                            hasBlank = true;
                            hasBlankGrp = (gmap[null] === true);
                        }
                        else if (Ext4.isDefined(v)) {
                            var datas = [v, v.toString(), v.toString(), true];
                            if (this.useGrouping === true) {
                                if (gmap[v.toString()] !== true) {
                                    datas[3] = false;
                                }
                            }
                            recs.push(datas);
                        }
                    }

                    if (hasBlank)
                        recs.unshift(['', '', this.emptyDisplayValue, hasBlankGrp]);

                    store.loadData(recs);
                    store.group(store.groupField, 'DESC');
                    store.isLoading = false;
                    this.storeReady = true;
                    this.onViewReady();
                    this.distinctValues = undefined; this.groupedValues = undefined;
                }
            }
        };

        // Select Disinct Configuration
        var config = Ext4.apply({
            success: function(d) {
                this.distinctValues = d;
                onSuccess.call(this);
            },
            scope: this
        }, baseConfig);

        if (this.useGrouping === true) {
            var grpConfig = Ext4.apply(Ext4.clone(baseConfig), {
                filterArray: this.groupFilters,
                maxRows: this.maxGroup,
                success: function(d) {
                    this.groupedValues = d;
                    onSuccess.call(this);
                },
                scope: this
            });
            LABKEY.Query.selectDistinctRows(grpConfig);
        }
        else {
            this.groupedValues = true;
        }

        LABKEY.Query.selectDistinctRows(config);

        return store;
    },

    onViewReady : function() {
        if (this.gridReady && this.storeReady) {
            // apply current filters
            var grid = this.getGrid();
            var numFilters = this.filters.length;
            var numFacets = grid.getStore().getCount();

            if (numFacets == 0) {
                grid.getSelectionModel().deselectAll();
            }
            else if (numFilters == 0) {
                grid.getSelectionModel().selectAll(true);
            }
            else {
                var mergedFilter = [];
                Ext4.each(this.filters, function (filter) {
                    if (this.model.column.fieldKey == filter.getColumnName() && !filter.isSelection) {
                        mergedFilter = LABKEY.Filter.merge(mergedFilter, this.model.column.fieldKey, filter);
                    }
                }, this);

                if (mergedFilter.length == 0) {
                    grid.getSelectionModel().selectAll(true);
                }
                else {
                    this.selectFilter(mergedFilter[0]);
                }
            }
        }
        if(Ext4.isDefined(this.onSuccessfulLoad) && Ext4.isFunction(this.onSuccessfulLoad))
            this.onSuccessfulLoad(this, this.scope);

        this.changed = false;
    },

    isChanged : function () {
        return this.changed;
    },
    
    constructFilter : function(selected, unselected) {
        var filter = null;

        if (selected.length > 0) {

            var columnName = this.getModel().get('fieldKey');

            // one selection
            if (selected.length == 1) {
                if (selected[0].get('displayValue') == this.emptyDisplayValue)
                    filter = LABKEY.Filter.create(columnName, null, LABKEY.Filter.Types.ISBLANK);
                else
                    filter = LABKEY.Filter.create(columnName, selected[0].get('value')); // default EQUAL
            }
            else if (this.filterOptimization && selected.length > unselected.length) {
                // Do the negation
                if (unselected.length == 1) {
                    var val = unselected[0].get('value');
                    var type = (Ext4.isEmpty(val) ? LABKEY.Filter.Types.NONBLANK : LABKEY.Filter.Types.NOT_EQUAL_OR_MISSING);

                    // 18716: Check if 'unselected' contains empty value
                    filter = LABKEY.Filter.create(columnName, val, type);
                }
                else
                    filter = LABKEY.Filter.create(columnName, this.delimitValues(unselected), LABKEY.Filter.Types.NOT_IN);
            }
            else {
                filter = LABKEY.Filter.create(columnName, this.delimitValues(selected), LABKEY.Filter.Types.IN);
            }
        }

        return filter;
    },

    delimitValues : function(valueArray) {
        var value = '', sep = '';
        for (var s=0; s < valueArray.length; s++) {
            value += sep + valueArray[s].get('value');
            sep = ';';
        }
        return value;
    },

    /* Grid Functions */
    selectFilter : function(filter) {
        var negated = this.determineNegation(filter);

        this.setValue(filter.getURLParameterValue().toString(), negated);

        if (!this.filterOptimization && negated) {
            this.fireEvent('invalidfilter');
        }
    },

    determineNegation: function(filter) {
        var suffix = filter.getFilterType().getURLSuffix();
        var negated = suffix == 'neqornull' || suffix == 'notin';

        // negation of the null case is a bit different so check it as a special case.
        var value = filter.getURLParameterValue();
        if (value == "" && suffix != 'isblank') {
            negated = true;
        }
        return negated;
    },

    setValue : function(values, negated) {
        if (!this.rendered) {
            this.on('render', function() { this.setValue(values, negated); }, this, {single: true});
        }

        if (Ext4.isArray(values) && values.length == 1) {
            values = values[0].split(';');
        }
        else if (!Ext4.isArray(values) && Ext4.isString(values)) {
            values = values.split(';');
        }

        var store = this.getGrid().getStore();
        if (store.isLoading) {
            // need to wait for the store to load to ensure records
            store.on('load', function() { this._checkAndLoadValues(store, values, negated); }, this, {single: true});
        }
        else {
            this._checkAndLoadValues(store, values, negated);
        }
    },

    _checkAndLoadValues : function(store, values, negated) {
        var records = [],
                recIdx,
                recordNotFound = false;

        Ext4.each(values, function(val) {
            recIdx = store.findBy(function(rec){
                return rec.get('strValue') === val;
            });

            if (recIdx != -1) {
                records.push(store.getAt(recIdx));
            }
            else {
                // Issue 14710: if the record is not found, we will not be able to select it, so should reject.
                // If it's null/empty, ignore silently
                if (!Ext4.isEmpty(val)) {
                    recordNotFound = true;
                    return false;
                }
            }
        }, this);

        if (negated) {
            var count = store.getCount(), found = false, negRecords = [], i, j;
            for (i=0; i < count; i++) {
                found = false;
                for (j=0; j < records.length; j++) {
                    if (records[j] == i)
                        found = true;
                }
                if (!found) {
                    negRecords.push(store.getAt(i));
                }
            }
            records = negRecords;
        }

        if (recordNotFound) {
            // cannot find any matching records
            if (this.getModel().get('column').facetingBehaviorType != 'ALWAYS_ON')
                this.fireEvent('invalidfilter');
            return;
        }

        this.getGrid().getSelectionModel().select(records, false, true);
    }
});