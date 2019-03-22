// Set USE_NATIVE_JSON so Ext.decode and Ext.encode use JSON.parse and JSON.stringify instead of eval
Ext.USE_NATIVE_JSON = true;

// Utility call to reset the location of the blank image; otherwise we connect to extjs.com by default:
Ext.BLANK_IMAGE_URL = LABKEY.contextPath + '/_.gif';  // 2.0

// set csrf value for all requests
if (!Ext.Ajax.defaultHeaders) {
    Ext.Ajax.defaultHeaders = {};
}
Ext.apply(Ext.Ajax.defaultHeaders, LABKEY.defaultHeaders);

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

+function() {
    var _initComponent = Ext.Window.prototype.initComponent;

    /**
     * Allow responsive modal interaction for Ext.Window components.
     * At small screen width (user configurable), the popup will take full screen width.
     * For modal dialog with 'closable' set to true, clicking outside the popup will close the popup.
     * Configs:
     *      suppressResponsive: true to opt out of this feature, default false
     *      smallScreenWidth: the pixel screen width at which responsive sizing kicks in, default 480
     *      maximizeOnSmallScreen: true to maximize popup to full screen width and height on small screen,
     *                              false to only take full width, default false.
     *      closableOnMaskClick: true to always support closing closable modal by click on mask regardless of screen size,
     *                            otherwise only closable on mask click for small screens, default false
     *      useExtStyle: true to use ext style, false to use bootstrap style, default true
     */
    Ext.override(Ext.Window, {
        initComponent : function() {
            if (this.suppressResponsive) {
                _initComponent.apply(this, arguments);
                return;
            }

            // experimental, change look of windows
            var useBootstrapStyle = this.useExtStyle === undefined ? false : !this.useExtStyle;
            if (useBootstrapStyle) {
                if (!this.cls)
                    this.cls = '';
                this.cls += ' modal-content';
                this.shadow = false;
            }

            var useMaxWidth = window.innerWidth < (this.smallScreenWidth ? this.smallScreenWidth : 481);
            useMaxWidth = useMaxWidth || (this.width && (this.width > window.innerWidth)); // if configured width is large than available screen size.
            useMaxWidth = useMaxWidth || (this.minWidth && (this.minWidth > window.innerWidth)); // if configured min-width is large than available screen size.

            if (useMaxWidth) {
                if (this.maximizeOnSmallScreen) {
                    this.maximized = true;
                }
                else {
                    var getBodyContainerWidth = function() {
                        var containerWidth = window.innerWidth;
                        var parent = Ext.getBody();
                        var container = parent.query('> div.container');
                        if (container && container[0])
                            containerWidth = container[0].offsetWidth;
                        else {
                            // if template is not body
                            container = parent.query('> div > div.container');
                            if (container && container[0])
                                containerWidth = container[0].offsetWidth;
                        }

                        return containerWidth;
                    };

                    var windowWidth = window.innerWidth;
                    var containerWidth = getBodyContainerWidth();
                    if (windowWidth - containerWidth < 30)
                        this.width = containerWidth - 20 * 2; // reserve extra padding for scrollbar
                    else
                        this.width = containerWidth;
                }
            }

            var me = this;
            if (this.modal && this.closable && (useMaxWidth || this.closableOnMaskClick)) {
                this.clickOutHandler = function() {
                    me.close(me.closeAction);
                };
                this.clickOutParentCmp = Ext.getBody();
                this.mon(this.clickOutParentCmp, 'click', this.clickOutHandler, this, { delegate: '.ext-el-mask' });
            }

            _initComponent.apply(this, arguments);
        }
    });
}();
