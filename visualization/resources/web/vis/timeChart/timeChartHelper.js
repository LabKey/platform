/*
 * Copyright (c) 2013-2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
if(!LABKEY.vis) {
    LABKEY.vis = {};
}

/**
 * @namespace Namespace used to encapsulate functions related to creating study Time Charts. Used in the
 * Time Chart Wizard and when exporting Time Charts as scripts.
 */
LABKEY.vis.TimeChartHelper = new function() {

    var studyNounSingular = 'Participant',
        studyNounPlural = 'Participants',
        studyNounColumnName = 'ParticipantId';

    if (LABKEY.moduleContext.study && LABKEY.moduleContext.study.subject)
    {
        studyNounSingular = LABKEY.moduleContext.study.subject.nounSingular;
        studyNounPlural = LABKEY.moduleContext.study.subject.nounPlural;
        studyNounColumnName = LABKEY.moduleContext.study.subject.columnName;
    }

    /**
     * Generate the main title and axis labels for the chart based on the specified x-axis and y-axis (left and right) labels.
     * @param {String} mainTitle The label to be used as the main chart title.
     * @param {Array} axisArr An array of axis information including the x-axis and y-axis (left and right) labels.
     * @returns {Object}
     */
    var generateLabels = function(mainTitle, axisArr) {
        var xTitle = '', yLeftTitle = '', yRightTitle = '';
        for (var i = 0; i < axisArr.length; i++)
        {
            var axis = axisArr[i];
            if (axis.name == "y-axis")
            {
                if (axis.side == "left")
                    yLeftTitle = axis.label;
                else
                    yRightTitle = axis.label;
            }
            else
            {
                xTitle = axis.label;
            }
        }

        return {
            main : {
                value : mainTitle
            },
            x : {
                value : xTitle
            },
            yLeft : {
                value : yLeftTitle
            },
            yRight : {
                value : yRightTitle
            }
        };
    };

    /**
     * Generates an object containing {@link LABKEY.vis.Scale} objects used for the chart.
     * @param {Object} config The chart configuration object that defines the selected measures, axis info, subjects/groups, etc.
     * @param {Object} tickMap For visit based charts, the x-axis tick mark mapping, from generateTickMap.
     * @param {Object} numberFormats The number format functions to use for the x-axis and y-axis (left and right) tick marks.
     * @returns {Object}
     */
    var generateScales = function(config, tickMap, numberFormats) {
        if (config.measures.length == 0)
            throw "There must be at least one specified measure in the chartInfo config!";

        var xMin = null, xMax = null, xTrans = null, xTickFormat, xTickHoverText,
            yLeftMin = null, yLeftMax = null, yLeftTrans = null, yLeftTickFormat,
            yRightMin = null, yRightMax = null, yRightTrans = null, yRightTickFormat;

        for (var i = 0; i < config.axis.length; i++)
        {
            var axis = config.axis[i];
            if (axis.name == "y-axis")
            {
                if (axis.side == "left")
                {
                    yLeftMin = typeof axis.range.min == "number" ? axis.range.min : (config.hasNoData ? 0 : null);
                    yLeftMax = typeof axis.range.max == "number" ? axis.range.max : (config.hasNoData ? 10 : null);
                    yLeftTrans = axis.scale ? axis.scale : "linear";
                    yLeftTickFormat = numberFormats.left ? numberFormats.left : null;
                }
                else
                {
                    yRightMin = typeof axis.range.min == "number" ? axis.range.min : (config.hasNoData ? 0 : null);
                    yRightMax = typeof axis.range.max == "number" ? axis.range.max : (config.hasNoData ? 10 : null);
                    yRightTrans = axis.scale ? axis.scale : "linear";
                    yRightTickFormat = numberFormats.right ? numberFormats.right : null;
                }
            }
            else
            {
                xMin = typeof axis.range.min == "number" ? axis.range.min : null;
                xMax = typeof axis.range.max == "number" ? axis.range.max : null;
                xTrans = axis.scale ? axis.scale : "linear";
            }
        }

        if (config.measures[0].time != "date")
        {
            xTickFormat = function(value) {
                return tickMap[value] ? tickMap[value].label : "";
            };

            xTickHoverText = function(value) {
                return tickMap[value] ? tickMap[value].description : "";
            };
        }

        return {
            x: {
                scaleType : 'continuous',
                trans : xTrans,
                min : xMin,
                max : xMax,
                tickFormat : xTickFormat ? xTickFormat : null,
                tickHoverText : xTickHoverText ? xTickHoverText : null
            },
            yLeft: {
                scaleType : 'continuous',
                trans : yLeftTrans,
                min : yLeftMin,
                max : yLeftMax,
                tickFormat : yLeftTickFormat ? yLeftTickFormat : null
            },
            yRight: {
                scaleType : 'continuous',
                trans : yRightTrans,
                min : yRightMin,
                max : yRightMax,
                tickFormat : yRightTickFormat ? yRightTickFormat : null
            },
            shape: {
                scaleType : 'discrete'
            }
        };
    };

    /**
     * Generate the x-axis interval column alias key. For date based charts, this will be a time interval (i.e. Days, Weeks, etc.)
     * and for visit based charts, this will be the column alias for the visit field.
     * @param {Object} config The chart configuration object that defines the selected measures, axis info, subjects/groups, etc.
     * @param {Array} individualColumnAliases The array of column aliases for the individual subject data.
     * @param {Array} aggregateColumnAliases The array of column aliases for the group/cohort aggregate data.
     * @param {String} nounSingular The singular name of the study subject noun (i.e. Participant).
     * @returns {String}
     */
    var generateIntervalKey = function(config, individualColumnAliases, aggregateColumnAliases, nounSingular) {
        if (config.measures.length == 0)
            throw "There must be at least one specified measure in the chartInfo config!";
        if (!individualColumnAliases && !aggregateColumnAliases)
            throw "We expect to either be displaying individual series lines or aggregate data!";

        if (config.measures[0].time == "date")
        {
            return config.measures[0].dateOptions.interval;
        }
        else
        {
            return individualColumnAliases ?
                LABKEY.vis.getColumnAlias(individualColumnAliases, (nounSingular || studyNounSingular) + "Visit/Visit") :
                LABKEY.vis.getColumnAlias(aggregateColumnAliases, (nounSingular || studyNounSingular) + "Visit/Visit");
        }
    };

    /**
     * Generate that x-axis tick mark mapping for a visit based chart.
     * @param {Object} visitMap For visit based charts, the study visit information map.
     * @returns {Object}
     */
    var generateTickMap = function(visitMap) {
        var tickMap = {};
        for (var rowId in visitMap)
        {
            if (visitMap.hasOwnProperty(rowId))
            {
                tickMap[visitMap[rowId].displayOrder] = {
                    label: visitMap[rowId].displayName,
                    description: visitMap[rowId].description || visitMap[rowId].displayName
                };
            }
        }

        return tickMap;
    };

    /**
     * Generates the aesthetic map object needed by the visualization API to render the chart. See {@link LABKEY.vis.Plot}
     * and {@link LABKEY.vis.Layer}.
     * @param {Object} config The chart configuration object that defines the selected measures, axis info, subjects/groups, etc.
     * @param {Object} visitMap For visit based charts, the study visit information map.
     * @param {Array} individualColumnAliases The array of column aliases for the individual subject data.
     * @param {String} intervalKey The x-axis interval column alias key (i.e. Days, Weeks, etc.), from generateIntervalKey.
     * @param {String} nounColumnName The name of the study subject noun column (i.e. ParticipantId).
     * @returns {Object}
     */
    var generateAes = function(config, visitMap, individualColumnAliases, intervalKey, nounColumnName) {
        if (config.measures.length == 0)
            throw "There must be at least one specified measure in the chartInfo config!";

        var xAes;
        if (config.measures[0].time == "date")
            xAes = function(row) { return (row[intervalKey] ? row[intervalKey].value : null); };
        else
            xAes = function(row) { return visitMap[row[intervalKey].value].displayOrder; };

        var individualSubjectColumn = individualColumnAliases ? LABKEY.vis.getColumnAlias(individualColumnAliases, nounColumnName || studyNounColumnName) : null;

        return {
            x: xAes,
            color: function(row) { return (row[individualSubjectColumn] ? row[individualSubjectColumn].value : null); },
            group: function(row) { return (row[individualSubjectColumn] ? row[individualSubjectColumn].value : null); },
            shape: function(row) { return (row[individualSubjectColumn] ? row[individualSubjectColumn].value : null); },
            pathColor: function(rows) { return (rows[0][individualSubjectColumn] ? rows[0][individualSubjectColumn].value : null); }
        };
    };

    /**
     * Generate an array of {@link LABKEY.vis.Layer} objects based on the selected chart series list.
     * @param {Object} config The chart configuration object that defines the selected measures, axis info, subjects/groups, etc.
     * @param {Object} visitMap For visit based charts, the study visit information map.
     * @param {Array} individualColumnAliases The array of column aliases for the individual subject data.
     * @param {Array} aggregateColumnAliases The array of column aliases for the group/cohort aggregate data.
     * @param {Array} aggregateData The array of group/cohort aggregate data, from getChartData.
     * @param {Array} seriesList The list of series that will be plotted for a given chart, from generateSeriesList.
     * @param {String} intervalKey The x-axis interval column alias key (i.e. Days, Weeks, etc.), from generateIntervalKey.
     * @param {String} nounColumnName The name of the study subject noun column (i.e. ParticipantId).
     * @returns {Array}
     */
    var generateLayers = function(config, visitMap, individualColumnAliases, aggregateColumnAliases, aggregateData, seriesList, intervalKey, nounColumnName) {
        if (config.measures.length == 0)
            throw "There must be at least one specified measure in the chartInfo config!";
        if (!individualColumnAliases && !aggregateColumnAliases)
            throw "We expect to either be displaying individual series lines or aggregate data!";

        var layers = [];
        var isDateBased = config.measures[0].time == "date";
        var individualSubjectColumn = individualColumnAliases ? LABKEY.vis.getColumnAlias(individualColumnAliases, nounColumnName || studyNounColumnName) : null;
        var aggregateSubjectColumn = "UniqueId";

        var generateLayerAes = function(name, yAxisSide, columnName){
            var yName = yAxisSide == "left" ? "yLeft" : "yRight";
            var aes = {};
            aes[yName] = function(row){return (row[columnName] ? parseFloat(row[columnName].value) : null)}; // Have to parseFloat because for some reason ObsCon from Luminex was returning strings not floats/ints.
            return aes;
        };

        var generateAggregateLayerAes = function(name, yAxisSide, columnName, intervalKey, subjectColumn, errorColumn){
            var yName = yAxisSide == "left" ? "yLeft" : "yRight";
            var aes = {};
            aes[yName] = function(row){return (row[columnName] ? parseFloat(row[columnName].value) : null)}; // Have to parseFloat because for some reason ObsCon from Luminex was returning strings not floats/ints.
            aes.group = aes.color = aes.shape = function(row){return row[subjectColumn].displayValue};
            aes.pathColor = function(rows){return rows[0][subjectColumn].displayValue};
            aes.error = function(row){return (row[errorColumn] ? row[errorColumn].value : null)};
            return aes;
        };

        var hoverTextFn = function(subjectColumn, intervalKey, name, columnName, visitMap, errorColumn, errorType){
            if (visitMap)
            {
                if (errorColumn)
                {
                    return function(row){
                        var subject = row[subjectColumn].displayValue ? row[subjectColumn].displayValue : row[subjectColumn].value;
                        var errorVal = row[errorColumn].value ? row[errorColumn].value : 'n/a';
                        return ' ' + subject + ',\n '+ visitMap[row[intervalKey].value].displayName + ',\n ' + name + ': ' + row[columnName].value +
                                ',\n ' + errorType + ': ' + errorVal;
                    }
                }
                else
                {
                    return function(row){
                        var subject = row[subjectColumn].displayValue ? row[subjectColumn].displayValue : row[subjectColumn].value;
                        return ' ' + subject + ',\n '+ visitMap[row[intervalKey].value].displayName + ',\n ' + name + ': ' + row[columnName].value;
                    };
                }
            }
            else
            {
                if (errorColumn)
                {
                    return function(row){
                        var subject = row[subjectColumn].displayValue ? row[subjectColumn].displayValue : row[subjectColumn].value;
                        var errorVal = row[errorColumn].value ? row[errorColumn].value : 'n/a';
                        return ' ' + subject + ',\n ' + intervalKey + ': ' + row[intervalKey].value + ',\n ' + name + ': ' + row[columnName].value +
                                ',\n ' + errorType + ': ' + errorVal;
                    };
                }
                else
                {
                    return function(row){
                        var subject = row[subjectColumn].displayValue ? row[subjectColumn].displayValue : row[subjectColumn].value;
                        return ' ' + subject + ',\n ' + intervalKey + ': ' + row[intervalKey].value + ',\n ' + name + ': ' + row[columnName].value;
                    };
                }
            }
        };

        // Issue 15369: if two measures have the same name, use the alias for the subsequent series names (which will be unique)
        // Issue 12369: if rendering two measures of the same pivoted value, use measure and pivot name for series names (which will be unique)
        var useUniqueSeriesNames = false;
        var uniqueChartSeriesNames = [];
        for (var i = 0; i < seriesList.length; i++)
        {
            if (uniqueChartSeriesNames.indexOf(seriesList[i].name) > -1)
            {
                useUniqueSeriesNames = true;
                break;
            }
            uniqueChartSeriesNames.push(seriesList[i].name);
        }

        for (var i = seriesList.length -1; i >= 0; i--)
        {
            var chartSeries = seriesList[i];

            var chartSeriesName = chartSeries.label;
            if (useUniqueSeriesNames)
            {
                if (chartSeries.aliasLookupInfo.pivotValue)
                    chartSeriesName = chartSeries.aliasLookupInfo.measureName + " " + chartSeries.aliasLookupInfo.pivotValue;
                else
                    chartSeriesName = chartSeries.aliasLookupInfo.alias;
            }

            var columnName = individualColumnAliases ? LABKEY.vis.getColumnAlias(individualColumnAliases, chartSeries.aliasLookupInfo) : LABKEY.vis.getColumnAlias(aggregateColumnAliases, chartSeries.aliasLookupInfo);
            if (individualColumnAliases)
            {
                var pathLayerConfig = {
                    geom: new LABKEY.vis.Geom.Path({size: config.lineWidth}),
                    aes: generateLayerAes(chartSeriesName, chartSeries.yAxisSide, columnName)
                };

                if (seriesList.length > 1)
                    pathLayerConfig.name = chartSeriesName;

                layers.push(new LABKEY.vis.Layer(pathLayerConfig));

                if (!config.hideDataPoints)
                {
                    var pointLayerConfig = {
                        geom: new LABKEY.vis.Geom.Point(),
                        aes: generateLayerAes(chartSeriesName, chartSeries.yAxisSide, columnName)
                    };

                    if (seriesList.length > 1)
                        pointLayerConfig.name = chartSeriesName;

                    if (isDateBased)
                        pointLayerConfig.aes.hoverText = hoverTextFn(individualSubjectColumn, intervalKey, chartSeriesName, columnName, null, null, null);
                    else
                        pointLayerConfig.aes.hoverText = hoverTextFn(individualSubjectColumn, intervalKey, chartSeriesName, columnName, visitMap, null, null);

                    if (config.pointClickFn)
                    {
                        pointLayerConfig.aes.pointClickFn = generatePointClickFn(
                                config.pointClickFn,
                                {participant: individualSubjectColumn, interval: intervalKey, measure: columnName},
                                {schemaName: chartSeries.schemaName, queryName: chartSeries.queryName, name: chartSeriesName}
                        );
                    }

                    layers.push(new LABKEY.vis.Layer(pointLayerConfig));
                }
            }

            if (aggregateData && aggregateColumnAliases)
            {
                var errorBarType = null;
                if (config.errorBars == 'SD')
                    errorBarType = '_STDDEV';
                else if (config.errorBars == 'SEM')
                    errorBarType = '_STDERR';

                var errorColumnName = errorBarType ? LABKEY.vis.getColumnAlias(aggregateColumnAliases, chartSeries.aliasLookupInfo) + errorBarType : null;

                var aggregatePathLayerConfig = {
                    data: aggregateData,
                    geom: new LABKEY.vis.Geom.Path({size: config.lineWidth}),
                    aes: generateAggregateLayerAes(chartSeriesName, chartSeries.yAxisSide, columnName, intervalKey, aggregateSubjectColumn, errorColumnName)
                };

                if (seriesList.length > 1)
                    aggregatePathLayerConfig.name = chartSeriesName;

                layers.push(new LABKEY.vis.Layer(aggregatePathLayerConfig));

                if (errorColumnName)
                {
                    var aggregateErrorLayerConfig = {
                        data: aggregateData,
                        geom: new LABKEY.vis.Geom.ErrorBar(),
                        aes: generateAggregateLayerAes(chartSeriesName, chartSeries.yAxisSide, columnName, intervalKey, aggregateSubjectColumn, errorColumnName)
                    };

                    if (seriesList.length > 1)
                        aggregateErrorLayerConfig.name = chartSeriesName;

                    layers.push(new LABKEY.vis.Layer(aggregateErrorLayerConfig));
                }

                if (!config.hideDataPoints)
                {
                    var aggregatePointLayerConfig = {
                        data: aggregateData,
                        geom: new LABKEY.vis.Geom.Point(),
                        aes: generateAggregateLayerAes(chartSeriesName, chartSeries.yAxisSide, columnName, intervalKey, aggregateSubjectColumn, errorColumnName)
                    };

                    if (seriesList.length > 1)
                        aggregatePointLayerConfig.name = chartSeriesName;

                    if (isDateBased)
                        aggregatePointLayerConfig.aes.hoverText = hoverTextFn(aggregateSubjectColumn, intervalKey, chartSeriesName, columnName, null, errorColumnName, config.errorBars)
                    else
                        aggregatePointLayerConfig.aes.hoverText = hoverTextFn(aggregateSubjectColumn, intervalKey, chartSeriesName, columnName, visitMap, errorColumnName, config.errorBars);

                    if (config.pointClickFn)
                    {
                        aggregatePointLayerConfig.aes.pointClickFn = generatePointClickFn(
                                config.pointClickFn,
                                {group: aggregateSubjectColumn, interval: intervalKey, measure: columnName},
                                {schemaName: chartSeries.schemaName, queryName: chartSeries.queryName, name: chartSeriesName}
                        );
                    }

                    layers.push(new LABKEY.vis.Layer(aggregatePointLayerConfig));
                }
            }
        }

        return layers;
    };

    // private function
    var generatePointClickFn = function(fnString, columnMap, measureInfo){
        // the developer is expected to return a function, so we encapalate it within the anonymous function
        // (note: the function should have already be validated in a try/catch when applied via the developerOptionsPanel)

        // using new Function is quicker than eval(), even in IE.
        var pointClickFn = new Function('return ' + fnString)();
        return function(clickEvent, data) {
            pointClickFn(data, columnMap, measureInfo, clickEvent);
        };
    };

    /**
     * Generate the list of series to be plotted in a given Time Chart. A series will be created for each measure and
     * dimension that is selected in the chart.
     * @param {Array} measures The array of selected measures from the chart config.
     * @returns {Array}
     */
    var generateSeriesList = function(measures) {
        var arr = [];
        for (var i = 0; i < measures.length; i++)
        {
            var md = measures[i];

            if (md.dimension && md.dimension.values)
            {
                Ext4.each(md.dimension.values, function(val) {
                    arr.push({
                        schemaName: md.dimension.schemaName,
                        queryName: md.dimension.queryName,
                        name: val,
                        label: val,
                        measureIndex: i,
                        yAxisSide: md.measure.yAxis,
                        aliasLookupInfo: {measureName: md.measure.name, pivotValue: val}
                    });
                });
            }
            else
            {
                arr.push({
                    schemaName: md.measure.schemaName,
                    queryName: md.measure.queryName,
                    name: md.measure.name,
                    label: md.measure.label,
                    measureIndex: i,
                    yAxisSide: md.measure.yAxis,
                    aliasLookupInfo: md.measure.alias ? {alias: md.measure.alias} : {measureName: md.measure.name}
                });
            }
        }
        return arr;
    };

    // private function
    var generateDataSortArray = function(subject, firstMeasure, isDateBased, nounSingular) {
        return [
            subject,
            {
                schemaName : firstMeasure.dateOptions ? firstMeasure.dateOptions.dateCol.schemaName : firstMeasure.measure.schemaName,
                queryName : firstMeasure.dateOptions ? firstMeasure.dateOptions.dateCol.queryName : firstMeasure.measure.queryName,
                name : isDateBased ? firstMeasure.dateOptions.dateCol.name : (nounSingular || studyNounSingular) + "Visit/Visit/DisplayOrder"
            },
            {
                schemaName : firstMeasure.dateOptions ? firstMeasure.dateOptions.dateCol.schemaName : firstMeasure.measure.schemaName,
                queryName : firstMeasure.dateOptions ? firstMeasure.dateOptions.dateCol.queryName : firstMeasure.measure.queryName,
                name : (nounSingular || studyNounSingular) + (isDateBased ? "Visit/Visit" : "Visit/Visit/SequenceNumMin")
            }
        ];
    };

    /**
     * Determine whether or not the chart needs to clip the plotted lines and points based on manually set axis ranges.
     * @param {Object} config The chart configuration object that defines the selected measures, axis info, subjects/groups, etc.
     * @returns {boolean}
     */
    var generateApplyClipRect = function(config) {
        var xAxisIndex = getAxisIndex(config.axis, "x-axis");
        var leftAxisIndex = getAxisIndex(config.axis, "y-axis", "left");
        var rightAxisIndex = getAxisIndex(config.axis, "y-axis", "right");

        return (
            xAxisIndex > -1 && (config.axis[xAxisIndex].range.min != null || config.axis[xAxisIndex].range.max != null) ||
            leftAxisIndex > -1 && (config.axis[leftAxisIndex].range.min != null || config.axis[leftAxisIndex].range.max != null) ||
            rightAxisIndex > -1 && (config.axis[rightAxisIndex].range.min != null || config.axis[rightAxisIndex].range.max != null)
        );
    };

    /**
     * Generates axis range min and max values based on the full Time Chart data. This will be used when plotting multiple
     * charts that are set to use the same axis ranges across all charts in the report.
     * @param {Object} config The chart configuration object that defines the selected measures, axis info, subjects/groups, etc.
     * @param {Object} data The data object, from getChartData.
     * @param {Array} seriesList The list of series that will be plotted for a given chart, from generateSeriesList.
     * @param {String} nounSingular The singular name of the study subject noun (i.e. Participant).
     */
    var generateAcrossChartAxisRanges = function(config, data, seriesList, nounSingular) {
        if (config.measures.length == 0)
            throw "There must be at least one specified measure in the chartInfo config!";
        if (!data.individual && !data.aggregate)
            throw "We expect to either be displaying individual series lines or aggregate data!";

        var rows = data.individual ? data.individual.rows : (data.aggregate ? data.aggregate.rows : []);
        config.hasNoData = rows.length == 0;

        // In multi-chart case, we need to precompute the default axis ranges so that all charts share them
        // (if 'automatic across charts' is selected for the given axis)
        if (config.chartLayout != "single")
        {
            var leftMeasures = [],
                rightMeasures = [],
                xName, xFunc,
                min, max, tempMin, tempMax, errorBarType,
                leftAccessor, leftAccessorMax, leftAccessorMin, rightAccessorMax, rightAccessorMin, rightAccessor,
                columnAliases = data.individual ? data.individual.columnAliases : (data.aggregate ? data.aggregate.columnAliases : null),
                isDateBased = config.measures[0].time == "date",
                xAxisIndex = getAxisIndex(config.axis, "x-axis"),
                leftAxisIndex = getAxisIndex(config.axis, "y-axis", "left"),
                rightAxisIndex = getAxisIndex(config.axis, "y-axis", "right");

            for (var i = 0; i < seriesList.length; i++)
            {
                var columnName = LABKEY.vis.getColumnAlias(columnAliases, seriesList[i].aliasLookupInfo);
                if (seriesList[i].yAxisSide == "left")
                    leftMeasures.push(columnName);
                else if (seriesList[i].yAxisSide == "right")
                    rightMeasures.push(columnName);
            }

            if (isDateBased)
            {
                xName = config.measures[0].dateOptions.interval;
                xFunc = function(row){
                    return row[xName].value;
                };
            }
            else
            {
                var visitMap = data.individual ? data.individual.visitMap : data.aggregate.visitMap;
                xName = LABKEY.vis.getColumnAlias(columnAliases, (nounSingular || studyNounSingular) + "Visit/Visit");
                xFunc = function(row){
                    return visitMap[row[xName].value].displayOrder;
                };
            }

            if (config.axis[xAxisIndex].range.type != 'automatic_per_chart')
            {
                if (!config.axis[xAxisIndex].range.min)
                    config.axis[xAxisIndex].range.min = d3.min(rows, xFunc);

                if (!config.axis[xAxisIndex].range.max)
                    config.axis[xAxisIndex].range.max = d3.max(rows, xFunc);
            }

            if (config.errorBars !== 'None')
                errorBarType = config.errorBars == 'SD' ? '_STDDEV' : '_STDERR';

            if (leftAxisIndex > -1)
            {
                // If we have a left axis then we need to find the min/max
                min = null; max = null; tempMin = null; tempMax = null;
                leftAccessor = function(row) {
                    return (row[leftMeasures[i]] ? row[leftMeasures[i]].value : null);
                };

                if (errorBarType)
                {
                    // If we have error bars we need to calculate min/max with the error values in mind.
                    leftAccessorMin = function(row){
                        if (row[leftMeasures[i] + errorBarType])
                        {
                            var error = row[leftMeasures[i] + errorBarType].value;
                            return row[leftMeasures[i]].value - error;
                        }
                        else
                            return null;
                    };

                    leftAccessorMax = function(row){
                        if (row[leftMeasures[i] + errorBarType])
                        {
                            var error = row[leftMeasures[i] + errorBarType].value;
                            return row[leftMeasures[i]].value + error;
                        }
                        else
                            return null;
                    };
                }

                if (config.axis[leftAxisIndex].range.type != 'automatic_per_chart')
                {
                    if (!config.axis[leftAxisIndex].range.min)
                    {
                        for (var i = 0; i < leftMeasures.length; i++)
                        {
                            tempMin = d3.min(rows, leftAccessorMin ? leftAccessorMin : leftAccessor);
                            min = min == null ? tempMin : tempMin < min ? tempMin : min;
                        }
                        config.axis[leftAxisIndex].range.min = min;
                    }

                    if (!config.axis[leftAxisIndex].range.max)
                    {
                        for (var i = 0; i < leftMeasures.length; i++)
                        {
                            tempMax = d3.max(rows, leftAccessorMax ? leftAccessorMax : leftAccessor);
                            max = max == null ? tempMax : tempMax > max ? tempMax : max;
                        }
                        config.axis[leftAxisIndex].range.max = max;
                    }
                }
            }

            if (rightAxisIndex > -1)
            {
                // If we have a right axis then we need to find the min/max
                min = null; max = null; tempMin = null; tempMax = null;
                rightAccessor = function(row){
                    return row[rightMeasures[i]].value
                };

                if (errorBarType)
                {
                    rightAccessorMin = function(row){
                        var error = row[rightMeasures[i] + errorBarType].value;
                        return row[rightMeasures[i]].value - error;
                    };

                    rightAccessorMax = function(row){
                        var error = row[rightMeasures[i] + errorBarType].value;
                        return row[rightMeasures[i]].value + error;
                    };
                }

                if (config.axis[rightAxisIndex].range.type != 'automatic_per_chart')
                {
                    if (!config.axis[rightAxisIndex].range.min)
                    {
                        for (var i = 0; i < rightMeasures.length; i++)
                        {
                            tempMin = d3.min(rows, rightAccessorMin ? rightAccessorMin : rightAccessor);
                            min = min == null ? tempMin : tempMin < min ? tempMin : min;
                        }
                        config.axis[rightAxisIndex].range.min = min;
                    }

                    if (!config.axis[rightAxisIndex].range.max)
                    {
                        for (var i = 0; i < rightMeasures.length; i++)
                        {
                            tempMax = d3.max(rows, rightAccessorMax ? rightAccessorMax : rightAccessor);
                            max = max == null ? tempMax : tempMax > max ? tempMax : max;
                        }
                        config.axis[rightAxisIndex].range.max = max;
                    }
                }
            }
        }
    };

    /**
     * Generates plot configs to be passed to the {@link LABKEY.vis.Plot} function for each chart in the report.
     * @param {Object} config The chart configuration object that defines the selected measures, axis info, subjects/groups, etc.
     * @param {Object} data The data object, from getChartData.
     * @param {Array} seriesList The list of series that will be plotted for a given chart, from generateSeriesList.
     * @param {boolean} applyClipRect A boolean indicating whether or not to clip the plotted data region, from generateApplyClipRect.
     * @param {int} maxCharts The maximum number of charts to display in one report.
     * @param {String} nounColumnName The name of the study subject noun column (i.e. ParticipantId).
     * @returns {Array}
     */
    var generatePlotConfigs = function(config, data, seriesList, applyClipRect, maxCharts, nounColumnName) {
        var plotConfigInfoArr = [],
            subjectColumnName = null;

        if (data.individual)
            subjectColumnName = LABKEY.vis.getColumnAlias(data.individual.columnAliases, nounColumnName || studyNounColumnName);

        var generateGroupSeries = function(rows, groups, subjectColumn) {
            // subjectColumn is the aliasColumnName looked up from the getData response columnAliases array
            // groups is config.subject.groups
            var dataByGroup = {};

            for (var i = 0; i < rows.length; i++)
            {
                var rowSubject = rows[i][subjectColumn].value;
                for (var j = 0; j < groups.length; j++)
                {
                    if (groups[j].participantIds.indexOf(rowSubject) > -1)
                    {
                        if (!dataByGroup[groups[j].label])
                            dataByGroup[groups[j].label] = [];

                        dataByGroup[groups[j].label].push(rows[i]);
                    }
                }
            }

            return dataByGroup;
        };

        var concatChartTitle = function(mainTitle, subTitle) {
            return mainTitle + (mainTitle ? ': ' : '') + subTitle;
        };

        // four options: all series on one chart, one chart per subject, one chart per group, or one chart per measure/dimension
        if (config.chartLayout == "per_subject")
        {
            var dataPerParticipant = getDataWithSeriesCheck(data.individual.rows, function(row){return row[subjectColumnName].value}, seriesList, data.individual.columnAliases);
            for (var participant in dataPerParticipant)
            {
                if (dataPerParticipant.hasOwnProperty(participant))
                {
                    // skip the group if there is no data for it
                    if (!dataPerParticipant[participant].hasSeriesData)
                        continue;

                    plotConfigInfoArr.push({
                        title: concatChartTitle(config.title, participant),
                        series: seriesList,
                        individualData: dataPerParticipant[participant].data,
                        style: config.subject.values.length > 1 ? 'border-bottom: solid black 1px;' : null,
                        applyClipRect: applyClipRect
                    });

                    if (plotConfigInfoArr.length > maxCharts)
                        break;
                }
            }
        }
        else if (config.chartLayout == "per_group")
        {
            var groupedIndividualData = null, groupedAggregateData = null;

            //Display individual lines
            if (data.individual)
                groupedIndividualData = generateGroupSeries(data.individual.rows, config.subject.groups, subjectColumnName);

            // Display aggregate lines
            if (data.aggregate)
                groupedAggregateData = getDataWithSeriesCheck(data.aggregate.rows, function(row){return row.UniqueId.displayValue}, seriesList, data.aggregate.columnAliases);

            for (var i = 0; i < (config.subject.groups.length > maxCharts ? maxCharts : config.subject.groups.length); i++)
            {
                var group = config.subject.groups[i];

                // skip the group if there is no data for it
                if ((groupedIndividualData != null && !groupedIndividualData[group.label])
                        || (groupedAggregateData != null && (!groupedAggregateData[group.label] || !groupedAggregateData[group.label].hasSeriesData)))
                {
                    continue;
                }

                plotConfigInfoArr.push({
                    title: concatChartTitle(config.title, group.label),
                    series: seriesList,
                    individualData: groupedIndividualData && groupedIndividualData[group.label] ? groupedIndividualData[group.label] : null,
                    aggregateData: groupedAggregateData && groupedAggregateData[group.label] ? groupedAggregateData[group.label].data : null,
                    style: config.subject.groups.length > 1 ? 'border-bottom: solid black 1px;' : null,
                    applyClipRect: applyClipRect
                });

                if (plotConfigInfoArr.length > maxCharts)
                    break;
            }
        }
        else if (config.chartLayout == "per_dimension")
        {
            for (var i = 0; i < (seriesList.length > maxCharts ? maxCharts : seriesList.length); i++)
            {
                // skip the measure/dimension if there is no data for it
                if ((data.aggregate && !data.aggregate.hasData[seriesList[i].name])
                        || (data.individual && !data.individual.hasData[seriesList[i].name]))
                {
                    continue;
                }

                plotConfigInfoArr.push({
                    title: concatChartTitle(config.title, seriesList[i].label),
                    series: [seriesList[i]],
                    individualData: data.individual ? data.individual.rows : null,
                    aggregateData: data.aggregate ? data.aggregate.rows : null,
                    style: seriesList.length > 1 ? 'border-bottom: solid black 1px;' : null,
                    applyClipRect: applyClipRect
                });

                if (plotConfigInfoArr.length > maxCharts)
                    break;
            }
        }
        else if (config.chartLayout == "single")
        {
            //Single Line Chart, with all participants or groups.
            plotConfigInfoArr.push({
                title: config.title,
                series: seriesList,
                individualData: data.individual ? data.individual.rows : null,
                aggregateData: data.aggregate ? data.aggregate.rows : null,
                height: 610,
                style: null,
                applyClipRect: applyClipRect
            });
        }

        return plotConfigInfoArr;
    };

    // private function
    var getDataWithSeriesCheck = function(data, groupAccessor, seriesList, columnAliases) {
        /*
         Groups data by the groupAccessor passed in. Also, checks for the existance of any series data for that groupAccessor.
         Returns an object where each attribute will be a groupAccessor with an array of data rows and a boolean for hasSeriesData
         */
        var groupedData = {};
        for (var i = 0; i < data.length; i++)
        {
            var value = groupAccessor(data[i]);
            if (!groupedData[value])
            {
                groupedData[value] = {data: [], hasSeriesData: false};
            }
            groupedData[value].data.push(data[i]);

            for (var j = 0; j < seriesList.length; j++)
            {
                var seriesAlias = LABKEY.vis.getColumnAlias(columnAliases, seriesList[j].aliasLookupInfo);
                if (seriesAlias && data[i][seriesAlias] && data[i][seriesAlias].value)
                {
                    groupedData[value].hasSeriesData = true;
                    break;
                }
            }
        }
        return groupedData;
    };

    /**
     * Get the index in the axes array for a given axis (ie left y-axis).
     * @param {Array} axes The array of specified axis information for this chart.
     * @param {String} axisName The chart axis (i.e. x-axis or y-axis).
     * @param {String} [side] The y-axis side (i.e. left or right).
     * @returns {number}
     */
    var getAxisIndex = function(axes, axisName, side) {
        var index = -1;
        for (var i = 0; i < axes.length; i++)
        {
            if (!side && axes[i].name == axisName)
            {
                index = i;
                break;
            }
            else if (axes[i].name == axisName && axes[i].side == side)
            {
                index = i;
                break;
            }
        }
        return index;
    };

    /**
     * Get the data needed for the specified Time Chart based on the chart config. Makes calls to the
     * {@link LABKEY.Query.Visualization.getData} to get the individual subject data and grouped aggregate data.
     * Calls the success callback function in the config when it has received all of the requested data.
     * @param {Object} config The chart configuration object that defines the selected measures, axis info, subjects/groups, etc.
     */
    var getChartData = function(config) {
        if (!config.success)
            throw "You must specify a success callback function!";
        if (!config.failure)
            throw "You must specify a failure callback function!";
        if (!config.chartInfo)
            throw "You must specify a chartInfo config!";
        if (config.chartInfo.measures.length == 0)
            throw "There must be at least one specified measure in the chartInfo config!";
        if (!config.chartInfo.displayIndividual && !config.chartInfo.displayAggregate)
            throw "We expect to either be displaying individual series lines or aggregate data!";

        // issue 22254: perf issues if we try to show individual lines for a group with a large number of subjects
        var subjectLength = config.chartInfo.subject.values ? config.chartInfo.subject.values.length : 0;
        if (config.chartInfo.displayIndividual && subjectLength > 10000)
        {
            config.chartInfo.displayIndividual = false;
            config.chartInfo.subject.values = undefined;
        }

        var chartData = {numberFormats: {}};
        var counter = config.chartInfo.displayIndividual && config.chartInfo.displayAggregate ? 2 : 1;
        var isDateBased = config.chartInfo.measures[0].time == "date";
        var seriesList = generateSeriesList(config.chartInfo.measures);

        // Issue 16156: for date based charts, give error message if there are no calculated interval values
        chartData.hasIntervalData = !isDateBased;
        var checkForIntervalValues = function(row) {
            if (isDateBased)
            {
                var intervalAlias = config.chartInfo.measures[0].dateOptions.interval;
                if (row[intervalAlias] && row[intervalAlias].value != null)
                    chartData.hasIntervalData = true;
            }
        };

        var trimVisitMapDomain = function(origVisitMap, visitsInDataArr) {
            // get the visit map info for those visits in the response data
            var trimmedVisits = [];
            for (var v in origVisitMap)
            {
                if (origVisitMap.hasOwnProperty(v))
                {
                    if (visitsInDataArr.indexOf(v) != -1)
                    {
                        trimmedVisits.push(Ext4.apply({id: v}, origVisitMap[v]));
                    }
                }
            }
            // sort the trimmed visit list by displayOrder and then reset displayOrder starting at 1
            trimmedVisits.sort(function(a,b){return a.displayOrder - b.displayOrder});
            var newVisitMap = {};
            for (var i = 0; i < trimmedVisits.length; i++)
            {
                trimmedVisits[i].displayOrder = i + 1;
                newVisitMap[trimmedVisits[i].id] = trimmedVisits[i];
            }

            return newVisitMap;
        };

        var successCallback = function(response, dataType) {

            // make sure each measure/dimension has at least some data, and get a list of which visits are in the data response
            // also keep track of which measure/dimensions have negative values (for log scale)
            var visitsInData = [];
            response.hasData = {};
            response.hasNegativeValues = {};
            Ext4.each(seriesList, function(s) {
                response.hasData[s.name] = false;
                response.hasNegativeValues[s.name] = false;
                for (var i = 0; i < response.rows.length; i++)
                {
                    var row = response.rows[i];
                    var alias = LABKEY.vis.getColumnAlias(response.columnAliases, s.aliasLookupInfo);
                    if (row[alias] && row[alias].value != null)
                    {
                        response.hasData[s.name] = true;

                        if (row[alias].value < 0)
                            response.hasNegativeValues[s.name] = true;
                    }

                    var visitMappedName = LABKEY.vis.getColumnAlias(response.columnAliases, (config.nounSingular || studyNounSingular) + "Visit/Visit");
                    if (!isDateBased && row[visitMappedName])
                    {
                        var visitVal = row[visitMappedName].value;
                        if (visitsInData.indexOf(visitVal) == -1)
                            visitsInData.push(visitVal.toString());
                    }

                    checkForIntervalValues(row);
                }
            });

            // trim the visit map domain to just those visits in the response data
            response.visitMap = trimVisitMapDomain(response.visitMap, visitsInData);

            chartData[dataType] = response;

            generateNumberFormats(config.chartInfo, chartData, config.defaultNumberFormat);

            // if we have all request data back, return the result
            counter--;
            if (counter == 0)
                config.success.call(config.scope, chartData);
        };

        if (config.chartInfo.displayIndividual)
        {
            //Get data for individual lines.
            LABKEY.Query.Visualization.getData({
                containerPath: config.containerPath,
                success: function(response) {
                    successCallback(response, "individual");
                },
                failure : function(info, response, options) {
                    config.failure.call(config.scope, info);
                },
                measures: config.chartInfo.measures,
                sorts: generateDataSortArray(config.chartInfo.subject, config.chartInfo.measures[0], isDateBased, config.nounSingular),
                limit : config.dataLimit || 10000,
                parameters : config.chartInfo.parameters,
                filterUrl: config.chartInfo.filterUrl,
                filterQuery: config.chartInfo.filterQuery
            });
        }

        if (config.chartInfo.displayAggregate)
        {
            //Get data for Aggregates lines.
            var groups = [];
            for (var i = 0; i < config.chartInfo.subject.groups.length; i++)
            {
                var group = config.chartInfo.subject.groups[i];
                // encode the group id & type, so we can distinguish between cohort and participant group in the union table
                groups.push(group.id + '-' + group.type);
            }

            LABKEY.Query.Visualization.getData({
                containerPath: config.containerPath,
                success: function(response) {
                    successCallback(response, "aggregate");
                },
                failure : function(info) {
                    config.failure.call(config.scope, info);
                },
                measures: config.chartInfo.measures,
                groupBys: [
                    // Issue 18747: if grouping by cohorts and ptid groups, order it so the cohorts are first
                    {schemaName: 'study', queryName: 'ParticipantGroupCohortUnion', name: 'GroupingOrder', values: [0,1]},
                    {schemaName: 'study', queryName: 'ParticipantGroupCohortUnion', name: 'UniqueId', values: groups}
                ],
                sorts: generateDataSortArray(config.chartInfo.subject, config.chartInfo.measures[0], isDateBased, config.nounSingular),
                limit : config.dataLimit || 10000,
                parameters : config.chartInfo.parameters,
                filterUrl: config.chartInfo.filterUrl,
                filterQuery: config.chartInfo.filterQuery
            });
        }
    };

    /**
     * Generate the number format functions for the left and right y-axis and attach them to the chart data object
     * @param {Object} config The chart configuration object that defines the selected measures, axis info, subjects/groups, etc.
     * @param {Object} data The data object, from getChartData.
     * @param {Object} defaultNumberFormat
     */
    var generateNumberFormats = function(config, data, defaultNumberFormat) {
        var fields = data.individual ? data.individual.metaData.fields : data.aggregate.metaData.fields;

        for (var i = 0; i < config.axis.length; i++)
        {
            var axis = config.axis[i];
            if (axis.side)
            {
                // Find the first measure with the matching side that has a numberFormat.
                for (var j = 0; j < config.measures.length; j++)
                {
                    var measure = config.measures[j].measure;

                    if (data.numberFormats[axis.side])
                        break;

                    if (measure.yAxis == axis.side)
                    {
                        var metaDataName = measure.alias;
                        for (var k = 0; k < fields.length; k++)
                        {
                            var field = fields[k];
                            if (field.name == metaDataName)
                            {
                                if (field.extFormatFn)
                                {
                                    data.numberFormats[axis.side] = eval(field.extFormatFn);
                                    break;
                                }
                            }
                        }
                    }
                }

                if (!data.numberFormats[axis.side])
                {
                    // If after all the searching we still don't have a numberformat use the default number format.
                    data.numberFormats[axis.side] = defaultNumberFormat;
                }
            }
        }
    };

    /**
     * Verifies the information in the chart config to make sure it has proper measures, axis info, subjects/groups, etc.
     * Returns an object with a success parameter (boolean) and a message parameter (string). If the success pararameter
     * is false there is a critical error and the chart cannot be rendered. If success is true the chart can be rendered.
     * Message will contain an error or warning message if applicable. If message is not null and success is true, there is a warning.
     * @param {Object} config The chart configuration object that defines the selected measures, axis info, subjects/groups, etc.
     * @returns {Object}
     */
    var validateChartConfig = function(config) {
        var message = "";

        if (!config.measures || config.measures.length == 0)
        {
            message = "No measure selected. Please select at lease one measure.";
            return {success: false, message: message};
        }

        if (!config.axis || getAxisIndex(config.axis, "x-axis") == -1)
        {
            message = "Could not find x-axis in chart measure information.";
            return {success: false, message: message};
        }

        if (config.chartSubjectSelection == "subjects" && config.subject.values.length == 0)
        {
            message = "No " + studyNounSingular.toLowerCase() + " selected. " +
                    "Please select at least one " + studyNounSingular.toLowerCase() + ".";
            return {success: false, message: message};
        }

        if (config.chartSubjectSelection == "groups" && config.subject.groups.length < 1)
        {
            message = "No group selected. Please select at least one group.";
            return {success: false, message: message};
        }

        if (generateSeriesList(config.measures).length == 0)
        {
            message = "No series or dimension selected. Please select at least one series/dimension value.";
            return {success: false, message: message};
        }

        if (!(config.displayIndividual || config.displayAggregate))
        {
            message = "Please select either \"Show Individual Lines\" or \"Show Mean\".";
            return {success: false, message: message};
        }

        // issue 22254: perf issues if we try to show individual lines for a group with a large number of subjects
        var subjectLength = config.subject.values ? config.subject.values.length : 0;
        if (config.displayIndividual && subjectLength > 10000)
        {
            message = "Unable to display individual series lines for greater than 10,000 total " + studyNounPlural.toLowerCase() + ".";
            return {success: false, message: message};
        }

        return {success: true, message: message};
    };

    /**
     * Verifies that the chart data contains the expected interval values and measure/dimension data. Also checks to make
     * sure that data can be used in a log scale (if applicable). Returns an object with a success parameter (boolean)
     * and a message parameter (string). If the success pararameter is false there is a critical error and the chart
     * cannot be rendered. If success is true the chart can be rendered. Message will contain an error or warning
     * message if applicable. If message is not null and success is true, there is a warning.
     * @param {Object} data The data object, from getChartData.
     * @param {Object} config The chart configuration object that defines the selected measures, axis info, subjects/groups, etc.
     * @param {Array} seriesList The list of series that will be plotted for a given chart, from generateSeriesList.
     * @param {int} limit The data limit for a single report.
     * @returns {Object}
     */
    var validateChartData = function(data, config, seriesList, limit) {
        var message = "",
            sep = "",
            msg = "",
            commaSep = "",
            noDataCounter = 0;

        // warn the user if the data limit has been reached
        if ((data.individual && data.individual.rows.length == limit) || (data.aggregate && data.aggregate.rows.length == limit))
        {
            message += sep + "The data limit for plotting has been reached. Consider filtering your data.";
            sep = "<br/>";
        }

        // for date based charts, give error message if there are no calculated interval values
        if (!data.hasIntervalData)
        {
            message += sep + "No calculated interval values (i.e. Days, Months, etc.) for the selected 'Measure Date' and 'Interval Start Date'.";
            sep = "<br/>";
        }

        // check to see if any of the measures don't have data
        Ext4.iterate(data.aggregate ? data.aggregate.hasData : data.individual.hasData, function(key, value) {
            if (!value)
            {
                noDataCounter++;
                msg += commaSep + key;
                commaSep = ", ";
            }
        }, this);
        if (msg.length > 0)
        {
            msg = "No data found for the following measures/dimensions: " + msg;

            // if there is no data for any series, add to explanation
            if (noDataCounter == seriesList.length)
            {
                var isDateBased = config && config.measures[0].time == "date";
                if (isDateBased)
                    msg += ". This may be the result of a missing start date value for the selected subject(s).";
            }

            message += sep + msg;
            sep = "<br/>";
        }

        // check to make sure that data can be used in a log scale (if applicable)
        if (config)
        {
            var leftAxisIndex = getAxisIndex(config.axis, "y-axis", "left");
            var rightAxisIndex = getAxisIndex(config.axis, "y-axis", "right");

            Ext4.each(config.measures, function(md){
                var m = md.measure;

                // check the left y-axis
                if (m.yAxis == "left" && leftAxisIndex > -1 && config.axis[leftAxisIndex].scale == "log"
                        && ((data.individual && data.individual.hasNegativeValues && data.individual.hasNegativeValues[m.name])
                        || (data.aggregate && data.aggregate.hasNegativeValues && data.aggregate.hasNegativeValues[m.name])))
                {
                    config.axis[leftAxisIndex].scale = "linear";
                    message += sep + "Unable to use a log scale on the left y-axis. All y-axis values must be >= 0. Reverting to linear scale on left y-axis.";
                    sep = "<br/>";
                }

                // check the right y-axis
                if (m.yAxis == "right" && rightAxisIndex > -1 && config.axis[rightAxisIndex].scale == "log"
                        && ((data.individual && data.individual.hasNegativeValues[m.name])
                        || (data.aggregate && data.aggregate.hasNegativeValues[m.name])))
                {
                    config.axis[rightAxisIndex].scale = "linear";
                    message += sep + "Unable to use a log scale on the right y-axis. All y-axis values must be >= 0. Reverting to linear scale on right y-axis.";
                    sep = "<br/>";
                }

            });
        }

        return {success: true, message: message};
    };

    return {
        /**
         * Loads all of the required dependencies for a Time Chart.
         * @param {Function} callback The callback to be executed when all of the visualization dependencies have been loaded.
         * @param {Object} scope The scope to be used when executing the callback.
         */
        loadVisDependencies: LABKEY.requiresVisualization,
        generateAcrossChartAxisRanges : generateAcrossChartAxisRanges,
        generateAes : generateAes,
        generateApplyClipRect : generateApplyClipRect,
        generateIntervalKey : generateIntervalKey,
        generateLabels : generateLabels,
        generateLayers : generateLayers,
        generatePlotConfigs : generatePlotConfigs,
        generateScales : generateScales,
        generateSeriesList : generateSeriesList,
        generateTickMap : generateTickMap,
        generateNumberFormats : generateNumberFormats,
        getAxisIndex : getAxisIndex,
        getChartData : getChartData,
        validateChartConfig : validateChartConfig,
        validateChartData : validateChartData
    };
};
