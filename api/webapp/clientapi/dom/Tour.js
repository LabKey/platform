/*
 * Copyright (c) 2014-2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
(function($) {
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

        var loads = 0;

        //
        // Private functions
        //
        /**
         * Run next tour in queue. Callback in show()
         */
        var _autoRun = function ()
        {
            if (_next < _queue.length)
            {
                _display(_tours[_queue[_next]], 0);
                _next++;
            }
            else
                resetRegistration();
        };

        var _display = function (config, step)
        {
            _initHopscotch(function()
            {
                hopscotch.listen("end", function() {
                    // 22390: Hopscotch doesn't actually end the tours until after this call
                    setTimeout(_autoRun, 1);
                });
                hopscotch.listen("close", function() {
                    resetRegistration();
                });
                hopscotch.startTour(config, step || 0);
                markSeen(config.id);
            }, me);
        };

        /**
         * Evaluate javascript in JSON defining tour options and step options
         */
        var _evalStepOptions = function(config)
        {
            config["steps"].forEach(function(step)
            {
                $.each(step, function(key, value)
                {
                    if (key == "target")
                    {
                        try
                        {
                            step[key] = document.querySelector(value);
                        }
                        catch (x)
                        {
                            if (x && x instanceof DOMException)
                            {
                                console.error('Tour provided invalid selector:', "'" + value + "'");
                            }
                        }
                    }
                    else if (stepFnOpts.indexOf(key) > -1)
                    {
                        var jsonFn = new Function("", "return " + value + ";");
                        step[key] = jsonFn.call();
                    }
                });
            });
        };


        var _evalTourOptions = function(config)
        {
            $.each(config, function(key, value)
            {
                if (tourFnOpts.indexOf(key) > -1)
                {
                    var jsonFn = new Function("", "return " + config[key] + ";");
                    config[key] = jsonFn.call();
                }
            });
        };

        var _get = function(idOrConfig)
        {
            var config = idOrConfig;
            if (LABKEY.Utils.isString(idOrConfig))
            {
                config = _tours[idOrConfig];
            }
            if (!config || !config.id)
            {
                console.warn("tour not found, or not configured properly: " + idOrConfig);
                return null;
            }
            return config;
        };

        var _init = function()
        {
            if (LABKEY.tours)
            {
                $.each(LABKEY.tours, function (tourId, tour)
                {
                    autoShow(tourId, 0);
                });
            }
        };

        var _initHopscotch = function(fn, scope)
        {
            LABKEY.requiresScript(_hopscotchSrc, true, fn, scope);
            LABKEY.requiresCss(_hopscotchCss);
        };

        /**
         * Queue up tours and start running
         */
        var _kickoffTours = function()
        {
            var continuing = continueTour();

            _queue = [];
            _next = 0;

            $.each(_tours, function(key, tour) {
                if(key != continuing)
                    _queue.push(key);
            });

            if (!continuing)
            {
                _autoRun();
            }
        };

        //
        // Public Functions
        //
        /**
         * Show tour if it has never been shown before.
         * Conditionally loads hopscotch.js if the tour needs to be shown.
         */
        var autoShow = function(id)
        {
            if (LABKEY.tours[id].mode == "0")
                return false;

            if (LABKEY.tours[id].mode == "1" && seen(id))
                return false;

            show(id, 0);
            return true;
        };

        /**
         * continueAtLocation() and continueTour() make a simple pattern for multi-page tours
         *
         * when leaving a page
         *
         * onNext: function()
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
        var continueAtLocation = function(href)
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

        /**
         * see continueAtLocation()
         */
        var continueTour = function()
        {
            var hash = window.location.hash, prefix = "tourstate:";
            if (hash && hash.charAt(0) == '#')
                hash = hash.substring(1);
            if (hash.substring(0, prefix.length) != prefix)
                return;
            var tourstate = hash.substring(prefix.length),
                endIdx = tourstate.indexOf(':');
            if (-1 != endIdx)
            {
                var id = tourstate.substring(0, endIdx);
                var step = tourstate.substring(endIdx + 1);
                return resume(id, parseInt(step));
            }
        };

        /**
         * AJAX getTour failure callback
         */
        var failure = function(id, result)
        {
            loads--;
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

        /**
         * Define a tour
         */
        var register = function(config, mode)
        {
            _tours[config.id] = config;
            _mode[config.id] = mode;
        };

        var reset = function()
        {
            localStorage.setItem(_localStorageProperty, "{}");
            _initHopscotch(function()
            {
                hopscotch.endTour(true,false);
            });
        };

        var resetRegistration = function()
        {
            _tours = {};
            _mode = {};;
        };

        /**
         * Countinue tour if it is currently on the indicated step, useful for multi-page tours
         * Always loads hopscotch.js
         */
        var resume = function(id, step)
        {
            var config = _get(id);
            if (config)
            {
                var testState = config.id + ":" + step;
                // peek into hopscotch state w/o loading hopscotch.js
                if (testState == sessionStorage.getItem(_hopscotchSessionProperty))
                {
                    _display(config, step);
                }
                return id;
            }
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
         * Show tour starting at step
         * Always loads hopscotch.js
         */
        var show = function(id, step)
        {
            loads++;

            LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL('tours', 'getTour'),
                jsonData : {id: id},
                success: LABKEY.Utils.getCallbackWrapper(function(result) { success.call(this, id, step, result); }, me, false),
                failure: LABKEY.Utils.getCallbackWrapper(function(result) { failure.call(this, id, result); }, me, false),
                scope: this
            });
        };

        /**
         * AJAX show() success callback
         */
        var success = function(id, step, result)
        {
            var json = JSON.parse(result.Json);
            json.id = id;

            _evalTourOptions(json);
            _evalStepOptions(json);
            register(json, result.Mode);

            if (--loads == 0)
            {
                _kickoffTours();
            }
        };

        LABKEY.Utils.onReady(_init);

        return {
            autoShow: autoShow,
            continueAtLocation: continueAtLocation,
            continueTour: continueTour,
            markSeen: markSeen,
            register: register,
            reset: reset,
            resume: resume,
            seen: seen,
            show: show
        }
    };

})(jQuery);