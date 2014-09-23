/*
 * Copyright (c) 2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

if (!('help' in LABKEY))
    LABKEY.help = {};

LABKEY.help.Tour =
{
    _hopscotchSessionProperty : 'hopscotch.tour.state',
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
        // peek into hopscotch state w/o loading hopscotch.js
        var hopscotchState = sessionStorage.getItem(this._hopscotchSessionProperty);
        if (hopscotchState == testState)
        {
            this._initHopscotch(function ()
            {
                hopscotch.startTour(config, step);
            }, this);
        }
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
    },


    /**
     * continueAtLocation() and continueTour() make a simple pattern for multi-page tours
     *
     * when leaving a page
     *
     * onNext: function()                                                                                                 t
     * {
     *     LABKEY.help.Tour.continueAtLocation("?pageId=study.DATA_ANALYSIS");
     * }
     *
     * and
     *
     * LABKEY.Utils.onReady(function(){
     *      LABKEY.help.Tour.continueTour();
     * })
     *
     * @param href
     */
    continueAtLocation : function(href)
    {
        // NOTE state is not updated yet
        // var hopscotchState = sessionStorage.getItem(this._hopscotchSessionProperty);
        if (!hopscotch.getCurrTour())
            window.location = href;
        var hopscotchState = hopscotch.getCurrTour().id + ":" + hopscotch.getCurrStepNum();

        var a = document.createElement("A");
        a.href = href;
        a.hash = 'tourstate:' + hopscotchState;
        window.location = a.href;
    },


    /** see continueAtLocation() */
    continueTour : function()
    {
        var hash = window.location.hash;
        if (hash && hash.charAt(0) == '#')
            hash = hash.substring(1);
        if (hash.substring(0,"tourstate:".length) != "tourstate:")
            return;
        var tourstate = hash.substring("tourstate:".length);
        if (-1 == tourstate.indexOf(":"))
            return;
        var id = tourstate.substring(0,tourstate.indexOf(":"));
        var step = tourstate.substring(tourstate.indexOf(":")+1);
        try
        {
            step = parseInt(step);
        }
        catch (ex)
        {
            return;
        }
        this.resume(id,step);
    }
};
