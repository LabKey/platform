// Set USE_NATIVE_JSON so Ext.decode and Ext.encode use JSON.parse and JSON.stringify instead of eval
Ext.USE_NATIVE_JSON = true;

// Utility call to reset the location of the blank image; otherwise we connect to extjs.com by default:
Ext.BLANK_IMAGE_URL = LABKEY.contextPath + '/_.gif';  // 2.0

// set csrf value for all requests
Ext.Ajax.defaultHeaders = {'X-LABKEY-CSRF': LABKEY.CSRF};

// set the default ajax timeout from 30's to 5 minutes
Ext.Ajax.timeout = 5 * 60 * 1000;

Ext.menu.Menu.prototype.addClass('extContainer');

Ext.apply(Ext, {
    /**
     * @author Nigel (Animal) White
     * @contributor Shea Frederick - http://www.vinylfox.com
     * @license MIT License
     * <p>Override to allow mouse event forwarding through masking layers - .</p>
     * http://www.vinylfox.com/forwarding-mouse-events-through-layers/
     * http://code.google.com/p/ext-ux-datadrop/source/browse/trunk/src/Override.js
     */
    num: function(v, defaultValue){
        v = Number(Ext.isEmpty(v) || Ext.isBoolean(v) ? NaN : v);
        return isNaN(v) ? defaultValue : v;
    },

    /**
     * 13407: Fix Ext _dc parameter appending
     * The default Ext.urlAppend method does not work with LabKey URL generation as LabKey
     * appends a ? at the end of the URL by default (e.g. controller/action.view? ).
     * To satisfy backwards compatibility this check just assures that an 'invalid chunk' is
     * not in the URL such as ?&.
     * See Ext.urlAppend in ext-3.4.1/src/ext-core/src/core/Ext.js
     */
    urlAppend : function(url, s) {
        if (!Ext.isEmpty(s)){
            url = url + (url.indexOf('?') === -1 ? '?' : '&') + s;
        }
        if (url.indexOf('?&') !== -1) {
            url = url.replace('?&', '?');
        }
        return url;
    }
});

// See change to Ext.lib.Event._unload in ext-base-debug.js -- could not be added to ext-patches since that is too late
// only added to debug, not to ext-all.js

// Adding 'tooltip' property to menu items
// http://www.sencha.com/forum/showthread.php?77656-How-to-put-a-tooltip-on-a-menuitem&p=374038#post374038
Ext.override(Ext.menu.Item, {
    onRender : function(container, position){
        if (!this.itemTpl) {
            this.itemTpl = Ext.menu.Item.prototype.itemTpl = new Ext.XTemplate(
                '<a id="{id}" class="{cls}" hidefocus="true" unselectable="on" href="{href}"',
                    '<tpl if="hrefTarget">',
                        ' target="{hrefTarget}"',
                    '</tpl>',
                 '>',
                     '<img src="{icon}" class="x-menu-item-icon {iconCls}"/>',
                     '<span class="x-menu-item-text">{text}</span>',
                 '</a>'
             );
        }
        var a = this.getTemplateArgs();
        this.el = position ? this.itemTpl.insertBefore(position, a, true) : this.itemTpl.append(container, a, true);
        this.iconEl = this.el.child('img.x-menu-item-icon');
        this.textEl = this.el.child('.x-menu-item-text');
        if (this.tooltip) {
            this.tooltip = new Ext.ToolTip(Ext.apply({
                target: this.el
            }, Ext.isObject(this.tooltip) ? this.toolTip : { html: this.tooltip }));
        }
        Ext.menu.Item.superclass.onRender.call(this, container, position);
    }
});

Ext.override(Ext.Element, (function(){
    var doc = document,
        SCROLLLEFT = 'scrollLeft',
        SCROLLTOP = 'scrollTop',
        HTMLEvts = /^(scroll|resize|load|unload|abort|error)$/,
        mouseEvts = /^(click|dblclick|mousedown|mouseup|mouseover|mouseout|contextmenu|mousenter|mouseleave)$/,
        UIEvts = /^(focus|blur|select|change|reset|keypress|keydown|keyup)$/,
        onPref = /^on/;

    function getScroll() {
        var dd = doc.documentElement,
            db = doc.body;
        if(dd && (dd[SCROLLTOP] || dd[SCROLLLEFT])){
            return [dd[SCROLLLEFT], dd[SCROLLTOP]];
        }else if(db){
            return [db[SCROLLLEFT], db[SCROLLTOP]];
        }else{
            return [0, 0];
        }
    }

    return {
        /**
         * Fires an event through this Element.
         * @param e {String} Event name. eg: 'mousedown'.
         * @param initializer {Function
         */
        fireEvent: Ext.isIE ? function(e, evtInitializer) {
            var evt;
            e = e.toLowerCase();
            if (!onPref.test(e)) {
                e = 'on' + e;
            }
            if (Ext.isFunction(evtInitializer)) {
                evt = document.createEventObject();
                evtInitializer(evt);
            } else {
                evt =  evtInitializer;
            }
            this.dom.fireEvent(e, evt);
        } : function(e, evtInitializer) {
            var evt;
            e = e.toLowerCase();
            e.replace(onPref, '');
            if (mouseEvts.test(e)) {
                var b = {};
                if (this.getBox) {
                    b = this.getBox();
                } else {
                    b.width = this.getWidth();
                    b.height = this.getHeight();
                    b.x = this.getX();
                    b.y = this.getY();
                }
                var x = b.x + b.width / 2,
                    y = b.y + b.height / 2;
                evt = document.createEvent("MouseEvents");
                evt.initMouseEvent(e, true, true, window, (e=='dblclick')?2:1, x, y, x, y, false, false, false, false, 0, null);
            } else if (UIEvts.test(e)) {
                evt = document.createEvent("UIEvents");
                evt.initUIEvent(e, true, true, window, 0);
            } else if (HTMLEvts.test(e)) {
                evt = document.createEvent("HTMLEvents");
                evt.initEvent(e, true, true);
            }
            if (evt) {
                if (Ext.isFunction(evtInitializer)) {
                    evtInitializer(evt);
                }
                this.dom.dispatchEvent(evt);
            }
        },

        /**
         * Forwards mouse events from a floating mask element to the underlying document.
         */
        forwardMouseEvents: function(evt) {
            var me = this,
                xy, t, lastT,
                evts = [ 'mousemove', 'mousedown', 'mouseup', 'dblclick', 'mousewheel' ];

            me.on('mouseout', function() {
                if (lastT) {
                    Ext.fly(lastT).fireEvent('mouseout');
                    lastT = null;
                }
            });

            for (var i = 0, l = evts.length; i < l; i++) {
                this.on(evts[i], function(e) {
                    var s = (Ext.isGecko) ? getScroll() : [0, 0],
                        be = e.browserEvent,
                        x = Ext.num(be.pageX, be.clientX) - s[0],
                        y = Ext.num(be.pageY, be.clientY) - s[1],
                        et = be.type,
                        t;

                    if (!me.forwardingSuspended && me.isVisible()) {
                        e.stopPropagation();
                        me.forwardingSuspended = true;
                        me.hide();
                        t = Ext.get(document.elementFromPoint(x, y));
                        me.show();
                        if (!t) {
                            lastT.fireEvent('mouseout');
                            lastT = t;
                            delete me.forwardingSuspended;
                            return;
                        }
                        if (t === lastT) {
                            if (et == 'mouseup') {
                                t.fireEvent('click');
                            }
                        } else {
                            if (lastT) {
                                lastT.fireEvent('mouseout');
                            }
                            t.fireEvent('mouseover');
                        }
                        if (et !== 'mousemove') {
                            if (t.dom.fireEvent) {
                                t.fireEvent(et, be);
                            } else {
                                e = document.createEvent("MouseEvents");
                                e.initMouseEvent(et, true, true, window, be.detail, be.screenX, be.screenY, be.clientX, be.clientY,
                                    be.ctrlKey, be.altKey, be.shiftKey, be.metaKey, be.button, null);
                                t.dom.dispatchEvent(e);
                            }
                        }
                        lastT = t;
                        delete me.forwardingSuspended;
                    }
                });
            }
        }
    };
})());

// 18985: Adding NaN check for width adjustments in IE8
// http://www.sencha.com/forum/archive/index.php/t-94121.html
if (Ext.isIE7 || Ext.isIE8) {
    Ext.override(Ext.Element, (function(){
        return {
            setWidth : function(width, animate) {
                var me = this;
                width = me.adjustWidth(width);
                if (isNaN(width)) { width = 'auto'; }
                !animate || !me.anim ?
                        me.dom.style.width = me.addUnits(width) :
                        me.anim({width : {to : width}}, me.preanim(arguments, 1));
                return me;
            }
        };
    })());
}

// Fix for improper ZIndex calculation
// Ext.dd.DragDropMgr.getZIndex in ext-all-debug.js line #21689
// added in ext-all.js as well.
// - while(element !== body) {
// + while(element !== body && element !== null) {

// Fix for Firefox not getting to a ready state with popup render
// initDocRedy() in ext-all-debug.js line #5136
// added in ext-all.js as well
// - }else if (Ext.isWebKit){
// + }else if (Ext.isWebKit || Ext.isGecko){


if (LABKEY.experimental.useExperimentalCoreUI)
{
    (function() {

        Ext.ns('LABKEY.ext3ResponsiveUtil');

        /**
         * @name LABKEY.ext4.ResponsiveUtil
         * @class
         * Ext4 responsive utilities, contains functions to support responsive ext4 components
         */
        LABKEY.ext3ResponsiveUtil = {};

        var Util = LABKEY.ext3ResponsiveUtil;

        Ext.apply(Util, {
            getHeaderAndNavHeight: function() {
                var parent = Ext.getBody();
                var headerNavHeight = 0;
                var header = parent.query("body>div.labkey-page-header");
                if (header && header[0])
                    headerNavHeight += header[0].offsetHeight;
                var nav = parent.query("body>div.labkey-page-nav");
                if (nav && nav[0])
                    headerNavHeight += nav[0].offsetHeight;

                return headerNavHeight;
            },

            getBodyContainerWidth: function () {
                var containerWidth = window.innerWidth;
                var parent = Ext.getBody();
                var container = parent.query(">div.container");
                if (container && container[0])
                    containerWidth = container[0].offsetWidth;
                else {
                    // if template is not body
                    container = parent.query(">div>div.container");
                    if (container && container[0])
                        containerWidth = container[0].offsetWidth;
                }

                return containerWidth;
            },

            /**
             * This method takes an object that is/extends an Ext3.Container (e.g. Panels, Toolbars, Viewports, Menus) and
             * resizes it so the Container fits inside the its parent container.
             * @param extContainer - (Required) outer container which is the target to be resized
             * @param skipWidth - true to skip updating width, default false
             * @param skipHeight - true to skip updating height, default false
             * @param paddingWidth - total width padding
             * @param paddingHeight - total height padding
             * @param offsetY - distance between bottom of page to bottom of component
             */
            resizeToParentContainer: function(extContainer, skipWidth, skipHeight, paddingWidth, paddingHeight, offsetY)
            {
                if (!extContainer || !extContainer.rendered || (skipWidth && skipHeight))
                    return;

                var width = 0;
                if (!skipWidth)
                {
                    var parent = extContainer.el.parent();
                    width = parent.getBox().width;
                }

                var xy = extContainer.el.getXY();

                var height = 0;
                if (!skipHeight)
                {
                    height = window.innerHeight - xy[1];
                }

                var padding = [0, 0];
                padding[0] = paddingWidth ? paddingWidth : 0;
                padding[1] = paddingHeight ? paddingHeight : 0;

                if (offsetY == undefined || offsetY == null)
                    offsetY = 35;

                var size = {
                    width  : Math.max(100,width-padding[0]),
                    height : Math.max(100,height-padding[1] - offsetY)
                };

                if (skipWidth)
                    extContainer.setHeight(size.height);
                else if (skipHeight)
                    extContainer.setWidth(size.width);
                else
                    extContainer.setSize(size);
                extContainer.doLayout();
            }
        });

    }());
    /**
     * Patch to allow responsive modal interaction for Ext4 Ext.Window components.
     * At small screen width (user configurable), the popup will take full screen width.
     * For modal dialog with 'closable' set to true, clicking outside the popup will close the popup.
     * Configs:
     *      suppressResponsive: true to opt out of this feature, default false
     *      smallScreenWidth: the pixel screen width at which responsive sizing kicks in, default 480
     *      maximizeOnSmallScreen: true to maximize popup to full screen width and height on small screen,
     *                              false to only take full width, default false.
     *      closableOnMaskClick: true to always support closing closable modal by click on mask regardless of screen size,
     *                            otherwise only closable on mask click for small screens, default false
     *      useExtStyle: true to use ext style, false to use bootstrap style, default false
     */
    Ext.override(Ext.Window, {
            initComponent: function()
            {
                if (this.suppressResponsive || !this.modal)
                {
                    this.defaultInitComponent();
                    return;
                }

                if (!this.useExtStyle)
                {
                    if (!this.cls)
                        this.cls = '';
                    this.cls += ' modal-content';
                    this.shadow = false;
                }

                var useMaxWidth = window.innerWidth < (this.smallScreenWidth ? this.smallScreenWidth : 481);
                useMaxWidth = useMaxWidth || (this.width && (this.width > window.innerWidth)); // if configured width is large than available screen size.
                useMaxWidth = useMaxWidth || (this.minWidth && (this.minWidth > window.innerWidth)); // if configured min-width is large than available screen size.

                if (useMaxWidth)
                {
                    if (this.maximizeOnSmallScreen)
                    {
                        this.maximized = true;
                    }
                    else {
                        var windowWidth = window.innerWidth;
                        var containerWidth = LABKEY.ext3ResponsiveUtil.getBodyContainerWidth();
                        if (windowWidth - containerWidth < 30)
                            this.width = containerWidth - 20 * 2; // reserve extra padding for scrollbar
                        else
                            this.width = containerWidth;
                    }
                }

                var me = this;
                if (this.closable && (useMaxWidth || this.closableOnMaskClick))
                {
                    var parentCmp = Ext.getBody();
                    this.clickOutHandler = function(){
                        me.close(me.closeAction);
                    };
                    this.clickOutParentCmp = parentCmp;
                    this.mon(this.clickOutParentCmp, 'click', this.clickOutHandler, this, { delegate: '.ext-el-mask' });
                }

                this.defaultInitComponent();
            },

            defaultInitComponent: function()
            {
                this.initTools();
                Ext.Window.superclass.initComponent.call(this);
                this.addEvents(
                        'resize',
                        'maximize',
                        'minimize',
                        'restore'
                );
                // for backwards compat, this should be removed at some point
                if(Ext.isDefined(this.initHidden)){
                    this.hidden = this.initHidden;
                }
                if(this.hidden === false){
                    this.hidden = true;
                    this.show();
                }
            }
        });
}
