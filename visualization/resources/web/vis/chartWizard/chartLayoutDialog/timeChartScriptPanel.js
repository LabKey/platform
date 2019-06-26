/*
 * Copyright (c) 2016-2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('LABKEY.vis.TimeChartScriptPanel', {
    extend: 'LABKEY.vis.ChartWizardPanel',

    mainTitle: 'Export as script',
    codeMirrorMode: 'text/html',
    height: 485,

    SCRIPT_TEMPLATE:
            "<div id='exportedChart'></div>\n" +
            "<script type='text/javascript'>\n" +
            "// Wrap in function to prevent leaking variables into global namespace.\n" +
            "(function() {\n" +
            "    var dependencyCallback = function() {\n" +
            "        var chartConfig = {{chartConfig}};\n" +
            "\n" +
            "        // When all the dependencies are loaded, we load the data using time chart helper getChartData\n" +
            "        var queryConfig = {\n" +
            "            containerPath: \"{{containerPath}}\",\n" +
            "            nounSingular: \"{{studyNounSingular}}\",\n" +
            "            subjectColumnName: \"{{studyNounColumnName}}\",\n" +
            "            //Uncomment any of the properties below to override the default value from the TimeChartHelper\n" +
            "            // dataLimit: 10000,\n" +
            "            // maxCharts: 20,\n" +
            "            // defaultMultiChartHeight: 380,\n" +
            "            // defaultSingleChartHeight: 600,\n" +
            "            // defaultWidth: 1075,\n" +
            "            // defaultNumberFormat: function(v) { return v.toFixed(1); }\n" +
            "        };\n" +
            "\n" +
            "        LABKEY.vis.TimeChartHelper.renderChartSVG('exportedChart', queryConfig, chartConfig);\n" +
            "    };\n" +
            "\n" +
            "    // Load the script dependencies for charts. \n" +
            "    LABKEY.requiresScript('/vis/timeChart/timeChartHelper.js', function(){LABKEY.vis.TimeChartHelper.loadVisDependencies(dependencyCallback);});\n" +
            "})();\n" +
            "</script>",

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
                    .replace('{{studyNounSingular}}', LABKEY.moduleContext.study.subject.nounSingular)
                    .replace('{{studyNounColumnName}}', LABKEY.moduleContext.study.subject.columnName)
                    .replace('{{containerPath}}', function(){return LABKEY.container.path});
        }
});