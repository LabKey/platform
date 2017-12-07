/*
 * Copyright (c) 2013-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.FilterDialog = Ext.extend(Ext.Window, {

    autoHeight: true,

    bbarCfg : {
        bodyStyle : 'border-top: 1px solid black;'
    },

    cls: 'labkey-filter-dialog',

    closeAction: 'destroy',

    defaults: {
        border: false,
        msgTarget: 'under'
    },

    itemId: 'filterWindow',

    modal: true,

    resizable: false,

    // 24846
    width: Ext.isGecko ? 425 : 410,

    allowFacet : undefined,

    cacheFacetResults: true,

    initComponent : function() {

        if (!this['dataRegionName']) {
            console.error('dataRegionName is required for a LABKEY.FilterDialog');
            return;
        }

        this.column = this.column || this.boundColumn; // backwards compat
        if (!this.configureColumn(this.column)) {
            return;
        }

        Ext.apply(this, {
            title: this.title || "Show Rows Where " + this.column.caption + "...",

            carryfilter : true, // whether filter state should try to be carried between views (e.g. when changing tabs)

            // buttons
            buttons: this.configureButtons(),

            // hook key events
            keys:[{
                key: Ext.EventObject.ENTER,
                handler: this.onApply,
                scope: this
            },{
                key: Ext.EventObject.ESC,
                handler: this.closeDialog,
                scope: this
            }],

            // listeners
            listeners: {
                destroy: function() {
                    if (this.focusTask) {
                        Ext.TaskMgr.stop(this.focusTask);
                    }
                },
                resize : function(panel) {  panel.syncShadow(); },
                scope : this
            }
        });

        this.items = [this.getContainer()];

        LABKEY.FilterDialog.superclass.initComponent.call(this);
    },

    allowFaceting : function() {
        if (Ext.isDefined(this.allowFacet))
            return this.allowFacet;

        var dr = this.getDataRegion();
        if (!this.isQueryDataRegion(dr)) {
            this.allowFacet = false;
            return this.allowFacet;
        }

        this.allowFacet = false;
        switch (this.column.facetingBehaviorType) {

            case 'ALWAYS_ON':
                this.allowFacet = true;
                break;
            case 'ALWAYS_OFF':
                this.allowFacet = false;
                break;
            case 'AUTOMATIC':
                // auto rules are if the column is a lookup or dimension
                // OR if it is of type : (boolean, int, date, text), multiline excluded
                if (this.column.lookup || this.column.dimension)
                    this.allowFacet = true;
                else if (this.jsonType == 'boolean' || this.jsonType == 'int' ||
                        (this.jsonType == 'string' && this.column.inputType != 'textarea'))
                    this.allowFacet = true;
                break;
        }

        return this.allowFacet;
    },

    // Returns an Array of button configurations based on supported operations on this column
    configureButtons : function() {
        var buttons = [
            {text: 'OK', handler: this.onApply, scope: this},
            {text: 'Cancel', handler: this.closeDialog, scope: this}
        ];

        if (this.getDataRegion()) {
            buttons.push({text: 'Clear Filter', handler: this.clearFilter, scope: this});
            buttons.push({text: 'Clear All Filters', handler: this.clearAllFilters, scope: this});
        }

        return buttons;
    },

    // Returns true if the initialization was a success
    configureColumn : function(column) {
        if (!column) {
            console.error('A column is required for LABKEY.FilterDialog');
            return false;
        }

        Ext.apply(this, {
            // DEPRECATED: Either invoked from GWT, which will handle the commit itself.
            // Or invoked as part of a regular filter dialog on a grid
            changeFilterCallback: this.confirmCallback,

            fieldCaption: column.caption,
            fieldKey: column.lookup && column.displayField ? column.displayField : column.fieldKey, // terrible
            jsonType: (column.displayFieldJsonType ? column.displayFieldJsonType : column.jsonType) || 'string'
        });

        return true;
    },

    onApply : function() {
        if (this.apply())
            this.closeDialog();
    },

    // Validates and applies the current filter(s) to the DataRegion
    apply : function() {
        var view = this.getContainer().getActiveTab();
        var isValid = true;

        if (!view.getForm().isValid())
            isValid = false;

        if (isValid) {
            isValid = view.checkValid();
        }

        if (isValid) {

            var dr = this.getDataRegion(),
                filters = view.getFilters();

            if (Ext.isFunction(this.changeFilterCallback)) {

                var filterParams = '', sep = '';
                for (var f=0; f < filters.length; f++) {
                    filterParams += sep + encodeURIComponent(filters[f].getURLParameterName(this.dataRegionName)) + '=' + encodeURIComponent(filters[f].getURLParameterValue());
                    sep = '&';
                }
                this.changeFilterCallback.call(this, null, null, filterParams);
            }
            else {
                if (filters.length > 0) {
                    // add the current filter(s)
                    if (view.supportsMultipleFilters) {
                        dr.replaceFilters(filters, this.column);
                    }
                    else
                        dr.replaceFilter(filters[0]);
                }
                else {
                    this.clearFilter();
                }
            }
        }

        return isValid;
    },

    clearFilter : function() {
        var dr = this.getDataRegion();
        if (!dr) { return; }
        Ext.StoreMgr.clear();
        dr.clearFilter(this.fieldKey);
        this.closeDialog();
    },

    clearAllFilters : function() {
        var dr = this.getDataRegion();
        if (!dr) { return; }
        dr.clearAllFilters();
        this.closeDialog();
    },

    closeDialog : function() {
        this.close();
    },

    getDataRegion : function() {
        return LABKEY.DataRegions[this.dataRegionName];
    },

    isQueryDataRegion : function(dr) {
        return dr && dr.schemaName && dr.queryName;
    },

    // Returns a class instance of a class that extends Ext.Container.
    // This container will hold all the views registered to this FilterDialog instance.
    // For caching purposes assign to this.viewcontainer
    getContainer : function() {

        if (!this.viewcontainer) {

            var views = this.getViews();
            var type = 'TabPanel';

            if (views.length == 1) {
                views[0].title = false;
                type = 'Panel';
            }

            var config = {
                defaults: this.defaults,
                deferredRender: false,
                monitorValid: true,

                // sizing and styling
                autoHeight: true,
                bodyStyle: 'margin: 0 5px;',
                border: true,
                items: views
            };

            if (type == 'TabPanel') {
                config.listeners = {
                    beforetabchange : function(tp, newTab, oldTab) {
                        if (this.carryfilter && newTab && oldTab && oldTab.isChanged()) {
                            newTab.setFilters(oldTab.getFilters());
                        }
                    },
                    tabchange : function() {
                        this.syncShadow();
                        this.viewcontainer.getActiveTab().doLayout(); // required when facets return while on another tab
                    },
                    scope : this
                };
            }

            if (views.length > 1) {
                config.activeTab = (this.allowFaceting() ? 1 : 0);
            }
            else {
                views[0].title = false;
            }

            this.viewcontainer = new Ext[type](config);

            if (!Ext.isFunction(this.viewcontainer.getActiveTab)) {
                var me = this;
                this.viewcontainer.getActiveTab = function() {
                    return me.viewcontainer.items.items[0];
                };
                // views attempt to hook the 'activate' event but some panel types do not fire
                // force fire on the first view
                this.viewcontainer.items.items[0].on('afterlayout', function(p) {
                    p.fireEvent('activate', p);
                }, this, {single: true});
            }
        }

        return this.viewcontainer;
    },

    _getFilters : function() {
        var filters = [];

        var dr = this.getDataRegion();
        if (dr) {
            Ext.each(dr.getUserFilterArray(), function(ff) {
                if (this.column.lookup && this.column.displayField && ff.getColumnName() == this.column.displayField) {
                    filters.push(ff);
                }
                else if (this.column.fieldKey && ff.getColumnName() == this.column.fieldKey) {
                    filters.push(ff);
                }
            }, this);
        }
        else if (this.queryString) { // deprecated
            filters = LABKEY.Filter.getFiltersFromUrl(this.queryString, this.dataRegionName);
        }

        return filters;
    },

    // Override to return your own filter views
    getViews : function() {

        var filters = this._getFilters(), views = [];

        // default view
        views.push({
            xtype: 'filter-view-default',
            column: this.column,
            fieldKey: this.fieldKey, // should not have to hand this in bc the column should supply correctly
            dataRegionName: this.dataRegionName,
            jsonType : this.jsonType,
            filters: filters
        });

        // facet view
        if (this.allowFaceting()) {
            views.push({
                xtype: 'filter-view-faceted',
                column: this.column,
                fieldKey: this.fieldKey, // should not have to hand this in bc the column should supply correctly
                dataRegionName: this.dataRegionName,
                jsonType : this.jsonType,
                filters: filters,
                cacheResults: this.cacheFacetResults,
                listeners: {
                    invalidfilter : function() {
                        this.carryfilter = false;
                        this.getContainer().setActiveTab(0);
                        this.getContainer().getActiveTab().doLayout();
                        this.carryfilter = true;
                    },
                    scope: this
                },
                scope: this
            })
        }

        return views;
    }
});

LABKEY.FilterDialog.ViewPanel = Ext.extend(Ext.form.FormPanel, {

    supportsMultipleFilters: false,

    filters : [],

    changed : false,

    initComponent : function() {
        if (!this['dataRegionName']) {
            console.error('dataRegionName is requied for a LABKEY.FilterDialog.ViewPanel');
            return;
        }
        LABKEY.FilterDialog.ViewPanel.superclass.initComponent.call(this);
    },

    // Override to provide own view validation
    checkValid : function() {
        return true;
    },

    getDataRegion : function() {
        return LABKEY.DataRegions[this.dataRegionName];
    },

    getFilters : function() {
        console.error('All classes which extend LABKEY.FilterDialog.ViewPanel must implement getFilters()');
    },

    setFilters : function(filterArray) {
        console.error('All classes which extend LABKEY.FilterDialog.ViewPanel must implement setFilters(filterArray)');
    },

    getXtype : function() {
        switch (this.jsonType) {
            case "date":
                return "datefield";
            case "int":
            case "float":
                return "textfield";
            case "boolean":
                return 'labkey-booleantextfield';
            default:
                return "textfield";
        }
    },

    // Returns true if a view has been altered since the last time it was activated
    isChanged : function() {
        return this.changed;
    }
});

Ext.ns('LABKEY.FilterDialog.View');

LABKEY.FilterDialog.View.Default = Ext.extend(LABKEY.FilterDialog.ViewPanel, {

    supportsMultipleFilters: true,

    itemDefaults: {
        border: false,
        msgTarget: 'under'
    },

    initComponent : function() {

        Ext.apply(this, {
            autoHeight: true,
            title: this.title === false ? false : 'Choose Filters',
            bodyStyle: 'padding: 5px;',
            bubbleEvents: ['add', 'remove', 'clientvalidation'],
            defaults: { border: false },
            items: this.generateFilterDisplays(2)
        });

        this.combos = [];
        this.inputs = [];

        LABKEY.FilterDialog.View.Default.superclass.initComponent.call(this);

        this.on('activate', this.onViewReady, this, {single: true});
    },

    onViewReady : function() {
        if (this.filters.length == 0) {
            for (var c=0; c < this.combos.length; c++) {
                // Update the input enabled/disabled status by using the 'select' event listener on the combobox.
                // However, ComboBox doesn't fire 'select' event when changed programatically so we fire it manually.
                this.combos[c].reset();
                this.combos[c].fireEvent('select', this.combos[c], null);
                this.inputs[c].reset();
            }
        }
        else {
            for (var f=0; f < this.filters.length; f++) {
                if (f < this.combos.length) {
                    var filter = this.filters[f];
                    var combo = this.combos[f];

                    // Update the input enabled/disabled status by using the 'select' event listener on the combobox.
                    // However, ComboBox doesn't fire 'select' event when changed programatically so we fire it manually.
                    var store = combo.getStore();
                    if (store) {
                        var rec = store.getAt(store.find('value', filter.getFilterType().getURLSuffix()));
                        if (rec) {
                            combo.setValue(filter.getFilterType().getURLSuffix());
                            combo.fireEvent('select', combo, rec);
                        }
                    }

                    this.inputs[f].setValue(filter.getValue());
                }
            }
        }

        //Issue 24550: always select the first filter field, and also select text if present
        if (this.inputs[0]) {
            this.inputs[0].focus(true, 100, this.inputs[0]);
        }

        this.changed = false;
    },

    checkValid : function() {
        var combos = this.combos;
        var inputs = this.inputs, input, value, f;

        var isValid = true;

        Ext.each(combos, function(c, i) {
            if (!c.isValid()) {
                isValid = false;
            }
            else {
                input = inputs[i];
                value = input.getValue();

                f = LABKEY.Filter.getFilterTypeForURLSuffix(c.getValue());

                if (!f) {
                    alert('filter not found: ' + c.getValue());
                    return;
                }

                if (f.isDataValueRequired() && Ext.isEmpty(value)) {
                    input.markInvalid('You must enter a value');
                    isValid = false;
                }
            }
        });

        return isValid;
    },

    inputFieldValidator : function(input, combo) {

        var store = combo.getStore();
        if (store) {
            var rec = store.getAt(store.find('value', combo.getValue()));
            var filter = LABKEY.Filter.getFilterTypeForURLSuffix(combo.getValue());

            if (rec) {
                if (filter.isMultiValued())
                    return this.validateMultiValueInput(input.getValue(), filter.getMultiValueSeparator(), filter.getMultiValueMinOccurs(), filter.getMultiValueMaxOccurs());
                return this.validateInputField(input.getValue());
            }
        }
        return true;
    },

    generateFilterDisplays : function(quantity) {
        var idx = this.nextIndex(), items = [], i=0;

        for(; i < quantity; i++) {
            items.push({
                xtype: 'panel',
                layout: 'form',
                itemId: 'filterPair' + idx,
                border: false,
                defaults: this.itemDefaults,
                items: [this.getComboConfig(idx), this.getInputConfig(idx)],
                scope: this
            });
            idx++;
        }
        return items;
    },

    getComboConfig : function(idx) {

        var val = idx === 0 ? LABKEY.Filter.getDefaultFilterForType(this.jsonType).getURLSuffix() : '';
        return {
            xtype: 'combo',
            itemId: 'filterComboBox' + idx,
            filterIndex: idx,
            name: 'filterType_'+(idx + 1),   //for compatibility with tests...
            listWidth: (this.jsonType == 'date' || this.jsonType == 'boolean') ? null : 380,
            emptyText: idx === 0 ? 'Choose a filter:' : 'No other filter',
            autoSelect: false,
            width: 250,
            minListWidth: 250,
            triggerAction: 'all',
            fieldLabel: (idx === 0 ?'Filter Type' : 'and'),
            store: this.getSelectionStore(idx),
            displayField: 'text',
            valueField: 'value',
            typeAhead: 'false',
            forceSelection: true,
            mode: 'local',
            clearFilterOnReset: false,
            editable: false,
            value: val,
            originalValue: val,
            listeners : {
                render : function(combo) {
                    this.combos.push(combo);
                    // Update the associated inputField's enabled/disabled state on initial render
                    this.enableInputField(combo);
                },
                select : function (combo) {
                    this.changed = true;
                    this.enableInputField(combo);
                },
                scope: this
            },
            scope: this
        };
    },

    enableInputField : function (combo) {

        var idx = combo.filterIndex;
        var inputField = this.find('itemId', 'inputField'+idx)[0];

        var filter = LABKEY.Filter.getFilterTypeForURLSuffix(combo.getValue());
        var selectedValue = filter ? filter.getURLSuffix() : '';

        var combos = this.combos;
        var inputFields = this.inputs;

        if (filter && !filter.isDataValueRequired()) {
            //Disable the field and allow it to be blank for values 'isblank' and 'isnonblank'.
            inputField.disable();
            inputField.setValue();
        }
        else {
            inputField.enable();
            inputField.validate();
            inputField.focus('', 50)
        }

        //if the value is null, this indicates no filter chosen.  if it lacks an operator (ie. isBlank)
        //in either case, this means we should disable all other filters
        if(selectedValue == '' || !filter.isDataValueRequired()){
            //Disable all subsequent combos
            Ext.each(combos, function(combo, idx){
                //we enable the next combo in the series
                if(combo.filterIndex == this.filterIndex + 1){
                    combo.setValue();
                    inputFields[idx].setValue();
                    inputFields[idx].enable();
                    inputFields[idx].validate();
                }
                else if (combo.filterIndex > this.filterIndex){
                    combo.setValue();
                    inputFields[idx].disable();
                }

            }, this);
        }
        else{
            //enable the other filterComboBoxes.
            Ext.each(combos, function(combo, i) { combo.enable(); }, this);

            if (combos.length) {
                combos[0].focus('', 50);
            }
        }
    },

    getFilters : function() {

        var inputs = this.inputs;
        var combos = this.combos;
        var value, type, filters = [];

        Ext.each(combos, function(c, i) {
            if (!inputs[i].disabled || (c.getRawValue() != 'No Other Filter')) {
                value = inputs[i].getValue();
                type = LABKEY.Filter.getFilterTypeForURLSuffix(c.getValue());

                if (!type) {
                    alert('Filter not found for suffix: ' + c.getValue());
                }

                filters.push(LABKEY.Filter.create(this.fieldKey, value, type));
            }
        }, this);

        return filters;
    },

    getInputConfig : function(idx) {
        var me = this;
        return {
            xtype         : this.getXtype(),
            itemId        : 'inputField' + idx,
            filterIndex   : idx,
            id            : 'value_'+(idx + 1),   //for compatibility with tests...
            width         : 250,
            blankText     : 'You must enter a value.',
            validateOnBlur: true,
            value         : null,
            altFormats: (this.jsonType == "date" ? LABKEY.Utils.getDateAltFormats() : undefined),
            validator : function(value) {

                // support for filtering '∞'
                if (me.jsonType == 'float' && value.indexOf('∞') > -1) {
                    value = value.replace('∞', 'Infinity');
                    this.setRawValue(value); // does not fire validation
                }

                var combos = me.combos;
                if (!combos.length) {
                    return;
                }

                return me.inputFieldValidator(this, combos[idx]);
            },
            listeners: {
                disable : function(field){
                    //Call validate after disable so any pre-existing validation errors go away.
                    if(field.rendered) {
                        field.validate();
                    }
                },
                focus : function(f) {
                    if (this.focusTask) {
                        Ext.TaskMgr.stop(this.focusTask);
                    }
                },
                render : function(input) {
                    me.inputs.push(input);
                    if (!me.focusReady) {
                        me.focusReady = true;
                        // create a task to set the input focus that will get started after layout is complete,
                        // the task will run for a max of 2000ms but will get stopped when the component receives focus
                        this.focusTask = {interval:150, run: function(){
                            input.focus(null, 50);
                            Ext.TaskMgr.stop(this.focusTask);
                        }, scope: this, duration: 2000};
                    }
                },
                change : function(input, newVal, oldVal) {
                    if (oldVal != newVal) {
                        this.changed = true;
                    }
                },
                scope : this
            },
            scope: this
        };
    },

    getSelectionStore : function(storeNum) {
        var fields = ['text', 'value',
            {name: 'isMulti', type: Ext.data.Types.BOOL},
            {name: 'isOperatorOnly', type: Ext.data.Types.BOOL}
        ];
        var store = new Ext.data.ArrayStore({
            fields: fields,
            idIndex: 1
        });
        var comboRecord = Ext.data.Record.create(fields);

        var filters = LABKEY.Filter.getFilterTypesForType(this.jsonType, this.column.mvEnabled);
        for (var i=0; i<filters.length; i++)
        {
            var filter = filters[i];
            store.add(new comboRecord({
                text: filter.getLongDisplayText(),
                value: filter.getURLSuffix(),
                isMulti: filter.isMultiValued(),
                isOperatorOnly: filter.isDataValueRequired()
            }));
        }

        if (storeNum > 0) {
            store.removeAt(0);
            store.insert(0, new comboRecord({text:'No Other Filter', value: ''}));
        }

        return store;
    },

    setFilters : function(filterArray) {
        this.filters = filterArray;
        this.onViewReady();
    },

    nextIndex : function() {
        return 0;
    },

    validateMultiValueInput : function(inputValues, multiValueSeparator, minOccurs, maxOccurs) {
        // Used when "Equals One Of.." or "Between" is selected. Calls validateInputField on each value entered.
        var values = inputValues.split(multiValueSeparator);
        var isValid = "";
        for(var i = 0; i < values.length; i++){
            isValid = this.validateInputField(values[i]);
            if(isValid !== true){
                return isValid;
            }
        }

        if (minOccurs !== undefined && minOccurs > 0)
        {
            if (values.length < minOccurs)
                return "At least " + minOccurs + " '" + multiValueSeparator + "' separated values are required";
        }

        if (maxOccurs !== undefined && maxOccurs > 0)
        {
            if (values.length > maxOccurs)
                return "At most " + maxOccurs + " '" + multiValueSeparator + "' separated values are allowed";
        }

        //If we make it out of the for loop we had no errors.
        return true;
    },

    // The fact that Ext3 ties validation to the editor is a little funny,
    // but using this shifts the work to Ext
    validateInputField : function(value) {
        var map = {
            'string': 'STRING',
            'int': 'INT',
            'float': 'FLOAT',
            'date': 'DATE',
            'boolean': 'BOOL'
        };
        var type = map[this.jsonType];
        if (type) {
            var field = new Ext.data.Field({
                type: Ext.data.Types[type],
                allowDecimals :  this.jsonType != "int",  //will be ignored by anything besides numberfield
                useNull: true
            });

            var convertedVal = field.convert(value);
            if (!Ext.isEmpty(value) && value != convertedVal) {
                return "Invalid value: " + value;
            }
        }
        else {
            console.log('Unrecognized type: ' + this.jsonType);
        }

        return true;
    }
});

Ext.reg('filter-view-default', LABKEY.FilterDialog.View.Default);

LABKEY.FilterDialog.View.Faceted = Ext.extend(LABKEY.FilterDialog.ViewPanel, {

    MAX_FILTER_CHOICES: 250, // This is the maximum number of filters that will be requested / shown

    applyContextFilters: true,

    /**
     * Logically convert filters to try and optimize the query on the server.
     * (e.g. using NOT IN when less than half the available values are checked)
     */
    filterOptimization: true,

    cacheResults: true,

    emptyDisplayValue: '[Blank]',

    initComponent : function() {

        Ext.apply(this, {
            title  : 'Choose Values',
            border : false,
            height : 200,
            bodyStyle: 'overflow-x: hidden; overflow-y: auto',
            bubbleEvents: ['add', 'remove', 'clientvalidation'],
            defaults : {
                border : false
            },
            markDisabled : true,
            items: [{
                layout: 'hbox',
                style: 'padding-bottom: 5px; overflow-x: hidden',
                width: 100,
                defaults: {
                    border: false
                },
                items: []
            }]
        });

        LABKEY.FilterDialog.View.Faceted.superclass.initComponent.call(this);

        this.on('render', this.onPanelRender, this, {single: true});
    },

    formatValue : function(val) {
        if(this.column) {
            if (this.column.extFormatFn) {
                try {
                    this.column.extFormatFn = eval(this.column.extFormatFn);
                }
                catch (error) {
                    console.log('improper extFormatFn: ' + this.column.extFormatFn);
                }

                if (Ext.isFunction(this.column.extFormatFn)) {
                    val = this.column.extFormatFn(val);
                }
            }
            else if (this.jsonType == 'int') {
                val = parseInt(val);
            }
        }
        return val;
    },

    // copied from Ext 4 Ext.Array.difference
    difference : function(arrayA, arrayB) {
        var clone = arrayA.slice(),
                ln = clone.length,
                i, j, lnB;

        for (i = 0,lnB = arrayB.length; i < lnB; i++) {
            for (j = 0; j < ln; j++) {
                if (clone[j] === arrayB[i]) {
                    clone.splice(j, 1);
                    j--;
                    ln--;
                }
            }
        }

        return clone;
    },

    constructFilter : function(selected, unselected) {
        var filter = null;

        if (selected.length > 0) {

            var columnName = this.fieldKey;

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
                    var type = (val === "" ? LABKEY.Filter.Types.NONBLANK : LABKEY.Filter.Types.NOT_EQUAL_OR_MISSING);

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

    // Implement interface LABKEY.FilterDialog.ViewPanel
    getFilters : function() {
        var grid = Ext.getCmp(this.gridID);
        var filters = [];

        if (grid) {
            var store = grid.store;
            var count = store.getCount(); // TODO: Check if store loaded
            var selected = grid.getSelectionModel().getSelections();

            if (count == 0 || selected.length == 0 || selected.length == count) {
                filters = [];
            }
            else {
                var unselected = this.filterOptimization ? this.difference(store.getRange(), selected) : [];
                filters = [this.constructFilter(selected, unselected)];
            }
        }

        return filters;
    },

    // Implement interface LABKEY.FilterDialog.ViewPanel
    setFilters : function(filterArray) {
        if (Ext.isArray(filterArray)) {
            this.filters = filterArray;
            this.onViewReady();
        }
    },

    getGridConfig : function(idx) {
        var sm = new Ext.grid.CheckboxSelectionModel({
            listeners: {
                selectionchange: {
                    fn: function(sm) {
                        // NOTE: this will manually set the checked state of the header checkbox.  it would be better
                        // to make this a real tri-state (ie. selecting some records is different then none), but since this is still Ext3
                        // and ext4 will be quite different it doesnt seem worth the effort right now
                        var selections = sm.getSelections();
                        var headerCell = Ext.fly(sm.grid.getView().getHeaderCell(0)).first('div');
                        if(selections.length == sm.grid.store.getCount()){
                            headerCell.addClass('x-grid3-hd-checker-on');
                        }
                        else {
                            headerCell.removeClass('x-grid3-hd-checker-on');
                        }


                    },
                    buffer: 50
                }
            }
        });

        this.gridID = Ext.id();
        var me = this;

        return {
            xtype: 'grid',
            id: this.gridID,
            border: true,
            bodyBorder: true,
            frame: false,
            autoHeight: true,
            itemId: 'inputField' + (idx || 0),
            filterIndex: idx || 0,
            msgTarget: 'title',
            store: this.getLookupStore(),
            headerClick: false,
            viewConfig: {
                headerTpl: new Ext.Template(
                    '<table border="0" cellspacing="0" cellpadding="0" style="{tstyle}">',
                        '<thead>',
                            '<tr class="x-grid3-row-table">{cells}</tr>',
                        '</thead>',
                    '</table>'
                )
            },
            sm: sm,
            cls: 'x-grid-noborder',
            columns: [
                sm,
                new Ext.grid.TemplateColumn({
                    header: '<a href="javascript:void(0);">[All]</a>',
                    dataIndex: 'value',
                    menuDisabled: true,
                    resizable: false,
                    width: 340,
                    tpl: new Ext.XTemplate('<tpl for=".">' +
                            '<span class="labkey-link" title="{[Ext.util.Format.htmlEncode(values["displayValue"])]}">' +
                            '{[Ext.util.Format.htmlEncode(values["displayValue"])]}' +
                            '</span></tpl>')
                })
            ],
            listeners: {
                afterrender : function(grid) {
                    grid.getSelectionModel().on('selectionchange', function() {
                        this.changed = true;
                    }, this);

                    grid.on('viewready', function(g) {
                        this.gridReady = true;
                        this.onViewReady();
                    }, this, {single: true});
                },
                scope : this
            },
            // extend toggle behavior to the header cell, not just the checkbox next to it
            onHeaderCellClick : function() {
                var sm = this.getSelectionModel();
                var selected = sm.getSelections();
                selected.length == this.store.getCount() ? this.selectNone() : this.selectAll();
            },
            getValue : function() {
                var vals = this.getValues();
                if (vals.length == vals.max) {
                    return [];
                }
                return vals.values;
            },
            getValues : function() {
                var values = [],
                        sels   = this.getSelectionModel().getSelections();

                Ext.each(sels, function(rec){
                    values.push(rec.get('strValue'));
                }, this);

                if(values.indexOf('') != -1 && values.length == 1)
                    values.push(''); //account for null-only filtering

                return {
                    values : values.join(';'),
                    length : values.length,
                    max    : this.getStore().getCount()
                };
            },
            setValue : function(values, negated) {
                if (!this.rendered) {
                    this.on('render', function() {
                        this.setValue(values, negated);
                    }, this, {single: true});
                }

                if (!Ext.isArray(values)) {
                    values = values.split(';');
                }

                if (this.store.isLoading) {
                    // need to wait for the store to load to ensure records
                    this.store.on('load', function() {
                        this._checkAndLoadValues(values, negated);
                    }, this, {single: true});
                }
                else {
                    this._checkAndLoadValues(values, negated);
                }
            },
            _checkAndLoadValues : function(values, negated) {
                var records = [],
                        recIdx,
                        recordNotFound = false;

                Ext.each(values, function(val) {
                    recIdx = this.store.findBy(function(rec){
                        return rec.get('strValue') === val;
                    });

                    if (recIdx != -1) {
                        records.push(recIdx);
                    }
                    else {
                        // Issue 14710: if the record isnt found, we wont be able to select it, so should reject.
                        // If it's null/empty, ignore silently
                        if (!Ext.isEmpty(val)) {
                            recordNotFound = true;
                            return false;
                        }
                    }
                }, this);

                if (negated) {
                    var count = this.store.getCount(), found = false, negRecords = [];
                    for (var i=0; i < count; i++) {
                        found = false;
                        for (var j=0; j < records.length; j++) {
                            if (records[j] == i)
                                found = true;
                        }
                        if (!found) {
                            negRecords.push(i);
                        }
                    }
                    records = negRecords;
                }

                if (recordNotFound) {
                    // cannot find any matching records
                    if (me.column.facetingBehaviorType != 'ALWAYS_ON')
                        me.fireEvent('invalidfilter');
                    return;
                }

                this.getSelectionModel().selectRows(records);
            },
            selectAll : function() {
                if (this.rendered) {
                    var sm = this.getSelectionModel();
                    sm.selectAll.defer(10, sm);
                }
                else {
                    this.on('render', this.selectAll, this, {single: true});
                }
            },
            selectNone : function() {
                if (this.rendered) {
                    this.getSelectionModel().selectRows([]);
                }
                else {
                    this.on('render', this.selectNone, this, {single: true});
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
            selectFilter : function(filter) {
                var negated = this.determineNegation(filter);

                this.setValue(filter.getURLParameterValue(), negated);

                if (!me.filterOptimization && negated) {
                    me.fireEvent('invalidfilter');
                }
            },
            scope : this
        };
    },

    onViewReady : function() {
        if (this.gridReady && this.storeReady) {
            var grid = Ext.getCmp(this.gridID);
            this.hideMask();

            if (grid) {

                var numFilters = this.filters.length;
                var numFacets = grid.store.getCount();

                // apply current filter
                if (numFacets == 0)
                    grid.selectNone();
                else if (numFilters == 0)
                    grid.selectAll();
                else
                    grid.selectFilter(this.filters[0]);

                if (!grid.headerClick) {
                    grid.headerClick = true;
                    var div = Ext.fly(grid.getView().getHeaderCell(1)).first('div');
                    div.on('click', grid.onHeaderCellClick, grid);
                }

                if (numFacets > this.MAX_FILTER_CHOICES) {
                    this.fireEvent('invalidfilter');
                }
            }
        }

        this.changed = false;
    },

    getLookupStore : function() {
        var dr = this.getDataRegion();
        var storeId = this.cacheResults ? [dr.schemaName, dr.queryName, this.fieldKey].join('||') : Ext.id();

        // cache
        var store = Ext.StoreMgr.get(storeId);
        if (store) {
            this.storeReady = true; // unsafe
            return store;
        }

        store = new Ext.data.ArrayStore({
            fields : ['value', 'strValue', 'displayValue'],
            storeId: storeId
        });

        var config = {
            schemaName: dr.schemaName,
            queryName: dr.queryName,
            dataRegionName: dr.name,
            viewName: dr.viewName,
            column: this.fieldKey,
            filterArray: dr.filters,
            containerPath: dr.container || dr.containerPath || LABKEY.container.path,
            containerFilter: dr.getContainerFilter(),
            parameters: dr.getParameters(),
            maxRows: this.MAX_FILTER_CHOICES+1,
            ignoreFilter: dr.ignoreFilter,
            success : function(d) {
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

        if (this.applyContextFilters) {
            var userFilters = dr.getUserFilterArray();
            if (userFilters && userFilters.length > 0) {

                var uf = [];

                // Remove filters for the current column
                for (var i=0; i < userFilters.length; i++) {
                    if (userFilters[i].getColumnName() != this.fieldKey) {
                        uf.push(userFilters[i]);
                    }
                }

                config.filterArray = uf;
            }
        }

        // Use Select Distinct
        LABKEY.Query.selectDistinctRows(config);

        return Ext.StoreMgr.add(store);
    },

    onPanelRender : function(panel) {
        var toAdd = [{
            xtype: 'panel',
            width: this.width - 40, //prevent horizontal scroll
            bodyStyle: 'padding-left: 5px;',
            items: [ this.getGridConfig(0) ],
            listeners : {
                afterrender : {
                    fn: this.showMask,
                    scope: this,
                    single: true
                }
            }
        }];
        panel.add(toAdd);
    },

    showMask : function() {
        if (!this.gridReady && this.getEl()) {
            this.getEl().mask('Loading...');
        }
    },

    hideMask : function() {
        if (this.getEl()) {
            this.getEl().unmask();
        }
    }
});

Ext.reg('filter-view-faceted', LABKEY.FilterDialog.View.Faceted);

Ext.ns('LABKEY.ext');

LABKEY.ext.BooleanTextField = Ext.extend(Ext.form.TextField, {
    initComponent : function() {
        Ext.apply(this, {
            validator: function(val){
                if(!val)
                    return true;

                return LABKEY.Utils.isBoolean(val) ? true : val + " is not a valid boolean. Try true/false; yes/no; on/off; or 1/0.";
            }
        });
        LABKEY.ext.BooleanTextField.superclass.initComponent.call(this);
    }
});

Ext.reg('labkey-booleantextfield', LABKEY.ext.BooleanTextField);

