/*
 * Copyright (c) 2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

if (!('help' in LABKEY))
    LABKEY.help = {};

LABKEY.help.Tour =
{
    _localStorageProperty : "LABKEY.tours.state",
    _hopscotchSrc : "/hopscotch/js/hopscotch.js",
    _hopscotchCss : "/hopscotch/css/hopscotch.css",
    _tours : {},

    _initHopscotch : function(fn,scope)
    {
        LABKEY.requiresScript(this._hopscotchSrc, true, fn, scope);
        LABKEY.requiresCss(this._hopscotchCss);
    },


    _get : function(idOrConfig)
    {
        var config = idOrConfig;
        if (typeof idOrConfig == "string")
            config = this._tours[idOrConfig];
        if (!config || !config.id)
        {
            console.warn("tour not found, or not configured properly: " + idOrConfig);
            return null;
        }
        return config;
    },


    /**
     * Define a tour
     */
    register : function(config)
    {
        this._tours[config.id] = config;
    },


    /**
     * Show tour starting at the beginning
     * Always loads hopscotch.js
     */
    show : function(idOrConfig, step)
    {
        var config = this._get(idOrConfig);
        if (!config)
            return;
        this._initHopscotch(function()
        {
            hopscotch.startTour(config, step||0);
            this.markSeen(config.id);
        }, this);
    },


    /**
     * Countinue tour if it is currently on the indicated step, useful for multi-page tours
     * Always loads hopscotch.js
     */
    resume : function(idOrConfig, step)
    {
        var config = this._get(idOrConfig);
        if (!config)
            return;
        var testState = config.id + ":" + step;
        this._initHopscotch(function()
        {
            if (hopscotch.getState() == testState)
                hopscotch.startTour(config, step);
        },this);
    },


    /**
     * Show tour if it has never been shown before.
     * Conditionally loads hopscotch.js if the tour needs to be shown.
     */
    autoShow : function(idOrConfig)
    {
        var config = this._get(idOrConfig);
        if (!config)
            return;
        if (this.seen(config.id))
            return;
        this.show(config);
    },


    seen : function(id)
    {
        // use one item for all tours, this is a little more complicated, but makes it easier to reset state
        var state = {};
        var v = localStorage.getItem(this._localStorageProperty);
        if (v)
            state = LABKEY.Utils.decode(v);
        return "seen" == state[id];
    },


    /**
     * Mark tour as seen so autoShow() will no longer show this tour
     */
    markSeen : function(id)
    {
        var state = {};
        var v = localStorage.getItem(this._localStorageProperty);
        if (v)
            state = LABKEY.Utils.decode(v);
        state[id] = "seen";
        localStorage.setItem(this._localStorageProperty, LABKEY.Utils.encode(state));
    },


    reset : function()
    {
        localStorage.setItem(this._localStorageProperty, "{}");
        this._initHopscotch(function()
        {
            hopscotch.endTour(true,false);
        });
    }
};