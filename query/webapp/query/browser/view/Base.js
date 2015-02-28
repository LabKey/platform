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