/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @version 10.2
 * @license Copyright (c) 2009-2010 LabKey Corporation
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
 * Make multiple ajax requests and fires an event when all are complete.
 * Requests are added as [function, config] array pairs where the config object
 * is passed as the argument to the request function.  The request function's config
 * object argument must accept a success callback named 'success' or 'successCallback'
 * and a failure callback named 'failure', 'failureCallback', or 'errorCallback'.
 * @class Make multiple ajax requests and fires an event when all are complete.
 * @memberOf LABKEY
 *
 * @param [config] Optional. Either an array of [function, config] array pairs
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
   successCallback: function (data, options, response) {
       console.log("selectRows success: " + data.rowCount);
   },
   failureCallback: function (response, options) {
       console.log("selectRows failure");
   }
 };

 // add the requests and config arguments one by one
 var multi = new LABKEY.MultiRequest();
 multi.add(LABKEY.Query.selectRows, config);
 multi.add(LABKEY.Query.selectRows, config);
 multi.add(LABKEY.Query.selectRows, config);
 multi.send(function () { console.log("send complete"); });

 // additional requests won't be sent while other requests are in progress
 multi.add(LABKEY.Query.selectRows, config);
 multi.send(function () { console.log("send complete"); });

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
   listeners : { 'done': function () { console.log("send complete"); } },
   requests : [ [ LABKEY.Query.selectRows, config ],
                [ LABKEY.Query.selectRows, config ],
                [ LABKEY.Query.selectRows, config ] ]
 });
 * </pre>
 */
LABKEY.MultiRequest = function (config) {
    config = config || {};

    this.sending = false;
    this.waitQ = new Array();

    var requests;
    if (Ext.isArray(config)) {
        requests = config;
    } else {
        requests = config.requests;
        this.listeners = config.listeners;
    }

    if (requests) {
        for (var i = 0; i < requests.length; i++) {
            var request = requests[i];
            this.add(request[0], request[1]);
        }
    }

    this.addEvents("done");
    LABKEY.MultiRequest.superclass.constructor.call(this);

    if (this.waitQ.length && this.hasListener("done")) {
        this.send();
    }
};
Ext.extend(LABKEY.MultiRequest, Ext.util.Observable,
/**
 * @lends LABKEY.MultiRequest.prototype
 */
{
    /**
     * Adds a request to the queue.
     * @param fn {Function} A function which takes single config object.
     * @param config {Object} The config object that will be passed to the request fn
     * and must contain success and failure callbacks.
     * @returns {LABKEY.MultiRequest} this object so add calls can be chained.
     * @example
     * new MultiRequest().add(Ext.Ajax.request, {
     *     url: LABKEY.ActionURL.buildURL("controller", "action1", "/container/path"),
     *     success: function () { console.log("success 1!"); },
     *     failure: function () { console.log("failure 1!"); },
     * }).add({Ext.Ajax.request, {
     *     url: LABKEY.ActionURL.buildURL("controller", "action2", "/container/path"),
     *     success: function () { console.log("success 2!"); },
     *     failure: function () { console.log("failure 2!"); },
     * }).send(function () { console.log("all done!") });
     */
    add : function (fn, config) {
        config = config || {};

        var self = this;
        function fireDone() {
            //console.log("fireDone: self.sendQ.length=" + self.sendQ.length);
            self.sendQ.pop();
            if (self.sendQ.length == 0) {
                self.sending = false;
                self.fireEvent("done");
                self.send();
            }
            return true;
        }

        var success = config.success || config.successCallback;
        if (!success) success = function () { };
        if (!success._hookInstalled) {
            config.success = config.successCallback = success.createSequence(fireDone);
            config.success._hookInstalled = true;
        }

        var failure = config.failure || config.failureCallback || config.errorCallback;
        if (!failure) failure = function () { };
        if (!failure._hookInstalled) {
            config.failure = config.failureCallback = config.errorCallback = failure.createSequence(fireDone);
            config.failure._hookInstalled = true;
        }

        this.waitQ.push(fn.createCallback(config));
        return this;
    },

    /**
     * Send the queued up requests.  When all requesta have returned, the callback
     * will be called.
     * @param callback {Function} A function with a single argument of 'this'.
     * @param [scope] {Object} Optional. The scope in which to execute the callback.
     */
    send : function (callback, scope) {
        if (this.sending || this.waitQ.length == 0)
            return;
        this.sending = true;
        this.sendQ = this.waitQ;
        this.waitQ = new Array();

        var len = this.sendQ.length;
        for (var i = 0; i < len; i++) {
            var fn = this.sendQ[i];
            fn();
        }

        var self = this;
        if (typeof callback == "function") {
            function onetimeCallback() {
                self.un("done", onetimeCallback);
                callback.apply(scope||window, [self]);
            }

            this.on("done", onetimeCallback);
        }
    }
});
