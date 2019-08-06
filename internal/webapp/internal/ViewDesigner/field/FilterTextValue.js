/*
 * Copyright (c) 2015-2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('LABKEY.internal.ViewDesigner.field.FilterTextValue', {

    extend: 'Ext.form.field.Text',

    alias: 'widget.labkey-filterValue',

    onMouseDown : function (e) {
        // XXX: work around annoying focus bug for Fields in DataView.
        this.focus();
        this.callParent([e]);
    },

    setRecord : function (filterRecord, clauseIndex) {
        this.record = filterRecord;
        this.clauseIndex = clauseIndex;

        var value = this.getRecordValue();
        var filter = this.createFilter(value);
        var filterType = filter.getFilterType();

        // UGH: get the op value to set visibility on init
        this.setVisible(filterType != null && filterType.isDataValueRequired());

        // The record value may be an Array for filters that support multiple values.
        // convert the filter value into a user-editable string using filter.getURLParameterValue()
        var valueString = filter.getURLParameterValue();

        this.setValue(valueString);
        this.on('blur', function (f) {
            var v = f.getValue();
            this.setRecordValue(v);
        }, this);
    },

    createFilter : function (value) {
        var fieldKey = this.record.get('fieldKey');
        var op = this.record.get("items")[this.clauseIndex].op;
        var filterType = LABKEY.Filter.getFilterTypeForURLSuffix(op);
        var filter = LABKEY.Filter.create(fieldKey, value, filterType);
        return filter;
    },

    getRecordValue : function () {
        return this.record.get("items")[this.clauseIndex].value;
    },

    setRecordValue : function (valueString) {
        // parse the value string into parts for multi-value filters
        try {
            var filter = this.createFilter(valueString);
            var filterValue = filter.getValue();
        }
        catch (e) {
            console.warn("Error parsing filter value: " + valueString);
            filterValue = valueString;
        }

        this.record.get("items")[this.clauseIndex].value = filterValue;
    }

});
