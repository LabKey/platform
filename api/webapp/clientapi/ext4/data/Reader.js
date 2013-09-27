/*
 * Copyright (c) 2012-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

/**
 * @name LABKEY.ext4.data.JsonReader
 * @-class
 */
Ext4.define('LABKEY.ext4.data.JsonReader', {
    /**
     * @-lends LABKEY.ext4.data.JsonReader
     */
    extend: 'Ext.data.reader.Json',
    alias: 'reader.labkeyjson',
    config: {
        userFilters: null,
        useSimpleAccessors: true
    },
    mixins: {
        observable: 'Ext.util.Observable'
    },
    constructor: function(){
        this.callParent(arguments);
        this.addEvents('dataload');
    },
    readRecords: function(data) {
        if (data.metaData){
            // NOTE: normalize which field holds the PK.  this is a little unfortunate b/c ext will automatically create this field if it doesnt exist,
            // such as a query w/o a PK.  therefore we fall back to a standard name, which we can ignore when drawing grids
            this.idProperty = data.metaData.id || this.idProperty || '_internalId';
            this.totalProperty = data.metaData.totalProperty; //NOTE: normalize which field holds total rows.
            if (this.model){
                this.model.prototype.idProperty = this.idProperty;
                this.model.prototype.totalProperty = this.totalProperty;
            }

            //NOTE: it would be interesting to convert this JSON into a more functional object here
            //for example, columns w/ lookups could actually reference their target
            //we could add methods like getDisplayString(), which accept the ext record and return the appropriate display string
            Ext4.each(data.metaData.fields, function(meta){
                if (meta.jsonType == 'int' || meta.jsonType=='float' || meta.jsonType=='boolean')
                    meta.useNull = true;  //prevents Ext from assigning 0's to field when record created

                if (meta.jsonType == 'string')
                    meta.sortType = 'asUCString';

                //convert string into function
                if (meta.extFormatFn){
                    try {
                        meta.extFormatFn = eval(meta.extFormatFn);
                    }
                    catch (ex)
                    {
                        //this is potentially the sort of thing we'd want to log to mothership??
                    }
                }

                if (meta.jsonType) {
                    meta.extType = LABKEY.ext4.Util.EXT_TYPE_MAP[meta.jsonType];
                }
            });
        }

        return this.callParent([data]);
    },

    //added event to allow store to modify metadata before it is applied
    onMetaChange : function(meta) {
        this.fireEvent('datachange', meta);

        this.callParent(arguments);

        //NOTE: Ext.data.Model.onFieldAddReplace() would normally reset the idField; however, we usually end up changing the idProperty since it was not known at time of store creation
        if (this.model){
            this.model.prototype.idField = this.model.prototype.fields.get(this.model.prototype.idProperty);
        }
    },

    /*
    because our 9.1 API format returns results as objects, we transform them here.  In addition to extracting the values, Ext creates an accessor for the record's ID
    this must also be modified to support the 9.1 API.  Because I believe getId() can be called both on initial load (prior to
    when we transform the data) and after, I modified the method to test whether the field's value is an object instead of
    looking for '.value' exclusively.
    */
    createFieldAccessExpression: (function() {
        var re = /[\[\.]/;

        return function(field, fieldVarName, dataName) {
            var me     = this,
                hasMap = (field.mapping !== null),
                map    = hasMap ? field.mapping : field.name,
                result,
                operatorSearch;

            if (typeof map === 'function') {
                result = fieldVarName + '.mapping(' + dataName + ', this)';
            } else if (this.useSimpleAccessors === true || ((operatorSearch = String(map).search(re)) < 0)) {
                if (!hasMap || isNaN(map)) {
                    // If we don't provide a mapping, we may have a field name that is numeric
                    map = '"' + map + '"';
                }
                //TODO: account for field.notFromServer here...
                //also: we should investigate how convert() works and probably use this instead
                result = dataName + "[" + map + "] !== undefined ? " + dataName + "[" + map + "].value : ''";
            } else {
                result = dataName + (operatorSearch > 0 ? '.' : '') + map;
            }
            return result;
        };
    }()),

    //see note for createFieldAccessExpression()
    buildExtractors: function(force) {
        this.callParent(arguments);

        var me = this,
            idProp      = me.getIdProperty(),
            accessor,
            idField,
            map;

        if (idProp) {
            idField = me.model.prototype.fields.get(idProp);
            if (idField) {
                map = idField.mapping;
                idProp = (map !== undefined && map !== null) ? map : idProp;
            }
            accessor = me.createAccessor('["' + idProp + '"].value');

            me.getId = function(record) {
                var id = accessor.call(me, record);
                return (id === undefined || id === '') ? null : id;
            };
        }
    }
});
