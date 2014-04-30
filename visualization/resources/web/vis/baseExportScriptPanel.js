/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('LABKEY.vis.BaseExportScriptPanel', {
    extend: 'Ext.Panel',

    SCRIPT_TEMPLATE: null,

    constructor: function(config){
        config.padding = '10px 0 0 0';
        config.border = false;
        config.frame = false;
        config.codeMirrorId = 'textarea-' + Ext4.id();
        config.html = '<textarea id="' + config.codeMirrorId + '" name="export-script-textarea"'
                + 'wrap="on" rows="23" cols="120" style="width: 100%;"></textarea>';

        this.callParent([config]);
    },

    initComponent: function(){
        this.buttons = [{
            text: 'Close',
            scope: this,
            handler: function(){this.fireEvent('closeOptionsWindow');}
        }];

        this.on('afterrender', function(cmp){
            var el = Ext4.get(this.codeMirrorId);
            var size = cmp.getSize();
            if (el) {
                this.codeMirror = CodeMirror.fromTextArea(el.dom, {
                    mode            : 'text/html',
                    lineNumbers     : true,
                    lineWrapping    : true,
                    indentUnit      : 3
                });

                this.codeMirror.setSize(null, size.height + 'px');
                this.codeMirror.setValue(this.compileTemplate(this.templateConfig));
                LABKEY.codemirror.RegisterEditorInstance('export-script-textarea', this.codeMirror);
            }
        }, this);
        this.callParent();
    },

    setScriptValue: function(templateConfig){
        if(this.codeMirror) {
            this.codeMirror.setValue(this.compileTemplate(templateConfig));
        } else {
            // The panel may not have been rendered yet, so instead we stash the template config.
            // which will be compiled after render.
            this.templateConfig = templateConfig;
        }
    },

    compileTemplate: function(input) {
        return this.SCRIPT_TEMPLATE.replace('{{chartConfig}}', LABKEY.Utils.encode(input.chartConfig));
    }
});