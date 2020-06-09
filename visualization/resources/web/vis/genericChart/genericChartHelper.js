/*
 * Copyright (c) 2013-2019 LabKey Corporation
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

    var DEFAULT_TICK_LABEL_MAX = 25;
    var $ = jQuery;

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
                    {name: 'y', label: 'Y Axis', required: true, numericOnly: true, allowMultiple: true},
                    {name: 'series', label: 'Series', nonNumericOnly: true}
                ],
                layoutOptions: {opacity: true, axisBased: true, series: true, chartLayout: true}
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
                    {name: 'y', label: 'Y Axis', required: true, numericOnly: true, allowMultiple: true},
                    {name: 'color', label: 'Color', nonNumericOnly: true},
                    {name: 'shape', label: 'Shape', nonNumericOnly: true}
                ],
                layoutOptions: {point: true, opacity: true, axisBased: true, binnable: true, chartLayout: true}
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
                layoutOptions: {time: true, axisBased: true, chartLayout: true}
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
     * @param properties - properties for the selected column, note that this can be an array of properties
     */
    var getSelectedMeasureLabel = function(renderType, measureName, properties)
    {
        var label = getDefaultMeasuresLabel(properties);

        if (label !== '' && measureName === 'y' && (renderType === 'bar_chart' || renderType === 'pie_chart')) {
            var aggregateProps = LABKEY.Utils.isArray(properties) && properties.length === 1
                    ? properties[0].aggregate : properties.aggregate;

            if (LABKEY.Utils.isDefined(aggregateProps)) {
                var aggLabel = LABKEY.Utils.isObject(aggregateProps) ? aggregateProps.name : LABKEY.Utils.capitalize(aggregateProps.toLowerCase());
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

        if (LABKEY.Utils.isObject(measures))
        {
            if (LABKEY.Utils.isArray(measures.y))
            {
                $.each(measures.y, function(idx, m)
                {
                    var measureQueryLabel = m.queryLabel || m.queryName;
                    if (queryLabels.indexOf(measureQueryLabel) === -1)
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
            columnTypes = LABKEY.Utils.isDefined(columnList.columns) ? columnList.columns : {};

        $.each(columnMetadata, function(idx, column)
        {
            var f = $.extend(true, {}, column);
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

        if (chartType == 'bar_chart' && LABKEY.Utils.isObject(measures.x)) {
            // 15px per bar + 15px between bars + 300 for default margins
            var xBarCount = measureStore.members(measures.x.name).length;
            width = Math.max((xBarCount * 15 * 2) + 300, defaultWidth);

            if (LABKEY.Utils.isObject(measures.xSub)) {
                // 15px per bar per group + 200px between groups + 300 for default margins
                var xSubCount = measureStore.members(measures.xSub.name).length;
                width = (xBarCount * xSubCount * 15) + (xSubCount * 200) + 300;
            }
        }
        else if (chartType == 'box_plot' && LABKEY.Utils.isObject(measures.x)) {
            // 20px per box + 20px between boxes + 300 for default margins
            var xBoxCount = measureStore.members(measures.x.name).length;
            width = Math.max((xBoxCount * 20 * 2) + 300, defaultWidth);
        }

        return width;
    };

    /**
     * Return the distinct set of y-axis sides for the given measures object.
     * @param measures
     */
    var getDistinctYAxisSides = function(measures)
    {
        var distinctSides = [];
        $.each(ensureMeasuresAsArray(measures.y), function (idx, measure) {
            if (LABKEY.Utils.isObject(measure)) {
                var side = measure.yAxis || 'left';
                if (distinctSides.indexOf(side) === -1) {
                    distinctSides.push(side);
                }
            }
        }, this);
        return distinctSides;
    };

    /**
     * Generate a default label for an array of measures by concatenating each meaures label together.
     * @param measures
     * @returns string concatenation of all measure labels
     */
    var getDefaultMeasuresLabel = function(measures)
    {
        if (LABKEY.Utils.isDefined(measures)) {
            if (!LABKEY.Utils.isArray(measures)) {
                return measures.label || measures.queryName || '';
            }

            var label = '', sep = '';
            $.each(measures, function(idx, m) {
                label += sep + (m.label || m.queryName);
                sep = ', ';
            });
            return label;
        }

        return '';
    };

    /**
     * Given the saved labels object we convert it to include all label types (main, x, and y). Each label type defaults
     * to empty string ('').
     * @param {Object} labels The saved labels object.
     * @returns {Object}
     */
    var generateLabels = function(labels) {
        return {
            main: { value: labels.main || '' },
            subtitle: { value: labels.subtitle || '' },
            footer: { value: labels.footer || '' },
            x: { value: labels.x || '' },
            y: { value: labels.y || '' },
            yRight: { value: labels.yRight || '' }
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
        var data = LABKEY.Utils.isArray(measureStore.rows) ? measureStore.rows : measureStore.records();
        var fields = LABKEY.Utils.isObject(measureStore.metaData) ? measureStore.metaData.fields : measureStore.getResponseMetadata().fields;
        var subjectColumn = _getStudySubjectInfo().columnName;
        var valExponentialDigits = 6;

        if (chartType === "box_plot")
        {
            // Issue 38105: For box plot of study visit labels, don't sort alphabetically
            var sortFn = measures.x.fieldKey === 'ParticipantVisit/Visit' ? undefined : LABKEY.vis.discreteSortFn;

            scales.x = {
                scaleType: 'discrete', // Force discrete x-axis scale for box plots.
                sortFn: sortFn,
                tickLabelMax: DEFAULT_TICK_LABEL_MAX
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
                    tickLabelMax: DEFAULT_TICK_LABEL_MAX
                };

                //bar chart x-axis subcategories support
                if (LABKEY.Utils.isDefined(measures.xSub)) {
                    scales.xSub = {
                        scaleType: 'discrete',
                        sortFn: LABKEY.vis.discreteSortFn,
                        tickLabelMax: DEFAULT_TICK_LABEL_MAX
                    };
                }
            }

            // add both y (i.e. yLeft) and yRight, in case multiple y-axis measures are being plotted
            scales.y = {
                scaleType: 'continuous',
                trans: savedScales.y ? savedScales.y.trans : 'linear'
            };
            scales.yRight = {
                scaleType: 'continuous',
                trans: savedScales.yRight ? savedScales.yRight.trans : 'linear'
            };
        }

        // if we have no data, show a default y-axis domain
        if (scales.x && data.length == 0 && scales.x.scaleType == 'continuous')
            scales.x.domain = [0,1];
        if (scales.y && data.length == 0)
            scales.y.domain = [0,1];

        // apply the field formatFn to the tick marks on the scales object
        for (var i = 0; i < fields.length; i++) {
            var type = fields[i].displayFieldJsonType ? fields[i].displayFieldJsonType : fields[i].type;

            var isMeasureXMatch = measures.x && _isFieldKeyMatch(measures.x, fields[i].fieldKey);
            if (isMeasureXMatch && measures.x.name === subjectColumn && LABKEY.demoMode) {
                scales.x.tickFormat = function(){return '******'};
            }
            else if (isMeasureXMatch && isNumericType(type)) {
                scales.x.tickFormat = _getNumberFormatFn(fields[i], defaultFormatFn);
            }

            var yMeasures = ensureMeasuresAsArray(measures.y);
            $.each(yMeasures, function(idx, yMeasure) {
                var isMeasureYMatch = yMeasure && _isFieldKeyMatch(yMeasure, fields[i].fieldKey);
                var isConvertedYMeasure = isMeasureYMatch && yMeasure.converted;
                if (isMeasureYMatch && (isNumericType(type) || isConvertedYMeasure)) {
                    var tickFormatFn = _getNumberFormatFn(fields[i], defaultFormatFn);

                    var ySide = yMeasure.yAxis === 'right' ? 'yRight' : 'y';
                    scales[ySide].tickFormat = function(value) {
                        if (LABKEY.Utils.isNumber(value) && Math.abs(Math.round(value)).toString().length >= valExponentialDigits) {
                            return value.toExponential();
                        }
                        else if (LABKEY.Utils.isFunction(tickFormatFn)) {
                            return tickFormatFn(value);
                        }
                        return value;
                    };
                }
            }, this);
        }

        _applySavedScaleDomain(scales, savedScales, 'x');
        if (LABKEY.Utils.isDefined(measures.xSub)) {
            _applySavedScaleDomain(scales, savedScales, 'xSub');
        }
        if (LABKEY.Utils.isDefined(measures.y)) {
            _applySavedScaleDomain(scales, savedScales, 'y');
            _applySavedScaleDomain(scales, savedScales, 'yRight');
        }

        return scales;
    };

    // Issue 36227: if Ext4 is not available, try to generate our own number format function based on the "format" field metadata
    var _getNumberFormatFn = function(field, defaultFormatFn) {
        if (field.extFormatFn) {
            if (window.Ext4) {
                return eval(field.extFormatFn);
            }
            else if (field.format && LABKEY.Utils.isString(field.format) && field.format.indexOf('.') > -1) {
                var precision = field.format.length - field.format.indexOf('.') - 1;
                return function(v) {
                    return LABKEY.Utils.isNumber(v) ? v.toFixed(precision) : v;
                }
            }
        }

        return defaultFormatFn;
    };

    var _isFieldKeyMatch = function(measure, fieldKey) {
        if (LABKEY.Utils.isFunction(fieldKey.getName)) {
            return fieldKey.getName() === measure.name || fieldKey.getName() === measure.fieldKey;
        }

        return fieldKey === measure.name || fieldKey === measure.fieldKey;
    };

    var ensureMeasuresAsArray = function(measures) {
        if (LABKEY.Utils.isDefined(measures)) {
            return LABKEY.Utils.isArray(measures) ? $.extend(true, [], measures) : [$.extend(true, {}, measures)];
        }
        return [];
    };

    var _applySavedScaleDomain = function(scales, savedScales, scaleName) {
        if (savedScales[scaleName] && (savedScales[scaleName].min != null || savedScales[scaleName].max != null)) {
            scales[scaleName].domain = [savedScales[scaleName].min, savedScales[scaleName].max];
        }
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
        var aes = {}, xMeasureType = getMeasureType(measures.x);

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

        // charts that have multiple y-measures selected will need to put the aes.y function on their specific layer
        if (LABKEY.Utils.isDefined(measures.y) && !LABKEY.Utils.isArray(measures.y))
        {
            var sideAesName = (measures.y.yAxis || 'left') === 'left' ? 'y' : 'yRight';
            var yMeasureName = measures.y.converted ? measures.y.convertedName : measures.y.name;
            aes[sideAesName] = generateContinuousAcc(yMeasureName);
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

    var getYMeasureAes = function(measure) {
        var yMeasureName = measure.converted ? measure.convertedName : measure.name;
        return generateContinuousAcc(yMeasureName);
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

            $.each(measures, function(key, measureObj) {
                var measureArr = ensureMeasuresAsArray(measureObj);
                $.each(measureArr, function(idx, measure) {
                    if (LABKEY.Utils.isObject(measure) && distinctNames.indexOf(measure.name) == -1) {
                        hover += sep + measure.label + ': ' + _getRowValue(row, measure.name);
                        sep = ', \n';

                        distinctNames.push(measure.name);
                    }
                }, this);
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
            if (LABKEY.Utils.isArray(row) && row.length > 0) {
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

        _addPointClickMeasureInfo(measureInfo, measures, 'x', 'xAxis');
        _addPointClickMeasureInfo(measureInfo, measures, 'y', 'yAxis');
        $.each(['color', 'shape', 'series'], function(idx, name) {
            _addPointClickMeasureInfo(measureInfo, measures, name, name + 'Name');
        }, this);

        // using new Function is quicker than eval(), even in IE.
        var pointClickFn = new Function('return ' + fnString)();
        return function(clickEvent, data){
            pointClickFn(data, measureInfo, clickEvent);
        };
    };

    var _addPointClickMeasureInfo = function(measureInfo, measures, name, key) {
        if (LABKEY.Utils.isDefined(measures[name])) {
            var measuresArr = ensureMeasuresAsArray(measures[name]);
            $.each(measuresArr, function(idx, measure) {
                if (!LABKEY.Utils.isDefined(measureInfo[key])) {
                    measureInfo[key] = measure.name;
                }
                else if (!LABKEY.Utils.isDefined(measureInfo[measure.name])) {
                    measureInfo[measure.name] = measure.name;
                }
            }, this);
        }
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
     * Generate an array of plot configs for the given chart renderType and config options.
     * @param renderTo
     * @param chartConfig
     * @param labels
     * @param aes
     * @param scales
     * @param geom
     * @param data
     * @returns {Array} array of plot config objects
     */
    var generatePlotConfigs = function(renderTo, chartConfig, labels, aes, scales, geom, data)
    {
        var plotConfigArr = [];

        // if we have multiple y-measures and the request is to plot them separately, call the generatePlotConfig function
        // for each y-measure separately with its own copy of the chartConfig object
        if (chartConfig.geomOptions.chartLayout === 'per_measure' && LABKEY.Utils.isArray(chartConfig.measures.y)) {

            // if 'automatic across charts' scales are requested, need to manually calculate the min and max
            if (chartConfig.scales.y && chartConfig.scales.y.type === 'automatic') {
                scales.y = $.extend(scales.y, _getScaleDomainValuesForAllMeasures(data, chartConfig.measures.y, 'left'));
            }
            if (chartConfig.scales.yRight && chartConfig.scales.yRight.type === 'automatic') {
                scales.yRight = $.extend(scales.yRight, _getScaleDomainValuesForAllMeasures(data, chartConfig.measures.y, 'right'));
            }

            $.each(chartConfig.measures.y, function(idx, yMeasure) {
                // copy the config and reset the measures.y array with the single measure
                var newChartConfig = $.extend(true, {}, chartConfig);
                newChartConfig.measures.y = $.extend(true, {}, yMeasure);

                // copy the labels object so that we can set the subtitle based on the y-measure
                var newLabels = $.extend(true, {}, labels);
                newLabels.subtitle = {value: yMeasure.label || yMeasure.name};

                // only copy over the scales that are needed for this measures
                var side = yMeasure.yAxis || 'left';
                var newScales = {x: $.extend(true, {}, scales.x)};
                if (side === 'left') {
                    newScales.y = $.extend(true, {}, scales.y);
                }
                else {
                    newScales.yRight = $.extend(true, {}, scales.yRight);
                }

                plotConfigArr.push(generatePlotConfig(renderTo, newChartConfig, newLabels, aes, newScales, geom, data));
            }, this);
        }
        else {
            plotConfigArr.push(generatePlotConfig(renderTo, chartConfig, labels, aes, scales, geom, data));
        }

        return plotConfigArr;
    };

    var _getScaleDomainValuesForAllMeasures = function(data, measures, side) {
        var min = null, max = null;

        $.each(measures, function(idx, measure) {
            var measureSide = measure.yAxis || 'left';
            if (side === measureSide) {
                var accFn = LABKEY.vis.GenericChartHelper.getYMeasureAes(measure);
                var tempMin = d3.min(data, accFn);
                var tempMax = d3.max(data, accFn);

                if (min == null || tempMin < min) {
                    min = tempMin;
                }
                if (max == null || tempMax > max) {
                    max = tempMax;
                }
            }
        }, this);

        return {domain: [min, max]};
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
            emptyTextFn = function(){return '';},
            plotConfig = {
                renderTo: renderTo,
                rendererType: 'd3',
                width: chartConfig.width,
                height: chartConfig.height
            };

        if (renderType === 'pie_chart') {
            return _generatePieChartConfig(plotConfig, chartConfig, labels, data);
        }

        clipRect = (scales.x && LABKEY.Utils.isArray(scales.x.domain)) || (scales.y && LABKEY.Utils.isArray(scales.y.domain));

        // account for one or many y-measures by ensuring that we have an array of y-measures
        var yMeasures = ensureMeasuresAsArray(chartConfig.measures.y);

        if (renderType === 'bar_chart') {
            aes = { x: 'label', y: 'value' };

            if (LABKEY.Utils.isDefined(chartConfig.measures.xSub))
            {
                aes.xSub = 'subLabel';
                aes.color = 'label';
            }

            if (!scales.y) {
                scales.y = {};
            }

            if (!scales.y.domain) {
                var values = $.map(data, function(d) {return d.value;}),
                    min = Math.min(0, Math.min.apply(Math, values)),
                    max = Math.max(0, Math.max.apply(Math, values));

                scales.y.domain = [min, max];
            }
        }
        else if (renderType === 'box_plot' && chartConfig.pointType === 'all')
        {
            layers.push(
                new LABKEY.vis.Layer({
                    geom: LABKEY.vis.GenericChartHelper.generatePointGeom(chartConfig.geomOptions),
                    aes: {hoverText: LABKEY.vis.GenericChartHelper.generatePointHover(chartConfig.measures)}
                })
            );
        }
        else if (renderType === 'line_plot') {
            var xName = chartConfig.measures.x.name,
                isDate = isDateType(getMeasureType(chartConfig.measures.x));

            $.each(yMeasures, function(idx, yMeasure) {
                var pathAes = {
                    sortFn: function(a, b) {
                        // No need to handle the case for a or b or a.getValue() or b.getValue() null as they are
                        // not currently included in this plot.
                        if (isDate){
                            return new Date(a.getValue(xName)) - new Date(b.getValue(xName));
                        }
                        return a.getValue(xName) - b.getValue(xName);
                    }
                };

                pathAes[yMeasure.yAxis === 'right' ? 'yRight' : 'yLeft'] = getYMeasureAes(yMeasure);

                // use the series measure's values for the distinct colors and grouping
                if (chartConfig.measures.series) {
                    pathAes.pathColor = generateGroupingAcc(chartConfig.measures.series.name);
                    pathAes.group = generateGroupingAcc(chartConfig.measures.series.name);
                }
                // if no series measures but we have multiple y-measures, force the color and grouping to be distinct for each measure
                else if (yMeasures.length > 1) {
                    pathAes.pathColor = emptyTextFn;
                    pathAes.group = emptyTextFn;
                }

                layers.push(
                    new LABKEY.vis.Layer({
                        name: yMeasures.length > 1 ? yMeasure.label || yMeasure.name : undefined,
                        geom: new LABKEY.vis.Geom.Path({
                            color: '#' + chartConfig.geomOptions.pointFillColor,
                            size: chartConfig.geomOptions.lineWidth?chartConfig.geomOptions.lineWidth:3,
                            opacity:chartConfig.geomOptions.opacity
                        }),
                        aes: pathAes
                    })
                );
            }, this);
        }

        // Issue 34711: better guess at the max number of discrete x-axis tick mark labels to show based on the plot width
        if (scales.x && scales.x.scaleType === 'discrete' && scales.x.tickLabelMax) {
            // approx 30 px for a 45 degree rotated tick label
            scales.x.tickLabelMax = Math.floor((plotConfig.width - 300) / 30);
        }

        var margins = _getPlotMargins(renderType, scales, aes, data, plotConfig, chartConfig);
        if (LABKEY.Utils.isObject(margins)) {
            plotConfig.margins = margins;
        }

        if (chartConfig.measures.color)
        {
            scales.color = {
                colorType: chartConfig.geomOptions.colorPaletteScale,
                scaleType: 'discrete'
            }
        }

        if ((renderType === 'line_plot' || renderType === 'scatter_plot') && yMeasures.length > 0) {
            $.each(yMeasures, function (idx, yMeasure) {
                var layerAes = {};
                layerAes[yMeasure.yAxis === 'right' ? 'yRight' : 'yLeft'] = getYMeasureAes(yMeasure);

                // if no series measures but we have multiple y-measures, force the color and shape to be distinct for each measure
                if (!aes.color && yMeasures.length > 1) {
                    layerAes.color = emptyTextFn;
                }
                if (!aes.shape && yMeasures.length > 1) {
                    layerAes.shape = emptyTextFn;
                }

                layers.push(
                    new LABKEY.vis.Layer({
                        name: yMeasures.length > 1 ? yMeasure.label || yMeasure.name : undefined,
                        geom: geom,
                        aes: layerAes
                    })
                );
            }, this);
        }
        else {
            layers.push(
                new LABKEY.vis.Layer({
                    data: data,
                    geom: geom
                })
            );
        }

        plotConfig = $.extend(plotConfig, {
            clipRect: clipRect,
            data: data,
            labels: labels,
            aes: aes,
            scales: scales,
            layers: layers
        });

        return plotConfig;
    };

    var _willRotateXAxisTickText = function(scales, plotConfig, maxTickLength, data) {
        if (scales.x && scales.x.scaleType === 'discrete') {
            var tickCount = scales.x && scales.x.tickLabelMax ? Math.min(scales.x.tickLabelMax, data.length) : data.length;
            return (tickCount * maxTickLength * 5) > (plotConfig.width - 150);
        }

        return false;
    };

    var _getPlotMargins = function(renderType, scales, aes, data, plotConfig, chartConfig) {
        var margins = {};

        // issue 29690: for bar and box plots, set default bottom margin based on the number of labels and the max label length
        if (LABKEY.Utils.isArray(data)) {
            var maxLen = 0;
            $.each(data, function(idx, d) {
                var val = LABKEY.Utils.isFunction(aes.x) ? aes.x(d) : d[aes.x];
                if (LABKEY.Utils.isString(val)) {
                    maxLen = Math.max(maxLen, val.length);
                }
            });

            if (_willRotateXAxisTickText(scales, plotConfig, maxLen, data)) {
                // min bottom margin: 50, max bottom margin: 275
                var bottomMargin = Math.min(Math.max(50, maxLen*5), 275);
                margins.bottom = bottomMargin;
            }
        }

        // issue 31857: allow custom margins to be set in Chart Layout dialog
        if (chartConfig && chartConfig.geomOptions) {
            if (chartConfig.geomOptions.marginTop !== null) {
                margins.top = chartConfig.geomOptions.marginTop;
            }
            if (chartConfig.geomOptions.marginRight !== null) {
                margins.right = chartConfig.geomOptions.marginRight;
            }
            if (chartConfig.geomOptions.marginBottom !== null) {
                margins.bottom = chartConfig.geomOptions.marginBottom;
            }
            if (chartConfig.geomOptions.marginLeft !== null) {
                margins.left = chartConfig.geomOptions.marginLeft;
            }
        }

        return !LABKEY.Utils.isEmptyObj(margins) ? margins : null;
    };

    var _generatePieChartConfig = function(baseConfig, chartConfig, labels, data)
    {
        var hasData = data.length > 0;

        return $.extend(baseConfig, {
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
        var dataArray = LABKEY.Utils.isDefined(measureStore) ? measureStore.rows || measureStore.records() : [];
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
     * @param {Object} chartConfigOrMeasure The saved chartConfig object or a specific measure object.
     * @param {String} measureName The name of the axis measure property.
     * @param {Object} aes The aes object from generateAes.
     * @param {Object} scales The scales object from generateScales.
     * @param {Array} data The response data from selectRows.
     * @param {Boolean} dataConversionHappened Whether we converted any values in the measure data
     * @returns {Object}
     */
    var validateAxisMeasure = function(chartType, chartConfigOrMeasure, measureName, aes, scales, data, dataConversionHappened) {
        var measure = LABKEY.Utils.isObject(chartConfigOrMeasure) && chartConfigOrMeasure.measures ? chartConfigOrMeasure.measures[measureName] : chartConfigOrMeasure;
        return _validateAxisMeasure(chartType, measure, measureName, aes, scales, data, dataConversionHappened);
    };

    var _validateAxisMeasure = function(chartType, measure, measureName, aes, scales, data, dataConversionHappened) {
        var dataIsNull = true, measureUndefined = true, invalidLogValues = false, hasZeroes = false, message = null;

        // no need to check measures if we have no data
        if (data.length === 0) {
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
            message = 'The measure, ' + measure.name + ', was not found. It may have been renamed or removed.';
            return {success: false, message: message};
        }

        if ((chartType == 'scatter_plot' || chartType == 'line_plot' || measureName == 'y') && dataIsNull && !dataConversionHappened)
        {
            message = 'All data values for ' + measure.label + ' are null. Please choose a different measure.';
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
        return LABKEY.Utils.isObject(measure) ? (measure.normalizedType || measure.type) : null;
    };

    var isNumericType = function(type)
    {
        var t = LABKEY.Utils.isString(type) ? type.toLowerCase() : null;
        return t == 'int' || t == 'integer' || t == 'float' || t == 'double';
    };

    var isDateType = function(type)
    {
        var t = LABKEY.Utils.isString(type) ? type.toLowerCase() : null;
        return t == 'date';
    };

    var _getStudySubjectInfo = function()
    {
        var studyCtx = LABKEY.getModuleContext("study") || {};
        return LABKEY.Utils.isObject(studyCtx.subject) ? studyCtx.subject : {
            tableName: 'Participant',
            columnName: 'ParticipantId',
            nounPlural: 'Participants',
            nounSingular: 'Participant'
        };
    };

    var _getStudyTimepointType = function()
    {
        var studyCtx = LABKEY.getModuleContext("study") || {};
        return LABKEY.Utils.isDefined(studyCtx.timepointType) ? studyCtx.timepointType : null;
    };

    var _getMeasureRestrictions = function (chartType, measure)
    {
        var measureRestrictions = {};
        $.each(getRenderTypes(), function (idx, renderType)
        {
            if (renderType.name === chartType)
            {
                $.each(renderType.fields, function (idx2, field)
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
            if (chartConfig.measures.hasOwnProperty(measureName) && LABKEY.Utils.isObject(chartConfig.measures[measureName])) {
                configMeasure = chartConfig.measures[measureName];
                $.extend(measureRestrictions, _getMeasureRestrictions(renderType, measureName));

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
        if (!LABKEY.Utils.isEmptyObj(measuresForProcessing)) {
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
            if (!LABKEY.Utils.isEmptyObj(measuresForProcessing)) {
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

    var renderChartSVG = function(renderTo, queryConfig, chartConfig) {
        queryConfig.containerPath = LABKEY.container.path;

        if (queryConfig.filterArray && queryConfig.filterArray.length > 0) {
            var filters = [];

            for (var i = 0; i < queryConfig.filterArray.length; i++) {
                var f = queryConfig.filterArray[i];
                // Issue 37191: Check to see if 'f' is already a filter instance (either labkey-api-js/src/filter/Filter.ts or clientapi/core/Query.js)
                if (f.hasOwnProperty('getValue') || f.getValue instanceof Function) {
                    filters.push(f);
                }
                else {
                    filters.push(LABKEY.Filter.create(f.name,  f.value, LABKEY.Filter.getFilterTypeForURLSuffix(f.type)));
                }
            }

            queryConfig.filterArray = filters;
        }

        queryConfig.success = function(measureStore) {
            _renderChartSVG(renderTo, chartConfig, measureStore);
        };

        LABKEY.Query.MeasureStore.selectRows(queryConfig);
    };

    var _renderChartSVG = function(renderTo, chartConfig, measureStore) {
        var responseMetaData = measureStore.getResponseMetadata();

        // explicitly set the chart width/height if not set in the config
        if (!chartConfig.hasOwnProperty('width') || chartConfig.width == null) chartConfig.width = 1000;
        if (!chartConfig.hasOwnProperty('height') || chartConfig.height == null) chartConfig.height = 600;

        var xAxisType = chartConfig.measures.x ? (chartConfig.measures.x.normalizedType || chartConfig.measures.x.type) : null;
        var chartType = getChartType(chartConfig.renderType, xAxisType);
        var aes = generateAes(chartType, chartConfig.measures, responseMetaData.schemaName, responseMetaData.queryName);
        var valueConversionResponse = doValueConversion(chartConfig, aes, chartType, measureStore.records());
        if (!LABKEY.Utils.isEmptyObj(valueConversionResponse.processed)) {
            $.extend(true, chartConfig.measures, valueConversionResponse.processed);
            aes = generateAes(chartType, chartConfig.measures, responseMetaData.schemaName, responseMetaData.queryName);
        }
        var data = measureStore.records();
        if (chartType === 'scatter_plot' && data.length > chartConfig.geomOptions.binThreshold) {
            chartConfig.geomOptions.binned = true;
        }
        var scales = generateScales(chartType, chartConfig.measures, chartConfig.scales, aes, measureStore);
        var geom = generateGeom(chartType, chartConfig.geomOptions);
        var labels = generateLabels(chartConfig.labels);

        if (chartType === 'bar_chart' || chartType === 'pie_chart') {
            var dimName = null, subDimName = null; measureName = null, aggType = 'COUNT';

            if (chartConfig.measures.x) {
                dimName = chartConfig.measures.x.converted ? chartConfig.measures.x.convertedName : chartConfig.measures.x.name;
            }
            if (chartConfig.measures.xSub) {
                subDimName = chartConfig.measures.xSub.converted ? chartConfig.measures.xSub.convertedName : chartConfig.measures.xSub.name;
            }
            if (chartConfig.measures.y) {
                measureName = chartConfig.measures.y.converted ? chartConfig.measures.y.convertedName : chartConfig.measures.y.name;

                if (LABKEY.Utils.isDefined(chartConfig.measures.y.aggregate)) {
                    aggType = chartConfig.measures.y.aggregate;
                    aggType = LABKEY.Utils.isObject(aggType) ? aggType.value : aggType;
                }
                else if (measureName != null) {
                    aggType = 'SUM';
                }
            }

            data = LABKEY.vis.getAggregateData(data, dimName, subDimName, measureName, aggType, '[Blank]', false);
        }

        var validation = _validateChartConfig(chartConfig, aes, scales, measureStore);
        _renderMessages(renderTo, validation.messages);
        if (!validation.success)
            return;

        var plotConfigArr = generatePlotConfigs(renderTo, chartConfig, labels, aes, scales, geom, data);
        $.each(plotConfigArr, function(idx, plotConfig) {
            if (chartType === 'pie_chart') {
                new LABKEY.vis.PieChart(plotConfig);
            }
            else {
                new LABKEY.vis.Plot(plotConfig).render();
            }
        }, this);
    };

    var _renderMessages = function(divId, messages) {
        if (messages && messages.length > 0) {
            var errorDiv = document.createElement('div');
            errorDiv.setAttribute('style', 'padding: 10px; background-color: #ffe5e5; color: #d83f48; font-weight: bold;');
            errorDiv.innerHTML = messages.join('<br/>');
            document.getElementById(divId).appendChild(errorDiv);
        }
    };

    var _validateChartConfig = function(chartConfig, aes, scales, measureStore) {
        var hasNoDataMsg = validateResponseHasData(measureStore, false);
        if (hasNoDataMsg != null)
            return {success: false, messages: [hasNoDataMsg]};

        var messages = [], firstRecord = measureStore.records()[0], measureNames = Object.keys(chartConfig.measures);
        for (var i = 0; i < measureNames.length; i++) {
            var measuresArr = ensureMeasuresAsArray(chartConfig.measures[measureNames[i]]);
            for (var j = 0; j < measuresArr.length; j++) {
                var measure = measuresArr[j];
                if (LABKEY.Utils.isObject(measure)) {
                    if (measure.name && !LABKEY.Utils.isDefined(firstRecord[measure.name])) {
                        return {success: false, messages: ['The measure, ' + measure.name + ', is not available. It may have been renamed or removed.']};
                    }

                    var validation;
                    if (measureNames[i] === 'y') {
                        var yAes = {y: getYMeasureAes(measure)};
                        validation = validateAxisMeasure(chartConfig.renderType, measure, 'y', yAes, scales, measureStore.records());
                    }
                    else if (measureNames[i] === 'x' || measureNames[i] === 'xSub') {
                        validation = validateAxisMeasure(chartConfig.renderType, measure, measureNames[i], aes, scales, measureStore.records());
                    }

                    if (LABKEY.Utils.isObject(validation)) {
                        if (validation.message != null)
                            messages.push(validation.message);
                        if (!validation.success)
                            return {success: false, messages: messages};
                    }
                }
            }
        }

        return {success: true, messages: messages};
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
        getDistinctYAxisSides : getDistinctYAxisSides,
        getYMeasureAes : getYMeasureAes,
        getDefaultMeasuresLabel: getDefaultMeasuresLabel,
        ensureMeasuresAsArray: ensureMeasuresAsArray,
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
        generatePlotConfigs: generatePlotConfigs,
        generatePlotConfig: generatePlotConfig,
        validateResponseHasData: validateResponseHasData,
        validateAxisMeasure: validateAxisMeasure,
        validateXAxis: validateXAxis,
        validateYAxis: validateYAxis,
        renderChartSVG: renderChartSVG,
        /**
         * Loads all of the required dependencies for a Generic Chart.
         * @param {Function} callback The callback to be executed when all of the visualization dependencies have been loaded.
         * @param {Object} scope The scope to be used when executing the callback.
         */
        loadVisDependencies: LABKEY.requiresVisualization
    };
};