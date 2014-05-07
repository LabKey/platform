/*
 * Copyright (c) 2011-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

// Rhino does not support XMLHttpRequest, so here we wrap the one from our ExtAdapter.
exports.Ajax = {
    request : function (config)
    {
        var o = LABKEY.ExtAdapter.Ajax.request(config);
        if (LABKEY.ExtAdapter.isObject(o))
            return o.responseJSON || o.responseXML || o.responseText;
        return o;
    }
};
