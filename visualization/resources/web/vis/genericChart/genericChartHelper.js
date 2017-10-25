/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
                    {name: 'x', label: 'X Axis Categories', required: true, nonNumericOnly: true},
                    {name: 'xSub', label: 'Split Categories By', required: false, nonNumericOnly: true},
                    {name: 'y', label: 'Y Axis', numericOnly: true}
                ],
                layoutOptions: {line: true, opacity: true, axisBased: true}
            },
            {
                name: 'box_plot',
                title: 'Box',
                imgUrl: LABKEY.contextPath + '/visualization/images/boxplot.png',
                fields: [
                    {name: 'x', label: 'X Axis Categories'},
                    {name: 'y', label: 'Y Axis', required: true, numericOnly: true},
                    {name: 'color', label: 'Color', nonNumericOnly: true},
                    {name: 'shape', label: 'Shape', nonNumericOnly: true}
                ],
                layoutOptions: {point: true, box: true, line: true, opacity: true, axisBased: true}
            },
            {
                name: 'line_plot',
                title: 'Line',
                imgUrl: LABKEY.contextPath + '/visualization/images/timechart.png',
                fields: [
                    {name: 'x', label: 'X Axis', required: true, numericOrDateOnly: true},
                    {name: 'y', label: 'Y Axis', required: true, numericOnly: true},
                    {name: 'series', label: 'Series', nonNumericOnly: true}
                ],
                layoutOptions: {opacity: true, axisBased: true, series: true}
            },
            {
                name: 'pie_chart',
                title: 'Pie',
                imgUrl: LABKEY.contextPath + '/visualization/images/piechart.png',
                fields: [
                    {name: 'x', label: 'Categories', required: true, nonNumericOnly: true},
                    // Issue #29046  'Remove "measure" option from pie chart'
                    // {name: 'y', label: 'Measure', numericOnly: true}
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
                layoutOptions: {point: true, opacity: true, axisBased: true, binnable: true}
            },
            {
                name: 'time_chart',
                title: 'Time',
                hidden: _getStudyTimepointType() == null,
                imgUrl: LABKEY.contextPath + '/visualization/images/timechart.png',
                fields: [
                    {name: 'x', label: 'X Axis', required: true, altSelectionOnly: true, altFieldType: 'LABKEY.vis.TimeChartXAxisField'},
                    {name: 'y', label: 'Y Axis', required: true, numericOnly: true, allowMultiple: true}
                ],
                layoutOptions: {time: true, axisBased: true}
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
    var getChartType = function(renderType, xAxisType)
    {
        if (renderType === 'time_chart' || renderType === "bar_chart" || renderType === "pie_chart"
            || renderType === "box_plot" || renderType === "scatter_plot" || renderType === "line_plot")
        {
            return renderType;
        }

        if (!xAxisType)
        {
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
    var getSelectedMeasureLabel = function(renderType, measureName, properties)
    {
        var label = properties ? properties.label || properties.queryName : '';

        if (label != '' && measureName == 'y' && (renderType == 'bar_chart' || renderType == 'pie_chart')) {
            if (Ext4.isDefined(properties.aggregate)) {
                var aggLabel = Ext4.isObject(properties.aggregate) ? properties.aggregate.name
                        : Ext4.String.capitalize(properties.aggregate.toLowerCase());
                label = aggLabel + ' of ' + label;
            }
            else {
                label = 'Sum of ' + label;
            }
        }

        return label;
    };

    /**
     * Generate a plot title based on the selected measures array or object.
     * @param renderType
     * @param measures
     * @returns {string}
     */
    var getTitleFromMeasures = function(renderType, measures)
    {
        var queryLabels = [];

        if (Ext4.isObject(measures))
        {
            if (Ext4.isArray(measures.y))
            {
                Ext4.each(measures.y, function(m)
                {
                    var measureQueryLabel = m.queryLabel || m.queryName;
                    if (queryLabels.indexOf(measureQueryLabel) == -1)
                        queryLabels.push(measureQueryLabel);
                });
            }
            else
            {
                var m = measures.x || measures.y;
                queryLabels.push(m.queryLabel || m.queryName);
            }
        }

        return queryLabels.join(', ');
    };

    /**
     * Get the sorted set of column metadata for the given schema/query/view.
     * @param queryConfig
     * @param successCallback
     * @param callbackScope
     */
    var getQueryColumns = function(queryConfig, successCallback, callbackScope)
    {
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('visualization', 'getGenericReportColumns.api'),
            method: 'GET',
            params: {
                schemaName: queryConfig.schemaName,
                queryName: queryConfig.queryName,
                viewName: queryConfig.viewName,
                dataRegionName: queryConfig.dataRegionName,
                includeCohort: true,
                includeParticipantCategory : true
            },
            success : function(response){
                var columnList = LABKEY.Utils.decode(response.responseText);
                _queryColumnMetadata(queryConfig, columnList, successCallback, callbackScope)
            },
            scope   : this
        });
    };

    var _queryColumnMetadata = function(queryConfig, columnList, successCallback, callbackScope)
    {
        LABKEY.Query.selectRows({
            maxRows: 0, // use maxRows 0 so that we just get the query metadata
            schemaName: queryConfig.schemaName,
            queryName: queryConfig.queryName,
            viewName: queryConfig.viewName,
            parameters: queryConfig.parameters,
            requiredVersion: 9.1,
            columns: columnList.columns.all,
            method: 'POST', // Issue 31744: use POST as the columns list can be very long and cause a 400 error
            success: function(response){
                var columnMetadata = _updateAndSortQueryFields(queryConfig, columnList, response.metaData.fields);
                successCallback.call(callbackScope, columnMetadata);
            },
            failure : function(response) {
                // this likely means that the query no longer exists
                successCallback.call(callbackScope, columnList, []);
            },
            scope   : this
        });
    };

    var _updateAndSortQueryFields = function(queryConfig, columnList, columnMetadata)
    {
        var queryFields = [],
            queryFieldKeys = [],
            columnTypes = Ext4.isDefined(columnList.columns) ? columnList.columns : {};

        Ext4.each(columnMetadata, function(column)
        {
            var f = Ext4.clone(column);
            f.schemaName = queryConfig.schemaName;
            f.queryName = queryConfig.queryName;
            f.isCohortColumn = false;
            f.isSubjectGroupColumn = false;

            // issue 23224: distinguish cohort and subject group fields in the list of query columns
            if (columnTypes['cohort'] && columnTypes['cohort'].indexOf(f.fieldKey) > -1)
            {
                f.shortCaption = 'Study: ' + f.shortCaption;
                f.isCohortColumn = true;
            }
            else if (columnTypes['subjectGroup'] && columnTypes['subjectGroup'].indexOf(f.fieldKey) > -1)
            {
                f.shortCaption = columnList.subject.nounSingular + ' Group: ' + f.shortCaption;
                f.isSubjectGroupColumn = true;
            }

            // Issue 31672: keep track of the distinct query field keys so we don't get duplicates
            if (f.fieldKey.toLowerCase() != 'lsid' && queryFieldKeys.indexOf(f.fieldKey) == -1) {
                queryFields.push(f);
                queryFieldKeys.push(f.fieldKey);
            }
        }, this);

        // Sorts fields by their shortCaption, but put subject groups/categories/cohort at the end.
        queryFields.sort(function(a, b)
        {
            if (a.isSubjectGroupColumn != b.isSubjectGroupColumn)
                return a.isSubjectGroupColumn ? 1 : -1;
            else if (a.isCohortColumn != b.isCohortColumn)
                return a.isCohortColumn ? 1 : -1;
            else if (a.shortCaption != b.shortCaption)
                return a.shortCaption < b.shortCaption ? -1 : 1;

            return 0;
        });

        return queryFields;
    };

    /**
     * Determine a reasonable width for the chart based on the chart type and selected measures / data.
     * @param chartType
     * @param measures
     * @param measureStore
     * @param defaultWidth
     * @returns {int}
     */
    var getChartTypeBasedWidth = function(chartType, measures, measureStore, defaultWidth) {
        var width = defaultWidth;

        if (chartType == 'bar_chart' && Ext4.isObject(measures.x)) {
            // 15px per bar + 15px between bars + 300 for default margins
            var xBarCount = measureStore.members(measures.x.name).length;
            width = Math.max((xBarCount * 15 * 2) + 300, defaultWidth);

            if (Ext4.isObject(measures.xSub)) {
                // 15px per bar per group + 200px between groups + 600 for default margins
                var xSubCount = measureStore.members(measures.xSub.name).length;
                width = (xBarCount * xSubCount * 15) + (xSubCount * 200) + 600;
            }
        }
        else if (chartType == 'box_plot' && Ext4.isObject(measures.x)) {
            // 20px per box + 20px between boxes + 300 for default margins
            var xBoxCount = measureStore.members(measures.x.name).length;
            width = Math.max((xBoxCount * 20 * 2) + 300, defaultWidth);
        }

        return width;
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
     * @param {Object} measureStore The MeasureStore data using a selectRows API call.
     * @param {Function} defaultFormatFn used to format values for tick marks.
     * @returns {Object}
     */
    var generateScales = function(chartType, measures, savedScales, aes, measureStore, defaultFormatFn) {
        var scales = {};
        var data = Ext4.isArray(measureStore.rows) ? measureStore.rows : measureStore.records();
        var fields = Ext4.isObject(measureStore.metaData) ? measureStore.metaData.fields : measureStore.getResponseMetadata().fields;
        var subjectColumn = _getStudySubjectInfo().columnName;
        var valExponentialDigits = 6;

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
            var xMeasureType = getMeasureType(measures.x);

            // Force discrete x-axis scale for bar plots.
            var useContinuousScale = chartType != 'bar_chart' && isNumericType(xMeasureType);

            if (useContinuousScale)
            {
                scales.x = {
                    scaleType: 'continuous',
                    trans: savedScales.x ? savedScales.x.trans : 'linear'
                };
            }
            else
            {
                scales.x = {
                    scaleType: 'discrete',
                    sortFn: LABKEY.vis.discreteSortFn,
                    tickLabelMax: 25
                };

                //bar chart x-axis subcategories support
                if (Ext4.isDefined(measures.xSub)) {
                    scales.xSub = {
                        scaleType: 'discrete',
                        sortFn: LABKEY.vis.discreteSortFn,
                        tickLabelMax: 25
                    };
                }
            }

            scales.y = {
                scaleType: 'continuous',
                trans: savedScales.y ? savedScales.y.trans : 'linear'
            };
        }

        // if we have no data, show a default y-axis domain
        if (scales.x && data.length == 0 && scales.x.scaleType == 'continuous')
            scales.x.domain = [0,1];
        if (scales.y && data.length == 0)
            scales.y.domain = [0,1];

        for (var i = 0; i < fields.length; i++) {
            var type = fields[i].displayFieldJsonType ? fields[i].displayFieldJsonType : fields[i].type;
            var isMeasureXMatch = measures.x && (fields[i].fieldKey == measures.x.name || fields[i].fieldKey == measures.x.fieldKey);
            var isMeasureYMatch = measures.y && (fields[i].fieldKey == measures.y.name || fields[i].fieldKey == measures.y.fieldKey);
            var isConvertedYMeasure = isMeasureYMatch && measures.y.converted;

            if (isNumericType(type) || isConvertedYMeasure) {
                if (isMeasureXMatch) {
                    if (fields[i].extFormatFn) {
                        scales.x.tickFormat = eval(fields[i].extFormatFn);
                    }
                    else if (defaultFormatFn) {
                        scales.x.tickFormat = defaultFormatFn;
                    }
                }

                if (isMeasureYMatch) {
                    var tickFormatFn;

                    if (fields[i].extFormatFn) {
                        tickFormatFn = eval(fields[i].extFormatFn);
                    }
                    else if (defaultFormatFn) {
                        tickFormatFn = defaultFormatFn;
                    }

                    scales.y.tickFormat = function(value) {
                        if (Ext4.isNumber(value) && Math.abs(Math.round(value)).toString().length >= valExponentialDigits) {
                            return value.toExponential();
                        }
                        else if (Ext4.isFunction(tickFormatFn)) {
                            return tickFormatFn(value);
                        }
                        return value;
                    };
                }
            }
            else if (isMeasureXMatch && measures.x.name == subjectColumn && LABKEY.demoMode) {
                    scales.x.tickFormat = function(){return '******'};
            }
        }

        if (savedScales.x && (savedScales.x.min != null || savedScales.x.max != null)) {
            scales.x.domain = [savedScales.x.min, savedScales.x.max];
        }

        if (Ext4.isDefined(measures.xSub) && savedScales.xSub && (savedScales.xSub.min != null || savedScales.xSub.max != null)) {
            scales.xSub.domain = [savedScales.xSub.min, savedScales.xSub.max];
        }

        if (savedScales.y && (savedScales.y.min != null || savedScales.y.max != null)) {
            scales.y.domain = [savedScales.y.min, savedScales.y.max];
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
            xMeasureType = getMeasureType(measures.x);

        if (chartType == "box_plot" && !measures.x)
        {
            aes.x = generateMeasurelessAcc(queryName);
        }
        else if (isNumericType(xMeasureType) || (chartType == 'scatter_plot' && measures.x.measure))
        {
            var xMeasureName = measures.x.converted ? measures.x.convertedName : measures.x.name;
            aes.x = generateContinuousAcc(xMeasureName);
        }
        else
        {
            var xMeasureName = measures.x.converted ? measures.x.convertedName : measures.x.name;
            aes.x = generateDiscreteAcc(xMeasureName, measures.x.label);
        }

        if (measures.y)
        {
            var yMeasureName = measures.y.converted ? measures.y.convertedName : measures.y.name;
            aes.y = generateContinuousAcc(yMeasureName);
        }

        if (chartType === "scatter_plot" || chartType === "line_plot")
        {
            aes.hoverText = generatePointHover(measures);
        }

        if (chartType === "box_plot")
        {
            if (measures.color) {
                aes.outlierColor = generateGroupingAcc(measures.color.name);
            }

            if (measures.shape) {
                aes.outlierShape = generateGroupingAcc(measures.shape.name);
            }

            aes.hoverText = generateBoxplotHover();
            aes.outlierHoverText = generatePointHover(measures);
        }
        else if (chartType === 'bar_chart')
        {
            var xSubMeasureType = measures.xSub ? getMeasureType(measures.xSub) : null;
            if (xSubMeasureType)
            {
                if (isNumericType(xSubMeasureType))
                    aes.xSub = generateContinuousAcc(measures.xSub.name);
                else
                    aes.xSub = generateDiscreteAcc(measures.xSub.name, measures.xSub.label);
            }
        }

        // color/shape aes are not dependent on chart type. If we have a box plot with all points enabled, then we
        // create a second layer for points. So we'll need this no matter what.
        if (measures.color) {
            aes.color = generateGroupingAcc(measures.color.name);
        }

        if (measures.shape) {
            aes.shape = generateGroupingAcc(measures.shape.name);
        }

        // also add the color and shape for the line plot series.
        if (measures.series) {
            aes.color = generateGroupingAcc(measures.series.name);
            aes.shape = generateGroupingAcc(measures.series.name);
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
    var generatePointHover = function(measures)
    {
        return function(row) {
            var hover = '', sep = '', distinctNames = [];

            Ext4.Object.each(measures, function(key, measure) {
                if (Ext4.isObject(measure) && distinctNames.indexOf(measure.name) == -1) {
                    hover += sep + measure.label + ': ' + _getRowValue(row, measure.name);
                    sep = ', \n';

                    distinctNames.push(measure.name);
                }
            });

            return hover;
        };
    };

    /**
     * Backwards compatibility for function that has been moved to LABKEY.vis.getAggregateData.
     */
    var generateAggregateData = function(data, dimensionName, measureName, aggregate, nullDisplayValue) {
        return LABKEY.vis.getAggregateData(data, dimensionName, null, measureName, aggregate, nullDisplayValue, false);
    };

    var _getRowValue = function(row, propName, valueName)
    {
        if (row.hasOwnProperty(propName)) {
            // backwards compatibility for response row that is not a LABKEY.Query.Row
            if (!(row instanceof LABKEY.Query.Row)) {
                return row[propName].displayValue || row[propName].value;
            }

            var propValue = row.get(propName);
            if (valueName != undefined && propValue.hasOwnProperty(valueName)) {
                return propValue[valueName];
            }
            else if (propValue.hasOwnProperty('displayValue')) {
                return propValue['displayValue'];
            }
            return row.getValue(propName);
        }

        return undefined;
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
    var generateDiscreteAcc = function(measureName, measureLabel)
    {
        return function(row)
        {
            var value = _getRowValue(row, measureName);
            if (value === null)
                value = "Not in " + measureLabel;

            return value;
        };
    };

    /**
     * Generates an accessor function that returns a value from a row of data for a given measure.
     * @param {String} measureName The name of the measure.
     * @returns {Function}
     */
    var generateContinuousAcc = function(measureName)
    {
        return function(row)
        {
            var value = _getRowValue(row, measureName, 'value');

            if (value !== undefined)
            {
                if (Math.abs(value) === Infinity)
                    value = null;

                if (value === false || value === true)
                    value = value.toString();

                return value;
            }

            return undefined;
        }
    };

    /**
     * Generates an accesssor function for shape and color measures.
     * @param {String} measureName The name of the measure.
     * @returns {Function}
     */
    var generateGroupingAcc = function(measureName)
    {
        return function(row)
        {
            var value = null;
            if (Ext4.isArray(row) && row.length > 0) {
                value = _getRowValue(row[0], measureName);
            }
            else {
                value = _getRowValue(row, measureName);
            }

            if (value === null || value === undefined)
                value = "n/a";

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
        // Used for box plots that do not have an x-axis measure. Instead we just return the queryName for every row.
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
        Ext4.each(['color', 'shape', 'series'], function(name) {
            if (measures[name]) {
                measureInfo[name + 'Name'] = measures[name].name;
            }
        }, this);

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
        else if (chartType == "scatter_plot" || chartType == "line_plot")
            return chartOptions.binned ? generateBinGeom(chartOptions) : generatePointGeom(chartOptions);
        else if (chartType == "bar_chart")
            return generateBarGeom(chartOptions);
    };

    /**
     * Generate the plot config for the given chart renderType and config options.
     * @param renderTo
     * @param chartConfig
     * @param labels
     * @param aes
     * @param scales
     * @param geom
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

            if (Ext4.isDefined(chartConfig.measures.xSub))
            {
                aes.xSub = 'subLabel';
                aes.color = 'label';
            }

            if (!scales.y) {
                scales.y = {};
            }

            if (!scales.y.domain) {
                var values = Ext4.Array.pluck(data, 'value'),
                    min = Math.min(0, Ext4.Array.min(values)),
                    max = Math.max(0, Ext4.Array.max(values));

                scales.y.domain = [min, max];
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
        else if (renderType == 'line_plot') {
            var xName = chartConfig.measures.x.name,
                isDate = isDateType(getMeasureType(chartConfig.measures.x)),
                    pathAes = {};

            pathAes.sortFn = function(a, b) {
                // No need to handle the case for a or b or a.getValue() or b.getValue() null as they are
                // not currently included in this plot.
                if (isDate){
                    return new Date(a.getValue(xName)) - new Date(b.getValue(xName));
                }
                return a.getValue(xName) - b.getValue(xName);
            };

            if (chartConfig.measures.series) {
                pathAes.pathColor = generateGroupingAcc(chartConfig.measures.series.name);
                pathAes.group = generateGroupingAcc(chartConfig.measures.series.name);
            }

            layers.push(
                new LABKEY.vis.Layer({
                    geom: new LABKEY.vis.Geom.Path({
                        color: '#' + chartConfig.geomOptions.pointFillColor,
                        size: chartConfig.geomOptions.lineWidth?chartConfig.geomOptions.lineWidth:3,
                        opacity:chartConfig.geomOptions.opacity
                    }),
                    aes: pathAes
                })
            );
        }

        var margins = _getPlotMargins(renderType, aes, data, plotConfig);
        if (Ext4.isObject(margins)) {
            plotConfig.margins = margins;
        }

        if (chartConfig.measures.color)
        {
            scales.color = {
                colorType: chartConfig.geomOptions.colorPaletteScale,
                scaleType: 'discrete'
            }
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

    var _getPlotMargins = function(renderType, aes, data, plotConfig) {
        // issue 29690: for bar and box plots, set default bottom margin based on the number of labels and the max label length
        if (Ext4.isArray(data) && ((renderType == 'bar_chart' && !Ext4.isDefined(aes.xSub)) || renderType == 'box_plot')) {
            var maxLen = 0;
            Ext4.each(data, function(d) {
                var val = Ext4.isFunction(aes.x) ? aes.x(d) : d[aes.x];
                if (Ext4.isString(val)) {
                    maxLen = Math.max(maxLen, val.length);
                }
            });

            if (data.length * maxLen*5 > plotConfig.width - 150) {
                // min bottom margin: 50, max bottom margin: 275
                var bottomMargin = Math.min(Math.max(50, maxLen*5), 275);
                return {bottom: bottomMargin};
            }
        }

        return null;
    };

    var _generatePieChartConfig = function(baseConfig, chartConfig, labels, data)
    {
        var hasData = data.length > 0;

        return Ext4.apply(baseConfig, {
            data: hasData ? data : [{label: '', value: 1}],
            header: {
                title: { text: labels.main.value },
                subtitle: { text: labels.subtitle.value },
                titleSubtitlePadding: 1
            },
            footer: {
                text: hasData ? labels.footer.value : 'No data to display',
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
                    format: hasData && chartConfig.geomOptions.showPiePercentages ? 'percentage' : 'none',
                    hideWhenLessThanPercentage: chartConfig.geomOptions.pieHideWhenLessThanPercentage
                }
            },
            size: {
                pieInnerRadius: hasData ? chartConfig.geomOptions.pieInnerRadius + '%' : '100%',
                pieOuterRadius: hasData ? chartConfig.geomOptions.pieOuterRadius + '%' : '90%'
            },
            misc: {
                gradient: {
                    enabled: chartConfig.geomOptions.gradientPercentage != 0,
                    percentage: chartConfig.geomOptions.gradientPercentage,
                    color: '#' + chartConfig.geomOptions.gradientColor
                },
                colors: {
                    segments: hasData ? LABKEY.vis.Scale[chartConfig.geomOptions.colorPaletteScale]() : ['#333333']
                }
            },
            effects: { highlightSegmentOnMouseover: false },
            tooltips: { enabled: true }
        });
    };

    /**
     * Check if the MeasureStore selectRows API response has data. Return an error string if no data exists.
     * @param measureStore
     * @param includeFilterMsg true to include a message about removing filters
     * @returns {String}
     */
    var validateResponseHasData = function(measureStore, includeFilterMsg)
    {
        var dataArray = Ext4.isDefined(measureStore) ? measureStore.rows || measureStore.records() : [];
        if (dataArray.length == 0)
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
     * @param {Boolean} dataConversionHappened Whether we converted any values in the measure data
     * @returns {Object}
     */
    var validateAxisMeasure = function(chartType, chartConfig, measureName, aes, scales, data, dataConversionHappened) {
        var dataIsNull = true, measureUndefined = true, invalidLogValues = false, hasZeroes = false, message = null;

        // no need to check measures if we have no data
        if (data.length == 0) {
            return {success: true, message: message};
        }

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

        if ((chartType == 'scatter_plot' || chartType == 'line_plot' || measureName == 'y') && dataIsNull && !dataConversionHappened)
        {
            message = 'All data values for ' + chartConfig.measures[measureName].label + ' are null. Please choose a different measure.';
            return {success: false, message: message};
        }

        if (scales[measureName] && scales[measureName].trans == "log")
        {
            if (invalidLogValues)
            {
                message = "Unable to use a log scale on the " + measureName + "-axis. All " + measureName
                        + "-axis values must be >= 0. Reverting to linear scale on " + measureName + "-axis.";
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

    var getMeasureType = function(measure) {
        return measure ? (measure.normalizedType || measure.type) : null;
    };

    var isNumericType = function(type)
    {
        var t = Ext4.isString(type) ? type.toLowerCase() : null;
        return t == 'int' || t == 'integer' || t == 'float' || t == 'double';
    };

    var isDateType = function(type)
    {
        var t = Ext4.isString(type) ? type.toLowerCase() : null;
        return t == 'date';
    };

    var _getStudySubjectInfo = function()
    {
        var studyCtx = LABKEY.getModuleContext("study") || {};
        return Ext4.isObject(studyCtx.subject) ? studyCtx.subject : {
            tableName: 'Participant',
            columnName: 'ParticipantId',
            nounPlural: 'Participants',
            nounSingular: 'Participant'
        };
    };

    var _getStudyTimepointType = function()
    {
        var studyCtx = LABKEY.getModuleContext("study") || {};
        return Ext4.isDefined(studyCtx.timepointType) ? studyCtx.timepointType : null;
    };

    var _getMeasureRestrictions = function (chartType, measure)
    {
        var measureRestrictions = {};
        Ext4.each(getRenderTypes(), function (renderType)
        {
            if (renderType.name === chartType)
            {
                Ext4.each(renderType.fields, function (field)
                {
                    if (field.name === measure)
                    {
                        measureRestrictions.numericOnly = field.numericOnly;
                        measureRestrictions.nonNumericOnly = field.nonNumericOnly;
                        return false;
                    }
                });
                return false;
            }
        });

        return measureRestrictions;
    };

    /**
     * Converts data values passed in to the appropriate type based on measure/dimension information.
     * @param chartConfig Chart configuration object
     * @param aes Aesthetic mapping functions for each measure/axis
     * @param renderType The type of plot or chart (e.g. scatter_plot, bar_chart)
     * @param data The response data from SelectRows
     * @returns {{processed: {}, warningMessage: *}}
     */
    var doValueConversion = function(chartConfig, aes, renderType, data)
    {
        var measuresForProcessing = {}, measureRestrictions = {}, configMeasure;
        for (var measureName in chartConfig.measures) {
            if (chartConfig.measures.hasOwnProperty(measureName) && Ext4.isObject(chartConfig.measures[measureName])) {
                configMeasure = chartConfig.measures[measureName];
                Ext4.apply(measureRestrictions, _getMeasureRestrictions(renderType, measureName));

                var isGroupingMeasure = measureName === 'color' || measureName === 'shape' || measureName === 'series';
                var isXAxis = measureName === 'x' || measureName === 'xSub';
                var isScatterOrLine = renderType === 'scatter_plot' || renderType === 'line_plot';
                var isBarYCount = renderType === 'bar_chart' && configMeasure.aggregate && (configMeasure.aggregate === 'COUNT' || configMeasure.aggregate.value === 'COUNT');

                if (configMeasure.measure && !isGroupingMeasure && !isBarYCount
                        && ((!isXAxis && measureRestrictions.numericOnly ) || isScatterOrLine) && !isNumericType(configMeasure.type)) {
                    measuresForProcessing[measureName] = {};
                    measuresForProcessing[measureName].name = configMeasure.name;
                    measuresForProcessing[measureName].convertedName = configMeasure.name + "_converted";
                    measuresForProcessing[measureName].label = configMeasure.label;
                    configMeasure.normalizedType = 'float';
                    configMeasure.type = 'float';
                }
            }
        }

        var response = {processed: {}};
        if (!Ext4.Object.isEmpty(measuresForProcessing)) {
            response = _processMeasureData(data, aes, measuresForProcessing);
        }

        //generate error message for dropped values
        var warningMessage = '';
        for (var measure in response.droppedValues) {
            if (response.droppedValues.hasOwnProperty(measure) && response.droppedValues[measure].numDropped) {
                warningMessage += " The "
                        + measure + "-axis measure '"
                        + response.droppedValues[measure].label + "' had "
                        + response.droppedValues[measure].numDropped +
                        " value(s) that could not be converted to a number and are not included in the plot.";
            }
        }

        return {processed: response.processed, warningMessage: warningMessage};
    };

    /**
     * Does the explicit type conversion for each measure deemed suitable to convert. Currently we only
     * attempt to convert strings to numbers for measures.
     * @param rows Data from SelectRows
     * @param aes Aesthetic mapping function for the measure/dimensions
     * @param measuresForProcessing The measures to be converted, if any
     * @returns {{droppedValues: {}, processed: {}}}
     */
    var _processMeasureData = function(rows, aes, measuresForProcessing) {
        var droppedValues = {}, processedMeasures = {}, dataIsNull;
        rows.forEach(function(row) {
            //convert measures if applicable
            if (!Ext4.Object.isEmpty(measuresForProcessing)) {
                for (var measure in measuresForProcessing) {
                    if (measuresForProcessing.hasOwnProperty(measure)) {
                        dataIsNull = true;
                        if (!droppedValues[measure]) {
                            droppedValues[measure] = {};
                            droppedValues[measure].label = measuresForProcessing[measure].label;
                            droppedValues[measure].numDropped = 0;
                        }

                        if (aes.hasOwnProperty(measure)) {
                            var value = aes[measure](row);
                            if (value !== null) {
                                dataIsNull = false;
                            }
                            row[measuresForProcessing[measure].convertedName] = {value: null};
                            if (typeof value !== 'number' && value !== null) {

                                //only try to convert strings to numbers
                                if (typeof value === 'string') {
                                    value = value.trim();
                                }
                                else {
                                    //dates, objects, booleans etc. to be assigned value: NULL
                                    value = '';
                                }

                                var n = Number(value);
                                // empty strings convert to 0, which we must explicitly deny
                                if (value === '' || isNaN(n)) {
                                    droppedValues[measure].numDropped++;
                                }
                                else {
                                    row[measuresForProcessing[measure].convertedName].value = n;
                                }
                            }
                        }

                        if (!processedMeasures[measure]) {
                            processedMeasures[measure] = {
                                converted: false,
                                convertedName: measuresForProcessing[measure].convertedName,
                                type: 'float',
                                normalizedType: 'float'
                            }
                        }

                        processedMeasures[measure].converted = processedMeasures[measure].converted || !dataIsNull;
                    }
                }
            }
        });

        return {droppedValues: droppedValues, processed: processedMeasures};
    };

    /**
     * removes all traces of String -> Numeric Conversion from the given chart config
     * @param chartConfig
     * @returns {updated ChartConfig}
     */
    var removeNumericConversionConfig = function(chartConfig) {
        if (chartConfig && chartConfig.measures) {
            for (var measureName in chartConfig.measures) {
                if (chartConfig.measures.hasOwnProperty(measureName)) {
                    var measure = chartConfig.measures[measureName];
                    if (measure && measure.converted && measure.convertedName) {
                        measure.converted = null;
                        measure.convertedName = null;
                        if (LABKEY.vis.GenericChartHelper.isNumericType(measure.type)) {
                            measure.type = 'string';
                            measure.normalizedType = 'string';
                        }
                    }
                }
            }
        }

        return chartConfig;
    };

    return {
        // NOTE: the @function below is needed or JSDoc will not include the documentation for loadVisDependencies. Don't
        // ask me why, I do not know.
        /**
         * @function
         */
        getRenderTypes: getRenderTypes,
        getChartType: getChartType,
        getSelectedMeasureLabel: getSelectedMeasureLabel,
        getTitleFromMeasures: getTitleFromMeasures,
        getMeasureType: getMeasureType,
        getQueryColumns : getQueryColumns,
        getChartTypeBasedWidth : getChartTypeBasedWidth,
        isNumericType: isNumericType,
        generateLabels: generateLabels,
        generateScales: generateScales,
        generateAes: generateAes,
        doValueConversion: doValueConversion,
        removeNumericConversionConfig: removeNumericConversionConfig,
        generateAggregateData: generateAggregateData,
        generatePointHover: generatePointHover,
        generateBoxplotHover: generateBoxplotHover,
        generateDiscreteAcc: generateDiscreteAcc,
        generateContinuousAcc: generateContinuousAcc,
        generateGroupingAcc: generateGroupingAcc,
        generatePointClickFn: generatePointClickFn,
        generateGeom: generateGeom,
        generateBoxplotGeom: generateBoxplotGeom,
        generatePointGeom: generatePointGeom,
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