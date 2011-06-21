/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext.util = {};
Ext.lib = {};
Ext.data = {};

Ext.Msg = {
    alert : function (title, msg) {
        require("console").warn(msg);
    }
};

// Following Ext.num function is from ext-3.4.0/src/core/Ext-more.js
/*!
 * Ext JS Library 3.4.0
 * Copyright(c) 2006-2011 Sencha Inc.
 * licensing@sencha.com
 * http://www.sencha.com/license
 */
/**
 * Utility method for validating that a value is numeric, returning the specified default value if it is not.
 * @param {Mixed} value Should be a number, but any type will be handled appropriately
 * @param {Number} defaultValue The value to return if the original value is non-numeric
 * @return {Number} Value, if numeric, else defaultValue
 */
Ext.num = function(v, defaultValue){
    v = Number(Ext.isEmpty(v) || Ext.isArray(v) || typeof v == 'boolean' || (typeof v == 'string' && v.trim().length == 0) ? NaN : v);
    return isNaN(v) ? defaultValue : v;
};

