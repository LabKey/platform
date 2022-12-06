/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

var console = require("console");

// Option 1: This is a custom JS module defined in RhinoService
var effectiveContainer = require("effectiveContainer").effectiveContainerId
console.log('effective container: ' + effectiveContainer)

// Option 2: This is poked in using require/preScript
console.log('containerid: ' + EffectiveContainerId)
const containerId = EffectiveContainerId || null;

var props = org.labkey.api.util.PageFlowUtil.jsInitObject(containerId || null);
for (var key in props)
    exports[key] = props[key];

