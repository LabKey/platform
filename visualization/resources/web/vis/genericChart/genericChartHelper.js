/*
 * Copyright (c) 2013-2015 LabKey Corporation
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
                active: false,
                imgUrl: LABKEY.contextPath + '/visualization/images/barchart.png',
                fields: [
                    {name: 'x', label: 'X Categories', required: true, nonNumericOnly: true},
                    {name: 'y', label: 'Y Axis'}
                ],
                layoutOptions: {line: true}
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
                layoutOptions: {point: true, box: true, line: true}
            },
            {
                name: 'pie_chart',
                title: 'Pie',
                active: false,
                imgUrl: LABKEY.contextPath + '/visualization/images/piechart.png',
                fields: [
                    {name: 'x', label: 'Categories', required: true, nonNumericOnly: true},
                    {name: 'y', label: 'Measure'}
                ],
                layoutOptions: {}
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
                layoutOptions: {point: true, box: false, line: false}
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

        // Issue 23015: sort categorical x-axis alphabetically with special case for "Not in X"
        var descreteSortFn = function(a,b) {
            if (a && a.indexOf("Not in ") == 0) {
                return 1;
            }
            else if (b && b.indexOf("Not in ") == 0) {
                return -1;
            }
            else if (a != b) {
                return a < b ? -1 : 1;
            }
            return 0;
        };

        if (LABKEY.moduleContext.study && LABKEY.moduleContext.study.subject)
            subjectColumn = LABKEY.moduleContext.study.subject.columnName;

        if (chartType === "box_plot")
        {
            scales.x = {
                scaleType: 'discrete', // Force discrete x-axis scale for box plots.
                sortFn: descreteSortFn,
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
        else {
            if (measures.x.normalizedType == "float" || measures.x.normalizedType == "int")
            {
                scales.x = {
                    scaleType: 'continuous',
                    trans: savedScales.x ? savedScales.x.trans : 'linear'
                };
            } else
            {
                scales.x = {
                    scaleType: 'discrete',
                    sortFn: descreteSortFn,
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
        var aes = {};

        if(chartType == "box_plot" && !measures.x) {
            aes.x = generateMeasurelessAcc(queryName);
        } else if (measures.x.normalizedType == "float" || measures.x.normalizedType == "int") {
            aes.x = generateContinuousAcc(measures.x.name);
        } else {
            aes.x = generateDiscreteAcc(measures.x.name, measures.x.label);
        }

        if (measures.y)
        {
            if (measures.y.normalizedType == "float" || measures.y.normalizedType == "int")
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
            color: '#' + chartOptions.lineColor,
            fill: '#' + chartOptions.boxFillColor,
            lineWidth: chartOptions.lineWidth
        });
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
            return generatePointGeom(chartOptions);
        else if (chartType == "bar_chart")
            return generateBarGeom(chartOptions);
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
     * @param {Array} data The data from selectRows.
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
            message = 'The measure ' + Ext4.util.Format.htmlEncode(chartConfig.measures[measureName].label) + ' was not found. It may have been renamed or removed.';
            return {success: false, message: message};
        }

        if ((chartType == 'scatter_plot' || measureName == 'y') && dataIsNull)
        {
            message = 'All data values for ' + Ext4.util.Format.htmlEncode(chartConfig.measures[measureName].label) + ' are null. Please choose a different measure';
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
                message = "Some " + measureName + "-axis values are 0. Plotting all " + measureName + "-axis values as value+1";
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

    return {
        // NOTE: the @function below is needed or JSDoc will not include the documentation for loadVisDependencies. Don't
        // ask me why, I do not know.
        /**
         * @function
         */
        getRenderTypes: getRenderTypes,
        getChartType: getChartType,
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