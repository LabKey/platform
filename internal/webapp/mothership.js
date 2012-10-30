/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

// Must not depend on ExtJS or LABKEY APIs.
// uses stacktrace.js if available.
LABKEY.Mothership = (function () {

    // When _gatherAllocation is true, a stacktrace is generated where the
    // callback function is registered.  Generating the allocation stacktraces
    // can reduce performance.
    var _gatherAllocation = true;

    // When enabled, the errors will be logged to the local LabKey server.
    // When not enabled, no logging is done but the uncaught error is still printed to the console.
    var _enabled = false;

    // Rethrow caught exceptions.
    // Chrome 18 has a bug that causes the original stacktrace to be lost when an Error is rethrown.  A fix for this issue will appear in Chrome 19.
    // http://code.google.com/p/chromium/issues/detail?id=60240
    var _rethrow = true;

    // Stack of last _maxLastErrors messages
    var _lastErrors = [];
    var _maxLastErrors = 10;

    // Remove mothership.js and stacktrace.js from the stacktrace.
    var _filterStacktrace = true;


    /**
     * Try XHR methods in order and store XHR factory.
     *
     * @return <Function> XHR function or equivalent
     */
    function createXMLHTTPObject() {
        var xmlhttp, XMLHttpFactories = [
            function() {
                return new XMLHttpRequest();
            }, function() {
                return new ActiveXObject('Msxml2.XMLHTTP');
            }, function() {
                return new ActiveXObject('Msxml3.XMLHTTP');
            }, function() {
                return new ActiveXObject('Microsoft.XMLHTTP');
            }
        ];
        for (var i = 0; i < XMLHttpFactories.length; i++) {
            try {
                xmlhttp = XMLHttpFactories[i]();
                // Use memoization to cache the factory
                createXMLHTTPObject = XMLHttpFactories[i];
                return xmlhttp;
            } catch (e) {}
        }
    }

    function encodeQuery(data) {
        var ret = '';
        var and = '';
        for (var i in data) {
            ret += and + encodeURIComponent(i) + '=' + encodeURIComponent(data[i]);
            and = '&';
        }
        return ret;
    }

    /** send an async GET message. */
    function send(url, data) {
        url = url + '?' + encodeQuery(data);

        var req = createXMLHTTPObject();
        if (!req) {
            console.warn("** Failed to send error report: ", data);
            return;
        }
        req.open('GET', url, true);
        var transId = req.send('');
        return transId;
    }

    // Record the last few error message
    function lastError(msg) {
        if (!msg)
            return;
        msg = ""+msg;
        if (_lastErrors.push(msg) > _maxLastErrors)
            _lastErrors = _lastErrors.slice(1);
    }

    // UNDONE: Throttle number of errors so we don't DOS ourselves.
    function ignore(msg, file) {
        // Ignore deuplicates from the same page.
        // Also ignores errors that have already been reported and rethrown.
        for (var i = 0; i < _lastErrors.length; i++) {
            if (msg.indexOf(_lastErrors[i]) > -1)
                return true;
        }

        // Ignore some URLs
        var ignoreURLs = [
            // Chrome extensions
            'extensions/'
        ];

        for (var i = 0; i < ignoreURLs.length; i++) {
            if (file.indexOf(ignoreURLs[i]) === 0)
                return true;
        }

        // Ignore some common errors
        var ignoreMsgs = [
            // Some plugins and extensions
            'top.GLOBALS',
            'originalCreateNotification', // See: http://blog.errorception.com/2012/03/tale-of-unfindable-js-error.html
            'canvas.contentDocument',

            // Probably comes from extensions: http://stackoverflow.com/questions/5913978/cryptic-script-error-reported-in-javascript-in-chrome-and-firefox
            'Script error.',

            // Firefox error when leaving a page before all scripts have finished loading
            // http://stackoverflow.com/questions/192570/firefox-error-loading-script-loading-google-analytics-in-ff2/7686057#7686057
            'Error loading script'
        ];

        for (var i = 0; i < ignoreMsgs.length; i++) {
            if (msg.indexOf(ignoreMsgs[i]) === 0)
                return true;
        }

        return false;
    }

    // matches: ?_dc=12345 or ?12345
    var _defeatCacheRegex = /\?(_dc=)?\d+/;

    // Gather a stacktrace at the current location or from the Error argument.
    // May return null if stacktrace.js isn't available or can't be generated.
    function gatherStackTrace(err) {
        if (window.printStackTrace) {
            try {
                var stackTrace = printStackTrace({e: err});
                if (stackTrace) {
                    var s = [];
                    for (var i=0; i < stackTrace.length; i++) {
                        var line = stackTrace[i];

                        // strip out mothership.js and stacktrace.js
                        if (_filterStacktrace &&
                                line.indexOf("/mothership.js") > -1 || line.indexOf("/stacktrace-0.3.js") > -1)
                            continue;

                        // remove defeat cache and server-session number from URLs
                        // so stack doesn't change between server requests.
                        line = line.replace(_defeatCacheRegex, '?xxx');
                        s.push(line);
                    }

                    return s.join('\n  ');
                }
            }
            catch (e) {
                // ignore
            }
        }
        return null;
    }

    // err is either the thrown Error object or a string message.
    // @return {Boolean} Returns true if the error is logged to mothership, false otherwise.
    function report(err, file, line, gatherStack) {
        file = file || window.location.href;
        line = line || "None";

        // Now figure out the actual error message
        // If it's an event, as triggered in several browsers
        var msg = err;
        if (err.target && err.type) {
            msg = err.type;
        }
        else if (err.message) {
            msg = err.message;
        }

        // Check for string error type
        if (!msg.indexOf) {
            msg = 'Other error: ' + (typeof msg);
        }

        // Filter out some errors
        if (ignore(msg, file))
            return false;

        lastError(msg);

        // A stacktrace can't be generated inside of window.onerror handler
        // See: https://github.com/eriwen/javascript-stacktrace/issues/26
        var stackTrace = gatherStack ? gatherStackTrace(err) : msg;
        if (!stackTrace)
            stackTrace = msg;
        if (err._allocation) {
            stackTrace += '\n\n' + err._allocation;
        }

        console.debug("** uncaught error:", stackTrace);
        if (!_enabled)
            return false;

        var o = {
            username: LABKEY.user ? LABKEY.user.email : "Unknown",
            //site: window.location.host,
            //version: LABKEY.versionString || "Unknown",
            requestURL: file,
            referrerURL: document.URL,
            exceptionMessage: msg,
            stackTrace: stackTrace || msg,
            file: file,
            line: line,
            browser: navigator && navigator.userAgent || "Unknown",
            platform:  navigator && navigator.platform  || "Unknown"
        };

        try {
            send(LABKEY.contextPath + '/mothership/logError.api', o);
            return true;
        }
        catch (e) {
            // ignore error
            return false;
        }
    }

    function reportError(error) {
        report(error, error.fileName, error.lineNumber || error.line, true);
    }

    // Wraps a fn with a try/catch block
    function createWrap(fn) {
        if (!fn)
            return fn;
        function wrap() {
            //console.log("wrap called");
            try {
                return fn.apply(this, arguments);
            }
            catch (e) {
                //console.log("wrap caught error", e);
                if (fn._allocation)
                    e._allocation = fn._allocation;
                reportError(e);
                if (_rethrow)
                    throw e;
            }
        };
        fn._wrapped = wrap;
        if (_gatherAllocation)
        {
            var allocation = gatherStackTrace();
            if (allocation)
                fn._allocation = "Callback Allocation:\n  " + allocation;
        }
        return wrap;
    }

    // Ext.lib.Event is used for DOM element events
    function replace_Ext_lib_Event() {
        if (replace_Ext_lib_Event.initialized)
            return;

        if (window.Ext && window.Ext.lib && window.Ext.lib.Event) {
            console.log("replacing Ext.lib.Event.addListener");
            var on = Ext.lib.Event.addListener;
            var un = Ext.lib.Event.removeListener;

            // Our replacement for addListener
            function addListener(el, eventName, fn) {
                //console.log("Ext.lib.Event.addListener called");
                fn = createWrap(fn);
                return on.call(this, el, eventName, fn);
            }

            // Our replacement for removeListener
            function removeListener(el, eventName, fn) {
                if (fn)
                    return un.call(this, el, eventName, fn._wrapped || fn);
                else
                    return un.call(this, el, eventName, fn);
            }

            Ext.lib.Event.on = Ext.lib.Event.addListener = addListener;
            Ext.lib.Event.un = Ext.lib.Event.removeListener = removeListener;
            replace_Ext_lib_Event.initialized = true;
        }
    }

    // Ext.util.Event is used for document ready event and Ext.Observable events.
    function replace_Ext_util_Event() {
        if (replace_Ext_util_Event.initialized)
            return;

        if (window.Ext && window.Ext.util && window.Ext.util.Event) {
            console.log("replacing Ext.util.Event.addListener");
            var on = Ext.util.Event.prototype.addListener;
            var un = Ext.util.Event.prototype.removeListener;

            // Our replacement for addListener
            function addListener(fn, scope, options) {
                //console.log("Ext.util.Event.addListener called");
                fn = createWrap(fn);
                return on.call(this, fn, scope, options);
            }

            // Our replacement for removeListener
            function removeListener(fn, scope) {
                if (fn)
                    return un.call(this, fn._wrapped || fn, scope);
                else
                    return un.call(this, fn, scope);
            }

            Ext.util.Event.prototype.addListener = addListener;
            Ext.util.Event.prototype.removeListener = removeListener;
            replace_Ext_util_Event.initialized = true;
        }
    }

    function replace_Ext_EventManager() {
        if (replace_Ext_EventManager.initialized)
            return;

        if (window.Ext && window.Ext.EventManager) {
            console.log("replacing Ext.EventManager.addListener");
            var on = Ext.EventManager.addListener;
            var un = Ext.EventManager.removeListener;

            // Our replacement for addListener
            function addListener(element, eventName, fn, scope, options) {
                //console.log("Ext.EventManager.addListener called");
                fn = createWrap(fn);
                return on(element, eventName, fn, scope, options);
            }

            // Our replacement for removeListener
            function removeListener(el, eventName, fn, scope) {
                if (fn)
                    return un(el, eventName, fn._wrapped || fn, scope);
                else
                    return un(el, eventName, fn, scope);
            }

            Ext.EventManager.on = Ext.EventManager.addListener = addListener;
            Ext.EventManager.un = Ext.EventManager.removeListener = removeListener;
            replace_Ext_EventManager.initialized = true;
        }
    }

    function replace_Ext_lib_Ajax() {
        if (replace_Ext_lib_Ajax.initialized)
            return;

        if (window.Ext && window.Ext.lib && window.Ext.lib.Ajax) {
            console.log("replacing Ext.lib.Ajax.request");
            var r = Ext.lib.Ajax.request;

            function request(method, uri, cb, data, options) {
                //console.log("Ext.lib.Ajax.request called");
                cb.success = createWrap(cb.success);
                cb.failure = createWrap(cb.failure);
                return r(method, uri, cb, data, options);
            }

            Ext.lib.Ajax.request = request;
            replace_Ext_lib_Ajax.initialized = true;
        }
    }

    // intercept calls to Ext.*.addListener and replace the callback fn
    // with a wrapped fn that adds a try/catch and logs uncaught errors.
    function replaceListeners() {
        replace_Ext_lib_Event();
        replace_Ext_util_Event();
        replace_Ext_EventManager();
        replace_Ext_lib_Ajax();

        if (replace_Ext_util_Event.initialized
            && replace_Ext_lib_Event.initialized
            && replace_Ext_EventManager.initialized
            && replace_Ext_lib_Ajax.initialized) {
            console.log("all listeners replaced");
        }
        else {
            setTimeout(replaceListeners, 10);
        }
    }

    function registerOnError() {
        var currentErrorFn = window.onerror;
        var q = report;
        if (typeof currentErrorFn == "function") {
            q = function (msg, file, line, col) {
                report(msg, file, line, col);
                currentErrorFn(msg, file, line, col);
            }
        }
        window.onerror = q;
    }

    function register() {
        registerOnError();
        replaceListeners();
    }

    register();

    return {
        /**
         * @cfg {Error} error The error object to log to mothership.
         */
        logError : reportError,

        /** Turn error reporting on or off. */
        enable  : function (b) { _enabled = b; },

        /** Turn rethrowing Errors on or off. */
        rethrow : function (b) { _rethrow = b; }
    };
})();

