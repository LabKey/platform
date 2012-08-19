var Ext = require("Ext").Ext;
var console = require("console");

exports.Adapter = {
    apply: Ext.apply,

    applyIf: Ext.applyIf,

    domAppend: function() { throw Error("DomHelper is not implemented on server-side JS");},

    each: Ext.each,

    EventManager: Ext.EventManager,

    //note: this is still present in Ext4, but is deprecated
    extend: Ext.extend,

    get: Ext.get,

    //htmlEncode: Ext.util.Format.htmlEncode,
    htmlEncode: Ext.emptyFn,

    isArray: Ext.isArray,

    isDefined: Ext.isDefined,

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
}

if (Ext.Version == 4){
    exports.Adapter.Ajax = {
        request: require("/labkey/adapter/ext4Bridge").request
    }

    exports.Adapter.decode = Ext.JSON.decode;

    exports.Adapter.encode = Ext.JSON.encode;

}
else if (Ext.Version == 3){
    exports.Adapter.Ajax = Ext.Ajax;

    exports.Adapter.decode = Ext.util.JSON.decode;
    //decode: Ext.JSON ? Ext.JSON.decode : Ext.util.JSON.decode

    exports.Adapter.encode = Ext.util.JSON.encode;

    exports.Format = Ext.util.Format;

    //NOTE: must be placed at end
    Ext.lib = {
        Ajax : require("labkey/adapter/bridge")
    };
}

