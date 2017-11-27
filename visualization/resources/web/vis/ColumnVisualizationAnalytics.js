/*
 * Copyright (c) 2016-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
(function ($)
{
    /**
     * @private
     * @namespace API used by the various column analytics providers for the Visualization module.
     */
    LABKEY.ColumnVisualizationAnalytics = new function ()
    {
        /**
         * Used via MeasurePlotAnalyticsProvider to visualize data for the selected measure column.
         * @param dataRegionName
         * @param columnName
         * @param colFieldKey
         * @param analyticsProviderName - VIS_BOX
         */
        var showMeasureFromDataRegion = function(dataRegionName, columnName, colFieldKey, analyticsProviderName)
        {
            var region = LABKEY.DataRegions[dataRegionName];
            if (region)
            {
                var plotDivId = _appendPlotDiv(region);

                _queryColumnData(region, colFieldKey, function(data)
                {
                    var regionViewName = region.viewName || "",
                        regionColumnNames = $.map(region.columns, function(c) { return c.name; }),
                        dataColumnNames = $.map(data.columnModel, function(col) { return col.dataIndex; }),
                        colIndex = regionColumnNames.indexOf(columnName);

                    if (dataColumnNames.indexOf(columnName) === -1)
                    {
                        console.warn('Could not find column "' + columnName + '" in "' + region.schemaName + '.' + region.queryName + '".');
                        return;
                    }

                    var plot = getColumnBoxPlot(plotDivId, data.rows, columnName, region.columns[colIndex], true);
                    plot.render();

                    _handleAnalyticsProvidersForCustomView(region, plotDivId, regionViewName, colFieldKey, analyticsProviderName);
                });
            }
            else
            {
                console.warn('Could not find data region "' + dataRegionName + '" for LABKEY.ColumnVisualizationAnalytics.showMeasureFromDataRegion() call.');
            }
        };

        /**
         * Used via DimensionPlotAnalyticsProvider to visualize data for the selected dimension column.
         * @param dataRegionName
         * @param columnName
         * @param colFieldKey
         * @param analyticsProviderName - VIS_BAR or VIS_PIE
         */
        var showDimensionFromDataRegion = function(dataRegionName, columnName, colFieldKey, analyticsProviderName)
        {
            var region = LABKEY.DataRegions[dataRegionName];
            if (region)
            {
                var plotDivId = _appendPlotDiv(region);

                _queryColumnData(region, colFieldKey, function (data)
                {
                    var regionViewName = region.viewName || "",
                        regionColumnNames = $.map(region.columns, function(c) { return c.name; }),
                        dataColumnNames = $.map(data.columnModel, function(col) { return col.dataIndex; }),
                        colIndex = regionColumnNames.indexOf(columnName),
                        plot;

                    if (dataColumnNames.indexOf(columnName) === -1)
                    {
                        console.warn('Could not find column "' + columnName + '" in "' + region.schemaName + '.' + region.queryName + '".');
                        return;
                    }

                    if ($('#' + plotDivId).length === 0)
                        return;

                    if (analyticsProviderName === 'VIS_BAR')
                    {
                        plot = getColumnBarPlot(plotDivId, data.rows, columnName, region.columns[colIndex], true);
                        plot.render();
                    }
                    else if (analyticsProviderName === 'VIS_PIE')
                    {
                        plot = getColumnPieChart(plotDivId, data.rows, columnName, region.columns[colIndex], true);
                    }

                    if (plot)
                    {
                        _handleAnalyticsProvidersForCustomView(region, plotDivId, regionViewName, colFieldKey, analyticsProviderName);
                    }
                });
            }
            else
            {
                console.warn('Could not find data region "' + dataRegionName + '" for LABKEY.ColumnVisualizationAnalytics.showDimensionFromDataRegion() call.');
            }
        };

        var getColumnBoxPlot = function(renderTo, dataArray, columnName, fieldMetadata, showMainLabel)
        {
            var mainLabel = fieldMetadata ? fieldMetadata.caption : columnName,
                scale = fieldMetadata ? fieldMetadata.defaultScale : 'LINEAR',
                labels = {};

            if (showMainLabel)
            {
                labels.main = {
                    value: mainLabel,
                    position: 20,
                    fontSize: 14
                };
            }

            var min = null, max = null;
            $.each(dataArray, function(index, row)
            {
                if (row[columnName].value != null)
                {
                    if (min == null || row[columnName].value < min)
                        min = row[columnName].value;
                    if (max == null || row[columnName].value > max)
                        max = row[columnName].value;
                }
            });
            if (min != null && max != null)
                min = min - ((max - min) * 0.02);

            if (dataArray.length === 0)
            {
                labels.x = {
                    value: 'No data to display',
                    fontSize: 10,
                    color: '#555555'
                };
            }

            return new LABKEY.vis.Plot({
                renderTo: renderTo,
                rendererType: 'd3',
                width: 300,
                height: 200,
                data: dataArray,
                margins: {
                    top: 35,
                    bottom: 15 + (dataArray.length == 0 ? 20 : 0),
                    left: 50,
                    right: 50
                },
                labels: labels,
                layers: [
                    new LABKEY.vis.Layer({
                        geom: new LABKEY.vis.Geom.Boxplot({
                            color: '#000000',
                            fill: '#DDDDDD',
                            showOutliers: false
                        })
                    })
                ],
                aes: {
                    yLeft: function(row){
                        return row[columnName].value;
                    },
                    x: function(row) {
                        return '';
                    }
                },
                scales: {
                    x: {
                        scaleType: 'discrete'
                    },
                    yLeft: {
                        scaleType: 'continuous',
                        trans: scale.toLowerCase(),
                        domain: dataArray.length > 0 ? [min, null] : [0,1],
                        tickDigits: 6
                    }
                }
            });
        };

        var getColumnBarPlot = function(renderTo, dataArray, columnName, fieldMetadata, showMainLabel, categoryLimit)
        {
            var mainLabel = fieldMetadata ? fieldMetadata.caption : columnName,
                categoryData = _getCategoryData(dataArray, columnName),
                validDataSize = _isValidCategoryDataSize(categoryData, categoryLimit),
                categoryShowLabel = {},
                labels = {};

            if (showMainLabel)
            {
                labels.main = {
                    value: mainLabel,
                    position: 20,
                    fontSize: 14
                };
            }

            categoryData.sort(function(a,b){
                return LABKEY.vis.discreteSortFn(a.label, b.label);
            });

            // if we have a long list of categories, only show a total of 15 x-axis tick labels
            if (categoryData.length > 15)
            {
                var m = Math.floor(categoryData.length / 15);
                for (var i = 0; i < categoryData.length; i++)
                    categoryShowLabel[categoryData[i].label] = i % m == 0;
            }

            if (!validDataSize)
            {
                labels.x = {
                    value: categoryData.length == 0 ? 'No data to display' : 'Too many categories to display',
                    fontSize: 10,
                    color: '#555555'
                };
            }

            return new LABKEY.vis.BarPlot({
                renderTo: renderTo,
                rendererType: 'd3',
                width: validDataSize && categoryData.length > 5 ? 605 : 300,
                height: 200,
                data: validDataSize ? dataArray : [],
                margins: {
                    top: 35,
                    bottom: 35,
                    left: 50,
                    right: 50
                },
                labels: labels,
                scales: {
                    x: {
                        scaleType: 'discrete',
                        sortFn: LABKEY.vis.discreteSortFn,
                        tickFormat: function(v) {
                            var val = categoryShowLabel[v] == undefined || categoryShowLabel[v] ? v : '';
                            return _truncateLabel(val, 7);
                        },
                        tickHoverText: function(value) {
                            return value;
                        }
                    },
                    yLeft: {
                        domain: [0,(validDataSize ? null : 1)]
                    }
                },
                options: {
                    color: '#000000',
                    fill: '#64A1C6'
                },
                xAes: function(row) {
                    var val = row[columnName] ? row[columnName].displayValue || row[columnName].value : '';

                    // Issue 27151: convert null to "[Blank]"
                    return val == null ? '[Blank]' : val;
                }
            });
        };

        var getColumnPieChart = function(renderTo, dataArray, columnName, fieldMetadata, showMainLabel, categoryLimit)
        {
            var mainLabel = fieldMetadata ? fieldMetadata.caption : columnName,
                categoryData = _getCategoryData(dataArray, columnName),
                validDataSize = _isValidCategoryDataSize(categoryData, categoryLimit),
                hideLabels = categoryData.length > 20,
                chartData = categoryData,
                header = {},
                footerTxt;

            if (showMainLabel)
            {
                header.title = {
                    text: mainLabel,
                    fontSize: 14
                };
            }

            if (!validDataSize)
            {
                chartData = [{label: '', value: 1}];
                if (categoryData.length === 0)
                    footerTxt = 'No data to display';
                else
                    footerTxt = 'Too many categories to display';
            }

            return new LABKEY.vis.PieChart({
                renderTo: renderTo,
                rendererType: 'd3',
                data: chartData,
                width: 300,
                height: 200,
                header: header,
                footer: {
                    text: footerTxt,
                    location: 'bottom-center',
                    fontSize: 10,
                    color: '#555555'
                },
                labels: {
                    outer: {
                        pieDistance: hideLabels ? 0 : 10,
                        hideWhenLessThanPercentage: hideLabels ? 100 : undefined
                    },
                    inner: {
                        format: validDataSize ? 'percentage' : 'none'
                    },
                    lines: {
                        enabled: !hideLabels
                    },
                    truncation: {
                        enabled: true,
                        length: 10
                    }
                },
                size: {
                    pieInnerRadius: validDataSize ? '0%' : '100%',
                    pieOuterRadius: validDataSize ? '76%' : '90%'
                },
                effects: {
                    highlightSegmentOnMouseover: false,
                    load: {
                        effect: 'none'
                    }
                },
                tooltips: {
                    enabled: true
                }
            });
        };

        var _getCategoryData = function(dataArray, columnName)
        {
            var categoryCountMap = {}, categoryData = [];

            $.each(dataArray, function (index, row)
            {
                var val = row[columnName].displayValue || row[columnName].value;

                // Issue 27151: convert null to "[Blank]"
                if (val == null)
                    val = '[Blank]';

                if (!categoryCountMap[val])
                    categoryCountMap[val] = 0;

                categoryCountMap[val]++;
            });

            for (var category in categoryCountMap)
            {
                if (categoryCountMap.hasOwnProperty(category))
                {
                    categoryData.push({
                        label: category,
                        value: categoryCountMap[category]
                    });
                }
            }

            return categoryData;
        };

        var _isValidCategoryDataSize = function(categoryData, categoryLimit)
        {
            if (categoryLimit == undefined || categoryLimit == null)
                categoryLimit = 100;
            return categoryData.length > 0 && categoryData.length <= categoryLimit;
        };

        var _handleAnalyticsProvidersForCustomView = function(dataRegion, plotDivId, viewName, colFieldKey, analyticsProviderName)
        {
            // add the provider to the custom view, if it isn't already included
            dataRegion.addAnalyticsProviderForCustomView(viewName, colFieldKey, analyticsProviderName);

            // add click handler to go to the chart wizard with the render type and column selected
            var chartWizardUrl = _getGenericChartWizardUrl(dataRegion, colFieldKey, analyticsProviderName);
            if (chartWizardUrl != null)
                $('#' + plotDivId).on('click', function() { window.location = chartWizardUrl; });

            // add the remove icon and register click handler for removing the visualization provider
            $('#' + plotDivId).append('<div class="fa fa-times plot-analytics-remove"></div>');
            $('#' + plotDivId + ' div.plot-analytics-remove').on('click', function() {
                $('#' + plotDivId).remove();
                dataRegion.removeAnalyticsProviderForCustomView(viewName, colFieldKey, analyticsProviderName);
            });
        };

        var _queryColumnData = function(dataRegion, colFieldKey, successCallback)
        {
            // Issue 26594: get the base filter for the QueryView and any user applied URL filters
            // using the data region's selectAllURL. See QueryView.java getSettings().getBaseFilter().applyToURL().
            var config = $.extend({}, dataRegion.getQueryConfig(), {
                columns: colFieldKey,
                ignoreFilter: LABKEY.ActionURL.getParameter(dataRegion.name + '.ignoreFilter'),
                filterArray: LABKEY.Filter.getFiltersFromUrl(dataRegion.selectAllURL, 'query'),
                requiredVersion: '9.1',
                maxRows: -1, // ALL
                success: successCallback
            });

            LABKEY.Query.selectRows(config);
        };

        var _appendPlotDiv = function(region)
        {
            var plotDivId = LABKEY.Utils.id();

            region.displaySection({
                append: true,
                html: '<div id="' + plotDivId + '" class="labkey-dataregion-msg-plot-analytic"></div>'
            });

            return plotDivId;
        };

        var _truncateLabel = function(value, length)
        {
            return value != null && value.length > length ? value.substring(0, length) + '...' : value;
        };

        var _getGenericChartWizardUrl = function(dataRegion, colFieldKey, analyticsProviderName)
        {
            var renderType = _getRenderTypeForAnalyticsProviderName(analyticsProviderName);
            if (renderType)
            {
                var params = {
                    renderType: renderType,
                    autoColumnName: colFieldKey
                };

                return dataRegion.chartWizardURL + '&' + LABKEY.ActionURL.queryString(params);
            }

            return null;
        };

        var _getRenderTypeForAnalyticsProviderName = function(name)
        {
            if (name === 'VIS_BOX')
                return 'box_plot';
            if (name === 'VIS_BAR')
                return 'bar_chart';
            if (name === 'VIS_PIE')
                return 'pie_chart';
            return null;
        };

        return {
            showMeasureFromDataRegion: showMeasureFromDataRegion,
            showDimensionFromDataRegion: showDimensionFromDataRegion,
            getColumnBoxPlot: getColumnBoxPlot,
            getColumnBarPlot: getColumnBarPlot,
            getColumnPieChart: getColumnPieChart
        };
    };
})(jQuery);
