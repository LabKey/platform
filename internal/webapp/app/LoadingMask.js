/*
 * Copyright (c) 2014-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext.define('LABKEY.app.plugin.LoadingMask', {
    extend: 'Ext.AbstractPlugin',

    // array of mask config objects for this component with the following properties:
    // element, beginEvent, endEvent, plus defaults applied in this.applyDefaultConfig
    configs: [],

    // Defaults
    AS_BLOCKING_MASK: true,
    ITEMS_MASK_CLS: null,
    LOADING_DELAY: 500,
    PRODUCTION_GIF_PATH: null,

    init : function(component)
    {
        if (!Ext.isArray(this.configs) || this.configs.length < 1)
        {
            console.warn('No configs provided for loading mask plugin.');
        }

        if (!Ext.isDefined(component.loadingMaskPlugin))
        {
            component.loadingMaskPlugin = {configs: []};
        }

        // move the configs to the component.loadingMaskPlugin.configs array
        if (Ext.isArray(this.configs))
        {
            Ext.each(this.configs, function(maskConfig)
            {
                this.loadingMaskPlugin.configs.push(maskConfig);
            }, component);
        }

        // apply defaults to the mask configs
        Ext.each(component.loadingMaskPlugin.configs, this.applyDefaultConfig, this);

        // initialize the showLoadingTask and hideLoadingTask for each of the configs
        Ext.each(component.loadingMaskPlugin.configs, this.initShowLoadingTask, component);
        Ext.each(component.loadingMaskPlugin.configs, this.initHideLoadingTask, component);

        // attach the begin and end events to the specified component's element to show mask
        Ext.each(component.loadingMaskPlugin.configs, this.attachBeginEndEvents, this);
    },

    applyDefaultConfig : function(maskConfig)
    {
        Ext.applyIf(maskConfig, {
            blockingMask: this.AS_BLOCKING_MASK, // blocking mask will show the large spinner and mask content area for the component
            itemsMaskCls: this.ITEMS_MASK_CLS, // items mask will show small spinners for all items of the component (see Info Pane detail counts)
            loadingDelay: this.LOADING_DELAY, // the number of milliseconds to delay before showing the mask if the end event hasn't already happened
            productionGifPath: this.PRODUCTION_GIF_PATH
        });
    },

    attachBeginEndEvents : function(maskConfig)
    {
        if (Ext.isDefined(maskConfig.element))
        {
            if (Ext.isString(maskConfig.beginEvent))
            {
                maskConfig.element.on(maskConfig.beginEvent, this.showMask, maskConfig);
            }

            if (Ext.isString(maskConfig.endEvent))
            {
                maskConfig.element.on(maskConfig.endEvent, this.hideMask, maskConfig);
            }
        }
        else
        {
            console.warn('No element defined to attach loading mask events.');
        }
    },

    initShowLoadingTask : function(maskConfig)
    {
        // note: scope is the component that the mask is being attached to
        maskConfig.showLoadingTask = new Ext.util.DelayedTask(function()
        {
            if (maskConfig.blockingMask)
            {
                if (!maskConfig.maskCmp)
                {
                    maskConfig.maskCmp = new Ext.LoadMask(this, {cls: "large-spinner-mask", msg: " "});
                }

                maskConfig.maskCmp.show();
            }
            else
            {
                if (maskConfig.itemsMaskCls)
                {
                    if (this.getEl())
                    {
                        this.getEl().addCls(maskConfig.itemsMaskCls);
                    }
                }
                else if (maskConfig.productionGifPath)
                {
                    maskConfig.maskCmp = Ext.create('Ext.Component', {
                        renderTo: this.getEl(),
                        autoEl: {
                            tag: 'img', alt: 'loading',
                            height: 22, width: 22,
                            src: maskConfig.productionGifPath + 'med.gif'
                        }
                    });
                }
            }
        }, this);
    },

    initHideLoadingTask : function(maskConfig)
    {
        // note: scope is the component that the mask is being attached to
        maskConfig.hideLoadingTask = new Ext.util.DelayedTask(function()
        {
            maskConfig.showLoadingTask.cancel();

            if (maskConfig.maskCmp)
            {
                maskConfig.maskCmp.hide();
            }

            if (maskConfig.itemsMaskCls && this.getEl())
            {
                this.getEl().removeCls(maskConfig.itemsMaskCls);
            }
        }, this);
    },

    showMask : function()
    {
        // note: scope is the maskConfig from the component.loadingMaskPlugin.configs array
        this.showLoadingTask.delay(this.loadingDelay);
    },

    hideMask : function()
    {
        // note: scope is the maskConfig from the component.loadingMaskPlugin.configs array
        this.hideLoadingTask.delay();
    }
});