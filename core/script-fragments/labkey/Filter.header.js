/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

var LABKEY = require("./init");
LABKEY.ActionURL = require("./ActionURL").ActionURL;

LABKEY.ext = {
    FormHelper: {
        validate: function () { throw Error("Not yet implemented"); }
    }
};

var Ext = require("Ext").Ext;

