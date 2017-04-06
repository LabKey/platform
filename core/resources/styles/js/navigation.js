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
    $(function() {
        var menus = window.__menus;
        if (!menus) {
            return;
        }
        $('[data-webpart]').on('show.bs.dropdown show.bs.mobiledrop', function() {
            var partName = $(this).data('name');
            var safeName = $(this).data('webpart');
            var target = $(this).find('.dropdown-menu');

            if (partName && safeName && target) {
                var id = target.attr('id');
                if (!id) {
                    id = LABKEY.Utils.id();
                    target.attr('id', id);
                }

                var config = {
                    renderTo: id,
                    partName: partName,
                    frame: 'none'
                };

                if (menus[safeName]) {
                    config.partConfig = menus[safeName];
                }

                var wp = new LABKEY.WebPart(config);
                wp.render();
                $(this).unbind('click');
            }
        });

        // lock body scrolling
        $('#project-mobile').on('show.bs.dropdown', function() {
            $('body').addClass('scroll-locked');
        });
        $('#project-mobile').on('hide.bs.dropdown', function() {
            $('body').removeClass('scroll-locked');
        });
    });
}(jQuery);