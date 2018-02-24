/*
 * Copyright (c) 2011-2014 LabKey Corporation
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

