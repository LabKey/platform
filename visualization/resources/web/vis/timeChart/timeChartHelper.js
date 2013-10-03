/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

LABKEY.vis.TimeChartHelper = new function() {

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

    var generateScales = function(config, tickMap, numberFormats) {
        if (config.measures.length == 0)
            throw "There must be at least one specified measure in the chartInfo config!";

        var xMin = null, xMax = null, xTrans = null, xTickFormat;
        var yLeftMin = null, yLeftMax = null, yLeftTrans = null, yLeftTickFormat;
        var yRightMin = null, yRightMax = null, yRightTrans = null, yRightTickFormat;

        for (var i = 0; i < config.axis.length; i++)
        {
            var axis = config.axis[i];
            if (axis.name == "y-axis")
            {
                if (axis.side == "left")
                {
                    yLeftMin = typeof axis.range.min == "number" ? axis.range.min : null;
                    yLeftMax = typeof axis.range.max == "number" ? axis.range.max : null;
                    yLeftTrans = axis.scale ? axis.scale : "linear";
                    yLeftTickFormat = numberFormats.left ? numberFormats.left : null;
                }
                else
                {
                    yRightMin = typeof axis.range.min == "number" ? axis.range.min : null;
                    yRightMax = typeof axis.range.max == "number" ? axis.range.max : null;
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
                return tickMap[value] ? tickMap[value] : "";
            };
        }


        return {
            x: {
                scaleType : 'continuous',
                trans : xTrans,
                min : xMin,
                max : xMax,
                tickFormat : xTickFormat ? xTickFormat : null
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

    var generateIntervalKey = function(config, individualColumnAliases, aggregateColumnAliases) {
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
                LABKEY.vis.getColumnAlias(individualColumnAliases, LABKEY.moduleContext.study.subject.nounSingular + "Visit/Visit") :
                LABKEY.vis.getColumnAlias(aggregateColumnAliases, LABKEY.moduleContext.study.subject.nounSingular + "Visit/Visit");
        }
    };

    var generateTickMap = function(visitMap) {
        var tickMap = {};
        for (var rowId in visitMap)
            tickMap[visitMap[rowId].displayOrder] = visitMap[rowId].displayName;

        return tickMap;
    };

    var generateAes = function(config, visitMap, individualColumnAliases, intervalKey) {
        if (config.measures.length == 0)
            throw "There must be at least one specified measure in the chartInfo config!";

        var xAes;
        if (config.measures[0].time == "date")
            xAes = function(row) { return (row[intervalKey] ? row[intervalKey].value : null); }
        else
            xAes = function(row) { return visitMap[row[intervalKey].value].displayOrder; };

        var individualSubjectColumn = individualColumnAliases ? LABKEY.vis.getColumnAlias(individualColumnAliases, LABKEY.moduleContext.study.subject.columnName) : null;

        return {
            x: xAes,
            color: function(row) { return (row[individualSubjectColumn] ? row[individualSubjectColumn].value : null) },
            group: function(row) { return (row[individualSubjectColumn] ? row[individualSubjectColumn].value : null) },
            shape: function(row) { return (row[individualSubjectColumn] ? row[individualSubjectColumn].value : null) }
        };
    };

    var generateLayers = function(config, visitMap, individualColumnAliases, aggregateColumnAliases, aggregateData, seriesList, intervalKey) {
        if (config.measures.length == 0)
            throw "There must be at least one specified measure in the chartInfo config!";
        if (!individualColumnAliases && !aggregateColumnAliases)
            throw "We expect to either be displaying individual series lines or aggregate data!";

        var layers = [];
        var isDateBased = config.measures[0].time == "date";
        var individualSubjectColumn = individualColumnAliases ? LABKEY.vis.getColumnAlias(individualColumnAliases, LABKEY.moduleContext.study.subject.columnName) : null;
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
                    }
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

    var generatePointClickFn = function(fnString, columnMap, measureInfo){
        // the developer is expected to return a function, so we encapalate it within the anonymous function
        // (note: the function should have already be validated in a try/catch when applied via the developerOptionsPanel)

        // using new Function is quicker than eval(), even in IE.
        var pointClickFn = new Function('return ' + fnString)();
        return function(clickEvent, data) {
            pointClickFn(data, columnMap, measureInfo, clickEvent);
        };
    };

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

    var generateDataSortArray = function(subject, firstMeasure, isDateBased) {
        return [
            subject,
            {
                schemaName : firstMeasure.dateOptions ? firstMeasure.dateOptions.dateCol.schemaName : firstMeasure.measure.schemaName,
                queryName : firstMeasure.dateOptions ? firstMeasure.dateOptions.dateCol.queryName : firstMeasure.measure.queryName,
                name : isDateBased ? firstMeasure.dateOptions.dateCol.name : LABKEY.moduleContext.study.subject.nounSingular + "Visit/Visit/DisplayOrder"
            },
            {
                schemaName : firstMeasure.dateOptions ? firstMeasure.dateOptions.dateCol.schemaName : firstMeasure.measure.schemaName,
                queryName : firstMeasure.dateOptions ? firstMeasure.dateOptions.dateCol.queryName : firstMeasure.measure.queryName,
                name : LABKEY.moduleContext.study.subject.nounSingular + "Visit/Visit"
            }
        ];
    };

    var generateApplyClipRect = function(config) {
        var xAxisIndex = LABKEY.vis.TimeChartHelper.getAxisIndex(config.axis, "x-axis");
        var leftAxisIndex = LABKEY.vis.TimeChartHelper.getAxisIndex(config.axis, "y-axis", "left");
        var rightAxisIndex = LABKEY.vis.TimeChartHelper.getAxisIndex(config.axis, "y-axis", "right");

        return (
            xAxisIndex > -1 && (config.axis[xAxisIndex].range.min != null || config.axis[xAxisIndex].range.max != null) ||
            leftAxisIndex > -1 && (config.axis[leftAxisIndex].range.min != null || config.axis[leftAxisIndex].range.max != null) ||
            rightAxisIndex > -1 && (config.axis[rightAxisIndex].range.min != null || config.axis[rightAxisIndex].range.max != null)
        );
    };

    var generateAcrossChartAxisRanges = function(config, data, seriesList) {
        if (config.measures.length == 0)
            throw "There must be at least one specified measure in the chartInfo config!";
        if (!data.individual && !data.aggregate)
            throw "We expect to either be displaying individual series lines or aggregate data!";

        // In multi-chart case, we need to precompute the default axis ranges so that all charts share them
        // (if 'automatic across charts' is selected for the given axis)
        if (config.chartLayout != "single")
        {
            var leftMeasures = [];
            var rightMeasures = [];
            var xName, xFunc;
            var min, max, tempMin, tempMax, errorBarType;
            var leftAccessor, leftAccessorMax, leftAccessorMin, rightAccessorMax, rightAccessorMin, rightAccessor;
            var columnAliases = data.individual ? data.individual.columnAliases : (data.aggregate ? data.aggregate.columnAliases : null);
            var isDateBased = config.measures[0].time == "date";
            var xAxisIndex = LABKEY.vis.TimeChartHelper.getAxisIndex(config.axis, "x-axis");
            var leftAxisIndex = LABKEY.vis.TimeChartHelper.getAxisIndex(config.axis, "y-axis", "left");
            var rightAxisIndex = LABKEY.vis.TimeChartHelper.getAxisIndex(config.axis, "y-axis", "right");

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
                xName = LABKEY.vis.getColumnAlias(columnAliases, LABKEY.moduleContext.study.subject.nounSingular + "Visit/Visit");
                xFunc = function(row){
                    return visitMap[row[xName].value].displayOrder;
                };
            }

            var rows = data.individual ? data.individual.rows : (data.aggregate ? data.aggregate.rows : []);
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
                min = null, max = null, tempMin = null, tempMax = null;
                leftAccessor = function(row){return (row[leftMeasures[i]] ? row[leftMeasures[i]].value : null);};

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
                min = null, max = null, tempMin = null, tempMax = null;
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

    var generatePlotConfigs = function(config, data, seriesList, applyClipRect, maxCharts) {
        var plotConfigInfoArr = [];

        var subjectColumnName = null;
        if (data.individual)
            subjectColumnName = LABKEY.vis.getColumnAlias(data.individual.columnAliases, LABKEY.moduleContext.study.subject.columnName);

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

    var getAxisIndex = function(axes, axisName, side) {
        var index = -1;
        for(var i = 0; i < axes.length; i++){
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

        var chartData = {numberFormats: {}};
        var counter = config.chartInfo.displayIndividual && config.chartInfo.displayAggregate ? 2 : 1;
        var isDateBased = config.chartInfo.measures[0].time == "date";
        var seriesList = LABKEY.vis.TimeChartHelper.generateSeriesList(config.chartInfo.measures);
        var nounSingular = LABKEY.moduleContext.study.subject.nounSingular;

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
                if (visitsInDataArr.indexOf(v) != -1)
                    trimmedVisits.push(Ext4.apply({id: v}, origVisitMap[v]));
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

        var getNumberFormats = function(fields, defaultNumberFormat) {
            for (var i = 0; i < config.chartInfo.axis.length; i++)
            {
                var axis = config.chartInfo.axis[i];
                if (axis.side)
                {
                    // Find the first measure with the matching side that has a numberFormat.
                    for (var j = 0; j < config.chartInfo.measures.length; j++)
                    {
                        var measure = config.chartInfo.measures[j].measure;

                        if (chartData.numberFormats[axis.side])
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
                                        chartData.numberFormats[axis.side] = eval(field.extFormatFn);
                                        break;
                                    }
                                }
                            }
                        }
                    }

                    if (!chartData.numberFormats[axis.side])
                    {
                        // If after all the searching we still don't have a numberformat use the default number format.
                        chartData.numberFormats[axis.side] = defaultNumberFormat;
                    }
                }
            }
        };

        var successCallback = function(response, dataType) {
            getNumberFormats(response.metaData.fields, config.defaultNumberFormat);

            // make sure each measure/dimension has at least some data, and get a list of which visits are in the data response
            var visitsInData = [];
            response.hasData = {};
            Ext4.each(seriesList, function(s) {
                response.hasData[s.name] = false;
                for (var i = 0; i < response.rows.length; i++)
                {
                    var row = response.rows[i];
                    var alias = LABKEY.vis.getColumnAlias(response.columnAliases, s.aliasLookupInfo);
                    if (row[alias] && row[alias].value != null)
                        response.hasData[s.name] = true;

                    var visitMappedName = LABKEY.vis.getColumnAlias(response.columnAliases, nounSingular + "Visit/Visit");
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

            // if we have all request data back, return the result
            counter--;
            if (counter == 0)
                config.success.call(config.scope, chartData);
        };

        if (config.chartInfo.displayIndividual)
        {
            //Get data for individual lines.
            LABKEY.Query.Visualization.getData({
                success: function(response) {
                    successCallback(response, "individual");
                },
                failure : function(info, response, options) {
                    config.failure.call(config.scope, info);
                },
                measures: config.chartInfo.measures,
                sorts: LABKEY.vis.TimeChartHelper.generateDataSortArray(config.chartInfo.subject, config.chartInfo.measures[0], isDateBased),
                limit : config.dataLimit || 10000,
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
                success: function(response) {
                    successCallback(response, "aggregate");
                },
                failure : function(info) {
                    config.failure.call(config.scope, info);
                },
                measures: config.chartInfo.measures,
                groupBys: [{schemaName: 'study', queryName: 'ParticipantGroupCohortUnion', name: 'UniqueId', values: groups}],
                sorts: LABKEY.vis.TimeChartHelper.generateDataSortArray(config.chartInfo.subject, config.chartInfo.measures[0], isDateBased),
                limit : config.dataLimit || 10000,
                filterUrl: config.chartInfo.filterUrl,
                filterQuery: config.chartInfo.filterQuery
            });
        }
    };

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
            message = "No " + LABKEY.moduleContext.study.subject.nounSingular.toLowerCase() + " selected. " +
                    "Please select at least one " + LABKEY.moduleContext.study.subject.nounSingular.toLowerCase() + ".";
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

        return {success: true, message: message};
    };

    var validateChartData = function(data, seriesList, limit) {
        var message = "";
        var sep = "";

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
        var msg = ""; var commaSep = "";
        var noDataCounter = 0;
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

            // if there is no data for any series, error out completely
            if (noDataCounter == seriesList.length)
            {
                return {success: false, message: msg};
            }
            else
            {
                message += sep + msg;
                sep = "<br/>";
            }
        }

        return {success: true, message: message};
    };

    return {
        generateLabels : generateLabels,
        generateScales : generateScales,
        generateIntervalKey : generateIntervalKey,
        generateTickMap : generateTickMap,
        generateAes : generateAes,
        generateLayers : generateLayers,
        generatePointClickFn : generatePointClickFn,
        generateSeriesList : generateSeriesList,
        generateDataSortArray : generateDataSortArray,
        generateApplyClipRect : generateApplyClipRect,
        generateAcrossChartAxisRanges : generateAcrossChartAxisRanges,
        generatePlotConfigs : generatePlotConfigs,
        getDataWithSeriesCheck : getDataWithSeriesCheck,
        getAxisIndex : getAxisIndex,
        getChartData : getChartData,
        validateChartConfig : validateChartConfig,
        validateChartData : validateChartData
    };
};