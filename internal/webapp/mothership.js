/*
 * Copyright (c) 2012-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

// Must not depend on ExtJS or LABKEY APIs.
// uses stacktrace.js if available.
LABKEY.Mothership = (function () {
    "use strict";

    // When _gatherAllocation is true, a stacktrace is generated where the
    // callback function is registered.  Generating the allocation stacktraces
    // can reduce performance.
    var _gatherAllocation = false;

    // When _generateStacktrace is true, a stacktrace is generated in the error
    // handler as a last-ditch effort to get meaningful error reporting.
    var _generateStacktrace = true;

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
    var _hookExt3 = false;
    var _hookExt4 = false;
    var _hookLabKey = true;

    // Wait 10 milliseconds between hook install attempts.
    var _delayMillis = 100;

    // Maximum number of milliseconds to attempt hook installation before giving up.
    var _maxDelayMillis = 4*1000;


    function log() {
        if (_debug && console && console.debug)
            console.debug.apply(console, arguments);
    }

    function warn() {
        if (console && console.warn)
            console.warn.apply(console, arguments);
        else
            alert(arguments);
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
        log("submitting error", data);
        var postData = JSON.stringify(data);

        var req = new XMLHttpRequest();
        if (!req) {
            warn("** Failed to send error report: ", data);
            return;
        }
        req.open('POST', url, true);
        req.setRequestHeader('Content-Type', 'application/json');
        req.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
        if (LABKEY.CSRF)
            req.setRequestHeader('X-LABKEY-CSRF', LABKEY.CSRF);
        var transId = req.send(postData);
        return transId;
    }

    // Record the last few error message
    function lastError(msg) {
        if (!msg)
            return;
        msg = ""+msg;
        if (_lastErrors.push({msg: msg, stackTrace: undefined}) > _maxLastErrors)
            _lastErrors = _lastErrors.slice(1);
    }

    // UNDONE: Throttle number of errors so we don't DOS ourselves.
    function ignore(msg, file) {
        var i;

        // Ignore duplicates from the same page.
        // Also ignores errors that have already been reported and rethrown.
        for (i = 0; i < _lastErrors.length; i++) {
            var lastError = _lastErrors[i];
            if (lastError && lastError.msg && msg.indexOf(lastError.msg) > -1)
                return true;
        }

        // Ignore some URLs
        var ignoreURLs = [
            // Chrome extensions
            'extensions/'
        ];

        for (i = 0; i < ignoreURLs.length; i++) {
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

        for (i = 0; i < ignoreMsgs.length; i++) {
            var ignoreMsg = ignoreMsgs[i];
            if (ignoreMsg.indexOf(msg) === 0)
                return true;
        }

        return false;
    }

    // matches: ?_dc=12345 or ?12345
    var _defeatCacheRegex = /\?(_dc=)?\d+/;

    function filterTrace(stackframe) {
        "use strict";

        var fileName = stackframe.getFileName();

        // remove stack elements for errors generated from within the Chrome console
        if (!fileName)
            return false;

        // remove stacktrace-1.3.0.min.js and mothership.js from the stack
        if (_filterStacktrace &&
                fileName.indexOf("/stacktrace-") > -1 ||
                fileName.indexOf("/mothership.js") != -1)
            return false;

        // remove defeat cache and server-session number from URLs
        // so stack doesn't change between server requests.
        stackframe.setFileName(fileName.replace(_defeatCacheRegex, ''));

        return true;
    }

    function errback(err) {
        console.debug(err.message);
    }

    function htmlEncode(s) {
        if (!s) return "";

        var htmlEncodeRules = {
            "&": "&#38;",
            "<": "&#60;",
            ">": "&#62;",
            '"': "&#34;",
            "'": "&#39;",
            "/": "&#47;"
        };
        var htmlEncodeRe = /[&<>"'\/]/g;

        return s.toString().replace(htmlEncodeRe, function(m) { return htmlEncodeRules[m] || m; });
    }

    function whitespace(s) {
        if (!s) return "";

        var whitespaceRules = {' ': '&nbsp;', '\n': '<br>', '\r': '' };
        var whitespaceRe = / |\n|\r/g;
        return s.toString().replace(whitespaceRe, function (m) { return whitespaceRules[m] || m; });
    }

    function renderLastErrors() {
        var html =
                "<div id='mothership-errors' style='" +
                "background-color:lightyellow; opacity: 0.85; border:1px solid grey; padding: .5em; " +
                "font-family:monospace; font-size: small; " +
                "position: fixed; top: 0; left: 0; width: 100%; z-index: 1000" +
                "'>";

        if (_lastErrors.length) {
            html += "LABKEY.Mothership most recent client-side errors:";
            for (var i = 0; i < _lastErrors.length; i++) {
                html += "<hr style='margin: 0.2em;'>";

                var lastError = _lastErrors[i];
                if (lastError.stackTrace)
                    html += "<div>" + whitespace(htmlEncode(lastError.stackTrace)) + "</div>";
                else
                    html += "<div>" + whitespace(htmlEncode(lastError.msg)) + "</div>";
            }
        }
        else {
            html += "LABKEY.Mothership didn't detect any client-side errors";
        }

        html += "</div>";

        var el = document.getElementById('mothership-errors');
        if (el) {
            el.innerHTML = html;
        }
        else {
            el = window.document.createElement("DIV");
            el.innerHTML = html;
            window.document.body.appendChild(el);
        }
    }

    function _report(msg, file, line, column, stackTrace) {
        "use strict";

        // Add the stackTrace to the corresponding lastError if it doesn't include a stackTrace yet.
        // The stackTrace will be needed in renderLastErrors
        if (stackTrace) {
            for (var i = 0; i < _lastErrors.length; i++) {
                var lastError = _lastErrors[i];
                if (lastError.msg === msg && lastError.stackTrace === undefined) {
                    lastError.stackTrace = stackTrace;
                    break;
                }
            }
        }

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
                column: column,
                browser: navigator && navigator.userAgent || "Unknown",
                platform:  navigator && navigator.platform  || "Unknown"
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

    // err is either the thrown Error object, ErrorEvent, or a string message.
    // @return {Boolean} Returns true if the error is logged to mothership, false otherwise.
    function report(err, file, line, column, errorObj) {
        //log("** report", err, errorObj);
        file = file || window.location.href;
        line = line || "None";

        // Now figure out the actual error message
        // or if it's an event, as triggered in several browsers
        var msg = err;
        if (err.message) {
            msg = err.message;
        }
        else if (err.target && err.type) {
            msg = err.type;
        }

        // Check for string error type
        if (!msg.indexOf) {
            var s = msg.toString();
            if (s == '[object Object]')
                s = JSON.stringify(msg);
            msg = 'Other error: ' + s;
        }

        // Filter out some errors
        if (ignore(msg, file))
            return false;

        lastError(msg);

        //log("** Uncaught error:", msg, '\n\n', stackTrace);
        if (!_enabled)
            return false;

        var error = errorObj || err.error || err;
        var promise = null;
        // Sadly, IE10 doesn't support Promises and I don't care enough to add a polyfill
        if (window.Promise) {
            if (error instanceof Error) {
                promise = StackTrace.fromError(error, {filter: filterTrace});
            }
            else if (_generateStacktrace) {
                promise = StackTrace.get({filter: filterTrace});
            }
        }
        if (!promise) {
            // We have no Error or the browser doesn't support Promises -- just submit the message that we have
            return _report(msg, file, line, column, null);
        }

        // wait for allocation stack
        if (err._allocationPromise) {
            //console.log("chaining allocationPromise");
            promise.then(function () {
                return err._allocationPromise;
            });
        }

        promise.then(function (stackframes) {
            var stackTrace = msg;
            if (stackframes && stackframes.length) {
                stackTrace += '\n  ' + stackframes.join('\n  ');
            }

            if (err._allocation) {
                stackTrace += '\n\n' + err._allocation;
            }

            _report(msg, file, line, column, stackTrace);

        }).catch(errback);
        return true;
    }

    function reportError(error) {
        if (!error)
            return;
        report(error, error && error.fileName, error && (error.lineNumber || error.line), error && error.columnNumber, error);
    }

    // Wraps a fn with a try/catch block
    function createWrap(fn) {
        if (!fn)
            return fn;

        // Avoid double-wrapping the same callback
        if (fn._wrapped)
            return fn._wrapped;

        function wrap() {
            //log("wrap called: ", this);

            // BUGBUG: See note about collecting Promise "then" allocation stacktrace
            //if (_gatherAllocation && this instanceof Promise) {
            //    if (fn._allocationPromise)
            //        this._allocationPromise = fn._allocationPromise;
            //    if (fn._allocation)
            //        this._allocation = fn._allocation;
            //}

            try {
                return fn.apply(this, arguments);
            }
            catch (e) {
                //log("wrap caught error", e);
                if (fn._allocationPromise)
                    e._allocationPromise = fn._allocationPromise;
                if (fn._allocation)
                    e._allocation = fn._allocation;
                reportError(e);
                if (_rethrow)
                    throw e;
            }
        }
        fn._wrapped = wrap;
        if (_gatherAllocation && window.Promise)
        {
            //log("Gathering allocation stack...");
            fn._allocationPromise = StackTrace.get({filter: filterTrace, offline: true}).then(function (stackframes) {
                delete fn._allocationPromise;
                if (stackframes && stackframes.length) {
                    fn._allocation = "Callback Allocation:\n  " + stackframes.join('\n  ');
                    //log(fn._allocation);
                }
            }).catch(errback);
        }
        return wrap;
    }

    function replace_LABKEY_Ajax() {
        if (replace_LABKEY_Ajax.initialized)
            return 1;

        if (window.LABKEY && window.LABKEY.Ajax && window.LABKEY.Ajax) {
            log("replacing LABKEY.Ajax.request");
            var r = LABKEY.Ajax.request;

            var request = function(config) {
                //log("LABKEY.Ajax.request called");
                config.success = createWrap(config.success);
                config.failure = createWrap(config.failure);
                return r.call(this, config);
            };

            LABKEY.Ajax.request = request;
            replace_LABKEY_Ajax.initialized = true;
            return 1;
        }
        return 0;
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
            var addListener = function(el, eventName, fn) {
                //log("Ext.lib.Event.addListener called");
                fn = createWrap(fn);
                return on.call(this, el, eventName, fn);
            };

            // Our replacement for removeListener
            var removeListener = function(el, eventName, fn) {
                if (fn)
                    return un.call(this, el, eventName, fn._wrapped || fn);
                else
                    return un.call(this, el, eventName, fn);
            };

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
            var addListener = function(fn, scope, options) {
                //log("Ext.util.Event.addListener called");
                fn = createWrap(fn);
                return on.call(this, fn, scope, options);
            };

            // Our replacement for removeListener
            var removeListener = function(fn, scope) {
                //log("Ext.util.Event.removeListener called");
                if (fn)
                    return un.call(this, fn._wrapped || fn, scope);
                else
                    return un.call(this, fn, scope);
            };

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
            var addListener = function(element, eventName, fn, scope, options) {
                //log("Ext.EventManager.addListener called");
                fn = createWrap(fn);
                return on(element, eventName, fn, scope, options);
            };

            // Our replacement for removeListener
            var removeListener = function(el, eventName, fn, scope) {
                if (fn)
                    return un(el, eventName, fn._wrapped || fn, scope);
                else
                    return un(el, eventName, fn, scope);
            };

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

            var request = function(method, uri, cb, data, options) {
                log("Ext.lib.Ajax.request called");
                cb.success = createWrap(cb.success);
                cb.failure = createWrap(cb.failure);
                return r.call(this, method, uri, cb, data, options);
            };

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
            var addListener = function(element, eventName, fn, scope, options) {
                //log("Ext4.EventManager.addListener called");
                fn = createWrap(fn);
                return on(element, eventName, fn, scope, options);
            };

            // Our replacement for removeListener
            var removeListener = function(el, eventName, fn, scope) {
                if (fn)
                    return un(el, eventName, fn._wrapped || fn, scope);
                else
                    return un(el, eventName, fn, scope);
            };

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

    function replaceLabKeyListeners(attempt) {
        "use strict";
        if (replaceLabKeyListeners.initialized) {
            log('LABKEY listeners already initialized');
            return;
        }

        if (attempt * _delayMillis >= _maxDelayMillis) {
            log('LABKEY listeners not initialized after ' + (_maxDelayMillis/1000) + ' seconds; giving up.');
            replaceLabKeyListeners.initialized = true;
            return;
        }

        if (!window.LABKEY) {
            log('deferring LABKEY replacements... attempt ' + attempt);
            setTimeout(function () { replaceLabKeyListeners(attempt+1); }, _delayMillis);
        } else {
            log('replacing LABKEY listeners... attempt ' + attempt);

            var expected = 1;
            var initialized = 0;

            initialized += replace_LABKEY_Ajax();

            if (expected && expected == initialized) {
                log("all LABKEY listeners replaced after " + attempt + " attempts.");
                replaceLabKeyListeners.initialized = true;
            } else {
                log("will attempt LABKEY replacements again shortly...");
                setTimeout(function () { replaceLabKeyListeners(attempt+1); }, _delayMillis);
            }
        }
    }

    function registerOnError() {
        window.addEventListener('error', report);

        // catch unhandled errors in promises
        // https://developer.mozilla.org/en-US/docs/Web/Events/unhandledrejection
        if (window.hasOwnProperty("onunhandledrejection"))
        {
            window.addEventListener('unhandledrejection', function (event) {
                //log("Unhandled Error in Promise ", event);
                var promise = event.promise;
                var reason = event.reason;

                // BUGBUG: See note about collecting Promise "then" allocation stacktrace
                //if (promise._allocation)
                //    reason._allocation = promise._allocation;
                reportError(reason);
            });

            window.addEventListener('rejectionhandled', function (event) {
                "use strict";
                log("Rejection handled", event);
            });
        }
    }

    function register() {
        // Default stacktrace is only 10 frames on Google Chrome
        Error.stackTraceLimit = 20;
        registerOnError();

        // BUGBUG: Unfortunately, I'm not sure how to collect the allocation stacktrace where the
        // "then" callback was created. Wrapping Promise.prototype.then() doesn't seem to work.
        //if (Promise.prototype.then)
        //    Promise.prototype.then = createWrap(Promise.prototype.then);
    }

    register();

    return {
        /**
         * @cfg {Error} error The error object to log to mothership.
         */
        logError : reportError,

        hookExt3 : function () { if (_hookExt3) { replaceExt3Listeners(0); } },

        hookExt4 : function () { if (_hookExt4) { replaceExt4Listeners(0); } },

        /**
         * Hooking LABKEY.Ajax is generally not useful since, unlike Ext3 listeners, we don't
         * add try/catch around the callback handler and swallow all errors. The uncaught
         * exception handler will typically catch any errors thrown within LABKEY.Ajax callbacks.
         */
        hookLabKey : function () { if (_hookLabKey) { replaceLabKeyListeners(0); } },

        /** Turn error reporting on or off (default is on). */
        enable  : function (b) { _enabled = b; },

        /** Turn rethrowing Errors on or off (default is on). */
        setRethrow : function (b) { _rethrow = b; },

        /** Turn verbose logging on or off (default is off). */
        setDebug : function (b) { _debug = b; },

        /** Turn reporting errors to central mothership on or off (default is on). */
        setReportErrors : function (b) { _mothership = b; },

        /** Render a little window displaying the 10 most recent errors collected. */
        renderLastErrors : renderLastErrors
    };
})();

