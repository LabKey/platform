/*
 * Copyright (c) 2014-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

if (!LABKEY.internal)
    LABKEY.internal = {};

/**
 * LABKEY.internal.MiniProfiler
 *
 * Port of MiniProfiler: https://github.com/MiniProfiler/ui
 *
 * NOTE: Should not have any dependencies other than LABKEY.ActionURL, LABKEY.Ajax, and LABKEY.Utils.getCallbackWrapper.
 */

LABKEY.internal.MiniProfiler = new function () {
    "use strict";

    //
    // DOM utilities - my half-ass re-implementation of jQuery... just enough to port the miniprofiler ui.
    //

    // check for browser features the miniprofiler requires.
    function browserSupported() {
        return typeof (document.querySelectorAll) != 'undefined' &&
                typeof (XMLHttpRequest) != 'undefined' &&
                _matches;
    }

    function toArray(nl) {
        if (isArray(nl))
            return nl;

        var ret = [];
        for (var i = 0, len = nl.length; i < len; i++) {
            ret.push(nl[i]);
        }
        return ret;
    }

    function keys(o) {
        var ret = [];
        for (var key in o) {
            if (o.hasOwnProperty(key))
                ret.push(key);
        }
        return ret;
    }

    // Convert html string into DOM nodes.
    function createFrag(html) {
        var frag = document.createDocumentFragment(),
            temp = document.createElement('div');
        temp.innerHTML = html;
        while (temp.firstChild) {
            frag.appendChild(temp.firstChild);
        }
        return frag;
    }

    // Insert html string
    function insertBefore(el, html) {
        var parentNode = el.parentNode;
        var node = createFrag(html);
        var nl = node.childNodes;
        var ret = nl.length == 1 ? nl[0] : toArray(nl);
        parentNode.insertBefore(node, el);
        return ret;
    }

    // Append html string to el and return the newly inserted Node or Array of Node.
    function append(el, html) {
        if (el.firstChild) {
            var node = createFrag(html);
            var nl = node.childNodes;
            var ret = nl.length == 1 ? nl[0] : toArray(nl);
            el.appendChild(node);
            return ret;
        } else {
            el.innerHTML = html;
            return el.firstChild;
        }
    }

    function remove(el) {
        if (el instanceof NodeList || isArray(el)) {
            for (var i = 0, len = el.length; i < len; i++)
                remove(el[i]);
            return;
        }

        el.parentNode.removeChild(el);
    }

    function select(el, selector) {
        if (el instanceof NodeList || isArray(el)) {
            var ret = [];
            for (var i = 0, len = el.length; i < len; i++) {
                ret = ret.concat(select(el[i], selector));
            }
            return ret;
        }

        return toArray(el.querySelectorAll(selector));
    }

    function filter(coll, predicate) {
        var ret = [];
        for (var i = 0, len = coll.length; i < len; i++) {
            var el = coll[i];
            if (predicate(el))
                ret.push(el);
        }
        return ret;
    }

    // Get sibling nodes that match the selector
    function siblings(el, selector) {
        if (el instanceof NodeList || isArray(el)) {
            var ret = [];
            for (var i = 0, len = el.length; i < len; i++) {
                ret = ret.concat(siblings(el[i], selector));
            }
            return ret;
        }

        var parentNode = el.parentNode;
        var nl = parentNode.querySelectorAll(selector);
        return filter(nl, function (sibling) { return el != sibling });
    }

    // Get parent nodes that match the selcetor.
    function closest(el, selector) {
        if (el instanceof NodeList || isArray(el)) {
            var ret = [];
            for (var i = 0, len = el.length; i < len; i++) {
                ret = ret.concat(closest(el[i], selector));
            }
            return ret;
        }

        el = el.parentNode;
        while (el) {
            if (matches(el, selector))
                return el;
            el = el.parentNode;
        }
        return null;
    }

    function parent(el) {
        if (el instanceof NodeList || isArray(el)) {
            var ret = [];
            for (var i = 0, len = el.length; i < len; i++) {
                ret = ret.concat(parent(el[i]));
            }
            return ret;
        }

        return el.parentNode;
    }

    // Returns true if el is a parent node of child.
    function contains(el, child) {
        var up = child.parentNode;
        return el === up || !!(up && up.nodeType === 1 && el.contains(up));
    }

    // Test if a node matches a selector
    var _matches =
        document.documentElement.matches ||
        document.documentElement.webkitMatchesSelector ||
        document.documentElement.mozMatchesSelector ||
        document.documentElement.msMatchesSelector ||
        document.documentElement.oMatchesSelector;

    function matches(el, selector) {
        return _matches.call(el, selector);
    }

    function isArray(value) {
        // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/isArray
        return Object.prototype.toString.call(value) === "[object Array]";
    }

    function hide(el) {
        if (el instanceof NodeList || isArray(el)) {
            for (var i = 0, len = el.length; i < len; i++)
                hide(el[i]);
            return;
        }

        //el.originalDisplay = getStyle(el, 'display');
        setStyle(el, 'display', 'none');
    }

    function show(el) {
        if (el instanceof NodeList || isArray(el)) {
            for (var i = 0, len = el.length; i < len; i++)
               show(el[i]);
            return;
        }

        var display = 'block';
        if (el.tagName == "TH" || el.tagName == "TD")
            display = 'table-cell';
        else if (el.tagName == "TR")
            display = 'table-row';
        else if (el.tagName == "SPAN")
            display = 'inline';
        setStyle(el, 'display', display);
    }

    function toggle(el, isVisible) {
        if (isVisible === undefined)
            isVisible = visible(el);

        if (isVisible)
            hide(el);
        else
            show(el);
    }

    function visible(el) {
        if (el instanceof NodeList || isArray(el)) {
            for (var i = 0, len = el.length; i < len; i++)
                if (!visible(el[i]))
                    return false;
            return len > 0;
        }

        return el.offsetWidth > 0 || el.offsetHeight > 0;
    }

    function getStyle(el, prop) {
        var style = el.style || (el.style = {});
        return style[prop];
    }

    function setStyle(el, prop, value) {
        if (el instanceof NodeList || isArray(el)) {
            for (var i = 0, len = el.length; i < len; i++)
                setStyle(el[i], prop, value);
            return;
        }

        var style = el.style || (el.style = {});
        style[prop] = value;
    }

    function addClass(el, clazz) {
        if (el instanceof NodeList || isArray(el)) {
            for (var i = 0, len = el.length; i < len; i++)
                addClass(el[i], clazz);
            return;
        }

        el.classList.add(clazz);
    }

    function removeClass(el, clazz) {
        if (el instanceof NodeList || isArray(el)) {
            for (var i = 0, len = el.length; i < len; i++)
                removeClass(el[i], clazz);
            return;
        }

        el.classList.remove(clazz);
    }

    function toggleClass(el, clazz) {
        if (el instanceof NodeList || isArray(el)) {
            for (var i = 0, len = el.length; i < len; i++)
                toggleClass(el[i], clazz);
            return;
        }

        el.classList.toggle(clazz);
    }

    function data(el, key, value) {
        if (el instanceof NodeList || isArray(el)) {
            var ret = [];
            for (var i = 0, len = el.length; i < len; i++)
                ret = ret.concat(data(el[i], key, value));
            return ret;
        }

        if (el.dataset)
        {
            if (value === undefined)
                return el.dataset[key];
            else
                return el.dataset[key] = value;
        }
        else
        {
            if (value === undefined)
                return el.getAttribute("data-" + key);
            else
                return el.setAttribute("data-" + key, value);
        }
    }

    function click(el, callback) {
        addEventListener('click', el, callback);
    }

    function hover(el, enter, leave) {
        addEventListener('mouseenter', el, enter);
        addEventListener('mouseleave', el, leave);
    }

    function addEventListener(type, el, callback) {
        if (el instanceof NodeList || isArray(el)) {
            for (var i = 0, len = el.length; i < len; i++)
                addEventListener(type, el[i], callback);
            return;
        }

        el.addEventListener(type, callback);
    }

    //
    // Minimal jquery.hotkeys.js - https://github.com/jeresig/jquery.hotkeys
    //

    var specialKeys = {
      8: "backspace",
      9: "tab",
      10: "return",
      13: "return",
      16: "shift",
      17: "ctrl",
      18: "alt",
      19: "pause",
      20: "capslock",
      27: "esc",
      32: "space",
      33: "pageup",
      34: "pagedown",
      35: "end",
      36: "home",
      37: "left",
      38: "up",
      39: "right",
      40: "down",
      45: "insert",
      46: "del",
      59: ";",
      61: "=",
      96: "0",
      97: "1",
      98: "2",
      99: "3",
      100: "4",
      101: "5",
      102: "6",
      103: "7",
      104: "8",
      105: "9",
      106: "*",
      107: "+",
      109: "-",
      110: ".",
      111: "/",
      112: "f1",
      113: "f2",
      114: "f3",
      115: "f4",
      116: "f5",
      117: "f6",
      118: "f7",
      119: "f8",
      120: "f9",
      121: "f10",
      122: "f11",
      123: "f12",
      144: "numlock",
      145: "scroll",
      173: "-",
      186: ";",
      187: "=",
      188: ",",
      189: "-",
      190: ".",
      191: "/",
      192: "`",
      219: "[",
      220: "\\",
      221: "]",
      222: "'"
    };

    var shiftNums = {
      "`": "~",
      "1": "!",
      "2": "@",
      "3": "#",
      "4": "$",
      "5": "%",
      "6": "^",
      "7": "&",
      "8": "*",
      "9": "(",
      "0": ")",
      "-": "_",
      "=": "+",
      ";": ": ",
      "'": "\"",
      ",": "<",
      ".": ">",
      "/": "?",
      "\\": "|"
    };

    // Returns true if the key combination described in the 'keys' string has been pressed.
    function isHotKey(keys, event) {
        if (typeof keys !== 'string') {
            return;
        }

        keys = keys.toLowerCase().split(" ");

        var special = event.type !== "keypress" && specialKeys[event.which],
            character = String.fromCharCode(event.which).toLowerCase(),
            modif = "",
            possible = {};

        var altCtrlShift = ["alt", "ctrl", "shift"];
        for (var i = 0, l = altCtrlShift.length; i < l; i++) {
            var specialKey = altCtrlShift[i];
            if (event[specialKey + 'Key'] && special !== specialKey) {
                modif += specialKey + '+';
            }
        }

        if (special) {
            possible[modif + special] = true;
        }
        else {
            possible[modif + character] = true;
            possible[modif + shiftNums[character]] = true;
        }

        for (var i = 0, l = keys.length; i < l; i++) {
            if (possible[keys[i]]) {
                return true;
            }
        }

        return false;
    }

    //
    // profiler
    //

    var _initialized = false;
    var _queue = [];

    var _fetched = [];
    var _fetching = [];
    var _options = {};
    var _container;
    var _controls;

    var _tmplCache = {};


    function isFetched(id) {
        return _fetched.indexOf(id) > -1;
    }

    function isFetching(id) {
        return _fetching.indexOf(id) > -1;
    }

    function fetchTemplates(success) {
        LABKEY.Ajax.request({
            method: 'GET',
            url: LABKEY.contextPath + "/internal/MiniProfiler/miniprofiler.tmpl?skip-profiling=true",
            success: function (xhr, options) {
                var text = xhr.responseText;
                append(document.body, text);
                success();
            }
        });
    }

    function fetchResults(ids) {
        if (!ids)
            return;

        if (!_initialized) {
            // queue requested ids until the MiniProfiler has initialized
            _queue = _queue.concat(ids);
        }
        else {
            // clear queue if needed
            if (_queue) {
                ids = ids.concat(_queue);
                _queue = null;
            }

            for (var i = 0; i < ids.length; i++)
            {
                fetchResult(ids[i]);
            }
        }
    }

    function fetchResult(id) {
        // TODO: collect client probe timings and send them

        if (!isFetched(id) && !isFetching(id)) {
            var idx = _fetching.push(id) - 1;

            LABKEY.Ajax.request({
                url: LABKEY.contextPath + '/mini-profiler/report.api',
                method: 'POST',
                jsonData: {
                    id: id
                },
                success: LABKEY.Utils.getCallbackWrapper(function (json, xhr, config) {
                    _fetching.splice(idx, 1);
                    _fetched.push(id);
                    if (!json)
                        console.error("empty json for " + id + ": ");
                    else {
                        buttonShow(json);
                    }
                }, this, false),
                failure: function () {
                    _fetching.splice(idx, 1);
                }
            })
        }
    }

    function processJson(json) {
        json.hasDuplicateCustomTimings = false;
        json.hasCustomTimings = false;
        json.hasAllocations = false;
        json.hasTrivialTimings = false;
        json.customTimingStats = {};
        json.customLinks = json.customLinks || {};
        json.trivialMilliseconds = _options.trivialMilliseconds;

        json.root.parentTimingId = json.id;

        json.allocationCount = keys(json.objects).length;

        // different serializers handle dates differently
        switch (typeof json.date) {
            case 'number':
                json.date = new Date(json.date);
                break;
            case 'string':
                json.date = new Date(parseInt(json.date));
                break;
        }

        processTiming(json, json.root, 0);
    }

    function processTiming(json, timing, depth) {
        timing.depth = depth;
        timing.hasCustomTimings = timing.customTimings ? true : false;
        timing.hasAllocations = timing.objects ? true : false;
        timing.hasDuplicateCustomTimings = {};
        json.hasCustomTimings = json.hasCustomTimings || timing.hasCustomTimings;
        json.hasAllocations = json.hasAllocations || timing.hasAllocations;

        timing.allocationCount = keys(timing.objects).length;

        if (timing.children) {
            for (var i = 0; i < timing.children.length; i++) {
                timing.children[i].parentTimingId = timing.id;
                processTiming(json, timing.children[i], depth+1);
            }
        } else {
            timing.children = [];
        }

        timing.isTrivial = timing.durationExclusive < _options.trivialMilliseconds;
        json.hasTrivialTimings = json.hasTrivialTimings || timing.isTrivial;

        if (timing.customTimings) {
            timing.customTimingStats = {};
            for (var customType in timing.customTimings) {
                if (!timing.customTimings.hasOwnProperty(customType))
                    continue;
                var customTimings = timing.customTimings[customType];
                var customStat = {
                    duration: 0,
                    count: 0
                };
                var duplicates = { };
                for (var i = 0; i < customTimings.length; i++) {
                    var customTiming = customTimings[i];
                    customTiming.parentTimingId = timing.id;
                    customStat.duration += customTiming.duration;
                    customStat.count++;
                    if (customTiming.message && duplicates[customTiming.message]) {
                        customTiming.isDuplicate = true;
                        timing.hasDuplicateCustomTimings[customType] = true;
                        json.hasDuplicateCustomTimings = true;
                    } else {
                        duplicates[customTiming.message] = true;
                    }
                }

                timing.customTimingStats[customType] = customStat;
                if (!json.customTimingStats[customType]) {
                    json.customTimingStats[customType] = {
                        duration: 0,
                        count: 0
                    };
                }
                json.customTimingStats[customType].duration += customStat.duration;
                json.customTimingStats[customType].count += customStat.count;
            }
        } else {
            timing.customTimings = {};
        }

    }

    function renderTemplate(json) {
        processJson(json);
        var html = template('#profilerTemplate', json);
        //console.log(html);
        return html;
    }

    // Helper functions made available to the template
    var templateHelpers = {
        tmpl : function (name, o) { return template(name, o); },

        shareUrl : function (id) {
            return "share";
        },

        allocationsUrl : function (id) {
            return LABKEY.ActionURL.buildURL("admin", "trackedAllocationsViewer.view", "/");
        },

        settingsUrl : function () {
            return LABKEY.ActionURL.buildURL("mini-profiler", "manage.view", "/");
        },

        formatDuration : function (millis) {
            return (millis || 0).toFixed(1);
        },

        formatDateTime : function (datetime) {
            return datetime.toLocaleString();     // Reasonable format for now. TODO: Use LABKEY.extDefaultDateTimeFormat to format based on server preference
        },

        formatAllocations : function (objects, shortNames) {
            var ret = [];
            for (var name in objects) {
                if (!objects.hasOwnProperty(name))
                    continue;
                var displayName = name;
                if (shortNames) {
                    displayName = name.split(".").pop();
                }
                ret.push(displayName + ": " + objects[name]);
            }
            return ret.join("\n");
        },

        getCustomTimings : function (root) {
            // Not sure why this isn't a part of processJson in the original
            var result = [],
                addToResults = function (timing) {
                    if (timing.customTimings) {
                        for (var customType in timing.customTimings)
                        {
                            var customTimings = timing.customTimings[customType];

                            for (var i = 0, customTiming; i < customTimings.length; i++) {
                                customTiming = customTimings[i];

                                // HACK: add info about the parent Timing to each CustomTiming so UI can render
                                customTiming.parentTimingName = timing.name;
                                customTiming.callType = customType;
                                result.push(customTiming);
                            }
                        }
                    }

                    if (timing.children) {
                        for (var i = 0; i < timing.children.length; i++) {
                            addToResults(timing.children[i]);
                        }
                    }
                };

            // start adding at the root and recurse down
            addToResults(root);

            return result;
        }

    };

    function template(name, o) {
        try {
            var source = document.querySelector(name).innerHTML;
            var tmpl = _tmplCache[name] || (_tmplCache[name] = doT.template(source));
            //console.log(tmpl);
            o._self = o;
            var html = tmpl.call(templateHelpers, o);
            return html.trim();
        } catch (e) {
            console.log("error with: " + name + ": " + e);
        }
    }

    function buttonShow(json) {
        var html = renderTemplate(json);
        var result;

        if (_controls)
            result = insertBefore(_controls, html);
        else
            result = append(_container, html);

        //console.log(result);

        var button = result.querySelectorAll('.profiler-button'),
            popup = result.querySelectorAll('.profiler-popup');

        // button will appear in corner with the total profiling duration - click to show details
        click(button, function (evt) { buttonClick(evt.currentTarget); });

        // small duration steps and the column with aggregate durations are hidden by default; allow toggling
        toggleHidden(popup);

        // lightbox in the queries
        click(select(popup, '.profiler-queries-show'), function (evt) { queriesShow(evt.currentTarget, result); });

        // add query message and stacktrace toggling
        toggleQueryDetails(result);

        // limit count
        while (_container.querySelectorAll('.profiler-result').length > _options.maxTracesToShow)
            resultRemove(_container.querySelectorAll('.profiler-result')[0]);

        show(button);
    }

    function toggleHidden(popup) {
        var trivial = select(popup, '.profiler-toggle-trivial'),
            toggleColumns = select(popup, '.profiler-toggle-hidden-columns'),
            toggleAllocations = select(popup, '.profiler-toggle-allocations');
            //trivialGaps = popup.parent().find('.profiler-toggle-trivial-gaps');

        var toggleIt = function (link, ignoreHorizScroll) {
            if (link instanceof NodeList || isArray(link)) {
                for (var i = 0, len = link.length; i < len; i++) {
                    toggleIt(link[i], true);
                }

                popupPreventHorizontalScroll(popup);
                return;
            }

            var klass = data(link, 'toggleclass'),
                hideText = data(link, 'hidetext'),
                showText = data(link, 'showtext'), // first call will be null
                isHidden = link.innerText != hideText;

            // save our initial text to allow reverting
            if (!showText) {
                showText = link.innerText;
                data(link, 'showtext', showText);
            }

            toggle(select(parent(popup), '.' + klass), !isHidden);
            link.innerText = isHidden ? hideText : showText;

            if (ignoreHorizScroll)
                popupPreventHorizontalScroll(popup);
        };

        click(toggleColumns.concat(trivial), function (e) {
            toggleIt(e.currentTarget);
        });

        // if option is set or all our timings are trivial, go ahead and show them
        if (_options.showTrivial) { // || trivial.data('show-on-load')) {
            toggleIt(trivial);
        }
        // if option is set, go ahead and show time with children
        if (_options.showChildrenTime) {
            toggleIt(toggleColumns);
        }
        // if option is set, go ahead and show allocations
        if (_options.showAllocations) {
            toggleIt(toggleAllocations);
        }
    }

    function toggleQueryDetails(result) {
        var queries = select(result, '.profiler-queries'),
            toggles = select(queries, '.profiler-toggle-custom');

        click(toggles, function (e) {
            var link = e.currentTarget;

            // Get message or stack this toggle is associated with
            var sibling = link.nextElementSibling;

            toggleClass(sibling, "profiler-custom-min");

            // Change the toggle icon
            var hideText = data(link, 'hidetext'),
                showText = data(link, 'showtext'), // first call will be null
                isHidden = link.innerText != hideText;

            // save our initial text to allow reverting
            if (!showText) {
                showText = link.innerText;
                data(link, 'showtext', showText);
            }
            link.innerText = isHidden ? hideText : showText;
        });
    }

    function buttonClick(button) {
        // we're toggling this button/popup
        var popup = button.parentNode.querySelector(".profiler-popup");
        if (visible(popup)) {
            popupHide(button, popup);
        }
        else {
            var visiblePopups = filter(_container.querySelectorAll('.profiler-popup'), visible),
                theirButtons = siblings(visiblePopups, '.profiler-button');

            // hide any other popups
            popupHide(theirButtons, visiblePopups);

            // before showing the one we clicked
            popupShow(button, popup);
        }

    }

    function popupShow(button, popup) {
        addClass(button, 'profiler-button-active');

        popupSetDimensions(button, popup);

        show(popup);

        popupPreventHorizontalScroll(popup);
    }

    function popupSetDimensions(button, popup) {
        var rect = button.getBoundingClientRect(),
            top = rect.top - 1, // position next to the button we clicked
            windowHeight = window.innerHeight,
            //maxHeight = windowHeight - top - 40, // make sure the popup doesn't extend below the fold
            isBottom = _options.renderPosition.indexOf("bottom") != -1; // is this rendering on the bottom (if no, then is top by default)

        if (isBottom) {
            var bottom = windowHeight - top - rect.height, // get bottom of button
                isLeft = _options.renderPosition.indexOf("left") != -1;

            var horizontalPosition = isLeft ? "left" : "right";
            setStyle(popup, 'bottom', bottom + "px");
            //setStyle(popup, 'max-height', maxHeight);
            setStyle(popup, horizontalPosition, (rect.width - 3) + "px"); // move left or right, based on config
        }
        else {
            setStyle(popup, 'top', top);
            //setStyle(popup, 'max-height', maxHeight);
            setStyle(popup, _options.renderPosition, (rect.width - 3) + "px"); // move left or right, based on config
        }
    }

    function popupPreventHorizontalScroll(popup) { }

    function popupHide(button, popup) {
        removeClass(button, 'profiler-button-active');
        hide(popup);
    }

    function resultRemove(result) {
        var bg = select(document, '.profiler-queries-bg'),
            queries = select(result, '.profiler-queries');
        var hideQueries = visible(bg) && visible(queries);
        if (hideQueries) {
            remove(bg);
        }
        remove(result);
    }

    function queriesShow(link, result) {

        var px = 30,
            win = window,
            height = win.innerHeight - 2 * px,
            queries = result.querySelectorAll('.profiler-queries');

        if (visible(queries))
            return;

        // opaque background
        var el = append(document.body, '<div class="profiler-queries-bg"/>');
        setStyle(el, 'height', window.innerHeight + "px");
        setStyle(el, 'top', window.scrollY + "px");
        show(el);

        // center the queries and ensure long content is scrolled
        setStyle(queries, 'max-height', height + "px");

        // have to show everything before we can get a position for the first query
        show(queries);

        //queriesScrollIntoView(link, queries, queries);

        // syntax highlighting
        //prettyPrint();

        // disable scrolling body
        setStyle(document.body, "overflow", "hidden");
    }

    function bindDocumentEvents() {
        function handleEvent(e) {
            // this happens on every keystroke
            var popup = filter(select(document, '.profiler-popup'), visible);

            if (!popup.length) {
                return;
            }

            var button = siblings(popup, '.profiler-button'),
                queries = select(closest(popup, '.profiler-result'), '.profiler-queries'),
                bg = select(document, '.profiler-queries-bg'),
                isEscPress = e.type == 'keyup' && e.which == 27,
                hidePopup = false,
                hideQueries = false;

            if (visible(bg)) {
                // escape pressed or the click target is outside the .profiler-result (ie., doesn't have it as a parent)
                hideQueries = isEscPress || (e.type == 'click' && e.target != queries[0] && !contains(popup[0], e.target) && !contains(queries[0], e.target));
            }
            else if (visible(popup[0])) {
                hidePopup = isEscPress || (e.type == 'click' && e.target != button[0] && !contains(popup[0], e.target) && !contains(button[0], e.target));
            }

            if (hideQueries) {
                remove(bg);
                hide(queries);
                // resume scrolling
                setStyle(document.body, "overflow", "auto");
            }

            if (hidePopup) {
                popupHide(button, popup);
            }
        }

        addEventListener('click', document, handleEvent);
        addEventListener('keyup', document, handleEvent);

        if (_options.toggleShortcut && !_options.toggleShortcut.match(/None$/i)) {
            addEventListener('keydown', document, function (e) {
                if (isHotKey(_options.toggleShortcut, e)) {
                    toggle(select(document, '.profiler-results'));
                }
            });
        }
    }

    function initFullView() {

    }

    function initControls() {
        if (_options.showControls) {
            _controls = append(_container,
                            '<div class="profiler-controls">' +
                            '<span class="profiler-min-max" title="click to minimize/maximize">m</span>' +
                            '<span class="profiler-clear" title="click to clear log">c</span>' +
                            '<span><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=profiler" target=_blank title="help">?</a></span>' +
                            '</div>');

            click(select(_container, '.profiler-controls .profiler-min-max'), function () {
                toggleClass(_container, 'profiler-min');
            });

            hover(_container, function (e) {
                if (_container.classList.contains('profiler-min')) {
                    show(select(_container, '.profiler-min-max'));
                }
            },
            function (e) {
                if (_container.classList.contains('profiler-min')) {
                    hide(select(_container, '.profiler-min-max'));
                }
            });

            click(select(_container, '.profiler-controls .profiler-clear'), function () {
                remove(select(_container, '.profiler-result'));
            });
        }
        else {
            addClass(_container, 'profiler-no-controls');
        }
    }

    function initPopupView() {
        if (_options.authorized) {
            // all fetched profilings will go in here
            _container = document.createElement("div");
            _container.className = 'profiler-results';
            document.body.appendChild(_container);

            // Sets which corner to render in - default is upper left
            _container.classList.add("profiler-" + (_options.renderPosition ? _options.renderPosition : "bottomright"));

            // initialize the controls
            initControls(_container);

            // we'll render results json via a jquery.tmpl - after we get the templates, we'll fetch the initial json to populate it
            fetchTemplates(function () {
                //console.log("initialized");
                _initialized = true;

                // get master page profiler results
                fetchResults(_options.ids);
            });
            if (_options.startHidden) {
                hide(_container);
            }
        }
        else {
            fetchResults(_options.ids);
        }

        // some elements want to be hidden on certain doc events
        bindDocumentEvents();
    }

    function interceptRequests() {
        if (!browserSupported())
            return;

        function fetchIds(stringIds) {
            if (stringIds) {
                var ids = typeof JSON != 'undefined' ? JSON.parse(stringIds) : eval(stringIds);
                fetchResults(ids);
            }
        }

        // Support LABKEY.Ajax and other libraries like AngularJS which use the basic XMLHttpRequest object.
        if (typeof (XMLHttpRequest) != 'undefined') {
            var _send = XMLHttpRequest.prototype.send;

            XMLHttpRequest.prototype.send = function sendReplacement(data) {
                if (this.onreadystatechange) {
                    if (!this._onreadystatechange) {
                        this._onreadystatechange = this.onreadystatechange;

                        this.onreadystatechange = function onReadyStateChangeReplacement()
                        {
                            if (this.readyState == 4) {
                                var stringIds = this.getResponseHeader('X-MiniProfiler-Ids');
                                fetchIds(stringIds);
                            }

                            return this._onreadystatechange.apply(this, arguments);
                        };
                    }
                }

                return _send.apply(this, arguments);
            }
        }
    }

    function initOptions(script) {
        var currentId = script.getAttribute('data-current-id');

        var ids = script.getAttribute('data-ids');
        if (ids)  ids = ids.split(',');

        if (script.getAttribute('data-authorized') == 'true') var authorized = true;
        if (script.getAttribute('data-start-hidden') == 'true') var startHidden = true;


        return {
            currentId: currentId,
            ids: ids,
            version: 3,
            renderPosition: renderPosition,
            showTrivial: showTrivial,
            trivialMilliseconds: trivialMilliseconds,
            showChildrenTime: showChildrenTime,
            maxStackTracesToShow: maxTraces,
            showControls: showControls,
            authorized: authorized,
            toggleShortcut: toggleShortcut,
            startHidden: startHidden
        }
    }

    function _init(config) {
        if (!browserSupported())
            return;

        _options = config;

        var doInit = function () {
            initPopupView();
        };

        var wait = 0;
        var finish = false;
        var deferInit = function () {
            if (finish) return;
            if (window.performance && window.performance.timing && window.performance.timing.loadEventEnd == 0 && wait < 10000) {
                setTimeout(deferInit, 100);
                wait += 100;
            } else {
                finish = true;
                doInit();
            }
        };

        deferInit();
    }

    // Invoke init using data-* options found on the <script id='mini-profiler'> element
    (function () {
        var script = document.getElementById('mini-profiler');
        if (!script || !script.getAttribute) return;

        var options = initOptions(script);
        _init(options);
    })();

    // Begin intercepting right-away even before we're initialized
    interceptRequests();

    return {

        init : function (config) {
            //console.log("MiniProfiler.init");
            _init(config);
        },

        fetch : function (ids) {
            return fetchResults(ids);
        }

    }
};

// doT.js v1.0.1
// Laura Doktorova, https://github.com/olado/doT
// Licensed under the MIT license.

(function(){function o(){var a={"&":"&#38;","<":"&#60;",">":"&#62;",'"':"&#34;","'":"&#39;","/":"&#47;"},b=/&(?!#?\w+;)|<|>|"|'|\//g;return function(){return this?this.replace(b,function(c){return a[c]||c}):this}}function p(a,b,c){return(typeof b==="string"?b:b.toString()).replace(a.define||i,function(l,e,f,g){if(e.indexOf("def.")===0)e=e.substring(4);if(!(e in c))if(f===":"){a.defineParams&&g.replace(a.defineParams,function(n,h,d){c[e]={arg:h,text:d}});e in c||(c[e]=g)}else(new Function("def","def['"+
e+"']="+g))(c);return""}).replace(a.use||i,function(l,e){if(a.useParams)e=e.replace(a.useParams,function(g,n,h,d){if(c[h]&&c[h].arg&&d){g=(h+":"+d).replace(/'|\\/g,"_");c.__exp=c.__exp||{};c.__exp[g]=c[h].text.replace(RegExp("(^|[^\\w$])"+c[h].arg+"([^\\w$])","g"),"$1"+d+"$2");return n+"def.__exp['"+g+"']"}});var f=(new Function("def","return "+e))(c);return f?p(a,f,c):f})}function m(a){return a.replace(/\\('|\\)/g,"$1").replace(/[\r\t\n]/g," ")}var j={version:"1.0.1",templateSettings:{evaluate:/\{\{([\s\S]+?(\}?)+)\}\}/g,
interpolate:/\{\{=([\s\S]+?)\}\}/g,encode:/\{\{!([\s\S]+?)\}\}/g,use:/\{\{#([\s\S]+?)\}\}/g,useParams:/(^|[^\w$])def(?:\.|\[[\'\"])([\w$\.]+)(?:[\'\"]\])?\s*\:\s*([\w$\.]+|\"[^\"]+\"|\'[^\']+\'|\{[^\}]+\})/g,define:/\{\{##\s*([\w\.$]+)\s*(\:|=)([\s\S]+?)#\}\}/g,defineParams:/^\s*([\w$]+):([\s\S]+)/,conditional:/\{\{\?(\?)?\s*([\s\S]*?)\s*\}\}/g,iterate:/\{\{~\s*(?:\}\}|([\s\S]+?)\s*\:\s*([\w$]+)\s*(?:\:\s*([\w$]+))?\s*\}\})/g,varname:"it",strip:true,append:true,selfcontained:false},template:undefined,
compile:undefined},q;if(typeof module!=="undefined"&&module.exports)module.exports=j;else if(typeof define==="function"&&define.amd)define(function(){return j});else{q=function(){return this||(0,eval)("this")}();q.doT=j}String.prototype.encodeHTML=o();var r={append:{start:"'+(",end:")+'",endencode:"||'').toString().encodeHTML()+'"},split:{start:"';out+=(",end:");out+='",endencode:"||'').toString().encodeHTML();out+='"}},i=/$^/;j.template=function(a,b,c){b=b||j.templateSettings;var l=b.append?r.append:
r.split,e,f=0,g;a=b.use||b.define?p(b,a,c||{}):a;a=("var out='"+(b.strip?a.replace(/(^|\r|\n)\t* +| +\t*(\r|\n|$)/g," ").replace(/\r|\n|\t|\/\*[\s\S]*?\*\//g,""):a).replace(/'|\\/g,"\\$&").replace(b.interpolate||i,function(h,d){return l.start+m(d)+l.end}).replace(b.encode||i,function(h,d){e=true;return l.start+m(d)+l.endencode}).replace(b.conditional||i,function(h,d,k){return d?k?"';}else if("+m(k)+"){out+='":"';}else{out+='":k?"';if("+m(k)+"){out+='":"';}out+='"}).replace(b.iterate||i,function(h,
d,k,s){if(!d)return"';} } out+='";f+=1;g=s||"i"+f;d=m(d);return"';var arr"+f+"="+d+";if(arr"+f+"){var "+k+","+g+"=-1,l"+f+"=arr"+f+".length-1;while("+g+"<l"+f+"){"+k+"=arr"+f+"["+g+"+=1];out+='"}).replace(b.evaluate||i,function(h,d){return"';"+m(d)+"out+='"})+"';return out;").replace(/\n/g,"\\n").replace(/\t/g,"\\t").replace(/\r/g,"\\r").replace(/(\s|;|\}|^|\{)out\+='';/g,"$1").replace(/\+''/g,"").replace(/(\s|;|\}|^|\{)out\+=''\+/g,"$1out+=");if(e&&b.selfcontained)a="String.prototype.encodeHTML=("+
o.toString()+"());"+a;try{return new Function(b.varname,a)}catch(n){typeof console!=="undefined"&&console.log("Could not create a template function: "+a);throw n;}};j.compile=function(a,b){return j.template(a,null,b)}})();

