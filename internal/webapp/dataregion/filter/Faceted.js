Ext4.define('LABKEY.dataregion.filter.Faceted', {

    extend: 'LABKEY.dataregion.filter.Base',

    border: false,

    ui: 'custom',

    /*** Overridden Methods ***/
    beforeInit : function() {

        this.gridReady = false;
        this.storeReady = false;

        if (!this.filters) {
            this.filters = [];
        }

        this.items = [this.getGrid()];
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

    setFilters : function(filterArray) {
        if (Ext4.isArray(filterArray)) {
            this.filters = filterArray;
            this.onViewReady();
        }
    },

    /*** Faceted Methods ***/

    getGrid : function() {
        if (!Ext4.isDefined(this.grid)) {
            this.grid = Ext4.create('Ext.grid.Panel', {
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
                cls: 'measuresgrid infopanegrid',

                listeners : {
                    viewready: {
                        fn: function() { this.gridReady = true; this.onViewReady(); },
                        scope: this,
                        single: true
                    }
                }
            });
        }

        return this.grid;
    },

    getLookupStore : function() {

        var model = this.getModel();
        var storeId = [model.get('schemaName'), model.get('queryName'), model.get('fieldKey')].join('||');

        // cache
        var store = Ext4.StoreMgr.get(storeId);
        if (store) {
            this.storeReady = true;
            return store;
        }

        store = Ext4.create('Ext.data.ArrayStore', {
            fields: ['value', 'strValue', 'displayValue'],
            storeId: storeId
        });

        // Select Disinct Configuration
        var config = {
            schemaName: model.get('schemaName'),
            queryName: model.get('queryName'),
            dataRegionName: model.get('dataRegionName'),
            viewName: model.get('viewName'),
            column: model.get('fieldKey'),
            container: model.get('container'),
            parameters: model.get('parameters'),
            maxRows: 251,
            success: function(d) {
                if (d && d.values) {
                    var recs = [], v, i=0, hasBlank = false, isString, formattedValue;
                    for (; i < d.values.length; i++) {
                        v = d.values[i];
                        formattedValue = this.formatValue(v);
                        isString = Ext4.isString(formattedValue);

                        if (formattedValue == null || (isString && formattedValue.length == 0) || (!isString && isNaN(formattedValue))) {
                            hasBlank = true;
                        }
                        else if (Ext4.isDefined(v)) {
                            recs.push([v, v.toString(), v.toString()]);
                        }
                    }

                    if (hasBlank)
                        recs.unshift(['', '', this.emptyDisplayValue]);

                    store.loadData(recs);
                    store.isLoading = false;
                    this.storeReady = true;
                    this.onViewReady();
                }
            },
            scope: this
        };

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
                grid.getSelectionModel().selectAll();
            }
            else {
                this.selectFilter(this.filters[0]);
            }
        }
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

        this.setValue(filter.getURLParameterValue(), negated);

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
        else if (!Ext4.isArray(values)) {
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

        this.getGrid().getSelectionModel().select(records);
    }
});