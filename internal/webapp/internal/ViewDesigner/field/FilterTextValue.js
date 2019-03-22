/*
 * Copyright (c) 2015 LabKey Corporation
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

        // UGH: get the op value to set visibility on init
        var op = this.record.get("items")[this.clauseIndex].op;
        var filterType = LABKEY.Filter.getFilterTypeForURLSuffix(op);
        this.setVisible(filterType != null && filterType.isDataValueRequired());

        var value = this.getRecordValue();
        this.setValue(value);
        this.on('blur', function (f) {
            var v = f.getValue();
            this.setRecordValue(v);
        }, this);
    },

    getRecordValue : function () {
        return this.record.get("items")[this.clauseIndex].value;
    },

    setRecordValue : function (value) {
        this.record.get("items")[this.clauseIndex].value = value;
    }

});
