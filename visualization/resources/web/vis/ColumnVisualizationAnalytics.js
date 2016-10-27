
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
                        colIndex = regionColumnNames.indexOf(columnName),
                        mainLabel = columnName,
                        scale = 'LINEAR';

                    if (dataColumnNames.indexOf(columnName) == -1)
                    {
                        console.warn('Could not find column "' + columnName + '" in "' + region.schemaName + '.' + region.queryName + '".');
                        return;
                    }

                    if (colIndex > -1)
                    {
                        mainLabel = region.columns[colIndex].caption;
                        scale = region.columns[colIndex].defaultScale;
                    }

                    var min = null, max = null;
                    $.each(data.rows, function(index, row)
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

                    var plot = new LABKEY.vis.Plot({
                        renderTo: plotDivId,
                        rendererType: 'd3',
                        width: 300,
                        height: 200,
                        data: data.rows,
                        margins: {
                            top: 35,
                            bottom: 15,
                            left: 50,
                            right: 50
                        },
                        labels: {
                            main: {
                                value: mainLabel,
                                position: 20,
                                fontSize: 14
                            }
                        },
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
                                domain: data.rows.length > 0 ? [min, null] : [0,1],
                                tickDigits: 6
                            }
                        }
                    });
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
                        mainLabel = colIndex > -1 ? region.columns[colIndex].caption : columnName
                        plot = null;

                    if (dataColumnNames.indexOf(columnName) == -1)
                    {
                        console.warn('Could not find column "' + columnName + '" in "' + region.schemaName + '.' + region.queryName + '".');
                        return;
                    }

                    var categoryCountMap = {};
                    $.each(data.rows, function (index, row)
                    {
                        var val = row[columnName].displayValue || row[columnName].value;

                        // Issue 27151: convert null to "[Blank]"
                        if (val == null)
                            val = '[Blank]';

                        if (!categoryCountMap[val])
                            categoryCountMap[val] = 0;

                        categoryCountMap[val]++;
                    });

                    var categoryData = [],
                        categoryShowLabel = {},
                        hasData = false;

                    for (var category in categoryCountMap)
                    {
                        if (categoryCountMap.hasOwnProperty(category))
                        {
                            categoryData.push({
                                label: category,
                                value: categoryCountMap[category]
                            });

                            hasData = true;
                        }
                    }

                    // if we have a long list of categories, only show a total of 15 x-axis tick labels
                    if (categoryData.length > 15)
                    {
                        var m = Math.floor(categoryData.length / 15);
                        for (var i = 0; i < categoryData.length; i++)
                            categoryShowLabel[categoryData[i].label] = i % m == 0;
                    }

                    if ($('#' + plotDivId).length == 0)
                        return;

                    if (analyticsProviderName == 'VIS_BAR')
                    {
                        plot = new LABKEY.vis.BarPlot({
                            renderTo: plotDivId,
                            rendererType: 'd3',
                            width: categoryData.length > 5 ? 605 : 300,
                            height: 200,
                            data: hasData ? data.rows : [],
                            margins: {
                                top: 35,
                                bottom: 15 + (hasData ? 20 : 0),
                                left: 50,
                                right: 50
                            },
                            labels: {
                                main: {
                                    value: mainLabel,
                                    position: 20,
                                    fontSize: 14
                                }
                            },
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
                                    domain: [0,(hasData ? null : 1)]
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

                        plot.render();
                    }
                    else if (analyticsProviderName == 'VIS_PIE')
                    {
                        var hideLabels = categoryData.length > 20;

                        plot = new LABKEY.vis.PieChart({
                            renderTo: plotDivId,
                            rendererType: 'd3',
                            data: hasData ? categoryData : [{label: '', value: 1}],
                            width: 300,
                            height: 200,
                            header: {
                                title: {
                                    text: mainLabel,
                                    fontSize: 14
                                }
                            },
                            footer: {
                                text: hasData ? undefined : 'No data to display',
                                location: 'bottom-center',
                                fontSize: 10
                            },
                            labels: {
                                outer: {
                                    pieDistance: hideLabels ? 0 : 10,
                                    hideWhenLessThanPercentage: hideLabels ? 100 : undefined
                                },
                                inner: {
                                    format: hasData ? 'percentage' : 'none'
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
                                pieInnerRadius: hasData ? '0%' : '100%',
                                pieOuterRadius: hasData ? '76%' : '100%'
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
                    }

                    if (plot != null)
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
            var filterArray = LABKEY.Filter.getFiltersFromUrl(dataRegion.selectAllURL, 'query');

            var config = $.extend({}, dataRegion.getQueryConfig(), {
                columns: colFieldKey,
                ignoreFilter: LABKEY.ActionURL.getParameter(dataRegion.name + '.ignoreFilter'),
                filterArray: filterArray,
                requiredVersion: '9.1',
                success: successCallback
            });

            LABKEY.Query.selectRows(config);
        };

        var _appendPlotDiv = function(dataRegion)
        {
            var plotDivId = LABKEY.Utils.id();

            dataRegion.addMessage({
                html: '<div id="' + plotDivId + '" class="labkey-dataregion-msg-plot-analytic"></div>',
                part: 'plotAnalyticsProvider',
                append: true
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
            if (renderType != null)
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
            if (name == 'VIS_BOX')
                return 'box_plot';
            if (name == 'VIS_BAR')
                return 'bar_chart';
            if (name == 'VIS_PIE')
                return 'pie_chart';
            return null;
        };

        return {
            showMeasureFromDataRegion: showMeasureFromDataRegion,
            showDimensionFromDataRegion: showDimensionFromDataRegion
        };
    };
})(jQuery);
