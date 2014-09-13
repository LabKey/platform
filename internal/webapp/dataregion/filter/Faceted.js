Ext4.define('LABKEY.dataregion.filter.Faceted', {

    extend: 'LABKEY.dataregion.filter.Base',

    /*** Overridden Methods ***/
    beforeInit : function() {

        this.gridReady = false;
        this.storeReady = false;

        this.items = [{
            xtype: 'panel',
            ui: 'custom',
//            width: this.width - 40, // prevent horizontal scrolling
            items: [this.getGrid()]
        }];
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
                    tpl: new Ext.XTemplate('{displayValue:htmlEncode}')
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
                        isString = Ext.isString(formattedValue);

                        if (formattedValue == null || (isString && formattedValue.length == 0) || (!isString && isNaN(formattedValue))) {
                            hasBlank = true;
                        }
                        else if (Ext.isDefined(v)) {
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
            console.log('really ready');
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
    }
});