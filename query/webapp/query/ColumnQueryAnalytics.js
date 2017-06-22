/*
 * Copyright (c) 2016-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

/**
 * @private
 * @namespace API used by the various column analytics providers for the Query module.
 */
LABKEY.ColumnQueryAnalytics = new function ()
{
    /**
     * Used via BaseAggregatesAnalyticsProvider to add or remove a summary stat from the selected column in the view.
     * @param dataRegionName
     * @param colFieldKey
     * @param selectedSummaryStat
     */
    var applySummaryStatFromDataRegion = function(dataRegionName, colFieldKey, selectedSummaryStat)
    {
        var region = LABKEY.DataRegions[dataRegionName];
        if (region)
        {
            var regionViewName = region.viewName || "";
            region.toggleSummaryStatForCustomView(regionViewName, colFieldKey, selectedSummaryStat);
        }
    };

    /**
     * Used via RemoveColumnAnalyticsProvider to remove the selected column in the view.
     * @param dataRegionName
     * @param colFieldKey
     */
    var removeColumnFromDataRegion = function(dataRegionName, colFieldKey)
    {
        var region = LABKEY.DataRegions[dataRegionName];
        if (region)
        {
            var regionViewName = region.viewName || "";
            region.removeColumn(regionViewName, colFieldKey);
        }
    };

    /**
     * Used to generate and navigate to the URL for the generic chart wizard for the given column field key (i.e. Quick Chart).
     * @param dataRegionName
     * @param colFieldKey
     */
    var goToChartWizardFromDataRegion = function(dataRegionName, colFieldKey)
    {
        var region = LABKEY.DataRegions[dataRegionName];
        if (region)
        {
            var column = region.getColumn(colFieldKey);
            if (column != null && region.chartWizardURL != undefined)
            {
                var isNumeric = column.jsonType == 'int' || column.jsonType == 'float',
                    isLookup = column.hasOwnProperty('lookup') && column.lookup.hasOwnProperty('queryName'),
                    parameters = {};

                if (isNumeric && !isLookup)
                {
                    parameters['renderType'] = 'box_plot';
                    parameters['autoColumnYName'] = colFieldKey;
                }
                else
                {
                    parameters['renderType'] = 'bar_chart';
                    parameters['autoColumnName'] = colFieldKey;
                }

                window.location = region.chartWizardURL + '&' + LABKEY.ActionURL.queryString(parameters);
            }
        }
    };

    return {
        applySummaryStatFromDataRegion: applySummaryStatFromDataRegion,
        removeColumnFromDataRegion: removeColumnFromDataRegion,
        goToChartWizardFromDataRegion: goToChartWizardFromDataRegion
    };
};
