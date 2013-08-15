/*
 * Copyright (c) 2012-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.initExtAdapter = function(LABKEY, Ext){

    if (!Ext) {
        return;
    }

    Ext.ns('LABKEY.ExtAdapter');
    //console.log('using Ext version: ' + Ext.version);

    Ext.apply(LABKEY.ExtAdapter, {
        Ajax: Ext.Ajax,

        apply: Ext.apply,

        applyIf: Ext.applyIf,

        decode: Ext.JSON ? Ext.JSON.decode : Ext.util.JSON.decode,

        // TODO: Remove
        DomHelper: Ext.DomHelper,

        /**
         * Returns a boolean
         * WARNING: Different method signatures in Ext JS 3.4 vs Ext JS 4.2.1
         * 3.4.1 - ( iterable, fn, scope )
         * 4.2.1 - ( iterable, fn, [scope], [reverse] )
         */
        each: Ext.each,

        encode: Ext.JSON ? Ext.JSON.encode : Ext.util.JSON.encode,

        // TODO: Remove
        EventManager: Ext.EventManager,

        // TODO: Remove
        //note: this is still present in Ext4, but is deprecated
        extend: Ext.extend,

        // TODO: Remove
        Format: Ext.util.Format,

        // TODO: Remove
        get: Ext.get,

        // Return Type : String
        htmlEncode: Ext.util.Format.htmlEncode,

        isArray: Ext.isArray,

        isDefined: Ext.isDefined,

        isEmpty: Ext.isEmpty,

        isFunction: Ext.isFunction,

        isGecko: Ext.isGecko,

        isIE: Ext.isIE,

        isObject: Ext.isObject,

        isPrimitive: Ext.isPrimitive,

        isString: Ext.isString,

        // TODO: Remove
        Msg: Ext.Msg,

        namespace: Ext.ns,

        ns: Ext.ns,

        // TODO: Remove
        onReady: Ext.onReady,

        query: Ext.query
    });
};

LABKEY.inferHighestExtVersion = function(){
    if (window.Ext4)
        return Ext4;
    else if (window.Ext)
        return Ext;
    console.warn('A known version of ExtJS cannot be found. Some features of this page may not work correctly.');
};

LABKEY.initExtAdapter(LABKEY, LABKEY.inferHighestExtVersion());
