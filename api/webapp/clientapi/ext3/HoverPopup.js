/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2009-2016 LabKey Corporation
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p/>
 */

/**
 * @private
 * @namespace LabKey Popup Menu Web Class.
 * This class allows an element to display a popup menu based on a LabKey webpart
 * The element will highlight on hover over.
 *
 * @constructor
 * @param {Object} config Describes the HoverPopup's properties.
 * @param {String} config.hoverElem Element or element id that should trigger the popup
 * @param {String} config.webPartName Name of webPart to load on hover
 * @param {Object} config.partConfig PartConfig for the webpart same as used in LABKEY.WebPart
 *
 */
LABKEY.HoverPopup = function(config)
{
    Ext.apply(this, config);
    LABKEY.HoverPopup.superclass.constructor.call(this);

    this.extElem = Ext.get(config.hoverElem);
    if (!this.extElem) {
        return;             // Custom Menu Bar may be hidden in which case element is null
    }

    this.showDelay = 150;

    var popup = Ext.DomHelper.insertAfter("menubar",
            {id:config.hoverElem + "_menu", tag:"div", cls:"labkey-webpart-menu",
                children:[{tag:"div", cls:"loading-indicator", style:"width:100px;height:100px"}]});
    this.extPopup = new Ext.Layer({shadow:true,shadowOffset:8,zindex:1000},popup);
    this.webPartName = config.webPartName;
    this.partConfig = config.partConfig || {};

    this.extElem.hover(function(e) {
        this.cancelHide();

        // show immediately if we already have a menu up
        // Otherwise, make sure that someone hovers for a while
        LABKEY.HoverPopup._visiblePopup ? this.showFn() : this.delayShow();

    }, this.delayCheck, this);

    this.extPopup.hover(this.cancelHide, this.delayCheck, this);

    //Update the shadow on click, since we sometimes cause the change of the inner div
    this.extPopup.on("click", function(e) {this.extPopup.enableShadow(true)}, this);
};

Ext.extend(LABKEY.HoverPopup,  Ext.util.Observable, {
    //private
    cancelHide: function() {
        if (this.hideTimeout) {
            clearTimeout(this.hideTimeout);
            delete this.hideTimeout;
        }
    },

    //private
    delayCheck : function(e) {
        if (!this.extElem.getRegion().contains(e.getPoint()) && !this.extPopup.getRegion().contains(e.getPoint())) {
            this.delayHide();
        }
    },

    //private
    delayHide: function() {
        if (this.hideTimeout) {
            clearTimeout(this.hideTimeout);
        }
        if (this.showTimeout) {
            clearTimeout(this.showTimeout);
            delete this.showTimeout;
        }
        this.hideTimeout = this.hideFn.defer(200, this);
    },

    //private
    delayShow: function() {
        if (!this.showTimeout) {
            this.showTimeout = this.showFn.defer(this.showDelay, this);
        }
    },

    //private
    showFn: function() {
        if (LABKEY.HoverPopup._visiblePopup) {
            if (LABKEY.HoverPopup._visiblePopup == this) {
                return;
            }
            else {
                LABKEY.HoverPopup._visiblePopup.hideFn();
            }
        }
        this.extElem.addClass("selected");
        //console.log(this.extElem.id  + " extPopup.y: " + this.extPopup.getY());
        if (!this.rendered) {
            var p = new LABKEY.WebPart({
                partName   : this.webPartName,
                renderTo   :  this.extPopup.id,
                frame      : 'none',
                partConfig : this.partConfig,
                failure : function(err) {if (window.console && window.console.log) { window.console.log(err);}},
                success : function() {
                    var x = function() {this.extPopup.enableShadow(true);};
                    x.defer(100, this);
                },
                scope:this
            });
            p.render();
            this.rendered = true;
            this.showDelay = 100; // show more quickly
        }
        this.extPopup.show();
        this.extPopup.constrain = false;
        this.extPopup.alignTo(this.extElem, "tl-bl");
        this.extPopup.setXY([Math.max(0 - this.extPopup.getBorderWidth('t'), this.extPopup.getX() - 1), this.extPopup.getY()- this.extPopup.getBorderWidth('t')]);
        this.extPopup.enableShadow(true);
        LABKEY.HoverPopup._visiblePopup = this;        
    },

    //private
    hideFn: function () {
        this.extElem.removeClass("selected");
        this.extPopup.hide();
        if (LABKEY.HoverPopup._visiblePopup == this) {
            LABKEY.HoverPopup._visiblePopup = null;
        }
    }
});

