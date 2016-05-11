
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
         * @param menuItem
         * @param dataRegionName
         * @param columnName
         */
        var showMeasureFromDataRegion = function(menuItem, dataRegionName, columnName)
        {
            var region = LABKEY.DataRegions[dataRegionName];
            if (region)
            {
                _queryColumnData(region, columnName, function(data)
                {
                    var regionColumnNames = $.map(region.columns, function(c) { return c.name; }),
                        colIndex = regionColumnNames.indexOf(columnName),
                        plotDivId = _appendPlotDiv(region);

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
                        fontFamily: 'Roboto, arial',
                        margins: {
                            top: 35,
                            bottom: 20,
                            left: 50,
                            right: 50
                        },
                        labels: {
                            main: {
                                value: colIndex > -1 ? region.columns[colIndex].caption : columnName,
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
                                trans: 'linear',
                                domain: [min, null]
                            }
                        }
                    });
                    plot.render();
                });

                menuItem.disable();
            }
        };

        /**
         * Used via DimensionPlotAnalyticsProvider to visualize data for the selected dimension column.
         * @param menuItem
         * @param dataRegionName
         * @param columnName
         * @param chartType - "bar" or "pie"
         */
        var showDimensionFromDataRegion = function(menuItem, dataRegionName, columnName, chartType)
        {
            var region = LABKEY.DataRegions[dataRegionName];
            if (region)
            {
                _queryColumnData(region, columnName, function (data)
                {
                    var regionColumnNames = $.map(region.columns, function(c) { return c.name; }),
                        colIndex = regionColumnNames.indexOf(columnName),
                        mainLabel = colIndex > -1 ? region.columns[colIndex].caption : columnName,
                        plotDivId = _appendPlotDiv(region);

                    var categoryCountMap = {};
                    $.each(data.rows, function (index, row)
                    {
                        if (!categoryCountMap[row[columnName].value])
                            categoryCountMap[row[columnName].value] = 0;
                        categoryCountMap[row[columnName].value]++;
                    });

                    var categoryData = [];
                    for (var category in categoryCountMap)
                    {
                        if (categoryCountMap.hasOwnProperty(category))
                        {
                            categoryData.push({label: category, value: categoryCountMap[category]})
                        }
                    }

                    if (chartType == "bar")
                    {
                        var plot = new LABKEY.vis.BarPlot({
                            renderTo: plotDivId,
                            rendererType: 'd3',
                            width: categoryData.length > 5 ? 600 : 300,
                            height: 200,
                            data: data.rows,
                            fontFamily: 'Roboto, arial',
                            margins: {
                                top: 35,
                                bottom: 20,
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
                            options: {
                                color: '#000000',
                                fill: '#64A1C6'
                            },
                            xAes: function(row){
                                return row[columnName].value
                            }
                        });

                        plot.render();
                    }
                    else if (chartType == "pie")
                    {
                        new LABKEY.vis.PieChart({
                            renderTo: plotDivId,
                            rendererType: 'd3',
                            data: categoryData,
                            width: 300,
                            height: 200,
                            header: {
                                title: {
                                    text: mainLabel,
                                    fontSize: 14,
                                    font: 'Roboto, arial',
                                    color: '#000000'
                                }
                            },
                            labels: {
                                outer: {
                                    pieDistance: 10
                                },
                                inner: {
                                    hideWhenLessThanPercentage: 10
                                },
                                lines: {
                                    style: 'straight',
                                    color: 'black'
                                }
                            },
                            misc: {
                                colors: {
                                    segments: LABKEY.vis.Scale.ColorDiscrete(),
                                    segmentStroke: '#a1a1a1'
                                }
                            },
                            effects: {
                                load: {
                                    effect: 'none'
                                },
                                pullOutSegmentOnClick: {
                                    effect: 'none'
                                }
                            }
                        });
                    }
                });

                menuItem.disable();
            }
        };

        var _queryColumnData = function(dataRegion, columnName, successCallback)
        {
            LABKEY.Query.selectRows({
                schemaName: dataRegion.schemaName,
                queryName: dataRegion.queryName,
                viewName: dataRegion.viewName,
                columns: columnName,
                filterArray: LABKEY.Filter.getFiltersFromUrl(window.location.search, dataRegion.name),
                requiredVersion: '9.1',
                success: successCallback
            });
        };

        var _appendPlotDiv = function(dataRegion)
        {
            var plotAnalyticsCls = 'labkey-dataregion-msg-plot-analytics',
                plotDivId = LABKEY.Utils.id();

            if (!$('.' + plotAnalyticsCls).length)
            {
                dataRegion.addMessage({
                    html: '<div class="' + plotAnalyticsCls + '"><span id="' + plotDivId + '"></span></div>',
                    part: 'plotAnalyticsProvider'
                });
            }
            else
            {
                $('.' + plotAnalyticsCls).append('<span id="' + plotDivId + '"></span>');
            }

            return plotDivId;
        };

        return {
            showMeasureFromDataRegion: showMeasureFromDataRegion,
            showDimensionFromDataRegion: showDimensionFromDataRegion
        };
    };
})(jQuery);
