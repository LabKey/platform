/*
 * Copyright (c) 2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.query.browser.view.Base', {

    extend: 'Ext.panel.Panel',

    bodyStyle: 'padding: 5px;',

    border: false,

    constructor : function(config) {
        this.callParent([config]);
        this.addEvents('schemaclick');
    },

    onSchemaLinkClick : function(schemas, evt, t) {
        var schemaName = LABKEY.SchemaKey.fromString(t.innerHTML); // this is bad
        var schema = schemas[schemaName];
        this.fireEvent('schemaclick', LABKEY.SchemaKey.fromString(schema.fullyQualifiedName));
    }
});