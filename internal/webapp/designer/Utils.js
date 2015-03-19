/*
 * Copyright (c) 2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('LABKEY.ext4.designer.FilterOpCombo', {

    extend: 'Ext.form.field.ComboBox',

    alias: 'labkey-filterOpCombo',

    constructor : function (config) {
        this.fieldMetaStore = config.fieldMetaStore;
        this.mode = 'local';
        this.triggerAction = 'all';
        this.forceSelection = true;
        this.valueField = 'value';
        this.displayField = 'text';
        this.allowBlank = false;

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
        if (this.record)
        {
            var fieldKey = this.record.data.fieldKey;
            var fieldMetaRecord = this.fieldMetaStore.getById(fieldKey.toUpperCase());
            if (fieldMetaRecord)
            {
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
        if (type)
            Ext4.each(LABKEY.Filter.getFilterTypesForType(type, mvEnabled), function (filterType) {
                if (value && value == filterType.getURLSuffix())
                    found = true;
                options.push([filterType.getURLSuffix(), filterType.getDisplayText()]);
            });

        if (!found) {
            for (var key in LABKEY.Filter.Types) {
                var filterType = LABKEY.Filter.Types[key];
                if (filterType.getURLSuffix() == value) {
                    options.unshift([filterType.getURLSuffix(), filterType.getDisplayText()]);
                    break;
                }
            }
        }

        var store = Ext4.create('Ext.data.ArrayStore', {
            fields: ['value', 'text'],
            data: options
        });

        // Ext.form.ComboBox private method
        this.bindStore(store);
        this.fireEvent('optionsupdated', this);
    },

    getFilterType : function () {
        return LABKEY.Filter.getFilterTypeForURLSuffix(this.getValue());
    }
});

Ext4.define('LABKEY.ext4.designer.FilterTextValue', {

    extend: 'Ext.form.field.Text',

    alias: 'labkey-filterValue',

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

Ext4.define('LABKEY.ext4.designer.PaperclipButton', {

    extend: 'Ext.button.Button',

    alias: 'paperclip-button',

    iconCls: 'labkey-paperclip',
    iconAlign: 'top',
    enableToggle: true,

    initComponent : function () {
        this.addEvents('blur');
        this.callParent();
    },

    afterRender : function () {
        this.callParent();
        this.updateToolTip();
    },

    // Called by ComponentDataView.renderItem when indexedProperty is false
    // When the record.urlParameter is true, the button is not pressed.
    setValue : function (value) {
        this.toggle(!value, true);
        this.updateToolTip();
    },

    // Called by ComponentDataView.renderItem when indexedProperty is false
    // We need to invert the value so the record.urlParameter is true when the button is not pressed.
    getValue : function () {
        return !this.pressed;
    },

    // 'blur' event needed by ComponentDataView to set the value after changing
    toggleHandler : function (btn, state) {
        this.fireEvent('blur', this);
        this.updateToolTip();
    },

    // Called by ComponentDataView.renderItem when indexedProperty is true
    setRecord : function (filterRecord, clauseIndex) {
        if (clauseIndex !== undefined)
        {
            this.record = filterRecord;
            this.clauseIndex = clauseIndex;

            var value = this.getRecordValue();
            this.setValue(value);
            this.on('toggle', function (f, pressed) {
                this.setRecordValue(!pressed);
            }, this);
        }
    },

    getRecordValue : function () {
        return this.record.get("items")[this.clauseIndex].urlParameter;
    },

    setRecordValue : function (value) {
        this.record.get("items")[this.clauseIndex].urlParameter = value;
    },

    getToolTipText : function ()
    {
        if (this.pressed) {
            return "This " + this.itemType + " will be saved with the view";
        }
        else {
            return "This " + this.itemType + " will NOT be saved as part of the view";
        }
    },

    updateToolTip : function () {
        var el = this.btnEl;
        var msg = this.getToolTipText();
        el.set({title: msg});
    }
});