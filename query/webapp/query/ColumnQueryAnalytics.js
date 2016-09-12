
/**
 * @private
 * @namespace API used by the various column analytics providers for the Query module.
 */
LABKEY.ColumnQueryAnalytics = new function ()
{
    /**
     * Used via BaseAggregatesAnalyticsProvider to add or remove an aggregate from the selected column in the view.
     * @param dataRegionName
     * @param colFieldKey
     * @param selectedAggregate
     */
    var applyAggregateFromDataRegion = function(dataRegionName, colFieldKey, selectedAggregate)
    {
        var region = LABKEY.DataRegions[dataRegionName];
        if (region)
        {
            var regionViewName = region.viewName || "";
            region.toggleAggregateForCustomView(regionViewName, colFieldKey, selectedAggregate);
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
                    isLookup = column.hasOwnProperty('lookup') && column.lookup.hasOwnProperty('queryName');

                if (isNumeric && !isLookup)
                    window.location = region.chartWizardURL + '&renderType=box_plot&autoColumnYName=' + colFieldKey;
                else
                    window.location = region.chartWizardURL + '&renderType=bar_chart&autoColumnName=' + colFieldKey;
            }
        }
    };

    return {
        applyAggregateFromDataRegion: applyAggregateFromDataRegion,
        removeColumnFromDataRegion: removeColumnFromDataRegion,
        goToChartWizardFromDataRegion: goToChartWizardFromDataRegion
    };
};
