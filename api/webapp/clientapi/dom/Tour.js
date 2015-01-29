/*
 * Copyright (c) 2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

if (!('help' in LABKEY))
    LABKEY.help = {};

LABKEY.help.Tour = new function()
{
    var _hopscotchSessionProperty = 'hopscotch.tour.state';
    var _localStorageProperty = "LABKEY.tours.state";
    var _hopscotchSrc = "/hopscotch/js/hopscotch.js";
    var _hopscotchCss = "/hopscotch/css/hopscotch.css";
    var _tours = {};
    var _queue = [];
    var _next = 0;
    var _mode = {};

    var me = this;

    var stepFnOpts = ["onPrev", "onNext", "onShow", "onCTA"];
    var tourFnOpts = ["onNext", "onPrev", "onStart", "onEnd", "onClose", "onError"];

    var tours = 0;

    var _initHopscotch = function(fn,scope)
    {
        LABKEY.requiresScript(_hopscotchSrc, true, fn, scope);
        LABKEY.requiresCss(_hopscotchCss);
    };


    var _get = function(idOrConfig)
    {
        var config = idOrConfig;
        if (typeof idOrConfig == "string")
            config = _tours[idOrConfig];
        if (!config || !config.id)
        {
            console.warn("tour not found, or not configured properly: " + idOrConfig);
            return null;
        }
        return config;
    };


    /**
     * Define a tour
     */
    this.register = function(config, mode)
    {
        _tours[config.id] = config;
        _mode[config.id] = mode;
    };


    /**
     * Show tour starting at the beginning
     * Always loads hopscotch.js
     */
    this.show = function(idOrConfig, step)
    {
        var config = _get(idOrConfig);
        if (!config)
            return;
        _initHopscotch(function()
        {
            hopscotch.listen("nextTour",autoRun);
            hopscotch.startTour(config, step||0);
            markSeen(config.id);
        }, me);
    };


    /**
     * Countinue tour if it is currently on the indicated step, useful for multi-page tours
     * Always loads hopscotch.js
     */
    this.resume = function(idOrConfig, step)
    {
        var config = _get(idOrConfig);
        if (!config)
            return;
        var testState = config.id + ":" + step;
        // peek into hopscotch state w/o loading hopscotch.js
        var hopscotchState = sessionStorage.getItem(_hopscotchSessionProperty);
        if (hopscotchState == testState)
        {
            _initHopscotch(function ()
            {
                hopscotch.startTour(config, step);
            }, me);
        }
        return true;
    };


    /**
     * Show tour if it has never been shown before.
     * Conditionally loads hopscotch.js if the tour needs to be shown.
     */
    this.autoShow = function(idOrConfig)
    {
        var config = _get(idOrConfig);
        if (!config)
            return false;
        if (_mode[config.id] == "1" && seen(config.id))
            return false;
        this.show(config);
        return true;
    };


    var seen = function(id)
    {
        // use one item for all tours, this is a little more complicated, but makes it easier to reset state
        var state = {};
        var v = localStorage.getItem(_localStorageProperty);
        if (v)
            state = LABKEY.Utils.decode(v);
        return "seen" == state[id];
    };


    /**
     * Mark tour as seen so autoShow() will no longer show this tour
     */
    var markSeen = function(id)
    {
        var state = {};
        var v = localStorage.getItem(_localStorageProperty);
        if (v)
            state = LABKEY.Utils.decode(v);
        state[id] = "seen";
        localStorage.setItem(_localStorageProperty, LABKEY.Utils.encode(state));
    };


    var reset = function()
    {
        localStorage.setItem(_localStorageProperty, "{}");
        _initHopscotch(function()
        {
            hopscotch.endTour(true,false);
        });
    };


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
    this.continueAtLocation = function(href)
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
    };


    /** see continueAtLocation() */
    this.continueTour = function()
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
        return this.resume(id,step);
    };

    /**
     * Evaluate javascript in JSON defining tour options and step options
     */
    var evalTourOptions = function(config)
    {
        for(var key in config)
        {
            if(tourFnOpts.indexOf(key) > -1)
            {
                var jsonFn = new Function("", "return " + config[key] + ";");
                config[key] = jsonFn.call();
            }
        }
    };

    var evalStepOptions = function(config)
    {
        config["steps"].forEach( function (step)
        {
            for (var key in step)
            {
                if (stepFnOpts.indexOf(key) > -1)
                {
                    var jsonFn = new Function("", "return " + step[key] + ";");
                    step[key] = jsonFn.call();
                }
            }
        });
    };

    /**
     * Run next tour in queue. Callback in show()
     */
    var autoRun = function()
    {
        var shown = true;
        if(_next < _queue.length)
        {
            shown = me.autoShow(_tours[_queue[_next]]);
            _next++;
        }

        // If not shown because already seen, callback won't be triggered. Need to call here.
        if(!shown)
            autoRun();
    };


    /**
     * Queue up tours and start running
     */
    var kickoffTours = function()
    {
        me.continueTour();

        _queue = [];
        _next = 0;

        for(var key in _tours)
        {
            _queue.push(key);
        }

        autoRun();
    };

    /**
     * AJAX getTour success callback
     */
    var success = function(result)
    {
        var json;
        var useEval = false;
        if (useEval)
        {
            eval("window.__eval__=(" + result.Json + ")");
            json = window.__eval__;
        }
        else
        {
            json = JSON.parse(result.Json);
        }
        evalTourOptions(json);
        evalStepOptions(json);
        me.register(json, result.Mode);

        if(--tours == 0)
        {
            kickoffTours();
        }
    };

    var registerTour = function(id, config)
    {
        var dataObject = {};
        dataObject.id = id;
        var myUrl = LABKEY.ActionURL.buildURL('tours', 'getTour', null, null);

        LABKEY.Ajax.request({
            url: myUrl,
            scope: this,
            jsonData : dataObject,
            headers : {
                'Content-Type' : 'application/json'},
            success: LABKEY.Utils.getCallbackWrapper(success, me, false),
            failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), me.scope, true)
        });
    };

    this.init = function()
    {
        var config = config || {};

        for (var key in LABKEY.tours)
        {
            registerTour(key, config);
            tours++;
        }
    };


    LABKEY.Utils.onReady(function()
    {
        LABKEY.help.Tour.init();
    });
};
