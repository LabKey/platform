/*
 * Copyright (c) 2021-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
(function ($)
{
    var HEADER_ID_PREFIX = 'headerProductDropdown';
    var HEADER_CONTENT_ID = HEADER_ID_PREFIX + '-content';

    // wait to load the product navigation dependencies until the hover over the header icon
    var productNavLoaded = false;
    $(document).on('mouseenter', '#' + HEADER_ID_PREFIX + ' .dropdown-toggle', function(e) {
        if (!productNavLoaded) {
            LABKEY.requiresScript('gen/productNavigation', loadProductNav);
            // LABKEY.requiresScript('http://localhost:3001/productNavigation.js', loadProductNav);
        }
    });

    var loadProductNav = function() {
        LABKEY.App.loadApp('productNavigation', HEADER_CONTENT_ID, { show: true });
        productNavLoaded = true;
    };

    // stop the product navigation menu from closing when click within the menu div
    $(document).on('click', '#' + HEADER_CONTENT_ID, function (e) {
        e.stopPropagation();
    });
})(jQuery);
