/*
 * Copyright (c) 2012-2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
/**
 * 32939: Ext.Error.raise is defined using the Ext.Class "statics" features. We don't currently support
 * Ext.Class on the server-side so the "statics" functions are not applied. Here are redefining Ext.Error.raise
 * to be suitable for use on the server-side.
 */
Ext.Error.raise = function(err) {
    err = err || {};
    if (Ext.isString(err)) {
        err = { msg: err };
    }

    throw new Ext.Error(err);
};

Ext.Msg = {
    alert : function (title, msg) {
        require("console").warn(msg);
    }
};

Ext.num = Ext.Number.from;

// Rhino resolves Function-constructor code against the global scope, where this module's Ext is not visible
(function() {
    var cache = {};

    function create(args) {
        var body = args.pop() || '';
        return new Function('Ext', 'return function(' + args.join(',') + '){' + body + '\n}')(Ext);
    }

    Ext.functionFactory = function() {
        return create(Array.prototype.slice.call(arguments));
    };

    Ext.cacheableFunctionFactory = function() {
        var args = Array.prototype.slice.call(arguments),
            key = args.join('');

        return cache[key] || (cache[key] = create(args));
    };
})();
