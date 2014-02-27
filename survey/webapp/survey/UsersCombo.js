/*
/*
 * Copyright (c) 2013-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.requiresScript("/dataview/DataViewUtil.js");

Ext4.define('LABKEY.ext4.GenericCombo', {

    extend  : 'Ext.form.field.ComboBox',

    dirty   : false,
    initialValue : null,

    constructor : function(config){

        Ext4.applyIf(config, {
/*
            typeAhead   : true,
            typeAheadDelay : 75,
*/
            editable    : false,
            forceSelection : true, // user must pick from list
            queryMode   : 'local'
        });

        this.callParent([config]);
    },

    initComponent : function() {

        this.getStore().on('load', function() {
            if (this.initialValue)
            {
                this.setValue(this.initialValue, null);
                this.dirty = false;
            }
        }, this);

        this.on('change', function(cmp, newValue, oldValue) {
            this.dirty = true;
        });

        this.callParent();
    },

    isDirty : function() {
        return this.dirty;
    },

    resetOriginalValue : function() {
        this.dirty = false;
    },

    setValue : function(value, doSelect) {
        this.initialValue = value;
        this.callParent([value, doSelect]);
    },

    getTypeStore : function(comboConfig) {

        // define data models
        var modelName = 'LABKEY.data.' + comboConfig.queryName;
        if (!Ext4.ModelManager.isRegistered(modelName)) {
            Ext4.define(modelName, {
                extend : 'Ext.data.Model',
                fields : [
                    {name : comboConfig.keyField,     type : 'int'},
                    {name : comboConfig.displayField              }
                ]
            });
        }

        var config = {
            model   : modelName,
            autoLoad: true,
            pageSize: 10000,
            proxy   : {
                type   : 'ajax',
                url : LABKEY.ActionURL.buildURL('query', 'selectRows.api'),
                extraParams : {
                    schemaName  : comboConfig.schemaName ? comboConfig.schemaName : 'study',
                    queryName   : comboConfig.queryName
                },
                reader : {
                    type : 'json',
                    root : 'rows'
                }
            },
            listeners : {
                load : function(s, recs, success, operation, ops) {
                    s.sort(this.displayField, 'ASC');
//                    s.insert(0, {UserId : -1, DisplayName : 'None'});
                }
            }
        };

        return Ext4.create('Ext.data.Store', config);
    }

});

Ext4.define('LABKEY.ext4.UsersCombo', {

    extend  : 'LABKEY.ext4.GenericCombo',
    alias   : 'widget.lk-userscombo',

    constructor : function(config){

        Ext4.applyIf(config, {
            store       : LABKEY.study.DataViewUtil.getUsersStore(),
            fieldLabel  : 'Users',
            valueField  : 'UserId',
            displayField : 'DisplayName'
        });

        this.callParent([config]);
    }
});

Ext4.define('LABKEY.ext4.GenericStudyCombo', {

    extend  : 'LABKEY.ext4.GenericCombo',
    alias   : 'widget.lk-genericcombo',

    // config must include keyField, displayField and queryName, optionally schemaName
    constructor : function(config){

        Ext4.applyIf(config, {
            store       : this.getTypeStore(config),
            valueField  : config.keyField,
            displayField : config.displayField
        });

        this.callParent([config]);
    }
});
