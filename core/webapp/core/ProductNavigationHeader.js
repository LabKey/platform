/*
 * Copyright (c) 2021-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
(function ($)
{
    const HEADER_ID_PREFIX = 'headerProductDropdown';
    const HEADER_CONTENT_ID = HEADER_ID_PREFIX + '-content';
    let scriptRequested = false;
    let scriptLoaded = false;
    let menuOpen = false;

    function showProductMenu(show) {
        LABKEY.App.loadApp('productNavigation', HEADER_CONTENT_ID, { show });
    }

    function onLoad() {
        scriptLoaded = true;
        if (menuOpen) showProductMenu(true);
    }

    function requireProductNav() {
        if (scriptRequested) return;
        scriptRequested = true;
        LABKEY.requiresScript('gen/productNavigation', onLoad);
        // LABKEY.requiresScript('http://localhost:3001/productNavigation.js', onLoad);
    }

    // preload on hover/focus so the menu is usually ready by the time it opens
    $(document).on('mouseenter focusin', '#' + HEADER_ID_PREFIX + ' .dropdown-toggle', requireProductNav);

    $(document).on('show.bs.dropdown', '#' + HEADER_ID_PREFIX, function () {
        menuOpen = true;
        requireProductNav();
        if (scriptLoaded) showProductMenu(true);
    });

    $(document).on('hidden.bs.dropdown', '#' + HEADER_ID_PREFIX, function () {
        menuOpen = false;
        if (scriptLoaded) showProductMenu(false);
    });

    // stop the product navigation menu from closing when click within the menu div
    $(document).on('click', '#' + HEADER_CONTENT_ID, function (e) {
        e.stopPropagation();
    });
})(jQuery);
