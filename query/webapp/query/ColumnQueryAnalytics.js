
/**
 * @private
 * @namespace API used by the various column analytics providers for the Query module.
 */
LABKEY.ColumnQueryAnalytics = new function ()
{
    /**
     * Used via BaseAggregatesAnalyticsProvider to add or remove an aggregate from the selected column in the view.
     * @param dataRegionName
     * @param columnName
     * @param selectedAggregate
     */
    var applyAggregateFromDataRegion = function(dataRegionName, columnName, selectedAggregate)
    {
        var region = LABKEY.DataRegions[dataRegionName];
        if (region)
        {
            var regionViewName = region.viewName || "";
            region.toggleAggregateForCustomView(regionViewName, columnName, selectedAggregate);
        }
    };

    /**
     * Used via RemoveColumnAnalyticsProvider to remove the selected column in the view.
     * @param dataRegionName
     * @param columnName
     */
    var removeColumnFromDataRegion = function(dataRegionName, columnName)
    {
        var region = LABKEY.DataRegions[dataRegionName];
        if (region)
        {
            var regionViewName = region.viewName || "";
            region.removeColumn(regionViewName, columnName);
        }
    };

    return {
        applyAggregateFromDataRegion: applyAggregateFromDataRegion,
        removeColumnFromDataRegion: removeColumnFromDataRegion
    };
};
