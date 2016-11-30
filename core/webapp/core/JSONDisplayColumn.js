
(function ($)
{
    /**
     * @private
     * @namespace API used by the JsonPrettyPrintDisplayColumnFactory to show and hide content for a large JSON blob.
     */
    LABKEY.JSONDisplayColumn = new function ()
    {
        var showMore = function (target)
        {
            var parent = $(target).closest('.json-collapsed');
            if (parent.length == 1)
                parent.addClass('json-expanded').removeClass('json-collapsed')
        };

        var showLess = function (target)
        {
            var parent = $(target).closest('.json-expanded');
            if (parent.length == 1)
                parent.removeClass('json-expanded').addClass('json-collapsed')
        };

        return {
            showMore: showMore,
            showLess: showLess
        };
    };

})(jQuery);