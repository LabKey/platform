/*
 * Copyright (c) 2012-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
var Ext = require("Ext4").Ext;
var console = require("console");

exports.Adapter = {
    apply: Ext.apply,

    applyIf: Ext.applyIf,

    DomHelper: function() { throw Error("DomHelper is not implemented on server-side JS");},

    each: Ext.each,

    EventManager: function() { throw Error("Ext.EventManager is not implemented on server-side JS");},

    //note: this is still present in Ext4, but is deprecated
    extend: function() { throw Error("Ext.extend is not implemented on server-side JS");},

    get: Ext.get,

    //this is b/c in Ext4, Ext.util.Format depends on the entire class system, and we are trying to avoid loading this
    //it is not used in the core API
    htmlEncode: function() { throw Error("htmlEncode is not implemented on server-side JS");},

    //see above
    format: function() { throw Error("Ext.Format is not implemented on server-side JS");},

    isArray: Ext.isArray,

    isDefined: Ext.isDefined,

    isEmpty: Ext.isEmpty,

    isFunction: Ext.isFunction,

    isGecko: Ext.isGecko,

    isIE: Ext.isIE,

    isObject: Ext.isObject,

    isPrimitive: Ext.isPrimitive,

    isString: Ext.isString,

    Msg: Ext.Msg,

    namespace: Ext.ns,

    ns: Ext.ns,

    onReady: Ext.onReady,

    query: Ext.query,

    Version: Ext.Version
};

if (Ext.Version == 4){
    exports.Adapter.Ajax = {
        request: require("/labkey/adapter/ext4Bridge").request
    };

    exports.Adapter.decode = Ext.JSON.decode;

    exports.Adapter.encode = Ext.JSON.encode;

}
else if (Ext.Version == 3){
    exports.Adapter.Ajax = Ext.Ajax;

    exports.Adapter.decode = Ext.util.JSON.decode;

    exports.Adapter.encode = Ext.util.JSON.encode;

    //NOTE: must be placed at end
    Ext.lib = {
        Ajax : require("labkey/adapter/bridge")
    };
}

