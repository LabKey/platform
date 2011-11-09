/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.requiresCss('GuidedTip.css', true);

/* Initial Design work of Guided Tooltips */
Ext.form.GuidedTip = Ext.extend(Ext.QuickTip, {

    // private
    tagConfig : {
        namespace : "ext",
        attribute : "gtip",
        width : "qwidth",
        target : "target",
        title : "gtitle",
        hide : "hide",
        cls : "qclass",
        align : "qalign",
        anchor : "anchor"
    },

    autoHeight : true,
//    height : 200,
//    width  : 200,
    baseCls : 'g-tip',
    showDelay : 0, // this tip type is click based

    // private
    initComponent : function() {
        Ext.form.GuidedTip.superclass.initComponent.call(this);
    },

    getTipCfg: function(e) {
        var t = e.getTarget(),
                ttp,
                cfg;
        if(this.interceptTitles && t.title && Ext.isString(t.title)){
            ttp = t.title;
            t.gtip = ttp;
            t.removeAttribute("title");
            e.preventDefault();
        }else{
            cfg = this.tagConfig;
            ttp = t.gtip || Ext.fly(t).getAttribute(cfg.attribute, cfg.namespace);
        }
        return ttp;
    },

    /**
     * Binds this GuidedTip to the specified element. The guidedtip will be displayed when the mouse clicks the element.
     * @param {Mixed} t The Element, HtmlElement, or ID of an element to bind to
     */
    initTarget : function(target){
        var t;
        if((t = Ext.get(target))){
            if(this.target){
                var tg = Ext.get(this.target);
//                this.mun(tg, 'mouseover', this.onTargetOver, this);
                this.mun(tg, 'mouseup', this.onTargetOver, this);
                this.mun(tg, 'mouseout', this.onTargetOut, this);
                this.mun(tg, 'mousemove', this.onMouseMove, this);
            }
            this.mon(t, {
//                mouseover : this.onTargetOver,
                mouseup : this.onTargetOver,
                mouseout: this.onTargetOut,
                mousemove: this.onMouseMove,
                scope: this
            });
            this.target = t;
        }
        if(this.anchor){
            this.anchorTarget = this.target;
        }
    }
});

Ext.GuidedTips = function(){
    var tip,
        disabled = false;

    return {
        /**
         * Initialize the global GuidedTips instance and prepare any guided tips.
         * @param {Boolean} autoRender True to render the GuidedTips container immediately to preload images. (Defaults to true)
         */
        init : function(autoRender){
            if(!tip){
                if(!Ext.isReady){
                    Ext.onReady(function(){
                        Ext.GuidedTips.init(autoRender);
                    });
                    return;
                }
                tip = new Ext.form.GuidedTip({
                    elements:'header,body',
                    disabled: disabled
                });
                if(autoRender !== false){
                    tip.render(Ext.getBody());
                }
            }
        },

        // Protected method called by the dd classes
        ddDisable : function(){
            // don't disable it if we don't need to
            if(tip && !disabled){
                tip.disable();
            }
        },

        // Protected method called by the dd classes
        ddEnable : function(){
            // only enable it if it hasn't been disabled
            if(tip && !disabled){
                tip.enable();
            }
        },

        /**
         * Enable guided tips globally.
         */
        enable : function(){
            if(tip){
                tip.enable();
            }
            disabled = false;
        },

        /**
         * Disable guided tips globally.
         */
        disable : function(){
            if(tip){
                tip.disable();
            }
            disabled = true;
        },

        /**
         * Returns true if guided tips are enabled, else false.
         * @return {Boolean}
         */
        isEnabled : function(){
            return tip !== undefined && !tip.disabled;
        },

        /**
         * Gets the single {@link Ext.form.GuidedTip GuidedTip} instance used to show tips from all registered elements.
         * @return {Ext.form.GuidedTip}
         */
        getGuidedTip : function(){
            return tip;
        },

        /**
         * Configures a new guided tip instance and assigns it to a target element.  See
         * {@link Ext.form.GuidedTip#register} for details.
         * @param {Object} config The config object
         */
        register : function(){
            tip.register.apply(tip, arguments);
        },

        /**
         * Removes any registered guided tip from the target element and destroys it.
         * @param {String/HTMLElement/Element} el The element from which the guided tip is to be removed.
         */
        unregister : function(){
            tip.unregister.apply(tip, arguments);
        },

        /**
         * Alias of {@link #register}.
         * @param {Object} config The config object
         */
        tips : function(){
            tip.register.apply(tip, arguments);
        }
    };
}();