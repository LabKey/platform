/*
 * Copyright (c) 2007-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

// Must not depend on ExtJS or LABKEY APIs.
// uses stacktrace.js if available.
LABKEY.Mothership = (function () {
    var enabled = true;

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
        //console.debug("** Sent error report", data);
        return transId;
    }

    // UNDONE: Throttle number of errors so we don't DOS ourselves.
    function ignore(msg, file) {
        // Ignore some URLs
        var ignoreURLs = [
            // Chrome extensions
            'extensions/',
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

    function gatherStackTrace(err) {
        if (window.printStackTrace) {
            try {
                var stackTrace = printStackTrace({e: err});
                if (stackTrace) {
                    // remove top of artificial stacktrace
                    //if (!err)
                    //    stackTrace = stackTrace.slice(3);
                    return stackTrace.join('\n  ');
                }
            }
            catch (e) {
                // ignore
            }
        }
    }

    // err is either the thrown Error object or a string message.
    // @return {Boolean} Returns false if the report is not logged.
    function report(err, file, line, gatherStack) {
        if (!enabled)
            return;

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

        // A stacktrace can't be generated inside of window.onerror handler
        // See: https://github.com/eriwen/javascript-stacktrace/issues/26
        var stackTrace = gatherStack ? gatherStackTrace(err) : msg;
        if (err._allocation) {
            stackTrace += '\n\nListener Allocation:\n' + err._allocation;
        }

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
            platform:  navigator && navigator.platform  || "Unknown",
        }

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

    // Ext.lib.Event is used for DOM element events
    function replaceListeners_Ext_lib_Event() {
        if (replaceListeners_Ext_lib_Event.initialized)
            return;

        if (window.Ext && window.Ext.lib && window.Ext.lib.Event) {
            console.log("replacing Ext.lib.Event.addListener");
            var on = Ext.lib.Event.addListener;
            var un = Ext.lib.Event.removeListener;

            // Our replacement for addListener
            function addListener(el, eventName, fn) {
                //console.log("Ext.lib.Event.addListener called");
                function wrap() {
                    //console.log("Ext.lib.Event.addListener.wrap called");
                    try {
                        return fn.apply(this, arguments);
                    }
                    catch (e) {
                        //console.log("Ext.lib.Event.addLister.wrap caught error", e);
                        if (fn._allocation)
                            e._allocation = fn._allocation;
                        reportError(e);
                    }
                }
                if (fn) {
                    fn._wrapped = wrap;
                    fn._allocation = gatherStackTrace();
                    return on.call(this, el, eventName, wrap);
                }
                else {
                    return on.call(this, el, eventName, fn);
                }
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
            replaceListeners_Ext_lib_Event.initialized = true;
        }
    }

    // Ext.util.Event is used for document ready event and Ext.Observable events.
    function replaceListeners_Ext_util_Event() {
        if (replaceListeners_Ext_util_Event.initialized)
            return;

        if (window.Ext && window.Ext.util && window.Ext.util.Event) {
            console.log("replacing Ext.util.Event.addListener");
            var on = Ext.util.Event.prototype.addListener;
            var un = Ext.util.Event.prototype.removeListener;

            // Our replacement for addListener
            function addListener(fn, scope, options) {
                //console.log("Ext.util.Event.addListener called");
                function wrap() {
                    //console.log("Ext.util.Event.addListener.wrap called");
                    try {
                        return fn.apply(this, arguments);
                    }
                    catch (e) {
                        //console.log("Ext.util.Event.addLister.wrap caught error", e);
                        if (fn._allocation)
                            e._allocation = fn._allocation;
                        reportError(e);
                    }
                }
                if (fn) {
                    fn._wrapped = wrap;
                    fn._allocation = gatherStackTrace();
                    return on.call(this, wrap, scope, options);
                }
                else {
                    return on.call(this, fn, scope, options);
                }
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
            replaceListeners_Ext_util_Event.initialized = true;
        }
    }

    function replaceListeners_Ext_EventManager() {
        if (replaceListeners_Ext_EventManager.initialized)
            return;

        if (window.Ext && window.Ext.EventManager) {
            console.log("replacing Ext.EventManager.addListener");
            var on = Ext.EventManager.addListener;
            var un = Ext.EventManager.removeListener;

            // Our replacement for addListener
            function addListener(element, eventName, fn, scope, options) {
                //console.log("Ext.EventManager.addListener called");
                function wrap() {
                    //console.log("Ext.EventManager.addListener.wrap called");
                    try {
                        return fn.apply(this, arguments);
                    }
                    catch (e) {
                        //console.log("Ext.EventManager.addLister.wrap caught error", e);
                        if (fn._allocation)
                            e._allocation = fn._allocation;
                        reportError(e);
                    }
                }
                if (fn) {
                    fn._wrapped = wrap;
                    fn._allocation = gatherStackTrace();
                    return on(element, eventName, wrap, scope, options);
                }
                else {
                    return on(element, eventName, fn, scope, options);
                }
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
            replaceListeners_Ext_EventManager.initialized = true;
        }
    }

    // intercept calls to Ext.*.addListener and replace the callback fn
    // with a wrapped fn that adds a try/catch and logs uncaught errors.
    function replaceListeners() {
        replaceListeners_Ext_lib_Event();
        replaceListeners_Ext_util_Event();
        replaceListeners_Ext_EventManager();

        if (replaceListeners_Ext_util_Event.initialized &&
            replaceListeners_Ext_lib_Event.initialized &&
            replaceListeners_Ext_EventManager.initialized) {

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
            q = function (msg, file, line) {
                report(msg, file, line);
                currentErrorFn(msg, file, line);
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

        /** Turn on error reporting. */
        enable  : function () { enabled = true; },

        /** Turn off error reporting. */
        disable : function () { enabled = false; },
    };
})();

