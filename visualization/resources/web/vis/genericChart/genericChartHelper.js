/*
 * Copyright (c) 2013-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
if(!LABKEY.vis) {
    LABKEY.vis = {};
}

/**
 * @namespace Namespace used to encapsulate functions related to creating Generic Charts (Box, Scatter, etc.). Used in the
 * Generic Chart Wizard and when exporting Generic Charts as Scripts.
 */
LABKEY.vis.GenericChartHelper = new function(){

    var getRenderTypes = function() {
        return [
            {
                name: 'bar_chart',
                title: 'Bar',
                imgUrl: LABKEY.contextPath + '/visualization/images/barchart.png',
                fields: [
                    {name: 'x', label: 'X Categories', required: true, nonNumericOnly: true},
                    {name: 'y', label: 'Y Axis', numericOnly: true}
                ],
                layoutOptions: {line: true, opacity: true, axisBased: true}
            },
            {
                name: 'box_plot',
                title: 'Box',
                imgUrl: LABKEY.contextPath + '/visualization/images/boxplot.png',
                fields: [
                    {name: 'x', label: 'X Axis Grouping'},
                    {name: 'y', label: 'Y Axis', required: true, numericOnly: true},
                    {name: 'color', label: 'Color', nonNumericOnly: true},
                    {name: 'shape', label: 'Shape', nonNumericOnly: true}
                ],
                layoutOptions: {point: true, box: true, line: true, opacity: true, axisBased: true}
            },
            {
                name: 'pie_chart',
                title: 'Pie',
                imgUrl: LABKEY.contextPath + '/visualization/images/piechart.png',
                fields: [
                    {name: 'x', label: 'Categories', required: true, nonNumericOnly: true},
                    {name: 'y', label: 'Measure', numericOnly: true}
                ],
                layoutOptions: {pie: true}
            },
            {
                name: 'scatter_plot',
                title: 'Scatter',
                imgUrl: LABKEY.contextPath + '/visualization/images/scatterplot.png',
                fields: [
                    {name: 'x', label: 'X Axis', required: true},
                    {name: 'y', label: 'Y Axis', required: true, numericOnly: true},
                    {name: 'color', label: 'Color', nonNumericOnly: true},
                    {name: 'shape', label: 'Shape', nonNumericOnly: true}
                ],
                layoutOptions: {point: true, box: false, line: false, opacity: true, axisBased: true, binnable: true}
            }
        ];
    };

    /**
     * Gets the chart type (i.e. box or scatter).
     * @param {String} renderType The selected renderType, this can be SCATTER_PLOT, BOX_PLOT, or BAR_CHART. Determined
     * at chart creation time in the Generic Chart Wizard.
     * @param {String} xAxisType The datatype of the x-axis, i.e. String, Boolean, Number.
     * @returns {String}
     */
    var getChartType = function(renderType, xAxisType) {
        if (renderType === "bar_chart" || renderType === "pie_chart"
            || renderType === "box_plot" || renderType === "scatter_plot") {
            return renderType;
        }

        if(!xAxisType) {
            // On some charts (non-study box plots) we don't require an x-axis, instead we generate one box plot for
            // all of the data of your y-axis. If there is no xAxisType, then we have a box plot. Scatter plots require
            // an x-axis measure.
            return 'box_plot';
        }



        return (xAxisType === 'string' || xAxisType === 'boolean') ? 'box_plot' : 'scatter_plot';
    };

    /**
     * Generate a default label for the selected measure for the given renderType.
     * @param renderType
     * @param measureName - the chart type's measure name
     * @param properties - properties for the selected column
     */
    var getDefaultLabel = function(renderType, measureName, properties)
    {
        var label = properties ? properties.label || properties.queryName : '';

        if ((renderType == 'bar_chart' || renderType == 'pie_chart') && measureName == 'y')
            label = 'Sum of ' + label;

        return label;
    };

    /**
     * Given the saved labels object we convert it to include all label types (main, x, and y). Each label type defaults
     * to empty string ('').
     * @param {Object} labels The saved labels object.
     * @returns {Object}
     */
    var generateLabels = function(labels) {
        return {
            main: {
                value: labels.main ? labels.main : ''
            },
            subtitle: {
                value: labels.subtitle ? labels.subtitle : ''
            },
            footer: {
                value: labels.footer ? labels.footer : ''
            },
            x: {
                value: labels.x ? labels.x : ''
            },
            y: {
                value: labels.y ? labels.y : ''
            }
        };
    };

    /**
     * Generates an object containing {@link LABKEY.vis.Scale} objects used for the chart.
     * @param {String} chartType The chartType from getChartType.
     * @param {Object} measures The measures from generateMeasures.
     * @param {Object} savedScales The scales object from the saved chart config.
     * @param {Object} aes The aesthetic map object from genereateAes.
     * @param {Object} responseData The data from selectRows.
     * @param {Function} defaultFormatFn used to format values for tick marks.
     * @returns {Object}
     */
    var generateScales = function(chartType, measures, savedScales, aes, responseData, defaultFormatFn) {
        var scales = {};
        var data = responseData.rows;
        var fields = responseData.metaData.fields;
        var subjectColumn = 'ParticipantId';

        if (LABKEY.moduleContext.study && LABKEY.moduleContext.study.subject)
            subjectColumn = LABKEY.moduleContext.study.subject.columnName;

        if (chartType === "box_plot")
        {
            scales.x = {
                scaleType: 'discrete', // Force discrete x-axis scale for box plots.
                sortFn: LABKEY.vis.discreteSortFn,
                tickLabelMax: 25
            };

            var yMin = d3.min(data, aes.y);
            var yMax = d3.max(data, aes.y);
            var yPadding = ((yMax - yMin) * .1);
            if (savedScales.y && savedScales.y.trans == "log")
            {
                // When subtracting padding we have to make sure we still produce valid values for a log scale.
                // log([value less than 0]) = NaN.
                // log(0) = -Infinity.
                if (yMin - yPadding > 0)
                {
                    yMin = yMin - yPadding;
                }
            }
            else
            {
                yMin = yMin - yPadding;
            }

            scales.y = {
                min: yMin,
                max: yMax + yPadding,
                scaleType: 'continuous',
                trans: savedScales.y ? savedScales.y.trans : 'linear'
            };
        }
        else
        {
            var xMeasureType = _getMeasureType(measures.x);
            if (xMeasureType == "float" || xMeasureType == "int")
            {
                scales.x = {
                    scaleType: 'continuous',
                    trans: savedScales.x ? savedScales.x.trans : 'linear'
                };
            } else
            {
                scales.x = {
                    scaleType: 'discrete',
                    sortFn: LABKEY.vis.discreteSortFn,
                    tickLabelMax: 25
                };
            }

            scales.y = {
                scaleType: 'continuous',
                trans: savedScales.y ? savedScales.y.trans : 'linear'
            };

        }

        for (var i = 0; i < fields.length; i++) {
            var type = fields[i].displayFieldJsonType ? fields[i].displayFieldJsonType : fields[i].type;

            if (type == 'int' || type == 'float') {
                if (measures.x && fields[i].name == measures.x.name) {
                    if (fields[i].extFormatFn) {
                        scales.x.tickFormat = eval(fields[i].extFormatFn);
                    } else if (defaultFormatFn) {
                        scales.x.tickFormat = defaultFormatFn;
                    }
                }

                if (measures.y && fields[i].name == measures.y.name) {
                    if (fields[i].extFormatFn) {
                        scales.y.tickFormat = eval(fields[i].extFormatFn);
                    } else if (defaultFormatFn) {
                        scales.y.tickFormat = defaultFormatFn;
                    }
                }
            } else if (measures.x && fields[i].name == measures.x.name && measures.x.name == subjectColumn && LABKEY.demoMode) {
                    scales.x.tickFormat = function(){return '******'};
            }
        }

        if (savedScales.x && (savedScales.x.min != null || savedScales.x.max != null)) {
            scales.x.domain = [savedScales.x.min, savedScales.x.max]
        }

        if (savedScales.y && (savedScales.y.min != null || savedScales.y.max != null)) {
            scales.y.domain = [savedScales.y.min, savedScales.y.max]
        }

        return scales;
    };

    /**
     * Generates the aesthetic map object needed by the visualization API to render the chart. See {@link LABKEY.vis.Plot}
     * and {@link LABKEY.vis.Layer}.
     * @param {String} chartType The chartType from getChartType.
     * @param {Object} measures The measures from getMeasures.
     * @param {String} schemaName The schemaName from the saved queryConfig.
     * @param {String} queryName The queryName from the saved queryConfig.
     * @returns {Object}
     */
    var generateAes = function(chartType, measures, schemaName, queryName) {
        var aes = {},
            xMeasureType = _getMeasureType(measures.x),
            yMeasureType = _getMeasureType(measures.y);

        if(chartType == "box_plot" && !measures.x) {
            aes.x = generateMeasurelessAcc(queryName);
        } else if (xMeasureType == "float" || xMeasureType == "int") {
            aes.x = generateContinuousAcc(measures.x.name);
        } else {
            aes.x = generateDiscreteAcc(measures.x.name, measures.x.label);
        }

        if (measures.y)
        {
            if (yMeasureType == "float" || yMeasureType == "int")
                aes.y = generateContinuousAcc(measures.y.name);
            else
                aes.y = generateDiscreteAcc(measures.y.name, measures.y.label);
        }

        if (chartType === "scatter_plot") {
            aes.hoverText = generatePointHover(measures);
        } else if (chartType === "box_plot") {
            if (measures.color) {
                aes.outlierColor = generateGroupingAcc(measures.color.name);
            }

            if (measures.shape) {
                aes.outlierShape = generateGroupingAcc(measures.shape.name);
            }

            aes.hoverText = generateBoxplotHover();
            aes.outlierHoverText = generatePointHover(measures);
        }

        // color/shape aes are not dependent on chart type. If we have a box plot with all points enabled, then we
        // create a second layer for points. So we'll need this no matter what.
        if (measures.color) {
            aes.color = generateGroupingAcc(measures.color.name);
        }

        if (measures.shape) {
            aes.shape = generateGroupingAcc(measures.shape.name);
        }

        if (measures.pointClickFn) {
            aes.pointClickFn = generatePointClickFn(
                    measures,
                    schemaName,
                    queryName,
                    measures.pointClickFn
            );
        }

        return aes;
    };

    /**
     * Generates a function that returns the text used for point hovers.
     * @param {Object} measures The measures object from the saved chart config.
     * @returns {Function}
     */
    var generatePointHover = function(measures){
        return function(row) {
            var hover;

            if(measures.x) {
                hover = measures.x.label + ': ';

                if(row[measures.x.name].displayValue){
                    hover = hover + row[measures.x.name].displayValue;
                } else {
                    hover = hover + row[measures.x.name].value;
                }
            }

            hover = hover + ', \n' + measures.y.label + ': ' + row[measures.y.name].value;

            if(measures.color){
                hover = hover +  ', \n' + measures.color.label + ': ';
                if(row[measures.color.name]){
                    if(row[measures.color.name].displayValue){
                        hover = hover + row[measures.color.name].displayValue;
                    } else {
                        hover = hover + row[measures.color.name].value;
                    }
                }
            }

            if(measures.shape && !(measures.color && measures.color.name == measures.shape.name)){
                hover = hover +  ', \n' + measures.shape.label + ': ';
                if(row[measures.shape.name]){
                    if(row[measures.shape.name].displayValue){
                        hover = hover + row[measures.shape.name].displayValue;
                    } else {
                        hover = hover + row[measures.shape.name].value;
                    }
                }
            }
            return hover;
        };
    };

    /**
     * Returns a function used to generate the hover text for box plots.
     * @returns {Function}
     */
    var generateBoxplotHover = function() {
        return function(xValue, stats) {
            return xValue + ':\nMin: ' + stats.min + '\nMax: ' + stats.max + '\nQ1: ' + stats.Q1 + '\nQ2: ' + stats.Q2 +
                    '\nQ3: ' + stats.Q3;
        };
    };

    /**
     * Generates an accessor function that returns a discrete value from a row of data for a given measure and label.
     * Used when an axis has a discrete measure (i.e. string).
     * @param {String} measureName The name of the measure.
     * @param {String} measureLabel The label of the measure.
     * @returns {Function}
     */
    var generateDiscreteAcc = function(measureName, measureLabel) {
        return function(row){
            var valueObj = row[measureName];
            var value = null;

            if(valueObj){
                value = valueObj.displayValue ? valueObj.displayValue : valueObj.value;
            } else {
                return undefined;
            }

            if(value === null){
                value = "Not in " + measureLabel;
            }

            return value;
        };
    };

    /**
     * Generates an accessor function that returns a value from a row of data for a given measure.
     * @param {String} measureName The name of the measure.
     * @returns {Function}
     */
    var generateContinuousAcc = function(measureName){
        return function(row){
            var value = null;

            if(row[measureName]){
                value = row[measureName].value;

                if(Math.abs(value) === Infinity){
                    value = null;
                }

                if(value === false || value === true){
                    value = value.toString();
                }

                return value;
            } else {
                return undefined;
            }
        }
    };

    /**
     * Generates an accesssor function for shape and color measures.
     * @param {String} measureName The name of the measure.
     * @returns {Function}
     */
    var generateGroupingAcc = function(measureName){
        return function(row) {
            var measureObj = row[measureName];
            var value = null;

            if(measureObj){
                value = measureObj.displayValue ? measureObj.displayValue : measureObj.value;
            }

            if(value === null || value === undefined){
                value = "n/a";
            }

            return value;
        };
    };

    /**
     * Generates an accessor for boxplots that do not have an x-axis measure. Generally the measureName passed in is the
     * queryName.
     * @param {String} measureName The name of the measure. In this case it is generally the query name.
     * @returns {Function}
     */
    var generateMeasurelessAcc = function(measureName) {
        // Used for boxplots that do not have an x-axis measure. Instead we just return the
        // queryName for every row.
        return function(row) {
            return measureName;
        }
    };

    /**
     * Generates the function to be executed when a user clicks a point.
     * @param {Object} measures The measures from the saved chart config.
     * @param {String} schemaName The schema name from the saved query config.
     * @param {String} queryName The query name from the saved query config.
     * @param {String} fnString The string value of the user-provided function to be executed when a point is clicked.
     * @returns {Function}
     */
    var generatePointClickFn = function(measures, schemaName, queryName, fnString){
        var measureInfo = {
            schemaName: schemaName,
            queryName: queryName
        };

        if (measures.y)
            measureInfo.yAxis = measures.y.name;
        if (measures.x)
            measureInfo.xAxis = measures.x.name;
        if (measures.shape)
            measureInfo.shapeName = measures.shape.name;
        if (measures.color)
            measureInfo.pointName = measures.color.name;

        // using new Function is quicker than eval(), even in IE.
        var pointClickFn = new Function('return ' + fnString)();
        return function(clickEvent, data){
            pointClickFn(data, measureInfo, clickEvent);
        };
    };

    /**
     * Generates the Point Geom used for scatter plots and box plots with all points visible.
     * @param {Object} chartOptions The saved chartOptions object from the chart config.
     * @returns {LABKEY.vis.Geom.Point}
     */
    var generatePointGeom = function(chartOptions){
        return new LABKEY.vis.Geom.Point({
            opacity: chartOptions.opacity,
            size: chartOptions.pointSize,
            color: '#' + chartOptions.pointFillColor,
            position: chartOptions.position
        });
    };

    /**
     * Generates the Boxplot Geom used for box plots.
     * @param {Object} chartOptions The saved chartOptions object from the chart config.
     * @returns {LABKEY.vis.Geom.Boxplot}
     */
    var generateBoxplotGeom = function(chartOptions){
        return new LABKEY.vis.Geom.Boxplot({
            lineWidth: chartOptions.lineWidth,
            outlierOpacity: chartOptions.opacity,
            outlierFill: '#' + chartOptions.pointFillColor,
            outlierSize: chartOptions.pointSize,
            color: '#' + chartOptions.lineColor,
            fill: chartOptions.boxFillColor == 'none' ? chartOptions.boxFillColor : '#' + chartOptions.boxFillColor,
            position: chartOptions.position,
            showOutliers: chartOptions.showOutliers
        });
    };

    /**
     * Generates the Barplot Geom used for bar charts.
     * @param {Object} chartOptions The saved chartOptions object from the chart config.
     * @returns {LABKEY.vis.Geom.BarPlot}
     */
    var generateBarGeom = function(chartOptions){
        return new LABKEY.vis.Geom.BarPlot({
            opacity: chartOptions.opacity,
            color: '#' + chartOptions.lineColor,
            fill: '#' + chartOptions.boxFillColor,
            lineWidth: chartOptions.lineWidth
        });
    };

    /**
     * Generates the Bin Geom used to bin a set of points.
     * @param {Object} chartOptions The saved chartOptions object from the chart config.
     * @returns {LABKEY.vis.Geom.Bin}
     */
    var generateBinGeom = function(chartOptions) {
        var colorRange = ["#e6e6e6", "#085D90"]; //light-gray and labkey blue is default
        if (chartOptions.binColorGroup == 'SingleColor') {
            var color = '#' + chartOptions.binSingleColor;
            colorRange = ["#FFFFFF", color];
        }
        else if (chartOptions.binColorGroup == 'Heat') {
            colorRange = ["#fff6bc", "#e23202"];
        }

        return new LABKEY.vis.Geom.Bin({
            shape: chartOptions.binShape,
            colorRange: colorRange,
            size: chartOptions.binShape == 'square' ? 10 : 5
        })
    };

    /**
     * Generates a Geom based on the chartType.
     * @param {String} chartType The chart type from getChartType.
     * @param {Object} chartOptions The chartOptions object from the saved chart config.
     * @returns {LABKEY.vis.Geom}
     */
    var generateGeom = function(chartType, chartOptions) {
        if (chartType == "box_plot")
            return generateBoxplotGeom(chartOptions);
        else if (chartType == "scatter_plot")
            return chartOptions.binned ? generateBinGeom(chartOptions) : generatePointGeom(chartOptions);
        else if (chartType == "bar_chart")
            return generateBarGeom(chartOptions);
    };

    /**
     *
     * @param {Array} data The response data from selectRows.
     * @param {String} dimensionName The grouping variable to get distinct members from.
     * @param {String} measureName The variable to calculate aggregate values over. Nullable.
     * @param {String} aggregate MIN/MAX/SUM/COUNT/etc. Defaults to COUNT.
     * @param {String} nullDisplayValue The display value to use for null dimension values. Defaults to 'null'.
     */
    var generateAggregateData = function(data, dimensionName, measureName, aggregate, nullDisplayValue)
    {
        var uniqueDimValues = {};
        for (var i = 0; i < data.length; i++)
        {
            var dimVal = null;
            if (typeof data[i][dimensionName] == 'object')
                dimVal = data[i][dimensionName].hasOwnProperty('displayValue') ? data[i][dimensionName].displayValue : data[i][dimensionName].value;

            var measureVal = null;
            if (measureName != undefined && measureName != null && typeof data[i][measureName] == 'object')
                measureVal = data[i][measureName].value;

            if (uniqueDimValues[dimVal] == undefined)
                uniqueDimValues[dimVal] = {count: 0, sum: 0};

            uniqueDimValues[dimVal].count++;
            if (!isNaN(measureVal))
                uniqueDimValues[dimVal].sum += measureVal;
        }

        var keys = Object.keys(uniqueDimValues), results = [];
        for (var i = 0; i < keys.length; i++)
        {
            var row = {
                label: keys[i] == null || keys[i] == 'null' ? nullDisplayValue || 'null' : keys[i]
            };

            // TODO add support for more aggregates
            if (aggregate == undefined || aggregate == null || aggregate == 'COUNT')
                row.value = uniqueDimValues[keys[i]].count;
            else if (aggregate == 'SUM')
                row.value = uniqueDimValues[keys[i]].sum;
            else
                throw 'Aggregate ' + aggregate + ' is not yet supported.';

            results.push(row);
        }
        return results;
    };

    /**
     * Generate the plot config for the given chart renderType and config options.
     * @param renderTo
     * @param chartConfig
     * @param labels
     * @param aes
     * @param scales
     * @param data
     * @returns {Object}
     */
    var generatePlotConfig = function(renderTo, chartConfig, labels, aes, scales, geom, data)
    {
        var renderType = chartConfig.renderType,
            layers = [], clipRect,
            plotConfig = {
                renderTo: renderTo,
                rendererType: 'd3',
                width: chartConfig.width,
                height: chartConfig.height
            };

        if (renderType == 'pie_chart')
            return _generatePieChartConfig(plotConfig, chartConfig, labels, data);

        clipRect = (scales.x && Ext4.isArray(scales.x.domain)) || (scales.y && Ext4.isArray(scales.y.domain));

        if (renderType == 'bar_chart')
        {
            aes = { x: 'label', y: 'value' };

            if (scales.y.domain) {
                scales.y = { domain: scales.y.domain };
            } else {
                var values = Ext4.Array.pluck(data, 'value'),
                        min = Math.min(0, Ext4.Array.min(values)),
                        max = Math.max(0, Ext4.Array.max(values));
                scales.y = { domain: [min, max] };
            }
        }
        else if (renderType == 'box_plot' && chartConfig.pointType == 'all')
        {
            layers.push(
                new LABKEY.vis.Layer({
                    data: data,
                    geom: LABKEY.vis.GenericChartHelper.generatePointGeom(chartConfig.geomOptions),
                    aes: {hoverText: LABKEY.vis.GenericChartHelper.generatePointHover(chartConfig.measures)}
                })
            );
        }

        layers.push(
            new LABKEY.vis.Layer({
                data: data,
                geom: geom
            })
        );

        plotConfig = Ext4.apply(plotConfig, {
            clipRect: clipRect,
            data: data,
            labels: labels,
            aes: aes,
            scales: scales,
            layers: layers
        });

        return plotConfig;
    };

    var _generatePieChartConfig = function(baseConfig, chartConfig, labels, data)
    {
        return Ext4.apply(baseConfig, {
            data: data,
            header: {
                title: { text: labels.main.value },
                subtitle: { text: labels.subtitle.value },
                titleSubtitlePadding: 1
            },
            footer: {
                text: labels.footer.value,
                location: 'bottom-center'
            },
            labels: {
                mainLabel: { fontSize: 14 },
                percentage: {
                    fontSize: 14,
                    color: chartConfig.geomOptions.piePercentagesColor != null ? '#' + chartConfig.geomOptions.piePercentagesColor : undefined
                },
                outer: { pieDistance: 20 },
                inner: {
                    format: chartConfig.geomOptions.showPiePercentages ? 'percentage' : 'none',
                    hideWhenLessThanPercentage: chartConfig.geomOptions.pieHideWhenLessThanPercentage
                }
            },
            size: {
                pieInnerRadius: chartConfig.geomOptions.pieInnerRadius + '%',
                pieOuterRadius: chartConfig.geomOptions.pieOuterRadius + '%'
            },
            misc: {
                gradient: {
                    enabled: chartConfig.geomOptions.gradientPercentage != 0,
                    percentage: chartConfig.geomOptions.gradientPercentage,
                    color: '#' + chartConfig.geomOptions.gradientColor
                },
                colors: {
                    segments: LABKEY.vis.Scale[chartConfig.geomOptions.colorPaletteScale]()
                }
            },
            effects: { highlightSegmentOnMouseover: false },
            tooltips: { enabled: true }
        });
    };

    /**
     * Check if the selectRows API response has data. Return an error string if no data exists.
     * @param response
     * @param includeFilterMsg true to include a message about removing filters
     * @returns {String}
     */
    var validateResponseHasData = function(response, includeFilterMsg)
    {
        if (!response || !response.rows || response.rows.length == 0)
        {
            return 'The response returned 0 rows of data. The query may be empty or the applied filters may be too strict.'
                + (includeFilterMsg ? 'Try removing or adjusting any filters if possible.' : '');
        }

        return null;
    };

    /**
     * Verifies that the axis measure is actually present and has data. Also checks to make sure that data can be used in a log
     * scale (if applicable). Returns an object with a success parameter (boolean) and a message parameter (string). If the
     * success parameter is false there is a critical error and the chart cannot be rendered. If success is true the chart
     * can be rendered. Message will contain an error or warning message if applicable. If message is not null and success
     * is true, there is a warning.
     * @param {String} chartType The chartType from getChartType.
     * @param {Object} chartConfig The saved chartConfig object.
     * @param {String} measureName The name of the axis measure property.
     * @param {Object} aes The aes object from generateAes.
     * @param {Object} scales The scales object from generateScales.
     * @param {Array} data The response data from selectRows.
     * @returns {Object}
     */
    var validateAxisMeasure = function(chartType, chartConfig, measureName, aes, scales, data){

        var dataIsNull = true, measureUndefined = true, invalidLogValues = false, hasZeroes = false, message = null;

        for (var i = 0; i < data.length; i ++)
        {
            var value = aes[measureName](data[i]);

            if (value !== undefined)
                measureUndefined = false;

            if (value !== null)
                dataIsNull = false;

            if (value && value < 0)
                invalidLogValues = true;

            if (value === 0 )
                hasZeroes = true;
        }

        if (measureUndefined)
        {
            message = 'The measure ' + chartConfig.measures[measureName].label + ' was not found. It may have been renamed or removed.';
            return {success: false, message: message};
        }

        if ((chartType == 'scatter_plot' || measureName == 'y') && dataIsNull)
        {
            message = 'All data values for ' + chartConfig.measures[measureName].label + ' are null. Please choose a different measure.';
            return {success: false, message: message};
        }

        if (scales[measureName] && scales[measureName].trans == "log")
        {
            if (invalidLogValues)
            {
                message = "Unable to use a log scale on the y-axis. All y-axis values must be >= 0. Reverting to linear scale on y-axis.";
                scales[measureName].trans = 'linear';
            }
            else if (hasZeroes)
            {
                message = "Some " + measureName + "-axis values are 0. Plotting all " + measureName + "-axis values as value+1.";
                var accFn = aes[measureName];
                aes[measureName] = function(row){return accFn(row) + 1};
            }
        }

        return {success: true, message: message};
    };

    /**
     * Deprecated - use validateAxisMeasure
     */
    var validateXAxis = function(chartType, chartConfig, aes, scales, data){
        return this.validateAxisMeasure(chartType, chartConfig, 'x', aes, scales, data);
    };
    /**
     * Deprecated - use validateAxisMeasure
     */
    var validateYAxis = function(chartType, chartConfig, aes, scales, data){
        return this.validateAxisMeasure(chartType, chartConfig, 'y', aes, scales, data);
    };

    var _getMeasureType = function(measure) {
        return measure ? (measure.normalizedType || measure.type) : null;
    };

    return {
        // NOTE: the @function below is needed or JSDoc will not include the documentation for loadVisDependencies. Don't
        // ask me why, I do not know.
        /**
         * @function
         */
        getRenderTypes: getRenderTypes,
        getMeasureType: _getMeasureType,
        getChartType: getChartType,
        getDefaultLabel: getDefaultLabel,
        generateLabels: generateLabels,
        generateScales: generateScales,
        generateAes: generateAes,
        generatePointHover: generatePointHover,
        generateBoxplotHover: generateBoxplotHover,
        generateDiscreteAcc: generateDiscreteAcc,
        generateContinuousAcc: generateContinuousAcc,
        generateGroupingAcc: generateGroupingAcc,
        generatePointClickFn: generatePointClickFn,
        generateGeom: generateGeom,
        generateBoxplotGeom: generateBoxplotGeom,
        generatePointGeom: generatePointGeom,
        generateAggregateData: generateAggregateData,
        generatePlotConfig: generatePlotConfig,
        validateResponseHasData: validateResponseHasData,
        validateAxisMeasure: validateAxisMeasure,
        validateXAxis: validateXAxis,
        validateYAxis: validateYAxis,
        /**
         * Loads all of the required dependencies for a Generic Chart.
         * @param {Function} callback The callback to be executed when all of the visualization dependencies have been loaded.
         * @param {Object} scope The scope to be used when executing the callback.
         */
        loadVisDependencies: LABKEY.requiresVisualization
    };
};