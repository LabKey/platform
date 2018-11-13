/*
 * Copyright (c) 2013-2018 LabKey Corporation
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
            "    var validateChartConfig = function(chartConfig, aes, scales, measureStore) {\n" +
            "        var hasNoDataMsg = LABKEY.vis.GenericChartHelper.validateResponseHasData(measureStore, false);\n" +
            "        if (hasNoDataMsg != null)\n" +
            "            return {success: false, messages: [hasNoDataMsg]};\n" +
            "\n" +
            "        var messages = [], firstRecord = measureStore.records()[0], measureNames = Object.keys(chartConfig.measures);\n" +
            "        for (var i = 0; i < measureNames.length; i++) {\n" +
            "            var measuresArr = LABKEY.vis.GenericChartHelper.ensureMeasuresAsArray(chartConfig.measures[measureNames[i]]);\n" +
            "            for (var j = 0; j < measuresArr.length; j++) {\n" +
            "                var measure = measuresArr[j];\n" +
            "                if (Ext4.isObject(measure)) {\n" +
            "                    if (measure.name && !Ext4.isDefined(firstRecord[measure.name])) {\n" +
            "                        return {success: false, messages: ['The measure, ' + measure.label + ', is not available. It may have been renamed or removed.']};\n" +
            "                    }\n" +
            "\n" +
            "                    var validation;\n" +
            "                    if (measureNames[i] === 'y') {\n" +
            "                        var yAes = {y: LABKEY.vis.GenericChartHelper.getYMeasureAes(measure)};\n" +
            "                        validation = LABKEY.vis.GenericChartHelper.validateAxisMeasure(chartConfig.renderType, measure, 'y', yAes, scales, measureStore.records());\n" +
            "                    }\n" +
            "                    else if (measureNames[i] === 'x' || measureNames[i] === 'xSub') {\n" +
            "                        validation = LABKEY.vis.GenericChartHelper.validateAxisMeasure(chartConfig.renderType, measure, measureNames[i], aes, scales, measureStore.records());\n" +
            "                    }\n" +
            "\n" +
            "                    if (Ext4.isObject(validation)) {\n" +
            "                        if (validation.message != null)\n" +
            "                            messages.push(validation.message);\n" +
            "                        if (!validation.success)\n" +
            "                            return {success: false, messages: messages};\n" +
            "                    }\n" +
            "                }\n" +
            "            }\n" +
            "        }\n" +
            "\n" +
            "        return {success: true, messages: messages};\n" +
            "\n" +
            "    };\n" +
            "\n" +
            "    var selectRowsCallback = function(measureStore) {\n" +
            "        // After the data is loaded we can render the chart.\n" +
            "        var responseMetaData = measureStore.getResponseMetadata();\n" +
            "\n" +
            "        // chartConfig is the saved information about the chart (labels, scales, etc.)\n" +
            "        var chartConfig = {{chartConfig}};\n" +
            "        if (!chartConfig.hasOwnProperty('width') || chartConfig.width == null) chartConfig.width = 1000;\n" +
            "        if (!chartConfig.hasOwnProperty('height') || chartConfig.height == null) chartConfig.height = 600;\n" +
            "\n" +
            "        var xAxisType = chartConfig.measures.x ? (chartConfig.measures.x.normalizedType || chartConfig.measures.x.type) : null;\n" +
            "        var chartType = LABKEY.vis.GenericChartHelper.getChartType(chartConfig.renderType, xAxisType);\n" +
            "        var aes = LABKEY.vis.GenericChartHelper.generateAes(chartType, chartConfig.measures, responseMetaData.schemaName, responseMetaData.queryName);\n" +
            "        var valueConversionResponse = LABKEY.vis.GenericChartHelper.doValueConversion(chartConfig, aes, chartType, measureStore.records()); \n" +
            "        if (!Ext4.Object.isEmpty(valueConversionResponse.processed)) {\n"+
            "               Ext4.Object.merge(chartConfig.measures, valueConversionResponse.processed);\n"+
            "               aes = LABKEY.vis.GenericChartHelper.generateAes(chartType, chartConfig.measures, responseMetaData.schemaName, responseMetaData.queryName);\n"+
            "        }\n"+
            "        var data = measureStore.records();\n" +
            "        if (chartType == 'scatter_plot' && data.length > chartConfig.geomOptions.binThreshold) {\n"+
            "               chartConfig.geomOptions.binned = true;\n"+
            "        }\n"+
            "        var scales = LABKEY.vis.GenericChartHelper.generateScales(chartType, chartConfig.measures, chartConfig.scales, aes, measureStore);\n" +
            "        var geom = LABKEY.vis.GenericChartHelper.generateGeom(chartType, chartConfig.geomOptions);\n" +
            "        var labels = LABKEY.vis.GenericChartHelper.generateLabels(chartConfig.labels);\n" +
            "\n" +
            "        if (chartType == 'bar_chart' || chartType == 'pie_chart') {\n" +
            "            var dimName = null, subDimName = null; measureName = null,\n"+
            "               aggType = 'COUNT';\n"+
            "\n" +
            "            if (chartConfig.measures.x) {\n" +
            "               dimName = chartConfig.measures.x.converted ? chartConfig.measures.x.convertedName : chartConfig.measures.x.name;\n" +
            "            }\n"+
            "            if (chartConfig.measures.xSub) {\n" +
            "               subDimName = chartConfig.measures.xSub.converted ? chartConfig.measures.xSub.convertedName : chartConfig.measures.xSub.name;\n" +
            "            }\n"+
            "            if (chartConfig.measures.y) {\n" +
            "               measureName = chartConfig.measures.y.converted ? chartConfig.measures.y.convertedName : chartConfig.measures.y.name;\n" +
            "\n" +
            "               if (Ext4.isDefined(chartConfig.measures.y.aggregate)) {\n" +
            "                  aggType = chartConfig.measures.y.aggregate;\n" +
            "               }\n" +
            "               else if (measureName != null) {\n" +
            "                  aggType = 'SUM';\n" +
            "               }\n" +
            "            }\n"+
            "\n" +
            "            data = LABKEY.vis.getAggregateData(data, dimName, subDimName, measureName, aggType, '[Blank]', false);\n" +
            "        }\n" +
            "\n" +
            "        var validation = validateChartConfig(chartConfig, aes, scales, measureStore);\n" +
            "        renderMessages(chartId, validation.messages);\n" +
            "        if (!validation.success)\n" +
            "            return;\n" +
            "\n" +
            "        var plotConfigArr = LABKEY.vis.GenericChartHelper.generatePlotConfigs(chartId, chartConfig, labels, aes, scales, geom, data);\n" +
            "        Ext4.each(plotConfigArr, function(plotConfig) {\n" +
            "            if (chartType === 'pie_chart') {\n" +
            "                new LABKEY.vis.PieChart(plotConfig);\n" +
            "            }\n" +
            "            else {\n" +
            "                var plot = new LABKEY.vis.Plot(plotConfig);\n" +
            "                plot.render();\n" +
            "            }\n" +
            "        }, this);\n" +
            "    };\n" +
            "\n" +
            "    var dependencyCallback = function() {\n" +
            "        // When all the dependencies are loaded we then load the data via the MeasureStore selectRows API.\n" +
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
            "        LABKEY.Query.MeasureStore.selectRows(queryConfig);\n" +
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