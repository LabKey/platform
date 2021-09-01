(function ($)
{
    var HEADER_ID_PREFIX = 'headerProductDropdown';
    var HEADER_CONTENT_ID = HEADER_ID_PREFIX + '-content';

    // wait to load the product navigation dependencies until the hover over the header icon
    var productNavLoaded = false;
    $(document).on('mouseenter', '#' + HEADER_ID_PREFIX + ' .dropdown-toggle', function(e) {
        if (!productNavLoaded) {
            $("#" + HEADER_CONTENT_ID).html("<div style=\"padding: 10px;\"><i class=\"fa fa-spinner fa-pulse\"></i> Loading...</div>");

            LABKEY.requiresScript('gen/productNavigation', loadProductNav);
            // LABKEY.requiresScript('http://localhost:3001/productNavigation.js', loadProductNav);
        } else {
            loadProductNav();
        }
    });

    var loadProductNav = function() {
        LABKEY.App.loadApp('productNavigation', HEADER_CONTENT_ID, { show: true });
        $(document).on('click', addProductNavClickHandler);
        productNavLoaded = true;
    };

    // stop the product navigation menu from closing when click within the menu div
    $(document).on('click', '#' + HEADER_CONTENT_ID, function (e) {
        e.stopPropagation();
    });

    // on click outside of the open menu, remove click handler and hide menu (which will force it to reset on next open)
    var addProductNavClickHandler = function (e) {
        if ($(e.target).closest(HEADER_CONTENT_ID).length === 0) {
            LABKEY.App.loadApp('productNavigation', HEADER_CONTENT_ID, { show: false });
            $(document).off('click', addProductNavClickHandler);
        }
    };
})(jQuery);
