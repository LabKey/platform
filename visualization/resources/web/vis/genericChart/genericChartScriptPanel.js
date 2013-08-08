/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.vis.GenericChartScriptPanel', {
    extend: 'Ext.Panel',

    SCRIPT_TEMPLATE: '<div id="exportedChart"></div>\n' +
            '<script type="text/javascript">\n' +
            "// Wrap in function to prevent leaking variables into global namespace.\n" +
            "(function() {\n" +
            "    var chartId = 'exportedChart';\n" +
            "    var loadVisDependencies = function(callback, scope) {\n" +
            "        var devScripts = [\n" +
            "            '/vis/lib/d3-2.0.4.min.js',\n" +
            "            '/vis/lib/raphael-min-2.1.0.js',\n" +
            "            '/vis/lib/patches.js',\n" +
            "            '/vis/src/utils.js',\n" +
            "            '/vis/src/geom.js',\n" +
            "            '/vis/src/stat.js',\n" +
            "            '/vis/src/scale.js',\n" +
            "            '/vis/src/layer.js',\n" +
            "            '/vis/src/plot.js',\n" +
            "            '/vis/genericChart/genericChartHelper.js'\n" +
            "        ];\n" +
            "        var productionScripts = [\n" +
            "            '/vis/lib/d3-2.0.4.min.js',\n" +
            "            '/vis/lib/raphael-min-2.1.0.js',\n" +
            "            '/vis/vis.min.js',\n" +
            "            '/vis/genericChart/genericChartHelper.js'\n" +
            "        ];\n" +
            "        LABKEY.requiresScript((LABKEY.devMode ? devScripts : productionScripts), true, callback, scope, true);\n" +
            "    };\n" +
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
            "        var plotConfig = {\n" +
            "            renderTo: chartId,\n" +
            "            width: chartConfig.width ? chartConfig.width : DEFAULT_WIDTH,\n" +
            "            height: chartConfig.height? chartConfig.height : DEFAULT_HEIGHT,\n" +
            "            labels: labels,\n" +
            "            aes: aes,\n" +
            "            scales: scales,\n" +
            "            layers: [new LABKEY.vis.Layer(layerConfig)],\n" +
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
            "        LABKEY.Query.selectRows(queryConfig);\n" +
            "    };\n" +
            "\n" +
            "    loadVisDependencies(dependencyCallback);\n" +
            "})();\n" +
            '</script>',

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
        return this.SCRIPT_TEMPLATE
                .replace('{{chartConfig}}', LABKEY.ExtAdapter.encode(input.chartConfig))
                .replace('{{queryConfig}}', LABKEY.ExtAdapter.encode(input.queryConfig));
    }
});