/*
 * Copyright (c) 2013-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.vis.GenericChartScriptPanel', {
    extend: 'LABKEY.vis.BaseExportScriptPanel',

    SCRIPT_TEMPLATE: '<div id="exportedChart"></div>\n' +
            '<script type="text/javascript">\n' +
            "// Wrap in function to prevent leaking variables into global namespace.\n" +
            "(function() {\n" +
            "    var chartId = 'exportedChart';\n" +
            "\n" +
            "    var renderMessages = function(id, messages) {\n" +
            "        var errorDiv;\n" +
            "        var el = document.getElementById(id);\n" +
            "        var child;\n" +
            "        if (el && el.children.length > 0) {\n" +
            "            child = el.children[0];\n" +
            "        }\n" +
            "\n" +
            "        for (var i = 0; i < messages.length; i++) {\n" +
            "            errorDiv = document.createElement('div');\n" +
            "            errorDiv.setAttribute('class', 'labkey-error');\n" +
            "            errorDiv.innerHTML = messages[i];\n" +
            "            if(child) {\n" +
            "                el.insertBefore(errorDiv, child);\n" +
            "            } else {\n" +
            "                el.appendChild(errorDiv);\n" +
            "            }\n" +
            "        }\n" +
            "    };\n" +
            "\n" +
            "    var selectRowsCallback = function(responseData) {\n" +
            "        // After the data is loaded we can render the chart.\n" +
            "        // chartConfig is the saved information about the chart (labels, scales, etc.)\n" +
            "        var gch = LABKEY.vis.GenericChartHelper;\n" +
            "        var chartConfig = {{chartConfig}};\n" +
            "        chartConfig.geomOptions.showOutliers = chartConfig.pointType ? chartConfig.pointType == 'outliers' : true;\n" +
            "        var DEFAULT_WIDTH = 800, DEFAULT_HEIGHT = 600;\n" +
            "        var chartType = gch.getChartType(chartConfig.renderType, chartConfig.measures.x.normalizedType);\n" +
            "        var layerConfig = {\n" +
            "            data: responseData.rows,\n" +
            "            geom: gch.generateGeom(chartType, chartConfig.geomOptions)\n" +
            "        };\n" +
            "        var aes = gch.generateAes(chartType, chartConfig.measures, responseData.schemaName, responseData.queryName);\n" +
            "        var scales = gch.generateScales(chartType, chartConfig.measures, chartConfig.scales, aes, responseData);\n" +
            "        var labels = gch.generateLabels(chartConfig.labels);\n" +
            "        var messages = [];\n" +
            "        var validation = gch.validateXAxis(chartType, chartConfig, aes, scales, responseData.rows);\n" +
            "        \n" +
            "        if (validation.message != null) {\n" +
            "            messages.push(validation.message);\n" +
            "        }\n" +
            "\n" +
            "        if (!validation.success) {\n" +
            "            renderMessages(chartId, messages);\n" +
            "            return;\n" +
            "        }\n" +
            "\n" +
            "        validation = gch.validateYAxis(chartType, chartConfig, aes, scales, responseData.rows);\n" +
            "\n" +
            "        if (validation.message != null) {\n" +
            "            messages.push(validation.message);\n" +
            "        }\n" +
            "\n" +
            "        if (!validation.success) {\n" +
            "            renderMessages(chartId, messages);\n" +
            "            return;\n" +
            "        }\n" +
            "\n" +
            "        var layers = [];\n" +
            "\n" +
            "" +
            "        if(chartConfig.pointType == 'all') {\n" +
            "           layers.push(new LABKEY.vis.Layer({\n" +
            "               data: responseData.rows,\n" +
            "               geom: gch.generatePointGeom(chartConfig.geomOptions),\n" +
            "               aes: {hoverText: gch.generatePointHover(chartConfig.measures)}\n" +
            "           }));\n" +
            "        }\n" +
            "\n" +
            "        layers.push(new LABKEY.vis.Layer(layerConfig));\n" +
            "        var plotConfig = {\n" +
            "            renderTo: chartId,\n" +
            "            width: chartConfig.width ? chartConfig.width : DEFAULT_WIDTH,\n" +
            "            height: chartConfig.height? chartConfig.height : DEFAULT_HEIGHT,\n" +
            "            labels: labels,\n" +
            "            aes: aes,\n" +
            "            scales: scales,\n" +
            "            layers: layers,\n" +
            "            data: responseData.rows\n" +
            "        }\n" +
            "        var plot = new LABKEY.vis.Plot(plotConfig);\n" +
            "        \n" +
            "        plot.render();\n" +
            "        renderMessages(chartId, messages);\n" +
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

    compileTemplate: function(input) {
        return this.SCRIPT_TEMPLATE
                .replace('{{chartConfig}}', LABKEY.Utils.encode(input.chartConfig))
                .replace('{{queryConfig}}', LABKEY.Utils.encode(input.queryConfig))
                .replace('{{containerPath}}', function(){return LABKEY.container.path});
    }
});