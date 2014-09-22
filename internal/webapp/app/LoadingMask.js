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

        this.showLoadingMaskTask = new Ext.util.DelayedTask(function(){
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

                    if (this.itemsMaskCls)
                    {
                        if (this.getEl())
                            this.getEl().addCls(this.itemsMaskCls);
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

        // attach the begin events to the specified component to show mask
        if (this.beginConfig && this.beginConfig.component && Ext.isArray(this.beginConfig.events)) {
            Ext.each(Ext.Array.unique(this.beginConfig.events), function(eventName){
                this.beginConfig.component.on(eventName, component.showMask, component);
            }, this);
        }

        // attach the end events to the specified component to hide mask
        if (this.endConfig && this.endConfig.component && Ext.isArray(this.endConfig.events)) {
            Ext.each(Ext.Array.unique(this.endConfig.events), function(eventName){
                this.endConfig.component.on(eventName, component.hideMask, component);
            }, this);
        }
    },

    showMask : function() {
        this.maskingLock = true;
        this.showLoadingMaskTask.delay(this.loadingDelay);
    },

    hideMask : function() {
        if (this.maskCmp)
            this.maskCmp.hide();

        if (this.itemsMaskCls && this.getEl())
            this.getEl().removeCls(this.itemsMaskCls);

        this.maskingLock = false;
    }
});