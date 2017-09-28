/*
 * Copyright (c) 2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('HoverNavigation', {
    mixins : {
        observable : 'Ext.util.Observable'
    },

    statics : {
        Parts : {},
        visiblePopup : false,
        clickClose : false,
        _click : function(e) {
            if (HoverNavigation.visiblePopup) {
                if (!HoverNavigation.visiblePopup.focused(e)) {
                    HoverNavigation.visiblePopup.hide();
                }
            }
        }
    },

    showDelay : 500,

    hideDelay : 500,

    hoverCls : 'selected',

    loginUrl : null,

    constructor : function(config) {

        // will apply config to this
        this.mixins.observable.constructor.call(this, config);

        this.hoverEl = Ext4.get(config.hoverElem);
        if (!this.hoverEl) {
            return;
        }

        var loader = Ext4.DomHelper.insertAfter('menubar', {
            id  : config.hoverElem + '_menu',
            tag : 'div',
            cls : 'labkey-webpart-menu',
            children : [{
                tag : 'div',
                cls : 'loading-indicator',
                style : 'width: 100px; height: 100px;'
            }]
        });

        this.addEvents('beforehide');

        this.popup = new Ext4.Layer({zindex : 1000, constrain : false }, loader);
        this.popup.alignTo(this.hoverEl);
        this.popup.hide();

        // Configure hover element list
        this.hoverEl.hover(this.onTargetOver, this.delayCheck, this);
        this.popup.hover(this.cancelHide, this.delayCheck, this);

        // Configure click element list
        this.hoverEl.on('click', this.onTargetOver, this);

        // Initialize global click
        if (!HoverNavigation.clickClose) {
            HoverNavigation.clickClose = true;
            Ext4.getDoc().on('click', HoverNavigation._click);
        }
    },

    cancelShow : function() {
        if (this.showTimeout) {
            clearTimeout(this.showTimeout);
            this.showTimeout = false;
        }
    },

    cancelHide : function() {
        if (this.hideTimeout) {
            clearTimeout(this.hideTimeout);
            this.hideTimeout = false;
        }
    },

    onTargetOver : function(e) {
        this.cancelHide();
        this.render();

        // show immediately if we already have a menu up
        // Otherwise, make sure that someone hovers for a while
        (e && e.type == 'click' ? this.show() : (HoverNavigation.visiblePopup ? this.show() : this.delayShow()));
    },

    notFocused : function(e) { return !this.hRegion(e) || !this.pRegion(e); },

    focused : function(e) { return this.hRegion(e) || this.pRegion(e); },

    hRegion : function(e) { return this.hoverEl.getRegion().contains(e.getPoint()); },

    pRegion : function(e) { return this.popup.getRegion().contains(e.getPoint()); },

    delayCheck : function(e) {
        if (this.notFocused(e)) {
            this.delayHide();
        }
    },

    delayShow : function() {
        if (!this.showTimeout) {
            this.showTimeout = Ext4.defer(this.show, this.showDelay, this);
        }
    },

    delayHide : function() {
        this.cancelHide();
        this.cancelShow();
        this.hideTimeout = Ext4.defer(this.hide, this.hideDelay, this);
    },

    render : function() {
        if (!this.rendered) {
            var targetId = this.popup.id;
            var partConfig = {
                renderTo : targetId,
                partName : this.webPartName,
                frame    : 'none',
                partConfig : this.partConfig,
                failure  : function(response) {
                    if (response.status == 401) {
                        document.getElementById(targetId).innerHTML = '<div style="padding: 5px">You do not have permission to view this data. You have likely been logged out.'
                                + (this.loginUrl != null ? ' Please <a href="' + this.loginUrl + '">log in</a> again.' : ' Please <a href="#" onclick="location.reload();">reload</a> the page.') + "</div>";
                    }
                    else {
                        if (window.console && window.console.log) {
                            window.console.log(response);
                        }
                    }
                },
                scope    : this
            };

            if (this.webPartUrl) {
                partConfig.partUrl = this.webPartUrl;
            }

            var p = new LABKEY.WebPart(partConfig);
            p.render();
            this.rendered = true;
        }
    },

    show : function() {

        if (HoverNavigation.visiblePopup) {
            if (HoverNavigation.visiblePopup == this) {
                return;
            }
            HoverNavigation.visiblePopup.hide();
        }

        this.hoverEl.addCls(this.hoverCls);

        this.render();

        this.popup.show();
        this.popup.alignTo(this.hoverEl); // default: tl-bl
        HoverNavigation.visiblePopup = this;
    },

    hide : function() {
        if (this.fireEvent('beforehide', this) !== false) {
            this.hoverEl.removeCls(this.hoverCls);
            this.popup.hide();
            if (HoverNavigation.visiblePopup == this) {
                HoverNavigation.visiblePopup = false;
            }
        }
    }
});