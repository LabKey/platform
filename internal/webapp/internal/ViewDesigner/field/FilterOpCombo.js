/*
 * Copyright (c) 2015-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('LABKEY.internal.ViewDesigner.field.FilterOpCombo', {

    extend: 'Ext.form.field.ComboBox',

    alias: 'widget.labkey-filterOpCombo',

    mode: 'local',
    triggerAction: 'all',
    forceSelection: true,
    valueField: 'value',
    displayField: 'text',
    allowBlank: false,
    matchFieldWidth: false,

    constructor : function (config) {
        this.fieldMetaStore = config.fieldMetaStore;

        this.callParent([config]);

        this.addEvents('optionsupdated');
    },

    initComponent : function () {
        this.callParent();
        this.setOptions();
    },

    onMouseDown : function (e) {
        // XXX: work around annoying focus bug for Fields in DataView.
        this.focus();
        this.callParent([e]);
    },

    // Called once during initialization
    setRecord : function (filterRecord, clauseIndex) {
        this.record = filterRecord;
        this.clauseIndex = clauseIndex;
        var jsonType = undefined;
        var mvEnabled = false;
        if (this.record) {
            var fieldMetaRecord = this.fieldMetaStore.getById(this.record.get('fieldKey'));
            if (fieldMetaRecord) {
                jsonType = fieldMetaRecord.data.jsonType;
                mvEnabled = fieldMetaRecord.data.mvEnabled;
            }
        }
        var value = this.getRecordValue();
        this.setOptions(jsonType, mvEnabled, value);

        this.setValue(value);
        this.on('blur', function (f) {
            var v = f.getValue();
            this.setRecordValue(v);
        }, this);
    },

    getRecordValue : function () {
        return this.record.get("items")[this.clauseIndex].op;
    },

    setRecordValue : function (value) {
        this.record.get("items")[this.clauseIndex].op = value;
    },

    setOptions : function (type, mvEnabled, value) {
        var found = false;
        var options = [];
        if (type) {
            Ext4.each(LABKEY.Filter.getFilterTypesForType(type, mvEnabled), function (filterType) {
                if (value && value == filterType.getURLSuffix()) {
                    found = true;
                }
                options.push([filterType.getURLSuffix(), filterType.getDisplayText()]);
            });
        }

        if (!found) {
            Ext4.iterate(LABKEY.Filter.Types, function(key, filterType) {
                if (filterType.getURLSuffix() == value) {
                    options.unshift([filterType.getURLSuffix(), filterType.getDisplayText()]);
                    return false;
                }
            });
        }

        var store = Ext4.create('Ext.data.ArrayStore', {
            fields: ['value', 'text'],
            data: options
        });

        this.bindStore(store);
        this.fireEvent('optionsupdated', this);
    },

    getFilterType : function () {
        return LABKEY.Filter.getFilterTypeForURLSuffix(this.getValue());
    }
});
