
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
                        mainLabel = columnName,
                        scale = 'LINEAR',
                        plotDivId = _appendPlotDiv(region);

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
                        var val = row[columnName].displayValue || row[columnName].value;

                        if (!categoryCountMap[val])
                            categoryCountMap[val] = 0;

                        categoryCountMap[val]++;
                    });

                    var categoryData = [], hasData = false;
                    for (var category in categoryCountMap)
                    {
                        if (categoryCountMap.hasOwnProperty(category))
                        {
                            categoryData.push({
                                label: _truncateLabel(category, 10),
                                value: categoryCountMap[category]
                            });

                            hasData = true;
                        }
                    }

                    if (chartType == "bar")
                    {
                        var plot = new LABKEY.vis.BarPlot({
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
                                x: {scaleType: 'discrete'},
                                yLeft: {domain: [0,(hasData ? null : 1)]}
                            },
                            options: {
                                color: '#000000',
                                fill: '#64A1C6'
                            },
                            xAes: function(row){
                                var val = row[columnName] ? row[columnName].displayValue || row[columnName].value : '';
                                return _truncateLabel(val, 7);
                            }
                        });

                        plot.render();
                    }
                    else if (chartType == "pie")
                    {
                        new LABKEY.vis.PieChart({
                            renderTo: plotDivId,
                            rendererType: 'd3',
                            data: hasData ? categoryData : [{label: '', value: 1}],
                            width: 300,
                            height: 200,
                            header: {
                                title: {
                                    text: mainLabel,
                                    fontSize: 14,
                                    color: '#000000'
                                }
                            },
                            footer: {
                                text: hasData ? undefined : 'No data to display',
                                location: 'bottom-center',
                                fontSize: 10
                            },
                            labels: {
                                outer: {
                                    pieDistance: 10
                                },
                                inner: {
                                    format: hasData ? 'percentage' : 'none',
                                    hideWhenLessThanPercentage: 10
                                },
                                lines: {
                                    style: 'straight',
                                    color: 'black'
                                }
                            },
                            size: {
                                pieInnerRadius: hasData ? '0%' : '100%',
                                pieOuterRadius: hasData ? '76%' : '100%'
                            },
                            misc: {
                                colors: {
                                    segments: LABKEY.vis.Scale.ColorDiscrete(),
                                    segmentStroke: '#222222'
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
            var plotDivId = LABKEY.Utils.id();

            dataRegion.addMessage({
                html: '<span id="' + plotDivId + '" class="labkey-dataregion-msg-plot-analytic"></span>',
                part: 'plotAnalyticsProvider',
                append: true
            });

            return plotDivId;
        };

        var _truncateLabel = function(value, length)
        {
            return value != null && value.length > length ? value.substring(0, length) + '...' : value;
        };

        return {
            showMeasureFromDataRegion: showMeasureFromDataRegion,
            showDimensionFromDataRegion: showDimensionFromDataRegion
        };
    };
})(jQuery);
