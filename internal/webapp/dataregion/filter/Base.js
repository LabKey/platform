/*
 * Copyright (c) 2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.dataregion.filter.Base', {

    extend: 'Ext.panel.Panel',

    model: undefined,

    emptyDisplayValue: '[Blank]',

    filterOptimization: false,

    initComponent : function() {

        if (!Ext4.isObject(this.model)) {
            console.error(this.$className, 'requires a "model" object/instance configuration.');
            return;
        }
        else if (!Ext4.isString(this.model.$className)) { // object configuration
            this.activeModel = Ext4.create('LABKEY.dataregion.filter.Model', this.model);
        }
        else {
            this.activeModel = this.model;
        }

        this.beforeInit();

        this.callParent();

        this.afterInit();
    },

    afterInit : function() {},
    beforeInit : function() {},

    // Override to provide own view validation
    checkValid : function() {
        return true;
    },

    getFilters : function() {
        console.error('All classes which extend', this.$className, 'must implement getFilters()');
    },

    setFilters : function(filterArray) {
        console.error('All classes which extend', this.$className, 'must implement setFilters(filterArray)');
    },

    getModel : function() {
        return this.activeModel;
    },

    formatValue : function(val) {
        var column = this.getModel().get('column');
        if (column.extFormatFn) {
            try {
                this.column.extFormatFn = eval(this.column.extFormatFn);
            }
            catch (error) {
                console.log('improper extFormatFn: ' + this.column.extFormatFn);
            }

            if (Ext4.isFunction(this.column.extFormatFn)) {
                val = this.column.extFormatFn(val);
            }
        }
        else if (this.jsonType == 'int') {
            val = parseInt(val);
        }
        return val;
    }
});