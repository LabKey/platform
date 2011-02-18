/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

var console = require("console");

// Pull in all of the labkey scripts into a single LABKEY object.
var LABKEY = require("labkey/init");

LABKEY.ActionURL = require("labkey/ActionURL").ActionURL;
LABKEY.Ajax = require("labkey/Ajax").Ajax;
LABKEY.Filter = require("labkey/Filter").Filter;
LABKEY.Message = require("labkey/Message").Message;
LABKEY.Security = require("labkey/Security").Security;
LABKEY.SecurityPolicy = require("labkey/SecurityPolicy").SecurityPolicy;
LABKEY.Utils = require("labkey/Utils").Utils;
LABKEY.Query = require("labkey/Query").Query;

// Export all symbols
for (var key in LABKEY)
    exports[key] = LABKEY[key];

