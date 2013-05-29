Ext.namespace("LABKEY.codemirror");

LABKEY.codemirror.RegisterEditorInstance = function(id, instance){

    var cm = LABKEY.CodeMirror || {};
    cm[id] = instance;

    LABKEY.CodeMirror = cm;
};
