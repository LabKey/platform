/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

// The labkey/adapter/bridge.js need Ext so export Ext before require()'ing
exports.Ext = Ext;

Ext.lib = {
    Ajax : require("labkey/adapter/bridge")
};

