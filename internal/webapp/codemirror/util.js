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
