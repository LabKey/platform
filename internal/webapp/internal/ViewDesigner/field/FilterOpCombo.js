
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
