/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
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