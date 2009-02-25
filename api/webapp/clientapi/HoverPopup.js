/**
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
    var dh = Ext.DomHelper;
    var popup = dh.insertAfter("menubar",
            {id:config.hoverElem + "$Menu", tag:"div", cls:"labkey-webpart-menu",
                children:[{tag:"div", cls:"loading-indicator", style:"width:100;height:100"}]});
    this.extPopup = new Ext.Layer({shadow:true,shadowOffset:8},popup);
    this.webPartName = config.webPartName;
    this.partConfig = config.partConfig || {};

    this.extElem.hover(function(e) {
            this.cancelHide();
            if (LABKEY.HoverPopup._visiblePopup)
            {
                if (LABKEY.HoverPopup._visiblePopup == this)
                    return;
                else
                {
                    LABKEY.HoverPopup._visiblePopup.hideFn();
                }
            }
            this.extElem.addClass("selected");
            console.log(this.extElem.id  + " extPopup.y: " + this.extPopup.getY());
            if (!this.rendered) {
                var p = new LABKEY.WebPart({
                partName: this.webPartName,
                renderTo: this.extPopup.id,
                frame: 'none',
                partConfig: this.partConfig,
                errorCallback:function(err) {if (window.console && window.console.log) { window.console.log(err);}},
                successCallback:function() {
                            var x = function() {this.extPopup.enableShadow(true);};
                            x.defer(100, this);},
                scope:this
                });
                p.render();
                this.rendered = true;
            }
            this.extPopup.show();
            this.extPopup.alignTo(this.extElem, "tl-bl");
            this.extPopup.setXY([this.extPopup.getX() - 20, this.extPopup.getY()- this.extPopup.getBorderWidth('t')]);
            this.extPopup.enableShadow(true);
            LABKEY.HoverPopup._visiblePopup = this;

        }, function (e) {
            if (!this.extElem.getRegion().contains(e.getPoint()) && !this.extPopup.getRegion().contains(e.getPoint()))
            {
                this.delayHide();
            }
        }, this);
        this.extPopup.hover(function(e) {
            this.cancelHide();
        }, function (e) {
            if (!this.extPopup.getRegion().contains(e.getPoint()) && !this.extElem.getRegion().contains(e.getPoint()))
            {
                this.delayHide();
            }
        }, this);
        //Update the shadow on click, since we sometimes cause the change of the inner div
        this.extPopup.on("click", function(e) {this.extPopup.enableShadow(true)}, this);
};

Ext.extend(LABKEY.HoverPopup,  Ext.util.Observable, {
    //private
    cancelHide: function() {
        if (this.hideTimeout)
        {
            clearTimeout(this.hideTimeout);
            delete this.hideTimeout;
        }
    },

    //private
    delayHide: function() {
        if (this.hideTimeout)
            clearTimeout(this.hideTimeout);
        this.hideTimeout = this.hideFn.defer(200, this);
    },

    //private
    hideFn: function () {
        this.extElem.removeClass("selected");
        this.extPopup.hide();
        if (LABKEY.HoverPopup._visiblePopup == this)
            LABKEY.HoverPopup._visiblePopup = null;
    }
});

