
(function ($)
{
    /**
     * @private
     * @namespace API used by the ExpandableTextDisplayColumnFactory to show and hide content for a large text blob.
     */
    LABKEY.ExpandableTextDisplayColumn = new function ()
    {
        var COLLAPSED_CLS = 'expandable-text-collapsed';
        var EXPANDED_CLS = 'expandable-text-expanded';

        var showMore = function (target)
        {
            var parent = $(target).closest('.' + COLLAPSED_CLS);
            if (parent.length == 1)
                parent.addClass(EXPANDED_CLS).removeClass(COLLAPSED_CLS)
        };

        var showLess = function (target)
        {
            var parent = $(target).closest('.' + EXPANDED_CLS);
            if (parent.length == 1)
                parent.removeClass(EXPANDED_CLS).addClass(COLLAPSED_CLS)
        };

        return {
            showMore: showMore,
            showLess: showLess
        };
    };

})(jQuery);