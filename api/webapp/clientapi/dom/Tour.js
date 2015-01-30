/*
 * Copyright (c) 2014 LabKey Corporation
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

        var tours = 0;

        //
        // Private functions
        //
        /**
         * Run next tour in queue. Callback in show()
         */
        var _autoRun = function()
        {
            var shown = true;
            if (_next < _queue.length)
            {
                shown = autoShow(_tours[_queue[_next]]);
                _next++;
            }

            // If not shown because already seen, callback won't be triggered. Need to call here.
            if (!shown)
            {
                _autoRun();
            }
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
            var config = config || {};

            if (LABKEY.tours)
            {
                $.each(LABKEY.tours, function (tourId, tour)
                {
                    registerTour(tourId, config);
                    tours++;
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
            continueTour();

            _queue = [];
            _next = 0;

            $.each(_tours, function(key, tour) {
                _queue.push(key);
            });

            _autoRun();
        };

        //
        // Public Functions
        //
        /**
         * Show tour if it has never been shown before.
         * Conditionally loads hopscotch.js if the tour needs to be shown.
         */
        var autoShow = function(idOrConfig)
        {
            var config = _get(idOrConfig);
            if (!config)
                return false;
            if (_mode[config.id] == "1" && seen(config.id))
                return false;
            show(config);
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
            var hash = window.location.hash;
            if (hash && hash.charAt(0) == '#')
                hash = hash.substring(1);
            if (hash.substring(0, "tourstate:".length) != "tourstate:")
                return;
            var tourstate = hash.substring("tourstate:".length);
            if (-1 == tourstate.indexOf(":"))
                return;
            var id = tourstate.substring(0, tourstate.indexOf(":"));
            var step = tourstate.substring(tourstate.indexOf(":")+1);
            try
            {
                step = parseInt(step);
            }
            catch (ex)
            {
                return;
            }
            return resume(id, step);
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

        var registerTour = function(id, config)
        {
            LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL('tours', 'getTour'),
                jsonData : {id: id},
                success: LABKEY.Utils.getCallbackWrapper(function(result) { success.call(this, id, result); }, me, false),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), me.scope, true),
                scope: this
            });
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
         * Countinue tour if it is currently on the indicated step, useful for multi-page tours
         * Always loads hopscotch.js
         */
        var resume = function(idOrConfig, step)
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
         * Show tour starting at the beginning
         * Always loads hopscotch.js
         */
        var show = function(idOrConfig, step)
        {
            var config = _get(idOrConfig);
            if (!config)
                return;
            _initHopscotch(function()
            {
                hopscotch.listen("nextTour", _autoRun);
                hopscotch.startTour(config, step||0);
                markSeen(config.id);
            }, me);
        };

        /**
         * AJAX getTour success callback
         */
        var success = function(id, result)
        {
            var json = JSON.parse(result.Json);
            json.id = id;

            _evalTourOptions(json);
            _evalStepOptions(json);
            register(json, result.Mode);

            if (--tours == 0)
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