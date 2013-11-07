/*
 * Copyright (c) 2012-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
(function() {

    //
    // Determine working version
    //
    var ExtConfig;
    if (window.Ext4) {
        ExtConfig = {
            version: window.Ext4.getVersion().version,
            lib: window.Ext4
        }
    }
    else if (window.Ext) {
        ExtConfig = {
            version: window.Ext.version,
            lib: window.Ext
        }
    }

    if (!ExtConfig) {
        console.warn('A known version of ExtJS cannot be found. Some features of this page may not work correctly.');
        return;
    }

    //
    // Declare ExtAdapter
    //
    var Ext = ExtConfig.lib;

    Ext.ns('LABKEY.ExtAdapter');
//    console.log('using Ext version: ' + ExtConfig.version);

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

        query: Ext.query,

        version: ExtConfig.version
    });

})();
