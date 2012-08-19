Ext.data.Types.FieldKey = {
    convert: function (v, record) {
        if (Ext.isArray(v))
            return LABKEY.FieldKey.fromParts(v);
        return LABKEY.FieldKey.fromString(v);
    },
    sortType: function (s) {
        return s.toString();
    },
    type: 'FieldKey'
};