/*
 * Copyright (c) 2012-2014 LabKey Corporation
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

    // When true, errors will be sent to the LabKey Server then bounced to the configured mothership server for error collection.
    // When false, errors will be sent to the LabKey Server's log file.
    var _mothership = true;

    // When enabled, uncaught errors will be sent to the local LabKey server.
    // When not enabled, no logging is done but the uncaught error is still printed to the console.
    var _enabled = true;

    // Print messages during initialization to the console.
    var _debug = false;

    // Rethrow caught exceptions.
    var _rethrow = true;

    // Stack of last _maxLastErrors messages
    var _lastErrors = [];
    var _maxLastErrors = 10;

    // Remove mothership.js and stacktrace.js from the stacktrace.
    var _filterStacktrace = true;

    // Allow hooking Ext3 and Ext4 callbacks
    var _hookExt3 = true;
    var _hookExt4 = true;

    // Wait 10 milliseconds between hook install attempts.
    var _delayMillis = 100;

    // Maximum number of milliseconds to attempt hook installation before giving up.
    var _maxDelayMillis = 4*1000;


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

    function log(msg) {
        if (_debug)
            console.debug(msg);
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
        // Ignore duplicates from the same page.
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
                                line.indexOf("/mothership.js") > -1 || line.indexOf("/stacktrace-0.6.0.js") > -1)
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
    function report(err, file, line, gatherStack, errorObj) {
        file = file || window.location.href;
        line = line || "None";

        // Now figure out the actual error message
        // If it's an event, as triggered in several browsers
        var msg = err;
        if (err.target && err.type) {
            msg = err.type;
        }
        else if (err.message) {
            msg = err.toString();
        }

        // Check for string error type
        if (!msg.indexOf) {
            msg = 'Other error: ' + (typeof msg);
        }

        // Filter out some errors
        if (ignore(msg, file))
            return false;

        lastError(msg);

        var stackTrace;
        if (gatherStack) {
            if (errorObj && errorObj.stack)
                stackTrace = errorObj.stack;
            else
                stackTrace = gatherStackTrace(err);
        }
        if (!stackTrace)
            stackTrace = msg;
        if (err._allocation) {
            stackTrace += '\n\n' + err._allocation;
        }

        console.debug("** Uncaught error:", msg, '\n\n', stackTrace);
        if (!_enabled)
            return false;

        // CONSIDER: Remove the _mothership flag and use a site-setting to suppress mothership reporting of client-side exceptions.
        var url;
        var o;
        if (_mothership || (LABKEY.user && LABKEY.user.isGuest)) {
            url = LABKEY.contextPath + '/admin/logClientException.api';
            o = {
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
        } else {
            url = LABKEY.contextPath + '/admin/log.api';
            o = {
                message: msg + ":\n\n" + stackTrace,
                level: 'error'
            };
        }

        try {
            send(url, o);
            return true;
        }
        catch (e) {
            // ignore error
            return false;
        }
    }

    function reportError(error) {
        report(error, error.fileName, error.lineNumber || error.line, true, error);
    }

    // Wraps a fn with a try/catch block
    function createWrap(fn) {
        if (!fn)
            return fn;

        // Avoid double-wrapping the same callback
        if (fn._wrapped)
            return fn._wrapped;

        function wrap() {
            //log("wrap called");
            try {
                return fn.apply(this, arguments);
            }
            catch (e) {
                //log("wrap caught error", e);
                if (fn._allocation)
                    e._allocation = fn._allocation;
                reportError(e);
                if (_rethrow)
                    throw e;
            }
        }
        fn._wrapped = wrap;
        if (_gatherAllocation)
        {
            var allocation;
            if (Error.prototype.hasOwnProperty("stack")) {
                var e = new Error();
                allocation = e.stack
            } else {
                allocation = gatherStackTrace();
            }
            if (allocation)
                fn._allocation = "Callback Allocation:\n  " + allocation;
        }
        return wrap;
    }

    // Ext.lib.Event is used for DOM element events
    function replace_Ext_lib_Event() {
        if (replace_Ext_lib_Event.initialized)
            return 1;

        if (window.Ext && window.Ext.lib && window.Ext.lib.Event) {
            log("replacing Ext.lib.Event.addListener");
            var on = Ext.lib.Event.addListener;
            var un = Ext.lib.Event.removeListener;

            // Our replacement for addListener
            function addListener(el, eventName, fn) {
                //log("Ext.lib.Event.addListener called");
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
            return 1;
        }

        return 0;
    }

    // Ext.util.Event is used for document ready event and Ext.Observable events.
    function replace_Ext_util_Event() {
        if (replace_Ext_util_Event.initialized)
            return 1;

        if (window.Ext && window.Ext.util && window.Ext.util.Event) {
            log("replacing Ext.util.Event.addListener");
            var on = Ext.util.Event.prototype.addListener;
            var un = Ext.util.Event.prototype.removeListener;

            // Our replacement for addListener
            function addListener(fn, scope, options) {
                //log("Ext.util.Event.addListener called");
                fn = createWrap(fn);
                return on.call(this, fn, scope, options);
            }

            // Our replacement for removeListener
            function removeListener(fn, scope) {
                //log("Ext.util.Event.removeListener called");
                if (fn)
                    return un.call(this, fn._wrapped || fn, scope);
                else
                    return un.call(this, fn, scope);
            }

            Ext.util.Event.prototype.addListener = addListener;
            Ext.util.Event.prototype.removeListener = removeListener;
            replace_Ext_util_Event.initialized = true;
            return 1;
        }
        return 0;
    }

    function replace_Ext_EventManager() {
        if (replace_Ext_EventManager.initialized)
            return 1;

        if (window.Ext && window.Ext.EventManager) {
            log("replacing Ext.EventManager.addListener");
            var on = Ext.EventManager.addListener;
            var un = Ext.EventManager.removeListener;

            // Our replacement for addListener
            function addListener(element, eventName, fn, scope, options) {
                //log("Ext.EventManager.addListener called");
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
            return 1;
        }
        return 0;
    }

    function replace_Ext_lib_Ajax() {
        if (replace_Ext_lib_Ajax.initialized)
            return 1;

        if (window.Ext && window.Ext.lib && window.Ext.lib.Ajax) {
            log("replacing Ext.lib.Ajax.request");
            var r = Ext.lib.Ajax.request;

            function request(method, uri, cb, data, options) {
                log("Ext.lib.Ajax.request called");
                cb.success = createWrap(cb.success);
                cb.failure = createWrap(cb.failure);
                return r.call(this, method, uri, cb, data, options);
            }

            Ext.lib.Ajax.request = request;
            replace_Ext_lib_Ajax.initialized = true;
            return 1;
        }
        return 0;
    }

    // Ext4.util.Event is used for document ready event and Ext.Observable events.
    function replace_Ext4_util_Event() {
        if (replace_Ext4_util_Event.initialized)
            return 1;

        if (window.Ext4 && window.Ext4.util && window.Ext4.util.Event) {
            log("replacing Ext4.util.Event.addListener");
            Ext4.override(Ext4.util.Event, {
                addListener: function (fn, scope, options) {
                    fn = createWrap(fn);
                    return this.callParent([fn, scope, options]);
                },

                removeListener: function (fn, scope) {
                    if (fn)
                        return this.callParent([fn._wrapped || fn, scope]);
                    else
                        return this.callParent([fn, scope]);
                }
            });

            replace_Ext4_util_Event.initialized = true;
            return 1;
        }

        return 0;
    }

    //Ext4.EventManager.addListener
    //Ext4.EventManager.removeListener
    function replace_Ext4_EventManager() {
        if (replace_Ext4_EventManager.initialized)
            return 1;

        if (window.Ext4 && window.Ext4.EventManager) {
            log("replacing Ext4.EventManager.addListener");
            var on = Ext4.EventManager.addListener;
            var un = Ext4.EventManager.removeListener;

            // Our replacement for addListener
            function addListener(element, eventName, fn, scope, options) {
                //log("Ext4.EventManager.addListener called");
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

            Ext4.EventManager.on = Ext4.EventManager.addListener = addListener;
            Ext4.EventManager.un = Ext4.EventManager.removeListener = removeListener;
            replace_Ext4_EventManager.initialized = true;
            return 1;
        }
        return 0;
    }

    //Ext4.dom.CompositeElementLite
    function replace_Ext4_dom_CompositeElementLite() {
        if (replace_Ext4_dom_CompositeElementLite.initialized)
            return 1;

        if (window.Ext4 && window.Ext4.dom && window.Ext4.dom.CompositeElementLite) {
            log("replacing Ext4.dom.CompositeElementLite.addListener");
            Ext4.override(Ext4.dom.CompositeElementLite, {
                addListener: function (ename, fn, scope, options) {
                    log("Ext4.dom.CompositeElementLite.addListener called");
                    fn = createWrap(fn);
                    return this.callParent([ename, fn, scope, options]);
                },

                removeListener: function (ename, fn, scope) {
                    if (fn)
                        return this.callParent([ename, fn._wrapped || fn, scope]);
                    else
                        return this.callParent([ename, fn, scope]);
                }
            });

            replace_Ext4_dom_CompositeElementLite.initialized = true;
            return 1;
        }
        return 0;
    }

    //Ext4.util.Observable
    function replace_Ext4_util_Observable() {
        if (replace_Ext4_util_Observable.initialized)
            return 1;

        if (window.Ext4 && window.Ext4.util && window.Ext4.util.Observable) {
            log("replacing Ext4.util.Observable.addListener");
            Ext4.override(Ext4.util.Observable, {
                addListener: function (ename, fn, scope, options) {
                    //log("Ext4.util.Observable.addListener called");
                    fn = createWrap(fn);
                    return this.callParent([ename, fn, scope, options]);
                },

                removeListener: function (ename, fn, scope) {
                    if (fn)
                        return this.callParent([ename, fn._wrapped || fn, scope]);
                    else
                        return this.callParent([ename, fn, scope]);
                }
            });

            replace_Ext4_util_Observable.initialized = true;
            return 1;
        }
        return 0;
    }

    function replaceExt3Listeners(attempt) {
        if (replaceExt3Listeners.initialized) {
            log('Ext3 listeners already initialized');
            return;
        }

        if (attempt * _delayMillis >= _maxDelayMillis) {
            log('Ext3 listeners not initialized after ' + (_maxDelayMillis/1000) + ' seconds; giving up.');
            replaceExt3Listeners.initialized = true;
            return;
        }

        if (!window.Ext) {
            log('deferring Ext3 replacements... attempt ' + attempt);
            setTimeout(function () { replaceExt3Listeners(attempt+1); }, _delayMillis);
        } else {
            log('replacing Ext3 listeners... attempt ' + attempt);

            var expected = 4;
            var initialized = 0;

            initialized += replace_Ext_lib_Event();
            initialized += replace_Ext_util_Event();
            initialized += replace_Ext_EventManager();
            initialized += replace_Ext_lib_Ajax();

            if (expected && expected == initialized) {
                log("all Ext3 listeners replaced after " + attempt + " attempts.");
                replaceExt3Listeners.initialized = true;
            } else {
                log("will attempt Ext3 replacements again shortly...");
                setTimeout(function () { replaceExt3Listeners(attempt+1); }, _delayMillis);
            }
        }
    }

    function replaceExt4Listeners(attempt) {
        if (replaceExt4Listeners.initialized) {
            log('Ext4 listeners already initialized');
            return;
        }

        if (attempt * _delayMillis >= _maxDelayMillis) {
            log('Ext4 listeners not initialized after ' + (_maxDelayMillis/1000) + ' seconds; giving up.');
            replaceExt4Listeners.initialized = true;
            return;
        }

        if (!window.Ext4) {
            log('deferring Ext4 replacements... attempt ' + attempt);
            setTimeout(function () { replaceExt4Listeners(attempt+1); }, _delayMillis);
        } else {
            log('replacing Ext4 listeners... attempt ' + attempt);

            var expected = 4;
            var initialized = 0;

            initialized += replace_Ext4_util_Event();
            initialized += replace_Ext4_EventManager();
            initialized += replace_Ext4_dom_CompositeElementLite();
            initialized += replace_Ext4_util_Observable();

            if (expected && expected == initialized) {
                log("all Ext4 listeners replaced after " + attempt + " attempts.");
                replaceExt4Listeners.initialized = true;
            } else {
                log("will attempt Ext4 replacements again shortly...");
                setTimeout(function () { replaceExt4Listeners(attempt+1); }, _delayMillis);
            }
        }
    }

    function registerOnError() {
        var currentErrorFn = window.onerror;
        var q = report;
        if (typeof currentErrorFn == "function") {
            q = function (msg, file, line, col, errorObj) {
                log(arguments);
                report(msg, file, line, col, !!errorObj, errorObj);
                currentErrorFn(msg, file, line, col, errorObj);
            }
        }
        window.onerror = q;
    }

    function register() {
        registerOnError();
    }

    register();

    return {
        /**
         * @cfg {Error} error The error object to log to mothership.
         */
        logError : reportError,

        hookExt3 : function () { if (_hookExt3) { replaceExt3Listeners(0); } },

        hookExt4 : function () { if (_hookExt4) { replaceExt4Listeners(0); } },

        /** Turn error reporting on or off (default is on). */
        enable  : function (b) { _enabled = b; },

        /** Turn rethrowing Errors on or off (default is on). */
        setRethrow : function (b) { _rethrow = b; },

        /** Turn verbose logging on or off (default is off). */
        setDebug : function (b) { _debug = b; },

        /** Turn reporting errors to central mothership on or off (default is on). */
        setReportErrors : function (b) { _mothership = b; }
    };
})();

