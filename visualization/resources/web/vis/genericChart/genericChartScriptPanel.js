/*
 * Copyright (c) 2013-2016 LabKey Corporation
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
            "// Wrap in function to prevent leaking variables into global namespace.\n" +
            "(function() {\n" +
            "    var chartId = 'exportedChart';\n" +
            "\n" +
            "    var renderMessages = function(id, messages) {\n" +
            "        if (messages && messages.length > 0) {\n" +
            "            var errorDiv = document.createElement('div');\n" +
            "            errorDiv.setAttribute('style', 'padding: 10px; background-color: #ffe5e5; color: #d83f48; font-weight: bold;');\n" +
            "            errorDiv.innerHTML = messages.join('<br/>');\n" +
            "            document.getElementById(id).appendChild(errorDiv);\n" +
            "        }\n" +
            "    };\n" +
            "\n" +
            "    var validateChartConfig = function(chartConfig, aes, scales, responseData) {\n" +
            "        var hasNoDataMsg = LABKEY.vis.GenericChartHelper.validateResponseHasData(responseData, false);\n" +
            "        if (hasNoDataMsg != null)\n" +
            "            return {success: false, messages: [hasNoDataMsg]};\n" +
            "\n" +
            "        var measureNames = Object.keys(chartConfig.measures);\n" +
            "        for (var i = 0; i < measureNames.length; i++) {\n" +
            "            var measureProps = chartConfig.measures[measureNames[i]];\n" +
            "            if (measureProps && measureProps.name && responseData.rows[0][measureProps.name] == undefined)\n" +
            "                return {success: false, messages: ['The measure, ' + measureProps.label + ', is not available. It may have been renamed or removed.']};\n" +
            "        }\n" +
            "\n" +
            "        var messages = [], axisMeasureNames = ['x', 'y'];\n" +
            "        for (var i = 0; i < axisMeasureNames.length; i++) {\n" +
            "            if (measureNames.indexOf(axisMeasureNames[i]) > 0) {\n" +
            "                var validation = LABKEY.vis.GenericChartHelper.validateAxisMeasure(chartConfig.renderType, chartConfig, axisMeasureNames[i], aes, scales, responseData.rows);\n" +
            "                if (validation.message != null)\n" +
            "                    messages.push(validation.message);\n" +
            "                if (!validation.success)\n" +
            "                    return {success: false, messages: messages};\n" +
            "            }\n" +
            "        }\n" +
            "\n" +
            "        return {success: true, messages: messages};\n" +
            "\n" +
            "    };\n" +
            "\n" +
            "    var selectRowsCallback = function(responseData) {\n" +
            "        // After the data is loaded we can render the chart.\n" +
            "\n" +
            "        // chartConfig is the saved information about the chart (labels, scales, etc.)\n" +
            "        var chartConfig = {{chartConfig}};\n" +
            "        if (!chartConfig.hasOwnProperty('width') || chartConfig.width == null) chartConfig.width = 800;\n" +
            "        if (!chartConfig.hasOwnProperty('height') || chartConfig.height == null) chartConfig.height = 600;\n" +
            "\n" +
            "        var xAxisType = chartConfig.measures.x ? (chartConfig.measures.x.normalizedType || chartConfig.measures.x.type) : null;\n" +
            "        var chartType = LABKEY.vis.GenericChartHelper.getChartType(chartConfig.renderType, xAxisType);\n" +
            "        var aes = LABKEY.vis.GenericChartHelper.generateAes(chartType, chartConfig.measures, responseData.schemaName, responseData.queryName);\n" +
            "        var scales = LABKEY.vis.GenericChartHelper.generateScales(chartType, chartConfig.measures, chartConfig.scales, aes, responseData);\n" +
            "        var geom = LABKEY.vis.GenericChartHelper.generateGeom(chartType, chartConfig.geomOptions);\n" +
            "        var labels = LABKEY.vis.GenericChartHelper.generateLabels(chartConfig.labels);\n" +
            "\n" +
            "        var data = responseData.rows;\n" +
            "        if (chartType == 'bar_chart' || chartType == 'pie_chart') {\n" +
            "            var dimName = chartConfig.measures.x ? chartConfig.measures.x.name : null;\n" +
            "            var measureName = chartConfig.measures.y ? chartConfig.measures.y.name : null;\n" +
            "            var aggType = measureName != null ? 'SUM' : 'COUNT';\n" +
            "            data = LABKEY.vis.GenericChartHelper.generateAggregateData(data, dimName, measureName, aggType, '[Blank]');\n" +
            "        }\n" +
            "\n" +
            "        var plotConfig = LABKEY.vis.GenericChartHelper.generatePlotConfig(chartId, chartConfig, labels, aes, scales, geom, data);\n" +
            "\n" +
            "        var validation = validateChartConfig(chartConfig, aes, scales, responseData);\n" +
            "        renderMessages(chartId, validation.messages);\n" +
            "        if (!validation.success)\n" +
            "            return;\n" +
            "\n" +
            "        var plot;\n" +
            "        if (chartType == 'pie_chart') {\n" +
            "            new LABKEY.vis.PieChart(plotConfig);\n" +
            "        }\n" +
            "        else {\n" +
            "            plot = new LABKEY.vis.Plot(plotConfig);\n" +
            "            plot.render();\n" +
            "        }\n" +
            "    };\n" +
            "\n" +
            "    var dependencyCallback = function() {\n" +
            "        // When all the dependencies are loaded we then load the data via selectRows.\n" +
            "        // The queryConfig object stores all the information needed to make a selectRows request.\n" +
            "        var queryConfig = {{queryConfig}};\n" +
            "                \n" +
            "        if (queryConfig.filterArray && queryConfig.filterArray.length > 0) {\n" +
            "            var filters = [];\n" +
            "\n" +
            "            for (var i = 0; i < queryConfig.filterArray.length; i++) {\n" +
            "                var f = queryConfig.filterArray[i];\n" +
            "                filters.push(LABKEY.Filter.create(f.name,  f.value, LABKEY.Filter.getFilterTypeForURLSuffix(f.type)));\n" +
            "            }\n" +
            "\n" +
            "            queryConfig.filterArray = filters;\n" +
            "        }\n" +
            "\n" +
            "        queryConfig.success = selectRowsCallback;\n" +
            "        queryConfig.containerPath = \"{{containerPath}}\";\n" +
            "        LABKEY.Query.selectRows(queryConfig);\n" +
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