
Ext4.define('LABKEY.internal.ViewDesigner.FieldMetaRecord', {

    extend: 'Ext.data.Model',

    fields: [
        {name: 'name'},
        {name: 'fieldKey', mapping: 'fieldKeyPath' },
        {name: 'description'},
        {name: 'friendlyType'},
        {name: 'type'},
        {name: 'jsonType'},
        {name: 'autoIncrement'},
        {name: 'hidden'},
        {name: 'keyField'},
        {name: 'mvEnabled'},
        {name: 'nullable'},
        {name: 'readOnly'},
        {name: 'userEditable'},
        {name: 'versionField'},
        {name: 'selectable'},
        {name: 'showInInsertView'},
        {name: 'showInUpdateView'},
        {name: 'showInDetailsView'},
        {name: 'importAliases'},
        {name: 'tsvFormat'},
        {name: 'format'},
        {name: 'excelFormat'},
        {name: 'inputType'},
        {name: 'caption'},
        {name: 'lookup'},
        {name: 'crosstabColumnDimension'},
        {name: 'crosstabColumnMember'}
    ],

    getToolTipHtml : function () {
        var body = "<table>";
        var field = this.data;
        if (field.description) {
            body += "<tr><td valign='top'><strong>Description:</strong></td><td>" + Ext4.util.Format.htmlEncode(field.description) + "</td></tr>";
        }
        body += "<tr><td valign='top'><strong>Field&nbsp;key:</strong></td><td>" + Ext4.util.Format.htmlEncode(LABKEY.FieldKey.fromString(field.fieldKey).toDisplayString()) + "</td></tr>";
        if (field.friendlyType) {
            body += "<tr><td valign='top'><strong>Data&nbsp;type:</strong></td><td>" + field.friendlyType + "</td></tr>";
        }
        if (field.hidden) {
            body += "<tr><td valign='top'><strong>Hidden:</strong></td><td>" + field.hidden + "</td></tr>";
        }
        body += "</table>";
        return body;
    }
});