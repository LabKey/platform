
(function ($)
{
    /**
     * @private
     * @namespace API used by the various column analytics providers for the Query module.
     */
    LABKEY.ColumnAnalytics = new function ()
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
                region.getQueryDetails(function(queryDetails)
                {
                    var view = _getViewFromQueryDetails(queryDetails, regionViewName);
                    if (view != null)
                    {
                        var colFieldKey = LABKEY.FieldKey.fromString(columnName),
                            fieldKeyAggregates = [],
                            hasSelected = false;

                        // if the selected aggregate already exists in the view, this is a "remove"
                        $.each(view.aggregates, function(index, existingAgg)
                        {
                            if (existingAgg.fieldKey == colFieldKey)
                            {
                                if (existingAgg.type == selectedAggregate)
                                    hasSelected = true;
                                else
                                    fieldKeyAggregates.push(existingAgg.type);
                            }
                        });
                        if (!hasSelected)
                            fieldKeyAggregates.push(selectedAggregate);

                        _applySelectedAggregate(
                            queryDetails.schemaName,
                            queryDetails.name,
                            view,
                            colFieldKey,
                            fieldKeyAggregates
                        );
                    }
                });
            }
        };

        /**
         * Used via HideColumnAnalyticsProvider to remove the selected column in the view.
         * @param dataRegionName
         * @param columnName
         */
        var hideColumnFromDataRegion = function(dataRegionName, columnName)
        {
            var region = LABKEY.DataRegions[dataRegionName];
            if (region)
            {
                var regionViewName = region.viewName || "";
                region.getQueryDetails(function(queryDetails)
                {
                    var view = _getViewFromQueryDetails(queryDetails, regionViewName);
                    if (view != null)
                    {
                        _hideSelectedColumn(
                            queryDetails.schemaName,
                            queryDetails.name,
                            view,
                            LABKEY.FieldKey.fromString(columnName)
                        );
                    }
                });
            }
        };

        var _getViewFromQueryDetails = function(queryDetails, viewName)
        {
            var matchingView = null;

            $.each(queryDetails.views, function(index, view)
            {
                if (view.name == viewName)
                {
                    matchingView = view;
                    return false;
                }
            });

            return matchingView;
        };

        var _applySelectedAggregate = function(schemaName, queryName, customView, fieldKey, newAggregates)
        {
            // first, keep any existing custom view aggregates that don't match this fieldKey
            var aggregates = [];
            $.each(customView.aggregates, function(index, existingAgg)
            {
                if (existingAgg.fieldKey != fieldKey)
                    aggregates.push(existingAgg);
            });

            // then add on the aggregates for the fieldKey selected
            $.each(newAggregates, function(index, newAggType)
            {
                aggregates.push({fieldKey: fieldKey, type: newAggType});
            });

            LABKEY.Query.saveQueryViews({
                containerPath: LABKEY.container.path,
                schemaName: schemaName,
                queryName: queryName,
                views: [{
                    name: customView.name,
                    hidden: customView.hidden,
                    columns: customView.columns,
                    filter: customView.filter,
                    sort: customView.sort,
                    aggregates: aggregates,
                    shared: false,
                    inherit: false,
                    session: true
                }],
                scope: this,
                success: function(savedViewsInfo) {
                    window.location.reload();
                }
            });
        };

        var _hideSelectedColumn = function(schemaName, queryName, customView, fieldKey)
        {
            var colFieldKeys = $.map(customView.columns, function(c) { return c.fieldKey; }),
                fieldKeyIndex = colFieldKeys.indexOf(fieldKey.toString());

            if (fieldKeyIndex > -1)
            {
                customView.columns.splice(fieldKeyIndex, 1);

                LABKEY.Query.saveQueryViews({
                    containerPath: LABKEY.container.path,
                    schemaName: schemaName,
                    queryName: queryName,
                    views: [{
                        name: customView.name,
                        hidden: customView.hidden,
                        columns: customView.columns,
                        filter: customView.filter,
                        sort: customView.sort,
                        aggregates: customView.aggregates,
                        shared: false,
                        inherit: false,
                        session: true
                    }],
                    scope: this,
                    success: function(savedViewsInfo) {
                        window.location.reload();
                    }
                });
            }
        };

        return {
            applyAggregateFromDataRegion: applyAggregateFromDataRegion,
            hideColumnFromDataRegion: hideColumnFromDataRegion
        };
    };
})(jQuery);
