/*
 * Copyright (c) 2016-2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.vis.GenericChartScriptPanel', {
    extend: 'LABKEY.vis.ChartWizardPanel',

    mainTitle: 'Export as script',
    codeMirrorMode: 'text/html',
    height: 485,

    SCRIPT_TEMPLATE: '<div id="exportedChart"></div>\n' +
            '<script type="text/javascript">\n' +
            "(function() {\n" +
            "    var dependencyCallback = function() {\n" +
            "\n" +
            "        var queryConfig = {{queryConfig}};\n" +
            "        queryConfig.containerPath = '{{containerPath}}';\n" +
            "\n" +
            "        var chartConfig = {{chartConfig}};\n" +
            "\n" +
            "        LABKEY.vis.GenericChartHelper.renderChartSVG('exportedChart', queryConfig, chartConfig);\n" +
            "    };\n" +
            "\n" +
            "   // Load the script dependencies for charts. \n" +
            "   LABKEY.requiresScript('/vis/genericChart/genericChartHelper.js', function(){LABKEY.vis.GenericChartHelper.loadVisDependencies(dependencyCallback);});\n" +
            "})();\n" +
            '</script>',

    initComponent: function()
    {
        this.codeMirrorId = 'textarea-' + Ext4.id();

        this.bottomButtons = [
                '->',
                {
                        text: 'Close',
                        scope: this,
                        handler: function(){this.fireEvent('closeOptionsWindow');}
                }
        ];

        this.items = [
                this.getTitlePanel(),
                this.getCenterPanel(),
                this.getButtonBar()
        ];

        this.on('afterrender', function(cmp){
                var el = Ext4.get(this.codeMirrorId);
                if (el)
                {
                        this.codeMirror = CodeMirror.fromTextArea(el.dom, {
                                mode: this.codeMirrorMode,
                                lineNumbers: true,
                                lineWrapping: true,
                                indentUnit: 3
                        });

                        this.codeMirror.setSize(null, '350px');
                        this.codeMirror.setValue(this.compileTemplate(this.templateConfig));
                        LABKEY.codemirror.RegisterEditorInstance('export-script-textarea', this.codeMirror);
                }
        }, this);

        this.callParent();
    },

    getCenterPanel: function()
    {
        if (!this.centerPanel)
        {
                this.centerPanel = Ext4.create('Ext.panel.Panel', {
                        region: 'center',
                        cls: 'region-panel',
                        html: '<textarea id="' + this.codeMirrorId + '" name="export-script-textarea"'
                        + 'wrap="on" rows="23" cols="120" style="width: 100%;"></textarea>'
                });
        }

        return this.centerPanel;
    },

    setScriptValue: function(templateConfig)
    {
        if (this.codeMirror)
        {
                this.codeMirror.setValue(this.compileTemplate(templateConfig));
        }
        else
        {
                // The panel may not have been rendered yet, so instead we stash the template config.
                // which will be compiled after render.
                this.templateConfig = templateConfig;
        }
    },

    compileTemplate: function(input)
    {
        return this.SCRIPT_TEMPLATE
                .replace('{{chartConfig}}', LABKEY.Utils.encode(input.chartConfig))
                .replace('{{queryConfig}}', LABKEY.Utils.encode(input.queryConfig))
                .replace('{{containerPath}}', function(){return LABKEY.container.path});
    }
});