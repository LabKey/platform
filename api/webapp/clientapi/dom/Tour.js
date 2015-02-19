/*
 * Copyright (c) 2014-2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
(function ($)
{
    if (!('help' in LABKEY))
        LABKEY.help = {};

    LABKEY.help.Tour = new function ()
    {
        var _hopscotchSessionProperty = 'hopscotch.tour.state',
            _localStorageProperty = "LABKEY.tours.state",
            _tours = {},
            _continue = {},
            _queue = [],
            _next = 0,
            loads = 0,
            me = this,
            stepFnOpts = ["onPrev", "onNext", "onShow", "onCTA"],
            tourFnOpts = ["onNext", "onPrev", "onStart", "onEnd", "onClose", "onError"],
            _modeOff = "off",
            _modeRunOnce = "runOnce",
            _modeRunAlways = "runAlways",
            modes = [_modeOff, _modeRunOnce, _modeRunAlways];

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
            _initHopscotch(function ()
            {
                hopscotch.listen("end", function ()
                {
                    // 22390: Hopscotch doesn't actually end the tours until after this call
                    setTimeout(_autoRun, 1);
                });
                hopscotch.listen("close", function ()
                {
                    resetRegistration();
                });
                if (LABKEY.Utils.isString(step))
                    step = parseInt(step);
                hopscotch.startTour(config, step || 0);
                markSeen(config.id);
            }, me);
        };

        /**
         * Evaluate javascript in JSON defining tour options and step options
         */
        var _evalStepOptions = function (config)
        {
            config["steps"].forEach(function (step)
            {
                $.each(step, function (key, value)
                {
                    if (key == "target")
                    {
                        try
                        {
                            step[key] = document.querySelector(value);
                        }
                        catch (x)
                        {
                            if (x instanceof DOMException)
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


        var _evalTourOptions = function (config)
        {
            $.each(config, function (key, value)
            {
                if (tourFnOpts.indexOf(key) > -1)
                {
                    var jsonFn = new Function("", "return " + config[key] + ";");
                    config[key] = jsonFn.call();
                }
            });
        };

        var _get = function (idOrConfig)
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

        // Get multipage tour info from URL
        var _getContinue = function ()
        {
            var config = {};
            var hash = window.location.hash, prefix = "tourstate:";
            if (hash && hash.charAt(0) == '#')
                hash = hash.substring(1);
            if (hash.substring(0, prefix.length) != prefix)
                return config;
            var tourstate = hash.substring(prefix.length),
                    endIdx = tourstate.indexOf(':');
            if (-1 != endIdx)
            {
                config.id = tourstate.substring(0, endIdx);
                config.step = tourstate.substring(endIdx + 1);
            }
            return config;
        };

        var _init = function ()
        {
            var config = _getContinue();

            if (LABKEY.tours)
            {
                $.each(LABKEY.tours, function (tourId, tour)
                {
                    if (!$.isEmptyObject(config) && config.id == tourId)
                        autoShow(tourId, config.step);
                    else
                        autoShow(tourId, 0);
                });
            }
        };

        var _initHopscotch = function (fn, scope)
        {
            var script = "/hopscotch/js/hopscotch" + (LABKEY.devMode ? "" : ".min") + ".js";
            var style = "/hopscotch/css/hopscotch" + (LABKEY.devMode ? "" : ".min") + ".css";
            LABKEY.requiresScript(script, true, fn, scope);
            LABKEY.requiresCss(style);
        };

        /**
         * Queue up tours and start running
         */
        var _kickoffTours = function ()
        {
            if (!$.isEmptyObject(_continue))
                resume(_continue.id, _continue.step);

            _queue = [];
            _next = 0;

            $.each(_tours, function (key, tour)
            {
                if (key != _continue.id)
                    _queue.push(key);
            });

            if ($.isEmptyObject(_continue))
            {
                _autoRun();
            }
            _continue = {};
        };

        //
        // Public Functions
        //
        /**
         * Show tour if it has never been shown before.
         * Conditionally loads hopscotch.js if the tour needs to be shown.
         */
        var autoShow = function (id, step)
        {
            if (modes[parseInt(LABKEY.tours[id].mode)] == _modeOff && step < 1)
                return false;

            if (modes[parseInt(LABKEY.tours[id].mode)] == _modeRunOnce && seen(id) && step < 1)
                return false;

            show(id, step);
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
        var continueAtLocation = function (href)
        {
            var context = window.location.pathname.split("/")[1];

            if (href.charAt(0) != "/")
                href = "/" + href;

            // NOTE state is not updated yet
            // var hopscotchState = sessionStorage.getItem(this._hopscotchSessionProperty);
            if (!hopscotch.getCurrTour())
                window.location = window.location.origin + "/" + context + href;
            var hopscotchState = hopscotch.getCurrTour().id + ":" + hopscotch.getCurrStepNum();

            var a = document.createElement("A");
            a.href = window.location.origin + "/" + context + href;
            a.hash = 'tourstate:' + hopscotchState;
            window.location = a.href;
        };

        /**
        * see continueAtLocation()
        */
        var continueTour = function ()
        {
            var config = _getContinue();
            if (!$.isEmptyObject(config))
                return resume(id, parseInt(step));
        };

        /**
         * AJAX getTour failure callback
         */
        var failure = function (id, result)
        {
            loads--;
        };

        /**
         * Mark tour as seen so autoShow() will no longer show this tour
         */
        var markSeen = function (id)
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
        var register = function (config, mode, step)
        {
            if ( config.steps.length > 0)
            {
                if (step > 0)
                {
                    _continue.id = config.id;
                    _continue.step = step;
                }
                _tours[config.id] = config;
            }
        };

        var reset = function ()
        {
            localStorage.setItem(_localStorageProperty, "{}");
            _initHopscotch(function ()
            {
                hopscotch.endTour(true, false);
            });
        };

        var resetRegistration = function ()
        {
            _tours = {};
        };

        /**
         * Countinue tour if it is currently on the indicated step, useful for multi-page tours
         * Always loads hopscotch.js
         */
        var resume = function (id, step)
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

        var seen = function (id)
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
        var show = function (id, step)
        {
            loads++;

            LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL('tours', 'getTour'),
                jsonData: {id: id},
                success: LABKEY.Utils.getCallbackWrapper(function (result)
                {
                    success.call(this, id, step, result);
                }, me, false),
                failure: LABKEY.Utils.getCallbackWrapper(function (result)
                {
                    failure.call(this, id, result);
                }, me, false),
                scope: this
            });
        };

        /**
         * AJAX show() success callback
         */
        var success = function (id, step, result)
        {
            var json = JSON.parse(result.Json);
            json.id = id;

            _evalTourOptions(json);
            _evalStepOptions(json);
            register(json, result.Mode, step);

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