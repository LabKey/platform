/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

var console = require("console");

// Option 1: This is a custom JS module defined in RhinoService
var serverContext = require("serverContext")
console.log('container via serverContext: ' + serverContext.container.id)

// Option 2: This is poked in using require/preScript. This is not needed if we go with the option above.
console.log('containerid from preScript: ' + EffectiveContainerId)

for (var key in serverContext)
    exports[key] = serverContext[key];

