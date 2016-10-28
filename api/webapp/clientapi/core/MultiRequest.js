/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2009-2016 LabKey Corporation
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

/**
 * Make multiple ajax requests and invokes a callback when all are complete.
 * Requests are added as [function, config] array pairs where the config object
 * is passed as the argument to the request function.  The request function's config
 * object argument must accept a success callback named 'success' and a failure
 * callback named 'failure'.
 * @class Make multiple ajax requests and fires an event when all are complete.
 * @memberOf LABKEY
 *
 * @param [config] Either an array of [function, config] array pairs
 * to be added or a config object with the shape:
 * <ul>
 * <li>listeners: a config object containing event handlers.
 * <li>requests: an array of [function, config] array pairs to be added.
 * </ul>
 * @example
 var config = {
   schemaName : "assay",
   queryName : protocolName + " Data",
   containerPath : "/Test",
   success: function (data, options, response) {
       console.log("selectRows success: " + data.rowCount);
   },
   failure: function (response, options) {
       console.log("selectRows failure");
   },
   scope: scope // scope to execute success and failure callbacks in.
 };

 // add the requests and config arguments one by one
 var multi = new LABKEY.MultiRequest();
 var requestScope = ... // scope to execute the request function in.
 multi.add(LABKEY.Query.selectRows, config, requestScope);
 multi.add(LABKEY.Query.selectRows, config, requestScope);
 multi.add(LABKEY.Query.selectRows, config, requestScope);
 multi.send(
   function () { console.log("send complete"); },
   sendCallbackScope // scope to execute 'send complete' callback in.
 );

 // additional requests won't be sent while other requests are in progress
 multi.add(LABKEY.Query.selectRows, config);
 multi.send(function () { console.log("send complete"); }, sendCallbackScope);

 // constructor can take an array of requests [function, config] pairs
 multi = new LABKEY.MultiRequest([
      [ LABKEY.Query.selectRows, config ],
      [ LABKEY.Query.selectRows, config ],
      [ LABKEY.Query.selectRows, config ]
 ]);
 multi.send();

 // constructor can take a config object with listeners and requests.
 // if there is a 'done' listener, the requests will be sent immediately.
 multi = new LABKEY.MultiRequest({
   listeners : { 'done': function () { console.log("send complete"); }, scope: sendCallbackScope },
   requests : [ [ LABKEY.Query.selectRows, config ],
                [ LABKEY.Query.selectRows, config ],
                [ LABKEY.Query.selectRows, config ] ]
 });

 // Alternate syntax for adding the 'done' event listener.
 multi = new LABKEY.MultiRequest({
   listeners : {
     'done': {
        fn: function () { console.log("send complete"); }
        scope: sendCallbackScope
     }
   },
 });
 * </pre>
 */
LABKEY.MultiRequest = function (config) {
    config = config || {};

    var self = this;
    var sending = false;
    var waitQ = [];
    var sendQ = [];

    var requests;
    var listeners;
    if (LABKEY.Utils.isArray(config)) {
        requests = config;
    } else {
        requests = config.requests;
        listeners = config.listeners;
    }

    if (requests) {
        for (var i = 0; i < requests.length; i++) {
            var request = requests[i];
            this.add(request[0], request[1]);
        }
    }

    var doneCallbacks = [];
    if (listeners && listeners.done) {
        if (typeof listeners.done == "function") {
            doneCallbacks.push({fn: listeners.done, scope: listeners.scope});
        }
        else if (typeof listeners.done.fn == "function") {
            doneCallbacks.push({fn: listeners.done.fn, scope: listeners.done.scope || listeners.scope});
        }
    }

    if (waitQ.length && doneCallbacks.length > 0) {
        this.send();
    }

    function fireDone() {
        //console.log("fireDone:");
        for (var i = 0; i < doneCallbacks.length; i++) {
            var cb = doneCallbacks[i];
            //console.log("  invoking done callback: ", cb);
            if (cb.fn && typeof cb.fn == "function") {
                cb.fn.call(cb.scope || window);
            }
        }
    }

    function checkDone() {
        //console.log("checkDone: sendQ.length=" + sendQ.length);
        sendQ.pop();
        if (sendQ.length == 0) {
            sending = false;
            fireDone();
            self.send();
        }
        return true;
    }

    function createSequence(fn1, fn2, scope) {
        return function () {
            var ret = fn1.apply(scope || this || window, arguments);
            fn2.apply(scope || this || window, arguments);
            return ret;
        }
    }

    /**
     * Adds a request to the queue.
     * @param fn {Function} A request function which takes single config object.
     * @param config {Object} The config object that will be passed to the request <code>fn</code>
     * and must contain success and failure callbacks.
     * @param [scope] {Object} The scope in which to execute the request <code>fn</code>.
     * Note that the config success and failure callbacks will execute in the <code>config.scope</code> and not the <code>scope</code> argument.
     * @returns {LABKEY.MultiRequest} this object so add calls can be chained.
     * @example
     * <pre>
     * new MultiRequest().add(Ext.Ajax.request, {
     *     url: LABKEY.ActionURL.buildURL("controller", "action1", "/container/path"),
     *     success: function () { console.log("success 1!"); },
     *     failure: function () { console.log("failure 1!"); },
     *     scope: this // The scope of the success and failure callbacks.
     * }).add({Ext.Ajax.request, {
     *     url: LABKEY.ActionURL.buildURL("controller", "action2", "/container/path"),
     *     success: function () { console.log("success 2!"); },
     *     failure: function () { console.log("failure 2!"); },
     *     scope: this // The scope of the success and failure callbacks.
     * }).send(function () { console.log("all done!") });
     * </pre>
     */
    this.add = function (fn, config, scope) {
        config = config || {};

        var success = LABKEY.Utils.getOnSuccess(config);
        if (!success) success = function () { };
        if (!success._hookInstalled) {
            config.success = createSequence(success, checkDone, config.scope);
            config.success._hookInstalled = true;
        }

        var failure = LABKEY.Utils.getOnFailure(config);
        if (!failure) failure = function () { };
        if (!failure._hookInstalled) {
            config.failure = createSequence(failure, checkDone, config.scope);
            config.failure._hookInstalled = true;
        }

        waitQ.push({fn: fn, args: [config], scope: scope});
        return this;
    };

    /**
     * Send the queued up requests.  When all requests have returned, the send callback
     * will be called.
     * @param callback {Function} A function with a single argument of 'this'.
     * @param [scope] {Object} The scope in which to execute the callback.
     *
     * Alternatively, a single config Object argument:
     * <ul>
     * <li>fn: The send callback function.
     * <li>scope: The scope to execute the send callback function in.
     * </ul>
     */
    this.send = function (callback, scope) {
        if (sending || waitQ.length == 0)
            return;
        sending = true;
        sendQ = waitQ;
        waitQ = new Array();

        var len = sendQ.length;
        for (var i = 0; i < len; i++) {
            var q = sendQ[i];
            q.fn.apply(q.scope || window, q.args);
        }

        var self = this;
        if (typeof callback == "function") {
            doneCallbacks.push({fn: callback, scope: scope});
        } else if (typeof callback.fn == "function") {
            doneCallbacks.push({fn: callback.fn, scope: callback.scope||scope});
        }
    };
};
