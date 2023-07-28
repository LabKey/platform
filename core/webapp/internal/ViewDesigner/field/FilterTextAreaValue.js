/*
 * Copyright (c) 2015-2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('LABKEY.internal.ViewDesigner.field.FilterTextAreaValue', {

    extend: 'Ext.form.field.TextArea',

    alias: 'widget.labkey-filterTextArea',

    mixins: {
        valueutil: 'LABKEY.internal.ViewDesigner.field.FilterTextValueUtil'
    },

    constructor: function(config){
        this.mixins.valueutil.constructor.apply(this, arguments);
        this.callParent([config]);
    },

    onMouseDown : function (e) {
        // XXX: work around annoying focus bug for Fields in DataView.
        this.focus();
        this.callParent([e]);
    },

    // UGH: get the op value to set visibility on init
    setVisibleField : function (filterType) {
        const hasValue = filterType != null && filterType.isDataValueRequired();
        const visible = hasValue && filterType.isMultiValued() && (filterType.getURLSuffix().indexOf('between') === -1)
        this.setVisible(visible);
    }

});
