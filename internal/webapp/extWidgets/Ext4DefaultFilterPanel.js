/*
 * Copyright (c) 2012-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.ext4.DefaultFilterPanel', {
    extend : 'Ext.form.Panel',
    alias : 'widget.labkey-default-filterpanel',
    cls : 'defaultfilterpanel',
    boundColumn : null,
    schemaName : null,
    queryName : null,
    border: false,
    filterType: 'default',
    bubbleEvents: ['add', 'remove', 'clientvalidation'],
    defaults: {
        border: false
    },

    initComponent : function () {
        this._fieldCaption = this.boundColumn.caption;
        this._fieldName = this.boundColumn.name;
        this._mappedType = this.getMappedType(this.boundColumn.displayFieldSqlType ? this.boundColumn.displayFieldSqlType : this.boundColumn.sqlType);

        if (this.boundColumn.lookup && this.schemaName && this.queryName){
            if (this.boundColumn.displayField){
                //TODO: perhaps we could be smarter about resolving alternate fieldnames, like the value field, into the displayField?
                this._fieldName = this.boundColumn.displayField;
            }
        }

        this.items = this.getFilterInputPairConfig(2);

        this.callParent();
    },

    afterRender : function () {
        this.callParent();
        this.applyFilterArray();
        var input = this.getInputField(0);
        input.focus(true, 250);
        this.changed = false;
    },

    isValid : function () {
        return this.getForm().isValid();
    },
    
    isChanged : function () {
        return this.changed;
    },

    setFilters : function(filters) {
        this.filterArray = filters;
        this.applyFilterArray();
    },

    applyFilterArray : function () {
        if (null == this.filterArray)
            return;

        if(this.filterArray.length == 0) {
            Ext4.each(this.getFilterCombos(), function(comb) {
                comb.setValue("");
            }, this);

            Ext4.each(this.getInputFields(), function(inp) {
                inp.setValue();
                inp.isValid();
            }, this);
        }

        var idx = 0;
        Ext4.each(this.filterArray, function(filter) {
            if (filter.getColumnName() != this._fieldName)
                return;

            // Only apply column filters
            if(filter.isSelection)
                return;

            var combo = this.getFilterCombo(idx);
            var input = this.getInputField(idx);

            combo.setValue(filter.getFilterType().getURLSuffix());
            if (filter.getValue() !== undefined)
            {
                input.setValue(filter.getValue());
                input.setDisabled(!filter.getFilterType().isDataValueRequired());
            }

            if (++idx > 1)
                return false;
        }, this);
    },

    /**
     * Remove filters from a filterArray if they were applied by this filter panel
     * @param filterArray
     */
    clearFilters : function (filterArray) {
        return LABKEY.Filter.merge(filterArray, this._fieldName, null);
    },

    getFilterInputPairConfig: function(quantity)
    {
        var idx = 0;
        var items = [];

        for(var i=0;i<quantity;i++){
            var combo = this.getComboConfig(idx);
            var input = this.getInputFieldConfig(idx);
            items.push({
                xtype: 'panel',
                //  layout: 'field',
                itemId: 'filterPair' + idx,
                border: false,
                defaults: {
                    border: false,
                    msgTarget: 'under'
                },
                items: [combo, input]
            });
            idx++;
        }
        return items;
    },

    findBy: function (selector, fn) {
        var all = this.query(selector);
        if (null == all)
            return null;

        var result = [];
        Ext4.Array.forEach(all, function(item) {
            if (fn(item))
                result.push(item);
        });
        return result;

    },

    getFilterCombos: function(){
        return this.findBy('combo', function(combo) {
            return combo.itemId && combo.itemId.indexOf('filterComboBox') == 0;
        });
    },

    getFilterCombo: function (idx) {
        return this.down('combo[itemId="filterComboBox' + idx +'"]');
    },

    getInputFields: function(){
        return this.findBy('component', function(item) {
            return item.itemId && item.itemId.indexOf('inputField') == 0;
        });
    },

    getInputField: function (idx) {
        return this.down('component[itemId="inputField' + idx +'"]');
    },

    getComboConfig: function(idx)
    {
        return {
            xtype: 'combo',
            itemId: 'filterComboBox' + idx,
            filterIndex: idx,
            listWidth: (this._mappedType == 'date' || this._mappedType == 'boolean') ? null : 380,
            emptyText: idx === 0 ? 'Choose a filter:' : 'No other filter',
            autoSelect: false,
            width: 320,
            //allowBlank: 'false',
            triggerAction: 'all',
            fieldLabel: (idx === 0 ?'Value' : 'and'),
            store: this.getComboStore(this.boundColumn.mvEnabled, this._mappedType, idx),
            displayField: 'text',
            valueField: 'value',
            typeAhead: false,
            forceSelection: true,
            mode: 'local',
            clearFilterOnReset: false,
            editable: false,
            value: idx === 0 ? this.getComboDefaultValue() : '',
            originalValue: idx === 0 ? this.getComboDefaultValue() : '',
            listeners:{
                scope: this,
                select: function(combo){
                    var idx = combo.filterIndex;
                    var inputField = this.down('component[itemId="inputField' +idx + '"]');

                    var filter = LABKEY.Filter.getFilterTypeForURLSuffix(combo.getValue());
                    var selectedValue = filter ? filter.getURLSuffix() : '';

                    var combos = this.getFilterCombos();
                    var inputFields = this.getInputFields();

                    if(filter && !filter.isDataValueRequired()){
                        //Disable the field and allow it to be blank for values 'isblank' and 'isnonblank'.
                        inputField.disable();
                        inputField.setValue();
                        this.changed = true;
                    }
                    else {
                        inputField.enable();
//                        inputFields[idx].validate();
                        inputField.focus('', 50)
                    }

                    //if the value is null, this indicates no filter chosen.  if it lacks an operator (ie. isBlank)
                    //in either case, this means we should disable all other filters
                    if(selectedValue == '' || !filter.isDataValueRequired()){
                        //Disable all subsequent combos
                        Ext4.each(combos, function(combo, idx){
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
                        combos = this.findBy('combo', function(item){
                            return item.itemId && item.itemId.indexOf('filterComboBox') == 0 && item.filterIndex > (combo.filterIndex + 1);
                        });
                        Ext4.each(combos, function(combo, idx){
                            combo.enable();
                        }, this);

                        if(combos.length){
                            combos[0].focus('', 50);
                        }
                    }
                },
                //enable/disable the input based on the
                disable: function(combo)
                {
                    var input = combo.up('panel').down('component[itemId="inputField' + combo.filterIndex + '"]');
                    input.disable();
                },
                enable: function(combo)
                {
                    var input = combo.up('panel').down('component[itemId="inputField' + combo.filterIndex + '"]');
                    var filter = LABKEY.Filter.getFilterTypeForURLSuffix(combo.getValue());

                    if(filter && filter.isDataValueRequired())
                        input.enable();
                }
            },
            scope: this
        }
    },



    getComboDefaultValue: function()
    {
        if(this._mappedType == 'string'){
            return 'startswith';
        }
        else if(this._mappedType == 'date'){
            return 'dateeq';
        }
        else{
            return 'eq';
        }
    },

    getInputFieldConfig: function(idx)
    {
        var me = this;
        idx = idx || 0;
        var config = {
            xtype         : this.getFieldXtype(),
            itemId        : 'inputField' + idx,
            //msgTarget     : 'under',
            filterIndex   : idx,
            id            : 'value_'+(idx + 1),   //for compatibility with tests...
            //allowBlank    : false,
            width         : 320,
            blankText     : 'You must enter a value.',
            validateOnBlur: true,
            disabled      : idx !== 0,
            value         : null,
            listeners     : {
                scope     : this,
                disable   : function(field){
                    //Call validate after disable so any pre-existing validation errors go away.
                    if(field.rendered)
                        field.validate();
                },
                focus: function(){
                    if (this.focusTask)
                        Ext4.TaskManager.stop(this.focusTask);
                },
                change: function(){
                    this.changed = true;
                }
            },
            validator: function(value){
                var combo = me.getFilterCombo(idx);
                if (combo)
                    return me.inputFieldValidator(this, combo);
            }
        };

        if(this._mappedType == "date")
            config.altFormats = LABKEY.Utils.getDateAltFormats();

        if(idx === 0){
            this.on('afterlayout', function(){
                if(this.focusTask)
                    Ext4.TaskManager.start(this.focusTask);
            }, this);
        }
        return config;

    },

    getFieldXtype: function()
    {
        var xtype = this.getXtype();
        if(xtype == 'numberfield')
            xtype = 'textfield';

        return xtype;
    },

    getXtype: function()
    {
        switch(this._mappedType){
            case "date":
                return "datefield";
            case "int":
            case "float":
                return "numberfield";
            case "boolean":
                return 'labkey-booleantextfield';
            default:
                return "textfield";
        }
    },

    getMappedType : function(dataType)
    {
        var mappedType = this._typeMap[dataType.toUpperCase()];
        if (mappedType == undefined)
            mappedType = dataType.toLowerCase();
        return mappedType;
    },

    getComboStore : function(mvEnabled, mappedType, storeNum)
    {
        var model = Ext4.ModelManager.getModel('LABKEY.ext4.FilterTypeOption');
        if (null == model)
            Ext4.define('LABKEY.ext4.FilterTypeOption', {
                extend : 'Ext.data.Model',
                fields : ['text', 'value', {name: 'isMulti', type: Ext4.data.Types.BOOL}, 'mappedType', {name: 'isOperatorOnly', type: Ext4.data.Types.BOOL}]
            });
        
        var options = [];
        Ext4.each(LABKEY.Filter.getFilterTypesForType(mappedType, mvEnabled), function (filterType) {
            options.push({text:filterType.getDisplayText(), value: filterType.getURLSuffix(), mappedType: mappedType, isMulti : filterType.isMultiValued(), isOperatorOnly : !filterType.isDataValueRequired()});
        });
        if (storeNum > 0)
            options[0].text = 'No Other Filter';

        var store = Ext4.create('Ext.data.Store', {
            model : 'LABKEY.ext4.FilterTypeOption',
            data : options,
            proxy: {
                type: 'memory'
            }
        });

        return store;
    },

    getOriginalFilters : function() {
        var clonedFilters = [];

        if (this.filterArray) {
            Ext.each(this.filterArray, function(filter) {
                clonedFilters.push(LABKEY.Filter.create(filter.getColumnName(), filter.getValue(), filter.getFilterType()));
            });
        }

        return clonedFilters;
    },

    getFilters : function () {
        var inputFields = this.getInputFields();
        var combos = this.getFilterCombos();

        //Step 1: validate
        var isValid = true;
        var filters = [];
        Ext4.each(combos, function(c, idx){
            if(!c.isValid()){
                isValid = false;
                return false;
            }
            else {
                if (c.getValue() == '' && c.getRawValue() != "Has Any Value")
                    return;

                var input = inputFields[idx];
                var value = input.getValue();

                var filter = LABKEY.Filter.getFilterTypeForURLSuffix(c.getValue());

                if(!filter){
                    alert('filter not found: ' + c.getValue());
                    return;
                }

                if(Ext4.isEmpty(value) && filter.isDataValueRequired()){
                    input.markInvalid('You must enter a value');
                    isValid = false;
                    return false;
                }
                if (isValid)
                    filters.push(LABKEY.Filter.create(this._fieldName, value, filter));
            }
        }, this);

        return isValid ? filters : null;
    },

    inputFieldValidator: function(input, cb)
    {
        var rec = cb.getStore().getAt(cb.getStore().find('value', cb.getValue()));
        var filter = LABKEY.Filter.getFilterTypeForURLSuffix(cb.getValue());

        if(rec){
            if (filter.isMultiValued()) {
                return this.validateEqOneOf(input.getValue(), filter.getMultiValueSeparator(), filter.getMultiValueMinOccurs(), filter.getMultiValueMaxOccurs(), rec.get('mappedType'));
            }

            return this.validateInputField(input.getValue(), rec.get('mappedType'));
        }
        return true;
    },

    validateEqOneOf: function(input, multiValueSeparator, minOccurs, maxOccurs, mappedType)
    {
        // Used when "Equals One Of.." or "Between" is selected. Calls validateInputField on each value entered.
        if (!input)
            return true;
        var values = input.split(multiValueSeparator);
        var isValid = "";
        for(var i = 0; i < values.length; i++){
            isValid = this.validateInputField(values[i], mappedType);
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

    //The fact that Ext3 ties validation to the editor is a little funny, but using this shifts the work to Ext
    validateInputField: function(value, mappedType){
        var type = this._extTypeMap[this._mappedType];
        if(type){
            var field = new Ext4.data.Field({
                type: Ext4.data.Types[type],
                allowDecimals :  this._mappedType != "int",  //will be ignored by anything besides numberfield
                useNull: true
            });

            var convertedVal = field.convert(value);
            if(!Ext4.isEmpty(value) && value != convertedVal){
                return "Invalid value: " + value;
            }
        }
        else {
            console.log('Unrecognized type: ' + this._mappedType);
        }

        return true;
    },

    _typeMap : {
        "BIGINT":"int",
        "BIGSERIAL":"int",
        "BIT":"boolean",
        "BOOL":"boolean",
        "BOOLEAN":"boolean",
        "CHAR":"string",
        "CLOB":"string",
        "DATE":"date",
        "DECIMAL":"float",
        "DOUBLE":"float",
        "DOUBLE PRECISION":"float",
        "FLOAT":"float",
        "INTEGER":"int",
        "LONGVARCHAR":"string",
        "NTEXT":"string",
        "NUMERIC":"float",
        "REAL":"float",
        "SMALLINT":"int",
        "TIME":"string",
        "TIMESTAMP":"date",
        "TINYINT":"int",
        "VARCHAR":"string",
        "INT":"int",
        "INT IDENTITY":"int",
        "DATETIME":"date",
        "TEXT":"string",
        "NVARCHAR":"string",
        "INT2":"int",
        "INT4":"int",
        "INT8":"int",
        "FLOAT4":"float",
        "FLOAT8":"float",
        "SERIAL":"int",
        "USERID":"int",
        "VARCHAR2":"string" // Oracle
    },

    _extTypeMap: {
        'string': 'STRING',
        'int': 'INT',
        'float': 'FLOAT',
        'date': 'DATE',
        'boolean': 'BOOL'
    },

    _mappedType : "string"

});
