/*
 * Copyright (c) 2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext.define('LABKEY.app.plugin.LoadingMask', {
    extend: 'Ext.AbstractPlugin',

    blockingMask: true, // blocking mask will show the large spinner and mask content area for the component

    itemsMaskCls: null, // items mask will show small spinners for all items of the component (see Info Pane detail counts)

    loadingDelay: 500, // the number of milliseconds to delay before showing the mask if the end event hasn't already happened

    beginConfig: null, // defines the component and events for that component that will trigger the showMask

    endConfig: null, // defines the component and events for that component that will trigger the hideMask

    maskingLock: false,

    productionGifPath: null,

    init : function(component) {

        this.showLoadingMaskTask = new Ext.util.DelayedTask(function(itemsMaskCls){
            if (this.maskingLock)
            {
                if (this.blockingMask)
                {
                    if (!this.maskCmp)
                        this.maskCmp = new Ext.LoadMask(this, { cls: "large-spinner-mask", msg:" " });
                    this.maskCmp.show();
                }
                else
                {
                    if (itemsMaskCls)
                    {
                        if (this.getEl())
                            this.getEl().addCls(itemsMaskCls);
                    }
                    else if (this.productionGifPath)
                    {
                        this.maskCmp = Ext.create('Ext.Component', {
                            renderTo: this.getEl(),
                            autoEl: {
                                tag: 'img', alt: 'loading',
                                height: 22, width: 22,
                                src: LABKEY.contextPath + this.productionGifPath + 'med.gif'
                            }
                        });
                    }
                }
            }
        }, component);

        Ext.override(component, {
            productionGifPath: this.productionGifPath,
            blockingMask: this.blockingMask,
            itemsMaskCls: this.itemsMaskCls,
            loadingDelay: this.loadingDelay,
            showLoadingMaskTask: this.showLoadingMaskTask,
            showMask: this.showMask,
            hideMask: this.hideMask
        });

        // attach the begin events to the specified component to show mask,
        // note: beginConfig can be a single config or an array of configs
        if (this.beginConfig && !Ext.isArray(this.beginConfig))
        {
            this.beginConfig = [this.beginConfig];
        }
        Ext.each(this.beginConfig, function(beginCfg)
        {
            if (beginCfg && beginCfg.component && Ext.isArray(beginCfg.events))
            {
                Ext.each(Ext.Array.unique(beginCfg.events), function(eventName)
                {
                    beginCfg.component.on(eventName, function()
                    {
                        component.showMask(beginCfg.itemsMaskCls || this.itemsMaskCls);
                    }, component);
                }, this);
            }
        }, this);

        // attach the end events to the specified component to hide mask,
        // note: endConfig can be a single config or an array of configs
        if (this.endConfig && !Ext.isArray(this.endConfig))
        {
            this.endConfig = [this.endConfig];
        }
        Ext.each(this.endConfig, function(endCfg) {
            if (endCfg && endCfg.component && Ext.isArray(endCfg.events))
            {
                Ext.each(Ext.Array.unique(endCfg.events), function(eventName)
                {
                    endCfg.component.on(eventName, function()
                    {
                        component.hideMask(endCfg.itemsMaskCls || this.itemsMaskCls)
                    }, component);
                }, this);
            }
        }, this);
    },

    showMask : function(itemsMaskCls) {
        this.maskingLock = true;
        this.showLoadingMaskTask.delay(this.loadingDelay, null, null, [itemsMaskCls]);
    },

    hideMask : function(itemsMaskCls) {
        if (this.maskCmp)
            this.maskCmp.hide();

        if (itemsMaskCls && this.getEl())
            this.getEl().removeCls(itemsMaskCls);

        this.maskingLock = false;
    }
});