/*
 * Copyright (c) 2007-2009 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
/**
 *
 * DEPRACATED use Ext.MessageBox or Ext.Window 
 *
 * A Dialog box widget that extends the yahoo Panel class in order to show the dialog box with
 * special animation effects like fading and adjusting the opacity of the background.
 *
 * @requires javascript: yahoo,dom,event,dragdrop,animation,container,dialogbox:
 * @requires css: container.css, dialogBox.css
 *
 * <pre>
 *  <link rel="stylesheet" href="<%=request.getContextPath()%>/_yui/build/container/assets/container.css" type="text/css"/>
 *  <link rel="stylesheet" href="<%=request.getContextPath()%>/utils/dialogBox.css" type="text/css"/>
 *  LABKEY.requiresYahoo("yahoo");
 *  LABKEY.requiresYahoo("event");
 *  LABKEY.requiresYahoo("dom");
 *  LABKEY.requiresYahoo("dragdrop");
 *  LABKEY.requiresYahoo("animation");
 *  LABKEY.requiresYahoo("container");
 *  LABKEY.requiresScript("utils/dialogBox.js");
 * </pre>
 *
 * Example use (instantiates a dialogbox object, sets the initial properties (height, width), sets the body,
 * renders and shows the dialog:
 *
 * <pre>
 *  dialogHelper = new LABKEY.widget.DialogBox("chartdesigner",{width:"675px", height:"250px"});
 *  dialogHelper.setBody("<iframe src=\"" + target + "\" height=\"100%\" width=\"100%\"></iframe>");
 *  dialogHelper.render();
 *  dialogHelper.show();
 * </pre>
 */

LABKEY.widget.DialogBox = function(el, userConfig)
{
    if (arguments.length > 0) {
        LABKEY.widget.DialogBox.superclass.constructor.call(this, el, userConfig);
    }
}

// extend the yahool panel class
YAHOO.extend(LABKEY.widget.DialogBox, YAHOO.widget.Panel);

// Define the CSS class for the dialog box
LABKEY.widget.DialogBox.CSS_DIALOGBOX = "lk_dialogbox";

// CSS style for resize handles
LABKEY.widget.DialogBox.CSS_PANEL_RESIZE = "lk_resizepanel";
LABKEY.widget.DialogBox.CSS_RESIZE_HANDLE = "lk_resizehandle";

/**
 * Initialize the dialog box by applying the stylesheet and any optional user
 * config params.
 */
LABKEY.widget.DialogBox.prototype.init = function(el, userConfig) {
    LABKEY.widget.DialogBox.superclass.init.call(this, el);

    this.beforeInitEvent.fire(LABKEY.widget.DialogBox);
    //YAHOO.util.Dom.addClass(this.innerElement, LABKEY.widget.DialogBox.CSS_DIALOGBOX);
    //YAHOO.util.Dom.addClass(this.innerElement, LABKEY.widget.DialogBox.CSS_PANEL_RESIZE);

    this.resizeHandle = document.createElement("DIV");
    this.resizeHandle.id = this.id + "_r";
    this.resizeHandle.className = LABKEY.widget.DialogBox.CSS_RESIZE_HANDLE;

    this.beforeShowEvent.subscribe(function() {

        this.body.style.overflow = "auto";

    }, this, true);

    this.beforeHideEvent.subscribe(function() {
        /*
             Set the CSS "overflow" property to "hidden" before
             hiding the panel to prevent the scrollbars from
             bleeding through on Firefox for OS X.
        */
        this.body.style.overflow = "hidden";

    }, this, true);

    this.beforeRenderEvent.subscribe(function() {
        /*
             Set the CSS "overflow" property to "hidden" by
             default to prevent the scrollbars from bleeding
             through on Firefox for OS X.
        */
        this.body.style.overflow = "hidden";

        if (! this.footer) {
            this.setFooter("");
        }

    }, this, true);

    this.renderEvent.subscribe(function() {
        var me = this;

        me.innerElement.appendChild(me.resizeHandle);

        this.ddResize = new YAHOO.util.DragDrop(this.resizeHandle.id, this.id);
        this.ddResize.setHandleElId(this.resizeHandle.id);

        var headerHeight = me.header.offsetHeight;

        this.ddResize.onMouseDown = function(e) {

            this.startWidth = me.innerElement.offsetWidth;
            this.startHeight = me.innerElement.offsetHeight;

            me.cfg.setProperty("width", this.startWidth + "px");
            me.cfg.setProperty("height", this.startHeight + "px");

            this.startPos = [YAHOO.util.Event.getPageX(e),
                             YAHOO.util.Event.getPageY(e)];

            me.innerElement.style.overflow = "hidden";
            me.body.style.overflow = "auto";
        }

        this.ddResize.onDrag = function(e) {
            var newPos = [YAHOO.util.Event.getPageX(e),
                          YAHOO.util.Event.getPageY(e)];

            var offsetX = newPos[0] - this.startPos[0];
            var offsetY = newPos[1] - this.startPos[1];

            var newWidth = Math.max(this.startWidth + offsetX, 10);
            var newHeight = Math.max(this.startHeight + offsetY, 10);

            me.cfg.setProperty("width", newWidth + "px");
            me.cfg.setProperty("height", newHeight + "px");

            var bodyHeight = (newHeight - 5 - me.footer.offsetHeight - me.header.offsetHeight - 3);
            if (bodyHeight < 0) {
                bodyHeight = 0;
            }

            me.body.style.height =  bodyHeight + "px";

            var innerHeight = me.innerElement.offsetHeight;
            var innerWidth = me.innerElement.offsetWidth;

            if (innerHeight < headerHeight) {
                me.innerElement.style.height = headerHeight + "px";
            }

            if (innerWidth < 20) {
                me.innerElement.style.width = "20px";
            }
        }
    }, this, true);
    
    // the default params
    this.cfg.applyConfig({effect:{effect:YAHOO.widget.ContainerEffect.FADE,duration:0.50},
            fixedcenter:true,
            constraintoviewport:true,
            underlay:"none",
            close:true,
            visible:true,
            modal:true
        }, true);

    if (userConfig) {
        this.cfg.applyConfig(userConfig, false);
    }
    this.initEvent.fire(LABKEY.widget.DialogBox);
};

/**
 * Overrides the handler for the 'modal' property so we can perform animation-related
 * functionality.
 */
LABKEY.widget.DialogBox.prototype.configModal = function(type, args, obj) {
    var modal = args[0];

    if (modal)
    {
        this.buildMask();

        if (typeof this.maskOpacity == 'undefined') {
            this.mask.style.visibility = "hidden";
            this.mask.style.display = "block";
            this.maskOpacity = YAHOO.util.Dom.getStyle(this.mask,"opacity");
            this.mask.style.display = "none";
            this.mask.style.visibility = "visible";
        }

        if (! YAHOO.util.Config.alreadySubscribed( this.beforeShowEvent, this.showMask, this ) ) {
            this.beforeShowEvent.subscribe(this.showMask, this, true);
        }
        if (! YAHOO.util.Config.alreadySubscribed( this.hideEvent, this.hideMask, this) ) {
            this.hideEvent.subscribe(this.hideMask, this, true);
        }
        if (! YAHOO.util.Config.alreadySubscribed( YAHOO.widget.Overlay.windowResizeEvent, this.sizeMask, this ) ) {
            YAHOO.widget.Overlay.windowResizeEvent.subscribe(this.sizeMask, this, true);
        }
        if (! YAHOO.util.Config.alreadySubscribed( this.destroyEvent, this.removeMask, this) ) {
            this.destroyEvent.subscribe(this.removeMask, this, true);
        }
        this.cfg.refireEvent("zIndex");
    }
    else
    {
        this.beforeShowEvent.unsubscribe(this.showMask, this);
        this.beforeHideEvent.unsubscribe(this.hideMask, this);
        YAHOO.widget.Overlay.windowResizeEvent.unsubscribe(this.sizeMask);
    }
};

/**
 * Overrides the showMask() function in order to do the fade-in.
 */
LABKEY.widget.DialogBox.prototype.showMask = function() {
    if (this.cfg.getProperty("modal") && this.mask)
    {
        YAHOO.util.Dom.addClass(document.body, "masked");
        this.sizeMask();

        var o = this.maskOpacity;

        if (! this.maskAnimIn)
        {
            this.maskAnimIn = new YAHOO.util.Anim(this.mask, {opacity: {to:o}}, 0.50)
            YAHOO.util.Dom.setStyle(this.mask, "opacity", 0);
        }

        if (! this.maskAnimOut)
        {
            this.maskAnimOut = new YAHOO.util.Anim(this.mask, {opacity: {to:0}}, 0.50)
            this.maskAnimOut.onComplete.subscribe(function() {
                                                    this.mask.tabIndex = -1;
                                                    this.mask.style.display = "none";
                                                    this.hideMaskEvent.fire();
                                                    YAHOO.util.Dom.removeClass(document.body, "masked");
                                                  }, this, true);
        }
        this.mask.style.display = "block";
        this.maskAnimIn.animate();
        this.mask.tabIndex = 0;
        this.showMaskEvent.fire();
    }
};

LABKEY.widget.DialogBox.prototype.hideMask = function() {
    if (this.cfg.getProperty("modal") && this.mask)
    {
        if (this.maskAnimOut)
            this.maskAnimOut.animate();
    }
};

LABKEY.widget.DialogBox.prototype.expand = function(width, height) {
    var oldHeight = this.innerElement.clientHeight;
    var animH = new LABKEY.widget.DialogBoxAnimHeight(this, {clientHeight : {from:oldHeight, to:height}});
    animH.animate();

    var oldWidth = this.innerElement.clientWidth;
    var animW = new LABKEY.widget.DialogBoxAnimWidth(this, {clientWidth : {from:oldWidth, to:width}});
    animW.animate();
}

LABKEY.widget.DialogBoxAnimHeight = function(el, attributes)
{
    if (arguments.length > 0) {
        LABKEY.widget.DialogBoxAnimHeight.superclass.constructor.call(this, el, attributes);
    }
}
YAHOO.extend(LABKEY.widget.DialogBoxAnimHeight, YAHOO.util.Anim);
                                                  
LABKEY.widget.DialogBoxAnimHeight.prototype.setAttribute = function(attr, val, unit){

    val = (val > 0) ? val : 0;
    this.getEl().cfg.setProperty("height", val + unit);
}

LABKEY.widget.DialogBoxAnimWidth = function(el, attributes)
{
    if (arguments.length > 0) {
        LABKEY.widget.DialogBoxAnimWidth.superclass.constructor.call(this, el, attributes);
    }
}
YAHOO.extend(LABKEY.widget.DialogBoxAnimWidth, YAHOO.util.Anim);

LABKEY.widget.DialogBoxAnimWidth.prototype.setAttribute = function(attr, val, unit){

    val = (val > 0) ? val : 0;
    this.getEl().cfg.setProperty("width", val + unit);
}
