/*
 * Copyright (c) 2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
+function ($) {
    'use strict';

    // MOBILEDROP CLASS DEFINITION
    // =========================

    var backdrop = '.mobiledrop-backdrop';
    var toggle   = '[data-toggle="mobiledrop"]';
    var Mobiledrop = function (element) {
        $(element).on('click.bs.mobiledrop', this.toggle)
    };

    Mobiledrop.VERSION = '3.3.7';

    function getParent($this) {
        var selector = $this.attr('data-target');

        if (!selector) {
            selector = $this.attr('href');
            selector = selector && /#[A-Za-z]/.test(selector) && selector.replace(/.*(?=#[^\s]*$)/, '');
        }

        var $parent = selector && $(selector);

        return $parent && $parent.length ? $parent : $this.parent()
    }

    function clearMenus(e) {
        if (e && e.which === 3) return;
        $(backdrop).remove();
        $(toggle).each(function () {
            var $this         = $(this);
            var $parent       = getParent($this);
            var relatedTarget = { relatedTarget: this };

            if (!$parent.hasClass('open')) return;

            if (e && e.type == 'click' && /input|textarea/i.test(e.target.tagName) && $.contains($parent[0], e.target)) return;

            $parent.trigger(e = $.Event('hide.bs.mobiledrop', relatedTarget));

            if (e.isDefaultPrevented()) return;

            $this.attr('aria-expanded', 'false');
            $parent.removeClass('open').trigger($.Event('hidden.bs.mobiledrop', relatedTarget))
        })
    }

    Mobiledrop.prototype.toggle = function (e) {
        var $this = $(this);

        if ($this.is('.disabled, :disabled')) return;

        var $parent  = getParent($this);
        var isActive = $parent.hasClass('open');

        clearMenus();

        if (!isActive) {
            if ('ontouchstart' in document.documentElement && !$parent.closest('.navbar-nav').length) {
                // if mobile we use a backdrop because click events don't delegate
                $(document.createElement('div'))
                        .addClass('mobiledrop-backdrop')
                        .insertAfter($(this))
                        .on('click', clearMenus)
            }

            var relatedTarget = { relatedTarget: this };
            $parent.trigger(e = $.Event('show.bs.mobiledrop', relatedTarget))

            if (e.isDefaultPrevented()) return;

            $this
                    .trigger('focus')
                    .attr('aria-expanded', 'true');

            $parent
                    .toggleClass('open')
                    .trigger($.Event('shown.bs.mobiledrop', relatedTarget))
        }

        return false
    };

    Mobiledrop.prototype.keydown = function (e) {
        if (!/(38|40|27|32)/.test(e.which) || /input|textarea/i.test(e.target.tagName)) return;

        var $this = $(this);

        e.preventDefault();
        e.stopPropagation();

        if ($this.is('.disabled, :disabled')) return;

        var $parent  = getParent($this);
        var isActive = $parent.hasClass('open');

        if (!isActive && e.which != 27 || isActive && e.which == 27) {
            if (e.which == 27) $parent.find(toggle).trigger('focus');
            return $this.trigger('click')
        }

        var desc = ' li:not(.disabled):visible a';
        var $items = $parent.find('.mobiledrop-menu' + desc);

        if (!$items.length) return;

        var index = $items.index(e.target);

        if (e.which == 38 && index > 0) { index--; }                 // up
        if (e.which == 40 && index < $items.length - 1) { index++; } // down
        if (!~index) { index = 0; }

        $items.eq(index).trigger('focus')
    };


    // MOBILEDROP PLUGIN DEFINITION
    // ==========================

    function Plugin(option) {
        return this.each(function () {
            var $this = $(this);
            var data  = $this.data('bs.mobiledrop');

            if (!data) $this.data('bs.mobiledrop', (data = new Mobiledrop(this)));
            if (typeof option == 'string') data[option].call($this)
        })
    }

    var old = $.fn.mobiledrop;

    $.fn.mobiledrop             = Plugin;
    $.fn.mobiledrop.Constructor = Mobiledrop;


    // MOBILEDROP NO CONFLICT
    // ====================

    $.fn.mobiledrop.noConflict = function () {
        $.fn.mobiledrop = old;
        return this
    };


    // APPLY TO STANDARD MOBILEDROP ELEMENTS
    // ===================================

    $(document)
            .on('click.bs.mobiledrop.data-api', clearMenus)
            .on('click.bs.mobiledrop.data-api', '.mobiledrop form', function (e) { e.stopPropagation() })
            .on('click.bs.mobiledrop.data-api', toggle, Mobiledrop.prototype.toggle)
            .on('keydown.bs.mobiledrop.data-api', toggle, Mobiledrop.prototype.keydown)
            .on('keydown.bs.mobiledrop.data-api', '.mobiledrop-menu', Mobiledrop.prototype.keydown)

}(jQuery);

+function ($) {
    'use strict';

    $(function() {
        var menus = window.__menus;
        if (!menus) {
            menus = {};
        }
        var menuCache = {};
        $('[data-webpart]').on('show.bs.dropdown show.bs.mobiledrop', function() {
            var partName = $(this).data('name');
            var safeName = $(this).data('webpart');

            if (menuCache[safeName]) {
                return;
            }

            var target = $(this).find('.dropdown-menu');

            if (target.length === 0) {
                target = $(this).parent().next('.dropdown-menu')
            }

            if (partName && safeName && target) {
                var id = target.attr('id');
                if (!id) {
                    id = LABKEY.Utils.id();
                    target.attr('id', id);
                }

                var config = {
                    renderTo: id,
                    partName: partName,
                    frame: 'none',
                    failure  : function(response) {
                        // Show a better error to the user than just a generic Unauthorized dialog
                        if (response.status === 401) {
                            document.getElementById(id).innerHTML = '<div style="padding: 5px">You do not have permission to view this data. You have likely been logged out.'
                                    + (this.loginUrl != null ? ' Please <a href="' + this.loginUrl + '">log in</a> again.' : ' Please <a href="#" onclick="location.reload();">reload</a> the page.') + "</div>";
                        }
                        else {
                            if (window.console && window.console.log) {
                                window.console.log(response);
                            }
                        }
                    }
                };

                if (menus[safeName]) {
                    config.partConfig = menus[safeName];
                }

                if (partName === 'MenuProjectNav') {
                    config.success = function() {
                        // if we have a selected part of the nav menu to jump to, update display states for the various
                        // parts of the nav tree submenus
                        var selectedSubmenu = $('ul.dropdown-menu li.lk-project-nav-tree-selected');
                        if (selectedSubmenu.length > 0) {
                            // toggle the open class for each of the submenus through the tree to the selected node
                            // and show each of the dropdown-submenu items up the tree, but hide the links below them
                            $.each(selectedSubmenu.parents('ul.dropdown-layer-menu'), function(index, submenu) {
                                var submenuEl = $(submenu);
                                submenuEl.toggleClass('open')
                                        .siblings('a').css('display', 'none');
                                submenuEl.parent().css('display', '')
                                        .siblings('li').css('display', 'none');
                            });

                            // show the selected submenu list items
                            selectedSubmenu.css('display', '');
                            selectedSubmenu.siblings('li').css('display', '');
                        }
                    };
                }

                var wp = new LABKEY.WebPart(config);
                menuCache[safeName] = wp;
                wp.render();
                $(this).unbind('click');
            }
        });

        $('#project-mobile')
                // lock body scrolling
                .on('show.bs.dropdown', function() { $('body').addClass('scroll-locked'); })
                .on('hide.bs.dropdown', function() { $('body').removeClass('scroll-locked'); })
                // select first menu item
                .on('show.bs.dropdown', function() {
                    var target = $(this).find('.lk-horizontal-menu li');
                    if (target && target.length > 0) {
                        $(target[0]).addClass('open').trigger('show.bs.mobiledrop');
                        $(this).find('.lk-horizontal-menu').animate({scrollLeft: 0});
                    }
                });
    });
}(jQuery);

+function($) {
    'use strict';

    $(function() {
        $('#global-search-trigger').click(function() {
            $(this).parent().toggleClass('active');
            var input = $('input.search-box');
            input.is(':focus') ? input.blur() : input.focus();
        });

        // delay for mobile focus on search
        $('#global-search-xs').on('show.bs.dropdown', function() {
            setTimeout(function() {
                jQuery(this).find('input.search-box').focus();
            }.bind(this), 500);
        });
    });
}(jQuery);

+function($) {
    'use strict';

    var SubMenu = function(element) {
        this.$element = $(element);
    };

    SubMenu.prototype.expand = function(e) {
        var el = $(this);

        // hide this link and its direct parent sibling list elements
        el.css('display', 'none');
        el.siblings('a').css('display', 'none');
        el.parent().siblings('li').css('display', 'none').trigger('subMenuExpand');

        // toggle the sibling ul element to show the nested list
        el.next('ul').toggleClass('open');

        e.stopPropagation();
        e.preventDefault();
    };

    SubMenu.prototype.collapse = function(e) {
        var el = $(this);
        var menu = el.parent().parent();

        // toggle the element class
        menu.toggleClass('open');
        menu.children('li').trigger('subMenuCollapse');

        // show the parent link and its direct parent sibling list elements
        var menuLink = menu.prev('a.subexpand');
        menuLink.css('display', '');
        menuLink.siblings('a').css('display', '');
        menuLink.parent().siblings('li').css('display', '');

        e.stopPropagation();
        e.preventDefault();
    };

    // TODO: Finish support for navigating SubMenu with keys
    SubMenu.prototype.keydown = function(e) {
        if (!/(27|37|38|39|40)/.test(e.which) || /input|textarea/i.test(e.target.tagName)) return;

        var el = $(this);

        e.preventDefault();
        e.stopPropagation();

        if (el.is('.disabled, :disabled')) return;

        switch (e.which) {
            case 27: // esc
                break;
            case 37: // left
                break;
            case 38: // up
                break;
            case 39: // right
                break;
            case 40: // down
                break;
        }
    };

    SubMenu.prototype.unfurl = function() {
        var menu =  $(this).children('ul.dropdown-menu');
        menu.find('li').css('display', '');
        menu.find('.subexpand').css('display', '');
        menu.find('.subexpand-link').css('display', '');
        menu.find('.dropdown-layer-menu.open').toggleClass('open');
    };

    SubMenu.prototype.viewportAlign = function() {
        var me = $(this);

        // menu may opt-out of aligning to viewport
        if (me.hasClass('skip-align')) {
            return;
        }

        var menu = me.children('ul.dropdown-menu');
        if (menu.length === 0) {
            return;
        }
        
        var offset = menu.offset();
        var win = $(window);

        // distance between menu's bottom edge and bottom of window
        var spaceDown = me.hasClass('dropup') ? win.height() - menu.height() - (me.offset().top - win.scrollTop())
                : win.scrollTop() + win.height() - (offset.top + menu.height());

        // distance between menu's top edge and top of window
        var spaceUp = me.hasClass('dropup') ? me.offset().top - (menu.height() + win.scrollTop())
                : offset.top - (menu.height() + me.height() + win.scrollTop());

        // if toggle element is in left half of screen then left align the menu
        var inLeftHemisphere = (me.offset().left + 100) < (win.width() / 2);

        if (spaceDown < 0) {
            if (spaceUp > 0 || spaceUp > spaceDown) {
                me.removeClass('dropdown').addClass('dropup');
            } else {
                me.removeClass('dropup').addClass('dropdown');
            }
        } else {
            me.removeClass('dropup').addClass('dropdown');
        }

        if (inLeftHemisphere) {
            menu.removeClass('dropdown-menu-right').addClass('dropdown-menu-left');
        }
        else {
            menu.removeClass('dropdown-menu-left').addClass('dropdown-menu-right');
        }
    };

    // SubMenu DATA-API
    // ==============
    var evt = (
        navigator.userAgent &&
        (
            navigator.userAgent.toLowerCase().indexOf('iphone') !== -1 ||
            navigator.userAgent.toLowerCase().indexOf('ipad') !== -1 ||
            navigator.userAgent.toLowerCase().indexOf('ipod') !== -1
        )
    ) ? 'touchstart' : 'click';

    $(document)
            .on(evt + '.bs.submenu.data-api', 'a.subexpand', SubMenu.prototype.expand)
            .on(evt + '.bs.submenu.data-api', 'a.subcollapse', SubMenu.prototype.collapse)
            .on('keydown.bs.submenu.data-api', 'a.subexpand', SubMenu.prototype.keydown)
            // note: the two bindings to unfurl. rollups are treated differently from common lk-menu-drop
            .on('hide.bs.dropdown.data-api', '.dropdown-rollup, .lk-menu-drop', SubMenu.prototype.unfurl)
            .on('shown.bs.dropdown.data-api', '.dropdown-rollup, .lk-menu-drop', SubMenu.prototype.viewportAlign);
}(jQuery);

// Initialize tooltips
+function($) {
    'use strict';

    $(function() {
        // initial bind
        $('[data-tt="tooltip"]').tooltip({
            trigger: 'hover'
        });

        // bind subsequent DOM updates
        $(document.body).tooltip({
            selector: '[data-tt="tooltip"]',
            trigger: 'hover'
        });
    });
}(jQuery);

// Menu filtering inputs
+function($) {
    'use strict';

    var menuFilterInputs;
    function attachMenuFiltering(e) {
        menuFilterInputs = [];

        // if the input has the data-filter-item attr then attach the keyup listner
        var menuFilterInputEls = $(e.target).find('input.dropdown-menu-filter');
        if (menuFilterInputEls.length) {
            menuFilterInputEls.each(function(index, filterInputEl) {
                var filterInput = $(filterInputEl);
                if (filterInput.attr('data-filter-item')) {
                    menuFilterInputs.push(filterInput);
                    filterInput.on('keyup', attachMenuFilterKeyup);

                    // add listener to the parent to know when the item is hidden so it can be reset
                    filterInput.parent().on('subMenuCollapse', function() { resetMenuFilteringInput(filterInput, 'list-item'); });
                    filterInput.parent().on('subMenuExpand', function() { resetMenuFilteringInput(filterInput); });
                }
            });
        }
    }

    function attachMenuFilterKeyup(e) {
        getMatchingMenuFilterItems(this).each(function(index, filterItemEl) {
            var filterItem = $(filterItemEl),
                filterItemTxt = filterItem.text().toLowerCase(),
                filterTxt = e.target.value.toLowerCase(),
                display = 'list-item';

            // hide the list-item if it does not contain the filter text
            if (filterTxt.length > 0 && filterItemTxt.indexOf(filterTxt) === -1) {
                display = 'none';
            }

            filterItem.css('display', display);
        });
    }

    function getMatchingMenuFilterItems(filterInputEl) {
        var itemStr = $(filterInputEl).attr('data-filter-item');
        return $('li.' + itemStr);
    }

    function resetMenuFiltering(e) {
        if (menuFilterInputs.length > 0) {
            for (var i = 0; i < menuFilterInputs.length; i++) {
                resetMenuFilteringInput(menuFilterInputs[i], 'list-item');
                menuFilterInputs[i].off('keyup', attachMenuFilterKeyup);
            }
        }
    }

    function resetMenuFilteringInput(inputEl, displayVal) {
        // reshow any hidden menu filter items
        if (displayVal) {
            getMatchingMenuFilterItems(inputEl).each(function(index, filterItemEl) {
                $(filterItemEl).css('display', displayVal);
            });
        }

        // reset the filter input value
        inputEl.val(null);
    }

    $(document).on('show.bs.dropdown', attachMenuFiltering);
    $(document).on('hide.bs.dropdown', resetMenuFiltering);
}(jQuery);

+function($) {
    'use strict';
    var LOCK_CLS = 'menu-locked',
        toggleTimeout = false,
        openMenu = undefined;

    var toggle = $.fn.dropdown.Constructor.prototype.toggle;
    var toggleSelector = '.navbar-header [data-toggle="dropdown"]';
    var popupSelector = '.navbar-header .dropdown-menu';

    function cancelToggle() {
        if (toggleTimeout) {
            clearTimeout(toggleTimeout);
            toggleTimeout = false;
        }
    }

    function delayShowPopup(elm) {
        if (openMenu) {
            cancelToggle();

            // If the open menu is different from the menu I'm hovering over then close it and open mine.
            // Otherwise, mine is already open, so do nothing.
            if (openMenu !== elm) {
                doToggle(openMenu);
                doToggle(elm);
            }
        }
        else {
            toggleTimeout = setTimeout(doToggle.bind(undefined, elm), 500);
        }
    }

    function delayHidePopup() {
        if (openMenu) {
            // If there is a menu open, then give the user some time before closing it.
            toggleTimeout = setTimeout(doToggle.bind(undefined, openMenu), 500);
        }
        else {
            // If there is not a menu open, then stop any attempt to show a menu
            cancelToggle();
        }
    }

    function doToggle(elm) {
        openMenu = openMenu ? undefined : elm;
        toggle.call(elm);
    }

    // Issue 32133: prevent openMenu toggle functions above from closing a locked menu
    function isLocked() {
        return $(openMenu).hasClass(LOCK_CLS);
    }

    function unlock() {
        return $(openMenu).removeClass(LOCK_CLS);
    }

    $(document)
            .on('hide.bs.dropdown', function(e) {
                // Issue 32133: prevent the default bootstrap dropdown clearMenus function from closing a locked menu
                if (e.relatedTarget && $(e.relatedTarget).hasClass(LOCK_CLS)) {
                    e.preventDefault();
                }
            })
            .on('click.bs.dropdown.data-api', function() {
                // This click handler respects clicking anywhere on the page.
                if (!isLocked()) {
                    openMenu = undefined;
                    cancelToggle();
                }
            })
            .on('click.bs.dropdown.data-api', toggleSelector, function() {
                unlock();
                openMenu = openMenu ? undefined : this;
                cancelToggle();
            })
            .on('mouseenter.bs.dropdown.data-api', toggleSelector, function() {
                unlock();
                delayShowPopup(this);
            })
            .on('mouseleave.bs.dropdown.data-api', toggleSelector, function() {
                if (!isLocked()) {
                    delayHidePopup(this);
                }
            })
            .on('mouseenter', popupSelector, function() {
                if (!isLocked()) {
                    cancelToggle();
                }
            })
            .on('mouseleave', popupSelector, function() {
                if (!isLocked()) {
                    delayHidePopup(openMenu);
                }
            })
            .on('click', popupSelector, function(e) {
                e.stopPropagation();
            });
}(jQuery);

+function($) {
    'use strict';

    var nav_ready = false;
    var nav_menus;
    var nav_tabs_separate;
    var nav_tabs_collapsed;
    var nav_container_widths = { menus: 0, tabs: 0 };

    var toggleNavTabs = function() {
        var nav_container = $('.labkey-page-nav > .container');

        if (nav_container_widths.menus == 0 && nav_menus.length) {
            nav_container_widths.menus = nav_menus.width();
        }
        if (nav_container_widths.tabs == 0 && nav_tabs_separate.length) {
            nav_container_widths.tabs = nav_tabs_separate.width();
        }

        if (nav_container.length) {
            var showCollapsed = !LABKEY.pageAdminMode && nav_container.width() < (nav_container_widths.menus + nav_container_widths.tabs + 20);
            showCollapsed && nav_tabs_separate ? nav_tabs_separate.hide() : nav_tabs_separate.show();
            showCollapsed && nav_tabs_collapsed ? nav_tabs_collapsed.show() : nav_tabs_collapsed.hide();
        }

        // using opacity 0 as a way to get the tabs width without showing it, so change that back now
        // that we have decided which to show/hide between the separate/collapsed tabs
        if (nav_tabs_separate) {
            nav_tabs_separate.css('opacity', 1);
        }
    };

    $(window).on('resize', function(){
        if (nav_ready) {
            toggleNavTabs();
        }
    });

    $(document).ready(function() {
        nav_menus = $('#nav_dropdowns');
        nav_tabs_collapsed = $('#nav_tabs > #lk-nav-tabs-collapsed');
        nav_tabs_separate = $('#nav_tabs > #lk-nav-tabs-separate');
        nav_ready = true;
        toggleNavTabs();
    });
}(jQuery);

// "fixed" header horizontally, remains in normal flow vertically
// consider: "position: sticky" when it is more widely supported
+function($) {
    $(function() {
        var head = $('.lk-header-ct');
        if (head) {
            var last = 0;
            var onScroll = function() {
                if (window.pageXOffset !== last) {
                    head.css('left', last = window.pageXOffset);
                }
            };

            $(window).on('scroll', onScroll);
            onScroll();
        }
    });
}(jQuery);