/*
 * Copyright (c) 2011-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

var LABKEY = require("./init");
LABKEY.ActionURL = require("./ActionURL").ActionURL;
LABKEY.ExtAdapter = require("ExtAdapter").Adapter;

LABKEY.ext = {
    FormHelper: {
        validate: function () { throw Error("Not yet implemented"); }
    }
};
