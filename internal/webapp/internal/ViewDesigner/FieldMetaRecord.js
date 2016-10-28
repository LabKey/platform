/*
 * Copyright (c) 2015-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
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
        {name: 'crosstabMember', type: 'boolean', defaultValue: false},
        {name: 'crosstabColumnDimension'},
        {name: 'crosstabColumnMember'},

        // Tree properties
        {
            name: 'text',
            type: 'string',
            mapping: 'name',
            convert: function(v, rec) {
                if (!Ext4.isEmpty(rec.raw.caption) && rec.raw.caption != '&nbsp;') {
                    return rec.raw.caption; // + (rec.raw.hidden === true ? ' (hidden)' : '');
                }
                return v;
            }
        },{
            name: 'leaf',
            type: 'boolean',
            mapping: 'lookup',
            convert: function(v, rec) {
                if (!Ext4.isDefined(rec.id)) {
                    return false; // root might not be defined, return false
                }
                return !Ext4.isObject(v);
            }
        },{
            name: 'checked',
            type: 'boolean',
            defaultValue: false
        },{
            name: 'hidden',
            type: 'boolean',
            convert: function(v) { return v === true; }
        },{
            name: 'disabled',
            type: 'boolean',
            mapping: 'selectable',
            convert: function(v) { return v === false; }
        },{
            name: 'iconCls',
            type: 'string',
            defaultValue: 'x4-hide-display'
        },{
            name: 'expanded',
            type: 'boolean',
            defaultValue: false
        },{
            name: 'loaded',
            type: 'boolean',
            defaultValue: false
        }
    ],

    getToolTipHtml : function () {
        var body = "<table style=\"white-space: nowrap;\">";
        var field = this.data;
        if (field.description) {
            body += "<tr><td valign='top'><strong>Description:</strong></td><td>" + Ext4.htmlEncode(field.description) + "</td></tr>";
        }
        body += "<tr><td valign='top'><strong>Field&nbsp;key:</strong></td><td>" + Ext4.htmlEncode(LABKEY.FieldKey.fromString(field.fieldKey).toDisplayString()) + "</td></tr>";
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