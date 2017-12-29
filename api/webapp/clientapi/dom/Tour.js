/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2014-2016 LabKey Corporation
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p/>
 */
(function ($)
{
    if (!LABKEY.help)
    {
        LABKEY.help = {};
    }

    /**
     * @private
     * @namespace API that provides the capability to run a Tour on the page highlighting different areas and aspects
     *      to show users. <p/>
     *            <p>Additional Documentation:
     *              <ul>
     *                  <li><span>Not yet provided.</span></li>
     *              </ul>
     *           </p>
     */
    LABKEY.help.Tour = new function ()
    {
        var ID_PREFIX = 'tour-',
            URL_PREFIX = 'tourstate';

        var _hopscotchSessionProperty = 'hopscotch.tour.state',
            _localStorageProperty = "LABKEY.tours.state",
            _tours = {},
            _continue = {},
            _queue = [],
            _next = 0,
            loads = 0,
            me = this,
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
        };

        var _autoShowFromDb = function (id, step)
        {
            if (LABKEY.tours[id].mode != undefined)
            {
                var modeIndex = parseInt(LABKEY.tours[id].mode);

                if (modeIndex > modes.length)
                {
                    console.warn("Invalid mode value. TourId: " + id + ", Mode: " + modeIndex);
                    return false;
                }
                if (modes[modeIndex] == _modeOff && step < 1)
                    return false;

                if (modes[modeIndex] == _modeRunOnce && seen(id) && step < 1)
                    return false;

                _load(id, step);
                return true;
            }
            console.warn("Tour mode not found. TourId: " + id);
        };

        // https://github.com/linkedin/hopscotch/commit/948eff6fa52cb71b7e6688735ae7089dc68873a3
        var _decodeId = function(id)
        {
            if (id.indexOf(ID_PREFIX) === 0) {
                return id.split(ID_PREFIX)[1];
            }

            console.warn('Tour: unable to decode id "' + id + '". Expected to start with "' + ID_PREFIX + '".');
            return id;
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
                if (LABKEY.Utils.isString(step))
                    step = parseInt(step);
                var clone = $.extend({}, config, {
                    id: _encodeId(config.id)
                });
                hopscotch.startTour(clone, step || 0);
                markSeen(config.id);
            }, me);
        };

        // https://github.com/linkedin/hopscotch/commit/948eff6fa52cb71b7e6688735ae7089dc68873a3
        var _encodeId = function (id)
        {
            return ID_PREFIX + id;
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
            var config = {},
                hash = window.location.hash,
                prefix = URL_PREFIX + ':';

            if (hash && hash.indexOf('#') === 0)
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
                        _autoShowFromDb(tourId, config.step);
                    else
                        _autoShowFromDb(tourId, 0);
                });
            }
        };

        var _initHopscotch = function (fn, scope)
        {
            var script = "/hopscotch/js/hopscotch" + (LABKEY.devMode ? "" : ".min") + ".js";
            var style = "/hopscotch/css/hopscotch" + (LABKEY.devMode ? "" : ".min") + ".css";
            LABKEY.requiresScript(script, fn, scope);
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

        /**
         * Show tour starting at step
         * Always loads hopscotch.js
         */
        var _load = function(id, step)
        {
            loads++;

            LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL('tours', 'getTour'),
                jsonData: {id: id},
                success: LABKEY.Utils.getCallbackWrapper(function(result)
                {
                    loads--;
                    _parseAndRegister.call(this, id, step, result);

                    if (loads == 0)
                    {
                        _kickoffTours();
                    }
                }, me, false),
                failure: LABKEY.Utils.getCallbackWrapper(function(result)
                {
                    loads--;
                }, me, false),
                scope: this
            });
        };

        /**
         * AJAX _load() success callback
         */
        var _parseAndRegister = function(id, step, result)
        {
            var tour = JSON.parse(result.json);
            tour.id = id;

            var realSteps = [];
            $.each(tour.steps, function(i, step)
            {
                var real = eval(JSON.parse(step.step));
                real.target = step.target;

                if (!real.placement) {
                    real.placement = 'bottom'; // required by hopscotch
                }
                realSteps.push(real);
            });

            if (window['_stepcontent'])
            {
                delete window['_stepcontent'];
            }

            tour.steps = realSteps;
            _register(tour, step);
        };

        /**
         * @param config
         * @param {number} step
         * @private
         */
        var _register = function(config, step)
        {
            if (!config.id)
                throw "'id' is required to define a tour.";
            if (!config.steps || !LABKEY.Utils.isArray(config.steps))
                throw "'steps' is required to be an Array to define a tour.";

            if (config.steps.length > 0)
            {
                if (step > 0)
                {
                    _continue.id = config.id;
                    _continue.step = step;
                }

                if (!_tours.hasOwnProperty(config.id))
                {
                    _tours[config.id] = config;
                }
            }
        };

        //
        // Public Functions
        //
        /**
         * Show tour if it has never been shown before. Conditionally loads hopscotch.js if the tour needs to be shown.
         * @param id
         * @returns {boolean}
         * @private
         */
        var autoShow = function (id)
        {
            if (seen(id))
            {
                return false;
            }

            show(id, 0);
            return true;
        };

        /**
         * continueAtLocation() and continueTour() make a simple pattern for multi-page tours
         * @param {string} href
         * @private
         * @example
<pre name="code">
var tourConfig = {
    // ...
    onNext: function() {
        var url = LABKEY.ActionURL.buildURL('project', 'begin');
        LABKEY.help.Tour.continueAtLocation(url);
    }
    // ...
};
LABKEY.Utils.onReady(function() {
    LABKEY.help.Tour.continueTour();
});
</pre>
         */
        var continueAtLocation = function (href)
        {
            var tour = hopscotch.getCurrTour();

            if (!tour)
            {
                window.location = href;
                return;
            }

            var a = document.createElement('a');
            a.href = href;
            a.hash = [
                URL_PREFIX,
                _decodeId(tour.id),
                hopscotch.getCurrStepNum()
            ].join(':');
            window.location = a.href;
        };

        /**
        * see continueAtLocation()
         * @private
        */
        var continueTour = function ()
        {
            var config = _getContinue();
            if (!$.isEmptyObject(config))
            {
                return resume(config.id, parseInt(config.step));
            }
        };

        /**
         * Mark tour as seen so autoShow() will no longer show this tour
         * @param id
         * @private
         */
        var markSeen = function (id)
        {
            var state = {};
            var v = localStorage.getItem(_localStorageProperty);
            if (v)
            {
                state = LABKEY.Utils.decode(v);
            }
            state[id] = "seen";
            localStorage.setItem(_localStorageProperty, LABKEY.Utils.encode(state));
        };

        /**
         * @param config
         */
        var register = function(config)
        {
            _register(config, 0);
        };

        /**
         * @private
         */
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
         * Continue tour if it is currently on the indicated step, useful for multi-page tours
         * Always loads hopscotch.js
         * @private
         */
        var resume = function (id, step)
        {
            var config = _get(id);
            if (config)
            {
                var testState = _encodeId(config.id) + ":" + step;
                // peek into hopscotch state w/o loading hopscotch.js
                if (testState === sessionStorage.getItem(_hopscotchSessionProperty))
                {
                    _display(config, step);
                }
                return id;
            }
        };

        /**
         * Determines if the given tour (by id) has already been seen by the user
         * @param id
         * @returns {boolean}
         * @private
         */
        var seen = function (id)
        {
            // use one item for all tours, this is a little more complicated, but makes it easier to reset state
            var state = {};
            var v = localStorage.getItem(_localStorageProperty);
            if (v)
            {
                state = LABKEY.Utils.decode(v);
            }
            return "seen" == state[id];
        };

        /**
         * @param id
         * @param step
         * @private
         */
        var show = function(id, step)
        {
            var tour = _get(id);
            if (tour)
            {
                _display(tour, step);
            }
        };

        /**
         * @param id
         * @param step
         * @private
         */
        var showFromDb = function(id, step)
        {
            _load(id,step);
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
            show: show,
            showFromDb: showFromDb
        };
    };

})(jQuery);