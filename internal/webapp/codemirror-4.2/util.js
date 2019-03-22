/*
 * Copyright (c) 2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
if (LABKEY.Utils.isDefined(window.Ext)) {
    Ext.namespace("LABKEY.codemirror");
}
else {
    LABKEY.codemirror = {};
}

LABKEY.codemirror.RegisterEditorInstance = function(id, instance){

    var cm = LABKEY.CodeMirror || {};
    cm[id] = instance;

    LABKEY.CodeMirror = cm;
};
