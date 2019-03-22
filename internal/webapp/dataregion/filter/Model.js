/*
 * Copyright (c) 2014-2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.dataregion.filter.Model', {
    extend: 'Ext.data.Model',

    fields: [
        {name: 'column', defaultValue: undefined},
        {name: 'fieldCaption'},
        {name: 'fieldKey'},
        {name: 'jsonType'},

        {name: 'dataRegionName', defaultValue: 'query'},
        {name: 'schemaName', defaultValue: undefined},
        {name: 'queryName', defaultValue: undefined},
        {name: 'viewName', defaultValue: null}, // Data Regions default to 'null'
        {name: 'container', defaultValue: undefined},
        {name: 'containerFilter', defaultValue: null}, // Data Regions default to 'null'
        {name: 'parameters', defaultValue: undefined}
    ],

    constructor : function(config) {

        this.callParent([config]);

        this.configureFields();
    },

    _getDataRegion : function() {
        var dataRegionName = this.get('dataRegionName'), dr;
        if (Ext4.isObject(LABKEY.DataRegions) && Ext4.isObject(LABKEY.DataRegions[dataRegionName])) {
            dr = LABKEY.DataRegions[dataRegionName];
        }
        return dr;
    },

    configureFields : function() {

        // column
        var column = this.get('column');
        if (!Ext4.isObject(column)) {
            console.error(this.$className, 'requires a "column" ColumnInfo be provided.');
            return;
        }
        else {
            this.set('fieldCaption', column.caption);
            this.set('fieldKey', this.calculateFieldKey(column));
            this.set('jsonType', this.calculateJsonType(column));
        }

        // schema, query
        var schema = this.get('schemaName');
        var query = this.get('queryName');
        if (!Ext4.isString(schema) || !Ext4.isString(query)) {

            var dr = this._getDataRegion();

            // attempt to base the schema off the Data Region
            if (Ext4.isObject(dr)) {
                this.set('schemaName', dr.schemaName);
                this.set('queryName', dr.queryName);
            }
            else {
                console.error(this.$className, 'requires that "schemaName" and "queryName" be provided or that "dataRegionName" provide a valid Data Region reference.');
            }
        }

        // view
        var view = this.get('viewName');
        if (view === null) {
            var dr = this._getDataRegion();

            if (Ext4.isObject(dr)) {
                this.set('viewName', dr.viewName);
            }
        }

        // container
        var container = this.get('container');
        if (!Ext4.isDefined(container)) {
            var dr = this._getDataRegion();

            if (Ext4.isObject(dr)) {
                if (Ext4.isString(dr.container)) {
                    this.set('container', dr.container);
                }
                else if (Ext4.isString(dr.containerPath)) {
                    this.set('container', dr.containerPath);
                }
            }
            else if (Ext4.isString(LABKEY.container.path)) {
                this.set('container', LABKEY.container.path);
            }
            else {
                console.error(this.$className, 'is unable to set the "container". Ensure that LABKEY.container.path is available or the Data Region provides container/containerPath.');
            }
        }

        // containerFilter
        var containerFilter = this.get('containerFilter');
        if (containerFilter === null) {
            var dr = this._getDataRegion();

            if (Ext4.isObject(dr)) {
                this.set('containerFilter', dr.containerFilter);
            }
        }

        // parameters
        var parameters = this.get('parameters');
        if (!Ext4.isDefined(parameters)) {
            var dr = this._getDataRegion();

            if (Ext4.isObject(dr)) {
                this.set('parameters', dr.parameters);
            }
        }
    },

    calculateFieldKey : function(column) {
        var fk = column.fieldKey;
        if (Ext4.isObject(column.lookup) && Ext4.isString(column.displayField)) {
            fk = column.displayField;
        }
        return fk;
    },

    calculateJsonType : function(column) {
        var jsonType = "string";
        if (Ext4.isString(column.displayFieldJsonType)) {
            jsonType = column.displayFieldJsonType;
        }
        else if (Ext4.isString(column.jsonType)) {
            jsonType = column.jsonType;
        }
        return jsonType;
    }
});