
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

    return {
        applyAggregateFromDataRegion: applyAggregateFromDataRegion,
        removeColumnFromDataRegion: removeColumnFromDataRegion
    };
};
